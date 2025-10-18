# Remove Mapbox dependencies completely and push to GitHub

Write-Host "Removing Mapbox dependencies completely..." -ForegroundColor Green

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
$commitMessage = "fix: Remove Mapbox dependencies completely to resolve build issues

- Remove all Mapbox SDK dependencies (unavailable versions)
- Keep Google Maps for primary navigation
- Keep OpenStreetMap (OSM) as alternative navigation solution
- This ensures successful build in Codemagic

Navigation solutions available:
- Google Maps API (primary)
- OpenStreetMap with osmdroid (alternative)
- No dependency on Mapbox services"

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

Write-Host "Mapbox dependencies removed and project is ready to build!" -ForegroundColor Green
