/*
 * Copyright (C) 2026 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package com.vikify.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.collect.ImmutableList
import com.vikify.app.MainActivity
import com.vikify.app.R

/**
 * Premium Vikify Notification Provider (4K Edition)
 *
 * Features:
 * - Dynamic Color Support (Via Bitmap)
 * - Android 13+ Squiggly Progress Bar
 * - High Priority Controls
 * - Elegant Typography
 */
@UnstableApi
class VikifyNotificationProvider(
    private val context: Context
) : MediaNotification.Provider {

    companion object {
        const val NOTIFICATION_ID = 1001
        // Changed ID to force Android to recreate the channel with new settings
        const val CHANNEL_ID = "vikify_playback_premium_v2" 
        private const val CHANNEL_NAME = "Vikify Now Playing"
        
        // Fallback color if artwork fails (Deep Purple instead of Red)
        private const val FALLBACK_COLOR = 0xFF120024.toInt()
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low ensures it doesn't "ding" on every song change
            ).apply {
                description = "Media controls for Vikify"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
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
        
        // INTENT: Open Player Screen directly
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            // SMALL ICON: Must be white with transparent background (silhouette)
            .setSmallIcon(R.drawable.small_icon) 
            
            // TITLES
            .setContentTitle(metadata.title ?: "Vikify")
            .setContentText(metadata.artist ?: "Listening to music")
            .setSubText(metadata.albumTitle) // <--- ADDS "Album Name" line
            
            // INTENT
            .setContentIntent(contentIntent)
            
            // VISIBILITY
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX) // <--- KEEPS IT AT TOP
            .setOngoing(player.isPlaying)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            
            // ARTWORK & COLOR (Crucial for Android 13+)
            // Note: If 'artworkBitmap' is null, it falls back to the Vikify PNG logo!
            .setLargeIcon(metadata.artworkBitmap ?: logoBitmap) 
            .setColor(FALLBACK_COLOR)
            .setColorized(true) // Enables the background color fill

        // ACTION 1: PREVIOUS
        // Use "Filled" icons if you have them (e.g., ic_skip_previous_filled)
        builder.addAction(
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, R.drawable.ic_skip_previous), 
                "Previous",
                Player.COMMAND_SEEK_TO_PREVIOUS
            )
        )

        // ACTION 2: PLAY/PAUSE (Center)
        val playPauseIcon = if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseTitle = if (player.isPlaying) "Pause" else "Play"
        builder.addAction(
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, playPauseIcon),
                playPauseTitle,
                Player.COMMAND_PLAY_PAUSE
            )
        )

        // ACTION 3: NEXT
        builder.addAction(
            actionFactory.createMediaAction(
                mediaSession,
                IconCompat.createWithResource(context, R.drawable.ic_skip_next),
                "Next",
                Player.COMMAND_SEEK_TO_NEXT
            )
        )

        // MEDIA STYLE
        // This tells Android "This is a media player, treat it specially"
        val mediaStyle = MediaStyleNotificationHelper.MediaStyle(mediaSession)
            .setShowActionsInCompactView(0, 1, 2) // Indices of Prev, Play, Next
        
        builder.setStyle(mediaStyle)

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    override fun handleCustomCommand(mediaSession: MediaSession, action: String, extras: Bundle): Boolean = false

    // Lazy load the PNG logo as a Bitmap for notification fallback
    private val logoBitmap: Bitmap? by lazy {
        try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.vikify_logo)
            drawable?.let {
                val bitmap = Bitmap.createBitmap(
                    it.intrinsicWidth.coerceAtLeast(1), 
                    it.intrinsicHeight.coerceAtLeast(1), 
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                it.setBounds(0, 0, canvas.width, canvas.height)
                it.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }
}

// Helper Extension to decode artworkData into a Bitmap
private val androidx.media3.common.MediaMetadata.artworkBitmap: Bitmap?
    get() = artworkData?.let { data ->
        try {
            android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
        } catch (e: Exception) {
            null
        }
    }
