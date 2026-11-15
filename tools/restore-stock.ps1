<#
Brings back the factory Android TV home screen. Safe to run at any time, any number of times.
It re-enables every package that make-default.ps1 disabled, plus the known factory launchers.

  .\tools\restore-stock.ps1
  .\tools\restore-stock.ps1 -Uninstall     # also remove Lumen from the device

Without this script, the same thing by hand:
  adb connect <IP>:5555
  adb shell pm enable com.google.android.tvlauncher
#>
param(
    [string]$Ip,
    [switch]$Uninstall
)
. (Join-Path $PSScriptRoot 'common.ps1')
Connect-Device $Ip

Step 'Przywracam fabryczny ekran glowny'
$list = @($script:StockLaunchers)
if (Test-Path $script:DisabledFile) { $list += Get-Content $script:DisabledFile }
$list = $list | Where-Object { $_ } | ForEach-Object { $_.Trim() } | Select-Object -Unique

$restored = 0
foreach ($p in $list) {
    if (Test-Installed $p) {
        Adb shell pm enable $p | Out-Host
        $restored++
    }
}
if ($restored -eq 0) { Write-Host '    Nie znalazlem zadnego fabrycznego launchera na urzadzeniu.' -ForegroundColor Yellow }
if (Test-Path $script:DisabledFile) { Remove-Item $script:DisabledFile -Confirm:$false }

if ($Uninstall) {
    Step 'Odinstalowuje Lumen'
    Adb uninstall $script:Pkg | Out-Host
}

Adb shell input keyevent KEYCODE_HOME | Out-Null
Start-Sleep -Seconds 3
Ok "Ekran glowny: $(Get-HomeResolution)"
Write-Host "    Okno na ekranie: $(Get-FocusedWindow)"
