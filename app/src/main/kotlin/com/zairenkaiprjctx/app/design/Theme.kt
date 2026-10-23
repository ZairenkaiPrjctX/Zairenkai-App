// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Immutable
data class ThemeSettings(val mode: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = true)

/** Spacing scale (dp): every padding and gap in the app comes from here. */
object ZairenkaiSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val gutter = 16.dp
}

/**
 * Shapes. The Material scale is rounder than the baseline, and there are role shapes so that
 * emphasis follows importance: heroes are the roundest, lists are segmented groups, and dialogs of
 * destructive actions are squarer than ordinary sheets.
 */
object ZairenkaiShapes {
    val material = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )
    val hero = RoundedCornerShape(32.dp)
    val metric = RoundedCornerShape(24.dp)
    val section = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(50)
    val destructive = RoundedCornerShape(16.dp)

    /** Segmented groups: large outer corners, small inner ones, 2 dp between rows. */
    val segmentOuter = 24.dp
    val segmentInner = 6.dp
    val segmentGap = 2.dp

    /** Chat bubbles: round, with the corner nearest the speaker tightened. */
    val bubbleUser = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp)
    val bubbleAssistant = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp)
}

/** True when the user turned animations off (Settings › Accessibility › Remove animations). */
val LocalReducedMotion = staticCompositionLocalOf { false }

object ZairenkaiMotion {
    /** Movement (size, position), from the theme's expressive motion scheme; instant if reduced. */
    @Composable
    fun <T> spatial(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()

    /** Colour and opacity changes; instant if reduced. */
    @Composable
    fun <T> effects(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()

    @Composable
    fun <T> fastEffects(): FiniteAnimationSpec<T> =
        if (LocalReducedMotion.current) snap() else MaterialTheme.motionScheme.fastEffectsSpec()
}

@Composable
private fun systemAnimationsOff(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * The Zairenkai theme: Material 3 Expressive (MaterialExpressiveTheme, which also supplies the
 * expressive motion scheme) with the brand or dynamic colour scheme, Google Sans Flex typography,
 * the Zairenkai shapes, semantic status colours and the reduced-motion flag.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ZairenkaiTheme(settings: ThemeSettings = ThemeSettings(), content: @Composable () -> Unit) {
    val dark = isDark(settings)
    val context = LocalContext.current
    val colors = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> ZairenkaiColorScheme.dark
        else -> ZairenkaiColorScheme.light
    }
    CompositionLocalProvider(
        LocalStatusColors provides if (dark) ZairenkaiStatusColors.dark else ZairenkaiStatusColors.light,
        LocalReducedMotion provides systemAnimationsOff(),
    ) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            shapes = ZairenkaiShapes.material,
            typography = ZairenkaiTypography,
            content = content,
        )
    }
}

@Composable
fun isDark(settings: ThemeSettings): Boolean = when (settings.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
