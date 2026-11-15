package com.lumen.launcher.data

import android.content.Context
import android.content.pm.ApplicationInfo

/** Short descriptions for the hero area and the apps that fill the dock on first run. */
object KnownApps {
    private const val NETFLIX = "com.netflix.ninja"
    private const val YOUTUBE = "com.google.android.youtube.tv"
    private const val PRIME = "com.amazon.amazonvideo.livingroom"
    private const val DISNEY = "com.disney.disneyplus"
    private const val MAX = "com.wbd.stream"
    private const val APPLE_TV = "com.apple.atve.androidtv.appletv"
    private const val SPOTIFY = "com.spotify.tv.android"
    private const val KODI = "org.xbmc.kodi"
    private const val PLEX = "com.plexapp.android"

    val DOCK_DEFAULTS = listOf(NETFLIX, YOUTUBE, PRIME, DISNEY, MAX, APPLE_TV, SPOTIFY, KODI, PLEX)

    private val descriptions = mapOf(
        NETFLIX to "Filmy, seriale i produkcje oryginalne",
        YOUTUBE to "Filmy, muzyka i transmisje na żywo",
        "com.google.android.youtube.tvmusic" to "Muzyka i teledyski",
        "com.google.android.youtube.tvkids" to "Filmy dla dzieci",
        PRIME to "Filmy i seriale Prime Video",
        DISNEY to "Disney, Pixar, Marvel i Gwiezdne wojny",
        MAX to "Filmy i seriale Max",
        APPLE_TV to "Apple TV+ oraz filmy",
        SPOTIFY to "Muzyka i podcasty",
        KODI to "Centrum multimediów",
        PLEX to "Twoja biblioteka multimediów",
        "org.videolan.vlc" to "Odtwarzacz multimediów",
        "com.google.android.videos" to "Filmy i seriale z Google",
        "com.android.vending" to "Aplikacje i gry",
        "com.google.android.play.games" to "Gry i osiągnięcia",
        "tv.twitch.android.app" to "Transmisje na żywo",
        "com.android.tv.settings" to "Ustawienia systemu",
        AppRepository.SETTINGS_PKG to "Wygląd, tła, aplikacje i wydajność launchera",
    )

    private val cache = HashMap<String, String>()

    fun describe(context: Context, e: AppEntry): String {
        descriptions[e.pkg]?.let { return it }
        return cache.getOrPut(e.pkg) {
            val category = try {
                context.packageManager.getApplicationInfo(e.pkg, 0).category
            } catch (ex: Exception) {
                ApplicationInfo.CATEGORY_UNDEFINED
            }
            when (category) {
                ApplicationInfo.CATEGORY_GAME -> "Gra"
                ApplicationInfo.CATEGORY_AUDIO -> "Muzyka i dźwięk"
                ApplicationInfo.CATEGORY_VIDEO -> "Wideo"
                ApplicationInfo.CATEGORY_IMAGE -> "Zdjęcia"
                ApplicationInfo.CATEGORY_SOCIAL -> "Społeczność"
                ApplicationInfo.CATEGORY_NEWS -> "Wiadomości"
                ApplicationInfo.CATEGORY_MAPS -> "Mapy"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Narzędzia"
                else -> if (e.isSystem) "Aplikacja systemowa" else "Aplikacja"
            }
        }
    }
}
