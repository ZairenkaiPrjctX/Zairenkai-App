package com.zairenkaiprjctx.integration.core

import com.zairenkaiprjctx.integration.exec.RootOutcome
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.exec.ShellScript
import com.zairenkaiprjctx.integration.exec.ShellWord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Zairenkai-Core protocol v1 (`docs/api/PROTOCOL.md`, `crates/zcore-domain/src/protocol.rs`),
 * reached through `zcored ctl` as a root client (ADR-0005: the socket is root-only in 1.x).
 * Clients must ignore unknown result fields, so decoding ignores unknown keys.
 */
object CorePaths {
    const val MODULE_ID = "zairenkai_core"
    const val BIN = "/data/adb/modules/zairenkai_core/bin/zcored"
    const val DIR = "/data/adb/zairenkai-core"
}

@Serializable
data class CoreHealth(
    val state: String,
    val reasons: List<String> = emptyList(),
    val version: String,
    @SerialName("core_api") val coreApi: String,
    val protocol: Int,
    @SerialName("policy_version") val policyVersion: String,
    @SerialName("uptime_ms") val uptimeMs: Long,
    @SerialName("context_age_ms") val contextAgeMs: Long? = null,
    val leases: Int = 0,
    @SerialName("audit_dropped") val auditDropped: Long = 0,
)

@Serializable
data class CoreCapability(
    val id: String,
    val status: String,
    val owner: String,
    val evidence: List<String> = emptyList(),
    @SerialName("verified_on_device") val verifiedOnDevice: Boolean = false,
)

@Serializable
data class CoreModule(
    val id: String,
    val health: String,
    val compatible: Boolean = false,
    val detail: String = "",
    val version: String? = null,
    val owns: List<String> = emptyList(),
    @SerialName("requested_permissions") val requestedPermissions: List<String> = emptyList(),
)

@Serializable
data class RuleOutcome(val rule: String, val tier: String, val verdict: String, val explanation: String)

@Serializable
data class Decision(
    val verdict: String,
    val tier: String,
    val rules: List<RuleOutcome> = emptyList(),
    @SerialName("policy_version") val policyVersion: String,
)

@Serializable
data class ActionResult(
    @SerialName("request_id") val requestId: String,
    val outcome: String,
    val decision: Decision? = null,
    val plan: JsonElement? = null,
    val verification: JsonElement? = null,
    @SerialName("lease_id") val leaseId: String? = null,
    val message: String = "",
)

/** A Core observation: provenance plus `{status, value}`. */
data class CoreObservation(val status: String, val value: JsonElement?, val source: String, val provenance: String) {
    val text: String? get() = (value as? JsonPrimitive)?.contentOrNull
}

/** Operations Core accepts (closed set; `crates/zcore-domain/src/request.rs`). */
sealed interface CoreOperation {
    data class FasSetProfile(val profile: FasProfileName) : CoreOperation
    data class FasSetEnabled(val enabled: Boolean) : CoreOperation
    data class SetLowPowerMode(val enabled: Boolean) : CoreOperation

    fun toJson(): JsonObject = buildJsonObject {
        when (val op = this@CoreOperation) {
            is FasSetProfile -> { put("op", "fas_set_profile"); put("profile", op.profile.wire) }
            is FasSetEnabled -> { put("op", "fas_set_enabled"); put("enabled", op.enabled) }
            is SetLowPowerMode -> { put("op", "set_low_power_mode"); put("enabled", op.enabled) }
        }
    }
}

/** FAS profiles as both Core and zfasd name them. */
enum class FasProfileName(val wire: String, val experimental: Boolean = false) {
    POWER_SAVER("power_saver"),
    BALANCED("balanced"),
    PERFORMANCE("performance"),
    FAST_RESPONSE("fast_response"),
    ADAPTIVE("adaptive", experimental = true),
    ;

    companion object {
        fun fromWire(s: String?): FasProfileName? = entries.firstOrNull { it.wire == s }
    }
}

sealed interface CoreCall<out T> {
    data class Ok<T>(val value: T) : CoreCall<T>

    /** Core answered with a protocol error (`bad_request`, `rate_limited`, ...). */
    data class Error(val code: String, val message: String) : CoreCall<Nothing>

    /** The module binary does not exist. */
    data object NotInstalled : CoreCall<Nothing>

    /** The binary exists but the daemon did not answer (not running, or starting). */
    data class Unreachable(val detail: String) : CoreCall<Nothing>
    data class NoRoot(val detail: String) : CoreCall<Nothing>
    data class Failed(val detail: String) : CoreCall<Nothing>
}

class CoreClient(private val root: RootShell) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun script(vararg extra: ShellWord): ShellScript = ShellScript(
        listOf(
            ShellWord.arg(CorePaths.BIN)!!, ShellWord.arg("ctl")!!, ShellWord.arg("--dir")!!, ShellWord.arg(CorePaths.DIR)!!,
        ) + extra,
    )

    /** Run one read-only or typed method and return the `result` element. */
    internal fun call(method: String, params: JsonObject? = null): CoreCall<JsonElement> {
        val methodWord = ShellWord.arg(method) ?: return CoreCall.Failed("invalid method")
        val words = buildList {
            add(methodWord)
            if (params != null) add(ShellWord.json(params.toString()) ?: return CoreCall.Failed("parameters contain disallowed characters"))
        }
        return when (val o = root.run(script(*words.toTypedArray()), 20_000)) {
            is RootOutcome.NotGranted -> CoreCall.NoRoot("root integration is not enabled (${o.state})")
            is RootOutcome.Failed -> CoreCall.Failed(o.detail)
            is RootOutcome.Finished -> {
                val r = o.result
                when {
                    r.exitCode == RootShell.EXIT_NOT_FOUND -> CoreCall.NotInstalled
                    // zcored ctl: 2 = cannot reach the daemon (message on stderr).
                    r.exitCode == 2 -> CoreCall.Unreachable(r.stderr.trim().take(300))
                    else -> parseResponse(r.stdout, r.exitCode, r.stderr)
                }
            }
        }
    }

    internal fun parseResponse(stdout: String, exit: Int, stderr: String): CoreCall<JsonElement> {
        val obj = try {
            json.parseToJsonElement(stdout).jsonObject
        } catch (e: Exception) {
            return CoreCall.Failed("zcored ctl exited $exit without JSON: ${stderr.trim().take(300)}")
        }
        val ok = (obj["ok"] as? JsonPrimitive)?.contentOrNull == "true"
        if (!ok) {
            val err = obj["error"] as? JsonObject
            return CoreCall.Error(
                (err?.get("code") as? JsonPrimitive)?.contentOrNull ?: "unknown",
                (err?.get("message") as? JsonPrimitive)?.contentOrNull ?: "",
            )
        }
        return obj["result"]?.let { CoreCall.Ok(it) } ?: CoreCall.Failed("response without result")
    }

    private inline fun <reified T> CoreCall<JsonElement>.decode(): CoreCall<T> = when (this) {
        is CoreCall.Ok -> try {
            CoreCall.Ok(json.decodeFromJsonElement<T>(value))
        } catch (e: Exception) {
            CoreCall.Failed("unexpected Core result: ${e.message?.take(200)}")
        }
        is CoreCall.Error -> this
        CoreCall.NotInstalled -> CoreCall.NotInstalled
        is CoreCall.Unreachable -> this
        is CoreCall.NoRoot -> this
        is CoreCall.Failed -> this
    }

    fun health(): CoreCall<CoreHealth> = call("health").decode()
    fun capabilities(): CoreCall<List<CoreCapability>> = call("capabilities").decode()
    fun modules(): CoreCall<List<CoreModule>> = call("modules").decode()

    /** Thermal status, battery saver and other telemetry, keyed by field name. */
    fun telemetry(): CoreCall<Map<String, CoreObservation>> = when (val r = call("context")) {
        is CoreCall.Ok -> try {
            val t = r.value.jsonObject["context"]!!.jsonObject["telemetry"]!!.jsonObject
            CoreCall.Ok(t.mapNotNull { (k, v) -> parseObservation(v)?.let { k to it } }.toMap())
        } catch (e: Exception) {
            CoreCall.Failed("unexpected Core context: ${e.message?.take(200)}")
        }
        else -> r.decode<Map<String, CoreObservation>>()
    }

    internal fun parseObservation(e: JsonElement): CoreObservation? {
        val o = e as? JsonObject ?: return null
        val v = o["value"] as? JsonObject ?: return null
        return CoreObservation(
            status = (v["status"] as? JsonPrimitive)?.contentOrNull ?: return null,
            value = v["value"],
            source = (o["source"] as? JsonPrimitive)?.contentOrNull ?: "",
            provenance = (o["provenance"] as? JsonPrimitive)?.contentOrNull ?: "",
        )
    }

    /**
     * Submit a typed operation. With [dryRun], Core evaluates policy and plans without executing,
     * which is how the UI previews a decision before the user confirms.
     */
    fun submit(op: CoreOperation, dryRun: Boolean, durationMs: Long? = null): CoreCall<ActionResult> {
        val params = buildJsonObject {
            put("operation", op.toJson())
            put("dry_run", dryRun)
            if (durationMs != null) put("duration_ms", durationMs)
            put("reason", "zairenkai-app")
        }
        return call("submit", params).decode()
    }

    /** The most recent audit events (oldest first), as raw JSON objects. */
    fun audit(limit: Int): CoreCall<JsonElement> = call("audit", buildJsonObject { put("limit", limit.coerceIn(1, 500)) })
}
