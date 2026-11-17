"""JSON-only preference learning and contextual Beta Thompson sampling.

No storage, configuration, API, or network dependencies. ``learn`` mutates its
profile; all other public operations are read-only (apart from consuming RNG).
Call learn once per feedback observation, with actual dwell, not a fabricated
completion duration. Repeated cumulative dwell observations cannot be deduped
without event IDs, so callers must deduplicate those before calling this layer.
"""

from __future__ import annotations

import math
import random
from copy import deepcopy
from datetime import datetime, timezone

from .acoustics import VECTOR_KEYS

BUCKETS = ("morning", "afternoon", "evening", "late_night")
DEFAULT_VECTORS = {
    "morning": {"energy": 0.42, "valence": 0.60, "danceability": 0.38, "acousticness": 0.62, "tempo_norm": 0.42},
    "afternoon": {"energy": 0.66, "valence": 0.65, "danceability": 0.66, "acousticness": 0.28, "tempo_norm": 0.62},
    "evening": {"energy": 0.62, "valence": 0.55, "danceability": 0.60, "acousticness": 0.30, "tempo_norm": 0.58},
    "late_night": {"energy": 0.30, "valence": 0.42, "danceability": 0.35, "acousticness": 0.65, "tempo_norm": 0.30},
}
EMA_RATE = 0.12
RECENT_RING_SIZE = 40
EPISODE_LIMIT = 200
_DIMENSION_WEIGHTS = {"genres": 0.45, "artists": 0.40, "eras": 0.15}
_GLOBAL_WEIGHT = 0.65


def _now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _empty_affinities() -> dict:
    return {"genres": {}, "artists": {}, "eras": {}}


def _empty_stats() -> dict:
    return {
        "events": 0,
        "positive_events": 0,
        "negative_events": 0,
        "neutral_events": 0,
        "event_counts": {},
        "total_reward": 0.0,
    }


def _nonnegative(value: object) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        return 0.0
    return max(0.0, float(value))


def _validate_bucket(bucket: str) -> None:
    if bucket not in BUCKETS:
        raise ValueError(f"unknown circadian bucket: {bucket!r}")


def new_profile(uid: str) -> dict:
    if not isinstance(uid, str) or not uid.strip():
        raise ValueError("uid must be a nonempty string")
    return {
        "uid": uid,
        "created_at": _now(),
        "total_listen_sec": 0.0,
        "global_affinities": _empty_affinities(),
        "circadian_affinities": {bucket: _empty_affinities() for bucket in BUCKETS},
        "circadian_vectors": deepcopy(DEFAULT_VECTORS),
        "recent_track_ring_buffer": [],
        "spoken_facts_history": [],
        "episodes": [],
        "stats": _empty_stats(),
    }


def bucket_for_hour(hour: int) -> str:
    """Bucket a local clock hour; timezone conversion belongs to the caller."""
    if not isinstance(hour, int) or isinstance(hour, bool) or not 0 <= hour <= 23:
        raise ValueError("hour must be an integer from 0 through 23")
    if 5 <= hour <= 11:
        return "morning"
    if 12 <= hour <= 16:
        return "afternoon"
    if 17 <= hour <= 21:
        return "evening"
    return "late_night"


def target_for(profile: dict, bucket: str) -> dict:
    """Return an independent 5D target, with defaults for older profiles."""
    _validate_bucket(bucket)
    stored = profile.get("circadian_vectors", {}).get(bucket, {})
    target = {}
    for key in VECTOR_KEYS:
        value = stored.get(key, DEFAULT_VECTORS[bucket][key])
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
            value = DEFAULT_VECTORS[bucket][key]
        target[key] = max(0.0, min(1.0, float(value)))
    return target


def reward_for(event_type, listened_ms, track_duration_ms) -> float:
    """Preference evidence from explicit intent and *actual* listening.

    A completion label by itself has no value. Early skips (<8 seconds) are
    strong negatives; skips at 15%-65% are weak fatigue signals. A true >=80%
    dwell earns completion credit even if the caller uses another event label.
    """
    event = str(event_type).strip().casefold()
    listened = _nonnegative(listened_ms)
    duration = _nonnegative(track_duration_ms)
    ratio = min(1.0, listened / duration) if duration > 0 else 0.0
    if event in ("like", "replay"):
        return 1.5
    if event == "skip":
        if listened < 8000:
            return -1.0
        if 0.15 <= ratio <= 0.65:
            return -0.25
    if ratio >= 0.80:
        return 0.7
    return 0.0


def _matching_key(entries: dict, name: str) -> str:
    if name in entries:
        return name
    normalized = name.strip().casefold()
    return next((key for key in entries if key.strip().casefold() == normalized), name)


def _parameters(entries: dict, name: str) -> tuple[float, float]:
    prior = entries.get(_matching_key(entries, name), {})
    alpha = _nonnegative(prior.get("alpha", 1.0)) or 1.0
    beta = _nonnegative(prior.get("beta", 1.0)) or 1.0
    return alpha, beta


def _dimensions(track: dict) -> list[tuple[str, str]]:
    return [
        (dimension, track[field])
        for dimension, field in (("genres", "genre"), ("artists", "artist"), ("eras", "era"))
        if isinstance(track.get(field), str) and track[field].strip()
    ]


def _effective_prior(profile: dict, dimension: str, name: str, bucket: str) -> tuple[float, float]:
    global_entries = profile.get("global_affinities", {}).get(dimension, {})
    local_entries = profile.get("circadian_affinities", {}).get(bucket, {}).get(dimension, {})
    global_alpha, global_beta = _parameters(global_entries, name)
    local_alpha, local_beta = _parameters(local_entries, name)
    # Blend evidence above the common Beta(1, 1) base, not two copies of it.
    alpha = 1.0 + _GLOBAL_WEIGHT * (global_alpha - 1.0) + (1 - _GLOBAL_WEIGHT) * (local_alpha - 1.0)
    beta = 1.0 + _GLOBAL_WEIGHT * (global_beta - 1.0) + (1 - _GLOBAL_WEIGHT) * (local_beta - 1.0)
    return alpha, beta


def sample_track(profile: dict, track: dict, bucket: str, rng: random.Random) -> float:
    """Weighted contextual Thompson draw in [0, 1], without profile mutation."""
    _validate_bucket(bucket)
    dimensions = _dimensions(track)
    if not dimensions:
        return rng.betavariate(1.0, 1.0)
    total_weight = sum(_DIMENSION_WEIGHTS[dimension] for dimension, _ in dimensions)
    return sum(
        _DIMENSION_WEIGHTS[dimension] * rng.betavariate(*_effective_prior(profile, dimension, name, bucket))
        for dimension, name in dimensions
    ) / total_weight


def track_uncertainty(profile: dict, track: dict, bucket: str) -> float:
    """Posterior standard deviation relative to an unseen Beta(1, 1) track."""
    _validate_bucket(bucket)
    dimensions = _dimensions(track)
    if not dimensions:
        return 1.0
    total_weight = sum(_DIMENSION_WEIGHTS[dimension] for dimension, _ in dimensions)
    result = 0.0
    for dimension, name in dimensions:
        alpha, beta = _effective_prior(profile, dimension, name, bucket)
        variance = alpha * beta / ((alpha + beta) ** 2 * (alpha + beta + 1))
        result += _DIMENSION_WEIGHTS[dimension] * min(1.0, math.sqrt(12 * variance))
    return result / total_weight


def learn(profile, track, bucket, event_type, listened_ms, track_duration_ms) -> dict:
    """Mutate a profile and return a JSON-serializable audit delta.

    Positive examples use a 0.12 EMA (completion credit scales it to 0.084).
    Negative acoustic repulsion is deliberately weak and bounded. A late skip
    changes only genre priors and acoustic fatigue, never the artist or era.
    Priors are keyed by their display names, matched case-insensitively.
    """
    _validate_bucket(bucket)
    event = str(event_type).strip().casefold()
    reward = reward_for(event, listened_ms, track_duration_ms)
    before = target_for(profile, bucket)
    after = dict(before)
    features = track.get("vector", {})
    if reward:
        rate = EMA_RATE * min(1.0, abs(reward)) * (1.0 if reward > 0 else 0.25)
        for key in VECTOR_KEYS:
            feature = features.get(key, before[key])
            if isinstance(feature, bool) or not isinstance(feature, (int, float)) or not math.isfinite(feature):
                feature = before[key]
            feature = max(0.0, min(1.0, float(feature)))
            direction = feature - before[key] if reward > 0 else before[key] - feature
            after[key] = max(0.0, min(1.0, before[key] + rate * direction))
    profile.setdefault("circadian_vectors", {})[bucket] = after

    changed_priors = []
    if reward:
        global_affinities = profile.setdefault("global_affinities", _empty_affinities())
        local_affinities = profile.setdefault("circadian_affinities", {}).setdefault(bucket, _empty_affinities())
        for scope, affinities in (("global", global_affinities), ("circadian", local_affinities)):
            for dimension, name in _dimensions(track):
                if reward == -0.25 and dimension != "genres":
                    continue
                entries = affinities.setdefault(dimension, {})
                key = _matching_key(entries, name)
                alpha, beta = _parameters(entries, key)
                prior_before = {"alpha": alpha, "beta": beta}
                prior_after = {"alpha": alpha + max(0.0, reward), "beta": beta + max(0.0, -reward)}
                entries[key] = prior_after
                change = {
                    "scope": scope, "dimension": dimension, "name": key,
                    "before": prior_before, "after": dict(prior_after),
                }
                if scope == "circadian":
                    change["bucket"] = bucket
                changed_priors.append(change)

    listened = _nonnegative(listened_ms)
    duration = _nonnegative(track_duration_ms)
    actual_ms = min(listened, duration) if duration > 0 else listened
    profile["total_listen_sec"] = _nonnegative(profile.get("total_listen_sec", 0)) + actual_ms / 1000
    stats = profile.setdefault("stats", _empty_stats())
    stats["events"] = stats.get("events", 0) + 1
    outcome = "positive_events" if reward > 0 else "negative_events" if reward < 0 else "neutral_events"
    stats[outcome] = stats.get(outcome, 0) + 1
    stats["total_reward"] = stats.get("total_reward", 0.0) + reward
    counts = stats.setdefault("event_counts", {})
    counts[event] = counts.get(event, 0) + 1

    track_id = track.get("id")
    if isinstance(track_id, str) and track_id:
        ring = profile.setdefault("recent_track_ring_buffer", [])
        ring[:] = [entry for entry in ring if entry != track_id]
        ring.append(track_id)
        del ring[:-RECENT_RING_SIZE]
    delta = {
        "track_id": track_id,
        "event_type": event,
        "reward": reward,
        "bucket": bucket,
        "listened_sec": actual_ms / 1000,
        "vector_before": before,
        "vector_after": dict(after),
        "changed_priors": changed_priors,
    }
    episodes = profile.setdefault("episodes", [])
    episodes.append({"at": _now(), **deepcopy(delta)})
    del episodes[:-EPISODE_LIMIT]
    return delta


def _beta_fraction(alpha: float, beta: float, x: float) -> float:
    """Continued fraction for the regularized incomplete beta function."""
    tiny = 1e-300
    qab, qap, qam = alpha + beta, alpha + 1, alpha - 1
    c = 1.0
    d = 1 - qab * x / qap
    if abs(d) < tiny:
        d = tiny
    d = 1 / d
    result = d
    for iteration in range(1, 201):
        twice = 2 * iteration
        term = iteration * (beta - iteration) * x / ((qam + twice) * (alpha + twice))
        d = 1 + term * d
        c = 1 + term / c
        if abs(d) < tiny:
            d = tiny
        if abs(c) < tiny:
            c = tiny
        d = 1 / d
        result *= d * c
        term = -(alpha + iteration) * (qab + iteration) * x / ((alpha + twice) * (qap + twice))
        d = 1 + term * d
        c = 1 + term / c
        if abs(d) < tiny:
            d = tiny
        if abs(c) < tiny:
            c = tiny
        d = 1 / d
        change = d * c
        result *= change
        if abs(change - 1) < 3e-12:
            break
    return result


def _beta_cdf(x: float, alpha: float, beta: float) -> float:
    if x <= 0:
        return 0.0
    if x >= 1:
        return 1.0
    front = math.exp(
        math.lgamma(alpha + beta) - math.lgamma(alpha) - math.lgamma(beta)
        + alpha * math.log(x) + beta * math.log1p(-x)
    )
    if x < (alpha + 1) / (alpha + beta + 2):
        value = front * _beta_fraction(alpha, beta, x) / alpha
    else:
        value = 1 - front * _beta_fraction(beta, alpha, 1 - x) / beta
    return max(0.0, min(1.0, value))


def _beta_quantile(probability: float, alpha: float, beta: float) -> float:
    low, high = 0.0, 1.0
    for _ in range(60):
        middle = (low + high) / 2
        if _beta_cdf(middle, alpha, beta) < probability:
            low = middle
        else:
            high = middle
    return (low + high) / 2


def _ranked_priors(entries: dict) -> list[dict]:
    ranked = []
    for name in entries:
        alpha, beta = _parameters(entries, name)
        evidence = max(0.0, alpha + beta - 2)
        ranked.append({
            "name": name,
            "alpha": alpha,
            "beta": beta,
            "mean": alpha / (alpha + beta),
            "evidence": evidence,
            "confidence": "high" if evidence >= 12 else "medium" if evidence >= 3 else "low",
            "credible_interval": {
                "level": 0.90,
                "lower": _beta_quantile(0.05, alpha, beta),
                "upper": _beta_quantile(0.95, alpha, beta),
                "method": "equal-tailed Beta posterior, numerical quantiles",
            },
        })
    ranked.sort(key=lambda item: (-item["mean"], -item["evidence"], item["name"].casefold()))
    return ranked[:10]


def inspect_profile(profile: dict) -> dict:
    """Independent profile view with ranked posterior summaries and audit log.

    Confidence labels describe feedback evidence, not objective taste certainty.
    Intervals are credible intervals for the heuristic Beta model, not measured
    recommendation accuracy. Recent IDs are oldest-to-newest; logs are bounded.
    """
    result = deepcopy(profile)
    affinities = profile.get("global_affinities", {})
    result["top_genres"] = _ranked_priors(affinities.get("genres", {}))
    result["top_artists"] = _ranked_priors(affinities.get("artists", {}))
    result["confidence_note"] = "Labels reflect effective feedback evidence; intervals describe the heuristic Beta posterior."
    result["episodic_log"] = deepcopy(profile.get("episodes", []))
    result.setdefault("stats", _empty_stats())
    return result
