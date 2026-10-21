// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.thermaltools

import com.zairenkaiprjctx.integration.exec.CommandOutcome
import com.zairenkaiprjctx.integration.exec.CommandRunner
import com.zairenkaiprjctx.integration.exec.RootOutcome
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.exec.ShellScript
import com.zairenkaiprjctx.integration.exec.ShellWord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * A zthermal reading (`DataStatus` in zthermal-core): a value, or the status and reason it is
 * missing. zthermal never substitutes a value for a failed read, and neither does this parser.
 */
data class Reading(val millicelsius: Long?, val status: String, val reason: String?)

data class ThermalZone(
    val path: String,
    val type: String?,
    val category: String?,
    val temperature: Reading,
    val tripCount: Int,
)

/** One entry of zthermal's capability list, as zthermal reports it. */
data class ThermalCapability(
    val id: String,
    val description: String,
    val state: String,
    val privilege: String,
    val access: String,
    val reason: String?,
    val hardwareTested: Boolean,
)

data class SourceStatus(val source: String, val status: String, val detail: String?)

data class HottestZone(val source: String, val name: String?, val millicelsius: Long)

data class ThermalSummary(
    val zones: Int,
    val zonesValid: Int,
    val zonesInvalid: Int,
    val coolingDevices: Int,
    val hottest: HottestZone?,
    /** `ThrottlingSeverity` from `dumpsys thermalservice`, lower case (none, light, …), or null. */
    val frameworkStatus: String?,
    val batteryMillicelsius: Long?,
)

data class ThermalSnapshot(
    val schemaVersion: Int,
    val toolVersion: String,
    /** "device" for a live run; "fixture" when zthermal read a `--root` directory. */
    val root: String,
    val socVendor: String?,
    val socModel: String?,
    val oemFamily: String?,
    val sources: List<SourceStatus>,
    val summary: ThermalSummary,
    val zones: List<ThermalZone>,
    val capabilities: List<ThermalCapability>,
) {
    /** True when at least one zone read failed because access was denied (SELinux or DAC). */
    val accessDenied: Boolean get() = zones.any { it.temperature.status == "permission_denied" }
}

class ThermalToolsFormatException(message: String) : Exception(message)

/**
 * Parses `zthermal snapshot` (schema_version 1, Zairenkai-ThermalTools `schemas/snapshot.schema.json`).
 * Unknown fields are ignored; missing required fields and any other schema version fail.
 */
object ThermalToolsParser {
    const val SCHEMA_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw ThermalToolsFormatException("missing string $key")

    private fun JsonObject.strOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: throw ThermalToolsFormatException("missing integer $key")

    private fun JsonObject.obj(key: String): JsonObject =
        (this[key] as? JsonObject) ?: throw ThermalToolsFormatException("missing object $key")

    private fun JsonObject.array(key: String): JsonArray =
        (this[key] as? JsonArray) ?: throw ThermalToolsFormatException("missing array $key")

    private fun JsonElement.asObject(what: String): JsonObject =
        this as? JsonObject ?: throw ThermalToolsFormatException("$what is not an object")

    /** `{"value": …, "status": …}` fields of the identity (`Inferred<T>`), or null. */
    private fun inferred(o: JsonObject, key: String): String? = (o[key] as? JsonObject)?.strOrNull("value")

    private fun reading(o: JsonObject): Reading = Reading(
        millicelsius = (o["millicelsius"] as? JsonPrimitive)?.longOrNull,
        status = o.str("status"),
        reason = o.strOrNull("reason"),
    )

    fun snapshot(text: String): ThermalSnapshot {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw ThermalToolsFormatException("not a JSON object: ${e.message}")
        }
        val version = (root["schema_version"] as? JsonPrimitive)?.intOrNull ?: throw ThermalToolsFormatException("missing schema_version")
        if (version != SCHEMA_VERSION) throw ThermalToolsFormatException("unsupported zthermal schema_version $version")
        val identity = root.obj("identity")
        val summary = root.obj("summary")
        val sysfs = root.obj("sysfs")
        return ThermalSnapshot(
            schemaVersion = version,
            toolVersion = root.str("tool_version"),
            root = root.str("root"),
            socVendor = inferred(identity, "soc_vendor"),
            socModel = identity.strOrNull("soc_model"),
            oemFamily = inferred(identity, "oem_family"),
            sources = root.array("sources").map {
                val s = it.asObject("source")
                SourceStatus(s.str("source"), s.str("status"), s.strOrNull("detail"))
            },
            summary = ThermalSummary(
                zones = summary.int("zones"),
                zonesValid = summary.int("zones_valid"),
                zonesInvalid = summary.int("zones_invalid"),
                coolingDevices = summary.int("cooling_devices"),
                hottest = (summary["hottest_zone"] as? JsonObject)?.let {
                    HottestZone(
                        it.str("source"),
                        it.strOrNull("name"),
                        (it["millicelsius"] as? JsonPrimitive)?.longOrNull ?: throw ThermalToolsFormatException("hottest_zone without millicelsius"),
                    )
                },
                frameworkStatus = summary.strOrNull("framework_status"),
                batteryMillicelsius = (summary["battery_millicelsius"] as? JsonPrimitive)?.longOrNull,
            ),
            zones = sysfs.array("zones").map {
                val z = it.asObject("zone")
                ThermalZone(
                    path = z.str("path"),
                    type = (z["zone_type"] as? JsonObject)?.strOrNull("value"),
                    category = (z["classification"] as? JsonObject)?.strOrNull("category"),
                    temperature = reading(z.obj("temp")),
                    tripCount = (z["trips"] as? JsonArray)?.size ?: 0,
                )
            },
            capabilities = root.array("capabilities").map {
                val c = it.asObject("capability")
                ThermalCapability(
                    id = c.str("id"),
                    description = c.str("description"),
                    state = c.str("state"),
                    privilege = c.str("privilege"),
                    access = c.str("access"),
                    reason = c.strOrNull("reason"),
                    hardwareTested = (c["hardware_tested"] as? JsonPrimitive)?.booleanOrNull ?: false,
                )
            },
        )
    }
}

sealed interface ThermalToolsResult<out T> {
    data class Ok<T>(val value: T) : ThermalToolsResult<T>
    data class Failed(val detail: String, val missingBinary: Boolean = false) : ThermalToolsResult<Nothing>
}

/**
 * Runs the packaged zthermal executable. The only sub-command is `snapshot`, which reads and
 * never writes; `apply`, `rollback` and `backup-config` have no path through this class.
 * As the app uid it reads sysfs only. Through [RootShell] it also reads `dumpsys thermalservice`
 * and `dumpsys battery` (`--framework`), and only when the user asks.
 */
class ThermalToolsClient(
    private val executable: () -> String?,
    private val runner: CommandRunner,
    private val root: RootShell,
) {
    private fun parse(exitCode: Int, stdout: String, stderr: String): ThermalToolsResult<ThermalSnapshot> =
        // `snapshot` exits 0 whatever each source reported; any other exit is a failure.
        if (exitCode != 0) {
            ThermalToolsResult.Failed("zthermal snapshot exited $exitCode: ${stderr.take(300)}")
        } else {
            try {
                ThermalToolsResult.Ok(ThermalToolsParser.snapshot(stdout))
            } catch (e: ThermalToolsFormatException) {
                ThermalToolsResult.Failed("unexpected zthermal output: ${e.message}")
            }
        }

    fun snapshot(): ThermalToolsResult<ThermalSnapshot> {
        val exe = executable() ?: return ThermalToolsResult.Failed("zthermal is not packaged for this ABI", missingBinary = true)
        return when (val o = runner.run(listOf(exe, SNAPSHOT), TIMEOUT_MS)) {
            is CommandOutcome.Finished -> parse(o.result.exitCode, o.result.stdout, o.result.stderr)
            is CommandOutcome.SpawnFailed -> ThermalToolsResult.Failed("zthermal could not start: ${o.detail}", missingBinary = true)
            CommandOutcome.TimedOut -> ThermalToolsResult.Failed("zthermal snapshot timed out")
            CommandOutcome.OutputTooLarge -> ThermalToolsResult.Failed("zthermal output too large")
        }
    }

    fun snapshotWithRoot(): ThermalToolsResult<ThermalSnapshot> {
        val exe = executable() ?: return ThermalToolsResult.Failed("zthermal is not packaged for this ABI", missingBinary = true)
        val exeWord = ShellWord.arg(exe) ?: return ThermalToolsResult.Failed("unexpected characters in the zthermal path")
        val script = ShellScript(listOf(exeWord, ShellWord.arg(SNAPSHOT)!!, ShellWord.arg(FRAMEWORK)!!))
        return when (val o = root.run(script, ROOT_TIMEOUT_MS)) {
            is RootOutcome.NotGranted -> ThermalToolsResult.Failed("root integration is not enabled (${o.state})")
            is RootOutcome.Failed -> ThermalToolsResult.Failed(o.detail)
            is RootOutcome.Finished -> parse(o.result.exitCode, o.result.stdout, o.result.stderr)
        }
    }

    companion object {
        private const val SNAPSHOT = "snapshot"
        private const val FRAMEWORK = "--framework"

        /** zthermal snapshot took 2–3 ms on a host; dumpsys on a phone can take seconds. */
        const val TIMEOUT_MS = 10_000L
        const val ROOT_TIMEOUT_MS = 20_000L
    }
}
