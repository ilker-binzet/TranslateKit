@echo off
echo ====================================
echo  TranslateKit by Ilker Binzet
echo  Build Script
echo ====================================
echo.

cd /d "%~dp0"

echo [1/3] Cleaning previous build...
call gradlew.bat clean
if errorlevel 1 (
    echo ERROR: Clean failed!
    pause
    exit /b 1
)
echo Done!
echo.

echo [2/3] Building plugin MTP file...
call gradlew.bat app:packageReleaseMtp
if errorlevel 1 (
    echo ERROR: Build failed!
    pause
    exit /b 1
)
echo Done!
echo.

echo [3/3] Locating output...
if exist "app\build\outputs\mt-plugin\app-release.mtp" (
    echo.
    echo ====================================
    echo  BUILD SUCCESSFUL!
    echo ====================================
    echo.
    echo Output location:
    echo %cd%\app\build\outputs\mt-plugin\app-release.mtp
    echo.
    echo Next steps:
    echo 1. Copy app-release.mtp to your Android device
    echo 2. Open the file in MT Manager
    echo 3. Install the plugin
    echo 4. Configure API key in settings
    echo.
    explorer /select,"app\build\outputs\mt-plugin\app-release.mtp"
) else (
    echo WARNING: MTP file not found!
    echo Check build output for errors.
)

echo.
pause
