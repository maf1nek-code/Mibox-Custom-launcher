package com.lumen.launcher.home

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import com.lumen.launcher.ui.Motion

/**
 * The background plane.
 *
 * It does two independent things:
 *  - cross-fades between pictures using two layers. A request that arrives mid-fade waits
 *    for the current one to finish (latest wins), and the incoming picture eases back from
 *    slightly too close, so a change of image reads as a camera move, not a swap;
 *  - takes its position in the scene from the home screen ([place]): it drifts against the
 *    focus, sinks back when the grid opens and falls away when an app launches.
 *
 * Both are a single textured quad with a transform and an alpha: nothing here costs the
 * GPU more than drawing the picture once.
 */
class BackdropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val layerA = makeLayer()
    private val layerB = makeLayer()
    private var front: ImageView = layerA
    private var fading = false
    private var hasPending = false
    private var pending: Bitmap? = null
    private var pendingAnimate = true

    // The two layers only overlap during a cross-fade, so dimming the whole view
    // does not need an offscreen buffer.
    override fun hasOverlappingRendering() = false

    private fun makeLayer() = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = 0f
        this@BackdropView.addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Position of the plane in the scene. Called every frame while anything on screen moves. */
    fun place(scale: Float, x: Float, y: Float, brightness: Float) {
        scaleX = scale
        scaleY = scale
        translationX = x
        translationY = y
        alpha = brightness
    }

    fun show(bitmap: Bitmap?, animate: Boolean) {
        if (fading) {
            hasPending = true; pending = bitmap; pendingAnimate = animate
            return
        }
        val old = front
        if (bitmap == null) {
            if (old.alpha == 0f) return
            if (!animate || !Motion.enabled) { old.alpha = 0f; old.setImageDrawable(null); return }
            fading = true
            old.animate().alpha(0f).setDuration(Motion.d(360)).setInterpolator(Motion.exit).withEndAction {
                old.setImageDrawable(null)
                finishFade()
            }.start()
            return
        }
        val next = if (front === layerA) layerB else layerA
        next.animate().cancel()
        next.setImageBitmap(bitmap)
        next.alpha = 0f
        next.bringToFront()
        front = next
        if (!animate || !Motion.enabled) {
            next.scaleX = 1f; next.scaleY = 1f
            next.alpha = 1f
            old.alpha = 0f; old.setImageDrawable(null)
            return
        }
        fading = true
        val drift = Motion.full
        next.scaleX = if (drift) 1.05f else 1f
        next.scaleY = next.scaleX
        // Opacity lands first; the slow push back continues a moment longer underneath.
        next.animate().alpha(1f).setDuration(Motion.d(640)).setInterpolator(Motion.standard).withEndAction {
            old.alpha = 0f; old.setImageDrawable(null)
            finishFade()
        }.start()
        if (drift) {
            next.animate().scaleX(1f).scaleY(1f).setDuration(1400).setInterpolator(Motion.enter).start()
        }
    }

    private fun finishFade() {
        fading = false
        if (hasPending) {
            hasPending = false
            val b = pending; pending = null
            show(b, pendingAnimate)
        }
    }
}
