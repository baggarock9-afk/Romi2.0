package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.avatar.AvatarRenderState
import com.example.avatar.SpeechPlaybackStatus
import com.example.model.RomiMood
import com.example.model.RomiResponseState
import com.example.ui.theme.LocalRomiPalette
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RomiHeaderBar(
    voiceEnabled: Boolean,
    isSpeaking: Boolean,
    isListening: Boolean = false,
    isVoiceChatActive: Boolean = false,
    messageCount: Int,
    responseState: RomiResponseState,
    onToggleVoice: () -> Unit,
    onToggleVoiceChat: () -> Unit = {},
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalRomiPalette.current
    val isThinking = responseState is RomiResponseState.Thinking
    val isError = responseState is RomiResponseState.Error

    val statusBg = when {
        isThinking -> palette.thinkingBg
        isError -> palette.errorBg
        else -> palette.onlineBg
    }
    val statusDot = when {
        isThinking -> palette.thinkingDot
        isError -> palette.errorBorder
        else -> palette.onlineDot
    }
    val statusText = when {
        isListening -> stringResource(R.string.voice_chat_live)
        isThinking -> stringResource(R.string.status_thinking)
        isSpeaking -> stringResource(R.string.status_speaking)
        isError -> stringResource(R.string.status_offline)
        else -> stringResource(R.string.status_online)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = palette.headerSurface,
        border = BorderStroke(1.dp, palette.pinkBorder.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.brand_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 1
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = statusBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .graphicsLayer {
                                        scaleX = dotScale
                                        scaleY = dotScale
                                    }
                                    .clip(CircleShape)
                                    .background(statusDot)
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.brand_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.softCaption.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onToggleVoiceChat,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("voice_chat_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RecordVoiceOver,
                        contentDescription = stringResource(R.string.cd_voice_call_toggle),
                        tint = if (isVoiceChatActive) palette.onlineDot else palette.softCaption.copy(alpha = 0.85f)
                    )
                }

                IconButton(
                    onClick = onToggleVoice,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("voice_toggle_button")
                ) {
                    Icon(
                        imageVector = if (voiceEnabled) {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        },
                        contentDescription = stringResource(R.string.cd_voice_toggle),
                        tint = if (voiceEnabled) MaterialTheme.colorScheme.primary else palette.softCaption.copy(alpha = 0.6f)
                    )
                }

                IconButton(
                    onClick = onOpenHistory,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("history_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (messageCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = palette.bubbleText
                                ) {
                                    Text(text = messageCount.coerceAtMost(99).toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Chat,
                            contentDescription = stringResource(R.string.cd_chat_history),
                            tint = Color.White
                        )
                    }
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun RomiHeroStage(
    mood: RomiMood,
    bubbleText: String,
    statusCaption: String,
    responseState: RomiResponseState,
    isSpeaking: Boolean,
    isListening: Boolean = false,
    avatarState: AvatarRenderState = AvatarRenderState(),
    speechPlaybackStatus: SpeechPlaybackStatus = SpeechPlaybackStatus.IDLE,
    affectionBurstTick: Int,
    onPortraitClick: () -> Unit,
    onBubbleClick: () -> Unit,
    onTogglePauseResume: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalRomiPalette.current
    val isThinking = responseState is RomiResponseState.Thinking

    val moodGlow by animateColorAsState(
        targetValue = mood.glowColor,
        animationSpec = tween(durationMillis = 450),
        label = "mood_glow"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "romi_live_motion")
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_phase"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = moodGlow,
                spotColor = moodGlow
            )
            .testTag("romi_hero_card"),
        shape = RoundedCornerShape(26.dp),
        color = palette.chipSurface,
        border = BorderStroke(1.5.dp, moodGlow.copy(alpha = 0.65f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onPortraitClick)
        ) {
            // Cozy fairy-lit bedroom backdrop with subtle 2.5D parallax
            Image(
                painter = painterResource(id = R.drawable.img_romi_room_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.06f
                        scaleY = 1.06f
                        translationX = -sin(particlePhase) * 6f
                        translationY = cos(particlePhase) * 4f
                    }
            )

            // Soft mood-reactive atmosphere overlay behind Romi
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                moodGlow.copy(alpha = 0.16f),
                                Color.Transparent,
                                palette.bgDeep.copy(alpha = if (mood == RomiMood.SLEEPY) 0.56f else 0.32f)
                            )
                        )
                    )
            )

            // Live 2D animated Romi character (blinking, breathing, head sway, lip-sync, 9 animation states)
            val portraitDescription = stringResource(R.string.cd_romi_portrait)
            RomiLiveAvatar(
                avatarState = avatarState,
                affectionBurstTick = affectionBurstTick,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = portraitDescription }
                    .testTag("romi_live_avatar")
            )

            // Ambient twinkling sparkles drawn on Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val points = listOf(
                    Offset(size.width * 0.14f, size.height * 0.18f),
                    Offset(size.width * 0.28f, size.height * 0.36f),
                    Offset(size.width * 0.85f, size.height * 0.48f),
                    Offset(size.width * 0.18f, size.height * 0.68f),
                    Offset(size.width * 0.74f, size.height * 0.72f)
                )
                points.forEachIndexed { index, base ->
                    val driftX = cos(particlePhase + index * 1.3f) * 8f
                    val driftY = sin(particlePhase + index * 1.7f) * 10f
                    val pulseAlpha = (0.35f + 0.45f * ((sin(particlePhase * 2f + index) + 1f) / 2f))
                    val center = Offset(base.x + driftX, base.y + driftY)
                    drawCircle(
                        color = moodGlow.copy(alpha = pulseAlpha * 0.6f),
                        radius = 7f + index % 3 * 2f,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = pulseAlpha),
                        radius = 3.2f,
                        center = center
                    )
                }
            }

            // Speech bubble overlaid at top-right (matching prototype)
            RomiSpeechBubble(
                text = bubbleText,
                isThinking = isThinking,
                isSpeaking = isSpeaking,
                speechPlaybackStatus = speechPlaybackStatus,
                onClick = onBubbleClick,
                onTogglePauseResume = onTogglePauseResume,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 12.dp, start = 48.dp)
                    .widthIn(max = 260.dp)
                    .testTag("romi_speech_bubble")
            )

            // Bottom mood pill & reaction caption
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = palette.headerSurface.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, moodGlow.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = mood.emoji,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = statusCaption,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.softCaption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RomiSpeechBubble(
    text: String,
    isThinking: Boolean,
    isSpeaking: Boolean,
    speechPlaybackStatus: SpeechPlaybackStatus,
    onClick: () -> Unit,
    onTogglePauseResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalRomiPalette.current
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 4.dp
    )

    Surface(
        modifier = modifier
            .shadow(10.dp, bubbleShape, ambientColor = palette.bgDeep, spotColor = palette.bgDeep)
            .clip(bubbleShape)
            .clickable(onClick = onClick),
        shape = bubbleShape,
        color = palette.bubbleSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f))
    ) {
        AnimatedContent(
            targetState = isThinking,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            label = "bubble_content"
        ) { thinking ->
            if (thinking) {
                ThinkingDotsRow(
                    tint = palette.bubbleText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            } else {
                val isPaused = speechPlaybackStatus == SpeechPlaybackStatus.PAUSED
                val isPlayingOrPaused = isSpeaking ||
                    speechPlaybackStatus == SpeechPlaybackStatus.SPEAKING ||
                    isPaused

                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = palette.bubbleText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPlayingOrPaused) {
                            if (!isPaused) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                            }
                            IconButton(
                                onClick = onTogglePauseResume,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(32.dp)
                                    .testTag("speech_pause_resume_button")
                            ) {
                                Icon(
                                    imageVector = if (isPaused) {
                                        Icons.Rounded.PlayCircle
                                    } else {
                                        Icons.Rounded.PauseCircle
                                    },
                                    contentDescription = stringResource(
                                        if (isPaused) R.string.cd_resume_speaking else R.string.cd_pause_speaking
                                    ),
                                    tint = palette.bubbleText.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Rounded.StopCircle,
                                contentDescription = stringResource(R.string.cd_stop_speaking),
                                tint = palette.bubbleText.copy(alpha = 0.75f),
                                modifier = Modifier.size(17.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = stringResource(R.string.cd_speak_bubble),
                                tint = palette.bubbleText.copy(alpha = 0.55f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingDotsRow(
    tint: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_dots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { idx ->
            val offset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 420,
                        delayMillis = idx * 120,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$idx"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = offset.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.8f))
            )
        }
    }
}

@Composable
fun MoodSelectorRow(
    selectedMood: RomiMood,
    onSelectMood: (RomiMood) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalRomiPalette.current

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(RomiMood.entries, key = { it.id }) { mood ->
            val isSelected = mood == selectedMood
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) palette.chipActiveSurface else palette.chipSurface,
                animationSpec = tween(220),
                label = "chip_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) palette.chipActiveText else Color.White,
                animationSpec = tween(220),
                label = "chip_fg"
            )
            val elevation by animateDpAsState(
                targetValue = if (isSelected) 6.dp else 0.dp,
                label = "chip_elev"
            )

            Surface(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .shadow(elevation, RoundedCornerShape(18.dp), ambientColor = mood.glowColor)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelectMood(mood) }
                    .testTag("mood_chip_${mood.id}"),
                shape = RoundedCornerShape(18.dp),
                color = bgColor,
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) Color.White.copy(alpha = 0.65f) else palette.pinkBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = mood.emoji,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = mood.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun QuickPromptsRow(
    prompts: List<String>,
    enabled: Boolean,
    onPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalRomiPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        prompts.forEachIndexed { index, prompt ->
            Surface(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = enabled) { onPromptClick(prompt) }
                    .testTag("quick_prompt_$index"),
                shape = RoundedCornerShape(14.dp),
                color = palette.inputSurface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, palette.pinkBorder.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.softCaption,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalRomiPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = palette.errorBg,
        border = BorderStroke(1.dp, palette.errorBorder.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = palette.bubbleText
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .testTag("retry_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.retry_label),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun RomiChatInputBar(
    inputText: String,
    isThinking: Boolean,
    isListening: Boolean = false,
    livePartialTranscript: String = "",
    onInputChange: (String) -> Unit,
    onMicClick: () -> Unit = {},
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalRomiPalette.current
    val canSend = inputText.isNotBlank() && !isThinking
    val placeholderText = if (isListening && livePartialTranscript.isNotBlank()) {
        livePartialTranscript
    } else if (isListening) {
        stringResource(R.string.voice_phase_listening)
    } else {
        stringResource(R.string.input_placeholder)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            placeholder = {
                Text(
                    text = placeholderText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isListening) {
                        palette.onlineDot
                    } else {
                        palette.softCaption.copy(alpha = 0.6f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = palette.inputSurface,
                unfocusedContainerColor = palette.inputSurface,
                focusedBorderColor = if (isListening) palette.onlineDot else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (isListening) {
                    palette.onlineDot.copy(alpha = 0.75f)
                } else {
                    palette.pinkBorder.copy(alpha = 0.45f)
                },
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("message_input")
        )

        FilledIconButton(
            onClick = onMicClick,
            shape = RoundedCornerShape(18.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isListening) palette.onlineDot else palette.chipSurface,
                contentColor = if (isListening) palette.bubbleText else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .sizeIn(minWidth = 52.dp, minHeight = 54.dp)
                .testTag("mic_input_button")
        ) {
            Icon(
                imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = stringResource(R.string.cd_mic_button)
            )
        }

        FilledIconButton(
            onClick = onSend,
            enabled = canSend,
            shape = RoundedCornerShape(18.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = palette.bubbleText,
                disabledContainerColor = palette.chipSurface,
                disabledContentColor = palette.softCaption.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .sizeIn(minWidth = 54.dp, minHeight = 54.dp)
                .testTag("send_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.cd_send_message)
            )
        }
    }
}
