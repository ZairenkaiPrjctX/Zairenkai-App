// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.ai

import java.text.Normalizer
import java.util.Locale

/**
 * Produces a readable answer from actual typed tool output, rather than repeating the tiny model's
 * often-unhelpful JSON-plan goal. It never fabricates telemetry: missing values stay missing and
 * failed reads are reported as failed reads. The UI still shows individual tool/fact cards below.
 */
object AiAnswerComposer {
    fun compose(question: String, reply: AgentReply.Planned): String {
        // Prefer the user's language when it is recognisable; the small model sometimes labels an
        // English prompt as Indonesian (or vice versa), so its language field is only a fallback.
        val id = when {
            isIndonesian(question) -> true
            isEnglish(question) -> false
            else -> reply.plan.language == "id"
        }
        val sentences = mutableListOf<String>()
        for ((call, outcome) in reply.reads) {
            when (outcome) {
                is ToolOutcome.Failed -> sentences += if (id) "${label(call.tool, true)} tidak dapat dibaca: ${outcome.detail}." else "${label(call.tool, false)} could not be read: ${outcome.detail}."
                is ToolOutcome.Completed -> {
                    val facts = outcome.facts.associate { it.key to it.value }
                    when (call.tool) {
                        "core.health" -> {
                            val state = facts[FactKey.STATE]
                            val version = facts[FactKey.VERSION]
                            if (state != null || version != null) {
                                sentences += if (id) "Status Zairenkai-Core: ${state ?: "tidak diketahui"}${version?.let { " (versi $it)" } ?: ""}." else "Zairenkai-Core status: ${state ?: "unknown"}${version?.let { " (version $it)" } ?: ""}."
                            }
                            facts[FactKey.REASONS]?.let { why -> sentences += if (id) "Alasan/peringatan Core: $why." else "Core diagnostics: $why." }
                        }
                        "core.capabilities" -> {
                            val count = facts[FactKey.CAPABILITIES]
                            val available = facts[FactKey.AVAILABLE]
                            if (count != null) sentences += if (id) "Core melaporkan $count kemampuan; yang bisa digunakan: ${available ?: "belum ada daftar"}." else "Core reports $count capabilities; reported usable capabilities: ${available ?: "no list returned"}."
                        }
                        "thermal.snapshot" -> {
                            val hot = facts[FactKey.HOTTEST]
                            val battery = facts[FactKey.BATTERY]
                            val zones = facts[FactKey.ZONES]
                            val framework = facts[FactKey.FRAMEWORK_STATUS]
                            if (hot != null) sentences += if (id) "Sensor terpanas yang terbaca: $hot." else "Hottest readable sensor: $hot."
                            if (battery != null) sentences += if (id) "Suhu baterai yang dilaporkan: $battery." else "Reported battery temperature: $battery."
                            if (framework != null) sentences += if (id) "Status termal Android: $framework." else "Android thermal status: $framework."
                            if (zones != null) sentences += if (id) "$zones thermal zone terbaca (valid/total)." else "$zones thermal zones readable (valid/total)."
                            if (hot == null && battery == null && framework == null) sentences += if (id) "Tidak ada nilai suhu yang dapat dibaca dari snapshot ini; Zairenkai tidak akan menebak suhu." else "This snapshot returned no readable temperature values; Zairenkai will not guess a temperature."
                        }
                        "fas.status" -> {
                            val running = facts[FactKey.RUNNING]
                            val enabled = facts[FactKey.ENABLED]
                            val profile = facts[FactKey.PROFILE]
                            val fps = facts[FactKey.FPS]
                            if (running != null) sentences += if (id) "Daemon FAS berjalan: $running." else "FAS daemon running: $running."
                            if (enabled != null) sentences += if (id) "FAS diaktifkan: $enabled." else "FAS enabled: $enabled."
                            if (profile != null) sentences += if (id) "Profil FAS: $profile." else "FAS profile: $profile."
                            if (fps != null) sentences += if (id) "FPS yang dilaporkan: $fps." else "Reported FPS: $fps."
                            if (facts.isEmpty()) sentences += if (id) "FAS tidak mengembalikan data status." else "FAS returned no status data."
                        }
                        "tuning.telemetry" -> {
                            val soc = facts[FactKey.TUNING_SOC]
                            val cpu = facts[FactKey.TUNING_CPU]
                            val gpu = facts[FactKey.TUNING_GPU]
                            val memory = facts[FactKey.TUNING_MEMORY]
                            val storage = facts[FactKey.TUNING_STORAGE]
                            if (soc != null) sentences += (if (id) "Platform terdeteksi: " else "Detected platform: ") + soc + "."
                            if (cpu != null) sentences += (if (id) "Telemetri CPU: " else "CPU telemetry: ") + cpu + "."
                            if (gpu != null) sentences += (if (id) "Telemetri GPU: " else "GPU telemetry: ") + gpu + "."
                            if (memory != null) sentences += (if (id) "Memori: " else "Memory: ") + memory + "."
                            if (storage != null) sentences += (if (id) "Penyimpanan: " else "Storage: ") + storage + "."
                            if (facts.isEmpty()) sentences += if (id) "Tuning tidak mengembalikan telemetri." else "Tuning returned no telemetry."
                            if (facts.values.any { it.contains("PermissionRestricted", ignoreCase = true) || it.contains("permission restricted", ignoreCase = true) }) {
                                sentences += if (id) "Sebagian nilai dibatasi izin kernel/SELinux; saya tidak menggantinya dengan angka perkiraan." else "Some values are restricted by kernel permissions/SELinux; they are not replaced with guessed numbers."
                            }
                        }
                    }
                }
            }
        }
        for ((call, why) in reply.refused) {
            sentences += if (id) "Permintaan ${call.tool} ditolak: $why." else "The ${call.tool} request was refused: $why."
        }
        for (change in reply.changes) {
            val status = when (change.verdict) {
                "allow" -> if (id) "Core mengizinkan pratinjau ini, tetapi perubahan belum dijalankan." else "Core allowed this preview, but it has not been applied."
                "deny" -> if (id) "Core menolak perubahan ini." else "Core denied this change."
                else -> if (id) "Core belum dapat memvalidasi perubahan ini." else "Core could not validate this change."
            }
            val profile = change.call.arguments["profile"]
            sentences += (if (id) "Pratinjau profil FAS${profile?.let { " $it" } ?: ""}: " else "FAS profile${profile?.let { " $it" } ?: ""} preview: ") + "$status${change.detail.takeIf { it.isNotBlank() && it != "ok" }?.let { " $it" } ?: ""}"
        }
        if (sentences.isEmpty()) return reply.plan.clarification.ifBlank { reply.plan.goal }
        val prefix = if (id) "Hasil pemeriksaan berdasarkan data yang benar-benar dikembalikan perangkat:" else "Device findings from the data actually returned:"
        return "$prefix\n${sentences.joinToString("\n")}"
    }

    private fun label(tool: String, id: Boolean): String = when (tool) {
        "core.health" -> "Zairenkai-Core"
        "core.capabilities" -> if (id) "Kemampuan Core" else "Core capabilities"
        "thermal.snapshot" -> if (id) "Snapshot termal" else "thermal snapshot"
        "fas.status" -> "FAS"
        else -> tool
    }

    private fun isIndonesian(value: String): Boolean {
        val q = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKC)
        return Regex("\\b(kenapa|mengapa|gimana|bagaimana|hp|ponsel|panas|suhu|baterai|perangkat|tolong|saya|aku|aktif|profil|cek|periksa|ubah|ganti|sekarang|berapa|apakah|yang|untuk|dengan|tidak|bisa|mengapa|memori|penyimpanan)\\b").containsMatchIn(q)
    }

    private fun isEnglish(value: String): Boolean {
        val q = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKC)
        return Regex("\\b(why|what|which|how|when|where|is|are|my|phone|device|temperature|battery|current|active|profile|check|show|tell|please|help|does|do|can|with|and|the|of|for)\\b").containsMatchIn(q)
    }
}
