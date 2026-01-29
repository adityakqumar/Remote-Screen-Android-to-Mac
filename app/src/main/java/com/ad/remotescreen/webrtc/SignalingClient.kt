package com.ad.remotescreen.webrtc

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebSocket-based signaling client for WebRTC connection establishment.
 * 
 * Handles:
 * - SDP offer/answer exchange
 * - ICE candidate exchange
 * - Session management messages
 */
@Singleton
class SignalingClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "SignalingClient"
        
        // Cloud server URL - Deployed on Render.com
        // Works globally across any network/country
        private const val DEFAULT_SERVER_URL = "wss://remotescreen-backend.onrender.com/ws"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    
    private var webSocket: WebSocket? = null
    private var pairingCode: String = ""
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _offerChannel = Channel<SessionDescription>(Channel.CONFLATED)
    val offerFlow: Flow<SessionDescription> = _offerChannel.receiveAsFlow()
    
    private val _answerChannel = Channel<SessionDescription>(Channel.CONFLATED)
    val answerFlow: Flow<SessionDescription> = _answerChannel.receiveAsFlow()
    
    private val _iceCandidateChannel = Channel<IceCandidate>(Channel.UNLIMITED)
    val iceCandidateFlow: Flow<IceCandidate> = _iceCandidateChannel.receiveAsFlow()
    
    private val _peerJoined = MutableStateFlow(false)
    val peerJoined: StateFlow<Boolean> = _peerJoined.asStateFlow()
    
    private val _peerLeft = MutableStateFlow(false)
    val peerLeft: StateFlow<Boolean> = _peerLeft.asStateFlow()
    
    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }
    
    /**
     * Connects to the signaling server.
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
                Log.i(TAG, "Connected to signaling server")
                _connectionState.value = ConnectionState.CONNECTED
                
                // Send join message
                sendMessage(SignalingMessage(
                    type = "join",
                    sessionId = pairingCode
                ))
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
     * Handles incoming signaling messages.
     */
    private fun handleMessage(text: String) {
        try {
            val message = gson.fromJson(text, SignalingMessage::class.java)
            
            when (message.type) {
                "offer" -> {
                    message.sdp?.let { sdpString ->
                        val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpString)
                        scope.launch { _offerChannel.send(sdp) }
                    }
                }
                "answer" -> {
                    message.sdp?.let { sdpString ->
                        val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
                        scope.launch { _answerChannel.send(sdp) }
                    }
                }
                "ice-candidate" -> {
                    message.candidate?.let { candidateData ->
                        val candidate = IceCandidate(
                            candidateData.sdpMid,
                            candidateData.sdpMLineIndex,
                            candidateData.candidate
                        )
                        scope.launch { _iceCandidateChannel.send(candidate) }
                    }
                }
                "peer-joined" -> {
                    Log.i(TAG, "Peer joined the session!")
                    _peerJoined.value = true
                }
                "peer-left" -> {
                    Log.i(TAG, "Peer left the session")
                    _peerLeft.value = true
                    _peerJoined.value = false
                }
                else -> {
                    Log.w(TAG, "Unknown message type: ${message.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }
    
    /**
     * Sends an SDP offer to the peer.
     */
    suspend fun sendOffer(sdp: SessionDescription) {
        sendMessage(SignalingMessage(
            type = "offer",
            sessionId = pairingCode,
            sdp = sdp.description
        ))
    }
    
    /**
     * Sends an SDP answer to the peer.
     */
    suspend fun sendAnswer(sdp: SessionDescription) {
        sendMessage(SignalingMessage(
            type = "answer",
            sessionId = pairingCode,
            sdp = sdp.description
        ))
    }
    
    /**
     * Sends an ICE candidate to the peer.
     */
    suspend fun sendIceCandidate(candidate: IceCandidate) {
        sendMessage(SignalingMessage(
            type = "ice-candidate",
            sessionId = pairingCode,
            candidate = CandidateData(
                candidate = candidate.sdp,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex
            )
        ))
    }
    
    /**
     * Sends a message to the signaling server.
     */
    private fun sendMessage(message: SignalingMessage) {
        val json = gson.toJson(message)
        webSocket?.send(json)
        Log.d(TAG, "Sent: ${message.type}")
    }
    
    /**
     * Disconnects from the signaling server.
     */
    fun disconnect() {
        webSocket?.close(1000, "Session ended")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}

/**
 * Data class for signaling messages.
 */
data class SignalingMessage(
    @SerializedName("type") val type: String,
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("sdp") val sdp: String? = null,
    @SerializedName("candidate") val candidate: CandidateData? = null
)

/**
 * Data class for ICE candidate data.
 */
data class CandidateData(
    @SerializedName("candidate") val candidate: String,
    @SerializedName("sdpMid") val sdpMid: String,
    @SerializedName("sdpMLineIndex") val sdpMLineIndex: Int
)
