package com.ad.remotescreen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ad.remotescreen.MainActivity
import com.ad.remotescreen.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for creating and managing notifications.
 * 
 * Creates persistent notifications during remote sessions to:
 * 1. Keep the foreground service alive
 * 2. Inform the user that remote control is active
 * 3. Provide quick access to stop the session
 */
@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context
) {
    companion object {
        const val CHANNEL_ID_SESSION = "remote_session_channel"
        const val CHANNEL_ID_CONNECTION = "connection_channel"
        
        const val NOTIFICATION_ID_SESSION = 1001
        const val NOTIFICATION_ID_CONNECTION = 1002
        
        private const val ACTION_STOP_SESSION = "com.ad.remotescreen.STOP_SESSION"
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Creates notification channels for Android O+.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Session channel - high importance for active control notification
            val sessionChannel = NotificationChannel(
                CHANNEL_ID_SESSION,
                "Remote Session",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for active remote control sessions"
                setShowBadge(true)
            }
            
            // Connection channel - default importance for connection status
            val connectionChannel = NotificationChannel(
                CHANNEL_ID_CONNECTION,
                "Connection Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for connection status updates"
            }
            
            notificationManager.createNotificationChannels(listOf(sessionChannel, connectionChannel))
        }
    }
    
    /**
     * Creates the foreground service notification for target device.
     * This notification is MANDATORY and informs the user that
     * their device is being remotely controlled.
     */
    fun createTargetSessionNotification(): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(ACTION_STOP_SESSION).apply {
            setPackage(context.packageName)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_ID_SESSION)
            .setContentTitle("Remote Control Active")
            .setContentText("This device is being remotely controlled")
            .setSmallIcon(R.drawable.ic_screen_share)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPendingIntent)
            .addAction(
                R.drawable.ic_stop,
                "STOP",
                stopPendingIntent
            )
            .setColor(context.getColor(R.color.notification_color))
            .build()
    }
    
    /**
     * Creates the foreground service notification for controller device.
     */
    fun createControllerSessionNotification(): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_ID_SESSION)
            .setContentTitle("Remote Session Active")
            .setContentText("Connected to remote device")
            .setSmallIcon(R.drawable.ic_remote_control)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPendingIntent)
            .setColor(context.getColor(R.color.notification_color))
            .build()
    }
    
    /**
     * Creates a notification for waiting for connection.
     */
    fun createWaitingNotification(pairingCode: String): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_ID_CONNECTION)
            .setContentTitle("Waiting for Connection")
            .setContentText("Pairing code: $pairingCode")
            .setSmallIcon(R.drawable.ic_link)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent)
            .build()
    }
    
    /**
     * Updates an existing notification.
     */
    fun updateNotification(notificationId: Int, notification: Notification) {
        notificationManager.notify(notificationId, notification)
    }
    
    /**
     * Cancels a notification.
     */
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}
