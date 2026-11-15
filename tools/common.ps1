# Shared helpers for the Lumen install/recovery scripts. Dot-sourced by the other scripts.
# Messages are ASCII-only on purpose: Windows PowerShell 5.1 misreads UTF-8 files without a BOM.

$script:Pkg = 'com.lumen.launcher'
$script:HomeActivity = 'com.lumen.launcher/.home.HomeActivity'
$script:StateDir = Join-Path $PSScriptRoot 'state'
$script:DeviceFile = Join-Path $script:StateDir 'device.txt'
$script:DisabledFile = Join-Path $script:StateDir 'disabled-packages.txt'

# Factory home screens this toolkit is allowed to switch off. Anything else is left alone.
$script:StockLaunchers = @(
    'com.google.android.tvlauncher',
    'com.google.android.leanbacklauncher',
    'com.google.android.apps.tv.launcherx'
)

$script:Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $script:Adb)) {
    $found = Get-Command adb -ErrorAction SilentlyContinue
    if ($found) { $script:Adb = $found.Source }
    else { Write-Host 'Nie znaleziono adb. Zainstaluj Android SDK platform-tools.' -ForegroundColor Red; exit 1 }
}
if (-not (Test-Path $script:StateDir)) { New-Item -ItemType Directory -Force $script:StateDir | Out-Null }

function Fail([string]$Message) {
    Write-Host "BLAD: $Message" -ForegroundColor Red
    exit 1
}

function Step([string]$Message) { Write-Host "==> $Message" -ForegroundColor Cyan }
function Ok([string]$Message) { Write-Host "    OK: $Message" -ForegroundColor Green }

# Runs adb against the selected device and returns its output as one trimmed string.
function Adb {
    $out = & $script:Adb -s $script:Serial @args
    if ($null -eq $out) { return '' }
    return (($out | Out-String).Trim())
}

# Connects to the device. Accepts an IP address, IP:port or an emulator serial.
# With no argument it reuses the address saved by a previous run.
function Connect-Device([string]$Ip) {
    if (-not $Ip -and (Test-Path $script:DeviceFile)) { $Ip = (Get-Content $script:DeviceFile -TotalCount 1).Trim() }
    if (-not $Ip) { Fail 'Podaj adres urzadzenia, np.  -Ip 192.168.1.50  (Ustawienia > Siec na Mi Boxie).' }

    if ($Ip -like 'emulator-*') { $script:Serial = $Ip }
    elseif ($Ip -match ':') { $script:Serial = $Ip }
    else { $script:Serial = "${Ip}:5555" }

    Step "Lacze z $($script:Serial)"
    if ($script:Serial -notlike 'emulator-*') { & $script:Adb connect $script:Serial | Out-Host }

    $state = ''
    for ($i = 0; $i -lt 30; $i++) {
        $line = (& $script:Adb devices) | Where-Object { $_ -like "$($script:Serial)*" } | Select-Object -First 1
        if ($line) { $state = ($line -split '\s+')[1] }
        if ($state -eq 'device') { break }
        if ($state -eq 'unauthorized' -and $i -eq 0) {
            Write-Host '    Na ekranie telewizora zaakceptuj pytanie o debugowanie (zaznacz "Zawsze zezwalaj").' -ForegroundColor Yellow
        }
        Start-Sleep -Seconds 2
    }
    if ($state -ne 'device') {
        Fail "Brak polaczenia (stan: '$state'). Sprawdz adres IP, to samo Wi-Fi i wlaczone debugowanie USB w Opcjach programisty."
    }
    Set-Content -Path $script:DeviceFile -Value $Ip -Encoding ascii
    $model = Adb shell getprop ro.product.model
    $release = Adb shell getprop ro.build.version.release
    Ok "Polaczono: $model, Android $release"
}

function Test-Installed([string]$Package) {
    return ((Adb shell pm path $Package) -like 'package:*')
}

function Get-FocusedWindow {
    $line = (Adb shell dumpsys window windows) -split "`n" | Where-Object { $_ -match 'mCurrentFocus' } | Select-Object -First 1
    if ($line) { return $line.Trim() }
    return ''
}

function Get-HomeResolution {
    $out = Adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
    return (($out -split "`n") | Select-Object -Last 1).Trim()
}
