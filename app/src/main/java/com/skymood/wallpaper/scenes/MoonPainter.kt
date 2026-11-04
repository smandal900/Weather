package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.skymood.domain.Astro
import kotlin.math.abs
import kotlin.math.cos

/**
 * Draws the moon with its real phase: the lit part is a half disc on one limb plus or minus half
 * an ellipse for the terminator, so crescent, quarter, gibbous and full moons all look right.
 * Waxing moons are lit on the right, waning moons on the left. The lit shape is rebuilt only
 * when the phase changes noticeably, never every frame.
 */
class MoonPainter {
    private val litPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F0E6.toInt() }
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3042.toInt() }
    private val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2A000000 }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lit = Path()
    private val limb = RectF()
    private val terminator = RectF()
    private var radius = 0f
    private var builtPhase = -1f

    fun setup(radius: Float) {
        this.radius = radius
        builtPhase = -1f
        glowPaint.shader = RadialGradient(
            0f, 0f, radius * 4f, intArrayOf(0x66DDE6FF, 0x18AAB8E0, Color.TRANSPARENT), floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
        litPaint.shader = RadialGradient(
            -radius * 0.35f, -radius * 0.35f, radius * 1.5f,
            intArrayOf(0xFFFFFDF4.toInt(), 0xFFE9E6D8.toInt(), 0xFFB9B6AA.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
    }

    private fun build(phase: Float) {
        builtPhase = phase
        val r = radius
        val t = cos(2.0 * Math.PI * phase).toFloat() // +1 new .. -1 full
        limb.set(-r, -r, r, r)
        val rx = abs(t) * r
        terminator.set(-rx, -r, rx, r)
        lit.rewind()
        // Lit limb on the right, then back along the terminator.
        lit.addArc(limb, -90f, 180f)
        lit.arcTo(terminator, 90f, if (t > 0f) -180f else 180f)
        lit.close()
    }

    /** @param alpha 0..1, so the moon can be faint by day. */
    fun draw(canvas: Canvas, cx: Float, cy: Float, phase: Float, alpha: Float = 1f) {
        if (radius <= 0f) return
        if (abs(phase - builtPhase) > 0.004f) build(phase)
        val a = (255 * alpha).toInt().coerceIn(0, 255)
        val illum = Astro.moonIllumination(phase)
        val waxing = phase < 0.5f
        canvas.save()
        canvas.translate(cx, cy)
        glowPaint.alpha = (a * (0.15f + 0.85f * illum)).toInt()
        canvas.drawCircle(0f, 0f, radius * 4f, glowPaint)
        // The unlit part of the disc, faintly visible (earthshine).
        darkPaint.alpha = (a * 0.55f).toInt()
        canvas.drawCircle(0f, 0f, radius, darkPaint)
        if (!waxing) canvas.scale(-1f, 1f)
        litPaint.alpha = a
        canvas.drawPath(lit, litPaint)
        canvas.save()
        canvas.clipPath(lit)
        craterPaint.alpha = (a * 0.17f).toInt()
        canvas.drawCircle(-radius * 0.35f, -radius * 0.2f, radius * 0.22f, craterPaint)
        canvas.drawCircle(radius * 0.3f, radius * 0.35f, radius * 0.15f, craterPaint)
        canvas.drawCircle(radius * 0.25f, -radius * 0.4f, radius * 0.1f, craterPaint)
        canvas.drawCircle(-radius * 0.2f, radius * 0.45f, radius * 0.12f, craterPaint)
        canvas.restore()
        canvas.restore()
    }
}
