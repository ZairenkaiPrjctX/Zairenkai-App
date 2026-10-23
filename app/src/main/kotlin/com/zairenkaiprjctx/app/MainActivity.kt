// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.LaunchedEffect
import com.zairenkaiprjctx.app.update.UpdateNotifier
import com.zairenkaiprjctx.app.ui.settings.AboutScreen
import com.zairenkaiprjctx.app.ui.settings.UpdatesScreen
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.zairenkaiprjctx.app.design.AppBackdrop
import com.zairenkaiprjctx.app.design.glassBar
import com.zairenkaiprjctx.app.design.glassSource
import com.zairenkaiprjctx.app.design.hasBackdrop
import com.zairenkaiprjctx.app.design.rememberGlassState
import com.zairenkaiprjctx.app.ui.settings.AppIconScreen
import com.zairenkaiprjctx.app.ui.settings.AppearanceScreen
import com.zairenkaiprjctx.app.ui.settings.FontScreen
import com.zairenkaiprjctx.app.ui.settings.GlassScreen
import com.zairenkaiprjctx.app.ui.settings.ThemeEditorScreen
import com.zairenkaiprjctx.app.ui.settings.WallpaperScreen
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zairenkaiprjctx.app.design.ZairenkaiIcons
import com.zairenkaiprjctx.app.design.ZairenkaiMotion
import com.zairenkaiprjctx.app.design.ZairenkaiSpacing
import com.zairenkaiprjctx.app.design.ZairenkaiTheme
import com.zairenkaiprjctx.app.design.WorkingBar
import com.zairenkaiprjctx.app.ui.ai.AiScreen
import com.zairenkaiprjctx.app.ui.ai.AiViewModel
import com.zairenkaiprjctx.app.design.isDark
import com.zairenkaiprjctx.app.ui.HomeScreen
import com.zairenkaiprjctx.app.ui.MainViewModel
import com.zairenkaiprjctx.app.ui.PerformanceScreen
import com.zairenkaiprjctx.app.ui.PerformanceViewModel
import com.zairenkaiprjctx.app.ui.RuntimeScreen
import com.zairenkaiprjctx.app.ui.RuntimeViewModel
import com.zairenkaiprjctx.app.ui.SettingsScreen
import com.zairenkaiprjctx.app.ui.SettingsViewModel
import com.zairenkaiprjctx.app.ui.SystemModuleScreen
import com.zairenkaiprjctx.app.ui.SystemModuleViewModel
import com.zairenkaiprjctx.app.ui.SystemScreen
import com.zairenkaiprjctx.app.ui.SystemViewModel
import com.zairenkaiprjctx.app.ui.ThermalScreen
import com.zairenkaiprjctx.app.ui.ThermalViewModel

/** Top-level destinations, each backed by registered subsystems (AppContainer.registry). */
enum class Tab(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
    val subsystems: List<String>,
    /** The app-bar title when the navigation label is a short form of it. */
    @StringRes val appBarTitle: Int = title,
) {
    HOME(R.string.nav_home, R.string.subtitle_home, ZairenkaiIcons.home, listOf("android", "afid")),
    PERFORMANCE(R.string.nav_performance, R.string.subtitle_performance, ZairenkaiIcons.performance, listOf("core", "fas")),
    THERMAL(R.string.nav_thermal, R.string.subtitle_thermal, ZairenkaiIcons.thermal, listOf("android", "thermaltools", "core", "fas")),
    AI(R.string.nav_ai, R.string.subtitle_ai, ZairenkaiIcons.ai, listOf("upstream"), appBarTitle = R.string.ai_title),
    SYSTEM(R.string.nav_system, R.string.subtitle_system, ZairenkaiIcons.system, listOf("core", "afid", "upstream", "art")),
}

/**
 * Destinations shown over the tabs, from the app bar or a row that opens a page. Back returns to
 * [parent] (the tabs for top-level pages).
 */
enum class Overlay(@StringRes val title: Int, @StringRes val subtitle: Int?, val parentName: String = "NONE") {
    NONE(0, null),
    SETTINGS(R.string.nav_settings, R.string.subtitle_settings),
    APPEARANCE(R.string.appearance_title, R.string.appearance_subtitle, "SETTINGS"),
    THEME_EDITOR(R.string.theme_editor_title, null, "APPEARANCE"),
    WALLPAPER(R.string.wallpaper_title, null, "APPEARANCE"),
    APP_ICON(R.string.icon_title, null, "APPEARANCE"),
    FONTS(R.string.font_title, null, "APPEARANCE"),
    GLASS(R.string.glass_title, R.string.glass_subtitle, "SETTINGS"),
    UPDATES(R.string.update_title, R.string.update_subtitle, "SETTINGS"),
    ABOUT(R.string.about_title, null, "SETTINGS"),
    SYSTEM_MODULE(R.string.module_title, null),
    RUNTIME(R.string.nav_runtime, R.string.subtitle_runtime),
    ;

    val parent: Overlay get() = valueOf(parentName)
}

class MainActivity : ComponentActivity() {
    /** English unless the user chose another language: see [AppLanguage]. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = (application as ZairenkaiApplication).container
        handle(intent)
        setContent { ZairenkaiRoot(c, this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** The update notification opens Settings › Updates. */
    private fun handle(intent: Intent?) {
        if (intent?.getStringExtra(UpdateNotifier.EXTRA_OPEN) == UpdateNotifier.OPEN_UPDATES) {
            (application as ZairenkaiApplication).container.openRequest.value = Overlay.UPDATES.name
        }
    }
}

@Composable
fun ZairenkaiRoot(c: AppContainer, activity: ComponentActivity?) {
    val settings by c.theme.settings.collectAsStateWithLifecycle()
    // UI scale: every dp and sp of the app grows or shrinks together (Settings › Appearance).
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density * settings.uiScale, density.fontScale)) {
    ZairenkaiTheme(settings) {
        val dark = isDark(settings)
        if (activity != null) {
            // Keep status- and navigation-bar icons legible when the app theme differs from the system.
            DisposableEffect(dark) {
                activity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
        }
        App(c)
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun App(c: AppContainer) {
    val main: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(c) } })
    val perf: PerformanceViewModel = viewModel(factory = viewModelFactory { initializer { PerformanceViewModel(c) } })
    val thermal: ThermalViewModel = viewModel(factory = viewModelFactory { initializer { ThermalViewModel(c) } })
    val runtime: RuntimeViewModel = viewModel(factory = viewModelFactory { initializer { RuntimeViewModel(c) } })
    val system: SystemViewModel = viewModel(factory = viewModelFactory { initializer { SystemViewModel(c) } })
    val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(c) } })
    val modulesVm: SystemModuleViewModel = viewModel(factory = viewModelFactory { initializer { SystemModuleViewModel(c) } })
    val ai: AiViewModel = viewModel(factory = viewModelFactory { initializer { AiViewModel(host = c.localAi) } })
    val snapshot by main.registry.collectAsStateWithLifecycle()
    val look by c.theme.settings.collectAsStateWithLifecycle()
    val photo by c.wallpaper.photo.collectAsStateWithLifecycle()
    val photoImage = remember(photo) { photo?.asImageBitmap() }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val showOverlay = overlay != Overlay.NONE
    val request by c.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        request?.let { name -> runCatching { Overlay.valueOf(name) }.getOrNull()?.let { overlay = it } }
        c.openRequest.value = null
    }
    BackHandler(enabled = showOverlay) { overlay = overlay.parent }
    val glass = rememberGlassState()
    val backdrop = look.hasBackdrop || look.glass.enabled

    // Compatibility checks run only when the user opens the System Module screen: granting root
    // alone neither checks nor installs anything.
    val openModule = { overlay = Overlay.SYSTEM_MODULE }
    val openRuntime = { overlay = Overlay.RUNTIME }

    val registered = c.registry.subsystems.map { it.id }.toSet()
    val tabs = Tab.entries.filter { t -> t.subsystems.any { it in registered } }
    val select: (Tab) -> Unit = {
        tab = it
        overlay = Overlay.NONE
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val title = stringResource(if (showOverlay) overlay.title else tab.appBarTitle)
    val subtitle = if (showOverlay) overlay.subtitle?.let { stringResource(it) } else stringResource(tab.subtitle)

    BoxWithConstraints(Modifier.semantics { testTagsAsResourceId = true }) {
        if (backdrop) AppBackdrop(look, photoImage, Modifier.glassSource(glass))
        val wide = maxWidth >= 600.dp
        Row {
            if (wide) {
                WideNavigationRail {
                    tabs.forEach { t ->
                        WideNavigationRailItem(
                            modifier = Modifier.testTag("nav-${t.name.lowercase()}"),
                            selected = !showOverlay && tab == t,
                            onClick = { select(t) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { NavLabel(stringResource(t.title)) },
                            railExpanded = false,
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
                containerColor = if (backdrop) Color.Transparent else MaterialTheme.colorScheme.background,
                topBar = {
                    Column {
                        MediumFlexibleTopAppBar(
                            title = { BarTitle(title) },
                            subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                            navigationIcon = {
                                if (showOverlay) {
                                    IconButton(onClick = { overlay = overlay.parent }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                    }
                                }
                            },
                            actions = {
                                if (!showOverlay) {
                                    IconButton(onClick = main::refresh, enabled = !snapshot.refreshing) {
                                        Icon(ZairenkaiIcons.refresh, contentDescription = stringResource(R.string.action_refresh))
                                    }
                                    IconButton(onClick = { overlay = Overlay.SETTINGS }) {
                                        Icon(ZairenkaiIcons.settings, contentDescription = stringResource(R.string.action_open_settings))
                                    }
                                }
                            },
                            scrollBehavior = scroll,
                            colors = if (backdrop) {
                                TopAppBarDefaults.topAppBarColors(
                                    containerColor = Color.Transparent,
                                    scrolledContainerColor = if (look.glass.enabled && look.glass.topBar) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                                )
                            } else {
                                TopAppBarDefaults.topAppBarColors()
                            },
                            modifier = if (look.glass.topBar) Modifier.glassBar(glass, look.glass) else Modifier,
                        )
                        // Shown only while the registry is really probing the components.
                        if (snapshot.refreshing) WorkingBar(Modifier.padding(horizontal = ZairenkaiSpacing.gutter))
                    }
                },
                bottomBar = {
                    if (!wide) {
                        ShortNavigationBar(
                            containerColor = if (look.glass.enabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.glassBar(glass, look.glass),
                        ) {
                            tabs.forEach { t ->
                                ShortNavigationBarItem(
                                    modifier = Modifier.testTag("nav-${t.name.lowercase()}"),
                                    selected = !showOverlay && tab == t,
                                    onClick = { select(t) },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { NavLabel(stringResource(t.title)) },
                                )
                            }
                        }
                    }
                },
            ) { inner ->
                val dir = LocalLayoutDirection.current
                val padding = PaddingValues(
                    start = inner.calculateStartPadding(dir) + ZairenkaiSpacing.gutter,
                    end = inner.calculateEndPadding(dir) + ZairenkaiSpacing.gutter,
                    top = inner.calculateTopPadding() + ZairenkaiSpacing.s,
                    bottom = inner.calculateBottomPadding() + ZairenkaiSpacing.xl,
                )
                val goToRoot = { select(Tab.SYSTEM) }
                val spatial = ZairenkaiMotion.spatial<IntOffset>()
                val effects = ZairenkaiMotion.effects<Float>()
                val openEditor = { id: String? -> editing = id; overlay = Overlay.THEME_EDITOR }
                AnimatedContent(
                    modifier = Modifier.glassSource(glass),
                    targetState = if (showOverlay) overlay else tab,
                    transitionSpec = { (fadeIn(effects) + slideInVertically(spatial) { it / 16 }) togetherWith fadeOut(effects) },
                    label = "destination",
                ) { destination ->
                    when (destination) {
                        Overlay.SETTINGS -> SettingsScreen(settingsVm, modulesVm, padding, openModule) { overlay = it }
                        Overlay.APPEARANCE -> AppearanceScreen(
                            settingsVm, padding, openEditor,
                            openWallpaper = { overlay = Overlay.WALLPAPER },
                            openIcon = { overlay = Overlay.APP_ICON },
                            openFonts = { overlay = Overlay.FONTS },
                        )
                        Overlay.THEME_EDITOR -> ThemeEditorScreen(settingsVm, editing, padding) { overlay = Overlay.APPEARANCE }
                        Overlay.WALLPAPER -> WallpaperScreen(settingsVm, padding)
                        Overlay.APP_ICON -> AppIconScreen(settingsVm, padding)
                        Overlay.FONTS -> FontScreen(settingsVm, padding)
                        Overlay.GLASS -> GlassScreen(settingsVm, padding)
                        Overlay.UPDATES -> UpdatesScreen(c.updates, padding)
                        Overlay.ABOUT -> AboutScreen(c, padding) { overlay = Overlay.UPDATES }
                        Overlay.SYSTEM_MODULE -> SystemModuleScreen(modulesVm, main, snapshot, padding)
                        Overlay.RUNTIME -> RuntimeScreen(runtime, snapshot, padding, goToRoot)
                        Tab.HOME -> HomeScreen(c, main, modulesVm, padding, openModule, select)
                        Tab.PERFORMANCE -> PerformanceScreen(perf, snapshot, padding, goToRoot)
                        Tab.THERMAL -> ThermalScreen(thermal, perf, snapshot, padding)
                        Tab.AI -> AiScreen(ai, snapshot, padding, select)
                        Tab.SYSTEM -> SystemScreen(system, main, snapshot, padding, openModule, openRuntime) { overlay = Overlay.SETTINGS }
                        else -> Unit
                    }
                }
            }
        }
    }
}

/** The app-bar title on one line, shrinking toward 18 sp before a long name ends in an ellipsis. */
@Composable
private fun BarTitle(text: String) {
    val style = LocalTextStyle.current
    Text(
        text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = style.fontSize, stepSize = 1.sp),
    )
}

/**
 * One line, shrinking toward 9 sp before it would wrap: five destinations share the bar, and longer
 * words ("Performance", larger system font) otherwise wrapped or overlapped the icon.
 */
@Composable
private fun NavLabel(text: String) {
    val style = LocalTextStyle.current
    Text(
        text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}
