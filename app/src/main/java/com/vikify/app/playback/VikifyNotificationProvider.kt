/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package com.vikify.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
 * Ultra-Minimal Vikify Notification Provider
 * 
 * Clean, premium notification with:
 * - Vikify brand accent color
 * - Only Previous, Play/Pause, Next buttons
 * - NO shuffle, repeat, or seek controls
 * - Minimal, distraction-free design
 */
@UnstableApi
class VikifyNotificationProvider(
    private val context: Context
) : MediaNotification.Provider {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "vikify_playback"
        private const val CHANNEL_NAME = "Vikify Playback"
        
        // Vikify brand color (coral red)
        private const val VIKIFY_ACCENT = 0xFFE53935.toInt()
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
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
        
        // Create intent to open app when notification is tapped
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(metadata.title ?: "Vikify")
            .setContentText(metadata.artist ?: "")
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(player.isPlaying)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setColor(VIKIFY_ACCENT) // Brand color accent
            .setColorized(true) // Use color for notification background on some devices

        // Add Previous action
        val prevAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(context, R.drawable.ic_skip_previous),
            "Previous",
            Player.COMMAND_SEEK_TO_PREVIOUS
        )
        builder.addAction(prevAction)

        // Add Play/Pause action
        val playPauseIcon = if (player.isPlaying) {
            R.drawable.ic_pause
        } else {
            R.drawable.ic_play
        }
        val playPauseAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(context, playPauseIcon),
            if (player.isPlaying) "Pause" else "Play",
            Player.COMMAND_PLAY_PAUSE
        )
        builder.addAction(playPauseAction)

        // Add Next action
        val nextAction = actionFactory.createMediaAction(
            mediaSession,
            IconCompat.createWithResource(context, R.drawable.ic_skip_next),
            "Next",
            Player.COMMAND_SEEK_TO_NEXT
        )
        builder.addAction(nextAction)

        // Apply MediaStyle - only show 3 actions in compact view
        // This creates the clean, minimal notification
        val mediaStyle = MediaStyleNotificationHelper.MediaStyle(mediaSession)
            .setShowActionsInCompactView(0, 1, 2) // Only Previous, Play/Pause, Next
        
        builder.setStyle(mediaStyle)

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean {
        return false
    }
}
