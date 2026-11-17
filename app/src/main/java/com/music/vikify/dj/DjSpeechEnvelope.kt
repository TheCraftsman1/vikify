package com.music.vikify.dj

import kotlin.math.sqrt

/** A small, synchronized envelope of synthesized PCM, sampled on the playback clock. */
internal class DjSpeechEnvelope(private val sampleRate: Int, channels: Int, private val pcm8: Boolean = false) {
    private val samplesPerFrame = (sampleRate.coerceAtLeast(1) * channels.coerceAtLeast(1) / FRAMES_PER_SECOND).coerceAtLeast(1)
    private val frames = mutableListOf<Float>()
    private var count = 0
    private var squares = 0.0
    private var lowByte: Int? = null

    @Synchronized fun append(bytes: ByteArray) {
        for (byte in bytes) {
            val unsigned = byte.toInt() and 0xff
            val sample = if (pcm8) (unsigned - 128) / 128.0 else {
                val low = lowByte
                if (low == null) { lowByte = unsigned; continue }
                lowByte = null
                ((unsigned shl 8) or low).toShort() / 32768.0
            }
            squares += sample * sample
            if (++count >= samplesPerFrame) {
                // Bound malformed/long utterances, including engines that eagerly synthesize everything.
                if (frames.size < MAX_FRAMES) frames += (sqrt(squares / count) * 3).toFloat().coerceIn(0f, 1f)
                squares = 0.0; count = 0
            }
        }
    }

    @Synchronized fun levelAt(elapsedMs: Long): Float {
        val index = (elapsedMs.coerceAtLeast(0) * FRAMES_PER_SECOND / 1_000).toInt()
        return frames.getOrNull(index) ?: 0f
    }

    companion object {
        const val FRAMES_PER_SECOND = 30
        private const val MAX_FRAMES = 900
    }
}

/** The baseline follows intentional volume changes while the DJ owns an attenuation envelope. */
internal class DjDuckingGain(baseVolume: Float) {
    var baseVolume = baseVolume.coerceIn(0f, 1f)
        private set
    var gain = 1f
        private set
    val volume get() = baseVolume * gain
    fun setGain(value: Float) { gain = value.coerceIn(0f, 1f) }
    fun userVolume(value: Float) { baseVolume = value.coerceIn(0f, 1f) }
}

internal fun ownsDjQueue(sessionId: String, currentEntry: String?, entries: List<String?>): Boolean =
    sessionId.isNotEmpty() && currentEntry?.startsWith("dj:$sessionId:") == true &&
        entries.isNotEmpty() && entries.all { it?.startsWith("dj:$sessionId:") == true }
