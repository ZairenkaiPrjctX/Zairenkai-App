// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.afid.AfidCatalog
import com.zairenkaiprjctx.integration.ecosystem.OrganizationInventory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class DataTest {
    @Test
    fun afidLookupAndSearch() {
        val c = AfidCatalog.parse(fixture("afid-two-devices.json"))
        assertEquals("unverified", c.verification)
        assertEquals("Pixel 8a", c.lookup("AKITA ").single().productNames.single())
        assertTrue(c.lookup("unknown").isEmpty())
        assertTrue(c.lookup("").isEmpty())
        assertEquals("barbet", c.search("765g").single().codename)
        assertTrue(c.search("a").isEmpty()) // too short to be meaningful
    }

    @Test
    fun afidAssetIsValidated() {
        val text = fixture("afid-two-devices.json")
        for (broken in listOf(text.replace("\"count\": 2", "\"count\": 3"), text.replace("CC-BY-SA-3.0", "MIT"))) {
            assertTrue(runCatching { AfidCatalog.parse(broken) }.isFailure)
        }
    }

    /** The generated asset (integration/out, present after build-components.sh). */
    @Test
    fun generatedAfidAssetParses() {
        val f = File("../integration/out/assets/afid/device-candidates.json")
        assumeTrue("integration/out not built; run integration/scripts/build-components.sh", f.exists())
        val c = AfidCatalog.parse(f.readText())
        assertTrue(c.count > 0)
        assertTrue(c.devices.all { it.sourceUrl.startsWith("https://github.com/LineageOS/lineage_wiki/") })
    }

    @Test
    fun inventoryCoversTheOrganizationAndMatchesTheLock() {
        val inv = OrganizationInventory.parse(repoFile("integration/organization-repositories.json"))
        assertEquals("ZairenkaiPrjctX", inv.organization)
        assertEquals(17, inv.repositories.size) // 16 Zairenkai repositories and the excluded anime-extensions (2026-10-22)
        assertTrue(inv.repositories.all { it.disposition.isNotBlank() && it.evidence.isNotBlank() })
        val lock = Json.parseToJsonElement(repoFile("integration/repository-lock.json")).jsonObject
        for (c in lock["components"]!!.jsonArray) {
            val o = c.jsonObject
            val repo = o["repository"]!!.jsonPrimitive.content.substringAfter('/')
            val commit = o["commit"]!!.jsonPrimitive.content
            assertEquals("lock and inventory disagree for $repo", inv.byName(repo)!!.auditedCommit, commit)
        }
    }
}
