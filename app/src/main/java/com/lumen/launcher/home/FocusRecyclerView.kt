package com.lumen.launcher.home

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.lumen.launcher.ui.Kick
import com.lumen.launcher.ui.Motion

/**
 * RecyclerView with deterministic D-pad navigation. The default focus search guesses the
 * next view geometrically, which can skip columns or lose focus during fast key repeats.
 * Here the next item is computed from the adapter position, and focus can only leave the
 * list through [onEdge].
 */
class FocusRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    /** true: a single horizontal row. false: a vertical list or grid with [span] columns. */
    var horizontal = false
    var span = 1

    /** Asked for a target when navigation runs off an edge. Return null to keep focus where it is. */
    var onEdge: ((direction: Int) -> View?)? = null

    var lastFocusedPos = 0
        private set
    private var pendingFocus = -1

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        overScrollMode = OVER_SCROLL_NEVER
        (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    // Children never overlap, so fading the list does not need an offscreen buffer.
    override fun hasOverlappingRendering() = false

    override fun focusSearch(focused: View?, direction: Int): View? {
        val holder = focused?.let { findContainingViewHolder(it) } ?: return super.focusSearch(focused, direction)
        val pos = holder.bindingAdapterPosition
        val count = adapter?.itemCount ?: 0
        if (pos == NO_POSITION || count == 0) return focused

        val target: Int = if (horizontal) {
            when (direction) {
                FOCUS_LEFT -> pos - 1
                FOCUS_RIGHT -> pos + 1
                FOCUS_UP, FOCUS_DOWN -> return onEdge?.invoke(direction) ?: bumped(focused, direction)
                else -> return super.focusSearch(focused, direction)
            }
        } else {
            val col = pos % span
            when (direction) {
                FOCUS_LEFT -> if (col > 0) pos - 1 else -1
                FOCUS_RIGHT -> if (col < span - 1 && pos + 1 < count) pos + 1 else -1
                FOCUS_UP -> if (pos - span >= 0) pos - span else return onEdge?.invoke(direction) ?: bumped(focused, direction)
                FOCUS_DOWN -> when {
                    pos + span < count -> pos + span
                    pos / span < (count - 1) / span -> count - 1
                    else -> return onEdge?.invoke(direction) ?: bumped(focused, direction)
                }
                else -> return super.focusSearch(focused, direction)
            }
        }
        if (target !in 0 until count) return bumped(focused, direction)
        val next = findViewHolderForAdapterPosition(target)
        if (next != null) return next.itemView
        focusPosition(target)
        return focused
    }

    /** Nothing lies further in [direction]: the focused item gives a little and springs back. */
    private fun bumped(focused: View, direction: Int): View {
        Kick.bump(focused, direction)
        return focused
    }

    /** Scrolling that follows focus uses the shared glide curve instead of the stock one. */
    override fun smoothScrollBy(dx: Int, dy: Int) {
        if (Motion.enabled) smoothScrollBy(dx, dy, Motion.glide, Motion.d(420).toInt()) else scrollBy(dx, dy)
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        if (child != null) {
            val pos = getChildAdapterPosition(child)
            if (pos != NO_POSITION) lastFocusedPos = pos
        }
    }

    /** Moves focus to [position], scrolling and waiting for layout first if the item is not on screen. */
    fun focusPosition(position: Int) {
        val count = adapter?.itemCount ?: 0
        if (count == 0) return
        val p = position.coerceIn(0, count - 1)
        val holder = findViewHolderForAdapterPosition(p)
        if (holder != null && !isLayoutRequested) {
            holder.itemView.requestFocus()
            return
        }
        pendingFocus = p
        scrollToPosition(p)
        requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (pendingFocus >= 0) {
            val p = pendingFocus
            pendingFocus = -1
            post { findViewHolderForAdapterPosition(p)?.itemView?.requestFocus() }
        }
    }
}

/** Lays out a little beyond the visible edge so the next item always exists when focus moves to it. */
class RowLayoutManager(context: Context, orientation: Int, private val extra: () -> Int) :
    LinearLayoutManager(context, orientation, false) {
    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        extraLayoutSpace[0] = extra()
        extraLayoutSpace[1] = extra()
    }
}

class GridRowsLayoutManager(context: Context, span: Int, private val extra: () -> Int) :
    GridLayoutManager(context, span) {
    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        extraLayoutSpace[0] = extra()
        extraLayoutSpace[1] = extra()
    }
}
