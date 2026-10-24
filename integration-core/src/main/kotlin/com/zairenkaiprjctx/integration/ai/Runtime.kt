// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.ai

import com.zairenkaiprjctx.integration.exec.CommandOutcome
import com.zairenkaiprjctx.integration.exec.CommandRunner
import com.zairenkaiprjctx.integration.update.HttpResponse
import java.io.File
import java.io.FileOutputStream
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

    /** The release answered with this HTTP status (404: the model release is not published yet). */
    data class HttpStatus(val code: Int) : ModelImport

    /** The download stopped (cancelled or the connection dropped) at [done] bytes; the next one resumes there. */
    data class Interrupted(val done: Long, val detail: String) : ModelImport
}

/**
 * The model file in app-private storage. It comes from the model release of
 * Zairenkai-Intelligence-Engine ([ModelManifest.releaseUrl], downloaded only when the user asks)
 * or from a file the user picks, and it is kept only if its size and SHA-256 match the manifest.
 * A copy that grows past the expected size is stopped at once.
 */
class ModelStore(private val dir: File, val manifest: ModelManifest) {
    val file: File get() = File(dir, manifest.fileName)
    private val marker: File get() = File(dir, manifest.fileName + ".sha256")
    private val part: File get() = File(dir, manifest.fileName + ".part")

    /** True once an import was verified; the marker avoids re-hashing hundreds of MB on every start. */
    fun installed(): Boolean =
        file.isFile && file.length() == manifest.sizeBytes && runCatching { marker.readText().trim() }.getOrNull() == manifest.sha256

    /** Bytes of an interrupted download kept for resuming (0 when there is none). */
    fun partialBytes(): Long = part.length().takeIf { it in 1 until manifest.sizeBytes } ?: 0

    fun import(input: InputStream, progress: (Long) -> Unit = {}): ModelImport {
        dir.mkdirs()
        part.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        val total = try {
            copy(input, digest, 0, append = false, progress) { false } ?: return ModelImport.WrongSize(manifest.sizeBytes + 1).also { part.delete() }
        } catch (e: IOException) {
            part.delete()
            return ModelImport.Failed(e.message ?: "read failed")
        }
        return finish(total, digest, keepPartial = false)
    }

    /**
     * Downloads the model through [open] (a GET from a byte offset, see `HttpsTransport.get`),
     * resuming an interrupted download when the server honours the range. [cancelled] is polled
     * between blocks; a cancelled or broken download keeps its bytes for the next attempt.
     */
    fun download(open: (offset: Long) -> HttpResponse, progress: (Long) -> Unit = {}, cancelled: () -> Boolean = { false }): ModelImport {
        dir.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        var offset = partialBytes()
        if (offset == 0L) part.delete() else hashExisting(digest)
        val response = try {
            open(offset)
        } catch (e: IOException) {
            return ModelImport.Interrupted(offset, e.message ?: "connection failed")
        }
        var total = offset
        try {
            response.use { r ->
                when {
                    r.status == 206 && offset > 0 -> Unit
                    r.status == 200 -> {
                        // The server sent the whole file: start over.
                        if (offset > 0) digest.reset()
                        offset = 0
                    }
                    else -> return ModelImport.HttpStatus(r.status)
                }
                total = copy(r.body, digest, offset, append = offset > 0, progress, cancelled)
                    ?: return ModelImport.WrongSize(manifest.sizeBytes + 1).also { part.delete() }
            }
        } catch (e: IOException) {
            return ModelImport.Interrupted(partialBytes(), e.message ?: "connection lost")
        }
        if (total < manifest.sizeBytes) return ModelImport.Interrupted(total, if (cancelled()) "cancelled" else "the connection ended early")
        return finish(total, digest, keepPartial = true)
    }

    /** Appends [input] to the partial file; null when it grows past the manifest's size. */
    private fun copy(input: InputStream, digest: MessageDigest, start: Long, append: Boolean, progress: (Long) -> Unit, cancelled: () -> Boolean): Long? {
        var total = start
        FileOutputStream(part, append).use { out ->
            val buf = ByteArray(1 shl 16)
            while (!cancelled()) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > manifest.sizeBytes) return null
                digest.update(buf, 0, n)
                out.write(buf, 0, n)
                progress(total)
            }
        }
        return total
    }

    private fun hashExisting(digest: MessageDigest) {
        part.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
    }

    private fun finish(total: Long, digest: MessageDigest, keepPartial: Boolean): ModelImport {
        if (total != manifest.sizeBytes) {
            if (!keepPartial) part.delete()
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
        part.delete()
    }
}
