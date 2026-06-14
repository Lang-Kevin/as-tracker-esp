package com.kevin.armswing.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ArmSwingColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = OnPrimary,
    secondary = SecondaryBlue,
    tertiary = TertiaryPink,
    error = ErrorRed,
    background = BackgroundDark,
    surface = SurfaceDark,
    onSurface = LightPurple,
)

@Composable
fun ArmSwingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArmSwingColorScheme,
        typography = ArmSwingTypography,
        content = content
    )
}
