package com.lumen.launcher.data

import android.content.Context

/**
 * All user configuration. Every write bumps [revision], which screens compare
 * against the value they last rendered to know when to re-apply settings.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("lumen_prefs", Context.MODE_PRIVATE)

    @Volatile
    var revision = 0
        private set

    /** Set at startup by CrashGuard after repeated crashes; forces the lightest configuration. */
    @Volatile
    var safeMode = false

    private fun b(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun i(key: String, def: Int) = sp.getInt(key, def)
    private fun setB(key: String, v: Boolean) { sp.edit().putBoolean(key, v).apply(); revision++ }
    private fun setI(key: String, v: Int) { sp.edit().putInt(key, v).apply(); revision++ }
    private fun list(key: String): List<String> =
        sp.getString(key, "").orEmpty().split('\n').filter { it.isNotEmpty() }
    private fun setList(key: String, v: Collection<String>) {
        sp.edit().putString(key, v.joinToString("\n")).apply(); revision++
    }

    // Appearance
    var theme: Int get() = i("theme", 0); set(v) = setI("theme", v)            // 0 graphite, 1 onyx, 2 midnight
    var accent: Int get() = i("accent", 0); set(v) = setI("accent", v)          // index into ACCENTS
    var tileSize: Int get() = i("tile_size", 1); set(v) = setI("tile_size", v)  // 0 compact, 1 standard, 2 large
    var labels: Int get() = i("labels", 0); set(v) = setI("labels", v)          // 0 focused only, 1 always, 2 never
    var showClock: Boolean get() = b("clock", true); set(v) = setB("clock", v)
    var clock24: Boolean get() = b("clock_24", true); set(v) = setB("clock_24", v)
    var showDate: Boolean get() = b("date_v2", true); set(v) = setB("date_v2", v)
    var heroTitle: Boolean get() = b("hero_title", true); set(v) = setB("hero_title", v)
    var heroDescription: Boolean get() = b("hero_desc", true); set(v) = setB("hero_desc", v)

    // Backgrounds
    var bgMode: Int get() = i("bg_mode", 1); set(v) = setI("bg_mode", v)        // 0 per app, 1 single, 2 none
    var bgSingle: String
        get() = sp.getString("bg_single", "photo:ridge") ?: "photo:ridge"
        set(v) { sp.edit().putString("bg_single", v).apply(); revision++ }
    var bgDim: Int get() = i("bg_dim", 1); set(v) = setI("bg_dim", v)           // 0 light, 1 medium, 2 strong

    // Dock and apps
    var dockEnabled: Boolean get() = b("dock_enabled", true); set(v) = setB("dock_enabled", v)
    var dockPlate: Boolean get() = b("dock_glass", true); set(v) = setB("dock_glass", v)
    var sortMode: Int get() = i("sort_mode", 0); set(v) = setI("sort_mode", v)  // 0 custom, 1 alphabetical
    var showNonTvApps: Boolean get() = b("non_tv_apps", true); set(v) = setB("non_tv_apps", v)
    var dock: List<String> get() = list("dock"); set(v) = setList("dock", v)
    var order: List<String> get() = list("order"); set(v) = setList("order", v)
    var hidden: Set<String> get() = list("hidden").toSet(); set(v) = setList("hidden", v)
    var firstRunDone: Boolean get() = b("first_run_done", false); set(v) = setB("first_run_done", v)

    // Animations and effects
    var animLevel: Int get() = i("anim_level", 0); set(v) = setI("anim_level", v) // 0 full, 1 reduced, 2 off
    var bgTransitions: Boolean get() = b("bg_transitions", true); set(v) = setB("bg_transitions", v)
    var depth: Boolean get() = b("depth_v2", true); set(v) = setB("depth_v2", v)
    var shadows: Boolean get() = b("shadows", true); set(v) = setB("shadows", v)

    // Performance
    var perfMode: Boolean get() = b("perf_mode", false); set(v) = setB("perf_mode", v)
    var bgQuality: Int get() = i("bg_quality", 1); set(v) = setI("bg_quality", v) // 0 low, 1 standard, 2 high

    // Startup
    var startOnBoot: Boolean get() = b("start_on_boot", true); set(v) = setB("start_on_boot", v)

    // Effective values: what the UI should actually use once performance and safe mode are applied.
    val effAnimLevel: Int get() = when {
        safeMode -> 2
        perfMode -> maxOf(animLevel, 1)
        else -> animLevel
    }
    val effBgMode: Int get() = if (safeMode) 2 else bgMode
    val effBgQuality: Int get() = if (perfMode) 0 else bgQuality
    val fxShadows: Boolean get() = shadows && !perfMode && !safeMode
    val fxDepth: Boolean get() = depth && !perfMode && !safeMode && animLevel == 0
    val fxBgTransitions: Boolean get() = bgTransitions && !safeMode && effAnimLevel != 2

    val accentColor: Int get() = ACCENTS[accent.coerceIn(0, ACCENTS.size - 1)]

    fun resetAppearance() {
        val e = sp.edit()
        for (k in APPEARANCE_KEYS) e.remove(k)
        e.apply(); revision++
    }

    fun resetLayout() {
        sp.edit().remove("dock").remove("order").remove("hidden").remove("first_run_done")
            .remove("dock_enabled").remove("sort_mode").apply()
        revision++
    }

    fun resetAll() {
        sp.edit().clear().apply(); revision++
    }

    companion object {
        val ACCENTS = intArrayOf(0xFFFFFFFF.toInt(), 0xFF64B5FF.toInt(), 0xFFFFB340.toInt(), 0xFF5EDDA5.toInt())
        val ACCENT_NAMES = listOf("Biały", "Błękitny", "Bursztynowy", "Miętowy")
        private val APPEARANCE_KEYS = listOf(
            "theme", "accent", "tile_size", "labels", "clock", "clock_24", "date_v2", "hero_title", "hero_desc",
            "bg_mode", "bg_single", "bg_dim", "dock_glass",
            "anim_level", "bg_transitions", "depth_v2", "shadows", "perf_mode", "bg_quality"
        )
    }
}
