/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Search ViewModel - Unified Search with History & Suggestions
 */
package com.vikify.app.vikifyui.data

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vikify.app.spotify.SpotifyPlaylist
import com.vikify.app.spotify.SpotifyRepository
import com.vikify.app.utils.reportException
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YTItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

// Used from external: Track, Artist, Category, MockData

// ═══════════════════════════════════════════════════════════════════════════════
// SEARCH ENUMS & SEALED CLASSES
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Search source
 */
enum class SearchSource {
    YOUTUBE,
    SPOTIFY,
    ALL
}

/**
 * Search filter for chips
 */
enum class SearchFilter(val displayName: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists")
}

/**
 * Search UI state
 */
sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<UnifiedSearchResult>) : SearchState()
    data class Error(val message: String, val isRetryable: Boolean = true) : SearchState()
    object Empty : SearchState()
}

/**
 * Unified search result - source agnostic
 */
sealed class UnifiedSearchResult(
    open val id: String,
    open val headline: String,
    open val subheadline: String,
    open val imageUrl: String?,
    open val isCircular: Boolean = false,
    open val type: String = "unknown",
    open val source: SearchSource = SearchSource.YOUTUBE
) {
    data class Song(
        override val id: String,
        override val headline: String,
        override val subheadline: String,
        override val imageUrl: String?,
        override val source: SearchSource = SearchSource.YOUTUBE,
        val duration: Long? = null,
        val originalItem: SongItem? = null
    ) : UnifiedSearchResult(id, headline, subheadline, imageUrl, false, "song", source)

    data class Album(
        override val id: String,
        override val headline: String,
        override val subheadline: String,
        override val imageUrl: String?,
        override val source: SearchSource = SearchSource.YOUTUBE,
        val year: Int? = null,
        val originalItem: AlbumItem? = null
    ) : UnifiedSearchResult(id, headline, subheadline, imageUrl, false, "album", source)

    data class Artist(
        override val id: String,
        override val headline: String,
        override val subheadline: String,
        override val imageUrl: String?,
        override val source: SearchSource = SearchSource.YOUTUBE,
        val subscriberCount: String? = null
    ) : UnifiedSearchResult(id, headline, subheadline, imageUrl, true, "artist", source)

    data class Playlist(
        override val id: String,
        override val headline: String,
        override val subheadline: String,
        override val imageUrl: String?,
        override val source: SearchSource = SearchSource.YOUTUBE,
        val trackCount: Int? = null,
        val spotifyPlaylist: SpotifyPlaylist? = null
    ) : UnifiedSearchResult(id, headline, subheadline, imageUrl, false, "playlist", source)

    data class Video(
        override val id: String,
        override val headline: String,
        override val subheadline: String,
        override val imageUrl: String?,
        val duration: Long? = null,
        val viewCount: String? = null
    ) : UnifiedSearchResult(id, headline, subheadline, imageUrl, false, "video", SearchSource.YOUTUBE)
}

/**
 * Search history item (persisted)
 */
@Serializable
data class SearchHistoryItem(
    val query: String,
    val timestamp: Long = System.currentTimeMillis(),
    val resultType: String? = null
)

// ═══════════════════════════════════════════════════════════════════════════════
// VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SearchViewModel"
        private const val DEBOUNCE_MS = 350L
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_HISTORY_SIZE = 20
        private const val PREFS_NAME = "vikify_search"
        private const val KEY_HISTORY = "search_history"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    // ─────────────────────────────────────────────────────────────────────────
    // STATE FLOWS
    // ─────────────────────────────────────────────────────────────────────────

    private val _searchSource = MutableStateFlow(SearchSource.YOUTUBE)
    val searchSource = _searchSource.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _activeFilter = MutableStateFlow(SearchFilter.ALL)
    val activeFilter = _activeFilter.asStateFlow()

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState = _searchState.asStateFlow()

    private val _unifiedResults = MutableStateFlow<List<UnifiedSearchResult>>(emptyList())
    val unifiedResults = _unifiedResults.asStateFlow()

    // Legacy compatibility
    private val _searchResults = MutableStateFlow<List<YTItem>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    // Derived loading state
    val isSearching: StateFlow<Boolean> = _searchState
        .map { it is SearchState.Loading }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _searchHistory = MutableStateFlow<List<SearchHistoryItem>>(emptyList())
    val searchHistory = _searchHistory.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val _trendingSearches = MutableStateFlow<List<String>>(emptyList())
    val trendingSearches = _trendingSearches.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

    // Spotify
    private var spotifyRepository: SpotifyRepository? = null

    val isSpotifyLoggedIn: Boolean
        get() = spotifyRepository?.isLoggedIn == true

    // ─────────────────────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────────────────────

    init {
        loadSearchHistory()
        loadTrendingSearches()

        // Reactive search pipeline
        combine(_query, _searchSource, _activeFilter) { query, source, filter ->
            Triple(query.trim(), source, filter)
        }
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .onEach { (query, source, filter) ->
                when {
                    query.isEmpty() -> {
                        _searchState.value = SearchState.Idle
                        _unifiedResults.value = emptyList()
                        _suggestions.value = emptyList()
                    }
                    query.length < MIN_QUERY_LENGTH -> {
                        _suggestions.value = getHistorySuggestions(query)
                    }
                    else -> {
                        _searchState.value = SearchState.Loading
                        performSearch(query, source, filter)
                    }
                }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    fun setSpotifyRepository(repo: SpotifyRepository?) {
        spotifyRepository = repo
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isEmpty()) {
            _searchState.value = SearchState.Idle
            _unifiedResults.value = emptyList()
            _searchResults.value = emptyList()
        }
    }

    fun updateSearchSource(source: SearchSource) {
        _searchSource.value = source
    }

    fun updateFilter(filter: SearchFilter) {
        _activeFilter.value = filter
        applyFilter(filter)
    }

    fun searchByCategory(categoryName: String) {
        _query.value = categoryName
        _searchSource.value = SearchSource.YOUTUBE

        viewModelScope.launch(Dispatchers.IO) {
            _searchState.value = SearchState.Loading
            performSearch("$categoryName music", SearchSource.YOUTUBE, SearchFilter.ALL)
        }
    }

    fun addToHistory(query: String, resultType: String? = null) {
        if (query.isBlank() || query.length < MIN_QUERY_LENGTH) return

        viewModelScope.launch(Dispatchers.IO) {
            val history = _searchHistory.value.toMutableList()
            history.removeAll { it.query.equals(query, ignoreCase = true) }
            history.add(0, SearchHistoryItem(query, System.currentTimeMillis(), resultType))

            val trimmed = history.take(MAX_HISTORY_SIZE)
            _searchHistory.value = trimmed
            saveSearchHistory(trimmed)
        }
    }

    fun removeFromHistory(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val history = _searchHistory.value.filterNot {
                it.query.equals(query, ignoreCase = true)
            }
            _searchHistory.value = history
            saveSearchHistory(history)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _searchHistory.value = emptyList()
            prefs.edit().remove(KEY_HISTORY).apply()
        }
    }

    fun retry() {
        val currentQuery = _query.value.trim()
        if (currentQuery.length >= MIN_QUERY_LENGTH) {
            viewModelScope.launch(Dispatchers.IO) {
                _searchState.value = SearchState.Loading
                performSearch(currentQuery, _searchSource.value, _activeFilter.value)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SEARCH IMPLEMENTATION
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun performSearch(
        query: String,
        source: SearchSource,
        filter: SearchFilter
    ) {
        Log.d(TAG, "Searching '$query' on $source with filter $filter")

        try {
            val results = when (source) {
                SearchSource.YOUTUBE -> searchYouTube(query)
                SearchSource.SPOTIFY -> searchSpotify(query)
                SearchSource.ALL -> searchAll(query)
            }

            if (results.isEmpty()) {
                _searchState.value = SearchState.Empty
                _unifiedResults.value = emptyList()
            } else {
                _searchState.value = SearchState.Success(results)
                _unifiedResults.value = filterResults(results, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            reportException(e)

            val errorMsg = when {
                e.message?.contains("network", ignoreCase = true) == true ->
                    "No internet connection. Please check your network."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "Request timed out. Please try again."
                else ->
                    "Something went wrong. Please try again."
            }

            _searchState.value = SearchState.Error(errorMsg)
            _errorMessage.emit(errorMsg)
        }
    }

    private suspend fun searchYouTube(query: String): List<UnifiedSearchResult> {
        return withContext(Dispatchers.IO) {
            val result = YouTube.searchSummary(query)
            result.fold(
                onSuccess = { page ->
                    val ytItems = page.summaries.flatMap { it.items }
                    _searchResults.value = ytItems
                    ytItems.mapNotNull { it.toUnifiedResult() }
                },
                onFailure = { e ->
                    Log.e(TAG, "YouTube search failed", e)
                    throw e
                }
            )
        }
    }

    private suspend fun searchSpotify(query: String): List<UnifiedSearchResult> {
        val repo = spotifyRepository ?: return emptyList()
        if (!repo.isLoggedIn) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val playlists = repo.searchPlaylists(query)
                playlists.map { playlist ->
                    UnifiedSearchResult.Playlist(
                        id = playlist.id,
                        headline = playlist.name,
                        subheadline = "Playlist • ${playlist.owner}",
                        imageUrl = playlist.imageUrl,
                        source = SearchSource.SPOTIFY,
                        trackCount = playlist.trackCount,
                        spotifyPlaylist = playlist
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Spotify search failed", e)
                emptyList()
            }
        }
    }

    private suspend fun searchAll(query: String): List<UnifiedSearchResult> {
        return withContext(Dispatchers.IO) {
            val youtubeDeferred = async {
                try { searchYouTube(query) } catch (e: Exception) { emptyList() }
            }
            val spotifyDeferred = async {
                try { searchSpotify(query) } catch (e: Exception) { emptyList() }
            }

            val youtube = youtubeDeferred.await()
            val spotify = spotifyDeferred.await()

            // Interleave for variety
            interleaveResults(youtube, spotify)
        }
    }

    private fun interleaveResults(
        list1: List<UnifiedSearchResult>,
        list2: List<UnifiedSearchResult>
    ): List<UnifiedSearchResult> {
        val result = mutableListOf<UnifiedSearchResult>()
        val maxSize = maxOf(list1.size, list2.size)

        for (i in 0 until maxSize) {
            list1.getOrNull(i)?.let { result.add(it) }
            list2.getOrNull(i)?.let { result.add(it) }
        }

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FILTERING
    // ─────────────────────────────────────────────────────────────────────────

    private fun applyFilter(filter: SearchFilter) {
        val currentState = _searchState.value
        if (currentState is SearchState.Success) {
            _unifiedResults.value = filterResults(currentState.results, filter)
        }
    }

    private fun filterResults(
        results: List<UnifiedSearchResult>,
        filter: SearchFilter
    ): List<UnifiedSearchResult> {
        return when (filter) {
            SearchFilter.ALL -> results
            SearchFilter.SONGS -> results.filterIsInstance<UnifiedSearchResult.Song>()
            SearchFilter.ARTISTS -> results.filterIsInstance<UnifiedSearchResult.Artist>()
            SearchFilter.ALBUMS -> results.filterIsInstance<UnifiedSearchResult.Album>()
            SearchFilter.PLAYLISTS -> results.filterIsInstance<UnifiedSearchResult.Playlist>()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HISTORY & SUGGESTIONS
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val historyJson = prefs.getString(KEY_HISTORY, null)
                if (!historyJson.isNullOrEmpty()) {
                    val history = json.decodeFromString<List<SearchHistoryItem>>(historyJson)
                    _searchHistory.value = history
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load search history", e)
            }
        }
    }

    private fun saveSearchHistory(history: List<SearchHistoryItem>) {
        try {
            val historyJson = json.encodeToString(history)
            prefs.edit().putString(KEY_HISTORY, historyJson).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save search history", e)
        }
    }

    private fun getHistorySuggestions(prefix: String): List<String> {
        return _searchHistory.value
            .filter { it.query.startsWith(prefix, ignoreCase = true) }
            .map { it.query }
            .take(5)
    }

    private fun loadTrendingSearches() {
        _trendingSearches.value = MockData.trendingSearches
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXTENSIONS
    // ─────────────────────────────────────────────────────────────────────────

    private fun YTItem.toUnifiedResult(): UnifiedSearchResult? {
        return when (this) {
            is SongItem -> UnifiedSearchResult.Song(
                id = id,
                headline = title,
                subheadline = buildSubheadline("Song", artists.firstOrNull()?.name),
                imageUrl = thumbnail,
                source = SearchSource.YOUTUBE,
                duration = duration?.toLong()?.times(1000),
                originalItem = this
            )
            is AlbumItem -> UnifiedSearchResult.Album(
                id = id,
                headline = title,
                subheadline = buildSubheadline("Album", artists?.firstOrNull()?.name),
                imageUrl = thumbnail,
                source = SearchSource.YOUTUBE,
                year = year,
                originalItem = this
            )
            is ArtistItem -> UnifiedSearchResult.Artist(
                id = id,
                headline = title,
                subheadline = "Artist",
                imageUrl = thumbnail,
                source = SearchSource.YOUTUBE,
                subscriberCount = null
            )
            is PlaylistItem -> UnifiedSearchResult.Playlist(
                id = id,
                headline = title,
                subheadline = buildSubheadline("Playlist", author?.name),
                imageUrl = thumbnail,
                source = SearchSource.YOUTUBE,
                trackCount = songCountText?.filter { it.isDigit() }?.toIntOrNull()
            )
            else -> null
        }
    }

    private fun buildSubheadline(type: String, artist: String?): String {
        return if (artist.isNullOrBlank()) type else "$type • $artist"
    }
}
