package com.music.vikify.data.personalization

import android.os.SystemClock
import com.music.vikify.data.DebugLog
import com.music.vikify.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Player callbacks stay cheap; durable checkpoints use an independent IO writer. */
object AndroidListeningEvidence {
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tracker: ListeningSessionTracker? = null

    fun install(store: ListeningEvidenceStore) {
        tracker = ListeningSessionTracker(AndroidLocalMixes::scope, SystemClock::elapsedRealtime,
            System::currentTimeMillis) { scope, record ->
            writer.launch {
                try { store.record(scope, record) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { DebugLog.w("ListeningEvidence", "Could not save session checkpoint", failure) }
            }
        }
    }

    fun transition(song: Song?, position: Long, duration: Long, playing: Boolean, cause: SessionTransition) =
        tracker?.transition(song?.sessionTrack(), position, duration, playing, cause)

    fun playing(song: Song?, playing: Boolean, position: Long, duration: Long, speed: Float) =
        tracker?.playing(song?.sessionTrack(), playing, position, duration, speed)

    fun sample(song: Song, position: Long, duration: Long, speed: Float) =
        tracker?.sample(song.sessionTrack(), position, duration, speed)

    fun intent(intent: ListeningIntent) = tracker?.intent(intent)
    fun seek(oldPosition: Long, newPosition: Long) = tracker?.seek(oldPosition, newPosition)
    fun finish(reason: SessionEnd) = tracker?.finish(reason)

    private fun Song.sessionTrack() = SessionTrack(videoId, queueEntryId, playbackSourceId)
}
