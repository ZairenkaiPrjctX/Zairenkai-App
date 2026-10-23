// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.zairenkaiprjctx.app.design.Detail
import com.zairenkaiprjctx.app.design.GroupHeader
import com.zairenkaiprjctx.app.design.ShapeBadge
import com.zairenkaiprjctx.app.design.TileGrid
import com.zairenkaiprjctx.app.design.entrance
import com.zairenkaiprjctx.app.design.rememberEntrance
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
import androidx.compose.ui.platform.LocalContext
import com.zairenkaiprjctx.app.design.Meter
import com.zairenkaiprjctx.app.design.ProgressRow
import com.zairenkaiprjctx.integration.ai.Fact
import com.zairenkaiprjctx.integration.ai.FactKey
import com.zairenkaiprjctx.integration.ai.ModelManifest
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.registry.CapabilityRecord
import com.zairenkaiprjctx.integration.registry.RegistrySnapshot

/** The registry record that says whether an on-device model runtime exists (Zairenkai-Intelligence-Engine). */
const val AI_CAPABILITY = "intelligence.analysis"

/** Questions the user can tap: asked to the model when it is ready, otherwise they open the screen with the answer. */
enum class AiPrompt(@StringRes val text: Int, val opens: Tab, val icon: ImageVector) {
    HOT(R.string.ai_prompt_hot, Tab.THERMAL, ZairenkaiGlyphs.thermometer),
    PROFILE(R.string.ai_prompt_profile, Tab.PERFORMANCE, ZairenkaiGlyphs.sliders),
    CONTROL(R.string.ai_prompt_control, Tab.HOME, ZairenkaiGlyphs.tiles),
    ROOT(R.string.ai_prompt_root, Tab.SYSTEM, ZairenkaiGlyphs.shield),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiScreen(vm: AiViewModel, snapshot: RegistrySnapshot, padding: PaddingValues, open: (Tab) -> Unit) {
    val turns by vm.transcript.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val record = snapshot.byId(AI_CAPABILITY)
    // The composer is enabled only when the registry reports a usable on-device runtime and model.
    val ready = record?.availability?.usable == true
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importModel(uri) }
    val dir = LocalLayoutDirection.current
    val start = padding.calculateStartPadding(dir)
    val end = padding.calculateEndPadding(dir)
    val entrance = rememberEntrance()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = start, end = end, top = padding.calculateTopPadding(), bottom = ZairenkaiSpacing.l),
            verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m),
        ) {
            item { Box(Modifier.entrance(entrance, 0)) { AiIntro(record?.availability?.state, thinking = busy) } }
            item {
                Column(Modifier.entrance(entrance, 1), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
                    GroupHeader(stringResource(R.string.ai_try), icon = ZairenkaiGlyphs.spark)
                    TileGrid(AiPrompt.entries) { p, m ->
                        val question = stringResource(p.text)
                        PromptCard(p, m) { if (ready && !busy) vm.send(question) else open(p.opens) }
                    }
                    Detail(stringResource(if (ready) R.string.ai_try_note_ready else R.string.ai_try_note), Modifier.padding(horizontal = ZairenkaiSpacing.xs))
                }
            }
            item { Box(Modifier.entrance(entrance, 2)) { HowItWorks() } }
            item {
                Box(Modifier.entrance(entrance, 3)) {
                    ModelCard(record, vm.manifest, importState, onImport = { pick.launch(arrayOf("*/*")) }, onDelete = vm::deleteModel)
                }
            }
            items(turns, key = { it.id }) { t -> AiTurnView(t, onDecide = vm::decide) }
            if (busy) item { ProgressRow(stringResource(R.string.ai_thinking)) }
        }
        AiComposer(
            enabled = ready && !busy,
            onSend = vm::send,
            modifier = Modifier.padding(start = start, end = end, bottom = (padding.calculateBottomPadding() - ZairenkaiSpacing.l).coerceAtLeast(ZairenkaiSpacing.s)),
        )
    }
}

@Composable
private fun AiIntro(state: AvailabilityState?, thinking: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = ZairenkaiSpacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // A soft halo behind the mark; the mark moves only while the model is actually working.
            Box(Modifier.size(196.dp).background(Brush.radialGradient(listOf(cs.primary.copy(alpha = 0.24f), cs.primary.copy(alpha = 0.06f), Color.Transparent))))
            AiOrb(size = 112.dp, active = thinking)
        }
        Text(
            stringResource(R.string.ai_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (state != null) StatusBadge(state)
    }
}

/** A suggested question as a card: glyph and question; it opens the screen that answers it today. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PromptCard(prompt: AiPrompt, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(onClick = onClick, color = cs.surfaceContainerHigh, shape = ZairenkaiShapes.metric, modifier = modifier) {
        Column(Modifier.padding(ZairenkaiSpacing.l), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
            ShapeBadge(prompt.icon, MaterialShapes.Cookie6Sided, cs.secondaryContainer, cs.onSecondaryContainer, size = 40.dp)
            Text(stringResource(prompt.text), style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HowItWorks() {
    SegmentedGroup(title = stringResource(R.string.ai_how), icon = ZairenkaiGlyphs.lock) {
        row { GroupRow(stringResource(R.string.ai_how_device), supporting = stringResource(R.string.ai_how_device_body), icon = ZairenkaiGlyphs.lock) }
        row { GroupRow(stringResource(R.string.ai_how_tools), supporting = stringResource(R.string.ai_how_tools_body), icon = ZairenkaiGlyphs.tool) }
        row { GroupRow(stringResource(R.string.ai_how_approve), supporting = stringResource(R.string.ai_how_approve_body), icon = ZairenkaiGlyphs.approve) }
    }
}

/** The model: what it is, whether it is imported, and the import or delete action. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCard(record: CapabilityRecord?, manifest: ModelManifest?, import: ModelImportUi, onImport: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    fun size(bytes: Long) = android.text.format.Formatter.formatShortFileSize(context, bytes)
    val state = record?.availability?.state
    val installed = record?.availability?.usable == true
    // Importing makes sense only where the runtime runs: not without zie-run or with too little memory.
    val canImport = manifest != null && state != AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE && state != AvailabilityState.UNSUPPORTED_DEVICE &&
        state != AvailabilityState.INITIALIZATION_FAILED
    SegmentedGroup(title = stringResource(R.string.ai_model), icon = ZairenkaiGlyphs.chip) {
        row {
            GroupRow(
                manifest?.displayName ?: stringResource(R.string.ai_model_name),
                supporting = stringResource(R.string.ai_model_body),
                icon = ZairenkaiGlyphs.chip,
                status = state ?: AvailabilityState.COMPONENT_NOT_INSTALLED,
            )
        }
        if (manifest != null) {
            row {
                GroupContent {
                    InfoRow(stringResource(R.string.ai_model_size), size(manifest.sizeBytes))
                    InfoRow(stringResource(R.string.ai_model_license), manifest.weightsLicense)
                    InfoRow(stringResource(R.string.ai_model_digest), manifest.sha256, code = true)
                    if (!installed && canImport) {
                        Detail(stringResource(R.string.ai_model_how, manifest.fileName, manifest.sourceUrl.removePrefix("https://"), manifest.revision.take(12)), maxLines = 6)
                    }
                    when (import) {
                        is ModelImportUi.Copying -> {
                            Meter(if (import.total > 0) import.done.toFloat() / import.total else 0f)
                            Supporting(stringResource(R.string.ai_model_copying, size(import.done), size(import.total)))
                        }
                        ModelImportUi.Verified -> Supporting(stringResource(R.string.ai_model_verified))
                        is ModelImportUi.WrongSize -> Supporting(stringResource(R.string.ai_model_wrong_size, size(import.actual), size(manifest.sizeBytes)))
                        ModelImportUi.WrongDigest -> Supporting(stringResource(R.string.ai_model_wrong_digest))
                        is ModelImportUi.Failed -> Supporting(stringResource(R.string.ai_model_failed, import.detail))
                        ModelImportUi.Idle -> Unit
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                        if (canImport && !installed) {
                            Button(onClick = onImport, enabled = import !is ModelImportUi.Copying) { Text(stringResource(R.string.ai_model_import)) }
                        }
                        if (installed) OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.ai_model_delete)) }
                    }
                }
            }
        }
        // The registry's own reason, for whoever wants the engineering detail.
        val reason = record?.availability?.reason
        if (!reason.isNullOrBlank()) {
            row { GroupContent { ExpandableSection(stringResource(R.string.ai_why)) { Detail(reason, maxLines = 6) } } }
        }
    }
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
        is AiTurn.Assistant -> Column(verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            ChatBubble(turn.text, fromUser = false)
            if (turn.clarification.isNotBlank()) ChatBubble("${stringResource(R.string.ai_clarification)}: ${turn.clarification}", fromUser = false)
        }
        is AiTurn.Tool -> ToolCallCard(turn)
        is AiTurn.Approval -> ApprovalCard(turn, onDecide)
        is AiTurn.Refusal -> RefusalCard(turn)
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
                Text(stringResource(R.string.ai_tool_request), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(stringResource(turn.status.label()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(turn.name, style = ZairenkaiTextStyles.code, maxLines = 2, overflow = TextOverflow.Ellipsis)
            turn.arguments.forEach { (k, v) -> InfoRow(k, v, code = true) }
            turn.facts.forEach { f -> InfoRow(stringResource(f.label()), f.value) }
            turn.detail?.let { Detail(it, maxLines = 4) }
        }
    }
}

@StringRes
private fun Fact.label(): Int = when (key) {
    FactKey.STATE -> R.string.ai_fact_state
    FactKey.VERSION -> R.string.ai_fact_version
    FactKey.REASONS -> R.string.ai_fact_reasons
    FactKey.CAPABILITIES -> R.string.ai_fact_capabilities
    FactKey.AVAILABLE -> R.string.ai_fact_available
    FactKey.HOTTEST -> R.string.ai_fact_hottest
    FactKey.FRAMEWORK_STATUS -> R.string.ai_fact_framework_status
    FactKey.BATTERY -> R.string.ai_fact_battery
    FactKey.ZONES -> R.string.ai_fact_zones
    FactKey.RUNNING -> R.string.ai_fact_running
    FactKey.ENABLED -> R.string.ai_fact_enabled
    FactKey.PROFILE -> R.string.ai_fact_profile
    FactKey.FPS -> R.string.ai_fact_fps
}

/** An answer that did not pass the checks, or a model that could not run: shown, never acted on. */
@Composable
fun RefusalCard(turn: AiTurn.Refusal) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainerLow, shape = ZairenkaiShapes.metric, border = BorderStroke(1.dp, cs.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ZairenkaiSpacing.l), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                Icon(ZairenkaiGlyphs.shield, contentDescription = null, modifier = Modifier.size(20.dp), tint = cs.onSurfaceVariant)
                Text(stringResource(R.string.ai_refusal), style = MaterialTheme.typography.labelLarge)
            }
            Detail(turn.reason, maxLines = 4)
        }
    }
}

/** A device change proposed by the model: Core's decision first, then the user's explicit answer. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
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
            turn.rules.forEach { Detail("• $it", maxLines = 2) }
            when (turn.state) {
                ApprovalState.PENDING -> FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                    // Core's dry run must allow the change before the user can approve it.
                    Button(onClick = { onDecide(turn.id, true) }, enabled = turn.coreVerdict == "allow") { Text(stringResource(R.string.ai_approve)) }
                    OutlinedButton(onClick = { onDecide(turn.id, false) }) { Text(stringResource(R.string.ai_reject)) }
                }
                ApprovalState.APPROVED -> Supporting(stringResource(R.string.ai_approval_approved))
                ApprovalState.REJECTED -> Supporting(stringResource(R.string.ai_approval_rejected))
                ApprovalState.APPLIED -> Supporting(stringResource(R.string.ai_approval_applied))
                ApprovalState.FAILED -> Supporting(stringResource(R.string.ai_approval_failed))
            }
            turn.result?.let { Detail(it, maxLines = 3) }
        }
    }
}
