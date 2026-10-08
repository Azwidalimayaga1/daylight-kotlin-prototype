param([Parameter(Mandatory=$true)][string]$Device)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { $adb='adb' }
& $adb -s $Device reverse tcp:8080 tcp:8080
if ($LASTEXITCODE -ne 0) { throw 'ADB reverse failed. Connect and authorize the phone.' }
& $adb -s $Device install -r (Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $adb -s $Device shell am start -n com.daylight.app/.MainActivity
