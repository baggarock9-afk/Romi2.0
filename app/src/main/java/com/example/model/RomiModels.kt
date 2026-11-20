package com.example.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R
import com.example.avatar.AvatarRenderState
import com.example.avatar.SpeechPlaybackStatus
import com.example.ui.theme.MoodAttitudeGlow
import com.example.ui.theme.MoodHappyGlow
import com.example.ui.theme.MoodShyGlow
import com.example.ui.theme.MoodSleepyGlow
import com.example.ui.theme.MoodSurprisedGlow
import com.example.ui.theme.MoodWinkGlow

enum class RomiMood(
    val id: String,
    val emoji: String,
    val label: String,
    @DrawableRes val portraitRes: Int,
    val glowColor: Color
) {
    HAPPY(
        id = "happy",
        emoji = "🥰",
        label = "Happy",
        portraitRes = R.drawable.img_romi_happy,
        glowColor = MoodHappyGlow
    ),
    WINK(
        id = "wink",
        emoji = "😉",
        label = "Wink",
        portraitRes = R.drawable.img_romi_wink,
        glowColor = MoodWinkGlow
    ),
    ATTITUDE(
        id = "attitude",
        emoji = "🙄",
        label = "Attitude",
        portraitRes = R.drawable.img_romi_wink,
        glowColor = MoodAttitudeGlow
    ),
    SHY(
        id = "shy",
        emoji = "😳",
        label = "Shy",
        portraitRes = R.drawable.img_romi_shy,
        glowColor = MoodShyGlow
    ),
    SLEEPY(
        id = "sleepy",
        emoji = "😴",
        label = "Sleepy",
        portraitRes = R.drawable.img_romi_shy,
        glowColor = MoodSleepyGlow
    ),
    SURPRISED(
        id = "surprised",
        emoji = "😮",
        label = "Surprised",
        portraitRes = R.drawable.img_romi_happy,
        glowColor = MoodSurprisedGlow
    );

    fun defaultBubbleLine(nickname: String): String {
        val safeName = nickname.ifBlank { "Friend" }
        return when (this) {
            HAPPY -> "Hehe! $safeName, aaj kya masti karein? 🥰"
            WINK -> "Oho, mujhe dekh kar smile aa gayi na? 😉"
            ATTITUDE -> "Excuse me! Main apni marzi ki malik hoon. Hmph! 🙄"
            SHY -> "A-arey… itna mat dekho! 😳"
            SLEEPY -> "Bas five more minutes… 😴"
            SURPRISED -> "HAWW! Ye kya bol diya tumne?! 😮"
        }
    }

    fun defaultStatusNote(): String = when (this) {
        HAPPY -> "Romi mood: Happy ✨"
        WINK -> "Romi mood: Wink 😉"
        ATTITUDE -> "Romi mood: Attitude 🙄"
        SHY -> "Romi mood: Shy 🌸"
        SLEEPY -> "Romi mood: Sleepy 🌙"
        SURPRISED -> "Romi mood: Surprised ⚡"
    }

    companion object {
        fun fromId(id: String): RomiMood =
            entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } ?: HAPPY
    }
}

enum class SpeakingStyle(
    val id: String,
    val label: String,
    val promptInstruction: String
) {
    HINGLISH(
        id = "hinglish",
        label = "Hinglish ♡",
        promptInstruction = "Speak naturally in warm, playful Hinglish (Hindi written in Roman script mixed smoothly with English), like a close friend."
    ),
    ENGLISH(
        id = "english",
        label = "English",
        promptInstruction = "Speak in warm, expressive, playful English."
    ),
    ANIME_KAWAII(
        id = "kawaii",
        label = "Anime Vibe",
        promptInstruction = "Speak in affectionate, upbeat anime-companion English with cute reactions and warm energy."
    );

    companion object {
        fun fromId(id: String): SpeakingStyle =
            entries.firstOrNull { it.id == id } ?: HINGLISH
    }
}

enum class MessageSender {
    USER,
    ROMI
}

data class ChatMessage(
    val id: String,
    val sender: MessageSender,
    val text: String,
    val mood: RomiMood,
    val timestampMillis: Long = System.currentTimeMillis()
)

sealed interface RomiResponseState {
    data object Idle : RomiResponseState
    data object Thinking : RomiResponseState
    data class Error(val message: String) : RomiResponseState
}

enum class VoiceChatPhase {
    OFF,
    LISTENING,
    PROCESSING,
    SPEAKING
}

data class VoiceConversationState(
    val isContinuousMode: Boolean = false,
    val isSingleDictation: Boolean = false,
    val phase: VoiceChatPhase = VoiceChatPhase.OFF,
    val partialTranscript: String = "",
    val micAmplitude: Float = 0f,
    val hasMicPermission: Boolean = false,
    val sttAvailable: Boolean = true,
    val statusHint: String = ""
)

data class RomiUiState(
    val nickname: String = "Manish",
    val currentMood: RomiMood = RomiMood.HAPPY,
    val bubbleText: String = "Hii Manish! ♡\nFinally, you're here! What shall we do today? 🥰",
    val statusCaption: String = "Tap a mood or chat with Romi ♡",
    val voiceEnabled: Boolean = true,
    val isSpeaking: Boolean = false,
    val speechPlaybackStatus: SpeechPlaybackStatus = SpeechPlaybackStatus.IDLE,
    val avatarRenderState: AvatarRenderState = AvatarRenderState(),
    val voiceChat: VoiceConversationState = VoiceConversationState(),
    val speakingStyle: SpeakingStyle = SpeakingStyle.HINGLISH,
    val messages: List<ChatMessage> = emptyList(),
    val responseState: RomiResponseState = RomiResponseState.Idle,
    val lastFailedMessage: String? = null,
    val affectionBurstTick: Int = 0
)
