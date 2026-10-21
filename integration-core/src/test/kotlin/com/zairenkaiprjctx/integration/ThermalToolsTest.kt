// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.exec.CommandOutcome
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.thermaltools.ThermalToolsClient
import com.zairenkaiprjctx.integration.thermaltools.ThermalToolsFormatException
import com.zairenkaiprjctx.integration.thermaltools.ThermalToolsParser
import com.zairenkaiprjctx.integration.thermaltools.ThermalToolsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `snapshot-synthetic-*.json` and `snapshot-empty-host.json`: the real zthermal binary reading
 * ThermalTools' own synthetic device fixtures (`--root`). `snapshot-host.json`: the same binary
 * on a Linux container with no thermal zones and no getprop.
 */
class ThermalToolsTest {
    @Test
    fun qualcommFixtureWithFramework() {
        val s = ThermalToolsParser.snapshot(contract("zthermal/snapshot-synthetic-qcom.json"))
        assertEquals(1, s.schemaVersion)
        assertEquals("0.2.0", s.toolVersion)
        assertEquals("fixture", s.root)
        assertEquals("qualcomm", s.socVendor)
        assertEquals("SM8250", s.socModel)
        assertEquals(5, s.summary.zones)
        assertEquals(4, s.summary.zonesValid)
        assertEquals(43200L, s.summary.hottest?.millicelsius)
        assertEquals("light", s.summary.frameworkStatus)
        assertEquals(30500L, s.summary.batteryMillicelsius)
        val missing = s.zones.single { it.temperature.millicelsius == null }
        assertEquals("unsupported", missing.temperature.status)
        assertTrue(missing.temperature.reason!!.contains("temp"))
        assertEquals(2, s.zones.first().tripCount)
        // zthermal 0.2.0 reports no writable control.
        assertFalse(s.capabilities.any { it.state == "AVAILABLE_WRITABLE" })
        assertEquals("UNSUPPORTED", s.capabilities.single { it.id == "control.thermal_policy" }.state)
        assertFalse(s.capabilities.any { it.hardwareTested })
    }

    @Test
    fun deniedFrameworkAndHostWithoutThermalZones() {
        val pixel = ThermalToolsParser.snapshot(contract("zthermal/snapshot-synthetic-pixel.json"))
        assertEquals("permission_denied", pixel.sources.single { it.source == "dumpsys thermalservice" }.status)
        assertNull(pixel.summary.frameworkStatus)
        assertEquals("PERMISSION_DENIED", pixel.capabilities.single { it.id == "framework.thermal_status" }.state)
        val host = ThermalToolsParser.snapshot(contract("zthermal/snapshot-host.json"))
        assertEquals("device", host.root)
        assertEquals(0, host.summary.zones)
        assertNull(host.summary.hottest)
        assertEquals("unsupported", host.sources.single { it.source == "getprop" }.status)
        for (f in listOf("snapshot-synthetic-mtk.json", "snapshot-empty-host.json")) ThermalToolsParser.snapshot(contract("zthermal/$f"))
    }

    @Test(expected = ThermalToolsFormatException::class)
    fun otherSchemaVersionIsRefused() {
        ThermalToolsParser.snapshot(contract("zthermal/snapshot-host.json").replace("\"schema_version\": 1,", "\"schema_version\": 2,"))
    }

    @Test
    fun clientFailuresAreReportedNotFaked() {
        val exe = "/data/app/x/lib/arm64/libzthermal.so"
        val noRoot = { r: FakeRunner -> RootShell(r, MemoryConsent(), suPresent = { true }) }
        val missing = ThermalToolsClient({ null }, FakeRunner(), noRoot(FakeRunner())).snapshot()
        assertTrue((missing as ThermalToolsResult.Failed).missingBinary)
        val timeout = FakeRunner().on(listOf(exe, "snapshot"), CommandOutcome.TimedOut)
        assertTrue(ThermalToolsClient({ exe }, timeout, noRoot(timeout)).snapshot() is ThermalToolsResult.Failed)
        val garbage = FakeRunner().on(listOf(exe, "snapshot"), 0, "{\"schema_version\": 1}")
        assertTrue(ThermalToolsClient({ exe }, garbage, noRoot(garbage)).snapshot() is ThermalToolsResult.Failed)
        val exit1 = FakeRunner().on(listOf(exe, "snapshot"), 1, "", "boom")
        assertTrue(ThermalToolsClient({ exe }, exit1, noRoot(exit1)).snapshot() is ThermalToolsResult.Failed)
    }

    @Test
    fun rootSnapshotNeedsConsentAndRunsOnlySnapshot() {
        val exe = "/data/app/x/lib/arm64/libzthermal.so"
        val runner = FakeRunner()
        val denied = ThermalToolsClient({ exe }, runner, RootShell(runner, MemoryConsent(), suPresent = { true })).snapshotWithRoot()
        assertTrue(denied is ThermalToolsResult.Failed)
        assertTrue("no su call without consent", runner.calls.isEmpty())

        val granted = FakeRunner()
        val root = grantedRoot(granted)
        granted.on(su("'$exe' 'snapshot' '--framework'"), 0, contract("zthermal/snapshot-synthetic-qcom.json"))
        val ok = ThermalToolsClient({ exe }, granted, root).snapshotWithRoot()
        assertEquals("light", (ok as ThermalToolsResult.Ok).value.summary.frameworkStatus)
        assertEquals(listOf(su("id"), su("'$exe' 'snapshot' '--framework'")), granted.calls)

        val bad = ThermalToolsClient({ "/data/app/x y/libzthermal.so" }, granted, root).snapshotWithRoot()
        assertTrue("a path that is not a safe shell word is refused", bad is ThermalToolsResult.Failed)
    }
}
