// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Where a row sits in a segmented group: outer corners are large, inner corners small. */
enum class SegmentPosition { SINGLE, FIRST, MIDDLE, LAST }

fun segmentPosition(index: Int, count: Int): SegmentPosition = when {
    count <= 1 -> SegmentPosition.SINGLE
    index == 0 -> SegmentPosition.FIRST
    index == count - 1 -> SegmentPosition.LAST
    else -> SegmentPosition.MIDDLE
}

fun segmentShape(position: SegmentPosition): Shape {
    val outer = ZairenkaiShapes.segmentOuter
    val inner = ZairenkaiShapes.segmentInner
    return when (position) {
        SegmentPosition.SINGLE -> RoundedCornerShape(outer)
        SegmentPosition.FIRST -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomEnd = inner, bottomStart = inner)
        SegmentPosition.MIDDLE -> RoundedCornerShape(inner)
        SegmentPosition.LAST -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomEnd = outer, bottomStart = outer)
    }
}

/** Collects the rows of a [SegmentedGroup]; each row becomes one segment. */
class SegmentedGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/** Small primary-coloured label above a group, announced as a heading. */
@Composable
fun GroupHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = ZairenkaiSpacing.l, top = ZairenkaiSpacing.s, bottom = ZairenkaiSpacing.xs).semantics { heading() },
    )
}

/**
 * Rows on one tonal surface, separated by 2 dp gaps instead of dividers (the Material 3
 * Expressive list style): calm, and each row stays a clear touch target.
 */
@Composable
fun SegmentedGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: SegmentedGroupScope.() -> Unit,
) {
    val rows = SegmentedGroupScope().apply(content).rows
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ZairenkaiShapes.segmentGap)) {
        if (title != null) GroupHeader(title)
        rows.forEachIndexed { i, row ->
            Surface(
                color = containerColor,
                shape = segmentShape(segmentPosition(i, rows.size)),
                modifier = Modifier.fillMaxWidth(),
            ) { row() }
        }
    }
}

/** Free content inside a segment, with the row padding. */
@Composable
fun GroupContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(ZairenkaiSpacing.l).animateContentSize(ZairenkaiMotion.spatial()),
        verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s),
        content = content,
    )
}

/** A round tonal backdrop for a row's leading glyph. */
@Composable
fun GlyphBadge(icon: ImageVector, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (emphasized) cs.primaryContainer else cs.surfaceContainerHighest,
        contentColor = if (emphasized) cs.onPrimaryContainer else cs.onSurfaceVariant,
        shape = CircleShape,
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) }
    }
}

/**
 * One row of a segmented group: optional glyph, headline, supporting text and a trailing slot.
 * With [onClick] the whole row is the touch target and [chevron] marks rows that open a page.
 */
@Composable
fun GroupRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
    status: com.zairenkaiprjctx.integration.model.AvailabilityState? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier.fillMaxWidth().then(clickable).heightIn(min = 64.dp).padding(horizontal = ZairenkaiSpacing.l, vertical = ZairenkaiSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l),
    ) {
        if (icon != null) GlyphBadge(icon, emphasized = emphasized)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(headline, style = MaterialTheme.typography.titleMedium)
            if (!supporting.isNullOrBlank()) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Status sits under the text: a long label must never squeeze the headline.
            if (status != null) StatusBadge(status, Modifier.padding(top = ZairenkaiSpacing.xs))
        }
        if (trailing != null) Box(Modifier.widthIn(max = 168.dp)) { trailing() }
        if (chevron) Icon(ZairenkaiGlyphs.chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A single choice among a few options as a Material 3 Expressive connected button group: the
 * chosen option morphs to the pill shape and carries a check mark, so the choice is not colour
 * alone. Each option is announced as a radio button.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedChoice(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(androidx.compose.material3.ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { i, label ->
            val shapes = when (i) {
                0 -> androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> androidx.compose.material3.ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            androidx.compose.material3.ToggleButton(
                checked = i == selected,
                onCheckedChange = { if (it || i != selected) onSelect(i) },
                shapes = shapes,
                enabled = enabled,
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) {
                if (i == selected) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 2.dp))
                }
                Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}
