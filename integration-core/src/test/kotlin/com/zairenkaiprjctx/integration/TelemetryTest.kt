// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.core.CoreCall
import com.zairenkaiprjctx.integration.core.CoreClient
import com.zairenkaiprjctx.integration.core.CoreObservation
import com.zairenkaiprjctx.integration.core.CoreTelemetry
import com.zairenkaiprjctx.integration.core.TelemetryReading
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Core telemetry read with the units Core declares (`crates/zcore-domain/src/device.rs`). The
 * envelope cases use the context captured from a real zcored; the unit cases build observations
 * directly, because a host has no battery or thermal zones to capture.
 */
class TelemetryTest {
    private val base = "'/data/adb/modules/zairenkai/bin/zcored' 'ctl' '--dir' '/data/adb/zairenkai/core'"

    private fun context(stdout: String): CoreCall<CoreTelemetry> {
        val runner = FakeRunner()
        runner.on(su("$base 'context'"), 0, stdout)
        return CoreClient(grantedRoot(runner)).telemetry()
    }

    private fun obs(value: Any?, status: String = "value", at: Long = 1_000, ttl: Long = 15_000) = CoreObservation(
        status = status,
        value = when (value) { null -> null; is Number -> JsonPrimitive(value); is Boolean -> JsonPrimitive(value); else -> JsonPrimitive(value.toString()) },
        source = "test", provenance = "measured", observedAtMs = at, ttlMs = ttl,
    )

    private fun read(key: String, o: CoreObservation, now: Long = 2_000) = CoreTelemetry(now, mapOf(key to o)).reading(key)!!

    @Test
    fun realContextKeepsCoresClockAndReportsUnsupportedSources() {
        val raw = contract("core/context.json")
        val t = (context(raw) as CoreCall.Ok).value
        val collected = Json.parseToJsonElement(raw).jsonObject.getValue("result").jsonObject
            .getValue("context").jsonObject.getValue("collected_at_ms").jsonPrimitive.long
        assertEquals(collected, t.collectedAtMs)
        val battery = t.reading("battery_temp")!!
        assertEquals(TelemetryReading.NotReported("unsupported", null), battery.reading)
        assertEquals("/sys/class/power_supply/battery/temp", battery.source)
        assertFalse(battery.stale)
        val mem = t.reading("mem_available")!!
        assertTrue(mem.reading is TelemetryReading.MemoryKib)
        assertFalse(mem.stale)
    }

    @Test
    fun batteryTemperatureIsMilliCelsius() {
        assertEquals(TelemetryReading.Temperature(37.9), read("battery_temp", obs(37_900)).reading)
        assertEquals(TelemetryReading.Temperature(-5.0), read("battery_temp", obs(-5_000)).reading)
    }

    @Test
    fun sentinelAndUnitErrorsAreNotMeasurements() {
        assertTrue(read("battery_temp", obs(-273_000)).reading is TelemetryReading.Invalid)
        // A tenths-of-a-degree value passed through unscaled would read as 0.4 °C; a decicelsius
        // value multiplied twice would read 3790 °C.
        assertTrue(read("battery_temp", obs(3_790_000)).reading is TelemetryReading.Invalid)
        assertTrue(read("battery_temp", obs("hot")).reading is TelemetryReading.Invalid)
        assertTrue(read("battery_level", obs(150)).reading is TelemetryReading.Invalid)
        assertTrue(read("thermal_status", obs("melting")).reading is TelemetryReading.Invalid)
        assertTrue(read("charging", obs("yes")).reading is TelemetryReading.Invalid)
    }

    @Test
    fun typedFields() {
        assertEquals(TelemetryReading.Level(80), read("battery_level", obs(80)).reading)
        assertEquals(TelemetryReading.Flag(true), read("charging", obs(true)).reading)
        assertEquals(TelemetryReading.ThermalLevel("moderate"), read("thermal_status", obs("moderate")).reading)
    }

    @Test
    fun staleValuesAreFlaggedWithTheirAge() {
        val fresh = read("battery_temp", obs(37_900, at = 1_000, ttl = 15_000), now = 16_000)
        assertNull(fresh.staleByMs)
        val stale = read("battery_temp", obs(37_900, at = 1_000, ttl = 15_000), now = 16_001)
        assertEquals(15_001L, stale.staleByMs)
        assertEquals(TelemetryReading.Temperature(37.9), stale.reading)
        // Observed after collection: the clocks disagree, so it is not trusted as fresh.
        assertTrue(read("battery_temp", obs(37_900, at = 5_000), now = 2_000).stale)
    }

    @Test
    fun unavailableKeepsCoresReason() {
        val r = read("thermal_status", obs("spawn failed: no dumpsys", status = "unavailable")).reading
        assertEquals(TelemetryReading.NotReported("unavailable", "spawn failed: no dumpsys"), r)
        assertFalse(read("thermal_status", obs(null, status = "unknown", at = 0)).stale)
    }

    @Test
    fun malformedContextIsAContractFailure() {
        assertTrue(context("""{"v":1,"id":"x","ok":true,"result":{"context":{}}}""") is CoreCall.Failed)
        assertTrue(context("""{"v":1,"id":"x","ok":true,"result":{"context":{"telemetry":{}}}}""") is CoreCall.Failed)
    }
}
