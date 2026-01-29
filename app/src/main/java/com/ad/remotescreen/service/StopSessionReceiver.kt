package com.ad.remotescreen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Broadcast receiver for handling stop session action from notification.
 */
@AndroidEntryPoint
class StopSessionReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == RemoteAssistanceService.ACTION_STOP_SESSION) {
            context?.let {
                val serviceIntent = Intent(it, RemoteAssistanceService::class.java).apply {
                    action = RemoteAssistanceService.ACTION_STOP_SESSION
                }
                it.startService(serviceIntent)
            }
        }
    }
}
