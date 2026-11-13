package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Emerald500,
    onPrimary = Navy950,
    primaryContainer = Emerald900,
    onPrimaryContainer = Emerald300,
    secondary = Emerald400,
    onSecondary = Navy950,
    secondaryContainer = Navy700,
    onSecondaryContainer = Emerald300,
    tertiary = Amber500,
    onTertiary = Navy950,
    background = Navy900,
    onBackground = TextPrimaryDark,
    surface = Navy800,
    onSurface = TextPrimaryDark,
    surfaceVariant = Navy700,
    onSurfaceVariant = TextSecondaryDark,
    outline = Navy600,
    error = ErrorRed,
    onError = SurfaceWhite
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald600,
    onPrimary = SurfaceWhite,
    primaryContainer = Color(0xFFECFDF5),
    onPrimaryContainer = Emerald900,
    secondary = Color(0xFF0F172A),
    onSecondary = SurfaceWhite,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Amber600,
    onTertiary = SurfaceWhite,
    background = Color(0xFFF8FAFC),
    onBackground = TextPrimaryLight,
    surface = SurfaceWhite,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFE2E8F0),
    error = ErrorRed,
    onError = SurfaceWhite
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Clean, modern bright light theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = (if (darkTheme) Navy900 else SurfaceBackground).toArgb()
                window.navigationBarColor = (if (darkTheme) Navy950 else SurfaceWhite).toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
