@echo off
echo Publishing vyxclient.aar to local Maven repository...

cd vyxclient-publisher
call ..\gradlew.bat publish --no-daemon

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Successfully published vyxclient.aar to local Maven repository!
    echo Location: sdk/android/local-maven/
) else (
    echo.
    echo Failed to publish vyxclient.aar
    exit /b 1
)
