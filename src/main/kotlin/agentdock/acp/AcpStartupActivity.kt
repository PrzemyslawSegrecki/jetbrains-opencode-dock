package opencodedock.acp

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import opencodedock.mcp.McpConfigStore
import opencodedock.settings.GlobalSettingsStore

class AcpStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        McpConfigStore.ensureConfigFileExists()
        AcpClientService.getInstance(project).initializeDownloadedAdaptersInBackground()
        if (GlobalSettingsStore.load().quotaWidgetEnabled) {
            AcpQuotaService.getInstance().updateQuotas()
        }
    }
}
