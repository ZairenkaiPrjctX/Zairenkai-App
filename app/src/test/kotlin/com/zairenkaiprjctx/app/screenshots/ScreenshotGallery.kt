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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
            compose.waitUntil(60_000) { c.registry.snapshot.value.records.isNotEmpty() && !c.registry.snapshot.value.refreshing }
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
        compose.onAllNodesWithText(app.getString(R.string.nav_runtime)).onFirst().performClick()
        shoot("$prefix-6-runtime")
        back()
        compose.onNodeWithContentDescription(app.getString(R.string.action_open_settings)).performClick()
        shoot("$prefix-7-settings")
        compose.onNodeWithText(app.getString(R.string.module_settings_open)).performScrollTo().performClick()
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
