package com.lumen.launcher.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import com.lumen.launcher.App
import com.lumen.launcher.BuildConfig
import com.lumen.launcher.R
import com.lumen.launcher.data.AppEntry
import com.lumen.launcher.data.AppRepository
import com.lumen.launcher.data.Prefs
import com.lumen.launcher.sys.CrashGuard
import com.lumen.launcher.sys.HomeStatus
import java.text.DateFormat
import java.util.Date

/** Every settings page, declared as data. */
class Pages(private val a: SettingsActivity) {
    private val p get() = App.prefs
    private val repo get() = App.repo
    private val backdrops get() = App.backdrops

    fun root() = Page("Ustawienia", R.drawable.ic_gear) {
        listOf(
            Nav("Wygląd", "Motyw, kolor akcentu, rozmiar kafli, zegar i podpisy aplikacji.") { appearance() },
            Nav("Tła", "Kinowe tła dopasowane do aplikacji, gotowe motywy albo własne obrazy.") { backgrounds() },
            Nav("Aplikacje", "Kolejność, sortowanie i ukrywanie aplikacji na ekranie głównym.") { apps() },
            Nav("Dock", "Ulubione aplikacje na szklanym panelu na środku ekranu.") { dock() },
            Nav("Animacje i efekty", "Płynność ruchu, przejścia tła, cienie i efekt głębi.") { effects() },
            Nav("Wydajność", "Tryb wydajności, jakość teł i zużycie pamięci.") { performance() },
            Nav("Uruchamianie", "Domyślny ekran główny, start po włączeniu urządzenia i tryb awaryjny.") { startup() },
            Action("Ustawienia systemu", "Sieć, dźwięk, konta, piloty i pozostałe ustawienia Android TV.") { a.openSystemSettings() },
            Nav("Informacje", "Wersja launchera i dane urządzenia.") { about() },
            Nav("Reset", "Przywracanie ustawień domyślnych.") { reset() },
        )
    }

    private fun appearance() = Page("Wygląd", R.drawable.ic_appearance) {
        listOf(
            Choice(
                "Motyw", "Kolor powierzchni w ustawieniach, menu i na ekranie bez tła.",
                listOf("Grafit", "Onyks", "Północ"),
                listOf("Neutralna, ciemna szarość.", "Czysta czerń o najwyższym kontraście.", "Głęboki granat."),
                { p.theme }, { p.theme = it }
            ),
            Choice(
                "Kolor akcentu", "Kolor włączonych opcji i zaznaczonych wyborów.",
                Prefs.ACCENT_NAMES, emptyList(), { p.accent }, { p.accent = it }
            ),
            Choice(
                "Rozmiar kafli", "Liczba aplikacji mieszczących się w jednym rzędzie.",
                listOf("Kompaktowe", "Standardowe", "Duże"),
                listOf("Sześć aplikacji w rzędzie.", "Pięć aplikacji w rzędzie.", "Cztery aplikacje w rzędzie."),
                { p.tileSize }, { p.tileSize = it }
            ),
            Choice(
                "Podpisy aplikacji", "Nazwy pod kaflami w siatce aplikacji.",
                listOf("Tylko zaznaczona", "Zawsze", "Nigdy"),
                listOf("Nazwa pojawia się pod zaznaczonym kaflem.", "Nazwy widoczne pod wszystkimi kaflami.", "Same kafle, bez nazw."),
                { p.labels }, { p.labels = it }
            ),
            Toggle("Nazwa pod dockiem", "Nazwa zaznaczonej aplikacji pod dockiem.", { p.heroTitle }, { p.heroTitle = it }),
            Toggle("Zegar", "Duży zegar na środku ekranu głównego.", { p.showClock }, { p.showClock = it }),
            Toggle("Zegar 24-godzinny", "Wyłącz, aby używać formatu 12-godzinnego.", { p.clock24 }, { p.clock24 = it }),
            Toggle("Data", "Dzień tygodnia i data nad zegarem.", { p.showDate }, { p.showDate = it }),
        )
    }

    private fun backgrounds() = Page("Tła", R.drawable.ic_image) {
        buildList {
            add(Choice(
                "Tryb tła", "Skąd launcher bierze tło ekranu głównego.",
                listOf("Dopasowane do aplikacji", "Jedno tło", "Bez tła"),
                listOf(
                    "Tło przejmuje kolory zaznaczonej aplikacji: jej grafika jest rozmywana i przyciemniana. Własny obraz przypisany do aplikacji ma pierwszeństwo.",
                    "Zawsze to samo tło: wbudowane zdjęcie, własny obraz albo jednolity ton.",
                    "Gładka powierzchnia w kolorze motywu. Najlżejsza opcja."
                ),
                { p.bgMode }, { p.bgMode = it }
            ))
            if (p.bgMode == 1) {
                add(Nav("Wybierz tło", "Wbudowane zdjęcia, Twoje obrazy i jednolite tony.", { singleName() }) { pickBackground() })
            }
            add(Choice(
                "Przyciemnienie", "Jak mocno tło jest przygaszone, aby tekst pozostał czytelny.",
                listOf("Lekkie", "Średnie", "Mocne"), emptyList(), { p.bgDim }, { p.bgDim = it }
            ))
            add(Nav("Własne obrazy", "Jak dodać swoje tła.", { "${backdrops.customFiles().size} plików" }) { customImages() })
            add(Action("Odśwież tła", "Usuwa zapisane tła aplikacji. Zostaną utworzone ponownie przy następnym użyciu.") {
                backdrops.clearCache()
                p.bgDim = p.bgDim
                a.toast("Tła zostaną utworzone ponownie")
            })
        }
    }

    private fun singleName(): String {
        val value = p.bgSingle
        if (value.startsWith("file:")) return value.removePrefix("file:")
        if (value.startsWith("preset:")) {
            backdrops.presets.firstOrNull { it.id == value.removePrefix("preset:") }?.let { return it.name }
        }
        return (backdrops.photos.firstOrNull { it.id == value.removePrefix("photo:") } ?: backdrops.photos[0]).name
    }

    private fun pickBackground() = Page("Wybierz tło", R.drawable.ic_image) {
        buildList {
            for (photo in backdrops.photos) {
                val value = "photo:${photo.id}"
                add(Radio(photo.name, "Zdjęcie wbudowane w launcher.", { p.bgSingle == value }, { p.bgSingle = value },
                    backdrops.photoSpec(photo.id, 0)))
            }
            for (file in backdrops.customFiles()) {
                val value = "file:${file.name}"
                add(Radio(file.name, "Twój obraz z folderu teł.", { p.bgSingle == value }, { p.bgSingle = value },
                    backdrops.fileSpec(file, 0)))
            }
            for (preset in backdrops.presets) {
                val value = "preset:${preset.id}"
                add(Radio(preset.name, "Jednolity, ciemny ton.", { p.bgSingle == value }, { p.bgSingle = value },
                    backdrops.presetSpec(preset.id, 0)))
            }
        }
    }

    private fun customImages() = Page("Własne obrazy", R.drawable.ic_image) {
        val folder = backdrops.folder()?.path ?: "niedostępny"
        listOf(
            Info("Folder teł", "Skopiuj obrazy JPG, PNG lub WebP do folderu:\n$folder") { "${backdrops.customFiles().size} plików" },
            Info("Tło dla jednej aplikacji", "Nazwij plik identyfikatorem pakietu, na przykład com.netflix.ninja.jpg. Identyfikator znajdziesz w Aplikacje → Zarządzaj aplikacjami."),
            Info("Tło dla całego ekranu", "Plik o dowolnej nazwie pojawi się w Tła → Wybierz tło po wybraniu trybu „Jedno tło”."),
            Info("Kopiowanie z komputera", "Uruchom skrypt tools\\push-background.ps1 z folderu projektu i podaj ścieżkę do obrazu."),
        )
    }

    private fun apps() = Page("Aplikacje", R.drawable.ic_apps) {
        buildList {
            add(Nav("Zarządzaj aplikacjami", "Ulubione, ukrywanie i odinstalowywanie.", { "${repo.all.count { !it.isInternal }}" }) { manageApps() })
            add(Choice(
                "Sortowanie", "Kolejność aplikacji w siatce.",
                listOf("Własna kolejność", "Alfabetycznie"),
                listOf("Układasz aplikacje samodzielnie.", "Aplikacje ułożone według nazwy."),
                { p.sortMode }, { p.sortMode = it; repo.onArrangementReset() }
            ))
            if (p.sortMode == 0) {
                add(Nav("Kolejność aplikacji", "Wybierz aplikację przyciskiem OK, przesuń ją strzałkami i zatwierdź przyciskiem OK.") { reorderGrid() })
            }
            add(Toggle(
                "Aplikacje bez wersji TV", "Pokazuje także aplikacje zainstalowane z pliku, które nie mają interfejsu dla telewizora.",
                { p.showNonTvApps }, { p.showNonTvApps = it; repo.reload() }
            ))
        }
    }

    private fun manageApps() = Page("Zarządzaj aplikacjami", R.drawable.ic_apps) {
        repo.all.filter { !it.isInternal }.sortedBy { it.label.lowercase() }.map { e ->
            Nav(e.label, e.pkg, {
                when {
                    repo.isHidden(e.pkg) -> "Ukryta"
                    repo.isInDock(e.pkg) -> "Ulubiona"
                    else -> ""
                }
            }) { appPage(e) }
        }
    }

    private fun appPage(e: AppEntry) = Page(e.label, R.drawable.ic_apps) {
        buildList {
            add(Toggle("Ulubiona", "Pokazuje aplikację w pierwszym rzędzie ekranu głównego.", { repo.isInDock(e.pkg) }, { repo.setDock(e.pkg, it) }))
            add(Toggle("Ukryta", "Ukryta aplikacja znika z ekranu głównego, ale pozostaje zainstalowana.", { repo.isHidden(e.pkg) }, {
                repo.setHidden(e.pkg, it)
                if (it && repo.isInDock(e.pkg)) repo.setDock(e.pkg, false)
            }))
            add(Info("Własne tło", "Aby przypisać obraz do tej aplikacji, skopiuj do folderu teł plik o nazwie:\n${e.pkg}.jpg") {
                if (backdrops.appFile(e.pkg) != null) "Ustawione" else "Brak"
            })
            add(Action("Informacje systemowe", "Pamięć, uprawnienia i wymuszenie zatrzymania w ustawieniach Android TV.") { a.openAppDetails(e.pkg) })
            if (!e.isSystem) {
                add(Action("Odinstaluj", "Usuwa aplikację z urządzenia.", danger = true) { a.uninstall(e.pkg) })
            }
        }
    }

    private fun reorderGrid() = Page("Kolejność aplikacji", R.drawable.ic_apps, reorder = { from, to -> repo.moveGrid(from, to) }) {
        repo.gridApps().map { Movable(it.label, "OK podnosi i odkłada aplikację. Strzałki w górę i w dół zmieniają jej miejsce.") }
    }

    private fun dock() = Page("Dock", R.drawable.ic_dock) {
        buildList {
            add(Toggle("Ulubione w docku", "Po wyłączeniu w docku zostają tylko przyciski Aplikacje i Ustawienia.", { p.dockEnabled }, { p.dockEnabled = it }))
            if (p.dockEnabled) {
                add(Toggle("Szklany panel", "Półprzezroczysty panel, na którym leżą aplikacje w docku.", { p.dockPlate }, { p.dockPlate = it }))
                add(Nav("Wybierz ulubione", "Aplikacje pokazywane w pierwszym rzędzie.", { "${repo.dockApps().size}" }) { dockApps() })
                add(Nav("Kolejność ulubionych", "Wybierz aplikację przyciskiem OK, przesuń ją strzałkami i zatwierdź przyciskiem OK.") { reorderDock() })
            }
        }
    }

    private fun dockApps() = Page("Wybierz ulubione", R.drawable.ic_dock) {
        repo.all.filter { !repo.isHidden(it.pkg) && !it.isInternal }.sortedBy { it.label.lowercase() }.map { e ->
            Toggle(e.label, "Włącz, aby dodać aplikację do ulubionych.", { repo.isInDock(e.pkg) }, { repo.setDock(e.pkg, it) })
        }
    }

    private fun reorderDock() = Page("Kolejność ulubionych", R.drawable.ic_dock, reorder = { from, to -> repo.moveDock(from, to) }) {
        repo.dockApps().map { Movable(it.label, "OK podnosi i odkłada aplikację. Strzałki w górę i w dół zmieniają jej miejsce.") }
    }

    private fun effects() = Page("Animacje i efekty", R.drawable.ic_motion) {
        listOf(
            Choice(
                "Animacje", "Ilość ruchu w całym interfejsie.",
                listOf("Pełne", "Ograniczone", "Wyłączone"),
                listOf("Wszystkie animacje i przejścia.", "Krótsze animacje i mniejsze powiększenie kafli.", "Natychmiastowe zmiany, bez ruchu."),
                { p.animLevel }, { p.animLevel = it }
            ),
            Toggle("Przejścia tła", "Płynne przenikanie przy zmianie tła.", { p.bgTransitions }, { p.bgTransitions = it }),
            Toggle("Ruch tła", "Tło powoli przesuwa się w stronę przeciwną do zaznaczenia, co daje wrażenie głębi.", { p.depth }, { p.depth = it }),
            Toggle("Cień zaznaczenia", "Miękki cień pod zaznaczonym kaflem.", { p.shadows }, { p.shadows = it }),
        )
    }

    private fun performance() = Page("Wydajność", R.drawable.ic_speed) {
        listOf(
            Toggle(
                "Tryb wydajności", "Wyłącza cień i efekt głębi, skraca animacje i obniża jakość teł. Włącz, jeśli interfejs się zacina.",
                { p.perfMode }, { p.perfMode = it }
            ),
            Choice(
                "Jakość teł", "Rozdzielczość teł. Niższa oznacza mniej pamięci i szybsze przełączanie.",
                listOf("Niska", "Standardowa", "Wysoka"),
                listOf("Najmniejsze zużycie pamięci.", "Zalecana dla Mi Box S.", "Najostrzejsze własne obrazy, największe zużycie pamięci."),
                { p.bgQuality }, { p.bgQuality = it }
            ),
            Info("Pamięć launchera", "Pamięć RAM zajmowana w tej chwili przez launcher.") { "${Debug.getPss() / 1024} MB" },
            Info("Wolna pamięć urządzenia", "Pamięć RAM dostępna dla aplikacji.") { memory() },
            Action("Wyczyść pamięć podręczną", "Zwalnia grafiki kafli i teł przechowywane w pamięci.") {
                App.tiles.clear()
                backdrops.clearCache()
                a.refresh()
                a.toast("Pamięć podręczna wyczyszczona")
            },
        )
    }

    private fun memory(): String {
        val info = ActivityManager.MemoryInfo()
        (a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return "${info.availMem / 1048576} z ${info.totalMem / 1048576} MB"
    }

    private fun startup() = Page("Uruchamianie", R.drawable.ic_power) {
        buildList {
            add(Info("Ekran główny", "Aplikacja, która otwiera się po naciśnięciu przycisku Home.") { HomeStatus.currentHomeLabel(a) })
            add(Nav("Jak ustawić Lumen jako ekran główny", "Android TV nie pozwala zmienić ekranu głównego z poziomu urządzenia. Robi się to raz, z komputera.") { howTo() })
            add(Toggle(
                "Uruchamiaj po starcie urządzenia", "Otwiera Lumen po włączeniu urządzenia, dopóki fabryczny ekran główny pozostaje domyślny.",
                { p.startOnBoot }, { p.startOnBoot = it }
            ))
            add(Action("Otwórz fabryczny ekran główny", "Przechodzi do oryginalnego ekranu Android TV, jeśli nie został wyłączony.") { a.openStockHome() })
            if (p.safeMode) {
                add(Action("Wyłącz tryb awaryjny", "Tryb awaryjny włączył się po serii błędów i wyłączył tła oraz animacje. Zmiana zadziała po ponownym uruchomieniu launchera.") {
                    CrashGuard.leaveSafeMode(a)
                    a.toast("Tryb awaryjny zostanie wyłączony po ponownym uruchomieniu")
                })
            }
            val crash = CrashGuard.lastCrash(a)
            add(Info("Ostatni błąd", crash?.let { it.readLines().take(4).joinToString("\n") } ?: "Launcher nie zapisał żadnego błędu.") {
                crash?.let { DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it.lastModified())) } ?: "Brak"
            })
        }
    }

    private fun howTo() = Page("Lumen jako ekran główny", R.drawable.ic_power) {
        listOf(
            Info("1. Włącz debugowanie", "Ustawienia systemu → Preferencje urządzenia → Informacje. Kliknij siedem razy pozycję „Kompilacja”, a potem w Opcjach programisty włącz debugowanie USB."),
            Info("2. Zainstaluj z komputera", "W folderze projektu uruchom tools\\install.ps1 i podaj adres IP urządzenia. Na ekranie telewizora potwierdź połączenie."),
            Info("3. Przełącz ekran główny", "Uruchom tools\\make-default.ps1. Skrypt najpierw sprawdza, czy Lumen działa, i dopiero wtedy wyłącza fabryczny ekran główny."),
            Info("Powrót do fabrycznego", "tools\\restore-stock.ps1 w każdej chwili przywraca oryginalny ekran główny. Niczego nie usuwa."),
        )
    }

    private fun about() = Page("Informacje", R.drawable.ic_info) {
        val metrics = a.resources.displayMetrics
        listOf(
            Info("Lumen", "Launcher dla Android TV.") { BuildConfig.VERSION_NAME },
            Info("Urządzenie", "Model zgłaszany przez system.") { "${Build.MANUFACTURER} ${Build.MODEL}" },
            Info("System", "Wersja Androida.") { "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" },
            Info("Ekran", "Rozdzielczość, w której rysowany jest interfejs.") { "${metrics.widthPixels} × ${metrics.heightPixels}" },
            Info("Pamięć", "Wolna i całkowita pamięć RAM.") { memory() },
            Info("Aplikacje", "Liczba aplikacji widocznych dla launchera.") { "${repo.all.count { !it.isInternal }}" },
        )
    }

    private fun reset() = Page("Reset", R.drawable.ic_reset) {
        listOf(
            Nav("Przywróć wygląd", "Przywraca domyślny motyw, tła, animacje i ustawienia wydajności. Układ aplikacji pozostaje bez zmian.") {
                confirm("Przywróć wygląd", "Przywróć") { p.resetAppearance() }
            },
            Nav("Zresetuj układ aplikacji", "Przywraca domyślny dock i kolejność oraz pokazuje ukryte aplikacje.") {
                confirm("Zresetuj układ aplikacji", "Zresetuj") { p.resetLayout(); repo.onArrangementReset() }
            },
            Nav("Przywróć wszystko", "Przywraca wszystkie ustawienia launchera do stanu po instalacji.") {
                confirm("Przywróć wszystko", "Przywróć wszystko") { p.resetAll(); repo.onArrangementReset() }
            },
        )
    }

    private fun confirm(title: String, label: String, action: () -> Unit) = Page(title, R.drawable.ic_reset) {
        listOf(
            Action("Anuluj", "Wraca bez wprowadzania zmian.") { a.back() },
            Action(label, "Tej operacji nie można cofnąć.", danger = true) {
                action()
                a.back()
                a.toast("Gotowe")
            },
        )
    }
}
