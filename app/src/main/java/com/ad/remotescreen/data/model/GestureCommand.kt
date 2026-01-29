package com.ad.remotescreen.data.model

import com.google.gson.annotations.SerializedName

/**
 * Sealed class hierarchy for gesture commands sent from Controller to Target.
 * Uses JSON serialization for WebSocket transmission.
 */
sealed class GestureCommand {
    abstract val type: String
    abstract val timestamp: Long
    
    /**
     * Tap gesture at specified coordinates.
     */
    data class Tap(
        @SerializedName("x") val x: Float,
        @SerializedName("y") val y: Float,
        @SerializedName("timestamp") override val timestamp: Long = System.currentTimeMillis()
    ) : GestureCommand() {
        @SerializedName("type")
        override val type: String = "tap"
    }
    
    /**
     * Swipe gesture from start to end coordinates.
     */
    data class Swipe(
        @SerializedName("startX") val startX: Float,
        @SerializedName("startY") val startY: Float,
        @SerializedName("endX") val endX: Float,
        @SerializedName("endY") val endY: Float,
        @SerializedName("duration") val duration: Long = 300,
        @SerializedName("timestamp") override val timestamp: Long = System.currentTimeMillis()
    ) : GestureCommand() {
        @SerializedName("type")
        override val type: String = "swipe"
    }
    
    /**
     * Long press gesture at specified coordinates.
     */
    data class LongPress(
        @SerializedName("x") val x: Float,
        @SerializedName("y") val y: Float,
        @SerializedName("duration") val duration: Long = 1000,
        @SerializedName("timestamp") override val timestamp: Long = System.currentTimeMillis()
    ) : GestureCommand() {
        @SerializedName("type")
        override val type: String = "longpress"
    }
    
    /**
     * Scroll gesture (multiple points for smooth scrolling).
     */
    data class Scroll(
        @SerializedName("startX") val startX: Float,
        @SerializedName("startY") val startY: Float,
        @SerializedName("deltaX") val deltaX: Float,
        @SerializedName("deltaY") val deltaY: Float,
        @SerializedName("duration") val duration: Long = 500,
        @SerializedName("timestamp") override val timestamp: Long = System.currentTimeMillis()
    ) : GestureCommand() {
        @SerializedName("type")
        override val type: String = "scroll"
    }
    
    /**
     * Text input command.
     */
    data class TextInput(
        @SerializedName("text") val text: String,
        @SerializedName("timestamp") override val timestamp: Long = System.currentTimeMillis()
    ) : GestureCommand() {
        @SerializedName("type")
        override val type: String = "text"
    }
    
    /**
     * Back button press.
     */
    data class Back(
        @SerializedName("timestamp") override val timestamp: Long = System.currentTimeMillis()
    ) : GestureCommand() {
        @SerializedName("type")
        override val type: String = "back"
    }
    
    /**
     * Home button press.
     */
    data class Home(
        @SerializedName("timestamp") override val timestamp: Long = System.currentTimeMillis()
    ) : GestureCommand() {
        @SerializedName("type")
        override val type: String = "home"
    }
    
    /**
     * Recent apps button press.
     */
    data class Recents(
        @SerializedName("timestamp") override val timestamp: Long = System.currentTimeMillis()
    ) : GestureCommand() {
        @SerializedName("type")
        override val type: String = "recents"
    }
}

/**
 * Wrapper for command protocol messages.
 */
data class CommandMessage(
    @SerializedName("type") val type: String,
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("command") val command: GestureCommand? = null,
    @SerializedName("payload") val payload: Map<String, Any>? = null
)
