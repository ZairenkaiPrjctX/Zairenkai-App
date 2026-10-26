// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where the app's releases are published (GitHub Releases of the app's public repository). */
object ReleaseSource {
    const val REPOSITORY = "ZairenkaiPrjctX/Zairenkai-App"
    const val API = "https://api.github.com/repos/$REPOSITORY/releases?per_page=30"
    const val PAGE = "https://github.com/$REPOSITORY/releases"

    /** The APK and its checksum file, as the release workflow names them. */
    fun apkName(v: AppVersion) = "zairenkai-$v.apk"
    fun checksumName(v: AppVersion) = "zairenkai-$v.apk.sha256"

    /** Zairenkai-Modules releases: the root module (docs/RELEASING.md there). */
    const val MODULES_REPOSITORY = "ZairenkaiPrjctX/Zairenkai-Modules"
    const val MODULES_API = "https://api.github.com/repos/$MODULES_REPOSITORY/releases?per_page=30"
    const val MODULES_PAGE = "https://github.com/$MODULES_REPOSITORY/releases"
    fun moduleZipName(v: AppVersion) = "zairenkai-v$v.zip"
}

/**
 * A published release of the zairenkai root module. [versionCode] is module.prop's
 * (MAJOR·10000 + MINOR·100 + PATCH, Zairenkai-Modules assemble.py), the number root managers and
 * the app compare with the installed module.
 */
data class ModuleRelease(
    val version: AppVersion,
    val tag: String,
    val notes: String,
    val publishedAt: String,
    val page: String,
    val zip: ReleaseAsset,
) {
    val versionCode: Long get() = version.major * 10_000L + version.minor * 100L + version.patch
}

/** One downloadable file of a release. */
data class ReleaseAsset(val name: String, val url: String, val size: Long)

/** A published app release the update check can offer. */
data class AppRelease(
    val version: AppVersion,
    val tag: String,
    val title: String,
    /** The release notes (Markdown) the maintainer wrote. */
    val notes: String,
    /** ISO-8601 time GitHub published it. */
    val publishedAt: String,
    val page: String,
    val prerelease: Boolean,
    val apk: ReleaseAsset,
    val checksum: ReleaseAsset,
)

/** Why a release in the feed cannot be offered (shown nowhere but tests and logs). */
data class SkippedRelease(val tag: String, val reason: String)

data class ReleaseFeedResult(
    val releases: List<AppRelease>,
    val skipped: List<SkippedRelease>,
    /** Number of GitHub releases that are actually published, including malformed/incomplete ones. */
    val publishedCount: Int = releases.size + skipped.size,
)

/** Explicit result of an update check. An empty feed is not evidence that this build is current. */
sealed interface ReleaseCheckResult {
    data class Available(val release: AppRelease) : ReleaseCheckResult
    data object UpToDate : ReleaseCheckResult
    data object NoReleasesPublished : ReleaseCheckResult
    data object NoCompatibleReleases : ReleaseCheckResult
}

object ReleaseFeed {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GhAsset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        val size: Long = 0,
        val state: String = "uploaded",
    )

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tag: String,
        val name: String? = null,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerialName("published_at") val publishedAt: String? = null,
        @SerialName("html_url") val page: String = "",
        val assets: List<GhAsset> = emptyList(),
    )

    /**
     * Parses the GitHub "list releases" answer. A release is offered only when it is published (not
     * a draft), its tag is an [AppVersion], its pre-release flag agrees with the version (a beta is
     * a pre-release and a release is not), and it carries the APK and the checksum file under the
     * names the release workflow uses, both served over HTTPS.
     */
    fun parse(text: String): ReleaseFeedResult {
        val all = json.decodeFromString<List<GhRelease>>(text)
        val ok = mutableListOf<AppRelease>()
        val skipped = mutableListOf<SkippedRelease>()
        for (r in all) {
            fun skip(reason: String) = skipped.add(SkippedRelease(r.tag, reason))
            if (r.draft || r.publishedAt == null) { skip("not published"); continue }
            val v = AppVersion.parse(r.tag)
            if (v == null) { skip("tag is not an app version"); continue }
            if (v.isBeta != r.prerelease) { skip("pre-release flag does not match the version"); continue }
            val byName = r.assets.filter { it.state == "uploaded" }.associateBy { it.name }
            val apk = byName[ReleaseSource.apkName(v)]
            val sum = byName[ReleaseSource.checksumName(v)]
            if (apk == null || sum == null) { skip("missing ${ReleaseSource.apkName(v)} or its .sha256"); continue }
            if (!apk.url.startsWith("https://") || !sum.url.startsWith("https://")) { skip("asset not served over HTTPS"); continue }
            ok += AppRelease(
                version = v,
                tag = r.tag,
                title = r.name?.takeIf { it.isNotBlank() } ?: r.tag,
                notes = r.body.orEmpty(),
                publishedAt = r.publishedAt,
                page = r.page,
                prerelease = r.prerelease,
                apk = ReleaseAsset(apk.name, apk.url, apk.size),
                checksum = ReleaseAsset(sum.name, sum.url, sum.size),
            )
        }
        return ReleaseFeedResult(
            releases = ok.sortedByDescending { it.version },
            skipped = skipped,
            publishedCount = all.count { !it.draft && it.publishedAt != null },
        )
    }

    /**
     * Module releases: published, not pre-release, tag `vMAJOR.MINOR.PATCH`, with the zip under
     * the name the module's release workflow uses. Newest first.
     */
    fun parseModules(text: String): List<ModuleRelease> =
        json.decodeFromString<List<GhRelease>>(text).mapNotNull { r ->
            if (r.draft || r.prerelease || r.publishedAt == null) return@mapNotNull null
            val v = AppVersion.parse(r.tag)?.takeIf { !it.isBeta && r.tag.startsWith("v") } ?: return@mapNotNull null
            val zip = r.assets.firstOrNull { it.state == "uploaded" && it.name == ReleaseSource.moduleZipName(v) } ?: return@mapNotNull null
            if (!zip.url.startsWith("https://")) return@mapNotNull null
            ModuleRelease(v, r.tag, r.body.orEmpty(), r.publishedAt, r.page, ReleaseAsset(zip.name, zip.url, zip.size))
        }.sortedByDescending { it.version }

    /**
     * The newest release on [channel] that is newer than [current], or null. The beta channel
     * also offers releases, so a beta user moves on to the release of the same version.
     */
    fun newest(releases: List<AppRelease>, channel: UpdateChannel, current: AppVersion): AppRelease? =
        releases.filter { channel == UpdateChannel.BETA || !it.version.isBeta }
            .filter { it.version > current }
            .maxByOrNull { it.version }

    /** Keep "no published release" distinct from "this build is current" and malformed feeds. */
    fun evaluate(feed: ReleaseFeedResult, channel: UpdateChannel, current: AppVersion): ReleaseCheckResult {
        if (feed.publishedCount == 0) return ReleaseCheckResult.NoReleasesPublished
        val channelReleases = feed.releases.filter { channel == UpdateChannel.BETA || !it.version.isBeta }
        if (channelReleases.isEmpty()) return ReleaseCheckResult.NoCompatibleReleases
        val newer = newest(channelReleases, channel, current)
        return if (newer == null) ReleaseCheckResult.UpToDate else ReleaseCheckResult.Available(newer)
    }
}

/** The SHA-256 a `.sha256` file names for [fileName] (`<hex>  <name>` or a bare hex digest), or null. */
fun parseChecksumFile(text: String, fileName: String): String? {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    for (line in lines) {
        val parts = line.split(Regex("\\s+"), limit = 2)
        val digest = parts[0].lowercase()
        if (!digest.matches(Regex("^[0-9a-f]{64}$"))) continue
        val name = parts.getOrNull(1)?.removePrefix("*")
        if (name == null && lines.size == 1) return digest
        if (name == fileName) return digest
    }
    return null
}
