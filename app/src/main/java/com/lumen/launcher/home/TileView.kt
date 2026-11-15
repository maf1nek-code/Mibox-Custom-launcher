package com.lumen.launcher.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lumen.launcher.R
import com.lumen.launcher.data.AppEntry
import com.lumen.launcher.gfx.sealed
import com.lumen.launcher.ui.Motion
import com.lumen.launcher.ui.Spring
import com.lumen.launcher.ui.dpf

class TileStyle(
    val scale: Float = 1.12f,
    val shadows: Boolean = true,
    /** 0 focused only, 1 always, 2 never. */
    val labels: Int = 0,
)

/**
 * The soft drop shadow under a focused tile: one pre-blurred bitmap shared by every tile
 * of the same size. Drawing it is a single alpha-blended quad, so the GPU never blurs.
 */
object TileShadow {
    private class Entry(val w: Int, val h: Int, val bitmap: Bitmap)

    private val entries = ArrayList<Entry>()

    /**
     * Half-resolution alpha mask of a rounded rectangle [w]x[h] with [pad] of blur room on each side.
     * Sizes within a few pixels share one bitmap (columns round differently), and dock and
     * grid sizes are both kept, so nothing is ever rebuilt while tiles animate.
     */
    fun get(w: Int, h: Int, pad: Int, radius: Float): Bitmap {
        for (e in entries) if (Math.abs(w - e.w) <= 8 && Math.abs(h - e.h) <= 8) return e.bitmap
        val bw = (w + 2 * pad) / 2
        val bh = (h + 2 * pad) / 2
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK
        paint.maskFilter = BlurMaskFilter(pad * 0.30f, BlurMaskFilter.Blur.NORMAL)
        val inset = pad / 2f
        Canvas(bmp).drawRoundRect(RectF(inset, inset, bw - inset, bh - inset), radius / 2f, radius / 2f, paint)
        val sealed = bmp.sealed()
        if (entries.size >= 4) entries.removeAt(0)
        entries.add(Entry(w, h, sealed))
        return sealed
    }
}

/**
 * One app tile.
 *
 * Its whole behaviour comes from four springs, so it never plays a canned animation:
 *  - focus: the card grows, a soft shadow spreads beneath it and the name appears. Pressing
 *    the remote quickly back and forth bends this value rather than restarting it;
 *  - press: the card dips while OK is held, like a key;
 *  - two tilts: as focus lands, the card leans in the direction it came from and rights
 *    itself, which is what makes focus feel like it has weight and travels.
 * Only transforms and alpha change, so none of this ever triggers a layout pass.
 */
class TileView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    lateinit var card: FrameLayout
        private set
    lateinit var art: ImageView
        private set
    lateinit var label: TextView
        private set

    var entry: AppEntry? = null
    var onFocus: ((Boolean) -> Unit)? = null

    private var style = TileStyle()
    private var focus = 0f
    private var press = 0f
    private val focusSpring = Spring(Motion.SNAPPY) { focus = it; apply() }
    private val pressSpring = Spring(Motion.PRESS) { press = it; apply() }
    private val leanSpring = Spring(Motion.TILT, 0.02f) { card.rotationY = it; invalidate() }
    private val pitchSpring = Spring(Motion.TILT, 0.02f) { card.rotationX = it }
    private val shadowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = Color.BLACK }
    private val shadowRect = RectF()
    private val shadowPad = dpf(40f).toInt()

    // The card and the name never overlap, so fading a tile needs no offscreen buffer.
    override fun hasOverlappingRendering() = false

    override fun onFinishInflate() {
        super.onFinishInflate()
        card = findViewById(R.id.card)
        art = findViewById(R.id.art)
        label = findViewById(R.id.label)
        // A distant camera keeps the lean readable as depth without visible distortion.
        card.cameraDistance = resources.displayMetrics.density * 5200f
    }

    fun configure(style: TileStyle, showLabel: Boolean) {
        this.style = style
        label.visibility = if (showLabel && style.labels != 2) View.VISIBLE else View.GONE
        focusSpring.snapTo(if (isFocused) 1f else 0f)
        pressSpring.snapTo(0f)
    }

    /** Used while another tile is being moved. */
    fun setDimmed(dimmed: Boolean) {
        val target = if (dimmed) 0.4f else 1f
        if (alpha != target) animate().alpha(target).setStartDelay(0).setDuration(Motion.d(160)).start()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        // Draw order among siblings follows Z, so the focused tile sits above its neighbours.
        translationZ = if (gainFocus) 1f else 0f
        focusSpring.animateTo(if (gainFocus) 1f else 0f)
        if (gainFocus) {
            when (direction) {
                View.FOCUS_RIGHT -> leanSpring.impulse(LEAN)
                View.FOCUS_LEFT -> leanSpring.impulse(-LEAN)
                View.FOCUS_DOWN -> pitchSpring.impulse(-LEAN * 0.8f)
                View.FOCUS_UP -> pitchSpring.impulse(LEAN * 0.8f)
            }
        }
        onFocus?.invoke(gainFocus)
    }

    override fun setPressed(pressed: Boolean) {
        val changed = pressed != isPressed
        super.setPressed(pressed)
        if (changed) pressSpring.animateTo(if (pressed) 1f else 0f)
    }

    private fun apply() {
        val s = (1f + (style.scale - 1f) * focus) * (1f - 0.06f * press)
        card.scaleX = s
        card.scaleY = s
        val shown = focus.coerceIn(0f, 1f)
        val rest = if (style.labels == 1) 0.6f else 0f
        label.alpha = rest + (1f - rest) * shown
        label.translationY = card.layoutParams.height * (style.scale - 1f) / 2f * shown
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val shown = focus.coerceIn(0f, 1f) * (1f - 0.5f * press)
        if (style.shadows && shown > 0.01f && card.width > 0) {
            val bmp = TileShadow.get(card.width, card.height, shadowPad, dpf(11f))
            val s = card.scaleX
            val halfW = (card.width / 2f + shadowPad) * s
            val halfH = (card.height / 2f + shadowPad) * s
            // The shadow slides opposite to the lean, as if the light stayed put.
            val cx = card.left + card.width / 2f - card.rotationY * dpf(1.6f)
            val cy = card.top + card.height / 2f + dpf(13f) * shown
            shadowRect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
            shadowPaint.alpha = (shown * 175).toInt()
            canvas.drawBitmap(bmp, null, shadowRect, shadowPaint)
        }
        super.dispatchDraw(canvas)
    }

    private companion object {
        /** Degrees per second given to the lean as focus arrives; peaks at roughly four degrees. */
        const val LEAN = 95f
    }
}
