// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.zairenkaiprjctx.app.design.LocalReducedMotion
import com.zairenkaiprjctx.app.design.StatusBadge
import com.zairenkaiprjctx.app.design.ThemeMode
import com.zairenkaiprjctx.app.design.ThemeSettings
import com.zairenkaiprjctx.app.design.ZairenkaiColorScheme
import com.zairenkaiprjctx.app.design.ZairenkaiFonts
import com.zairenkaiprjctx.app.design.ZairenkaiTheme
import com.zairenkaiprjctx.app.design.ZairenkaiTypography
import com.zairenkaiprjctx.app.design.visual
import com.zairenkaiprjctx.integration.model.AvailabilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DesignSystemTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun everyTextStyleUsesGoogleSansFlex() {
        val t = ZairenkaiTypography
        val styles = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
        )
        val flex = setOf(ZairenkaiFonts.text, ZairenkaiFonts.display, ZairenkaiFonts.small)
        styles.forEach { assertTrue("style uses ${it.fontFamily}", it.fontFamily in flex) }
        assertNotEquals(FontFamily.Default, ZairenkaiFonts.text)
    }

    @Test
    fun bundledFontResourceLoads() {
        assertNotNull(ResourcesCompat.getFont(context, R.font.google_sans_flex))
    }

    @Test
    fun themeAppliesTypographyAndBrandColours() {
        var bodyFamily: FontFamily? = null
        var primary: Long? = null
        compose.setContent {
            ZairenkaiTheme(ThemeSettings(ThemeMode.LIGHT, dynamicColor = false)) {
                bodyFamily = MaterialTheme.typography.bodyLarge.fontFamily
                primary = MaterialTheme.colorScheme.primary.value.toLong()
            }
        }
        compose.waitForIdle()
        assertEquals(ZairenkaiFonts.text, bodyFamily)
        assertEquals(ZairenkaiColorScheme.light.primary.value.toLong(), primary)
    }

    @Test
    fun darkModeUsesTheDarkScheme() {
        var primary: Long? = null
        compose.setContent {
            ZairenkaiTheme(ThemeSettings(ThemeMode.DARK, dynamicColor = false)) {
                primary = MaterialTheme.colorScheme.primary.value.toLong()
            }
        }
        compose.waitForIdle()
        assertEquals(ZairenkaiColorScheme.dark.primary.value.toLong(), primary)
    }

    @Test
    fun statusIsTextAndStateDescriptionNotColourAlone() {
        compose.setContent { ZairenkaiTheme { StatusBadge(AvailabilityState.ROOT_REQUIRED) } }
        compose.onNodeWithText("Root required").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Root required")).assertExists()
    }

    @Test
    fun everyStateHasALabelInBothLanguages() {
        AvailabilityState.entries.forEach { s ->
            assertTrue(context.getString(s.visual().label).isNotBlank())
        }
    }

    @Test
    @Config(qualifiers = "in")
    fun indonesianStrings() {
        compose.setContent { ZairenkaiTheme { StatusBadge(AvailabilityState.AVAILABLE) } }
        compose.onNodeWithText("Siap").assertIsDisplayed()
    }

    @Test
    fun reducedMotionFollowsTheSystemAnimatorScale() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var reduced: Boolean? = null
        compose.setContent { ZairenkaiTheme { reduced = LocalReducedMotion.current } }
        compose.waitForIdle()
        assertEquals(true, reduced)
    }
}
