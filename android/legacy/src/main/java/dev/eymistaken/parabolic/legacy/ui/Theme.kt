package dev.eymistaken.parabolic.legacy.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.eymistaken.parabolic.core.Prefs
import dev.eymistaken.parabolic.core.ThemeOption

/*
 * Every role is spelled out. Leaving some to the library defaults is what can
 * end up with containers that are hard to tell apart from the background, so
 * the whole tonal-spot scheme for the brand indigo (#4F5BD5) is listed here,
 * generated with Google's material-color-utilities.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF545A92),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF3D4279),
    inversePrimary = Color(0xFFBDC2FF),
    secondary = Color(0xFF5C5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E0F9),
    onSecondaryContainer = Color(0xFF444559),
    tertiary = Color(0xFF78536B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7EF),
    onTertiaryContainer = Color(0xFF5E3C53),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceTint = Color(0xFF545A92),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF2EFF7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceDim = Color(0xFFDBD9E0),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFEAE7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBDC2FF),
    onPrimary = Color(0xFF262B61),
    primaryContainer = Color(0xFF3D4279),
    onPrimaryContainer = Color(0xFFE0E0FF),
    inversePrimary = Color(0xFF545A92),
    secondary = Color(0xFFC4C4DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF444559),
    onSecondaryContainer = Color(0xFFE1E0F9),
    tertiary = Color(0xFFE7B9D5),
    onTertiary = Color(0xFF45263C),
    tertiaryContainer = Color(0xFF5E3C53),
    onTertiaryContainer = Color(0xFFFFD7EF),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceTint = Color(0xFFBDC2FF),
    inverseSurface = Color(0xFFE4E1E9),
    inverseOnSurface = Color(0xFF303036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF91909A),
    outlineVariant = Color(0xFF46464F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF39393F),
    surfaceDim = Color(0xFF131318),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF34343A),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainerLowest = Color(0xFF0D0E13),
)

/** Wallpaper colors exist from Android 12 on; older versions get the brand palette. */
val dynamicColorSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun LegacyTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val dark = when (prefs.theme) {
        ThemeOption.SYSTEM -> isSystemInDarkTheme()
        ThemeOption.LIGHT -> false
        ThemeOption.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        prefs.dynamicColor && dynamicColorSupported ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    // System bar icons follow the app's theme rather than the system's, so "Açık"
    // on a phone in dark mode does not leave white icons on a white bar. The bars
    // stay transparent, letting the bottom navigation color run under the buttons;
    // Android 7.1 and older cannot draw dark navigation icons, so they get a dark scrim.
    val activity = context as? Activity
    LaunchedEffect(activity, dark) {
        val bars = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, DarkScrim)
        }
        (activity as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }

    // Default typography on purpose: the system font, nothing loaded from assets.
    MaterialTheme(colorScheme = colors, content = content)
}

// The scrim the activity library uses behind light icons it cannot darken.
private val DarkScrim = AndroidColor.argb(0x80, 0x1B, 0x1B, 0x1B)
