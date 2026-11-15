<#
Read-only report about the device. Changes nothing. Writes tools\state\report-<time>.txt with:
installed, disabled and third-party packages, home-screen candidates, memory and storage
use, and Lumen's own state (memory, frame statistics, last crash).

This is the input for deciding what is safe to switch off on the device - nothing gets
disabled based on guesses.

  .\tools\diagnose.ps1
#>
param([string]$Ip)
. (Join-Path $PSScriptRoot 'common.ps1')
Connect-Device $Ip

$file = Join-Path $script:StateDir ("report-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + ".txt")
function Section([string]$Title, [string]$Body) {
    Add-Content -Path $file -Value ("`r`n===== $Title =====`r`n" + $Body) -Encoding utf8
}

Step 'Zbieram informacje (tylko odczyt)'
Section 'DEVICE' ((Adb shell getprop ro.product.model) + ' | Android ' + (Adb shell getprop ro.build.version.release) + ' | build ' + (Adb shell getprop ro.build.display.id))
Section 'DISPLAY' ((Adb shell wm size) + "`r`n" + (Adb shell wm density))
Section 'HOME CANDIDATES' (Adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME)
Section 'HOME RESOLVES TO' (Get-HomeResolution)
Section 'TV APPS (LEANBACK)' (Adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER)
Section 'PACKAGES: THIRD PARTY' (Adb shell pm list packages -3)
Section 'PACKAGES: DISABLED' (Adb shell pm list packages -d)
Section 'PACKAGES: SYSTEM' (Adb shell pm list packages -s)
Section 'MEMORY SUMMARY' (Adb shell cat /proc/meminfo)
Section 'MEMORY BY PROCESS' (Adb shell dumpsys meminfo)
Section 'STORAGE' (Adb shell df -h /data /cache)
Section 'RUNNING SERVICES' (Adb shell dumpsys activity services)
Section 'ANIMATION SCALES' ('window=' + (Adb shell settings get global window_animation_scale) + ' transition=' + (Adb shell settings get global transition_animation_scale) + ' animator=' + (Adb shell settings get global animator_duration_scale))

if (Test-Installed $script:Pkg) {
    Section 'LUMEN MEMORY' (Adb shell dumpsys meminfo $script:Pkg)
    Section 'LUMEN FRAMES' (Adb shell dumpsys gfxinfo $script:Pkg)
    Section 'LUMEN LOG' (Adb logcat -d -t 400 AndroidRuntime:E ActivityManager:W *:S)
}

Ok "Zapisano: $file"
