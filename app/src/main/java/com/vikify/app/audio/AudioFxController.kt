package com.vikify.app.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioFxController @Inject constructor() {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var audioSessionId: Int = 0

    // State
    private val _equalizerBands = MutableStateFlow<List<EqBand>>(emptyList())
    val equalizerBands = _equalizerBands.asStateFlow()

    private val _bassStrength = MutableStateFlow(0)
    val bassStrength = _bassStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength = _virtualizerStrength.asStateFlow()
    
    // Band data helper
    data class EqBand(
        val index: Short,
        val centerFreqHz: Int,
        val minLevel: Short,
        val maxLevel: Short,
        val currentLevel: Short
    )

    fun release() {
        equalizer?.release()
        bassBoost?.release()
        virtualizer?.release()
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    fun attachToSession(sessionId: Int) {
        if (audioSessionId == sessionId && equalizer != null) return
        
        release()
        audioSessionId = sessionId
        
        try {
            // Equalizer
            equalizer = Equalizer(0, sessionId).apply {
                enabled = true
                updateBandsState(this)
            }

            // Bass Boost
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = true
                setStrength(_bassStrength.value.toShort())
            }

            // Virtualizer
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = true
                setStrength(_virtualizerStrength.value.toShort())
            }
            
            Log.d("AudioFxController", "Attached effects to session $sessionId")
        } catch (e: Exception) {
            Log.e("AudioFxController", "Failed to attach audio effects", e)
        }
    }

    private fun updateBandsState(eq: Equalizer) {
        val numBands = eq.numberOfBands
        val range = eq.bandLevelRange // [min, max]
        val bands = mutableListOf<EqBand>()
        for (i in 0 until numBands) {
            val idx = i.toShort()
            bands.add(
                EqBand(
                    index = idx,
                    centerFreqHz = eq.getCenterFreq(idx) / 1000,
                    minLevel = range[0],
                    maxLevel = range[1],
                    currentLevel = eq.getBandLevel(idx)
                )
            )
        }
        _equalizerBands.value = bands
    }

    fun setBandLevel(bandIndex: Short, level: Short) {
        equalizer?.let { eq ->
            eq.setBandLevel(bandIndex, level)
            updateBandsState(eq)
        }
    }

    fun setBassStrength(strength: Short) {
        bassBoost?.let { bb ->
            bb.setStrength(strength)
            _bassStrength.value = strength.toInt()
        }
    }

    fun setVirtualizerStrength(strength: Short) {
        virtualizer?.let { v ->
            v.setStrength(strength)
            _virtualizerStrength.value = strength.toInt()
        }
    }
}
