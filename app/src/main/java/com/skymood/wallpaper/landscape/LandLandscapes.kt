package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.sin
import com.skymood.wallpaper.fauna.FlowerBed
import com.skymood.wallpaper.fauna.GroundSlot
import com.skymood.wallpaper.fauna.MeadowSpec
import com.skymood.wallpaper.fauna.GroundY
import com.skymood.wallpaper.fauna.Species
import kotlin.random.Random

/** Dense conifer forest in front of a misty mountain range, framed by big pines and a meadow. */
class ForestLandscape : PaintedLandscape() {
    private var meadowSpec: MeadowSpec? = null
    private var meadowFor = 0
    override fun meadow(): MeadowSpec {
        if (meadowSpec == null || meadowFor != width * 31 + height) {
            meadowFor = width * 31 + height
            val h = height.toFloat()
            val r = Ridge(width, h * 0.9f, h * 0.035f, false, 10)
            meadowSpec = MeadowSpec(listOf(FlowerBed(0.02f, 0.98f, GroundY { x -> r.yAt(x) }, h * 0.06f, 14)))
        }
        return meadowSpec!!
    }
    override val layers = arrayOf(
        PaintLayer(0.45f, 0.82f, 0.06f), PaintLayer(0.58f, 0.92f, 0.25f),
        PaintLayer(0.55f, 1f, 0.55f), PaintLayer(0.34f, 1f, 1f),
    )

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
            // A pair of gaur-like wild cattle in the trees, and a fox that only shows at dusk, dawn and night.
            GroundSlot(Species.WILD_COW, 0.3f, 0.7f, GroundY { x -> r.yAt(x) + h * 0.062f }, 1.05f, 2),
            GroundSlot(Species.FOX, 0.15f, 0.85f, GroundY { x -> r.yAt(x) + h * 0.07f }, 1.2f, 1),
            GroundSlot(Species.HARE, 0.3f, 0.7f, GroundY { x -> r.yAt(x) + h * 0.075f }, 1f, 1),
        )
    }

    override fun paintAll(k: PaintKit) = PaintResult(arrayOf(far(k), hills(k), wall(k), near(k)))

    /** Snow-dusted, faceted peaks far off, and a low, bluish wooded range in front of them. */
    private fun far(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(101)
        k.peaks(
            c, Ridge(k.width, h * 0.68f, h * 0.2f, true, 4), h * 0.82f,
            l.distant(l.ground(0xFF8497B2.toInt()), 0.28f), l.distant(l.ground(0xFF475972.toInt()), 0.28f), rnd,
            snowLine = h * 0.6f, snowDepth = 0.1f,
        )
        k.haze(c, h * 0.56f, h * 0.8f, 0.3f)
        val ridge = Ridge(k.width, h * 0.73f, h * 0.035f, false, 8)
        val body = Path(); ridge.addTo(body, h * 0.82f)
        k.fill.color = l.distant(l.foliage(0xFF3A5E4C.toInt()), 0.4f)
        c.drawPath(body, k.fill)
        val lit = l.distant(l.foliage(0xFF4F7A5E.toInt()), 0.4f)
        val dark = l.distant(l.foliage(0xFF2F4F3E.toInt()), 0.4f)
        var x = -k.margin
        while (x < k.w + k.margin) {
            val th = h * (0.028f + rnd.nextFloat() * 0.027f)
            k.simplePine(c, x, ridge.yAt(x) + th * 0.25f, th, k.jitter(lit, rnd, 0.18f), dark, rnd)
            x += 4f + rnd.nextFloat() * 7f
        }
        k.haze(c, h * 0.62f, h * 0.82f, 0.45f)
        return bmp
    }

    /** Forest on rolling hills, small and hazy: two rows of spruce with the odd leafy tree. */
    private fun hills(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(152)
        val ridge = Ridge(k.width, h * 0.775f, h * 0.04f, false, 9)
        k.hill(c, ridge, h * 0.92f, l.distant(l.ground(0xFF37603F.toInt()), 0.3f), l.ground(0xFF21402B.toInt()), rnd, 80)
        val lit = l.distant(l.foliage(0xFF40785A.toInt()), 0.3f)
        val dark = l.distant(l.foliage(0xFF1A3A2C.toInt()), 0.3f)
        val bark = l.distant(l.shade(0xFF3A2C22.toInt()), 0.3f)
        val leafLit = l.distant(l.foliage(0xFF6E9C3A.toInt()), 0.3f)
        val leafDark = l.distant(l.foliage(0xFF2F5A2A.toInt()), 0.3f)
        for (row in 0 until 2) {
            var x = -k.margin + rnd.nextFloat() * 10f
            while (x < k.w + k.margin) {
                val th = h * (0.05f + rnd.nextFloat() * 0.03f) * (if (row == 0) 0.88f else 1f)
                val y = ridge.yAt(x) + th * (0.12f + row * 0.1f)
                if (rnd.nextFloat() < 0.12f) {
                    k.broadleaf(c, x, y, th * 0.85f, k.jitter(leafLit, rnd, 0.2f), leafDark, bark, rnd, 0.25f)
                } else {
                    k.spruce(c, x, y, th, k.jitter(lit, rnd, 0.22f), dark, bark, rnd, 0.3f, 0.06f)
                }
                x += th * (0.22f + 0.2f * rnd.nextFloat())
            }
            if (row == 0) k.haze(c, h * 0.66f, h * 0.82f, 0.22f)
        }
        k.haze(c, h * 0.7f, h * 0.9f, 0.18f)
        return bmp
    }

    /**
     * The forest wall: three rows of mixed trees (spruce, birch, oak, pine), each a little nearer
     * and darker than the last, with the wood's dark interior between their trunks, mist between
     * the rows and shafts of sun falling through the canopy.
     */
    private fun wall(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(202)
        val ridge = Ridge(k.width, h * 0.83f, h * 0.03f, false, 9)
        k.hill(c, ridge, h, l.distant(l.ground(0xFF37603D.toInt()), 0.1f), l.ground(0xFF1B3322.toInt()), rnd, 120)
        val floor = l.foliage(0xFF07120C.toInt())
        for (row in 0 until 3) {
            val f = row / 2f
            val dist = 0.26f - 0.2f * f
            val base = h * (0.84f + 0.035f * f)
            val rowRidge = Ridge(k.width, base, h * 0.012f, false, 20 + row)
            val lit = l.distant(l.foliage(0xFF3C7552.toInt()), dist)
            val dark = l.distant(l.foliage(0xFF163527.toInt()), dist)
            val bark = l.distant(l.shade(0xFF3A2C22.toInt()), dist)
            val oakLit = l.distant(l.foliage(0xFF6C9A3E.toInt()), dist)
            val oakDark = l.distant(l.foliage(0xFF2C5428.toInt()), dist)
            val birchLeaf = l.distant(l.foliage(0xFF9CC247.toInt()), dist)
            val birchDark = l.distant(l.foliage(0xFF4A7A2A.toInt()), dist)
            // Shade pooled under the canopy: the wood's interior is dark, which makes the trees stand out.
            k.shader = LinearGradient(
                0f, base - h * 0.1f, 0f, base + h * 0.03f, k.alpha(floor, 0), k.alpha(floor, 230), Shader.TileMode.CLAMP,
            )
            c.drawRect(-k.margin, base - h * 0.1f, w + k.margin, base + h * 0.03f, k.fill)
            k.shader = null
            var x = -k.margin - rnd.nextFloat() * 30f
            while (x < w + k.margin) {
                val th = h * (0.085f + 0.045f * f + 0.05f * rnd.nextFloat())
                val y = rowRidge.yAt(x) + th * 0.04f
                val foot = 0.09f + 0.1f * f
                val pick = rnd.nextFloat()
                when {
                    pick < 0.7f -> k.spruce(c, x, y, th, k.jitter(lit, rnd, 0.24f), dark, bark, rnd, 0.5f, foot)
                    pick < 0.83f -> k.birch(c, x, y, th * 0.85f, k.jitter(birchLeaf, rnd, 0.2f), birchDark, rnd, 0.5f)
                    pick < 0.94f -> k.broadleaf(c, x, y, th * 0.85f, k.jitter(oakLit, rnd, 0.2f), oakDark, bark, rnd, 0.4f)
                    else -> k.scotsPine(c, x, y, th * 1.05f, k.jitter(lit, rnd, 0.2f), dark, bark, rnd, 0.4f)
                }
                x += th * (0.2f + 0.2f * rnd.nextFloat())
            }
            if (row < 2) k.haze(c, base - h * 0.14f, base + h * 0.03f, 0.16f)
        }
        k.lightShafts(c, h * 0.62f, h * 0.97f, 8, rnd, 40)
        return bmp
    }

    /** The clearing in front: grass and flowers, big trees framing the view, ferns, shrubs, mossy rocks and a log. */
    private fun near(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[3]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(303)
        val ridge = Ridge(k.width, h * 0.9f, h * 0.035f, false, 10)
        k.hill(c, ridge, h, l.ground(0xFF4A7536.toInt()), l.ground(0xFF223B1C.toInt()), rnd, 0)
        k.groundPatches(
            c, ridge, h, rnd, 46,
            intArrayOf(
                l.ground(0xFF2E5A2A.toInt()), l.ground(0xFF8FBF58.toInt()), l.ground(0xFF5A4A30.toInt()),
                l.ground(0xFF1F3A1E.toInt()), l.ground(0xFF6C9A40.toInt()),
            ),
            70,
        )
        k.dapple(c, ridge, h, rnd, 9)
        k.grassCarpet(
            c, ridge, h, rnd,
            deep = l.foliage(0xFF14301A.toInt()), lush = l.ground(0xFF3F7A33.toInt()), dry = l.ground(0xFF8A8A45.toInt()),
            lit = l.ground(0xFFA6D464.toInt()), mist = l.skyBottom, density = 0.9f,
        )
        k.flowers(c, ridge, h, 40, rnd)

        val bark = l.shade(0xFF4A3628.toInt())
        val spruceLit = l.foliage(0xFF3E7A52.toInt()); val spruceDark = l.foliage(0xFF12301F.toInt())
        fun spruceAt(fx: Float, th: Float, foot: Float) {
            val x = w * fx; val y = ridge.yAt(x) + th * 0.02f
            k.treeShadow(c, x, y, th)
            k.spruce(c, x, y, th, k.jitter(spruceLit, rnd, 0.14f), spruceDark, bark, rnd, 1f, foot)
        }
        spruceAt(0.2f, h * 0.3f, 0.08f)
        spruceAt(0.9f, h * 0.42f, 0.07f)
        // A pair of silver birches in front of the big spruce on the right: pale trunks against the dark.
        val birchLit = l.foliage(0xFFA8CC4C.toInt()); val birchDark = l.foliage(0xFF4E7F2C.toInt())
        for ((fx, ht) in arrayOf(0.77f to 0.21f, 0.835f to 0.26f)) {
            val x = w * fx; val th = h * ht; val y = ridge.yAt(x) + th * 0.02f
            k.treeShadow(c, x, y, th)
            k.birch(c, x, y, th, k.jitter(birchLit, rnd, 0.14f), birchDark, rnd, 1f)
        }
        // A tall Scots pine at the left edge, its bare trunk and ragged crown framing the scene.
        run {
            val x = w * 0.075f; val th = h * 0.54f; val y = ridge.yAt(x) + th * 0.02f
            k.treeShadow(c, x, y, th)
            k.scotsPine(
                c, x, y, th, l.foliage(0xFF5A8A4C.toInt()), l.foliage(0xFF1F3B26.toInt()), l.shade(0xFF5E4130.toInt()), rnd, 1f, inward = 1,
            )
        }

        // The floor: ferns and shrubs at the foot of the trees, mossy rocks and a fallen log.
        val fernLit = l.foliage(0xFF6FA83C.toInt()); val fernDark = l.foliage(0xFF2A5420.toInt())
        if (!l.snow) {
            for (fx in floatArrayOf(0.02f, 0.1f, 0.15f, 0.24f, 0.74f, 0.88f, 0.95f, 0.99f)) {
                val x = w * fx + (rnd.nextFloat() - 0.5f) * w * 0.03f
                val size = h * (0.03f + 0.025f * rnd.nextFloat())
                k.fern(c, x, ridge.yAt(x) + h * (0.02f + 0.05f * rnd.nextFloat()), size, fernLit, fernDark, rnd)
            }
        }
        val bushLit = l.foliage(0xFF5C9440.toInt()); val bushDark = l.foliage(0xFF1F4222.toInt())
        for (fx in floatArrayOf(0.27f, 0.7f, 0.97f)) {
            val x = w * fx
            k.bush(c, x, ridge.yAt(x) + h * 0.025f, h * 0.035f, bushLit, bushDark, rnd)
        }
        val rockCol = l.ground(0xFF5A6068.toInt()); val moss = l.foliage(0xFF4F8A3A.toInt())
        k.mossyRock(c, w * 0.08f, ridge.yAt(w * 0.08f) + h * 0.065f, w * 0.05f, rockCol, moss, rnd)
        k.mossyRock(c, w * 0.93f, ridge.yAt(w * 0.93f) + h * 0.07f, w * 0.04f, rockCol, moss, rnd)
        k.fallenLog(c, w * 0.72f, ridge.yAt(w * 0.72f) + h * 0.075f, w * 0.24f, h * 0.014f, bark, moss, -3f, rnd)
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
        return listOf(
            GroundSlot(Species.GOAT, 0.15f, 0.85f, GroundY { x -> r.yAt(x) + h * 0.004f }, 1f, 2),
            GroundSlot(Species.FOX, 0.2f, 0.8f, GroundY { x -> r.yAt(x) + h * 0.01f }, 1.1f, 1),
        )
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
    private var meadowSpec: MeadowSpec? = null
    private var meadowFor = 0
    override fun meadow(): MeadowSpec {
        if (meadowSpec == null || meadowFor != width * 31 + height) {
            meadowFor = width * 31 + height
            val h = height.toFloat()
            val r = Ridge(width, h * 0.9f, h * 0.045f, false, 10)
            meadowSpec = MeadowSpec(listOf(FlowerBed(0.02f, 0.98f, GroundY { x -> r.yAt(x) }, h * 0.07f, 18)))
        }
        return meadowSpec!!
    }
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
            // Foxes and a pair of jackals work the hillside from dusk to dawn.
            GroundSlot(Species.FOX, 0.2f, 0.8f, GroundY { x -> r.yAt(x) + h * 0.055f }, 1.1f, 1),
            GroundSlot(Species.JACKAL, 0.3f, 0.7f, GroundY { x -> r.yAt(x) + h * 0.06f }, 1.1f, 2),
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
    private var meadowSpec: MeadowSpec? = null
    private var meadowFor = 0
    override fun meadow(): MeadowSpec {
        if (meadowSpec == null || meadowFor != width * 31 + height) {
            meadowFor = width * 31 + height
            val h = height.toFloat()
            val r = Ridge(width, h * 0.78f, h * 0.015f, false, 15)
            meadowSpec = MeadowSpec(listOf(FlowerBed(0.02f, 0.98f, GroundY { x -> r.yAt(x) + h * 0.04f }, h * 0.14f, 28)))
        }
        return meadowSpec!!
    }
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
            // A herd of horses (stallion, mare, foal, grey) behind the cows, and jackals after dark.
            GroundSlot(Species.HORSE, 0.3f, 0.7f, GroundY { x -> r.yAt(x) + h * 0.07f }, 1.4f, 4),
            GroundSlot(Species.JACKAL, 0.3f, 0.7f, GroundY { x -> r.yAt(x) + h * 0.075f }, 1.35f, 3),
            GroundSlot(Species.COW, 0.05f, 0.65f, GroundY { x -> r.yAt(x) + h * 0.09f }, 1.5f, 2),
        )
    }

    /** The swaying foreground: tapered blades and seed heads, plus a pale gust that rolls over the field. */
    private val blades = GrassBlades(BLADES)
    private val sheen = Paint()
    private val sheenMatrix = Matrix()
    private var sheenOn = false
    private var sheenShader: LinearGradient? = null

    override fun build() {
        blades.layout(width.toFloat(), height.toFloat(), width / 1080f)
        rebuildSheen()
    }

    override fun onLightingChanged() {
        val l = lighting
        blades.setColors(
            base = l.foliage(0xFF17391A.toInt()), lush = l.foliage(0xFF4A9A30.toInt()), dry = l.foliage(0xFF9FAA4C.toInt()),
            tip = l.ground(0xFFB5DB72.toInt()), stalk = l.foliage(0xFF7E9A44.toInt()), gold = l.foliage(0xFFD9C36E.toInt()),
            sunOnLeft = l.reflectionX < 0.5f,
        )
        rebuildSheen()
    }

    private fun rebuildSheen() {
        val l = lighting
        if (width <= 1) return
        sheenOn = !l.snow
        val col = l.shade(0xFFF2F8BE.toInt())
        sheenShader = LinearGradient(
            0f, 0f, width * 0.5f, 0f, intArrayOf(col and 0xFFFFFF, col, col and 0xFFFFFF), null, Shader.TileMode.CLAMP,
        )
        sheen.shader = sheenShader
    }

    override fun paintAll(k: PaintKit): PaintResult {
        val oak = oak(k)
        return PaintResult(arrayOf(far(k), meadow(k)), if (oak != null) mapOf("oak" to oak) else emptyMap())
    }

    /** The big oak on its own sprite (trunk foot at the bottom centre) so it can bend in the wind. */
    private fun oak(k: PaintKit): Bitmap? {
        val th = k.h * OAK_HEIGHT
        val (c, bmp) = k.newSprite((th * 1.1f).toInt(), (th * 1.08f).toInt()) ?: return null
        val l = k.l
        k.broadleaf(
            c, bmp.width / 2f, bmp.height - 2f, th, l.foliage(0xFF7CC862.toInt()), l.foliage(0xFF2A5A2A.toInt()),
            l.shade(0xFF4E3A28.toInt()), Random(33), 1f,
        )
        return bmp
    }

    private val oakPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val oakMesh = FloatArray((OAK_COLS + 1) * (OAK_ROWS + 1) * 2)

    /**
     * Draws the oak through a mesh whose vertices swing further the higher they are above the
     * trunk's foot, with a faster flutter on top, so the trunk barely moves, the limbs bend and
     * the leaves ripple. The stronger the wind, the wider and quicker the motion.
     */
    private fun drawOak(canvas: Canvas) {
        val oak = sprites["oak"] ?: return
        val t = timeMs / 1000f
        val sw = oak.width.toFloat(); val sh = oak.height.toFloat()
        val th = height * OAK_HEIGHT
        val amp = (0.006f + windKmh * 0.0016f).coerceAtMost(0.06f) * th
        val freq = 0.7f + windKmh / 25f
        val left = width * 0.84f + shift(0.6f) - sw / 2f
        val top = height * 0.8f - sh + 2f
        var n = 0
        for (r in 0..OAK_ROWS) {
            val y = sh * r / OAK_ROWS
            val f = 1f - y / sh                      // 0 at the foot, 1 at the crown top
            val f2 = f * f
            for (col in 0..OAK_COLS) {
                val x = sw * col / OAK_COLS
                val swing = sin(t * freq) * 0.65f + 0.35f
                val flutter = sin(t * freq * 2.6f + x * 0.045f + y * 0.03f)
                val dx = amp * (f2 * swing + 0.22f * f * f * flutter)
                val dy = amp * 0.1f * f2 * sin(t * freq * 2.1f + x * 0.05f)
                oakMesh[n++] = left + x + dx
                oakMesh[n++] = top + y + dy
            }
        }
        canvas.drawBitmapMesh(oak, OAK_COLS, OAK_ROWS, oakMesh, 0, null, 0, oakPaint)
    }

    private fun far(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(31)
        // Two rows of far hills, the farther one paler and patched with crops, each fading into the haze.
        val hills = Ridge(k.width, h * 0.72f, h * 0.05f, false, 12)
        k.hill(c, hills, h * 0.8f, l.distant(l.ground(0xFF8DB46E.toInt()), 0.38f), l.distant(l.ground(0xFF5E8F50.toInt()), 0.38f), rnd, 0)
        k.haze(c, h * 0.66f, h * 0.8f, 0.32f)
        val mid = Ridge(k.width, h * 0.748f, h * 0.022f, false, 19)
        k.hill(c, mid, h * 0.8f, l.distant(l.ground(0xFF7BAE57.toInt()), 0.3f), l.distant(l.ground(0xFF4E8444.toInt()), 0.3f), rnd, 0)

        // The tree line stands in clumps with gaps between. Every trunk is sunk behind the meadow's
        // edge, so only crowns show, with no stilts under them.
        val edge = groundRidge(k.width, h)
        val lit = l.distant(l.foliage(0xFF6FB85A.toInt()), 0.18f); val dark = l.distant(l.foliage(0xFF2A5A2A.toInt()), 0.18f)
        val trunk = l.shade(0xFF4A3A2A.toInt())
        var x = -k.margin
        while (x < k.w + k.margin) {
            val u = x / k.w
            val clump = (0.55f + 0.3f * sin(u * 17.7f + 0.9f) + 0.2f * sin(u * 41f + 2f)).coerceIn(0f, 1f)
            if (clump > 0.3f) {
                val th = h * (0.022f + 0.04f * clump * (0.5f + rnd.nextFloat() * 0.6f))
                k.broadleaf(c, x, edge.yAt(x) + th * 0.26f, th, k.jitter(lit, rnd, 0.2f), dark, trunk, rnd, 0.25f)
            }
            x += (14f + rnd.nextFloat() * 18f) * k.unit
        }
        k.haze(c, h * 0.7f, h * 0.8f, 0.12f)

        // A hedge of shrubs along the field's far edge, where the trees stand and the grass begins.
        val shrubDark = l.distant(l.foliage(0xFF234D24.toInt()), 0.1f); val shrubLit = l.distant(l.foliage(0xFF4E8F3A.toInt()), 0.1f)
        var sx = -k.margin
        while (sx < k.w + k.margin) {
            val r = (5f + rnd.nextFloat() * 10f) * k.unit
            val sy = edge.yAt(sx) - r * 0.15f
            k.fill.color = shrubDark
            c.drawCircle(sx, sy, r, k.fill)
            k.fill.color = k.jitter(shrubLit, rnd, 0.2f)
            c.drawCircle(sx + k.sun * r * 0.25f, sy - r * 0.3f, r * 0.6f, k.fill)
            sx += (6f + rnd.nextFloat() * 8f) * k.unit
        }
        return bmp
    }

    private fun meadow(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(32)
        val ridge = groundRidge(k.width, h)
        // The lawn: a body shaded from pale far grass to deep near grass, then big soft patches of
        // lush, sun-dried and shadowed tones, then the tufts themselves, then the flowers.
        k.hill(c, ridge, h, l.ground(0xFF8CC05A.toInt()), l.ground(0xFF2D5A22.toInt()), rnd, 0)
        k.groundPatches(
            c, ridge, h, rnd, 70,
            intArrayOf(
                l.ground(0xFFBBD36E.toInt()), l.ground(0xFFCDBB6A.toInt()), l.ground(0xFF2A5E26.toInt()),
                l.ground(0xFF3F8A4A.toInt()), l.ground(0xFF9CC85C.toInt()),
            ),
            85,
        )
        k.grassCarpet(
            c, ridge, h, rnd,
            deep = l.foliage(0xFF17391A.toInt()), lush = l.ground(0xFF58A038.toInt()), dry = l.ground(0xFFB3B457.toInt()),
            lit = l.ground(0xFFC2EC78.toInt()), mist = l.skyBottom, density = 1f,
        )
        k.wildflowers(c, ridge, h, rnd, 26)

        // The oak's shadow lies on the grass, thrown away from the sun.
        val ox = w * 0.84f - k.sun * h * OAK_HEIGHT * 0.3f
        val th = h * OAK_HEIGHT
        val gy = h * 0.8f + th * 0.03f
        c.save()
        c.translate(ox, gy)
        c.scale(1f, 0.14f)
        k.shader = RadialGradient(0f, 0f, th * 0.55f, 0x70000000, 0, Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, th * 0.55f, k.fill)
        k.shader = null
        c.restore()

        // The near edge of the field sinks into shadow.
        val dusk = h * 0.86f
        k.shader = LinearGradient(0f, dusk, 0f, h, k.alpha(l.foliage(0xFF0C220C.toInt()), 0), k.alpha(l.foliage(0xFF0C220C.toInt()), 120), Shader.TileMode.CLAMP)
        c.drawRect(-k.margin, dusk, w + k.margin, h, k.fill)
        k.shader = null
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage != layers.size) return
        val t = timeMs / 1000f
        drawOak(canvas)
        if (sheenOn) drawSheen(canvas, t)
        blades.draw(canvas, t, windKmh, shift(1f), shift(0.6f))
    }

    /**
     * A pale band that sweeps across the lawn, brighter and quicker the stronger the wind: grass
     * flipped by a gust shows its lighter underside, which is what makes a field look windswept.
     */
    private fun drawSheen(canvas: Canvas, t: Float) {
        val w = width.toFloat(); val h = height.toFloat()
        val travel = w * 1.8f
        val pos = (t * w * (0.05f + windKmh * 0.005f)) % travel - w * 0.7f
        sheenMatrix.setRotate(-18f)
        sheenMatrix.postTranslate(pos, 0f)
        sheenShader?.setLocalMatrix(sheenMatrix)
        sheen.shader = sheenShader
        sheen.alpha = (30 + windKmh * 4f).toInt().coerceIn(30, 110)
        canvas.drawRect(0f, h * 0.8f, w, h, sheen)
    }

    private companion object {
        fun groundRidge(width: Int, h: Float) = Ridge(width, h * 0.78f, h * 0.015f, false, 15)
        const val BLADES = 640
        const val OAK_HEIGHT = 0.26f
        const val OAK_COLS = 8
        const val OAK_ROWS = 12
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
            // An arctic fox: white, so it only turns up where the snow is.
            GroundSlot(Species.FOX, 0.2f, 0.8f, GroundY { x -> r.yAt(x) + h * 0.032f }, 1.1f, 1),
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
    private var meadowSpec: MeadowSpec? = null
    private var meadowFor = 0
    override fun meadow(): MeadowSpec {
        if (meadowSpec == null || meadowFor != width * 31 + height) {
            meadowFor = width * 31 + height
            val h = height.toFloat()
            val r = Ridge(width, h * 0.9f, h * 0.02f, false, 21)
            meadowSpec = MeadowSpec(listOf(FlowerBed(0.02f, 0.98f, GroundY { x -> r.yAt(x) }, h * 0.07f, 16)))
        }
        return meadowSpec!!
    }
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
            // Jackals keep to the edge of the village after dark.
            GroundSlot(Species.JACKAL, 0.3f, 0.7f, GroundY { x -> r.yAt(x) + h * 0.06f }, 1.2f, 2),
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
