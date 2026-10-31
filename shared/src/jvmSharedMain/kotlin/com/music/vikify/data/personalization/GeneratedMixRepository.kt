package com.music.vikify.data.personalization

import com.music.vikify.data.DebugLog
import com.music.vikify.data.model.ArtistRef
import com.music.vikify.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

data class GeneratedMix(
    val kind: LocalMixKind,
    val version: Long,
    val generatedAt: Long,
    val songs: List<Song>,
    val feedbackRevision: Long = 0,
    val algorithmVersion: Int = LocalMixGenerator.ALGORITHM_VERSION,
    val confidence: PreferenceConfidence = PreferenceConfidence.LEARNING,
    val basedOnArtists: List<String> = emptyList(),
)

enum class MixFailure { INSUFFICIENT_EVIDENCE, NO_DISCOVERIES, HIDDEN_BY_FEEDBACK, REFRESH_FAILED, SCOPE_CHANGED }
data class MixLoadResult(val mix: GeneratedMix?, val failure: MixFailure? = null)

/** Durable snapshots. Publishing a replacement never touches the player's queue. */
class GeneratedMixRepository(
    private val directory: File,
    private val evidence: suspend () -> List<TrackListeningEvidence>,
    private val related: suspend (Song) -> List<Song>,
    private val isCurrentScope: (String) -> Boolean = { true },
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val feedback: suspend (String) -> MixFeedback = { MixFeedback() },
    private val artistTracks: (suspend (Song) -> List<Song>)? = null,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val snapshots = ConcurrentHashMap<String, GeneratedMix>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val revisions = ConcurrentHashMap<String, Long>()
    private val catalogGate = Semaphore(2)
    private val retiredScopes = ConcurrentHashMap.newKeySet<String>()

    suspend fun clearScope(scope: String) = withContext(Dispatchers.IO) {
        retiredScopes.add(scope)
        LocalMixKind.entries.forEach { kind ->
            val key = key(scope, kind)
            locks.getOrPut(key) { Mutex() }.withLock {
                snapshots.remove(key)
                revisions.remove(key)
                listOf("json", "tmp").forEach { suffix ->
                    val file = File(directory, "$key.$suffix")
                    check(!file.exists() || file.delete()) { "Could not remove saved mix" }
                }
            }
        }
    }

    suspend fun cached(scope: String, kind: LocalMixKind): GeneratedMix? = withContext(Dispatchers.IO) {
        val key = key(scope, kind)
        if (scope in retiredScopes || !isCurrentScope(scope)) return@withContext null
        val preferences = feedback(scope).preferences
        val profile = if (kind.discoveryOnly) TasteProfileBuilder.build(evidence(), clock(), zone(), preferences) else null
        // Keep the previous snapshot immediately readable while another caller refreshes it.
        snapshots[key]?.let { return@withContext filtered(it, preferences, profile).takeIf { scope !in retiredScopes && isCurrentScope(scope) } }
        locks.getOrPut(key) { Mutex() }.withLock {
            val saved = if (scope !in retiredScopes && isCurrentScope(scope)) read(key, kind) else null
            saved?.let { filtered(it, preferences, profile) }.takeIf { scope !in retiredScopes && isCurrentScope(scope) }
        }
    }

    suspend fun load(scope: String, kind: LocalMixKind, force: Boolean = false): MixLoadResult =
        withContext(Dispatchers.IO) {
            val key = key(scope, kind)
            val revisionAtRequest = revisions[key] ?: 0L
            val result = locks.getOrPut(key) { Mutex() }.withLock {
                if (scope in retiredScopes || !isCurrentScope(scope)) return@withLock MixLoadResult(null, MixFailure.SCOPE_CHANGED)
                val preference = try { feedback(scope) } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    DebugLog.w(TAG, "Cannot read recommendation preferences", failure)
                    return@withLock MixLoadResult(null, MixFailure.REFRESH_FAILED)
                }
                val saved = read(key, kind)
                val now = clock()
                val discoveryHistory = if (kind.discoveryOnly) try { evidence() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { return@withLock MixLoadResult(null, MixFailure.REFRESH_FAILED) }
                    else null
                val discoveryProfile = discoveryHistory?.let { TasteProfileBuilder.build(it, now, zone(), preference.preferences) }
                val previous = saved?.let { filtered(it, preference.preferences, discoveryProfile) }
                if (previous != null && previous.feedbackRevision == preference.revision &&
                    (!kind.discoveryOnly || previous.songs.size * 2 >= saved.songs.size) &&
                    previous.algorithmVersion == LocalMixGenerator.ALGORITHM_VERSION && ((!force && now >= previous.generatedAt &&
                    now - previous.generatedAt < kind.freshnessMs) ||
                    (revisions[key] ?: 0L) != revisionAtRequest)) {
                    return@withLock MixLoadResult(previous)
                }
                try {
                    val history = discoveryHistory ?: evidence()
                    currentCoroutineContext().ensureActive()
                    if (!isCurrentScope(scope)) return@withLock MixLoadResult(null, MixFailure.SCOPE_CHANGED)
                    val profile = discoveryProfile ?: TasteProfileBuilder.build(history, now, zone(), preference.preferences)
                    val seeds = LocalMixGenerator.seedTracks(kind, history, now, zone(), preference.preferences, profile)
                    val retrieved = retrieveCandidates(kind, seeds, scope)
                    if (seeds.isNotEmpty() && !retrieved.answered && (previous != null || kind.discoveryOnly))
                        return@withLock MixLoadResult(previous, MixFailure.REFRESH_FAILED)
                    val discoveries = retrieved.candidates.map { it.song }
                    // Re-read after catalog work so feedback submitted during retrieval takes effect.
                    val latestPreferences = feedback(scope)
                    val tracks = LocalMixGenerator.generate(kind, history, now, zone(), discoveries,
                        latestPreferences.preferences, retrieved.candidates)
                    if (tracks.isEmpty()) {
                        val hidden = latestPreferences.preferences.values.any { it == TrackPreference.HIDE } &&
                            LocalMixGenerator.generate(kind, history, now, zone(), discoveries,
                                candidates = retrieved.candidates).isNotEmpty()
                        val ready = LocalMixGenerator.readyForDiscovery(kind,
                            TasteProfileBuilder.build(history, now, zone(), latestPreferences.preferences))
                        return@withLock MixLoadResult(previous,
                            if (hidden) MixFailure.HIDDEN_BY_FEEDBACK
                            else if (kind.discoveryOnly && ready) MixFailure.NO_DISCOVERIES else MixFailure.INSUFFICIENT_EVIDENCE)
                    }
                    currentCoroutineContext().ensureActive()
                    if (!isCurrentScope(scope)) return@withLock MixLoadResult(null, MixFailure.SCOPE_CHANGED)
                    val latestProfile = TasteProfileBuilder.build(history, now, zone(), latestPreferences.preferences)
                    val artistNames = if (kind == LocalMixKind.ARTIST_ORBIT) LocalMixGenerator
                        .seedTracks(kind, history, now, zone(), latestPreferences.preferences, latestProfile)
                        .flatMap { seed -> latestProfile.artists.filter { it.key in latestProfile.artistKeys(seed) }.map { it.name } }
                        .filter { it.isNotBlank() }.distinct().take(3) else emptyList()
                    val candidate = GeneratedMix(kind, maxOf(now, (saved?.version ?: 0L) + 1), now, tracks,
                        latestPreferences.revision, confidence = latestProfile.confidence.level, basedOnArtists = artistNames)
                    if (scope in retiredScopes) return@withLock MixLoadResult(null, MixFailure.SCOPE_CHANGED)
                    write(key, candidate)
                    snapshots[key] = candidate
                    revisions[key] = revisionAtRequest + 1
                    MixLoadResult(candidate)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    DebugLog.w(TAG, "Could not refresh ${kind.id}", failure)
                    MixLoadResult(previous, MixFailure.REFRESH_FAILED)
                }
            }
            if (scope !in retiredScopes && isCurrentScope(scope)) {
                try {
                    val latest = feedback(scope)
                    val finalProfile = if (kind.discoveryOnly) TasteProfileBuilder.build(evidence(), clock(), zone(), latest.preferences) else null
                    if (scope !in retiredScopes && isCurrentScope(scope)) result.copy(mix = result.mix?.let { filtered(it, latest.preferences, finalProfile) })
                    else MixLoadResult(null, MixFailure.SCOPE_CHANGED)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { MixLoadResult(null, MixFailure.REFRESH_FAILED) }
            } else MixLoadResult(null, MixFailure.SCOPE_CHANGED)
        }

    private fun key(scope: String, kind: LocalMixKind): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "$hash-${kind.id}"
    }

    private fun read(key: String, kind: LocalMixKind): GeneratedMix? {
        snapshots[key]?.let { return it }
        val file = File(directory, "$key.json")
        if (!file.isFile || file.length() > MAX_FILE_BYTES) return null
        return try {
            val record = json.decodeFromString(StoredMix.serializer(), file.readText())
            if (record.schemaVersion != 1 || record.algorithmVersion !in 1..LocalMixGenerator.ALGORITHM_VERSION ||
                record.key != key || record.kind != kind.id || record.generatedAt <= 0L) return null
            val songs = record.tracks.map { it.song() }.filter(LocalMixGenerator::eligible)
                .distinctBy { it.videoId }.take(LocalMixGenerator.MAX_TRACKS)
            if (songs.isEmpty()) null else GeneratedMix(kind, record.version, record.generatedAt, songs,
                record.feedbackRevision, record.algorithmVersion, record.confidence, record.basedOnArtists.take(3))
                .also { snapshots[key] = it }
        } catch (failure: Exception) {
            DebugLog.w(TAG, "Could not read saved mix", failure)
            null
        }
    }

    private fun write(key: String, mix: GeneratedMix) {
        check(directory.isDirectory || directory.mkdirs()) { "Mix storage unavailable" }
        val target = File(directory, "$key.json")
        val temporary = File(directory, "$key.tmp")
        try {
            val record = StoredMix(key = key, kind = mix.kind.id, version = mix.version,
                generatedAt = mix.generatedAt, tracks = mix.songs.map(StoredMixTrack::from), feedbackRevision = mix.feedbackRevision,
                confidence = mix.confidence, basedOnArtists = mix.basedOnArtists)
            temporary.outputStream().use { stream ->
                stream.write(json.encodeToString(StoredMix.serializer(), record).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    companion object {
        private const val TAG = "GeneratedMixes"
        private const val MAX_FILE_BYTES = 1_000_000L
    }

    private fun filtered(mix: GeneratedMix, preferences: Map<String, TrackPreference>, profile: TasteProfile? = null): GeneratedMix? {
        if (mix.kind.discoveryOnly && (profile == null || !LocalMixGenerator.readyForDiscovery(mix.kind, profile))) return null
        val songs = mix.songs.filter { song ->
            preferences[song.videoId] != TrackPreference.HIDE && (!mix.kind.discoveryOnly ||
                (song.videoId !in profile!!.heardTrackIds && (mix.kind != LocalMixKind.BEYOND_THE_USUAL ||
                    (profile.artistKeys(song).isNotEmpty() && profile.artistKeys(song).none(profile.dominantArtistKeys::contains)))))
        }
        return mix.copy(songs = songs).takeIf { songs.isNotEmpty() }
    }

    private data class CandidateRequest(val seed: Song, val source: CandidateSource, val fetch: suspend () -> List<Song>)
    private data class CandidateBatch(val candidates: List<MixCandidate>, val answered: Boolean)

    private suspend fun retrieveCandidates(kind: LocalMixKind, seeds: List<Song>, scope: String): CandidateBatch = coroutineScope {
        val requests = seeds.flatMap { seed -> buildList {
            add(CandidateRequest(seed, CandidateSource.RELATED_TRACKS) { related(seed) })
            if (kind.usesArtistCatalog && !seed.artistId.isNullOrBlank()) artistTracks?.let { fetch ->
                add(CandidateRequest(seed, CandidateSource.ARTIST_CATALOG) { fetch(seed) })
            }
        } }.take(4)
        if (requests.isEmpty()) return@coroutineScope CandidateBatch(emptyList(), false)
        val responses = withTimeoutOrNull(8_000) {
            requests.map { request -> async {
                catalogGate.withPermit {
                    if (!isCurrentScope(scope)) return@withPermit CandidateBatch(emptyList(), false)
                    try {
                        val songs = withTimeoutOrNull(3_500) { request.fetch() }
                        CandidateBatch(songs.orEmpty().take(75).map { MixCandidate(it, request.seed.videoId, request.source) }, songs != null)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        DebugLog.w(TAG, "Catalog candidates unavailable", failure)
                        CandidateBatch(emptyList(), false)
                    }
                }
            } }.awaitAll()
        }.orEmpty()
        CandidateBatch(responses.flatMap { it.candidates }, responses.any { it.answered })
    }
}

@Serializable
private data class StoredMix(
    val schemaVersion: Int = 1,
    val algorithmVersion: Int = LocalMixGenerator.ALGORITHM_VERSION,
    val key: String,
    val kind: String,
    val version: Long,
    val generatedAt: Long,
    val tracks: List<StoredMixTrack>,
    val feedbackRevision: Long = 0,
    val confidence: PreferenceConfidence = PreferenceConfidence.LEARNING,
    val basedOnArtists: List<String> = emptyList(),
)

@Serializable
internal data class StoredMixArtist(val name: String, val browseId: String?)

/** Queue entry IDs and temporary playback state deliberately do not belong in a mix snapshot. */
@Serializable
internal data class StoredMixTrack(
    val id: String, val title: String, val artist: String, val art: String?,
    val duration: String?, val artistId: String?, val albumId: String?, val album: String?,
    val localUri: String?, val localPath: String?, val explicit: Boolean?,
    val artists: List<StoredMixArtist> = emptyList(),
) {
    fun song() = Song(videoId = id, title = title, artist = artist, thumbnailUrl = art,
        durationText = duration, artistId = artistId, albumId = albumId, albumName = album,
        localUri = localUri, localPath = localPath, isExplicit = explicit,
        artists = artists.map { ArtistRef(it.name, it.browseId) })

    companion object {
        fun from(song: Song) = StoredMixTrack(song.videoId, song.title, song.artist, song.thumbnailUrl,
            song.durationText, song.artistId, song.albumId, song.albumName, song.localUri, song.localPath,
            song.isExplicit, song.artists.map { StoredMixArtist(it.name, it.browseId) })
    }
}

/** Installed by platforms with a local listening store; other platforms keep their existing path. */
object LocalMixPlatform {
    @Volatile var repository: GeneratedMixRepository? = null
    @Volatile var listenerScope: () -> String = { "guest" }
}
