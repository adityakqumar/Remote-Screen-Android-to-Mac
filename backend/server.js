/**
 * Remote Assist Backend Server
 * 
 * Provides:
 * 1. Static file serving for web controller
 * 2. WebSocket signaling for WebRTC
 * 3. Gesture command relay
 */

const WebSocket = require('ws');
const http = require('http');
const fs = require('fs');
const path = require('path');

// Single port for cloud deployment
const PORT = process.env.PORT || 8080;

// MIME types for static files
const MIME_TYPES = {
    '.html': 'text/html',
    '.css': 'text/css',
    '.js': 'application/javascript',
    '.json': 'application/json',
    '.png': 'image/png',
    '.jpg': 'image/jpeg',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon'
};

// Create HTTP server
const server = http.createServer((req, res) => {
    // CORS headers
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    
    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }
    
    // Health check endpoint
    if (req.url === '/health') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'ok', timestamp: new Date().toISOString() }));
        return;
    }
    
    // Serve static files from public directory
    let filePath = req.url === '/' ? '/index.html' : req.url;
    filePath = path.join(__dirname, 'public', filePath);
    
    const ext = path.extname(filePath);
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';
    
    fs.readFile(filePath, (err, data) => {
        if (err) {
            if (err.code === 'ENOENT') {
                // File not found - serve index.html for SPA routing
                fs.readFile(path.join(__dirname, 'public', 'index.html'), (err2, data2) => {
                    if (err2) {
                        res.writeHead(404);
                        res.end('Not Found');
                    } else {
                        res.writeHead(200, { 'Content-Type': 'text/html' });
                        res.end(data2);
                    }
                });
            } else {
                res.writeHead(500);
                res.end('Server Error');
            }
        } else {
            res.writeHead(200, { 'Content-Type': contentType });
            res.end(data);
        }
    });
});

// Create WebSocket server
const wss = new WebSocket.Server({ server, path: '/ws' });

// Rooms: Map<roomId, Set<WebSocket>>
const rooms = new Map();
// Client info: Map<WebSocket, { room: string, role: string, type: string }>
const clients = new Map();

console.log(`🚀 Server starting on port ${PORT}`);

wss.on('connection', (ws, req) => {
    const url = new URL(req.url, `http://localhost:${PORT}`);
    const codeFromUrl = url.searchParams.get('code');
    const clientType = url.searchParams.get('type') || 'signaling';
    
    console.log(`📱 New ${clientType} connection${codeFromUrl ? ` with code: ${codeFromUrl}` : ''}`);
    
    ws.on('message', (data) => {
        try {
            const message = JSON.parse(data.toString());
            console.log(`📨 [${clientType}] Received: ${message.type}`);
            handleMessage(ws, message, codeFromUrl, clientType);
        } catch (error) {
            console.error('Invalid message:', error.message);
        }
    });
    
    ws.on('close', () => {
        const clientInfo = clients.get(ws);
        if (clientInfo) {
            const { room } = clientInfo;
            const roomClients = rooms.get(room);
            
            if (roomClients) {
                roomClients.delete(ws);
                
                // Notify others that peer left
                roomClients.forEach(client => {
                    if (client.readyState === WebSocket.OPEN) {
                        client.send(JSON.stringify({ type: 'peer-left' }));
                    }
                });
                
                if (roomClients.size === 0) {
                    rooms.delete(room);
                    console.log(`🗑️ Room ${room} deleted`);
                }
            }
            
            clients.delete(ws);
        }
        console.log(`📱 Connection closed`);
    });
    
    ws.on('error', (error) => {
        console.error('WebSocket error:', error.message);
    });
    
    // Auto-join if code provided in URL
    if (codeFromUrl) {
        joinRoom(ws, codeFromUrl, clientType);
    }
});

function joinRoom(ws, roomId, clientType) {
    if (!rooms.has(roomId)) {
        rooms.set(roomId, new Set());
    }
    
    const roomClients = rooms.get(roomId);
    roomClients.add(ws);
    clients.set(ws, { room: roomId, type: clientType });
    
    console.log(`📱 Client joined room ${roomId}. Room size: ${roomClients.size}`);
    
    // Send acknowledgment
    ws.send(JSON.stringify({ type: 'joined', room: roomId }));
    
    // Notify all peers if there are others
    if (roomClients.size > 1) {
        ws.send(JSON.stringify({ type: 'peer-joined' }));
        
        roomClients.forEach(client => {
            if (client !== ws && client.readyState === WebSocket.OPEN) {
                client.send(JSON.stringify({ type: 'peer-joined' }));
            }
        });
    }
}

function handleMessage(ws, message, codeFromUrl, clientType) {
    const roomId = message.room || message.sessionId || codeFromUrl;
    const { type } = message;
    
    switch (type) {
        case 'join':
            if (roomId) {
                joinRoom(ws, roomId, clientType);
            }
            break;
            
        case 'offer':
        case 'answer':
            console.log(`📤 Relaying ${type}`);
            relayToRoom(ws, { type, sdp: message.sdp });
            break;
            
        case 'ice-candidate':
            console.log(`📤 Relaying ICE candidate`);
            relayToRoom(ws, { 
                type: 'ice-candidate', 
                candidate: message.candidate,
                sdpMid: message.sdpMid,
                sdpMLineIndex: message.sdpMLineIndex
            });
            break;
            
        case 'gesture':
            console.log(`🎮 Relaying gesture: ${message.action}`);
            relayToRoom(ws, message);
            break;
            
        case 'heartbeat':
            ws.send(JSON.stringify({ type: 'heartbeat-ack' }));
            break;
            
        default:
            console.log('Unknown message type:', type);
    }
}

function relayToRoom(sender, message) {
    const clientInfo = clients.get(sender);
    if (!clientInfo) return;
    
    const roomClients = rooms.get(clientInfo.room);
    if (!roomClients) return;
    
    let relayCount = 0;
    roomClients.forEach(client => {
        if (client !== sender && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify(message));
            relayCount++;
        }
    });
    console.log(`📤 Relayed to ${relayCount} peer(s)`);
}

// Start server
server.listen(PORT, () => {
    console.log('\n✅ Remote Assist Backend Ready!');
    console.log('================================');
    console.log(`Web Controller: http://localhost:${PORT}`);
    console.log(`WebSocket:      ws://localhost:${PORT}/ws`);
    console.log('================================');
    console.log('\nWaiting for connections...\n');
});

// Graceful shutdown
process.on('SIGINT', () => {
    console.log('\nShutting down...');
    server.close();
    process.exit(0);
});
