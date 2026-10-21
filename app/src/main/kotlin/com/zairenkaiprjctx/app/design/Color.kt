// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Zairenkai brand colours: a deep teal-cyan primary, a warm amber tertiary for emphasis, and
 * cool neutral surfaces. Used when dynamic colour is off or unavailable (Android < 12).
 */
object ZairenkaiColorScheme {
    val light: ColorScheme = lightColorScheme(
        primary = Color(0xFF006782),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFBDE9FF),
        onPrimaryContainer = Color(0xFF001F29),
        secondary = Color(0xFF4C616B),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCFE6F1),
        onSecondaryContainer = Color(0xFF071E26),
        tertiary = Color(0xFF8A5100),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDCBE),
        onTertiaryContainer = Color(0xFF2C1600),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF6FAFD),
        onBackground = Color(0xFF171C1F),
        surface = Color(0xFFF6FAFD),
        onSurface = Color(0xFF171C1F),
        surfaceVariant = Color(0xFFDCE4E9),
        onSurfaceVariant = Color(0xFF40484C),
        outline = Color(0xFF70787D),
        outlineVariant = Color(0xFFC0C8CC),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF0F4F7),
        surfaceContainer = Color(0xFFEAEEF1),
        surfaceContainerHigh = Color(0xFFE4E9EC),
        surfaceContainerHighest = Color(0xFFDFE3E6),
    )

    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFF65D3FF),
        onPrimary = Color(0xFF003545),
        primaryContainer = Color(0xFF004D63),
        onPrimaryContainer = Color(0xFFBDE9FF),
        secondary = Color(0xFFB3CAD4),
        onSecondary = Color(0xFF1E333C),
        secondaryContainer = Color(0xFF354A53),
        onSecondaryContainer = Color(0xFFCFE6F1),
        tertiary = Color(0xFFFFB871),
        onTertiary = Color(0xFF4A2800),
        tertiaryContainer = Color(0xFF693C00),
        onTertiaryContainer = Color(0xFFFFDCBE),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0F1417),
        onBackground = Color(0xFFDFE3E6),
        surface = Color(0xFF0F1417),
        onSurface = Color(0xFFDFE3E6),
        surfaceVariant = Color(0xFF40484C),
        onSurfaceVariant = Color(0xFFC0C8CC),
        outline = Color(0xFF8A9297),
        outlineVariant = Color(0xFF40484C),
        surfaceContainerLowest = Color(0xFF0A0F12),
        surfaceContainerLow = Color(0xFF171C1F),
        surfaceContainer = Color(0xFF1B2023),
        surfaceContainerHigh = Color(0xFF252B2E),
        surfaceContainerHighest = Color(0xFF303539),
    )
}

/** One semantic role: a container colour and the content colour drawn on it. */
@Immutable
data class StatusTone(val container: Color, val content: Color)

/**
 * Semantic status colours, independent of dynamic colour so that "ready" and "failed" never
 * swap meaning with the wallpaper. Status is never shown by colour alone (see StatusBadge).
 */
@Immutable
data class ZairenkaiStatusColors(
    val success: StatusTone,
    val warning: StatusTone,
    val error: StatusTone,
    val info: StatusTone,
    val unavailable: StatusTone,
) {
    companion object {
        val light = ZairenkaiStatusColors(
            success = StatusTone(Color(0xFFC7EFCB), Color(0xFF0C3A15)),
            warning = StatusTone(Color(0xFFFFE08A), Color(0xFF3A2C00)),
            error = StatusTone(Color(0xFFFFDAD6), Color(0xFF5C0007)),
            info = StatusTone(Color(0xFFD6E3FF), Color(0xFF0B2A5C)),
            unavailable = StatusTone(Color(0xFFE1E3E5), Color(0xFF33383B)),
        )
        val dark = ZairenkaiStatusColors(
            success = StatusTone(Color(0xFF1D4F27), Color(0xFFB4F1BC)),
            warning = StatusTone(Color(0xFF574400), Color(0xFFFFE08A)),
            error = StatusTone(Color(0xFF7A1016), Color(0xFFFFDAD6)),
            info = StatusTone(Color(0xFF233F73), Color(0xFFD6E3FF)),
            unavailable = StatusTone(Color(0xFF3A4044), Color(0xFFD6DADD)),
        )
    }
}

val LocalStatusColors = staticCompositionLocalOf { ZairenkaiStatusColors.light }
