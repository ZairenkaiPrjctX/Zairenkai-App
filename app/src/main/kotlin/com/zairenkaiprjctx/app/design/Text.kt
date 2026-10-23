// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.design

import android.content.ClipData
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zairenkaiprjctx.app.R
import kotlinx.coroutines.launch

/**
 * Long text, kept tidy: at most [maxLines] lines with an ellipsis, and a "Show more" link only when
 * the text really overflows. Used for reasons and details that come from daemons and probes,
 * whose length the app does not control.
 */
@Composable
fun ClampText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = 3,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text, maxLines) { mutableStateOf(false) }
    Column(modifier.animateContentSize(ZairenkaiMotion.spatial())) {
        Text(
            text,
            style = style,
            color = color,
            maxLines = if (expanded) Int.MAX_VALUE else maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            val label = stringResource(if (expanded) R.string.text_show_less else R.string.text_show_more)
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

/** A secondary detail line: small, muted and clamped (see [ClampText]). */
@Composable
fun Detail(text: String, modifier: Modifier = Modifier, maxLines: Int = 3) {
    ClampText(text, modifier, maxLines = maxLines)
}

/**
 * A headline that never breaks a layout: it shrinks step by step toward [minFontSize] before it
 * wraps, and ends with an ellipsis after [maxLines] lines (long device or module names).
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 2,
    minFontSize: TextUnit = 16.sp,
) {
    Text(
        text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = style.fontSize, stepSize = 1.sp),
        modifier = modifier,
    )
}

/**
 * Shortens a long identifier (a SHA-256, a key id) to its start and end, which is how people
 * compare them; the full value stays one tap away and can be copied.
 */
fun middleEllipsis(value: String, keep: Int = 10): String =
    if (value.length <= keep * 2 + 3) value else value.take(keep) + "…" + value.takeLast(keep)

/**
 * A long identifier in monospace: shortened with [middleEllipsis] until tapped, with a copy button.
 * Screen readers get the full value.
 */
@Composable
fun CodeValue(value: String, modifier: Modifier = Modifier, keep: Int = 10) {
    var full by rememberSaveable(value) { mutableStateOf(false) }
    val shortened = middleEllipsis(value, keep)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringResource(R.string.action_copy)
    val state = stringResource(if (full || shortened == value) R.string.state_expanded else R.string.state_collapsed)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs)) {
        Text(
            if (full) value else shortened,
            style = ZairenkaiTextStyles.code,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable(enabled = shortened != value, role = Role.Button) { full = !full }
                .semantics {
                    contentDescription = value
                    stateDescription = state
                },
        )
        IconButton(
            onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(value, value))) } },
            modifier = Modifier.size(40.dp).semantics { contentDescription = copyLabel },
        ) { Icon(ZairenkaiGlyphs.copy, contentDescription = null, modifier = Modifier.size(18.dp)) }
    }
}
