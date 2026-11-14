package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val SynthWaveDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianBlack,
    primaryContainer = MidnightViolet,
    onPrimaryContainer = CrispWhite,
    secondary = CyberMagenta,
    onSecondary = ObsidianBlack,
    secondaryContainer = GlassSurfaceElevated,
    onSecondaryContainer = CrispWhite,
    tertiary = ElectricLime,
    onTertiary = ObsidianBlack,
    tertiaryContainer = DeepNavy,
    onTertiaryContainer = CrispWhite,
    background = ObsidianBlack,
    onBackground = CrispWhite,
    surface = GlassSurface,
    onSurface = CrispWhite,
    surfaceVariant = GlassSurfaceElevated,
    onSurfaceVariant = CoolSlate,
    outline = GlassBorder,
    outlineVariant = MutedSlate,
    error = ErrorNeon,
    onError = ObsidianBlack
)

@Composable
fun SynthWaveTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        SynthWaveDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SynthWaveTypography,
        content = content
    )
}
