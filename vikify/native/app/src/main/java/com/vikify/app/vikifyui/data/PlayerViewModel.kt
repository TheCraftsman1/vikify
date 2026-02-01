package com.vikify.app.vikifyui.data

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vikify.app.playback.PlayerConnection
import com.vikify.app.playback.DownloadUtil
import com.vikify.app.extensions.metadata
import com.vikify.app.extensions.toMediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.isActive
import java.util.Calendar
import com.vikify.app.models.toMediaMetadata
import com.vikify.app.playback.queues.ListQueue
import com.zionhuang.innertube.models.WatchEndpoint
import com.vikify.app.playback.queues.YouTubeQueue
import com.vikify.app.db.entities.Song
import com.vikify.app.db.entities.SongEntity
import kotlinx.coroutines.flow.firstOrNull
import com.vikify.app.db.MusicDatabase
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import org.akanework.gramophone.logic.utils.SemanticLyrics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import androidx.compose.ui.graphics.Color

// Ambient Mode types moved to AmbientModeViewModel.kt

/**
 * Visual-only state for UI animation/theming
 * DOES NOT affect playback logic - exists purely for delight
 */
data class PlayerVisualState(
    val expandedFraction: Float = 0f,
    val artworkScale: Float = 1f,
    val backgroundAlpha: Float = 0.92f,
    val glowAlpha: Float = 0.4f,
    val accentColor: Color = Color(0xFFE53935)
)

/**
 * PlayerViewModel (Restored & Connected)
 * 
 * Manages PlayerUIState by bridging to the native PlayerConnection.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    private val lyricsHelper: com.vikify.app.lyrics.LyricsHelper,
    private val downloadUtil: DownloadUtil,
    private val database: MusicDatabase,
    private val playlistRepository: com.vikify.app.data.PlaylistRepository,
    private val jamSessionManager: JamSessionManager,
    private val networkMonitor: com.vikify.app.utils.NetworkMonitor
) : AndroidViewModel(application) {
    
    // We will initialize this from the MainActivity/VikifyApp level if needed,
    // or better, we can inject MediaControllerViewModel and build PlayerConnection here.
    private var playerConnection: PlayerConnection? = null
    
    // Mutex to serialize all state updates, preventing race conditions
    private val stateMutex = Mutex()
    
    private val _uiState = MutableStateFlow(
        PlayerUIState(
            currentTrack = null,
            isPlaying = false,
            progress = 0f
        )
    )
    val uiState: StateFlow<PlayerUIState> = _uiState.asStateFlow()
    
    // Lyrics State - Simple list of SyncedLyric
    private val _lyrics = MutableStateFlow<List<com.vikify.app.vikifyui.components.SyncedLyric>?>(null)
    val lyrics: StateFlow<List<com.vikify.app.vikifyui.components.SyncedLyric>?> = _lyrics.asStateFlow()
    
    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading: StateFlow<Boolean> = _isLyricsLoading.asStateFlow()
    
    // Download State
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()
    
    // Track downloaded song IDs (YouTube IDs)
    private val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds: StateFlow<Set<String>> = _downloadedTrackIds.asStateFlow()
    
    // Queue state - tracks currently in player queue
    private val _queueTracks = MutableStateFlow<List<Track>>(emptyList())
    val queueTracks: StateFlow<List<Track>> = _queueTracks.asStateFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // USER QUEUE (Spotify-style "Add to Queue")
    // Songs explicitly added that play BEFORE context queue advances
    // ═══════════════════════════════════════════════════════════════════════
    private val _userQueueTracks = MutableStateFlow<List<Track>>(emptyList())
    val userQueueTracks: StateFlow<List<Track>> = _userQueueTracks.asStateFlow()
    
    // Context title (e.g., "Starboy" or "Chill Vibes" playlist)
    private val _contextTitle = MutableStateFlow("")
    val contextTitle: StateFlow<String> = _contextTitle.asStateFlow()
    
    // Liked songs from database
    private val _likedSongs = MutableStateFlow<List<com.vikify.app.db.entities.Song>>(emptyList())
    val likedSongs: StateFlow<List<com.vikify.app.db.entities.Song>> = _likedSongs.asStateFlow()
    
    private val _likedSongsCount = MutableStateFlow(0)
    val likedSongsCount: StateFlow<Int> = _likedSongsCount.asStateFlow()
    
    // Downloaded songs from database
    private val _downloadedSongs = MutableStateFlow<List<com.vikify.app.db.entities.Song>>(emptyList())
    val downloadedSongs: StateFlow<List<com.vikify.app.db.entities.Song>> = _downloadedSongs.asStateFlow()
    
    private val _downloadedSongsCount = MutableStateFlow(0)
    val downloadedSongsCount: StateFlow<Int> = _downloadedSongsCount.asStateFlow()
    
    // Sleep Timer State (Coroutine-based)
    private val _sleepTimerState = MutableStateFlow(com.vikify.app.vikifyui.components.SleepTimerState())
    val sleepTimerState: StateFlow<com.vikify.app.vikifyui.components.SleepTimerState> = _sleepTimerState.asStateFlow()
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    
    // Progress loop job - tracked to prevent zombie loops
    private var progressJob: kotlinx.coroutines.Job? = null
    
    // Toast/Snackbar messages
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()
    
    // Visual State - Animation/Theming Only (NO playback logic)
    private val _visualState = MutableStateFlow(PlayerVisualState())
    val visualState: StateFlow<PlayerVisualState> = _visualState.asStateFlow()

    // Offline Mode State
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()
    
// Ambient Mode logic moved to AmbientModeViewModel
    
    // ═══════════════════════════════════════════════════════════════════════
    // JAM MODE - Collaborative Listening
    // ═══════════════════════════════════════════════════════════════════════
    private val _jamSessionState = MutableStateFlow<JamSessionState>(JamSessionState.Idle)
    val jamSessionState: StateFlow<JamSessionState> = _jamSessionState.asStateFlow()
    
    private val _jamError = MutableStateFlow<String?>(null)
    val jamError: StateFlow<String?> = _jamError.asStateFlow()
    
    private val _isJamLoading = MutableStateFlow(false)
    val isJamLoading: StateFlow<Boolean> = _isJamLoading.asStateFlow()
    
    // Current user info for Jam Mode
    private val _currentJamUser = MutableStateFlow<com.vikify.app.vikifyui.components.JamUser?>(null)
    val currentJamUser: StateFlow<com.vikify.app.vikifyui.components.JamUser?> = _currentJamUser.asStateFlow()
    
    // Jam session observation job
    private var jamObserverJob: kotlinx.coroutines.Job? = null
    
    // Chat observation job
    private var jamChatObserverJob: kotlinx.coroutines.Job? = null
    
    // Chat messages for the current Jam session
    private val _jamChatMessages = MutableStateFlow<List<JamChatMessage>>(emptyList())
    val jamChatMessages: StateFlow<List<JamChatMessage>> = _jamChatMessages.asStateFlow()
    
    // Unread chat message count (for showing badge)
    private val _unreadChatCount = MutableStateFlow(0)
    val unreadChatCount: StateFlow<Int> = _unreadChatCount.asStateFlow()
    
    // Whether chat panel is currently open
    private val _isChatOpen = MutableStateFlow(false)
    val isChatOpen: StateFlow<Boolean> = _isChatOpen.asStateFlow()
    
    val greeting: String
        get() = MockData.getGreeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

    init {
        // Load liked songs from database (on IO thread for safety)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.likedSongs(com.vikify.app.constants.SongSortType.CREATE_DATE, true).collect { songs ->
                _likedSongs.value = songs
                _likedSongsCount.value = songs.size
            }
        }
        
        // Load downloaded songs from database (on IO thread for safety)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.downloadedSongs().collect { songs ->
                _downloadedSongs.value = songs
                _downloadedSongsCount.value = songs.size
                
                // CRITICAL FIX: Include BOTH the resolved YouTube ID (id) AND the original Spotify ID (spotifyId)
                // This ensures that "Download All" checks work regardless of which ID the UI uses
                _downloadedTrackIds.value = songs.flatMap { song ->
                    listOfNotNull(song.id, song.song.spotifyId)
                }.toSet()
                
                android.util.Log.d("PlayerViewModel", "Loaded ${songs.size} downloaded songs, ${_downloadedTrackIds.value.size} IDs cached")
            }
        }
        
        // Ambient mode monitoring moved to AmbientModeViewModel

        // Monitor Network State for Offline Mode
        viewModelScope.launch {
            networkMonitor.isOnline.collect { isOnline ->
                _isOffline.value = !isOnline
            }
        }
    }
    
    // Update visual state (colors)
    fun updateAccentColor(color: Color) {
        _visualState.update { it.copy(accentColor = color) }
    }

// Ambient mode methods moved to AmbientModeViewModel

    fun setPlayerConnection(pc: PlayerConnection) {
        if (this.playerConnection != null) return
        this.playerConnection = pc
        
        viewModelScope.launch {
            pc.isPlaying.collect { playing ->
                stateMutex.withLock {
                _uiState.update { it.copy(isPlaying = playing) }
            }
                
                
                // Sync playback state to Jam session (host only)
                syncJamPlaybackState()
            }
        }
        
        // Progress Loop - cancel any existing job to prevent zombie loops
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.isPlaying) {
                     val player = pc.player
                     val current = player.currentPosition
                     val total = player.duration
                     if (total > 0) {
                         stateMutex.withLock {
                         _uiState.update { it.copy(progress = current.toFloat() / total.toFloat()) }
                     }
                }
                }
                kotlinx.coroutines.delay(250) // UI animates between values - less jitter
            }
        }
        
        viewModelScope.launch {
            pc.mediaMetadata.collect { metadata ->
                if (metadata != null) {
                    val newTrack = Track(
                        id = metadata.id ?: "",
                        title = metadata.title ?: "Unknown",
                        artist = metadata.artists.firstOrNull()?.name ?: "Unknown Artist",
                        remoteArtworkUrl = metadata.thumbnailUrl,
                        duration = if (metadata.duration > 30000) metadata.duration.toLong() else metadata.duration.toLong() * 1000L
                    )
                    
                    stateMutex.withLock {
                    _uiState.update { 
                        it.copy(currentTrack = newTrack)
                    }
                    }
                    
                    // Sync track change to Jam session (host only)
                    syncJamTrackChange(newTrack)
                    
                    // Fetch lyrics
                    fetchLyrics(metadata)
                } else {
                    _lyrics.value = null
                }
            }
        }
        
        
        // Observe current song from DB to sync Like button state
        viewModelScope.launch {
            pc.currentSong.collect { songWrapper ->
                stateMutex.withLock {
                    val isLiked = songWrapper?.song?.liked == true
                    _uiState.update { it.copy(isLiked = isLiked) }
                }
            }
        }

        // Track queue windows for Queue display - ONLY UPCOMING SONGS
        viewModelScope.launch {
            pc.queueWindows.collect { windows ->
                val player = pc.player
                val currentIndex = player.currentMediaItemIndex
                
                // Filter to ONLY show songs AFTER the current song
                // This removes already-played songs from the "Up Next" queue
                _queueTracks.value = windows
                    .filterIndexed { index, _ -> index > currentIndex }
                    .mapNotNull { window ->
                        window.mediaItem.metadata?.let { meta ->
                            Track(
                                id = meta.id ?: "",
                                title = meta.title ?: "Unknown",
                                artist = meta.artists.firstOrNull()?.name ?: "Unknown Artist",
                                remoteArtworkUrl = meta.thumbnailUrl,
                                duration = meta.duration.toLong() * 1000L
                            )
                        }
                    }
            }
        }
        
        // Load liked songs from database
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.likedSongs(com.vikify.app.constants.SongSortType.CREATE_DATE, true).collect { songs ->
                _likedSongs.value = songs
                _likedSongsCount.value = songs.size
            }
        }
        
        // Load downloaded songs from database
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            database.downloadedSongs().collect { songs ->
                _downloadedSongs.value = songs
                _downloadedSongsCount.value = songs.size
            }
        }
    }
    
    private fun fetchLyrics(metadata: com.vikify.app.models.MediaMetadata) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLyricsLoading.value = true
            
            try {
                // Timeout after 10 seconds to prevent infinite loading
                val semanticLyrics = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    lyricsHelper.getLyrics(metadata)
                }
                
                if (semanticLyrics != null) {
                    // Debug: Log what type of lyrics we got
                    android.util.Log.d("PlayerViewModel", "Lyrics type: ${semanticLyrics.javaClass.simpleName}")
                    
                    // Convert SemanticLyrics to simple UI model
                    val uiLyrics = when (semanticLyrics) {
                        is org.akanework.gramophone.logic.utils.SemanticLyrics.SyncedLyrics -> {
                            android.util.Log.d("PlayerViewModel", "Got SYNCED lyrics! Lines: ${semanticLyrics.text.size}, First timestamp: ${semanticLyrics.text.firstOrNull()?.start}")
                            semanticLyrics.text.map { line ->
                                com.vikify.app.vikifyui.components.SyncedLyric(
                                    timestamp = line.start.toLong(),
                                    text = line.text
                                )
                            }
                        }
                        is org.akanework.gramophone.logic.utils.SemanticLyrics.UnsyncedLyrics -> {
                            android.util.Log.d("PlayerViewModel", "Got UNSYNCED lyrics - using duration distribution. Lines: ${semanticLyrics.unsyncedText.size}")
                            // For unsynced lyrics, distribute evenly across song duration
                            val totalLines = semanticLyrics.unsyncedText.size
                            val songDurationMs = metadata.duration.toLong() * 1000L  // Convert seconds to ms
                            val intervalMs = if (totalLines > 1) songDurationMs / totalLines else songDurationMs
                            
                            semanticLyrics.unsyncedText.mapIndexed { index, pair ->
                                com.vikify.app.vikifyui.components.SyncedLyric(
                                    timestamp = (index.toLong() * intervalMs),
                                    text = pair.first
                                )
                            }
                        }
                        else -> emptyList()
                    }
                    _lyrics.value = uiLyrics
                } else {
                    _lyrics.value = null
                }
            } catch (e: Exception) {
                _lyrics.value = null
            } finally {
                _isLyricsLoading.value = false
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VISUAL STATE METHODS (Animation/Theming Only - No Playback Logic)
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Called when player expands/collapses for smooth animation states
     */
    fun onPlayerExpanded(expanded: Boolean) {
        _visualState.update {
            it.copy(
                expandedFraction = if (expanded) 1f else 0f,
                artworkScale = if (expanded) 1.15f else 1f,
                glowAlpha = if (expanded) 0.7f else 0.4f
            )
        }
    }
    
    /**
     * Called when artwork dominant color is extracted for dynamic theming
     */
    fun onArtworkColorExtracted(color: Color) {
        _visualState.update { it.copy(accentColor = color) }
    }

    fun togglePlayPause() {
        val player = playerConnection?.player ?: return
        if (player.isPlaying) player.pause() else player.play()
    }
    
    fun playTrack(track: Track) {
        // === SILENT MIGRATION: Handle unresolved Spotify tracks ===
        // Tracks from Spotify skeleton import have UNRESOLVED_ prefix
        if (track.id.startsWith("UNRESOLVED_")) {
            // Extract spotify ID and resolve on-demand
            val spotifyId = track.id.removePrefix("UNRESOLVED_")
            android.util.Log.d("PlayerViewModel", "On-demand resolution for: ${track.title} (Spotify: $spotifyId)")
            
            _uiState.update { it.copy(isResolvingTrack = true) }
            
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val searchQuery = "${track.spotifyTitle ?: track.title} ${track.spotifyArtist ?: track.artist}"
                    val searchResult = com.zionhuang.innertube.YouTube.search(
                        searchQuery, 
                        com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG
                    ).getOrNull()
                    
                    val songs = searchResult?.items?.filterIsInstance<com.zionhuang.innertube.models.SongItem>() ?: emptyList()
                    
                    // IMPROVED MATCHING: Use findBestMatchingSong instead of simple duration heuristic
                    // This ensures the title matches, not just the duration (prevents "Kadalini" vs "Nuvve Nuvve" mixups)
                    val matchedSong = findBestMatchingSong(
                        songs, 
                        track.spotifyTitle ?: track.title, 
                        track.spotifyArtist ?: track.artist
                    )
                    
                    if (matchedSong != null) {
                        // Update database with resolved ID
                        try {
                            database.updateYouTubeIdBySpotifyId(spotifyId, matchedSong.id)
                            android.util.Log.d("PlayerViewModel", "Resolved: ${track.title} -> ${matchedSong.id}")
                        } catch (e: Exception) {
                            android.util.Log.e("PlayerViewModel", "Failed to update DB: ${e.message}")
                        }
                        
                        // Play the resolved track
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            playMediaMetadata(matchedSong.toMediaMetadata())
                            _uiState.update { it.copy(isResolvingTrack = false) }
                        }
                    } else {
                        android.util.Log.e("PlayerViewModel", "Could not find acceptable matches for: ${track.title}")
                        
                        // Fallback: Use strict duration matching if title matching failed but we need SOMETHING
                        val durationSec = if (track.duration > 0) (track.duration / 1000).toInt() else 0
                        val durationMatch = if (durationSec > 0) {
                             songs.filter { kotlin.math.abs((it.duration ?: 0) - durationSec) <= 2 }.firstOrNull() 
                        } else null
                        
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                             if (durationMatch != null) {
                                  // Risky but better than nothing?
                                  playMediaMetadata(durationMatch.toMediaMetadata())
                             } else {
                                  _toastMessage.emit("Could not resolve song: ${track.title}")
                             }
                             _uiState.update { it.copy(isResolvingTrack = false) }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "On-demand resolution failed: ${e.message}")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _uiState.update { it.copy(isResolvingTrack = false) }
                    }
                }
            }
            return
        }
        
        // === NORMAL PLAYBACK: Regular YouTube/local tracks ===
        val ref = track.originalBackendRef
        
        when (ref) {
            is com.vikify.app.db.entities.Song -> {
                // === SELF-HEALING: Verify existing resolution ===
                // If we have a Spotify Title, check if the resolved YouTube title (ref.title) actually matches.
                // This fixes cases where a previous bad resolution (e.g. "Kadalini") was saved for a request (e.g. "Nuvve Nuvve")
                var needsReResolution = false
                
                // Properties are on the inner SongEntity, not the wrapper!
                if (ref.song.spotifyTitle != null) {
                    val score = calculateSimilarity(
                        normalizeForMatch(ref.title), 
                        normalizeForMatch(ref.song.spotifyTitle!!)
                    )
                    
                    // If similarity is low (< 0.4), assume the saved ID is wrong and try to resolve again
                    if (score < 0.4) {
                        android.util.Log.w("PlayerViewModel", "Bad match detected in DB: '${ref.title}' vs '${ref.song.spotifyTitle}' (Score: $score). Triggering self-healing.")
                        needsReResolution = true
                    }
                }
                
                if (needsReResolution) {
                     // Construct a temporary track with UNRESOLVED prefix to force strict search
                     val fixTrack = track.copy(
                         id = "UNRESOLVED_${ref.song.spotifyId}",
                         title = ref.song.spotifyTitle ?: track.title,
                         artist = ref.song.spotifyArtist ?: track.artist
                     )
                     playTrack(fixTrack) // Recurse to trigger the strict search logic above
                } else {
                    playMediaMetadata(ref.toMediaMetadata())
                }
            }
            is com.zionhuang.innertube.models.SongItem -> {
                try {
                    playMediaMetadata(ref.toMediaMetadata())
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Error converting SongItem to Metadata: ${e.message}")
                    e.printStackTrace()
                    // If conversion fails, fallback to search (continue to else block logic if ref was null, but here we return)
                    // Better to launch the search fallback here explicitly
                    searchAndPlay(track) 
                }
            }
            else -> {
                // If we have a direct YouTube ID, prefer it over searching
                if (track.youtubeId != null) {
                    val metadata = com.vikify.app.models.MediaMetadata(
                        id = track.youtubeId,
                        title = track.title,
                        artists = listOf(com.vikify.app.models.MediaMetadata.Artist(id = null, name = track.artist)),
                        duration = (track.duration / 1000).toInt(),
                        thumbnailUrl = track.remoteArtworkUrl,
                        genre = null
                    )
                    playMediaMetadata(metadata)
                } else {
                    searchAndPlay(track)
                }
            }
        }
    }

    /**
     * Search YouTube for tracks (for Jam Mode search)
     */
    suspend fun searchTracks(query: String): List<Track> {
        return try {
            val searchResult = com.zionhuang.innertube.YouTube.search(query, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
            searchResult?.items?.filterIsInstance<com.zionhuang.innertube.models.SongItem>()?.map { song ->
                Track(
                    id = song.id,
                    title = song.title ?: "Unknown",
                    artist = song.artists.firstOrNull()?.name ?: "Unknown Artist",
                    remoteArtworkUrl = song.thumbnail,
                    duration = (song.duration ?: 0) * 1000L,
                    youtubeId = song.id,
                    originalBackendRef = song
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Search failed: ${e.message}")
            emptyList()
        }
    }

    private fun searchAndPlay(track: Track) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Search YouTube for this track
                val searchQuery = "${track.title} ${track.artist}"
                val searchResult = com.zionhuang.innertube.YouTube.search(searchQuery, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                
                // Find best matching song - not just the first one
                val songs = searchResult?.items?.filterIsInstance<com.zionhuang.innertube.models.SongItem>() ?: emptyList()
                val matchedSong = findBestMatchingSong(songs, track.title, track.artist)
                
                if (matchedSong != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        playMediaMetadata(matchedSong.toMediaMetadata())
                    }
                } else if (songs.isNotEmpty()) {
                    // Fallback to first song if no good match found
                    Log.w("PlayerViewModel", "No exact match found for '${track.title}', using first result")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        playMediaMetadata(songs.first().toMediaMetadata())
                    }
                } else {
                    // No results with full query - try simplified searches
                    Log.w("PlayerViewModel", "No results for '${track.title} ${track.artist}', trying title only...")
                    
                    // Try 1: Title only search
                    val titleOnlyResult = com.zionhuang.innertube.YouTube.search(track.title, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    val titleOnlySongs = titleOnlyResult?.items?.filterIsInstance<com.zionhuang.innertube.models.SongItem>() ?: emptyList()
                    
                    if (titleOnlySongs.isNotEmpty()) {
                        val matchedSong = findBestMatchingSong(titleOnlySongs, track.title, track.artist)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            playMediaMetadata((matchedSong ?: titleOnlySongs.first()).toMediaMetadata())
                        }
                    } else {
                        // Try 2: Strip common suffixes and parentheticals from title
                        val cleanTitle = track.title
                            .replace(Regex("\\s*\\(.*?\\)"), "")  // Remove (feat. X), (Remix), etc
                            .replace(Regex("\\s*\\[.*?\\]"), "")  // Remove [Official Video], etc
                            .replace(Regex("\\s*-.*"), "")        // Remove " - Something" suffixes
                            .trim()
                        
                        if (cleanTitle != track.title && cleanTitle.length > 2) {
                            val cleanResult = com.zionhuang.innertube.YouTube.search("$cleanTitle ${track.artist}", com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                            val cleanSongs = cleanResult?.items?.filterIsInstance<com.zionhuang.innertube.models.SongItem>() ?: emptyList()
                            
                            if (cleanSongs.isNotEmpty()) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    playMediaMetadata(cleanSongs.first().toMediaMetadata())
                                }
                            } else {
                                Log.e("PlayerViewModel", "Could not find YouTube match for '${track.title}' by '${track.artist}'")
                                _toastMessage.emit("Could not find matching song")
                            }
                        } else {
                            Log.e("PlayerViewModel", "Could not find YouTube match for '${track.title}' by '${track.artist}'")
                            _toastMessage.emit("Could not find matching song")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error searching for track: ${e.message}")
                e.printStackTrace()
                _toastMessage.emit("Playback failed: ${e.message}")
            }
        }
    }
    
    /**
     * QUEUE ENTIRE PLAYLIST and start at specific index.
     * This fixes the issue where only one song plays from Spotify/other lists.
     */
    fun playFromPlaylist(playlist: List<Track>, index: Int, title: String = "Playlist") {
        playPlaylistWithIndex(playlist, index, title)
    }
    
    fun playPlaylistWithIndex(tracks: List<Track>, startIndex: Int, title: String = "Playlist") {
        if (tracks.isEmpty()) return
        
        // Update context title for UI display
        _contextTitle.value = title
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val queueItems = tracks.map { track ->
                // Check if we have a direct backend ref (e.g. SongEntity or Innertube SongItem)
                // If not (e.g. Spotify), construct basic metadata to be resolved later
                val ref = track.originalBackendRef
                
                try {
                    val metadata = when (ref) {
                        is com.vikify.app.db.entities.Song -> ref.toMediaMetadata()
                        is com.zionhuang.innertube.models.SongItem -> ref.toMediaMetadata()
                        else -> com.vikify.app.models.MediaMetadata(
                                id = track.youtubeId ?: track.id,
                                title = track.title,
                                artists = listOf(com.vikify.app.models.MediaMetadata.Artist(name = track.artist, id = null)),
                                duration = (track.duration / 1000).toInt(),
                                thumbnailUrl = track.remoteArtworkUrl,
                                genre = null
                        )
                    }
                    // CRITICAL FIX: If metadata ID is UNRESOLVED but we have a resolved ID in track, use it!
                    if (metadata.id.startsWith("UNRESOLVED_") && track.youtubeId != null) {
                         metadata.copy(id = track.youtubeId)
                    } else {
                         metadata
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlayerViewModel", "Error converting track to metadata: ${e.message}")
                    // Fallback to basic metadata from Track object
                    com.vikify.app.models.MediaMetadata(
                        id = track.youtubeId ?: track.id,
                        title = track.title,
                        artists = listOf(com.vikify.app.models.MediaMetadata.Artist(name = track.artist, id = null)),
                        duration = (track.duration / 1000).toInt(),
                        thumbnailUrl = track.remoteArtworkUrl,
                        genre = null
                    )
                }
            }.toMutableList()
            
            val validIndex = startIndex.coerceIn(0, queueItems.size - 1)
            
            // 1. Synchronously resolve the STARTING track (if needed)
            // This ensures it plays immediately without error
            val startTrack = tracks[validIndex]
            val startId = queueItems[validIndex].id
            
            // FIX: Resolve if ID is still UNRESOLVED, even if it's a Song entity!
            if (startId.startsWith("UNRESOLVED_") || (startTrack.youtubeId == null && startTrack.originalBackendRef !is com.vikify.app.db.entities.Song && startTrack.originalBackendRef !is com.zionhuang.innertube.models.SongItem)) {
                try {
                    android.util.Log.d("PlayerViewModel", "Synchronously resolving start track: ${startTrack.title}")
                    val searchQuery = "${startTrack.title} ${startTrack.artist}"
                    val searchResult = com.zionhuang.innertube.YouTube.search(searchQuery, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    val songs = searchResult?.items?.filterIsInstance<com.zionhuang.innertube.models.SongItem>() ?: emptyList()
                    val matchedSong = findBestMatchingSong(songs, startTrack.title, startTrack.artist) ?: songs.firstOrNull()
                    
                    if (matchedSong != null) {
                        queueItems[validIndex] = matchedSong.toMediaMetadata()
                        android.util.Log.d("PlayerViewModel", "Resolved start track to: ${matchedSong.id}")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                playerConnection?.playQueue(
                    ListQueue(
                        title = title,
                        items = queueItems,
                        startIndex = validIndex
                    )
                )
            }
            
            // 2. Resolve the REST in background
            tracks.forEachIndexed { index, track ->
                // Skip the one we already resolved
                if (index == validIndex) return@forEachIndexed
                
                // Skip if already has a valid ID (not UNRESOLVED_)
                val currentId = queueItems[index].id
                if (!currentId.startsWith("UNRESOLVED_") && !track.id.startsWith("UNRESOLVED_") && track.youtubeId != null) return@forEachIndexed

                try {
                    // Optimization: Delay slightly to let UI settle
                    if (index == 0) kotlinx.coroutines.delay(100) 
                    
                    val query = "${track.title} ${track.artist}"
                    val result = com.zionhuang.innertube.YouTube.search(query, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    val trackSongs = result?.items?.filterIsInstance<com.zionhuang.innertube.models.SongItem>() ?: emptyList()
                    val song = findBestMatchingSong(trackSongs, track.title, track.artist) ?: trackSongs.firstOrNull()

                    if (song != null) {
                        val resolvedMetadata = song.toMediaMetadata()
                        
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            val player = playerConnection?.player
                            // Map list index to player window index (assuming 1:1 mapping for playQueue)
                            val playerIndex = index
                            if (player != null && playerIndex < player.mediaItemCount) {
                                // Don't replace playing item if it's playing (it's fine)
                                if (player.currentMediaItemIndex == playerIndex) {
                                     // checks..
                                } else {
                                     // Only replace if the ID is different
                                     val currentItem = player.getMediaItemAt(playerIndex)
                                     if (currentItem.mediaId != resolvedMetadata.id) {
                                         player.removeMediaItem(playerIndex)
                                         player.addMediaItem(playerIndex, resolvedMetadata.toMediaItem())
                                         android.util.Log.d("PlayerViewModel", "Background resolved index $index: ${resolvedMetadata.id}")
                                     }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Find the best matching song from search results.
     * This prevents playing wrong songs (covers, remixes, wrong language versions).
     */
    private fun findBestMatchingSong(
        songs: List<com.zionhuang.innertube.models.SongItem>,
        targetTitle: String,
        targetArtist: String
    ): com.zionhuang.innertube.models.SongItem? {
        if (songs.isEmpty()) return null
        
        val normalizedTitle = normalizeForMatch(targetTitle)
        val normalizedArtist = normalizeForMatch(targetArtist)
        
        // Score each song based on how well it matches
        val scoredSongs = songs.mapNotNull { song ->
            val songTitle = normalizeForMatch(song.title)
            val songArtist = normalizeForMatch(song.artists.joinToString { it.name })
            
            val titleScore = calculateSimilarity(normalizedTitle, songTitle)
            val artistScore = calculateSimilarity(normalizedArtist, songArtist)
            
            // Combined score: title is more important than artist
            val totalScore = (titleScore * 0.7) + (artistScore * 0.3)
            
            // Require at least 40% match (lowered from 60% for better fuzzy matching)
            if (totalScore >= 0.4) {
                song to totalScore
            } else {
                null
            }
        }
        
        // Return the highest scoring song
        return scoredSongs.maxByOrNull { it.second }?.first
    }
    
    /**
     * Normalize a string for matching (lowercase, remove special chars)
     */
    private fun normalizeForMatch(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "") // Remove special chars
            .replace(Regex("\\s+"), " ")        // Normalize spaces
            .trim()
    }
    
    /**
     * Calculate similarity between two strings (0.0 to 1.0)
     * Uses a simple word overlap approach
     */
    private fun calculateSimilarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        
        // Check if one contains the other (common for multilingual matches)
        if (a.contains(b) || b.contains(a)) return 0.9
        
        // Word overlap scoring
        val wordsA = a.split(" ").filter { it.length > 1 }.toSet()
        val wordsB = b.split(" ").filter { it.length > 1 }.toSet()
        
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0
        
        val intersection = wordsA.intersect(wordsB).size
        val union = wordsA.union(wordsB).size
        
        return intersection.toDouble() / union.toDouble()
    }

    fun playAlbum(album: Album) {
        val ref = album.originalBackendRef
        when (ref) {
            is com.zionhuang.innertube.models.AlbumItem -> {
                playerConnection?.playQueue(
                    queue = YouTubeQueue(WatchEndpoint(playlistId = ref.id))
                )
            }
        }
    }
    
    fun playMediaMetadata(metadata: com.vikify.app.models.MediaMetadata) {
        // Use playSingleTrack to enable Autoplay/Radio mode automatically
        playerConnection?.playSingleTrack(metadata)
    }

    fun updateProgress(progress: Float) {
        val player = playerConnection?.player ?: return
        val duration = player.duration
        if (duration > 0) {
            player.seekTo((progress * duration).toLong())
        }
    }
    
    fun expandPlayer() {
        _uiState.update { it.copy(density = 1f) }
    }
    
    fun collapsePlayer() {
        _uiState.update { it.copy(density = 0f) }
    }
    
    fun toggleQueue() {
        _uiState.update { it.copy(showQueue = !it.showQueue) }
    }
    
    fun toggleLyrics() {
        _uiState.update { it.copy(showLyrics = !it.showLyrics) }
    }
    
    fun closeOverlays() {
        _uiState.update { it.copy(showQueue = false, showLyrics = false) }
    }
    
    fun skipNext() {
        playerConnection?.player?.seekToNext()
    }
    
    fun skipPrevious() {
        playerConnection?.player?.seekToPrevious()
    }
    
    fun toggleShuffle() {
        val player = playerConnection?.player ?: return
        val newValue = !player.shuffleModeEnabled
        player.shuffleModeEnabled = newValue
        _uiState.update { it.copy(shuffleEnabled = newValue) }
    }
    
    fun toggleRepeat() {
        val player = playerConnection?.player ?: return
        // Cycle: 0 (off) -> 1 (all) -> 2 (one) -> 0
        val newMode = (player.repeatMode + 1) % 3
        _uiState.update { it.copy(repeatMode = newMode) }
    }

    /**
     * Add current track to a local playlist
     */
    fun addToPlaylist(playlistId: String) {
        val uiTrack = _uiState.value.currentTrack ?: return
        
        viewModelScope.launch {
            try {
                // 1. Ensure song exists in DB
                // Convert UI Track -> MediaMetadata -> SongEntity
                val metadata = com.vikify.app.models.MediaMetadata(
                    id = uiTrack.id,
                    title = uiTrack.title,
                    artists = listOf(com.vikify.app.models.MediaMetadata.Artist(name = uiTrack.artist, id = null)),
                    duration = (uiTrack.duration / 1000).toInt(),
                    thumbnailUrl = uiTrack.remoteArtworkUrl,
                    genre = null
                )
                
                // Insert/Update song in DB
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    database.insert(metadata)
                }

                // 2. Add relation
                playlistRepository.addSongToPlaylist(playlistId, uiTrack.id)
                
                // 3. Feedback
                _toastMessage.emit("Added to playlist")
            } catch (e: Exception) {
                _toastMessage.emit("Failed to add to playlist")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Get local playlists for picker
     */
    val localPlaylists = playlistRepository.getLocalPlaylists()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Lazily, emptyList())

    fun createLocalPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createLocalPlaylist(name)
        }
    }

    /**
     * Load local playlist tracks
     */
    suspend fun loadLocalPlaylist(playlistId: String): List<Track>? {
        // Check if playlist exists
        val playlist = playlistRepository.getPlaylist(playlistId).firstOrNull() ?: return null
        
        // Get songs
        val songs = playlistRepository.getPlaylistSongs(playlistId).firstOrNull() ?: emptyList()
        
        // Convert to UI Track
        return songs.map { song ->
            Track(
                id = song.song.id, // DB ID
                title = song.song.title,
                artist = song.song.artists.joinToString(", ") { it.name },
                remoteArtworkUrl = song.song.thumbnailUrl,
                duration = song.song.song.duration * 1000L,
                originalBackendRef = song.song
            )
        }
    }

    /**
     * Toggle like status for the current track.
     * Uses OPTIMISTIC UI UPDATE with rollback on database error.
     * 
     * Flow:
     * 1. Update UI immediately (snappy feedback)
     * 2. Persist to database in background
     * 3. Rollback UI if database write fails
     */
    fun toggleLike() {
        val track = _uiState.value.currentTrack ?: return
        
        // 1. OPTIMISTIC UPDATE (Instant Feedback for snappy UX)
        val originalState = _uiState.value.isLiked
        val newState = !originalState
        _uiState.update { it.copy(isLiked = newState) }
        
        // 2. PERSIST TO DATABASE (Background)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val existingSong = database.song(track.id).firstOrNull()?.song
                
                if (existingSong != null) {
                    // Song exists - use localToggleLike() for safe local toggle
                    val updatedSong = existingSong.copy(
                        liked = newState,
                        likedDate = if (newState) java.time.LocalDateTime.now() else null
                    )
                    database.update(updatedSong)
                    android.util.Log.d("PlayerViewModel", "Like toggled for ${track.id}: $newState")
                } else if (newState) {
                    // Song not in database - only insert if we're liking it
                    // Note: Artist info stored via SongArtistMap junction table, not in SongEntity
                    val newSong = com.vikify.app.db.entities.SongEntity(
                        id = track.id,
                        title = track.title,
                        thumbnailUrl = track.remoteArtworkUrl,
                        duration = if (track.duration > 0) (track.duration / 1000).toInt() else 0,
                        localPath = null,
                        liked = true,
                        likedDate = java.time.LocalDateTime.now(),
                        inLibrary = java.time.LocalDateTime.now()
                    )
                    database.insert(newSong)
                    android.util.Log.d("PlayerViewModel", "New song liked: ${track.id}")
                }
                // Note: If unliking a song that doesn't exist, we just don't do anything
                
            } catch (e: Exception) {
                // 3. ROLLBACK ON ERROR - Revert UI to original state
                android.util.Log.e("PlayerViewModel", "Failed to toggle like: ${e.message}", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.update { it.copy(isLiked = originalState) }
                    _toastMessage.emit("Failed to save like status")
                }
            }
        }
    }

    
    /**
     * Remove a track from the queue at the specified index.
     * Syncs with ExoPlayer and updates UI.
     */
    fun removeFromQueue(index: Int) {
        playerConnection?.service?.let { service ->
            val removed = service.queueBoard.removeCurrentQueueSong(index)
            if (removed) {
                Log.d("PlayerViewModel", "Removed track at index $index from queue")
            } else {
                Log.e("PlayerViewModel", "Failed to remove track at index $index")
            }
        }
    }
    
    /**
     * Move a track in the queue from one position to another.
     * Syncs with ExoPlayer for real-time reorder.
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playerConnection?.service?.let { service ->
            service.queueBoard.moveSong(fromIndex, toIndex)
            Log.d("PlayerViewModel", "Moved queue item from $fromIndex to $toIndex")
        }
    }
    
    /**
     * Download the currently playing track to device storage.
     * Uses DownloadUtil which handles both ExoPlayer cache and external storage.
     */
    fun downloadCurrentTrack() {
        val track = _uiState.value.currentTrack ?: return
        
        // Check if already downloaded (by original ID or YouTube ID)
        val currentTrackId = track.youtubeId ?: track.id
        if (_downloadedTrackIds.value.contains(track.id) || _downloadedTrackIds.value.contains(currentTrackId)) {
            viewModelScope.launch {
                _toastMessage.emit("\"${track.title}\" is already downloaded")
            }
            return
        }
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Get the MediaMetadata from the track
                val ref = track.originalBackendRef
                var metadata: com.vikify.app.models.MediaMetadata? = when (ref) {
                    is com.vikify.app.db.entities.Song -> ref.toMediaMetadata()
                    is com.zionhuang.innertube.models.SongItem -> ref.toMediaMetadata()
                    else -> null
                }
                
                // If no backend ref, try to find from database by Spotify ID
                if (metadata == null && track.spotifyId != null) {
                    val existingSong = database.songBySpotifyId(track.spotifyId).first()
                    if (existingSong != null && !existingSong.id.startsWith("UNRESOLVED_")) {
                        metadata = com.vikify.app.models.MediaMetadata(
                            id = existingSong.id,
                            title = track.title,
                            artists = listOf(com.vikify.app.models.MediaMetadata.Artist(id = null, name = track.artist)),
                            duration = (track.duration / 1000).toInt(),
                            thumbnailUrl = track.remoteArtworkUrl,
                            genre = null
                        )
                    }
                }
                
                // If still no metadata, search YouTube
                if (metadata == null) {
                    Log.d("PlayerViewModel", "Searching YouTube for download: ${track.title} - ${track.artist}")
                    val searchQuery = "${track.spotifyTitle ?: track.title} ${track.spotifyArtist ?: track.artist}"
                    val searchResult = com.zionhuang.innertube.YouTube.search(searchQuery, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    val songs = searchResult?.items?.filterIsInstance<com.zionhuang.innertube.models.SongItem>() ?: emptyList()
                    val song = findBestMatchingSong(songs, track.spotifyTitle ?: track.title, track.spotifyArtist ?: track.artist) ?: songs.firstOrNull()
                    
                    if (song != null) {
                        metadata = song.toMediaMetadata()
                        
                        // Cache the resolved ID for future
                        if (track.spotifyId != null) {
                            try {
                                database.updateYouTubeIdBySpotifyId(track.spotifyId, song.id)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }
                }
                
                if (metadata != null) {
                    // CRITICAL: Ensure song exists in database BEFORE downloading
                    // Otherwise updateDownloadStatus won't find it and song won't appear in Downloads
                    database.insert(metadata)
                    
                    downloadUtil.download(metadata)
                    _downloadedTrackIds.update { it + track.id }
                    _downloadedTrackIds.update { it + metadata.id } // Also track by YouTube ID
                    _toastMessage.emit("Downloading \"${track.title}\"")
                } else {
                    _toastMessage.emit("Could not find track for download")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("Download failed: ${e.message}")
            }
        }
    }
    
    /**
     * Check if a track is currently downloaded
     */
    fun isTrackDownloaded(trackId: String): Boolean {
        return _downloadedTrackIds.value.contains(trackId)
    }
    
    /**
     * Seek to a specific track in the current queue by its ID.
     * FIX: Find the song in ExoPlayer's actual queue, not the filtered UI list.
     */
    fun seekToQueueItem(track: Track) {
        val player = playerConnection?.player ?: return
        
        // Find the track's position in ExoPlayer's actual queue by media ID
        val playerIndex = (0 until player.mediaItemCount).firstOrNull { i ->
            player.getMediaItemAt(i).mediaId == track.id
        }
        
        if (playerIndex != null && playerIndex >= 0) {
            player.seekTo(playerIndex, 0)
            player.play()
            android.util.Log.d("PlayerViewModel", "Seeking to queue item: ${track.title} at ExoPlayer index $playerIndex")
        } else {
            android.util.Log.w("PlayerViewModel", "Track ${track.id} not found in ExoPlayer queue")
        }
    }
    
    /**
     * Add a track to the queue (from swipe gesture)
     * Uses cached YouTube search to avoid rate limiting
     */
    fun addToQueue(track: Track) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val mediaItem = getYouTubeMediaItem(track)
                
                if (mediaItem != null) {
                    // CRITICAL: NonCancellable ensures queue add survives ViewModel destruction
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
                        val player = playerConnection?.player ?: return@withContext
                        
                        // DUPLICATE CHECK: Prevent queue spam
                        val isDuplicate = (0 until player.mediaItemCount).any { i ->
                            player.getMediaItemAt(i).mediaId == mediaItem.mediaId
                        }
                        
                        if (!isDuplicate) {
                            player.addMediaItem(mediaItem)
                            _toastMessage.emit("Added \"${track.title}\" to queue")
                        } else {
                            _toastMessage.emit("\"${track.title}\" is already in queue")
                        }
                    }
                } else {
                    _toastMessage.emit("Could not find track on YouTube")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("Failed to add to queue - try again")
            }
        }
    }
    
    // Rate limiting: track last API call time to prevent YouTube throttling
    private var lastYouTubeApiCall = 0L
    private val apiCallDelayMs = 500L // Minimum 500ms between YouTube API calls
    
    // Spotify → YouTube MediaItem cache (avoids repeated searches for same song)
    // Key: "title|artist", Value: MediaItem
    private val spotifyToYouTubeCache = java.util.concurrent.ConcurrentHashMap<String, androidx.media3.common.MediaItem>()
    
    /**
     * Get or search for a YouTube MediaItem for a Spotify track
     * Priority:
     * 1. Pre-resolved youtubeId in Track (fastest, no API call)
     * 2. originalBackendRef if it's a SongItem (no API call)
     * 3. Cache lookup (no API call)
     * 4. YouTube search (API call, rate-limited)
     */
    private suspend fun getYouTubeMediaItem(track: Track): androidx.media3.common.MediaItem? {
        // 1. Check for pre-resolved YouTube ID (fastest path)
        track.youtubeId?.let { ytId ->
            android.util.Log.d("PlayerViewModel", "Using pre-resolved YouTube ID for ${track.title}")
            val metadata = com.vikify.app.models.MediaMetadata(
                id = ytId,
                title = track.title,
                artists = listOf(com.vikify.app.models.MediaMetadata.Artist(id = null, name = track.artist)),
                duration = track.duration.toInt().coerceAtLeast(0),
                thumbnailUrl = track.remoteArtworkUrl,
                genre = null
            )
            return metadata.toMediaItem()
        }
        
        // 2. Check originalBackendRef
        when (val ref = track.originalBackendRef) {
            is com.zionhuang.innertube.models.SongItem -> {
                android.util.Log.d("PlayerViewModel", "Using originalBackendRef for ${track.title}")
                return ref.toMediaMetadata().toMediaItem()
            }
            is com.vikify.app.db.entities.Song -> {
                return ref.toMediaMetadata().toMediaItem()
            }
        }
        
        val cacheKey = "${track.title}|${track.artist}".lowercase()
        
        // 3. Check cache
        spotifyToYouTubeCache[cacheKey]?.let { 
            android.util.Log.d("PlayerViewModel", "Cache HIT for ${track.title}")
            return it 
        }
        
        // 4. Rate-limited YouTube search (last resort)
        val now = System.currentTimeMillis()
        val timeSinceLastCall = now - lastYouTubeApiCall
        if (timeSinceLastCall < apiCallDelayMs) {
            kotlinx.coroutines.delay(apiCallDelayMs - timeSinceLastCall)
        }
        lastYouTubeApiCall = System.currentTimeMillis()
        
        android.util.Log.d("PlayerViewModel", "Cache MISS, searching YouTube for ${track.title}")
        val searchQuery = "${track.title} ${track.artist}"
        val searchResult = com.zionhuang.innertube.YouTube.search(searchQuery, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
        val songs = searchResult?.items?.filterIsInstance<com.zionhuang.innertube.models.SongItem>() ?: emptyList()
        val song = findBestMatchingSong(songs, track.title, track.artist) ?: songs.firstOrNull()
        
        return song?.let {
            val metadata = it.toMediaMetadata()
            val mediaItem = metadata.toMediaItem()
            spotifyToYouTubeCache[cacheKey] = mediaItem
            android.util.Log.d("PlayerViewModel", "Cached ${track.title} -> ${mediaItem.mediaId}")
            mediaItem
        }
    }
    
    /**
     * Add a track to play next (after current song)
     * Uses cached YouTube search to avoid rate limiting
     */
    fun playNext(track: Track) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
               val mediaItem = getYouTubeMediaItem(track)
                
                if (mediaItem != null) {
                    // CRITICAL: NonCancellable ensures queue add survives ViewModel destruction
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
                        val player = playerConnection?.player ?: return@withContext
                        
                        // DUPLICATE CHECK: Prevent queue spam
                        val isDuplicate = (0 until player.mediaItemCount).any { i ->
                            player.getMediaItemAt(i).mediaId == mediaItem.mediaId
                        }
                        
                        if (!isDuplicate) {
                            // Insert at current position + 1 (after current song)
                            val insertIndex = player.currentMediaItemIndex + 1
                            player.addMediaItem(insertIndex, mediaItem)
                            _toastMessage.emit("Playing \"${track.title}\" next")
                        } else {
                            _toastMessage.emit("\"${track.title}\" is already in queue")
                        }
                    }
                } else {
                    _toastMessage.emit("Could not find track on YouTube")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("Failed to add track - try again")
            }
        }
    }
    
    /**
     * Like/Unlike a track by its video ID
     * For use from context menu
     */
    fun likeTrackById(track: Track) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Search for the song to get full metadata
                val searchQuery = "${track.title} ${track.artist}"
                val searchResult = com.zionhuang.innertube.YouTube.search(searchQuery, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                val song = searchResult?.items?.filterIsInstance<com.zionhuang.innertube.models.SongItem>()?.firstOrNull()
                
                if (song != null) {
                    // Check if already liked
                    val existingSong = database.song(song.id).first()
                    
                    if (existingSong != null) {
                        // Toggle like status
                        database.update(existingSong.song.toggleLike())
                        if (existingSong.song.liked) {
                            _toastMessage.emit("Removed from Liked Songs")
                        } else {
                            _toastMessage.emit("Added to Liked Songs")
                        }
                    } else {
                        // Song not in library - add it first, then like
                        _toastMessage.emit("Adding to library...")
                    }
                } else {
                    _toastMessage.emit("Could not find track")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("Failed to update liked status")
            }
        }
    }
    
    /**
     * Check if a track is liked
     */
    fun isTrackLiked(trackId: String): Boolean {
        return likedSongs.value.any { it.song.id == trackId }
    }
    
    /**
     * Play a liked song from the database
     */
    fun playLikedSong(song: com.vikify.app.db.entities.Song) {
        val metadata = song.toMediaMetadata()
        playerConnection?.playQueue(
            ListQueue(title = "Liked Songs", items = listOf(metadata), startIndex = 0)
        )
        _uiState.update { 
            it.copy(
                currentTrack = Track(
                    id = song.song.id,
                    title = song.song.title,
                    artist = song.artists.joinToString { it.name },
                    remoteArtworkUrl = song.song.thumbnailUrl
                ),
                isPlaying = true
            )
    }
    }
    
    /**
     * Shuffle play all liked songs
     */
    fun shufflePlayLikedSongs(songs: List<com.vikify.app.db.entities.Song>) {
        if (songs.isEmpty()) return
        
        val shuffled = songs.shuffled()
        val metadataList = shuffled.map { it.toMediaMetadata() }
        
        playerConnection?.playQueue(
            ListQueue(title = "Liked Songs (Shuffled)", items = metadataList, startIndex = 0)
        )
        playerConnection?.player?.shuffleModeEnabled = true
        _uiState.update { it.copy(shuffleEnabled = true) }
        
        val first = shuffled.first()
        _uiState.update { 
            it.copy(
                currentTrack = Track(
                    id = first.song.id,
                    title = first.song.title,
                    artist = first.artists.joinToString { it.name },
                    remoteArtworkUrl = first.song.thumbnailUrl
                ),
                isPlaying = true
            )
        }
    }
    
    /**
     * Play a downloaded song from the database
     */
    fun playDownloadedSong(song: com.vikify.app.db.entities.Song) {
        val metadata = song.toMediaMetadata()
        playerConnection?.playQueue(
            ListQueue(title = "Downloads", items = listOf(metadata), startIndex = 0)
        )
        _uiState.update { 
            it.copy(
                currentTrack = Track(
                    id = song.song.id,
                    title = song.song.title,
                    artist = song.artists.joinToString { it.name },
                    remoteArtworkUrl = song.song.thumbnailUrl
                ),
                isPlaying = true
            )
    }
    }
    
    /**
     * Shuffle play all downloaded songs
     */
    fun shufflePlayDownloadedSongs(songs: List<com.vikify.app.db.entities.Song>) {
        if (songs.isEmpty()) return
        
        val shuffled = songs.shuffled()
        val metadataList = shuffled.map { it.toMediaMetadata() }
        
        playerConnection?.playQueue(
            ListQueue(title = "Downloads (Shuffled)", items = metadataList, startIndex = 0)
        )
        playerConnection?.player?.shuffleModeEnabled = true
        _uiState.update { it.copy(shuffleEnabled = true) }
        
        val first = shuffled.first()
        _uiState.update { 
            it.copy(
                currentTrack = Track(
                    id = first.song.id,
                    title = first.song.title,
                    artist = first.artists.joinToString { it.name },
                    remoteArtworkUrl = first.song.thumbnailUrl
                ),
                isPlaying = true
            )
        }
    }
    
    /**
     * Play all tracks in a list (for playlist Play All button)
     * Starts playing the first track immediately, then loads more in background
     */
    fun playAllTracks(tracks: List<Track>, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val tracksToPlay = if (shuffle) tracks.shuffled() else tracks
            val mediaItems = tracksToPlay.map { it.toMediaMetadata() }.toMutableList()

            // 1. Resolve the FIRST track immediately (Sync)
            // This ensures playback starts successfully (Fixes Error 3000)
            val firstTrack = tracksToPlay.first()
            try {
                val searchQuery = "${firstTrack.title} ${firstTrack.artist}"
                val searchResult = com.zionhuang.innertube.YouTube.search(searchQuery, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                val songs = searchResult?.items?.filterIsInstance<com.zionhuang.innertube.models.SongItem>() ?: emptyList()
                val firstSong = findBestMatchingSong(songs, firstTrack.title, firstTrack.artist) ?: songs.firstOrNull()

                if (firstSong != null) {
                    mediaItems[0] = firstSong.toMediaMetadata()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Load the ENTIRE queue (mix of 1 resolved + N unresolved)
            // This ensures Queue Size = 34 (Fixes 15-song Autoplay bug)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                playerConnection?.playQueue(
                    queue = ListQueue(
                        title = "Playlist",
                        items = mediaItems,
                        startIndex = 0,
                        startShuffled = shuffle
                    )
                )
                if (shuffle) {
                    playerConnection?.player?.shuffleModeEnabled = true
                    _uiState.update { it.copy(shuffleEnabled = true) }
                }
            }

            // 3. Resolve the REST in background and update Player
            // This fixes Error 3000 for specific tracks as we reach them
            val remainingTracks = tracksToPlay.drop(1)
            remainingTracks.forEachIndexed { index, track ->
                try {
                    // Optimization: Delay slightly to let UI settle, but process fast enough for skipping
                    if (index == 0) kotlinx.coroutines.delay(100) 
                    
                    val query = "${track.title} ${track.artist}"
                    val result = com.zionhuang.innertube.YouTube.search(query, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    val trackSongs = result?.items?.filterIsInstance<com.zionhuang.innertube.models.SongItem>() ?: emptyList()
                    val song = findBestMatchingSong(trackSongs, track.title, track.artist) ?: trackSongs.firstOrNull()

                    if (song != null) {
                        val resolvedMetadata = song.toMediaMetadata()
                        val playerIndex = index + 1
                        
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            // Update the item in the live player!
                            // This seamlessly "activates" the track before the user reaches it
                            // Use remove+add for compatibility if replaceMediaItem is missing
                            val player = playerConnection?.player
                            if (player != null && playerIndex < player.mediaItemCount) {
                                // CRITICAL FIX: Do not replace the item if it is currently playing!
                                // The UNRESOLVED item works fine via JIT (StreamResolver), but replacing it 
                                // using remove/add causes playback to stop/skip.
                                if (player.currentMediaItemIndex == playerIndex) {
                                    android.util.Log.d("PlayerViewModel", "Skipping replacement of currently playing item: $playerIndex")
                                } else {
                                    player.removeMediaItem(playerIndex)
                                    player.addMediaItem(playerIndex, resolvedMetadata.toMediaItem())
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * Download a playlist of tracks.
     * 
     * OPTIMIZED FLOW:
     * 1. First, batch-lookup cached YouTube IDs from database (FAST - no API calls)
     * 2. For tracks with cached IDs: download directly (50ms delay each)
     * 3. For tracks without cached IDs: search YouTube (1000ms delay to avoid rate limits)
     * 
     * For 100 songs where 90 are already cached:
     * - Old: 100 × 1000ms = 100 seconds
     * - New: 90 × 50ms + 10 × 1000ms = 4.5 + 10 = 14.5 seconds (7x faster!)
     */
    fun downloadPlaylist(tracks: List<Track>) {
        if (tracks.isEmpty() || _isDownloading.value) return

        _isDownloading.value = true
        _downloadProgress.value = 0f

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var successCount = 0
            var searchCount = 0
            val totalTracks = tracks.size
            
            // === PHASE 1: Batch lookup cached YouTube IDs from database ===
            // This is FAST - just database lookups, no API calls
            val cachedIdMap = mutableMapOf<String, String>() // spotifyId -> youtubeId
            
            // Build a map of spotify IDs to lookup
            val spotifyIdsToLookup = tracks.mapNotNull { track ->
                // Extract spotify ID if present (either in spotifyId field or UNRESOLVED_ prefix)
                when {
                    track.id.startsWith("UNRESOLVED_") -> track.id.removePrefix("UNRESOLVED_")
                    track.spotifyId != null -> track.spotifyId
                    else -> null
                }
            }.distinct()
            
            // Batch lookup from database
            for (spotifyId in spotifyIdsToLookup) {
                try {
                    val existingSong = database.songBySpotifyId(spotifyId).first()
                    if (existingSong != null && !existingSong.id.startsWith("UNRESOLVED_")) {
                        cachedIdMap[spotifyId] = existingSong.id
                    }
                } catch (e: Exception) {
                    // Ignore lookup errors
                }
            }
            
            Log.d("PlayerViewModel", "Download: Found ${cachedIdMap.size}/${spotifyIdsToLookup.size} cached YouTube IDs")

            // === PHASE 2: Process each track ===
            for ((index, track) in tracks.withIndex()) {
                if (!_isDownloading.value) break // Allow cancellation

                try {
                    var metadata: com.vikify.app.models.MediaMetadata? = null
                    var usedCachedId = false
                    
                    // Priority 1: Check cached database lookup
                    val spotifyId = when {
                        track.id.startsWith("UNRESOLVED_") -> track.id.removePrefix("UNRESOLVED_")
                        track.spotifyId != null -> track.spotifyId
                        else -> null
                    }
                    
                    val cachedYoutubeId = spotifyId?.let { cachedIdMap[it] }
                    if (cachedYoutubeId != null) {
                        metadata = com.vikify.app.models.MediaMetadata(
                            id = cachedYoutubeId,
                            title = track.spotifyTitle ?: track.title,
                            artists = listOf(com.vikify.app.models.MediaMetadata.Artist(id = null, name = track.spotifyArtist ?: track.artist)),
                            duration = (track.duration / 1000).toInt(),
                            thumbnailUrl = track.remoteArtworkUrl,
                            genre = null
                        )
                        usedCachedId = true
                    }
                    
                    // Priority 2: Use existing YouTube ID from track
                    if (metadata == null && track.youtubeId != null) {
                         metadata = com.vikify.app.models.MediaMetadata(
                            id = track.youtubeId,
                            title = track.title,
                            artists = listOf(com.vikify.app.models.MediaMetadata.Artist(id = null, name = track.artist)),
                            duration = (track.duration / 1000).toInt(),
                            thumbnailUrl = track.remoteArtworkUrl,
                            genre = null
                        )
                        usedCachedId = true
                    } 
                    
                    // Priority 3: Use Original Backend Ref if it's a valid Innertube SongItem
                    if (metadata == null && track.originalBackendRef is com.zionhuang.innertube.models.SongItem) {
                        try {
                            metadata = (track.originalBackendRef as com.zionhuang.innertube.models.SongItem).toMediaMetadata()
                            usedCachedId = true
                        } catch (e: Exception) {
                            // Fallback if conversion fails
                        }
                    }
                    
                    // Priority 4: Check if track ID is already a valid YouTube ID (11 chars)
                    if (metadata == null && track.id.length == 11 && track.id.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                         metadata = com.vikify.app.models.MediaMetadata(
                            id = track.id,
                            title = track.title,
                            artists = listOf(com.vikify.app.models.MediaMetadata.Artist(id = null, name = track.artist)),
                            duration = (track.duration / 1000).toInt(),
                            thumbnailUrl = track.remoteArtworkUrl,
                            genre = null
                        )
                        usedCachedId = true
                    }

                    // Priority 5 (FALLBACK): Search YouTube - ONLY if nothing else worked
                    if (metadata == null) {
                        searchCount++
                        Log.d("PlayerViewModel", "[$searchCount] Searching for: ${track.title} - ${track.artist}")
                        val searchQuery = "${track.spotifyTitle ?: track.title} ${track.spotifyArtist ?: track.artist}"
                        val result = com.zionhuang.innertube.YouTube.search(searchQuery, com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        val songs = result?.items?.filterIsInstance<com.zionhuang.innertube.models.SongItem>() ?: emptyList()
                        val matchedSong = findBestMatchingSong(songs, track.spotifyTitle ?: track.title, track.spotifyArtist ?: track.artist) ?: songs.firstOrNull()

                        if (matchedSong != null) {
                            metadata = matchedSong.toMediaMetadata()
                            
                            // Cache the resolved ID for future use
                            if (spotifyId != null) {
                                try {
                                    database.updateYouTubeIdBySpotifyId(spotifyId, matchedSong.id)
                                    Log.d("PlayerViewModel", "Cached mapping: $spotifyId -> ${matchedSong.id}")
                                } catch (e: Exception) {
                                    // Ignore cache errors
                                }
                            }
                        }
                    }

                    if (metadata != null) {
                        // IMPORTANT: Ensure song exists in database BEFORE downloading
                        // Otherwise updateDownloadStatus won't find it
                        database.insert(metadata)
                        
                        // Actually download using DownloadUtil
                        downloadUtil.download(metadata)

                        // Track this as downloaded by ORIGINAL track ID (for UI indicator)
                        _downloadedTrackIds.update { it + track.id }
                        // Also track by YouTube ID for cross-reference
                        _downloadedTrackIds.update { it + metadata.id }
                        successCount++
                    } else {
                        Log.e("PlayerViewModel", "Download failed: Could not find track ${track.title} - ${track.artist}")
                    }

                    _downloadProgress.value = (index + 1).toFloat() / totalTracks

                    // Adaptive delay: very fast for cached IDs, slower for searches
                    if (usedCachedId) {
                        kotlinx.coroutines.delay(30) // Super fast for cached!
                    } else {
                        kotlinx.coroutines.delay(800) // Slower for searches to avoid rate limits
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _isDownloading.value = false
            _downloadProgress.value = 0f
            
            val cacheHitRate = if (totalTracks > 0) ((totalTracks - searchCount) * 100 / totalTracks) else 0
            Log.d("PlayerViewModel", "Download complete: $successCount/$totalTracks, Cache hit rate: $cacheHitRate%")
            
            if (successCount > 0) {
                _toastMessage.emit("Downloading $successCount songs (${cacheHitRate}% cached)")
            } else {
                _toastMessage.emit("Failed to find songs on YouTube")
            }
        }
    }

    
    // --- SLEEP TIMER ---
    
    /**
     * Start a sleep timer with the specified duration
     * Uses Kotlin Coroutines for lifecycle-aware countdown
     */
    fun startSleepTimer(duration: com.vikify.app.vikifyui.components.SleepTimerDuration) {
        // Cancel any existing timer
        sleepTimerJob?.cancel()
        
        if (duration == com.vikify.app.vikifyui.components.SleepTimerDuration.OFF) {
            cancelSleepTimer()
            return
        }
        
        val durationMs = duration.minutes * 60 * 1000L
        val endTime = System.currentTimeMillis() + durationMs
        
        _sleepTimerState.value = com.vikify.app.vikifyui.components.SleepTimerState(
            isActive = true,
            remainingMs = durationMs,
            duration = duration,
            endTime = endTime
        )
        
        viewModelScope.launch {
            _toastMessage.emit("Sleep timer set for ${duration.label}")
        }
        
        sleepTimerJob = viewModelScope.launch {
            try {
                when (duration) {
                    com.vikify.app.vikifyui.components.SleepTimerDuration.END_OF_TRACK -> {
                        // Wait for current track to end
                        // Monitor playback and pause when track changes
                        var initialMediaId = playerConnection?.player?.currentMediaItem?.mediaId
                        while (coroutineContext.isActive) {
            kotlinx.coroutines.delay(1000)
                            val currentId = playerConnection?.player?.currentMediaItem?.mediaId
                            if (currentId != null && currentId != initialMediaId) {
                                // Track changed, pause playback
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    playerConnection?.player?.pause()
                                }
                                break
                            }
                        }
                    }
                    else -> {
                        // Fixed duration countdown
                        var remaining = durationMs
                        while (coroutineContext.isActive && remaining > 0) {
                            kotlinx.coroutines.delay(1000)
                            remaining -= 1000
                            _sleepTimerState.update { it.copy(remainingMs = remaining) }
                        }
                        
                        // Timer finished - pause playback
                        if (coroutineContext.isActive && remaining <= 0) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                playerConnection?.player?.pause()
                                _toastMessage.emit("Goodnight! Sleep timer finished.")
                            }
                        }
                    }
                }
            } finally {
                // Reset state when timer completes or is cancelled
                _sleepTimerState.value = com.vikify.app.vikifyui.components.SleepTimerState()
            }
        }
    }
    
    /**
     * Cancel the active sleep timer
     */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerState.value = com.vikify.app.vikifyui.components.SleepTimerState()
        viewModelScope.launch {
            _toastMessage.emit("Sleep timer cancelled")
        }
    }
    // === Spotify Helper ===
    
    /**
     * Load a Spotify playlist for playback
     */
    suspend fun loadSpotifyPlaylist(playlistId: String, spotifyRepository: com.vikify.app.spotify.SpotifyRepository): List<com.vikify.app.spotify.SpotifyTrack> {
        return spotifyRepository.loadPlaylistAndSync(playlistId, database)
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // JAM MODE METHODS - Collaborative Listening
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Set the current user info for Jam Mode display
     */
    fun setCurrentJamUser(userId: String, displayName: String, avatarUrl: String?) {
        _currentJamUser.value = com.vikify.app.vikifyui.components.JamUser(
            id = userId,
            displayName = displayName,
            avatarUrl = avatarUrl
        )
    }
    
    /**
     * Start a new Jam session as host
     * Uses Firebase Realtime Database for real-time sync
     */
    fun startJamSession() {
        val currentUser = _currentJamUser.value ?: run {
            // Auto-create user if not set (for anonymous users)
            _currentJamUser.value = com.vikify.app.vikifyui.components.JamUser(
                id = jamSessionManager.getCurrentUserId() ?: "anonymous",
                displayName = "You",
                avatarUrl = null
            )
            _currentJamUser.value!!
        }
        
        _isJamLoading.value = true
        _jamError.value = null
        
        viewModelScope.launch {
            try {
                val result = jamSessionManager.createSession(
                    hostName = currentUser.displayName,
                    hostAvatar = currentUser.avatarUrl
                )
                
                result.fold(
                    onSuccess = { session ->
                        _jamSessionState.value = JamSessionState.WaitingForGuest(session.sessionCode, session)
                        _isJamLoading.value = false
                        
                        // Reset last synced track for new session
                        lastSyncedTrackId = null
                        processedJamQueueIds.clear()
                        
                        // Start observing the session for guest joins
                        startSessionObserver(session.sessionId)
                        
                        // Start observing chat messages
                        startChatObserver(session.sessionId)
                        
                        // Start heartbeat to keep online status updated
                        jamSessionManager.startHeartbeat(session.sessionId)
                        
                        // Cleanup expired sessions (opportunistic)
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            jamSessionManager.cleanupExpiredSessions()
                        }
                        
                        Log.d("PlayerViewModel", "Created Jam session: ${session.sessionCode}")
                    },
                    onFailure = { error ->
                        _jamError.value = "Failed to create session: ${error.message}"
                        _jamSessionState.value = JamSessionState.Idle
                        _isJamLoading.value = false
                    }
                )
                
            } catch (e: Exception) {
                _jamError.value = "Failed to create session: ${e.message}"
                _jamSessionState.value = JamSessionState.Idle
                _isJamLoading.value = false
            }
        }
    }
    
    // Track the last synced track ID to avoid re-playing the same track
    private var lastSyncedTrackId: String? = null
    
    // Track processed queue items to avoid duplicates
    private val processedJamQueueIds = mutableSetOf<String>()
    
    /**
     * Start observing a session for real-time updates
     */
    private fun startSessionObserver(sessionId: String) {
        jamObserverJob?.cancel()
        jamObserverJob = viewModelScope.launch {
            jamSessionManager.observeSession(sessionId).collect { updatedSession ->
                val currentState = _jamSessionState.value
                val isHost = when (currentState) {
                    is JamSessionState.WaitingForGuest -> true
                    is JamSessionState.Active -> currentState.isHost
                    else -> true
                }
                
                // Check if a guest has joined
                if (updatedSession.guestId != null) {
                    _jamSessionState.value = JamSessionState.Active(updatedSession, isHost)
                    Log.d("PlayerViewModel", "Guest joined: ${updatedSession.guestName}")
                }
                
                // If we're the guest, sync playback state from host
                if (!isHost) {
                    // Update playback to match host
                    if (updatedSession.isPlaying != _uiState.value.isPlaying) {
                        if (updatedSession.isPlaying) {
                            playerConnection?.player?.play()
                        } else {
                            playerConnection?.player?.pause()
                        }
                    }
                    
                    // ═══════════════════════════════════════════════════════════════════════
                    // POSITION DRIFT CORRECTION - Keep guest synced within 2 seconds of host
                    // ═══════════════════════════════════════════════════════════════════════
                    if (updatedSession.isPlaying && updatedSession.currentTrackId == lastSyncedTrackId) {
                        val hostPosition = updatedSession.currentPosition
                        val timeSinceUpdate = System.currentTimeMillis() - updatedSession.lastUpdated
                        val correctedPosition = hostPosition + timeSinceUpdate  // Compensate for network latency
                        
                        val localPosition = playerConnection?.player?.currentPosition ?: 0L
                        val drift = kotlin.math.abs(correctedPosition - localPosition)
                        
                        // Only seek if drift > 2 seconds to avoid constant seeking jitter
                        if (drift > 2000) {
                            Log.d("JamSync", "Correcting position drift: ${drift}ms (host: $correctedPosition, local: $localPosition)")
                            playerConnection?.player?.seekTo(correctedPosition)
                        }
                    }
                    
                    // Check if host changed the track
                    val hostTrackId = updatedSession.currentTrackId
                    if (hostTrackId != null && hostTrackId != lastSyncedTrackId) {
                        lastSyncedTrackId = hostTrackId
                        Log.d("PlayerViewModel", "Host changed track: ${updatedSession.currentTrackTitle}")
                        
                        // Play the host's track (ROBUST SYNC FIX)
                        val isLikelyYouTubeId = hostTrackId.length == 11 && hostTrackId.all { it.isLetterOrDigit() || it == '-' || it == '_' }
                        
                        val hostTrack = Track(
                            id = hostTrackId,
                            title = updatedSession.currentTrackTitle ?: "Unknown",
                            artist = updatedSession.currentTrackArtist ?: "Unknown",
                            remoteArtworkUrl = updatedSession.currentTrackArtwork,
                            // Fix: Only use ID as YouTube ID if it looks like one. Otherwise let getYouTubeMediaItem resolve it.
                            youtubeId = if (isLikelyYouTubeId) hostTrackId else null 
                        )
                        playTrack(hostTrack)
                    }
                }
                
                // If we're the host, sync queue from Firebase to Local Player
                if (isHost) {
                    syncJamQueueToPlayer(updatedSession)
                }
            }
        }
    }
    
    /**
     * Join an existing Jam session with code
     * Uses Firebase Realtime Database for real-time sync
     */
    fun joinJamSession(code: String) {
        val currentUser = _currentJamUser.value ?: run {
            // Auto-create user if not set
            _currentJamUser.value = com.vikify.app.vikifyui.components.JamUser(
                id = jamSessionManager.getCurrentUserId() ?: "anonymous_guest",
                displayName = "Guest",
                avatarUrl = null
            )
            _currentJamUser.value!!
        }
        
        if (code.length != 6 || !code.all { it.isDigit() }) {
            _jamError.value = "Invalid session code"
            return
        }
        
        _isJamLoading.value = true
        _jamError.value = null
        _jamSessionState.value = JamSessionState.Joining(code)
        
        viewModelScope.launch {
            try {
                val result = jamSessionManager.joinSession(
                    code = code,
                    guestName = currentUser.displayName,
                    guestAvatar = currentUser.avatarUrl
                )
                
                result.fold(
                    onSuccess = { session ->
                        _jamSessionState.value = JamSessionState.Active(session, isHost = false)
                        _isJamLoading.value = false
                        
                        // Reset last synced track so we sync to host's current track
                        lastSyncedTrackId = null
                        processedJamQueueIds.clear()
                        
                        // Start observing for host's playback updates
                        startSessionObserver(session.sessionId)
                        
                        // Start observing chat messages
                        startChatObserver(session.sessionId)
                        
                        // Immediately sync to host's current track if available
                        if (session.currentTrackId != null) {
                            Log.d("PlayerViewModel", "Initial sync to host track: ${session.currentTrackTitle}")
                            lastSyncedTrackId = session.currentTrackId
                            val hostTrack = Track(
                                id = session.currentTrackId!!,
                                title = session.currentTrackTitle ?: "Unknown",
                                artist = session.currentTrackArtist ?: "Unknown",
                                remoteArtworkUrl = session.currentTrackArtwork,
                                youtubeId = if (session.currentTrackId?.length == 11) session.currentTrackId else null
                            )
                            playTrack(hostTrack)
                        }
                        
                        // Start heartbeat to keep online status updated
                        jamSessionManager.startHeartbeat(session.sessionId)
                        
                        Log.d("PlayerViewModel", "Joined Jam session: $code")
                    },
                    onFailure = { error ->
                        _jamError.value = error.message ?: "Failed to join session"
                        _jamSessionState.value = JamSessionState.Idle
                        _isJamLoading.value = false
                    }
                )
                
            } catch (e: Exception) {
                _jamError.value = "Failed to join session: ${e.message}"
                _jamSessionState.value = JamSessionState.Idle
                _isJamLoading.value = false
            }
        }
    }
    
    /**
     * Leave the current Jam session
     */
    fun leaveJamSession() {
        jamObserverJob?.cancel()
        jamObserverJob = null
        lastSyncedTrackId = null  // Reset for next session
        
        // Stop chat observer and clear messages
        stopChatObserver()
        _isChatOpen.value = false
        
        val currentState = _jamSessionState.value
        
        viewModelScope.launch {
            // Stop heartbeat and mark offline
            jamSessionManager.stopHeartbeat()
            
            when (currentState) {
                is JamSessionState.Active -> {
                    jamSessionManager.leaveSession(currentState.session.sessionId)
                    jamSessionManager.clearSessionChat(currentState.session.sessionId)
                    Log.d("PlayerViewModel", "Left Jam session: ${currentState.session.sessionId}")
                }
                is JamSessionState.WaitingForGuest -> {
                    jamSessionManager.leaveSession(currentState.session.sessionId)
                    jamSessionManager.clearSessionChat(currentState.session.sessionId)
                    Log.d("PlayerViewModel", "Cancelled Jam session: ${currentState.sessionCode}")
                }
                else -> {}
            }
            
            _jamSessionState.value = JamSessionState.Idle
            _jamError.value = null
        }
    }
    
    /**
     * Clear Jam error message
     */
    fun clearJamError() {
        _jamError.value = null
    }
    
    /**
     * Sync playback state to Firebase (host only)
     */
    private fun syncJamPlaybackState() {
        val currentState = _jamSessionState.value
        if (currentState is JamSessionState.Active && currentState.isHost) {
            viewModelScope.launch {
                val position = (playerConnection?.player?.currentPosition ?: 0L)
                jamSessionManager.updatePlaybackState(
                    sessionId = currentState.session.sessionId,
                    isPlaying = _uiState.value.isPlaying,
                    position = position
                )
            }
        }
    }
    
    /**
     * Sync track change to Firebase (host only)
     * This notifies the guest when the host changes songs
     */
    private fun syncJamTrackChange(track: Track) {
        val currentState = _jamSessionState.value
        if (currentState is JamSessionState.Active && currentState.isHost) {
            viewModelScope.launch {
                jamSessionManager.changeTrack(
                    sessionId = currentState.session.sessionId,
                    track = track
                )
                Log.d("PlayerViewModel", "Synced track change to Jam: ${track.title}")
            }
        }
    }
    
    /**
     * Sync Jam Queue to Player (Host only)
     * Automatically adds songs from guest queue to the real player queue
     */
    private fun syncJamQueueToPlayer(session: JamSession) {
        session.queue.forEach { queueItem ->
            if (queueItem.id !in processedJamQueueIds) {
                processedJamQueueIds.add(queueItem.id)
                Log.d("PlayerViewModel", "Auto-adding Jam item to player: ${queueItem.title}")
                
                viewModelScope.launch {
                     // Add to queue (this handles resolution automatically)
                     // converting JamQueueItem to Track
                     val track = Track(
                         id = queueItem.trackId,
                         title = queueItem.title,
                         artist = queueItem.artist,
                         remoteArtworkUrl = queueItem.artwork,
                         duration = queueItem.duration
                     )
                     
                     // Use addToQueue logic but suppress duplicate toasts if possible
                     addToQueue(track)
                     
                     // Show social context
                     if ((queueItem.addedByName ?: "User") != "You") { // Simple check, ideally use ID
                         _toastMessage.emit("${queueItem.addedByName} added to queue")
                     }
                }
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // JAM QUEUE & REACTIONS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    fun addToJamQueue(track: Track) {
        val currentState = _jamSessionState.value
        val sessionId = when (currentState) {
            is JamSessionState.Active -> currentState.session.sessionId
            is JamSessionState.WaitingForGuest -> currentState.session.sessionId
            else -> return
        }
        
        val currentUser = _currentJamUser.value
        val addedByName = currentUser?.displayName ?: "User"

        viewModelScope.launch {
            jamSessionManager.addToQueue(
                sessionId = sessionId,
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                artwork = track.remoteArtworkUrl,
                duration = track.duration,
                addedByName = addedByName
            ).onFailure { e ->
                _toastMessage.emit("Failed to add to queue: ${e.message}")
            }
        }
    }
    
    fun removeFromJamQueue(itemId: String) {
        val currentState = _jamSessionState.value
        val sessionId = when (currentState) {
            is JamSessionState.Active -> currentState.session.sessionId
            is JamSessionState.WaitingForGuest -> currentState.session.sessionId
            else -> return
        }
        
        viewModelScope.launch {
            jamSessionManager.removeFromQueue(sessionId, itemId)
        }
    }
    
    fun sendJamReaction(emoji: String) {
        val currentState = _jamSessionState.value
        val sessionId = when (currentState) {
            is JamSessionState.Active -> currentState.session.sessionId
            is JamSessionState.WaitingForGuest -> currentState.session.sessionId
            else -> return
        }
        
        viewModelScope.launch {
            jamSessionManager.sendReaction(sessionId, emoji)
        }
    }
    
    /**
     * Play the next track from the Jam session queue (host only)
     * Links the collaborative queue to actual playback
     */
    fun playNextFromJamQueue() {
        val currentState = _jamSessionState.value
        if (currentState !is JamSessionState.Active) return
        if (!currentState.isHost) {
            Log.w("PlayerViewModel", "Only host can control queue playback")
            return
        }
        
        val queue = currentState.session.queue
        if (queue.isEmpty()) {
            Log.d("PlayerViewModel", "Jam queue is empty")
            return
        }
        
        val nextItem = queue.first()
        val track = Track(
            id = nextItem.trackId,
            title = nextItem.title,
            artist = nextItem.artist,
            remoteArtworkUrl = nextItem.artwork,
            youtubeId = nextItem.trackId,
            duration = nextItem.duration
        )
        
        Log.d("PlayerViewModel", "Playing next from Jam queue: ${track.title} (added by ${nextItem.addedByName})")
        
        // Play the track and remove from queue
        playTrack(track)
        viewModelScope.launch {
            jamSessionManager.removeFromQueue(currentState.session.sessionId, nextItem.id)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // JAM CHAT - Real-time messaging
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Start observing chat messages for the current session
     */
    private fun startChatObserver(sessionId: String) {
        jamChatObserverJob?.cancel()
        _jamChatMessages.value = emptyList()
        _unreadChatCount.value = 0
        
        jamChatObserverJob = viewModelScope.launch {
            jamSessionManager.observeChat(sessionId).collect { messages ->
                val previousCount = _jamChatMessages.value.size
                _jamChatMessages.value = messages
                
                // Update unread count if chat is closed and new messages arrived
                if (!_isChatOpen.value && messages.size > previousCount) {
                    _unreadChatCount.value += (messages.size - previousCount)
                }
            }
        }
    }
    
    /**
     * Stop observing chat messages
     */
    private fun stopChatObserver() {
        jamChatObserverJob?.cancel()
        jamChatObserverJob = null
        _jamChatMessages.value = emptyList()
        _unreadChatCount.value = 0
    }
    
    /**
     * Send a chat message in the current Jam session
     */
    fun sendJamChatMessage(message: String) {
        val trimmedMessage = message.trim()
        if (trimmedMessage.isEmpty()) return
        
        val currentState = _jamSessionState.value
        val sessionId = when (currentState) {
            is JamSessionState.Active -> currentState.session.sessionId
            is JamSessionState.WaitingForGuest -> currentState.session.sessionId
            else -> return
        }
        
        val currentUser = _currentJamUser.value ?: return
        
        viewModelScope.launch {
            val result = jamSessionManager.sendChatMessage(
                sessionId = sessionId,
                senderName = currentUser.displayName,
                senderAvatar = currentUser.avatarUrl,
                message = trimmedMessage
            )
            
            result.fold(
                onSuccess = {
                    Log.d("PlayerViewModel", "Sent chat message: ${trimmedMessage.take(20)}...")
                },
                onFailure = { error ->
                    Log.e("PlayerViewModel", "Failed to send chat: ${error.message}")
                    viewModelScope.launch {
                        _toastMessage.emit("Failed to send message")
                    }
                }
            )
        }
    }
    
    /**
     * Open the chat panel and clear unread count
     */
    fun openJamChat() {
        _isChatOpen.value = true
        _unreadChatCount.value = 0
    }
    
    /**
     * Close the chat panel
     */
    fun closeJamChat() {
        _isChatOpen.value = false
    }
    
    /**
     * Toggle chat panel visibility
     */
    fun toggleJamChat() {
        if (_isChatOpen.value) {
            closeJamChat()
        } else {
            openJamChat()
        }
    }
}