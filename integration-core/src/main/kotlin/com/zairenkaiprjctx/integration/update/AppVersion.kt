// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.update

/**
 * The app's version: `MAJOR.MINOR.PATCH` for a release, `MAJOR.MINOR.PATCH-beta.N` for a beta of it
 * (a GitHub pre-release). Betas come before their release, as in semantic versioning.
 *
 * [code] is Android's versionCode, derived so that it grows with the version:
 * `MAJOR·1 000 000 + MINOR·10 000 + PATCH·100 + (N for beta N, 99 for the release)`.
 * 0.2.0-beta.1 is 20001, 0.2.0 is 20099, 0.2.1-beta.1 is 20101. MINOR and PATCH stay below 100
 * and N between 1 and 98, so no two versions share a code. app/build.gradle.kts computes the same
 * number (`AppVersionTest` checks both agree).
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int, val beta: Int? = null) : Comparable<AppVersion> {
    init {
        require(major in 0..2000 && minor in 0..99 && patch in 0..99) { "version out of range: $major.$minor.$patch" }
        require(beta == null || beta in 1..98) { "beta number out of range: $beta" }
    }

    val isBeta: Boolean get() = beta != null

    val code: Long get() = major * 1_000_000L + minor * 10_000L + patch * 100L + (beta ?: 99)

    /** The release this is a beta of, or itself. */
    val release: AppVersion get() = copy(beta = null)

    override fun compareTo(other: AppVersion): Int = code.compareTo(other.code)

    override fun toString(): String = "$major.$minor.$patch" + (beta?.let { "-beta.$it" } ?: "")

    companion object {
        private val PATTERN = Regex("""^v?(0|[1-9]\d{0,3})\.(0|[1-9]\d?)\.(0|[1-9]\d?)(?:-beta\.([1-9]\d?))?$""")

        /** Parses `1.2.3` or `1.2.3-beta.4`, with or without a leading `v`; anything else is null. */
        fun parse(text: String): AppVersion? {
            val m = PATTERN.matchEntire(text.trim()) ?: return null
            val (a, b, c, d) = m.destructured
            return runCatching { AppVersion(a.toInt(), b.toInt(), c.toInt(), d.ifEmpty { null }?.toInt()) }.getOrNull()
        }
    }
}

/** Which releases the user wants to be offered. */
enum class UpdateChannel {
    /** Releases only. */
    STABLE,

    /** Releases and betas (GitHub pre-releases): newer features, less testing. */
    BETA,
}
