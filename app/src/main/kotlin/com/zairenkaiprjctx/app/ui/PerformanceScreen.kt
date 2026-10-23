// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.design.ConfirmDialog
import com.zairenkaiprjctx.app.design.Emphasis
import com.zairenkaiprjctx.app.design.ExpandableSection
import com.zairenkaiprjctx.app.design.GroupContent
import com.zairenkaiprjctx.app.design.GroupRow
import com.zairenkaiprjctx.app.design.HeroPanel
import com.zairenkaiprjctx.app.design.InfoRow
import com.zairenkaiprjctx.app.design.Meter
import com.zairenkaiprjctx.app.design.MetricTile
import com.zairenkaiprjctx.app.design.ProgressRow
import com.zairenkaiprjctx.app.design.RingGauge
import com.zairenkaiprjctx.app.design.SectionCard
import com.zairenkaiprjctx.app.design.SegmentedGroup
import com.zairenkaiprjctx.app.design.StatusBadge
import com.zairenkaiprjctx.app.design.Supporting
import com.zairenkaiprjctx.app.design.UnavailableCard
import com.zairenkaiprjctx.app.design.ZairenkaiGlyphs
import com.zairenkaiprjctx.app.design.ZairenkaiSpacing
import com.zairenkaiprjctx.integration.core.CoreCall
import com.zairenkaiprjctx.integration.core.CoreOperation
import com.zairenkaiprjctx.integration.core.FasProfileName
import com.zairenkaiprjctx.integration.core.TuningReading
import com.zairenkaiprjctx.integration.core.TuningTelemetry
import com.zairenkaiprjctx.integration.core.tuningNotInstalled
import com.zairenkaiprjctx.integration.fas.FasCall
import com.zairenkaiprjctx.integration.fas.FasCommand
import com.zairenkaiprjctx.integration.fas.FasStatus
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.registry.RegistrySnapshot

/** Plain-language name and effect of each FAS profile (Zairenkai-FAS docs/CONFIGURATION.md, policy table). */
@StringRes
fun FasProfileName.title(): Int = when (this) {
    FasProfileName.POWER_SAVER -> R.string.profile_power_saver
    FasProfileName.BALANCED -> R.string.profile_balanced
    FasProfileName.PERFORMANCE -> R.string.profile_performance
    FasProfileName.FAST_RESPONSE -> R.string.profile_fast_response
    FasProfileName.ADAPTIVE -> R.string.profile_adaptive
}

@StringRes
fun FasProfileName.description(): Int = when (this) {
    FasProfileName.POWER_SAVER -> R.string.profile_power_saver_body
    FasProfileName.BALANCED -> R.string.profile_balanced_body
    FasProfileName.PERFORMANCE -> R.string.profile_performance_body
    FasProfileName.FAST_RESPONSE -> R.string.profile_fast_response_body
    FasProfileName.ADAPTIVE -> R.string.profile_adaptive_body
}

/** The profile zfasd reports, by its plain name when it is one the app knows. */
@Composable
fun profileName(wire: String?): String =
    FasProfileName.fromWire(wire)?.let { stringResource(it.title()) } ?: wire ?: stringResource(R.string.not_reported)

@Composable
fun PerformanceScreen(vm: PerformanceViewModel, snapshot: RegistrySnapshot, padding: PaddingValues, goToRoot: () -> Unit) {
    LaunchedEffect(snapshot.refreshedAtMs) { vm.load() }
    val health by vm.coreHealth.collectAsStateWithLifecycle()
    val fas by vm.fasStatus.collectAsStateWithLifecycle()
    val action by vm.actionState.collectAsStateWithLifecycle()
    val tuning by vm.tuning.collectAsStateWithLifecycle()
    val coreReady = health is CoreCall.Ok
    val current = (fas as? FasCall.Ok)?.value

    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
        item {
            when (val f = fas) {
                is FasCall.Ok -> FasHero(f.value)
                null -> SectionCard(stringResource(R.string.perf_fas)) { ProgressRow(stringResource(R.string.loading)) }
                else -> UnavailableCard(snapshot.byId("fas.status"), f.describeFailure()) {
                    if (f is FasCall.NoRoot) OutlinedButton(onClick = goToRoot) { Text(stringResource(R.string.root_settings)) }
                }
            }
        }
        item { Profiles(vm, coreReady, fasReady = current != null, current) }
        item { ServiceControls(vm, coreReady, current) }
        item {
            when (val h = health) {
                is CoreCall.Ok -> SegmentedGroup(title = stringResource(R.string.perf_core)) {
                    row {
                        GroupRow(
                            h.value.state,
                            supporting = stringResource(R.string.core_version, h.value.version, h.value.coreApi, h.value.protocol),
                            icon = ZairenkaiGlyphs.tiles,
                            status = AvailabilityState.AVAILABLE,
                        )
                    }
                    row {
                        GroupContent {
                            InfoRow(stringResource(R.string.key_policy), h.value.policyVersion, code = true)
                            InfoRow(stringResource(R.string.key_leases), h.value.leases.toString(), numeric = true)
                            h.value.reasons.forEach { Supporting("• $it") }
                        }
                    }
                }
                null -> SectionCard(stringResource(R.string.perf_core)) { ProgressRow(stringResource(R.string.loading)) }
                else -> UnavailableCard(snapshot.byId("core.actions"), h.describeFailure()) {
                    if (h is CoreCall.NoRoot) OutlinedButton(onClick = goToRoot) { Text(stringResource(R.string.root_settings)) }
                }
            }
        }
        // Tuning telemetry exists only through Core: nothing to show while Core does not answer.
        if (coreReady) item { TuningSection(tuning, snapshot) }
    }
    ActionDialog(action, vm)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FasHero(s: FasStatus) {
    val step = s.step
    val fps = s.displayableFps
    val target = step?.targetFps
    HeroPanel(
        overline = stringResource(R.string.perf_fas),
        headline = profileName(s.profile),
        supporting = stringResource(if (s.observeOnly) R.string.fas_observe_only else if (s.running) R.string.fas_running else R.string.fas_stopped),
        trailing = {
            RingGauge(
                progress = if (fps != null && target != null && target > 0) (fps / target).toFloat() else null,
                value = fps?.let { "%.0f".format(it) } ?: "—",
                caption = stringResource(R.string.fas_fps_short),
                label = when {
                    fps == null -> stringResource(R.string.fas_fps_unavailable)
                    target != null -> stringResource(R.string.fas_ring_label, "%.1f".format(fps), "%.0f".format(target))
                    else -> stringResource(R.string.fas_ring_label_no_target, "%.1f".format(fps))
                },
            )
        },
    ) {
        // The ring carries the frame rate; this line says where it comes from, or why there is none.
        Supporting(
            when {
                s.telemetryError != null -> stringResource(R.string.fas_telemetry_error, s.telemetryError!!)
                fps == null -> stringResource(R.string.fas_fps_unavailable)
                else -> target?.let { stringResource(R.string.fas_target, "%.0f".format(it)) } ?: stringResource(R.string.fas_fps_measured)
            },
        )
        // Missed frames only mean something once frames were measured.
        val miss = step?.stats?.missRatio
        if (miss != null && fps != null && s.telemetryError == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
                MetricTile(stringResource(R.string.fas_missed), "%.1f %%".format(miss * 100), null, Modifier.weight(1f), meter = miss.toFloat())
                MetricTile(stringResource(R.string.fas_fps), "%.1f".format(fps), null, Modifier.weight(1f), emphasis = Emphasis.HIGH)
            }
        }
        ExpandableSection(stringResource(R.string.fas_details)) {
            InfoRow(stringResource(R.string.fas_daemon), stringResource(if (s.running) R.string.fas_running else R.string.fas_stopped))
            InfoRow(stringResource(R.string.fas_enabled), s.enabled?.let { stringResource(if (it) R.string.on else R.string.off) } ?: stringResource(R.string.fas_enabled_unknown))
            InfoRow(stringResource(R.string.fas_default_profile), s.defaultProfile)
            InfoRow(stringResource(R.string.fas_foreground), s.packageName, code = true)
            if (step != null) {
                InfoRow(stringResource(R.string.fas_decision), "${step.decision.state}: ${step.decision.reason}")
                step.decision.bottleneck?.let { InfoRow(stringResource(R.string.fas_bottleneck), it) }
                step.thermal?.let { InfoRow(stringResource(R.string.fas_thermal_cap), "${it.levelCap ?: "?"}${if (it.suspended) " · suspended" else ""} · ${it.reason}") }
            }
            s.errors.forEach { Supporting("• $it") }
        }
    }
}

/**
 * Profiles as a list with what each one does. Tapping a profile never changes anything directly:
 * it opens Core's dry-run preview (or the direct-command confirmation), and the check mark only
 * moves once zfasd reports the new profile.
 */
@Composable
private fun Profiles(vm: PerformanceViewModel, coreReady: Boolean, fasReady: Boolean, current: FasStatus?) {
    SegmentedGroup(title = stringResource(R.string.controls_profile)) {
        row {
            GroupContent {
                Supporting(
                    stringResource(
                        when {
                            coreReady -> R.string.controls_via_core
                            fasReady -> R.string.controls_direct
                            else -> R.string.controls_none
                        },
                    ),
                )
            }
        }
        if (coreReady || fasReady) {
            FasProfileName.entries.forEach { p ->
                row {
                    val isCurrent = current?.profile == p.wire
                    GroupRow(
                        headline = stringResource(p.title()) + if (p.experimental) " · " + stringResource(R.string.experimental) else "",
                        supporting = stringResource(p.description()),
                        icon = ZairenkaiGlyphs.sliders,
                        emphasized = isCurrent,
                        onClick = { if (coreReady) vm.preview(CoreOperation.FasSetProfile(p)) else vm.setProfileDirect(p) },
                        modifier = Modifier.semantics { selected = isCurrent; role = Role.RadioButton },
                        trailing = if (isCurrent) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/**
 * FAS on/off and battery saver as switches showing the state the provider reports; flipping one
 * opens the preview, and the switch moves only when zfasd (or Android) reports the new state.
 */
@Composable
private fun ServiceControls(vm: PerformanceViewModel, coreReady: Boolean, current: FasStatus?) {
    if (!coreReady && current == null) return
    SegmentedGroup(title = stringResource(R.string.controls)) {
        row {
            val enabled = current?.enabled
            GroupRow(
                stringResource(R.string.fas_switch),
                supporting = enabled?.let { stringResource(if (it) R.string.fas_switch_on else R.string.fas_switch_off) } ?: stringResource(R.string.fas_enabled_unknown),
                icon = ZairenkaiGlyphs.gauge,
                trailing = {
                    if (enabled != null) {
                        Switch(checked = enabled, onCheckedChange = { on ->
                            if (coreReady) vm.preview(CoreOperation.FasSetEnabled(on)) else vm.commandDirect(if (on) FasCommand.ENABLE else FasCommand.DISABLE)
                        })
                    }
                },
            )
        }
        if (current?.enabled == null) {
            // State unknown: explicit buttons instead of a switch that would have to guess.
            row {
                GroupContent {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                        OutlinedButton(onClick = { if (coreReady) vm.preview(CoreOperation.FasSetEnabled(true)) else vm.commandDirect(FasCommand.ENABLE) }) { Text(stringResource(R.string.fas_enable)) }
                        OutlinedButton(onClick = { if (coreReady) vm.preview(CoreOperation.FasSetEnabled(false)) else vm.commandDirect(FasCommand.DISABLE) }) { Text(stringResource(R.string.fas_disable)) }
                    }
                }
            }
        }
        if (coreReady) {
            row {
                val on = vm.powerSave
                GroupRow(
                    stringResource(R.string.battery_saver),
                    supporting = stringResource(R.string.battery_saver_via_core),
                    icon = ZairenkaiGlyphs.battery,
                    trailing = { Switch(checked = on, onCheckedChange = { vm.preview(CoreOperation.SetLowPowerMode(it)) }) },
                )
            }
        } else {
            row {
                GroupRow(
                    stringResource(R.string.fas_reset),
                    supporting = stringResource(R.string.fas_reset_body),
                    icon = ZairenkaiGlyphs.sliders,
                    trailing = { TextButton(onClick = { vm.commandDirect(FasCommand.RESET) }) { Text(stringResource(R.string.fas_reset_action)) } },
                )
            }
        }
    }
}

/** ztune telemetry as Core returns it: only readings ztune marked `ok` show a value. */
@Composable
private fun TuningSection(result: CoreCall<TuningTelemetry>?, snapshot: RegistrySnapshot) {
    val record = snapshot.byId("tuning.telemetry")
    when (result) {
        null -> SectionCard(stringResource(R.string.tuning_title)) { ProgressRow(stringResource(R.string.loading)) }
        is CoreCall.Ok -> {
            val t = result.value
            SegmentedGroup(title = stringResource(R.string.tuning_title)) {
                row { ReadingRow(stringResource(R.string.tuning_cpu), t.cpu.load, percent = true) }
                row {
                    val used = t.memory.used.long
                    val total = t.memory.total.long
                    GroupContent {
                        Text(stringResource(R.string.tuning_memory), style = MaterialTheme.typography.titleSmall)
                        if (used != null && total != null && total > 0) {
                            Meter(used.toFloat() / total)
                            Supporting(stringResource(R.string.tuning_memory_value, gib(used), gib(total)))
                        } else {
                            Supporting(readingProblem(t.memory.used))
                        }
                    }
                }
                row {
                    GroupContent {
                        InfoRow(stringResource(R.string.tuning_gpu_governor), t.gpu.governor.text ?: readingProblem(t.gpu.governor))
                        InfoRow(stringResource(R.string.tuning_gpu_freq), t.gpu.curFreq.long?.let { "${it / 1_000_000} MHz" } ?: readingProblem(t.gpu.curFreq), numeric = true)
                        t.storage.firstOrNull()?.let { InfoRow(stringResource(R.string.tuning_io, it.device), it.scheduler.text ?: readingProblem(it.scheduler), code = true) }
                        InfoRow(stringResource(R.string.tuning_soc), t.soc.family)
                        Supporting(stringResource(R.string.tuning_source, t.component.version))
                    }
                }
            }
        }
        is CoreCall.Error -> UnavailableCard(record, if (result.tuningNotInstalled) stringResource(R.string.tuning_not_installed) else result.describeFailure())
        else -> UnavailableCard(record, result.describeFailure())
    }
}

@Composable
private fun ReadingRow(label: String, r: TuningReading, percent: Boolean) {
    GroupContent {
        Text(label, style = MaterialTheme.typography.titleSmall)
        val v = r.number
        if (v != null) {
            if (percent) Meter((v / 100).toFloat())
            Supporting(if (percent) "%.0f %%".format(v) else "%.1f".format(v))
        } else {
            Supporting(readingProblem(r))
        }
    }
}

private fun gib(bytes: Long): String = "%.1f GiB".format(bytes / 1_073_741_824.0)

/** Why a reading has no value: ztune's status and detail, never a substituted number. */
private fun readingProblem(r: TuningReading): String =
    listOfNotNull(r.status, r.detail).joinToString(": ")

@Composable
private fun ActionDialog(state: ActionState, vm: PerformanceViewModel) {
    // One confirmation per dialog instance: a second tap cannot submit twice.
    var confirmed by remember(state) { mutableStateOf(false) }
    when (state) {
        ActionState.Idle -> Unit
        is ActionState.Working -> ConfirmDialog(
            title = state.label.resolve(),
            body = { ProgressRow(stringResource(R.string.action_waiting)) },
            confirmLabel = "",
            onConfirm = null,
            onDismiss = {},
        )
        is ActionState.Preview -> {
            val d = state.result.decision
            val allowed = d?.verdict == "allow" && state.result.outcome != "rejected"
            ConfirmDialog(
                title = state.label.resolve(),
                body = {
                    Column(verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs)) {
                        Text(stringResource(R.string.dry_run_result, state.result.outcome, d?.verdict ?: "—"))
                        d?.rules?.forEach { Supporting("${it.verdict.uppercase()} · ${it.rule}: ${it.explanation}") }
                        if (state.result.message.isNotBlank()) Supporting(state.result.message)
                        if (!allowed) Text(stringResource(R.string.dry_run_refused), color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmLabel = stringResource(R.string.apply),
                onConfirm = if (allowed) {
                    { if (!confirmed) { confirmed = true; vm.apply(state.op) } }
                } else {
                    null
                },
                onDismiss = vm::dismiss,
            )
        }
        is ActionState.ConfirmDirect -> ConfirmDialog(
            title = state.label.resolve(),
            body = { Text(stringResource(R.string.direct_confirm)) },
            confirmLabel = stringResource(R.string.send),
            onConfirm = { if (!confirmed) { confirmed = true; vm.applyDirect(state.label, state.run, state.requestedProfile) } },
            onDismiss = vm::dismiss,
            destructive = true,
        )
        is ActionState.Done -> {
            val check = state.profile?.let { p ->
                when {
                    p.confirmed -> stringResource(R.string.profile_confirmed, p.requested)
                    p.reported == null -> stringResource(R.string.profile_unreadable)
                    else -> stringResource(R.string.profile_not_reflected, p.reported, p.requested)
                }
            }
            val head = stringResource(if (state.succeeded) R.string.action_done else R.string.action_not_applied) + " · " + state.message
            ResultDialog(state.label.resolve(), listOfNotNull(head, check).joinToString("\n"), vm::dismiss)
        }
        is ActionState.Error -> ResultDialog(state.label.resolve(), stringResource(R.string.action_failed) + " · " + state.detail, vm::dismiss)
    }
}

@Composable
private fun ResultDialog(title: String, body: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}
