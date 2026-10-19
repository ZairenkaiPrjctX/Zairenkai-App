package com.zairenkaiprjctx.integration.registry

import com.zairenkaiprjctx.integration.model.Availability
import com.zairenkaiprjctx.integration.model.AvailabilityState
import com.zairenkaiprjctx.integration.model.Privilege
import com.zairenkaiprjctx.integration.model.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One capability as the UI sees it: what it is, who provides it, and whether it works here. */
data class CapabilityRecord(
    val id: String,
    val name: String,
    val description: String,
    val provider: Provider,
    val privilege: Privilege,
    val availability: Availability,
    val operations: List<String> = emptyList(),
    val diagnostics: List<String> = emptyList(),
)

/**
 * A subsystem probes its own capabilities. Probes must not report success for something they did
 * not check: an unprobed capability is [AvailabilityState.NOT_VERIFIED], not AVAILABLE.
 * Probes may block (they run commands); the registry runs them off the main thread.
 */
interface ZairenkaiSubsystem {
    val id: String
    val displayName: String
    val provider: Provider
    fun probe(): List<CapabilityRecord>
}

data class RegistrySnapshot(val records: List<CapabilityRecord>, val refreshing: Boolean, val refreshedAtMs: Long) {
    fun byId(id: String): CapabilityRecord? = records.firstOrNull { it.id == id }
    fun availability(id: String): Availability =
        byId(id)?.availability ?: Availability(AvailabilityState.INITIALIZATION_FAILED, "capability $id is not registered")
}

class IntegrationRegistry(
    val subsystems: List<ZairenkaiSubsystem>,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val bySubsystem = LinkedHashMap<String, List<CapabilityRecord>>()
    private val state = MutableStateFlow(RegistrySnapshot(emptyList(), refreshing = false, refreshedAtMs = 0))
    val snapshot: StateFlow<RegistrySnapshot> = state.asStateFlow()

    init {
        val ids = subsystems.map { it.id }
        require(ids.size == ids.toSet().size) { "duplicate subsystem ids: $ids" }
    }

    private fun probeSafely(s: ZairenkaiSubsystem): List<CapabilityRecord> = try {
        s.probe()
    } catch (e: Exception) {
        listOf(
            CapabilityRecord(
                id = "${s.id}.probe",
                name = s.displayName,
                description = "Subsystem probe",
                provider = s.provider,
                privilege = Privilege.NONE,
                availability = Availability(
                    AvailabilityState.INITIALIZATION_FAILED,
                    "${e.javaClass.simpleName}: ${e.message ?: ""}".take(300),
                ),
            ),
        )
    }

    private fun publish(): RegistrySnapshot {
        val records = subsystems.flatMap { bySubsystem[it.id].orEmpty() }
        val ids = records.map { it.id }
        check(ids.size == ids.toSet().size) { "duplicate capability ids: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}" }
        return RegistrySnapshot(records, refreshing = false, refreshedAtMs = clock()).also { state.value = it }
    }

    /** Probe every subsystem concurrently. A failing probe becomes an INITIALIZATION_FAILED record. */
    suspend fun refresh(): RegistrySnapshot = mutex.withLock {
        state.value = state.value.copy(refreshing = true)
        val results = coroutineScope {
            subsystems.map { s -> async(io) { s.id to probeSafely(s) } }.awaitAll()
        }
        results.forEach { (id, records) -> bySubsystem[id] = records }
        publish()
    }

    /** Re-probe one subsystem (after an action, for example). */
    suspend fun refresh(subsystemId: String): RegistrySnapshot = mutex.withLock {
        val s = subsystems.firstOrNull { it.id == subsystemId } ?: return@withLock state.value
        bySubsystem[s.id] = withContext(io) { probeSafely(s) }
        publish()
    }
}
