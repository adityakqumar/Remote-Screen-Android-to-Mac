# Remote Assist Backend Server

WebSocket signaling server for the Remote Assist Android app.

## Quick Deploy to Cloud (FREE)

### Option 1: Render.com (Recommended)

1. **Push to GitHub:**
   ```bash
   cd backend
   git init
   git add .
   git commit -m "Initial commit"
   git remote add origin https://github.com/YOUR_USERNAME/remote-assist-backend.git
   git push -u origin main
   ```

2. **Deploy on Render:**
   - Go to [render.com](https://render.com) and sign up (free)
   - Click "New" → "Web Service"
   - Connect your GitHub repo
   - Select the `backend` folder
   - Render will auto-detect `render.yaml` and configure everything
   - Click "Create Web Service"

3. **Copy your URL:**
   Your server will be at: `wss://remote-assist-server.onrender.com/ws`

### Option 2: Railway.app

1. Go to [railway.app](https://railway.app)
2. Click "New Project" → "Deploy from GitHub repo"
3. Select the backend folder
4. Railway will auto-deploy

Your URL will be like: `wss://remote-assist-backend.up.railway.app/ws`

## Update Android App

After deploying, update this line in **both** files:

**`SignalingClient.kt`** (line ~39):
```kotlin
private const val DEFAULT_SERVER_URL = "wss://YOUR-APP-NAME.onrender.com/ws"
```

**`ControlClient.kt`** (line ~36):
```kotlin
private const val DEFAULT_SERVER_URL = "wss://YOUR-APP-NAME.onrender.com/ws"
```

## Local Development

```bash
npm install
npm start
```

Server runs on `http://localhost:8080`

## Endpoints

| Endpoint | Description |
|----------|-------------|
| `/` | Info page |
| `/health` | Health check (returns JSON) |
| `/ws` | WebSocket endpoint |

## How It Works

1. **Target device** connects with a pairing code → joins a "room"
2. **Controller device** connects with same code → joins same room
3. Server relays WebRTC signaling (SDP, ICE candidates) between them
4. Devices establish direct P2P connection for video streaming
