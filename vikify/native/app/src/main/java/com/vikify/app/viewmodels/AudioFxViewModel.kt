package com.vikify.app.viewmodels

import androidx.lifecycle.ViewModel
import com.vikify.app.audio.AudioFxController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AudioFxViewModel @Inject constructor(
    private val audioFxController: AudioFxController
) : ViewModel() {

    val equalizerBands = audioFxController.equalizerBands
    val bassStrength = audioFxController.bassStrength
    val virtualizerStrength = audioFxController.virtualizerStrength

    fun setBandLevel(bandIndex: Short, level: Short) {
        audioFxController.setBandLevel(bandIndex, level)
    }

    fun setBassStrength(strength: Int) {
        audioFxController.setBassStrength(strength.toShort())
    }

    fun setVirtualizerStrength(strength: Int) {
        audioFxController.setVirtualizerStrength(strength.toShort())
    }
}
