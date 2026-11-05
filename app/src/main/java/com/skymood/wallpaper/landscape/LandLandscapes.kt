package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.sin
import com.skymood.wallpaper.fauna.GroundSlot
import com.skymood.wallpaper.fauna.GroundY
import com.skymood.wallpaper.fauna.Species
import kotlin.random.Random

/** Dense conifer forest in front of a misty mountain range, framed by big pines and a meadow. */
class ForestLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.45f, 0.82f, 0.1f), PaintLayer(0.6f, 1f, 0.5f), PaintLayer(0.5f, 1f, 1f))

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
        val r = Ridge(width, h * 0.9f, h * 0.035f, false, 10)
        return listOf(
            GroundSlot(Species.DEER, 0.28f, 0.72f, GroundY { x -> r.yAt(x) + h * 0.05f }, 1.15f, 2),
            GroundSlot(Species.HARE, 0.3f, 0.7f, GroundY { x -> r.yAt(x) + h * 0.075f }, 1f, 1),
        )
    }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(far(k), mid(k), near(k)))

    private fun far(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(101)
        k.mountain(
            c, Ridge(k.width, h * 0.7f, h * 0.15f, true, 7), h * 0.82f,
            l.distant(l.ground(0xFF8798AE.toInt()), 0.4f), l.distant(l.ground(0xFF5F7088.toInt()), 0.4f), rnd,
        )
        val ridge = Ridge(k.width, h * 0.75f, h * 0.035f, false, 8)
        val ground = Path(); ridge.addTo(ground, h * 0.82f)
        k.fill.color = l.distant(l.foliage(0xFF3E6450.toInt()), 0.42f)
        c.drawPath(ground, k.fill)
        val litBase = l.distant(l.foliage(0xFF4F7A5E.toInt()), 0.4f)
        val darkBase = l.distant(l.foliage(0xFF2F4F3E.toInt()), 0.4f)
        var x = -k.margin
        while (x < k.w + k.margin) {
            val th = h * (0.025f + rnd.nextFloat() * 0.03f)
            k.simplePine(c, x, ridge.yAt(x) + th * 0.25f, th, k.jitter(litBase, rnd, 0.18f), darkBase, rnd)
            x += 5f + rnd.nextFloat() * 8f
        }
        k.haze(c, h * 0.62f, h * 0.82f, 0.5f)
        return bmp
    }

    private fun mid(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(202)
        val ridge = Ridge(k.width, h * 0.8f, h * 0.05f, false, 9)
        k.hill(c, ridge, h, l.distant(l.ground(0xFF3D6B45.toInt()), 0.2f), l.ground(0xFF24402A.toInt()), rnd, 200)
        val litBase = l.distant(l.foliage(0xFF4C8255.toInt()), 0.18f)
        val darkBase = l.distant(l.foliage(0xFF1C3A2A.toInt()), 0.18f)
        val trunk = l.shade(0xFF3A2C22.toInt())
        for (row in 0 until 2) {
            var x = -k.margin + rnd.nextFloat() * 10f
            while (x < k.w + k.margin) {
                val th = h * (0.06f + rnd.nextFloat() * 0.05f) * (if (row == 0) 0.85f else 1f)
                k.conifer(c, x, ridge.yAt(x) + th * (0.15f + row * 0.12f), th, k.jitter(litBase, rnd, 0.2f), darkBase, trunk, rnd, 0.45f)
                x += (16f + rnd.nextFloat() * 18f) * k.unit
            }
        }
        k.haze(c, h * 0.74f, h * 0.92f, 0.22f)
        return bmp
    }

    private fun near(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(303)
        val ridge = Ridge(k.width, h * 0.9f, h * 0.035f, false, 10)
        k.hill(c, ridge, h, l.ground(0xFF4E7A3A.toInt()), l.ground(0xFF253F1E.toInt()), rnd, 0)
        val litBase = l.foliage(0xFF4F8A4A.toInt())
        val darkBase = l.foliage(0xFF183322.toInt())
        val trunk = l.shade(0xFF4A3628.toInt())
        for (s in floatArrayOf(-0.04f, 0.07f, 0.17f, 0.83f, 0.93f, 1.03f)) {
            val x = w * s + (rnd.nextFloat() - 0.5f) * w * 0.03f
            val th = h * (0.22f + rnd.nextFloat() * 0.12f)
            val gy = ridge.yAt(x)
            k.shader = RadialGradient(x, gy + th * 0.02f, th * 0.28f, 0x77000000, 0, Shader.TileMode.CLAMP)
            c.drawCircle(x, gy + th * 0.02f, th * 0.28f, k.fill)
            k.shader = null
            k.conifer(c, x, gy + th * 0.03f, th, k.jitter(litBase, rnd, 0.14f), darkBase, trunk, rnd, 1f)
        }
        k.grass(c, ridge, h, 3200, l.ground(0xFF7FA85A.toInt()), l.ground(0xFF1E3518.toInt()), rnd)
        k.flowers(c, ridge, h, 60, rnd)
        return bmp
    }
}

/** Layered rocky mountains with lit and shadowed faces, a pine belt and rocks in front. */
class MountainLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.4f, 0.8f, 0.1f), PaintLayer(0.5f, 0.92f, 0.45f), PaintLayer(0.62f, 1f, 1f))

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
        val r = Ridge(width, h * 0.9f, h * 0.06f, true, 14)
        return listOf(GroundSlot(Species.GOAT, 0.15f, 0.85f, GroundY { x -> r.yAt(x) + h * 0.004f }, 1f, 2))
    }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(far(k), mid(k), near(k)))

    private fun far(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        k.peaks(
            c, Ridge(k.width, h * 0.66f, h * 0.22f, true, 4), h * 0.8f,
            l.distant(l.ground(0xFF8D9DB3.toInt()), 0.45f), l.distant(l.ground(0xFF5C6B82.toInt()), 0.45f),
            Random(11), snowLine = h * 0.5f,
        )
        k.haze(c, h * 0.55f, h * 0.8f, 0.45f)
        return bmp
    }

    private fun mid(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(12)
        k.peaks(
            c, Ridge(k.width, h * 0.76f, h * 0.15f, true, 5), h * 0.92f,
            l.distant(l.ground(0xFF6B788C.toInt()), 0.22f), l.distant(l.ground(0xFF3A4556.toInt()), 0.22f), rnd,
        )
        val forest = Ridge(k.width, h * 0.86f, h * 0.02f, false, 6)
        k.hill(c, forest, h * 0.92f, l.distant(l.foliage(0xFF2F5A3C.toInt()), 0.2f), l.foliage(0xFF1B3626.toInt()), rnd, 100)
        val lit = l.distant(l.foliage(0xFF3E744D.toInt()), 0.2f)
        val dark = l.distant(l.foliage(0xFF1C3A2A.toInt()), 0.2f)
        var x = -k.margin
        while (x < k.w + k.margin) {
            val th = h * (0.04f + rnd.nextFloat() * 0.035f)
            k.conifer(c, x, forest.yAt(x) + th * 0.25f, th, k.jitter(lit, rnd, 0.2f), dark, l.shade(0xFF33261C.toInt()), rnd, 0.35f)
            x += (13f + rnd.nextFloat() * 14f) * k.unit
        }
        k.haze(c, h * 0.78f, h * 0.9f, 0.2f)
        return bmp
    }

    private fun near(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(13)
        val ridge = Ridge(k.width, h * 0.9f, h * 0.06f, true, 14)
        k.peaks(c, ridge, h, l.ground(0xFF4A5260.toInt()), l.ground(0xFF1E232C.toInt()), rnd)
        val lit = l.foliage(0xFF3F7A4B.toInt()); val dark = l.foliage(0xFF17301F.toInt())
        for (i in 0 until 7) {
            val x = w * (i / 6f) + (rnd.nextFloat() - 0.5f) * w * 0.08f
            val th = h * (0.1f + rnd.nextFloat() * 0.12f)
            k.conifer(c, x, ridge.yAt(x) + th * 0.12f, th, k.jitter(lit, rnd, 0.14f), dark, l.shade(0xFF3A2C22.toInt()), rnd, 0.9f)
        }
        for (i in 0 until 6) k.rock(c, w * rnd.nextFloat(), h * (0.94f + rnd.nextFloat() * 0.05f), w * (0.03f + rnd.nextFloat() * 0.04f), l.ground(0xFF5A6270.toInt()), rnd)
        return bmp
    }
}

/** Gentle rolling hills: patchwork fields in the distance, scattered leafy trees, big oaks in front. */
class HillLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.5f, 0.86f, 0.1f), PaintLayer(0.6f, 1f, 0.5f), PaintLayer(0.32f, 1f, 1f))

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
        val r = Ridge(width, h * 0.9f, h * 0.045f, false, 10)
        return listOf(
            GroundSlot(Species.SHEEP, 0.2f, 0.8f, GroundY { x -> r.yAt(x) + h * 0.05f }, 1f, 4),
            GroundSlot(Species.COW, 0.3f, 0.7f, GroundY { x -> r.yAt(x) + h * 0.07f }, 1.2f, 1),
        )
    }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(far(k), mid(k), near(k)))

    private fun far(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(21)
        val ridge = Ridge(k.width, h * 0.7f, h * 0.07f, false, 8)
        k.hill(c, ridge, h * 0.86f, l.distant(l.ground(0xFF8DB46E.toInt()), 0.4f), l.distant(l.ground(0xFF5E8F50.toInt()), 0.4f), rnd, 300)
        k.fields(c, ridge, h * 0.86f, rnd, 8)
        k.haze(c, h * 0.62f, h * 0.86f, 0.4f)
        return bmp
    }

    private fun mid(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(22)
        val ridge = Ridge(k.width, h * 0.78f, h * 0.06f, false, 9)
        k.hill(c, ridge, h, l.distant(l.ground(0xFF66A055.toInt()), 0.14f), l.ground(0xFF3B7035.toInt()), rnd, 500)
        k.fields(c, ridge, h, rnd, 6)
        val lit = l.distant(l.foliage(0xFF6FB85A.toInt()), 0.12f); val dark = l.distant(l.foliage(0xFF2A5A2A.toInt()), 0.12f)
        var x = -k.margin
        while (x < k.w + k.margin) {
            if (rnd.nextFloat() < 0.5f) {
                val th = h * (0.04f + rnd.nextFloat() * 0.04f)
                k.broadleaf(c, x, ridge.yAt(x) + th * 0.1f, th, k.jitter(lit, rnd, 0.18f), dark, l.shade(0xFF4A3A2A.toInt()), rnd, 0.35f)
            }
            x += (38f + rnd.nextFloat() * 36f) * k.unit
        }
        k.haze(c, h * 0.8f, h * 0.95f, 0.15f)
        return bmp
    }

    private fun near(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(23)
        val ridge = Ridge(k.width, h * 0.9f, h * 0.045f, false, 10)
        k.hill(c, ridge, h, l.ground(0xFF4F9040.toInt()), l.ground(0xFF285522.toInt()), rnd, 100)
        val lit = l.foliage(0xFF74C25E.toInt()); val dark = l.foliage(0xFF24502A.toInt())
        for (s in floatArrayOf(0.1f, 0.88f)) {
            val x = w * s
            val th = h * (0.2f + rnd.nextFloat() * 0.06f)
            val gy = ridge.yAt(x)
            k.shader = RadialGradient(x, gy, th * 0.3f, 0x66000000, 0, Shader.TileMode.CLAMP)
            c.drawCircle(x, gy, th * 0.3f, k.fill)
            k.shader = null
            k.broadleaf(c, x, gy + th * 0.02f, th, lit, dark, l.shade(0xFF4E3A28.toInt()), rnd, 1f)
        }
        k.grass(c, ridge, h, 2400, l.ground(0xFF8FC060.toInt()), l.ground(0xFF234A1C.toInt()), rnd)
        k.flowers(c, ridge, h, 70, rnd)
        return bmp
    }
}

/** A meadow with grass that sways in the wind, in front of a leafy tree line and a big oak. */
class GrassFieldLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.55f, 0.8f, 0.15f), PaintLayer(0.30f, 1f, 0.6f))

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
        val r = Ridge(width, h * 0.78f, h * 0.015f, false, 15)
        return listOf(
            GroundSlot(Species.COW, 0.05f, 0.65f, GroundY { x -> r.yAt(x) + h * 0.09f }, 1.5f, 2),
            GroundSlot(Species.HORSE, 0.1f, 0.6f, GroundY { x -> r.yAt(x) + h * 0.07f }, 1.3f, 1),
        )
    }

    private val darkBlade = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val lightBlade = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val bx = FloatArray(BLADES); private val by = FloatArray(BLADES)
    private val bh = FloatArray(BLADES); private val bp = FloatArray(BLADES)
    private val darkLines = FloatArray(BLADES * 4); private val lightLines = FloatArray(BLADES * 4)

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        val rnd = Random(13)
        for (i in 0 until BLADES) {
            val d = rnd.nextFloat()
            by[i] = h * (0.8f + 0.2f * d)
            bx[i] = -w * 0.05f + rnd.nextFloat() * w * 1.1f
            bh[i] = h * (0.012f + 0.05f * d * d) * (0.7f + rnd.nextFloat() * 0.6f)
            bp[i] = rnd.nextFloat() * 6.28f
        }
        darkBlade.strokeWidth = 3f * unit.coerceAtLeast(0.6f)
        lightBlade.strokeWidth = 2.2f * unit.coerceAtLeast(0.6f)
    }

    override fun onLightingChanged() {
        val l = lighting
        darkBlade.color = l.foliage(0xFF2F6A2A.toInt())
        lightBlade.color = l.foliage(0xFF86C15A.toInt())
    }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(far(k), meadow(k)))

    private fun far(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(31)
        val hills = Ridge(k.width, h * 0.72f, h * 0.05f, false, 12)
        k.hill(c, hills, h * 0.8f, l.distant(l.ground(0xFF8DB46E.toInt()), 0.35f), l.distant(l.ground(0xFF5E8F50.toInt()), 0.35f), rnd, 200)
        val ridge = Ridge(k.width, h * 0.76f, h * 0.012f, false, 14)
        k.fill.color = l.distant(l.foliage(0xFF3F7A3C.toInt()), 0.25f)
        val strip = Path(); ridge.addTo(strip, h * 0.8f)
        c.drawPath(strip, k.fill)
        val lit = l.distant(l.foliage(0xFF6FB85A.toInt()), 0.2f); val dark = l.distant(l.foliage(0xFF2A5A2A.toInt()), 0.2f)
        var x = -k.margin
        while (x < k.w + k.margin) {
            val th = h * (0.03f + rnd.nextFloat() * 0.025f)
            k.broadleaf(c, x, ridge.yAt(x) + th * 0.12f, th, k.jitter(lit, rnd, 0.18f), dark, l.shade(0xFF4A3A2A.toInt()), rnd, 0.25f)
            x += (22f + rnd.nextFloat() * 20f) * k.unit
        }
        k.haze(c, h * 0.66f, h * 0.8f, 0.3f)
        return bmp
    }

    private fun meadow(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(32)
        val ridge = Ridge(k.width, h * 0.78f, h * 0.015f, false, 15)
        k.hill(c, ridge, h, l.ground(0xFF62A046.toInt()), l.ground(0xFF2D5E22.toInt()), rnd, 900)
        k.fields(c, ridge, h, rnd, 4)
        val ox = w * 0.84f
        val th = h * 0.26f
        val gy = h * 0.8f
        k.shader = RadialGradient(ox, gy, th * 0.3f, 0x66000000, 0, Shader.TileMode.CLAMP)
        c.drawCircle(ox, gy, th * 0.3f, k.fill)
        k.shader = null
        k.broadleaf(c, ox, gy, th, l.foliage(0xFF7CC862.toInt()), l.foliage(0xFF2A5A2A.toInt()), l.shade(0xFF4E3A28.toInt()), rnd, 1f)
        k.grass(c, ridge, h, 1400, l.ground(0xFF8FC060.toInt()), l.ground(0xFF234A1C.toInt()), rnd)
        k.flowers(c, ridge, h, 160, rnd)
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != layers.size) return
        val t = timeMs / 1000f
        val gust = 1f + windKmh / 15f
        val sway = (0.15f + windKmh / 40f).coerceAtMost(1.4f)
        val s = shift(1f)
        var d = 0; var li = 0
        for (i in 0 until BLADES) {
            val bend = (sin(t * gust + bp[i] + bx[i] * 0.004f) * 0.5f + 0.5f * sway) * bh[i] * sway
            val x0 = bx[i] + s
            if (i % 3 == 0) {
                lightLines[li++] = x0; lightLines[li++] = by[i]
                lightLines[li++] = x0 + bend; lightLines[li++] = by[i] - bh[i]
            } else {
                darkLines[d++] = x0; darkLines[d++] = by[i]
                darkLines[d++] = x0 + bend; darkLines[d++] = by[i] - bh[i]
            }
        }
        canvas.drawLines(darkLines, 0, d, darkBlade)
        canvas.drawLines(lightLines, 0, li, lightBlade)
    }

    private companion object {
        const val BLADES = 420
    }
}

/** Huge snow-capped peaks with lit and shadowed faces, above a snow-covered pine forest. */
class SnowyPeaksLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.25f, 0.8f, 0.08f), PaintLayer(0.4f, 0.92f, 0.45f), PaintLayer(0.66f, 1f, 1f))

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
        val r = Ridge(width, h * 0.9f, h * 0.035f, false, 17)
        return listOf(
            GroundSlot(Species.GOAT, 0.2f, 0.8f, GroundY { x -> r.yAt(x) + h * 0.025f }, 1.1f, 2),
            GroundSlot(Species.HARE, 0.3f, 0.7f, GroundY { x -> r.yAt(x) + h * 0.04f }, 1f, 1),
        )
    }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(far(k), mid(k), near(k)))

    private fun far(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        k.peaks(
            c, Ridge(k.width, h * 0.66f, h * 0.38f, true, 15), h * 0.8f,
            l.distant(l.shade(0xFF8794AB.toInt()), 0.3f), l.distant(l.shade(0xFF566379.toInt()), 0.3f),
            Random(41), snowLine = h * 0.6f, snowDepth = 0.22f,
        )
        k.haze(c, h * 0.55f, h * 0.8f, 0.4f)
        return bmp
    }

    private fun mid(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(42)
        val ridge = Ridge(k.width, h * 0.78f, h * 0.17f, true, 16)
        k.peaks(
            c, ridge, h * 0.92f, l.distant(l.shade(0xFF65738A.toInt()), 0.15f), l.distant(l.shade(0xFF39445A.toInt()), 0.15f),
            rnd, snowLine = h * 0.76f, snowDepth = 0.15f,
        )
        k.haze(c, h * 0.78f, h * 0.9f, 0.25f)
        return bmp
    }

    private fun near(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(43)
        val ridge = Ridge(k.width, h * 0.9f, h * 0.035f, false, 17)
        val snowTop = l.shade(Lighting.SNOW)
        k.hill(c, ridge, h, snowTop, k.mix(snowTop, 0xFF9CB0CE.toInt(), 0.5f), rnd, 0)
        k.line.color = k.alpha(Color.WHITE, 90)
        k.line.strokeWidth = 2f * k.unit
        repeat(70) {
            val x = rnd.nextFloat() * w
            val y = ridge.yAt(x) + (h - ridge.yAt(x)) * rnd.nextFloat()
            c.drawLine(x, y, x + w * (0.05f + rnd.nextFloat() * 0.1f), y - h * 0.004f, k.line)
        }
        val lit = k.mix(l.shade(0xFF3A6A55.toInt()), snowTop, 0.28f); val dark = k.mix(l.shade(0xFF1C3A2E.toInt()), snowTop, 0.1f)
        for (i in 0 until 9) {
            val x = w * (i / 8f) + (rnd.nextFloat() - 0.5f) * w * 0.07f
            val th = h * (0.07f + rnd.nextFloat() * 0.13f)
            k.conifer(c, x, ridge.yAt(x) + th * 0.1f, th, k.jitter(lit, rnd, 0.14f), dark, l.shade(0xFF3A2C22.toInt()), rnd, 0.8f)
        }
        for (i in 0 until 5) k.rock(c, w * rnd.nextFloat(), h * (0.95f + rnd.nextFloat() * 0.04f), w * (0.02f + rnd.nextFloat() * 0.03f), l.shade(0xFF6F7788.toInt()), rnd)
        return bmp
    }
}

/** Farmland with a small village: houses with lit windows after dark, a church, trees and fences. */
class VillageLandscape : PaintedLandscape() {
    override val layers = arrayOf(PaintLayer(0.5f, 0.86f, 0.1f), PaintLayer(0.62f, 0.96f, 0.55f), PaintLayer(0.8f, 1f, 1f))

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
        val r = Ridge(width, h * 0.9f, h * 0.02f, false, 21)
        return listOf(
            GroundSlot(Species.COW, 0.1f, 0.9f, GroundY { x -> r.yAt(x) + h * 0.045f }, 1.3f, 1),
            GroundSlot(Species.SHEEP, 0.1f, 0.9f, GroundY { x -> r.yAt(x) + h * 0.05f }, 1.1f, 2),
            GroundSlot(Species.DOG, 0.1f, 0.9f, GroundY { x -> r.yAt(x) + h * 0.055f }, 1.2f, 1),
        )
    }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(far(k), mid(k), near(k)))

    private fun far(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(51)
        val ridge = Ridge(k.width, h * 0.7f, h * 0.05f, false, 19)
        k.hill(c, ridge, h * 0.86f, l.distant(l.ground(0xFF8DB46E.toInt()), 0.4f), l.distant(l.ground(0xFF5E8F50.toInt()), 0.4f), rnd, 300)
        k.fields(c, ridge, h * 0.86f, rnd, 9)
        k.haze(c, h * 0.62f, h * 0.86f, 0.4f)
        return bmp
    }

    private fun mid(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(52)
        val ridge = Ridge(k.width, h * 0.8f, h * 0.035f, false, 20)
        k.hill(c, ridge, h * 0.96f, l.ground(0xFF6EA656.toInt()), l.ground(0xFF3F7A35.toInt()), rnd, 500)
        k.fields(c, ridge, h * 0.96f, rnd, 5)
        val lit = l.foliage(0xFF6FB85A.toInt()); val dark = l.foliage(0xFF2A5A2A.toInt())
        var x = w * 0.05f
        var index = 0
        while (x < w * 0.97f) {
            val base = ridge.yAt(x) + h * 0.012f
            if (rnd.nextFloat() < 0.32f) {
                val th = h * (0.05f + rnd.nextFloat() * 0.03f)
                k.broadleaf(c, x, base, th, k.jitter(lit, rnd, 0.16f), dark, l.shade(0xFF4A3A2A.toInt()), rnd, 0.5f)
                x += w * 0.05f
                continue
            }
            val church = index == 3
            val hw = w * (if (church) 0.03f else 0.034f + rnd.nextFloat() * 0.01f)
            val hh = if (church) hw * 2.1f else hw * (0.95f + rnd.nextFloat() * 0.3f)
            house(k, c, x, base, hw, hh, rnd, church)
            index++
            x += hw * 2.3f + (10f + rnd.nextFloat() * 34f) * k.unit
        }
        k.haze(c, h * 0.82f, h * 0.94f, 0.14f)
        return bmp
    }

    private fun near(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(53)
        val ridge = Ridge(k.width, h * 0.9f, h * 0.02f, false, 21)
        k.hill(c, ridge, h, l.ground(0xFF5A9B44.toInt()), l.ground(0xFF2B5A22.toInt()), rnd, 200)
        val path = Path()
        path.moveTo(w * 0.55f, h * 0.9f)
        path.quadTo(w * 0.7f, h * 0.94f, w * 0.5f, h)
        path.lineTo(w * 0.25f, h)
        path.quadTo(w * 0.48f, h * 0.95f, w * 0.52f, h * 0.9f)
        path.close()
        k.fill.color = l.ground(0xFFA58B66.toInt())
        c.drawPath(path, k.fill)
        k.line.color = l.shade(0xFF6B5240.toInt())
        k.line.strokeWidth = 3f * k.unit
        var fx = -k.margin
        while (fx < w + k.margin) {
            val fy = ridge.yAt(fx) + h * 0.02f
            val fy2 = ridge.yAt(fx + 22f * k.unit) + h * 0.02f
            c.drawLine(fx, fy, fx, fy - h * 0.022f, k.line)
            c.drawLine(fx, fy - h * 0.016f, fx + 22f * k.unit, fy2 - h * 0.016f, k.line)
            c.drawLine(fx, fy - h * 0.007f, fx + 22f * k.unit, fy2 - h * 0.007f, k.line)
            fx += 22f * k.unit
        }
        k.grass(c, ridge, h, 1800, l.ground(0xFF86BC58.toInt()), l.ground(0xFF234A1C.toInt()), rnd)
        k.flowers(c, ridge, h, 50, rnd)
        return bmp
    }

    /** A cottage with a gabled two-tone roof, chimney, door and windows; a church gets a tower. */
    private fun house(k: PaintKit, c: Canvas, x: Float, base: Float, hw: Float, hh: Float, rnd: Random, church: Boolean) {
        val l = k.l
        val walls = intArrayOf(0xFFEADBC2.toInt(), 0xFFE3CFAE.toInt(), 0xFFF0E4CF.toInt(), 0xFFD9C3A0.toInt())
        val wall = l.shade(if (church) 0xFFF1EBDD.toInt() else walls[rnd.nextInt(walls.size)])
        val roofBase = l.ground(if (church) 0xFF5E5A66.toInt() else 0xFFA4483A.toInt())
        k.fill.color = k.alpha(Color.BLACK, 55)
        c.drawOval(x - hw * 1.2f, base - hw * 0.1f, x + hw * 1.4f, base + hw * 0.18f, k.fill)
        k.shader = LinearGradient(
            x - hw, 0f, x + hw, 0f,
            if (k.sunOnLeft) k.lighten(wall, 0.1f) else k.darken(wall, 0.3f),
            if (k.sunOnLeft) k.darken(wall, 0.3f) else k.lighten(wall, 0.1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(x - hw, base - hh, x + hw, base, k.fill)
        k.shader = null
        val roofH = hw * (if (church) 1.1f else 0.85f)
        val eave = hw * 0.18f
        val left = Path(); val right = Path()
        left.moveTo(x, base - hh - roofH); left.lineTo(x - hw - eave, base - hh + eave * 0.4f); left.lineTo(x, base - hh + eave * 0.4f); left.close()
        right.moveTo(x, base - hh - roofH); right.lineTo(x + hw + eave, base - hh + eave * 0.4f); right.lineTo(x, base - hh + eave * 0.4f); right.close()
        k.fill.color = if (k.sunOnLeft) k.lighten(roofBase, 0.14f) else k.darken(roofBase, 0.3f)
        c.drawPath(left, k.fill)
        k.fill.color = if (k.sunOnLeft) k.darken(roofBase, 0.3f) else k.lighten(roofBase, 0.14f)
        c.drawPath(right, k.fill)
        k.line.color = k.alpha(Color.BLACK, 40)
        k.line.strokeWidth = max(1f, hw * 0.03f)
        for (i in 1..3) {
            val u = i / 4f
            val ry = base - hh - roofH + (roofH + eave * 0.4f) * u
            c.drawLine(x - (hw + eave) * u, ry, x + (hw + eave) * u, ry, k.line)
        }
        if (!church) {
            k.fill.color = k.darken(wall, 0.35f)
            val cxp = x + hw * 0.45f * -k.sun
            c.drawRect(cxp - hw * 0.1f, base - hh - roofH * 0.9f, cxp + hw * 0.1f, base - hh - roofH * 0.4f, k.fill)
        } else {
            val tw = hw * 0.5f
            val th = hh * 0.9f
            k.fill.color = k.darken(wall, 0.12f)
            c.drawRect(x - tw, base - hh - th, x + tw, base - hh + 1f, k.fill)
            val spire = Path()
            spire.moveTo(x, base - hh - th - hw * 1.2f); spire.lineTo(x - tw * 1.15f, base - hh - th); spire.lineTo(x + tw * 1.15f, base - hh - th); spire.close()
            k.fill.color = k.darken(roofBase, 0.15f)
            c.drawPath(spire, k.fill)
            k.line.color = k.darken(wall, 0.4f)
            k.line.strokeWidth = max(1.5f, hw * 0.05f)
            val top = base - hh - th - hw * 1.2f
            c.drawLine(x, top, x, top - hw * 0.35f, k.line)
            c.drawLine(x - hw * 0.1f, top - hw * 0.2f, x + hw * 0.1f, top - hw * 0.2f, k.line)
            k.fill.color = if (l.windowsLit) 0xFFFFD27A.toInt() else l.shade(0xFF3F4756.toInt())
            c.drawRect(x - tw * 0.3f, base - hh - th * 0.7f, x + tw * 0.3f, base - hh - th * 0.4f, k.fill)
        }
        k.fill.color = k.darken(wall, 0.55f)
        c.drawRect(x - hw * 0.14f, base - hh * 0.5f, x + hw * 0.14f, base, k.fill)
        val ws = hw * 0.3f
        for (side in intArrayOf(-1, 1)) {
            val wx = x + side * hw * 0.58f
            val wy = base - hh * 0.72f
            if (l.windowsLit) {
                k.shader = RadialGradient(wx, wy + ws / 2, ws * 2f, 0x66FFC060, 0, Shader.TileMode.CLAMP)
                c.drawCircle(wx, wy + ws / 2, ws * 2f, k.fill)
                k.shader = null
                k.fill.color = 0xFFFFD27A.toInt()
            } else {
                k.fill.color = l.shade(0xFF4A5566.toInt())
            }
            c.drawRect(wx - ws / 2, wy, wx + ws / 2, wy + ws, k.fill)
            k.line.color = k.darken(wall, 0.45f)
            k.line.strokeWidth = max(1f, hw * 0.025f)
            c.drawLine(wx, wy, wx, wy + ws, k.line)
            c.drawLine(wx - ws / 2, wy + ws / 2, wx + ws / 2, wy + ws / 2, k.line)
        }
    }
}
