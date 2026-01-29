package com.ad.remotescreen

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Main Application class for the Remote Screen app.
 * Uses Hilt for dependency injection.
 */
@HiltAndroidApp
class RemoteScreenApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // Initialize any app-wide configurations here
    }
}
