package com.lumen.launcher.gfx

/**
 * Box blur on a small pixel buffer. Three passes approximate a Gaussian. This runs once
 * per backdrop on a ~5k pixel image, so the GPU never has to blur anything live.
 */
object Blur {
    fun box(px: IntArray, w: Int, h: Int, radius: Int, passes: Int = 3) {
        val tmp = IntArray(px.size)
        repeat(passes) {
            horizontal(px, tmp, w, h, radius)
            vertical(tmp, px, w, h, radius)
        }
    }

    private fun horizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) {
                val c = src[row + i.coerceIn(0, w - 1)]
                rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val out = src[row + (x - r).coerceIn(0, w - 1)]
                val inn = src[row + (x + r + 1).coerceIn(0, w - 1)]
                rs += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                gs += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                bs += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun vertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) {
                val c = src[i.coerceIn(0, h - 1) * w + x]
                rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val out = src[(y - r).coerceIn(0, h - 1) * w + x]
                val inn = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                rs += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                gs += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                bs += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
