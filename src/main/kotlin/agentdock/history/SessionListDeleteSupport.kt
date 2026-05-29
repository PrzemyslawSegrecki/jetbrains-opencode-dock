package opencodedock.history

internal object SessionListDeleteSupport {
    fun resolveSourceFilePath(projectPath: String, adapterName: String, sessionId: String): String {
        if (projectPath.isBlank() || sessionId.isBlank() || adapterName != "opencode") return ""
        return ""
    }

    fun deleteSession(projectPath: String, adapterName: String, sessionId: String, sourceFilePath: String?): Boolean {
        return adapterName == "opencode" &&
            runAgentHistoryCliCommand(adapterName, projectPath, listOf("session", "delete", sessionId)) != null
    }
}
