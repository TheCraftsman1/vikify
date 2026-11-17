"""Inventory-safe four-part recommendation sets without session mutation.

Current and queued tracks are already committed and cannot be scheduled again.
Explicit exclusions and cooldowns are hard, case-insensitive filters. Recent
history is different: replay becomes eligible only after fresher inventory is
exhausted. This module neither creates playback sources nor mutates a session,
profile or catalog; only the supplied RNG advances.
"""

from __future__ import annotations

import random

from .acoustics import arc_target, cosine_similarity, harmonic_flow
from .catalog import CatalogProvider, normalize_text
from .profile import sample_track, target_for, track_uncertainty


def _names(values: list[str] | None) -> set[str]:
    return {normalize_text(value) for value in (values or []) if isinstance(value, str) and value.strip()}


def _record_ids(records: list) -> list[str]:
    """Accept ID rings as well as stored history objects, oldest first."""
    result = []
    for record in records:
        value = record.get("track_id", record.get("id")) if isinstance(record, dict) else record
        if isinstance(value, str) and value:
            result.append(value)
    return result


def _recency(records: list[str]) -> dict[str, float]:
    count = len(records)
    return {track_id: (index + 1) / count for index, track_id in enumerate(records)}


def _eligible(track: dict, filters: dict[str, set[str]], committed: set[str]) -> bool:
    if track["id"] in committed:
        return False
    return not (
        normalize_text(track["genre"]) in filters["genres"]
        or normalize_text(track["artist"]) in filters["artists"]
        or normalize_text(track["era"]) in filters["eras"]
        or bool(_names(track["subgenres"]) & filters["subgenres"])
    )


def _previous_track(catalog: CatalogProvider, session: dict) -> dict | None:
    queue = _record_ids(session.get("queue") or [])
    history = _record_ids(session.get("history") or [])
    current = session.get("current_track_id")
    ids = list(reversed(queue)) + ([current] if isinstance(current, str) else []) + list(reversed(history))
    return next((catalog.by_id[track_id] for track_id in ids if track_id in catalog.by_id), None)


def _tag_boost(track: dict, tags: set[str], seed_genres: set[str], seed_artists: set[str]) -> float:
    genres = {normalize_text(track["genre"]), *_names(track["subgenres"])}
    artist = normalize_text(track["artist"])
    descriptors = genres | _names(track["moods"]) | {artist, normalize_text(track["era"])}
    boost = 0.12 if genres & seed_genres else 0.0
    boost += 0.16 if artist in seed_artists else 0.0
    if tags:
        boost += 0.12 * len(descriptors & tags) / len(tags)
    return boost


def build_set(
    catalog: CatalogProvider,
    profile: dict,
    session: dict,
    size: int = 4,
    rng: random.Random | None = None,
) -> list[dict]:
    """Return at most ``size`` distinct eligible catalog records.

    Order is anchor, momentum, discovery, bridge (repeating for longer sets).
    Score = contextual Thompson sample + 5D cosine + estimated harmonic flow,
    with seed/tag boosts and recency/diversity penalties. Position 2 receives
    an additional posterior-uncertainty bonus. Within each freshness tier,
    prefer unused artists whenever possible; subgenre overlap is a soft cost.

    No allowlist, negative filter or cooldown is relaxed to fill a set. Unknown
    inventory IDs never create candidates. When eligible inventory is empty,
    raise ValueError; a zero-size request simply returns an empty list.
    """
    if not isinstance(size, int) or isinstance(size, bool) or size < 0:
        raise ValueError("size must be a nonnegative integer")
    if size == 0:
        return []
    rng = rng if rng is not None else random.Random()
    bucket = session.get("bucket", "evening")
    default_target = target_for(profile, bucket)
    target = session.get("target_vector") or default_target
    filters = {
        "genres": _names(session.get("exclude_genres")),
        "artists": _names(session.get("exclude_artists")) | _names(session.get("cooldown_artists")),
        "subgenres": _names(session.get("exclude_subgenres")) | _names(session.get("cooldown_subgenres")),
        "eras": _names(session.get("exclude_eras")),
    }
    committed = set(_record_ids(session.get("queue") or []))
    current = session.get("current_track_id")
    if isinstance(current, str):
        committed.add(current)
    remaining = [
        track for track in catalog.candidates(session.get("available_track_ids"))
        if _eligible(track, filters, committed)
    ]
    if not remaining:
        raise ValueError("No playable tracks match the inventory and hard filters")

    global_recent = _record_ids(profile.get("recent_track_ring_buffer") or [])
    session_recent = _record_ids(session.get("history") or [])
    global_recency = _recency(global_recent)
    session_recency = _recency(session_recent)
    tags = _names(session.get("positive_tags"))
    seed_genres = _names(session.get("seed_genres"))
    seed_artists = _names(session.get("seed_artists"))
    selected = []
    used_artists: set[str] = set()
    used_subgenres: set[str] = set()
    genre_counts: dict[str, int] = {}
    previous = _previous_track(catalog, session)

    def freshness(track: dict) -> int:
        if track["id"] in session_recency:
            return 2
        if track["id"] in global_recency:
            return 1
        return 0

    for position in range(min(size, len(remaining))):
        tier = min(freshness(track) for track in remaining)
        pool = [track for track in remaining if freshness(track) == tier]
        unused_artists = [track for track in pool if normalize_text(track["artist"]) not in used_artists]
        if unused_artists:
            pool = unused_artists
        acoustic_target = arc_target(target, position)

        def score(track: dict) -> float:
            subgenres = _names(track["subgenres"])
            genre = normalize_text(track["genre"])
            recency = max(global_recency.get(track["id"], 0.0), session_recency.get(track["id"], 0.0))
            overlap = len(subgenres & used_subgenres) / max(1, len(subgenres))
            value = (
                0.42 * sample_track(profile, track, bucket, rng)
                + 0.33 * cosine_similarity(acoustic_target, track["vector"])
                + 0.17 * harmonic_flow(previous, track)
                - 0.14 * recency
                - 0.08 * overlap
                - 0.035 * genre_counts.get(genre, 0)
                + _tag_boost(track, tags, seed_genres, seed_artists)
            )
            if position % 4 == 2:
                value += 0.14 * track_uncertainty(profile, track, bucket)
            return value

        # Catalog order provides stable tie-breaking for seeded RNG tests.
        chosen = max(pool, key=score)
        selected.append(chosen)
        remaining = [track for track in remaining if track["id"] != chosen["id"]]
        used_artists.add(normalize_text(chosen["artist"]))
        used_subgenres.update(_names(chosen["subgenres"]))
        genre = normalize_text(chosen["genre"])
        genre_counts[genre] = genre_counts.get(genre, 0) + 1
        previous = chosen
    return selected
