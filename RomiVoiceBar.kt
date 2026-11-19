package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.VoiceChatPhase
import com.example.model.VoiceConversationState
import com.example.ui.theme.LocalRomiPalette
import kotlin.math.sin

@Composable
fun VoiceConversationBanner(
    voiceChat: VoiceConversationState,
    onInterruptClick: () -> Unit,
    onStopDictationClick: () -> Unit,
    onEndVoiceChatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalRomiPalette.current
    val isListening = voiceChat.phase == VoiceChatPhase.LISTENING
    val isSpeaking = voiceChat.phase == VoiceChatPhase.SPEAKING
    val isProcessing = voiceChat.phase == VoiceChatPhase.PROCESSING

    val accentColor by animateColorAsState(
        targetValue = when (voiceChat.phase) {
            VoiceChatPhase.LISTENING -> palette.onlineDot
            VoiceChatPhase.SPEAKING -> MaterialTheme.colorScheme.primary
            VoiceChatPhase.PROCESSING -> palette.thinkingDot
            VoiceChatPhase.OFF -> palette.errorBorder
        },
        animationSpec = tween(250),
        label = "voice_accent"
    )

    val phaseLabel = when {
        voiceChat.phase == VoiceChatPhase.LISTENING -> stringResource(R.string.voice_phase_listening)
        voiceChat.phase == VoiceChatPhase.PROCESSING -> stringResource(R.string.voice_phase_processing)
        voiceChat.phase == VoiceChatPhase.SPEAKING -> stringResource(R.string.voice_phase_speaking)
        voiceChat.statusHint.isNotBlank() -> voiceChat.statusHint
        else -> stringResource(R.string.voice_chat_live)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_conversation_banner"),
        shape = RoundedCornerShape(18.dp),
        color = palette.headerSurface.copy(alpha = 0.94f),
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiveAudioWaveform(
                        amplitude = voiceChat.micAmplitude,
                        isActive = isListening || isSpeaking,
                        barColor = accentColor,
                        modifier = Modifier.size(width = 44.dp, height = 24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = phaseLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (voiceChat.statusHint.isNotBlank() && voiceChat.phase != VoiceChatPhase.OFF) {
                            Text(
                                text = voiceChat.statusHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.softCaption.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSpeaking || isProcessing) {
                        Button(
                            onClick = onInterruptClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = palette.bubbleText
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("voice_interrupt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = stringResource(R.string.cd_interrupt_romi),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.voice_interrupt_action),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    } else if (voiceChat.isSingleDictation && !voiceChat.isContinuousMode) {
                        OutlinedButton(
                            onClick = onStopDictationClick,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("voice_stop_dictation_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.voice_end_action),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    if (voiceChat.isContinuousMode) {
                        OutlinedButton(
                            onClick = onEndVoiceChatClick,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, palette.errorBorder.copy(alpha = 0.75f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.errorBorder),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("voice_end_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CallEnd,
                                contentDescription = stringResource(R.string.cd_end_voice_chat),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.voice_end_action),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            if (voiceChat.partialTranscript.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.inputSurface.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, palette.pinkBorder.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "“${voiceChat.partialTranscript}”",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = palette.softCaption,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LiveAudioWaveform(
    amplitude: Float,
    isActive: Boolean,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val smoothAmp by animateFloatAsState(
        targetValue = if (isActive) amplitude.coerceIn(0.12f, 1f) else 0.08f,
        animationSpec = tween(durationMillis = 80),
        label = "waveform_amp"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_idle")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(modifier = modifier) {
        val barCount = 5
        val spacing = size.width * 0.10f
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = (size.width - totalSpacing) / barCount
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val harmonic = ((sin(phase + i * 1.1f) + 1f) * 0.5f)
            val centerWeight = 1f - kotlin.math.abs(i - 2) * 0.18f
            val ratio = (0.20f + smoothAmp * (0.45f + 0.55f * harmonic) * centerWeight)
                .coerceIn(0.16f, 1f)
            val barHeight = size.height * ratio
            val x = i * (barWidth + spacing)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
