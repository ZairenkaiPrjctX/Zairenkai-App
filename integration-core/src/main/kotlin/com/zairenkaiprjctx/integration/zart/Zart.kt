// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.zart

import com.zairenkaiprjctx.integration.exec.CommandOutcome
import com.zairenkaiprjctx.integration.exec.CommandRunner
import com.zairenkaiprjctx.integration.exec.RootOutcome
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.exec.ShellScript
import com.zairenkaiprjctx.integration.exec.ShellWord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * zart's observation (`crates/zart/src/obs.rs`): a value with its source, or the reason it is
 * missing. Never a default.
 */
sealed interface Obs<out T> {
    val source: String

    data class Value<T>(val value: T, override val source: String) : Obs<T>
    data class Missing(val status: String, override val source: String, val detail: String) : Obs<Nothing>

    fun valueOrNull(): T? = (this as? Value<T>)?.value
}

data class ApexInfo(
    val modulePath: String?,
    val preinstalledModulePath: String?,
    val versionCode: Long?,
    val versionName: String?,
    val isFactory: Boolean?,
    val isActive: Boolean?,
    val partition: String?,
)

data class Finding(val id: String, val severity: String, val message: String)

data class Arm64Features(val a53ErrataFix: Boolean, val crc: Boolean, val lse: Boolean, val fp16: Boolean, val dotprod: Boolean, val sve: Boolean)

data class Arm64Evaluation(
    val tableRevision: String,
    val effective: Arm64Features?,
    val detected: Arm64Features?,
    val findings: List<Finding>,
)

data class IsaConfig(val abi: String, val isa: String?, val variant: Obs<String>, val features: Obs<String>, val arm64: Arm64Evaluation?)

data class ZartReport(
    val schema: Int,
    val zartVersion: String,
    val uid: Obs<Long>,
    val selinuxContext: Obs<String>,
    val zartArch: String,
    val apiLevel: Obs<Long>,
    val artModule: Obs<List<ApexInfo>>,
    val artProperties: Obs<Map<String, String>>,
    val pageSizeBytes: Obs<Long>,
    val isa: List<IsaConfig>,
)

data class IsaStatus(val isa: String, val status: String, val reason: String, val primaryAbi: Boolean, val location: String?)

data class DexFile(val path: String, val visibility: String?, val classLoaderContext: String?, val statuses: List<IsaStatus>)

data class PackageDexopt(val packageName: String, val primary: List<DexFile>, val secondary: List<DexFile>)

class ZartFormatException(message: String) : Exception(message)

/** Parses zart's JSON (schema 1). Unknown fields are ignored; missing required fields fail. */
object ZartParser {
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull ?: throw ZartFormatException("missing string $key")

    private fun JsonObject.strOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.obj(key: String): JsonObject =
        (this[key] as? JsonObject) ?: throw ZartFormatException("missing object $key")

    fun <T> obs(e: JsonElement?, value: (JsonElement) -> T): Obs<T> {
        val o = e as? JsonObject ?: throw ZartFormatException("observation is not an object")
        val status = o.str("status")
        val source = o.str("source")
        return if (status == "value") {
            Obs.Value(value(o["value"] ?: throw ZartFormatException("value missing")), source)
        } else {
            Obs.Missing(status, source, o.strOrNull("detail") ?: "")
        }
    }

    private fun features(e: JsonElement?): Arm64Features? {
        val o = e as? JsonObject ?: return null
        fun b(k: String) = o[k]?.jsonPrimitive?.booleanOrNull ?: false
        return Arm64Features(b("a53_errata_fix"), b("crc"), b("lse"), b("fp16"), b("dotprod"), b("sve"))
    }

    fun inspect(text: String): ZartReport {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw ZartFormatException("not JSON: ${e.message}")
        }
        val schema = root["schema"]?.jsonPrimitive?.intOrNull ?: throw ZartFormatException("missing schema")
        if (schema != 1) throw ZartFormatException("unsupported zart schema $schema")
        val process = root.obj("process")
        return ZartReport(
            schema = schema,
            zartVersion = root.str("zart_version"),
            uid = obs(process["uid"]) { it.jsonPrimitive.longOrNull ?: throw ZartFormatException("uid") },
            selinuxContext = obs(process["selinux_context"]) { it.jsonPrimitive.content },
            zartArch = process.str("zart_arch"),
            apiLevel = obs(root["api_level"]) { it.jsonPrimitive.longOrNull ?: throw ZartFormatException("api_level") },
            artModule = obs(root["art_module"]) { v ->
                v.jsonArray.map {
                    val a = it.jsonObject
                    ApexInfo(
                        modulePath = a.strOrNull("module_path"),
                        preinstalledModulePath = a.strOrNull("preinstalled_module_path"),
                        versionCode = a["version_code"]?.jsonPrimitive?.longOrNull,
                        versionName = a.strOrNull("version_name"),
                        isFactory = a["is_factory"]?.jsonPrimitive?.booleanOrNull,
                        isActive = a["is_active"]?.jsonPrimitive?.booleanOrNull,
                        partition = a.strOrNull("partition"),
                    )
                }
            },
            artProperties = obs(root["art_properties"]) { v ->
                v.jsonObject.mapValues { (_, p) -> p.jsonPrimitive.content }
            },
            pageSizeBytes = obs(root["page_size_bytes"]) { it.jsonPrimitive.longOrNull ?: throw ZartFormatException("page size") },
            isa = (root["isa"] as? JsonArray ?: throw ZartFormatException("missing isa")).map { e ->
                val i = e.jsonObject
                val arm = i["arm64"] as? JsonObject
                IsaConfig(
                    abi = i.str("abi"),
                    isa = i.strOrNull("isa"),
                    variant = obs(i["variant"]) { it.jsonPrimitive.content },
                    features = obs(i["features"]) { it.jsonPrimitive.content },
                    arm64 = arm?.let { a ->
                        Arm64Evaluation(
                            tableRevision = a.str("table_revision"),
                            effective = features(a["effective"]),
                            detected = features(a["detected"]),
                            findings = (a["findings"] as? JsonArray ?: JsonArray(emptyList())).map {
                                val f = it.jsonObject
                                Finding(f.str("id"), f.str("severity"), f.str("message"))
                            },
                        )
                    },
                )
            },
        )
    }

    private fun dexFiles(e: JsonElement?): List<DexFile> = (e as? JsonArray ?: JsonArray(emptyList())).map { d ->
        val o = d.jsonObject
        DexFile(
            path = o.str("path"),
            visibility = o.strOrNull("visibility"),
            classLoaderContext = o.strOrNull("class_loader_context"),
            statuses = (o["statuses"] as? JsonArray ?: JsonArray(emptyList())).map { s ->
                val x = s.jsonObject
                IsaStatus(
                    x.str("isa"),
                    x.str("status"),
                    x.str("reason"),
                    x["primary_abi"]?.jsonPrimitive?.booleanOrNull ?: false,
                    x.strOrNull("location"),
                )
            },
        )
    }

    fun dexoptStatus(text: String): Obs<PackageDexopt> {
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw ZartFormatException("not JSON: ${e.message}")
        }
        return obs(root) { v ->
            val o = v.jsonObject
            PackageDexopt(o.str("package"), dexFiles(o["primary"]), dexFiles(o["secondary"]))
        }
    }
}

/** Android package name rules, identical to zart's `valid_package_name`. */
object PackageNames {
    private val SEGMENT = Regex("^[A-Za-z][A-Za-z0-9_]*$")

    @JvmStatic
    fun isValid(name: String): Boolean =
        name.isNotEmpty() && name.length <= 255 && name.split('.').all { SEGMENT.matches(it) }
}

sealed interface ZartResult<out T> {
    data class Ok<T>(val value: T) : ZartResult<T>
    data class Failed(val detail: String, val missingBinary: Boolean = false) : ZartResult<Nothing>
}

/**
 * Runs the packaged zart executable. `inspect` runs as the app's own uid; `dexopt-status` needs
 * the shell or root uid and therefore goes through [RootShell].
 */
class ZartClient(
    private val executable: () -> String?,
    private val runner: CommandRunner,
    private val root: RootShell,
) {
    fun inspect(): ZartResult<ZartReport> {
        val exe = executable() ?: return ZartResult.Failed("zart is not packaged for this ABI", missingBinary = true)
        return when (val o = runner.run(listOf(exe, "inspect"), 20_000)) {
            is CommandOutcome.Finished ->
                if (o.result.exitCode != 0) {
                    ZartResult.Failed("zart inspect exited ${o.result.exitCode}: ${o.result.stderr.take(300)}")
                } else {
                    try {
                        ZartResult.Ok(ZartParser.inspect(o.result.stdout))
                    } catch (e: ZartFormatException) {
                        ZartResult.Failed("unexpected zart output: ${e.message}")
                    }
                }
            is CommandOutcome.SpawnFailed -> ZartResult.Failed("zart could not start: ${o.detail}", missingBinary = true)
            CommandOutcome.TimedOut -> ZartResult.Failed("zart inspect timed out")
            CommandOutcome.OutputTooLarge -> ZartResult.Failed("zart output too large")
        }
    }

    fun dexoptStatus(packageName: String): ZartResult<Obs<PackageDexopt>> {
        if (!PackageNames.isValid(packageName)) return ZartResult.Failed("invalid package name")
        val exe = executable() ?: return ZartResult.Failed("zart is not packaged for this ABI", missingBinary = true)
        val exeWord = ShellWord.arg(exe) ?: return ZartResult.Failed("unexpected characters in the zart path")
        val pkgWord = ShellWord.arg(packageName) ?: return ZartResult.Failed("invalid package name")
        val sub = ShellWord.arg("dexopt-status")!!
        return when (val o = root.run(ShellScript(listOf(exeWord, sub, pkgWord)), 40_000)) {
            is RootOutcome.NotGranted -> ZartResult.Failed("root integration is not enabled (${o.state})")
            is RootOutcome.Failed -> ZartResult.Failed(o.detail)
            is RootOutcome.Finished -> {
                // zart exits 0 (value), 77 (permission denied) or 1 (unavailable), always with JSON.
                try {
                    ZartResult.Ok(ZartParser.dexoptStatus(o.result.stdout))
                } catch (e: ZartFormatException) {
                    ZartResult.Failed("zart exited ${o.result.exitCode}: ${o.result.stderr.ifBlank { e.message ?: "" }.take(300)}")
                }
            }
        }
    }
}
