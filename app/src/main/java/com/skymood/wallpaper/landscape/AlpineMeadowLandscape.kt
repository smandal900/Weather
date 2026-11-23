package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.skymood.wallpaper.fauna.FlowerBed
import com.skymood.wallpaper.fauna.GroundSlot
import com.skymood.wallpaper.fauna.GroundY
import com.skymood.wallpaper.fauna.MeadowSpec
import com.skymood.wallpaper.fauna.Species
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * An alpine valley: a sunlit granite peak streaked with gullies and snow, a pine-clad shoulder, a
 * dark band of conifers with one great spruce, and a meadow of yellow and blue flowers running down
 * to a winding stream that glints and ripples over stones. The scene mirrors itself with the sun,
 * so the lit faces of the mountain always face the light.
 */
class AlpineMeadowLandscape : PaintedLandscape() {
    override val layers = arrayOf(
        PaintLayer(0.2f, 0.7f, 0.04f),   // far snowy range
        PaintLayer(0.2f, 0.72f, 0.1f),   // granite peak and pine-clad shoulder
        PaintLayer(0.3f, 0.74f, 0.25f),  // conifer band and the great spruce
        PaintLayer(0.6f, 1f, 0.55f),     // meadow and stream
        PaintLayer(0.7f, 1f, 1f),        // foreground tussocks and flowers
    )

    private var meadowSpec: MeadowSpec? = null
    private var meadowFor = 0
    override fun meadow(): MeadowSpec {
        if (meadowSpec == null || meadowFor != width * 31 + height) {
            meadowFor = width * 31 + height
            val h = height.toFloat()
            meadowSpec = MeadowSpec(listOf(FlowerBed(0.02f, 0.98f, GroundY { h * 0.7f }, h * 0.05f, 16)))
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
                GroundSlot(Species.DEER, 0.03f, 0.22f, GroundY { h * 0.745f }, 0.95f, 2),
                GroundSlot(Species.GOAT, 0.87f, 0.97f, GroundY { h * 0.705f }, 0.85f, 2),
            )
        }
        return slots
    }

    // ---------------------------------------------------------------- the stream, shared by paint and animation

    /** Centre line, half width and height of the stream, sampled from its far end (0) to the viewer (1). */
    private class Stream(val w: Float, val h: Float) {
        val n = 120
        val cx = FloatArray(n + 1); val y = FloatArray(n + 1)
        /** Offset from the centre line to the stream's edge, in screen pixels (perspective-squashed). */
        val ox = FloatArray(n + 1); val oy = FloatArray(n + 1)

        init {
            // A Catmull-Rom spline through points that enter from the left in the distance, swing right
            // and then curl back towards the viewer, widening as they come.
            val px = floatArrayOf(-0.3f, -0.14f, 0.15f, 0.45f, 0.74f, 0.72f, 0.45f, 0.30f, 0.32f, 0.34f)
            val py = floatArrayOf(0.665f, 0.67f, 0.682f, 0.70f, 0.745f, 0.82f, 0.89f, 0.96f, 1.04f, 1.1f)
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
                val gy = (y[b] - y[a]) / 0.4f      // the ground plane is squashed vertically by perspective
                val len = max(1e-3f, hypot(dx, gy))
                val half = w * (0.014f + 0.27f * (i / n.toFloat()).pow(1.3f))
                ox[i] = -gy / len * half
                oy[i] = dx / len * half * 0.4f
            }
        }

        fun lerp(a: FloatArray, v: Float): Float {
            val f = v.coerceIn(0f, 1f) * n
            val i = f.toInt().coerceAtMost(n - 1)
            return a[i] + (a[i + 1] - a[i]) * (f - i)
        }

        /** A point at [v] along the stream and [u] across it (-1 one bank, 0 the middle, 1 the other). */
        fun x(v: Float, u: Float) = lerp(cx, v) + u * lerp(ox, v)
        fun y(v: Float, u: Float) = lerp(y, v) + u * lerp(oy, v)

        /** The stream's outline, with the width scaled by [scale]. */
        fun path(scale: Float = 1f): Path {
            val p = Path()
            for (i in 0..n) { val x = cx[i] - ox[i] * scale; val yy = y[i] - oy[i] * scale; if (i == 0) p.moveTo(x, yy) else p.lineTo(x, yy) }
            for (i in n downTo 0) p.lineTo(cx[i] + ox[i] * scale, y[i] + oy[i] * scale)
            p.close()
            return p
        }
    }

    private var stream: Stream? = null
    private var streamPath = Path()
    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val streakLines = FloatArray(STREAKS * 4)
    private val su = FloatArray(STREAKS); private val sp = FloatArray(STREAKS); private val ss = FloatArray(STREAKS)
    private var unitPx = 1f

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat()
        unitPx = w / 1080f
        val st = Stream(w, h)
        stream = st
        streamPath = st.path(0.98f)
        val rnd = Random(311)
        for (i in 0 until STREAKS) { su[i] = rnd.nextFloat() * 2f - 1f; sp[i] = rnd.nextFloat(); ss[i] = 0.6f + rnd.nextFloat() * 0.8f }
    }

    override fun onLightingChanged() {
        streakPaint.color = lighting.shade(0xFFFFFFFF.toInt())
        glintPaint.color = lighting.shade(0xFFFFFFFF.toInt())
    }

    // ---------------------------------------------------------------- painting

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(farRange(k), peak(k), forest(k), meadowLayer(k), foreground(k)))

    private fun farRange(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(301)
        val raw = listOf(
            0.46f to 0.40f, 0.52f to 0.32f, 0.58f to 0.28f, 0.64f to 0.245f, 0.70f to 0.222f, 0.74f to 0.232f,
            0.80f to 0.246f, 0.86f to 0.238f, 0.93f to 0.222f, 1.0f to 0.212f, 1.08f to 0.20f, 1.14f to 0.2f, 1.14f to 0.42f,
        )
        val (xs, ys) = roughen(raw.map { k.mx(it.first) to k.my(it.second) }, rnd, 10f * k.unit, 7f * k.unit)
        val baseY = h * 0.66f
        val top = l.distant(l.ground(0xFFB4BFCC.toInt()), 0.34f); val bot = l.distant(l.ground(0xFF7F90A8.toInt()), 0.34f)
        val shTop = l.distant(l.ground(0xFF8190A8.toInt()), 0.34f); val shBot = l.distant(l.ground(0xFF5E6E88.toInt()), 0.34f)
        val spur = k.spurPx(rnd, listOf(0.70f to 0.226f, 0.73f to 0.28f, 0.755f to 0.34f, 0.77f to 0.42f))
        val summits = intArrayOf(nearest(xs, k.mx(0.70f)), nearest(xs, k.mx(0.93f)), nearest(xs, k.mx(1.0f)))
        k.rockMass(c, xs, ys, baseY, top, bot, shTop, shBot, listOf(spur), floatArrayOf(k.mx(0.92f)), summits,
            floatArrayOf(h * 0.06f, h * 0.045f, h * 0.05f), rnd, 90)
        // Snow lying in the high basins between the peaks.
        val snow = l.distant(l.shade(Lighting.SNOW), 0.2f)
        for (b in listOf(0.64f to 0.27f, 0.76f to 0.285f, 0.88f to 0.29f)) {
            k.snowBlob(c, k.mx(b.first), k.my(b.second), 0.07f * k.w, 0.012f * h, rnd, snow)
        }
        k.haze(c, h * 0.4f, h * 0.66f, 0.3f)
        return bmp
    }

    private fun peak(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(302)
        val raw = listOf(
            -0.14f to 0.0f, 0.0f to 0.10f, 0.05f to 0.19f, 0.10f to 0.18f, 0.17f to 0.14f, 0.25f to 0.12f, 0.33f to 0.092f,
            0.37f to 0.097f, 0.43f to 0.13f, 0.48f to 0.16f, 0.52f to 0.178f, 0.56f to 0.19f, 0.60f to 0.21f, 0.63f to 0.235f,
            0.66f to 0.30f, 0.70f to 0.36f, 0.76f to 0.42f, 0.84f to 0.46f,
        )
        val (xs, ys) = roughen(raw.map { k.mx(it.first) to k.my(it.second) }, rnd, 12f * k.unit, 10f * k.unit)
        val baseY = h * 0.66f
        val litTop = l.distant(l.ground(0xFFDCCDB2.toInt()), 0.06f); val litBot = l.distant(l.ground(0xFFA8977E.toInt()), 0.1f)
        val shTop = l.distant(l.ground(0xFF8F877D.toInt()), 0.06f); val shBot = l.distant(l.ground(0xFF5C5852.toInt()), 0.1f)
        val spurA = k.spurPx(rnd, listOf(0.36f to 0.095f, 0.40f to 0.16f, 0.44f to 0.24f, 0.47f to 0.33f, 0.49f to 0.42f))
        val spurB = k.spurPx(rnd, listOf(0.52f to 0.178f, 0.55f to 0.23f, 0.58f to 0.30f, 0.60f to 0.38f))
        val summits = intArrayOf(nearest(xs, k.mx(0.33f)), nearest(xs, k.mx(0.52f)), nearest(xs, k.mx(0.0f)))
        k.rockMass(c, xs, ys, baseY, litTop, litBot, shTop, shBot, listOf(spurA, spurB),
            floatArrayOf(k.mx(0.535f), k.mx(0.80f)), summits, floatArrayOf(h * 0.055f, h * 0.03f, h * 0.02f), rnd, 260)
        // The pine-clad shoulder sweeping down from the edge, and low foothills at the foot of the face.
        pineSlope(k, c, rnd, listOf(-0.14f to 0.19f, 0.0f to 0.2f, 0.07f to 0.26f, 0.17f to 0.31f, 0.27f to 0.37f, 0.34f to 0.43f), 300, 0xFF7C8E42.toInt())
        pineSlope(k, c, rnd, listOf(0.28f to 0.42f, 0.42f to 0.35f, 0.54f to 0.335f, 0.66f to 0.365f, 0.80f to 0.43f), 220, 0xFF6E8A3C.toInt())
        k.haze(c, h * 0.5f, h * 0.68f, 0.1f)
        return bmp
    }

    /** The forested shoulder of the mountain sweeping down from the left (or right, with the sun) edge. */
    private fun pineSlope(k: PaintKit, c: Canvas, rnd: Random, edge: List<Pair<Float, Float>>, count: Int, topCol: Int) {
        val l = k.l; val h = k.h
        fun topAt(fx: Float): Float {
            for (i in 0 until edge.size - 1) if (fx <= edge[i + 1].first) {
                val t = ((fx - edge[i].first) / (edge[i + 1].first - edge[i].first)).coerceIn(0f, 1f)
                return edge[i].second + (edge[i + 1].second - edge[i].second) * t
            }
            return edge.last().second
        }
        val bottomF = 0.47f
        val body = Path()
        body.moveTo(k.mx(edge[0].first), k.my(edge[0].second))
        for (e in edge) body.lineTo(k.mx(e.first), k.my(e.second))
        body.lineTo(k.mx(edge.last().first), k.my(bottomF)); body.lineTo(k.mx(edge[0].first), k.my(bottomF)); body.close()
        k.shader = LinearGradient(0f, k.my(edge.minOf { it.second }), 0f, k.my(bottomF), l.ground(topCol), l.foliage(0xFF1F3D24.toInt()), Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null
        c.save(); c.clipPath(body)
        val lit = l.foliage(0xFF4C7A3A.toInt()); val dark = l.foliage(0xFF14301C.toInt())
        // Pines pack tighter and grow larger down the slope, from a ragged line of scrub at the top.
        repeat(count) {
            val fx = edge[0].first + rnd.nextFloat() * (edge.last().first - edge[0].first)
            val ty = topAt(fx)
            val d = rnd.nextFloat().pow(0.8f)
            val fy = ty + (bottomF - ty) * d
            val th = h * (0.012f + 0.026f * d) * (0.8f + 0.5f * rnd.nextFloat())
            k.simplePine(c, k.mx(fx), k.my(fy) + th * 0.2f, th, k.jitter(lit, rnd, 0.2f), dark, rnd)
        }
        c.restore()
    }

    private fun forest(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(303)
        val trunk = l.shade(0xFF33261C.toInt())
        // Far row, small and misty.
        val farLit = l.distant(l.foliage(0xFF2F6040.toInt()), 0.22f); val farDark = l.distant(l.foliage(0xFF17321F.toInt()), 0.22f)
        var x = -k.margin
        while (x < w + k.margin) {
            val th = h * (0.03f + 0.04f * rnd.nextFloat())
            k.simplePine(c, x, h * (0.616f + 0.006f * rnd.nextFloat()), th, k.jitter(farLit, rnd, 0.2f), farDark, rnd)
            x += (8f + rnd.nextFloat() * 7f) * k.unit
        }
        // Near row, darker and taller, with a gap where the snow shows through between the peaks.
        val lit = l.foliage(0xFF24512F.toInt()); val dark = l.foliage(0xFF0F2619.toInt())
        x = -k.margin
        while (x < w + k.margin) {
            val gap = abs(x / w - k.mx(0.58f) / w) < 0.07f && rnd.nextFloat() < 0.2f
            if (!gap) {
                val th = h * (0.045f + 0.045f * rnd.nextFloat())
                k.conifer(c, x, h * (0.628f + 0.012f * rnd.nextFloat()), th, k.jitter(lit, rnd, 0.2f), dark, trunk, rnd, 0.5f)
            }
            x += (15f + rnd.nextFloat() * 10f) * k.unit
        }
        // The great spruce standing at the edge of the wood.
        k.conifer(c, k.mx(0.86f), h * 0.652f, h * 0.24f, l.foliage(0xFF2F6638.toInt()), l.foliage(0xFF10281A.toInt()), trunk, rnd, 1f)
        k.conifer(c, k.mx(0.97f), h * 0.64f, h * 0.17f, l.foliage(0xFF2A5E36.toInt()), l.foliage(0xFF10281A.toInt()), trunk, rnd, 0.8f)
        k.haze(c, h * 0.56f, h * 0.66f, 0.1f)
        return bmp
    }

    private fun meadowLayer(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[3]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(304)
        val ridge = Ridge(k.width, h * 0.652f, h * 0.014f, false, 31, rolling = true)
        val top = l.distant(l.ground(0xFF9CC456.toInt()), 0.12f); val base = l.ground(0xFF357A26.toInt())
        k.hill(c, ridge, h, top, base, rnd, 0)
        k.contourGrass(c, ridge, h, h * 0.36f, l.ground(0xFFA8D45C.toInt()), l.ground(0xFF2A6420.toInt()), rnd, 40)
        val patches = intArrayOf(0xFFEAD94A.toInt(), 0xFF6AB03A.toInt(), 0xFF86A8E6.toInt(), 0xFF4F9A30.toInt(), 0xFFF2D21E.toInt())
            .map { l.ground(it) }.toIntArray()
        k.groundPatches(c, ridge, h, rnd, 130, patches, 110)
        if (!l.snow) flowerDrifts(k, c, ridge, rnd)
        meadowMounds(k, c, ridge, rnd)
        k.terrainShade(c, ridge, h, h * 0.05f, top, base)
        paintStream(k, c, Stream(w, h), rnd)
        return bmp
    }

    /** Carpets of yellow and blue flowers, laid out in rows that shrink with distance. */
    private fun flowerDrifts(k: PaintKit, c: Canvas, ridge: Ridge, rnd: Random) {
        val l = k.l; val h = k.h; val w = k.w
        val yellow = l.shade(0xFFF2D21E.toInt()); val paleYellow = l.shade(0xFFFFEF7A.toInt())
        val blue = l.shade(0xFF7F9CE8.toInt()); val white = l.shade(0xFFF4F4F0.toInt())
        val rows = 120
        val y0 = ridge.yAt(w * 0.5f)
        for (r in 0 until rows) {
            val d = ((r + 0.5f) / rows).pow(1.6f)
            val y = y0 + (h - y0) * d
            val gap = (1.7f + 6.5f * d) * k.unit
            var x = -k.margin + rnd.nextFloat() * gap
            while (x < w + k.margin) {
                val a = valueNoise(x / (w * 0.16f), d * 7f, 11)
                val b = valueNoise(x / (w * 0.12f) + 40f, d * 6f, 23)
                val rad = (0.8f + 2.6f * d) * k.unit * (0.7f + 0.6f * rnd.nextFloat())
                val col = when {
                    a > 0.48f && rnd.nextFloat() < (a - 0.38f) * 3.8f -> if (rnd.nextFloat() < 0.8f) yellow else paleYellow
                    b > 0.52f && rnd.nextFloat() < (b - 0.42f) * 3.4f -> if (rnd.nextFloat() < 0.15f) white else blue
                    rnd.nextFloat() < 0.015f -> white
                    else -> 0
                }
                if (col != 0) {
                    k.fill.color = col
                    c.drawCircle(x, y + (rnd.nextFloat() - 0.5f) * gap * 1.6f, rad, k.fill)
                }
                x += gap * (0.6f + rnd.nextFloat() * 0.8f)
            }
        }
    }

    /** Clumps of long grass dotted over the meadow, bigger towards the viewer, with flowers among them. */
    private fun meadowMounds(k: PaintKit, c: Canvas, ridge: Ridge, rnd: Random) {
        val l = k.l; val h = k.h; val w = k.w
        val light = l.ground(0xFF8CC24E.toInt()); val dark = l.ground(0xFF25601C.toInt())
        val yellow = l.shade(0xFFF2D21E.toInt()); val blue = l.shade(0xFF6F8EE6.toInt())
        val y0 = ridge.yAt(w * 0.5f)
        val rows = 18
        for (r in 0 until rows) {
            val d = ((r + 0.5f) / rows).pow(1.5f)
            val y = y0 + (h - y0) * d
            repeat(3 + (r * 0.9f).toInt()) {
                val x = -k.margin + rnd.nextFloat() * (w + 2 * k.margin)
                val size = h * (0.008f + 0.045f * d) * (0.7f + 0.7f * rnd.nextFloat())
                k.tussock(c, x, y, size, light, dark, rnd, (10 + 70 * d).toInt())
                if (!l.snow) repeat(2 + (d * 6f).toInt()) {
                    k.fill.color = if (rnd.nextFloat() < 0.6f) yellow else blue
                    c.drawCircle(x + (rnd.nextFloat() - 0.5f) * size * 1.7f, y - size * rnd.nextFloat() * 0.5f, (1f + 2.4f * d) * k.unit, k.fill)
                }
            }
        }
    }

    private fun paintStream(k: PaintKit, c: Canvas, st: Stream, rnd: Random) {
        val l = k.l; val h = k.h; val w = k.w
        // Bank: a dark rim of wet earth around the water.
        k.fill.color = k.alpha(l.ground(0xFF2E4A24.toInt()), 210)
        c.drawPath(st.path(1.12f), k.fill)
        // Water, bluer and paler in the distance, deeper where it is near.
        val farW = k.mix(l.skyBottom, l.shade(0xFF3F83B8.toInt()), 0.7f)
        val nearW = l.shade(0xFF1F5C86.toInt())
        k.shader = LinearGradient(0f, h * 0.665f, 0f, h, farW, nearW, Shader.TileMode.CLAMP)
        val body = st.path(1f)
        c.drawPath(body, k.fill)
        k.shader = null
        c.save(); c.clipPath(body)
        // Warm, shallow water over a brown bed on the near side, fading to blue across the stream.
        k.shader = LinearGradient(0f, 0f, w * 0.95f, 0f, k.alpha(l.shade(0xFF9A6E3C.toInt()), 225), 0, Shader.TileMode.CLAMP)
        c.drawRect(-k.margin, h * 0.7f, w + k.margin, h, k.fill)
        k.shader = null
        // The bed shows through: brown and grey stones, larger near the viewer.
        val stones = intArrayOf(0xFF8B6A44.toInt(), 0xFF6F6454.toInt(), 0xFFA08561.toInt(), 0xFF4E5A5C.toInt()).map { l.shade(it) }
        repeat(420) {
            val v = rnd.nextFloat().pow(0.7f)
            val u = rnd.nextFloat() * 2f - 1f
            val x = st.x(v, u); val y = st.y(v, u)
            val r = (2f + 17f * v) * k.unit * (0.5f + rnd.nextFloat())
            k.fill.color = k.alpha(stones[rnd.nextInt(stones.size)], 85 + rnd.nextInt(60))
            c.drawOval(x - r, y - r * 0.42f, x + r, y + r * 0.42f, k.fill)
        }
        // A band of sky reflected down the middle, and dark water along each bank.
        k.fill.color = k.alpha(l.skyBottom, 55)
        c.drawPath(st.path(0.4f), k.fill)
        k.line.color = k.alpha(Color.BLACK, 42)
        for (i in 0 until st.n) {
            k.line.strokeWidth = 7f * k.unit * (0.3f + 0.7f * (i / st.n.toFloat()))
            c.drawLine(st.cx[i] - st.ox[i], st.y[i] - st.oy[i], st.cx[i + 1] - st.ox[i + 1], st.y[i + 1] - st.oy[i + 1], k.line)
            c.drawLine(st.cx[i] + st.ox[i], st.y[i] + st.oy[i], st.cx[i + 1] + st.ox[i + 1], st.y[i + 1] + st.oy[i + 1], k.line)
        }
        c.restore()
        // Rapids where the stream leaves the screen at the bottom: foam in the current.
        k.fill.color = k.alpha(Color.WHITE, 200)
        repeat(28) {
            val v = 0.9f + 0.1f * rnd.nextFloat()
            val x = st.x(v, -0.95f + 0.8f * rnd.nextFloat()); val y = st.y(v, 0f)
            val rw = w * (0.01f + 0.04f * rnd.nextFloat())
            c.drawOval(x - rw, y - rw * 0.3f, x + rw, y + rw * 0.3f, k.fill)
        }
        // Stones breaking the surface, each with a ruff of foam on its upstream side.
        val rock = intArrayOf(0xFF59564F.toInt(), 0xFF6A5E50.toInt(), 0xFF4A4A48.toInt())
        for (i in 0 until 18) {
            val v = 0.3f + 0.68f * rnd.nextFloat()
            val x = st.x(v, rnd.nextFloat() * 1.5f - 0.75f); val y = st.y(v, 0f)
            val size = w * (0.006f + 0.045f * v.pow(1.5f)) * (0.6f + 0.8f * rnd.nextFloat())
            k.fill.color = k.alpha(Color.WHITE, 170)
            c.drawOval(x - size * 1.25f, y - size * 0.55f, x + size * 1.25f, y + size * 0.05f, k.fill)
            k.rock(c, x, y + size * 0.15f, size, l.ground(rock[rnd.nextInt(rock.size)]), rnd)
        }
        // Tussocks and pale boulders along both banks.
        val light = l.ground(0xFF8CC24E.toInt()); val dark = l.ground(0xFF2A5E1E.toInt())
        for (side in intArrayOf(-1, 1)) {
            var v = 0.03f
            while (v < 1f) {
                val u = side * (1.02f + 0.12f * rnd.nextFloat())
                val x = st.x(v, u)
                val size = h * (0.008f + 0.05f * v.pow(1.25f)) * (0.7f + 0.6f * rnd.nextFloat())
                val y = st.y(v, u) + size * 0.15f
                k.tussock(c, x, y, size, light, dark, rnd, (14 + 90 * v).toInt())
                v += 0.03f + 0.025f * rnd.nextFloat()
            }
        }
        val pale = l.ground(0xFFC9C4B8.toInt())
        for (i in 0 until 9) {
            val v = 0.35f + 0.65f * rnd.nextFloat()
            val u = (if (rnd.nextFloat() < 0.7f) -1f else 1f) * (1.05f + 0.1f * rnd.nextFloat())
            k.rock(c, st.x(v, u), st.y(v, u) + 6f * k.unit, w * (0.012f + 0.04f * v.pow(1.3f)), pale, rnd)
        }
    }

    private fun foreground(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[4]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(305)
        val light = l.ground(0xFF8CC24E.toInt()); val dark = l.ground(0xFF24561A.toInt())
        // Boulders at the edge of the picture.
        k.rock(c, w * 0.03f, h * 0.935f, w * 0.07f, l.ground(0xFFC4BFB4.toInt()), rnd)
        k.rock(c, w * 0.15f, h * 0.99f, w * 0.05f, l.ground(0xFFB9B4A8.toInt()), rnd)
        // The big clumps of grass, flowers and lupins in the bottom corners.
        val cxm = k.mx(0.84f)
        k.tussock(c, cxm, h * 1.0f, h * 0.17f, light, dark, rnd, 340)
        k.tussock(c, k.mx(1.0f), h * 1.0f, h * 0.12f, light, dark, rnd, 170)
        k.tussock(c, k.mx(0.04f), h * 1.0f, h * 0.1f, light, dark, rnd, 150)
        k.tussock(c, k.mx(0.12f), h * 0.88f, h * 0.05f, light, dark, rnd, 90)
        if (!l.snow) {
            val yellow = l.shade(0xFFF7C928.toInt()); val blue = l.shade(0xFF5F7FE0.toInt())
            repeat(20) {
                val x = cxm + (rnd.nextFloat() - 0.3f) * w * 0.34f
                k.yellowFlower(c, x, h * (0.89f + 0.1f * rnd.nextFloat()), w * (0.014f + 0.014f * rnd.nextFloat()), yellow, rnd)
            }
            repeat(9) {
                val x = cxm + (rnd.nextFloat() - 0.5f) * w * 0.4f
                k.lupine(c, x, h * (0.97f + 0.03f * rnd.nextFloat()), h * (0.035f + 0.04f * rnd.nextFloat()), blue, rnd)
            }
            repeat(6) {
                k.lupine(c, k.mx(0.02f + 0.14f * rnd.nextFloat()), h * (0.9f + 0.1f * rnd.nextFloat()), h * (0.03f + 0.03f * rnd.nextFloat()), blue, rnd)
            }
            repeat(40) {
                k.fill.color = l.shade(if (rnd.nextBoolean()) 0xFF8AA6EC.toInt() else 0xFFF2F2EE.toInt())
                c.drawCircle(w * rnd.nextFloat(), h * (0.9f + 0.1f * rnd.nextFloat()), (1.5f + 2.5f * rnd.nextFloat()) * k.unit, k.fill)
            }
        }
        return bmp
    }

    // ---------------------------------------------------------------- animation

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != 4) return
        val st = stream ?: return
        val t = timeMs / 1000f
        canvas.save()
        canvas.translate(shift(0.55f), 0f)
        canvas.clipPath(streamPath)
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
        // Sun glints winking on the ripples.
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

    private companion object {
        const val STREAKS = 130
    }
}

// -------------------------------------------------------------------- helpers

private fun PaintKit.mx(f: Float) = (if (sunOnLeft) f else 1f - f) * w
private fun PaintKit.my(f: Float) = h * (0.27f + (f - 0.09f) * 0.92f)

private fun nearest(xs: FloatArray, x: Float): Int {
    var best = 0
    for (i in xs.indices) if (abs(xs[i] - x) < abs(xs[best] - x)) best = i
    return best
}

/** Subdivides a polyline and pushes each new point sideways at random, giving crags a broken edge. */
private fun roughen(pts: List<Pair<Float, Float>>, rnd: Random, seg: Float, amt: Float): Pair<FloatArray, FloatArray> {
    val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
    for (i in 0 until pts.size - 1) {
        val (x0, y0) = pts[i]; val (x1, y1) = pts[i + 1]
        val len = hypot(x1 - x0, y1 - y0)
        if (len < 1f) continue
        val n = max(1, (len / seg).toInt())
        val nx = -(y1 - y0) / len; val ny = (x1 - x0) / len
        for (s in 0 until n) {
            val t = s / n.toFloat()
            val off = if (s == 0) 0f else (rnd.nextFloat() - 0.5f) * amt
            xs.add(x0 + (x1 - x0) * t + nx * off); ys.add(y0 + (y1 - y0) * t + ny * off)
        }
    }
    xs.add(pts.last().first); ys.add(pts.last().second)
    return xs.toFloatArray() to ys.toFloatArray()
}

/** A spur (the crest line between a lit and a shaded face) as pixel points, given in picture fractions. */
private fun PaintKit.spurPx(rnd: Random, pts: List<Pair<Float, Float>>): List<FloatArray> {
    val (xs, ys) = roughen(pts.map { mx(it.first) to my(it.second) }, rnd, 14f * unit, 8f * unit)
    return xs.indices.map { floatArrayOf(xs[it], ys[it]) }
}

/** The x of a spur at height [y]; above the summit and below the foot it runs straight. */
private fun spurX(sp: List<FloatArray>, y: Float): Float {
    if (y <= sp.first()[1]) return sp.first()[0]
    for (i in 0 until sp.size - 1) if (y <= sp[i + 1][1]) {
        val t = (y - sp[i][1]) / max(0.001f, sp[i + 1][1] - sp[i][1])
        return sp[i][0] + (sp[i + 1][0] - sp[i][0]) * t
    }
    return sp.last()[0]
}

/**
 * A mass of rock: a lit face with a darker one beyond each spur, striated by gullies and cracks that
 * fall away from the summits, with snow caps and tongues of snow in the gullies, and a rim of light
 * along the sun-facing edge. [xs]/[ys] is the silhouette, closed down to [baseY].
 */
private fun PaintKit.rockMass(
    c: Canvas, xs: FloatArray, ys: FloatArray, baseY: Float,
    litTop: Int, litBot: Int, shadeTop: Int, shadeBot: Int,
    spurs: List<List<FloatArray>>, spurEnd: FloatArray, summits: IntArray, snowDepths: FloatArray,
    rnd: Random, striations: Int,
) {
    val body = Path()
    body.moveTo(xs[0], baseY)
    for (i in xs.indices) body.lineTo(xs[i], ys[i])
    body.lineTo(xs.last(), baseY); body.close()
    var minY = baseY
    for (v in ys) minY = min(minY, v)
    c.save()
    c.clipPath(body)
    shader = LinearGradient(0f, minY, 0f, baseY, litTop, litBot, Shader.TileMode.CLAMP)
    c.drawRect(-margin, minY - 4f, w + margin, baseY, fill)
    shader = null
    val shadePaths = ArrayList<Path>()
    for ((si, sp) in spurs.withIndex()) {
        val p = Path()
        p.moveTo(sp[0][0], minY - 4f)
        for (pt in sp) p.lineTo(pt[0], pt[1])
        p.lineTo(sp.last()[0], baseY + 4f)
        p.lineTo(spurEnd[si], baseY + 4f); p.lineTo(spurEnd[si], minY - 4f); p.close()
        shader = LinearGradient(0f, minY, 0f, baseY, shadeTop, shadeBot, Shader.TileMode.CLAMP)
        c.drawPath(p, fill)
        shader = null
        shadePaths.add(p)
    }
    fun shadedAt(x: Float, y: Float): Boolean {
        for ((si, sp) in spurs.withIndex()) {
            val sx = spurX(sp, y)
            if ((x - sx) * (spurEnd[si] - sx) > 0f && abs(x - sx) < abs(spurEnd[si] - sx)) return true
        }
        return false
    }
    val shadeDir = if (sunOnLeft) 1f else -1f
    // Mottled weathering: tall soft blotches of lighter and darker rock.
    repeat(striations / 3) {
        val i = 2 + rnd.nextInt(xs.size - 4)
        val x0 = xs[i]; val y0 = ys[i] + rnd.nextFloat() * h * 0.12f
        if (y0 > baseY) return@repeat
        val rx = (6f + 26f * rnd.nextFloat()) * unit; val ry = h * (0.03f + 0.09f * rnd.nextFloat())
        val shaded = shadedAt(x0, y0)
        val col = if (rnd.nextFloat() < 0.55f) darken(if (shaded) shadeBot else litBot, 0.5f) else lighten(if (shaded) shadeTop else litTop, 0.5f)
        c.save(); c.translate(x0, y0); c.scale(1f, ry / rx)
        shader = RadialGradient(0f, 0f, rx, alpha(col, 42 + rnd.nextInt(40)), 0, Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, rx, fill)
        shader = null
        c.restore()
    }
    // Soft weathering streaks that widen and fade as they run down the face.
    val strip = Path()
    repeat(striations / 2) {
        val i = 2 + rnd.nextInt(xs.size - 4)
        val x0 = xs[i]; val y0 = ys[i]
        if (y0 > baseY - h * 0.06f) return@repeat
        val s = summits.minByOrNull { abs(xs[it] - x0) } ?: 0
        val dx = (x0 - xs[s]) * 0.2f * (0.4f + rnd.nextFloat())
        val len = h * (0.06f + 0.22f * rnd.nextFloat())
        val wd = (4f + 22f * rnd.nextFloat()) * unit
        val shaded = shadedAt(x0, y0)
        val col = if (rnd.nextFloat() < 0.55f) darken(if (shaded) shadeBot else litBot, 0.5f) else lighten(if (shaded) shadeTop else litTop, 0.5f)
        strip.rewind()
        strip.moveTo(x0 - wd * 0.3f, y0); strip.lineTo(x0 + wd * 0.3f, y0)
        strip.lineTo(x0 + dx + wd, y0 + len); strip.lineTo(x0 + dx - wd, y0 + len); strip.close()
        shader = LinearGradient(0f, y0, 0f, y0 + len, alpha(col, 36 + rnd.nextInt(50)), alpha(col, 0), Shader.TileMode.CLAMP)
        c.drawPath(strip, fill)
        shader = null
    }
    // Ribs: jagged secondary spurs on the lit face, each throwing a wedge of shadow down its far side.
    for (s in summits) {
        repeat(7) {
            val j = (s + rnd.nextInt(60) - 30).coerceIn(2, xs.size - 3)
            val x0 = xs[j]; val y0 = ys[j] + 6f * unit
            if (shadedAt(x0, y0)) return@repeat
            val len = h * (0.08f + 0.16f * rnd.nextFloat())
            val segs = 7
            val drift = (x0 - xs[s]) * 0.25f
            val sx = FloatArray(segs + 1); val sy = FloatArray(segs + 1)
            for (m in 0..segs) {
                val t = m / segs.toFloat()
                sx[m] = x0 + drift * t + (if (m in 1 until segs) (rnd.nextFloat() - 0.5f) * 9f * unit else 0f)
                sy[m] = y0 + len * t
            }
            val wMax = (14f + 30f * rnd.nextFloat()) * unit
            strip.rewind()
            strip.moveTo(sx[0], sy[0])
            for (m in 1..segs) strip.lineTo(sx[m], sy[m])
            for (m in segs downTo 0) strip.lineTo(sx[m] + shadeDir * wMax * (m / segs.toFloat()).pow(0.6f), sy[m])
            strip.close()
            val rib = darken(litBot, 0.55f)
            shader = LinearGradient(0f, y0, 0f, y0 + len, alpha(rib, 135), alpha(rib, 0), Shader.TileMode.CLAMP)
            c.drawPath(strip, fill)
            shader = null
            line.strokeWidth = 1.6f * unit
            line.color = alpha(lighten(litTop, 0.5f), 70)
            for (m in 0 until segs) c.drawLine(sx[m] - shadeDir * 1.5f * unit, sy[m], sx[m + 1] - shadeDir * 1.5f * unit, sy[m + 1], line)
        }
    }
    // Gullies and cracks falling away from the summits.
    repeat(striations / 2) {
        val i = 2 + rnd.nextInt(xs.size - 4)
        val x0 = xs[i]; val y0 = ys[i] + rnd.nextFloat() * h * 0.02f
        if (y0 > baseY - h * 0.04f) return@repeat
        val s = summits.minByOrNull { abs(xs[it] - x0) } ?: 0
        val dx = (x0 - xs[s]) * 0.2f * (0.4f + rnd.nextFloat())
        val len = h * (0.03f + 0.15f * rnd.nextFloat())
        val shaded = shadedAt(x0, y0)
        line.strokeWidth = (1f + 2.2f * rnd.nextFloat()) * unit
        line.color = alpha(darken(if (shaded) shadeBot else litBot, 0.45f), 42 + rnd.nextInt(50))
        c.drawLine(x0, y0, x0 + dx * (len / (h * 0.1f)), y0 + len, line)
        if (!shaded) {
            line.strokeWidth = 1.4f * unit
            line.color = alpha(lighten(litTop, 0.55f), 38)
            c.drawLine(x0 + 2.5f * unit, y0, x0 + 2.5f * unit + dx * (len / (h * 0.1f)), y0 + len, line)
        }
    }
    // Snow caps with ragged lower edges, bluish in the shade, plus tongues running down the gullies.
    val snowLit = l.shade(Lighting.SNOW)
    val snowShade = mix(snowLit, 0xFF7A90B8.toInt(), 0.5f)
    val cap = Path()
    for ((k, s) in summits.withIndex()) {
        val depth = snowDepths[k]
        var l0 = s; while (l0 > 0 && ys[l0] < ys[s] + depth) l0--
        var r0 = s; while (r0 < xs.size - 1 && ys[r0] < ys[s] + depth) r0++
        cap.rewind()
        cap.moveTo(xs[l0], ys[l0])
        for (j in l0 + 1..r0) cap.lineTo(xs[j], ys[j])
        val teeth = max(4, (abs(xs[r0] - xs[l0]) / (18f * unit)).toInt())
        for (kk in 1..teeth) {
            val t = kk / teeth.toFloat()
            val x = xs[r0] + (xs[l0] - xs[r0]) * t
            val base = ys[r0] + (ys[l0] - ys[r0]) * t
            val dy = if (kk == teeth) 0f else depth * (if (kk % 2 == 1) 0.04f + 0.14f * rnd.nextFloat() else -0.05f)
            cap.lineTo(x, base + dy)
        }
        cap.close()
        fill.color = snowLit; c.drawPath(cap, fill)
        for (sp in shadePaths) { c.save(); c.clipPath(sp); fill.color = snowShade; c.drawPath(cap, fill); c.restore() }
        repeat(9) {
            val j = (l0 + (r0 - l0) * rnd.nextFloat()).toInt().coerceIn(0, xs.size - 1)
            val tx = xs[j]; val ty = ys[j] + depth * 0.4f
            val len = depth * (0.8f + 1.6f * rnd.nextFloat())
            val tongue = Path()
            tongue.moveTo(tx - 3f * unit, ty); tongue.lineTo(tx + 3f * unit, ty)
            tongue.lineTo(tx + (rnd.nextFloat() - 0.5f) * 6f * unit, ty + len); tongue.close()
            fill.color = if (shadedAt(tx, ty)) snowShade else snowLit
            c.drawPath(tongue, fill)
        }
    }
    // A crisp rim of light along the sun-facing silhouette.
    line.strokeWidth = 2f * unit
    line.color = alpha(lighten(litTop, 0.5f), 120)
    for (j in 5 until xs.size - 6) {
        val wide = ys[j + 5] - ys[j - 5]
        val facingSun = if (sunOnLeft) wide < 0f else wide > 0f
        // Silhouette points run left to right when the sun is on the left, and right to left when it is not.
        val ordered = if (xs[0] < xs.last()) facingSun else !facingSun
        if (ordered) c.drawLine(xs[j], ys[j] + 1f, xs[j + 1], ys[j + 1] + 1f, line)
    }
    c.restore()
}

/** A ragged patch of snow lying on a slope. */
private fun PaintKit.snowBlob(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, rnd: Random, color: Int) {
    val p = Path()
    val n = 16
    for (i in 0 until n) {
        val a = i / n.toFloat() * 6.2832f
        val r = 0.65f + 0.5f * rnd.nextFloat()
        val x = cx + cos(a) * rx * r; val y = cy + sin(a) * ry * r
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    fill.color = color
    c.drawPath(p, fill)
}

/** A clump of grass: a dark mound with blades fanning out, lighter towards the tips. */
private fun PaintKit.tussock(c: Canvas, x: Float, y: Float, size: Float, light: Int, dark: Int, rnd: Random, blades: Int) {
    fill.color = alpha(darken(dark, 0.3f), 170)
    c.drawOval(x - size * 0.55f, y - size * 0.16f, x + size * 0.55f, y + size * 0.1f, fill)
    repeat(blades) {
        val a = (rnd.nextFloat() - 0.5f) * 2.1f
        val len = size * (0.45f + 0.55f * rnd.nextFloat())
        val bx = x + (rnd.nextFloat() - 0.5f) * size * 0.8f
        val tx = bx + sin(a) * len * 0.85f
        val ty = y - cos(a) * len
        line.strokeWidth = max(1f, size * 0.035f)
        val shade = ((a * 0.4f) * sun + 0.5f + (rnd.nextFloat() - 0.5f) * 0.5f).coerceIn(0f, 1f)
        line.color = mix(dark, light, shade)
        c.drawLine(bx, y, tx, ty, line)
    }
}

/** A buttercup: five round petals round a darker heart. */
private fun PaintKit.yellowFlower(c: Canvas, x: Float, y: Float, r: Float, color: Int, rnd: Random) {
    val off = rnd.nextFloat() * 6.28f
    fill.color = color
    for (i in 0 until 5) {
        val a = off + i * 1.2566f
        c.drawCircle(x + cos(a) * r * 0.6f, y + sin(a) * r * 0.45f, r * 0.42f, fill)
    }
    fill.color = darken(color, 0.3f)
    c.drawCircle(x, y, r * 0.22f, fill)
}

/** A spike of lupin: a stem set with blue florets that shrink towards the tip. */
private fun PaintKit.lupine(c: Canvas, x: Float, y: Float, height: Float, color: Int, rnd: Random) {
    line.strokeWidth = max(1f, height * 0.03f)
    line.color = mix(color, Color.rgb(40, 110, 40), 0.7f)
    c.drawLine(x, y, x, y - height, line)
    val n = 9
    for (i in 0 until n) {
        val t = i / n.toFloat()
        val r = height * (0.1f - 0.07f * t)
        fill.color = mix(color, Color.WHITE, 0.25f * rnd.nextFloat())
        c.drawCircle(x + (rnd.nextFloat() - 0.5f) * r, y - height * (0.45f + 0.55f * t), r, fill)
    }
}
