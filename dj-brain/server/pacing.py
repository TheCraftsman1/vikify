"""Commentary cadence is deterministic and independent of script generation."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class SpeechDecision:
    speech_mode: str
    max_sentences: int

    @property
    def should_speak(self) -> bool:
        return self.max_sentences > 0


def decide(session: dict, trigger: str = "TRACK_ADVANCE", *, next_number: int | None = None) -> SpeechDecision:
    if trigger == "SESSION_START":
        return SpeechDecision("OPENING_SET", 3)
    if trigger in ("DJ_BUTTON", "VOICE_INTENT"):
        return SpeechDecision("DIRECT_RESPONSE", 2)
    if session.get("pending_auto_pivot") or trigger == "SKIP_CLUSTER":
        return SpeechDecision("COURSE_CORRECT", 2)
    number = next_number if next_number is not None else session.get("tracks_started", 0) + 1
    # The fourth track is the spoken bridge, as in the requested cadence check.
    # Tracks two and three remain uninterrupted; a new arc starts at track five.
    if number % 4 == 0:
        return SpeechDecision("SET_TRANSITION", 2)
    last_callout = session.get("last_callout_track", 0)
    if number % 4 == 1 and session.get("completion_streak", 0) >= 6 and number - last_callout >= 8:
        return SpeechDecision("VIBE_LOCK_CALLOUT", 1)
    return SpeechDecision("SILENT_FLOW", 0)
