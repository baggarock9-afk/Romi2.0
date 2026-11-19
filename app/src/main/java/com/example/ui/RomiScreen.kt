package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.RomiResponseState
import com.example.model.VoiceChatPhase
import com.example.ui.components.ChatHistorySheet
import com.example.ui.components.CompanionSettingsSheet
import com.example.ui.components.ErrorBanner
import com.example.ui.components.MoodSelectorRow
import com.example.ui.components.QuickPromptsRow
import com.example.ui.components.RomiChatInputBar
import com.example.ui.components.RomiHeaderBar
import com.example.ui.components.RomiHeroStage
import com.example.ui.components.VoiceConversationBanner
import com.example.ui.theme.LocalRomiPalette

@Composable
fun RomiScreen(
    viewModel: RomiViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalRomiPalette.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var inputText by rememberSaveable { mutableStateOf("") }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var pendingContinuousAfterGrant by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onMicPermissionResult(
            granted = granted,
            startContinuous = pendingContinuousAfterGrant
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.syncMicPermission()
                Lifecycle.Event.ON_STOP -> viewModel.onScreenStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val quickPrompts = remember {
        listOf(
            "Kya haal hai? ✨",
            "Tell me a joke 😂",
            "Cheer me up! 💖",
            "Chai or coffee? ☕"
        )
    }

    val isThinking = uiState.responseState is RomiResponseState.Thinking
    val isListening = uiState.voiceChat.phase == VoiceChatPhase.LISTENING
    val showVoiceBanner = uiState.voiceChat.isContinuousMode ||
        uiState.voiceChat.isSingleDictation ||
        uiState.voiceChat.statusHint.isNotBlank()

    val onVoiceChatToggleClick: () -> Unit = {
        if (uiState.voiceChat.isContinuousMode) {
            viewModel.stopVoiceChat()
        } else if (uiState.voiceChat.hasMicPermission) {
            viewModel.toggleContinuousVoiceChat()
        } else {
            pendingContinuousAfterGrant = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val onMicButtonClick: () -> Unit = {
        when {
            uiState.voiceChat.isSingleDictation -> viewModel.stopSingleDictation()
            uiState.voiceChat.isContinuousMode &&
                (uiState.isSpeaking || uiState.voiceChat.phase == VoiceChatPhase.SPEAKING || isThinking) -> {
                viewModel.interruptAndListen()
            }
            uiState.voiceChat.isContinuousMode && isListening -> viewModel.stopVoiceChat()
            uiState.voiceChat.hasMicPermission -> viewModel.startSingleVoiceDictation()
            else -> {
                pendingContinuousAfterGrant = false
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(palette.bgTopGlow, palette.bgDeep),
                    center = Offset(500f, 180f),
                    radius = 1500f
                )
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWideLayout = maxWidth >= 680.dp

            if (isWideLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1020.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RomiHeroStage(
                        mood = uiState.currentMood,
                        bubbleText = uiState.bubbleText,
                        statusCaption = uiState.statusCaption,
                        responseState = uiState.responseState,
                        isSpeaking = uiState.isSpeaking,
                        isListening = isListening,
                        avatarState = uiState.avatarRenderState,
                        speechPlaybackStatus = uiState.speechPlaybackStatus,
                        affectionBurstTick = uiState.affectionBurstTick,
                        onPortraitClick = { viewModel.onPortraitTapped() },
                        onBubbleClick = { viewModel.onBubbleTapped() },
                        onTogglePauseResume = { viewModel.toggleSpeechPauseResume() },
                        modifier = Modifier
                            .weight(1.05f)
                            .fillMaxHeight()
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            RomiHeaderBar(
                                voiceEnabled = uiState.voiceEnabled,
                                isSpeaking = uiState.isSpeaking,
                                isListening = isListening,
                                isVoiceChatActive = uiState.voiceChat.isContinuousMode,
                                messageCount = uiState.messages.size,
                                responseState = uiState.responseState,
                                onToggleVoice = { viewModel.toggleVoice() },
                                onToggleVoiceChat = onVoiceChatToggleClick,
                                onOpenHistory = { showHistorySheet = true },
                                onOpenSettings = { showSettingsSheet = true }
                            )

                            MoodSelectorRow(
                                selectedMood = uiState.currentMood,
                                onSelectMood = { viewModel.selectMood(it) }
                            )

                            QuickPromptsRow(
                                prompts = quickPrompts,
                                enabled = !isThinking,
                                onPromptClick = { viewModel.sendMessage(it) }
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AnimatedVisibility(visible = showVoiceBanner) {
                                VoiceConversationBanner(
                                    voiceChat = uiState.voiceChat,
                                    onInterruptClick = { viewModel.interruptAndListen() },
                                    onStopDictationClick = { viewModel.stopSingleDictation() },
                                    onEndVoiceChatClick = { viewModel.stopVoiceChat() }
                                )
                            }

                            AnimatedVisibility(visible = uiState.responseState is RomiResponseState.Error) {
                                val err = uiState.responseState as? RomiResponseState.Error
                                if (err != null) {
                                    ErrorBanner(
                                        message = err.message,
                                        onRetry = { viewModel.retryLastMessage() }
                                    )
                                }
                            }

                            RomiChatInputBar(
                                inputText = inputText,
                                isThinking = isThinking,
                                isListening = isListening,
                                livePartialTranscript = uiState.voiceChat.partialTranscript,
                                onInputChange = { inputText = it },
                                onMicClick = onMicButtonClick,
                                onSend = {
                                    val msg = inputText
                                    inputText = ""
                                    viewModel.sendMessage(msg)
                                }
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 540.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RomiHeaderBar(
                        voiceEnabled = uiState.voiceEnabled,
                        isSpeaking = uiState.isSpeaking,
                        isListening = isListening,
                        isVoiceChatActive = uiState.voiceChat.isContinuousMode,
                        messageCount = uiState.messages.size,
                        responseState = uiState.responseState,
                        onToggleVoice = { viewModel.toggleVoice() },
                        onToggleVoiceChat = onVoiceChatToggleClick,
                        onOpenHistory = { showHistorySheet = true },
                        onOpenSettings = { showSettingsSheet = true }
                    )

                    RomiHeroStage(
                        mood = uiState.currentMood,
                        bubbleText = uiState.bubbleText,
                        statusCaption = uiState.statusCaption,
                        responseState = uiState.responseState,
                        isSpeaking = uiState.isSpeaking,
                        isListening = isListening,
                        avatarState = uiState.avatarRenderState,
                        speechPlaybackStatus = uiState.speechPlaybackStatus,
                        affectionBurstTick = uiState.affectionBurstTick,
                        onPortraitClick = { viewModel.onPortraitTapped() },
                        onBubbleClick = { viewModel.onBubbleTapped() },
                        onTogglePauseResume = { viewModel.toggleSpeechPauseResume() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    MoodSelectorRow(
                        selectedMood = uiState.currentMood,
                        onSelectMood = { viewModel.selectMood(it) }
                    )

                    QuickPromptsRow(
                        prompts = quickPrompts,
                        enabled = !isThinking,
                        onPromptClick = { viewModel.sendMessage(it) }
                    )

                    AnimatedVisibility(visible = showVoiceBanner) {
                        VoiceConversationBanner(
                            voiceChat = uiState.voiceChat,
                            onInterruptClick = { viewModel.interruptAndListen() },
                            onStopDictationClick = { viewModel.stopSingleDictation() },
                            onEndVoiceChatClick = { viewModel.stopVoiceChat() }
                        )
                    }

                    AnimatedVisibility(visible = uiState.responseState is RomiResponseState.Error) {
                        val err = uiState.responseState as? RomiResponseState.Error
                        if (err != null) {
                            ErrorBanner(
                                message = err.message,
                                onRetry = { viewModel.retryLastMessage() }
                            )
                        }
                    }

                    RomiChatInputBar(
                        inputText = inputText,
                        isThinking = isThinking,
                        isListening = isListening,
                        livePartialTranscript = uiState.voiceChat.partialTranscript,
                        onInputChange = { inputText = it },
                        onMicClick = onMicButtonClick,
                        onSend = {
                            val msg = inputText
                            inputText = ""
                            viewModel.sendMessage(msg)
                        }
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }

    if (showHistorySheet) {
        ChatHistorySheet(
            messages = uiState.messages,
            nickname = uiState.nickname,
            onDismiss = { showHistorySheet = false }
        )
    }

    if (showSettingsSheet) {
        CompanionSettingsSheet(
            nickname = uiState.nickname,
            voiceEnabled = uiState.voiceEnabled,
            speakingStyle = uiState.speakingStyle,
            onNicknameSave = { viewModel.updateNickname(it) },
            onVoiceToggle = { viewModel.toggleVoice() },
            onStyleSelect = { viewModel.updateSpeakingStyle(it) },
            onClearChat = { viewModel.clearChat() },
            onDismiss = { showSettingsSheet = false }
        )
    }
}
