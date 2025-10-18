# Vyx Android SDK

The Vyx Android SDK allows third-party Android apps to participate in the Vyx network by sharing bandwidth in exchange for rewards. The SDK runs as a background service and handles all networking operations transparently.

> **Note:** This is the SDK for third-party app integration. For the official standalone Vyx Android app, please visit our website.

## Features

- 🔐 Native Go QUIC implementation (same as desktop clients)
- 🚀 High-performance TLS-encrypted connections
- 🔋 Battery-optimized background service
- 📱 Works on Android 7.0+ (API 24+)
- 🎨 Customizable notification appearance
- 🔧 Simple integration - just 3 lines of code
- 📊 Same dashboard as desktop clients

## Requirements

- **Android 7.0 (API 24) or higher**
- **Go 1.25+** (for building the Go Mobile library)
- **Android SDK with NDK** (for Go Mobile compilation)
- Internet connectivity
- Notification permission (Android 13+)
- Vyx API token (get from [dashboard](https://vyx.network/dashboard))

## Architecture

This SDK uses **Go Mobile** to compile the native Go QUIC client code into an Android library. This approach:
- ✅ Uses the same proven QUIC code as desktop clients
- ✅ Maintains perfect protocol compatibility with the server
- ✅ Provides native performance without Java/Kotlin overhead
- ✅ Avoids dependency on Cronet or other Android QUIC libraries

```
Android App → Kotlin SDK Wrapper → Go Mobile AAR → Native QUIC (quic-go) → Vyx Server
```

## Installation

### Step 1: Build the Go Mobile Library

First, you need to compile the Go QUIC client into an Android AAR:

```bash
# Navigate to the Go Mobile directory
cd sdk/gomobile

# Run the build script (macOS/Linux)
chmod +x build.sh
./build.sh

# Or on Windows
build.bat
```

This will create `vyxclient.aar` containing the native Go QUIC implementation compiled for all Android architectures.

### Step 2: Copy Go Mobile AAR to SDK

```bash
# Copy the AAR to the SDK libs directory
cp vyxclient.aar ../android/vyx-sdk/libs/
```

### Step 3: Build the Android SDK

Now build the Android SDK AAR that wraps the Go Mobile library:

```bash
cd ../android

# Build the SDK (macOS/Linux)
./gradlew :vyx-sdk:assembleRelease

# Or on Windows
gradlew.bat :vyx-sdk:assembleRelease
```

Output: `vyx-sdk/build/outputs/aar/vyx-sdk-release.aar`

### Step 4: Integrate into Your App

**Option A: AAR File (Recommended)**

```bash
# Copy SDK AAR to your app
cp vyx-sdk/build/outputs/aar/vyx-sdk-release.aar YourApp/app/libs/
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(files("libs/vyx-sdk-release.aar"))

    // Required dependencies
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.jakewharton.timber:timber:5.0.1")
}
```

**Option B: Local Module (Development)**

```gradle
// settings.gradle.kts
include(":vyx-sdk")
project(":vyx-sdk").projectDir = file("path/to/VyxNetwork/sdk/android/vyx-sdk")
```

```gradle
// app/build.gradle.kts
dependencies {
    implementation(project(":vyx-sdk"))
}
```

## Build Requirements

### Go Mobile Setup

If you don't have Go Mobile installed, the build script will install it automatically. Or install manually:

```bash
# Install Go Mobile tools
go install golang.org/x/mobile/cmd/gomobile@latest
go install golang.org/x/mobile/cmd/gobind@latest

# Initialize Go Mobile
gomobile init
```

### Android SDK Setup

Ensure `ANDROID_HOME` environment variable is set:

```bash
# macOS/Linux
export ANDROID_HOME=$HOME/Library/Android/sdk

# Windows
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
```

## Quick Start

### 1. Initialize SDK in Application Class

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = VyxConfig(
            apiToken = "your_api_token_here", // Get from dashboard
            enableDebugLogging = BuildConfig.DEBUG
        )

        VyxNode.initialize(this, config)
    }
}
```

Don't forget to register your Application class in AndroidManifest.xml:

```xml
<application
    android:name=".MyApplication"
    ...>
```

### 2. Start the Node (After User Consent)

```kotlin
// Show consent dialog first
showConsentDialog { userAgreed ->
    if (userAgreed) {
        VyxNode.start()
    }
}
```

### 3. Stop the Node

```kotlin
VyxNode.stop()
```

### 4. Check Status

```kotlin
if (VyxNode.isRunning()) {
    // Node is active
}
```

## Configuration Options

```kotlin
VyxConfig(
    apiToken: String,                      // Required: Your API token
    enableDebugLogging: Boolean = false,   // Optional: Enable logs
    notificationChannelName: String = "Vyx Node Service",
    notificationTitle: String = "Vyx Node Running",
    notificationMessage: String = "Earning rewards by sharing bandwidth",
    notificationIcon: Int = android.R.drawable.ic_dialog_info // Use your app icon
)
```

## User Consent Best Practices

**IMPORTANT**: Always obtain user consent before starting the node. Here's a recommended approach:

```kotlin
fun showConsentDialog(callback: (Boolean) -> Unit) {
    AlertDialog.Builder(this)
        .setTitle("Share Bandwidth")
        .setMessage("""
            This app uses Vyx to share your device's unused bandwidth
            with other users. You will earn rewards for sharing.

            • Uses background data
            • Minimal battery impact
            • You can stop anytime

            Do you agree to share bandwidth?
        """.trimIndent())
        .setPositiveButton("I Agree") { _, _ ->
            // Save consent preference
            saveConsentPreference(true)
            callback(true)
        }
        .setNegativeButton("No Thanks") { _, _ ->
            callback(false)
        }
        .show()
}
```

## Permissions

The SDK automatically includes these permissions:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

For Android 13+, request notification permission at runtime:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
        PERMISSION_CODE
    )
}
```

## Dashboard Integration

All devices using your API token will appear in your [Vyx dashboard](https://vyx.network/dashboard) with:
- Client type: `android_sdk` (identifies as third-party SDK integration)
- App package name (e.g., `com.yourapp.example`)
- Device model and Android version
- Connection status
- Bandwidth shared
- Rewards earned

**Note:** SDK connections are tracked separately from:
- `desktop` - Windows/Mac/Linux desktop clients
- `android_app` - Official Vyx Android app (future release)

## Architecture

The SDK consists of:

1. **VyxNode** - Main API for initialization and control
2. **VyxService** - Foreground service that maintains connection
3. **QuicClient** - QUIC protocol handler for server communication
4. **VyxConfig** - Configuration data class

```
┌─────────────┐
│   Your App  │
└──────┬──────┘
       │
       │ VyxNode.start()
       ▼
┌─────────────┐
│ VyxService  │ (Foreground Service)
└──────┬──────┘
       │
       │ QUIC Protocol
       ▼
┌─────────────┐
│ QuicClient  │ ◄──► Vyx Server
└─────────────┘
```

## Troubleshooting

### Node won't start

- Check internet connectivity
- Verify API token is correct
- Check Android version (must be 7.0+)
- Enable debug logging to see error messages

### Service stops unexpectedly

- Check battery optimization settings
- Verify notification permission is granted
- Review device logs for errors

### No data showing in dashboard

- Ensure API token matches dashboard account
- Check that service is running: `VyxNode.isRunning()`
- Wait a few minutes for initial sync

## Debug Logging

Enable debug logging to see SDK activity:

```kotlin
val config = VyxConfig(
    apiToken = "your_token",
    enableDebugLogging = true
)
```

View logs with:
```bash
adb logcat | grep Vyx
```

## Example App

See the `example-app` module for a complete working example showing:
- SDK initialization
- User consent flow
- Start/stop functionality
- Status monitoring
- Permission handling

## API Reference

### VyxNode

```kotlin
object VyxNode {
    // Initialize SDK (call in Application.onCreate)
    fun initialize(context: Context, config: VyxConfig)

    // Start the node service
    fun start()

    // Stop the node service
    fun stop()

    // Check if service is running
    fun isRunning(): Boolean

    // Get SDK version
    fun getVersion(): String

    // Get current configuration
    fun getConfig(): VyxConfig?
}
```

### VyxConfig

```kotlin
data class VyxConfig(
    val apiToken: String,
    val serverUrl: String = "api.vyx.network:8443",
    val enableDebugLogging: Boolean = false,
    val notificationChannelName: String = "Vyx Node Service",
    val notificationTitle: String = "Vyx Node Running",
    val notificationMessage: String = "Earning rewards by sharing bandwidth",
    val notificationIcon: Int = android.R.drawable.ic_dialog_info
)
```

## Building the SDK

To build the SDK AAR file:

```bash
./gradlew :vyx-sdk:assembleRelease
```

Output: `vyx-sdk/build/outputs/aar/vyx-sdk-release.aar`

## Testing

Run the example app:

```bash
./gradlew :example-app:installDebug
```

## Minimum Code Example

The absolute minimum code needed:

```kotlin
// In Application class
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VyxNode.initialize(this, VyxConfig(apiToken = "YOUR_TOKEN"))
    }
}

// In Activity
VyxNode.start()  // Start earning
VyxNode.stop()   // Stop earning
```

## Support

- 📧 Email: support@vyx.network
- 🌐 Website: https://vyx.network
- 📊 Dashboard: https://vyx.network/dashboard
- 📖 Docs: https://docs.vyx.network

## License

Copyright (c) 2025 Vyx Network. All rights reserved.

## Version History

### 1.0.0 (2025-01-XX)
- Initial release
- QUIC-based connection
- Background service support
- Android 7.0+ compatibility
