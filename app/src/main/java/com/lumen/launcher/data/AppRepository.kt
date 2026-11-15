package com.lumen.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** One launchable app. [component] is null only for the launcher's own Settings tile. */
class AppEntry(
    val pkg: String,
    val component: ComponentName?,
    val label: String,
    val isTv: Boolean,
    val isSystem: Boolean,
    /** Changes whenever the package is updated; used to invalidate cached artwork. */
    val version: Long,
) {
    val isInternal: Boolean get() = component == null
}

/**
 * Source of truth for installed apps and for the user's arrangement of them
 * (dock, order, hidden). Loading happens off the main thread; listeners are
 * always called on the main thread.
 */
class AppRepository(private val app: Context, private val prefs: Prefs) {

    interface Listener {
        fun onAppsChanged()
    }

    private val io = Executors.newSingleThreadExecutor { Thread(it, "lumen-apps") }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<Listener>()

    @Volatile
    private var byPkg: Map<String, AppEntry> = emptyMap()

    var all: List<AppEntry> = emptyList()
        private set
    var loaded = false
        private set

    fun addListener(l: Listener) { if (l !in listeners) listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }
    fun find(pkg: String): AppEntry? = byPkg[pkg]

    fun reload() {
        io.execute {
            val list = try { query() } catch (t: Throwable) { null } ?: return@execute
            main.post { apply(list) }
        }
    }

    private fun query(): List<AppEntry> {
        val pm = app.packageManager
        val out = LinkedHashMap<String, AppEntry>()
        fun collect(category: String, tv: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName
                if (pkg == app.packageName || out.containsKey(pkg)) continue
                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                val system = (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val version = try { pm.getPackageInfo(pkg, 0).lastUpdateTime } catch (e: Exception) { 0L }
                out[pkg] = AppEntry(pkg, ComponentName(pkg, ai.name), label, tv, system, version)
            }
        }
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, true)
        if (prefs.showNonTvApps) collect(Intent.CATEGORY_LAUNCHER, false)
        out[SETTINGS_PKG] = AppEntry(SETTINGS_PKG, null, "Ustawienia", true, true, 1L)
        return out.values.toList()
    }

    private fun apply(list: List<AppEntry>) {
        val signature = list.joinToString("|") { "${it.pkg}:${it.version}:${it.label}" }
        val changed = signature != lastSignature || !loaded
        lastSignature = signature
        all = list
        byPkg = list.associateBy { it.pkg }

        // Unknown packages stay in the saved order on purpose: an app that is mid-update
        // disappears briefly and must come back in the same place. Entries are only
        // dropped when the system reports a full uninstall (see forget()).
        val order = prefs.order
        val known = order.toHashSet()
        val added = list.filter { it.pkg !in known }.sortedBy { it.label.lowercase() }.map { it.pkg }
        if (added.isNotEmpty()) prefs.order = order + added

        if (!prefs.firstRunDone) {
            val preferred = KnownApps.DOCK_DEFAULTS.filter { it in byPkg }
            val fill = list.filter { !it.isInternal && it.pkg !in preferred && it.isTv }.map { it.pkg }
            prefs.dock = (preferred + fill).take(4)
            prefs.firstRunDone = true
        }
        loaded = true
        if (changed || added.isNotEmpty()) notifyChanged()
    }

    private var lastSignature = ""

    private fun notifyChanged() {
        for (l in ArrayList(listeners)) l.onAppsChanged()
    }

    // Arrangement

    fun isInDock(pkg: String) = pkg in prefs.dock
    fun isHidden(pkg: String) = pkg in prefs.hidden

    fun dockApps(): List<AppEntry> {
        if (!prefs.dockEnabled) return emptyList()
        val hidden = prefs.hidden
        return prefs.dock.mapNotNull { byPkg[it] }.filter { it.pkg !in hidden && !it.isInternal }
    }

    fun gridApps(): List<AppEntry> {
        val hidden = prefs.hidden
        val inDock = dockApps().mapTo(HashSet()) { it.pkg }
        val base = prefs.order.mapNotNull { byPkg[it] }.filter { it.pkg !in hidden && it.pkg !in inDock && !it.isInternal }
        return if (prefs.sortMode == 1) base.sortedBy { it.label.lowercase() } else base
    }

    fun setDock(pkg: String, on: Boolean) {
        val dock = prefs.dock.toMutableList()
        if (on) {
            if (pkg !in dock) dock.add(pkg)
            if (pkg in prefs.hidden) prefs.hidden = prefs.hidden - pkg
        } else {
            dock.remove(pkg)
        }
        prefs.dock = dock
        notifyChanged()
    }

    fun setHidden(pkg: String, on: Boolean) {
        if (pkg == SETTINGS_PKG) return
        prefs.hidden = if (on) prefs.hidden + pkg else prefs.hidden - pkg
        notifyChanged()
    }

    fun moveDock(from: Int, to: Int) {
        val visible = dockApps().map { it.pkg }.toMutableList()
        if (from !in visible.indices || to !in visible.indices) return
        visible.add(to, visible.removeAt(from))
        prefs.dock = rewrite(prefs.dock, visible)
        notifyChanged()
    }

    fun moveGrid(from: Int, to: Int) {
        if (prefs.sortMode == 1) return
        val visible = gridApps().map { it.pkg }.toMutableList()
        if (from !in visible.indices || to !in visible.indices) return
        visible.add(to, visible.removeAt(from))
        prefs.order = rewrite(prefs.order, visible)
        notifyChanged()
    }

    /** Re-orders the [visible] subset inside [full] while leaving every other entry in its slot. */
    private fun rewrite(full: List<String>, visible: List<String>): List<String> {
        val set = visible.toHashSet()
        val next = visible.iterator()
        return full.map { if (it in set && next.hasNext()) next.next() else it }
    }

    /** Called when a package is fully uninstalled. */
    fun forget(pkg: String) {
        if (pkg in prefs.order) prefs.order = prefs.order - pkg
        if (pkg in prefs.dock) prefs.dock = prefs.dock - pkg
        if (pkg in prefs.hidden) prefs.hidden = prefs.hidden - pkg
    }

    fun onArrangementReset() {
        lastSignature = ""
        reload()
    }

    fun launchIntent(e: AppEntry): Intent? {
        val component = e.component ?: return null
        return Intent(Intent.ACTION_MAIN)
            .addCategory(if (e.isTv) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    companion object {
        const val SETTINGS_PKG = "lumen.settings"
        const val APPS_PKG = "lumen.apps"

        /** The two fixed buttons at the end of the dock. */
        val APPS_BUTTON = AppEntry(APPS_PKG, null, "Aplikacje", true, true, 1L)
        val SETTINGS_BUTTON = AppEntry(SETTINGS_PKG, null, "Ustawienia", true, true, 1L)
    }
}
