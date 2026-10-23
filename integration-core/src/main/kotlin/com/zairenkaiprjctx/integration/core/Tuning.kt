// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Zairenkai-Tuning's telemetry document (`zairenkai.tuning` schema 1, `kind: "telemetry"`) as
 * Core's `tuning` method returns it. The contract is Zairenkai-Tuning `docs/CONTRACT.md`; ztune
 * itself is GPL-3.0 and the app never runs, links or copies it: it only reads this JSON from Core.
 */
object TuningContract {
    const val SCHEMA = "zairenkai.tuning"
    const val SCHEMA_VERSION = 1
    const val COMPONENT = "zairenkai-tuning"

    /** The `not_found` message prefix Core uses when the module carries no ztune (`TuningError::NotInstalled`). */
    internal const val NOT_INSTALLED_MESSAGE = "Zairenkai-Tuning telemetry: not installed"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Check the envelope (a consumer must refuse any other schema version), then decode. A mismatch
     * names the field, as Core's other results do.
     */
    fun telemetry(result: JsonElement): CoreCall<TuningTelemetry> {
        val o = result as? JsonObject ?: return mismatch("not an object")
        val schema = (o["schema"] as? JsonPrimitive)?.contentOrNull
        val version = (o["schema_version"] as? JsonPrimitive)?.intOrNull
        val kind = (o["kind"] as? JsonPrimitive)?.contentOrNull
        val component = ((o["component"] as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull
        return when {
            schema != SCHEMA -> mismatch("schema is $schema, not $SCHEMA")
            version != SCHEMA_VERSION -> mismatch("unsupported schema_version ${o["schema_version"]} (this app reads $SCHEMA_VERSION)")
            kind != "telemetry" -> mismatch("kind is $kind, not telemetry")
            component != COMPONENT -> mismatch("component is $component, not $COMPONENT")
            else -> try {
                CoreCall.Ok(json.decodeFromJsonElement<TuningTelemetry>(o))
            } catch (e: Exception) {
                mismatch(e.message?.lineSequence()?.first()?.take(200) ?: "undecodable")
            }
        }
    }

    private fun mismatch(why: String) = CoreCall.Failed("Core's tuning result does not match this app's contract: $why")
}

/**
 * One ztune reading. A reading whose status is not `ok` carries no value, so every accessor returns
 * null for it, whatever the producer sent: a number is never substituted for a failed read.
 */
@Serializable
data class TuningReading(
    val status: String,
    private val value: JsonElement? = null,
    val unit: String,
    val source: String? = null,
    val detail: String? = null,
) {
    val ok: Boolean get() = status == OK && value != null && value !is JsonNull

    val number: Double? get() = (value.takeIf { ok } as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
    val long: Long? get() = (value.takeIf { ok } as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
    val text: String? get() = (value.takeIf { ok } as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /** `cpu.present`: the core ids, or null when the list is unreadable or not a list of integers. */
    val ids: List<Int>? get() = (value.takeIf { ok } as? JsonArray)?.map { (it as? JsonPrimitive)?.intOrNull ?: return null }

    companion object {
        const val OK = "ok"
    }
}

@Serializable
data class TuningComponent(val name: String, val version: String)

/** SoC family as ztune identified it (`qualcomm`, `mediatek`, `samsung_exynos`, `google_tensor`, `unknown`). */
@Serializable
data class TuningSoc(val family: String, @SerialName("platform_id") val platformId: String? = null)

@Serializable
data class TuningPlatform(val vendor: String)

@Serializable
data class TuningCore(
    val id: Int,
    @SerialName("cur_freq") val curFreq: TuningReading,
    @SerialName("min_freq") val minFreq: TuningReading,
    @SerialName("max_freq") val maxFreq: TuningReading,
    val governor: TuningReading,
    val load: TuningReading,
)

@Serializable
data class TuningCpu(val present: TuningReading, val load: TuningReading, val cores: List<TuningCore> = emptyList())

@Serializable
data class TuningThermal(val cpu: TuningReading, val gpu: TuningReading)

@Serializable
data class TuningMemory(
    val total: TuningReading,
    val available: TuningReading,
    val used: TuningReading,
    @SerialName("swap_total") val swapTotal: TuningReading,
    @SerialName("swap_used") val swapUsed: TuningReading,
)

@Serializable
data class TuningGpu(val devfreq: String? = null, @SerialName("cur_freq") val curFreq: TuningReading, val governor: TuningReading)

@Serializable
data class TuningStorage(val device: String, val scheduler: TuningReading)

/** Units as ztune reports them, not rescaled: CPU kHz, GPU Hz, memory bytes, temperatures °C. */
@Serializable
data class TuningTelemetry(
    val component: TuningComponent,
    @SerialName("interval_ms") val intervalMs: Long,
    val soc: TuningSoc,
    val platform: TuningPlatform,
    val cpu: TuningCpu,
    val thermal: TuningThermal,
    val memory: TuningMemory,
    val gpu: TuningGpu,
    val storage: List<TuningStorage> = emptyList(),
)

/** Core's answer when the installed module carries no ztune, as opposed to ztune failing. */
val CoreCall.Error.tuningNotInstalled: Boolean
    get() = code == "not_found" && message.startsWith(TuningContract.NOT_INSTALLED_MESSAGE)
