package com.music.vikify.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A private activity-result bridge shared by onboarding, Home, Library and Settings. */
class GoogleSignInActivity : ComponentActivity() {
    private var requested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        // A killed picker cannot safely restore an in-flight credential request.
        // Return cancellation instead of starting a second account chooser.
        if (savedInstanceState?.getBoolean("requested") == true) {
            requested = true
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("requested", requested)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (requested) return
        requested = true
        lifecycleScope.launch {
            try {
                val account = GoogleAuthHelper.request(this@GoogleSignInActivity)
                setResult(Activity.RESULT_OK, Intent().apply {
                    putExtra(GoogleAuthHelper.TOKEN, account.idToken)
                    putExtra(GoogleAuthHelper.EMAIL, account.email)
                    putExtra(GoogleAuthHelper.NAME, account.displayName)
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = GoogleAuthHelper.userMessage(error)
                setResult(Activity.RESULT_CANCELED, message?.let { Intent().putExtra(GoogleAuthHelper.ERROR, it) })
            } finally {
                finish()
            }
        }
    }
}
