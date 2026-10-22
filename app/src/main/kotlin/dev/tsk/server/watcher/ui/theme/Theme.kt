package dev.tsk.server.watcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ServerWatcherGreen,
    onPrimary = DarkBackground,
    primaryContainer = Color(0xFF003322),
    onPrimaryContainer = ServerWatcherGreenDim,
    secondary = ServerWatcherBlue,
    background = DarkBackground,
    onBackground = OnDarkPrimary,
    surface = DarkSurface,
    onSurface = OnDarkPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSecondary,
    outline = DarkOutline,
    error = ServerWatcherRed,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00873A),
    onPrimary = LightBackground,
    primaryContainer = Color(0xFFB8F5D0),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF1565C0),
    background = LightBackground,
    onBackground = OnLightPrimary,
    surface = LightSurface,
    onSurface = OnLightPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightSecondary,
    outline = LightOutline,
    error = ServerWatcherRed,
)

@Composable
fun ServerWatcherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ServerWatcherTypography,
        content = content,
    )
}
