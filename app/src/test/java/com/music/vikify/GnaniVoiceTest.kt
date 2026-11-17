package com.music.vikify

import com.music.vikify.dj.GnaniWavAudio
import com.music.vikify.dj.gnaniFailureMessage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class GnaniVoiceTest {
    private fun wav(extraChunk: Boolean = false): ByteArray {
        val samples = ByteArray(1600)
        val extraSize = if (extraChunk) 12 else 0
        return ByteBuffer.allocate(44 + extraSize + samples.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(-1); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(24000); putInt(48000); putShort(2); putShort(16)
            if (extraChunk) { put("JUNK".toByteArray()); putInt(3); put(byteArrayOf(4, 5, 6, 0)) }
            put("data".toByteArray()); putInt(-1); put(samples)
        }.array()
    }

    @Test fun `streaming WAV lengths are finalized for Android file playback`() {
        val source = wav()
        val normalized = GnaniWavAudio.finalize(source)!!
        val buffer = ByteBuffer.wrap(normalized).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(normalized.size - 8, buffer.getInt(4))
        assertEquals(1600, buffer.getInt(40))
        assertEquals(-1, ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN).getInt(4))
        val pcm = GnaniWavAudio.pcm(normalized)!!
        assertEquals(24000, pcm.sampleRate)
        assertEquals(1, pcm.channels)
        assertEquals(16, pcm.bits)
        assertEquals(44, pcm.offset)
    }

    @Test fun `metadata chunks and odd padding do not enter the speech envelope`() {
        val source = wav(extraChunk = true)
        val normalized = GnaniWavAudio.finalize(source)!!
        val pcm = GnaniWavAudio.pcm(normalized)!!
        assertEquals(56, pcm.offset)
        assertEquals(1600, pcm.size)
        assertEquals(1600, ByteBuffer.wrap(normalized).order(ByteOrder.LITTLE_ENDIAN).getInt(52))
    }

    @Test fun `JSON errors and truncated WAV responses are never cached as audio`() {
        assertNull(GnaniWavAudio.finalize("{\"success\":false,\"message\":\"account disabled\"}".toByteArray()))
        assertNull(GnaniWavAudio.finalize(wav().copyOf(30)))
        val truncated = wav()
        ByteBuffer.wrap(truncated).order(ByteOrder.LITTLE_ENDIAN).putInt(40, 9000)
        assertNull(GnaniWavAudio.finalize(truncated))
        assertNull(GnaniWavAudio.finalize(wav().copyOf(45)))
    }

    @Test fun `auth credit and access errors suggest the correct action`() {
        assertTrue(gnaniFailureMessage(401, "INVALID_API_KEY").contains("valid Gnani key"))
        assertTrue(gnaniFailureMessage(402).contains("balance"))
        assertTrue(gnaniFailureMessage(403, "SCOPE_NOT_ALLOWED").contains("TTS access"))
        assertTrue(gnaniFailureMessage(429).contains("Wait a moment"))
        assertFalse(gnaniFailureMessage(503).contains("valid Gnani key"))
    }
}
