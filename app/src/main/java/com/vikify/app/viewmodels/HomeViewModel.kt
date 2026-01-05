package com.vikify.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vikify.app.auth.AuthManager
import com.vikify.app.constants.PlaylistFilter
import com.vikify.app.constants.PlaylistSortType
import com.vikify.app.db.MusicDatabase
import com.vikify.app.db.entities.Album
import com.vikify.app.db.entities.LocalItem
import com.vikify.app.db.entities.Song
import com.vikify.app.models.SimilarRecommendation
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

// ═══════════════════════════════════════════════════════════════════════════════
// HOME SECTION - Unified data model for layered home feed
// ═══════════════════════════════════════════════════════════════════════════════
sealed class HomeSection {
    abstract val id: String
    
    /** Local songs row (Quick Picks, Daily Mix, Jump Back In, etc.) */
    data class LocalSongRow(
        override val id: String,
        val title: String,
        val subtitle: String? = null,
        val songs: List<Song>
    ) : HomeSection()
    
    /** YouTube items row (Albums, Artists, Playlists from YT Music) */
    data class YouTubeRow(
        override val id: String,
        val title: String,
        val subtitle: String? = null,
        val items: List<YTItem>
    ) : HomeSection()
    
    /** Mood/Genre chips for discovery */
    data class MoodChipRow(
        override val id: String,
        val title: String,
        val moods: List<com.zionhuang.innertube.pages.MoodAndGenres.Item>
    ) : HomeSection()
    
    /** Banner section for featured content */
    data class BannerSection(
        override val id: String,
        val title: String,
        val subtitle: String,
        val imageUrl: String,
        val browseId: String? = null
    ) : HomeSection()
}

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
    val homeSections = MutableStateFlow<List<HomeSection>>(emptyList())
    
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
            // InnerTune way is YouTube.likedPlaylists().onSuccess { ... }
            // OuterTune uses YouTube.library("FEmusic_liked_playlists").completedL().onSuccess { ... }
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
        // BUILD UNIFIED SECTIONS - Layered content for infinite discovery
        // ═══════════════════════════════════════════════════════════════
        val sections = mutableListOf<HomeSection>()
        
        // LAYER 1: "RIGHT NOW" - Contextual picks
        if (quickPicks.value?.isNotEmpty() == true) {
            sections.add(HomeSection.LocalSongRow(
                id = "quick_picks",
                title = "Quick Picks",
                subtitle = "Based on your recent listening",
                songs = quickPicks.value!!.take(10)
            ))
        }
        
        if (dailyMix.value?.isNotEmpty() == true) {
            sections.add(HomeSection.LocalSongRow(
                id = "daily_mix",
                title = "${timeBasedGreeting.value} Mix",
                subtitle = "Energy-matched for your vibe",
                songs = dailyMix.value!!
            ))
        }
        
        // LAYER 2: "GLOBAL PULSE" - New releases and trending
        explorePage.value?.let { explore ->
            if (explore.newReleaseAlbums.isNotEmpty()) {
                sections.add(HomeSection.YouTubeRow(
                    id = "new_releases",
                    title = "New Releases",
                    subtitle = "Fresh music just dropped",
                    items = explore.newReleaseAlbums.take(12)
                ))
            }
        }
        
        // LAYER 3: "DEEP DIVE" - Random moods for discovery
        YouTube.moodAndGenres().onSuccess { moods ->
            val flatMoods = moods.flatMap { it.items }
            if (flatMoods.isNotEmpty()) {
                val randomThree = flatMoods.shuffled().take(3)
                randomMoods.value = randomThree
                sections.add(HomeSection.MoodChipRow(
                    id = "discover_moods",
                    title = "Explore Moods",
                    moods = randomThree
                ))
            }
        }
        
        // LAYER 4: "TIME MACHINE" - Nostalgia
        if (jumpBackIn.value?.isNotEmpty() == true) {
            sections.add(HomeSection.LocalSongRow(
                id = "jump_back_in",
                title = "Jump Back In",
                subtitle = "Your recent favorites",
                songs = jumpBackIn.value!!
            ))
        }
        
        if (forgottenFavorites.value?.isNotEmpty() == true) {
            sections.add(HomeSection.LocalSongRow(
                id = "forgotten_favorites",
                title = "Rediscover",
                subtitle = "Songs you haven't played in a while",
                songs = forgottenFavorites.value!!.take(10)
            ))
        }
        
        // Similar recommendations from artists
        similarRecommendations.value?.forEach { rec ->
            if (rec.items.isNotEmpty()) {
                sections.add(HomeSection.YouTubeRow(
                    id = "similar_${rec.title.hashCode()}",
                    title = "Similar to ${(rec.title as? Song)?.song?.title ?: (rec.title as? com.vikify.app.db.entities.Artist)?.artist?.name ?: "Your Picks"}",
                    items = rec.items.take(10)
                ))
            }
        }
        
        // LAYER 5: "INFINITE SCROLL" - YouTube Home sections
        homePage.value?.sections?.forEach { ytSection ->
            sections.add(HomeSection.YouTubeRow(
                id = "yt_${ytSection.title.hashCode()}",
                title = ytSection.title,
                items = ytSection.items
            ))
        }
        
        homeSections.value = sections
        
        isLoading.value = false

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
            val newSections = nextSections.sections.map { ytSection ->
                HomeSection.YouTubeRow(
                    id = "yt_${ytSection.title.hashCode()}_${System.currentTimeMillis()}",
                    title = ytSection.title,
                    items = ytSection.items
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
