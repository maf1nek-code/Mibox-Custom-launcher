package com.lumen.launcher.gfx

import android.graphics.Bitmap

/**
 * Turns a finished bitmap into one that lives on the GPU.
 *
 * A mutable bitmap is uploaded to the GPU again on every frame it is drawn, because the
 * renderer cannot know its pixels did not change. Measured on the Mi Box S, that made a
 * full-screen background cost about 40 ms per frame instead of well under 16. A hardware
 * bitmap is uploaded once. Falls back to a plain immutable copy if the device refuses.
 */
fun Bitmap.sealed(): Bitmap {
    if (!isMutable) return this
    return try {
        val target = if (config == Bitmap.Config.ARGB_8888) Bitmap.Config.HARDWARE else config
        copy(target, false) ?: copy(config, false) ?: this
    } catch (t: Throwable) {
        try { copy(config, false) ?: this } catch (t2: Throwable) { this }
    }
}
