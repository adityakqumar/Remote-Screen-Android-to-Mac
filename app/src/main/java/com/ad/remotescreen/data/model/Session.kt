package com.ad.remotescreen.data.model

/**
 * Status of a remote session.
 */
enum class SessionStatus {
    /** Waiting for the other device to connect */
    WAITING_FOR_CONNECTION,
    
    /** Devices are connected and session is active */
    CONNECTED,
    
    /** Session is paused (screen visible but control disabled) */
    PAUSED,
    
    /** Session has ended */
    ENDED,
    
    /** An error occurred during the session */
    ERROR
}

/**
 * Represents a remote assistance session between two devices.
 * 
 * @property id Unique session identifier
 * @property pairingCode One-time code for device pairing
 * @property status Current session status
 * @property role The role of this device in the session
 * @property partnerId The ID of the connected partner device (if connected)
 * @property createdAt Timestamp when session was created
 * @property connectedAt Timestamp when devices connected (null if not yet connected)
 * @property endedAt Timestamp when session ended (null if still active)
 */
data class Session(
    val id: String,
    val pairingCode: String,
    val status: SessionStatus,
    val role: DeviceRole,
    val partnerId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val connectedAt: Long? = null,
    val endedAt: Long? = null
) {
    /**
     * Whether the session is currently active (connected or paused).
     */
    val isActive: Boolean
        get() = status == SessionStatus.CONNECTED || status == SessionStatus.PAUSED
    
    /**
     * Whether remote control is currently enabled.
     */
    val isControlEnabled: Boolean
        get() = status == SessionStatus.CONNECTED
}
