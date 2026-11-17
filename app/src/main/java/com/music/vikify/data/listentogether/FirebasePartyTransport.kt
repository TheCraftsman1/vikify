package com.music.vikify.data.listentogether

import android.os.SystemClock
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Realtime transport for ephemeral party state. Audio and personal libraries never enter this database.
 * Persistence is deliberately left disabled; transactions publish only server-confirmed state.
 */
class FirebasePartyTransport(databaseUrl: String, private val deviceId: String) {
    private val database = FirebaseDatabase.getInstance(databaseUrl)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()
    private val offset = MutableStateFlow<Long?>(null)
    @Volatile private var serverEpochAtElapsed: Long? = null
    private val observers = AtomicInteger()
    private val presenceOwners = ConcurrentHashMap<String, Any>()
    private val random = SecureRandom()
    private val monitorLock = Any()
    private var monitoring = false
    @Volatile private var screenActive = false
    val clockReady: Boolean get() = connected.value && offset.value != null
    val roundTripMs: Long get() = 0 // RTDB's offset is an estimate, not an NTP round-trip measurement.

    init { require(deviceId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid party device identity" } }

    private val connectionListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            _connected.value = snapshot.getValue(Boolean::class.java) == true
        }
        override fun onCancelled(error: DatabaseError) { _connected.value = false }
    }
    private val offsetListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val value = (snapshot.value as? Number)?.toLong()
            serverEpochAtElapsed = value?.let { System.currentTimeMillis() + it - SystemClock.elapsedRealtime() }
            offset.value = value
        }
        override fun onCancelled(error: DatabaseError) { serverEpochAtElapsed = null; offset.value = null }
    }

    fun startMonitoring() = synchronized(monitorLock) {
        if (monitoring) return@synchronized
        monitoring = true
        database.getReference(".info/connected").addValueEventListener(connectionListener)
        database.getReference(".info/serverTimeOffset").addValueEventListener(offsetListener)
    }

    fun setScreenActive(active: Boolean) {
        screenActive = active
        if (active) startMonitoring() else pauseIfIdle()
    }

    private fun pauseIfIdle() = synchronized(monitorLock) {
        if (screenActive || observers.get() > 0 || !monitoring) return@synchronized
        database.getReference(".info/connected").removeEventListener(connectionListener)
        database.getReference(".info/serverTimeOffset").removeEventListener(offsetListener)
        monitoring = false
        _connected.value = false
        offset.value = null
    }

    fun nowServerMs(): Long = serverEpochAtElapsed?.let { SystemClock.elapsedRealtime() + it } ?: System.currentTimeMillis()

    /** The Firebase account, rather than a claimed YouTube identity, authorizes every database operation. */
    fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.takeIf { user ->
        !user.isAnonymous && user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
    }?.uid

    private fun requireUid(): String = currentUid()
        ?: throw FirebasePartyFailure("not_signed_in", "Sign in with Google to listen together.")

    private fun requireCurrent(uid: String) {
        if (currentUid() != uid) throw FirebasePartyFailure("account_changed", "Your Google account changed. Join the party again.")
    }

    private suspend fun ready(uid: String) {
        requireCurrent(uid)
        startMonitoring()
        try {
            withTimeout(15_000) { combine(connected, offset) { online, clock -> online && clock != null }.first { it } }
        } catch (cancelled: CancellationException) {
            if (cancelled is kotlinx.coroutines.TimeoutCancellationException)
                throw FirebasePartyFailure("offline", "Party Room could not connect to Firebase. Check your connection.")
            throw cancelled
        }
        requireCurrent(uid)
    }

    suspend fun create(displayName: String, avatarUrl: String?, maxMembers: Int, autoplayEnabled: Boolean): PartyMembership {
        val uid = requireUid()
        ready(uid)
        repeat(8) {
            val code = (1..6).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
            try {
                val room = mutate(code, uid, allowMissing = true) { old, requestedAt ->
                    if (old != null) throw FirebasePartyFailure("code_taken", "Try creating the party again.")
                    FirebasePartyEngine.create(code, uid, displayName, avatarUrl, maxMembers, autoplayEnabled, requestedAt)
                } ?: error("Created party was empty")
                return membership(room, uid)
            } catch (failure: FirebasePartyFailure) {
                if (failure.code != "code_taken") throw failure
            }
        }
        throw FirebasePartyFailure("code_taken", "Could not reserve a party code. Try again.")
    }

    suspend fun join(code: String, displayName: String, avatarUrl: String?): PartyMembership {
        val uid = requireUid()
        val cleanCode = cleanCode(code)
        ready(uid)
        val banned = remoteCall { withTimeout(15_000) { database.getReference("partyBans/$cleanCode/$uid").get().await().value == true } }
        requireCurrent(uid)
        if (banned) throw FirebasePartyFailure("kicked", "The host removed you from this party.")
        val room = mutate(cleanCode, uid) { old, requestedAt ->
            val existing = old ?: throw FirebasePartyFailure("no_such_party", "No party with that code.")
            FirebasePartyEngine.join(existing, uid, displayName, avatarUrl, requestedAt)
        } ?: error("Joined party was empty")
        return membership(room, uid)
    }

    suspend fun leave(code: String) {
        val uid = requireUid()
        val cleanCode = cleanCode(code)
        try {
            ready(uid)
            removePresence(cleanCode, uid)
            // One Google account may listen on several devices but occupies one room seat.
            val remaining = remoteCall { database.getReference("partyPresence/$cleanCode/$uid").get().await() }
            requireCurrent(uid)
            if (remaining.children.any { it.key != deviceId && it.value == true }) return
            mutate(cleanCode, uid) { old, requestedAt -> old?.let { FirebasePartyEngine.leave(it, uid, requestedAt) } }
        } finally { removePresence(cleanCode, uid); pauseIfIdle() }
    }

    suspend fun control(code: String, action: String, body: JsonObject) {
        val uid = requireUid()
        val cleanCode = cleanCode(code)
        ready(uid)
        if (action == "kick") {
            val target = (body["memberId"] as? JsonPrimitive)?.content
                ?: throw FirebasePartyFailure("bad_member", "Choose a listener to remove.")
            // Rules independently require the current host; the room transaction removes the member next.
            remoteCall { withTimeout(10_000) { database.getReference("partyBans/$cleanCode/$target").setValue(true).await() } }
            requireCurrent(uid)
        }
        mutate(cleanCode, uid) { old, requestedAt ->
            val existing = old ?: throw FirebasePartyFailure("no_such_party", "This party has ended.")
            FirebasePartyEngine.control(existing, uid, action, body, requestedAt)
        }
    }

    suspend fun preview(code: String): PartyPreview {
        val uid = requireUid()
        val cleanCode = cleanCode(code)
        ready(uid)
        val value = remoteCall { withTimeout(15_000) { database.getReference("partyRooms/$cleanCode").get().await().value } }
        requireCurrent(uid)
        val room = FirebasePartyCodec.decode(value)
            ?: throw FirebasePartyFailure("no_such_party", "No party with that code.")
        if (room.expiresAtMs <= nowServerMs()) throw FirebasePartyFailure("no_such_party", "This party has expired.")
        val people = room.members.entries.sortedBy { it.value.joinedAtMs }.map { (memberUid, person) ->
            PartyPreviewMember(person.displayName, person.avatarUrl, memberUid == room.hostUid)
        }
        return PartyPreview(room.code, room.members[room.hostUid]?.displayName.orEmpty(), room.members.size,
            room.maxMembers, room.members.size >= room.maxMembers, people)
    }

    /** One observation owns its listeners, connection marker and heartbeat; cancel it on leave/account changes. */
    fun observe(code: String, uid: String, onSnapshot: (PartySnapshot, Boolean) -> Unit,
        onError: (Throwable) -> Unit): Job = scope.launch {
        val cleanCode = try { requireCurrent(uid); cleanCode(code) }
            catch (failure: Exception) { onError(failure); return@launch }
        val ownerKey = "$cleanCode:$uid"
        val owner = Any()
        presenceOwners[ownerKey] = owner
        observers.incrementAndGet()
        startMonitoring()
        try {
            coroutineScope {
                val roomRef = database.getReference("partyRooms/$cleanCode")
                val presenceRef = database.getReference("partyPresence/$cleanCode")
                val leasesRef = database.getReference("partyLeases/$cleanCode")
                val ownBanRef = database.getReference("partyBans/$cleanCode/$uid")
                val latest = AtomicReference<FirebasePartyRoom?>(null)
                val present = AtomicReference<Set<String>>(emptySet())
                val leases = AtomicReference<Map<String, Long>>(emptyMap())
                val registration = Mutex()
                var registered = false
                val observation = coroutineContext[Job]!!

                fun fail(failure: Throwable) {
                    if (observation.isActive) onError(failure)
                    observation.cancel()
                }
                fun publish() {
                    if (!observation.isActive) return
                    if (currentUid() != uid) { fail(FirebasePartyFailure("account_changed", "Your Google account changed.")); return }
                    val room = latest.get() ?: return
                    val now = nowServerMs()
                    val live = present.get().filterTo(HashSet()) { member ->
                        val seen = leases.get()[member] ?: 0L
                        seen in (now - HOST_GRACE_MS)..(now + 5_000)
                    }
                    onSnapshot(room.snapshot(now, live), connected.value)
                }
                suspend fun registerPresence() = registration.withLock {
                    if (registered || !connected.value || presenceOwners[ownerKey] !== owner ||
                        latest.get()?.members?.containsKey(uid) != true) return@withLock
                    requireCurrent(uid)
                    val mine = database.getReference("partyPresence/$cleanCode/$uid/$deviceId")
                    val lease = database.getReference("partyLeases/$cleanCode/$uid")
                    withTimeout(10_000) {
                        mine.onDisconnect().removeValue().await()
                        lease.onDisconnect().setValue(ServerValue.TIMESTAMP).await()
                        requireCurrent(uid)
                        if (!connected.value) return@withTimeout
                        mine.setValue(true).await()
                        lease.setValue(ServerValue.TIMESTAMP).await()
                        registered = true
                    }
                }
                fun guarded(action: (DataSnapshot) -> Unit) = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!observation.isActive) return
                        try { requireCurrent(uid); action(snapshot) }
                        catch (failure: Exception) { fail(failure) }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        launch {
                            if (error.code == DatabaseError.PERMISSION_DENIED && currentUid() == uid) {
                                val banned = withTimeoutOrNull(3_000) { runCatching { ownBanRef.get().await().value == true }.getOrNull() }
                                if (banned == true) {
                                    fail(FirebasePartyFailure("kicked", "The host removed you from this party."))
                                    return@launch
                                }
                            }
                            fail(databaseFailure(error))
                        }
                    }
                }
                val roomListener = guarded { data ->
                    val room = FirebasePartyCodec.decode(data.value)
                        ?: throw FirebasePartyFailure("no_such_party", "This party has ended.")
                    if (room.expiresAtMs <= nowServerMs()) throw FirebasePartyFailure("no_such_party", "This party has expired.")
                    if (uid !in room.members) throw FirebasePartyFailure("not_joined", "You are no longer in this party.")
                    latest.set(room)
                    publish()
                    launch { try { registerPresence() } catch (failure: Exception) { if (failure !is CancellationException) fail(failure) } }
                }
                val presenceListener = guarded { data ->
                    present.set(data.children.filter { member -> member.children.any { it.value == true } }.mapTo(HashSet()) { it.key!! })
                    publish()
                }
                val leaseListener = guarded { data ->
                    leases.set(data.children.mapNotNull { child -> (child.value as? Number)?.toLong()?.let { child.key!! to it } }.toMap())
                    publish()
                }
                val banListener = guarded { data -> if (data.value == true) throw FirebasePartyFailure("kicked", "The host removed you from this party.") }
                roomRef.addValueEventListener(roomListener)
                presenceRef.addValueEventListener(presenceListener)
                leasesRef.addValueEventListener(leaseListener)
                ownBanRef.addValueEventListener(banListener)
                try {
                    launch {
                        combine(connected, offset) { online, _ -> online }.collect { online ->
                            if (!online) registration.withLock { registered = false }
                            else try { registerPresence() } catch (failure: Exception) { if (failure !is CancellationException) fail(failure) }
                            publish()
                        }
                    }
                    launch {
                        while (isActive) {
                            delay(15_000)
                            if (currentUid() != uid) { fail(FirebasePartyFailure("account_changed", "Your Google account changed.")); break }
                            val room = latest.get() ?: continue
                            if (room.expiresAtMs <= nowServerMs()) { fail(FirebasePartyFailure("no_such_party", "This party has expired.")); break }
                            if (!connected.value) continue
                            try {
                                registerPresence()
                                withTimeout(10_000) { database.getReference("partyLeases/$cleanCode/$uid").setValue(ServerValue.TIMESTAMP).await() }
                                val hostLease = leases.get()[room.hostUid] ?: room.createdAtMs
                                if (room.hostUid != uid && nowServerMs() - hostLease > HOST_GRACE_MS) {
                                    mutate(cleanCode, uid) { current, requestedAt -> current?.let {
                                        FirebasePartyEngine.takeover(it, uid, hostLease, requestedAt)
                                    } }
                                }
                                publish()
                            } catch (cancelled: CancellationException) {
                                if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                            } catch (failure: Exception) {
                                // A simultaneous host election can lose its transaction; the next snapshot/heartbeat resolves it.
                                publish()
                            }
                        }
                    }
                    awaitCancellation()
                } finally {
                    roomRef.removeEventListener(roomListener)
                    presenceRef.removeEventListener(presenceListener)
                    leasesRef.removeEventListener(leaseListener)
                    ownBanRef.removeEventListener(banListener)
                }
            }
        } finally {
            if (presenceOwners.remove(ownerKey, owner)) withContext(NonCancellable) { removePresence(cleanCode, uid) }
            observers.decrementAndGet()
            pauseIfIdle()
        }
    }

    private suspend fun removePresence(code: String, uid: String) {
        if (currentUid() != uid) return // Registered server-side onDisconnect + lease expiry handles an old account.
        withTimeoutOrNull(3_000) {
            runCatching {
                val mine = database.getReference("partyPresence/$code/$uid/$deviceId")
                mine.removeValue().await()
                mine.onDisconnect().cancel().await()
                // Keep the shared UID lease: another device may still be connected under the same account.
                database.getReference("partyLeases/$code/$uid").onDisconnect().cancel().await()
            }
        }
    }

    private fun membership(room: FirebasePartyRoom, uid: String): PartyMembership {
        val snapshot = room.snapshot(nowServerMs(), setOf(uid))
        return PartyMembership(room.code, "firebase", snapshot.members.first { it.memberId == uid }, snapshot, nowServerMs())
    }

    private suspend fun mutate(code: String, uid: String, allowMissing: Boolean = false,
        operation: (FirebasePartyRoom?, Long) -> FirebasePartyRoom?): FirebasePartyRoom? {
        requireCurrent(uid)
        if (!clockReady) throw FirebasePartyFailure("offline", "Connect to Firebase before changing the party.")
        val requestedAt = nowServerMs()
        val startedAt = SystemClock.elapsedRealtime()
        val reference = database.getReference("partyRooms/$code")
        return withTimeout(20_000) {
            val retained = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) = Unit
                override fun onCancelled(error: DatabaseError) = Unit
            }
            // get() alone can discard an unobserved server cache before runTransaction sees it.
            if (!allowMissing) reference.addValueEventListener(retained)
            try {
            // A cold RTDB transaction may initially see null before its server cache is loaded.
            // Seed that cache for existing-room operations so a real room is not mistaken for absence.
            if (!allowMissing) {
                val loaded = remoteCall { reference.get().await() }
                requireCurrent(uid)
                if (!loaded.exists()) throw FirebasePartyFailure("no_such_party", "No party with that code.")
            }
            suspendCancellableCoroutine { continuation ->
                val aborted = AtomicReference<Throwable?>(null)
                reference.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(data: MutableData): Transaction.Result {
                        if (!continuation.isActive) return Transaction.abort()
                        return try {
                            requireCurrent(uid)
                            if (!connected.value || SystemClock.elapsedRealtime() - startedAt > COMMAND_TTL_MS)
                                throw FirebasePartyFailure("offline", "The party command expired. Reconnect and try again.")
                            val old = FirebasePartyCodec.decode(data.value)
                            var next = operation(old, requestedAt)
                            // Even an idempotent join must be confirmed by the server, never a cached get fallback.
                            if (old != null && next == old) next = old.copy(revision = old.revision + 1, updatedAtMs = requestedAt)
                            data.value = next?.let(FirebasePartyCodec::encode)
                            Transaction.success(data)
                        } catch (failure: Exception) { aborted.set(failure); Transaction.abort() }
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                        if (!continuation.isActive) return
                        try {
                            requireCurrent(uid)
                            if (error != null) throw databaseFailure(error)
                            if (!committed) throw aborted.get() ?: FirebasePartyFailure("conflict", "The party changed. Try again.")
                            continuation.resume(FirebasePartyCodec.decode(currentData?.value))
                        } catch (failure: Exception) { continuation.resumeWithException(failure) }
                    }
                }, false)
            }
            } finally {
                if (!allowMissing) reference.removeEventListener(retained)
            }
        }
    }

    fun close() {
        screenActive = false
        scope.cancel()
        synchronized(monitorLock) {
            if (monitoring) {
                database.getReference(".info/connected").removeEventListener(connectionListener)
                database.getReference(".info/serverTimeOffset").removeEventListener(offsetListener)
            }
            monitoring = false
            _connected.value = false
            offset.value = null
        }
    }

    private fun cleanCode(code: String): String = code.trim().uppercase().also {
        if (!it.matches(Regex("[A-Z0-9]{6}"))) throw FirebasePartyFailure("bad_code", "A party code is six letters or digits.")
    }

    private suspend fun <T> remoteCall(block: suspend () -> T): T = try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            if (failure.message?.contains("permission", ignoreCase = true) == true) throw permissionFailure()
            throw FirebasePartyFailure("firebase_unavailable", "Party Room could not reach Firebase. Check your connection and try again.")
        }

    private fun databaseFailure(error: DatabaseError): FirebasePartyFailure =
        if (error.code == DatabaseError.PERMISSION_DENIED) permissionFailure() else
            FirebasePartyFailure("firebase_unavailable", "Party Room could not reach Firebase. Check your connection and try again.")

    private fun permissionFailure() = FirebasePartyFailure("permission_denied",
        "Party Room needs its Firebase room rules published. Sign in with Google and check the database setup.")

    companion object {
        private const val HOST_GRACE_MS = 45_000L
        private const val COMMAND_TTL_MS = 10_000L
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}

/** RTDB's untyped maps omit empty children and can return numeric slot maps as lists. */
internal object FirebasePartyCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun encode(room: FirebasePartyRoom): Map<String, Any?> =
        value(json.encodeToJsonElement(FirebasePartyRoom.serializer(), room)) as Map<String, Any?>

    fun decode(raw: Any?): FirebasePartyRoom? {
        if (raw == null) return null
        var root = element(raw) as? JsonObject ?: error("Invalid Firebase party snapshot")
        val slots = root["slots"]
        if (slots is JsonArray) root = JsonObject(root + ("slots" to JsonObject(slots.mapIndexedNotNull { index, item ->
            item.takeUnless { it == JsonNull }?.let { index.toString() to it }
        }.toMap())))
        return json.decodeFromJsonElement(FirebasePartyRoom.serializer(), root).validated()
    }

    private fun element(raw: Any?): JsonElement = when (raw) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(raw.entries.associate { it.key.toString() to element(it.value) })
        is List<*> -> JsonArray(raw.map(::element))
        is Boolean -> JsonPrimitive(raw)
        is Number -> raw.toLong().takeIf { it.toDouble() == raw.toDouble() }?.let(::JsonPrimitive) ?: JsonPrimitive(raw)
        is String -> JsonPrimitive(raw)
        else -> error("Unsupported Firebase party value")
    }

    private fun value(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonObject -> element.mapValues { value(it.value) }
        is JsonArray -> element.map(::value)
        is JsonPrimitive -> if (element.isString) element.content else
            element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull ?: error("Invalid Firebase party primitive")
    }
}
