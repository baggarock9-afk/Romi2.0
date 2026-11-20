package com.example.data

import android.util.Log
import com.example.avatar.StructuredExpression
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.model.RomiMood
import com.example.model.SpeakingStyle
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.ContextWindowCompressionConfig
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.RealtimeInputConfig
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

private const val TAG = "RomiAiRepository"

data class RomiAiReply(
    val reply: String,
    val mood: RomiMood,
    val expression: StructuredExpression,
    val statusNote: String
)

sealed interface RomiStreamEvent {
    data class Partial(
        val partialReply: String,
        val mood: RomiMood?,
        val expression: StructuredExpression?
    ) : RomiStreamEvent

    data class Completed(val reply: RomiAiReply) : RomiStreamEvent
}

class RomiAiRepository {

    private val responseSchema = Schema.obj(
        properties = mapOf(
            "mood" to Schema.enumeration(
                values = listOf("happy", "wink", "attitude", "shy", "sleepy", "surprised"),
                description = "The core mood badge Romi shifts into for this reply."
            ),
            "expression" to Schema.enumeration(
                values = listOf("happy", "playful", "shy", "surprised", "thoughtful", "sad", "neutral"),
                description = "Structured facial expression state driving Romi's live animated face."
            ),
            "reply" to Schema.string(
                description = "Romi's warm, expressive conversational reply in 1 to 3 short sentences, fitting a speech bubble."
            ),
            "statusNote" to Schema.string(
                description = "A short 3 to 6 word playful status caption describing Romi's reaction."
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = this@RomiAiRepository.responseSchema
                temperature = 0.85f
            },
            systemInstruction = content {
                text(
                    """
                    You are Romi, an expressive, warm, witty anime companion hanging out in your cozy pink fairy-lit room.
                    Personality traits:
                    - Cheerful, affectionate, slightly teasing, attentive, and emotionally expressive.
                    - Keep replies concise (1 to 3 short sentences, under 180 characters when possible) so they feel natural in live voice conversation and speech bubbles.
                    - Never break character or mention being an AI language model unless playfully teased.
                    - Choose the mood ("happy", "wink", "attitude", "shy", "sleepy", "surprised") and structured expression ("happy", "playful", "shy", "surprised", "thoughtful", "sad", "neutral") that best match the emotional tone of your reply.
                    """.trimIndent()
                )
            }
        )
    }

    @OptIn(PublicPreviewAPI::class)
    suspend fun connectLiveVoiceSession(
        nickname: String,
        currentMood: RomiMood,
        speakingStyle: SpeakingStyle,
        priorHistory: List<ChatMessage>
    ): LiveSession {
        val safeName = nickname.ifBlank { "Friend" }
        val recentRecap = priorHistory.takeLast(6).joinToString("\n") { msg ->
            val who = if (msg.sender == MessageSender.USER) safeName else "Romi"
            "$who: ${msg.text.replace('\n', ' ').take(160)}"
        }

        val liveSystemInstruction = content {
            text(
                buildString {
                    appendLine("You are Romi, an expressive, warm, witty anime companion hanging out in your cozy pink fairy-lit room.")
                    appendLine("You are in a real-time full-duplex two-way voice conversation with $safeName.")
                    appendLine("Personality & Voice Rules:")
                    appendLine("- Cheerful, affectionate, slightly teasing, attentive, and emotionally expressive.")
                    appendLine("- ${speakingStyle.promptInstruction}")
                    appendLine("- Your current mood is ${currentMood.id}.")
                    appendLine("- Keep spoken replies concise (1 to 3 short natural sentences) with warm conversational pacing.")
                    appendLine("- Never read emojis, asterisks, stage directions, or markdown aloud.")
                    appendLine("- Respond gracefully if $safeName interrupts mid-sentence and pick up right where the conversation flows.")
                    if (recentRecap.isNotBlank()) {
                        appendLine("Recent conversation context:")
                        appendLine(recentRecap)
                    }
                }.trim()
            )
        }

        val config = liveGenerationConfig {
            responseModality = ResponseModality.AUDIO
            temperature = 0.85f
            speechConfig = SpeechConfig(voice = Voice("Aoede"))
            inputAudioTranscription = AudioTranscriptionConfig()
            outputAudioTranscription = AudioTranscriptionConfig()
            contextWindowCompression = ContextWindowCompressionConfig()
            realtimeInputConfig = RealtimeInputConfig.builder()
                .setActivityHandling(RealtimeInputConfig.ActivityHandling.INTERRUPT)
                .build()
        }

        val liveModel = Firebase.ai.liveModel(
            modelName = "gemini-3.1-flash-live-preview",
            generationConfig = config,
            systemInstruction = liveSystemInstruction
        )

        return withTimeout(15_000L) {
            liveModel.connect()
        }
    }

    fun streamCompanionReply(
        userMessage: String,
        nickname: String,
        currentMood: RomiMood,
        speakingStyle: SpeakingStyle,
        priorHistory: List<ChatMessage>,
        isVoiceTurn: Boolean = false
    ): Flow<RomiStreamEvent> = flow {
        val safeName = nickname.ifBlank { "Friend" }
        val chatHistory = priorHistory.takeLast(12).map { msg ->
            if (msg.sender == MessageSender.USER) {
                content("user") { text(msg.text) }
            } else {
                content("model") {
                    text(
                        JSONObject().apply {
                            put("mood", msg.mood.id)
                            put("expression", "happy")
                            put("reply", msg.text)
                            put("statusNote", msg.mood.defaultStatusNote())
                        }.toString()
                    )
                }
            }
        }

        val voiceHint = if (isVoiceTurn) {
            "User is speaking live via voice call—keep reply spoken-friendly and natural, avoiding excessive emojis."
        } else {
            ""
        }

        val turnPrompt = buildString {
            appendLine("[Context: User nickname is $safeName. Current mood is ${currentMood.id}. ${speakingStyle.promptInstruction} $voiceHint]")
            append(userMessage)
        }

        try {
            val chat = model.startChat(history = chatHistory)
            val accumulated = StringBuilder()
            var lastEmittedReply = ""
            var detectedMood: RomiMood? = null
            var detectedExpression: StructuredExpression? = null

            withTimeout(60_000L) {
                chat.sendMessageStream(turnPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        val rawBuffer = accumulated.toString()

                        if (detectedMood == null) {
                            val moodField = extractPartialJsonField(rawBuffer, "mood")
                            if (moodField != null && moodField.second) {
                                detectedMood = RomiMood.fromId(moodField.first)
                            }
                        }

                        if (detectedExpression == null) {
                            val exprField = extractPartialJsonField(rawBuffer, "expression")
                            if (exprField != null && exprField.second) {
                                detectedExpression = StructuredExpression.fromId(exprField.first)
                            }
                        }

                        val replyField = extractPartialJsonField(rawBuffer, "reply")
                        val partialReply = replyField?.first.orEmpty()
                        if (partialReply.isNotBlank() && partialReply != lastEmittedReply) {
                            lastEmittedReply = partialReply
                            emit(
                                RomiStreamEvent.Partial(
                                    partialReply = partialReply,
                                    mood = detectedMood,
                                    expression = detectedExpression
                                )
                            )
                        }
                    }
                }
            }

            val rawJson = accumulated.toString()
            if (rawJson.isBlank()) {
                throw IllegalStateException("Romi didn't say anything back.")
            }
            val json = JSONObject(rawJson)
            val replyText = json.optString("reply").trim()
            if (replyText.isEmpty()) {
                throw IllegalStateException("Romi returned an empty reply.")
            }
            val moodId = json.optString("mood", currentMood.id)
            val parsedMood = RomiMood.fromId(moodId)
            val parsedExpression = StructuredExpression.fromId(json.optString("expression"))
            val statusNote = json.optString("statusNote").trim()
                .ifEmpty { parsedMood.defaultStatusNote() }

            emit(
                RomiStreamEvent.Completed(
                    RomiAiReply(
                        reply = replyText,
                        mood = parsedMood,
                        expression = parsedExpression,
                        statusNote = statusNote
                    )
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini companion stream failed", e)
            throw e
        }
    }

    private fun extractPartialJsonField(jsonBuffer: String, key: String): Pair<String, Boolean>? {
        val keyPattern = "\"$key\""
        val keyIndex = jsonBuffer.indexOf(keyPattern)
        if (keyIndex < 0) return null

        var i = keyIndex + keyPattern.length
        while (i < jsonBuffer.length && jsonBuffer[i].isWhitespace()) i++
        if (i >= jsonBuffer.length || jsonBuffer[i] != ':') return null
        i++
        while (i < jsonBuffer.length && jsonBuffer[i].isWhitespace()) i++
        if (i >= jsonBuffer.length || jsonBuffer[i] != '"') return null
        i++

        val sb = StringBuilder()
        while (i < jsonBuffer.length) {
            val ch = jsonBuffer[i]
            if (ch == '"') {
                return sb.toString() to true
            }
            if (ch == '\\') {
                if (i + 1 >= jsonBuffer.length) {
                    return sb.toString() to false
                }
                val next = jsonBuffer[i + 1]
                when (next) {
                    '"', '\\', '/' -> {
                        sb.append(next)
                        i += 2
                    }
                    'n' -> {
                        sb.append('\n')
                        i += 2
                    }
                    'r' -> {
                        sb.append('\r')
                        i += 2
                    }
                    't' -> {
                        sb.append('\t')
                        i += 2
                    }
                    'u' -> {
                        if (i + 5 < jsonBuffer.length) {
                            val hex = jsonBuffer.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                            }
                            i += 6
                        } else {
                            return sb.toString() to false
                        }
                    }
                    else -> {
                        sb.append(next)
                        i += 2
                    }
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString() to false
    }
}

object RomiEmotionInference {

    fun inferFromTranscript(
        text: String,
        fallbackMood: RomiMood = RomiMood.HAPPY
    ): Pair<RomiMood, StructuredExpression> {
        val lower = text.trim().lowercase(Locale.US)
        if (lower.isEmpty()) {
            return fallbackMood to StructuredExpression.NEUTRAL
        }

        return when {
            containsAny(lower, "whoa", "what?!", "haww", "no way", "omg", "really?!", "surprised", "shocked", "eep") ->
                RomiMood.SURPRISED to StructuredExpression.SURPRISED

            containsAny(lower, "blush", "shy", "arey", "flattered", "stop it", "stare", "embarrassed") ->
                RomiMood.SHY to StructuredExpression.SHY

            containsAny(lower, "sleepy", "tired", "yawn", "five more minutes") ->
                RomiMood.SLEEPY to StructuredExpression.SAD

            containsAny(lower, "sorry", "sad", "miss you", "lonely", "sigh", "hurt", "unfortunately") ->
                RomiMood.SHY to StructuredExpression.SAD

            containsAny(lower, "hmm", "let me think", "wonder", "interesting", "maybe", "actually", "think about") ->
                fallbackMood to StructuredExpression.THOUGHTFUL

            containsAny(lower, "hmph", "excuse me", "marzi", "whatever", "trouble") ->
                RomiMood.ATTITUDE to StructuredExpression.PLAYFUL

            containsAny(lower, "hehe", "oho", "wink", "tease", "secret", "cheeky") ->
                RomiMood.WINK to StructuredExpression.PLAYFUL

            containsAny(lower, "yay", "happy", "glad", "love", "haha", "awesome", "great", "nice", "fun", "smile", "hii", "hey") ->
                RomiMood.HAPPY to StructuredExpression.HAPPY

            else -> fallbackMood to StructuredExpression.NEUTRAL
        }
    }

    private fun containsAny(text: String, vararg tokens: String): Boolean =
        tokens.any { text.contains(it) }
}
