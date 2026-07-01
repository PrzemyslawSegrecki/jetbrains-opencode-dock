package opencodedock.acp

import java.io.File
import java.util.concurrent.TimeUnit

private const val DEFAULT_NPM_LAUNCH_PATH = "dist/index.js"

internal const val ADAPTER_RUNTIME_SOURCE_LOCAL = "local"
internal const val ADAPTER_RUNTIME_SOURCE_SYSTEM = "system"

internal data class SystemExecutableProbeResult(
    val executable: String? = null,
    val version: String? = null,
    val error: String? = null
) {
    val available: Boolean get() = executable != null
}

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
        AcpAdapterConfig.DistributionType.SYSTEM -> null
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
        AcpAdapterConfig.DistributionType.SYSTEM ->
            platformBinaryForTarget(adapterInfo.systemExecutable, target)?.takeIf { it.isNotBlank() }
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
    if (adapterInfo.preferSystemExecutable) {
        return systemExecutable
    }
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
    if (adapterInfo.preferSystemExecutable) {
        return if (systemAvailable) ADAPTER_RUNTIME_SOURCE_SYSTEM else null
    }
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
    return probeSystemExecutable(adapterInfo, target).available
}

internal fun systemExecutableVersion(
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): String? {
    return probeSystemExecutable(adapterInfo, target).version
}

internal fun systemExecutableProbeError(
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): String? {
    return probeSystemExecutable(adapterInfo, target).error
}

internal fun buildAdapterLaunchCommand(
    adapterRootPath: String,
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    projectPath: String?,
    target: AcpExecutionTarget
): List<String> {
    probeSystemExecutable(adapterInfo, target).executable?.let { executable ->
        if (adapterInfo.preferSystemExecutable || !isLocalLaunchAvailable(adapterRootPath, adapterInfo, target)) {
            return buildAdapterExecutableCommand(executable, adapterInfo.args)
        }
    } ?: run {
        if (adapterInfo.preferSystemExecutable) {
            throw IllegalStateException("OpenCode executable was not found on PATH. Install OpenCode system-wide and reopen the IDE.")
        }
    }

    val launchPath = resolveAdapterLaunchPath(adapterRootPath, adapterInfo, target)
        ?: throw IllegalStateException("Missing launch target for adapter '${adapterInfo.id}'")
    return buildAdapterExecutableCommand(File(launchPath).absolutePath, adapterInfo.args)
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

internal fun systemExecutableCandidates(
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): List<String> = systemExecutableCandidates(resolveSystemExecutable(adapterInfo, target), target)

internal fun systemExecutableCandidates(
    executable: String?,
    target: AcpExecutionTarget
): List<String> {
    val raw = executable?.trim().takeUnless { it.isNullOrEmpty() } ?: return emptyList()
    if (!isWindowsLocalTarget(target)) return listOf(raw)

    val file = File(raw)
    val parent = file.parentFile
    val stem = file.nameWithoutExtension.ifBlank { file.name }
    val basePath = parent?.let { File(it, stem).path } ?: stem

    val names = buildList {
        add(raw)
        add("$basePath.exe")
        add("$basePath.ps1")
        add(basePath)
        add("$basePath.cmd")
        add("$basePath.bat")
    }.distinct()

    return names.flatMap { candidate ->
        val resolved = resolveWindowsExecutableCandidate(candidate)
        if (resolved != null) listOf(resolved, candidate) else listOf(candidate)
    }.distinct()
}

internal fun resolveAvailableSystemExecutable(
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): String? = probeSystemExecutable(adapterInfo, target).executable

internal fun probeSystemExecutable(
    adapterInfo: AcpAdapterConfig.AdapterInfo,
    target: AcpExecutionTarget
): SystemExecutableProbeResult {
    val candidates = systemExecutableCandidates(adapterInfo, target)
    val errors = mutableListOf<String>()

    candidates.forEach { executable ->
        val probe = runSystemExecutableVersionCheck(executable)
        if (probe?.available == true) {
            return probe
        }
        probe?.error?.takeIf { it.isNotBlank() }?.let { errors.add(it) }
    }

    val detail = when {
        errors.isEmpty() -> null
        errors.size == 1 -> errors.first()
        else -> errors.distinct().joinToString(" | ")
    }

    return SystemExecutableProbeResult(error = detail)
}

private fun runSystemExecutableVersionCheck(executable: String): SystemExecutableProbeResult {
    return runCatching {
        val process = ProcessBuilder(buildAdapterExecutableCommand(executable, listOf("--version"))).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        if (!finished) {
            return SystemExecutableProbeResult(error = "OpenCode process did not answer to --version within 10 seconds.")
        }
        if (process.exitValue() != 0) {
            val detail = output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            return SystemExecutableProbeResult(
                error = detail ?: "OpenCode exited with code ${process.exitValue()} while probing --version."
            )
        }
        val version = Regex("""(\d+\.\d+[\d.\-]*)""").find(output)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        SystemExecutableProbeResult(executable = executable, version = version)
    }.getOrElse { error ->
        SystemExecutableProbeResult(error = error.message ?: error.toString())
    }
}

private fun resolveWindowsExecutableCandidate(candidate: String): String? {
    val file = File(candidate)
    if (file.isAbsolute) {
        return file.takeIf { it.isFile }?.absolutePath
    }

    return windowsExecutableSearchDirs()
        .asSequence()
        .map { dir -> File(dir, candidate) }
        .firstOrNull { it.isFile }
        ?.absolutePath
}

private fun windowsExecutableSearchDirs(): List<File> {
    val env = System.getenv()
    val pathKey = env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
    val pathDirs = env[pathKey]
        .orEmpty()
        .split(File.pathSeparator)
        .mapNotNull { entry -> entry.trim().takeIf { it.isNotEmpty() }?.let(::File) }

    val home = System.getProperty("user.home").orEmpty()
    val appData = env["APPDATA"].orEmpty()
    val localAppData = env["LOCALAPPDATA"].orEmpty()
    val programData = env["ProgramData"].orEmpty()

    val knownDirs = listOf(
        appData.takeIf { it.isNotBlank() }?.let { File(it, "npm") },
        home.takeIf { it.isNotBlank() }?.let { File(it, ".opencode\\bin") },
        home.takeIf { it.isNotBlank() }?.let { File(it, "bin") },
        home.takeIf { it.isNotBlank() }?.let { File(it, "scoop\\shims") },
        programData.takeIf { it.isNotBlank() }?.let { File(it, "chocolatey\\bin") },
        localAppData.takeIf { it.isNotBlank() }?.let { File(it, "Microsoft\\WinGet\\Links") }
    )

    return (pathDirs + knownDirs.filterNotNull())
        .filter { it.isDirectory }
        .distinctBy { it.absolutePath.lowercase() }
}

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
