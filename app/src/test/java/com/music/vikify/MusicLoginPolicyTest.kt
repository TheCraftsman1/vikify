package com.music.vikify

import com.music.vikify.auth.isYouTubeMusicPage
import com.music.vikify.auth.youtubeLoginUrl
import com.music.vikify.auth.isSpotifyLoginPage
import com.music.vikify.auth.isSpotifyPlayerPage
import com.music.vikify.auth.spotifyLoginUrl
import com.music.vikify.auth.youtubeBrowserProfile
import org.junit.Assert.*
import org.junit.Test

class MusicLoginPolicyTest {
    @Test fun accountBrowserProfilesAreStableDistinctAndContainNoIdentity() {
        assertEquals(youtubeBrowserProfile("account-a"), youtubeBrowserProfile("account-a"))
        assertNotEquals(youtubeBrowserProfile("account-a"), youtubeBrowserProfile("account-b"))
        assertEquals(youtubeBrowserProfile(null), youtubeBrowserProfile(""))
        assertNotEquals(youtubeBrowserProfile(null), youtubeBrowserProfile("account-a"))
        val name = youtubeBrowserProfile("listener+music@example.com")
        assertFalse(name.contains("listener"))
        assertFalse(name.contains("@"))
        assertTrue(name.matches(Regex("vikify-youtube-[0-9a-f]{24}")))
    }
    @Test fun acceptsOnlyTheSecureMusicOrigin() {
        assertTrue(isYouTubeMusicPage("https://music.youtube.com/"))
        assertTrue(isYouTubeMusicPage("https://MUSIC.YOUTUBE.COM:443/watch?v=abc"))
        assertTrue(isYouTubeMusicPage("https://music.youtube.com/browse/library?authuser=1"))
    }

    @Test fun rejectsLookalikeHostsCredentialsAndOtherPorts() {
        listOf(null, "", "not a url", "http://music.youtube.com/", "https://music.youtube.com.evil.example/",
            "https://music.youtube.com@evil.example/", "https://evil.example@music.youtube.com/",
            "https://music.youtube.com:8443/", "https://accounts.google.com/", "javascript:alert(1)")
            .forEach { assertFalse(it, isYouTubeMusicPage(it)) }
    }

    @Test fun savedSessionReopensMusicWithoutSigningInAgain() {
        assertEquals("https://music.youtube.com/", youtubeLoginUrl("listener@example.com", true))
    }

    @Test fun newSessionOffersAccountSelection() {
        val url = youtubeLoginUrl(null, false)
        assertTrue(url.startsWith("https://accounts.google.com/ServiceLogin?"))
        assertTrue(url.contains("prompt=select_account"))
        assertFalse(url.contains("login_hint="))
    }

    @Test fun emailHintCannotInjectLoginParameters() {
        val url = youtubeLoginUrl(" listener+tag@example.com&continue=https://evil.example ", false)
        assertTrue(url.contains("login_hint=listener%2Btag%40example.com%26continue%3Dhttps%3A%2F%2Fevil.example"))
        assertEquals(1, Regex("&continue=").findAll(url).count())
        assertTrue(url.endsWith("&continue=https%3A%2F%2Fmusic.youtube.com%2F"))
    }

    @Test fun spotifySessionMayOnlyBeCapturedOnThePlayerOrigin() {
        assertTrue(isSpotifyPlayerPage("https://open.spotify.com/"))
        assertTrue(isSpotifyPlayerPage("https://OPEN.SPOTIFY.COM:443/collection/playlists"))
        listOf(null, "https://open.spotify.com.evil.example/", "https://open.spotify.com@evil.example/",
            "https://evil.example@open.spotify.com/", "http://open.spotify.com/", "https://open.spotify.com:8443/",
            "https://accounts.spotify.com/", "https://open.spotify.comevil.example/")
            .forEach { assertFalse(it, isSpotifyPlayerPage(it)) }
    }

    @Test fun spotifyEmailHintIsOnlyAppliedOnItsOwnLoginOrigin() {
        assertTrue(isSpotifyLoginPage("https://accounts.spotify.com/en/login"))
        listOf("https://accounts.google.com/", "https://accounts.spotify.com.evil.example/",
            "https://accounts.spotify.com@evil.example/", "http://accounts.spotify.com/",
            "https://evil.example@accounts.spotify.com/").forEach { assertFalse(isSpotifyLoginPage(it)) }
    }

    @Test fun spotifyEmailCannotChangeTheTrustedReturnDestination() {
        val url = spotifyLoginUrl(" person+music@example.com&continue=https://evil.example ")
        assertTrue(url.contains("login_hint=person%2Bmusic%40example.com%26continue%3Dhttps%3A%2F%2Fevil.example"))
        assertEquals(1, Regex("[?&]continue=").findAll(url).count())
        assertFalse(spotifyLoginUrl(" ").contains("login_hint="))
    }
}
