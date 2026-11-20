package com.example.avatar

import com.example.model.RomiMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class AvatarAnimationState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    HAPPY,
    SHY,
    SURPRISED,
    SAD,
    WINK
}

data class AvatarRenderState(
    val animationState: AvatarAnimationState = AvatarAnimationState.IDLE,
    val expression: StructuredExpression = StructuredExpression.NEUTRAL,
    val mood: RomiMood = RomiMood.HAPPY,
    val lipSyncFrame: LipSyncFrame = LipSyncFrame(),
    val blinkProgress: Float = 0f,
    val gazeX: Float = 0f,
    val gazeY: Float = 0f
)

/**
 * Central avatar coordinator combining conversational state (listening, thinking, speaking),
 * structured facial expressions, lip-sync frames, automatic blinking, and subtle gaze shifts.
 */
class AvatarAnimationController(
    private val expressionController: AvatarExpressionController,
    private val lipSyncController: LipSyncController,
    private val scope: CoroutineScope
) {

    private data class ConversationalSignals(
        val isListening: Boolean = false,
        val isThinking: Boolean = false
    )

    private val conversationalSignals = MutableStateFlow(ConversationalSignals())
    private val blinkProgressFlow = MutableStateFlow(0f)
    private val gazeFlow = MutableStateFlow(0f to 0f)

    private val _avatarState = MutableStateFlow(AvatarRenderState())
    val avatarState: StateFlow<AvatarRenderState> = _avatarState.asStateFlow()

    private var combinerJob: Job? = null
    private var blinkJob: Job? = null
    private var gazeJob: Job? = null
    private var tapBlinkJob: Job? = null

    fun start() {
        if (combinerJob?.isActive == true) return
        lipSyncController.start()
        startCombiner()
        startBlinkLoop()
        startGazeLoop()
    }

    fun setListening(listening: Boolean) {
        conversationalSignals.update { it.copy(isListening = listening) }
    }

    fun setThinking(thinking: Boolean) {
        conversationalSignals.update { it.copy(isThinking = thinking) }
    }

    fun triggerReactiveBlink() {
        tapBlinkJob?.cancel()
        tapBlinkJob = scope.launch {
            blinkProgressFlow.value = 1f
            delay(70L)
            blinkProgressFlow.value = 0f
        }
    }

    private fun startCombiner() {
        combinerJob = scope.launch {
            combine(
                conversationalSignals,
                expressionController.expressionState,
                lipSyncController.lipSyncFrame,
                blinkProgressFlow,
                gazeFlow
            ) { signals, exprSnapshot, lipSync, blink, gaze ->
                val resolvedState = resolveAnimationState(
                    isSpeaking = lipSync.isActive,
                    isListening = signals.isListening,
                    isThinking = signals.isThinking,
                    expression = exprSnapshot.expression,
                    mood = exprSnapshot.mood
                )
                AvatarRenderState(
                    animationState = resolvedState,
                    expression = exprSnapshot.expression,
                    mood = exprSnapshot.mood,
                    lipSyncFrame = lipSync,
                    blinkProgress = blink,
                    gazeX = gaze.first,
                    gazeY = gaze.second
                )
            }.collect { combined ->
                _avatarState.value = combined
            }
        }
    }

    private fun startBlinkLoop() {
        blinkJob?.cancel()
        blinkJob = scope.launch {
            while (isActive) {
                val activeState = _avatarState.value.animationState
                val waitMs = when (activeState) {
                    AvatarAnimationState.SAD -> Random.nextLong(1900L, 3400L)
                    AvatarAnimationState.SURPRISED -> Random.nextLong(2900L, 5000L)
                    else -> Random.nextLong(2300L, 4500L)
                }
                delay(waitMs)
                blinkProgressFlow.value = 1f
                delay(if (activeState == AvatarAnimationState.SAD) 115L else 75L)
                blinkProgressFlow.value = 0f

                // Occasional natural double-blink
                if (activeState != AvatarAnimationState.SAD && Random.nextFloat() < 0.22f) {
                    delay(125L)
                    blinkProgressFlow.value = 1f
                    delay(70L)
                    blinkProgressFlow.value = 0f
                }
            }
        }
    }

    private fun startGazeLoop() {
        gazeJob?.cancel()
        gazeJob = scope.launch {
            while (isActive) {
                delay(Random.nextLong(1800L, 3600L))
                val x = (Random.nextFloat() - 0.5f) * 0.24f
                val y = (Random.nextFloat() - 0.5f) * 0.16f
                gazeFlow.value = x to y
            }
        }
    }

    fun release() {
        combinerJob?.cancel()
        blinkJob?.cancel()
        gazeJob?.cancel()
        tapBlinkJob?.cancel()
        combinerJob = null
        blinkJob = null
        gazeJob = null
        tapBlinkJob = null
        lipSyncController.release()
        expressionController.release()
    }

    companion object {
        fun resolveAnimationState(
            isSpeaking: Boolean,
            isListening: Boolean,
            isThinking: Boolean,
            expression: StructuredExpression,
            mood: RomiMood
        ): AvatarAnimationState = when {
            isSpeaking -> AvatarAnimationState.SPEAKING
            isListening -> AvatarAnimationState.LISTENING
            isThinking -> AvatarAnimationState.THINKING
            expression == StructuredExpression.SAD -> AvatarAnimationState.SAD
            expression == StructuredExpression.SHY -> AvatarAnimationState.SHY
            expression == StructuredExpression.SURPRISED -> AvatarAnimationState.SURPRISED
            expression == StructuredExpression.THOUGHTFUL -> AvatarAnimationState.THINKING
            expression == StructuredExpression.PLAYFUL && mood == RomiMood.WINK -> AvatarAnimationState.WINK
            expression == StructuredExpression.PLAYFUL -> AvatarAnimationState.HAPPY
            expression == StructuredExpression.HAPPY -> AvatarAnimationState.HAPPY
            mood == RomiMood.WINK -> AvatarAnimationState.WINK
            mood == RomiMood.SHY -> AvatarAnimationState.SHY
            mood == RomiMood.SURPRISED -> AvatarAnimationState.SURPRISED
            else -> AvatarAnimationState.IDLE
        }
    }
}
