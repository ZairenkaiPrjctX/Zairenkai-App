// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph

/** Flat wave when animations are off: the value is still shown, without movement. */
@Composable
private fun amplitude(): (Float) -> Float =
    if (LocalReducedMotion.current) { _ -> 0f } else WavyProgressIndicatorDefaults.indicatorAmplitude

/**
 * A measured fraction drawn as Material 3 Expressive's circular wavy indicator, with the value
 * in the centre. Only real ratios use it (capabilities ready, frame rate against target, thermal
 * headroom); [label] is what a screen reader announces.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RingGauge(
    progress: Float?,
    value: String,
    caption: String?,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(modifier.size(size).semantics(mergeDescendants = true) { contentDescription = label }, contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { (progress ?: 0f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = color,
            amplitude = amplitude(),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = ZairenkaiTextStyles.ringValue, textAlign = TextAlign.Center)
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

/** A thin determinate wavy bar for a measured fraction in a row (battery, load, memory). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Meter(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    LinearWavyProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth(),
        color = color,
        amplitude = amplitude(),
    )
}

/**
 * Indeterminate wavy bar for work that is really running (a registry refresh, a module step).
 * Nothing is drawn when animations are off; the caller keeps a text label for that case.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WorkingBar(modifier: Modifier = Modifier) {
    if (!LocalReducedMotion.current) LinearWavyProgressIndicator(modifier.fillMaxWidth())
}

/**
 * The Zairenkai AI mark: a soft cookie that slowly morphs into a sunburst and back while
 * [active], filled with the primary-to-tertiary gradient. Static when animations are off.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AiOrb(modifier: Modifier = Modifier, size: Dp = 96.dp, active: Boolean = true) {
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    val animate = active && !LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "ai-orb")
    val progress = if (animate) {
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "morph").value
    } else {
        0f
    }
    val turn = if (animate) {
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "turn").value
    } else {
        0f
    }
    val brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer))
    Canvas(modifier.size(size)) {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { scale(this@Canvas.size.width, this@Canvas.size.height) })
        rotate(turn) { drawPath(path, brush) }
    }
}
