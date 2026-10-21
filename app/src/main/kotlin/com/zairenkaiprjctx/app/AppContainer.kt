package com.zairenkaiprjctx.app

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.zairenkaiprjctx.app.design.ThemeMode
import com.zairenkaiprjctx.app.design.ThemeSettings
import com.zairenkaiprjctx.app.platform.AndroidPlatformInfo
import com.zairenkaiprjctx.app.platform.ThermalStatusMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.zairenkaiprjctx.integration.afid.AfidCatalog
import com.zairenkaiprjctx.integration.core.CoreClient
import com.zairenkaiprjctx.integration.ecosystem.OrganizationInventory
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
import com.zairenkaiprjctx.integration.registry.ThermalToolsSubsystem
import com.zairenkaiprjctx.integration.registry.UpstreamGapSubsystem
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

/** Manual dependency container: one instance per process, created by [ZairenkaiApplication]. */
class AppContainer(private val app: Application) {
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
    val runner = ProcessCommandRunner()
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
            CoreSubsystem(core, root, lockedCommit("Zairenkai-Core")),
            fasSubsystem,
            UpstreamGapSubsystem { inventory },
        ),
        Dispatchers.IO,
    )

    private companion object {
        const val KEY_ROOT = "root_granted"
    }
}
