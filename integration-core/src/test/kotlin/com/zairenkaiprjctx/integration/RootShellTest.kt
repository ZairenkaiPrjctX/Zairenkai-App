package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.exec.CommandOutcome
import com.zairenkaiprjctx.integration.exec.RootOutcome
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.exec.RootState
import com.zairenkaiprjctx.integration.exec.ShellScript
import com.zairenkaiprjctx.integration.exec.ShellWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootShellTest {
    @Test
    fun wordsRejectShellSyntax() {
        for (bad in listOf("a;b", "\$(id)", "`id`", "a b", "a'b", "a\nb", "a|b", "a&b", "a>b", "", "a\"b")) {
            assertNull(bad, ShellWord.arg(bad))
        }
        for (ok in listOf("com.example.app", "/data/app/~~Ab3==/com.x-Y_z==/lib/arm64/libzart.so", "power_saver", "--dir")) {
            assertNotNull(ok, ShellWord.arg(ok))
        }
        assertNotNull(ShellWord.json("""{"operation":{"op":"fas_set_profile","profile":"performance"},"dry_run":true}"""))
        assertNull(ShellWord.json("""{"a":"'; reboot; '"}"""))
        assertNull(ShellWord.json("{\"a\":\"\$(id)\"}"))
    }

    @Test
    fun scriptQuotesEveryWord() {
        val s = ShellScript(listOf(ShellWord.arg("/x/zcored")!!, ShellWord.arg("ctl")!!, ShellWord.json("""{"limit":5}""")!!))
        assertEquals("""'/x/zcored' 'ctl' '{"limit":5}'""", s.render())
    }

    @Test
    fun nothingRunsBeforeTheUserAsks() {
        val runner = FakeRunner()
        val root = RootShell(runner, MemoryConsent(), suPresent = { true })
        assertEquals(RootState.NOT_REQUESTED, root.state)
        val r = root.run(ShellScript(listOf(ShellWord.arg("id")!!)), 1000)
        assertTrue(r is RootOutcome.NotGranted)
        assertTrue(runner.calls.isEmpty())
    }

    @Test
    fun grantRequiresUidZeroAndIsRemembered() {
        val runner = FakeRunner().on(su("id"), 0, "uid=0(root) gid=0(root)\n")
        val consent = MemoryConsent()
        val root = RootShell(runner, consent, suPresent = { true })
        assertEquals(RootState.GRANTED, root.request())
        assertTrue(consent.rootGranted)
        root.revoke()
        assertFalse(consent.rootGranted)
        assertEquals(RootState.NOT_REQUESTED, root.state)
    }

    @Test
    fun deniedOrMissingSu() {
        val denied = FakeRunner().on(su("id"), 1, "", "Permission denied")
        val c = MemoryConsent()
        assertEquals(RootState.DENIED, RootShell(denied, c, suPresent = { true }).request())
        assertFalse(c.rootGranted)
        // A shell that answers but is not uid 0 is not root.
        val notRoot = FakeRunner().on(su("id"), 0, "uid=2000(shell) gid=2000(shell)\n")
        assertEquals(RootState.DENIED, RootShell(notRoot, MemoryConsent(), suPresent = { true }).request())
        val none = FakeRunner()
        assertEquals(RootState.UNAVAILABLE, RootShell(none, MemoryConsent(), suPresent = { false }).request())
        assertTrue(none.calls.isEmpty())
        // su not on PATH: the spawn fails, which also means "not rooted".
        assertEquals(RootState.UNAVAILABLE, RootShell(FakeRunner(), MemoryConsent()).request())
    }

    @Test
    fun runFailuresAreReported() {
        val runner = FakeRunner()
        val root = grantedRoot(runner)
        val script = ShellScript(listOf(ShellWord.arg("x")!!))
        runner.on(su(script.render()), CommandOutcome.TimedOut)
        assertEquals(RootOutcome.Failed("timed out after 1 s"), root.run(script, 1000))
    }
}
