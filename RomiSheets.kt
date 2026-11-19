package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.model.SpeakingStyle
import com.example.ui.theme.LocalRomiPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistorySheet(
    messages: List<ChatMessage>,
    nickname: String,
    onDismiss: () -> Unit
) {
    val palette = LocalRomiPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.bgDeep,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(14.dp))

            if (messages.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = palette.inputSurface,
                    border = BorderStroke(1.dp, palette.pinkBorder.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.softCaption
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isUser = msg.sender == MessageSender.USER
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                modifier = Modifier.widthIn(max = 290.dp),
                                shape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (isUser) 18.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 18.dp
                                ),
                                color = if (isUser) palette.chipActiveSurface else palette.bubbleSurface
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(
                                        text = if (isUser) nickname else "Romi ${msg.mood.emoji}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.bubbleText.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.bubbleText
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = timeFormatter.format(Date(msg.timestampMillis)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.bubbleText.copy(alpha = 0.55f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionSettingsSheet(
    nickname: String,
    voiceEnabled: Boolean,
    speakingStyle: SpeakingStyle,
    onNicknameSave: (String) -> Unit,
    onVoiceToggle: () -> Unit,
    onStyleSelect: (SpeakingStyle) -> Unit,
    onClearChat: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalRomiPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftName by remember(nickname) { mutableStateOf(nickname) }

    ModalBottomSheet(
        onDismissRequest = {
            if (draftName.isNotBlank() && draftName != nickname) {
                onNicknameSave(draftName)
            }
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = palette.bgDeep,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )

            OutlinedTextField(
                value = draftName,
                onValueChange = {
                    draftName = it
                    if (it.isNotBlank()) onNicknameSave(it)
                },
                label = { Text(stringResource(R.string.settings_nickname_label)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.inputSurface,
                    unfocusedContainerColor = palette.inputSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = palette.pinkBorder.copy(alpha = 0.4f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = palette.softCaption
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nickname_input")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_style_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.softCaption
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeakingStyle.entries.forEach { style ->
                        val selected = style == speakingStyle
                        FilterChip(
                            selected = selected,
                            onClick = { onStyleSelect(style) },
                            label = {
                                Text(
                                    text = style.label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = palette.inputSurface,
                                labelColor = Color.White,
                                selectedContainerColor = palette.chipActiveSurface,
                                selectedLabelColor = palette.chipActiveText
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = palette.pinkBorder.copy(alpha = 0.45f),
                                selectedBorderColor = Color.White.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("style_chip_${style.id}")
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = palette.inputSurface,
                border = BorderStroke(1.dp, palette.pinkBorder.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_voice_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.settings_voice_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.softCaption.copy(alpha = 0.8f)
                        )
                    }
                    Switch(
                        checked = voiceEnabled,
                        onCheckedChange = { onVoiceToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = palette.bubbleText,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("settings_voice_switch")
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    onClearChat()
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, palette.errorBorder.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.errorBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("clear_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_clear_chat),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
