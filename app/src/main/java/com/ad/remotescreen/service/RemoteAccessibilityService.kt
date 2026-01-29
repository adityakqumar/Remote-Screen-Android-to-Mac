package com.ad.remotescreen.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ad.remotescreen.data.model.GestureCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Custom AccessibilityService for executing remote gestures.
 * 
 * This service ONLY works when an active session is enabled.
 * It automatically disables gesture execution when the session ends.
 * 
 * Security considerations:
 * - Gestures are only executed during active sessions
 * - User must explicitly enable the service via Settings
 * - Persistent notification informs user when service is active
 * - Emergency stop immediately disables all gesture execution
 */
class RemoteAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "RemoteAccessibility"
        
        // Static reference for service communication
        private var instance: RemoteAccessibilityService? = null
        
        private val _isServiceEnabled = MutableStateFlow(false)
        val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()
        
        private val _isSessionActive = MutableStateFlow(false)
        val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()
        
        /**
         * Enables gesture execution for the current session.
         * Call this when a remote session is established.
         */
        fun enableSession() {
            _isSessionActive.value = true
            Log.i(TAG, "Session enabled - gesture execution allowed")
        }
        
        /**
         * Disables gesture execution.
         * Call this when the session ends or emergency stop is triggered.
         */
        fun disableSession() {
            _isSessionActive.value = false
            Log.i(TAG, "Session disabled - gesture execution blocked")
        }
        
        /**
         * Executes a gesture command if session is active.
         * 
         * @param command The gesture command to execute
         * @return true if the gesture was dispatched, false if session is inactive
         */
        fun executeGesture(command: GestureCommand): Boolean {
            if (!_isSessionActive.value) {
                Log.w(TAG, "Gesture rejected - no active session")
                return false
            }
            
            return instance?.dispatchGestureCommand(command) ?: run {
                Log.e(TAG, "Cannot execute gesture - service not running")
                false
            }
        }
        
        /**
         * Performs a global action (back, home, recents).
         * 
         * @param action The global action to perform
         * @return true if the action was performed
         */
        fun performGlobalAction(action: Int): Boolean {
            if (!_isSessionActive.value) {
                Log.w(TAG, "Global action rejected - no active session")
                return false
            }
            
            return instance?.performGlobalAction(action) ?: run {
                Log.e(TAG, "Cannot perform action - service not running")
                false
            }
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        _isServiceEnabled.value = true
        Log.i(TAG, "Accessibility Service created")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceEnabled.value = false
        _isSessionActive.value = false
        serviceScope.cancel()
        Log.i(TAG, "Accessibility Service destroyed")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility Service connected")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle accessibility events for this use case
        // This service is purely for gesture dispatch
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
    }
    
    /**
     * Dispatches a gesture command based on its type.
     */
    private fun dispatchGestureCommand(command: GestureCommand): Boolean {
        return when (command) {
            is GestureCommand.Tap -> performTap(command.x, command.y)
            is GestureCommand.Swipe -> performSwipe(
                command.startX, command.startY,
                command.endX, command.endY,
                command.duration
            )
            is GestureCommand.LongPress -> performLongPress(command.x, command.y, command.duration)
            is GestureCommand.Scroll -> performScroll(
                command.startX, command.startY,
                command.deltaX, command.deltaY,
                command.duration
            )
            is GestureCommand.TextInput -> performTextInput(command.text)
            is GestureCommand.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
            is GestureCommand.Home -> performGlobalAction(GLOBAL_ACTION_HOME)
            is GestureCommand.Recents -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }
    
    /**
     * Performs a tap gesture at the specified coordinates.
     */
    private fun performTap(x: Float, y: Float): Boolean {
        Log.d(TAG, "Performing tap at ($x, $y)")
        
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap completed")
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap cancelled")
            }
        }, null)
    }
    
    /**
     * Performs a swipe gesture from start to end coordinates.
     */
    private fun performSwipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long
    ): Boolean {
        Log.d(TAG, "Performing swipe from ($startX, $startY) to ($endX, $endY)")
        
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe completed")
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Swipe cancelled")
            }
        }, null)
    }
    
    /**
     * Performs a long press gesture at the specified coordinates.
     */
    private fun performLongPress(x: Float, y: Float, duration: Long): Boolean {
        Log.d(TAG, "Performing long press at ($x, $y) for ${duration}ms")
        
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Long press completed")
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Long press cancelled")
            }
        }, null)
    }
    
    /**
     * Performs a scroll gesture.
     */
    private fun performScroll(
        startX: Float, startY: Float,
        deltaX: Float, deltaY: Float,
        duration: Long
    ): Boolean {
        Log.d(TAG, "Performing scroll from ($startX, $startY) by ($deltaX, $deltaY)")
        
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX + deltaX, startY + deltaY)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Scroll completed")
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Scroll cancelled")
            }
        }, null)
    }
    
    /**
     * Performs text input by finding the focused text field and inserting text.
     */
    private fun performTextInput(text: String): Boolean {
        Log.d(TAG, "Performing text input: $text")
        
        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "Cannot get root node for text input")
            return false
        }
        
        // Find the focused input field
        val focusedNode = findFocusedEditText(rootNode)
        if (focusedNode == null) {
            Log.w(TAG, "No focused text field found")
            rootNode.recycle()
            return false
        }
        
        // Set the text
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        
        focusedNode.recycle()
        rootNode.recycle()
        
        Log.d(TAG, "Text input result: $result")
        return result
    }
    
    /**
     * Recursively finds the focused editable text field.
     */
    private fun findFocusedEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        
        // Check if this node is an editable text field
        if (node.isFocused && node.isEditable) {
            return AccessibilityNodeInfo.obtain(node)
        }
        
        // Search children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditText(child)
            child.recycle()
            if (result != null) return result
        }
        
        return null
    }
}
