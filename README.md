# Remote Assist - Android Remote Assistance Application

A **secure, legal Android remote assistance application** that enables one Android device to view and control another with explicit user consent. Built with modern Android architecture, WebRTC for streaming, and full Google Play policy compliance.

## 🌟 Features

### Core Functionality
- **Real-time Screen Streaming** - WebRTC-based screen sharing with low latency
- **Remote Control** - Tap, swipe, long press, scroll, and text input from controller device
- **Secure Pairing** - 6-character one-time codes with QR code support
- **Emergency Stop** - Prominent button to immediately end remote sessions
- **Activity Logging** - Full transparency of all remote actions

### Security & Privacy
- **Explicit User Consent** - All permissions require manual approval
- **Accessibility Service** - Only active during approved sessions
- **Persistent Notification** - Users always know when being controlled
- **Encrypted Connections** - TLS/DTLS for all communications
- **Session Tokens** - Cryptographically secure session management

### Google Play Compliance
- Clear accessibility service disclosure
- Transparent permission explanations
- User-controlled session management
- Complete activity logging for transparency

## 📱 Screenshots

*Coming soon*

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │ Onboarding  │  │ Role Select  │  │ Controller/Target │  │
│  │   Screen    │  │    Screen    │  │     Screens       │  │
│  └─────────────┘  └──────────────┘  └───────────────────┘  │
│                    Jetpack Compose + Navigation              │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                     ViewModel Layer                          │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │ Onboarding  │  │   Pairing    │  │ Controller/Target │  │
│  │  ViewModel  │  │   ViewModel  │  │    ViewModels     │  │
│  └─────────────┘  └──────────────┘  └───────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                    Service Layer                             │
│  ┌─────────────────────────────────────────────────────────┐│
│  │            RemoteAssistanceService (Foreground)         ││
│  │  ┌──────────────┐  ┌────────────┐  ┌───────────────┐  ││
│  │  │ WebRTCClient │  │ControlClient│  │ScreenCapture  │  ││
│  │  │  (Signaling) │  │ (Gestures) │  │   Manager     │  ││
│  │  └──────────────┘  └────────────┘  └───────────────┘  ││
│  └─────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────┐│
│  │          RemoteAccessibilityService                     ││
│  │            (Gesture Dispatch)                           ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                      Data Layer                              │
│  ┌──────────────────┐  ┌────────────────────────────────┐  │
│  │ SessionRepository│  │ Models (Session, GestureCommand)│  │
│  └──────────────────┘  └────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 🛠️ Tech Stack

| Component | Technology |
|-----------|------------|
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| DI | Hilt |
| Networking | OkHttp + WebSocket |
| Streaming | WebRTC (via stream-webrtc) |
| Screen Capture | MediaProjection API |
| Remote Control | Accessibility Service |
| Async | Kotlin Coroutines + Flow |

## 📦 Project Structure

```
app/src/main/java/com/ad/remotescreen/
├── RemoteScreenApp.kt          # Hilt Application
├── MainActivity.kt             # Main entry point
├── capture/
│   └── ScreenCaptureManager.kt # MediaProjection handler
├── control/
│   └── ControlClient.kt        # WebSocket gesture client
├── data/
│   ├── model/
│   │   ├── ActivityLogEntry.kt
│   │   ├── DeviceRole.kt
│   │   ├── GestureCommand.kt
│   │   └── Session.kt
│   ├── repository/
│   │   └── SessionRepository.kt
│   └── PairingCodeGenerator.kt
├── di/
│   ├── AppModule.kt
│   └── ServiceModule.kt
├── service/
│   ├── NotificationHelper.kt
│   ├── RemoteAccessibilityService.kt
│   ├── RemoteAssistanceService.kt
│   └── StopSessionReceiver.kt
├── ui/
│   ├── navigation/
│   │   └── AppNavigation.kt
│   ├── screen/
│   │   ├── ControllerScreen.kt
│   │   ├── OnboardingScreen.kt
│   │   ├── PairingScreen.kt
│   │   ├── RoleSelectionScreen.kt
│   │   ├── SettingsScreen.kt
│   │   └── TargetScreen.kt
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   └── viewmodel/
│       ├── ControllerViewModel.kt
│       ├── OnboardingViewModel.kt
│       ├── PairingViewModel.kt
│       └── TargetViewModel.kt
└── webrtc/
    ├── ScreenCapturer.kt
    ├── SignalingClient.kt
    └── WebRTCClient.kt
```

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17+
- Android SDK 24+ (target 35)
- Backend signaling server (see below)

### Setup

1. **Clone the repository**
```bash
git clone https://github.com/your-repo/remote-assist.git
cd remote-assist
```

2. **Configure the backend server URL**
   
   Update the server URLs in:
   - `SignalingClient.kt` - `DEFAULT_SERVER_URL`
   - `ControlClient.kt` - `DEFAULT_CONTROL_URL`

3. **Set up Firebase (Optional)**
   - Create a Firebase project
   - Add `google-services.json` to `app/`
   - Enable Authentication

4. **Build and run**
```bash
./gradlew assembleDebug
```

### Backend Server

The app requires a backend for:
- **WebRTC Signaling** - Relay SDP offers/answers and ICE candidates
- **Control Channel** - Relay gesture commands between devices

A simple signaling server implementation:

```javascript
// Node.js WebSocket Signaling Server
const WebSocket = require('ws');
const wss = new WebSocket.Server({ port: 8080 });

const rooms = new Map();

wss.on('connection', (ws) => {
  ws.on('message', (data) => {
    const msg = JSON.parse(data);
    
    if (msg.type === 'join') {
      if (!rooms.has(msg.room)) rooms.set(msg.room, new Set());
      rooms.get(msg.room).add(ws);
      ws.room = msg.room;
    } else {
      // Relay to room members
      rooms.get(ws.room)?.forEach(client => {
        if (client !== ws) client.send(data);
      });
    }
  });
});
```

## 📋 Permissions Required

| Permission | Purpose |
|------------|---------|
| `INTERNET` | WebRTC/WebSocket communication |
| `FOREGROUND_SERVICE` | Keep session alive |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Screen capture |
| `POST_NOTIFICATIONS` | Session status alerts |
| `ACCESSIBILITY_SERVICE` | Remote gesture execution (user-enabled) |
| `RECORD_AUDIO` | Optional voice communication |

## ⚠️ Google Play Compliance

This app is designed for **legitimate remote assistance** use cases:

1. **Accessibility Service Declaration**
   - Clear description in `strings.xml`
   - Only enabled by user action
   - Settings link provided

2. **User Transparency**
   - Onboarding explains all permissions
   - Persistent notification during control
   - Activity log shows all actions

3. **Emergency Controls**
   - Prominent STOP button
   - Notification action to end session
   - Automatic session timeout

## 🔒 Security Features

- **Secure Pairing Codes** - SecureRandom-generated, unambiguous characters
- **Session Tokens** - UUID-based with expiration
- **TLS/DTLS** - All connections encrypted
- **Session-based Control** - Gestures only execute during active sessions
- **Activity Logging** - Complete audit trail

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## ⚠️ Disclaimer

This application is intended for **legitimate remote assistance purposes only**. Users are responsible for:
- Obtaining consent before accessing any device
- Complying with all applicable laws and regulations
- Using the software ethically and responsibly

Misuse for unauthorized access, surveillance, or any illegal activity is strictly prohibited.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📧 Support

For issues and questions, please open a GitHub issue or contact [your-email@example.com].
