<#
Installs (or updates) Lumen on the device and starts it. Changes nothing else:
the factory home screen stays the default until you run make-default.ps1.

  .\tools\install.ps1 -Ip 192.168.1.50
  .\tools\install.ps1                 # reuses the last address
  .\tools\install.ps1 -Build          # rebuild the APK first
#>
param(
    [string]$Ip,
    [switch]$Build
)
. (Join-Path $PSScriptRoot 'common.ps1')

$root = Split-Path $PSScriptRoot -Parent
$apk = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'

if ($Build -or -not (Test-Path $apk)) {
    Step 'Buduje APK (release)'
    Push-Location $root
    & .\gradlew.bat :app:assembleRelease -q --console=plain
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0 -or -not (Test-Path $apk)) { Fail 'Budowanie nie powiodlo sie.' }
}

Connect-Device $Ip

Step 'Instaluje Lumen'
$result = Adb install -r $apk
if ($result -notmatch 'Success') { Fail "Instalacja nie powiodla sie: $result" }
Ok ('Zainstalowano ' + [math]::Round((Get-Item $apk).Length / 1KB) + ' KB')

Step 'Uruchamiam'
Adb shell am start -n $script:HomeActivity | Out-Null
Start-Sleep -Seconds 5
$appPid = Adb shell pidof $script:Pkg
if (-not $appPid) { Fail 'Launcher nie dziala po uruchomieniu. Uruchom  .\tools\diagnose.ps1  i przeslij wynik.' }
Ok "Lumen dziala (pid $appPid)"

Write-Host ''
Write-Host 'Lumen jest zainstalowany obok fabrycznego ekranu glownego.' -ForegroundColor White
Write-Host 'Wyprobuj go pilotem. Przycisk Home nadal otwiera fabryczny ekran.'
Write-Host 'Gdy wszystko dziala:  .\tools\make-default.ps1'
