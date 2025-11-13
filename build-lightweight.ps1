# Lightweight build script for low-memory systems
# Optimized for systems with limited RAM

Write-Host "🚀 Starting lightweight Android build..." -ForegroundColor Green

# Set low memory options
$env:JAVA_OPTS = "-Xmx1536m -XX:MaxMetaspaceSize=256m"
$env:GRADLE_OPTS = "-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=256m -XX:+UseG1GC"
$env:ORG_GRADLE_PROJECT_DAEMON = "false"

# Stop any running Gradle daemon
Write-Host "🛑 Stopping Gradle daemon..." -ForegroundColor Yellow
./gradlew --stop

# Clean with minimal memory
Write-Host "🧹 Cleaning (minimal mode)..." -ForegroundColor Yellow
./gradlew clean --no-daemon --max-workers=1 --parallel=false

# Build debug APK with minimal settings
Write-Host "🔨 Building debug APK (lightweight mode)..." -ForegroundColor Yellow
./gradlew assembleDebug --no-daemon --max-workers=1 --parallel=false --stacktrace

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Build successful!" -ForegroundColor Green
    Write-Host "📱 APK: app/build/outputs/apk/debug/app-debug.apk" -ForegroundColor Cyan
    
    # Show APK size
    $apkPath = "app/build/outputs/apk/debug/app-debug.apk"
    if (Test-Path $apkPath) {
        $size = (Get-Item $apkPath).Length / 1MB
        Write-Host "📊 APK Size: $([math]::Round($size, 2)) MB" -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    Write-Host "Try running: .\build-debug-locally.ps1" -ForegroundColor Yellow
}
