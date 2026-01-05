package com.vikify.app.spotify

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Spotify integration
 */
@HiltViewModel
class SpotifyViewModel @Inject constructor(
    application: Application,
    private val repository: SpotifyRepository
) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(SpotifyUiState())
    val uiState: StateFlow<SpotifyUiState> = _uiState.asStateFlow()
    
    init {
        // Check if user still has a valid connection (token or refresh token)
        if (repository.isLoggedIn) {
            // Immediately update UI state to show connected
            _uiState.value = _uiState.value.copy(isLoggedIn = true)
            // Load full user data in background
            loadUserData()
        }
    }
    
    val isLoggedIn: Boolean
        get() = repository.isLoggedIn
    
    fun getLoginIntent(): Intent = repository.startLogin()
    
    fun handleAuthCallback(code: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val success = repository.exchangeCodeForToken(code)
            if (success) {
                loadUserData()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to login"
                )
            }
        }
    }
    
    fun loadUserData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val user = repository.getCurrentUser()
            val playlists = repository.getUserPlaylists()
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                user = user,
                playlists = playlists,
                isLoggedIn = user != null
            )
        }
    }
    
    fun logout() {
        repository.logout()
        _uiState.value = SpotifyUiState()
    }
}

data class SpotifyUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val user: SpotifyUser? = null,
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val error: String? = null
)
