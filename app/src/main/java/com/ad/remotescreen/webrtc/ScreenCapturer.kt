package com.ad.remotescreen.webrtc

import android.util.Log
import org.webrtc.*
import java.nio.ByteBuffer

/**
 * Custom VideoCapturer that processes screen capture frames
 * and feeds them into the WebRTC video pipeline.
 */
class ScreenCapturer(
    private val videoSource: VideoSource
) {
    companion object {
        private const val TAG = "ScreenCapturer"
    }
    
    private var capturerObserver: CapturerObserver? = null
    private var isDisposed = false
    
    init {
        // Create a capturer observer from the video source
        capturerObserver = videoSource.capturerObserver
    }
    
    /**
     * Processes a JPEG frame and sends it to WebRTC.
     * 
     * @param jpegData The JPEG encoded frame data
     */
    fun processFrame(jpegData: ByteArray) {
        if (isDisposed) return
        
        try {
            // Decode JPEG to NV21 (or I420) format
            // In a production app, you'd use a proper decoder
            // For now, we'll send raw frame data
            
            val buffer = NV21Buffer(
                jpegData,
                1920, // width - should be dynamic
                1080, // height - should be dynamic
                null
            )
            
            val videoFrame = VideoFrame(
                buffer,
                0, // rotation
                System.nanoTime()
            )
            
            capturerObserver?.onFrameCaptured(videoFrame)
            videoFrame.release()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        }
    }
    
    /**
     * Disposes of resources.
     */
    fun dispose() {
        isDisposed = true
        capturerObserver = null
    }
}
