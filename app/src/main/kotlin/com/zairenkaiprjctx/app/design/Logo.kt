// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * The Zairenkai mark, in the geometry of the launcher icon (res/drawable/ic_launcher_foreground):
 * a Z whose diagonal is a wave, and a small spark for the assistant. Coordinates are on the
 * adaptive-icon grid (108 units, the visible part from 18 to 90).
 */
object ZairenkaiMark {
    val strokes: List<Path> = listOf("M36,40 L68,40 C60,46 62,53 54,55 C46,57 48,64 40,70 L72,70")
        .map { PathParser().parsePathString(it).toPath() }
    val spark: Path = PathParser().parsePathString("M71,27 Q71.6,30.4 75,31 Q71.6,31.6 71,35 Q70.4,31.6 67,31 Q70.4,30.4 71,27 Z").toPath()
    const val STROKE = 7.5f

    /** The default icon's background: deep teal into midnight. */
    val background = Brush.linearGradient(listOf(Color(0xFF0E5568), Color(0xFF0B2433), Color(0xFF0A1218)), Offset.Zero, Offset.Infinite)
}

/**
 * The mark as a launcher shows it: [background] in [shape], the Z in [foreground] and the spark in
 * [spark]. [label], when set, is what a screen reader announces.
 */
@Composable
fun ZairenkaiLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    background: Brush = ZairenkaiMark.background,
    foreground: Color = Color.White,
    spark: Color = Color(0xFF65D3FF),
    shape: Shape? = null,
    label: String? = null,
) {
    Canvas(modifier.size(size).then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)) {
        val outline = (shape ?: androidx.compose.foundation.shape.RoundedCornerShape(percent = 28)).createOutline(this.size, layoutDirection, this)
        val clip = Path().apply {
            when (outline) {
                is Outline.Generic -> addPath(outline.path)
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Rectangle -> addRect(outline.rect)
            }
        }
        clipPath(clip) {
            drawOutline(outline, background)
            // Visible part of the 108-unit grid: 18..90.
            val k = this.size.width / 72f
            withTransform({
                scale(k, k, Offset.Zero)
                translate(-18f, -18f)
            }) {
                ZairenkaiMark.strokes.forEach { drawPath(it, foreground, style = Stroke(width = ZairenkaiMark.STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                drawPath(ZairenkaiMark.spark, spark)
            }
        }
    }
}
