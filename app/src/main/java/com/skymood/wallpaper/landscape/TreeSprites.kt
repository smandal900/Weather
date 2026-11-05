package com.skymood.wallpaper.landscape

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Photographic tree cut-outs (assets/trees, transparent WebP). They are decoded once on a
 * background thread. Landscapes use them when present and fall back to the painted trees
 * otherwise, so deleting the folder reverts to the old look.
 */
object TreeSprites {
    private val ready = CountDownLatch(1)
    @Volatile private var firs: List<Bitmap> = emptyList()
    @Volatile private var oaks: List<Bitmap> = emptyList()
    private var started = false

    @Synchronized
    fun init(assets: AssetManager) {
        if (started) return
        started = true
        Executors.newSingleThreadExecutor { r -> Thread(r, "SkyMoodTrees").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
            .execute {
                try {
                    val all = assets.list("trees")?.filter { it.endsWith(".webp") }?.sorted().orEmpty()
                    fun load(prefix: String) = all.filter { it.startsWith(prefix) }
                        .mapNotNull { n -> runCatching { assets.open("trees/$n").use { BitmapFactory.decodeStream(it) } }.getOrNull() }
                    firs = load("fir")
                    oaks = load("oak")
                } finally {
                    ready.countDown()
                }
            }
    }

    /** The broadleaf (acacia / rain tree) cut-outs, same rules as [firs]. */
    fun oaks(): List<Bitmap> {
        if (started) ready.await(2, TimeUnit.SECONDS)
        return oaks
    }

    /** The fir/spruce cut-outs. Call from a background (painting) thread: waits briefly for loading. */
    fun firs(): List<Bitmap> {
        if (started) ready.await(2, TimeUnit.SECONDS)
        return firs
    }
}

private val treePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

/**
 * Draws a cut-out tree with its base centred on [x], [baseY] and the given pixel [height].
 * The picture is tinted to the light of the scene and, for distant trees, faded into the sky.
 */
internal fun PaintKit.photoTree(c: Canvas, bmp: Bitmap, x: Float, baseY: Float, height: Float, haze: Float, flip: Boolean) {
    val tint = l.shade(Color.WHITE)
    val sky = l.skyBottom
    val keep = 1f - haze
    val mul = Color.rgb((Color.red(tint) * keep).toInt(), (Color.green(tint) * keep).toInt(), (Color.blue(tint) * keep).toInt())
    val add = Color.rgb((Color.red(sky) * haze).toInt(), (Color.green(sky) * haze).toInt(), (Color.blue(sky) * haze).toInt())
    treePaint.colorFilter = LightingColorFilter(mul, add)
    val w = height * bmp.width / bmp.height
    c.save()
    c.translate(x, baseY)
    if (flip) c.scale(-1f, 1f)
    c.drawBitmap(bmp, null, android.graphics.RectF(-w / 2f, -height, w / 2f, 0f), treePaint)
    c.restore()
    treePaint.colorFilter = null
}

/** A ready-made, tinted sprite of a cut-out tree, for trees that sway in the wind. */
internal fun PaintKit.photoTreeSprite(bmp: Bitmap, height: Float): Bitmap? {
    val w = (height * bmp.width / bmp.height).toInt().coerceAtLeast(2)
    val (c, out) = newSprite(w, height.toInt().coerceAtLeast(2)) ?: return null
    val tint = l.shade(Color.WHITE)
    treePaint.colorFilter = LightingColorFilter(tint, 0)
    c.drawBitmap(bmp, null, android.graphics.RectF(0f, 0f, w.toFloat(), height), treePaint)
    treePaint.colorFilter = null
    return out
}
