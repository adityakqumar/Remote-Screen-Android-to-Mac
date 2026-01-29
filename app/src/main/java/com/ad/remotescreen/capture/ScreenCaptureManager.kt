package com.ad.remotescreen.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages screen capture using MediaProjection API.
 * 
 * Flow:
 * 1. Request permission via requestScreenCapture()
 * 2. Handle the result with handleActivityResult()
 * 3. Start capture with startCapture()
 * 4. Receive frames via frameFlow
 * 5. Stop capture with stopCapture()
 */
@Singleton
class ScreenCaptureManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "ScreenCapture"
        const val REQUEST_CODE_SCREEN_CAPTURE = 1000
        
        private const val VIRTUAL_DISPLAY_NAME = "RemoteScreenCapture"
    }
    
    private val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()
    
    private val _frameChannel = Channel<ByteArray>(Channel.CONFLATED)
    val frameFlow: Flow<ByteArray> = _frameChannel.receiveAsFlow()
    
    // Store display metrics for coordinate translation
    private var displayWidth: Int = 0
    private var displayHeight: Int = 0
    private var displayDensity: Int = 0
    
    /**
     * Gets the display metrics (width, height, density).
     */
    fun getDisplayMetrics(): Triple<Int, Int, Int> {
        updateDisplayMetrics()
        return Triple(displayWidth, displayHeight, displayDensity)
    }
    
    private fun updateDisplayMetrics() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels
        displayDensity = metrics.densityDpi
    }
    
    /**
     * Creates an intent to request screen capture permission.
     * 
     * @param activity The activity to start the permission request from
     */
    fun requestScreenCapture(activity: Activity) {
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        activity.startActivityForResult(intent, REQUEST_CODE_SCREEN_CAPTURE)
    }
    
    /**
     * Handles the result from the screen capture permission request.
     * 
     * @param resultCode The result code from onActivityResult
     * @param data The intent data from onActivityResult
     * @return true if permission was granted
     */
    fun handleActivityResult(resultCode: Int, data: Intent?): Boolean {
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "Screen capture permission denied")
            return false
        }
        
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection stopped")
                stopCapture()
            }
        }, null)
        
        Log.i(TAG, "Screen capture permission granted")
        return true
    }
    
    /**
     * Starts screen capture and begins emitting frames.
     * 
     * @param quality Quality factor (0.0 to 1.0) for JPEG compression
     * @param maxFps Maximum frames per second
     */
    fun startCapture(quality: Float = 0.8f, maxFps: Int = 30) {
        if (_isCapturing.value) {
            Log.w(TAG, "Already capturing")
            return
        }
        
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection not initialized - request permission first")
            return
        }
        
        updateDisplayMetrics()
        
        // Create ImageReader with screen dimensions
        imageReader = ImageReader.newInstance(
            displayWidth,
            displayHeight,
            PixelFormat.RGBA_8888,
            2 // Double buffer
        )
        
        // Create VirtualDisplay
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            displayWidth,
            displayHeight,
            displayDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
        
        // Set up image reader listener
        val minFrameInterval = 1000L / maxFps
        var lastFrameTime = 0L
        
        imageReader?.setOnImageAvailableListener({ reader ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastFrameTime < minFrameInterval) {
                // Skip frame to maintain FPS limit
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val frameData = imageToByteArray(image, quality)
                if (frameData != null) {
                    scope.launch {
                        _frameChannel.send(frameData)
                    }
                }
                lastFrameTime = currentTime
            } finally {
                image.close()
            }
        }, null)
        
        _isCapturing.value = true
        Log.i(TAG, "Screen capture started: ${displayWidth}x${displayHeight} @ ${maxFps}fps")
    }
    
    /**
     * Stops screen capture and releases resources.
     */
    fun stopCapture() {
        _isCapturing.value = false
        
        virtualDisplay?.release()
        virtualDisplay = null
        
        imageReader?.close()
        imageReader = null
        
        mediaProjection?.stop()
        mediaProjection = null
        
        Log.i(TAG, "Screen capture stopped")
    }
    
    /**
     * Converts an Image to a JPEG byte array.
     */
    private fun imageToByteArray(image: Image, quality: Float): ByteArray? {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        
        // Create bitmap
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        // Crop to actual dimensions
        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        bitmap.recycle()
        
        // Compress to JPEG
        val outputStream = java.io.ByteArrayOutputStream()
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, (quality * 100).toInt(), outputStream)
        croppedBitmap.recycle()
        
        return outputStream.toByteArray()
    }
    
    /**
     * Releases all resources.
     */
    fun release() {
        stopCapture()
        scope.cancel()
    }
}
