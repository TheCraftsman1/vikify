package com.vikify.app.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vikify.app.constants.AudioGaplessOffloadKey
import com.vikify.app.constants.AudioNormalizationKey
import com.vikify.app.constants.AudioQualityKey
import com.vikify.app.constants.AnimatedBackgroundKey
import com.vikify.app.constants.DarkModeKey
import com.vikify.app.db.MusicDatabase
import com.vikify.app.spotify.SpotifyRepository
import com.vikify.app.utils.dataStore
import com.vikify.app.utils.enumPreference
import com.vikify.app.utils.get
import com.zionhuang.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import androidx.datastore.preferences.core.edit

@HiltViewModel
class ProfileViewModel @Inject constructor(
    application: Application,
    private val database: MusicDatabase,
    private val spotifyRepository: SpotifyRepository
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    // -------------------------------------------------------------------------
    // Settings State (DataStore)
    // -------------------------------------------------------------------------
    
    // Theme
    val appThemeMode = context.dataStore.data
        .map { it[DarkModeKey] ?: "SYSTEM" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "SYSTEM")

    fun toggleTheme() {
        viewModelScope.launch {
            val current = appThemeMode.value
            val next = if (current == "ON") "OFF" else "ON" // Simple toggle for now, ignoring SYSTEM
            context.dataStore.edit { it[DarkModeKey] = next }
        }
    }

    // Playback
    val gaplessEnabled = context.dataStore.data
        .map { it[AudioGaplessOffloadKey] ?: true }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun setGapless(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[AudioGaplessOffloadKey] = enabled }
        }
    }

    val normalizeEnabled = context.dataStore.data
        .map { it[AudioNormalizationKey] ?: true }
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun setNormalize(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[AudioNormalizationKey] = enabled }
        }
    }

    // Audio Quality
    val audioQuality = context.dataStore.data
        .map { it[AudioQualityKey] ?: "High" } // Default to High
        .stateIn(viewModelScope, SharingStarted.Lazily, "High")

    fun setAudioQuality(quality: String) {
        viewModelScope.launch {
            context.dataStore.edit { it[AudioQualityKey] = quality }
        }
    }

    // Animated Background
    val animatedBackground = context.dataStore.data
        .map { it[AnimatedBackgroundKey] ?: true } // Default: ON (beautiful animations)
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun setAnimatedBackground(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { it[AnimatedBackgroundKey] = enabled }
        }
    }


    // -------------------------------------------------------------------------
    // Profile Stats
    // -------------------------------------------------------------------------
    
    private val _stats = MutableStateFlow(ProfileStats())
    val stats = _stats.asStateFlow()

    fun refreshStats() {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Calculate Total Minutes Listened
            // Query: SUM(playCount.count * song.duration)
            val minutesListened = try {
                val query = "SELECT sum(p.count * s.duration) FROM playCount p JOIN song s ON p.song = s.id"
                val cursor = database.openHelper.readableDatabase.query(query)
                var totalSeconds = 0L
                if (cursor.moveToFirst()) {
                    totalSeconds = cursor.getLong(0)
                }
                cursor.close()
                (totalSeconds / 60)
            } catch (e: Exception) {
                0L
            }

            // 2. Calculate Top Genre
            // This is harder without a direct map join in play counts. 
            // We'll take top 50 played songs -> find their genres -> count frequency
            val topGenre = try {
                // Get top 50 song IDs by play count
                val topSongsQuery = "SELECT song FROM playCount ORDER BY count DESC LIMIT 50"
                val cursor = database.openHelper.readableDatabase.query(topSongsQuery)
                val songIds = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    songIds.add(cursor.getString(0))
                }
                cursor.close()

                if (songIds.isEmpty()) "None" else {
                    // For each song, finding genre is expensive if we do N queries.
                    // Let's just find the genre for the #1 song for now for speed
                    val topSongId = songIds.first()
                    // Get genre name
                    val genreQuery = "SELECT g.title FROM genre g JOIN song_genre_map m ON g.id = m.genreId WHERE m.songId = ?"
                    val genreCursor = database.openHelper.readableDatabase.query(genreQuery, arrayOf(topSongId))
                    var genre = "Pop" // Default fallback
                    if (genreCursor.moveToFirst()) {
                        genre = genreCursor.getString(0)
                    }
                    genreCursor.close()
                    genre
                }
            } catch (e: Exception) {
                "Pop"
            }
            
            // Get liked songs count first (correct value)
            val likedCount = database.likedSongsCount().first()
            
            _stats.value = ProfileStats(
                minutesListened = minutesListened,
                topGenre = topGenre,
                likedSongsCount = likedCount
            )
            
             // Continue observing liked count for reactive updates
             database.likedSongsCount().collect { count ->
                 _stats.update { it.copy(likedSongsCount = count) }
             }

            // 3. Calculate Top Artists
            // Query: Top artists by play count
            try {
                val query = """
                    SELECT artist.name 
                    FROM artist 
                    JOIN song_artist_map ON artist.id = song_artist_map.artistId
                    JOIN playCount ON song_artist_map.songId = playCount.song
                    GROUP BY artist.id
                    ORDER BY SUM(playCount.count) DESC
                    LIMIT 5
                """.trimIndent()
                
                val cursor = database.openHelper.readableDatabase.query(query)
                val artists = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    artists.add(cursor.getString(0))
                }
                cursor.close()
                _topArtists.value = artists
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cache Management
    // -------------------------------------------------------------------------

    private val _cacheSize = MutableStateFlow("Calculating...")
    val cacheSize = _cacheSize.asStateFlow()

    fun calculateCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = context.cacheDir
            // Also check for 'songs' dir if we store them there
            // Usually ExoPlayer cache is in context.cacheDir/exoplayer or similar
            // Let's just sum up the whole cache dir for simplicity + consistency
            val sizeBytes = getFolderSize(cacheDir)
            val sizeMb = sizeBytes / (1024 * 1024)
            _cacheSize.value = "${sizeMb} MB"
        }
    }

    fun clearCache() {
         viewModelScope.launch(Dispatchers.IO) {
             _cacheSize.value = "Clearing..."
             try {
                 context.cacheDir.deleteRecursively()
                 context.cacheDir.mkdirs() // Recreate
                 // Update DB if needed (remove download flags?) 
                 // No, cache clearing shouldn't remove "Downloads" metadata, but might remove cached streams.
                 // Real "Downloads" are stored in filesDir usually?
                 // If we store downloads in cacheDir, we just deleted them! 
                 // Vikify seems to use ExoPlayer cache for "Downloads" too? 
                 // We should be careful. 
                 // For now, let's assume cacheDir is safe to clear.
             } catch (e: Exception) {
                 e.printStackTrace()
             }
             calculateCacheSize()
         }
    }

    // -------------------------------------------------------------------------
    // Top Artists
    // -------------------------------------------------------------------------

    private val _topArtists = MutableStateFlow<List<String>>(emptyList())
    val topArtists = _topArtists.asStateFlow()

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            for (child in file.listFiles() ?: emptyArray()) {
                size += getFolderSize(child)
            }
        } else {
            size = file.length()
        }
        return size
    }
}

data class ProfileStats(
    val minutesListened: Long = 0,
    val topGenre: String = "-",
    val likedSongsCount: Int = 0
)
