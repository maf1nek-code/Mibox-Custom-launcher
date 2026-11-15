package com.lumen.launcher.ui

import android.content.Context
import android.view.View
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

/**
 * The motion language, in one place.
 *
 * Rules every screen follows:
 *  - Anything with position or size moves on a spring (see [Spring]), so it can be
 *    redirected mid-flight and keeps its momentum. Timed curves are only for opacity.
 *  - Things arrive with a little life (a spring that may overshoot) and leave plainly and
 *    faster than they came.
 *  - What you are touching reacts at once and quickly; what is far away (the background)
 *    follows slowly. Speed tells you how close something is.
 *  - Everything on a screen is derived from a few shared values, never animated separately.
 */
object Motion {
    // Springs, from nearest to farthest.
    /** Focus on the element under the remote: immediate, a trace of overshoot. */
    val SNAPPY = SpringSpec(520f, 0.74f)
    /** The dip while OK is held. */
    val PRESS = SpringSpec(900f, 0.9f)
    /** Neighbours making room, edge bumps. */
    val NUDGE = SpringSpec(340f, 0.72f)
    /** The lean a tile takes as focus lands on it. */
    val TILT = SpringSpec(170f, 0.42f)
    /** Arrivals: the dock, pop-up menus. */
    val BOUNCY = SpringSpec(230f, 0.68f)
    /** Whole-screen moves: opening the grid, diving into an app. */
    val SMOOTH = SpringSpec(190f, 0.95f)
    /** The background: always the slowest thing on screen. */
    val GENTLE = SpringSpec(34f, 1f)

    // Curves, for opacity and for the few timed moves.
    /** Fast start, long soft landing. */
    val enter = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    /** Slow start, quick exit. */
    val exit = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    /** List scrolling that follows focus. */
    val glide = PathInterpolator(0.22f, 0.6f, 0.2f, 1f)
    val emphasized = PathInterpolator(0.2f, 0f, 0f, 1f)
    val standard = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    /** Spring-shaped curve for the few places that still use a timed animator. */
    val spring = Interpolator { t -> (1.0 - Math.exp(-6.5 * t) * Math.cos(9.0 * t)).toFloat() }

    /** 1 = full animations, 0.6 = reduced, 0 = off. */
    @Volatile
    var scale = 1f

    fun d(ms: Long): Long = (ms * scale).toLong()

    val enabled: Boolean get() = scale > 0f
    val full: Boolean get() = scale == 1f
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun View.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun View.dpf(value: Float): Float = value * resources.displayMetrics.density
