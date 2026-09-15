package com.ad.remotescreen

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ad.remotescreen.capture.ScreenCaptureManager
import com.ad.remotescreen.ui.navigation.AppNavigation
import com.ad.remotescreen.ui.theme.RemoteScreenTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main Activity for the Remote Screen application.
 * Uses Jetpack Compose for the UI and Hilt for dependency injection.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        
        // Static reference to allow screen capture request from anywhere
        private var instance: MainActivity? = null
        
        fun requestScreenCapture() {
            instance?.launchScreenCaptureRequest()
        }
    }
    
    @Inject
    lateinit var screenCaptureManager: ScreenCaptureManager
    
    // Activity result launcher for screen capture permission
    private val screenCapturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Log.i(TAG, "Screen capture permission granted")
            val granted = screenCaptureManager.handleActivityResult(result.resultCode, result.data)
            if (granted) {
                // Start capturing
                screenCaptureManager.startCapture(quality = 0.7f, maxFps = 15)
                Log.i(TAG, "Screen capture started!")
            }
        } else {
            Log.w(TAG, "Screen capture permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        enableEdgeToEdge()
        
        setContent {
            RemoteScreenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }
    
    private fun launchScreenCaptureRequest() {
        Log.i(TAG, "Requesting screen capture permission...")
        try {
            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val intent = mediaProjectionManager.createScreenCaptureIntent()
            screenCapturePermissionLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting screen capture", e)
        }
    }
}