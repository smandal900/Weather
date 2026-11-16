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
import android.graphics.RectF
import android.graphics.Shader
import com.skymood.domain.TimeOfDay
import com.skymood.wallpaper.fauna.GroundSlot
import com.skymood.wallpaper.fauna.GroundY
import com.skymood.wallpaper.fauna.Light
import com.skymood.wallpaper.fauna.MeadowSpec
import com.skymood.wallpaper.fauna.Species
import com.skymood.wallpaper.scenes.SkyGradient
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A forest clearing at a campsite. A ridge tent with a lit doorway, guy ropes and a backpack sits
 * beside a stacked firewood pile; a camper on a log roasts a marshmallow over a stone-ringed fire
 * of leaning logs, with flickering layered flames, glowing coals, drifting smoke and sparks, and a
 * pool of firelight that moves over everything around it.
 *
 * The fire sits between two painted layers: its back logs and stones are painted before the
 * flames, its front logs and stones after, so the flames really burn inside the pile.
 */
class CampingLandscape : PaintedLandscape() {
    override val layers = arrayOf(
        PaintLayer(0.34f, 0.64f, 0.08f),   // snowy peaks
        PaintLayer(0.46f, 0.72f, 0.4f),    // distant forest
        PaintLayer(0.60f, 1f, 0.9f),       // the clearing's grass
        PaintLayer(0.62f, 0.84f, 0.9f),    // the camp: tent, benches, camper, back half of the fire
        PaintLayer(0.70f, 0.84f, 0.9f),    // in front of the flames: front logs and stones, tufts
        PaintLayer(0.60f, 1f, 0.9f),       // the near trees framing the clearing
    )

    private val fx get() = width * 0.68f
    private val fy get() = height * 0.762f
    private val ringRx get() = width * 0.062f
    private val ringRy get() = height * 0.0085f

    // ---- animated parts: paints, shaders and paths are made once, never in a frame
    private val pool = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private val air = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private val bed = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flameOuter = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flameMid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flameInner = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smoke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.TRANSPARENT }
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tongue = Path()

    private var meadowSpec: MeadowSpec? = null
    private var meadowFor = 0
    /** No flowers here, but moths come to the fire after dark and circle it. */
    override fun meadow(): MeadowSpec {
        if (meadowSpec == null || meadowFor != width * 31 + height) {
            meadowFor = width * 31 + height
            meadowSpec = MeadowSpec(emptyList(), lights = listOf(Light(fx, fy - height * 0.04f, width * 0.07f)))
        }
        return meadowSpec!!
    }

    private var slots: List<GroundSlot> = emptyList()
    private var slotsFor = 0
    override fun groundSlots(): List<GroundSlot> {
        if (slotsFor != width * 31 + height) {
            slotsFor = width * 31 + height
            val h = height.toFloat()
            // A fox beyond the fire on the right and a pair of jackals prowling the clearing, all after dark.
            slots = listOf(
                GroundSlot(Species.FOX, 0.74f, 0.94f, GroundY { _ -> h * 0.79f }, 1.2f, 1),
                GroundSlot(Species.JACKAL, 0.3f, 0.7f, GroundY { _ -> h * 0.84f }, 1.3f, 2),
            )
        }
        return slots
    }

    override fun build() {
        pool.shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(0xB0FFB058.toInt(), 0x66FF8A30.toInt(), 0x26FF6A1C.toInt(), 0x00FF5A10),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        air.shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(0x80FFB460.toInt(), 0x3CFF8A30.toInt(), 0x00FF6A1C),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        bed.shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(0xFFFFE08A.toInt(), 0xE0FF9A2E.toInt(), 0x00FF4A10),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        // The flame shaders live in unit space (base at 0, tip at -1) and are scaled with the canvas.
        flameOuter.shader = LinearGradient(0f, 0f, 0f, -1f,
            intArrayOf(0xFFFFE9A0.toInt(), 0xFFFFB02E.toInt(), 0xFFFF6A12.toInt(), 0xE0D8350A.toInt(), 0x00B02008),
            floatArrayOf(0f, 0.2f, 0.55f, 0.85f, 1f), Shader.TileMode.CLAMP)
        flameMid.shader = LinearGradient(0f, 0f, 0f, -1f,
            intArrayOf(0xFFFFF6C8.toInt(), 0xFFFFD25A.toInt(), 0xFFFFA11F.toInt(), 0x00FF8A10),
            floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
        flameInner.shader = LinearGradient(0f, 0f, 0f, -1f,
            intArrayOf(0xFFFFFFF0.toInt(), 0xFFFFF0A8.toInt(), 0x00FFE070),
            floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        glint.shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(0xFFFFE9A0.toInt(), 0xCCFF8A2A.toInt(), 0x00FF4A10),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onLightingChanged() {
        // Smoke is pale grey by day and picks up the fire's orange after dark.
        val c = SkyGradient.blend(lighting.shade(0xFFCACACA.toInt()), 0xFFFFB070.toInt(), 0.32f * fireLevel(lighting.timeOfDay, lighting.windowsLit))
        val rgb = c and 0x00FFFFFF
        smoke.shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(rgb or 0xB0000000.toInt(), rgb or 0x70000000, rgb),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }

    private fun fireLevel(tod: TimeOfDay, lit: Boolean) = when (tod) {
        TimeOfDay.NIGHT -> 1f
        TimeOfDay.DUSK, TimeOfDay.DAWN -> 0.65f
        else -> if (lit) 0.6f else 0.18f
    }

    private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun newPen() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    /** Spreads firelight over [path] from the side at [x0] (facing the fire) to [x1], fading out. */
    private fun warm(k: PaintKit, c: Canvas, path: Path, x0: Float, x1: Float, a: Float) {
        if (a < 0.03f) return
        k.shader = LinearGradient(x0, 0f, x1, 0f, k.alpha(0xFFFFA040.toInt(), (215 * a).toInt()), k.alpha(0xFFFF7A20.toInt(), 0), Shader.TileMode.CLAMP)
        c.drawPath(path, k.fill)
        k.shader = null
    }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(peaks(k), forest(k), ground(k), camp(k), front(k), trees(k)))

    // ------------------------------------------------------------------ backdrop

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

    private fun ground(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(203)
        val ridge = Ridge(k.width, h * 0.68f, h * 0.012f, false, 24)
        k.hill(c, ridge, h, l.ground(0xFF4E7A3A.toInt()), l.ground(0xFF253F1E.toInt()), rnd, 0)
        k.grass(c, ridge, h, 2600, l.ground(0xFF7FA85A.toInt()), l.ground(0xFF1E3518.toInt()), rnd)
        k.flowers(c, ridge, h, 60, rnd)
        return bmp
    }

    private fun trees(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[5]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(207)
        val litP = l.foliage(0xFF4F8A4A.toInt()); val darkP = l.foliage(0xFF183322.toInt()); val trunk = l.shade(0xFF4A3628.toInt())
        for (s in floatArrayOf(-0.03f, 0.07f, 0.93f, 1.03f)) {
            k.conifer(c, w * s, h * 0.98f, h * (0.26f + 0.06f * rnd.nextFloat()), k.jitter(litP, rnd, 0.14f), darkP, trunk, rnd, 1f)
        }
        return bmp
    }

    // ------------------------------------------------------------------ the camp

    private fun camp(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[3]) ?: return null
        val w = k.w; val h = k.h
        val fl = fireLevel(k.l.timeOfDay, k.l.windowsLit)
        val rnd = Random(204)
        earth(c, k)
        tent(c, k, fl)
        backpack(c, k, fl)
        firewood(c, k, fl, rnd)
        bench(c, k, w * 0.755f, w * 0.885f, h * 0.752f, h * 0.017f, fl, grainLeft = true, rnd = rnd)
        camper(c, k, fl)
        bench(c, k, w * 0.50f, w * 0.625f, h * 0.786f, h * 0.02f, fl, grainLeft = false, rnd = rnd)
        ringStones(c, k, fl, front = false)
        logs(c, k, fl, front = false)
        return bmp
    }

    private fun front(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[4]) ?: return null
        val w = k.w; val h = k.h
        val fl = fireLevel(k.l.timeOfDay, k.l.windowsLit)
        val rnd = Random(205)
        logs(c, k, fl, front = true)
        ringStones(c, k, fl, front = true)
        // Grass creeping over the tent hem, the bench feet and the edge of the fire ring.
        tufts(c, k, w * 0.13f, w * 0.47f, h * 0.746f, 90, h * 0.014f, rnd)
        tufts(c, k, fx - ringRx * 1.9f, fx - ringRx * 1.25f, fy + h * 0.008f, 14, h * 0.011f, rnd)
        tufts(c, k, fx + ringRx * 1.25f, fx + ringRx * 1.9f, fy + h * 0.008f, 14, h * 0.011f, rnd)
        tufts(c, k, w * 0.49f, w * 0.64f, h * 0.806f, 50, h * 0.012f, rnd)
        tufts(c, k, w * 0.75f, w * 0.89f, h * 0.772f, 50, h * 0.012f, rnd)
        return bmp
    }

    /** Bare trampled earth around the fire and a worn track to the tent. */
    private fun earth(c: Canvas, k: PaintKit) {
        val l = k.l; val w = k.w; val h = k.h
        val dirt = l.shade(0xFF5B4630.toInt())
        val pen = newPen()
        pen.strokeWidth = w * 0.034f
        pen.color = k.alpha(dirt, 80)
        val track = Path()
        track.moveTo(w * 0.25f, h * 0.752f)
        track.quadTo(w * 0.42f, h * 0.779f, fx - w * 0.075f, fy + h * 0.005f)
        c.drawPath(track, pen)
        k.shader = RadialGradient(0f, 0f, 1f, intArrayOf(k.alpha(dirt, 245), k.alpha(dirt, 215), k.alpha(dirt, 0)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.save(); c.translate(fx, fy + h * 0.003f); c.scale(w * 0.092f, h * 0.0175f)
        c.drawCircle(0f, 0f, 1f, k.fill); c.restore()
        k.shader = null
        // The ash bed inside the ring.
        k.fill.color = l.shade(0xFF2A2623.toInt())
        c.drawOval(fx - ringRx * 0.92f, fy - ringRy * 0.8f, fx + ringRx * 0.92f, fy + ringRy * 1.2f, k.fill)
    }

    /**
     * A ridge tent seen from the front corner: the gable with its doorway pulled open on a lit
     * interior (sleeping bag and a hanging lantern), and the long right-hand slope running back.
     */
    private fun tent(c: Canvas, k: PaintKit, fl: Float) {
        val l = k.l; val w = k.w; val h = k.h; val u = k.unit
        val ax = w * 0.245f; val base = h * 0.742f; val hw = w * 0.105f; val ay = base - h * 0.098f
        val dx = w * 0.115f; val dy = h * 0.017f
        val col = l.shade(0xFFDB7632.toInt())
        val lit = l.windowsLit
        val pen = newPen()

        val foot = Path()
        foot.moveTo(ax - hw - 6f * u, base + 5f * u); foot.lineTo(ax + hw + 10f * u, base + 6f * u)
        foot.lineTo(ax + hw + dx + 14f * u, base - dy + 4f * u); foot.lineTo(ax + dx - hw, base - dy)
        foot.close()
        k.fill.color = k.alpha(Color.BLACK, 85); c.drawPath(foot, k.fill)

        // The long slope.
        val side = Path()
        side.moveTo(ax, ay); side.lineTo(ax + hw, base); side.lineTo(ax + hw + dx, base - dy); side.lineTo(ax + dx, ay - dy); side.close()
        val sideTop = if (k.sunOnLeft) k.darken(col, 0.16f) else k.lighten(col, 0.06f)
        val sideBot = if (k.sunOnLeft) k.darken(col, 0.42f) else k.darken(col, 0.18f)
        k.shader = LinearGradient(ax, ay - dy, ax + hw, base, sideTop, sideBot, Shader.TileMode.CLAMP)
        c.drawPath(side, k.fill); k.shader = null
        pen.strokeWidth = 1.3f * u; pen.color = k.alpha(k.darken(col, 0.55f), 110)
        for (t in floatArrayOf(0.34f, 0.67f)) {
            c.drawLine(lerpF(ax, ax + hw, t), lerpF(ay, base, t), lerpF(ax + dx, ax + hw + dx, t), lerpF(ay - dy, base - dy, t), pen)
        }
        c.drawLine(ax + dx * 0.5f, ay - dy * 0.5f, ax + hw + dx * 0.5f, base - dy * 0.5f, pen)
        warm(k, c, side, ax + hw + dx, ax + hw * 0.4f, fl * 0.5f)

        // The front gable.
        val gable = Path(); gable.moveTo(ax - hw, base); gable.lineTo(ax, ay); gable.lineTo(ax + hw, base); gable.close()
        val gl = if (k.sunOnLeft) k.lighten(col, 0.12f) else k.darken(col, 0.08f)
        val gd = if (k.sunOnLeft) k.darken(col, 0.1f) else k.darken(col, 0.3f)
        k.shader = LinearGradient(ax - hw, 0f, ax + hw, 0f, gl, gd, Shader.TileMode.CLAMP)
        c.drawPath(gable, k.fill); k.shader = null
        warm(k, c, gable, ax + hw, ax - hw * 0.2f, fl * 0.55f)

        // The doorway.
        val dw = hw * 0.40f; val dTop = ay + (base - ay) * 0.14f
        val door = Path(); door.moveTo(ax, dTop); door.lineTo(ax + dw, base); door.lineTo(ax - dw, base); door.close()
        c.save(); c.clipPath(door)
        if (lit) {
            k.shader = RadialGradient(ax, base - (base - ay) * 0.28f, hw * 0.85f,
                intArrayOf(0xFFFFE29A.toInt(), 0xFFF2A144.toInt(), 0xFF8A4318.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        } else {
            k.shader = null; k.fill.color = l.shade(0xFF2A1E16.toInt())
        }
        c.drawRect(ax - dw, dTop, ax + dw, base, k.fill); k.shader = null
        // Sleeping bag and pillow on the floor.
        val bag = l.shade(0xFF35597C.toInt())
        k.fill.color = if (lit) k.mix(bag, 0xFFFFC070.toInt(), 0.3f) else bag
        c.drawRoundRect(RectF(ax - dw * 1.0f, base - (base - ay) * 0.17f, ax + dw * 0.55f, base + 4f * u), 12f * u, 12f * u, k.fill)
        k.fill.color = if (lit) k.mix(0xFFEDE3D0.toInt(), 0xFFFFC070.toInt(), 0.35f) else l.shade(0xFFC9C0B0.toInt())
        c.drawOval(ax - dw * 0.92f, base - (base - ay) * 0.2f, ax - dw * 0.32f, base - (base - ay) * 0.1f, k.fill)
        pen.strokeWidth = 1.4f * u; pen.color = k.alpha(Color.BLACK, 70)
        c.drawLine(ax - dw * 0.1f, base - (base - ay) * 0.15f, ax - dw * 0.1f, base, pen)
        if (lit) {
            val ly = dTop + (base - dTop) * 0.36f
            pen.strokeWidth = 1f * u; pen.color = 0xFF3A2A1C.toInt()
            c.drawLine(ax, dTop, ax + dw * 0.04f, ly - 5f * u, pen)
            k.shader = RadialGradient(ax + dw * 0.04f, ly, w * 0.05f, intArrayOf(0xCCFFF2C0.toInt(), 0x55FFC060, 0x00FFC060), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(ax + dw * 0.04f, ly, w * 0.05f, k.fill); k.shader = null
            k.fill.color = 0xFFFFF6D6.toInt(); c.drawCircle(ax + dw * 0.04f, ly, 4.5f * u, k.fill)
        }
        c.restore()
        // Rolled-back door flaps, with their ties.
        pen.strokeWidth = 4.4f * u; pen.color = k.lighten(col, 0.04f)
        c.drawLine(ax, dTop, ax - dw, base, pen); c.drawLine(ax, dTop, ax + dw, base, pen)
        pen.strokeWidth = 1.2f * u; pen.color = k.alpha(k.darken(col, 0.6f), 190)
        c.drawLine(ax + 2.4f * u, dTop + 3f * u, ax + dw - 1.6f * u, base, pen)
        c.drawLine(ax - 2.4f * u, dTop + 3f * u, ax - dw + 1.6f * u, base, pen)
        pen.strokeWidth = 2f * u; pen.color = l.shade(0xFFE9E2CF.toInt())
        for (sgn in floatArrayOf(-1f, 1f)) {
            val ty = lerpF(dTop, base, 0.55f); val tx = ax + sgn * dw * 0.55f
            c.drawLine(tx, ty, tx + sgn * 6f * u, ty + 8f * u, pen)
        }

        // Seams, ridge, hem and pole tips.
        pen.strokeWidth = 1.8f * u; pen.color = k.alpha(k.darken(col, 0.5f), 210)
        c.drawLine(ax, ay, ax - hw, base, pen); c.drawLine(ax, ay, ax + hw, base, pen)
        pen.strokeWidth = 2.2f * u; pen.color = k.lighten(col, 0.1f)
        c.drawLine(ax, ay, ax + dx, ay - dy, pen)
        pen.strokeWidth = 1.8f * u; pen.color = k.alpha(k.darken(col, 0.65f), 190)
        c.drawLine(ax - hw, base, ax + hw, base, pen); c.drawLine(ax + hw, base, ax + hw + dx, base - dy, pen)
        pen.strokeWidth = 2.8f * u; pen.color = l.shade(0xFF454B54.toInt())
        c.drawLine(ax, ay, ax, ay - h * 0.012f, pen)
        c.drawLine(ax + dx, ay - dy, ax + dx, ay - dy - h * 0.01f, pen)

        // Guy ropes to pegs.
        val rope = k.alpha(l.shade(0xFFE6DFC8.toInt()), 200)
        val pegs = arrayOf(
            floatArrayOf(ax, ay - h * 0.004f, ax - hw * 1.65f, base + h * 0.008f),
            floatArrayOf(ax + dx, ay - dy - h * 0.003f, ax + dx + hw * 1.45f, base - dy + h * 0.008f),
            floatArrayOf(ax + hw + dx * 0.5f, base - dy * 0.5f, ax + hw + dx * 0.5f + hw * 0.5f, base - dy * 0.5f + h * 0.012f),
        )
        for (p in pegs) {
            pen.strokeWidth = 1.2f * u; pen.color = rope
            c.drawLine(p[0], p[1], p[2], p[3], pen)
            pen.strokeWidth = 2.6f * u; pen.color = l.shade(0xFFB03A28.toInt())
            c.drawLine(p[2] - 4f * u, p[3] - 7f * u, p[2] + 1f * u, p[3] + 2f * u, pen)
        }
    }

    /** A red hiking pack standing against the tent: domed lid, side and front pockets, straps. */
    private fun backpack(c: Canvas, k: PaintKit, fl: Float) {
        val l = k.l; val w = k.w; val h = k.h; val u = k.unit
        val x = w * 0.372f; val gy = h * 0.753f
        val bw = w * 0.052f; val bh = h * 0.04f
        val pen = newPen()
        k.fill.color = k.alpha(Color.BLACK, 75); c.drawOval(x - bw * 0.75f, gy - 3f * u, x + bw * 0.95f, gy + 5f * u, k.fill)
        val left = x - bw / 2f; val right = x + bw / 2f; val top = gy - bh
        val col = l.shade(0xFFC13B33.toInt())
        val dark = k.darken(col, 0.3f)
        val rr = bw * 0.3f
        val body = Path()
        body.moveTo(left, gy); body.lineTo(left + 1f * u, top + rr)
        body.quadTo(left + 1f * u, top, left + rr, top); body.lineTo(right - rr, top)
        body.quadTo(right - 1f * u, top, right - 1f * u, top + rr); body.lineTo(right, gy); body.close()
        k.shader = LinearGradient(left, 0f, right, 0f,
            if (k.sunOnLeft) k.lighten(col, 0.1f) else col, k.darken(col, 0.38f), Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill); k.shader = null
        // Side pockets.
        k.fill.color = k.darken(col, 0.16f)
        c.drawRoundRect(RectF(left - 5f * u, gy - bh * 0.52f, left + bw * 0.22f, gy - bh * 0.04f), 5f * u, 5f * u, k.fill)
        c.drawRoundRect(RectF(right - bw * 0.22f, gy - bh * 0.52f, right + 5f * u, gy - bh * 0.04f), 5f * u, 5f * u, k.fill)
        // Front pocket with its zip.
        k.fill.color = k.darken(col, 0.08f)
        c.drawRoundRect(RectF(left + bw * 0.18f, gy - bh * 0.55f, right - bw * 0.18f, gy - bh * 0.05f), 6f * u, 6f * u, k.fill)
        pen.strokeWidth = 1.2f * u; pen.color = k.alpha(dark, 220)
        c.drawLine(left + bw * 0.24f, gy - bh * 0.4f, right - bw * 0.24f, gy - bh * 0.4f, pen)
        // The domed lid and the straps holding it down.
        k.fill.color = dark
        c.drawRoundRect(RectF(left - 2.5f * u, top - 5f * u, right + 2.5f * u, top + bh * 0.26f), rr, rr, k.fill)
        pen.strokeWidth = 2.4f * u; pen.color = k.darken(col, 0.5f)
        c.drawLine(left + bw * 0.3f, top + bh * 0.2f, left + bw * 0.3f, gy - bh * 0.52f, pen)
        c.drawLine(right - bw * 0.3f, top + bh * 0.2f, right - bw * 0.3f, gy - bh * 0.52f, pen)
        pen.strokeWidth = 2.6f * u; pen.color = l.shade(0xFFD9D2BD.toInt())
        c.drawLine(x - 3f * u, top + bh * 0.22f, x + 3f * u, top + bh * 0.22f, pen)
        val p = Path(); p.addRoundRect(RectF(left - 5f * u, top - 5f * u, right + 5f * u, gy), rr, rr, Path.Direction.CW)
        warm(k, c, p, right + 5f * u, left, fl * 0.65f)
    }

    private fun firewood(c: Canvas, k: PaintKit, fl: Float, rnd: Random) {
        val l = k.l; val w = k.w; val h = k.h
        val r = w * 0.0088f
        val x0 = w * 0.412f; val gy = h * 0.7555f
        k.fill.color = k.alpha(Color.BLACK, 75)
        c.drawOval(x0 - r * 0.6f, gy - r * 0.5f, x0 + r * 9.4f, gy + r * 0.7f, k.fill)
        val pen = newPen()
        val counts = intArrayOf(4, 3, 2)
        for (row in counts.indices) {
            for (i in 0 until counts[row]) {
                val cx = x0 + r + row * r + i * 2f * r
                val cy = gy - r - row * 1.72f * r
                pen.strokeWidth = r * 0.28f; pen.color = k.jitter(l.shade(0xFF4E3624.toInt()), rnd, 0.2f)
                k.fill.color = k.jitter(l.shade(0xFFC9A26C.toInt()), rnd, 0.14f)
                c.drawCircle(cx, cy, r, k.fill); c.drawCircle(cx, cy, r, pen)
                pen.strokeWidth = max(1f, r * 0.08f); pen.color = k.alpha(l.shade(0xFF6B4A2C.toInt()), 170)
                c.drawCircle(cx, cy, r * 0.62f, pen); c.drawCircle(cx, cy, r * 0.28f, pen)
                k.fill.color = k.alpha(0xFFFFA040.toInt(), (80 * fl).toInt()); c.drawCircle(cx + r * 0.25f, cy, r * 0.8f, k.fill)
            }
        }
    }

    /** A log bench: a thick bark-covered trunk lying on the ground, one sawn end showing its rings. */
    private fun bench(c: Canvas, k: PaintKit, x0: Float, x1: Float, top: Float, thick: Float, fl: Float, grainLeft: Boolean, rnd: Random) {
        val l = k.l; val u = k.unit
        val bark = l.shade(0xFF5A3E28.toInt())
        val r = thick / 2f
        k.fill.color = k.alpha(Color.BLACK, 75)
        c.drawOval(x0 - r * 0.6f, top + thick - r * 0.35f, x1 + r * 1.1f, top + thick + r * 0.75f, k.fill)
        val body = RectF(x0, top, x1, top + thick)
        k.shader = LinearGradient(0f, top, 0f, top + thick, k.lighten(bark, 0.16f), k.darken(bark, 0.42f), Shader.TileMode.CLAMP)
        c.drawRoundRect(body, r * 0.55f, r * 0.55f, k.fill); k.shader = null
        val pen = newPen()
        pen.strokeWidth = max(1f, 1.3f * u); pen.color = k.alpha(k.darken(bark, 0.55f), 140)
        for (i in 0 until 7) {
            val yy = top + thick * (0.15f + 0.7f * rnd.nextFloat())
            val xs = x0 + (x1 - x0) * rnd.nextFloat() * 0.8f
            c.drawLine(xs, yy, xs + (x1 - x0) * (0.08f + 0.18f * rnd.nextFloat()), yy + thick * 0.04f, pen)
        }
        pen.strokeWidth = max(1f, 2f * u); pen.color = k.alpha(k.lighten(bark, 0.3f), 120)
        c.drawLine(x0 + r, top + thick * 0.12f, x1 - r, top + thick * 0.12f, pen)
        val ex = if (grainLeft) x0 else x1
        val e = RectF(ex - r * 0.5f, top, ex + r * 0.5f, top + thick)
        k.fill.color = l.shade(0xFFC9A777.toInt()); c.drawOval(e, k.fill)
        pen.strokeWidth = max(1f, 1.1f * u); pen.color = k.alpha(l.shade(0xFF7A5A36.toInt()), 190)
        c.drawOval(RectF(ex - r * 0.3f, top + thick * 0.2f, ex + r * 0.3f, top + thick * 0.8f), pen)
        c.drawOval(RectF(ex - r * 0.14f, top + thick * 0.38f, ex + r * 0.14f, top + thick * 0.62f), pen)
        val p = Path(); p.addRoundRect(body, r * 0.55f, r * 0.55f, Path.Direction.CW)
        if (grainLeft) warm(k, c, p, x0, x1, fl * 0.85f) else warm(k, c, p, x1, x0, fl * 0.85f)
    }

    /** A camper in a blue jacket and red beanie, sitting on the far log and toasting a marshmallow. */
    private fun camper(c: Canvas, k: PaintKit, fl: Float) {
        val l = k.l; val w = k.w; val h = k.h
        val u = h * 0.0072f
        val hx = w * 0.822f
        val seat = h * 0.752f
        val ground = seat + h * 0.017f
        val pen = newPen()
        val skin = l.shade(0xFFD9A47E.toInt()); val jacket = l.shade(0xFF3F6FA6.toInt()); val pants = l.shade(0xFF39404C.toInt())
        val boot = l.shade(0xFF2B2420.toInt()); val hat = l.shade(0xFFB5392E.toInt())
        val warmC = 0xFFFFA040.toInt()

        pen.strokeWidth = 2.6f * u; pen.color = pants
        c.drawLine(hx + 0.8f * u, seat - 1.3f * u, hx - 4.6f * u, seat - 1.5f * u, pen)
        pen.strokeWidth = 2.1f * u
        c.drawLine(hx - 4.6f * u, seat - 1.5f * u, hx - 5.2f * u, ground - 1.0f * u, pen)
        pen.strokeWidth = 0.6f * u; pen.color = k.alpha(warmC, (95 * fl).toInt())
        c.drawLine(hx - 0.2f * u, seat - 2.2f * u, hx - 5.0f * u, seat - 2.4f * u, pen)
        c.drawLine(hx - 5.9f * u, seat - 1.0f * u, hx - 6.1f * u, ground - 1.4f * u, pen)
        pen.strokeWidth = 1.9f * u; pen.color = boot
        c.drawLine(hx - 5.1f * u, ground - 0.9f * u, hx - 7.3f * u, ground - 0.7f * u, pen)

        val torso = Path()
        torso.moveTo(hx + 1.9f * u, seat - 0.2f * u)
        torso.quadTo(hx + 2.1f * u, seat - 3.6f * u, hx + 0.9f * u, seat - 6.5f * u)
        torso.quadTo(hx - 0.6f * u, seat - 7.0f * u, hx - 2.4f * u, seat - 6.2f * u)
        torso.quadTo(hx - 2.3f * u, seat - 3.0f * u, hx - 1.3f * u, seat - 0.2f * u)
        torso.close()
        k.shader = LinearGradient(hx - 2.4f * u, 0f, hx + 2.1f * u, 0f, k.lighten(jacket, 0.08f), k.darken(jacket, 0.35f), Shader.TileMode.CLAMP)
        c.drawPath(torso, k.fill); k.shader = null
        warm(k, c, torso, hx - 2.4f * u, hx + 0.8f * u, fl * 0.9f)

        pen.strokeWidth = 1.2f * u; pen.color = skin
        c.drawLine(hx - 1.2f * u, seat - 6.3f * u, hx - 1.4f * u, seat - 7.3f * u, pen)
        val hcx = hx - 1.5f * u; val hcy = seat - 8.3f * u
        k.fill.color = skin; c.drawCircle(hcx, hcy, 1.3f * u, k.fill)
        val nose = Path(); nose.moveTo(hcx - 1.1f * u, hcy - 0.3f * u); nose.lineTo(hcx - 1.9f * u, hcy + 0.35f * u); nose.lineTo(hcx - 1.0f * u, hcy + 0.6f * u); nose.close()
        c.drawPath(nose, k.fill)
        k.shader = RadialGradient(hcx - 1.2f * u, hcy, 2.4f * u, k.alpha(warmC, (190 * fl).toInt()), k.alpha(warmC, 0), Shader.TileMode.CLAMP)
        c.drawCircle(hcx, hcy, 1.35f * u, k.fill); k.shader = null
        val cap = Path(); cap.moveTo(hcx - 1.55f * u, hcy - 0.2f * u)
        cap.quadTo(hcx - 1.2f * u, hcy - 2.3f * u, hcx + 0.1f * u, hcy - 1.9f * u)
        cap.quadTo(hcx + 1.6f * u, hcy - 1.4f * u, hcx + 1.45f * u, hcy - 0.2f * u)
        cap.close()
        k.fill.color = hat; c.drawPath(cap, k.fill)
        warm(k, c, cap, hcx - 1.55f * u, hcx + 1.2f * u, fl * 0.9f)
        // The folded cuff, a shade darker than the cap, and a pom-pom.
        pen.strokeWidth = 0.7f * u; pen.color = k.darken(hat, 0.22f)
        c.drawLine(hcx - 1.45f * u, hcy - 0.55f * u, hcx + 1.4f * u, hcy - 0.55f * u, pen)
        k.fill.color = k.lighten(hat, 0.12f); c.drawCircle(hcx + 0.1f * u, hcy - 2.1f * u, 0.5f * u, k.fill)

        // The arm reaching towards the fire with the toasting stick.
        pen.strokeWidth = 1.9f * u; pen.color = jacket
        c.drawLine(hx - 1.3f * u, seat - 5.8f * u, hx - 1.9f * u, seat - 3.3f * u, pen)
        pen.strokeWidth = 1.6f * u
        c.drawLine(hx - 1.9f * u, seat - 3.3f * u, hx - 4.7f * u, seat - 3.9f * u, pen)
        pen.strokeWidth = 0.8f * u; pen.color = k.alpha(warmC, (210 * fl).toInt())
        c.drawLine(hx - 2.2f * u, seat - 3.8f * u, hx - 4.6f * u, seat - 4.3f * u, pen)
        k.fill.color = skin; c.drawCircle(hx - 4.8f * u, seat - 3.9f * u, 0.7f * u, k.fill)
        pen.strokeWidth = max(1f, 0.36f * u); pen.color = l.shade(0xFF6B4A2C.toInt())
        val tipX = hx - 8.4f * u; val tipY = seat - 3.1f * u
        c.drawLine(hx - 4.8f * u, seat - 3.9f * u, tipX, tipY, pen)
        k.fill.color = l.shade(0xFFF3E9D2.toInt()); c.drawOval(tipX - 0.9f * u, tipY - 0.55f * u, tipX + 0.5f * u, tipY + 0.55f * u, k.fill)
        k.fill.color = k.alpha(0xFF9A5A22.toInt(), (170 * fl + 40).toInt()); c.drawOval(tipX - 0.9f * u, tipY - 0.35f * u, tipX - 0.2f * u, tipY + 0.5f * u, k.fill)
    }

    private class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val th: Float)

    /** Split logs leaning into a pile: three behind the flames, three in front. Tips char and glow. */
    private fun logs(c: Canvas, k: PaintKit, fl: Float, front: Boolean) {
        val l = k.l; val w = k.w; val h = k.h; val u = k.unit
        val th = w * 0.0145f
        val segs = if (!front) listOf(
            Seg(fx - 0.042f * w, fy - 0.001f * h, fx - 0.010f * w, fy - 0.034f * h, th),
            Seg(fx + 0.040f * w, fy - 0.003f * h, fx + 0.012f * w, fy - 0.032f * h, th),
            Seg(fx - 0.016f * w, fy - 0.006f * h, fx + 0.003f * w, fy - 0.038f * h, th * 0.92f),
        ) else listOf(
            Seg(fx - 0.046f * w, fy + 0.0045f * h, fx - 0.012f * w, fy - 0.019f * h, th * 1.05f),
            Seg(fx + 0.047f * w, fy + 0.005f * h, fx + 0.016f * w, fy - 0.016f * h, th * 1.05f),
            Seg(fx - 0.034f * w, fy + 0.0095f * h, fx + 0.036f * w, fy + 0.0065f * h, th * 1.15f),
        )
        val bark = l.shade(0xFF5A3E28.toInt())
        val pen = newPen()
        val rnd = Random(if (front) 311 else 310)
        for (s in segs) {
            val dxs = s.x1 - s.x0; val dys = s.y1 - s.y0
            val len = sqrt(dxs * dxs + dys * dys)
            val nx = -dys / len; val ny = dxs / len     // normal to the log
            pen.strokeWidth = s.th; pen.color = if (front) bark else k.darken(bark, 0.35f)
            c.drawLine(s.x0, s.y0, s.x1, s.y1, pen)
            pen.strokeWidth = s.th * 0.32f; pen.color = k.alpha(k.lighten(bark, 0.28f), if (front) 150 else 80)
            c.drawLine(s.x0 + nx * s.th * 0.2f, s.y0 + ny * s.th * 0.2f, s.x1 + nx * s.th * 0.2f, s.y1 + ny * s.th * 0.2f, pen)
            // Bark cracks.
            pen.strokeWidth = max(1f, 1.2f * u); pen.color = k.alpha(k.darken(bark, 0.6f), 150)
            for (i in 0 until 5) {
                val t = 0.1f + 0.5f * rnd.nextFloat()
                val px = lerpF(s.x0, s.x1, t); val py = lerpF(s.y0, s.y1, t)
                c.drawLine(px - nx * s.th * 0.3f, py - ny * s.th * 0.3f, px + nx * s.th * 0.3f, py + ny * s.th * 0.3f, pen)
            }
            // Charred, glowing top.
            pen.strokeWidth = s.th * 0.98f; pen.color = l.shade(0xFF1A120D.toInt())
            c.drawLine(lerpF(s.x0, s.x1, 0.58f), lerpF(s.y0, s.y1, 0.58f), s.x1, s.y1, pen)
            pen.strokeWidth = max(1f, s.th * 0.16f); pen.color = k.alpha(0xFFFF6A1C.toInt(), (120 + 110 * fl).toInt())
            c.drawLine(lerpF(s.x0, s.x1, 0.64f) + nx * s.th * 0.22f, lerpF(s.y0, s.y1, 0.64f) + ny * s.th * 0.22f, s.x1 + nx * s.th * 0.1f, s.y1 + ny * s.th * 0.1f, pen)
            // The sawn end at the foot.
            val ang = atan2(dys, dxs) * 180f / PI.toFloat()
            c.save(); c.translate(s.x0, s.y0); c.rotate(ang)
            k.fill.color = l.shade(0xFFC9A777.toInt())
            c.drawOval(-s.th * 0.22f, -s.th * 0.5f, s.th * 0.22f, s.th * 0.5f, k.fill)
            pen.strokeWidth = max(1f, 0.9f * u); pen.color = k.alpha(l.shade(0xFF7A5A36.toInt()), 180)
            c.drawOval(-s.th * 0.1f, -s.th * 0.28f, s.th * 0.1f, s.th * 0.28f, pen)
            c.restore()
        }
    }

    private fun ringStones(c: Canvas, k: PaintKit, fl: Float, front: Boolean) {
        val w = k.w
        val n = 14
        val rr = Random(205)
        for (i in 0 until n) {
            val a = (i + (rr.nextFloat() - 0.5f) * 0.4f) / n * 2f * PI.toFloat()
            val sa = sin(a)
            val sz = w * (0.0135f + 0.005f * rr.nextFloat())
            if ((sa > 0.05f) != front) continue
            stone(c, k, fx + cos(a) * ringRx, fy + sa * ringRy + k.h * 0.004f, sz * (0.85f + 0.2f * sa), Random(300 + i), fl)
        }
    }

    /** A rounded fire-ring stone, warmed on the face towards the flames. */
    private fun stone(c: Canvas, k: PaintKit, x: Float, y: Float, sz: Float, rnd: Random, fl: Float) {
        val l = k.l; val u = k.unit
        val n = 8
        val px = FloatArray(n); val py = FloatArray(n)
        for (i in 0 until n) {
            val a = PI.toFloat() * (1f - i / (n - 1f))
            px[i] = x + cos(a) * sz * (0.9f + 0.25f * rnd.nextFloat())
            py[i] = y - sin(a) * sz * 0.72f * (0.8f + 0.4f * rnd.nextFloat())
        }
        val p = Path()
        p.moveTo(px[0], y)
        p.lineTo(px[0], py[0])
        for (i in 1 until n - 1) p.quadTo(px[i], py[i], (px[i] + px[i + 1]) / 2f, (py[i] + py[i + 1]) / 2f)
        p.lineTo(px[n - 1], py[n - 1]); p.lineTo(px[n - 1], y)
        p.quadTo(x, y + sz * 0.3f, px[0], y)
        p.close()
        k.fill.color = k.alpha(Color.BLACK, 80)
        c.drawOval(x - sz * 1.1f, y - sz * 0.1f, x + sz * 1.2f, y + sz * 0.3f, k.fill)
        val base = l.shade(0xFF7B756C.toInt())
        k.shader = LinearGradient(0f, y - sz * 0.75f, 0f, y + sz * 0.2f, k.lighten(base, 0.16f), k.darken(base, 0.32f), Shader.TileMode.CLAMP)
        c.drawPath(p, k.fill); k.shader = null
        val facing = if (x < fx) 1f else -1f
        warm(k, c, p, x + facing * sz, x - facing * sz * 0.3f, fl * 0.95f)
        val pen = newPen()
        pen.strokeWidth = max(1f, 1.2f * u); pen.color = k.alpha(k.lighten(base, 0.4f), 90)
        c.drawLine(px[1], py[1] + 1f, px[n - 2], py[n - 2] + 1f, pen)
    }

    private fun tufts(c: Canvas, k: PaintKit, x0: Float, x1: Float, y: Float, count: Int, hMax: Float, rnd: Random) {
        val l = k.l
        val light = l.ground(0xFF6E9C4C.toInt()); val dark = l.ground(0xFF2A4A22.toInt())
        for (i in 0 until (count * k.unit).toInt().coerceAtLeast(8)) {
            val x = x0 + rnd.nextFloat() * (x1 - x0)
            val yy = y + (rnd.nextFloat() - 0.4f) * hMax * 0.8f
            val bh = hMax * (0.5f + rnd.nextFloat() * 0.7f)
            k.line.strokeWidth = max(1f, (1.2f + rnd.nextFloat() * 1.6f) * k.unit)
            k.line.color = k.mix(dark, light, rnd.nextFloat())
            c.drawLine(x, yy, x + (rnd.nextFloat() - 0.5f) * bh * 0.7f, yy - bh, k.line)
        }
    }

    // ------------------------------------------------------------------ animation

    private companion object {
        const val STAGE_FIRE = 4    // before the front layer: light pool, coal bed, flames
        const val STAGE_GLOW = 5    // after the front layer: light on the logs and stones
        const val STAGE_SMOKE = 6   // after everything: smoke and sparks
        const val PUFFS = 11
        const val SPARKS = 16
        val TONGUE_X = floatArrayOf(-0.021f, -0.011f, 0f, 0.011f, 0.021f)
        val TONGUE_H = floatArrayOf(0.60f, 0.82f, 1.0f, 0.78f, 0.55f)
        val TONGUE_W = floatArrayOf(0.8f, 0.95f, 1.1f, 0.95f, 0.8f)
        val GLINT_X = floatArrayOf(-0.026f, -0.012f, 0.004f, 0.020f, 0.032f, -0.004f, 0.012f)
        val GLINT_Y = floatArrayOf(-0.020f, -0.030f, -0.034f, -0.022f, -0.008f, -0.004f, -0.014f)
    }

    private fun flicker(t: Float) = 0.86f + 0.10f * sin(t * 12.3f) * sin(t * 7.1f + 1f) + 0.05f * sin(t * 29f)

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage < STAGE_FIRE) return
        val w = width.toFloat(); val h = height.toFloat()
        val t = timeMs / 1000f
        val dark = fireLevel(lighting.timeOfDay, lighting.windowsLit)
        val flick = flicker(t)
        val lean = (windKmh / 30f).coerceIn(0f, 1.2f)
        canvas.save(); canvas.translate(shift(0.9f), 0f)
        when (stage) {
            STAGE_FIRE -> fireBody(canvas, w, h, t, dark, flick, lean)
            STAGE_GLOW -> fireGlow(canvas, w, h, t, dark, flick)
            STAGE_SMOKE -> smokeAndSparks(canvas, w, h, t, dark, lean)
        }
        canvas.restore()
    }

    private fun fireBody(canvas: Canvas, w: Float, h: Float, t: Float, dark: Float, flick: Float, lean: Float) {
        // A wide, flat pool of warm light on the ground, stronger the darker it is.
        pool.alpha = (255 * dark * flick).toInt().coerceIn(0, 255)
        canvas.save(); canvas.translate(fx, fy + h * 0.004f); canvas.scale(w * 0.34f, h * 0.052f)
        canvas.drawCircle(0f, 0f, 1f, pool); canvas.restore()
        // The bed of coals.
        bed.alpha = (255 * (0.78f + 0.22f * flick)).toInt().coerceIn(0, 255)
        canvas.save(); canvas.translate(fx, fy - h * 0.002f); canvas.scale(w * 0.05f, h * 0.011f)
        canvas.drawCircle(0f, 0f, 1f, bed); canvas.restore()
        // Flames: five tongues, each drawn as an outer, a middle and an inner layer.
        val baseH = h * 0.066f; val baseHalf = w * 0.0135f
        for (pass in 0..2) {
            val paint = when (pass) { 0 -> flameOuter; 1 -> flameMid; else -> flameInner }
            val sx = when (pass) { 0 -> 1f; 1 -> 0.66f; else -> 0.36f }
            val sy = when (pass) { 0 -> 1f; 1 -> 0.74f; else -> 0.46f }
            for (i in 0 until 5) {
                val hh = baseH * TONGUE_H[i] * (0.80f + 0.20f * sin(t * (7.5f + i * 1.9f) + i * 2.1f) + 0.10f * sin(t * (17f + i * 3.1f) + i)) * (0.9f + 0.1f * flick)
                val sway = sin(t * (4.2f + i * 0.9f) + i * 1.3f) * 1.0f + lean * 2.2f + (i - 2) * 0.25f
                val hw = baseHalf * TONGUE_W[i] * (0.92f + 0.08f * sin(t * (9f + i) + i))
                tongue.rewind()
                tongue.moveTo(-1f, 0f)
                tongue.cubicTo(-1.2f, -0.32f, -0.5f + sway * 0.4f, -0.58f, sway, -1f)
                tongue.cubicTo(0.5f + sway * 0.4f, -0.56f, 1.2f, -0.30f, 1f, 0f)
                tongue.close()
                canvas.save()
                canvas.translate(fx + TONGUE_X[i] * w, fy - h * 0.004f)
                canvas.scale(hw * sx, hh * sy)
                canvas.drawPath(tongue, paint)
                canvas.restore()
            }
        }
    }

    private fun fireGlow(canvas: Canvas, w: Float, h: Float, t: Float, dark: Float, flick: Float) {
        // Light in the air around the fire, over the logs, stones, camper and tent.
        air.alpha = (255 * dark * flick).toInt().coerceIn(0, 255)
        canvas.save(); canvas.translate(fx, fy - h * 0.035f); canvas.scale(w * 0.22f, h * 0.085f)
        canvas.drawCircle(0f, 0f, 1f, air); canvas.restore()
        // Embers pulsing on the logs.
        for (i in GLINT_X.indices) {
            val pulse = 0.5f + 0.5f * sin(t * (1.3f + i * 0.53f) + i * 2.2f)
            glint.alpha = (255 * (0.3f + 0.7f * pulse) * (0.45f + 0.55f * dark)).toInt().coerceIn(0, 255)
            val r = w * (0.0045f + 0.003f * pulse)
            canvas.save(); canvas.translate(fx + GLINT_X[i] * w, fy + GLINT_Y[i] * h); canvas.scale(r, r * 0.8f)
            canvas.drawCircle(0f, 0f, 1f, glint); canvas.restore()
        }
    }

    private fun smokeAndSparks(canvas: Canvas, w: Float, h: Float, t: Float, dark: Float, lean: Float) {
        for (i in 0 until PUFFS) {
            val ph = (t * 0.07f + i / PUFFS.toFloat()) % 1f
            val r = w * (0.014f + 0.058f * sqrt(ph))
            val x = fx + (0.02f + lean * 0.12f) * w * ph + sin(ph * 6f + i * 2.3f) * w * 0.010f * (0.3f + ph)
            val y = fy - h * 0.07f - ph * h * 0.27f
            val env = min(ph / 0.12f, 1f) * (1f - ph) * (1f - ph)
            smoke.alpha = (200 * env).toInt().coerceIn(0, 255)
            canvas.save(); canvas.translate(x, y); canvas.scale(r, r * 0.85f)
            canvas.drawCircle(0f, 0f, 1f, smoke); canvas.restore()
        }
        for (i in 0 until SPARKS) {
            val sp = 0.35f + 0.5f * ((i * 0.618f) % 1f)
            val ph = (t * sp * 0.55f + i * 0.173f) % 1f
            val x = fx + (((i * 0.37f) % 1f) - 0.5f) * w * 0.03f + sin(ph * 8f + i * 3.7f) * w * 0.016f * ph + lean * w * 0.09f * ph * ph
            val y = fy - h * 0.035f - ph * h * (0.10f + 0.12f * ((i * 0.29f) % 1f))
            val a = ((1f - ph) * (1f - ph) * 255f * (0.5f + 0.5f * dark)).toInt().coerceIn(0, 255)
            spark.color = Color.argb(a, 255, (235 - 140 * ph).toInt(), (130 - 110 * ph).toInt())
            canvas.drawCircle(x, y, w * (0.0014f + 0.0012f * ((i * 0.53f) % 1f)) * (1f - 0.5f * ph), spark)
        }
    }
}
