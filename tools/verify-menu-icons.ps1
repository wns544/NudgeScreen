$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$jsonPath = Join-Path $root "app\src\main\res\raw\menu_icon_geometry.json"
$drawableDir = Join-Path $root "app\src\main\res\drawable"

function Normalize-PathData {
    param([string] $Value)
    return ($Value -replace "\s+", "" -replace ",", "")
}

function Get-PathDataByName {
    param(
        [string] $FileName,
        [string] $PathName
    )
    $path = Join-Path $drawableDir $FileName
    $xml = Get-Content -LiteralPath $path -Raw
    $pattern = '<path[\s\S]*?android:name="' + [regex]::Escape($PathName) + '"[\s\S]*?android:pathData="([^"]+)"'
    $match = [regex]::Match($xml, $pattern)
    if (-not $match.Success) {
        throw "Path '$PathName' was not found in $FileName"
    }
    return $match.Groups[1].Value
}

function Assert-PathData {
    param(
        [string] $Label,
        [string] $Expected,
        [string] $Actual
    )
    $expectedNormalized = Normalize-PathData $Expected
    $actualNormalized = Normalize-PathData $Actual
    if ($expectedNormalized -ne $actualNormalized) {
        throw "$Label mismatch.`nExpected: $Expected`nActual:   $Actual"
    }
    Write-Host "OK $Label"
}

$spec = Get-Content -LiteralPath $jsonPath -Raw | ConvertFrom-Json

Assert-PathData "locked.shackle" $spec.locked.shackle (Get-PathDataByName "ic_menu_lock_closed.xml" "shackle")
Assert-PathData "locked.body" $spec.locked.body (Get-PathDataByName "ic_menu_lock_closed.xml" "body")
Assert-PathData "locked.keyCircle" $spec.locked.keyCircle (Get-PathDataByName "ic_menu_lock_closed.xml" "key_circle")
Assert-PathData "locked.keySlot" $spec.locked.keySlot (Get-PathDataByName "ic_menu_lock_closed.xml" "key_slot")

Assert-PathData "unlocked.shackle" $spec.unlocked.shackle (Get-PathDataByName "ic_menu_lock_open.xml" "shackle")
Assert-PathData "unlocked.body" $spec.unlocked.body (Get-PathDataByName "ic_menu_lock_open.xml" "body")
Assert-PathData "unlocked.keyCircle" $spec.unlocked.keyCircle (Get-PathDataByName "ic_menu_lock_open.xml" "key_circle")
Assert-PathData "unlocked.keySlot" $spec.unlocked.keySlot (Get-PathDataByName "ic_menu_lock_open.xml" "key_slot")

Assert-PathData "undo.arrowHead" $spec.undo.arrowHead (Get-PathDataByName "ic_menu_undo_uturn.xml" "arrow_head")
Assert-PathData "undo.stem" $spec.undo.stem (Get-PathDataByName "ic_menu_undo_uturn.xml" "stem")

Write-Host "Menu icon geometry verification passed."
