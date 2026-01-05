package com.vikify.app.vikifyui

import com.vikify.app.LocalPlayerConnection

import android.app.Activity
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vikify.app.vikifyui.components.*
import com.vikify.app.vikifyui.data.PlayerViewModel
import com.vikify.app.vikifyui.screens.ArtistScreen
import com.vikify.app.vikifyui.screens.HomeScreen
import com.vikify.app.vikifyui.screens.LibraryScreen
import com.vikify.app.vikifyui.screens.LikedSongsScreen
import com.vikify.app.vikifyui.screens.DownloadsScreen
import com.vikify.app.vikifyui.screens.CinematicOnboardingScreen
import com.vikify.app.vikifyui.screens.ProfileScreen
import com.vikify.app.vikifyui.screens.PlaylistScreen
import com.vikify.app.vikifyui.screens.SearchScreen
import com.vikify.app.vikifyui.screens.TimeCapsuleScreen
import com.vikify.app.vikifyui.theme.*
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.vikify.app.viewmodels.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.vikify.app.vikifyui.screens.BrowseScreen
import com.vikify.app.vikifyui.screens.MoodAndGenresScreen
import com.vikify.app.vikifyui.screens.DownloadsScreen

/**
 * VikifyApp
 * 
 * Main app with:
 * - Bottom navigation (Home, Search, Library, Profile)
 * - Floating glass player above nav
 * - Expanded player overlay
 */

enum class NavScreen(
    val label: String,
    val iconOutlined: ImageVector,
    val iconFilled: ImageVector
) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Search("Search", Icons.Outlined.Search, Icons.Filled.Search),
    Library("Library", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic),

    Profile("Profile", Icons.Outlined.Person, Icons.Filled.Person)
}

@Composable

fun VikifyApp(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    spotifyAuthCode: String? = null
) {
    val playerConnection = LocalPlayerConnection.current
    LaunchedEffect(playerConnection) {
        playerConnection?.let { viewModel.setPlayerConnection(it) }
    }
    
    val uiState by viewModel.uiState.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsState()
    val visualState by viewModel.visualState.collectAsState()
    
    // ═══════════════════════════════════════════════════════════════
    // AMBIENT MODE - Contextual UI Density
    // ═══════════════════════════════════════════════════════════════
    val ambientModeState by viewModel.ambientModeState.collectAsState()
    val isAmbientMode = ambientModeState.mode == com.vikify.app.vikifyui.data.AmbientModeType.AMBIENT
    
    // Animated ambient mode transitions (800ms as per design system)
    val animatedNavOpacity by animateFloatAsState(
        targetValue = ambientModeState.navOpacity,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "navOpacity"
    )
    val animatedGlowMultiplier by animateFloatAsState(
        targetValue = ambientModeState.glowRadiusMultiplier,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "glowMultiplier"
    )
    
    val isExpanded = uiState.density > 0.5f
    var isDarkTheme by remember { mutableStateOf(true) }
    var currentScreen by remember { mutableStateOf(NavScreen.Home) }
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var selectedArtistId by remember { mutableStateOf<String?>(null) }
    var selectedArtistName by remember { mutableStateOf<String?>(null) } // Artist name for search
    var activePlaylistId by remember { mutableStateOf<String?>(null) }
    var activePlaylistInfo by remember { mutableStateOf<com.vikify.app.spotify.SpotifyPlaylist?>(null) }
    var playlistTracks by remember { mutableStateOf<List<com.vikify.app.vikifyui.data.Track>>(emptyList()) }
    var initialSearchQuery by remember { mutableStateOf<String?>(null) } // For mood navigation
    
    // Browse Screen State (Moods/Genres)
    var selectedBrowseId by remember { mutableStateOf<String?>(null) }
    var selectedBrowseParams by remember { mutableStateOf<String?>(null) }
    var browseTitle by remember { mutableStateOf<String?>(null) }
    var browseColor by remember { mutableStateOf<Int?>(null) }
    
    // Spotify state - shared across recompositions
    val context = LocalContext.current
    val spotifyRepo = remember { com.vikify.app.spotify.SpotifyRepository(context) }
    var spotifyUser by remember { mutableStateOf<com.vikify.app.spotify.SpotifyUser?>(null) }
    var spotifyPlaylists by remember { mutableStateOf<List<com.vikify.app.spotify.SpotifyPlaylist>>(emptyList()) }
    var isSpotifyLoggedIn by remember { mutableStateOf(spotifyRepo.isLoggedIn) }
    
    // Onboarding state - check SharedPreferences
    val prefs = remember { context.getSharedPreferences("vikify_prefs", Context.MODE_PRIVATE) }
    var hasCompletedOnboarding by remember { mutableStateOf(prefs.getBoolean("onboarding_complete", false)) }
    var userDisplayName by remember { mutableStateOf(prefs.getString("user_display_name", null)) }
    
    // Handle Spotify auth code when received
    LaunchedEffect(spotifyAuthCode) {
        spotifyAuthCode?.let { code ->
            val success = spotifyRepo.exchangeCodeForToken(code)
            if (success) {
                isSpotifyLoggedIn = true
                spotifyUser = spotifyRepo.getCurrentUser()
                spotifyPlaylists = spotifyRepo.getUserPlaylists()
            }
        }
    }
    
    // Load user data on start if already logged in
    LaunchedEffect(Unit) {
        if (spotifyRepo.isLoggedIn) {
            spotifyUser = spotifyRepo.getCurrentUser()
            spotifyPlaylists = spotifyRepo.getUserPlaylists()
        }
    }
    
    // Fetch album tracks when selectedAlbumId changes
    LaunchedEffect(selectedAlbumId) {
        selectedAlbumId?.let { albumId ->
            // Capture albumId in let scope - safe from race conditions
            withContext(Dispatchers.IO) {
                // Fetch album details from YouTube Music (Innertube)
                val result = YouTube.album(albumId)
                result.onSuccess { albumPage ->
                    activePlaylistInfo = null // Clear playlist info to avoid confusion
                    playlistTracks = albumPage.songs.map { song ->
                        com.vikify.app.vikifyui.data.Track(
                            id = song.id,
                            title = song.title,
                            // Use song artists or fallback to "Unknown"
                            artist = song.artists.firstOrNull()?.name ?: "Unknown",
                            remoteArtworkUrl = song.thumbnail,
                            // Innertube duration is Int (seconds) or null
                            duration = (song.duration?.toLong() ?: 0L) * 1000L,
                            originalBackendRef = song
                        )
                    }
                }.onFailure {
                    // Could show error toast here
                }
            }
        }
    }

    // Fetch playlist tracks when activePlaylistId changes
    LaunchedEffect(activePlaylistId) {
        activePlaylistId?.let { playlistId ->
            // Capture playlistId in let scope - safe from race conditions
            if (playlistId.startsWith("mock_")) {
                // Mock handled by PlaylistScreen default logic
                playlistTracks = emptyList()
            } else {
                // Try local first
                val localTracks = viewModel.loadLocalPlaylist(playlistId)
                if (localTracks != null) {
                    playlistTracks = localTracks
                } else {
                    // Spotify fetch with batch YouTube ID resolution (Hybrid Sync)
                    // We use viewModel to access the database and ensure IDs match DB entities
                    val tracks = viewModel.loadSpotifyPlaylist(playlistId, spotifyRepo)
                    playlistTracks = tracks.map { st ->
                        com.vikify.app.vikifyui.data.Track(
                            id = st.id, // This is now the DB ID (YT_ID or UNRESOLVED_...)
                            title = st.title,
                            artist = st.artist,
                            remoteArtworkUrl = st.imageUrl,
                            duration = st.duration,
                            youtubeId = st.youtubeId  // Pre-resolved or null (JIT will handle nulls via UNRESOLVED mechanism)
                        )
                    }
                }
            }
        } ?: run {
            playlistTracks = emptyList()
        }
    }
    
    // NavController for HomeScreen (even though we don't navigate yet)
    val navController = rememberNavController()
    
    // Liked Songs state
    var showLikedSongs by remember { mutableStateOf(false) }
    val likedSongs by viewModel.likedSongs.collectAsState()
    val likedSongsCount by viewModel.likedSongsCount.collectAsState()
    
    // Downloads state
    var showDownloads by remember { mutableStateOf(false) }
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val downloadedSongsCount by viewModel.downloadedSongsCount.collectAsState()

    // Time Capsule state
    var showTimeCapsule by remember { mutableStateOf(false) }
    
    // Mood & Genres screen state
    var showMoodAndGenres by remember { mutableStateOf(false) }

    // Show onboarding for first-time users
    // Auth State
    // Show onboarding for first-time users
    // Auth State
    // Auth State (from HomeViewModel)
    val currentUser by homeViewModel.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Back press state
    var backPressedOnce by remember { mutableStateOf(false) }
    
    // Check if onboarding is complete AND we have a valid session (either signed in or guest/skipped)
    // For now, relies on shared prefs "onboarding_complete" mostly to avoid showing it every time
    // But ideally should check currentUser
    
    // Google Sign In Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                account.idToken?.let { idToken ->
                    scope.launch {
                        homeViewModel.authManager.signInWithGoogle(idToken)
                        // Onboarding complete!
                        prefs.edit().putBoolean("onboarding_complete", true).apply()
                        hasCompletedOnboarding = true
                    }
                } ?: run {
                    Toast.makeText(context, "Google Sign In Failed: No ID Token", Toast.LENGTH_SHORT).show()
                }
            } catch (e: com.google.android.gms.common.api.ApiException) {
                // Google Sign In failed, update UI appropriately
                Toast.makeText(context, "Google Sign In Failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (!hasCompletedOnboarding) {
        CinematicOnboardingScreen(
            onGoogleLogin = {
                val signInClient = homeViewModel.authManager.getGoogleSignInClient(context)
                googleSignInLauncher.launch(signInClient.signInIntent)
            },
            onGuestLogin = {
                scope.launch {
                    homeViewModel.authManager.signInAnonymously()
                    // Onboarding complete for guest
                    prefs.edit().putBoolean("onboarding_complete", true).apply()
                    hasCompletedOnboarding = true
                }
            }
        )
        return
    }
    
    // Smart Back Button Handling
    
    BackHandler {
        when {
            // 1. Close overlays first (Queue, Lyrics)
            uiState.showQueue || uiState.showLyrics -> {
                viewModel.closeOverlays()
            }
            
            // 2. Collapse expanded player
            isExpanded -> {
                viewModel.collapsePlayer()
            }
            
            // 3. Close playlist overlay
            activePlaylistId != null -> {
                activePlaylistId = null
                activePlaylistInfo = null
            }
            
            // 4. Close Liked Songs screen
            showLikedSongs -> {
                showLikedSongs = false
            }
            
            // 5. Close Downloads screen
            showDownloads -> {
                showDownloads = false
            }
            
            // 5.5 Close Time Capsule
            showTimeCapsule -> {
                showTimeCapsule = false
            }
            
            // 5.6 Close Mood & Genres
            showMoodAndGenres -> {
                showMoodAndGenres = false
            }
            
            // 6. Navigate to Home if on another tab
            currentScreen != NavScreen.Home -> {
                currentScreen = NavScreen.Home
            }
            
            // 7. Exit app with double-press confirmation
            else -> {
                if (backPressedOnce) {
                    // Exit the app
                    (context as? Activity)?.finish()
                } else {
                    backPressedOnce = true
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                    // Reset after 2 seconds
                    scope.launch {
                        delay(2000)
                        backPressedOnce = false
                    }
                }
            }
        }
    }
    
    VikifyTheme(themeMode = if (isDarkTheme) ThemeMode.DARK else ThemeMode.LIGHT) {
    Box(modifier = modifier.fillMaxSize()) {
        // Main content based on selected tab
        if (!isExpanded && !showLikedSongs && !showDownloads && !showMoodAndGenres && selectedBrowseId == null) {
            when (currentScreen) {
                NavScreen.Home -> {
                    HomeScreen(
                        navController = navController,
                        onPlaylistClick = { id, playlist -> 
                            activePlaylistId = id
                            activePlaylistInfo = playlist
                        },
                        onLikedSongsClick = { showLikedSongs = true },
                        onDownloadsClick = { showDownloads = true }, // Wire up Downloads button
                        onMoodClick = { moodTitle, browseId, color ->
                            // Use dedicated Browse Screen
                            if (browseId != null) {
                                selectedBrowseId = browseId
                                browseTitle = moodTitle
                                browseColor = color
                            } else {
                                // Fallback to search if no ID (shouldn't happen with new API)
                                initialSearchQuery = moodTitle
                                currentScreen = NavScreen.Search
                            }
                        },
                        onAlbumClick = { albumId ->
                            selectedAlbumId = albumId
                        },
                        onMoodAndGenresClick = { showMoodAndGenres = true }
                    )
                }
                NavScreen.Search -> {
                    SearchScreen(
                        onCategoryClick = { /* Navigate to category */ },
                        onArtistClick = { artist ->
                            selectedArtistId = artist.id
                            // TODO: Show artist screen
                        },
                        onAlbumClick = { album ->
                            // When an album is clicked in search (AlbumItem), we get its browseId
                            selectedAlbumId = album.id
                            // TODO: Show album/playlist screen
                        },
                        onTrackClick = { track -> viewModel.playTrack(track) },
                        onPlaylistClick = { id, playlist -> 
                            activePlaylistId = id
                            activePlaylistInfo = playlist
                        },
                        spotifyRepository = spotifyRepo,
                        initialQuery = initialSearchQuery, // Pre-fill from mood click
                        onInitialQueryConsumed = { initialSearchQuery = null },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                NavScreen.Library -> {
                    LibraryScreen(
                        onTrackClick = { trackId -> 
                            // TODO: Look up track by ID and play
                        },
                        onPlaylistClick = { id, playlist -> 
                            activePlaylistId = id
                            activePlaylistInfo = playlist
                        },
                        onLikedSongsClick = { showLikedSongs = true },
                        onDownloadsClick = { showDownloads = true },
                        playlists = spotifyPlaylists,
                        downloadedSongs = downloadedSongs,
                        onDownloadedSongClick = { song -> viewModel.playDownloadedSong(song) },
                        onSettingsClick = { /* Navigate to settings */ },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                NavScreen.Profile -> {
                    ProfileScreen(
                        onSpotifyLogin = {
                            context.startActivity(spotifyRepo.startLogin())
                        },
                        spotifyUser = spotifyUser,
                        spotifyPlaylists = spotifyPlaylists,
                        isSpotifyLoggedIn = isSpotifyLoggedIn,
                        onSpotifyLogout = {
                            spotifyRepo.logout()
                            spotifyUser = null
                            spotifyPlaylists = emptyList()
                            isSpotifyLoggedIn = false
                        },
                        likedSongsCount = likedSongsCount,
                        onLikedSongsClick = { showLikedSongs = true },
                        modifier = Modifier.fillMaxSize(),
                        onThemeToggle = { isDarkTheme = !isDarkTheme },
                        isDarkTheme = isDarkTheme,
                        userName = userDisplayName ?: currentUser?.displayName,
                        userEmail = currentUser?.email.also { 
                            android.util.Log.d("VikifyApp", "Profile User: ${currentUser?.uid}, Email: $it, Anon: ${currentUser?.isAnonymous}") 
                        },
                        onTimeCapsuleClick = { showTimeCapsule = true },
                        onSignIn = {
                            val signInClient = homeViewModel.authManager.getGoogleSignInClient(context)
                            googleSignInLauncher.launch(signInClient.signInIntent)
                        },
                        isGuest = currentUser?.isAnonymous == true
                    )
                }
                
                // Settings screen merged into Profile
            }
        }

        
        // Liked Songs Screen
        if (showLikedSongs && !isExpanded) {
            LikedSongsScreen(
                likedSongs = likedSongs,
                likedCount = likedSongsCount,
                onSongClick = { song -> 
                    viewModel.playLikedSong(song)
                },
                onBackClick = { showLikedSongs = false },
                onShufflePlay = { viewModel.shufflePlayLikedSongs(likedSongs) },
                currentSongId = uiState.currentTrack?.id,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Downloads Screen
        if (showDownloads && !isExpanded) {
            DownloadsScreen(
                downloadedSongs = downloadedSongs,
                downloadCount = downloadedSongsCount,
                onSongClick = { song -> 
                    viewModel.playDownloadedSong(song)
                },
                onBackClick = { showDownloads = false },
                onShufflePlay = { viewModel.shufflePlayDownloadedSongs(downloadedSongs) },
                currentSongId = uiState.currentTrack?.id,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Time Capsule Screen (Full Overlay)
        if (showTimeCapsule) {
            TimeCapsuleScreen(
                onBackClick = { showTimeCapsule = false }
            )
        }
        
        // Artist Profile Screen (Wiki-Style)
        if (selectedArtistName != null && !isExpanded) {
            ArtistScreen(
                artistName = selectedArtistName!!,
                onBackClick = { selectedArtistName = null },
                onTrackClick = { track ->
                    viewModel.playTrack(track)
                },
                onShuffleClick = { /* TODO: Shuffle artist tracks */ },
                onArtistClick = { newArtistName ->
                    // Navigate to related artist
                    selectedArtistName = newArtistName
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Expanded player (full screen)
        if (isExpanded) {
            val currentTrackId = uiState.currentTrack?.id ?: ""
            PlayerContainer(
                uiState = uiState,
                lyrics = lyrics,
                isDownloaded = downloadedTrackIds.contains(currentTrackId),
                accentColor = visualState.accentColor, // Pass dynamic color
                onPlayPause = viewModel::togglePlayPause,
                onExpand = viewModel::expandPlayer,
                onCollapse = viewModel::collapsePlayer,
                onSkipNext = viewModel::skipNext,
                onSkipPrevious = viewModel::skipPrevious,
                onSeek = viewModel::updateProgress,
                onQueueClick = viewModel::toggleQueue,
                onLyricsClick = viewModel::toggleLyrics,
                onShuffleClick = viewModel::toggleShuffle,
                onRepeatClick = viewModel::toggleRepeat,
                onLikeClick = viewModel::toggleLike,
                onDownloadClick = viewModel::downloadCurrentTrack,
                onAddToPlaylist = { showPlaylistPicker = true },
                onArtistClick = { artistName ->
                    selectedArtistName = artistName
                    viewModel.collapsePlayer()
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
         // 5. BROWSE OVERLAY
        AnimatedVisibility(
            visible = selectedBrowseId != null && !isExpanded,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            if (selectedBrowseId != null) {
                BrowseScreen(
                    browseId = selectedBrowseId!!,
                    params = selectedBrowseParams,
                    initialTitle = browseTitle,
                    gradientColor = browseColor,
                    onBackClick = { selectedBrowseId = null },
                    onTrackClick = { track -> viewModel.playTrack(track) },
                    onPlaylistClick = { id -> 
                        activePlaylistId = id 
                        // We don't have full object, let Library load it or create minimal
                        activePlaylistInfo = null 
                    },
                    onAlbumClick = { id -> selectedAlbumId = id },
                    onArtistClick = { id -> selectedArtistId = id }
                )
            }
        }
        
        // MOOD & GENRES OVERLAY
        AnimatedVisibility(
            visible = showMoodAndGenres && !isExpanded,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            MoodAndGenresScreen(
                onBackClick = { showMoodAndGenres = false },
                onMoodClick = { browseId, params, title, color ->
                    // Navigate to BrowseScreen for the selected mood
                    selectedBrowseId = browseId
                    selectedBrowseParams = params
                    browseTitle = title
                    browseColor = color
                    showMoodAndGenres = false
                }
            )
        }

        // 6. MINI PLAYER (Bottom Layer)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                // Floating glass player with swipe gestures
                SwipeableMiniPlayer(
                    track = uiState.currentTrack,
                    isPlaying = uiState.isPlaying,
                    progress = uiState.progress,
                    onPlayPause = viewModel::togglePlayPause,
                    onExpand = viewModel::expandPlayer,
                    onSkipNext = viewModel::skipNext,
                    onSkipPrevious = viewModel::skipPrevious,
                    accentColor = visualState.accentColor,
                    onColorExtracted = viewModel::updateAccentColor,
                    modifier = Modifier
                        .padding(horizontal = Spacing.MD, vertical = Spacing.SM)
                        .graphicsLayer { alpha = animatedNavOpacity }
                )
                
                // Bottom navigation - ambient mode fades to 60%
                Box(
                    modifier = Modifier.graphicsLayer { alpha = animatedNavOpacity }
                ) {
                    BottomNavigation(
                        currentScreen = currentScreen,
                        onScreenSelected = { currentScreen = it }
                    )
                }
            }
        }
        
        // Queue overlay
        if (uiState.showQueue) {
            val queueTracks by viewModel.queueTracks.collectAsState()
            val userQueueTracks by viewModel.userQueueTracks.collectAsState()
            val contextTitle by viewModel.contextTitle.collectAsState()
            
            // Find current track index in queue
            val currentTrackIndex = queueTracks.indexOfFirst { it.id == uiState.currentTrack?.id }.coerceAtLeast(0)
            
            QueueOverlay(
                currentTrack = uiState.currentTrack,
                currentTrackIndex = currentTrackIndex,
                queueTracks = queueTracks,
                userQueueTracks = userQueueTracks,  // New: Spotify-style "Next In Queue"
                contextTitle = contextTitle,         // New: "Playing from: Album/Playlist"
                onDismiss = viewModel::closeOverlays,
                onTrackClick = { track ->
                    viewModel.seekToQueueItem(track)
                },
                onRemoveTrack = { index ->
                    viewModel.removeFromQueue(index)
                },
                onMoveTrack = { from, to ->
                    viewModel.moveQueueItem(from, to)
                },
                onSkipNext = viewModel::skipNext,
                onSkipPrevious = viewModel::skipPrevious
            )
        }
        
        // Lyrics overlay
        if (uiState.showLyrics) {
            LyricsOverlay(
                track = uiState.currentTrack,
                currentProgress = uiState.progress,
                lyrics = lyrics,
                onDismiss = viewModel::closeOverlays
            )
        }

        // Playlist Overlay
        if (activePlaylistId != null) {
            val playlist = activePlaylistInfo ?: spotifyPlaylists.find { it.id == activePlaylistId }
            val downloadedIds by viewModel.downloadedTrackIds.collectAsState()
            
            PlaylistScreen(
                playlistId = activePlaylistId,
                playlistName = playlist?.name ?: activePlaylistId?.takeIf { it.startsWith("mock_") }?.substring(5) ?: "Playlist",
                tracks = playlistTracks,
                coverUrl = playlist?.imageUrl ?: playlistTracks.firstOrNull()?.remoteArtworkUrl,
                currentTrack = uiState.currentTrack,
                onTrackClick = { track -> 
                    // Use playPlaylistWithIndex to queue the entire playlist
                    // ensuring continuous playback and queue context
                    val playlistName = playlist?.name ?: "Playlist"
                    viewModel.playPlaylistWithIndex(playlistTracks, playlistTracks.indexOf(track), playlistName)
                },
                onBackClick = { 
                    activePlaylistId = null
                    activePlaylistInfo = null
                },
                onShuffleClick = { viewModel.playAllTracks(playlistTracks, shuffle = true) },
                onPlayAllClick = { viewModel.playAllTracks(playlistTracks, shuffle = false) },
                onDownloadClick = { viewModel.downloadPlaylist(playlistTracks) },
                onAddToQueue = { track -> viewModel.addToQueue(track) },
                isDownloading = isDownloading,
                downloadProgress = downloadProgress,
                downloadedTrackIds = downloadedIds,
                isPlaylistDownloaded = playlistTracks.isNotEmpty() && playlistTracks.all { downloadedIds.contains(it.id) },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Playlist Picker Dialog (Global)
        var showPlaylistPicker by remember { mutableStateOf(false) }
        val localPlaylists by viewModel.localPlaylists.collectAsState()
        
        if (showPlaylistPicker) {
            PlaylistPickerDialog(
                playlists = localPlaylists,
                onDismissRequest = { showPlaylistPicker = false },
                onPlaylistSelected = { playlistId ->
                    viewModel.addToPlaylist(playlistId)
                    showPlaylistPicker = false
                },
                onCreatePlaylist = { name ->
                    viewModel.createLocalPlaylist(name)
                    // Don't dismiss, let user see it appear or auto-select?
                    // Better to just stay open or auto-select the new one?
                    // Simplest: just creating it effectively adds it to the list, let user click.
                    // Or I can add and auto-select. For now, just create.
                }
            )
        }
        
        // Album Overlay (handled by PlaylistScreen)
        if (selectedAlbumId != null) {
            val downloadedIds by viewModel.downloadedTrackIds.collectAsState()
            
            PlaylistScreen(
                playlistId = selectedAlbumId!!, // This will trigger fetchAlbum in ViewModel
                playlistName = "Album", // Will be updated by ViewModel
                tracks = playlistTracks, // ViewModel updates this
                coverUrl = playlistTracks.firstOrNull()?.remoteArtworkUrl,
                currentTrack = uiState.currentTrack,
                onTrackClick = { track -> 
                    // Use playFromPlaylist to queue the entire album
                    viewModel.playFromPlaylist(playlistTracks, playlistTracks.indexOf(track), "Album") 
                },
                onBackClick = { 
                    selectedAlbumId = null
                },
                onShuffleClick = { viewModel.playAllTracks(playlistTracks, shuffle = true) },
                onPlayAllClick = { viewModel.playAllTracks(playlistTracks, shuffle = false) },
                onDownloadClick = { viewModel.downloadPlaylist(playlistTracks) },
                onAddToQueue = { track -> viewModel.addToQueue(track) },
                isDownloading = isDownloading,
                downloadProgress = downloadProgress,
                downloadedTrackIds = downloadedIds,
                isPlaylistDownloaded = playlistTracks.isNotEmpty() && playlistTracks.all { downloadedIds.contains(it.id) },

            )
        }
    }
    }


}

/**
 * Swipeable Mini Player
 * 
 * Wraps GlassMusicPlayer with swipe gestures:
 * - Swipe right → Previous song
 * - Swipe left → Skip to next song
 * Uses SwipeToDismissBox for proper spring-back physics
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableMiniPlayer(
    track: com.vikify.app.vikifyui.data.Track?,
    isPlaying: Boolean,
    progress: Float = 0f,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    accentColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFE53935),
    onColorExtracted: (androidx.compose.ui.graphics.Color) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (track == null) return
    
    val view = LocalView.current
    
    // SwipeToDismiss state with custom handler
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swiped right → Previous
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onSkipPrevious()
                    false // Return false to snap back (don't actually dismiss)
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swiped left → Next
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onSkipNext()
                    false // Return false to snap back
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.35f } // 35% of width to trigger
    )
    
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Background hints during swipe
            val direction = dismissState.dismissDirection
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Filled.SkipPrevious
                SwipeToDismissBoxValue.EndToStart -> Icons.Filled.SkipNext
                else -> null
            }
            val backgroundColor by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50).copy(alpha = 0.3f)
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFF2196F3).copy(alpha = 0.3f)
                    else -> Color.Transparent
                },
                label = "bgColor"
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.MD)
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor),
                contentAlignment = alignment
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .size(32.dp)
                    )
                }
            }
        },
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        // The actual player content - using premium mini player
        PremiumMiniPlayer(
            track = track,
            isPlaying = isPlaying,
            progress = progress,
            onPlayPause = onPlayPause,
            onExpand = onExpand,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            accentColor = accentColor,
            onColorExtracted = onColorExtracted
        )
    }
}

/**
 * Bottom Navigation
 * 
 * Clean, branded bottom nav with Vikify accent
 */
@Composable
private fun BottomNavigation(
    currentScreen: NavScreen,
    onScreenSelected: (NavScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    // Vikify branded nav bar
    val colors = VikifyTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        colors.surfaceBackground,
                        colors.surface
                    )
                )
            )
            .navigationBarsPadding()
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavScreen.values().forEach { screen ->
            val isSelected = screen == currentScreen
            val accentColor = colors.accent // Vikify accent
            
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onScreenSelected(screen) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isSelected) screen.iconFilled else screen.iconOutlined,
                    contentDescription = screen.label,
                    tint = if (isSelected) accentColor else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = screen.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) accentColor else TextSecondary,
                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                )
            }
        }
    }
}

