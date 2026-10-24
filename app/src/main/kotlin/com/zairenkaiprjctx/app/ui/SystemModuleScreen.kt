// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.zairenkaiprjctx.app.design.CodeValue
import com.zairenkaiprjctx.app.design.Entrance
import com.zairenkaiprjctx.app.design.GroupHeader
import com.zairenkaiprjctx.app.design.GroupRow
import com.zairenkaiprjctx.app.design.HeroDefaults
import com.zairenkaiprjctx.app.design.InfoChip
import com.zairenkaiprjctx.app.design.LocalStatusColors
import com.zairenkaiprjctx.app.design.visual
import com.zairenkaiprjctx.app.design.SegmentedGroup
import com.zairenkaiprjctx.app.design.StatusGlyph
import com.zairenkaiprjctx.app.design.TileDefaults
import com.zairenkaiprjctx.app.design.TileGrid
import com.zairenkaiprjctx.app.design.ZairenkaiLogo
import com.zairenkaiprjctx.app.design.ZairenkaiShapes
import com.zairenkaiprjctx.app.design.entrance
import com.zairenkaiprjctx.app.design.middleEllipsis
import com.zairenkaiprjctx.app.design.rememberEntrance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.design.Emphasis
import com.zairenkaiprjctx.app.design.ClampText
import com.zairenkaiprjctx.app.design.Detail
import com.zairenkaiprjctx.app.design.ShapeBadge
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.unit.dp
import com.zairenkaiprjctx.app.design.HeroPanel
import com.zairenkaiprjctx.app.design.WorkingBar
import com.zairenkaiprjctx.app.design.ZairenkaiGlyphs
import com.zairenkaiprjctx.app.design.ExpandableSection
import com.zairenkaiprjctx.app.design.InfoRow
import com.zairenkaiprjctx.app.design.ProgressRow
import com.zairenkaiprjctx.app.design.SectionCard
import com.zairenkaiprjctx.app.design.StatusBadge
import com.zairenkaiprjctx.app.design.Supporting
import com.zairenkaiprjctx.app.design.ZairenkaiSpacing
import com.zairenkaiprjctx.app.design.ZairenkaiTextStyles
import com.zairenkaiprjctx.integration.exec.RootState
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.registry.RegistrySnapshot
import com.zairenkaiprjctx.integration.systemmodule.BundledModule
import com.zairenkaiprjctx.integration.systemmodule.CheckStatus
import com.zairenkaiprjctx.integration.systemmodule.InstallFlowState
import com.zairenkaiprjctx.integration.systemmodule.InstallPhase
import com.zairenkaiprjctx.integration.systemmodule.InstallSupport
import com.zairenkaiprjctx.integration.systemmodule.SignatureVerdict
import com.zairenkaiprjctx.integration.systemmodule.Verdict

/** The registry state a phase is shown with (icon + text, never colour alone). */
fun InstallPhase.badge(): AvailabilityState = when (this) {
    InstallPhase.ACTIVE -> AvailabilityState.AVAILABLE
    InstallPhase.DEGRADED, InstallPhase.INSTALL_FAILED, InstallPhase.INSTALLED_NOT_INITIALIZED -> AvailabilityState.INITIALIZATION_FAILED
    InstallPhase.UNSUPPORTED, InstallPhase.MANAGER_UNSUPPORTED -> AvailabilityState.UNSUPPORTED_DEVICE
    InstallPhase.ROOT_NOT_AVAILABLE, InstallPhase.ROOT_PERMISSION_REQUIRED, InstallPhase.ROOT_PERMISSION_DENIED -> AvailabilityState.ROOT_REQUIRED
    InstallPhase.MODULE_AVAILABLE, InstallPhase.MODULE_NOT_AVAILABLE, InstallPhase.READY_TO_INSTALL, InstallPhase.AWAITING_USER_CONFIRMATION, InstallPhase.HANDOFF_READY ->
        AvailabilityState.COMPONENT_NOT_INSTALLED
    else -> AvailabilityState.NOT_VERIFIED
}

@StringRes
fun InstallPhase.label(): Int = when (this) {
    InstallPhase.ROOT_NOT_AVAILABLE -> R.string.module_phase_root_not_available
    InstallPhase.ROOT_PERMISSION_REQUIRED -> R.string.module_phase_root_required
    InstallPhase.ROOT_PERMISSION_DENIED -> R.string.module_phase_root_denied
    InstallPhase.MANAGER_DETECTION -> R.string.module_phase_detection
    InstallPhase.MANAGER_UNSUPPORTED -> R.string.module_phase_manager_unsupported
    InstallPhase.MODULE_NOT_AVAILABLE -> R.string.module_phase_not_available
    InstallPhase.INSTALLED_NOT_INITIALIZED -> R.string.module_phase_not_initialized
    InstallPhase.COMPATIBILITY_CHECK -> R.string.module_phase_compatibility
    InstallPhase.MODULE_AVAILABLE -> R.string.module_phase_available
    InstallPhase.DOWNLOAD_PENDING -> R.string.module_phase_preparing
    InstallPhase.VERIFYING_ARTIFACT -> R.string.module_phase_verifying
    InstallPhase.READY_TO_INSTALL -> R.string.module_phase_ready
    InstallPhase.AWAITING_USER_CONFIRMATION -> R.string.module_phase_confirm
    InstallPhase.INSTALLING -> R.string.module_phase_installing
    InstallPhase.INSTALL_FAILED -> R.string.module_phase_failed
    InstallPhase.INSTALL_PENDING_REBOOT -> R.string.module_phase_pending_reboot
    InstallPhase.VERIFYING_INSTALLED_STATE -> R.string.module_phase_verifying_installed
    InstallPhase.ACTIVE -> R.string.module_phase_active
    InstallPhase.DEGRADED -> R.string.module_phase_degraded
    InstallPhase.UNSUPPORTED -> R.string.module_phase_unsupported
    InstallPhase.HANDOFF_READY -> R.string.module_phase_handoff
    InstallPhase.REMOVAL_PENDING_REBOOT -> R.string.module_phase_removal_pending
}

@StringRes
private fun CheckStatus.label(): Int = when (this) {
    CheckStatus.PASS -> R.string.module_check_pass
    CheckStatus.WARN -> R.string.module_check_warn
    CheckStatus.FAIL -> R.string.module_check_fail
    CheckStatus.INFO -> R.string.module_check_info
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SystemModuleScreen(vm: SystemModuleViewModel, main: MainViewModel, snapshot: RegistrySnapshot, padding: PaddingValues) {
    val root by main.rootState.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    // Re-read on entry and on return (after a reboot, or from the manager app after a handoff).
    LifecycleResumeEffect(root) {
        vm.evaluateAll()
        onPauseOrDispose {}
    }
    val entrance = rememberEntrance()
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
        if (root != RootState.GRANTED) {
            item {
                SectionCard(stringResource(R.string.root_access), Modifier.entrance(entrance, 1), trailing = { StatusBadge(AvailabilityState.ROOT_REQUIRED) }) {
                    InfoRow(stringResource(R.string.key_state), rootStateLabel(root))
                    Detail(stringResource(R.string.module_root_why))
                    if (root != RootState.UNAVAILABLE) Button(onClick = main::requestRoot) { Text(stringResource(R.string.root_enable)) }
                }
            }
        }
        notice?.let { item { SectionCard(stringResource(R.string.module_handoff_title), emphasis = Emphasis.LOW) { Detail(it) } } }
        if (!vm.packaged) {
            item {
                HeroPanel(stringResource(R.string.module_overline), stringResource(R.string.module_title), stringResource(R.string.module_not_packaged), decoration = MaterialShapes.Pill)
            }
        }
        vm.modules.forEach { m -> moduleItems(vm, m, snapshot, entrance) }
        item {
            Detail(stringResource(R.string.module_no_promise), Modifier.padding(horizontal = ZairenkaiSpacing.s), maxLines = 4)
        }
    }
}

/** One packaged module as screen sections: hero, journey and action, components, security, details. */
private fun LazyListScope.moduleItems(vm: SystemModuleViewModel, m: BundledModule, snapshot: RegistrySnapshot, entrance: Entrance) {
    item(key = "${m.id}-hero") { ModuleHero(vm, m, Modifier.entrance(entrance, 0)) }
    item(key = "${m.id}-journey") { ModuleJourney(vm, m, Modifier.entrance(entrance, 1)) }
    item(key = "${m.id}-components") { ModuleComponents(m, Modifier.entrance(entrance, 2)) }
    item(key = "${m.id}-security") { ModuleSecurity(vm, m, Modifier.entrance(entrance, 3)) }
    item(key = "${m.id}-details") { ModuleDetails(vm, m, snapshot, Modifier.entrance(entrance, 4)) }
}

@Composable
private fun rememberModuleState(vm: SystemModuleViewModel, m: BundledModule): InstallFlowState? {
    val flow = vm.state(m.id) ?: return null
    val s by flow.collectAsState()
    return s
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModuleHero(vm: SystemModuleViewModel, m: BundledModule, modifier: Modifier) {
    val s = rememberModuleState(vm, m) ?: return
    HeroPanel(
        overline = stringResource(R.string.module_overline),
        headline = m.name,
        supporting = null,
        modifier = modifier,
        colors = if (s.phase == InstallPhase.ACTIVE) HeroDefaults.primary() else HeroDefaults.status(s.phase.badge().visual().tone(LocalStatusColors.current)),
        decoration = MaterialShapes.Pill,
        trailing = { ZairenkaiLogo(64.dp, label = stringResource(R.string.brand_logo)) },
    ) {
        ClampText(stringResource(R.string.module_intro), style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
            StatusGlyph(s.phase.badge(), size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(s.phase.label()), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (s.detail.isNotBlank()) Detail(s.detail, maxLines = 3)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            InfoChip(stringResource(R.string.module_chip_version, m.version, m.versionCode), icon = ZairenkaiGlyphs.box)
            s.manager?.let { InfoChip("${it.family.displayName} ${it.versionName}", icon = ZairenkaiGlyphs.lock) }
            InfoChip(stringResource(if (m.rebootRequired) R.string.module_chip_reboot else R.string.module_reboot_no), icon = ZairenkaiGlyphs.loop)
        }
    }
}

/** The installation journey, root to active, the step the flow is in, and what to do next. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModuleJourney(vm: SystemModuleViewModel, m: BundledModule, modifier: Modifier) {
    val s = rememberModuleState(vm, m) ?: return
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirmRemove by rememberSaveable(m.id) { mutableStateOf(false) }
    val release by vm.moduleUpdate.collectAsStateWithLifecycle()
    SectionCard(stringResource(R.string.module_journey), modifier) {
        JourneySteps(s.phase)
        release?.takeIf { it.updateAvailable }?.latest?.let { r ->
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = ZairenkaiShapes.metric) {
                Column(Modifier.fillMaxWidth().padding(ZairenkaiSpacing.m), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs)) {
                    Text(stringResource(R.string.module_update_available, r.tag), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.module_update_how), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (busy && s.phase in WORKING) {
            // A real step is running: the wavy bar moves (none if reduced) and the step is named.
            WorkingBar()
            Supporting(stringResource(s.phase.label()))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            when (s.phase) {
                InstallPhase.MODULE_AVAILABLE -> {
                    Button(onClick = { vm.prepare(m.id) }, enabled = !busy) {
                        Text(stringResource(if (m.role == "base") R.string.module_install_base else R.string.module_install_optional))
                    }
                    if (m.role == "base") OutlinedButton(onClick = vm::decline, enabled = !busy) { Text(stringResource(R.string.module_not_now)) }
                }
                InstallPhase.READY_TO_INSTALL -> {
                    Button(onClick = { vm.requestConfirmation(m.id) }, enabled = !busy) { Text(stringResource(R.string.module_continue)) }
                    OutlinedButton(onClick = { vm.cancel(m.id) }, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
                }
                InstallPhase.HANDOFF_READY -> {
                    Button(onClick = { vm.handOff(m.id) }, enabled = !busy) {
                        Text(stringResource(R.string.module_open_manager, s.handoff?.managerName ?: ""))
                    }
                    OutlinedButton(onClick = { vm.cancel(m.id) }, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
                }
                InstallPhase.ACTIVE, InstallPhase.DEGRADED, InstallPhase.INSTALLED_NOT_INITIALIZED -> {
                    OutlinedButton(onClick = { vm.evaluate(m.id) }, enabled = !busy) { Text(stringResource(R.string.module_check_again)) }
                    if (s.disabled) Button(onClick = { vm.enable(m.id) }, enabled = !busy) { Text(stringResource(R.string.module_enable)) }
                    OutlinedButton(onClick = { confirmRemove = true }, enabled = !busy) { Text(stringResource(R.string.module_remove)) }
                }
                InstallPhase.INSTALL_FAILED, InstallPhase.INSTALL_PENDING_REBOOT, InstallPhase.REMOVAL_PENDING_REBOOT, InstallPhase.UNSUPPORTED,
                InstallPhase.MANAGER_UNSUPPORTED, InstallPhase.MODULE_NOT_AVAILABLE ->
                    OutlinedButton(onClick = { vm.evaluate(m.id) }, enabled = !busy) { Text(stringResource(R.string.module_check_again)) }
                else -> Unit
            }
            if (s.removalHandoff.isNotEmpty()) {
                OutlinedButton(onClick = { vm.openManager(s.removalHandoff) }) { Text(stringResource(R.string.module_open_manager, s.manager?.family?.displayName ?: "")) }
            }
        }
        if (s.phase == InstallPhase.INSTALL_PENDING_REBOOT || s.phase == InstallPhase.REMOVAL_PENDING_REBOOT) {
            Detail(stringResource(R.string.module_reboot_next))
        }
    }
    if (s.phase == InstallPhase.AWAITING_USER_CONFIRMATION) ConfirmInstall(vm, m, s)
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.module_remove_title, m.name)) },
            text = { Text(stringResource(R.string.module_remove_body, m.dataDir)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.uninstall(m.id) }) { Text(stringResource(R.string.module_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Where a phase sits on the journey (0 root … 4 active), and whether it is stuck there. */
internal fun InstallPhase.journeyStep(): Int = when (this) {
    InstallPhase.ROOT_NOT_AVAILABLE, InstallPhase.ROOT_PERMISSION_REQUIRED, InstallPhase.ROOT_PERMISSION_DENIED -> 0
    InstallPhase.MANAGER_DETECTION, InstallPhase.MANAGER_UNSUPPORTED, InstallPhase.COMPATIBILITY_CHECK,
    InstallPhase.UNSUPPORTED, InstallPhase.MODULE_NOT_AVAILABLE -> 1
    InstallPhase.MODULE_AVAILABLE, InstallPhase.DOWNLOAD_PENDING, InstallPhase.VERIFYING_ARTIFACT, InstallPhase.READY_TO_INSTALL,
    InstallPhase.AWAITING_USER_CONFIRMATION, InstallPhase.INSTALLING, InstallPhase.INSTALL_FAILED, InstallPhase.HANDOFF_READY -> 2
    InstallPhase.INSTALL_PENDING_REBOOT, InstallPhase.REMOVAL_PENDING_REBOOT -> 3
    InstallPhase.VERIFYING_INSTALLED_STATE, InstallPhase.ACTIVE, InstallPhase.DEGRADED, InstallPhase.INSTALLED_NOT_INITIALIZED -> 4
}

internal fun InstallPhase.journeyBlocked(): Boolean = this in setOf(
    InstallPhase.ROOT_NOT_AVAILABLE, InstallPhase.ROOT_PERMISSION_DENIED, InstallPhase.MANAGER_UNSUPPORTED, InstallPhase.UNSUPPORTED,
    InstallPhase.MODULE_NOT_AVAILABLE, InstallPhase.INSTALL_FAILED, InstallPhase.DEGRADED, InstallPhase.INSTALLED_NOT_INITIALIZED,
)

private val STEP_LABELS = listOf(
    R.string.module_step_root, R.string.module_step_check, R.string.module_step_install, R.string.module_step_reboot, R.string.module_step_active,
)

/**
 * Five steps joined by a line. A done step shows a check, the current one is filled and numbered
 * (or shows "!" when the flow is stuck there), later ones are outlined: the state is in the mark
 * and in the spoken description, never in colour alone.
 */
@Composable
private fun JourneySteps(phase: InstallPhase) {
    val current = phase.journeyStep()
    val blocked = phase.journeyBlocked()
    val done = phase == InstallPhase.ACTIVE
    val cs = MaterialTheme.colorScheme
    val labels = STEP_LABELS.map { stringResource(it) }
    val spoken = stringResource(R.string.module_journey_spoken, current + 1, labels.size, labels[current])
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken }, verticalAlignment = Alignment.Top) {
        labels.forEachIndexed { i, label ->
            val state = when {
                i < current || (i == current && done) -> 0 // done
                i == current -> 1 // current
                else -> 2 // later
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(2.dp).background(if (i == 0) Color.Transparent else if (i <= current) cs.primary else cs.outlineVariant))
                    val container = when {
                        state == 1 && blocked -> cs.errorContainer
                        state == 2 -> Color.Transparent
                        else -> cs.primary
                    }
                    val content = when {
                        state == 1 && blocked -> cs.onErrorContainer
                        state == 2 -> cs.onSurfaceVariant
                        else -> cs.onPrimary
                    }
                    Surface(
                        shape = CircleShape,
                        color = container,
                        contentColor = content,
                        // Same size for every step so the connectors line up; the current one gets a ring.
                        border = when (state) {
                            2 -> BorderStroke(1.5.dp, cs.outline)
                            1 -> BorderStroke(3.dp, if (blocked) cs.error else cs.primaryContainer)
                            else -> null
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when {
                                state == 0 -> Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                state == 1 && blocked -> Text("!", style = MaterialTheme.typography.labelLarge)
                                else -> Text("${i + 1}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Box(Modifier.weight(1f).height(2.dp).background(if (i == labels.lastIndex) Color.Transparent else if (i < current) cs.primary else cs.outlineVariant))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (state == 1) FontWeight.Bold else null,
                    color = if (state == 2) cs.onSurfaceVariant else cs.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A program the module carries, read from the build record (its bin/ entries and their digests). */
private data class ModuleProgram(val binary: String, val sha256: String, @StringRes val role: Int?)

private fun BundledModule.programs(): List<ModuleProgram> = entries.filterKeys { it.startsWith("bin/") }.toSortedMap().map { (path, digest) ->
    val name = path.removePrefix("bin/")
    ModuleProgram(
        name,
        digest,
        when (name) {
            "zcored" -> R.string.module_prog_zcored
            "zfasd" -> R.string.module_prog_zfasd
            "ztune" -> R.string.module_prog_ztune
            else -> null
        },
    )
}

@Composable
private fun ModuleComponents(m: BundledModule, modifier: Modifier) {
    val programs = m.programs()
    if (programs.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
        GroupHeader(stringResource(R.string.module_components), icon = ZairenkaiGlyphs.nodes)
        Detail(pluralStringResource(R.plurals.module_components_summary, programs.size, programs.size), Modifier.padding(horizontal = ZairenkaiSpacing.s))
        TileGrid(programs) { p, mod -> ProgramTile(p, mod) }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProgramTile(p: ModuleProgram, modifier: Modifier) {
    val colors = when (p.binary) {
        "zcored" -> TileDefaults.primary()
        "zfasd" -> TileDefaults.tertiary()
        else -> TileDefaults.secondary()
    }
    val glyph = when (p.binary) {
        "zcored" -> ZairenkaiGlyphs.nodes
        "zfasd" -> ZairenkaiGlyphs.bolt
        "ztune" -> ZairenkaiGlyphs.sliders
        else -> ZairenkaiGlyphs.box
    }
    Surface(color = colors.container, contentColor = colors.content, shape = ZairenkaiShapes.metric, modifier = modifier) {
        Column(Modifier.padding(ZairenkaiSpacing.l), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            ShapeBadge(glyph, MaterialShapes.Cookie9Sided, colors.badge, colors.onBadge)
            Text(p.binary, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.role != null) {
                Text(stringResource(p.role), style = MaterialTheme.typography.bodySmall, color = colors.content.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text(middleEllipsis(p.sha256, 6), style = ZairenkaiTextStyles.code, color = colors.content.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

/** What protects the module, each line from the build record or the last verification. */
@Composable
private fun ModuleSecurity(vm: SystemModuleViewModel, m: BundledModule, modifier: Modifier) {
    val s = rememberModuleState(vm, m) ?: return
    val sig = vm.signature
    val sigText = when (sig) {
        null -> stringResource(R.string.module_sig_none)
        is SignatureVerdict.Verified -> stringResource(R.string.module_sig_verified, sig.owner, sig.keyId.take(16))
        SignatureVerdict.Unsigned -> stringResource(R.string.module_sig_unsigned)
        SignatureVerdict.NoTrustedKey -> stringResource(R.string.module_sig_no_key)
        is SignatureVerdict.UnknownKey -> stringResource(R.string.module_sig_unknown)
        is SignatureVerdict.Invalid -> stringResource(R.string.module_sig_invalid)
    }
    val manifest = "SHA256SUMS" in m.entries
    val verifiedChecks = s.verified?.checks.orEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
    GroupHeader(stringResource(R.string.module_security), icon = ZairenkaiGlyphs.shield)
    Detail(stringResource(R.string.module_security_summary), Modifier.padding(horizontal = ZairenkaiSpacing.s))
    SegmentedGroup {
        row {
            GroupRow(
                stringResource(R.string.module_signature),
                supporting = sigText,
                icon = if (sig is SignatureVerdict.Verified) ZairenkaiGlyphs.approve else ZairenkaiGlyphs.lock,
                emphasized = sig is SignatureVerdict.Verified,
            )
        }
        row {
            GroupRow(
                stringResource(R.string.module_digest),
                supporting = stringResource(R.string.module_sec_digest_body, android.text.format.Formatter.formatFileSize(LocalContext.current, m.size)),
                icon = ZairenkaiGlyphs.lock,
                action = { CodeValue(m.sha256) },
            )
        }
        row {
            GroupRow(
                stringResource(R.string.module_sec_checked),
                supporting = if (verifiedChecks.isEmpty()) stringResource(R.string.module_sec_checked_pending)
                else verifiedChecks.joinToString("\n") { "✓ $it" },
                icon = ZairenkaiGlyphs.document,
                emphasized = verifiedChecks.isNotEmpty(),
            )
        }
        row {
            GroupRow(
                stringResource(R.string.module_sec_device),
                supporting = stringResource(if (manifest) R.string.module_sec_device_yes else R.string.module_sec_device_no),
                icon = ZairenkaiGlyphs.shield,
                emphasized = manifest,
            )
        }
        row {
            GroupRow(
                stringResource(R.string.module_distribution),
                supporting = if (m.distribution == "distributable") stringResource(R.string.module_distribution_ok)
                else listOf(stringResource(R.string.module_distribution_internal), m.distributionReason).filter { it.isNotBlank() }.joinToString("\n"),
                icon = ZairenkaiGlyphs.box,
            )
        }
    }
    }
}

@Composable
private fun ModuleDetails(vm: SystemModuleViewModel, m: BundledModule, snapshot: RegistrySnapshot, modifier: Modifier) {
    val s = rememberModuleState(vm, m) ?: return
    SectionCard(stringResource(R.string.module_details), modifier, emphasis = Emphasis.LOW) {
        InfoRow(stringResource(R.string.module_release), "${m.source.repository} @ ${m.source.commit.take(12)}", code = true)
        InfoRow(stringResource(R.string.module_source), stringResource(R.string.module_source_value))
        s.manager?.let { id ->
            val how = stringResource(
                when (id.install) {
                    InstallSupport.DIRECT -> R.string.module_install_direct
                    InstallSupport.HANDOFF_ONLY, InstallSupport.UNVERIFIED -> R.string.module_install_handoff
                },
            )
            InfoRow(stringResource(R.string.module_manager), "${id.family.displayName} ${id.versionName} · $how")
        }
        InfoRow(stringResource(R.string.module_features), m.features.joinToString { snapshot.byId(it)?.name ?: it })
        val deps = m.requirements.dependsOn + m.requirements.requiredPaths
        if (deps.isNotEmpty()) InfoRow(stringResource(R.string.module_requires), deps.joinToString(), code = true)
        if (m.requirements.conflicts.isNotEmpty()) InfoRow(stringResource(R.string.module_conflicts), m.requirements.conflicts.joinToString { it.id }, code = true)
        s.decision?.let { d ->
            InfoRow(
                stringResource(R.string.module_compatibility),
                stringResource(
                    when (d.verdict) {
                        Verdict.COMPATIBLE -> R.string.module_verdict_compatible
                        Verdict.EXPERIMENTAL -> R.string.module_verdict_experimental
                        Verdict.INCOMPATIBLE -> R.string.module_verdict_incompatible
                        Verdict.ALREADY_INSTALLED -> R.string.module_verdict_installed
                        Verdict.PENDING_REBOOT -> R.string.module_verdict_pending
                    },
                ),
            )
            if (d.verdict == Verdict.EXPERIMENTAL) Detail(stringResource(R.string.module_experimental_note))
            ExpandableSection(stringResource(R.string.module_checks, d.checks.size), initiallyExpanded = d.verdict == Verdict.INCOMPATIBLE) {
                d.checks.forEach { c -> InfoRow(stringResource(c.status.label()), c.detail) }
            }
        }
        ExpandableSection(stringResource(R.string.module_risks)) {
            m.risks.forEach { Detail(it) }
            val l = m.lifecycle
            l.destinations.forEach { (k, v) -> InfoRow(k, v, code = true) }
            listOf(
                R.string.module_life_startup to l.startup, R.string.module_life_shutdown to l.shutdown,
                R.string.module_life_uninstall to l.uninstall, R.string.module_life_recovery to l.recovery,
                R.string.module_life_kernel to l.kernel,
            ).filter { it.second.isNotBlank() }.forEach { (label, text) -> InfoRow(stringResource(label), text) }
        }
        if (s.log.isNotBlank()) {
            ExpandableSection(stringResource(R.string.module_log)) { Text(s.log, style = ZairenkaiTextStyles.code) }
        }
    }
}

@Composable
private fun ConfirmInstall(vm: SystemModuleViewModel, m: BundledModule, s: InstallFlowState) {
    val experimental = s.decision?.verdict == Verdict.EXPERIMENTAL
    var ack by rememberSaveable(m.id) { mutableStateOf(false) }
    val manager = s.manager?.family?.displayName ?: ""
    AlertDialog(
        onDismissRequest = { vm.cancel(m.id) },
        title = { Text(stringResource(R.string.module_confirm_title, m.name, m.version)) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                Text(
                    stringResource(
                        if (s.decision?.install == InstallSupport.DIRECT) R.string.module_confirm_direct else R.string.module_confirm_handoff,
                        manager,
                    ),
                )
                if (m.rebootRequired) Text(stringResource(R.string.module_confirm_reboot))
                s.decision?.upgradeFrom?.let { Text(stringResource(R.string.module_confirm_upgrade, it)) }
                if (experimental) {
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = ack, role = Role.Checkbox, onValueChange = { ack = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = ack, onCheckedChange = null)
                        Text(stringResource(R.string.module_confirm_experimental), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.confirm(m.id, ack) }, enabled = !experimental || ack) { Text(stringResource(R.string.module_confirm_install)) }
        },
        dismissButton = { TextButton(onClick = { vm.cancel(m.id) }) { Text(stringResource(R.string.action_cancel)) } },
    )
    LaunchedEffect(s.phase) { if (s.phase != InstallPhase.AWAITING_USER_CONFIRMATION) ack = false }
}

private val WORKING = setOf(
    InstallPhase.MANAGER_DETECTION, InstallPhase.COMPATIBILITY_CHECK, InstallPhase.DOWNLOAD_PENDING,
    InstallPhase.VERIFYING_ARTIFACT, InstallPhase.INSTALLING, InstallPhase.VERIFYING_INSTALLED_STATE,
)
