package com.music.vikify.dj

import com.music.vikify.data.model.Song
import com.music.vikify.data.intelligence.MusicSelection
import kotlin.math.min

enum class DjOrigin { FOR_YOU, HISTORY, LIKED, RADIO, DISCOVERY, REQUEST, DOWNLOAD }

data class DjCandidate(val song: Song, val origin: DjOrigin, val rank: Int = 0)

/** Six-track chapters: a personal opening, an artist connection, then discovery.
 * Unknown audio features are never manufactured for live tracks. Provider rank,
 * observed artist preferences, freshness and diversity supply the ranking.
 */
object DjLivePlanner {
    const val CHAPTER_SIZE = 6
    fun chapter(number: Int): String = when (number % 3) {
        1 -> "An artist connection"
        2 -> "A fresh direction"
        else -> "Picked for you"
    }

    fun choose(
        candidates: List<DjCandidate>, taste: DjTaste, committed: Set<String>,
        played: List<String>, familiarArtists: Set<String>, chapter: Int,
        excludedArtists: Set<String> = emptySet(), hidden: Set<String> = emptySet(),
        size: Int = CHAPTER_SIZE,
        explicitRequest: Boolean = false,
        preferredIds: Set<String> = emptySet(),
    ): List<DjCandidate> {
        require(size >= 0)
        // Preserve the strongest provenance when several sources return the same recording.
        val pool = candidates.filter { it.song.videoId.isNotBlank() && it.song.title.isNotBlank() &&
            it.song.videoId !in committed &&
            it.song.videoId !in hidden && !MusicSelection.artistExcluded(it.song.artist, excludedArtists) }
            .groupBy { it.song.videoId }.values.map { group ->
                val ranked = group.maxBy { priority(it.origin, chapter) }
                val downloaded = group.firstOrNull { !it.song.localUri.isNullOrBlank() }?.song
                // Keep recommendation provenance while resolving an available local copy offline.
                if (downloaded == null) ranked else ranked.copy(song = ranked.song.copy(
                    localUri = downloaded.localUri, localPath = downloaded.localPath))
            }.toMutableList()
        val chosen = mutableListOf<DjCandidate>()
        repeat(min(size, pool.size)) { position ->
            fun fresh(c: DjCandidate) = if (explicitRequest) 0 else when (c.song.videoId) { in played -> 2; in taste.recent -> 1; else -> 0 }
            val tier = pool.minOf(::fresh)
            var eligible = pool.filter { fresh(it) == tier }
            if (!explicitRequest && chapter == 0) {
                // Open with a recent favorite, then follow its connections. Recents are seeds,
                // rather than an entire queue replayed in history order.
                val source = when (position) { 0 -> DjOrigin.HISTORY; 1, 2 -> DjOrigin.RADIO; else -> null }
                source?.let { origin -> eligible.filter { it.origin == origin }.takeIf { it.isNotEmpty() }?.let { eligible = it } }
                if (chosen.count { it.origin == DjOrigin.HISTORY } >= 2)
                    eligible.filter { it.origin != DjOrigin.HISTORY }.takeIf { it.isNotEmpty() }?.let { eligible = it }
            }
            val previousArtist = chosen.lastOrNull()?.song?.artist?.let(DjBrain::normalize)
            eligible.filter { DjBrain.normalize(it.song.artist) != previousArtist }.takeIf { it.isNotEmpty() }?.let { eligible = it }
            eligible.filter { c -> chosen.count { DjBrain.normalize(it.song.artist) == DjBrain.normalize(c.song.artist) } < 2 }
                .takeIf { it.isNotEmpty() }?.let { eligible = it }
            // Each chapter makes room for artists outside the listener's familiar set.
            val explore = chapter % 3 == 2 || position == 3 || position == 5
            val best = eligible.maxBy { c ->
                val artist = DjBrain.normalize(c.song.artist)
                val p = taste.global["1:$artist"] ?: DjPrior()
                val preference = p.alpha / (p.alpha + p.beta)
                val novelty = if (artist !in familiarArtists) 1.0 else 0.0
                val artistCount = chosen.count { DjBrain.normalize(it.song.artist) == artist }
                priority(c.origin, chapter) + (if (c.song.videoId in preferredIds) .5 else 0.0) +
                    .40 * preference + .24 / (1 + c.rank / 5.0) - (if (c.song.isVideo) .04 else 0.0) +
                    (if (explore) .32 * novelty else .12 * (1 - novelty)) - .30 * artistCount
            }
            chosen += best
            pool.remove(best)
        }
        return chosen
    }

    private fun priority(origin: DjOrigin, chapter: Int): Double = when (origin) {
        DjOrigin.REQUEST -> 1.6
        DjOrigin.FOR_YOU -> if (chapter % 3 == 0) .75 else .40
        DjOrigin.RADIO -> if (chapter % 3 == 1) .85 else .50
        DjOrigin.DISCOVERY -> if (chapter % 3 == 2) .85 else .43
        DjOrigin.LIKED -> .48
        DjOrigin.HISTORY -> .40
        DjOrigin.DOWNLOAD -> .35
    }
}
