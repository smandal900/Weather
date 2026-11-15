package com.skymood.wallpaper.scenes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * A sun with a soft halo and gently shimmering light shafts. Shaders and the ray sprite are built in
 * [setup] (on resize or colour change); [draw] only transforms the canvas.
 */
class SunGlow {

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rays = Path()
    private var radius = 1f
    private var rayColor = Color.WHITE
    private var raySprite: Bitmap? = null
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val spriteDst = RectF()

    fun setup(radius: Float, warm: Boolean) {
        this.radius = radius
        val halo = if (warm) 0xAAFFA864.toInt() else 0x88FFF3C4.toInt()
        val haloMid = if (warm) 0x33FF8A50 else 0x26FFE9A8
        haloPaint.shader = RadialGradient(
            0f, 0f, radius * 6f,
            intArrayOf(halo, haloMid, Color.TRANSPARENT), floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP,
        )
        // Shafts are brightest at the sun and dissolve gradually towards their tips.
        rayColor = if (warm) 0xB0FFC48A.toInt() else 0xA0FFFFFF.toInt()
        streakPaint.shader = RadialGradient(
            0f, 0f, radius * 7f,
            intArrayOf(
                if (warm) 0x55FFC890 else 0x50FFFFFF,
                if (warm) 0x18FFB070 else 0x14FFF4D0,
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
        )
        corePaint.shader = RadialGradient(
            -radius * 0.25f, -radius * 0.25f, radius * 1.2f,
            intArrayOf(Color.WHITE, if (warm) 0xFFFFD08A.toInt() else 0xFFFFF4CF.toInt()),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        rimPaint.shader = RadialGradient(
            0f, 0f, radius * 1.6f,
            intArrayOf(if (warm) 0xCCFFB070.toInt() else 0xCCFFF8E0.toInt(), Color.TRANSPARENT),
            floatArrayOf(0.6f, 1f), Shader.TileMode.CLAMP,
        )
        bakeRays()
    }

    /** The shafts are filled shapes with a gradient: draw them once into a bitmap and rotate that. */
    private fun bakeRays() {
        raySprite?.recycle()
        val len = radius * 11f
        val size = (len * 2f * SPRITE_SCALE).toInt().coerceAtLeast(8)
        val bmp = runCatching { Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888) }.getOrNull()
        raySprite = bmp
        bmp ?: return
        val c = Canvas(bmp)
        c.translate(size / 2f, size / 2f)
        c.scale(SPRITE_SCALE, SPRITE_SCALE)
        drawRays(c, len)
    }

    /**
     * Many thin shafts of uneven length, width and brightness. Each is a few stacked wedges, wide and
     * faint to narrow and brighter, so its edges feather out instead of cutting off like a starburst.
     */
    private fun drawRays(c: Canvas, maxLen: Float) {
        val rnd = java.util.Random(7)
        val count = 30
        for (i in 0 until count) {
            val a = (i + (rnd.nextFloat() - 0.5f) * 0.9f) / count * Math.PI * 2
            val reach = maxLen * (0.45f + 0.55f * rnd.nextFloat())
            val width = 0.03 + 0.05 * rnd.nextFloat() * rnd.nextFloat()
            val bright = 0.35f + 0.65f * rnd.nextFloat()
            // Each shaft fades to nothing exactly at its own tip (ease-out curve), so none ends abruptly.
            rayPaint.shader = RadialGradient(
                0f, 0f, reach,
                intArrayOf(
                    rayColor, fade(rayColor, 0.72f), fade(rayColor, 0.45f), fade(rayColor, 0.22f),
                    fade(rayColor, 0.07f), Color.TRANSPARENT,
                ),
                floatArrayOf(0.06f, 0.2f, 0.4f, 0.62f, 0.82f, 1f), Shader.TileMode.CLAMP,
            )
            for (layer in 0 until 4) {
                val half = width * (1.0 - layer * 0.22)
                rayPaint.alpha = (255 * bright * (0.10f + 0.12f * layer)).toInt().coerceIn(0, 255)
                rays.reset()
                rays.moveTo(0f, 0f)
                rays.lineTo((cos(a - half) * reach).toFloat(), (sin(a - half) * reach).toFloat())
                rays.lineTo((cos(a + half) * reach).toFloat(), (sin(a + half) * reach).toFloat())
                rays.close()
                c.drawPath(rays, rayPaint)
            }
        }
        rayPaint.alpha = 255
    }

    /** @param strength 0..1, lowers everything when the sun is behind clouds. */
    fun draw(canvas: Canvas, cx: Float, cy: Float, timeMs: Float, strength: Float, showCore: Boolean, rays: Boolean = true) {
        val a = (255 * strength).toInt().coerceIn(0, 255)
        haloPaint.alpha = a
        canvas.save()
        canvas.translate(cx, cy)
        val sprite = raySprite
        if (rays && sprite != null) {
            val len = radius * 11f
            val t = timeMs / 1000f
            // Two slowly counter-turning layers whose brightness drifts out of step, so the shafts
            // shimmer and swell instead of rotating rigidly.
            spriteDst.set(-len, -len, len, len)
            canvas.save()
            canvas.rotate(t * 0.5f)
            spritePaint.alpha = (a * (0.75f + 0.25f * sin(t * 0.55f))).toInt().coerceIn(0, 255)
            canvas.drawBitmap(sprite, null, spriteDst, spritePaint)
            canvas.rotate(-t * 0.9f + 11f)
            canvas.scale(0.8f, 0.8f)
            spritePaint.alpha = (a * (0.75f + 0.25f * sin(t * 0.8f + 2f))).toInt().coerceIn(0, 255)
            canvas.drawBitmap(sprite, null, spriteDst, spritePaint)
            canvas.restore()
            if (showCore) {
                // Faint horizontal bloom through the sun, like light scattering in a lens.
                streakPaint.alpha = a
                canvas.save()
                canvas.scale(1f, 0.1f)
                canvas.drawCircle(0f, 0f, radius * 7f, streakPaint)
                canvas.restore()
            }
        }
        val pulse = 1f + 0.04f * sin(timeMs / 1500f)
        canvas.scale(pulse, pulse)
        canvas.drawCircle(0f, 0f, radius * 6f, haloPaint)
        if (showCore) {
            canvas.drawCircle(0f, 0f, radius * 1.6f, rimPaint)
            canvas.drawCircle(0f, 0f, radius, corePaint)
        }
        canvas.restore()
    }

    private companion object {
        const val SPRITE_SCALE = 0.5f

        /** Same colour with its alpha scaled by [f]. */
        fun fade(color: Int, f: Float): Int =
            ((Color.alpha(color) * f).toInt() shl 24) or (color and 0x00FFFFFF)
    }
}
