package com.vikify.app.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val songId: String,
    val title: String,
    val artist: String,
    val genre: String?,
    val durationPlayed: Int, // in seconds
    val timestamp: Long // Epoch millis
)
