// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairenkaiprjctx.app.AppLanguage
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.design.LocalReducedMotion
import com.zairenkaiprjctx.app.design.ConnectedChoice
import com.zairenkaiprjctx.app.design.GroupContent
import com.zairenkaiprjctx.app.design.GroupRow
import com.zairenkaiprjctx.app.design.SegmentedGroup
import com.zairenkaiprjctx.app.design.Detail
import com.zairenkaiprjctx.app.design.ThemeMode
import com.zairenkaiprjctx.app.design.ZairenkaiGlyphs
import com.zairenkaiprjctx.app.design.ZairenkaiSpacing
import com.zairenkaiprjctx.app.design.ZairenkaiShapes
import com.zairenkaiprjctx.app.design.InfoChip
import com.zairenkaiprjctx.app.Overlay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth

/** One searchable setting: where it lives and the words it is found by. */
private data class SettingEntry(@androidx.annotation.StringRes val title: Int, @androidx.annotation.StringRes val place: Int, val target: Overlay, val keywords: String)

private val SETTING_ENTRIES = listOf(
    SettingEntry(R.string.settings_theme, R.string.appearance_title, Overlay.APPEARANCE, "theme dark light system mode tema gelap terang"),
    SettingEntry(R.string.app_theme, R.string.appearance_title, Overlay.APPEARANCE, "palette colour color dynamic material you warna"),
    SettingEntry(R.string.theme_new, R.string.appearance_title, Overlay.THEME_EDITOR, "custom theme colour wheel picker kustom"),
    SettingEntry(R.string.amoled_title, R.string.appearance_title, Overlay.APPEARANCE, "amoled black oled hitam"),
    SettingEntry(R.string.wallpaper_title, R.string.appearance_title, Overlay.WALLPAPER, "wallpaper background photo image latar foto"),
    SettingEntry(R.string.icon_title, R.string.appearance_title, Overlay.APP_ICON, "icon launcher ikon"),
    SettingEntry(R.string.anim_title, R.string.appearance_title, Overlay.APPEARANCE, "animation rings waves orbs animasi"),
    SettingEntry(R.string.font_title, R.string.appearance_title, Overlay.FONTS, "font typeface google fonts huruf"),
    SettingEntry(R.string.ui_scale_title, R.string.appearance_title, Overlay.APPEARANCE, "scale size density zoom skala ukuran"),
    SettingEntry(R.string.glass_title, R.string.nav_settings, Overlay.GLASS, "glass blur frost liquid kaca"),
    SettingEntry(R.string.settings_language, R.string.nav_settings, Overlay.SETTINGS, "language english indonesia bahasa"),
    SettingEntry(R.string.module_title, R.string.nav_settings, Overlay.SYSTEM_MODULE, "module root magisk kernelsu apatch modul"),
)

@Composable
fun SettingsScreen(vm: SettingsViewModel, modules: SystemModuleViewModel, padding: PaddingValues, openModule: () -> Unit, open: (Overlay) -> Unit) {
    val declined by modules.declined.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    var query by rememberSaveable { mutableStateOf("") }
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.settings_search)) },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = ZairenkaiShapes.pill,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotBlank()) {
            val q = query.trim()
            val hits = SETTING_ENTRIES.filter { e -> resources.getString(e.title).contains(q, true) || e.keywords.contains(q, true) }
            item {
                SegmentedGroup(title = stringResource(R.string.settings_results), icon = Icons.Filled.Search) {
                    if (hits.isEmpty()) row { GroupContent { Detail(stringResource(R.string.settings_no_results)) } }
                    hits.forEach { e ->
                        row { GroupRow(stringResource(e.title), supporting = stringResource(e.place), onClick = { query = ""; open(e.target) }, chevron = true) }
                    }
                }
            }
            return@LazyColumn
        }
        item {
            SegmentedGroup(title = stringResource(R.string.settings_appearance), icon = ZairenkaiGlyphs.sliders) {
                row {
                    GroupRow(
                        stringResource(R.string.appearance_title),
                        supporting = stringResource(R.string.appearance_row),
                        icon = ZairenkaiGlyphs.spark,
                        onClick = { open(Overlay.APPEARANCE) },
                        chevron = true,
                    )
                }
                row {
                    GroupRow(
                        stringResource(R.string.glass_title),
                        supporting = stringResource(if (theme.glass.enabled) R.string.glass_row_on else R.string.glass_row_off),
                        icon = ZairenkaiGlyphs.tiles,
                        onClick = { open(Overlay.GLASS) },
                        chevron = true,
                        action = { InfoChip(stringResource(R.string.experimental), color = MaterialTheme.colorScheme.tertiaryContainer) },
                    )
                }
                row { GroupRow(stringResource(R.string.settings_motion), supporting = stringResource(if (LocalReducedMotion.current) R.string.settings_motion_reduced else R.string.settings_motion_full)) }
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.settings_language), icon = ZairenkaiGlyphs.document) {
                row {
                    GroupContent {
                        val activity = context.findActivity()
                        val current = AppLanguage.current(context)
                        val languages = AppLanguage.entries
                        ConnectedChoice(
                            options = languages.map { stringResource(it.label) },
                            selected = languages.indexOf(current),
                            onSelect = { i -> if (languages[i] != current && activity != null) AppLanguage.set(activity, languages[i]) },
                            enabled = activity != null,
                        )
                        Detail(stringResource(R.string.settings_language_note))
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                            }) { Text(stringResource(R.string.settings_language_open)) }
                        }
                    }
                }
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.module_title), icon = ZairenkaiGlyphs.box) {
                row {
                    GroupContent {
                        Detail(stringResource(if (declined) R.string.module_settings_declined else R.string.module_settings_note))
                        Row(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                            OutlinedButton(onClick = openModule) { Text(stringResource(R.string.module_settings_open)) }
                            if (declined) OutlinedButton(onClick = modules::reconsider) { Text(stringResource(R.string.module_settings_reoffer)) }
                        }
                    }
                }
            }
        }
    }
}

/** The hosting activity, if any (none in a preview or a test host without one). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
