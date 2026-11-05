package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** A city skyline over a river: lit windows after dark, blinking red beacons on the tallest towers. */
class CitySkylineLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.34f, 0.68f, 0.08f), PaintLayer(0.26f, 0.72f, 0.5f))
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

/** A hill station: colourful cottages stacked up a pine-covered slope under misty blue mountains. */
class HillStationLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.30f, 0.62f, 0.08f), PaintLayer(0.36f, 0.92f, 0.5f), PaintLayer(0.60f, 1f, 1f))

    override fun build() {}

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(peaks(k), slope(k), edge(k)))

    private fun peaks(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(171)
        k.mountain(c, Ridge(k.width, h * 0.48f, h * 0.14f, true, 17), h * 0.62f,
            l.distant(l.ground(0xFF8EA2BC.toInt()), 0.4f), l.distant(l.ground(0xFF5C7088.toInt()), 0.4f), rnd)
        k.haze(c, h * 0.5f, h * 0.62f, 0.5f)
        return bmp
    }

    private fun slope(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(172)
        val ridge = Ridge(k.width, h * 0.54f, h * 0.07f, false, 18)
        k.hill(c, ridge, h * 0.92f, l.ground(0xFF4E8A44.toInt()), l.ground(0xFF2A5A2C.toInt()), rnd, 120)
        val litP = l.foliage(0xFF3C7A4C.toInt()); val darkP = l.foliage(0xFF1C3A2A.toInt()); val trunk = l.shade(0xFF3A2C22.toInt())
        val walls = intArrayOf(0xFFF0E4CF.toInt(), 0xFFE9C9A0.toInt(), 0xFFCFE0E8.toInt(), 0xFFF0D4C8.toInt(), 0xFFE0E8C8.toInt(), 0xFFF2DC8A.toInt())
        val roofs = intArrayOf(0xFFA4483A.toInt(), 0xFF2F5C8A.toInt(), 0xFF3C6A52.toInt(), 0xFF8A4B2A.toInt(), 0xFFB5412F.toInt())
        for (row in 0 until 4) {
            val off = h * (0.012f + 0.045f * row)
            var x = w * (0.0f + 0.04f * rnd.nextFloat())
            while (x < w * 1.02f) {
                val hw = w * (0.026f + 0.006f * row + 0.008f * rnd.nextFloat())
                val by = ridge.yAt(x) + off + hw * 0.9f
                if (rnd.nextFloat() < 0.82f) k.cottage(c, x, by, hw, hw * (1.0f + 0.3f * rnd.nextFloat()), walls[rnd.nextInt(walls.size)], roofs[rnd.nextInt(roofs.size)])
                else { val th = h * (0.04f + 0.02f * row); k.conifer(c, x, by + th * 0.1f, th, k.jitter(litP, rnd, 0.2f), darkP, trunk, rnd, 0.3f) }
                x += hw * (2.2f + 0.8f * rnd.nextFloat())
            }
        }
        k.haze(c, h * 0.56f, h * 0.7f, 0.3f)
        return bmp
    }

    private fun edge(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(173)
        val litP = l.foliage(0xFF4F8A4A.toInt()); val darkP = l.foliage(0xFF183322.toInt()); val trunk = l.shade(0xFF4A3628.toInt())
        for (s in floatArrayOf(-0.03f, 0.06f, 0.95f, 1.04f)) {
            val x = w * s; val th = h * (0.24f + 0.08f * rnd.nextFloat())
            k.conifer(c, x, h * 0.99f, th, k.jitter(litP, rnd, 0.14f), darkP, trunk, rnd, 1f)
        }
        return bmp
    }
}

/** A hilltop temple with a curved shikhara tower, a long stairway, flags, and diyas that glow after dark. */
class TempleHilltopLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.34f, 0.70f, 0.08f), PaintLayer(0.26f, 0.84f, 0.5f), PaintLayer(0.78f, 1f, 1f))
    private val flag = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF8A1E.toInt() }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flagPath = Path()

    private val tx get() = width * 0.62f
    private val plat get() = height * 0.56f
    private val towerH get() = height * 0.16f

    override fun build() {
        glowPaint.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xCCFFC060.toInt(), 0x44FF9A30, Color.TRANSPARENT), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onLightingChanged() { flag.color = lighting.shade(0xFFFF8A1E.toInt()) }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(far(k), temple(k), fore(k)))

    private fun far(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(181)
        k.mountain(c, Ridge(k.width, h * 0.56f, h * 0.1f, false, 19), h * 0.7f,
            l.distant(l.ground(0xFF7C9A8A.toInt()), 0.5f), l.distant(l.ground(0xFF4E6A5C.toInt()), 0.5f), rnd)
        k.haze(c, h * 0.52f, h * 0.7f, 0.5f)
        return bmp
    }

    private fun shikhara(k: PaintKit, c: Canvas, x: Float, base: Float, bw: Float, tw: Float, hgt: Float, stone: Int) {
        val l = k.l
        val outline = Path()
        outline.moveTo(x - bw, base)
        outline.cubicTo(x - bw * 1.12f, base - hgt * 0.4f, x - tw * 2.0f, base - hgt * 0.72f, x - tw, base - hgt)
        outline.lineTo(x + tw, base - hgt)
        outline.cubicTo(x + tw * 2.0f, base - hgt * 0.72f, x + bw * 1.12f, base - hgt * 0.4f, x + bw, base)
        outline.close()
        val a = k.lighten(stone, 0.15f); val b = k.darken(stone, 0.35f)
        k.shader = LinearGradient(x - bw, 0f, x + bw, 0f, if (k.sunOnLeft) a else b, if (k.sunOnLeft) b else a, Shader.TileMode.CLAMP)
        c.drawPath(outline, k.fill)
        k.shader = null
        // Horizontal ribs follow the tapering silhouette.
        k.line.strokeWidth = max1(1f, 1.4f * k.unit); k.line.color = k.alpha(Color.BLACK, 55)
        var t = 0.06f
        while (t < 0.96f) {
            val half = (bw + (tw - bw) * Math.pow(t.toDouble(), 0.8).toFloat()) * (1f + 0.12f * sin(PI.toFloat() * t))
            c.drawLine(x - half, base - hgt * t, x + half, base - hgt * t, k.line)
            t += 0.07f
        }
        // Crown: amalaka disc, kalash and finial in gold.
        val gold = l.shade(0xFFE0B040.toInt())
        k.fill.color = k.darken(stone, 0.1f)
        c.drawOval(x - tw * 1.7f, base - hgt - hgt * 0.035f, x + tw * 1.7f, base - hgt + hgt * 0.02f, k.fill)
        k.fill.color = gold
        c.drawCircle(x, base - hgt - hgt * 0.07f, tw * 0.9f, k.fill)
        k.line.strokeWidth = max1(1.5f, 2f * k.unit); k.line.color = gold
        c.drawLine(x, base - hgt - hgt * 0.1f, x, base - hgt - hgt * 0.2f, k.line)
    }

    private fun max1(a: Float, b: Float) = if (a > b) a else b

    private fun temple(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(182)
        // The hill.
        k.shader = LinearGradient(0f, h * 0.54f, 0f, h * 0.76f, l.ground(0xFF5E9A4A.toInt()), l.ground(0xFF2E6232.toInt()), Shader.TileMode.CLAMP)
        c.drawPath(moundPath(w * 0.08f, w * 1.16f, h * 0.76f, h * 0.22f), k.fill)
        k.shader = null
        val lit = l.foliage(0xFF4E9A3E.toInt()); val dark = l.foliage(0xFF1E4A24.toInt())
        for (i in 0 until 14) {
            val x = w * (0.14f + 0.9f * rnd.nextFloat())
            if (abs(x - tx) < w * 0.09f) continue
            val u = ((x - w * 0.08f) / (w * 1.08f)).coerceIn(0f, 1f)
            val topY = h * 0.76f - h * 0.22f * Math.pow(sin(PI.toFloat() * u).coerceAtLeast(0f).toDouble(), 1.3).toFloat()
            k.broadleaf(c, x, topY + h * 0.03f + h * 0.05f * rnd.nextFloat(), h * (0.05f + 0.03f * rnd.nextFloat()), k.jitter(lit, rnd, 0.2f), dark, dark, rnd, 0.3f)
        }
        // Stairway up the front.
        val stone = l.ground(0xFFD9C9A8.toInt())
        val stairs = Path()
        stairs.moveTo(tx - w * 0.022f, plat); stairs.lineTo(tx + w * 0.022f, plat)
        stairs.lineTo(tx + w * 0.06f, h * 0.755f); stairs.lineTo(tx - w * 0.06f, h * 0.755f); stairs.close()
        c.drawPath(stairs, k.apply { fill.color = stone }.fill)
        k.line.strokeWidth = 1.4f * k.unit; k.line.color = k.alpha(Color.BLACK, 60)
        var sy = plat + h * 0.01f
        while (sy < h * 0.75f) {
            val f = (sy - plat) / (h * 0.755f - plat)
            val half = w * (0.022f + 0.038f * f)
            c.drawLine(tx - half, sy, tx + half, sy, k.line)
            sy += h * 0.0125f
        }
        // Plinth, sanctum, door.
        val sand = l.ground(0xFFE2C79A.toInt())
        k.fill.color = k.darken(sand, 0.2f)
        c.drawRect(tx - w * 0.17f, plat - h * 0.012f, tx + w * 0.17f, plat + h * 0.004f, k.fill)
        k.fill.color = sand
        c.drawRect(tx - w * 0.075f, plat - h * 0.04f, tx + w * 0.075f, plat - h * 0.012f, k.fill)
        k.fill.color = if (l.windowsLit) 0xFFFFC060.toInt() else l.shade(0xFF3A2A1E.toInt())
        val door = Path(); door.moveTo(tx - w * 0.016f, plat - h * 0.012f); door.lineTo(tx - w * 0.016f, plat - h * 0.026f)
        door.quadTo(tx, plat - h * 0.038f, tx + w * 0.016f, plat - h * 0.026f); door.lineTo(tx + w * 0.016f, plat - h * 0.012f); door.close()
        c.drawPath(door, k.fill)
        shikhara(k, c, tx, plat - h * 0.04f, w * 0.07f, w * 0.018f, towerH, sand)
        // Smaller spires either side.
        shikhara(k, c, tx - w * 0.13f, plat - h * 0.012f, w * 0.03f, w * 0.009f, h * 0.075f, sand)
        shikhara(k, c, tx + w * 0.13f, plat - h * 0.012f, w * 0.03f, w * 0.009f, h * 0.075f, sand)
        // Flagpole.
        k.line.strokeWidth = 2f * k.unit; k.line.color = l.shade(0xFF3A2C22.toInt())
        val topY = plat - h * 0.04f - towerH - towerH * 0.2f
        c.drawLine(tx, topY, tx, topY - h * 0.045f, k.line)
        // Lamp posts up the stairs.
        for (i in 0..4) {
            val f = i / 4f
            val y = plat + (h * 0.755f - plat) * f; val x = tx + (if (i % 2 == 0) -1 else 1) * w * (0.03f + 0.04f * f)
            k.fill.color = l.shade(0xFF4A3A2A.toInt())
            c.drawRect(x - 1.5f * k.unit, y - h * 0.012f, x + 1.5f * k.unit, y, k.fill)
        }
        return bmp
    }

    private fun fore(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(183)
        val lit = l.foliage(0xFF4E9A3E.toInt()); val dark = l.foliage(0xFF1E4A24.toInt())
        for (s in floatArrayOf(0.02f, 0.14f, 0.9f)) k.broadleaf(c, w * s, h * 1.02f, h * (0.2f + 0.05f * rnd.nextFloat()), k.jitter(lit, rnd, 0.15f), dark, l.shade(0xFF4A3628.toInt()), rnd, 0.6f)
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != layers.size) return
        val w = width.toFloat(); val h = height.toFloat()
        val t = timeMs / 1000f
        canvas.save(); canvas.translate(shift(0.5f), 0f)
        // Saffron flag waving in the wind.
        val topY = plat - h * 0.04f - towerH - towerH * 0.2f - h * 0.045f
        val amp = h * (0.004f + windKmh / 40f * 0.006f)
        flagPath.rewind()
        flagPath.moveTo(tx, topY)
        flagPath.quadTo(tx + w * 0.02f, topY + amp * sin(t * 4f), tx + w * 0.04f, topY + h * 0.008f + amp * sin(t * 4f + 1f))
        flagPath.quadTo(tx + w * 0.02f, topY + h * 0.012f + amp * sin(t * 4f + 0.5f), tx, topY + h * 0.018f)
        flagPath.close()
        canvas.drawPath(flagPath, flag)
        // Diyas flickering up the stairs once it is dark.
        if (lighting.windowsLit) {
            for (i in 0..4) {
                val f = i / 4f
                val y = plat + (h * 0.755f - plat) * f - h * 0.012f
                val x = tx + (if (i % 2 == 0) -1 else 1) * w * (0.03f + 0.04f * f)
                val fl = 0.75f + 0.25f * sin(t * 9f + i * 2.3f) * sin(t * 5.1f + i)
                glowPaint.alpha = (255 * fl).toInt().coerceIn(0, 255)
                canvas.save(); canvas.translate(x, y); canvas.scale(w * 0.02f, w * 0.02f); canvas.drawCircle(0f, 0f, 1f, glowPaint); canvas.restore()
            }
        }
        canvas.restore()
    }
}

/** A stone arch bridge over a wide river, with cars crossing and headlights after dark. */
class BridgeRiverLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.34f, 0.64f, 0.08f), PaintLayer(0.46f, 0.68f, 0.45f), PaintLayer(0.70f, 1f, 1f))
    private val water = Water(maxGlints = 60)
    private val carPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lampGlow = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat()
        water.build(-w * 0.1f, w * 1.1f, h * 0.64f, h, w / 1080f)
        lampGlow.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xAAFFE0A0.toInt(), 0x33FFD27A, Color.TRANSPARENT), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onLightingChanged() = water.applyLighting(lighting, 0xFF1E4A5A.toInt(), width)

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(hills(k), bridge(k), banks(k)))

    private fun hills(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(191)
        k.mountain(c, Ridge(k.width, h * 0.54f, h * 0.1f, false, 21), h * 0.64f,
            l.distant(l.ground(0xFF6F9A7A.toInt()), 0.5f), l.distant(l.ground(0xFF4A6E5A.toInt()), 0.5f), rnd)
        k.haze(c, h * 0.5f, h * 0.64f, 0.5f)
        return bmp
    }

    private fun bridge(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(192)
        val deck = h * DECK; val waterY = h * 0.64f
        val x0 = -w * 0.1f; val x1 = w * 1.1f
        val body = Path()
        body.addRect(x0, deck, x1, waterY, Path.Direction.CW)
        val n = 5; val span = (x1 - x0) / n
        for (i in 0 until n) {
            val cx = x0 + span * (i + 0.5f)
            val top = deck + h * 0.02f
            val arch = Path()
            arch.arcTo(RectF(cx - span * 0.4f, top, cx + span * 0.4f, waterY * 2f - top), 180f, 180f, true)
            arch.close()
            body.op(arch, Path.Op.DIFFERENCE)
        }
        val stoneLit = l.ground(0xFFC2B79F.toInt()); val stoneDark = l.ground(0xFF6E6656.toInt())
        k.shader = LinearGradient(0f, deck, 0f, waterY, stoneLit, stoneDark, Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null
        c.save(); c.clipPath(body)
        k.line.strokeWidth = 1.2f * k.unit; k.line.color = k.alpha(Color.BLACK, 45)
        var y = deck + h * 0.008f
        while (y < waterY) { c.drawLine(x0, y, x1, y, k.line); y += h * 0.009f }
        repeat(160) {
            val x = x0 + (x1 - x0) * rnd.nextFloat(); val yy = deck + (waterY - deck) * rnd.nextFloat()
            c.drawLine(x, yy, x, yy + h * 0.009f, k.line)
        }
        c.restore()
        // Parapet and lamps.
        k.fill.color = k.darken(stoneLit, 0.15f)
        c.drawRect(x0, deck - h * 0.011f, x1, deck, k.fill)
        for (i in 0..12) {
            val x = x0 + (x1 - x0) * i / 12f
            k.fill.color = k.darken(stoneLit, 0.3f)
            c.drawRect(x - 2f * k.unit, deck - h * 0.016f, x + 2f * k.unit, deck - h * 0.011f, k.fill)
            if (i % 3 == 1) {
                k.line.strokeWidth = 2f * k.unit; k.line.color = l.shade(0xFF2E3238.toInt())
                c.drawLine(x, deck - h * 0.011f, x, deck - h * 0.04f, k.line)
                k.fill.color = if (l.windowsLit) 0xFFFFE9A8.toInt() else l.shade(0xFFBFD3DE.toInt())
                c.drawCircle(x, deck - h * 0.042f, 3.2f * k.unit, k.fill)
            }
        }
        return bmp
    }

    private fun banks(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(193)
        val lit = l.foliage(0xFF5C9A58.toInt()); val dark = l.foliage(0xFF28553A.toInt())
        for ((x0, x1, hgt) in listOf(Triple(-w * 0.25f, w * 0.42f, 0.2f), Triple(w * 0.62f, w * 1.25f, 0.15f))) {
            k.shader = LinearGradient(0f, h * (1f - hgt), 0f, h, l.ground(0xFF4E8A3C.toInt()), l.ground(0xFF2A5A2C.toInt()), Shader.TileMode.CLAMP)
            c.drawPath(moundPath(x0, x1, h * 1.01f, h * hgt), k.fill)
            k.shader = null
            for (i in 0 until 4) {
                val x = x0 + (x1 - x0) * (0.25f + 0.5f * rnd.nextFloat())
                k.broadleaf(c, x, h * (1.0f - hgt * 0.6f) + h * 0.04f * rnd.nextFloat(), h * (0.1f + 0.05f * rnd.nextFloat()), k.jitter(lit, rnd, 0.2f), dark, dark, rnd, 0.4f)
            }
        }
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        if (stage == 1) { water.draw(canvas, timeMs, windKmh, shift(0.4f)); return }
        if (stage != 2) return
        val t = timeMs / 1000f
        val deck = h * DECK
        canvas.save(); canvas.translate(shift(0.45f), 0f)
        val cols = intArrayOf(0xFFD9442F.toInt(), 0xFF2F5C8A.toInt(), 0xFFF2EDE0.toInt(), 0xFF3C6A52.toInt())
        for (i in 0 until 4) {
            val dir = if (i % 2 == 0) 1f else -1f
            val u = ((t * 0.09f + i * 0.27f) % 1f)
            val x = if (dir > 0f) -w * 0.1f + u * w * 1.2f else w * 1.1f - u * w * 1.2f
            val y = deck - h * (0.003f + 0.0035f * (i % 2))
            carPaint.color = lighting.shade(cols[i])
            canvas.drawRect(x - w * 0.02f, y - h * 0.011f, x + w * 0.02f, y, carPaint)
            carPaint.color = lighting.shade(0xFF2E3A48.toInt())
            canvas.drawRect(x - w * 0.011f, y - h * 0.017f, x + w * 0.009f, y - h * 0.011f, carPaint)
            if (lighting.windowsLit) {
                lampGlow.alpha = 200
                canvas.save(); canvas.translate(x + dir * w * 0.02f, y - h * 0.005f); canvas.scale(w * 0.02f, w * 0.02f); canvas.drawCircle(0f, 0f, 1f, lampGlow); canvas.restore()
            }
        }
        canvas.restore()
    }

    private companion object { const val DECK = 0.52f }
}

/** A forest clearing at a campsite: a tent glowing from within and a crackling campfire with sparks. */
class CampingLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.34f, 0.64f, 0.08f), PaintLayer(0.46f, 0.72f, 0.4f), PaintLayer(0.60f, 1f, 0.9f))
    private val flame = Paint(Paint.ANTI_ALIAS_FLAG)
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC060.toInt() }
    private val fpath = Path()
    private val fx get() = width * 0.66f
    private val fy get() = height * 0.745f

    override fun build() {
        glow.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xAAFFA040.toInt(), 0x44FF7A20, Color.TRANSPARENT), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        flame.color = 0xFFFF7A1A.toInt(); core.color = 0xFFFFE08A.toInt()
    }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(peaks(k), forest(k), clearing(k)))

    private fun peaks(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(201)
        k.mountain(c, Ridge(k.width, h * 0.52f, h * 0.13f, true, 22), h * 0.64f,
            l.distant(l.ground(0xFF7C8EA6.toInt()), 0.4f), l.distant(l.ground(0xFF4E6078.toInt()), 0.4f), rnd, snowLine = h * 0.46f)
        k.haze(c, h * 0.5f, h * 0.64f, 0.45f)
        return bmp
    }

    private fun forest(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(202)
        val ridge = Ridge(k.width, h * 0.64f, h * 0.02f, false, 23)
        k.hill(c, ridge, h * 0.72f, l.distant(l.ground(0xFF3D6B45.toInt()), 0.2f), l.ground(0xFF24402A.toInt()), rnd, 0)
        val litP = l.distant(l.foliage(0xFF4C8255.toInt()), 0.2f); val darkP = l.distant(l.foliage(0xFF1C3A2A.toInt()), 0.2f); val trunk = l.shade(0xFF3A2C22.toInt())
        for (row in 0 until 2) {
            var x = -k.margin + rnd.nextFloat() * 10f
            while (x < w + k.margin) {
                val th = h * (0.07f + rnd.nextFloat() * 0.06f)
                k.conifer(c, x, ridge.yAt(x) + th * (0.15f + row * 0.1f), th, k.jitter(litP, rnd, 0.2f), darkP, trunk, rnd, 0.45f)
                x += (18f + rnd.nextFloat() * 20f) * k.unit
            }
        }
        k.haze(c, h * 0.6f, h * 0.72f, 0.3f)
        return bmp
    }

    private fun clearing(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(203)
        val ridge = Ridge(k.width, h * 0.68f, h * 0.012f, false, 24)
        k.hill(c, ridge, h, l.ground(0xFF4E7A3A.toInt()), l.ground(0xFF253F1E.toInt()), rnd, 0)
        k.grass(c, ridge, h, 2600, l.ground(0xFF7FA85A.toInt()), l.ground(0xFF1E3518.toInt()), rnd)
        // The tent: an A-frame with a lit doorway.
        val cx = w * 0.30f; val base = h * 0.735f; val hw = w * 0.13f; val th = h * 0.085f
        k.fill.color = k.alpha(Color.BLACK, 60)
        c.drawOval(cx - hw * 1.05f, base - h * 0.006f, cx + hw * 1.2f, base + h * 0.012f, k.fill)
        val tent = Path(); tent.moveTo(cx - hw, base); tent.lineTo(cx, base - th); tent.lineTo(cx + hw, base); tent.close()
        val col = l.shade(0xFFE0762E.toInt())
        k.shader = LinearGradient(cx - hw, 0f, cx + hw, 0f,
            if (k.sunOnLeft) k.lighten(col, 0.15f) else k.darken(col, 0.35f), if (k.sunOnLeft) k.darken(col, 0.35f) else k.lighten(col, 0.15f), Shader.TileMode.CLAMP)
        c.drawPath(tent, k.fill)
        k.shader = null
        val door = Path(); door.moveTo(cx - hw * 0.28f, base); door.lineTo(cx, base - th * 0.72f); door.lineTo(cx + hw * 0.28f, base); door.close()
        k.fill.color = if (l.windowsLit) 0xFFFFC060.toInt() else l.shade(0xFF2A1E16.toInt())
        c.drawPath(door, k.fill)
        if (l.windowsLit) {
            k.shader = RadialGradient(cx, base - th * 0.3f, hw * 1.2f, 0x66FFB050, 0, Shader.TileMode.CLAMP)
            c.drawCircle(cx, base - th * 0.3f, hw * 1.2f, k.fill)
            k.shader = null
        }
        k.line.strokeWidth = 1.8f * k.unit; k.line.color = k.darken(col, 0.45f)
        c.drawLine(cx, base - th, cx - hw * 0.28f, base, k.line); c.drawLine(cx, base - th, cx + hw * 0.28f, base, k.line)
        k.line.color = k.alpha(l.shade(0xFFDDDDDD.toInt()), 150); k.line.strokeWidth = 1f * k.unit
        c.drawLine(cx - hw * 0.5f, base - th * 0.5f, cx - hw * 1.15f, base + h * 0.004f, k.line)
        c.drawLine(cx + hw * 0.5f, base - th * 0.5f, cx + hw * 1.15f, base + h * 0.004f, k.line)
        // Fire ring stones, logs and a sitting log.
        for (i in 0 until 9) {
            val a = i / 9f * 6.283f
            k.rock(c, fx + kotlin.math.cos(a) * w * 0.045f, fy + sin(a) * h * 0.006f + h * 0.008f, w * 0.012f, l.ground(0xFF8A847A.toInt()), rnd)
        }
        k.line.strokeWidth = 5f * k.unit; k.line.color = l.shade(0xFF4A3220.toInt())
        c.drawLine(fx - w * 0.035f, fy + h * 0.004f, fx + w * 0.03f, fy - h * 0.002f, k.line)
        c.drawLine(fx - w * 0.03f, fy - h * 0.002f, fx + w * 0.035f, fy + h * 0.004f, k.line)
        k.line.strokeWidth = 10f * k.unit; k.line.color = l.shade(0xFF5A3E26.toInt())
        c.drawLine(w * 0.50f, h * 0.775f, w * 0.60f, h * 0.78f, k.line)
        // Trees framing the clearing.
        val litP = l.foliage(0xFF4F8A4A.toInt()); val darkP = l.foliage(0xFF183322.toInt()); val trunk = l.shade(0xFF4A3628.toInt())
        for (s in floatArrayOf(-0.03f, 0.07f, 0.93f, 1.03f)) k.conifer(c, w * s, h * 0.98f, h * (0.26f + 0.06f * rnd.nextFloat()), k.jitter(litP, rnd, 0.14f), darkP, trunk, rnd, 1f)
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != layers.size) return
        val w = width.toFloat(); val h = height.toFloat()
        val t = timeMs / 1000f
        canvas.save(); canvas.translate(shift(0.9f), 0f)
        // Warm light on the ground, stronger the darker it is.
        val dark = when (lighting.timeOfDay) {
            com.skymood.domain.TimeOfDay.NIGHT -> 1f
            com.skymood.domain.TimeOfDay.DUSK, com.skymood.domain.TimeOfDay.DAWN -> 0.6f
            else -> if (lighting.windowsLit) 0.6f else 0.22f
        }
        val flick = 0.85f + 0.15f * sin(t * 11f) * sin(t * 7.3f)
        glow.alpha = (255 * dark * flick).toInt().coerceIn(0, 255)
        canvas.save(); canvas.translate(fx, fy - h * 0.012f); canvas.scale(w * 0.25f, w * 0.17f); canvas.drawCircle(0f, 0f, 1f, glow); canvas.restore()
        // Flames: three tongues of different height.
        for (i in 0 until 3) {
            val hh = h * (0.036f + 0.014f * sin(t * (9f + i * 2f) + i * 1.7f)) * (if (i == 1) 1.25f else 0.85f)
            val x = fx + (i - 1) * w * 0.012f
            val sway = sin(t * (6f + i) + i) * w * 0.004f
            fpath.rewind()
            fpath.moveTo(x - w * 0.012f, fy)
            fpath.quadTo(x - w * 0.01f + sway, fy - hh * 0.55f, x + sway * 2f, fy - hh)
            fpath.quadTo(x + w * 0.01f + sway, fy - hh * 0.5f, x + w * 0.012f, fy)
            fpath.close()
            canvas.drawPath(fpath, flame)
            canvas.save(); canvas.translate(0f, 0f); canvas.scale(0.55f, 0.6f, x, fy); canvas.drawPath(fpath, core); canvas.restore()
        }
        // Sparks drifting up.
        for (i in 0 until 8) {
            val ph = (t * 0.5f + i * 0.37f) % 1f
            spark.alpha = (255 * (1f - ph)).toInt()
            canvas.drawCircle(fx + sin(i * 5f + ph * 6f) * w * 0.02f, fy - h * 0.03f - ph * h * 0.1f, w * 0.0018f, spark)
        }
        canvas.restore()
    }
}
