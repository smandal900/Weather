package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** A city skyline over a river: lit windows after dark, blinking red beacons on the tallest towers. */
class CitySkylineLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.34f, 0.68f, 0.08f), PaintLayer(0.26f, 0.72f, 0.5f))

    private var slots: List<com.skymood.wallpaper.fauna.GroundSlot> = emptyList()
    private var slotsFor = 0
    override fun groundSlots(): List<com.skymood.wallpaper.fauna.GroundSlot> {
        if (slotsFor != width * 31 + height) {
            slotsFor = width * 31 + height
            val h = height.toFloat()
            // Pigeons strut along the river embankment.
            slots = listOf(
                com.skymood.wallpaper.fauna.GroundSlot(com.skymood.wallpaper.fauna.Species.GULL, 0.06f, 0.94f, com.skymood.wallpaper.fauna.GroundY { _ -> h * 0.678f }, 1.5f, 4),
            )
        }
        return slots
    }
    private val water = Water(maxGlints = 60)
    private val beacon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B30.toInt() }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    private val n = 16
    private val bx = FloatArray(n); private val bw = FloatArray(n); private val bh = FloatArray(n)

    init {
        val r = Random(161)
        var x = -0.04f
        for (i in 0 until n) {
            bw[i] = 0.05f + 0.05f * r.nextFloat()
            bx[i] = x + bw[i] / 2f
            val mid = sin(PI.toFloat() * (i / (n - 1f)))
            bh[i] = 0.08f + 0.2f * r.nextFloat() * (0.4f + 0.6f * mid)
            x += bw[i] * 0.93f
        }
    }

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat()
        water.build(-w * 0.1f, w * 1.1f, h * 0.68f, h, w / 1080f)
        glow.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xAAFF3B30.toInt(), 0x33FF3B30, Color.TRANSPARENT), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onLightingChanged() = water.applyLighting(lighting, 0xFF1A3350.toInt(), width)

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(far(k), near(k)))

    private fun far(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(162)
        val col = l.distant(l.shade(0xFF7C8AA2.toInt()), 0.5f)
        var x = -w * 0.05f
        while (x < w * 1.1f) {
            val bwid = w * (0.03f + 0.04f * rnd.nextFloat()); val bhgt = h * (0.04f + 0.09f * rnd.nextFloat())
            k.fill.color = k.jitter(col, rnd, 0.1f)
            c.drawRect(x, h * 0.68f - bhgt, x + bwid, h * 0.68f, k.fill)
            if (l.windowsLit) {
                k.fill.color = k.alpha(0xFFFFD27A.toInt(), 110)
                repeat(6) { c.drawRect(x + bwid * rnd.nextFloat() * 0.8f, h * 0.68f - bhgt * rnd.nextFloat(), x + bwid * 0.9f, h * 0.68f - bhgt * 0.6f, k.fill).let { } }
            }
            x += bwid * 0.9f
        }
        k.haze(c, h * 0.58f, h * 0.69f, 0.45f)
        return bmp
    }

    private fun near(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(163)
        val base = h * 0.68f
        val body = l.shade(0xFF8896AE.toInt())
        for (i in 0 until n) {
            val x0 = w * (bx[i] - bw[i] / 2f); val x1 = w * (bx[i] + bw[i] / 2f)
            val top = base - h * bh[i]
            k.shader = LinearGradient(x0, 0f, x1, 0f,
                if (k.sunOnLeft) k.lighten(body, 0.12f) else k.darken(body, 0.3f),
                if (k.sunOnLeft) k.darken(body, 0.3f) else k.lighten(body, 0.12f), Shader.TileMode.CLAMP)
            c.drawRect(x0, top, x1, base, k.fill)
            k.shader = null
            // Stepped crown and antenna on the taller ones.
            if (bh[i] > 0.14f) {
                k.fill.color = k.darken(body, 0.12f)
                c.drawRect(x0 + (x1 - x0) * 0.2f, top - h * 0.018f, x1 - (x1 - x0) * 0.2f, top, k.fill)
                k.line.strokeWidth = 2f * k.unit; k.line.color = k.darken(body, 0.3f)
                c.drawLine((x0 + x1) / 2f, top - h * 0.018f, (x0 + x1) / 2f, top - h * 0.04f, k.line)
            }
            val pad = (x1 - x0) * 0.12f
            val cols = ((x1 - x0 - 2 * pad) / (13f * k.unit)).toInt().coerceAtLeast(2)
            val rows = ((base - top - h * 0.02f) / (20f * k.unit)).toInt().coerceAtLeast(2)
            k.windows(c, x0 + pad, top + h * 0.012f, x1 - pad, base - h * 0.008f, cols, rows, rnd, 0.55f)
        }
        // Embankment and street lamps.
        k.fill.color = l.shade(0xFF4E5766.toInt())
        c.drawRect(-k.margin, base - h * 0.004f, w + k.margin, base + h * 0.012f, k.fill)
        for (i in 0 until 14) {
            val x = w * (0.02f + 0.075f * i)
            k.fill.color = if (l.windowsLit) 0xFFFFE9A8.toInt() else l.shade(0xFFBFC8D2.toInt())
            c.drawCircle(x, base - h * 0.012f, 2.5f * k.unit, k.fill)
            if (l.windowsLit) {
                k.shader = RadialGradient(x, base - h * 0.012f, 10f * k.unit, 0x66FFE0A0, 0, Shader.TileMode.CLAMP)
                c.drawCircle(x, base - h * 0.012f, 10f * k.unit, k.fill)
                k.shader = null
            }
        }
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        if (stage == 1) { water.draw(canvas, timeMs, windKmh, shift(0.4f)); return }
        if (stage != layers.size) return
        val blink = 0.5f + 0.5f * sin(timeMs / 1000f * 3.2f)
        canvas.save(); canvas.translate(shift(0.5f), 0f)
        for (i in 0 until n) {
            if (bh[i] <= 0.2f) continue
            val x = w * bx[i]; val y = h * (0.68f - bh[i]) - h * 0.042f
            glow.alpha = (200 * blink).toInt()
            canvas.save(); canvas.translate(x, y); canvas.scale(w * 0.012f, w * 0.012f); canvas.drawCircle(0f, 0f, 1f, glow); canvas.restore()
            beacon.alpha = (120 + 135 * blink).toInt()
            canvas.drawCircle(x, y, w * 0.0035f, beacon)
        }
        canvas.restore()
    }
}
