package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NicoCyan,
    onPrimary = NicoOnCyan,
    primaryContainer = NicoCyanContainer,
    onPrimaryContainer = NicoCyan,
    secondary = NicoCoral,
    onSecondary = Color.White,
    secondaryContainer = NicoCoralContainer,
    onSecondaryContainer = NicoCoral,
    background = NicoObsidian,
    onBackground = NicoTextPrimary,
    surface = NicoSurface,
    onSurface = NicoTextPrimary,
    surfaceVariant = NicoSurfaceVariant,
    onSurfaceVariant = NicoTextSecondary,
    surfaceContainerHigh = NicoSurfaceHigh,
    outline = NicoOutline,
    outlineVariant = NicoSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = NicoCyanDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F5FF),
    onPrimaryContainer = Color(0xFF003545),
    secondary = NicoCoral,
    onSecondary = Color.White,
    background = NicoLightBg,
    onBackground = NicoLightTextPrimary,
    surface = NicoLightSurface,
    onSurface = NicoLightTextPrimary,
    surfaceVariant = NicoLightSurfaceVariant,
    onSurfaceVariant = NicoLightTextSecondary,
    outline = NicoLightOutline
)

@Composable
fun NicoFlowTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
