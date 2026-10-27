// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zairenkaiprjctx.integration.model.AvailabilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.zairenkaiprjctx.integration.systemmodule.InstallPhase
import androidx.compose.ui.test.assertCountEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The real app UI with the real AppContainer (registry, Java platform adapter, packaged assets)
 * under Robolectric. Root is never requested here, so no su call happens; the zart executable
 * does not exist in Robolectric, so ART inspection must say so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ZairenkaiApplication::class)
class AppUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ZairenkaiApplication>().container

    private fun launch() {
        val c = container
        compose.setContent { ZairenkaiRoot(c, activity = null) }
        compose.waitUntil(10_000) { c.registry.snapshot.value.records.isNotEmpty() }
        compose.waitForIdle()
    }

    @Test
    fun homeShowsRealRegistryStates() {
        launch()
        val snap = container.registry.snapshot.value
        assertEquals(AvailabilityState.ROOT_REQUIRED, snap.availability("core.health").state)
        assertEquals(AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE, snap.availability("art.inspect").state)
        compose.onNodeWithText("This device").assertIsDisplayed()
        // The health list is further down the LazyColumn: scroll to it, then check what it says.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Root required"))
        compose.onAllNodesWithText("Root required")[0].assertIsDisplayed()
    }

    @Test
    fun navigationReachesEveryDestinationAndSettings() {
        launch()
        for (tab in listOf("performance", "thermal", "ai", "system", "home")) {
            compose.onNodeWithTag("nav-$tab").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("This device").assertIsDisplayed()
        // Runtime (ART) is a page of System.
        compose.onNodeWithTag("nav-system").performClick()
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Runtime"))
        // The row can sit under the bottom bar on this 470 dp screen: invoke its click action.
        compose.onNodeWithText("Runtime").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("ART module").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Root access").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Search settings…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Root access").assertIsDisplayed()
    }

    @Test
    fun thermalScreenSaysWhyZthermalCannotRunAndOffersNoControl() {
        launch()
        compose.onNodeWithTag("nav-thermal").performClick()
        compose.waitForIdle()
        val snap = container.registry.snapshot.value
        // The Robolectric APK has no packaged zthermal: the state says so instead of showing data.
        assertEquals(AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE, snap.availability("thermaltools.snapshot").state)
        assertEquals(AvailabilityState.NO_UPSTREAM_IMPLEMENTATION, snap.availability("thermaltools.control").state)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Thermal controls"))
        compose.onNodeWithText("Thermal controls").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Thermal sensors"))
        compose.onNodeWithText("Thermal sensors").assertIsDisplayed()
    }

    @Test
    fun systemModuleScreenNeedsRootAndOffersNoInstallWithoutIt() {
        launch()
        // No root granted: Home shows no install offer.
        compose.onAllNodesWithText("System integrations available").assertCountEquals(0)
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Open"))
        // The button can sit under the bottom bar on this 320 dp screen: invoke its click action.
        compose.onNodeWithText("Open").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onAllNodesWithText("Zairenkai System Module")[0].assertIsDisplayed() // the app-bar title
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Enable root integration"))
        compose.onNodeWithText("Enable root integration").assertIsDisplayed()
        compose.onAllNodesWithText("Install Zairenkai System Module").assertCountEquals(0)
        for (s in container.moduleInstallers.values) {
            val p = s.state.value.phase
            assertTrue(p.name, p == InstallPhase.ROOT_PERMISSION_REQUIRED || p == InstallPhase.MANAGER_DETECTION)
        }
    }

    @Test
    fun securityCenterFollowsSystemModuleAndDoesNotAssumeSecure() {
        launch()
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Zairenkai Security Center"))
        compose.onAllNodesWithText("Zairenkai Security Center")[1].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Vulnerability findings"))
        compose.onNodeWithText("Vulnerability findings").assertIsDisplayed()
        compose.onNodeWithText("Not scanned").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Module integrity"))
        compose.waitForIdle()
        // The catalogue section also reports Unknown when no signature verdict is available.
        compose.onAllNodesWithText("Unknown")[0].assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Packaged module catalogue signature", substring = true))
        compose.onNodeWithText("Packaged module catalogue signature", substring = true).assertExists()
    }

    @Test
    fun themeChoiceIsAppliedAndPersisted() {
        launch()
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.waitForIdle()
        // The theme choice is on Settings › Appearance.
        compose.onNodeWithText("Theme, palette, wallpaper, app icon, font and size").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        // It is a connected button group: its end segments have path outlines that LEGACY graphics
        // cannot hit-test, so this invokes the click action; ConnectedChoiceTouchTest covers real
        // touches with NATIVE graphics.
        compose.onNodeWithText("Dark").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(com.zairenkaiprjctx.app.design.ThemeMode.DARK, container.theme.settings.value.mode)
        val prefs = ApplicationProvider.getApplicationContext<ZairenkaiApplication>().getSharedPreferences("zairenkai", 0)
        assertEquals("DARK", prefs.getString("theme_mode", null))
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun wideScreensUseTheNavigationRail() {
        launch()
        // Same destinations, now in the rail instead of the bottom bar.
        compose.onNodeWithTag("nav-thermal").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Android thermal service").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun smallScreenWithLargeFontStillRenders() {
        RuntimeEnvironment.setFontScale(2f)
        launch()
        compose.onNode(hasText("This device")).assertExists()
    }
}
