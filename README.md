# Lumen — launcher dla Mi Box S (MDZ-22-AB)

Ekran główny dla Android TV 8.1/9 inspirowany tvOS: fotograficzne tło, rząd ulubionych, siatka aplikacji i pełny panel ustawień. Kotlin, klasyczne widoki, bez roota. Gotowy plik: `dist\Lumen-1.0.0.apk` (ok. 1,8 MB).

## Instalacja na Mi Boxie

Wszystkie polecenia uruchamiasz w PowerShellu, w folderze projektu.

1. **Włącz debugowanie na Mi Boxie.** Ustawienia → Preferencje urządzenia → Informacje → kliknij 7 razy „Kompilacja”. Potem Preferencje urządzenia → Opcje programisty → włącz „Debugowanie USB”.
2. **Sprawdź adres IP.** Ustawienia → Sieć i internet → Twoja sieć Wi-Fi. Komputer musi być w tej samej sieci.
3. **Zainstaluj** (na telewizorze potwierdź pytanie o debugowanie, zaznacz „Zawsze zezwalaj”):

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\tools\install.ps1 -Ip 192.168.1.50
   ```

   Lumen pojawi się wśród aplikacji. Fabryczny ekran główny nadal jest domyślny, więc niczym nie ryzykujesz. Przejdź listę testów poniżej.
4. **Ustaw jako ekran główny**, gdy wszystko działa:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\tools\make-default.ps1
   ```

   Skrypt sprawdza, czy Lumen działa, wyłącza fabryczny launcher (`pm disable-user`, nic nie jest usuwane), naciska Home i weryfikuje wynik. Jeśli Lumen nie przejmie przycisku Home, zmiana cofa się sama.

Kolejne skrypty pamiętają adres urządzenia, więc `-Ip` podajesz tylko raz.

## Powrót do fabrycznego ekranu

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\restore-stock.ps1
```

Z dopiskiem `-Uninstall` skrypt dodatkowo usuwa Lumen. To samo ręcznie, bez skryptów:

```powershell
adb connect 192.168.1.50:5555
adb shell pm enable com.google.android.tvlauncher
```

Zostaw debugowanie USB włączone na stałe — to droga awaryjna. Ostateczność bez komputera: reset fabryczny Mi Boxa przywraca wszystko.

## Obsługa pilotem

| Przycisk | Działanie |
|---|---|
| Strzałki | Nawigacja; w dół z ulubionych otwiera siatkę aplikacji |
| OK | Uruchamia aplikację |
| OK przytrzymane | Menu aplikacji: dodaj/usuń z ulubionych, przenieś, ukryj, informacje, odinstaluj |
| Wstecz | Z siatki do ulubionych, potem na początek rzędu |
| Home | Powrót do launchera; drugi raz — na początek ulubionych |

Ustawienia launchera to kafel z zębatką (domyślnie ostatni w ulubionych). Ustawienia Android TV są w Ustawienia → Ustawienia systemu.

## Tła

Domyślnie tłem jest jedno z siedmiu wbudowanych zdjęć górskich (Ustawienia → Tła → Wybierz tło). Autorzy i źródła są w `CREDITS.md`. Tryb „Dopasowane do aplikacji” zastępuje zdjęcie rozmytą grafiką zaznaczonej aplikacji.

Własne obrazy dodajesz z komputera:

```powershell
# tło całego ekranu (tryb „Jedno tło” → Wybierz tło)
powershell -ExecutionPolicy Bypass -File .\tools\push-background.ps1 -Path C:\Obrazy\gory.jpg

# tło przypisane do aplikacji (tryb „Dopasowane do aplikacji”)
powershell -ExecutionPolicy Bypass -File .\tools\push-background.ps1 -Path C:\Obrazy\netflix.jpg -Package com.netflix.ninja
```

## Testy do wykonania na Mi Boxie

Tego nie dało się sprawdzić bez urządzenia:

1. **Płynność.** Przejdź kilka razy po ulubionych i siatce. Jeśli animacje szarpią: Ustawienia → Wydajność → Tryb wydajności, albo Animacje i efekty → wyłącz „Przejścia tła”.
2. **Przytrzymanie OK** na pilocie Mi Boxa otwiera menu aplikacji.
3. **Banery prawdziwych aplikacji** (Netflix, YouTube itd.) i dopasowane do nich tła wyglądają dobrze.
4. **Powrót z aplikacji** przyciskiem Wstecz i Home.
5. **Restart urządzenia** po `make-default.ps1`: Lumen startuje sam.
6. **Pamięć i klatki**: `.\tools\diagnose.ps1` zapisuje raport do `tools\state\`.

## Odchudzanie systemu

Jeszcze niewykonane, celowo. `.\tools\diagnose.ps1` tylko odczytuje dane (pakiety, usługi, pamięć) i zapisuje raport. Na jego podstawie powstanie lista pakietów do wyłączenia, każdy z opisem funkcji, skutków i sposobu przywrócenia. Nic nie jest wyłączane na podstawie zgadywania.

## Budowanie

```powershell
.\gradlew.bat :app:assembleRelease
```

Wymaga JDK 21 i Android SDK (ścieżki w `gradle.properties` i `local.properties`). Klucz podpisu jest w `signing\` — zachowaj go, bo aktualizacja podpisana innym kluczem wymaga odinstalowania launchera.

## Struktura

| Folder | Zawartość |
|---|---|
| `app/.../home` | Ekran główny: kafle, dock, siatka, tło, menu aplikacji |
| `app/.../settings` | Panel ustawień; strony opisane jako dane w `Pages.kt` |
| `app/.../gfx` | Kafle, wyciąganie kolorów, generowanie teł |
| `app/.../data` | Lista aplikacji, układ użytkownika, preferencje |
| `app/.../sys` | Tryb awaryjny, stan ekranu głównego, start po włączeniu |
| `demo/` | Atrapy aplikacji używane tylko do testów na emulatorze |
| `tools/` | Skrypty instalacji, przełączania, przywracania i diagnostyki |
