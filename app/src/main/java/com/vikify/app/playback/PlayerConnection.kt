/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.vikify.app.playback

import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import com.vikify.app.db.MusicDatabase
import com.vikify.app.db.entities.LyricsEntity.Companion.uninitializedLyric
import com.vikify.app.extensions.currentMetadata
import com.vikify.app.extensions.getCurrentQueueIndex
import com.vikify.app.extensions.getQueueWindows
import com.vikify.app.extensions.metadata
import com.vikify.app.playback.queues.Queue
import com.vikify.app.utils.reportException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.utils.SemanticLyrics

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    binder: MediaControllerViewModel,
    val database: MusicDatabase,
) : Player.Listener {
    val TAG = PlayerConnection::class.simpleName.toString()

    val service = binder.getService()!!
    val player = service.player
    val scope = binder.viewModelScope

    val playbackState = MutableStateFlow(player.playbackState)
    private val playWhenReady = MutableStateFlow(player.playWhenReady)
    val isPlaying = combine(playbackState, playWhenReady) { playbackState, playWhenReady ->
        playWhenReady && playbackState != STATE_ENDED
    }.stateIn(scope, SharingStarted.Lazily, player.playWhenReady && player.playbackState != STATE_ENDED)
    val waitingForNetworkConnection: StateFlow<Boolean> = service.waitingForNetworkConnection.asStateFlow()
    val mediaMetadata = MutableStateFlow(player.currentMetadata)
    val currentSong = mediaMetadata.flatMapLatest {
        database.song(it?.id)
    }
    val currentLyrics: Flow<SemanticLyrics> = mediaMetadata.flatMapLatest { mediaMetadata ->
        if (mediaMetadata != null) {
            return@flatMapLatest flowOf(service.lyricsHelper.getLyrics(mediaMetadata) ?: uninitializedLyric)
        } else {
            return@flatMapLatest flowOf()
        }
    }

    private val currentMediaItemIndex = MutableStateFlow(-1)

    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())

    var queuePlaylistId = MutableStateFlow<String?>(null)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val error = MutableStateFlow<PlaybackException?>(null)
    
    // ═══════════════════════════════════════════════════════════════════════
    // USER QUEUE (Spotify-style) - Exposed to UI
    // ═══════════════════════════════════════════════════════════════════════
    
    /** User Queue - songs explicitly added via "Add to Queue" */
    val userQueue: StateFlow<List<com.vikify.app.models.MediaMetadata>> = 
        service.queueBoard.userQueueManager.userQueue
    
    /** Current context (playlist/album) title for UI display */
    val contextTitle: StateFlow<String> = MutableStateFlow(
        service.queueBoard.getCurrentQueue()?.title ?: ""
    )
    
    // ═══════════════════════════════════════════════════════════════════════
    // AUTOPLAY (Spotify-style Infinite Radio) - Exposed to UI
    // ═══════════════════════════════════════════════════════════════════════
    
    /** Autoplay enabled/disabled toggle */
    val isAutoplayEnabled: StateFlow<Boolean> = service.autoplayManager.isAutoplayEnabled
    
    /** Autoplay queue songs */
    val autoplayQueue: StateFlow<List<AutoplayManager.AutoplayTrack>> = service.autoplayManager.autoplayQueue
    
    /** Seed artist for "Similar to [Artist]" subtitle */
    val autoplaySeedArtist: StateFlow<String?> = service.autoplayManager.seedArtist
    
    /** Set autoplay enabled/disabled */
    fun setAutoplayEnabled(enabled: Boolean) {
        service.autoplayManager.setAutoplayEnabled(enabled)
    }

    init {
        player.addListener(this)

        playbackState.value = player.playbackState
        playWhenReady.value = player.playWhenReady
        queuePlaylistId.value = service.queuePlaylistId
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        currentMediaItemIndex.value = player.currentMediaItemIndex
        shuffleModeEnabled.value = player.shuffleModeEnabled
        repeatMode.value = player.repeatMode

        scope.launch {
            mediaMetadata.value = player.currentMetadata ?: database.getResumptionQueue()?.getCurrentSong()
        }
    }

    fun playQueue(
        queue: Queue,
        shouldResume: Boolean = false,
        replace: Boolean = true,
        isRadio: Boolean = false,
        title: String? = null
    ) {
        service.playQueue(
            queue = queue,
            shouldResume = shouldResume,
            replace = replace,
            title = title,
            isRadio = isRadio
        )
    }

    /**
     * Play a single track and enable Autoplay/Radio mode
     */
    fun playSingleTrack(metadata: com.vikify.app.models.MediaMetadata) {
        service.playQueue(
            com.vikify.app.playback.queues.ListQueue(
                title = metadata.title,
                items = listOf(metadata),
                startIndex = 0
            )
        )
    }

    /**
     * Add item to queue, right after current playing item
     */
    fun enqueueNext(item: MediaItem) = enqueueNext(listOf(item))

    /**
     * Add items to queue, right after current playing item
     */
    fun enqueueNext(items: List<MediaItem>) {
        service.enqueueNext(items)
    }

    /**
     * Add item to end of current queue
     */
    fun enqueueEnd(item: MediaItem) = enqueueEnd(listOf(item))

    /**
     * Add items to end of current queue
     */
    fun enqueueEnd(items: List<MediaItem>) {
        service.enqueueEnd(items)
    }

    fun toggleLike() {
        service.toggleLike()
    }

    fun toggleLibrary() {
        service.toggleLibrary()
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // USER QUEUE METHODS (Spotify-style)
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Add a song to User Queue (Spotify's "Add to Queue")
     * Plays before context queue advances
     */
    fun addToUserQueue(item: MediaItem) {
        service.addToUserQueue(item)
    }
    
    /**
     * Add a song to play next (front of user queue)
     * Spotify's "Play Next" behavior
     */
    fun playNext(item: MediaItem) {
        service.playNext(item)
    }
    
    /**
     * Clear the entire User Queue
     */
    fun clearUserQueue() {
        service.clearUserQueue()
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(newPlayWhenReady: Boolean, reason: Int) {
        playWhenReady.value = newPlayWhenReady
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        queueWindows.value = player.getQueueWindows()
        queuePlaylistId.value = service.queuePlaylistId
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    /**
     * Shuffles the queue
     */
    fun triggerShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        try {
            if (!player.currentTimeline.isEmpty) {
                val index = player.currentMediaItemIndex
                val timeline = player.currentTimeline
                // Bounds check before accessing
                if (index >= 0 && index < timeline.windowCount) {
                    val window = timeline.getWindow(index, Timeline.Window())
                    canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                            || !window.isLive()
                            || player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    canSkipNext.value = window.isLive() && window.isDynamic
                            || player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                } else {
                    canSkipPrevious.value = false
                    canSkipNext.value = false
                }
            } else {
                canSkipPrevious.value = false
                canSkipNext.value = false
            }
        } catch (e: Exception) {
            // Gracefully handle timeline changes during update
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun dispose() {
        player.removeListener(this)
    }

    fun softKillPlayer() {
        Log.i(TAG, "Stopping player and uninitializing queue")
        player.clearMediaItems()
        service.deInitQueue()
    }
}
