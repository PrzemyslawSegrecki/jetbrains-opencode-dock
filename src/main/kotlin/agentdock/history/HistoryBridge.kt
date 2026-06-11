package opencodedock.history

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.cef.browser.CefBrowser
import opencodedock.utils.escapeForJsString
import java.util.concurrent.ConcurrentHashMap

private val permissiveJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

class HistoryBridge(
    private val browser: JBCefBrowser,
    private val project: Project,
    private val scope: CoroutineScope
) {
    @Serializable
    private data class DeleteHistoryPayload(
        val projectPath: String,
        val conversationIds: List<String>
    )

    @Serializable
    private data class RenameHistoryPayload(
        val projectPath: String,
        val conversationId: String,
        val newTitle: String
    )

    @Serializable
    private data class DeleteHistoryResultPayload(
        val success: Boolean,
        val requestedConversationIds: List<String>,
        val failures: List<DeleteConversationFailure> = emptyList()
    )

    private var listHistoryQuery: JBCefJSQuery? = null
    private var syncHistoryQuery: JBCefJSQuery? = null
    private var deleteHistoryQuery: JBCefJSQuery? = null
    private var renameHistoryQuery: JBCefJSQuery? = null

    @Volatile
    private var cachedHistoryJson: String = "[]"
    private val syncingProjects = ConcurrentHashMap.newKeySet<String>()

    fun install() {
        val defaultProjectPath = project.basePath ?: System.getProperty("user.dir")

        listHistoryQuery = JBCefJSQuery.create(browser as com.intellij.ui.jcef.JBCefBrowserBase).apply {
            addHandler { payload ->
                val projectPath = payload?.trim()?.takeUnless { it.isEmpty() || it == "undefined" } ?: defaultProjectPath

                pushHistoryList(cachedHistoryJson)
                triggerBackgroundSync(projectPath)

                JBCefJSQuery.Response("ok")
            }
        }

        syncHistoryQuery = JBCefJSQuery.create(browser as com.intellij.ui.jcef.JBCefBrowserBase).apply {
            addHandler { payload ->
                val projectPath = payload?.trim()?.takeUnless { it.isEmpty() || it == "undefined" } ?: defaultProjectPath

                scope.launch(Dispatchers.Default) {
                    syncAndUpdateCache(projectPath)
                }
                JBCefJSQuery.Response("ok")
            }
        }

        deleteHistoryQuery = JBCefJSQuery.create(browser as com.intellij.ui.jcef.JBCefBrowserBase).apply {
            addHandler { payload ->
                if (payload.isNullOrBlank()) return@addHandler JBCefJSQuery.Response(null, -1, "Empty payload")

                scope.launch(Dispatchers.Default) {
                    val request = runCatching { permissiveJson.decodeFromString<DeleteHistoryPayload>(payload) }.getOrNull()
                    if (request == null) {
                        sendJsError("Invalid delete payload")
                        return@launch
                    }
                    try {
                        val result = OpenCodeDockHistoryService.deleteConversations(request.projectPath, request.conversationIds)
                        pushDeleteResult(
                            DeleteHistoryResultPayload(
                                success = result.success,
                                requestedConversationIds = request.conversationIds,
                                failures = result.failures
                            )
                        )
                        triggerBackgroundSync(request.projectPath)
                    } catch (e: Exception) {
                        pushDeleteResult(
                            DeleteHistoryResultPayload(
                                success = false,
                                requestedConversationIds = request.conversationIds,
                                failures = request.conversationIds.map { conversationId ->
                                    DeleteConversationFailure(conversationId, "Error: ${e.message ?: e}")
                                }
                            )
                        )
                    }
                }
                JBCefJSQuery.Response("ok")
            }
        }

        renameHistoryQuery = JBCefJSQuery.create(browser as com.intellij.ui.jcef.JBCefBrowserBase).apply {
            addHandler { payload ->
                if (payload.isNullOrBlank()) return@addHandler JBCefJSQuery.Response(null, -1, "Empty payload")

                scope.launch(Dispatchers.Default) {
                    val request = runCatching { permissiveJson.decodeFromString<RenameHistoryPayload>(payload) }.getOrNull()
                    if (request == null) {
                        sendJsError("Invalid rename payload")
                        return@launch
                    }
                    try {
                        val success = OpenCodeDockHistoryService.renameConversation(request.projectPath, request.conversationId, request.newTitle)
                        if (success) {
                            triggerBackgroundSync(request.projectPath)
                        } else {
                            sendJsError("Failed to rename conversation")
                        }
                    } catch (e: Exception) {
                        sendJsError("Error during rename: ${e.message}")
                    }
                }
                JBCefJSQuery.Response("ok")
            }
        }
    }

    private fun triggerBackgroundSync(projectPath: String) {
        if (!syncingProjects.add(projectPath)) return
        scope.launch(Dispatchers.IO) {
            try {
                syncAndUpdateCache(projectPath)
            } finally {
                syncingProjects.remove(projectPath)
            }
        }
    }

    private suspend fun syncAndUpdateCache(projectPath: String) {
        val history = OpenCodeDockHistoryService.syncAndGetHistoryList(projectPath)
        val json = permissiveJson.encodeToString(history)
        cachedHistoryJson = json
        pushHistoryList(json)
    }

    fun injectApi(cefBrowser: CefBrowser) {
        val listInject = listHistoryQuery?.inject("projectPath") ?: "console.error('[HistoryBridge] List query not ready')"
        val syncInject = syncHistoryQuery?.inject("projectPath") ?: "console.error('[HistoryBridge] Sync query not ready')"
        val deleteInject = deleteHistoryQuery?.inject("JSON.stringify(payload)") ?: "console.error('[HistoryBridge] Delete query not ready')"
        val renameInject = renameHistoryQuery?.inject("JSON.stringify(payload)") ?: "console.error('[HistoryBridge] Rename query not ready')"

        val script = """
            (function() {
                window.__onHistoryList = window.__onHistoryList || function(list) {};
                window.__onHistoryDeleteResult = window.__onHistoryDeleteResult || function(result) {};

                window.__requestHistoryList = function(projectPath) {
                    try { $listInject } catch(e) { console.error('[HistoryBridge] Request error', e); }
                };

                window.__syncHistoryList = function(projectPath) {
                    try { $syncInject } catch(e) { console.error('[HistoryBridge] Sync error', e); }
                };

                window.__deleteHistoryConversations = function(payload) {
                    if (!payload) return;
                    try {
                        $deleteInject
                    } catch(e) {
                        console.error('[HistoryBridge] Critical error during delete:', e);
                    }
                };

                window.__renameHistoryConversation = function(payload) {
                    if (!payload) return;
                    try {
                        $renameInject
                    } catch(e) {
                        console.error('[HistoryBridge] Critical error during rename:', e);
                    }
                };
            })();
        """.trimIndent()
        cefBrowser.executeJavaScript(script, cefBrowser.url, 0)
    }

    private fun pushHistoryList(jsonArray: String) {
        val escaped = jsonArray.escapeForJsString()
        runOnEdt {
            browser.cefBrowser.executeJavaScript(
                "if(window.__onHistoryList) window.__onHistoryList(JSON.parse('$escaped'));",
                browser.cefBrowser.url, 0
            )
        }
    }

    private fun pushDeleteResult(result: DeleteHistoryResultPayload) {
        val escaped = permissiveJson.encodeToString(result).escapeForJsString()
        runOnEdt {
            browser.cefBrowser.executeJavaScript(
                "if(window.__onHistoryDeleteResult) window.__onHistoryDeleteResult(JSON.parse('$escaped'));",
                browser.cefBrowser.url, 0
            )
        }
    }

    private fun sendJsError(msg: String) {
        runOnEdt {
            browser.cefBrowser.executeJavaScript("console.error('[HistoryBridge] ' + '${msg.escapeForJsString()}');", browser.cefBrowser.url, 0)
        }
    }

    private fun runOnEdt(action: () -> Unit) = ApplicationManager.getApplication().invokeLater(action)
}