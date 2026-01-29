package com.ad.remotescreen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ad.remotescreen.data.model.ActivityLogEntry
import com.ad.remotescreen.data.model.SessionStatus
import com.ad.remotescreen.data.repository.SessionRepository
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
    val activityLog: List<ActivityLogEntry> = emptyList()
)

@HiltViewModel
class TargetViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TargetUiState())
    val uiState: StateFlow<TargetUiState> = _uiState.asStateFlow()
    
    private var isTimerRunning = false
    
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
        
        // Simulate connection for demo
        viewModelScope.launch {
            delay(2000)
            sessionRepository.onConnected("controller-device")
        }
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
    }
    
    /**
     * Resumes the remote control session.
     */
    fun resumeSession() {
        sessionRepository.resumeSession()
    }
    
    /**
     * Emergency stop - immediately ends the session.
     */
    fun emergencyStop() {
        isTimerRunning = false
        sessionRepository.emergencyStop()
    }
    
    override fun onCleared() {
        super.onCleared()
        isTimerRunning = false
    }
}
