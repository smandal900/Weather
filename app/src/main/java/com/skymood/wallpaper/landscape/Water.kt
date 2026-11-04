package com.skymood.wallpaper.landscape

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.skymood.wallpaper.scenes.SkyGradient
import kotlin.math.sin
import kotlin.random.Random

/**
 * A body of water between [top] and [bottom]: a gradient that picks up the sky colour near the
 * horizon, drifting glints, wave lines that grow with the wind, and a shimmering sun or moon
 * reflection when the sky is clear enough.
 */
class Water(private val maxGlints: Int = 70) {

    private val bodyPaint = Paint()
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val reflectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val gx = FloatArray(maxGlints)
    private val gy = FloatArray(maxGlints)
    private val gw = FloatArray(maxGlints)
    private val gp = FloatArray(maxGlints)
    private val lines = FloatArray(maxGlints * 4)
    private val reflectLines = FloatArray(REFLECT_ROWS * 4)

    var top = 0f
        private set
    var bottom = 0f
        private set
    private var left = 0f
    private var right = 0f
    private var reflection = 0
    private var reflectX = 0f

    fun build(left: Float, right: Float, top: Float, bottom: Float, unit: Float) {
        this.left = left; this.right = right; this.top = top; this.bottom = bottom
        val rnd = Random(3)
        for (i in 0 until maxGlints) {
            // Glints bunch up towards the horizon, like perspective.
            val d = rnd.nextFloat()
            gy[i] = top + (bottom - top) * d * d
            gx[i] = left + rnd.nextFloat() * (right - left)
            gw[i] = unit * (8f + 40f * d)
            gp[i] = rnd.nextFloat() * 6.28f
        }
        glintPaint.strokeWidth = 2f * unit.coerceAtLeast(0.6f)
        reflectPaint.strokeWidth = 3f * unit.coerceAtLeast(0.6f)
    }

    fun applyLighting(lighting: Lighting, deep: Int, screenWidth: Int) {
        val near = SkyGradient.blend(lighting.skyBottom, deep, 0.35f)
        val far = lighting.shade(deep)
        bodyPaint.shader = LinearGradient(0f, top, 0f, bottom, near, far, Shader.TileMode.CLAMP)
        glintPaint.color = SkyGradient.blend(lighting.skyBottom, 0xFFFFFFFF.toInt(), 0.5f)
        reflection = lighting.reflection
        reflectPaint.color = reflection
        reflectX = screenWidth * lighting.reflectionX
    }

    fun draw(canvas: Canvas, timeMs: Float, windKmh: Float, shift: Float) {
        canvas.drawRect(left, top, right, bottom, bodyPaint)
        val t = timeMs / 1000f
        val drift = 6f + windKmh * 0.8f
        var n = 0
        for (i in 0 until maxGlints) {
            val x = left + ((gx[i] - left + t * drift * (0.3f + (gy[i] - top) / (bottom - top + 1f))) % (right - left))
            val a = sin(t * 1.4f + gp[i])
            if (a <= 0.2f) continue
            val w = gw[i] * a
            lines[n++] = x - w + shift; lines[n++] = gy[i]
            lines[n++] = x + w + shift; lines[n++] = gy[i]
        }
        glintPaint.alpha = (90 + windKmh * 2f).toInt().coerceIn(60, 170)
        if (n > 0) canvas.drawLines(lines, 0, n, glintPaint)

        if (reflection != 0) {
            var m = 0
            val h = bottom - top
            for (r in 0 until REFLECT_ROWS) {
                val f = r / (REFLECT_ROWS - 1f)
                val y = top + h * 0.02f + f * h * 0.9f
                val wobble = sin(t * 2.2f + r * 1.7f) * h * 0.02f
                val w = h * (0.03f + 0.09f * f) * (0.6f + 0.4f * sin(t * 1.3f + r))
                reflectLines[m++] = reflectX - w + wobble + shift; reflectLines[m++] = y
                reflectLines[m++] = reflectX + w + wobble + shift; reflectLines[m++] = y
            }
            reflectPaint.alpha = 150
            canvas.drawLines(reflectLines, 0, m, reflectPaint)
        }
    }

    private companion object {
        const val REFLECT_ROWS = 16
    }
}
