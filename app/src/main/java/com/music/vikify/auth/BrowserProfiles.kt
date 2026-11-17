package com.music.vikify.auth

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** Separate provider cookies, storage and service workers, including Google's federated login. */
internal object BrowserProfiles {
    const val SPOTIFY_LOGIN = "vikify-spotify-login"
    const val SPOTIFY_HARVEST = "vikify-spotify-harvest"
    // Profile APIs and all callers are on the main thread. A fresh view must
    // await pending asynchronous sign-out before reading or restoring cookies.
    private val clearing = mutableMapOf<String, MutableList<() -> Unit>>()

    fun whenReady(name: String, action: () -> Unit) {
        val waiting = clearing[name]
        if (waiting == null) action() else waiting += action
    }

    val supported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    /** Must be the first operation on a newly constructed WebView. */
    fun attach(view: WebView, name: String): CookieManager {
        if (supported) WebViewCompat.setProfile(view, name)
        return cookies(view)
    }

    fun cookies(view: WebView): CookieManager =
        if (supported) WebViewCompat.getProfile(view).cookieManager else CookieManager.getInstance()

    fun storage(view: WebView): WebStorage =
        if (supported) WebViewCompat.getProfile(view).webStorage else WebStorage.getInstance()

    /** False means this provider only supports the legacy, shared cookie jar. */
    fun clear(name: String): Boolean {
        if (!supported) return false
        val store = ProfileStore.getInstance()
        if (name in clearing) return true
        // Do not create an empty profile just to disconnect it.
        if (name in store.allProfileNames) {
            val profile = store.getOrCreateProfile(name)
            clearing[name] = mutableListOf()
            profile.webStorage.deleteAllData()
            profile.cookieManager.removeAllCookies {
                clearing.remove(name)?.forEach { action -> action() }
            }
        }
        return true
    }
}
