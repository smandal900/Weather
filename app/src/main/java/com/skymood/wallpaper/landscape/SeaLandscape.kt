package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.skymood.domain.SceneType
import com.skymood.wallpaper.fauna.GroundSlot
import com.skymood.wallpaper.fauna.GroundY
import com.skymood.wallpaper.fauna.Species
import com.skymood.wallpaper.scenes.SkyGradient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Daylight colour of the water in the middle distance, and of the water right under the viewer. */
internal const val SEA_FAR = 0xFF1C6C94.toInt()
private const val SEA_NEAR = 0xFF0B3A57.toInt()

/** Where sea meets sky, as a fraction of the screen height. */
private const val SEA_HORIZON = 0.64f

private const val CRESTS = 150
private const val LANES = 16
private const val PATCHES = 8
private const val SPARKLES = 70
private const val GLITTER = 64
private const val CAPS = 48
private const val RING_MAX = 36

private const val REFL_COLS = 2
private const val REFL_ROWS = 36

/** Overall pace of the boats; each type scales it by its own [BoatSpec.speed]. */
private const val BOAT_PACE = 2.5f

/** A boat on the water: which kind, where along the screen, and how it sits on the swell this frame. */
private class Ship(var spec: BoatSpec, var x: Float) {
    var sx = 0f          // screen x, with parallax
    var y = 0f           // waterline y at the middle of the hull
    var pitch = 0f       // degrees, positive = bow down
    var base = 0f        // mean waterline y at this depth
    var amp = 0f         // swell height at this depth
    var kw = 0f          // swell wave number at this depth
}

private fun seaStroke() = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

private fun seaHash(n: Int): Float {
    var x = n * -1640531535
    x = x xor (x ushr 15)
    x *= 0x2C1B3C6D
    x = x xor (x ushr 12)
    x *= 0x297A2D39
    x = x xor (x ushr 15)
    return (x and 0xFFFFFF) / 16777216f
}

/** Height of the swell (-1..1) at screen [x]; the crests, the boat and its wake all ride the same wave. */
private fun swell(x: Float, kw: Float, t: Float): Float =
    0.62f * sin(x * kw - t * 0.9f) + 0.38f * sin(x * kw * 1.73f + t * 1.25f + 2.1f)

/** Waves look longer the closer they are, so the wave number falls with depth (0 horizon .. 1 nearest). */
private fun waveNumber(d: Float, w: Float): Float = (2f * PI.toFloat() / (w * 0.62f)) * (1.75f - d)

private fun waveHeight(d: Float, h: Float, rough: Float): Float = h * 0.0016f * (0.3f + 1.7f * d) * rough

/**
 * Open sea to the horizon with a rocky island and a ship hull-down in the haze. The water is a
 * sky-tinted gradient with soft wind patches and streaks, perspective swell crests that grow with
 * the wind into whitecaps, sparkles, a sun or moon glitter path and rain rings. The optional
 * sloop rides the same swell, pitching as it climbs the waves, and carries its mirror image in
 * the water (a rippling mesh that fades with depth), a bow wave and a wake.
 */
class SeaLandscape(private val withBoat: Boolean) : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.52f, 0.68f, 0.08f))

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
        return listOf(GroundSlot(Species.DOLPHIN, 0.10f, 0.90f, GroundY { _ -> h * 0.82f }, 1f, 3))
    }

    // ------------------------------------------------------------------ animated water

    private val bodyPaint = Paint()
    private val lapPaint = seaStroke()
    private val hazePaint = Paint()
    private val patchLight = Paint(Paint.ANTI_ALIAS_FLAG)
    private val patchDark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightLane = seaStroke()
    private val darkLane = seaStroke()
    private val crestLightFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crestDarkFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crestPath = Path()
    private val sparklePaint = seaStroke()
    private val glitterPaint = seaStroke()
    private val capPaint = seaStroke()
    private val ringPaint = seaStroke()
    private val wakePaint = seaStroke()
    private val foamPaint = seaStroke()
    private val bowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lampPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val reflPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val verts = FloatArray((REFL_COLS + 1) * (REFL_ROWS + 1) * 2)

    private var horizon = 0f
    private var ships: List<Ship> = emptyList()
    private var reflecting = false
    private var rings = 0
    private var lamps = false

    private val crestD = FloatArray(CRESTS)
    private val crestX = FloatArray(CRESTS)
    private val crestLen = FloatArray(CRESTS)
    private val crestPh = FloatArray(CRESTS)

    private val laneD = FloatArray(LANES)
    private val laneX = FloatArray(LANES)
    private val laneLen = FloatArray(LANES)
    private val lanePh = FloatArray(LANES)
    private val laneAlpha = FloatArray(LANES)
    private val laneLight = BooleanArray(LANES)

    private val patchX = FloatArray(PATCHES)
    private val patchD = FloatArray(PATCHES)
    private val patchPh = FloatArray(PATCHES)
    private val patchIsLight = BooleanArray(PATCHES)

    private val spX = FloatArray(SPARKLES)
    private val spD = FloatArray(SPARKLES)
    private val spP = FloatArray(SPARKLES)

    private val glD = FloatArray(GLITTER)
    private val glO = FloatArray(GLITTER)
    private val glP = FloatArray(GLITTER)

    private val capX = FloatArray(CAPS)
    private val capD = FloatArray(CAPS)
    private val capP = FloatArray(CAPS)
    private val capBuf = FloatArray(CAPS * 8)

    private val lapBuf = FloatArray(LAP_SEGMENTS * 4)

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        horizon = h * SEA_HORIZON
        val rnd = Random(12)
        for (i in 0 until CRESTS) {
            // Crests bunch up towards the horizon, like perspective.
            crestD[i] = 0.015f + 0.98f * rnd.nextFloat().pow(1.7f)
            crestX[i] = -w * 0.15f + rnd.nextFloat() * w * 1.3f
            crestLen[i] = 0.55f + rnd.nextFloat() * 0.95f
            crestPh[i] = rnd.nextFloat() * 6.28f
        }
        for (i in 0 until LANES) {
            val r = rnd.nextFloat()
            laneD[i] = 0.03f + 0.95f * r * r
            laneX[i] = -w * 0.15f + rnd.nextFloat() * w * 1.3f
            laneLen[i] = w * (0.14f + 0.3f * rnd.nextFloat())
            lanePh[i] = rnd.nextFloat() * 6.28f
            laneAlpha[i] = 22f + rnd.nextFloat() * 26f
            laneLight[i] = rnd.nextFloat() < 0.5f
        }
        for (i in 0 until PATCHES) {
            patchX[i] = -w * 0.15f + rnd.nextFloat() * w * 1.3f
            patchD[i] = 0.06f + 0.9f * rnd.nextFloat().pow(1.3f)
            patchPh[i] = rnd.nextFloat() * 6.28f
            patchIsLight[i] = i % 2 == 0
        }
        for (i in 0 until SPARKLES) {
            val r = rnd.nextFloat()
            spD[i] = r * r
            spX[i] = -w * 0.15f + rnd.nextFloat() * w * 1.3f
            spP[i] = rnd.nextFloat() * 6.28f
        }
        for (i in 0 until GLITTER) {
            val r = rnd.nextFloat()
            glD[i] = r.pow(1.4f)
            glO[i] = rnd.nextFloat() * 2f - 1f
            glP[i] = rnd.nextFloat() * 6.28f
        }
        for (i in 0 until CAPS) {
            capD[i] = 0.08f + 0.9f * rnd.nextFloat().pow(1.5f)
            capX[i] = -w * 0.15f + rnd.nextFloat() * w * 1.3f
            capP[i] = rnd.nextFloat() * 6.28f
        }
        wakePaint.strokeCap = Paint.Cap.ROUND
        // A different kind of boat each time the scene loads, and another every time it sails off.
        ships = if (withBoat) listOf(Ship(BOATS.random(), w * 0.62f)) else emptyList()
        if (unit <= 0f) ships.forEach { it.x = 0f }
    }

    override fun onLightingChanged() {
        val l = lighting
        val h = height.toFloat()
        val hz = h * SEA_HORIZON
        val sky = l.skyBottom
        val far = l.shade(SEA_FAR)
        val near = l.shade(SEA_NEAR)

        // Water reflects the sky at a grazing angle, so it is palest at the horizon and deepens towards us.
        val body = LinearGradient(
            0f, hz, 0f, h,
            intArrayOf(SkyGradient.blend(sky, far, 0.4f), SkyGradient.blend(sky, far, 0.74f), far, near),
            floatArrayOf(0f, 0.07f, 0.4f, 1f), Shader.TileMode.CLAMP,
        )
        bodyPaint.shader = body
        lapPaint.shader = body

        val skyClear = sky and 0x00FFFFFF
        hazePaint.shader = LinearGradient(
            0f, hz - h * 0.022f, 0f, hz + h * 0.09f,
            intArrayOf(skyClear, skyClear or (170 shl 24), skyClear),
            floatArrayOf(0f, 0.2f, 1f), Shader.TileMode.CLAMP,
        )

        val pl = SkyGradient.blend(sky, Color.WHITE, 0.4f) and 0x00FFFFFF
        patchLight.shader = RadialGradient(0f, 0f, 1f, intArrayOf(pl or (80 shl 24), pl or (34 shl 24), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        val pd = l.shade(0xFF03182A.toInt()) and 0x00FFFFFF
        patchDark.shader = RadialGradient(0f, 0f, 1f, intArrayOf(pd or (96 shl 24), pd or (44 shl 24), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)

        lightLane.color = SkyGradient.blend(sky, Color.WHITE, 0.4f)
        darkLane.color = l.shade(0xFF05202F.toInt())
        crestLightFill.color = SkyGradient.blend(sky, Color.WHITE, 0.55f)
        crestDarkFill.color = l.shade(0xFF041C2A.toInt())
        sparklePaint.color = SkyGradient.blend(sky, Color.WHITE, 0.7f)
        capPaint.color = l.shade(0xFFF4FAFC.toInt())
        ringPaint.color = SkyGradient.blend(sky, Color.WHITE, 0.7f)
        wakePaint.color = l.shade(0xFFEAF6FA.toInt())
        foamPaint.color = l.shade(0xFFF4FAFC.toInt())
        bowPaint.color = l.shade(0xFFF2FAFC.toInt())

        reflecting = l.reflection != 0
        if (reflecting) {
            glitterPaint.color = l.reflection
            val rc = l.reflection and 0x00FFFFFF
            glowPaint.shader = RadialGradient(
                0f, 0f, 1f, intArrayOf(rc or (95 shl 24), rc or (45 shl 24), 0), floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        lamps = l.windowsLit
        if (lamps) {
            val lc = 0xFFFFC870.toInt() and 0x00FFFFFF
            lampPaint.shader = RadialGradient(0f, 0f, 1f, intArrayOf(lc or (120 shl 24), lc or (40 shl 24), 0), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        }
        rings = when (l.weather) {
            SceneType.DRIZZLE -> 12
            SceneType.HEAVY_RAIN -> 28
            SceneType.STORM -> RING_MAX
            else -> 0
        }
    }

    // ------------------------------------------------------------------ painting

    override fun paintAll(k: PaintKit): PaintResult {
        val sprites = if (withBoat) paintBoats(k) else emptyMap()
        return PaintResult(arrayOf(distantSea(k)), sprites)
    }

    /** A rocky island, a smaller islet and a freighter on the horizon, all dissolving into the haze. */
    private fun distantSea(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h; val w = k.w; val unit = k.unit
        val rnd = Random(71)
        val hz = h * SEA_HORIZON

        fun island(cx: Float, half: Float, peak: Float, seed: Int, trees: Int) {
            val r = Random(seed)
            val n = 48
            val xs = FloatArray(n + 1); val ys = FloatArray(n + 1)
            for (i in 0..n) {
                val u = i / n.toFloat()
                xs[i] = cx - half + 2f * half * u
                ys[i] = hz - peak * sin(PI.toFloat() * u).pow(1.35f) * (0.78f + 0.22f * sin(u * 21f + seed) + 0.08f * sin(u * 57f))
            }
            val body = Path()
            body.moveTo(xs[0], hz + 1f)
            for (i in 0..n) body.lineTo(xs[i], ys[i])
            body.lineTo(xs[n], hz + 1f)
            body.close()
            k.shader = LinearGradient(0f, hz - peak, 0f, hz, l.distant(l.foliage(0xFF48705A.toInt()), 0.3f), l.distant(l.ground(0xFF7A7E6E.toInt()), 0.4f), Shader.TileMode.CLAMP)
            c.drawPath(body, k.fill)
            k.shader = null
            // The flank turned away from the sun falls into shade, the other catches a pale rim.
            var top = 0
            for (i in 0..n) if (ys[i] < ys[top]) top = i
            val shadow = Path()
            if (k.sunOnLeft) {
                shadow.moveTo(xs[top], ys[top])
                for (i in top..n) shadow.lineTo(xs[i], ys[i])
                shadow.lineTo(xs[n], hz + 1f)
                shadow.lineTo(xs[top] + half * 0.04f, hz + 1f)
            } else {
                shadow.moveTo(xs[top], ys[top])
                for (i in top downTo 0) shadow.lineTo(xs[i], ys[i])
                shadow.lineTo(xs[0], hz + 1f)
                shadow.lineTo(xs[top] - half * 0.04f, hz + 1f)
            }
            shadow.close()
            k.fill.color = k.alpha(l.distant(0xFF14241E.toInt(), 0.35f), 85)
            c.drawPath(shadow, k.fill)
            val lit = l.distant(l.foliage(0xFF5C8C64.toInt()), 0.34f); val dark = l.distant(l.foliage(0xFF2F5240.toInt()), 0.34f)
            repeat(trees) {
                val u = 0.14f + 0.72f * r.nextFloat()
                val i = (u * n).toInt().coerceIn(0, n)
                k.simplePine(c, xs[i], ys[i] + h * 0.004f, peak * (0.16f + 0.16f * r.nextFloat()), lit, dark, r)
            }
            // Surf breaking at the foot.
            k.line.strokeWidth = 2f * unit
            k.line.color = k.alpha(Color.WHITE, 110)
            c.drawLine(cx - half * 0.96f, hz - 1f, cx + half * 0.96f, hz - 1f, k.line)
        }
        island(w * 0.82f, w * 0.21f, h * 0.05f, 71, 15)
        island(w * 0.5f, w * 0.05f, h * 0.012f, 17, 0)
        freighter(k, c, w * 0.2f, hz, rnd)
        k.haze(c, h * 0.6f, h * 0.67f, 0.38f)
        return bmp
    }

    /** A container ship hull-down on the horizon: a dark hull, stacks of boxes and a bridge at the stern. */
    private fun freighter(k: PaintKit, c: Canvas, cx: Float, hz: Float, rnd: Random) {
        val l = k.l; val u = k.unit
        val len = 96f * u; val hullH = 8f * u
        val mist = 0.6f
        val x0 = cx - len / 2f; val x1 = cx + len / 2f
        val hull = Path()
        hull.moveTo(x0, hz - hullH); hull.lineTo(x1 + 5f * u, hz - hullH); hull.lineTo(x1 - 5f * u, hz + 2f * u); hull.lineTo(x0 + 6f * u, hz + 2f * u); hull.close()
        k.fill.color = l.distant(l.shade(0xFF2E333D.toInt()), mist)
        c.drawPath(hull, k.fill)
        k.fill.color = l.distant(l.shade(0xFF8A2E2A.toInt()), mist)
        c.drawRect(x0 + 5f * u, hz - 2.5f * u, x1 - 6f * u, hz + 1f * u, k.fill)
        val palette = intArrayOf(0xFFC0402F.toInt(), 0xFF2F6AA8.toInt(), 0xFFD8A53A.toInt(), 0xFF3E8A74.toInt(), 0xFFE8E4DA.toInt(), 0xFF7A4A8C.toInt())
        var x = x0 + 8f * u
        while (x < x1 - 34f * u) {
            val cw = 5f * u
            val rows = 1 + rnd.nextInt(3)
            for (r in 0 until rows) {
                k.fill.color = l.distant(l.shade(palette[rnd.nextInt(palette.size)]), mist)
                c.drawRect(x, hz - hullH - (r + 1) * 4.2f * u, x + cw - 0.6f * u, hz - hullH - r * 4.2f * u, k.fill)
            }
            x += cw
        }
        // Bridge block and funnel.
        val bx = x1 - 26f * u
        k.fill.color = l.distant(l.shade(0xFFE6E2D8.toInt()), mist)
        c.drawRect(bx, hz - hullH - 15f * u, x1 - 6f * u, hz - hullH, k.fill)
        k.fill.color = l.distant(l.shade(0xFFC9402F.toInt()), mist)
        c.drawRect(x1 - 15f * u, hz - hullH - 21f * u, x1 - 9f * u, hz - hullH - 15f * u, k.fill)
        k.fill.color = if (l.windowsLit) l.distant(0xFFFFD27A.toInt(), 0.3f) else l.distant(l.shade(0xFF2A3640.toInt()), mist)
        c.drawRect(bx + 1f * u, hz - hullH - 12f * u, x1 - 7f * u, hz - hullH - 9f * u, k.fill)
    }

    // ------------------------------------------------------------------ per-frame drawing

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != 1) return
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        val t = timeMs / 1000f
        val rough = (1f + windKmh / 25f).coerceAtMost(2.8f)
        for (ship in ships) pose(ship, t, w, h, rough)
        drawWater(canvas, t, w, h, unit, rough)
        val hz = horizon
        canvas.drawRect(-w * 0.15f, hz - h * 0.022f, w * 1.15f, hz + h * 0.09f, hazePaint)
        for (ship in ships) drawBoat(canvas, ship, t, unit)
    }

    /** Settles a boat on the swell: it rests on the waves under its bow and stern and so pitches with them. */
    private fun pose(ship: Ship, t: Float, w: Float, h: Float, rough: Float) {
        val sp = ship.spec
        val s = w / 1080f * sp.scale
        ship.base = horizon + (h - horizon) * sp.depth
        ship.kw = waveNumber(sp.depth, w)
        ship.amp = waveHeight(sp.depth, h, rough)
        ship.sx = ship.x + shift(0.6f)
        val xb = ship.sx + sp.bow * s; val xs = ship.sx + sp.stern * s
        val yb = waterAt(ship, xb, t); val ys = waterAt(ship, xs, t)
        ship.y = (yb + ys) / 2f
        ship.pitch = (atan2(yb - ys, xb - xs) * 180f / PI.toFloat() * sp.pitch + sp.trim).coerceIn(-9f, 9f)
    }

    private fun waterAt(ship: Ship, x: Float, t: Float): Float = ship.base + ship.amp * swell(x, ship.kw, t)

    private fun drawWater(canvas: Canvas, t: Float, w: Float, h: Float, unit: Float, rough: Float) {
        val hz = horizon; val wh = h - hz
        val left = -w * 0.15f; val right = w * 1.15f; val span = right - left
        val sh = shift(0.5f)
        canvas.save()
        canvas.clipRect(left, hz, right, h)
        canvas.drawRect(left, hz, right, h, bodyPaint)

        // Broad soft patches of lighter and darker water, as the wind and passing clouds paint the sea.
        val pdrift = (3f + windKmh * 0.25f) * unit
        for (i in 0 until PATCHES) {
            val d = patchD[i]
            val x = left + (patchX[i] - left + t * pdrift * (0.3f + d)) % span
            val pulse = 0.6f + 0.4f * sin(t * 0.3f + patchPh[i])
            val p = if (patchIsLight[i]) patchLight else patchDark
            p.alpha = (255 * pulse).toInt()
            canvas.save()
            canvas.translate(x + sh, hz + d * wh)
            canvas.scale(w * (0.16f + 0.24f * d), h * (0.005f + 0.03f * d))
            canvas.drawCircle(0f, 0f, 1f, p)
            canvas.restore()
        }

        // Long streaks where the wind brushes the surface.
        val ldrift = (3f + windKmh * 0.3f) * unit
        for (i in 0 until LANES) {
            val d = laneD[i]
            val x = left + (laneX[i] - left + t * ldrift * (0.3f + d)) % span
            val len = laneLen[i] * (0.4f + 0.8f * d) * 0.5f
            val p = if (laneLight[i]) lightLane else darkLane
            p.strokeWidth = (1.4f + 6.5f * d) * unit
            p.alpha = (laneAlpha[i] * (0.55f + 0.45f * sin(t * 0.5f + lanePh[i]))).toInt().coerceIn(0, 255)
            val y = hz + d * wh
            canvas.drawLine(x - len + sh, y, x + len + sh, y, p)
        }

        // The boat's mirror image, under the swell and the glitter so the waves break it up.
        for (ship in ships) {
            val sp = ship.spec
            val s = unit * sp.scale
            val refl = sprites[sp.reflKey] ?: continue
            drawReflection(canvas, refl, ship.sx - sp.ox * s, ship.y, t, unit, rough)
            if (lamps) {
                canvas.save()
                canvas.translate(ship.sx, ship.y + 10f * s)
                canvas.scale(sp.w * 0.5f * s, 22f * s)
                lampPaint.alpha = (200 + 40 * sin(t * 2.1f)).toInt().coerceIn(0, 255)
                canvas.drawCircle(0f, 0f, 1f, lampPaint)
                canvas.restore()
            }
        }

        // Swell crests: tapered lens shapes riding the shared wave, a pale lit crest over a dark trough.
        // Each grows out of the water and fades back as its pulse rises and falls, so none pops in.
        val drift = (4f + windKmh * 0.5f) * unit
        for (i in 0 until CRESTS) {
            val d = crestD[i]
            val pulse = 0.5f + 0.5f * sin(t * 0.55f + crestPh[i])
            if (pulse < 0.1f) continue
            val x = left + (crestX[i] - left + t * drift * (0.25f + d)) % span + sh
            val half = unit * (6f + 52f * d) * crestLen[i] * (0.4f + 0.6f * pulse)
            val kw = waveNumber(d, w); val amp = waveHeight(d, h, rough)
            val yb = hz + wh * d
            val xa = x - half; val xc = x + half
            val ya = yb + amp * swell(xa, kw, t); val ym = yb + amp * swell(x, kw, t); val yc = yb + amp * swell(xc, kw, t)
            val tk = unit * (0.45f + 1.9f * d) * (0.6f + 0.4f * pulse)
            val off = (0.9f + 2.6f * d) * unit
            val fade = pulse.coerceAtMost(1f)
            val p = crestPath
            p.rewind()
            p.moveTo(xa + half * 0.1f, ya + off)
            p.quadTo(x, ym + off - tk * 2.6f, xc - half * 0.05f, yc + off)
            p.quadTo(x, ym + off + tk * 1.4f, xa + half * 0.1f, ya + off)
            p.close()
            crestDarkFill.alpha = ((50f + 40f * d) * fade).toInt()
            canvas.drawPath(p, crestDarkFill)
            p.rewind()
            p.moveTo(xa, ya)
            p.quadTo(x, ym - tk * 2.4f, xc, yc)
            p.quadTo(x, ym + tk * 0.5f, xa, ya)
            p.close()
            crestLightFill.alpha = ((60f + 70f * d) * fade).toInt()
            canvas.drawPath(p, crestLightFill)
        }

        // Sparkles of sky catching the ripples.
        val sdrift = t * (6f + windKmh * 0.8f) * unit
        sparklePaint.strokeWidth = 2f * unit.coerceAtLeast(0.6f)
        for (i in 0 until SPARKLES) {
            val a = sin(t * 1.4f + spP[i])
            if (a <= 0.25f) continue
            val d = spD[i]
            val x = left + (spX[i] - left + sdrift * (0.3f + d)) % span
            val len = unit * (5f + 26f * d) * a
            val y = hz + wh * 0.01f + d * wh * 0.97f
            sparklePaint.alpha = ((60f + windKmh * 2f).coerceIn(50f, 150f) * a).toInt().coerceIn(0, 255)
            canvas.drawLine(x - len + sh, y, x + len + sh, y, sparklePaint)
        }

        // Sun or moon: a soft glow and a column of glitter widening towards us.
        if (reflecting) {
            val rx = w * lighting.reflectionX + sh
            canvas.save()
            canvas.translate(rx, hz + wh * 0.3f)
            canvas.scale(w * 0.11f, wh * 0.6f)
            canvas.drawCircle(0f, 0f, 1f, glowPaint)
            canvas.restore()
            for (i in 0 until GLITTER) {
                val a = sin(t * 1.7f + glP[i])
                if (a < 0.1f) continue
                val d = glD[i]
                val y = hz + wh * 0.012f + d * wh * 0.95f
                val spread = w * (0.03f + 0.13f * d)
                val x = rx + glO[i] * spread + sin(t * 0.6f + glP[i] * 2f) * spread * 0.15f
                val len = unit * (5f + 34f * d) * a
                glitterPaint.alpha = (230f * a * (1f - abs(glO[i]) * 0.55f) * (1f - 0.25f * d)).toInt().coerceIn(0, 255)
                glitterPaint.strokeWidth = (1.6f + 2.6f * d) * unit
                canvas.drawLine(x - len, y, x + len, y, glitterPaint)
            }
        }

        // Whitecaps: foam streaks that appear once the wind gets up.
        val caps = ((windKmh - 12f) / 2.2f).toInt().coerceIn(0, CAPS)
        if (caps > 0) {
            val cdrift = (5f + windKmh * 0.6f) * unit
            var n = 0
            for (i in 0 until caps) {
                val a = sin(t * 0.9f + capP[i])
                if (a < 0.15f) continue
                val d = capD[i]
                val x = left + (capX[i] - left + t * cdrift * (0.3f + d)) % span + sh
                val len = unit * (8f + 48f * d) * a
                val amp = waveHeight(d, h, rough); val kw = waveNumber(d, w)
                val y = hz + wh * d + amp * swell(x, kw, t)
                val off = (1.4f + 3.4f * d) * unit
                capBuf[n++] = x - len; capBuf[n++] = y; capBuf[n++] = x + len * 0.7f; capBuf[n++] = y
                capBuf[n++] = x - len * 0.5f; capBuf[n++] = y + off; capBuf[n++] = x + len * 0.3f; capBuf[n++] = y + off
            }
            capPaint.strokeWidth = (1.6f + 3.2f * 0.5f) * unit
            capPaint.alpha = (110 + windKmh * 1.5f).toInt().coerceAtMost(210)
            if (n > 0) canvas.drawLines(capBuf, 0, n, capPaint)
        }

        // Rings from falling rain.
        if (rings > 0) {
            ringPaint.strokeWidth = 1.4f * unit.coerceAtLeast(0.6f)
            for (i in 0 until rings) {
                val x0 = t / (0.8f + 0.9f * seaHash(i * 7 + 1)) + seaHash(i * 13 + 2)
                val cycle = floor(x0).toInt()
                val u = x0 - cycle
                val d = seaHash(i * 53 + cycle * 331 + 5).pow(1.5f)
                val px = -w * 0.05f + seaHash(i * 101 + cycle * 977 + 3) * w * 1.1f
                val py = hz + wh * 0.015f + d * wh * 0.96f
                val rx = (3f + 22f * u) * unit * (0.35f + 1.1f * d)
                val ry = rx * (0.22f + 0.15f * d)
                ringPaint.alpha = (150f * (1f - u) * (0.5f + 0.5f * d)).toInt().coerceIn(0, 255)
                canvas.drawOval(px - rx, py - ry, px + rx, py + ry, ringPaint)
            }
        }
        canvas.restore()
    }

    /**
     * Draws the mirror image whose top-left is ([left], [top]). Each row is shoved sideways by a travelling
     * wave that grows with depth, which is what makes a reflection shimmer and break up.
     */
    private fun drawReflection(canvas: Canvas, bmp: Bitmap, left: Float, top: Float, t: Float, unit: Float, rough: Float) {
        val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
        var i = 0
        for (r in 0..REFL_ROWS) {
            val f = r / REFL_ROWS.toFloat()
            val phase = f * 18f
            val dx = unit * (0.6f + 7f * f) * rough * (0.7f * sin(phase - t * 1.1f) + 0.3f * sin(phase * 2.3f + t * 1.7f))
            val dy = sin(phase * 1.3f - t * 1.5f) * unit * 1.4f * f
            for (c in 0..REFL_COLS) {
                verts[i++] = left + bw * c / REFL_COLS + dx
                verts[i++] = top + bh * f + dy
            }
        }
        canvas.drawBitmapMesh(bmp, REFL_COLS, REFL_ROWS, verts, 0, null, 0, reflPaint)
    }

    private fun drawBoat(canvas: Canvas, ship: Ship, t: Float, unit: Float) {
        val sp = ship.spec
        val boat = sprites[sp.boatKey] ?: return
        val s = unit * sp.scale
        val sx = ship.sx
        val xs = sx + sp.stern * s; val xb = sx + sp.bow * s
        val speed = sailSpeed(sp)

        // Wake: foam streaks trailing from the stern, widening and fading; long and churned on a fast boat.
        val wakeLen = (70f + speed * 5f).coerceAtMost(240f) * sp.wake * s
        for (j in 0 until 3) {
            var lx = xs - 4f * s
            var ly = waterAt(ship, lx, t) + (1.5f + j * 3.2f) * s
            for (i in 1..10) {
                val u = i / 10f
                val x1 = xs - wakeLen * u * (1f - 0.12f * j)
                val y1 = waterAt(ship, x1, t) + (1.5f + j * 3.2f + u * (3f + 2.5f * j)) * s
                wakePaint.strokeWidth = max(1f, (2.4f - 1.4f * u) * s)
                wakePaint.alpha = (160f * (1f - u) * (0.7f + 0.3f * sin(t * 2f + i + j * 2f))).toInt().coerceIn(0, 255)
                canvas.drawLine(lx, ly, x1, y1, wakePaint)
                lx = x1; ly = y1
            }
        }

        // A fast boat churns the water white right behind its stern.
        if (sp.speed > 2f) {
            val ys = waterAt(ship, xs, t)
            bowPaint.alpha = (130 + 40 * sin(t * 6f)).toInt()
            canvas.drawOval(xs - 34f * s, ys - 1.5f * s, xs + 4f * s, ys + 6f * s, bowPaint)
            canvas.drawOval(xs - 56f * s, ys + 1f * s, xs - 20f * s, ys + 7f * s, bowPaint)
        }

        // The hull, pitching with the swell about its waterline.
        canvas.save()
        canvas.translate(sx, ship.y)
        canvas.rotate(ship.pitch)
        canvas.drawBitmap(boat, -sp.ox * s, -sp.oy * s, spritePaint)
        canvas.restore()

        // Water lapping over the hull's bottom edge, in exactly the colour of the water behind it.
        lapPaint.strokeWidth = 8f * s
        val x0 = sx + (sp.stern - 28f) * s; val x1 = sx + (sp.bow + 18f) * s
        var n = 0
        var lx = x0; var ly = waterAt(ship, lx, t) + 1.5f * s
        for (i in 1..LAP_SEGMENTS) {
            val x = x0 + (x1 - x0) * i / LAP_SEGMENTS
            val y = waterAt(ship, x, t) + 1.5f * s
            lapBuf[n++] = lx; lapBuf[n++] = ly; lapBuf[n++] = x; lapBuf[n++] = y
            lx = x; ly = y
        }
        canvas.drawLines(lapBuf, 0, n, lapPaint)

        // A thin line of foam where the hull meets the water, broken up by the ripples.
        foamPaint.strokeWidth = max(1f, 1.6f * s)
        n = 0
        lx = x0 + 12f * s; ly = waterAt(ship, lx, t) - 0.6f * s
        for (i in 1..LAP_SEGMENTS) {
            val x = x0 + 12f * s + (x1 - x0 - 24f * s) * i / LAP_SEGMENTS
            val y = waterAt(ship, x, t) - 0.6f * s
            if (sin(t * 1.6f + x * 0.06f) > -0.2f) { lapBuf[n++] = lx; lapBuf[n++] = ly; lapBuf[n++] = x; lapBuf[n++] = y }
            lx = x; ly = y
        }
        foamPaint.alpha = 150
        canvas.drawLines(lapBuf, 0, n, foamPaint)

        // Bow wave piling up against the stem, with a little spray.
        val by = waterAt(ship, xb, t)
        val swellUp = (0.75f + 0.25f * sin(t * 2.3f)) * sp.wake
        bowPaint.alpha = 120
        canvas.drawOval(xb - 20f * s * swellUp, by - 0.8f * s, xb + 1f * s, by + 2.6f * s, bowPaint)
        foamPaint.strokeWidth = max(1f, 1.3f * s)
        foamPaint.alpha = 140
        canvas.drawLine(xb - 2f * s, by - 2f * s, xb - 9f * s * swellUp, by - 8f * s * swellUp, foamPaint)
        canvas.drawLine(xb - 6f * s, by - 1.4f * s, xb - 15f * s * swellUp, by - 5f * s * swellUp, foamPaint)
    }

    /** How fast a boat sails, in pixels per second on a 1080 wide screen: a steady base, quickened by the wind, times its own pace. */
    private fun sailSpeed(spec: BoatSpec) = (8f + windKmh * 0.6f) * BOAT_PACE * spec.speed

    /** Each time the sea comes back on screen a different kind of boat is out on it, part-way across. */
    override fun onShown() {
        val ship = ships.firstOrNull() ?: return
        ship.spec = BOATS.filter { it !== ship.spec }.random()
        ship.x = width * (0.3f + 0.4f * Random.nextFloat())
    }

    /** Half the width of a boat's sprite on screen, plus room for the parallax shift: how far off-screen it must be to be gone. */
    private fun clearance(spec: BoatSpec) = spec.w * 0.5f * spec.scale * width / 1080f + width * 0.05f

    override fun update(dtMs: Long) {
        super.update(dtMs)
        for (ship in ships) {
            ship.x += sailSpeed(ship.spec) * (width / 1080f) * dtMs / 1000f
            if (ship.x - clearance(ship.spec) > width) {
                // Sailed off: the next boat to come along is a different kind, entering from the other side.
                ship.spec = BOATS.filter { it !== ship.spec }.random()
                ship.x = -clearance(ship.spec)
            }
        }
    }

    private companion object {
        const val LAP_SEGMENTS = 16
    }
}
