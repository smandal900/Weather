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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val SEA = 0xFF123E5C.toInt()

/** Tall sandstone cliffs with a grassy top, sea stacks standing in the water and surf at their feet. */
class CoastalCliffsLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.50f, 0.64f, 0.08f), PaintLayer(0.26f, 0.84f, 0.55f), PaintLayer(0.86f, 1f, 1f))
    private val water = Water(maxGlints = 64)
    private val foam = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat()
        water.build(-w * 0.1f, w * 1.1f, h * 0.62f, h, w / 1080f)
    }

    override fun onLightingChanged() {
        water.applyLighting(lighting, SEA, width)
        foam.color = lighting.shade(0xFFFFFFFF.toInt())
    }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(coast(k), cliffs(k), rocks(k)))

    private fun coast(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        k.shader = LinearGradient(0f, h * 0.55f, 0f, h * 0.62f, l.distant(l.foliage(0xFF557A5A.toInt()), 0.55f), l.distant(l.ground(0xFF7C8070.toInt()), 0.6f), Shader.TileMode.CLAMP)
        c.drawPath(moundPath(w * 0.55f, w * 1.15f, h * 0.62f, h * 0.05f), k.fill)
        k.shader = null
        k.haze(c, h * 0.56f, h * 0.63f, 0.4f)
        return bmp
    }

    private fun cliffs(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(111)
        val top = h * 0.36f; val waterY = h * 0.74f
        val edgeX = w * 0.40f
        // Top profile of the cliff (x fractions of the screen to y).
        val px = floatArrayOf(-0.12f * w, 0.10f * w, 0.26f * w, 0.40f * w)
        val py = floatArrayOf(top - h * 0.03f, top - h * 0.04f, top, top + h * 0.015f)
        val body = Path()
        body.moveTo(-w * 0.12f, waterY)
        body.lineTo(px[0], py[0])
        var x = px[0]
        while (x < edgeX) { x += w * 0.02f; body.lineTo(x.coerceAtMost(edgeX), interp(px, py, x)) }
        // The sheer face, a little ragged.
        val faceTop = top + h * 0.015f
        for (i in 1..14) {
            val yy = faceTop + (waterY - faceTop) * i / 14f
            body.lineTo(edgeX + (rnd.nextFloat() - 0.4f) * w * 0.025f, yy)
        }
        body.lineTo(edgeX - w * 0.03f, waterY)
        body.close()
        val rockLit = l.ground(0xFFB3A184.toInt()); val rockDark = l.ground(0xFF5E5240.toInt())
        k.rockFace(c, body, -w * 0.12f, edgeX, top, waterY, rockLit, rockDark, rnd, litOnRight = true)
        // Grassy cap with bushes.
        val grass = l.foliage(0xFF5A8A48.toInt())
        var gx = -w * 0.1f
        while (gx < edgeX - w * 0.01f) {
            val gy = interp(px, py, gx)
            k.line.strokeWidth = 1.6f * k.unit
            k.line.color = k.jitter(if (rnd.nextBoolean()) grass else k.lighten(grass, 0.2f), rnd, 0.2f)
            c.drawLine(gx, gy + h * 0.004f, gx + (rnd.nextFloat() - 0.5f) * 4f * k.unit, gy - h * (0.006f + 0.008f * rnd.nextFloat()), k.line)
            gx += 3f * k.unit
        }
        val lit = l.foliage(0xFF5F9A5E.toInt()); val dark = l.foliage(0xFF2F5A3C.toInt())
        for (i in 0 until 5) {
            val bx = w * (0.02f + 0.07f * i)
            k.broadleaf(c, bx, interp(px, py, bx) + h * 0.006f, h * (0.035f + 0.02f * rnd.nextFloat()), k.jitter(lit, rnd, 0.2f), dark, dark, rnd, 0.3f)
        }
        // Sea stacks.
        for ((sx, sw, sh) in listOf(Triple(0.60f, 0.07f, 0.20f), Triple(0.76f, 0.045f, 0.12f))) {
            val cx = w * sx; val half = w * sw / 2f; val topY = waterY - h * sh
            val st = Path()
            st.moveTo(cx - half * 1.2f, waterY)
            st.lineTo(cx - half, topY + h * 0.03f)
            st.lineTo(cx - half * 0.6f, topY)
            st.lineTo(cx + half * 0.3f, topY - h * 0.006f)
            st.lineTo(cx + half, topY + h * 0.02f)
            st.lineTo(cx + half * 1.2f, waterY)
            st.close()
            k.rockFace(c, st, cx - half * 1.2f, cx + half * 1.2f, topY, waterY, rockLit, rockDark, rnd, litOnRight = true)
            k.fill.color = grass
            c.drawOval(cx - half * 0.9f, topY - h * 0.004f, cx + half * 0.5f, topY + h * 0.012f, k.fill)
        }
        return bmp
    }

    private fun rocks(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(113)
        k.rock(c, w * 0.9f, h * 1.01f, w * 0.18f, l.ground(0xFF6A645C.toInt()), rnd)
        k.rock(c, w * 0.05f, h * 1.02f, w * 0.14f, l.ground(0xFF5C564F.toInt()), rnd)
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        if (stage == 1) { water.draw(canvas, timeMs, windKmh, shift(0.4f)); return }
        if (stage != layers.size) return
        val t = timeMs / 1000f; val r = roughness(windKmh)
        canvas.save(); canvas.translate(shift(0.55f), 0f)
        foamDots(canvas, foam, w * 0.22f, w * 0.42f, h * 0.745f, 14, t, w * 0.009f, r)
        foamDots(canvas, foam, w * 0.55f, w * 0.65f, h * 0.745f, 8, t + 1f, w * 0.008f, r)
        foamDots(canvas, foam, w * 0.72f, w * 0.80f, h * 0.745f, 6, t + 2f, w * 0.007f, r)
        canvas.restore()
    }
}

/** A deep fjord: snowy peaks far off, steep pine-clad walls closing in and a waterfall down one of them. */
class FjordLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.22f, 0.68f, 0.08f), PaintLayer(0.10f, 0.86f, 0.5f), PaintLayer(0.86f, 1f, 1f))
    private val water = Water(maxGlints = 50)

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat()
        water.build(-w * 0.1f, w * 1.1f, h * 0.66f, h, w / 1080f)
    }

    override fun onLightingChanged() = water.applyLighting(lighting, 0xFF0F3A52.toInt(), width)

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(peaks(k), walls(k), rocks(k)))

    private fun peaks(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(121)
        k.mountain(c, Ridge(k.width, h * 0.5f, h * 0.2f, true, 12), h * 0.68f,
            l.distant(l.ground(0xFF9AAABF.toInt()), 0.4f), l.distant(l.ground(0xFF62748C.toInt()), 0.4f), rnd, snowLine = h * 0.43f)
        k.haze(c, h * 0.54f, h * 0.68f, 0.5f)
        return bmp
    }

    private fun wall(k: PaintKit, c: Canvas, leftSide: Boolean, rnd: Random) {
        val l = k.l; val h = k.h; val w = k.w
        val waterY = h * 0.70f
        val sx = if (leftSide) 1f else -1f
        val x0 = if (leftSide) -w * 0.12f else w * 1.12f
        val xs = floatArrayOf(0f, 0.10f, 0.20f, 0.30f, 0.38f)
        val ys = floatArrayOf(h * 0.12f, h * 0.2f, h * 0.36f, h * 0.55f, waterY)
        val body = Path()
        body.moveTo(x0, waterY)
        body.lineTo(x0, ys[0])
        var u = 0f
        while (u <= 0.38f) { body.lineTo(x0 + sx * u * w, interp(xs, ys, u) + (rnd.nextFloat() - 0.5f) * h * 0.012f); u += 0.012f }
        body.lineTo(x0 + sx * 0.4f * w, waterY)
        body.close()
        val lit = l.ground(0xFF6C7A6E.toInt()); val dark = l.ground(0xFF2E3A32.toInt())
        k.rockFace(c, body, x0 - w * 0.0f, x0 + sx * 0.4f * w, h * 0.12f, waterY, lit, dark, rnd, litOnRight = !leftSide)
        // Pines on the slope.
        val litP = l.foliage(0xFF3C6A4C.toInt()); val darkP = l.foliage(0xFF1C3A2A.toInt())
        for (i in 0 until 90) {
            val uu = rnd.nextFloat() * 0.37f
            val edge = interp(xs, ys, uu)
            val y = edge + (waterY - edge) * rnd.nextFloat().let { it * it } * 0.85f
            val th = h * (0.02f + 0.03f * rnd.nextFloat())
            k.simplePine(c, x0 + sx * uu * w, y + th * 0.3f, th, k.jitter(litP, rnd, 0.2f), darkP, rnd)
        }
        // Waterfall on one wall.
        if (leftSide) {
            val fx = x0 + sx * 0.14f * w
            k.line.strokeWidth = 5f * k.unit
            k.line.color = k.alpha(l.shade(0xFFFFFFFF.toInt()), 190)
            c.drawLine(fx, interp(xs, ys, 0.14f) + h * 0.03f, fx + 2f * k.unit, h * 0.64f, k.line)
            k.line.strokeWidth = 11f * k.unit
            k.line.color = k.alpha(l.shade(0xFFFFFFFF.toInt()), 50)
            c.drawLine(fx, interp(xs, ys, 0.14f) + h * 0.03f, fx + 2f * k.unit, h * 0.64f, k.line)
        }
    }

    private fun walls(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val rnd = Random(122)
        wall(k, c, true, rnd)
        wall(k, c, false, rnd)
        k.haze(c, k.h * 0.6f, k.h * 0.72f, 0.25f)
        return bmp
    }

    private fun rocks(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val rnd = Random(123)
        k.rock(c, k.w * 0.1f, k.h * 1.02f, k.w * 0.16f, k.l.ground(0xFF4E5650.toInt()), rnd)
        k.rock(c, k.w * 0.92f, k.h * 1.02f, k.w * 0.15f, k.l.ground(0xFF4A524C.toInt()), rnd)
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage == 1) water.draw(canvas, timeMs, windKmh, shift(0.4f))
    }
}

/** A fishing harbour: stone breakwater with a lamp, waterfront cottages and three bobbing fishing boats. */
class HarborLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.48f, 0.62f, 0.08f), PaintLayer(0.42f, 0.78f, 0.6f), PaintLayer(0.86f, 1f, 1f))
    private val water = Water(maxGlints = 60)
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val foam = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat()
        water.build(-w * 0.1f, w * 1.1f, h * 0.64f, h, w / 1080f)
    }

    override fun onLightingChanged() {
        water.applyLighting(lighting, SEA, width)
        foam.color = lighting.shade(0xFFFFFFFF.toInt())
    }

    override fun paintAll(k: PaintKit): PaintResult {
        val sprites = HashMap<String, Bitmap>()
        val hulls = intArrayOf(0xFF2F5C8A.toInt(), 0xFFB5412F.toInt(), 0xFF2E7A5A.toInt())
        for (i in 0 until 3) fishingBoat(k, hulls[i], 130 + i)?.let { sprites["boat$i"] = it }
        return PaintResult(arrayOf(shore(k), quay(k), rocks(k)), sprites)
    }

    private fun shore(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        k.shader = LinearGradient(0f, h * 0.53f, 0f, h * 0.62f, l.distant(l.foliage(0xFF557A5A.toInt()), 0.55f), l.distant(l.ground(0xFF7C8070.toInt()), 0.6f), Shader.TileMode.CLAMP)
        c.drawPath(moundPath(w * 0.6f, w * 1.2f, h * 0.62f, h * 0.06f), k.fill)
        k.shader = null
        k.haze(c, h * 0.55f, h * 0.63f, 0.4f)
        return bmp
    }

    private fun quay(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(131)
        val quayY = h * 0.65f
        // Land on the left with a stone wall.
        val land = Path()
        land.moveTo(-w * 0.12f, h * 0.74f); land.lineTo(-w * 0.12f, quayY)
        land.lineTo(w * 0.38f, quayY + h * 0.005f); land.lineTo(w * 0.40f, h * 0.74f); land.close()
        k.shader = LinearGradient(0f, quayY, 0f, h * 0.74f, l.ground(0xFF8F877A.toInt()), l.ground(0xFF4E4A42.toInt()), Shader.TileMode.CLAMP)
        c.drawPath(land, k.fill)
        k.shader = null
        k.line.strokeWidth = 1.5f * k.unit; k.line.color = k.alpha(Color.BLACK, 50)
        var sy = quayY + h * 0.012f
        while (sy < h * 0.74f) { c.drawLine(-w * 0.1f, sy, w * 0.39f, sy, k.line); sy += h * 0.012f }
        // Cottages along the quay.
        val walls = intArrayOf(0xFFF0E4CF.toInt(), 0xFFE9C9A0.toInt(), 0xFFCFE0E8.toInt(), 0xFFF0D4C8.toInt(), 0xFFE0E8C8.toInt())
        val roofs = intArrayOf(0xFFA4483A.toInt(), 0xFF2F5C8A.toInt(), 0xFF6A5A4A.toInt(), 0xFFA4483A.toInt(), 0xFF3C6A52.toInt())
        for (i in 0 until 5) {
            val hw = w * (0.04f + 0.012f * rnd.nextFloat())
            k.cottage(c, w * (0.02f + 0.075f * i), quayY + h * 0.002f, hw, h * (0.045f + 0.02f * rnd.nextFloat()), walls[i], roofs[i])
        }
        // Breakwater with a lamp.
        val pierY = h * 0.685f
        val pier = Path()
        pier.moveTo(w * 0.38f, pierY - h * 0.012f); pier.lineTo(w * 1.12f, pierY - h * 0.012f)
        pier.lineTo(w * 1.12f, pierY + h * 0.02f); pier.lineTo(w * 0.38f, pierY + h * 0.022f); pier.close()
        k.shader = LinearGradient(0f, pierY - h * 0.012f, 0f, pierY + h * 0.022f, l.ground(0xFFA39A8A.toInt()), l.ground(0xFF5A554C.toInt()), Shader.TileMode.CLAMP)
        c.drawPath(pier, k.fill)
        k.shader = null
        var px = w * 0.42f
        while (px < w * 1.1f) { c.drawLine(px, pierY + h * 0.0f, px + w * 0.001f, pierY + h * 0.02f, k.line); px += w * 0.04f }
        // Bollards and a lamp post at the end.
        k.fill.color = l.shade(0xFF2E3238.toInt())
        for (bx in floatArrayOf(0.5f, 0.62f, 0.74f, 0.86f)) c.drawRect(w * bx, pierY - h * 0.02f, w * bx + w * 0.012f, pierY - h * 0.011f, k.fill)
        val lx = w * 0.93f
        k.line.strokeWidth = 3f * k.unit; k.line.color = l.shade(0xFF2E3238.toInt())
        c.drawLine(lx, pierY - h * 0.012f, lx, pierY - h * 0.075f, k.line)
        k.fill.color = if (l.windowsLit) 0xFFFFE9A8.toInt() else l.shade(0xFFBFD3DE.toInt())
        c.drawRect(lx - w * 0.008f, pierY - h * 0.088f, lx + w * 0.008f, pierY - h * 0.072f, k.fill)
        if (l.windowsLit) {
            k.shader = RadialGradient(lx, pierY - h * 0.08f, h * 0.05f, 0x88FFE0A0.toInt(), 0, Shader.TileMode.CLAMP)
            c.drawCircle(lx, pierY - h * 0.08f, h * 0.05f, k.fill)
            k.shader = null
        }
        return bmp
    }

    private fun rocks(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val rnd = Random(133)
        k.rock(c, k.w * 0.06f, k.h * 1.02f, k.w * 0.15f, k.l.ground(0xFF5C564F.toInt()), rnd)
        k.rock(c, k.w * 0.94f, k.h * 1.03f, k.w * 0.17f, k.l.ground(0xFF645E56.toInt()), rnd)
        return bmp
    }

    /** A small fishing boat: coloured hull, white wheelhouse, mast with a lamp, drying net. */
    private fun fishingBoat(k: PaintKit, hullCol: Int, seed: Int): Bitmap? {
        val s = k.w * 0.0009f
        val bw = (300 * s * 1.0f * 1f).toInt().coerceAtLeast(40); val bh = (200 * s).toInt().coerceAtLeast(30)
        val (c, bmp) = k.newSprite(bw, bh) ?: return null
        val l = k.l
        val ox = bw / 2f; val oy = bh * 0.74f
        fun px(x: Float) = ox + x * s * 1f
        fun py(y: Float) = oy + y * s
        val hull = Path()
        hull.moveTo(px(-130f), py(-22f)); hull.lineTo(px(125f), py(-30f)); hull.lineTo(px(95f), py(18f)); hull.lineTo(px(-100f), py(18f)); hull.close()
        val hc = l.shade(hullCol)
        k.shader = LinearGradient(0f, py(-30f), 0f, py(18f), k.lighten(hc, 0.15f), k.darken(hc, 0.35f), Shader.TileMode.CLAMP)
        c.drawPath(hull, k.fill)
        k.shader = null
        k.fill.color = l.shade(0xFFF4F1EA.toInt())
        c.drawRect(px(-128f), py(-26f), px(122f), py(-20f), k.fill)
        // Wheelhouse.
        k.fill.color = l.shade(0xFFEDE8DC.toInt())
        c.drawRect(px(-20f), py(-70f), px(60f), py(-26f), k.fill)
        k.fill.color = l.shade(0xFFB5412F.toInt())
        c.drawRect(px(-24f), py(-76f), px(64f), py(-68f), k.fill)
        k.fill.color = if (l.windowsLit) 0xFFFFD27A.toInt() else l.shade(0xFF3F5A72.toInt())
        c.drawRect(px(-10f), py(-62f), px(14f), py(-44f), k.fill)
        c.drawRect(px(26f), py(-62f), px(50f), py(-44f), k.fill)
        // Mast, boom and net.
        k.line.strokeWidth = max2(2f, 3f * s); k.line.color = l.shade(0xFF3A2C22.toInt())
        c.drawLine(px(-70f), py(-26f), px(-70f), py(-135f), k.line)
        c.drawLine(px(-70f), py(-110f), px(-125f), py(-30f), k.line)
        k.line.strokeWidth = max2(1f, 1.5f * s); k.line.color = k.alpha(l.shade(0xFF6A5A4A.toInt()), 200)
        for (i in 1..5) c.drawLine(px(-70f - i * 10f), py(-110f + i * 14f), px(-70f - i * 10f + 6f), py(-30f), k.line)
        k.fill.color = if (l.windowsLit) 0xFFFFE9A8.toInt() else l.shade(0xFFFFF2C4.toInt())
        c.drawCircle(px(-70f), py(-137f), 5f * s, k.fill)
        return bmp
    }

    private fun max2(a: Float, b: Float) = if (a > b) a else b

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        if (stage == 1) { water.draw(canvas, timeMs, windKmh, shift(0.4f)); return }
        if (stage == 2) {
            val t = timeMs / 1000f; val r = roughness(windKmh)
            canvas.save(); canvas.translate(shift(0.6f), 0f)
            val xs = floatArrayOf(0.52f, 0.72f, 0.9f); val ys = floatArrayOf(0.728f, 0.745f, 0.718f)
            for (i in 0 until 3) {
                val b = sprites["boat$i"] ?: continue
                canvas.save()
                canvas.translate(w * xs[i], h * ys[i] + sin(t * 1.1f + i * 2f) * h * 0.004f * r)
                canvas.rotate(sin(t * 0.9f + i * 1.7f) * 1.6f * r, 0f, 0f)
                canvas.drawBitmap(b, -b.width / 2f, -b.height * 0.78f, spritePaint)
                canvas.restore()
            }
            foamDots(canvas, foam, w * 0.4f, w * 1.0f, h * 0.70f, 20, t, w * 0.005f, r)
            canvas.restore()
        }
    }
}

/** A frozen lake: cracked ice and snow drifts, snowy pines on the shore, white peaks and an ice-fishing hut. */
class FrozenLakeLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.26f, 0.66f, 0.08f), PaintLayer(0.50f, 0.72f, 0.4f), PaintLayer(0.62f, 1f, 0.9f))
    private val sparkle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spx = FloatArray(28); private val spy = FloatArray(28); private val spp = FloatArray(28)

    override fun build() {
        val rnd = Random(141)
        for (i in 0 until 28) { spx[i] = width * (0.04f + 0.92f * rnd.nextFloat()); spy[i] = height * (0.68f + 0.28f * rnd.nextFloat()); spp[i] = rnd.nextFloat() * 6.28f }
    }

    override fun onLightingChanged() { sparkle.color = lighting.shade(0xFFFFFFFF.toInt()) }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(peaks(k), shore(k), ice(k)))

    private fun snow(k: PaintKit, base: Int = Lighting.SNOW) = k.l.shade(base)

    private fun peaks(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(142)
        k.mountain(c, Ridge(k.width, h * 0.52f, h * 0.17f, true, 14), h * 0.66f,
            l.distant(snow(k, 0xFFE8EEF6.toInt()), 0.3f), l.distant(l.shade(0xFF7C8EA8.toInt()), 0.35f), rnd, snowLine = h * 0.5f)
        k.haze(c, h * 0.52f, h * 0.67f, 0.45f)
        return bmp
    }

    private fun shore(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(143)
        val ridge = Ridge(k.width, h * 0.66f, h * 0.012f, false, 15)
        k.hill(c, ridge, h * 0.72f, snow(k), l.shade(0xFFC4D2E2.toInt()), rnd, 0)
        val litP = k.mix(l.foliage(0xFF3C6A4C.toInt()), Lighting.SNOW, 0.55f)
        val darkP = k.mix(l.foliage(0xFF1C3A2A.toInt()), Lighting.SNOW, 0.25f)
        val trunk = l.shade(0xFF3A2C22.toInt())
        var x = -k.margin
        while (x < w + k.margin) {
            val th = h * (0.05f + rnd.nextFloat() * 0.05f)
            k.conifer(c, x, ridge.yAt(x) + th * 0.12f, th, k.jitter(litP, rnd, 0.15f), darkP, trunk, rnd, 0.4f)
            x += (18f + rnd.nextFloat() * 24f) * k.unit
        }
        k.haze(c, h * 0.62f, h * 0.7f, 0.25f)
        return bmp
    }

    private fun ice(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(144)
        val top = h * 0.68f
        val body = Path()
        body.moveTo(-k.margin, h)
        body.lineTo(-k.margin, top + h * 0.01f)
        for (i in 0..20) { val x = -k.margin + (w + 2 * k.margin) * i / 20f; body.lineTo(x, top + h * 0.006f * sin(i * 1.7f)) }
        body.lineTo(w + k.margin, h)
        body.close()
        k.shader = LinearGradient(0f, top, 0f, h, l.shade(0xFFD5E8F2.toInt()), l.shade(0xFF8FB4CC.toInt()), Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null
        c.save(); c.clipPath(body)
        // Sheen and cracks.
        k.shader = LinearGradient(0f, top, w, h, 0x00FFFFFF, 0x66FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(-k.margin, top, w + k.margin, h, k.fill)
        k.shader = null
        k.line.strokeWidth = 1.4f * k.unit
        repeat(26) {
            var x = w * rnd.nextFloat(); var y = top + (h - top) * rnd.nextFloat()
            k.line.color = k.alpha(if (rnd.nextFloat() < 0.6f) 0xFF3A5A78.toInt() else Color.WHITE, 90)
            repeat(4) {
                val nx = x + (rnd.nextFloat() - 0.5f) * w * 0.12f; val ny = y + (rnd.nextFloat() - 0.2f) * h * 0.03f
                c.drawLine(x, y, nx, ny, k.line); x = nx; y = ny
            }
        }
        // Snow drifts along the edges.
        k.fill.color = snow(k)
        c.drawOval(-w * 0.15f, h * 0.9f, w * 0.35f, h * 1.1f, k.fill)
        c.drawOval(w * 0.7f, h * 0.92f, w * 1.2f, h * 1.12f, k.fill)
        c.restore()
        // Ice-fishing hut.
        val hx = w * 0.72f; val hy = h * 0.76f; val hw = w * 0.07f
        k.fill.color = k.alpha(Color.BLACK, 50)
        c.drawOval(hx - hw * 1.2f, hy - h * 0.004f, hx + hw * 1.4f, hy + h * 0.012f, k.fill)
        k.fill.color = l.shade(0xFF8A4B2A.toInt())
        c.drawRect(hx - hw, hy - hw * 0.9f, hx + hw, hy, k.fill)
        val roof = Path(); roof.moveTo(hx - hw * 1.12f, hy - hw * 0.9f); roof.lineTo(hx, hy - hw * 1.55f); roof.lineTo(hx + hw * 1.12f, hy - hw * 0.9f); roof.close()
        k.fill.color = snow(k)
        c.drawPath(roof, k.fill)
        k.fill.color = if (l.windowsLit) 0xFFFFD27A.toInt() else l.shade(0xFF3F4756.toInt())
        c.drawRect(hx - hw * 0.28f, hy - hw * 0.7f, hx + hw * 0.28f, hy - hw * 0.25f, k.fill)
        k.fill.color = l.shade(0xFF2E2418.toInt())
        c.drawOval(hx + hw * 1.6f, hy + h * 0.008f, hx + hw * 2.0f, hy + h * 0.02f, k.fill)
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != layers.size) return
        val t = timeMs / 1000f
        canvas.save(); canvas.translate(shift(0.9f), 0f)
        for (i in 0 until 28) {
            val tw = sin(t * 1.5f + spp[i])
            if (tw < 0.3f) continue
            sparkle.alpha = (230 * (tw - 0.3f) / 0.7f).toInt()
            val r = width * 0.004f * tw
            canvas.drawLine(spx[i] - r * 2f, spy[i], spx[i] + r * 2f, spy[i], sparkle.apply { strokeWidth = width * 0.0015f })
            canvas.drawLine(spx[i], spy[i] - r * 2f, spx[i], spy[i] + r * 2f, sparkle)
        }
        canvas.restore()
    }
}

/** Kerala-style backwaters: still green water between mangrove and palm banks, with a houseboat drifting at its moorings. */
class MangroveBackwatersLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.46f, 0.64f, 0.08f), PaintLayer(0.40f, 0.86f, 0.55f), PaintLayer(0.86f, 1f, 1f))
    private val water = Water(maxGlints = 40)
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat()
        water.build(-w * 0.1f, w * 1.1f, h * 0.62f, h, w / 1080f)
    }

    override fun onLightingChanged() = water.applyLighting(lighting, 0xFF2A5E4C.toInt(), width)

    override fun paintAll(k: PaintKit): PaintResult {
        val sp = HashMap<String, Bitmap>()
        houseboat(k)?.let { sp["boat"] = it }
        return PaintResult(arrayOf(treeline(k), banks(k, sp), rocks(k)), sp)
    }

    private fun treeline(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(151)
        k.fill.color = l.distant(l.foliage(0xFF3E6A4A.toInt()), 0.55f)
        c.drawRect(-k.margin, h * 0.585f, w + k.margin, h * 0.625f, k.fill)
        val lit = l.distant(l.foliage(0xFF4E8458.toInt()), 0.55f); val dark = l.distant(l.foliage(0xFF2A4C38.toInt()), 0.55f)
        var x = -k.margin
        while (x < w + k.margin) {
            k.broadleaf(c, x, h * 0.62f, h * (0.035f + 0.025f * rnd.nextFloat()), k.jitter(lit, rnd, 0.2f), dark, dark, rnd, 0.1f)
            x += w * 0.04f
        }
        k.haze(c, h * 0.53f, h * 0.63f, 0.5f)
        return bmp
    }

    private fun banks(k: PaintKit, sp: Map<String, Bitmap>): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(152)
        val soil = l.ground(0xFF4A3E2E.toInt())
        val lit = l.foliage(0xFF4E9A48.toInt()); val dark = l.foliage(0xFF1E4A28.toInt())
        for (leftSide in booleanArrayOf(true, false)) {
            val sx = if (leftSide) 1f else -1f
            val x0 = if (leftSide) -w * 0.12f else w * 1.12f
            val waterY = h * 0.70f
            val bank = Path()
            bank.moveTo(x0, waterY + h * 0.02f)
            bank.lineTo(x0, h * 0.62f)
            for (i in 0..12) { val u = i / 12f; bank.lineTo(x0 + sx * w * 0.38f * u, h * 0.62f + (waterY - h * 0.62f) * u * u + (rnd.nextFloat() - 0.5f) * h * 0.004f) }
            bank.lineTo(x0 + sx * w * 0.38f, waterY + h * 0.02f)
            bank.close()
            k.shader = LinearGradient(0f, h * 0.62f, 0f, waterY + h * 0.02f, l.foliage(0xFF3E7A3C.toInt()), soil, Shader.TileMode.CLAMP)
            c.drawPath(bank, k.fill)
            k.shader = null
            // Mangroves: crowns on stilt roots, nearer ones bigger.
            for (i in 0 until 9) {
                val u = 0.02f + 0.34f * (i / 9f) + 0.02f * rnd.nextFloat()
                val bx = x0 + sx * w * u
                val by = h * 0.62f + (waterY - h * 0.62f) * (u / 0.38f) * (u / 0.38f) + h * 0.012f
                val th = h * (0.07f + 0.05f * rnd.nextFloat())
                k.line.strokeWidth = 2.2f * k.unit; k.line.color = l.shade(0xFF4A3A2A.toInt())
                for (r in -2..2) c.drawLine(bx + r * th * 0.05f, by - th * 0.28f, bx + r * th * 0.12f, by + th * 0.03f, k.line)
                k.broadleaf(c, bx, by - th * 0.2f, th, k.jitter(lit, rnd, 0.2f), dark, dark, rnd, 0.35f)
            }
        }
        // Palms leaning over the water.
        val palm = paintPalm(k, 153)
        if (palm != null) {
            val sc = 0.5f
            val dstW = palm.width * sc; val dstH = palm.height * sc
            for ((fx, fy) in listOf(0.16f to 0.70f, 0.30f to 0.70f)) c.drawBitmap(palm, null, android.graphics.RectF(w * fx - dstW * 0.55f, h * fy - dstH, w * fx + dstW * 0.45f, h * fy), null)
            for ((fx, fy) in listOf(0.84f to 0.70f)) c.drawBitmap(palm, null, android.graphics.RectF(w * fx - dstW * 0.55f, h * fy - dstH, w * fx + dstW * 0.45f, h * fy), null)
            palm.recycle()
        }
        return bmp
    }

    private fun rocks(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val rnd = Random(154)
        val reed = k.l.foliage(0xFF3E7A3C.toInt())
        for (i in 0 until 120) {
            val x = if (i % 2 == 0) k.w * 0.18f * rnd.nextFloat() else k.w * (0.82f + 0.2f * rnd.nextFloat())
            val y = k.h * (0.96f + 0.05f * rnd.nextFloat())
            k.line.strokeWidth = 2f * k.unit; k.line.color = k.jitter(reed, rnd, 0.25f)
            c.drawLine(x, y, x + (rnd.nextFloat() - 0.5f) * 12f * k.unit, y - k.h * (0.03f + 0.04f * rnd.nextFloat()), k.line)
        }
        return bmp
    }

    /** A kettuvallam houseboat: long dark hull, arched thatch roof, a lit window and a small flag. */
    private fun houseboat(k: PaintKit): Bitmap? {
        val s = k.w * 0.0011f
        val bw = (360 * s).toInt(); val bh = (170 * s).toInt()
        val (c, bmp) = k.newSprite(bw, bh) ?: return null
        val l = k.l
        val ox = bw / 2f; val oy = bh * 0.85f
        fun px(x: Float) = ox + x * s
        fun py(y: Float) = oy + y * s
        val hull = Path()
        hull.moveTo(px(-165f), py(-20f)); hull.quadTo(px(-185f), py(-5f), px(-150f), py(14f)); hull.lineTo(px(150f), py(14f)); hull.quadTo(px(185f), py(-5f), px(165f), py(-20f)); hull.close()
        val hc = l.shade(0xFF3A2A1E.toInt())
        k.shader = LinearGradient(0f, py(-20f), 0f, py(14f), k.lighten(hc, 0.2f), k.darken(hc, 0.3f), Shader.TileMode.CLAMP)
        c.drawPath(hull, k.fill)
        k.shader = null
        // Cabin walls and the arched thatched roof.
        k.fill.color = l.shade(0xFFC9A66E.toInt())
        c.drawRect(px(-120f), py(-62f), px(120f), py(-20f), k.fill)
        val roof = Path()
        roof.moveTo(px(-140f), py(-58f)); roof.quadTo(px(0f), py(-130f), px(140f), py(-58f)); roof.close()
        k.shader = LinearGradient(0f, py(-110f), 0f, py(-58f), l.shade(0xFFB89A5E.toInt()), l.shade(0xFF6E5430.toInt()), Shader.TileMode.CLAMP)
        c.drawPath(roof, k.fill)
        k.shader = null
        k.line.strokeWidth = max3(1f, 1.4f * s); k.line.color = k.alpha(Color.BLACK, 60)
        for (i in -6..6) c.drawLine(px(i * 20f), py(-58f), px(i * 20f * 0.85f), py(-106f + Math.abs(i) * 4f), k.line)
        // Windows and door.
        for (wx in floatArrayOf(-90f, -40f, 50f, 90f)) {
            k.fill.color = if (l.windowsLit) 0xFFFFD27A.toInt() else l.shade(0xFF3F4756.toInt())
            c.drawRect(px(wx - 10f), py(-52f), px(wx + 10f), py(-34f), k.fill)
        }
        k.fill.color = l.shade(0xFF4A2E20.toInt())
        c.drawRect(px(0f), py(-56f), px(24f), py(-20f), k.fill)
        // Flag and pole.
        k.line.strokeWidth = max3(1.5f, 2f * s); k.line.color = l.shade(0xFF3A2C22.toInt())
        c.drawLine(px(120f), py(-20f), px(120f), py(-100f), k.line)
        k.fill.color = l.shade(0xFFD9442F.toInt())
        c.drawRect(px(120f), py(-100f), px(145f), py(-88f), k.fill)
        return bmp
    }

    private fun max3(a: Float, b: Float) = if (a > b) a else b

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        if (stage == 1) { water.draw(canvas, timeMs, windKmh, shift(0.4f)); return }
        if (stage == 2) {
            val b = sprites["boat"] ?: return
            val t = timeMs / 1000f
            canvas.save()
            canvas.translate(w * 0.5f + shift(0.55f) + sin(t * 0.3f) * w * 0.004f, h * 0.745f + sin(t * 0.8f) * h * 0.0025f)
            canvas.rotate(sin(t * 0.6f) * 0.7f)
            canvas.drawBitmap(b, -b.width / 2f, -b.height * 0.85f, spritePaint)
            canvas.restore()
        }
    }
}
