jipackage com.example.avatar

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SpeechPlaybackStatus {
    IDLE,
    PREPARING,
    SPEAKING,
    PAUSED,
    INTERRUPTED,
    ERROR
}

enum class LipSyncSource {
    NONE,
    /** Real PCM RMS amplitude envelope synchronized with AudioTrack playbackHeadPosition. */
    PCM_AMPLITUDE_ENVELOPE,
    /** Approximate fallback using TTS speech timing / onRangeStart word boundaries (not phoneme-level). */
    APPROXIMATE_SYLLABLE_TIMING
}

data class SpeechPlaybackState(
    val status: SpeechPlaybackStatus = SpeechPlaybackStatus.IDLE,
    val utteranceId: String? = null,
    val currentText: String = "",
    val playbackPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentAmplitude: Float = 0f,
    val lipSyncSource: LipSyncSource = LipSyncSource.NONE,
    val syllableTick: Int = 0,
    val wordToken: String = "",
    val errorMessage: String? = null
) {
    val isSpeaking: Boolean
        get() = status == SpeechPlaybackStatus.SPEAKING

    val isPaused: Boolean
        get() = status == SpeechPlaybackStatus.PAUSED
}

/**
 * Coordinates speech playback lifecycle callbacks (preparing, speaking, paused, completed,
 * interrupted, error) and exposes synchronized playback position + amplitude telemetry
 * to [LipSyncController] and [AvatarAnimationController].
 */
class SpeechStateManager {

    private val _playbackState = MutableStateFlow(SpeechPlaybackState())
    val playbackState: StateFlow<SpeechPlaybackState> = _playbackState.asStateFlow()

    private val _playbackCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val playbackCompleted: SharedFlow<Unit> = _playbackCompleted.asSharedFlow()

    private var tickCounter = 0

    fun onPreparing(utteranceId: String, text: String) {
        _playbackState.update {
            SpeechPlaybackState(
                status = SpeechPlaybackStatus.PREPARING,
                utteranceId = utteranceId,
                currentText = text,
                playbackPositionMs = 0L,
                durationMs = 0L,
                currentAmplitude = 0f,
                lipSyncSource = LipSyncSource.NONE
            )
        }
    }

    fun onStart(
        utteranceId: String,
        text: String,
        source: LipSyncSource,
        durationMs: Long = 0L
    ) {
        _playbackState.update {
            SpeechPlaybackState(
                status = SpeechPlaybackStatus.SPEAKING,
                utteranceId = utteranceId,
                currentText = text,
                playbackPositionMs = 0L,
                durationMs = durationMs.coerceAtLeast(0L),
                currentAmplitude = 0f,
                lipSyncSource = source,
                syllableTick = tickCounter
            )
        }
    }

    fun onPcmProgress(
        utteranceId: String,
        playbackPositionMs: Long,
        amplitude: Float
    ) {
        _playbackState.update { current ->
            if (current.utteranceId != utteranceId || current.status != SpeechPlaybackStatus.SPEAKING) {
                current
            } else {
                tickCounter += 1
                current.copy(
                    playbackPositionMs = playbackPositionMs.coerceAtLeast(0L),
                    currentAmplitude = amplitude.coerceIn(0f, 1f),
                    lipSyncSource = LipSyncSource.PCM_AMPLITUDE_ENVELOPE,
                    syllableTick = tickCounter
                )
            }
        }
    }

    fun onApproximateToken(
        utteranceId: String,
        token: String,
        amplitude: Float,
        estimatedPositionMs: Long
    ) {
        _playbackState.update { current ->
            if (current.utteranceId != utteranceId || current.status != SpeechPlaybackStatus.SPEAKING) {
                current
            } else {
                tickCounter += 1
                current.copy(
                    playbackPositionMs = estimatedPositionMs.coerceAtLeast(0L),
                    currentAmplitude = amplitude.coerceIn(0f, 1f),
                    lipSyncSource = LipSyncSource.APPROXIMATE_SYLLABLE_TIMING,
                    syllableTick = tickCounter,
                    wordToken = token
                )
            }
        }
    }

    fun onPause(utteranceId: String? = null) {
        _playbackState.update { current ->
            if (utteranceId != null && current.utteranceId != utteranceId) {
                current
            } else if (current.status == SpeechPlaybackStatus.SPEAKING ||
                current.status == SpeechPlaybackStatus.PREPARING
            ) {
                current.copy(
                    status = SpeechPlaybackStatus.PAUSED,
                    currentAmplitude = 0f
                )
            } else {
                current
            }
        }
    }

    fun onResume(utteranceId: String? = null) {
        _playbackState.update { current ->
            if (utteranceId != null && current.utteranceId != utteranceId) {
                current
            } else if (current.status == SpeechPlaybackStatus.PAUSED) {
                current.copy(status = SpeechPlaybackStatus.SPEAKING)
            } else {
                current
            }
        }
    }

    fun onDone(utteranceId: String? = null, hasMoreQueued: Boolean = false) {
        val current = _playbackState.value
        if (utteranceId != null && current.utteranceId != utteranceId) return
        if (!hasMoreQueued) {
            _playbackState.value = SpeechPlaybackState(
                status = SpeechPlaybackStatus.IDLE,
                syllableTick = tickCounter
            )
            _playbackCompleted.tryEmit(Unit)
        } else {
            _playbackState.update {
                it.copy(currentAmplitude = 0f)
            }
        }
    }

    fun onInterrupted() {
        _playbackState.value = SpeechPlaybackState(
            status = SpeechPlaybackStatus.INTERRUPTED,
            currentAmplitude = 0f,
            syllableTick = tickCounter
        )
    }

    fun onResetToIdle() {
        _playbackState.value = SpeechPlaybackState(
            status = SpeechPlaybackStatus.IDLE,
            currentAmplitude = 0f,
            syllableTick = tickCounter
        )
    }

    fun onError(utteranceId: String? = null, message: String? = null) {
        val current = _playbackState.value
        if (utteranceId != null && current.utteranceId != utteranceId && current.utteranceId != null) {
            return
        }
        _playbackState.value = SpeechPlaybackState(
            status = SpeechPlaybackStatus.ERROR,
            currentAmplitude = 0f,
            syllableTick = tickCounter,
            errorMessage = message
        )
    }
}
