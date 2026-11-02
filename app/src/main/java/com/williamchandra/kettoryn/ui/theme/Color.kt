package com.williamchandra.kettoryn.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Graphite surfaces keep training data in focus.
val DarkBackground = Color(0xFF0D1015)
val DarkSurface = Color(0xFF191E26)
val DarkMutedSurface = Color(0xFF212833)
val DarkRaisedSurface = Color(0xFF303946)
val DarkForeground = Color(0xFFF6F8FC)
val DarkSecondary = Color(0xFFB3BDCD)
val DarkDivider = Color(0xFF303946)
val DarkAccent = Color(0xFF9EB9E0)
val DarkOnAccent = Color(0xFF0D1015)
val DarkSoftAccent = Color(0xFF253247)
val DarkOnSoftAccent = Color(0xFFD5E6FF)
val DarkError = Color(0xFFFF858A)
val DarkOnError = Color(0xFF2D0606)
val DarkErrorContainer = Color(0xFF3B1215)
val DarkOnErrorContainer = Color(0xFFFECACA)
val DarkFocus = Color(0xFF9EB9E0)
val DarkBrandLime = Color(0xFF9EB9E0)

// Warm paper and steel blue form the light palette.
val LightBackground = Color(0xFFF6F5F1)
val LightSurface = Color(0xFFFFFFFF)
val LightMutedSurface = Color(0xFFEDF0F4)
val LightRaisedSurface = Color(0xFFE7EBF2)
val LightForeground = Color(0xFF1C2634)
val LightSecondary = Color(0xFF5A6574)
val LightDivider = Color(0xFFDDE2E9)
val LightAccent = Color(0xFF2E527E)
val LightOnAccent = Color(0xFFFFFFFF)
val LightSoftAccent = Color(0xFFE3EAF3)
val LightOnSoftAccent = Color(0xFF2E527E)
val LightError = Color(0xFFA62F3D)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFEE2E2)
val LightOnErrorContainer = Color(0xFF7F1D1D)
val LightFocus = Color(0xFF2E527E)
val LightBrandLime = Color(0xFF9EB9E0)

// Backwards-compatible aliases
val CharcoalGround = DarkBackground
val GraphiteSurface = DarkSurface
val GraphiteElevated = DarkMutedSurface
val GraphiteContainerHigh = DarkRaisedSurface
val GraphiteBorder = DarkDivider
val InkPrimary = DarkForeground
val InkSecondary = DarkSecondary
val InkMuted = DarkSecondary
val AthleticLime = DarkAccent
val OnAthleticLime = DarkOnAccent
val AthleticLimeContainer = DarkSoftAccent
val OnAthleticLimeContainer = DarkOnSoftAccent
val TealAccent = DarkAccent
val OnTealAccent = DarkOnAccent
val TealContainer = DarkSoftAccent
val OnTealContainer = DarkOnSoftAccent
val DangerRed = DarkError
val OnDangerRed = DarkOnError
val DangerContainer = DarkErrorContainer
val OnDangerContainer = DarkOnErrorContainer
val WarmupAmber = Color(0xFFFBBF24)

val LightGround = LightBackground
val LightElevated = LightMutedSurface
val LightBorder = LightDivider
val LightInkPrimary = LightForeground
val LightInkSecondary = LightSecondary
val LightAthleticGreen = LightAccent
val OnLightAthleticGreen = LightOnAccent
val LightGreenContainer = LightSoftAccent
val OnLightGreenContainer = LightOnSoftAccent
val LightTeal = LightAccent
val LightDanger = LightError

@Immutable
data class KettorynColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val mutedSurface: Color,
    val raisedSurface: Color,
    val foreground: Color,
    val secondary: Color,
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
    val softAccent: Color,
    val onSoftAccent: Color,
    val error: Color,
    val onError: Color,
    val focus: Color,
    val brandLime: Color
)

val DarkKettorynColors = KettorynColors(
    isDark = true,
    background = DarkBackground,
    surface = DarkSurface,
    mutedSurface = DarkMutedSurface,
    raisedSurface = DarkRaisedSurface,
    foreground = DarkForeground,
    secondary = DarkSecondary,
    divider = DarkDivider,
    accent = DarkAccent,
    onAccent = DarkOnAccent,
    softAccent = DarkSoftAccent,
    onSoftAccent = DarkOnSoftAccent,
    error = DarkError,
    onError = DarkOnError,
    focus = DarkFocus,
    brandLime = DarkBrandLime
)

val LightKettorynColors = KettorynColors(
    isDark = false,
    background = LightBackground,
    surface = LightSurface,
    mutedSurface = LightMutedSurface,
    raisedSurface = LightRaisedSurface,
    foreground = LightForeground,
    secondary = LightSecondary,
    divider = LightDivider,
    accent = LightAccent,
    onAccent = LightOnAccent,
    softAccent = LightSoftAccent,
    onSoftAccent = LightOnSoftAccent,
    error = LightError,
    onError = LightOnError,
    focus = LightFocus,
    brandLime = LightBrandLime
)

val LocalKettorynColors = staticCompositionLocalOf { DarkKettorynColors }
