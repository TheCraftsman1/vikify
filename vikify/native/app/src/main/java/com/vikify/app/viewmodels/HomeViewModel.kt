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
import com.vikify.app.models.RailItemType
import com.vikify.app.models.QuickResumeItem
import com.vikify.app.models.QuickResumeType

// ═══════════════════════════════════════════════════════════════════════════════
// HOME VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════════

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    val authManager: AuthManager
) : ViewModel() {
    // Auth State (Exposed for UI)
    val currentUser = authManager.currentUser

    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    
    // ═══════════════════════════════════════════════════════════════
    // LANGUAGE PREFERENCES - For content personalization
    // ═══════════════════════════════════════════════════════════════
    
    /** User's selected music languages */
    val selectedLanguages = com.vikify.app.vikifyui.data.MusicPreferences.getSelectedLanguages(context)
        .stateIn(viewModelScope, SharingStarted.Eagerly, setOf(com.vikify.app.vikifyui.data.MusicLanguage.ENGLISH))
    
    /** Get the primary YouTube locale based on language preferences */
    private fun getPrimaryLocale(): String {
        return com.vikify.app.vikifyui.data.MusicPreferences.getPrimaryLocale(selectedLanguages.value)
    }
    
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
    
    /**
     * Build YouTubeLocale based on user's language preferences
     * Maps MusicLanguage to YouTube gl (geolocation) and hl (host language)
     */
    private fun buildYouTubeLocale(): com.zionhuang.innertube.models.YouTubeLocale {
        val languages = selectedLanguages.value
        val primaryLanguage = languages.firstOrNull() ?: com.vikify.app.vikifyui.data.MusicLanguage.ENGLISH
        
        return com.zionhuang.innertube.models.YouTubeLocale(
            gl = primaryLanguage.youtubeLocale,  // Geolocation (e.g., "IN", "US", "KR")
            hl = primaryLanguage.languageCode     // Host language (e.g., "hi", "en", "ko")
        )
    }
    
    /**
     * Get language-specific search queries for discovering content
     * Returns a map of language to list of search queries
     * Each language gets its own dedicated row
     */
    private fun getLanguageSearchQueriesMap(): Map<com.vikify.app.vikifyui.data.MusicLanguage, List<String>> {
        val result = mutableMapOf<com.vikify.app.vikifyui.data.MusicLanguage, List<String>>()
        val languages = selectedLanguages.value
        
        languages.forEach { lang ->
            val queries = when (lang) {
                com.vikify.app.vikifyui.data.MusicLanguage.ENGLISH -> listOf(
                    "global top 50 songs 2026",
                    "billboard hot 100",
                    "trending worldwide songs",
                    "top hits 2026"
                )
                com.vikify.app.vikifyui.data.MusicLanguage.HINDI -> listOf(
                    "bollywood hits 2026",
                    "latest hindi songs",
                    "arijit singh latest",
                    "new hindi romantic songs"
                )
                com.vikify.app.vikifyui.data.MusicLanguage.TELUGU -> listOf(
                    "telugu hit songs 2026",
                    "latest telugu songs",
                    "tollywood new songs",
                    "telugu melody songs"
                )
                com.vikify.app.vikifyui.data.MusicLanguage.TAMIL -> listOf(
                    "tamil hits 2026",
                    "latest tamil songs",
                    "anirudh latest songs",
                    "kollywood new songs"
                )
                com.vikify.app.vikifyui.data.MusicLanguage.PUNJABI -> listOf(
                    "punjabi hits 2026",
                    "ap dhillon latest",
                    "new punjabi songs",
                    "punjabi party songs"
                )
                com.vikify.app.vikifyui.data.MusicLanguage.MALAYALAM -> listOf(
                    "malayalam hit songs 2026",
                    "latest malayalam songs",
                    "mollywood new songs"
                )
                com.vikify.app.vikifyui.data.MusicLanguage.KANNADA -> listOf(
                    "kannada hit songs 2026",
                    "latest kannada songs",
                    "sandalwood new songs"
                )
                com.vikify.app.vikifyui.data.MusicLanguage.KOREAN -> listOf(
                    "kpop hits 2026",
                    "bts latest",
                    "blackpink songs",
                    "trending kpop 2026"
                )
                com.vikify.app.vikifyui.data.MusicLanguage.SPANISH -> listOf(
                    "spanish hits 2026",
                    "reggaeton new songs",
                    "bad bunny latest",
                    "latin pop hits"
                )
                com.vikify.app.vikifyui.data.MusicLanguage.LATIN -> listOf(
                    "latin hits 2026",
                    "reggaeton 2026",
                    "latin urbano"
                )
                com.vikify.app.vikifyui.data.MusicLanguage.ARABIC -> listOf(
                    "arabic hits 2026",
                    "latest arabic songs",
                    "khaleeji songs"
                )
                com.vikify.app.vikifyui.data.MusicLanguage.JAPANESE -> listOf(
                    "jpop hits 2026",
                    "yoasobi latest",
                    "anime openings 2026",
                    "japanese trending music"
                )
            }
            result[lang] = queries
        }
        
        return result
    }
    
    /**
     * Get the display title for a language section
     */
    private fun getLanguageSectionTitle(lang: com.vikify.app.vikifyui.data.MusicLanguage): String {
        return when (lang) {
            com.vikify.app.vikifyui.data.MusicLanguage.ENGLISH -> "🌍 Global Top Charts"
            com.vikify.app.vikifyui.data.MusicLanguage.HINDI -> "🇮🇳 Bollywood Hits"
            com.vikify.app.vikifyui.data.MusicLanguage.TELUGU -> "🎬 Telugu Hits"
            com.vikify.app.vikifyui.data.MusicLanguage.TAMIL -> "🎬 Tamil Hits"
            com.vikify.app.vikifyui.data.MusicLanguage.PUNJABI -> "🎤 Punjabi Hits"
            com.vikify.app.vikifyui.data.MusicLanguage.MALAYALAM -> "🎬 Malayalam Hits"
            com.vikify.app.vikifyui.data.MusicLanguage.KANNADA -> "🎬 Kannada Hits"
            com.vikify.app.vikifyui.data.MusicLanguage.KOREAN -> "🇰🇷 K-Pop Hits"
            com.vikify.app.vikifyui.data.MusicLanguage.SPANISH -> "🇪🇸 Spanish Hits"
            com.vikify.app.vikifyui.data.MusicLanguage.LATIN -> "🌎 Latin Hits"
            com.vikify.app.vikifyui.data.MusicLanguage.ARABIC -> "🇸🇦 Arabic Hits"
            com.vikify.app.vikifyui.data.MusicLanguage.JAPANESE -> "🇯🇵 J-Pop Hits"
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

        // Fetch YouTube home with user's language preference
        val userLocale = buildYouTubeLocale()
        YouTube.home(locale = userLocale).onSuccess { page ->
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
        // SECTION 1.5: ✨ AI DJ MIX - Personalized Radio based on listening DNA
        // This is the MAGIC FEATURE that creates a unique experience
        // ─────────────────────────────────────────────────────────────────
        val allListeningHistory = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + jumpBackIn.value.orEmpty())
            .distinctBy { it.song.id }
        
        if (allListeningHistory.size >= 5) {
            // Helper function to calculate total plays from play count history
            fun getTotalPlays(song: Song): Int {
                return song.playCount?.sumOf { it.count } ?: 0
            }
            
            // Create 3 different AI-curated mixes based on listening patterns
            
            // Mix 1: "Your Flow" - Based on most played with similar energy
            val yourFlowSongs = allListeningHistory
                .sortedByDescending { getTotalPlays(it) }
                .take(25)
                .map { it.song.id }
            
            if (yourFlowSongs.size >= 5) {
                sections.add(FeedSection.DJMixCard(
                    id = "dj_your_flow",
                    title = "Your Flow",
                    subtitle = "AI-curated based on your taste",
                    description = "A personalized mix of your favorites and songs you'll love",
                    gradientColors = listOf(0xFF6366F1, 0xFF8B5CF6, 0xFFEC4899),
                    songCount = yourFlowSongs.size,
                    duration = "${yourFlowSongs.size * 3}+ min",
                    songIds = yourFlowSongs
                ))
            }
            
            // Mix 2: "Chill Vibes" - Low energy relaxing songs
            val chillSongs = allListeningHistory
                .filter { song ->
                    val dna = generateSongDNA(song.toMediaMetadata())
                    dna.energyLevel < 0.5f
                }
                .shuffled()
                .take(20)
                .map { it.song.id }
            
            if (chillSongs.size >= 5) {
                sections.add(FeedSection.DJMixCard(
                    id = "dj_chill_vibes",
                    title = "Chill Vibes",
                    subtitle = "Relax and unwind",
                    description = "Mellow tunes for focus, relaxation, or winding down",
                    gradientColors = listOf(0xFF0EA5E9, 0xFF06B6D4, 0xFF14B8A6),
                    songCount = chillSongs.size,
                    duration = "${chillSongs.size * 3}+ min",
                    songIds = chillSongs
                ))
            }
            
            // Mix 3: "Energy Boost" - High energy workout songs
            val energySongs = allListeningHistory
                .filter { song ->
                    val dna = generateSongDNA(song.toMediaMetadata())
                    dna.energyLevel >= 0.6f
                }
                .shuffled()
                .take(20)
                .map { it.song.id }
            
            if (energySongs.size >= 5) {
                sections.add(FeedSection.DJMixCard(
                    id = "dj_energy_boost",
                    title = "Energy Boost",
                    subtitle = "Get pumped up",
                    description = "High-energy tracks to fuel your workout or commute",
                    gradientColors = listOf(0xFFF59E0B, 0xFFEF4444, 0xFFEC4899),
                    songCount = energySongs.size,
                    duration = "${energySongs.size * 3}+ min",
                    songIds = energySongs
                ))
            }
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
        // SECTION 3.5: Language-Based Content - DEDICATED ROW FOR EACH LANGUAGE
        // ─────────────────────────────────────────────────────────────────
        val languageQueriesMap = getLanguageSearchQueriesMap()
        
        // Create a dedicated section for EACH selected language
        for ((language, queries) in languageQueriesMap) {
            val languageSongs = mutableListOf<RailItem>()
            
            // Search for this language's songs
            queries.take(2).forEach { query ->
                YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).onSuccess { result ->
                    result.items.filterIsInstance<com.zionhuang.innertube.models.SongItem>().take(8).forEach { song ->
                        languageSongs.add(RailItem(
                            id = song.id,
                            title = song.title,
                            subtitle = song.artists.joinToString { it.name },
                            imageUrl = song.thumbnail
                        ))
                    }
                }
            }
            
            // Add this language's section if we got results
            if (languageSongs.isNotEmpty()) {
                sections.add(FeedSection.HorizontalRail(
                    id = "lang_${language.name.lowercase()}",
                    title = getLanguageSectionTitle(language),
                    subtitle = "Top ${language.displayName} songs right now",
                    items = languageSongs.shuffled().distinctBy { it.id }.take(15)
                ))
            }
            
            // For English, also add a "Global Albums" section with chart-toppers
            if (language == com.vikify.app.vikifyui.data.MusicLanguage.ENGLISH) {
                val globalAlbums = mutableListOf<RailItem>()
                YouTube.search("billboard top albums 2026", YouTube.SearchFilter.FILTER_ALBUM).onSuccess { result ->
                    result.items.filterIsInstance<com.zionhuang.innertube.models.AlbumItem>().take(12).forEach { album ->
                        globalAlbums.add(RailItem(
                            id = album.browseId,
                            title = album.title,
                            subtitle = album.artists?.joinToString { it.name } ?: "Album",
                            imageUrl = album.thumbnail,
                            itemType = RailItemType.ALBUM
                        ))
                    }
                }
                if (globalAlbums.isNotEmpty()) {
                    sections.add(FeedSection.LargeSquareRail(
                        id = "global_albums",
                        title = "🏆 Chart-Topping Albums",
                        subtitle = "Worldwide best sellers",
                        items = globalAlbums.distinctBy { it.id }.take(10)
                    ))
                }
            }
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
        
        // YouTube Home sections (generic content at the end)
        homePage.value?.sections?.forEach { ytSection ->
            sections.add(FeedSection.HorizontalRail(
                id = "yt_${ytSection.title.hashCode()}",
                title = ytSection.title,
                items = ytSection.items.map { it.toRailItem() }
            ))
        }
        
        homeSections.value = sections
        isLoading.value = false
    }

    private fun Song.toRailItem() = RailItem(
        id = song.id,
        title = song.title,
        subtitle = artists.joinToString { it.name },
        imageUrl = song.thumbnailUrl
    )

    private fun YTItem.toRailItem() = RailItem(
        id = id,
        title = title,
        subtitle = when (this) {
            is SongItem -> artists.joinToString { it.name }
            is AlbumItem -> artists?.joinToString { it.name } ?: "Album"
            else -> ""
        },
        imageUrl = thumbnail
    )

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
        
        // Listen for language preference changes and refresh content
        viewModelScope.launch {
            selectedLanguages.collect { languages ->
                // Refresh home content when language preferences change
                // Use a small delay to avoid rapid refreshes
                kotlinx.coroutines.delay(500)
                if (!isRefreshing.value && !isLoading.value) {
                    refresh()
                }
            }
        }
    }
}
