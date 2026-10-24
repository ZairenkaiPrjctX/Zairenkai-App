// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.ai.AgentReply
import com.zairenkaiprjctx.integration.ai.AiDevice
import com.zairenkaiprjctx.integration.ai.AiFormatException
import com.zairenkaiprjctx.integration.ai.AiPlanParser
import com.zairenkaiprjctx.integration.ai.AiPrompt
import com.zairenkaiprjctx.integration.ai.AiToolRegistry
import com.zairenkaiprjctx.integration.ai.ChangeResult
import com.zairenkaiprjctx.integration.ai.Fact
import com.zairenkaiprjctx.integration.ai.FactKey
import com.zairenkaiprjctx.integration.ai.IntelligenceSubsystem
import com.zairenkaiprjctx.integration.ai.LocalAgent
import com.zairenkaiprjctx.integration.ai.LocalAiAssets
import com.zairenkaiprjctx.integration.ai.ModelImport
import com.zairenkaiprjctx.integration.ai.ModelManifest
import com.zairenkaiprjctx.integration.ai.ModelStore
import com.zairenkaiprjctx.integration.ai.PlanResult
import com.zairenkaiprjctx.integration.ai.RuntimeCall
import com.zairenkaiprjctx.integration.ai.ToolOutcome
import com.zairenkaiprjctx.integration.ai.ZieRuntime
import com.zairenkaiprjctx.integration.core.ActionResult
import com.zairenkaiprjctx.integration.core.CoreCall
import com.zairenkaiprjctx.integration.core.CoreOperation
import com.zairenkaiprjctx.integration.core.Decision
import com.zairenkaiprjctx.integration.core.FasProfileName
import com.zairenkaiprjctx.integration.exec.CommandOutcome
import com.zairenkaiprjctx.integration.exec.CommandResult
import com.zairenkaiprjctx.integration.exec.CommandRunner
import com.zairenkaiprjctx.integration.exec.ProcessCommandRunner
import com.zairenkaiprjctx.integration.model.AvailabilityState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/** Zairenkai Local AI: registry, prompt, output checks, runtime, model import and the agent. */
class AiTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val registry = AiToolRegistry.parse(contract("intelligence/tools.json"))
    private val cases = Json.parseToJsonElement(contract("intelligence/cases.json")).jsonObject.mapValues { it.value.jsonPrimitive.content }

    @Test
    fun theEnginesRegistryLoads() {
        assertEquals(listOf("core.health", "core.capabilities", "thermal.snapshot", "fas.status", "fas.set_profile"), registry.tools.map { it.id })
        val set = registry.get("fas.set_profile")!!
        assertEquals("system", set.risk)
        assertEquals("fas_set_profile", set.coreOperation)
        assertEquals(FasProfileName.entries.map { it.wire }, set.parameters.getValue("profile"))
        assertEquals(setOf("profile"), set.required)
    }

    @Test
    fun aRegistryWithAnOpenOrFreeTextParameterIsRefused() {
        val open = """[{"id":"x","description":"d","risk":"read_only","parameters":{"type":"object"}}]"""
        assertThrows(AiFormatException::class.java) { AiToolRegistry.parse(open) }
        val text = """[{"id":"x","description":"d","risk":"read_only","parameters":{"type":"object","additionalProperties":false,"properties":{"cmd":{"type":"string"}}}}]"""
        assertThrows(AiFormatException::class.java) { AiToolRegistry.parse(text) }
    }

    @Test
    fun thePromptIsByteForByteTheEnginesPrompt() {
        for ((case, utterance) in cases) {
            assertEquals(case, contract("intelligence/prompt-$case.txt"), AiPrompt.build(registry, utterance))
        }
        // Template tokens in the user text cannot close the user turn.
        val injected = AiPrompt.build(registry, cases.getValue("injection"))
        assertEquals(1, Regex("<\\|im_start\\|>system").findAll(injected).count())
    }

    @Test
    fun theRealModelsAnswersParse() {
        val fas = (AiPlanParser.parse(registry, contract("intelligence/output-fas-performance.json")) as PlanResult.Ok).plan
        assertEquals("fas.set_profile", fas.calls.single().tool)
        assertEquals(mapOf("profile" to "performance"), fas.calls.single().arguments)
        val temp = (AiPlanParser.parse(registry, contract("intelligence/output-temperature-id.json")) as PlanResult.Ok).plan
        assertEquals("id", temp.language)
        assertEquals("thermal.snapshot", temp.calls.single().tool)
        // The 0.5B model asks for no tool here: the app shows that, it does not invent one.
        val health = (AiPlanParser.parse(registry, contract("intelligence/output-core-health.json")) as PlanResult.Ok).plan
        assertTrue(health.calls.isEmpty())
    }

    @Test
    fun outputOutsideTheSchemaYieldsNoAction() {
        fun rejected(raw: String) = AiPlanParser.parse(registry, raw) as? PlanResult.Rejected
        val ok = """{"language":"en","goal":"g","confidence_percent":5,"calls":[],"clarification":""}"""
        assertTrue(AiPlanParser.parse(registry, ok) is PlanResult.Ok)
        assertTrue(rejected("not json") != null)
        assertTrue(rejected(ok.replace("\"clarification\":\"\"", "\"clarification\":\"\",\"shell\":\"rm -rf /\"")) != null)
        assertTrue(rejected(ok.replace("\"en\"", "\"fr\"")) != null)
        assertTrue(rejected(ok.replace("5", "101")) != null)
        assertTrue(rejected(ok.replace("5", "5.5")) != null)
        assertTrue(rejected(ok.replace("\"g\"", "\"" + "x".repeat(201) + "\"")) != null)
        fun call(c: String) = ok.replace("\"calls\":[]", "\"calls\":[$c]")
        assertTrue(rejected(call("""{"tool":"shell.exec","arguments":{}}""")) != null)
        assertTrue(rejected(call("""{"tool":"fas.set_profile","arguments":{"profile":"turbo"}}""")) != null)
        assertTrue(rejected(call("""{"tool":"fas.set_profile","arguments":{}}""")) != null)
        assertTrue(rejected(call("""{"tool":"core.health","arguments":{"path":"/data"}}""")) != null)
        val four = List(4) { """{"tool":"core.health","arguments":{}}""" }.joinToString(",")
        assertTrue(rejected(call(four)) != null)
    }

    private class Runner(private val outcome: CommandOutcome) : CommandRunner {
        val calls = mutableListOf<List<String>>()
        var promptSeen: String? = null

        override fun run(argv: List<String>, timeoutMs: Long): CommandOutcome {
            calls += argv
            argv.indexOf("-f").takeIf { it >= 0 }?.let { promptSeen = File(argv[it + 1]).readText() }
            return outcome
        }
    }

    @Test
    fun theRuntimeRunsAFixedArgvAndRemovesThePrompt() {
        val work = tmp.newFolder("ai")
        val r = Runner(CommandOutcome.Finished(CommandResult(0, "{}", "")))
        val rt = ZieRuntime({ "/lib/libzierun.so" }, r, work, threads = 4)
        val out = rt.generate(File("/m.gguf"), File("/g.gbnf"), "hello; rm -rf /")
        assertEquals(RuntimeCall.Ok("{}"), out)
        val argv = r.calls.single()
        assertEquals(listOf("/lib/libzierun.so", "-m", "/m.gguf", "-g", "/g.gbnf", "-f"), argv.take(6))
        assertEquals(listOf("-n", "160", "-t", "4"), argv.drop(7))
        assertEquals("hello; rm -rf /", r.promptSeen)
        assertTrue("the prompt file is removed", work.listFiles()!!.isEmpty())
        val failed = ZieRuntime({ "/z" }, Runner(CommandOutcome.Finished(CommandResult(3, "", "zie-run: cannot load the model\n"))), work)
            .generate(File("/m"), File("/g"), "p") as RuntimeCall.Unavailable
        assertTrue(failed.reason, failed.reason.startsWith("the model could not be loaded"))
        assertEquals(RuntimeCall.Unavailable(ZieRuntime.NOT_PACKAGED), ZieRuntime({ null }, r, work).version())
    }

    private fun manifestFor(bytes: ByteArray) = ModelManifest.parse(
        contract("intelligence/model.json")
            .replace("7671c0c304e6ce5a7fc577bcb12aba01e2c155cc2efd29b2213c95b18edaf6ed", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            .replace("428730208", bytes.size.toString()),
    )

    @Test
    fun theEnginesManifestLoads() {
        val m = ModelManifest.parse(contract("intelligence/model.json"))
        assertEquals("qwen2.5-0.5b-instruct-q4_0.gguf", m.fileName)
        assertEquals(428730208L, m.sizeBytes)
        assertEquals(1073741824L, m.minMemoryBytes)
        assertEquals("9217f5db79a29953eb74d5343926648285ec7e67", m.revision)
        assertThrows(AiFormatException::class.java) { ModelManifest.parse(contract("intelligence/model.json").replace("qwen2.5-0.5b-instruct-q4_0.gguf", "../x.gguf")) }
    }

    @Test
    fun onlyAFileMatchingTheManifestIsKept() {
        val good = "a GGUF stand-in".toByteArray()
        val store = ModelStore(tmp.newFolder("models"), manifestFor(good))
        assertFalse(store.installed())
        assertEquals(ModelImport.WrongSize(good.size - 1L), store.import(good.copyOf(good.size - 1).inputStream()))
        assertEquals(ModelImport.WrongSize(good.size + 1L), store.import((good + 0).inputStream()))
        val tampered = good.copyOf().also { it[0] = 'A'.code.toByte() }
        assertTrue(store.import(tampered.inputStream()) is ModelImport.WrongDigest)
        assertFalse(store.installed())
        assertEquals(ModelImport.Verified, store.import(good.inputStream()))
        assertTrue(store.installed())
        assertTrue(store.file.readBytes().contentEquals(good))
        store.delete()
        assertFalse(store.installed())
    }

    private class Device(private val verdict: String = "allow", private val coreUp: Boolean = true) : AiDevice {
        val submits = mutableListOf<Pair<CoreOperation, Boolean>>()
        val reads = mutableListOf<String>()

        override fun read(toolId: String): ToolOutcome {
            reads += toolId
            return ToolOutcome.Completed(listOf(Fact(FactKey.STATE, "ready")))
        }

        override fun submit(op: CoreOperation, dryRun: Boolean): CoreCall<ActionResult> {
            submits += op to dryRun
            if (!coreUp) return CoreCall.Unreachable("socket closed")
            return CoreCall.Ok(ActionResult("r1", if (dryRun) "planned" else "applied", Decision(verdict, "user", emptyList(), "1"), message = "ok"))
        }
    }

    private fun agentWith(output: String, device: AiDevice) = LocalAgent(registry, { RuntimeCall.Ok(output) }, device)

    @Test
    fun readOnlyToolsRunAndChangesAreOnlyDryRunsUntilApproved() {
        val device = Device()
        val temp = agentWith(contract("intelligence/output-temperature-id.json"), device).handle(cases.getValue("temperature-id")) as AgentReply.Planned
        assertEquals(listOf("thermal.snapshot"), device.reads)
        assertTrue(temp.changes.isEmpty())

        val agent = agentWith(contract("intelligence/output-fas-performance.json"), device)
        val fas = agent.handle(cases.getValue("fas-performance")) as AgentReply.Planned
        val change = fas.changes.single()
        assertEquals(CoreOperation.FasSetProfile(FasProfileName.PERFORMANCE), change.op)
        assertEquals("allow", change.verdict)
        assertEquals(listOf(change.op to true), device.submits)
        // Only an explicit apply submits for real.
        assertTrue(agent.apply(change) is ChangeResult.Applied)
        assertEquals(change.op to false, device.submits.last())
    }

    @Test
    fun theInjectionAttemptStillNeedsCoreAndTheUser() {
        val device = Device(verdict = "deny")
        val agent = agentWith(contract("intelligence/output-injection.json"), device)
        val r = agent.handle(cases.getValue("injection")) as AgentReply.Planned
        val change = r.changes.single()
        assertEquals("deny", change.verdict)
        assertEquals(ChangeResult.Failed("Core did not allow this change"), agent.apply(change))
        assertTrue("nothing was submitted for real", device.submits.all { it.second })
    }

    @Test
    fun withoutCoreNoChangeCanBeApplied() {
        val device = Device(coreUp = false)
        val agent = agentWith(contract("intelligence/output-fas-performance.json"), device)
        val change = (agent.handle("x") as AgentReply.Planned).changes.single()
        assertNull(change.verdict)
        assertTrue(change.detail, change.detail.contains("not answering"))
        assertTrue(agent.apply(change) is ChangeResult.Failed)
        assertEquals(1, device.submits.size)
    }

    @Test
    fun aRuntimeFailureOrBadOutputIsReportedNotGuessed() {
        val down = LocalAgent(registry, { RuntimeCall.Unavailable("the model could not be loaded") }, Device()).handle("x")
        assertTrue((down as AgentReply.Rejected).reason.contains("could not be loaded"))
        assertTrue(agentWith("{\"calls\":\"rm -rf /\"}", Device()).handle("x") is AgentReply.Rejected)
    }

    @Test
    fun theSubsystemReportsEachMissingPiece() {
        val good = "model".toByteArray()
        val store = ModelStore(tmp.newFolder("m"), manifestFor(good))
        val version = Runner(CommandOutcome.Finished(CommandResult(0, "zie-run 1 (llama.cpp b11500)\n", "")))
        fun state(rt: ZieRuntime, mem: Long? = 8L shl 30) =
            IntelligenceSubsystem(rt, { Result.success(LocalAiAssets(registry, store)) }, { mem }, "af31fd7").probe().single().availability
        val work = tmp.newFolder("w")
        assertEquals(AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE, state(ZieRuntime({ null }, version, work)).state)
        val rt = ZieRuntime({ "/z" }, version, work)
        assertEquals(AvailabilityState.UNSUPPORTED_DEVICE, state(rt, mem = 512L shl 20).state)
        assertEquals(AvailabilityState.COMPONENT_NOT_INSTALLED, state(rt).state)
        store.import(good.inputStream())
        val ready = state(rt)
        assertEquals(AvailabilityState.NOT_VERIFIED, ready.state)
        assertTrue(ready.usable)
        val broken = IntelligenceSubsystem(rt, { Result.failure(AiFormatException("tools.json is not a JSON array")) }, { null }, null).probe().single()
        assertEquals(AvailabilityState.INITIALIZATION_FAILED, broken.availability.state)
    }

    /**
     * The real runner and model on this host, when ZIE_RUN and ZIE_MODEL name them (as in
     * Zairenkai-Intelligence-Engine's own real-model test); skipped otherwise.
     */
    @Test
    fun theRealRunnerAnswersThroughTheAgent() {
        val bin = System.getenv("ZIE_RUN")
        val model = System.getenv("ZIE_MODEL")
        assumeTrue("ZIE_RUN and ZIE_MODEL not set", bin != null && model != null)
        val grammar = tmp.newFile("output.gbnf").apply { writeText(contract("intelligence/output.gbnf")) }
        val rt = ZieRuntime({ bin }, ProcessCommandRunner(), tmp.newFolder("rt"))
        val agent = LocalAgent(registry, { rt.generate(File(model!!), grammar, it) }, Device())
        val r = agent.handle(cases.getValue("fas-performance"))
        assertTrue(r.toString(), r is AgentReply.Planned)
        assertEquals("fas.set_profile", (r as AgentReply.Planned).plan.calls.single().tool)
    }
}
