// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import android.app.LocaleManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real MainActivity on a device whose system language is Indonesian: the app must still
 * start in English, and switch only when the user picks Bahasa Indonesia.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ZairenkaiApplication::class, qualifiers = "in")
class LanguageTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitRegistry() {
        val c = (compose.activity.application as ZairenkaiApplication).container
        compose.waitUntil(10_000) { c.registry.snapshot.value.records.isNotEmpty() }
        compose.waitForIdle()
    }

    /**
     * The language choice is a connected button group further down the settings list: scroll to it,
     * then invoke its click action (LEGACY graphics cannot hit-test the segments' path outlines;
     * ConnectedChoiceTouchTest covers real touches).
     */
    private fun choose(language: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(language))
        compose.onNodeWithText(language).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun englishIsTheDefaultOnAnIndonesianSystem() {
        awaitRegistry()
        assertEquals(AppLanguage.ENGLISH, AppLanguage.current(compose.activity))
        // "Home" is both the top-bar title and the navigation label.
        assertTrue(count("Home") >= 2)
        assertEquals(0, count("Beranda"))
    }

    @Test
    fun choosingIndonesianSwitchesTheAppAndBack() {
        awaitRegistry()
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.waitForIdle()
        choose("Bahasa Indonesia")
        // API 33+: the choice is the per-app language, so the system setting shows it too.
        val locales = compose.activity.getSystemService(LocaleManager::class.java).applicationLocales
        assertEquals("id", locales[0].language)
        assertEquals(AppLanguage.INDONESIAN, AppLanguage.current(compose.activity))

        // On a device the system recreates the activity after a per-app language change.
        // Saved state brings the user back to settings, now in Indonesian.
        compose.activityRule.scenario.recreate()
        awaitRegistry()
        compose.onNodeWithText("Setelan").assertIsDisplayed()
        assertEquals(1, count("Beranda"))
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("nav-system").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Akses root").assertIsDisplayed()

        compose.onNodeWithContentDescription("Buka setelan").performClick()
        compose.waitForIdle()
        choose("English")
        compose.activityRule.scenario.recreate()
        awaitRegistry()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        assertEquals(1, count("Home"))
        assertEquals(0, count("Beranda"))
    }

    @Test
    @Config(sdk = [32])
    fun beforeAndroid13TheChoiceIsStoredAndTheActivityRecreated() {
        awaitRegistry()
        assertTrue(count("Home") >= 2)
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.waitForIdle()
        choose("Bahasa Indonesia")
        val prefs = compose.activity.getSharedPreferences("zairenkai", 0)
        assertEquals("id", prefs.getString("app_language", null))
        awaitRegistry()
        compose.onNodeWithText("Setelan").assertIsDisplayed()
        assertEquals(1, count("Beranda"))
    }
}
