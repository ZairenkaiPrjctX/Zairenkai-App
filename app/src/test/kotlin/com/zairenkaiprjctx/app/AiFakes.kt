// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import android.content.res.AssetManager
import android.net.Uri
import com.zairenkaiprjctx.app.ui.ai.LocalAiHost
import com.zairenkaiprjctx.integration.ai.AiDevice
import com.zairenkaiprjctx.integration.ai.AiToolRegistry
import com.zairenkaiprjctx.integration.ai.Fact
import com.zairenkaiprjctx.integration.ai.FactKey
import com.zairenkaiprjctx.integration.ai.LocalAgent
import com.zairenkaiprjctx.integration.ai.ModelImport
import com.zairenkaiprjctx.integration.ai.ModelManifest
import com.zairenkaiprjctx.integration.ai.RuntimeCall
import com.zairenkaiprjctx.integration.ai.ToolOutcome
import com.zairenkaiprjctx.integration.core.ActionResult
import com.zairenkaiprjctx.integration.core.CoreCall
import com.zairenkaiprjctx.integration.core.CoreOperation
import com.zairenkaiprjctx.integration.core.Decision

/**
 * zie-run's answers as it printed them on a Linux host (integration-core
 * `contracts/intelligence/output-fas-performance.json` and `output-temperature-id.json`).
 */
internal object CapturedAnswers {
    const val FAS = """{"language": "other", "goal": "Switch FAS to the performance profile", "confidence_percent": 1,
        "calls": [{"tool": "fas.set_profile", "arguments": {"profile": "performance"}}],
        "clarification": "Please note that this tool requires a device to be in the 'zcored' state."}"""
    const val TEMPERATURE = """{"language": "id", "goal": "check temperature", "confidence_percent": 0,
        "calls": [{"tool": "thermal.snapshot", "arguments": {}}], "clarification": ""}"""
}

/** A stand-in device: fixed readings, and a Core that allows and applies; it records every submit. */
internal class StandInDevice : AiDevice {
    val submits = mutableListOf<Pair<CoreOperation, Boolean>>()

    override fun read(toolId: String) = ToolOutcome.Completed(listOf(Fact(FactKey.HOTTEST, "41.0 °C (cpu-0)"), Fact(FactKey.ZONES, "12/14")))

    override fun submit(op: CoreOperation, dryRun: Boolean): CoreCall<ActionResult> {
        submits += op to dryRun
        return CoreCall.Ok(ActionResult("r", if (dryRun) "planned" else "applied", Decision("allow", "user", emptyList(), "1"), message = "read back performance"))
    }
}

/** The real agent over the packaged registry, answering with [answer] for each prompt. */
internal class CapturedAiHost(assets: AssetManager, answer: (String) -> String, val device: StandInDevice = StandInDevice()) : LocalAiHost {
    private fun asset(a: AssetManager, path: String) = a.open(path).use { it.readBytes().decodeToString() }
    override val manifest = ModelManifest.parse(asset(assets, "intelligence/model.json"))
    private val agent = LocalAgent(AiToolRegistry.parse(asset(assets, "intelligence/tools.json")), { RuntimeCall.Ok(answer(it)) }, device)
    var imported = 0

    override fun agent() = agent
    override fun importModel(uri: Uri, progress: (Long) -> Unit): ModelImport {
        imported++
        progress(manifest.sizeBytes / 2)
        return ModelImport.WrongDigest("0".repeat(64))
    }
    override fun deleteModel() = Unit
    override suspend fun refreshAvailability() = Unit
}
