param(
    [ValidateSet("release", "debug")]
    [string] $Variant = "release",
    [int] $Cycles = 3,
    [switch] $SkipInstall,
    [string] $OutputDir = "lock-compare"
)

$ErrorActionPreference = "Stop"

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$package = "com.wns544.nudgescreen"
$main = "$package/com.example.screenlocktodo.MainActivity"
$lockActivity = "com.example.screenlocktodo.LockActivity"
$apk = Join-Path $PSScriptRoot "app\build\outputs\apk\$Variant\app-$Variant.apk"
$outRoot = Join-Path $PSScriptRoot $OutputDir
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$out = Join-Path $outRoot "wallpaper-$stamp"

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]] $Args)
    & $adb @Args
}

function Save-AdbText {
    param(
        [string] $Name,
        [string[]] $Args
    )
    $path = Join-Path $out $Name
    Invoke-Adb @Args | Out-File -FilePath $path -Encoding utf8
    return $path
}

function Save-Screenshot {
    param([string] $Name)
    $path = Join-Path $out $Name
    $remote = "/sdcard/nudgescreen-$Name"
    Invoke-Adb shell screencap -p $remote | Out-Null
    Invoke-Adb pull $remote $path | Out-Null
    Invoke-Adb shell rm $remote | Out-Null
    return $path
}

if (-not (Test-Path $adb)) {
    throw "adb.exe was not found at $adb"
}

if (-not $SkipInstall -and -not (Test-Path $apk)) {
    throw "$Variant APK was not found at $apk. Build it first with Gradle."
}

$devices = Invoke-Adb devices
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

New-Item -ItemType Directory -Force -Path $out | Out-Null

Write-Host "Writing evidence to $out"
Save-AdbText "device.txt" @("devices", "-l") | Out-Null
Save-AdbText "props.txt" @("shell", "getprop") | Out-Null
Save-AdbText "wallpaper-before.txt" @("shell", "dumpsys", "wallpaper") | Out-Null

if (-not $SkipInstall) {
    Write-Host "Installing $apk"
    Invoke-Adb install -r $apk
}

Invoke-Adb shell pm grant $package android.permission.POST_NOTIFICATIONS 2>$null
Invoke-Adb shell logcat -c
Invoke-Adb shell am start -n $main | Out-Null

Write-Host ""
Write-Host "Before the lock-screen wallpaper check:"
Write-Host "1. In the app, remove any manually selected background photo if you want pure system wallpaper transparency."
Write-Host "2. Ensure the lock curtain is enabled and full-screen alerts are allowed."
Write-Host "3. Set the Samsung lock screen wallpaper you want to test."
Write-Host "4. Leave the phone unlocked, then press Enter here."
Read-Host | Out-Null

for ($i = 1; $i -le $Cycles; $i++) {
    Write-Host "Cycle ${i}/${Cycles}: screen off"
    Invoke-Adb shell input keyevent KEYCODE_POWER
    Start-Sleep -Seconds 2

    Write-Host "Cycle ${i}/${Cycles}: screen on"
    Invoke-Adb shell input keyevent KEYCODE_POWER
    Start-Sleep -Seconds 5

    Save-Screenshot ("nudge-cycle-{0}.png" -f $i) | Out-Null
    Save-AdbText ("wallpaper-cycle-{0}.txt" -f $i) @("shell", "dumpsys", "wallpaper") | Out-Null
    Save-AdbText ("activity-cycle-{0}.txt" -f $i) @("shell", "dumpsys", "activity", "activities") | Out-Null
    Save-AdbText ("window-cycle-{0}.txt" -f $i) @("shell", "dumpsys", "window") | Out-Null

    $activityDump = Get-Content (Join-Path $out ("activity-cycle-{0}.txt" -f $i)) -Raw
    if ($activityDump -match [regex]::Escape($lockActivity)) {
        Write-Host "PASS: LockActivity appears in the activity stack for cycle $i."
    } else {
        Write-Warning "LockActivity was not detected in the activity stack for cycle $i."
    }

    Write-Host "Unlock or dismiss the phone back to an unlocked state, then press Enter for the next cycle."
    if ($i -lt $Cycles) {
        Read-Host | Out-Null
    }
}

Save-AdbText "logcat-nudge.txt" @("logcat", "-d", "-v", "time") | Out-Null

Write-Host ""
Write-Host "Evidence collected:"
Write-Host $out
Write-Host ""
Write-Host "Check these in the evidence files:"
Write-Host "- wallpaper-cycle-*.txt: look for mWpType, mUri=multipack://, mLastLockWallpaper, and wallpaper target entries."
Write-Host "- logcat-nudge.txt: look for 'using system wallpaper background' and 'system wallpaper state'."
Write-Host "- nudge-cycle-*.png: compare the visible background with the Samsung lock screen wallpaper for each cycle."
