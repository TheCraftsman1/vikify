package com.music.vikify.data.intelligence

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class MusicAction {
    @SerialName("play") PLAY,
    @SerialName("pause") PAUSE,
    @SerialName("resume") RESUME,
    @SerialName("next") NEXT,
    @SerialName("previous") PREVIOUS,
    @SerialName("replay") REPLAY,
    @SerialName("explain") EXPLAIN,
}

/** Reasoning describes selection constraints. It never supplies playback URLs or invented IDs. */
@Serializable
data class MusicIntent(
    val action: MusicAction = MusicAction.PLAY,
    val query: String = "",
    val excludedArtists: List<String> = emptyList(),
    val excludePlayedToday: Boolean = false,
    val libraryOnly: Boolean = false,
    val confidence: Double = 0.0,
) {
    fun valid(): Boolean = query.length <= 250 && !query.contains(Regex("(?i)https?://|file:|content:")) &&
        confidence.isFinite() && confidence in 0.0..1.0 && excludedArtists.size <= 12 &&
        excludedArtists.all { it.isNotBlank() && it.length <= 120 }
}

/** Only facts supplied by the app; unknown audio measurements stay absent. */
@Serializable
data class MusicInventoryTrack(
    val id: String,
    val title: String,
    val artist: String,
    val inLibrary: Boolean = false,
    val playable: Boolean = true,
)

@Serializable
data class MusicPlanRequest(
    val transcript: String,
    val inventory: List<MusicInventoryTrack> = emptyList(),
    val blockedIds: List<String> = emptyList(),
    val playedTodayIds: List<String> = emptyList(),
    val preferredArtists: List<String> = emptyList(),
    val size: Int = 6,
)

@Serializable
data class MusicPlan(
    val schemaVersion: Int,
    val intent: MusicIntent,
    val trackIds: List<String>,
    val source: String,
    val explanation: String,
) {
    /** Recheck the server result against this exact request before it can affect a queue. */
    fun validFor(request: MusicPlanRequest): Boolean {
        if (schemaVersion != 1 || !intent.valid() || source !in setOf("model", "rules") ||
            explanation.length > 500 || trackIds.size > request.size || trackIds.distinct().size != trackIds.size) return false
        if (intent.action !in setOf(MusicAction.PLAY, MusicAction.EXPLAIN) &&
            LocalMusicIntent.parse(request.transcript).action != intent.action) return false
        val allowed = request.inventory.filter { it.playable && (!intent.libraryOnly || it.inLibrary) }
            .filterNot { it.id in request.blockedIds || (intent.excludePlayedToday && it.id in request.playedTodayIds) }
            .filterNot { track -> MusicSelection.artistExcluded(track.artist, intent.excludedArtists) }
            .mapTo(HashSet()) { it.id }
        return trackIds.all { it in allowed }
    }
}

/** Strict decoding deliberately rejects extra actions/URLs/acoustic fields from a model or endpoint. */
object MusicPlanCodec {
    val json = Json { ignoreUnknownKeys = false; isLenient = false; encodeDefaults = true }
    fun decode(raw: String, request: MusicPlanRequest): MusicPlan? =
        if (raw.length > 32_768) null else runCatching {
            json.decodeFromString<MusicPlan>(raw).takeIf { it.validFor(request) }
        }.getOrNull()
}

/** Small, explicit offline commands; it makes no claim to understand arbitrary natural language. */
object LocalMusicIntent {
    fun parse(transcript: String): MusicIntent {
        val text = transcript.trim().take(300)
        val normalized = text.lowercase().replace(Regex("[’']"), "'").removePrefix("please ").trimEnd('.', '!')
        val action = when (normalized) {
            "pause", "pause music", "stop music" -> MusicAction.PAUSE
            "play", "resume" -> MusicAction.RESUME
            "skip", "next", "next song" -> MusicAction.NEXT
            "previous", "previous song", "back" -> MusicAction.PREVIOUS
            "replay", "play again" -> MusicAction.REPLAY
            "why", "why this song", "why this song?", "explain my music" -> MusicAction.EXPLAIN
            else -> MusicAction.PLAY
        }
        val noRepeats = Regex("(?i)(?:don't|do not|never) (?:play|repeat)(?: anything)? (?:i've |i have |already )?(?:heard|played)(?: already)? today|no repeats today")
        val library = Regex("(?i)(?:only (?:from )?my (?:library|playlists)|from my (?:library|playlists) only)")
        val excluded = Regex("(?i)(?:avoid|exclude|no songs by) ([^,;.]+)").find(text)?.groupValues?.get(1)?.trim()
        var query = text.replace(noRepeats, "").replace(library, "")
            .replace(Regex("(?i)(?:avoid|exclude|no songs by) [^,;.]+"), "")
            .replace(Regex("(?i)^(please\\s+)?(play|put on|give me|i want|find me)\\s+"), "")
            .trim(' ', ',', '.', ';').take(250)
        if (action != MusicAction.PLAY || query.lowercase() in setOf("let dj pick", "surprise me", "you choose", "change vibe")) query = ""
        return MusicIntent(action, query, listOfNotNull(excluded), noRepeats.containsMatchIn(text),
            library.containsMatchIn(text), if (action != MusicAction.PLAY) 1.0 else .35)
    }
}

/** Match a named credited artist, including collaborations, without matching name fragments. */
object MusicSelection {
    fun artistExcluded(credits: String, excluded: Iterable<String>): Boolean {
        fun normalize(value: String) = java.text.Normalizer.normalize(value.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val credited = " ${normalize(credits)} "
        return excluded.any { name -> normalize(name).takeIf { it.isNotEmpty() }?.let { " $it " in credited } == true }
    }
}
