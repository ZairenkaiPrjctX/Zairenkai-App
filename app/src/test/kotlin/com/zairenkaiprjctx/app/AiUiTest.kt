// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zairenkaiprjctx.app.design.ThemeMode
import com.zairenkaiprjctx.app.design.ThemeSettings
import com.zairenkaiprjctx.app.design.ZairenkaiTheme
import com.zairenkaiprjctx.app.ui.ai.AI_CAPABILITY
import com.zairenkaiprjctx.app.ui.ai.AiScreen
import com.zairenkaiprjctx.app.ui.ai.AiTurn
import com.zairenkaiprjctx.app.ui.ai.AiViewModel
import com.zairenkaiprjctx.app.ui.ai.ApprovalState
import com.zairenkaiprjctx.app.ui.ai.ModelImportUi
import android.net.Uri
import com.zairenkaiprjctx.integration.ai.ModelImport
import com.zairenkaiprjctx.integration.core.CoreOperation
import com.zairenkaiprjctx.integration.model.Availability
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.model.Privilege
import com.zairenkaiprjctx.integration.model.Provider
import com.zairenkaiprjctx.integration.registry.CapabilityRecord
import com.zairenkaiprjctx.integration.registry.RegistrySnapshot
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The AI screen over the real agent code with the engine's registry and manifest. The runtime
 * answers with zie-run's captured outputs (integration-core `contracts/intelligence/`); the
 * device is a stand-in that records what reached Core.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ZairenkaiApplication::class)
class AiUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<ZairenkaiApplication>()

    private fun host(answer: String) = CapturedAiHost({ answer })

    private fun snapshot(state: AvailabilityState) = RegistrySnapshot(
        listOf(CapabilityRecord(AI_CAPABILITY, "Zairenkai Local AI", "", Provider("ZairenkaiPrjctX/Zairenkai-Intelligence-Engine", null, "zie-run"), Privilege.STANDARD, Availability(state, "probe reason"))),
        refreshing = false, refreshedAtMs = 0,
    )

    private fun show(vm: AiViewModel, state: AvailabilityState) = compose.setContent {
        ZairenkaiTheme(ThemeSettings(ThemeMode.LIGHT, dynamicColor = false)) { AiScreen(vm, snapshot(state), PaddingValues(0.dp)) {} }
    }

    @Test
    fun aQuestionBecomesACoreDryRunThatOnlyTheUserCanApply() {
        val host = host(CapturedAnswers.FAS)
        val vm = AiViewModel(host = host, io = Dispatchers.Unconfined)
        show(vm, AvailabilityState.NOT_VERIFIED)
        compose.onNodeWithText("Ask Zairenkai…").performTextInput("Switch FAS to the performance profile")
        compose.onNodeWithContentDescription("Send").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val turns = vm.transcript.value
        assertEquals(listOf("User", "Assistant", "Approval"), turns.map { it::class.simpleName })
        val approval = turns.last() as AiTurn.Approval
        assertEquals("fas.set_profile", approval.title)
        assertEquals("profile: performance", approval.summary)
        assertEquals(listOf(CoreOperation.FasSetProfile(com.zairenkaiprjctx.integration.core.FasProfileName.PERFORMANCE) to true), host.device.submits)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Needs your approval"))
        compose.onNodeWithText("Approve").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(ApprovalState.APPLIED, (vm.transcript.value.last() as AiTurn.Approval).state)
        assertEquals(false, host.device.submits.last().second)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Applied and verified by Core."))
    }

    @Test
    fun aReadOnlyToolShowsWhatItFound() {
        val vm = AiViewModel(host = host(CapturedAnswers.TEMPERATURE), io = Dispatchers.Unconfined)
        show(vm, AvailabilityState.NOT_VERIFIED)
        vm.send("Kenapa HP saya panas?")
        compose.waitForIdle()
        val tool = vm.transcript.value.filterIsInstance<AiTurn.Tool>().single()
        assertEquals("thermal.snapshot", tool.name)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Hottest sensor"))
        compose.onNodeWithText("41.0 °C (cpu-0)").assertExists()
    }

    @Test
    fun withoutAModelNothingIsAskedAndAWrongFileIsRefused() {
        val host = host(CapturedAnswers.FAS)
        val vm = AiViewModel(host = host, io = Dispatchers.Unconfined)
        show(vm, AvailabilityState.MODEL_NOT_IMPORTED)
        compose.onNodeWithContentDescription("Send").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Import model file"))
        compose.onNodeWithText("qwen2.5-0.5b-instruct-q4_0.gguf", substring = true).assertExists()
        vm.importModel(Uri.parse("content://test/model.gguf"))
        compose.waitForIdle()
        assertEquals(1, host.imported)
        assertEquals(ModelImportUi.WrongDigest, vm.importState.value)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Not this model", substring = true))
        assertTrue(vm.transcript.value.isEmpty())
    }

    @Test
    fun theModelIsDownloadedOnlyOnRequestAndAMissingReleaseIsSaid() {
        val host = host(CapturedAnswers.FAS)
        val vm = AiViewModel(host = host, io = Dispatchers.Unconfined)
        show(vm, AvailabilityState.MODEL_NOT_IMPORTED)
        assertEquals("nothing is downloaded until the user asks", 0, host.downloads)
        // First-run setup must be visible near the AI status, not buried below the suggestion grid.
        compose.onNodeWithText("Download model", substring = true).assertExists()
        compose.onAllNodesWithText("Download model", substring = true).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(1, host.downloads)
        assertEquals(ModelImportUi.NotAvailable(404), vm.importState.value)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("not published on GitHub yet", substring = true))
    }

    @Test
    fun onMobileDataTheDownloadWaitsForAYes() {
        val host = host(CapturedAnswers.FAS).apply { metered = true; downloadResult = ModelImport.Verified }
        val vm = AiViewModel(host = host, io = Dispatchers.Unconfined)
        show(vm, AvailabilityState.MODEL_NOT_IMPORTED)
        // First-run setup must be visible near the AI status, not buried below the suggestion grid.
        compose.onNodeWithText("Download model", substring = true).assertExists()
        compose.onAllNodesWithText("Download model", substring = true).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Download over mobile data?").assertExists()
        assertEquals(0, host.downloads)
        compose.onNodeWithText("Download").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(1, host.downloads)
        assertEquals(ModelImportUi.Verified, vm.importState.value)
    }
}
