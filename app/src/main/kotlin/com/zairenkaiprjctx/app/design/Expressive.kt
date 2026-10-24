// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon

/** Colours of a hero surface: a tinted gradient, its accent, and the content drawn on it. */
@Immutable
data class HeroColors(val start: Color, val end: Color, val accent: Color, val content: Color)

object HeroDefaults {
    @Composable
    fun primary(): HeroColors {
        val cs = MaterialTheme.colorScheme
        return HeroColors(cs.primaryContainer, cs.surfaceContainerHigh, cs.primary, cs.onSurface)
    }

    @Composable
    fun tertiary(): HeroColors {
        val cs = MaterialTheme.colorScheme
        return HeroColors(cs.tertiaryContainer, cs.surfaceContainerHigh, cs.tertiary, cs.onSurface)
    }

    @Composable
    fun secondary(): HeroColors {
        val cs = MaterialTheme.colorScheme
        return HeroColors(cs.secondaryContainer, cs.surfaceContainerHigh, cs.secondary, cs.onSurface)
    }

    /** A hero tinted by a semantic status tone (thermal state); the text still names the state. */
    @Composable
    fun status(tone: StatusTone): HeroColors =
        HeroColors(tone.container, MaterialTheme.colorScheme.surfaceContainerHigh, tone.content, MaterialTheme.colorScheme.onSurface)
}

/** Colours of a tile: its container, its content, and the badge behind its glyph. */
@Immutable
data class TileColors(val container: Color, val content: Color, val badge: Color, val onBadge: Color)

object TileDefaults {
    @Composable
    fun primary() = MaterialTheme.colorScheme.let { TileColors(it.primaryContainer, it.onPrimaryContainer, it.primary, it.onPrimary) }

    @Composable
    fun secondary() = MaterialTheme.colorScheme.let { TileColors(it.secondaryContainer, it.onSecondaryContainer, it.secondary, it.onSecondary) }

    @Composable
    fun tertiary() = MaterialTheme.colorScheme.let { TileColors(it.tertiaryContainer, it.onTertiaryContainer, it.tertiary, it.onTertiary) }

    @Composable
    fun neutral() = MaterialTheme.colorScheme.let { TileColors(it.surfaceContainerHigh, it.onSurface, it.surfaceContainerHighest, it.onSurfaceVariant) }
}

/** A glyph inside an expressive shape from the Material shapes library. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeBadge(
    icon: ImageVector,
    polygon: RoundedPolygon,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Surface(color = container, contentColor = content, shape = polygon.toShape(), modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.5f)) }
    }
}

/**
 * The most prominent surface of a screen: a tinted gradient with a large, faint expressive shape
 * behind the trailing visual, an overline, a headline that shrinks before it wraps, and one
 * supporting line. Colour is a backdrop here; every state is also written out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HeroPanel(
    overline: String,
    headline: String,
    supporting: String?,
    modifier: Modifier = Modifier,
    colors: HeroColors = HeroDefaults.primary(),
    decoration: RoundedPolygon = MaterialShapes.Cookie12Sided,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = colors.content,
        shape = ZairenkaiShapes.hero,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(listOf(colors.start, colors.end))),
            )
            // Decoration only: a faint shape that the trailing visual sits on.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 56.dp, y = (-48).dp)
                    .size(220.dp)
                    .rotate(18f)
                    .background(colors.accent.copy(alpha = 0.10f), decoration.toShape()),
            )
            Column(
                Modifier.padding(ZairenkaiSpacing.xl).animateContentSize(ZairenkaiMotion.spatial()),
                verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs)) {
                        Text(overline, style = MaterialTheme.typography.labelLarge, color = colors.accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        FitText(headline, MaterialTheme.typography.headlineMedium, minFontSize = 20.sp)
                        if (supporting != null) {
                            Text(
                                supporting,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    trailing?.invoke()
                }
                content?.invoke()
            }
        }
    }
}

/**
 * A tappable summary tile: a glyph in an expressive shape, a label and a value that shrinks before
 * it wraps. Used in a [TileGrid]; each opens the screen behind its value.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: TileColors = TileDefaults.neutral(),
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
) {
    Surface(onClick = onClick, color = colors.container, contentColor = colors.content, shape = ZairenkaiShapes.metric, modifier = modifier) {
        Column(Modifier.padding(ZairenkaiSpacing.l), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(icon, polygon, colors.badge, colors.onBadge)
                Spacer(Modifier.weight(1f))
                Icon(ZairenkaiGlyphs.chevron, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.content.copy(alpha = 0.6f))
            }
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = colors.content.copy(alpha = 0.78f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                FitText(value, MaterialTheme.typography.titleLarge, minFontSize = 14.sp)
            }
        }
    }
}

/**
 * [items] in rows of two, each row as tall as its tallest item (cards, tiles). When the text is
 * large for the width (a narrow screen, a large system font) the grid becomes one column, so a
 * label never has to fit a column narrower than about 150 dp of default-size text.
 */
@Composable
fun <T> TileGrid(items: List<T>, modifier: Modifier = Modifier, content: @Composable (T, Modifier) -> Unit) {
    BoxWithConstraints(modifier) {
        val fontScale = LocalDensity.current.fontScale
        val columns = if (maxWidth / fontScale >= 300.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
            items.chunked(columns).forEach { rowItems ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
                    rowItems.forEach { content(it, Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * One option of a single choice as a card: glyph, title, what it does. The chosen card turns to
 * the primary container, rounds further and shows a check mark, so the choice is not colour alone;
 * each card is announced as a radio button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChoiceCard(
    icon: ImageVector,
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (selected) 32.dp else 20.dp, ZairenkaiMotion.spatial(), label = "choice")
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        color = if (selected) cs.primaryContainer else cs.surfaceContainer,
        contentColor = if (selected) cs.onPrimaryContainer else cs.onSurface,
        border = if (selected) null else BorderStroke(1.dp, cs.outlineVariant),
        modifier = modifier.semantics { role = Role.RadioButton },
    ) {
        Column(Modifier.padding(ZairenkaiSpacing.l), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(
                    icon,
                    if (selected) MaterialShapes.Cookie9Sided else MaterialShapes.Circle,
                    if (selected) cs.primary else cs.surfaceContainerHighest,
                    if (selected) cs.onPrimary else cs.onSurfaceVariant,
                    size = 40.dp,
                )
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = cs.primary)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (tag != null) InfoChip(tag)
            Text(body, style = MaterialTheme.typography.bodySmall, color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A small one-line fact (Android version, SoC, a feature name); long values end in an ellipsis. */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    Surface(color = color, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shape = ZairenkaiShapes.pill, modifier = modifier.widthIn(max = 280.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One entrance per screen: items fade and rise in turn. Instant when animations are off. */
class Entrance internal constructor(internal val progress: Animatable<Float, AnimationVector1D>)

@Composable
fun rememberEntrance(): Entrance {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing)) }
    return remember(progress) { Entrance(progress) }
}

/** The [index]-th item of a screen under [entrance]; items after the sixth arrive with the sixth. */
fun Modifier.entrance(entrance: Entrance, index: Int): Modifier = graphicsLayer {
    val start = index.coerceIn(0, 6) * 0.08f
    val t = ((entrance.progress.value - start) / 0.52f).coerceIn(0f, 1f)
    alpha = t
    translationY = (1f - t) * 20.dp.toPx()
}
