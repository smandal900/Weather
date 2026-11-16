package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.skymood.wallpaper.fauna.GroundSlot
import com.skymood.wallpaper.fauna.GroundY
import com.skymood.wallpaper.fauna.Species
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A tropical island in a turquoise lagoon. Open sea fades to turquoise and then to pale aqua over
 * white sand; a hazy volcanic island sits on the horizon. The island is a billowy jungle canopy
 * (sunlit crowns, shaded gaps, palm crowns on the skyline) over a strip of sand with a wet edge,
 * a halo of shallows, coral heads and a soft reflection. Surf rolls in along the beach in broken
 * lines of foam, and coconut palms on the beach and in the foreground sway, their crowns rocking
 * and every frond fluttering. Water, surf and palms are animated; everything else is painted once.
 */
class TropicalIslandLandscape : PaintedLandscape() {
    override val layers = arrayOf(
        PaintLayer(0.50f, 0.62f, 0.08f), // far islands
        PaintLayer(0.50f, 0.84f, 0.5f),  // the island, its shallows and reflection
        PaintLayer(0.86f, 1f, 1f),       // sand bank in front
    )

    private val water = Water(maxGlints = 64)
    private val drawer = PalmDrawer()
    private val foamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    // Each foam row follows the beach with its own ripples, so the rows never run parallel.
    private val shoreRows = Array(SURF_ROWS) { Path() }
    private val bankRows = Array(SURF_ROWS) { Path() }
    private var dashes: Array<DashPathEffect> = emptyArray()
    private var unit = 1f

    private class Spot(val u: Float, val trunk: Int, val scale: Float, val phase: Float, val crown: PalmCrown)
    private val beachPalms = listOf(
        Spot(0.07f, 0, 1.0f, 0.0f, PalmCrown(21, 1)),
        Spot(0.26f, 1, 0.82f, 1.3f, PalmCrown(22, 1)),
        Spot(0.57f, 2, 1.05f, 2.1f, PalmCrown(23, 1)),
        Spot(0.82f, 3, 0.9f, 3.4f, PalmCrown(24, 1)),
        Spot(0.95f, 0, 0.7f, 4.2f, PalmCrown(25, 1)),
    )
    private val bigCrown = PalmCrown(31, 2)

    private var spritesFor: Map<String, Bitmap>? = null
    private var farPalms: PalmSprites? = null
    private var nearPalm: PalmSprites? = null

    private var slots: List<GroundSlot> = emptyList()
    private var slotsFor = 0
    override fun groundSlots(): List<GroundSlot> {
        if (slotsFor != width * 31 + height) {
            slotsFor = width * 31 + height
            slots = listOf(GroundSlot(
                Species.DOLPHIN, 0.12f, 0.88f,
                GroundY { _ -> height * 0.80f }, 1f, 3))
        }
        return slots
    }

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat()
        unit = w / 1080f
        water.build(-w * 0.1f, w * 1.1f, h * 0.6f, h, unit)
        // The lines of the beaches, for the surf to follow.
        val shore = Path()
        val n = 48
        for (i in 0..n) {
            val u = -0.02f + 1.04f * i / n
            val x = ix(u, w); val y = shoreY(u, h)
            if (i == 0) shore.moveTo(x, y) else shore.lineTo(x, y)
        }
        val bank = Path()
        bankEdge(bank, w, h, w * 0.08f)
        for (r in 0 until SURF_ROWS) {
            ripple(shore, shoreRows[r], h * 0.0016f, (190f + 53f * r) * unit, r * 2.1f)
            ripple(bank, bankRows[r], h * 0.0022f, (230f + 61f * r) * unit, r * 1.7f + 0.8f)
        }
        // Foam breaks into patches of every size, from long tongues to specks.
        dashes = arrayOf(
            DashPathEffect(floatArrayOf(22f * unit, 6f * unit, 5f * unit, 9f * unit, 40f * unit, 12f * unit, 8f * unit, 6f * unit, 14f * unit, 18f * unit, 3f * unit, 7f * unit), 0f),
            DashPathEffect(floatArrayOf(9f * unit, 5f * unit, 30f * unit, 10f * unit, 4f * unit, 8f * unit, 18f * unit, 14f * unit, 52f * unit, 9f * unit, 6f * unit, 12f * unit), 60f * unit),
            DashPathEffect(floatArrayOf(36f * unit, 8f * unit, 7f * unit, 6f * unit, 12f * unit, 15f * unit, 4f * unit, 5f * unit, 26f * unit, 11f * unit, 3f * unit, 9f * unit), 130f * unit),
        )
    }

    override fun onLightingChanged() {
        water.applyLighting(lighting, LAGOON, width, SHALLOW, OPEN_SEA)
        foamPaint.color = lighting.shade(0xFFFFFFFF.toInt())
    }

    override fun paintAll(k: PaintKit): PaintResult {
        val sprites = HashMap<String, Bitmap>()
        val far = PalmSprites.paint(k, "pf", k.h * FAR_TH, FAR_LEANS, 1, 0.42f, 5, sprites)
        PalmSprites.paint(k, "pn", k.h * NEAR_TH, NEAR_LEANS, 2, 0.28f, 17, sprites)
        return PaintResult(arrayOf(farIslands(k), island(k, far), bank(k)), sprites)
    }

    // ------------------------------------------------------------------ far islands

    private fun farIslands(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(91)
        val hz = h * 0.6f
        val top = l.distant(l.foliage(0xFF3F7F5C.toInt()), 0.5f)
        val foot = l.distant(l.ground(0xFF8A9A7E.toInt()), 0.65f)
        // A high island with a peak and a ridge, the way a volcanic island rises out of the sea.
        farIsle(
            k, c, -w * 0.07f, w * 0.36f, hz, h * 0.075f,
            arrayOf(floatArrayOf(0.30f, 0.80f, 0.16f), floatArrayOf(0.56f, 1.0f, 0.1f), floatArrayOf(0.82f, 0.42f, 0.2f)),
            top, foot, rnd,
        )
        farIsle(k, c, w * 0.80f, w * 1.04f, hz, h * 0.03f, arrayOf(floatArrayOf(0.5f, 1f, 0.3f)), top, foot, rnd)
        k.haze(c, h * 0.52f, h * 0.615f, 0.4f)
        return bmp
    }

    private fun farIsle(
        k: PaintKit, c: Canvas, x0: Float, x1: Float, hz: Float, hh: Float, bumps: Array<FloatArray>,
        top: Int, foot: Int, rnd: Random,
    ) {
        val unit = k.unit
        val path = Path()
        path.moveTo(x0, hz + 1f)
        val n = 70
        for (i in 0..n) {
            val u = i / n.toFloat()
            var v = 0f
            for (b in bumps) { val d = (u - b[0]) / b[2]; v = max(v, b[1] * exp(-d * d)) }
            v *= min(1f, min(u, 1f - u) / 0.08f)
            path.lineTo(x0 + (x1 - x0) * u, hz - hh * v)
        }
        path.lineTo(x1, hz + 1f)
        path.close()
        k.shader = LinearGradient(0f, hz - hh, 0f, hz, k.lighten(top, 0.08f), foot, Shader.TileMode.CLAMP)
        c.drawPath(path, k.fill)
        k.shader = null
        c.save(); c.clipPath(path)
        // Forest texture, kept faint and fine: at this distance it is only a grain.
        repeat((420 * unit).toInt()) {
            val x = x0 + (x1 - x0) * rnd.nextFloat()
            val y = hz - hh * rnd.nextFloat().pow(1.4f)
            k.fill.color = k.alpha(if (rnd.nextBoolean()) k.darken(top, 0.12f) else k.lighten(top, 0.1f), 70)
            c.drawCircle(x, y, (1f + 1.4f * rnd.nextFloat()) * unit, k.fill)
        }
        c.restore()
    }

    // ------------------------------------------------------------------ the island

    private fun island(k: PaintKit, far: PalmSprites?): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val rnd = Random(92)
        shallows(k, c, rnd)
        jungle(k, c, far, rnd)
        beach(k, c, rnd)
        return bmp
    }

    /** Pale water over the sand round the island, coral heads showing through, and the jungle's reflection. */
    private fun shallows(k: PaintKit, c: Canvas, rnd: Random) {
        val l = k.l; val h = k.h; val w = k.w; val unit = k.unit
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val shore = Path()
        val n = 64
        for (i in 0..n) {
            val u = -0.08f + 1.16f * i / n
            if (i == 0) shore.moveTo(ix(u, w), shoreY(u, h)) else shore.lineTo(ix(u, w), shoreY(u, h))
        }
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.maskFilter = BlurMaskFilter(h * 0.03f, BlurMaskFilter.Blur.NORMAL)
        p.strokeWidth = h * 0.075f
        p.color = k.alpha(l.shade(0xFF4CCFC4.toInt()), 150)
        c.save(); c.translate(0f, h * 0.012f); c.drawPath(shore, p); c.restore()
        p.strokeWidth = h * 0.04f
        p.color = k.alpha(l.shade(0xFF9AF0DA.toInt()), 150)
        c.save(); c.translate(0f, h * 0.006f); c.drawPath(shore, p); c.restore()

        // A sandbar trailing off the left end, and dark coral heads on the lagoon floor.
        p.style = Paint.Style.FILL
        p.maskFilter = BlurMaskFilter(9f * unit, BlurMaskFilter.Blur.NORMAL)
        p.color = k.alpha(l.shade(0xFFC2EED2.toInt()), 105)
        c.drawOval(ix(-0.22f, w), shoreY(0f, h) - h * 0.001f, ix(0.1f, w), shoreY(0f, h) + h * 0.016f, p)
        repeat(10) {
            val u = 0.04f + 0.96f * rnd.nextFloat()
            val cx = ix(u, w); val cy = shoreY(u, h) + h * (0.014f + 0.04f * rnd.nextFloat())
            val rx = (14f + 34f * rnd.nextFloat()) * unit
            p.maskFilter = BlurMaskFilter((4f + 5f * rnd.nextFloat()) * unit, BlurMaskFilter.Blur.NORMAL)
            p.color = k.alpha(l.shade(0xFF0C7A86.toInt()), 105)
            c.drawOval(cx - rx, cy - rx * 0.3f, cx + rx, cy + rx * 0.3f, p)
        }

        // The jungle's reflection, soft and dark, fading with depth.
        val refl = Path()
        refl.moveTo(ix(0f, w), shoreY(0f, h))
        for (i in 0..n) {
            val u = i / n.toFloat()
            refl.lineTo(ix(u, w), shoreY(u, h) + h * ISLAND_H * ridge(u) * 0.3f * (0.75f + 0.25f * sin(u * 40f)) + h * 0.004f)
        }
        refl.close()
        val dark = l.shade(0xFF0D4030.toInt())
        p.maskFilter = BlurMaskFilter(5f * unit, BlurMaskFilter.Blur.NORMAL)
        p.shader = LinearGradient(0f, h * SHORE, 0f, h * (SHORE + 0.04f), k.alpha(dark, 120), k.alpha(dark, 0), Shader.TileMode.CLAMP)
        c.drawPath(refl, p)
    }

    /** The jungle: a body, a skyline of billowy crowns, thousands of leaf dabs, and palms standing above the canopy. */
    private fun jungle(k: PaintKit, c: Canvas, far: PalmSprites?, rnd: Random) {
        val l = k.l; val h = k.h; val w = k.w; val unit = k.unit
        val ih = h * ISLAND_H
        val lit = l.foliage(0xFF7DBB44.toInt())
        val mid = l.foliage(0xFF3E8C37.toInt())
        val dark = l.foliage(0xFF1D5A2B.toInt())
        val deep = l.foliage(0xFF0E3524.toInt())
        val warm = l.foliage(0xFF9CBF48.toInt())
        fun topY(u: Float) = shoreY(u, h) - beachDepth(u, h) * 0.6f - ih * ridge(u)
        fun footY(u: Float) = shoreY(u, h) - beachDepth(u, h) * 0.5f

        val n = 120
        val body = Path()
        body.moveTo(ix(0f, w), shoreY(0f, h))
        for (i in 0..n) { val u = i / n.toFloat(); body.lineTo(ix(u, w), topY(u)) }
        body.lineTo(ix(1f, w), shoreY(1f, h))
        for (i in n downTo 0) { val u = i / n.toFloat(); body.lineTo(ix(u, w), shoreY(u, h)) }
        body.close()
        k.shader = LinearGradient(0f, h * SHORE - ih * 1.1f, 0f, h * SHORE, k.mix(mid, lit, 0.3f), deep, Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null

        // Crowns along the skyline give it the broken outline of real jungle.
        class Crown(val x: Float, val y: Float, val r: Float, val bias: Float)
        val crowns = ArrayList<Crown>()
        val count = (w * (X1 - X0) / (26f * unit)).toInt()
        for (i in 0 until count) {
            val u = ((i + 0.5f + (rnd.nextFloat() - 0.5f) * 0.7f) / count).coerceIn(0.02f, 0.98f)
            val r = (ih * (0.09f + 0.10f * rnd.nextFloat()) * (0.35f + 0.65f * sqrt(ridge(u)))).coerceAtLeast(5f * unit)
            crowns.add(Crown(ix(u, w), topY(u) + r * 0.3f, r, 0.1f))
        }
        // A few taller trees standing clear of the canopy.
        for (u in floatArrayOf(0.31f, 0.5f, 0.74f)) {
            val r = ih * (0.17f + 0.04f * rnd.nextFloat())
            crowns.add(Crown(ix(u, w), topY(u) - r * 0.1f, r, 0.14f))
        }
        crowns.sortByDescending { it.y }
        for (cr in crowns) crown(k, c, cr.x, cr.y, cr.r, lit, mid, dark, deep, cr.bias, rnd)

        // Everything below the skyline stays above the waterline, so no crown hangs into the sea.
        val land = Path()
        land.moveTo(ix(0f, w) - w, 0f)
        land.lineTo(ix(0f, w) - w, shoreY(0f, h))
        for (i in 0..n) { val u = i / n.toFloat(); land.lineTo(ix(u, w), shoreY(u, h)) }
        land.lineTo(ix(1f, w) + w, shoreY(1f, h))
        land.lineTo(ix(1f, w) + w, 0f)
        land.close()

        // The hillside: rows of crowns from the skyline down to the beach, each row a little bigger
        // and darker than the one above, the nearer ones overlapping the farther. Slopes that face
        // the sun come out lighter.
        val rows = 6
        c.save(); c.clipPath(land)
        for (row in 0 until rows) {
            val d = (row + 0.5f) / rows
            val step = (24f - 9f * d) * unit
            var x = ix(0.02f, w) + rnd.nextFloat() * step
            val xEnd = ix(0.98f, w)
            while (x < xEnd) {
                val u = (x / w - X0) / (X1 - X0)
                val ty = topY(u); val by = footY(u)
                if (by - ty > 10f * unit) {
                    val r = ih * (0.075f + 0.04f * d) * (0.8f + 0.4f * rnd.nextFloat())
                    val y = ty + (by - ty) * (d + (rnd.nextFloat() - 0.5f) * 0.12f)
                    val slope = ridge(u + 0.02f) - ridge(u - 0.02f)
                    val bias = (if (k.sunOnLeft) slope else -slope).coerceIn(-0.15f, 0.15f) * 1.4f - d * 0.28f + 0.05f
                    crown(k, c, x, y, r, lit, mid, dark, deep, bias, rnd)
                }
                x += step * (0.8f + 0.4f * rnd.nextFloat())
            }
        }
        // Shade under the canopy, deepest just above the sand.
        c.save(); c.clipPath(body)
        k.shader = LinearGradient(0f, h * SHORE - ih * 0.5f, 0f, h * SHORE, k.alpha(deep, 0), k.alpha(deep, 150), Shader.TileMode.CLAMP)
        c.drawRect(ix(0f, w), h * SHORE - ih * 0.5f, ix(1f, w), h * SHORE + h * 0.02f, k.fill)
        k.shader = null
        c.restore()
        // A little sun-bleached leaf on the lit tops.
        repeat((220 * unit).toInt()) {
            val u = 0.03f + 0.94f * rnd.nextFloat()
            val ty = topY(u); val by = footY(u)
            if (by - ty < 6f * unit) return@repeat
            k.fill.color = k.alpha(k.jitter(warm, rnd, 0.12f), 190)
            c.drawCircle(ix(u, w), ty + (by - ty) * rnd.nextFloat().pow(2.2f) * 0.4f + 2f * unit, (1.5f + 2f * rnd.nextFloat()) * unit, k.fill)
        }
        c.restore()

        // Palms in the jungle, their trunks rising out of the canopy; they stand still, being far
        // off. A clump of leaves hides each foot.
        if (far != null) {
            val drawer = PalmDrawer()
            val spots = floatArrayOf(0.17f, 0.36f, 0.52f, 0.66f, 0.80f, 0.90f)
            for ((i, u) in spots.withIndex()) {
                val sc = 0.5f + 0.22f * rnd.nextFloat()
                val bx = ix(u, w); val by = topY(u) + ih * 0.3f
                drawer.draw(c, far, PalmCrown(40 + i, 1), (i + 1) % FAR_LEANS.size, bx, by, sc, 0f, 0f, 0f)
                c.save(); c.clipPath(land)
                crown(k, c, bx, by + ih * 0.02f, ih * 0.11f, lit, mid, dark, deep, -0.1f, rnd)
                crown(k, c, bx + ih * 0.1f, by + ih * 0.06f, ih * 0.08f, lit, mid, dark, deep, -0.15f, rnd)
                c.restore()
            }
        }
    }

    /** One clump of leaves: a dark body with dabs that are lit on top and on the sun's side. [bias] lightens or darkens the whole clump. */
    private fun crown(k: PaintKit, c: Canvas, cx: Float, cy: Float, r: Float, lit: Int, mid: Int, dark: Int, deep: Int, bias: Float, rnd: Random) {
        k.fill.color = deep
        c.drawCircle(cx, cy + r * 0.1f, r, k.fill)
        val dabs = (10 + r / (2f * k.unit)).toInt().coerceAtMost(40)
        for (i in 0 until dabs) {
            val a = rnd.nextFloat() * 6.2832f
            val d = sqrt(rnd.nextFloat()) * 0.9f
            val dx = kotlin.math.cos(a) * d; val dy = sin(a) * d
            val light = (0.58f - dy * 0.7f - k.sun * dx * 0.45f + bias + (rnd.nextFloat() - 0.5f) * 0.3f).coerceIn(0f, 1f)
            val base = if (light < 0.5f) k.mix(deep, dark, light * 2f) else k.mix(dark, k.mix(mid, lit, 0.7f), (light - 0.5f) * 2f)
            k.fill.color = k.jitter(base, rnd, 0.16f)
            c.drawCircle(cx + dx * r, cy + dy * r, r * (0.18f + 0.2f * rnd.nextFloat()), k.fill)
        }
    }

    /** The sandy strip below the jungle: dry sand, a wet edge, scrub where it meets the trees, rocks and driftwood. */
    private fun beach(k: PaintKit, c: Canvas, rnd: Random) {
        val l = k.l; val h = k.h; val w = k.w; val unit = k.unit
        val n = 110
        val sand = Path()
        for (i in 0..n) {
            val u = i / n.toFloat()
            val y = shoreY(u, h) - beachDepth(u, h) + h * 0.0015f * sin(u * 61f)
            if (i == 0) sand.moveTo(ix(u, w), y) else sand.lineTo(ix(u, w), y)
        }
        for (i in n downTo 0) { val u = i / n.toFloat(); sand.lineTo(ix(u, w), shoreY(u, h)) }
        sand.close()
        val dry = l.ground(0xFFF8EDCC.toInt()); val wet = l.ground(0xFFDEC796.toInt())
        k.shader = LinearGradient(0f, h * SHORE - h * 0.026f, 0f, h * SHORE + h * 0.012f, dry, wet, Shader.TileMode.CLAMP)
        c.drawPath(sand, k.fill)
        k.shader = null

        c.save(); c.clipPath(sand)
        repeat((520 * unit).toInt()) {
            val u = rnd.nextFloat()
            val y = shoreY(u, h) - beachDepth(u, h) * rnd.nextFloat()
            k.fill.color = k.alpha(if (rnd.nextBoolean()) k.darken(dry, 0.25f) else k.lighten(dry, 0.5f), 120)
            c.drawCircle(ix(u, w), y, (0.7f + rnd.nextFloat()) * unit, k.fill)
        }
        // The wet edge: damp sand, darker and glossy, with a gleam of sky where it meets the water.
        val edge = Path()
        for (i in 0..n) {
            val u = i / n.toFloat()
            if (i == 0) edge.moveTo(ix(u, w), shoreY(u, h)) else edge.lineTo(ix(u, w), shoreY(u, h))
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        p.strokeWidth = h * 0.007f
        p.color = k.alpha(l.ground(0xFFA89168.toInt()), 120)
        c.drawPath(edge, p)
        p.strokeWidth = 2f * unit
        p.color = k.alpha(l.skyBottom, 90)
        c.save(); c.translate(0f, -h * 0.004f); c.drawPath(edge, p); c.restore()
        c.restore()

        // Beach scrub and grass where the sand meets the trees.
        val leafLit = l.foliage(0xFF79B23C.toInt()); val leafDark = l.foliage(0xFF2F6E2E.toInt())
        repeat(48) {
            val u = 0.03f + 0.94f * rnd.nextFloat()
            val x = ix(u, w); val y = shoreY(u, h) - beachDepth(u, h) * (0.85f + 0.15f * rnd.nextFloat())
            val s = (3f + 5f * rnd.nextFloat()) * unit
            repeat(4) {
                k.fill.color = k.jitter(if (rnd.nextBoolean()) leafLit else leafDark, rnd, 0.2f)
                c.drawCircle(x + (rnd.nextFloat() - 0.5f) * s * 2f, y - rnd.nextFloat() * s, s * (0.5f + 0.5f * rnd.nextFloat()), k.fill)
            }
            k.line.strokeWidth = 1.2f * unit
            repeat(3) {
                k.line.color = k.jitter(leafLit, rnd, 0.2f)
                val bx = x + (rnd.nextFloat() - 0.5f) * s * 3f
                c.drawLine(bx, y, bx + (rnd.nextFloat() - 0.5f) * s, y - s * (1.2f + rnd.nextFloat()), k.line)
            }
        }
        // Rocks, and a log washed up on the sand.
        for ((u, size) in listOf(0.05f to 0.02f, 0.64f to 0.012f, 0.93f to 0.016f)) {
            k.rock(c, ix(u, w), shoreY(u, h) - beachDepth(u, h) * 0.25f, w * size, l.ground(0xFF8B8478.toInt()), rnd)
        }
        val lx = ix(0.42f, w); val ly = shoreY(0.42f, h) - beachDepth(0.42f, h) * 0.35f
        k.line.strokeWidth = 5f * unit
        k.line.color = l.ground(0xFF6B5A47.toInt())
        c.drawLine(lx - w * 0.022f, ly, lx + w * 0.022f, ly - h * 0.002f, k.line)
        k.line.strokeWidth = 2f * unit
        k.line.color = k.lighten(l.ground(0xFF6B5A47.toInt()), 0.25f)
        c.drawLine(lx - w * 0.02f, ly - 2f * unit, lx + w * 0.02f, ly - h * 0.002f - 2f * unit, k.line)
    }

    // ------------------------------------------------------------------ foreground sand bank

    private fun bank(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w; val unit = k.unit
        val rnd = Random(93)
        val edge = Path()
        bankEdge(edge, w, h, k.margin)
        val body = Path(edge)
        body.lineTo(w * 0.78f, h)
        body.lineTo(-k.margin, h)
        body.close()

        // Pale shallows lapping round the bank.
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        p.maskFilter = BlurMaskFilter(h * 0.02f, BlurMaskFilter.Blur.NORMAL)
        p.strokeWidth = h * 0.06f
        p.color = k.alpha(l.shade(0xFF8CE8D4.toInt()), 150)
        c.drawPath(edge, p)

        val dry = l.ground(0xFFF8EBC8.toInt())
        k.shader = LinearGradient(0f, h * 0.9f, 0f, h, l.ground(0xFFE2CB98.toInt()), dry, Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null
        c.save(); c.clipPath(body)
        // Damp sand along the water, and a gleam where it meets it.
        val q = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        q.strokeWidth = h * 0.026f
        q.maskFilter = BlurMaskFilter(h * 0.005f, BlurMaskFilter.Blur.NORMAL)
        q.color = k.alpha(l.ground(0xFFB59C6C.toInt()), 130)
        c.drawPath(edge, q)
        q.maskFilter = null
        q.strokeWidth = 3f * unit
        q.color = k.alpha(l.skyBottom, 100)
        c.save(); c.translate(w * 0.004f, -h * 0.003f); c.drawPath(edge, q); c.restore()
        repeat((1400 * unit).toInt()) {
            val x = -k.margin + rnd.nextFloat() * (w * 0.84f)
            val y = h * (0.89f + 0.11f * rnd.nextFloat())
            k.fill.color = k.alpha(if (rnd.nextFloat() < 0.5f) k.darken(dry, 0.3f) else k.lighten(dry, 0.5f), 120)
            c.drawCircle(x, y, (0.8f + rnd.nextFloat() * 1.3f) * unit, k.fill)
        }
        // Wind ripples on the dry sand.
        k.line.strokeWidth = 1.5f * unit
        repeat(26) {
            val x = -k.margin + rnd.nextFloat() * w * 0.6f
            val y = h * (0.935f + 0.06f * rnd.nextFloat())
            k.line.color = k.alpha(k.darken(dry, 0.22f), 45)
            val len = (30f + 60f * rnd.nextFloat()) * unit
            c.drawLine(x, y, x + len * 0.5f, y - 3f * unit, k.line)
            c.drawLine(x + len * 0.5f, y - 3f * unit, x + len, y, k.line)
        }
        c.restore()

        // Rocks, a coconut, a few shells and tufts of beach grass.
        k.rock(c, w * 0.40f, h * 0.987f, w * 0.03f, l.ground(0xFF9C9486.toInt()), rnd)
        k.rock(c, w * 0.15f, h * 0.915f, w * 0.014f, l.ground(0xFF8B8478.toInt()), rnd)
        val nut = l.ground(0xFF5E452B.toInt())
        k.fill.color = k.darken(nut, 0.4f); c.drawOval(w * 0.26f, h * 0.972f, w * 0.30f, h * 0.985f, k.fill)
        k.fill.color = nut; c.drawOval(w * 0.262f, h * 0.9715f, w * 0.296f, h * 0.982f, k.fill)
        k.fill.color = k.alpha(k.lighten(nut, 0.4f), 150); c.drawOval(w * 0.268f, h * 0.9725f, w * 0.282f, h * 0.9765f, k.fill)
        repeat(7) {
            val x = w * (0.05f + 0.5f * rnd.nextFloat()); val y = h * (0.94f + 0.05f * rnd.nextFloat())
            k.fill.color = k.alpha(if (rnd.nextBoolean()) 0xFFF4DDD0.toInt() else 0xFFE8C9A8.toInt(), 230)
            c.drawCircle(x, y, (2f + 2f * rnd.nextFloat()) * unit, k.fill)
        }
        val grass = l.foliage(0xFF6FA83A.toInt()); val grassDark = l.foliage(0xFF3E7A2E.toInt())
        for (gx in floatArrayOf(0.035f, 0.17f, 0.22f)) {
            val x = w * gx; val y = h * (0.9f + 0.03f * rnd.nextFloat())
            repeat(14) {
                k.line.strokeWidth = (1.4f + rnd.nextFloat()) * unit
                k.line.color = k.jitter(if (rnd.nextBoolean()) grass else grassDark, rnd, 0.2f)
                val a = (rnd.nextFloat() - 0.5f) * 1.6f
                val len = (22f + 26f * rnd.nextFloat()) * unit
                c.drawLine(x, y, x + sin(a) * len, y - kotlin.math.cos(a) * len, k.line)
            }
        }
        return bmp
    }

    // ------------------------------------------------------------------ animation

    private fun resolveSprites() {
        val s = sprites
        if (s !== spritesFor) {
            spritesFor = s
            farPalms = PalmSprites.from(s, "pf", FAR_LEANS, 1)
            nearPalm = PalmSprites.from(s, "pn", NEAR_LEANS, 2)
        }
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = timeMs / 1000f
        when (stage) {
            1 -> water.draw(canvas, timeMs, windKmh, shift(0.4f))
            2 -> {
                resolveSprites()
                canvas.save(); canvas.translate(shift(0.5f), 0f)
                surf(canvas, shoreRows, t, 0f, h * 0.026f)
                val far = farPalms
                if (far != null) {
                    for (i in beachPalms.indices) {
                        val s = beachPalms[i]
                        drawer.draw(
                            canvas, far, s.crown, s.trunk, ix(s.u, w), shoreY(s.u, h) - beachDepth(s.u, h) * 0.5f,
                            s.scale, t, windKmh, s.phase,
                        )
                    }
                }
                canvas.restore()
            }
            layers.size -> {
                resolveSprites()
                canvas.save(); canvas.translate(shift(1f), 0f)
                surf(canvas, bankRows, t, w * 0.02f, -h * 0.032f)
                val near = nearPalm
                if (near != null) drawer.draw(canvas, near, bigCrown, 0, w * 0.065f, h * 0.935f, 1f, t, windKmh, 0.7f)
                canvas.restore()
            }
        }
    }

    /** Copies [src] to [dst], pushing every point sideways by a double ripple of height [amp] and wavelength [wave]. */
    private fun ripple(src: Path, dst: Path, amp: Float, wave: Float, phase: Float) {
        dst.rewind()
        val pm = android.graphics.PathMeasure(src, false)
        val len = pm.length
        val pos = FloatArray(2); val tan = FloatArray(2)
        val step = 9f * unit
        var d = 0f
        var first = true
        while (d <= len) {
            pm.getPosTan(d, pos, tan)
            val off = amp * (sin(d / wave * 6.2832f + phase) + 0.5f * sin(d / (wave * 0.37f) * 6.2832f + phase * 1.7f))
            val x = pos[0] - tan[1] * off; val y = pos[1] + tan[0] * off
            if (first) { dst.moveTo(x, y); first = false } else dst.lineTo(x, y)
            d += step
        }
    }

    /**
     * Surf rolling in along the [rows]: each row starts out in the water, [dx]/[dy] away from the
     * beach, and slides in at its own pace as it thickens, a thin wash line with patches of
     * foam breaking along it, then sinks into the sand. The wind roughens it.
     */
    private fun surf(canvas: Canvas, rows: Array<Path>, t: Float, dx: Float, dy: Float) {
        val rough = 0.6f + (windKmh / 40f).coerceAtMost(1f)
        for (r in rows.indices) {
            val ph = (t * SURF_SPEED[r] + r / rows.size.toFloat()) % 1f
            val away = 1f - ph
            val strength = sin(PI.toFloat() * ph) * (0.55f + 0.35f * rough)
            canvas.save()
            canvas.translate(dx * away, dy * away)
            // A soft wet band, then the thin wash line, then the foam itself.
            foamPaint.pathEffect = null
            foamPaint.strokeWidth = (6f + 9f * ph) * unit
            foamPaint.alpha = (strength * 34f).toInt().coerceIn(0, 255)
            canvas.drawPath(rows[r], foamPaint)
            foamPaint.strokeWidth = (1.2f + 1.4f * ph) * unit
            foamPaint.alpha = (strength * 80f).toInt().coerceIn(0, 255)
            canvas.drawPath(rows[r], foamPaint)
            foamPaint.pathEffect = dashes.getOrNull(r)
            foamPaint.strokeWidth = (2.4f + 3.4f * ph) * unit * (0.8f + 0.3f * rough)
            foamPaint.alpha = (strength * 175f).toInt().coerceIn(0, 255)
            canvas.drawPath(rows[r], foamPaint)
            canvas.restore()
        }
    }

    private companion object {
        // Where the island sits, as fractions of the screen.
        const val X0 = 0.25f
        const val X1 = 0.97f
        const val SHORE = 0.735f
        const val ISLAND_H = 0.10f

        const val SURF_ROWS = 3
        val SURF_SPEED = floatArrayOf(0.13f, 0.17f, 0.11f)

        const val FAR_TH = 0.09f
        const val NEAR_TH = 0.36f
        val FAR_LEANS = floatArrayOf(-0.22f, 0.04f, 0.14f, 0.30f)
        val NEAR_LEANS = floatArrayOf(0.13f)

        const val OPEN_SEA = 0xFF0C5C94.toInt()
        const val LAGOON = 0xFF15A2B2.toInt()
        const val SHALLOW = 0xFF4FD0C6.toInt()

        /** Screen x of a point [u] (0..1) along the island. */
        fun ix(u: Float, w: Float) = w * (X0 + (X1 - X0) * u)

        /** Screen y of the waterline at [u]; it bulges a little towards the viewer in the middle. */
        fun shoreY(u: Float, h: Float) =
            h * SHORE + h * 0.011f * sin(PI.toFloat() * u.coerceIn(0f, 1f)).coerceAtLeast(0f).pow(0.7f)

        /** How deep the strip of sand is at [u], tapering to nothing at the island's ends. */
        fun beachDepth(u: Float, h: Float): Float {
            val s = sin(PI.toFloat() * u.coerceIn(0f, 1f)).coerceAtLeast(0f).pow(0.5f)
            return h * 0.026f * s * (0.8f + 0.2f * sin(u * 23f + 1f))
        }

        /** Height of the jungle at [u], 0..1: a small hill, the main one and a lower one beyond. */
        fun ridge(u: Float): Float {
            if (u <= 0f || u >= 1f) return 0f
            fun hill(c: Float, a: Float, s: Float): Float { val d = (u - c) / s; return a * exp(-d * d) }
            fun ease(x: Float): Float { val f = x.coerceIn(0f, 1f); return f * f * (3f - 2f * f) }
            val v = hill(0.22f, 0.5f, 0.13f) + hill(0.46f, 1f, 0.2f) + hill(0.74f, 0.72f, 0.13f)
            // Ridges and gullies, so the skyline is not a smooth dome.
            val craggy = 1f + 0.07f * sin(u * 37f + 0.5f) + 0.05f * sin(u * 71f + 2f)
            return min(v, 1.05f) * craggy * ease(u / 0.09f) * ease((1f - u) / 0.09f)
        }

        /** The foreground bank's water edge, left to bottom right. */
        fun bankEdge(p: Path, w: Float, h: Float, margin: Float) {
            p.moveTo(-margin, h * 0.895f)
            p.cubicTo(w * 0.12f, h * 0.875f, w * 0.34f, h * 0.90f, w * 0.54f, h * 0.948f)
            p.cubicTo(w * 0.63f, h * 0.97f, w * 0.72f, h * 0.99f, w * 0.78f, h)
        }
    }
}
