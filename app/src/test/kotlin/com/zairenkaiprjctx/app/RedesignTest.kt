// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import android.os.PowerManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.zairenkaiprjctx.app.design.ConnectedChoice
import com.zairenkaiprjctx.app.design.ThemeMode
import com.zairenkaiprjctx.app.design.ThemeSettings
import com.zairenkaiprjctx.app.design.ZairenkaiTheme
import com.zairenkaiprjctx.app.ui.CapabilityGroup
import com.zairenkaiprjctx.app.ui.NextStep
import com.zairenkaiprjctx.app.ui.ai.AI_CAPABILITY
import com.zairenkaiprjctx.app.ui.ai.AiTurn
import com.zairenkaiprjctx.app.ui.ai.AiViewModel
import com.zairenkaiprjctx.app.ui.ai.ApprovalCard
import com.zairenkaiprjctx.app.ui.ai.ApprovalState
import com.zairenkaiprjctx.app.ui.group
import com.zairenkaiprjctx.app.ui.nextSteps
import com.zairenkaiprjctx.integration.exec.RootState
import com.zairenkaiprjctx.integration.model.Availability
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.model.Privilege
import com.zairenkaiprjctx.integration.model.Provider
import com.zairenkaiprjctx.integration.registry.CapabilityRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun record(state: AvailabilityState) =
    CapabilityRecord("x", "X", "", Provider("ZairenkaiPrjctX/Zairenkai-App", null, "test"), Privilege.STANDARD, Availability(state, "why"))

/** Home's suggestions and grouping, the AI conversation model, and the AI screen in the app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ZairenkaiApplication::class)
class RedesignTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ZairenkaiApplication>().container

    @Test
    fun homeSuggestsRootOnlyWhenSomethingWaitsForItAndSuMayExist() {
        val waiting = listOf(record(AvailabilityState.AVAILABLE), record(AvailabilityState.ROOT_REQUIRED))
        assertEquals(listOf(NextStep.ENABLE_ROOT), nextSteps(waiting, RootState.NOT_REQUESTED, null))
        assertEquals(listOf(NextStep.ENABLE_ROOT), nextSteps(waiting, RootState.DENIED, null))
        assertEquals(emptyList<NextStep>(), nextSteps(waiting, RootState.GRANTED, null))
        assertEquals(emptyList<NextStep>(), nextSteps(waiting, RootState.UNAVAILABLE, null))
        assertEquals(emptyList<NextStep>(), nextSteps(listOf(record(AvailabilityState.AVAILABLE)), RootState.NOT_REQUESTED, null))
    }

    @Test
    fun homeSuggestsCoolingDownFromSevereThrottlingOnly() {
        val none = emptyList<CapabilityRecord>()
        assertEquals(emptyList<NextStep>(), nextSteps(none, RootState.GRANTED, PowerManager.THERMAL_STATUS_MODERATE))
        assertEquals(listOf(NextStep.COOL_DOWN), nextSteps(none, RootState.GRANTED, PowerManager.THERMAL_STATUS_SEVERE))
        assertEquals(listOf(NextStep.COOL_DOWN), nextSteps(none, RootState.GRANTED, PowerManager.THERMAL_STATUS_SHUTDOWN))
        assertEquals(emptyList<NextStep>(), nextSteps(none, RootState.GRANTED, null))
    }

    @Test
    fun capabilityGroupsKeepNotVerifiedUsableAndUnfixableApart() {
        assertEquals(CapabilityGroup.READY, record(AvailabilityState.AVAILABLE).group())
        assertEquals(CapabilityGroup.READY, record(AvailabilityState.NOT_VERIFIED).group())
        assertEquals(CapabilityGroup.ATTENTION, record(AvailabilityState.ROOT_REQUIRED).group())
        assertEquals(CapabilityGroup.ATTENTION, record(AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE).group())
        assertEquals(CapabilityGroup.NOT_HERE, record(AvailabilityState.UNSUPPORTED_DEVICE).group())
        assertEquals(CapabilityGroup.NOT_HERE, record(AvailabilityState.NO_UPSTREAM_IMPLEMENTATION).group())
    }

    @Test
    fun aiTranscriptIsEmptyAndADecisionOnlyAnswersAPendingPreview() {
        assertTrue(AiViewModel().transcript.value.isEmpty())
        val pending = AiTurn.Approval("p", "t", "s", "allow", emptyList(), ApprovalState.PENDING)
        val applied = AiTurn.Approval("q", "t", "s", "allow", emptyList(), ApprovalState.APPLIED)
        val vm = AiViewModel(listOf(pending, applied))
        vm.decide("p", approve = true)
        vm.decide("q", approve = false)
        assertEquals(listOf(ApprovalState.APPROVED, ApprovalState.APPLIED), vm.transcript.value.map { (it as AiTurn.Approval).state })
        vm.decide("p", approve = false) // already answered: no change
        assertEquals(ApprovalState.APPROVED, (vm.transcript.value[0] as AiTurn.Approval).state)
    }

    @Test
    fun approvalNeedsCoreToAllowBeforeTheUserCanApprove() {
        val answers = mutableListOf<Pair<String, Boolean>>()
        compose.setContent {
            ZairenkaiTheme(ThemeSettings(ThemeMode.LIGHT, dynamicColor = false)) {
                androidx.compose.foundation.layout.Column {
                    ApprovalCard(AiTurn.Approval("denied", "Denied change", "s", "deny", emptyList(), ApprovalState.PENDING)) { id, ok -> answers += id to ok }
                    ApprovalCard(AiTurn.Approval("allowed", "Allowed change", "s", "allow", emptyList(), ApprovalState.PENDING)) { id, ok -> answers += id to ok }
                }
            }
        }
        val approve = compose.onAllNodesWithText("Approve")
        approve[0].assertIsNotEnabled()
        // The second card is below the fold of the 470 dp test screen: invoke the click actions.
        approve[1].assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick)
        compose.onAllNodesWithText("Reject")[0].performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("allowed" to true, "denied" to false), answers)
    }

    @Test
    fun aiScreenIsHonestAboutTheMissingRunnerAndItsPromptsOpenTheAnsweringScreen() {
        val c = container
        compose.setContent { ZairenkaiRoot(c, activity = null) }
        compose.waitUntil(10_000) { c.registry.snapshot.value.records.isNotEmpty() }
        compose.onNodeWithTag("nav-ai").performClick()
        compose.waitForIdle()
        // Robolectric extracts no native libraries, so zie-run is absent here: the probe says so.
        assertEquals(AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE, c.registry.snapshot.value.availability(AI_CAPABILITY).state)
        compose.onNodeWithText("Native component unavailable").assertExists() // above the fold on this 470 dp screen
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Until the on-device model is available", substring = true))
        compose.onNodeWithText("Until the on-device model is available, each question opens the screen that answers it.").assertExists()
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Why is my phone hot?"))
        compose.onNodeWithText("Why is my phone hot?").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("nav-thermal").assertIsSelected()
        compose.onNodeWithText("PowerManager thermal status listener", substring = true).assertIsDisplayed()
    }
}

/**
 * Real touches on a connected button group. Its first and last segments have path outlines,
 * which LEGACY graphics cannot hit-test, so this needs NATIVE graphics.
 */
@RunWith(NativeGraphicsTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectedChoiceTouchTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everySegmentTakesATouchAndTheChoiceIsAnnouncedAsARadioButton() {
        var selected by androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent {
            ZairenkaiTheme(ThemeSettings(ThemeMode.LIGHT, dynamicColor = false)) {
                ConnectedChoice(listOf("System", "Light", "Dark"), selected, { selected = it })
            }
        }
        for ((i, label) in listOf("Dark", "Light", "System").withIndex()) {
            compose.onNodeWithText(label).performClick()
            compose.waitForIdle()
            assertEquals(label, 2 - i, selected)
        }
        compose.onNodeWithText("System")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        compose.onAllNodesWithText("Dark")[0].assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    }
}
