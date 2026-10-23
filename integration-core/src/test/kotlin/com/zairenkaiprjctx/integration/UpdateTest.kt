// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.update.ApkCheck
import com.zairenkaiprjctx.integration.update.ApkFacts
import com.zairenkaiprjctx.integration.update.ApkProblem
import com.zairenkaiprjctx.integration.update.ApkVerdict
import com.zairenkaiprjctx.integration.update.AppVersion
import com.zairenkaiprjctx.integration.update.HttpResponse
import com.zairenkaiprjctx.integration.update.NoteBlock
import com.zairenkaiprjctx.integration.update.Notes
import com.zairenkaiprjctx.integration.update.ReleaseClient
import com.zairenkaiprjctx.integration.update.ReleaseFeed
import com.zairenkaiprjctx.integration.update.Transport
import com.zairenkaiprjctx.integration.update.UpdateChannel
import com.zairenkaiprjctx.integration.update.UpdateException
import com.zairenkaiprjctx.integration.update.parseChecksumFile
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    private fun v(s: String) = AppVersion.parse(s)!!

    @Test
    fun versionsParseOrderAndMapToVersionCodes() {
        assertEquals(AppVersion(0, 2, 0, 1), v("v0.2.0-beta.1"))
        assertEquals(20001L, v("0.2.0-beta.1").code)
        assertEquals(20099L, v("0.2.0").code)
        assertEquals(20101L, v("0.2.1-beta.1").code)
        assertEquals(1_000_099L, v("1.0.0").code)
        val ordered = listOf("0.1.0", "0.2.0-beta.1", "0.2.0-beta.2", "0.2.0", "0.2.1-beta.1", "0.10.0", "1.0.0").map(::v)
        assertEquals(ordered, ordered.shuffled(java.util.Random(3)).sorted())
        assertEquals("0.2.0-beta.12", v("0.2.0-beta.12").toString())
        for (bad in listOf("", "1.2", "1.2.3.4", "01.2.3", "1.2.3-rc.1", "1.2.3-beta", "1.2.3-beta.0", "1.2.3-beta.99", "1.100.0", "x1.2.3", "vv1.2.3")) {
            assertNull(bad, AppVersion.parse(bad))
        }
        assertTrue(v("0.2.0-beta.3").isBeta)
        assertEquals(v("0.2.0"), v("0.2.0-beta.3").release)
    }

    /** The app's own version, as app/build.gradle.kts declares and maps it. */
    @Test
    fun buildFileComputesTheSameVersionCode() {
        val gradle = java.io.File("../app/build.gradle.kts").readText()
        val name = Regex("""val appVersion = "([^"]+)"""").find(gradle)!!.groupValues[1]
        val parsed = AppVersion.parse(name)!!
        // The build file's formula, evaluated for this version and the edge cases.
        assertTrue(gradle.contains("major * 1_000_000 + minor * 10_000 + patch * 100 + (beta ?: 99)"))
        assertTrue("the first version code was 1", parsed.code > 1)
    }

    @Test
    fun theRealFeedOfThisRepositoryIsEmpty() {
        val r = ReleaseFeed.parse(contract("github/releases-zairenkai-app-2026-10-23.json"))
        assertEquals(emptyList<Any>(), r.releases)
        assertNull(ReleaseFeed.newest(r.releases, UpdateChannel.BETA, v("0.1.0")))
    }

    private fun release(tag: String, prerelease: Boolean, draft: Boolean = false, assets: List<String>? = null, url: String = "https://github.com/x") =
        """{"tag_name":"$tag","name":"Zairenkai $tag","body":"## Added\n- thing","draft":$draft,"prerelease":$prerelease,
           "published_at":${if (draft) "null" else "\"2026-10-23T10:00:00Z\""},"html_url":"https://github.com/ZairenkaiPrjctX/Zairenkai-App/releases/tag/$tag",
           "assets":[${(assets ?: listOf("zairenkai-${tag.removePrefix("v")}.apk", "zairenkai-${tag.removePrefix("v")}.apk.sha256"))
            .joinToString { """{"name":"$it","browser_download_url":"$url/$it","size":10,"state":"uploaded"}""" }}]}"""

    /** A synthetic feed (see contracts/github/README.md): only fields GitHub documents. */
    private val synthetic = "[" + listOf(
        release("v0.3.0-beta.1", prerelease = true),
        release("v0.2.0", prerelease = false),
        release("v0.2.0-beta.2", prerelease = true),
        release("v0.4.0", prerelease = false, draft = true),
        release("v0.5.0", prerelease = true), // flag disagrees with the version
        release("nightly", prerelease = true),
        release("v0.2.1", prerelease = false, assets = listOf("app-release.apk")),
        release("v0.2.2", prerelease = false, url = "http://github.com/x"),
    ).joinToString(",") + "]"

    @Test
    fun channelsOfferOnlyPublishedCompleteNewerReleases() {
        val feed = ReleaseFeed.parse(synthetic)
        assertEquals(listOf("v0.3.0-beta.1", "v0.2.0", "v0.2.0-beta.2"), feed.releases.map { it.tag })
        assertEquals(5, feed.skipped.size)
        assertEquals(v("0.2.0"), ReleaseFeed.newest(feed.releases, UpdateChannel.STABLE, v("0.1.0"))?.version)
        assertEquals(v("0.3.0-beta.1"), ReleaseFeed.newest(feed.releases, UpdateChannel.BETA, v("0.1.0"))?.version)
        // A beta user is moved on to the release of that version.
        assertEquals(v("0.2.0"), ReleaseFeed.newest(feed.releases, UpdateChannel.STABLE, v("0.2.0-beta.2"))?.version)
        assertNull(ReleaseFeed.newest(feed.releases, UpdateChannel.STABLE, v("0.2.0")))
        assertNull(ReleaseFeed.newest(feed.releases, UpdateChannel.BETA, v("0.3.0-beta.1")))
        val r = feed.releases.first()
        assertEquals("zairenkai-0.3.0-beta.1.apk", r.apk.name)
        assertEquals("Zairenkai v0.3.0-beta.1", r.title)
    }

    @Test
    fun checksumFiles() {
        val d = "a".repeat(64)
        assertEquals(d, parseChecksumFile("$d  zairenkai-0.2.0.apk\n", "zairenkai-0.2.0.apk"))
        assertEquals(d, parseChecksumFile("$d *zairenkai-0.2.0.apk", "zairenkai-0.2.0.apk"))
        assertEquals(d, parseChecksumFile(d.uppercase(), "zairenkai-0.2.0.apk"))
        assertNull(parseChecksumFile("$d  other.apk", "zairenkai-0.2.0.apk"))
        assertNull(parseChecksumFile("abc  zairenkai-0.2.0.apk", "zairenkai-0.2.0.apk"))
    }

    @Test
    fun apkChecks() {
        val installed = ApkFacts("com.zairenkaiprjctx.app", 20001, "0.2.0-beta.1", setOf("k1"))
        val good = ApkFacts("com.zairenkaiprjctx.app", 20099, "0.2.0", setOf("k1"))
        val expected = v("0.2.0")
        assertEquals(ApkVerdict.Accept, ApkCheck.decide(installed, good, expected))
        fun problem(c: ApkFacts?, e: AppVersion = expected) = (ApkCheck.decide(installed, c, e) as ApkVerdict.Reject).reason
        assertEquals(ApkProblem.NOT_AN_APK, problem(null))
        assertEquals(ApkProblem.OTHER_PACKAGE, problem(good.copy(packageName = "evil.app")))
        assertEquals(ApkProblem.WRONG_VERSION, problem(good.copy(versionCode = 20098)))
        assertEquals(ApkProblem.WRONG_VERSION, problem(good, v("0.2.1")))
        assertEquals(ApkProblem.NOT_NEWER, problem(ApkFacts("com.zairenkaiprjctx.app", 20001, "0.2.0-beta.1", setOf("k1")), v("0.2.0-beta.1")))
        assertEquals(ApkProblem.UNSIGNED, problem(good.copy(signers = emptySet())))
        assertEquals(ApkProblem.OTHER_SIGNER, problem(good.copy(signers = setOf("k2"))))
    }

    @Test
    fun releaseNotesAndChangelog() {
        val blocks = Notes.parse("## Added\n- **Bold** [link](https://x) and `code`\n  continued\n  - nested\n\nA <b>para</b>\nline two\n<!-- hidden -->")
        assertEquals(
            listOf(
                NoteBlock.Heading(2, "Added"),
                NoteBlock.Bullet("Bold link and code continued", 0),
                NoteBlock.Bullet("nested", 1),
                NoteBlock.Paragraph("A para line two"),
            ),
            blocks,
        )
        val log = "# Changelog\n\n## [Unreleased]\n\n## [0.2.0-beta.1] - 2026-10-23\n### Added\n- Updates\n\n## [0.1.0] - 2026-10-19\n- First\n"
        val entries = Notes.changelog(log)
        assertEquals(listOf("Unreleased", "0.2.0-beta.1", "0.1.0"), entries.map { it.version })
        assertEquals("2026-10-23", entries[1].date)
        assertEquals(NoteBlock.Bullet("Updates", 0), Notes.entryFor(log, "v0.2.0-beta.1")!!.blocks.last())
        // The repository's own changelog has an entry for the version the build declares.
        val gradle = java.io.File("../app/build.gradle.kts").readText()
        val name = Regex("""val appVersion = "([^"]+)"""").find(gradle)!!.groupValues[1]
        assertTrue(Notes.entryFor(java.io.File("../CHANGELOG.md").readText(), name)!!.blocks.isNotEmpty())
    }

    private class FakeTransport(val files: Map<String, ByteArray>) : Transport {
        val asked = mutableListOf<String>()
        override fun get(url: String, accept: String): HttpResponse {
            asked += url
            val b = files[url] ?: return HttpResponse(url, 404, 0, ByteArrayInputStream(ByteArray(0)))
            return HttpResponse(url, 200, b.size.toLong(), ByteArrayInputStream(b))
        }
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Test
    fun downloadKeepsOnlyAMatchingFile() {
        val apk = "PK fake apk bytes".toByteArray()
        val base = "https://github.com/ZairenkaiPrjctX/Zairenkai-App/releases/download/v0.2.0/"
        fun feed(size: Int) = "[" + """{"tag_name":"v0.2.0","draft":false,"prerelease":false,"published_at":"2026-10-23T10:00:00Z","html_url":"https://github.com/x",
            "assets":[{"name":"zairenkai-0.2.0.apk","browser_download_url":"${base}zairenkai-0.2.0.apk","size":$size},
            {"name":"zairenkai-0.2.0.apk.sha256","browser_download_url":"${base}zairenkai-0.2.0.apk.sha256","size":90}]}""" + "]"
        val dir = Files.createTempDirectory("upd").toFile()

        val good = FakeTransport(mapOf(
            "https://api.github.com/repos/ZairenkaiPrjctX/Zairenkai-App/releases?per_page=30" to feed(apk.size).toByteArray(),
            "${base}zairenkai-0.2.0.apk" to apk,
            "${base}zairenkai-0.2.0.apk.sha256" to "${sha(apk)}  zairenkai-0.2.0.apk\n".toByteArray(),
        ))
        val client = ReleaseClient(good)
        val r = client.check(UpdateChannel.STABLE, v("0.1.0"))!!
        val d = client.download(r, dir)
        assertEquals(sha(apk), d.sha256)
        assertTrue(d.file.readBytes().contentEquals(apk))

        val tampered = FakeTransport(good.files + ("${base}zairenkai-0.2.0.apk" to "PK other bytes!!!".toByteArray()))
        assertThrows(UpdateException::class.java) { ReleaseClient(tampered).download(r, dir) }
        assertFalse(java.io.File(dir, "zairenkai-0.2.0.apk.part").exists())

        val longer = FakeTransport(good.files + ("${base}zairenkai-0.2.0.apk" to (apk + apk)))
        assertThrows(UpdateException::class.java) { ReleaseClient(longer).download(r, dir) }

        // Only HTTPS on GitHub's hosts, whatever the feed says.
        val elsewhere = r.copy(apk = r.apk.copy(url = "https://evil.example/zairenkai-0.2.0.apk"))
        assertThrows(UpdateException::class.java) { client.download(elsewhere, dir) }
        assertThrows(UpdateException::class.java) { ReleaseClient.requireAllowed("http://github.com/x") }
        ReleaseClient.requireAllowed("https://objects.githubusercontent.com/x")
    }
}
