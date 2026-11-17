package com.music.vikify

import com.music.vikify.data.listentogether.FirebasePartyCodec
import com.music.vikify.data.listentogether.FirebasePartyEngine
import com.music.vikify.data.listentogether.PartyTrack
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FirebasePartyCodecTest {
    private val now = 1_800_000_000_000L
    private val json = Json { encodeDefaults = true }
    private fun created() = FirebasePartyEngine.create("TEST12", "host", "Host", null, 5, false, now)

    @Test fun realtimeDatabaseOmissionOfEmptyChildrenKeepsDefaultPlaybackAndQueue() {
        val room = created()
        assertEquals(room, FirebasePartyCodec.decode(pruneEmpty(FirebasePartyCodec.encode(room))))
        assertNull(FirebasePartyCodec.decode(null))
    }

    @Test fun aRealSharedQueueAndMembershipSurviveTheUntypedDatabaseBoundary() {
        val joined = FirebasePartyEngine.join(created(), "guest", "Guest", "https://example.com/guest.png", now + 10)
        val tracks = listOf(PartyTrack("abcdefghijk", "First", "Artist", durationMs = 180_000),
            PartyTrack("lmnopqrstuv", "Second", "Artist", durationMs = 200_000, fromAutoplay = true))
        val room = FirebasePartyEngine.control(joined, "host", "setQueue", buildJsonObject {
            put("queue", json.encodeToJsonElement(ListSerializer(PartyTrack.serializer()), tracks))
            put("queueIndex", 0)
        }, now + 100)
        val decoded = FirebasePartyCodec.decode(pruneEmpty(FirebasePartyCodec.encode(room)))!!
        assertEquals(room, decoded)
        assertEquals(listOf("abcdefghijk", "lmnopqrstuv"), decoded.queue.items.map { it.videoId })
        assertEquals(mapOf("s0" to "host", "s1" to "guest"), decoded.slots)
    }

    @Test fun malformedRoomMembershipCannotEnterThePlayerState() {
        val room = FirebasePartyCodec.encode(created())
        assertThrows(IllegalArgumentException::class.java) {
            FirebasePartyCodec.decode(room + ("hostUid" to "not-a-member"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FirebasePartyCodec.decode(room + ("slots" to mapOf("s0" to "someone-else")))
        }
    }

    /** RTDB drops empty maps/lists and null children instead of storing JSON's empty structures. */
    private fun pruneEmpty(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.mapValues { pruneEmpty(it.value) }.filterValues { it != null }.takeIf { it.isNotEmpty() }
        is List<*> -> value.map(::pruneEmpty).takeIf { it.isNotEmpty() }
        else -> value
    }
}
