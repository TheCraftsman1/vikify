# Live Debug Lite for Vikify
# This script monitors for file changes and triggers an install, then streams logs.

$ADB = "C:\Users\vishn\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$PACKAGE_NAME = "com.vikify.app.debug"

Write-Host "--- Starting Live Debug Lite ---" -ForegroundColor Cyan

# 1. Initial Build and Install
Write-Host "Performing initial build and install..." -ForegroundColor Yellow
./gradlew :app:installDebug

if ($LASTEXITCODE -ne 0) {
    Write-Host "Initial build failed. Please fix errors and try again." -ForegroundColor Red
    exit
}

# 2. Start the App
Write-Host "Launching app..." -ForegroundColor Yellow
& $ADB shell monkey -p $PACKAGE_NAME -c android.intent.category.LAUNCHER 1

# 3. Stream Logs (Filtered)
Write-Host "Streaming logs (Ctrl+C to stop)..." -ForegroundColor Green
& $ADB logcat -v time *:S Vikify:V PlayerViewModel:V AndroidRuntime:E Exception:V
