// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.ai

import com.zairenkaiprjctx.integration.core.ActionResult
import com.zairenkaiprjctx.integration.core.CoreCall
import com.zairenkaiprjctx.integration.core.CoreClient
import com.zairenkaiprjctx.integration.core.CoreOperation
import com.zairenkaiprjctx.integration.core.FasProfileName
import com.zairenkaiprjctx.integration.core.TuningReading
import com.zairenkaiprjctx.integration.fas.FasCall
import com.zairenkaiprjctx.integration.fas.FasClient
import com.zairenkaiprjctx.integration.thermaltools.ThermalToolsClient
import com.zairenkaiprjctx.integration.thermaltools.ThermalToolsResult

/** What a read-only tool found, keyed so the UI can label each value in the user's language. */
enum class FactKey { STATE, VERSION, REASONS, CAPABILITIES, AVAILABLE, HOTTEST, FRAMEWORK_STATUS, BATTERY, ZONES, RUNNING, ENABLED, PROFILE, FPS, TUNING_SOC, TUNING_CPU, TUNING_GPU, TUNING_MEMORY, TUNING_STORAGE }

data class Fact(val key: FactKey, val value: String)

sealed interface ToolOutcome {
    data class Completed(val facts: List<Fact>) : ToolOutcome
    data class Failed(val detail: String) : ToolOutcome
}

/** The device behind the registry's tools. Reads only; changes go to Core's submit. */
interface AiDevice {
    fun read(toolId: String): ToolOutcome
    fun submit(op: CoreOperation, dryRun: Boolean): CoreCall<ActionResult>
}

/** A system tool turned into a Core operation, with Core's dry-run decision (null if Core did not answer). */
data class ProposedChange(val call: AiCall, val op: CoreOperation, val verdict: String?, val rules: List<String>, val detail: String)

sealed interface AgentReply {
    data class Planned(val plan: AiPlan, val reads: List<Pair<AiCall, ToolOutcome>>, val changes: List<ProposedChange>, val refused: List<Pair<AiCall, String>>) : AgentReply
    data class Rejected(val reason: String) : AgentReply
}

sealed interface ChangeResult {
    data class Applied(val message: String) : ChangeResult
    data class Failed(val detail: String) : ChangeResult
}

/**
 * The Local AI turn, as the engine's agent defines it: prompt → local model under the grammar →
 * re-validated plan → read-only tools run → system tools become Core dry runs. Nothing changes
 * until [apply] is called for a change the user approved, and only Core applies it.
 */
class LocalAgent(
    private val registry: AiToolRegistry,
    private val generate: (String) -> RuntimeCall<String>,
    private val device: AiDevice,
) {
    fun handle(utterance: String): AgentReply {
        // Small on-device models are not reliable routers. For obvious device questions, choose a
        // read-only tool deterministically so the common prompts answer from live measurements,
        // even before the optional language model has been imported. Free-form questions still use
        // the constrained local model below.
        val plan = DeviceIntentRouter.plan(registry, utterance) ?: run {
            val raw = when (val r = generate(AiPrompt.build(registry, utterance))) {
                is RuntimeCall.Ok -> r.value
                is RuntimeCall.Unavailable -> return AgentReply.Rejected("local model unavailable: ${r.reason}")
            }
            when (val p = AiPlanParser.parse(registry, raw)) {
                is PlanResult.Ok -> p.plan
                is PlanResult.Rejected -> return AgentReply.Rejected(p.reason)
            }
        }
        val reads = mutableListOf<Pair<AiCall, ToolOutcome>>()
        val changes = mutableListOf<ProposedChange>()
        val refused = mutableListOf<Pair<AiCall, String>>()
        for (call in plan.calls) {
            val tool = registry.get(call.tool) ?: continue
            when (tool.risk) {
                "read_only" -> reads += call to device.read(call.tool)
                "system" -> when (val op = operation(tool, call)) {
                    null -> refused += call to "no Core operation for ${tool.id}"
                    else -> changes += when (val d = device.submit(op, dryRun = true)) {
                        is CoreCall.Ok -> ProposedChange(
                            call, op, d.value.decision?.verdict,
                            d.value.decision?.rules.orEmpty().map { "${it.rule}: ${it.verdict} (${it.explanation})" }, d.value.message,
                        )
                        else -> ProposedChange(call, op, null, emptyList(), describe(d))
                    }
                }
                else -> refused += call to "${tool.id} is prohibited for the assistant"
            }
        }
        return AgentReply.Planned(plan, reads, changes, refused)
    }

    /** Applies a change the user approved, only if Core's dry run allowed it; Core verifies the result. */
    fun apply(change: ProposedChange): ChangeResult {
        if (change.verdict != "allow") return ChangeResult.Failed("Core did not allow this change")
        return when (val r = device.submit(change.op, dryRun = false)) {
            is CoreCall.Ok ->
                if (r.value.outcome in setOf("applied", "already_applied")) ChangeResult.Applied("${r.value.outcome}: ${r.value.message}")
                else ChangeResult.Failed("${r.value.outcome}: ${r.value.message}")
            else -> ChangeResult.Failed(describe(r))
        }
    }

    private fun operation(tool: AiTool, call: AiCall): CoreOperation? = when (tool.coreOperation) {
        "fas_set_profile" -> FasProfileName.fromWire(call.arguments["profile"])?.let { CoreOperation.FasSetProfile(it) }
        else -> null
    }

    companion object {
        fun describe(c: CoreCall<*>): String = when (c) {
            is CoreCall.Ok -> "ok"
            is CoreCall.Error -> "Core error ${c.code}: ${c.message}"
            CoreCall.NotInstalled -> "Core (zcored) is not installed"
            is CoreCall.Unreachable -> "Core is not answering: ${c.detail}"
            is CoreCall.NoRoot -> c.detail
            is CoreCall.Failed -> c.detail
        }
    }
}

/** Deterministic routing for obvious prompts; every route is to a closed, read-only tool. */
internal object DeviceIntentRouter {
    fun plan(registry: AiToolRegistry, utterance: String): AiPlan? {
        val q = utterance.lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}_]+"), " ")
            .trim()
        if (q.isEmpty()) return null
        fun has(vararg words: String) = words.any { Regex("(^| )" + Regex.escape(it) + "( |$)").containsMatchIn(q) }
        fun call(id: String) = if (registry.get(id) != null) AiCall(id, emptyMap()) else null
        val isId = has("kenapa", "mengapa", "saya", "hp", "panas", "cek", "periksa", "profil", "aktif", "perangkat", "apa", "yang", "bisa", "mana", "kesehatan", "integrasi", "kendalikan", "suhu", "memori", "penyimpanan", "sekarang", "tolong", "apakah")
        val selected: Pair<String, String>? = when {
            // Only route a real status question. A change request stays on the model -> Core
            // dry-run -> explicit human confirmation path.
            has("fas") && has("profile", "profil") &&
                has("active", "aktif", "current", "currently", "mana", "which", "status", "apa", "what") &&
                !has("set", "switch", "ubah", "ganti", "aktifkan", "apply", "atur", "enable", "disable") ->
                "fas.status" to if (isId) "Memeriksa status dan profil FAS aktif" else "Checking the active FAS profile"
            has("panas", "hot", "overheat", "overheating", "suhu", "temperature", "thermal") ->
                "thermal.snapshot" to if (isId) "Membaca sensor termal yang tersedia" else "Reading available thermal sensors"
            has("tuning", "cpu", "gpu", "governor", "scheduler", "ram", "memory", "memori", "frekuensi", "frequency", "performa", "performance", "lag", "lemot") ->
                "tuning.telemetry" to if (isId) "Membaca telemetri CPU, GPU, memori, dan penyimpanan" else "Reading CPU, GPU, memory and storage telemetry"
            has("root", "integrasi", "integration", "core", "status", "healthy", "health", "kapabilitas", "capabilities", "kontrol", "control", "kendalikan", "available") ->
                "core.health" to if (isId) "Memeriksa kesehatan integrasi Zairenkai" else "Checking Zairenkai integration health"
            else -> null
        } ?: return null
        val primary = call(selected.first) ?: return null
        val ids = when (selected.first) {
            "core.health" -> listOfNotNull(primary, call("core.capabilities"), call("tuning.telemetry"))
            else -> listOf(primary)
        }.take(AiPlanParser.MAX_CALLS)
        val clarification = when (selected.first) {
            "thermal.snapshot" -> if (isId) "Data ini menunjukkan sensor yang terbaca, bukan bukti tunggal penyebab perangkat panas." else "These readings show the available sensors; they do not by themselves prove the cause of heat."
            "tuning.telemetry" -> if (isId) "Hanya nilai yang berhasil dibaca yang ditampilkan. Pembacaan yang dibatasi izin akan tetap ditandai sebagai tidak tersedia; tidak ada pengaturan yang diubah." else "Only successful readings are shown. Permission-restricted values remain unavailable; no settings were changed."
            else -> if (isId) "Hasil ini berasal dari data perangkat yang benar-benar tersedia." else "This answer comes from the device data available right now."
        }
        return AiPlan(
            language = if (isId) "id" else "en",
            goal = selected.second,
            confidencePercent = 100,
            calls = ids,
            clarification = clarification,
        )
    }
}

/** The registry's read-only tools on the real clients: Core and zfasd through root, zthermal as the app. */
class DeviceTools(private val core: CoreClient, private val fas: FasClient, private val zthermal: ThermalToolsClient) : AiDevice {
    override fun read(toolId: String): ToolOutcome = when (toolId) {
        "core.health" -> when (val h = core.health()) {
            is CoreCall.Ok -> ToolOutcome.Completed(
                listOf(Fact(FactKey.STATE, h.value.state), Fact(FactKey.VERSION, h.value.version)) +
                    h.value.reasons.takeIf { it.isNotEmpty() }?.let { listOf(Fact(FactKey.REASONS, it.joinToString("; "))) }.orEmpty(),
            )
            else -> ToolOutcome.Failed(LocalAgent.describe(h))
        }
        "core.capabilities" -> when (val c = core.capabilities()) {
            is CoreCall.Ok -> {
                val available = c.value.filter { it.status == "read_write" || it.status == "read_only" }.map { "${it.id} (${it.status})" }
                ToolOutcome.Completed(
                    listOf(Fact(FactKey.CAPABILITIES, c.value.size.toString())) +
                        listOf(Fact(FactKey.AVAILABLE, available.joinToString(", ").ifEmpty { "-" })),
                )
            }
            else -> ToolOutcome.Failed(LocalAgent.describe(c))
        }
        "fas.status" -> when (val f = fas.status()) {
            is FasCall.Ok -> ToolOutcome.Completed(
                listOfNotNull(
                    Fact(FactKey.RUNNING, f.value.running.toString()),
                    f.value.enabled?.let { Fact(FactKey.ENABLED, it.toString()) },
                    f.value.profile?.let { Fact(FactKey.PROFILE, it) },
                    f.value.displayableFps?.let { Fact(FactKey.FPS, "%.1f".format(java.util.Locale.ROOT, it)) },
                ),
            )
            FasCall.NotInstalled -> ToolOutcome.Failed("zfasd is not installed")
            is FasCall.Unreachable -> ToolOutcome.Failed("zfasd is not answering: ${f.detail}")
            is FasCall.Rejected -> ToolOutcome.Failed(f.message)
            is FasCall.NoRoot -> ToolOutcome.Failed(f.detail)
            is FasCall.Failed -> ToolOutcome.Failed(f.detail)
        }
        "thermal.snapshot" -> when (val t = zthermal.snapshot()) {
            is ThermalToolsResult.Ok -> {
                val s = t.value.summary
                ToolOutcome.Completed(
                    listOfNotNull(
                        s.hottest?.let { Fact(FactKey.HOTTEST, "%.1f °C (%s)".format(java.util.Locale.ROOT, it.millicelsius / 1000.0, it.name ?: it.source)) },
                        s.frameworkStatus?.let { Fact(FactKey.FRAMEWORK_STATUS, it) },
                        s.batteryMillicelsius?.let { Fact(FactKey.BATTERY, "%.1f °C".format(java.util.Locale.ROOT, it / 1000.0)) },
                        Fact(FactKey.ZONES, "${s.zonesValid}/${s.zones}"),
                    ),
                )
            }
            is ThermalToolsResult.Failed -> ToolOutcome.Failed(t.detail)
        }
        "tuning.telemetry" -> when (val t = core.tuning()) {
            is CoreCall.Ok -> {
                val v = t.value
                ToolOutcome.Completed(listOf(
                    Fact(FactKey.TUNING_SOC, "${v.soc.family}${v.soc.platformId?.let { " · $it" } ?: ""}; ${v.platform.vendor}"),
                    Fact(FactKey.TUNING_CPU, buildString {
                        append("load ").append(v.cpu.load.display())
                        append("; cores ").append(v.cpu.present.ids?.size?.toString() ?: v.cpu.present.display())
                        v.cpu.cores.firstOrNull()?.let { core ->
                            append("; core ").append(core.id).append(" ").append(core.curFreq.number?.let { "%.0f MHz".format(java.util.Locale.ROOT, it / 1000.0) } ?: core.curFreq.display())
                            append("; governor ").append(core.governor.display())
                        }
                    }),
                    Fact(FactKey.TUNING_GPU, "frequency ${v.gpu.curFreq.number?.let { "%.0f MHz".format(java.util.Locale.ROOT, it / 1_000_000.0) } ?: v.gpu.curFreq.display()}; governor ${v.gpu.governor.display()}; GPU thermal ${v.thermal.gpu.display()}"),
                    Fact(FactKey.TUNING_MEMORY, "used ${v.memory.used.bytesMiB()} MiB / total ${v.memory.total.bytesMiB()} MiB; available ${v.memory.available.bytesMiB()} MiB; swap used ${v.memory.swapUsed.bytesMiB()} MiB"),
                    Fact(FactKey.TUNING_STORAGE, v.storage.joinToString("; ") { "${it.device}: ${it.scheduler.display()}" }.ifBlank { "No storage scheduler readings were reported" }),
                ))
            }
            else -> ToolOutcome.Failed(LocalAgent.describe(t))
        }
        else -> ToolOutcome.Failed("no executor for $toolId")
    }

    override fun submit(op: CoreOperation, dryRun: Boolean): CoreCall<ActionResult> = core.submit(op, dryRun)
}


/** A failed reading is shown as a status, never as a guessed or stale numeric value. */
private fun TuningReading.display(): String = if (!ok) {
    status + detail?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
} else {
    text ?: number?.let { n -> if (unit == "percent" || unit == "celsius") "%.1f %s".format(java.util.Locale.ROOT, n, if (unit == "celsius") "°C" else "%") else "%.0f %s".format(java.util.Locale.ROOT, n, unit) }
        ?: long?.let { "$it $unit" } ?: ids?.joinToString(",") ?: "available"
}

private fun TuningReading.bytesMiB(): String = long?.let { "%.0f".format(java.util.Locale.ROOT, it / 1048576.0) }
    ?: number?.let { "%.0f".format(java.util.Locale.ROOT, it / 1048576.0) }
    ?: display()
