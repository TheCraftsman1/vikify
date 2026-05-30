package com.vikify.app.core.utils

/**
 * Unified Media ID type detection
 * Prevents confusion between Spotify IDs, YouTube IDs, and local file IDs
 */
enum class MediaIdType {
    YOUTUBE,      // 11-char alphanumeric (e.g., "dQw4w9WgXcQ")
    SPOTIFY,      // 22-char base62 (e.g., "4uLU6hMCjMI75M1A2tKUQC")
    LOCAL,        // Starts with "LS" prefix
    UNRESOLVED,   // Starts with "UNRESOLVED_" prefix
    UNKNOWN       // Fallback
}

/**
 * Extension function to detect the type of a media ID
 */
fun String.getMediaIdType(): MediaIdType = when {
    this.startsWith("UNRESOLVED_") -> MediaIdType.UNRESOLVED
    this.startsWith("LS") -> MediaIdType.LOCAL
    // YouTube IDs: 11 chars, alphanumeric + _ and -
    this.length == 11 && this.all { it.isLetterOrDigit() || it == '_' || it == '-' } -> MediaIdType.YOUTUBE
    // Spotify IDs: 22 chars, alphanumeric
    this.length == 22 && this.all { it.isLetterOrDigit() } -> MediaIdType.SPOTIFY
    // Also check for 10-12 char range for some YouTube edge cases
    this.length in 10..12 && this.all { it.isLetterOrDigit() || it == '_' || it == '-' } &&
        !this.startsWith("LS") && !this.startsWith("UN") -> MediaIdType.YOUTUBE
    else -> MediaIdType.UNKNOWN
}

/**
 * Check if this ID needs stream resolution
 */
fun String.needsStreamResolution(): Boolean {
    val type = this.getMediaIdType()
    return type == MediaIdType.UNRESOLVED || 
           type == MediaIdType.SPOTIFY || 
           type == MediaIdType.UNKNOWN
}

/**
 * Check if this is a valid YouTube video ID
 */
fun String.isValidYouTubeId(): Boolean {
    return this.getMediaIdType() == MediaIdType.YOUTUBE
}

/**
 * Extract the original ID from an UNRESOLVED_ prefixed ID
 */
fun String.extractUnresolvedId(): String {
    return if (this.startsWith("UNRESOLVED_")) {
        this.removePrefix("UNRESOLVED_")
    } else {
        this
    }
}
