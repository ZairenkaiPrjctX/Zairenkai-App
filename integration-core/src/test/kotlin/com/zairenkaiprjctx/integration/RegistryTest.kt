// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.afid.AfidCatalog
import com.zairenkaiprjctx.integration.core.CoreClient
import com.zairenkaiprjctx.integration.ecosystem.OrganizationInventory
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.fas.FasClient
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.model.Provider
import com.zairenkaiprjctx.integration.registry.ArtSubsystem
import com.zairenkaiprjctx.integration.registry.CapabilityRecord
import com.zairenkaiprjctx.integration.registry.CoreSubsystem
import com.zairenkaiprjctx.integration.registry.DeviceDbSubsystem
import com.zairenkaiprjctx.integration.registry.FasSubsystem
import com.zairenkaiprjctx.integration.registry.IntegrationRegistry
import com.zairenkaiprjctx.integration.registry.PlatformInfo
import com.zairenkaiprjctx.integration.registry.PlatformSubsystem
import com.zairenkaiprjctx.integration.registry.ThermalToolsSubsystem
import com.zairenkaiprjctx.integration.registry.UpstreamGapSubsystem
import com.zairenkaiprjctx.integration.registry.ZairenkaiSubsystem
import com.zairenkaiprjctx.integration.thermaltools.ThermalToolsClient
import com.zairenkaiprjctx.integration.zart.ZartClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryTest {
    private val platform = object : PlatformInfo {
        override val sdkInt = 29
        override val manufacturer = "Google"
        override val brand = "google"
        override val model = "Pixel 8a"
        override val deviceCodename = "akita"
        override val socManufacturer: String? = null
        override val socModel: String? = null
        override val supportedAbis = listOf("arm64-v8a")
        override fun thermalStatus() = 0
        override fun thermalHeadroom(forecastSeconds: Int): Float? = null
        override fun powerSaveMode() = false
        override fun artApexVersion(): Long? = null
    }

    private fun registry(runner: FakeRunner, root: RootShell, exe: String?, zthermal: String? = null): IntegrationRegistry {
        val inv = OrganizationInventory.parse(repoFile("integration/organization-repositories.json"))
        return IntegrationRegistry(
            listOf(
                PlatformSubsystem(platform),
                ArtSubsystem(ZartClient({ exe }, runner, root), root, "5810ff36aa67"),
                ThermalToolsSubsystem(ThermalToolsClient({ zthermal }, runner, root), root, "9d4b310"),
                DeviceDbSubsystem({ runCatching { AfidCatalog.parse(fixture("afid-two-devices.json")) } }, platform),
                CoreSubsystem(CoreClient(root), root, "8bc7dac"),
                FasSubsystem(FasClient(root), root, "9d43894"),
                UpstreamGapSubsystem { inv },
            ),
            Dispatchers.Unconfined,
        )
    }

    @Test
    fun withoutRootPrivilegedCapabilitiesSayWhy() = runTest {
        val runner = FakeRunner()
        val root = RootShell(runner, MemoryConsent(), suPresent = { true })
        val s = registry(runner, root, exe = null).refresh()
        assertEquals(AvailabilityState.ROOT_REQUIRED, s.availability("core.health").state)
        assertEquals(AvailabilityState.ROOT_REQUIRED, s.availability("fas.status").state)
        assertEquals(AvailabilityState.ROOT_REQUIRED, s.availability("art.dexopt_status").state)
        assertEquals(AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE, s.availability("art.inspect").state)
        assertEquals(AvailabilityState.AVAILABLE, s.availability("afid.lookup").state)
        assertTrue(s.availability("afid.lookup").reason.contains("1 match"))
        assertEquals(AvailabilityState.UNSUPPORTED_DEVICE, s.availability("android.thermal_headroom").state)
        assertEquals(AvailabilityState.UNSUPPORTED_DEVICE, s.availability("art.patches").state)
        assertEquals(AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE, s.availability("thermaltools.snapshot").state)
        assertEquals(AvailabilityState.ROOT_REQUIRED, s.availability("thermaltools.snapshot_root").state)
        for (id in listOf("thermaltools.control", "intelligence.analysis", "license.entitlement", "sdk.api", "graphics.control")) {
            assertEquals(id, AvailabilityState.NO_UPSTREAM_IMPLEMENTATION, s.availability(id).state)
        }
        // Zairenkai-Modules is implemented (the zairenkai module); it is no longer a planned gap.
        assertNull(s.byId("modules.packages"))
        assertTrue("no su call without consent", runner.calls.isEmpty())
    }

    @Test
    fun withRootTheRealDaemonsAnswer() = runTest {
        val runner = FakeRunner()
        val root = grantedRoot(runner)
        val core = "'/data/adb/modules/zairenkai/bin/zcored' 'ctl' '--dir' '/data/adb/zairenkai/core'"
        val fas = "'/data/adb/modules/zairenkai/bin/zfasd' 'ctl' '--dir' '/data/adb/zairenkai/fas'"
        runner.on(su("$core 'health'"), 0, contract("core/health.json"))
        runner.on(su("$fas 'status'"), 127, "", "not found")
        val exe = "/data/app/x/lib/arm64/libzart.so"
        runner.on(listOf(exe, "inspect"), 0, contract("zart/inspect-fixture.json"))
        val s = registry(runner, root, exe).refresh()
        assertEquals(AvailabilityState.AVAILABLE, s.availability("core.health").state)
        assertTrue(s.availability("core.health").reason.contains("degraded"))
        assertEquals(AvailabilityState.COMPONENT_NOT_INSTALLED, s.availability("fas.status").state)
        assertEquals(AvailabilityState.AVAILABLE, s.availability("art.inspect").state)
        assertEquals(AvailabilityState.NOT_VERIFIED, s.availability("art.dexopt_status").state)
    }

    @Test
    fun thermalSnapshotStatesFollowWhatZthermalRead() = runTest {
        val exe = "/data/app/x/lib/arm64/libzthermal.so"
        fun probe(fixture: String, rooted: Boolean): Map<String, AvailabilityState> {
            val runner = FakeRunner().on(listOf(exe, "snapshot"), 0, contract("zthermal/$fixture"))
            val root = if (rooted) grantedRoot(runner) else RootShell(runner, MemoryConsent(), suPresent = { true })
            val sub = ThermalToolsSubsystem(ThermalToolsClient({ exe }, runner, root), root, "9d4b310")
            val records = sub.probe().associate { it.id to it.availability.state }
            // The probe runs `snapshot` only: never apply, rollback or backup-config, never through su.
            assertEquals(listOf(listOf(exe, "snapshot")), runner.calls.filterNot { it == su("id") })
            return records
        }
        val qcom = probe("snapshot-synthetic-qcom.json", rooted = true)
        assertEquals(AvailabilityState.AVAILABLE, qcom["thermaltools.snapshot"])
        assertEquals(AvailabilityState.NOT_VERIFIED, qcom["thermaltools.snapshot_root"])
        assertEquals(AvailabilityState.NO_UPSTREAM_IMPLEMENTATION, qcom["thermaltools.control"])
        val host = probe("snapshot-host.json", rooted = false)
        assertEquals(AvailabilityState.UNSUPPORTED_DEVICE, host["thermaltools.snapshot"])
        assertEquals(AvailabilityState.ROOT_REQUIRED, host["thermaltools.snapshot_root"])
    }

    @Test
    fun aThrowingProbeBecomesAFailureRecord() = runTest {
        val bad = object : ZairenkaiSubsystem {
            override val id = "bad"
            override val displayName = "Broken"
            override val provider = Provider("test", null, "test")
            override fun probe(): List<CapabilityRecord> = error("boom")
        }
        val s = IntegrationRegistry(listOf(bad, PlatformSubsystem(platform)), Dispatchers.Unconfined).refresh()
        assertEquals(AvailabilityState.INITIALIZATION_FAILED, s.availability("bad.probe").state)
        assertTrue(s.availability("bad.probe").reason.contains("boom"))
        assertEquals(AvailabilityState.AVAILABLE, s.availability("android.identity").state)
        assertEquals(AvailabilityState.INITIALIZATION_FAILED, s.availability("not.registered").state)
    }
}
