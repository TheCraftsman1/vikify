package com.music.vikify.data.firebase

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.music.vikify.BuildConfig
import com.music.vikify.data.model.Song
import com.music.vikify.data.spotify.LocalPlaylist
import com.music.vikify.data.spotify.LocalPlaylistStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null

    private val _userId = MutableStateFlow<String?>(null)
    val userId = _userId.asStateFlow()

    private val _isAnonymous = MutableStateFlow(true)
    val isAnonymous = _isAnonymous.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail = _userEmail.asStateFlow()

    private val _userDisplayName = MutableStateFlow<String?>(null)
    val userDisplayName = _userDisplayName.asStateFlow()

    private val _syncState = MutableStateFlow("Idle")
    val syncState = _syncState.asStateFlow()

    fun init(context: Context) {
        scope.launch {
            runCatching {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                val fbAuth = FirebaseAuth.getInstance()
                val db = FirebaseFirestore.getInstance()

                auth = fbAuth
                firestore = db

                // Sign in anonymously if no user session exists
                if (fbAuth.currentUser == null) {
                    Log.d(TAG, "No user signed in. Attempting anonymous sign-in...")
                    fbAuth.signInAnonymously().await()
                }

                val current = fbAuth.currentUser
                _userId.value = current?.uid
                _isAnonymous.value = current?.isAnonymous ?: true
                _userEmail.value = current?.email
                _userDisplayName.value = current?.displayName
                Log.d(TAG, "Firebase authenticated. UID: ${current?.uid}, isAnonymous: ${current?.isAnonymous}")

                // Record user device session metadata
                updateUserDeviceMetadata(current?.uid, current?.email, current?.displayName)

                // Initial pull: restore remote playlists and sync with local
                pullRemotePlaylists()
            }.onFailure { e ->
                Log.w(TAG, "Firebase initialization failed: ${e.message}", e)
            }
        }
    }

    private suspend fun updateUserDeviceMetadata(
        uid: String?,
        email: String? = null,
        displayName: String? = null
    ) {
        if (uid.isNullOrBlank()) return
        val db = firestore ?: return
        runCatching {
            val userDoc = db.collection("users").document(uid)
            val metadata = hashMapOf(
                "lastActive" to FieldValue.serverTimestamp(),
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "androidVersion" to Build.VERSION.RELEASE,
                "appVersion" to BuildConfig.VERSION_NAME,
                "isAnonymous" to (auth?.currentUser?.isAnonymous ?: true)
            )
            email?.let { metadata["email"] = it }
            displayName?.let { metadata["displayName"] = it }
            userDoc.set(metadata, SetOptions.merge()).await()
        }
    }

    /**
     * Authenticates with Firebase using a Google ID token from device account selection.
     * Links device email, updates profile, and pulls cloud playlists.
     */
    suspend fun signInWithGoogleCredential(
        idToken: String,
        email: String? = null,
        displayName: String? = null
    ): Result<String> {
        val fbAuth = auth ?: FirebaseAuth.getInstance()
        return runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = fbAuth.signInWithCredential(credential).await()
            val user = result.user ?: error("No user returned from Firebase sign-in")

            _userId.value = user.uid
            _isAnonymous.value = false
            _userEmail.value = user.email ?: email
            _userDisplayName.value = user.displayName ?: displayName

            Log.d(TAG, "Google Sign-In successful. UID: ${user.uid}, email: ${user.email}")
            updateUserDeviceMetadata(user.uid, user.email, user.displayName)
            pullRemotePlaylists()
            user.uid
        }.onFailure { e ->
            Log.e(TAG, "signInWithGoogleCredential failed: ${e.message}", e)
        }
    }

    /**
     * Uploads the local playlists to Cloud Firestore under users/{uid}/playlists/{playlistId}
     */
    fun syncPlaylists(playlists: List<LocalPlaylist>) {
        val uid = _userId.value ?: return
        val db = firestore ?: return

        scope.launch {
            runCatching {
                _syncState.value = "Syncing playlists..."
                val userPlaylistsCol = db.collection("users").document(uid).collection("playlists")

                for (p in playlists) {
                    val doc = userPlaylistsCol.document(p.id)
                    val songsData = p.songs.map { song ->
                        mapOf(
                            "videoId" to song.videoId,
                            "title" to song.title,
                            "artist" to song.artist,
                            "thumbnailUrl" to (song.thumbnailUrl ?: ""),
                            "durationText" to (song.durationText ?: ""),
                            "artistId" to (song.artistId ?: ""),
                            "albumId" to (song.albumId ?: ""),
                            "albumName" to (song.albumName ?: ""),
                            "isVideo" to song.isVideo
                        )
                    }
                    val data = hashMapOf(
                        "id" to p.id,
                        "title" to p.title,
                        "songs" to songsData,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    doc.set(data, SetOptions.merge()).await()
                }
                _syncState.value = "Playlists synced"
            }.onFailure { e ->
                Log.w(TAG, "Failed to sync playlists to Firestore: ${e.message}")
                _syncState.value = "Sync error: ${e.message}"
            }
        }
    }

    /**
     * Delete a playlist from Cloud Firestore
     */
    fun deleteRemotePlaylist(playlistId: String) {
        val uid = _userId.value ?: return
        val db = firestore ?: return
        val cleanId = playlistId.removePrefix("local:playlist:").removePrefix("VL")

        scope.launch {
            runCatching {
                db.collection("users")
                    .document(uid)
                    .collection("playlists")
                    .document(cleanId)
                    .delete()
                    .await()
            }
        }
    }

    /**
     * Pull remote playlists from Firestore and merge with local playlists
     */
    fun pullRemotePlaylists() {
        val uid = _userId.value ?: return
        val db = firestore ?: return

        scope.launch {
            runCatching {
                _syncState.value = "Pulling remote playlists..."
                val snapshot = db.collection("users")
                    .document(uid)
                    .collection("playlists")
                    .get()
                    .await()

                val remoteList = mutableListOf<LocalPlaylist>()
                for (doc in snapshot.documents) {
                    val id = doc.getString("id") ?: doc.id
                    val title = doc.getString("title") ?: "Untitled"
                    val songsRaw = doc.get("songs") as? List<Map<String, Any?>> ?: emptyList()

                    val songs = songsRaw.map { s ->
                        Song(
                            videoId = s["videoId"] as? String ?: "",
                            title = s["title"] as? String ?: "",
                            artist = s["artist"] as? String ?: "",
                            thumbnailUrl = (s["thumbnailUrl"] as? String).takeIf { !it.isNullOrBlank() },
                            durationText = (s["durationText"] as? String).takeIf { !it.isNullOrBlank() },
                            artistId = (s["artistId"] as? String).takeIf { !it.isNullOrBlank() },
                            albumId = (s["albumId"] as? String).takeIf { !it.isNullOrBlank() },
                            albumName = (s["albumName"] as? String).takeIf { !it.isNullOrBlank() },
                            isVideo = s["isVideo"] as? Boolean ?: false
                        )
                    }
                    remoteList.add(LocalPlaylist(id, title, songs))
                }

                if (remoteList.isNotEmpty()) {
                    LocalPlaylistStore.mergeRemote(remoteList)
                }
                _syncState.value = "Synced"
            }.onFailure { e ->
                Log.w(TAG, "Failed to pull remote playlists: ${e.message}")
            }
        }
    }

    /**
     * Sync favorite/liked songs to Firestore
     */
    fun syncFavorite(videoId: String, isLiked: Boolean) {
        val uid = _userId.value ?: return
        val db = firestore ?: return

        scope.launch {
            runCatching {
                val favDoc = db.collection("users").document(uid).collection("library").document("favorites")
                val update = if (isLiked) {
                    mapOf("likedVideoIds" to FieldValue.arrayUnion(videoId), "updatedAt" to FieldValue.serverTimestamp())
                } else {
                    mapOf("likedVideoIds" to FieldValue.arrayRemove(videoId), "updatedAt" to FieldValue.serverTimestamp())
                }
                favDoc.set(update, SetOptions.merge()).await()
            }
        }
    }

    /**
     * Sync track feedback (boosted or hidden from mixes)
     */
    fun syncTrackFeedback(videoId: String, isMore: Boolean, isHidden: Boolean) {
        val uid = _userId.value ?: return
        val db = firestore ?: return

        scope.launch {
            runCatching {
                val feedbackDoc = db.collection("users").document(uid).collection("personalization").document("feedback")
                val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
                if (isMore) {
                    updates["more"] = FieldValue.arrayUnion(videoId)
                    updates["hidden"] = FieldValue.arrayRemove(videoId)
                } else if (isHidden) {
                    updates["hidden"] = FieldValue.arrayUnion(videoId)
                    updates["more"] = FieldValue.arrayRemove(videoId)
                } else {
                    updates["more"] = FieldValue.arrayRemove(videoId)
                    updates["hidden"] = FieldValue.arrayRemove(videoId)
                }
                feedbackDoc.set(updates, SetOptions.merge()).await()
            }
        }
    }
}
