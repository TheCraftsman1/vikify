/*
 * Standalone reference, NOT wired into the existing app or a Gradle source set.
 * Requires the dependencies in DjApi.kt plus media3-exoplayer/common:1.11.0.
 * Construct on the main/application looper with the EXISTING music ExoPlayer and
 * an application/service CoroutineScope. Public suspend functions may be called
 * from any dispatcher; close() must be called on main (before releasing music).
 * The host separately owns/closes DjApiClient; this class receives only its API.
 * Cancelling the supplied scope also releases this coordinator's own resources.
 *
 * startSession takes control of the primary PLAYLIST (repeat/shuffle are disabled),
 * but never owns/releases that player or changes its audio-focus configuration.
 * resolveTrack is a host-supplied suspend resolver, called on IO. It MUST map DJ
 * catalog IDs to legitimately available local/licensed inventory and preserve
 * any required DRM/authorization in its MediaItem. Supply available_track_ids;
 * null is appropriate only if the resolver can resolve the entire server catalog.
 * No sample stream URLs, provider keys, or app-specific playback hooks are used.
 *
 * preferServerTts downloads authenticated /v1/tts WAVs (maximum 4 MiB per file),
 * falling back to Android TTS on HTTP 503, IO failure, or the bounded server-TTS
 * timeout. Other HTTP errors are surfaced, not treated as a development bypass.
 * Android fallback uses plain script, NOT SSML markup. Android 11+ hosts using
 * TTS may need a manifest <queries> intent for android.intent.action.TTS_SERVICE.
 * No manifest/Gradle/app changes are made here. Engine/voice availability varies.
 *
 * Music is staged in the PRIMARY playlist before the end. Only an actual forward
 * media-item transition commits /next; the 75%/20-second threshold is prefetch,
 * never a backend advance. HTTP/TTS can fail without stopping preloaded music.
 * Such fallback entries are speculative until /next confirms their IDs. A 409,
 * unexpected revision, external queue takeover, or ID mismatch requires a new
 * session; the coordinator reports it and leaves available music playing.
 *
 * Cue scheduling is best effort: device buffering, cue accuracy, focus, polling,
 * and two independent players preclude sample-accurate/hard timing guarantees.
 * Wire host lifecycle, UI controls and microphone capture explicitly. The SSE
 * transport in DjApiClient is independent; do not issue concurrent SSE commands
 * behind this coordinator's revision tracking. This is not automatic app integration.
 */
package com.music.vikify.dj

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okhttp3.ResponseBody
import retrofit2.HttpException

class DjSynchronizationException(message: String) : IllegalStateException(message)

@androidx.annotation.OptIn(UnstableApi::class)
class DjCoordinator(
    context: Context,
    private val musicPlayer: ExoPlayer,
    private val api: DjApi,
    parentScope: CoroutineScope,
    private val resolveTrack: suspend (TrackDto) -> MediaItem,
    private val preferServerTts: Boolean = false,
    private val onError: (Throwable) -> Unit = {},
    locale: Locale = Locale.getDefault(),
) : AutoCloseable {
    private data class Entry(val track: TrackDto, val item: MediaItem, val sequence: Long) {
        val id: String get() = item.mediaId
    }

    private class Session(
        val id: String,
        var revision: Int,
        var current: Entry,
        val availableIds: Set<String>?,
        var invalidReason: String? = null,
    )

    private data class CueKey(
        val sessionId: String,
        val revision: Int,
        val cueId: String,
        val targetEntryId: String,
    )

    private class CueSlot(
        var key: CueKey,
        val cue: DjCueDto,
        val previousEntry: Entry?,
        val targetTrack: TrackDto,
        val opening: Boolean,
        var file: File? = null,
        var preparation: Job? = null,
    )

    private data class Staged(
        val fromEntryId: String,
        val toEntryId: String,
        val response: NextResponseDto,
        val requestId: String = UUID.randomUUID().toString(),
    )

    private class Transition(
        val to: Entry,
        val prefetch: Staged?,
        val requestId: String = prefetch?.requestId ?: UUID.randomUUID().toString(),
        var expectedRevision: Int? = null,
    )

    private data class OpeningGate(val entryId: String, var leading: Boolean = false)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ownerJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + ownerJob + Dispatchers.Main.immediate)
    private val mutationMutex = Mutex()
    private val files = VoiceFiles(context.applicationContext.cacheDir)
    private val androidTts: AndroidFileTts
    private val voicePlayer: ExoPlayer
    private val instanceId = UUID.randomUUID().toString()
    private val entries = mutableMapOf<String, Entry>()
    private val transitions = ArrayDeque<Transition>()
    private var sequence = 0L
    private var generation = 0L
    private var session: Session? = null
    private var staged: Staged? = null
    private var pendingCue: CueSlot? = null
    private var activeCue: CueSlot? = null
    private var loadedCue: CueSlot? = null
    private var speechJob: Job? = null
    private var speechOwner = 0L
    private var prefetchJob: Job? = null
    private var previewJob: Job? = null
    private var commitJob: Job? = null
    private var pollJob: Job? = null
    private var openingLeadJob: Job? = null
    private var openingGate: OpeningGate? = null
    private var prefetchRetryAt = 0L
    private var forcePrefetchRequested = false
    private var commitRetryAt = 0L
    private var bargeInDepth = 0
    private var internalPlayerChange = false
    private var baseVolume = musicPlayer.volume.coerceIn(0f, 1f)
    private var duckFactor = 1f
    private var lastInternalVolume = Float.NaN
    @Volatile private var closed = false

    private val musicListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (closed) return
            handleTransition(mediaItem, reason)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (closed) return
            if (!isPlaying && openingGate == null) cancelSpeechAndCue()
            updatePolling()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (closed || internalPlayerChange) return
            if (!playWhenReady) {
                cancelOpeningLead()
                cancelSpeechAndCue()
            }
            updatePolling()
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (closed) return
            if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                cancelOpeningLead()
                cancelSpeechAndCue()
            }
            updatePolling()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (closed) return
            if (playbackState == Player.STATE_READY) startOpeningOrMusic()
            updatePolling()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (closed || internalPlayerChange || reason != Player.DISCONTINUITY_REASON_SEEK) return
            cancelOpeningLead()
            cancelSpeechAndCue()
            if (oldPosition.mediaItem?.mediaId == newPosition.mediaItem?.mediaId) {
                invalidatePlanning()
                if (musicPlayer.isPlaying) requestPrefetch(force = true)
            }
        }

        override fun onVolumeChanged(volume: Float) {
            if (closed || abs(volume - lastInternalVolume) < 0.0001f) return
            // A host volume change is a new user base, not a reason to permanently
            // remember the ducked level. Prefer setBaseVolume from host controls.
            baseVolume = volume.coerceIn(0f, 1f)
            applyDuck(duckFactor)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (closed) return
            cancelOpeningLead()
            cancelSpeechAndCue()
            report(error)
        }
    }

    private val voiceListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (closed) return
            if (playbackState == Player.STATE_READY) maybeStartCue()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (closed) return
            cancelSpeechAndCue()
            report(error)
            // A failed opening must not strand the music behind its lead timer.
            finishOpeningLead(playMusic = true)
        }
    }

    init {
        checkMainThread()
        require(musicPlayer.applicationLooper == Looper.getMainLooper()) {
            "The supplied music ExoPlayer must use the main application looper"
        }
        require(parentScope.coroutineContext[Job]?.isActive != false) { "Parent scope is not active" }
        voicePlayer = ExoPlayer.Builder(context.applicationContext)
            .setLooper(musicPlayer.applicationLooper)
            .build()
        voicePlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            false, // Only the existing music player owns audio focus.
        )
        androidTts = AndroidFileTts(context.applicationContext, locale, files)
        voicePlayer.addListener(voiceListener)
        musicPlayer.addListener(musicListener)
        ownerJob.invokeOnCompletion {
            if (Looper.myLooper() == Looper.getMainLooper()) closeOnMain()
            else mainHandler.post { closeOnMain() }
        }
    }

    /** Does not interrupt old music while the new session/first item is resolving. */
    suspend fun startSession(
        request: StartSessionRequest = StartSessionRequest(),
        autoPlay: Boolean = true,
    ): SessionResponseDto = onMain {
        require(request.localHour == null || request.localHour in 0..23)
        invalidatePlanning()
        cancelOpeningLead()
        commitJob?.cancel()
        transitions.clear()
        session = null
        val ticket = ++generation
        mutationMutex.withLock {
            val response = api.startSession(request)
            require(response.sessionId.isNotBlank() && response.playbackRevision >= 0)
            checkCueIdentity(response.cueId, response.djCue)
            checkTicket(ticket)
            val allowed = request.availableTrackIds?.toSet()
            val reservedSequence = ++sequence
            val entryId = "$instanceId:$reservedSequence"
            val slot = CueSlot(
                CueKey(response.sessionId, response.playbackRevision, response.cueId, entryId),
                response.djCue, null, response.track, opening = true,
            )
            installCue(slot)
            val first = try {
                resolveEntry(response.track, allowed, reservedSequence)
            } catch (error: Throwable) {
                cancelSpeechAndCue()
                throw error
            }
            checkTicket(ticket)
            val state = Session(response.sessionId, response.playbackRevision, first, allowed)
            session = state
            entries.clear()
            entries[first.id] = first
            openingGate = if (autoPlay) OpeningGate(first.id) else null
            changePlayer {
                musicPlayer.pause()
                musicPlayer.repeatMode = Player.REPEAT_MODE_OFF
                musicPlayer.shuffleModeEnabled = false
                musicPlayer.setMediaItems(listOf(first.item), true)
                musicPlayer.prepare()
            }
            // If READY was delivered synchronously, the gate has already been used.
            if (musicPlayer.playbackState == Player.STATE_READY) startOpeningOrMusic()
            val preview = response.upNextPreview.ifEmpty {
                // mini_set can include the already selected first item.
                if (response.miniSet.firstOrNull()?.id == response.track.id) response.miniSet.drop(1)
                else response.miniSet
            }
            schedulePreviews(state, first, preview, first.id)
            updatePolling()
            response
        }
    }

    /**
     * Advances immediately when an item is preloaded. Otherwise resolves/stages a
     * prefetch while the CURRENT music continues; it never stops music for HTTP.
     * The transition listener, not this method, performs the /next commit.
     * This does not invent listening statistics; call feedback(SKIP, actualMs).
     */
    suspend fun next(): Unit = onMain {
        val state = requireSession()
        val wasOpening = openingGate != null
        cancelOpeningLead()
        cancelSpeechAndCue()
        if (!musicPlayer.hasNextMediaItem()) {
            mutationMutex.withLock { drainTransitions(state) }
            requestPrefetch(force = true)
            prefetchJob?.join()
        }
        if (session !== state) throw CancellationException("DJ session changed")
        if (!musicPlayer.hasNextMediaItem()) throw IOException("No resolved next item is available")
        changePlayer { musicPlayer.seekToNextMediaItem() }
        if (wasOpening) musicPlayer.play()
        // changePlayer only guards seek invalidation, not media-item transitions.
        if (musicPlayer.playbackState == Player.STATE_ENDED || musicPlayer.playbackState == Player.STATE_IDLE) {
            musicPlayer.prepare()
        }
        updatePolling()
    }

    suspend fun skip(): Unit = next()

    /** Seek/pause controls are also observed when performed directly by the host. */
    suspend fun seekTo(positionMs: Long): Unit = onMain {
        require(positionMs >= 0)
        cancelOpeningLead()
        invalidatePlanning()
        musicPlayer.seekTo(positionMs)
        if (musicPlayer.isPlaying) requestPrefetch(force = true)
    }

    suspend fun pause(): Unit = onMain {
        cancelOpeningLead()
        cancelSpeechAndCue()
        musicPlayer.pause()
    }

    suspend fun resume(): Unit = onMain {
        requireSession()
        musicPlayer.play()
        updatePolling()
    }

    /** Host volume sliders should use this rather than interpreting a ducked level. */
    suspend fun setBaseVolume(volume: Float): Unit = onMain {
        require(volume.isFinite() && volume in 0f..1f)
        baseVolume = volume
        applyDuck(duckFactor)
    }

    suspend fun feedback(
        eventType: FeedbackEventType,
        listenedMs: Long,
        eventId: String = UUID.randomUUID().toString(),
    ): FeedbackResponseDto = onMain {
        require(listenedMs >= 0 && eventId.isNotBlank())
        mutationMutex.withLock {
            val state = requireSession()
            drainTransitions(state)
            val track = state.current.track
            val request = FeedbackRequest(
                track.id, eventType, listenedMs, durationOf(state.current), eventId, state.revision,
            )
            submitFeedback(state, request)
        }
    }

    /** Explicit track IDs allow complete/skip feedback for a just-finished item. */
    suspend fun feedback(request: FeedbackRequest): FeedbackResponseDto = onMain {
        require(request.trackId.isNotBlank() && request.listenedMs >= 0 && request.trackDurationMs > 0)
        mutationMutex.withLock {
            val state = requireSession()
            drainTransitions(state)
            require(state.availableIds == null || request.trackId in state.availableIds)
            submitFeedback(
                state,
                request.copy(
                    eventId = request.eventId ?: UUID.randomUUID().toString(),
                    playbackRevision = request.playbackRevision ?: state.revision,
                ),
            )
        }
    }

    private suspend fun submitFeedback(state: Session, request: FeedbackRequest): FeedbackResponseDto {
        // Even likes can affect queue/cue selection. Never retain old speech.
        invalidatePlanning()
        val response = performMutation(state) { api.feedback(state.id, request) }
        checkRevision(state, response.playbackRevision)
        state.revision = response.playbackRevision
        requestPrefetch(force = true)
        return response
    }

    /** Caller may reuse requestId after its own retry; internal retries keep it stable. */
    suspend fun djButton(requestId: String = UUID.randomUUID().toString()): NextResponseDto = onMain {
        require(requestId.isNotBlank())
        mutationMutex.withLock {
            val state = requireSession()
            drainTransitions(state)
            invalidatePlanning()
            val response = performMutation(state) { api.djButton(state.id, DjButtonRequest(requestId)) }
            validateResponse(state, response)
            checkRevision(state, response.playbackRevision)
            if (transitions.isNotEmpty()) {
                invalidateSession(state, "Music advanced while the DJ button response was in flight")
            }
            state.revision = response.playbackRevision
            if (response.track.id != state.current.track.id) {
                // The mutation already selected this item: do NOT call /next again.
                replaceCurrent(state, resolveAuthoritativeEntry(state, response.track))
            }
            installCue(
                CueSlot(
                    CueKey(state.id, state.revision, response.cueId, state.current.id),
                    response.djCue, null, state.current.track, opening = false,
                ),
            )
            val preview = response.upNextPreview.ifEmpty {
                val queue = response.queue.orEmpty()
                if (queue.firstOrNull()?.id == state.current.track.id) queue.drop(1) else queue
            }
            scheduleReplacementPreview(state, preview)
            response
        }
    }

    /**
     * Returns the action/params for explicit host handling. The protocol does not
     * define arbitrary action strings as Media3 commands. By default queue/head
     * are treated as upcoming inventory, without jumping the current item. For a
     * backend action which ALREADY committed playback, [currentTrackAfterVoice]
     * must return its authoritative current track (for example, its queue head).
     * That item is resolved/adopted without another /next, including same-ID replays.
     * The callback runs on main. Route other transport actions through the public
     * pause/resume/seek/skip controls according to the host's action vocabulary.
     */
    suspend fun voice(
        transcript: String,
        requestId: String = UUID.randomUUID().toString(),
        currentTrackAfterVoice: (VoiceResponseDto) -> TrackDto? = { null },
    ): VoiceResponseDto = onMain {
        require(transcript.isNotBlank() && transcript.length <= 1_000 && requestId.isNotBlank())
        beginBargeInOnMain()
        try {
            mutationMutex.withLock {
                val state = requireSession()
                drainTransitions(state)
                val request = VoiceRequest(
                    transcript, state.current.track.id, musicPlayer.currentPosition.coerceAtLeast(0), requestId,
                )
                invalidatePlanning()
                val response = performMutation(state) { api.voice(state.id, request) }
                if (response.sessionId != state.id || response.playbackRevision < 0) {
                    invalidateSession(state, "Voice response has an invalid session/revision")
                }
                try {
                    checkCueIdentity(response.cueId, response.djCue)
                } catch (_: IllegalArgumentException) {
                    invalidateSession(state, "Voice response has an inconsistent cue identity/timing")
                }
                checkRevision(state, response.playbackRevision)
                if (transitions.isNotEmpty()) {
                    invalidateSession(state, "Music advanced while the voice response was in flight")
                }
                state.revision = response.playbackRevision
                val committedCurrent = try {
                    currentTrackAfterVoice(response)
                } catch (error: Exception) {
                    state.invalidReason = "The host voice playback policy failed after a mutation"
                    throw error
                }
                if (committedCurrent != null) {
                    replaceCurrent(state, resolveAuthoritativeEntry(state, committedCurrent))
                }
                val queue = if (committedCurrent != null && response.queue.firstOrNull()?.id == committedCurrent.id) {
                    response.queue.drop(1)
                } else response.queue
                val upcoming = response.updatedQueueHead?.takeUnless { it.id == committedCurrent?.id }?.let { head ->
                    listOf(head) + if (queue.firstOrNull()?.id == head.id) queue.drop(1) else queue
                } ?: queue
                // Resolve before replacing any playable fallback. At the prefetch
                // threshold, /prefetch supplies the authoritative track/cue tuple.
                scheduleReplacementPreview(state, upcoming)
                installCue(
                    CueSlot(
                        CueKey(state.id, state.revision, response.cueId, state.current.id),
                        response.djCue, null, state.current.track, opening = false,
                    ),
                )
                response
            }
        } finally {
            endBargeInOnMain()
        }
    }

    suspend fun beginBargeIn(): Unit = onMain { beginBargeInOnMain() }
    suspend fun endBargeIn(): Unit = onMain { endBargeInOnMain() }

    private fun beginBargeInOnMain() {
        bargeInDepth++
        cancelSpeechAndCue()
        // Do not leave music paused just because an opening voice was interrupted.
        finishOpeningLead(playMusic = true)
    }

    private fun endBargeInOnMain() {
        if (bargeInDepth > 0) bargeInDepth--
        if (bargeInDepth == 0) maybeStartCue()
    }

    private suspend fun <T> onMain(block: suspend () -> T): T {
        currentCoroutineContext().ensureActive()
        check(!closed) { "DjCoordinator is closed" }
        val task = scope.async {
            check(!closed) { "DjCoordinator is closed" }
            block()
        }
        try {
            return task.await()
        } finally {
            // Public work belongs to BOTH the caller's cancellation and our lifetime.
            if (!task.isCompleted) task.cancel()
        }
    }

    private fun requireSession(): Session {
        check(!closed) { "DjCoordinator is closed" }
        val state = checkNotNull(session) { "Start a DJ session first" }
        state.invalidReason?.let { throw DjSynchronizationException("$it; start a new DJ session") }
        if (musicPlayer.currentMediaItem?.mediaId != state.current.id) {
            invalidateSession(state, "The host replaced the DJ current media item")
        }
        return state
    }

    private fun handleTransition(mediaItem: MediaItem?, reason: Int) {
        val state = session ?: return
        if (mediaItem?.mediaId == state.current.id) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) cancelSpeechAndCue()
            return
        }
        val incoming = mediaItem?.mediaId?.let(entries::get)
        if (incoming == null || incoming.sequence <= state.current.sequence) {
            cancelSpeechAndCue()
            state.invalidReason = "Unexpected/external or backward media-item transition"
            report(DjSynchronizationException(state.invalidReason!!))
            return
        }
        val old = state.current
        val plan = staged?.takeIf { it.fromEntryId == old.id && it.toEntryId == incoming.id }
        staged = null
        prefetchJob?.cancel()
        previewJob?.cancel()
        generation++
        state.current = incoming
        openingGate = null
        openingLeadJob?.cancel()
        openingLeadJob = null
        val incomingCue = activeCue?.takeIf { it.key.targetEntryId == incoming.id }
            ?: pendingCue?.takeIf { it.key.targetEntryId == incoming.id }
        if (incomingCue == null || introDeadline(incomingCue) == 0L) cancelSpeechAndCue()
        transitions.addLast(Transition(incoming, plan))
        ensureCommitWorker()
        maybeStartCue()
        // Retain only current + future music, avoiding unbounded playlist/history.
        scope.launch {
            yield() // avoid reentrant playlist edits inside a Player.Listener callback
            val index = musicPlayer.currentMediaItemIndex
            if (!closed && index > 0 && musicPlayer.currentMediaItem?.mediaId == state.current.id) {
                val past = (0 until index).map { musicPlayer.getMediaItemAt(it).mediaId }
                changePlayer { musicPlayer.removeMediaItems(0, index) }
                past.forEach(entries::remove)
            }
        }
        updatePolling()
    }

    private fun ensureCommitWorker() {
        val state = session ?: return
        if (closed || state.invalidReason != null || transitions.isEmpty() || commitJob?.isActive == true) return
        if (SystemClock.elapsedRealtime() < commitRetryAt) return
        commitJob = scope.launch {
            try {
                mutationMutex.withLock { drainTransitions(state) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                commitRetryAt = SystemClock.elapsedRealtime() + RETRY_BACKOFF_MS
                report(error)
            }
        }
    }

    /** Must be called with mutationMutex held. Music/listener never waits for it. */
    private suspend fun drainTransitions(state: Session) {
        while (session === state && transitions.isNotEmpty()) {
            state.invalidReason?.let { throw DjSynchronizationException(it) }
            val transition = transitions.first()
            val expected = transition.expectedRevision ?: state.revision.also {
                transition.expectedRevision = it
            }
            val response = try {
                retryIdempotent { api.next(state.id, expected, transition.requestId) }
            } catch (error: HttpException) {
                if (error.code() == 409) invalidateSession(state, "Backend rejected the playback revision")
                if (error.code() in 400..499) {
                    invalidateSession(state, "Backend rejected /next (HTTP ${error.code()})")
                }
                throw error
            }
            if (session !== state) throw CancellationException("DJ session changed")
            validateResponse(state, response)
            if (response.playbackRevision <= expected) {
                invalidateSession(state, "The /next response did not advance its expected revision")
            }
            state.revision = response.playbackRevision
            if (response.track.id != transition.to.track.id ||
                (transition.prefetch != null && response.track.id != transition.prefetch.response.track.id)
            ) {
                invalidateSession(state, "Committed track does not match the preloaded/prefetched track")
            }
            val prefetched = transition.prefetch?.response
            if (prefetched != null &&
                (prefetched.playbackRevision != expected || prefetched.cueId != response.cueId ||
                    prefetched.djCue != response.djCue)
            ) {
                // Music is correct, but cached speech no longer describes this revision.
                cancelSpeechAndCue()
            } else {
                listOfNotNull(pendingCue, activeCue).forEach { slot ->
                    if (slot.key.targetEntryId == transition.to.id && slot.key.cueId == response.cueId) {
                        slot.key = slot.key.copy(revision = response.playbackRevision)
                    }
                }
            }
            transitions.removeFirst()
            commitRetryAt = 0
            if (transitions.isEmpty() && state.current.id == transition.to.id && !musicPlayer.hasNextMediaItem()) {
                schedulePreviews(state, state.current, response.upNextPreview, state.current.id)
            }
        }
    }

    private suspend fun <T> retryIdempotent(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val transient = error is IOException ||
                    (error is HttpException && error.code() in 500..599)
                if (!transient || ++attempt >= 3) throw error
                delay(250L * attempt)
            }
        }
    }

    private suspend fun <T> performMutation(state: Session, block: suspend () -> T): T {
        try {
            val response = retryIdempotent(block)
            if (closed || session !== state) throw CancellationException("DJ session changed")
            return response
        } catch (error: CancellationException) {
            if (session === state && !closed) {
                state.invalidReason = "A cancelled mutation may have reached the server"
                cancelSpeechAndCue()
            }
            throw error
        } catch (error: Exception) {
            if (error is HttpException && error.code() in 400..499 && error.code() != 409) {
                // Authentication/validation rejection is not an ambiguous commit.
                forcePrefetchRequested = true
                throw error
            }
            // An exhausted mutation can have committed even when its response was
            // lost. Do not guess its new revision or silently reuse old prefetched TTS.
            state.invalidReason = "A DJ mutation failed or its result is uncertain"
            cancelSpeechAndCue()
            throw error
        }
    }

    private fun requestPrefetch(force: Boolean = false) {
        if (force) forcePrefetchRequested = true
        val state = session ?: return
        if (closed || state.invalidReason != null || prefetchJob?.isActive == true) return
        if (transitions.isNotEmpty()) {
            ensureCommitWorker()
            return
        }
        // A concurrent mutation can change revision while a read is in flight.
        // A forced refresh remains pending and is picked up when playback polls.
        if (mutationMutex.isLocked) return
        if (!forcePrefetchRequested && SystemClock.elapsedRealtime() < prefetchRetryAt) return
        if (staged?.let { it.fromEntryId == state.current.id && it.response.playbackRevision == state.revision } == true) {
            forcePrefetchRequested = false
            return
        }
        forcePrefetchRequested = false
        val from = state.current
        val revision = state.revision
        val ticket = generation
        prefetchJob = scope.launch {
            try {
                val response = try {
                    api.prefetch(state.id) // does NOT advance backend playback
                } catch (error: HttpException) {
                    if (error.code() in 400..499) {
                        invalidateSession(state, "Backend rejected prefetch (HTTP ${error.code()})")
                    }
                    throw error
                }
                if (!isCurrent(state, from, ticket) || state.revision != revision || transitions.isNotEmpty()) return@launch
                validateResponse(state, response)
                if (response.playbackRevision != revision) {
                    invalidateSession(state, "Prefetch returned a different current revision")
                }
                val incoming = resolveEntry(response.track, state.availableIds)
                if (!isCurrent(state, from, ticket) || state.revision != revision || transitions.isNotEmpty()) return@launch
                previewJob?.cancel()
                staged = Staged(from.id, incoming.id, response)
                replaceFuture(state, listOf(incoming))
                installCue(
                    CueSlot(
                        CueKey(state.id, revision, response.cueId, incoming.id),
                        response.djCue, from, incoming.track, opening = false,
                    ),
                )
                schedulePreviews(state, from, response.upNextPreview, incoming.id)
                prefetchRetryAt = 0
                maybeStartCue()
                // If HTTP missed the end, resume only once music is locally staged.
                if (musicPlayer.playbackState == Player.STATE_ENDED && musicPlayer.playWhenReady &&
                    musicPlayer.hasNextMediaItem()
                ) {
                    musicPlayer.seekToNextMediaItem()
                    musicPlayer.prepare()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                prefetchRetryAt = SystemClock.elapsedRealtime() + RETRY_BACKOFF_MS
                report(error) // already staged music remains available
            }
        }
    }

    private fun scheduleReplacementPreview(state: Session, tracks: List<TrackDto>) {
        previewJob?.cancel()
        if (tracks.isEmpty()) {
            requestPrefetch(force = true)
            return
        }
        val from = state.current
        val ticket = generation
        previewJob = scope.launch {
            try {
                val resolved = resolveEntry(tracks.first(), state.availableIds)
                if (!isCurrent(state, from, ticket)) return@launch
                replaceFuture(state, listOf(resolved))
                // Remaining items are appended by a separate job, after this one.
                previewJob = null
                schedulePreviews(state, from, tracks.drop(1), resolved.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                report(error)
                requestPrefetch(force = true)
            }
        }
    }

    private fun schedulePreviews(state: Session, from: Entry, tracks: List<TrackDto>, afterId: String) {
        previewJob?.cancel()
        val ticket = generation
        previewJob = scope.launch {
            try {
                var anchor = afterId
                for (track in tracks.take(MAX_PREVIEW_ITEMS)) {
                    val entry = resolveEntry(track, state.availableIds)
                    if (!isCurrent(state, from, ticket) || lastPlaylistId() != anchor) return@launch
                    if (musicPlayer.mediaItemCount >= MAX_PREVIEW_ITEMS + 2) return@launch
                    entries[entry.id] = entry
                    musicPlayer.addMediaItem(entry.item)
                    anchor = entry.id
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                report(error)
            }
        }
    }

    private suspend fun resolveEntry(track: TrackDto, allowed: Set<String>?, reserved: Long? = null): Entry {
        require(track.id.isNotBlank() && track.durationMs > 0) { "Invalid DJ track metadata" }
        require(allowed == null || track.id in allowed) { "DJ track is outside the client's available inventory" }
        val item = withContext(Dispatchers.IO) { resolveTrack(track) }
        currentCoroutineContext().ensureActive()
        val number = reserved ?: ++sequence
        // Distinct IDs distinguish a legitimate replay from the previous occurrence;
        // buildUpon preserves the resolver's URI, DRM, metadata and custom tag.
        return Entry(track, item.buildUpon().setMediaId("$instanceId:$number").build(), number)
    }

    private fun replaceFuture(state: Session, future: List<Entry>) {
        check(musicPlayer.currentMediaItem?.mediaId == state.current.id)
        val index = musicPlayer.currentMediaItemIndex + 1
        val removed = (index until musicPlayer.mediaItemCount).map { musicPlayer.getMediaItemAt(it).mediaId }
        future.forEach { entries[it.id] = it }
        changePlayer {
            // There is always a resolved replacement before deleting fallback items.
            if (index < musicPlayer.mediaItemCount) musicPlayer.removeMediaItems(index, musicPlayer.mediaItemCount)
            musicPlayer.addMediaItems(future.map { it.item })
        }
        removed.forEach(entries::remove)
    }

    private suspend fun resolveAuthoritativeEntry(state: Session, track: TrackDto): Entry {
        val fromId = state.current.id
        val entry = try {
            resolveEntry(track, state.availableIds)
        } catch (error: Exception) {
            if (session === state) {
                state.invalidReason = "An already-committed track could not be resolved"
                cancelSpeechAndCue()
            }
            throw error
        }
        if (session !== state || state.current.id != fromId || transitions.isNotEmpty()) {
            invalidateSession(state, "Music advanced while an authoritative item was resolving")
        }
        return entry
    }

    private fun replaceCurrent(state: Session, entry: Entry) {
        cancelOpeningLead()
        cancelSpeechAndCue()
        entries.clear()
        entries[entry.id] = entry
        state.current = entry // listener sees this as an authoritative replacement, not /next
        val shouldPlay = musicPlayer.playWhenReady
        changePlayer {
            musicPlayer.setMediaItems(listOf(entry.item), true)
            musicPlayer.prepare()
            musicPlayer.playWhenReady = shouldPlay
        }
    }

    private fun lastPlaylistId(): String = if (musicPlayer.mediaItemCount == 0) ""
    else musicPlayer.getMediaItemAt(musicPlayer.mediaItemCount - 1).mediaId

    private fun isCurrent(state: Session, from: Entry, ticket: Long): Boolean =
        !closed && session === state && state.invalidReason == null && generation == ticket &&
            state.current.id == from.id && musicPlayer.currentMediaItem?.mediaId == from.id

    private fun invalidatePlanning() {
        generation++
        prefetchJob?.cancel()
        prefetchJob = null
        previewJob?.cancel()
        previewJob = null
        staged = null
        prefetchRetryAt = 0
        forcePrefetchRequested = false
        cancelSpeechAndCue()
        // Keep already resolved music as speculative fallback until fresh inventory
        // replaces it. Never keep its old cue or treat it as a fresh prefetch result.
    }

    private fun validateResponse(state: Session, response: NextResponseDto) {
        if (response.sessionId != state.id || response.playbackRevision < 0) {
            invalidateSession(state, "DJ response has an invalid session/revision")
        }
        try {
            checkCueIdentity(response.cueId, response.djCue)
        } catch (_: IllegalArgumentException) {
            invalidateSession(state, "DJ response has an inconsistent cue identity/timing")
        }
    }

    private fun checkCueIdentity(cueId: String, cue: DjCueDto) {
        require(cueId.isNotBlank() && cue.cueId == cueId) { "Inconsistent DJ cue identity" }
        require(cue.timing.maxTalkOverNextIntroMs >= 0 && cue.timing.rampDurationMs >= 0)
        require(cue.timing.duckMusicVolumeTo.isFinite() && cue.timing.duckMusicVolumeTo in 0f..1f)
    }

    private fun checkRevision(state: Session, revision: Int) {
        if (revision < state.revision) invalidateSession(state, "DJ mutation returned an older revision")
    }

    private fun invalidateSession(state: Session, reason: String): Nothing {
        state.invalidReason = reason
        invalidatePlanning()
        transitions.clear()
        throw DjSynchronizationException("$reason; start a new DJ session (preloaded music is unchanged)")
    }

    private fun checkTicket(ticket: Long) {
        if (closed || ticket != generation) throw CancellationException("DJ operation superseded")
    }

    private fun updatePolling() {
        if (closed || !musicPlayer.isPlaying || session == null) {
            pollJob?.cancel()
            pollJob = null
            return
        }
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive && !closed && musicPlayer.isPlaying) {
                val state = session ?: break
                if (state.invalidReason != null) break
                ensureCommitWorker()
                if (forcePrefetchRequested ||
                    musicPlayer.currentPosition >= prefetchPositionMs(durationOf(state.current))
                ) {
                    requestPrefetch()
                }
                maybeStartCue()
                delay(POLL_MS)
            }
        }
    }

    private fun durationOf(entry: Entry): Long {
        val actual = if (musicPlayer.currentMediaItem?.mediaId == entry.id) musicPlayer.duration else C.TIME_UNSET
        return if (actual != C.TIME_UNSET && actual > 0) actual else entry.track.durationMs
    }

    private fun installCue(slot: CueSlot) {
        // A newly selected/revised cue must not leave old speech attached to music.
        cancelSpeechAndCue()
        if (!slot.cue.shouldSpeak) return
        if (!preferServerTts && slot.cue.script.isNullOrBlank()) return
        pendingCue = slot
        slot.preparation = scope.launch {
            var file: File? = null
            try {
                file = prepareCueFile(slot)
                if (closed || pendingCue !== slot) return@launch
                slot.file = file
                file = null // slot now owns it
                loadPreparedCue(slot)
                maybeStartCue()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (pendingCue === slot) pendingCue = null
                report(error)
            } finally {
                file?.let(files::discard)
            }
        }
    }

    private suspend fun prepareCueFile(slot: CueSlot): File {
        if (preferServerTts) {
            try {
                return withTimeout(TTS_TIMEOUT_MS) {
                    val body = api.synthesizeCue(SynthesizeCueRequest(slot.key.sessionId, slot.key.cueId))
                    downloadWav(body)
                }
            } catch (error: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive() // don't convert caller cancellation into speech
            } catch (error: HttpException) {
                if (error.code() != 503) throw error
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
            }
        }
        val script = slot.cue.script?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IOException("No plain script is available for Android TTS fallback")
        return androidTts.synthesize(script, TTS_TIMEOUT_MS)
    }

    private suspend fun downloadWav(body: ResponseBody): File = suspendCancellableCoroutine { continuation ->
        // Cancellation closes the socket/body immediately, rather than waiting for
        // a blocking IO read to notice coroutine cancellation on its own.
        val worker = scope.launch(Dispatchers.IO) {
            var file: File? = null
            try {
                body.use { response ->
                    val length = response.contentLength()
                    if (length > MAX_WAV_BYTES) throw IOException("DJ WAV exceeds 4 MiB")
                    val mime = response.contentType()?.let { "${it.type}/${it.subtype}" }
                    if (mime !in setOf("audio/wav", "audio/wave", "audio/x-wav", "audio/vnd.wave")) {
                        throw IOException("DJ TTS did not return WAV audio")
                    }
                    val target = files.create()
                    file = target
                    response.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(8_192)
                            var total = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > MAX_WAV_BYTES) throw IOException("DJ WAV exceeds 4 MiB")
                                output.write(buffer, 0, count)
                            }
                            if (total < 12) throw IOException("Empty/truncated DJ WAV")
                        }
                    }
                    val header = ByteArray(12)
                    target.inputStream().use { input ->
                        var offset = 0
                        while (offset < header.size) {
                            val count = input.read(header, offset, header.size - offset)
                            if (count < 0) throw IOException("Truncated DJ WAV header")
                            offset += count
                        }
                    }
                    if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF" ||
                        String(header, 8, 4, Charsets.US_ASCII) != "WAVE"
                    ) throw IOException("Invalid DJ WAV header")
                    currentCoroutineContext().ensureActive()
                    continuation.resume(target) { _, abandoned, _ -> files.discard(abandoned) }
                    file = null
                }
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally {
                file?.let(files::discard)
                body.close()
            }
        }
        continuation.invokeOnCancellation {
            body.close()
            worker.cancel()
        }
        worker.invokeOnCompletion { error ->
            if (error != null) {
                body.close()
                continuation.cancel(error)
            }
        }
    }

    private fun loadPreparedCue(slot: CueSlot) {
        if (closed || pendingCue !== slot || activeCue != null) return
        val file = slot.file ?: return
        loadedCue = slot
        voicePlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        voicePlayer.prepare()
        voicePlayer.playWhenReady = false
    }

    private fun maybeStartCue() {
        if (closed || bargeInDepth > 0 || activeCue != null) return
        val state = session ?: return
        if (state.invalidReason != null) return
        val slot = pendingCue ?: return
        if (slot.key.sessionId != state.id || slot.file == null) return
        if (loadedCue !== slot) loadPreparedCue(slot)
        if (voicePlayer.playbackState != Player.STATE_READY || loadedCue !== slot) return
        if (!musicPlayer.isPlaying) return // opening lead is handled explicitly below
        val id = musicPlayer.currentMediaItem?.mediaId
        val position = musicPlayer.currentPosition.coerceAtLeast(0)
        val previous = slot.previousEntry
        if (previous != null && id == previous.id) {
            val absoluteOutro = slot.cue.timing.startOnPrevOutroMs ?: previous.track.cues.outroRampMs
            val duration = durationOf(previous)
            if (position < absoluteOutro.coerceIn(0, duration)) return
            if (introDeadline(slot) == 0L && duration - position <= VOCAL_SAFETY_MS) return
            startSpeech(slot)
        } else if (id == slot.key.targetEntryId && position < introDeadline(slot)) {
            startSpeech(slot)
        }
    }

    private fun startOpeningOrMusic() {
        val gate = openingGate ?: return
        if (gate.leading || closed || musicPlayer.playbackState != Player.STATE_READY) return
        if (session?.current?.id != gate.entryId || musicPlayer.currentMediaItem?.mediaId != gate.entryId) {
            cancelOpeningLead()
            return
        }
        val slot = pendingCue
        val ready = bargeInDepth == 0 && slot != null && slot.opening &&
            slot.key.targetEntryId == gate.entryId && slot.file != null && loadedCue === slot &&
            voicePlayer.playbackState == Player.STATE_READY
        if (!ready) {
            // Never wait for synthesis, download, or secondary-player preparation.
            openingGate = null
            musicPlayer.play()
            return
        }
        val opening = checkNotNull(slot)
        val lead = opening.cue.timing.openingLeadMs.coerceIn(0, 1_500)
        gate.leading = true
        startSpeech(opening)
        if (lead == 0L) {
            openingGate = null
            musicPlayer.play()
        } else {
            openingLeadJob = scope.launch {
                delay(lead)
                if (!closed && openingGate === gate && session?.current?.id == gate.entryId) {
                    openingGate = null
                    openingLeadJob = null
                    musicPlayer.play()
                }
            }
        }
    }

    private fun startSpeech(slot: CueSlot) {
        if (activeCue != null || loadedCue !== slot) return
        pendingCue = null
        activeCue = slot
        val owner = ++speechOwner
        speechJob = scope.launch {
            try {
                voicePlayer.play()
                coroutineScope {
                    // Guard the vocal deadline DURING the fade as well as afterwards.
                    val fade = launch {
                        rampDuck(slot.cue.timing.duckMusicVolumeTo, slot.cue.timing.rampDurationMs)
                    }
                    try {
                        val startedAt = SystemClock.elapsedRealtime()
                        while (isActive && activeCue === slot && voicePlayer.playbackState != Player.STATE_ENDED) {
                            if (closed || bargeInDepth > 0 || session?.id != slot.key.sessionId) break
                            if (SystemClock.elapsedRealtime() - startedAt >= MAX_SPEECH_MS) break
                            val currentId = musicPlayer.currentMediaItem?.mediaId
                            val inOpeningLead = slot.opening && openingGate?.leading == true &&
                                openingGate?.entryId == slot.key.targetEntryId
                            if (currentId == slot.key.targetEntryId && !inOpeningLead) {
                                if (!musicPlayer.isPlaying || musicPlayer.currentPosition >= introDeadline(slot)) break
                            } else if (currentId == slot.previousEntry?.id) {
                                if (!musicPlayer.isPlaying) break
                                val previous = checkNotNull(slot.previousEntry)
                                if (introDeadline(slot) == 0L &&
                                    durationOf(previous) - musicPlayer.currentPosition <= VOCAL_SAFETY_MS
                                ) break
                            } else if (!inOpeningLead) break
                            delay(VOICE_GUARD_POLL_MS)
                        }
                    } finally {
                        fade.cancelAndJoin()
                    }
                }
                // Stop first: ramp-up must never leave narration playing past a vocal cue.
                voicePlayer.stop()
                rampDuck(1f, slot.cue.timing.rampDurationMs)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                report(error)
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (speechOwner == owner) {
                        voicePlayer.stop()
                        voicePlayer.clearMediaItems()
                        loadedCue = null
                        activeCue = null
                        speechJob = null
                        applyDuck(1f)
                    }
                    slot.file?.let(files::discard)
                    slot.file = null
                    if (!closed && pendingCue != null) loadPreparedCue(checkNotNull(pendingCue))
                }
            }
        }
    }

    private fun introDeadline(slot: CueSlot): Long {
        val vocal = slot.targetTrack.cues.introVocalMs ?: Long.MAX_VALUE
        val limit = min(slot.cue.timing.maxTalkOverNextIntroMs, min(vocal, slot.targetTrack.durationMs))
        return (limit - VOCAL_SAFETY_MS).coerceAtLeast(0)
    }

    private suspend fun rampDuck(target: Float, requestedDurationMs: Long) {
        val from = duckFactor
        val duration = requestedDurationMs.coerceIn(0, 5_000)
        if (duration == 0L) {
            applyDuck(target)
            return
        }
        val start = SystemClock.elapsedRealtime()
        while (true) {
            currentCoroutineContext().ensureActive()
            val fraction = ((SystemClock.elapsedRealtime() - start).toFloat() / duration).coerceIn(0f, 1f)
            applyDuck(from + (target - from) * fraction)
            if (fraction >= 1f) break
            delay(16)
        }
    }

    private fun applyDuck(factor: Float) {
        duckFactor = factor.coerceIn(0f, 1f)
        val volume = (baseVolume * duckFactor).coerceIn(0f, 1f)
        lastInternalVolume = volume
        if (abs(musicPlayer.volume - volume) >= 0.0001f) musicPlayer.volume = volume
    }

    private fun cancelSpeechAndCue() {
        speechOwner++
        speechJob?.cancel()
        speechJob = null
        val old = listOfNotNull(pendingCue, activeCue).distinct()
        pendingCue = null
        activeCue = null
        loadedCue = null
        voicePlayer.stop()
        voicePlayer.clearMediaItems()
        applyDuck(1f)
        old.forEach { slot ->
            slot.preparation?.cancel()
            slot.file?.let(files::discard)
            slot.file = null
        }
    }

    private fun cancelOpeningLead() {
        openingLeadJob?.cancel()
        openingLeadJob = null
        openingGate = null
    }

    private fun finishOpeningLead(playMusic: Boolean) {
        val gate = openingGate
        cancelOpeningLead()
        if (playMusic && gate != null && !closed && musicPlayer.currentMediaItem?.mediaId == gate.entryId) {
            musicPlayer.play()
        }
    }

    private inline fun changePlayer(block: () -> Unit) {
        val previous = internalPlayerChange
        internalPlayerChange = true
        try { block() } finally { internalPlayerChange = previous }
    }

    private fun report(error: Throwable) {
        if (!closed) runCatching { onError(error) } // application callbacks cannot break cleanup
    }

    /** Main thread only; leaves the existing music player and its playable queue intact. */
    override fun close() {
        checkMainThread()
        closeOnMain()
    }

    private fun closeOnMain() {
        if (closed) return
        closed = true
        generation++
        musicPlayer.removeListener(musicListener)
        cancelOpeningLead()
        cancelSpeechAndCue()
        ownerJob.cancel()
        try {
            voicePlayer.removeListener(voiceListener)
            voicePlayer.release()
        } finally {
            try {
                androidTts.close()
            } finally {
                files.close()
                session = null
                staged = null
                entries.clear()
                transitions.clear()
            }
        }
    }

    companion object {
        private const val POLL_MS = 100L
        private const val VOICE_GUARD_POLL_MS = 25L
        private const val VOCAL_SAFETY_MS = 100L
        private const val RETRY_BACKOFF_MS = 5_000L
        private const val TTS_TIMEOUT_MS = 15_000L
        private const val MAX_SPEECH_MS = 60_000L
        private const val MAX_WAV_BYTES = 4L * 1_024 * 1_024
        private const val MAX_PREVIEW_ITEMS = 3

        /** Earlier of 75% elapsed and 20 seconds remaining; short tracks clamp to 0. */
        internal fun prefetchPositionMs(durationMs: Long): Long =
            min((durationMs.coerceAtLeast(0) * 0.75).toLong(), durationMs - 20_000).coerceAtLeast(0)

        private fun checkMainThread() {
            check(Looper.myLooper() == Looper.getMainLooper()) { "DjCoordinator must be created/closed on main" }
        }
    }
}

/** Only files created by this coordinator are deleted; never sweep the host cache. */
private class VoiceFiles(cacheDir: File) : AutoCloseable {
    private val directory = File(cacheDir, "dj-voice-${UUID.randomUUID()}")
    private val owned = mutableSetOf<File>()
    private var closed = false

    @Synchronized
    fun create(): File {
        check(!closed) { "DJ voice cache is closed" }
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create DJ voice cache")
        return File.createTempFile("cue-", ".wav", directory).also { owned.add(it) }
    }

    @Synchronized
    fun discard(file: File) {
        if (file in owned && (!file.exists() || file.delete())) owned.remove(file)
        if (closed && owned.isEmpty()) directory.delete()
    }

    @Synchronized
    override fun close() {
        closed = true
        owned.toList().forEach(::discard)
        directory.delete()
    }
}

/** One engine/one active synthesis; callbacks can arrive on arbitrary binder threads. */
private class AndroidFileTts(
    context: Context,
    private val locale: Locale,
    private val files: VoiceFiles,
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val initialized = CompletableDeferred<Unit>()
    private val mutex = Mutex()
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var engine: TextToSpeech? = null
    private var closed = false

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) {
            utteranceId?.let { waiting.remove(it)?.complete(Unit) }
        }
        @Deprecated("Android callback required for older engines")
        override fun onError(utteranceId: String?) = fail(utteranceId, "Android TTS synthesis failed")
        override fun onError(utteranceId: String?, errorCode: Int) =
            fail(utteranceId, "Android TTS synthesis failed ($errorCode)")
        override fun onStop(utteranceId: String?, interrupted: Boolean) =
            fail(utteranceId, "Android TTS synthesis stopped")
    }

    init {
        // Posting also handles an engine which invokes onInit unusually early,
        // before the constructor assignment to engine has completed.
        try {
            engine = TextToSpeech(context) { status -> handler.post { onInitialized(status) } }
        } catch (_: RuntimeException) {
            initialized.completeExceptionally(IOException("Android TTS engine is unavailable"))
        }
    }

    private fun onInitialized(status: Int) {
        if (closed) return
        val tts = engine
        if (status != TextToSpeech.SUCCESS || tts == null) {
            initialized.completeExceptionally(IOException("Android TTS initialization failed"))
            return
        }
        try {
            if (tts.setOnUtteranceProgressListener(listener) == TextToSpeech.ERROR) {
                initialized.completeExceptionally(IOException("Android TTS listener setup failed"))
                return
            }
            val language = tts.setLanguage(locale)
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                initialized.completeExceptionally(IOException("Android TTS language is unavailable"))
            } else initialized.complete(Unit)
        } catch (_: RuntimeException) {
            initialized.completeExceptionally(IOException("Android TTS engine setup failed"))
        }
    }

    suspend fun synthesize(text: String, timeoutMs: Long): File = withContext(Dispatchers.Main.immediate) {
        withTimeout(timeoutMs) {
            mutex.withLock {
                check(!closed) { "Android TTS is closed" }
                require(text.length <= TextToSpeech.getMaxSpeechInputLength()) { "DJ script exceeds Android TTS limit" }
                initialized.await()
                val tts = checkNotNull(engine)
                val utteranceId = UUID.randomUUID().toString()
                val completion = CompletableDeferred<Unit>()
                var created: File? = null
                var succeeded = false
                try {
                    // Keep ownership even if cancellation wins the IO-to-main handoff.
                    val file = withContext(Dispatchers.IO) { files.create().also { created = it } }
                    waiting[utteranceId] = completion
                    val status = tts.synthesizeToFile(text, Bundle(), file, utteranceId)
                    if (status == TextToSpeech.ERROR) throw IOException("Android TTS rejected synthesis")
                    completion.await()
                    if (!withContext(Dispatchers.IO) { file.isFile && file.length() > 0 }) {
                        throw IOException("Android TTS produced an empty file")
                    }
                    succeeded = true
                    file
                } finally {
                    waiting.remove(utteranceId)
                    if (!succeeded) {
                        tts.stop()
                        created?.let(files::discard)
                    }
                }
            }
        }
    }

    private fun fail(id: String?, message: String) {
        id?.let { waiting.remove(it)?.completeExceptionally(IOException(message)) }
    }

    override fun close() {
        if (closed) return
        closed = true
        initialized.cancel()
        waiting.values.forEach { it.cancel() }
        waiting.clear()
        val tts = engine
        engine = null
        try {
            tts?.stop()
        } finally {
            tts?.shutdown()
        }
    }
}
