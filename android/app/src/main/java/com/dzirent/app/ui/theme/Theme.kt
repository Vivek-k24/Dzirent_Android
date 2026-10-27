package com.dzirent.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Design: black, white and greys, plus ONE accent.
/** The accent in light mode (placeholder, owner to confirm). */
private val AccentOnLight = Color(0xFF8E1538)

/**
 * The same hue, lighter, for dark mode: #8E1538 is only 2.1:1 on the dark background.
 * This is 6.0:1 on the background and 5.6:1 on the drawer, so accent text passes 4.5:1.
 */
private val AccentOnDark = Color(0xFFE8638A)

private val Ink = Color(0xFF111111)

private val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * The one accent for the current theme. Used only for: the active tab underline, the
 * center Search button, notification dots, the selected category and the fit row.
 */
val DzirentAccent: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) AccentOnDark else AccentOnLight

/** Icons and text drawn on the accent: white on the dark red, near-black on the light pink. */
val OnDzirentAccent: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Ink else Color.White

private val Grey50 = Color(0xFFFAFAFA)
private val Grey100 = Color(0xFFF2F2F2)
private val Grey200 = Color(0xFFE6E6E6)
private val Grey400 = Color(0xFFA3A3A3)
private val Grey600 = Color(0xFF5E5E5E)
private val Grey800 = Color(0xFF2A2A2A)
private val Grey850 = Color(0xFF202020)
private val Grey900 = Color(0xFF171717)
private val Night = Color(0xFF0E0E0E)

// Every surface container is set too: Material's defaults are tinted purple.
private val LightColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Grey100,
    onPrimaryContainer = Ink,
    inversePrimary = Color.White,
    secondary = Grey600,
    onSecondary = Color.White,
    secondaryContainer = Grey200,
    onSecondaryContainer = Ink,
    tertiary = Grey600,
    onTertiary = Color.White,
    tertiaryContainer = Grey200,
    onTertiaryContainer = Ink,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Grey100,
    onSurfaceVariant = Grey600,
    surfaceTint = Color.Transparent,
    outline = Grey400,
    outlineVariant = Grey200,
    surfaceBright = Color.White,
    surfaceDim = Grey200,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Grey50,
    surfaceContainer = Grey50,
    surfaceContainerHigh = Grey100,
    surfaceContainerHighest = Grey200
)

private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Ink,
    primaryContainer = Grey800,
    onPrimaryContainer = Color.White,
    inversePrimary = Ink,
    secondary = Grey400,
    onSecondary = Ink,
    secondaryContainer = Grey800,
    onSecondaryContainer = Color.White,
    tertiary = Grey400,
    onTertiary = Ink,
    tertiaryContainer = Grey800,
    onTertiaryContainer = Color.White,
    background = Night,
    onBackground = Color.White,
    surface = Night,
    onSurface = Color.White,
    surfaceVariant = Grey800,
    onSurfaceVariant = Grey400,
    surfaceTint = Color.Transparent,
    outline = Grey600,
    outlineVariant = Grey800,
    surfaceBright = Grey800,
    surfaceDim = Night,
    surfaceContainerLowest = Night,
    surfaceContainerLow = Grey900,
    surfaceContainer = Grey900,
    surfaceContainerHigh = Grey850,
    surfaceContainerHighest = Grey800
)

@Composable
fun DzirentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Android 15+ draws the app behind transparent system bars, so the icons must contrast
    // with the app background. Older versions keep the theme's dark bars and light icons.
    val view = LocalView.current
    if (!view.isInEditMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}
