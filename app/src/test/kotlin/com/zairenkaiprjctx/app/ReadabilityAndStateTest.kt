// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zairenkaiprjctx.app.design.InfoRow
import com.zairenkaiprjctx.app.design.infoRowSideBySide
import com.zairenkaiprjctx.app.design.ThemeMode
import com.zairenkaiprjctx.app.design.ThemeSettings
import com.zairenkaiprjctx.app.design.ZairenkaiTheme
import com.zairenkaiprjctx.app.ui.profileCheck
import com.zairenkaiprjctx.integration.fas.FasCall
import com.zairenkaiprjctx.integration.fas.FasStatus
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadabilityAndStateTest {
    @get:Rule
    val compose = createComposeRule()

    private fun row(width: Dp, key: String, value: String, code: Boolean = false) {
        compose.setContent {
            ZairenkaiTheme(ThemeSettings(ThemeMode.LIGHT, dynamicColor = false)) {
                Box(Modifier.width(width)) { InfoRow(key, value, code = code) }
            }
        }
    }

    private fun bounds(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun columnsAreKeptOnlyWhenTheLongestWordsFit() {
        // 360 px row, 8 px gap: key column 151 px, value column 201 px.
        assertTrue(infoRowSideBySide(keyLongestWord = 120, valueLongestWord = 60, width = 360, gap = 8))
        assertFalse(infoRowSideBySide(keyLongestWord = 300, valueLongestWord = 60, width = 360, gap = 8))
        assertFalse(infoRowSideBySide(keyLongestWord = 100, valueLongestWord = 250, width = 360, gap = 8))
        assertTrue(infoRowSideBySide(keyLongestWord = 151, valueLongestWord = 201, width = 360, gap = 8))
    }

    @Test
    fun shortRowsStaySideBySide() {
        row(360.dp, "Battery level", "80 %")
        val k = bounds("Battery level")
        val v = bounds("80 %")
        assertEquals(k.top, v.top)
        assertTrue(v.left > k.right)
    }

    @Test
    fun profileIsConfirmedOnlyByZfasdsOwnStatus() {
        fun status(profile: String?) = FasCall.Ok(FasStatus(version = 1, running = true, mode = "control", observeOnly = false, profile = profile))
        assertTrue(profileCheck("performance", status("performance"))!!.confirmed)
        val stale = profileCheck("performance", status("balanced"))!!
        assertFalse(stale.confirmed)
        assertEquals("balanced", stale.reported)
        val unreadable = profileCheck("performance", FasCall.Unreachable("socket closed"))!!
        assertFalse(unreadable.confirmed)
        assertNull(unreadable.reported)
        assertNull(profileCheck(null, status("balanced")))
    }

    @Test
    fun everyStringHasAnIndonesianTranslationWithTheSamePlaceholders() {
        fun strings(dir: String): Map<String, String> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
            val nodes = doc.getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it) }
                .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
                .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
        }
        val en = strings("values")
        val id = strings("values-in")
        assertEquals(en.keys - id.keys, emptySet<String>())
        assertEquals(id.keys - en.keys, emptySet<String>())
        val placeholder = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z%]")
        en.forEach { (name, text) ->
            val a = placeholder.findAll(text).map { it.value }.filter { it != "%%" }.sorted().toList()
            val b = placeholder.findAll(id.getValue(name)).map { it.value }.filter { it != "%%" }.sorted().toList()
            assertEquals("placeholders of $name", a, b)
        }
    }
}
