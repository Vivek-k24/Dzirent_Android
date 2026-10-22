package com.dzirent.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Design specification: black, white and soft grey with one accent color (#2A64F6)
val AccentBlue = Color(0xFF2A64F6)
val SoftGreyLight = Color(0xFFF6F7F9)
val SoftGreyDark = Color(0xFF1E2024)

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color.Black,
    background = Color(0xFF121316),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF181A20),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = SoftGreyDark,
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = SoftGreyLight,
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun DzirentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
