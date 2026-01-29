/**
 * Remote Screen Web Controller
 * 
 * Connects to the signaling server and establishes WebRTC connection
 * to view and control the Android device.
 */

class RemoteController {
    constructor() {
        // WebRTC configuration
        this.rtcConfig = {
            iceServers: [
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'stun:stun1.l.google.com:19302' },
                { urls: 'stun:stun2.l.google.com:19302' }
            ]
        };
        
        this.ws = null;
        this.pc = null;
        this.pairingCode = '';
        this.isConnected = false;
        
        // DOM Elements
        this.connectionScreen = document.getElementById('connection-screen');
        this.controllerScreen = document.getElementById('controller-screen');
        this.pairingCodeInput = document.getElementById('pairing-code');
        this.connectBtn = document.getElementById('connect-btn');
        this.statusDiv = document.getElementById('connection-status');
        this.remoteVideo = document.getElementById('remote-video');
        this.videoOverlay = document.getElementById('video-overlay');
        this.gestureCanvas = document.getElementById('gesture-canvas');
        this.sessionCodeSpan = document.getElementById('session-code');
        
        this.initEventListeners();
    }
    
    initEventListeners() {
        // Connect button
        this.connectBtn.addEventListener('click', () => this.connect());
        
        // Enter key on input
        this.pairingCodeInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.connect();
        });
        
        // Auto-uppercase input
        this.pairingCodeInput.addEventListener('input', (e) => {
            e.target.value = e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
        });
        
        // Disconnect button
        document.getElementById('disconnect-btn').addEventListener('click', () => this.disconnect());
        
        // Fullscreen button
        document.getElementById('fullscreen-btn').addEventListener('click', () => this.toggleFullscreen());
        
        // Navigation buttons
        document.getElementById('btn-back').addEventListener('click', () => this.sendGesture('back'));
        document.getElementById('btn-home').addEventListener('click', () => this.sendGesture('home'));
        document.getElementById('btn-recents').addEventListener('click', () => this.sendGesture('recents'));
        
        // Gesture handling on canvas
        this.initGestureHandling();
    }
    
    initGestureHandling() {
        const canvas = this.gestureCanvas;
        let startX, startY, startTime;
        let isDragging = false;
        
        const getRelativeCoords = (e) => {
            const rect = canvas.getBoundingClientRect();
            const videoRect = this.remoteVideo.getBoundingClientRect();
            
            // Get position relative to video
            const x = (e.clientX - videoRect.left) / videoRect.width;
            const y = (e.clientY - videoRect.top) / videoRect.height;
            
            return { x: Math.max(0, Math.min(1, x)), y: Math.max(0, Math.min(1, y)) };
        };
        
        canvas.addEventListener('mousedown', (e) => {
            const coords = getRelativeCoords(e);
            startX = coords.x;
            startY = coords.y;
            startTime = Date.now();
            isDragging = true;
        });
        
        canvas.addEventListener('mouseup', (e) => {
            if (!isDragging) return;
            isDragging = false;
            
            const coords = getRelativeCoords(e);
            const duration = Date.now() - startTime;
            const distance = Math.sqrt(Math.pow(coords.x - startX, 2) + Math.pow(coords.y - startY, 2));
            
            if (distance < 0.02) {
                // Tap or Long Press
                if (duration > 500) {
                    this.sendGesture('longpress', { x: startX, y: startY, duration });
                } else {
                    this.sendGesture('tap', { x: startX, y: startY });
                }
            } else {
                // Swipe
                this.sendGesture('swipe', {
                    startX, startY,
                    endX: coords.x,
                    endY: coords.y,
                    duration: Math.max(100, duration)
                });
            }
        });
        
        canvas.addEventListener('mouseleave', () => {
            isDragging = false;
        });
        
        // Touch support
        canvas.addEventListener('touchstart', (e) => {
            e.preventDefault();
            const touch = e.touches[0];
            const coords = getRelativeCoords(touch);
            startX = coords.x;
            startY = coords.y;
            startTime = Date.now();
            isDragging = true;
        });
        
        canvas.addEventListener('touchend', (e) => {
            e.preventDefault();
            if (!isDragging) return;
            isDragging = false;
            
            const touch = e.changedTouches[0];
            const coords = getRelativeCoords(touch);
            const duration = Date.now() - startTime;
            const distance = Math.sqrt(Math.pow(coords.x - startX, 2) + Math.pow(coords.y - startY, 2));
            
            if (distance < 0.02) {
                if (duration > 500) {
                    this.sendGesture('longpress', { x: startX, y: startY, duration });
                } else {
                    this.sendGesture('tap', { x: startX, y: startY });
                }
            } else {
                this.sendGesture('swipe', {
                    startX, startY,
                    endX: coords.x,
                    endY: coords.y,
                    duration: Math.max(100, duration)
                });
            }
        });
    }
    
    showStatus(message, type = 'info') {
        this.statusDiv.textContent = message;
        this.statusDiv.className = `status ${type}`;
    }
    
    connect() {
        const code = this.pairingCodeInput.value.trim().toUpperCase();
        
        if (code.length !== 6) {
            this.showStatus('Please enter a 6-character code', 'error');
            return;
        }
        
        this.pairingCode = code;
        this.showStatus('Connecting to server...', 'info');
        this.connectBtn.disabled = true;
        
        // Determine WebSocket URL
        const isSecure = window.location.protocol === 'https:';
        const wsProtocol = isSecure ? 'wss:' : 'ws:';
        const wsUrl = `${wsProtocol}//${window.location.host}/ws?code=${code}&type=controller`;
        
        console.log('Connecting to:', wsUrl);
        
        try {
            this.ws = new WebSocket(wsUrl);
            
            this.ws.onopen = () => {
                console.log('WebSocket connected');
                this.showStatus('Connected! Waiting for peer...', 'success');
                
                // Send join message
                this.ws.send(JSON.stringify({
                    type: 'join',
                    room: code,
                    role: 'controller'
                }));
            };
            
            this.ws.onmessage = (event) => {
                this.handleSignalingMessage(JSON.parse(event.data));
            };
            
            this.ws.onerror = (error) => {
                console.error('WebSocket error:', error);
                this.showStatus('Connection error. Please try again.', 'error');
                this.connectBtn.disabled = false;
            };
            
            this.ws.onclose = () => {
                console.log('WebSocket closed');
                if (this.isConnected) {
                    this.disconnect();
                } else {
                    this.showStatus('Connection closed', 'error');
                    this.connectBtn.disabled = false;
                }
            };
        } catch (error) {
            console.error('Failed to connect:', error);
            this.showStatus('Failed to connect: ' + error.message, 'error');
            this.connectBtn.disabled = false;
        }
    }
    
    async handleSignalingMessage(message) {
        console.log('Received:', message.type);
        
        switch (message.type) {
            case 'joined':
                console.log('Joined room:', message.room);
                break;
                
            case 'peer-joined':
                console.log('Peer joined, creating offer...');
                this.showStatus('Peer connected! Establishing video...', 'success');
                await this.createPeerConnection();
                await this.createOffer();
                break;
                
            case 'offer':
                console.log('Received offer');
                await this.createPeerConnection();
                await this.pc.setRemoteDescription(new RTCSessionDescription({
                    type: 'offer',
                    sdp: message.sdp
                }));
                await this.createAnswer();
                break;
                
            case 'answer':
                console.log('Received answer');
                if (this.pc) {
                    await this.pc.setRemoteDescription(new RTCSessionDescription({
                        type: 'answer',
                        sdp: message.sdp
                    }));
                }
                break;
                
            case 'ice-candidate':
                console.log('Received ICE candidate');
                if (this.pc && message.candidate) {
                    try {
                        await this.pc.addIceCandidate(new RTCIceCandidate({
                            candidate: message.candidate.candidate || message.candidate,
                            sdpMid: message.sdpMid || message.candidate.sdpMid || '0',
                            sdpMLineIndex: message.sdpMLineIndex ?? message.candidate.sdpMLineIndex ?? 0
                        }));
                    } catch (e) {
                        console.error('Error adding ICE candidate:', e);
                    }
                }
                break;
                
            case 'peer-left':
                console.log('Peer left');
                this.showStatus('Target device disconnected', 'error');
                break;
        }
    }
    
    async createPeerConnection() {
        if (this.pc) {
            this.pc.close();
        }
        
        this.pc = new RTCPeerConnection(this.rtcConfig);
        
        // Handle incoming tracks
        this.pc.ontrack = (event) => {
            console.log('Received track:', event.track.kind);
            if (event.track.kind === 'video') {
                this.remoteVideo.srcObject = event.streams[0];
                this.videoOverlay.classList.add('hidden');
                this.showControllerScreen();
            }
        };
        
        // Handle ICE candidates
        this.pc.onicecandidate = (event) => {
            if (event.candidate) {
                this.ws.send(JSON.stringify({
                    type: 'ice-candidate',
                    candidate: event.candidate.candidate,
                    sdpMid: event.candidate.sdpMid,
                    sdpMLineIndex: event.candidate.sdpMLineIndex
                }));
            }
        };
        
        // Handle connection state changes
        this.pc.onconnectionstatechange = () => {
            console.log('Connection state:', this.pc.connectionState);
            if (this.pc.connectionState === 'connected') {
                this.isConnected = true;
            } else if (this.pc.connectionState === 'failed' || this.pc.connectionState === 'disconnected') {
                this.showStatus('Connection lost', 'error');
            }
        };
        
        // Add transceiver for receiving video
        this.pc.addTransceiver('video', { direction: 'recvonly' });
    }
    
    async createOffer() {
        try {
            const offer = await this.pc.createOffer();
            await this.pc.setLocalDescription(offer);
            
            this.ws.send(JSON.stringify({
                type: 'offer',
                sdp: offer.sdp
            }));
        } catch (error) {
            console.error('Error creating offer:', error);
        }
    }
    
    async createAnswer() {
        try {
            const answer = await this.pc.createAnswer();
            await this.pc.setLocalDescription(answer);
            
            this.ws.send(JSON.stringify({
                type: 'answer',
                sdp: answer.sdp
            }));
        } catch (error) {
            console.error('Error creating answer:', error);
        }
    }
    
    showControllerScreen() {
        this.connectionScreen.classList.remove('active');
        this.controllerScreen.classList.add('active');
        this.sessionCodeSpan.textContent = `Connected: ${this.pairingCode}`;
        
        // Resize canvas to match video
        this.resizeCanvas();
        window.addEventListener('resize', () => this.resizeCanvas());
    }
    
    resizeCanvas() {
        const container = document.querySelector('.video-container');
        this.gestureCanvas.width = container.clientWidth;
        this.gestureCanvas.height = container.clientHeight;
    }
    
    sendGesture(action, params = {}) {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            console.error('WebSocket not connected');
            return;
        }
        
        const message = {
            type: 'gesture',
            action,
            ...params
        };
        
        console.log('Sending gesture:', message);
        this.ws.send(JSON.stringify(message));
    }
    
    toggleFullscreen() {
        if (!document.fullscreenElement) {
            document.documentElement.requestFullscreen();
        } else {
            document.exitFullscreen();
        }
    }
    
    disconnect() {
        this.isConnected = false;
        
        if (this.pc) {
            this.pc.close();
            this.pc = null;
        }
        
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
        
        // Reset UI
        this.controllerScreen.classList.remove('active');
        this.connectionScreen.classList.add('active');
        this.videoOverlay.classList.remove('hidden');
        this.remoteVideo.srcObject = null;
        this.connectBtn.disabled = false;
        this.pairingCodeInput.value = '';
        this.statusDiv.className = 'status';
    }
}

// Initialize when page loads
document.addEventListener('DOMContentLoaded', () => {
    window.controller = new RemoteController();
});
