package com.vikify.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.view.WindowCompat
import com.vikify.app.db.MusicDatabase
import com.vikify.app.playback.MediaControllerViewModel
import com.vikify.app.playback.PlayerConnection
import com.vikify.app.utils.NetworkConnectivityObserver
import com.vikify.app.vikifyui.VikifyApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Legacy providers needed to convince Gradle to compile the older fragments/composables
val LocalNetworkConnected = staticCompositionLocalOf { mutableStateOf(false) }
val LocalPlayerConnection = compositionLocalOf<PlayerConnection?> { null }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var database: MusicDatabase

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private val controllerViewModel: MediaControllerViewModel by viewModels()
    private lateinit var connectivityObserver: NetworkConnectivityObserver
    private val isNetworkConnected = mutableStateOf(false)

    // Helper for requests in MainActivityUtils
    val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        // Handle if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        connectivityObserver = NetworkConnectivityObserver(this)
        
        // Initialize PlayerConnection (needed for real music)
        lifecycle.addObserver(controllerViewModel)
        controllerViewModel.addControllerCallback(lifecycle) { controller, _ ->
            playerConnection = PlayerConnection(controllerViewModel, database)
        }

        setContent {
            com.vikify.app.vikifyui.theme.VikifyTheme {
                CompositionLocalProvider(
                    LocalNetworkConnected provides isNetworkConnected,
                    LocalPlayerConnection provides playerConnection
                ) {
                    VikifyApp(spotifyAuthCode = spotifyAuthCode)
                }
            }
        }
        
        // Handle if app was started with Spotify callback
        handleSpotifyCallback(intent)
    }
    
    // Spotify auth code state
    private var spotifyAuthCode by mutableStateOf<String?>(null)
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleSpotifyCallback(intent)
    }
    
    private fun handleSpotifyCallback(intent: android.content.Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "vikify" && uri.host == "spotify-callback") {
                uri.getQueryParameter("code")?.let { code ->
                    spotifyAuthCode = code
                }
            }
        }
    }
}
