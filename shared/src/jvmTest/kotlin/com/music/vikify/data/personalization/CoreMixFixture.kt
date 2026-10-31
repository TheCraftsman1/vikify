package com.music.vikify.data.personalization

import com.music.vikify.data.model.Song
import java.time.Instant
import java.time.ZoneId

internal class CoreMixFixture {
    val zone = ZoneId.of("Asia/Kolkata")
    val now = Instant.parse("2026-10-31T12:00:00Z").toEpochMilli()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
    fun song(id: Int, artist: String = "Alpha") = Song(id.toString().padStart(11, '0'), "Song $id", artist,
        null, artistId = "UC_$artist")
    fun track(id: Int, artist: String = "Alpha", plays: Int = 12, days: Int = 3) = TrackListeningEvidence(
        song(id, artist), plays, plays * 120_000L, now,
        (0 until days).map { day -> DailyListeningEvidence(today - day,
            plays / days + if (day < plays % days) 1 else 0, 120_000L) },
    )
    val history: List<TrackListeningEvidence> = (1..4).map { track(it) } +
        (5..7).map { track(it, "Beta", 5) } + track(8, "Gamma", 2, 2)
    val candidates = listOf(
        MixCandidate(song(101), song(1).videoId, CandidateSource.ARTIST_CATALOG),
        MixCandidate(song(102, "Beta"), song(5).videoId, CandidateSource.ARTIST_CATALOG),
        MixCandidate(song(103, "New artist X"), song(1).videoId),
        MixCandidate(song(104, "New artist Y"), song(5).videoId),
        MixCandidate(song(105, "Gamma"), song(1).videoId),
        MixCandidate(song(1), song(1).videoId),
    )
    fun generate(kind: LocalMixKind, history: List<TrackListeningEvidence> = this.history,
        candidates: List<MixCandidate> = this.candidates, preferences: Map<String, TrackPreference> = emptyMap()) =
        LocalMixGenerator.generate(kind, history, now, zone, preferences = preferences, candidates = candidates)
}
