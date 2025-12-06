#!/usr/bin/env pwsh
# ================================================================
# Quick Build Script for Persian AI Assistant
# استخدام: .\build-quick.ps1
# ================================================================

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Persian AI Assistant - Quick Build" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Get current directory
$ProjectRoot = Get-Location
Write-Host "📂 Project Root: $ProjectRoot" -ForegroundColor Yellow

# Check if gradlew exists
if (-Not (Test-Path ".\gradlew")) {
    Write-Host "❌ Error: gradlew not found!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "🔧 Build Steps:" -ForegroundColor Cyan
Write-Host "================"

# Step 1: Clean
Write-Host ""
Write-Host "1️⃣  Cleaning previous build..." -ForegroundColor Yellow
& .\gradlew clean
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Clean failed!" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Clean completed" -ForegroundColor Green

# Step 2: Build
Write-Host ""
Write-Host "2️⃣  Building Debug APK..." -ForegroundColor Yellow
& .\gradlew assembleDebug --stacktrace --info
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "✅ Build completed" -ForegroundColor Green

# Step 3: Check APK
Write-Host ""
Write-Host "3️⃣  Checking APK..." -ForegroundColor Yellow
$ApkPath = "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $ApkPath) {
    $ApkSize = (Get-Item $ApkPath).Length / 1MB
    Write-Host "✅ APK found: $ApkPath" -ForegroundColor Green
    Write-Host "   Size: $([Math]::Round($ApkSize, 2)) MB" -ForegroundColor Green
} else {
    Write-Host "❌ APK not found at: $ApkPath" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  ✅ BUILD SUCCESSFUL!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "📱 APK Location:" -ForegroundColor Cyan
Write-Host "   $ApkPath" -ForegroundColor Yellow
Write-Host ""
Write-Host "🚀 Next Steps:" -ForegroundColor Cyan
Write-Host "   1. Install: adb install $ApkPath" -ForegroundColor White
Write-Host "   2. Push to GitHub: git push origin restore-old-version" -ForegroundColor White
Write-Host ""
