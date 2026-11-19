package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.avatar.AvatarAnimationController
import com.example.avatar.AvatarExpressionController
import com.example.avatar.LipSyncController
import com.example.avatar.SpeechPlaybackStatus
import com.example.avatar.SpeechStateManager
import com.example.avatar.StructuredExpression
import com.example.data.RomiAiRepository
import com.example.data.RomiEmotionInference
import com.example.data.RomiPreferencesRepository
import com.example.data.RomiStreamEvent
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.model.RomiMood
import com.example.model.RomiResponseState
import com.example.model.RomiUiState
import com.example.model.SpeakingStyle
import com.example.model.VoiceChatPhase
import com.example.model.VoiceConversationState
import com.example.util.RomiVoiceInputManager
import com.example.util.RomiVoiceSpeaker
import com.example.util.VoiceInputEvent
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.example.avatar.PcmAudioAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "RomiViewModel"

class RomiViewModel(application: Application) : AndroidViewModel(application) {

    private val aiRepository = RomiAiRepository()
    private val prefsRepository = RomiPreferencesRepository(application)

    private val speechStateManager = SpeechStateManager()
    private val expressionController = AvatarExpressionController(viewModelScope)
    private val lipSyncController = LipSyncController(speechStateManager, viewModelScope)
    private val avatarAnimationController = AvatarAnimationController(
        expressionController = expressionController,
        lipSyncController = lipSyncController,
        scope = viewModelScope
    )

    private val voiceSpeaker = RomiVoiceSpeaker(application, speechStateManager)
    private val voiceInputManager = RomiVoiceInputManager(application)

    private val _uiState = MutableStateFlow(
        RomiUiState(
            voiceChat = VoiceConversationState(
                hasMicPermission = voiceInputManager.hasRecordAudioPermission(),
                sttAvailable = voiceInputManager.isRecognitionAvailable()
            )
        )
    )
    val uiState: StateFlow<RomiUiState> = _uiState.asStateFlow()

    private var hasUserInteracted = false
    private var activeAiJob: Job? = null
    private var restartListenJob: Job? = null

    @OptIn(PublicPreviewAPI::class)
    private var activeLiveSession: LiveSession? = null
    private var liveSessionJob: Job? = null
    private var liveAudioSendJob: Job? = null
    private val liveTurnCounter = AtomicInteger(0)

    @Volatile
    private var locallyInterruptedTurn = false

    @Volatile
    private var serverTurnInFlight = false

    @OptIn(PublicPreviewAPI::class)
    private fun isLiveSessionActive(): Boolean =
        activeLiveSession != null && liveSessionJob?.isActive == true

    init {
        avatarAnimationController.start()

        viewModelScope.launch {
            prefsRepository.preferencesFlow.collect { prefs ->
                _uiState.update { state ->
                    val updatedBubble = if (!hasUserInteracted && state.messages.isEmpty()) {
                        "Hii ${prefs.nickname}! ♡\nFinally, you're here! What shall we do today? 🥰"
                    } else {
                        state.bubbleText
                    }
                    state.copy(
                        nickname = prefs.nickname,
                        voiceEnabled = prefs.voiceEnabled,
                        speakingStyle = prefs.speakingStyle,
                        bubbleText = updatedBubble
                    )
                }
            }
        }

        viewModelScope.launch {
            speechStateManager.playbackState.collect { speechState ->
                _uiState.update { state ->
                    state.copy(
                        isSpeaking = speechState.isSpeaking,
                        speechPlaybackStatus = speechState.status
                    )
                }
            }
        }

        viewModelScope.launch {
            avatarAnimationController.avatarState.collect { renderState ->
                _uiState.update { state ->
                    state.copy(avatarRenderState = renderState)
                }
            }
        }

        viewModelScope.launch {
            voiceSpeaker.playbackCompleted.collect {
                val state = _uiState.value
                if (isLiveSessionActive()) {
                    voiceInputManager.setBargeInArmed(false)
                    avatarAnimationController.setListening(true)
                    _uiState.update { s ->
                        s.copy(
                            statusCaption = "Listening to you… 🎙️",
                            voiceChat = s.voiceChat.copy(
                                phase = VoiceChatPhase.LISTENING,
                                statusHint = "Full-duplex live"
                            )
                        )
                    }
                } else if (state.voiceChat.isContinuousMode && activeAiJob?.isActive != true) {
                    voiceInputManager.stopBargeInMonitor()
                    delay(260L)
                    if (_uiState.value.voiceChat.isContinuousMode && activeAiJob?.isActive != true) {
                        beginListeningTurn()
                    }
                }
            }
        }

        viewModelScope.launch {
            voiceInputManager.micAmplitude.collect { amp ->
                _uiState.update { state ->
                    state.copy(voiceChat = state.voiceChat.copy(micAmplitude = amp))
                }
            }
        }

        viewModelScope.launch {
            voiceInputManager.events.collect { event ->
                handleVoiceInputEvent(event)
            }
        }
    }

    private fun handleVoiceInputEvent(event: VoiceInputEvent) {
        when (event) {
            is VoiceInputEvent.PartialTranscript -> {
                _uiState.update { state ->
                    state.copy(
                        voiceChat = state.voiceChat.copy(
                            partialTranscript = event.text,
                            statusHint = ""
                        )
                    )
                }
            }

            is VoiceInputEvent.FinalTranscript -> {
                val spokenText = event.text.trim()
                val wasContinuous = _uiState.value.voiceChat.isContinuousMode
                avatarAnimationController.setListening(false)
                _uiState.update { state ->
                    state.copy(
                        voiceChat = state.voiceChat.copy(
                            isSingleDictation = false,
                            partialTranscript = "",
                            phase = if (wasContinuous) VoiceChatPhase.PROCESSING else VoiceChatPhase.OFF,
                            statusHint = ""
                        )
                    )
                }
                if (spokenText.isNotEmpty()) {
                    sendMessage(spokenText, isVoiceTurn = true)
                } else if (wasContinuous) {
                    scheduleListenRestart(240L)
                }
            }

            is VoiceInputEvent.BargeInDetected -> {
                val state = _uiState.value
                if (state.voiceChat.isContinuousMode &&
                    (state.isSpeaking || state.voiceChat.phase == VoiceChatPhase.SPEAKING)
                ) {
                    interruptAndListen()
                }
            }

            is VoiceInputEvent.Error -> {
                val state = _uiState.value
                avatarAnimationController.setListening(false)
                if (event.isRecoverableSilence && state.voiceChat.isContinuousMode && !isLiveSessionActive()) {
                    _uiState.update { s ->
                        s.copy(
                            voiceChat = s.voiceChat.copy(
                                partialTranscript = "",
                                statusHint = "Listening…"
                            )
                        )
                    }
                    scheduleListenRestart(320L)
                } else {
                    stopLiveSessionResources()
                    _uiState.update { s ->
                        s.copy(
                            voiceChat = s.voiceChat.copy(
                                isContinuousMode = false,
                                isSingleDictation = false,
                                phase = VoiceChatPhase.OFF,
                                partialTranscript = "",
                                statusHint = event.message
                            )
                        )
                    }
                }
            }
        }
    }

    fun syncMicPermission() {
        val granted = voiceInputManager.hasRecordAudioPermission()
        val available = voiceInputManager.isRecognitionAvailable()
        _uiState.update { state ->
            state.copy(
                voiceChat = state.voiceChat.copy(
                    hasMicPermission = granted,
                    sttAvailable = available
                )
            )
        }
    }

    fun onMicPermissionResult(granted: Boolean, startContinuous: Boolean) {
        _uiState.update { state ->
            state.copy(
                voiceChat = state.voiceChat.copy(
                    hasMicPermission = granted,
                    sttAvailable = voiceInputManager.isRecognitionAvailable(),
                    statusHint = if (granted) "" else "Microphone permission is needed for voice chat."
                )
            )
        }
        if (granted) {
            if (startContinuous) {
                enableContinuousVoiceChat()
            } else {
                startSingleVoiceDictation()
            }
        }
    }

    fun toggleContinuousVoiceChat() {
        if (_uiState.value.voiceChat.isContinuousMode) {
            stopVoiceChat()
        } else {
            enableContinuousVoiceChat()
        }
    }

    @OptIn(PublicPreviewAPI::class)
    private fun enableContinuousVoiceChat() {
        if (!voiceInputManager.hasRecordAudioPermission()) return

        hasUserInteracted = true
        activeAiJob?.cancel()
        restartListenJob?.cancel()
        stopLiveSessionResources()
        avatarAnimationController.setThinking(false)
        avatarAnimationController.setListening(false)
        voiceSpeaker.stop()

        if (!_uiState.value.voiceEnabled) {
            viewModelScope.launch { prefsRepository.setVoiceEnabled(true) }
        }

        val snapshot = _uiState.value
        _uiState.update { state ->
            state.copy(
                responseState = RomiResponseState.Idle,
                statusCaption = "Connecting to Romi Live… ✨",
                voiceChat = state.voiceChat.copy(
                    isContinuousMode = true,
                    isSingleDictation = false,
                    phase = VoiceChatPhase.PROCESSING,
                    partialTranscript = "",
                    statusHint = "Connecting live voice…"
                )
            )
        }

        liveSessionJob = viewModelScope.launch {
            var session: LiveSession? = null
            try {
                session = aiRepository.connectLiveVoiceSession(
                    nickname = snapshot.nickname,
                    currentMood = snapshot.currentMood,
                    speakingStyle = snapshot.speakingStyle,
                    priorHistory = snapshot.messages
                )
                activeLiveSession = session

                if (!voiceInputManager.startFullDuplexCapture()) {
                    return@launch
                }

                avatarAnimationController.setListening(true)
                _uiState.update { state ->
                    state.copy(
                        statusCaption = "Listening to you… 🎙️",
                        voiceChat = state.voiceChat.copy(
                            phase = VoiceChatPhase.LISTENING,
                            partialTranscript = "",
                            statusHint = "Full-duplex live"
                        )
                    )
                }

                val connectedSession = session
                liveAudioSendJob = launch(Dispatchers.IO) {
                    voiceInputManager.pcmFrames.collect { pcmBytes ->
                        if (isActive && !connectedSession.isClosed()) {
                            runCatching {
                                connectedSession.sendAudioRealtime(InlineData(pcmBytes, "audio/pcm"))
                            }.onFailure { err ->
                                Log.w(TAG, "Live audio send failed", err)
                            }
                        }
                    }
                }

                val userTranscriptBuf = StringBuilder()
                val romiTranscriptBuf = StringBuilder()
                var turnUtteranceId: String? = null
                var turnMood = snapshot.currentMood

                fun commitUserTurnIfNeeded() {
                    val userText = userTranscriptBuf.toString().trim()
                    userTranscriptBuf.clear()
                    if (userText.isNotEmpty()) {
                        val userMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sender = MessageSender.USER,
                            text = userText,
                            mood = _uiState.value.currentMood
                        )
                        _uiState.update { s ->
                            s.copy(
                                messages = s.messages + userMsg,
                                voiceChat = s.voiceChat.copy(partialTranscript = "")
                            )
                        }
                    }
                }

                fun commitRomiTurnIfNeeded() {
                    val romiText = romiTranscriptBuf.toString().trim()
                    romiTranscriptBuf.clear()
                    if (romiText.isNotEmpty()) {
                        val romiMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sender = MessageSender.ROMI,
                            text = romiText,
                            mood = turnMood
                        )
                        _uiState.update { s ->
                            s.copy(
                                messages = s.messages + romiMsg,
                                affectionBurstTick = s.affectionBurstTick + 1
                            )
                        }
                    }
                }

                connectedSession.receive().collect { serverMsg ->
                    when (serverMsg) {
                        is LiveServerGoAway -> {
                            Log.i(TAG, "LiveServerGoAway received; resuming live session")
                            _uiState.update { s ->
                                s.copy(
                                    voiceChat = s.voiceChat.copy(
                                        statusHint = "Refreshing voice connection…"
                                    )
                                )
                            }
                            runCatching { connectedSession.resumeSession() }
                        }

                        is LiveServerContent -> {
                            val inputText = serverMsg.inputTranscription?.text.orEmpty()
                            if (inputText.isNotBlank()) {
                                appendLiveTranscription(userTranscriptBuf, inputText)
                                val partial = userTranscriptBuf.toString().trim()
                                _uiState.update { s ->
                                    s.copy(
                                        voiceChat = s.voiceChat.copy(
                                            partialTranscript = partial
                                        )
                                    )
                                }
                            }

                            if (serverMsg.interrupted) {
                                serverTurnInFlight = false
                                locallyInterruptedTurn = false
                                voiceSpeaker.interruptLiveAudioStream()
                                voiceInputManager.setBargeInArmed(false)
                                commitRomiTurnIfNeeded()
                                turnUtteranceId = null
                                avatarAnimationController.setThinking(false)
                                avatarAnimationController.setListening(true)
                                _uiState.update { s ->
                                    s.copy(
                                        statusCaption = "Listening to you… 🎙️",
                                        voiceChat = s.voiceChat.copy(
                                            phase = VoiceChatPhase.LISTENING,
                                            statusHint = "Interrupted — listening now"
                                        )
                                    )
                                }
                            }

                            val hasModelOutput = !serverMsg.content?.parts.isNullOrEmpty() ||
                                !serverMsg.outputTranscription?.text.isNullOrBlank()
                            if (hasModelOutput && !locallyInterruptedTurn) {
                                serverTurnInFlight = true
                            }

                            if (!locallyInterruptedTurn) {
                                val parts = serverMsg.content?.parts.orEmpty()
                                for (part in parts) {
                                    if (part is InlineDataPart &&
                                        part.mimeType.startsWith("audio/pcm", ignoreCase = true) &&
                                        part.inlineData.isNotEmpty()
                                    ) {
                                        if (turnUtteranceId == null) {
                                            commitUserTurnIfNeeded()
                                            val sampleRate = PcmAudioAnalyzer.parsePcmSampleRate(
                                                mimeType = part.mimeType,
                                                defaultRate = 24_000
                                            )
                                            val nextId = "live_${liveTurnCounter.incrementAndGet()}_${System.currentTimeMillis()}"
                                            turnUtteranceId = nextId
                                            voiceSpeaker.startLiveAudioStream(nextId, sampleRate)
                                            voiceInputManager.setBargeInArmed(true)
                                            avatarAnimationController.setListening(false)
                                            avatarAnimationController.setThinking(false)
                                            _uiState.update { s ->
                                                s.copy(
                                                    statusCaption = "Romi is speaking ✨",
                                                    voiceChat = s.voiceChat.copy(
                                                        phase = VoiceChatPhase.SPEAKING,
                                                        statusHint = "Live audio — speak to interrupt"
                                                    )
                                                )
                                            }
                                        }
                                        voiceSpeaker.enqueueLiveAudioChunk(turnUtteranceId!!, part.inlineData)
                                    }
                                }

                                val outputText = serverMsg.outputTranscription?.text.orEmpty()
                                if (outputText.isNotBlank()) {
                                    appendLiveTranscription(romiTranscriptBuf, outputText)
                                    val currentReply = romiTranscriptBuf.toString().trim()
                                    val (inferredMood, inferredExpr) = RomiEmotionInference.inferFromTranscript(
                                        text = currentReply,
                                        fallbackMood = turnMood
                                    )
                                    turnMood = inferredMood
                                    expressionController.onAiExpression(
                                        expression = inferredExpr,
                                        mood = inferredMood,
                                        autoRelax = true
                                    )
                                    _uiState.update { s ->
                                        s.copy(
                                            currentMood = inferredMood,
                                            bubbleText = currentReply,
                                            statusCaption = inferredMood.defaultStatusNote()
                                        )
                                    }
                                }
                            }

                            if (serverMsg.turnComplete) {
                                serverTurnInFlight = false
                                val wasLocallyInterrupted = locallyInterruptedTurn
                                locallyInterruptedTurn = false
                                commitUserTurnIfNeeded()
                                commitRomiTurnIfNeeded()
                                val completedTurnId = turnUtteranceId
                                turnUtteranceId = null
                                if (completedTurnId != null && !wasLocallyInterrupted) {
                                    voiceSpeaker.finishLiveAudioStream(completedTurnId)
                                } else {
                                    voiceInputManager.setBargeInArmed(false)
                                    avatarAnimationController.setListening(true)
                                    _uiState.update { s ->
                                        s.copy(
                                            statusCaption = "Listening to you… 🎙️",
                                            voiceChat = s.voiceChat.copy(
                                                phase = VoiceChatPhase.LISTENING,
                                                statusHint = "Full-duplex live"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Live voice session error", e)
                _uiState.update { state ->
                    state.copy(
                        statusCaption = "Live voice disconnected",
                        voiceChat = state.voiceChat.copy(
                            isContinuousMode = false,
                            phase = VoiceChatPhase.OFF,
                            partialTranscript = "",
                            micAmplitude = 0f,
                            statusHint = e.localizedMessage ?: "Unable to connect live voice."
                        )
                    )
                }
            } finally {
                avatarAnimationController.setListening(false)
                liveAudioSendJob?.cancel()
                liveAudioSendJob = null
                voiceInputManager.stopPcmCapture()
                if (activeLiveSession === session) {
                    activeLiveSession = null
                }
                session?.let { closeLiveSessionAsync(it) }
            }
        }
    }

    private fun appendLiveTranscription(buffer: StringBuilder, incoming: String) {
        if (incoming.isEmpty()) return
        val existing = buffer.toString()
        when {
            existing.isEmpty() -> buffer.append(incoming)
            incoming.startsWith(existing) -> {
                buffer.clear()
                buffer.append(incoming)
            }
            existing.endsWith(incoming) && incoming.length > 2 -> Unit
            else -> buffer.append(incoming)
        }
    }

    @OptIn(PublicPreviewAPI::class)
    private fun closeLiveSessionAsync(session: LiveSession) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { session.stopReceiving() }
            runCatching { session.close() }
        }
    }

    @OptIn(PublicPreviewAPI::class)
    private fun stopLiveSessionResources() {
        serverTurnInFlight = false
        locallyInterruptedTurn = false
        liveAudioSendJob?.cancel()
        liveAudioSendJob = null
        liveSessionJob?.cancel()
        liveSessionJob = null
        val session = activeLiveSession
        activeLiveSession = null
        voiceInputManager.stopPcmCapture()
        if (session != null) {
            closeLiveSessionAsync(session)
        }
    }

    fun startSingleVoiceDictation() {
        if (!voiceInputManager.hasRecordAudioPermission()) return
        if (!voiceInputManager.isRecognitionAvailable()) {
            _uiState.update { state ->
                state.copy(
                    voiceChat = state.voiceChat.copy(
                        sttAvailable = false,
                        statusHint = "Speech recognition service is unavailable on this device."
                    )
                )
            }
            return
        }
        hasUserInteracted = true
        activeAiJob?.cancel()
        restartListenJob?.cancel()
        stopLiveSessionResources()
        avatarAnimationController.setThinking(false)
        avatarAnimationController.setListening(true)
        voiceSpeaker.stop()

        _uiState.update { state ->
            state.copy(
                responseState = RomiResponseState.Idle,
                statusCaption = "Listening to you… 🎙️",
                voiceChat = state.voiceChat.copy(
                    isContinuousMode = false,
                    isSingleDictation = true,
                    phase = VoiceChatPhase.LISTENING,
                    partialTranscript = "",
                    statusHint = ""
                )
            )
        }
        voiceInputManager.startListening(_uiState.value.speakingStyle)
    }

    fun stopSingleDictation() {
        voiceInputManager.stopListening()
        avatarAnimationController.setListening(false)
        _uiState.update { state ->
            state.copy(
                statusCaption = state.currentMood.defaultStatusNote(),
                voiceChat = state.voiceChat.copy(
                    isSingleDictation = false,
                    phase = if (state.voiceChat.isContinuousMode) state.voiceChat.phase else VoiceChatPhase.OFF,
                    partialTranscript = ""
                )
            )
        }
    }

    @OptIn(PublicPreviewAPI::class)
    fun interruptAndListen() {
        val state = _uiState.value
        if (!state.voiceChat.hasMicPermission) return
        restartListenJob?.cancel()
        avatarAnimationController.setThinking(false)

        val liveSession = activeLiveSession
        if (liveSession != null && liveSessionJob?.isActive == true) {
            locallyInterruptedTurn = serverTurnInFlight
            voiceSpeaker.interruptLiveAudioStream()
            voiceInputManager.setBargeInArmed(false)
            avatarAnimationController.setListening(true)
            _uiState.update { s ->
                s.copy(
                    responseState = RomiResponseState.Idle,
                    statusCaption = "Listening to you… 🎙️",
                    voiceChat = s.voiceChat.copy(
                        isContinuousMode = true,
                        isSingleDictation = false,
                        phase = VoiceChatPhase.LISTENING,
                        partialTranscript = "",
                        statusHint = "Interrupted — listening now"
                    )
                )
            }
            return
        }

        activeAiJob?.cancel()
        voiceSpeaker.stop()
        voiceInputManager.stopBargeInMonitor()

        _uiState.update { s ->
            s.copy(
                responseState = RomiResponseState.Idle,
                voiceChat = s.voiceChat.copy(
                    isContinuousMode = true,
                    isSingleDictation = false,
                    partialTranscript = "",
                    statusHint = "Interrupted — listening now"
                )
            )
        }
        beginListeningTurn()
    }

    fun stopVoiceChat() {
        restartListenJob?.cancel()
        activeAiJob?.cancel()
        stopLiveSessionResources()
        avatarAnimationController.setListening(false)
        avatarAnimationController.setThinking(false)
        voiceInputManager.stopAll()
        voiceSpeaker.stop()
        _uiState.update { state ->
            state.copy(
                responseState = RomiResponseState.Idle,
                statusCaption = state.currentMood.defaultStatusNote(),
                voiceChat = state.voiceChat.copy(
                    isContinuousMode = false,
                    isSingleDictation = false,
                    phase = VoiceChatPhase.OFF,
                    partialTranscript = "",
                    micAmplitude = 0f,
                    statusHint = ""
                )
            )
        }
    }

    private fun beginListeningTurn() {
        val style = _uiState.value.speakingStyle
        avatarAnimationController.setListening(true)
        _uiState.update { state ->
            state.copy(
                statusCaption = "Listening to you… 🎙️",
                voiceChat = state.voiceChat.copy(
                    phase = VoiceChatPhase.LISTENING,
                    partialTranscript = "",
                    statusHint = ""
                )
            )
        }
        voiceInputManager.startListening(style)
    }

    private fun scheduleListenRestart(delayMs: Long) {
        restartListenJob?.cancel()
        restartListenJob = viewModelScope.launch {
            delay(delayMs)
            val state = _uiState.value
            if (state.voiceChat.isContinuousMode &&
                !state.isSpeaking &&
                activeAiJob?.isActive != true
            ) {
                beginListeningTurn()
            }
        }
    }

    fun selectMood(mood: RomiMood) {
        hasUserInteracted = true
        val nickname = _uiState.value.nickname
        val line = mood.defaultBubbleLine(nickname)
        expressionController.onUserMoodSelected(mood, autoRelax = false)
        avatarAnimationController.triggerReactiveBlink()
        _uiState.update { state ->
            state.copy(
                currentMood = mood,
                bubbleText = line,
                statusCaption = mood.defaultStatusNote(),
                responseState = RomiResponseState.Idle,
                affectionBurstTick = state.affectionBurstTick + 1
            )
        }
        if (_uiState.value.voiceEnabled) {
            voiceSpeaker.speak(line, mood, queueAdd = false)
        }
    }

    fun onPortraitTapped() {
        hasUserInteracted = true
        val state = _uiState.value
        val name = state.nickname
        val tapReactions = listOf(
            RomiMood.SHY to "Arey $name! Aise poke mat karo, blush ho jata hai~ 😳",
            RomiMood.HAPPY to "Hehe! $name, you always make my room feel brighter ♡ 🥰",
            RomiMood.WINK to "Oho, checking if I'm paying attention? Always on you 😉",
            RomiMood.SURPRISED to "Eep! Caught me daydreaming about snacks! 😮"
        )
        val next = tapReactions[state.affectionBurstTick % tapReactions.size]
        expressionController.onUserMoodSelected(next.first, autoRelax = true)
        avatarAnimationController.triggerReactiveBlink()
        _uiState.update {
            it.copy(
                currentMood = next.first,
                bubbleText = next.second,
                statusCaption = "Romi reacted to your tap ♡",
                affectionBurstTick = it.affectionBurstTick + 1
            )
        }
        if (state.voiceEnabled) {
            voiceSpeaker.speak(next.second, next.first, queueAdd = false)
        }
    }

    fun sendMessage(rawText: String, isVoiceTurn: Boolean = false) {
        val text = rawText.trim()
        if (text.isEmpty() || _uiState.value.responseState is RomiResponseState.Thinking) {
            return
        }
        hasUserInteracted = true
        restartListenJob?.cancel()
        if (!isVoiceTurn && isLiveSessionActive()) {
            stopVoiceChat()
        }
        avatarAnimationController.setListening(false)
        avatarAnimationController.setThinking(true)
        voiceInputManager.stopBargeInMonitor()
        if (!isVoiceTurn) {
            voiceInputManager.stopListening()
        }
        voiceSpeaker.stop()

        val currentState = _uiState.value
        val priorHistory = currentState.messages
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.USER,
            text = text,
            mood = currentState.currentMood
        )

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMsg,
                responseState = RomiResponseState.Thinking,
                statusCaption = "Romi is thinking… ✨",
                lastFailedMessage = null,
                voiceChat = state.voiceChat.copy(
                    phase = if (state.voiceChat.isContinuousMode) VoiceChatPhase.PROCESSING else VoiceChatPhase.OFF,
                    partialTranscript = ""
                )
            )
        }

        requestAiReply(
            userText = text,
            nickname = currentState.nickname,
            currentMood = currentState.currentMood,
            speakingStyle = currentState.speakingStyle,
            priorHistory = priorHistory,
            isVoiceTurn = isVoiceTurn || currentState.voiceChat.isContinuousMode
        )
    }

    fun retryLastMessage() {
        val failed = _uiState.value.lastFailedMessage ?: return
        if (_uiState.value.responseState is RomiResponseState.Thinking) return
        val currentState = _uiState.value
        val priorHistory = currentState.messages.dropLastWhile {
            it.sender == MessageSender.USER && it.text == failed
        }
        avatarAnimationController.setThinking(true)
        _uiState.update { state ->
            state.copy(
                responseState = RomiResponseState.Thinking,
                statusCaption = "Romi is thinking… ✨",
                lastFailedMessage = null
            )
        }
        requestAiReply(
            userText = failed,
            nickname = currentState.nickname,
            currentMood = currentState.currentMood,
            speakingStyle = currentState.speakingStyle,
            priorHistory = priorHistory,
            isVoiceTurn = currentState.voiceChat.isContinuousMode
        )
    }

    private fun requestAiReply(
        userText: String,
        nickname: String,
        currentMood: RomiMood,
        speakingStyle: SpeakingStyle,
        priorHistory: List<ChatMessage>,
        isVoiceTurn: Boolean
    ) {
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            var activeMood = currentMood
            var activeExpression: StructuredExpression? = null
            var spokenOffset = 0
            var spokenSentenceCount = 0
            var bargeInStarted = false

            try {
                aiRepository.streamCompanionReply(
                    userMessage = userText,
                    nickname = nickname,
                    currentMood = currentMood,
                    speakingStyle = speakingStyle,
                    priorHistory = priorHistory,
                    isVoiceTurn = isVoiceTurn
                ).collect { event ->
                    when (event) {
                        is RomiStreamEvent.Partial -> {
                            avatarAnimationController.setThinking(false)
                            val mood = event.mood ?: activeMood
                            val expr = event.expression ?: activeExpression
                            activeMood = mood
                            activeExpression = expr
                            expressionController.onAiExpression(expr, mood, autoRelax = true)

                            _uiState.update { state ->
                                state.copy(
                                    currentMood = mood,
                                    bubbleText = event.partialReply,
                                    responseState = RomiResponseState.Idle
                                )
                            }

                            val shouldSpeak = _uiState.value.voiceEnabled ||
                                _uiState.value.voiceChat.isContinuousMode
                            if (shouldSpeak && event.partialReply.length > spokenOffset) {
                                val remaining = event.partialReply.substring(spokenOffset)
                                val sentenceEnd = findSentenceBoundary(remaining)
                                if (sentenceEnd > 0) {
                                    val sentence = remaining.substring(0, sentenceEnd).trim()
                                    spokenOffset += sentenceEnd
                                    if (sentence.isNotEmpty()) {
                                        voiceSpeaker.speak(
                                            text = sentence,
                                            mood = activeMood,
                                            queueAdd = spokenSentenceCount > 0
                                        )
                                        spokenSentenceCount++
                                        if (_uiState.value.voiceChat.isContinuousMode && !bargeInStarted) {
                                            bargeInStarted = true
                                            _uiState.update { s ->
                                                s.copy(
                                                    voiceChat = s.voiceChat.copy(
                                                        phase = VoiceChatPhase.SPEAKING
                                                    )
                                                )
                                            }
                                            voiceInputManager.startBargeInMonitor()
                                        }
                                    }
                                }
                            }
                        }

                        is RomiStreamEvent.Completed -> {
                            avatarAnimationController.setThinking(false)
                            val aiReply = event.reply
                            expressionController.onAiExpression(
                                expression = aiReply.expression,
                                mood = aiReply.mood,
                                autoRelax = true
                            )
                            val romiMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                sender = MessageSender.ROMI,
                                text = aiReply.reply,
                                mood = aiReply.mood
                            )

                            val shouldSpeak = _uiState.value.voiceEnabled ||
                                _uiState.value.voiceChat.isContinuousMode
                            val inContinuous = _uiState.value.voiceChat.isContinuousMode

                            _uiState.update { state ->
                                state.copy(
                                    currentMood = aiReply.mood,
                                    bubbleText = aiReply.reply,
                                    statusCaption = aiReply.statusNote,
                                    messages = state.messages + romiMsg,
                                    responseState = RomiResponseState.Idle,
                                    lastFailedMessage = null,
                                    affectionBurstTick = state.affectionBurstTick + 1,
                                    voiceChat = state.voiceChat.copy(
                                        phase = if (inContinuous && shouldSpeak) {
                                            VoiceChatPhase.SPEAKING
                                        } else {
                                            state.voiceChat.phase
                                        }
                                    )
                                )
                            }

                            if (shouldSpeak) {
                                val tail = if (spokenOffset < aiReply.reply.length) {
                                    aiReply.reply.substring(spokenOffset).trim()
                                } else {
                                    ""
                                }
                                if (tail.isNotEmpty()) {
                                    voiceSpeaker.speak(
                                        text = tail,
                                        mood = aiReply.mood,
                                        queueAdd = spokenSentenceCount > 0
                                    )
                                    spokenSentenceCount++
                                } else if (spokenSentenceCount == 0) {
                                    voiceSpeaker.speak(
                                        text = aiReply.reply,
                                        mood = aiReply.mood,
                                        queueAdd = false
                                    )
                                    spokenSentenceCount++
                                }

                                if (inContinuous && spokenSentenceCount > 0 && !bargeInStarted) {
                                    bargeInStarted = true
                                    voiceInputManager.startBargeInMonitor()
                                } else if (inContinuous && spokenSentenceCount == 0) {
                                    scheduleListenRestart(250L)
                                }
                            } else if (inContinuous) {
                                scheduleListenRestart(250L)
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Romi AI request timed out", e)
                avatarAnimationController.setThinking(false)
                _uiState.update { state ->
                    state.copy(
                        responseState = RomiResponseState.Error("Romi took too long to reply. Please try again."),
                        statusCaption = "Connection timed out",
                        lastFailedMessage = userText,
                        voiceChat = state.voiceChat.copy(
                            phase = if (state.voiceChat.isContinuousMode) VoiceChatPhase.LISTENING else VoiceChatPhase.OFF
                        )
                    )
                }
                if (_uiState.value.voiceChat.isContinuousMode) {
                    scheduleListenRestart(600L)
                }
            } catch (e: CancellationException) {
                avatarAnimationController.setThinking(false)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Romi AI request failed", e)
                avatarAnimationController.setThinking(false)
                _uiState.update { state ->
                    state.copy(
                        responseState = RomiResponseState.Error(
                            e.localizedMessage ?: "Could not reach Romi's AI brain. Check network and try again."
                        ),
                        statusCaption = "Unable to reply right now",
                        lastFailedMessage = userText,
                        voiceChat = state.voiceChat.copy(
                            isContinuousMode = false,
                            phase = VoiceChatPhase.OFF
                        )
                    )
                }
            }
        }
    }

    private fun findSentenceBoundary(text: String): Int {
        for (i in text.indices) {
            val ch = text[i]
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                val nextIsSpaceOrEnd = (i + 1 >= text.length) || text[i + 1].isWhitespace()
                if (nextIsSpaceOrEnd && i >= 10) {
                    return i + 1
                }
            }
        }
        return -1
    }

    fun onBubbleTapped() {
        val state = _uiState.value
        when (state.speechPlaybackStatus) {
            SpeechPlaybackStatus.SPEAKING -> {
                if (state.voiceChat.isContinuousMode) {
                    interruptAndListen()
                } else {
                    voiceSpeaker.stop()
                }
            }
            SpeechPlaybackStatus.PAUSED -> voiceSpeaker.resume()
            else -> voiceSpeaker.speak(state.bubbleText, state.currentMood, queueAdd = false)
        }
    }

    fun toggleSpeechPauseResume() {
        when (_uiState.value.speechPlaybackStatus) {
            SpeechPlaybackStatus.SPEAKING -> voiceSpeaker.pause()
            SpeechPlaybackStatus.PAUSED -> voiceSpeaker.resume()
            else -> Unit
        }
    }

    fun toggleVoice() {
        val next = !_uiState.value.voiceEnabled
        if (!next) {
            voiceSpeaker.stop()
            if (_uiState.value.voiceChat.isContinuousMode) {
                stopVoiceChat()
            }
        }
        viewModelScope.launch {
            prefsRepository.setVoiceEnabled(next)
        }
    }

    fun updateNickname(nickname: String) {
        viewModelScope.launch {
            prefsRepository.setNickname(nickname)
        }
    }

    fun updateSpeakingStyle(style: SpeakingStyle) {
        viewModelScope.launch {
            prefsRepository.setSpeakingStyle(style)
        }
    }

    fun clearChat() {
        hasUserInteracted = false
        stopVoiceChat()
        expressionController.returnToIdle()
        val name = _uiState.value.nickname
        _uiState.update { state ->
            state.copy(
                currentMood = RomiMood.HAPPY,
                bubbleText = "Hii $name! ♡\nFresh start! What shall we talk about? 🥰",
                statusCaption = "Tap a mood or chat with Romi ♡",
                messages = emptyList(),
                responseState = RomiResponseState.Idle,
                lastFailedMessage = null
            )
        }
    }

    fun onScreenStopped() {
        if (_uiState.value.speechPlaybackStatus == SpeechPlaybackStatus.SPEAKING) {
            voiceSpeaker.pause()
        }
        if (_uiState.value.voiceChat.isContinuousMode) {
            stopVoiceChat()
        } else if (_uiState.value.voiceChat.phase == VoiceChatPhase.LISTENING) {
            stopSingleDictation()
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeAiJob?.cancel()
        restartListenJob?.cancel()
        stopLiveSessionResources()
        avatarAnimationController.release()
        voiceInputManager.destroy()
        voiceSpeaker.shutdown()
    }
}
