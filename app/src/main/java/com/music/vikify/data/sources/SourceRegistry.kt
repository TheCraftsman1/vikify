package com.music.vikify.data.sources

import android.net.Uri
import com.music.vikify.data.sources.SourceTrackKeys

/** Identity for the built-in catalogue, retained for playback and saved queues. */
data class SourceConfig(
    val id: String = "youtube",
    val kind: SourceKind = SourceKind.YOUTUBE,
    val label: String = "",
    val enabled: Boolean = true,
    val allowDownloads: Boolean = true,
) {
    val displayName: String get() = label.ifBlank { kind.label }
}

/** Vikify uses its built-in catalogue without configurable source providers. */
object SourceRegistry {
    private val catalogue = SourceConfig()
    private val youtube: MusicSource = YouTubeSource(catalogue)
    fun active(): List<MusicSource> = listOf(youtube)
    fun activeForPlayback(): List<MusicSource> = active()
    fun activeForDownload(): List<MusicSource> = active()
    fun instance(configId: String): MusicSource? = youtube.takeIf { configId == catalogue.id }
    fun config(configId: String): SourceConfig? = catalogue.takeIf { configId == it.id }
    fun trackKey(configId: String, trackId: String) = "src:$configId::$trackId"
    fun parseTrackKey(key: String): Pair<String, String>? = SourceTrackKeys.parse(key)
    fun trackUri(configId: String, trackId: String): String = Uri.Builder()
        .scheme("bitchord").authority("source")
        .appendQueryParameter("s", configId).appendQueryParameter("t", trackId)
        .build().toString()
    internal interface ConfigBacked { val config: SourceConfig }
}
