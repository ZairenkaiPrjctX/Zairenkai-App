// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zairenkaiprjctx.app.R

/**
 * Google Sans Flex (OFL-1.1), bundled as res/font/google_sans_flex.ttf: one variable font with
 * wght 1–1000, opsz 1–144, wdth, slnt and ROND axes (integration/third-party-assets.json).
 *
 * Each weight is an instance of the same file with `wght` set; `opsz` follows the role so large
 * display text uses the display optical size and small labels the small one. If the font cannot be
 * loaded, Compose falls back to the system sans-serif, and missing glyphs fall back per character
 * through the platform font chain.
 */
@OptIn(ExperimentalTextApi::class)
private fun googleSansFlex(opticalSize: Float): FontFamily = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { w ->
        Font(
            resId = R.font.google_sans_flex,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(w),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    },
)

object ZairenkaiFonts {
    /** Body and UI text (the font's default optical size). */
    val text: FontFamily = googleSansFlex(18f)

    /** Display and headline text. */
    val display: FontFamily = googleSansFlex(48f)

    /** Labels and captions. */
    val small: FontFamily = googleSansFlex(12f)
}

/** Material 3 type scale set entirely in Google Sans Flex. */
val ZairenkaiTypography: Typography = typography(ZairenkaiFonts.display, ZairenkaiFonts.text, ZairenkaiFonts.small)

/**
 * The type scale for the user's font choice: Google Sans Flex (bundled), the system font, or a
 * Google Font fetched by Google Play services' font provider (the app itself has no network
 * access; until the font arrives, or without Play services, text falls back to the system font).
 */
@Composable
fun rememberTypography(font: FontSettings): Typography = remember(font) {
    when (font.source) {
        FontSource.BUILT_IN -> ZairenkaiTypography
        FontSource.SYSTEM -> typography(FontFamily.Default, FontFamily.Default, FontFamily.Default)
        FontSource.GOOGLE -> font.googleFamily?.let { name ->
            val family = googleFontFamily(name)
            typography(family, family, family)
        } ?: ZairenkaiTypography
    }
}

private val googleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

/** A Google Font family in the weights the type scale uses. */
fun googleFontFamily(name: String): FontFamily = FontFamily(
    listOf(FontWeight(400), FontWeight(500), FontWeight(600), FontWeight(700)).map { w ->
        androidx.compose.ui.text.googlefonts.Font(googleFont = GoogleFont(name), fontProvider = googleFontsProvider, weight = w)
    },
)

private fun typography(display: FontFamily, text: FontFamily, small: FontFamily): Typography = Typography(
    displayLarge = TextStyle(fontFamily = display, fontWeight = FontWeight(500), fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.02).em),
    displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight(500), fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight(500), fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = display, fontWeight = FontWeight(600), fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight(600), fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight(600), fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = text, fontWeight = FontWeight(600), fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = text, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.01.em),
    titleSmall = TextStyle(fontFamily = text, fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),
    bodyLarge = TextStyle(fontFamily = text, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = text, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = small, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = small, fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(fontFamily = small, fontWeight = FontWeight(600), fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.03.em),
    labelSmall = TextStyle(fontFamily = small, fontWeight = FontWeight(600), fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.03.em),
)

/** Roles beyond the Material scale, for technical data. */
object ZairenkaiTextStyles {
    /** Large primary values (FPS, thermal state): tabular figures so digits do not jump. */
    val metric = TextStyle(fontFamily = ZairenkaiFonts.display, fontWeight = FontWeight(700), fontSize = 34.sp, lineHeight = 40.sp, fontFeatureSettings = "tnum")

    /** The value inside a ring gauge. */
    val ringValue = TextStyle(fontFamily = ZairenkaiFonts.display, fontWeight = FontWeight(600), fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = "tnum")

    /** Inline numbers in rows (versions, sizes, percentages). */
    val numeric = TextStyle(fontFamily = ZairenkaiFonts.text, fontWeight = FontWeight(500), fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum")

    /** Paths, property names and commits: monospace, so similar characters stay distinct. */
    val code = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, lineHeight = 18.sp)
}
