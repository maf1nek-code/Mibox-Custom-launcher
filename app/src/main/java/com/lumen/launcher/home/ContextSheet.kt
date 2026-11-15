package com.lumen.launcher.home

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.lumen.launcher.R
import com.lumen.launcher.ui.FocusLift
import com.lumen.launcher.ui.Motion
import com.lumen.launcher.ui.dp
import com.lumen.launcher.ui.dpf

/**
 * The per-app menu: a compact list that opens beside the tile it was called from.
 * While it is open the rest of the screen cannot take focus.
 */
class ContextSheet(private val layer: FrameLayout, private val content: ViewGroup) {

    class Option(val label: String, val danger: Boolean = false, val run: () -> Unit)

    private val scrim: View = layer.findViewById(R.id.sheetScrim)
    private val panel: LinearLayout = layer.findViewById(R.id.sheetPanel)
    private val title: TextView = layer.findViewById(R.id.sheetTitle)
    private val list: LinearLayout = layer.findViewById(R.id.sheetList)
    private var returnFocus: View? = null

    var isOpen = false
        private set

    fun setSurface(color: Int) {
        panel.background = GradientDrawable().apply {
            cornerRadius = panel.dpf(14f)
            setColor(color)
        }
        panel.elevation = panel.dpf(18f)
    }

    fun open(titleText: String, options: List<Option>, anchor: View) {
        if (isOpen || options.isEmpty()) return
        isOpen = true
        returnFocus = anchor
        title.text = titleText
        list.removeAllViews()

        val inflater = LayoutInflater.from(layer.context)
        val rows = options.map { option ->
            val row = inflater.inflate(R.layout.item_menu, list, false) as TextView
            row.id = View.generateViewId()
            row.text = option.label
            if (option.danger) row.setTextColor(dangerColors(row))
            row.setOnClickListener {
                close()
                option.run()
            }
            list.addView(row)
            row
        }
        // Keep focus inside the menu: every direction that would leave it points back at the row itself.
        rows.forEachIndexed { i, row ->
            row.nextFocusLeftId = row.id
            row.nextFocusRightId = row.id
            row.nextFocusUpId = rows[maxOf(i - 1, 0)].id
            row.nextFocusDownId = rows[minOf(i + 1, rows.size - 1)].id
        }

        place(anchor)
        content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        layer.visibility = View.VISIBLE
        scrim.alpha = 0f
        panel.alpha = 0f
        panel.scaleX = 0.88f
        panel.scaleY = 0.88f
        scrim.animate().alpha(1f).setDuration(Motion.d(240)).start()
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.d(420))
            .setInterpolator(Motion.spring).start()
        if (Motion.scale == 1f) {
            rows.forEachIndexed { i, row ->
                row.alpha = 0f
                row.translationY = row.dpf(10f)
                row.animate().alpha(1f).translationY(0f).setStartDelay(40L + i * 30L)
                    .setDuration(320).setInterpolator(Motion.enter).start()
            }
        }
        rows.first().requestFocus()
    }

    /** Puts the panel next to [anchor]: to its right when there is room, otherwise to its left. */
    private fun place(anchor: View) {
        val screenW = layer.width
        val screenH = layer.height
        val edge = layer.dp(24)
        val gap = layer.dp(22)
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(panel.layoutParams.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val w = panel.measuredWidth
        val h = panel.measuredHeight
        val at = IntArray(2)
        anchor.getLocationOnScreen(at)
        val right = at[0] + anchor.width + gap
        val x = if (right + w <= screenW - edge) right else at[0] - gap - w
        val y = (at[1] + anchor.height / 2 - h / 2).coerceIn(edge, maxOf(edge, screenH - edge - h))
        panel.translationX = x.coerceAtLeast(edge).toFloat()
        panel.translationY = y.toFloat()
        panel.pivotX = if (x >= right) 0f else w.toFloat()
        panel.pivotY = h / 2f
    }

    fun close(restoreFocus: Boolean = true) {
        if (!isOpen) return
        isOpen = false
        content.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (restoreFocus) returnFocus?.takeIf { it.isAttachedToWindow }?.requestFocus()
        returnFocus = null
        scrim.animate().alpha(0f).setDuration(Motion.d(160)).start()
        panel.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setInterpolator(Motion.exit).setDuration(Motion.d(150))
            .withEndAction { if (!isOpen) layer.visibility = View.GONE }.start()
    }

    private fun dangerColors(v: View) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
        intArrayOf(0xFFC62822.toInt(), v.context.getColor(R.color.danger))
    )

    companion object {
        /** Focus treatment for settings rows: a slight lift, nothing more. */
        fun focusScale(v: View, focused: Boolean) = FocusLift.set(v, focused)
    }
}
