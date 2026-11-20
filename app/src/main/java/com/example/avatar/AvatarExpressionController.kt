package com.example.avatar

import com.example.model.RomiMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StructuredExpression(val id: String) {
    HAPPY("happy"),
    PLAYFUL("playful"),
    SHY("shy"),
    SURPRISED("surprised"),
    THOUGHTFUL("thoughtful"),
    SAD("sad"),
    NEUTRAL("neutral");

    companion object {
        fun fromId(id: String?): StructuredExpression =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: NEUTRAL
    }
}

data class ExpressionSnapshot(
    val expression: StructuredExpression = StructuredExpression.NEUTRAL,
    val mood: RomiMood = RomiMood.HAPPY,
    val updatedAtMs: Long = 0L
)

/**
 * Maps Gemini structured expression metadata and user mood interactions into
 * smooth expression transitions, relaxing intense emotions back to IDLE/NEUTRAL
 * after a natural conversational pause.
 */
class AvatarExpressionController(
    private val scope: CoroutineScope,
    private val relaxToIdleDelayMs: Long = 10_000L
) {

    private val _expressionState = MutableStateFlow(ExpressionSnapshot())
    val expressionState: StateFlow<ExpressionSnapshot> = _expressionState.asStateFlow()

    private var relaxJob: Job? = null

    fun onAiExpression(
        expression: StructuredExpression?,
        mood: RomiMood,
        autoRelax: Boolean = true
    ) {
        val resolvedExpression = expression ?: mapMoodToExpression(mood)
        _expressionState.value = ExpressionSnapshot(
            expression = resolvedExpression,
            mood = mood,
            updatedAtMs = System.currentTimeMillis()
        )
        if (autoRelax) {
            scheduleRelaxToIdle(mood)
        } else {
            relaxJob?.cancel()
        }
    }

    fun onUserMoodSelected(mood: RomiMood, autoRelax: Boolean = false) {
        val mapped = mapMoodToExpression(mood)
        _expressionState.value = ExpressionSnapshot(
            expression = mapped,
            mood = mood,
            updatedAtMs = System.currentTimeMillis()
        )
        if (autoRelax) {
            scheduleRelaxToIdle(mood)
        } else {
            relaxJob?.cancel()
        }
    }

    fun returnToIdle() {
        relaxJob?.cancel()
        val currentMood = _expressionState.value.mood
        _expressionState.value = ExpressionSnapshot(
            expression = StructuredExpression.NEUTRAL,
            mood = currentMood,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    private fun scheduleRelaxToIdle(mood: RomiMood) {
        relaxJob?.cancel()
        relaxJob = scope.launch {
            delay(relaxToIdleDelayMs)
            _expressionState.value = ExpressionSnapshot(
                expression = StructuredExpression.NEUTRAL,
                mood = mood,
                updatedAtMs = System.currentTimeMillis()
            )
        }
    }

    fun release() {
        relaxJob?.cancel()
        relaxJob = null
    }

    companion object {
        fun mapMoodToExpression(mood: RomiMood): StructuredExpression = when (mood) {
            RomiMood.HAPPY -> StructuredExpression.HAPPY
            RomiMood.WINK, RomiMood.ATTITUDE -> StructuredExpression.PLAYFUL
            RomiMood.SHY -> StructuredExpression.SHY
            RomiMood.SURPRISED -> StructuredExpression.SURPRISED
            RomiMood.SLEEPY -> StructuredExpression.THOUGHTFUL
        }
    }
}
