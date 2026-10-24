// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.zairenkaiprjctx.app.design.CapabilityItem
import com.zairenkaiprjctx.app.design.ChoiceCard
import com.zairenkaiprjctx.app.design.ClampText
import com.zairenkaiprjctx.app.design.ExpandableSection
import com.zairenkaiprjctx.app.design.GroupRow
import com.zairenkaiprjctx.app.design.HeroPanel
import com.zairenkaiprjctx.app.design.InfoChip
import com.zairenkaiprjctx.app.design.InfoRow
import com.zairenkaiprjctx.app.design.MetricTile
import com.zairenkaiprjctx.app.design.RingGauge
import com.zairenkaiprjctx.app.design.SectionCard
import com.zairenkaiprjctx.app.design.SegmentedGroup
import com.zairenkaiprjctx.app.design.StatTile
import com.zairenkaiprjctx.app.design.StatusBadge
import com.zairenkaiprjctx.app.design.ThemeMode
import com.zairenkaiprjctx.app.design.ThemeSettings
import com.zairenkaiprjctx.app.design.TileGrid
import com.zairenkaiprjctx.app.design.ZairenkaiGlyphs
import com.zairenkaiprjctx.app.design.ZairenkaiTheme
import com.zairenkaiprjctx.app.design.middleEllipsis
import com.zairenkaiprjctx.integration.model.Availability
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.model.Privilege
import com.zairenkaiprjctx.integration.model.Provider
import com.zairenkaiprjctx.integration.registry.CapabilityRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_WORDS = "Unavailable: spawn failed: No such file or directory (os error 2) while reading " +
    "/sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors from the zairenkai component"
private const val NO_BREAKS = "persist.device_config.runtime_native_boot.usejitprofiles_and_a_very_long_tail"
private const val DIGEST = "f4ad8d649071c6a168e846803662c5cd7976b174ee8dccb44da765693478400d"

/**
 * Long text never breaks a layout: every component, fed text far longer than real data, on a
 * 320 dp screen with a 1.5× font, keeps all of its text inside the screen. Real text measurement
 * needs NATIVE graphics.
 */
@RunWith(NativeGraphicsTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h5000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LongTextLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun componentsKeepLongTextInsideANarrowScreenWithALargeFont() {
        RuntimeEnvironment.setFontScale(1.5f)
        val record = CapabilityRecord(
            "x", "Capability with a deliberately long name that goes on and on", "", Provider("ZairenkaiPrjctX/Zairenkai-App", null, "test"),
            Privilege.ROOT, Availability(AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE, LONG_WORDS),
        )
        compose.setContent {
            ZairenkaiTheme(ThemeSettings(ThemeMode.LIGHT, dynamicColor = false)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroPanel(
                        overline = "An overline that is much longer than any real overline would be",
                        headline = "Manufacturer With A Very Long Name Model XYZ-123456789 Ultra Pro Max Edition",
                        supporting = LONG_WORDS,
                        trailing = { RingGauge(0.5f, "12", "of 23 ready", "ring") },
                    ) { InfoChip(NO_BREAKS) }
                    TileGrid(listOf(1, 2)) { _, m -> StatTile(ZairenkaiGlyphs.gauge, "Frame pacing and something else", "Native component unavailable on this device", {}, m) }
                    TileGrid(listOf(1, 2, 3)) { i, m -> ChoiceCard(ZairenkaiGlyphs.bolt, "Profile title number $i that is long", LONG_WORDS, i == 1, {}, m, tag = "Experimental") }
                    TileGrid(listOf(1, 2)) { _, m -> MetricTile("Thermal status (framework, via Core)", "Not supported on this device", LONG_WORDS, m) }
                    SectionCard("A section title that keeps going for a long while", trailing = { StatusBadge(AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE) }) {
                        InfoRow(NO_BREAKS, LONG_WORDS, code = true)
                        InfoRow("Key", NO_BREAKS, code = true)
                        InfoRow("SHA-256", DIGEST, code = true, shorten = true)
                        ExpandableSection("An expandable section with a really long title text", summary = "summary that is long too") { Text(LONG_WORDS) }
                        ClampText(LONG_WORDS + " " + LONG_WORDS)
                    }
                    SegmentedGroup(title = "A group header that is as long as a sentence can be", summary = "123 / 456 ready") {
                        row {
                            GroupRow(
                                "Row headline that would not fit on one line at all",
                                supporting = LONG_WORDS,
                                icon = ZairenkaiGlyphs.shield,
                                status = AvailabilityState.ROOT_REQUIRED,
                                action = { Button(onClick = {}) { Text("A button label that is longer than usual") } },
                                trailing = { Switch(checked = true, onCheckedChange = {}) },
                            )
                        }
                        row { CapabilityItem(record) }
                    }
                }
            }
        }
        compose.waitForIdle()
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        val density = compose.density.density
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("texts found", texts.size > 30)
        // The screen is tall enough for everything: a node with no area would be scrolled away, not checked.
        assertTrue("all on screen", texts.none { it.boundsInRoot.height == 0f })
        texts.forEach { n ->
            val b = n.boundsInRoot
            val text = n.config[SemanticsProperties.Text].joinToString()
            assertTrue("'${text.take(40)}' starts off screen (${b.left})", b.left >= -0.5f)
            assertTrue("'${text.take(40)}' ends off screen (${b.right} > $width)", b.right <= width + 0.5f)
            // Squeezed text (a few letters per line beside a wide neighbour) is the other failure.
            if (text.length > 12) assertTrue("'${text.take(40)}' squeezed to ${b.width / density} dp", b.width / density >= 64f)
        }
        // Long details are clamped behind a link instead of running on.
        assertTrue(compose.onAllNodesWithText("Show more").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun digestsAreShortenedByTheirEnds() {
        assertEquals("f4ad8d6490…693478400d", middleEllipsis(DIGEST))
        assertEquals("short", middleEllipsis("short"))
    }
}
