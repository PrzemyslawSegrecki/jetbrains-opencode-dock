package opencodedock.acp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class AcpPlatformCompatibilityTest {
    @Test
    fun `local Linux and macOS use unix npm launch binaries`() = withOsName("Linux") {
        val adapter = npmAdapter()

        val launchPath = resolveAdapterLaunchPath("/tmp/agent", adapter, AcpExecutionTarget.LOCAL).orEmpty()
            .replace("\\", "/")

        assertEquals("/tmp/agent/node_modules/.bin/tool", launchPath)
        assertFalse(launchPath.endsWith(".cmd"))
    }

    @Test
    fun `JavaScript launch files use node on unix local hosts`() = withOsName("Mac OS X") {
        val adapter = AcpAdapterConfig.AdapterInfo(
            id = "tool",
            name = "Tool",
            distribution = AcpAdapterConfig.Distribution(
                type = AcpAdapterConfig.DistributionType.NPM,
                version = "latest",
                packageName = "tool"
            ),
            launchPath = "dist/index.js"
        )

        val command = buildAdapterLaunchCommand("/tmp/agent", adapter, "/tmp/project", AcpExecutionTarget.LOCAL)

        assertEquals("node", command.first())
    }

    @Test
    fun `preferred system executable is used as runtime path`() = withOsName("Linux") {
        val adapter = npmAdapter().copy(
            systemExecutable = AcpAdapterConfig.PlatformBinary(unix = "tool"),
            preferSystemExecutable = true
        )

        val launchPath = resolveAdapterRuntimePath("/tmp/agent", adapter, AcpExecutionTarget.LOCAL)

        assertEquals("tool", launchPath)
    }

    @Test
    fun `windows system executable candidates fall back from cmd to exe`() = withOsName("Windows 11") {
        val candidates = systemExecutableCandidates("opencode.cmd", AcpExecutionTarget.LOCAL)

        assertTrue(candidates.any { it.endsWith("opencode.cmd", ignoreCase = true) })
        assertTrue(candidates.any { it.endsWith("opencode.exe", ignoreCase = true) })
        assertTrue(candidates.any { it.endsWith("opencode.ps1", ignoreCase = true) })
        assertTrue(candidates.any { it == "opencode" || it.endsWith("\\opencode", ignoreCase = true) })
        assertTrue(candidates.any { it.endsWith("opencode.bat", ignoreCase = true) })
    }

    @Test
    fun `unix system executable candidates are unchanged`() = withOsName("Linux") {
        assertContentEquals(
            listOf("opencode"),
            systemExecutableCandidates("opencode", AcpExecutionTarget.LOCAL)
        )
    }

    private fun npmAdapter(): AcpAdapterConfig.AdapterInfo {
        return AcpAdapterConfig.AdapterInfo(
            id = "tool",
            name = "Tool",
            distribution = AcpAdapterConfig.Distribution(
                type = AcpAdapterConfig.DistributionType.NPM,
                version = "latest",
                packageName = "tool"
            ),
            launchBinary = AcpAdapterConfig.PlatformBinary(
                win = "node_modules/.bin/tool.cmd",
                unix = "node_modules/.bin/tool"
            )
        )
    }

    private fun withOsName(value: String, block: () -> Unit) {
        val previous = System.getProperty("os.name")
        try {
            System.setProperty("os.name", value)
            block()
        } finally {
            System.setProperty("os.name", previous)
        }
    }
}
