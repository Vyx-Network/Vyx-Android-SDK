@echo off
REM Build script for Go Mobile AAR (Windows)

echo ===== Vyx Go Mobile Build Script =====
echo.

REM Set Android SDK and NDK paths
if "%ANDROID_HOME%"=="" (
    echo ANDROID_HOME not set, using default location...
    set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
)

echo ANDROID_HOME: %ANDROID_HOME%

REM Find the NDK directory
if exist "%ANDROID_HOME%\ndk" (
    REM Get the latest NDK version
    for /f "delims=" %%i in ('dir /b /ad "%ANDROID_HOME%\ndk" ^| sort /r') do (
        set NDK_VERSION=%%i
        goto :found_ndk
    )
    :found_ndk
    set ANDROID_NDK_HOME=%ANDROID_HOME%\ndk\%NDK_VERSION%
    echo Found NDK: %NDK_VERSION%
) else if exist "%ANDROID_HOME%\ndk-bundle" (
    set ANDROID_NDK_HOME=%ANDROID_HOME%\ndk-bundle
    echo Using NDK bundle
) else (
    echo NDK not found!
    echo Please install NDK via Android Studio:
    echo   Tools - SDK Manager - SDK Tools - NDK (Side by side^)
    exit /b 1
)

echo ANDROID_NDK_HOME: %ANDROID_NDK_HOME%
echo.

REM Check if gomobile is installed
where gomobile >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo gomobile not found!
    echo Installing gomobile...
    go install golang.org/x/mobile/cmd/gomobile@latest
    go install golang.org/x/mobile/cmd/gobind@latest

    echo Initializing gomobile...
    gomobile init
)

echo gomobile is installed
echo.

REM Clean previous builds
echo Cleaning previous builds...
if exist vyxclient.aar del vyxclient.aar
if exist vyxclient-sources.jar del vyxclient-sources.jar

REM Get dependencies
echo Getting Go dependencies...
go mod download
go mod tidy

REM Build AAR for Android
echo Building AAR for Android...
echo This may take a few minutes...

gomobile bind -v -o vyxclient.aar -target=android -androidapi 24 .

if exist vyxclient.aar (
    echo.
    echo Build successful!
    echo.
    echo Output: vyxclient.aar
    echo Location: %cd%\vyxclient.aar
    echo.
    echo To use in Android project:
    echo 1. Copy vyxclient.aar to android/vyx-sdk/libs/
    echo 2. Add dependency in build.gradle.kts:
    echo    implementation(files("libs/vyxclient.aar"^)^)
    echo.
) else (
    echo.
    echo Build failed!
    exit /b 1
)
