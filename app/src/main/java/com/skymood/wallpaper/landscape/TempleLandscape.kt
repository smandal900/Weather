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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Where the stairway lamps stand, as fractions of the climb (0 at the gate, 1 at the foot). */
private val LAMP_T = floatArrayOf(0.10f, 0.27f, 0.44f, 0.61f, 0.78f, 0.93f)

/** The ground is seen from low down, so distances along the screen's y axis are squashed by this much. */
private const val FORESHORTEN = 0.5f

/**
 * A hilltop temple. A winding stone stairway climbs a wooded hill to a walled terrace; on it stand
 * a pillared hall under a stepped roof, a ribbed curvilinear shikhara tower flanked by smaller
 * towers, two side shrines and corner chhatris. A saffron flag flies from the tower, and lamps
 * glow on the stairs and in the hall once it is dark.
 */
class TempleHilltopLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.34f, 0.80f, 0.08f), PaintLayer(0.20f, 0.92f, 0.5f), PaintLayer(0.74f, 1f, 1f))

    private val flag = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF8A1E.toInt() }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flagPath = Path()
    private val lampX = FloatArray(LAMP_T.size)
    private val lampY = FloatArray(LAMP_T.size)

    private var slots: List<com.skymood.wallpaper.fauna.GroundSlot> = emptyList()
    private var slotsFor = 0
    override fun groundSlots(): List<com.skymood.wallpaper.fauna.GroundSlot> {
        if (slotsFor != width * 31 + height) {
            slotsFor = width * 31 + height
            val h = height.toFloat()
            val r = fgRidge(width, h)
            // Sacred cattle and a stray dog in the meadow below the stairway, goats, and pigeons on the terrace.
            slots = listOf(
                com.skymood.wallpaper.fauna.GroundSlot(com.skymood.wallpaper.fauna.Species.COW, 0.05f, 0.45f, com.skymood.wallpaper.fauna.GroundY { x -> r.yAt(x) + h * 0.04f }, 1.35f, 1),
                com.skymood.wallpaper.fauna.GroundSlot(com.skymood.wallpaper.fauna.Species.GOAT, 0.55f, 0.95f, com.skymood.wallpaper.fauna.GroundY { x -> r.yAt(x) + h * 0.045f }, 1.2f, 2),
                com.skymood.wallpaper.fauna.GroundSlot(com.skymood.wallpaper.fauna.Species.DOG, 0.2f, 0.8f, com.skymood.wallpaper.fauna.GroundY { x -> r.yAt(x) + h * 0.055f }, 1.2f, 1),
                com.skymood.wallpaper.fauna.GroundSlot(com.skymood.wallpaper.fauna.Species.GULL, 0.3f, 0.82f, com.skymood.wallpaper.fauna.GroundY { _ -> h * 0.588f }, 1f, 4),
            )
        }
        return slots
    }

    // ------------------------------------------------------------------ layout

    private fun cxOf(w: Float) = w * 0.56f
    private fun platOf(h: Float) = h * 0.56f
    private fun wallOf(h: Float) = h * 0.03f
    private fun sanctumTopOf(h: Float) = platOf(h) - h * 0.10f
    private fun towerOf(h: Float) = h * 0.15f
    private fun crownTopOf(h: Float) = sanctumTopOf(h) - towerOf(h) * 1.22f
    private fun poleTopOf(h: Float) = crownTopOf(h) - h * 0.045f

    /** The hill: a flat terrace at the top, a rounded shoulder, a steep face and a gentle foot. */
    private fun hillY(x: Float, w: Float, h: Float): Float {
        val d = x - cxOf(w)
        val reach = if (d < 0f) w * 0.55f else w * 0.42f
        val s = ((abs(d) - w * 0.29f) / reach).coerceIn(0f, 1f)
        val g = s * s * (3f - 2f * s)
        val top = platOf(h) + wallOf(h)
        val ripple = sin(x / w * 17f) * 0.6f + sin(x / w * 41f + 1.3f) * 0.4f
        return top + (h * 0.82f - top) * g + h * 0.022f * g * (1f - g * 0.3f) * ripple
    }

    private fun fgRidge(width: Int, h: Float) = Ridge(width, h * 0.91f, h * 0.08f, false, 191)

    /** The switchback stairway, parametrised from the gate (t = 0) down to the meadow (t = 1). */
    private class Stairs(val w: Float, val h: Float, val cx: Float, val y0: Float, val y1: Float) {
        private val n = 90
        private val px = FloatArray(n + 1)
        private val py = FloatArray(n + 1)

        init {
            for (i in 0..n) { px[i] = x(i / n.toFloat()); py[i] = y(i / n.toFloat()) }
        }

        fun x(t: Float) = cx + w * (0.012f + 0.16f * t.pow(1.1f)) * sin(2f * PI.toFloat() * 1.15f * t)
        fun y(t: Float) = y0 + (y1 - y0) * t
        fun half(t: Float) = w * (0.02f + 0.032f * t)

        /** Writes the centre (0, 1), left edge (2, 3) and right edge (4, 5) of the stairway at [t]. */
        fun at(t: Float, o: FloatArray) {
            val ta = max(0f, t - 0.006f); val tb = min(1f, t + 0.006f)
            val gdx = x(tb) - x(ta)
            val gdy = (y(tb) - y(ta)) / FORESHORTEN
            val len = sqrt(gdx * gdx + gdy * gdy).coerceAtLeast(1e-3f)
            val hw = half(t)
            val ox = -gdy / len * hw
            val oy = gdx / len * hw * FORESHORTEN
            val cx = x(t); val cy = y(t)
            o[0] = cx; o[1] = cy; o[2] = cx + ox; o[3] = cy + oy; o[4] = cx - ox; o[5] = cy - oy
        }

        /** True when something [halfW] wide standing between [top] and [bottom] would hide the stairway. */
        fun blocks(x: Float, top: Float, bottom: Float, halfW: Float): Boolean {
            for (i in 0..n) if (py[i] in top..bottom && abs(px[i] - x) < halfW + half(i / n.toFloat())) return true
            return false
        }
    }

    private fun makeStairs(w: Float, h: Float): Stairs {
        val y0 = platOf(h) + wallOf(h) * 0.85f
        val probe = Stairs(w, h, cxOf(w), y0, h * 0.9f)
        val end = fgRidge(w.toInt(), h).yAt(probe.x(1f)) + h * 0.004f
        return Stairs(w, h, cxOf(w), y0, end)
    }

    private fun lampPos(st: Stairs, i: Int, o: FloatArray, out: FloatArray) {
        val t = LAMP_T[i]
        st.at(t, o)
        val e = if (i % 2 == 0) 2 else 4
        out[0] = o[e]; out[1] = o[e + 1]; out[2] = st.h * 0.016f * (0.7f + 0.8f * t)
    }

    private fun strokePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    // ---------------------------------------------------------------- lifecycle

    override fun build() {
        glowPaint.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xCCFFC060.toInt(), 0x44FF9A30, Color.TRANSPARENT), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        val st = makeStairs(width.toFloat(), height.toFloat())
        val o = FloatArray(6); val p = FloatArray(3)
        for (i in LAMP_T.indices) {
            lampPos(st, i, o, p)
            lampX[i] = p[0]; lampY[i] = p[1] - p[2]
        }
    }

    override fun onLightingChanged() { flag.color = lighting.shade(0xFFFF8A1E.toInt()) }

    override fun paintAll(k: PaintKit): PaintResult {
        val st = makeStairs(k.w, k.h)
        return PaintResult(arrayOf(far(k), temple(k, st), fore(k, st)))
    }

    // -------------------------------------------------------------- far hills

    private fun far(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(181)
        k.mountain(c, Ridge(k.width, h * 0.58f, h * 0.13f, true, 19), h * 0.8f,
            l.distant(l.ground(0xFF8A9CB8.toInt()), 0.55f), l.distant(l.ground(0xFF566A88.toInt()), 0.55f), rnd)
        k.haze(c, h * 0.5f, h * 0.7f, 0.45f)
        k.hill(c, Ridge(k.width, h * 0.68f, h * 0.07f, false, 27), h * 0.8f,
            l.distant(l.ground(0xFF6C9A74.toInt()), 0.35f), l.distant(l.ground(0xFF3E6A4C.toInt()), 0.35f), rnd, 250)
        k.haze(c, h * 0.6f, h * 0.8f, 0.35f)
        return bmp
    }

    // ------------------------------------------------------- hill and temple

    private fun temple(k: PaintKit, st: Stairs): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val rnd = Random(182)
        hillBody(k, c, st, rnd)
        wall(k, c)
        stairway(k, c, st)
        scrub(k, c, st, rnd)
        complex(k, c)
        return bmp
    }

    private fun hillBody(k: PaintKit, c: Canvas, st: Stairs, rnd: Random) {
        val l = k.l; val h = k.h; val w = k.w; val u = k.unit
        val top = platOf(h) + wallOf(h)
        val bottom = h * 0.92f
        val body = Path()
        body.moveTo(-k.margin, bottom)
        var x = -k.margin
        while (x <= w + k.margin) { body.lineTo(x, hillY(x, w, h)); x += 6f * u }
        body.lineTo(w + k.margin, bottom)
        body.close()
        val grassTop = l.ground(0xFF6FA24F.toInt()); val grassLow = l.ground(0xFF2C5E33.toInt())
        k.shader = LinearGradient(0f, top, 0f, bottom, grassTop, grassLow, Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null

        c.save(); c.clipPath(body)
        // Light on the side facing the sun, shade on the other.
        val warm = 0xFFFFF2C0.toInt()
        val sunSide = k.alpha(warm, 70); val sunClear = k.alpha(warm, 0)
        val shadeSide = k.alpha(Color.BLACK, 85); val shadeClear = k.alpha(Color.BLACK, 0)
        k.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(if (k.sunOnLeft) sunSide else shadeSide, if (k.sunOnLeft) sunClear else shadeClear, if (k.sunOnLeft) shadeClear else sunClear, if (k.sunOnLeft) shadeSide else sunSide),
            floatArrayOf(0f, 0.38f, 0.62f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(-k.margin, 0f, w + k.margin, bottom, k.fill)
        k.shader = null

        // Terraced contours that follow the slope.
        val sp = strokePaint()
        sp.strokeWidth = 1.4f * u
        for (i in 1..7) {
            val off = h * (0.018f + 0.032f * i)
            val p = Path()
            x = -k.margin
            var first = true
            while (x <= w + k.margin) {
                val y = hillY(x, w, h) + off * (1f + 0.15f * sin(x * 0.01f + i))
                if (first) { p.moveTo(x, y); first = false } else p.lineTo(x, y)
                x += 12f * u
            }
            sp.color = k.alpha(if (i % 2 == 0) Color.BLACK else Color.WHITE, 16)
            c.drawPath(p, sp)
        }
        // Grass tufts, larger towards the viewer.
        val lo = k.darken(grassLow, 0.2f); val hi = k.lighten(grassTop, 0.2f)
        repeat((900 * u).toInt()) {
            val tx = -k.margin + rnd.nextFloat() * (w + 2 * k.margin)
            val ty0 = hillY(tx, w, h)
            val d = rnd.nextFloat() * rnd.nextFloat()
            val ty = ty0 + (bottom - ty0) * d
            val bh = h * (0.003f + 0.008f * d)
            k.line.strokeWidth = (1f + 1.3f * rnd.nextFloat()) * u
            k.line.color = k.mix(lo, hi, rnd.nextFloat())
            c.drawLine(tx, ty, tx + (rnd.nextFloat() - 0.5f) * bh, ty - bh, k.line)
        }
        c.restore()

        // Boulders and rocky outcrops where the slope is steep.
        val rockCol = l.ground(0xFF8D8272.toInt())
        var placed = 0; var tries = 0
        while (placed < 12 && tries < 80) {
            tries++
            val rx = -k.margin * 0.5f + rnd.nextFloat() * (w + k.margin)
            if (abs(rx - cxOf(w)) < w * 0.31f) continue
            val size = w * (0.012f + 0.024f * rnd.nextFloat())
            val ry = hillY(rx, w, h) + h * (0.004f + 0.05f * rnd.nextFloat())
            if (st.blocks(rx, ry - size, ry, size * 1.3f)) continue
            k.rock(c, rx, ry, size, rockCol, rnd)
            placed++
        }
    }

    /** The retaining wall under the terrace, with buttresses, lamp niches and the arched gate the stairs climb to. */
    private fun wall(k: PaintKit, c: Canvas) {
        val l = k.l; val h = k.h; val w = k.w; val u = k.unit
        val cx = cxOf(w)
        val yT = platOf(h); val yB = yT + wallOf(h) + h * 0.004f
        val x0 = cx - w * 0.325f; val x1 = cx + w * 0.325f
        val stone = l.ground(0xFFB39D7F.toInt())

        // Contact shadow on the hill below.
        k.shader = LinearGradient(0f, yB, 0f, yB + h * 0.008f, k.alpha(Color.BLACK, 70), k.alpha(Color.BLACK, 0), Shader.TileMode.CLAMP)
        c.drawRect(x0, yB, x1, yB + h * 0.008f, k.fill)
        k.shader = LinearGradient(0f, yT, 0f, yB, k.lighten(stone, 0.10f), k.darken(stone, 0.22f), Shader.TileMode.CLAMP)
        c.drawRect(x0, yT, x1, yB, k.fill)
        k.shader = LinearGradient(
            x0, 0f, x1, 0f,
            if (k.sunOnLeft) k.alpha(Color.WHITE, 26) else k.alpha(Color.BLACK, 50),
            if (k.sunOnLeft) k.alpha(Color.BLACK, 50) else k.alpha(Color.WHITE, 26), Shader.TileMode.CLAMP,
        )
        c.drawRect(x0, yT, x1, yB, k.fill)
        k.shader = null

        // Courses of dressed stone, with staggered joints.
        val course = h * 0.0075f
        k.line.strokeWidth = max(1f, 1.1f * u)
        var row = 0
        var y = yT + course
        while (y < yB) {
            k.line.color = k.alpha(Color.BLACK, 38)
            c.drawLine(x0, y, x1, y, k.line)
            k.line.color = k.alpha(Color.BLACK, 26)
            var jx = x0 + (if (row % 2 == 0) 0f else w * 0.015f)
            while (jx < x1) { c.drawLine(jx, y - course, jx, y, k.line); jx += w * 0.03f }
            y += course; row++
        }

        // Buttresses with a lamp niche between each pair.
        val bw = w * 0.016f
        val nicheCol = k.darken(stone, 0.5f)
        var bx = x0 + w * 0.012f
        while (bx < x1 - bw) {
            if (abs(bx + bw / 2f - cx) > w * 0.065f) {
                k.fill.color = k.lighten(stone, 0.12f)
                c.drawRect(bx, yT, bx + bw, yB, k.fill)
                k.fill.color = k.alpha(Color.BLACK, 40)
                if (k.sunOnLeft) c.drawRect(bx + bw * 0.65f, yT, bx + bw, yB, k.fill) else c.drawRect(bx, yT, bx + bw * 0.35f, yB, k.fill)
                val nx = bx + bw + w * 0.012f; val nw = w * 0.022f
                if (nx + nw < x1 && abs(nx + nw / 2f - cx) > w * 0.065f) {
                    val nTop = yT + (yB - yT) * 0.2f; val nBot = yB - (yB - yT) * 0.12f
                    val p = Path()
                    p.moveTo(nx, nBot); p.lineTo(nx, nTop + nw / 2f)
                    p.arcTo(RectF(nx, nTop, nx + nw, nTop + nw), 180f, 180f)
                    p.lineTo(nx + nw, nBot); p.close()
                    k.fill.color = nicheCol
                    c.drawPath(p, k.fill)
                    if (l.windowsLit) { k.fill.color = 0xFFFFC060.toInt(); c.drawCircle(nx + nw / 2f, nBot - nw * 0.3f, nw * 0.2f, k.fill) }
                }
            }
            bx += w * 0.0625f
        }

        // The gate: a lighter stone frame round a dark arched opening.
        val gw = w * 0.075f
        val m = w * 0.009f
        fun arch(ax0: Float, ax1: Float, bottom: Float, top: Float) = Path().apply {
            val r = (ax1 - ax0) / 2f
            moveTo(ax0, bottom); lineTo(ax0, top + r)
            arcTo(RectF(ax0, top, ax1, top + 2f * r), 180f, 180f)
            lineTo(ax1, bottom); close()
        }
        val gTop = yT + (yB - yT) * 0.04f
        k.fill.color = k.lighten(stone, 0.2f)
        c.drawPath(arch(cx - gw / 2f - m, cx + gw / 2f + m, yB, gTop - m), k.fill)
        val opening = arch(cx - gw / 2f, cx + gw / 2f, yB, gTop)
        k.shader = LinearGradient(0f, gTop, 0f, yB,
            if (l.windowsLit) 0xFFB36A2C.toInt() else l.shade(0xFF20160F.toInt()),
            if (l.windowsLit) 0xFFFFC060.toInt() else l.shade(0xFF5A3C26.toInt()), Shader.TileMode.CLAMP)
        c.drawPath(opening, k.fill)
        k.shader = null
        k.fill.color = k.darken(stone, 0.1f)
        c.drawRect(cx - m * 1.3f, gTop - m * 1.6f, cx + m * 1.3f, gTop - m * 0.2f, k.fill)
    }

    /** The stone stairway: a ribbon of steps with low side walls and lamp posts. */
    private fun stairway(k: PaintKit, c: Canvas, st: Stairs) {
        val l = k.l; val u = k.unit
        val o = FloatArray(6)
        val n = 120
        val lx = FloatArray(n + 1); val ly = FloatArray(n + 1); val rx = FloatArray(n + 1); val ry = FloatArray(n + 1)
        for (i in 0..n) { st.at(i / n.toFloat(), o); lx[i] = o[2]; ly[i] = o[3]; rx[i] = o[4]; ry[i] = o[5] }
        val ribbon = Path()
        ribbon.moveTo(lx[0], ly[0])
        for (i in 1..n) ribbon.lineTo(lx[i], ly[i])
        for (i in n downTo 0) ribbon.lineTo(rx[i], ry[i])
        ribbon.close()
        val tread = l.ground(0xFFDCCBA6.toInt())
        k.shader = LinearGradient(0f, st.y0, 0f, st.y1, k.darken(tread, 0.12f), k.lighten(tread, 0.04f), Shader.TileMode.CLAMP)
        c.drawPath(ribbon, k.fill)
        k.shader = null

        // Steps: a dark riser with a pale tread edge below it, spaced wider as they come nearer.
        val steps = 64
        for (i in 1 until steps) {
            val t = (i / steps.toFloat()).pow(1.12f)
            st.at(t, o)
            k.line.strokeWidth = max(1f, 1.3f * u * (0.6f + t))
            k.line.color = k.alpha(Color.BLACK, 58)
            c.drawLine(o[2], o[3], o[4], o[5], k.line)
            k.line.color = k.alpha(Color.WHITE, 46)
            val d = k.line.strokeWidth
            c.drawLine(o[2], o[3] + d, o[4], o[5] + d, k.line)
        }

        // Low side walls.
        val sp = strokePaint()
        val wallCol = l.ground(0xFF9B8A6C.toInt())
        for (side in 0..1) {
            val ex = if (side == 0) lx else rx; val ey = if (side == 0) ly else ry
            val p = Path()
            p.moveTo(ex[0], ey[0])
            for (i in 1..n) p.lineTo(ex[i], ey[i])
            sp.strokeWidth = 3.6f * u; sp.color = k.darken(wallCol, 0.25f); c.drawPath(p, sp)
            sp.strokeWidth = 2f * u; sp.color = k.lighten(wallCol, 0.18f)
            p.offset(0f, -1.6f * u); c.drawPath(p, sp)
        }

        // Lamp posts, one side then the other.
        val p3 = FloatArray(3)
        for (i in LAMP_T.indices) {
            lampPos(st, i, o, p3)
            val px = p3[0]; val py = p3[1]; val ph = p3[2]
            k.line.strokeWidth = (2f + 1.6f * LAMP_T[i]) * u
            k.line.color = l.shade(0xFF3A2A1E.toInt())
            c.drawLine(px, py, px, py - ph, k.line)
            k.fill.color = if (l.windowsLit) 0xFFFFD27A.toInt() else l.shade(0xFFD8A648.toInt())
            c.drawCircle(px, py - ph, (3f + 2.2f * LAMP_T[i]) * u, k.fill)
        }
    }

    /** Bushes and trees on the slope, kept clear of the stairway and the terrace. */
    private fun scrub(k: PaintKit, c: Canvas, st: Stairs, rnd: Random) {
        val l = k.l; val h = k.h; val w = k.w
        val top = platOf(h) + wallOf(h)
        val lit = l.foliage(0xFF4E9A3E.toInt()); val dark = l.foliage(0xFF1E4A24.toInt()); val trunk = l.shade(0xFF4A3628.toInt())

        // Shrubs.
        repeat(34) {
            val x = -k.margin + rnd.nextFloat() * (w + 2 * k.margin)
            val hy = hillY(x, w, h)
            val d = 0.05f + 0.85f * rnd.nextFloat()
            val y = hy + (h * 0.9f - hy) * d
            val r = w * (0.012f + 0.022f * rnd.nextFloat()) * (0.7f + d)
            if (y - r * 1.5f < top + h * 0.004f || st.blocks(x, y - r * 1.5f, y, r * 1.2f)) return@repeat
            // A domed bush: dark clumps behind, lighter ones on the sun's side in front.
            val sx = k.sun * r * 0.25f
            k.fill.color = k.darken(dark, 0.15f)
            c.drawCircle(x - r * 0.55f, y - r * 0.4f, r * 0.62f, k.fill)
            c.drawCircle(x + r * 0.55f, y - r * 0.38f, r * 0.58f, k.fill)
            c.drawCircle(x, y - r * 0.75f, r * 0.7f, k.fill)
            k.fill.color = k.jitter(lit, rnd, 0.25f)
            c.drawCircle(x - r * 0.5f - sx, y - r * 0.5f, r * 0.42f, k.fill)
            c.drawCircle(x - sx, y - r * 0.9f, r * 0.45f, k.fill)
            c.drawCircle(x + r * 0.5f - sx, y - r * 0.5f, r * 0.36f, k.fill)
        }

        class Tree(val x: Float, val y: Float, val th: Float)
        val list = ArrayList<Tree>()
        var tries = 0
        while (list.size < 20 && tries < 300) {
            tries++
            val x = -k.margin * 0.5f + rnd.nextFloat() * (w + k.margin)
            val hy = hillY(x, w, h)
            val d = 0.10f + 0.72f * rnd.nextFloat() * rnd.nextFloat() + 0.08f * rnd.nextFloat()
            val y = hy + (h * 0.9f - hy) * d
            val depth = ((y - top) / (h * 0.9f - top)).coerceIn(0f, 1f)
            val th = h * (0.045f + 0.08f * depth)
            if (y - th * 0.95f < top + h * 0.004f) continue
            if (st.blocks(x, y - th, y, th * 0.42f + w * 0.02f)) continue
            list.add(Tree(x, y, th))
        }
        list.sortBy { it.y }
        for (t in list) k.broadleaf(c, t.x, t.y, t.th, k.jitter(lit, rnd, 0.2f), dark, trunk, rnd, 0.35f)
    }

    // ---------------------------------------------------------- the complex

    private fun complex(k: PaintKit, c: Canvas) {
        val l = k.l; val h = k.h; val w = k.w; val u = k.unit
        val cx = cxOf(w); val plat = platOf(h)
        val sand = l.ground(0xFFE6D3AC.toInt())
        val floorY = plat - h * 0.012f
        val sTop = sanctumTopOf(h)

        for (side in intArrayOf(-1, 1)) shrine(k, c, cx + side * w * 0.235f, floorY, sand)

        // Sanctum walls: pilasters under a projecting cornice.
        val sx0 = cx - w * 0.13f; val sx1 = cx + w * 0.13f
        k.shader = LinearGradient(sx0, 0f, sx1, 0f,
            if (k.sunOnLeft) k.lighten(sand, 0.1f) else k.darken(sand, 0.3f),
            if (k.sunOnLeft) k.darken(sand, 0.3f) else k.lighten(sand, 0.1f), Shader.TileMode.CLAMP)
        c.drawRect(sx0, sTop, sx1, floorY, k.fill)
        k.shader = null
        k.fill.color = k.alpha(Color.BLACK, 40)
        for (i in 0..8) {
            val px = sx0 + (sx1 - sx0) * (i + 0.5f) / 9f
            c.drawRect(px - w * 0.0035f, sTop + h * 0.008f, px + w * 0.0035f, floorY, k.fill)
        }
        k.fill.color = k.darken(sand, 0.22f)
        c.drawRect(sx0 - w * 0.008f, sTop, sx1 + w * 0.008f, sTop + h * 0.007f, k.fill)
        k.fill.color = k.alpha(Color.BLACK, 60)
        c.drawRect(sx0, sTop + h * 0.007f, sx1, sTop + h * 0.0105f, k.fill)

        // The tower and the smaller towers clustered against it.
        val th = towerOf(h)
        for (side in intArrayOf(-1, 1)) shikhara(k, c, cx + side * w * 0.118f, sTop, w * 0.026f, th * 0.28f, sand, false)
        for (side in intArrayOf(-1, 1)) shikhara(k, c, cx + side * w * 0.088f, sTop, w * 0.04f, th * 0.52f, sand, false)
        shikhara(k, c, cx, sTop, w * 0.085f, th, sand, true)
        // Flagpole above the finial.
        k.line.strokeWidth = max(1.5f, 2f * u); k.line.color = l.shade(0xFF3A2C22.toInt())
        c.drawLine(cx, crownTopOf(h), cx, poleTopOf(h), k.line)

        mandapa(k, c, floorY, sand)

        for (side in intArrayOf(-1, 1)) chhatri(k, c, cx + side * w * 0.30f, plat - h * 0.003f, sand)

        // Parapet with merlons along the front of the terrace.
        val px0 = cx - w * 0.325f; val px1 = cx + w * 0.325f
        val par = k.darken(sand, 0.06f)
        k.shader = LinearGradient(0f, plat - h * 0.013f, 0f, plat, k.lighten(par, 0.1f), k.darken(par, 0.18f), Shader.TileMode.CLAMP)
        c.drawRect(px0, plat - h * 0.0065f, px1, plat, k.fill)
        k.shader = null
        var mx = px0
        val mw = w * 0.018f
        while (mx + mw <= px1) {
            val p = Path()
            p.moveTo(mx, plat - h * 0.0065f); p.lineTo(mx + mw * 0.12f, plat - h * 0.0135f)
            p.lineTo(mx + mw * 0.88f, plat - h * 0.0135f); p.lineTo(mx + mw, plat - h * 0.0065f); p.close()
            k.fill.color = if (k.sunOnLeft) k.lighten(par, 0.04f) else k.darken(par, 0.1f)
            c.drawPath(p, k.fill)
            mx += w * 0.03f
        }
        k.fill.color = k.alpha(Color.BLACK, 70)
        c.drawRect(px0, plat - h * 0.0008f, px1, plat, k.fill)
    }

    /** The pillared hall in front of the sanctum, under a three-tier stepped roof. */
    private fun mandapa(k: PaintKit, c: Canvas, floorY: Float, sand: Int) {
        val l = k.l; val h = k.h; val w = k.w; val u = k.unit
        val cx = cxOf(w)
        val gold = l.shade(0xFFE0B040.toInt())
        val roof = l.ground(0xFFB5683E.toInt())

        // Two plinth steps.
        k.fill.color = k.darken(sand, 0.22f)
        c.drawRect(cx - w * 0.19f, floorY - h * 0.007f, cx + w * 0.19f, floorY, k.fill)
        k.fill.color = k.darken(sand, 0.1f)
        c.drawRect(cx - w * 0.18f, floorY - h * 0.013f, cx + w * 0.18f, floorY - h * 0.007f, k.fill)

        val yp1 = floorY - h * 0.013f
        val yp0 = yp1 - h * 0.026f
        // Dark hall interior with the sanctum door glowing at the back.
        k.shader = LinearGradient(0f, yp0, 0f, yp1,
            if (l.windowsLit) 0xFF8A4A20.toInt() else l.shade(0xFF1E140E.toInt()),
            if (l.windowsLit) 0xFFFFB454.toInt() else l.shade(0xFF4A3220.toInt()), Shader.TileMode.CLAMP)
        c.drawRect(cx - w * 0.17f, yp0, cx + w * 0.17f, yp1, k.fill)
        k.shader = null
        val dw = w * 0.026f
        val door = Path()
        door.moveTo(cx - dw, yp1); door.lineTo(cx - dw, yp0 + h * 0.012f)
        door.quadTo(cx, yp0 - h * 0.002f, cx + dw, yp0 + h * 0.012f); door.lineTo(cx + dw, yp1); door.close()
        k.fill.color = if (l.windowsLit) 0xFFFFD98A.toInt() else l.shade(0xFFC8884A.toInt())
        c.drawPath(door, k.fill)

        // Eight pillars with capitals.
        for (i in 0..7) {
            val px = cx - w * 0.16f + w * 0.32f * i / 7f
            val pw = w * 0.0065f
            k.fill.color = if (k.sunOnLeft) k.lighten(sand, 0.06f) else k.darken(sand, 0.12f)
            c.drawRect(px - pw, yp0, px + pw, yp1, k.fill)
            k.fill.color = k.darken(sand, 0.12f)
            c.drawRect(px - pw * 1.6f, yp0, px + pw * 1.6f, yp0 + h * 0.003f, k.fill)
            c.drawRect(px - pw * 1.5f, yp1 - h * 0.003f, px + pw * 1.5f, yp1, k.fill)
        }
        // Beam above the pillars with a red band.
        k.fill.color = k.darken(sand, 0.08f)
        c.drawRect(cx - w * 0.18f, yp0 - h * 0.008f, cx + w * 0.18f, yp0, k.fill)
        k.fill.color = l.ground(0xFFA8452A.toInt())
        c.drawRect(cx - w * 0.18f, yp0 - h * 0.0045f, cx + w * 0.18f, yp0 - h * 0.0025f, k.fill)

        // Roof tiers.
        var yb = yp0 - h * 0.008f
        val halves = floatArrayOf(0.2f, 0.158f, 0.116f)
        val tierH = h * 0.0095f
        for (i in halves.indices) {
            val hb = w * halves[i]; val ht = hb - w * 0.022f
            val p = Path()
            p.moveTo(cx - hb, yb); p.lineTo(cx - ht, yb - tierH); p.lineTo(cx + ht, yb - tierH); p.lineTo(cx + hb, yb); p.close()
            k.shader = LinearGradient(cx - hb, 0f, cx + hb, 0f,
                if (k.sunOnLeft) k.lighten(roof, 0.14f) else k.darken(roof, 0.3f),
                if (k.sunOnLeft) k.darken(roof, 0.3f) else k.lighten(roof, 0.14f), Shader.TileMode.CLAMP)
            c.drawPath(p, k.fill)
            k.shader = null
            c.save(); c.clipPath(p)
            k.line.strokeWidth = max(1f, 0.9f * u); k.line.color = k.alpha(Color.BLACK, 30)
            var tx = cx - hb
            while (tx < cx + hb) { c.drawLine(tx, yb - tierH, tx - (hb - ht) * 0.4f, yb, k.line); tx += 5f * u }
            c.restore()
            k.fill.color = k.alpha(Color.BLACK, 70)
            c.drawRect(cx - hb, yb - tierH * 0.16f, cx + hb, yb, k.fill)
            k.fill.color = k.alpha(Color.WHITE, 60)
            c.drawRect(cx - ht, yb - tierH, cx + ht, yb - tierH * 0.88f, k.fill)
            if (l.windowsLit) {
                k.fill.color = 0xFFFFD27A.toInt()
                var dx = -hb + w * 0.012f
                while (dx < hb) { c.drawCircle(cx + dx, yb + 1.5f * u, 1.7f * u, k.fill); dx += w * 0.02f }
            }
            yb -= tierH
        }
        // Little ribbed finial on the roof.
        val ry = w * 0.014f
        k.fill.color = k.darken(sand, 0.12f)
        c.drawOval(cx - ry, yb - h * 0.006f, cx + ry, yb + h * 0.0005f, k.fill)
        k.fill.color = gold
        c.drawOval(cx - ry * 0.6f, yb - h * 0.016f, cx + ry * 0.6f, yb - h * 0.005f, k.fill)
        k.line.color = gold; k.line.strokeWidth = max(1f, 1.4f * u)
        c.drawLine(cx, yb - h * 0.016f, cx, yb - h * 0.023f, k.line)
    }

    /** A small shrine: a door in a block of stone under its own little tower. */
    private fun shrine(k: PaintKit, c: Canvas, x: Float, floorY: Float, stone: Int) {
        val l = k.l; val h = k.h; val w = k.w
        val hw = w * 0.034f; val bh = h * 0.032f
        val top = floorY - bh
        k.shader = LinearGradient(x - hw, 0f, x + hw, 0f,
            if (k.sunOnLeft) k.lighten(stone, 0.1f) else k.darken(stone, 0.3f),
            if (k.sunOnLeft) k.darken(stone, 0.3f) else k.lighten(stone, 0.1f), Shader.TileMode.CLAMP)
        c.drawRect(x - hw, top, x + hw, floorY, k.fill)
        k.shader = null
        k.fill.color = k.darken(stone, 0.22f)
        c.drawRect(x - hw * 1.12f, top - h * 0.004f, x + hw * 1.12f, top, k.fill)
        val dw = hw * 0.36f
        val door = Path()
        door.moveTo(x - dw, floorY); door.lineTo(x - dw, top + bh * 0.4f)
        door.quadTo(x, top + bh * 0.05f, x + dw, top + bh * 0.4f); door.lineTo(x + dw, floorY); door.close()
        k.fill.color = if (l.windowsLit) 0xFFFFC060.toInt() else l.shade(0xFF3A2A1E.toInt())
        c.drawPath(door, k.fill)
        shikhara(k, c, x, top - h * 0.004f, hw * 0.82f, h * 0.075f, stone, false)
    }

    /** A four-pillared pavilion under a lotus dome, standing on the terrace corners. */
    private fun chhatri(k: PaintKit, c: Canvas, x: Float, base: Float, stone: Int) {
        val l = k.l; val h = k.h; val w = k.w; val u = k.unit
        val hw = w * 0.022f; val ph = h * 0.026f
        val top = base - ph
        k.fill.color = k.darken(stone, 0.55f)
        c.drawRect(x - hw, top, x + hw, base, k.fill)
        for (i in 0..3) {
            val px = x - hw + 2f * hw * i / 3f
            k.fill.color = if (k.sunOnLeft) k.lighten(stone, 0.05f) else k.darken(stone, 0.1f)
            c.drawRect(px - w * 0.0025f, top, px + w * 0.0025f, base, k.fill)
        }
        k.fill.color = k.darken(stone, 0.1f)
        c.drawRect(x - hw * 1.2f, top - h * 0.004f, x + hw * 1.2f, top, k.fill)
        val r = hw * 1.25f; val dTop = top - h * 0.004f
        val dome = Path()
        dome.moveTo(x - r, dTop)
        dome.cubicTo(x - r, dTop - r * 1.2f, x - r * 0.35f, dTop - r * 1.5f, x, dTop - r * 1.95f)
        dome.cubicTo(x + r * 0.35f, dTop - r * 1.5f, x + r, dTop - r * 1.2f, x + r, dTop)
        dome.close()
        k.shader = LinearGradient(x - r, 0f, x + r, 0f,
            if (k.sunOnLeft) k.lighten(stone, 0.12f) else k.darken(stone, 0.3f),
            if (k.sunOnLeft) k.darken(stone, 0.3f) else k.lighten(stone, 0.12f), Shader.TileMode.CLAMP)
        c.drawPath(dome, k.fill)
        k.shader = null
        val gold = l.shade(0xFFE0B040.toInt())
        k.line.color = gold; k.line.strokeWidth = max(1f, 1.5f * u)
        c.drawLine(x, dTop - r * 1.9f, x, dTop - r * 2.5f, k.line)
        k.fill.color = gold
        c.drawCircle(x, dTop - r * 2.55f, 2.2f * u, k.fill)
        if (l.windowsLit) { k.fill.color = 0xFFFFC060.toInt(); c.drawCircle(x, base - ph * 0.3f, 2.4f * u, k.fill) }
    }

    /**
     * A curvilinear nagara tower: a bulging beehive profile cut by vertical ribs, tier lines and ribbed
     * discs jutting from the edges, crowned with a wide amalaka disc, a gold pot and a finial.
     * [rich] adds the arched niches of the main tower.
     */
    private fun shikhara(k: PaintKit, c: Canvas, x: Float, base: Float, bw: Float, hgt: Float, stone: Int, rich: Boolean) {
        val l = k.l; val u = k.unit
        val tw = bw * 0.3f
        fun half(t: Float) = tw + (bw - tw) * (1f - t.pow(1.8f))
        val n = 40
        val body = Path()
        body.moveTo(x - half(0f), base)
        for (i in 1..n) { val t = i / n.toFloat(); body.lineTo(x - half(t), base - hgt * t) }
        for (i in n downTo 0) { val t = i / n.toFloat(); body.lineTo(x + half(t), base - hgt * t) }
        body.close()
        val lightSide = k.lighten(stone, 0.14f); val shadeSide = k.darken(stone, 0.34f)
        k.shader = LinearGradient(x - bw, 0f, x + bw, 0f,
            if (k.sunOnLeft) lightSide else shadeSide, if (k.sunOnLeft) shadeSide else lightSide, Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null

        val sp = strokePaint()
        val tiers = if (rich) 9 else 6
        c.save(); c.clipPath(body)
        // A lit central band.
        val band = Path()
        for (i in 0..n) {
            val t = i / n.toFloat(); val px = x - 0.36f * half(t); val py = base - hgt * t
            if (i == 0) band.moveTo(px, py) else band.lineTo(px, py)
        }
        for (i in n downTo 0) { val t = i / n.toFloat(); band.lineTo(x + 0.36f * half(t), base - hgt * t) }
        band.close()
        k.fill.color = k.alpha(Color.WHITE, 30)
        c.drawPath(band, k.fill)
        // Vertical ribs follow the profile.
        sp.strokeWidth = max(1f, 1.5f * u)
        for (s in floatArrayOf(-0.7f, -0.36f, 0.36f, 0.7f)) {
            val p = Path()
            p.moveTo(x + s * half(0f), base)
            for (i in 1..n) { val t = i / n.toFloat(); p.lineTo(x + s * half(t), base - hgt * t) }
            sp.color = k.alpha(Color.BLACK, if (abs(s) > 0.5f) 55 else 80)
            c.drawPath(p, sp)
        }
        // Tier courses, with a fainter one halfway between.
        for (j in 1 until tiers * 2) {
            val t = j / (tiers * 2f)
            val hf = half(t); val y = base - hgt * t
            sp.strokeWidth = max(1f, (if (j % 2 == 0) 1.3f else 0.8f) * u)
            sp.color = k.alpha(Color.BLACK, if (j % 2 == 0) 62 else 26)
            c.drawLine(x - hf, y, x + hf, y, sp)
        }
        if (rich) {
            // Pointed arched niches in the central band, glowing at night.
            for ((t0, t1, sc) in arrayOf(Triple(0.05f, 0.21f, 1f), Triple(0.30f, 0.40f, 0.7f))) {
                val aw = bw * 0.16f * sc
                val yb = base - hgt * t0; val ah = hgt * (t1 - t0)
                val p = Path()
                p.moveTo(x - aw, yb); p.lineTo(x - aw, yb - ah * 0.55f)
                p.quadTo(x - aw, yb - ah * 0.95f, x, yb - ah)
                p.quadTo(x + aw, yb - ah * 0.95f, x + aw, yb - ah * 0.55f)
                p.lineTo(x + aw, yb); p.close()
                k.fill.color = k.lighten(stone, 0.22f)
                c.drawPath(p, k.fill)
                val inner = Path()
                val m = aw * 0.65f
                inner.moveTo(x - m, yb); inner.lineTo(x - m, yb - ah * 0.5f)
                inner.quadTo(x - m, yb - ah * 0.82f, x, yb - ah * 0.86f)
                inner.quadTo(x + m, yb - ah * 0.82f, x + m, yb - ah * 0.5f)
                inner.lineTo(x + m, yb); inner.close()
                k.fill.color = if (l.windowsLit) 0xFFFFC060.toInt() else l.shade(0xFF2E2018.toInt())
                c.drawPath(inner, k.fill)
            }
        }
        c.restore()

        // Ribbed discs on the edges break the smooth outline.
        for (j in 1..tiers) {
            val t = (j / (tiers + 0.6f)).coerceAtMost(0.97f)
            val hf = half(t); val y = base - hgt * t
            val rx = max(2f * u, hf * 0.17f); val ry = rx * 0.55f
            for (side in intArrayOf(-1, 1)) {
                val bx = x + side * hf * 0.98f
                k.fill.color = k.darken(stone, 0.18f)
                c.drawOval(bx - rx, y - ry, bx + rx, y + ry, k.fill)
                k.fill.color = k.alpha(k.lighten(stone, 0.2f), 200)
                c.drawOval(bx - rx * 0.8f, y - ry * 0.95f, bx + rx * 0.8f, y - ry * 0.05f, k.fill)
            }
        }

        // Crown.
        val topY = base - hgt
        val gold = l.shade(0xFFE0B040.toInt())
        val ar = tw * 1.9f
        k.fill.color = k.darken(stone, 0.2f)
        c.drawRect(x - tw * 0.9f, topY - hgt * 0.02f, x + tw * 0.9f, topY + hgt * 0.01f, k.fill)
        k.shader = LinearGradient(x - ar, 0f, x + ar, 0f,
            if (k.sunOnLeft) lightSide else shadeSide, if (k.sunOnLeft) shadeSide else lightSide, Shader.TileMode.CLAMP)
        c.drawOval(x - ar, topY - hgt * 0.06f, x + ar, topY, k.fill)
        k.shader = null
        sp.strokeWidth = max(1f, u); sp.color = k.alpha(Color.BLACK, 70)
        for (i in -3..3) c.drawLine(x + i * ar * 0.28f, topY - hgt * 0.052f, x + i * ar * 0.3f, topY - hgt * 0.006f, sp)
        k.fill.color = gold
        c.drawOval(x - tw * 0.7f, topY - hgt * 0.075f, x + tw * 0.7f, topY - hgt * 0.055f, k.fill)
        c.drawOval(x - tw * 0.8f, topY - hgt * 0.15f, x + tw * 0.8f, topY - hgt * 0.07f, k.fill)
        k.fill.color = k.alpha(Color.WHITE, 90)
        c.drawOval(x - tw * 0.5f, topY - hgt * 0.14f, x - tw * 0.1f, topY - hgt * 0.10f, k.fill)
        sp.color = gold; sp.strokeWidth = max(1.2f, 2f * u)
        c.drawLine(x, topY - hgt * 0.15f, x, topY - hgt * 0.22f, sp)
        k.fill.color = gold
        c.drawCircle(x, topY - hgt * 0.225f, max(1.2f * u, tw * 0.22f), k.fill)
    }

    // ------------------------------------------------------------- foreground

    private fun fore(k: PaintKit, st: Stairs): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(183)
        val ridge = fgRidge(k.width, h)
        val litG = l.ground(0xFF5E9A44.toInt()); val darkG = l.ground(0xFF2A5A30.toInt())
        k.hill(c, ridge, h, litG, darkG, rnd, 500)
        k.grass(c, ridge, h, 700, k.lighten(litG, 0.1f), darkG, rnd)
        k.flowers(c, ridge, h, 50, rnd)
        torana(k, c, st)
        val lit = l.foliage(0xFF4E9A3E.toInt()); val dark = l.foliage(0xFF1E4A24.toInt()); val trunk = l.shade(0xFF4A3628.toInt())
        for ((s, sz) in arrayOf(0.02f to 0.23f, 0.97f to 0.2f)) {
            k.broadleaf(c, w * s, h * 1.02f, h * (sz + 0.03f * rnd.nextFloat()), k.jitter(lit, rnd, 0.15f), dark, trunk, rnd, 0.6f)
        }
        return bmp
    }

    /** A painted gateway at the foot of the stairs, hung with marigold garlands. */
    private fun torana(k: PaintKit, c: Canvas, st: Stairs) {
        val l = k.l; val h = k.h; val w = k.w; val u = k.unit
        val o = FloatArray(6)
        st.at(1f, o)
        val x0 = o[2] - w * 0.012f; val x1 = o[4] + w * 0.012f
        val base = max(o[3], o[5]) + h * 0.006f
        val ph = h * 0.075f; val pw = w * 0.012f
        val red = l.ground(0xFFB8502A.toInt())
        val gold = l.shade(0xFFE0B040.toInt())
        for (px in floatArrayOf(x0, x1)) {
            k.shader = LinearGradient(px - pw, 0f, px + pw, 0f,
                if (k.sunOnLeft) k.lighten(red, 0.12f) else k.darken(red, 0.3f),
                if (k.sunOnLeft) k.darken(red, 0.3f) else k.lighten(red, 0.12f), Shader.TileMode.CLAMP)
            c.drawRect(px - pw, base - ph, px + pw, base, k.fill)
            k.shader = null
            k.fill.color = k.darken(red, 0.25f)
            c.drawRect(px - pw * 1.5f, base - h * 0.008f, px + pw * 1.5f, base, k.fill)
            c.drawRect(px - pw * 1.4f, base - ph - h * 0.004f, px + pw * 1.4f, base - ph, k.fill)
            k.fill.color = gold
            c.drawRect(px - pw, base - ph * 0.62f, px + pw, base - ph * 0.58f, k.fill)
        }
        // Curved lintel.
        val sp = strokePaint()
        val topY = base - ph - h * 0.004f
        val lintel = Path()
        lintel.moveTo(x0 - pw * 1.4f, topY)
        lintel.quadTo((x0 + x1) / 2f, topY - h * 0.034f, x1 + pw * 1.4f, topY)
        sp.strokeWidth = max(3f, 6f * u); sp.color = red
        c.drawPath(lintel, sp)
        sp.strokeWidth = max(1.5f, 2.4f * u); sp.color = gold
        c.drawPath(lintel, sp)
        k.fill.color = gold
        c.drawCircle((x0 + x1) / 2f, topY - h * 0.0175f, 3f * u, k.fill)
        // Garlands.
        val marigold = intArrayOf(0xFFFF9A1E.toInt(), 0xFFFFC43A.toInt(), 0xFFFF7A12.toInt())
        for (g in 0 until 3) {
            val gx0 = x0 + pw + (x1 - x0 - pw * 2f) * g / 3f
            val gx1 = x0 + pw + (x1 - x0 - pw * 2f) * (g + 1) / 3f
            for (i in 0..8) {
                val f = i / 8f
                val gx = gx0 + (gx1 - gx0) * f
                val gy = topY + h * 0.003f + h * 0.016f * sin(PI.toFloat() * f)
                k.fill.color = l.shade(marigold[(i + g) % 3])
                c.drawCircle(gx, gy, 2.6f * u, k.fill)
            }
        }
    }

    // --------------------------------------------------------------- animation

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != layers.size) return
        val w = width.toFloat(); val h = height.toFloat()
        val t = timeMs / 1000f
        val cx = cxOf(w)
        canvas.save(); canvas.translate(shift(0.5f), 0f)

        // Saffron flag waving in the wind.
        val poleTop = poleTopOf(h)
        val amp = h * (0.004f + windKmh / 40f * 0.006f)
        val len = w * 0.06f
        flagPath.rewind()
        flagPath.moveTo(cx, poleTop)
        flagPath.quadTo(cx + len / 2f, poleTop + amp * sin(t * 4f), cx + len, poleTop + h * 0.010f + amp * sin(t * 4f + 1f))
        flagPath.quadTo(cx + len / 2f, poleTop + h * 0.017f + amp * sin(t * 4f + 0.5f), cx, poleTop + h * 0.020f)
        flagPath.close()
        canvas.drawPath(flagPath, flag)

        // Lamps on the stairs and a warm glow from the hall and the gate once it is dark.
        if (lighting.windowsLit) {
            for (i in lampX.indices) {
                val fl = 0.75f + 0.25f * sin(t * 9f + i * 2.3f) * sin(t * 5.1f + i)
                glowPaint.alpha = (255 * fl).toInt().coerceIn(0, 255)
                canvas.save(); canvas.translate(lampX[i], lampY[i]); canvas.scale(w * 0.02f, w * 0.02f); canvas.drawCircle(0f, 0f, 1f, glowPaint); canvas.restore()
            }
            val fl = 0.85f + 0.15f * sin(t * 6.3f) * sin(t * 3.1f)
            glowPaint.alpha = (150 * fl).toInt().coerceIn(0, 255)
            canvas.save(); canvas.translate(cx, platOf(h) - h * 0.03f); canvas.scale(w * 0.13f, h * 0.045f); canvas.drawCircle(0f, 0f, 1f, glowPaint); canvas.restore()
            canvas.save(); canvas.translate(cx, platOf(h) + wallOf(h) * 0.6f); canvas.scale(w * 0.06f, h * 0.025f); canvas.drawCircle(0f, 0f, 1f, glowPaint); canvas.restore()
        }
        canvas.restore()
    }
}
