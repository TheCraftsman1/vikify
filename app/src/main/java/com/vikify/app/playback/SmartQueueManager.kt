package com.vikify.app.playback

import com.vikify.app.models.MediaMetadata
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs
import java.util.Calendar

/**
 * Smart Queue Manager - Spotify-style Autoplay Engine
 * 
 * Uses content-based filtering to generate infinite "Compatible" tracks.
 * Creates smooth transitions by matching Genre, Energy, and Tempo.
 */
class SmartQueueManager {
    
    // History of recently played track IDs (anti-repeat) - Thread-safe for concurrent access
    private val playHistory = CopyOnWriteArrayList<String>()
    private val maxHistorySize = 50
    
    /**
     * Add a track to play history for anti-repeat logic
     */
    fun addToHistory(trackId: String) {
        playHistory.add(0, trackId) // Add to front
        if (playHistory.size > maxHistorySize) {
            playHistory.removeAt(playHistory.lastIndex)
        }
    }
    
    /**
     * Get next compatible tracks based on current track's "DNA"
     * 
     * Algorithm:
     * 1. Genre Lock - Match primary genre
     * 2. Energy Flow - Within ±0.2 of current
     * 3. Anti-Repeat - Exclude last 50 songs
     * 4. Rank by BPM/Energy proximity
     */
    fun getNextCompatibleTracks(
        currentTrack: MediaMetadata,
        allTracks: List<MediaMetadata>,
        count: Int = 3
    ): List<MediaMetadata> {
        val currentDNA = generateSongDNA(currentTrack)
        
        return allTracks
            // Filter 1: Exclude current track
            .filter { it.id != currentTrack.id }
            // Filter 2: Exclude recently played
            .filter { it.id !in playHistory }
            // Filter 3: Genre match (same or related)
            .filter { track ->
                val trackDNA = generateSongDNA(track)
                trackDNA.primaryGenre == currentDNA.primaryGenre || 
                isRelatedGenre(currentDNA.primaryGenre, trackDNA.primaryGenre)
            }
            // Filter 4: Energy flow (within ±0.25)
            .filter { track ->
                val trackDNA = generateSongDNA(track)
                abs(trackDNA.energyLevel - currentDNA.energyLevel) <= 0.25f
            }
            // Rank by weighted distance (BPM + Energy)
            .sortedBy { track ->
                val trackDNA = generateSongDNA(track)
                calculateCompatibilityScore(currentDNA, trackDNA)
            }
            .take(count)
    }
    
    /**
     * Get recommendations using "Seed Track" scoring algorithm
     * 
     * Uses the scoring method from the prompt:
     * - Same Genre: +50 points
     * - Same Artist: +30 points
     * - BPM within ±15: +20 points
     * - Energy within ±0.25: +15 points
     * 
     * Returns top 20 tracks, shuffled for variety
     */
    fun getRecommendations(
        seedTrack: MediaMetadata,
        allTracks: List<MediaMetadata>,
        count: Int = 20
    ): List<MediaMetadata> {
        val seedDNA = generateSongDNA(seedTrack)
        val seedArtist = seedTrack.artists.firstOrNull()?.name?.lowercase() ?: ""
        
        return allTracks
            // Exclude seed track itself
            .filter { it.id != seedTrack.id }
            // Exclude recently played (anti-repeat)
            .filter { it.id !in playHistory }
            // Score and sort
            .map { track ->
                val trackDNA = generateSongDNA(track)
                val trackArtist = track.artists.firstOrNull()?.name?.lowercase() ?: ""
                
                var score = 0
                
                // Genre match: +50 points
                if (trackDNA.primaryGenre == seedDNA.primaryGenre) score += 50
                else if (isRelatedGenre(seedDNA.primaryGenre, trackDNA.primaryGenre)) score += 25
                
                // Artist match: +30 points
                if (trackArtist.isNotEmpty() && trackArtist == seedArtist) score += 30
                
                // BPM match: +20 points (within ±15 BPM)
                if (abs(trackDNA.tempo - seedDNA.tempo) <= 15) score += 20
                
                // Energy match: +15 points (within ±0.25)
                if (abs(trackDNA.energyLevel - seedDNA.energyLevel) <= 0.25f) score += 15
                
                track to score
            }
            // Sort by score descending
            .sortedByDescending { it.second }
            // Take top candidates
            .take(50)
            // Shuffle for variety (prevents "same order every time")
            .shuffled()
            // Return requested count
            .take(count)
            .map { it.first }
    }
    
    /**
     * Calculate compatibility score (lower = more compatible)
     * Weighted Euclidean distance on BPM and Energy
     */
    private fun calculateCompatibilityScore(current: SongDNA, candidate: SongDNA): Float {
        val tempoWeight = 0.4f
        val energyWeight = 0.6f
        
        // Normalize tempo difference (max 60 BPM diff = 1.0)
        val tempoDiff = abs(current.tempo - candidate.tempo) / 60f
        
        // Energy difference already 0-1
        val energyDiff = abs(current.energyLevel - candidate.energyLevel)
        
        return (tempoDiff * tempoWeight) + (energyDiff * energyWeight)
    }
    
    /**
     * Check if two genres are related (allows cross-genre discovery)
     */
    private fun isRelatedGenre(genre1: String, genre2: String): Boolean {
        val relatedGenres = mapOf(
            "Pop" to listOf("Dance", "R&B", "Electronic"),
            "Rock" to listOf("Alternative", "Metal", "Indie"),
            "Hip-Hop" to listOf("R&B", "Trap", "Pop"),
            "Electronic" to listOf("Dance", "Ambient", "Pop"),
            "R&B" to listOf("Soul", "Pop", "Hip-Hop"),
            "Indie" to listOf("Alternative", "Folk", "Rock"),
            "Classical" to listOf("Ambient", "Jazz", "Instrumental"),
            "Jazz" to listOf("Blues", "Soul", "Classical"),
            "Ambient" to listOf("Classical", "Electronic", "Chill"),
            "Country" to listOf("Folk", "Rock", "Americana")
        )
        
        return relatedGenres[genre1]?.contains(genre2) == true ||
               relatedGenres[genre2]?.contains(genre1) == true
    }
    
    /**
     * Clear play history (e.g., on new session)
     */
    fun clearHistory() {
        playHistory.clear()
    }
    
    companion object {
        // Singleton instance
        private var instance: SmartQueueManager? = null
        
        fun getInstance(): SmartQueueManager {
            if (instance == null) {
                instance = SmartQueueManager()
            }
            return instance!!
        }
    }
}

/**
 * Song DNA - The "fingerprint" of a track
 * 
 * Used for content-based filtering to find compatible tracks.
 * If real audio analysis isn't available, this is generated
 * deterministically from the song title/artist hash.
 */
data class SongDNA(
    val primaryGenre: String,  // Pop, Rock, Hip-Hop, etc.
    val energyLevel: Float,    // 0.0 (Acoustic/Slow) to 1.0 (EDM/Intense)
    val tempo: Int             // BPM (60-180)
)

/**
 * Generate SongDNA from track metadata
 * 
 * Uses deterministic hash-based generation when real data isn't available.
 * This ensures consistent DNA for the same song across sessions.
 */
fun generateSongDNA(track: MediaMetadata): SongDNA {
    // Use hash of title + artist for deterministic generation
    val hash = (track.title + (track.artists.firstOrNull()?.name ?: "")).hashCode()
    
    // Genre detection from title/artist keywords
    val genre = detectGenre(track.title, track.artists.firstOrNull()?.name ?: "")
    
    // Energy level (0.0 - 1.0) - deterministic from hash
    val energyLevel = ((hash and 0xFF) / 255f * 0.6f) + 0.2f  // Range: 0.2-0.8
    
    // Tempo (BPM) - deterministic from hash
    val tempo = 80 + ((hash.ushr(8) and 0xFF) % 80)  // Range: 80-160 BPM
    
    return SongDNA(
        primaryGenre = genre,
        energyLevel = energyLevel.coerceIn(0f, 1f),
        tempo = tempo
    )
}

/**
 * Detect genre from title and artist name using keywords
 */
private fun detectGenre(title: String, artist: String): String {
    val combined = (title + " " + artist).lowercase()
    
    return when {
        // Electronic indicators
        combined.contains("remix") || combined.contains("edm") || 
        combined.contains("beat") || combined.contains("electronic") -> "Electronic"
        
        // Hip-Hop indicators
        combined.contains("rap") || combined.contains("trap") ||
        combined.contains("hip") || combined.contains("feat.") -> "Hip-Hop"
        
        // Rock indicators
        combined.contains("rock") || combined.contains("metal") ||
        combined.contains("guitar") -> "Rock"
        
        // R&B/Soul indicators
        combined.contains("soul") || combined.contains("r&b") ||
        combined.contains("slow") -> "R&B"
        
        // Classical/Ambient
        combined.contains("classical") || combined.contains("ambient") ||
        combined.contains("piano") || combined.contains("orchestra") -> "Classical"
        
        // Country
        combined.contains("country") || combined.contains("folk") -> "Country"
        
        // Jazz
        combined.contains("jazz") || combined.contains("blues") -> "Jazz"
        
        // Indie
        combined.contains("indie") || combined.contains("alternative") -> "Indie"
        
        // Default to Pop (most common)
        else -> "Pop"
    }
}

/**
 * Time-of-day energy preference
 * 
 * Morning: Higher energy to wake up
 * Afternoon: Medium energy for productivity
 * Evening: Medium-low for winding down
 * Night: Low energy for relaxation
 */
fun getTimeBasedEnergyRange(): Pair<Float, Float> {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    
    return when (hour) {
        in 5..9 -> 0.5f to 0.9f     // Morning: Upbeat
        in 10..14 -> 0.4f to 0.8f   // Late Morning/Lunch: Moderate-High
        in 15..18 -> 0.3f to 0.7f   // Afternoon: Moderate
        in 19..22 -> 0.2f to 0.6f   // Evening: Winding Down
        else -> 0.0f to 0.4f        // Night (10PM-5AM): Ambient/Chill
    }
}

/**
 * Extension to generate SongDNA from Track (UI model)
 */
fun com.vikify.app.vikifyui.data.Track.toSongDNA(): SongDNA {
    val hash = (title + artist).hashCode()
    val genre = detectGenre(title, artist)
    val energyLevel = ((hash and 0xFF) / 255f * 0.6f) + 0.2f
    val tempo = 80 + ((hash.ushr(8) and 0xFF) % 80)
    
    return SongDNA(
        primaryGenre = genre,
        energyLevel = energyLevel.coerceIn(0f, 1f),
        tempo = tempo
    )
}
