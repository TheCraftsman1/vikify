package com.music.vikify.data.covers

import android.content.Context
import java.security.MessageDigest

/** Artwork for YouTube playlists is kept on this device, scoped to the music profile. */
object PlaylistCoverStore {
    private fun key(listener: String, playlistId: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$listener:${playlistId.removePrefix("VL")}".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun get(context: Context, listener: String?, playlistId: String): String? {
        if (listener == null) return null
        return context.getSharedPreferences("vikify_playlist_covers", Context.MODE_PRIVATE)
            .getString(key(listener, playlistId), null)?.takeIf { CoverUri.decode(it) != null }
    }

    fun save(context: Context, listener: String?, playlistId: String, title: String, draft: String?) {
        if (listener == null) return
        val saved = savedPlaylistCover(playlistId, title, draft) ?: return
        context.getSharedPreferences("vikify_playlist_covers", Context.MODE_PRIVATE).edit()
            .putString(key(listener, playlistId), saved).apply()
    }

    fun remove(context: Context, listener: String?, playlistId: String) {
        if (listener == null) return
        context.getSharedPreferences("vikify_playlist_covers", Context.MODE_PRIVATE).edit()
            .remove(key(listener, playlistId)).apply()
    }
}

internal fun savedPlaylistCover(playlistId: String, title: String, draft: String?): String? {
    val spec = draft?.let(CoverUri::decode)?.first ?: return null
    return CoverUri.encode(spec.copy(collectionId = "playlist:${playlistId.removePrefix("VL")}",
        title = CoverCatalog.title(title)), 720)
}
