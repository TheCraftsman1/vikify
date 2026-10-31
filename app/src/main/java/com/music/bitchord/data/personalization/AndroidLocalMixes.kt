package com.music.bitchord.data.personalization

import android.content.Context
import com.music.bitchord.BitChordApplication
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.TrackEntry
import java.io.File
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AndroidLocalMixes {
    lateinit var evidenceStore: ListeningEvidenceStore
        private set
    lateinit var home: AdaptiveHomeRepository
        private set
    lateinit var copies: MixCopyStore
        private set
    private lateinit var boundaries: PersonalizationResetStore
    private lateinit var resetCoordinator: PersonalizationResetCoordinator
    fun baseScope(): String {
        val auth = BitChordApplication.authStore
        val account = auth.activeAccountId ?: return "guest"
        val profile = auth.activeProfileId.orEmpty()
        return "account:${account.length}:$account:profile:$profile"
    }

    fun scope(): String {
        val base = baseScope()
        return try { if (::boundaries.isInitialized) boundaries.scope(base) else base }
        catch (failure: Exception) { "$base:reset-unavailable" }
    }

    suspend fun clear(base: String): PersonalizationResetResult {
        AndroidListeningEvidence.finish(SessionEnd.SCOPE_CHANGED)
        return resetCoordinator.reset(base, { withContext(Dispatchers.IO) {
            deviceListeningEvidence(ListeningStats.personalizationTracks())
        } })
    }

    suspend fun resumeCleanup() = resetCoordinator.resumeCleanup(baseScope())

    fun install(context: Context) {
        boundaries = PersonalizationResetStore(File(context.filesDir, "personalization_boundaries"))
        copies = MixCopyStore(File(context.filesDir, "saved_mix_copies"))
        evidenceStore = ListeningEvidenceStore(File(context.filesDir, "listening_evidence"))
        AndroidListeningEvidence.install(evidenceStore)
        LocalMixPlatform.listenerScope = ::scope
        LocalMixPlatform.repository = GeneratedMixRepository(
            directory = File(context.filesDir, "personalized_mixes"),
            evidence = {
                val identity = scope()
                val legacy = boundaries.learningHistory(baseScope(), deviceListeningEvidence(ListeningStats.personalizationTracks()))
                enrichSessionEvidence(legacy, evidenceStore.snapshot(identity).sessions, System.currentTimeMillis())
            },
            related = { song -> YtMusicRepository.radio(song.videoId).getOrThrow() },
            isCurrentScope = { it == scope() },
            feedback = { identity -> evidenceStore.snapshot(identity).feedback() },
            artistTracks = { song -> song.artistId?.let { YtMusicRepository.artistTopTracks(it).getOrThrow() }.orEmpty() },
        )
        home = AdaptiveHomeRepository(
            HomeCollectionStore(File(context.filesDir, "home_collections"),
                com.music.bitchord.data.mood.MoodGenreRepository.allMoods.mapTo(HashSet()) { it.id }),
            checkNotNull(LocalMixPlatform.repository), { it == scope() },
        )
        resetCoordinator = PersonalizationResetCoordinator(boundaries, evidenceStore,
            checkNotNull(LocalMixPlatform.repository), home.store)
    }
}

/** Sessions contribute intent and confidence, never a second copy of Replay play totals. */
internal fun enrichSessionEvidence(history: List<TrackListeningEvidence>, sessions: List<ListeningSessionRecord>,
    now: Long, zone: ZoneId = ZoneId.systemDefault()): List<TrackListeningEvidence> {
    val recent = sessions.filter { it.startedAt <= now && it.updatedAt >= now - ListeningEvidenceStore.RETENTION_MS }
        .groupBy { it.trackId }
    return history.map { track ->
        val observations = recent[track.song.videoId].orEmpty()
        track.copy(
            intentionalReplays = observations.count { it.intentionalReplay && it.heardMs >= 30_000 },
            earlySkips = observations.count { it.ended == SessionEnd.MANUAL_SKIP && it.heardMs in 1 until 30_000 &&
                (it.durationMs <= 0 || it.uniqueHeardMs.toDouble() / it.durationMs < 0.5) },
            completedSessions = observations.count { it.ended in setOf(SessionEnd.AUTO_ADVANCE, SessionEnd.AUTO_REPEAT) &&
                it.durationMs > 0 && it.uniqueHeardMs.toDouble() / it.durationMs >= 0.85 },
            distinctSessionDays = observations.filter { it.heardMs >= 30_000 }.map {
                Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
            }.distinct().size,
            sessionEpochDays = observations.filter { it.heardMs >= 30_000 }.mapTo(HashSet()) {
                Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().toEpochDay()
            },
        )
    }
}

/** Existing Replay evidence is device wide; undated totals never become recent daily plays. */
internal fun deviceListeningEvidence(entries: List<TrackEntry>): List<TrackListeningEvidence> =
    entries.groupBy { it.id }.mapNotNull { (id, records) ->
        // Replay's schema does not preserve local file URIs or external source references.
        // Reconstruct only identities that the YouTube playback resolver actually understands.
        if (!Regex("[A-Za-z0-9_-]{11}").matches(id)) return@mapNotNull null
        val latest = records.maxBy { it.last }
        val song = Song(videoId = id, title = latest.title, artist = latest.artist,
            thumbnailUrl = latest.art, artistId = latest.artistId,
            albumId = latest.albumId, albumName = latest.album)
        TrackListeningEvidence(
            song = song,
            plays = records.sumOf { it.plays.coerceAtLeast(0) },
            heardMs = records.sumOf { it.ms.coerceAtLeast(0L) },
            lastPlayedAt = records.maxOf { it.last },
            days = records.flatMap { it.days.entries }.mapNotNull { (date, stats) ->
                val day = runCatching { LocalDate.parse(date).toEpochDay() }.getOrNull()
                    ?: return@mapNotNull null
                DailyListeningEvidence(day, stats.plays.coerceAtLeast(0), stats.ms.coerceAtLeast(0L))
            }.groupBy { it.epochDay }.map { (day, evidence) ->
                DailyListeningEvidence(day, evidence.sumOf { it.plays }, evidence.sumOf { it.heardMs })
            },
        )
    }
