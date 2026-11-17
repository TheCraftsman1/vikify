package com.music.vikify.auth

import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

internal const val YOUTUBE_MUSIC_ORIGIN = "https://music.youtube.com"
internal const val SPOTIFY_PLAYER_ORIGIN = "https://open.spotify.com"

/** Persistent WebView names contain neither an email nor session credentials. */
internal fun youtubeBrowserProfile(accountId: String?): String =
    if (accountId.isNullOrBlank()) "vikify-youtube-login" else "vikify-youtube-" +
        MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }

/** These destinations still need their own website sessions after Google identifies the listener. */
internal enum class GoogleConnectionTarget { VIKIFY, YOUTUBE_MUSIC, SPOTIFY }

/** A page may expose a session only from the real secure Music origin. */
internal fun isYouTubeMusicPage(url: String?): Boolean = isSecurePage(url, "music.youtube.com")

internal fun isSpotifyPlayerPage(url: String?): Boolean = isSecurePage(url, "open.spotify.com")

internal fun isSpotifyLoginPage(url: String?): Boolean = isSecurePage(url, "accounts.spotify.com")

private fun isSecurePage(url: String?, host: String): Boolean = try {
    val uri = URI(url ?: "")
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals(host, ignoreCase = true) &&
        uri.userInfo == null && (uri.port == -1 || uri.port == 443)
} catch (_: Exception) { false }

internal fun spotifyLoginUrl(emailHint: String?): String {
    val base = "https://accounts.spotify.com/en/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
    val email = emailHint?.trim()?.takeIf { it.isNotBlank() } ?: return base
    return "$base&login_hint=${URLEncoder.encode(email, "UTF-8").replace("+", "%20")}"
}

internal fun youtubeLoginUrl(loginHint: String?, resumeSavedSession: Boolean): String {
    if (resumeSavedSession) return "$YOUTUBE_MUSIC_ORIGIN/"
    val hint = loginHint?.trim()?.takeIf { it.isNotBlank() }
    val account = if (hint == null) "&prompt=select_account" else {
        val email = URLEncoder.encode(hint, "UTF-8").replace("+", "%20")
        "&Email=$email&login_hint=$email"
    }
    return "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube$account" +
        "&continue=https%3A%2F%2Fmusic.youtube.com%2F"
}
