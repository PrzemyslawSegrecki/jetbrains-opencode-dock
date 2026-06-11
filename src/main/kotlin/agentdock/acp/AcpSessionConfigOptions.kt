package opencodedock.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.SessionConfigOption
import com.agentclientprotocol.model.SessionConfigSelectOption
import com.agentclientprotocol.model.SessionConfigSelectOptions

private const val MODEL_CONFIG_OPTION_ID = "model"
private const val EFFORT_CONFIG_OPTION_ID = "effort"
private const val MODE_CONFIG_OPTION_ID = "mode"

@OptIn(UnstableApi::class)
internal fun AcpClientService.runtimeMetadataFromConfigOptions(
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    configOptions: List<SessionConfigOption>
): AcpClientService.AdapterRuntimeMetadata? {
    val rawMetadata = rawRuntimeMetadataFromConfigOptions(configOptions) ?: return null
    return applyAdapterRuntimePreferences(
        adapterInfo = adapterInfo,
        currentModelId = rawMetadata.currentModelId,
        availableModels = rawMetadata.availableModels,
        currentModeId = rawMetadata.currentModeId,
        availableModes = rawMetadata.availableModes,
        currentVariant = rawMetadata.currentVariant,
        availableVariants = rawMetadata.availableVariants
    )
}

@OptIn(UnstableApi::class)
internal fun rawRuntimeMetadataFromConfigOptions(
    configOptions: List<SessionConfigOption>
): AcpClientService.AdapterRuntimeMetadata? {
    val modelSelect = selectConfigOption(configOptions, MODEL_CONFIG_OPTION_ID)
    val effortSelect = selectConfigOption(configOptions, EFFORT_CONFIG_OPTION_ID)
    val modeSelect = selectConfigOption(configOptions, MODE_CONFIG_OPTION_ID)
    if (modelSelect == null && modeSelect == null && effortSelect == null) return null

    return AcpClientService.AdapterRuntimeMetadata(
        currentModelId = modelSelect?.currentValue?.value,
        availableModels = modelSelect?.selectOptions()?.map { option ->
            AcpAdapterConfig.ModelInfo(
                modelId = option.value.value,
                name = option.name,
                description = option.description
            )
        } ?: emptyList(),
        currentModeId = modeSelect?.currentValue?.value,
        availableModes = modeSelect?.selectOptions()?.map { option ->
            AcpAdapterConfig.ModeInfo(
                id = option.value.value,
                name = option.name,
                description = option.description
            )
        } ?: emptyList(),
        currentVariant = effortSelect?.currentValue?.value,
        availableVariants = effortSelect?.selectOptions()?.map { it.value.value } ?: emptyList()
    )
}

@OptIn(UnstableApi::class)
internal fun selectConfigOption(
    configOptions: List<SessionConfigOption>,
    configId: String
): SessionConfigOption.Select? {
    return configOptions
        .filterIsInstance<SessionConfigOption.Select>()
        .firstOrNull { it.id.value == configId }
}

@OptIn(UnstableApi::class)
internal fun SessionConfigOption.Select.selectOptions(): List<SessionConfigSelectOption> {
    return when (val selectOptions = options) {
        is SessionConfigSelectOptions.Flat -> selectOptions.options
        is SessionConfigSelectOptions.Grouped -> selectOptions.groups.flatMap { it.options }
    }
}

internal fun modelConfigOptionId(): String = MODEL_CONFIG_OPTION_ID

internal fun effortConfigOptionId(): String = EFFORT_CONFIG_OPTION_ID

internal fun modeConfigOptionId(): String = MODE_CONFIG_OPTION_ID
