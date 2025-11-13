# Script for building Android debug APK locally
# This can help identify if there are any remaining issues

Write-Host "🚀 Starting Android debug build..." -ForegroundColor Green

# Clean previous builds
Write-Host "🧹 Cleaning previous builds..." -ForegroundColor Yellow
./gradlew clean

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Clean failed!" -ForegroundColor Red
    exit 1
}

# Build debug APK
Write-Host "🔨 Building debug APK..." -ForegroundColor Yellow
./gradlew assembleDebug --stacktrace

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    Write-Host "Please check the error messages above." -ForegroundColor Red
    exit 1
}

# Success
Write-Host "✅ Build successful!" -ForegroundColor Green
Write-Host "📱 APK location: app/build/outputs/apk/debug/app-debug.apk" -ForegroundColor Cyan

# Open the folder containing the APK
Invoke-Item "app/build/outputs/apk/debug/"
