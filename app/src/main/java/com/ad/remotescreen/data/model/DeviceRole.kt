package com.ad.remotescreen.data.model

/**
 * Represents the role of a device in the remote session.
 */
enum class DeviceRole {
    /** The device that views and controls another device */
    CONTROLLER,
    
    /** The device being viewed and controlled */
    TARGET
}
