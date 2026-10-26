// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.integration.ai

import com.zairenkaiprjctx.integration.model.Availability
import com.zairenkaiprjctx.integration.model.AvailabilityState.MODEL_NOT_IMPORTED
import com.zairenkaiprjctx.integration.model.AvailabilityState.INITIALIZATION_FAILED
import com.zairenkaiprjctx.integration.model.AvailabilityState.NATIVE_COMPONENT_UNAVAILABLE
import com.zairenkaiprjctx.integration.model.AvailabilityState.NOT_VERIFIED
import com.zairenkaiprjctx.integration.model.AvailabilityState.UNSUPPORTED_DEVICE
import com.zairenkaiprjctx.integration.model.Privilege
import com.zairenkaiprjctx.integration.model.Provider
import com.zairenkaiprjctx.integration.registry.CapabilityRecord
import com.zairenkaiprjctx.integration.registry.ZairenkaiSubsystem

/** Everything the Local AI needs, or why it could not be loaded from the APK's assets. */
data class LocalAiAssets(val registry: AiToolRegistry, val store: ModelStore)

/**
 * `intelligence.analysis`: usable only when zie-run starts on this device, the packaged registry
 * and manifest load, the device has the memory the manifest asks for, and a model file passed the
 * SHA-256 check. Then it is NOT_VERIFIED: the runtime answered its probe, but the assistant has
 * not been validated on hardware.
 */
class IntelligenceSubsystem(
    private val runtime: ZieRuntime,
    private val assets: () -> Result<LocalAiAssets>,
    private val totalMemoryBytes: () -> Long?,
    revision: String?,
) : ZairenkaiSubsystem {
    override val id = "intelligence"
    override val displayName = "Zairenkai Local AI"
    override val provider = Provider("ZairenkaiPrjctX/Zairenkai-Intelligence-Engine", revision, "zie-run (llama.cpp), tool registry, model manifest")

    override fun probe(): List<CapabilityRecord> {
        // The runner first: without zie-run for this ABI nothing else matters (a reduced build
        // packages neither the runner nor its assets).
        val a = when (val v = runtime.version()) {
            is RuntimeCall.Unavailable -> Availability(NATIVE_COMPONENT_UNAVAILABLE, v.reason)
            is RuntimeCall.Ok -> assets().fold(
                onSuccess = { ai ->
                    val mem = totalMemoryBytes()
                    when {
                        mem != null && mem < ai.store.manifest.minMemoryBytes ->
                            Availability(UNSUPPORTED_DEVICE, "The model needs ${ai.store.manifest.minMemoryBytes / MIB} MiB of memory; this device has ${mem / MIB} MiB.")
                        !ai.store.installed() -> Availability(MODEL_NOT_IMPORTED, "${v.value} is ready; the model file (${ai.store.manifest.displayName}) has not been imported.")
                        else -> Availability(NOT_VERIFIED, "${v.value}; ${ai.store.manifest.displayName} matches its SHA-256. Not validated on hardware yet.")
                    }
                },
                onFailure = { Availability(INITIALIZATION_FAILED, "Local AI assets could not be loaded: ${it.message}") },
            )
        }
        return listOf(
            CapabilityRecord(
                "intelligence.analysis", "Zairenkai Local AI",
                "Offline language model that maps a question to read-only tools; changes go to Core and need your approval",
                provider, Privilege.STANDARD, a, listOf("ask", "import_model", "delete_model"),
            ),
        )
    }

    private companion object {
        const val MIB = 1024L * 1024
    }
}
