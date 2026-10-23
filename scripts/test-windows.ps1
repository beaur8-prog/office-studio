$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# This script installs and removes all three apps. It runs only in disposable CI.
if ($env:CI -ne 'true' -or $env:RUNNER_OS -ne 'Windows') {
  throw 'Run only on an isolated Windows CI runner.'
}
$root = Split-Path $PSScriptRoot -Parent
$lock = Get-Content (Join-Path $root 'suite-lock.json') -Raw | ConvertFrom-Json
$release = Join-Path $root "release/$($lock.version)"
$setup = Join-Path $release "Office-Studio-$($lock.version)-win-x64-Setup.exe"
$build = Get-Content (Join-Path $release 'BUILD-win-x64.json') -Raw | ConvertFrom-Json
if ((Get-FileHash $setup -Algorithm SHA256).Hash.ToLowerInvariant() -ne $build.sha256) { throw 'Setup checksum mismatch.' }
$report = [ordered]@{ suiteVersion = $lock.version; setupSHA256 = $build.sha256; passed = $false }

function Entries([string]$name) {
  @(Get-ItemProperty 'HKCU:/Software/Microsoft/Windows/CurrentVersion/Uninstall/*' |
    Where-Object { $_.PSObject.Properties['DisplayName'] -and $_.DisplayName -eq $name })
}
function Run-Setup([string]$file, [string[]]$arguments) {
  $process = Start-Process -FilePath $file -ArgumentList $arguments -PassThru
  if (-not $process.WaitForExit(600000)) { throw "Installer timeout: $file" }
  if ($process.ExitCode -notin @(0,3010)) { throw "Installer failed: $($process.ExitCode)" }
}
function Assert-Installed {
  foreach ($app in $lock.apps) {
    $entry = @(Entries $app.product)
    if ($entry.Count -ne 1 -or $entry[0].DisplayVersion -ne $app.version) { throw "Wrong installed version: $($app.product)" }
    $executable = Join-Path $entry[0].InstallLocation "$($app.product).exe"
    if (-not (Test-Path $executable)) { throw "Missing executable: $executable" }
    if ((Get-Item $executable).VersionInfo.ProductVersion -ne $app.version) { throw "Executable version mismatch: $executable" }
    if (-not (Test-Path (Join-Path $entry[0].InstallLocation 'resources/app.asar'))) { throw 'Missing app.asar' }
    Assert-Preserved $app.product
  }
}
function Assert-Preserved([string]$name) {
  foreach ($file in @((Join-Path $env:APPDATA "$name/suite-preservation-test.txt"), (Join-Path $env:USERPROFILE "Documents/$name-suite-preservation-test.txt"))) {
    if (-not (Test-Path $file) -or (Get-Content $file -Raw).Trim() -ne 'PRESERVE STUDIO SUITE USER DATA') { throw "User data changed: $file" }
  }
}
foreach ($app in $lock.apps) {
  if (@(Entries $app.product).Count -ne 0 -or (Test-Path (Join-Path $env:LOCALAPPDATA "Programs/$($app.product)"))) { throw "Already installed: $($app.product)" }
}
try {
  foreach ($app in $lock.apps) {
    $profile = Join-Path $env:APPDATA $app.product
    New-Item -ItemType Directory $profile -Force | Out-Null
    New-Item -ItemType Directory (Join-Path $env:USERPROFILE 'Documents') -Force | Out-Null
    Set-Content (Join-Path $profile 'suite-preservation-test.txt') 'PRESERVE STUDIO SUITE USER DATA'
    Set-Content (Join-Path $env:USERPROFILE "Documents/$($app.product)-suite-preservation-test.txt") 'PRESERVE STUDIO SUITE USER DATA'
  }
  Run-Setup $setup @('/S')
  Assert-Installed
  $report.freshInstall = $true
  Run-Setup $setup @('/S')
  Assert-Installed
  $report.reinstall = $true
  foreach ($app in $lock.apps) {
    $entry = @(Entries $app.product)[0]
    if ($entry.UninstallString -notmatch '^"([^"]+)"') { throw 'Unexpected uninstaller registration.' }
    $uninstaller = $Matches[1]
    $arguments = if ($app.id -eq 'slide') { @('/S') } else { @('/S','/currentuser') }
    Run-Setup $uninstaller $arguments
    $deadline = (Get-Date).AddSeconds(60)
    while (@(Entries $app.product).Count -ne 0 -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 250 }
    if (@(Entries $app.product).Count -ne 0) { throw "Uninstall registration remains: $($app.product)" }
    if (Test-Path (Join-Path $entry.InstallLocation "$($app.product).exe")) { throw 'Executable remains after uninstall.' }
    Assert-Preserved $app.product
  }
  $report.uninstall = $true
  $report.userDataPreserved = $true
  $report.passed = $true
} finally {
  $results = Join-Path $root 'test-results'
  New-Item -ItemType Directory $results -Force | Out-Null
  $report | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $results 'windows-install.json')
  $report | ConvertTo-Json -Depth 8 | Write-Output
}
