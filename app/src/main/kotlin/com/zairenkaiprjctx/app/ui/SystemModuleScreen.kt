// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.design.Emphasis
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

@Composable
fun SystemModuleScreen(vm: SystemModuleViewModel, main: MainViewModel, snapshot: RegistrySnapshot, padding: PaddingValues) {
    val root by main.rootState.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    // Re-read on entry and on return (after a reboot, or from the manager app after a handoff).
    LifecycleResumeEffect(root) {
        vm.evaluateAll()
        onPauseOrDispose {}
    }
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
        item {
            SectionCard(stringResource(R.string.module_title), emphasis = Emphasis.HIGH) {
                Supporting(stringResource(R.string.module_intro))
                Supporting(stringResource(R.string.module_no_promise))
            }
        }
        if (root != RootState.GRANTED) {
            item {
                SectionCard(stringResource(R.string.root_access), trailing = { StatusBadge(AvailabilityState.ROOT_REQUIRED) }) {
                    InfoRow(stringResource(R.string.key_state), rootStateLabel(root))
                    Supporting(stringResource(R.string.module_root_why))
                    if (root != RootState.UNAVAILABLE) Button(onClick = main::requestRoot) { Text(stringResource(R.string.root_enable)) }
                }
            }
        }
        notice?.let { item { SectionCard(stringResource(R.string.module_handoff_title), emphasis = Emphasis.LOW) { Supporting(it) } } }
        if (!vm.packaged) {
            item { SectionCard(stringResource(R.string.module_title)) { Supporting(stringResource(R.string.module_not_packaged)) } }
        }
        items(vm.modules, key = { it.id }) { m -> ModuleCard(vm, m, snapshot) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModuleCard(vm: SystemModuleViewModel, m: BundledModule, snapshot: RegistrySnapshot) {
    val flow = vm.state(m.id) ?: return
    val s by flow.collectAsState()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirmRemove by rememberSaveable(m.id) { mutableStateOf(false) }
    SectionCard(m.name, trailing = { StatusBadge(s.phase.badge()) }) {
        InfoRow(stringResource(R.string.key_state), stringResource(s.phase.label()))
        if (s.detail.isNotBlank()) Supporting(s.detail)
        InfoRow(stringResource(R.string.module_version), "${m.version} (${m.versionCode})", code = true)
        InfoRow(stringResource(R.string.module_release), "${m.source.repository} @ ${m.source.commit.take(12)}", code = true)
        InfoRow(stringResource(R.string.module_source), stringResource(R.string.module_source_value))
        InfoRow(
            stringResource(R.string.module_distribution),
            stringResource(if (m.distribution == "distributable") R.string.module_distribution_ok else R.string.module_distribution_internal),
        )
        if (m.distribution != "distributable" && m.distributionReason.isNotBlank()) Supporting(m.distributionReason)
        InfoRow(stringResource(R.string.module_digest), m.sha256, code = true)
        InfoRow(
            stringResource(R.string.module_signature),
            when (val sig = vm.signature) {
                null -> stringResource(R.string.module_sig_none)
                is SignatureVerdict.Verified -> stringResource(R.string.module_sig_verified, sig.owner, sig.keyId.take(16))
                SignatureVerdict.Unsigned -> stringResource(R.string.module_sig_unsigned)
                SignatureVerdict.NoTrustedKey -> stringResource(R.string.module_sig_no_key)
                is SignatureVerdict.UnknownKey -> stringResource(R.string.module_sig_unknown)
                is SignatureVerdict.Invalid -> stringResource(R.string.module_sig_invalid)
            },
        )
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
        InfoRow(stringResource(R.string.module_reboot), stringResource(if (m.rebootRequired) R.string.module_reboot_yes else R.string.module_reboot_no))
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
            if (d.verdict == Verdict.EXPERIMENTAL) Supporting(stringResource(R.string.module_experimental_note))
            ExpandableSection(stringResource(R.string.module_checks, d.checks.size), initiallyExpanded = d.verdict == Verdict.INCOMPATIBLE) {
                d.checks.forEach { c -> InfoRow(stringResource(c.status.label()), c.detail) }
            }
        }
        s.verified?.let { v ->
            ExpandableSection(stringResource(R.string.module_verification), initiallyExpanded = true) {
                v.checks.forEach { Supporting("✓ $it") }
            }
        }
        ExpandableSection(stringResource(R.string.module_risks)) {
            m.risks.forEach { Supporting(it) }
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
        if (busy && s.phase in WORKING) ProgressRow(stringResource(s.phase.label()))

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
                    if (s.disabled) OutlinedButton(onClick = { vm.enable(m.id) }, enabled = !busy) { Text(stringResource(R.string.module_enable)) }
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
            Supporting(stringResource(R.string.module_reboot_next))
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
