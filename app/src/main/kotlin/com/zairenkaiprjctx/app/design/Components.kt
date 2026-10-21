// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.integration.model.Availability
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.registry.CapabilityRecord

/** Icons used across the app, from the Material core icon set. */
object ZairenkaiIcons {
    val home = Icons.Filled.Home
    val performance = Icons.Filled.PlayArrow
    val thermal = Icons.Filled.Warning
    val runtime = Icons.Filled.Build
    val system = Icons.Filled.List
    val settings = Icons.Filled.Settings
    val refresh = Icons.Filled.Refresh
    val expand = Icons.Filled.KeyboardArrowDown
    val close = Icons.Filled.Close
}

/** How a capability state is presented: label, icon and semantic tone. Never colour alone. */
data class StatusVisual(@StringRes val label: Int, val icon: ImageVector, val tone: (ZairenkaiStatusColors) -> StatusTone)

/**
 * Expressive container shape per tone (Material shapes library): a soft cookie for ready, a
 * pentagon for attention, a burst for failures, a circle for not applicable. Shape is a third
 * cue next to the icon and the text, never the only one.
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
    Surface(color = tone.container, contentColor = tone.content, shape = glyphPolygon(state).toShape(), modifier = modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(v.icon, contentDescription = null, modifier = Modifier.size(24.dp)) }
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

/** A titled section. Emphasis sets the surface tone, so importance is visible at a glance. */
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                trailing?.invoke()
            }
            content()
        }
    }
}

/** The main system status: the most prominent surface on a screen. */
@Composable
fun HeroStatusCard(
    overline: String,
    headline: String,
    supporting: String?,
    state: AvailabilityState?,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = ZairenkaiShapes.hero,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(ZairenkaiSpacing.xl), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
                if (state != null) StatusGlyph(state)
                Column(Modifier.weight(1f)) {
                    Text(overline, style = MaterialTheme.typography.labelLarge)
                    if (state != null) Text(stringResource(state.visual().label), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(headline, style = MaterialTheme.typography.headlineMedium)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium)
            content?.invoke()
        }
    }
}

/**
 * One measured value. Only the value text changes when new data arrives: a short cross-fade on
 * change (none if reduced), never a counting animation, so readings stay legible.
 */
@Composable
fun MetricTile(label: String, value: String, caption: String?, modifier: Modifier = Modifier, emphasis: Emphasis = Emphasis.NORMAL) {
    val cs = MaterialTheme.colorScheme
    val bg = if (emphasis == Emphasis.HIGH) cs.tertiaryContainer else cs.surfaceContainerHigh
    val fg = if (emphasis == Emphasis.HIGH) cs.onTertiaryContainer else cs.onSurface
    Surface(color = bg, contentColor = fg, shape = ZairenkaiShapes.metric, modifier = modifier.widthIn(min = 140.dp)) {
        Column(Modifier.padding(ZairenkaiSpacing.l).semantics(mergeDescendants = true) {}) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            val spec = ZairenkaiMotion.fastEffects<Float>()
            AnimatedContent(targetState = value, transitionSpec = { fadeIn(spec) togetherWith fadeOut(spec) }, label = "metric") {
                Text(it, style = ZairenkaiTextStyles.metric)
            }
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
    // Longest word measured here, not with minIntrinsicWidth: that follows the line BreakIterator,
    // which (on the JVM, and on some ICU versions) breaks at '.' and '_', so a property name looked
    // like short words that fit the 42 % column and then wrapped beside its value.
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

/** A registry record as a list item: name, the registry's reason, and the status badge. */
@Composable
fun CapabilityItem(r: CapabilityRecord) {
    ListItem(
        headlineContent = { Text(r.name, style = MaterialTheme.typography.titleSmall) },
        supportingContent = if (r.availability.reason.isNotBlank()) {
            { Text(r.availability.reason, style = MaterialTheme.typography.bodySmall) }
        } else {
            null
        },
        trailingContent = { StatusBadge(r.availability.state) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
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

/** Collapsible section; the expanded state survives configuration changes. */
@Composable
fun ExpandableSection(title: String, initiallyExpanded: Boolean = false, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val angle by animateFloatAsState(if (expanded) 180f else 0f, ZairenkaiMotion.spatial(), label = "chevron")
    Column(Modifier.fillMaxWidth().animateContentSize(ZairenkaiMotion.spatial())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            val desc = stringResource(if (expanded) R.string.collapse_section else R.string.expand_section, title)
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.semantics { contentDescription = desc; role = Role.Button }) {
                Icon(ZairenkaiIcons.expand, contentDescription = null, modifier = Modifier.rotate(angle))
            }
        }
        if (expanded) content()
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
