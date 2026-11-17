"""Validated, metadata-only reference catalog; no audio or network access.

The bundled catalog is a demo, not an audio analysis database. BPM, Camelot
keys, durations, vectors and cue points are illustrative estimates. Genre and
mood labels are editorial. In cues, intro_vocal_ms is an estimated first vocal
onset (None for instrumental/no supplied vocal cue); outro_ramp_ms is the
absolute playhead timestamp at which an outro ramp may start, not its duration.
Both timestamps are relative to the start of the track. Do not use these
illustrative cues for precise mixing.
"""

from __future__ import annotations

import json
import math
import re
import unicodedata
from difflib import SequenceMatcher
from pathlib import Path

VECTOR_KEYS = ("energy", "valence", "danceability", "acousticness", "tempo_norm")


def normalize_text(value: str) -> str:
    """Case-, accent-, and punctuation-insensitive comparison text."""
    decomposed = unicodedata.normalize("NFKD", value.casefold())
    plain = "".join(char for char in decomposed if not unicodedata.combining(char))
    return " ".join("".join(char if char.isalnum() else " " for char in plain).split())


def _number(value: object) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def _validate_track(track: object, index: int) -> dict:
    label = f"catalog track {index}"
    if not isinstance(track, dict):
        raise ValueError(f"{label} must be an object")
    label = f"{label} ({track.get('id', 'missing id')})"

    for field in ("id", "title", "artist", "album", "genre", "era", "metadata_quality"):
        if not isinstance(track.get(field), str) or not track[field].strip():
            raise ValueError(f"{label}: {field} must be a nonempty string")
    for field in ("subgenres", "moods", "artist_facts"):
        values = track.get(field)
        if not isinstance(values, list) or not values or any(
            not isinstance(value, str) or not value.strip() for value in values
        ):
            raise ValueError(f"{label}: {field} must be a nonempty list of strings")

    duration = track.get("duration_ms")
    if not isinstance(duration, int) or isinstance(duration, bool) or duration <= 0:
        raise ValueError(f"{label}: duration_ms must be a positive integer")
    bpm = track.get("bpm")
    if not _number(bpm) or not 20 <= bpm <= 300:
        raise ValueError(f"{label}: bpm must be finite and between 20 and 300")
    key = track.get("camelot_key")
    if not isinstance(key, str) or not re.fullmatch(r"(?:[1-9]|1[0-2])[AB]", key):
        raise ValueError(f"{label}: camelot_key must be 1A..12A or 1B..12B")

    vector = track.get("vector")
    if not isinstance(vector, dict):
        raise ValueError(f"{label}: vector must be an object")
    for field in VECTOR_KEYS:
        value = vector.get(field)
        if not _number(value) or not 0 <= value <= 1:
            raise ValueError(f"{label}: vector.{field} must be finite and in [0, 1]")

    cues = track.get("cues")
    if not isinstance(cues, dict) or not {"intro_vocal_ms", "outro_ramp_ms"} <= cues.keys():
        raise ValueError(f"{label}: cues must include intro_vocal_ms and outro_ramp_ms")
    for field in ("intro_vocal_ms", "outro_ramp_ms"):
        value = cues[field]
        if field == "intro_vocal_ms" and value is None:
            continue
        if not isinstance(value, int) or isinstance(value, bool) or not 0 <= value <= duration:
            raise ValueError(f"{label}: cues.{field} must be an absolute integer timestamp within the duration")

    provenance = track.get("metadata_provenance")
    if not isinstance(provenance, dict) or not provenance.get("source"):
        raise ValueError(f"{label}: metadata_provenance must identify a source")
    return track


class CatalogProvider:
    """Load a JSON root list and enforce the recommendation metadata contract.

    Available IDs are an allowlist, never hints: None exposes the catalog;
    [] exposes nothing. Unknown IDs are ignored, and order follows the catalog.
    Returned dictionaries are catalog records; callers should treat them as
    read-only. The engine itself never adds playback URLs or fetches anything.
    """

    def __init__(self, path: Path | None = None):
        self.path = Path(path) if path is not None else Path(__file__).with_name("catalog.json")
        with self.path.open(encoding="utf-8") as handle:
            data = json.load(handle)
        if not isinstance(data, list):
            raise ValueError("catalog JSON root must be a list of tracks")
        self.tracks: list[dict] = []
        self.by_id: dict[str, dict] = {}
        for index, raw_track in enumerate(data):
            track = _validate_track(raw_track, index)
            if track["id"] in self.by_id:
                raise ValueError(f"duplicate catalog track id: {track['id']}")
            self.tracks.append(track)
            self.by_id[track["id"]] = track

    def get(self, id: str) -> dict:
        """Return a known metadata record, or raise KeyError."""
        return self.by_id[id]

    def candidates(self, available_track_ids: list[str] | None = None) -> list[dict]:
        if available_track_ids is None:
            return list(self.tracks)
        allowed = set(available_track_ids)
        return [track for track in self.tracks if track["id"] in allowed]

    def search(
        self, query: str, available_track_ids: list[str] | None = None, limit: int = 10
    ) -> list[dict]:
        """Fuzzy metadata search, constrained by the same playback allowlist.

        Empty queries return the first allowed records; ties are stable. Search
        covers IDs, titles, artists, albums, genres, subgenres, moods and eras.
        """
        if limit <= 0:
            return []
        candidates = self.candidates(available_track_ids)
        needle = normalize_text(query)
        if not needle:
            return candidates[:limit]
        query_tokens = needle.split()
        matches = []
        for index, track in enumerate(candidates):
            fields = [
                track["id"], track["title"], track["artist"], track["album"],
                track["genre"], track["era"], *track["subgenres"], *track["moods"],
                f"{track['artist']} {track['title']}",
            ]
            normalized = [normalize_text(field) for field in fields]
            exact = needle in normalized
            substring = any(needle in field for field in normalized)
            field_score = max(SequenceMatcher(None, needle, field).ratio() for field in normalized)
            tokens = {token for field in normalized for token in field.split()}
            token_score = sum(
                max(SequenceMatcher(None, query_token, token).ratio() for token in tokens)
                for query_token in query_tokens
            ) / len(query_tokens)
            score = max(field_score, 0.92 * token_score, 0.95 if substring else 0.0)
            if exact:
                score = 1.0
            if score >= 0.60:
                matches.append((score, index, track))
        matches.sort(key=lambda match: (-match[0], match[1]))
        return [track for _, _, track in matches[:limit]]
