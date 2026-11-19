package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class RomiPalette(
    val bgTopGlow: Color = PlumGlow,
    val bgDeep: Color = PlumDeep,
    val headerSurface: Color = PlumCardSurface,
    val chipSurface: Color = PlumMid,
    val chipActiveSurface: Color = RomiPinkActive,
    val chipActiveText: Color = RomiBerryInk,
    val bubbleSurface: Color = RomiBlushBubble,
    val bubbleText: Color = RomiBerryInk,
    val softCaption: Color = RomiSoftText,
    val pinkBorder: Color = RomiPinkBorder,
    val inputSurface: Color = PlumInputSurface,
    val onlineBg: Color = OnlineGreenBg,
    val onlineDot: Color = OnlineGreenDot,
    val thinkingBg: Color = ThinkingAmberBg,
    val thinkingDot: Color = ThinkingAmberDot,
    val errorBg: Color = ErrorRoseBg,
    val errorBorder: Color = ErrorRoseBorder
)

val LocalRomiPalette = staticCompositionLocalOf { RomiPalette() }

private val RomiDarkColorScheme = darkColorScheme(
    primary = RomiPinkPrimary,
    onPrimary = RomiBerryInk,
    primaryContainer = PlumMid,
    onPrimaryContainer = RomiBlushBubble,
    secondary = RomiPinkActive,
    onSecondary = RomiBerryInk,
    secondaryContainer = PlumInputSurface,
    onSecondaryContainer = RomiSoftText,
    tertiary = MoodWinkGlow,
    background = PlumDeep,
    onBackground = Color.White,
    surface = PlumCardSurface,
    onSurface = Color.White,
    surfaceVariant = PlumInputSurface,
    onSurfaceVariant = RomiSoftText,
    outline = RomiPinkBorder,
    error = ErrorRoseBorder,
    onError = Color.White
)

@Composable
fun RomiTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalRomiPalette provides RomiPalette()) {
        MaterialTheme(
            colorScheme = RomiDarkColorScheme,
            typography = RomiTypography,
            content = content
        )
    }
}
