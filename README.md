# Lumen — launcher for the Mi Box S (MDZ-22-AB)

A home screen for Android TV 8.1/9 inspired by tvOS: photo backdrops, a favourites row, an app grid and a full settings panel. Kotlin, classic views, no root needed. Ready-made file: `dist\Lumen-1.0.0.apk` (about 1.8 MB).

The launcher's interface is in Polish. Menu names below are given as they appear in the app, with an English translation.

## Installing on the Mi Box

Run all commands in PowerShell, in the project folder.

1. **Enable debugging on the Mi Box.** Settings → Device Preferences → About → click "Build" 7 times. Then Device Preferences → Developer options → turn on "USB debugging".
2. **Find the IP address.** Settings → Network & Internet → your Wi-Fi network. The computer must be on the same network.
3. **Install** (on the TV, accept the debugging prompt and tick "Always allow"):

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\tools\install.ps1 -Ip 192.168.1.50
   ```

   Lumen shows up among your apps. The factory home screen is still the default, so nothing is at risk. Go through the test list below.
4. **Make it the home screen** once everything works:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\tools\make-default.ps1
   ```

   The script checks that Lumen runs, disables the factory launcher (`pm disable-user`, nothing is uninstalled), presses Home and verifies the result. If Lumen doesn't take over the Home button, the change rolls back by itself.

The scripts remember the device address, so you only pass `-Ip` once.

## Going back to the factory home screen

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\restore-stock.ps1
```

With `-Uninstall` the script also removes Lumen. The same by hand, without scripts:

```powershell
adb connect 192.168.1.50:5555
adb shell pm enable com.google.android.tvlauncher
```

Leave USB debugging on permanently — it's your way back. Last resort without a computer: a factory reset of the Mi Box restores everything.

## Remote control

| Button | Action |
|---|---|
| Arrows | Navigate; down from the favourites opens the app grid |
| OK | Launch the app |
| OK (hold) | App menu: add/remove from favourites, move, hide, info, uninstall |
| Back | From the grid to the favourites, then to the start of the row |
| Home | Back to the launcher; press again to go to the start of the favourites |

The launcher's settings are the gear tile (by default the last one in the favourites). Android TV's own settings are under Ustawienia → Ustawienia systemu (Settings → System settings).

## Backgrounds

By default the background is one of seven built-in mountain photos (Ustawienia → Tła → Wybierz tło, i.e. Settings → Backgrounds → Choose background). Authors and sources are in `CREDITS.md`. The "Dopasowane do aplikacji" (matched to the app) mode replaces the photo with a blurred image of the selected app.

You can add your own images from the computer:

```powershell
# background for the whole screen ("Jedno tło" / single background mode → Choose background)
powershell -ExecutionPolicy Bypass -File .\tools\push-background.ps1 -Path C:\Pictures\mountains.jpg

# background tied to an app ("Dopasowane do aplikacji" / matched to the app mode)
powershell -ExecutionPolicy Bypass -File .\tools\push-background.ps1 -Path C:\Pictures\netflix.jpg -Package com.netflix.ninja
```

## Tests to run on the Mi Box

These couldn't be checked without the device:

1. **Smoothness.** Move through the favourites and the grid a few times. If animations stutter: Ustawienia → Wydajność → Tryb wydajności (Settings → Performance → Performance mode), or Animacje i efekty (Animations and effects) → turn off "Przejścia tła" (background transitions).
2. **Holding OK** on the Mi Box remote opens the app menu.
3. **Banners of real apps** (Netflix, YouTube etc.) and their matched backgrounds look right.
4. **Returning from an app** with Back and Home.
5. **Rebooting the device** after `make-default.ps1`: Lumen starts by itself.
6. **Memory and frames**: `.\tools\diagnose.ps1` writes a report to `tools\state\`.

## Slimming down the system

Not done yet, on purpose. `.\tools\diagnose.ps1` only reads data (packages, services, memory) and writes a report. Based on it, a list of packages to disable will be made, each with a description of what it does, what disabling it changes and how to restore it. Nothing gets disabled based on guesses.

## Building

```powershell
.\gradlew.bat :app:assembleRelease
```

Requires JDK 21 and the Android SDK (paths in `gradle.properties` and `local.properties`).

The signing key is not in the repository. To sign with your own key, put it in `signing\lumen.keystore` and add the passwords to `local.properties`:

```properties
lumen.storePassword=...
lumen.keyAlias=lumen
lumen.keyPassword=...
```

Without them the build uses the default debug key. Keep your key safe – an update signed with a different key requires uninstalling the launcher first.

## Structure

| Folder | Contents |
|---|---|
| `app/.../home` | Home screen: tiles, dock, grid, backdrop, app menu |
| `app/.../settings` | Settings panel; pages are described as data in `Pages.kt` |
| `app/.../gfx` | Tiles, colour extraction, backdrop generation |
| `app/.../data` | App list, user layout, preferences |
| `app/.../sys` | Safe mode, home screen state, start on boot |
| `demo/` | Dummy apps used only for testing on the emulator |
| `tools/` | Scripts for installing, switching, restoring and diagnostics |
