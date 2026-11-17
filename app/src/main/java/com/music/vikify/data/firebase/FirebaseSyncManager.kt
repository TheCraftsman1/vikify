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
import com.google.firebase.firestore.Source
import com.music.vikify.BuildConfig
import com.music.vikify.data.LikeState
import com.music.vikify.data.model.Song
import com.music.vikify.data.spotify.LocalPlaylist
import com.music.vikify.data.spotify.LocalPlaylistStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException

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

    private val _lastSyncedTime = MutableStateFlow<Long?>(null)
    val lastSyncedTime = _lastSyncedTime.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (_userId.value != user?.uid) {
            _syncState.value = "Idle"
            _lastSyncedTime.value = null
        }
        _userId.value = user?.uid
        _isAnonymous.value = user?.isAnonymous ?: true
        _userEmail.value = user?.email
        _userDisplayName.value = user?.displayName
    }

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
                fbAuth.addAuthStateListener(authStateListener)

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
                withTimeoutOrNull(10_000L) {
                    updateUserDeviceMetadata(current?.uid, current?.email, current?.displayName)
                }

                // Initial pull: restore remote playlists and favorites
                if (fbAuth.currentUser?.uid == current?.uid) pullRemoteAll()
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
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = withTimeout(25_000L) { fbAuth.signInWithCredential(credential).await() }
            val user = result.user ?: error("No user returned from Firebase sign-in")

            if (_userId.value != user.uid) {
                _syncState.value = "Idle"
                _lastSyncedTime.value = null
            }
            _userId.value = user.uid
            _isAnonymous.value = false
            _userEmail.value = user.email ?: email
            _userDisplayName.value = user.displayName ?: displayName

            // A slow cloud write must not keep a successful account picker spinning.
            scope.launch {
                withTimeoutOrNull(10_000L) { updateUserDeviceMetadata(user.uid, user.email, user.displayName) }
                if (fbAuth.currentUser?.uid == user.uid) pullRemoteAll()
            }
            Result.success(user.uid)
        } catch (_: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Google sign-in timed out. Check your connection and try again."))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Google sign-in failed (${error.javaClass.simpleName})")
            Result.failure(error)
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
                _syncState.value = "Synced"
                _lastSyncedTime.value = System.currentTimeMillis()
            }.onFailure { e ->
                Log.w(TAG, "Failed to sync playlists to Firestore: ${e.message}")
                _syncState.value = "Sync error"
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

    private fun isCurrentAccount(uid: String): Boolean =
        _userId.value == uid && auth?.currentUser?.uid == uid

    private fun requireCurrentAccount(uid: String) {
        if (!isCurrentAccount(uid)) throw CancellationException("Firebase account changed")
    }

    private fun reportPullFailure(uid: String, error: Exception) {
        if (error is CancellationException && error !is TimeoutCancellationException) throw error
        if (!isCurrentAccount(uid)) return
        Log.w(TAG, "Cloud restore failed (${error.javaClass.simpleName})", error)
        _syncState.value = "Sync error"
    }

    /** Direct callers retain a safe API; the combined restore uses throwing reads below. */
    suspend fun pullRemotePlaylists() {
        val uid = _userId.value ?: return
        val db = firestore ?: return
        try {
            requireCurrentAccount(uid)
            _syncState.value = "Pulling remote playlists..."
            val playlists = readRemotePlaylists(uid, db)
            requireCurrentAccount(uid)
            if (playlists.isNotEmpty()) LocalPlaylistStore.mergeRemote(playlists)
        } catch (error: Exception) {
            reportPullFailure(uid, error)
        }
    }

    /** Restore only after the response still belongs to the active account. */
    suspend fun pullRemoteFavorites(): Set<String> {
        val uid = _userId.value ?: return emptySet()
        val db = firestore ?: return emptySet()
        return try {
            val favorites = readRemoteFavorites(uid, db)
            requireCurrentAccount(uid)
            if (favorites.isNotEmpty()) LikeState.seedLiked(favorites)
            favorites
        } catch (error: Exception) {
            reportPullFailure(uid, error)
            emptySet()
        }
    }

    private suspend fun readRemotePlaylists(uid: String, db: FirebaseFirestore): List<LocalPlaylist> {
        requireCurrentAccount(uid)
        // SERVER prevents an offline cache response from being reported as a completed cloud sync.
        val snapshot = withTimeout(20_000L) {
            db.collection("users").document(uid).collection("playlists").get(Source.SERVER).await()
        }
        requireCurrentAccount(uid)
        return snapshot.documents.map { doc ->
            val songs = (doc.get("songs") as? List<*>).orEmpty().mapNotNull { raw ->
                val song = raw as? Map<*, *> ?: return@mapNotNull null
                val videoId = song["videoId"] as? String ?: return@mapNotNull null
                if (videoId.isBlank()) return@mapNotNull null
                fun text(key: String) = (song[key] as? String)?.takeIf { it.isNotBlank() }
                Song(
                    videoId = videoId,
                    title = text("title").orEmpty(),
                    artist = text("artist").orEmpty(),
                    thumbnailUrl = text("thumbnailUrl"),
                    durationText = text("durationText"),
                    artistId = text("artistId"),
                    albumId = text("albumId"),
                    albumName = text("albumName"),
                    isVideo = song["isVideo"] as? Boolean ?: false,
                )
            }
            LocalPlaylist(doc.getString("id") ?: doc.id, doc.getString("title") ?: "Untitled", songs)
        }
    }

    private suspend fun readRemoteFavorites(uid: String, db: FirebaseFirestore): Set<String> {
        requireCurrentAccount(uid)
        val snapshot = withTimeout(20_000L) {
            db.collection("users").document(uid).collection("library")
                .document("favorites").get(Source.SERVER).await()
        }
        requireCurrentAccount(uid)
        return (snapshot.get("likedVideoIds") as? List<*>).orEmpty()
            .mapNotNull { (it as? String)?.takeIf(String::isNotBlank) }.toSet()
    }

    /** A successful status means both server reads completed for this same account. */
    fun pullRemoteAll() {
        val uid = _userId.value ?: return
        val db = firestore ?: return
        scope.launch {
            try {
                requireCurrentAccount(uid)
                _syncState.value = "Syncing cloud data..."
                val playlists = readRemotePlaylists(uid, db)
                val favorites = readRemoteFavorites(uid, db)
                requireCurrentAccount(uid)
                if (playlists.isNotEmpty()) LocalPlaylistStore.mergeRemote(playlists)
                if (favorites.isNotEmpty()) LikeState.seedLiked(favorites)
                requireCurrentAccount(uid)
                _syncState.value = "Synced"
                _lastSyncedTime.value = System.currentTimeMillis()
            } catch (error: Exception) {
                reportPullFailure(uid, error)
            }
        }
    }

    /**
     * Sync favorite/liked songs to Firestore
     */
    fun syncFavorite(videoId: String, isLiked: Boolean, song: Song? = null) {
        val uid = _userId.value ?: return
        val db = firestore ?: return

        scope.launch {
            runCatching {
                val favDoc = db.collection("users").document(uid).collection("library").document("favorites")
                val updates = mutableMapOf<String, Any>(
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (isLiked) {
                    updates["likedVideoIds"] = FieldValue.arrayUnion(videoId)
                    if (song != null) {
                        updates["songs.$videoId"] = mapOf(
                            "videoId" to song.videoId,
                            "title" to song.title,
                            "artist" to song.artist,
                            "thumbnailUrl" to (song.thumbnailUrl ?: ""),
                            "durationText" to (song.durationText ?: "")
                        )
                    }
                } else {
                    updates["likedVideoIds"] = FieldValue.arrayRemove(videoId)
                    updates["songs.$videoId"] = FieldValue.delete()
                }
                favDoc.set(updates, SetOptions.merge()).await()
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

    /**
     * Store Spotify connection state in Cloud Firestore
     */
    fun syncSpotifyStatus(connected: Boolean) {
        val uid = _userId.value ?: return
        val db = firestore ?: return
        scope.launch {
            runCatching {
                db.collection("users").document(uid).set(
                    mapOf(
                        "spotifyConnected" to connected,
                        "spotifyUpdatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            }
        }
    }

    /** Music profile metadata must never replace the Google identity verified by Firebase Auth. */
    fun syncYouTubeProfile(displayName: String?, handle: String?) {
        val uid = _userId.value ?: return
        val db = firestore ?: return
        scope.launch {
            runCatching {
                val map = mutableMapOf<String, Any>("lastActive" to FieldValue.serverTimestamp())
                if (!displayName.isNullOrBlank()) {
                    map["youtubeDisplayName"] = displayName
                }
                if (!handle.isNullOrBlank()) {
                    map["youtubeHandle"] = handle
                }
                db.collection("users").document(uid).set(map, SetOptions.merge()).await()
            }
        }
    }

    /**
     * Sign out of Firebase Auth and clear local user session state
     */
    fun signOut() {
        auth?.signOut()
        _userId.value = null
        _isAnonymous.value = true
        _userEmail.value = null
        _userDisplayName.value = null
        _syncState.value = "Idle"
        _lastSyncedTime.value = null
    }
}
