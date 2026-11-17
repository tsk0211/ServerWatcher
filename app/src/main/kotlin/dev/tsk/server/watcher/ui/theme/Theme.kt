package dev.tsk.server.watcher.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp

/**
 * Colors Material 3 has no slot for: status meaning (success / warning / info) with matching
 * container tints, plus a couple of helpers the design components need.
 */
@Immutable
data class SwColors(
    val isDark: Boolean,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val star: Color,
)

private val DarkSwColors = SwColors(
    isDark = true,
    success = Color(0xFF2EE88A),
    successContainer = Color(0xFF0B3322),
    onSuccessContainer = Color(0xFF8FF5C2),
    warning = Color(0xFFFFB938),
    warningContainer = Color(0xFF3A2A0A),
    onWarningContainer = Color(0xFFFFD98A),
    danger = Color(0xFFFF5470),
    dangerContainer = Color(0xFF3A1019),
    onDangerContainer = Color(0xFFFFB3C0),
    info = Color(0xFF5B9BFF),
    infoContainer = Color(0xFF11264A),
    onInfoContainer = Color(0xFFB5D2FF),
    star = Color(0xFFFFD180),
)

private val LightSwColors = SwColors(
    isDark = false,
    success = Color(0xFF006B2E),
    successContainer = Color(0xFFC8F2D9),
    onSuccessContainer = Color(0xFF002D14),
    warning = Color(0xFF8F5200),
    warningContainer = Color(0xFFFFE4B3),
    onWarningContainer = Color(0xFF3D2300),
    danger = Color(0xFFB0102B),
    dangerContainer = Color(0xFFFFD6DC),
    onDangerContainer = Color(0xFF4D000D),
    info = Color(0xFF0F4AA8),
    infoContainer = Color(0xFFD3E2FF),
    onInfoContainer = Color(0xFF001F4D),
    star = Color(0xFFB36200),
)

val LocalSwColors = staticCompositionLocalOf { DarkSwColors }

/** `MaterialTheme.sw.success`, `MaterialTheme.sw.warningContainer`, … */
val MaterialTheme.sw: SwColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSwColors.current

private val DarkColorScheme = darkColorScheme(
    primary = ServerWatcherGreen,
    onPrimary = Color(0xFF00210E),
    primaryContainer = Color(0xFF053D24),
    onPrimaryContainer = ServerWatcherGreenDim,
    secondary = Color(0xFF5B9BFF),
    onSecondary = Color(0xFF00255C),
    secondaryContainer = Color(0xFF16305E),
    onSecondaryContainer = Color(0xFFD3E3FF),
    tertiary = ServerWatcherTeal,
    onTertiary = Color(0xFF00332D),
    tertiaryContainer = Color(0xFF0A3B38),
    onTertiaryContainer = Color(0xFF9CF5EA),
    background = DarkBackground,
    onBackground = OnDarkPrimary,
    surface = DarkSurface,
    onSurface = OnDarkPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSecondary,
    surfaceTint = ServerWatcherGreen,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFFF5470),
    onError = Color(0xFF3A0010),
    errorContainer = Color(0xFF4A1020),
    onErrorContainer = Color(0xFFFFB3C0),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006B2E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4F5D8),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF0F4AA8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8FF),
    onSecondaryContainer = Color(0xFF00255C),
    tertiary = Color(0xFF00695C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBDF3EA),
    onTertiaryContainer = Color(0xFF00201C),
    background = LightBackground,
    onBackground = OnLightPrimary,
    surface = LightSurface,
    onSurface = OnLightPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightSecondary,
    surfaceTint = Color(0xFF006B2E),
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = Color(0xFFB0102B),
    onError = Color.White,
    errorContainer = Color(0xFFFFDDE2),
    onErrorContainer = Color(0xFF5C0010),
)

/** Softer, rounder corners than the Material defaults; cards use `large`, controls `medium`. */
private val ServerWatcherShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        // Keep status/navigation bar icons readable when the chosen theme differs from the system one.
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalSwColors provides if (darkTheme) DarkSwColors else LightSwColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ServerWatcherTypography,
            shapes = ServerWatcherShapes,
            content = content,
        )
    }
}
