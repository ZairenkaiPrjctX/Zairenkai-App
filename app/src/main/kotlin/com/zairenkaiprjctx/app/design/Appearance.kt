// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.zairenkaiprjctx.app.R

/**
 * A colour palette for the app. [ZAIRENKAI] is the hand-tuned brand scheme; the others are generated
 * from their seed by [schemeFromSeed]. "Dynamic" (wallpaper colours) is not a palette here: it is
 * [ThemeSettings.dynamicColor], because it needs Android 12 and has no fixed seed.
 */
enum class Palette(@StringRes val label: Int, val seed: Long) {
    ZAIRENKAI(R.string.palette_zairenkai, 0xFF006782),
    OCEAN(R.string.palette_ocean, 0xFF1565C0),
    FOREST(R.string.palette_forest, 0xFF2E7D32),
    MINT(R.string.palette_mint, 0xFF00897B),
    LAVENDER(R.string.palette_lavender, 0xFF6750A4),
    ROSE(R.string.palette_rose, 0xFFB0416A),
    CRIMSON(R.string.palette_crimson, 0xFFC62828),
    SUNSET(R.string.palette_sunset, 0xFFE65100),
    AMBER(R.string.palette_amber, 0xFFB58100),
    MIDNIGHT(R.string.palette_midnight, 0xFF283593),
    SLATE(R.string.palette_slate, 0xFF546E7A),
    MONOCHROME(R.string.palette_monochrome, 0xFF616161),
}

/** A palette the user made: a name and the seed colour the scheme is generated from. */
@Immutable
data class CustomTheme(val id: String, val name: String, val seed: Int)

/** A slow decorative layer behind the screens. Drawn faintly, and still when animations are off. */
enum class BackgroundAnimation(@StringRes val label: Int) {
    NONE(R.string.anim_none),
    RINGS(R.string.anim_rings),
    WAVES(R.string.anim_waves),
    ORBS(R.string.anim_orbs),
}

/** What is drawn behind the screens: nothing, a built-in gradient, or a photo the user picked. */
enum class WallpaperSource(@StringRes val label: Int) {
    NONE(R.string.wallpaper_none),
    AURORA(R.string.wallpaper_aurora),
    SUNSET(R.string.wallpaper_sunset),
    OCEAN(R.string.wallpaper_ocean),
    NEBULA(R.string.wallpaper_nebula),
    PHOTO(R.string.wallpaper_photo),
}

/**
 * Wallpaper settings. [fill] crops the image to the screen (otherwise it fits inside it), and
 * [dim] is how strongly the app's background colour covers it, so text stays readable.
 */
@Immutable
data class WallpaperSettings(val source: WallpaperSource = WallpaperSource.NONE, val fill: Boolean = true, val dim: Float = 0.55f)

/** The app's typeface: the bundled Google Sans Flex, the system font, or a Google Font. */
enum class FontSource { BUILT_IN, SYSTEM, GOOGLE }

@Immutable
data class FontSettings(val source: FontSource = FontSource.BUILT_IN, val googleFamily: String? = null)

/**
 * The glass navigation bar (experimental): the content behind the bar is blurred ([frost]), tinted
 * ([tint]) and edged with a light rim ([highlight]); [noise] adds grain against banding.
 */
@Immutable
data class GlassSettings(
    val enabled: Boolean = false,
    val topBar: Boolean = false,
    val frost: Float = 0.6f,
    val tint: Float = 0.6f,
    val highlight: Float = 0.6f,
    val noise: Float = 0.12f,
)

private fun hsl(h: Float, s: Float, l: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

/** Relative luminance Y (0–1) for a CIE lightness L* (0–100). */
private fun luminanceOf(lstar: Float): Double {
    val f = (lstar + 16.0) / 116.0
    return if (lstar > 8f) f * f * f else lstar / 903.3
}

/**
 * The colour of hue [h] and saturation [s] whose perceived lightness is [tone] (L*, 0–100). Tones
 * are found by luminance, not by HSL lightness, so a yellow and a blue of the same tone have the
 * same contrast against white or black, as in Material's tonal palettes.
 */
internal fun tone(h: Float, s: Float, tone: Float): Color {
    if (tone >= 100f) return Color.White
    if (tone <= 0f) return Color.Black
    val target = luminanceOf(tone)
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val mid = (lo + hi) / 2f
        if (ColorUtils.calculateLuminance(hsl(h, s, mid).toArgb()) < target) lo = mid else hi = mid
    }
    return hsl(h, s, (lo + hi) / 2f)
}

/**
 * A full Material colour scheme from one seed colour, in light or dark. Each role takes the tone
 * Material assigns it (primary 40 in light, 80 in dark, containers 90 and 30, surfaces 98 and 6,
 * ...): the primary keeps the seed's hue, the secondary is a muted version of it, the tertiary is
 * the hue turned by 60°, and neutral surfaces carry a trace of the hue. With [amoled] the dark
 * background and surfaces are pure black.
 */
fun schemeFromSeed(seed: Color, dark: Boolean, amoled: Boolean = false): ColorScheme {
    val out = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), out)
    val h = out[0]
    // Greys stay grey; colours are kept vivid enough to read as the chosen colour.
    val s = if (out[1] < 0.08f) 0f else out[1].coerceIn(0.4f, 0.9f)
    val sec = s * 0.3f
    val ter = h + 60f
    val ns = s * 0.12f
    val nv = s * 0.24f
    val scheme = if (!dark) {
        lightColorScheme(
            primary = tone(h, s, 40f), onPrimary = Color.White,
            primaryContainer = tone(h, s, 90f), onPrimaryContainer = tone(h, s, 10f),
            secondary = tone(h, sec, 40f), onSecondary = Color.White,
            secondaryContainer = tone(h, sec, 90f), onSecondaryContainer = tone(h, sec, 10f),
            tertiary = tone(ter, s * 0.6f, 40f), onTertiary = Color.White,
            tertiaryContainer = tone(ter, s * 0.6f, 90f), onTertiaryContainer = tone(ter, s * 0.6f, 10f),
            background = tone(h, ns, 98f), onBackground = tone(h, ns, 10f),
            surface = tone(h, ns, 98f), onSurface = tone(h, ns, 10f),
            surfaceVariant = tone(h, nv, 90f), onSurfaceVariant = tone(h, nv, 30f),
            outline = tone(h, nv, 50f), outlineVariant = tone(h, nv, 80f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(h, ns, 96f),
            surfaceContainer = tone(h, ns, 94f),
            surfaceContainerHigh = tone(h, ns, 92f),
            surfaceContainerHighest = tone(h, ns, 90f),
        )
    } else {
        darkColorScheme(
            primary = tone(h, s, 80f), onPrimary = tone(h, s, 20f),
            primaryContainer = tone(h, s, 30f), onPrimaryContainer = tone(h, s, 90f),
            secondary = tone(h, sec, 80f), onSecondary = tone(h, sec, 20f),
            secondaryContainer = tone(h, sec, 30f), onSecondaryContainer = tone(h, sec, 90f),
            tertiary = tone(ter, s * 0.6f, 80f), onTertiary = tone(ter, s * 0.6f, 20f),
            tertiaryContainer = tone(ter, s * 0.6f, 30f), onTertiaryContainer = tone(ter, s * 0.6f, 90f),
            background = tone(h, ns, 6f), onBackground = tone(h, ns, 90f),
            surface = tone(h, ns, 6f), onSurface = tone(h, ns, 90f),
            surfaceVariant = tone(h, nv, 30f), onSurfaceVariant = tone(h, nv, 80f),
            outline = tone(h, nv, 60f), outlineVariant = tone(h, nv, 30f),
            surfaceContainerLowest = tone(h, ns, 4f),
            surfaceContainerLow = tone(h, ns, 10f),
            surfaceContainer = tone(h, ns, 12f),
            surfaceContainerHigh = tone(h, ns, 17f),
            surfaceContainerHighest = tone(h, ns, 22f),
        )
    }
    return if (dark && amoled) scheme.amoled() else scheme
}

/** Pure black background and darker containers for OLED screens, keeping the roles' order. */
fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0C),
    surfaceContainer = Color(0xFF121214),
    surfaceContainerHigh = Color(0xFF19191C),
    surfaceContainerHighest = Color(0xFF212125),
    surfaceVariant = Color(0xFF1C1C20),
)

/** Accepts `#RRGGBB` or `RRGGBB` (any case); anything else is null. */
fun parseHexColor(text: String): Color? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it !in "0123456789abcdefABCDEF" }) return null
    return Color(0xFF000000 or hex.toLong(16))
}

fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
