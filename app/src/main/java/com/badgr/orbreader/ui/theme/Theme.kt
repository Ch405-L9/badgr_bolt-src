package com.badgr.orbreader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyanTealAccent,
    background = NavyBackground,
    surface = CardSurface,
    onPrimary = NavyBackground,
    onBackground = WarmOffWhite,
    onSurface = WarmOffWhite,
    onSurfaceVariant = TextDimmed
)

private val LightColorScheme = lightColorScheme(
    primary = CyanTealAccent,
    background = Color(0xFFF7F9FC),
    surface = LightCardSurface,
    onPrimary = Color.White,
    onBackground = Color(0xFF1A202C),
    onSurface = Color(0xFF2D3748),
    onSurfaceVariant = LightTextDimmed
)

@Composable
fun OrbreaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
