package com.music.vikify.auth

import android.app.Activity
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.music.vikify.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class GoogleIdentity(val idToken: String, val email: String, val displayName: String?)
class GoogleSignInCancelled : Exception()
private class GoogleSignInFailure(message: String) : Exception(message)

/** Native device-account selection; it never claims to supply a YouTube website session. */
object GoogleAuthHelper {
    internal const val TOKEN = "google_id_token"
    internal const val EMAIL = "google_email"
    internal const val NAME = "google_name"
    internal const val ERROR = "google_error"

    fun getSignInIntent(activity: Activity): Intent = Intent(activity, GoogleSignInActivity::class.java)

    internal suspend fun request(activity: Activity): GoogleIdentity {
        // The phone's Google Services plugin generates this resource. A TV
        // build without Firebase configuration supplies an empty placeholder.
        val clientId = activity.getString(R.string.default_web_client_id).trim()
        if (clientId.isEmpty()) throw GoogleSignInFailure("Google sign-in isn't configured for this app build.")
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val result = CredentialManager.create(activity).getCredential(
            context = activity,
            request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
        )
        val credential = result.credential
        check(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google did not return a sign-in credential."
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        return GoogleIdentity(google.idToken, google.id, google.displayName)
    }

    fun parseSignInResult(data: Intent?): Result<GoogleIdentity> = runCatching {
        if (data == null) throw GoogleSignInCancelled()
        data.getStringExtra(ERROR)?.let { throw GoogleSignInFailure(it) }
        val token = data.getStringExtra(TOKEN)?.takeIf(String::isNotBlank)
            ?: throw GoogleSignInFailure("Google couldn't finish signing in. Please try again.")
        val email = data.getStringExtra(EMAIL)?.takeIf(String::isNotBlank)
            ?: throw GoogleSignInFailure("Google didn't return an account. Please try again.")
        GoogleIdentity(token, email, data.getStringExtra(NAME))
    }

    internal fun userMessage(error: Throwable): String? = when (error) {
        is GetCredentialCancellationException, is GoogleSignInCancelled -> null
        is NoCredentialException -> "No Google account is available. Add an account in Android settings, then try again."
        is GetCredentialProviderConfigurationException, is GetCredentialUnsupportedException ->
            "Google sign-in isn't available on this device. Check that Google Play services is installed and up to date."
        is GoogleSignInFailure -> error.message
        else -> "Google couldn't finish signing in. Please try again."
    }

    fun signOut(activity: androidx.activity.ComponentActivity, onComplete: () -> Unit = {}) {
        activity.lifecycleScope.launch {
            try {
                CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Cloud sign-out must still work if the credential provider is unavailable.
            } finally {
                onComplete()
            }
        }
    }
}
