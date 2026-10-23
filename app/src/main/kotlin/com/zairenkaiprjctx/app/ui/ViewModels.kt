// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zairenkaiprjctx.app.AppContainer
import com.zairenkaiprjctx.app.R
import com.zairenkaiprjctx.integration.afid.AfidCatalog
import com.zairenkaiprjctx.integration.afid.AfidDevice
import com.zairenkaiprjctx.integration.core.ActionResult
import com.zairenkaiprjctx.integration.core.CoreCall
import com.zairenkaiprjctx.integration.core.CoreHealth
import com.zairenkaiprjctx.integration.core.CoreModule
import com.zairenkaiprjctx.integration.core.CoreTelemetry
import com.zairenkaiprjctx.integration.core.TuningTelemetry
import com.zairenkaiprjctx.integration.core.CoreOperation
import com.zairenkaiprjctx.integration.core.FasProfileName
import com.zairenkaiprjctx.integration.exec.RootState
import com.zairenkaiprjctx.integration.fas.FasCall
import com.zairenkaiprjctx.integration.fas.FasCommand
import com.zairenkaiprjctx.integration.fas.FasStatus
import com.zairenkaiprjctx.integration.registry.RegistrySnapshot
import com.zairenkaiprjctx.integration.thermaltools.ThermalSnapshot
import com.zairenkaiprjctx.integration.thermaltools.ThermalToolsResult
import com.zairenkaiprjctx.integration.zart.Obs
import com.zairenkaiprjctx.integration.zart.PackageDexopt
import com.zairenkaiprjctx.integration.zart.PackageNames
import com.zairenkaiprjctx.integration.zart.ZartReport
import com.zairenkaiprjctx.integration.zart.ZartResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Describes a failed call in words for the UI; never turns a failure into a value. */
fun <T> CoreCall<T>.describeFailure(): String = when (this) {
    is CoreCall.Ok -> ""
    is CoreCall.Error -> "Core refused the request ($code): $message"
    CoreCall.NotInstalled -> "Zairenkai-Core is not installed."
    is CoreCall.Unreachable -> "zcored is not answering: $detail"
    is CoreCall.NoRoot -> detail
    is CoreCall.Failed -> detail
}

fun <T> FasCall<T>.describeFailure(): String = when (this) {
    is FasCall.Ok -> ""
    is FasCall.Rejected -> "zfasd rejected the request: $message"
    FasCall.NotInstalled -> "Zairenkai-FAS is not installed."
    is FasCall.Unreachable -> "zfasd is not answering: $detail"
    is FasCall.NoRoot -> detail
    is FasCall.Failed -> detail
}

class MainViewModel(private val c: AppContainer) : ViewModel() {
    val registry: StateFlow<RegistrySnapshot> = c.registry.snapshot
    private val afidState = MutableStateFlow<Result<AfidCatalog>?>(null)

    /** The packaged AFID asset, parsed off the main thread. */
    val afid: StateFlow<Result<AfidCatalog>?> = afidState.asStateFlow()
    private val rootStateFlow = MutableStateFlow(c.root.state)
    val rootState: StateFlow<RootState> = rootStateFlow.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) { afidState.value = c.afid }
        viewModelScope.launch {
            // A stored consent is re-checked once per process: the root manager may have revoked it.
            if (c.root.state == RootState.GRANTED) {
                rootStateFlow.value = withContext(Dispatchers.IO) { c.root.request() }
            }
            c.registry.refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch { c.registry.refresh() }
    }

    /** Explicit user action: shows the root manager's prompt. */
    fun requestRoot() {
        viewModelScope.launch {
            rootStateFlow.value = withContext(Dispatchers.IO) { c.root.request() }
            c.registry.refresh()
        }
    }

    fun revokeRoot() {
        c.root.revoke()
        rootStateFlow.value = c.root.state
        viewModelScope.launch { c.registry.refresh() }
    }
}

/** Localised text built in the UI layer from a resource id and arguments. */
data class UiText(@androidx.annotation.StringRes val id: Int, val args: List<Any> = emptyList())

sealed interface ActionState {
    data object Idle : ActionState
    data class Working(val label: UiText) : ActionState
    data class Preview(val label: UiText, val op: CoreOperation, val result: ActionResult) : ActionState

    /** Direct zfasd command waiting for the user's confirmation (no Core policy preview exists). */
    data class ConfirmDirect(val label: UiText, val run: () -> FasCall<String>, val requestedProfile: String? = null) : ActionState

    /**
     * The provider's answer ([succeeded]: Core reported applied, or zfasd accepted the command) and,
     * for a profile change, what zfasd reported when read back afterwards ([profile]).
     */
    data class Done(val label: UiText, val message: String, val succeeded: Boolean, val profile: ProfileCheck? = null) : ActionState
    data class Error(val label: UiText, val detail: String) : ActionState
}

/** A profile change read back from zfasd: [reported] is null when the status could not be read. */
data class ProfileCheck(val requested: String, val reported: String?) {
    val confirmed: Boolean get() = reported == requested
}

/** What zfasd reports after a profile request; only zfasd's own status can confirm it. */
fun profileCheck(requested: String?, status: FasCall<FasStatus>?): ProfileCheck? =
    requested?.let { ProfileCheck(it, (status as? FasCall.Ok)?.value?.profile) }

class PerformanceViewModel(private val c: AppContainer) : ViewModel() {
    private val health = MutableStateFlow<CoreCall<CoreHealth>?>(null)
    val coreHealth: StateFlow<CoreCall<CoreHealth>?> = health.asStateFlow()
    private val fasState = MutableStateFlow<FasCall<FasStatus>?>(null)
    val fasStatus: StateFlow<FasCall<FasStatus>?> = fasState.asStateFlow()
    private val action = MutableStateFlow<ActionState>(ActionState.Idle)
    val actionState: StateFlow<ActionState> = action.asStateFlow()
    private val tuningState = MutableStateFlow<CoreCall<TuningTelemetry>?>(null)

    /** ztune telemetry through Core's `tuning` method; asked only while Core answers. */
    val tuning: StateFlow<CoreCall<TuningTelemetry>?> = tuningState.asStateFlow()

    /** Battery saver as Android reports it now (PowerManager.isPowerSaveMode). */
    val powerSave: Boolean get() = c.platform.powerSaveMode()

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val h = c.core.health()
            health.value = h
            fasState.value = c.fas.status()
            tuningState.value = if (h is CoreCall.Ok) c.core.tuning() else null
        }
    }

    /** Core is the coordination authority: when it answers, every action goes through it. */
    val coreAvailable: Boolean get() = health.value is CoreCall.Ok

    private fun label(op: CoreOperation) = when (op) {
        is CoreOperation.FasSetProfile -> UiText(R.string.action_profile, listOf(op.profile.wire))
        is CoreOperation.FasSetEnabled -> UiText(if (op.enabled) R.string.fas_enable else R.string.fas_disable)
        is CoreOperation.SetLowPowerMode -> UiText(if (op.enabled) R.string.battery_saver_on else R.string.battery_saver_off)
    }

    /** One operation at a time: a request while another is running is ignored. */
    private val busy: Boolean get() = action.value is ActionState.Working

    private fun requestedProfile(op: CoreOperation) = (op as? CoreOperation.FasSetProfile)?.profile?.wire

    fun preview(op: CoreOperation) {
        if (busy) return
        val l = label(op)
        action.value = ActionState.Working(l)
        viewModelScope.launch(Dispatchers.IO) {
            action.value = when (val r = c.core.submit(op, dryRun = true)) {
                is CoreCall.Ok -> ActionState.Preview(l, op, r.value)
                else -> ActionState.Error(l, r.describeFailure())
            }
        }
    }

    fun apply(op: CoreOperation) {
        if (busy) return
        val l = label(op)
        action.value = ActionState.Working(l)
        viewModelScope.launch(Dispatchers.IO) {
            val r = c.core.submit(op, dryRun = false)
            // Read the provider back before reporting, so the result shows what zfasd now says.
            val status = c.fas.status()
            fasState.value = status
            health.value = c.core.health()
            action.value = when (r) {
                is CoreCall.Ok -> ActionState.Done(
                    l, "${r.value.outcome}: ${r.value.message}", r.value.outcome in setOf("applied", "already_applied"),
                    profileCheck(requestedProfile(op), status),
                )
                else -> ActionState.Error(l, r.describeFailure())
            }
            c.registry.refresh("fas")
        }
    }

    /** Direct zfasd control: only offered when Core is not answering (FAS's own WebUI does the same). */
    fun applyDirect(label: UiText, call: () -> FasCall<String>, requestedProfile: String? = null) {
        if (busy) return
        action.value = ActionState.Working(label)
        viewModelScope.launch(Dispatchers.IO) {
            val r = call()
            val status = c.fas.status()
            fasState.value = status
            action.value = when (r) {
                is FasCall.Ok -> ActionState.Done(label, r.value, true, profileCheck(requestedProfile, status))
                else -> ActionState.Error(label, r.describeFailure())
            }
            c.registry.refresh("fas")
        }
    }

    fun setProfileDirect(p: FasProfileName) {
        if (busy) return
        action.value = ActionState.ConfirmDirect(UiText(R.string.action_profile, listOf(p.wire)), { c.fas.setProfile(p) }, p.wire)
    }

    fun commandDirect(cmd: FasCommand) {
        if (busy) return
        val label = when (cmd) {
            FasCommand.ENABLE -> R.string.fas_enable
            FasCommand.DISABLE -> R.string.fas_disable
            FasCommand.RESET -> R.string.fas_reset
        }
        action.value = ActionState.ConfirmDirect(UiText(label), run = { c.fas.command(cmd) })
    }

    fun dismiss() {
        action.value = ActionState.Idle
    }
}

data class ThermalSample(val status: Int?, val headroom: Float?, val powerSave: Boolean, val atMs: Long)

/**
 * The Java [com.zairenkaiprjctx.app.platform.ThermalStatusMonitor] callback as a Flow. The
 * listener is registered on collection and removed when the collector is cancelled.
 */
fun com.zairenkaiprjctx.app.platform.ThermalStatusMonitor.statusFlow(): Flow<Int> = callbackFlow {
    val registration = start(java.util.concurrent.Executor { it.run() }) { status -> trySend(status) }
    if (registration == null) {
        // The platform refused the listener: complete without values; the caller polls instead.
        channel.close()
    }
    awaitClose { registration?.close() }
}

class ThermalViewModel(private val c: AppContainer) : ViewModel() {
    private val sample = MutableStateFlow<ThermalSample?>(null)
    val android: StateFlow<ThermalSample?> = sample.asStateFlow()
    private val telemetry = MutableStateFlow<CoreCall<CoreTelemetry>?>(null)
    val core: StateFlow<CoreCall<CoreTelemetry>?> = telemetry.asStateFlow()
    private var jobs: List<Job> = emptyList()

    @Volatile
    private var listenerActive = false

    /**
     * Thermal status arrives from the platform listener (event-driven); headroom has no listener
     * and is read every 2 s. Both run only while the screen is visible.
     */
    fun start() {
        if (jobs.any { it.isActive }) return
        val p = c.platform
        sample.value = ThermalSample(p.thermalStatus(), p.thermalHeadroom(10), p.powerSaveMode(), System.currentTimeMillis())
        listenerActive = false
        val statusJob = viewModelScope.launch {
            c.thermalMonitor.statusFlow()
                .onStart { listenerActive = true }
                .onCompletion { listenerActive = false }
                .collect { s -> sample.value = sample.value?.copy(status = s, atMs = System.currentTimeMillis()) }
        }
        val headroomJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(2000)
                val cur = sample.value
                // Without the listener (refused by the platform), the status is polled here too.
                val status = if (listenerActive) cur?.status else p.thermalStatus()
                sample.value = ThermalSample(status, p.thermalHeadroom(10), p.powerSaveMode(), System.currentTimeMillis())
            }
        }
        jobs = listOf(statusJob, headroomJob)
        viewModelScope.launch(Dispatchers.IO) { telemetry.value = c.core.telemetry() }
        if (zthermal.value == null) loadThermalTools()
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }

    fun reloadCore() {
        viewModelScope.launch(Dispatchers.IO) { telemetry.value = c.core.telemetry() }
    }

    private val zthermal = MutableStateFlow<ThermalToolsResult<ThermalSnapshot>?>(null)

    /** The latest zthermal snapshot (read only), or why there is none. */
    val thermalTools: StateFlow<ThermalToolsResult<ThermalSnapshot>?> = zthermal.asStateFlow()
    private val zthermalBusy = MutableStateFlow(false)
    val thermalToolsBusy: StateFlow<Boolean> = zthermalBusy.asStateFlow()
    val rootGranted: Boolean get() = c.root.state == RootState.GRANTED

    /** `zthermal snapshot` as the app uid; with [withRoot], through su with `--framework`. */
    fun loadThermalTools(withRoot: Boolean = false) {
        if (zthermalBusy.value) return
        zthermalBusy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            zthermal.value = if (withRoot) c.zthermal.snapshotWithRoot() else c.zthermal.snapshot()
            zthermalBusy.value = false
        }
    }
}

class RuntimeViewModel(private val c: AppContainer) : ViewModel() {
    private val report = MutableStateFlow<ZartResult<ZartReport>?>(null)
    val inspect: StateFlow<ZartResult<ZartReport>?> = report.asStateFlow()
    private val dexopt = MutableStateFlow<ZartResult<Obs<PackageDexopt>>?>(null)
    val dexoptStatus: StateFlow<ZartResult<Obs<PackageDexopt>>?> = dexopt.asStateFlow()
    private val busy = MutableStateFlow(false)
    val querying: StateFlow<Boolean> = busy.asStateFlow()

    val artApexVersion: Long? get() = c.platform.artApexVersion()
    val ownPackage: String get() = c.packageName

    fun load() {
        viewModelScope.launch(Dispatchers.IO) { report.value = c.zart.inspect() }
    }

    fun query(packageName: String) {
        if (!PackageNames.isValid(packageName)) return
        busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            dexopt.value = c.zart.dexoptStatus(packageName)
            busy.value = false
        }
    }
}

class SystemViewModel(private val c: AppContainer) : ViewModel() {
    private val mods = MutableStateFlow<CoreCall<List<CoreModule>>?>(null)
    val modules: StateFlow<CoreCall<List<CoreModule>>?> = mods.asStateFlow()
    private val results = MutableStateFlow<List<AfidDevice>>(emptyList())
    val searchResults: StateFlow<List<AfidDevice>> = results.asStateFlow()

    val container: AppContainer get() = c

    fun loadModules() {
        viewModelScope.launch(Dispatchers.IO) { mods.value = c.core.modules() }
    }

    fun search(q: String) {
        viewModelScope.launch(Dispatchers.Default) { results.value = c.afid.getOrNull()?.search(q).orEmpty() }
    }
}

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val theme: StateFlow<com.zairenkaiprjctx.app.design.ThemeSettings> = c.theme.settings

    fun setMode(mode: com.zairenkaiprjctx.app.design.ThemeMode) = c.theme.update(theme.value.copy(mode = mode))
    fun setDynamic(on: Boolean) = c.theme.update(theme.value.copy(dynamicColor = on))
}
