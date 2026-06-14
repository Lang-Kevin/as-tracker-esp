package com.kevin.armswing.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.kevin.armswing.R

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val spaceGroteskFont = GoogleFont("Space Grotesk")

val SpaceGroteskFamily = FontFamily(
    Font(googleFont = spaceGroteskFont, fontProvider = fontProvider, weight = FontWeight.Light),
    Font(googleFont = spaceGroteskFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = spaceGroteskFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = spaceGroteskFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = spaceGroteskFont, fontProvider = fontProvider, weight = FontWeight.Bold),
)

private val base = Typography()

val ArmSwingTypography = Typography(
    displayLarge   = base.displayLarge.copy(fontFamily   = SpaceGroteskFamily),
    displayMedium  = base.displayMedium.copy(fontFamily  = SpaceGroteskFamily),
    displaySmall   = base.displaySmall.copy(fontFamily   = SpaceGroteskFamily),
    headlineLarge  = base.headlineLarge.copy(fontFamily  = SpaceGroteskFamily),
    headlineMedium = base.headlineMedium.copy(fontFamily = SpaceGroteskFamily),
    headlineSmall  = base.headlineSmall.copy(fontFamily  = SpaceGroteskFamily),
    titleLarge     = base.titleLarge.copy(fontFamily     = SpaceGroteskFamily),
    titleMedium    = base.titleMedium.copy(fontFamily    = SpaceGroteskFamily),
    titleSmall     = base.titleSmall.copy(fontFamily     = SpaceGroteskFamily),
    bodyLarge      = base.bodyLarge.copy(fontFamily      = SpaceGroteskFamily),
    bodyMedium     = base.bodyMedium.copy(fontFamily     = SpaceGroteskFamily),
    bodySmall      = base.bodySmall.copy(fontFamily      = SpaceGroteskFamily),
    labelLarge     = base.labelLarge.copy(fontFamily     = SpaceGroteskFamily),
    labelMedium    = base.labelMedium.copy(fontFamily    = SpaceGroteskFamily),
    labelSmall     = base.labelSmall.copy(fontFamily     = SpaceGroteskFamily),
)
