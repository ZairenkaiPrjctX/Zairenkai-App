package com.zairenkaiprjctx.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.zairenkaiprjctx.integration.core.CoreCall
import com.zairenkaiprjctx.integration.exec.RootState
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.registry.RegistrySnapshot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun rootStateLabel(s: RootState): String = stringResource(
    when (s) {
        RootState.NOT_REQUESTED -> R.string.root_state_not_requested
        RootState.GRANTED -> R.string.root_state_granted
        RootState.DENIED -> R.string.root_state_denied
        RootState.UNAVAILABLE -> R.string.root_state_unavailable
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemScreen(vm: SystemViewModel, main: MainViewModel, snapshot: RegistrySnapshot, padding: PaddingValues) {
    val c = vm.container
    val root by main.rootState.collectAsStateWithLifecycle()
    val modules by vm.modules.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val afid by main.afid.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var licence by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(snapshot.refreshedAtMs) { vm.loadModules() }

    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
        item {
            SectionCard(
                stringResource(R.string.root_access),
                emphasis = Emphasis.HIGH,
                trailing = { StatusBadge(if (root == RootState.GRANTED) AvailabilityState.AVAILABLE else AvailabilityState.ROOT_REQUIRED) },
            ) {
                InfoRow(stringResource(R.string.key_state), rootStateLabel(root))
                Supporting(stringResource(R.string.root_note))
                if (root == RootState.GRANTED) {
                    OutlinedButton(onClick = main::revokeRoot) { Text(stringResource(R.string.root_disable)) }
                } else {
                    Button(onClick = main::requestRoot) { Text(stringResource(R.string.root_enable)) }
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.modules_title)) {
                when (val m = modules) {
                    is CoreCall.Ok -> m.value.forEach { mod ->
                        InfoRow(mod.id, "${mod.health}${mod.version?.let { " · $it" } ?: ""}", code = true)
                        if (mod.detail.isNotBlank()) Supporting(mod.detail)
                    }
                    null -> ProgressRow(stringResource(R.string.loading))
                    else -> Supporting(m.describeFailure())
                }
                snapshot.byId("modules.packages")?.let { Supporting(it.availability.reason) }
            }
        }
        item {
            SectionCard(stringResource(R.string.search_title)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        vm.search(it)
                    },
                    label = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.length >= 2 && results.isEmpty()) Supporting(stringResource(R.string.search_none))
                results.forEach { DeviceCard(it) }
                afid?.getOrNull()?.let { Supporting(it.license.attribution) }
            }
        }
        item {
            val inv = c.inventory
            SectionCard(pluralStringResource(R.plurals.ecosystem_title, inv?.repositories?.size ?: 0, inv?.repositories?.size ?: 0), emphasis = Emphasis.LOW) {
                if (inv == null) {
                    Supporting(stringResource(R.string.ecosystem_missing))
                } else {
                    Supporting(stringResource(R.string.ecosystem_note, inv.surveyed))
                    inv.repositories.forEach { r ->
                        ExpandableSection("${r.name} · ${r.integrationStatus}") {
                            InfoRow(r.auditedBranch, r.auditedCommit.take(12), code = true)
                            Supporting(r.disposition)
                            r.appCapabilities.forEach { Supporting("• $it") }
                        }
                    }
                    inv.outside.forEach { Supporting(stringResource(R.string.ecosystem_outside, it.fullName)) }
                }
            }
        }
        item { Diagnostics(vm, snapshot, root, context) }
        item {
            SectionCard(stringResource(R.string.licences_title), emphasis = Emphasis.LOW) {
                Supporting(stringResource(R.string.licence_font))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                    TextButton(onClick = { licence = "licenses/google-sans-flex/OFL.txt" }) { Text(stringResource(R.string.licence_font_view)) }
                    TextButton(onClick = { licence = "licenses/google-sans-flex/TRADEMARKS.md" }) { Text(stringResource(R.string.licence_font_trademarks)) }
                }
                afid?.getOrNull()?.let { Supporting(stringResource(R.string.licence_data, it.license.spdx, it.license.attribution)) }
                Supporting(stringResource(R.string.licence_zart))
                Supporting(stringResource(R.string.licence_zthermal))
                if (c.components?.zthermalCommit != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                        TextButton(onClick = { licence = "licenses/zthermal/LICENSE" }) { Text(stringResource(R.string.licence_zthermal_view)) }
                        TextButton(onClick = { licence = "licenses/zthermal/NOTICE" }) { Text(stringResource(R.string.licence_zthermal_notice)) }
                    }
                }
                Supporting(stringResource(R.string.licence_libs))
                Supporting(stringResource(R.string.licence_project))
            }
        }
    }
    licence?.let { path ->
        val text = remember(path) { runCatching { c.asset(path) }.getOrElse { it.message ?: "" } }
        AlertDialog(
            onDismissRequest = { licence = null },
            confirmButton = { TextButton(onClick = { licence = null }) { Text(stringResource(R.string.close)) } },
            text = {
                Text(text, style = ZairenkaiTextStyles.code, modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()))
            },
        )
    }
}

@Composable
private fun Diagnostics(vm: SystemViewModel, snapshot: RegistrySnapshot, root: RootState, context: android.content.Context) {
    val c = vm.container
    SectionCard(stringResource(R.string.diagnostics_title), emphasis = Emphasis.LOW) {
        val comp = c.components
        InfoRow(stringResource(R.string.key_app), "${c.versionName} (${c.packageName})", code = true)
        InfoRow(stringResource(R.string.key_zart), comp?.let { "${it.zartVersion} @ ${it.zartCommit?.take(12)}" } ?: stringResource(R.string.not_packaged), code = true)
        InfoRow(stringResource(R.string.key_zart_exe), stringResource(if (c.zartPath != null) R.string.present else R.string.missing_abi))
        InfoRow(stringResource(R.string.key_zthermal), comp?.zthermalCommit?.let { "${comp.zthermalVersion} @ ${it.take(12)}" } ?: stringResource(R.string.not_packaged), code = true)
        InfoRow(stringResource(R.string.key_zthermal_exe), stringResource(if (c.zthermalPath != null) R.string.present else R.string.missing_abi))
        InfoRow(stringResource(R.string.key_afid_data), comp?.afidCommit?.take(12) ?: stringResource(R.string.not_packaged), code = true)
        val chooserTitle = stringResource(R.string.diagnostics_share)
        Button(onClick = {
            val report = buildJsonObject {
                put("app_version", c.versionName)
                put("api_level", c.platform.sdkInt)
                put("abis", JsonArray(c.platform.supportedAbis.map { JsonPrimitive(it) }))
                put("zart_commit", comp?.zartCommit)
                put("zthermal_commit", comp?.zthermalCommit)
                put("afid_commit", comp?.afidCommit)
                put("root_state", root.name)
                put(
                    "capabilities",
                    JsonArray(
                        snapshot.records.map {
                            buildJsonObject {
                                put("id", it.id)
                                put("state", it.availability.state.name)
                                put("reason", it.availability.reason)
                                put("provider", it.provider.repository)
                                put("revision", it.provider.revision)
                            }
                        },
                    ),
                )
            }
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report.toString())
            context.startActivity(Intent.createChooser(send, chooserTitle))
        }) { Text(chooserTitle) }
        Supporting(stringResource(R.string.diagnostics_note))
    }
}
