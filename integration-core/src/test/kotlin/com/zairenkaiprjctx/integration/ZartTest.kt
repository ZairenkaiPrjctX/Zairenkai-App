package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.exec.CommandOutcome
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.zart.Obs
import com.zairenkaiprjctx.integration.zart.PackageNames
import com.zairenkaiprjctx.integration.zart.ZartClient
import com.zairenkaiprjctx.integration.zart.ZartFormatException
import com.zairenkaiprjctx.integration.zart.ZartParser
import com.zairenkaiprjctx.integration.zart.ZartResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `*-host.json`: the real zart binary run on a Linux host (Android inputs unavailable).
 * `*-fixture.json` / `dexopt-status-denied.json`: serialised by zart's own library from the fixture
 * inputs its tests use (see integration/scripts/zart_fixture_main.rs).
 */
class ZartTest {
    @Test
    fun hostRunReportsEverythingAndroidAsUnavailable() {
        val r = ZartParser.inspect(contract("zart/inspect-host.json"))
        assertEquals(1, r.schema)
        assertEquals("x86_64", r.zartArch)
        assertTrue(r.apiLevel is Obs.Missing)
        assertTrue(r.artModule is Obs.Missing)
        assertTrue(r.isa.isEmpty())
        assertTrue(r.pageSizeBytes is Obs.Value)
    }

    @Test
    fun populatedReport() {
        val r = ZartParser.inspect(contract("zart/inspect-fixture.json"))
        assertEquals(37L, r.apiLevel.valueOrNull())
        assertEquals(16384L, r.pageSizeBytes.valueOrNull())
        val active = r.artModule.valueOrNull()!!.single { it.isActive == true }
        assertEquals(370000000L, active.versionCode)
        assertFalse(r.artProperties.valueOrNull()!!.containsKey("ro.serialno"))
        val arm64 = r.isa.first { it.isa == "arm64" }.arm64!!
        assertEquals(listOf("configured_feature_not_in_hwcap"), arm64.findings.map { it.id })
        assertTrue(arm64.findings.single().message.contains("dotprod"))
        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), r.isa.map { it.abi })
    }

    @Test
    fun dexoptStatusShapes() {
        val ok = ZartParser.dexoptStatus(contract("zart/dexopt-status-fixture.json")).valueOrNull()!!
        assertEquals("com.example.app", ok.packageName)
        assertEquals("speed-profile", ok.primary.single().statuses.first { it.primaryAbi }.status)
        assertEquals("public", ok.secondary.single().visibility)
        val denied = ZartParser.dexoptStatus(contract("zart/dexopt-status-denied.json"))
        assertEquals("permission_denied", (denied as Obs.Missing).status)
        val host = ZartParser.dexoptStatus(contract("zart/dexopt-status-host.json"))
        assertEquals("unavailable", (host as Obs.Missing).status)
    }

    @Test(expected = ZartFormatException::class)
    fun otherSchemaIsRefused() {
        ZartParser.inspect(contract("zart/inspect-host.json").replace("\"schema\": 1", "\"schema\": 2"))
    }

    @Test
    fun packageNamesMatchZart() {
        for (ok in listOf("android", "com.example.app", "a.b_c.D9")) assertTrue(ok, PackageNames.isValid(ok))
        for (bad in listOf("", ".a", "a.", "a..b", "1a.b", "a.b-c", "a b", "a;b", "a/b", "\$(id)", "a".repeat(256))) {
            assertFalse(bad, PackageNames.isValid(bad))
        }
    }

    @Test
    fun clientPaths() {
        val exe = "/data/app/~~Ab==/com.zairenkaiprjctx.app-Xy==/lib/arm64/libzart.so"
        val runner = FakeRunner().on(listOf(exe, "inspect"), 0, contract("zart/inspect-fixture.json"))
        val root = grantedRoot(runner)
        val client = ZartClient({ exe }, runner, root)
        assertTrue(client.inspect() is ZartResult.Ok)

        runner.on(su("'$exe' 'dexopt-status' 'com.example.app'"), 0, contract("zart/dexopt-status-fixture.json"))
        val d = client.dexoptStatus("com.example.app") as ZartResult.Ok
        assertTrue(d.value is Obs.Value)
        // Invalid input never reaches a process.
        val before = runner.calls.size
        assertTrue(client.dexoptStatus("x;reboot") is ZartResult.Failed)
        assertEquals(before, runner.calls.size)

        val missing = ZartClient({ null }, runner, root).inspect() as ZartResult.Failed
        assertTrue(missing.missingBinary)
        val crashed = FakeRunner().on(listOf(exe, "inspect"), CommandOutcome.SpawnFailed("exec format error"))
        assertTrue((ZartClient({ exe }, crashed, RootShell(crashed, MemoryConsent(), { false })).inspect() as ZartResult.Failed).missingBinary)
    }
}
