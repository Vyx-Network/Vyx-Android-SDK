#!/bin/bash

# Build script for Go Mobile AAR
# This script compiles the Go code into an Android AAR library

set -e

echo "===== Vyx Go Mobile Build Script ====="
echo ""

# Set Android SDK and NDK paths
if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  ANDROID_HOME not set, using default location..."
    export ANDROID_HOME="$HOME/Library/Android/sdk"
fi

echo "📱 ANDROID_HOME: $ANDROID_HOME"

# Find the NDK directory
if [ -d "$ANDROID_HOME/ndk" ]; then
    # Get the latest NDK version (should be 29.x or higher)
    NDK_VERSION=$(ls "$ANDROID_HOME/ndk" | sort -V | tail -n 1)
    export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"
    echo "📦 Found NDK: $NDK_VERSION"
elif [ -d "$ANDROID_HOME/ndk-bundle" ]; then
    export ANDROID_NDK_HOME="$ANDROID_HOME/ndk-bundle"
    echo "📦 Using NDK bundle"
else
    echo "❌ NDK not found!"
    echo "Please install NDK via Android Studio:"
    echo "  Tools → SDK Manager → SDK Tools → NDK (Side by side)"
    exit 1
fi

echo "🔧 ANDROID_NDK_HOME: $ANDROID_NDK_HOME"
echo ""

# Check if gomobile is installed
if ! command -v gomobile &> /dev/null; then
    echo "❌ gomobile not found!"
    echo "Installing gomobile..."
    go install golang.org/x/mobile/cmd/gomobile@latest
    go install golang.org/x/mobile/cmd/gobind@latest

    echo "Initializing gomobile..."
    gomobile init
fi

echo "✓ gomobile is installed"
echo ""

# Clean previous builds
echo "🧹 Cleaning previous builds..."
rm -rf vyxclient.aar
rm -rf vyxclient-sources.jar

# Get dependencies
echo "📦 Getting Go dependencies..."
go mod download
go mod tidy

# Build AAR for Android
echo "🔨 Building AAR for Android..."
echo "   This may take a few minutes..."

gomobile bind -v -o vyxclient.aar -target=android -androidapi 24 .

if [ -f "vyxclient.aar" ]; then
    echo ""
    echo "✅ Build successful!"
    echo ""
    echo "📦 Output: vyxclient.aar"
    echo "📍 Location: $(pwd)/vyxclient.aar"
    echo ""
    echo "To use in Android project:"
    echo "1. Copy vyxclient.aar to android/vyx-sdk/libs/"
    echo "2. Add dependency in build.gradle.kts:"
    echo "   implementation(files(\"libs/vyxclient.aar\"))"
    echo ""
else
    echo ""
    echo "❌ Build failed!"
    exit 1
fi
