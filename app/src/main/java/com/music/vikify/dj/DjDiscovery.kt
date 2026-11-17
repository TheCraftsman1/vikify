package com.music.vikify.dj

import com.music.vikify.data.YtMusicRepository
import com.music.vikify.data.innertube.Innertube
import com.music.vikify.data.innertube.InnertubeParser
import com.music.vikify.data.model.SearchFilter
import com.music.vikify.data.model.SearchResult
import com.music.vikify.data.model.Song
import com.music.vikify.data.personalization.AndroidLocalMixes
import com.music.vikify.data.personalization.TrackPreference
import kotlinx.coroutines.*

data class DjDiscoveryResult(
    val candidates: List<DjCandidate>,
    val familiar: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
)

/** Live music inventory through the app's existing account-aware music APIs. */
class DjDiscovery(private val offlineSongs: suspend () -> List<Song> = { emptyList() }) {
    private var familiar = emptySet<String>()
    private var hidden = emptySet<String>()
    private val recentSeeds = ArrayDeque<Song>()

    suspend fun opening(current: Song?, request: String? = null, preferredArtists: List<String> = emptyList()): DjDiscoveryResult = coroutineScope {
        val offline = async { safe { offlineSongs() } }
        val history = async { safe { YtMusicRepository.recents().getOrThrow().take(30) } }
        val recommendations = async { safe { YtMusicRepository.quickPicks().getOrThrow().take(60) } }
        val evidence = async {
            try { AndroidLocalMixes.evidenceStore.snapshot(AndroidLocalMixes.scope()) }
            catch (c: CancellationException) { throw c }
            catch (_: Exception) { null }
        }
        val likes = async {
            if (Innertube.cookie == null) emptyList() else safe {
                YtMusicRepository.browseSongs("FEmusic_liked_videos").getOrThrow().songs.take(40)
            }
        }
        val requested = async { if (request.isNullOrBlank()) emptyList() else search(request) }
        val learned = async { preferredArtists.take(2).map { artist -> async { search(artist).take(8) } }.awaitAll().flatten() }
        val past = history.await()
        val liked = likes.await()
        val learnedSongs = learned.await()
        familiar = (past + liked + learnedSongs + listOfNotNull(current)).map { DjBrain.normalize(it.artist) }.toSet()
        hidden = evidence.await()?.preferences?.filterValues { it == TrackPreference.HIDE }?.keys.orEmpty()
        (past.take(8) + listOfNotNull(current) + liked.take(8)).distinctBy { it.videoId }.forEach { recentSeeds.addLast(it) }
        // Related tracks belong in the opening too, alongside actual YouTube Music recents.
        val connections = async {
            (past.take(3) + listOfNotNull(current) + liked.take(2)).distinctBy { DjBrain.normalize(it.artist) }.take(2)
                .map { seed -> async { related(seed) } }.awaitAll().flatten().distinctBy { it.videoId }
        }
        val local = offline.await()
        val initial = candidates(learnedSongs, DjOrigin.FOR_YOU) + candidates(recommendations.await(), DjOrigin.FOR_YOU) + candidates(liked, DjOrigin.LIKED) +
            candidates(past, DjOrigin.HISTORY) + candidates(requested.await(), DjOrigin.REQUEST)
        val localCandidates = offlineCandidates(local, request)
        // Downloads are already playable; avoid another round of failed discovery before using them.
        val fallback = if (initial.size < 12 && localCandidates.isEmpty()) discover() else emptyList()
        val connected = connections.await()
        familiar = familiar + local.map { DjBrain.normalize(it.artist) }
        DjDiscoveryResult((candidates(connected.take(40), DjOrigin.RADIO) + initial + localCandidates + candidates(fallback, DjOrigin.DISCOVERY))
            .take(180), familiar, hidden)
    }

    suspend fun next(anchor: Song?, chapter: Int, request: String?): DjDiscoveryResult = coroutineScope {
        val actualSeed = anchor ?: recentSeeds.firstOrNull()
        val related = async { actualSeed?.let { related(it) }.orEmpty() }
        val fresh = async { if (chapter % 3 == 2) discover() else safe { YtMusicRepository.quickPicks().getOrThrow().take(50) } }
        val requested = async { if (request.isNullOrBlank()) emptyList() else search(request) }
        val offline = async { safe { offlineSongs() } }
        // Rotate a second observed artist into the pool; don't stay in one station forever.
        val otherSeed = recentSeeds.firstOrNull { it.artist != actualSeed?.artist }
        val other = async { otherSeed?.let { related(it) }.orEmpty() }
        DjDiscoveryResult(candidates(requested.await(), DjOrigin.REQUEST) + candidates(related.await(), DjOrigin.RADIO) +
            candidates(other.await(), DjOrigin.RADIO) + candidates(fresh.await(), DjOrigin.DISCOVERY) +
            offlineCandidates(offline.await(), request), familiar, hidden)
    }

    fun observe(song: Song, reward: Double) {
        if (reward <= 0) return
        recentSeeds.removeAll { it.videoId == song.videoId }
        recentSeeds.addFirst(song)
        while (recentSeeds.size > 12) recentSeeds.removeLast()
        familiar = familiar + DjBrain.normalize(song.artist)
    }

    suspend fun search(query: String): List<Song> = safe {
        YtMusicRepository.search(query.take(250), SearchFilter.SONGS).getOrThrow()
            .mapNotNull { when (it) { is SearchResult.Track -> it.song; is SearchResult.TopTrack -> it.song; else -> null } }.take(40)
    }

    private suspend fun related(song: Song) = safe { YtMusicRepository.radio(song.videoId).getOrThrow().filter { it.videoId != song.videoId }.take(50) }

    private suspend fun discover(): List<Song> {
        val shelves = safe { YtMusicRepository.homeSupplement("FEmusic_charts").getOrThrow() }
        val direct = shelves.flatMap { it.items }.mapNotNull { item -> item.videoId?.let {
            Song(it, item.title, InnertubeParser.artistFromSubtitle(item.subtitle), item.thumbnailUrl)
        } }
        if (direct.size >= 10) return direct.take(50)
        // The region comes from the app's music locale, not a hard-coded country/artist list.
        val playlists = shelves.flatMap { it.items }.mapNotNull { it.browseId }.filter { it.startsWith("VL") }.distinct().take(2)
        val chartSongs = coroutineScope { playlists.map { id -> async { safe { YtMusicRepository.browseSongs(id).getOrThrow().songs.take(25) } } }.awaitAll().flatten() }
        if (chartSongs.isNotEmpty()) return direct + chartSongs
        return direct + search("${Innertube.currentRegion} popular songs")
    }

    private fun candidates(songs: List<Song>, origin: DjOrigin) = songs.mapIndexed { index, song -> DjCandidate(song, origin, index) }
    private suspend fun <T> safe(action: suspend () -> List<T>): List<T> = try {
        withTimeoutOrNull(8_000) { withContext(Dispatchers.IO) { action() } }.orEmpty()
    } catch (c: CancellationException) { throw c } catch (_: Exception) { emptyList() }
}

/** Explicit requests may use matching downloads, never silently substitute an unrelated offline mix. */
internal fun offlineCandidates(songs: List<Song>, request: String?): List<DjCandidate> {
    val words = request?.let(DjBrain::normalize)?.split(' ')?.filter { it.isNotBlank() }.orEmpty()
    return songs.asSequence().filter { !it.localUri.isNullOrBlank() }.filter { song ->
        words.isEmpty() || words.all { word -> DjBrain.normalize("${song.title} ${song.artist} ${song.albumName.orEmpty()}").contains(word) }
    }.distinctBy { it.videoId }.take(60).mapIndexed { index, song ->
        DjCandidate(song, if (words.isEmpty()) DjOrigin.DOWNLOAD else DjOrigin.REQUEST, index)
    }.toList()
}
