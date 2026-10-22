// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.fas

import com.zairenkaiprjctx.integration.core.FasProfileName
import com.zairenkaiprjctx.integration.exec.RootOutcome
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.exec.ShellScript
import com.zairenkaiprjctx.integration.exec.ShellWord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Zairenkai-FAS control interface (`docs/INTEGRATION.md`): `zfasd ctl` as a root client.
 * Status schema version 1. Estimated values must be labelled as estimates in the UI, "observe
 * only" must be shown, and no FPS may be shown while `telemetry_error` is set.
 */
object FasPaths {
    /** zfasd is packaged in the single `zairenkai` module, next to zcored. */
    const val MODULE_ID = "zairenkai"
    const val BIN = "/data/adb/modules/zairenkai/bin/zfasd"
    const val DIR = "/data/adb/zairenkai/fas"
}

@Serializable
data class FasDecision(val state: String, val reason: String = "", val bottleneck: String? = null, @SerialName("cpu_level") val cpuLevel: Int? = null)

@Serializable
data class FasStats(
    val samples: Int = 0,
    val fps: Double? = null,
    @SerialName("p50_ns") val p50Ns: Long? = null,
    @SerialName("p95_ns") val p95Ns: Long? = null,
    @SerialName("miss_ratio") val missRatio: Double? = null,
    val confidence: Double? = null,
)

@Serializable
data class FasThermal(@SerialName("level_cap") val levelCap: Int? = null, val suspended: Boolean = false, val reason: String = "")

@Serializable
data class FasStep(
    val decision: FasDecision,
    val stats: FasStats = FasStats(),
    @SerialName("target_fps") val targetFps: Double? = null,
    val thermal: FasThermal? = null,
)

@Serializable
data class FasStatus(
    val version: Int,
    val running: Boolean,
    val mode: String,
    @SerialName("observe_only") val observeOnly: Boolean,
    @SerialName("package") val packageName: String? = null,
    /** Added on the FAS topic branch (commit 9d43894); absent from older daemons. */
    val enabled: Boolean? = null,
    @SerialName("default_profile") val defaultProfile: String? = null,
    val profile: String? = null,
    @SerialName("telemetry_error") val telemetryError: String? = null,
    val step: FasStep? = null,
    val errors: List<String> = emptyList(),
) {
    /** FPS may only be shown when telemetry is valid (FAS contract). */
    val displayableFps: Double? get() = if (telemetryError == null) step?.stats?.fps else null
}

enum class FasCommand(val words: List<String>) {
    ENABLE(listOf("enable")),
    DISABLE(listOf("disable")),
    RESET(listOf("reset")),
}

sealed interface FasCall<out T> {
    data class Ok<T>(val value: T) : FasCall<T>
    data class Rejected(val message: String) : FasCall<Nothing>
    data object NotInstalled : FasCall<Nothing>
    data class Unreachable(val detail: String) : FasCall<Nothing>
    data class NoRoot(val detail: String) : FasCall<Nothing>
    data class Failed(val detail: String) : FasCall<Nothing>
}

class FasClient(private val root: RootShell) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Reply(val ok: Boolean, val message: String? = null, val error: String? = null)

    private fun run(words: List<String>): FasCall<String> {
        val all = listOf(FasPaths.BIN, "ctl", "--dir", FasPaths.DIR) + words
        val script = ShellScript(all.map { ShellWord.arg(it) ?: return FasCall.Failed("invalid argument") })
        return when (val o = root.run(script, 20_000)) {
            is RootOutcome.NotGranted -> FasCall.NoRoot("root integration is not enabled (${o.state})")
            is RootOutcome.Failed -> FasCall.Failed(o.detail)
            is RootOutcome.Finished -> when (o.result.exitCode) {
                0 -> FasCall.Ok(o.result.stdout)
                RootShell.EXIT_NOT_FOUND -> FasCall.NotInstalled
                else -> {
                    val reply = runCatching { json.decodeFromString<Reply>(o.result.stdout) }.getOrNull()
                    if (reply != null && !reply.ok) {
                        FasCall.Rejected(reply.error ?: "rejected")
                    } else {
                        FasCall.Unreachable(o.result.stderr.ifBlank { o.result.stdout }.trim().take(300))
                    }
                }
            }
        }
    }

    fun status(): FasCall<FasStatus> = when (val r = run(listOf("status"))) {
        is FasCall.Ok -> parseStatus(r.value)
        is FasCall.Rejected -> r
        FasCall.NotInstalled -> r as FasCall.NotInstalled
        is FasCall.Unreachable -> r
        is FasCall.NoRoot -> r
        is FasCall.Failed -> r
    }

    fun parseStatus(text: String): FasCall<FasStatus> = try {
        val s = json.decodeFromString<FasStatus>(text)
        if (s.version != 1) FasCall.Failed("unsupported FAS status schema ${s.version}") else FasCall.Ok(s)
    } catch (e: Exception) {
        FasCall.Failed("unexpected FAS status: ${e.message?.take(200)}")
    }

    private fun reply(r: FasCall<String>): FasCall<String> = when (r) {
        is FasCall.Ok -> {
            val reply = runCatching { json.decodeFromString<Reply>(r.value) }.getOrNull()
            when {
                reply == null -> FasCall.Failed("unexpected reply")
                reply.ok -> FasCall.Ok(reply.message ?: "ok")
                else -> FasCall.Rejected(reply.error ?: "rejected")
            }
        }
        else -> r
    }

    fun command(cmd: FasCommand): FasCall<String> = reply(run(cmd.words))
    fun setProfile(profile: FasProfileName): FasCall<String> = reply(run(listOf("profile", profile.wire)))
}
