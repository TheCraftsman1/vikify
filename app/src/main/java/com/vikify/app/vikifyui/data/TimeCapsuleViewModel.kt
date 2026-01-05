package com.vikify.app.vikifyui.data

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimeCapsuleState(
    val isLoading: Boolean = true,
    val totalMinutes: Long = 0,
    val topArtist: String = "Loading...",
    val topArtistImageUrl: String? = null,
    val genres: List<GenrePlanet> = emptyList()
)

data class GenrePlanet(
    val name: String,
    val minutes: Long,
    val colorHex: String,
    val orbitSpeed: Float
)

@HiltViewModel
class TimeCapsuleViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimeCapsuleState())
    val uiState: StateFlow<TimeCapsuleState> = _uiState.asStateFlow()

    init {
        fetchStats()
    }

    fun fetchStats() {
        val uid = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Real-time listener for "Sonic DNA" updates
            firestore.collection("users").document(uid).collection("stats").document("summary")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("TimeCapsuleViewModel", "Listen failed.", e)
                        _uiState.update { it.copy(isLoading = false) }
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val totalMins = snapshot.getLong("total_minutes") ?: 0
                        val topArtist = snapshot.getString("top_artist") ?: "Pending..."
                        
                        // Fake genre generation for visualization demo (until we have real aggregation)
                        val demoGenres = listOf(
                            GenrePlanet("Pop", totalMins / 2, "#FF6B6B", 1.5f),
                            GenrePlanet("Hip Hop", totalMins / 3, "#4ECDC4", 1.2f),
                            GenrePlanet("Indie", totalMins / 6, "#FFFF00", 0.8f)
                        )

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                totalMinutes = totalMins,
                                topArtist = topArtist,
                                genres = demoGenres
                            )
                        }
                    } else {
                        Log.d("TimeCapsuleViewModel", "Current data: null")
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
        }
    }
}
