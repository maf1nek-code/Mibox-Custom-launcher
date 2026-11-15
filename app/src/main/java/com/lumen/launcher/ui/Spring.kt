package com.lumen.launcher.ui

import android.view.Choreographer
import android.view.View
import java.util.WeakHashMap

/** Stiffness and damping ratio of a spring. A ratio of 1 never overshoots; lower values bounce more. */
class SpringSpec(val stiffness: Float, val ratio: Float)

/**
 * A value that moves like a mass on a damped spring.
 *
 * Unlike a timed animation it has no duration and no start: you change the target at any
 * moment and the value bends towards it, keeping the speed it already had. That is what
 * makes rapid remote presses feel continuous instead of restarting an animation each time.
 */
class Spring(
    private val spec: SpringSpec,
    /** How close to the target counts as arrived, in the unit the value is used in. */
    private val tolerance: Float = 0.001f,
    private val onUpdate: (Float) -> Unit,
) {
    var value = 0f
        private set
    var target = 0f
        private set
    private var velocity = 0f
    var isRunning = false
        internal set
    var onSettle: (() -> Unit)? = null

    fun animateTo(to: Float) {
        if (!Motion.enabled) { snapTo(to); return }
        target = to
        SpringDriver.start(this)
    }

    fun snapTo(to: Float) {
        target = to
        value = to
        velocity = 0f
        onUpdate(value)
    }

    /** Pushes the value without changing where it is heading, like flicking it. */
    fun impulse(push: Float) {
        if (Motion.scale != 1f) return
        velocity += push
        SpringDriver.start(this)
    }

    /** Advances the simulation by [dt] seconds. Returns false once the value has come to rest. */
    internal fun advance(dt: Float): Boolean {
        // Reduced motion keeps the movement but removes every bounce.
        val ratio = if (Motion.scale == 1f) spec.ratio else 1f
        val k = spec.stiffness
        val c = 2f * ratio * Math.sqrt(k.toDouble()).toFloat()
        val steps = Math.ceil((dt / 0.004f).toDouble()).toInt().coerceAtLeast(1)
        val h = dt / steps
        repeat(steps) {
            velocity += (-k * (value - target) - c * velocity) * h
            value += velocity * h
        }
        val resting = Math.abs(value - target) < tolerance && Math.abs(velocity) < tolerance * 20f
        if (resting) {
            value = target
            velocity = 0f
        }
        onUpdate(value)
        return !resting
    }
}

/** One frame callback advances every moving spring, so all motion on screen shares a clock. */
object SpringDriver : Choreographer.FrameCallback {
    private val active = ArrayList<Spring>()
    private var last = 0L
    private var posted = false

    fun start(spring: Spring) {
        if (!spring.isRunning) {
            spring.isRunning = true
            active.add(spring)
        }
        if (!posted) {
            posted = true
            last = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun doFrame(now: Long) {
        val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(0.001f, 0.034f)
        last = now
        var i = 0
        while (i < active.size) {
            val spring = active[i]
            if (spring.advance(dt)) {
                i++
            } else {
                active.removeAt(i)
                spring.isRunning = false
                spring.onSettle?.invoke()
            }
        }
        if (active.isNotEmpty()) {
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            posted = false
        }
    }
}

/**
 * Sideways and vertical give for any view: neighbours making room for the focused tile,
 * and the small bump when the remote is pressed towards an edge with nothing beyond it.
 */
object Kick {
    private val xs = WeakHashMap<View, Spring>()
    private val ys = WeakHashMap<View, Spring>()

    fun x(view: View): Spring = xs.getOrPut(view) { Spring(Motion.NUDGE, 0.3f) { view.translationX = it } }
    fun y(view: View): Spring = ys.getOrPut(view) { Spring(Motion.NUDGE, 0.3f) { view.translationY = it } }

    /** The view lunges a little in [direction] and springs back: there is nothing further that way. */
    fun bump(view: View, direction: Int) {
        val push = view.resources.displayMetrics.density * 260f
        val horizontal = direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT
        val spring = if (horizontal) x(view) else y(view)
        // A held button repeats; one bump at a time keeps it a nudge and not a shake.
        if (spring.isRunning) return
        spring.impulse(if (direction == View.FOCUS_LEFT || direction == View.FOCUS_UP) -push else push)
    }
}

/** The lift a list row gets when focused: slightly larger and raised, on the same spring as tiles. */
object FocusLift {
    private val springs = WeakHashMap<View, Spring>()

    fun set(view: View, focused: Boolean) {
        val lift = view.resources.displayMetrics.density * 8f
        val spring = springs.getOrPut(view) {
            Spring(Motion.SNAPPY) { v ->
                val s = 1f + 0.03f * v
                view.scaleX = s
                view.scaleY = s
                view.translationZ = lift * v.coerceIn(0f, 1f)
            }
        }
        spring.animateTo(if (focused) 1f else 0f)
    }
}
