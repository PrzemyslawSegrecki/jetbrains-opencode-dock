package opencodedock.acp

import java.io.File

/**
 * Resolves the OpenCode runtime. OpenCode Dock requires a system-installed
 * `opencode` executable on PATH and no longer installs a local runtime.
 */
object AcpAdapterPaths {
    private const val ADAPTER_NAME_OVERRIDE_PROPERTY = "opencodedock.acp.adapter.name"

    fun getAdapterInfo(adapterName: String? = null): AcpAdapterConfig.AdapterInfo {
        val resolvedName = resolveAdapterName(adapterName)
        return try {
            AcpAdapterConfig.getAdapterInfo(resolvedName)
        } catch (e: Exception) {
            throw IllegalStateException("ACP adapter '$resolvedName' not found in configuration.", e)
        }
    }

    private fun currentTarget(): AcpExecutionTarget = AcpExecutionMode.currentTarget()

    fun getBaseRuntimeDir(): File {
        val dir = AcpExecutionMode.localBaseRuntimeDir()
        dir.mkdirs()
        return dir
    }

    fun getDependenciesDir(): File {
        val dir = AcpExecutionMode.localDependenciesDir()
        dir.mkdirs()
        return dir
    }

    internal fun getExecutionTarget(): AcpExecutionTarget = currentTarget()

    internal fun getTargetDependenciesPath(
        target: AcpExecutionTarget = currentTarget()
    ): String = resolveTargetDependenciesPath(target)

    internal fun getDownloadPath(
        adapterName: String? = null,
        target: AcpExecutionTarget = currentTarget()
    ): String {
        val adapterInfo = getAdapterInfo(adapterName)
        return resolveDownloadPath(adapterInfo, target)
    }

    internal fun installedVersion(
        adapterName: String? = null,
        target: AcpExecutionTarget = currentTarget()
    ): String? {
        val adapterInfo = getAdapterInfo(adapterName)
        return systemExecutableVersion(adapterInfo, target)
    }

    internal fun isDownloaded(
        adapterName: String? = null,
        target: AcpExecutionTarget = currentTarget()
    ): Boolean {
        val adapterInfo = getAdapterInfo(adapterName)
        return isSystemExecutableAvailable(adapterInfo, target)
    }

    internal fun runtimeSource(
        adapterName: String? = null,
        target: AcpExecutionTarget = currentTarget()
    ): String? {
        val adapterInfo = getAdapterInfo(adapterName)
        return if (isSystemExecutableAvailable(adapterInfo, target)) ADAPTER_RUNTIME_SOURCE_SYSTEM else null
    }

    suspend fun getAdapterRoot(adapterName: String? = null): File? {
        return null
    }

    internal fun ensurePatched(adapterName: String? = null, target: AcpExecutionTarget = currentTarget()) = Unit

    fun resolveAdapterName(adapterName: String?): String {
        val explicit = adapterName?.trim().takeUnless { it.isNullOrEmpty() }
        if (explicit != null) return explicit
        val override = System.getProperty(ADAPTER_NAME_OVERRIDE_PROPERTY)?.trim()
        if (!override.isNullOrEmpty()) return override
        throw IllegalStateException(
            "ACP adapter name is required. Provide it explicitly or set system property '$ADAPTER_NAME_OVERRIDE_PROPERTY'."
        )
    }

    internal fun resolveLaunchFile(
        adapterRoot: File,
        adapterInfo: AcpAdapterConfig.AdapterInfo,
        target: AcpExecutionTarget = currentTarget()
    ): File? = resolveAdapterLaunchFile(adapterRoot, adapterInfo, target)

    internal fun resolveLaunchPath(
        adapterRootPath: String,
        adapterInfo: AcpAdapterConfig.AdapterInfo,
        target: AcpExecutionTarget = currentTarget()
    ): String? = resolveAdapterRuntimePath(adapterRootPath, adapterInfo, target)

    internal fun buildLaunchCommand(
        adapterRootPath: String,
        adapterInfo: AcpAdapterConfig.AdapterInfo,
        projectPath: String? = null,
        target: AcpExecutionTarget = currentTarget()
    ): List<String> = buildAdapterLaunchCommand(adapterRootPath, adapterInfo, projectPath, target)

}
