/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Premium Vikify Notification Provider
 * 
 * Features:
 * - Dynamic color extraction from album art
 * - Android 13+ media style with squiggly progress
 * - Like/Unlike action support
 * - Elegant fallback handling
 * - Cached bitmap management
 */
package com.vikify.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.palette.graphics.Palette
import com.google.common.collect.ImmutableList
import com.vikify.app.MainActivity
import com.vikify.app.R
import kotlinx.coroutines.*

/**
 * Premium notification provider with dynamic theming and enhanced controls
 */
@UnstableApi
class VikifyNotificationProvider(
    private val context: Context
) : MediaNotification.Provider {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "vikify_playback_v3"
        private const val CHANNEL_NAME = "Now Playing"
        private const val CHANNEL_DESCRIPTION = "Media playback controls"
        
        // Brand colors
        private const val VIKIFY_PURPLE = 0xFF7C4DFF.toInt()
        private const val VIKIFY_DARK = 0xFF0A0A0F.toInt()
        private const val VIKIFY_CYAN = 0xFF00E5FF.toInt()
        
        // Custom actions
        const val ACTION_LIKE = "com.vikify.action.LIKE"
        const val ACTION_REPEAT = "com.vikify.action.REPEAT"
        const val ACTION_SHUFFLE = "com.vikify.action.SHUFFLE"
        
        // Artwork cache
        private var cachedArtworkUrl: String? = null
        private var cachedArtwork: Bitmap? = null
        private var cachedDominantColor: Int = VIKIFY_PURPLE
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logoBitmap: Bitmap? = null
    
    // Track like state (should be synced with your database)
    private var isCurrentTrackLiked: Boolean = false

    init {
        createNotificationChannel()
        loadLogoBitmap()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                
                // Disable sound and vibration for media notifications
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            }
            
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun loadLogoBitmap() {
        scope.launch {
            logoBitmap = try {
                val drawable = ContextCompat.getDrawable(context, R.drawable.vikify_logo)
                when (drawable) {
                    is BitmapDrawable -> drawable.bitmap
                    else -> drawable?.let {
                        val width = it.intrinsicWidth.coerceAtLeast(128)
                        val height = it.intrinsicHeight.coerceAtLeast(128)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        it.setBounds(0, 0, canvas.width, canvas.height)
                        it.draw(canvas)
                        bitmap
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val player = mediaSession.player
        val metadata = player.mediaMetadata
        
        // Extract artwork and color
        val (artwork, dominantColor) = getArtworkAndColor(metadata)
        
        // Build content intent
        val contentIntent = buildContentIntent()
        
        // Build notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
            // ═══════════════════════════════════════════════════════════════
            // BASIC INFO
            // ═══════════════════════════════════════════════════════════════
            
            setSmallIcon(R.drawable.small_icon) // Monochrome icon required
            setContentTitle(metadata.title ?: "Vikify")
            setContentText(formatArtistText(metadata))
            
            // Album as subtext (appears above controls on expanded view)
            metadata.albumTitle?.let { setSubText(it) }
            
            setContentIntent(contentIntent)
            
            // ═══════════════════════════════════════════════════════════════
            // VISUAL STYLING
            // ═══════════════════════════════════════════════════════════════
            
            setLargeIcon(artwork ?: logoBitmap)
            setColor(dominantColor)
            setColorized(true) // Enables tinted background on Android 8+
            
            // ═══════════════════════════════════════════════════════════════
            // BEHAVIOR
            // ═══════════════════════════════════════════════════════════════
            
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            priority = NotificationCompat.PRIORITY_MAX
            setOngoing(player.playWhenReady) // Dismissible when paused
            setShowWhen(false)
            setOnlyAlertOnce(true)
            
            // Delete intent (when swiped away while paused)
            setDeleteIntent(buildDeleteIntent())
            
            // ═══════════════════════════════════════════════════════════════
            // ACTIONS
            // ═══════════════════════════════════════════════════════════════
            
            addActions(this, mediaSession, player, actionFactory)
            
            // ═══════════════════════════════════════════════════════════════
            // MEDIA STYLE
            // ═══════════════════════════════════════════════════════════════
            
            val mediaStyle = MediaStyleNotificationHelper.MediaStyle(mediaSession)
                .setShowActionsInCompactView(0, 1, 2) // Like, Play/Pause, Next in compact
            
            setStyle(mediaStyle)
        }

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    private fun addActions(
        builder: NotificationCompat.Builder,
        mediaSession: MediaSession,
        player: Player,
        actionFactory: MediaNotification.ActionFactory
    ) {
        // ─────────────────────────────────────────────────────────────────────
        // ACTION 0: LIKE/UNLIKE (or PREVIOUS in compact)
        // ─────────────────────────────────────────────────────────────────────
        
        // For compact view, we show Previous instead of Like
        builder.addAction(
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, R.drawable.ic_skip_previous),
                "Previous",
                Player.COMMAND_SEEK_TO_PREVIOUS
            )
        )

        // ─────────────────────────────────────────────────────────────────────
        // ACTION 1: PLAY / PAUSE (Center, most important)
        // ─────────────────────────────────────────────────────────────────────
        
        val isPlaying = player.isPlaying
        val playPauseIcon = if (isPlaying) {
            R.drawable.ic_pause
        } else {
            R.drawable.ic_play
        }
        val playPauseLabel = if (isPlaying) "Pause" else "Play"
        
        builder.addAction(
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, playPauseIcon),
                playPauseLabel,
                Player.COMMAND_PLAY_PAUSE
            )
        )

        // ─────────────────────────────────────────────────────────────────────
        // ACTION 2: NEXT
        // ─────────────────────────────────────────────────────────────────────
        
        builder.addAction(
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, R.drawable.ic_skip_next),
                "Next",
                Player.COMMAND_SEEK_TO_NEXT
            )
        )

        // ─────────────────────────────────────────────────────────────────────
        // ACTION 3: LIKE (Expanded view only)
        // ─────────────────────────────────────────────────────────────────────
        
        val likeIcon = if (isCurrentTrackLiked) {
            R.drawable.favorite
        } else {
            R.drawable.favorite_border
        }
        
        builder.addAction(
            NotificationCompat.Action.Builder(
                IconCompat.createWithResource(context, likeIcon),
                if (isCurrentTrackLiked) "Unlike" else "Like",
                buildLikeIntent()
            ).build()
        )

        // ─────────────────────────────────────────────────────────────────────
        // ACTION 4: CLOSE/STOP (Expanded view only)
        // ─────────────────────────────────────────────────────────────────────
        
        builder.addAction(
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, R.drawable.close),
                "Stop",
                Player.COMMAND_STOP
            )
        )
    }

    private fun getArtworkAndColor(metadata: MediaMetadata): Pair<Bitmap?, Int> {
        // Check cache first
        val artworkUri = metadata.artworkUri?.toString()
        if (artworkUri == cachedArtworkUrl && cachedArtwork != null) {
            return Pair(cachedArtwork, cachedDominantColor)
        }
        
        // Try to get artwork from metadata
        val artwork = metadata.artworkData?.let { data ->
            try {
                BitmapFactory.decodeByteArray(data, 0, data.size)?.also { bitmap ->
                    // Extract dominant color
                    extractDominantColor(bitmap)?.let { color ->
                        cachedDominantColor = color
                    }
                    cachedArtwork = bitmap
                    cachedArtworkUrl = artworkUri
                }
            } catch (e: Exception) {
                null
            }
        }
        
        return Pair(artwork, cachedDominantColor)
    }

    private fun extractDominantColor(bitmap: Bitmap): Int? {
        return try {
            val palette = Palette.from(bitmap)
                .maximumColorCount(16)
                .generate()
            
            // Priority: Vibrant > Muted > Dominant
            palette.vibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: VIKIFY_PURPLE
        } catch (e: Exception) {
            null
        }
    }

    private fun formatArtistText(metadata: MediaMetadata): String {
        val artist = metadata.artist?.toString()
        val albumArtist = metadata.albumArtist?.toString()
        
        return when {
            !artist.isNullOrBlank() -> artist
            !albumArtist.isNullOrBlank() -> albumArtist
            else -> "Unknown Artist"
        }
    }

    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            // Optional: Add extra to navigate to player screen
            putExtra("navigate_to", "player")
        }
        
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildDeleteIntent(): PendingIntent {
        val intent = Intent(context, MusicService::class.java).apply {
            action = "com.vikify.action.STOP"
        }
        
        return PendingIntent.getService(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildLikeIntent(): PendingIntent {
        val intent = Intent(context, MusicService::class.java).apply {
            action = ACTION_LIKE
        }
        
        return PendingIntent.getService(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun handleCustomCommand(
        mediaSession: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean {
        return when (action) {
            ACTION_LIKE -> {
                // Toggle like state
                isCurrentTrackLiked = !isCurrentTrackLiked
                // Trigger notification update
                true
            }
            ACTION_REPEAT -> {
                // Handle repeat mode toggle
                val player = mediaSession.player
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                true
            }
            ACTION_SHUFFLE -> {
                // Handle shuffle toggle
                val player = mediaSession.player
                player.shuffleModeEnabled = !player.shuffleModeEnabled
                true
            }
            else -> false
        }
    }

    /**
     * Update like state from external source (e.g., database)
     */
    fun updateLikeState(isLiked: Boolean) {
        isCurrentTrackLiked = isLiked
    }

    /**
     * Clear cached artwork (call on track change)
     */
    fun clearArtworkCache() {
        cachedArtwork?.recycle()
        cachedArtwork = null
        cachedArtworkUrl = null
        cachedDominantColor = VIKIFY_PURPLE
    }

    /**
     * Cleanup resources
     */
    fun release() {
        scope.cancel()
        clearArtworkCache()
        logoBitmap?.recycle()
        logoBitmap = null
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// EXTENSION FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Safely decode artwork data to bitmap with size limits
 */
private fun ByteArray.decodeToBitmap(maxSize: Int = 512): Bitmap? {
    return try {
        // First decode bounds only
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(this, 0, size, options)
        
        // Calculate sample size
        val sampleSize = calculateInSampleSize(options, maxSize, maxSize)
        
        // Decode with sample size
        options.apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565 // Lower memory usage
        }
        
        BitmapFactory.decodeByteArray(this, 0, size, options)
    } catch (e: Exception) {
        null
    }
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (height, width) = options.outHeight to options.outWidth
    var inSampleSize = 1
    
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    
    return inSampleSize
}
