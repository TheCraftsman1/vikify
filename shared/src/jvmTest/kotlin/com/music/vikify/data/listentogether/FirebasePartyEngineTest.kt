package com.music.vikify.data.listentogether

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirebasePartyEngineTest {
    private val now = 1_800_000_000_000L
    private val json = Json { encodeDefaults = true }
    private val empty = JsonObject(emptyMap())
    private fun room(max: Int = 5) = FirebasePartyEngine.create("TEST12", "host", "Host", null, max, false, now)
    private fun song(n: Int, autoplay: Boolean = false) = PartyTrack("video${n.toString().padStart(6, '0')}",
        "Song $n", "Artist", durationMs = 180_000, fromAutoplay = autoplay)
    private fun control(room: FirebasePartyRoom, action: String, body: JsonObject = empty,
        uid: String = "host", at: Long = now + 1_000) = FirebasePartyEngine.control(room, uid, action, body, at)
    private fun setTrack(room: FirebasePartyRoom, track: PartyTrack, at: Long = now + 100) =
        control(room, "setTrack", buildJsonObject { put("track", json.encodeToJsonElement(PartyTrack.serializer(), track)) }, at = at)
    private fun setQueue(room: FirebasePartyRoom, tracks: List<PartyTrack>, index: Int,
        at: Long = now + 200) = control(room, "setQueue", buildJsonObject {
        put("queue", json.encodeToJsonElement(ListSerializer(PartyTrack.serializer()), tracks)); put("queueIndex", index)
    }, at = at)

    @Test fun creationAndPortableRoundTripDeriveHostAndPresenceWithoutPersistingThem() {
        val room = room()
        assertEquals(mapOf("s0" to "host"), room.slots)
        assertEquals("s0", room.members.getValue("host").slot)
        assertEquals(now, room.updatedAtMs)
        assertEquals(now + FirebasePartyEngine.ROOM_TTL_MS, room.expiresAtMs)
        val encoded = json.encodeToString(FirebasePartyRoom.serializer(), room)
        assertFalse(encoded.contains("connected"))
        assertFalse(encoded.contains("isHost"))
        assertEquals(room, json.decodeFromString(FirebasePartyRoom.serializer(), encoded).validated())
        val snapshot = room.snapshot(now + 5_000, emptySet())
        assertTrue(snapshot.members.single().isHost)
        assertFalse(snapshot.members.single().connected)
        assertEquals("host", snapshot.members.single().userId)
    }

    @Test fun sameAccountRejoinIsIdempotentEvenWhenTheRoomIsFullAndReusesItsSeat() {
        val created = room(2)
        val joined = FirebasePartyEngine.join(created, "guest", "Guest", null, now + 100)
        assertEquals("s1", joined.members.getValue("guest").slot)
        assertEquals(joined, FirebasePartyEngine.join(joined, "guest", "Guest", null, now + 200))
        val renamed = FirebasePartyEngine.join(joined, "guest", "New name", null, now + 300)
        assertEquals(2, renamed.members.size)
        assertEquals(now + 100, renamed.members.getValue("guest").joinedAtMs)
        assertEquals(joined.slots, renamed.slots)
        assertEquals(joined.revision + 1, renamed.revision)
        assertEquals(now + 300, renamed.updatedAtMs)
        assertEquals("party_full", assertFailsWith<FirebasePartyFailure> {
            FirebasePartyEngine.join(joined, "third", "Third", null, now + 400)
        }.code)
        assertEquals("kicked", assertFailsWith<FirebasePartyFailure> {
            FirebasePartyEngine.join(joined, "guest", "Guest", null, now + 400, banned = true)
        }.code)
    }

    @Test fun expiryAndNonmemberControlsCannotMutateARoom() {
        val created = room()
        assertEquals("no_such_party", assertFailsWith<FirebasePartyFailure> {
            FirebasePartyEngine.join(created, "guest", "Guest", null, created.expiresAtMs)
        }.code)
        assertEquals("not_a_member", assertFailsWith<FirebasePartyFailure> {
            control(created, "pause", uid = "outsider")
        }.code)
        assertEquals("missing_track", assertFailsWith<FirebasePartyFailure> { control(created, "play") }.code)
    }

    @Test fun hostDepartureReassignsTheOldestMemberAndLeavesNoDanglingSeat() {
        var current = FirebasePartyEngine.join(room(), "later", "Later", null, now + 200)
        current = FirebasePartyEngine.join(current, "earlier", "Earlier", null, now + 100)
        val transferred = FirebasePartyEngine.leave(current, "host", now + 300)!!
        assertEquals("earlier", transferred.hostUid)
        assertEquals(setOf("s1", "s2"), transferred.slots.keys)
        assertFalse("host" in transferred.slots.values)
        val last = FirebasePartyEngine.leave(transferred, "later", now + 400)!!
        assertNull(FirebasePartyEngine.leave(last, "earlier", now + 500))
        assertEquals(current.playback.seq, transferred.playback.seq)
    }

    @Test fun reducingCapacityCompactsSeatsWithoutChangingMemberIdentityOrJoinOrder() {
        var current = room()
        repeat(4) { current = FirebasePartyEngine.join(current, "guest$it", "Guest $it", null, now + it + 1) }
        current = FirebasePartyEngine.leave(current, "guest0", now + 10)!!
        current = FirebasePartyEngine.leave(current, "guest1", now + 11)!!
        val before = current.members.mapValues { it.value.joinedAtMs }
        val smaller = control(current, "setMaxMembers", buildJsonObject { put("maxMembers", 3) })
        assertEquals(3, smaller.maxMembers)
        assertEquals(setOf("s0", "s1", "s2"), smaller.slots.keys)
        assertEquals(before, smaller.members.mapValues { it.value.joinedAtMs })
        assertEquals(current.playback, smaller.playback)
        assertEquals("invalid_capacity", assertFailsWith<FirebasePartyFailure> {
            control(smaller, "setMaxMembers", buildJsonObject { put("maxMembers", 2) })
        }.code)
    }

    @Test fun hostLeaseAllowsOnlyAnExistingMemberToTakeOverAfterTheGracePeriod() {
        val joined = FirebasePartyEngine.join(room(), "guest", "Guest", null, now + 1)
        assertEquals("host_present", assertFailsWith<FirebasePartyFailure> {
            FirebasePartyEngine.takeover(joined, "guest", now, now + 44_999)
        }.code)
        assertEquals("not_a_member", assertFailsWith<FirebasePartyFailure> {
            FirebasePartyEngine.takeover(joined, "outsider", now, now + 45_000)
        }.code)
        val taken = FirebasePartyEngine.takeover(joined, "guest", now, now + 45_000)
        assertEquals("guest", taken.hostUid)
        assertEquals(joined.playback, taken.playback)
        assertEquals(joined.revision + 1, taken.revision)
    }

    @Test fun hostOnlyPolicyProtectsControlsAndHostSettingsRegardlessOfTheCallerUI() {
        val joined = FirebasePartyEngine.join(room(), "guest", "Guest", null, now + 1)
        val selected = setTrack(joined, song(1))
        assertFalse(control(selected, "pause", uid = "guest").playback.isPlaying)
        assertEquals("host_only", assertFailsWith<FirebasePartyFailure> {
            control(selected, "setMaxMembers", buildJsonObject { put("maxMembers", 3) }, uid = "guest")
        }.code)
        val locked = control(selected, "setHostOnlyControl", buildJsonObject { put("enabled", true) })
        assertEquals(selected.playback.seq, locked.playback.seq)
        assertEquals("host_only", assertFailsWith<FirebasePartyFailure> {
            control(locked, "pause", uid = "guest")
        }.code)
        assertFalse(control(locked, "pause").playback.isPlaying)
        val kicked = control(locked, "kick", buildJsonObject { put("memberId", "guest") })
        assertFalse("guest" in kicked.members)
        assertFalse("guest" in kicked.slots.values)
        assertEquals(locked.playback, kicked.playback)
    }

    @Test fun anchorsStayStationaryUntilScheduledStartAndPausedPositionDoesNotDrift() {
        val selected = setTrack(room(), song(1), now + 100)
        assertEquals(now + 1_100, selected.playback.anchorMs)
        assertEquals(0L, FirebasePartyEngine.positionAt(selected.playback, now + 500))
        assertEquals(5_000L, FirebasePartyEngine.positionAt(selected.playback, now + 6_100))
        val paused = control(selected, "pause", at = now + 6_100)
        assertEquals(5_000L, paused.playback.positionMs)
        assertEquals(5_000L, FirebasePartyEngine.positionAt(paused.playback, now + 100_000))
        val resumed = control(paused, "play", at = now + 7_000)
        assertEquals(5_000L, resumed.playback.positionMs)
        assertEquals(now + 8_000, resumed.playback.anchorMs)
        val end = control(resumed, "seek", buildJsonObject { put("positionMs", Long.MAX_VALUE) }, at = now + 9_000)
        assertEquals(180_000L, end.playback.positionMs)
        assertEquals("host", end.playback.startedBy)
        assertEquals(selected.playback.seq + 3, end.playback.seq)
    }

    @Test fun largeQueuesKeepThePlayingSongAndNextTwentyFiveInsteadOfOverflowingTheRoom() {
        val songs = (0 until 100).map { song(it) }
        val selected = setTrack(room(), songs[30])
        val queued = setQueue(selected, songs, 30)
        assertEquals(songs.subList(30, 56), queued.queue.items)
        assertEquals(0, queued.queue.index)
        assertEquals(queued.queue.seq, queued.playback.queueSeq)
        assertEquals(26, queued.playback.queueLength)
        val next = control(queued, "next")
        assertEquals(songs[31], next.playback.track)
        assertEquals(1, next.queue.index)
        assertEquals(queued.queue.seq, next.queue.seq)
        assertEquals(queued.playback.seq + 1, next.playback.seq)
    }

    @Test fun queueChangesNeverReplaceThePlayingTrackAndPreferDroppingOldHistory() {
        val initial = (0 until 10).map { song(it) }
        val queued = setQueue(setTrack(room(), initial[7]), initial, 7)
        val added = control(queued, "queueAdd", buildJsonObject {
            put("tracks", json.encodeToJsonElement(ListSerializer(PartyTrack.serializer()), (10 until 40).map { song(it) }))
        })
        assertEquals(initial[7], added.playback.track)
        assertEquals(0, added.queue.index)
        assertEquals(26, added.queue.items.size)
        assertEquals(initial[7], added.queue.items.first())
        val cleared = control(added, "queueClear")
        assertEquals(listOf(initial[7]), cleared.queue.items)
        assertEquals(added.playback.track, cleared.playback.track)
        assertEquals(added.playback.anchorMs, cleared.playback.anchorMs)
    }

    @Test fun manualDuplicatesRemainWhileRepeatedAutoplayBatchesAreIdempotent() {
        val selected = setQueue(setTrack(room(), song(1)), listOf(song(1)), 0)
        val batch = listOf(song(2, true), song(2, true), song(3), song(3))
        val body = buildJsonObject { put("tracks", json.encodeToJsonElement(ListSerializer(PartyTrack.serializer()), batch)) }
        val queued = control(selected, "queueAdd", body)
        assertEquals(listOf(1, 2, 3, 3).map { song(it).videoId }, queued.queue.items.map { it.videoId })
        val autoOnly = buildJsonObject { put("tracks", json.encodeToJsonElement(ListSerializer(PartyTrack.serializer()), listOf(song(2, true)))) }
        assertEquals(queued, control(queued, "queueAdd", autoOnly))
        assertTrue(queued.queue.items[1].fromAutoplay)
        assertFalse(queued.queue.items[2].fromAutoplay)
    }

    @Test fun removingAndMovingDuplicateTracksOnlyTouchUpcomingCopies() {
        val duplicate = song(1)
        val initial = listOf(duplicate, song(2), song(3), duplicate, song(4))
        val queued = setQueue(setTrack(room(), song(3)), initial, 2)
        val removed = control(queued, "queueRemove", buildJsonObject { put("videoId", duplicate.videoId) })
        assertEquals(listOf(duplicate, song(2), song(3), song(4)), removed.queue.items)
        assertEquals(2, removed.queue.index)
        val moved = control(queued, "queueMove", buildJsonObject {
            put("fromIndex", 3); put("toIndex", 4); put("videoId", duplicate.videoId)
        })
        assertEquals(listOf(duplicate, song(2), song(3), song(4), duplicate), moved.queue.items)
        assertEquals("invalid_move", assertFailsWith<FirebasePartyFailure> {
            control(queued, "queueMove", buildJsonObject { put("fromIndex", 2); put("toIndex", 3) })
        }.code)
        assertEquals("invalid_move", assertFailsWith<FirebasePartyFailure> {
            control(queued, "queueMove", buildJsonObject { put("fromIndex", 3); put("toIndex", 4); put("videoId", "stale-id") })
        }.code)
    }

    @Test fun malformedAndDeviceLocalTracksAreRejectedBeforeAnyCloudCommit() {
        assertEquals("invalid_track", assertFailsWith<FirebasePartyFailure> {
            setTrack(room(), PartyTrack("local:song", "Private file"))
        }.code)
        assertEquals("invalid_track", assertFailsWith<FirebasePartyFailure> {
            setTrack(room(), song(1).copy(thumbnailUrl = "file:///private/cover.jpg"))
        }.code)
        assertEquals("invalid_profile", assertFailsWith<FirebasePartyFailure> {
            FirebasePartyEngine.join(room(), "guest", "Guest", "content://private/avatar", now + 10)
        }.code)
        val invalid = room().copy(slots = mapOf("s0" to "outsider"))
        assertFailsWith<IllegalArgumentException> { invalid.validated() }
    }
}
