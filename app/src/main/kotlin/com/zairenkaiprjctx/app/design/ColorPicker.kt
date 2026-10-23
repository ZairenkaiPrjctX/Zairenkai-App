// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.zairenkaiprjctx.app.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Hue (0–360), saturation and value (0–1) of a colour, and back. */
data class Hsv(val h: Float, val s: Float, val v: Float) {
    fun toColor(): Color = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

    companion object {
        fun of(c: Color): Hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }.let { Hsv(it[0], it[1], it[2]) }
    }
}

enum class PickerMode(val label: Int) { WHEEL(R.string.picker_wheel), PICKER(R.string.picker_square), SLIDERS(R.string.picker_sliders) }

/** Colours one tap away, in every picker mode. */
val QuickColors = listOf(
    Color.White, Color(0xFF9E9E9E), Color(0xFF424242), Color.Black, Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835),
    Color(0xFF43A047), Color(0xFF00897B), Color(0xFF039BE5), Color(0xFF3949AB), Color(0xFF8E24AA), Color(0xFFD81B60),
)

/**
 * A colour picker with three modes (a hue/saturation wheel with brightness, a saturation/value
 * square with a hue bar, and red/green/blue sliders), the hex value, and quick colours.
 */
@Composable
fun ColorPickerPanel(color: Color, onChange: (Color) -> Unit, modifier: Modifier = Modifier) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
        ConnectedChoice(PickerMode.entries.map { stringResource(it.label) }, mode, { mode = it })
        when (PickerMode.entries[mode]) {
            PickerMode.WHEEL -> ColorWheel(color, onChange)
            PickerMode.PICKER -> SaturationValueSquare(color, onChange)
            PickerMode.SLIDERS -> RgbSliders(color, onChange)
        }
        HexField(color, onChange)
        Text(stringResource(R.string.picker_quick), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            items(QuickColors) { c ->
                val hex = c.toHex()
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(if (c.toArgb() == color.toArgb()) 3.dp else 1.dp, MaterialTheme.colorScheme.outline), CircleShape)
                        .clickable { onChange(c) }
                        .semantics { contentDescription = hex },
                ) { Canvas(Modifier.size(40.dp)) { drawCircle(c) } }
            }
        }
    }
}

@Composable
private fun ColorWheel(color: Color, onChange: (Color) -> Unit) {
    val hsv = Hsv.of(color)
    val current by rememberUpdatedState(hsv)
    val change by rememberUpdatedState(onChange)
    Column(verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    fun pick(p: Offset) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = size.width / 2f
                        val d = p - c
                        val hue = ((Math.toDegrees(atan2(d.y, d.x).toDouble()).toFloat()) + 360f) % 360f
                        val sat = (hypot(d.x, d.y) / r).coerceIn(0f, 1f)
                        change(Hsv(hue, sat, current.v.coerceAtLeast(0.05f)).toColor())
                    }
                    detectTapGestures { pick(it) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { ch, _ ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val d = ch.position - c
                        val hue = ((Math.toDegrees(atan2(d.y, d.x).toDouble()).toFloat()) + 360f) % 360f
                        val sat = (hypot(d.x, d.y) / (size.width / 2f)).coerceIn(0f, 1f)
                        change(Hsv(hue, sat, current.v.coerceAtLeast(0.05f)).toColor())
                    }
                },
        ) {
            val r = size.minDimension / 2f
            val hues = (0..360 step 30).map { Hsv(it.toFloat(), 1f, hsv.v.coerceAtLeast(0.05f)).toColor() }
            drawCircle(Brush.sweepGradient(hues, center), r)
            drawCircle(Brush.radialGradient(listOf(Hsv(0f, 0f, hsv.v).toColor(), Color.Transparent), center, r), r)
            val a = Math.toRadians(hsv.h.toDouble())
            val thumb = Offset(center.x + (cos(a) * hsv.s * r).toFloat(), center.y + (sin(a) * hsv.s * r).toFloat())
            drawCircle(Color.White, 11.dp.toPx(), thumb, style = Stroke(3.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.4f), 12.5.dp.toPx(), thumb, style = Stroke(1.dp.toPx()))
        }
        LabeledSlider(stringResource(R.string.picker_brightness), hsv.v, "${(hsv.v * 100).roundToInt()} %") { change(hsv.copy(v = it).toColor()) }
    }
}

@Composable
private fun SaturationValueSquare(color: Color, onChange: (Color) -> Unit) {
    val hsv = Hsv.of(color)
    val current by rememberUpdatedState(hsv)
    val change by rememberUpdatedState(onChange)
    Row(Modifier.fillMaxWidth().aspectRatio(1.25f), horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    fun pick(p: Offset) = change(current.copy(s = (p.x / size.width).coerceIn(0f, 1f), v = 1f - (p.y / size.height).coerceIn(0f, 1f)).toColor())
                    detectTapGestures { pick(it) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { ch, _ -> change(current.copy(s = (ch.position.x / size.width).coerceIn(0f, 1f), v = 1f - (ch.position.y / size.height).coerceIn(0f, 1f)).toColor()) }
                },
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, Hsv(hsv.h, 1f, 1f).toColor())))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val thumb = Offset(hsv.s * size.width, (1f - hsv.v) * size.height)
            drawCircle(Color.White, 10.dp.toPx(), thumb, style = Stroke(3.dp.toPx()))
        }
        Canvas(
            Modifier
                .width(28.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTapGestures { change(current.copy(h = (it.y / size.height).coerceIn(0f, 1f) * 360f).toColor()) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { ch, _ -> change(current.copy(h = (ch.position.y / size.height).coerceIn(0f, 1f) * 360f).toColor()) }
                },
        ) {
            drawRect(Brush.verticalGradient((0..360 step 30).map { Hsv(it.toFloat(), 1f, 1f).toColor() }))
            val y = hsv.h / 360f * size.height
            drawCircle(Color.White, 10.dp.toPx(), Offset(size.width / 2f, y), style = Stroke(3.dp.toPx()))
        }
    }
}

@Composable
private fun RgbSliders(color: Color, onChange: (Color) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs)) {
        LabeledSlider(stringResource(R.string.picker_red), color.red, "${(color.red * 255).roundToInt()}") { onChange(color.copy(red = it)) }
        LabeledSlider(stringResource(R.string.picker_green), color.green, "${(color.green * 255).roundToInt()}") { onChange(color.copy(green = it)) }
        LabeledSlider(stringResource(R.string.picker_blue), color.blue, "${(color.blue * 255).roundToInt()}") { onChange(color.copy(blue = it)) }
    }
}

/** A slider with its name and current value above it. */
@Composable
fun LabeledSlider(label: String, value: Float, valueText: String, range: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueText, style = ZairenkaiTextStyles.numeric, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps, modifier = Modifier.semantics { contentDescription = label })
    }
}

/** The colour as `#RRGGBB`, editable; only a complete, valid value changes the colour. */
@Composable
private fun HexField(color: Color, onChange: (Color) -> Unit) {
    var text by rememberSaveable { mutableStateOf(color.toHex()) }
    LaunchedEffect(color) { if (parseHexColor(text)?.toArgb() != color.toArgb()) text = color.toHex() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
        Surface(color = color, shape = CircleShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.size(44.dp)) {}
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it.take(7)
                parseHexColor(text)?.let(onChange)
            },
            label = { Text(stringResource(R.string.picker_hex)) },
            singleLine = true,
            isError = parseHexColor(text) == null,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            shape = ZairenkaiShapes.metric,
            modifier = Modifier.weight(1f).padding(bottom = ZairenkaiSpacing.xs),
        )
    }
}
