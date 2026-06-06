package co.mobilise.adda.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Adda is dark-only. Map the design palette onto a Material3 dark scheme. */
private val AddaColorScheme = darkColorScheme(
    primary = AddaPrimary,
    onPrimary = AddaOnPrimary,
    primaryContainer = AddaPrimaryDim,
    onPrimaryContainer = AddaSecondary,
    secondary = AddaSecondary,
    onSecondary = AddaOnPrimary,
    background = AddaBackground,
    onBackground = AddaText,
    surface = AddaSurface,
    onSurface = AddaText,
    surfaceVariant = AddaSurface,
    onSurfaceVariant = AddaMuted,
    outline = AddaOutline,
    outlineVariant = AddaOutline,
    error = AddaError,
    onError = AddaOnPrimary,
    tertiary = AddaSuccess,
)

@Composable
fun AddaTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AddaBackground.toArgb()
            window.navigationBarColor = AddaBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = AddaColorScheme, // always dark
        typography = AddaTypography,
        shapes = AddaShapes,
        content = content,
    )
}
