param([Parameter(Mandatory=$true)][string]$Device,[Parameter(Mandatory=$true)][string]$OutputPath)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { $adb='adb' }
& $adb -s $Device reverse tcp:8080 tcp:8080
& $adb -s $Device install -r (Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'App installation failed.' }
& $adb -s $Device install -r (Join-Path $project 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed.' }
& $adb -s $Device shell pm clear com.daylight.app
if ($LASTEXITCODE -ne 0) { throw 'Could not reset demonstration app.' }
$capture=Start-Process -FilePath $adb -ArgumentList '-s',$Device,'shell','screenrecord','--bit-rate','4000000','--time-limit','180','/sdcard/daylight-demo.mp4' -WindowStyle Hidden -PassThru
Start-Sleep -Seconds 2
try {
    & $adb -s $Device shell am instrument -w -e class com.daylight.app.DemoTest com.daylight.app.test/androidx.test.runner.AndroidJUnitRunner
    if ($LASTEXITCODE -ne 0) { throw 'Walkthrough command failed. Inspect instrumentation output.' }
} finally {
    & $adb -s $Device shell pkill -2 screenrecord
    $capture.WaitForExit(10000) | Out-Null
    & $adb -s $Device pull /sdcard/daylight-demo.mp4 $OutputPath
}
