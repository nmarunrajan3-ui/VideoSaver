package com.instasaver.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.instasaver.app.data.local.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = AppleBlue,
    onPrimary = AppleDarkLabel,
    primaryContainer = AppleDarkTertiaryBackground,
    onPrimaryContainer = AppleDarkLabel,
    secondary = AppleIndigo,
    onSecondary = AppleDarkLabel,
    background = AppleDarkBackground,
    onBackground = AppleDarkLabel,
    surface = AppleDarkSecondaryBackground,
    onSurface = AppleDarkLabel,
    surfaceVariant = AppleDarkTertiaryBackground,
    onSurfaceVariant = AppleDarkSecondaryLabel,
    outline = AppleDarkSeparator,
    error = AppleRed
)

private val LightColorScheme = lightColorScheme(
    primary = AppleBlue,
    onPrimary = AppleLightSecondaryBackground,
    primaryContainer = AppleLightTertiaryBackground,
    onPrimaryContainer = AppleLightLabel,
    secondary = AppleIndigo,
    onSecondary = AppleLightSecondaryBackground,
    background = AppleLightBackground,
    onBackground = AppleLightLabel,
    surface = AppleLightSecondaryBackground,
    onSurface = AppleLightLabel,
    surfaceVariant = AppleLightTertiaryBackground,
    onSurfaceVariant = AppleLightSecondaryLabel,
    outline = AppleLightSeparator,
    error = AppleRed
)

@Composable
fun InstaSaverTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
