// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.core.CoreCall
import com.zairenkaiprjctx.integration.core.CoreClient
import com.zairenkaiprjctx.integration.core.TuningReading
import com.zairenkaiprjctx.integration.core.TuningTelemetry
import com.zairenkaiprjctx.integration.core.tuningNotInstalled
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.registry.CoreSubsystem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Core's `tuning` method. `core/tuning.json` is Core's own TuningAdapter running the real ztune
 * (the commit Zairenkai-Modules locks) on the capture host, wrapped in Core's Response;
 * `core/tuning-not-installed.json` is the live daemon's answer when the module carries no ztune
 * (capture-contracts.sh, core_fixture_main.rs).
 */
class TuningContractTest {
    private val base = "'/data/adb/modules/zairenkai/bin/zcored' 'ctl' '--dir' '/data/adb/zairenkai/core'"

    private fun runner(vararg answers: Pair<String, Pair<Int, String>>) = FakeRunner().apply {
        answers.forEach { (method, r) -> on(su("$base '$method'"), r.first, r.second) }
    }

    private fun tuning(stdout: String, exit: Int = 0): CoreCall<TuningTelemetry> =
        CoreClient(grantedRoot(runner("tuning" to (exit to stdout)))).tuning()

    /** The captured response with its `result` document edited. */
    private fun edited(edit: MutableMap<String, JsonElement>.() -> Unit): String {
        val resp = Json.parseToJsonElement(contract("core/tuning.json")).jsonObject
        val doc = resp.getValue("result").jsonObject.toMutableMap().apply(edit)
        return JsonObject(resp + ("result" to JsonObject(doc))).toString()
    }

    private fun failure(r: CoreCall<TuningTelemetry>): String {
        assertTrue("expected a contract failure, got $r", r is CoreCall.Failed)
        return (r as CoreCall.Failed).detail
    }

    // CI re-captures these fixtures on its own runner, so the tests assert what holds on any
    // Linux capture host, not this host's particular readings.
    private fun readings(t: TuningTelemetry): List<TuningReading> =
        listOf(t.cpu.present, t.cpu.load, t.thermal.cpu, t.thermal.gpu, t.gpu.curFreq, t.gpu.governor) +
            t.cpu.cores.flatMap { listOf(it.curFreq, it.minFreq, it.maxFreq, it.governor, it.load) } +
            with(t.memory) { listOf(total, available, used, swapTotal, swapUsed) } +
            t.storage.map { it.scheduler }

    @Test
    fun capturedTelemetryDecodes() {
        val t = (tuning(contract("core/tuning.json")) as CoreCall.Ok).value
        assertEquals("zairenkai-tuning", t.component.name)
        assertEquals(200L, t.intervalMs)
        assertEquals("unknown", t.soc.family) // a Linux capture host is not a phone SoC
        assertTrue(t.cpu.cores.isNotEmpty())
        assertNotNull(t.cpu.present.ids)
        assertTrue(t.memory.total.long!! > 0)
        assertEquals("bytes", t.memory.total.unit)
        assertEquals("kHz", t.cpu.cores.first().curFreq.unit)
        assertEquals("celsius", t.thermal.cpu.unit)
    }

    @Test
    fun aFailedReadingNeverYieldsAValue() {
        val t = (tuning(contract("core/tuning.json")) as CoreCall.Ok).value
        for (r in readings(t).filterNot { it.ok }) {
            assertNull("$r", r.number)
            assertNull("$r", r.long)
            assertNull("$r", r.text)
            assertNull("$r", r.ids)
        }
        assertTrue(readings(t).any { it.ok }) // memory is readable on any Linux host
    }

    @Test
    fun aValueSentWithAFailedStatusIsNeverShown() {
        val r = Json { ignoreUnknownKeys = true }.decodeFromString(
            TuningReading.serializer(),
            """{"status":"unavailable","value":1804800,"unit":"kHz","detail":"read error"}""",
        )
        assertFalse(r.ok)
        assertNull(r.number)
        assertNull(r.long)
    }

    @Test
    fun aReadingOfTheWrongTypeIsNotCoerced() {
        val json = Json { ignoreUnknownKeys = true }
        val s = json.decodeFromString(TuningReading.serializer(), """{"status":"ok","value":"1804800","unit":"kHz"}""")
        assertNull(s.number)
        assertEquals("1804800", s.text)
        val n = json.decodeFromString(TuningReading.serializer(), """{"status":"ok","value":null,"unit":"kHz"}""")
        assertFalse(n.ok)
        val ids = json.decodeFromString(TuningReading.serializer(), """{"status":"ok","value":[0,"x"],"unit":"count"}""")
        assertNull(ids.ids)
    }

    @Test
    fun theEnvelopeIsChecked() {
        assertTrue(failure(tuning(edited { put("schema_version", JsonPrimitive(2)) })).contains("unsupported schema_version 2"))
        assertTrue(failure(tuning(edited { put("kind", JsonPrimitive("capabilities")) })).contains("kind is capabilities"))
        assertTrue(failure(tuning(edited { put("schema", JsonPrimitive("zairenkai.other")) })).contains("schema is zairenkai.other"))
        assertTrue(failure(tuning(edited { put("component", JsonObject(mapOf("name" to JsonPrimitive("x"), "version" to JsonPrimitive("1")))) })).contains("component is x"))
        assertTrue(failure(tuning(edited { remove("schema_version") })).contains("schema_version"))
    }

    @Test
    fun aMissingSectionIsAContractMismatchNamingIt() {
        val detail = failure(tuning(edited { remove("memory") }))
        assertTrue(detail, detail.contains("tuning"))
        assertTrue(detail, detail.contains("memory"))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val r = tuning(edited { put("future_section", JsonObject(emptyMap())) })
        assertTrue(r is CoreCall.Ok)
    }

    @Test
    fun theLiveDaemonsNotInstalledAnswerIsRecognised() {
        val r = tuning(contract("core/tuning-not-installed.json"), exit = 1)
        assertTrue(r is CoreCall.Error)
        r as CoreCall.Error
        assertEquals("not_found", r.code)
        assertTrue(r.tuningNotInstalled)
    }

    @Test
    fun aFailingZtuneIsNotReportedAsNotInstalled() {
        val body = """{"v":1,"id":"x","ok":false,"error":{"code":"not_found","message":"Zairenkai-Tuning telemetry: timed out"}}"""
        val r = tuning(body, exit = 1) as CoreCall.Error
        assertFalse(r.tuningNotInstalled)
    }

    private fun subsystem(runner: FakeRunner, root: RootShell) =
        CoreSubsystem(CoreClient(root), root, "8931cbb").probe().associate { it.id to it.availability }

    @Test
    fun theTuningRecordFollowsWhatCoreReports() {
        val present = runner("health" to (0 to contract("core/health.json")), "tuning" to (0 to contract("core/tuning.json")))
        val ok = subsystem(present, grantedRoot(present))
        assertEquals(AvailabilityState.AVAILABLE, ok.getValue("tuning.telemetry").state)
        assertTrue(ok.getValue("tuning.telemetry").reason.contains("SoC family unknown"))

        val missing = runner("health" to (0 to contract("core/health.json")), "tuning" to (1 to contract("core/tuning-not-installed.json")))
        val m = subsystem(missing, grantedRoot(missing))
        assertEquals(AvailabilityState.COMPONENT_NOT_INSTALLED, m.getValue("tuning.telemetry").state)
        assertEquals(AvailabilityState.AVAILABLE, m.getValue("core.health").state)
    }

    @Test
    fun withoutRootOrCoreTheTuningRecordSaysWhyAndZtuneIsNotAsked() {
        val noRoot = FakeRunner()
        val n = subsystem(noRoot, RootShell(noRoot, MemoryConsent(), suPresent = { true }))
        assertEquals(AvailabilityState.ROOT_REQUIRED, n.getValue("tuning.telemetry").state)
        assertTrue(noRoot.calls.isEmpty())

        val noCore = FakeRunner().apply { on(su("$base 'health'"), 127, "", "not found") }
        val c = subsystem(noCore, grantedRoot(noCore))
        assertEquals(AvailabilityState.COMPONENT_NOT_INSTALLED, c.getValue("tuning.telemetry").state)
        assertTrue(noCore.calls.none { it == su("$base 'tuning'") })
    }
}
