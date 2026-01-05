package com.vikify.app.vikifyui.data

import com.vikify.app.db.entities.Song
import com.zionhuang.innertube.models.SongItem

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val remoteArtworkUrl: String?,
    val duration: Long = -1L,
    val artwork: Any? = null,
    val originalBackendRef: Any? = null,
    val youtubeId: String? = null,
    val spotifyId: String? = null,
    val spotifyTitle: String? = null,
    val spotifyArtist: String? = null
)

fun Track.toMediaMetadata() = com.vikify.app.models.MediaMetadata(
    id = id,
    title = title,
    artists = listOf(com.vikify.app.models.MediaMetadata.Artist(id = null, name = artist)),
    duration = (duration / 1000).toInt(),
    thumbnailUrl = remoteArtworkUrl,
    genre = null
)
