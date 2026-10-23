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
import com.zairenkaiprjctx.app.design.Supporting
import com.zairenkaiprjctx.app.design.ThemeMode
import com.zairenkaiprjctx.app.design.ZairenkaiSpacing

@Composable
fun SettingsScreen(vm: SettingsViewModel, modules: SystemModuleViewModel, padding: PaddingValues, openModule: () -> Unit) {
    val declined by modules.declined.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
        item {
            SegmentedGroup(title = stringResource(R.string.settings_appearance)) {
                row {
                    GroupContent {
                        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                        val modes = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
                        ConnectedChoice(
                            options = modes.map { stringResource(it.second) },
                            selected = modes.indexOfFirst { it.first == theme.mode },
                            onSelect = { vm.setMode(modes[it].first) },
                        )
                    }
                }
                row {
                    val dynamicLabel = stringResource(R.string.settings_dynamic)
                    GroupRow(
                        dynamicLabel,
                        supporting = stringResource(if (dynamicSupported) R.string.settings_dynamic_on else R.string.settings_dynamic_unsupported),
                        trailing = {
                            Switch(
                                checked = theme.dynamicColor && dynamicSupported,
                                onCheckedChange = vm::setDynamic,
                                enabled = dynamicSupported,
                                modifier = Modifier.semantics { contentDescription = dynamicLabel },
                            )
                        },
                    )
                }
                row { GroupRow(stringResource(R.string.settings_motion), supporting = stringResource(if (LocalReducedMotion.current) R.string.settings_motion_reduced else R.string.settings_motion_full)) }
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.settings_language)) {
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
                        Supporting(stringResource(R.string.settings_language_note))
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
            SegmentedGroup(title = stringResource(R.string.module_title)) {
                row {
                    GroupContent {
                        Supporting(stringResource(if (declined) R.string.module_settings_declined else R.string.module_settings_note))
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
