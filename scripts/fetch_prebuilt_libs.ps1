Param(
    [string]$Url = $env:PREBUILT_LIBS_URL
)
if ([string]::IsNullOrWhiteSpace($Url)) {
    Write-Host "PREBUILT_LIBS_URL not set; skipping prebuilt libs download"
    exit 0
}

$dest = Join-Path -Path (Get-Location) -ChildPath "prebuilt_libs"
$tmp = Join-Path -Path $env:TEMP -ChildPath "prebuilt_libs.zip"

Write-Host "Downloading prebuilt libs from $Url"
Invoke-WebRequest -Uri $Url -OutFile $tmp -UseBasicParsing
if (-Not (Test-Path $dest)) { New-Item -ItemType Directory -Path $dest | Out-Null }
Expand-Archive -Path $tmp -DestinationPath $dest -Force
Remove-Item $tmp -Force
Write-Host "Prebuilt libs unpacked to $dest"
