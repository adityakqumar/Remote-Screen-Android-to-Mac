package com.ad.remotescreen.webrtc

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.webrtc.*
import java.nio.ByteBuffer

/**
 * Custom VideoCapturer that processes screen capture frames
 * and feeds them into the WebRTC video pipeline.
 * 
 * Converts JPEG frames to I420 format for WebRTC.
 */
class ScreenCapturer(
    private val videoSource: VideoSource
) {
    companion object {
        private const val TAG = "ScreenCapturer"
    }
    
    private var capturerObserver: CapturerObserver? = null
    private var isDisposed = false
    private var frameCount = 0
    
    init {
        // Create a capturer observer from the video source
        capturerObserver = videoSource.capturerObserver
        Log.i(TAG, "ScreenCapturer initialized")
    }
    
    /**
     * Processes a JPEG frame and sends it to WebRTC.
     * 
     * @param jpegData The JPEG encoded frame data
     */
    fun processFrame(jpegData: ByteArray) {
        if (isDisposed) return
        
        try {
            frameCount++
            if (frameCount % 30 == 1) {
                Log.d(TAG, "Processing frame #$frameCount, size: ${jpegData.size} bytes")
            }
            
            // Decode JPEG to Bitmap
            val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
            if (bitmap == null) {
                Log.w(TAG, "Failed to decode JPEG frame")
                return
            }
            
            val width = bitmap.width
            val height = bitmap.height
            
            if (frameCount % 30 == 1) {
                Log.d(TAG, "Frame dimensions: ${width}x${height}")
            }
            
            // Convert Bitmap to I420 format
            val i420Buffer = bitmapToI420Buffer(bitmap, width, height)
            bitmap.recycle()
            
            if (i420Buffer == null) {
                Log.w(TAG, "Failed to convert bitmap to I420")
                return
            }
            
            val videoFrame = VideoFrame(
                i420Buffer,
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
     * Converts a Bitmap to I420 VideoFrame.Buffer
     */
    private fun bitmapToI420Buffer(bitmap: Bitmap, width: Int, height: Int): VideoFrame.Buffer? {
        return try {
            // Get ARGB pixels from bitmap
            val argbData = IntArray(width * height)
            bitmap.getPixels(argbData, 0, width, 0, 0, width, height)
            
            // Calculate buffer sizes for I420 format
            // Y plane: width * height bytes
            // U plane: (width/2) * (height/2) bytes  
            // V plane: (width/2) * (height/2) bytes
            val ySize = width * height
            val uvSize = (width / 2) * (height / 2)
            
            val yPlane = ByteBuffer.allocateDirect(ySize)
            val uPlane = ByteBuffer.allocateDirect(uvSize)
            val vPlane = ByteBuffer.allocateDirect(uvSize)
            
            // Convert ARGB to I420 (YUV)
            convertArgbToI420(argbData, width, height, yPlane, uPlane, vPlane)
            
            // Reset buffer positions
            yPlane.position(0)
            uPlane.position(0)
            vPlane.position(0)
            
            JavaI420Buffer.wrap(
                width, height,
                yPlane, width,         // Y plane and stride
                uPlane, width / 2,     // U plane and stride
                vPlane, width / 2,     // V plane and stride
                null                    // No release callback
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error converting bitmap to I420", e)
            null
        }
    }
    
    /**
     * Converts ARGB pixel data to I420 YUV planes.
     */
    private fun convertArgbToI420(
        argb: IntArray,
        width: Int,
        height: Int,
        yPlane: ByteBuffer,
        uPlane: ByteBuffer,
        vPlane: ByteBuffer
    ) {
        var yIndex = 0
        var uvIndex = 0
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = argb[y * width + x]
                
                // Extract RGB components (Android ARGB format)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                // Convert RGB to YUV using standard BT.601 formula
                val yValue = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                
                // Clamp Y value to valid range [0, 255]
                yPlane.put(yIndex++, yValue.coerceIn(0, 255).toByte())
                
                // Only compute U and V for every 2x2 block (subsampling)
                if (y % 2 == 0 && x % 2 == 0) {
                    val uValue = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val vValue = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    
                    uPlane.put(uvIndex, uValue.coerceIn(0, 255).toByte())
                    vPlane.put(uvIndex, vValue.coerceIn(0, 255).toByte())
                    uvIndex++
                }
            }
        }
    }
    
    /**
     * Disposes of resources.
     */
    fun dispose() {
        isDisposed = true
        capturerObserver = null
        Log.i(TAG, "ScreenCapturer disposed after $frameCount frames")
    }
}
