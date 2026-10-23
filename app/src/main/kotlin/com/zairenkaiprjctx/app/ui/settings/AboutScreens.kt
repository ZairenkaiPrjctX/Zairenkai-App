// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui.settings

import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairenkaiprjctx.app.AppContainer
import com.zairenkaiprjctx.app.BuildConfig
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.design.ChoiceCard
import com.zairenkaiprjctx.app.design.CodeValue
import com.zairenkaiprjctx.app.design.Detail
import com.zairenkaiprjctx.app.design.ExpandableSection
import com.zairenkaiprjctx.app.design.GroupContent
import com.zairenkaiprjctx.app.design.GroupRow
import com.zairenkaiprjctx.app.design.HeroDefaults
import com.zairenkaiprjctx.app.design.HeroPanel
import com.zairenkaiprjctx.app.design.InfoChip
import com.zairenkaiprjctx.app.design.LocalStatusColors
import com.zairenkaiprjctx.app.design.Meter
import com.zairenkaiprjctx.app.design.SectionCard
import com.zairenkaiprjctx.app.design.SegmentedGroup
import com.zairenkaiprjctx.app.design.ShapeBadge
import com.zairenkaiprjctx.app.design.TileGrid
import com.zairenkaiprjctx.app.design.WorkingBar
import com.zairenkaiprjctx.app.design.ZairenkaiGlyphs
import com.zairenkaiprjctx.app.design.ZairenkaiLogo
import com.zairenkaiprjctx.app.design.ZairenkaiShapes
import com.zairenkaiprjctx.app.design.ZairenkaiSpacing
import com.zairenkaiprjctx.app.design.ZairenkaiTextStyles
import com.zairenkaiprjctx.app.design.entrance
import com.zairenkaiprjctx.app.design.rememberEntrance
import com.zairenkaiprjctx.app.update.ApkInspector
import com.zairenkaiprjctx.app.update.UpdateController
import com.zairenkaiprjctx.app.update.UpdateNotifier
import com.zairenkaiprjctx.app.update.UpdateState
import com.zairenkaiprjctx.integration.update.AppRelease
import com.zairenkaiprjctx.integration.update.AppVersion
import com.zairenkaiprjctx.integration.update.NoteBlock
import com.zairenkaiprjctx.integration.update.Notes
import com.zairenkaiprjctx.integration.update.ReleaseSource
import com.zairenkaiprjctx.integration.update.UpdateChannel

/** "0.2.0-beta.1 (20001)": the version name and Android's version code. */
@Composable
fun fullVersion(): String = stringResource(R.string.about_version_full, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

/** Release notes as text blocks: headings, bullets and paragraphs, never HTML. */
@Composable
fun ReleaseNotes(blocks: List<NoteBlock>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.xs)) {
        blocks.forEach { b ->
            when (b) {
                is NoteBlock.Heading -> Text(
                    b.text,
                    style = if (b.level <= 2) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = ZairenkaiSpacing.s).semantics { heading() },
                )
                is NoteBlock.Bullet -> Row(Modifier.padding(start = (b.depth * 16).dp), horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(b.text, style = MaterialTheme.typography.bodyMedium)
                }
                is NoteBlock.Paragraph -> Text(b.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun open(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun relativeTime(ms: Long): String =
    if (ms <= 0) stringResource(R.string.update_never) else DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** Settings › Updates: status, the offered release with its notes, channel and daily check. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdatesScreen(updates: UpdateController, padding: PaddingValues) {
    val state by updates.state.collectAsStateWithLifecycle()
    val settings by updates.prefs.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canNotify by remember { mutableStateOf(UpdateNotifier.canNotify(context)) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = UpdateNotifier.canNotify(context) }
    LifecycleResumeEffect(Unit) {
        canNotify = UpdateNotifier.canNotify(context)
        updates.recheckPermission() // back from "Install unknown apps"
        onPauseOrDispose {}
    }
    val entrance = rememberEntrance()
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
        item { UpdateHero(state, settings.lastCheck, Modifier.entrance(entrance, 0)) }
        item { UpdateActions(updates, state, Modifier.entrance(entrance, 1)) }
        val release = when (val s = state) {
            is UpdateState.Available -> s.release
            is UpdateState.Downloading -> s.release
            is UpdateState.Ready -> s.release
            is UpdateState.NeedsInstallPermission -> s.release
            is UpdateState.Installing -> s.release
            is UpdateState.Failed -> s.release
            else -> null
        }
        if (release != null) item { ReleaseCard(release, Modifier.entrance(entrance, 2)) }
        item {
            Column(Modifier.entrance(entrance, 3), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m)) {
                Text(stringResource(R.string.update_channel), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                TileGrid(UpdateChannel.entries) { c, mod ->
                    ChoiceCard(
                        icon = if (c == UpdateChannel.STABLE) ZairenkaiGlyphs.shield else ZairenkaiGlyphs.spark,
                        title = stringResource(if (c == UpdateChannel.STABLE) R.string.update_channel_stable else R.string.update_channel_beta),
                        body = stringResource(if (c == UpdateChannel.STABLE) R.string.update_channel_stable_body else R.string.update_channel_beta_body),
                        selected = settings.channel == c,
                        onClick = { updates.setChannel(c) },
                        modifier = mod,
                    )
                }
            }
        }
        item {
            SegmentedGroup(Modifier.entrance(entrance, 4), title = stringResource(R.string.update_auto_title), icon = ZairenkaiGlyphs.timer) {
                row {
                    GroupRow(
                        stringResource(R.string.update_auto),
                        supporting = stringResource(R.string.update_auto_body),
                        trailing = { Switch(checked = settings.autoCheck, onCheckedChange = updates::setAutoCheck) },
                    )
                }
                if (settings.autoCheck && !canNotify) {
                    row {
                        GroupRow(
                            stringResource(R.string.update_notify_off),
                            supporting = stringResource(R.string.update_notify_off_body),
                            action = {
                                OutlinedButton(onClick = {
                                    if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else open(context, "package:${context.packageName}")
                                }) { Text(stringResource(R.string.update_notify_allow)) }
                            },
                        )
                    }
                }
            }
        }
        item {
            SegmentedGroup(Modifier.entrance(entrance, 5), title = stringResource(R.string.update_how_title), icon = ZairenkaiGlyphs.lock) {
                row { GroupRow(stringResource(R.string.update_how_source), supporting = stringResource(R.string.update_how_source_body), icon = ZairenkaiGlyphs.document) }
                row { GroupRow(stringResource(R.string.update_how_digest), supporting = stringResource(R.string.update_how_digest_body), icon = ZairenkaiGlyphs.lock) }
                row { GroupRow(stringResource(R.string.update_how_signer), supporting = stringResource(R.string.update_how_signer_body), icon = ZairenkaiGlyphs.approve) }
                row { GroupRow(stringResource(R.string.update_how_confirm), supporting = stringResource(R.string.update_how_confirm_body), icon = ZairenkaiGlyphs.phone) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UpdateHero(state: UpdateState, lastCheck: Long, modifier: Modifier) {
    val status = LocalStatusColors.current
    val (headline, colors) = when (state) {
        is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Ready, is UpdateState.NeedsInstallPermission, is UpdateState.Installing ->
            stringResource(R.string.update_available) to HeroDefaults.tertiary()
        is UpdateState.UpToDate -> stringResource(R.string.update_up_to_date) to HeroDefaults.primary()
        is UpdateState.Checking -> stringResource(R.string.update_checking) to HeroDefaults.secondary()
        is UpdateState.Failed -> stringResource(R.string.update_failed) to HeroDefaults.status(status.warning)
        UpdateState.Idle -> stringResource(R.string.update_idle) to HeroDefaults.primary()
    }
    HeroPanel(
        overline = stringResource(R.string.update_title),
        headline = headline,
        supporting = stringResource(R.string.update_last_check, relativeTime(lastCheck)),
        modifier = modifier,
        colors = colors,
        decoration = MaterialShapes.Burst,
        trailing = {
            ShapeBadge(
                if (state is UpdateState.UpToDate) ZairenkaiGlyphs.approve else ZairenkaiGlyphs.loop,
                MaterialShapes.Cookie9Sided,
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.onPrimary,
                size = 64.dp,
            )
        },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            InfoChip(stringResource(R.string.update_installed, fullVersion()), icon = ZairenkaiGlyphs.phone)
            AppVersion.parse(BuildConfig.VERSION_NAME)?.let { InfoChip(stringResource(if (it.isBeta) R.string.update_build_beta else R.string.update_build_stable), icon = ZairenkaiGlyphs.spark) }
        }
        if (state is UpdateState.Checking || state is UpdateState.Installing) WorkingBar()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UpdateActions(updates: UpdateController, state: UpdateState, modifier: Modifier) {
    val context = LocalContext.current
    SectionCard(stringResource(R.string.update_next), modifier) {
        when (state) {
            UpdateState.Idle, is UpdateState.UpToDate -> Detail(stringResource(R.string.update_next_check))
            UpdateState.Checking -> Detail(stringResource(R.string.update_checking_body))
            is UpdateState.Available -> Detail(
                stringResource(R.string.update_next_download, state.release.version.toString(), Formatter.formatShortFileSize(context, state.release.apk.size)),
            )
            is UpdateState.Downloading -> {
                val f = if (state.total > 0) (state.bytes.toFloat() / state.total).coerceIn(0f, 1f) else 0f
                Meter(f, Modifier.fillMaxWidth())
                Detail(stringResource(R.string.update_downloading, Formatter.formatShortFileSize(context, state.bytes), Formatter.formatShortFileSize(context, state.total)))
            }
            is UpdateState.Ready -> {
                Text(stringResource(R.string.update_verified), style = MaterialTheme.typography.titleSmall)
                Detail(stringResource(R.string.update_verified_body))
                CodeValue(state.sha256)
            }
            is UpdateState.NeedsInstallPermission -> Detail(stringResource(R.string.update_permission_body))
            is UpdateState.Installing -> Detail(stringResource(R.string.update_installing))
            is UpdateState.Failed -> Detail(state.message, maxLines = 6)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            when (state) {
                UpdateState.Idle, is UpdateState.UpToDate -> Button(onClick = { updates.check() }) { Text(stringResource(R.string.update_check_now)) }
                is UpdateState.Available -> {
                    Button(onClick = updates::download) { Text(stringResource(R.string.update_download)) }
                    TextButton(onClick = updates::dismiss) { Text(stringResource(R.string.update_later)) }
                }
                is UpdateState.Ready -> Button(onClick = updates::install) { Text(stringResource(R.string.update_install)) }
                is UpdateState.NeedsInstallPermission -> Button(onClick = updates::openInstallPermission) { Text(stringResource(R.string.update_permission_open)) }
                is UpdateState.Failed -> {
                    if (state.release != null) Button(onClick = updates::download) { Text(stringResource(R.string.update_retry)) }
                    OutlinedButton(onClick = { updates.dismiss(); updates.check() }) { Text(stringResource(R.string.update_check_now)) }
                }
                else -> Unit
            }
            OutlinedButton(onClick = { open(context, ReleaseSource.PAGE) }) { Text(stringResource(R.string.update_open_releases)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReleaseCard(r: AppRelease, modifier: Modifier) {
    val context = LocalContext.current
    val blocks = remember(r.notes) { Notes.parse(r.notes) }
    SectionCard(r.title, modifier, emphasis = com.zairenkaiprjctx.app.design.Emphasis.HIGH) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
            InfoChip(stringResource(R.string.about_version_full, r.version.toString(), r.version.code), icon = ZairenkaiGlyphs.box)
            InfoChip(
                stringResource(if (r.version.isBeta) R.string.update_channel_beta else R.string.update_channel_stable),
                icon = if (r.version.isBeta) ZairenkaiGlyphs.spark else ZairenkaiGlyphs.shield,
                color = if (r.version.isBeta) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            InfoChip(Formatter.formatShortFileSize(context, r.apk.size), icon = ZairenkaiGlyphs.document)
            InfoChip(r.publishedAt.take(10), icon = ZairenkaiGlyphs.timer)
        }
        Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = ZairenkaiSpacing.s))
        if (blocks.isEmpty()) Detail(stringResource(R.string.update_no_notes)) else ReleaseNotes(blocks)
    }
}

/** What Settings › About shows, read from the build and the APK's own assets. */
class AboutInfo(c: AppContainer, context: android.content.Context) {
    val changelog: List<NoteBlock> = runCatching { Notes.entryFor(c.asset("about/CHANGELOG.md"), BuildConfig.VERSION_NAME)?.blocks }.getOrNull().orEmpty()
    /** SHA-256 of the certificate this APK is signed with: compare it with the release's. */
    val signer: String? = runCatching { ApkInspector(context).installed().signers.firstOrNull() }.getOrNull()
    val notices: String? = runCatching { c.asset("licenses/android/NOTICES.txt") }.getOrNull()
    val module: String? = c.moduleCatalog?.base?.let { "${it.version} (${it.versionCode})" }
    val components = c.components
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(c: AppContainer, padding: PaddingValues, openUpdates: () -> Unit) {
    val context = LocalContext.current
    val info = remember { AboutInfo(c, context) }
    val state by c.updates.state.collectAsStateWithLifecycle()
    val entrance = rememberEntrance()
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
        item { AboutHero(Modifier.entrance(entrance, 0)) }
        item {
            SegmentedGroup(Modifier.entrance(entrance, 1)) {
                row {
                    GroupRow(
                        stringResource(R.string.update_title),
                        supporting = when (val s = state) {
                            is UpdateState.Available -> stringResource(R.string.about_update_available, s.release.version.toString())
                            is UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                            is UpdateState.Checking -> stringResource(R.string.update_checking)
                            else -> stringResource(R.string.about_update_row)
                        },
                        icon = ZairenkaiGlyphs.loop,
                        emphasized = state is UpdateState.Available,
                        onClick = openUpdates,
                        chevron = true,
                    )
                }
            }
        }
        if (info.changelog.isNotEmpty()) {
            item {
                SectionCard(stringResource(R.string.about_whats_new, BuildConfig.VERSION_NAME), Modifier.entrance(entrance, 2)) {
                    ReleaseNotes(info.changelog)
                }
            }
        }
        item {
            SegmentedGroup(Modifier.entrance(entrance, 3), title = stringResource(R.string.about_build), icon = ZairenkaiGlyphs.chip) {
                row { GroupRow(stringResource(R.string.about_version), supporting = fullVersion(), icon = ZairenkaiGlyphs.box) }
                row {
                    GroupRow(
                        stringResource(R.string.about_commit),
                        supporting = stringResource(R.string.about_commit_body, BuildConfig.GIT_DATE),
                        icon = ZairenkaiGlyphs.nodes,
                        action = { CodeValue(BuildConfig.GIT_COMMIT) },
                    )
                }
                row {
                    GroupRow(
                        stringResource(R.string.about_android),
                        supporting = stringResource(R.string.about_android_body, Build.VERSION.SDK_INT, context.applicationInfo.minSdkVersion, context.applicationInfo.targetSdkVersion),
                        icon = ZairenkaiGlyphs.phone,
                    )
                }
                row { GroupRow(stringResource(R.string.about_package), supporting = context.packageName, icon = ZairenkaiGlyphs.document) }
                info.signer?.let { s -> row { GroupRow(stringResource(R.string.about_signer), supporting = stringResource(R.string.about_signer_body), icon = ZairenkaiGlyphs.approve, action = { CodeValue(s) }) } }
            }
        }
        item {
            SegmentedGroup(Modifier.entrance(entrance, 4), title = stringResource(R.string.about_components), icon = ZairenkaiGlyphs.nodes) {
                row { GroupRow(stringResource(R.string.module_title), supporting = info.module ?: stringResource(R.string.about_not_packaged), icon = ZairenkaiGlyphs.box) }
                val comp = info.components
                row { GroupRow("zart", supporting = listOfNotNull(comp?.zartVersion, comp?.zartCommit?.take(12)).joinToString(" · ").ifEmpty { stringResource(R.string.about_not_packaged) }, icon = ZairenkaiGlyphs.chip) }
                row { GroupRow("zthermal", supporting = listOfNotNull(comp?.zthermalVersion, comp?.zthermalCommit?.take(12)).joinToString(" · ").ifEmpty { stringResource(R.string.about_not_packaged) }, icon = ZairenkaiGlyphs.thermometer) }
                row { GroupRow("AFID", supporting = comp?.afidCommit?.take(12) ?: stringResource(R.string.about_not_packaged), icon = ZairenkaiGlyphs.database) }
            }
        }
        item {
            SegmentedGroup(Modifier.entrance(entrance, 5), title = stringResource(R.string.about_privacy), icon = ZairenkaiGlyphs.lock) {
                row { GroupRow(stringResource(R.string.about_privacy_data), supporting = stringResource(R.string.about_privacy_data_body), icon = ZairenkaiGlyphs.shield) }
                row { GroupRow(stringResource(R.string.about_privacy_network), supporting = stringResource(R.string.about_privacy_network_body), icon = ZairenkaiGlyphs.pulse) }
                row { GroupRow(stringResource(R.string.about_privacy_root), supporting = stringResource(R.string.about_privacy_root_body), icon = ZairenkaiGlyphs.lock) }
            }
        }
        item {
            SegmentedGroup(Modifier.entrance(entrance, 6), title = stringResource(R.string.about_links), icon = ZairenkaiGlyphs.spark) {
                row { GroupRow(stringResource(R.string.about_source), supporting = "github.com/${ReleaseSource.REPOSITORY}", icon = ZairenkaiGlyphs.document, onClick = { open(context, "https://github.com/${ReleaseSource.REPOSITORY}") }, chevron = true) }
                row { GroupRow(stringResource(R.string.about_releases), supporting = stringResource(R.string.about_releases_body), icon = ZairenkaiGlyphs.box, onClick = { open(context, ReleaseSource.PAGE) }, chevron = true) }
                row { GroupRow(stringResource(R.string.about_issues), supporting = stringResource(R.string.about_issues_body), icon = ZairenkaiGlyphs.tool, onClick = { open(context, "https://github.com/${ReleaseSource.REPOSITORY}/issues") }, chevron = true) }
            }
        }
        item {
            SectionCard(stringResource(R.string.about_licences), Modifier.entrance(entrance, 6), emphasis = com.zairenkaiprjctx.app.design.Emphasis.LOW) {
                Detail(stringResource(R.string.about_licences_body), maxLines = 6)
                info.notices?.let { n ->
                    ExpandableSection(stringResource(R.string.about_notices)) {
                        Text(n, style = ZairenkaiTextStyles.code, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.about_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(ZairenkaiSpacing.l),
            )
        }
    }
}

/** The brand hero: the logo large on its gradient, the name, the tagline and the full version. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutHero(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val beta = AppVersion.parse(BuildConfig.VERSION_NAME)?.isBeta == true
    Surface(shape = ZairenkaiShapes.hero, color = cs.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Box {
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(cs.primaryContainer, cs.surfaceContainerHigh))))
            Column(
                Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = ZairenkaiSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m),
            ) {
                ZairenkaiLogo(112.dp, label = stringResource(R.string.brand_logo))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.brand_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                    InfoChip(fullVersion(), icon = ZairenkaiGlyphs.box, color = cs.surfaceContainerHighest)
                    InfoChip(
                        stringResource(if (beta) R.string.update_build_beta else R.string.update_build_stable),
                        icon = if (beta) ZairenkaiGlyphs.spark else ZairenkaiGlyphs.shield,
                        color = if (beta) cs.tertiaryContainer else cs.surfaceContainerHighest,
                    )
                    InfoChip("Apache-2.0", icon = ZairenkaiGlyphs.document, color = cs.surfaceContainerHighest)
                }
            }
        }
    }
}
