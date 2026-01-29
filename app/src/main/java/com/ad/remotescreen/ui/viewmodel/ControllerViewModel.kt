package com.ad.remotescreen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ad.remotescreen.data.model.GestureCommand
import com.ad.remotescreen.data.model.SessionStatus
import com.ad.remotescreen.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ControllerUiState(
    val isConnected: Boolean = false,
    val isStreaming: Boolean = false,
    val connectionQuality: Float = 1f,
    val sessionDurationSeconds: Long = 0,
    val showTextInput: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ControllerViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ControllerUiState())
    val uiState: StateFlow<ControllerUiState> = _uiState.asStateFlow()
    
    private var isTimerRunning = false
    
    /**
     * Connects to the target device.
     */
    fun connect(pairingCode: String) {
        viewModelScope.launch {
            // Simulate connection
            delay(1500)
            _uiState.update { 
                it.copy(
                    isConnected = true,
                    isStreaming = true
                )
            }
            sessionRepository.onConnected("partner-device-id")
            startSessionTimer()
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
     * Sends a gesture command to the target device.
     */
    fun sendGesture(command: GestureCommand) {
        viewModelScope.launch {
            // In real implementation, send via ControlClient
            sessionRepository.logGestureReceived(command.type)
        }
    }
    
    /**
     * Shows the text input dialog.
     */
    fun showTextInputDialog() {
        _uiState.update { it.copy(showTextInput = true) }
    }
    
    /**
     * Hides the text input dialog.
     */
    fun hideTextInputDialog() {
        _uiState.update { it.copy(showTextInput = false) }
    }
    
    /**
     * Disconnects from the target device.
     */
    fun disconnect() {
        isTimerRunning = false
        _uiState.update { 
            it.copy(
                isConnected = false,
                isStreaming = false
            )
        }
        sessionRepository.endSession()
    }
    
    override fun onCleared() {
        super.onCleared()
        isTimerRunning = false
    }
}
