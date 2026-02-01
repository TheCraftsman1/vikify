package com.vikify.app.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vikify.app.db.MusicDatabase
import com.vikify.app.utils.reportException
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    database: MusicDatabase
) : ViewModel() {
    private val playlistId = requireNotNull(savedStateHandle.get<String>("playlistId")) {
        "playlistId argument is required for OnlinePlaylistViewModel"
    }

    val playlist = MutableStateFlow<PlaylistItem?>(null)
    val playlistSongs = MutableStateFlow<List<SongItem>>(emptyList())
    var continuation: String? = null
    val dbPlaylist = database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val isLoading = MutableStateFlow(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading.value = true
            YouTube.playlist(playlistId)
                .onSuccess { playlistPage ->
                    playlist.value = playlistPage.playlist
                    playlistSongs.value = playlistPage.songs
                    continuation = playlistPage.songsContinuation
                }.onFailure {
                    reportException(it)
                }
            isLoading.value = false
        }
    }

    fun loadMoreSongs() {
        continuation?.let {
            isLoading.value = true
            viewModelScope.launch(Dispatchers.IO) {
                getContinuation(it)
            }
            isLoading.value = false
        }
    }

    fun loadRemainingSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading.value = true
            while (continuation != null) {
                continuation?.let { getContinuation(it) }
            }
            isLoading.value = false
        }
    }

    suspend fun getContinuation(continuation: String) {
        val continuationPage = YouTube.playlistContinuation(continuation).getOrElse { e ->
            reportException(e)
            return
        }
        playlistSongs.value = playlistSongs.value + continuationPage.songs
        this.continuation = continuationPage.continuation
    }
}
