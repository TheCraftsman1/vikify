package com.music.vikify

import com.music.vikify.dj.NemotronCodec
import com.music.vikify.dj.nemotronFailureMessage
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class NemotronClientTest {
    private fun response(content: String, finish: String = "stop", tool: Boolean = false) = buildJsonObject {
        putJsonArray("choices") { add(buildJsonObject {
            put("finish_reason", finish)
            putJsonObject("message") {
                put("role", "assistant"); put("content", content)
                put("reasoning_content", "Private reasoning is never surfaced")
                if (tool) putJsonArray("tool_calls") { add(buildJsonObject { put("id", "untrusted") }) }
            }
        }) }
    }.toString()

    @Test fun `direct request separates context from system and disables thinking and streaming`() {
        val context = "{\"transcript\":\"hi I am Vishnu\"}"
        val request = Json.parseToJsonElement(NemotronCodec.request("nvidia/nemotron-3.5-lightning-30b-a3b", "Reply as a companion.", context)).jsonObject
        assertEquals(false, request["stream"]!!.jsonPrimitive.boolean)
        assertEquals(false, request["chat_template_kwargs"]!!.jsonObject["enable_thinking"]!!.jsonPrimitive.boolean)
        assertEquals("json_object", request["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val messages = request["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals(context, messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertFalse(request.containsKey("api_key"))
    }

    @Test fun `only final JSON content is returned and private reasoning is ignored`() {
        assertEquals("{\"reply\":\"Hi Vishnu!\"}", NemotronCodec.content(response("{\"reply\":\"Hi Vishnu!\"}")))
        assertEquals("{\"reply\":\"Hello\"}", NemotronCodec.content(response("```json\n{\"reply\":\"Hello\"}\n```")))
    }

    @Test fun `tool calls reasoning prose arrays and truncated generation cannot become app actions`() {
        val valid = "{\"action\":\"play\"}"
        assertNull(NemotronCodec.content(response(valid, tool = true)))
        assertNull(NemotronCodec.content(response(valid, finish = "length")))
        assertNull(NemotronCodec.content(response("<think>pick a song</think>$valid")))
        assertNull(NemotronCodec.content(response("Here you go: $valid")))
        assertNull(NemotronCodec.content(response("[]")))
        assertNull(NemotronCodec.content(response("{\"reply\":\"" + "x".repeat(17_000) + "\"}")))
    }

    @Test fun `NVIDIA key rate limit and unavailable model failures have different fixes`() {
        assertTrue(nemotronFailureMessage(401).contains("API key"))
        assertTrue(nemotronFailureMessage(404).contains("model name"))
        assertTrue(nemotronFailureMessage(429).contains("Wait a moment"))
        assertFalse(nemotronFailureMessage(503).contains("API key"))
    }
}
