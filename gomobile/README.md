# Vyx Go Mobile Bridge

This package provides a Go Mobile bridge that allows the Android SDK to use the native Go QUIC implementation from the desktop client.

## Why Go Mobile?

The server uses raw QUIC with `quic-go`, and there's no mature QUIC library for Android that's compatible. By using Go Mobile, we can:
- Reuse the proven desktop client QUIC code
- Maintain protocol compatibility with the server
- Get native TLS and QUIC performance
- Avoid Cronet limitations and complexity

## Architecture

```
Android App
    ↓
VyxNode.kt (SDK API)
    ↓
VyxService.kt (Foreground Service)
    ↓
QuicClient.kt (Kotlin wrapper)
    ↓
vyxclient.aar (Go Mobile AAR) ← This package
    ↓
Vyx Server (QUIC)
```

## Prerequisites

1. **Go 1.25+** installed and in PATH
2. **Android SDK** with NDK installed
3. **gomobile** tool (will be auto-installed by build script)

### Install Go Mobile (if not using build script)

```bash
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
gomobile init
```

## Building

### macOS/Linux

```bash
chmod +x build.sh
./build.sh
```

### Windows

```cmd
build.bat
```

### Manual Build

```bash
# Get dependencies
go mod download

# Build AAR for Android
gomobile bind -v -o vyxclient.aar -target=android .
```

## Output

The build produces:
- **vyxclient.aar** - Android library containing Go code compiled for all Android architectures (arm, arm64, x86, x86_64)
- **vyxclient-sources.jar** - Java source files for IDE integration

## Integration

### Step 1: Copy AAR to Android Project

```bash
cp vyxclient.aar ../android/vyx-sdk/libs/
```

### Step 2: Add Dependency

In `android/vyx-sdk/build.gradle.kts`:

```kotlin
dependencies {
    // Go Mobile QUIC client
    implementation(files("libs/vyxclient.aar"))

    // Other dependencies...
}
```

### Step 3: Use in Kotlin

```kotlin
import gomobile.MessageCallback
import gomobile.Gomobile
import gomobile.VyxClient

class MyCallback : MessageCallback {
    override fun onConnected() {
        // Connected and authenticated
    }

    override fun onDisconnected(reason: String?) {
        // Connection lost
    }

    override fun onMessage(messageType: String?, id: String?, addr: String?, data: String?) {
        // Handle messages from server
    }

    override fun onLog(message: String?) {
        // Log messages
    }
}

// Create client
val callback = MyCallback()
val client = Gomobile.newVyxClient(
    "api.vyx.network:8443",  // server URL
    apiToken,                 // API token
    "android_sdk",            // client type
    metadata,                 // JSON metadata
    callback                  // callback handler
)

// Start connection
client.start()

// Send message
client.sendMessage("pong", "msg-id", "", "")

// Stop
client.stop()
```

## API Reference

### VyxClient

Main QUIC client class.

#### Constructor

```go
NewVyxClient(serverURL, apiToken, clientType, metadata string, callback MessageCallback) *VyxClient
```

- **serverURL**: Server address (e.g., "api.vyx.network:8443")
- **apiToken**: Authentication token from dashboard
- **clientType**: Client identifier (use "android_sdk")
- **metadata**: JSON string with device info
- **callback**: MessageCallback implementation

#### Methods

```go
// Start connection loop with auto-reconnect
Start()

// Stop and disconnect
Stop()

// Send message to server
SendMessage(messageType, id, addr, data string) string

// Check connection status
IsConnected() bool
```

### MessageCallback Interface

Implement this interface in Kotlin to receive events.

```go
type MessageCallback interface {
    OnConnected()
    OnDisconnected(reason string)
    OnMessage(messageType, id, addr, data string)
    OnLog(message string)
}
```

#### Callback Methods

- **OnConnected()**: Called when successfully connected and authenticated
- **OnDisconnected(reason)**: Called when connection is lost
- **OnMessage(type, id, addr, data)**: Called for each message from server
  - **type**: "connect", "data", "close", "ping", "auth_success", "error"
  - **id**: Connection/message ID
  - **addr**: Target address (for "connect" messages)
  - **data**: Base64-encoded data or error message
- **OnLog(message)**: Optional logging

## Message Types

The protocol supports these message types:

### From Server → Client

- **auth_success**: Authentication succeeded
- **error**: Error message
- **connect**: Request to open TCP connection to `addr`
- **data**: Data to forward to TCP connection `id`
- **close**: Close TCP connection `id`
- **ping**: Keepalive ping

### From Client → Server

- **auth**: Send authentication (automatic)
- **connected**: TCP connection established
- **data**: Data from TCP connection
- **close**: TCP connection closed
- **pong**: Response to ping (automatic)

## Protocol Flow

1. **Connection**: Client connects via QUIC with TLS
2. **Authentication**: Client sends `auth` message with API token
3. **Auth Response**: Server responds with `auth_success` or `error`
4. **Proxy Operations**:
   - Server sends `connect` → Client opens TCP to target
   - Client responds with `connected`
   - Bidirectional data relay via `data` messages
   - Either side sends `close` to terminate
5. **Keepalive**: Server sends periodic `ping`, client responds with `pong`

## Troubleshooting

### Build fails with "gomobile: command not found"

Install gomobile tools:
```bash
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest
gomobile init
```

### Build fails with "Android SDK not found"

Set ANDROID_HOME environment variable:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk  # macOS
export ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk  # Windows
```

### Build succeeds but AAR is huge (>50MB)

This is normal. The AAR contains native libraries for multiple architectures (arm, arm64, x86, x86_64). You can reduce size by targeting specific architectures:

```bash
gomobile bind -o vyxclient.aar -target=android/arm64 .
```

### Connection fails in production

Check TLS configuration. For production servers, ensure the server has valid certificates. For localhost/development, the code automatically uses `InsecureSkipVerify`.

## Development Notes

### Go Mobile Limitations

- No channels in exported APIs (use callbacks instead)
- No slices of interfaces
- Limited type support (primitives, strings, interfaces, errors)
- No generics in exported APIs

### TCP Connection Handling

The Go code handles QUIC ↔ Server communication. The Android code must handle TCP connections to target addresses when receiving "connect" messages. See the updated `QuicClient.kt` for reference.

## File Structure

```
sdk/gomobile/
├── go.mod              # Go dependencies
├── vyxclient.go        # Main QUIC client implementation
├── build.sh            # Build script (macOS/Linux)
├── build.bat           # Build script (Windows)
├── README.md           # This file
└── vyxclient.aar       # Output (after build)
```

## Version Compatibility

- Go: 1.25+
- Android SDK: API 24+ (Android 7.0+)
- quic-go: v0.55.0
- Protocol: Compatible with Vyx server v1.0.0

## License

Copyright (c) 2025 Vyx Network. All rights reserved.
