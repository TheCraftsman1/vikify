/*
 * Copyright (C) 2025 Vikify
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * EcoVisualizerViewModel - Battery-efficient audio visualization engine
 * Uses Android's Visualizer API to capture FFT data and throttles to ~30fps
 */

package com.vikify.app.viewmodels

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * EcoVisualizerViewModel
 *
 * Battery-efficient audio visualization engine that:
 * 1. Captures FFT data from Android's Visualizer API
 * 2. Throttles updates to ~30fps to prevent thread thrashing
 * 3. Exposes State<FloatArray> optimized for draw-phase consumption
 *
 * Key Optimization: Compose reads `amplitudes.value` inside Canvas.onDraw(),
 * which bypasses recomposition entirely. Only the draw phase is invalidated.
 */
@HiltViewModel
class EcoVisualizerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "EcoVisualizer"
        private const val BAR_COUNT = 32  // Number of frequency bars
        private const val FALLBACK_DECAY = 0.92f  // Smooth decay for fallback mode
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EXPOSED STATE - Read directly in onDraw() for zero-recomposition updates
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Normalized amplitude values for each frequency bar (0f-1f)
     * Read this INSIDE Canvas.onDraw() to bypass recomposition
     */
    private val _amplitudes = mutableStateOf(FloatArray(BAR_COUNT) { 0f })
    val amplitudes: State<FloatArray> = _amplitudes

    /**
     * Peak amplitude across all bars (0f-1f)
     * Use for button pulse, glow intensity, etc.
     */
    private val _peakAmplitude = mutableFloatStateOf(0f)
    val peakAmplitude: State<Float> = _peakAmplitude

    /**
     * Whether visualizer is actively capturing audio
     */
    private val _isActive = mutableStateOf(false)
    val isActive: State<Boolean> = _isActive

    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL STATE
    // ═══════════════════════════════════════════════════════════════════════

    private var visualizer: Visualizer? = null
    private var currentAudioSessionId: Int = 0
    private var fallbackJob: Job? = null

    // Smoothing for more pleasing visuals
    private val smoothedAmplitudes = FloatArray(BAR_COUNT) { 0f }
    private val smoothingFactor = 0.4f  // Lower = smoother, higher = more reactive

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Attach to an audio session for real-time FFT capture
     *
     * @param audioSessionId The audio session ID from ExoPlayer (player.audioSessionId)
     */
    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId == currentAudioSessionId && visualizer != null) {
            Log.d(TAG, "Already attached to session $audioSessionId")
            return
        }

        Log.d(TAG, "Attaching to audio session: $audioSessionId")
        release()
        currentAudioSessionId = audioSessionId

        // Check for RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission not granted, using fallback mode")
            startFallbackMode()
            return
        }

        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]  // Max for quality

                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            // Unused - we only need FFT
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            fft?.let { processFFT(it) }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,  // ~30fps throttling
                    false,  // No waveform capture
                    true    // FFT capture enabled
                )

                enabled = true
            }
            _isActive.value = true
            Log.d(TAG, "Visualizer attached successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Visualizer: ${e.message}")
            startFallbackMode()
        }
    }

    /**
     * Release visualizer resources
     * Call when playback stops or view is destroyed
     */
    fun release() {
        Log.d(TAG, "Releasing visualizer")
        fallbackJob?.cancel()
        fallbackJob = null

        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing visualizer: ${e.message}")
        }
        visualizer = null
        _isActive.value = false
        currentAudioSessionId = 0

        // Reset amplitudes
        _amplitudes.value = FloatArray(BAR_COUNT) { 0f }
        _peakAmplitude.floatValue = 0f
    }

    /**
     * Pause visualization (e.g., when playback pauses)
     * Keeps resources ready but stops updates
     */
    fun pause() {
        visualizer?.enabled = false
        fallbackJob?.cancel()
        _isActive.value = false

        // Decay amplitudes smoothly
        viewModelScope.launch {
            repeat(20) {
                val current = _amplitudes.value.clone()
                for (i in current.indices) {
                    current[i] *= 0.85f
                }
                _amplitudes.value = current
                _peakAmplitude.floatValue *= 0.85f
                delay(16)
            }
            _amplitudes.value = FloatArray(BAR_COUNT) { 0f }
            _peakAmplitude.floatValue = 0f
        }
    }

    /**
     * Resume visualization after pause
     */
    fun resume() {
        if (visualizer != null) {
            try {
                visualizer?.enabled = true
                _isActive.value = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resume visualizer: ${e.message}")
                startFallbackMode()
            }
        } else if (currentAudioSessionId != 0) {
            attachToAudioSession(currentAudioSessionId)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL PROCESSING
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Process raw FFT data into normalized bar amplitudes
     */
    private fun processFFT(fft: ByteArray) {
        val n = BAR_COUNT.coerceAtMost(fft.size / 2)
        val newAmplitudes = FloatArray(BAR_COUNT)

        for (i in 0 until n) {
            // FFT data is interleaved real/imaginary pairs
            val real = fft[i * 2].toInt()
            val imag = fft[i * 2 + 1].toInt()
            val magnitude = sqrt((real * real + imag * imag).toDouble()).toFloat()

            // Normalize to 0-1 range with some headroom
            val normalized = (magnitude / 180f).coerceIn(0f, 1f)

            // Apply smoothing for pleasing visual transitions
            smoothedAmplitudes[i] = smoothedAmplitudes[i] * (1f - smoothingFactor) +
                    normalized * smoothingFactor

            newAmplitudes[i] = smoothedAmplitudes[i]
        }

        // Fill remaining bars if needed
        for (i in n until BAR_COUNT) {
            newAmplitudes[i] = smoothedAmplitudes[i] * 0.9f
            smoothedAmplitudes[i] = newAmplitudes[i]
        }

        _amplitudes.value = newAmplitudes
        _peakAmplitude.floatValue = newAmplitudes.maxOrNull() ?: 0f
    }

    /**
     * Fallback mode: Generate pleasing fake visualization when Visualizer unavailable
     * Uses beat-simulation heuristics for a "good enough" visual experience
     */
    private fun startFallbackMode() {
        fallbackJob?.cancel()
        _isActive.value = true

        fallbackJob = viewModelScope.launch(Dispatchers.Default) {
            Log.d(TAG, "Starting fallback visualization mode")
            var phase = 0f

            while (isActive) {
                phase += 0.15f

                val newAmplitudes = FloatArray(BAR_COUNT) { i ->
                    // Create a pleasing wave pattern
                    val wave1 = kotlin.math.sin(phase + i * 0.3f).toFloat() * 0.3f
                    val wave2 = kotlin.math.sin(phase * 0.7f + i * 0.15f).toFloat() * 0.2f
                    val base = 0.25f + (kotlin.math.sin(i * 0.5f).toFloat() * 0.15f)

                    // Apply decay to smoothed values
                    val target = (base + wave1 + wave2).coerceIn(0.1f, 0.8f)
                    smoothedAmplitudes[i] = smoothedAmplitudes[i] * FALLBACK_DECAY +
                            target * (1f - FALLBACK_DECAY)
                    smoothedAmplitudes[i]
                }

                withContext(Dispatchers.Main) {
                    _amplitudes.value = newAmplitudes
                    _peakAmplitude.floatValue = newAmplitudes.maxOrNull() ?: 0f
                }

                delay(33)  // ~30fps
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════

    override fun onCleared() {
        release()
        super.onCleared()
    }
}
