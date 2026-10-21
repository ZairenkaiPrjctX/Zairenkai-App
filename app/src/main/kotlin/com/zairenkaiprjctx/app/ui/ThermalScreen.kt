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
                    listOf("thermal_status", "battery_temp", "battery_level", "charging", "low_power_mode").forEach { key ->
                        t.value[key]?.let { o ->
                            InfoRow(key, if (o.status == "value") (o.text ?: o.value.toString()) else "${o.status}: ${o.text ?: ""}", numeric = true)
                        }
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
