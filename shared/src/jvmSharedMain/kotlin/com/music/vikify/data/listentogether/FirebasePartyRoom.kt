package com.music.vikify.data.listentogether

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** One Google account occupies one seat; its device connections are held separately in presence. */
@Serializable
data class FirebasePartyPerson(
    val displayName: String,
    val avatarUrl: String? = null,
    val joinedAtMs: Long,
    val slot: String,
)

/** The bounded room value committed atomically by Firebase Realtime Database transactions. */
@Serializable
data class FirebasePartyRoom(
    val v: Int = 1,
    val code: String,
    val hostUid: String,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val updatedAtMs: Long,
    val maxMembers: Int = 5,
    val hostOnlyControl: Boolean = false,
    val revision: Long = 0,
    val members: Map<String, FirebasePartyPerson>,
    val slots: Map<String, String>,
    val playback: PartyPlayback = PartyPlayback(),
    val queue: PartyQueue = PartyQueue(),
) {
    fun validated(): FirebasePartyRoom = apply {
        require(v == 1 && code.matches(Regex("[A-Z0-9]{6}"))) { "Unsupported party room" }
        require(createdAtMs > 0 && expiresAtMs > createdAtMs &&
            expiresAtMs - createdAtMs <= FirebasePartyEngine.ROOM_TTL_MS && updatedAtMs >= createdAtMs && revision >= 0) { "Invalid party lifetime" }
        require(maxMembers in 2..10 && members.size in 1..maxMembers && hostUid in members) { "Invalid party membership" }
        require(members.keys.all(FirebasePartyEngine::validUid) && slots.size == members.size &&
            slots.keys.all { it.matches(Regex("s[0-9]")) && it.substring(1).toInt() < maxMembers } &&
            slots.values.toSet() == members.keys && slots.values.distinct().size == slots.size &&
            members.all { (uid, person) -> slots[person.slot] == uid }) { "Invalid party identity or seat" }
        require(members.values.all { it.displayName.isNotBlank() && it.displayName.length <= 80 &&
            it.joinedAtMs in createdAtMs until expiresAtMs && FirebasePartyEngine.validAvatar(it.avatarUrl) }) { "Invalid party profile" }
        require(queue.seq >= 0 && queue.items.size <= FirebasePartyEngine.MAX_QUEUE &&
            queue.index >= -1 && queue.index < queue.items.size &&
            queue.items.all(FirebasePartyEngine::validTrack)) { "Invalid party queue" }
        require(playback.seq >= 0 && playback.queueSeq == queue.seq && playback.queueIndex == queue.index &&
            playback.queueLength == queue.items.size && playback.positionMs in 0..FirebasePartyEngine.MAX_POSITION_MS &&
            playback.anchorMs > 0 && playback.updatedAtMs > 0 &&
            (playback.track == null || FirebasePartyEngine.validTrack(playback.track)) &&
            (!playback.isPlaying || playback.track != null)) { "Invalid party playback" }
        require(queue.index == -1 || playback.track?.videoId == queue.items[queue.index].videoId) { "Party index does not name its track" }
    }

    fun snapshot(now: Long, connected: Set<String> = members.keys): PartySnapshot = PartySnapshot(
        code = code, createdAtMs = createdAtMs, maxMembers = maxMembers,
        hostOnlyControl = hostOnlyControl,
        members = members.entries.sortedWith(compareBy<Map.Entry<String, FirebasePartyPerson>> { it.value.joinedAtMs }.thenBy { it.key })
            .map { (uid, person) -> PartyMember(memberId = uid, userId = uid,
                displayName = person.displayName, avatarUrl = person.avatarUrl, isHost = uid == hostUid,
                connected = uid in connected, joinedAtMs = person.joinedAtMs,
                lastSeenMs = if (uid in connected) now else person.joinedAtMs) },
        playback = playback.copy(effectivePositionMs = FirebasePartyEngine.positionAt(playback, now)),
        queue = queue, serverMs = now,
    )
}

class FirebasePartyFailure(val code: String, message: String) : Exception(message)

/** Pure room transitions. Firebase rules independently enforce account ownership and host privileges. */
object FirebasePartyEngine {
    const val ROOM_TTL_MS = 12 * 60 * 60 * 1_000L
    const val HOST_LEASE_MS = 45_000L
    const val MAX_QUEUE = 26
    const val MAX_POSITION_MS = 24 * 60 * 60 * 1_000L
    const val PLAY_LEAD_MS = 1_000L
    private val json = Json { ignoreUnknownKeys = true }
    private val hostActions = setOf("setMaxMembers", "setHostOnlyControl", "kick")

    fun create(code: String, uid: String, name: String, avatar: String?, max: Int, autoplay: Boolean, now: Long): FirebasePartyRoom {
        checkUid(uid)
        if (!code.matches(Regex("[A-Z0-9]{6}"))) fail("bad_code", "A party code is six letters or digits.")
        if (max !in 2..10) fail("invalid_capacity", "Choose a party size between 2 and 10.")
        require(now > 0)
        return FirebasePartyRoom(code = code, hostUid = uid, createdAtMs = now,
            expiresAtMs = Math.addExact(now, ROOM_TTL_MS), updatedAtMs = now, maxMembers = max,
            members = mapOf(uid to person(name, avatar, now, "s0")), slots = mapOf("s0" to uid),
            playback = PartyPlayback(anchorMs = now, updatedAtMs = now, autoplayEnabled = autoplay)).validated()
    }

    fun join(room: FirebasePartyRoom, uid: String, name: String, avatar: String?, now: Long, banned: Boolean = false): FirebasePartyRoom {
        live(room, now); checkUid(uid)
        if (banned) fail("kicked", "The host removed you from this party.")
        val existing = room.members[uid]
        if (existing == null && room.members.size >= room.maxMembers) fail("party_full", "This party is full.")
        val slot = existing?.slot ?: (0 until room.maxMembers).map { "s$it" }.first { it !in room.slots }
        val next = person(name, avatar, existing?.joinedAtMs ?: now, slot)
        if (next == existing) return room
        return changed(room, room.copy(members = room.members + (uid to next), slots = room.slots + (slot to uid)), now)
    }

    fun leave(room: FirebasePartyRoom, uid: String, now: Long): FirebasePartyRoom? {
        live(room, now)
        if (uid !in room.members) return room
        val remaining = room.members - uid
        if (remaining.isEmpty()) return null
        val host = if (room.hostUid == uid) remaining.entries
            .minWith(compareBy<Map.Entry<String, FirebasePartyPerson>> { it.value.joinedAtMs }.thenBy { it.key }).key else room.hostUid
        return changed(room, room.copy(hostUid = host, members = remaining, slots = room.slots - room.members.getValue(uid).slot), now)
    }

    fun takeover(room: FirebasePartyRoom, uid: String, oldHostLeaseMs: Long, now: Long): FirebasePartyRoom {
        live(room, now); member(room, uid)
        if (uid == room.hostUid) return room
        val last = oldHostLeaseMs.takeIf { it > 0 } ?: room.createdAtMs
        if (last > now || now - last < HOST_LEASE_MS) fail("host_present", "The host is still connected.")
        return changed(room, room.copy(hostUid = uid), now)
    }

    fun control(room: FirebasePartyRoom, uid: String, action: String, body: JsonObject, now: Long): FirebasePartyRoom {
        live(room, now); member(room, uid)
        if ((action in hostActions || room.hostOnlyControl) && uid != room.hostUid)
            fail("host_only", "Only the host can control the music in this party.")
        val p = room.playback
        val q = room.queue
        val next = when (action) {
            "play" -> {
                requireTrack(p)
                playback(room, uid, now, p.copy(positionMs = clamp(body.long("positionMs") ?: positionAt(p, now), p.track),
                    isPlaying = true, anchorMs = Math.addExact(now, PLAY_LEAD_MS)))
            }
            "pause" -> playback(room, uid, now, p.copy(
                positionMs = clamp(body.long("positionMs") ?: positionAt(p, now), p.track), isPlaying = false, anchorMs = now))
            "seek" -> {
                requireTrack(p)
                val position = body.long("positionMs") ?: fail("missing_position", "Seek requires a playback position.")
                playback(room, uid, now, p.copy(positionMs = clamp(position, p.track),
                    anchorMs = Math.addExact(now, if (p.isPlaying) PLAY_LEAD_MS else 0)))
            }
            "setTrack" -> {
                val track = track(body["track"])
                val hinted = body.int("queueIndex") ?: -1
                val index = if (hinted in q.items.indices && q.items[hinted].videoId == track.videoId) hinted else
                    partyQueueIndexOf(q, p, track.videoId)
                select(room, uid, track, index, body.long("positionMs") ?: 0,
                    body.boolean("isPlaying") ?: true, now)
            }
            "setQueue" -> setQueue(room, uid, tracks(body["queue"]), body.int("queueIndex") ?: -1, now)
            "queueAdd" -> {
                val batch = if (body["track"] != null) listOf(track(body["track"])) else tracks(body["tracks"])
                add(room, uid, batch, body.boolean("playNext") ?: false, now)
            }
            "queueRemove" -> {
                val from = q.index + 1
                val id = body.string("videoId") ?: body.int("index")?.takeIf { it in q.items.indices }?.let { q.items[it].videoId }
                    ?: fail("missing_track", "Choose a queued song to remove.")
                val index = (from until q.items.size).firstOrNull { q.items[it].videoId == id }
                    ?: fail("not_found", "That song is not in the upcoming queue.")
                queue(room, uid, q.items.toMutableList().also { it.removeAt(index) }, q.index, now)
            }
            "queueClear" -> {
                val keep = if (q.index >= 0) q.index + 1 else 0
                if (q.items.size == keep) fail("no_upcoming", "No upcoming songs to clear.")
                queue(room, uid, q.items.take(keep), q.index, now)
            }
            "queueMove" -> {
                val from = body.int("fromIndex") ?: fail("missing_indices", "Choose a queued song to move.")
                val to = body.int("toIndex") ?: fail("missing_indices", "Choose its new queue position.")
                val id = body.string("videoId")
                if (from !in q.items.indices || to !in q.items.indices || from <= q.index || to <= q.index ||
                    (id != null && q.items[from].videoId != id)) fail("invalid_move", "The queue changed. Try moving that song again.")
                if (from == to) room else queue(room, uid,
                    q.items.toMutableList().also { it.add(to, it.removeAt(from)) }, q.index, now)
            }
            "next", "previous" -> {
                val index = q.index + if (action == "next") 1 else -1
                if (index !in q.items.indices) fail(if (action == "next") "end_of_queue" else "start_of_queue",
                    if (action == "next") "Already at the end of the queue." else "Already at the beginning of the queue.")
                select(room, uid, q.items[index], index, 0, true, now)
            }
            "setAutoplay" -> {
                val enabled = body.boolean("enabled") ?: fail("invalid_autoplay", "Choose whether AutoPlay is enabled.")
                if (enabled == p.autoplayEnabled) room else playback(room, uid, now, p.copy(autoplayEnabled = enabled))
            }
            "setHostOnlyControl" -> room.copy(hostOnlyControl = body.boolean("enabled")
                ?: fail("invalid_control_policy", "Choose whether host-only control is enabled."))
            "setMaxMembers" -> {
                val size = body.int("maxMembers") ?: fail("invalid_capacity", "Choose a party size between 2 and 10.")
                if (size !in 2..10 || size < room.members.size) fail("invalid_capacity", "The party must have room for everyone already here.")
                val compacted = room.members.entries.sortedBy { it.value.slot.substring(1).toInt() }.mapIndexed { index, entry ->
                    entry.key to entry.value.copy(slot = "s$index")
                }.toMap()
                room.copy(maxMembers = size, members = compacted,
                    slots = compacted.entries.associate { it.value.slot to it.key })
            }
            "kick" -> {
                val target = body.string("memberId") ?: fail("invalid_member", "Choose a listener to remove.")
                if (target == uid) fail("invalid_member", "Leave the party to remove yourself.")
                if (target !in room.members) fail("not_found", "That listener is no longer in this party.")
                room.copy(members = room.members - target, slots = room.slots - room.members.getValue(target).slot)
            }
            else -> fail("unknown_action", "This party control is not supported.")
        }
        return changed(room, next, now)
    }

    fun positionAt(playback: PartyPlayback, now: Long): Long {
        val elapsed = if (playback.isPlaying) (now - playback.anchorMs).coerceAtLeast(0) else 0
        val position = if (elapsed > MAX_POSITION_MS - playback.positionMs.coerceAtMost(MAX_POSITION_MS)) MAX_POSITION_MS
            else playback.positionMs + elapsed
        return clamp(position, playback.track)
    }

    internal fun validUid(uid: String) = uid.matches(Regex("[A-Za-z0-9_-]{1,128}"))
    internal fun validAvatar(value: String?) = value == null || (value.length <= 2_000 &&
        (value.startsWith("https://") || value.startsWith("http://")))
    internal fun validTrack(track: PartyTrack) = track.videoId.isNotBlank() && track.videoId.length <= 128 &&
        !track.videoId.startsWith("local:") && !track.videoId.contains("://") &&
        track.title.length <= 300 && track.artist.length <= 300 &&
        (track.thumbnailUrl == null || (track.thumbnailUrl.length <= 1_000 &&
            (track.thumbnailUrl.startsWith("https://") || track.thumbnailUrl.startsWith("http://")))) &&
        (track.durationMs == null || track.durationMs in 1..MAX_POSITION_MS)

    private fun person(name: String, avatar: String?, joinedAt: Long, slot: String): FirebasePartyPerson {
        if (!validAvatar(avatar)) fail("invalid_profile", "This account has an invalid profile picture.")
        return FirebasePartyPerson(name.trim().take(80).ifBlank { "Listener" }, avatar, joinedAt, slot)
    }
    private fun member(room: FirebasePartyRoom, uid: String) {
        if (uid !in room.members) fail("not_a_member", "Join this party before controlling it.")
    }
    private fun live(room: FirebasePartyRoom, now: Long) {
        room.validated()
        if (now >= room.expiresAtMs) fail("no_such_party", "This party has expired. Start a new room.")
    }
    private fun checkUid(uid: String) {
        if (!validUid(uid)) fail("not_signed_in", "Sign in with Google to join a party.")
    }
    private fun requireTrack(playback: PartyPlayback) {
        if (playback.track == null) fail("missing_track", "Choose a song to play first.")
    }
    private fun clamp(position: Long, track: PartyTrack?): Long = position.coerceIn(0, track?.durationMs ?: MAX_POSITION_MS)
    private fun changed(old: FirebasePartyRoom, next: FirebasePartyRoom, now: Long): FirebasePartyRoom =
        if (old == next) old else next.copy(revision = Math.addExact(old.revision, 1), updatedAtMs = now).validated()

    private fun playback(room: FirebasePartyRoom, uid: String, now: Long, next: PartyPlayback): FirebasePartyRoom = room.copy(
        playback = next.copy(seq = Math.addExact(room.playback.seq, 1), updatedBy = uid,
            updatedAtMs = now, effectivePositionMs = next.positionMs),
    )

    private fun select(room: FirebasePartyRoom, uid: String, track: PartyTrack, index: Int,
        position: Long, playing: Boolean, now: Long): FirebasePartyRoom {
        val next = playback(room, uid, now, room.playback.copy(track = track,
            positionMs = clamp(position, track), isPlaying = playing,
            anchorMs = Math.addExact(now, if (playing) PLAY_LEAD_MS else 0),
            queueIndex = index, startedBy = uid, startedByName = room.members.getValue(uid).displayName))
        return next.copy(queue = room.queue.copy(index = index))
    }

    private fun queue(room: FirebasePartyRoom, uid: String, items: List<PartyTrack>, index: Int, now: Long): FirebasePartyRoom {
        val nextQueue = PartyQueue(Math.addExact(room.queue.seq, 1), index, items)
        return playback(room, uid, now, room.playback.copy(queueSeq = nextQueue.seq,
            queueLength = items.size, queueIndex = index)).copy(queue = nextQueue)
    }

    private fun setQueue(room: FirebasePartyRoom, uid: String, tracks: List<PartyTrack>, hint: Int, now: Long): FirebasePartyRoom {
        var index = hint.takeIf { it in tracks.indices } ?: -1
        room.playback.track?.let { current ->
            if (index !in tracks.indices || tracks[index].videoId != current.videoId) {
                val candidateQueue = PartyQueue(seq = room.queue.seq, index = hint, items = tracks)
                index = partyQueueIndexOf(candidateQueue, room.playback.copy(queueIndex = hint), current.videoId)
            }
        }
        // A queue can retain some history, but the active song and the next 25 always take priority.
        val upcoming = if (index >= 0) (tracks.size - index - 1).coerceAtMost(MAX_QUEUE - 1) else 0
        val start = if (index >= 0) (index - (MAX_QUEUE - upcoming - 1)).coerceAtLeast(0) else 0
        val bounded = tracks.drop(start).take(MAX_QUEUE)
        index = if (index >= 0) index - start else -1
        if (room.playback.track == null && index >= 0) index = -1
        if (bounded == room.queue.items && index == room.queue.index) return room
        return queue(room, uid, bounded, index, now)
    }

    private fun add(room: FirebasePartyRoom, uid: String, tracks: List<PartyTrack>, playNext: Boolean, now: Long): FirebasePartyRoom {
        val q = room.queue
        val waiting = q.items.drop(q.index + 1).mapTo(HashSet()) { it.videoId }
        val incoming = tracks.filter { !it.fromAutoplay || waiting.add(it.videoId) }
        if (incoming.isEmpty()) {
            if (tracks.isEmpty()) fail("no_tracks", "Choose a song to add to the queue.")
            return room // An echoed/retried AutoPlay batch is an idempotent success.
        }
        val upcoming = q.items.size - q.index - 1
        val available = (MAX_QUEUE - 1 - upcoming).coerceAtLeast(0)
        if (available == 0) fail("queue_full", "The party queue has 25 upcoming songs.")
        val added = incoming.take(available)
        val insertAt = if (playNext && q.index >= 0) q.index + 1 else q.items.size
        val newItems = q.items.toMutableList().also { it.addAll(insertAt, added) }
        // Drop the oldest history first to keep the full room value small.
        val drop = (newItems.size - MAX_QUEUE).coerceAtLeast(0)
        return queue(room, uid, newItems.drop(drop), if (q.index >= 0) q.index - drop else -1, now)
    }

    private fun track(value: kotlinx.serialization.json.JsonElement?): PartyTrack {
        val decoded = runCatching { json.decodeFromJsonElement(PartyTrack.serializer(), value ?: error("Missing track")) }.getOrNull()
            ?: fail("invalid_track", "That song cannot be shared in a party.")
        if (!validTrack(decoded)) fail("invalid_track", "That song cannot be shared in a party.")
        return decoded
    }
    private fun tracks(value: kotlinx.serialization.json.JsonElement?): List<PartyTrack> {
        val array = value as? JsonArray ?: fail("invalid_tracks", "Choose songs for the party queue.")
        if (array.size > 10_000) fail("queue_full", "Choose a smaller queue for this party.")
        return array.map(::track)
    }
    private fun JsonObject.long(key: String) = (get(key) as? JsonPrimitive)?.longOrNull
    private fun JsonObject.int(key: String) = (get(key) as? JsonPrimitive)?.intOrNull
    private fun JsonObject.boolean(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    private fun fail(code: String, message: String): Nothing = throw FirebasePartyFailure(code, message)
}
