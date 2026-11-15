package com.lumen.launcher.settings

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.lumen.launcher.App
import com.lumen.launcher.R
import com.lumen.launcher.home.ContextSheet
import com.lumen.launcher.home.FocusRecyclerView
import com.lumen.launcher.home.RowLayoutManager
import com.lumen.launcher.sys.HomeStatus
import com.lumen.launcher.ui.Motion
import com.lumen.launcher.ui.dp

/**
 * Settings in the tvOS arrangement: page title on top, context on the left (icon or
 * preview plus an explanation of the focused row), the list on the right. Pages are
 * plain data (see [Pages]) kept on a back stack inside this one activity.
 */
class SettingsActivity : Activity() {

    private class Frame(val page: Page, var index: Int = 0)

    private lateinit var root: View
    private lateinit var title: TextView
    private lateinit var leftPane: View
    private lateinit var iconBox: View
    private lateinit var pageIcon: ImageView
    private lateinit var previewBox: View
    private lateinit var preview: ImageView
    private lateinit var desc: TextView
    private lateinit var list: FocusRecyclerView

    private val stack = ArrayList<Frame>()
    private var items: List<Item> = emptyList()
    private val adapter = RowAdapter()
    private var grabbed = -1
    private var previewKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        root = findViewById(R.id.settingsRoot)
        title = findViewById(R.id.pageTitle)
        leftPane = findViewById(R.id.leftPane)
        iconBox = findViewById(R.id.iconBox)
        pageIcon = findViewById(R.id.pageIcon)
        previewBox = findViewById(R.id.previewBox)
        preview = findViewById(R.id.preview)
        desc = findViewById(R.id.itemDesc)
        list = findViewById(R.id.list)
        previewBox.clipToOutline = true

        list.layoutManager = RowLayoutManager(this, RecyclerView.VERTICAL) { dp(120) }
        list.adapter = adapter
        applyTheme()
        stack.add(Frame(Pages(this).root()))
        show(0)
    }

    // Navigation

    fun open(page: Page) {
        stack.last().index = list.lastFocusedPos
        stack.add(Frame(page))
        show(1)
    }

    fun back() {
        grabbed = -1
        if (stack.size <= 1) { finish(); return }
        stack.removeAt(stack.size - 1)
        show(-1)
    }

    /** Re-reads the current page after a value changed. */
    fun refresh() {
        applyTheme()
        val before = items.size
        items = stack.last().page.build()
        if (items.size == before) {
            adapter.notifyItemRangeChanged(0, items.size, PAYLOAD)
        } else {
            val index = list.lastFocusedPos
            adapter.notifyDataSetChanged()
            list.post { list.focusPosition(index) }
        }
        (currentFocus?.let { list.findContainingViewHolder(it) })?.bindingAdapterPosition
            ?.takeIf { it in items.indices }?.let { showContext(items[it]) }
    }

    fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun show(direction: Int) {
        val frame = stack.last()
        applyTheme()
        items = frame.page.build()
        title.text = frame.page.title
        pageIcon.setImageResource(frame.page.icon)
        adapter.notifyDataSetChanged()
        val index = frame.index.coerceIn(0, maxOf(items.size - 1, 0))
        list.scrollToPosition(index)
        list.focusPosition(index)
        items.getOrNull(index)?.let { showContext(it) }

        if (direction != 0 && Motion.enabled) {
            val offset = dp(28).toFloat() * direction
            for (v in arrayOf(list, leftPane, title)) {
                v.animate().cancel()
                v.alpha = 0f
                v.translationX = if (v === title) 0f else offset
                v.animate().alpha(1f).translationX(0f).setDuration(Motion.d(360))
                    .setInterpolator(Motion.enter).start()
            }
        }
        // Rows arrive one after another from the side the page came from.
        if (Motion.scale == 1f) {
            val from = dp(36).toFloat() * (if (direction < 0) -1 else 1)
            list.post {
                for (i in 0 until list.childCount) {
                    val row = list.getChildAt(i)
                    val position = list.getChildAdapterPosition(row)
                    if (position < 0) continue
                    row.alpha = 0f
                    row.translationX = from
                    row.animate().alpha(1f).translationX(0f).setStartDelay(30L + (position - index).coerceIn(-8, 8).let { Math.abs(it) } * 26L)
                        .setDuration(420).setInterpolator(Motion.enter).start()
                }
            }
        }
    }

    private fun applyTheme() {
        root.setBackgroundResource(
            when (App.prefs.theme) { 1 -> R.drawable.surface_onyx; 2 -> R.drawable.surface_midnight; else -> R.drawable.surface_graphite }
        )
    }

    /** Left pane: explanation of the focused row, plus a live preview for background choices. */
    private fun showContext(item: Item) {
        desc.text = item.desc
        val spec = item.preview
        if (spec == null) {
            previewKey = null
            previewBox.visibility = View.GONE
            iconBox.visibility = View.VISIBLE
            return
        }
        iconBox.visibility = View.GONE
        previewBox.visibility = View.VISIBLE
        if (previewKey == spec.key) return
        previewKey = spec.key
        preview.setImageDrawable(null)
        App.backdrops.load(spec, exclusive = false) { loaded, bitmap ->
            if (loaded.key == previewKey && !isDestroyed) preview.setImageBitmap(bitmap)
        }
    }

    private fun activate(item: Item, position: Int) {
        when (item) {
            is Nav -> open(item.target())
            is Toggle -> { item.set(!item.get()); refresh() }
            is Choice -> open(choicePage(item))
            is Radio -> { item.pick(); if (stack.last().page.popOnPick) back() else refresh() }
            is Action -> item.run()
            is Movable -> { grabbed = if (grabbed == position) -1 else position; refresh() }
            is Info -> Unit
        }
    }

    private fun choicePage(choice: Choice) = Page(choice.title, stack.last().page.icon) {
        choice.options.mapIndexed { i, option ->
            Radio(option, choice.optionDescs.getOrElse(i) { choice.desc }, { choice.get() == i }, { choice.set(i) })
        }
    }

    // Keys

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (grabbed >= 0 && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
            if (event.action == KeyEvent.ACTION_DOWN) moveGrabbed(if (code == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun moveGrabbed(step: Int) {
        val page = stack.last().page
        val reorder = page.reorder ?: return
        val to = grabbed + step
        if (to !in items.indices || items[to] !is Movable) return
        reorder(grabbed, to)
        items = page.build()
        // The two rows swap their contents in place and focus follows the grabbed app.
        // Moving the views themselves made the list lose its scroll anchor.
        val first = minOf(grabbed, to)
        grabbed = to
        adapter.notifyItemRangeChanged(first, 2, PAYLOAD)
        list.focusPosition(to)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (grabbed >= 0) { grabbed = -1; refresh() } else back()
    }

    // Helpers used by pages

    fun openSystemSettings() = start(Intent(Settings.ACTION_SETTINGS))

    fun openAppDetails(pkg: String) =
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    fun uninstall(pkg: String) = start(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))

    fun openStockHome() {
        val component = HomeStatus.stockHome(this)
        if (component == null) {
            toast("Fabryczny ekran główny jest wyłączony")
            return
        }
        start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(component))
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Ta funkcja nie jest dostępna na tym urządzeniu")
        }
    }

    // List

    private inner class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rowTitle: TextView = view.findViewById(R.id.rowTitle)
        val rowValue: TextView = view.findViewById(R.id.rowValue)
        val rowIcon: ImageView = view.findViewById(R.id.rowIcon)
    }

    private inner class RowAdapter : RecyclerView.Adapter<RowHolder>() {
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_setting, parent, false)
            val holder = RowHolder(view)
            view.setOnFocusChangeListener { v, focused ->
                ContextSheet.focusScale(v, focused)
                val position = holder.bindingAdapterPosition
                if (focused && position in items.indices) showContext(items[position])
            }
            view.setOnClickListener {
                val position = holder.bindingAdapterPosition
                if (position in items.indices) activate(items[position], position)
            }
            return holder
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val item = items[position]
            val accent = App.prefs.accentColor
            holder.rowTitle.text = item.title
            holder.rowTitle.setTextColor(
                if (item is Action && item.danger) stateColors(0xFFC62822.toInt(), getColor(R.color.danger))
                else getColorStateList(R.color.row_title)
            )

            var value = ""
            var icon = 0
            var highlight = false
            when (item) {
                is Nav -> { value = item.value?.invoke().orEmpty(); icon = R.drawable.ic_chevron }
                is Toggle -> { val on = item.get(); value = if (on) "Wł." else "Wył."; highlight = on }
                is Choice -> { value = item.options.getOrElse(item.get()) { "" }; icon = R.drawable.ic_chevron }
                is Radio -> if (item.selected()) { icon = R.drawable.ic_check; highlight = true }
                is Info -> value = item.value()
                is Movable -> if (position == grabbed) { icon = R.drawable.ic_move; value = "Przesuń ▲▼"; highlight = true }
                is Action -> Unit
            }
            holder.rowValue.text = value
            holder.rowValue.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
            val tint = if (highlight) stateColors(0xFF0B0B0C.toInt(), accent) else getColorStateList(R.color.row_value)
            holder.rowValue.setTextColor(tint)
            holder.rowIcon.visibility = if (icon == 0) View.GONE else View.VISIBLE
            if (icon != 0) holder.rowIcon.setImageResource(icon)
            holder.rowIcon.imageTintList = tint
        }
    }

    private fun stateColors(focused: Int, normal: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(focused, normal)
    )

    companion object {
        private const val PAYLOAD = "value"
    }
}
