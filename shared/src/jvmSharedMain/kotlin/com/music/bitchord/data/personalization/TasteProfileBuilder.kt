package com.music.bitchord.data.personalization

import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

@Serializable
enum class PreferenceConfidence { NONE, LEARNING, DEVELOPING, ESTABLISHED }

data class TasteConfidence(
    val level: PreferenceConfidence,
    val distinctTracks: Int,
    val distinctDays: Int,
    val meaningfulPlays: Int,
)

data class TrackTaste(
    val evidence: TrackListeningEvidence,
    val artistKeys: Set<String>,
    val longTermAffinity: Double,
    val recentAffinity: Double,
)

data class ArtistTaste(
    val key: String,
    val name: String,
    val longTermAffinity: Double,
    val recentAffinity: Double,
    val confidence: TasteConfidence,
    val meaningfulPlays: Int,
    val explicitlyPreferred: Boolean,
) {
    val affinity: Double get() = 0.65 * longTermAffinity + 0.35 * recentAffinity
}

data class TasteProfile(
    val confidence: TasteConfidence,
    val tracks: List<TrackTaste>,
    val artists: List<ArtistTaste>,
    private val nameAliases: Map<String, String>,
    val heardTrackIds: Set<String> = tracks.mapTo(HashSet()) { it.evidence.song.videoId },
) {
    val favoriteArtistKeys: Set<String> = artists.filter { it.meaningfulPlays >= 2 || it.explicitlyPreferred }
        .take(5).mapTo(LinkedHashSet()) { it.key }
    val dominantArtistKeys: Set<String> = artists.maxOfOrNull { it.affinity }?.let { strongest ->
        artists.filter { it.meaningfulPlays >= 2 && it.affinity >= strongest * 0.75 }.mapTo(HashSet()) { it.key }
    }.orEmpty()

    fun artistKeys(song: Song): Set<String> = credits(song).mapNotNullTo(LinkedHashSet()) {
        identity(it, nameAliases)
    }

    fun artistAffinity(song: Song): Double {
        val keys = artistKeys(song)
        return artists.filter { it.key in keys }.maxOfOrNull { it.affinity } ?: 0.0
    }
}

/** Normalized artist and track evidence. Confidence measures breadth, not a probability of liking music. */
object TasteProfileBuilder {
    fun build(evidence: List<TrackListeningEvidence>, now: Long, zone: ZoneId,
        preferences: Map<String, TrackPreference> = emptyMap()): TasteProfile {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        val history = evidence.filter {
            LocalMixGenerator.eligible(it.song) && it.plays > 0 && it.lastPlayedAt in 1..now &&
                preferences[it.song.videoId] != TrackPreference.HIDE
        }.distinctBy { it.song.videoId }.sortedBy { it.song.videoId }
        val aliases = history.flatMap { credits(it.song) }
            .groupBy { normalized(it.name) }.mapNotNull { (name, refs) ->
                val ids = refs.mapNotNull { it.browseId?.takeIf(String::isNotBlank)?.let { id -> "id:$id" } }.distinct()
                when {
                    name.isBlank() || ids.size > 1 -> null
                    ids.size == 1 -> name to ids.single()
                    else -> name to "name:$name"
                }
            }.toMap()
        data class Raw(val evidence: TrackListeningEvidence, val keys: Set<String>, val long: Double, val recent: Double)
        val raw = history.map { track ->
            val recent = track.days.filter { it.epochDay in (today - 27)..today }.sumOf {
                it.plays.coerceIn(0, 10) * 0.5.pow((today - it.epochDay) / 14.0)
            }
            Raw(track, credits(track.song).mapNotNullTo(LinkedHashSet()) { identity(it, aliases) },
                ln(1.0 + track.plays.coerceAtMost(100)) + 0.20 * ln(1.0 + track.heardMs.coerceAtLeast(0) / 60_000.0), recent)
        }
        val maxLong = raw.maxOfOrNull { it.long }?.coerceAtLeast(1.0) ?: 1.0
        val maxRecent = raw.maxOfOrNull { it.recent }?.coerceAtLeast(1.0) ?: 1.0
        val tracks = raw.map { TrackTaste(it.evidence, it.keys, it.long / maxLong, it.recent / maxRecent) }
        data class ArtistRaw(val key: String, val tracks: List<Raw>, val long: Double, val recent: Double)
        val artistRaw = raw.flatMap { item -> item.keys.map { it to item } }.groupBy({ it.first }, { it.second })
            .map { (key, items) -> ArtistRaw(key, items,
                items.sumOf { it.long / it.keys.size.coerceAtLeast(1) },
                items.sumOf { it.recent / it.keys.size.coerceAtLeast(1) }) }
        val maxArtistLong = artistRaw.maxOfOrNull { it.long }?.coerceAtLeast(1.0) ?: 1.0
        val maxArtistRecent = artistRaw.maxOfOrNull { it.recent }?.coerceAtLeast(1.0) ?: 1.0
        val artists = artistRaw.map { artist ->
            val artistHistory = artist.tracks.map { it.evidence }
            val display = artistHistory.flatMap { credits(it.song) }
                .firstOrNull { identity(it, aliases) == artist.key }?.name.orEmpty().trim()
            ArtistTaste(artist.key, display, artist.long / maxArtistLong, artist.recent / maxArtistRecent,
                confidence(artistHistory, today, artist = true), artistHistory.sumOf { it.plays },
                artistHistory.any { preferences[it.song.videoId] == TrackPreference.MORE })
        }.sortedWith(compareByDescending<ArtistTaste> { it.explicitlyPreferred }.thenByDescending { it.affinity }.thenBy { it.key })
        return TasteProfile(confidence(history, today), tracks, artists, aliases,
            evidence.filter { LocalMixGenerator.eligible(it.song) && it.plays > 0 }.mapTo(HashSet()) { it.song.videoId })
    }

    private fun confidence(tracks: List<TrackListeningEvidence>, today: Long, artist: Boolean = false): TasteConfidence {
        if (tracks.isEmpty()) return TasteConfidence(PreferenceConfidence.NONE, 0, 0, 0)
        val datedTracks = tracks.filter { track ->
            track.days.any { it.epochDay in (today - 89)..today && it.plays > 0 } ||
                track.sessionEpochDays.any { it in (today - 89)..today }
        }
        val days = datedTracks.flatMap { track ->
            track.days.filter { it.epochDay in (today - 89)..today && it.plays > 0 }.map { it.epochDay } +
                track.sessionEpochDays.filter { it in (today - 89)..today }
        }.distinct().size
        val plays = datedTracks.sumOf { track ->
            track.days.filter { it.epochDay in (today - 89)..today }.sumOf { it.plays.coerceAtLeast(0) }
        }
        val level = when {
            datedTracks.size >= (if (artist) 3 else 5) && days >= 3 && plays >= (if (artist) 8 else 12) -> PreferenceConfidence.ESTABLISHED
            datedTracks.size >= 2 && days >= 2 && plays >= 4 -> PreferenceConfidence.DEVELOPING
            else -> PreferenceConfidence.LEARNING
        }
        return TasteConfidence(level, datedTracks.size, days, plays)
    }
}

internal fun normalized(name: String) = name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
private fun credits(song: Song): List<ArtistRef> = song.artists.ifEmpty { listOf(ArtistRef(song.artist, song.artistId)) }
private fun identity(artist: ArtistRef, aliases: Map<String, String>): String? {
    val name = normalized(artist.name)
    val alias = aliases[name]
    artist.browseId?.takeIf { it.isNotBlank() }?.let {
        // Legacy name-only history can still match catalog credits. Known conflicting IDs stay separate.
        return alias?.takeIf { key -> key.startsWith("name:") } ?: "id:$it"
    }
    if (name.isBlank()) return null
    return alias ?: "name:$name"
}

enum class CandidateSource { RELATED_TRACKS, ARTIST_CATALOG }
data class MixCandidate(val song: Song, val seedTrackId: String, val source: CandidateSource = CandidateSource.RELATED_TRACKS)
