package com.music.vikify.dj

import com.music.vikify.BuildConfig
import com.music.vikify.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Direct NVIDIA NIM access; no intermediary server, Firebase token or music playback is required. */
internal object NemotronClient {
    private const val URL = "https://integrate.api.nvidia.com/v1/chat/completions"
    private val requests = Mutex()
    private val mutableStatus = MutableStateFlow<String?>(null)
    val status = mutableStatus.asStateFlow()
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(18, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).build()

    private fun key(): String = AppSettings.nvidiaApiKey.value.trim().ifBlank { BuildConfig.NVIDIA_API_KEY.trim() }
    fun isConfigured(): Boolean = key().isNotBlank()

    /** Returns only a validated JSON object string, never the provider envelope or its reasoning. */
    suspend fun json(system: String, context: String): String? {
        val apiKey = key()
        if (apiKey.isBlank()) {
            mutableStatus.value = "Add your NVIDIA API key in AI preferences to talk with Nemotron."
            return null
        }
        return try {
            withTimeout(20_000) {
                requests.withLock {
                    currentCoroutineContext().ensureActive()
                    mutableStatus.value = "Talking to Nemotron…"
                    val model = AppSettings.nvidiaModel.value.trim().ifBlank { AppSettings.NVIDIA_DEFAULT_MODEL }
                    val payload = NemotronCodec.request(model, system, context)
                    val request = Request.Builder().url(URL).header("Authorization", "Bearer $apiKey")
                        .header("Accept", "application/json")
                        .post(payload.toRequestBody("application/json".toMediaType())).build()
                    val (code, body) = client.newCall(request).boundedResponse()
                    currentCoroutineContext().ensureActive()
                    if (code != 200) {
                        mutableStatus.value = nemotronFailureMessage(code)
                        return@withLock null
                    }
                    val result = NemotronCodec.content(body)
                    mutableStatus.value = if (result != null) "Nemotron is connected."
                        else "Nemotron sent an incomplete reply. Try your message again."
                    result
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            mutableStatus.value = "NVIDIA took too long to reply. Try again."
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            mutableStatus.value = "Check the NVIDIA model name and shorten this message, then try again."
            null
        } catch (_: IOException) {
            mutableStatus.value = "Could not reach NVIDIA. Check your internet connection and try again."
            null
        } catch (_: Exception) {
            mutableStatus.value = "Nemotron could not finish this reply. Try again."
            null
        }
    }

    // Read on OkHttp's worker, so coroutine cancellation can close the call even during a body read.
    private suspend fun Call.boundedResponse(): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (it.code != 200) return@use it.code to ""
                        val source = it.body.source()
                        source.request(NemotronCodec.MAX_RESPONSE_BYTES.toLong() + 1)
                        if (source.buffer.size > NemotronCodec.MAX_RESPONSE_BYTES) throw IOException("Response limit exceeded")
                        it.code to source.readUtf8()
                    }
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
}
