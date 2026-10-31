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
  if (-not (Test-Path 'HKCU:/Software/Microsoft/Windows/CurrentVersion/Uninstall')) { return @() }
  @(Get-ItemProperty 'HKCU:/Software/Microsoft/Windows/CurrentVersion/Uninstall/*' |
    Where-Object { $_.PSObject.Properties['DisplayName'] -and $_.DisplayName -eq $name })
}
function Run-Setup([string]$file, [string[]]$arguments) {
  $process = Start-Process -FilePath $file -ArgumentList $arguments -PassThru
  if (-not $process.WaitForExit(600000)) { throw "Installer timeout: $file" }
  if ($process.ExitCode -notin @(0,3010)) { throw "Installer failed: $($process.ExitCode)" }
}
function Install-Directory($entry) {
  if ($entry.PSObject.Properties['InstallLocation'] -and $entry.InstallLocation) { return $entry.InstallLocation }
  # electron-builder NSIS records its install path in UninstallString.
  if ($entry.UninstallString -match '^"([^"]+)"') { return Split-Path -Parent $Matches[1] }
  throw 'Installation directory is missing from the app registration.'
}
function Assert-Installed {
  foreach ($app in $lock.apps) {
    $entry = @(Entries $app.product)
    if ($entry.Count -ne 1 -or $entry[0].DisplayVersion -ne $app.version) { throw "Wrong installed version: $($app.product)" }
    $directory = Install-Directory $entry[0]
    $executable = Join-Path $directory "$($app.product).exe"
    if (-not (Test-Path $executable)) { throw "Missing executable: $executable" }
    if ((Get-Item $executable).VersionInfo.ProductVersion -ne $app.version) { throw "Executable version mismatch: $executable" }
    if (-not (Test-Path (Join-Path $directory 'resources/app.asar'))) { throw 'Missing app.asar' }
    Assert-Preserved $app.product
  }
}
function Assert-Preserved([string]$name) {
  foreach ($file in @((Join-Path $env:APPDATA "$name/suite-preservation-test.txt"), (Join-Path $env:USERPROFILE "Documents/$name-suite-preservation-test.txt"))) {
    if (-not (Test-Path $file) -or (Get-Content $file -Raw).Trim() -ne 'PRESERVE STUDIO SUITE USER DATA') { throw "User data changed: $file" }
  }
}
function Remove-App($app) {
  $entry = @(Entries $app.product)[0]
  $directory = Install-Directory $entry
  if ($entry.UninstallString -notmatch '^"([^"]+)"') { throw 'Unexpected uninstaller registration.' }
  $uninstaller = $Matches[1]
  $arguments = if ($app.id -eq 'slide') { @('/S') } else { @('/S','/currentuser') }
  Run-Setup $uninstaller $arguments
  $deadline = (Get-Date).AddSeconds(60)
  while (@(Entries $app.product).Count -ne 0 -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 250 }
  if (@(Entries $app.product).Count -ne 0) { throw "Uninstall registration remains: $($app.product)" }
  if (Test-Path (Join-Path $directory "$($app.product).exe")) { throw 'Executable remains after uninstall.' }
  Assert-Preserved $app.product
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
  Write-Output 'All three apps installed; registered versions and preserved user data verified.'
  Run-Setup $setup @('/S')
  Assert-Installed
  $report.reinstall = $true
  Write-Output 'Suite reinstall and user data preservation verified.'
  # Exercise the real update helper from the previous public app versions.
  # Only this disposable CI runner is used; no user application is terminated.
  $previous = Get-Content (Join-Path $root 'previous-apps-lock.json') -Raw | ConvertFrom-Json
  $extracted = Join-Path $env:RUNNER_TEMP 'office-studio-payloads'
  & 7z x -y "-o$extracted" $setup | Out-Null
  if ($LASTEXITCODE -ne 0) { throw 'Payload extraction failed.' }
  $report.upgrades = @()
  foreach ($app in $lock.apps) {
    $entry = @(Entries $app.product)[0]
    $installDirectory = Install-Directory $entry
    $old = @($previous.apps | Where-Object id -eq $app.id)[0]
    $directory = Join-Path $env:RUNNER_TEMP "office-studio-사용자 O'Brien 업데이트-$($app.id)"
    New-Item -ItemType Directory $directory -Force | Out-Null
    $oldSetup = Join-Path $directory 'previous.exe'
    Invoke-WebRequest -Uri $old.assets.'win-x64'.url -OutFile $oldSetup
    if ((Get-FileHash $oldSetup -Algorithm SHA256).Hash.ToLowerInvariant() -ne $old.assets.'win-x64'.sha256) { throw 'Previous installer digest mismatch.' }
    $arguments = if ($app.id -eq 'slide') { @('/S') } else { @('/S','/currentuser') }
    Remove-App $app
    Run-Setup $oldSetup $arguments
    $executable = Join-Path $installDirectory "$($app.product).exe"
    if ((Get-Item $executable).VersionInfo.ProductVersion -ne $old.version) { throw 'Previous app was not installed.' }
    $newSetup = Join-Path $directory 'update.exe'
    Copy-Item -LiteralPath (Join-Path $extracted ('$PLUGINSDIR/' + $app.id + '-setup.exe')) -Destination $newSetup
    $log = Join-Path $directory '업데이트 기록.log'
    $jobFile = Join-Path $directory 'job.json'
    # The wait target exits naturally, as the editor does before handing off.
    $parent = Start-Process $env:ComSpec -ArgumentList @('/c','exit','0') -PassThru
    $parent.WaitForExit()
    @{ schema=1; id=$app.id; parentPid=$parent.Id; installer=$newSetup; bytes=$app.assets.'win-x64'.bytes; sha256=$app.assets.'win-x64'.sha256; version=$app.version; executable=$executable; log=$log } | ConvertTo-Json | Set-Content -LiteralPath $jobFile
    & powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File (Join-Path $root 'updater/studio-update-windows.ps1') -JobFile $jobFile
    if ($LASTEXITCODE -ne 0 -or (Get-Content $log -Raw) -notmatch '(?m)^Installed ') { throw "Real update failed: $($app.id)" }
    $deadline = (Get-Date).AddSeconds(45)
    do {
      Start-Sleep -Milliseconds 500
      $running = @(Get-Process -Name $app.product -ErrorAction SilentlyContinue | Where-Object MainWindowHandle -ne 0)
    } while ($running.Count -eq 0 -and (Get-Date) -lt $deadline)
    if ($running.Count -eq 0) { throw "Updated app did not open a window: $($app.id)" }
    foreach ($process in $running) { [void]$process.CloseMainWindow(); if (-not $process.WaitForExit(30000)) { throw 'Updated app did not close normally.' } }
    $asar = Join-Path $installDirectory 'resources/app.asar'
    & node (Join-Path $root 'scripts/verify-installed-updater.cjs') $asar $app.id $app.version
    if ($LASTEXITCODE -ne 0) { throw 'Installed update controller verification failed.' }
    Assert-Preserved $app.product
    $report.upgrades += @{ app=$app.id; from=$old.version; to=$app.version; helper=$true; nativeWindow=$true; normalClose=$true }
  }
  Assert-Installed
  foreach ($app in $lock.apps) {
    Remove-App $app
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
