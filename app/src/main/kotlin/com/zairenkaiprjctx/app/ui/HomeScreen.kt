package com.zairenkaiprjctx.app.ui

import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
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
import com.zairenkaiprjctx.app.design.CapabilityItem
import com.zairenkaiprjctx.app.design.Emphasis
import com.zairenkaiprjctx.app.design.HeroStatusCard
import com.zairenkaiprjctx.app.design.InfoRow
import com.zairenkaiprjctx.app.design.MetricTile
import com.zairenkaiprjctx.app.design.ProgressRow
import com.zairenkaiprjctx.app.design.SectionCard
import com.zairenkaiprjctx.app.design.Supporting
import com.zairenkaiprjctx.app.design.ZairenkaiSpacing
import com.zairenkaiprjctx.integration.afid.AfidCatalog
import com.zairenkaiprjctx.integration.exec.RootState
import com.zairenkaiprjctx.integration.systemmodule.InstallPhase
import com.zairenkaiprjctx.integration.afid.AfidDevice

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(c: AppContainer, main: MainViewModel, modules: SystemModuleViewModel, padding: PaddingValues, openModule: () -> Unit) {
    val snapshot by main.registry.collectAsStateWithLifecycle()
    val root by main.rootState.collectAsStateWithLifecycle()
    val declined by modules.declined.collectAsStateWithLifecycle()
    val base = modules.modules.firstOrNull { it.role == "base" }
    val baseState = base?.let { modules.state(it.id) }?.collectAsStateWithLifecycle()?.value
    val afid by main.afid.collectAsStateWithLifecycle()
    val p = c.platform
    val records = snapshot.records
    val ready = records.count { it.availability.usable }
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
        // System integration: shown when root is granted or a root-manager app is installed.
        // Nothing is checked until the user asks ("Set up"); "Not now" hides it (Settings undoes that).
        val settled = baseState?.phase in setOf(InstallPhase.ACTIVE, InstallPhase.INSTALL_PENDING_REBOOT, InstallPhase.INSTALLED_NOT_INITIALIZED, InstallPhase.DEGRADED)
        if (base != null && !declined && !settled && (root == RootState.GRANTED || modules.managerAppHint != null)) {
            item {
                val rootFeatures = snapshot.records.filter { it.privilege == com.zairenkaiprjctx.integration.model.Privilege.ROOT && !it.id.startsWith("systemmodule.") }
                SectionCard(stringResource(R.string.module_offer_title), emphasis = Emphasis.HIGH) {
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
        item {
            HeroStatusCard(
                overline = stringResource(R.string.home_device),
                headline = "${p.manufacturer} ${p.model}".trim(),
                supporting = stringResource(R.string.android_version, p.androidRelease, p.sdkInt),
                state = null,
            ) {
                InfoRow(stringResource(R.string.key_codename), p.deviceCodename, code = true)
                InfoRow(stringResource(R.string.key_soc), listOfNotNull(p.socManufacturer, p.socModel).joinToString(" ").ifBlank { null })
                InfoRow(stringResource(R.string.key_abis), p.supportedAbis.joinToString(", "))
                Supporting(stringResource(R.string.home_device_source))
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
                MetricTile(
                    label = stringResource(R.string.thermal_status),
                    value = thermalStatusLabel(p.thermalStatus()),
                    caption = stringResource(R.string.thermal_android),
                    emphasis = Emphasis.HIGH,
                )
                MetricTile(
                    label = stringResource(R.string.home_health),
                    value = if (records.isEmpty()) "…" else "$ready/${records.size}",
                    caption = if (records.isEmpty()) stringResource(R.string.home_health_probing) else pluralStringResource(R.plurals.home_ready_summary, records.size, ready, records.size),
                )
            }
        }
        item { DeviceDbMatch(c, afid) }
        item {
            SectionCard(stringResource(R.string.home_health)) {
                if (snapshot.refreshing && records.isEmpty()) ProgressRow(stringResource(R.string.home_health_probing))
                records.forEach { CapabilityItem(it) }
            }
        }
    }
}

@Composable
private fun DeviceDbMatch(c: AppContainer, afid: Result<AfidCatalog>?) {
    SectionCard(stringResource(R.string.home_afid)) {
        if (afid == null) {
            ProgressRow(stringResource(R.string.loading))
            return@SectionCard
        }
        val catalog = afid.getOrNull()
        if (catalog == null) {
            Supporting(stringResource(R.string.home_afid_missing, afid.exceptionOrNull()?.message ?: ""))
            return@SectionCard
        }
        val matches = catalog.lookup(c.platform.deviceCodename)
        if (matches.isEmpty()) {
            Text(pluralStringResource(R.plurals.home_afid_none, catalog.count, c.platform.deviceCodename, catalog.count), style = MaterialTheme.typography.bodyMedium)
        } else {
            matches.forEach { DeviceCard(it) }
        }
        Supporting(stringResource(R.string.home_afid_unverified, catalog.generatedFrom.commit.take(12)))
        Supporting(catalog.license.attribution)
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
