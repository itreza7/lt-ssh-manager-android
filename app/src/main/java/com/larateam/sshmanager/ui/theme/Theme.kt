package com.larateam.sshmanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Cyan,
    onPrimary = OnCyan,
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = Amber,
    onSecondary = OnAmber,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = SkyTertiary,
    onTertiary = OnSkyTertiary,
    background = InkBackground,
    onBackground = OnInk,
    surface = InkSurface,
    onSurface = OnInk,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = OnInkVariant,
    surfaceContainerLowest = InkBackground,
    surfaceContainerLow = InkSurface,
    surfaceContainer = InkContainer,
    surfaceContainerHigh = InkContainerHigh,
    surfaceContainerHighest = InkContainerHighest,
    surfaceTint = Cyan,
    outline = InkOutline,
    outlineVariant = InkOutlineVariant,
    error = Rose,
    onError = OnRose,
    errorContainer = RoseContainer,
    onErrorContainer = OnRoseContainer,
)

private val LightColors = lightColorScheme(
    primary = DeepTeal,
    onPrimary = OnDeepTeal,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainer,
    secondary = DeepAmber,
    onSecondary = OnDeepAmber,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = OnAmberContainerLight,
    tertiary = SkyTertiaryLight,
    onTertiary = OnSkyTertiaryLight,
    background = PaperBackground,
    onBackground = OnPaper,
    surface = PaperSurface,
    onSurface = OnPaper,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = OnPaperVariant,
    surfaceContainerLowest = PaperSurface,
    surfaceContainerLow = PaperBackground,
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = PaperContainerHigh,
    surfaceContainerHighest = PaperContainerHighest,
    surfaceTint = DeepTeal,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant,
    error = RoseLight,
    onError = OnRoseLight,
    errorContainer = RoseContainerLight,
    onErrorContainer = OnRoseContainerLight,
)

@Composable
fun SshManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // The app has its own identity; wallpaper-derived Material You is opt-in only and off by
    // default so the palette is consistent on every device.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalBrand provides if (darkTheme) DarkBrand else LightBrand) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
