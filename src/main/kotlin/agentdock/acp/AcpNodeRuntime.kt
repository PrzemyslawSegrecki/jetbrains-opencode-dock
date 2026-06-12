package opencodedock.acp

import com.intellij.execution.configurations.GeneralCommandLine
import java.io.File
import java.util.concurrent.TimeUnit

internal data class AcpNodeRuntime(
    val node: String,
    val npm: String,
    val npx: String,
    val pathEntries: List<File> = emptyList(),
    val managed: Boolean = false
)

internal object AcpNodeRuntimeResolver {
    fun resolveAvailable(): AcpNodeRuntime? {
        resolveSystem()?.let { return it }
        resolveManaged()?.let { return it }
        return null
    }

    fun applyTo(builder: ProcessBuilder, runtime: AcpNodeRuntime) {
        val path = mergedPath(runtime.pathEntries, builder.environment())
        if (path.isNotBlank()) {
            builder.environment()[pathKey(builder.environment())] = path
        }
    }

    fun applyTo(commandLine: GeneralCommandLine, runtime: AcpNodeRuntime): GeneralCommandLine {
        val env = System.getenv().toMutableMap()
        val path = mergedPath(runtime.pathEntries, env)
        if (path.isNotBlank()) env[pathKey(env)] = path
        return commandLine.withEnvironment(env)
    }

    fun resolveManaged(): AcpNodeRuntime? {
        val root = managedVersionRoot() ?: return null
        val node = nodeIn(root) ?: return null
        val npm = npmIn(root) ?: return null
        val npx = npxIn(root) ?: return null
        val runtime = AcpNodeRuntime(
            node = node.absolutePath,
            npm = npm.absolutePath,
            npx = npx.absolutePath,
            pathEntries = listOfNotNull(node.parentFile, npm.parentFile).distinctBy { it.absolutePath.lowercase() },
            managed = true
        )
        return runtime.takeIf { smokeTest(it) }
    }

    fun managedRoot(): File = File(AcpAdapterPaths.getDependenciesDir(), "node")

    private fun managedVersionRoot(): File? {
        val root = managedRoot()
        if (!root.isDirectory) return null
        return root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("v") }
            ?.maxWithOrNull(compareBy<File> { semanticVersionPart(it.name, 0) }
                .thenBy { semanticVersionPart(it.name, 1) }
                .thenBy { semanticVersionPart(it.name, 2) })
    }

    private fun semanticVersionPart(version: String, index: Int): Int =
        version.trimStart('v')
            .split('.')
            .getOrNull(index)
            ?.takeWhile { it.isDigit() }
            ?.toIntOrNull()
            ?: 0

    private fun resolveSystem(): AcpNodeRuntime? {

        val candidates = buildList {
            add(emptyList<File>())
            nvmdBinDir()?.takeIf { it.isDirectory }?.let { add(listOf(it)) }
        }

        candidates.forEach { entries ->
            val runtime = AcpNodeRuntime(
                node = commandName("node"),
                npm = commandName("npm"),
                npx = commandName("npx"),
                pathEntries = entries,
                managed = false
            )
            if (smokeTest(runtime)) return runtime
        }

        return resolveFromNvmdWhich()
    }

    private fun resolveFromNvmdWhich(): AcpNodeRuntime? {
        val nvmdBin = nvmdBinDir()?.takeIf { it.isDirectory } ?: return null
        val nvmd = executableIn(nvmdBin, "nvmd") ?: File(nvmdBin, if (AcpExecutionMode.isWindowsHost()) "nvmd.cmd" else "nvmd")
        if (!nvmd.exists()) return null
        val pathEntries = listOf(nvmdBin)
        val node = runWhich(nvmd.absolutePath, "node", pathEntries) ?: return null
        val npm = runWhich(nvmd.absolutePath, "npm", pathEntries) ?: return null
        val npx = runWhich(nvmd.absolutePath, "npx", pathEntries) ?: commandName("npx")
        val runtime = AcpNodeRuntime(node, npm, npx, pathEntries, managed = false)
        return runtime.takeIf { smokeTest(it) }
    }

    private fun runWhich(nvmd: String, name: String, pathEntries: List<File>): String? {
        val process = ProcessBuilder(nvmd, "which", name).redirectErrorStream(true)
        applyTo(process, AcpNodeRuntime(commandName("node"), commandName("npm"), commandName("npx"), pathEntries))
        return runCatching {
            val started = process.start()
            val output = started.inputStream.bufferedReader().use { it.readText() }.trim()
            if (!started.waitFor(10, TimeUnit.SECONDS)) {
                started.destroyForcibly()
                return null
            }
            output.lines().firstOrNull { it.isNotBlank() }?.trim()?.takeIf { started.exitValue() == 0 }
        }.getOrNull()
    }

    private fun smokeTest(runtime: AcpNodeRuntime): Boolean =
        versionCheck(runtime.node, runtime) && versionCheck(runtime.npm, runtime)

    private fun versionCheck(command: String, runtime: AcpNodeRuntime): Boolean {
        return runCatching {
            val processBuilder = ProcessBuilder(command, "--version").redirectErrorStream(true)
            applyTo(processBuilder, runtime)
            val process = processBuilder.start()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            finished && process.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun commandName(base: String): String {
        return when {
            !AcpExecutionMode.isWindowsHost() -> base
            base == "node" -> "node.exe"
            else -> "$base.cmd"
        }
    }

    private fun executableIn(dir: File, base: String): File? {
        val names = if (AcpExecutionMode.isWindowsHost()) {
            listOf("$base.exe", "$base.cmd", base)
        } else {
            listOf(base)
        }
        return names.map { File(dir, it) }.firstOrNull { it.isFile }
    }

    private fun npmIn(root: File): File? =
        executableIn(root, "npm") ?: executableIn(File(root, "bin"), "npm")

    private fun npxIn(root: File): File? =
        executableIn(root, "npx") ?: executableIn(File(root, "bin"), "npx")

    private fun nodeIn(root: File): File? =
        executableIn(root, "node") ?: executableIn(File(root, "bin"), "node")

    private fun nvmdBinDir(): File? {
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() } ?: return null
        return File(File(home, ".nvmd"), "bin")
    }

    private fun pathKey(env: Map<String, String>): String =
        env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"

    private fun mergedPath(extraEntries: List<File>, env: Map<String, String>): String {
        val key = pathKey(env)
        val existing = env[key].orEmpty()
        val prefix = extraEntries
            .filter { it.isDirectory }
            .joinToString(File.pathSeparator) { it.absolutePath }
        return when {
            prefix.isBlank() -> existing
            existing.isBlank() -> prefix
            else -> "$prefix${File.pathSeparator}$existing"
        }
    }
}
