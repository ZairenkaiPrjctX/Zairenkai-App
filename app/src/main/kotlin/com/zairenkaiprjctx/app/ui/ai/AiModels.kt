// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui.ai

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zairenkaiprjctx.integration.ai.AgentReply
import com.zairenkaiprjctx.integration.ai.AiAnswerComposer
import com.zairenkaiprjctx.integration.ai.ChangeResult
import com.zairenkaiprjctx.integration.ai.Fact
import com.zairenkaiprjctx.integration.ai.LocalAgent
import com.zairenkaiprjctx.integration.ai.ModelImport
import com.zairenkaiprjctx.integration.ai.ModelManifest
import com.zairenkaiprjctx.integration.ai.ProposedChange
import com.zairenkaiprjctx.integration.ai.ToolOutcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One entry of a Zairenkai AI conversation, shaped after the Local-AI contracts in Zairenkai-Sdk
 * (`IntentPlan`, `ToolInvocation`, `ActionPreview`, `UserApproval`): the model only ever produces
 * typed tool requests from a closed registry; anything that changes the device is a preview that
 * Core checks and the user approves.
 */
sealed interface AiTurn {
    val id: String

    data class User(override val id: String, val text: String) : AiTurn

    /** The model's own words: the goal it understood and, if it asked one, its question back. */
    data class Assistant(override val id: String, val text: String, val clarification: String = "") : AiTurn

    /** A read-only tool the model asked for (`thermal.snapshot`, `fas.status`, ...) and what it found. */
    data class Tool(
        override val id: String,
        val name: String,
        val arguments: Map<String, String>,
        val status: ToolStatus,
        val facts: List<Fact> = emptyList(),
        val detail: String? = null,
    ) : AiTurn

    /** A device change the model proposed, with Core's dry-run decision, waiting for the user. */
    data class Approval(
        override val id: String,
        val title: String,
        val summary: String,
        val coreVerdict: String?,
        val rules: List<String>,
        val state: ApprovalState,
        val result: String? = null,
    ) : AiTurn

    /** The answer did not pass the checks (or the model could not run): nothing was done. */
    data class Refusal(override val id: String, val reason: String) : AiTurn
}

enum class ToolStatus { RUNNING, COMPLETED, FAILED, REJECTED }

/** APPROVED means the user said yes; only Core's verified result may later say APPLIED. */
enum class ApprovalState { PENDING, APPROVED, REJECTED, APPLIED, FAILED }

/** The model file import, as the screen shows it. */
sealed interface ModelImportUi {
    data object Idle : ModelImportUi
    data class Copying(val done: Long, val total: Long) : ModelImportUi
    data class Downloading(val done: Long, val total: Long) : ModelImportUi

    /** The download stopped at [done] bytes (cancelled or the connection dropped); downloading again resumes. */
    data class Paused(val done: Long, val total: Long, val detail: String) : ModelImportUi

    /** The model release is not published (HTTP 404), or the server answered [code]. */
    data class NotAvailable(val code: Int) : ModelImportUi
    data object Verified : ModelImportUi
    data class WrongSize(val actual: Long) : ModelImportUi
    data object WrongDigest : ModelImportUi
    data class Failed(val detail: String) : ModelImportUi
}

/** What the AI screen needs from the app; [com.zairenkaiprjctx.app.AppContainer] provides it. */
interface LocalAiHost {
    val manifest: ModelManifest?

    /** The agent over the packaged runtime and the imported model, or null when either is missing. */
    fun agent(): LocalAgent?
    fun importModel(uri: Uri, progress: (Long) -> Unit): ModelImport

    /** Downloads the model from Zairenkai-Intelligence-Engine's model release; only on the user's request. */
    fun downloadModel(progress: (Long) -> Unit, cancelled: () -> Boolean): ModelImport

    /** Bytes of an interrupted download that the next one resumes from. */
    fun partialBytes(): Long

    /** True on a metered network (mobile data), where the app asks before downloading. */
    fun meteredNetwork(): Boolean
    fun deleteModel()

    /** Re-probes `intelligence.analysis` after the model changed. */
    suspend fun refreshAvailability()
}

/**
 * The conversation with the on-device model. Every turn comes from the model's checked answer, a
 * tool's real output or Core's decision; nothing is invented. Without a host (or a model) no turn
 * is ever added.
 */
class AiViewModel(
    initial: List<AiTurn> = emptyList(),
    private val host: LocalAiHost? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val turns = MutableStateFlow(initial)
    val transcript: StateFlow<List<AiTurn>> = turns.asStateFlow()
    private val working = MutableStateFlow(false)

    /** True while the model is answering; one question at a time. */
    val busy: StateFlow<Boolean> = working.asStateFlow()
    private val importing = MutableStateFlow<ModelImportUi>(ModelImportUi.Idle)
    val importState: StateFlow<ModelImportUi> = importing.asStateFlow()
    val manifest: ModelManifest? get() = host?.manifest

    /** Each pending change with the agent that proposed it (applying needs Core, not the model). */
    private val changes = mutableMapOf<String, Pair<LocalAgent, ProposedChange>>()
    private var seq = 0
    private fun nextId(kind: String) = "$kind-${++seq}"

    fun send(text: String) {
        val agent = host?.agent() ?: return
        val question = text.trim()
        if (question.isEmpty() || working.value) return
        working.value = true
        turns.update { it + AiTurn.User(nextId("u"), question) }
        viewModelScope.launch(io) {
            try {
                val added: List<AiTurn> = try {
                    when (val r = agent.handle(question)) {
                        is AgentReply.Rejected -> listOf(AiTurn.Refusal(nextId("x"), r.reason))
                        is AgentReply.Planned -> buildList<AiTurn> {
                            add(AiTurn.Assistant(nextId("a"), AiAnswerComposer.compose(question, r), r.plan.clarification))
                            r.reads.forEach { (call, outcome) ->
                                add(
                                    when (outcome) {
                                        is ToolOutcome.Completed -> AiTurn.Tool(nextId("t"), call.tool, call.arguments, ToolStatus.COMPLETED, outcome.facts)
                                        is ToolOutcome.Failed -> AiTurn.Tool(nextId("t"), call.tool, call.arguments, ToolStatus.FAILED, detail = outcome.detail)
                                    },
                                )
                            }
                            r.refused.forEach { (call, why) -> add(AiTurn.Tool(nextId("t"), call.tool, call.arguments, ToolStatus.REJECTED, detail = why)) }
                            r.changes.forEach { c ->
                                val id = nextId("c")
                                changes[id] = agent to c
                                add(
                                    AiTurn.Approval(
                                        id, c.call.tool, c.call.arguments.entries.joinToString(", ") { "${it.key}: ${it.value}" },
                                        c.verdict, c.rules + listOfNotNull(c.detail.takeIf { it.isNotBlank() && it != "ok" }), ApprovalState.PENDING,
                                    ),
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    listOf(AiTurn.Refusal(nextId("x"), e.message ?: e.javaClass.simpleName))
                }
                turns.update { it + added }
            } finally {
                working.value = false
            }
        }
    }

    /** Records the user's answer to a pending preview; an approval goes to Core, which applies and verifies it. */
    fun decide(id: String, approve: Boolean) {
        val pending = turns.value.firstOrNull { it.id == id } as? AiTurn.Approval ?: return
        if (pending.state != ApprovalState.PENDING) return
        setApproval(id) { it.copy(state = if (approve) ApprovalState.APPROVED else ApprovalState.REJECTED) }
        val (agent, change) = changes.remove(id) ?: return
        if (!approve) return
        viewModelScope.launch(io) {
            val r = agent.apply(change)
            setApproval(id) {
                when (r) {
                    is ChangeResult.Applied -> it.copy(state = ApprovalState.APPLIED, result = r.message)
                    is ChangeResult.Failed -> it.copy(state = ApprovalState.FAILED, result = r.detail)
                }
            }
        }
    }

    private fun setApproval(id: String, f: (AiTurn.Approval) -> AiTurn.Approval) {
        turns.update { list -> list.map { t -> if (t is AiTurn.Approval && t.id == id) f(t) else t } }
    }

    private val modelBusy get() = importing.value.let { it is ModelImportUi.Copying || it is ModelImportUi.Downloading }

    @Volatile
    private var cancelRequested = false

    /** Bytes already downloaded by an interrupted download, shown as "resume". */
    fun partialBytes(): Long = host?.partialBytes() ?: 0

    fun meteredNetwork(): Boolean = host?.meteredNetwork() == true

    fun importModel(uri: Uri) {
        val h = host ?: return
        if (modelBusy) return
        val total = h.manifest?.sizeBytes ?: return
        importing.value = ModelImportUi.Copying(0, total)
        viewModelScope.launch(io) {
            val r = h.importModel(uri, throttled(total) { importing.value = ModelImportUi.Copying(it, total) })
            importing.value = r.toUi(total)
            h.refreshAvailability()
        }
    }

    /** Downloads (or resumes) the model; the screen asks first on mobile data. */
    fun downloadModel() {
        val h = host ?: return
        if (modelBusy) return
        val total = h.manifest?.sizeBytes ?: return
        cancelRequested = false
        importing.value = ModelImportUi.Downloading(h.partialBytes(), total)
        viewModelScope.launch(io) {
            val r = h.downloadModel(throttled(total) { importing.value = ModelImportUi.Downloading(it, total) }) { cancelRequested }
            importing.value = r.toUi(total)
            h.refreshAvailability()
        }
    }

    fun cancelDownload() {
        cancelRequested = true
    }

    // About every 4 MiB, so the screen is not flooded while hundreds of MB are hashed.
    private fun throttled(total: Long, emit: (Long) -> Unit): (Long) -> Unit {
        var last = 0L
        return { done ->
            if (done - last >= 4L shl 20 || done == total) {
                last = done
                emit(done)
            }
        }
    }

    private fun ModelImport.toUi(total: Long): ModelImportUi = when (this) {
        ModelImport.Verified -> ModelImportUi.Verified
        is ModelImport.WrongSize -> ModelImportUi.WrongSize(actual)
        is ModelImport.WrongDigest -> ModelImportUi.WrongDigest
        is ModelImport.Failed -> ModelImportUi.Failed(detail)
        is ModelImport.HttpStatus -> ModelImportUi.NotAvailable(code)
        is ModelImport.Interrupted -> ModelImportUi.Paused(done, total, detail)
    }

    fun deleteModel() {
        val h = host ?: return
        viewModelScope.launch(io) {
            h.deleteModel()
            importing.value = ModelImportUi.Idle
            h.refreshAvailability()
        }
    }
}
