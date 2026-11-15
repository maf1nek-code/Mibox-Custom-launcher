<#
Makes Lumen the home screen by switching off the factory Android TV home screen.

Android TV ignores the normal "default home app" setting, so the only way without root is
to disable the factory launcher for the current user. Nothing is uninstalled, and
restore-stock.ps1 undoes it at any time.

Safety order:
  1. Lumen must be installed, must start, and must still be running after a few seconds.
  2. Only known factory launchers are disabled; the system fallback home is never touched.
  3. After the switch the Home button is pressed and checked. If Lumen does not come up,
     the factory launcher is re-enabled automatically.

  .\tools\make-default.ps1
  .\tools\make-default.ps1 -Yes       # skip the confirmation question
#>
param(
    [string]$Ip,
    [switch]$Yes
)
. (Join-Path $PSScriptRoot 'common.ps1')
Connect-Device $Ip

Step 'Sprawdzam, czy Lumen dziala'
if (-not (Test-Installed $script:Pkg)) { Fail 'Lumen nie jest zainstalowany. Najpierw uruchom  .\tools\install.ps1' }
Adb shell am start -n $script:HomeActivity | Out-Null
Start-Sleep -Seconds 6
if (-not (Adb shell pidof $script:Pkg)) { Fail 'Lumen nie uruchamia sie poprawnie. Niczego nie zmieniono.' }
if ((Get-FocusedWindow) -notmatch [regex]::Escape($script:Pkg)) { Fail 'Lumen nie jest na ekranie po uruchomieniu. Niczego nie zmieniono.' }
Ok 'Lumen uruchamia sie i pozostaje aktywny'

Step 'Szukam fabrycznego ekranu glownego'
$homes = (Adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME) -split "`n" |
    Where-Object { $_ -match '/' } | ForEach-Object { ($_.Trim() -split '/')[0] } | Select-Object -Unique
$targets = @($homes | Where-Object { $script:StockLaunchers -contains $_ })
$others = @($homes | Where-Object { $_ -ne $script:Pkg -and $script:StockLaunchers -notcontains $_ })
foreach ($o in $others) { Write-Host "    Pomijam (nie ruszam): $o" -ForegroundColor DarkGray }

if ($targets.Count -eq 0) {
    if ((Get-HomeResolution) -match [regex]::Escape($script:Pkg)) { Ok 'Lumen juz jest ekranem glownym.'; exit 0 }
    Fail 'Nie znalazlem znanego fabrycznego launchera. Uruchom  .\tools\diagnose.ps1  i przeslij wynik.'
}
Write-Host "    Do wylaczenia: $($targets -join ', ')"

if (-not $Yes) {
    $answer = Read-Host 'Wylaczyc fabryczny ekran glowny i ustawic Lumen? (t/n)'
    if ($answer -notmatch '^[tTyY]') { Write-Host 'Przerwano. Niczego nie zmieniono.'; exit 0 }
}

Step 'Przelaczam ekran glowny'
foreach ($t in $targets) {
    Adb shell pm disable-user --user 0 $t | Out-Host
    Add-Content -Path $script:DisabledFile -Value $t -Encoding ascii
}

Adb shell input keyevent KEYCODE_HOME | Out-Null
Start-Sleep -Seconds 4
$focus = Get-FocusedWindow
$resolved = Get-HomeResolution
$running = Adb shell pidof $script:Pkg

if ($focus -match [regex]::Escape($script:Pkg) -and $resolved -match [regex]::Escape($script:Pkg) -and $running) {
    Ok 'Przycisk Home otwiera teraz Lumen'
    Write-Host ''
    Write-Host 'Gotowe. Powrot do fabrycznego ekranu:  .\tools\restore-stock.ps1' -ForegroundColor White
    Write-Host 'Zostaw wlaczone debugowanie USB na Mi Boxie - to droga awaryjna.'
    exit 0
}

Write-Host '    Lumen nie przejal przycisku Home. Przywracam fabryczny ekran glowny.' -ForegroundColor Yellow
foreach ($t in $targets) { Adb shell pm enable $t | Out-Host }
Adb shell input keyevent KEYCODE_HOME | Out-Null
Fail "Przelaczenie cofniete. Okno: $focus ; Home: $resolved"
