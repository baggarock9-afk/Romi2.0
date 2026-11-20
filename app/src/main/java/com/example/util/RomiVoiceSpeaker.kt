package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.avatar.LipSyncSource
import com.example.avatar.PcmAudioAnalyzer
import com.example.avatar.SpeechPlaybackStatus
import com.example.avatar.SpeechStateManager
import com.example.model.RomiMood
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sin

private const val TAG = "RomiVoiceSpeaker"

private data class QueuedSpeechItem(
    val utteranceId: String,
    val text: String,
    val mood: RomiMood
)

/**
 * Dual-path TTS speaker integrated with [SpeechStateManager]:
 * 1. Primary path: synthesizes TTS to a WAV PCM file via [TextToSpeech.synthesizeToFile],
 *    extracts real PCM RMS amplitude envelopes, and plays through [AudioTrack] while polling
 *    [AudioTrack.getPlaybackHeadPosition] at 25 FPS with native pause/resume/interrupt support.
 * 2. Fallback path: falls back cleanly to [TextToSpeech.speak] with [UtteranceProgressListener]
 *    and approximate syllable-timing lip sync if PCM synthesis is unavailable or times out.
 */
class RomiVoiceSpeaker(
    context: Context,
    val speechStateManager: SpeechStateManager = SpeechStateManager()
) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    @Volatile
    private var isReady = false

    @Volatile
    private var isPaused = false

    @Volatile
    private var activeTrack: AudioTrack? = null

    @Volatile
    private var activeUtteranceId: String? = null

    @Volatile
    private var activeUtteranceText: String = ""

    @Volatile
    private var activeUtteranceMood: RomiMood = RomiMood.HAPPY

    @Volatile
    private var activeSpeechRate: Float = 1.0f

    @Volatile
    private var fallbackLastCharIndex: Int = 0

    @Volatile
    private var fallbackUsingDirectTts: Boolean = false

    @Volatile
    private var lastRangeStartAtMs: Long = 0L

    private var playbackQueue = Channel<QueuedSpeechItem>(Channel.UNLIMITED)
    private val speakerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queueConsumerJob: Job? = null
    private var fallbackCadenceJob: Job? = null

    @Volatile
    private var activeLiveStreamId: String? = null
    private var liveAudioChannel: Channel<ByteArray>? = null
    private var liveStreamJob: Job? = null

    private val pendingBeforeInit = ArrayDeque<QueuedSpeechItem>()
    private val synthWaiters = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val directSpeakWaiters = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val utteranceCounter = AtomicInteger(0)

    val isSpeaking: StateFlow<Boolean> = speechStateManager.playbackState
        .map { it.isSpeaking }
        .stateIn(speakerScope, SharingStarted.Eagerly, false)

    val playbackCompleted: SharedFlow<Unit> = speechStateManager.playbackCompleted

    init {
        startQueueConsumer()
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                configureLanguageAndVoice(engine)
                attachUtteranceListener(engine)
                isReady = true
                synchronized(pendingBeforeInit) {
                    while (pendingBeforeInit.isNotEmpty()) {
                        playbackQueue.trySend(pendingBeforeInit.removeFirst())
                    }
                }
            } else {
                Log.w(TAG, "TextToSpeech initialization failed with status: $status")
                speechStateManager.onError(message = "TextToSpeech init failed ($status)")
            }
        }
    }

    private fun attachUtteranceListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val id = utteranceId ?: return
                if (directSpeakWaiters.containsKey(id)) {
                    lastRangeStartAtMs = 0L
                    speechStateManager.onStart(
                        utteranceId = id,
                        text = activeUtteranceText,
                        source = LipSyncSource.APPROXIMATE_SYLLABLE_TIMING
                    )
                    startFallbackCadenceDriver(id, activeUtteranceText)
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                val id = utteranceId ?: return
                if (!directSpeakWaiters.containsKey(id)) return
                val text = activeUtteranceText
                if (text.isEmpty()) return
                val safeStart = start.coerceIn(0, text.length)
                val safeEnd = end.coerceIn(safeStart, text.length)
                fallbackLastCharIndex = safeEnd
                lastRangeStartAtMs = System.currentTimeMillis()
                val token = text.substring(safeStart, safeEnd)
                val amplitude = approximateAmplitudeForToken(token)
                val estimatedPosMs = ((safeStart.toFloat() / text.length.coerceAtLeast(1)) *
                    (text.length * 65L)).toLong()
                speechStateManager.onApproximateToken(
                    utteranceId = id,
                    token = token,
                    amplitude = amplitude,
                    estimatedPositionMs = estimatedPosMs
                )
            }

            override fun onDone(utteranceId: String?) {
                val id = utteranceId ?: return
                synthWaiters.remove(id)?.complete(true)
                if (directSpeakWaiters.containsKey(id)) {
                    fallbackCadenceJob?.cancel()
                    directSpeakWaiters.remove(id)?.complete(true)
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                val id = utteranceId ?: return
                synthWaiters.remove(id)?.complete(false)
                if (directSpeakWaiters.containsKey(id)) {
                    fallbackCadenceJob?.cancel()
                    directSpeakWaiters.remove(id)?.complete(false)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val id = utteranceId ?: return
                synthWaiters.remove(id)?.complete(false)
                if (directSpeakWaiters.containsKey(id)) {
                    fallbackCadenceJob?.cancel()
                    directSpeakWaiters.remove(id)?.complete(false)
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "TTS utterance error code: $errorCode ($utteranceId)")
                val id = utteranceId ?: return
                synthWaiters.remove(id)?.complete(false)
                if (directSpeakWaiters.containsKey(id)) {
                    fallbackCadenceJob?.cancel()
                    directSpeakWaiters.remove(id)?.complete(false)
                }
            }
        })
    }

    private fun startQueueConsumer() {
        queueConsumerJob?.cancel()
        queueConsumerJob = speakerScope.launch {
            for (item in playbackQueue) {
                if (!isActive) break
                isPaused = false
                activeUtteranceId = item.utteranceId
                activeUtteranceText = item.text
                activeUtteranceMood = item.mood

                val (_, rate) = voiceProsodyForMood(item.mood)
                activeSpeechRate = rate

                speechStateManager.onPreparing(item.utteranceId, item.text)

                val playedWithPcm = tryPlayViaPcmSynthesis(item)
                if (!playedWithPcm && isActive && activeUtteranceId == item.utteranceId) {
                    playViaDirectTtsFallback(item)
                }

                if (activeUtteranceId == item.utteranceId &&
                    speechStateManager.playbackState.value.status != SpeechPlaybackStatus.INTERRUPTED &&
                    speechStateManager.playbackState.value.status != SpeechPlaybackStatus.ERROR
                ) {
                    val hasMore = !playbackQueue.isEmpty
                    speechStateManager.onDone(item.utteranceId, hasMoreQueued = hasMore)
                }
            }
        }
    }

    private suspend fun tryPlayViaPcmSynthesis(item: QueuedSpeechItem): Boolean {
        val engine = tts ?: return false
        val cacheFile = File(appContext.cacheDir, "romi_tts_${item.utteranceId}.wav")
        return try {
            val synthDeferred = CompletableDeferred<Boolean>()
            synthWaiters[item.utteranceId] = synthDeferred

            val (pitch, rate) = voiceProsodyForMood(item.mood)
            engine.setPitch(pitch)
            engine.setSpeechRate(rate)

            val params = Bundle()
            val synthStatus = engine.synthesizeToFile(item.text, params, cacheFile, item.utteranceId)
            if (synthStatus != TextToSpeech.SUCCESS) {
                synthWaiters.remove(item.utteranceId)
                return false
            }

            val completed = withTimeoutOrNull(2000L) { synthDeferred.await() } ?: false
            synthWaiters.remove(item.utteranceId)
            if (!completed || !cacheFile.exists() || cacheFile.length() <= 44L) {
                if (!completed) {
                    runCatching { engine.stop() }
                }
                return false
            }

            val wavBytes = cacheFile.readBytes()
            val decoded = PcmAudioAnalyzer.parseWav(wavBytes) ?: return false
            if (decoded.samples.isEmpty()) return false

            val envelope = PcmAudioAnalyzer.buildEnvelope(
                samples = decoded.samples,
                sampleRate = decoded.sampleRate,
                channelCount = decoded.channelCount,
                windowMs = 40
            )
            playPcmWithSyncedLipSync(item, decoded.sampleRate, decoded.channelCount, decoded.samples, envelope.durationMs, envelope)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "PCM synthesis unavailable, using direct TTS fallback", e)
            false
        } finally {
            runCatching { cacheFile.delete() }
        }
    }

    private suspend fun playPcmWithSyncedLipSync(
        item: QueuedSpeechItem,
        sampleRate: Int,
        channelCount: Int,
        samples: ShortArray,
        durationMs: Long,
        envelope: com.example.avatar.PcmAmplitudeEnvelope
    ) {
        val channelMask = if (channelCount >= 2) {
            AudioFormat.CHANNEL_OUT_STEREO
        } else {
            AudioFormat.CHANNEL_OUT_MONO
        }
        val byteData = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = samples[i].toInt()
            byteData[i * 2] = (s and 0xFF).toByte()
            byteData[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }

        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(4096)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, byteData.size))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            return
        }

        activeTrack = track
        fallbackUsingDirectTts = false
        val totalFrames = samples.size / channelCount.coerceAtLeast(1)

        try {
            track.write(byteData, 0, byteData.size)
            speechStateManager.onStart(
                utteranceId = item.utteranceId,
                text = item.text,
                source = LipSyncSource.PCM_AMPLITUDE_ENVELOPE,
                durationMs = durationMs
            )
            track.play()

            while (activeUtteranceId == item.utteranceId) {
                if (isPaused) {
                    delay(40L)
                    continue
                }
                val headFrames = runCatching { track.playbackHeadPosition }.getOrDefault(totalFrames)
                if (headFrames >= totalFrames) {
                    break
                }
                val posMs = (headFrames.toLong() * 1000L) / sampleRate
                val amp = envelope.amplitudeAt(posMs)
                speechStateManager.onPcmProgress(
                    utteranceId = item.utteranceId,
                    playbackPositionMs = posMs,
                    amplitude = amp
                )
                delay(40L) // 25 FPS hardware-position sync
            }
        } finally {
            activeTrack = null
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING ||
                    track.playState == AudioTrack.PLAYSTATE_PAUSED
                ) {
                    track.stop()
                }
            }
            runCatching { track.release() }
        }
    }

    private suspend fun playViaDirectTtsFallback(item: QueuedSpeechItem) {
        val engine = tts ?: return
        fallbackUsingDirectTts = true
        fallbackLastCharIndex = 0
        var remainingText = item.text

        while (activeUtteranceId == item.utteranceId && remainingText.isNotBlank()) {
            if (isPaused) {
                delay(40L)
                continue
            }
            val waiter = CompletableDeferred<Boolean>()
            directSpeakWaiters[item.utteranceId] = waiter

            val (pitch, rate) = voiceProsodyForMood(item.mood)
            engine.setPitch(pitch)
            engine.setSpeechRate(rate)
            val result = engine.speak(remainingText, TextToSpeech.QUEUE_FLUSH, null, item.utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                directSpeakWaiters.remove(item.utteranceId)
                speechStateManager.onError(item.utteranceId, "TTS speak failed ($result)")
                return
            }

            waiter.await()
            if (isPaused) {
                val safeCut = fallbackLastCharIndex.coerceIn(0, remainingText.length)
                remainingText = remainingText.substring(safeCut).trimStart()
                fallbackLastCharIndex = 0
                while (isPaused && activeUtteranceId == item.utteranceId) {
                    delay(40L)
                }
            } else {
                break
            }
        }
        fallbackUsingDirectTts = false
    }

    private fun startFallbackCadenceDriver(utteranceId: String, spokenText: String) {
        fallbackCadenceJob?.cancel()
        fallbackCadenceJob = speakerScope.launch {
            val words = spokenText
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .ifEmpty { listOf("hi") }
            var wordIndex = 0
            var phase = 0f
            val startedAt = System.currentTimeMillis()

            while (isActive &&
                !isPaused &&
                activeUtteranceId == utteranceId &&
                speechStateManager.playbackState.value.status == SpeechPlaybackStatus.SPEAKING
            ) {
                val now = System.currentTimeMillis()
                val elapsedSinceRange = now - lastRangeStartAtMs
                val elapsedTotal = now - startedAt

                if (lastRangeStartAtMs == 0L || elapsedSinceRange > 165L) {
                    val token = words[wordIndex % words.size]
                    val baseAmp = approximateAmplitudeForToken(token)
                    val syllablePulse = 0.45f + 0.55f * ((sin(phase) + 1f) * 0.5f)
                    val targetAmp = (baseAmp * syllablePulse).coerceIn(0.12f, 0.94f)
                    speechStateManager.onApproximateToken(
                        utteranceId = utteranceId,
                        token = token,
                        amplitude = targetAmp,
                        estimatedPositionMs = elapsedTotal
                    )
                    phase += 1.45f
                    if (phase > 6.283f) {
                        phase -= 6.283f
                        wordIndex++
                    }
                }
                delay(40L) // 25 FPS smooth fallback update
            }
        }
    }

    private fun approximateAmplitudeForToken(rawToken: String): Float {
        val token = rawToken.lowercase(Locale.US)
        if (token.all { !it.isLetterOrDigit() }) return 0.08f
        val aCount = token.count { it == 'a' || it == 'e' }
        val oCount = token.count { it == 'o' || it == 'u' }
        val iCount = token.count { it == 'i' || it == 'y' }
        val bilabialCount = token.count { it == 'm' || it == 'b' || it == 'p' }
        return when {
            aCount >= oCount && aCount >= iCount && aCount > 0 -> 0.88f
            oCount > aCount && oCount >= iCount -> 0.74f
            iCount > 0 -> 0.54f
            bilabialCount > 0 -> 0.22f
            else -> 0.52f
        }
    }

    fun startLiveAudioStream(utteranceId: String, sampleRate: Int = 24_000) {
        interruptInternal(notifyInterruptedState = false)

        isPaused = false
        fallbackUsingDirectTts = false
        activeUtteranceId = utteranceId
        activeUtteranceText = ""
        activeLiveStreamId = utteranceId

        val chunkChannel = Channel<ByteArray>(Channel.UNLIMITED)
        liveAudioChannel = chunkChannel
        speechStateManager.onPreparing(utteranceId, "")

        liveStreamJob = speakerScope.launch {
            val safeRate = sampleRate.coerceIn(8_000, 48_000)
            val channelMask = AudioFormat.CHANNEL_OUT_MONO
            val minBuf = AudioTrack.getMinBufferSize(
                safeRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val bufferBytes = maxOf(minBuf * 4, (safeRate * 2) / 4)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(safeRate)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                runCatching { track.release() }
                chunkChannel.close()
                if (activeLiveStreamId == utteranceId) {
                    activeLiveStreamId = null
                    activeUtteranceId = null
                    speechStateManager.onError(utteranceId, "Unable to initialize live audio output.")
                }
                return@launch
            }

            activeTrack = track
            val amplitudeLock = Any()
            val windowMs = 40
            val framesPerWindow = ((safeRate * windowMs) / 1000).coerceAtLeast(1)
            val sliceBytes = framesPerWindow * 2 * 2 // ~80ms write slices for responsive pause/interrupt
            val windowAmplitudes = ArrayList<Float>(512)
            var writtenFrames = 0L

            val writerFinished = AtomicBoolean(false)

            speechStateManager.onStart(
                utteranceId = utteranceId,
                text = "",
                source = LipSyncSource.PCM_AMPLITUDE_ENVELOPE,
                durationMs = 0L
            )
            track.play()

            val writerJob = launch {
                try {
                    for (chunk in chunkChannel) {
                        val validBytes = (chunk.size / 2) * 2
                        if (validBytes <= 0) continue

                        val chunkAmps = PcmAudioAnalyzer.computeChunkWindowAmplitudes(
                            pcmBytes = chunk,
                            sampleRate = safeRate,
                            channelCount = 1,
                            windowMs = windowMs
                        )
                        synchronized(amplitudeLock) {
                            for (a in chunkAmps) {
                                windowAmplitudes.add(a)
                            }
                            writtenFrames += (validBytes / 2).toLong()
                        }

                        var offset = 0
                        while (offset < validBytes && isActive && activeLiveStreamId == utteranceId) {
                            while (isPaused && isActive && activeLiveStreamId == utteranceId) {
                                delay(25L)
                            }
                            if (!isActive || activeLiveStreamId != utteranceId) break

                            val toWrite = minOf(sliceBytes, validBytes - offset)
                            val written = track.write(chunk, offset, toWrite, AudioTrack.WRITE_BLOCKING)
                            if (written <= 0) {
                                Log.w(TAG, "AudioTrack live write returned $written")
                                break
                            }
                            offset += written
                        }
                    }
                } finally {
                    writerFinished.set(true)
                }
            }

            try {
                var lastHeadFrames = -1L
                var staleTicks = 0

                while (isActive && activeLiveStreamId == utteranceId) {
                    if (isPaused) {
                        delay(40L)
                        continue
                    }

                    val headFrames = runCatching {
                        track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                    }.getOrDefault(0L)

                    val totalFrames: Long
                    val amp: Float
                    synchronized(amplitudeLock) {
                        totalFrames = writtenFrames
                        val idx = (headFrames / framesPerWindow).toInt()
                        amp = if (headFrames < totalFrames && idx in windowAmplitudes.indices) {
                            windowAmplitudes[idx]
                        } else {
                            0f
                        }
                    }

                    if (totalFrames > 0L && headFrames < totalFrames) {
                        val posMs = (headFrames * 1000L) / safeRate
                        speechStateManager.onPcmProgress(
                            utteranceId = utteranceId,
                            playbackPositionMs = posMs,
                            amplitude = amp
                        )
                    }

                    if (writerFinished.get()) {
                        if (headFrames >= totalFrames) {
                            break
                        }
                        if (headFrames == lastHeadFrames) {
                            staleTicks++
                            if (staleTicks >= 12) {
                                break
                            }
                        } else {
                            staleTicks = 0
                            lastHeadFrames = headFrames
                        }
                    }

                    delay(40L)
                }
            } finally {
                writerJob.cancel()
                activeTrack = null
                chunkChannel.close()
                runCatching {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING ||
                        track.playState == AudioTrack.PLAYSTATE_PAUSED
                    ) {
                        track.stop()
                    }
                }
                runCatching { track.release() }
                if (activeLiveStreamId == utteranceId) {
                    activeLiveStreamId = null
                    if (activeUtteranceId == utteranceId) {
                        activeUtteranceId = null
                        val status = speechStateManager.playbackState.value.status
                        if (status != SpeechPlaybackStatus.INTERRUPTED &&
                            status != SpeechPlaybackStatus.ERROR
                        ) {
                            speechStateManager.onDone(utteranceId, hasMoreQueued = false)
                        }
                    }
                }
            }
        }
    }

    fun enqueueLiveAudioChunk(utteranceId: String, pcmBytes: ByteArray) {
        if (activeLiveStreamId != utteranceId || pcmBytes.isEmpty()) return
        liveAudioChannel?.trySend(pcmBytes)
    }

    fun finishLiveAudioStream(utteranceId: String) {
        if (activeLiveStreamId != utteranceId) return
        liveAudioChannel?.close()
    }

    fun interruptLiveAudioStream() {
        interruptInternal(notifyInterruptedState = true)
    }

    fun speak(text: String, mood: RomiMood = RomiMood.HAPPY, queueAdd: Boolean = false) {
        val cleanText = sanitizeForSpeech(text)
        if (cleanText.isBlank()) return

        if (!queueAdd || activeLiveStreamId != null) {
            interruptInternal(notifyInterruptedState = false)
        }

        val item = QueuedSpeechItem(
            utteranceId = "romi_${utteranceCounter.incrementAndGet()}_${System.currentTimeMillis()}",
            text = cleanText,
            mood = mood
        )

        if (!isReady) {
            synchronized(pendingBeforeInit) {
                if (!queueAdd) pendingBeforeInit.clear()
                pendingBeforeInit.addLast(item)
            }
            return
        }
        playbackQueue.trySend(item)
    }

    fun pause() {
        val id = activeUtteranceId ?: return
        if (speechStateManager.playbackState.value.status != SpeechPlaybackStatus.SPEAKING) return
        isPaused = true
        speechStateManager.onPause(id)

        val track = activeTrack
        if (track != null) {
            runCatching { track.pause() }
        } else if (fallbackUsingDirectTts) {
            fallbackCadenceJob?.cancel()
            tts?.stop()
            directSpeakWaiters.remove(id)?.complete(false)
        }
    }

    fun resume() {
        val id = activeUtteranceId ?: return
        if (!isPaused) return
        isPaused = false
        speechStateManager.onResume(id)
        val track = activeTrack
        if (track != null) {
            runCatching { track.play() }
        }
    }

    fun stop() {
        interruptInternal(notifyInterruptedState = true)
    }

    private fun interruptInternal(notifyInterruptedState: Boolean) {
        isPaused = false
        activeUtteranceId = null
        activeLiveStreamId = null
        liveAudioChannel?.close()
        liveAudioChannel = null
        liveStreamJob?.cancel()
        liveStreamJob = null
        synchronized(pendingBeforeInit) {
            pendingBeforeInit.clear()
        }
        while (playbackQueue.tryReceive().isSuccess) {
            // Drain remaining queued speech items
        }
        fallbackCadenceJob?.cancel()
        synthWaiters.values.forEach { it.complete(false) }
        synthWaiters.clear()
        directSpeakWaiters.values.forEach { it.complete(false) }
        directSpeakWaiters.clear()

        runCatching {
            activeTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING ||
                    track.playState == AudioTrack.PLAYSTATE_PAUSED
                ) {
                    track.pause()
                    track.flush()
                }
            }
        }
        tts?.stop()
        if (notifyInterruptedState) {
            speechStateManager.onInterrupted()
        } else {
            speechStateManager.onResetToIdle()
        }
    }

    fun shutdown() {
        interruptInternal(notifyInterruptedState = false)
        queueConsumerJob?.cancel()
        playbackQueue.close()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        speakerScope.cancel()
    }

    private fun configureLanguageAndVoice(engine: TextToSpeech) {
        val indianEnglish = Locale.Builder().setLanguage("en").setRegion("IN").build()
        val result = engine.setLanguage(indianEnglish)
        val activeLocale = if (
            result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            engine.setLanguage(Locale.US)
            Locale.US
        } else {
            indianEnglish
        }

        runCatching {
            val preferredVoice = engine.voices
                ?.filter { voice ->
                    voice.locale.language == activeLocale.language &&
                        !voice.isNetworkConnectionRequired
                }
                ?.sortedWith(
                    compareByDescending<Voice> { it.locale.country == activeLocale.country }
                        .thenByDescending {
                            it.name.contains("female", ignoreCase = true) ||
                                it.name.endsWith("-x-iol", ignoreCase = true) ||
                                it.name.endsWith("-x-iob", ignoreCase = true)
                        }
                        .thenByDescending { it.quality }
                )
                ?.firstOrNull()
            if (preferredVoice != null) {
                engine.voice = preferredVoice
            }
        }
    }

    private fun voiceProsodyForMood(mood: RomiMood): Pair<Float, Float> = when (mood) {
        RomiMood.HAPPY -> 1.18f to 1.04f
        RomiMood.WINK -> 1.14f to 1.02f
        RomiMood.ATTITUDE -> 1.06f to 1.07f
        RomiMood.SHY -> 1.20f to 0.94f
        RomiMood.SLEEPY -> 1.02f to 0.86f
        RomiMood.SURPRISED -> 1.24f to 1.08f
    }

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace(Regex("[\\p{So}\\p{Cn}♡✨🥰😉🙄😳😴😮💗⚡🌙🌸😂☕💖]"), " ")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
