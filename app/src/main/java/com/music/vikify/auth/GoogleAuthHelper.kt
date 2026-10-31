package com.music.vikify.auth

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/**
 * Helper to initiate native Google Sign-In with device account discovery.
 * Uses play-services-auth to present the Android system account picker bottom sheet,
 * finding and listing all existing Google / Gmail accounts on the user's phone.
 */
object GoogleAuthHelper {
    // OAuth 2.0 Web Client ID registered in google-services.json for project vikify-95144
    const val WEB_CLIENT_ID = "794653884734-a6rc2ij1n85pqkg8ga7830cdtton5835.apps.googleusercontent.com"

    fun getGoogleSignInClient(activity: Activity): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(activity, gso)
    }

    fun getSignInIntent(activity: Activity): Intent {
        val client = getGoogleSignInClient(activity)
        return client.signInIntent
    }

    fun signOut(activity: Activity, onComplete: () -> Unit = {}) {
        getGoogleSignInClient(activity).signOut().addOnCompleteListener { onComplete() }
    }

    fun parseSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return runCatching {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            task.getResult(ApiException::class.java) ?: error("Failed to get Google account from intent")
        }
    }
}
