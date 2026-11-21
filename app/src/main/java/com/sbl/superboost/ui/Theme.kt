package com.sbl.superboost.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SblRed = Color(0xFFE53935)
val SblRedHot = Color(0xFFFF1744)
val SblCardColor = Color(0xFF141414)
val SblOutline = Color(0xFF2A2A2A)
val SblMuted = Color(0xFF9E9E9E)

private val SblColors = darkColorScheme(
    primary = SblRed,
    onPrimary = Color.White,
    secondary = Color(0xFFFF6659),
    background = Color.Black,
    onBackground = Color.White,
    surface = SblCardColor,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = SblMuted,
    outline = SblOutline
)

@Composable
fun SblTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SblColors, content = content)
}
