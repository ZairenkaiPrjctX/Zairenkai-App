// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.zairenkaiprjctx.app.design.InfoRow
import com.zairenkaiprjctx.app.design.ThemeMode
import com.zairenkaiprjctx.app.design.ThemeSettings
import com.zairenkaiprjctx.app.design.ZairenkaiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runner.notification.RunNotifier
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric runner for NATIVE-graphics tests. Robolectric 4.14 has no native runtime for Linux
 * aarch64 and loads it before any rule or test body runs, so on that host every test is reported
 * ignored instead of failing; CI (x86-64) runs them.
 */
class NativeGraphicsTestRunner(testClass: Class<*>) : RobolectricTestRunner(testClass) {
    override fun run(notifier: RunNotifier) {
        val linuxAarch64 = System.getProperty("os.name").orEmpty().startsWith("Linux") &&
            System.getProperty("os.arch") == "aarch64"
        if (linuxAarch64) description.children.forEach(notifier::fireTestIgnored) else super.run(notifier)
    }
}

/**
 * InfoRow layout with real text measurement. LEGACY graphics, Robolectric 4.14's default, fakes
 * glyph widths, so this class asks for NATIVE.
 */
@RunWith(NativeGraphicsTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InfoRowNativeLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun longPropertyNamesStackInsteadOfWrappingPerCharacter() {
        // No space or hyphen: nothing to break at, so a 42 % column would wrap it per character.
        val key = "persist.device_config.runtime_native_boot.usejitprofiles"
        compose.setContent {
            ZairenkaiTheme(ThemeSettings(ThemeMode.LIGHT, dynamicColor = false)) {
                Box(Modifier.width(280.dp)) { InfoRow(key, "true", code = true) }
            }
        }
        val k = compose.onNodeWithText(key, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val v = compose.onNodeWithText("true", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("value below the label (${v.top} >= ${k.bottom})", v.top >= k.bottom)
        // The label keeps nearly the full width, so it wraps at most once instead of per character.
        val labelHeight = (k.bottom - k.top).value
        assertTrue("label $labelHeight dp tall", labelHeight < 4 * 20f)
    }
}
