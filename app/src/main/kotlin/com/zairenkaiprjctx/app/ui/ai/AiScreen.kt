// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui.ai

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.Tab
import com.zairenkaiprjctx.app.design.AiOrb
import com.zairenkaiprjctx.app.design.GroupRow
import com.zairenkaiprjctx.app.design.GroupContent
import com.zairenkaiprjctx.app.design.ExpandableSection
import com.zairenkaiprjctx.app.design.InfoRow
import com.zairenkaiprjctx.app.design.SegmentedGroup
import com.zairenkaiprjctx.app.design.StatusBadge
import com.zairenkaiprjctx.app.design.Supporting
import com.zairenkaiprjctx.app.design.ZairenkaiGlyphs
import com.zairenkaiprjctx.app.design.ZairenkaiShapes
import com.zairenkaiprjctx.app.design.ZairenkaiSpacing
import com.zairenkaiprjctx.app.design.ZairenkaiTextStyles
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.registry.RegistrySnapshot

/** The registry record that says whether an on-device model runtime exists (Zairenkai-Intelligence-Engine). */
const val AI_CAPABILITY = "intelligence.analysis"

/** Questions the user can tap; until a model runs on the device each opens the screen with the answer. */
enum class AiPrompt(@StringRes val text: Int, val opens: Tab) {
    HOT(R.string.ai_prompt_hot, Tab.THERMAL),
    PROFILE(R.string.ai_prompt_profile, Tab.PERFORMANCE),
    CONTROL(R.string.ai_prompt_control, Tab.HOME),
    ROOT(R.string.ai_prompt_root, Tab.SYSTEM),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiScreen(vm: AiViewModel, snapshot: RegistrySnapshot, padding: PaddingValues, open: (Tab) -> Unit) {
    val turns by vm.transcript.collectAsStateWithLifecycle()
    val record = snapshot.byId(AI_CAPABILITY)
    // The composer is enabled only when the registry reports a usable on-device runtime.
    val ready = record?.availability?.usable == true
    val dir = LocalLayoutDirection.current
    val start = padding.calculateStartPadding(dir)
    val end = padding.calculateEndPadding(dir)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = start, end = end, top = padding.calculateTopPadding(), bottom = ZairenkaiSpacing.l),
            verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m),
        ) {
            item { AiIntro(record?.availability?.state) }
            item { HowItWorks() }
            item { ModelStatus(record?.availability?.reason) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                    Text(
                        stringResource(R.string.ai_try),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = ZairenkaiSpacing.l).semantics { heading() },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs)) {
                        AiPrompt.entries.forEach { p -> PromptChip(stringResource(p.text)) { open(p.opens) } }
                    }
                    Supporting(stringResource(if (ready) R.string.ai_try_note_ready else R.string.ai_try_note))
                }
            }
            items(turns, key = { it.id }) { t -> AiTurnView(t, onDecide = vm::decide) }
        }
        AiComposer(
            enabled = ready,
            modifier = Modifier.padding(start = start, end = end, bottom = (padding.calculateBottomPadding() - ZairenkaiSpacing.l).coerceAtLeast(ZairenkaiSpacing.s)),
        )
    }
}

@Composable
private fun AiIntro(state: AvailabilityState?) {
    Column(
        Modifier.fillMaxWidth().padding(top = ZairenkaiSpacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m),
    ) {
        // The mark stays still: it moves only while a model is actually working.
        AiOrb(size = 88.dp, active = false)
        Text(
            stringResource(R.string.ai_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (state != null) StatusBadge(state)
    }
}

@Composable
private fun HowItWorks() {
    SegmentedGroup(title = stringResource(R.string.ai_how)) {
        row { GroupRow(stringResource(R.string.ai_how_device), supporting = stringResource(R.string.ai_how_device_body), icon = ZairenkaiGlyphs.lock) }
        row { GroupRow(stringResource(R.string.ai_how_tools), supporting = stringResource(R.string.ai_how_tools_body), icon = ZairenkaiGlyphs.tool) }
        row { GroupRow(stringResource(R.string.ai_how_approve), supporting = stringResource(R.string.ai_how_approve_body), icon = ZairenkaiGlyphs.approve) }
    }
}

@Composable
private fun ModelStatus(reason: String?) {
    SegmentedGroup(title = stringResource(R.string.ai_model)) {
        row {
            GroupRow(
                stringResource(R.string.ai_model_name),
                supporting = stringResource(R.string.ai_model_body),
                icon = ZairenkaiGlyphs.chip,
                status = AvailabilityState.COMPONENT_NOT_INSTALLED,
            )
        }
        // The registry's own reason, for whoever wants the engineering detail.
        if (!reason.isNullOrBlank()) {
            row { GroupContent { ExpandableSection(stringResource(R.string.ai_why)) { Supporting(reason) } } }
        }
    }
}

@Composable
fun PromptChip(text: String, onClick: () -> Unit) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelLarge) },
        icon = { Icon(ZairenkaiGlyphs.spark, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = ZairenkaiShapes.pill,
    )
}

/** Message field and send button, pinned above the navigation bar. */
@Composable
fun AiComposer(enabled: Boolean, modifier: Modifier = Modifier, onSend: (String) -> Unit = {}) {
    var draft by rememberSaveable { mutableStateOf("") }
    val sendLabel = stringResource(R.string.ai_send)
    Column(modifier.fillMaxWidth().padding(top = ZairenkaiSpacing.s), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                enabled = enabled,
                placeholder = { Text(stringResource(R.string.ai_placeholder)) },
                shape = RoundedCornerShape(28.dp),
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            FilledIconButton(
                onClick = { onSend(draft.trim()); draft = "" },
                enabled = enabled && draft.isNotBlank(),
                modifier = Modifier.size(52.dp).semantics { contentDescription = sendLabel },
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) }
        }
        if (!enabled) Supporting(stringResource(R.string.ai_composer_unavailable))
    }
}

/** One conversation entry; stateless, so it renders the same in the app and in tests. */
@Composable
fun AiTurnView(turn: AiTurn, onDecide: (String, Boolean) -> Unit) {
    when (turn) {
        is AiTurn.User -> ChatBubble(turn.text, fromUser = true)
        is AiTurn.Assistant -> ChatBubble(turn.text, fromUser = false)
        is AiTurn.Tool -> ToolCallCard(turn)
        is AiTurn.Approval -> ApprovalCard(turn, onDecide)
    }
}

@Composable
fun ChatBubble(text: String, fromUser: Boolean) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = if (fromUser) cs.primaryContainer else cs.surfaceContainerHigh,
            contentColor = if (fromUser) cs.onPrimaryContainer else cs.onSurface,
            shape = if (fromUser) ZairenkaiShapes.bubbleUser else ZairenkaiShapes.bubbleAssistant,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = ZairenkaiSpacing.l, vertical = ZairenkaiSpacing.m))
        }
    }
}

@StringRes
private fun ToolStatus.label(): Int = when (this) {
    ToolStatus.RUNNING -> R.string.ai_tool_running
    ToolStatus.COMPLETED -> R.string.ai_tool_completed
    ToolStatus.FAILED -> R.string.ai_tool_failed
    ToolStatus.REJECTED -> R.string.ai_tool_rejected
}

/** A typed, read-only tool request: the tool's name and arguments exactly as the model sent them. */
@Composable
fun ToolCallCard(turn: AiTurn.Tool) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = ZairenkaiShapes.metric, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ZairenkaiSpacing.l), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                Icon(ZairenkaiGlyphs.tool, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.ai_tool_request), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(stringResource(turn.status.label()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(turn.name, style = ZairenkaiTextStyles.code)
            turn.arguments.forEach { (k, v) -> InfoRow(k, v, code = true) }
        }
    }
}

/** A device change proposed by the model: Core's decision first, then the user's explicit answer. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ApprovalCard(turn: AiTurn.Approval, onDecide: (String, Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceContainer,
        shape = ZairenkaiShapes.section,
        border = BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(ZairenkaiSpacing.l), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                Icon(ZairenkaiGlyphs.shield, contentDescription = null, tint = cs.primary)
                Text(stringResource(R.string.ai_approval_title), style = MaterialTheme.typography.labelLarge, color = cs.primary)
            }
            Text(turn.title, style = MaterialTheme.typography.titleMedium)
            Text(turn.summary, style = MaterialTheme.typography.bodyMedium)
            InfoRow(stringResource(R.string.ai_approval_core), turn.coreVerdict ?: stringResource(R.string.not_reported))
            turn.rules.forEach { Supporting("• $it") }
            when (turn.state) {
                ApprovalState.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                    // Core's dry run must allow the change before the user can approve it.
                    Button(onClick = { onDecide(turn.id, true) }, enabled = turn.coreVerdict == "allow") { Text(stringResource(R.string.ai_approve)) }
                    OutlinedButton(onClick = { onDecide(turn.id, false) }) { Text(stringResource(R.string.ai_reject)) }
                }
                ApprovalState.APPROVED -> Supporting(stringResource(R.string.ai_approval_approved))
                ApprovalState.REJECTED -> Supporting(stringResource(R.string.ai_approval_rejected))
                ApprovalState.APPLIED -> Supporting(stringResource(R.string.ai_approval_applied))
                ApprovalState.FAILED -> Supporting(stringResource(R.string.ai_approval_failed))
            }
        }
    }
}
