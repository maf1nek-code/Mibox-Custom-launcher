package com.lumen.launcher.gfx

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.lumen.launcher.R
import com.lumen.launcher.data.AppEntry
import com.lumen.launcher.data.AppRepository
import java.util.concurrent.Executors

/**
 * Renders app tiles (TV banner, or a generated tile built from the icon) at exactly the
 * size they are shown, with corners already rounded, so drawing a tile is one plain
 * textured quad with no clipping.
 */
class TileLoader(private val app: Context) {
    private val pool = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "lumen-tiles").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val main = Handler(Looper.getMainLooper())
    private val cache: LruCache<String, Bitmap>

    init {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val budget = minOf(am.memoryClass * 1024 * 1024 / 6, 14 * 1024 * 1024)
        cache = object : LruCache<String, Bitmap>(budget) {
            override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
        }
    }

    fun bind(view: ImageView, e: AppEntry, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val key = "${e.pkg}:${e.version}:$w"
        if (view.tag == key && view.drawable != null) return
        view.tag = key
        val cached = cache.get(key)
        if (cached != null) {
            view.animate().cancel()
            view.alpha = 1f
            view.setImageBitmap(cached)
            (view.drawable as? BitmapDrawable)?.setAntiAlias(true)
            return
        }
        view.setImageDrawable(null)
        val radius = app.resources.displayMetrics.density * 11f
        pool.execute {
            val bmp = try { render(e, w, h, radius).sealed() } catch (t: Throwable) { null } ?: return@execute
            cache.put(key, bmp)
            main.post {
                if (view.tag == key) {
                    view.setImageBitmap(bmp)
                    (view.drawable as? BitmapDrawable)?.setAntiAlias(true)
                    view.alpha = 0f
                    view.animate().alpha(1f).setDuration(160).start()
                }
            }
        }
    }

    /** Square-cornered artwork used as the colour source for backdrops. Not cached. */
    fun art(e: AppEntry): Bitmap? = try { render(e, 256, 144, 0f) } catch (t: Throwable) { null }

    fun trim() = cache.trimToSize(cache.maxSize() / 2)
    fun clear() = cache.evictAll()

    private fun render(e: AppEntry, w: Int, h: Int, radius: Float): Bitmap {
        val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(flat)
        val banner = if (e.isInternal) null else loadBanner(e)
        when {
            e.isInternal -> {
                // Dock buttons: a pane of frosted glass with a white glyph.
                val glass = Paint(Paint.ANTI_ALIAS_FLAG)
                glass.color = 0x2EFFFFFF
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), glass)
                if (e.pkg == AppRepository.APPS_PKG) {
                    val dot = Paint(Paint.ANTI_ALIAS_FLAG)
                    dot.color = 0xFFFFFFFF.toInt()
                    val cell = h * 0.105f
                    val step = h * 0.15f
                    for (row in -1..1) for (col in -1..1) {
                        val cx = w / 2f + col * step
                        val cy = h / 2f + row * step
                        c.drawRoundRect(RectF(cx - cell / 2, cy - cell / 2, cx + cell / 2, cy + cell / 2), cell * 0.28f, cell * 0.28f, dot)
                    }
                } else {
                    app.getDrawable(R.drawable.ic_gear)?.let { gear ->
                        val s = (h * 0.50f).toInt()
                        gear.setBounds((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2)
                        gear.draw(c)
                    }
                }
            }
            banner != null -> {
                c.drawColor(0xFF1C1C1E.toInt())
                banner.setBounds(0, 0, w, h)
                banner.draw(c)
            }
            else -> {
                val icon = loadIcon(e)
                val s = (h * 0.56f).toInt().coerceAtLeast(1)
                val iconBmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
                icon.setBounds(0, 0, s, s)
                icon.draw(Canvas(iconBmp))
                val colors = ColorPick.from(iconBmp)
                // One flat tone taken from the icon. No gradients: the icon is the only detail.
                c.drawColor(if (ColorPick.isNeutral(colors)) 0xFF2C2C2E.toInt() else ColorPick.tune(colors[0], 0.50f, 0.26f))
                c.drawBitmap(iconBmp, (w - s) / 2f, (h - s) / 2f, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
        if (radius <= 0f) return flat
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = BitmapShader(flat, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        Canvas(out).drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)
        return out
    }


    private fun loadBanner(e: AppEntry): Drawable? {
        val component = e.component ?: return null
        val pm = app.packageManager
        return try {
            pm.getActivityInfo(component, 0).loadBanner(pm) ?: pm.getApplicationBanner(e.pkg)
        } catch (ex: Exception) {
            null
        }
    }

    private fun loadIcon(e: AppEntry): Drawable {
        val pm = app.packageManager
        return try {
            pm.getActivityIcon(e.component!!)
        } catch (ex: Exception) {
            pm.defaultActivityIcon
        }
    }
}
