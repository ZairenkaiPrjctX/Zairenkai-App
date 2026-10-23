// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui

import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairenkaiprjctx.app.AppContainer
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.Tab
import com.zairenkaiprjctx.app.design.ActionTile
import com.zairenkaiprjctx.app.design.CapabilityItem
import com.zairenkaiprjctx.app.design.ExpandableSection
import com.zairenkaiprjctx.app.design.GroupContent
import com.zairenkaiprjctx.app.design.GroupRow
import com.zairenkaiprjctx.app.design.HeroPanel
import com.zairenkaiprjctx.app.design.InfoRow
import com.zairenkaiprjctx.app.design.ProgressRow
import com.zairenkaiprjctx.app.design.RingGauge
import com.zairenkaiprjctx.app.design.SegmentedGroup
import com.zairenkaiprjctx.app.design.Supporting
import com.zairenkaiprjctx.app.design.ZairenkaiGlyphs
import com.zairenkaiprjctx.app.design.ZairenkaiSpacing
import com.zairenkaiprjctx.integration.afid.AfidCatalog
import com.zairenkaiprjctx.integration.afid.AfidDevice
import com.zairenkaiprjctx.integration.exec.RootState
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.model.Privilege
import com.zairenkaiprjctx.integration.registry.CapabilityRecord
import com.zairenkaiprjctx.integration.systemmodule.InstallPhase

@Composable
fun UiText.resolve(): String = stringResource(id, *args.toTypedArray())

/** Localised name of a `PowerManager.THERMAL_STATUS_*` value. */
@Composable
fun thermalStatusLabel(status: Int?): String = stringResource(
    when (status) {
        PowerManager.THERMAL_STATUS_NONE -> R.string.thermal_none
        PowerManager.THERMAL_STATUS_LIGHT -> R.string.thermal_light
        PowerManager.THERMAL_STATUS_MODERATE -> R.string.thermal_moderate
        PowerManager.THERMAL_STATUS_SEVERE -> R.string.thermal_severe
        PowerManager.THERMAL_STATUS_CRITICAL -> R.string.thermal_critical
        PowerManager.THERMAL_STATUS_EMERGENCY -> R.string.thermal_emergency
        PowerManager.THERMAL_STATUS_SHUTDOWN -> R.string.thermal_shutdown
        else -> R.string.thermal_unavailable
    },
)

/** A suggestion on Home, derived only from probe results; never a guess about the device. */
enum class NextStep { ENABLE_ROOT, COOL_DOWN }

/**
 * What Home suggests: enabling root when features are waiting for it and su exists, and opening
 * the thermal screen when Android reports severe throttling or worse.
 */
fun nextSteps(records: List<CapabilityRecord>, root: RootState, thermalStatus: Int?): List<NextStep> = buildList {
    val waitingForRoot = records.any { it.availability.state == AvailabilityState.ROOT_REQUIRED }
    if (waitingForRoot && root != RootState.GRANTED && root != RootState.UNAVAILABLE) add(NextStep.ENABLE_ROOT)
    if (thermalStatus != null && thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) add(NextStep.COOL_DOWN)
}

/** Capability groups on Home: what needs the user, what works, and what cannot exist here. */
enum class CapabilityGroup { ATTENTION, READY, NOT_HERE }

fun CapabilityRecord.group(): CapabilityGroup = when {
    availability.usable -> CapabilityGroup.READY
    availability.state == AvailabilityState.UNSUPPORTED_DEVICE || availability.state == AvailabilityState.NO_UPSTREAM_IMPLEMENTATION -> CapabilityGroup.NOT_HERE
    else -> CapabilityGroup.ATTENTION
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(c: AppContainer, main: MainViewModel, modules: SystemModuleViewModel, padding: PaddingValues, openModule: () -> Unit, open: (Tab) -> Unit) {
    val snapshot by main.registry.collectAsStateWithLifecycle()
    val root by main.rootState.collectAsStateWithLifecycle()
    val declined by modules.declined.collectAsStateWithLifecycle()
    val base = modules.modules.firstOrNull { it.role == "base" }
    val baseState = base?.let { modules.state(it.id) }?.collectAsStateWithLifecycle()?.value
    val afid by main.afid.collectAsStateWithLifecycle()
    val p = c.platform
    val records = snapshot.records
    val ready = records.count { it.availability.usable }
    val thermal = p.thermalStatus()
    // System integration: offered when root is granted or a root-manager app is installed.
    // Nothing is checked until the user asks ("Set up"); "Not now" hides it (Settings undoes that).
    val settled = baseState?.phase in setOf(InstallPhase.ACTIVE, InstallPhase.INSTALL_PENDING_REBOOT, InstallPhase.INSTALLED_NOT_INITIALIZED, InstallPhase.DEGRADED)
    val moduleOffer = base != null && !declined && !settled && (root == RootState.GRANTED || modules.managerAppHint != null)
    val steps = nextSteps(records, root, thermal)

    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
        item {
            HeroPanel(
                overline = stringResource(R.string.home_device),
                headline = "${p.manufacturer} ${p.model}".trim(),
                supporting = stringResource(R.string.android_version, p.androidRelease, p.sdkInt),
                trailing = {
                    RingGauge(
                        progress = if (records.isEmpty()) null else ready.toFloat() / records.size,
                        value = if (records.isEmpty()) "…" else "$ready",
                        caption = if (records.isEmpty()) null else pluralStringResource(R.plurals.home_ring_caption, records.size, records.size),
                        label = if (records.isEmpty()) stringResource(R.string.home_health_probing) else pluralStringResource(R.plurals.home_ready_summary, records.size, ready, records.size),
                    )
                },
            ) {
                ExpandableSection(stringResource(R.string.home_device_details)) {
                    InfoRow(stringResource(R.string.key_codename), p.deviceCodename, code = true)
                    InfoRow(stringResource(R.string.key_soc), listOfNotNull(p.socManufacturer, p.socModel).joinToString(" ").ifBlank { null })
                    InfoRow(stringResource(R.string.key_abis), p.supportedAbis.joinToString(", "))
                    Supporting(stringResource(R.string.home_device_source))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
                ActionTile(ZairenkaiGlyphs.thermometer, stringResource(R.string.home_tile_thermal), thermalStatusLabel(thermal), { open(Tab.THERMAL) }, Modifier.weight(1f))
                ActionTile(ZairenkaiGlyphs.shield, stringResource(R.string.root_access), rootStateLabel(root), { open(Tab.SYSTEM) }, Modifier.weight(1f))
            }
        }
        if (moduleOffer || steps.isNotEmpty()) {
            item {
                SegmentedGroup(title = stringResource(R.string.home_suggested)) {
                    if (moduleOffer) {
                        row {
                            GroupContent {
                                val rootFeatures = records.filter { it.privilege == Privilege.ROOT && !it.id.startsWith("systemmodule.") }
                                Text(stringResource(R.string.module_offer_title), style = MaterialTheme.typography.titleMedium)
                                if (baseState?.phase == InstallPhase.MODULE_AVAILABLE) {
                                    Supporting(stringResource(R.string.module_offer_body, baseState.manager?.family?.displayName ?: "", base.features.joinToString { snapshot.byId(it)?.name ?: it }))
                                } else {
                                    modules.managerAppHint?.let { Supporting(stringResource(R.string.module_offer_manager_hint, it)) }
                                    Supporting(stringResource(R.string.module_offer_features, rootFeatures.joinToString { it.name }))
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                                    Button(onClick = openModule) {
                                        Text(stringResource(if (baseState?.phase == InstallPhase.MODULE_AVAILABLE) R.string.module_offer_review else R.string.module_offer_setup))
                                    }
                                    OutlinedButton(onClick = modules::decline) { Text(stringResource(R.string.module_not_now)) }
                                }
                            }
                        }
                    }
                    steps.forEach { step ->
                        row {
                            when (step) {
                                NextStep.ENABLE_ROOT -> {
                                    val waiting = records.filter { it.availability.state == AvailabilityState.ROOT_REQUIRED }
                                    GroupRow(
                                        stringResource(R.string.home_step_root),
                                        supporting = pluralStringResource(R.plurals.home_step_root_body, waiting.size, waiting.size, waiting.take(3).joinToString { it.name }),
                                        icon = ZairenkaiGlyphs.shield,
                                        emphasized = true,
                                        trailing = { FilledTonalButton(onClick = main::requestRoot) { Text(stringResource(R.string.home_step_root_action)) } },
                                    )
                                }
                                NextStep.COOL_DOWN -> GroupRow(
                                    stringResource(R.string.home_step_hot),
                                    supporting = stringResource(R.string.home_step_hot_body, thermalStatusLabel(thermal)),
                                    icon = ZairenkaiGlyphs.thermometer,
                                    emphasized = true,
                                    onClick = { open(Tab.THERMAL) },
                                    chevron = true,
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.home_health)) {
                if (records.isEmpty()) {
                    row { GroupContent { ProgressRow(stringResource(R.string.home_health_probing)) } }
                }
                val groups = records.groupBy { it.group() }
                CapabilityGroup.entries.forEach { g ->
                    val list = groups[g].orEmpty()
                    if (list.isNotEmpty()) {
                        row {
                            GroupContent {
                                ExpandableSection(
                                    title = stringResource(
                                        when (g) {
                                            CapabilityGroup.ATTENTION -> R.string.home_group_attention
                                            CapabilityGroup.READY -> R.string.home_group_ready
                                            CapabilityGroup.NOT_HERE -> R.string.home_group_not_here
                                        },
                                    ),
                                    initiallyExpanded = g == CapabilityGroup.ATTENTION,
                                    summary = list.size.toString(),
                                ) { list.forEach { CapabilityItem(it, horizontalPadding = ZairenkaiSpacing.xs) } }
                            }
                        }
                    }
                }
            }
        }
        item { DeviceDbMatch(c, afid) }
    }
}

@Composable
private fun DeviceDbMatch(c: AppContainer, afid: Result<AfidCatalog>?) {
    SegmentedGroup(title = stringResource(R.string.home_afid)) {
        row {
            GroupContent {
                if (afid == null) {
                    ProgressRow(stringResource(R.string.loading))
                    return@GroupContent
                }
                val catalog = afid.getOrNull()
                if (catalog == null) {
                    Supporting(stringResource(R.string.home_afid_missing, afid.exceptionOrNull()?.message ?: ""))
                    return@GroupContent
                }
                val matches = catalog.lookup(c.platform.deviceCodename)
                if (matches.isEmpty()) {
                    Text(pluralStringResource(R.plurals.home_afid_none, catalog.count, c.platform.deviceCodename, catalog.count), style = MaterialTheme.typography.bodyMedium)
                } else {
                    matches.forEach { DeviceCard(it) }
                }
                ExpandableSection(stringResource(R.string.home_afid_source)) {
                    Supporting(stringResource(R.string.home_afid_unverified, catalog.generatedFrom.commit.take(12)))
                    Supporting(catalog.license.attribution)
                }
            }
        }
    }
}

@Composable
fun DeviceCard(d: AfidDevice) {
    Text(d.productNames.joinToString(" / ").ifBlank { d.codename }, style = MaterialTheme.typography.titleSmall, modifier = Modifier)
    InfoRow(stringResource(R.string.key_codename), d.codename, code = true)
    InfoRow(stringResource(R.string.key_brand_inferred), d.brandId)
    InfoRow(stringResource(R.string.key_soc), listOfNotNull(d.soc.platform, d.soc.architecture).joinToString(" · ").ifBlank { null })
    if (d.modelNumbers.isNotEmpty()) InfoRow(stringResource(R.string.key_models), d.modelNumbers.joinToString(", "))
    InfoRow(stringResource(R.string.key_released), d.releaseDate, numeric = true)
}
