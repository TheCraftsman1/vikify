package com.music.vikify

import com.music.vikify.data.listentogether.FirebasePartyCodec
import com.music.vikify.data.listentogether.FirebasePartyEngine
import com.music.vikify.data.listentogether.FirebasePartyRoom
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** The same native client payloads are also fed through the local RTDB rules emulator. */
class FirebasePartyInteropTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    @Test fun nativePayloadsRoundTripFirebaseStorageAndGenerateRulesScenarios() {
        val born = 1_700_000_000_000L
        var now = born
        var room = FirebasePartyEngine.create("PARTY1", "host", "Host", null, 5, false, now)
        val steps = mutableListOf<JsonElement>()
        fun record(uid: String, state: FirebasePartyRoom?) {
            if (state != null) {
                assertEquals(state, FirebasePartyCodec.decode(storage(FirebasePartyCodec.encode(state))))
            }
            steps += buildJsonObject {
                put("uid", uid)
                put("room", state?.let { json.encodeToJsonElement(FirebasePartyRoom.serializer(), it) } ?: JsonNull)
            }
        }
        fun change(uid: String, action: (Long) -> FirebasePartyRoom) {
            now += 10
            room = action(now)
            record(uid, room)
        }
        fun command(action: String, body: String = "{}") = change("host") {
            FirebasePartyEngine.control(room, "host", action, json.parseToJsonElement(body).jsonObject, it)
        }
        fun track(id: String) = """{"videoId":"$id","title":"Song","artist":"Artist","durationMs":180000}"""
        val a = "aaaaaaaaaaa"; val b = "bbbbbbbbbbb"; val c = "ccccccccccc"
        record("host", room)
        change("guest") { FirebasePartyEngine.join(room, "guest", "Guest", null, it) }
        command("setQueue", """{"queue":[${track(a)},${track(b)},${track(c)}],"queueIndex":0}""")
        command("setTrack", """{"track":${track(a)}}""")
        command("pause", """{"positionMs":1000}""")
        command("play", """{"positionMs":1000}""")
        command("seek", """{"positionMs":2000}""")
        command("queueAdd", """{"tracks":[${track("ddddddddddd") }]}""")
        command("queueMove", """{"fromIndex":1,"toIndex":2}""")
        command("queueRemove", """{"videoId":"$b"}""")
        command("next")
        command("previous")
        command("queueClear")
        command("setAutoplay", """{"enabled":true}""")
        command("setHostOnlyControl", """{"enabled":true}""")
        command("setMaxMembers", """{"maxMembers":2}""")
        change("guest") { checkNotNull(FirebasePartyEngine.leave(room, "guest", it)) }
        change("third") { FirebasePartyEngine.join(room, "third", "Third", null, it) }
        change("host") { checkNotNull(FirebasePartyEngine.leave(room, "host", it)) }
        now += 10
        room = FirebasePartyEngine.control(room, "third", "pause", buildJsonObject {}, now)
        record("third", room)
        now += 10
        assertEquals(null, FirebasePartyEngine.leave(room, "third", now))
        record("third", null)
        System.getenv("VIKIFY_PARTY_FIXTURE_OUTPUT")?.let { path ->
            File(path).writeText(buildJsonObject {
                put("baseMs", born)
                put("steps", JsonArray(steps))
            }.toString())
        }
    }

    /** RTDB omits empty children and SDK snapshots can expose whole numbers as Double. */
    private fun storage(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.mapValues { storage(it.value) }.filterValues { it != null }.takeIf { it.isNotEmpty() }
        is List<*> -> value.map(::storage).takeIf { it.isNotEmpty() }
        is Number -> value.toDouble()
        else -> value
    }
}
