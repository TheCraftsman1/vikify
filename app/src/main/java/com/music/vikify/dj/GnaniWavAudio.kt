package com.music.vikify.dj

/** Gnani can return streaming WAV lengths (0xffffffff), which need finalizing before file playback. */
internal object GnaniWavAudio {
    data class Pcm(val sampleRate: Int, val channels: Int, val bits: Int, val offset: Int, val size: Int)

    fun pcm(bytes: ByteArray): Pcm? {
        if (bytes.size < 44 || tag(bytes, 0) != "RIFF" || tag(bytes, 8) != "WAVE") return null
        var offset = 12
        var sampleRate = 0
        var channels = 0
        var bits = 0
        var format = 0
        while (offset <= bytes.size - 8) {
            val size = uint(bytes, offset + 4)
            val start = offset + 8
            val remaining = bytes.size - start
            when (tag(bytes, offset)) {
                "fmt " -> {
                    if (size < 16 || size > remaining) return null
                    format = ushort(bytes, start)
                    channels = ushort(bytes, start + 2)
                    sampleRate = uint(bytes, start + 4).takeIf { it <= Int.MAX_VALUE }?.toInt() ?: return null
                    bits = ushort(bytes, start + 14)
                }
                "data" -> {
                    if (format != 1 || channels !in 1..2 || sampleRate !in 8_000..48_000 || bits !in listOf(8, 16)) return null
                    val actual = if (size == 0xffff_ffffL || size == 0L) remaining else size.takeIf { it <= remaining }?.toInt() ?: return null
                    val frameBytes = channels * bits / 8
                    if (actual < frameBytes || actual % frameBytes != 0) return null
                    return Pcm(sampleRate, channels, bits, start, actual)
                }
            }
            if (size > remaining) return null
            val next = start.toLong() + size + (size and 1L)
            if (next > bytes.size) return null
            offset = next.toInt()
        }
        return null
    }

    /** Returns a finite, validated PCM WAV. Rejects JSON errors and truncated audio. */
    fun finalize(bytes: ByteArray): ByteArray? {
        val pcm = pcm(bytes) ?: return null
        return bytes.copyOf().apply {
            putInt(this, 4, size - 8)
            putInt(this, pcm.offset - 4, pcm.size)
        }
    }

    private fun tag(bytes: ByteArray, offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
    private fun ushort(bytes: ByteArray, offset: Int) = (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)
    private fun uint(bytes: ByteArray, offset: Int) = (0..3).fold(0L) { value, byte -> value or ((bytes[offset + byte].toLong() and 255) shl (8 * byte)) }
    private fun putInt(bytes: ByteArray, offset: Int, value: Int) { repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() } }
}

internal fun gnaniFailureMessage(httpCode: Int, providerCode: String = ""): String = when {
    httpCode == 401 || providerCode == "INVALID_API_KEY" || providerCode == "MISSING_API_KEY" ->
        "Gnani rejected the API key. Add a valid Gnani key, then try Preview voice again."
    httpCode == 402 -> "Gnani has no voice credits available. Check your Gnani account balance."
    httpCode == 403 -> "This Gnani key cannot use text-to-speech. Enable TTS access and check your account credits."
    httpCode == 429 -> "Gnani is busy or rate limited. Wait a moment, then try Preview voice again."
    httpCode in listOf(400, 422) -> "Gnani rejected the voice settings. Check the selected voice and try again."
    else -> "Gnani voice is temporarily unavailable. Your device voice will be used when available."
}
