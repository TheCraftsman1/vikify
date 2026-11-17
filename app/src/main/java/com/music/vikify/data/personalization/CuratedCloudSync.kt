package com.music.vikify.data.personalization

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.music.vikify.VikifyApplication
import com.music.vikify.data.firebase.FirebaseSyncManager
import com.music.vikify.data.stats.ReplayCloudSync
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

enum class CuratedSyncStatus { OFF, SYNCING, SAVED, RETRYING, PROFILE_REQUIRED, ACCOUNT_MISMATCH }

/** Vikify data only: this never creates, replaces or edits a YouTube Music playlist. */
object CuratedCloudSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _status = MutableStateFlow(CuratedSyncStatus.OFF)
    val status = _status.asStateFlow()
    private val _restored = MutableStateFlow(0L)
    val restored = _restored.asStateFlow()
    private lateinit var directory: File
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var writer: String

    private data class Identity(val uid: String, val profile: String, val base: String) {
        val fileKey get() = hash("$uid\n$profile\n$base")
    }

    fun init(context: Context) {
        directory = File(context.noBackupFilesDir, "curated_cloud")
        prefs = context.getSharedPreferences("vikify_curated_sync", Context.MODE_PRIVATE)
        check(directory.isDirectory || directory.mkdirs())
        val writerFile = File(directory, "installation")
        writer = if (writerFile.isFile) writerFile.readText() else UUID.randomUUID().toString().also {
            writerFile.outputStream().use { out -> out.write(it.toByteArray(Charsets.UTF_8)); out.fd.sync() }
        }
        scope.launch {
            ReplayCloudSync.enabled.collectLatest { enabled ->
                _status.value = CuratedSyncStatus.OFF
                if (enabled) while (isActive) {
                    var retrySoon = false
                    try {
                        val identity = identity()
                        when {
                            identity == null -> _status.value = CuratedSyncStatus.PROFILE_REQUIRED
                            !bind(identity) -> _status.value = CuratedSyncStatus.ACCOUNT_MISMATCH
                            else -> {
                                _status.value = CuratedSyncStatus.SYNCING
                                val complete = withTimeout(25_000) { synchronize(identity) }
                                retrySoon = !complete
                                if (current(identity) && complete) _status.value = CuratedSyncStatus.SAVED
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        // A network timeout is retryable; account/consent cancellation must stop immediately.
                        if (cancelled is kotlinx.coroutines.TimeoutCancellationException) {
                            _status.value = CuratedSyncStatus.RETRYING
                        } else throw cancelled
                    } catch (failure: Exception) {
                        _status.value = CuratedSyncStatus.RETRYING
                        Log.w("CuratedCloudSync", "Curated backup unavailable (${failure.javaClass.simpleName})")
                    }
                    delay(if (retrySoon) 1_000 else 30_000)
                }
            }
        }
    }

    private fun identity(): Identity? {
        val uid = FirebaseSyncManager.userId.value ?: return null
        if (!ReplayCloudSync.isEnabledFor(uid)) return null
        val auth = VikifyApplication.authStore
        val session = auth.activeSession
        val stable = if (session == null) "vikify" else {
            val profile = session.profiles.firstOrNull { it.profileId == auth.activeProfileId }
                ?: session.profiles.firstOrNull { it.profileId == session.activeProfileId }
                ?: return null
            // Cookies and cookie hashes are neither portable identities nor cloud credentials.
            val channel = profile.pageId?.takeIf { it.isNotBlank() }
                ?: profile.dataSyncId?.takeIf { it.isNotBlank() } ?: return null
            "youtube:$channel"
        }
        return Identity(uid, hash(stable), AndroidLocalMixes.baseScope())
    }

    private fun current(selected: Identity) = identity() == selected

    /** A local profile already restored for one Google identity cannot leak into another. */
    private fun bind(identity: Identity): Boolean {
        val key = "owner:${hash(identity.base)}"
        val previous = prefs.getString(key, null)
        return previous == identity.uid || (previous == null && prefs.edit().putString(key, identity.uid).commit())
    }

    private suspend fun capture(base: String, learning: String): Map<String, String> = buildMap {
        AndroidLocalMixes.cloudResetMarker(base)?.let { put("reset", it) }
        AndroidLocalMixes.home.store.exportCloud(learning).forEach { (k, v) -> put("home/$k", v) }
        AndroidLocalMixes.evidenceStore.exportCloud(learning).forEach { (k, v) -> put("evidence/$k", v) }
        AndroidLocalMixes.copies.exportCloud(base).forEach { (k, v) -> put("copies/$k", v) }
        checkNotNull(LocalMixPlatform.repository).exportCloud(learning).forEach { (k, v) -> put("mixes/$k", v) }
    }

    private suspend fun synchronize(identity: Identity): Boolean {
        check(current(identity)) { "Listener changed" }
        check(AndroidLocalMixes.resumeCleanup()) { "Personalization cleanup is pending" }
        val learning = AndroidLocalMixes.scope()
        val local = capture(identity.base, learning)
        check(current(identity) && learning == AndroidLocalMixes.scope()) { "Listener changed" }
        val epoch = local["reset"]?.let { json.parseToJsonElement(it).jsonObject.getValue("cutoff").jsonPrimitive.long } ?: 0
        var replica = read(identity)?.observe(local, epoch, writer, System.currentTimeMillis())
            ?: CuratedCloudReplica.bootstrap(local, epoch, writer)
        write(identity, replica) // Offline edits and deletions are durable before contacting Firestore.
        val document = FirebaseFirestore.getInstance().collection("users").document(identity.uid)
            .collection("curatedProfiles").document(identity.profile)
        val pending = replica.manifest
        val merged = FirebaseFirestore.getInstance().runTransaction { transaction ->
            check(current(identity)) { "Listener changed" }
            val snapshot = transaction.get(document)
            val remote = if (snapshot.exists()) {
                val blob = checkNotNull(snapshot.getBlob("bucket")) { "Unsupported curated backup" }
                decode(blob.toBytes())
            } else CuratedCloudManifest()
            val result = remote.mergeReplica(pending, System.currentTimeMillis())
            check(current(identity)) { "Listener changed" }
            if (result != remote) transaction.set(document, mapOf("bucket" to Blob.fromBytes(encode(result))))
            result
        }.await()
        check(current(identity) && learning == AndroidLocalMixes.scope()) { "Listener changed" }
        val desired = merged.values()
        replica = replica.copy(manifest = merged, restoreTarget = desired)
        write(identity, replica)
        if (!AndroidLocalMixes.adoptCloudReset(identity.base, local["reset"], desired["reset"])) return false
        if (learning != AndroidLocalMixes.scope()) {
            // The next pass captures anything recorded in the new generation before importing.
            _restored.update { it + 1 }
            return false
        }
        var observed = local.toMutableMap()
        var changed = false
        var complete = true
        suspend fun apply(prefix: String, operation: suspend (Map<String, String>, Map<String, String>) -> Boolean) {
            check(current(identity) && learning == AndroidLocalMixes.scope()) { "Listener changed" }
            fun Map<String, String>.part() = filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
            val expected = local.part()
            val target = desired.part()
            if (operation(expected, target)) {
                observed.keys.removeAll { it.startsWith(prefix) }
                target.forEach { (key, value) -> observed["$prefix$key"] = value }
                changed = changed || expected != target
            } else complete = false
        }
        apply("evidence/", { a, b -> AndroidLocalMixes.evidenceStore.applyCloud(learning, a, b) })
        apply("home/", { a, b -> AndroidLocalMixes.home.store.applyCloud(learning, a, b) })
        apply("copies/", { a, b -> AndroidLocalMixes.copies.applyCloud(identity.base, a, b) })
        apply("mixes/", { a, b -> checkNotNull(LocalMixPlatform.repository).applyCloud(learning, a, b) })
        write(identity, replica.copy(observed = observed, restoreTarget = null))
        if (changed) _restored.update { it + 1 }
        return complete
    }

    private fun read(identity: Identity): CuratedCloudReplica? {
        val file = File(directory, "${identity.fileKey}.json")
        if (!file.exists()) return null
        check(file.length() <= MAX_JOURNAL_BYTES) { "Curated sync journal is too large" }
        return json.decodeFromString(CuratedCloudReplica.serializer(), file.readText()).also { it.manifest.validated() }
    }

    private fun write(identity: Identity, replica: CuratedCloudReplica) {
        check(directory.isDirectory || directory.mkdirs())
        val bytes = json.encodeToString(CuratedCloudReplica.serializer(), replica).toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_JOURNAL_BYTES) { "Curated sync journal is too large" }
        val temp = File(directory, "${identity.fileKey}.tmp")
        val target = File(directory, "${identity.fileKey}.json")
        try {
            temp.outputStream().use { it.write(bytes); it.fd.sync() }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }

    private fun encode(manifest: CuratedCloudManifest): ByteArray {
        val raw = json.encodeToString(CuratedCloudManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)
        check(raw.size <= MAX_DECODED_BYTES) { "Curated backup is too large" }
        val bytes = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(raw) } }.toByteArray()
        check(bytes.size <= 950_000) { "Curated backup is too large" }
        return bytes
    }

    private fun decode(bytes: ByteArray): CuratedCloudManifest {
        check(bytes.size <= 950_000)
        val raw = GZIPInputStream(bytes.inputStream()).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count != -1) {
                check(out.size() + count <= MAX_DECODED_BYTES) { "Curated backup is too large" }
                out.write(buffer, 0, count)
                count = input.read(buffer)
            }
            out.toString("UTF-8")
        }
        return json.decodeFromString(CuratedCloudManifest.serializer(), raw).validated()
    }

    private const val MAX_DECODED_BYTES = 24_000_000
    private const val MAX_JOURNAL_BYTES = MAX_DECODED_BYTES * 4
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
