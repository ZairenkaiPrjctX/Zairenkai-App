// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.ai

import com.zairenkaiprjctx.integration.exec.CommandOutcome
import com.zairenkaiprjctx.integration.exec.CommandRunner
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

sealed interface RuntimeCall<out T> {
    data class Ok<T>(val value: T) : RuntimeCall<T>
    data class Unavailable(val reason: String) : RuntimeCall<Nothing>
}

/**
 * zie-run, the engine's on-device runner (`runtime/zie_run.cpp`), packaged as `libzierun.so` and
 * run as the app's own uid, never through root. It links no networking function (its build
 * refuses one), so inference cannot reach the network whatever the model emits. Arguments are
 * fixed: the model, the grammar and a prompt file this class writes.
 */
class ZieRuntime(
    private val binary: () -> String?,
    private val runner: CommandRunner,
    private val workDir: File,
    private val threads: Int = 4,
) {
    fun version(): RuntimeCall<String> {
        val bin = binary() ?: return RuntimeCall.Unavailable(NOT_PACKAGED)
        return when (val r = runner.run(listOf(bin, "--version"), 10_000)) {
            is CommandOutcome.Finished ->
                if (r.result.exitCode == 0 && r.result.stdout.startsWith("zie-run ")) RuntimeCall.Ok(r.result.stdout.trim())
                else RuntimeCall.Unavailable("zie-run --version exited ${r.result.exitCode}: ${r.result.stderr.trim().take(200)}")
            is CommandOutcome.SpawnFailed -> RuntimeCall.Unavailable("zie-run cannot start: ${r.detail}")
            CommandOutcome.TimedOut -> RuntimeCall.Unavailable("zie-run --version timed out")
            CommandOutcome.OutputTooLarge -> RuntimeCall.Unavailable("zie-run --version printed too much")
        }
    }

    /** One generation under [grammar]; the prompt goes through a private file, never the argv. */
    fun generate(model: File, grammar: File, prompt: String, maxTokens: Int = MAX_TOKENS, timeoutMs: Long = 180_000): RuntimeCall<String> {
        val bin = binary() ?: return RuntimeCall.Unavailable(NOT_PACKAGED)
        val promptFile = try {
            workDir.mkdirs()
            File.createTempFile("prompt-", ".txt", workDir).apply {
                setReadable(false, false); setReadable(true, true)
                writeText(prompt)
            }
        } catch (e: IOException) {
            return RuntimeCall.Unavailable("cannot write the prompt: ${e.message}")
        }
        try {
            val argv = listOf(
                bin, "-m", model.absolutePath, "-g", grammar.absolutePath, "-f", promptFile.absolutePath,
                "-n", maxTokens.coerceIn(1, 1024).toString(), "-t", threads.coerceIn(1, 16).toString(),
            )
            return when (val r = runner.run(argv, timeoutMs)) {
                is CommandOutcome.Finished -> when (r.result.exitCode) {
                    0 -> RuntimeCall.Ok(r.result.stdout)
                    else -> RuntimeCall.Unavailable(exitReason(r.result.exitCode) + stderrTail(r.result.stderr))
                }
                is CommandOutcome.SpawnFailed -> RuntimeCall.Unavailable("zie-run cannot start: ${r.detail}")
                CommandOutcome.TimedOut -> RuntimeCall.Unavailable("the model did not answer within ${timeoutMs / 1000} s")
                CommandOutcome.OutputTooLarge -> RuntimeCall.Unavailable("the model's answer was too long")
            }
        } finally {
            promptFile.delete()
        }
    }

    companion object {
        const val MAX_TOKENS = 160
        const val NOT_PACKAGED = "zie-run is not packaged for this device's ABI (built for arm64-v8a and x86_64)"

        /** zie-run's exit statuses (`runtime/zie_run.cpp`). */
        fun exitReason(code: Int): String = when (code) {
            2 -> "zie-run rejected its arguments"
            3 -> "the model could not be loaded (memory, or a damaged file)"
            4 -> "the grammar could not be read or parsed"
            5 -> "the prompt could not be read or does not fit the context"
            6 -> "decoding failed"
            else -> "zie-run exited $code"
        }

        private fun stderrTail(s: String): String = s.trim().lineSequence().lastOrNull()?.takeIf { it.isNotBlank() }?.let { ": ${it.take(200)}" } ?: ""
    }
}

sealed interface ModelImport {
    data object Verified : ModelImport
    data class WrongSize(val actual: Long) : ModelImport
    data class WrongDigest(val actual: String) : ModelImport
    data class Failed(val detail: String) : ModelImport
}

/**
 * The model file in app-private storage. The app never downloads it: the user picks the GGUF
 * file the manifest names (from the source and revision it gives), and it is kept only if its
 * size and SHA-256 match the manifest. A copy that grows past the expected size is stopped at once.
 */
class ModelStore(private val dir: File, val manifest: ModelManifest) {
    val file: File get() = File(dir, manifest.fileName)
    private val marker: File get() = File(dir, manifest.fileName + ".sha256")

    /** True once an import was verified; the marker avoids re-hashing hundreds of MB on every start. */
    fun installed(): Boolean =
        file.isFile && file.length() == manifest.sizeBytes && runCatching { marker.readText().trim() }.getOrNull() == manifest.sha256

    fun import(input: InputStream, progress: (Long) -> Unit = {}): ModelImport {
        dir.mkdirs()
        val part = File(dir, manifest.fileName + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            part.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > manifest.sizeBytes) {
                        part.delete()
                        return ModelImport.WrongSize(total)
                    }
                    digest.update(buf, 0, n)
                    out.write(buf, 0, n)
                    progress(total)
                }
            }
        } catch (e: IOException) {
            part.delete()
            return ModelImport.Failed(e.message ?: "read failed")
        }
        if (total != manifest.sizeBytes) {
            part.delete()
            return ModelImport.WrongSize(total)
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (hex != manifest.sha256) {
            part.delete()
            return ModelImport.WrongDigest(hex)
        }
        marker.delete()
        if (!part.renameTo(file)) {
            part.delete()
            return ModelImport.Failed("cannot move the verified file into place")
        }
        marker.writeText(hex + "\n")
        return ModelImport.Verified
    }

    fun delete() {
        marker.delete()
        file.delete()
        File(dir, manifest.fileName + ".part").delete()
    }
}
