package com.zairenkaiprjctx.integration.systemmodule

import com.zairenkaiprjctx.integration.exec.CommandResult
import com.zairenkaiprjctx.integration.exec.RootOutcome
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.exec.RootState
import com.zairenkaiprjctx.integration.exec.ShellScript
import com.zairenkaiprjctx.integration.exec.ShellWord

/**
 * Root managers with a module installer this app knows. Each fact below was read in the
 * manager's own source (see docs/integration/system-module.md for the commits):
 * - Magisk: `su -v` prints `<version>:MAGISKSU`; `magisk -V` prints the daemon version code;
 *   `magisk --install-module ZIP` runs util_functions.sh `install_module` (fixed in v21.2).
 * - KernelSU and KernelSU-Next: `su -v` prints `<version>:KernelSU`; `/data/adb/ksud module
 *   install|uninstall|list` (list prints JSON).
 * - APatch: `su -v` prints `<version>:APatch`; `/data/adb/apd module install|uninstall|list`.
 * All three install into /data/adb/modules_update/<id> and activate it on the next boot.
 */
enum class ManagerFamily(val displayName: String, val suTag: String) {
    MAGISK("Magisk", "MAGISKSU"),

    /** KernelSU and its forks with the same ksud CLI (KernelSU-Next prints the same su tag). */
    KERNELSU("KernelSU", "KernelSU"),
    APATCH("APatch", "APatch"),
}

/** How this app may install a module with the detected manager. */
enum class InstallSupport {
    /** The manager's own CLI installer, verified present on this device. */
    DIRECT,

    /** Only the manager app can install: the app verifies the zip and hands it over. */
    HANDOFF_ONLY,

    /** The manager answered, but its installer could not be confirmed. */
    UNVERIFIED,
}

data class ManagerIdentity(
    val family: ManagerFamily,
    val versionName: String,
    val versionCode: Long?,
    val install: InstallSupport,
    /** What was observed, in order, for the UI and diagnostics. */
    val evidence: List<String>,
)

sealed interface ManagerDetection {
    /** No su: the device is not rooted (or su cannot be started). */
    data object RootNotDetected : ManagerDetection

    /** su exists but the user has not enabled root integration in the app. */
    data object NotGranted : ManagerDetection

    /** The root manager refused, or su did not return uid 0. */
    data object Denied : ManagerDetection

    /** Root works, but the manager is not one this app has an installer adapter for. */
    data class Unsupported(val suVersion: String, val evidence: List<String>) : ManagerDetection

    /** A supported manager whose version is too old for its installer. */
    data class VersionIncompatible(val identity: ManagerIdentity, val required: String) : ManagerDetection

    data class Identified(val identity: ManagerIdentity) : ManagerDetection
}

/** Module ids as Magisk, ksud and apd accept them (ksud `validate_module_id`). */
object ModuleIds {
    private val ID = Regex("^[a-zA-Z][a-zA-Z0-9._-]{1,63}$")

    @JvmStatic
    fun isValid(id: String): Boolean = ID.matches(id)
}

/** A module as the manager's on-disk state shows it. */
data class ModuleRecord(
    val id: String,
    val version: String?,
    val versionCode: Long?,
    val enabled: Boolean,
    /** Installed into modules_update; the manager applies it on the next boot. */
    val updatePending: Boolean,
    val removePending: Boolean,
)

data class InstalledState(
    /** /data/adb/modules/<id>: the version that is (or will stay) active. */
    val active: ModuleRecord?,
    /** /data/adb/modules_update/<id>: a staged install waiting for a reboot. */
    val staged: ModuleRecord?,
)

sealed interface ManagerResult<out T> {
    data class Ok<T>(val value: T) : ManagerResult<T>
    data class Failed(val detail: String, val output: String = "") : ManagerResult<Nothing>
}

/** `key=value` lines of module.prop (Magisk's grep_prop and ksud's reader accept the same). */
object ModuleProp {
    fun parse(text: String): Map<String, String> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
        .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
}

private fun word(text: String): ShellWord = requireNotNull(ShellWord.arg(text)) { "invalid shell word $text" }

private fun script(vararg words: String) = ShellScript(words.map(::word))

/** A script whose last word is a path or id from outside this file: null if it is not a safe word. */
private fun scriptWith(value: String, vararg fixed: String): ShellScript? =
    ShellWord.arg(value)?.let { ShellScript(fixed.map(::word) + it) }

private val UNSAFE = ManagerResult.Failed("the path or id contains characters that cannot be passed safely")

/** The root manager's module installer. One implementation per manager; no generic command. */
interface ManagerAdapter {
    val family: ManagerFamily

    /** Installs the zip with the manager's own installer. Requires [InstallSupport.DIRECT]. */
    fun install(zipPath: String): ManagerResult<String>

    /** Marks the module for removal with the manager's own command, or says it cannot. */
    fun uninstall(id: String): ManagerResult<String>

    /** The manager app that can finish an install or removal itself, for a handoff. */
    val managerPackages: List<String>

    /** Whether that app accepts `ACTION_VIEW` on a `content://` zip (KernelSU's manager does). */
    val acceptsZipIntent: Boolean
}

/**
 * Reads module state from the manager's documented layout. Magisk (util_functions.sh), ksud
 * and apd (defs.rs) all use /data/adb/modules/<id> and /data/adb/modules_update/<id> with the
 * marker files `disable`, `remove` and `update`. Only `cat` and `ls` run here: nothing is written.
 */
class ModuleStateReader(private val root: RootShell) {
    private fun prop(path: String): Map<String, String>? =
        when (val o = root.run(scriptWith(path, "cat") ?: return null, READ_TIMEOUT_MS)) {
            is RootOutcome.Finished -> if (o.result.exitCode == 0) ModuleProp.parse(o.result.stdout) else null
            else -> null
        }

    private fun markers(dir: String): Set<String>? =
        when (val o = root.run(scriptWith(dir, "ls", "-a") ?: return null, READ_TIMEOUT_MS)) {
            is RootOutcome.Finished -> if (o.result.exitCode == 0) o.result.stdout.lines().map { it.trim() }.toSet() else null
            else -> null
        }

    private fun record(id: String, dir: String, update: Boolean): ModuleRecord? {
        val p = prop("$dir/module.prop") ?: return null
        val m = markers(dir).orEmpty()
        return ModuleRecord(
            id = p["id"] ?: id,
            version = p["version"],
            versionCode = p["versionCode"]?.toLongOrNull(),
            enabled = "disable" !in m,
            updatePending = update || "update" in m,
            removePending = "remove" in m,
        )
    }

    fun read(id: String): ManagerResult<InstalledState> {
        if (!ModuleIds.isValid(id)) return ManagerResult.Failed("invalid module id")
        if (root.state != RootState.GRANTED) return ManagerResult.Failed("root integration is not enabled")
        return ManagerResult.Ok(
            InstalledState(
                active = record(id, "$MODULES/$id", update = false),
                staged = record(id, "$MODULES_UPDATE/$id", update = true),
            ),
        )
    }

    /** Ids of every installed module (for conflict checks), from `ls` of the module directory. */
    fun installedIds(): ManagerResult<Set<String>> = when (val o = root.run(script("ls", MODULES), READ_TIMEOUT_MS)) {
        is RootOutcome.Finished ->
            if (o.result.exitCode == 0) ManagerResult.Ok(o.result.stdout.lines().map { it.trim() }.filter(ModuleIds::isValid).toSet())
            else ManagerResult.Ok(emptySet()) // no module directory yet
        is RootOutcome.NotGranted -> ManagerResult.Failed("root integration is not enabled")
        is RootOutcome.Failed -> ManagerResult.Failed(o.detail)
    }

    companion object {
        const val MODULES = "/data/adb/modules"
        const val MODULES_UPDATE = "/data/adb/modules_update"
        const val READ_TIMEOUT_MS = 10_000L
    }
}

private fun outcome(o: RootOutcome, what: String): ManagerResult<String> = when (o) {
    is RootOutcome.NotGranted -> ManagerResult.Failed("root integration is not enabled (${o.state})")
    is RootOutcome.Failed -> ManagerResult.Failed("$what: ${o.detail}")
    is RootOutcome.Finished -> finished(o.result, what)
}

private fun finished(r: CommandResult, what: String): ManagerResult<String> {
    val out = (r.stdout + if (r.stderr.isBlank()) "" else "\n" + r.stderr).trim().takeLast(MAX_LOG)
    return if (r.exitCode == 0) ManagerResult.Ok(out) else ManagerResult.Failed("$what exited ${r.exitCode}", out)
}

private const val MAX_LOG = 8_000
const val INSTALL_TIMEOUT_MS = 180_000L

/** `magisk --install-module <zip>`. Magisk has no single-module removal command. */
class MagiskAdapter(private val root: RootShell) : ManagerAdapter {
    override val family = ManagerFamily.MAGISK
    override val managerPackages = listOf("com.topjohnwu.magisk")
    override val acceptsZipIntent = false

    override fun install(zipPath: String): ManagerResult<String> {
        val s = scriptWith(zipPath, "magisk", "--install-module") ?: return UNSAFE
        return outcome(root.run(s, INSTALL_TIMEOUT_MS), "magisk --install-module")
    }

    override fun uninstall(id: String): ManagerResult<String> =
        ManagerResult.Failed("Magisk has no command to remove one module; remove it in the Magisk app (Modules).")
}

/** ksud for KernelSU and KernelSU-Next. */
class KernelSuAdapter(private val root: RootShell) : ManagerAdapter {
    override val family = ManagerFamily.KERNELSU
    override val managerPackages = listOf("me.weishu.kernelsu", "com.rifsxd.ksunext")
    override val acceptsZipIntent = true

    override fun install(zipPath: String): ManagerResult<String> {
        val s = scriptWith(zipPath, KSUD, "module", "install") ?: return UNSAFE
        return outcome(root.run(s, INSTALL_TIMEOUT_MS), "ksud module install")
    }

    override fun uninstall(id: String): ManagerResult<String> {
        if (!ModuleIds.isValid(id)) return ManagerResult.Failed("invalid module id")
        val s = scriptWith(id, KSUD, "module", "uninstall") ?: return UNSAFE
        return outcome(root.run(s, INSTALL_TIMEOUT_MS), "ksud module uninstall")
    }

    companion object {
        const val KSUD = "/data/adb/ksud"
    }
}

/** apd for APatch. */
class APatchAdapter(private val root: RootShell) : ManagerAdapter {
    override val family = ManagerFamily.APATCH
    override val managerPackages = listOf("me.bmax.apatch")
    override val acceptsZipIntent = false

    override fun install(zipPath: String): ManagerResult<String> {
        val s = scriptWith(zipPath, APD, "module", "install") ?: return UNSAFE
        return outcome(root.run(s, INSTALL_TIMEOUT_MS), "apd module install")
    }

    override fun uninstall(id: String): ManagerResult<String> {
        if (!ModuleIds.isValid(id)) return ManagerResult.Failed("invalid module id")
        val s = scriptWith(id, APD, "module", "uninstall") ?: return UNSAFE
        return outcome(root.run(s, INSTALL_TIMEOUT_MS), "apd module uninstall")
    }

    companion object {
        const val APD = "/data/adb/apd"
    }
}

fun adapterFor(family: ManagerFamily, root: RootShell): ManagerAdapter = when (family) {
    ManagerFamily.MAGISK -> MagiskAdapter(root)
    ManagerFamily.KERNELSU -> KernelSuAdapter(root)
    ManagerFamily.APATCH -> APatchAdapter(root)
}

/**
 * Identifies the manager from two independent sources: the su binary's own version tag
 * (`su -v`, `su -V`) and the manager's CLI answering on its fixed path. A family is accepted only
 * when both agree; nothing is inferred from a package name, a file path or a property alone.
 */
class RootManagerDetector(private val root: RootShell) {
    private fun run(vararg words: String): CommandResult? =
        (root.run(script(*words), DETECT_TIMEOUT_MS) as? RootOutcome.Finished)?.result

    fun detect(): ManagerDetection {
        when (root.state) {
            RootState.UNAVAILABLE -> return ManagerDetection.RootNotDetected
            RootState.NOT_REQUESTED -> return ManagerDetection.NotGranted
            RootState.DENIED -> return ManagerDetection.Denied
            RootState.GRANTED -> Unit
        }
        val evidence = mutableListOf<String>()
        val suV = run("su", "-v")?.takeIf { it.exitCode == 0 }?.stdout?.trim().orEmpty()
        evidence += "su -v: ${suV.ifEmpty { "(no answer)" }}"
        val suCode = run("su", "-V")?.takeIf { it.exitCode == 0 }?.stdout?.trim()?.toLongOrNull()
        evidence += "su -V: ${suCode ?: "(no answer)"}"
        val tag = suV.substringAfterLast(':', "")
        val family = ManagerFamily.entries.firstOrNull { it.suTag == tag }
            ?: return ManagerDetection.Unsupported(suV, evidence)
        val name = suV.substringBeforeLast(':').substringBefore(':')
        return when (family) {
            ManagerFamily.MAGISK -> magisk(name, suCode, evidence)
            ManagerFamily.KERNELSU -> cliManager(family, KernelSuAdapter.KSUD, name, suCode, evidence)
            ManagerFamily.APATCH -> cliManager(family, APatchAdapter.APD, name, suCode, evidence)
        }
    }

    private fun magisk(name: String, suCode: Long?, evidence: MutableList<String>): ManagerDetection {
        val daemon = run("magisk", "-V")?.takeIf { it.exitCode == 0 }?.stdout?.trim()?.toLongOrNull()
        evidence += "magisk -V: ${daemon ?: "(no answer)"}"
        if (daemon == null || (suCode != null && suCode != daemon)) {
            return ManagerDetection.Unsupported("$name:MAGISKSU", evidence + "the magisk CLI did not confirm the su tag")
        }
        val min = MAGISK_INSTALL_MODULE_MIN
        val id = ManagerIdentity(ManagerFamily.MAGISK, name, daemon, if (daemon >= min) InstallSupport.DIRECT else InstallSupport.HANDOFF_ONLY, evidence)
        return if (daemon < MAGISK_MODULE_MIN) ManagerDetection.VersionIncompatible(id, "Magisk v20.4 (20400) or newer") else ManagerDetection.Identified(id)
    }

    private fun cliManager(family: ManagerFamily, cli: String, name: String, suCode: Long?, evidence: MutableList<String>): ManagerDetection {
        val list = run(cli, "module", "list")
        val listOk = list != null && list.exitCode == 0 && list.stdout.trimStart().startsWith("[")
        evidence += "$cli module list: ${if (listOk) "JSON" else "no answer (exit ${list?.exitCode ?: "-"})"}"
        if (!listOk) {
            return ManagerDetection.Unsupported("$name:${family.suTag}", evidence + "the $cli CLI did not confirm the su tag")
        }
        val help = run(cli, "module", "--help")
        val hasInstall = help != null && help.exitCode == 0 && Regex("^\\s*install\\b", RegexOption.MULTILINE).containsMatchIn(help.stdout)
        evidence += "$cli module --help: ${if (hasInstall) "lists install" else "no install subcommand"}"
        return ManagerDetection.Identified(
            ManagerIdentity(family, name, suCode, if (hasInstall) InstallSupport.DIRECT else InstallSupport.UNVERIFIED, evidence),
        )
    }

    companion object {
        const val DETECT_TIMEOUT_MS = 10_000L

        /** Magisk v21.2 fixed `magisk --install-module` (Magisk docs/changes.md). */
        const val MAGISK_INSTALL_MODULE_MIN = 21200L

        /** The modules' update-binary refuses Magisk older than v20.4. */
        const val MAGISK_MODULE_MIN = 20400L
    }
}
