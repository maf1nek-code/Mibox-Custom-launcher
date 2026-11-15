<#
Copies an image to Lumen's backgrounds folder on the device.

  .\tools\push-background.ps1 -Path C:\Obrazy\gory.jpg
      -> available in Ustawienia > Tla > Wybierz tlo (tryb "Jedno tlo")

  .\tools\push-background.ps1 -Path C:\Obrazy\netflix.jpg -Package com.netflix.ninja
      -> shown whenever that app is focused (tryb "Dopasowane do aplikacji")
#>
param(
    [Parameter(Mandatory = $true)][string]$Path,
    [string]$Package,
    [string]$Ip
)
. (Join-Path $PSScriptRoot 'common.ps1')

if (-not (Test-Path $Path)) { Fail "Nie znaleziono pliku: $Path" }
$item = Get-Item $Path
$ext = $item.Extension.ToLower()
if (@('.jpg', '.jpeg', '.png', '.webp') -notcontains $ext) { Fail 'Obslugiwane formaty: JPG, PNG, WebP.' }

Connect-Device $Ip
$name = if ($Package) { "$Package$ext" } else { $item.Name -replace '[^A-Za-z0-9._-]', '_' }
$dir = "/sdcard/Android/data/$($script:Pkg)/files/backgrounds"

Step "Kopiuje jako $name"
Adb shell mkdir -p $dir | Out-Null
Adb push $item.FullName "$dir/$name" | Out-Host
Ok 'Gotowe. Jesli tlo aplikacji sie nie zmienilo: Ustawienia > Tla > Odswiez tla.'
