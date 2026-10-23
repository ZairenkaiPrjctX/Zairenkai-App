// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** The content a glass surface blurs (Haze, Apache-2.0). */
@Composable
fun rememberGlassState(): HazeState = remember { HazeState() }

/** Marks what lies behind glass surfaces. */
fun Modifier.glassSource(state: HazeState): Modifier = hazeSource(state)

/**
 * A frosted surface: the content behind it, blurred by [GlassSettings.frost] (up to 40 dp), tinted
 * with the surface colour at [GlassSettings.tint], with grain ([GlassSettings.noise]) and a light
 * rim whose strength is [GlassSettings.highlight]. On Android 11 and older Haze draws the tint
 * without blur. Off ([GlassSettings.enabled] false), nothing is added.
 */
fun Modifier.glassBar(state: HazeState, glass: GlassSettings, shape: Shape = RectangleShape): Modifier = composed {
    if (!glass.enabled) return@composed this
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val rim = MaterialTheme.colorScheme.onSurface
    val style = HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.surface,
        tint = HazeTint(surface.copy(alpha = 0.15f + glass.tint * 0.7f)),
        blurRadius = (glass.frost * 40f).dp,
        noiseFactor = glass.noise,
        fallbackTint = HazeTint(surface.copy(alpha = 0.92f)),
    )
    this
        .clip(shape)
        .hazeEffect(state, style)
        .border(
            1.dp,
            Brush.verticalGradient(listOf(rim.copy(alpha = 0.10f + glass.highlight * 0.35f), Color.Transparent, rim.copy(alpha = glass.highlight * 0.12f))),
            shape,
        )
}
