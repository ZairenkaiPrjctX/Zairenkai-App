// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The built-in wallpapers: gradients drawn at any size, so no image files ship with the app. */
fun DrawScope.drawBuiltInWallpaper(source: WallpaperSource) {
    val w = size.width
    val h = size.height
    when (source) {
        WallpaperSource.AURORA -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF041523), Color(0xFF07263A), Color(0xFF0B3B46))))
            drawRect(Brush.radialGradient(listOf(Color(0xAA2BE0A0), Color.Transparent), Offset(w * 0.25f, h * 0.35f), w * 0.8f))
            drawRect(Brush.radialGradient(listOf(Color(0x8836C5F0), Color.Transparent), Offset(w * 0.8f, h * 0.2f), w * 0.7f))
            drawRect(Brush.radialGradient(listOf(Color(0x66A061F0), Color.Transparent), Offset(w * 0.6f, h * 0.55f), w * 0.6f))
        }
        WallpaperSource.SUNSET -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2B1055), Color(0xFF7B2F8E), Color(0xFFE0576B), Color(0xFFF8A65B))))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD27A), Color(0x00FFD27A)), Offset(w * 0.5f, h * 0.72f), w * 0.45f), w * 0.45f, Offset(w * 0.5f, h * 0.72f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC2A0E3A)), startY = h * 0.78f, endY = h))
        }
        WallpaperSource.OCEAN -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF8FE3F0), Color(0xFF1B9BB5), Color(0xFF0B4F6C), Color(0xFF062B3D))))
            for (i in 0 until 7) {
                val x = w * (0.1f + i * 0.14f)
                drawLine(Color(0x22FFFFFF), Offset(x, 0f), Offset(x - w * 0.18f, h), strokeWidth = w * 0.03f)
            }
        }
        WallpaperSource.NEBULA -> {
            drawRect(Color(0xFF0A0614))
            drawRect(Brush.radialGradient(listOf(Color(0xAAE0409A), Color.Transparent), Offset(w * 0.3f, h * 0.3f), w * 0.7f))
            drawRect(Brush.radialGradient(listOf(Color(0x995560F0), Color.Transparent), Offset(w * 0.75f, h * 0.6f), w * 0.8f))
            val r = kotlin.random.Random(7)
            repeat(90) { drawCircle(Color.White.copy(alpha = 0.25f + r.nextFloat() * 0.6f), 0.6f + r.nextFloat() * 1.6f, Offset(r.nextFloat() * w, r.nextFloat() * h)) }
        }
        WallpaperSource.NONE, WallpaperSource.PHOTO -> Unit
    }
}

/**
 * Everything behind the screens: the wallpaper (with the app background laid over it at the chosen
 * dim, so text keeps its contrast) and the background animation. Draws nothing when both are off.
 */
@Composable
fun AppBackdrop(settings: ThemeSettings, photo: ImageBitmap?, modifier: Modifier = Modifier) {
    val wallpaper = settings.wallpaper
    val bg = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(bg)) {
        when {
            wallpaper.source == WallpaperSource.PHOTO && photo != null -> Image(
                photo,
                contentDescription = null,
                contentScale = if (wallpaper.fill) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            wallpaper.source != WallpaperSource.NONE && wallpaper.source != WallpaperSource.PHOTO ->
                Canvas(Modifier.fillMaxSize()) { drawBuiltInWallpaper(wallpaper.source) }
        }
        if (wallpaper.source != WallpaperSource.NONE) {
            Box(Modifier.fillMaxSize().background(bg.copy(alpha = wallpaper.dim.coerceIn(0f, 0.95f))))
        }
        if (settings.animation != BackgroundAnimation.NONE) BackgroundMotion(settings.animation, MaterialTheme.colorScheme.primary)
    }
}

/** True when something is drawn behind the screens, so bars and screens keep their background clear. */
val ThemeSettings.hasBackdrop: Boolean get() = wallpaper.source != WallpaperSource.NONE || animation != BackgroundAnimation.NONE

/** The background animation: faint, slow, and frozen on its first frame when animations are off. */
@Composable
fun BackgroundMotion(kind: BackgroundAnimation, color: Color, modifier: Modifier = Modifier) {
    val still = LocalReducedMotion.current
    val t = if (still) 0.25f else {
        rememberInfiniteTransition(label = "backdrop").animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "t").value
    }
    Canvas(modifier.fillMaxSize()) {
        when (kind) {
            BackgroundAnimation.RINGS -> {
                val c = Offset(size.width * 0.5f, size.height * 0.42f)
                val max = hypot(size.width, size.height) * 0.6f
                for (i in 0 until 5) {
                    val p = (t + i / 5f) % 1f
                    drawCircle(color.copy(alpha = 0.16f * (1f - p)), radius = max * p, center = c, style = Stroke(width = 2.dp.toPx()))
                }
            }
            BackgroundAnimation.WAVES -> {
                for (band in 0 until 4) {
                    val path = Path()
                    val y0 = size.height * (0.35f + band * 0.14f)
                    val amp = 18.dp.toPx() + band * 6.dp.toPx()
                    path.moveTo(0f, y0)
                    var x = 0f
                    while (x <= size.width) {
                        val phase = (x / size.width) * 2f * PI.toFloat() * 1.3f + t * 2f * PI.toFloat() + band
                        path.lineTo(x, y0 + sin(phase) * amp)
                        x += 8f
                    }
                    path.lineTo(size.width, size.height)
                    path.lineTo(0f, size.height)
                    path.close()
                    drawPath(path, color.copy(alpha = 0.035f + band * 0.01f))
                }
            }
            BackgroundAnimation.ORBS -> {
                for (i in 0 until 4) {
                    val a = 2f * PI.toFloat() * (t + i / 4f)
                    val c = Offset(size.width * (0.5f + 0.32f * cos(a + i)), size.height * (0.45f + 0.28f * sin(a * 0.8f + i)))
                    val r = size.minDimension * (0.28f + 0.06f * i)
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.14f), Color.Transparent), c, r), r, c)
                }
            }
            BackgroundAnimation.NONE -> Unit
        }
    }
}
