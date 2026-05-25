package agentdock.acp

import java.io.File
import java.util.concurrent.TimeUnit

private const val DEFAULT_NPM_LAUNCH_PATH = "dist/index.js"

internal const val ADAPTER_RUNTIME_SOURCE_LOCAL = "local"
internal const val ADAPTER_RUNTIME_SOURCE_SYSTEM = "system"

internal fun isWindowsLocalTarget(target: AcpExecutionTarget): Boolean =
    target == AcpExecutionTarget.LOCAL && AcpExecutionMode.isWindowsHost()

internal fun platformBinaryForTarget(
    binary: AcpAdapterConfig.PlatformBinary?,
    target: AcpExecutionTarget
): String? {
    return if (isWindowsLocalTarget(target)) binary?.win else binary?.unix
}

internal fun resolveTargetDependenciesPath(
    target: AcpExecutionTarget
): String = AcpAdapterPaths.getDependenciesDir().absolutePath

internal fun resolveDownloadPath(
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): String = File(AcpAdapterPaths.getDependenciesDir(), adapterInfo.id).absolutePath

internal fun resolveAdapterLaunchFile(
    adapterRoot: File,
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): File? {
    return when (adapterInfo.distribution.type) {
        AcpAdapterConfig.DistributionType.ARCHIVE -> {
            val binName = platformBinaryForTarget(adapterInfo.distribution.binaryName, target)
            if (binName.isNullOrBlank()) null else File(adapterRoot, binName)
        }
        AcpAdapterConfig.DistributionType.NPM -> {
            val launchPath = resolveNpmLaunchRelativePath(adapterInfo, target)
            File(adapterRoot, launchPath.replace("/", File.separator).replace("\\", File.separator))
        }
    }
}

internal fun resolveAdapterLaunchPath(
    adapterRootPath: String,
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): String? {
    return when (adapterInfo.distribution.type) {
        AcpAdapterConfig.DistributionType.ARCHIVE -> {
            val binName = platformBinaryForTarget(adapterInfo.distribution.binaryName, target)
            binName?.takeIf { it.isNotBlank() }?.let { joinAdapterPath(adapterRootPath, it, target) }
        }
        AcpAdapterConfig.DistributionType.NPM -> {
            val launchPath = resolveNpmLaunchRelativePath(adapterInfo, target)
            joinAdapterPath(adapterRootPath, launchPath, target)
        }
    }
}

internal fun resolveAdapterRuntimePath(
    adapterRootPath: String,
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): String? {
    val systemExecutable = resolveSystemExecutable(adapterInfo, target)
    if (systemExecutable != null && (adapterInfo.preferSystemExecutable || !isLocalLaunchAvailable(adapterRootPath, adapterInfo, target))) {
        return systemExecutable
    }
    return resolveAdapterLaunchPath(adapterRootPath, adapterInfo, target)
}

internal fun resolveAdapterRuntimeSource(
    adapterRootPath: String,
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): String? {
    val localAvailable = isLocalLaunchAvailable(adapterRootPath, adapterInfo, target)
    val systemAvailable = isSystemExecutableAvailable(adapterInfo, target)
    return when {
        systemAvailable && (adapterInfo.preferSystemExecutable || !localAvailable) -> ADAPTER_RUNTIME_SOURCE_SYSTEM
        localAvailable -> ADAPTER_RUNTIME_SOURCE_LOCAL
        systemAvailable -> ADAPTER_RUNTIME_SOURCE_SYSTEM
        else -> null
    }
}

internal fun isSystemExecutableAvailable(
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): Boolean {
    val executable = resolveSystemExecutable(adapterInfo, target) ?: return false
    return runCatching {
        val process = ProcessBuilder(buildAdapterExecutableCommand(executable, listOf("--version"))).redirectErrorStream(true).start()
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        finished && process.exitValue() == 0
    }.getOrDefault(false)
}

internal fun systemExecutableVersion(
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): String? {
    val executable = resolveSystemExecutable(adapterInfo, target) ?: return null
    return runCatching {
        val process = ProcessBuilder(buildAdapterExecutableCommand(executable, listOf("--version"))).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        if (!finished || process.exitValue() != 0) return null
        Regex("""(\d+\.\d+[\d.\-]*)""").find(output)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

internal fun buildAdapterLaunchCommand(
    adapterRootPath: String,
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    projectPath: String?,
    target: AcpExecutionTarget
): List<String> {
    resolveSystemExecutable(adapterInfo, target)?.let { executable ->
        if (adapterInfo.preferSystemExecutable || !isLocalLaunchAvailable(adapterRootPath, adapterInfo, target)) {
            if (isSystemExecutableAvailable(adapterInfo, target)) {
                return buildAdapterExecutableCommand(executable, adapterInfo.args)
            }
        }
    }

    val launchPath = resolveAdapterLaunchPath(adapterRootPath, adapterInfo, target)
        ?: throw IllegalStateException("Missing launch target for adapter '${adapterInfo.id}'")
    return buildAdapterExecutableCommand(File(launchPath).absolutePath, adapterInfo.args)
}

internal fun resolvePatchRoot(adapterRoot: File, adapterInfo: AcpAdapterConfig.AdapterInfo): File {
    return when (adapterInfo.distribution.type) {
        AcpAdapterConfig.DistributionType.ARCHIVE -> adapterRoot
        AcpAdapterConfig.DistributionType.NPM -> resolveNpmPackageRoot(adapterRoot, adapterInfo)
    }
}

private fun resolveNpmPackageRoot(adapterRoot: File, adapterInfo: AcpAdapterConfig.AdapterInfo): File {
    val packageName = adapterInfo.distribution.packageName
        ?: throw IllegalStateException("Adapter '${adapterInfo.id}' missing distribution.packageName in configuration")
    return File(adapterRoot, "node_modules${File.separator}$packageName")
}

internal fun resolveNpmPackageRootPath(
    adapterRootPath: String,
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): String {
    val packageName = adapterInfo.distribution.packageName
        ?: throw IllegalStateException("Adapter '${adapterInfo.id}' missing distribution.packageName in configuration")
    return joinAdapterPath(adapterRootPath, "node_modules/$packageName", target)
}

private fun resolveNpmLaunchRelativePath(
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): String {
    val launchBinary = platformBinaryForTarget(adapterInfo.launchBinary, target).orEmpty().trim()
    if (launchBinary.isNotEmpty()) return launchBinary

    val launchPath = adapterInfo.launchPath.ifBlank { DEFAULT_NPM_LAUNCH_PATH }
    val packageName = adapterInfo.distribution.packageName
        ?: throw IllegalStateException("Adapter '${adapterInfo.id}' missing distribution.packageName in configuration")
    return "node_modules/$packageName/$launchPath"
}

private fun resolveSystemExecutable(
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): String? = platformBinaryForTarget(adapterInfo.systemExecutable, target)?.trim()?.takeIf { it.isNotEmpty() }

private fun isLocalLaunchAvailable(
    adapterRootPath: String,
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): Boolean = resolveAdapterLaunchFile(File(adapterRootPath), adapterInfo, target)?.isFile == true

internal fun buildAdapterExecutableCommand(executable: String, args: List<String>): MutableList<String> {
    val executableFile = File(executable)
    val name = executableFile.name.lowercase()
    val base = when {
        name.endsWith(".cmd") || name.endsWith(".bat") -> mutableListOf("cmd.exe", "/c", executable)
        name.endsWith(".ps1") -> mutableListOf(
            "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", executable
        )
        name.endsWith(".js") || name.endsWith(".mjs") -> {
            val node = AcpNodeRuntimeResolver.resolveAvailable()?.node
                ?: if (AcpExecutionMode.isWindowsHost()) "node.exe" else "node"
            mutableListOf(node, executable)
        }
        else -> mutableListOf(executable)
    }
    base.addAll(args)
    return base
}

private fun joinAdapterPath(base: String, relative: String, target: AcpExecutionTarget): String {
    val separator = File.separator
    val normalizedRelative = relative.replace("/", File.separator).replace("\\", File.separator)
    return if (base.endsWith(separator)) base + normalizedRelative else base + separator + normalizedRelative
}
