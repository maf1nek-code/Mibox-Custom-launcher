package com.lumen.launcher.gfx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorSpace
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.lumen.launcher.App
import com.lumen.launcher.data.AppEntry
import com.lumen.launcher.data.Prefs
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/** A plain dark tone offered as a fixed background. */
class Preset(val id: String, val name: String, val color: Int)

/** A photograph shipped inside the app (assets/wallpapers). */
class Photo(val id: String, val name: String)

/** Identifies one background image at one quality level. */
class BackdropSpec(val kind: Int, val arg: String, val quality: Int, val version: Long = 0L, val dim: Int = 1) {
    val key: String = "$kind:$arg:$quality:$version:$dim"

    companion object {
        const val APP = 0
        const val PRESET = 1
        const val FILE = 2
        const val PHOTO = 3
    }
}

/**
 * Produces full-screen backgrounds. Three sources:
 *  - the focused app's own artwork, blurred until only its colour and light remain,
 *  - built-in landscape photographs,
 *  - plain dark tones,
 *  - the user's own image files.
 * Everything is prepared off the main thread, once, and cached; nothing blurs or filters at draw time.
 */
class Backdrops(private val app: Context, private val prefs: Prefs, private val tiles: TileLoader) {

    val presets = listOf(
        Preset("graphite", "Grafit", 0xFF3B3B40.toInt()),
        Preset("midnight", "Północ", 0xFF1E2D52.toInt()),
        Preset("forest", "Las", 0xFF1D3B31.toInt()),
        Preset("wine", "Bordo", 0xFF47202A.toInt()),
    )

    val photos = listOf(
        Photo("ridge", "Grań"),
        Photo("summit", "Szczyt"),
        Photo("dusk", "Zmierzch"),
        Photo("valley", "Dolina"),
        Photo("range", "Pasmo"),
        Photo("storm", "Burza"),
        Photo("lake", "Jezioro"),
    )

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "lumen-backdrops").apply { priority = Thread.MIN_PRIORITY + 1 }
    }
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    @Volatile
    private var latest: String? = null

    // User images

    fun folder(): File? = app.getExternalFilesDir("backgrounds")

    fun customFiles(): List<File> =
        folder()?.listFiles { f -> f.isFile && f.extension.lowercase() in EXTENSIONS }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()

    /** An image named after a package (for example com.netflix.ninja.jpg) belongs to that app. */
    fun appFile(pkg: String): File? = customFiles().firstOrNull { it.nameWithoutExtension == pkg }

    // Specs

    fun presetSpec(id: String, quality: Int = prefs.effBgQuality) =
        BackdropSpec(BackdropSpec.PRESET, id, quality, 0L, prefs.bgDim)

    fun fileSpec(file: File, quality: Int = prefs.effBgQuality) =
        BackdropSpec(BackdropSpec.FILE, file.name, quality, file.lastModified(), prefs.bgDim)

    fun photoSpec(id: String, quality: Int = prefs.effBgQuality) =
        BackdropSpec(BackdropSpec.PHOTO, id, quality, 0L, prefs.bgDim)

    private fun themePreset(): String = if (prefs.theme == 2) "midnight" else "graphite"

    private fun singleSpec(quality: Int): BackdropSpec {
        val value = prefs.bgSingle
        if (value.startsWith("file:")) {
            val file = File(folder(), value.removePrefix("file:"))
            if (file.isFile) return fileSpec(file, quality)
        }
        if (value.startsWith("preset:")) {
            val id = value.removePrefix("preset:")
            if (presets.any { it.id == id }) return presetSpec(id, quality)
        }
        val photo = value.removePrefix("photo:")
        return photoSpec(if (photos.any { it.id == photo }) photo else photos[0].id, quality)
    }

    /** The background to show while [e] is focused, or null for no image at all. */
    fun specFor(e: AppEntry?, quality: Int = prefs.effBgQuality): BackdropSpec? = when (prefs.effBgMode) {
        2 -> null
        1 -> singleSpec(quality)
        else -> when {
            e == null || e.isInternal -> presetSpec(themePreset(), quality)
            else -> appFile(e.pkg)?.let { fileSpec(it, quality) }
                ?: BackdropSpec(BackdropSpec.APP, e.pkg, quality, e.version, prefs.bgDim)
        }
    }

    // Loading

    /**
     * Delivers the bitmap on the main thread. With [exclusive], a request that has been
     * superseded by a newer one is dropped before any work is done, so fast scrolling
     * never queues up images nobody will see.
     */
    fun load(spec: BackdropSpec, exclusive: Boolean = true, callback: (BackdropSpec, Bitmap?) -> Unit) {
        if (exclusive) latest = spec.key
        cache.get(spec.key)?.let { callback(spec, it); return }
        io.execute {
            if (exclusive && latest != spec.key) return@execute
            val bmp = try { produce(spec)?.let { scrim(it, spec).apply { setHasAlpha(false) }.sealed() } } catch (t: Throwable) { null }
            if (bmp != null) cache.put(spec.key, bmp)
            main.post { callback(spec, bmp) }
        }
    }

    fun trim() = cache.evictAll()

    fun clearCache() {
        cache.evictAll()
        io.execute { diskDir().listFiles()?.forEach { it.delete() } }
    }

    private fun diskDir() = File(app.cacheDir, "backdrops").apply { mkdirs() }

    private fun produce(spec: BackdropSpec): Bitmap? = when (spec.kind) {
        BackdropSpec.FILE -> decodeFile(File(folder(), spec.arg), FILE_W[spec.quality], FILE_H[spec.quality])
        BackdropSpec.PHOTO -> decodeAsset("wallpapers/${spec.arg}.jpg", FILE_W[spec.quality], FILE_H[spec.quality])
        BackdropSpec.PRESET -> {
            val p = presets.firstOrNull { it.id == spec.arg } ?: presets[0]
            tone(AMBIENT_W[spec.quality], AMBIENT_H[spec.quality], p.color)
        }
        else -> appAmbient(spec)
    }

    private fun appAmbient(spec: BackdropSpec): Bitmap? {
        val w = AMBIENT_W[spec.quality]
        val h = AMBIENT_H[spec.quality]
        val prefix = spec.arg.replace(Regex("[^A-Za-z0-9._-]"), "_") + "__"
        val file = File(diskDir(), "$prefix${spec.version}_${w}_v2.png")
        if (file.isFile) {
            val opts = BitmapFactory.Options().apply { inMutable = true }
            BitmapFactory.decodeFile(file.path, opts)?.let { return it }
        }
        val entry = App.repo.find(spec.arg) ?: return null
        val art = tiles.art(entry) ?: return null
        val bmp = blurred(w, h, art)
        try {
            diskDir().listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Exception) {
            file.delete()
        }
        return bmp
    }

    /**
     * Photos are converted to sRGB while decoding. A JPEG that keeps its own colour profile
     * makes the GPU convert every screen pixel on every frame, which cost about 30 ms per
     * frame on the Mi Box S.
     */
    private fun decodeOptions(sample: Int) = BitmapFactory.Options().apply {
        inSampleSize = sample
        inMutable = true
        inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
    }

    private fun decodeFile(file: File, tw: Int, th: Int): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= tw && bounds.outHeight / (sample * 2) >= th) sample *= 2
        val opts = decodeOptions(sample)
        val decoded = BitmapFactory.decodeFile(file.path, opts) ?: return null
        if (decoded.width <= tw * 1.25f) return decoded
        val scale = max(tw.toFloat() / decoded.width, th.toFloat() / decoded.height)
        return Bitmap.createScaledBitmap(
            decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true
        )
    }

    private fun decodeAsset(path: String, tw: Int, th: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= tw && bounds.outHeight / (sample * 2) >= th) sample *= 2
        val opts = decodeOptions(sample)
        return app.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /**
     * The app's artwork blurred until no shape survives, then darkened. Blurring happens in
     * two stages (tiny, then medium) so the enlarged result is perfectly smooth, and a
     * little noise is added at the end so the soft gradients do not band on a TV panel.
     */
    private fun blurred(w: Int, h: Int, art: Bitmap): Bitmap {
        val sw = 48
        val sh = 27
        val small = Bitmap.createScaledBitmap(art, sw, sh, true)
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        Blur.box(px, sw, sh, 8)
        val stage1 = Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888)

        val mw = 192
        val mh = 108
        val mid = Bitmap.createScaledBitmap(stage1, mw, mh, true)
        val mpx = IntArray(mw * mh)
        mid.getPixels(mpx, 0, mw, 0, 0, mw, mh)
        Blur.box(mpx, mw, mh, 10)
        val stage2 = Bitmap.createBitmap(mpx, mw, mh, Bitmap.Config.ARGB_8888)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val matrix = ColorMatrix().apply { setSaturation(1.1f) }
        matrix.postConcat(ColorMatrix().apply { setScale(0.46f, 0.46f, 0.46f, 1f) })
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        Canvas(out).drawBitmap(stage2, null, Rect(0, 0, w, h), paint)
        dither(out)
        return out
    }

    /** A plain tone that is slightly lighter at the top, like light falling on a dark wall. */
    private fun tone(w: Int, h: Int, color: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint()
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            ColorPick.tune(color, 0.5f, 0.21f), ColorPick.tune(color, 0.5f, 0.07f), Shader.TileMode.CLAMP
        )
        Canvas(out).drawPaint(paint)
        dither(out)
        return out
    }

    /**
     * Bakes the legibility dim into the image: an even layer of black that deepens a little
     * towards the bottom, where the app rows sit. Done once here instead of as a separate
     * full-screen translucent view drawn every frame. Plain tones are dark already.
     */
    private fun scrim(source: Bitmap, spec: BackdropSpec): Bitmap {
        if (spec.kind == BackdropSpec.PRESET) return source
        val bmp = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true)
        val base = when (spec.dim) { 0 -> 0f; 2 -> 0.36f; else -> 0.16f }
        val top = if (spec.kind == BackdropSpec.FILE) base + 0.12f else base
        val bottom = top + 0.30f
        fun black(alpha: Float) = (alpha.coerceIn(0f, 1f) * 255).toInt() shl 24
        val paint = Paint()
        paint.shader = LinearGradient(
            0f, 0f, 0f, bmp.height.toFloat(),
            intArrayOf(black(top), black(top), black(bottom)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        Canvas(bmp).drawPaint(paint)
        return bmp
    }

    private fun dither(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var seed = 0x2545F491
        for (i in px.indices) {
            seed = seed xor (seed shl 13)
            seed = seed xor (seed ushr 17)
            seed = seed xor (seed shl 5)
            val d = (seed and 1) - ((seed ushr 1) and 1)
            val p = px[i]
            val r = (((p shr 16) and 0xFF) + d).coerceIn(0, 255)
            val g = (((p shr 8) and 0xFF) + d).coerceIn(0, 255)
            val b = ((p and 0xFF) + d).coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    companion object {
        private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        private val AMBIENT_W = intArrayOf(320, 480, 640)
        private val AMBIENT_H = intArrayOf(180, 270, 360)
        private val FILE_W = intArrayOf(960, 1280, 1920)
        private val FILE_H = intArrayOf(540, 720, 1080)
    }
}
