package dev.eymistaken.parabolic.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.eymistaken.parabolic.core.Prefs
import dev.eymistaken.parabolic.core.ThemeOption

/**
 * Fallback palette for devices without Material You (API < 31) or when the
 * user turns dynamic color off. Seeded from the desktop app's violet accent.
 */
private val BrandLight = lightColorScheme(
    primary = Color(0xFF4F5BD5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5C5D72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E0F9),
    onSecondaryContainer = Color(0xFF191A2C),
    tertiary = Color(0xFF78536B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8EE),
    onTertiaryContainer = Color(0xFF2E1126),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    onPrimary = Color(0xFF1C2585),
    primaryContainer = Color(0xFF353FA2),
    onPrimaryContainer = Color(0xFFDFE0FF),
    secondary = Color(0xFFC5C4DD),
    onSecondary = Color(0xFF2E2F42),
    secondaryContainer = Color(0xFF444559),
    onSecondaryContainer = Color(0xFFE1E0F9),
    tertiary = Color(0xFFE8B9D4),
    onTertiary = Color(0xFF46263C),
    tertiaryContainer = Color(0xFF5E3C53),
    onTertiaryContainer = Color(0xFFFFD8EE),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun ParabolicTheme(
    prefs: Prefs,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (prefs.theme) {
        ThemeOption.SYSTEM -> systemDark
        ThemeOption.LIGHT -> false
        ThemeOption.DARK -> true
    }
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        prefs.dynamicColor && supportsDynamic ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> BrandDark
        else -> BrandLight
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ParabolicShapes,
        typography = ParabolicTypography,
        content = content,
    )
}

/** Expressive corners: noticeably rounder than the baseline at every size. */
val ParabolicShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** True when the running device can source colors from the wallpaper. */
val dynamicColorSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
