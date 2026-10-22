// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.core

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Core's `context.telemetry` with the clock it was collected on. `observed_at_ms`,
 * `collected_at_ms` and `ttl_ms` share Core's monotonic clock, so freshness is decided exactly as
 * Core decides it (`Observation::fresh_value`, `crates/zcore-domain/src/observation.rs`).
 */
data class CoreTelemetry(val collectedAtMs: Long, val fields: Map<String, CoreObservation>) {
    operator fun get(key: String): CoreObservation? = fields[key]

    /** [key] read with the unit Core declares for it, or null when Core did not send the field. */
    fun reading(key: String): TelemetryField? = fields[key]?.let { o -> TelemetryField(key, o.source, interpret(key, o), staleBy(o)) }

    /** How far past its TTL a value is, or null while it is fresh (or carries no value). */
    private fun staleBy(o: CoreObservation): Long? {
        if (o.status != "value") return null
        val age = collectedAtMs - o.observedAtMs
        return if (age < 0 || age > o.ttlMs) age else null
    }

    private fun interpret(key: String, o: CoreObservation): TelemetryReading {
        if (o.status != "value") return TelemetryReading.NotReported(o.status, o.text)
        val p = o.value as? JsonPrimitive ?: return TelemetryReading.Invalid("$key is not a single value")
        return when (key) {
            // MilliCelsius: Core multiplies power_supply `temp` (tenths of a degree) by 100.
            "battery_temp" -> p.longOrNull?.let { milli ->
                val c = milli / 1000.0
                if (c in BATTERY_CELSIUS) TelemetryReading.Temperature(c)
                else TelemetryReading.Invalid("implausible battery temperature %.1f °C (%d m°C)".format(c, milli))
            } ?: TelemetryReading.Invalid("battery_temp is not an integer")
            "battery_level" -> p.longOrNull?.takeIf { it in 0..100 }?.let { TelemetryReading.Level(it.toInt()) }
                ?: TelemetryReading.Invalid("battery_level ${p.contentOrNull} is outside 0..100")
            "charging", "low_power_mode" -> p.booleanOrNull?.let { TelemetryReading.Flag(it) }
                ?: TelemetryReading.Invalid("$key is not true or false")
            "thermal_status" -> p.contentOrNull?.takeIf { it in THERMAL_STATUSES }?.let { TelemetryReading.ThermalLevel(it) }
                ?: TelemetryReading.Invalid("unknown thermal status ${p.contentOrNull}")
            "mem_available" -> p.longOrNull?.takeIf { it >= 0 }?.let { TelemetryReading.MemoryKib(it) }
                ?: TelemetryReading.Invalid("mem_available is not a size")
            else -> TelemetryReading.Invalid("$key has no known unit")
        }
    }

    companion object {
        /** Wider than any battery operates in; outside it the reading is a sentinel or a unit error (e.g. -273 °C). */
        val BATTERY_CELSIUS = -40.0..100.0

        /** `ThermalStatus` in `crates/zcore-domain/src/device.rs` (PowerManager.THERMAL_STATUS_*). */
        val THERMAL_STATUSES = listOf("none", "light", "moderate", "severe", "critical", "emergency", "shutdown")
    }
}

data class TelemetryField(val key: String, val source: String, val reading: TelemetryReading, val staleByMs: Long?) {
    val stale: Boolean get() = staleByMs != null
}

sealed interface TelemetryReading {
    data class Temperature(val celsius: Double) : TelemetryReading
    data class Level(val percent: Int) : TelemetryReading
    data class Flag(val on: Boolean) : TelemetryReading
    data class ThermalLevel(val status: String) : TelemetryReading
    data class MemoryKib(val kib: Long) : TelemetryReading

    /** Core reported `unavailable`, `unsupported`, `permission_denied` or `unknown`, with its reason. */
    data class NotReported(val status: String, val detail: String?) : TelemetryReading

    /** Core sent a value this app cannot trust (wrong type, out of range); never shown as a measurement. */
    data class Invalid(val detail: String) : TelemetryReading
}
