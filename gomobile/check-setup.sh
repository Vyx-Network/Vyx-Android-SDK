#!/bin/bash

# Quick setup verification script

echo "===== Vyx Go Mobile Setup Check ====="
echo ""

# Check Go
if command -v go &> /dev/null; then
    GO_VERSION=$(go version | awk '{print $3}')
    echo "✅ Go installed: $GO_VERSION"
else
    echo "❌ Go not installed"
    exit 1
fi

# Check Android SDK
if [ -z "$ANDROID_HOME" ]; then
    ANDROID_HOME="$HOME/Library/Android/sdk"
fi

if [ -d "$ANDROID_HOME" ]; then
    echo "✅ Android SDK found: $ANDROID_HOME"
else
    echo "❌ Android SDK not found at $ANDROID_HOME"
    exit 1
fi

# Check NDK
if [ -d "$ANDROID_HOME/ndk" ]; then
    NDK_VERSION=$(ls "$ANDROID_HOME/ndk" | sort -V | tail -n 1)
    NDK_PATH="$ANDROID_HOME/ndk/$NDK_VERSION"
    echo "✅ NDK found: $NDK_VERSION"
    echo "   Path: $NDK_PATH"

    # Verify NDK is usable
    if [ -f "$NDK_PATH/source.properties" ]; then
        API_LEVEL=$(grep "Pkg.Revision" "$NDK_PATH/source.properties" | cut -d'=' -f2 | cut -d'.' -f1)
        echo "   API Level: $API_LEVEL"
    fi
else
    echo "❌ NDK not found"
    echo "   Install via Android Studio: Tools → SDK Manager → SDK Tools → NDK"
    exit 1
fi

# Check gomobile
if command -v gomobile &> /dev/null; then
    echo "✅ gomobile installed"
else
    echo "⚠️  gomobile not installed (will be installed by build script)"
fi

echo ""
echo "🎉 Setup looks good! Ready to build."
echo ""
echo "Run: ./build.sh"
