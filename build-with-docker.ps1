# Build Android APK using Docker (low memory usage)
Write-Host "🐳 Starting Docker build..." -ForegroundColor Green

# Check if Docker is running
try {
    docker version | Out-Null
} catch {
    Write-Host "❌ Docker is not running!" -ForegroundColor Red
    Write-Host "Please install and start Docker Desktop." -ForegroundColor Yellow
    exit 1
}

# Build Docker image
Write-Host "🔨 Building Docker image..." -ForegroundColor Yellow
docker build -f Dockerfile.android -t android-builder .

if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Docker image build failed!" -ForegroundColor Red
    exit 1
}

# Run build container
Write-Host "🚀 Running Android build in container..." -ForegroundColor Yellow
docker run --rm -v "${PWD}:/app" android-builder

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Build successful!" -ForegroundColor Green
    Write-Host "📱 APK location: app/build/outputs/apk/debug/app-debug.apk" -ForegroundColor Cyan
    
    # Show APK info
    if (Test-Path "app/build/outputs/apk/debug/app-debug.apk") {
        $size = (Get-Item "app/build/outputs/apk/debug/app-debug.apk").Length / 1MB
        Write-Host "📊 APK Size: $([math]::Round($size, 2)) MB" -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ Build failed!" -ForegroundColor Red
}
