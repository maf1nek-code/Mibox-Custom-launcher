package com.lumen.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.lumen.launcher.data.AppRepository
import com.lumen.launcher.data.Prefs
import com.lumen.launcher.gfx.Backdrops
import com.lumen.launcher.gfx.TileLoader
import com.lumen.launcher.sys.CrashGuard

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        prefs.safeMode = CrashGuard.install(this)
        tiles = TileLoader(this)
        backdrops = Backdrops(this, prefs, tiles)
        repo = AppRepository(this, prefs)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val pkg = intent.data?.schemeSpecificPart
                if (intent.action == Intent.ACTION_PACKAGE_FULLY_REMOVED && pkg != null) repo.forget(pkg)
                repo.reload()
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        repo.reload()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The launcher stays alive behind every other app, so give memory back eagerly.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) backdrops.trim()
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) tiles.trim()
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) tiles.clear()
    }

    companion object {
        lateinit var prefs: Prefs
            private set
        lateinit var repo: AppRepository
            private set
        lateinit var tiles: TileLoader
            private set
        lateinit var backdrops: Backdrops
            private set
    }
}
