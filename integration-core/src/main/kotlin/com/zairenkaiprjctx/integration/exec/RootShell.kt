package com.zairenkaiprjctx.integration.exec

/**
 * One word of a root shell script. Words are either constants defined in this code base
 * ([literal]) or values that passed validation ([arg], [json]). Every word is rendered
 * single-quoted, and no word may contain a single quote, so the shell never interprets a value.
 */
class ShellWord private constructor(val text: String) {
    companion object {
        private val ARG = Regex("^[A-Za-z0-9._:/=@+~,-]{1,512}$")
        private val JSON_CHARS = Regex("^[A-Za-z0-9 _.:,{}\\[\\]\"-]{1,4096}$")

        /** A constant from this code base (paths, sub-commands). Still checked, as a guard. */
        internal fun literal(text: String): ShellWord {
            require(ARG.matches(text)) { "literal $text contains characters outside the allowed set" }
            return ShellWord(text)
        }

        /** A value derived from input: package names, enum tokens, paths reported by Android. */
        @JvmStatic
        fun arg(text: String): ShellWord? = if (ARG.matches(text)) ShellWord(text) else null

        /** A JSON document produced by our own serializer from typed values. */
        @JvmStatic
        fun json(text: String): ShellWord? = if (JSON_CHARS.matches(text)) ShellWord(text) else null
    }

    override fun toString(): String = text
}

class ShellScript(val words: List<ShellWord>) {
    init {
        require(words.isNotEmpty()) { "empty script" }
    }

    /** The `su -c` argument: every word single-quoted, separated by spaces. */
    fun render(): String = words.joinToString(" ") { "'${it.text}'" }
}

enum class RootState {
    /** The user has not asked for root integration; no su call is ever made. */
    NOT_REQUESTED,

    /** `su -c id` returned uid 0 after the user asked for it. */
    GRANTED,

    /** su was found, but the request was refused or did not return uid 0. */
    DENIED,

    /** No su binary was found in the standard locations. */
    UNAVAILABLE,
}

interface RootConsentStore {
    var rootGranted: Boolean
}

sealed interface RootOutcome {
    data class Finished(val result: CommandResult) : RootOutcome
    data class NotGranted(val state: RootState) : RootOutcome
    data class Failed(val detail: String) : RootOutcome
}

/**
 * The only path to root. It refuses to run anything until the user has explicitly enabled root
 * integration and `su` has confirmed uid 0. Scripts are [ShellScript]s, never free text.
 */
class RootShell(
    private val runner: CommandRunner,
    private val consent: RootConsentStore,
    /** Optional fast check; KernelSU's su may not exist as a file, so absence is also detected at spawn. */
    private val suPresent: () -> Boolean = { true },
) {
    @Volatile
    var state: RootState = when {
        !consent.rootGranted -> RootState.NOT_REQUESTED
        else -> RootState.GRANTED
    }
        private set

    /** Called only from an explicit user action. Shows the root manager's prompt. */
    fun request(): RootState {
        if (!suPresent()) {
            state = RootState.UNAVAILABLE
            consent.rootGranted = false
            return state
        }
        val outcome = runner.run(listOf("su", "-c", "id"), 30_000)
        if (outcome is CommandOutcome.SpawnFailed) {
            state = RootState.UNAVAILABLE
            consent.rootGranted = false
            return state
        }
        val ok = outcome is CommandOutcome.Finished &&
            outcome.result.exitCode == 0 &&
            outcome.result.stdout.trim().startsWith("uid=0(")
        state = if (ok) RootState.GRANTED else RootState.DENIED
        consent.rootGranted = ok
        return state
    }

    fun revoke() {
        consent.rootGranted = false
        state = RootState.NOT_REQUESTED
    }

    fun run(script: ShellScript, timeoutMs: Long): RootOutcome {
        if (state != RootState.GRANTED) return RootOutcome.NotGranted(state)
        return when (val o = runner.run(listOf("su", "-c", script.render()), timeoutMs)) {
            is CommandOutcome.Finished -> RootOutcome.Finished(o.result)
            is CommandOutcome.SpawnFailed -> RootOutcome.Failed("su could not be started: ${o.detail}")
            CommandOutcome.TimedOut -> RootOutcome.Failed("timed out after ${timeoutMs / 1000} s")
            CommandOutcome.OutputTooLarge -> RootOutcome.Failed("output too large")
        }
    }

    companion object {
        /** Exit status of `sh` when the command does not exist. */
        const val EXIT_NOT_FOUND = 127
    }
}
