/**
 * Remote Assist Backend Server
 * 
 * Provides:
 * 1. Signaling Server - For WebRTC SDP/ICE exchange
 * 2. Control Server - For gesture command relay
 * 
 * Both run on a single port for cloud deployment compatibility.
 */

const WebSocket = require('ws');
const http = require('http');

// Single port for cloud deployment (Render, Railway, etc.)
const PORT = process.env.PORT || 8080;

// Create HTTP server
const server = http.createServer((req, res) => {
    // Health check endpoint
    if (req.url === '/health') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'ok', timestamp: new Date().toISOString() }));
        return;
    }
    
    // Info endpoint
    if (req.url === '/') {
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(`
            <html>
                <head><title>Remote Assist Server</title></head>
                <body style="font-family: Arial; padding: 20px;">
                    <h1>🔗 Remote Assist Signaling Server</h1>
                    <p>WebSocket endpoint: <code>wss://${req.headers.host}/ws</code></p>
                    <p>Status: ✅ Running</p>
                </body>
            </html>
        `);
        return;
    }
    
    res.writeHead(404);
    res.end('Not Found');
});

// Create WebSocket server attached to HTTP server
const wss = new WebSocket.Server({ server, path: '/ws' });

// Rooms: Map<roomId, Set<WebSocket>>
const rooms = new Map();
// Client info: Map<WebSocket, { room: string, role: string, type: string }>
const clients = new Map();

console.log(`🚀 Server starting on port ${PORT}`);

wss.on('connection', (ws, req) => {
    const url = new URL(req.url, `http://localhost:${PORT}`);
    const codeFromUrl = url.searchParams.get('code');
    const clientType = url.searchParams.get('type') || 'signaling'; // 'signaling' or 'control'
    
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
                
                // Clean up empty rooms
                if (roomClients.size === 0) {
                    rooms.delete(room);
                    console.log(`🗑️ Room ${room} deleted (empty)`);
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
    
    // Notify if there's already a peer
    if (roomClients.size > 1) {
        ws.send(JSON.stringify({ type: 'peer-joined' }));
        
        // Notify existing peers
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
            } else {
                console.error('No room/sessionId provided');
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
                sdpMid: message.sdpMid || (message.candidate && message.candidate.sdpMid),
                sdpMLineIndex: message.sdpMLineIndex || (message.candidate && message.candidate.sdpMLineIndex)
            });
            break;
            
        case 'gesture':
        case 'command':
            console.log(`🎮 Relaying gesture command`);
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
    if (!clientInfo) {
        console.error('Sender not in any room');
        return;
    }
    
    const roomClients = rooms.get(clientInfo.room);
    if (!roomClients) {
        console.error('Room not found');
        return;
    }
    
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
    console.log(`HTTP:      http://localhost:${PORT}`);
    console.log(`WebSocket: ws://localhost:${PORT}/ws`);
    console.log('================================');
    console.log('\nWaiting for connections...\n');
});

// Graceful shutdown
process.on('SIGINT', () => {
    console.log('\nShutting down server...');
    server.close();
    process.exit(0);
});
