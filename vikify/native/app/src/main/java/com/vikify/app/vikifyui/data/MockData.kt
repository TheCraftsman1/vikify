package com.vikify.app.vikifyui.data

import com.vikify.app.R

import androidx.annotation.DrawableRes

/**
 * Mock Data for Vikify UI
 * 
 * All UI renders from this fake data.
 * Backend integration replaces this later.
 */

// Core data models
// Core data models - Moved to separate files
// data class Track(...) 
// data class Album(...)
// data class Playlist(...)
// data class PlayerUIState(...)

// Sample data
object MockData {
    
    val sampleTracks = listOf(
        Track("1", "Midnight Dreams", "Luna Wave", null, 180000L, R.drawable.artwork_placeholder),
        Track("2", "Ocean Waves", "Calm Collective", null, 200000L, R.drawable.artwork_placeholder),
        Track("3", "Starlight", "Nova", null, 210000L, R.drawable.artwork_placeholder),
        Track("4", "Golden Hour", "Sunset Beats", null, 190000L, R.drawable.artwork_placeholder),
        Track("5", "Morning Mist", "Ambient Flow", null, 185000L, R.drawable.artwork_placeholder),
        Track("6", "Electric Pulse", "Synth Masters", null, 220000L, R.drawable.artwork_placeholder),
        Track("7", "Velvet Sky", "Dream Weavers", null, 195000L, R.drawable.artwork_placeholder),
        Track("8", "Crystal Clear", "Pure Tones", null, 175000L, R.drawable.artwork_placeholder),
        Track("9", "Autumn Leaves", "Nature Sounds", null, 205000L, R.drawable.artwork_placeholder),
        Track("10", "Urban Nights", "City Vibes", null, 240000L, R.drawable.artwork_placeholder)
    )
    
    val sampleAlbums = listOf(
        Album("a1", "Dreamscape", "Luna Wave", tracks = sampleTracks.take(4)),
        Album("a2", "Oceanic", "Calm Collective", tracks = sampleTracks.drop(2).take(3)),
        Album("a3", "Stargazer", "Nova", tracks = sampleTracks.drop(4).take(3)),
        Album("a4", "Golden Days", "Sunset Beats", tracks = sampleTracks.takeLast(4)),
        Album("a5", "Morning Light", "Ambient Flow"),
        Album("a6", "Electric Dreams", "Synth Masters")
    )
    
    val recentlyPlayed = sampleTracks.take(5)
    
    val samplePlaylists = listOf(
        Playlist("p1", "Liked Songs", trackCount = 127),
        Playlist("p2", "Chill Vibes", trackCount = 45),
        Playlist("p3", "Focus Flow", trackCount = 32),
        Playlist("p4", "Night Drive", trackCount = 28)
    )
    
    val sampleLyrics = """
        |[00:00.00] Midnight dreams, floating by
        |[00:04.50] Stars align in the velvet sky
        |[00:09.20] Close your eyes, let the music flow
        |[00:14.00] Where the night takes us, we'll never know
        |[00:18.50] 
        |[00:19.00] Dancing shadows on the wall
        |[00:23.50] Whispered echoes, hear them call
        |[00:28.20] In this moment, time stands still
        |[00:33.00] Chasing dreams against our will
    """.trimMargin()
    
    // Time-based greeting
    fun getGreeting(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..21 -> "Good evening"
        else -> "Good night"
    }
    
    // Recent searches for Search screen
    val recentSearches = listOf(
        Artist("ar1", "The Weeknd"),
        Artist("ar2", "Lo-Fi Beats"),
        Artist("ar3", "Pink Floyd"),
        Artist("ar4", "Taylor Swift")
    )
    
    // Browse categories
    val browseCategories = listOf(
        Category("c1", "Pop", 0xFFFF6B6B, isPriority = true),           // Louder
        Category("c2", "Hip-Hop", 0xFF4D96FF, isPriority = true),       // Louder
        Category("c3", "Indie", 0xFF6BCB77),
        Category("c4", "Electronic", 0xFF9D4EDD, isNew = true),         // Trending
        Category("c5", "Podcasts", 0xFFFF9F43),
        Category("c6", "New Releases", 0xFFE74C3C, isNew = true),       // Trending
        Category("c7", "Charts", 0xFF34495E, isPriority = true),        // Louder
        Category("c8", "Moods", 0xFF1ABC9C),
        Category("c9", "Rock", 0xFF607D8B),
        Category("c10", "R&B", 0xFF9C27B0, isNew = true)
    )

    val trendingSearches = listOf(
        "Taylor Swift",
        "The Weeknd",
        "Bad Bunny",
        "Drake",
        "Dua Lipa",
        "BTS",
        "Ed Sheeran",
        "Billie Eilish",
        "Post Malone",
        "Ariana Grande"
    )
}

// Artist model for recent searches
data class Artist(
    val id: String,
    val name: String,
    @DrawableRes val artwork: Int = R.drawable.artwork_placeholder,
    val remoteArtworkUrl: String? = null
)

// Category model for Browse All
data class Category(
    val id: String,
    val name: String,
    val color: Long,
    val isPriority: Boolean = false,  // "Louder" genres get bigger cards
    val isNew: Boolean = false         // Trending/new indicator
)


