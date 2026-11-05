package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * A lighthouse on a rocky headland above a heaving sea. The headland, the red and white tower
 * and the foreground rocks are painted once (like the other landscapes); the sea, the foam
 * breaking on the rocks and the sweeping lamp beam are animated. The lamp is lit in the
 * evening, at night and in dark weather, and its beam swings across the sea.
 */
class LighthouseLandscape : PaintedLandscape() {
    override val layers = arrayOf(
        PaintLayer(0.50f, 0.66f, 0.08f), // distant coast
        PaintLayer(0.36f, 0.93f, 0.55f), // headland with the lighthouse
        PaintLayer(0.80f, 1f, 1f),       // rocks in front
    )

    private val water = Water(maxGlints = 64)
    private val foamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beam = Path()
    private var lit = false

    // Tower geometry, in fractions of the screen so paint and animation agree.
    private val towerX get() = width * 0.74f
    private val baseY get() = height * 0.80f
    private val towerH get() = height * 0.31f
    private val lampY get() = baseY - towerH * 0.955f

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        water.build(-w * 0.1f, w * 1.1f, h * 0.62f, h, unit)
        glowPaint.shader = RadialGradient(
            0f, 0f, 1f, intArrayOf(0xAAFFE9A8.toInt(), 0x33FFD27A, Color.TRANSPARENT), floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onLightingChanged() {
        water.applyLighting(lighting, DEEP, width)
        foamPaint.color = lighting.shade(0xFFFFFFFF.toInt())
        lit = lighting.windowsLit
    }

    override fun paintAll(k: PaintKit): PaintResult =
        PaintResult(arrayOf(coast(k), headland(k), foreground(k)))

    private fun coast(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(81)
        val hz = h * 0.62f
        val cx = w * 0.12f; val half = w * 0.3f
        val body = Path()
        body.moveTo(cx - half, hz + 1f)
        val n = 40
        for (i in 0..n) {
            val u = i / n.toFloat()
            body.lineTo(cx - half + 2f * half * u, hz - h * 0.05f * sin(PI.toFloat() * u).pow(1.2f) * (0.85f + 0.15f * sin(u * 19f)))
        }
        body.lineTo(cx + half, hz + 1f)
        body.close()
        k.shader = LinearGradient(0f, hz - h * 0.05f, 0f, hz, l.distant(l.foliage(0xFF557A5A.toInt()), 0.55f), l.distant(l.ground(0xFF7C8070.toInt()), 0.6f), Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null
        val lite = l.distant(l.foliage(0xFF5A8A62.toInt()), 0.55f); val dark = l.distant(l.foliage(0xFF2F5240.toInt()), 0.55f)
        for (i in 0 until 16) {
            val u = 0.1f + 0.8f * rnd.nextFloat()
            val top = hz - h * 0.05f * sin(PI.toFloat() * u).pow(1.2f)
            k.simplePine(c, cx - half + 2f * half * u, top + h * 0.004f, h * (0.008f + 0.008f * rnd.nextFloat()), lite, dark, rnd)
        }
        k.haze(c, h * 0.57f, h * 0.65f, 0.4f)
        return bmp
    }

    private fun headland(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(82)
        val baseY = h * 0.80f
        val water = h * 0.90f
        // The rock mass.
        val body = Path()
        body.moveTo(w * 0.38f, water)
        body.cubicTo(w * 0.46f, water - h * 0.01f, w * 0.52f, baseY + h * 0.03f, w * 0.60f, baseY + h * 0.012f)
        body.cubicTo(w * 0.66f, baseY - h * 0.004f, w * 0.80f, baseY - h * 0.006f, w * 0.92f, baseY + h * 0.004f)
        body.cubicTo(w * 1.02f, baseY + h * 0.012f, w * 1.1f, baseY - h * 0.01f, w * 1.14f, baseY - h * 0.02f)
        body.lineTo(w * 1.14f, water)
        body.close()
        val rockLit = l.ground(0xFF8A847A.toInt()); val rockDark = l.ground(0xFF4A4640.toInt())
        k.shader = LinearGradient(0f, baseY - h * 0.02f, 0f, water, rockLit, rockDark, Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null
        // Strata and cracks, clipped to the rock.
        c.save(); c.clipPath(body)
        k.line.strokeWidth = 1.6f * k.unit
        repeat(70) {
            val x = w * (0.4f + 0.75f * rnd.nextFloat()); val y = baseY + h * (0.0f + 0.1f * rnd.nextFloat())
            k.line.color = k.alpha(if (rnd.nextFloat() < 0.6f) Color.BLACK else Color.WHITE, 36)
            c.drawLine(x, y, x + w * (0.03f + 0.07f * rnd.nextFloat()), y + h * 0.003f * (rnd.nextFloat() - 0.5f) * 2f, k.line)
        }
        // The side away from the light is shadowed.
        k.shader = LinearGradient(w * 0.38f, 0f, w * 1.14f, 0f,
            intArrayOf(k.alpha(Color.BLACK, if (k.sun > 0f) 80 else 0), 0, k.alpha(Color.BLACK, if (k.sun > 0f) 0 else 80)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(w * 0.38f, baseY - h * 0.03f, w * 1.14f, water, k.fill)
        k.shader = null
        c.restore()
        // Boulders along the edges and a grassy cap.
        for (i in 0 until 9) {
            val x = w * (0.42f + 0.1f * i)
            k.rock(c, x, baseY + h * (0.012f + 0.05f * rnd.nextFloat()) + h * 0.02f, w * (0.035f + 0.03f * rnd.nextFloat()), l.ground(0xFF76706A.toInt()), rnd)
        }
        k.shader = LinearGradient(0f, baseY - h * 0.012f, 0f, baseY + h * 0.012f, l.foliage(0xFF5A8A48.toInt()), l.foliage(0xFF3A6A34.toInt()), Shader.TileMode.CLAMP)
        val cap = Path()
        cap.moveTo(w * 0.56f, baseY + h * 0.02f)
        cap.cubicTo(w * 0.64f, baseY - h * 0.012f, w * 0.82f, baseY - h * 0.016f, w * 0.97f, baseY - h * 0.004f)
        cap.cubicTo(w * 1.04f, baseY - h * 0.004f, w * 1.1f, baseY - h * 0.02f, w * 1.14f, baseY - h * 0.03f)
        cap.lineTo(w * 1.14f, baseY + h * 0.008f)
        cap.cubicTo(w * 1.0f, baseY + h * 0.016f, w * 0.8f, baseY + h * 0.006f, w * 0.56f, baseY + h * 0.02f)
        cap.close()
        c.drawPath(cap, k.fill)
        k.shader = null
        val grass = l.foliage(0xFF4E8A3E.toInt())
        for (i in 0 until 260) {
            val x = w * (0.58f + 0.55f * rnd.nextFloat()); val y = baseY - h * 0.004f + h * 0.01f * rnd.nextFloat()
            k.line.strokeWidth = 1.4f * k.unit
            k.line.color = k.jitter(if (rnd.nextBoolean()) grass else k.lighten(grass, 0.25f), rnd, 0.2f)
            c.drawLine(x, y, x + (rnd.nextFloat() - 0.5f) * 5f * k.unit, y - h * 0.008f * (0.5f + rnd.nextFloat()), k.line)
        }
        tower(k, c, w * 0.74f, baseY - h * 0.002f, h * 0.31f)
        return bmp
    }

    /** The tower: tapered, red and white banded, with a gallery, a glass lantern room and a red dome. */
    private fun tower(k: PaintKit, c: Canvas, x: Float, base: Float, th: Float) {
        val l = k.l
        val bw = th * 0.17f; val tw = th * 0.11f
        val topY = base - th * 0.84f
        fun halfAt(y: Float) = tw + (bw - tw) * ((y - topY) / (base - topY)).coerceIn(0f, 1f)
        val red = l.shade(0xFFC23B30.toInt()); val white = l.shade(0xFFF1EEE6.toInt())
        val bands = 5
        val lightSide = k.sun // +1: the right edge is lit
        for (i in 0 until bands) {
            val y0 = topY + (base - topY) * i / bands
            val y1 = topY + (base - topY) * (i + 1) / bands
            val col = if (i % 2 == 0) white else red
            val p = Path()
            p.moveTo(x - halfAt(y0), y0); p.lineTo(x + halfAt(y0), y0)
            p.lineTo(x + halfAt(y1), y1); p.lineTo(x - halfAt(y1), y1); p.close()
            val a = k.lighten(col, 0.18f); val b = k.darken(col, 0.38f)
            k.shader = LinearGradient(x - bw, 0f, x + bw, 0f,
                intArrayOf(if (lightSide > 0f) b else a, col, if (lightSide > 0f) a else b), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(p, k.fill)
        }
        k.shader = null
        // Door and windows.
        k.fill.color = l.shade(0xFF3B2A22.toInt())
        c.drawRect(x - tw * 0.3f, base - th * 0.09f, x + tw * 0.3f, base, k.fill)
        val win = if (l.windowsLit) 0xFFFFD27A.toInt() else l.shade(0xFF39485A.toInt())
        k.fill.color = win
        for (fy in floatArrayOf(0.3f, 0.55f)) c.drawRect(x - tw * 0.16f, base - th * fy, x + tw * 0.16f, base - th * fy + th * 0.045f, k.fill)
        // Gallery: a dark platform with a railing.
        val gy = topY
        k.fill.color = l.shade(0xFF2E3238.toInt())
        c.drawRect(x - tw * 1.55f, gy - th * 0.012f, x + tw * 1.55f, gy + th * 0.014f, k.fill)
        k.line.strokeWidth = 1.4f * k.unit; k.line.color = l.shade(0xFF2E3238.toInt())
        c.drawLine(x - tw * 1.5f, gy - th * 0.045f, x + tw * 1.5f, gy - th * 0.045f, k.line)
        var rx = x - tw * 1.5f
        while (rx <= x + tw * 1.5f) { c.drawLine(rx, gy - th * 0.045f, rx, gy - th * 0.012f, k.line); rx += tw * 0.3f }
        // Lantern room.
        val lampH = th * 0.1f
        k.fill.color = if (l.windowsLit) 0xFFFFE9A8.toInt() else l.shade(0xFFBFD3DE.toInt())
        c.drawRect(x - tw * 0.75f, gy - th * 0.012f - lampH, x + tw * 0.75f, gy - th * 0.012f, k.fill)
        k.line.strokeWidth = 1.6f * k.unit; k.line.color = l.shade(0xFF2E3238.toInt())
        for (f in floatArrayOf(-0.75f, -0.25f, 0.25f, 0.75f)) c.drawLine(x + tw * f, gy - th * 0.012f - lampH, x + tw * f, gy - th * 0.012f, k.line)
        // Dome and spike.
        val dome = Path()
        dome.moveTo(x - tw * 0.95f, gy - th * 0.012f - lampH)
        dome.quadTo(x, gy - th * 0.012f - lampH - th * 0.115f, x + tw * 0.95f, gy - th * 0.012f - lampH)
        dome.close()
        k.shader = LinearGradient(x - tw, 0f, x + tw, 0f,
            intArrayOf(if (lightSide > 0f) k.darken(red, 0.3f) else k.lighten(red, 0.15f), if (lightSide > 0f) k.lighten(red, 0.15f) else k.darken(red, 0.3f)),
            null, Shader.TileMode.CLAMP)
        c.drawPath(dome, k.fill)
        k.shader = null
        k.line.strokeWidth = 2f * k.unit
        c.drawLine(x, gy - th * 0.012f - lampH - th * 0.055f, x, gy - th * 0.012f - lampH - th * 0.095f, k.line)
    }

    private fun foreground(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(83)
        val base = l.ground(0xFF6A645C.toInt())
        k.rock(c, w * 0.10f, h * 1.0f, w * 0.2f, base, rnd)
        k.rock(c, w * 0.33f, h * 1.03f, w * 0.15f, l.ground(0xFF5C564F.toInt()), rnd)
        k.rock(c, w * 0.02f, h * 0.93f, w * 0.1f, l.ground(0xFF787268.toInt()), rnd)
        k.rock(c, w * 0.92f, h * 1.02f, w * 0.14f, base, rnd)
        for (i in 0 until 12) {
            k.rock(c, w * rnd.nextFloat(), h * (0.97f + 0.05f * rnd.nextFloat()), w * (0.012f + 0.02f * rnd.nextFloat()), l.ground(0xFF7A746A.toInt()), rnd)
        }
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        if (stage == 1) {
            water.draw(canvas, timeMs, windKmh, shift(0.4f))
            return
        }
        if (stage != layers.size) return
        val t = timeMs / 1000f
        // Surf breaking at the foot of the headland and on the front rocks.
        val rough = 0.6f + (windKmh / 40f).coerceAtMost(1f)
        canvas.save()
        canvas.translate(shift(0.55f), 0f)
        for (i in 0 until 16) {
            val x = w * (0.42f + 0.03f * i)
            val pulse = 0.5f + 0.5f * sin(t * (0.9f + 0.2f * (i % 3)) + i * 1.7f)
            foamPaint.alpha = (200 * pulse * (0.6f + 0.4f * rough)).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, h * 0.9f + h * 0.003f * sin(t * 1.3f + i), w * (0.008f + 0.012f * pulse * rough), foamPaint)
        }
        canvas.restore()
        canvas.save()
        canvas.translate(shift(1f), 0f)
        for (i in 0 until 10) {
            val x = w * (0.04f + 0.1f * i) + w * 0.03f * sin(i * 2.3f)
            val pulse = 0.5f + 0.5f * sin(t * 1.1f + i * 2.1f)
            foamPaint.alpha = (190 * pulse).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, h * 0.975f, w * (0.006f + 0.01f * pulse * rough), foamPaint)
        }
        canvas.restore()
        if (!lit) return
        // The lamp: a glow and a beam that sweeps round (long when side-on, short when pointing at us).
        val lx = towerX + shift(0.55f); val ly = lampY
        val ang = t * 0.9f
        val side = cos(ang)
        val dir = if (side >= 0f) 1f else -1f
        val len = abs(side) * w * 0.9f + w * 0.05f
        canvas.save()
        canvas.translate(lx, ly)
        canvas.scale(h * 0.05f, h * 0.05f)
        canvas.drawCircle(0f, 0f, 1f, glowPaint)
        canvas.restore()
        for (pass in 0 until 3) {
            val spread = (h * 0.006f + len * 0.035f) * (1f + pass * 0.8f)
            beam.rewind()
            beam.moveTo(lx, ly)
            beam.lineTo(lx + dir * len, ly - spread)
            beam.lineTo(lx + dir * len, ly + spread)
            beam.close()
            beamPaint.color = 0xFFFFF2C4.toInt()
            beamPaint.alpha = 34 - pass * 8
            canvas.drawPath(beam, beamPaint)
        }
    }

    private companion object {
        const val DEEP = 0xFF123E5C.toInt()
    }
}
