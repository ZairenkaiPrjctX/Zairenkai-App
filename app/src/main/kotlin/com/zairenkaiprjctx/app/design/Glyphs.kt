// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Line glyphs drawn for Zairenkai (original geometry, 24 dp grid, 1.8 dp strokes with round caps).
 * The Material core icon set has no gauge, thermometer, chip or spark, and the extended set
 * would add megabytes to the APK. Icons are tinted by [androidx.compose.material3.Icon] like any
 * other vector.
 */
object ZairenkaiGlyphs {
    private fun glyph(name: String, build: GlyphScope.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply { GlyphScope(this).build() }.build()

    class GlyphScope(private val b: ImageVector.Builder) {
        private val ink = SolidColor(Color.Black)

        fun stroke(width: Float = 1.8f, d: PathBuilder.() -> Unit) {
            b.path(stroke = ink, strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = d)
        }

        fun fill(d: PathBuilder.() -> Unit) {
            b.path(fill = ink, pathBuilder = d)
        }
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float) {
        moveTo(l + radius, t)
        lineTo(r - radius, t)
        arcTo(radius, radius, 0f, false, true, r, t + radius)
        lineTo(r, b - radius)
        arcTo(radius, radius, 0f, false, true, r - radius, b)
        lineTo(l + radius, b)
        arcTo(radius, radius, 0f, false, true, l, b - radius)
        lineTo(l, t + radius)
        arcTo(radius, radius, 0f, false, true, l + radius, t)
        close()
    }

    /** Four-pointed spark: Zairenkai AI. */
    val spark: ImageVector = glyph("spark") {
        fill {
            moveTo(11f, 3f)
            quadTo(11.9f, 10.1f, 19f, 11f)
            quadTo(11.9f, 11.9f, 11f, 19f)
            quadTo(10.1f, 11.9f, 3f, 11f)
            quadTo(10.1f, 10.1f, 11f, 3f)
            close()
            moveTo(18.5f, 15.5f)
            quadTo(18.9f, 18.1f, 21.5f, 18.5f)
            quadTo(18.9f, 18.9f, 18.5f, 21.5f)
            quadTo(18.1f, 18.9f, 15.5f, 18.5f)
            quadTo(18.1f, 18.1f, 18.5f, 15.5f)
            close()
        }
    }

    /** Speedometer: performance. */
    val gauge: ImageVector = glyph("gauge") {
        stroke {
            moveTo(4.6f, 17.5f)
            arcTo(8.2f, 8.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 19.4f, y1 = 17.5f)
        }
        stroke { moveTo(12f, 14.5f); lineTo(15.6f, 9.6f) }
        fill { circle(12f, 14.5f, 1.9f) }
    }

    /** Thermometer: thermal. */
    val thermometer: ImageVector = glyph("thermometer") {
        stroke {
            moveTo(9.8f, 13.6f)
            lineTo(9.8f, 5.2f)
            arcTo(2.2f, 2.2f, 0f, false, true, 14.2f, 5.2f)
            lineTo(14.2f, 13.6f)
            arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 9.8f, y1 = 13.6f)
            close()
        }
        stroke(2f) { moveTo(12f, 9f); lineTo(12f, 15.5f) }
        fill { circle(12f, 17.1f, 1.8f) }
    }

    /** Processor: ART runtime. */
    val chip: ImageVector = glyph("chip") {
        stroke { roundRect(6.5f, 6.5f, 17.5f, 17.5f, 2.2f) }
        fill { roundRect(9.6f, 9.6f, 14.4f, 14.4f, 1f) }
        stroke {
            listOf(10f, 14f).forEach { p ->
                moveTo(p, 3.5f); lineTo(p, 6.5f)
                moveTo(p, 17.5f); lineTo(p, 20.5f)
                moveTo(3.5f, p); lineTo(6.5f, p)
                moveTo(17.5f, p); lineTo(20.5f, p)
            }
        }
    }

    /** Four tiles: system. */
    val tiles: ImageVector = glyph("tiles") {
        stroke {
            roundRect(4f, 4f, 10.5f, 10.5f, 2f)
            roundRect(13.5f, 4f, 20f, 10.5f, 2f)
            roundRect(4f, 13.5f, 10.5f, 20f, 2f)
        }
        fill { circle(16.75f, 16.75f, 3.25f) }
    }

    /** Shield with a check: root access. */
    val shield: ImageVector = glyph("shield") {
        stroke {
            moveTo(12f, 3.2f)
            lineTo(19f, 6f)
            lineTo(19f, 11.4f)
            curveTo(19f, 15.8f, 16.1f, 19.3f, 12f, 20.8f)
            curveTo(7.9f, 19.3f, 5f, 15.8f, 5f, 11.4f)
            lineTo(5f, 6f)
            close()
        }
        stroke { moveTo(9.2f, 12f); lineTo(11.2f, 14f); lineTo(15f, 10.2f) }
    }

    /** Box: the system module. */
    val box: ImageVector = glyph("box") {
        stroke {
            moveTo(12f, 3.3f)
            lineTo(19.8f, 7.6f)
            lineTo(19.8f, 16.4f)
            lineTo(12f, 20.7f)
            lineTo(4.2f, 16.4f)
            lineTo(4.2f, 7.6f)
            close()
            moveTo(4.4f, 7.7f); lineTo(12f, 12f); lineTo(19.6f, 7.7f)
            moveTo(12f, 12f); lineTo(12f, 20.5f)
        }
    }

    /** Cylinder: the device database. */
    val database: ImageVector = glyph("database") {
        stroke {
            moveTo(5f, 6.2f)
            curveTo(5f, 4.6f, 8.1f, 3.4f, 12f, 3.4f)
            curveTo(15.9f, 3.4f, 19f, 4.6f, 19f, 6.2f)
            lineTo(19f, 17.8f)
            curveTo(19f, 19.4f, 15.9f, 20.6f, 12f, 20.6f)
            curveTo(8.1f, 20.6f, 5f, 19.4f, 5f, 17.8f)
            close()
            moveTo(5f, 6.2f)
            curveTo(5f, 7.8f, 8.1f, 9f, 12f, 9f)
            curveTo(15.9f, 9f, 19f, 7.8f, 19f, 6.2f)
            moveTo(5f, 12f)
            curveTo(5f, 13.6f, 8.1f, 14.8f, 12f, 14.8f)
            curveTo(15.9f, 14.8f, 19f, 13.6f, 19f, 12f)
        }
    }

    /** Heartbeat line: diagnostics. */
    val pulse: ImageVector = glyph("pulse") {
        stroke { moveTo(3f, 12.5f); lineTo(7f, 12.5f); lineTo(9.4f, 6.5f); lineTo(14f, 18f); lineTo(16.4f, 12.5f); lineTo(21f, 12.5f) }
    }

    /** Page with lines: licences. */
    val document: ImageVector = glyph("document") {
        stroke {
            moveTo(14f, 3.5f); lineTo(7f, 3.5f)
            arcTo(1.8f, 1.8f, 0f, false, false, 5.2f, 5.3f)
            lineTo(5.2f, 18.7f)
            arcTo(1.8f, 1.8f, 0f, false, false, 7f, 20.5f)
            lineTo(17f, 20.5f)
            arcTo(1.8f, 1.8f, 0f, false, false, 18.8f, 18.7f)
            lineTo(18.8f, 8.3f)
            close()
            moveTo(14f, 3.5f); lineTo(14f, 8.3f); lineTo(18.8f, 8.3f)
            moveTo(8.5f, 12.5f); lineTo(15.5f, 12.5f)
            moveTo(8.5f, 16f); lineTo(13f, 16f)
        }
    }

    /** Linked nodes: the ecosystem of repositories. */
    val nodes: ImageVector = glyph("nodes") {
        stroke {
            circle(12f, 6f, 2.4f)
            circle(6f, 17f, 2.4f)
            circle(18f, 17f, 2.4f)
            moveTo(10.9f, 8.1f); lineTo(7.1f, 14.9f)
            moveTo(13.1f, 8.1f); lineTo(16.9f, 14.9f)
            moveTo(8.4f, 17f); lineTo(15.6f, 17f)
        }
    }

    /** Sliders: profiles and tuning. */
    val sliders: ImageVector = glyph("sliders") {
        stroke {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(4f, 12f); lineTo(20f, 12f)
            moveTo(4f, 17f); lineTo(20f, 17f)
        }
        fill {
            circle(9f, 7f, 2.3f)
            circle(15.5f, 12f, 2.3f)
            circle(7.5f, 17f, 2.3f)
        }
    }

    /** Battery. */
    val battery: ImageVector = glyph("battery") {
        stroke { roundRect(7f, 5f, 17f, 21f, 2.2f) }
        fill { roundRect(10f, 2.8f, 14f, 5f, 0.8f) }
        fill { roundRect(9.4f, 12.5f, 14.6f, 18.6f, 0.8f) }
    }

    /** Device outline: this phone. */
    val phone: ImageVector = glyph("phone") {
        stroke { roundRect(6.5f, 2.8f, 17.5f, 21.2f, 2.6f) }
        stroke(2.2f) { moveTo(10.8f, 18f); lineTo(13.2f, 18f) }
    }

    /** Padlock: privacy. */
    val lock: ImageVector = glyph("lock") {
        stroke { roundRect(5.5f, 10.5f, 18.5f, 20.5f, 2.4f) }
        stroke { moveTo(8.5f, 10.5f); lineTo(8.5f, 7.8f); arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 7.8f); lineTo(15.5f, 10.5f) }
        fill { circle(12f, 15.5f, 1.5f) }
    }

    /** Hand: the user approves. */
    val approve: ImageVector = glyph("approve") {
        stroke { circle(12f, 12f, 8.5f) }
        stroke { moveTo(8.3f, 12.2f); lineTo(10.9f, 14.8f); lineTo(15.9f, 9.6f) }
    }

    /** Wrench in a square: typed tool calls. */
    val tool: ImageVector = glyph("tool") {
        stroke {
            moveTo(14.6f, 4.3f)
            arcTo(4.4f, 4.4f, 0f, false, false, 9.9f, 10.1f)
            lineTo(4.4f, 15.6f)
            arcTo(1.9f, 1.9f, 0f, false, false, 7.1f, 18.3f)
            lineTo(12.6f, 12.8f)
            arcTo(4.4f, 4.4f, 0f, false, false, 18.4f, 8.1f)
            lineTo(15.8f, 10.7f)
            lineTo(13.3f, 10.3f)
            lineTo(12.9f, 7.8f)
            close()
        }
    }

    /** House: overview. */
    val home: ImageVector = glyph("home") {
        stroke {
            moveTo(4.5f, 10.4f)
            lineTo(12f, 4.2f)
            lineTo(19.5f, 10.4f)
            lineTo(19.5f, 18.5f)
            arcTo(1.8f, 1.8f, 0f, false, true, 17.7f, 20.3f)
            lineTo(6.3f, 20.3f)
            arcTo(1.8f, 1.8f, 0f, false, true, 4.5f, 18.5f)
            close()
            moveTo(10f, 20.3f); lineTo(10f, 15f); lineTo(14f, 15f); lineTo(14f, 20.3f)
        }
    }

    /** Arrow pointing right, for rows that open a page. */
    val chevron: ImageVector = glyph("chevron") {
        stroke(2f) { moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f) }
    }
}
