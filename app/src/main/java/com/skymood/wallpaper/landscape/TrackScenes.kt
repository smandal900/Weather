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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A railway along a hillside: telegraph poles, rolling green hills and a passenger train that passes now and then. */
class RailwayHillsLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.36f, 0.62f, 0.08f), PaintLayer(0.44f, 0.80f, 0.5f), PaintLayer(0.72f, 1f, 1f))
    private val train = TrainRenderer()

    override fun build() {}
    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(far(k), hillside(k), fore(k)))

    private fun far(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(211)
        k.mountain(c, Ridge(k.width, h * 0.5f, h * 0.12f, true, 25), h * 0.62f,
            l.distant(l.ground(0xFF8798AE.toInt()), 0.45f), l.distant(l.ground(0xFF5F7088.toInt()), 0.45f), rnd)
        k.haze(c, h * 0.5f, h * 0.62f, 0.5f)
        return bmp
    }

    private fun hillside(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(212)
        val ridge = Ridge(k.width, h * 0.58f, h * 0.04f, false, 26)
        k.hill(c, ridge, h * 0.8f, l.distant(l.ground(0xFF4E8A44.toInt()), 0.15f), l.ground(0xFF2A5A2C.toInt()), rnd, 160)
        val litP = l.foliage(0xFF4C8255.toInt()); val darkP = l.foliage(0xFF1C3A2A.toInt()); val trunk = l.shade(0xFF3A2C22.toInt())
        var x = -k.margin
        while (x < w + k.margin) {
            val th = h * (0.05f + rnd.nextFloat() * 0.04f)
            k.conifer(c, x, ridge.yAt(x) + th * 0.2f, th, k.jitter(litP, rnd, 0.2f), darkP, trunk, rnd, 0.4f)
            x += (22f + rnd.nextFloat() * 22f) * k.unit
        }
        // Cutting and track.
        val ty = h * TRACK
        k.fill.color = l.ground(0xFF6A5A44.toInt())
        c.drawRect(-k.margin, ty - h * 0.004f, w + k.margin, ty + h * 0.03f, k.fill)
        k.railTrack(c, -k.margin, w + k.margin, ty)
        // Telegraph poles with sagging wires.
        k.line.strokeWidth = 3f * k.unit; k.line.color = l.shade(0xFF3A2C22.toInt())
        val poles = floatArrayOf(0.1f, 0.4f, 0.7f, 1.0f)
        for (px in poles) c.drawLine(w * px, ty + h * 0.006f, w * px, ty - h * 0.07f, k.line)
        k.line.strokeWidth = 1.2f * k.unit; k.line.color = k.alpha(l.shade(0xFF202428.toInt()), 200)
        for (i in 0 until poles.size - 1) {
            val a = w * poles[i]; val b = w * poles[i + 1]
            var prevX = a; var prevY = ty - h * 0.064f
            for (s in 1..8) {
                val u = s / 8f
                val xx = a + (b - a) * u; val yy = ty - h * 0.064f + sin(PI.toFloat() * u) * h * 0.012f
                c.drawLine(prevX, prevY, xx, yy, k.line); prevX = xx; prevY = yy
            }
        }
        k.haze(c, h * 0.6f, h * 0.74f, 0.22f)
        return bmp
    }

    private fun fore(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(213)
        val ridge = Ridge(k.width, h * 0.8f, h * 0.02f, false, 27)
        k.hill(c, ridge, h, l.ground(0xFF4E7A3A.toInt()), l.ground(0xFF253F1E.toInt()), rnd, 0)
        k.grass(c, ridge, h, 2000, l.ground(0xFF7FA85A.toInt()), l.ground(0xFF1E3518.toInt()), rnd)
        k.flowers(c, ridge, h, 40, rnd)
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != 2) return
        val w = width.toFloat(); val h = height.toFloat()
        val t = timeMs / 1000f
        val cycle = 26f
        val u = (t % cycle) / 15f
        if (u > 1f) return
        canvas.save(); canvas.translate(shift(0.5f), 0f)
        train.draw(canvas, -w * 0.75f + u * w * 2.1f, h * TRACK + h * 0.004f, w, h, lighting, 5)
        canvas.restore()
    }

    private companion object { const val TRACK = 0.655f }
}

/** A tall stone viaduct over a misty valley with a lit train crossing it, best at night. */
class TrainViaductLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.30f, 0.64f, 0.08f), PaintLayer(0.46f, 0.84f, 0.35f), PaintLayer(0.40f, 0.86f, 0.55f), PaintLayer(0.80f, 1f, 1f))
    private val train = TrainRenderer()

    override fun build() {}
    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(peaks(k), valley(k), viaduct(k), fore(k)))

    private fun peaks(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(221)
        k.mountain(c, Ridge(k.width, h * 0.5f, h * 0.15f, true, 28), h * 0.64f,
            l.distant(l.ground(0xFF8798AE.toInt()), 0.4f), l.distant(l.ground(0xFF5F7088.toInt()), 0.4f), rnd, snowLine = h * 0.43f)
        k.haze(c, h * 0.5f, h * 0.64f, 0.45f)
        return bmp
    }

    private fun valley(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(222)
        val ridge = Ridge(k.width, h * 0.62f, h * 0.04f, false, 29)
        k.hill(c, ridge, h * 0.84f, l.distant(l.ground(0xFF3D6B45.toInt()), 0.25f), l.distant(l.ground(0xFF24402A.toInt()), 0.2f), rnd, 0)
        val litP = l.distant(l.foliage(0xFF4C8255.toInt()), 0.25f); val darkP = l.distant(l.foliage(0xFF1C3A2A.toInt()), 0.25f); val trunk = l.shade(0xFF3A2C22.toInt())
        var x = -k.margin
        while (x < w + k.margin) {
            val th = h * (0.05f + rnd.nextFloat() * 0.04f)
            k.conifer(c, x, ridge.yAt(x) + th * 0.2f + h * 0.05f * rnd.nextFloat(), th, k.jitter(litP, rnd, 0.2f), darkP, trunk, rnd, 0.4f)
            x += (14f + rnd.nextFloat() * 14f) * k.unit
        }
        k.haze(c, h * 0.6f, h * 0.8f, 0.5f)
        return bmp
    }

    private fun viaduct(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(223)
        val deck = h * DECK; val foot = h * 0.80f
        val x0 = -w * 0.1f; val x1 = w * 1.1f
        val body = Path()
        body.addRect(x0, deck, x1, foot, Path.Direction.CW)
        val n = 6; val span = (x1 - x0) / n
        for (i in 0 until n) {
            val cx = x0 + span * (i + 0.5f)
            val top = deck + h * 0.045f
            val arch = Path()
            arch.arcTo(RectF(cx - span * 0.34f, top, cx + span * 0.34f, foot * 2f - top - h * 0.0f), 180f, 180f, true)
            arch.close()
            body.op(arch, Path.Op.DIFFERENCE)
        }
        val stoneLit = l.ground(0xFFBDB29A.toInt()); val stoneDark = l.ground(0xFF5E5646.toInt())
        k.shader = LinearGradient(0f, deck, 0f, foot, stoneLit, stoneDark, Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null
        c.save(); c.clipPath(body)
        k.line.strokeWidth = 1.2f * k.unit; k.line.color = k.alpha(Color.BLACK, 45)
        var y = deck + h * 0.008f
        while (y < foot) { c.drawLine(x0, y, x1, y, k.line); y += h * 0.011f }
        repeat(220) { val x = x0 + (x1 - x0) * rnd.nextFloat(); val yy = deck + (foot - deck) * rnd.nextFloat(); c.drawLine(x, yy, x, yy + h * 0.011f, k.line) }
        c.restore()
        // Deck, rails and parapet.
        k.fill.color = k.darken(stoneLit, 0.25f)
        c.drawRect(x0, deck - h * 0.006f, x1, deck + h * 0.004f, k.fill)
        k.railTrack(c, x0, x1, deck - h * 0.014f)
        k.fill.color = k.darken(stoneLit, 0.1f)
        c.drawRect(x0, deck - h * 0.008f, x1, deck, k.fill)
        // Overhead-line masts.
        k.line.strokeWidth = 2.5f * k.unit; k.line.color = l.shade(0xFF2E3238.toInt())
        for (i in 0..8) { val x = x0 + (x1 - x0) * i / 8f; c.drawLine(x, deck - h * 0.012f, x, deck - h * 0.06f, k.line) }
        c.drawLine(x0, deck - h * 0.055f, x1, deck - h * 0.055f, k.line)
        return bmp
    }

    private fun fore(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[3]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(224)
        val litP = l.foliage(0xFF3F7A4A.toInt()); val darkP = l.foliage(0xFF183322.toInt()); val trunk = l.shade(0xFF4A3628.toInt())
        for (s in floatArrayOf(-0.03f, 0.08f, 0.9f, 1.02f)) k.conifer(c, w * s, h * 1.0f, h * (0.24f + 0.06f * rnd.nextFloat()), k.jitter(litP, rnd, 0.14f), darkP, trunk, rnd, 1f)
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != 3) return
        val w = width.toFloat(); val h = height.toFloat()
        val t = timeMs / 1000f
        val u = (t % 24f) / 14f
        if (u > 1f) return
        canvas.save(); canvas.translate(shift(0.55f), 0f)
        train.draw(canvas, -w * 0.75f + u * w * 2.1f, h * DECK - h * 0.014f + h * 0.004f, w, h, lighting, 5)
        canvas.restore()
    }

    private companion object { const val DECK = 0.50f }
}

/** A hairpin mountain road climbing towards snowy peaks, with a car driving up it. */
class MountainRoadLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.20f, 0.62f, 0.08f), PaintLayer(0.34f, 0.96f, 0.5f))
    private val carPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val n = 60
    private val rx = FloatArray(n + 1); private val ry = FloatArray(n + 1); private val rw = FloatArray(n + 1)

    private fun computeRoad(w: Float, h: Float) {
        for (i in 0..n) {
            val t = i / n.toFloat()
            ry[i] = h * 0.90f - h * 0.44f * Math.pow(t.toDouble(), 0.82).toFloat()
            rx[i] = w * 0.5f + sin(t * 3.5f * PI.toFloat() + 0.6f) * w * 0.3f * (1f - 0.55f * t)
            rw[i] = w * (0.11f * Math.pow((1f - t).toDouble(), 1.15).toFloat() + 0.012f)
        }
    }

    override fun build() {
        computeRoad(width.toFloat(), height.toFloat())
        glow.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xAAFF3B30.toInt(), 0x33FF3B30, Color.TRANSPARENT), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
    }

    override fun paintAll(k: PaintKit): PaintResult {
        computeRoad(k.w, k.h)
        return PaintResult(arrayOf(peaks(k), slope(k)))
    }

    private fun peaks(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(231)
        k.mountain(c, Ridge(k.width, h * 0.44f, h * 0.2f, true, 31), h * 0.62f,
            l.distant(l.ground(0xFF9AAABF.toInt()), 0.35f), l.distant(l.ground(0xFF62748C.toInt()), 0.35f), rnd, snowLine = h * 0.36f)
        k.haze(c, h * 0.46f, h * 0.62f, 0.45f)
        return bmp
    }

    private fun slope(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(232)
        k.mountain(c, Ridge(k.width, h * 0.46f, h * 0.08f, true, 32), h * 0.97f,
            l.ground(0xFF7A8C72.toInt()), l.ground(0xFF44563E.toInt()), rnd)
        val litP = l.foliage(0xFF3C6A4C.toInt()); val darkP = l.foliage(0xFF1C3A2A.toInt())
        for (i in 0 until 70) {
            val x = w * rnd.nextFloat(); val y = h * (0.52f + 0.4f * rnd.nextFloat())
            k.simplePine(c, x, y, h * (0.02f + 0.03f * (y / h - 0.4f)), k.jitter(litP, rnd, 0.2f), darkP, rnd)
        }
        // The road, drawn from far to near as quadrilaterals.
        for (i in n - 1 downTo 0) {
            val p = Path()
            p.moveTo(rx[i] - rw[i], ry[i]); p.lineTo(rx[i] + rw[i], ry[i]); p.lineTo(rx[i + 1] + rw[i + 1], ry[i + 1]); p.lineTo(rx[i + 1] - rw[i + 1], ry[i + 1]); p.close()
            k.fill.color = l.ground(if (i % 2 == 0) 0xFF4A4C52.toInt() else 0xFF45474D.toInt())
            c.drawPath(p, k.fill)
            // Edge lines and guard posts.
            k.line.strokeWidth = max4(1f, rw[i] * 0.05f); k.line.color = k.alpha(Color.WHITE, 150)
            c.drawLine(rx[i] - rw[i] * 0.92f, ry[i], rx[i + 1] - rw[i + 1] * 0.92f, ry[i + 1], k.line)
            c.drawLine(rx[i] + rw[i] * 0.92f, ry[i], rx[i + 1] + rw[i + 1] * 0.92f, ry[i + 1], k.line)
            if (i % 2 == 0) {
                k.line.strokeWidth = max4(1f, rw[i] * 0.07f); k.line.color = k.alpha(0xFFF2D26B.toInt(), 220)
                c.drawLine(rx[i], ry[i], rx[i + 1], ry[i + 1], k.line)
            }
            if (i % 4 == 0) {
                k.line.strokeWidth = max4(1.2f, rw[i] * 0.06f); k.line.color = l.shade(0xFFD6D6D6.toInt())
                c.drawLine(rx[i] + rw[i] * 1.12f, ry[i], rx[i] + rw[i] * 1.12f, ry[i] - rw[i] * 0.35f, k.line)
            }
        }
        k.haze(c, h * 0.48f, h * 0.6f, 0.3f)
        return bmp
    }

    private fun max4(a: Float, b: Float) = if (a > b) a else b

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != layers.size) return
        val t = timeMs / 1000f
        val u = ((t % 22f) / 20f).coerceAtMost(1f)
        val f = u * n
        val i = f.toInt().coerceIn(0, n - 1)
        val a = f - i
        val x = rx[i] + (rx[i + 1] - rx[i]) * a; val y = ry[i] + (ry[i + 1] - ry[i]) * a; val hw = rw[i] + (rw[i + 1] - rw[i]) * a
        val s = hw * 0.55f
        val dark = lighting.windowsLit
        canvas.save(); canvas.translate(shift(0.5f), 0f)
        carPaint.color = lighting.shade(0xFFC23B30.toInt())
        canvas.drawRoundRect(x - s, y - s * 0.55f, x + s, y, s * 0.15f, s * 0.15f, carPaint)
        carPaint.color = lighting.shade(0xFF2E3A48.toInt())
        canvas.drawRect(x - s * 0.7f, y - s * 0.95f, x + s * 0.7f, y - s * 0.55f, carPaint)
        carPaint.color = if (dark) 0xFFFF3B30.toInt() else lighting.shade(0xFF8A1F18.toInt())
        canvas.drawRect(x - s * 0.9f, y - s * 0.42f, x - s * 0.5f, y - s * 0.25f, carPaint)
        canvas.drawRect(x + s * 0.5f, y - s * 0.42f, x + s * 0.9f, y - s * 0.25f, carPaint)
        if (dark) {
            glow.alpha = 220
            for (sx in floatArrayOf(-0.7f, 0.7f)) { canvas.save(); canvas.translate(x + s * sx, y - s * 0.33f); canvas.scale(s * 0.9f, s * 0.9f); canvas.drawCircle(0f, 0f, 1f, glow); canvas.restore() }
        }
        canvas.restore()
    }
}

/** A dirt path through fields and flowers with a cyclist pedalling along it. */
class CountryCycleLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.44f, 0.68f, 0.08f), PaintLayer(0.52f, 0.78f, 0.4f), PaintLayer(0.40f, 1f, 0.9f))
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val f = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun pathY(h: Float, xFrac: Float) = h * 0.735f + h * 0.02f * sin(xFrac * 4.2f + 0.5f)

    override fun build() {}
    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(hills(k), fields(k), meadow(k)))

    private fun hills(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(241)
        k.mountain(c, Ridge(k.width, h * 0.58f, h * 0.06f, false, 33), h * 0.68f,
            l.distant(l.ground(0xFF7FA088.toInt()), 0.5f), l.distant(l.ground(0xFF4E7258.toInt()), 0.5f), rnd)
        k.haze(c, h * 0.52f, h * 0.68f, 0.45f)
        return bmp
    }

    private fun fields(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(242)
        val ridge = Ridge(k.width, h * 0.62f, h * 0.025f, false, 34)
        k.hill(c, ridge, h * 0.78f, l.distant(l.ground(0xFF8DB46E.toInt()), 0.3f), l.distant(l.ground(0xFF5E8F50.toInt()), 0.3f), rnd, 200)
        k.fields(c, ridge, h * 0.78f, rnd, 6)
        val lit = l.distant(l.foliage(0xFF5C9A58.toInt()), 0.25f); val dark = l.distant(l.foliage(0xFF28553A.toInt()), 0.25f)
        for (i in 0 until 7) {
            val x = w * (0.05f + 0.9f * rnd.nextFloat())
            k.broadleaf(c, x, ridge.yAt(x) + h * 0.02f, h * (0.045f + 0.03f * rnd.nextFloat()), k.jitter(lit, rnd, 0.2f), dark, dark, rnd, 0.2f)
        }
        k.haze(c, h * 0.62f, h * 0.76f, 0.3f)
        return bmp
    }

    private fun meadow(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(243)
        val ridge = Ridge(k.width, h * 0.7f, h * 0.012f, false, 35)
        k.hill(c, ridge, h, l.ground(0xFF6FAF55.toInt()), l.ground(0xFF2F6A2C.toInt()), rnd, 0)
        // The dirt path, a gently winding band.
        val steps = 40
        val path = Path()
        for (i in 0..steps) { val x = -k.margin + (w + 2 * k.margin) * i / steps; val y = pathY(h, x / w) - h * 0.012f; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        for (i in steps downTo 0) { val x = -k.margin + (w + 2 * k.margin) * i / steps; path.lineTo(x, pathY(h, x / w) + h * 0.022f) }
        path.close()
        k.shader = LinearGradient(0f, h * 0.7f, 0f, h * 0.78f, l.ground(0xFFD2B48A.toInt()), l.ground(0xFFA8895F.toInt()), Shader.TileMode.CLAMP)
        c.drawPath(path, k.fill); k.shader = null
        k.line.strokeWidth = 1.4f * k.unit
        repeat(90) {
            val x = w * rnd.nextFloat(); val y = pathY(h, x / w) + h * (-0.01f + 0.03f * rnd.nextFloat())
            k.line.color = k.alpha(if (rnd.nextBoolean()) 0xFF6A5030.toInt() else Color.WHITE, 60)
            c.drawLine(x, y, x + 8f * k.unit, y + h * 0.001f, k.line)
        }
        k.grass(c, ridge, h, 2200, l.ground(0xFF86BC58.toInt()), l.ground(0xFF234A1C.toInt()), rnd)
        k.flowers(c, ridge, h, 70, rnd)
        // A wooden fence along the far side of the path.
        k.line.strokeWidth = 3f * k.unit; k.line.color = l.shade(0xFF8A6A48.toInt())
        var x = w * 0.0f
        while (x < w * 1.02f) { val y = pathY(h, x / w) - h * 0.016f; c.drawLine(x, y, x, y - h * 0.02f, k.line); x += w * 0.045f }
        // A big tree on the left.
        val lit = l.foliage(0xFF4E9A3E.toInt()); val dark = l.foliage(0xFF1E4A24.toInt())
        k.broadleaf(c, w * 0.08f, h * 0.7f, h * 0.24f, lit, dark, l.shade(0xFF4A3628.toInt()), rnd, 0.7f)
        return bmp
    }

    /** A side-on cyclist whose wheels turn and legs pedal; [s] is its height in pixels. */
    private fun cyclist(c: Canvas, x: Float, ground: Float, s: Float, ph: Float) {
        val lt = lighting
        val r = s * 0.2f
        val rear = x - s * 0.28f; val front = x + s * 0.28f; val axle = ground - r
        p.strokeWidth = s * 0.022f; p.color = lt.shade(0xFF14171C.toInt())
        for (wx in floatArrayOf(rear, front)) {
            c.drawCircle(wx, axle, r, p)
            for (sp in 0 until 3) { val a = ph + sp * PI.toFloat() / 3f; c.drawLine(wx - cos(a) * r, axle - sin(a) * r, wx + cos(a) * r, axle + sin(a) * r, p.apply { strokeWidth = s * 0.008f }) }
            p.strokeWidth = s * 0.022f
        }
        val bbx = x; val bby = axle
        val seatX = x - s * 0.1f; val seatY = axle - s * 0.28f
        val headX = x + s * 0.22f; val headY = axle - s * 0.3f
        p.strokeWidth = s * 0.03f; p.color = lt.shade(0xFF2F5C8A.toInt())
        c.drawLine(bbx, bby, seatX, seatY, p); c.drawLine(seatX, seatY, rear, axle, p); c.drawLine(bbx, bby, rear, axle, p)
        c.drawLine(bbx, bby, headX, headY, p); c.drawLine(seatX, seatY, headX, headY, p); c.drawLine(headX, headY, front, axle, p)
        p.color = lt.shade(0xFF14171C.toInt()); c.drawLine(headX, headY, headX - s * 0.04f, headY - s * 0.06f, p)
        // Rider.
        val shX = x + s * 0.06f; val shY = axle - s * 0.66f
        p.strokeWidth = s * 0.07f; p.color = lt.shade(0xFFD9442F.toInt()); c.drawLine(seatX, seatY - s * 0.02f, shX, shY, p)
        f.color = lt.shade(0xFFE2B48C.toInt()); c.drawCircle(shX + s * 0.05f, shY - s * 0.08f, s * 0.065f, f)
        f.color = lt.shade(0xFFF2EDE0.toInt()); c.drawArc(RectF(shX - s * 0.01f, shY - s * 0.16f, shX + s * 0.11f, shY - s * 0.02f), 180f, 180f, true, f)
        p.strokeWidth = s * 0.035f; p.color = lt.shade(0xFFE2B48C.toInt()); c.drawLine(shX, shY + s * 0.02f, headX - s * 0.04f, headY - s * 0.06f, p)
        // Legs on pedals.
        val pr = s * 0.07f
        for (k in 0..1) {
            val a = ph * 2f + k * PI.toFloat()
            val fx = bbx + cos(a) * pr; val fy = bby + sin(a) * pr
            val kx = (seatX + fx) / 2f + s * 0.07f; val ky = (seatY + fy) / 2f - s * 0.02f
            p.strokeWidth = s * 0.045f; p.color = lt.shade(if (k == 0) 0xFF2E3A5A.toInt() else 0xFF222C46.toInt())
            c.drawLine(seatX, seatY, kx, ky, p); c.drawLine(kx, ky, fx, fy, p)
        }
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != layers.size) return
        val w = width.toFloat(); val h = height.toFloat()
        val t = timeMs / 1000f
        val u = (t % 16f) / 13f
        if (u > 1f) return
        val x = -w * 0.12f + u * w * 1.24f
        canvas.save(); canvas.translate(shift(0.9f), 0f)
        cyclist(canvas, x, pathY(h, x / w) + h * 0.012f, h * 0.085f, t * 7f)
        canvas.restore()
    }
}
