package com.zairenkaiprjctx.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
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
import com.zairenkaiprjctx.app.design.isDark
import com.zairenkaiprjctx.app.ui.HomeScreen
import com.zairenkaiprjctx.app.ui.MainViewModel
import com.zairenkaiprjctx.app.ui.PerformanceScreen
import com.zairenkaiprjctx.app.ui.PerformanceViewModel
import com.zairenkaiprjctx.app.ui.RuntimeScreen
import com.zairenkaiprjctx.app.ui.RuntimeViewModel
import com.zairenkaiprjctx.app.ui.SettingsScreen
import com.zairenkaiprjctx.app.ui.SettingsViewModel
import com.zairenkaiprjctx.app.ui.SystemScreen
import com.zairenkaiprjctx.app.ui.SystemViewModel
import com.zairenkaiprjctx.app.ui.ThermalScreen
import com.zairenkaiprjctx.app.ui.ThermalViewModel

/** Top-level destinations, each backed by registered subsystems (AppContainer.registry). */
enum class Tab(@StringRes val title: Int, val icon: ImageVector, val subsystems: List<String>) {
    HOME(R.string.nav_home, ZairenkaiIcons.home, listOf("android", "afid")),
    PERFORMANCE(R.string.nav_performance, ZairenkaiIcons.performance, listOf("core", "fas")),
    THERMAL(R.string.nav_thermal, ZairenkaiIcons.thermal, listOf("android", "thermaltools", "core", "fas")),
    RUNTIME(R.string.nav_runtime, ZairenkaiIcons.runtime, listOf("art")),
    SYSTEM(R.string.nav_system, ZairenkaiIcons.system, listOf("core", "afid", "upstream")),
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
        setContent { ZairenkaiRoot(c, this) }
    }
}

@Composable
fun ZairenkaiRoot(c: AppContainer, activity: ComponentActivity?) {
    val settings by c.theme.settings.collectAsStateWithLifecycle()
    ZairenkaiTheme(settings) {
        val dark = isDark(settings)
        if (activity != null) {
            // Keep status- and navigation-bar icons legible when the app theme differs from the system.
            DisposableEffect(dark) {
                activity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
        }
        App(c)
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
    val snapshot by main.registry.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showSettings) { showSettings = false }

    val registered = c.registry.subsystems.map { it.id }.toSet()
    val tabs = Tab.entries.filter { t -> t.subsystems.any { it in registered } }
    val select: (Tab) -> Unit = {
        tab = it
        showSettings = false
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val title = if (showSettings) stringResource(R.string.nav_settings) else stringResource(tab.title)

    BoxWithConstraints(Modifier.semantics { testTagsAsResourceId = true }) {
        val wide = maxWidth >= 600.dp
        Row {
            if (wide) {
                WideNavigationRail {
                    tabs.forEach { t ->
                        WideNavigationRailItem(
                            selected = !showSettings && tab == t,
                            onClick = { select(t) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.title)) },
                            railExpanded = false,
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
                topBar = {
                    MediumTopAppBar(
                        title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
                        navigationIcon = {
                            if (showSettings) {
                                IconButton(onClick = { showSettings = false }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                            }
                        },
                        actions = {
                            if (!showSettings) {
                                IconButton(onClick = main::refresh, enabled = !snapshot.refreshing) {
                                    Icon(ZairenkaiIcons.refresh, contentDescription = stringResource(R.string.action_refresh))
                                }
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(ZairenkaiIcons.settings, contentDescription = stringResource(R.string.action_open_settings))
                                }
                            }
                        },
                        scrollBehavior = scroll,
                    )
                },
                bottomBar = {
                    if (!wide) {
                        ShortNavigationBar {
                            tabs.forEach { t ->
                                ShortNavigationBarItem(
                                    selected = !showSettings && tab == t,
                                    onClick = { select(t) },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(stringResource(t.title)) },
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
                AnimatedContent(
                    targetState = if (showSettings) null else tab,
                    transitionSpec = { (fadeIn(effects) + slideInVertically(spatial) { it / 16 }) togetherWith fadeOut(effects) },
                    label = "destination",
                ) { destination ->
                    when (destination) {
                        null -> SettingsScreen(settingsVm, padding)
                        Tab.HOME -> HomeScreen(c, main, padding)
                        Tab.PERFORMANCE -> PerformanceScreen(perf, snapshot, padding, goToRoot)
                        Tab.THERMAL -> ThermalScreen(thermal, perf, snapshot, padding)
                        Tab.RUNTIME -> RuntimeScreen(runtime, snapshot, padding, goToRoot)
                        Tab.SYSTEM -> SystemScreen(system, main, snapshot, padding)
                    }
                }
            }
        }
    }
}
