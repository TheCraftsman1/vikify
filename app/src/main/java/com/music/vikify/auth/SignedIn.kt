package com.music.vikify.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a YouTube account is signed in, as one flow for the whole process.
 *
 * [MainViewModel][com.music.vikify.ui.MainViewModel] owns sign-in and sign-out
 * refreshes it from persisted credentials; the playback service reads it, so the surfaces it draws — the
 * notification, the home-screen widgets, the watch — offer Like on exactly the
 * tracks the player does. A service started without the app (a widget tap, the
 * watch) seeds it from [AuthStore] the same way the view model does.
 */
object SignedIn {

    private class Snapshot(signedIn: Boolean) {
        val mutable = MutableStateFlow(signedIn)
        val readOnly: StateFlow<Boolean> = mutable.asStateFlow()
    }

    @Volatile private var snapshot: Snapshot? = null

    fun state(context: Context): StateFlow<Boolean> =
        (snapshot ?: synchronized(this) {
            snapshot ?: Snapshot(AuthStore(context.applicationContext).isSignedIn).also { snapshot = it }
        }).readOnly

    /** Publish only the state derived from stored, structurally usable credentials. */
    internal fun refreshFrom(store: AuthStore) = synchronized(this) {
        val signedIn = store.isSignedIn
        val current = snapshot ?: Snapshot(signedIn).also { snapshot = it }
        current.mutable.value = signedIn
    }
}
