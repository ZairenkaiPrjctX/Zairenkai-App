package com.zairenkaiprjctx.integration

import com.zairenkaiprjctx.integration.exec.CommandOutcome
import com.zairenkaiprjctx.integration.exec.CommandResult
import com.zairenkaiprjctx.integration.exec.CommandRunner
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.systemmodule.ArtifactStore
import com.zairenkaiprjctx.integration.systemmodule.BundledModule
import com.zairenkaiprjctx.integration.systemmodule.DeviceFacts
import com.zairenkaiprjctx.integration.systemmodule.InstallPhase
import com.zairenkaiprjctx.integration.systemmodule.InstallSupport
import com.zairenkaiprjctx.integration.systemmodule.InstalledState
import com.zairenkaiprjctx.integration.systemmodule.ManagerDetection
import com.zairenkaiprjctx.integration.systemmodule.ManagerFamily
import com.zairenkaiprjctx.integration.systemmodule.ManagerIdentity
import com.zairenkaiprjctx.integration.systemmodule.ManagerRequirement
import com.zairenkaiprjctx.integration.systemmodule.ModuleCatalog
import com.zairenkaiprjctx.integration.systemmodule.ModuleConflict
import com.zairenkaiprjctx.integration.systemmodule.ModuleInstallController
import com.zairenkaiprjctx.integration.systemmodule.ModulePackageVerifier
import com.zairenkaiprjctx.integration.systemmodule.ModuleRecord
import com.zairenkaiprjctx.integration.systemmodule.ModuleRequirements
import com.zairenkaiprjctx.integration.systemmodule.ModuleSource
import com.zairenkaiprjctx.integration.systemmodule.ModuleStateReader
import com.zairenkaiprjctx.integration.systemmodule.PackageFault
import com.zairenkaiprjctx.integration.systemmodule.PackageVerdict
import com.zairenkaiprjctx.integration.systemmodule.Preflight
import com.zairenkaiprjctx.integration.systemmodule.PreflightProbe
import com.zairenkaiprjctx.integration.systemmodule.RootManagerDetector
import com.zairenkaiprjctx.integration.systemmodule.RuntimeProbe
import com.zairenkaiprjctx.integration.systemmodule.Verdict
import com.zairenkaiprjctx.integration.systemmodule.adapterFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A scripted su: answers keyed by the exact `su -c` script, plus a mutable on-disk module layout
 * that the installer commands change the way the managers do (staging into modules_update).
 * Nothing here is a real manager; these tests prove the app's logic, not device behaviour.
 */
private class FakeSu : CommandRunner {
    val calls = mutableListOf<String>()
    val answers = mutableMapOf<String, CommandOutcome>()
    val files = mutableMapOf<String, String>() // path -> content (module.prop)
    val dirs = mutableMapOf<String, MutableSet<String>>() // dir -> marker names
    var onInstall: (String) -> CommandOutcome = { CommandOutcome.Finished(CommandResult(1, "", "no installer")) }

    fun answer(script: String, exit: Int, out: String = "") = apply { answers[script] = CommandOutcome.Finished(CommandResult(exit, out, "")) }

    override fun run(argv: List<String>, timeoutMs: Long): CommandOutcome {
        if (argv == listOf("su", "-c", "id")) return CommandOutcome.Finished(CommandResult(0, "uid=0(root) gid=0(root)\n", ""))
        require(argv.size == 3 && argv[0] == "su" && argv[1] == "-c") { "unexpected argv $argv" }
        val s = argv[2]
        calls += s
        answers[s]?.let { return it }
        val words = Regex("'([^']*)'").findAll(s).map { it.groupValues[1] }.toList()
        return when {
            words.firstOrNull() == "cat" -> files[words[1]]?.let { ok(it) } ?: fail()
            words.take(2) == listOf("ls", "-a") -> dirs[words[2]]?.let { ok((setOf(".", "..", "module.prop") + it).joinToString("\n")) } ?: fail()
            words == listOf("ls", "/data/adb/modules") -> ok(dirs.keys.filter { it.startsWith("/data/adb/modules/") }.joinToString("\n") { it.substringAfterLast('/') })
            words.contains("install") || words.contains("--install-module") -> onInstall(words.last())
            else -> fail()
        }
    }

    private fun ok(out: String) = CommandOutcome.Finished(CommandResult(0, out, ""))
    private fun fail() = CommandOutcome.Finished(CommandResult(1, "", "No such file or directory"))

    fun stage(id: String, version: String, code: Long, active: Boolean = false) {
        val dir = if (active) "/data/adb/modules/$id" else "/data/adb/modules_update/$id"
        files["$dir/module.prop"] = "id=$id\nname=x\nversion=$version\nversionCode=$code\n"
        dirs.getOrPut(dir) { mutableSetOf() }
    }

    /** Calls that change the device. Everything else must be a read. */
    val mutating get() = calls.filter { c -> listOf("install", "--install-module", "uninstall").any { "'$it'" in c } }
}

private fun q(vararg w: String) = w.joinToString(" ") { "'$it'" }

private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

private val tmp: File = Files.createTempDirectory("sysmod").toFile().also { it.deleteOnExit() }

private fun zip(name: String, entries: Map<String, String>): File {
    val f = File(tmp, name)
    ZipOutputStream(f.outputStream()).use { z ->
        entries.forEach { (n, body) ->
            z.putNextEntry(ZipEntry(n))
            z.write(body.toByteArray())
            z.closeEntry()
        }
    }
    return f
}

private val CORE_ENTRIES = mapOf(
    "module.prop" to "id=zairenkai_core\nname=Zairenkai-Core\nversion=v0.1.0\nversionCode=100\n",
    "customize.sh" to "#!/system/bin/sh\n",
    "service.sh" to "#!/system/bin/sh\n",
    "META-INF/com/google/android/update-binary" to "#!/sbin/sh\n",
    "META-INF/com/google/android/updater-script" to "#MAGISK\n",
    "bin/zcored" to "ELF",
)

private fun module(
    file: File,
    entries: Map<String, String> = CORE_ENTRIES,
    id: String = "zairenkai_core",
    requirements: ModuleRequirements = REQ,
    validatedOn: List<String> = emptyList(),
) = BundledModule(
    id = id, role = "base", name = "Zairenkai System Module (Core)", features = listOf("core.health"),
    requirements = requirements, rebootRequired = true, dataDir = "/data/adb/zairenkai-core", runtimeCheck = "core.health",
    validatedOn = validatedOn, file = "system-modules/$id.zip", version = "v0.1.0", versionCode = 100,
    size = file.length(), sha256 = sha(file.readBytes()), source = ModuleSource("ZairenkaiPrjctX/Zairenkai-Core", "34e93e34440efb90d64d4af1f9f10292d790575c"),
    entries = entries.mapValues { sha(it.value.toByteArray()) },
    sourceMatched = listOf("META-INF/com/google/android/update-binary", "META-INF/com/google/android/updater-script", "customize.sh", "service.sh"),
)

private val REQ = ModuleRequirements(
    arch = "arm64", abi = "arm64-v8a", minApi = 29,
    managers = mapOf(
        "magisk" to ManagerRequirement(21200, "test"),
        "kernelsu" to ManagerRequirement(null, "test"),
        "apatch" to ManagerRequirement(null, "test"),
    ),
)

private val DEVICE = DeviceFacts(
    manufacturer = "Google", brand = "google", model = "Pixel 8a", codename = "akita",
    socManufacturer = "Google", socModel = "Tensor G3", sdkInt = 35, supportedAbis = listOf("arm64-v8a"),
    kernelMachine = "aarch64", kernelRelease = "6.1.0", freeDataBytes = 8L shl 30,
)

private fun ksu(): FakeSu = FakeSu()
    .answer(q("su", "-v"), 0, "v1.0.5:KernelSU\n")
    .answer(q("su", "-V"), 0, "12000\n")
    .answer(q("/data/adb/ksud", "module", "list"), 0, "[]\n")
    .answer(q("/data/adb/ksud", "module", "--help"), 0, "Usage: ksud module <COMMAND>\n\nCommands:\n  install    Install module <ZIP>\n  uninstall  Uninstall module <id>\n  list       list all modules\n")
    .answer(q("uname", "-m"), 0, "aarch64\n")
    .answer(q("uname", "-r"), 0, "6.1.0\n")

private fun magisk(code: Long): FakeSu = FakeSu()
    .answer(q("su", "-v"), 0, "${code / 1000}.0:MAGISKSU\n")
    .answer(q("su", "-V"), 0, "$code\n")
    .answer(q("magisk", "-V"), 0, "$code\n")
    .answer(q("uname", "-m"), 0, "aarch64\n")
    .answer(q("uname", "-r"), 0, "6.1.0\n")

private fun granted(su: FakeSu) = RootShell(su, MemoryConsent(), suPresent = { true }).also { it.request() }

private class Harness(val su: FakeSu, val module: BundledModule, runtimeFailure: String? = null, storeFails: Boolean = false, rootGranted: Boolean = true) {
    val root = if (rootGranted) granted(su) else RootShell(su, MemoryConsent(), suPresent = { true })
    val reader = ModuleStateReader(root)
    val stagedFiles = mutableListOf<File>()
    val controller = ModuleInstallController(
        root = root,
        catalog = { ModuleCatalog(1, listOf(module)) },
        detector = { RootManagerDetector(root).detect() },
        facts = { DEVICE },
        probe = PreflightProbe(root, reader),
        store = ArtifactStore { m ->
            if (storeFails) error("asset missing")
            val src = File(tmp, "${m.id}.zip")
            File(tmp, "staged-${stagedFiles.size}.zip").also { src.copyTo(it, overwrite = true); stagedFiles += it }
        },
        adapters = { adapterFor(it, root) },
        runtime = RuntimeProbe { runtimeFailure },
    )
}

private fun coreZip(): File = zip("zairenkai_core.zip", CORE_ENTRIES)

class SystemModuleTest {
    // ---- root-manager detection -------------------------------------------------------------

    @Test
    fun detectsEachManagerFromTwoIndependentSources() {
        val k = RootManagerDetector(granted(ksu())).detect() as ManagerDetection.Identified
        assertEquals(ManagerFamily.KERNELSU, k.identity.family)
        assertEquals(InstallSupport.DIRECT, k.identity.install)
        assertEquals("v1.0.5", k.identity.versionName)

        val m = RootManagerDetector(granted(magisk(27000))).detect() as ManagerDetection.Identified
        assertEquals(ManagerFamily.MAGISK, m.identity.family)
        assertEquals(27000L, m.identity.versionCode)
        assertEquals(InstallSupport.DIRECT, m.identity.install)

        val ap = FakeSu().answer(q("su", "-v"), 0, "11039:APatch\n").answer(q("su", "-V"), 0, "11039\n")
            .answer(q("/data/adb/apd", "module", "list"), 0, "[\n]\n")
            .answer(q("/data/adb/apd", "module", "--help"), 0, "Commands:\n  install  Install module <ZIP>\n")
        assertEquals(ManagerFamily.APATCH, (RootManagerDetector(granted(ap)).detect() as ManagerDetection.Identified).identity.family)
    }

    @Test
    fun oldOrUnknownOrContradictoryManagersAreNotTrusted() {
        // Magisk 21.0: modules work, but --install-module was broken before v21.2: hand off.
        val old = RootManagerDetector(granted(magisk(21000))).detect() as ManagerDetection.Identified
        assertEquals(InstallSupport.HANDOFF_ONLY, old.identity.install)
        // Magisk 20.0: the module's update-binary refuses it.
        assertTrue(RootManagerDetector(granted(magisk(20000))).detect() is ManagerDetection.VersionIncompatible)
        // An su that is none of the three.
        val other = FakeSu().answer(q("su", "-v"), 0, "2.82:SUPERSU\n")
        assertTrue(RootManagerDetector(granted(other)).detect() is ManagerDetection.Unsupported)
        // su claims Magisk, but no magisk CLI answers: not accepted on the tag alone.
        val liar = FakeSu().answer(q("su", "-v"), 0, "27.0:MAGISKSU\n")
        assertTrue(RootManagerDetector(granted(liar)).detect() is ManagerDetection.Unsupported)
        // ksud answers but has no install subcommand: installation is not assumed.
        val noInstall = ksu().answer(q("/data/adb/ksud", "module", "--help"), 0, "Commands:\n  list\n")
        assertEquals(InstallSupport.UNVERIFIED, (RootManagerDetector(granted(noInstall)).detect() as ManagerDetection.Identified).identity.install)
    }

    @Test
    fun withoutGrantedRootNothingRunsThroughSu() {
        val su = ksu()
        val notAsked = RootShell(su, MemoryConsent(), suPresent = { true })
        assertEquals(ManagerDetection.NotGranted, RootManagerDetector(notAsked).detect())
        val absent = RootShell(su, MemoryConsent(), suPresent = { false }).also { it.request() }
        assertEquals(ManagerDetection.RootNotDetected, RootManagerDetector(absent).detect())
        val deniedSu = object : CommandRunner {
            override fun run(argv: List<String>, timeoutMs: Long) = CommandOutcome.Finished(CommandResult(1, "", "Permission denied"))
        }
        val denied = RootShell(deniedSu, MemoryConsent(), suPresent = { true }).also { it.request() }
        assertEquals(ManagerDetection.Denied, RootManagerDetector(denied).detect())
        assertTrue(su.calls.isEmpty())
    }

    // ---- package verification ---------------------------------------------------------------

    @Test
    fun aGoodPackageVerifies() {
        val f = coreZip()
        val v = ModulePackageVerifier.verify(f, module(f)) as PackageVerdict.Verified
        assertTrue(v.checks.any { it.contains("identical to ZairenkaiPrjctX/Zairenkai-Core") })
    }

    @Test
    fun tamperedOrMismatchedPackagesAreRejected() {
        val good = coreZip()
        val record = module(good)
        fun fault(f: File, m: BundledModule = record) = (ModulePackageVerifier.verify(f, m) as PackageVerdict.Rejected).fault

        // Corrupted: same size, one byte flipped.
        val corrupt = File(tmp, "corrupt.zip").also { good.copyTo(it, true) }
        corrupt.writeBytes(corrupt.readBytes().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() })
        assertEquals(PackageFault.DIGEST_MISMATCH, fault(corrupt))
        // Wrong size.
        assertEquals(PackageFault.SIZE_MISMATCH, fault(File(tmp, "short.zip").also { it.writeBytes(good.readBytes().copyOf(100)) }))
        // Incorrect recorded checksum.
        assertEquals(PackageFault.DIGEST_MISMATCH, fault(good, record.copy(sha256 = "0".repeat(64))))
        // Not a zip at all, with a matching record (as if the record were for garbage).
        val junk = File(tmp, "junk.zip").also { it.writeText("not a zip") }
        assertEquals(PackageFault.NOT_A_ZIP, fault(junk, record.copy(size = junk.length(), sha256 = sha(junk.readBytes()))))
        // A different module id inside an otherwise consistent package.
        val otherEntries = CORE_ENTRIES + ("module.prop" to "id=evil_module\nversion=v0.1.0\nversionCode=100\n")
        val other = zip("other.zip", otherEntries)
        assertEquals(PackageFault.MODULE_ID_MISMATCH, fault(other, module(other, otherEntries)))
        // An extra entry not in the build record.
        val extraEntries = CORE_ENTRIES + ("system/bin/sh" to "x")
        val extra = zip("extra.zip", extraEntries)
        assertEquals(PackageFault.UNEXPECTED_ENTRIES, fault(extra, module(extra, extraEntries).copy(entries = record.entries)))
        // Path traversal.
        val evilEntries = CORE_ENTRIES + ("../../data/adb/evil" to "x")
        val evil = zip("evil.zip", evilEntries)
        assertEquals(PackageFault.UNSAFE_ENTRY, fault(evil, module(evil, evilEntries)))
        // A version that differs from the record.
        assertEquals(PackageFault.VERSION_MISMATCH, fault(good, record.copy(versionCode = 101)))
        // Installer scripts not matched to reviewed source.
        assertEquals(PackageFault.INSTALLER_MISSING, fault(good, record.copy(sourceMatched = listOf("service.sh"))))
    }

    @Test
    fun catalogRejectsInconsistentRecords() {
        val f = coreZip()
        val m = module(f)
        fun parses(x: BundledModule) = runCatching {
            ModuleCatalog.parse(kotlinx.serialization.json.Json.encodeToString(ModuleCatalog.serializer(), ModuleCatalog(1, listOf(x))))
        }.isSuccess
        assertTrue(parses(m))
        assertFalse(parses(m.copy(file = "system-modules/other.zip")))
        assertFalse(parses(m.copy(sha256 = "abc")))
        assertFalse(parses(m.copy(source = m.source.copy(commit = "34e93e3"))))
        assertFalse(parses(m.copy(id = "1bad id")))
    }

    // ---- preflight --------------------------------------------------------------------------

    private val kernelsu = ManagerIdentity(ManagerFamily.KERNELSU, "v1.0.5", 12000, InstallSupport.DIRECT, emptyList())
    private val none = InstalledState(null, null)

    private fun decide(
        m: BundledModule = module(coreZip()),
        device: DeviceFacts = DEVICE,
        manager: ManagerIdentity = kernelsu,
        installed: InstalledState = none,
        ids: Set<String> = emptySet(),
        paths: Boolean = true,
        deps: Boolean = true,
    ) = Preflight.decide(m, device, manager, installed, ids, { paths }, { deps })

    @Test
    fun aCompatibleButUnvalidatedDeviceIsExperimentalNeverSupported() {
        val d = decide()
        assertEquals(Verdict.EXPERIMENTAL, d.verdict)
        assertTrue(d.checks.any { it.id == "validation" && it.detail.contains("experimental") })
        // A validation record for this codename (not the manufacturer) is what makes it COMPATIBLE.
        assertEquals(Verdict.COMPATIBLE, decide(m = module(coreZip(), validatedOn = listOf("akita"))).verdict)
        assertEquals(Verdict.EXPERIMENTAL, decide(m = module(coreZip(), validatedOn = listOf("Google"))).verdict)
    }

    @Test
    fun unmetRequirementsRefuseInstallation() {
        fun failed(d: com.zairenkaiprjctx.integration.systemmodule.CompatibilityDecision) = d.verdict == Verdict.INCOMPATIBLE
        assertTrue(failed(decide(device = DEVICE.copy(supportedAbis = listOf("x86_64")))))
        assertTrue(failed(decide(device = DEVICE.copy(kernelMachine = "x86_64"))))
        assertTrue(failed(decide(device = DEVICE.copy(sdkInt = 28))))
        assertTrue(failed(decide(device = DEVICE.copy(freeDataBytes = 1000))))
        assertTrue(failed(decide(manager = kernelsu.copy(family = ManagerFamily.MAGISK, versionCode = 21000))))
        val noApatch = REQ.copy(managers = REQ.managers - "apatch")
        assertTrue(failed(decide(m = module(coreZip(), requirements = noApatch), manager = kernelsu.copy(family = ManagerFamily.APATCH))))
        val fasReq = REQ.copy(requiredPaths = listOf("/sys/devices/system/cpu/cpufreq"), dependsOn = listOf("zairenkai_core"), conflicts = listOf(ModuleConflict("fas_rs", "same nodes")))
        val fas = module(coreZip(), requirements = fasReq)
        assertTrue(failed(decide(m = fas, paths = false)))
        assertTrue(failed(decide(m = fas, deps = false)))
        assertTrue(failed(decide(m = fas, ids = setOf("fas_rs"))))
        assertEquals(Verdict.EXPERIMENTAL, decide(m = fas).verdict)
    }

    @Test
    fun existingInstallationsAreRespected() {
        fun rec(code: Long, enabled: Boolean = true, remove: Boolean = false, update: Boolean = false) =
            ModuleRecord("zairenkai_core", "v0.0.$code", code, enabled, update, remove)
        assertEquals(Verdict.ALREADY_INSTALLED, decide(installed = InstalledState(rec(100), null)).verdict)
        assertEquals(Verdict.PENDING_REBOOT, decide(installed = InstalledState(null, rec(100, update = true))).verdict)
        assertEquals(Verdict.INCOMPATIBLE, decide(installed = InstalledState(null, rec(90, update = true))).verdict)
        assertEquals(Verdict.INCOMPATIBLE, decide(installed = InstalledState(rec(200), null)).verdict) // downgrade
        assertEquals(Verdict.INCOMPATIBLE, decide(installed = InstalledState(rec(90, remove = true), null)).verdict)
        assertEquals(Verdict.INCOMPATIBLE, decide(installed = InstalledState(rec(100, enabled = false), null)).verdict)
        val up = decide(installed = InstalledState(rec(90), null))
        assertEquals(Verdict.EXPERIMENTAL, up.verdict)
        assertEquals("v0.0.90", up.upgradeFrom)
    }

    // ---- the installation flow --------------------------------------------------------------

    @Test
    fun kernelSuInstallStagesTheModuleAndWaitsForReboot() {
        val f = coreZip()
        val su = ksu()
        val h = Harness(su, module(f))
        su.onInstall = { path ->
            assertTrue("the manager reads the verified staged copy", path == h.stagedFiles.last().absolutePath)
            su.stage("zairenkai_core", "v0.1.0", 100)
            CommandOutcome.Finished(CommandResult(0, "- Installing\n", ""))
        }
        val c = h.controller
        assertEquals(InstallPhase.MODULE_AVAILABLE, c.evaluate("zairenkai_core").phase)
        assertTrue("evaluation changes nothing", su.mutating.isEmpty())
        assertEquals(InstallPhase.READY_TO_INSTALL, c.prepare().phase)
        assertEquals(InstallPhase.AWAITING_USER_CONFIRMATION, c.requestConfirmation().phase)
        // Experimental: confirming without the acknowledgement does not install.
        assertEquals(InstallPhase.AWAITING_USER_CONFIRMATION, c.confirm(acknowledgedExperimental = false).phase)
        assertTrue(su.mutating.isEmpty())
        val done = c.confirm(acknowledgedExperimental = true)
        assertEquals(InstallPhase.INSTALL_PENDING_REBOOT, done.phase)
        assertEquals(listOf(q("/data/adb/ksud", "module", "install", h.stagedFiles.last().absolutePath)), su.mutating)
        assertFalse("the staged copy is removed after installation", h.stagedFiles.last().exists())

        // After the reboot the manager has moved it into modules/; the runtime answers.
        su.files.clear(); su.dirs.clear()
        su.stage("zairenkai_core", "v0.1.0", 100, active = true)
        assertEquals(InstallPhase.ACTIVE, c.evaluate("zairenkai_core").phase)
    }

    @Test
    fun magiskUsesItsOwnInstallerAndStateIsCheckedNotAssumed() {
        val f = coreZip()
        val su = magisk(27000)
        val h = Harness(su, module(f))
        su.onInstall = { CommandOutcome.Finished(CommandResult(0, "", "")) } // exit 0, but nothing staged
        val c = h.controller
        c.evaluate("zairenkai_core"); c.prepare(); c.requestConfirmation()
        val r = c.confirm(acknowledgedExperimental = true)
        assertEquals(InstallPhase.INSTALL_FAILED, r.phase)
        assertTrue(r.detail.contains("no zairenkai_core"))
        assertTrue(su.mutating.single().startsWith(q("magisk", "--install-module")))
    }

    @Test
    fun installerFailuresAndTimeoutsAreReported() {
        for (outcome in listOf(CommandOutcome.Finished(CommandResult(1, "! Metamodule installation blocked", "")), CommandOutcome.TimedOut)) {
            val su = ksu()
            val h = Harness(su, module(coreZip()))
            su.onInstall = { outcome }
            val c = h.controller
            c.evaluate("zairenkai_core"); c.prepare(); c.requestConfirmation()
            val r = c.confirm(true)
            assertEquals(InstallPhase.INSTALL_FAILED, r.phase)
            assertFalse(h.stagedFiles.last().exists())
        }
    }

    @Test
    fun preflightFailureChangesNothing() {
        val su = ksu().answer(q("uname", "-m"), 0, "x86_64\n")
        val h = Harness(su, module(coreZip()))
        val r = h.controller.evaluate("zairenkai_core")
        assertEquals(InstallPhase.UNSUPPORTED, r.phase)
        assertTrue(r.detail.contains("x86_64"))
        assertEquals(r, h.controller.prepare()) // no way forward
        assertTrue(su.mutating.isEmpty())
        assertTrue(h.stagedFiles.isEmpty())
        val allowed = listOf("'su'", "'/data/adb/ksud' 'module' 'list'", "'/data/adb/ksud' 'module' '--help'", "'cat'", "'ls'", "'uname'")
        assertTrue(su.calls.toString(), su.calls.all { c -> allowed.any { c.startsWith(it) } })
    }

    @Test
    fun cancellationAndDeclineLeaveTheDeviceUntouched() {
        val su = ksu()
        val h = Harness(su, module(coreZip()))
        val c = h.controller
        c.evaluate("zairenkai_core"); c.prepare(); c.requestConfirmation()
        assertEquals(InstallPhase.MODULE_AVAILABLE, c.cancel().phase)
        assertFalse(h.stagedFiles.single().exists())
        assertTrue(su.mutating.isEmpty())
        // Confirm after cancel does nothing.
        assertEquals(InstallPhase.MODULE_AVAILABLE, c.confirm(true).phase)
        assertTrue(su.mutating.isEmpty())
    }

    @Test
    fun anUnreadableArtifactFailsSafely() {
        val su = ksu()
        val h = Harness(su, module(coreZip()), storeFails = true)
        h.controller.evaluate("zairenkai_core")
        val r = h.controller.prepare()
        assertEquals(InstallPhase.INSTALL_FAILED, r.phase)
        assertTrue(su.mutating.isEmpty())
    }

    @Test
    fun aCorruptedArtifactNeverReachesTheManager() {
        val f = coreZip()
        val record = module(f).copy(sha256 = "1".repeat(64))
        val su = ksu()
        val h = Harness(su, record)
        h.controller.evaluate("zairenkai_core")
        val r = h.controller.prepare()
        assertEquals(InstallPhase.INSTALL_FAILED, r.phase)
        assertTrue(r.detail.contains("DIGEST_MISMATCH"))
        assertTrue(su.mutating.isEmpty())
    }

    @Test
    fun withoutAUsableCliTheVerifiedPackageIsHandedToTheManagerApp() {
        val su = magisk(21000)
        val h = Harness(su, module(coreZip(), requirements = REQ.copy(managers = REQ.managers + ("magisk" to ManagerRequirement(20400, "test")))))
        val c = h.controller
        assertEquals(InstallPhase.MODULE_AVAILABLE, c.evaluate("zairenkai_core").phase)
        c.prepare(); c.requestConfirmation()
        val r = c.confirm(true)
        assertEquals(InstallPhase.HANDOFF_READY, r.phase)
        assertEquals(listOf("com.topjohnwu.magisk"), r.handoff!!.packages)
        assertTrue(r.handoff!!.file.exists())
        assertTrue("a handoff is not an automated install", su.mutating.isEmpty())
    }

    @Test
    fun duplicateInstallsAreNotOfferedAndRuntimeHealthDecidesActive() {
        val su = ksu()
        su.stage("zairenkai_core", "v0.1.0", 100, active = true)
        val ok = Harness(su, module(coreZip()))
        assertEquals(InstallPhase.ACTIVE, ok.controller.evaluate("zairenkai_core").phase)
        assertEquals(InstallPhase.ACTIVE, ok.controller.prepare().phase) // no install path from ACTIVE
        val down = Harness(su, module(coreZip()), runtimeFailure = "zcored is installed but not answering")
        val r = down.controller.evaluate("zairenkai_core")
        assertEquals(InstallPhase.DEGRADED, r.phase)
        assertTrue(r.detail.contains("not answering"))
        assertTrue(su.mutating.isEmpty())
    }

    @Test
    fun pendingRebootIsRecognisedOnReturn() {
        val su = ksu()
        su.stage("zairenkai_core", "v0.1.0", 100)
        assertEquals(InstallPhase.INSTALL_PENDING_REBOOT, Harness(su, module(coreZip())).controller.evaluate("zairenkai_core").phase)
    }

    @Test
    fun removalUsesTheManagerAndIsVerified() {
        val su = ksu()
        su.stage("zairenkai_core", "v0.1.0", 100, active = true)
        val h = Harness(su, module(coreZip()))
        h.controller.evaluate("zairenkai_core")
        su.answer(q("/data/adb/ksud", "module", "uninstall", "zairenkai_core"), 0, "")
        // The manager says success but marks nothing: not reported as removed.
        assertEquals(InstallPhase.ACTIVE, h.controller.uninstall().phase)
        su.dirs.getValue("/data/adb/modules/zairenkai_core").add("remove")
        assertEquals(InstallPhase.REMOVAL_PENDING_REBOOT, h.controller.uninstall().phase)
        assertEquals(listOf(q("/data/adb/ksud", "module", "uninstall", "zairenkai_core")), su.mutating.distinct())

        // A failing uninstall keeps the state and says why.
        val su2 = ksu().also { it.stage("zairenkai_core", "v0.1.0", 100, active = true) }
        su2.answer(q("/data/adb/ksud", "module", "uninstall", "zairenkai_core"), 1, "error")
        val h2 = Harness(su2, module(coreZip()))
        h2.controller.evaluate("zairenkai_core")
        val r2 = h2.controller.uninstall()
        assertEquals(InstallPhase.ACTIVE, r2.phase)
        assertTrue(r2.detail.contains("exited 1"))

        // Magisk has no single-module removal command: the app hands off and writes nothing.
        val su3 = magisk(27000).also { it.stage("zairenkai_core", "v0.1.0", 100, active = true) }
        val h3 = Harness(su3, module(coreZip()))
        h3.controller.evaluate("zairenkai_core")
        val r3 = h3.controller.uninstall()
        assertEquals(listOf("com.topjohnwu.magisk"), r3.removalHandoff)
        assertTrue(su3.mutating.isEmpty())
    }

    @Test
    fun rootStatesStopTheFlowBeforeAnySuCall() {
        val su = ksu()
        val h = Harness(su, module(coreZip()), rootGranted = false)
        assertEquals(InstallPhase.ROOT_PERMISSION_REQUIRED, h.controller.evaluate("zairenkai_core").phase)
        assertTrue(su.calls.isEmpty())
        assertNull(h.controller.state.value.decision)
    }
}

/** The catalog build-components.sh produced from the real Core and FAS module zips. */
class SystemModuleCatalogContractTest {
    @Test
    fun theBuiltCatalogMatchesTheLockAndClaimsNoValidation() {
        val c = ModuleCatalog.parse(contract("system-modules/catalog.json"))
        assertEquals("zairenkai_core", c.base?.id)
        val lock = kotlinx.serialization.json.Json.parseToJsonElement(repoFile("integration/repository-lock.json"))
        val commits = (lock as kotlinx.serialization.json.JsonObject)["components"]!!.let { it as kotlinx.serialization.json.JsonArray }
            .associate { e ->
                val o = e as kotlinx.serialization.json.JsonObject
                (o["id"] as kotlinx.serialization.json.JsonPrimitive).content to (o["commit"] as kotlinx.serialization.json.JsonPrimitive).content
            }
        assertEquals(commits["zairenkai-core-module"], c.byId("zairenkai_core")!!.source.commit)
        assertEquals(commits["zairenkai-fas-module"], c.byId("zairenkai_fas")!!.source.commit)
        for (m in c.modules) {
            assertTrue("${m.id} claims a validation that never happened", m.validatedOn.isEmpty())
            assertTrue(m.sourceMatched.containsAll(listOf("customize.sh", "META-INF/com/google/android/update-binary")))
            assertFalse("no /system overlay", m.entries.keys.any { it.startsWith("system/") })
            assertEquals("arm64-v8a", m.requirements.abi)
        }
        assertEquals(listOf("fas_rs"), c.byId("zairenkai_fas")!!.requirements.conflicts.map { it.id })
    }
}

class SystemModuleAdapterTest {
    @Test
    fun unsafePathsAndIdsAreRefusedWithoutRunningAnything() {
        val su = ksu()
        val root = granted(su)
        for (f in ManagerFamily.entries) {
            val a = adapterFor(f, root)
            assertTrue(a.install("/data/user/0/x/cache/a b.zip") is com.zairenkaiprjctx.integration.systemmodule.ManagerResult.Failed)
            assertTrue(a.install("/tmp/x';reboot;'.zip") is com.zairenkaiprjctx.integration.systemmodule.ManagerResult.Failed)
            assertTrue(a.uninstall("../evil") is com.zairenkaiprjctx.integration.systemmodule.ManagerResult.Failed)
        }
        assertTrue(su.mutating.isEmpty())
    }
}
