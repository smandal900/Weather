package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Base for the painted landscapes. The detailed scenery is painted into a few bitmaps on a
 * background thread whenever the size, time of day or weather changes (the old bitmaps keep
 * showing until the new ones are ready), so each frame only copies bitmaps and draws the small
 * animated parts through [drawAnimated].
 */
abstract class PaintedLandscape : Landscape() {

    protected abstract val layers: Array<PaintLayer>

    /** Runs on a background thread; must only use [kit] and constants, never view state. */
    protected abstract fun paintAll(kit: PaintKit): PaintResult

    /**
     * Called on the main thread before the layer at [stage] is drawn, and once more with
     * `stage == layers.size` after all layers. Water, boats, swaying grass and so on go here.
     */
    protected open fun drawAnimated(canvas: Canvas, stage: Int) {}

    /** Main-thread hook for updating animated parts after the lighting changed. */
    protected open fun onLightingChanged() {}

    protected var sprites: Map<String, Bitmap> = emptyMap()
        private set

    private var bitmaps: Array<Bitmap?> = emptyArray()
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var generation = 0

    protected fun bitmap(index: Int): Bitmap? = bitmaps.getOrNull(index)

    protected val margin: Float get() = width * 0.08f

    override fun build() {
        // Painting happens in applyLighting, which always follows build.
    }

    override fun applyLighting() {
        val gen = ++generation
        val w = width
        val h = height
        val tod = lighting.timeOfDay
        val weather = lighting.weather
        onLightingChanged()
        painter.execute {
            if (gen != generation) return@execute
            val kit = PaintKit(w, h, Lighting().apply { set(tod, weather) })
            val result = try {
                paintAll(kit)
            } catch (e: Throwable) {
                android.util.Log.e("PaintedLandscape", "painting failed", e)
                null
            } ?: return@execute
            main.post {
                if (gen != generation) {
                    result.bitmaps.forEach { it?.recycle() }
                    result.sprites.values.forEach { it.recycle() }
                    return@post
                }
                val oldBitmaps = bitmaps
                val oldSprites = sprites
                bitmaps = result.bitmaps
                sprites = result.sprites
                oldBitmaps.forEach { it?.recycle() }
                oldSprites.values.forEach { it.recycle() }
            }
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bitmaps
        val m = margin
        for (i in layers.indices) {
            drawAnimated(canvas, i)
            val bmp = b.getOrNull(i) ?: continue
            canvas.drawBitmap(bmp, -m + shift(layers[i].depth), height * layers[i].top, blit)
        }
        drawAnimated(canvas, layers.size)
    }

    private companion object {
        /** One background thread for all painting; slow, detailed work never blocks a frame. */
        val painter = Executors.newSingleThreadExecutor { r ->
            Thread(r, "SkyMoodPainter").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }
    }
}
