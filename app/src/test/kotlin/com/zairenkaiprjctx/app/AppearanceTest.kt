// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextInput
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.zairenkaiprjctx.app.appearance.LauncherIcon
import com.zairenkaiprjctx.app.appearance.LauncherIconSwitcher
import com.zairenkaiprjctx.app.design.BackgroundAnimation
import com.zairenkaiprjctx.app.design.CustomTheme
import com.zairenkaiprjctx.app.design.FontSettings
import com.zairenkaiprjctx.app.design.FontSource
import com.zairenkaiprjctx.app.design.GlassSettings
import com.zairenkaiprjctx.app.design.Palette
import com.zairenkaiprjctx.app.design.ThemeMode
import com.zairenkaiprjctx.app.design.ThemeSettings
import com.zairenkaiprjctx.app.design.WallpaperSettings
import com.zairenkaiprjctx.app.design.WallpaperSource
import com.zairenkaiprjctx.app.design.parseHexColor
import com.zairenkaiprjctx.app.design.schemeFromSeed
import com.zairenkaiprjctx.app.design.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Appearance: generated palettes, stored choices, the launcher icon and search. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ZairenkaiApplication::class)
class AppearanceTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<ZairenkaiApplication>()

    private fun contrast(a: Color, b: Color) = ColorUtils.calculateContrast(a.toArgb(), b.toArgb())

    @Test
    fun everyGeneratedSchemeKeepsTextReadable() {
        val seeds = Palette.entries.map { Color(it.seed) } +
            listOf(Color(0xFFFFEB3B), Color(0xFF00E5FF), Color.White, Color.Black, Color(0xFF808080), Color(0xFFFF00FF))
        for (seed in seeds) for (dark in listOf(false, true)) for (amoled in listOf(false, true)) {
            val s = schemeFromSeed(seed, dark, amoled)
            val name = "${seed.toHex()} dark=$dark amoled=$amoled"
            assertTrue("$name onPrimary", contrast(s.onPrimary, s.primary) >= 4.5)
            assertTrue("$name onPrimaryContainer", contrast(s.onPrimaryContainer, s.primaryContainer) >= 4.5)
            assertTrue("$name onSecondaryContainer", contrast(s.onSecondaryContainer, s.secondaryContainer) >= 4.5)
            assertTrue("$name onTertiaryContainer", contrast(s.onTertiaryContainer, s.tertiaryContainer) >= 4.5)
            assertTrue("$name onSurface", contrast(s.onSurface, s.surface) >= 7.0)
            assertTrue("$name onSurface on containers", contrast(s.onSurface, s.surfaceContainerHighest) >= 4.5)
            assertTrue("$name onSurfaceVariant", contrast(s.onSurfaceVariant, s.surfaceContainer) >= 4.5)
            if (dark && amoled) assertEquals(Color.Black, s.background)
        }
    }

    @Test
    fun hexValuesAreParsedStrictly() {
        assertEquals(Color(0xFF6750A4), parseHexColor("#6750a4"))
        assertEquals(Color(0xFF6750A4), parseHexColor("6750A4"))
        assertNull(parseHexColor("#6750A"))
        assertNull(parseHexColor("#GG50A4"))
        assertEquals("#6750A4", Color(0xFF6750A4).toHex())
    }

    @Test
    fun appearanceChoicesSurviveARestart() {
        val prefs = app.getSharedPreferences("appearance-test", 0)
        val look = ThemeSettings(
            mode = ThemeMode.DARK, dynamicColor = false, palette = Palette.FOREST,
            customThemes = listOf(CustomTheme("a", "Mine \"quoted\"", 0xFF123456.toInt())), customId = "a", amoled = true,
            animation = BackgroundAnimation.WAVES, wallpaper = WallpaperSettings(WallpaperSource.NEBULA, fill = false, dim = 0.7f),
            font = FontSettings(FontSource.GOOGLE, "Inter"), uiScale = 1.1f,
            glass = GlassSettings(enabled = true, topBar = true, frost = 0.3f, tint = 0.2f, highlight = 0.9f, noise = 0.05f),
        )
        ThemePreferences(prefs).update(look)
        assertEquals(look, ThemePreferences(prefs).settings.value)
        // A broken stored list is dropped, never half-read.
        prefs.edit().putString("custom_themes", "[{\"id\":1}]").commit()
        assertEquals(emptyList<CustomTheme>(), ThemePreferences(prefs).settings.value.customThemes)
    }

    @Test
    fun theLauncherIconSwitchesAliasesAndKeepsOneEnabled() {
        val switcher = LauncherIconSwitcher(app)
        assertEquals(LauncherIcon.MIDNIGHT, switcher.current())
        switcher.apply(LauncherIcon.CRIMSON)
        assertEquals(LauncherIcon.CRIMSON, switcher.current())
        val pm = app.packageManager
        val enabled = LauncherIcon.entries.filter {
            pm.getComponentEnabledSetting(ComponentName(app.packageName, app.packageName + it.alias)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        assertEquals(listOf(LauncherIcon.CRIMSON), enabled)
        assertEquals(LauncherIcon.CRIMSON, LauncherIcon.closestTo(Color(0xFFC0281E)))
        assertEquals(LauncherIcon.PAPER, LauncherIcon.closestTo(Color(0xFFF5F5F5)))
    }

    @Test
    fun settingsSearchOpensTheMatchingPage() {
        val c = app.container
        compose.setContent { ZairenkaiRoot(c, activity = null) }
        compose.waitUntil(10_000) { c.registry.snapshot.value.records.isNotEmpty() }
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.onNodeWithText("Search settings…").performTextInput("wallpaper")
        compose.waitForIdle()
        compose.onNodeWithText("Wallpaper").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        // The button is further down the wallpaper page: scroll to it, which also proves the page is open.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Choose a photo"))
        compose.onNodeWithText("Choose a photo").assertExists()
    }
}
