package com.zairenkaiprjctx.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zairenkaiprjctx.integration.model.AvailabilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        compose.onAllNodesWithText("Root required").onFirst().assertIsDisplayed()
    }

    @Test
    fun navigationReachesEveryDestinationAndSettings() {
        launch()
        compose.onNodeWithText("Runtime").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ART module").assertIsDisplayed()
        compose.onNodeWithText("System").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Root access").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Root access").assertIsDisplayed()
    }

    @Test
    fun themeChoiceIsAppliedAndPersisted() {
        launch()
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Dark").performClick() // a real touch, not a semantics shortcut
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
        compose.onNodeWithText("Thermal").performClick()
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

    @Test
    @Config(qualifiers = "in")
    fun indonesianNavigation() {
        launch()
        // "Beranda" is both the top-bar title and the navigation label.
        assertTrue(compose.onAllNodesWithText("Beranda").fetchSemanticsNodes().size >= 2)
        compose.onNodeWithText("Sistem").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Akses root").assertIsDisplayed()
    }
}
