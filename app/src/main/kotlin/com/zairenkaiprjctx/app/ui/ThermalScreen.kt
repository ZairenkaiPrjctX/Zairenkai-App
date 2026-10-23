// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zairenkaiprjctx.app.design.Detail
import com.zairenkaiprjctx.app.design.GroupHeader
import com.zairenkaiprjctx.app.design.HeroDefaults
import com.zairenkaiprjctx.app.design.InfoChip
import com.zairenkaiprjctx.app.design.LocalStatusColors
import com.zairenkaiprjctx.app.design.MetricTile
import com.zairenkaiprjctx.app.design.ZairenkaiTextStyles
import androidx.compose.material3.MaterialTheme
import com.zairenkaiprjctx.app.design.StatusTone
import com.zairenkaiprjctx.app.design.TileGrid
import com.zairenkaiprjctx.app.design.ZairenkaiStatusColors
import com.zairenkaiprjctx.app.design.entrance
import com.zairenkaiprjctx.app.design.rememberEntrance
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.design.GroupContent
import com.zairenkaiprjctx.app.design.GroupRow
import com.zairenkaiprjctx.app.design.HeroPanel
import com.zairenkaiprjctx.app.design.Meter
import com.zairenkaiprjctx.app.design.RingGauge
import com.zairenkaiprjctx.app.design.SegmentedGroup
import com.zairenkaiprjctx.app.design.StatusGlyph
import com.zairenkaiprjctx.app.design.ZairenkaiGlyphs
import com.zairenkaiprjctx.app.design.visual
import androidx.compose.material3.TextButton
import com.zairenkaiprjctx.app.design.ExpandableSection
import com.zairenkaiprjctx.app.design.InfoRow
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

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
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
    val statusRecord = snapshot.byId("android.thermal_status")

    val entrance = rememberEntrance()
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
        item {
            val usable = headroom?.availability?.usable == true
            val h = sample?.headroom
            val status = LocalStatusColors.current
            HeroPanel(
                overline = stringResource(R.string.thermal_android),
                headline = thermalStatusLabel(sample?.status),
                supporting = statusRecord?.availability?.state?.let { stringResource(it.visual().label) },
                modifier = Modifier.entrance(entrance, 0),
                colors = HeroDefaults.status(thermalTone(sample?.status, status)),
                decoration = MaterialShapes.Sunny,
                trailing = {
                    if (usable) {
                        // Headroom 1.0 is where Android expects severe throttling: the ring fills toward it.
                        RingGauge(
                            progress = h,
                            value = h?.let { "%.2f".format(it) } ?: "—",
                            caption = stringResource(R.string.thermal_headroom_short),
                            label = h?.let { stringResource(R.string.thermal_headroom_label, "%.2f".format(it)) } ?: stringResource(R.string.thermal_headroom),
                        )
                    } else {
                        statusRecord?.availability?.state?.let { StatusGlyph(it, size = 72.dp) }
                    }
                },
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                    InfoChip(
                        stringResource(R.string.thermal_chip_saver, stringResource(if (sample?.powerSave == true) R.string.on else R.string.off)),
                        icon = ZairenkaiGlyphs.battery,
                    )
                }
                if (usable) Detail(stringResource(R.string.thermal_headroom_note)) else headroom?.let { Detail(it.availability.reason) }
                Detail(stringResource(R.string.thermal_android_source), maxLines = 2)
            }
        }
        item {
            Box(Modifier.entrance(entrance, 1)) {
                when (val t = core) {
                    is CoreCall.Ok -> Column(verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
                        GroupHeader(stringResource(R.string.thermal_core), icon = ZairenkaiGlyphs.tiles)
                        val fields = CORE_FIELDS.mapNotNull { (key, label) -> t.value.reading(key)?.let { label to it } }
                        TileGrid(fields) { (label, f), m -> ReadingTile(label, f, m) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Detail(stringResource(R.string.thermal_core_source), Modifier.weight(1f), maxLines = 2)
                            TextButton(onClick = vm::reloadCore) { Text(stringResource(R.string.action_refresh)) }
                        }
                    }
                    null -> SectionCard(stringResource(R.string.thermal_core)) { ProgressRow(stringResource(R.string.loading)) }
                    else -> UnavailableCard(snapshot.byId("core.health"), t.describeFailure())
                }
            }
        }
        item {
            val step = (fas as? FasCall.Ok)?.value?.step
            SegmentedGroup(title = stringResource(R.string.thermal_fas_cap), icon = ZairenkaiGlyphs.gauge, modifier = Modifier.entrance(entrance, 2)) {
                row {
                    GroupContent {
                        val th = step?.thermal
                        if (th != null) {
                            InfoRow(stringResource(R.string.key_level_cap), th.levelCap?.toString(), numeric = true)
                            InfoRow(stringResource(R.string.key_boost_suspended), stringResource(if (th.suspended) R.string.on else R.string.off))
                            InfoRow(stringResource(R.string.key_reason), th.reason)
                        } else {
                            Detail(snapshot.byId("fas.status")?.availability?.reason?.takeIf { it.isNotBlank() } ?: stringResource(R.string.thermal_fas_hint))
                        }
                    }
                }
            }
        }
        item { Box(Modifier.entrance(entrance, 3)) { ThermalToolsCard(vm, snapshot) } }
        item { Box(Modifier.entrance(entrance, 4)) { UnavailableCard(snapshot.byId("thermaltools.control")) } }
    }
}

/** The tone of the thermal hero: calm up to light throttling, warning at moderate, error from severe. */
fun thermalTone(status: Int?, colors: ZairenkaiStatusColors): StatusTone = when {
    status == null || status < 0 -> colors.unavailable
    status <= android.os.PowerManager.THERMAL_STATUS_LIGHT -> colors.success
    status == android.os.PowerManager.THERMAL_STATUS_MODERATE -> colors.warning
    else -> colors.error
}

/** One Core reading as a tile: the value (or why there is none) big, the daemon's detail clamped. */
@Composable
private fun ReadingTile(@StringRes label: Int, f: TelemetryField, modifier: Modifier) {
    val (value, detail) = telemetryParts(f)
    val measured = f.reading !is TelemetryReading.NotReported && f.reading !is TelemetryReading.Invalid
    MetricTile(
        stringResource(label),
        value,
        detail,
        modifier,
        meter = (f.reading as? TelemetryReading.Level)?.let { it.percent / 100f },
        // A state in words is not a measurement: it gets the title size, not the big figure.
        valueStyle = if (measured) ZairenkaiTextStyles.metric else MaterialTheme.typography.titleMedium,
    )
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

/**
 * A reading in the user's language and unit, split into a short value and an optional detail;
 * invalid values are named as such, never shown as measurements.
 */
@Composable
private fun telemetryParts(f: TelemetryField): Pair<String, String?> {
    val (text, detail) = when (val r = f.reading) {
        is TelemetryReading.Temperature -> stringResource(R.string.reading_celsius, r.celsius) to null
        is TelemetryReading.Level -> stringResource(R.string.reading_percent, r.percent) to null
        is TelemetryReading.Flag -> stringResource(if (r.on) R.string.on else R.string.off) to null
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
        ) to null
        is TelemetryReading.MemoryKib -> "${r.kib} KiB" to null
        is TelemetryReading.Invalid -> stringResource(R.string.reading_invalid_short) to r.detail
        is TelemetryReading.NotReported -> stringResource(
            when (r.status) {
                "unavailable" -> R.string.obs_status_unavailable
                "unsupported" -> R.string.obs_status_unsupported
                "permission_denied" -> R.string.obs_status_permission_denied
                else -> R.string.obs_status_unknown
            },
        ) to r.detail?.takeIf { it.isNotBlank() }
    }
    val stale = f.staleByMs?.let { (it / 1000).toInt() }?.let { pluralStringResource(R.plurals.reading_stale_short, it, it) }
    return text to listOfNotNull(stale, detail).joinToString(" · ").ifBlank { null }
}

/** zthermal's read-only snapshot: zones as zthermal read them, failed reads shown with their reason. */
@OptIn(ExperimentalLayoutApi::class)
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
            SegmentedGroup(title = stringResource(R.string.thermaltools_title), icon = ZairenkaiGlyphs.thermometer) {
                row {
                    GroupRow(
                        s.summary.hottest?.let { celsius(it.millicelsius) } ?: stringResource(R.string.not_reported),
                        supporting = s.summary.hottest?.let { stringResource(R.string.thermaltools_hottest_in, it.name ?: it.source) } ?: stringResource(R.string.thermaltools_hottest),
                        icon = ZairenkaiGlyphs.thermometer,
                        status = record?.availability?.state,
                    )
                }
                row {
                    GroupContent {
                        InfoRow(stringResource(R.string.thermaltools_zones), stringResource(R.string.thermaltools_zones_value, s.summary.zonesValid, s.summary.zones), numeric = true)
                        if (s.summary.zones > 0) Meter(s.summary.zonesValid.toFloat() / s.summary.zones)
                        InfoRow(stringResource(R.string.thermaltools_framework), s.summary.frameworkStatus ?: s.sources.firstOrNull { it.source == "dumpsys thermalservice" }?.status)
                        s.summary.batteryMillicelsius?.let { InfoRow(stringResource(R.string.thermaltools_battery), celsius(it), numeric = true) }
                        if (s.accessDenied) Detail(stringResource(R.string.thermaltools_denied))
                        ExpandableSection(stringResource(R.string.thermaltools_zone_list, s.zones.size)) {
                            s.zones.sortedByDescending { it.temperature.millicelsius ?: Long.MIN_VALUE }.forEach { z ->
                                InfoRow(
                                    z.type ?: z.path.substringAfterLast('/'),
                                    z.temperature.millicelsius?.let(::celsius) ?: listOfNotNull(z.temperature.status, z.temperature.reason?.takeIf { it.isNotBlank() }).joinToString(": "),
                                    numeric = true,
                                )
                            }
                        }
                        Detail(stringResource(R.string.thermaltools_source, s.toolVersion), maxLines = 2)
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
    }
}
