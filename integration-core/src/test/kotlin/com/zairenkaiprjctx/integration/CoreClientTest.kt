package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.core.CoreCall
import com.zairenkaiprjctx.integration.core.CoreClient
import com.zairenkaiprjctx.integration.core.CoreOperation
import com.zairenkaiprjctx.integration.core.FasProfileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses responses captured from a real zcored (commit in contracts/PROVENANCE.json). */
class CoreClientTest {
    private val base = "'/data/adb/modules/zairenkai_core/bin/zcored' 'ctl' '--dir' '/data/adb/zairenkai-core'"

    private fun client(method: String, exit: Int, stdout: String, stderr: String = ""): Pair<CoreClient, FakeRunner> {
        val runner = FakeRunner()
        val root = grantedRoot(runner)
        runner.on(su("$base '$method'"), exit, stdout, stderr)
        return CoreClient(root) to runner
    }

    @Test
    fun healthFromRealDaemon() {
        val (c, _) = client("health", 0, contract("core/health.json"))
        val h = (c.health() as CoreCall.Ok).value
        assertEquals("degraded", h.state)
        assertEquals(1, h.protocol)
        assertEquals("1.0.0", h.coreApi)
        assertTrue(h.reasons.any { it.contains("fas.profile") })
    }

    @Test
    fun capabilitiesModulesAndContext() {
        val caps = (client("capabilities", 0, contract("core/capabilities.json")).first.capabilities() as CoreCall.Ok).value
        assertTrue(caps.any { it.id == "fas.profile" && it.status == "unsupported" })
        val mods = (client("modules", 0, contract("core/modules.json")).first.modules() as CoreCall.Ok).value
        assertTrue(mods.any { it.id == "zairenkai_thermaltools" && it.health == "not_installed" })
        val tel = (client("context", 0, contract("core/context.json")).first.telemetry() as CoreCall.Ok).value
        val thermal = tel.getValue("thermal_status")
        assertEquals("unavailable", thermal.status)
        assertEquals("dumpsys thermalservice", thermal.source)
    }

    @Test
    fun dryRunSubmitShowsEveryRule() {
        val runner = FakeRunner()
        val root = grantedRoot(runner)
        val params = """{"operation":{"op":"fas_set_profile","profile":"performance"},"dry_run":true,"reason":"zairenkai-app"}"""
        runner.on(su("$base 'submit' '$params'"), 0, contract("core/submit-dry-run.json"))
        val r = (CoreClient(root).submit(CoreOperation.FasSetProfile(FasProfileName.PERFORMANCE), dryRun = true) as CoreCall.Ok).value
        assertEquals("rejected", r.outcome)
        assertEquals("deny", r.decision!!.verdict)
        assertEquals(4, r.decision!!.rules.size)
        assertTrue(r.decision!!.rules.any { it.rule == "builtin.thermal" && it.verdict == "deny" })
    }

    @Test
    fun installationAndDaemonStates() {
        assertEquals(CoreCall.NotInstalled, client("health", 127, "", "sh: zcored: not found").first.health())
        val unreachable = client("health", 2, "", "ctl: cannot reach zcored at /data/adb/zairenkai-core/zcored.sock: Connection refused").first.health()
        assertTrue(unreachable is CoreCall.Unreachable)
        val err = client("health", 1, """{"v":1,"id":"x","ok":false,"error":{"code":"rate_limited","message":"slow down"}}""").first.health()
        assertEquals(CoreCall.Error("rate_limited", "slow down"), err)
        assertTrue(client("health", 0, "not json").first.health() is CoreCall.Failed)
    }

    @Test
    fun noRootNoCall() {
        val runner = FakeRunner()
        val root = com.zairenkaiprjctx.integration.exec.RootShell(runner, MemoryConsent(), suPresent = { true })
        assertTrue(CoreClient(root).health() is CoreCall.NoRoot)
        assertTrue(runner.calls.isEmpty())
    }
}
