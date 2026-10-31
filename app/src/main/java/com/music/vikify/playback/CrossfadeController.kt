package com.music.vikify.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.music.vikify.data.listentogether.ListenTogether
import com.music.vikify.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Two-player, equal-power crossfades using the listener's chosen duration. */
@UnstableApi
class CrossfadeController(
    private val scope: CoroutineScope,
    private val active: () -> ExoPlayer,
    private val standby: () -> ExoPlayer,
    private val onHandoff: (ExoPlayer, ExoPlayer) -> Unit,
    private val onArmIncoming: (MediaItem, String?) -> Unit = { _, _ -> },
    private val onBlendHeadroom: (Float) -> Unit = {},
    private val onSettled: () -> Unit = {},
) {
    private var ticker: Job? = null
    private var outgoing: ExoPlayer? = null
    private var incoming: ExoPlayer? = null
    private var outgoingId: String? = null
    private var incomingId: String? = null
    private var handedOff = false
    private var fading = false
    private var fadeMs = 0L
    private var armedAt = 0L
    private var queuedCount = 0
    private var lastTransitionAt = 0L
    private var pausedAtEnd = false
    private val endListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            pausedAtEnd = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
        }
    }

    fun isTransitioning(): Boolean = outgoing != null
    fun msSinceTransition(): Long? = lastTransitionAt.takeIf { it > 0L }
        ?.let { SystemClock.elapsedRealtime() - it }

    fun start() {
        if (ticker != null) return
        ticker = scope.launch {
            while (isActive) {
                tick()
                delay(20)
            }
        }
    }

    fun release() {
        ticker?.cancel()
        ticker = null
        finish()
    }

    fun onSkipRequested() = finish()

    fun applyPlaybackSpeed(speed: Float) {
        active().setPlaybackSpeed(speed)
        standby().setPlaybackSpeed(speed)
    }

    private fun tick() {
        val out = outgoing
        val into = incoming
        if (out == null || into == null) {
            considerTransition()
            return
        }
        if (ListenTogether.state.value.inParty || AppSettings.crossfadeSeconds.value <= 0 ||
            out.currentMediaItem?.mediaId != outgoingId || into.currentMediaItem?.mediaId != incomingId ||
            into.playerError != null || out.playerError != null
        ) {
            finish()
            return
        }
        val owner = active()
        val shouldPlay = owner.playWhenReady || (!handedOff && pausedAtEnd && owner === out)
        if (!fading) {
            if (SystemClock.elapsedRealtime() - armedAt > 15_000L) {
                finish()
                return
            }
            val remaining = out.duration - out.currentPosition
            if (!shouldPlay || into.playbackState != Player.STATE_READY || remaining > fadeMs) return
            fadeMs = minOf(fadeMs, remaining.coerceAtLeast(100L))
            fading = true
            into.play()
        } else {
            if (!pausedAtEnd && out.playWhenReady != shouldPlay) out.playWhenReady = shouldPlay
            if (into.playWhenReady != shouldPlay) into.playWhenReady = shouldPlay
        }
        val progress = (into.currentPosition.toDouble() / fadeMs).coerceIn(0.0, 1.0)
        val rise = sin(progress * PI / 2).toFloat()
        val fall = cos(progress * PI / 2).toFloat()
        into.volume = rise
        out.volume = fall
        onBlendHeadroom(1f / sqrt((rise + fall).coerceAtLeast(1f)))
        if (!handedOff && progress >= 0.5) {
            // Preserve queue additions made while the incoming track buffered.
            if (out.mediaItemCount > queuedCount) {
                into.addMediaItems((queuedCount until out.mediaItemCount).map(out::getMediaItemAt))
            }
            handedOff = true
            onHandoff(out, into)
        }
        if (progress >= 1.0 || (handedOff && out.playbackState == Player.STATE_ENDED)) finish()
    }

    private fun considerTransition() {
        val current = active()
        if (!current.isPlaying || ListenTogether.state.value.inParty ||
            SleepTimer.afterTrack.value || current.repeatMode == Player.REPEAT_MODE_ONE ||
            !current.hasNextMediaItem()
        ) return
        val duration = current.duration
        if (duration == C.TIME_UNSET || duration <= 0L) return
        val fade = minOf(AppSettings.crossfadeSeconds.value * 1000L, duration / 2)
        if (fade <= 0L || duration - current.currentPosition > fade + 3_000L) return
        val nextIndex = current.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val next = standby()
        if (next === current) return
        val items = (0 until current.mediaItemCount).map(current::getMediaItemAt)
        outgoing = current
        incoming = next
        outgoingId = current.currentMediaItem?.mediaId
        incomingId = items[nextIndex].mediaId
        fadeMs = fade
        armedAt = SystemClock.elapsedRealtime()
        queuedCount = items.size
        handedOff = false
        fading = false
        pausedAtEnd = false
        current.addListener(endListener)
        current.pauseAtEndOfMediaItems = true
        next.stop()
        next.clearMediaItems()
        next.volume = 0f
        next.playWhenReady = false
        next.repeatMode = current.repeatMode
        next.shuffleModeEnabled = current.shuffleModeEnabled
        next.setPlaybackSpeed(AppSettings.playbackSpeed.value)
        next.setMediaItems(items, nextIndex, 0L)
        onArmIncoming(items[nextIndex], items.getOrNull(nextIndex + 1)?.mediaId)
        next.prepare()
    }

    private fun finish() {
        val out = outgoing ?: return
        val into = incoming
        outgoing = null
        incoming = null
        out.removeListener(endListener)
        out.pauseAtEndOfMediaItems = false
        if (!handedOff && pausedAtEnd) out.play()
        pausedAtEnd = false
        val retired = if (handedOff) out else into
        retired?.apply {
            volume = 0f
            stop()
            clearMediaItems()
        }
        active().volume = 1f
        onBlendHeadroom(1f)
        lastTransitionAt = SystemClock.elapsedRealtime()
        fading = false
        handedOff = false
        onSettled()
    }
}
