@echo off
echo ============================================
echo Copying SDK files for app integration
echo ============================================
echo.

REM Check if output directory argument provided
if "%~1"=="" (
    echo Usage: copy-for-integration.bat ^<destination-directory^> [--two-files]
    echo Example: copy-for-integration.bat C:\MyApp\app\libs
    echo.
    echo Options:
    echo   --two-files    Copy separate AAR files instead of fat AAR
    exit /b 1
)

set DEST_DIR=%~1
set TWO_FILES=%~2

REM Check if fat AAR exists (primary method)
if not exist "vyx-sdk\build\outputs\aar\vyx-sdk-fat.aar" (
    echo ERROR: vyx-sdk-fat.aar not found!
    echo Please run build-sdk.bat first.
    exit /b 1
)

REM Create destination directory if it doesn't exist
if not exist "%DEST_DIR%" (
    mkdir "%DEST_DIR%"
)

REM Check if user wants two separate files
if "%TWO_FILES%"=="--two-files" (
    echo [Two-File Mode] Copying separate AAR files...

    REM Check if both files exist
    if not exist "vyx-sdk\build\outputs\aar\vyx-sdk-release.aar" (
        echo ERROR: vyx-sdk-release.aar not found!
        exit /b 1
    )
    if not exist "local-maven\com\vyx\vyxclient\1.0.0\vyxclient-1.0.0.aar" (
        echo ERROR: vyxclient.aar not found!
        exit /b 1
    )

    echo Copying vyx-sdk-release.aar...
    copy "vyx-sdk\build\outputs\aar\vyx-sdk-release.aar" "%DEST_DIR%\vyx-sdk-release.aar"
    if %ERRORLEVEL% NEQ 0 (
        echo ERROR: Failed to copy vyx-sdk-release.aar
        exit /b 1
    )

    echo Copying vyxclient.aar...
    copy "local-maven\com\vyx\vyxclient\1.0.0\vyxclient-1.0.0.aar" "%DEST_DIR%\vyxclient.aar"
    if %ERRORLEVEL% NEQ 0 (
        echo ERROR: Failed to copy vyxclient.aar
        exit /b 1
    )

    echo.
    echo ============================================
    echo Success! Files copied to: %DEST_DIR%
    echo ============================================
    echo.
    echo Files copied:
    echo   - vyx-sdk-release.aar
    echo   - vyxclient.aar
    echo.
    echo Next steps:
    echo 1. Add to your app's build.gradle:
    echo.
    echo    dependencies {
    echo        implementation(files("libs/vyx-sdk-release.aar"))
    echo        implementation(files("libs/vyxclient.aar"))
    echo    }
    echo.
    echo 2. Sync Gradle and rebuild your app
    echo.
) else (
    echo [Single-File Mode] Copying fat AAR (recommended)...

    echo Copying vyx-sdk-fat.aar...
    copy "vyx-sdk\build\outputs\aar\vyx-sdk-fat.aar" "%DEST_DIR%\vyx-sdk-fat.aar"
    if %ERRORLEVEL% NEQ 0 (
        echo ERROR: Failed to copy vyx-sdk-fat.aar
        exit /b 1
    )

    echo.
    echo ============================================
    echo Success! File copied to: %DEST_DIR%
    echo ============================================
    echo.
    echo File copied:
    echo   ► vyx-sdk-fat.aar (single file, all dependencies included)
    echo.
    echo Next steps:
    echo 1. Add to your app's build.gradle:
    echo.
    echo    dependencies {
    echo        implementation(files("libs/vyx-sdk-fat.aar"))
    echo    }
    echo.
    echo 2. Sync Gradle and rebuild your app
    echo.
)
