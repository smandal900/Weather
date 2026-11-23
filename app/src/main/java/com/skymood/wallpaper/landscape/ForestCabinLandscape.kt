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
import com.skymood.wallpaper.fauna.FlowerBed
import com.skymood.wallpaper.fauna.GroundSlot
import com.skymood.wallpaper.fauna.GroundY
import com.skymood.wallpaper.fauna.MeadowSpec
import com.skymood.wallpaper.fauna.Perch
import com.skymood.wallpaper.fauna.Species
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A log cabin in the woods: a river winds out of a dark spruce forest, past a clearing where a wooden
 * cabin stands with smoke curling from its stone chimney and a little jetty on the bank, and tumbles
 * down over boulders to the viewer. Mossy rocks, ferns and a great spruce frame the foreground. The
 * mountains mirror themselves with the sun, and the cabin turns its lit front to the light.
 */
class ForestCabinLandscape : PaintedLandscape() {
    override val layers = arrayOf(
        PaintLayer(0.2f, 0.7f, 0.04f),    // far snowy range and wooded ridge
        PaintLayer(0.4f, 0.74f, 0.12f),   // forested hills
        PaintLayer(0.46f, 0.76f, 0.28f),  // the tree wall behind the clearing
        PaintLayer(0.42f, 1f, 0.55f),     // clearing, river, jetty, cabin and the trees beside it
        PaintLayer(0.42f, 1f, 1f),        // foreground spruce, boulders and ferns
    )

    // ---------------------------------------------------------------- layout shared by paint and animation

    /** The river: its centre line, and the offset from it to either bank, sampled from far (0) to near (1). */
    private class River(val w: Float, val h: Float) {
        val n = 120
        val cx = FloatArray(n + 1); val y = FloatArray(n + 1)
        val ox = FloatArray(n + 1); val oy = FloatArray(n + 1)

        init {
            // A Catmull-Rom spline: out of the trees at the horizon, a lazy bend, then sweeping to the bottom left.
            val px = floatArrayOf(0.66f, 0.56f, 0.50f, 0.42f, 0.45f, 0.33f, 0.21f, 0.12f, 0.08f, 0.08f)
            val py = floatArrayOf(0.645f, 0.652f, 0.667f, 0.70f, 0.77f, 0.84f, 0.915f, 0.99f, 1.06f, 1.12f)
            fun cr(p0: Float, p1: Float, p2: Float, p3: Float, t: Float) = 0.5f * (
                2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t * t + (-p0 + 3f * p1 - 3f * p2 + p3) * t * t * t)
            for (i in 0..n) {
                val f = i / n.toFloat() * (px.size - 3)
                val s = f.toInt().coerceAtMost(px.size - 4)
                val t = f - s
                cx[i] = w * cr(px[s], px[s + 1], px[s + 2], px[s + 3], t)
                y[i] = h * cr(py[s], py[s + 1], py[s + 2], py[s + 3], t)
            }
            for (i in 0..n) {
                val a = max(0, i - 1); val b = min(n, i + 1)
                val dx = cx[b] - cx[a]
                val gy = (y[b] - y[a]) / 0.4f   // the ground plane is squashed vertically by perspective
                val len = max(1e-3f, hypot(dx, gy))
                val half = w * (0.012f + 0.21f * (i / n.toFloat()).pow(1.3f))
                ox[i] = -gy / len * half
                oy[i] = dx / len * half * 0.4f
            }
        }

        private fun lerp(a: FloatArray, v: Float): Float {
            val f = v.coerceIn(0f, 1f) * n
            val i = f.toInt().coerceAtMost(n - 1)
            return a[i] + (a[i + 1] - a[i]) * (f - i)
        }

        /** A point at [v] along the river and [u] across it (-1 one bank, 0 the middle, 1 the other). */
        fun x(v: Float, u: Float) = lerp(cx, v) + u * lerp(ox, v)
        fun y(v: Float, u: Float) = lerp(y, v) + u * lerp(oy, v)
        fun rightEdge(v: Float) = max(x(v, 1f), x(v, -1f))
        fun leftEdge(v: Float) = min(x(v, 1f), x(v, -1f))

        /** The v at which the centre line is [py] pixels down the screen. */
        fun vAtY(py: Float): Float {
            for (i in 0 until n) if (y[i + 1] >= py) return (i + (py - y[i]) / max(1e-3f, y[i + 1] - y[i])) / n
            return 1f
        }

        /** The river's outline, with the width scaled by [scale]. */
        fun path(scale: Float = 1f): Path {
            val p = Path()
            for (i in 0..n) { val px = cx[i] - ox[i] * scale; val py = y[i] - oy[i] * scale; if (i == 0) p.moveTo(px, py) else p.lineTo(px, py) }
            for (i in n downTo 0) p.lineTo(cx[i] + ox[i] * scale, y[i] + oy[i] * scale)
            p.close()
            return p
        }
    }

    /** Where the cabin, jetty and path sit, in pixels; a pure function of the screen size. */
    private class Plan(val w: Float, val h: Float) {
        val river = River(w, h)
        val cabinX = w * 0.71f
        val cabinY = h * 0.742f
        val cabinW = w * 0.27f
        val jettyV = river.vAtY(h * 0.795f)
        val jettyY = river.y(jettyV, 0f)
        val bankX = river.rightEdge(jettyV)
        val jettyLen = w * 0.075f
        val jetty = RectF(bankX - jettyLen, jettyY - h * 0.012f, bankX + w * 0.012f, jettyY + h * 0.026f)
        /** A sitting spot in the water for the kingfisher, clear of the rocks. */
        val perchV = 0.6f
    }

    private var plan: Plan? = null
    private var planFor = 0
    private fun plan(): Plan {
        val key = width * 31 + height
        val p = plan
        if (p != null && planFor == key) return p
        return Plan(width.toFloat(), height.toFloat()).also { plan = it; planFor = key }
    }

    private var meadowSpec: MeadowSpec? = null
    private var meadowFor = 0
    override fun meadow(): MeadowSpec {
        if (meadowSpec == null || meadowFor != width * 31 + height) {
            meadowFor = width * 31 + height
            val h = height.toFloat()
            val p = plan()
            val v = p.perchV
            val px = p.river.x(v, 0.1f); val py = p.river.y(v, 0.1f)
            meadowSpec = MeadowSpec(
                listOf(FlowerBed(0.5f, 0.98f, GroundY { h * 0.79f }, h * 0.1f, 16)),
                perch = Perch(px, py, h * 0.045f, py - h * 0.008f, 1.5f),
            )
        }
        return meadowSpec!!
    }

    private var slots: List<GroundSlot> = emptyList()
    private var slotsFor = 0
    override fun groundSlots(): List<GroundSlot> {
        if (slotsFor != width * 31 + height) {
            slotsFor = width * 31 + height
            val h = height.toFloat()
            slots = listOf(
                GroundSlot(Species.DEER, 0.02f, 0.2f, GroundY { h * 0.805f }, 0.95f, 2),
                GroundSlot(Species.HARE, 0.52f, 0.66f, GroundY { h * 0.83f }, 1f, 1),
                GroundSlot(Species.FOX, 0.76f, 0.97f, GroundY { h * 0.84f }, 1.1f, 1),
            )
        }
        return slots
    }

    // ---------------------------------------------------------------- animated parts

    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val streakLines = FloatArray(STREAKS * 4)
    private val su = FloatArray(STREAKS); private val sp = FloatArray(STREAKS); private val ss = FloatArray(STREAKS)
    private var riverPath = Path()
    private val jettyClip = RectF()
    private var unitPx = 1f

    override fun build() {
        val w = width.toFloat()
        unitPx = w / 1080f
        val pl = plan()
        riverPath = pl.river.path(0.98f)
        jettyClip.set(pl.jetty)
        val rnd = Random(411)
        for (i in 0 until STREAKS) { su[i] = rnd.nextFloat() * 2f - 1f; sp[i] = rnd.nextFloat(); ss[i] = 0.6f + rnd.nextFloat() * 0.8f }
    }

    override fun onLightingChanged() {
        streakPaint.color = lighting.shade(0xFFFFFFFF.toInt())
        glintPaint.color = lighting.shade(0xFFFFFFFF.toInt())
        // Woodsmoke: pale grey in the day, dimmer and bluer in the dark.
        val rgb = lighting.shade(0xFFD2D2D0.toInt()) and 0x00FFFFFF
        smokePaint.shader = RadialGradient(
            0f, 0f, 1f, intArrayOf(rgb or 0xB0000000.toInt(), rgb or 0x70000000, rgb), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }

    // ---------------------------------------------------------------- painting

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(far(k), hills(k), wall(k), clearing(k), foreground(k)))

    private fun far(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(401)
        k.peaks(
            c, Ridge(k.width, h * 0.62f, h * 0.19f, true, 23), h * 0.68f,
            l.distant(l.ground(0xFF8396B2.toInt()), 0.3f), l.distant(l.ground(0xFF475C76.toInt()), 0.3f), rnd,
            snowLine = h * 0.5f, snowDepth = 0.1f,
        )
        k.haze(c, h * 0.5f, h * 0.68f, 0.3f)
        val ridge = Ridge(k.width, h * 0.655f, h * 0.03f, false, 12)
        val body = Path(); ridge.addTo(body, h * 0.7f)
        k.fill.color = l.distant(l.foliage(0xFF3A5E4C.toInt()), 0.4f)
        c.drawPath(body, k.fill)
        val lit = l.distant(l.foliage(0xFF4F7A5E.toInt()), 0.4f)
        val dark = l.distant(l.foliage(0xFF2F4F3E.toInt()), 0.4f)
        var x = -k.margin
        while (x < k.w + k.margin) {
            val th = h * (0.026f + rnd.nextFloat() * 0.026f)
            k.simplePine(c, x, ridge.yAt(x) + th * 0.25f, th, k.jitter(lit, rnd, 0.18f), dark, rnd)
            x += 4f + rnd.nextFloat() * 7f
        }
        k.haze(c, h * 0.6f, h * 0.7f, 0.4f)
        return bmp
    }

    /** Wooded hills, small and hazy: two rows of spruce with the odd leafy tree. */
    private fun hills(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(412)
        val ridge = Ridge(k.width, h * 0.665f, h * 0.045f, false, 31)
        k.hill(c, ridge, h * 0.73f, l.distant(l.ground(0xFF37603F.toInt()), 0.3f), l.ground(0xFF21402B.toInt()), rnd, 60)
        val lit = l.distant(l.foliage(0xFF40785A.toInt()), 0.3f)
        val dark = l.distant(l.foliage(0xFF1A3A2C.toInt()), 0.3f)
        val bark = l.distant(l.shade(0xFF3A2C22.toInt()), 0.3f)
        val leafLit = l.distant(l.foliage(0xFF6E9C3A.toInt()), 0.3f)
        val leafDark = l.distant(l.foliage(0xFF2F5A2A.toInt()), 0.3f)
        for (row in 0 until 2) {
            var x = -k.margin + rnd.nextFloat() * 10f
            while (x < k.w + k.margin) {
                val th = h * (0.045f + rnd.nextFloat() * 0.03f) * (if (row == 0) 0.88f else 1f)
                val y = ridge.yAt(x) + th * (0.12f + row * 0.1f)
                if (rnd.nextFloat() < 0.12f) k.broadleaf(c, x, y, th * 0.85f, k.jitter(leafLit, rnd, 0.2f), leafDark, bark, rnd, 0.25f)
                else k.conifer(c, x, y, th, k.jitter(lit, rnd, 0.22f), dark, bark, rnd, 0.3f)
                x += th * (0.22f + 0.2f * rnd.nextFloat())
            }
            if (row == 0) k.haze(c, h * 0.6f, h * 0.72f, 0.2f)
        }
        k.haze(c, h * 0.62f, h * 0.74f, 0.16f)
        return bmp
    }

    /** The tree wall behind the clearing: two rows of mixed trees round a gap where the river comes out. */
    private fun wall(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(413)
        val ridge = Ridge(k.width, h * 0.655f, h * 0.01f, false, 33)
        k.hill(c, ridge, h * 0.76f, l.distant(l.ground(0xFF37603D.toInt()), 0.1f), l.ground(0xFF1B3322.toInt()), rnd, 80)
        val floor = l.foliage(0xFF07120C.toInt())
        for (row in 0 until 2) {
            val f = row.toFloat()
            val dist = 0.2f - 0.12f * f
            val base = h * (0.648f + 0.012f * f)
            val rowRidge = Ridge(k.width, base, h * 0.008f, false, 40 + row)
            val lit = l.distant(l.foliage(0xFF3C7552.toInt()), dist)
            val dark = l.distant(l.foliage(0xFF163527.toInt()), dist)
            val bark = l.distant(l.shade(0xFF3A2C22.toInt()), dist)
            val oakLit = l.distant(l.foliage(0xFF6C9A3E.toInt()), dist)
            val oakDark = l.distant(l.foliage(0xFF2C5428.toInt()), dist)
            val birchLeaf = l.distant(l.foliage(0xFF9CC247.toInt()), dist)
            val birchDark = l.distant(l.foliage(0xFF4A7A2A.toInt()), dist)
            k.shader = LinearGradient(
                0f, base - h * 0.09f, 0f, base + h * 0.01f, k.alpha(floor, 0), k.alpha(floor, 225), Shader.TileMode.CLAMP,
            )
            c.drawRect(-k.margin, base - h * 0.09f, w + k.margin, base + h * 0.01f, k.fill)
            k.shader = null
            var x = -k.margin - rnd.nextFloat() * 30f
            while (x < w + k.margin) {
                val th = h * (0.085f + 0.035f * f + 0.05f * rnd.nextFloat())
                // The nearer row leaves a gap for the river to come out of the trees.
                val gap = row == 1 && abs(x / w - 0.57f) < 0.05f
                if (!gap) {
                    val y = rowRidge.yAt(x) + th * 0.04f
                    val pick = rnd.nextFloat()
                    when {
                        pick < 0.5f -> k.conifer(c, x, y, th, k.jitter(lit, rnd, 0.24f), dark, bark, rnd, 0.8f)
                        pick < 0.68f -> k.scotsPine(c, x, y, th * 1.05f, k.jitter(lit, rnd, 0.2f), dark, k.mix(bark, 0xFFB5683A.toInt(), 0.35f), rnd, 0.5f)
                        pick < 0.85f -> k.birch(c, x, y, th * 0.85f, k.jitter(birchLeaf, rnd, 0.2f), birchDark, rnd, 0.5f)
                        pick < 0.95f -> k.broadleaf(c, x, y, th * 0.85f, k.jitter(oakLit, rnd, 0.2f), oakDark, bark, rnd, 0.4f)
                        else -> k.conifer(c, x, y, th, k.jitter(lit, rnd, 0.2f), dark, bark, rnd, 0.55f)
                    }
                }
                x += th * (0.2f + 0.2f * rnd.nextFloat())
            }
            if (row == 0) k.haze(c, base - h * 0.12f, base + h * 0.02f, 0.16f)
        }
        k.lightShafts(c, h * 0.5f, h * 0.7f, 5, rnd, 34)
        return bmp
    }

    /** The clearing: meadow, river, jetty, path, rocks and the cabin, with trees on either side. */
    private fun clearing(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[3]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(414)
        val pl = Plan(w, h)
        val ridge = Ridge(k.width, h * 0.648f, h * 0.008f, false, 35, rolling = true)
        val top = l.distant(l.ground(0xFF8EB654.toInt()), 0.1f); val base = l.ground(0xFF3E7A2C.toInt())
        k.hill(c, ridge, h, top, base, rnd, 0)
        k.groundPatches(
            c, ridge, h, rnd, 60,
            intArrayOf(
                l.ground(0xFF2E5A2A.toInt()), l.ground(0xFFA8CC62.toInt()), l.ground(0xFF6A5A38.toInt()),
                l.ground(0xFF4F8A34.toInt()), l.ground(0xFF86A84A.toInt()),
            ),
            85,
        )
        k.grassCarpet(
            c, ridge, h, rnd,
            deep = l.foliage(0xFF14301A.toInt()), lush = l.ground(0xFF3F7A33.toInt()), dry = l.ground(0xFF8A8A45.toInt()),
            lit = l.ground(0xFFA6D464.toInt()), mist = l.skyBottom, density = 0.8f,
        )
        k.wildflowers(c, ridge, h, rnd, 26)
        k.terrainShade(c, ridge, h, h * 0.05f, top, base)
        paintRiver(k, c, pl, rnd)
        paintPath(k, c, pl, rnd)
        paintJetty(k, c, pl, rnd)
        bankRocks(k, c, pl, rnd)

        // The cabin, with a shade under the nearest trees and the trees on the far side.
        k.logCabin(c, pl.cabinX, pl.cabinY, pl.cabinW, rnd)
        val bark = l.shade(0xFF4A3628.toInt())
        val spruceLit = l.foliage(0xFF3E7A52.toInt()); val spruceDark = l.foliage(0xFF12301F.toInt())
        fun spruceAt(fx: Float, fy: Float, th: Float, foot: Float, pine: Boolean = false) {
            val x = w * fx; val y = h * fy
            k.treeShadow(c, x, y, th)
            if (pine) k.scotsPine(c, x, y, th * 1.1f, l.foliage(0xFF5A8A4C.toInt()), spruceDark, l.shade(0xFF8A5A3A.toInt()), rnd, 0.9f)
            else k.conifer(c, x, y, th, k.jitter(spruceLit, rnd, 0.14f), spruceDark, bark, rnd, 0.9f)
        }
        // The wood on the far bank, left of the river, with a rocky outcrop at its foot.
        spruceAt(0.05f, 0.715f, h * 0.24f, 0.07f)
        spruceAt(0.2f, 0.73f, h * 0.17f, 0.08f, pine = true)
        spruceAt(0.285f, 0.705f, h * 0.12f, 0.08f)
        val birchLit = l.foliage(0xFFA8CC4C.toInt()); val birchDark = l.foliage(0xFF4E7F2C.toInt())
        run {
            val x = w * 0.125f; val y = h * 0.722f; val th = h * 0.14f
            k.treeShadow(c, x, y, th)
            k.birch(c, x, y, th, k.jitter(birchLit, rnd, 0.14f), birchDark, rnd, 0.8f)
        }
        val rockCol = l.ground(0xFF7B7E82.toInt()); val moss = l.foliage(0xFF4F8A3A.toInt())
        k.mossyRock(c, w * 0.19f, h * 0.762f, w * 0.075f, rockCol, moss, rnd)
        k.rock(c, w * 0.27f, h * 0.775f, w * 0.05f, l.ground(0xFF8A8C90.toInt()), rnd)
        k.mossyRock(c, w * 0.09f, h * 0.77f, w * 0.06f, l.ground(0xFF6F7377.toInt()), moss, rnd)
        k.rock(c, w * 0.145f, h * 0.775f, w * 0.04f, l.ground(0xFF94969A.toInt()), rnd)
        // Shrubs and ferns round the cabin and along the path.
        val bushLit = l.foliage(0xFF5C9440.toInt()); val bushDark = l.foliage(0xFF1F4222.toInt())
        for ((fx, fy) in arrayOf(0.54f to 0.758f, 0.865f to 0.76f, 0.9f to 0.775f)) {
            k.bush(c, w * fx, h * fy, h * 0.028f, bushLit, bushDark, rnd)
        }
        if (!l.snow) {
            val fernLit = l.foliage(0xFF6FA83C.toInt()); val fernDark = l.foliage(0xFF2A5420.toInt())
            for ((fx, fy) in arrayOf(0.565f to 0.77f, 0.83f to 0.765f, 0.88f to 0.79f, 0.31f to 0.78f)) {
                k.fern(c, w * fx, h * fy, h * (0.026f + 0.01f * rnd.nextFloat()), fernLit, fernDark, rnd)
            }
        }
        return bmp
    }

    /** The river: wet banks, clear water over a pebbly bed, boulders breaking the surface and rapids at the foot. */
    private fun paintRiver(k: PaintKit, c: Canvas, pl: Plan, rnd: Random) {
        val l = k.l; val h = k.h; val w = k.w
        val st = pl.river
        k.fill.color = k.alpha(l.ground(0xFF2E4A24.toInt()), 215)
        c.drawPath(st.path(1.12f), k.fill)
        val farW = k.mix(l.skyBottom, l.shade(0xFF4FA0B0.toInt()), 0.7f)
        val nearW = l.shade(0xFF2486A0.toInt())
        k.shader = LinearGradient(0f, h * 0.65f, 0f, h, farW, nearW, Shader.TileMode.CLAMP)
        val body = st.path(1f)
        c.drawPath(body, k.fill)
        k.shader = null
        c.save(); c.clipPath(body)
        // Shallow, tea-brown water over the bed near the banks, clear green in the middle.
        k.shader = LinearGradient(0f, 0f, w * 0.95f, 0f, k.alpha(l.shade(0xFF9A7A44.toInt()), 28), 0, Shader.TileMode.CLAMP)
        c.drawRect(-k.margin, h * 0.7f, w + k.margin, h, k.fill)
        k.shader = null
        // The bed shows through, mostly near the banks: stones, larger towards the viewer.
        val stones = intArrayOf(0xFF8B6A44.toInt(), 0xFF6F6454.toInt(), 0xFFA08561.toInt(), 0xFF4E5A5C.toInt()).map { l.shade(it) }
        repeat(380) {
            val v = rnd.nextFloat().pow(0.7f)
            val u = (if (rnd.nextBoolean()) 1f else -1f) * (0.35f + 0.65f * rnd.nextFloat())
            val x = st.x(v, u); val y = st.y(v, u)
            val r = (2f + 16f * v) * k.unit * (0.5f + rnd.nextFloat())
            k.fill.color = k.alpha(stones[rnd.nextInt(stones.size)], 45 + rnd.nextInt(45))
            c.drawOval(x - r, y - r * 0.42f, x + r, y + r * 0.42f, k.fill)
        }
        // Sky reflected down the middle; the dark of the trees reflected along each bank.
        k.fill.color = k.alpha(l.skyBottom, 70)
        c.drawPath(st.path(1f), k.fill)
        k.fill.color = k.alpha(l.skyBottom, 50)
        c.drawPath(st.path(0.4f), k.fill)
        val treeRefl = l.foliage(0xFF12301F.toInt())
        for (i in 0 until st.n) {
            val t = i / st.n.toFloat()
            k.line.color = k.alpha(treeRefl, 60)
            k.line.strokeWidth = 16f * k.unit * (0.3f + 0.9f * t)
            c.drawLine(st.cx[i] - st.ox[i] * 0.9f, st.y[i] - st.oy[i] * 0.9f, st.cx[i + 1] - st.ox[i + 1] * 0.9f, st.y[i + 1] - st.oy[i + 1] * 0.9f, k.line)
            c.drawLine(st.cx[i] + st.ox[i] * 0.9f, st.y[i] + st.oy[i] * 0.9f, st.cx[i + 1] + st.ox[i + 1] * 0.9f, st.y[i + 1] + st.oy[i + 1] * 0.9f, k.line)
            k.line.color = k.alpha(Color.BLACK, 42)
            k.line.strokeWidth = 6f * k.unit * (0.3f + 0.7f * t)
            c.drawLine(st.cx[i] - st.ox[i], st.y[i] - st.oy[i], st.cx[i + 1] - st.ox[i + 1], st.y[i + 1] - st.oy[i + 1], k.line)
            c.drawLine(st.cx[i] + st.ox[i], st.y[i] + st.oy[i], st.cx[i + 1] + st.ox[i + 1], st.y[i + 1] + st.oy[i + 1], k.line)
        }
        c.restore()
        // Rapids where the river leaves the picture at the bottom: foam in the current.
        k.fill.color = k.alpha(Color.WHITE, 200)
        repeat(30) {
            val v = 0.9f + 0.1f * rnd.nextFloat()
            val x = st.x(v, -0.95f + 1.9f * rnd.nextFloat()); val y = st.y(v, 0f)
            val rw = w * (0.01f + 0.04f * rnd.nextFloat())
            c.drawOval(x - rw, y - rw * 0.3f, x + rw, y + rw * 0.3f, k.fill)
        }
        // Boulders breaking the surface, each with a ruff of foam on its upstream side, kept clear of the perch.
        val rock = intArrayOf(0xFF59564F.toInt(), 0xFF6A5E50.toInt(), 0xFF4A4A48.toInt(), 0xFF6C706F.toInt())
        var made = 0
        while (made < 16) {
            val v = 0.3f + 0.68f * rnd.nextFloat()
            val u = rnd.nextFloat() * 1.5f - 0.75f
            if (abs(v - pl.perchV) < 0.07f && abs(u - 0.1f) < 0.5f) continue
            made++
            val x = st.x(v, u); val y = st.y(v, u)
            val size = w * (0.006f + 0.04f * v.pow(1.5f)) * (0.6f + 0.8f * rnd.nextFloat())
            k.fill.color = k.alpha(Color.WHITE, 170)
            c.drawOval(x - size * 1.25f, y - size * 0.55f, x + size * 1.25f, y + size * 0.05f, k.fill)
            val col = l.ground(rock[rnd.nextInt(rock.size)])
            if (size > w * 0.02f && rnd.nextBoolean()) k.mossyRock(c, x, y + size * 0.15f, size, col, l.foliage(0xFF4F8A3A.toInt()), rnd)
            else k.rock(c, x, y + size * 0.15f, size, col, rnd)
        }
        // Tussocks and pale pebbles along both banks.
        val light = l.ground(0xFF8CC24E.toInt()); val dark = l.ground(0xFF2A5E1E.toInt())
        for (side in intArrayOf(-1, 1)) {
            var v = 0.03f
            while (v < 1f) {
                val u = side * (1.03f + 0.12f * rnd.nextFloat())
                val size = h * (0.008f + 0.045f * v.pow(1.25f)) * (0.7f + 0.6f * rnd.nextFloat())
                k.bankTuft(c, st.x(v, u), st.y(v, u) + size * 0.15f, size, light, dark, rnd, (12 + 70 * v).toInt())
                v += 0.035f + 0.03f * rnd.nextFloat()
            }
        }
        val pale = l.ground(0xFFC9C4B8.toInt())
        repeat(26) {
            val v = 0.2f + 0.8f * rnd.nextFloat()
            val u = (if (rnd.nextFloat() < 0.5f) -1f else 1f) * (1.02f + 0.08f * rnd.nextFloat())
            val r = w * (0.003f + 0.012f * v) * (0.6f + rnd.nextFloat())
            k.fill.color = k.jitter(pale, rnd, 0.15f)
            c.drawOval(st.x(v, u) - r, st.y(v, u) - r * 0.5f, st.x(v, u) + r, st.y(v, u) + r * 0.5f, k.fill)
        }
    }

    /** A dirt path from the cabin's steps down to the jetty, worn pale in the middle. */
    private fun paintPath(k: PaintKit, c: Canvas, pl: Plan, rnd: Random) {
        val l = k.l
        val x0 = pl.cabinX; val y0 = pl.cabinY + pl.cabinW * 0.1f
        val x2 = pl.bankX + pl.w * 0.012f; val y2 = pl.jettyY + pl.h * 0.004f
        val x1 = (x0 + x2) * 0.5f + pl.w * 0.03f; val y1 = y2 + pl.h * 0.02f
        val dirt = l.ground(0xFF9C7E54.toInt()); val worn = l.ground(0xFFBFA574.toInt())
        val steps = 36
        for (pass in 0..1) {
            for (i in 0 until steps) {
                val t = i / steps.toFloat(); val t2 = (i + 1) / steps.toFloat()
                fun px(t: Float) = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * x1 + t * t * x2
                fun py(t: Float) = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * y1 + t * t * y2
                // Wider where it starts, near the steps, and narrowing as it runs off down to the water.
                val wd = pl.cabinW * (0.1f - 0.045f * t) * (if (pass == 0) 1f else 0.55f)
                k.line.strokeWidth = wd
                k.line.color = if (pass == 0) k.alpha(dirt, 235) else k.alpha(worn, 200)
                c.drawLine(px(t), py(t) * 1f, px(t2), py(t2), k.line)
            }
        }
        // Pebbles and tufts at the edges so it does not read as a flat stripe.
        repeat(34) {
            val t = rnd.nextFloat()
            val x = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * x1 + t * t * x2 + (rnd.nextFloat() - 0.5f) * pl.cabinW * 0.12f
            val y = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * y1 + t * t * y2 + (rnd.nextFloat() - 0.5f) * 4f * k.unit
            val r = (1.2f + 2.2f * rnd.nextFloat()) * k.unit
            k.fill.color = k.alpha(k.jitter(l.ground(0xFFB9B2A4.toInt()), rnd, 0.2f), 200)
            c.drawOval(x - r, y - r * 0.5f, x + r, y + r * 0.5f, k.fill)
        }
    }

    /** A little plank jetty reaching out from the bank, on posts. */
    private fun paintJetty(k: PaintKit, c: Canvas, pl: Plan, rnd: Random) {
        val l = k.l; val h = k.h
        val r = pl.jetty
        val deckTop = r.top + h * 0.004f
        val deckBot = deckTop + h * 0.007f
        val plank = l.shade(0xFFB58A56.toInt())
        val dark = l.shade(0xFF5A4028.toInt())
        // Posts, with ripples where they meet the water.
        val n = 4
        for (i in 0 until n) {
            val x = r.left + h * 0.006f + (r.width() - h * 0.02f) * i / (n - 1f)
            k.fill.color = k.alpha(Color.WHITE, 90)
            c.drawOval(x - 8f * k.unit, deckBot + h * 0.0185f, x + 8f * k.unit, deckBot + h * 0.0225f, k.fill)
            k.fill.color = dark
            c.drawRect(x - 3f * k.unit, deckBot, x + 3f * k.unit, deckBot + h * 0.021f, k.fill)
        }
        // The deck: a pale top and a dark edge, with planks across it.
        k.shader = LinearGradient(0f, deckTop, 0f, deckBot, k.lighten(plank, 0.1f), k.darken(plank, 0.15f), Shader.TileMode.CLAMP)
        c.drawRect(r.left, deckTop, r.right, deckBot, k.fill)
        k.shader = null
        k.fill.color = k.darken(plank, 0.5f)
        c.drawRect(r.left, deckBot, r.right, deckBot + h * 0.003f, k.fill)
        k.line.strokeWidth = max(1f, 1.2f * k.unit)
        k.line.color = k.alpha(Color.BLACK, 90)
        var x = r.left + 7f * k.unit
        while (x < r.right) { c.drawLine(x, deckTop, x, deckBot, k.line); x += 8f * k.unit }
        // A mooring post at the bank end with a coil of rope.
        val px = r.right - 4f * k.unit
        k.fill.color = dark
        c.drawRect(px - 4f * k.unit, deckTop - h * 0.011f, px + 4f * k.unit, deckTop + 1f, k.fill)
        k.line.strokeWidth = max(1.4f, 2f * k.unit)
        k.line.color = k.alpha(l.shade(0xFFCDB98A.toInt()), 230)
        c.drawLine(px - 4f * k.unit, deckTop - h * 0.006f, px + 4f * k.unit, deckTop - h * 0.008f, k.line)
    }

    /** Boulders at the water's edge, left and right, so the banks are stone as well as turf. */
    private fun bankRocks(k: PaintKit, c: Canvas, pl: Plan, rnd: Random) {
        val l = k.l; val w = k.w; val st = pl.river
        val pale = intArrayOf(0xFFB9B4A8.toInt(), 0xFF9C9A92.toInt(), 0xFFA8A296.toInt())
        for (i in 0 until 12) {
            val v = 0.3f + 0.7f * rnd.nextFloat()
            val side = if (i % 3 == 0) -1f else 1f
            val u = side * (1.1f + 0.1f * rnd.nextFloat())
            val x = st.x(v, u); val y = st.y(v, u)
            if (x in pl.jetty.left..pl.jetty.right + w * 0.02f && abs(y - pl.jettyY) < k.h * 0.03f) continue
            k.rock(c, x, y + 5f * k.unit, w * (0.01f + 0.035f * v.pow(1.3f)), l.ground(pale[rnd.nextInt(pale.size)]), rnd)
        }
    }

    private fun foreground(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[4]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(415)
        val bark = l.shade(0xFF4A3628.toInt())
        // The great spruce at the right edge, framing the cabin.
        val sx = w * 1.09f; val sy = h * 1.0f; val sth = h * 0.4f
        k.treeShadow(c, sx, sy, sth)
        k.conifer(c, sx, sy, sth, l.foliage(0xFF3E7A52.toInt()), l.foliage(0xFF12301F.toInt()), bark, rnd, 1f)
        // Ferns and shrubs at its foot and along the bottom.
        val fernLit = l.foliage(0xFF6FA83C.toInt()); val fernDark = l.foliage(0xFF2A5420.toInt())
        val bushLit = l.foliage(0xFF5C9440.toInt()); val bushDark = l.foliage(0xFF1F4222.toInt())
        k.bush(c, w * 0.72f, h * 0.985f, h * 0.05f, bushLit, bushDark, rnd)
        k.bush(c, w * 0.93f, h * 0.99f, h * 0.045f, bushLit, bushDark, rnd)
        if (!l.snow) {
            for (fx in floatArrayOf(0.6f, 0.66f, 0.78f, 0.84f, 0.9f, 0.97f)) {
                k.fern(c, w * fx, h * (0.95f + 0.045f * rnd.nextFloat()), h * (0.04f + 0.02f * rnd.nextFloat()), fernLit, fernDark, rnd)
            }
        }
        // Mossy boulders in and beside the river at the bottom left.
        val moss = l.foliage(0xFF4F8A3A.toInt())
        k.mossyRock(c, w * 0.04f, h * 0.975f, w * 0.1f, l.ground(0xFF787B7F.toInt()), moss, rnd)
        k.mossyRock(c, w * 0.24f, h * 1.0f, w * 0.07f, l.ground(0xFF8A8C90.toInt()), moss, rnd)
        k.rock(c, w * 0.41f, h * 1.01f, w * 0.06f, l.ground(0xFF6C706F.toInt()), rnd)
        // A fallen log lying along the bank.
        k.fallenLog(c, w * 0.5f, h * 0.915f, w * 0.2f, h * 0.011f, bark, moss, 2f, rnd)
        return bmp
    }

    // ---------------------------------------------------------------- animation

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        when (stage) {
            4 -> flowingWater(canvas)
            layers.size -> chimneySmoke(canvas)
        }
    }

    /** Current streaks and glints on the river, after the clearing and before the foreground. */
    private fun flowingWater(canvas: Canvas) {
        val pl = plan()
        val st = pl.river
        val t = timeMs / 1000f
        canvas.save()
        canvas.translate(shift(layers[3].depth), 0f)
        canvas.clipPath(riverPath)
        canvas.clipOutRect(jettyClip)
        var n = 0
        for (i in 0 until STREAKS) {
            val v = (sp[i] + t * 0.03f * ss[i]) % 1f
            val v2 = min(1f, v + 0.012f + 0.03f * v)
            streakLines[n++] = st.x(v, su[i] * 0.92f); streakLines[n++] = st.y(v, su[i] * 0.92f)
            streakLines[n++] = st.x(v2, su[i] * 0.92f); streakLines[n++] = st.y(v2, su[i] * 0.92f)
        }
        streakPaint.strokeWidth = 2.2f * unitPx
        streakPaint.alpha = 120
        canvas.drawLines(streakLines, 0, n, streakPaint)
        for (i in 0 until 26) {
            val v = ((i * 0.618f) % 1f).pow(0.8f)
            val a = sin(t * (1.4f + (i % 5) * 0.4f) + i * 2.1f)
            if (a > 0.3f) {
                glintPaint.alpha = (a * 230).toInt().coerceIn(0, 255)
                canvas.drawCircle(st.x(v, su[i] * 0.9f), st.y(v, su[i] * 0.9f), (1.2f + 2.4f * v) * unitPx, glintPaint)
            }
        }
        canvas.restore()
    }

    /** Smoke rising from the chimney, leaning away in the wind. */
    private fun chimneySmoke(canvas: Canvas) {
        val pl = plan()
        val w = width.toFloat(); val h = height.toFloat()
        val p = cabinSmokePoint(pl.cabinX, pl.cabinY, pl.cabinW, lighting.reflectionX < 0.5f)
        val t = timeMs / 1000f
        val lean = (windKmh / 30f).coerceIn(0f, 1.2f)
        canvas.save()
        canvas.translate(shift(layers[3].depth), 0f)
        for (i in 0 until PUFFS) {
            val ph = (t * 0.075f + i / PUFFS.toFloat()) % 1f
            val r = w * (0.008f + 0.042f * sqrt(ph))
            val x = p[0] + (0.012f + lean * 0.16f) * w * ph + sin(ph * 6f + i * 2.3f) * w * 0.008f * (0.3f + ph)
            val y = p[1] - ph * h * 0.17f
            val env = min(ph / 0.12f, 1f) * (1f - ph) * (1f - ph)
            smokePaint.alpha = (190 * env).toInt().coerceIn(0, 255)
            canvas.save(); canvas.translate(x, y); canvas.scale(r, r * 0.85f)
            canvas.drawCircle(0f, 0f, 1f, smokePaint); canvas.restore()
        }
        canvas.restore()
    }

    private companion object {
        const val STREAKS = 130
        const val PUFFS = 10
    }
}

// -------------------------------------------------------------------- helpers

/** A clump of grass at the water's edge: a dark mound with blades fanning out, lighter towards the tips. */
private fun PaintKit.bankTuft(c: Canvas, x: Float, y: Float, size: Float, light: Int, dark: Int, rnd: Random, blades: Int) {
    fill.color = alpha(darken(dark, 0.3f), 170)
    c.drawOval(x - size * 0.55f, y - size * 0.16f, x + size * 0.55f, y + size * 0.1f, fill)
    repeat(blades) {
        val a = (rnd.nextFloat() - 0.5f) * 2.1f
        val len = size * (0.45f + 0.55f * rnd.nextFloat())
        val bx = x + (rnd.nextFloat() - 0.5f) * size * 0.8f
        line.strokeWidth = max(1f, size * 0.035f)
        val shade = ((a * 0.4f) * sun + 0.5f + (rnd.nextFloat() - 0.5f) * 0.5f).coerceIn(0f, 1f)
        line.color = mix(dark, light, shade)
        c.drawLine(bx, y, bx + sin(a) * len * 0.85f, y - kotlin.math.cos(a) * len, line)
    }
}
