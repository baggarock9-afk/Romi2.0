package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.SpeakingStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

private const val TAG = "RomiVoiceInput"

sealed interface VoiceInputEvent {
    data class PartialTranscript(val text: String) : VoiceInputEvent
    data class FinalTranscript(val text: String) : VoiceInputEvent
    data object BargeInDetected : VoiceInputEvent
    data class Error(
        val code: Int,
        val isRecoverableSilence: Boolean,
        val message: String
    ) : VoiceInputEvent
}

class RomiVoiceInputManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var speechRecognizer: SpeechRecognizer? = null
    private var pcmCaptureJob: Job? = null
    private var isListeningActive = false

    @Volatile
    private var bargeInArmed = false

    @Volatile
    private var bargeInArmedAtMs = 0L

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    private val _events = MutableSharedFlow<VoiceInputEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<VoiceInputEvent> = _events.asSharedFlow()

    private val _pcmFrames = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val pcmFrames: SharedFlow<ByteArray> = _pcmFrames.asSharedFlow()

    fun isRecognitionAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(appContext)

    fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    fun startListening(speakingStyle: SpeakingStyle) {
        if (!hasRecordAudioPermission()) {
            _events.tryEmit(
                VoiceInputEvent.Error(
                    code = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                    isRecoverableSilence = false,
                    message = "Microphone permission is needed to listen."
                )
            )
            return
        }
        if (!isRecognitionAvailable()) {
            _events.tryEmit(
                VoiceInputEvent.Error(
                    code = -1,
                    isRecoverableSilence = false,
                    message = "Speech recognition service is unavailable on this device."
                )
            )
            return
        }

        stopBargeInMonitor()

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = createRecognizer()
                } else if (isListeningActive) {
                    speechRecognizer?.cancel()
                }
                val localeTag = when (speakingStyle) {
                    SpeakingStyle.HINGLISH -> "en-IN"
                    SpeakingStyle.ENGLISH, SpeakingStyle.ANIME_KAWAII -> "en-US"
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
                }
                isListeningActive = true
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                isListeningActive = false
                _events.tryEmit(
                    VoiceInputEvent.Error(
                        code = SpeechRecognizer.ERROR_CLIENT,
                        isRecoverableSilence = false,
                        message = e.localizedMessage ?: "Unable to start microphone listening."
                    )
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            isListeningActive = false
            runCatching { speechRecognizer?.cancel() }
            _micAmplitude.value = 0f
        }
    }

    fun setBargeInArmed(armed: Boolean) {
        if (armed && !bargeInArmed) {
            bargeInArmedAtMs = System.currentTimeMillis()
        }
        bargeInArmed = armed
    }

    fun startFullDuplexCapture(): Boolean {
        if (!hasRecordAudioPermission()) {
            _events.tryEmit(
                VoiceInputEvent.Error(
                    code = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                    isRecoverableSilence = false,
                    message = "Microphone permission is needed for live voice chat."
                )
            )
            return false
        }
        bargeInArmed = false
        startPcmCapture(streamFrames = true)
        return true
    }

    fun startBargeInMonitor() {
        if (!hasRecordAudioPermission()) return
        bargeInArmedAtMs = System.currentTimeMillis()
        bargeInArmed = true
        startPcmCapture(streamFrames = false)
    }

    @SuppressLint("MissingPermission")
    private fun startPcmCapture(streamFrames: Boolean) {
        stopListening()
        pcmCaptureJob?.cancel()

        pcmCaptureJob = ioScope.launch {
            val sampleRate = 16_000
            val frameSamples = 640 // 40ms @ 16kHz = 1280 bytes
            val minBufBytes = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(frameSamples * 2 * 2)

            var audioRecord: AudioRecord? = null
            var aec: AcousticEchoCanceler? = null
            var ns: NoiseSuppressor? = null

            try {
                audioRecord = createBestAudioRecord(sampleRate, minBufBytes)
                if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "AudioRecord unavailable for PCM capture")
                    if (streamFrames) {
                        _events.tryEmit(
                            VoiceInputEvent.Error(
                                code = SpeechRecognizer.ERROR_AUDIO,
                                isRecoverableSilence = false,
                                message = "Unable to access device microphone."
                            )
                        )
                    }
                    return@launch
                }

                runCatching {
                    if (AcousticEchoCanceler.isAvailable()) {
                        aec = AcousticEchoCanceler.create(audioRecord.audioSessionId)?.apply {
                            enabled = true
                        }
                    }
                    if (NoiseSuppressor.isAvailable()) {
                        ns = NoiseSuppressor.create(audioRecord.audioSessionId)?.apply {
                            enabled = true
                        }
                    }
                }

                val buffer = ShortArray(frameSamples)
                audioRecord.startRecording()

                var noiseFloor = 0.08f
                var elevatedFrames = 0
                var lastArmedAt = bargeInArmedAtMs

                while (isActive) {
                    val readCount = audioRecord.read(buffer, 0, buffer.size)
                    if (readCount <= 0) continue

                    var sumSq = 0.0
                    for (i in 0 until readCount) {
                        val s = buffer[i].toDouble() / 32768.0
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / readCount).toFloat()
                    val normalized = (rms * 4.8f).coerceIn(0f, 1f)
                    _micAmplitude.value = normalized

                    if (streamFrames) {
                        val byteFrame = ByteArray(readCount * 2)
                        for (i in 0 until readCount) {
                            val sample = buffer[i].toInt()
                            byteFrame[i * 2] = (sample and 0xFF).toByte()
                            byteFrame[i * 2 + 1] = ((sample ushr 8) and 0xFF).toByte()
                        }
                        _pcmFrames.tryEmit(byteFrame)
                    }

                    if (!bargeInArmed) {
                        elevatedFrames = 0
                        noiseFloor = (noiseFloor * 0.94f + normalized * 0.06f).coerceIn(0.05f, 0.30f)
                        continue
                    }

                    if (lastArmedAt != bargeInArmedAtMs) {
                        lastArmedAt = bargeInArmedAtMs
                        elevatedFrames = 0
                    }

                    val elapsedSinceArmed = System.currentTimeMillis() - bargeInArmedAtMs
                    if (elapsedSinceArmed < 520L) {
                        noiseFloor = (noiseFloor * 0.75f + normalized * 0.25f).coerceIn(0.05f, 0.35f)
                        continue
                    }

                    val bargeInThreshold = max(0.38f, noiseFloor + 0.24f)
                    if (normalized > bargeInThreshold) {
                        elevatedFrames += 1
                        if (elevatedFrames >= 4) {
                            Log.d(TAG, "Dynamic voice barge-in detected (rms=$normalized, threshold=$bargeInThreshold)")
                            bargeInArmed = false
                            elevatedFrames = 0
                            _events.tryEmit(VoiceInputEvent.BargeInDetected)
                            if (!streamFrames) {
                                _micAmplitude.value = 0f
                                break
                            }
                        }
                    } else {
                        elevatedFrames = (elevatedFrames - 1).coerceAtLeast(0)
                        noiseFloor = (noiseFloor * 0.94f + normalized * 0.06f).coerceIn(0.05f, 0.35f)
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "RECORD_AUDIO permission missing for AudioRecord PCM capture", e)
            } catch (e: Exception) {
                Log.w(TAG, "PCM capture stream stopped", e)
            } finally {
                _micAmplitude.value = 0f
                runCatching { aec?.release() }
                runCatching { ns?.release() }
                runCatching {
                    if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop()
                    }
                }
                runCatching { audioRecord?.release() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createBestAudioRecord(sampleRate: Int, bufferBytes: Int): AudioRecord? {
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )
        for (source in sources) {
            val candidate = runCatching {
                AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes
                )
            }.getOrNull()
            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) {
                return candidate
            }
            runCatching { candidate?.release() }
        }
        return null
    }

    fun stopPcmCapture() {
        bargeInArmed = false
        pcmCaptureJob?.cancel()
        pcmCaptureJob = null
        _micAmplitude.value = 0f
    }

    fun stopBargeInMonitor() {
        stopPcmCapture()
    }

    fun stopAll() {
        stopPcmCapture()
        stopListening()
    }

    fun destroy() {
        stopPcmCapture()
        mainHandler.post {
            isListeningActive = false
            runCatching { speechRecognizer?.cancel() }
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
        }
        ioScope.cancel()
    }

    private fun createRecognizer(): SpeechRecognizer {
        return SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _micAmplitude.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    isListeningActive = false
                    _micAmplitude.value = 0f
                }

                override fun onError(error: Int) {
                    isListeningActive = false
                    _micAmplitude.value = 0f
                    val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — listening again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard."
                        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy."
                        else -> "Speech recognition error ($error)."
                    }
                    if (!recoverable) {
                        Log.w(TAG, "SpeechRecognizer error=$error: $msg")
                    }
                    _events.tryEmit(VoiceInputEvent.Error(error, recoverable, msg))
                }

                override fun onResults(results: Bundle?) {
                    isListeningActive = false
                    _micAmplitude.value = 0f
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (text.isNotEmpty()) {
                        _events.tryEmit(VoiceInputEvent.FinalTranscript(text))
                    } else {
                        _events.tryEmit(
                            VoiceInputEvent.Error(
                                code = SpeechRecognizer.ERROR_NO_MATCH,
                                isRecoverableSilence = true,
                                message = "Didn't catch that."
                            )
                        )
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        _events.tryEmit(VoiceInputEvent.PartialTranscript(partial))
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }
}
