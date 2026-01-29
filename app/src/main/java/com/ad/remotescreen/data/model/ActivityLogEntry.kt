package com.ad.remotescreen.data.model

/**
 * Represents an activity log entry for transparency and user visibility.
 */
data class ActivityLogEntry(
    val id: String,
    val timestamp: Long,
    val type: ActivityType,
    val description: String,
    val sessionId: String? = null
)

/**
 * Types of activities logged for user transparency.
 */
enum class ActivityType {
    SESSION_STARTED,
    SESSION_ENDED,
    SESSION_PAUSED,
    SESSION_RESUMED,
    PERMISSION_GRANTED,
    PERMISSION_DENIED,
    GESTURE_RECEIVED,
    CONNECTION_ESTABLISHED,
    CONNECTION_LOST,
    EMERGENCY_STOP
}
