package com.zairenkaiprjctx.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zairenkaiprjctx.app.AppContainer
import com.zairenkaiprjctx.integration.systemmodule.BundledModule
import com.zairenkaiprjctx.integration.systemmodule.InstallFlowState
import com.zairenkaiprjctx.integration.systemmodule.ModuleInstallController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The System Module screen and the Home offer. Every call into a controller runs on IO, one at
 * a time: an installation cannot overlap another step. Nothing runs before root is granted.
 */
class SystemModuleViewModel(private val c: AppContainer) : ViewModel() {
    private val mutex = Mutex()
    private val busyFlow = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = busyFlow.asStateFlow()
    private val declinedFlow = MutableStateFlow(c.moduleOfferDeclined)
    val declined: StateFlow<Boolean> = declinedFlow.asStateFlow()
    private val noticeFlow = MutableStateFlow<String?>(null)

    /** Result of the last handoff to a manager app, for the screen. */
    val notice: StateFlow<String?> = noticeFlow.asStateFlow()

    val modules: List<BundledModule> get() = c.moduleCatalog?.modules.orEmpty().sortedBy { it.role != "base" }
    val packaged: Boolean get() = c.moduleCatalog != null

    fun state(id: String): StateFlow<InstallFlowState>? = c.moduleInstallers[id]?.state

    private fun run(id: String, step: ModuleInstallController.() -> Unit) {
        val ctl = c.moduleInstallers[id] ?: return
        viewModelScope.launch(Dispatchers.IO) {
            mutex.withLock {
                busyFlow.value = true
                try {
                    ctl.step()
                } finally {
                    busyFlow.value = false
                }
            }
        }
    }

    /** Re-checks every packaged module (only with root granted: otherwise no su call is made). */
    fun evaluateAll() {
        // Without granted root the controllers settle in a ROOT_* phase without any su call.
        modules.forEach { m -> run(m.id) { evaluate(m.id) } }
    }

    fun evaluate(id: String) = run(id) { evaluate(id) }
    fun prepare(id: String) = run(id) { prepare() }
    fun requestConfirmation(id: String) = run(id) { requestConfirmation() }
    fun confirm(id: String, acknowledgedExperimental: Boolean) = run(id) { confirm(acknowledgedExperimental) }
    fun cancel(id: String) = run(id) { cancel() }
    fun uninstall(id: String) = run(id) { uninstall() }

    fun handOff(id: String) {
        val h = c.moduleInstallers[id]?.state?.value?.handoff ?: return
        val r = c.handoff.handOff(h.file, h.packages, h.acceptsZipIntent)
        noticeFlow.value = r.detail
    }

    fun openManager(packages: List<String>) {
        val pkg = c.handoff.installedManager(packages)
        noticeFlow.value = if (pkg != null && c.handoff.open(pkg)) null else "The manager app is not installed or is hidden (${packages.joinToString()})."
    }

    fun decline() {
        c.moduleOfferDeclined = true
        declinedFlow.value = true
    }

    fun reconsider() {
        c.moduleOfferDeclined = false
        declinedFlow.value = false
        evaluateAll()
    }
}
