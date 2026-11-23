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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import com.skymood.wallpaper.scenes.SkyGradient
import com.skymood.wallpaper.fauna.FlowerBed
import com.skymood.wallpaper.fauna.GroundSlot
import com.skymood.wallpaper.fauna.MeadowSpec
import com.skymood.wallpaper.fauna.Perch
import com.skymood.wallpaper.fauna.GroundY
import com.skymood.wallpaper.fauna.Species
import kotlin.math.sqrt
import kotlin.random.Random

private const val DEEP_SEA = 0xFF123E5C.toInt()
private const val DEEP_LAKE = 0xFF173F4A.toInt()
private const val BEACH_FAR = 0xFF1A5F8E.toInt()
private const val BEACH_MID = 0xFF1B88A6.toInt()
private const val BEACH_SHALLOW = 0xFF58CDBF.toInt()

private fun stroke() = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
private fun fillPaint() = Paint(Paint.ANTI_ALIAS_FLAG)

/** Where the sand meets the sea, shared by the painted sand and the animated foam. */
internal fun shoreEdge(x: Float, w: Float, h: Float): Float =
    h * 0.83f + h * 0.012f * sin(x / w * 3.2f + 0.6f) + h * 0.014f * (x / w)

/** Sea washing onto a painted sandy beach with a leafy headland, wet sand, foam and a palm tree. */
/** Sea washing onto a painted sandy beach with a leafy headland, wet sand, foam, palms, speed boat, relax chairs, umbrella and beachgoers. */
class BeachLandscape : PaintedLandscape() {
    // Headland, then the sand, then the things standing on it. The swash runs up the sand between the
    // last two (see drawAnimated), so it washes over the beach but never over the props.
    override val layers = arrayOf(PaintLayer(0.5f, 0.68f, 0.1f), PaintLayer(0.78f, 1f, 1f), PaintLayer(0.78f, 1f, 1f))

    private var slots: List<GroundSlot> = emptyList()
    private var slotsFor = 0
    override fun groundSlots(): List<GroundSlot> {
        if (slotsFor != width * 31 + height) {
            slotsFor = width * 31 + height
            slots = makeSlots(width.toFloat(), height.toFloat())
        }
        return slots
    }

    private fun makeSlots(w: Float, h: Float): List<GroundSlot> {
        return listOf(
            GroundSlot(Species.CRAB, 0.08f, 0.7f, GroundY { x -> shoreEdge(x, w, h) + h * 0.07f }, 1.2f, 3),
            GroundSlot(Species.DOLPHIN, 0.08f, 0.58f, GroundY { _ -> h * 0.77f }, 1f, 3),
        )
    }

    private val water = Water(drawReflection = false)
    private val surf = BeachSurf()
    private val wakes = BoatStyle.FLEET.associateWith { BoatWake(it) }
    private val foamPaint = stroke()
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        water.build(-w * 0.1f, w * 1.1f, h * 0.66f, h * 0.86f, unit)
        surf.build(w, h, unit, h * 0.66f)
        foamPaint.strokeWidth = 3f * unit.coerceAtLeast(0.6f)
    }

    override fun onLightingChanged() {
        // Open sea is a deep blue that clears through teal to turquoise over the sand.
        water.applyLighting(lighting, BEACH_MID, width, shallow = BEACH_SHALLOW, horizon = BEACH_FAR)
        surf.applyLighting(lighting)
        for (wk in wakes.values) wk.applyLighting(lighting)
        foamPaint.color = lighting.shade(0xFFFFFFFF.toInt())
    }

    override fun paintAll(k: PaintKit): PaintResult {
        val palm = palm(k)
        val sp = HashMap<String, Bitmap>()
        if (palm != null) sp["palm"] = palm
        // The boats on the sea: a different kind on each row, each with its own speed and wake.
        k.paintSpeedBoat()?.let { sp[BoatStyle.RUNABOUT.key] = it }
        k.paintJetSki()?.let { sp[BoatStyle.JETSKI.key] = it }
        k.paintRacer()?.let { sp[BoatStyle.RACER.key] = it }
        k.paintRib()?.let { sp[BoatStyle.RIB.key] = it }
        k.paintCruiser()?.let { sp[BoatStyle.CRUISER.key] = it }
        k.paintSailboat()?.let { sp["sail"] = it }
        return PaintResult(arrayOf(headland(k), sand(k), props(k)), sp)
    }

    private fun headland(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h; val w = k.w; val u = k.unit.coerceAtLeast(0.6f)
        val rnd = Random(61)
        val horizon = h * 0.66f

        // A faint, low island far out to the right, so the sea has depth.
        farIsland(c, k, w * 0.6f, w * 0.97f, h * 0.014f, rnd)

        // The headland: a rounded wooded hill that drops steeply to rocks on the sea side.
        val n = 80
        val x0 = -w * 0.1f; val span = w * 0.52f
        val xs = FloatArray(n + 1); val ys = FloatArray(n + 1)
        for (i in 0..n) {
            val t = i / n.toFloat()
            // sin(pi) comes out a hair below zero in floats, and pow() of that is NaN, which would drop the path.
            val hill = sin(PI.toFloat() * t.pow(0.85f)).coerceAtLeast(0f).pow(1.15f)
            val lumps = 0.88f + 0.08f * sin(t * 13f + 1f) + 0.06f * sin(t * 31f)
            xs[i] = x0 + t * span
            ys[i] = horizon - h * 0.05f * hill * lumps
        }
        val body = Path()
        body.moveTo(xs[0], horizon + 2f)
        for (i in 0..n) body.lineTo(xs[i], ys[i])
        body.lineTo(xs[n], horizon + 2f)
        body.close()
        // Far hills fade into the sky, but this one is close enough to keep its colour and form.
        val lit = l.distant(l.foliage(0xFF5C9A5A.toInt()), 0.18f); val dark = l.distant(l.foliage(0xFF24492F.toInt()), 0.18f)
        val rock = l.distant(l.ground(0xFF7A756A.toInt()), 0.25f)
        val trunk = l.distant(l.shade(0xFF5A4632.toInt()), 0.2f)
        k.shader = LinearGradient(0f, horizon - h * 0.055f, 0f, horizon, k.mix(lit, dark, 0.35f), k.mix(dark, rock, 0.5f), Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null

        c.save()
        c.clipPath(body)
        // Leafy texture: a scatter of dabs, lighter near the crest and darker lower down.
        repeat((650 * u).toInt()) {
            val x = x0 + rnd.nextFloat() * span
            val i = (((x - x0) / span) * n).toInt().coerceIn(0, n)
            val top = ys[i]
            val y = top + (horizon - top) * rnd.nextFloat().pow(1.4f)
            val f = (y - top) / (horizon - top + 1f)
            k.fill.color = k.jitter(k.mix(lit, dark, (f * 0.8f + rnd.nextFloat() * 0.4f).coerceIn(0f, 1f)), rnd, 0.1f)
            c.drawCircle(x, y, (1.2f + rnd.nextFloat() * 2.8f) * u, k.fill)
        }
        // Light on the sun's side of the hill, shade on the other.
        k.shader = LinearGradient(
            x0, 0f, x0 + span, 0f,
            if (k.sunOnLeft) k.alpha(Color.WHITE, 38) else k.alpha(Color.BLACK, 46),
            if (k.sunOnLeft) k.alpha(Color.BLACK, 46) else k.alpha(Color.WHITE, 38),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(x0, horizon - h * 0.06f, x0 + span, horizon + 2f, k.fill)
        // Bare rock where the hill meets the water.
        k.shader = LinearGradient(0f, horizon - h * 0.012f, 0f, horizon, k.alpha(rock, 0), k.alpha(rock, 200), Shader.TileMode.CLAMP)
        c.drawRect(x0, horizon - h * 0.012f, x0 + span, horizon + 2f, k.fill)
        k.shader = null
        c.restore()

        // Palms in clumps of two to four, some low on the slope, the rest along the crest; lowest first.
        class Tree(val x: Float, val base: Float, val th: Float)
        val trees = ArrayList<Tree>()
        for (cx in floatArrayOf(0.1f, 0.24f, 0.39f, 0.53f, 0.68f, 0.82f)) {
            repeat(2 + rnd.nextInt(3)) {
                val t = (cx + (rnd.nextFloat() - 0.5f) * 0.08f).coerceIn(0.03f, 0.95f)
                val i = (t * n).toInt().coerceIn(0, n)
                val depth = if (rnd.nextFloat() < 0.35f) rnd.nextFloat() * 0.55f else 0f
                val th = h * (0.010f + rnd.nextFloat() * 0.018f) * (1f - 0.35f * depth)
                trees.add(Tree(xs[i], ys[i] + (horizon - ys[i]) * depth + th * 0.15f, th))
            }
        }
        trees.sortBy { it.base }
        for (tr in trees) drawMiniPalm(c, k, tr.x, tr.base, tr.th, lit, dark, rnd, trunk)
        // Low scrub between the palms.
        repeat(16) {
            val i = (n * (0.04f + 0.9f * rnd.nextFloat())).toInt().coerceIn(0, n)
            val r = h * (0.004f + 0.006f * rnd.nextFloat())
            k.fill.color = k.jitter(k.mix(lit, dark, 0.25f + 0.5f * rnd.nextFloat()), rnd, 0.1f)
            c.drawCircle(xs[i], ys[i] + r * 0.4f, r, k.fill)
        }
        // Boulders at the foot, and a small sea stack off the point.
        for (i in 0 until 5) {
            val bx = xs[n] - w * 0.02f + w * 0.03f * i * 0.6f + rnd.nextFloat() * w * 0.01f
            k.rock(c, bx, horizon + 1f, w * (0.004f + 0.006f * rnd.nextFloat()), rock, rnd)
        }
        k.rock(c, xs[n] + w * 0.04f, horizon + 1f, w * 0.009f, rock, rnd)
        k.rock(c, xs[(n * 0.06f).toInt()], horizon + 1f, w * 0.007f, rock, rnd)
        k.haze(c, h * 0.6f, h * 0.675f, 0.22f)
        return bmp
    }

    /** A low, hazy island on the horizon with a fringe of tiny palms. */
    private fun farIsland(c: Canvas, k: PaintKit, x0: Float, x1: Float, height: Float, rnd: Random) {
        val l = k.l
        val horizon = k.h * 0.66f
        val n = 30
        val ys = FloatArray(n + 1)
        val body = Path()
        body.moveTo(x0, horizon + 2f)
        for (i in 0..n) {
            val t = i / n.toFloat()
            ys[i] = horizon - height * sin(PI.toFloat() * t).coerceAtLeast(0f).pow(0.8f) * (0.8f + 0.2f * sin(t * 23f))
            body.lineTo(x0 + (x1 - x0) * t, ys[i])
        }
        body.lineTo(x1, horizon + 2f)
        body.close()
        k.fill.color = l.distant(l.foliage(0xFF3F7352.toInt()), 0.4f)
        c.drawPath(body, k.fill)
        val lit = l.distant(l.foliage(0xFF4E8F50.toInt()), 0.36f); val dark = l.distant(l.foliage(0xFF265034.toInt()), 0.36f)
        for (i in 4 until n - 3 step 2) {
            val th = height * (0.7f + 0.5f * rnd.nextFloat())
            drawMiniPalm(c, k, x0 + (x1 - x0) * i / n, ys[i] + th * 0.15f, th, lit, dark, rnd, lit)
        }
    }

    private fun sand(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(62)
        val steps = 40
        val body = Path()
        body.moveTo(-k.margin, h)
        for (i in 0..steps) {
            val x = -k.margin + (w + 2 * k.margin) * i / steps
            body.lineTo(x, shoreEdge(x, w, h))
        }
        body.lineTo(w + k.margin, h)
        body.close()
        val dry = l.ground(0xFFE9D3A2.toInt())
        k.shader = LinearGradient(0f, h * 0.82f, 0f, h, l.ground(0xFFD8BE88.toInt()), dry, Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null
        c.save()
        c.clipPath(body)
        k.beachTone(c, rnd, dry)
        // Wet sand: dark and glossy at the waterline, following the shore, fading into dry sand.
        k.beachWetSand(c)
        k.beachRipples(c)
        // Grains.
        repeat((2600 * k.unit).toInt()) {
            val x = -k.margin + rnd.nextFloat() * (w + k.margin * 2)
            val y = shoreEdge(x, w, h) + h * (0.01f + 0.17f * rnd.nextFloat())
            k.fill.color = k.alpha(if (rnd.nextFloat() < 0.5f) k.darken(dry, 0.3f) else k.lighten(dry, 0.5f), 120)
            c.drawCircle(x, y, (0.8f + rnd.nextFloat() * 1.3f) * k.unit, k.fill)
        }
        c.restore()

        // Pebbles and shells.
        for (i in 0 until 7) {
            val x = w * (0.05f + 0.9f * rnd.nextFloat())
            k.rock(c, x, shoreEdge(x, w, h) + h * (0.05f + 0.1f * rnd.nextFloat()), w * (0.008f + 0.012f * rnd.nextFloat()), l.ground(0xFF9C9486.toInt()), rnd)
        }
        k.rock(c, w * 0.08f, h * 0.96f, w * 0.05f, l.ground(0xFF8A8478.toInt()), rnd)
        // Seaweed line, shells, footprints, driftwood and dune grass (under the props below).
        k.beachDebris(c, rnd)
        return bmp
    }

    /** Chairs, umbrella, sandcastle, towel, people and surfboard, in a layer of their own above the swash. */
    private fun props(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        k.beachProps(c)
        return bmp
    }

    private fun palm(k: PaintKit): Bitmap? = paintPalm(k, 63)

    /**
     * One boat crossing the sea, left to right. Slow boats stay on screen; fast ones cross and then spend
     * a while out of sight before coming round again. Planing boats ride bow-up and slap over the chop.
     */
    private fun drawBoat(canvas: Canvas, st: BoatStyle, boat: Bitmap, w: Float, h: Float) {
        val t = timeMs / 1000f
        val r = roughness(windKmh)
        val unit = w / 1080f
        val bw = boat.width.toFloat()
        val wl = boat.height * st.waterline
        val ph = st.start * 5f
        // From just off the left edge to far enough right that the wake has gone too, then round again.
        val lead = bw * 0.6f
        val trail = bw * (0.5f + st.wakeLen * 0.85f)
        val x = ((t * st.speed + st.start) * w) % max(st.span * w, w + lead + trail) - lead
        val bounce = (sin(t * 2.6f + ph) * 0.6f + sin(t * 4.1f + 1f + ph) * 0.4f) * h * 0.0016f * r * st.bob
        val y = h * st.y + bounce + sin(t * 0.7f + ph) * h * st.weave
        val pitch = st.pitch + (sin(t * 1.9f + ph) * 1.1f + sin(t * 3.3f + ph) * 0.6f) * r * st.bob.coerceAtMost(1.2f)
        val wake = wakes.getValue(st)

        canvas.save()
        canvas.translate(x + shift(0.5f), y)
        wake.drawTrail(canvas, boat, t, r, unit)
        // The hull is cut off at the waterline: what is below it is under the water.
        canvas.save()
        canvas.clipRect(-bw, -bw, bw, 0f)
        canvas.rotate(-pitch)
        canvas.drawBitmap(boat, -bw * 0.5f, -wl, spritePaint)
        canvas.restore()
        wake.drawFront(canvas, bw, t, r, unit)

        // The bow lamp: full strength at night, faint against the dusk sky, none in daylight.
        val lamp = if (!lighting.windowsLit) 0f else when (lighting.timeOfDay) {
            com.skymood.domain.TimeOfDay.NIGHT -> 1f
            com.skymood.domain.TimeOfDay.DUSK -> 0.5f
            else -> 0.6f
        }
        wake.drawLight(canvas, bw, pitch, lamp, t, unit)
        canvas.restore()
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        if (stage == 1) {
            water.draw(canvas, timeMs, windKmh, shift(0.4f))
            surf.drawSwells(canvas, timeMs, windKmh, shift(0.5f))
            surf.drawGlitter(canvas, timeMs, shift(0.4f))

            // A sailing boat far out, crossing the horizon very slowly.
            val sail = sprites["sail"]
            if (sail != null) {
                val st = timeMs / 1000f
                val sxp = w * (((st * 0.0035f + 0.55f) % 1.3f) - 0.15f)
                val syp = h * 0.676f + sin(st * 1.1f) * h * 0.0012f
                canvas.save()
                canvas.translate(sxp + shift(0.45f), syp)
                canvas.rotate(3.5f + sin(st * 0.8f) * 1.2f)
                canvas.drawBitmap(sail, -sail.width * 0.5f, -sail.height * 0.9f, spritePaint)
                canvas.restore()
            }

            // Far rows first, so a boat passing in front of another hides it.
            for (st in BoatStyle.FLEET) drawBoat(canvas, st, sprites[st.key] ?: continue, w, h)
            // Over the boat and its wake: the sea clearing to turquoise as it shoals onto the sand.
            surf.drawShallows(canvas, shift(1f))
            return
        }
        if (stage == 2) {
            // Before the props layer: the swash covers the sand, and the props stand on top of it.
            surf.drawRunup(canvas, timeMs, windKmh, shift(1f))
            return
        }
        if (stage != layers.size) return
        val t = timeMs / 1000f
        val palm = sprites["palm"] ?: return
        canvas.save()
        canvas.translate(w * 0.78f + shift(1f), h * 0.89f)
        canvas.rotate(sin(t * (0.8f + windKmh / 30f)) * (0.8f + windKmh / 14f))
        canvas.drawBitmap(palm, -palm.width * 0.55f, -palm.height + 2f, spritePaint)
        canvas.restore()
        canvas.save()
        canvas.translate(w * 0.90f + shift(1f), h * 0.88f)
        canvas.scale(0.72f, 0.72f)
        canvas.rotate(sin(t * (0.8f + windKmh / 30f) + 1.2f) * (0.8f + windKmh / 14f))
        canvas.drawBitmap(palm, -palm.width * 0.55f, -palm.height + 2f, spritePaint)
        canvas.restore()
    }
}

// ---------------------------------------------------------------------------------------------
// River valley
// ---------------------------------------------------------------------------------------------

private const val DEEP_RIVER = 0xFF1F5B64.toInt()

/** Ground distances (screen widths) of the nearest and furthest bits of river that are drawn. */
private const val VALLEY_NEAR = 0.8f
private const val VALLEY_FAR = 18f
private const val VALLEY_DEPTH = 0.4f
private const val RIVER_STREAKS = 80

private fun valleySmooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Top and bottom of the mist that hangs over the far end of the valley around the horizon at [hy]. */
private fun hazeTop(hy: Float, h: Float) = hy - h * 0.06f
private fun hazeBottom(hy: Float, h: Float) = hy + h * 0.2f

/** Mist that fades in above the horizon, is thickest there, and thins out towards the viewer. */
private fun valleyHaze(sky: Int, clarity: Int, hy: Float, h: Float): LinearGradient {
    val clear = sky and 0x00FFFFFF
    val thick = (0.5f * (1f + (4 - clarity) * 0.35f)).coerceAtMost(0.95f)
    val misty = clear or ((255 * thick).toInt() shl 24)
    val mid = clear or ((255 * thick * 0.5f).toInt() shl 24)
    return LinearGradient(
        0f, hazeTop(hy, h), 0f, hazeBottom(hy, h), intArrayOf(clear, misty, mid, clear),
        floatArrayOf(0f, 0.25f, 0.45f, 1f), Shader.TileMode.CLAMP,
    )
}

/**
 * The ground plan of the valley. The river is laid out on the ground and projected, so its bends
 * shrink and bunch up towards the horizon as they would in a real view: a point [d] screen-widths
 * away, [x] screen-widths to the side, lands at `hy + (h - hy) / d` down and `0.5 + x / d` across.
 * Everything else (field patches, trees, cottages, the banks) hangs off the same plan.
 */
private class ValleyGeom(val w: Float, val h: Float) {
    val hy = h * 0.645f
    val vx = w * 0.5f
    val n = 96
    val ys = FloatArray(n + 1)
    val cx = FloatArray(n + 1)
    val half = FloatArray(n + 1)
    val dist = FloatArray(n + 1)
    val gravL = FloatArray(n + 1)
    val gravR = FloatArray(n + 1)
    /** The gravel part of [gravL] / [gravR]: how far each point bar reaches out from the water. */
    val barL = FloatArray(n + 1)
    val barR = FloatArray(n + 1)

    /** Sideways offset of the river's centre at ground distance [d]. */
    fun bend(d: Float) = 0.30f * (0.75f * sin(0.85f * d + 1.7f) + 0.35f * sin(2.1f * d + 0.4f))
    fun projX(x: Float, d: Float) = w * (0.5f + x / d)
    fun projY(d: Float) = hy + (h - hy) / d

    init {
        for (i in 0..n) {
            // Far to near, evenly spaced in log distance so the far bends get enough samples.
            val d = VALLEY_FAR * (VALLEY_NEAR / VALLEY_FAR).pow(i / n.toFloat())
            val s = 1f / d
            dist[i] = d
            ys[i] = projY(d)
            cx[i] = projX(bend(d), d)
            // Wider on the straights, and narrowing to nothing as it winds out of sight.
            // Pools and riffles: the channel widens and narrows rather than running parallel-sided.
            val wobble = 0.88f + 0.16f * sin(d * 1.3f + 0.5f) + 0.07f * sin(d * 3.3f + 1f)
            half[i] = w * 0.19f * s * wobble * (1f - 0.8f * valleySmooth(11f, VALLEY_FAR, d))
        }
        // A thin earthen bank all along, and a crescent of gravel (a point bar) on the inside of each
        // bend that tapers away on the straights, its outline ragged.
        for (i in 0..n) {
            val a = (i - 1).coerceAtLeast(0); val b = (i + 1).coerceAtMost(n)
            val m0 = (cx[i] - cx[a]) / (ys[i] - ys[a] + 1e-3f)
            val m1 = (cx[b] - cx[i]) / (ys[b] - ys[i] + 1e-3f)
            val curve = (m1 - m0) / (ys[b] - ys[a] + 1e-3f) * 2f * half[i]
            val bar = (curve * 3f).coerceIn(-1f, 1f)
            val ragged = 0.75f + 0.35f * sin(dist[i] * 5.3f + 1f) + 0.25f * sin(dist[i] * 13f)
            val bank = half[i] * (0.08f + 0.05f * sin(dist[i] * 4.1f))
            barL[i] = half[i] * 1.1f * ((max(0f, -bar) - 0.2f) / 0.8f).coerceAtLeast(0f).pow(1.3f) * ragged
            barR[i] = half[i] * 1.1f * ((max(0f, bar) - 0.2f) / 0.8f).coerceAtLeast(0f).pow(1.3f) * ragged
            gravL[i] = bank + barL[i]
            gravR[i] = bank + barR[i]
        }
    }

    private fun find(y: Float): Int {
        var lo = 0; var hi = n
        while (hi - lo > 1) { val mid = (lo + hi) ushr 1; if (ys[mid] <= y) lo = mid else hi = mid }
        return lo
    }
    private fun at(arr: FloatArray, y: Float): Float {
        val i = find(y)
        val f = ((y - ys[i]) / (ys[i + 1] - ys[i])).coerceIn(0f, 1f)
        return arr[i] + (arr[i + 1] - arr[i]) * f
    }
    fun cxAt(y: Float) = at(cx, y)
    fun halfAt(y: Float) = at(half, y)
    fun leftEdge(y: Float) = cxAt(y) - halfAt(y)
    fun rightEdge(y: Float) = cxAt(y) + halfAt(y)
    /** Where the near bank on each side has to stop, clear of the gravel. */
    fun leftBound(y: Float) = leftEdge(y) - at(gravL, y) - w * 0.012f
    fun rightBound(y: Float) = rightEdge(y) + at(gravR, y) + w * 0.012f

    fun waterPath(): Path {
        val p = Path()
        for (i in 0..n) if (i == 0) p.moveTo(cx[i] - half[i], ys[i]) else p.lineTo(cx[i] - half[i], ys[i])
        for (i in n downTo 0) p.lineTo(cx[i] + half[i], ys[i])
        p.close()
        return p
    }

    fun shorePath(): Path {
        val p = Path()
        for (i in 0..n) if (i == 0) p.moveTo(cx[i] - half[i] - gravL[i], ys[i]) else p.lineTo(cx[i] - half[i] - gravL[i], ys[i])
        for (i in n downTo 0) p.lineTo(cx[i] + half[i] + gravR[i], ys[i])
        p.close()
        return p
    }

    /** The gravel bar on one side, from the water's edge out to [barL] / [barR]. */
    fun barPath(side: Int): Path {
        val p = Path()
        val bar = if (side < 0) barL else barR
        for (i in 0..n) {
            val x = cx[i] + side * half[i]
            if (i == 0) p.moveTo(x, ys[i]) else p.lineTo(x, ys[i])
        }
        for (i in n downTo 0) p.lineTo(cx[i] + side * (half[i] + bar[i]), ys[i])
        p.close()
        return p
    }

    // ------------------------------------------------------------- the near banks

    val leftTopEdge = h * 0.74f
    val rightTopEdge = h * 0.75f
    val leftEnd = h * 0.865f
    val rightEnd = h * 0.9f
    val leftXEnd = leftBound(leftEnd)
    val rightXEnd = rightBound(rightEnd)

    private fun bump(x: Float, ph: Float) = h * (0.005f * sin(x / w * 21f + ph) + 0.003f * sin(x / w * 57f + ph * 2f))

    /** Crest of the near bank on the left: steep at the screen edge, easing down to the water. */
    fun leftTop(x: Float): Float {
        val x0 = -w * 0.12f
        val t = ((x - x0) / (leftXEnd - x0)).coerceIn(0f, 1f)
        return leftTopEdge + (leftEnd - leftTopEdge) * (1f - (1f - t).pow(2.2f)) + bump(x, 1f) * (1f - t)
    }

    fun rightTop(x: Float): Float {
        val x1 = w * 1.12f
        val t = ((x1 - x) / (x1 - rightXEnd)).coerceIn(0f, 1f)
        return rightTopEdge + (rightEnd - rightTopEdge) * (1f - (1f - t).pow(2.2f)) + bump(x, 2.4f) * (1f - t)
    }

    fun slopePath(side: Int): Path {
        val p = Path()
        val steps = 48
        if (side < 0) {
            val x0 = -w * 0.12f
            p.moveTo(x0, h * 1.04f)
            for (i in 0..steps) { val x = x0 + (leftXEnd - x0) * i / steps; p.lineTo(x, leftTop(x)) }
            for (i in 1..steps) { val y = leftEnd + (h * 1.04f - leftEnd) * i / steps; p.lineTo(leftBound(y), y) }
        } else {
            val x1 = w * 1.12f
            p.moveTo(x1, h * 1.04f)
            for (i in 0..steps) { val x = x1 + (rightXEnd - x1) * i / steps; p.lineTo(x, rightTop(x)) }
            for (i in 1..steps) { val y = rightEnd + (h * 1.04f - rightEnd) * i / steps; p.lineTo(rightBound(y), y) }
        }
        p.close()
        return p
    }
}

/** A wooded spur of the valley wall: crest height at the screen edge, and where it meets the floor. */
private class Spur(val side: Int, val footX: Float, val footY: Float, val topY: Float, val tone: Float, val tree: Float, val seed: Float)

// Far to near; each nearer spur is taller at the screen edge and hides the foot of the one behind.
private val SPURS = listOf(
    Spur(-1, 0.44f, 0.652f, 0.585f, 0.46f, 0.017f, 1f),
    Spur(1, 0.57f, 0.651f, 0.575f, 0.46f, 0.017f, 2f),
    Spur(-1, 0.31f, 0.668f, 0.548f, 0.33f, 0.026f, 3f),
    Spur(1, 0.70f, 0.664f, 0.542f, 0.33f, 0.026f, 4f),
    Spur(-1, 0.17f, 0.700f, 0.505f, 0.2f, 0.042f, 5f),
    Spur(1, 0.87f, 0.696f, 0.500f, 0.2f, 0.042f, 6f),
)

/**
 * A river winding out of snowy mountains down a wooded valley. Interlocking forested spurs close in
 * on both sides, fields and cottages lie on the floor, and the river bends away in perspective,
 * with gravel bars on the inside of each bend, wet banks, drifting ripples and rocks with foam.
 */
class RiverValleyLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.3f, 0.7f, 0.06f), PaintLayer(0.4f, 1f, VALLEY_DEPTH), PaintLayer(0.5f, 1f, 1f))

    private var geomCache: ValleyGeom? = null
    private var geomFor = 0
    private fun geom(): ValleyGeom {
        val key = width * 31 + height
        val g = geomCache
        if (g != null && geomFor == key) return g
        return ValleyGeom(width.toFloat(), height.toFloat()).also { geomCache = it; geomFor = key }
    }

    private var meadowSpec: MeadowSpec? = null
    private var meadowFor = 0
    override fun meadow(): MeadowSpec {
        val key = width * 31 + height
        if (meadowSpec == null || meadowFor != key) {
            meadowFor = key
            val g = geom(); val w = g.w; val h = g.h
            val py = h * 0.945f
            meadowSpec = MeadowSpec(
                listOf(
                    FlowerBed(0f, g.leftXEnd / w, GroundY { x -> g.leftTop(x) + h * 0.012f }, h * 0.05f, 9),
                    FlowerBed(g.rightXEnd / w, 1f, GroundY { x -> g.rightTop(x) + h * 0.012f }, h * 0.05f, 9),
                ),
                // A snag standing in the shallows at the water's edge, fished from by the kingfisher.
                Perch(g.leftEdge(py) + g.halfAt(py) * 0.3f, py, h * 0.05f, py - h * 0.01f, 1.6f),
            )
        }
        return meadowSpec!!
    }

    private var slots: List<GroundSlot> = emptyList()
    private var slotsFor = 0
    override fun groundSlots(): List<GroundSlot> {
        val key = width * 31 + height
        if (slotsFor != key) {
            slotsFor = key
            slots = makeSlots(geom())
        }
        return slots
    }

    private fun makeSlots(g: ValleyGeom): List<GroundSlot> {
        val w = g.w; val h = g.h
        val yNear = h * 0.935f; val yFar = h * 0.805f
        val frogY = h * 0.965f
        val cowY = h * 0.95f
        return listOf(
            GroundSlot(Species.DUCK, (g.cxAt(yNear) - g.halfAt(yNear) * 0.6f) / w, (g.cxAt(yNear) + g.halfAt(yNear) * 0.6f) / w, GroundY { _ -> yNear }, 1.5f, 2),
            GroundSlot(Species.DUCK, (g.cxAt(yFar) - g.halfAt(yFar) * 0.5f) / w, (g.cxAt(yFar) + g.halfAt(yFar) * 0.5f) / w, GroundY { _ -> yFar }, 0.85f, 1),
            // Frogs in the grass at the water's edge, and a lone wild ox on the right bank.
            GroundSlot(Species.FROG, (g.leftBound(frogY) - w * 0.12f) / w, (g.leftBound(frogY) - w * 0.01f) / w, GroundY { _ -> frogY }, 1.7f, 2),
            GroundSlot(Species.FROG, (g.rightBound(frogY) + w * 0.01f) / w, (g.rightBound(frogY) + w * 0.14f) / w, GroundY { _ -> frogY }, 1.7f, 1),
            GroundSlot(Species.WILD_COW, 0.8f, 0.93f, GroundY { _ -> cowY }, 0.9f, 1),
        )
    }

    // ---------------------------------------------------------------- animated parts

    private val water = Water(0)
    private var river = Path()
    private val edgePaint = stroke()
    private val shadePaint = Paint()
    private val hazePaint = Paint()
    private val skyPaint = Paint()
    private val lightStreak = stroke()
    private val darkStreak = stroke()
    private val foamPaint = fillPaint()
    private val rockPaint = fillPaint()
    private val capPaint = fillPaint()
    private val rockBody = Path()
    private val rockCap = Path()
    private var vertsL = FloatArray(0)
    private var vertsR = FloatArray(0)
    private var shadeColors = IntArray(0)
    private val sd = FloatArray(RIVER_STREAKS); private val sv = FloatArray(RIVER_STREAKS); private val sl = FloatArray(RIVER_STREAKS)
    private val rockD = floatArrayOf(1.9f, 2.7f, 3.9f, 6.2f)
    private val rockV = floatArrayOf(0.5f, -0.4f, 0.25f, -0.45f)
    private val rockS = floatArrayOf(1f, 0.8f, 1.1f, 0.9f)
    private var unitPx = 1f

    override fun build() {
        val g = geom()
        val w = g.w; val h = g.h
        unitPx = w / 1080f
        river = g.waterPath()
        water.build(-w * 0.1f, w * 1.1f, g.hy, h, unitPx)
        val rows = g.n + 1
        vertsL = FloatArray(rows * 4); vertsR = FloatArray(rows * 4); shadeColors = IntArray(rows * 2)
        for (i in 0..g.n) {
            vertsL[i * 4] = g.cx[i] - g.half[i]; vertsL[i * 4 + 1] = g.ys[i]
            vertsL[i * 4 + 2] = g.cx[i] - g.half[i] * 0.6f; vertsL[i * 4 + 3] = g.ys[i]
            vertsR[i * 4] = g.cx[i] + g.half[i]; vertsR[i * 4 + 1] = g.ys[i]
            vertsR[i * 4 + 2] = g.cx[i] + g.half[i] * 0.6f; vertsR[i * 4 + 3] = g.ys[i]
        }
        val rnd = Random(5)
        for (i in 0 until RIVER_STREAKS) {
            sd[i] = VALLEY_FAR * (VALLEY_NEAR / VALLEY_FAR).pow(rnd.nextFloat())
            sv[i] = (rnd.nextFloat() * 2f - 1f) * 0.88f
            sl[i] = 0.5f + rnd.nextFloat()
        }
        // One lumpy boulder, drawn at unit size and scaled for each rock in the stream.
        rockBody.rewind()
        rockBody.moveTo(-1f, 0f)
        for (i in 0..16) {
            val a = PI.toFloat() + PI.toFloat() * i / 16f
            rockBody.lineTo(cos(a), sin(a) * 0.75f * (0.85f + 0.2f * sin(a * 3f)))
        }
        rockBody.lineTo(1f, 0f)
        rockBody.close()
        rockCap.rewind()
        rockCap.addOval(-0.6f, -0.7f, 0.25f, -0.18f, Path.Direction.CW)
    }

    override fun onLightingChanged() {
        val g = geomCache ?: return
        val l = lighting
        water.applyLighting(l, DEEP_RIVER, width)
        val dark = l.shade(0xFF06241F.toInt()) and 0x00FFFFFF
        for (i in 0..g.n) {
            // Shade under the banks fades out towards the far end, where the river is a thread.
            val a = (150 * valleySmooth(0.01f, 0.12f, 1f / g.dist[i])).toInt()
            shadeColors[i * 2] = dark or (a shl 24)
            shadeColors[i * 2 + 1] = dark
        }
        val bank = l.ground(0xFF3E4A32.toInt()) and 0x00FFFFFF
        edgePaint.shader = LinearGradient(
            0f, g.hy, 0f, g.hy + g.h * 0.1f, bank, bank or (140 shl 24), Shader.TileMode.CLAMP,
        )
        edgePaint.strokeWidth = 2.4f * unitPx.coerceAtLeast(0.6f)
        hazePaint.shader = valleyHaze(l.skyBottom, l.clarity, g.hy, g.h)
        // Still water mirrors the sky, most of all at a low angle, so the far river takes on the
        // colour of the sky (pale blue by day, orange at dusk) and the near water shows more of its depth.
        // It mirrors the higher sky as well as the horizon band, which is bluer and darker, so at
        // dusk the water stays distinct from the glowing ground instead of turning into a pale path.
        val sky = SkyGradient.blend(l.skyBottom, SkyGradient.topColor(l.weather, l.timeOfDay), 0.5f) and 0x00FFFFFF
        skyPaint.shader = LinearGradient(0f, g.hy, 0f, g.h, sky or (140 shl 24), sky or (40 shl 24), Shader.TileMode.CLAMP)
        lightStreak.color = SkyGradient.blend(l.skyBottom, 0xFFFFFFFF.toInt(), 0.6f)
        darkStreak.color = l.shade(0xFF0B3038.toInt())
        foamPaint.color = l.shade(0xFFFFFFFF.toInt())
        rockPaint.color = l.ground(0xFF6D7178.toInt())
        capPaint.color = SkyGradient.blend(rockPaint.color, 0xFFFFFFFF.toInt(), 0.22f)
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != 2 || bitmap(1) == null) return
        val g = geomCache ?: return
        val t = timeMs / 1000f
        canvas.save()
        canvas.translate(shift(VALLEY_DEPTH), 0f)
        canvas.save()
        canvas.clipPath(river)
        water.draw(canvas, timeMs, windKmh + 6f, 0f)
        canvas.drawRect(-g.w * 0.1f, g.hy, g.w * 1.1f, g.h * 1.1f, skyPaint)
        drawStreaks(canvas, g, t)
        val count = (g.n + 1) * 2
        canvas.drawVertices(Canvas.VertexMode.TRIANGLE_STRIP, count * 2, vertsL, 0, null, 0, shadeColors, 0, null, 0, 0, shadePaint)
        canvas.drawVertices(Canvas.VertexMode.TRIANGLE_STRIP, count * 2, vertsR, 0, null, 0, shadeColors, 0, null, 0, 0, shadePaint)
        canvas.drawRect(-g.w * 0.1f, hazeTop(g.hy, g.h), g.w * 1.1f, hazeBottom(g.hy, g.h), hazePaint)
        canvas.restore()
        // A soft wet line along both banks hides the hard edge of the clip.
        canvas.drawPath(river, edgePaint)
        drawRocks(canvas, g, t)
        canvas.restore()
    }

    private fun drawStreaks(canvas: Canvas, g: ValleyGeom, t: Float) {
        val range = VALLEY_FAR - VALLEY_NEAR
        val flow = 0.05f * (1f + windKmh / 60f)
        for (i in 0 until RIVER_STREAKS) {
            val d = VALLEY_NEAR + (((sd[i] - VALLEY_NEAR - t * flow) % range) + range) % range
            val fade = min(1f, min((VALLEY_FAR - d) / 3f, (d - VALLEY_NEAR) / 0.4f))
            if (fade < 0.15f) continue
            val s = 1f / d
            val y = g.hy + (g.h - g.hy) * s
            val x = g.cxAt(y) + sv[i] * g.halfAt(y)
            val len = g.w * 0.03f * s * sl[i] + 1.5f
            val p = if (i % 3 == 0) darkStreak else lightStreak
            p.alpha = ((if (i % 3 == 0) 80 else 120) * fade).toInt()
            p.strokeWidth = max(1f, 2.4f * s * unitPx)
            canvas.drawLine(x - len, y, x + len, y, p)
        }
    }

    private fun drawRocks(canvas: Canvas, g: ValleyGeom, t: Float) {
        val rough = roughness(windKmh)
        for (i in rockD.indices) {
            val s = 1f / rockD[i]
            val y = g.projY(rockD[i])
            val x = g.cxAt(y) + rockV[i] * g.halfAt(y)
            val r = g.w * 0.05f * s * rockS[i]
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(r, r)
            canvas.drawPath(rockBody, rockPaint)
            canvas.drawPath(rockCap, capPaint)
            canvas.restore()
            // Water piles up against the rock and trails away downstream.
            foamDots(canvas, foamPaint, x - r * 1.5f, x + r * 1.5f, y + r * 0.12f, 7, t, max(1.2f, r * 0.09f), rough)
            foamDots(canvas, foamPaint, x - r * 0.9f, x + r * 0.9f, y + r * 0.45f, 5, t + 1f, max(1f, r * 0.07f), rough)
        }
    }

    // ---------------------------------------------------------------- painted scenery

    override fun paintAll(k: PaintKit): PaintResult {
        val g = ValleyGeom(k.w, k.h)
        return PaintResult(arrayOf(farPeaks(k), valley(k, g), banks(k, g)))
    }

    private fun farPeaks(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        // A high snowy range far off, and a lower, darker, bluer range in front of it, so the
        // mountains read as ranges receding into the distance and not as one grey slab.
        k.peaks(
            c, Ridge(k.width, h * 0.63f, h * 0.27f, true, 40), h * 0.7f,
            l.distant(l.ground(0xFF8896AD.toInt()), 0.34f), l.distant(l.ground(0xFF55657E.toInt()), 0.34f),
            Random(90), snowLine = h * 0.5f, snowDepth = 0.2f,
        )
        k.haze(c, h * 0.46f, h * 0.66f, 0.4f)
        k.peaks(
            c, Ridge(k.width, h * 0.665f, h * 0.15f, true, 47), h * 0.7f,
            l.distant(l.ground(0xFF5F7089.toInt()), 0.26f), l.distant(l.ground(0xFF3B4A62.toInt()), 0.26f),
            Random(93), snowLine = h * 0.55f, snowDepth = 0.1f,
        )
        k.haze(c, h * 0.56f, h * 0.7f, 0.3f)
        return bmp
    }

    /** The valley floor, its fields and cottages, the shore, and the forested walls closing in on both sides. */
    private fun valley(k: PaintKit, g: ValleyGeom): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(91)

        // The far end of the valley: low rolling ground with a fringe of pines, where the river winds out of sight.
        val floor = Ridge(k.width, g.hy + h * 0.004f, h * 0.014f, false, 41)
        k.hill(c, floor, h, l.distant(l.ground(0xFF7DAE5C.toInt()), 0.45f), l.ground(0xFF4A8638.toInt()), rnd, 220)
        val pineLit = l.distant(l.foliage(0xFF5C9A58.toInt()), 0.4f); val pineDark = l.distant(l.foliage(0xFF28553A.toInt()), 0.4f)
        var px = -k.margin
        while (px < w + k.margin) {
            if (abs(px - w * 0.5f) > w * 0.04f) {
                val th = h * (0.012f + rnd.nextFloat() * 0.014f)
                k.simplePine(c, px, floor.yAt(px) + th * 0.2f, th, k.jitter(pineLit, rnd, 0.2f), pineDark, rnd)
            }
            px += 6f + rnd.nextFloat() * 8f
        }

        fields(k, c, g, rnd)
        farmland(k, c, g, rnd)
        shore(k, c, g, rnd)
        for (sp in SPURS) spur(k, c, g, sp, rnd)

        // Distance: mist hangs over the far end of the valley and thins towards the viewer.
        k.shader = valleyHaze(l.skyBottom, l.clarity, g.hy, h)
        c.drawRect(-k.margin, hazeTop(g.hy, h), w + k.margin, hazeBottom(g.hy, h), k.fill)
        k.shader = null
        return bmp
    }

    /** Patchwork fields laid out on the ground and projected, so furrows run to the vanishing point. */
    private fun fields(k: PaintKit, c: Canvas, g: ValleyGeom, rnd: Random) {
        val l = k.l
        val colors = intArrayOf(
            0xFF8DB84F.toInt(), 0xFF6C9A3F.toInt(), 0xFFA3BE52.toInt(), 0xFF5E9A3E.toInt(),
            0xFFD2C25E.toInt(), 0xFFE4CF45.toInt(), 0xFF9A7B52.toInt(), 0xFFB7C45A.toInt(),
        )
        val p = Path()
        var dA = 2.3f
        while (dA < 15f) {
            val dB = dA * (1.25f + rnd.nextFloat() * 0.12f)
            var x0 = -2.4f
            while (x0 < 2.4f) {
                val x1 = x0 + 0.28f + rnd.nextFloat() * 0.5f
                val dm = (dA + dB) * 0.5f
                // Leave a strip of meadow along the river.
                if (x1 > g.bend(dm) - 0.32f && x0 < g.bend(dm) + 0.32f) { x0 = x1 + 0.03f; continue }
                val d0 = dA * (1f + rnd.nextFloat() * 0.04f); val d1 = dB * (1f - rnd.nextFloat() * 0.04f)
                p.rewind()
                p.moveTo(g.projX(x0, d0), g.projY(d0)); p.lineTo(g.projX(x1, d0), g.projY(d0))
                p.lineTo(g.projX(x1, d1), g.projY(d1)); p.lineTo(g.projX(x0, d1), g.projY(d1))
                p.close()
                val col = colors[rnd.nextInt(colors.size)]
                k.fill.color = l.distant(l.ground(col), 0.06f + 0.4f * dm / VALLEY_FAR)
                c.drawPath(p, k.fill)
                // Furrows and a darker hedge along the near edge.
                k.line.strokeWidth = max(1f, 1.6f * k.unit / dm)
                k.line.color = k.alpha(Color.BLACK, 28)
                val rows = 5
                for (f in 1..rows) {
                    val x = x0 + (x1 - x0) * f / (rows + 1f)
                    c.drawLine(g.projX(x, d0), g.projY(d0), g.projX(x, d1), g.projY(d1), k.line)
                }
                k.line.strokeWidth = max(1.2f, 2.4f * k.unit / dm)
                k.line.color = k.alpha(l.distant(l.foliage(0xFF3C6A34.toInt()), 0.1f + 0.4f * dm / VALLEY_FAR), 170)
                c.drawLine(g.projX(x0, d0), g.projY(d0), g.projX(x1, d0), g.projY(d0), k.line)
                x0 = x1 + 0.03f + rnd.nextFloat() * 0.05f
            }
            dA = dB * 1.04f
        }
    }

    /** Cottages and trees on the floor of the valley, the nearer ones drawn last. */
    private fun farmland(k: PaintKit, c: Canvas, g: ValleyGeom, rnd: Random) {
        val l = k.l; val w = k.w
        val trunk = l.shade(0xFF4A3A2C.toInt())
        var wall = 0
        class Tree(val x: Float, val y: Float, val th: Float, val d: Float)
        val list = ArrayList<Tree>()
        // Poplars and willows following the river, alternating banks.
        val riverD = floatArrayOf(2.3f, 3.0f, 3.9f, 5.0f, 6.4f, 8.2f, 10.5f, 13f)
        for ((i, d) in riverD.withIndex()) {
            val side = if (i % 2 == 0) 1 else -1
            val off = 0.19f + 0.1f + rnd.nextFloat() * 0.16f
            list.add(Tree(g.projX(g.bend(d) + side * off, d), g.projY(d), w * 0.25f / d * (0.8f + 0.4f * rnd.nextFloat()), d))
        }
        // Single trees and small groves scattered over the fields.
        repeat(16) {
            val d = 3f + rnd.nextFloat() * 10f
            val x = (rnd.nextFloat() * 2f - 1f) * 1.4f
            if (abs(x - g.bend(d)) < 0.4f) return@repeat
            list.add(Tree(g.projX(x, d), g.projY(d), w * 0.2f / d * (0.7f + 0.6f * rnd.nextFloat()), d))
        }
        list.sortByDescending { it.d }
        for (t in list) {
            val far = 0.06f + 0.5f * t.d / VALLEY_FAR
            k.broadleaf(
                c, t.x, t.y, t.th, k.jitter(l.distant(l.foliage(0xFF5C9A44.toInt()), far), rnd, 0.2f),
                l.distant(l.foliage(0xFF2A5A30.toInt()), far), trunk, rnd, 0.6f,
            )
            // Cottages tucked beside the trees.
            if (wall < 2 && (t.d > 5f && t.d < 11f) && abs(t.x - w * 0.5f) > w * 0.12f) {
                val hw = w * 0.1f / t.d
                k.cottage(c, t.x + t.th * 0.9f * (if (t.x < w * 0.5f) -1f else 1f), t.y + 2f, hw, hw * 0.8f, 0xFFE8DCC4.toInt(), 0xFFB5523B.toInt())
                wall++
            }
        }
    }

    /** Muddy banks along the water, gravel bars on the inside of the bends, pebbles and lush grass. */
    private fun shore(k: PaintKit, c: Canvas, g: ValleyGeom, rnd: Random) {
        val l = k.l; val u = k.unit
        // The earthen bank, softened into the meadow by a wide band of grass colour along its outer edge.
        k.fill.color = l.ground(0xFF6F6A4A.toInt())
        c.drawPath(g.shorePath(), k.fill)
        for (i in 0 until g.n) {
            val s = 1f / g.dist[i]
            k.line.strokeWidth = (2f + 12f * s) * u
            k.line.color = k.alpha(l.ground(0xFF5E8A3E.toInt()), 150)
            c.drawLine(g.cx[i] - g.half[i] - g.gravL[i], g.ys[i], g.cx[i + 1] - g.half[i + 1] - g.gravL[i + 1], g.ys[i + 1], k.line)
            c.drawLine(g.cx[i] + g.half[i] + g.gravR[i], g.ys[i], g.cx[i + 1] + g.half[i + 1] + g.gravR[i + 1], g.ys[i + 1], k.line)
        }
        // Gravel bars.
        k.fill.color = l.ground(0xFFA79E86.toInt())
        c.drawPath(g.barPath(-1), k.fill)
        c.drawPath(g.barPath(1), k.fill)
        // Wet mud and sand where the water laps the bank.
        for (i in 0 until g.n) {
            val s = 1f / g.dist[i]
            k.line.strokeWidth = (2f + 14f * s) * u
            k.line.color = k.alpha(l.ground(0xFF5C5440.toInt()), 200)
            c.drawLine(g.cx[i] - g.half[i], g.ys[i], g.cx[i + 1] - g.half[i + 1], g.ys[i + 1], k.line)
            c.drawLine(g.cx[i] + g.half[i], g.ys[i], g.cx[i + 1] + g.half[i + 1], g.ys[i + 1], k.line)
        }
        val tones = intArrayOf(0xFF8C8A84.toInt(), 0xFFB5A98C.toInt(), 0xFF6E6C68.toInt(), 0xFFCFC4A6.toInt(), 0xFF7E7566.toInt())
        for (i in 0..g.n) {
            val s = 1f / g.dist[i]
            for (side in intArrayOf(-1, 1)) {
                val bar = if (side < 0) g.barL[i] else g.barR[i]
                val grav = if (side < 0) g.gravL[i] else g.gravR[i]
                // Pebbles scattered over the bars.
                if (bar > 2f * u) repeat(3) {
                    val off = g.half[i] + bar * rnd.nextFloat()
                    k.fill.color = k.jitter(l.ground(tones[rnd.nextInt(tones.size)]), rnd, 0.2f)
                    val r = (0.8f + 3.4f * s) * u * (0.6f + rnd.nextFloat() * 0.8f)
                    c.drawOval(g.cx[i] + side * off - r, g.ys[i] - r * 0.6f, g.cx[i] + side * off + r, g.ys[i] + r * 0.6f, k.fill)
                }
                // Grass and rushes growing right down to the bank, taller where there is no gravel.
                val outer = g.half[i] + grav
                repeat(5) {
                    val bh = g.h * (0.005f + 0.017f * s) * (0.6f + rnd.nextFloat()) * (if (bar > 2f * u) 0.7f else 1.15f)
                    val x = g.cx[i] + side * (outer + (rnd.nextFloat() - 0.35f) * g.half[i] * 0.25f)
                    k.line.strokeWidth = max(1f, (1f + 1.8f * s) * u)
                    k.line.color = k.mix(l.ground(0xFF2E5A24.toInt()), l.ground(0xFF8FC060.toInt()), rnd.nextFloat())
                    c.drawLine(x, g.ys[i], x + (rnd.nextFloat() - 0.5f) * bh * 0.6f, g.ys[i] - bh, k.line)
                }
            }
        }
    }

    /** One wooded spur of the valley wall, its trees standing on the slope from the crest down to the floor. */
    private fun spur(k: PaintKit, c: Canvas, g: ValleyGeom, sp: Spur, rnd: Random) {
        val l = k.l; val w = k.w; val h = k.h
        val left = sp.side < 0
        val edge = if (left) -w * 0.12f else w * 1.12f
        val fx = sp.footX * w; val fy = sp.footY * h; val ey = sp.topY * h
        // The foot of the wall runs down a perspective line from the vanishing point out to the screen edge.
        val toeEdge = g.hy + (fy - g.hy) * (abs(g.vx - edge) / max(1f, abs(g.vx - fx)))
        val m = 60
        val xs = FloatArray(m + 1); val ys = FloatArray(m + 1)
        val p1x = edge + (fx - edge) * 0.1f; val p1y = ey + (fy - ey) * 0.55f
        val p2x = fx - (fx - edge) * 0.45f; val p2y = fy - (fy - ey) * 0.04f
        for (i in 0..m) {
            val t = i / m.toFloat(); val u = 1f - t
            xs[i] = u * u * u * edge + 3f * u * u * t * p1x + 3f * u * t * t * p2x + t * t * t * fx
            ys[i] = u * u * u * ey + 3f * u * u * t * p1y + 3f * u * t * t * p2y + t * t * t * fy +
                h * (0.005f * sin(t * 11f + sp.seed) + 0.003f * sin(t * 29f + sp.seed * 2f)) * t * (1f - t) * 4f
        }
        if (!left) { xs.reverse(); ys.reverse() }
        fun crest(x: Float) = interp(xs, ys, x)
        fun toe(x: Float) = fy + (toeEdge - fy) * (x - fx) / (edge - fx)

        val body = Path()
        body.moveTo(xs[0], ys[0])
        for (i in 1..m) body.lineTo(xs[i], ys[i])
        body.lineTo(edge, toeEdge)
        body.close()

        val top = l.distant(l.foliage(0xFF557F4B.toInt()), sp.tone)
        val bottom = l.distant(l.foliage(0xFF2B5535.toInt()), sp.tone * 0.85f)
        c.save()
        c.clipPath(body)
        k.shader = LinearGradient(0f, ey, 0f, toeEdge, top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(min(edge, fx), ey - h * 0.01f, max(edge, fx), toeEdge + 1f, k.fill)
        k.shader = null
        // The wall facing the sun is bright, the opposite one in shade.
        val lit = left != k.sunOnLeft
        k.fill.color = if (lit) k.alpha(Color.WHITE, 16) else k.alpha(Color.BLACK, 62)
        c.drawRect(min(edge, fx), ey - h * 0.01f, max(edge, fx), toeEdge + 1f, k.fill)
        // Clearings and alpine meadows among the trees.
        repeat(5) {
            val x = min(edge, fx) + abs(fx - edge) * (0.1f + 0.7f * rnd.nextFloat())
            val a = crest(x); val b = toe(x)
            val cy = a + (b - a) * (0.3f + 0.5f * rnd.nextFloat())
            val rx = w * (0.03f + 0.05f * rnd.nextFloat())
            k.fill.color = k.alpha(l.distant(l.ground(0xFF8DB760.toInt()), sp.tone), 85)
            c.drawOval(x - rx, cy - rx * 0.28f, x + rx, cy + rx * 0.28f, k.fill)
        }
        c.restore()

        // Trees, thickest where the wall is tallest, drawn from the crest down so lower ones overlap.
        val th0 = sp.tree * h
        val area = abs(fx - edge) * (toeEdge - ey) * 0.5f
        val count = (area / (th0 * th0 * 0.22f)).toInt().coerceIn(30, 260)
        val tx = FloatArray(count); val ty = FloatArray(count)
        for (i in 0 until count) {
            val x = edge + (fx - edge) * (1f - kotlin.math.sqrt(rnd.nextFloat()))
            val a = crest(x); val b = toe(x)
            tx[i] = x
            ty[i] = a + (b - a) * (0.03f + 0.97f * rnd.nextFloat().pow(0.8f))
        }
        val lit0 = l.distant(l.foliage(0xFF4E8A55.toInt()), sp.tone); val dark0 = l.distant(l.foliage(0xFF1E4430.toInt()), sp.tone)
        val trunk = l.shade(0xFF33261C.toInt())
        for (i in (0 until count).sortedBy { ty[it] }) {
            val a = crest(tx[i]); val b = toe(tx[i])
            val f = if (b - a > 1f) ((ty[i] - a) / (b - a)).coerceIn(0f, 1f) else 0f
            val th = th0 * (0.72f + 0.55f * f) * (0.85f + 0.3f * rnd.nextFloat())
            if (th > h * 0.035f) k.conifer(c, tx[i], ty[i], th, k.jitter(lit0, rnd, 0.2f), dark0, trunk, rnd, 0.3f)
            else k.simplePine(c, tx[i], ty[i], th, k.jitter(lit0, rnd, 0.2f), dark0, rnd)
        }
    }

    /** The near banks either side of the water: grassy slopes with tall conifers, bushes, reeds and boulders. */
    private fun banks(k: PaintKit, g: ValleyGeom): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w; val u = k.unit
        val rnd = Random(92)
        val lit = l.foliage(0xFF3F7A4B.toInt()); val dark = l.foliage(0xFF17301F.toInt())
        val trunk = l.shade(0xFF3A2C22.toInt())

        for (side in intArrayOf(-1, 1)) {
            val x0 = if (side < 0) -k.margin else g.rightXEnd - w * 0.05f
            val x1 = if (side < 0) g.leftXEnd + w * 0.05f else w + k.margin
            fun top(x: Float) = if (side < 0) g.leftTop(x) else g.rightTop(x)
            c.save()
            c.clipPath(g.slopePath(side))
            k.shader = LinearGradient(0f, h * 0.72f, 0f, h, l.ground(0xFF5A9345.toInt()), l.ground(0xFF224A20.toInt()), Shader.TileMode.CLAMP)
            c.drawRect(x0, h * 0.68f, x1, h * 1.04f, k.fill)
            k.shader = null
            repeat((900 * u).toInt()) {
                val x = x0 + rnd.nextFloat() * (x1 - x0)
                val d = rnd.nextFloat()
                val y = top(x) + (h - top(x)) * d
                val bh = h * (0.005f + 0.017f * d) * (0.6f + rnd.nextFloat())
                k.line.strokeWidth = (1f + rnd.nextFloat() * 1.6f) * u
                k.line.color = k.mix(l.ground(0xFF1E3518.toInt()), l.ground(0xFF8FC060.toInt()), rnd.nextFloat())
                c.drawLine(x, y, x + (rnd.nextFloat() - 0.5f) * bh * 0.6f, y - bh, k.line)
            }
            // A bright rim where the grass catches the light along the crest.
            k.line.strokeWidth = 2f * u
            k.line.color = k.alpha(k.lighten(l.ground(0xFF8FC060.toInt()), 0.3f), 110)
            var x = x0
            while (x < x1) { c.drawLine(x, top(x) + 1f, x + 8f * u, top(x + 8f * u) + 1f, k.line); x += 8f * u }
            c.restore()
        }

        // Conifers standing on each slope; the lower ones in front.
        class Pine(val x: Float, val y: Float, val th: Float)
        val pines = ArrayList<Pine>()
        for (i in 0 until 6) {
            val x = (w * (0.005f + 0.046f * i) + (rnd.nextFloat() - 0.5f) * w * 0.03f).coerceAtMost(g.leftXEnd - w * 0.04f)
            pines.add(Pine(x, g.leftTop(x) + h * (0.012f + 0.05f * rnd.nextFloat()), h * (0.09f + 0.09f * rnd.nextFloat())))
        }
        for (i in 0 until 5) {
            val x = (w * (0.99f - 0.05f * i) + (rnd.nextFloat() - 0.5f) * w * 0.03f).coerceAtLeast(g.rightXEnd + w * 0.05f)
            pines.add(Pine(x, g.rightTop(x) + h * (0.012f + 0.05f * rnd.nextFloat()), h * (0.09f + 0.09f * rnd.nextFloat())))
        }
        pines.sortBy { it.y }
        for (p in pines) k.conifer(c, p.x, p.y, p.th, k.jitter(lit, rnd, 0.14f), dark, trunk, rnd, 0.8f)

        // A couple of leafy bushes at the water's edge for variety.
        val bushLit = l.foliage(0xFF6AA845.toInt()); val bushDark = l.foliage(0xFF2B5A2E.toInt())
        val lx = g.leftXEnd - w * 0.07f
        k.broadleaf(c, lx, g.leftTop(lx) + h * 0.035f, h * 0.055f, bushLit, bushDark, trunk, rnd, 0.6f)
        val rx = g.rightXEnd + w * 0.08f
        k.broadleaf(c, rx, g.rightTop(rx) + h * 0.04f, h * 0.06f, bushLit, bushDark, trunk, rnd, 0.6f)

        // Reeds along the water's edge.
        val reedA = l.foliage(0xFF6D9A3E.toInt()); val reedB = l.foliage(0xFF3A5E26.toInt())
        for (side in intArrayOf(-1, 1)) repeat(120) {
            val y = h * (0.865f + 0.15f * rnd.nextFloat())
            val bx = if (side < 0) g.leftBound(y) - rnd.nextFloat() * w * 0.025f else g.rightBound(y) + rnd.nextFloat() * w * 0.025f
            val len = h * (0.012f + 0.03f * rnd.nextFloat()) * (0.6f + (y / h - 0.865f) * 4f)
            k.line.strokeWidth = (1.5f + rnd.nextFloat() * 1.5f) * u
            k.line.color = k.mix(reedB, reedA, rnd.nextFloat())
            c.drawLine(bx, y, bx + (rnd.nextFloat() - 0.5f) * len * 0.5f, y - len, k.line)
        }

        // Boulders where the slopes meet the water.
        for (side in intArrayOf(-1, 1)) repeat(3) {
            val y = h * (0.9f + 0.09f * rnd.nextFloat())
            val x = if (side < 0) g.leftBound(y) - rnd.nextFloat() * w * 0.1f else g.rightBound(y) + rnd.nextFloat() * w * 0.1f
            k.rock(c, x, y, w * (0.015f + 0.02f * rnd.nextFloat()), l.ground(0xFF70767E.toInt()), rnd)
        }
        return bmp
    }
}

/**
 * A tall rock wall with a waterfall pouring over a notch in its top. The water falls in two
 * steps, foaming where it hits each ledge, into a misty pool with ripples and flying spray,
 * with boulders, ferns and trees around it.
 */
class WaterfallLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.36f, 0.72f, 0.1f), PaintLayer(0.36f, 0.9f, 0.5f), PaintLayer(0.6f, 1f, 1f))

    private var slots: List<GroundSlot> = emptyList()
    private var slotsFor = 0
    override fun groundSlots(): List<GroundSlot> {
        if (slotsFor != width * 31 + height) {
            slotsFor = width * 31 + height
            val h = height.toFloat()
            slots = listOf(
                GroundSlot(Species.DUCK, 0.2f, 0.8f, GroundY { _ -> h * 0.93f }, 1.3f, 2),
                // Frogs on the mossy banks either side of the pool.
                GroundSlot(Species.FROG, 0.05f, 0.22f, GroundY { _ -> h * 0.955f }, 1.7f, 2),
                GroundSlot(Species.FROG, 0.78f, 0.95f, GroundY { _ -> h * 0.955f }, 1.7f, 1),
            )
        }
        return slots
    }

    private val pool = Water()
    private val fallPath = Path()
    private val fallPaint = Paint()
    private val thinPaint = stroke()
    private val thickPaint = stroke()
    private val foamPaint = fillPaint()
    private val mistPaint = fillPaint()
    private val ripplePaint = stroke()
    private val sprayPaint = fillPaint()
    private val su = FloatArray(STREAKS); private val sy = FloatArray(STREAKS)
    private val sl = FloatArray(STREAKS); private val sv = FloatArray(STREAKS)
    private val thinLines = FloatArray(STREAKS * 4)
    private val thickLines = FloatArray(STREAKS * 4)
    private var lipY = 0f; private var baseY = 0f; private var cx = 0f; private var unitPx = 1f

    private fun fallCx(t: Float) = cx + width * 0.012f * sin(t * 3f)
    private fun fallHalf(t: Float) = width * (0.045f + 0.04f * t.pow(1.3f))
    private fun sm(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); unitPx = w / 1080f
        lipY = h * 0.55f; baseY = h * 0.86f; cx = w * 0.5f
        pool.build(-w * 0.1f, w * 1.1f, baseY, h, unitPx)
        fallPath.rewind()
        val n = 24
        for (i in 0..n) {
            val t = i / n.toFloat()
            val y = lipY + (baseY - lipY) * t
            val x = fallCx(t) - fallHalf(t) + sin(t * 17f) * w * 0.003f
            if (i == 0) fallPath.moveTo(x, y) else fallPath.lineTo(x, y)
        }
        for (i in n downTo 0) {
            val t = i / n.toFloat()
            val y = lipY + (baseY - lipY) * t
            fallPath.lineTo(fallCx(t) + fallHalf(t) + sin(t * 13f + 2f) * w * 0.003f, y)
        }
        fallPath.close()
        val rnd = Random(51)
        for (i in 0 until STREAKS) {
            su[i] = rnd.nextFloat(); sy[i] = rnd.nextFloat()
            sl[i] = 0.04f + rnd.nextFloat() * 0.12f
            sv[i] = 0.5f + rnd.nextFloat() * 0.6f
        }
        thinPaint.strokeWidth = 2f * unitPx.coerceAtLeast(0.6f)
        thickPaint.strokeWidth = 6f * unitPx.coerceAtLeast(0.6f)
        ripplePaint.strokeWidth = 2f * unitPx.coerceAtLeast(0.6f)
        mistPaint.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xAAFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onLightingChanged() {
        val l = lighting
        pool.applyLighting(l, DEEP_LAKE, width)
        fallPaint.shader = LinearGradient(
            0f, lipY, 0f, baseY,
            intArrayOf(l.shade(0xFFDDEEF4.toInt()), l.shade(0xFFEAF5F8.toInt()), l.shade(0xFFFFFFFF.toInt())),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        thinPaint.color = l.shade(0xFFFFFFFF.toInt())
        thickPaint.color = l.shade(0xFFCFE6EE.toInt())
        foamPaint.color = l.shade(0xFFFFFFFF.toInt())
        ripplePaint.color = l.shade(0xFFFFFFFF.toInt())
        sprayPaint.color = l.shade(0xFFFFFFFF.toInt())
    }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(farRidge(k), wall(k), foreground(k)))

    private fun farRidge(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(101)
        val r = Ridge(k.width, h * 0.6f, h * 0.12f, true, 50)
        k.mountain(c, r, h * 0.72f, l.distant(l.ground(0xFF6D8096.toInt()), 0.38f), l.distant(l.ground(0xFF4A5E52.toInt()), 0.33f), rnd)
        val lit = l.distant(l.foliage(0xFF3F7550.toInt()), 0.35f); val dark = l.distant(l.foliage(0xFF1C3A2A.toInt()), 0.35f)
        var x = -k.margin
        while (x < k.w + k.margin) {
            val th = h * (0.02f + rnd.nextFloat() * 0.025f)
            k.conifer(c, x, r.yAt(x) + th * 0.5f, th, k.jitter(lit, rnd, 0.2f), dark, l.shade(0xFF33261C.toInt()), rnd, 0.25f)
            x += (13f + rnd.nextFloat() * 12f) * k.unit
        }
        k.haze(c, h * 0.48f, h * 0.7f, 0.4f)
        return bmp
    }

    private fun wall(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(102)
        val lip = h * 0.55f; val mid = w * 0.5f; val bottom = h * 0.9f
        val rid = Ridge(k.width, h * 0.5f, h * 0.05f, true, 60)
        fun topY(x: Float): Float {
            val f = 1f - sm(w * 0.035f, w * 0.17f, abs(x - mid))
            return rid.yAt(x) * (1f - f) + (lip + h * 0.004f) * f
        }
        val body = Path()
        body.moveTo(-k.margin, bottom)
        var x = -k.margin
        while (x <= w + k.margin) { body.lineTo(x, topY(x)); x += 6f * k.unit }
        body.lineTo(w + k.margin, bottom)
        body.close()
        val rock = l.ground(0xFF7A6E60.toInt())
        c.save()
        c.clipPath(body)
        k.shader = LinearGradient(0f, h * 0.45f, 0f, bottom, rock, k.darken(rock, 0.55f), Shader.TileMode.CLAMP)
        c.drawRect(-k.margin, h * 0.4f, w + k.margin, bottom, k.fill)
        k.shader = null
        // Light from the sun's side.
        k.shader = LinearGradient(
            if (k.sunOnLeft) 0f else w, 0f, if (k.sunOnLeft) w else 0f, 0f, k.alpha(Color.WHITE, 55), 0, Shader.TileMode.CLAMP,
        )
        c.drawRect(-k.margin, h * 0.4f, w + k.margin, bottom, k.fill)
        k.shader = null
        // Columns of rock: vertical bands that are slightly lighter or darker.
        repeat(46) {
            val bx = -k.margin + rnd.nextFloat() * (w + 2 * k.margin)
            val bw = w * (0.01f + 0.03f * rnd.nextFloat())
            k.fill.color = k.alpha(if (rnd.nextBoolean()) Color.BLACK else Color.WHITE, 14 + rnd.nextInt(14))
            c.drawRect(bx, h * 0.45f, bx + bw, bottom, k.fill)
        }
        // Strata: wavering horizontal joints with a lit lip underneath.
        var y = h * 0.5f
        while (y < bottom) {
            val tilt = (rnd.nextFloat() - 0.5f) * h * 0.012f
            var px = -k.margin; var py = y
            k.line.strokeWidth = (1.2f + rnd.nextFloat() * 1.8f) * k.unit
            while (px < w + k.margin) {
                val nx = px + 40f * k.unit
                val ny = y + tilt * (nx / w) + (rnd.nextFloat() - 0.5f) * 3f * k.unit
                k.line.color = k.alpha(Color.BLACK, 48)
                c.drawLine(px, py, nx, ny, k.line)
                k.line.color = k.alpha(Color.WHITE, 20)
                c.drawLine(px, py + 3f * k.unit, nx, ny + 3f * k.unit, k.line)
                px = nx; py = ny
            }
            y += h * (0.008f + rnd.nextFloat() * 0.016f)
        }
        // Cracks running down from the top, with a few branches.
        repeat(130) {
            val sx = -k.margin + rnd.nextFloat() * (w + 2 * k.margin)
            var cy = topY(sx) + rnd.nextFloat() * h * 0.04f
            var cxp = sx
            val len = h * (0.03f + rnd.nextFloat() * 0.18f)
            val end = cy + len
            k.line.strokeWidth = (1f + rnd.nextFloat() * 1.6f) * k.unit
            k.line.color = k.alpha(Color.BLACK, 34 + rnd.nextInt(34))
            while (cy < end) {
                val nx = cxp + (rnd.nextFloat() - 0.5f) * 7f * k.unit
                val ny = cy + 9f * k.unit
                c.drawLine(cxp, cy, nx, ny, k.line)
                cxp = nx; cy = ny
            }
        }
        // Moss and ferns clinging to the lower rock and the ledges.
        repeat(70) {
            val mx = -k.margin + rnd.nextFloat() * (w + 2 * k.margin)
            val my = h * (0.62f + 0.27f * rnd.nextFloat() * rnd.nextFloat() + 0.0f)
            k.fill.color = k.alpha(l.foliage(if (rnd.nextBoolean()) 0xFF4E7A33.toInt() else 0xFF35602F.toInt()), 120 + rnd.nextInt(60))
            c.drawCircle(mx, my, (2f + rnd.nextFloat() * 7f) * k.unit, k.fill)
        }
        // Wet, dark rock beside the falling water.
        for (side in intArrayOf(-1, 1)) {
            val x0 = mid + side * w * 0.07f
            k.shader = LinearGradient(x0, 0f, x0 + side * w * 0.09f, 0f, k.alpha(Color.BLACK, 110), 0, Shader.TileMode.CLAMP)
            c.drawRect(minOf(x0, x0 + side * w * 0.09f), lip, maxOf(x0, x0 + side * w * 0.09f), bottom, k.fill)
            k.shader = null
        }
        c.restore()
        // Two rock ledges where the water steps down.
        for (tier in floatArrayOf(0.3f, 0.62f)) {
            val ty = lip + (h * 0.86f - lip) * tier
            val half = fallHalf(tier) * 1.25f
            val ledge = Path()
            ledge.moveTo(mid - half, ty - h * 0.004f)
            ledge.lineTo(mid + half, ty - h * 0.004f)
            ledge.lineTo(mid + half * 1.1f, ty + h * 0.012f)
            ledge.lineTo(mid - half * 1.1f, ty + h * 0.012f)
            ledge.close()
            k.fill.color = k.darken(rock, 0.35f)
            c.drawPath(ledge, k.fill)
            k.fill.color = k.alpha(Color.WHITE, 30)
            c.drawRect(mid - half, ty - h * 0.004f, mid + half, ty, k.fill)
        }
        // Trees on the plateau and bushes at the foot of the wall.
        val lit = l.foliage(0xFF4C8A4A.toInt()); val dark = l.foliage(0xFF1C3A2A.toInt())
        x = -k.margin
        while (x < w + k.margin) {
            if (abs(x - mid) > w * 0.1f) {
                val th = h * (0.05f + rnd.nextFloat() * 0.06f)
                if (rnd.nextFloat() < 0.75f) k.conifer(c, x, topY(x) + th * 0.12f, th, k.jitter(lit, rnd, 0.16f), dark, l.shade(0xFF3A2C22.toInt()), rnd, 0.6f)
                else k.broadleaf(c, x, topY(x) + th * 0.1f, th * 0.8f, k.jitter(lit, rnd, 0.16f), dark, l.shade(0xFF4A3A2A.toInt()), rnd, 0.4f)
            }
            x += (17f + rnd.nextFloat() * 15f) * k.unit
        }
        for (i in 0 until 9) {
            val bx = if (i < 5) w * (0.02f + 0.06f * i) else w * (0.97f - 0.06f * (i - 5))
            k.broadleaf(c, bx, h * (0.875f + 0.01f * rnd.nextFloat()), h * (0.035f + 0.02f * rnd.nextFloat()), k.jitter(lit, rnd, 0.12f), dark, l.shade(0xFF4A3A2A.toInt()), rnd, 0.35f)
        }
        return bmp
    }

    private fun foreground(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(103)
        val ridge = Ridge(k.width, h * 0.93f, h * 0.03f, false, 61)
        for (side in intArrayOf(-1, 1)) {
            c.save()
            if (side < 0) c.clipRect(-k.margin, h * 0.8f, w * 0.3f, h) else c.clipRect(w * 0.72f, h * 0.8f, w + k.margin, h)
            k.hill(c, ridge, h, l.ground(0xFF4E7A3A.toInt()), l.ground(0xFF21401C.toInt()), rnd, 200)
            c.restore()
        }
        // Boulders along the bank and a few stepping stones near the fall.
        for (i in 0 until 8) {
            val bx = if (i % 2 == 0) w * (0.02f + 0.28f * rnd.nextFloat()) else w * (0.72f + 0.26f * rnd.nextFloat())
            k.rock(c, bx, h * (0.92f + 0.06f * rnd.nextFloat()), w * (0.035f + 0.06f * rnd.nextFloat()), l.ground(0xFF6A6358.toInt()), rnd)
        }
        for (i in 0 until 4) k.rock(c, w * (0.36f + 0.28f * rnd.nextFloat()), h * (0.885f + 0.01f * rnd.nextFloat()), w * (0.012f + 0.018f * rnd.nextFloat()), l.ground(0xFF5F584E.toInt()), rnd)
        val lit = l.foliage(0xFF5A9A4A.toInt()); val dark = l.foliage(0xFF1E4224.toInt())
        for (side in intArrayOf(-1, 1)) {
            val tx = if (side < 0) w * 0.01f else w * 0.99f
            k.conifer(c, tx, h * 0.97f, h * 0.27f, lit, dark, l.shade(0xFF3A2C22.toInt()), rnd, 0.9f)
            for (j in 0 until 3) {
                val bx = if (side < 0) w * (0.06f + 0.08f * j) else w * (0.94f - 0.08f * j)
                k.broadleaf(c, bx, h * (0.965f + 0.02f * rnd.nextFloat()), h * (0.05f + 0.025f * rnd.nextFloat()), k.jitter(lit, rnd, 0.12f), dark, l.shade(0xFF4A3A2A.toInt()), rnd, 0.45f)
            }
        }
        k.grass(c, ridge, h, 700, l.ground(0xFF86C15A.toInt()), l.ground(0xFF1E3518.toInt()), rnd)
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != 2) return
        val w = width.toFloat(); val h = height.toFloat()
        val t = timeMs / 1000f
        pool.draw(canvas, timeMs, windKmh + 12f, shift(0.9f))

        canvas.save()
        canvas.translate(shift(0.7f), 0f)
        canvas.save()
        canvas.clipPath(fallPath)
        canvas.drawRect(0f, lipY, w, baseY, fallPaint)
        // Streaks of water sliding down; each slants with the fall's own shape.
        var nThin = 0; var nThick = 0
        for (i in 0 until STREAKS) {
            val tt = (sy[i] + t * sv[i] * 0.45f) % 1f
            val t2 = (tt + sl[i]).coerceAtMost(1f)
            val y1 = lipY + (baseY - lipY) * tt
            val y2 = lipY + (baseY - lipY) * t2
            val x1 = fallCx(tt) + (su[i] - 0.5f) * 2f * fallHalf(tt) * 0.92f
            val x2 = fallCx(t2) + (su[i] - 0.5f) * 2f * fallHalf(t2) * 0.92f
            if (i % 4 == 0) { thickLines[nThick++] = x1; thickLines[nThick++] = y1; thickLines[nThick++] = x2; thickLines[nThick++] = y2 }
            else { thinLines[nThin++] = x1; thinLines[nThin++] = y1; thinLines[nThin++] = x2; thinLines[nThin++] = y2 }
        }
        thickPaint.alpha = 90
        canvas.drawLines(thickLines, 0, nThick, thickPaint)
        thinPaint.alpha = 190
        canvas.drawLines(thinLines, 0, nThin, thinPaint)
        // Foam boiling where the water hits each ledge.
        for (tier in floatArrayOf(0.3f, 0.62f)) {
            val ty = lipY + (baseY - lipY) * tier
            val half = fallHalf(tier)
            for (k in 0 until 7) {
                val fx = fallCx(tier) + (k - 3) / 3f * half * 0.95f
                foamPaint.alpha = (150 + 70 * sin(t * 3f + k * 1.7f + tier * 9f)).toInt().coerceIn(60, 230)
                canvas.drawCircle(fx, ty + sin(t * 4f + k) * 2f * unitPx, w * (0.012f + 0.005f * sin(t * 2f + k)), foamPaint)
            }
        }
        canvas.restore()

        // Mist and ripples at the foot of the fall.
        val mx = fallCx(1f)
        for (m in 0 until 3) {
            val r = w * (0.2f + 0.05f * m + 0.015f * sin(t * 0.9f + m))
            mistPaint.alpha = (150 - 30 * m + 25 * sin(t * 1.3f + m * 2f)).toInt().coerceIn(40, 200)
            canvas.save()
            canvas.translate(mx + (m - 1) * w * 0.05f, baseY - h * (0.012f + 0.01f * m))
            canvas.scale(r, r * 0.4f)
            canvas.drawCircle(0f, 0f, 1f, mistPaint)
            canvas.restore()
        }
        for (r in 0 until 3) {
            val p = (t * 0.35f + r / 3f) % 1f
            val rx = p * w * 0.2f
            ripplePaint.alpha = ((1f - p) * 140).toInt()
            canvas.drawOval(mx - rx, baseY + h * 0.012f - rx * 0.18f, mx + rx, baseY + h * 0.012f + rx * 0.18f, ripplePaint)
        }
        // Spray thrown up and falling back.
        for (i in 0 until 28) {
            val p = (t * 0.8f + i / 28f) % 1f
            val side = (((i * 7919) % 13) / 6.5f - 1f)
            val sx = mx + side * fallHalf(1f) * (0.5f + 1.1f * p)
            val sy2 = baseY - sin(PI.toFloat() * p) * h * 0.045f * (0.5f + ((i * 31) % 10) / 10f)
            sprayPaint.alpha = ((1f - p) * 200).toInt()
            canvas.drawCircle(sx, sy2, (1.5f + (i % 3)) * unitPx, sprayPaint)
        }
        canvas.restore()
    }

    private companion object {
        const val STREAKS = 70
    }
}

/** A small tropical palm for distant headlands and ridges. */
private fun drawMiniPalm(
    c: Canvas, k: PaintKit, x: Float, y: Float, th: Float, lit: Int, dark: Int, rnd: Random,
    trunk: Int = k.l.shade(0xFF5A4632.toInt()),
) {
    val l = k.l
    val lean = (rnd.nextFloat() - 0.5f) * th * 0.4f
    val cx = x + lean
    val cy = y - th
    k.line.strokeWidth = (th * 0.12f).coerceAtLeast(1.2f * k.unit)
    k.line.color = trunk
    c.drawLine(x, y + 1f, cx, cy, k.line)
    val fronds = 8
    val len = th * 0.65f
    k.line.strokeWidth = (th * 0.06f).coerceAtLeast(1.0f * k.unit)
    for (f in 0 until fronds) {
        val a = -PI.toFloat() + 0.2f + f * (PI.toFloat() - 0.4f) / (fronds - 1f)
        val ex = cx + cos(a) * len
        val ey = cy + sin(a) * len * 0.45f + len * 0.4f
        val qx = cx + cos(a) * len * 0.5f
        val qy = cy + sin(a) * len * 0.5f - len * 0.2f
        k.line.color = if (f % 2 == 0) lit else dark
        var sx = cx; var sy = cy
        for (j in 1..4) {
            val t = j / 4f
            val px = (1 - t) * (1 - t) * cx + 2 * (1 - t) * t * qx + t * t * ex
            val py = (1 - t) * (1 - t) * cy + 2 * (1 - t) * t * qy + t * t * ey
            c.drawLine(sx, sy, px, py, k.line)
            sx = px; sy = py
        }
    }
}

/** A hyper-realistic coconut tree (coconut palm): curved ringed trunk, swollen base, dense arching drooping fronds, and hanging coconut bunches. */
internal fun paintPalm(k: PaintKit, seed: Int): Bitmap? {
    val th = k.h * 0.28f
    val bw = (th * 2.5f).toInt()
    val bh = (th * 2.2f).toInt()
    val (c, bmp) = k.newSprite(bw, bh) ?: return null
    val l = k.l
    val rnd = Random(seed)
    val bx = bw * 0.5f; val by = bh - 2f
    val cx = bx - th * 0.2f; val cy = by - th * 1.5f

    // Trunk
    val trunkBase = l.shade(0xFF888075.toInt()) // Greyish brown
    val trunkShadow = k.darken(trunkBase, 0.4f)
    var px = bx; var py = by
    val trunkSteps = 40
    val xs = FloatArray(trunkSteps + 1)
    val ys = FloatArray(trunkSteps + 1)
    xs[0] = bx; ys[0] = by
    for (i in 1..trunkSteps) {
        val t = i / trunkSteps.toFloat()
        val x = (1 - t) * (1 - t) * bx + 2 * (1 - t) * t * (bx + th * 0.12f) + t * t * cx
        val y = (1 - t) * (1 - t) * by + 2 * (1 - t) * t * (by - th * 0.5f) + t * t * cy
        xs[i] = x; ys[i] = y
        // Swollen base
        val wFrac = if (t < 0.15f) 1.6f - 4.0f * t else 1.0f - 0.2f * t
        k.line.strokeWidth = th * 0.035f * wFrac
        k.line.color = if (i % 3 == 0) trunkShadow else k.jitter(trunkBase, rnd, 0.08f)
        c.drawLine(px, py, x, y, k.line)
        px = x; py = y
    }
    // Leaf scars
    k.line.strokeWidth = (th * 0.007f).coerceAtLeast(1.2f)
    k.line.color = k.darken(trunkShadow, 0.3f)
    for (i in 4 until trunkSteps step 2) {
        val t = i / trunkSteps.toFloat()
        val wFrac = if (t < 0.15f) 1.6f - 4.0f * t else 1.0f - 0.2f * t
        val rw = th * 0.02f * wFrac
        c.drawLine(xs[i] - rw, ys[i], xs[i] + rw, ys[i] + th * 0.004f, k.line)
    }

    // Coconuts
    val greenNut = l.foliage(0xFF5A8022.toInt())
    val yellowNut = l.foliage(0xFFB5A63A.toInt())
    val brownNut = l.ground(0xFF5E452B.toInt())
    
    for (cluster in 0 until 4) {
        val clusterAngle = cluster * (PI.toFloat() * 0.5f) + rnd.nextFloat() * 0.5f
        val clx = cx + cos(clusterAngle) * th * 0.03f
        val cly = cy + sin(clusterAngle) * th * 0.02f + th * 0.02f
        
        k.line.strokeWidth = (th * 0.006f).coerceAtLeast(1.0f)
        k.line.color = k.darken(greenNut, 0.3f)
        c.drawLine(cx, cy, clx, cly, k.line)
        
        val nutCount = 3 + rnd.nextInt(4)
        for (n in 0 until nutCount) {
            val offsetA = n * (PI.toFloat() * 2f / nutCount) + rnd.nextFloat() * 0.5f
            val dist = th * (0.015f + 0.012f * rnd.nextFloat())
            val nx = clx + cos(offsetA) * dist
            val ny = cly + sin(offsetA) * dist + th * 0.01f
            val nr = th * (0.022f + 0.008f * rnd.nextFloat())
            
            val baseCol = when (rnd.nextInt(5)) {
                0, 1 -> greenNut
                2, 3 -> yellowNut
                else -> brownNut
            }
            k.fill.color = k.darken(baseCol, 0.4f)
            c.drawCircle(nx, ny, nr, k.fill)
            k.fill.color = k.jitter(baseCol, rnd, 0.08f)
            c.drawCircle(nx - nr * 0.15f, ny - nr * 0.15f, nr * 0.82f, k.fill)
            k.fill.color = k.alpha(k.lighten(baseCol, 0.3f), 150)
            c.drawCircle(nx - nr * 0.3f, ny - nr * 0.3f, nr * 0.4f, k.fill)
        }
    }

    // Fronds (Crown)
    val freshLit = l.foliage(0xFF7CB838.toInt())
    val freshDark = l.foliage(0xFF326B22.toInt())
    val matureLit = l.foliage(0xFF4C8C24.toInt())
    val matureDark = l.foliage(0xFF184A15.toInt())
    val dyingCol = l.ground(0xFFB59A30.toInt())
    val deadCol = l.ground(0xFF755C2F.toInt())
    
    val frondCount = 48 // Very dense realistic canopy
    for (f in 0 until frondCount) {
        val u = f / (frondCount - 1f) // 0 (top/young) to 1 (bottom/old)
        val state = when {
            u < 0.25f -> 0
            u < 0.70f -> 1
            u < 0.85f -> 2
            else -> 3
        }

        val spreadA = rnd.nextFloat() * PI.toFloat() * 2f
        val elevA = -PI.toFloat() * 0.48f + u.pow(0.8f) * PI.toFloat() * 1.15f // Curve from -86 deg to +120 deg
        
        val len = th * (0.4f + rnd.nextFloat() * 0.3f)
        val ex = cx + cos(spreadA) * cos(elevA) * len
        val ey = cy + sin(elevA) * len
        
        val initVx = cos(spreadA) * cos(elevA)
        val initVy = sin(elevA)
        val qx = cx + initVx * len * 0.6f
        val qy = cy + initVy * len * 0.6f - th * 0.15f * (1f - u)

        val (baseLit, baseDark) = when (state) {
            0 -> freshLit to freshDark
            1 -> matureLit to matureDark
            2 -> k.mix(matureLit, dyingCol, 0.6f) to k.mix(matureDark, dyingCol, 0.6f)
            else -> deadCol to k.darken(deadCol, 0.3f)
        }
        
        val sunSide = if (cos(spreadA) > 0) 1f else -1f
        val sunExposure = (0.5f + 0.5f * (if (sunSide == k.sun) 1f else -1f) - u * 0.3f).coerceIn(0f, 1f)
        val frondCol = k.jitter(k.mix(baseDark, baseLit, sunExposure), rnd, 0.1f)
        
        var sx = cx; var sy = cy
        val steps = if (state == 3) 24 else 48
        val curveLen = sqrt((ex - cx)*(ex - cx) + (ey - cy)*(ey - cy)).coerceAtLeast(0.1f)

        for (j in 1..steps) {
            val t = j / steps.toFloat()
            val x = (1 - t) * (1 - t) * cx + 2 * (1 - t) * t * qx + t * t * ex
            val y = (1 - t) * (1 - t) * cy + 2 * (1 - t) * t * qy + t * t * ey
            
            val tx = x - sx; val ty = y - sy
            val tl = sqrt(tx * tx + ty * ty).coerceAtLeast(0.001f)
            
            // Rachis
            k.line.strokeWidth = th * 0.008f * (1f - t)
            k.line.color = if (state < 2) k.mix(frondCol, l.foliage(0xFF9ACD32.toInt()), 0.5f) else frondCol
            c.drawLine(sx, sy, x, y, k.line)

            // Leaflets (Pinnae)
            val leafLen = curveLen * 0.28f * sin(t * PI.toFloat()) * (0.6f + rnd.nextFloat() * 0.6f)
            k.line.strokeWidth = (th * 0.004f).coerceAtLeast(1f)
            k.line.color = frondCol

            val nx1 = -ty / tl; val ny1 = tx / tl
            val nx2 = ty / tl; val ny2 = -tx / tl

            val gravWeight = 1.0f + u * 2.0f + t * 1.5f
            
            var lx1 = (nx1 * 0.5f + (tx / tl) * 0.3f)
            var ly1 = (ny1 * 0.5f + (ty / tl) * 0.3f) + gravWeight
            val lLen1 = sqrt(lx1 * lx1 + ly1 * ly1).coerceAtLeast(0.001f)
            lx1 = (lx1 / lLen1) * leafLen
            ly1 = (ly1 / lLen1) * leafLen
            
            var lx2 = (nx2 * 0.5f + (tx / tl) * 0.3f)
            var ly2 = (ny2 * 0.5f + (ty / tl) * 0.3f) + gravWeight
            val lLen2 = sqrt(lx2 * lx2 + ly2 * ly2).coerceAtLeast(0.001f)
            lx2 = (lx2 / lLen2) * leafLen
            ly2 = (ly2 / lLen2) * leafLen

            c.drawLine(x, y, x + lx1, y + ly1, k.line)
            c.drawLine(x, y, x + lx2, y + ly2, k.line)

            sx = x; sy = y
        }
    }
    return bmp
}
