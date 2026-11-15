package com.lumen.launcher.gfx

import android.graphics.Bitmap
import android.graphics.Color

/** Lightweight dominant-colour extraction (no Palette dependency). */
object ColorPick {
    const val NEUTRAL_PRIMARY = 0xFF4B5563.toInt()
    const val NEUTRAL_SECONDARY = 0xFF27303D.toInt()
    private const val BUCKETS = 12

    /** Returns [primary, secondary]. Falls back to a neutral slate pair for colourless artwork. */
    fun from(src: Bitmap): IntArray {
        val size = 32
        val small = if (src.width == size && src.height == size) src else Bitmap.createScaledBitmap(src, size, size, true)
        val px = IntArray(size * size)
        small.getPixels(px, 0, size, 0, 0, size, size)

        val weight = FloatArray(BUCKETS)
        val r = FloatArray(BUCKETS)
        val g = FloatArray(BUCKETS)
        val b = FloatArray(BUCKETS)
        val hsv = FloatArray(3)
        var total = 0f
        for (c in px) {
            if ((c ushr 24) < 128) continue
            Color.colorToHSV(c, hsv)
            if (hsv[1] < 0.14f || hsv[2] < 0.16f) continue
            val w = hsv[1] * hsv[1] * (0.3f + hsv[2])
            val bucket = ((hsv[0] / 360f) * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
            weight[bucket] += w
            r[bucket] += Color.red(c) * w
            g[bucket] += Color.green(c) * w
            b[bucket] += Color.blue(c) * w
            total += w
        }
        if (total < 6f) return intArrayOf(NEUTRAL_PRIMARY, NEUTRAL_SECONDARY)

        var first = 0
        for (i in 1 until BUCKETS) if (weight[i] > weight[first]) first = i
        var second = -1
        for (i in 0 until BUCKETS) {
            val distance = minOf(Math.abs(i - first), BUCKETS - Math.abs(i - first))
            if (distance < 2 || weight[i] < weight[first] * 0.15f) continue
            if (second < 0 || weight[i] > weight[second]) second = i
        }
        val primary = average(r, g, b, weight, first)
        val secondary = if (second >= 0) average(r, g, b, weight, second) else shiftHue(primary, 32f)
        return intArrayOf(primary, secondary)
    }

    fun isNeutral(colors: IntArray) = colors[0] == NEUTRAL_PRIMARY

    private fun average(r: FloatArray, g: FloatArray, b: FloatArray, w: FloatArray, i: Int): Int =
        Color.rgb((r[i] / w[i]).toInt(), (g[i] / w[i]).toInt(), (b[i] / w[i]).toInt())

    private fun shiftHue(color: Int, by: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + by) % 360f
        return Color.HSVToColor(hsv)
    }

    /** Same hue, with saturation capped and brightness fixed, so any artwork yields a usable tone. */
    fun tune(color: Int, maxSaturation: Float, value: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = minOf(hsv[1], maxSaturation)
        hsv[2] = value
        return Color.HSVToColor(hsv)
    }
}
