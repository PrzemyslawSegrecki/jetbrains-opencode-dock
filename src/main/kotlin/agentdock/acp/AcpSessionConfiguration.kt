package opencodedock.acp

import com.agentclientprotocol.model.ModelId
import com.agentclientprotocol.model.SessionConfigId
import com.agentclientprotocol.model.SessionConfigOptionValue
import com.agentclientprotocol.model.SessionModeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("OPT_IN_USAGE")
internal suspend fun AcpClientService.setModel(chatId: String, modelId: String): Boolean {
    val context = sessions[chatId] ?: return false
    val trimmedModelId = modelId.trim()
    val adapterName = context.activeAdapterNameRef.get() ?: return false
    if (context.activeModelIdRef.get() == trimmedModelId) {
        AcpAgentPreferencesStore.rememberModel(adapterName, trimmedModelId)
        return true
    }

    val adapterInfo = AcpAdapterPaths.getAdapterInfo(adapterName)
    return when (adapterInfo.modelChangeStrategy) {
        "restart-resume" -> runCatching {
            startAgent(chatId, adapterName, trimmedModelId, context.sessionIdRef.get())
            context.activeModelIdRef.set(trimmedModelId)
            AcpAgentPreferencesStore.rememberModel(adapterName, trimmedModelId)
            adapterRuntimeMetadataMap[adapterName]?.let { metadata ->
                adapterRuntimeMetadataMap[adapterName] = metadata.copy(currentModelId = trimmedModelId)
            }
            true
        }.getOrDefault(false)
        else -> {
            val session = context.session ?: return false
            runCatching {
                if (session.modelsSupported) {
                    withContext(Dispatchers.IO) {
                        session.setModel(ModelId(trimmedModelId))
                    }
                    context.activeModelIdRef.set(trimmedModelId)
                    AcpAgentPreferencesStore.rememberModel(adapterName, trimmedModelId)
                    adapterRuntimeMetadataMap[adapterName]?.let { metadata ->
                        adapterRuntimeMetadataMap[adapterName] = metadata.copy(currentModelId = trimmedModelId)
                    }
                } else {
                    if (!session.configOptionsSupported || selectConfigOption(session.configOptions.value, modelConfigOptionId()) == null) {
                        return@runCatching false
                    }
                    val response = withContext(Dispatchers.IO) {
                        session.setConfigOption(
                            SessionConfigId(modelConfigOptionId()),
                            SessionConfigOptionValue.of(trimmedModelId)
                        )
                    }
                    context.activeModelIdRef.set(trimmedModelId)
                    AcpAgentPreferencesStore.rememberModel(adapterName, trimmedModelId)
                    runtimeMetadataFromConfigOptions(adapterInfo, response.configOptions)?.let { metadata ->
                        adapterRuntimeMetadataMap[adapterName] = metadata.copy(currentModelId = trimmedModelId)
                    }
                }
                true
            }.getOrDefault(false)
        }
    }
}

@Suppress("OPT_IN_USAGE")
internal suspend fun AcpClientService.setMode(chatId: String, modeId: String): Boolean {
    val context = sessions[chatId] ?: return false
    val trimmedModeId = modeId.trim()
    val adapterName = context.activeAdapterNameRef.get()
    if (context.activeModeIdRef.get() == trimmedModeId) {
        if (!adapterName.isNullOrBlank()) {
            AcpAgentPreferencesStore.rememberMode(adapterName, trimmedModeId)
        }
        return true
    }

    val session = context.session ?: return false
    val adapterInfo = adapterName?.let { AcpAdapterPaths.getAdapterInfo(it) }
    return runCatching {
        if (session.modesSupported) {
            withContext(Dispatchers.IO) {
                session.setMode(SessionModeId(trimmedModeId))
            }
        } else {
            if (!session.configOptionsSupported || selectConfigOption(session.configOptions.value, modeConfigOptionId()) == null) {
                return@runCatching false
            }
            val response = withContext(Dispatchers.IO) {
                session.setConfigOption(
                    SessionConfigId(modeConfigOptionId()),
                    SessionConfigOptionValue.of(trimmedModeId)
                )
            }
            if (!adapterName.isNullOrBlank() && adapterInfo != null) {
                AcpAgentPreferencesStore.rememberMode(adapterName, trimmedModeId)
                runtimeMetadataFromConfigOptions(adapterInfo, response.configOptions)?.let { metadata ->
                    adapterRuntimeMetadataMap[adapterName] = metadata.copy(currentModeId = trimmedModeId)
                }
            }
        }
        context.activeModeIdRef.set(trimmedModeId)
        if (!adapterName.isNullOrBlank()) {
            AcpAgentPreferencesStore.rememberMode(adapterName, trimmedModeId)
            adapterRuntimeMetadataMap[adapterName]?.let { metadata ->
                adapterRuntimeMetadataMap[adapterName] = metadata.copy(currentModeId = trimmedModeId)
            }
        }
        true
    }.getOrDefault(false)
}

@Suppress("OPT_IN_USAGE")
internal suspend fun AcpClientService.setEffort(chatId: String, variantId: String): Boolean {
    val context = sessions[chatId] ?: return false
    val trimmedVariantId = variantId.trim()
    val adapterName = context.activeAdapterNameRef.get() ?: return false
    if (context.activeVariantRef.get() == trimmedVariantId) return true

    val adapterInfo = AcpAdapterPaths.getAdapterInfo(adapterName)
    val session = context.session ?: return false
    if (!session.configOptionsSupported || selectConfigOption(session.configOptions.value, effortConfigOptionId()) == null) {
        return false
    }

    return runCatching {
        val response = withContext(Dispatchers.IO) {
            session.setConfigOption(
                SessionConfigId(effortConfigOptionId()),
                SessionConfigOptionValue.of(trimmedVariantId)
            )
        }
        context.activeVariantRef.set(trimmedVariantId)
        runtimeMetadataFromConfigOptions(adapterInfo, response.configOptions)?.let { metadata ->
            adapterRuntimeMetadataMap[adapterName] = metadata
        }
        true
    }.getOrDefault(false)
}
