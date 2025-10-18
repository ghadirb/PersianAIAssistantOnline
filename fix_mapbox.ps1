# Fix Mapbox dependencies and push to GitHub

Write-Host "Fixing Mapbox dependencies..." -ForegroundColor Green

# Set project path
$projectPath = "c:\Users\Admin\CascadeProjects\PersianAIAssistantOnline"
Set-Location $projectPath

# Step 1: Check git status
Write-Host "Checking git status..." -ForegroundColor Cyan
git status

# Step 2: Add all files
Write-Host "Adding all files..." -ForegroundColor Cyan
git add .

# Step 3: Commit changes
$commitMessage = "fix: Update Mapbox dependencies to resolve build issues

- Update Mapbox SDK from 9.6.1 to 10.9.0
- Update Mapbox Navigation to 2.5.0
- Replace deprecated mapbox-android-navigation-ui with new navigation SDK
- Fix build errors caused by unavailable Mapbox versions

This resolves the build failure in Codemagic and enables successful compilation."

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

Write-Host "Mapbox dependencies fixed and pushed!" -ForegroundColor Green
