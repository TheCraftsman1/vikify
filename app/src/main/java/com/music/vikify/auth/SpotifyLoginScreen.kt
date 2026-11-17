package com.music.vikify.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

private val SPOTIFY_COOKIE_URLS = listOf(
    "https://open.spotify.com",
    "https://accounts.spotify.com",
    "https://www.spotify.com",
    "https://spotify.com",
)

private val LOGIN_HOST_SUFFIXES = listOf(
    "spotify.com", "scdn.co", "google.com", "gstatic.com", "googleapis.com",
    "googleusercontent.com", "facebook.com", "apple.com",
)

private fun hostMatches(uri: Uri?, suffixes: List<String>): Boolean {
    if (uri?.scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    return suffixes.any { host == it || host.endsWith(".$it") }
}

private fun isLoginHost(uri: Uri?) = hostMatches(uri, LOGIN_HOST_SUFFIXES)

/**
 * Signs the login WebView out of Spotify. Disconnecting only forgot the
 * stored cookie, and the WebView's own jar would have signed the next
 * "Sign in" straight back in. Isolated Spotify profiles are cleared fully;
 * the legacy fallback expires only Spotify cookies in the shared jar.
 */
fun clearSpotifyWebSession() {
    if (runCatching {
        BrowserProfiles.clear(BrowserProfiles.SPOTIFY_LOGIN).also { isolated ->
            if (isolated) BrowserProfiles.clear(BrowserProfiles.SPOTIFY_HARVEST)
        }
    }.getOrDefault(false)) return
    val cookies = CookieManager.getInstance()
    for (url in SPOTIFY_COOKIE_URLS) {
        val names = cookies.getCookie(url)?.split(";")
            ?.map { it.substringBefore("=").trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        for (name in names) {
            val expired = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
            cookies.setCookie(url, expired)
            cookies.setCookie(url, "$expired; Domain=.spotify.com")
        }
    }
    cookies.flush()
}

/**
 * In-app Spotify sign-in. Lets the real accounts.spotify.com page handle the
 * login and hands the `sp_dc` cookie to [onConnected] once the page lands on
 * open.spotify.com. An email hint identifies neither a Spotify session nor a
 * Google website session; the provider still owns authorization.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(
    emailHint: String? = null,
    onConnected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                val cookies = BrowserProfiles.attach(this, BrowserProfiles.SPOTIFY_LOGIN)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportMultipleWindows(false)
                setBackgroundColor(0xFF121212.toInt())
                cookies.setAcceptCookie(true)
                cookies.setAcceptThirdPartyCookies(this, true)

                var sent = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = !isLoginHost(request.url)

                    override fun onPageFinished(view: WebView, url: String?) {
                        canGoBack = view.canGoBack()

                        // Convenience only, on Spotify's actual login origin. Never enter passwords
                        // or inject an identifier into Google's authorization pages.
                        if (!emailHint.isNullOrBlank() && isSpotifyLoginPage(url)) {
                            val jsEmail = JSONObject.quote(emailHint)
                            val autoFillSnippet = """
                                (function() {
                                    try {
                                        var spInput = document.getElementById('login-username') ||
                                                      document.querySelector('input[data-testid="login-username"]') ||
                                                      document.querySelector('input[name="username"]') ||
                                                      document.querySelector('input[type="email"]');
                                        if (spInput && (!spInput.value || spInput.value.length === 0)) {
                                            spInput.value = $jsEmail;
                                            spInput.dispatchEvent(new Event('input', { bubbles: true }));
                                            spInput.dispatchEvent(new Event('change', { bubbles: true }));
                                        }
                                    } catch(e) {}
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(autoFillSnippet, null)
                        }

                        if (sent || !isSpotifyPlayerPage(url) || !isSpotifyPlayerPage(view.url)) return
                        val raw = cookies.getCookie("https://open.spotify.com") ?: return
                        val token = raw.split(";")
                            .map { it.trim() }
                            .firstOrNull { it.startsWith("sp_dc=") }
                            ?.substringAfter("=")
                            ?.takeIf { it.isNotBlank() }
                            ?: return
                        sent = true
                        cookies.flush()
                        view.visibility = View.GONE
                        onConnected(token)
                    }
                }
                webView = this
                // Reopening a valid website session should not start at an email form.
                if (!emailHint.isNullOrBlank() && BrowserProfiles.supported) {
                    BrowserProfiles.clear(BrowserProfiles.SPOTIFY_LOGIN)
                }
                BrowserProfiles.whenReady(BrowserProfiles.SPOTIFY_LOGIN) {
                    if (webView === this) {
                        val existing = cookies.getCookie(SPOTIFY_PLAYER_ORIGIN)
                        val hasSession = existing?.split(';')?.any {
                            it.substringBefore('=').trim() == "sp_dc" && it.substringAfter('=', "").isNotBlank()
                        } == true
                        loadUrl(if (hasSession && emailHint.isNullOrBlank()) "$SPOTIFY_PLAYER_ORIGIN/" else spotifyLoginUrl(emailHint))
                    }
                }
            }
        },
        onRelease = {
            BrowserProfiles.cookies(it).flush()
            webView = null
            it.stopLoading()
            it.destroy()
        },
    )

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}
