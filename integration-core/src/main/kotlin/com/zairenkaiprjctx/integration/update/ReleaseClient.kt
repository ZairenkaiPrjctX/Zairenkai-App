// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.update

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/** An HTTP GET with its final URL (after redirects), status, length and body. */
class HttpResponse(val finalUrl: String, val status: Int, val length: Long, val body: InputStream) : AutoCloseable {
    override fun close() = body.close()
}

/** Opens a GET request. The app uses [HttpsTransport]; tests use a fake. */
fun interface Transport {
    @Throws(IOException::class)
    fun get(url: String, accept: String): HttpResponse
}

/**
 * GET over HTTPS only, following GitHub's redirects to its asset storage. No cookies, no
 * credentials, no query beyond the URL: GitHub sees the app's IP address and a User-Agent with
 * the app version, nothing else.
 */
class HttpsTransport(private val userAgent: String) : Transport {
    override fun get(url: String, accept: String): HttpResponse {
        var target = url
        repeat(5) {
            ReleaseClient.requireAllowed(target)
            val c = URI(target).toURL().openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("Accept", accept)
            c.setRequestProperty("User-Agent", userAgent)
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            val code = c.responseCode
            if (code in 300..399) {
                val next = c.getHeaderField("Location") ?: throw IOException("redirect without a location")
                c.disconnect()
                target = URI(target).resolve(next).toString()
                return@repeat
            }
            val body = if (code in 200..299) c.inputStream else (c.errorStream ?: InputStream.nullInputStream())
            return HttpResponse(target, code, c.contentLengthLong, body)
        }
        throw IOException("too many redirects")
    }
}

/** A download that finished and matched its published SHA-256. */
data class DownloadedApk(val release: AppRelease, val file: File, val sha256: String)

class UpdateException(message: String) : Exception(message)

/**
 * Reads the release feed and downloads a release's APK. Every URL must be HTTPS on GitHub's own
 * hosts; the APK is written to a temporary file, its size bounded by the release record (and an
 * absolute cap), and kept only when its SHA-256 equals the release's `.sha256` file.
 */
class ReleaseClient(private val transport: Transport) {
    fun releases(): ReleaseFeedResult {
        transport.get(ReleaseSource.API, "application/vnd.github+json").use { r ->
            if (r.status == 403 || r.status == 429) throw UpdateException("GitHub asked to wait before checking again (HTTP ${r.status})")
            if (r.status != 200) throw UpdateException("GitHub answered HTTP ${r.status}")
            val text = r.body.readNBytes(MAX_FEED + 1)
            if (text.size > MAX_FEED) throw UpdateException("release list is too large")
            return try {
                ReleaseFeed.parse(text.decodeToString())
            } catch (e: Exception) {
                throw UpdateException("release list is not readable: ${e.message}")
            }
        }
    }

    /** The newest release on [channel] newer than [current], or null when up to date. */
    fun check(channel: UpdateChannel, current: AppVersion): AppRelease? = ReleaseFeed.newest(releases().releases, channel, current)

    /** Downloads and checks [release]'s APK into [dir]; [progress] gets (bytes, total). */
    fun download(release: AppRelease, dir: File, progress: (Long, Long) -> Unit = { _, _ -> }): DownloadedApk {
        requireAllowed(release.checksum.url)
        requireAllowed(release.apk.url)
        val expected = transport.get(release.checksum.url, "application/octet-stream").use { r ->
            if (r.status != 200) throw UpdateException("checksum file: HTTP ${r.status}")
            parseChecksumFile(r.body.readNBytes(4096).decodeToString(), release.apk.name)
                ?: throw UpdateException("${release.checksum.name} names no SHA-256 for ${release.apk.name}")
        }
        dir.mkdirs()
        val part = File(dir, release.apk.name + ".part")
        val out = File(dir, release.apk.name)
        part.delete()
        val md = MessageDigest.getInstance("SHA-256")
        val limit = if (release.apk.size > 0) release.apk.size else MAX_APK
        if (limit > MAX_APK) throw UpdateException("the APK is larger than ${MAX_APK / (1024 * 1024)} MB")
        var total = 0L
        transport.get(release.apk.url, "application/octet-stream").use { r ->
            if (r.status != 200) throw UpdateException("APK: HTTP ${r.status}")
            part.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = r.body.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > limit) throw UpdateException("the APK is larger than the release says")
                    md.update(buf, 0, n)
                    o.write(buf, 0, n)
                    progress(total, limit)
                }
            }
        }
        if (release.apk.size > 0 && total != release.apk.size) {
            part.delete()
            throw UpdateException("download ended at $total of ${release.apk.size} bytes")
        }
        val digest = md.digest().joinToString("") { "%02x".format(it) }
        if (digest != expected) {
            part.delete()
            throw UpdateException("SHA-256 $digest does not match the release's $expected")
        }
        out.delete()
        if (!part.renameTo(out)) throw UpdateException("cannot keep the downloaded file")
        return DownloadedApk(release, out, digest)
    }

    companion object {
        const val MAX_FEED = 2 * 1024 * 1024
        const val MAX_APK = 300L * 1024 * 1024

        /** GitHub's API, web and release-asset hosts; nothing else is ever contacted. */
        val ALLOWED_HOSTS = setOf(
            "api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com",
        )

        fun requireAllowed(url: String) {
            val u = runCatching { URI(url) }.getOrNull() ?: throw UpdateException("not a URL: $url")
            if (u.scheme != "https") throw UpdateException("refusing a non-HTTPS URL: $url")
            if (u.host !in ALLOWED_HOSTS) throw UpdateException("refusing host ${u.host}")
        }
    }
}
