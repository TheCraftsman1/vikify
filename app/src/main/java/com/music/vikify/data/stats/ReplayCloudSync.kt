package com.music.vikify.data.stats

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.music.vikify.data.firebase.FirebaseSyncManager
import java.io.ByteArrayOutputStream
import java.time.YearMonth
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/** Each installation publishes cumulative snapshots. Restored snapshots are read-only inputs. */
object ReplayCloudSync {
    private const val MAX_DECODED_BYTES = 8 * 1024 * 1024
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val consent = MutableStateFlow<String?>(null)
    private val _remote = MutableStateFlow<List<StoredBucket>>(emptyList())
    internal val remote = _remote.asStateFlow()
    private val _failed = MutableStateFlow(false)
    val failed = _failed.asStateFlow()
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var installation: String

    val account = combine(FirebaseSyncManager.userId, FirebaseSyncManager.isAnonymous) { uid, guest ->
        uid.takeUnless { guest }
    }.distinctUntilChanged()

    val enabled = combine(account, consent) { uid, allowed -> uid != null && uid == allowed }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("vikify_replay_sync", Context.MODE_PRIVATE)
        installation = prefs.getString("installation", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installation", it).apply()
        }
        consent.value = prefs.getString("account", null)
        scope.launch {
            combine(account, consent) { uid, allowed -> uid?.takeIf { it == allowed } }
                .distinctUntilChanged()
                .collectLatest { uid ->
                    _remote.value = emptyList()
                    _failed.value = false
                    if (uid != null) sync(uid)
                }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val uid = FirebaseSyncManager.userId.value
        if (enabled && (uid == null || FirebaseSyncManager.isAnonymous.value)) return
        val allowed = uid.takeIf { enabled }
        prefs.edit().putString("account", allowed).apply()
        consent.value = allowed
    }

    private fun allowed(uid: String) = consent.value == uid &&
        FirebaseSyncManager.userId.value == uid && !FirebaseSyncManager.isAnonymous.value

    internal fun isEnabledFor(uid: String): Boolean = allowed(uid)

    private suspend fun sync(uid: String) = kotlinx.coroutines.coroutineScope {
        val collection = FirebaseFirestore.getInstance().collection("users")
            .document(uid).collection("replayMonths")
        val readFailed = MutableStateFlow(false)
        launch {
            callbackFlow {
                val listener = collection.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (allowed(uid)) trySend(snapshot to error)
                }
                awaitClose { listener.remove() }
            }.collect { (snapshot, error) ->
                readFailed.value = error != null
                if (error != null) _failed.value = true
                if (snapshot != null && allowed(uid)) {
                    val oldest = YearMonth.now().minusMonths(35)
                    _remote.value = snapshot.documents.mapNotNull { doc ->
                        if (doc.id.startsWith("${installation}_")) return@mapNotNull null
                        val month = runCatching { YearMonth.parse(doc.id.substringAfterLast('_')) }
                            .getOrNull()?.takeIf { it >= oldest } ?: return@mapNotNull null
                        val blob = doc.getBlob("bucket") ?: return@mapNotNull null
                        runCatching {
                            val compressed = blob.toBytes()
                            check(compressed.size <= 950_000) { "Replay snapshot is too large" }
                            val payload = GZIPInputStream(compressed.inputStream()).use { input ->
                                val bytes = input.readBytesBounded(MAX_DECODED_BYTES)
                                bytes.toString(Charsets.UTF_8)
                            }
                            json.decodeFromString(StoredBucket.serializer(), payload)
                                .takeIf { it.month == month.toString() }
                        }.getOrNull()
                    }
                }
            }
        }
        val uploaded = mutableMapOf<String, String>()
        while (isActive && allowed(uid)) {
            try {
                var oversized = false
                for (bucket in ListeningStats.exportAll()) {
                    val payload = json.encodeToString(StoredBucket.serializer(), bucket)
                    if (uploaded[bucket.month] == payload) continue
                    val bytes = ByteArrayOutputStream().also { buffer ->
                        GZIPOutputStream(buffer).use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                    }.toByteArray()
                    if (bytes.size > 950_000) {
                        oversized = true
                        continue
                    }
                    if (!allowed(uid)) break
                    collection.document("${installation}_${bucket.month}")
                        .set(mapOf("bucket" to Blob.fromBytes(bytes))).await()
                    uploaded[bucket.month] = payload
                }
                _failed.value = oversized || readFailed.value
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _failed.value = true
                Log.w("ReplayCloudSync", "Replay sync failed", error)
            }
            delay(60_000)
        }
    }
}

/** A compressed Firestore document must not allocate an unbounded decoded buffer. */
private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        check(count <= limit - output.size()) { "Replay snapshot expands beyond the supported size" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
