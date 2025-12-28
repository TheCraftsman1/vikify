package com.vikify.app.vikifyui.data

import android.app.Application
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Search Source Enum
 */
enum class SearchSource {
    YOUTUBE,
    SPOTIFY
}

/**
 * Unified Search Result Item
 * This abstraction allows the UI to be agnostic of data source
 */
sealed class UnifiedSearchResult(
    open val id: String,
    open val headline: String,
    open val subheadline: String,
    open val imageUrl: String?,
    open val isCircular: Boolean = false,
    open val type: String = "unknown"  // For filter chips: "song", "artist", "album", "playlist"
) {
    data class Song(
        override val id: String,
        override val headline: String,
        override val subheadline: String,
        override val imageUrl: String?,
        val originalItem: SongItem? = null
    ) : UnifiedSearchResult(id, headline, subheadline, imageUrl, false, "song")
    
    data class Album(
        override val id: String,
        override val headline: String,
        override val subheadline: String,
        override val imageUrl: String?,
        val originalItem: AlbumItem? = null
    ) : UnifiedSearchResult(id, headline, subheadline, imageUrl, false, "album")
    
    data class Artist(
        override val id: String,
        override val headline: String,
        override val subheadline: String,
        override val imageUrl: String?
    ) : UnifiedSearchResult(id, headline, subheadline, imageUrl, true, "artist")
    
    data class Playlist(
        override val id: String,
        override val headline: String,
        override val subheadline: String,
        override val imageUrl: String?,
        val spotifyPlaylist: SpotifyPlaylist? = null
    ) : UnifiedSearchResult(id, headline, subheadline, imageUrl, false, "playlist")
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "SearchViewModel"
        private const val DEBOUNCE_MS = 400L
        private const val MIN_QUERY_LENGTH = 2
    }
    
    // --- Search Source ---
    private val _searchSource = MutableStateFlow(SearchSource.YOUTUBE)
    val searchSource = _searchSource.asStateFlow()
    
    // --- Query State ---
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    // --- Unified Results (UI consumes this single source) ---
    private val _unifiedResults = MutableStateFlow<List<UnifiedSearchResult>>(emptyList())
    val unifiedResults = _unifiedResults.asStateFlow()
    
    // --- Legacy Results (for backward compatibility if needed) ---
    private val _searchResults = MutableStateFlow<List<YTItem>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()
    
    // --- Spotify Integration ---
    private var spotifyRepository: SpotifyRepository? = null
    
    val isSpotifyLoggedIn: Boolean
        get() = spotifyRepository?.isLoggedIn == true

    init {
        // Reactive search: combines query + source changes
        combine(_query, _searchSource) { query, source -> 
            Pair(query.trim(), source)
        }
            .filter { (query, _) -> query.length >= MIN_QUERY_LENGTH }
            .debounce(DEBOUNCE_MS)
            .distinctUntilChanged()
            .onEach { _isSearching.value = true }
            .onEach { (query, source) -> 
                performSearch(query, source)
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)
    }
    
    /**
     * Set SpotifyRepository for Spotify search
     */
    fun setSpotifyRepository(repo: SpotifyRepository?) {
        spotifyRepository = repo
    }
    
    /**
     * Perform search based on source
     */
    private suspend fun performSearch(query: String, source: SearchSource) {
        Log.d(TAG, "Searching '$query' on $source")
        
        when (source) {
            SearchSource.YOUTUBE -> searchYouTube(query)
            SearchSource.SPOTIFY -> searchSpotify(query)
        }
        
        _isSearching.value = false
    }
    
    /**
     * YouTube search implementation
     */
    private suspend fun searchYouTube(query: String) {
        withContext(Dispatchers.IO) {
            try {
                val result = YouTube.searchSummary(query)
                result.fold(
                    onSuccess = { page ->
                        val ytItems = page.summaries.flatMap { it.items }
                        _searchResults.value = ytItems
                        
                        // Convert to unified results
                        _unifiedResults.value = ytItems.mapNotNull { item ->
                            when (item) {
                                is SongItem -> UnifiedSearchResult.Song(
                                    id = item.id,
                                    headline = item.title,
                                    subheadline = "Song • ${item.artists.firstOrNull()?.name ?: "Unknown"}",
                                    imageUrl = item.thumbnail,
                                    originalItem = item
                                )
                                is AlbumItem -> UnifiedSearchResult.Album(
                                    id = item.id,
                                    headline = item.title,
                                    subheadline = "Album • ${item.artists?.firstOrNull()?.name ?: "Unknown"}",
                                    imageUrl = item.thumbnail,
                                    originalItem = item
                                )
                                is ArtistItem -> UnifiedSearchResult.Artist(
                                    id = item.id,
                                    headline = item.title,
                                    subheadline = "Artist",
                                    imageUrl = item.thumbnail
                                )
                                else -> null
                            }
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "YouTube search failed", e)
                        reportException(e)
                        _searchResults.value = emptyList()
                        _unifiedResults.value = emptyList()
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "YouTube search exception", e)
                _searchResults.value = emptyList()
                _unifiedResults.value = emptyList()
            }
        }
    }
    
    /**
     * Spotify search implementation
     */
    private suspend fun searchSpotify(query: String) {
        val repo = spotifyRepository
        if (repo == null || !repo.isLoggedIn) {
            Log.w(TAG, "Spotify not available for search")
            _unifiedResults.value = emptyList()
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                val playlists = repo.searchPlaylists(query)
                _unifiedResults.value = playlists.map { playlist ->
                    UnifiedSearchResult.Playlist(
                        id = playlist.id,
                        headline = playlist.name,
                        subheadline = "Playlist • ${playlist.owner}",
                        imageUrl = playlist.imageUrl,
                        spotifyPlaylist = playlist
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Spotify search failed", e)
                _unifiedResults.value = emptyList()
            }
        }
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isEmpty()) {
            _searchResults.value = emptyList()
            _unifiedResults.value = emptyList()
            _isSearching.value = false
        }
    }
    
    fun updateSearchSource(source: SearchSource) {
        _searchSource.value = source
        // Re-trigger search if we have a query
        if (_query.value.length >= MIN_QUERY_LENGTH) {
            viewModelScope.launch(Dispatchers.IO) {
                _isSearching.value = true
                performSearch(_query.value.trim(), source)
            }
        }
    }
    
    /**
     * Search by category - triggers a search for the category name
     */
    fun searchByCategory(categoryName: String) {
        _query.value = categoryName
        _searchSource.value = SearchSource.YOUTUBE // Categories are YouTube-only
        
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            try {
                val result = YouTube.searchSummary("$categoryName music")
                result.fold(
                    onSuccess = { page ->
                        val ytItems = page.summaries.flatMap { it.items }
                        _searchResults.value = ytItems
                        _unifiedResults.value = ytItems.mapNotNull { item ->
                            when (item) {
                                is SongItem -> UnifiedSearchResult.Song(
                                    id = item.id,
                                    headline = item.title,
                                    subheadline = "Song • ${item.artists.firstOrNull()?.name ?: "Unknown"}",
                                    imageUrl = item.thumbnail,
                                    originalItem = item
                                )
                                is AlbumItem -> UnifiedSearchResult.Album(
                                    id = item.id,
                                    headline = item.title,
                                    subheadline = "Album • ${item.artists?.firstOrNull()?.name ?: "Unknown"}",
                                    imageUrl = item.thumbnail,
                                    originalItem = item
                                )
                                is ArtistItem -> UnifiedSearchResult.Artist(
                                    id = item.id,
                                    headline = item.title,
                                    subheadline = "Artist",
                                    imageUrl = item.thumbnail
                                )
                                else -> null
                            }
                        }
                    },
                    onFailure = {
                        it.printStackTrace()
                        _searchResults.value = emptyList()
                        _unifiedResults.value = emptyList()
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _isSearching.value = false
        }
    }
}
