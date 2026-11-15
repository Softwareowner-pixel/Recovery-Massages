package com.example.messagevault

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF128C7E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F5EF),
    onPrimaryContainer = Color(0xFF00201C),
    secondaryContainer = Color(0xFFDCEFE9),
    onSecondaryContainer = Color(0xFF0B2B26),
    surfaceVariant = Color(0xFFEDEFEE),
    onSurfaceVariant = Color(0xFF4A4F4D),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD1BE),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFFB4F0E5),
    secondaryContainer = Color(0xFF1F3A35),
    onSecondaryContainer = Color(0xFFCFEDE6),
    surfaceVariant = Color(0xFF2A2E2D),
    onSurfaceVariant = Color(0xFFC4C9C7),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun MessageVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
