// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.zairenkaiprjctx.app.design.ThemeMode
import com.zairenkaiprjctx.app.design.ThemeSettings
import com.zairenkaiprjctx.app.platform.AndroidPlatformInfo
import com.zairenkaiprjctx.app.platform.ManagerHandoff
import com.zairenkaiprjctx.app.platform.ThermalStatusMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.zairenkaiprjctx.integration.afid.AfidCatalog
import com.zairenkaiprjctx.integration.core.CoreClient
import com.zairenkaiprjctx.integration.ecosystem.OrganizationInventory
import com.zairenkaiprjctx.integration.exec.CommandRunner
import com.zairenkaiprjctx.integration.exec.ProcessCommandRunner
import com.zairenkaiprjctx.integration.exec.RootConsentStore
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.fas.FasClient
import com.zairenkaiprjctx.integration.registry.ArtSubsystem
import com.zairenkaiprjctx.integration.registry.CoreSubsystem
import com.zairenkaiprjctx.integration.registry.DeviceDbSubsystem
import com.zairenkaiprjctx.integration.registry.FasSubsystem
import com.zairenkaiprjctx.integration.registry.IntegrationRegistry
import com.zairenkaiprjctx.integration.registry.PlatformSubsystem
import com.zairenkaiprjctx.integration.registry.SystemModuleSubsystem
import com.zairenkaiprjctx.integration.registry.ThermalToolsSubsystem
import com.zairenkaiprjctx.integration.registry.UpstreamGapSubsystem
import com.zairenkaiprjctx.integration.core.CoreCall
import com.zairenkaiprjctx.integration.fas.FasCall
import com.zairenkaiprjctx.integration.systemmodule.ArtifactStore
import com.zairenkaiprjctx.integration.systemmodule.DeviceFacts
import com.zairenkaiprjctx.integration.systemmodule.ModuleCatalog
import com.zairenkaiprjctx.integration.systemmodule.ModuleRelease
import com.zairenkaiprjctx.integration.systemmodule.ModuleTrust
import com.zairenkaiprjctx.integration.systemmodule.RuntimeStatus
import com.zairenkaiprjctx.integration.systemmodule.ModuleInstallController
import com.zairenkaiprjctx.integration.systemmodule.ModuleStateReader
import com.zairenkaiprjctx.integration.systemmodule.PreflightProbe
import com.zairenkaiprjctx.integration.systemmodule.RootManagerDetector
import com.zairenkaiprjctx.integration.systemmodule.RuntimeProbe
import com.zairenkaiprjctx.integration.systemmodule.adapterFor
import com.zairenkaiprjctx.integration.thermaltools.ThermalToolsClient
import com.zairenkaiprjctx.integration.zart.ZartClient
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Revisions of the packaged components (assets/components.json, written by build-components.sh). */
data class PackagedComponents(val zartCommit: String?, val zartVersion: String?, val zthermalCommit: String?, val zthermalVersion: String?, val afidCommit: String?)

/** Appearance preferences, persisted in SharedPreferences and observed by the root composable. */
class ThemePreferences(private val prefs: android.content.SharedPreferences) {
    private val state = MutableStateFlow(read())
    val settings: StateFlow<ThemeSettings> = state.asStateFlow()

    private fun read() = ThemeSettings(
        mode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_MODE, null) ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
    )

    fun update(settings: ThemeSettings) {
        prefs.edit {
            putString(KEY_MODE, settings.mode.name)
            putBoolean(KEY_DYNAMIC, settings.dynamicColor)
        }
        state.value = settings
    }

    private companion object {
        const val KEY_MODE = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
    }
}

/**
 * Manual dependency container: one instance per process, created by [ZairenkaiApplication].
 * [runner] runs every external command (su, zart, zthermal); tests pass one that replays output
 * captured from the real binaries.
 */
class AppContainer(private val app: Application, val runner: CommandRunner = ProcessCommandRunner()) {
    private val prefs = app.getSharedPreferences("zairenkai", Context.MODE_PRIVATE)

    private val consent = object : RootConsentStore {
        override var rootGranted: Boolean
            get() = prefs.getBoolean(KEY_ROOT, false)
            set(value) {
                prefs.edit { putBoolean(KEY_ROOT, value) }
            }
    }

    val packageName: String = app.packageName
    val versionName: String = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
    val theme = ThemePreferences(prefs)
    val root = RootShell(runner, consent)
    val platform = AndroidPlatformInfo(app)
    val thermalMonitor = ThermalStatusMonitor(app)

    /** zart is extracted to nativeLibraryDir (useLegacyPackaging) and must be executable there. */
    val zartPath: String? = nativeExecutable("libzart.so")

    /** zthermal, packaged the same way. */
    val zthermalPath: String? = nativeExecutable("libzthermal.so")

    private fun nativeExecutable(name: String): String? =
        File(app.applicationInfo.nativeLibraryDir, name).takeIf { it.isFile && it.canExecute() }?.absolutePath

    fun asset(path: String): String = app.assets.open(path).use { it.readBytes().decodeToString() }

    fun assetExists(path: String): Boolean = runCatching { app.assets.open(path).close() }.isSuccess

    val inventory: OrganizationInventory? by lazy {
        runCatching { OrganizationInventory.parse(asset("integration/organization-repositories.json")) }.getOrNull()
    }

    val afid: Result<AfidCatalog> by lazy { runCatching { AfidCatalog.parse(asset("afid/device-candidates.json")) } }

    val components: PackagedComponents? by lazy {
        runCatching {
            val o = Json.parseToJsonElement(asset("components.json")).jsonObject
            fun field(section: String, key: String) = (o[section] as? JsonObject)?.get(key)?.jsonPrimitive?.content
            PackagedComponents(
                field("zart", "commit"), field("zart", "version"),
                field("zthermal", "commit"), field("zthermal", "version"),
                field("afid", "commit"),
            )
        }.getOrNull()
    }

    private fun lockedCommit(repo: String): String? = inventory?.byName(repo)?.auditedCommit

    val zart = ZartClient({ zartPath }, runner, root)
    val zthermal = ThermalToolsClient({ zthermalPath }, runner, root)
    val core = CoreClient(root)
    val fas = FasClient(root)

    val art = ArtSubsystem(zart, root, components?.zartCommit)
    val thermalTools = ThermalToolsSubsystem(zthermal, root, components?.zthermalCommit)
    val fasSubsystem = FasSubsystem(fas, root, lockedCommit("Zairenkai-FAS"))

    val registry = IntegrationRegistry(
        listOf(
            PlatformSubsystem(platform),
            art,
            thermalTools,
            DeviceDbSubsystem({ afid }, platform),
            SystemModuleSubsystem({ moduleRelease }, { moduleInstallers.mapValues { it.value.state.value } }),
            CoreSubsystem(core, root, lockedCommit("Zairenkai-Core")),
            fasSubsystem,
            UpstreamGapSubsystem { inventory },
        ),
        Dispatchers.IO,
    )

    /**
     * Root modules packaged in this APK and the verdict on the catalog's signature against the
     * module-signing keys in integration/module-trust.json; null if no catalog is packaged.
     */
    val moduleRelease: ModuleRelease? by lazy {
        runCatching {
            val catalog = app.assets.open("system-modules.json").use { it.readBytes() }
            val sig = runCatching { asset("system-modules.json.sig") }.getOrNull()
            ModuleRelease.load(catalog, sig, ModuleTrust.parse(asset("integration/module-trust.json")))
        }.getOrNull()
    }

    val moduleCatalog: ModuleCatalog? get() = moduleRelease?.catalog

    /** Copies a packaged module to the cache staging directory (served by the FileProvider). */
    private val moduleStore = ArtifactStore { m ->
        val dir = File(app.cacheDir, "system-modules").apply { mkdirs() }
        File(dir, "${m.id}-${m.versionCode}.zip").also { out ->
            app.assets.open(m.file).use { input -> out.outputStream().use { input.copyTo(it) } }
        }
    }

    fun deviceFacts() = DeviceFacts(
        manufacturer = platform.manufacturer,
        brand = platform.brand,
        model = platform.model,
        codename = platform.deviceCodename,
        socManufacturer = platform.socManufacturer,
        socModel = platform.socModel,
        sdkInt = platform.sdkInt,
        supportedAbis = platform.supportedAbis,
        kernelMachine = null,
        kernelRelease = null,
        freeDataBytes = android.os.StatFs(app.filesDir.path).availableBytes,
    )

    /** What the component a module provides says: Core health or FAS status. */
    private val moduleRuntime = RuntimeProbe { m ->
        when (m.runtimeCheck) {
            "core.health" -> when (val h = core.health()) {
                is CoreCall.Ok ->
                    if (h.value.state == "ready") RuntimeStatus.Healthy("Core ${h.value.version}: ready")
                    else RuntimeStatus.Degraded("Core ${h.value.version}: ${h.value.state} (${h.value.reasons.joinToString("; ")})")
                CoreCall.NotInstalled -> RuntimeStatus.NotAnswering("zcored was not found in the module")
                is CoreCall.Unreachable -> RuntimeStatus.NotAnswering("zcored is not answering: ${h.detail}")
                is CoreCall.Error -> RuntimeStatus.Degraded("Core error ${h.code}: ${h.message}")
                is CoreCall.NoRoot -> RuntimeStatus.NotAnswering(h.detail)
                is CoreCall.Failed -> RuntimeStatus.NotAnswering(h.detail)
            }
            "fas.status" -> when (val f = fas.status()) {
                is FasCall.Ok -> RuntimeStatus.Healthy(if (f.value.observeOnly) "zfasd running in observe-only mode" else "zfasd running")
                FasCall.NotInstalled -> RuntimeStatus.NotAnswering("zfasd was not found in the module")
                is FasCall.Unreachable -> RuntimeStatus.NotAnswering("zfasd is not answering: ${f.detail}")
                is FasCall.Rejected -> RuntimeStatus.Degraded(f.message)
                is FasCall.NoRoot -> RuntimeStatus.NotAnswering(f.detail)
                is FasCall.Failed -> RuntimeStatus.NotAnswering(f.detail)
            }
            else -> RuntimeStatus.NotAnswering("no runtime check is defined for ${m.id}")
        }
    }

    private val moduleState = ModuleStateReader(root)

    /** One installer per packaged module, in catalog order (base first). */
    val moduleInstallers: Map<String, ModuleInstallController> by lazy {
        moduleCatalog?.modules.orEmpty().sortedBy { it.role != "base" }.associate { m ->
            m.id to ModuleInstallController(
                root = root,
                release = { moduleRelease },
                detector = { RootManagerDetector(root).detect() },
                facts = ::deviceFacts,
                probe = PreflightProbe(root, moduleState),
                store = moduleStore,
                adapters = { adapterFor(it, root) },
                runtime = moduleRuntime,
            )
        }
    }

    val handoff = ManagerHandoff(app)

    /** The user said "not now" to the install offer; Settings can undo it. */
    var moduleOfferDeclined: Boolean
        get() = prefs.getBoolean(KEY_MODULE_DECLINED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_MODULE_DECLINED, value) }
        }

    private companion object {
        const val KEY_ROOT = "root_granted"
        const val KEY_MODULE_DECLINED = "system_module_offer_declined"
    }
}
