package com.dd3boh.lrclib.models

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class Track(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val duration: Double,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)

// Increased tolerance from 2 to 10 seconds - song durations vary across versions
internal fun List<Track>.bestMatchingFor(duration: Int) = firstOrNull { abs(it.duration.toInt() - duration) <= 10 }
