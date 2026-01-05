package com.vikify.app.vikifyui.data

import androidx.annotation.DrawableRes
import com.vikify.app.R

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    @DrawableRes val artwork: Int = R.drawable.artwork_placeholder,
    val remoteArtworkUrl: String? = null,
    val tracks: List<Track> = emptyList(),
    val originalBackendRef: Any? = null
)
