package com.ad.remotescreen.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ad.remotescreen.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Settings Activity for the Accessibility Service.
 * This is referenced in accessibility_service_config.xml
 * and allows users to configure the service from system settings.
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Redirect to the main app's settings screen
        // In a real app, you might have a dedicated settings layout here
        finish()
    }
}
