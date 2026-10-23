// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.update

/** What an APK says about itself (PackageManager reads it on the device). */
data class ApkFacts(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    /** SHA-256 of each signing certificate, lowercase hex. */
    val signers: Set<String>,
)

sealed interface ApkVerdict {
    data object Accept : ApkVerdict
    data class Reject(val reason: ApkProblem, val detail: String) : ApkVerdict
}

enum class ApkProblem { NOT_AN_APK, OTHER_PACKAGE, WRONG_VERSION, NOT_NEWER, UNSIGNED, OTHER_SIGNER }

object ApkCheck {
    /**
     * Whether the downloaded [candidate] may replace the [installed] app as [expected]. Android
     * itself refuses an update signed by another key; this check says so before the system
     * installer opens, and also refuses another package, a different version than the release
     * named, and anything not newer than what is installed.
     */
    fun decide(installed: ApkFacts, candidate: ApkFacts?, expected: AppVersion): ApkVerdict = when {
        candidate == null -> ApkVerdict.Reject(ApkProblem.NOT_AN_APK, "Android could not read the file as an app package")
        candidate.packageName != installed.packageName ->
            ApkVerdict.Reject(ApkProblem.OTHER_PACKAGE, "package ${candidate.packageName}, expected ${installed.packageName}")
        candidate.versionCode != expected.code || candidate.versionName != expected.toString() ->
            ApkVerdict.Reject(ApkProblem.WRONG_VERSION, "${candidate.versionName} (${candidate.versionCode}), the release says $expected (${expected.code})")
        candidate.versionCode <= installed.versionCode ->
            ApkVerdict.Reject(ApkProblem.NOT_NEWER, "${candidate.versionName} is not newer than the installed ${installed.versionName}")
        candidate.signers.isEmpty() -> ApkVerdict.Reject(ApkProblem.UNSIGNED, "the package is not signed")
        candidate.signers != installed.signers ->
            ApkVerdict.Reject(ApkProblem.OTHER_SIGNER, "signed by ${candidate.signers.joinToString { it.take(16) }}, this app by ${installed.signers.joinToString { it.take(16) }}")
        else -> ApkVerdict.Accept
    }
}
