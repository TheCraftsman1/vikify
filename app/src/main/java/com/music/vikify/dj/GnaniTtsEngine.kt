package com.music.vikify.dj

import android.content.Context
import android.util.Log
import com.music.vikify.BuildConfig
import com.music.vikify.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object GnaniTtsEngine {
    private const val TAG = "GnaniTtsEngine"
    private const val BASE_URL = "https://api.vachana.ai/api/v1/tts/inference"
    private const val MODEL = "timbre-v2.5"
    private const val MAX_CACHE_BYTES = 25 * 1024 * 1024L
    private const val MAX_AUDIO_BYTES = 8 * 1024 * 1024
    // Some accounts permit only one active request, including previews and session speech.
    private val synthesis = Mutex()
    private val mutableStatus = MutableStateFlow<String?>(null)
    val status = mutableStatus.asStateFlow()
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS).build()
    }

    fun getApiKey(): String = AppSettings.gnaniApiKey.value.trim().ifBlank { BuildConfig.GNANI_API_KEY.trim() }
    fun isConfigured(): Boolean = getApiKey().isNotBlank() && AppSettings.gnaniTtsEnabled.value

    suspend fun synthesize(context: Context, text: String, voice: String = AppSettings.gnaniTtsVoice.value,
        speed: Float = 1.0f): File? = synthesis.withLock {
        withContext(Dispatchers.IO) {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                mutableStatus.value = "Add a Gnani API key to use the AI voice."
                return@withContext null
            }
            if (text.isBlank()) return@withContext null
            val selectedVoice = voice.trim().ifBlank { "Girish" }
            val selectedSpeed = speed.coerceIn(.85f, 1.15f)
            val cacheDir = File(context.cacheDir, "gnani_tts").apply { mkdirs() }
            // New cache namespace excludes earlier files with unfinalized WAV headers.
            val cacheKey = hash("wav-v2:$MODEL:$selectedVoice:$selectedSpeed:$text")
            val cachedFile = File(cacheDir, "$cacheKey.wav")
            if (cachedFile.exists() && cachedFile.length() in 44..MAX_AUDIO_BYTES.toLong() &&
                GnaniWavAudio.pcm(cachedFile.readBytes()) != null) {
                cachedFile.setLastModified(System.currentTimeMillis())
                mutableStatus.value = "Gnani voice is ready."
                return@withContext cachedFile
            }
            val tempFile = File(cacheDir, "$cacheKey.tmp")
            try {
                mutableStatus.value = "Preparing Gnani voice…"
                val payload = JSONObject().apply {
                    put("text", text); put("voice", selectedVoice); put("model", MODEL)
                    put("language", "en-IN"); put("speed", selectedSpeed.toDouble())
                    put("audio_config", JSONObject().apply {
                        put("sample_rate", 24000); put("num_channels", 1); put("sample_width", 2)
                        put("encoding", "linear_pcm"); put("container", "wav")
                    })
                }
                val request = Request.Builder().url(BASE_URL).header("X-API-Key-ID", apiKey)
                    .header("Accept", "audio/wav")
                    .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).awaitResponse().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string().orEmpty()
                        val providerCode = runCatching {
                            JSONObject(errorBody).optJSONObject("detail")?.optString("error_code").orEmpty()
                        }.getOrDefault("")
                        mutableStatus.value = gnaniFailureMessage(response.code, providerCode)
                        Log.w(TAG, "Gnani request failed: HTTP ${response.code}")
                        return@withContext null
                    }
                    val body = response.body ?: throw IOException("Empty audio response")
                    val audio = body.byteStream().use { stream ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (out.size() <= MAX_AUDIO_BYTES) {
                            currentCoroutineContext().ensureActive()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                        }
                        out.toByteArray()
                    }
                    if (audio.size > MAX_AUDIO_BYTES) throw IOException("Audio response too large")
                    currentCoroutineContext().ensureActive()
                    val finalized = GnaniWavAudio.finalize(audio) ?: throw IOException("Invalid WAV audio")
                    tempFile.writeBytes(finalized)
                    if (cachedFile.exists()) cachedFile.delete()
                    if (!tempFile.renameTo(cachedFile)) throw IOException("Unable to save voice audio")
                    trimCache(cacheDir)
                    mutableStatus.value = "Gnani voice is ready."
                    cachedFile
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableStatus.value = if (error is java.net.UnknownHostException || error is java.net.SocketTimeoutException)
                    "Could not reach Gnani. Check your connection; your device voice will be used when available."
                else "Gnani returned audio that could not be played. Your device voice will be used when available."
                // Keep credentials, spoken text and provider response bodies out of logs.
                Log.w(TAG, "Gnani synthesis failed: ${error.javaClass.simpleName}")
                null
            } finally {
                tempFile.delete()
            }
        }
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response, onCancellation = { _, resource, _ -> resource.close() })
            }
        })
    }

    internal fun extractEnvelope(wavFile: File): DjSpeechEnvelope? = runCatching {
        val bytes = wavFile.readBytes()
        val pcm = GnaniWavAudio.pcm(bytes) ?: return null
        DjSpeechEnvelope(pcm.sampleRate, pcm.channels, pcm8 = pcm.bits == 8).apply {
            append(bytes.copyOfRange(pcm.offset, pcm.offset + pcm.size))
        }
    }.getOrNull()

    private fun hash(input: String) = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun trimCache(cacheDir: File) {
        val files = cacheDir.listFiles { file -> file.extension == "wav" }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return
        files.forEach { file ->
            if (total > MAX_CACHE_BYTES * .75) { val size = file.length(); if (file.delete()) total -= size }
        }
    }
}
