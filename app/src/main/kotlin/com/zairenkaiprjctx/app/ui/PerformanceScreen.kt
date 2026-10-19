package com.zairenkaiprjctx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.design.ConfirmDialog
import com.zairenkaiprjctx.app.design.Emphasis
import com.zairenkaiprjctx.app.design.HeroStatusCard
import com.zairenkaiprjctx.app.design.InfoRow
import com.zairenkaiprjctx.app.design.MetricTile
import com.zairenkaiprjctx.app.design.ProgressRow
import com.zairenkaiprjctx.app.design.SectionCard
import com.zairenkaiprjctx.app.design.StatusBadge
import com.zairenkaiprjctx.app.design.Supporting
import com.zairenkaiprjctx.app.design.UnavailableCard
import com.zairenkaiprjctx.app.design.ZairenkaiSpacing
import com.zairenkaiprjctx.integration.core.CoreCall
import com.zairenkaiprjctx.integration.core.CoreOperation
import com.zairenkaiprjctx.integration.core.FasProfileName
import com.zairenkaiprjctx.integration.fas.FasCall
import com.zairenkaiprjctx.integration.fas.FasCommand
import com.zairenkaiprjctx.integration.fas.FasStatus
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.registry.RegistrySnapshot

@Composable
fun PerformanceScreen(vm: PerformanceViewModel, snapshot: RegistrySnapshot, padding: PaddingValues, goToRoot: () -> Unit) {
    LaunchedEffect(snapshot.refreshedAtMs) { vm.load() }
    val health by vm.coreHealth.collectAsStateWithLifecycle()
    val fas by vm.fasStatus.collectAsStateWithLifecycle()
    val action by vm.actionState.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
        item {
            when (val f = fas) {
                is FasCall.Ok -> FasHero(f.value)
                null -> SectionCard(stringResource(R.string.perf_fas)) { ProgressRow(stringResource(R.string.loading)) }
                else -> UnavailableCard(snapshot.byId("fas.status"), f.describeFailure()) {
                    if (f is FasCall.NoRoot) OutlinedButton(onClick = goToRoot) { Text(stringResource(R.string.root_settings)) }
                }
            }
        }
        item {
            when (val h = health) {
                is CoreCall.Ok -> SectionCard(stringResource(R.string.perf_core), trailing = { StatusBadge(AvailabilityState.AVAILABLE) }) {
                    InfoRow(stringResource(R.string.key_state), h.value.state)
                    InfoRow(stringResource(R.string.key_version), stringResource(R.string.core_version, h.value.version, h.value.coreApi, h.value.protocol))
                    InfoRow(stringResource(R.string.key_policy), h.value.policyVersion, code = true)
                    InfoRow(stringResource(R.string.key_leases), h.value.leases.toString(), numeric = true)
                    h.value.reasons.forEach { Supporting("• $it") }
                }
                null -> SectionCard(stringResource(R.string.perf_core)) { ProgressRow(stringResource(R.string.loading)) }
                else -> UnavailableCard(snapshot.byId("core.actions"), h.describeFailure()) {
                    if (h is CoreCall.NoRoot) OutlinedButton(onClick = goToRoot) { Text(stringResource(R.string.root_settings)) }
                }
            }
        }
        item { Controls(vm, coreReady = health is CoreCall.Ok, fasReady = fas is FasCall.Ok, current = (fas as? FasCall.Ok)?.value) }
    }
    ActionDialog(action, vm)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FasHero(s: FasStatus) {
    val step = s.step
    HeroStatusCard(
        overline = stringResource(R.string.perf_fas),
        headline = s.profile ?: stringResource(R.string.not_reported),
        supporting = if (s.observeOnly) stringResource(R.string.fas_observe_only) else null,
        state = AvailabilityState.AVAILABLE,
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
            val fps = s.displayableFps
            MetricTile(
                label = stringResource(R.string.fas_fps),
                value = fps?.let { "%.1f".format(it) } ?: "—",
                caption = when {
                    s.telemetryError != null -> stringResource(R.string.fas_telemetry_error, s.telemetryError!!)
                    fps == null -> stringResource(R.string.fas_fps_unavailable)
                    else -> step?.targetFps?.let { stringResource(R.string.fas_target, "%.0f".format(it)) } ?: stringResource(R.string.fas_fps_measured)
                },
                emphasis = Emphasis.HIGH,
            )
            step?.stats?.missRatio?.let {
                if (s.telemetryError == null) MetricTile(stringResource(R.string.fas_missed), "%.1f %%".format(it * 100), null)
            }
        }
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Controls(vm: PerformanceViewModel, coreReady: Boolean, fasReady: Boolean, current: FasStatus?) {
    SectionCard(stringResource(R.string.controls), emphasis = Emphasis.LOW) {
        when {
            coreReady -> Supporting(stringResource(R.string.controls_via_core))
            fasReady -> Supporting(stringResource(R.string.controls_direct))
            else -> {
                Supporting(stringResource(R.string.controls_none))
                return@SectionCard
            }
        }
        Text(stringResource(R.string.controls_profile), style = MaterialTheme.typography.labelLarge)
        // Expressive toggle buttons: the selected (current) profile morphs to a square shape.
        // Tapping one never changes anything directly: it opens Core's dry-run preview (or the
        // direct-command confirmation), and the selection only moves once zfasd reports it.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            FasProfileName.entries.forEach { p ->
                ToggleButton(
                    checked = current?.profile == p.wire,
                    onCheckedChange = { if (coreReady) vm.preview(CoreOperation.FasSetProfile(p)) else vm.setProfileDirect(p) },
                ) {
                    if (current?.profile == p.wire) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = ZairenkaiSpacing.xs))
                    Text(if (p.experimental) "${p.wire} (${stringResource(R.string.experimental)})" else p.wire)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            OutlinedButton(onClick = { if (coreReady) vm.preview(CoreOperation.FasSetEnabled(true)) else vm.commandDirect(FasCommand.ENABLE) }) { Text(stringResource(R.string.fas_enable)) }
            OutlinedButton(onClick = { if (coreReady) vm.preview(CoreOperation.FasSetEnabled(false)) else vm.commandDirect(FasCommand.DISABLE) }) { Text(stringResource(R.string.fas_disable)) }
            if (coreReady) {
                OutlinedButton(onClick = { vm.preview(CoreOperation.SetLowPowerMode(true)) }) { Text(stringResource(R.string.battery_saver_on)) }
                OutlinedButton(onClick = { vm.preview(CoreOperation.SetLowPowerMode(false)) }) { Text(stringResource(R.string.battery_saver_off)) }
            } else {
                OutlinedButton(onClick = { vm.commandDirect(FasCommand.RESET) }) { Text(stringResource(R.string.fas_reset)) }
            }
        }
    }
}

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
            onConfirm = { if (!confirmed) { confirmed = true; vm.applyDirect(state.label, state.run) } },
            onDismiss = vm::dismiss,
            destructive = true,
        )
        is ActionState.Done -> ResultDialog(state.label.resolve(), stringResource(if (state.succeeded) R.string.action_done else R.string.action_not_applied) + " · " + state.message, vm::dismiss)
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
