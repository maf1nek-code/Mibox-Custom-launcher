package com.lumen.launcher.sys

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Which app currently answers the Home button, and where the factory home screen is. */
object HomeStatus {
    /** Home activities that must never be treated as a "real" launcher: the system fallback and setup screens. */
    private val NOT_LAUNCHERS = setOf("com.android.tv.settings", "com.android.settings", "com.google.android.tungsten.setupwraith")

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun currentHomePackage(context: Context): String? =
        context.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName

    fun isDefault(context: Context) = currentHomePackage(context) == context.packageName

    fun currentHomeLabel(context: Context): String {
        val pm = context.packageManager
        val info = pm.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return "Nieznany"
        if (info.packageName == context.packageName) return "Lumen"
        if (info.packageName == "android") return "Wybór przy naciśnięciu Home"
        return info.applicationInfo.loadLabel(pm).toString()
    }

    /** The other enabled home screen (normally the factory Android TV one), if there is one. */
    fun stockHome(context: Context): ComponentName? =
        context.packageManager.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo }
            .firstOrNull { it.packageName != context.packageName && it.packageName !in NOT_LAUNCHERS }
            ?.let { ComponentName(it.packageName, it.name) }
}
