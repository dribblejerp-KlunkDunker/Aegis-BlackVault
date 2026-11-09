package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AegisColorScheme = darkColorScheme(
    primary = PhosphorGreen,
    onPrimary = Color(0xFF04120A),
    primaryContainer = ObsidianSurface,
    onPrimaryContainer = PhosphorGreen,
    secondary = AmberSignal,
    onSecondary = Color(0xFF140B00),
    secondaryContainer = AmberDim,
    onSecondaryContainer = AmberSignal,
    tertiary = CyanTelemetry,
    onTertiary = Color(0xFF00141F),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianPanel,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurface,
    onSurfaceVariant = TextSecondary,
    error = AlarmRed,
    onError = Color.White,
    outline = PhosphorGreenDim
)

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AegisColorScheme,
        typography = Typography,
        content = content
    )
}
