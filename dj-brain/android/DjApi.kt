/*
 * Standalone integration reference; this directory is NOT an Android source set.
 * Package these files in an Android module only after deliberately wiring the host.
 *
 * Dependencies for the parent's README (do not paste provider keys into Android):
 *   implementation("com.squareup.retrofit2:retrofit:3.0.0") // new dependency
 *   implementation("com.squareup.okhttp3:okhttp:5.3.2")
 *   implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
 *   implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
 *   implementation("com.google.firebase:firebase-auth") // includes Tasks
 *   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<host version>")
 * Apply the Kotlin serialization plugin matching the host Kotlin compiler. A small
 * JSON converter below avoids requiring a separate Retrofit converter artifact.
 * The host also needs INTERNET permission. Production URLs must use HTTPS.
 * NVIDIA/Gnani credentials belong exclusively on the server. /v1/tts accepts only
 * an authenticated session/cue identity, never arbitrary text or provider keys.
 *
 * Retrofit suspend calls enqueue on OkHttp's background dispatcher. Do not call
 * synchronous execute() on the main thread: Firebase token lookup deliberately
 * rejects that path. FirebaseApp must already be initialized by the host.
 */
package com.music.vikify.dj

import android.os.Looper
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.Route
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

@Serializable
data class StartSessionRequest(
    @SerialName("client_tz") val clientTz: String = "UTC",
    @SerialName("local_hour") val localHour: Int? = null,
    @SerialName("seed_genres") val seedGenres: List<String> = emptyList(),
    @SerialName("seed_artists") val seedArtists: List<String> = emptyList(),
    @SerialName("persona_id") val personaId: String = "midnight_fm",
    @SerialName("available_track_ids") val availableTrackIds: List<String>? = null,
)

@Serializable
data class AcousticVectorDto(
    val energy: Double,
    val valence: Double,
    val danceability: Double,
    val acousticness: Double,
    @SerialName("tempo_norm") val tempoNorm: Double,
)

@Serializable
data class TrackCuesDto(
    /** Null means instrumental, not a vocal onset at zero. */
    @SerialName("intro_vocal_ms") val introVocalMs: Long?,
    /** Absolute playhead position, NOT milliseconds remaining or a fade duration. */
    @SerialName("outro_ramp_ms") val outroRampMs: Long,
)

@Serializable
data class TrackDto(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    @SerialName("duration_ms") val durationMs: Long,
    val genre: String,
    val subgenres: List<String>,
    val moods: List<String>,
    val era: String,
    val bpm: Double,
    @SerialName("camelot_key") val camelotKey: String,
    val vector: AcousticVectorDto,
    val cues: TrackCuesDto,
    @SerialName("artist_facts") val artistFacts: List<String>,
)

@Serializable
data class DjTimingDto(
    @SerialName("start_on_prev_outro_ms") val startOnPrevOutroMs: Long? = null,
    @SerialName("max_talk_over_next_intro_ms") val maxTalkOverNextIntroMs: Long,
    @SerialName("duck_music_volume_to") val duckMusicVolumeTo: Float = 0.18f,
    @SerialName("ramp_duration_ms") val rampDurationMs: Long = 600,
    @SerialName("opening_lead_ms") val openingLeadMs: Long = 1_500,
)

@Serializable
data class DjCueDto(
    @SerialName("cue_id") val cueId: String,
    @SerialName("should_speak") val shouldSpeak: Boolean,
    @SerialName("speech_mode") val speechMode: String,
    val script: String? = null,
    val ssml: String? = null,
    @SerialName("vibe_label") val vibeLabel: String,
    val timing: DjTimingDto,
)

@Serializable
data class SessionResponseDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("vibe_label") val vibeLabel: String,
    val bucket: String,
    val track: TrackDto,
    @SerialName("mini_set") val miniSet: List<TrackDto>,
    @SerialName("dj_cue") val djCue: DjCueDto,
    @SerialName("playback_revision") val playbackRevision: Int,
    @SerialName("up_next_preview") val upNextPreview: List<TrackDto>,
    @SerialName("cue_id") val cueId: String,
)

/** /prefetch has this same shape, but its revision is the CURRENT revision. */
@Serializable
data class NextResponseDto(
    @SerialName("session_id") val sessionId: String,
    val track: TrackDto,
    @SerialName("dj_cue") val djCue: DjCueDto,
    @SerialName("up_next_preview") val upNextPreview: List<TrackDto>,
    @SerialName("playback_revision") val playbackRevision: Int,
    @SerialName("cue_id") val cueId: String,
    val queue: List<TrackDto>? = null, // optional extra on /dj-button
)

@Serializable
enum class FeedbackEventType {
    @SerialName("like") LIKE,
    @SerialName("skip") SKIP,
    @SerialName("complete") COMPLETE,
    @SerialName("replay") REPLAY,
}

@Serializable
data class FeedbackRequest(
    @SerialName("track_id") val trackId: String,
    @SerialName("event_type") val eventType: FeedbackEventType,
    @SerialName("listened_ms") val listenedMs: Long,
    @SerialName("track_duration_ms") val trackDurationMs: Long,
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("playback_revision") val playbackRevision: Int? = null,
)

@Serializable
data class FeedbackResponseDto(
    @SerialName("taste_delta") val tasteDelta: JsonObject,
    @SerialName("should_auto_pivot") val shouldAutoPivot: Boolean,
    @SerialName("playback_revision") val playbackRevision: Int,
)

@Serializable
data class DjButtonRequest(@SerialName("request_id") val requestId: String? = null)

@Serializable
data class VoiceRequest(
    val transcript: String,
    @SerialName("current_track_id") val currentTrackId: String?,
    @SerialName("current_position_ms") val currentPositionMs: Long,
    @SerialName("request_id") val requestId: String? = null,
)

@Serializable
data class VoiceResponseDto(
    val action: String,
    val params: JsonObject,
    @SerialName("dj_cue") val djCue: DjCueDto,
    @SerialName("updated_queue_head") val updatedQueueHead: TrackDto? = null,
    val queue: List<TrackDto>,
    @SerialName("playback_revision") val playbackRevision: Int,
    @SerialName("session_id") val sessionId: String,
    @SerialName("cue_id") val cueId: String,
)

@Serializable
data class SynthesizeCueRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("cue_id") val cueId: String,
)

@Serializable
sealed interface DjVoiceStreamEvent {
    @Serializable
    @SerialName("action")
    data class Action(
        val action: String,
        val params: JsonObject,
        @SerialName("playback_revision") val playbackRevision: Int,
        @SerialName("updated_queue_head") val updatedQueueHead: TrackDto? = null,
    ) : DjVoiceStreamEvent

    @Serializable
    @SerialName("tts_chunk")
    data class TtsChunk(val text: String) : DjVoiceStreamEvent

    @Serializable
    @SerialName("done")
    data class Done(
        @SerialName("dj_cue") val djCue: DjCueDto,
        @SerialName("session_id") val sessionId: String,
        @SerialName("cue_id") val cueId: String = djCue.cueId,
    ) : DjVoiceStreamEvent
}

interface DjApi {
    @POST("v1/sessions")
    suspend fun startSession(@Body request: StartSessionRequest): SessionResponseDto

    @GET("v1/sessions/{session_id}/next")
    suspend fun next(
        @Path("session_id") sessionId: String,
        @Query("expected_revision") expectedRevision: Int? = null,
        @Query("request_id") requestId: String? = null,
    ): NextResponseDto

    @GET("v1/sessions/{session_id}/prefetch")
    suspend fun prefetch(@Path("session_id") sessionId: String): NextResponseDto

    @POST("v1/sessions/{session_id}/feedback")
    suspend fun feedback(
        @Path("session_id") sessionId: String,
        @Body request: FeedbackRequest,
    ): FeedbackResponseDto

    @POST("v1/sessions/{session_id}/dj-button")
    suspend fun djButton(
        @Path("session_id") sessionId: String,
        @Body request: DjButtonRequest,
    ): NextResponseDto

    @POST("v1/sessions/{session_id}/voice")
    suspend fun voice(
        @Path("session_id") sessionId: String,
        @Body request: VoiceRequest,
    ): VoiceResponseDto

    @GET("v1/me/profile")
    suspend fun profile(): JsonObject

    /** Caller MUST close the streaming body, including on cancellation. */
    @Streaming
    @POST("v1/tts")
    suspend fun synthesizeCue(@Body request: SynthesizeCueRequest): ResponseBody
}

/** There is intentionally no unauthenticated/default development mode. */
sealed interface DjAuthentication {
    data class Firebase(
        val auth: FirebaseAuth,
        val tokenTimeoutMs: Long = 10_000,
    ) : DjAuthentication {
        init {
            require(tokenTimeoutMs in 1..60_000)
        }
    }

    /** Host must explicitly opt in AND the server must enable its development mode. */
    data class Development(
        val userId: String,
        val explicitlyEnabled: Boolean,
        val allowCleartextHttp: Boolean = false,
    ) : DjAuthentication {
        init {
            require(explicitlyEnabled) { "Development authentication must be explicitly enabled" }
            require(userId.matches(Regex("[A-Za-z0-9_.:-]{1,128}")))
        }
    }
}

/** Owns a dedicated HTTP client; closing it does not close the application's client. */
class DjApiClient private constructor(
    val api: DjApi,
    val json: Json,
    private val baseUrl: HttpUrl,
    private val http: OkHttpClient,
) : Closeable {
    private val streamingHttp = http.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    /**
     * Cold, single-request SSE flow; cancellation closes the body AND cancels the
     * authenticated OkHttp call. No automatic reconnection: voice commands mutate
     * state, so reconnecting blindly could apply a command twice. Use a stable
     * request_id if the host deliberately retries. tts_chunk contains TEXT, not WAV.
     * This transport helper does not independently mutate DjCoordinator state.
     */
    fun voiceStream(sessionId: String, request: VoiceRequest): Flow<DjVoiceStreamEvent> = callbackFlow {
        require(sessionId.isNotBlank())
        val url = baseUrl.newBuilder()
            .addPathSegment("v1")
            .addPathSegment("sessions")
            .addPathSegment(sessionId)
            .addPathSegment("voice")
            .addPathSegment("stream")
            .build()
        val call = streamingHttp.newCall(
            Request.Builder().url(url)
                .header("Accept", "text/event-stream")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
        val openResponse = AtomicReference<Response?>(null)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                openResponse.set(response)
                try {
                    response.use {
                        if (!it.isSuccessful) throw IOException("DJ voice stream HTTP ${it.code}")
                        val mime = it.header("Content-Type")?.substringBefore(';')?.trim()
                        if (!mime.equals("text/event-stream", ignoreCase = true)) {
                            throw IOException("DJ voice stream did not return text/event-stream")
                        }
                        val body = it.body
                        val source = body.source()
                        var eventName = ""
                        val data = StringBuilder()
                        var firstLine = true
                        while (!call.isCanceled()) {
                            // Both lines and whole frames are bounded, including malformed streams.
                            var line = source.readUtf8LineStrict(MAX_SSE_LINE_BYTES)
                            if (firstLine) {
                                line = line.removePrefix("\uFEFF")
                                firstLine = false
                            }
                            if (line.isEmpty()) {
                                if (data.isNotEmpty()) {
                                    val frame = decodeFrame(eventName, data.toString())
                                    if (frame != null) {
                                        if (trySendBlocking(frame).isFailure) return
                                        if (frame is DjVoiceStreamEvent.Done) {
                                            close()
                                            return
                                        }
                                    }
                                }
                                eventName = ""
                                data.setLength(0)
                            } else if (!line.startsWith(':')) {
                                val name = line.substringBefore(':')
                                val value = line.substringAfter(':', "").removePrefix(" ")
                                when (name) {
                                    "event" -> eventName = value
                                    "data" -> {
                                        if (data.length + value.length + 1 > MAX_SSE_FRAME_CHARS) {
                                            throw IOException("DJ SSE frame exceeds size limit")
                                        }
                                        if (data.isNotEmpty()) data.append('\n')
                                        data.append(value)
                                    }
                                }
                            }
                        }
                    }
                    close(IOException("DJ voice stream cancelled before done"))
                } catch (error: Exception) {
                    val failure = if (error is EOFException) {
                        IOException("DJ voice stream ended before done (or line exceeded limit)", error)
                    } else error
                    close(failure)
                } finally {
                    openResponse.compareAndSet(response, null)
                }
            }
        })
        awaitClose {
            call.cancel()
            openResponse.getAndSet(null)?.close()
        }
    }

    private fun decodeFrame(event: String, data: String): DjVoiceStreamEvent? {
        val supported = setOf("action", "tts_chunk", "done")
        if (event.isNotEmpty() && event !in supported) return null
        val type = json.parseToJsonElement(data).jsonObject["type"]?.jsonPrimitive?.content
        if (event.isNotEmpty() && event != type) throw IOException("Mismatched DJ SSE event/type")
        if (type !in supported) return null
        return json.decodeFromString<DjVoiceStreamEvent>(data)
    }

    override fun close() {
        http.dispatcher.cancelAll() // streamingHttp shares this dedicated dispatcher/pool
        http.connectionPool.evictAll()
        http.dispatcher.executorService.shutdown()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_SSE_LINE_BYTES = 65_536L
        private const val MAX_SSE_FRAME_CHARS = 262_144

        fun create(baseUrl: String, authentication: DjAuthentication): DjApiClient {
            val url = baseUrl.toHttpUrl()
            require(url.encodedPath.endsWith('/')) { "DJ base URL must end with /" }
            require(url.query == null && url.fragment == null)
            require(url.username.isEmpty() && url.password.isEmpty()) { "No credentials in DJ URL" }
            require(
                url.isHttps ||
                    (authentication is DjAuthentication.Development && authentication.allowCleartextHttp),
            ) { "HTTPS is required unless development HTTP is explicitly enabled" }
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true // includes the declared request defaults and nullable inventory
            }
            val builder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                // Do not forward a development header or token to a redirect target.
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false) // mutations are retried explicitly with stable IDs
            when (authentication) {
                is DjAuthentication.Firebase -> {
                    builder.addInterceptor(FirebaseIdTokenInterceptor(url, authentication))
                    builder.authenticator(FirebaseIdTokenAuthenticator(url, authentication))
                }
                is DjAuthentication.Development -> builder.addInterceptor { chain ->
                    checkOrigin(url, chain.request().url)
                    chain.proceed(
                        chain.request().newBuilder()
                            .removeHeader("Authorization")
                            .header("X-Dev-User-Id", authentication.userId)
                            .build(),
                    )
                }
            }
            val http = builder.build()
            val api = Retrofit.Builder()
                .baseUrl(url)
                .client(http)
                .addConverterFactory(DjJsonConverterFactory(json))
                .validateEagerly(true)
                .build()
                .create(DjApi::class.java)
            return DjApiClient(api, json, url, http)
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class DjJsonConverterFactory(private val json: Json) : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> {
        val serializer = json.serializersModule.serializer(type)
        return Converter<ResponseBody, Any> { body ->
            body.use { json.decodeFromString(serializer, it.string()) }
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<Annotation>,
        methodAnnotations: Array<Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody> {
        val serializer = json.serializersModule.serializer(type)
        return Converter<Any, RequestBody> { value ->
            json.encodeToString(serializer, value)
                .toRequestBody("application/json; charset=utf-8".toMediaType())
        }
    }
}

private data class FirebaseAttempt(val userId: String, val forcedRefresh: Boolean)

private fun checkOrigin(expected: HttpUrl, actual: HttpUrl) {
    if (expected.scheme != actual.scheme || expected.host != actual.host || expected.port != actual.port) {
        throw IOException("Refusing to send DJ authentication to a different origin")
    }
}

private fun firebaseToken(
    configuration: DjAuthentication.Firebase,
    user: FirebaseUser,
    forceRefresh: Boolean,
): String {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        throw IOException("Firebase Tasks.await is forbidden on main; use suspend Retrofit or enqueue")
    }
    try {
        val token = Tasks.await(
            user.getIdToken(forceRefresh), configuration.tokenTimeoutMs, TimeUnit.MILLISECONDS,
        ).token?.takeIf { it.isNotBlank() } ?: throw IOException("Firebase returned no ID token")
        if (configuration.auth.currentUser?.uid != user.uid) {
            throw IOException("Firebase user changed during DJ authentication")
        }
        return token
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IOException("DJ authentication interrupted", error)
    } catch (error: TimeoutException) {
        throw IOException("DJ authentication timed out", error)
    } catch (error: ExecutionException) {
        // Do not expose SDK exception text: it may contain account/token details.
        throw IOException("Firebase ID token lookup failed")
    }
}

private class FirebaseIdTokenInterceptor(
    private val origin: HttpUrl,
    private val configuration: DjAuthentication.Firebase,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        checkOrigin(origin, chain.request().url)
        val user = configuration.auth.currentUser ?: throw IOException("Sign in before using the DJ API")
        val token = firebaseToken(configuration, user, forceRefresh = false)
        return chain.proceed(
            chain.request().newBuilder()
                .removeHeader("X-Dev-User-Id")
                .header("Authorization", "Bearer $token")
                .tag(FirebaseAttempt::class.java, FirebaseAttempt(user.uid, forcedRefresh = false))
                .build(),
        )
    }
}

private class FirebaseIdTokenAuthenticator(
    private val origin: HttpUrl,
    private val configuration: DjAuthentication.Firebase,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != 401) return null
        val attempt = response.request.tag(FirebaseAttempt::class.java) ?: return null
        if (attempt.forcedRefresh || response.priorResponse != null) return null
        val body = response.request.body
        if (body != null && (body.isOneShot() || body.isDuplex())) return null
        checkOrigin(origin, response.request.url)
        val user = configuration.auth.currentUser?.takeIf { it.uid == attempt.userId } ?: return null
        val refreshed = try {
            firebaseToken(configuration, user, forceRefresh = true)
        } catch (_: IOException) {
            return null
        }
        return response.request.newBuilder()
            .header("Authorization", "Bearer $refreshed")
            .tag(FirebaseAttempt::class.java, FirebaseAttempt(user.uid, forcedRefresh = true))
            .build()
    }
}
