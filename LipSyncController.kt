package com.example.avatar

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class LipSyncFrame(
    val isActive: Boolean = false,
    val mouthOpenness: Float = 0f,
    val mouthWidth: Float = 0.62f,
    val source: LipSyncSource = LipSyncSource.NONE,
    val playbackPositionMs: Long = 0L
)

/**
 * Generates smooth 25 FPS (40ms) lip-sync frames synchronized with [SpeechStateManager].
 *
 * Uses real PCM amplitude analysis when [LipSyncSource.PCM_AMPLITUDE_ENVELOPE] is active,
 * and clearly distinguishes approximate syllable-timing fallback
 * ([LipSyncSource.APPROXIMATE_SYLLABLE_TIMING]) from true phoneme-level lip sync.
 */
class LipSyncController(
    private val speechStateManager: SpeechStateManager,
    private val scope: CoroutineScope,
    private val frameIntervalMs: Long = 40L // 25 FPS (within 20-30 FPS target)
) {

    private val _lipSyncFrame = MutableStateFlow(LipSyncFrame())
    val lipSyncFrame: StateFlow<LipSyncFrame> = _lipSyncFrame.asStateFlow()

    private var stateCollectorJob: Job? = null
    private var frameTickerJob: Job? = null
    private var smoothedOpenness = 0f
    private var smoothedWidth = 0.62f

    fun start() {
        if (stateCollectorJob?.isActive == true) return
        stateCollectorJob = scope.launch {
            speechStateManager.playbackState.collect { speechState ->
                if (speechState.status == SpeechPlaybackStatus.SPEAKING) {
                    ensureFrameTickerRunning()
                } else {
                    stopFrameTickerAndCloseMouth()
                }
            }
        }
    }

    private fun ensureFrameTickerRunning() {
        if (frameTickerJob?.isActive == true) return
        frameTickerJob = scope.launch {
            while (isActive) {
                val state = speechStateManager.playbackState.value
                if (state.status != SpeechPlaybackStatus.SPEAKING) {
                    stopFrameTickerAndCloseMouth()
                    break
                }

                val targetOpen = state.currentAmplitude.coerceIn(0f, 1f)
                val targetWidth = when (state.lipSyncSource) {
                    LipSyncSource.PCM_AMPLITUDE_ENVELOPE -> {
                        // Shape mouth width smoothly with vocal energy (wider on open peaks)
                        (0.54f + targetOpen * 0.36f).coerceIn(0.45f, 0.92f)
                    }
                    LipSyncSource.APPROXIMATE_SYLLABLE_TIMING -> {
                        widthHintForToken(state.wordToken)
                    }
                    LipSyncSource.NONE -> 0.62f
                }

                // Smooth attack / natural release without sudden snaps or random oscillation
                val openLerp = if (targetOpen > smoothedOpenness) 0.58f else 0.38f
                smoothedOpenness += (targetOpen - smoothedOpenness) * openLerp
                smoothedWidth += (targetWidth - smoothedWidth) * 0.42f

                val clampedOpen = if (smoothedOpenness < 0.03f) 0f else smoothedOpenness.coerceIn(0f, 1f)
                _lipSyncFrame.value = LipSyncFrame(
                    isActive = true,
                    mouthOpenness = clampedOpen,
                    mouthWidth = smoothedWidth.coerceIn(0.38f, 1f),
                    source = state.lipSyncSource,
                    playbackPositionMs = state.playbackPositionMs
                )

                delay(frameIntervalMs)
            }
        }
    }

    private fun stopFrameTickerAndCloseMouth() {
        frameTickerJob?.cancel()
        frameTickerJob = null
        smoothedOpenness = 0f
        smoothedWidth = 0.62f
        if (_lipSyncFrame.value.isActive || _lipSyncFrame.value.mouthOpenness > 0f) {
            _lipSyncFrame.value = LipSyncFrame()
        }
    }

    private fun widthHintForToken(rawToken: String): Float {
        if (rawToken.isBlank()) return 0.64f
        val token = rawToken.lowercase(Locale.US)
        val aCount = token.count { it == 'a' || it == 'e' }
        val oCount = token.count { it == 'o' || it == 'u' }
        val iCount = token.count { it == 'i' || it == 'y' }
        return when {
            oCount > aCount && oCount >= iCount -> 0.46f
            iCount > aCount -> 0.90f
            aCount > 0 -> 0.82f
            else -> 0.66f
        }
    }

    fun stop() {
        stopFrameTickerAndCloseMouth()
    }

    fun release() {
        stateCollectorJob?.cancel()
        stateCollectorJob = null
        stopFrameTickerAndCloseMouth()
    }
}
