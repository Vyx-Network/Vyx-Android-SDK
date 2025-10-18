@echo off
echo ============================================
echo Building Vyx Android SDK
echo ============================================
echo.

REM Step 1: Publish vyxclient.aar to local Maven
echo [1/3] Publishing vyxclient.aar to local Maven...
call gradlew.bat :vyxclient-publisher:publish --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to publish vyxclient.aar
    exit /b 1
)
echo ✓ vyxclient.aar published successfully
echo.

REM Step 2: Build the SDK AAR
echo [2/4] Building vyx-sdk.aar...
call gradlew.bat :vyx-sdk:assembleRelease --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to build vyx-sdk.aar
    exit /b 1
)
echo ✓ vyx-sdk.aar built successfully
echo.

REM Step 3: Create fat AAR (bundles vyxclient.aar inside)
echo [3/4] Creating fat AAR (single file with all dependencies)...
call gradlew.bat :vyx-sdk:createFatAar --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to create fat AAR
    exit /b 1
)
echo ✓ Fat AAR created successfully
echo.

REM Step 4: Show output location
echo [4/4] Build complete!
echo.
echo ============================================
echo Output files:
echo ============================================
echo.
echo ► SINGLE FILE (Recommended for apps):
echo   vyx-sdk\build\outputs\aar\vyx-sdk-fat.aar
echo.
echo ► Individual files (Advanced):
echo   vyx-sdk\build\outputs\aar\vyx-sdk-release.aar
echo   vyx-sdk\build\outputs\aar\vyx-sdk-release-sources.jar
echo   vyx-sdk\build\outputs\aar\vyx-sdk-release-javadoc.jar
echo.
echo ============================================
echo Integration:
echo ============================================
echo.
echo 1. Copy vyx-sdk-fat.aar to your app's libs/ directory
echo.
echo 2. Add to your app's build.gradle:
echo    implementation(files("libs/vyx-sdk-fat.aar"))
echo.
echo 3. Sync Gradle and rebuild
echo.
