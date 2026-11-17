package com.music.vikify

import com.music.vikify.data.listentogether.JamInviteLink
import com.music.vikify.data.listentogether.ListenTogether
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyDeploymentClientTest {
    @Test fun generatedVikifyInvitesRoundTripWithTheirServer() {
        val server = "https://party.example.com/music"
        val url = JamInviteLink.schemeUrl("ab12cd", server)
        assertTrue(url.startsWith("vikify://party/AB12CD"))
        val parsed = assertNotNullInvite(url)
        assertEquals("AB12CD", parsed.code)
        assertEquals(server, parsed.serverUrl)
    }

    @Test fun legacyInvitesRemainReadableWithoutAcceptingOtherSchemesOrHosts() {
        val current = JamInviteLink.parseInvite("vikify://party/AB12CD?server=https%3A%2F%2Fparty.example.com")
        val legacy = JamInviteLink.parseInvite("bitchord://party/AB12CD?server=https%3A%2F%2Fparty.example.com")
        assertEquals(current, legacy)
        assertNotNull(current)
        assertNull(JamInviteLink.parseInvite("other://party/AB12CD"))
        assertNull(JamInviteLink.parseInvite("vikify://account/AB12CD"))
    }

    @Test fun missingDeploymentHasAnActionableErrorWithoutPretendingTheRoomExpired() {
        listOf("Not Found", "<!doctype html><title>404</title>", "{\"message\":\"Not Found\"}").forEach { body ->
            val error = ListenTogether.parsePartyError(404, body)
            assertEquals("http_404", error.code)
            assertEquals(404, error.statusCode)
            assertEquals("Party server isn't deployed at this address. Check server settings.", error.message)
            assertFalse(ListenTogether.isEligibleForFallback(error))
        }
    }

    @Test fun realServerRoomAndIdentityErrorsKeepTheirMeaning() {
        val expired = ListenTogether.parsePartyError(404,
            """{"error":"no_such_party","message":"No party with that code."}""")
        assertEquals("no_such_party", expired.code)
        assertEquals("No party with that code.", expired.message)
        assertFalse(ListenTogether.isEligibleForFallback(expired))
        val identity = ListenTogether.parsePartyError(422, "invalid request")
        assertEquals("This account can't be used to jam.", identity.message)
        assertTrue(ListenTogether.isEligibleForFallback(ListenTogether.parsePartyError(503, "Unavailable")))
    }

    @Test fun formattedBooleanHealthWorksThroughTheActualHttpProbe() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("""
                {
                  "ok": true,
                  "serverMs": 1800000000000
                }
            """.trimIndent()))
            val result = ListenTogether.probeHealthWithLatency(server.url("/").toString(), 2_000)
            assertTrue(result.isOnline)
            assertEquals("/healthz", server.takeRequest().path)
        }
    }

    @Test fun fakeHealthResponsesDoNotMarkAnUnrelatedSiteOnline() {
        assertFalse(ListenTogether.isHealthResponse(404, "{\"ok\":true}"))
        assertFalse(ListenTogether.isHealthResponse(200, "<html>{\"ok\":true}</html>"))
        assertFalse(ListenTogether.isHealthResponse(200, "{\"ok\":\"true\"}"))
        assertFalse(ListenTogether.isHealthResponse(200, "{\"ok\":false}"))
        assertFalse(ListenTogether.isHealthResponse(200, "[{\"ok\":true}]"))
        assertFalse(ListenTogether.isHealthResponse(200, "{\"service\":\"portfolio\"}"))
        assertTrue(ListenTogether.isHealthResponse(200, "{\"ok\":true}"))
    }

    private fun assertNotNullInvite(value: String) = requireNotNull(JamInviteLink.parseInvite(value))
}
