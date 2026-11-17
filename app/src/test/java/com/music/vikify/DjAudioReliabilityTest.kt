package com.music.vikify

import com.music.vikify.data.model.Song
import com.music.vikify.dj.*
import org.junit.Assert.*
import org.junit.Test

class DjAudioReliabilityTest {
    @Test fun `volume changes during speech become the restored baseline`() {
        val gain = DjDuckingGain(.8f)
        gain.setGain(.22f)
        assertEquals(.176f, gain.volume, .0001f)
        gain.userVolume(.5f)
        assertEquals(.11f, gain.volume, .0001f)
        gain.setGain(1f)
        assertEquals(.5f, gain.volume, .0001f)
    }

    @Test fun `an interrupted ramp continues from its current gain without losing the original volume`() {
        val gain = DjDuckingGain(.7f)
        gain.setGain(.4f)
        gain.setGain(.6f)
        gain.setGain(.22f)
        gain.setGain(1f)
        assertEquals(.7f, gain.volume, .0001f)
        gain.userVolume(0f); gain.setGain(.22f); gain.setGain(1f)
        assertEquals(0f, gain.volume, 0f)
    }

    @Test fun `queue restoration is limited to the owning DJ set`() {
        assertTrue(ownsDjQueue("one", "dj:one:2", listOf("dj:one:1", "dj:one:2")))
        assertFalse(ownsDjQueue("one", "manual", listOf("manual")))
        assertFalse(ownsDjQueue("one", "dj:two:1", listOf("dj:two:1")))
        assertFalse(ownsDjQueue("one", "dj:one:1", listOf("dj:one:1", "user-added")))
        assertFalse(ownsDjQueue("one", null, emptyList()))
    }

    @Test fun `PCM envelope follows sound and silence at playback time including split samples`() {
        val envelope = DjSpeechEnvelope(300, 1)
        // Ten samples at 1/4 full scale, followed by ten silent samples (one 30 Hz frame each).
        val bytes = ByteArray(40) { if (it < 20 && it % 2 == 1) 32 else 0 }
        envelope.append(bytes.copyOfRange(0, 3))
        envelope.append(bytes.copyOfRange(3, bytes.size))
        assertEquals(.75f, envelope.levelAt(0), .0001f)
        assertEquals(0f, envelope.levelAt(34), 0f)
        assertEquals(0f, envelope.levelAt(10_000), 0f)
    }

    @Test fun `unsigned eight bit PCM silence does not move the mouth`() {
        val envelope = DjSpeechEnvelope(300, 1, pcm8 = true)
        envelope.append(ByteArray(10) { 128.toByte() })
        assertEquals(0f, envelope.levelAt(0), 0f)
    }

    @Test fun `offline requests do not substitute unrelated downloads`() {
        val songs = listOf(
            Song("one", "Soft Night", "Artist", null, localUri = "file:///one.m4a"),
            Song("two", "Fast Day", "Band", null, localUri = "file:///two.m4a"),
            Song("remote", "Soft Night", "Artist", null),
        )
        assertEquals(listOf("one", "two"), offlineCandidates(songs, null).map { it.song.videoId })
        assertEquals(listOf("one"), offlineCandidates(songs, "soft artist").map { it.song.videoId })
        assertTrue(offlineCandidates(songs, "unknown artist").isEmpty())
        val picks = DjLivePlanner.choose(offlineCandidates(songs, null), DjTaste(), emptySet(), emptyList(), emptySet(), 0)
        assertEquals(2, picks.size)
        assertTrue(picks.all { it.song.localUri != null })
        val withRemoteDuplicate = offlineCandidates(songs, null) + DjCandidate(songs.first().copy(localUri = null), DjOrigin.FOR_YOU)
        val duplicatePick = DjLivePlanner.choose(withRemoteDuplicate, DjTaste(), emptySet(), emptyList(), emptySet(), 0)
            .first { it.song.videoId == "one" }
        assertEquals(DjOrigin.FOR_YOU, duplicatePick.origin)
        assertEquals("file:///one.m4a", duplicatePick.song.localUri)
    }
}
