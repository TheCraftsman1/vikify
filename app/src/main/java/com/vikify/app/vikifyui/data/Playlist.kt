package com.vikify.app.vikifyui.data

import androidx.annotation.DrawableRes
import com.vikify.app.R

data class Playlist(
    val id: String,
    val name: String,
    @DrawableRes val artwork: Int = R.drawable.artwork_placeholder,
    val trackCount: Int = 0
)
