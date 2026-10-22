// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.design.Emphasis
import com.zairenkaiprjctx.app.design.ExpandableSection
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
import com.zairenkaiprjctx.integration.core.TelemetryField
import com.zairenkaiprjctx.integration.core.TelemetryReading
import com.zairenkaiprjctx.integration.fas.FasCall
import com.zairenkaiprjctx.integration.registry.RegistrySnapshot
import com.zairenkaiprjctx.integration.thermaltools.ThermalToolsResult

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThermalScreen(vm: ThermalViewModel, perf: PerformanceViewModel, snapshot: RegistrySnapshot, padding: PaddingValues) {
    DisposableEffect(Unit) {
        vm.start()
        onDispose { vm.stop() }
    }
    val sample by vm.android.collectAsStateWithLifecycle()
    val core by vm.core.collectAsStateWithLifecycle()
    val fas by perf.fasStatus.collectAsStateWithLifecycle()
    val headroom = snapshot.byId("android.thermal_headroom")

    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
        item {
            HeroStatusCard(
                overline = stringResource(R.string.thermal_android),
                headline = thermalStatusLabel(sample?.status),
                supporting = stringResource(R.string.thermal_android_source),
                state = snapshot.byId("android.thermal_status")?.availability?.state,
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
                    if (headroom?.availability?.usable == true) {
                        MetricTile(
                            stringResource(R.string.thermal_headroom),
                            sample?.headroom?.let { "%.2f".format(it) } ?: "—",
                            stringResource(R.string.thermal_headroom_note),
                            emphasis = Emphasis.HIGH,
                        )
                    }
                    MetricTile(stringResource(R.string.battery_saver), stringResource(if (sample?.powerSave == true) R.string.on else R.string.off), null)
                }
                if (headroom?.availability?.usable != true && headroom != null) Supporting(headroom.availability.reason)
            }
        }
        item {
            when (val t = core) {
                is CoreCall.Ok -> SectionCard(stringResource(R.string.thermal_core), trailing = {
                    OutlinedButton(onClick = vm::reloadCore) { Text(stringResource(R.string.action_refresh)) }
                }) {
                    CORE_FIELDS.forEach { (key, label) ->
                        t.value.reading(key)?.let { f -> InfoRow(stringResource(label), telemetryText(f), numeric = true) }
                    }
                    Supporting(stringResource(R.string.thermal_core_source))
                }
                null -> SectionCard(stringResource(R.string.thermal_core)) { ProgressRow(stringResource(R.string.loading)) }
                else -> UnavailableCard(snapshot.byId("core.health"), t.describeFailure())
            }
        }
        item {
            val step = (fas as? FasCall.Ok)?.value?.step
            SectionCard(stringResource(R.string.thermal_fas_cap), emphasis = Emphasis.LOW) {
                val th = step?.thermal
                if (th != null) {
                    InfoRow(stringResource(R.string.key_level_cap), th.levelCap?.toString(), numeric = true)
                    InfoRow(stringResource(R.string.key_boost_suspended), stringResource(if (th.suspended) R.string.on else R.string.off))
                    InfoRow(stringResource(R.string.key_reason), th.reason)
                } else {
                    Supporting(snapshot.byId("fas.status")?.availability?.reason?.takeIf { it.isNotBlank() } ?: stringResource(R.string.thermal_fas_hint))
                }
            }
        }
        item { ThermalToolsCard(vm, snapshot) }
        item { UnavailableCard(snapshot.byId("thermaltools.control")) }
    }
}

private fun celsius(milli: Long): String = "%.1f °C".format(milli / 1000.0)

/** Core telemetry shown on this screen, with its label. Keys are Core's `TelemetrySnapshot` fields. */
private val CORE_FIELDS = listOf(
    "thermal_status" to R.string.core_field_thermal_status,
    "battery_temp" to R.string.core_field_battery_temp,
    "battery_level" to R.string.core_field_battery_level,
    "charging" to R.string.core_field_charging,
    "low_power_mode" to R.string.battery_saver,
)

/** A reading in the user's language and unit; invalid values are named as such, never shown as measurements. */
@Composable
private fun telemetryText(f: TelemetryField): String {
    val text = when (val r = f.reading) {
        is TelemetryReading.Temperature -> stringResource(R.string.reading_celsius, r.celsius)
        is TelemetryReading.Level -> stringResource(R.string.reading_percent, r.percent)
        is TelemetryReading.Flag -> stringResource(if (r.on) R.string.on else R.string.off)
        is TelemetryReading.ThermalLevel -> stringResource(
            when (r.status) {
                "none" -> R.string.thermal_none
                "light" -> R.string.thermal_light
                "moderate" -> R.string.thermal_moderate
                "severe" -> R.string.thermal_severe
                "critical" -> R.string.thermal_critical
                "emergency" -> R.string.thermal_emergency
                else -> R.string.thermal_shutdown
            },
        )
        is TelemetryReading.MemoryKib -> "${r.kib} KiB"
        is TelemetryReading.Invalid -> stringResource(R.string.reading_invalid, r.detail)
        is TelemetryReading.NotReported -> {
            val status = stringResource(
                when (r.status) {
                    "unavailable" -> R.string.obs_status_unavailable
                    "unsupported" -> R.string.obs_status_unsupported
                    "permission_denied" -> R.string.obs_status_permission_denied
                    else -> R.string.obs_status_unknown
                },
            )
            val detail = r.detail
            if (detail.isNullOrBlank()) status else stringResource(R.string.reading_not_reported, status, detail)
        }
    }
    return f.staleByMs?.let { stringResource(R.string.reading_stale, text, (it / 1000).toInt()) } ?: text
}

/** zthermal's read-only snapshot: zones as zthermal read them, failed reads shown with their reason. */
@Composable
private fun ThermalToolsCard(vm: ThermalViewModel, snapshot: RegistrySnapshot) {
    val result by vm.thermalTools.collectAsStateWithLifecycle()
    val busy by vm.thermalToolsBusy.collectAsStateWithLifecycle()
    val record = snapshot.byId("thermaltools.snapshot")
    when (val r = result) {
        null -> SectionCard(stringResource(R.string.thermaltools_title)) { ProgressRow(stringResource(R.string.loading)) }
        is ThermalToolsResult.Failed -> UnavailableCard(record, r.detail)
        is ThermalToolsResult.Ok -> {
            val s = r.value
            SectionCard(stringResource(R.string.thermaltools_title), trailing = { record?.let { StatusBadge(it.availability.state) } }) {
                InfoRow(stringResource(R.string.thermaltools_zones), stringResource(R.string.thermaltools_zones_value, s.summary.zonesValid, s.summary.zones), numeric = true)
                InfoRow(
                    stringResource(R.string.thermaltools_hottest),
                    s.summary.hottest?.let { "${it.name ?: it.source}: ${celsius(it.millicelsius)}" },
                    numeric = true,
                )
                InfoRow(stringResource(R.string.thermaltools_framework), s.summary.frameworkStatus ?: s.sources.firstOrNull { it.source == "dumpsys thermalservice" }?.status)
                s.summary.batteryMillicelsius?.let { InfoRow(stringResource(R.string.thermaltools_battery), celsius(it), numeric = true) }
                if (s.accessDenied) Supporting(stringResource(R.string.thermaltools_denied))
                ExpandableSection(stringResource(R.string.thermaltools_zone_list, s.zones.size)) {
                    s.zones.sortedByDescending { it.temperature.millicelsius ?: Long.MIN_VALUE }.forEach { z ->
                        InfoRow(
                            z.type ?: z.path.substringAfterLast('/'),
                            z.temperature.millicelsius?.let(::celsius) ?: "${z.temperature.status}: ${z.temperature.reason.orEmpty()}",
                            numeric = true,
                        )
                    }
                }
                Supporting(stringResource(R.string.thermaltools_source, s.toolVersion))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                    OutlinedButton(onClick = { vm.loadThermalTools() }, enabled = !busy) { Text(stringResource(R.string.action_refresh)) }
                    if (vm.rootGranted) {
                        OutlinedButton(onClick = { vm.loadThermalTools(withRoot = true) }, enabled = !busy) { Text(stringResource(R.string.thermaltools_with_root)) }
                    }
                }
                if (busy) ProgressRow(stringResource(R.string.loading))
            }
        }
    }
}
