# Git Push Script for Persian AI Assistant
# This script automatically pushes changes to GitHub

Write-Host "Starting GitHub push process..." -ForegroundColor Green

# Set project path
$projectPath = "c:\Users\Admin\CascadeProjects\PersianAIAssistantOnline"
Set-Location $projectPath

Write-Host "Project path: $projectPath" -ForegroundColor Yellow

# Step 1: Check git status
Write-Host "Checking git status..." -ForegroundColor Cyan
git status

# Step 2: Add all files
Write-Host "Adding all files..." -ForegroundColor Cyan
git add .

# Step 3: Commit changes
$commitMessage = "feat: Implement enhanced music player with RealMusicService

- Add RealMusicService with ExoPlayer for better media playback
- Implement MediaSessionCompat for notification controls
- Update MusicActivity to use RealMusicService instead of direct ExoPlayer
- Add music notification with play/pause/skip controls
- Create Song model for music data structure
- Add necessary drawable icons for music controls
- Add media support dependencies to build.gradle
- Register RealMusicService in AndroidManifest.xml

This enhancement provides:
- Background music playback
- System notification with media controls
- Better audio focus handling
- Improved performance with ExoPlayer
- Media session integration for system-wide controls"

Write-Host "Committing changes..." -ForegroundColor Cyan
git commit -m $commitMessage

# Step 4: Push to GitHub
Write-Host "Pushing to GitHub..." -ForegroundColor Cyan
try {
    git push origin main
    Write-Host "Changes successfully pushed to GitHub!" -ForegroundColor Green
} catch {
    Write-Host "Error pushing to GitHub: $_" -ForegroundColor Red
    Write-Host "Please check your authentication" -ForegroundColor Yellow
}

Write-Host "Process completed!" -ForegroundColor Green
