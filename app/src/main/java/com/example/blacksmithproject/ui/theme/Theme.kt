package com.example.blacksmithproject.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Ember80,
    onPrimary = Color(0xFF3A1500),
    primaryContainer = EmberContainerDark,
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Iron80,
    onSecondary = Color(0xFF23282E),
    secondaryContainer = IronContainerDark,
    onSecondaryContainer = Color(0xFFE2E6EC),
    tertiary = Brass80,
    onTertiary = Color(0xFF3B2A08),
    tertiaryContainer = BrassContainerDark,
    onTertiaryContainer = Color(0xFFFFE9B8),
    background = SootBg,
    onBackground = Parchment,
    surface = SootSurface,
    onSurface = Parchment,
    surfaceVariant = SootVariant,
    onSurfaceVariant = ParchmentMuted,
    outline = OutlineDark,
    outlineVariant = Color(0xFF4E4034),
    surfaceDim = Color(0xFF15100D),
    surfaceBright = Color(0xFF3F3329),
    surfaceContainerLowest = Color(0xFF130E0B),
    surfaceContainerLow = Color(0xFF2A211B),
    surfaceContainer = Color(0xFF2F2620),
    surfaceContainerHigh = Color(0xFF3A2F27),
    surfaceContainerHighest = Color(0xFF453A31),
    inverseSurface = Parchment,
    inverseOnSurface = Ink,
    inversePrimary = Ember40,
)

private val LightColorScheme = lightColorScheme(
    primary = Ember40,
    onPrimary = Color.White,
    primaryContainer = EmberContainerLight,
    onPrimaryContainer = Color(0xFF3A1500),
    secondary = Iron40,
    onSecondary = Color.White,
    secondaryContainer = IronContainerLight,
    onSecondaryContainer = Color(0xFF1C2026),
    tertiary = Brass40,
    onTertiary = Color.White,
    tertiaryContainer = BrassContainerLight,
    onTertiaryContainer = Color(0xFF2A1D00),
    background = ParchmentBg,
    onBackground = Ink,
    surface = ParchmentSurface,
    onSurface = Ink,
    surfaceVariant = ParchmentVariant,
    onSurfaceVariant = InkMuted,
    outline = OutlineLight,
    outlineVariant = Color(0xFFD5C5A8),
    surfaceDim = Color(0xFFE2D3B8),
    surfaceBright = Color(0xFFFDF6E8),
    surfaceContainerLowest = Color(0xFFFFFCF4),
    surfaceContainerLow = Color(0xFFF7ECD8),
    surfaceContainer = Color(0xFFF1E3CB),
    surfaceContainerHigh = Color(0xFFEBDCC1),
    surfaceContainerHighest = Color(0xFFE4D3B5),
    inverseSurface = Ink,
    inverseOnSurface = Parchment,
    inversePrimary = Ember80,
)

/** Material 3 with a fixed forge palette; dynamic colour is deliberately off so the art palette always matches. */
@Composable
fun BlacksmithProjectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
