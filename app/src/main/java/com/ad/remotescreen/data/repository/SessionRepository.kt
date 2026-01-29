package com.ad.remotescreen.data.repository

import com.ad.remotescreen.data.PairingCodeGenerator
import com.ad.remotescreen.data.model.ActivityLogEntry
import com.ad.remotescreen.data.model.ActivityType
import com.ad.remotescreen.data.model.DeviceRole
import com.ad.remotescreen.data.model.Session
import com.ad.remotescreen.data.model.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing remote assistance sessions.
 * Handles session creation, pairing, and lifecycle management.
 */
@Singleton
class SessionRepository @Inject constructor() {
    
    private val _currentSession = MutableStateFlow<Session?>(null)
    val currentSession: StateFlow<Session?> = _currentSession.asStateFlow()
    
    private val _activityLog = MutableStateFlow<List<ActivityLogEntry>>(emptyList())
    val activityLog: StateFlow<List<ActivityLogEntry>> = _activityLog.asStateFlow()
    
    /**
     * Creates a new session as a target device (being controlled).
     * Generates a pairing code for the controller to use.
     * 
     * @return The created session with pairing code
     */
    fun createTargetSession(): Session {
        val session = Session(
            id = UUID.randomUUID().toString(),
            pairingCode = PairingCodeGenerator.generate(),
            status = SessionStatus.WAITING_FOR_CONNECTION,
            role = DeviceRole.TARGET
        )
        _currentSession.value = session
        addActivityLog(ActivityType.SESSION_STARTED, "Session created, waiting for controller")
        return session
    }
    
    /**
     * Creates a new session as a controller device.
     * 
     * @param pairingCode The pairing code from the target device
     * @return The created session
     */
    fun createControllerSession(pairingCode: String): Session {
        val normalizedCode = PairingCodeGenerator.normalize(pairingCode)
        val session = Session(
            id = UUID.randomUUID().toString(),
            pairingCode = normalizedCode,
            status = SessionStatus.WAITING_FOR_CONNECTION,
            role = DeviceRole.CONTROLLER
        )
        _currentSession.value = session
        addActivityLog(ActivityType.SESSION_STARTED, "Attempting to connect to target device")
        return session
    }
    
    /**
     * Updates the session status to connected.
     * 
     * @param partnerId The ID of the connected partner device
     */
    fun onConnected(partnerId: String) {
        _currentSession.value?.let { session ->
            _currentSession.value = session.copy(
                status = SessionStatus.CONNECTED,
                partnerId = partnerId,
                connectedAt = System.currentTimeMillis()
            )
            addActivityLog(ActivityType.CONNECTION_ESTABLISHED, "Connected to partner device")
        }
    }
    
    /**
     * Pauses the current session (screen visible but control disabled).
     */
    fun pauseSession() {
        _currentSession.value?.let { session ->
            if (session.status == SessionStatus.CONNECTED) {
                _currentSession.value = session.copy(status = SessionStatus.PAUSED)
                addActivityLog(ActivityType.SESSION_PAUSED, "Session paused")
            }
        }
    }
    
    /**
     * Resumes a paused session.
     */
    fun resumeSession() {
        _currentSession.value?.let { session ->
            if (session.status == SessionStatus.PAUSED) {
                _currentSession.value = session.copy(status = SessionStatus.CONNECTED)
                addActivityLog(ActivityType.SESSION_RESUMED, "Session resumed")
            }
        }
    }
    
    /**
     * Ends the current session.
     * This should be called when either party wants to stop the session.
     */
    fun endSession() {
        _currentSession.value?.let { session ->
            _currentSession.value = session.copy(
                status = SessionStatus.ENDED,
                endedAt = System.currentTimeMillis()
            )
            addActivityLog(ActivityType.SESSION_ENDED, "Session ended")
        }
    }
    
    /**
     * Emergency stop - immediately ends the session.
     * Used when the target device user wants to immediately stop remote control.
     */
    fun emergencyStop() {
        _currentSession.value?.let { session ->
            _currentSession.value = session.copy(
                status = SessionStatus.ENDED,
                endedAt = System.currentTimeMillis()
            )
            addActivityLog(ActivityType.EMERGENCY_STOP, "Emergency stop triggered")
        }
    }
    
    /**
     * Handles connection loss.
     */
    fun onConnectionLost() {
        _currentSession.value?.let { session ->
            _currentSession.value = session.copy(status = SessionStatus.ERROR)
            addActivityLog(ActivityType.CONNECTION_LOST, "Connection to partner lost")
        }
    }
    
    /**
     * Clears the current session.
     */
    fun clearSession() {
        _currentSession.value = null
    }
    
    /**
     * Logs a gesture received (for transparency).
     */
    fun logGestureReceived(gestureType: String) {
        addActivityLog(ActivityType.GESTURE_RECEIVED, "Received $gestureType gesture")
    }
    
    /**
     * Logs a permission event.
     */
    fun logPermissionEvent(granted: Boolean, permissionName: String) {
        val type = if (granted) ActivityType.PERMISSION_GRANTED else ActivityType.PERMISSION_DENIED
        addActivityLog(type, "$permissionName permission ${if (granted) "granted" else "denied"}")
    }
    
    /**
     * Clears the activity log.
     */
    fun clearActivityLog() {
        _activityLog.value = emptyList()
    }
    
    /**
     * Adds an entry to the activity log.
     */
    fun addActivityLog(type: ActivityType, description: String) {
        val entry = ActivityLogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            type = type,
            description = description,
            sessionId = _currentSession.value?.id
        )
        _activityLog.value = _activityLog.value + entry
    }
}
