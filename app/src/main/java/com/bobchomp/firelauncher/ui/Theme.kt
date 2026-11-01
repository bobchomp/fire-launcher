package com.bobchomp.firelauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Background = Color(0xFF101216)
val CardBackground = Color(0xFF242832)
val Accent = Color(0xFFFF6A2B)
val TextPrimary = Color(0xFFF2F3F5)
val TextSecondary = Color(0xFFA9AFBB)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Background,
            surface = CardBackground,
            onSurface = TextPrimary,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides TextPrimary, content = content)
    }
}
