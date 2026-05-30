package com.vikify.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vikify.app.ui.models.AmbientModePreference
import com.vikify.app.ui.models.AmbientModeState
import com.vikify.app.ui.models.AmbientModeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AmbientModeViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    // Ambient Mode State
    private val _ambientMode = MutableStateFlow(AmbientModeState())
    val ambientMode = _ambientMode.asStateFlow()

    private val _ambientPreference = MutableStateFlow(AmbientModePreference.AUTO)
    val ambientPreference = _ambientPreference.asStateFlow()

    init {
        startAmbientModeMonitoring()
    }

    /**
     * Ambient Mode Monitoring
     * Checks every 30 seconds if conditions for ambient mode are met
     */
    private fun startAmbientModeMonitoring() {
        viewModelScope.launch {
            while (isActive) {
                updateAmbientMode()
                delay(30_000) // Check every 30 seconds
            }
        }
    }

    /**
     * Check and update ambient mode based on current conditions
     * Respects user preference:
     * - ALWAYS_ACTIVE: Never enter ambient mode
     * - ALWAYS_AMBIENT: Always stay in ambient mode
     * - AUTO: Time-based (10PM-5AM)
     */
    private fun updateAmbientMode() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isLateNight = currentHour >= 22 || currentHour < 5

        val targetMode = when (_ambientPreference.value) {
            AmbientModePreference.ALWAYS_ACTIVE -> AmbientModeType.ACTIVE
            AmbientModePreference.ALWAYS_AMBIENT -> AmbientModeType.AMBIENT
            AmbientModePreference.AUTO -> {
                if (isLateNight) AmbientModeType.AMBIENT else AmbientModeType.ACTIVE
            }
        }

        if (_ambientMode.value.mode != targetMode) {
            _ambientMode.value = when (targetMode) {
                AmbientModeType.AMBIENT -> AmbientModeState(
                    mode = AmbientModeType.AMBIENT,
                    navOpacity = 0.6f,
                    textBrightness = 0.8f,
                    glowRadiusMultiplier = 2f,
                    backgroundSpeedMultiplier = 0.6f
                )
                AmbientModeType.ACTIVE -> AmbientModeState(
                    mode = AmbientModeType.ACTIVE,
                    navOpacity = 1f,
                    textBrightness = 1f,
                    glowRadiusMultiplier = 1f,
                    backgroundSpeedMultiplier = 1f
                )
                AmbientModeType.MINIMAL -> AmbientModeState(
                    mode = AmbientModeType.MINIMAL,
                    navOpacity = 0.8f,
                    textBrightness = 0.9f,
                    glowRadiusMultiplier = 0.8f,
                    backgroundSpeedMultiplier = 0.4f
                )
            }
        }
    }

    fun setAmbientModePreference(preference: AmbientModePreference) {
        _ambientPreference.value = preference
        updateAmbientMode() // Apply immediately
    }
}
