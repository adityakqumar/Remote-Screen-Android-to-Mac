package com.ad.remotescreen.webrtc

import android.content.Context
import android.util.Log
import com.ad.remotescreen.data.model.DeviceRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebRTC client for real-time screen streaming.
 * 
 * For Target device: Sends screen frames to Controller
 * For Controller device: Receives and displays screen frames
 */
@Singleton
class WebRTCClient @Inject constructor(
    private val context: Context,
    private val signalingClient: SignalingClient
) {
    companion object {
        private const val TAG = "WebRTCClient"
        
        // Google STUN servers for NAT traversal
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null
    
    private var deviceRole: DeviceRole = DeviceRole.CONTROLLER
    
    private val _connectionState = MutableStateFlow(PeerConnectionState.NEW)
    val connectionState: StateFlow<PeerConnectionState> = _connectionState.asStateFlow()
    
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()
    
    // Custom video capturer for screen frames
    private var screenCapturer: ScreenCapturer? = null
    
    enum class PeerConnectionState {
        NEW, CONNECTING, CONNECTED, DISCONNECTED, FAILED, CLOSED
    }
    
    /**
     * Initializes WebRTC and connects to the signaling server.
     * 
     * @param pairingCode The session pairing code
     * @param role The role of this device (Controller or Target)
     */
    suspend fun connect(pairingCode: String, role: DeviceRole) {
        deviceRole = role
        
        initializePeerConnectionFactory()
        createPeerConnection()
        
        // Connect to signaling server
        signalingClient.connect(pairingCode)
        
        // Set up signaling message handlers
        setupSignalingHandlers()
        
        // If target, create offer after connecting
        if (role == DeviceRole.TARGET) {
            createVideoTrack()
            createAndSendOffer()
        }
    }
    
    /**
     * Initializes the PeerConnectionFactory.
     */
    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        
        PeerConnectionFactory.initialize(options)
        
        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext,
            true,
            true
        )
        
        val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        
        Log.i(TAG, "PeerConnectionFactory initialized")
    }
    
    /**
     * Creates the PeerConnection with ICE servers.
     */
    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    Log.d(TAG, "Signaling state: $state")
                }
                
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE connection state: $state")
                    _connectionState.value = when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> PeerConnectionState.CONNECTED
                        PeerConnection.IceConnectionState.CHECKING -> PeerConnectionState.CONNECTING
                        PeerConnection.IceConnectionState.DISCONNECTED -> PeerConnectionState.DISCONNECTED
                        PeerConnection.IceConnectionState.FAILED -> PeerConnectionState.FAILED
                        PeerConnection.IceConnectionState.CLOSED -> PeerConnectionState.CLOSED
                        else -> PeerConnectionState.NEW
                    }
                }
                
                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Log.d(TAG, "ICE receiving: $receiving")
                }
                
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.d(TAG, "ICE gathering state: $state")
                }
                
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        Log.d(TAG, "New ICE candidate: ${it.sdp}")
                        scope.launch {
                            signalingClient.sendIceCandidate(it)
                        }
                    }
                }
                
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                    Log.d(TAG, "ICE candidates removed")
                }
                
                override fun onAddStream(stream: MediaStream?) {
                    Log.d(TAG, "Remote stream added")
                    stream?.videoTracks?.firstOrNull()?.let {
                        _remoteVideoTrack.value = it
                    }
                }
                
                override fun onRemoveStream(stream: MediaStream?) {
                    Log.d(TAG, "Remote stream removed")
                    _remoteVideoTrack.value = null
                }
                
                override fun onDataChannel(channel: DataChannel?) {
                    Log.d(TAG, "Data channel: ${channel?.label()}")
                }
                
                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "Renegotiation needed")
                }
                
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    Log.d(TAG, "Track added")
                    receiver?.track()?.let { track ->
                        if (track is VideoTrack) {
                            _remoteVideoTrack.value = track
                        }
                    }
                }
            }
        )
        
        Log.i(TAG, "PeerConnection created")
    }
    
    /**
     * Creates a video track from screen capture frames.
     */
    private fun createVideoTrack() {
        videoSource = peerConnectionFactory?.createVideoSource(false)
        localVideoTrack = peerConnectionFactory?.createVideoTrack("screen-video", videoSource)
        
        // Create screen capturer
        screenCapturer = ScreenCapturer(videoSource!!)
        
        // Add track to peer connection
        peerConnection?.addTrack(localVideoTrack, listOf("screen-stream"))
        
        Log.i(TAG, "Video track created")
    }
    
    /**
     * Sets up handlers for signaling messages.
     */
    private fun setupSignalingHandlers() {
        scope.launch {
            signalingClient.offerFlow.collect { sdp ->
                handleRemoteOffer(sdp)
            }
        }
        
        scope.launch {
            signalingClient.answerFlow.collect { sdp ->
                handleRemoteAnswer(sdp)
            }
        }
        
        scope.launch {
            signalingClient.iceCandidateFlow.collect { candidate ->
                handleRemoteIceCandidate(candidate)
            }
        }
    }
    
    /**
     * Creates and sends an SDP offer (Target device only).
     */
    private fun createAndSendOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }
        
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            scope.launch {
                                signalingClient.sendOffer(it)
                            }
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "Failed to set local description: $error")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set local description: $error")
                        }
                    }, it)
                }
            }
            
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create offer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
    
    /**
     * Handles a remote SDP offer (Controller device only).
     */
    private fun handleRemoteOffer(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                createAndSendAnswer()
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to set remote offer: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote offer: $error")
            }
        }, sdp)
    }
    
    /**
     * Creates and sends an SDP answer (Controller device only).
     */
    private fun createAndSendAnswer() {
        val constraints = MediaConstraints()
        
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            scope.launch {
                                signalingClient.sendAnswer(it)
                            }
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "Failed to set local description: $error")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set local description: $error")
                        }
                    }, it)
                }
            }
            
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create answer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }
    
    /**
     * Handles a remote SDP answer (Target device only).
     */
    private fun handleRemoteAnswer(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.i(TAG, "Remote answer set successfully")
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to set remote answer: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote answer: $error")
            }
        }, sdp)
    }
    
    /**
     * Handles a remote ICE candidate.
     */
    private fun handleRemoteIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }
    
    /**
     * Sends a frame to the video track (Target device only).
     */
    fun sendFrame(frameData: ByteArray) {
        screenCapturer?.processFrame(frameData)
    }
    
    /**
     * Disconnects and releases all resources.
     */
    fun disconnect() {
        screenCapturer?.dispose()
        screenCapturer = null
        
        localVideoTrack?.dispose()
        localVideoTrack = null
        
        videoSource?.dispose()
        videoSource = null
        
        peerConnection?.close()
        peerConnection = null
        
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        
        signalingClient.disconnect()
        
        _connectionState.value = PeerConnectionState.CLOSED
        _remoteVideoTrack.value = null
        
        scope.cancel()
        
        Log.i(TAG, "Disconnected and released resources")
    }
}
