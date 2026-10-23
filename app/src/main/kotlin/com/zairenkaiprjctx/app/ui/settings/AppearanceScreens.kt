// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.appearance.LauncherIcon
import com.zairenkaiprjctx.app.design.BackgroundAnimation
import com.zairenkaiprjctx.app.design.ChipChoice
import com.zairenkaiprjctx.app.design.ColorPickerPanel
import com.zairenkaiprjctx.app.design.ConnectedChoice
import com.zairenkaiprjctx.app.design.Detail
import com.zairenkaiprjctx.app.design.ExpandableSection
import com.zairenkaiprjctx.app.design.FontSettings
import com.zairenkaiprjctx.app.design.FontSource
import com.zairenkaiprjctx.app.design.GlassSettings
import com.zairenkaiprjctx.app.design.GroupContent
import com.zairenkaiprjctx.app.design.GroupHeader
import com.zairenkaiprjctx.app.design.GroupRow
import com.zairenkaiprjctx.app.design.InfoChip
import com.zairenkaiprjctx.app.design.LabeledSlider
import com.zairenkaiprjctx.app.design.Palette
import com.zairenkaiprjctx.app.design.SegmentedGroup
import com.zairenkaiprjctx.app.design.ThemeMode
import com.zairenkaiprjctx.app.design.WallpaperSource
import com.zairenkaiprjctx.app.design.ZairenkaiColorScheme
import com.zairenkaiprjctx.app.design.ZairenkaiGlyphs
import com.zairenkaiprjctx.app.design.ZairenkaiShapes
import com.zairenkaiprjctx.app.design.ZairenkaiSpacing
import com.zairenkaiprjctx.app.design.amoled
import com.zairenkaiprjctx.app.design.drawBuiltInWallpaper
import com.zairenkaiprjctx.app.design.glassBar
import com.zairenkaiprjctx.app.design.googleFontFamily
import com.zairenkaiprjctx.app.design.isDark
import com.zairenkaiprjctx.app.design.rememberGlassState
import com.zairenkaiprjctx.app.design.schemeFromSeed
import com.zairenkaiprjctx.app.design.glassSource
import com.zairenkaiprjctx.app.ui.SettingsViewModel
import kotlin.math.roundToInt

/** A palette as the user sees it: a tiny screen drawn in its colours. */
@Composable
fun ThemePreviewCard(scheme: ColorScheme, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .width(84.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(width = 76.dp, height = 112.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.background)
                .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant), RoundedCornerShape(18.dp))
                .padding(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(scheme.surfaceContainerHighest))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(scheme.surfaceContainer).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(26.dp).height(10.dp).clip(CircleShape).background(scheme.primary))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(9.dp).clip(CircleShape).background(scheme.tertiary))
                }
                Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(8.dp)).background(scheme.primaryContainer))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(scheme.surfaceContainerHigh))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(Modifier.size(8.dp).clip(CircleShape).background(scheme.secondary)) }
            }
            if (selected) {
                Surface(color = cs.primary, contentColor = cs.onPrimary, shape = CircleShape, modifier = Modifier.size(20.dp).align(Alignment.TopStart)) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(3.dp))
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) cs.primary else cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Settings › Appearance: theme, palette, wallpaper, icon, animation, AMOLED, font and scale. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(vm: SettingsViewModel, padding: PaddingValues, openEditor: (String?) -> Unit, openWallpaper: () -> Unit, openIcon: () -> Unit, openFonts: () -> Unit) {
    val s by vm.theme.collectAsStateWithLifecycle()
    val icon by vm.launcherIcon.collectAsStateWithLifecycle()
    val dark = isDark(s)
    val context = LocalContext.current
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
        item {
            SegmentedGroup(title = stringResource(R.string.settings_theme), icon = ZairenkaiGlyphs.sliders) {
                row {
                    GroupContent {
                        val modes = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
                        ConnectedChoice(modes.map { stringResource(it.second) }, modes.indexOfFirst { it.first == s.mode }, { vm.setMode(modes[it].first) })
                    }
                }
                row {
                    Column(Modifier.padding(vertical = ZairenkaiSpacing.l)) {
                        Text(
                            stringResource(R.string.app_theme),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = ZairenkaiSpacing.l, vertical = ZairenkaiSpacing.xs),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ZairenkaiSpacing.l, vertical = ZairenkaiSpacing.s),
                            horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s),
                            modifier = Modifier.selectableGroup(),
                        ) {
                            if (dynamicSupported) {
                                item {
                                    val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                                    ThemePreviewCard(if (dark && s.amoled) scheme.amoled() else scheme, stringResource(R.string.palette_dynamic), s.dynamicColor, vm::selectDynamic)
                                }
                            }
                            items(Palette.entries) { p ->
                                val scheme = if (p == Palette.ZAIRENKAI) {
                                    (if (dark) ZairenkaiColorScheme.dark else ZairenkaiColorScheme.light).let { if (dark && s.amoled) it.amoled() else it }
                                } else {
                                    schemeFromSeed(Color(p.seed), dark, s.amoled)
                                }
                                ThemePreviewCard(scheme, stringResource(p.label), !s.dynamicColor && s.custom == null && s.palette == p, { vm.selectPalette(p) })
                            }
                            items(s.customThemes, key = { it.id }) { t ->
                                ThemePreviewCard(schemeFromSeed(Color(t.seed), dark, s.amoled), t.name, !s.dynamicColor && s.customId == t.id, { vm.selectCustom(t.id) })
                            }
                            item { NewThemeCard { openEditor(null) } }
                        }
                        s.custom?.let { t ->
                            Row(Modifier.padding(horizontal = ZairenkaiSpacing.l), horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                                OutlinedButton(onClick = { openEditor(t.id) }) { Text(stringResource(R.string.theme_edit, t.name), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                TextButton(onClick = { vm.deleteCustom(t.id) }) { Text(stringResource(R.string.theme_delete)) }
                            }
                        }
                    }
                }
                row {
                    GroupRow(
                        stringResource(R.string.amoled_title),
                        supporting = stringResource(R.string.amoled_body),
                        icon = ZairenkaiGlyphs.phone,
                        trailing = { Switch(checked = s.amoled, onCheckedChange = vm::setAmoled) },
                    )
                }
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.appearance_personalise), icon = ZairenkaiGlyphs.spark) {
                row {
                    GroupRow(
                        stringResource(R.string.wallpaper_title),
                        supporting = stringResource(s.wallpaper.source.label),
                        icon = ZairenkaiGlyphs.document,
                        onClick = openWallpaper,
                        chevron = true,
                    )
                }
                row {
                    GroupRow(
                        stringResource(R.string.icon_title),
                        supporting = stringResource(R.string.icon_current, stringResource(icon.label)),
                        onClick = openIcon,
                        chevron = true,
                        trailing = { IconPreview(icon.background, icon.foreground, 40.dp) },
                    )
                }
                row {
                    GroupContent {
                        Text(stringResource(R.string.anim_title), style = MaterialTheme.typography.titleMedium)
                        Detail(stringResource(R.string.anim_body), maxLines = 2)
                        ChipChoice(BackgroundAnimation.entries.map { stringResource(it.label) }, s.animation.ordinal, { vm.setAnimation(BackgroundAnimation.entries[it]) })
                    }
                }
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.appearance_text), icon = ZairenkaiGlyphs.document) {
                row {
                    GroupRow(
                        stringResource(R.string.font_title),
                        supporting = stringResource(
                            R.string.font_current,
                            when (s.font.source) {
                                FontSource.BUILT_IN -> stringResource(R.string.font_builtin)
                                FontSource.SYSTEM -> stringResource(R.string.font_system)
                                FontSource.GOOGLE -> s.font.googleFamily ?: stringResource(R.string.font_builtin)
                            },
                        ),
                        onClick = openFonts,
                        chevron = true,
                    )
                }
                row {
                    GroupContent {
                        var scale by remember(s.uiScale) { mutableFloatStateOf(s.uiScale) }
                        LabeledSlider(
                            stringResource(R.string.ui_scale_title),
                            scale,
                            "${(scale * 100).roundToInt()} %",
                            range = 0.85f..1.2f,
                            steps = 6,
                            onChange = { scale = it },
                        )
                        Detail(stringResource(R.string.ui_scale_body), maxLines = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                            Button(onClick = { vm.setUiScale(scale) }, enabled = scale != s.uiScale) { Text(stringResource(R.string.apply)) }
                            TextButton(onClick = { scale = 1f; vm.setUiScale(1f) }, enabled = s.uiScale != 1f) { Text(stringResource(R.string.reset_default)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewThemeCard(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(84.dp).clickable(role = Role.Button, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(width = 76.dp, height = 112.dp).clip(RoundedCornerShape(18.dp)).border(BorderStroke(1.dp, cs.outline), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = null, tint = cs.onSurfaceVariant) }
        Text(stringResource(R.string.theme_new), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** Settings › Appearance › New custom theme: a name and a seed colour, with a live preview. */
@Composable
fun ThemeEditorScreen(vm: SettingsViewModel, editId: String?, padding: PaddingValues, done: () -> Unit) {
    val s by vm.theme.collectAsStateWithLifecycle()
    val existing = s.customThemes.firstOrNull { it.id == editId }
    val defaultName = stringResource(R.string.theme_default_name, s.customThemes.size + 1)
    var name by rememberSaveable(editId) { mutableStateOf(existing?.name ?: defaultName) }
    var seed by rememberSaveable(editId, stateSaver = ColorSaver) { mutableStateOf(existing?.let { Color(it.seed) } ?: Color(0xFF6750A4)) }
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
        item {
            SegmentedGroup(title = stringResource(R.string.theme_live_preview), icon = ZairenkaiGlyphs.phone) {
                row {
                    Row(Modifier.fillMaxWidth().padding(ZairenkaiSpacing.l), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ThemePreviewCard(schemeFromSeed(seed, dark = false), stringResource(R.string.theme_light), selected = false, onClick = {})
                        ThemePreviewCard(schemeFromSeed(seed, dark = true, amoled = s.amoled), stringResource(R.string.theme_dark), selected = false, onClick = {})
                    }
                }
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.theme_colour), icon = ZairenkaiGlyphs.sliders) {
                row {
                    GroupContent {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            label = { Text(stringResource(R.string.theme_name)) },
                            singleLine = true,
                            shape = ZairenkaiShapes.metric,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                row { GroupContent { ColorPickerPanel(seed, { seed = it }) } }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                Button(onClick = { vm.saveCustom(editId, name, seed); done() }) { Text(stringResource(R.string.theme_save)) }
                if (existing != null) TextButton(onClick = { vm.deleteCustom(existing.id); done() }) { Text(stringResource(R.string.theme_delete)) }
            }
        }
    }
}

private val ColorSaver = androidx.compose.runtime.saveable.Saver<Color, Int>(save = { it.toArgb() }, restore = { Color(it) })

/** A launcher icon drawn as Android shows it: the brand mark on its background, in a squircle. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IconPreview(background: Color, foreground: Color, size: Dp) {
    Box(Modifier.size(size).clip(MaterialShapes.Cookie4Sided.toShape()).background(background), contentAlignment = Alignment.Center) {
        // The mark spans half of an adaptive icon's visible area, as on the launcher.
        Canvas(Modifier.size(size * 0.46f)) {
            // The Z mark of ic_launcher_foreground (viewport 36..72 of 108), scaled to this box.
            val k = this.size.width / 36f
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f); lineTo(36f * k, 0f); lineTo(36f * k, 7f * k); lineTo(10f * k, 29f * k); lineTo(36f * k, 29f * k)
                lineTo(36f * k, 36f * k); lineTo(0f, 36f * k); lineTo(0f, 29f * k); lineTo(26f * k, 7f * k); lineTo(0f, 7f * k); close()
            }
            drawPath(p, foreground)
        }
    }
}

/** Settings › Appearance › Wallpaper: built-in gradients or a photo, fit or fill, and how much it is dimmed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallpaperScreen(vm: SettingsViewModel, padding: PaddingValues) {
    val s by vm.theme.collectAsStateWithLifecycle()
    val photo by vm.wallpaperPhoto.collectAsStateWithLifecycle()
    var failed by rememberSaveable { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) failed = !vm.usePhoto(uri) }
    val w = s.wallpaper
    val image = remember(photo) { photo?.asImageBitmap() }
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // A phone-shaped preview with the app's background over the wallpaper at the chosen dim.
                Box(
                    Modifier
                        .width(180.dp)
                        .aspectRatio(9f / 19.5f)
                        .clip(RoundedCornerShape(28.dp))
                        .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(28.dp)),
                ) {
                    when {
                        w.source == WallpaperSource.PHOTO && image != null ->
                            Image(image, null, contentScale = if (w.fill) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        w.source != WallpaperSource.NONE && w.source != WallpaperSource.PHOTO -> Canvas(Modifier.fillMaxSize()) { drawBuiltInWallpaper(w.source) }
                    }
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = if (w.source == WallpaperSource.NONE) 1f else w.dim)))
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(70.dp).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)))
                        repeat(3) { Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f))) }
                    }
                    if (w.source == WallpaperSource.NONE) {
                        Text(stringResource(R.string.wallpaper_none), style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.wallpaper_choose), icon = ZairenkaiGlyphs.document) {
                row {
                    GroupContent {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), modifier = Modifier.selectableGroup()) {
                            WallpaperSource.entries.filter { it != WallpaperSource.PHOTO || image != null }.forEach { src ->
                                WallpaperThumb(src, image, selected = w.source == src) { vm.setWallpaper(w.copy(source = src)) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                            Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.wallpaper_pick)) }
                            if (image != null) TextButton(onClick = vm::clearPhoto) { Text(stringResource(R.string.wallpaper_remove_photo)) }
                        }
                        if (failed) Detail(stringResource(R.string.wallpaper_pick_failed))
                        Detail(stringResource(R.string.wallpaper_privacy), maxLines = 3)
                    }
                }
            }
        }
        if (w.source != WallpaperSource.NONE) {
            item {
                SegmentedGroup(title = stringResource(R.string.wallpaper_adjust), icon = ZairenkaiGlyphs.sliders) {
                    row {
                        GroupContent {
                            ConnectedChoice(listOf(stringResource(R.string.wallpaper_fill), stringResource(R.string.wallpaper_fit)), if (w.fill) 0 else 1, { vm.setWallpaper(w.copy(fill = it == 0)) })
                            var dim by remember(w.dim) { mutableFloatStateOf(w.dim) }
                            LabeledSlider(stringResource(R.string.wallpaper_dim), dim, "${(dim * 100).roundToInt()} %", range = 0.2f..0.9f) {
                                dim = it
                                vm.setWallpaper(w.copy(dim = it))
                            }
                            Detail(stringResource(R.string.wallpaper_dim_body), maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperThumb(src: WallpaperSource, photo: androidx.compose.ui.graphics.ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(72.dp).selectable(selected, onClick = onClick, role = Role.RadioButton), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp, 96.dp).clip(RoundedCornerShape(14.dp))
                .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                src == WallpaperSource.PHOTO && photo != null -> Image(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                src == WallpaperSource.NONE -> Icon(Icons.Filled.Clear, contentDescription = null, tint = cs.onSurfaceVariant)
                else -> Canvas(Modifier.fillMaxSize()) { drawBuiltInWallpaper(src) }
            }
            if (selected) {
                Surface(color = cs.primary, contentColor = cs.onPrimary, shape = CircleShape, modifier = Modifier.size(20.dp).align(Alignment.TopStart).padding(2.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                }
            }
        }
        Text(stringResource(src.label), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}



/** Settings › Appearance › App icon: preview a launcher icon, then apply it. */
@Composable
fun AppIconScreen(vm: SettingsViewModel, padding: PaddingValues) {
    val current by vm.launcherIcon.collectAsStateWithLifecycle()
    var chosen by rememberSaveable { mutableStateOf(current) }
    var custom by rememberSaveable(stateSaver = ColorSaver) { mutableStateOf(chosen.background) }
    val cs = MaterialTheme.colorScheme
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
        item {
            Surface(shape = ZairenkaiShapes.hero, modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.background(Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer))).padding(ZairenkaiSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m),
                ) {
                    InfoChip(stringResource(R.string.icon_launcher_preview))
                    IconPreview(chosen.background, chosen.foreground, 104.dp)
                    Text(stringResource(chosen.label), style = MaterialTheme.typography.titleLarge)
                    InfoChip(stringResource(if (chosen == current) R.string.icon_in_use else R.string.icon_not_applied))
                }
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.icon_variants), icon = ZairenkaiGlyphs.tiles) {
                row {
                    LazyRow(
                        contentPadding = PaddingValues(ZairenkaiSpacing.l),
                        horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.m),
                        modifier = Modifier.selectableGroup(),
                    ) {
                        items(LauncherIcon.entries) { v ->
                            val label = stringResource(v.label)
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(BorderStroke(if (v == chosen) 3.dp else 0.dp, if (v == chosen) cs.primary else Color.Transparent), RoundedCornerShape(18.dp))
                                    .selectable(v == chosen, onClick = { chosen = v; custom = v.background }, role = Role.RadioButton)
                                    .padding(6.dp)
                                    .semantics { contentDescription = label },
                            ) { IconPreview(v.background, v.foreground, 52.dp) }
                        }
                    }
                }
                row {
                    GroupContent {
                        ExpandableSection(stringResource(R.string.icon_custom)) {
                            Detail(stringResource(R.string.icon_custom_body), maxLines = 3)
                            ColorPickerPanel(custom, {
                                custom = it
                                chosen = LauncherIcon.closestTo(it)
                            })
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                Button(onClick = { vm.applyIcon(chosen) }, enabled = chosen != current, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.apply)) }
                Detail(stringResource(R.string.icon_note), maxLines = 3)
            }
        }
    }
}

/** Google Fonts the app offers, with their category. Names only: nothing is fetched until one is chosen. */
internal val GOOGLE_FONTS: List<Pair<String, Int>> = listOf(
    "Inter" to R.string.font_cat_sans, "Roboto" to R.string.font_cat_sans, "Montserrat" to R.string.font_cat_sans,
    "Poppins" to R.string.font_cat_sans, "Nunito" to R.string.font_cat_sans, "Lato" to R.string.font_cat_sans,
    "Open Sans" to R.string.font_cat_sans, "Raleway" to R.string.font_cat_sans, "Work Sans" to R.string.font_cat_sans,
    "DM Sans" to R.string.font_cat_sans, "Manrope" to R.string.font_cat_sans, "Rubik" to R.string.font_cat_sans,
    "Outfit" to R.string.font_cat_sans, "Plus Jakarta Sans" to R.string.font_cat_sans, "Lexend" to R.string.font_cat_sans,
    "Figtree" to R.string.font_cat_sans, "Urbanist" to R.string.font_cat_sans, "Sora" to R.string.font_cat_sans,
    "Space Grotesk" to R.string.font_cat_sans, "IBM Plex Sans" to R.string.font_cat_sans, "Source Sans 3" to R.string.font_cat_sans,
    "Noto Sans" to R.string.font_cat_sans, "Ubuntu" to R.string.font_cat_sans, "Quicksand" to R.string.font_cat_sans,
    "Karla" to R.string.font_cat_sans, "Mulish" to R.string.font_cat_sans, "Barlow" to R.string.font_cat_sans,
    "Josefin Sans" to R.string.font_cat_sans, "Exo 2" to R.string.font_cat_sans, "Kanit" to R.string.font_cat_sans,
    "Fira Sans" to R.string.font_cat_sans, "Archivo" to R.string.font_cat_sans, "Red Hat Display" to R.string.font_cat_sans,
    "Albert Sans" to R.string.font_cat_sans, "Onest" to R.string.font_cat_sans, "Lora" to R.string.font_cat_serif,
    "Merriweather" to R.string.font_cat_serif, "Playfair Display" to R.string.font_cat_serif, "Roboto Slab" to R.string.font_cat_serif,
    "Noto Serif" to R.string.font_cat_serif, "Source Serif 4" to R.string.font_cat_serif, "Crimson Pro" to R.string.font_cat_serif,
    "JetBrains Mono" to R.string.font_cat_mono, "Fira Code" to R.string.font_cat_mono, "Roboto Mono" to R.string.font_cat_mono,
    "Space Mono" to R.string.font_cat_mono, "IBM Plex Mono" to R.string.font_cat_mono, "Source Code Pro" to R.string.font_cat_mono,
    "Comfortaa" to R.string.font_cat_display, "Orbitron" to R.string.font_cat_display, "Audiowide" to R.string.font_cat_display,
    "Righteous" to R.string.font_cat_display, "Bebas Neue" to R.string.font_cat_display, "Oswald" to R.string.font_cat_display,
    "Anton" to R.string.font_cat_display, "Pacifico" to R.string.font_cat_handwriting, "Caveat" to R.string.font_cat_handwriting,
    "Dancing Script" to R.string.font_cat_handwriting, "Comic Neue" to R.string.font_cat_handwriting,
)

/** Settings › Appearance › App font. */
@Composable
fun FontScreen(vm: SettingsViewModel, padding: PaddingValues) {
    val s by vm.theme.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val f = s.font
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s), modifier = Modifier.selectableGroup()) {
        item {
            SegmentedGroup(title = stringResource(R.string.font_preview), icon = ZairenkaiGlyphs.document) {
                row {
                    GroupContent {
                        Text(stringResource(R.string.font_sample_title), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.font_sample_body), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        item { GroupHeader(stringResource(R.string.font_app_fonts), icon = ZairenkaiGlyphs.sliders) }
        item { FontRow(stringResource(R.string.font_builtin), stringResource(R.string.font_builtin_body), f.source == FontSource.BUILT_IN) { vm.setFont(FontSettings(FontSource.BUILT_IN)) } }
        item { FontRow(stringResource(R.string.font_system), stringResource(R.string.font_system_body), f.source == FontSource.SYSTEM) { vm.setFont(FontSettings(FontSource.SYSTEM)) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                GroupHeader(stringResource(R.string.font_google), icon = ZairenkaiGlyphs.database)
                Detail(stringResource(R.string.font_google_body), maxLines = 4)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.font_search)) },
                    singleLine = true,
                    shape = ZairenkaiShapes.pill,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val shown = GOOGLE_FONTS.filter { query.isBlank() || it.first.contains(query.trim(), ignoreCase = true) }
        items(shown, key = { it.first }) { (name, cat) ->
            FontRow(name, stringResource(cat), f.source == FontSource.GOOGLE && f.googleFamily == name) { vm.setFont(FontSettings(FontSource.GOOGLE, name)) }
        }
    }
}

@Composable
private fun FontRow(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (selected) cs.primaryContainer else cs.surfaceContainer,
        contentColor = if (selected) cs.onPrimaryContainer else cs.onSurface,
        shape = ZairenkaiShapes.metric,
        modifier = Modifier.fillMaxWidth().selectable(selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(horizontal = ZairenkaiSpacing.l, vertical = ZairenkaiSpacing.m), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.padding(start = ZairenkaiSpacing.m).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(body, style = MaterialTheme.typography.bodySmall, color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Settings › Liquid glass (experimental): a frosted navigation bar over the screens. */
@Composable
fun GlassScreen(vm: SettingsViewModel, padding: PaddingValues) {
    val s by vm.theme.collectAsStateWithLifecycle()
    val g = s.glass
    val set = { n: GlassSettings -> vm.setGlass(n) }
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.l)) {
        item { GlassPreview(g) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZairenkaiSpacing.s)) {
                InfoChip(stringResource(R.string.experimental), color = MaterialTheme.colorScheme.tertiaryContainer)
                Detail(stringResource(R.string.glass_experimental_note), Modifier.weight(1f), maxLines = 3)
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.glass_general), icon = ZairenkaiGlyphs.tiles) {
                row { GroupRow(stringResource(R.string.glass_bar), supporting = stringResource(R.string.glass_bar_body), trailing = { Switch(g.enabled, { set(g.copy(enabled = it)) }) }) }
                row { GroupRow(stringResource(R.string.glass_top), supporting = stringResource(R.string.glass_top_body), trailing = { Switch(g.topBar, { set(g.copy(topBar = it)) }, enabled = g.enabled) }) }
            }
        }
        item {
            SegmentedGroup(title = stringResource(R.string.glass_material), icon = ZairenkaiGlyphs.sliders) {
                row { GroupContent { LabeledSlider(stringResource(R.string.glass_frost), g.frost, "${(g.frost * 100).roundToInt()} %") { set(g.copy(frost = it)) } } }
                row { GroupContent { LabeledSlider(stringResource(R.string.glass_tint), g.tint, "${(g.tint * 100).roundToInt()} %") { set(g.copy(tint = it)) } } }
                row { GroupContent { LabeledSlider(stringResource(R.string.glass_highlight), g.highlight, "${(g.highlight * 100).roundToInt()} %") { set(g.copy(highlight = it)) } } }
                row { GroupContent { LabeledSlider(stringResource(R.string.glass_noise), g.noise, "${(g.noise * 100).roundToInt()} %", range = 0f..0.4f) { set(g.copy(noise = it)) } } }
            }
        }
        item {
            TextButton(onClick = { set(GlassSettings(enabled = g.enabled)) }) { Text(stringResource(R.string.reset_default)) }
        }
    }
}

/** The glass over a checkerboard, so frost, tint and rim are easy to judge. */
@Composable
private fun GlassPreview(g: GlassSettings) {
    val glass = rememberGlassState()
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(180.dp).clip(ZairenkaiShapes.hero)) {
        Canvas(Modifier.fillMaxSize().glassSource(glass)) {
            val cell = 28.dp.toPx()
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = 0f
                var col = 0
                while (x < size.width) {
                    drawRect(if ((row + col) % 2 == 0) cs.primary else cs.tertiaryContainer, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Size(cell, cell))
                    x += cell
                    col++
                }
                y += cell
                row++
            }
        }
        Row(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = ZairenkaiSpacing.xl)
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .glassBar(glass, g.copy(enabled = true), RoundedCornerShape(36.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(ZairenkaiGlyphs.home, ZairenkaiGlyphs.gauge, ZairenkaiGlyphs.thermometer, ZairenkaiGlyphs.spark).forEach {
                Icon(it, contentDescription = null, tint = cs.onSurface, modifier = Modifier.size(26.dp))
            }
        }
    }
}
