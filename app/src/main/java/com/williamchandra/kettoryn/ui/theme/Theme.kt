package com.williamchandra.kettoryn.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.williamchandra.kettoryn.data.preferences.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkSoftAccent,
    onPrimaryContainer = DarkOnSoftAccent,
    secondary = DarkAccent,
    onSecondary = DarkOnAccent,
    secondaryContainer = DarkSoftAccent,
    onSecondaryContainer = DarkOnSoftAccent,
    tertiary = WarmupAmber,
    onTertiary = Color(0xFF241A00),
    background = DarkBackground,
    onBackground = DarkForeground,
    surface = DarkSurface,
    onSurface = DarkForeground,
    surfaceVariant = DarkMutedSurface,
    onSurfaceVariant = DarkSecondary,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkMutedSurface,
    surfaceContainerHigh = DarkRaisedSurface,
    surfaceContainerHighest = DarkDivider,
    outline = DarkDivider,
    outlineVariant = DarkMutedSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    primaryContainer = LightSoftAccent,
    onPrimaryContainer = LightOnSoftAccent,
    secondary = LightAccent,
    onSecondary = LightOnAccent,
    secondaryContainer = LightSoftAccent,
    onSecondaryContainer = LightOnSoftAccent,
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightForeground,
    surface = LightSurface,
    onSurface = LightForeground,
    surfaceVariant = LightMutedSurface,
    onSurfaceVariant = LightSecondary,
    surfaceContainerLowest = LightBackground,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightMutedSurface,
    surfaceContainerHigh = LightRaisedSurface,
    surfaceContainerHighest = LightDivider,
    outline = LightDivider,
    outlineVariant = LightDivider,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer
)

val KettorynShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

object KettorynTheme {
    val colors: KettorynColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKettorynColors.current
}

@Composable
fun KettorynTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme
    val kettorynColors = if (useDarkTheme) DarkKettorynColors else LightKettorynColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    CompositionLocalProvider(LocalKettorynColors provides kettorynColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = KettorynShapes,
            content = content
        )
    }
}
