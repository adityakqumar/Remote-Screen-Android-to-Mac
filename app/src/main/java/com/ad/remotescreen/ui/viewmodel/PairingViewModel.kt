package com.ad.remotescreen.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ad.remotescreen.data.PairingCodeGenerator
import com.ad.remotescreen.data.repository.SessionRepository
import com.ad.remotescreen.data.model.SessionStatus
import com.ad.remotescreen.webrtc.SignalingClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PairingUiState(
    val pairingCode: String = "",
    val enteredCode: String = "",
    val isWaiting: Boolean = false,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val connectionStatus: String = "",
    val error: String? = null
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val signalingClient: SignalingClient
) : ViewModel() {
    
    companion object {
        private const val TAG = "PairingViewModel"
    }
    
    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()
    
    init {
        // Observe signaling connection state
        viewModelScope.launch {
            signalingClient.connectionState.collectLatest { state ->
                Log.d(TAG, "Signaling state: $state")
                when (state) {
                    SignalingClient.ConnectionState.CONNECTED -> {
                        _uiState.update { 
                            it.copy(
                                connectionStatus = "Connected to server, waiting for peer...",
                                isConnecting = false
                            )
                        }
                    }
                    SignalingClient.ConnectionState.CONNECTING -> {
                        _uiState.update { 
                            it.copy(connectionStatus = "Connecting to server...")
                        }
                    }
                    SignalingClient.ConnectionState.ERROR -> {
                        _uiState.update { 
                            it.copy(
                                error = "Failed to connect to server. Check network connection.",
                                isConnecting = false,
                                isWaiting = false
                            )
                        }
                    }
                    SignalingClient.ConnectionState.DISCONNECTED -> {
                        // Ignore initial disconnected state
                    }
                }
            }
        }
        
        // Observe peer joined - this triggers navigation to Target/Controller screen
        viewModelScope.launch {
            signalingClient.peerJoined.collectLatest { joined ->
                if (joined) {
                    Log.d(TAG, "Peer joined! Marking as connected.")
                    sessionRepository.onConnected("peer-connected")
                    _uiState.update { 
                        it.copy(
                            isConnected = true, 
                            isConnecting = false, 
                            isWaiting = false,
                            connectionStatus = "Peer connected!"
                        )
                    }
                }
            }
        }
        
        // Observe session state for connection success
        viewModelScope.launch {
            sessionRepository.currentSession.collectLatest { session ->
                when (session?.status) {
                    SessionStatus.CONNECTED -> {
                        _uiState.update { 
                            it.copy(
                                isConnected = true, 
                                isConnecting = false, 
                                isWaiting = false,
                                connectionStatus = "Connected!"
                            )
                        }
                    }
                    SessionStatus.ERROR -> {
                        _uiState.update { 
                            it.copy(
                                error = "Connection failed. Please try again.", 
                                isConnecting = false
                            )
                        }
                    }
                    else -> { /* ignore */ }
                }
            }
        }
    }
    
    /**
     * Initializes the screen for target device (generates pairing code).
     */
    fun initializeAsTarget() {
        val session = sessionRepository.createTargetSession()
        val code = session.pairingCode
        
        _uiState.update { 
            it.copy(
                pairingCode = code,
                isWaiting = true,
                connectionStatus = "Connecting to server..."
            )
        }
        
        // Connect to signaling server with the pairing code
        Log.d(TAG, "Target connecting with code: $code")
        signalingClient.connect(code)
        
        // Listen for peer joined
        viewModelScope.launch {
            signalingClient.connectionState.collectLatest { state ->
                if (state == SignalingClient.ConnectionState.CONNECTED) {
                    _uiState.update { 
                        it.copy(connectionStatus = "Waiting for controller to connect...")
                    }
                }
            }
        }
    }
    
    /**
     * Generates a new pairing code.
     */
    fun generateNewCode() {
        signalingClient.disconnect()
        sessionRepository.clearSession()
        initializeAsTarget()
    }
    
    /**
     * Updates the entered code (controller only).
     */
    fun updateEnteredCode(code: String) {
        _uiState.update { 
            it.copy(
                enteredCode = code,
                error = null
            )
        }
    }
    
    /**
     * Attempts to connect as controller.
     */
    fun connectAsController() {
        val code = _uiState.value.enteredCode
        
        if (!PairingCodeGenerator.isValidFormat(code)) {
            _uiState.update { it.copy(error = "Invalid code format") }
            return
        }
        
        _uiState.update { 
            it.copy(
                isConnecting = true, 
                error = null,
                connectionStatus = "Connecting to server..."
            )
        }
        
        // Create session and connect to signaling server
        sessionRepository.createControllerSession(code)
        Log.d(TAG, "Controller connecting with code: $code")
        signalingClient.connect(code)
        
        // Wait for connection and then mark as connected for navigation
        viewModelScope.launch {
            signalingClient.connectionState.collectLatest { state ->
                if (state == SignalingClient.ConnectionState.CONNECTED) {
                    // Give a brief moment for server to register
                    kotlinx.coroutines.delay(500)
                    
                    // Mark as connected for navigation
                    sessionRepository.onConnected("controller-connected")
                    _uiState.update { 
                        it.copy(
                            isConnected = true, 
                            pairingCode = code,
                            connectionStatus = "Connected!"
                        )
                    }
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Don't disconnect here - let the service maintain the connection
    }
}
