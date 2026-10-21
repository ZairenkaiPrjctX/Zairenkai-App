// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.registry.RegistrySnapshot
import com.zairenkaiprjctx.integration.zart.Obs
import com.zairenkaiprjctx.integration.zart.PackageNames
import com.zairenkaiprjctx.integration.zart.ZartReport
import com.zairenkaiprjctx.integration.zart.ZartResult

private fun <T> Obs<T>.show(f: (T) -> String = { it.toString() }): String = when (this) {
    is Obs.Value -> f(value)
    is Obs.Missing -> "$status: $detail".trimEnd(':', ' ')
}

@Composable
fun RuntimeScreen(vm: RuntimeViewModel, snapshot: RegistrySnapshot, padding: PaddingValues, goToRoot: () -> Unit) {
    LaunchedEffect(Unit) { vm.load() }
    val inspect by vm.inspect.collectAsStateWithLifecycle()
    val dexopt by vm.dexoptStatus.collectAsStateWithLifecycle()
    val busy by vm.querying.collectAsStateWithLifecycle()
    var pkg by rememberSaveable { mutableStateOf("") }

    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
        item {
            HeroStatusCard(
                overline = stringResource(R.string.art_module),
                headline = vm.artApexVersion?.toString() ?: stringResource(R.string.not_reported),
                supporting = stringResource(R.string.art_version),
                state = if (vm.artApexVersion != null) AvailabilityState.AVAILABLE else null,
            ) { Supporting(stringResource(R.string.art_module_source)) }
        }
        item {
            when (val r = inspect) {
                is ZartResult.Ok -> InspectCard(r.value)
                is ZartResult.Failed -> UnavailableCard(snapshot.byId("art.inspect"), r.detail)
                null -> SectionCard(stringResource(R.string.art_inspect)) { ProgressRow(stringResource(R.string.art_running)) }
            }
        }
        item {
            val record = snapshot.byId("art.dexopt_status")
            SectionCard(stringResource(R.string.dexopt_title), trailing = { record?.let { StatusBadge(it.availability.state) } }) {
                record?.availability?.reason?.let { if (it.isNotBlank()) Supporting(it) }
                val invalid = pkg.isNotEmpty() && !PackageNames.isValid(pkg)
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it.trim() },
                    label = { Text(stringResource(R.string.dexopt_package)) },
                    singleLine = true,
                    isError = invalid,
                    supportingText = { if (invalid) Text(stringResource(R.string.dexopt_invalid)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                    Button(onClick = { vm.query(pkg) }, enabled = !busy && PackageNames.isValid(pkg) && record?.availability?.usable == true) {
                        Text(stringResource(if (busy) R.string.dexopt_querying else R.string.dexopt_query))
                    }
                    TextButton(onClick = { pkg = vm.ownPackage }) { Text(stringResource(R.string.dexopt_this_app)) }
                    if (record?.availability?.usable != true) TextButton(onClick = goToRoot) { Text(stringResource(R.string.root_settings)) }
                }
                if (busy) ProgressRow(stringResource(R.string.dexopt_querying))
                when (val d = dexopt) {
                    is ZartResult.Ok -> when (val o = d.value) {
                        is Obs.Value -> {
                            Text(o.value.packageName, style = MaterialTheme.typography.titleSmall)
                            (o.value.primary + o.value.secondary).forEach { f ->
                                InfoRow("Dex", f.path + (f.visibility?.let { " ($it)" } ?: ""), code = true)
                                f.statuses.forEach { s ->
                                    InfoRow("  ${s.isa}${if (s.primaryAbi) " (${stringResource(R.string.dexopt_primary)})" else ""}", "${s.status} · ${s.reason}")
                                }
                            }
                        }
                        is Obs.Missing -> Supporting("${o.status}: ${o.detail}")
                    }
                    is ZartResult.Failed -> Supporting(d.detail)
                    null -> Unit
                }
            }
        }
        item { UnavailableCard(snapshot.byId("art.patches")) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectCard(r: ZartReport) {
    SectionCard(stringResource(R.string.art_inspect_title, r.zartVersion), trailing = { StatusBadge(AvailabilityState.AVAILABLE) }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
            MetricTile(stringResource(R.string.key_api_level), r.apiLevel.show(), r.apiLevel.source, emphasis = Emphasis.HIGH)
            MetricTile(stringResource(R.string.key_page_size), r.pageSizeBytes.show { "${it / 1024} KiB" }, r.pageSizeBytes.source)
        }
        InfoRow(stringResource(R.string.key_process), "${r.uid.show()} · ${r.selinuxContext.show()}", code = true)
        when (val m = r.artModule) {
            is Obs.Value -> m.value.forEach { a ->
                InfoRow(
                    stringResource(if (a.isActive == true) R.string.art_apex_active else R.string.art_apex),
                    "${a.versionCode ?: "?"} · ${stringResource(if (a.isFactory == true) R.string.factory else R.string.updated)} · ${a.modulePath ?: ""}",
                    code = true,
                )
            }
            is Obs.Missing -> InfoRow(stringResource(R.string.art_apex_list), m.show())
        }
        r.isa.forEach { i ->
            InfoRow(stringResource(R.string.art_variant, i.abi), i.variant.show(), code = true)
            InfoRow(stringResource(R.string.art_features, i.abi), i.features.show(), code = true)
            i.arm64?.findings?.forEach { f -> Supporting("${f.severity.uppercase()} · ${f.id}: ${f.message}") }
        }
        when (val p = r.artProperties) {
            is Obs.Value -> ExpandableSection(stringResource(R.string.art_properties, p.value.size)) {
                p.value.forEach { (k, v) -> InfoRow(k, v, code = true) }
            }
            is Obs.Missing -> InfoRow(stringResource(R.string.art_properties, 0), p.show())
        }
        Supporting(stringResource(R.string.art_inspect_note))
    }
}
