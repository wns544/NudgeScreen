$ErrorActionPreference = "Stop"

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $adb)) {
    throw "adb.exe was not found at $adb"
}

if (-not (Test-Path $apk)) {
    throw "Debug APK was not found. Build it first with Android Studio or Gradle."
}

& $adb devices
$devices = & $adb devices
$unauthorizedDevices = $devices | Select-String -Pattern "\tunauthorized$"
$authorizingDevices = $devices | Select-String -Pattern "\tauthorizing$"
if ($authorizingDevices) {
    throw "An Android device is waiting for USB debugging authorization. Unlock the phone and accept the RSA prompt, then run this script again."
}
if ($unauthorizedDevices) {
    throw "An Android device is connected but unauthorized. Unlock the phone and allow the USB debugging RSA prompt, then run this script again."
}

& $adb install -r $apk
& $adb shell am start -n com.example.screenlocktodo/.MainActivity
