package com.music.vikify.data.personalization

import com.music.vikify.data.model.Song
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ln
import kotlin.math.pow

enum class LocalMixKind(val id: String, val freshnessMs: Long, val relatedSeedLimit: Int = 0,
    val discoveryOnly: Boolean = false, val usesArtistCatalog: Boolean = false) {
    ON_REPEAT("on_repeat", 24 * 60 * 60 * 1000L),
    BACK_IN_ROTATION("back_in_rotation", 7 * 24 * 60 * 60 * 1000L),
    DAILY_MIX("your_daily_mix", 24 * 60 * 60 * 1000L, relatedSeedLimit = 2),
    COMFORT_ZONE("comfort_zone", 3 * 24 * 60 * 60 * 1000L, relatedSeedLimit = 1),
    ARTIST_ORBIT("artist_orbit", 7 * 24 * 60 * 60 * 1000L, relatedSeedLimit = 2, usesArtistCatalog = true),
    FRESH_FINDS("fresh_finds", 7 * 24 * 60 * 60 * 1000L, relatedSeedLimit = 3, discoveryOnly = true),
    BEYOND_THE_USUAL("beyond_the_usual", 7 * 24 * 60 * 60 * 1000L, relatedSeedLimit = 3, discoveryOnly = true);

    companion object {
        fun fromId(id: String): LocalMixKind? = entries.firstOrNull { it.id == id }
    }
}

data class DailyListeningEvidence(val epochDay: Long, val plays: Int, val heardMs: Long)

/** Only evidence the device actually recorded. Undated legacy plays stay undated. */
data class TrackListeningEvidence(
    val song: Song,
    val plays: Int,
    val heardMs: Long,
    val lastPlayedAt: Long,
    val days: List<DailyListeningEvidence> = emptyList(),
    val intentionalReplays: Int = 0,
    val earlySkips: Int = 0,
    val completedSessions: Int = 0,
    val distinctSessionDays: Int = 0,
    val sessionEpochDays: Set<Long> = emptySet(),
)

/** Pure selection rules, independent of the player, storage, and catalog transport. */
object LocalMixGenerator {
    const val ALGORITHM_VERSION = 4
    const val MAX_TRACKS = 30
    private val youtubeId = Regex("[A-Za-z0-9_-]{11}")

    fun eligible(song: Song): Boolean = song.title.isNotBlank() && !song.isVideo &&
        (youtubeId.matches(song.videoId) || (song.videoId.isNotBlank() && !song.localUri.isNullOrBlank()))

    fun favorites(evidence: List<TrackListeningEvidence>, now: Long, zone: ZoneId,
        preferences: Map<String, TrackPreference> = emptyMap()): List<Song> =
        evidence.asSequence()
            .filter { eligible(it.song) && it.plays > 0 && it.lastPlayedAt <= now && preferences[it.song.videoId] != TrackPreference.HIDE }
            .sortedWith(compareByDescending<TrackListeningEvidence> {
                ln(1.0 + it.plays.coerceAtLeast(0)) + recentWeight(it, now, zone) + intentScore(it, preferences)
            }.thenByDescending { it.heardMs }.thenBy { it.song.videoId })
            .map { it.song }.distinctBy { it.videoId }.toList()

    fun generate(
        kind: LocalMixKind,
        evidence: List<TrackListeningEvidence>,
        now: Long,
        zone: ZoneId,
        related: List<Song> = emptyList(),
        preferences: Map<String, TrackPreference> = emptyMap(),
        candidates: List<MixCandidate> = emptyList(),
    ): List<Song> = when (kind) {
        LocalMixKind.ON_REPEAT -> evidence.asSequence()
            .filter { eligible(it.song) && it.lastPlayedAt <= now && recentPlays(it, now, zone) >= 2 &&
                preferences[it.song.videoId] != TrackPreference.HIDE }
            .sortedWith(compareByDescending<TrackListeningEvidence> { recentWeight(it, now, zone) + intentScore(it, preferences) }
                .thenByDescending { it.heardMs }.thenBy { it.song.videoId })
            .map { it.song }.distinctBy { it.videoId }.take(MAX_TRACKS).toList()
        LocalMixKind.BACK_IN_ROTATION -> evidence.asSequence()
            .filter { eligible(it.song) && it.plays >= 3 && it.lastPlayedAt > 0 &&
                now - it.lastPlayedAt >= 14 * DAY_MS && preferences[it.song.videoId] != TrackPreference.HIDE }
            .sortedWith(compareByDescending<TrackListeningEvidence> {
                val gapDays = ((now - it.lastPlayedAt) / DAY_MS.toDouble()).coerceAtMost(180.0)
                ln(1.0 + it.plays) * (1.0 - 0.5.pow(gapDays / 30.0)) + intentScore(it, preferences)
            }.thenByDescending { it.heardMs }.thenBy { it.song.videoId })
            .map { it.song }.distinctBy { it.videoId }.take(MAX_TRACKS).toList()
        LocalMixKind.DAILY_MIX -> {
            val familiar = spreadArtists(favorites(evidence, now, zone, preferences))
            // A daily mix needs a personal seed; catalog results alone are not personal evidence.
            if (familiar.isEmpty()) emptyList() else {
                val heard = evidence.filter { it.plays > 0 }.mapTo(HashSet()) { it.song.videoId }
                val pool = if (candidates.isEmpty()) related else supportedCandidates(
                    TasteProfileBuilder.build(evidence, now, zone, preferences), candidates, preferences).map { it.song }
                val discoveries = spreadArtists(pool.filter { eligible(it) && it.videoId !in heard &&
                    preferences[it.videoId] != TrackPreference.HIDE }.distinctBy { it.videoId }
                    .sortedByDescending { preferences[it.videoId] == TrackPreference.MORE }).take(minOf(10, familiar.size / 2))
                val selected = mutableListOf<Song>()
                var known = 0
                var discovery = 0
                while (selected.size < MAX_TRACKS && (known < familiar.size || discovery < discoveries.size)) {
                    repeat(2) { if (known < familiar.size && selected.size < MAX_TRACKS) selected += familiar[known++] }
                    if (discovery < discoveries.size && selected.size < MAX_TRACKS) selected += discoveries[discovery++]
                }
                selected.toList()
            }
        }
        LocalMixKind.COMFORT_ZONE -> {
            val profile = TasteProfileBuilder.build(evidence, now, zone, preferences)
            val familiar = spreadArtists(favorites(evidence, now, zone, preferences).filter { song ->
                evidence.any { it.song.videoId == song.videoId && it.plays >= 2 } || preferences[song.videoId] == TrackPreference.MORE
            })
            val close = supportedCandidates(profile, candidates, preferences).map { it.song }
                .filter { it.videoId !in profile.heardTrackIds && profile.artistKeys(it).any(profile.favoriteArtistKeys::contains) }
                .distinctBy { it.videoId }
            if (familiar.isEmpty()) emptyList() else interleave(familiar, spreadArtists(close).take(minOf(6, familiar.size / 4)), 4)
        }
        LocalMixKind.ARTIST_ORBIT -> {
            val profile = TasteProfileBuilder.build(evidence, now, zone, preferences)
            val seeds = seedTracks(kind, evidence, now, zone, preferences, profile)
            val focus = seeds.flatMapTo(HashSet()) { profile.artistKeys(it) }
            val known = profile.tracks.filter { it.artistKeys.any(focus::contains) }.map { it.evidence.song }
            val offered = supportedCandidates(profile, candidates, preferences)
                .filter { candidate -> seeds.any { it.videoId == candidate.seedTrackId } }
            val plays = evidence.associate { it.song.videoId to it.plays }
            val pool = (offered.map { it.song } + known).distinctBy { it.videoId }
                .sortedWith(compareByDescending<Song> {
                    (if (profile.artistKeys(it).any(focus::contains)) 2.0 else 0.0) +
                        (if (it.videoId !in profile.heardTrackIds) 0.8 else 0.5 / (1.0 + (plays[it.videoId] ?: 0))) +
                        (if (preferences[it.videoId] == TrackPreference.MORE) 1.0 else 0.0)
                }.thenBy { it.videoId })
            diverse(pool, profile, perArtist = 8)
        }
        LocalMixKind.FRESH_FINDS, LocalMixKind.BEYOND_THE_USUAL -> {
            val profile = TasteProfileBuilder.build(evidence, now, zone, preferences)
            if (!readyForDiscovery(kind, profile)) emptyList() else {
                val history = profile.tracks.associateBy { it.evidence.song.videoId }
                val supported = supportedCandidates(profile, candidates, preferences)
                    .filter { it.song.videoId !in profile.heardTrackIds }
                    .groupBy { it.song.videoId }
                val ranked = supported.values.mapNotNull { offers ->
                    val song = offers.first().song
                    val keys = profile.artistKeys(song)
                    val seedFit = offers.maxOf { offer ->
                        history[offer.seedTrackId]?.evidence?.song?.let(profile::artistAffinity) ?: 0.0
                    }
                    if (kind == LocalMixKind.BEYOND_THE_USUAL &&
                        (keys.isEmpty() || keys.any(profile.dominantArtistKeys::contains) || seedFit < 0.25)) null
                    else {
                        val novelty = if (kind == LocalMixKind.BEYOND_THE_USUAL) 1.4 * (1.0 - profile.artistAffinity(song)) else
                            0.30 * profile.artistAffinity(song)
                        song to (seedFit * 0.65 + novelty + offers.map { it.seedTrackId }.distinct().size.coerceAtMost(3) * 0.1 +
                            if (preferences[song.videoId] == TrackPreference.MORE) 1.0 else 0.0)
                    }
                }.sortedWith(compareByDescending<Pair<Song, Double>> { it.second }.thenBy { it.first.videoId }).map { it.first }
                diverse(ranked, profile, if (kind == LocalMixKind.BEYOND_THE_USUAL) 2 else 3)
            }
        }
    }

    fun seedTracks(kind: LocalMixKind, evidence: List<TrackListeningEvidence>, now: Long, zone: ZoneId,
        preferences: Map<String, TrackPreference>, profile: TasteProfile = TasteProfileBuilder.build(evidence, now, zone, preferences)): List<Song> {
        if (kind.relatedSeedLimit == 0 || (kind.discoveryOnly && !readyForDiscovery(kind, profile))) return emptyList()
        var pool = favorites(evidence, now, zone, preferences).filter { it.localUri == null && profile.artistKeys(it).isNotEmpty() }
        if (kind == LocalMixKind.ARTIST_ORBIT) pool = pool.filter { profile.artistKeys(it).any(profile.favoriteArtistKeys::contains) }
        if (kind == LocalMixKind.BEYOND_THE_USUAL) pool = pool.filter { profile.artistAffinity(it) >= 0.25 }
            .sortedBy { profile.artistAffinity(it) }
        return pool.distinctBy { profile.artistKeys(it).sorted().joinToString("|") }.take(kind.relatedSeedLimit)
    }

    fun readyForDiscovery(kind: LocalMixKind, profile: TasteProfile): Boolean =
        profile.confidence.level != PreferenceConfidence.NONE &&
            (kind != LocalMixKind.BEYOND_THE_USUAL || (profile.confidence.level >= PreferenceConfidence.DEVELOPING &&
                profile.dominantArtistKeys.isNotEmpty()))

    private fun supportedCandidates(profile: TasteProfile, candidates: List<MixCandidate>, preferences: Map<String, TrackPreference>): List<MixCandidate> {
        val known = profile.tracks.associateBy { it.evidence.song.videoId }
        return candidates.filter { candidate ->
            val seed = known[candidate.seedTrackId]
            eligible(candidate.song) && preferences[candidate.song.videoId] != TrackPreference.HIDE && seed != null &&
                seed.artistKeys.isNotEmpty() && (candidate.source != CandidateSource.ARTIST_CATALOG || !seed.evidence.song.artistId.isNullOrBlank())
        }
    }

    private fun interleave(familiar: List<Song>, discoveries: List<Song>, familiarPerDiscovery: Int): List<Song> = buildList {
        var known = 0
        var fresh = 0
        while (size < MAX_TRACKS && (known < familiar.size || fresh < discoveries.size)) {
            repeat(familiarPerDiscovery) { if (known < familiar.size && size < MAX_TRACKS) add(familiar[known++]) }
            if (fresh < discoveries.size && size < MAX_TRACKS) add(discoveries[fresh++])
        }
    }

    private fun diverse(songs: List<Song>, profile: TasteProfile, perArtist: Int): List<Song> {
        val counts = HashMap<String, Int>()
        return buildList {
            songs.forEach { song ->
                val keys = profile.artistKeys(song).ifEmpty { setOf("track:${song.videoId}") }
                if (size < MAX_TRACKS && keys.none { (counts[it] ?: 0) >= perArtist }) {
                    add(song)
                    keys.forEach { counts[it] = (counts[it] ?: 0) + 1 }
                }
            }
        }
    }

    /** Round robin artist groups without discarding a listener's one-artist favorites. */
    private fun spreadArtists(songs: List<Song>): List<Song> {
        val groups = songs.groupBy { it.artistId ?: it.artist.trim().lowercase(java.util.Locale.ROOT) }
            .values.map { it.iterator() }
        return buildList {
            while (groups.any { it.hasNext() }) groups.forEach { if (it.hasNext()) add(it.next()) }
        }
    }

    private fun recentPlays(track: TrackListeningEvidence, now: Long, zone: ZoneId): Int {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        return track.days.filter { it.epochDay in (today - 27)..today }.sumOf { it.plays.coerceAtLeast(0) }
    }

    private fun intentScore(track: TrackListeningEvidence, preferences: Map<String, TrackPreference>): Double =
        (if (preferences[track.song.videoId] == TrackPreference.MORE) 1.0 else 0.0) +
            track.intentionalReplays.coerceIn(0, 3) * 0.25 + track.completedSessions.coerceIn(0, 3) * 0.05 -
            track.earlySkips.coerceIn(0, 4) * 0.15

    private fun recentWeight(track: TrackListeningEvidence, now: Long, zone: ZoneId): Double {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        return track.days.asSequence().filter { it.epochDay in (today - 27)..today }.sumOf {
            it.plays.coerceAtLeast(0) * 0.5.pow((today - it.epochDay) / 14.0)
        }
    }

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}
