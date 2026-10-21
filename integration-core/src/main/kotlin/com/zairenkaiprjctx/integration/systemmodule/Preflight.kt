package com.zairenkaiprjctx.integration.systemmodule

import com.zairenkaiprjctx.integration.exec.RootOutcome
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.exec.ShellScript
import com.zairenkaiprjctx.integration.exec.ShellWord

/** Device facts from public APIs (filled in by :app) and from root reads ([PreflightProbe]). */
data class DeviceFacts(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val codename: String,
    val socManufacturer: String?,
    val socModel: String?,
    val sdkInt: Int,
    val supportedAbis: List<String>,
    /** `uname -m` / `uname -r` as root, or null when they could not be read. */
    val kernelMachine: String?,
    val kernelRelease: String?,
    /** Free bytes on /data (the app's files directory is on the same partition). */
    val freeDataBytes: Long,
)

enum class CheckStatus { PASS, WARN, FAIL, INFO }

data class PreflightCheck(val id: String, val status: CheckStatus, val detail: String)

enum class Verdict {
    /** Every requirement holds and the module has a validation record for this device. */
    COMPATIBLE,

    /** Every requirement holds, but nobody has validated the module on this device. */
    EXPERIMENTAL,

    /** A mandatory requirement is unmet: installation is refused. */
    INCOMPATIBLE,

    /** The same version is already active and enabled. */
    ALREADY_INSTALLED,

    /** The same version is staged; the manager applies it on the next boot. */
    PENDING_REBOOT,
}

data class CompatibilityDecision(
    val module: BundledModule,
    val verdict: Verdict,
    val checks: List<PreflightCheck>,
    val install: InstallSupport,
    val upgradeFrom: String?,
) {
    val failures: List<PreflightCheck> get() = checks.filter { it.status == CheckStatus.FAIL }
}

/** Root reads the preflight needs. Read-only: `uname`, `ls -d`, and the module state reader. */
class PreflightProbe(private val root: RootShell, private val state: ModuleStateReader) {
    private fun one(vararg words: String): String? {
        val s = ShellScript(words.map { requireNotNull(ShellWord.arg(it)) })
        val o = root.run(s, ModuleStateReader.READ_TIMEOUT_MS) as? RootOutcome.Finished ?: return null
        return o.result.stdout.trim().takeIf { o.result.exitCode == 0 && it.isNotEmpty() }
    }

    fun machine(): String? = one("uname", "-m")
    fun kernelRelease(): String? = one("uname", "-r")
    fun pathExists(path: String): Boolean = ShellWord.arg(path) != null && one("ls", "-d", path) != null
    fun installed(id: String) = state.read(id)
    fun installedIds() = state.installedIds()
}

private val MANAGER_KEYS = mapOf(ManagerFamily.MAGISK to "magisk", ManagerFamily.KERNELSU to "kernelsu", ManagerFamily.APATCH to "apatch")

/** arm64 as uname reports it. */
private fun archMatches(required: String, machine: String) = when (required) {
    "arm64" -> machine == "aarch64" || machine == "arm64"
    else -> machine == required
}

/**
 * Decides whether [module] may be installed here. Pure: every input was gathered beforehand,
 * so the decision is testable and nothing on the device changes while it is made.
 */
object Preflight {
    /** The zip, its staged copy and the extracted module must fit, with a margin. */
    const val STORAGE_FACTOR = 4

    fun decide(
        module: BundledModule,
        device: DeviceFacts,
        manager: ManagerIdentity,
        installed: InstalledState,
        installedIds: Set<String>,
        pathExists: (String) -> Boolean,
        dependencyActive: (String) -> Boolean,
    ): CompatibilityDecision {
        val r = module.requirements
        val checks = mutableListOf<PreflightCheck>()
        fun check(id: String, ok: Boolean, pass: String, fail: String) {
            checks += PreflightCheck(id, if (ok) CheckStatus.PASS else CheckStatus.FAIL, if (ok) pass else fail)
        }

        checks += PreflightCheck(
            "device", CheckStatus.INFO,
            "${device.manufacturer} ${device.model} (${device.brand}/${device.codename}), SoC ${listOfNotNull(device.socManufacturer, device.socModel).joinToString(" ").ifEmpty { "not reported" }}",
        )
        check("abi", r.abi in device.supportedAbis, "${r.abi} supported", "needs ${r.abi}; this device supports ${device.supportedAbis.joinToString()}")
        when (val m = device.kernelMachine) {
            null -> checks += PreflightCheck("arch", CheckStatus.WARN, "uname -m could not be read; the module's installer checks the architecture itself")
            else -> check("arch", archMatches(r.arch, m), "kernel $m (${device.kernelRelease ?: "release unknown"})", "needs ${r.arch}; kernel reports $m")
        }
        check("api", device.sdkInt >= r.minApi, "API ${device.sdkInt}", "needs API ${r.minApi} or newer; this device has API ${device.sdkInt}")

        val req = r.managers[MANAGER_KEYS.getValue(manager.family)]
        check("manager", req != null, "${manager.family.displayName} ${manager.versionName}", "${manager.family.displayName} is not a supported manager for ${module.id}")
        if (req?.minVersionCode != null) {
            val code = manager.versionCode
            check(
                "manager_version", code != null && code >= req.minVersionCode,
                "version code $code ≥ ${req.minVersionCode}",
                "needs version code ${req.minVersionCode} or newer; found ${code ?: "unknown"}",
            )
        }
        checks += when (manager.install) {
            InstallSupport.DIRECT -> PreflightCheck("installer", CheckStatus.PASS, "the manager's own installer (${manager.family.displayName} CLI)")
            InstallSupport.HANDOFF_ONLY -> PreflightCheck("installer", CheckStatus.WARN, "this manager version has no usable CLI installer: the verified zip is handed to the manager app")
            InstallSupport.UNVERIFIED -> PreflightCheck("installer", CheckStatus.WARN, "the CLI installer could not be confirmed: the verified zip is handed to the manager app")
        }

        for (p in r.requiredPaths) check("path:$p", pathExists(p), "$p present", "$p is missing: the module's runtime needs it")
        for (d in r.dependsOn) check("depends:$d", dependencyActive(d), "$d active", "requires module $d, which is not active")
        for (c in r.conflicts) {
            check("conflict:${c.id}", c.id !in installedIds, "no ${c.id} module", "module ${c.id} is installed: ${c.reason}")
        }
        val mounts = module.entries.keys.any { it.startsWith("system/") }
        checks += PreflightCheck(
            "overlay", CheckStatus.INFO,
            if (mounts) "the module overlays /system: KernelSU and APatch may need a metamodule" else "no /system overlay: no mount or metamodule needed",
        )
        val need = module.size * STORAGE_FACTOR
        check("storage", device.freeDataBytes >= need, "${device.freeDataBytes / 1_048_576} MiB free", "needs ${need / 1_048_576} MiB free on /data; ${device.freeDataBytes / 1_048_576} MiB available")

        var upgradeFrom: String? = null
        val active = installed.active
        val staged = installed.staged
        var settled: Verdict? = null
        when {
            staged != null && staged.versionCode == module.versionCode ->
                settled = Verdict.PENDING_REBOOT.also { checks += PreflightCheck("existing", CheckStatus.INFO, "${module.version} is installed and waits for a reboot") }
            staged != null ->
                checks += PreflightCheck("existing", CheckStatus.FAIL, "another version (${staged.version}) is staged: reboot first")
            active == null -> checks += PreflightCheck("existing", CheckStatus.PASS, "not installed")
            active.removePending ->
                checks += PreflightCheck("existing", CheckStatus.FAIL, "${active.version} is marked for removal: reboot first")
            active.versionCode == null ->
                checks += PreflightCheck("existing", CheckStatus.FAIL, "an installed ${module.id} has no readable versionCode")
            active.versionCode > module.versionCode ->
                checks += PreflightCheck("existing", CheckStatus.FAIL, "${active.version} is newer than ${module.version}: downgrades are refused")
            active.versionCode == module.versionCode && active.enabled ->
                settled = Verdict.ALREADY_INSTALLED.also { checks += PreflightCheck("existing", CheckStatus.INFO, "${module.version} is already installed") }
            active.versionCode == module.versionCode ->
                checks += PreflightCheck("existing", CheckStatus.WARN, "${module.version} is installed but disabled in the manager; enable it there")
            else -> {
                upgradeFrom = active.version
                checks += PreflightCheck("existing", CheckStatus.PASS, "upgrade from ${active.version}")
            }
        }
        if (module.rebootRequired) checks += PreflightCheck("reboot", CheckStatus.INFO, "a reboot activates the module")

        val validated = device.codename in module.validatedOn
        checks += PreflightCheck(
            "validation",
            if (validated) CheckStatus.PASS else CheckStatus.WARN,
            if (validated) "validated on ${device.codename}" else "not validated on any device yet (experimental)",
        )
        val disabledSameVersion = active != null && active.versionCode == module.versionCode && !active.enabled && staged == null
        val verdict = when {
            checks.any { it.status == CheckStatus.FAIL } -> Verdict.INCOMPATIBLE
            settled != null -> settled
            disabledSameVersion -> Verdict.INCOMPATIBLE.also { checks += PreflightCheck("existing_disabled", CheckStatus.FAIL, "reinstalling would not change a module disabled in the manager") }
            validated -> Verdict.COMPATIBLE
            else -> Verdict.EXPERIMENTAL
        }
        return CompatibilityDecision(module, verdict, checks, manager.install, upgradeFrom)
    }
}
