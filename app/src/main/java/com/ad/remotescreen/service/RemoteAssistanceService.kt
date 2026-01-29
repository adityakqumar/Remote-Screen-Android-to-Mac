package com.ad.remotescreen.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.ad.remotescreen.capture.ScreenCaptureManager
import com.ad.remotescreen.control.ControlClient
import com.ad.remotescreen.data.model.DeviceRole
import com.ad.remotescreen.data.model.GestureCommand
import com.ad.remotescreen.data.model.SessionStatus
import com.ad.remotescreen.data.repository.SessionRepository
import com.ad.remotescreen.webrtc.WebRTCClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main foreground service for remote assistance operations.
 * 
 * This service:
 * - Maintains WebRTC connection for screen streaming
 * - Handles WebSocket control channel
 * - Shows persistent notification during active sessions
 * - Manages session lifecycle
 */
@AndroidEntryPoint
class RemoteAssistanceService : Service() {
    
    companion object {
        private const val TAG = "RemoteAssistService"
        
        const val ACTION_START_TARGET = "com.ad.remotescreen.START_TARGET"
        const val ACTION_START_CONTROLLER = "com.ad.remotescreen.START_CONTROLLER"
        const val ACTION_STOP_SESSION = "com.ad.remotescreen.STOP_SESSION"
        
        const val EXTRA_PAIRING_CODE = "pairing_code"
        const val EXTRA_SERVER_URL = "server_url"
    }
    
    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var screenCaptureManager: ScreenCaptureManager
    @Inject lateinit var webRTCClient: WebRTCClient
    @Inject lateinit var controlClient: ControlClient
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val binder = LocalBinder()
    
    private var isRunning = false
    private var currentRole: DeviceRole? = null
    
    // Broadcast receiver for stop action from notification
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP_SESSION) {
                stopSession()
            }
        }
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): RemoteAssistanceService = this@RemoteAssistanceService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        
        // Register stop receiver
        val filter = IntentFilter(ACTION_STOP_SESSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, filter)
        }
        
        // Observe session changes
        serviceScope.launch {
            sessionRepository.currentSession.collectLatest { session ->
                when (session?.status) {
                    SessionStatus.CONNECTED -> onSessionConnected()
                    SessionStatus.ENDED, SessionStatus.ERROR -> stopSelf()
                    else -> { /* ignore */ }
                }
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service started with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_TARGET -> startAsTarget()
            ACTION_START_CONTROLLER -> {
                val pairingCode = intent.getStringExtra(EXTRA_PAIRING_CODE) ?: ""
                val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: ""
                startAsController(pairingCode, serverUrl)
            }
            ACTION_STOP_SESSION -> stopSession()
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
        
        unregisterReceiver(stopReceiver)
        cleanup()
        serviceScope.cancel()
    }
    
    /**
     * Starts the service as a target device (being controlled).
     */
    private fun startAsTarget() {
        if (isRunning) return
        
        currentRole = DeviceRole.TARGET
        isRunning = true
        
        // Create session
        val session = sessionRepository.createTargetSession()
        
        // Start as foreground service
        val notification = notificationHelper.createWaitingNotification(session.pairingCode)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID_SESSION,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_SESSION, notification)
        }
        
        // Initialize WebRTC and control clients
        serviceScope.launch {
            initializeConnections(session.pairingCode)
        }
        
        Log.i(TAG, "Started as target with pairing code: ${session.pairingCode}")
    }
    
    /**
     * Starts the service as a controller device.
     */
    private fun startAsController(pairingCode: String, serverUrl: String) {
        if (isRunning) return
        
        currentRole = DeviceRole.CONTROLLER
        isRunning = true
        
        // Create session
        sessionRepository.createControllerSession(pairingCode)
        
        // Start as foreground service
        val notification = notificationHelper.createControllerSessionNotification()
        startForeground(NotificationHelper.NOTIFICATION_ID_SESSION, notification)
        
        // Initialize connections
        serviceScope.launch {
            initializeConnections(pairingCode)
        }
        
        Log.i(TAG, "Started as controller with pairing code: $pairingCode")
    }
    
    /**
     * Initializes WebRTC and WebSocket connections.
     */
    private suspend fun initializeConnections(pairingCode: String) {
        try {
            // Connect signaling client first (this is shared)
            // Note: For target, signaling is already connected from PairingViewModel
            
            // Initialize WebRTC based on role
            if (currentRole == DeviceRole.TARGET) {
                webRTCClient.initializeAsTarget()
            } else {
                webRTCClient.initializeAsController()
            }
            
            // Connect control channel
            controlClient.connect(pairingCode)
            
            // If target, start handling incoming control commands
            if (currentRole == DeviceRole.TARGET) {
                controlClient.commandFlow.collect { command ->
                    handleIncomingCommand(command)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize connections", e)
            sessionRepository.onConnectionLost()
        }
    }
    
    /**
     * Called when session is connected.
     */
    private fun onSessionConnected() {
        if (currentRole == DeviceRole.TARGET) {
            // Update notification
            val notification = notificationHelper.createTargetSessionNotification()
            notificationHelper.updateNotification(
                NotificationHelper.NOTIFICATION_ID_SESSION,
                notification
            )
            
            // Enable accessibility service gestures
            RemoteAccessibilityService.enableSession()
            
            // Start screen capture if ready
            if (screenCaptureManager.isCapturing.value) {
                startScreenStreaming()
            }
        }
    }
    
    /**
     * Starts streaming screen frames via WebRTC.
     */
    private fun startScreenStreaming() {
        serviceScope.launch {
            screenCaptureManager.frameFlow.collect { frameData ->
                webRTCClient.sendFrame(frameData)
            }
        }
    }
    
    /**
     * Handles incoming gesture commands from the controller.
     */
    private fun handleIncomingCommand(command: GestureCommand) {
        Log.d(TAG, "Received command: ${command.type}")
        
        // Only process if session is active
        val session = sessionRepository.currentSession.value
        if (session?.status != SessionStatus.CONNECTED) {
            Log.w(TAG, "Ignoring command - session not connected")
            return
        }
        
        // Log the gesture for transparency
        sessionRepository.logGestureReceived(command.type)
        
        // Execute the gesture
        RemoteAccessibilityService.executeGesture(command)
    }
    
    /**
     * Sends a gesture command to the target device (controller only).
     */
    fun sendGesture(command: GestureCommand) {
        if (currentRole != DeviceRole.CONTROLLER) {
            Log.w(TAG, "Only controller can send gestures")
            return
        }
        
        serviceScope.launch {
            controlClient.sendCommand(command)
        }
    }
    
    /**
     * Stops the current session and cleans up.
     */
    fun stopSession() {
        Log.i(TAG, "Stopping session")
        
        // Disable accessibility service gestures
        RemoteAccessibilityService.disableSession()
        
        // End session
        sessionRepository.endSession()
        
        // Cleanup connections
        cleanup()
        
        // Stop the service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    /**
     * Emergency stop - immediately ends everything.
     */
    fun emergencyStop() {
        Log.w(TAG, "Emergency stop triggered")
        
        RemoteAccessibilityService.disableSession()
        sessionRepository.emergencyStop()
        cleanup()
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    /**
     * Cleans up all resources.
     */
    private fun cleanup() {
        isRunning = false
        currentRole = null
        
        screenCaptureManager.stopCapture()
        webRTCClient.disconnect()
        controlClient.disconnect()
    }
}
