package opencodedock.acp

import opencodedock.settings.GlobalSettingsStore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class QuotaDetail(
    val adapterId: String,
    val adapterName: String,
    val mainPercentage: Int,
    val details: List<String> = emptyList()
)

@Service(Service.Level.APP)
class AcpQuotaService : Disposable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _quotas = MutableStateFlow<Map<String, QuotaDetail>>(emptyMap())
    val quotas = _quotas.asStateFlow()
    private var pollingJob: Job? = null

    init {
        if (GlobalSettingsStore.load().quotaWidgetEnabled) {
            startPolling()
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                updateQuotas()
            }
        }
    }

    fun onQuotaWidgetEnabledChanged(enabled: Boolean) {
        if (enabled) {
            scope.launch { updateQuotas() }
            startPolling()
        } else {
            pollingJob?.cancel()
            pollingJob = null
            _quotas.update { emptyMap() }
        }
    }

    suspend fun updateQuotas() {
        // OpenCode does not expose a local quota endpoint. Quota state is only updated when
        // the runtime explicitly pushes usable data back through the bridge.
    }

    fun updateQuotaForAdapter(adapterId: String, rawJson: String) {
        if (adapterId.isBlank() || rawJson.isBlank()) return
    }

    override fun dispose() {
        scope.cancel()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5L * 60 * 1000

        fun getInstance(): AcpQuotaService =
            ApplicationManager.getApplication().getService(AcpQuotaService::class.java)
    }
}
