"""Offline stdlib tests: python -m unittest discover -s tests.

Synthetic fixture metadata is used for selection assertions so tests do not
pretend that the demo catalog's illustrative acoustics are measured values.
"""

from __future__ import annotations

import json
import math
import random
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path
from unittest.mock import patch

from server.acoustics import VECTOR_KEYS, arc_target, cosine_similarity, harmonic_flow
from server.catalog import CatalogProvider
from server.lineup import build_set
from server.profile import (
    BUCKETS,
    DEFAULT_VECTORS,
    EPISODE_LIMIT,
    RECENT_RING_SIZE,
    bucket_for_hour,
    inspect_profile,
    learn,
    new_profile,
    reward_for,
    sample_track,
    target_for,
    track_uncertainty,
)


def fixture_track(
    number=1, artist="Aster", genre="pop", subgenre="dance-pop", era="2010s", bpm=100, key="8A"
):
    return {
        "id": f"fixture_{number:03d}",
        "title": f"Fixture track {number}",
        "artist": artist,
        "album": "Synthetic test fixture",
        "duration_ms": 200000,
        "genre": genre,
        "subgenres": [subgenre],
        "moods": ["calm", "focus"],
        "era": era,
        "bpm": bpm,
        "camelot_key": key,
        "vector": {dimension: 0.5 for dimension in VECTOR_KEYS},
        "cues": {"intro_vocal_ms": None, "outro_ramp_ms": 192000},
        "artist_facts": ["This is synthetic test metadata, not a real artist biography."],
        "metadata_provenance": {"source": "synthetic_test_fixture"},
        "metadata_quality": "synthetic",
    }


class CatalogFixture(unittest.TestCase):
    def make_catalog(self, data):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / "catalog.json"
        path.write_text(json.dumps(data), encoding="utf-8")
        return CatalogProvider(path)


class CatalogTests(CatalogFixture):
    @classmethod
    def setUpClass(cls):
        cls.catalog = CatalogProvider()

    def test_bundled_catalog_is_rich_valid_and_metadata_only(self):
        tracks = self.catalog.tracks
        self.assertGreaterEqual(len(tracks), 80)
        self.assertEqual(len(tracks), len(self.catalog.by_id))
        first = self.catalog.get("trk_001")
        self.assertEqual((first["artist"], first["title"]), ("M83", "Midnight City"))
        with self.catalog.path.open(encoding="utf-8") as handle:
            self.assertIsInstance(json.load(handle), list)
        genres = {track["genre"] for track in tracks}
        self.assertTrue({"pop", "synthwave", "lo-fi", "rock", "acoustic", "hip-hop", "electronic"} <= genres)
        for genre in genres:
            self.assertGreaterEqual(sum(track["genre"] == genre for track in tracks), 8)
        for track in tracks:
            with self.subTest(track=track["id"]):
                self.assertEqual(set(track["vector"]), set(VECTOR_KEYS))
                self.assertGreaterEqual(len(track["subgenres"]), 2)
                self.assertGreaterEqual(len(track["moods"]), 3)
                self.assertTrue(track["artist_facts"])
                self.assertIn("not_measured", track["metadata_quality"])
                self.assertTrue(
                    {"duration_ms", "bpm", "camelot_key", "vector", "cues"}
                    <= set(track["metadata_provenance"]["estimated_fields"])
                )
                serialized = json.dumps(track, allow_nan=False).casefold()
                self.assertNotIn("https://", serialized)
                self.assertNotIn("http://", serialized)
                self.assertNotIn("audio_url", serialized)
                self.assertNotIn("preview_url", serialized)

    def test_outro_cues_are_absolute_near_end_timestamps(self):
        for track in self.catalog.tracks:
            with self.subTest(track=track["id"]):
                outro = track["cues"]["outro_ramp_ms"]
                duration = track["duration_ms"]
                self.assertGreaterEqual(outro, 0.80 * duration)
                self.assertLess(outro, duration)
                self.assertGreaterEqual(duration - outro, 5000)
                self.assertLessEqual(duration - outro, 20000)
                intro = track["cues"]["intro_vocal_ms"]
                if intro is not None:
                    self.assertLessEqual(intro, outro)
        expected = {
            "trk_001": 232000, "trk_002": 192000, "trk_015": 244000,
            "trk_028": 235000, "trk_030": 93000, "trk_084": 227000,
            "trk_090": 297000,
        }
        for track_id, timestamp in expected.items():
            self.assertEqual(self.catalog.get(track_id)["cues"]["outro_ramp_ms"], timestamp)

    def test_provider_preserves_absolute_cues_and_nullable_vocal_intro(self):
        instrumental = fixture_track()
        vocal = fixture_track(2)
        vocal["cues"] = {"intro_vocal_ms": 12000, "outro_ramp_ms": 185000}
        catalog = self.make_catalog([instrumental, vocal])
        self.assertEqual(catalog.get(instrumental["id"])["cues"], {
            "intro_vocal_ms": None, "outro_ramp_ms": 192000,
        })
        self.assertEqual(catalog.get(vocal["id"])["cues"], vocal["cues"])

    def test_synthwave_taxonomy_supports_blueprint_and_seed_artists(self):
        first = self.catalog.get("trk_001")
        self.assertEqual(first["genre"], "synthwave")
        self.assertIn("synth-pop", first["subgenres"])
        self.assertIn("editorial", first["metadata_provenance"]["taxonomy"])
        synthwave = [track for track in self.catalog.tracks if track["genre"] == "synthwave"]
        self.assertGreaterEqual(len(synthwave), 12)
        artists = {track["artist"] for track in synthwave}
        self.assertTrue({"M83", "Kavinsky", "The Midnight", "FM-84", "HOME"} <= artists)
        subgenres = {name for track in synthwave for name in track["subgenres"]}
        self.assertTrue({"retrowave", "dreamwave", "darksynth", "outrun", "synth-pop"} <= subgenres)
        found = {track["id"] for track in self.catalog.search("synthwave", limit=100)}
        self.assertTrue({track["id"] for track in synthwave} <= found)

    def test_get_raises_for_unknown_id(self):
        with self.assertRaises(KeyError):
            self.catalog.get("unknown")

    def test_inventory_none_empty_unknown_and_duplicates(self):
        self.assertEqual(self.catalog.candidates(), self.catalog.tracks)
        self.assertIsNot(self.catalog.candidates(), self.catalog.tracks)
        self.assertEqual(self.catalog.candidates([]), [])
        self.assertEqual(self.catalog.candidates(["missing"]), [])
        self.assertEqual(
            [track["id"] for track in self.catalog.candidates(["trk_002", "missing", "trk_001", "trk_001"])],
            ["trk_001", "trk_002"],
        )

    def test_search_handles_typos_case_accents_and_limits(self):
        self.assertEqual(self.catalog.search("midnigt cty", limit=1)[0]["id"], "trk_001")
        self.assertEqual(self.catalog.search("M83 MIDNIGHT CITY", limit=1)[0]["id"], "trk_001")
        self.assertEqual(self.catalog.search("jose gonzalez", limit=1)[0]["id"], "trk_055")
        self.assertEqual(self.catalog.search("bjork", limit=1)[0]["id"], "trk_087")
        self.assertEqual(len(self.catalog.search("", limit=3)), 3)
        self.assertEqual(self.catalog.search("pop", limit=0), [])
        self.assertEqual(self.catalog.search("pop", limit=-1), [])

    def test_search_cannot_escape_playable_inventory(self):
        self.assertEqual(self.catalog.search("Midnight City", []), [])
        self.assertEqual(self.catalog.search("Midnight City", ["unknown"]), [])
        result = self.catalog.search("pop", ["trk_003", "unknown"])
        self.assertEqual([track["id"] for track in result], ["trk_003"])
        self.assertEqual(self.catalog.search("", []), [])

    def test_rejects_nonlist_root_and_duplicate_ids(self):
        with self.assertRaisesRegex(ValueError, "root"):
            self.make_catalog({"tracks": []})
        with self.assertRaisesRegex(ValueError, "duplicate"):
            self.make_catalog([fixture_track(), fixture_track()])
        with self.assertRaises(ValueError):
            self.make_catalog(["not an object"])

    def test_rejects_invalid_metadata(self):
        changes = [
            ("title", ""), ("duration_ms", 0), ("duration_ms", True),
            ("bpm", float("nan")), ("bpm", True), ("camelot_key", "13A"),
            ("vector", {key: 1.1 for key in VECTOR_KEYS}),
            ("vector", {key: float("inf") for key in VECTOR_KEYS}),
            ("vector", {"energy": 0.5}), ("moods", []), ("artist_facts", []),
            ("cues", {"intro_vocal_ms": -1, "outro_ramp_ms": 192000}),
            ("cues", {"intro_vocal_ms": 200001, "outro_ramp_ms": 192000}),
            ("cues", {"intro_vocal_ms": None, "outro_ramp_ms": -1}),
            ("cues", {"intro_vocal_ms": None, "outro_ramp_ms": None}),
            ("cues", {"intro_vocal_ms": None, "outro_ramp_ms": True}),
            ("cues", {"intro_vocal_ms": None, "outro_ramp_ms": 200001}),
            ("cues", {"outro_ramp_ms": 192000}),
            ("metadata_quality", ""), ("metadata_provenance", {}),
        ]
        for field, value in changes:
            with self.subTest(field=field, value=value):
                track = fixture_track()
                track[field] = value
                with self.assertRaises(ValueError):
                    self.make_catalog([track])

    def test_empty_custom_catalog_is_valid_but_unplayable(self):
        catalog = self.make_catalog([])
        self.assertEqual(catalog.tracks, [])
        with self.assertRaises(ValueError):
            build_set(catalog, new_profile("empty"), {})


class AcousticTests(unittest.TestCase):
    def test_cosine_identity_orthogonality_symmetry_and_opposition(self):
        energy = {"energy": 1.0}
        acoustic = {"acousticness": 1.0}
        mixed = {"energy": 1, "valence": 1, "tempo_norm": 0.25}
        self.assertAlmostEqual(cosine_similarity(mixed, mixed), 1.0)
        self.assertEqual(cosine_similarity(energy, acoustic), 0.0)
        self.assertEqual(cosine_similarity(energy, {"energy": -2}), -1.0)
        self.assertAlmostEqual(cosine_similarity(energy, mixed), cosine_similarity(mixed, energy))

    def test_cosine_zero_nonfinite_extra_keys_and_large_values(self):
        self.assertEqual(cosine_similarity({}, {}), 0.0)
        self.assertEqual(cosine_similarity({"energy": 1}, {}), 0.0)
        self.assertEqual(cosine_similarity({"energy": float("nan")}, {"energy": 1}), 0.0)
        self.assertEqual(cosine_similarity({"bpm": 120}, {"bpm": 120}), 0.0)
        self.assertAlmostEqual(cosine_similarity({"energy": 1e300}, {"energy": 1e300}), 1.0)
        self.assertEqual(cosine_similarity({"energy": 1}, {"energy": 1, "ignored": 1e300}), 1.0)

    def test_camelot_same_adjacent_wraparound_relative_and_unrelated(self):
        previous = {"bpm": 100, "camelot_key": "12A"}
        same = harmonic_flow(previous, previous)
        neighbor = harmonic_flow(previous, {"bpm": 100, "camelot_key": "1A"})
        relative = harmonic_flow(previous, {"bpm": 100, "camelot_key": "12B"})
        distant = harmonic_flow(previous, {"bpm": 100, "camelot_key": "6A"})
        self.assertEqual(same, 1.0)
        self.assertGreater(same, neighbor)
        self.assertGreater(neighbor, relative)
        self.assertGreater(relative, distant)
        self.assertAlmostEqual(neighbor, 0.935)
        self.assertEqual(harmonic_flow(previous, {"bpm": 100, "camelot_key": " 1a "}), neighbor)

    def test_tempo_twelve_bpm_half_double_and_neutral_missing(self):
        previous = {"bpm": 100, "camelot_key": "8A"}
        for bpm in (50, 100, 200):
            with self.subTest(bpm=bpm):
                candidate = {"bpm": bpm, "camelot_key": "8A"}
                self.assertEqual(harmonic_flow(previous, candidate), 1.0)
                self.assertEqual(harmonic_flow(candidate, previous), 1.0)
        within = harmonic_flow(previous, {"bpm": 112, "camelot_key": "8A"})
        outside = harmonic_flow(previous, {"bpm": 130, "camelot_key": "8A"})
        self.assertGreater(within, outside)
        self.assertEqual(harmonic_flow(None, previous), 0.5)
        self.assertEqual(harmonic_flow({}, {}), 0.5)
        self.assertTrue(math.isfinite(harmonic_flow({"bpm": float("nan")}, {})))

    def test_arc_is_zero_based_copied_clamped_and_repeating(self):
        target = {key: 0.5 for key in VECTOR_KEYS}
        original = deepcopy(target)
        self.assertEqual(arc_target(target, 0), target)
        self.assertIsNot(arc_target(target, 0), target)
        self.assertAlmostEqual(arc_target(target, 1)["energy"], 0.6)
        self.assertEqual(arc_target(target, 2)["energy"], 0.54)
        self.assertLess(arc_target(target, 3)["energy"], target["energy"])
        self.assertEqual(arc_target(target, 4), arc_target(target, 0))
        self.assertEqual(target, original)
        self.assertEqual(arc_target({key: 0.98 for key in VECTOR_KEYS}, 1)["energy"], 1.0)
        with self.assertRaises(ValueError):
            arc_target(target, -1)


class ProfileTests(unittest.TestCase):
    def setUp(self):
        self.profile = new_profile("listener")
        self.track = fixture_track()
        self.track["vector"] = {key: 0.8 for key in VECTOR_KEYS}

    def test_new_profile_has_json_schema_and_independent_defaults(self):
        required = {
            "uid", "created_at", "total_listen_sec", "global_affinities", "circadian_vectors",
            "recent_track_ring_buffer", "spoken_facts_history", "episodes", "stats",
        }
        self.assertTrue(required <= self.profile.keys())
        self.assertEqual(set(self.profile["circadian_vectors"]), set(BUCKETS))
        self.assertEqual(set(self.profile["global_affinities"]), {"genres", "artists", "eras"})
        self.assertTrue(self.profile["created_at"].endswith("Z"))
        json.dumps(self.profile, allow_nan=False)
        other = new_profile("other")
        self.profile["circadian_vectors"]["morning"]["energy"] = 0.99
        self.assertEqual(other["circadian_vectors"], DEFAULT_VECTORS)
        self.assertNotEqual(other["uid"], self.profile["uid"])
        with self.assertRaises(ValueError):
            new_profile("")

    def test_all_24_hours_and_bucket_boundaries(self):
        expected = {
            "morning": set(range(5, 12)), "afternoon": set(range(12, 17)),
            "evening": set(range(17, 22)), "late_night": {22, 23, 0, 1, 2, 3, 4},
        }
        for hour in range(24):
            self.assertIn(hour, expected[bucket_for_hour(hour)])
        for invalid in (-1, 24, 5.5, True, "5"):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                bucket_for_hour(invalid)

    def test_target_is_copy_with_backward_compatible_defaults(self):
        before = deepcopy(self.profile)
        target = target_for(self.profile, "morning")
        target["energy"] = 0
        self.assertEqual(self.profile, before)
        self.assertEqual(target_for({}, "late_night"), DEFAULT_VECTORS["late_night"])
        with self.assertRaises(ValueError):
            target_for(self.profile, "midnight")

    def test_explicit_positive_and_early_skip_rewards(self):
        for event in ("like", "replay", " LIKE "):
            self.assertEqual(reward_for(event, 0, 200000), 1.5)
        self.assertEqual(reward_for("skip", 7999, 200000), -1.0)
        self.assertEqual(reward_for("skip", 8000, 200000), 0.0)
        self.assertEqual(reward_for("skip", 1000, 2000), -1.0)

    def test_late_skip_ratio_boundaries_and_partial_neutrality(self):
        for listened in (30000, 60000, 130000):
            self.assertEqual(reward_for("skip", listened, 200000), -0.25)
        for listened in (29999, 130001, 150000):
            self.assertEqual(reward_for("skip", listened, 200000), 0.0)
        self.assertEqual(reward_for("pause", 120000, 200000), 0.0)

    def test_completion_requires_actual_eighty_percent_not_event_label(self):
        for event in ("complete", "completed", "ended", "progress"):
            self.assertEqual(reward_for(event, 1000, 200000), 0.0)
            self.assertEqual(reward_for(event, 159999, 200000), 0.0)
            self.assertEqual(reward_for(event, 160000, 200000), 0.7)
        self.assertEqual(reward_for("skip", 180000, 200000), 0.7)
        self.assertEqual(reward_for("complete", 1000000, 200000), 0.7)
        for duration in (0, -1, None, float("nan")):
            self.assertEqual(reward_for("complete", 1000, duration), 0.0)
        self.assertEqual(reward_for("complete", float("nan"), 200000), 0.0)

    def test_like_updates_priors_ema_stats_log_and_serializable_delta(self):
        before = target_for(self.profile, "morning")
        untouched_track = deepcopy(self.track)
        delta = learn(self.profile, self.track, "morning", "like", 20000, 200000)
        self.assertEqual(delta["reward"], 1.5)
        self.assertEqual(delta["vector_before"], before)
        for key in VECTOR_KEYS:
            self.assertAlmostEqual(delta["vector_after"][key], before[key] + 0.12 * (0.8 - before[key]))
        expected = {"alpha": 2.5, "beta": 1.0}
        self.assertEqual(self.profile["global_affinities"]["artists"]["Aster"], expected)
        self.assertEqual(self.profile["circadian_affinities"]["morning"]["genres"]["pop"], expected)
        self.assertEqual(self.profile["circadian_affinities"]["evening"]["genres"], {})
        self.assertEqual(len(delta["changed_priors"]), 6)
        self.assertEqual(self.profile["total_listen_sec"], 20.0)
        self.assertEqual(self.profile["stats"]["positive_events"], 1)
        self.assertEqual(self.profile["recent_track_ring_buffer"], [self.track["id"]])
        self.assertEqual(self.profile["episodes"][-1]["reward"], 1.5)
        self.assertEqual(self.track, untouched_track)
        json.dumps(delta, allow_nan=False)
        json.dumps(self.profile, allow_nan=False)
        delta["vector_after"]["energy"] = 0
        self.assertNotEqual(self.profile["circadian_vectors"]["morning"]["energy"], 0)

    def test_completion_scales_positive_ema(self):
        before = target_for(self.profile, "afternoon")
        delta = learn(self.profile, self.track, "afternoon", "complete", 160000, 200000)
        self.assertAlmostEqual(delta["vector_after"]["energy"], before["energy"] + 0.084 * (0.8 - before["energy"]))
        self.assertEqual(self.profile["global_affinities"]["genres"]["pop"], {"alpha": 1.7, "beta": 1.0})

    def test_late_skip_never_penalizes_artist_or_era(self):
        learn(self.profile, self.track, "evening", "like", 20000, 200000)
        global_before = deepcopy(self.profile["global_affinities"])
        local_before = deepcopy(self.profile["circadian_affinities"]["evening"])
        delta = learn(self.profile, self.track, "evening", "skip", 60000, 200000)
        self.assertEqual(delta["reward"], -0.25)
        for dimension in ("artists", "eras"):
            self.assertEqual(self.profile["global_affinities"][dimension], global_before[dimension])
            self.assertEqual(self.profile["circadian_affinities"]["evening"][dimension], local_before[dimension])
        self.assertEqual({change["dimension"] for change in delta["changed_priors"]}, {"genres"})
        self.assertEqual(self.profile["global_affinities"]["genres"]["pop"]["beta"], 1.25)
        cold = new_profile("cold")
        learn(cold, self.track, "evening", "skip", 60000, 200000)
        self.assertEqual(cold["global_affinities"]["artists"], {})

    def test_early_skip_updates_negative_evidence_and_bounds_vectors(self):
        for _ in range(40):
            delta = learn(self.profile, self.track, "late_night", "skip", 500, 200000)
        self.assertEqual(delta["reward"], -1.0)
        self.assertEqual(self.profile["global_affinities"]["artists"]["Aster"]["beta"], 41.0)
        self.assertTrue(all(0 <= value <= 1 for value in delta["vector_after"].values()))

    def test_neutral_and_fake_completion_do_not_learn_preferences(self):
        before = deepcopy(self.profile["global_affinities"])
        vector_before = target_for(self.profile, "morning")
        delta = learn(self.profile, self.track, "morning", "complete", 1000, 200000)
        self.assertEqual(delta["reward"], 0.0)
        self.assertEqual(delta["changed_priors"], [])
        self.assertEqual(self.profile["global_affinities"], before)
        self.assertEqual(delta["vector_after"], vector_before)
        self.assertEqual(self.profile["stats"]["neutral_events"], 1)

    def test_priors_match_case_insensitively_and_optional_context_can_be_absent(self):
        del self.profile["circadian_affinities"]
        self.profile["global_affinities"]["artists"]["ASTER"] = {"alpha": 2.0, "beta": 1.0}
        sample_track(self.profile, self.track, "morning", random.Random(2))
        learn(self.profile, self.track, "morning", "replay", 0, 200000)
        self.assertEqual(set(self.profile["global_affinities"]["artists"]), {"ASTER"})
        self.assertEqual(self.profile["global_affinities"]["artists"]["ASTER"]["alpha"], 3.5)
        self.assertIn("morning", self.profile["circadian_affinities"])

    def test_recent_ring_and_episode_log_are_bounded_and_dwell_is_capped(self):
        for number in range(EPISODE_LIMIT + 5):
            track = fixture_track(number)
            learn(self.profile, track, "morning", "progress", 100, 200000)
        self.assertEqual(len(self.profile["recent_track_ring_buffer"]), RECENT_RING_SIZE)
        self.assertEqual(len(self.profile["episodes"]), EPISODE_LIMIT)
        self.assertEqual(self.profile["recent_track_ring_buffer"][-1], f"fixture_{EPISODE_LIMIT + 4:03d}")
        learn(self.profile, self.track, "morning", "complete", 1000000, 200000)
        self.assertAlmostEqual(self.profile["total_listen_sec"], (EPISODE_LIMIT + 5) / 10 + 200)
        learn(self.profile, self.track, "morning", "like", 0, 200000)
        self.assertEqual(self.profile["recent_track_ring_buffer"].count(self.track["id"]), 1)

    def test_contextual_thompson_blends_global_and_bucket_evidence(self):
        class MeanRng(random.Random):
            def __init__(self):
                super().__init__(0)
                self.calls = []

            def betavariate(self, alpha, beta):
                self.calls.append((alpha, beta))
                return alpha / (alpha + beta)

        self.profile["global_affinities"]["genres"]["pop"] = {"alpha": 9, "beta": 1}
        self.profile["circadian_affinities"]["morning"]["genres"]["pop"] = {"alpha": 1, "beta": 9}
        rng = MeanRng()
        morning = sample_track(self.profile, self.track, "morning", rng)
        self.assertAlmostEqual(rng.calls[0][0], 6.2)
        self.assertAlmostEqual(rng.calls[0][1], 3.8)
        evening = sample_track(self.profile, self.track, "evening", MeanRng())
        self.assertLess(morning, evening)

    def test_sampling_is_seeded_read_only_and_uncertainty_shrinks(self):
        before = deepcopy(self.profile)
        values = [sample_track(self.profile, self.track, "morning", random.Random(seed)) for seed in range(10)]
        repeated = [sample_track(self.profile, self.track, "morning", random.Random(seed)) for seed in range(10)]
        self.assertEqual(values, repeated)
        self.assertTrue(all(0 <= value <= 1 for value in values))
        self.assertEqual(self.profile, before)
        cold_uncertainty = track_uncertainty(self.profile, self.track, "morning")
        for _ in range(20):
            learn(self.profile, self.track, "morning", "like", 0, 200000)
        self.assertLess(track_uncertainty(self.profile, self.track, "morning"), cold_uncertainty)

    def test_inspection_ranks_labeled_numeric_beta_intervals_and_copies_log(self):
        self.profile["global_affinities"]["genres"] = {
            "uniform": {"alpha": 1, "beta": 1}, "favored": {"alpha": 16, "beta": 1},
        }
        self.profile["global_affinities"]["artists"] = {"Aster": {"alpha": 3, "beta": 2}}
        before = deepcopy(self.profile)
        view = inspect_profile(self.profile)
        self.assertEqual(view["top_genres"][0]["name"], "favored")
        self.assertEqual(view["top_genres"][0]["confidence"], "high")
        self.assertEqual(view["top_artists"][0]["confidence"], "medium")
        favored = view["top_genres"][0]["credible_interval"]
        uniform = view["top_genres"][1]["credible_interval"]
        self.assertAlmostEqual(favored["lower"], 0.05 ** (1 / 16), places=7)
        self.assertAlmostEqual(favored["upper"], 0.95 ** (1 / 16), places=7)
        self.assertAlmostEqual(uniform["lower"], 0.05, places=7)
        self.assertAlmostEqual(uniform["upper"], 0.95, places=7)
        self.assertEqual(view["episodic_log"], self.profile["episodes"])
        self.assertEqual(view["stats"], self.profile["stats"])
        self.assertEqual(self.profile, before)
        view["global_affinities"]["genres"]["favored"]["alpha"] = 0
        self.assertEqual(self.profile, before)
        json.dumps(view, allow_nan=False)


class LineupTests(CatalogFixture):
    def setUp(self):
        self.tracks = [
            fixture_track(1),
            fixture_track(2, "Aster", "pop", "indie pop", "2020s"),
            fixture_track(3, "Birch", "rock", "indie rock", "1990s", 155, "2A"),
            fixture_track(4, "Cedar", "acoustic", "folk", "1970s"),
            fixture_track(5, "Delta", "electronic", "ambient", "2000s"),
            fixture_track(6, "Elm", "hip-hop", "jazz rap", "1990s"),
            fixture_track(7, "Fir", "lo-fi", "chillhop", "2010s"),
            fixture_track(8, "Grove", "synthwave", "outrun", "2010s"),
        ]
        self.catalog = self.make_catalog(self.tracks)
        self.profile = new_profile("lineup-listener")
        self.session = {
            "bucket": "evening", "target_vector": {key: 0.5 for key in VECTOR_KEYS},
            "available_track_ids": None, "positive_tags": [], "seed_genres": [], "seed_artists": [],
            "exclude_genres": [], "exclude_artists": [], "exclude_subgenres": [], "exclude_eras": [],
            "cooldown_artists": [], "cooldown_subgenres": [],
            "current_track_id": None, "queue": [], "history": [],
        }

    def choose(self, size=4, seed=7):
        return build_set(self.catalog, self.profile, self.session, size=size, rng=random.Random(seed))

    def test_inventory_is_a_hard_allowlist(self):
        self.session["available_track_ids"] = ["fixture_003", "unknown", "fixture_006"]
        result = self.choose(size=10)
        self.assertEqual({track["id"] for track in result}, {"fixture_003", "fixture_006"})
        self.assertEqual(len(result), 2)

    def test_explicit_empty_and_unknown_only_inventory_raise(self):
        for inventory in ([], ["unknown"]):
            self.session["available_track_ids"] = inventory
            self.session["seed_artists"] = ["Aster"]
            with self.subTest(inventory=inventory), self.assertRaisesRegex(ValueError, "No playable"):
                self.choose()

    def test_none_inventory_uses_full_catalog_and_size_is_bounded(self):
        self.assertEqual(len(self.choose(size=100)), len(self.tracks))
        self.assertEqual(self.choose(size=0), [])
        with self.assertRaises(ValueError):
            self.choose(size=-1)

    def test_all_explicit_exclusions_are_case_insensitive_and_strict(self):
        self.session.update({
            "exclude_genres": ["PoP"], "exclude_artists": ["bIrCh"],
            "exclude_subgenres": ["FoLk"], "exclude_eras": ["2000S"],
            "seed_genres": ["pop"], "seed_artists": ["Aster"],
        })
        result = self.choose(size=20)
        self.assertEqual({track["id"] for track in result}, {"fixture_006", "fixture_007", "fixture_008"})
        self.assertEqual(len(result), 3)

    def test_negative_filters_never_relax_even_for_tiny_inventory(self):
        cases = [
            ("exclude_genres", ["POP"]), ("exclude_artists", ["ASTER"]),
            ("exclude_subgenres", ["DANCE POP"]), ("exclude_eras", ["2010S"]),
            ("cooldown_artists", ["AsTeR"]), ("cooldown_subgenres", ["DANCE-POP"]),
        ]
        for field, values in cases:
            session = deepcopy(self.session)
            session["available_track_ids"] = ["fixture_001"]
            session[field] = values
            session["seed_artists"] = ["Aster"]
            with self.subTest(field=field), self.assertRaises(ValueError):
                build_set(self.catalog, self.profile, session, rng=random.Random(0))

    def test_cooldowns_are_hard_not_just_score_penalties(self):
        self.session["cooldown_artists"] = ["aStEr"]
        self.session["cooldown_subgenres"] = ["INDIE ROCK", "FOLK"]
        result = self.choose(size=20)
        self.assertEqual({track["id"] for track in result}, {f"fixture_{number:03d}" for number in (5, 6, 7, 8)})

    def test_current_and_queue_are_already_committed_and_never_repeated(self):
        self.session["current_track_id"] = "fixture_001"
        self.session["queue"] = ["fixture_003", "unknown"]
        result = self.choose(size=20)
        self.assertEqual(len(result), 6)
        self.assertTrue({track["id"] for track in result}.isdisjoint({"fixture_001", "fixture_003", "unknown"}))
        self.session["available_track_ids"] = ["fixture_001", "fixture_003"]
        with self.assertRaises(ValueError):
            self.choose()

    def test_fresh_inventory_precedes_global_recent_then_session_replay(self):
        self.profile["recent_track_ring_buffer"] = ["fixture_001"]
        self.session["history"] = [{"track_id": "fixture_002"}]
        result = self.choose(size=20)
        ids = [track["id"] for track in result]
        self.assertEqual(set(ids[:6]), {f"fixture_{number:03d}" for number in range(3, 9)})
        self.assertEqual(ids[-2:], ["fixture_001", "fixture_002"])
        self.assertEqual(len(ids), len(set(ids)))

    def test_recent_history_can_replay_when_inventory_is_exhausted(self):
        self.profile["recent_track_ring_buffer"] = [{"track_id": "fixture_001"}]
        self.session["history"] = [{"track_id": "fixture_003"}]
        self.session["available_track_ids"] = ["fixture_001", "fixture_003"]
        result = self.choose(size=4)
        self.assertEqual([track["id"] for track in result], ["fixture_001", "fixture_003"])

    def test_older_session_history_wins_equal_scores(self):
        self.session["history"] = [{"track_id": "fixture_001"}, {"track_id": "fixture_003"}]
        self.session["available_track_ids"] = ["fixture_001", "fixture_003"]
        with patch("server.lineup.sample_track", return_value=0.5), patch("server.lineup.cosine_similarity", return_value=0.5), patch("server.lineup.harmonic_flow", return_value=0.5):
            self.assertEqual(self.choose(size=1)[0]["id"], "fixture_001")

    def test_unique_tracks_and_artist_diversity_across_seeds(self):
        for seed in range(20):
            result = self.choose(size=4, seed=seed)
            self.assertEqual(len({track["id"] for track in result}), 4)
            self.assertEqual(len({track["artist"] for track in result}), 4)
        all_tracks = self.choose(size=20)
        self.assertEqual(len({track["id"] for track in all_tracks}), len(self.tracks))
        self.assertEqual(len({track["artist"] for track in all_tracks[:7]}), 7)

    def test_seeded_rng_is_reproducible_without_mutating_inputs(self):
        before_profile = deepcopy(self.profile)
        before_session = deepcopy(self.session)
        before_catalog = deepcopy(self.catalog.tracks)
        first = self.choose(seed=123)
        second = self.choose(seed=123)
        self.assertEqual(first, second)
        rng = random.Random(123)
        state = rng.getstate()
        build_set(self.catalog, self.profile, self.session, rng=rng)
        self.assertNotEqual(rng.getstate(), state)
        self.assertEqual(self.profile, before_profile)
        self.assertEqual(self.session, before_session)
        self.assertEqual(self.catalog.tracks, before_catalog)

    def test_seed_and_tag_boosts_affect_ranking_without_becoming_filters(self):
        self.session["seed_artists"] = ["gRoVe"]
        with patch("server.lineup.sample_track", return_value=0.5), patch("server.lineup.cosine_similarity", return_value=0.5), patch("server.lineup.harmonic_flow", return_value=0.5):
            self.assertEqual(self.choose(size=1)[0]["id"], "fixture_008")
            self.session["seed_artists"] = []
            self.session["seed_genres"] = ["HIP HOP"]
            self.assertEqual(self.choose(size=1)[0]["id"], "fixture_006")
            self.session["seed_genres"] = []
            self.session["positive_tags"] = ["outrun"]
            self.assertEqual(self.choose(size=1)[0]["id"], "fixture_008")
        self.assertEqual(len(self.choose(size=20)), len(self.tracks))

    def test_discovery_position_two_receives_uncertainty_bonus(self):
        def uncertainty(profile, track, bucket):
            return 1.0 if track["id"] == "fixture_008" else 0.0

        with patch("server.lineup.sample_track", return_value=0.5), patch("server.lineup.cosine_similarity", return_value=0.5), patch("server.lineup.harmonic_flow", return_value=0.5), patch("server.lineup.track_uncertainty", side_effect=uncertainty):
            ids = [track["id"] for track in self.choose(size=3)]
        self.assertEqual(ids, ["fixture_001", "fixture_003", "fixture_008"])

    def test_acoustic_similarity_participates_in_selection(self):
        tracks = deepcopy(self.tracks[:3:2])
        tracks[0]["vector"] = {key: float(key == "energy") for key in VECTOR_KEYS}
        tracks[1]["vector"] = {key: float(key == "acousticness") for key in VECTOR_KEYS}
        catalog = self.make_catalog(tracks)
        self.session["target_vector"] = {key: float(key == "energy") for key in VECTOR_KEYS}
        with patch("server.lineup.sample_track", return_value=0), patch("server.lineup.harmonic_flow", return_value=0):
            result = build_set(catalog, self.profile, self.session, size=1, rng=random.Random(0))
        self.assertEqual(result[0]["id"], "fixture_001")

    def test_harmonic_flow_uses_end_of_queue_as_transition_context(self):
        self.session["available_track_ids"] = ["fixture_001", "fixture_003"]
        self.session["current_track_id"] = "fixture_003"
        self.session["queue"] = ["fixture_002"]
        with patch("server.lineup.sample_track", return_value=0), patch("server.lineup.cosine_similarity", return_value=0), patch("server.lineup.harmonic_flow", wraps=harmonic_flow) as flow:
            result = self.choose(size=1)
        self.assertEqual(result[0]["id"], "fixture_001")
        self.assertEqual(flow.call_args.args[0]["id"], "fixture_002")

    def test_bundled_synthwave_seeds_remain_inventory_safe(self):
        session = deepcopy(self.session)
        allowed = ["trk_001", "trk_015", "trk_017", "trk_019"]
        session.update({
            "available_track_ids": allowed,
            "seed_genres": ["SYNTHWAVE"],
            "seed_artists": ["M83", "The Midnight", "FM-84"],
        })
        result = build_set(CatalogProvider(), self.profile, session, rng=random.Random(42))
        self.assertEqual({track["id"] for track in result}, set(allowed))
        self.assertTrue(all(track["genre"] == "synthwave" for track in result))

    def test_bundled_catalog_builds_four_track_set_offline(self):
        result = build_set(CatalogProvider(), self.profile, self.session, rng=random.Random(42))
        self.assertEqual(len(result), 4)
        self.assertEqual(len({track["id"] for track in result}), 4)


if __name__ == "__main__":
    unittest.main()
