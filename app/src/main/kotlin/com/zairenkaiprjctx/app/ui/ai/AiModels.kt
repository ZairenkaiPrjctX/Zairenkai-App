// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui.ai

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One entry of a Zairenkai AI conversation, shaped after the Local-AI contracts in Zairenkai-Sdk
 * (`IntentPlan`, `ToolInvocation`, `ActionPreview`, `UserApproval`): the model only ever produces
 * typed tool requests from a closed registry; anything that changes the device is a preview that
 * Core checks and the user approves.
 */
sealed interface AiTurn {
    val id: String

    data class User(override val id: String, val text: String) : AiTurn

    data class Assistant(override val id: String, val text: String) : AiTurn

    /** A read-only tool the model asked for (`thermal.snapshot`, `fas.status`, ...). */
    data class Tool(override val id: String, val name: String, val arguments: Map<String, String>, val status: ToolStatus) : AiTurn

    /** A device change the model proposed, with Core's dry-run decision, waiting for the user. */
    data class Approval(
        override val id: String,
        val title: String,
        val summary: String,
        val coreVerdict: String?,
        val rules: List<String>,
        val state: ApprovalState,
    ) : AiTurn
}

enum class ToolStatus { RUNNING, COMPLETED, FAILED, REJECTED }

/** APPROVED means the user said yes; only Core's verified result may later say APPLIED. */
enum class ApprovalState { PENDING, APPROVED, REJECTED, APPLIED, FAILED }

/**
 * The conversation. No on-device model runtime is packaged in this app yet (Zairenkai-Intelligence-
 * Engine is a host-only spike, `intelligence.analysis` in the registry), so nothing ever adds a turn
 * here: no answer is invented. The screen and its components are ready for the runtime.
 */
class AiViewModel(initial: List<AiTurn> = emptyList()) : ViewModel() {
    private val turns = MutableStateFlow(initial)
    val transcript: StateFlow<List<AiTurn>> = turns.asStateFlow()

    /** Records the user's answer to a pending preview; execution would go through Core's submit. */
    fun decide(id: String, approve: Boolean) {
        turns.update { list ->
            list.map { t ->
                if (t is AiTurn.Approval && t.id == id && t.state == ApprovalState.PENDING) {
                    t.copy(state = if (approve) ApprovalState.APPROVED else ApprovalState.REJECTED)
                } else {
                    t
                }
            }
        }
    }
}
