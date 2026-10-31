package com.music.bitchord.playback.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** Removing effects must leave disabled normalization sample-exact on every layout. */
class FloatDspChainTest {
    @Test
    fun `disabled normalization preserves mono stereo and surround samples`() {
        for (channels in listOf(1, 2, 6, 8)) {
            val chain = DspChain()
            chain.configure(96000, channels)
            val block = AudioBlock(channelCount = channels, capacityFrames = 256)
            val expected = FloatArray(256 * channels) { index ->
                when (index % 7) {
                    0 -> -1f
                    1 -> 1f
                    2 -> -0.0000001f
                    3 -> 0.0000001f
                    else -> (index % 257 - 128) / 128f
                }
            }
            expected.copyInto(block.samples)
            block.reset(256)
            chain.process(block)
            assertArrayEquals("channels=$channels", expected, block.samples.copyOf(expected.size), 0f)
        }
    }
}
