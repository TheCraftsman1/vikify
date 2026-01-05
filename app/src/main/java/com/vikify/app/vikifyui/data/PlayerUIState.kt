package com.vikify.app.vikifyui.data

data class PlayerUIState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0, // 0=Off, 1=All, 2=One
    val isLiked: Boolean = false,
    val density: Float = 0f, // 0.0 = Mini, 1.0 = Expanded
    val showQueue: Boolean = false,
    val showLyrics: Boolean = false,
    // === SILENT MIGRATION ===
    val isResolvingTrack: Boolean = false
)
