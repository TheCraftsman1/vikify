"""Validated public request models. Responses are metadata-only JSON records."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class RequestModel(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True, allow_inf_nan=False)


class StartSession(RequestModel):
    client_tz: str = Field(default="UTC", min_length=1, max_length=80)
    local_hour: int | None = Field(default=None, ge=0, le=23)
    seed_genres: list[str] = Field(default_factory=list, max_length=20)
    seed_artists: list[str] = Field(default_factory=list, max_length=20)
    persona_id: Literal["midnight_fm", "hype_coach", "deep_crate_digger"] = "midnight_fm"
    available_track_ids: list[str] | None = Field(default=None, max_length=10000)

    @field_validator("seed_genres", "seed_artists", "available_track_ids")
    @classmethod
    def safe_tags(cls, value):
        if value is not None and any(not tag.strip() or len(tag) > 160 for tag in value):
            raise ValueError("Track IDs and seed tags must be nonempty and at most 160 characters")
        return list(dict.fromkeys(tag.strip() for tag in value)) if value is not None else None


class FeedbackRequest(RequestModel):
    track_id: str = Field(min_length=1, max_length=160)
    event_type: Literal["like", "skip", "complete", "replay"]
    listened_ms: int = Field(ge=0, le=86400000)
    track_duration_ms: int = Field(gt=0, le=86400000)
    event_id: str | None = Field(default=None, pattern=r"^[A-Za-z0-9_-]{1,128}$")
    playback_revision: int | None = Field(default=None, ge=0)


class DjButtonRequest(RequestModel):
    request_id: str | None = Field(default=None, pattern=r"^[A-Za-z0-9_-]{1,128}$")


class VoiceRequest(DjButtonRequest):
    transcript: str = Field(min_length=1, max_length=1000)
    current_track_id: str | None = Field(default=None, min_length=1, max_length=160)
    current_position_ms: int = Field(default=0, ge=0, le=86400000)


class TtsRequest(RequestModel):
    # Clients cannot use this as an arbitrary paid text-to-speech relay.
    session_id: str = Field(pattern=r"^[a-f0-9]{32}$")
    cue_id: str = Field(pattern=r"^[a-f0-9]{32}$")
