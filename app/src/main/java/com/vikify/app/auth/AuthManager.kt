/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package com.vikify.app.auth

import android.content.Context
import android.util.Log
import com.vikify.app.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Auth Manager - Handles Firebase Authentication (Google & Anonymous)
 */
@Singleton
class AuthManager @Inject constructor() {
    
    private val TAG = "AuthManager"
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    
    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser = _currentUser.asStateFlow()
    
    // Auth State
    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        data class Success(val user: FirebaseUser) : AuthState()
        data class Error(val message: String) : AuthState()
    }
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    /**
     * Configure Google Sign In
     */
    fun getGoogleSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
            
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * Sign in with Google ID Token
     */
    suspend fun signInWithGoogle(idToken: String) {
        try {
            _authState.value = AuthState.Loading
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user
            
            if (user != null) {
                _currentUser.value = user
                _authState.value = AuthState.Success(user)
                Log.d(TAG, "Google Sign In Success: ${user.email}")
            } else {
                _authState.value = AuthState.Error("Sign in failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign In Error", e)
            _authState.value = AuthState.Error(e.message ?: "Authentication failed")
        }
    }

    /**
     * Sign in as Guest (Anonymous)
     */
    suspend fun signInAnonymously() {
        try {
            _authState.value = AuthState.Loading
            val authResult = auth.signInAnonymously().await()
            val user = authResult.user
            
            if (user != null) {
                _currentUser.value = user
                _authState.value = AuthState.Success(user)
                Log.d(TAG, "Guest Login Success")
            } else {
                _authState.value = AuthState.Error("Guest login failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Guest Login Error", e)
            _authState.value = AuthState.Error(e.message ?: "Guest login failed")
        }
    }

    /**
     * Sign Out
     */
    fun signOut(context: Context) {
        auth.signOut()
        getGoogleSignInClient(context).signOut()
        _currentUser.value = null
        _authState.value = AuthState.Idle
    }
}
