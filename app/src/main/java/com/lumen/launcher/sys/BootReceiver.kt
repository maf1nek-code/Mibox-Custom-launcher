package com.lumen.launcher.sys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lumen.launcher.App
import com.lumen.launcher.home.HomeActivity

/**
 * When Lumen is the default home screen the system starts it by itself. This receiver only
 * covers the period before that switch, when the factory home screen is still the default.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!App.prefs.startOnBoot || HomeStatus.isDefault(context)) return
        try {
            context.startActivity(
                Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (ignored: Exception) {
        }
    }
}
