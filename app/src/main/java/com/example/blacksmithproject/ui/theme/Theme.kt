package com.example.blacksmithproject.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val ForgeColorScheme = darkColorScheme(
    primary = Ember,
    onPrimary = Color(0xFF331300),
    primaryContainer = EmberContainer,
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Iron,
    onSecondary = Color(0xFF23282E),
    secondaryContainer = BronzeContainer,      // the nav bar pill, a selected segment, tonal buttons
    onSecondaryContainer = GoldBright,
    tertiary = Gold,
    onTertiary = Color(0xFF3B2A08),
    tertiaryContainer = BronzeContainer,
    onTertiaryContainer = GoldBright,
    background = ForgeNight,
    onBackground = Cream,
    surface = ForgePanel,
    onSurface = Cream,
    surfaceVariant = Color(0xFF252C3A),
    onSurfaceVariant = CreamMuted,
    surfaceTint = Gold,
    outline = Bronze,
    outlineVariant = BronzeDeep,
    surfaceDim = ForgeNight,
    surfaceBright = Color(0xFF2E3646),
    surfaceContainerLowest = ForgeSlot,
    surfaceContainerLow = ForgePanel,
    surfaceContainer = Color(0xFF191F2A),
    surfaceContainerHigh = ForgePanelRaised,
    surfaceContainerHighest = Color(0xFF252C3A),
    inverseSurface = Cream,
    inverseOnSurface = Ink,
    inversePrimary = Color(0xFFB4451A),
    error = FlawRed,
    onError = Color(0xFF4A0D05),
)

// Cut close to square: panels, cards, dialogs and sheets read as framed plates, not as rounded phone cards.
private val ForgeShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

/**
 * Material 3 with one fixed dark forge palette in both system modes; dynamic colour is deliberately off so the art
 * palette always matches. The status bar icons are kept light, since the ground is dark whatever the system says.
 */
@Composable
fun BlacksmithProjectTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false } }
    }
    MaterialTheme(colorScheme = ForgeColorScheme, typography = Typography, shapes = ForgeShapes, content = content)
}
