package opencodedock.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.SessionConfigOption
import com.agentclientprotocol.model.SessionConfigSelectGroup
import com.agentclientprotocol.model.SessionConfigSelectOption
import com.agentclientprotocol.model.SessionConfigSelectOptions
import com.agentclientprotocol.model.SessionConfigValueId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(UnstableApi::class)
class AcpSessionConfigOptionsTest {

    @Test
    fun `model config option becomes runtime model metadata`() {
        val metadata = rawRuntimeMetadataFromConfigOptions(
            listOf(
                SessionConfigOption.select(
                    id = "model",
                    name = "Model",
                    currentValue = "anthropic/claude-sonnet-4-5",
                    options = SessionConfigSelectOptions.Flat(
                        listOf(
                            selectOption("anthropic/claude-sonnet-4-5", "Anthropic/Claude Sonnet 4.5"),
                            selectOption("openai/gpt-5", "OpenAI/GPT-5"),
                            selectOption("google/gemini-3-pro", "Google/Gemini 3 Pro")
                        )
                    )
                )
            )
        )

        assertNotNull(metadata)
        assertEquals("anthropic/claude-sonnet-4-5", metadata.currentModelId)
        assertEquals(
            listOf("anthropic/claude-sonnet-4-5", "openai/gpt-5", "google/gemini-3-pro"),
            metadata.availableModels.map { it.modelId }
        )
    }

    @Test
    fun `grouped model config option becomes flat runtime model metadata`() {
        val metadata = rawRuntimeMetadataFromConfigOptions(
            listOf(
                SessionConfigOption.select(
                    id = "model",
                    name = "Model",
                    currentValue = "openai/gpt-5",
                    options = SessionConfigSelectOptions.Grouped(
                        listOf(
                            SessionConfigSelectGroup(
                                group = com.agentclientprotocol.model.SessionConfigGroupId("openai"),
                                name = "OpenAI",
                                options = listOf(selectOption("openai/gpt-5", "GPT-5"))
                            ),
                            SessionConfigSelectGroup(
                                group = com.agentclientprotocol.model.SessionConfigGroupId("anthropic"),
                                name = "Anthropic",
                                options = listOf(selectOption("anthropic/claude-sonnet-4-5", "Claude Sonnet 4.5"))
                            )
                        )
                    )
                )
            )
        )

        assertNotNull(metadata)
        assertEquals(listOf("openai/gpt-5", "anthropic/claude-sonnet-4-5"), metadata.availableModels.map { it.modelId })
    }

    @Test
    fun `mode config option becomes runtime mode metadata`() {
        val metadata = rawRuntimeMetadataFromConfigOptions(
            listOf(
                SessionConfigOption.select(
                    id = "mode",
                    name = "Session Mode",
                    currentValue = "build",
                    options = SessionConfigSelectOptions.Flat(
                        listOf(selectOption("build", "build"), selectOption("plan", "plan"))
                    )
                )
            )
        )

        assertNotNull(metadata)
        assertEquals("build", metadata.currentModeId)
        assertEquals(listOf("build", "plan"), metadata.availableModes.map { it.id })
    }

    @Test
    fun `effort config option becomes runtime variant metadata`() {
        val metadata = rawRuntimeMetadataFromConfigOptions(
            listOf(
                SessionConfigOption.select(
                    id = "effort",
                    name = "Effort",
                    currentValue = "high",
                    options = SessionConfigSelectOptions.Flat(
                        listOf(
                            selectOption("low", "Low"),
                            selectOption("default", "Default"),
                            selectOption("high", "High")
                        )
                    )
                )
            )
        )

        assertNotNull(metadata)
        assertEquals("high", metadata.currentVariant)
        assertEquals(listOf("low", "default", "high"), metadata.availableVariants)
    }

    private fun selectOption(value: String, name: String): SessionConfigSelectOption {
        return SessionConfigSelectOption(SessionConfigValueId(value), name)
    }
}
