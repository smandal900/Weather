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
import com.skymood.wallpaper.fauna.GroundSlot
import com.skymood.wallpaper.fauna.GroundY
import com.skymood.wallpaper.fauna.Species
import kotlin.random.Random

private const val DEEP_SEA = 0xFF123E5C.toInt()
private const val DEEP_LAKE = 0xFF173F4A.toInt()

private fun stroke() = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
private fun fillPaint() = Paint(Paint.ANTI_ALIAS_FLAG)

/** Where the sand meets the sea, shared by the painted sand and the animated foam. */
private fun shoreEdge(x: Float, w: Float, h: Float): Float =
    h * 0.83f + h * 0.012f * sin(x / w * 3.2f + 0.6f) + h * 0.014f * (x / w)

/** Sea washing onto a painted sandy beach with a leafy headland, wet sand, foam and a palm tree. */
class BeachLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.5f, 0.68f, 0.1f), PaintLayer(0.78f, 1f, 1f))

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
        return listOf(GroundSlot(Species.CRAB, 0.08f, 0.7f, GroundY { x -> shoreEdge(x, w, h) + h * 0.07f }, 1.2f, 3))
    }

    private val water = Water()
    private val foamLines = FloatArray(FOAM_SEGMENTS * 4)
    private val foamPaint = stroke()
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        water.build(-w * 0.1f, w * 1.1f, h * 0.66f, h * 0.86f, unit)
        foamPaint.strokeWidth = 3f * unit.coerceAtLeast(0.6f)
    }

    override fun onLightingChanged() {
        water.applyLighting(lighting, DEEP_SEA, width)
        foamPaint.color = lighting.shade(0xFFFFFFFF.toInt())
    }

    override fun paintAll(k: PaintKit): PaintResult {
        val palm = palm(k)
        return PaintResult(arrayOf(headland(k), sand(k)), if (palm != null) mapOf("palm" to palm) else emptyMap())
    }

    private fun headland(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(61)
        val horizon = h * 0.66f
        val n = 60
        val xs = FloatArray(n + 1); val ys = FloatArray(n + 1)
        for (i in 0..n) {
            val u = i / n.toFloat()
            xs[i] = -w * 0.1f + u * w * 0.5f
            ys[i] = horizon - h * 0.045f * sin(PI.toFloat() * u).pow(1.3f) * (0.85f + 0.15f * sin(u * 17f))
        }
        val body = Path()
        body.moveTo(xs[0], horizon + 2f)
        for (i in 0..n) body.lineTo(xs[i], ys[i])
        body.lineTo(xs[n], horizon + 2f)
        body.close()
        k.shader = LinearGradient(0f, horizon - h * 0.05f, 0f, horizon, l.distant(l.foliage(0xFF4F7A55.toInt()), 0.45f), l.distant(l.ground(0xFF8A8478.toInt()), 0.5f), Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null
        val lit = l.distant(l.foliage(0xFF5F9A5E.toInt()), 0.4f); val dark = l.distant(l.foliage(0xFF2F5A3C.toInt()), 0.4f)
        for (i in 2 until n - 2 step 3) {
            val th = h * (0.012f + rnd.nextFloat() * 0.014f)
            k.broadleaf(c, xs[i], ys[i] + th * 0.2f, th, k.jitter(lit, rnd, 0.18f), dark, dark, rnd, 0.15f)
        }
        k.haze(c, h * 0.58f, h * 0.68f, 0.45f)
        return bmp
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
        // Wet sand: dark and glossy at the waterline, fading into dry sand.
        c.save()
        c.clipPath(body)
        k.shader = LinearGradient(0f, h * 0.82f, 0f, h * 0.9f, l.ground(0xFF8C7650.toInt()), k.alpha(l.ground(0xFFB59B6E.toInt()), 0), Shader.TileMode.CLAMP)
        c.drawRect(-k.margin, h * 0.8f, w + k.margin, h * 0.92f, k.fill)
        k.shader = null
        // Ripples in the sand.
        k.line.strokeWidth = 1.5f * k.unit
        repeat(90) {
            val x = rnd.nextFloat() * w
            val y = shoreEdge(x, w, h) + h * (0.03f + rnd.nextFloat() * 0.14f)
            k.line.color = k.alpha(if (rnd.nextBoolean()) Color.BLACK else Color.WHITE, 22)
            c.drawLine(x, y, x + w * (0.04f + rnd.nextFloat() * 0.08f), y + h * 0.002f, k.line)
        }
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
        return bmp
    }

    private fun palm(k: PaintKit): Bitmap? = paintPalm(k, 63)

    private companion object {
        const val FOAM_SEGMENTS = 24
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        if (stage == 1) {
            water.draw(canvas, timeMs, windKmh, shift(0.4f))
            return
        }
        if (stage != layers.size) return
        val t = timeMs / 1000f
        val reach = sin(t * 0.7f) * h * 0.008f
        var fn = 0
        var fpx = 0f; var fpy = 0f
        for (i in 0..FOAM_SEGMENTS) {
            val x = -w * 0.1f + w * 1.2f * i / FOAM_SEGMENTS
            val y = shoreEdge(x, w, h) - h * 0.004f + reach + sin(x * 0.02f + t * 2f) * h * 0.002f
            if (i > 0) { foamLines[fn++] = fpx; foamLines[fn++] = fpy; foamLines[fn++] = x; foamLines[fn++] = y }
            fpx = x; fpy = y
        }
        foamPaint.alpha = (170 + 60 * sin(t * 0.7f)).toInt()
        canvas.save()
        canvas.translate(shift(0.8f), 0f)
        canvas.drawLines(foamLines, 0, fn, foamPaint)
        canvas.restore()
        val palm = sprites["palm"] ?: return
        canvas.save()
        canvas.translate(w * 0.82f + shift(1f), h * 0.93f)
        canvas.rotate(sin(t * (0.8f + windKmh / 30f)) * (0.8f + windKmh / 14f))
        canvas.drawBitmap(palm, -palm.width * 0.55f, -palm.height + 2f, spritePaint)
        canvas.restore()
    }
}

/** Open water to the horizon with a distant island; optionally a painted sailing boat on the swell. */
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
        return listOf(GroundSlot(Species.DOLPHIN, 0.15f, 0.85f, GroundY { _ -> h * 0.7f }, 1f, 1))
    }

    private val water = Water(maxGlints = 64)
    private val swellLines = FloatArray(SWELL_SEGMENTS * 4)
    private val swellPaint = stroke()
    private val lampPaint = fillPaint()
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var horizon = 0f
    private var boatX = 0f

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        horizon = h * 0.64f
        water.build(-w * 0.1f, w * 1.1f, horizon, h, unit)
        swellPaint.strokeWidth = 2.5f * unit.coerceAtLeast(0.6f)
        boatX = w * 0.62f
    }

    override fun onLightingChanged() {
        val l = lighting
        water.applyLighting(l, DEEP_SEA, width)
        swellPaint.color = l.shade(0xFFD8ECF5.toInt())
        lampPaint.color = if (l.windowsLit) 0xFFFFD27A.toInt() else 0
    }

    override fun paintAll(k: PaintKit): PaintResult {
        val boat = if (withBoat) boat(k) else null
        return PaintResult(arrayOf(island(k)), if (boat != null) mapOf("boat" to boat) else emptyMap())
    }

    /** A small distant island with trees, almost lost in haze. */
    private fun island(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(71)
        val hz = h * 0.64f
        val cx = w * 0.82f
        val half = w * 0.2f
        val n = 40
        val body = Path()
        body.moveTo(cx - half, hz + 1f)
        for (i in 0..n) {
            val u = i / n.toFloat()
            body.lineTo(cx - half + 2f * half * u, hz - h * 0.045f * sin(PI.toFloat() * u).pow(1.4f) * (0.8f + 0.2f * sin(u * 23f)))
        }
        body.lineTo(cx + half, hz + 1f)
        body.close()
        k.shader = LinearGradient(0f, hz - h * 0.03f, 0f, hz, l.distant(l.foliage(0xFF486E58.toInt()), 0.55f), l.distant(l.ground(0xFF7C8070.toInt()), 0.6f), Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null
        val lit = l.distant(l.foliage(0xFF5A8A62.toInt()), 0.55f); val dark = l.distant(l.foliage(0xFF2F5240.toInt()), 0.55f)
        for (i in 0 until 14) {
            val u = 0.15f + 0.7f * rnd.nextFloat()
            val x = cx - half + 2f * half * u
            val top = hz - h * 0.045f * sin(PI.toFloat() * u).pow(1.4f)
            k.simplePine(c, x, top + h * 0.004f, h * (0.008f + 0.008f * rnd.nextFloat()), lit, dark, rnd)
        }
        k.haze(c, h * 0.6f, h * 0.67f, 0.38f)
        return bmp
    }

    /** A wooden sailboat: planked hull, shaded main sail and jib, mast, boom, rigging and a pennant. */
    private fun boat(k: PaintKit): Bitmap? {
        val s = k.unit * 1.15f
        val bw = (280 * s).toInt(); val bh = (240 * s).toInt()
        val (c, bmp) = k.newSprite(bw, bh) ?: return null
        val l = k.l
        val ox = bw / 2f; val oy = bh * 0.9f
        fun px(x: Float) = ox + x * s
        fun py(y: Float) = oy + y * s
        val hull = Path()
        hull.moveTo(px(-100f), py(-6f)); hull.lineTo(px(100f), py(-6f)); hull.lineTo(px(72f), py(24f)); hull.lineTo(px(-68f), py(24f)); hull.close()
        val wood = l.shade(0xFF8A4B2A.toInt())
        k.shader = LinearGradient(0f, py(-6f), 0f, py(24f), k.lighten(wood, 0.1f), k.darken(wood, 0.35f), Shader.TileMode.CLAMP)
        c.drawPath(hull, k.fill)
        k.shader = null
        k.line.strokeWidth = max(1f, 1.5f * s)
        k.line.color = k.alpha(Color.BLACK, 70)
        for (i in 1..3) c.drawLine(px(-100f + i * 9f), py(-6f + i * 7f), px(100f - i * 9f), py(-6f + i * 7f), k.line)
        k.fill.color = l.shade(0xFFF2EDE0.toInt())
        c.drawRect(px(-98f), py(-8f), px(98f), py(-3f), k.fill)
        // Sails: sun-side half lighter.
        val sail = l.shade(0xFFF4EFE2.toInt())
        val main = Path()
        main.moveTo(px(6f), py(-175f)); main.quadTo(px(88f), py(-70f), px(82f), py(-16f)); main.lineTo(px(6f), py(-16f)); main.close()
        k.shader = LinearGradient(px(6f), 0f, px(84f), 0f, if (k.sunOnLeft) sail else k.darken(sail, 0.28f), if (k.sunOnLeft) k.darken(sail, 0.28f) else sail, Shader.TileMode.CLAMP)
        c.drawPath(main, k.fill)
        val jib = Path()
        jib.moveTo(px(-6f), py(-158f)); jib.quadTo(px(-72f), py(-70f), px(-80f), py(-16f)); jib.lineTo(px(-6f), py(-16f)); jib.close()
        k.shader = LinearGradient(px(-80f), 0f, px(-6f), 0f, if (k.sunOnLeft) sail else k.darken(sail, 0.28f), if (k.sunOnLeft) k.darken(sail, 0.28f) else sail, Shader.TileMode.CLAMP)
        c.drawPath(jib, k.fill)
        k.shader = null
        // Seams in the sails.
        k.line.color = k.alpha(Color.BLACK, 28)
        for (i in 1..4) {
            val y = -16f - i * 30f
            c.drawLine(px(6f), py(y), px(6f + (78f * (1f - (-y - 16f) / 160f)).coerceAtLeast(6f)), py(y + 4f), k.line)
        }
        // Mast, boom, rigging, pennant.
        val mast = l.shade(0xFF4A3A2A.toInt())
        k.line.color = mast
        k.line.strokeWidth = max(1.5f, 3f * s)
        c.drawLine(px(0f), py(-6f), px(0f), py(-182f), k.line)
        c.drawLine(px(0f), py(-18f), px(86f), py(-14f), k.line)
        k.line.strokeWidth = max(1f, 1f * s)
        k.line.color = k.alpha(Color.BLACK, 110)
        c.drawLine(px(0f), py(-180f), px(-95f), py(-6f), k.line)
        c.drawLine(px(0f), py(-180f), px(92f), py(-6f), k.line)
        val flag = Path()
        flag.moveTo(px(0f), py(-182f)); flag.lineTo(px(-26f), py(-176f)); flag.lineTo(px(0f), py(-170f)); flag.close()
        k.fill.color = l.shade(0xFFD6402F.toInt())
        c.drawPath(flag, k.fill)
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != 1) return
        water.draw(canvas, timeMs, windKmh, shift(0.5f))
        val t = timeMs / 1000f
        val w = width.toFloat(); val h = height.toFloat()
        val rough = 1f + windKmh / 25f
        for (row in 0 until 4) {
            val f = (row + 1) / 5f
            val y0 = horizon + (h - horizon) * f * f
            val amp = h * 0.004f * (1f + row) * rough
            var n = 0
            var px = 0f; var py = 0f
            for (i in 0..SWELL_SEGMENTS) {
                val x = -w * 0.1f + w * 1.2f * i / SWELL_SEGMENTS
                val y = y0 + sin(x * (0.012f / (1f + row * 0.4f)) + t * (0.8f + row * 0.15f) + row) * amp
                if (i > 0) { swellLines[n++] = px; swellLines[n++] = py; swellLines[n++] = x; swellLines[n++] = y }
                px = x; py = y
            }
            swellPaint.alpha = (35 + row * 18).coerceAtMost(130)
            canvas.save()
            canvas.translate(shift(f), 0f)
            canvas.drawLines(swellLines, 0, n, swellPaint)
            canvas.restore()
        }
        val boat = sprites["boat"]
        if (withBoat && boat != null) {
            val s = w / 1080f * 1.15f
            val by = h * 0.78f + sin(t * 1.3f) * h * 0.004f * rough
            canvas.save()
            canvas.translate(boatX + shift(0.7f), by)
            canvas.rotate(sin(t * 1.1f) * 2.5f * rough)
            canvas.drawBitmap(boat, -boat.width / 2f, -boat.height * 0.9f, spritePaint)
            if (lampPaint.color != 0) canvas.drawCircle(0f, -150f * s, w / 200f, lampPaint)
            canvas.restore()
        }
    }

    private companion object {
        const val SWELL_SEGMENTS = 14
    }

    override fun update(dtMs: Long) {
        super.update(dtMs)
        if (withBoat) {
            boatX += (8f + windKmh * 0.6f) * (width / 1080f) * dtMs / 1000f
            if (boatX > width * 1.15f) boatX = -width * 0.15f
        }
    }
}

/** A still lake mirroring a mountain and forest shore, with reeds and rocks on the near bank. */
class LakeLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.42f, 0.66f, 0.05f), PaintLayer(0.82f, 1f, 1f))

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
        return listOf(GroundSlot(Species.DUCK, 0.15f, 0.85f, GroundY { _ -> h * 0.77f }, 1.3f, 3))
    }

    private val water = Water()
    private val reeds = Path()
    private val reedPaint = stroke()
    private val mirror = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 95 }
    private var horizon = 0f
    private var shoreTop = 0f

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        horizon = h * 0.66f
        shoreTop = h * 0.86f
        water.build(-w * 0.1f, w * 1.1f, horizon, shoreTop + h * 0.02f, unit)
        reeds.rewind()
        val rnd = Random(81)
        var x = -w * 0.05f
        while (x < w * 1.05f) {
            if (rnd.nextFloat() < 0.5f) {
                val rh = h * (0.03f + rnd.nextFloat() * 0.04f)
                reeds.moveTo(x, shoreTop + h * 0.035f)
                reeds.quadTo(x + rh * 0.1f, shoreTop + h * 0.035f - rh * 0.6f, x + rh * 0.2f, shoreTop + h * 0.035f - rh)
            }
            x += 9f * unit
        }
        reedPaint.strokeWidth = 2.5f * unit.coerceAtLeast(0.6f)
    }

    override fun onLightingChanged() {
        water.applyLighting(lighting, DEEP_LAKE, width)
        reedPaint.color = lighting.foliage(0xFF5E8A38.toInt())
    }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(farShore(k), nearBank(k)))

    private fun farShore(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(82)
        val horizon = h * 0.66f
        k.mountain(
            c, Ridge(k.width, horizon, h * 0.14f, true, 30), horizon + 1f,
            l.distant(l.ground(0xFF7B8CA4.toInt()), 0.4f), l.distant(l.ground(0xFF566880.toInt()), 0.4f), rnd,
            snowLine = horizon - h * 0.115f,
        )
        // Forest at the water's edge.
        val shore = Ridge(k.width, horizon, h * 0.01f, false, 31)
        val lit = l.distant(l.foliage(0xFF3F7550.toInt()), 0.25f); val dark = l.distant(l.foliage(0xFF1C3A2A.toInt()), 0.25f)
        k.fill.color = l.distant(l.foliage(0xFF24452F.toInt()), 0.25f)
        c.drawRect(-k.margin, horizon - h * 0.006f, k.w + k.margin, horizon + 1f, k.fill)
        var x = -k.margin
        while (x < k.w + k.margin) {
            val th = h * (0.02f + rnd.nextFloat() * 0.03f)
            k.conifer(c, x, shore.yAt(x) + 1f, th, k.jitter(lit, rnd, 0.2f), dark, l.shade(0xFF33261C.toInt()), rnd, 0.3f)
            x += (9f + rnd.nextFloat() * 8f) * k.unit
        }
        k.haze(c, h * 0.5f, horizon, 0.3f)
        return bmp
    }

    private fun nearBank(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(83)
        val ridge = Ridge(k.width, h * 0.89f, h * 0.02f, false, 33)
        k.hill(c, ridge, h, l.ground(0xFF4E7A3A.toInt()), l.ground(0xFF21401C.toInt()), rnd, 300)
        for (i in 0 until 9) {
            val x = w * (i / 8f) + (rnd.nextFloat() - 0.5f) * w * 0.08f
            k.rock(c, x, ridge.yAt(x) + h * 0.012f, w * (0.012f + 0.02f * rnd.nextFloat()), l.ground(0xFF7A7F86.toInt()), rnd)
        }
        k.grass(c, ridge, h, 1600, l.ground(0xFF7FA85A.toInt()), l.ground(0xFF1E3518.toInt()), rnd)
        k.flowers(c, ridge, h, 40, rnd)
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        val w = width.toFloat()
        if (stage == 1) {
            water.draw(canvas, timeMs, windKmh, shift(0.5f))
            val far = bitmap(0) ?: return
            // Mirror image of the far shore, squashed and faint, clipped to the water.
            canvas.save()
            canvas.clipRect(0f, horizon, w, shoreTop + height * 0.02f)
            canvas.translate(0f, horizon)
            canvas.scale(1f, -0.7f)
            canvas.translate(0f, -horizon)
            canvas.drawBitmap(far, -margin + shift(0.05f), height * layers[0].top, mirror)
            canvas.restore()
        } else if (stage == layers.size) {
            val t = timeMs / 1000f
            canvas.save()
            canvas.translate(shift(1f) + sin(t * (1f + windKmh / 20f)) * (1f + windKmh / 15f), 0f)
            canvas.drawPath(reeds, reedPaint)
            canvas.restore()
        }
    }
}

/** A river winding out of the mountains through a green valley, between wooded slopes. */
class RiverValleyLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.42f, 1f, 0.1f), PaintLayer(0.62f, 1f, 1f))

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
        return listOf(GroundSlot(Species.DUCK, 0.33f, 0.59f, GroundY { _ -> h * 0.95f }, 1.5f, 2))
    }

    private val water = Water()
    private val river = Path()

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        river.rewind()
        val top = h * 0.69f
        val n = 40
        for (i in 0..n) {
            val f = i / n.toFloat()
            val y = top + (h - top) * f
            val half = w * (0.006f + 0.2f * f * f)
            val cx = w * 0.5f + sin(f * 7f) * w * 0.12f * f
            if (i == 0) river.moveTo(cx - half, y) else river.lineTo(cx - half, y)
        }
        for (i in n downTo 0) {
            val f = i / n.toFloat()
            val y = top + (h - top) * f
            val half = w * (0.006f + 0.2f * f * f)
            val cx = w * 0.5f + sin(f * 7f) * w * 0.12f * f
            river.lineTo(cx + half, y)
        }
        river.close()
        water.build(0f, w, top, h, unit)
    }

    override fun onLightingChanged() = water.applyLighting(lighting, DEEP_LAKE, width)

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(valley(k), banks(k)))

    private fun valley(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(91)
        k.mountain(
            c, Ridge(k.width, h * 0.66f, h * 0.17f, true, 40), h * 0.74f,
            l.distant(l.ground(0xFF76889F.toInt()), 0.38f), l.distant(l.ground(0xFF4E6078.toInt()), 0.38f), rnd, snowLine = h * 0.52f,
        )
        val floor = Ridge(k.width, h * 0.7f, h * 0.012f, false, 41)
        k.hill(c, floor, h, l.distant(l.ground(0xFF76A85E.toInt()), 0.2f), l.ground(0xFF3F7A35.toInt()), rnd, 500)
        k.fields(c, floor, h, rnd, 5)
        val lit = l.distant(l.foliage(0xFF5C9A58.toInt()), 0.3f); val dark = l.distant(l.foliage(0xFF28553A.toInt()), 0.3f)
        var x = -k.margin
        while (x < w + k.margin) {
            if (abs(x - w * 0.5f) > w * 0.12f) {
                val th = h * (0.02f + rnd.nextFloat() * 0.02f)
                k.simplePine(c, x, floor.yAt(x) + th * 0.2f, th, k.jitter(lit, rnd, 0.2f), dark, rnd)
            }
            x += 7f + rnd.nextFloat() * 9f
        }
        k.haze(c, h * 0.6f, h * 0.78f, 0.35f)
        return bmp
    }

    private fun banks(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(92)
        val left = Path()
        left.moveTo(-w * 0.1f, h * 0.72f); left.quadTo(w * 0.15f, h * 0.76f, w * 0.24f, h); left.lineTo(-w * 0.1f, h); left.close()
        val right = Path()
        right.moveTo(w * 1.1f, h * 0.7f); right.quadTo(w * 0.85f, h * 0.78f, w * 0.8f, h); right.lineTo(w * 1.1f, h); right.close()
        val lit = l.foliage(0xFF3F7A4B.toInt()); val dark = l.foliage(0xFF17301F.toInt())
        for (slope in arrayOf(left, right)) {
            c.save()
            c.clipPath(slope)
            k.shader = LinearGradient(0f, h * 0.7f, 0f, h, l.ground(0xFF4E8A3E.toInt()), l.ground(0xFF21401C.toInt()), Shader.TileMode.CLAMP)
            c.drawRect(-k.margin, h * 0.68f, w + k.margin, h, k.fill)
            k.shader = null
            for (i in 0 until 600) {
                val x = -k.margin + rnd.nextFloat() * (w + k.margin * 2)
                val y = h * (0.7f + 0.3f * rnd.nextFloat())
                val bh = h * (0.005f + 0.014f * rnd.nextFloat())
                k.line.strokeWidth = (1f + rnd.nextFloat() * 1.5f) * k.unit
                k.line.color = k.mix(l.ground(0xFF1E3518.toInt()), l.ground(0xFF8FC060.toInt()), rnd.nextFloat())
                c.drawLine(x, y, x + (rnd.nextFloat() - 0.5f) * bh, y - bh, k.line)
            }
            c.restore()
        }
        for (i in 0 until 7) {
            val x = w * 0.01f + i * w * 0.035f
            val th = h * (0.1f + rnd.nextFloat() * 0.08f)
            k.conifer(c, x, h * (0.76f + i * 0.026f), th, k.jitter(lit, rnd, 0.14f), dark, l.shade(0xFF3A2C22.toInt()), rnd, 0.8f)
        }
        for (i in 0 until 6) {
            val x = w * 0.99f - i * w * 0.035f
            val th = h * (0.1f + rnd.nextFloat() * 0.08f)
            k.conifer(c, x, h * (0.75f + i * 0.026f), th, k.jitter(lit, rnd, 0.14f), dark, l.shade(0xFF3A2C22.toInt()), rnd, 0.8f)
        }
        for (i in 0 until 5) k.rock(c, w * (0.2f + 0.6f * rnd.nextFloat()), h * (0.97f + 0.02f * rnd.nextFloat()), w * (0.015f + 0.02f * rnd.nextFloat()), l.ground(0xFF70767E.toInt()), rnd)
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != 1) return
        canvas.save()
        canvas.translate(shift(0.6f), 0f)
        canvas.clipPath(river)
        water.draw(canvas, timeMs, windKmh + 10f, 0f)
        canvas.restore()
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
            slots = listOf(GroundSlot(Species.DUCK, 0.2f, 0.8f, GroundY { _ -> h * 0.93f }, 1.3f, 2))
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

/** A palm tree sprite: curved trunk, a crown of drooping fronds and coconuts. Shared by the tropical landscapes. */
internal fun paintPalm(k: PaintKit, seed: Int): Bitmap? {
    val th = k.h * 0.26f
    val bw = (th * 1.6f).toInt()
    val bh = (th * 1.25f).toInt()
    val (c, bmp) = k.newSprite(bw, bh) ?: return null
    val l = k.l
    val rnd = Random(seed)
    val bx = bw * 0.55f; val by = bh - 2f
    val cx = bx - th * 0.2f; val cy = by - th * 1.0f
    val trunk = l.shade(0xFF7A5A3A.toInt())
    var px = bx; var py = by
    for (i in 1..28) {
        val t = i / 28f
        val x = (1 - t) * (1 - t) * bx + 2 * (1 - t) * t * (bx + th * 0.1f) + t * t * cx
        val y = (1 - t) * (1 - t) * by + 2 * (1 - t) * t * (by - th * 0.55f) + t * t * cy
        k.line.strokeWidth = th * 0.04f * (1f - 0.45f * t)
        k.line.color = if (i % 3 == 0) k.darken(trunk, 0.3f) else k.jitter(trunk, rnd, 0.1f)
        c.drawLine(px, py, x, y, k.line)
        px = x; py = y
    }
    val lit = l.foliage(0xFF4E9A3E.toInt()); val dark = l.foliage(0xFF1E4A24.toInt())
    for (f in 0 until 11) {
        val a = -PI.toFloat() + 0.2f + f * (PI.toFloat() - 0.4f) / 10f
        val len = th * (0.5f + rnd.nextFloat() * 0.12f)
        val ex = cx + cos(a) * len
        val ey = cy + sin(a) * len * 0.45f + len * 0.3f
        val qx = cx + cos(a) * len * 0.55f
        val qy = cy + sin(a) * len * 0.55f - len * 0.3f
        k.line.strokeWidth = max(1.5f, th * 0.008f)
        k.line.color = k.darken(lit, 0.25f)
        var sx = cx; var sy = cy
        for (j in 1..24) {
            val t = j / 24f
            val x = (1 - t) * (1 - t) * cx + 2 * (1 - t) * t * qx + t * t * ex
            val y = (1 - t) * (1 - t) * cy + 2 * (1 - t) * t * qy + t * t * ey
            val tx = x - sx; val ty = y - sy
            val tl = kotlin.math.sqrt(tx * tx + ty * ty).coerceAtLeast(0.001f)
            val nx = -ty / tl; val nyv = tx / tl
            val leaf = len * 0.2f * (1f - 0.55f * t) * (0.8f + rnd.nextFloat() * 0.4f)
            val sunSide = if (a < -PI.toFloat() / 2f) -1f else 1f
            val tone = (0.5f + 0.4f * (if (sunSide == k.sun) 1f else -1f) + (rnd.nextFloat() - 0.5f) * 0.3f).coerceIn(0f, 1f)
            k.line.color = k.jitter(k.mix(dark, lit, tone), rnd, 0.12f)
            k.line.strokeWidth = max(1f, th * 0.006f)
            c.drawLine(x, y, x + nx * leaf + tx / tl * leaf * 0.5f, y + nyv * leaf + ty / tl * leaf * 0.5f + leaf * 0.3f, k.line)
            c.drawLine(x, y, x - nx * leaf + tx / tl * leaf * 0.5f, y - nyv * leaf + ty / tl * leaf * 0.5f + leaf * 0.3f, k.line)
            sx = x; sy = y
        }
        k.line.strokeWidth = max(1.5f, th * 0.008f)
        k.line.color = k.darken(lit, 0.3f)
        c.drawLine(cx, cy, ex, ey, k.line)
    }
    k.fill.color = k.darken(trunk, 0.45f)
    for (i in 0 until 3) c.drawCircle(cx + (i - 1) * th * 0.03f, cy + th * 0.03f, th * 0.025f, k.fill)
    return bmp
}
