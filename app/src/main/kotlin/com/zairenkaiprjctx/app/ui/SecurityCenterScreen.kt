// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zairenkaiprjctx.app.BuildConfig
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.design.Detail
import com.zairenkaiprjctx.app.design.GroupContent
import com.zairenkaiprjctx.app.design.SegmentedGroup
import com.zairenkaiprjctx.app.design.ZairenkaiGlyphs
import com.zairenkaiprjctx.app.design.ZairenkaiSpacing
import com.zairenkaiprjctx.integration.systemmodule.SignatureVerdict

/** Local, read-only security facts. No scan or remote report is implied by opening this screen. */
@Composable
fun SecurityCenterScreen(padding: PaddingValues, modules: SystemModuleViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l),
    ) {
        item {
            Text(
                stringResource(R.string.security_center_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SegmentedGroup(title = stringResource(R.string.security_device_title), icon = ZairenkaiGlyphs.phone) {
                row {
                    GroupContent {
                        Detail(stringResource(R.string.security_android_release, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
                        Detail(stringResource(R.string.security_patch_level, Build.VERSION.SECURITY_PATCH.ifBlank { stringResource(R.string.security_unknown) }))
                        Detail(stringResource(R.string.security_kernel, System.getProperty("os.version") ?: stringResource(R.string.security_unknown)))
                        Detail(stringResource(R.string.security_app_version, BuildConfig.VERSION_NAME))
                    }
                }
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.security_findings_title), icon = ZairenkaiGlyphs.lock) {
                row {
                    GroupContent {
                        Text(stringResource(R.string.security_not_scanned), style = MaterialTheme.typography.titleMedium)
                        Detail(stringResource(R.string.security_feed_unavailable))
                    }
                }
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.security_integrity_title), icon = ZairenkaiGlyphs.box) {
                row {
                    GroupContent {
                        Text(stringResource(R.string.security_unknown), style = MaterialTheme.typography.titleMedium)
                        Detail(stringResource(R.string.security_integrity_unavailable))
                    }
                }
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.security_catalog_title), icon = ZairenkaiGlyphs.lock) {
                row {
                    GroupContent {
                        val verdict = modules.signature
                        val status = when (verdict) {
                            is SignatureVerdict.Verified -> stringResource(R.string.security_catalog_verified, verdict.owner)
                            SignatureVerdict.Unsigned, SignatureVerdict.NoTrustedKey,
                            is SignatureVerdict.UnknownKey, is SignatureVerdict.Invalid -> stringResource(R.string.security_catalog_unverified)
                            null -> stringResource(R.string.security_unknown)
                        }
                        Text(status, style = MaterialTheme.typography.titleMedium)
                        Detail(
                            stringResource(
                                if (modules.packaged) R.string.security_catalog_scope
                                else R.string.security_catalog_not_packaged,
                            ),
                        )
                    }
                }
            }
        }
    }
}
