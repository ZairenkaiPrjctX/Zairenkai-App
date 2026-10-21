// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.core.FasProfileName
import com.zairenkaiprjctx.integration.fas.FasCall
import com.zairenkaiprjctx.integration.fas.FasClient
import com.zairenkaiprjctx.integration.fas.FasCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parses `zfasd ctl` output captured from a real observe-only zfasd. */
class FasClientTest {
    private val base = "'/data/adb/modules/zairenkai_fas/bin/zfasd' 'ctl' '--dir' '/data/adb/zairenkai-fas'"

    private fun setup(words: String, exit: Int, stdout: String, stderr: String = ""): FasClient {
        val runner = FakeRunner()
        val root = grantedRoot(runner)
        runner.on(su("$base $words"), exit, stdout, stderr)
        return FasClient(root)
    }

    @Test
    fun statusFromRealDaemon() {
        val s = (setup("'status'", 0, contract("fas/status.json")).status() as FasCall.Ok).value
        assertEquals(1, s.version)
        assertTrue(s.running)
        assertTrue(s.observeOnly)
        assertEquals("balanced", s.defaultProfile)
        assertEquals("telemetry_unavailable", s.step!!.decision.state)
        assertNull(s.displayableFps)
    }

    @Test
    fun fpsHiddenWhileTelemetryIsInvalid() {
        val text = contract("fas/status.json")
            .replace("\"telemetry_error\": null", "\"telemetry_error\": \"dumpsys failed\"")
            .replace("\"fps\": null", "\"fps\": 59.8")
        val s = (FasClient(grantedRoot(FakeRunner())).parseStatus(text) as FasCall.Ok).value
        assertEquals(59.8, s.step!!.stats.fps!!, 0.0)
        assertNull(s.displayableFps)
    }

    @Test
    fun controlReplies() {
        assertEquals(FasCall.Ok("applied"), setup("'profile' 'balanced'", 0, contract("fas/profile-ok.json")).setProfile(FasProfileName.BALANCED))
        val rejected = setup("'enable'", 1, """{"ok":false,"error":"config invalid"}""").command(FasCommand.ENABLE)
        assertEquals(FasCall.Rejected("config invalid"), rejected)
        assertEquals(FasCall.NotInstalled, setup("'reset'", 127, "", "not found").command(FasCommand.RESET))
        assertTrue(setup("'status'", 2, "", "ctl: cannot connect").status() is FasCall.Unreachable)
    }

    @Test
    fun unsupportedSchemaIsRefused() {
        val text = contract("fas/status.json").replaceFirst("\"version\": 1", "\"version\": 2")
        assertTrue(FasClient(grantedRoot(FakeRunner())).parseStatus(text) is FasCall.Failed)
    }
}
