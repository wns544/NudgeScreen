$ErrorActionPreference = "Stop"

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
$package = "com.example.screenlocktodo"
$main = "$package/.MainActivity"
$lockActivity = "$package.LockActivity"

if (-not (Test-Path $adb)) {
    throw "adb.exe was not found at $adb"
}

if (-not (Test-Path $apk)) {
    throw "Debug APK was not found. Build it first with Android Studio or Gradle."
}

$devices = & $adb devices
$readyDevices = $devices | Select-String -Pattern "\tdevice$"
$unauthorizedDevices = $devices | Select-String -Pattern "\tunauthorized$"
$authorizingDevices = $devices | Select-String -Pattern "\tauthorizing$"
if ($authorizingDevices) {
    throw "An Android device is waiting for USB debugging authorization. Unlock the phone and accept the RSA prompt, then run this script again."
}
if ($unauthorizedDevices) {
    throw "An Android device is connected but unauthorized. Unlock the phone and allow the USB debugging RSA prompt, then run this script again."
}
if (-not $readyDevices) {
    throw "No unlocked USB-debugging Android device is connected."
}

& $adb install -r $apk
& $adb shell pm grant $package android.permission.POST_NOTIFICATIONS 2>$null
& $adb shell am start -n $main

Write-Host ""
Write-Host "Before the lock-screen check:"
Write-Host "1. In the app, allow notifications/full-screen notifications and battery exception if prompted."
Write-Host "2. Add at least one todo item."
Write-Host "3. Leave the phone unlocked on the Todo Lock app, then press Enter here."
Read-Host

Write-Host "Turning the screen off..."
& $adb shell input keyevent KEYCODE_POWER
Start-Sleep -Seconds 2

Write-Host "Turning the screen back on..."
& $adb shell input keyevent KEYCODE_POWER
Start-Sleep -Seconds 5

$activityDump = & $adb shell dumpsys activity activities
if ($activityDump -match [regex]::Escape($lockActivity)) {
    Write-Host "PASS: LockActivity is visible in the activity stack."
} else {
    Write-Warning "LockActivity was not detected automatically."
    Write-Host "Manual check: the Todo Lock screen should appear before the Galaxy fingerprint/PIN unlock."
}

Write-Host ""
Write-Host "Final manual check:"
Write-Host "1. Add/delete a todo on the lock screen."
Write-Host "2. Tap the fingerprint/PIN unlock button."
Write-Host "3. Confirm the Galaxy system unlock flow continues normally."
