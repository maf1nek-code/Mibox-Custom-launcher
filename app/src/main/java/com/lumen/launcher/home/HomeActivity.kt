package com.lumen.launcher.home

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.lumen.launcher.App
import com.lumen.launcher.R
import com.lumen.launcher.data.AppEntry
import com.lumen.launcher.data.AppRepository
import com.lumen.launcher.data.KnownApps
import com.lumen.launcher.settings.SettingsActivity
import com.lumen.launcher.sys.CrashGuard
import com.lumen.launcher.ui.Kick
import com.lumen.launcher.ui.Motion
import com.lumen.launcher.ui.Spring
import com.lumen.launcher.ui.dp
import kotlin.math.abs

/**
 * The home screen. Two states share one layout:
 *  - hero: large title over the backdrop, the dock below it, the first row of apps peeking in;
 *  - grid: the whole content layer slides up and the app grid takes the screen.
 * Moving between them is a single translation of [content], so it stays cheap.
 */
class HomeActivity : Activity(), AppRepository.Listener, TileAdapter.Host {

    private val prefs get() = App.prefs
    private val repo get() = App.repo
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var root: FrameLayout
    private lateinit var backdrop: BackdropView
    private lateinit var content: FrameLayout
    private lateinit var hero: View
    private lateinit var dockLabel: TextView
    private lateinit var dockPlate: View
    private lateinit var dockClip: FrameLayout
    private lateinit var dock: FocusRecyclerView
    private lateinit var gridClip: FrameLayout
    private lateinit var grid: FocusRecyclerView
    private lateinit var clock: TextClock
    private lateinit var date: TextClock
    private lateinit var notice: View
    private lateinit var moveHint: View
    private lateinit var sheet: ContextSheet

    private lateinit var dockAdapter: TileAdapter
    private lateinit var gridAdapter: TileAdapter
    private lateinit var gridLayout: GridRowsLayoutManager

    override var tileW = 0
        private set
    override var tileH = 0
        private set
    override var tileStyle = TileStyle()
        private set
    override var movingPkg: String? = null
        private set
    override var dockW = 0
        private set
    override var dockH = 0
        private set

    private var cols = 5
    private var seenRevision = -1
    private var dockVisible = false
    private var laidOut = false
    private var inGrid = false
    private var gridShift = 0f
    private var visible = false
    private var movingInDock = false
    private var moveArmed = false
    private var focusedEntry: AppEntry? = null
    private var labelPosition = 0
    private var wantedBackdrop: String? = ""
    private var shownBackdrop: String? = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        window.setBackgroundDrawable(null)   // the root view paints the only opaque background
        root = findViewById(R.id.root)
        backdrop = findViewById(R.id.backdrop)
        content = findViewById(R.id.content)
        hero = findViewById(R.id.hero)
        dockLabel = findViewById(R.id.dockLabel)
        dockPlate = findViewById(R.id.dockPlate)
        dockClip = findViewById(R.id.dockClip)
        dock = findViewById(R.id.dock)
        gridClip = findViewById(R.id.gridClip)
        grid = findViewById(R.id.grid)
        clock = findViewById(R.id.clock)
        date = findViewById(R.id.date)
        notice = findViewById(R.id.notice)
        moveHint = findViewById(R.id.moveHint)
        sheet = ContextSheet(findViewById(R.id.sheetLayer), content)

        dockAdapter = TileAdapter(this, true)
        gridAdapter = TileAdapter(this, false)

        dock.horizontal = true
        dock.layoutManager = RowLayoutManager(this, RecyclerView.HORIZONTAL) { dockW * 2 }
        dock.adapter = dockAdapter
        dock.onEdge = { direction -> if (direction == View.FOCUS_DOWN) nearestIn(grid, cols) else null }

        gridLayout = GridRowsLayoutManager(this, cols) { tileH * 2 }
        grid.layoutManager = gridLayout
        grid.adapter = gridAdapter
        grid.onEdge = { direction -> if (direction == View.FOCUS_UP) nearestIn(dock, Int.MAX_VALUE) else null }

        val half = dp(GAP_DP) / 2
        val spacing = object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(out: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                out.left = half; out.right = half
            }
        }
        val dockHalf = dp(DOCK_GAP_DP) / 2
        dock.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(out: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                out.left = dockHalf; out.right = dockHalf
            }
        })
        grid.addItemDecoration(spacing)

        applySettings()
        render()
    }

    override fun onStart() {
        super.onStart()
        visible = true
        repo.addListener(this)
        if (prefs.revision != seenRevision) applySettings()
        render()
        playEntrance()
        repo.reload()
        if (!prefs.safeMode) ui.postDelayed({ CrashGuard.markStable(this) }, 10_000)
    }

    override fun onStop() {
        super.onStop()
        visible = false
        repo.removeListener(this)
        ui.removeCallbacksAndMessages(null)
        if (movingPkg != null) stopMove()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while the launcher is already on screen: go back to the start.
        // Coming back from another app keeps the previous position instead.
        if (visible && intent.hasCategory(Intent.CATEGORY_HOME)) goToStart()
    }

    override fun onAppsChanged() = render()

    // Settings and layout

    private fun applySettings() {
        seenRevision = prefs.revision
        val level = prefs.effAnimLevel
        Motion.scale = when (level) { 0 -> 1f; 1 -> 0.6f; else -> 0f }
        tileStyle = TileStyle(
            scale = if (level == 0) 1.12f else 1.07f,
            shadows = prefs.fxShadows,
            labels = prefs.labels,
        )
        cols = when (prefs.tileSize) { 0 -> 6; 2 -> 4; else -> 5 }
        val metrics = resources.displayMetrics
        tileW = (metrics.widthPixels - 2 * dp(MARGIN_DP) - (cols - 1) * dp(GAP_DP)) / cols
        tileH = tileW * 9 / 16
        gridLayout.spanCount = cols
        grid.span = cols
        dockH = dp(66)
        dockW = dockH * 16 / 9

        clock.visibility = if (prefs.showClock) View.VISIBLE else View.GONE
        date.visibility = if (prefs.showDate) View.VISIBLE else View.GONE
        val timePattern = if (prefs.clock24) "HH:mm" else "h:mm"
        clock.format24Hour = timePattern; clock.format12Hour = timePattern
        date.format24Hour = "EEEE, d MMMM"; date.format12Hour = "EEEE, d MMMM"

        parallax = prefs.fxDepth
        // With a backdrop on screen the themed surface would never be seen, so it is not drawn.
        if (prefs.effBgMode == 2) {
            root.setBackgroundResource(
                when (prefs.theme) { 1 -> R.drawable.surface_onyx; 2 -> R.drawable.surface_midnight; else -> R.drawable.surface_graphite }
            )
        } else {
            root.setBackgroundColor(0xFF000000.toInt())
        }
        sheet.setSurface(when (prefs.theme) { 1 -> 0xFF1C1C1E.toInt(); 2 -> 0xFF1B2433.toInt(); else -> 0xFF2A2A2C.toInt() })
        dockLabel.visibility = if (prefs.heroTitle) View.VISIBLE else View.INVISIBLE
        notice.visibility = if (prefs.safeMode) View.VISIBLE else View.GONE

        dockPlate.background = if (prefs.dockPlate) {
            GradientDrawable().apply { cornerRadius = dp(19).toFloat(); setColor(0x29FFFFFF); setStroke(dp(1), 0x47FFFFFF) }
        } else null

        laidOut = false
        shownBackdrop = ""
        dockAdapter.notifyDataSetChanged()
        gridAdapter.notifyDataSetChanged()
        scheduleBackdrop(0)
    }

    private fun render() {
        if (!repo.loaded) return
        val dockApps = repo.dockApps() + AppRepository.APPS_BUTTON + AppRepository.SETTINGS_BUTTON
        val sizeChanged = dockApps.size != dockAdapter.itemCount
        dockAdapter.submit(dockApps)
        gridAdapter.submit(repo.gridApps())
        if (sizeChanged || !laidOut) layoutContent()
        ensureFocus()
    }

    /**
     * Positions the clock, the dock and the app grid. The dock is as wide as its tiles and
     * sits centred a little below the middle of the screen; the grid waits below the bottom
     * edge until it is opened. Runs when the number of dock tiles or the tile size changes.
     */
    private fun layoutContent() {
        laidOut = true
        dockVisible = true
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val margin = dp(MARGIN_DP)
        val gap = dp(GAP_DP)
        val overflow = dp(44)           // room above and below the dock for the focus scale and shadow
        val gridPadTop = dp(24)
        val header = dp(64)             // top of the first row once the grid is open
        val dockPad = dp(8)
        val half = dp(DOCK_GAP_DP) / 2

        var contentW = 2 * (dockPad - half)
        for (e in dockAdapter.items) contentW += (if (e.isInternal) dockH else dockW) + 2 * half
        val maxW = w - 2 * margin
        val fits = contentW <= maxW
        val plateW = minOf(contentW, maxW)
        val plateH = dockH + 2 * dockPad
        val plateLeft = (w - plateW) / 2
        val plateTop = (h * 0.60f).toInt()
        // While everything fits nothing scrolls, so the clip can be generous and shadows stay whole.
        val side = if (fits) dp(40) else 0

        dockPlate.visibility = if (prefs.dockPlate) View.VISIBLE else View.INVISIBLE
        dockPlate.layoutParams = FrameLayout.LayoutParams(plateW, plateH).apply {
            leftMargin = plateLeft; topMargin = plateTop
        }
        dockClip.layoutParams = FrameLayout.LayoutParams(plateW + 2 * side, plateH + 2 * overflow).apply {
            leftMargin = plateLeft - side; topMargin = plateTop - overflow
        }
        dock.setPadding(side + dockPad - half, overflow + dockPad, side + dockPad - half, overflow + dockPad)
        dockLabel.layoutParams = FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = plateTop + plateH + dp(16)
        }
        hero.layoutParams = FrameLayout.LayoutParams(w, plateTop - dp(46))

        val firstRowTop = h + dp(60)
        gridClip.layoutParams = FrameLayout.LayoutParams(w, h - (header - gridPadTop)).apply {
            topMargin = firstRowTop - gridPadTop
        }
        grid.setPadding(margin - gap / 2, gridPadTop, margin - gap / 2, dp(36))

        gridShift = (firstRowTop - header).toFloat()
        applyScene()
    }

    // Scene
    //
    // A handful of springs describe the whole home screen, and applyScene() turns them into
    // transforms. Because the clock, dock, grid and background are all computed from the
    // same values in the same frame, they cannot drift out of step, and any transition can
    // be reversed halfway without a jump.

    /** 0 = dock, 1 = app grid open. */
    private val gridSpring = Spring(Motion.SMOOTH) { applyScene() }
    /** 0 = away, 1 = the home screen has arrived. Overshoots a little: the dock settles into place. */
    private val enterSpring = Spring(Motion.BOUNCY) { applyScene() }
    /** The background easing back as the home screen arrives; much slower than everything else. */
    private val settleSpring = Spring(Motion.GENTLE) { applyScene() }
    /** 0 = idle, 1 = diving into an app. */
    private val launchSpring = Spring(Motion.SMOOTH) { applyScene() }
    /** Where focus sits across the screen, -1..1; the background drifts against it. */
    private val driftSpring = Spring(Motion.GENTLE, 0.0005f) { applyScene() }
    /** The name under the dock arriving. */
    private val labelSpring = Spring(Motion.SNAPPY) { applyScene() }
    private var labelDirection = 1
    private var parallax = true

    init {
        for (s in arrayOf(gridSpring, enterSpring, launchSpring)) s.onSettle = { updateLayers() }
    }

    private fun applyScene() {
        val grid = gridSpring.value
        val gridC = grid.coerceIn(0f, 1f)
        val enter = enterSpring.value
        val launch = launchSpring.value.coerceIn(0f, 1f)

        // Foreground: the grid rides up; the clock and dock sink back and fade as it comes.
        content.translationY = -gridShift * grid
        val present = (1f - gridC * 1.8f).coerceIn(0f, 1f) * (enter * 1.7f).coerceIn(0f, 1f) * (1f - launch)
        val recede = (1f - 0.10f * gridC) * (1f + 0.06f * launch)
        val rise = dp(28) * (1f - enter)

        hero.alpha = present
        hero.scaleX = recede; hero.scaleY = recede
        hero.translationY = rise * 0.45f

        val dockScale = recede * (0.9f + 0.1f * enter)
        for (v in arrayOf(dockPlate, dockClip)) {
            v.alpha = present
            v.scaleX = dockScale; v.scaleY = dockScale
            v.translationY = rise
        }

        val label = labelSpring.value
        dockLabel.alpha = present * label.coerceIn(0f, 1f)
        dockLabel.translationX = (1f - label) * labelDirection * dp(18)
        dockLabel.translationY = rise

        // Rows of the grid arrive one after another, each from a little further down.
        if (gridSpring.isRunning) {
            for (i in 0 until this.grid.childCount) {
                val child = this.grid.getChildAt(i)
                val position = this.grid.getChildAdapterPosition(child)
                if (position < 0) continue
                val row = (position / cols).coerceAtMost(3)
                val local = ((gridC - row * 0.09f) / 0.73f).coerceIn(0f, 1f)
                val eased = 1f - (1f - local) * (1f - local) * (1f - local)
                child.translationY = (1f - eased) * dp(46 + 16 * row)
                if (movingPkg == null) child.alpha = eased
            }
        }
        gridClip.alpha = 1f - 0.7f * launch
        gridClip.scaleX = 1f + 0.05f * launch; gridClip.scaleY = gridClip.scaleX

        // Background: the farthest plane. It drifts against the focus, sinks and dims as the
        // grid opens, eases back when the screen arrives and falls away into an app.
        val settle = 1f - settleSpring.value.coerceIn(0f, 1f)
        backdrop.place(
            scale = 1.04f + 0.05f * settle + 0.045f * gridC + 0.05f * launch,
            x = if (parallax) -driftSpring.value * dp(13) else 0f,
            y = -dp(14) * gridC,
            brightness = (1f - 0.28f * gridC) * (1f - 0.45f * launch),
        )
    }

    /**
     * While the scene is in motion the clock and dock are drawn through a cached layer, so
     * fading and scaling them costs one quad each instead of redrawing their contents.
     */
    private fun updateLayers() {
        val busy = gridSpring.isRunning || enterSpring.isRunning || launchSpring.isRunning
        val type = if (busy) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        if (hero.layerType != type) hero.setLayerType(type, null)
        if (dockClip.layerType != type) dockClip.setLayerType(type, null)
    }

    private fun setGridState(open: Boolean) {
        if (inGrid == open) return
        inGrid = open
        gridSpring.animateTo(if (open) 1f else 0f)
        updateLayers()
        if (!open) grid.scrollToPosition(0)
    }

    /**
     * Played whenever the home screen comes into view (after boot, Home, or leaving an app):
     * the picture eases back, the clock rises and the dock settles into place. It is the
     * exact reverse of diving into an app, which is what makes the two feel like one move.
     */
    private fun playEntrance() {
        launchSpring.snapTo(0f)
        if (!Motion.full || inGrid) {
            enterSpring.snapTo(1f)
            settleSpring.snapTo(1f)
            return
        }
        enterSpring.snapTo(0f)
        settleSpring.snapTo(0f)
        enterSpring.animateTo(1f)
        settleSpring.animateTo(1f)
        updateLayers()
    }

    /** The name under the dock slides in from the side the focus is moving towards. */
    private fun showLabel(entry: AppEntry, position: Int) {
        if (dockLabel.text.toString() == entry.label) return
        labelDirection = if (position >= labelPosition) 1 else -1
        labelPosition = position
        dockLabel.text = entry.label
        if (Motion.full) labelSpring.snapTo(0f)
        labelSpring.animateTo(1f)
    }

    /**
     * Tiles beside the focused one step aside as it grows, so it never looks pasted on top
     * of its neighbours. [span] is the number of columns, or 0 for a single row.
     */
    private fun makeRoom(list: FocusRecyclerView, focused: Int, span: Int) {
        val near = dp(if (span == 0) 6 else 5).toFloat()
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            val position = list.getChildAdapterPosition(child)
            var shift = 0f
            if (Motion.full && focused >= 0 && position >= 0 && (span == 0 || position / span == focused / span)) {
                val distance = position - focused
                shift = when (abs(distance)) {
                    1 -> near
                    2 -> near * 0.35f
                    else -> 0f
                } * (if (distance < 0) -1 else 1)
            }
            Kick.x(child).animateTo(shift)
        }
    }

    // Focus

    override fun onTileFocused(entry: AppEntry, tile: TileView, inDock: Boolean, position: Int) {
        setGridState(!inDock)
        if (inDock) showLabel(entry, position)
        if (focusedEntry?.pkg != entry.pkg) {
            focusedEntry = entry
            scheduleBackdrop(BACKDROP_DELAY)
        }
        makeRoom(dock, if (inDock) position else -1, 0)
        makeRoom(grid, if (inDock) -1 else position, cols)
        val location = IntArray(2)
        tile.getLocationOnScreen(location)
        val center = location[0] + tile.width / 2f
        driftSpring.animateTo((center / resources.displayMetrics.widthPixels - 0.5f) * 2f)
    }

    /** The view in [list] (among its first [limit] items) horizontally closest to the focused one. */
    private fun nearestIn(list: FocusRecyclerView, limit: Int): View? {
        val from = currentFocus ?: return null
        val x = centerX(from)
        var best: View? = null
        var bestDistance = Int.MAX_VALUE
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            if (list.getChildAdapterPosition(child) !in 0 until limit) continue
            val distance = abs(centerX(child) - x)
            if (distance < bestDistance) { best = child; bestDistance = distance }
        }
        return best
    }

    private fun centerX(v: View): Int {
        val location = IntArray(2)
        v.getLocationOnScreen(location)
        return location[0] + v.width / 2
    }

    /** Makes sure something is focused after the lists change (for example an app was removed). */
    private fun ensureFocus() {
        root.post {
            if (sheet.isOpen) return@post
            val focused = currentFocus
            if (focused is TileView && focused.isAttachedToWindow) return@post
            if (dockVisible) dock.focusPosition(dock.lastFocusedPos) else grid.focusPosition(grid.lastFocusedPos)
        }
    }

    private fun goToStart() {
        if (movingPkg != null) stopMove()
        sheet.close(restoreFocus = false)
        if (dockVisible) dock.focusPosition(0) else grid.focusPosition(0)
    }

    // Backdrop

    private val backdropTask = Runnable { loadBackdrop() }

    private fun scheduleBackdrop(delay: Long) {
        ui.removeCallbacks(backdropTask)
        ui.postDelayed(backdropTask, delay)
    }

    private fun loadBackdrop() {
        val spec = App.backdrops.specFor(focusedEntry)
        if (spec?.key == shownBackdrop) return
        wantedBackdrop = spec?.key
        if (spec == null) {
            shownBackdrop = null
            backdrop.show(null, prefs.fxBgTransitions)
            return
        }
        App.backdrops.load(spec) { loaded, bitmap ->
            if (loaded.key == wantedBackdrop && !isDestroyed) {
                shownBackdrop = loaded.key
                backdrop.show(bitmap, prefs.fxBgTransitions)
            }
        }
    }

    // Launching and the context menu

    override fun onTileClick(entry: AppEntry, tile: TileView) {
        if (entry.pkg == AppRepository.APPS_PKG) {
            if (gridAdapter.itemCount > 0) grid.focusPosition(0)
            return
        }
        if (entry.isInternal) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val intent = repo.launchIntent(entry) ?: return
        try {
            if (Motion.enabled) {
                val card = tile.card
                // The app grows out of its tile while the home screen falls away behind it.
                launchSpring.animateTo(1f)
                updateLayers()
                startActivity(intent, ActivityOptions.makeScaleUpAnimation(card, 0, 0, card.width, card.height).toBundle())
            } else {
                startActivity(intent)
            }
        } catch (e: Exception) {
            launchSpring.animateTo(0f)
            Toast.makeText(this, "Nie można uruchomić: ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onTileLongClick(entry: AppEntry, tile: TileView, inDock: Boolean) = openMenu(entry, tile, inDock)

    private fun openMenu(entry: AppEntry, tile: TileView, inDock: Boolean) {
        if (entry.isInternal) return
        val options = ArrayList<ContextSheet.Option>()
        options += ContextSheet.Option("Otwórz") { onTileClick(entry, tile) }
        if (prefs.dockEnabled) {
            if (repo.isInDock(entry.pkg)) {
                options += ContextSheet.Option("Usuń z ulubionych") { repo.setDock(entry.pkg, false) }
            } else {
                options += ContextSheet.Option("Dodaj do ulubionych") { repo.setDock(entry.pkg, true) }
            }
        }
        if (inDock || prefs.sortMode == 0) {
            options += ContextSheet.Option("Przenieś") { startMove(entry, inDock) }
        }
        if (!entry.isInternal) {
            options += ContextSheet.Option("Ukryj") { repo.setHidden(entry.pkg, true) }
            options += ContextSheet.Option("Informacje o aplikacji") {
                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${entry.pkg}")))
            }
            if (!entry.isSystem) {
                options += ContextSheet.Option("Odinstaluj", danger = true) {
                    open(Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.pkg}")))
                }
            }
        }
        sheet.open(entry.label, options, tile.card)
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Ta funkcja nie jest dostępna na tym urządzeniu", Toast.LENGTH_SHORT).show()
        }
    }

    // Reordering

    private fun startMove(entry: AppEntry, inDock: Boolean) {
        movingPkg = entry.pkg
        movingInDock = inDock
        moveArmed = false
        refreshMoving()
        moveHint.visibility = View.VISIBLE
        moveHint.animate().alpha(1f).setDuration(Motion.d(200)).start()
    }

    private fun stopMove() {
        movingPkg = null
        refreshMoving()
        moveHint.animate().alpha(0f).setDuration(Motion.d(160))
            .withEndAction { if (movingPkg == null) moveHint.visibility = View.GONE }.start()
    }

    private fun refreshMoving() {
        for (list in arrayOf(dock, grid)) {
            for (i in 0 until list.childCount) {
                val tile = list.getChildAt(i) as? TileView ?: continue
                tile.setDimmed(movingPkg != null && tile.entry?.pkg != movingPkg)
            }
        }
    }

    private fun handleMoveKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN) {
            moveArmed = true
            val adapter = if (movingInDock) dockAdapter else gridAdapter
            val from = adapter.items.indexOfFirst { it.pkg == movingPkg }
            if (from < 0) { stopMove(); return true }
            val step = when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                KeyEvent.KEYCODE_DPAD_UP -> if (movingInDock) 0 else -cols
                KeyEvent.KEYCODE_DPAD_DOWN -> if (movingInDock) 0 else cols
                else -> 0
            }
            val to = from + step
            if (step != 0 && to in adapter.items.indices && !adapter.items[to].isInternal) {
                if (movingInDock) repo.moveDock(from, to) else repo.moveGrid(from, to)
                val list = if (movingInDock) dock else grid
                list.post { list.smoothScrollToPosition(to) }
            }
            return true
        }
        val confirm = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
            code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BACK
        if (event.action == KeyEvent.ACTION_UP && moveArmed && confirm) stopMove()
        return true
    }

    // Keys

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (movingPkg != null) return handleMoveKey(event)
        // Menu key, or OK held down. The held OK is also caught by the tile's own long-click
        // timer; handling the system's long-press flag here as well covers remotes whose
        // repeat timing differs.
        val code = event.keyCode
        val heldOk = event.isLongPress && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)
        if (!sheet.isOpen && event.action == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_MENU || heldOk)) {
            val tile = currentFocus as? TileView
            val entry = tile?.entry
            if (tile != null && entry != null) {
                openMenu(entry, tile, tile.parent === dock)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // The home screen never closes. Back steps towards the starting position instead.
        when {
            sheet.isOpen -> sheet.close()
            inGrid -> dock.focusPosition(dock.lastFocusedPos)
            else -> goToStart()
        }
    }

    companion object {
        private const val MARGIN_DP = 48
        private const val GAP_DP = 22
        private const val DOCK_GAP_DP = 8
        private const val BACKDROP_DELAY = 260L
    }
}
