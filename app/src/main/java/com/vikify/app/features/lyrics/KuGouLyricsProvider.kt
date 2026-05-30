package com.vikify.app.features.lyrics

import android.content.Context
import com.vikify.app.core.constants.EnableKugouKey
import com.vikify.app.core.utils.dataStore
import com.vikify.app.core.utils.get
import com.zionhuang.kugou.KuGou

object KuGouLyricsProvider : LyricsProvider {
    override val name = "Kugou"
    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableKugouKey] ?: false // Disabled by default - Chinese lyrics service

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): Result<String> =
        KuGou.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(id: String, title: String, artist: String, duration: Int, callback: (String) -> Unit) {
        KuGou.getAllPossibleLyricsOptions(title, artist, duration, callback)
    }
}
