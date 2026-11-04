package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * A sun with a soft halo and slowly turning light rays. Shaders and the ray path are built in
 * [setup] (on resize or colour change); [draw] only transforms the canvas.
 */
class SunGlow {

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rays = Path()
    private var radius = 1f

    fun setup(radius: Float, warm: Boolean) {
        this.radius = radius
        val halo = if (warm) 0xAAFFA864.toInt() else 0x88FFF3C4.toInt()
        val haloMid = if (warm) 0x33FF8A50 else 0x26FFE9A8
        haloPaint.shader = RadialGradient(
            0f, 0f, radius * 6f,
            intArrayOf(halo, haloMid, Color.TRANSPARENT), floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP,
        )
        val ray = if (warm) 0x40FFC48A else 0x38FFFFFF
        rayPaint.shader = RadialGradient(
            0f, 0f, radius * 11f,
            intArrayOf(ray, ray and 0x00FFFFFF or 0x14000000, Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
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
        buildRays()
    }

    /** Twelve thin wedges of uneven width around the centre. */
    private fun buildRays() {
        rays.reset()
        val len = radius * 11f
        val count = 12
        for (i in 0 until count) {
            val a = (i.toDouble() / count) * Math.PI * 2
            val half = if (i % 2 == 0) 0.07 else 0.04
            rays.moveTo(0f, 0f)
            rays.lineTo((cos(a - half) * len).toFloat(), (sin(a - half) * len).toFloat())
            rays.lineTo((cos(a + half) * len).toFloat(), (sin(a + half) * len).toFloat())
            rays.close()
        }
    }

    /** @param strength 0..1, lowers everything when the sun is behind clouds. */
    fun draw(canvas: Canvas, cx: Float, cy: Float, timeMs: Float, strength: Float, showCore: Boolean) {
        val a = (255 * strength).toInt().coerceIn(0, 255)
        haloPaint.alpha = a
        rayPaint.alpha = a
        canvas.save()
        canvas.translate(cx, cy)
        canvas.save()
        canvas.rotate(timeMs / 1000f * 1.5f)
        canvas.drawPath(rays, rayPaint)
        canvas.rotate(-timeMs / 1000f * 2.6f + 15f)
        canvas.scale(0.7f, 0.7f)
        canvas.drawPath(rays, rayPaint)
        canvas.restore()
        val pulse = 1f + 0.04f * sin(timeMs / 1500f)
        canvas.scale(pulse, pulse)
        canvas.drawCircle(0f, 0f, radius * 6f, haloPaint)
        if (showCore) {
            canvas.drawCircle(0f, 0f, radius * 1.6f, rimPaint)
            canvas.drawCircle(0f, 0f, radius, corePaint)
        }
        canvas.restore()
    }
}
