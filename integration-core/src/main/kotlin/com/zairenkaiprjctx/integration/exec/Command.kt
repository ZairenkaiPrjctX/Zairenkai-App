// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.exec

/** Output of a finished process. A non-zero exit is data, not an exception. */
data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String)

sealed interface CommandOutcome {
    data class Finished(val result: CommandResult) : CommandOutcome
    data class SpawnFailed(val detail: String) : CommandOutcome
    data object TimedOut : CommandOutcome
    /** Stdout exceeded the cap; the output is discarded rather than parsed truncated. */
    data object OutputTooLarge : CommandOutcome
}

/**
 * Runs a fixed argv. There is no shell here: callers pass every argument separately.
 * The only way a shell is involved is [RootShell], which builds its script from validated words.
 * The production implementation is the Java class [ProcessCommandRunner].
 */
fun interface CommandRunner {
    fun run(argv: List<String>, timeoutMs: Long): CommandOutcome
}
