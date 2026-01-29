package com.ad.remotescreen.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ad.remotescreen.capture.ScreenCaptureManager
import com.ad.remotescreen.data.model.ActivityLogEntry
import com.ad.remotescreen.data.model.ActivityType
import com.ad.remotescreen.data.model.SessionStatus
import com.ad.remotescreen.data.repository.SessionRepository
import com.ad.remotescreen.webrtc.WebRTCClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TargetUiState(
    val isConnected: Boolean = false,
    val isPaused: Boolean = false,
    val sessionEnded: Boolean = false,
    val partnerName: String = "Controller",
    val sessionDurationSeconds: Long = 0,
    val activityLog: List<ActivityLogEntry> = emptyList(),
    val isStreamingStarted: Boolean = false,
    val webRtcState: String = ""
)

@HiltViewModel
class TargetViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val webRTCClient: WebRTCClient,
    private val screenCaptureManager: ScreenCaptureManager
) : ViewModel() {
    
    companion object {
        private const val TAG = "TargetViewModel"
    }
    
    private val _uiState = MutableStateFlow(TargetUiState())
    val uiState: StateFlow<TargetUiState> = _uiState.asStateFlow()
    
    private var isTimerRunning = false
    private var isWebRtcInitialized = false
    
    init {
        // Observe session state
        viewModelScope.launch {
            sessionRepository.currentSession.collectLatest { session ->
                _uiState.update { state ->
                    state.copy(
                        isConnected = session?.status == SessionStatus.CONNECTED || 
                                     session?.status == SessionStatus.PAUSED,
                        isPaused = session?.status == SessionStatus.PAUSED,
                        sessionEnded = session?.status == SessionStatus.ENDED
                    )
                }
                
                // Start WebRTC when connected
                if (session?.status == SessionStatus.CONNECTED && !isWebRtcInitialized) {
                    startWebRTCStreaming()
                }
                
                if (session?.status == SessionStatus.CONNECTED && !isTimerRunning) {
                    startSessionTimer()
                }
            }
        }
        
        // Observe activity log
        viewModelScope.launch {
            sessionRepository.activityLog.collectLatest { log ->
                _uiState.update { it.copy(activityLog = log) }
            }
        }
        
        // Observe WebRTC connection state
        viewModelScope.launch {
            webRTCClient.connectionState.collectLatest { state ->
                Log.d(TAG, "WebRTC state: $state")
                _uiState.update { it.copy(webRtcState = state.name) }
                
                if (state == WebRTCClient.PeerConnectionState.CONNECTED) {
                    // Log that streaming started
                    sessionRepository.addActivityLog(
                        ActivityType.SESSION_STARTED,
                        "Screen streaming started"
                    )
                }
            }
        }
        
        // Forward screen capture frames to WebRTC
        viewModelScope.launch {
            screenCaptureManager.frameFlow.collectLatest { frameData ->
                webRTCClient.sendFrame(frameData)
            }
        }
    }
    
    /**
     * Starts WebRTC streaming.
     */
    private fun startWebRTCStreaming() {
        if (isWebRtcInitialized) {
            Log.w(TAG, "WebRTC already initialized")
            return
        }
        
        isWebRtcInitialized = true
        Log.i(TAG, "Starting WebRTC streaming as Target...")
        
        _uiState.update { it.copy(webRtcState = "Initializing...") }
        
        // Initialize WebRTC as target (this will create offer and send it)
        webRTCClient.initializeAsTarget()
        
        // Start screen capture if permission is granted
        if (screenCaptureManager.isCapturing.value) {
            Log.i(TAG, "Screen capture already running")
        } else {
            // Screen capture needs to be started via Activity (for permission)
            // Check if we already have permission and can start
            Log.w(TAG, "Screen capture not yet started - waiting for permission")
        }
        
        _uiState.update { it.copy(isStreamingStarted = true) }
    }
    
    /**
     * Called when screen capture permission is granted.
     */
    fun onScreenCapturePermissionGranted() {
        Log.i(TAG, "Screen capture permission granted, starting capture...")
        screenCaptureManager.startCapture(quality = 0.7f, maxFps = 15)
    }
    
    /**
     * Starts the session duration timer.
     */
    private fun startSessionTimer() {
        if (isTimerRunning) return
        isTimerRunning = true
        
        viewModelScope.launch {
            while (isTimerRunning && _uiState.value.isConnected) {
                delay(1000)
                _uiState.update { 
                    it.copy(sessionDurationSeconds = it.sessionDurationSeconds + 1)
                }
            }
        }
    }
    
    /**
     * Pauses the remote control session.
     */
    fun pauseSession() {
        sessionRepository.pauseSession()
        sessionRepository.addActivityLog(ActivityType.SESSION_PAUSED, "Session paused by user")
    }
    
    /**
     * Resumes the remote control session.
     */
    fun resumeSession() {
        sessionRepository.resumeSession()
        sessionRepository.addActivityLog(ActivityType.SESSION_RESUMED, "Session resumed")
    }
    
    /**
     * Emergency stop - immediately ends the session.
     */
    fun emergencyStop() {
        isTimerRunning = false
        isWebRtcInitialized = false
        
        sessionRepository.addActivityLog(ActivityType.EMERGENCY_STOP, "Emergency stop activated")
        sessionRepository.emergencyStop()
        
        webRTCClient.disconnect()
        screenCaptureManager.stopCapture()
    }
    
    override fun onCleared() {
        super.onCleared()
        isTimerRunning = false
        webRTCClient.disconnect()
        screenCaptureManager.stopCapture()
    }
}
