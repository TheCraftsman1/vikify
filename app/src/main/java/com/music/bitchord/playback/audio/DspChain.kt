package com.music.bitchord.playback.audio

import com.music.bitchord.playback.LoudnessProcessor

/** Optional loudness normalization, performed without intermediate quantization. */
class DspChain(val loudness: LoudnessProcessor = LoudnessProcessor()) : FloatAudioProcessor {
    override fun configure(sampleRate: Int, channelCount: Int) = loudness.configure(sampleRate, channelCount)
    fun onStreamBoundary() = loudness.onStreamBoundary()
    override fun process(block: AudioBlock) = loudness.process(block)
    override fun flush() = loudness.flush()
    override fun reset() = loudness.reset()
}
