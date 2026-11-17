"""Small, deterministic acoustic heuristics, not measured audio analysis.

All five dimensions are normalized to [0, 1] in the catalog. Harmonic flow is
only advisory: its inputs in the demo catalog are illustrative estimates.
"""

from __future__ import annotations

import math
import re

VECTOR_KEYS = ("energy", "valence", "danceability", "acousticness", "tempo_norm")


def _finite(value: object, default: float = 0.0) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return default
    return float(value) if math.isfinite(value) else default


def cosine_similarity(v1: dict, v2: dict) -> float:
    """5D cosine in [-1, 1]; absent/invalid coordinates are zero.

    A zero-length vector has similarity zero, including with another zero
    vector. Only the five named acoustic coordinates participate.
    """
    left = [_finite(v1.get(key)) for key in VECTOR_KEYS]
    right = [_finite(v2.get(key)) for key in VECTOR_KEYS]
    # Scaling avoids overflow while preserving the angle for finite inputs.
    left_scale = max(map(abs, left))
    right_scale = max(map(abs, right))
    if left_scale == 0 or right_scale == 0:
        return 0.0
    left = [value / left_scale for value in left]
    right = [value / right_scale for value in right]
    dot = math.fsum(a * b for a, b in zip(left, right))
    norm = math.sqrt(math.fsum(a * a for a in left) * math.fsum(b * b for b in right))
    return max(-1.0, min(1.0, dot / norm))


def _camelot(value: object) -> tuple[int, str] | None:
    if not isinstance(value, str):
        return None
    match = re.fullmatch(r"([1-9]|1[0-2])([AB])", value.strip().upper())
    return (int(match[1]), match[2]) if match else None


def harmonic_flow(prev: dict | None, candidate: dict) -> float:
    """Blend Camelot compatibility (65%) and tempo compatibility (35%).

    Same key is best; same-letter neighbors wrap 12 <-> 1; relative major/
    minor is the same number with the other letter. Tempo differences up to
    12 BPM receive useful credit, including half-/double-time equivalence.
    Unknown metadata contributes neutral 0.5 rather than an error.
    """
    if prev is None:
        return 0.5
    first_key = _camelot(prev.get("camelot_key"))
    next_key = _camelot(candidate.get("camelot_key"))
    key_score = 0.5
    if first_key is not None and next_key is not None:
        first_number, first_letter = first_key
        next_number, next_letter = next_key
        distance = min(abs(first_number - next_number), 12 - abs(first_number - next_number))
        if first_key == next_key:
            key_score = 1.0
        elif first_letter == next_letter and distance == 1:
            key_score = 0.90
        elif first_number == next_number:
            key_score = 0.88
        else:
            key_score = 0.15

    first_bpm = _finite(prev.get("bpm"))
    next_bpm = _finite(candidate.get("bpm"))
    tempo_score = 0.5
    if first_bpm > 0 and next_bpm > 0:
        difference = min(
            abs(first_bpm - next_bpm),
            abs(first_bpm - next_bpm / 2),
            abs(first_bpm / 2 - next_bpm),
        )
        if difference <= 12:
            tempo_score = 1.0 - difference / 24
        else:
            tempo_score = max(0.0, 0.5 - (difference - 12) / 36)
    return 0.65 * key_score + 0.35 * tempo_score


def arc_target(target: dict, position: int) -> dict:
    """Copy a target into a four-position anchor/momentum/discovery/bridge arc.

    Position 1 adds 0.10 energy. Discovery stays close to the requested mood;
    selection, rather than a drastic acoustic shift, supplies its novelty.
    Longer sets repeat the four-position arc. Inputs are never mutated.
    """
    if not isinstance(position, int) or isinstance(position, bool) or position < 0:
        raise ValueError("position must be a nonnegative integer")
    result = {key: max(0.0, min(1.0, _finite(target.get(key), 0.5))) for key in VECTOR_KEYS}
    offsets = (
        {},
        {"energy": 0.10, "danceability": 0.04},
        {"energy": 0.04, "valence": 0.03},
        {"energy": -0.05, "acousticness": 0.05, "tempo_norm": -0.03},
    )[position % 4]
    for key, offset in offsets.items():
        result[key] = max(0.0, min(1.0, result[key] + offset))
    return result
