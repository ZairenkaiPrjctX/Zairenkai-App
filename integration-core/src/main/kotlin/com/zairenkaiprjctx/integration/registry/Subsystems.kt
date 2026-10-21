package com.zairenkaiprjctx.integration.registry

import com.zairenkaiprjctx.integration.afid.AfidCatalog
import com.zairenkaiprjctx.integration.core.CoreCall
import com.zairenkaiprjctx.integration.core.CoreClient
import com.zairenkaiprjctx.integration.ecosystem.OrganizationInventory
import com.zairenkaiprjctx.integration.exec.RootShell
import com.zairenkaiprjctx.integration.exec.RootState
import com.zairenkaiprjctx.integration.fas.FasCall
import com.zairenkaiprjctx.integration.fas.FasClient
import com.zairenkaiprjctx.integration.fas.FasStatus
import com.zairenkaiprjctx.integration.model.Availability
import com.zairenkaiprjctx.integration.model.AvailabilityState.COMPONENT_NOT_INSTALLED
import com.zairenkaiprjctx.integration.model.AvailabilityState.INITIALIZATION_FAILED
import com.zairenkaiprjctx.integration.model.AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE
import com.zairenkaiprjctx.integration.model.AvailabilityState.NOT_VERIFIED
import com.zairenkaiprjctx.integration.model.AvailabilityState.NO_UPSTREAM_IMPLEMENTATION
import com.zairenkaiprjctx.integration.model.AvailabilityState.PERMISSION_REQUIRED
import com.zairenkaiprjctx.integration.model.AvailabilityState.ROOT_REQUIRED
import com.zairenkaiprjctx.integration.model.AvailabilityState.UNSUPPORTED_DEVICE
import com.zairenkaiprjctx.integration.model.Privilege
import com.zairenkaiprjctx.integration.model.Provider
import com.zairenkaiprjctx.integration.thermaltools.ThermalSnapshot
import com.zairenkaiprjctx.integration.thermaltools.ThermalToolsClient
import com.zairenkaiprjctx.integration.thermaltools.ThermalToolsResult
import com.zairenkaiprjctx.integration.zart.ZartClient
import com.zairenkaiprjctx.integration.zart.ZartReport
import com.zairenkaiprjctx.integration.zart.ZartResult

/** Android-side facts the core module cannot obtain itself (implemented in :app). */
interface PlatformInfo {
    val sdkInt: Int
    val manufacturer: String
    val brand: String
    val model: String
    val deviceCodename: String
    val socManufacturer: String?
    val socModel: String?
    val supportedAbis: List<String>

    /** `PowerManager.getCurrentThermalStatus()` (API 29+), or null. */
    fun thermalStatus(): Int?

    /** `PowerManager.getThermalHeadroom()` (API 30+); null if unsupported or NaN. */
    fun thermalHeadroom(forecastSeconds: Int): Float?
    fun powerSaveMode(): Boolean

    /** Version code of the `com.android.art` APEX via PackageManager (API 29+), or null. */
    fun artApexVersion(): Long?
}

private fun rootUnavailable(root: RootShell): Availability? = when (root.state) {
    RootState.GRANTED -> null
    RootState.NOT_REQUESTED -> Availability(ROOT_REQUIRED, "Root integration is off. Enable it in System → Root access.")
    RootState.DENIED -> Availability(ROOT_REQUIRED, "The root manager refused the request, or su did not return uid 0.")
    RootState.UNAVAILABLE -> Availability(ROOT_REQUIRED, "No su binary found: this device is not rooted.")
}

class PlatformSubsystem(private val platform: PlatformInfo) : ZairenkaiSubsystem {
    override val id = "android"
    override val displayName = "Android platform"
    override val provider = Provider("Android framework (public API)", null, "android.os / android.content.pm")

    override fun probe(): List<CapabilityRecord> {
        val api = platform.sdkInt
        val headroom = platform.thermalHeadroom(10)
        return listOf(
            CapabilityRecord(
                "android.identity", "Device identity", "Manufacturer, model, codename, SoC (API 31+) and ABIs from android.os.Build",
                provider, Privilege.STANDARD, Availability.available(),
            ),
            CapabilityRecord(
                "android.thermal_status", "Thermal status", "PowerManager.getCurrentThermalStatus()",
                provider, Privilege.STANDARD,
                if (api >= 29 && platform.thermalStatus() != null) Availability.available()
                else Availability(UNSUPPORTED_DEVICE, "Needs Android 10 (API 29) or later."),
            ),
            CapabilityRecord(
                "android.thermal_headroom", "Thermal headroom", "PowerManager.getThermalHeadroom(10 s forecast)",
                provider, Privilege.STANDARD,
                when {
                    api < 30 -> Availability(UNSUPPORTED_DEVICE, "Needs Android 11 (API 30) or later.")
                    headroom == null -> Availability(UNSUPPORTED_DEVICE, "The device's thermal HAL does not report headroom.")
                    else -> Availability.available()
                },
            ),
            CapabilityRecord(
                "android.power_save", "Battery saver state", "PowerManager.isPowerSaveMode() (read only)",
                provider, Privilege.STANDARD, Availability.available(),
            ),
        )
    }
}

class ArtSubsystem(
    private val zart: ZartClient,
    private val root: RootShell,
    revision: String?,
) : ZairenkaiSubsystem {
    override val id = "art"
    override val displayName = "ART runtime (Zairenkai-ART)"
    override val provider = Provider("ZairenkaiPrjctX/Zairenkai-ART", revision, "zart")

    @Volatile
    var lastReport: ZartReport? = null
        private set

    override fun probe(): List<CapabilityRecord> {
        val inspect = when (val r = zart.inspect()) {
            is ZartResult.Ok -> {
                lastReport = r.value
                val findings = r.value.isa.flatMap { it.arm64?.findings.orEmpty() }
                Availability.available(
                    if (findings.isEmpty()) "zart ${r.value.zartVersion}" else "zart ${r.value.zartVersion}: ${findings.size} ISA finding(s)",
                )
            }
            is ZartResult.Failed -> {
                lastReport = null
                Availability(if (r.missingBinary) NATIVE_COMPONENT_UNAVAILABLE else INITIALIZATION_FAILED, r.detail)
            }
        }
        val dexopt = rootUnavailable(root)
            ?: if (!inspect.usable && inspect.state == NATIVE_COMPONENT_UNAVAILABLE) inspect
            else Availability(NOT_VERIFIED, "Runs `zart dexopt-status` through root; the result appears when you query a package.")
        return listOf(
            CapabilityRecord(
                "art.inspect", "ART inspection",
                "ART module version, ART and dexopt properties, page size, arm64 ISA configuration compared with AT_HWCAP",
                provider, Privilege.STANDARD, inspect, listOf("inspect"),
            ),
            CapabilityRecord(
                "art.dexopt_status", "Dexopt status per package",
                "ART Service's dump for one package (compiler filter, reason, artifact location per ISA)",
                provider, Privilege.ROOT, dexopt, listOf("dexopt-status"),
            ),
            CapabilityRecord(
                "art.patches", "ART source patches",
                "Upstream patch series for a ROM or ART module build. Build-time only: an app cannot change ART.",
                provider, Privilege.NONE,
                Availability(UNSUPPORTED_DEVICE, "Requires building ART into a ROM or a signed ART module (Zairenkai-ART docs/ART_BUILD_GUIDE.md)."),
            ),
        )
    }
}

class ThermalToolsSubsystem(
    private val zthermal: ThermalToolsClient,
    private val root: RootShell,
    revision: String?,
) : ZairenkaiSubsystem {
    override val id = "thermaltools"
    override val displayName = "Thermal diagnostics (Zairenkai-ThermalTools)"
    override val provider = Provider("ZairenkaiPrjctX/Zairenkai-ThermalTools", revision, "zthermal")

    @Volatile
    var lastSnapshot: ThermalSnapshot? = null
        private set

    override fun probe(): List<CapabilityRecord> {
        val snapshot = when (val r = zthermal.snapshot()) {
            is ThermalToolsResult.Ok -> {
                lastSnapshot = r.value
                val s = r.value.summary
                val counts = "zthermal ${r.value.toolVersion}: ${s.zonesValid} of ${s.zones} thermal zones readable"
                when {
                    s.zonesValid > 0 -> Availability.available(counts)
                    r.value.accessDenied -> Availability(PERMISSION_REQUIRED, "$counts. Android denies this app the thermal zones; a root snapshot may read them.")
                    else -> Availability(UNSUPPORTED_DEVICE, "$counts. The kernel exposes no readable thermal zone here.")
                }
            }
            is ThermalToolsResult.Failed -> {
                lastSnapshot = null
                Availability(if (r.missingBinary) NATIVE_COMPONENT_UNAVAILABLE else INITIALIZATION_FAILED, r.detail)
            }
        }
        val withRoot = rootUnavailable(root)
            ?: if (snapshot.state == NATIVE_COMPONENT_UNAVAILABLE) snapshot
            else Availability(NOT_VERIFIED, "Runs `zthermal snapshot --framework` through root when you ask; adds dumpsys thermalservice and battery.")
        return listOf(
            CapabilityRecord(
                "thermaltools.snapshot", "Thermal sensors",
                "Thermal zones, trip points, cooling devices and hwmon read by zthermal as this app (read only)",
                provider, Privilege.STANDARD, snapshot, listOf("snapshot"),
            ),
            CapabilityRecord(
                "thermaltools.snapshot_root", "Full thermal snapshot",
                "The same snapshot through root, with the framework's thermal status and battery temperature",
                provider, Privilege.ROOT, withRoot, listOf("snapshot --framework"),
            ),
            CapabilityRecord(
                "thermaltools.control", "Thermal controls",
                "Changing thermal policy, trip points or cooling devices",
                provider, Privilege.NONE,
                Availability(
                    NO_UPSTREAM_IMPLEMENTATION,
                    "zthermal has no validated control: it only plans changes and refuses to write. The app never runs apply, rollback or backup-config.",
                ),
            ),
        )
    }
}

class DeviceDbSubsystem(
    private val catalog: () -> Result<AfidCatalog>,
    private val platform: PlatformInfo,
) : ZairenkaiSubsystem {
    override val id = "afid"
    override val displayName = "Device database (Zairenkai-Database-Source)"
    override val provider get() = Provider("ZairenkaiPrjctX/Zairenkai-Database-Source", catalog().getOrNull()?.generatedFrom?.commit, "AFID device candidates")

    override fun probe(): List<CapabilityRecord> {
        val c = catalog()
        val a = c.fold(
            onSuccess = { cat ->
                val n = cat.lookup(platform.deviceCodename).size
                Availability.available("${cat.count} unverified candidates; $n match this device's codename")
            },
            onFailure = { Availability(INITIALIZATION_FAILED, "Device asset not packaged or invalid: ${it.message}") },
        )
        return listOf(
            CapabilityRecord("afid.lookup", "Device lookup", "Community-reported device metadata for this codename (unverified)", provider, Privilege.STANDARD, a, listOf("lookup")),
            CapabilityRecord("afid.search", "Device search", "Search the packaged candidates by codename, product, model or SoC", provider, Privilege.STANDARD, a, listOf("search")),
        )
    }
}

class CoreSubsystem(private val core: CoreClient, private val root: RootShell, revision: String?) : ZairenkaiSubsystem {
    override val id = "core"
    override val displayName = "Zairenkai-Core"
    override val provider = Provider("ZairenkaiPrjctX/Zairenkai-Core", revision, "zcored ctl (protocol v1)")

    override fun probe(): List<CapabilityRecord> {
        val a = rootUnavailable(root) ?: when (val h = core.health()) {
            is CoreCall.Ok -> Availability.available("Core ${h.value.version}: ${h.value.state}" + if (h.value.reasons.isEmpty()) "" else " (${h.value.reasons.joinToString("; ")})")
            CoreCall.NotInstalled -> Availability(COMPONENT_NOT_INSTALLED, "Module ${com.zairenkaiprjctx.integration.core.CorePaths.MODULE_ID} is not installed.")
            is CoreCall.Unreachable -> Availability(INITIALIZATION_FAILED, "zcored is installed but not answering: ${h.detail}")
            is CoreCall.Error -> Availability(INITIALIZATION_FAILED, "Core error ${h.code}: ${h.message}")
            is CoreCall.NoRoot -> Availability(ROOT_REQUIRED, h.detail)
            is CoreCall.Failed -> Availability(INITIALIZATION_FAILED, h.detail)
        }
        return listOf(
            CapabilityRecord("core.health", "Core health", "Health, policy version and capability reasons from zcored", provider, Privilege.ROOT, a, listOf("health", "capabilities", "context", "audit")),
            CapabilityRecord("core.actions", "Policy-checked actions", "FAS profile, FAS on/off and battery saver through Core's policy engine, with a dry-run preview", provider, Privilege.ROOT, a, listOf("submit")),
            CapabilityRecord("core.modules", "Module inventory", "Installed Zairenkai modules as Core reports them (read only)", provider, Privilege.ROOT, a, listOf("modules")),
        )
    }
}

class FasSubsystem(private val fas: FasClient, private val root: RootShell, revision: String?) : ZairenkaiSubsystem {
    override val id = "fas"
    override val displayName = "Zairenkai-FAS"
    override val provider = Provider("ZairenkaiPrjctX/Zairenkai-FAS", revision, "zfasd ctl")

    @Volatile
    var lastStatus: FasStatus? = null
        private set

    override fun probe(): List<CapabilityRecord> {
        val a = rootUnavailable(root) ?: when (val s = fas.status()) {
            is FasCall.Ok -> {
                lastStatus = s.value
                Availability.available(
                    if (s.value.observeOnly) "zfasd running in observe-only mode" else "zfasd running (${s.value.profile ?: "?"})",
                )
            }
            FasCall.NotInstalled -> Availability(COMPONENT_NOT_INSTALLED, "Module ${com.zairenkaiprjctx.integration.fas.FasPaths.MODULE_ID} is not installed.")
            is FasCall.Unreachable -> Availability(INITIALIZATION_FAILED, "zfasd is installed but not answering: ${s.detail}")
            is FasCall.Rejected -> Availability(INITIALIZATION_FAILED, s.message)
            is FasCall.NoRoot -> Availability(ROOT_REQUIRED, s.detail)
            is FasCall.Failed -> Availability(INITIALIZATION_FAILED, s.detail)
        }
        if (!a.usable) lastStatus = null
        return listOf(
            CapabilityRecord("fas.status", "Frame-aware scheduling status", "Frame statistics, decision and thermal cap from zfasd", provider, Privilege.ROOT, a, listOf("status")),
            CapabilityRecord("fas.control", "FAS controls", "Enable, disable, profile and reset through zfasd ctl (used directly only when Core is not running)", provider, Privilege.ROOT, a, listOf("enable", "disable", "profile", "reset")),
        )
    }
}

/**
 * Repositories that have no implementation yet. Their capabilities are listed so the UI can say
 * why the feature is absent, with the audited commit as evidence.
 */
class UpstreamGapSubsystem(private val inventory: () -> OrganizationInventory?) : ZairenkaiSubsystem {
    override val id = "upstream"
    override val displayName = "Planned components"
    override val provider = Provider("ZairenkaiPrjctX", null, "organization inventory")

    private val planned = listOf(
        Triple("Zairenkai-Intelligence-Engine", "intelligence.analysis", "Analysis and recommendations"),
        Triple("Zairenkai-License-Server", "license.entitlement", "Licence and entitlement"),
        Triple("Zairenkai-Sdk", "sdk.api", "SDK"),
        Triple("Zairenkai-Graphics-Platform", "graphics.control", "GPU controls"),
        Triple("Zairenkai-Modules", "modules.packages", "Module packages"),
    )

    override fun probe(): List<CapabilityRecord> {
        val inv = inventory()
        return planned.map { (repo, capId, name) ->
            val r = inv?.byName(repo)
            CapabilityRecord(
                capId, name, "Owned by $repo",
                Provider("ZairenkaiPrjctX/$repo", r?.auditedCommit, repo), Privilege.NONE,
                if (r != null && !r.codePresent) {
                    Availability(NO_UPSTREAM_IMPLEMENTATION, "$repo has no implementation at ${r.auditedBranch} ${r.auditedCommit.take(12)}.")
                } else {
                    Availability(INITIALIZATION_FAILED, "Inventory entry for $repo is missing or now lists code; re-audit before integrating.")
                },
            )
        }
    }
}
