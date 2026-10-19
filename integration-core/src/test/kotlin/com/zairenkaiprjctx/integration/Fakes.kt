package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.exec.CommandOutcome
import com.zairenkaiprjctx.integration.exec.CommandResult
import com.zairenkaiprjctx.integration.exec.CommandRunner
import com.zairenkaiprjctx.integration.exec.RootConsentStore
import com.zairenkaiprjctx.integration.exec.RootShell
import java.io.File

/** Test double: canned outcomes keyed by the exact argv. Unknown argv = spawn failure. */
class FakeRunner : CommandRunner {
    private val outcomes = mutableMapOf<List<String>, CommandOutcome>()
    val calls = mutableListOf<List<String>>()

    fun on(argv: List<String>, exit: Int, stdout: String = "", stderr: String = "") = apply {
        outcomes[argv] = CommandOutcome.Finished(CommandResult(exit, stdout, stderr))
    }

    fun on(argv: List<String>, outcome: CommandOutcome) = apply { outcomes[argv] = outcome }

    override fun run(argv: List<String>, timeoutMs: Long): CommandOutcome {
        calls += argv
        return outcomes[argv] ?: CommandOutcome.SpawnFailed("no such program in test: ${argv.first()}")
    }
}

class MemoryConsent(override var rootGranted: Boolean = false) : RootConsentStore

fun su(script: String) = listOf("su", "-c", script)

/** A RootShell that has been granted through the same path the app uses (`su -c id`). */
fun grantedRoot(runner: FakeRunner): RootShell {
    runner.on(su("id"), 0, "uid=0(root) gid=0(root) context=u:r:magisk:s0\n")
    return RootShell(runner, MemoryConsent(), suPresent = { true }).also { it.request() }
}

/** Contract fixtures captured from the real binaries (see integration/scripts/capture-contracts.sh). */
fun contract(path: String): String =
    requireNotNull(object {}.javaClass.getResource("/contracts/$path")) { "missing contract fixture $path" }.readText()

fun fixture(path: String): String =
    requireNotNull(object {}.javaClass.getResource("/fixtures/$path")) { "missing fixture $path" }.readText()

/** Files in the repository's integration/ directory (inventory and lock). */
fun repoFile(path: String): String = File("../$path").readText()
