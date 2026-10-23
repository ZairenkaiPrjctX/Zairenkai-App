// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.registry.CapabilityRecord

/** Icons used across the app: Zairenkai line glyphs, plus Material core icons for actions. */
object ZairenkaiIcons {
    val home = ZairenkaiGlyphs.home
    val performance = ZairenkaiGlyphs.gauge
    val thermal = ZairenkaiGlyphs.thermometer
    val runtime = ZairenkaiGlyphs.chip
    val system = ZairenkaiGlyphs.tiles
    val ai = ZairenkaiGlyphs.spark
    val settings = Icons.Filled.Settings
    val refresh = Icons.Filled.Refresh
    val expand = Icons.Filled.KeyboardArrowDown
    val close = Icons.Filled.Close
}

/** How a capability state is presented: label, icon and semantic tone. Never colour alone. */
data class StatusVisual(@StringRes val label: Int, val icon: ImageVector, val tone: (ZairenkaiStatusColors) -> StatusTone)

/**
 * Expressive container shape per tone (Material shapes library): a soft cookie for ready, a gem
 * for not verified, a burst for failures, a circle for not applicable. Shape is a third cue next
 * to the icon and the text, never the only one.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun glyphPolygon(state: AvailabilityState) = when (state) {
    AvailabilityState.AVAILABLE -> MaterialShapes.Cookie9Sided
    AvailabilityState.NOT_VERIFIED -> MaterialShapes.Gem
    AvailabilityState.INITIALIZATION_FAILED, AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE -> MaterialShapes.SoftBurst
    AvailabilityState.UNSUPPORTED_DEVICE, AvailabilityState.NO_UPSTREAM_IMPLEMENTATION -> MaterialShapes.Circle
    else -> MaterialShapes.Pentagon
}

/** Large status mark for hero surfaces: tone + expressive shape + icon. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusGlyph(state: AvailabilityState, modifier: Modifier = Modifier) {
    val v = state.visual()
    val tone = v.tone(LocalStatusColors.current)
    Surface(color = tone.container, contentColor = tone.content, shape = glyphPolygon(state).toShape(), modifier = modifier.size(56.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(v.icon, contentDescription = null, modifier = Modifier.size(26.dp)) }
    }
}

fun AvailabilityState.visual(): StatusVisual = when (this) {
    AvailabilityState.AVAILABLE -> StatusVisual(R.string.state_available, Icons.Filled.CheckCircle) { it.success }
    AvailabilityState.NOT_VERIFIED -> StatusVisual(R.string.state_not_verified, Icons.Filled.Info) { it.info }
    AvailabilityState.ROOT_REQUIRED -> StatusVisual(R.string.state_root, Icons.Filled.Lock) { it.warning }
    AvailabilityState.PERMISSION_REQUIRED -> StatusVisual(R.string.state_permission, Icons.Filled.Lock) { it.warning }
    AvailabilityState.LICENSE_REQUIRED -> StatusVisual(R.string.state_license, Icons.Filled.Lock) { it.warning }
    AvailabilityState.COMPONENT_NOT_INSTALLED -> StatusVisual(R.string.state_not_installed, Icons.Filled.Build) { it.warning }
    AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE -> StatusVisual(R.string.state_native, Icons.Filled.Build) { it.error }
    AvailabilityState.NETWORK_UNAVAILABLE -> StatusVisual(R.string.state_network, Icons.Filled.Warning) { it.warning }
    AvailabilityState.INITIALIZATION_FAILED -> StatusVisual(R.string.state_failed, Icons.Filled.Warning) { it.error }
    AvailabilityState.UNSUPPORTED_DEVICE -> StatusVisual(R.string.state_unsupported, Icons.Filled.Close) { it.unavailable }
    AvailabilityState.NO_UPSTREAM_IMPLEMENTATION -> StatusVisual(R.string.state_no_upstream, Icons.Filled.Clear) { it.unavailable }
}

/** Status pill: icon + text on a semantic tone; animates between states (instant if reduced). */
@Composable
fun StatusBadge(state: AvailabilityState, modifier: Modifier = Modifier) {
    val v = state.visual()
    val tone = v.tone(LocalStatusColors.current)
    val container by animateColorAsState(tone.container, ZairenkaiMotion.effects(), label = "badge")
    val label = stringResource(v.label)
    Surface(
        color = container,
        contentColor = tone.content,
        shape = ZairenkaiShapes.pill,
        modifier = modifier.semantics(mergeDescendants = true) { stateDescription = label },
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(v.icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

enum class Emphasis { LOW, NORMAL, HIGH }

/** A titled section on one tonal surface. Emphasis sets the tone, so importance shows at a glance. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    emphasis: Emphasis = Emphasis.NORMAL,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (emphasis) {
        Emphasis.LOW -> cs.surfaceContainerLow to cs.onSurface
        Emphasis.NORMAL -> cs.surfaceContainer to cs.onSurface
        Emphasis.HIGH -> cs.secondaryContainer to cs.onSecondaryContainer
    }
    Surface(color = bg, contentColor = fg, shape = ZairenkaiShapes.section, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(ZairenkaiSpacing.l).animateContentSize(ZairenkaiMotion.spatial()),
            verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s),
        ) {
            // The trailing badge moves under the title when both do not fit on one line, so a
            // long status label never squeezes the title into a narrow column.
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s, Alignment.Start),
                verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                trailing?.invoke()
            }
            content()
        }
    }
}

/**
 * The most prominent surface of a screen: an overline, a headline and one supporting line, with
 * a trailing visual (status glyph, ring gauge or the AI mark). Tonal, not saturated: colour is
 * kept for the status and the measured value.
 */
@Composable
fun HeroPanel(
    overline: String,
    headline: String,
    supporting: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = ZairenkaiShapes.hero,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(ZairenkaiSpacing.xl).animateContentSize(ZairenkaiMotion.spatial()),
            verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs)) {
                    Text(overline, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(headline, style = MaterialTheme.typography.headlineMedium)
                    if (supporting != null) {
                        Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                trailing?.invoke()
            }
            content?.invoke()
        }
    }
}

/** A hero whose trailing visual is the status glyph of [state], named under the overline. */
@Composable
fun HeroStatusCard(
    overline: String,
    headline: String,
    supporting: String?,
    state: AvailabilityState?,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    val label = state?.let { stringResource(it.visual().label) }
    HeroPanel(
        overline = overline,
        headline = headline,
        supporting = listOfNotNull(label, supporting).joinToString(" · ").ifBlank { null },
        modifier = modifier,
        trailing = state?.let { { StatusGlyph(it) } },
        content = content,
    )
}

/**
 * One measured value, optionally with a [meter] for values that are fractions. Only the value text
 * changes when new data arrives: a short cross-fade (none if reduced), never a counting animation.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    caption: String?,
    modifier: Modifier = Modifier,
    emphasis: Emphasis = Emphasis.NORMAL,
    meter: Float? = null,
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (emphasis == Emphasis.HIGH) cs.primaryContainer else cs.surfaceContainerHighest
    val fg = if (emphasis == Emphasis.HIGH) cs.onPrimaryContainer else cs.onSurface
    Surface(color = bg, contentColor = fg, shape = ZairenkaiShapes.metric, modifier = modifier.widthIn(min = 140.dp)) {
        Column(Modifier.padding(ZairenkaiSpacing.l).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            val spec = ZairenkaiMotion.fastEffects<Float>()
            AnimatedContent(targetState = value, transitionSpec = { fadeIn(spec) togetherWith fadeOut(spec) }, label = "metric") {
                Text(it, style = ZairenkaiTextStyles.metric)
            }
            if (meter != null) Meter(meter, Modifier.padding(vertical = ZairenkaiSpacing.xs), color = if (emphasis == Emphasis.HIGH) cs.onPrimaryContainer else cs.primary)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Whether [InfoRow] keeps its 42 % / 58 % columns: only when the longest unbreakable word of each fits. */
internal fun infoRowSideBySide(keyLongestWord: Int, valueLongestWord: Int, width: Int, gap: Int): Boolean {
    val keyColumn = (width * 0.42f).toInt()
    return keyLongestWord <= keyColumn && valueLongestWord <= width - keyColumn - gap
}

/** Width in px of the widest whitespace-separated word of [text], laid out on one line. */
private fun longestWordWidth(measurer: TextMeasurer, text: String, style: TextStyle): Int =
    text.split(Regex("\\s+")).filter { it.isNotEmpty() }.maxOfOrNull {
        measurer.measure(it, style, softWrap = false, maxLines = 1).size.width
    } ?: 0

/**
 * Key/value row for technical details; [code] switches the value to monospace.
 * Label and value side by side (42 % / 58 %) when every word fits its column; otherwise the label
 * stacks above the value at full width. Property names, paths and package names have no break
 * points, and a fixed split wrapped them one character per line on narrow screens. Code values are
 * selectable so long identifiers can be copied.
 */
@Composable
fun InfoRow(key: String, value: String?, code: Boolean = false, numeric: Boolean = false) {
    val shown = value ?: stringResource(R.string.not_reported)
    val keyStyle = MaterialTheme.typography.bodyMedium
    val valueStyle = when {
        code -> ZairenkaiTextStyles.code
        numeric -> ZairenkaiTextStyles.numeric
        else -> MaterialTheme.typography.bodyMedium
    }
    // Longest whitespace-separated word, measured on one line: the columns are kept only when no
    // word has to be broken, whatever break opportunities the line breaker would find inside it.
    val measurer = rememberTextMeasurer()
    val keyWord = remember(key, keyStyle, measurer) { longestWordWidth(measurer, key, keyStyle) }
    val valueWord = remember(shown, valueStyle, measurer) { longestWordWidth(measurer, shown, valueStyle) }
    val row = @Composable {
        Layout(
            content = {
                Text(key, style = keyStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(shown, style = valueStyle)
            },
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        ) { measurables, constraints ->
            val (k, v) = measurables
            val width = constraints.maxWidth
            val gap = ZairenkaiSpacing.s.roundToPx()
            val keyColumn = (width * 0.42f).toInt()
            val valueColumn = width - keyColumn - gap
            val sideBySide = constraints.hasBoundedWidth && infoRowSideBySide(keyWord, valueWord, width, gap)
            if (sideBySide) {
                val kp = k.measure(Constraints(maxWidth = keyColumn))
                val vp = v.measure(Constraints(maxWidth = valueColumn))
                layout(width, maxOf(kp.height, vp.height)) {
                    kp.placeRelative(0, 0)
                    vp.placeRelative(keyColumn + gap, 0)
                }
            } else {
                val bounded = Constraints(maxWidth = if (constraints.hasBoundedWidth) width else Constraints.Infinity)
                val kp = k.measure(bounded)
                val vp = v.measure(bounded)
                layout(maxOf(kp.width, vp.width).coerceAtMost(constraints.maxWidth), kp.height + vp.height) {
                    kp.placeRelative(0, 0)
                    vp.placeRelative(0, kp.height)
                }
            }
        }
    }
    if (code) SelectionContainer { row() } else row()
}

@Composable
fun Supporting(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A registry record as a row: name, the registry's reason, and the status badge. */
@Composable
fun CapabilityItem(r: CapabilityRecord, horizontalPadding: androidx.compose.ui.unit.Dp = ZairenkaiSpacing.l) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = horizontalPadding, vertical = ZairenkaiSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.name, style = MaterialTheme.typography.titleSmall)
            if (r.availability.reason.isNotBlank()) {
                Text(r.availability.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusBadge(r.availability.state, Modifier.padding(top = ZairenkaiSpacing.xs))
        }
    }
}

/** Feature that cannot run here: why (from the registry), and what the user can do. */
@Composable
fun UnavailableCard(record: CapabilityRecord?, detail: String? = null, action: (@Composable () -> Unit)? = null) {
    if (record == null) return
    SectionCard(record.name, emphasis = Emphasis.LOW, trailing = { StatusBadge(record.availability.state) }) {
        if (record.availability.reason.isNotBlank()) Supporting(record.availability.reason)
        if (detail != null && detail != record.availability.reason) Supporting(detail)
        action?.invoke()
    }
}

/**
 * Collapsible section; the whole header is the touch target, and the expanded state survives
 * configuration changes. [summary] (a count, a state) stays visible while collapsed.
 */
@Composable
fun ExpandableSection(title: String, initiallyExpanded: Boolean = false, summary: String? = null, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val angle by animateFloatAsState(if (expanded) 180f else 0f, ZairenkaiMotion.spatial(), label = "chevron")
    val state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = stringResource(if (expanded) R.string.collapse_section else R.string.expand_section, title)) { expanded = !expanded }
                .semantics { stateDescription = state },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (summary != null) Text(summary, style = ZairenkaiTextStyles.numeric, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(ZairenkaiIcons.expand, contentDescription = null, modifier = Modifier.padding(start = ZairenkaiSpacing.s).rotate(angle))
        }
        AnimatedVisibility(expanded, enter = expandVertically(ZairenkaiMotion.spatial()) + fadeIn(ZairenkaiMotion.effects()), exit = shrinkVertically(ZairenkaiMotion.spatial()) + fadeOut(ZairenkaiMotion.effects())) {
            Column(verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs)) { content() }
        }
    }
}

/**
 * In-progress state tied to a real pending operation (never a decorative timer). Uses the
 * expressive LoadingIndicator; with animations off it shows a static mark and the text alone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProgressRow(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        if (LocalReducedMotion.current) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(24.dp))
        } else {
            LoadingIndicator(Modifier.size(32.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Confirmation for an action that changes the device. [destructive] uses the error role and a
 * squarer shape, so it cannot be mistaken for an ordinary dialog.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: @Composable () -> Unit,
    confirmLabel: String,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = if (destructive) ZairenkaiShapes.destructive else MaterialTheme.shapes.extraLarge,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = body,
        confirmButton = {
            if (onConfirm != null) {
                Button(
                    onClick = onConfirm,
                    colors = if (destructive) ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),
                ) { Text(confirmLabel) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A compact tile that opens the screen behind a value (thermal state, root access). */
@Composable
fun ActionTile(icon: ImageVector, label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = ZairenkaiShapes.metric,
        modifier = modifier,
    ) {
        Column(Modifier.padding(ZairenkaiSpacing.l), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            GlyphBadge(icon)
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
