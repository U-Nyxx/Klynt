package com.unyxx.act.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6C63FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A52D5),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF00D4AA),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00B894),
    background = Color(0xFF0F0F0F),
    onBackground = Color.White,
    surface = Color(0xFF1A1A1A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF757575),
    error = Color(0xFFCF6679),
    onError = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6C63FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A52D5),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF00D4AA),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00B894),
    background = Color(0xFFFFFFFF),
    onBackground = Color.Black,
    surface = Color(0xFFFFFFFF),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF444444),
    outline = Color(0xFF888888),
    error = Color(0xFFB00020),
    onError = Color.White
)

@Composable
fun KlyntTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KlyntTypography.typography,
        shapes = KlyntShapes.shapes,
        content = content
    )
}
