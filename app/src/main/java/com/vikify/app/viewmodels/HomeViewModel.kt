package com.vikify.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vikify.app.auth.AuthManager
import com.vikify.app.constants.PlaylistFilter
import com.vikify.app.constants.PlaylistSortType
import com.vikify.app.db.MusicDatabase
import com.vikify.app.db.entities.Album
import com.vikify.app.db.entities.Artist
import com.vikify.app.db.entities.LocalItem
import com.vikify.app.db.entities.Song
import com.vikify.app.models.SimilarRecommendation
import com.vikify.app.models.RailItemType
import com.vikify.app.models.toMediaMetadata
import com.vikify.app.playback.generateSongDNA
import com.vikify.app.playback.getTimeBasedEnergyRange
import com.vikify.app.utils.SyncUtils
import com.vikify.app.utils.reportException
import com.vikify.app.utils.syncCoroutine
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.WatchEndpoint
import com.zionhuang.innertube.models.YTItem
import com.zionhuang.innertube.pages.ExplorePage
import com.zionhuang.innertube.pages.HomePage
import com.zionhuang.innertube.utils.completed
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.AlbumItem
import com.vikify.app.models.FeedSection
import com.vikify.app.models.RailItem
import com.vikify.app.models.QuickResumeItem
import com.vikify.app.models.QuickResumeType

// ═══════════════════════════════════════════════════════════════════════════════
// HOME VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════════

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    val authManager: AuthManager
) : ViewModel() {
    // Auth State (Exposed for UI)
    val currentUser = authManager.currentUser

    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    
    // === SPOTIFY SYNC STATUS ===
    val syncProgress = androidx.work.WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow("spotify_sync")
        .map { workInfoList ->
            val workInfo = workInfoList.firstOrNull()
            if (workInfo != null && workInfo.state == androidx.work.WorkInfo.State.RUNNING) {
                val progress = workInfo.progress
                val resolved = progress.getInt("resolved", 0)
                val total = progress.getInt("total", 0)
                if (total > 0) "Syncing Library: $resolved/$total" else "Syncing Library..."
            } else {
                null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    // Pre-formatted date string - computed once to avoid recomposition overhead
    val currentDateText: String = java.time.LocalDate.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d"))

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val playlists = database.playlists(PlaylistFilter.LIBRARY, PlaylistSortType.NAME, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val recentActivity = database.recentActivity()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())
    
    // ═══════════════════════════════════════════════════════════════
    // SMART QUEUE - Context-Aware Home Sections
    // ═══════════════════════════════════════════════════════════════
    
    // "Jump Back In" - Top 6 most played in last 7 days
    val jumpBackIn = MutableStateFlow<List<Song>?>(null)
    
    // "Daily Mix" - Energy-filtered based on time of day
    val dailyMix = MutableStateFlow<List<Song>?>(null)
    
    // ═══════════════════════════════════════════════════════════════
    // INFINITE DISCOVERY FEED - Unified sections for layered content
    // ═══════════════════════════════════════════════════════════════
    
    /** Unified feed sections (all layers combined) */
    val homeSections = MutableStateFlow<List<FeedSection>>(emptyList())
    
    /** Loading state for infinite scroll pagination */
    val isLoadingMore = MutableStateFlow(false)
    
    /** Random moods for discovery layer */
    val randomMoods = MutableStateFlow<List<com.zionhuang.innertube.pages.MoodAndGenres.Item>?>(null)
    
    // Time-based greeting (updates on load)
    val timeBasedGreeting = MutableStateFlow(getGreeting())

    
    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good Morning"
            in 12..17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    private suspend fun load() {
        isLoading.value = true

        quickPicks.value = database.quickPicks()
            .first().shuffled().take(20)

        forgottenFavorites.value = database.forgottenFavorites()
            .first().shuffled().take(20)
            
        // ═══════════════════════════════════════════════════════════════
        // SMART QUEUE - Load context-aware sections
        // ═══════════════════════════════════════════════════════════════
        
        // Update greeting
        timeBasedGreeting.value = getGreeting()
        
        // "Jump Back In" - Top 6 by play count (last 7 days)
        val sevenDaysAgo = System.currentTimeMillis() - 86400000 * 7
        jumpBackIn.value = database.mostPlayedSongs(sevenDaysAgo, limit = 6)
            .first()
        
        // "Daily Mix" - Energy-filtered based on time of day
        val (minEnergy, maxEnergy) = getTimeBasedEnergyRange()
        val allSongs = database.quickPicks().first() + database.forgottenFavorites().first()
        dailyMix.value = allSongs
            .distinctBy { it.song.id }
            .filter { song ->
                val dna = generateSongDNA(song.toMediaMetadata())
                dna.energyLevel in minEnergy..maxEnergy
            }
            .shuffled()
            .take(10)

        val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2
        val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
            .first().shuffled().take(10)
        val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2)
            .first().filter { it.album.thumbnailUrl != null }.shuffled().take(5)
        val keepListeningArtists = database.mostPlayedArtists(0, 1)
            .first().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
        keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()

        allLocalItems.value =
            (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
                .filter { it is Song || it is Album }

        if (YouTube.cookie != null) { // if logged in
            YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
                accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
            }.onFailure {
                reportException(it)
            }
        }

        // Similar to artists
        val artistRecommendations =
            database.mostPlayedArtists(0, 1, limit = 10).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(3)
                .mapNotNull {
                    val items = mutableListOf<YTItem>()
                    YouTube.artist(it.id).onSuccess { page ->
                        items += page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty()
                        items += page.sections.lastOrNull()?.items.orEmpty()
                    }
                    SimilarRecommendation(
                        title = it,
                        items = items
                            .shuffled()
                            .ifEmpty { return@mapNotNull null }
                    )
                }
        // Similar to songs
        val songRecommendations =
            database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
                .filter { it.album != null }
                .shuffled().take(2)
                .mapNotNull { song ->
                    val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                        ?: return@mapNotNull null
                    val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                    SimilarRecommendation(
                        title = song,
                        items = (page.songs.shuffled().take(8) +
                                page.albums.shuffled().take(4) +
                                page.artists.shuffled().take(4) +
                                page.playlists.shuffled().take(4))
                            .shuffled()
                            .ifEmpty { return@mapNotNull null }
                    )
                }
        similarRecommendations.value = (artistRecommendations + songRecommendations).shuffled()

        YouTube.home().onSuccess { page ->
            homePage.value = page
        }.onFailure {
            reportException(it)
        }

        YouTube.explore().onSuccess { page ->
            explorePage.value = page
        }.onFailure {
            reportException(it)
        }

        syncUtils.syncRecentActivity()

        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty()

        // ═══════════════════════════════════════════════════════════════
        // BUILD UNIFIED SECTIONS - LOCAL/PERSONAL CONTENT FIRST
        // ═══════════════════════════════════════════════════════════════
        val sections = mutableListOf<FeedSection>()
        
        // Get local playlists from database
        val localPlaylists = playlists.value
        
        // ─────────────────────────────────────────────────────────────────
        // SECTION 1: Quick Resume Grid (Liked Songs, Downloads, Recent)
        // ─────────────────────────────────────────────────────────────────
        val resumeItems = mutableListOf<QuickResumeItem>()
        resumeItems.add(QuickResumeItem("liked_songs", "Liked Songs", null, QuickResumeType.LIKED_SONGS))
        resumeItems.add(QuickResumeItem("downloaded", "Downloaded", null, QuickResumeType.DOWNLOADED))
        // Add top playlists to quick resume
        localPlaylists?.take(2)?.forEach { playlist ->
            resumeItems.add(QuickResumeItem(
                id = playlist.playlist.id,
                title = playlist.playlist.name,
                imageUrl = playlist.thumbnails.firstOrNull(),
                type = QuickResumeType.PLAYLIST
            ))
        }
        // Add recent tracks
        quickPicks.value?.take(2)?.forEach { song ->
            resumeItems.add(QuickResumeItem(song.song.id, song.song.title, song.song.thumbnailUrl, QuickResumeType.RECENT_SONG))
        }
        if (resumeItems.isNotEmpty()) {
            sections.add(FeedSection.QuickResumeGrid(items = resumeItems.take(6)))
        }

        // ─────────────────────────────────────────────────────────────────
        // SECTION 2: Jump Back In (Your most played recently)
        // ─────────────────────────────────────────────────────────────────
        if (jumpBackIn.value?.isNotEmpty() == true) {
            sections.add(FeedSection.HorizontalRail(
                id = "jump_back_in",
                title = "Jump Back In",
                subtitle = "Your recent favorites",
                items = jumpBackIn.value!!.map { it.toRailItem() }
            ))
        }

        // ─────────────────────────────────────────────────────────────────
        // SECTION 3: Daily Mix (Energy-matched for time of day)
        // ─────────────────────────────────────────────────────────────────
        if (dailyMix.value?.isNotEmpty() == true) {
            sections.add(FeedSection.HorizontalRail(
                id = "daily_mix",
                title = "${timeBasedGreeting.value} Mix",
                subtitle = "Energy-matched for your vibe",
                items = dailyMix.value!!.map { it.toRailItem() }
            ))
        }

        // ─────────────────────────────────────────────────────────────────
        // SECTION 4: Your Playlists (Local playlists)
        // ─────────────────────────────────────────────────────────────────
        localPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
            sections.add(FeedSection.HorizontalRail(
                id = "your_playlists",
                title = "Your Playlists",
                subtitle = "${playlists.size} playlists",
                items = playlists.take(10).map { playlist ->
                    RailItem(
                        id = playlist.playlist.id,
                        title = playlist.playlist.name,
                        subtitle = "${playlist.songCount} songs",
                        imageUrl = playlist.thumbnails.firstOrNull()
                    )
                }
            ))
        }

        // ─────────────────────────────────────────────────────────────────
        // SECTION 5: Quick Picks (Recent listening history)
        // ─────────────────────────────────────────────────────────────────
        if (quickPicks.value?.isNotEmpty() == true) {
            sections.add(FeedSection.HorizontalRail(
                id = "quick_picks",
                title = "Quick Picks",
                subtitle = "Based on your recent listening",
                items = quickPicks.value!!.take(10).map { it.toRailItem() }
            ))
        }

        // ─────────────────────────────────────────────────────────────────
        // SECTION 6: Keep Listening (Continue where you left off)
        // ─────────────────────────────────────────────────────────────────
        keepListening.value?.takeIf { it.isNotEmpty() }?.let { items ->
            val songItems = items.filterIsInstance<Song>().take(8)
            if (songItems.isNotEmpty()) {
                sections.add(FeedSection.HorizontalRail(
                    id = "keep_listening",
                    title = "Keep Listening",
                    subtitle = "Continue where you left off",
                    items = songItems.map { it.toRailItem() }
                ))
            }
        }

        // ─────────────────────────────────────────────────────────────────
        // SECTION 7: Forgotten Favorites (Rediscover old songs)
        // ─────────────────────────────────────────────────────────────────
        if (forgottenFavorites.value?.isNotEmpty() == true) {
            sections.add(FeedSection.HorizontalRail(
                id = "forgotten_favorites",
                title = "Rediscover",
                subtitle = "Songs you haven't played in a while",
                items = forgottenFavorites.value!!.take(10).map { it.toRailItem() }
            ))
        }

        // ─────────────────────────────────────────────────────────────────
        // SECTION 8: Personalized Recommendations (Similar to favorites)
        // ─────────────────────────────────────────────────────────────────
        similarRecommendations.value?.forEach { rec ->
            if (rec.items.isNotEmpty()) {
                val titleText = when (val title = rec.title) {
                    is Song -> "Because you like ${title.song.title}"
                    is com.vikify.app.db.entities.Artist -> "More from ${title.artist.name}"
                    else -> "Recommended for you"
                }
                sections.add(FeedSection.HorizontalRail(
                    id = "similar_${rec.title.hashCode()}",
                    title = titleText,
                    subtitle = "Based on your taste",
                    items = rec.items.take(10).map { it.toRailItem() }
                ))
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // YOUTUBE CONTENT (Placed at the END for discovery)
        // ═══════════════════════════════════════════════════════════════
        
        // New Releases from YouTube Explore
        explorePage.value?.let { explore ->
            if (explore.newReleaseAlbums.isNotEmpty()) {
                sections.add(FeedSection.LargeSquareRail(
                    id = "new_releases",
                    title = "New Releases",
                    subtitle = "Fresh music just dropped",
                    items = explore.newReleaseAlbums.take(12).map { album ->
                        RailItem(
                            id = album.browseId,
                            title = album.title,
                            subtitle = album.artists?.firstOrNull()?.name ?: "Album",
                            imageUrl = album.thumbnail
                        )
                    }
                ))
            }
        }
        
        // Mood discovery chips removed per user request
        
        // YouTube Home sections - Split into Song sections and Playlist/Album sections
        // This gives the feed variety between individual songs and collections
        // Filter out video/performance sections to keep feed music-focused
        val excludedSectionPatterns = listOf(
            "video",
            "live performance",
            "live session",
            "concert",
            "performance"
        )
        
        val filteredSections = homePage.value?.sections
            ?.filter { section -> 
                excludedSectionPatterns.none { pattern -> 
                    section.title.contains(pattern, ignoreCase = true) 
                }
            }
            ?.take(6) // Take more sections to extract variety
            ?: emptyList()
        
        // Extract songs from YouTube sections for a "Songs for You" rail
        val ytSongsForYou = filteredSections
            .flatMap { it.items }
            .filterIsInstance<SongItem>()
            .distinctBy { it.id }
            .take(15)
        
        if (ytSongsForYou.isNotEmpty()) {
            sections.add(FeedSection.HorizontalRail(
                id = "yt_songs_for_you",
                title = "Songs for You",
                subtitle = "Handpicked tracks",
                items = ytSongsForYou.map { it.toRailItem() }
            ))
        }
        
        // Add 2 playlist/album-focused sections (original behavior)
        filteredSections
            .filter { section -> 
                section.items.any { it is PlaylistItem || it is AlbumItem }
            }
            .take(2)
            .forEach { ytSection ->
                sections.add(FeedSection.HorizontalRail(
                    id = "yt_${ytSection.title.hashCode()}",
                    title = ytSection.title,
                    items = ytSection.items.map { it.toRailItem() }
                ))
            }
        
        // Add another song section for variety ("More Songs")
        val moreSongs = filteredSections
            .flatMap { it.items }
            .filterIsInstance<SongItem>()
            .distinctBy { it.id }
            .drop(15)  // Skip the ones already used
            .take(12)
        
        if (moreSongs.isNotEmpty()) {
            sections.add(FeedSection.HorizontalRail(
                id = "yt_more_songs",
                title = "More Songs",
                subtitle = "Keep the music flowing",
                items = moreSongs.map { it.toRailItem() }
            ))
        }
        
        homeSections.value = sections
        isLoading.value = false
    }

    private fun Song.toRailItem() = RailItem(
        id = song.id,
        title = cleanSongTitle(song.title),
        subtitle = artists.joinToString { it.name },
        imageUrl = song.thumbnailUrl
    )

    private fun YTItem.toRailItem() = RailItem(
        id = id,
        title = cleanSongTitle(title),
        subtitle = when (this) {
            is SongItem -> artists.joinToString { it.name }
            is AlbumItem -> artists?.joinToString { it.name } ?: "Album"
            is PlaylistItem -> "Playlist"
            else -> ""
        },
        imageUrl = thumbnail,
        itemType = when (this) {
            is SongItem -> RailItemType.SONG
            is AlbumItem -> RailItemType.ALBUM
            is PlaylistItem -> RailItemType.PLAYLIST
            else -> RailItemType.SONG
        }
    )

    /**
     * Cleans YouTube-style titles to show only the song name.
     * Removes common suffixes like (Official Video), [Music Video], etc.
     */
    private fun cleanSongTitle(title: String): String {
        val patterns = listOf(
            // Parentheses variations
            "\\s*\\(Official Video\\)\\s*",
            "\\s*\\(Official Music Video\\)\\s*",
            "\\s*\\(Music Video\\)\\s*",
            "\\s*\\(Official Audio\\)\\s*",
            "\\s*\\(Audio\\)\\s*",
            "\\s*\\(Lyric Video\\)\\s*",
            "\\s*\\(Lyrics\\)\\s*",
            "\\s*\\(Official Lyric Video\\)\\s*",
            "\\s*\\(Official Visualizer\\)\\s*",
            "\\s*\\(Visualizer\\)\\s*",
            "\\s*\\(Official\\)\\s*",
            "\\s*\\(HD\\)\\s*",
            "\\s*\\(HQ\\)\\s*",
            "\\s*\\(4K\\)\\s*",
            "\\s*\\(Full Video\\)\\s*",
            "\\s*\\(Video\\)\\s*",
            // Bracket variations
            "\\s*\\[Official Video\\]\\s*",
            "\\s*\\[Official Music Video\\]\\s*",
            "\\s*\\[Music Video\\]\\s*",
            "\\s*\\[Official Audio\\]\\s*",
            "\\s*\\[Audio\\]\\s*",
            "\\s*\\[Lyric Video\\]\\s*",
            "\\s*\\[Lyrics\\]\\s*",
            "\\s*\\[Official\\]\\s*",
            "\\s*\\[HD\\]\\s*",
            "\\s*\\[HQ\\]\\s*",
            "\\s*\\[4K\\]\\s*",
            "\\s*\\[Video\\]\\s*",
            // Common text suffixes
            "\\s*-\\s*Official Video\\s*$",
            "\\s*-\\s*Official Music Video\\s*$",
            "\\s*-\\s*Music Video\\s*$",
            "\\s*\\|\\s*Official Video\\s*$",
            "\\s*\\|\\s*Official Music Video\\s*$"
        )
        
        var cleaned = title
        patterns.forEach { pattern ->
            cleaned = cleaned.replace(Regex(pattern, RegexOption.IGNORE_CASE), "")
        }
        return cleaned.trim()
    }

    private val _isLoadingMore = MutableStateFlow(false)
    
    /**
     * Load more YouTube content for infinite scroll.
     * Appends new sections to both homePage and unified homeSections.
     */
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            isLoadingMore.value = true
            
            val nextSections = YouTube.home(continuation).getOrNull() ?: run {
                _isLoadingMore.value = false
                isLoadingMore.value = false
                return@launch
            }
            
            // Update homePage
            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = homePage.value?.sections.orEmpty() + nextSections.sections
            )
            
            // Append to unified homeSections for infinite scroll
            // Filter out video/performance sections
            val excludedPatterns = listOf("video", "live performance", "live session", "concert", "performance")
            val newSections = nextSections.sections
                .filter { section -> 
                    excludedPatterns.none { pattern -> 
                        section.title.contains(pattern, ignoreCase = true) 
                    }
                }
                .map { ytSection ->
                    FeedSection.HorizontalRail(
                        id = "yt_${ytSection.title.hashCode()}_${System.currentTimeMillis()}",
                        title = ytSection.title,
                        items = ytSection.items.map { it.toRailItem() }
                    )
                }
            homeSections.value = homeSections.value + newSections
            
            _isLoadingMore.value = false
            isLoadingMore.value = false
        }
    }
    
    /**
     * Convenience function to trigger infinite scroll from UI.
     * Uses the current homePage continuation token.
     */
    fun loadMore() {
        loadMoreYouTubeItems(homePage.value?.continuation)
    }


    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            // store the actual homepage for deselecting chips
            previousHomePage.value = homePage.value
        }
        viewModelScope.launch(Dispatchers.IO) {
            val nextSections = YouTube.home(params = chip?.endpoint?.params).getOrNull() ?: return@launch
            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections,
                continuation = nextSections.continuation
            )
            selectedChip.value = chip
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(syncCoroutine) {
            isRefreshing.value = true
            load()
            isRefreshing.value = false
        }
    }

    init {
        refresh()
        viewModelScope.launch(syncCoroutine) {
            syncUtils.tryAutoSync()
        }
        
        // Ensure we have at least a Guest user
        viewModelScope.launch(Dispatchers.IO) {
            authManager.ensureUser()
        }
    }
}
