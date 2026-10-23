// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zairenkaiprjctx.app.AppContainer
import com.zairenkaiprjctx.app.NativeGraphicsTestRunner
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.app.ZairenkaiApplication
import com.zairenkaiprjctx.app.ZairenkaiRoot
import com.zairenkaiprjctx.app.design.ThemeMode
import com.zairenkaiprjctx.app.design.ThemeSettings
import com.zairenkaiprjctx.app.design.ZairenkaiTheme
import com.zairenkaiprjctx.app.ui.ai.AiComposer
import com.zairenkaiprjctx.app.ui.ai.AiTurn
import com.zairenkaiprjctx.app.ui.ai.AiTurnView
import com.zairenkaiprjctx.app.ui.ai.ApprovalState
import com.zairenkaiprjctx.app.ui.ai.ToolStatus
import com.zairenkaiprjctx.integration.exec.CommandOutcome
import com.zairenkaiprjctx.integration.exec.CommandResult
import com.zairenkaiprjctx.integration.exec.CommandRunner
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real app to PNG files for review (run with ZAIRENKAI_SCREENSHOT_DIR set; skipped
 * otherwise). Two sets:
 * - `host-*`: the real AppContainer on this JVM, so the states are what the registry probed here
 *   (no root, no packaged native components);
 * - `captured-*`: root granted, and every su command answered with output captured from the real
 *   zcored, zfasd and ztune binaries (integration-core/src/test/resources/contracts, written by
 *   integration/scripts/capture-contracts.sh). Nothing is invented: a command without a capture
 *   fails as it would on a device without the component.
 */
@RunWith(NativeGraphicsTestRunner::class)
@Config(sdk = [35], application = ZairenkaiApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotGallery {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir: String? = System.getProperty("zairenkai.screenshotDir")
    private val app get() = ApplicationProvider.getApplicationContext<ZairenkaiApplication>()

    @Before
    fun onlyWhenRequested() = assumeTrue("set ZAIRENKAI_SCREENSHOT_DIR to render screenshots", outDir != null)

    private fun shoot(name: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bmp)) }
        File(outDir!!).mkdirs()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun launch(c: AppContainer, mode: ThemeMode) {
        c.theme.update(ThemeSettings(mode, dynamicColor = false))
        compose.setContent { ZairenkaiRoot(c, activity = null) }
        try {
            // Generous: a full gallery run renders ~60 NATIVE screenshots in one JVM and slows the probes.
            compose.waitUntil(120_000) { c.registry.snapshot.value.records.isNotEmpty() && !c.registry.snapshot.value.refreshing }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            Thread.getAllStackTraces().forEach { (t, st) -> println("THREAD ${t.name}\n  " + st.take(25).joinToString("\n  ")) }
            throw e
        }
        compose.waitForIdle()
    }

    private fun tour(prefix: String) {
        shoot("$prefix-1-home")
        for ((i, tab) in listOf("performance", "thermal", "ai", "system").withIndex()) {
            compose.onNodeWithTag("nav-$tab").performClick()
            compose.waitForIdle()
            shoot("$prefix-${i + 2}-$tab")
        }
        // Pages opened from System and the app bar.
        val runtime = app.getString(R.string.nav_runtime)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(runtime))
        compose.onAllNodesWithText(runtime).onFirst().performClick()
        shoot("$prefix-6-runtime")
        back()
        compose.onNodeWithContentDescription(app.getString(R.string.action_open_settings)).performClick()
        shoot("$prefix-7-settings")
        val openModule = app.getString(R.string.module_settings_open)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(openModule))
        // It can sit under the bottom bar on a short screen: invoke the button's click action.
        compose.onNodeWithText(openModule).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        shoot("$prefix-8-module")
    }

    private fun back() {
        compose.onNodeWithContentDescription(app.getString(R.string.action_back)).performClick()
        compose.waitForIdle()
    }

    /**
     * The AI conversation components with SAMPLE turns, labelled `preview-*`: no on-device model
     * exists yet, so the app itself never shows a transcript. The tool names are the ones in
     * Zairenkai-Intelligence-Engine's closed registry: a read-only tool shows as a tool card, a
     * device change (`fas.set_profile`) only as an approval card. The Core verdict is illustrative.
     */
    private fun aiPreview(mode: ThemeMode, name: String) {
        val turns = listOf(
            AiTurn.User("u1", app.getString(R.string.ai_prompt_hot)),
            AiTurn.Tool("t1", "thermal.snapshot", emptyMap(), ToolStatus.COMPLETED),
            AiTurn.Assistant("a1", "Sample answer: the battery is at 41 °C and the thermal status is Light. Frame pacing is on Balanced."),
            AiTurn.User("u2", "Switch to power saver"),
            AiTurn.Approval("p1", "Set FAS profile to Power saver", "Sample preview: Core checked the change in a dry run.", "allow", listOf("user_choice_wins", "thermal_guard"), ApprovalState.PENDING),
        )
        compose.setContent {
            ZairenkaiTheme(ThemeSettings(mode, dynamicColor = false)) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp).padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        turns.forEach { AiTurnView(it) { _, _ -> } }
                        AiComposer(enabled = true)
                    }
                }
            }
        }
        shoot(name)
    }

    /** The page's vertical list (the palette row also scrolls, sideways). */
    private val page = hasScrollAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)

    private fun open(text: String) {
        compose.onNode(page).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Settings › Appearance and its pages. */
    @Test
    fun appearancePages() {
        launch(app.container, ThemeMode.DARK)
        compose.onNodeWithContentDescription(app.getString(R.string.action_open_settings)).performClick()
        shoot("settings-1-hub")
        open(app.getString(R.string.appearance_row))
        shoot("settings-2-appearance")
        compose.onNode(page).performScrollToNode(hasText(app.getString(R.string.ui_scale_title)))
        shoot("settings-3-appearance-lower")
        open(app.getString(R.string.wallpaper_title))
        shoot("settings-4-wallpaper")
        back()
        open(app.getString(R.string.icon_title))
        shoot("settings-5-app-icon")
        back()
        open(app.getString(R.string.font_title))
        shoot("settings-6-font")
        back()
        compose.onNode(page).performScrollToNode(hasText(app.getString(R.string.app_theme)))
        compose.onNode(hasScrollAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange))
            .performScrollToNode(hasText(app.getString(R.string.theme_new)))
        compose.onAllNodesWithText(app.getString(R.string.theme_new)).onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        shoot("settings-7-theme-editor")
        back()
        back()
        open(app.getString(R.string.glass_row_off))
        shoot("settings-8-glass")
    }

    /** Settings › Updates and Settings › About on a tall screen, so each page shows whole. */
    @Test
    @Config(qualifiers = "w411dp-h2500dp-xxhdpi")
    fun appPagesTall() {
        launch(app.container, ThemeMode.LIGHT)
        compose.onNodeWithContentDescription(app.getString(R.string.action_open_settings)).performClick()
        open(app.getString(R.string.settings_about_row))
        shoot("app-1-about")
        back()
        open(app.getString(R.string.update_title))
        shoot("app-2-updates")
    }

    /** The System Module screen whole, with the captured root manager answers. */
    @Test
    @Config(qualifiers = "w411dp-h2900dp-xxhdpi")
    fun moduleTall() {
        app.getSharedPreferences("zairenkai", 0).edit().putBoolean("root_granted", true).commit()
        launch(AppContainer(app, CapturedOutputRunner()), ThemeMode.DARK)
        compose.onNodeWithContentDescription(app.getString(R.string.action_open_settings)).performClick()
        open(app.getString(R.string.module_settings_open))
        shoot("app-3-module-dark")
    }

    /**
     * An update offer with a SAMPLE release, labelled `preview-*`: no release is published yet,
     * so the app itself has never shown one. The notes are the app's own changelog entry.
     */
    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    fun updateOfferPreview() {
        val v = com.zairenkaiprjctx.integration.update.AppVersion.parse("0.2.0-beta.2")!!
        val notes = java.io.File("../CHANGELOG.md").readText().substringAfter("## [0.2.0-beta.1] - unreleased").substringBefore("## [0.1.0]")
        val release = com.zairenkaiprjctx.integration.update.AppRelease(
            v, "v$v", "Zairenkai $v", notes, "2026-10-30T09:00:00Z", "https://github.com/ZairenkaiPrjctX/Zairenkai-App/releases/tag/v$v", true,
            com.zairenkaiprjctx.integration.update.ReleaseAsset("zairenkai-$v.apk", "https://github.com/x", 31_457_280),
            com.zairenkaiprjctx.integration.update.ReleaseAsset("zairenkai-$v.apk.sha256", "https://github.com/x", 90),
        )
        val state = com.zairenkaiprjctx.app.update.UpdateState.Available(release)
        compose.setContent {
            ZairenkaiTheme(ThemeSettings(ThemeMode.DARK, dynamicColor = false)) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp).padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        com.zairenkaiprjctx.app.ui.settings.UpdateHero(state, System.currentTimeMillis() - 3_600_000, Modifier)
                        com.zairenkaiprjctx.app.ui.settings.UpdateActions(app.container.updates, state, Modifier)
                        com.zairenkaiprjctx.app.ui.settings.ReleaseCard(release, Modifier)
                    }
                }
            }
        }
        shoot("preview-update-offer")
    }

    /** The same app dressed up: a palette, a wallpaper, the glass bar and a background animation. */
    private fun showcase(name: String, look: ThemeSettings) {
        app.getSharedPreferences("zairenkai", 0).edit().putBoolean("root_granted", true).commit()
        val c = AppContainer(app, CapturedOutputRunner())
        c.theme.update(look)
        compose.setContent { ZairenkaiRoot(c, activity = null) }
        compose.waitUntil(60_000) { c.registry.snapshot.value.records.isNotEmpty() && !c.registry.snapshot.value.refreshing }
        shoot("$name-1-home")
        compose.onNodeWithTag("nav-performance").performClick()
        shoot("$name-2-performance")
        compose.onNodeWithTag("nav-ai").performClick()
        shoot("$name-3-ai")
    }

    @Test
    fun showcaseLavenderAuroraGlass() = showcase(
        "showcase-dark",
        ThemeSettings(
            ThemeMode.DARK, dynamicColor = false, palette = com.zairenkaiprjctx.app.design.Palette.LAVENDER, amoled = true,
            wallpaper = com.zairenkaiprjctx.app.design.WallpaperSettings(com.zairenkaiprjctx.app.design.WallpaperSource.AURORA, dim = 0.62f),
            glass = com.zairenkaiprjctx.app.design.GlassSettings(enabled = true),
        ),
    )

    @Test
    fun showcaseSunsetOrbs() = showcase(
        "showcase-light",
        ThemeSettings(
            ThemeMode.LIGHT, dynamicColor = false, palette = com.zairenkaiprjctx.app.design.Palette.SUNSET,
            animation = com.zairenkaiprjctx.app.design.BackgroundAnimation.ORBS,
            wallpaper = com.zairenkaiprjctx.app.design.WallpaperSettings(com.zairenkaiprjctx.app.design.WallpaperSource.SUNSET, dim = 0.78f),
            glass = com.zairenkaiprjctx.app.design.GlassSettings(enabled = true),
        ),
    )

    /** Stress: Indonesian strings (often longer), a 360 dp screen and a 1.3× font. */
    @Test
    @Config(qualifiers = "in-w360dp-h780dp-xxhdpi")
    fun capturedIndonesianLargeFont() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        app.getSharedPreferences("zairenkai", 0).edit().putBoolean("root_granted", true).commit()
        launch(AppContainer(app, CapturedOutputRunner()), ThemeMode.LIGHT)
        tour("stress-id")
    }

    /** The logo, large, and every launcher variant, on light and dark. */
    @Test
    fun brandLogo() {
        compose.setContent {
            ZairenkaiTheme(ThemeSettings(ThemeMode.DARK, dynamicColor = false)) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp).padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        com.zairenkaiprjctx.app.design.ZairenkaiLogo(220.dp)
                        androidx.compose.material3.Text("Zairenkai", style = MaterialTheme.typography.displaySmall)
                        com.zairenkaiprjctx.app.appearance.LauncherIcon.entries.chunked(5).forEach { row ->
                            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { com.zairenkaiprjctx.app.ui.settings.IconPreview(it.background, it.foreground, 60.dp) }
                            }
                        }
                    }
                }
            }
        }
        shoot("brand-logo")
    }

    @Test
    fun aiPreviewLight() = aiPreview(ThemeMode.LIGHT, "preview-ai-chat-light")

    @Test
    fun aiPreviewDark() = aiPreview(ThemeMode.DARK, "preview-ai-chat-dark")

    @Test
    fun hostLight() {
        launch(app.container, ThemeMode.LIGHT)
        tour("host-light")
    }

    @Test
    fun hostDark() {
        launch(app.container, ThemeMode.DARK)
        tour("host-dark")
    }

    @Test
    fun capturedLight() {
        app.getSharedPreferences("zairenkai", 0).edit().putBoolean("root_granted", true).commit()
        launch(AppContainer(app, CapturedOutputRunner()), ThemeMode.LIGHT)
        tour("captured-light")
    }

    @Test
    fun capturedDark() {
        app.getSharedPreferences("zairenkai", 0).edit().putBoolean("root_granted", true).commit()
        launch(AppContainer(app, CapturedOutputRunner()), ThemeMode.DARK)
        tour("captured-dark")
    }
}

/** Answers su with the real binaries' captured output; anything not captured fails to spawn. */
class CapturedOutputRunner : CommandRunner {
    private val dir = File("../integration-core/src/test/resources/contracts")

    private fun captured(path: String) = CommandOutcome.Finished(CommandResult(0, File(dir, path).readText(), ""))

    override fun run(argv: List<String>, timeoutMs: Long): CommandOutcome {
        val script = argv.getOrNull(2).orEmpty()
        return when {
            argv.firstOrNull() != "su" -> CommandOutcome.SpawnFailed("not captured: ${argv.firstOrNull()}")
            script == "id" -> CommandOutcome.Finished(CommandResult(0, "uid=0(root) gid=0(root) context=u:r:magisk:s0\n", ""))
            "zcored" in script && script.endsWith("'health'") -> captured("core/health.json")
            "zcored" in script && script.endsWith("'capabilities'") -> captured("core/capabilities.json")
            "zcored" in script && script.endsWith("'context'") -> captured("core/context.json")
            "zcored" in script && script.endsWith("'modules'") -> captured("core/modules-installed.json")
            "zcored" in script && script.endsWith("'tuning'") -> captured("core/tuning.json")
            "zfasd" in script && script.endsWith("'status'") -> captured("fas/status.json")
            else -> CommandOutcome.SpawnFailed("not captured: $script")
        }
    }
}
