package com.music.bitchord.data.personalization

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class SessionEnd { AUTO_ADVANCE, AUTO_REPEAT, MANUAL_SKIP, MANUAL_REPLAY, REPLACED, ERROR, STOPPED, SCOPE_CHANGED }
enum class SessionTransition { AUTO_ADVANCE, AUTO_REPEAT, OTHER }
enum class ListeningIntent { SKIP, REPLAY }

data class SessionTrack(val id: String, val entryId: String?, val collectionId: String? = null)

@Serializable
data class ListeningSessionRecord(
    val id: String,
    val trackId: String,
    val queueEntryId: String?,
    val collectionId: String?,
    val startedAt: Long,
    val updatedAt: Long,
    val revision: Long,
    val heardMs: Long,
    val uniqueHeardMs: Long,
    val durationMs: Long,
    val intentionalReplay: Boolean,
    val ended: SessionEnd? = null,
)

/** Prospective recommendation evidence. This never increments Replay's play counters. */
class ListeningSessionTracker(
    private val listenerScope: () -> String,
    private val monotonicMs: () -> Long,
    private val wallClockMs: () -> Long,
    private val checkpoint: (String, ListeningSessionRecord) -> Unit,
) {
    private class Active(val scope: String, val track: SessionTrack, val started: Long, val replay: Boolean) {
        val id = UUID.randomUUID().toString()
        var heard = 0L
        var duration = 0L
        var revision = 0L
        var lastCheckpoint = 0L
        val intervals = mutableListOf<LongRange>()
    }
    private data class Intent(val scope: String, val key: String, val kind: ListeningIntent, val at: Long)
    private var active: Active? = null
    private var pending: Intent? = null
    private var playing = false
    private var lastMono = 0L
    private var lastPosition = 0L
    private var speed = 1f

    @Synchronized
    fun transition(track: SessionTrack?, positionMs: Long, durationMs: Long, isPlaying: Boolean,
        cause: SessionTransition = SessionTransition.OTHER) {
        val old = active
        val scope = listenerScope()
        if (old != null && old.scope == scope && track != null && key(old.track) == key(track) &&
            cause == SessionTransition.OTHER) return
        val intent = validIntent(old)
        // The account switch time is unknown; do not attribute the unsampled interval to either listener.
        if (old != null && old.scope != scope) { playing = false; anchor(lastPosition) }
        val end = when {
            old != null && old.scope != scope -> SessionEnd.SCOPE_CHANGED
            cause == SessionTransition.AUTO_REPEAT -> SessionEnd.AUTO_REPEAT
            cause == SessionTransition.AUTO_ADVANCE -> SessionEnd.AUTO_ADVANCE
            intent == ListeningIntent.SKIP -> SessionEnd.MANUAL_SKIP
            else -> SessionEnd.REPLACED
        }
        finish(end)
        if (track != null) begin(scope, track, positionMs, durationMs, isPlaying, false)
    }

    @Synchronized
    fun playing(track: SessionTrack?, isPlaying: Boolean, positionMs: Long, durationMs: Long, playbackSpeed: Float = 1f) {
        ensureTrack(track, positionMs, durationMs, isPlaying)
        accumulate(positionMs)
        active?.let { if (durationMs > 0) it.duration = durationMs }
        playing = isPlaying
        speed = playbackSpeed.takeIf { it.isFinite() && it > 0 } ?: 1f
        anchor(positionMs)
        if (!isPlaying) emit()
    }

    @Synchronized
    fun sample(track: SessionTrack, positionMs: Long, durationMs: Long, playbackSpeed: Float = 1f) {
        ensureTrack(track, positionMs, durationMs, true)
        accumulate(positionMs)
        active?.let { if (durationMs > 0) it.duration = durationMs }
        playing = true
        speed = playbackSpeed.takeIf { it.isFinite() && it > 0 } ?: 1f
        anchor(positionMs)
        active?.let { if (monotonicMs() - it.lastCheckpoint >= CHECKPOINT_MS) emit() }
    }

    @Synchronized
    fun intent(kind: ListeningIntent) {
        active?.let { pending = Intent(it.scope, key(it.track), kind, monotonicMs()) }
    }

    /** Only a command-marked seek back to the start is an intentional replay. */
    @Synchronized
    fun seek(oldPositionMs: Long, newPositionMs: Long) {
        accumulate(oldPositionMs)
        val previous = active
        if (previous != null && previous.scope == listenerScope() &&
            validIntent(previous) == ListeningIntent.REPLAY && oldPositionMs >= 10_000 && newPositionMs <= 1_000) {
            val wasPlaying = playing
            val duration = previous.duration
            finish(SessionEnd.MANUAL_REPLAY)
            begin(previous.scope, previous.track, newPositionMs, duration, wasPlaying, true)
        }
        pending = null
        anchor(newPositionMs)
    }

    @Synchronized
    fun finish(reason: SessionEnd) {
        accumulate(lastPosition)
        emit(reason)
        active = null
        pending = null
        playing = false
    }

    private fun ensureTrack(track: SessionTrack?, position: Long, duration: Long, isPlaying: Boolean) {
        val old = active
        if (track != null && (old == null || old.scope != listenerScope() || key(old.track) != key(track))) {
            transition(track, position, duration, isPlaying)
        }
    }

    private fun begin(scope: String, track: SessionTrack, position: Long, duration: Long, isPlaying: Boolean, replay: Boolean) {
        active = Active(scope, track, wallClockMs(), replay).also {
            it.duration = duration.coerceAtLeast(0)
            it.lastCheckpoint = monotonicMs()
        }
        playing = isPlaying
        anchor(position)
    }

    private fun validIntent(session: Active?): ListeningIntent? = pending?.takeIf {
        session != null && it.scope == session.scope && it.key == key(session.track) &&
            monotonicMs() - it.at in 0..2_000
    }?.kind

    private fun accumulate(position: Long) {
        val session = active ?: return
        val elapsed = (monotonicMs() - lastMono).coerceIn(0, 8_000)
        if (playing && elapsed > 0) {
            session.heard += elapsed
            val distance = position - lastPosition
            // Seeks never fill the gap. Coverage is a conservative union of observed intervals.
            if (distance > 0 && distance <= elapsed * speed * 1.5 + 500) {
                val start = lastPosition.coerceAtLeast(0)
                val end = if (session.duration > 0) position.coerceAtMost(session.duration) else position
                if (end > start) addInterval(session, start until end)
            }
        }
        anchor(position)
    }

    private fun addInterval(session: Active, incoming: LongRange) {
        var start = incoming.first
        var end = incoming.last
        val kept = mutableListOf<LongRange>()
        session.intervals.forEach { range ->
            if (range.last + 1 < start || range.first > end + 1) kept += range
            else { start = minOf(start, range.first); end = maxOf(end, range.last) }
        }
        kept += start..end
        session.intervals.clear()
        // Retain a bounded conservative subset if someone repeatedly seeks to isolated positions.
        session.intervals += kept.sortedBy { it.first }.take(256)
    }

    private fun emit(ended: SessionEnd? = null) {
        val session = active ?: return
        if (session.heard <= 0) return
        session.revision++
        session.lastCheckpoint = monotonicMs()
        checkpoint(session.scope, ListeningSessionRecord(
            session.id, session.track.id, session.track.entryId, session.track.collectionId,
            session.started, wallClockMs(), session.revision, session.heard,
            session.intervals.sumOf { it.last - it.first + 1 }, session.duration, session.replay, ended,
        ))
    }

    private fun anchor(position: Long) { lastMono = monotonicMs(); lastPosition = position.coerceAtLeast(0) }
    private fun key(track: SessionTrack) = track.entryId ?: track.id
    private companion object { const val CHECKPOINT_MS = 30_000L }
}
