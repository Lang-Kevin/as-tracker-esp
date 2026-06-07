# cleanup.ps1 — Run once to finish the hrtracker → armswing rename
# Usage: powershell -ExecutionPolicy Bypass -File cleanup.ps1
# BleManager.kt is already written to armswing/ble/ — this script handles everything else.

$base = "C:\code\Arduino\ArmSwingProject\code\app\src\main\kotlin\com\kevin"
$src  = "$base\hrtracker"
$dst  = "$base\armswing"

# Create directory structure (idempotent)
@("data\db","data\entity","data\repository","di","domain","export","service",
  "ui\detail","ui\history","ui\live","ui\scan","ui\settings","ui\shared","ui\theme") | ForEach-Object {
    New-Item -ItemType Directory -Force "$dst\$_" | Out-Null
}

# Files to copy: [source-relative, dest-relative]
$moves = @(
    @("MainActivity.kt",                        "MainActivity.kt"),
    @("HrTrackerApplication.kt",                "ArmSwingApplication.kt"),
    @("ble\OmegaReading.kt",                   "ble\OmegaReading.kt"),
    @("data\db\ArmSwingDatabase.kt",            "data\db\ArmSwingDatabase.kt"),
    @("data\db\OmegaSampleDao.kt",              "data\db\OmegaSampleDao.kt"),
    @("data\db\SessionDao.kt",                  "data\db\SessionDao.kt"),
    @("data\entity\OmegaSample.kt",             "data\entity\OmegaSample.kt"),
    @("data\entity\Session.kt",                 "data\entity\Session.kt"),
    @("data\repository\SessionRepository.kt",   "data\repository\SessionRepository.kt"),
    @("data\repository\SettingsRepository.kt",  "data\repository\SettingsRepository.kt"),
    @("di\DataModule.kt",                       "di\DataModule.kt"),
    @("di\DatabaseModule.kt",                   "di\DatabaseModule.kt"),
    @("domain\DiscoveredDevice.kt",             "domain\DiscoveredDevice.kt"),
    @("domain\SavedDevice.kt",                  "domain\SavedDevice.kt"),
    @("export\SessionExporter.kt",              "export\SessionExporter.kt"),
    @("service\HrRecordingService.kt",          "service\ArmSwingRecordingService.kt"),
    @("ui\detail\DetailScreen.kt",              "ui\detail\DetailScreen.kt"),
    @("ui\detail\DetailViewModel.kt",           "ui\detail\DetailViewModel.kt"),
    @("ui\history\HistoryScreen.kt",            "ui\history\HistoryScreen.kt"),
    @("ui\history\HistoryViewModel.kt",         "ui\history\HistoryViewModel.kt"),
    @("ui\live\LiveScreen.kt",                  "ui\live\LiveScreen.kt"),
    @("ui\live\LiveViewModel.kt",               "ui\live\LiveViewModel.kt"),
    @("ui\scan\ScanScreen.kt",                  "ui\scan\ScanScreen.kt"),
    @("ui\scan\ScanViewModel.kt",               "ui\scan\ScanViewModel.kt"),
    @("ui\settings\SettingsScreen.kt",          "ui\settings\SettingsScreen.kt"),
    @("ui\settings\SettingsViewModel.kt",       "ui\settings\SettingsViewModel.kt"),
    @("ui\shared\OmegaLineChart.kt",            "ui\shared\OmegaLineChart.kt"),
    @("ui\shared\TrainingUi.kt",               "ui\shared\TrainingUi.kt"),
    @("ui\theme\Color.kt",                      "ui\theme\Color.kt"),
    @("ui\theme\Theme.kt",                      "ui\theme\Theme.kt")
)

foreach ($pair in $moves) {
    $from = "$src\$($pair[0])"
    $to   = "$dst\$($pair[1])"
    if (Test-Path $from) {
        Copy-Item -Path $from -Destination $to -Force
        Write-Host "  copied $($pair[0]) -> $($pair[1])"
    } else {
        Write-Warning "  missing: $from"
    }
}

# Delete old hrtracker tree
Remove-Item -Recurse -Force $src
Write-Host "Deleted $src"

# Delete finished plan
$plan = "C:\Users\Kiwi PC\.claude\plans\sleepy-cooking-stream.md"
if (Test-Path $plan) {
    Remove-Item -Force $plan
    Write-Host "Deleted plan: $plan"
}

Write-Host ""
Write-Host "Done. New source tree:"
Get-ChildItem -Recurse "$dst" -Filter "*.kt" | ForEach-Object { "  $($_.FullName.Replace($dst,''))" }
