package com.ad.remotescreen.control

import android.util.Log
import com.ad.remotescreen.data.model.GestureCommand
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket client for sending and receiving remote control commands.
 * 
 * Separate from the signaling WebSocket to keep control traffic
 * isolated from WebRTC signaling.
 */
@Singleton
class ControlClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "ControlClient"
        
        // Cloud server URL - Deployed on Render.com
        // Works globally across any network/country
        private const val DEFAULT_SERVER_URL = "wss://remotescreen-backend.onrender.com/ws?type=control"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    
    private var webSocket: WebSocket? = null
    private var pairingCode: String = ""
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _commandChannel = Channel<GestureCommand>(Channel.UNLIMITED)
    val commandFlow: Flow<GestureCommand> = _commandChannel.receiveAsFlow()
    
    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }
    
    /**
     * Connects to the control server.
     * 
     * @param pairingCode The session pairing code
     * @param serverUrl Optional custom server URL
     */
    fun connect(pairingCode: String, serverUrl: String = DEFAULT_SERVER_URL) {
        this.pairingCode = pairingCode
        _connectionState.value = ConnectionState.CONNECTING
        
        val request = Request.Builder()
            .url("$serverUrl?code=$pairingCode")
            .build()
        
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Connected to control server")
                _connectionState.value = ConnectionState.CONNECTED
                
                // Start heartbeat
                startHeartbeat()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Connection closing: $reason")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Connection closed: $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Connection failed", t)
                _connectionState.value = ConnectionState.ERROR
            }
        })
    }
    
    /**
     * Starts sending periodic heartbeats to keep the connection alive.
     */
    private fun startHeartbeat() {
        scope.launch {
            while (_connectionState.value == ConnectionState.CONNECTED) {
                sendRawMessage("""{"type":"heartbeat","sessionId":"$pairingCode"}""")
                kotlinx.coroutines.delay(30000) // Every 30 seconds
            }
        }
    }
    
    /**
     * Handles incoming control messages.
     */
    private fun handleMessage(text: String) {
        try {
            val jsonObject = gson.fromJson(text, JsonObject::class.java)
            val type = jsonObject.get("type")?.asString ?: return
            
            when (type) {
                "gesture" -> {
                    val command = parseGestureCommand(jsonObject)
                    command?.let {
                        scope.launch { _commandChannel.send(it) }
                    }
                }
                "heartbeat-ack" -> {
                    Log.d(TAG, "Heartbeat acknowledged")
                }
                "session-end" -> {
                    Log.i(TAG, "Session ended by peer")
                    disconnect()
                }
                else -> {
                    Log.w(TAG, "Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }
    
    /**
     * Parses a gesture command from JSON.
     */
    private fun parseGestureCommand(json: JsonObject): GestureCommand? {
        val action = json.get("action")?.asString ?: return null
        
        return when (action) {
            "tap" -> GestureCommand.Tap(
                x = json.get("x")?.asFloat ?: 0f,
                y = json.get("y")?.asFloat ?: 0f
            )
            "swipe" -> GestureCommand.Swipe(
                startX = json.get("startX")?.asFloat ?: 0f,
                startY = json.get("startY")?.asFloat ?: 0f,
                endX = json.get("endX")?.asFloat ?: 0f,
                endY = json.get("endY")?.asFloat ?: 0f,
                duration = json.get("duration")?.asLong ?: 300
            )
            "longpress" -> GestureCommand.LongPress(
                x = json.get("x")?.asFloat ?: 0f,
                y = json.get("y")?.asFloat ?: 0f,
                duration = json.get("duration")?.asLong ?: 1000
            )
            "scroll" -> GestureCommand.Scroll(
                startX = json.get("startX")?.asFloat ?: 0f,
                startY = json.get("startY")?.asFloat ?: 0f,
                deltaX = json.get("deltaX")?.asFloat ?: 0f,
                deltaY = json.get("deltaY")?.asFloat ?: 0f,
                duration = json.get("duration")?.asLong ?: 500
            )
            "text" -> GestureCommand.TextInput(
                text = json.get("text")?.asString ?: ""
            )
            "back" -> GestureCommand.Back()
            "home" -> GestureCommand.Home()
            "recents" -> GestureCommand.Recents()
            else -> null
        }
    }
    
    /**
     * Sends a gesture command to the target device.
     */
    suspend fun sendCommand(command: GestureCommand) {
        val message = buildString {
            append("""{"type":"gesture","sessionId":"$pairingCode",""")
            when (command) {
                is GestureCommand.Tap -> {
                    append(""""action":"tap","x":${command.x},"y":${command.y}""")
                }
                is GestureCommand.Swipe -> {
                    append(""""action":"swipe","startX":${command.startX},"startY":${command.startY},"endX":${command.endX},"endY":${command.endY},"duration":${command.duration}""")
                }
                is GestureCommand.LongPress -> {
                    append(""""action":"longpress","x":${command.x},"y":${command.y},"duration":${command.duration}""")
                }
                is GestureCommand.Scroll -> {
                    append(""""action":"scroll","startX":${command.startX},"startY":${command.startY},"deltaX":${command.deltaX},"deltaY":${command.deltaY},"duration":${command.duration}""")
                }
                is GestureCommand.TextInput -> {
                    append(""""action":"text","text":"${command.text.replace("\"", "\\\"")}"""")
                }
                is GestureCommand.Back -> {
                    append(""""action":"back"""")
                }
                is GestureCommand.Home -> {
                    append(""""action":"home"""")
                }
                is GestureCommand.Recents -> {
                    append(""""action":"recents"""")
                }
            }
            append("}")
        }
        
        sendRawMessage(message)
    }
    
    /**
     * Sends a raw JSON message.
     */
    private fun sendRawMessage(message: String) {
        webSocket?.send(message)
    }
    
    /**
     * Disconnects from the control server.
     */
    fun disconnect() {
        webSocket?.close(1000, "Session ended")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
