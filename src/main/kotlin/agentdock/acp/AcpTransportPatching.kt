package opencodedock.acp

import com.agentclientprotocol.rpc.JsonRpcMessage
import com.agentclientprotocol.rpc.JsonRpcRequest
import com.agentclientprotocol.rpc.JsonRpcResponse
import com.agentclientprotocol.transport.MessageListener as TransportMessageListener
import com.agentclientprotocol.transport.Transport
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

private val SESSION_METHODS = setOf("session/load", "session/resume")

internal class SessionIdPatchingTransport(
    private val delegate: Transport
) : Transport, AutoCloseable by delegate {

    private val requestSessionIds = ConcurrentHashMap<String, String>()

    override val state: StateFlow<Transport.State> get() = delegate.state

    override fun start() = delegate.start()

    override fun send(message: JsonRpcMessage) {
        if (message is JsonRpcRequest) {
            val methodName = message.method.toString()
            if (methodName in SESSION_METHODS) {
                val params = message.params?.jsonObject
                val sessionId = params?.get("sessionId")?.jsonPrimitive?.content
                if (sessionId != null) {
                    requestSessionIds[message.id.toString()] = sessionId
                }
            }
        }
        delegate.send(message)
    }

    override fun onMessage(handler: TransportMessageListener) {
        delegate.onMessage { message ->
            handler(patchResponse(message))
        }
    }

    override fun onError(handler: (Throwable) -> Unit) = delegate.onError(handler)
    override fun onClose(handler: () -> Unit) = delegate.onClose(handler)

    override fun close() = delegate.close()

    private fun patchResponse(message: JsonRpcMessage): JsonRpcMessage {
        if (message !is JsonRpcResponse) return message
        val result = message.result ?: return message
        val sessionId = requestSessionIds.remove(message.id.toString()) ?: return message
        val resultObj = result.jsonObject
        if ("sessionId" in resultObj) return message
        val patched = resultObj.toMutableMap().apply {
            put("sessionId", JsonPrimitive(sessionId))
        }
        return message.copy(result = JsonObject(patched))
    }
}
