package com.music.vikify.data.spotify

import android.content.Context
import android.content.SharedPreferences
import com.music.vikify.data.firebase.FirebaseSyncManager
import com.music.vikify.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class LocalPlaylist(
    val id: String,
    val title: String,
    val songs: List<Song>,
) {
    val browseId: String get() = "local:playlist:$id"
}

object LocalPlaylistStore {
    private const val PREF_NAME = "vikify_local_playlists"
    private const val LEGACY_PREF_NAME = "bitchord_local_playlists"
    private const val KEY_PLAYLISTS = "playlists_json"

    private lateinit var prefs: SharedPreferences

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // Check for legacy data migration
        if (!prefs.contains(KEY_PLAYLISTS)) {
            val legacy = context.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE)
            val legacyData = legacy.getString(KEY_PLAYLISTS, null)
            if (legacyData != null) {
                prefs.edit().putString(KEY_PLAYLISTS, legacyData).apply()
            }
        }
        load()
    }

    private fun load() {
        if (!this::prefs.isInitialized) return
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return
        runCatching {
            val list = mutableListOf<LocalPlaylist>()
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val songsArray = obj.getJSONArray("songs")
                val songs = mutableListOf<Song>()
                for (j in 0 until songsArray.length()) {
                    val sObj = songsArray.getJSONObject(j)
                    val song = Song(
                        videoId = sObj.optString("videoId", ""),
                        title = sObj.optString("title", ""),
                        artist = sObj.optString("artist", ""),
                        thumbnailUrl = sObj.optString("thumbnailUrl", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        durationText = sObj.optString("durationText", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        artistId = sObj.optString("artistId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        albumId = sObj.optString("albumId", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        albumName = sObj.optString("albumName", null).takeIf { !it.isNullOrBlank() && it != "null" },
                        isVideo = sObj.optBoolean("isVideo", false),
                    )
                    songs.add(song)
                }
                list.add(LocalPlaylist(id, title, songs))
            }
            _playlists.value = list
        }
    }

    fun savePlaylist(title: String, songs: List<Song>): LocalPlaylist {
        val id = "sp_local_" + System.currentTimeMillis()
        val playlist = LocalPlaylist(id = id, title = title, songs = songs)
        val updated = listOf(playlist) + _playlists.value.filterNot { it.id == id }
        _playlists.value = updated
        persist(updated)
        return playlist
    }

    fun deletePlaylist(id: String) {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        val updated = _playlists.value.filterNot { it.id == cleanId || it.id == id || it.browseId == id }
        _playlists.value = updated
        persist(updated)
        FirebaseSyncManager.deleteRemotePlaylist(cleanId)
    }

    fun renamePlaylist(id: String, newTitle: String) {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        val updated = _playlists.value.map {
            if (it.id == cleanId || it.id == id || it.browseId == id) {
                it.copy(title = newTitle)
            } else it
        }
        _playlists.value = updated
        persist(updated)
    }

    fun getPlaylist(id: String): LocalPlaylist? {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        return _playlists.value.firstOrNull { it.id == cleanId || it.id == id || it.browseId == id }
    }

    fun addSongToPlaylist(id: String, song: Song) {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        val updated = _playlists.value.map {
            if (it.id == cleanId || it.id == id || it.browseId == id) {
                if (it.songs.none { s -> s.videoId == song.videoId }) {
                    it.copy(songs = it.songs + song)
                } else it
            } else it
        }
        _playlists.value = updated
        persist(updated)
    }

    fun removeSongFromPlaylist(id: String, videoId: String) {
        val cleanId = id.removePrefix("local:playlist:").removePrefix("VL")
        val updated = _playlists.value.map {
            if (it.id == cleanId || it.id == id || it.browseId == id) {
                it.copy(songs = it.songs.filterNot { s -> s.videoId == videoId })
            } else it
        }
        _playlists.value = updated
        persist(updated)
    }

    fun mergeRemote(remoteList: List<LocalPlaylist>) {
        val current = _playlists.value.toMutableList()
        var changed = false
        for (remote in remoteList) {
            val exists = current.any { it.id == remote.id }
            if (!exists) {
                current.add(remote)
                changed = true
            }
        }
        if (changed) {
            _playlists.value = current
            persistInternal(current, syncToCloud = false)
        }
    }

    private fun persist(list: List<LocalPlaylist>) {
        persistInternal(list, syncToCloud = true)
    }

    private fun persistInternal(list: List<LocalPlaylist>, syncToCloud: Boolean) {
        if (!this::prefs.isInitialized) return
        runCatching {
            val jsonArray = JSONArray()
            for (playlist in list) {
                val pObj = JSONObject()
                pObj.put("id", playlist.id)
                pObj.put("title", playlist.title)
                val songsArray = JSONArray()
                for (song in playlist.songs) {
                    val sObj = JSONObject()
                    sObj.put("videoId", song.videoId)
                    sObj.put("title", song.title)
                    sObj.put("artist", song.artist)
                    sObj.put("thumbnailUrl", song.thumbnailUrl)
                    sObj.put("durationText", song.durationText)
                    sObj.put("artistId", song.artistId)
                    sObj.put("albumId", song.albumId)
                    sObj.put("albumName", song.albumName)
                    sObj.put("isVideo", song.isVideo)
                    songsArray.put(sObj)
                }
                pObj.put("songs", songsArray)
                jsonArray.put(pObj)
            }
            prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).apply()
            if (syncToCloud) {
                FirebaseSyncManager.syncPlaylists(list)
            }
        }
    }
}
