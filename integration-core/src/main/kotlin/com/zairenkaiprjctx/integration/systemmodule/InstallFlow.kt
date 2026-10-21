package com.zairenkaiprjctx.integration.systemmodule

import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.exec.RootState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Installation phases (the names the feature specification uses). */
enum class InstallPhase {
    ROOT_NOT_AVAILABLE,
    ROOT_PERMISSION_REQUIRED,
    ROOT_PERMISSION_DENIED,
    MANAGER_DETECTION,
    COMPATIBILITY_CHECK,
    MODULE_AVAILABLE,

    /** Copying the module packaged in this APK to the staging file (no network is used). */
    DOWNLOAD_PENDING,
    VERIFYING_ARTIFACT,
    READY_TO_INSTALL,
    AWAITING_USER_CONFIRMATION,
    INSTALLING,
    INSTALL_FAILED,
    INSTALL_PENDING_REBOOT,
    VERIFYING_INSTALLED_STATE,
    ACTIVE,
    DEGRADED,
    UNSUPPORTED,

    /** The manager has no usable installer here: the verified zip waits for the manager app. */
    HANDOFF_READY,

    /** The manager marked the module for removal; it disappears on the next boot. */
    REMOVAL_PENDING_REBOOT,
}

/** What the app layer needs to open the manager app with the verified package. */
data class HandoffRequest(val file: File, val packages: List<String>, val acceptsZipIntent: Boolean, val managerName: String)

data class InstallFlowState(
    val phase: InstallPhase,
    val moduleId: String?,
    val manager: ManagerIdentity? = null,
    val decision: CompatibilityDecision? = null,
    val verified: PackageVerdict.Verified? = null,
    /** Why the flow is in this phase, in words. */
    val detail: String = "",
    /** The manager's installer output (bounded), for the result screen. */
    val log: String = "",
    val handoff: HandoffRequest? = null,
    /** Set for a manager that cannot remove a module from the CLI (Magisk). */
    val removalHandoff: List<String> = emptyList(),
)

/** Copies the packaged module to a file the root manager can read. */
fun interface ArtifactStore {
    /** Throws if the packaged artifact cannot be read. */
    fun stage(module: BundledModule): File
}

/** The component a module provides answers at runtime: Core health, FAS status. */
fun interface RuntimeProbe {
    /** Null when it answers; otherwise why not. */
    fun failure(module: BundledModule): String?
}

/**
 * Drives one module through detection, preflight, verification, installation and the
 * post-install checks. Every transition is explicit; device changes happen only in [confirm]
 * (install) and [uninstall], both after a user action. Calls block: run them off the main thread.
 */
class ModuleInstallController(
    private val root: RootShell,
    private val catalog: () -> ModuleCatalog?,
    private val detector: () -> ManagerDetection,
    private val facts: () -> DeviceFacts,
    private val probe: PreflightProbe,
    private val store: ArtifactStore,
    private val adapters: (ManagerFamily) -> ManagerAdapter,
    private val runtime: RuntimeProbe,
) {
    private val flow = MutableStateFlow(InstallFlowState(InstallPhase.MANAGER_DETECTION, null))
    val state: StateFlow<InstallFlowState> = flow.asStateFlow()

    private fun set(s: InstallFlowState): InstallFlowState = s.also { flow.value = it }
    private fun update(phase: InstallPhase, detail: String = flow.value.detail, f: (InstallFlowState) -> InstallFlowState = { it }) =
        set(f(flow.value.copy(phase = phase, detail = detail)))

    /** Re-reads everything for [moduleId] and settles in an offer, a refusal, or the installed state. */
    fun evaluate(moduleId: String): InstallFlowState {
        discardStaged()
        set(InstallFlowState(InstallPhase.MANAGER_DETECTION, moduleId))
        when (root.state) {
            RootState.UNAVAILABLE -> return update(InstallPhase.ROOT_NOT_AVAILABLE, "No su binary: this device is not rooted.")
            RootState.NOT_REQUESTED -> return update(InstallPhase.ROOT_PERMISSION_REQUIRED, "Root integration is off.")
            RootState.DENIED -> return update(InstallPhase.ROOT_PERMISSION_DENIED, "The root manager refused, or su did not return uid 0.")
            RootState.GRANTED -> Unit
        }
        val module = catalog()?.byId(moduleId)
            ?: return update(InstallPhase.UNSUPPORTED, "$moduleId is not packaged in this build.")
        val manager = when (val d = detector()) {
            ManagerDetection.RootNotDetected -> return update(InstallPhase.ROOT_NOT_AVAILABLE, "No su binary: this device is not rooted.")
            ManagerDetection.NotGranted -> return update(InstallPhase.ROOT_PERMISSION_REQUIRED, "Root integration is off.")
            ManagerDetection.Denied -> return update(InstallPhase.ROOT_PERMISSION_DENIED, "The root manager refused.")
            is ManagerDetection.Unsupported -> return update(InstallPhase.UNSUPPORTED, "Unsupported root manager (su -v: ${d.suVersion.ifEmpty { "no answer" }}). Supported: Magisk, KernelSU, APatch.")
            is ManagerDetection.VersionIncompatible -> return update(InstallPhase.UNSUPPORTED, "${d.identity.family.displayName} ${d.identity.versionName} is too old: needs ${d.required}.") { it.copy(manager = d.identity) }
            is ManagerDetection.Identified -> d.identity
        }
        update(InstallPhase.COMPATIBILITY_CHECK, "Checking the device and the installed modules.") { it.copy(manager = manager) }
        val installed = when (val i = probe.installed(module.id)) {
            is ManagerResult.Ok -> i.value
            is ManagerResult.Failed -> return update(InstallPhase.UNSUPPORTED, "Could not read the installed modules: ${i.detail}")
        }
        val ids = (probe.installedIds() as? ManagerResult.Ok)?.value.orEmpty()
        val device = facts().copy(kernelMachine = probe.machine(), kernelRelease = probe.kernelRelease())
        val decision = Preflight.decide(
            module, device, manager, installed, ids, probe::pathExists,
            dependencyActive = { dep -> (probe.installed(dep) as? ManagerResult.Ok)?.value?.active?.let { it.enabled && !it.removePending } == true },
        )
        update(InstallPhase.COMPATIBILITY_CHECK) { it.copy(decision = decision) }
        return when (decision.verdict) {
            Verdict.INCOMPATIBLE -> update(InstallPhase.UNSUPPORTED, decision.failures.joinToString("; ") { it.detail })
            Verdict.PENDING_REBOOT -> update(InstallPhase.INSTALL_PENDING_REBOOT, "${module.version} is installed; reboot to activate it.")
            Verdict.ALREADY_INSTALLED -> verifyInstalled()
            Verdict.COMPATIBLE, Verdict.EXPERIMENTAL -> update(InstallPhase.MODULE_AVAILABLE, "${module.name} ${module.version} can be installed.")
        }
    }

    private fun module(): BundledModule? = flow.value.decision?.module

    /** Copies and verifies the packaged artifact. Nothing on the device changes. */
    fun prepare(): InstallFlowState {
        val s = flow.value
        if (s.phase != InstallPhase.MODULE_AVAILABLE) return s
        val module = module() ?: return s
        update(InstallPhase.DOWNLOAD_PENDING, "Preparing the packaged module.")
        val file = try {
            store.stage(module)
        } catch (e: Exception) {
            return update(InstallPhase.INSTALL_FAILED, "The packaged module could not be read: ${e.message}")
        }
        update(InstallPhase.VERIFYING_ARTIFACT, "Verifying size, SHA-256, entries and installer scripts.")
        return when (val v = ModulePackageVerifier.verify(file, module)) {
            is PackageVerdict.Rejected -> {
                file.delete()
                update(InstallPhase.INSTALL_FAILED, "Verification failed (${v.fault}): ${v.detail}. Nothing was installed.")
            }
            is PackageVerdict.Verified -> update(InstallPhase.READY_TO_INSTALL, "Verified.") { it.copy(verified = v) }
        }
    }

    fun requestConfirmation(): InstallFlowState =
        if (flow.value.phase == InstallPhase.READY_TO_INSTALL) update(InstallPhase.AWAITING_USER_CONFIRMATION) else flow.value

    /** Before installation starts, the user can always back out; nothing has changed on the device. */
    fun cancel(): InstallFlowState {
        val s = flow.value
        if (s.phase !in CANCELLABLE) return s
        discardStaged()
        return update(InstallPhase.MODULE_AVAILABLE, "Installation cancelled. Nothing was changed.") { it.copy(verified = null, handoff = null) }
    }

    /**
     * The user confirmed. Experimental modules need [acknowledgedExperimental]. The artifact is
     * verified again immediately before the manager reads it.
     */
    fun confirm(acknowledgedExperimental: Boolean): InstallFlowState {
        val s = flow.value
        if (s.phase != InstallPhase.AWAITING_USER_CONFIRMATION) return s
        val decision = s.decision ?: return s
        val manager = s.manager ?: return s
        if (decision.verdict == Verdict.EXPERIMENTAL && !acknowledgedExperimental) {
            return update(InstallPhase.AWAITING_USER_CONFIRMATION, "Confirm that you accept an experimental module.")
        }
        val verified = s.verified ?: return update(InstallPhase.INSTALL_FAILED, "The package was not verified.")
        val again = ModulePackageVerifier.verify(verified.file, decision.module)
        if (again !is PackageVerdict.Verified) {
            verified.file.delete()
            return update(InstallPhase.INSTALL_FAILED, "The package changed after verification (${(again as PackageVerdict.Rejected).fault}). Nothing was installed.")
        }
        val adapter = adapters(manager.family)
        if (decision.install != InstallSupport.DIRECT) {
            return update(
                InstallPhase.HANDOFF_READY,
                "${manager.family.displayName} has no installer this app can call here. Open ${manager.family.displayName} to install the verified package.",
            ) { it.copy(handoff = HandoffRequest(verified.file, adapter.managerPackages, adapter.acceptsZipIntent, manager.family.displayName)) }
        }
        update(InstallPhase.INSTALLING, "${manager.family.displayName} is installing ${decision.module.id}.")
        return when (val r = adapter.install(verified.file.absolutePath)) {
            is ManagerResult.Failed -> {
                discardStaged()
                update(InstallPhase.INSTALL_FAILED, "${r.detail}. The manager's installer reported failure.") { it.copy(log = r.output) }
            }
            is ManagerResult.Ok -> {
                discardStaged()
                update(InstallPhase.VERIFYING_INSTALLED_STATE, "Checking the manager's module state.") { it.copy(log = r.value) }
                afterInstall(decision.module)
            }
        }
    }

    /** An exit status of 0 is not proof: the staged module must be there, with this version. */
    private fun afterInstall(module: BundledModule): InstallFlowState {
        val st = (probe.installed(module.id) as? ManagerResult.Ok)?.value
            ?: return update(InstallPhase.INSTALL_FAILED, "The installer finished, but the module state could not be read.")
        return when {
            st.staged?.versionCode == module.versionCode ->
                update(InstallPhase.INSTALL_PENDING_REBOOT, "Installed. ${module.name} ${module.version} activates after a reboot.")
            st.active?.versionCode == module.versionCode && !module.rebootRequired -> verifyInstalled()
            else -> update(InstallPhase.INSTALL_FAILED, "The installer reported success, but no ${module.id} ${module.version} is staged or installed.")
        }
    }

    /** After a reboot (or for an existing install): module state first, then the runtime. */
    fun verifyInstalled(): InstallFlowState {
        val module = module() ?: return flow.value
        update(InstallPhase.VERIFYING_INSTALLED_STATE, "Checking the installed module.")
        val st = when (val i = probe.installed(module.id)) {
            is ManagerResult.Ok -> i.value
            is ManagerResult.Failed -> return update(InstallPhase.DEGRADED, "The module state could not be read: ${i.detail}")
        }
        val active = st.active
        return when {
            st.staged != null -> update(InstallPhase.INSTALL_PENDING_REBOOT, "${st.staged.version} is staged; reboot to activate it.")
            active == null -> update(InstallPhase.MODULE_AVAILABLE, "${module.id} is not installed.")
            active.removePending -> update(InstallPhase.REMOVAL_PENDING_REBOOT, "${module.id} is marked for removal; reboot to finish.")
            !active.enabled -> update(InstallPhase.DEGRADED, "${module.id} ${active.version} is installed but disabled in the root manager.")
            active.versionCode != module.versionCode -> update(InstallPhase.DEGRADED, "${module.id} ${active.version} is installed; this build packages ${module.version}.")
            else -> when (val why = runtime.failure(module)) {
                null -> update(InstallPhase.ACTIVE, "${module.name} ${active.version} is installed, enabled and answering.")
                else -> update(InstallPhase.DEGRADED, "${module.name} ${active.version} is installed and enabled, but its service is not answering: $why")
            }
        }
    }

    /** Asks the manager to remove the module. Magisk cannot from the CLI: hand off to its app. */
    fun uninstall(): InstallFlowState {
        val s = flow.value
        if (s.phase !in REMOVABLE) return s
        val module = module() ?: return s
        val manager = s.manager ?: return s
        val adapter = adapters(manager.family)
        return when (val r = adapter.uninstall(module.id)) {
            is ManagerResult.Failed -> update(s.phase, r.detail) {
                it.copy(log = r.output, removalHandoff = if (manager.family == ManagerFamily.MAGISK) adapter.managerPackages else emptyList())
            }
            is ManagerResult.Ok -> {
                val st = (probe.installed(module.id) as? ManagerResult.Ok)?.value
                if (st?.active?.removePending == true || (st?.active == null && st?.staged == null)) {
                    update(InstallPhase.REMOVAL_PENDING_REBOOT, "${module.id} is marked for removal; reboot to finish.") { it.copy(log = r.value) }
                } else {
                    update(s.phase, "The manager reported success, but ${module.id} is not marked for removal.") { it.copy(log = r.value) }
                }
            }
        }
    }

    private fun discardStaged() {
        flow.value.verified?.file?.delete()
    }

    companion object {
        private val CANCELLABLE = setOf(
            InstallPhase.MODULE_AVAILABLE, InstallPhase.READY_TO_INSTALL,
            InstallPhase.AWAITING_USER_CONFIRMATION, InstallPhase.HANDOFF_READY,
        )
        private val REMOVABLE = setOf(InstallPhase.ACTIVE, InstallPhase.DEGRADED)
    }
}
