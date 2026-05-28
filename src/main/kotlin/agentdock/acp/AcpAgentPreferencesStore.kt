package agentdock.acp

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import agentdock.utils.atomicWriteText
import java.io.File

@Serializable
data class AcpAgentPreference(
    val modelId: String = "",
    val modeId: String = "",
    val hiddenModelIds: Set<String> = emptySet()
)

@Serializable
data class AcpAgentPreferencesState(
    val lastAgentId: String = "",
    val agents: Map<String, AcpAgentPreference> = emptyMap(),
    val enabledSystemAdapters: Set<String> = emptySet()
)

object AcpAgentPreferencesStore {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val lock = Any()

    private fun stateFile(): File = File(AcpAdapterPaths.getBaseRuntimeDir(), "acp-agent-preferences.json")

    fun load(): AcpAgentPreferencesState = synchronized(lock) {
        val file = stateFile()
        if (!file.isFile) {
            return@synchronized save(AcpAgentPreferencesState())
        }
        val loaded = runCatching {
            json.decodeFromString<AcpAgentPreferencesState>(file.readText())
        }.getOrDefault(AcpAgentPreferencesState())
        save(loaded)
    }

    fun save(state: AcpAgentPreferencesState): AcpAgentPreferencesState = synchronized(lock) {
        val normalized = normalize(state)
        val file = stateFile()
        file.parentFile?.mkdirs()
        file.atomicWriteText(json.encodeToString(normalized))
        normalized
    }

    fun lastAgentId(): String? = load().lastAgentId.takeIf { it.isNotBlank() }

    fun preferenceFor(adapterId: String): AcpAgentPreference? {
        if (adapterId.isBlank()) return null
        return load().agents[adapterId]?.takeIf { it.modelId.isNotBlank() || it.modeId.isNotBlank() }
    }

    fun rememberAgent(adapterId: String) {
        val trimmedAdapterId = adapterId.trim()
        if (trimmedAdapterId.isEmpty()) return
        updateState { current -> current.copy(lastAgentId = trimmedAdapterId) }
    }

    fun isSystemAdapterEnabled(adapterId: String): Boolean {
        val trimmedAdapterId = adapterId.trim()
        if (trimmedAdapterId.isEmpty()) return false
        return load().enabledSystemAdapters.contains(trimmedAdapterId)
    }

    fun setSystemAdapterEnabled(adapterId: String, enabled: Boolean) {
        val trimmedAdapterId = adapterId.trim()
        if (trimmedAdapterId.isEmpty()) return
        updateState { current ->
            val nextEnabled = if (enabled) {
                current.enabledSystemAdapters + trimmedAdapterId
            } else {
                current.enabledSystemAdapters - trimmedAdapterId
            }
            current.copy(enabledSystemAdapters = nextEnabled)
        }
    }

    fun rememberModel(adapterId: String, modelId: String) {
        val trimmedAdapterId = adapterId.trim()
        val trimmedModelId = modelId.trim()
        if (trimmedAdapterId.isEmpty() || trimmedModelId.isEmpty()) return
        updateState { current ->
            val existing = current.agents[trimmedAdapterId] ?: AcpAgentPreference()
            current.copy(
                lastAgentId = current.lastAgentId,
                agents = current.agents + (trimmedAdapterId to existing.copy(modelId = trimmedModelId))
            )
        }
    }

    fun rememberMode(adapterId: String, modeId: String) {
        val trimmedAdapterId = adapterId.trim()
        val trimmedModeId = modeId.trim()
        if (trimmedAdapterId.isEmpty() || trimmedModeId.isEmpty()) return
        updateState { current ->
            val existing = current.agents[trimmedAdapterId] ?: AcpAgentPreference()
            current.copy(
                lastAgentId = current.lastAgentId,
                agents = current.agents + (trimmedAdapterId to existing.copy(modeId = trimmedModeId))
            )
        }
    }

    fun hiddenModelIdsFor(adapterId: String): Set<String> {
        val trimmedAdapterId = adapterId.trim()
        if (trimmedAdapterId.isEmpty()) return emptySet()
        return load().agents[trimmedAdapterId]?.hiddenModelIds.orEmpty()
    }

    fun setHiddenModels(adapterId: String, modelIds: Set<String>) {
        val trimmedAdapterId = adapterId.trim()
        if (trimmedAdapterId.isEmpty()) return
        val normalizedModelIds = modelIds
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .toSet()
        updateState { current ->
            val existing = current.agents[trimmedAdapterId] ?: AcpAgentPreference()
            current.copy(
                lastAgentId = current.lastAgentId,
                agents = current.agents + (trimmedAdapterId to existing.copy(hiddenModelIds = normalizedModelIds))
            )
        }
    }

    private fun updateState(transform: (AcpAgentPreferencesState) -> AcpAgentPreferencesState): AcpAgentPreferencesState =
        synchronized(lock) {
            val file = stateFile()
            val current = if (file.isFile) {
                runCatching {
                    json.decodeFromString<AcpAgentPreferencesState>(file.readText())
                }.getOrDefault(AcpAgentPreferencesState())
            } else {
                AcpAgentPreferencesState()
            }
            save(transform(current))
        }

    private fun normalize(state: AcpAgentPreferencesState): AcpAgentPreferencesState {
        val normalizedAgents = state.agents.entries.mapNotNull { (adapterId, pref) ->
            val trimmedAdapterId = adapterId.trim()
            if (trimmedAdapterId.isEmpty()) {
                null
            } else {
                val normalizedPref = AcpAgentPreference(
                    modelId = pref.modelId.trim(),
                    modeId = pref.modeId.trim(),
                    hiddenModelIds = pref.hiddenModelIds
                        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                        .toSet()
                )
                if (
                    normalizedPref.modelId.isEmpty() &&
                    normalizedPref.modeId.isEmpty() &&
                    normalizedPref.hiddenModelIds.isEmpty()
                ) {
                    null
                } else {
                    trimmedAdapterId to normalizedPref
                }
            }
        }.toMap()
        val normalizedLastAgentId = state.lastAgentId.trim().takeIf { it.isNotEmpty() } ?: ""
        val normalizedEnabledSystemAdapters = state.enabledSystemAdapters
            .mapNotNull { it.trim().takeIf { adapterId -> adapterId.isNotEmpty() } }
            .toSet()
        return AcpAgentPreferencesState(
            lastAgentId = normalizedLastAgentId,
            agents = normalizedAgents,
            enabledSystemAdapters = normalizedEnabledSystemAdapters
        )
    }
}
