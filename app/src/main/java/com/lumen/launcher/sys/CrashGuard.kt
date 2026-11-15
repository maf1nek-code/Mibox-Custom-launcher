package com.lumen.launcher.sys

import android.content.Context
import android.util.Log
import java.io.File

/**
 * A home screen that crashes is restarted by the system straight away, so a crash on
 * startup would loop. After three crashes within a few minutes the launcher switches to
 * safe mode (no backgrounds, no animations) and stays there until the user turns it off.
 */
object CrashGuard {
    private const val FILE = "lumen_crash"
    private const val WINDOW_MS = 3 * 60 * 1000L
    private const val LIMIT = 3

    fun install(context: Context): Boolean {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val recent = sp.getString("times", "").orEmpty().split(',')
            .mapNotNull { it.toLongOrNull() }
            .filter { it <= now && now - it < WINDOW_MS }
        if (recent.size >= LIMIT) sp.edit().putBoolean("safe", true).remove("times").apply()

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val times = (recent + System.currentTimeMillis()).takeLast(LIMIT + 2)
                sp.edit().putString("times", times.joinToString(",")).commit()
                File(context.filesDir, "last_crash.txt").writeText(Log.getStackTraceString(error))
            } catch (ignored: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
        return sp.getBoolean("safe", false)
    }

    /** Called once the home screen has been running normally for a while. */
    fun markStable(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove("times").apply()
    }

    fun leaveSafeMode(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun lastCrash(context: Context): File? =
        File(context.filesDir, "last_crash.txt").takeIf { it.isFile }
}
