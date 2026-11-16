package com.skymood.wallpaper.fauna

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

enum class Species(
    /** Height of the animal as a fraction of the screen height at scale 1. */
    val heightFraction: Float,
    /** Width over height of its sprite. */
    val aspect: Float,
) {
    DEER(0.05f, 1.2f),
    COW(0.042f, 1.25f),
    SHEEP(0.03f, 1.0f),
    HORSE(0.055f, 1.3f),
    GOAT(0.034f, 0.9f),
    HARE(0.017f, 1.3f),
    DOG(0.026f, 0.95f),
    CRAB(0.013f, 1.5f),
    DUCK(0.016f, 1.7f),
    DOLPHIN(0.032f, 2.0f),
    /** Red fox, or an arctic fox in snow (sprite variant 1). Solitary, out at dawn, dusk and night. */
    FOX(0.03f, 1.55f),
    /** Golden jackal; hunts in small packs after dark. */
    JACKAL(0.032f, 1.5f),
    /** Hops about at the water's edge in rain and at night, puffing out its throat to call. */
    FROG(0.013f, 1.3f),
    /** A gaur-like wild ox: dark, heavy shouldered, white stockings, big curved horns. */
    WILD_COW(0.054f, 1.3f),
    /**
     * A bird on foot: a herring gull (variant 0) on piers and rocks, or a smaller blue-grey pigeon
     * (variant 1) on walls and rooftops. It walks, pecks and stands about.
     */
    GULL(0.02f, 1.1f),
}

/**
 * Draws silhouettes with a little shading, facing right, standing on the bottom edge of the
 * bitmap. Everything is drawn in a 100-unit-high box and scaled to the bitmap, so one painter
 * serves any size. [frame] 0 or 1 swaps the legs for a two-step walk.
 */
object AnimalSprites {


    /** Whether the species walks with a quadruped gait (frames 0-7 walking, 8 grazing). */
    fun isQuadruped(s: Species) = s == Species.DEER || s == Species.COW || s == Species.SHEEP ||
        s == Species.HORSE || s == Species.GOAT || s == Species.DOG ||
        s == Species.FOX || s == Species.JACKAL || s == Species.WILD_COW

    /** Species that move as a group and follow a leader. */
    fun isHerd(s: Species) = s == Species.HORSE || s == Species.WILD_COW || s == Species.JACKAL

    /** Horse coats: 0 bay, 1 chestnut, 2 grey, 3 black, 4 foal. */
    const val FOAL = 4

    /**
     * A red fox: black stockings and ear backs, cream chest and a bushy white-tipped brush. The arctic
     * fox (variant 1) is all white.
     */
    private fun fox(variant: Int): QuadSpec {
        val white = variant == 1
        val coat = if (white) 0xFFF1F3F5.toInt() else 0xFFC9622A.toInt()
        val legs = if (white) 0xFFE4E8EB.toInt() else 0xFF2B1E17.toInt()
        return QuadSpec(
            58f, 28f, 14f, 15f, 14f, 26f, 25f, 13f,
            coat, if (white) 0xFFE3E7EA.toInt() else 0xFFF3E6D2.toInt(), legs, if (white) 0xFF4A4A4A.toInt() else 0xFF1A1410.toInt(),
            42f, false, 4.2f,
            bushyTail = true, tailTip = if (white) 0 else 0xFFF7F3EA.toInt(), tailDroop = 10f, tailWidth = 8.5f,
            earHeight = 15f, earTone = if (white) 0xFFDDE2E6.toInt() else 0xFF2B1E17.toInt(),
            noseColor = 0xFF15100C.toInt(), chin = if (white) 0xFFFAFBFC.toInt() else 0xFFF5EBDD.toInt(),
            headDrop = 30f, rearPad = 28f,
        )
    }

    private fun horse(variant: Int): QuadSpec = when (variant) {
        1 -> QuadSpec(60f, 26f, 22f, 26f, 30f, 52f, 20f, 10f, 0xFFA45F2B.toInt(), 0xFFB87A44.toInt(), 0xFF8A4F24.toInt(), 0xFF1E140C.toInt(), 30f, false, 3.6f, mane = true)
        2 -> QuadSpec(60f, 26f, 22f, 26f, 30f, 52f, 20f, 10f, 0xFFBFC3C8.toInt(), 0xFFD6D9DC.toInt(), 0xFFA7ABB0.toInt(), 0xFF2A2624.toInt(), 30f, false, 3.6f, mane = true)
        3 -> QuadSpec(60f, 26f, 22f, 26f, 30f, 52f, 20f, 10f, 0xFF2B2522.toInt(), 0xFF3A322E.toInt(), 0xFF1F1A18.toInt(), 0xFF120E0C.toInt(), 30f, false, 3.6f, mane = true)
        // A foal: shorter body and neck on long, spindly legs.
        FOAL -> QuadSpec(46f, 21f, 23f, 27f, 22f, 56f, 17f, 8.5f, 0xFF9A6D45.toInt(), 0xFFB78A60.toInt(), 0xFF7A5232.toInt(), 0xFF1E140C.toInt(), 22f, false, 2.9f, mane = true)
        else -> QuadSpec(60f, 26f, 22f, 26f, 30f, 52f, 20f, 10f, 0xFF7B4E2E.toInt(), 0xFF9A6A44.toInt(), 0xFF5A3822.toInt(), 0xFF1E140C.toInt(), 30f, false, 3.6f, mane = true)
    }

    private fun spec(s: Species, variant: Int): QuadSpec = when (s) {
        Species.FOX -> fox(variant)
        Species.JACKAL -> QuadSpec(
            62f, 27f, 18f, 19f, 15f, 28f, 26f, 12.5f,
            0xFFC4A06A.toInt(), 0xFFE8D9B8.toInt(), 0xFFB78E57.toInt(), 0xFF2A1E16.toInt(),
            34f, false, 4f,
            bushyTail = true, tailTip = 0xFF2A2420.toInt(), tailDroop = 38f, tailWidth = 6.5f,
            earHeight = 17f, earTone = 0xFFA87B48.toInt(), noseColor = 0xFF15100C.toInt(), chin = 0xFFEFE3C8.toInt(),
            saddle = 0xFF7A6850.toInt(), headDrop = 30f, rearPad = 26f,
        )
        Species.WILD_COW -> QuadSpec(
            66f, 34f, 17f, 19f, 17f, 14f, 19f, 13f,
            0xFF3B2A21.toInt(), 0xFF2E211A.toInt(), 0xFF3B2A21.toInt(), 0xFF1C1612.toInt(),
            28f, false, 5.2f,
            horns = 3, hump = true, socks = 0xFFE3D8BE.toInt(),
        )
        Species.HORSE -> horse(variant)
        Species.DEER -> QuadSpec(56f, 22f, 20f, 24f, 26f, 62f, 15f, 8f, 0xFFA9784A.toInt(), 0xFFE0C9A6.toInt(), 0xFF7B5636.toInt(), 0xFF2A2018.toInt(), 8f, false, 3.2f, antlers = true, whiteTail = true)
        Species.COW -> QuadSpec(62f, 32f, 15f, 16f, 18f, 18f, 17f, 12f, 0xFFF2EEE6.toInt(), 0xFFD8D2C8.toInt(), 0xFFE6E0D4.toInt(), 0xFF3A322A.toInt(), 26f, false, 4.6f, horns = 1, patches = true, udder = true)
        Species.SHEEP -> QuadSpec(44f, 28f, 11f, 13f, 12f, 30f, 13f, 9f, 0xFFEFE9DC.toInt(), 0xFFEFE9DC.toInt(), 0xFF3A3430.toInt(), 0xFF1E1A18.toInt(), 6f, false, 3f, wool = true)
        Species.GOAT -> QuadSpec(42f, 20f, 14f, 16f, 14f, 48f, 11f, 7f, 0xFFEAE2D2.toInt(), 0xFFD2C8B4.toInt(), 0xFF8C826E.toInt(), 0xFF2A2018.toInt(), 8f, true, 2.8f, horns = 2, beard = true)
        else -> QuadSpec(42f, 18f, 15f, 15f, 12f, 38f, 13f, 8f, 0xFF9A6B3F.toInt(), 0xFFB88C5E.toInt(), 0xFF7A5230.toInt(), 0xFF2A2018.toInt(), 16f, true, 3.2f, floppyEars = true)
    }
    /** [variant] picks a coat: horse colours and foal, red or arctic fox. Other species ignore it. */
    fun bake(species: Species, frame: Int, heightPx: Int, variant: Int = 0): Bitmap? {
        val wPx = (heightPx * species.aspect).toInt().coerceAtLeast(4)
        val bmp = runCatching { Bitmap.createBitmap(wPx, heightPx.coerceAtLeast(4), Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val c = Canvas(bmp)
        val s = heightPx / 100f
        c.scale(s, s)
        val p = Painter(c)
        when (species) {
            Species.DEER, Species.COW, Species.SHEEP, Species.HORSE, Species.GOAT, Species.DOG,
            Species.FOX, Species.JACKAL, Species.WILD_COW -> {
                bmp.recycle()
                return Quadruped.bake(spec(species, variant), frame, heightPx, species.aspect)
            }
            Species.FROG -> p.frog(frame)
            Species.HARE -> p.hare(frame)
            Species.CRAB -> p.crab(frame)
            Species.DUCK -> p.duck(frame, variant)
            Species.GULL -> p.groundBird(frame, variant)
            Species.DOLPHIN -> p.dolphin(frame)
        }
        return bmp
    }

    private class Painter(val c: Canvas) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
        val path = Path()
        val r = RectF()

        fun oval(l: Float, t: Float, rr: Float, b: Float, color: Int) {
            fill.color = color; r.set(l, t, rr, b); c.drawOval(r, fill)
        }

        fun leg(x0: Float, y0: Float, x1: Float, y1: Float, w: Float, color: Int) {
            line.color = color; line.strokeWidth = w; c.drawLine(x0, y0, x1, y1, line)
        }

        /** Four legs: front pair near x=fx, back pair near x=bx, swapped between frames. */
        fun legs(bodyY: Float, fx: Float, bx: Float, w: Float, color: Int, frame: Int, stride: Float = 9f) {
            val a = if (frame == 0) stride else -stride
            leg(fx, bodyY, fx + a, 100f, w, color)
            leg(fx - 6f, bodyY, fx - 6f - a, 100f, w, shade(color, 0.75f))
            leg(bx, bodyY, bx - a, 100f, w, color)
            leg(bx + 6f, bodyY, bx + 6f + a, 100f, w, shade(color, 0.75f))
        }

        fun shade(c: Int, k: Float) = Color.rgb(
            (Color.red(c) * k).toInt(), (Color.green(c) * k).toInt(), (Color.blue(c) * k).toInt(),
        )

        /**
         * A tree frog facing right. Frame 0 sits, 1 sits with the throat sac blown up to call, 2 is
         * mid-hop with the back legs stretched out behind and the front legs reaching ahead.
         */
        fun frog(frame: Int) {
            val skin = 0xFF5B9A3E.toInt()
            val deep = 0xFF3F7A2C.toInt()
            val pale = 0xFFD9E69C.toInt()
            val leap = frame == 2
            if (leap) {
                // Far hind leg, then near hind leg, stretched out behind; both front legs reach forward.
                leg(40f, 72f, 14f, 88f, 7f, shade(skin, 0.75f)); leg(14f, 88f, 2f, 97f, 3.5f, shade(skin, 0.75f))
                leg(44f, 70f, 18f, 84f, 9f, skin); leg(18f, 84f, 3f, 94f, 4.5f, shade(skin, 0.9f))
                leg(86f, 68f, 108f, 80f, 6f, shade(skin, 0.75f)); leg(108f, 80f, 119f, 78f, 3.5f, shade(skin, 0.75f))
                c.save(); c.rotate(-16f, 60f, 66f)
            } else {
                // Far hind foot and front leg peek out behind the body.
                leg(70f, 96f, 94f, 98f, 4.5f, shade(skin, 0.7f))
                leg(80f, 74f, 86f, 98f, 6f, shade(skin, 0.75f))
            }
            // Body and head.
            oval(16f, 42f, 100f, 97f, skin)
            oval(66f, 38f, 114f, 84f, skin)
            oval(32f, 70f, 98f, 97f, pale)
            fill.color = deep
            c.drawCircle(40f, 54f, 6f, fill); c.drawCircle(58f, 49f, 5f, fill); c.drawCircle(50f, 66f, 4f, fill); c.drawCircle(26f, 66f, 3.5f, fill)
            // A pale stripe along the flank.
            line.color = shade(pale, 0.95f); line.strokeWidth = 2.4f
            c.drawLine(28f, 62f, 76f, 56f, line)
            // Bulging eye with a gold iris.
            oval(84f, 24f, 108f, 48f, skin)
            fill.color = 0xFFE8C040.toInt(); c.drawCircle(97f, 35f, 7.2f, fill)
            fill.color = 0xFF14100A.toInt(); c.drawCircle(98f, 35f, 3.6f, fill)
            fill.color = 0xAAFFFFFF.toInt(); c.drawCircle(95.5f, 32.5f, 1.4f, fill)
            // Nostril and the line of the mouth.
            fill.color = 0xFF1E3A14.toInt(); c.drawCircle(111f, 54f, 1.3f, fill)
            line.color = 0xFF2A4A1C.toInt(); line.strokeWidth = 1.8f
            c.drawLine(113f, 66f, 80f, 70f, line)
            if (frame == 1) {
                // Throat sac blown up under the chin.
                oval(88f, 62f, 124f, 96f, 0xFFE6EAB2.toInt())
                oval(94f, 68f, 118f, 90f, 0xFFF3F5D2.toInt())
                line.color = 0xFF2A4A1C.toInt(); line.strokeWidth = 1.8f
                c.drawLine(113f, 66f, 84f, 70f, line)
            }
            if (!leap) {
                // Near hind leg folded up: a big thigh, the shin tucked along the ground, a long webbed foot.
                oval(14f, 54f, 62f, 98f, shade(skin, 0.92f))
                leg(24f, 90f, 66f, 97f, 8f, skin)
                leg(66f, 97f, 96f, 99f, 4.5f, shade(skin, 0.9f))
                leg(80f, 76f, 88f, 98f, 7f, skin)
                leg(88f, 98f, 102f, 99f, 4f, skin)
            } else {
                c.restore()
            }
        }

        private fun rotOval(cx: Float, cy: Float, rx: Float, ry: Float, deg: Float, color: Int) {
            fill.color = color
            c.save(); c.translate(cx, cy); c.rotate(deg)
            r.set(-rx, -ry, rx, ry); c.drawOval(r, fill)
            c.restore()
        }

        /** A long hare ear from its base to its tip, bowed by [bow], with a pink inner and a dark tip. */
        private fun hareEar(bx: Float, by: Float, tx: Float, ty: Float, bow: Float, wid: Float, coat: Int) {
            val mx = (bx + tx) / 2f; val my = (by + ty) / 2f
            val dx = tx - bx; val dy = ty - by
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val nx = -dy / len; val ny = dx / len
            path.rewind()
            path.moveTo(bx - nx * wid * 0.5f, by - ny * wid * 0.5f)
            path.quadTo(mx - nx * wid + nx * bow, my - ny * wid + ny * bow, tx, ty)
            path.quadTo(mx + nx * wid + nx * bow, my + ny * wid + ny * bow, bx + nx * wid * 0.5f, by + ny * wid * 0.5f)
            path.close()
            fill.color = coat; c.drawPath(path, fill)
            line.color = 0xFFD9A79C.toInt(); line.strokeWidth = wid * 0.38f
            c.drawLine(bx + (tx - bx) * 0.12f + nx * bow * 0.4f, by + (ty - by) * 0.12f + ny * bow * 0.4f,
                bx + (tx - bx) * 0.74f + nx * bow * 0.7f, by + (ty - by) * 0.74f + ny * bow * 0.7f, line)
            line.color = 0xFF2A2018.toInt(); line.strokeWidth = wid * 0.42f
            c.drawLine(bx + (tx - bx) * 0.86f + nx * bow * 0.4f, by + (ty - by) * 0.86f + ny * bow * 0.4f, tx, ty, line)
        }

        /**
         * A brown hare facing right. Frame 0 sits up with the ears raised, 1 nibbles with its head low,
         * 2 is mid-leap, stretched out with the ears laid back, and 3 is the crouch it lands in.
         */
        fun hare(frame: Int) {
            val coat = 0xFFB59C7A.toInt()
            val back = 0xFF8C7352.toInt()
            val light = 0xFFE8DCC6.toInt()
            val farLeg = shade(coat, 0.72f)
            fun eye(x: Float, y: Float) {
                fill.color = 0x66F2E8D4; c.drawCircle(x, y, 4.6f, fill)
                fill.color = 0xFF1E1610.toInt(); c.drawCircle(x, y, 2.5f, fill)
                fill.color = 0xCCFFFFFF.toInt(); c.drawCircle(x + 0.8f, y - 0.9f, 0.8f, fill)
            }
            fun tail(x: Float, y: Float) {
                rotOval(x, y, 6.5f, 6f, 0f, light)
                rotOval(x, y - 2.6f, 6f, 3.4f, -10f, 0xFF3A3028.toInt())
            }
            when (frame) {
                0 -> {
                    hareEar(86f, 33f, 80f, 1f, -3f, 8f, shade(coat, 0.8f))
                    leg(84f, 70f, 86f, 97f, 5f, farLeg)
                    rotOval(38f, 74f, 26f, 25f, 0f, shade(coat, 0.92f))
                    rotOval(66f, 63f, 27f, 23f, -22f, coat)
                    rotOval(52f, 50f, 24f, 8f, -14f, back)
                    rotOval(80f, 74f, 9f, 17f, 0f, light)
                    leg(30f, 98.2f, 66f, 98.2f, 6f, shade(coat, 0.85f))
                    leg(86f, 74f, 89f, 97.5f, 5.6f, coat)
                    rotOval(91f, 98.2f, 6.4f, 2.4f, 0f, shade(coat, 0.9f))
                    rotOval(86f, 51f, 12f, 14f, 10f, coat)
                    rotOval(94f, 40f, 15f, 11f, 12f, coat)
                    rotOval(105f, 45f, 8f, 6.2f, 14f, shade(coat, 1.05f))
                    fill.color = 0xFF3A2A22.toInt(); c.drawCircle(111f, 44f, 2.1f, fill)
                    eye(100f, 37.5f)
                    hareEar(95f, 32f, 99f, 2f, 3f, 8.6f, coat)
                    tail(13f, 66f)
                }
                1 -> {
                    hareEar(90f, 74f, 64f, 56f, 3f, 7f, shade(coat, 0.8f))
                    leg(80f, 78f, 82f, 97f, 5f, farLeg)
                    rotOval(38f, 76f, 26f, 23f, 0f, shade(coat, 0.92f))
                    rotOval(64f, 70f, 28f, 21f, 12f, coat)
                    rotOval(56f, 55f, 24f, 7f, 8f, back)
                    leg(30f, 98.2f, 66f, 98.2f, 6f, shade(coat, 0.85f))
                    leg(84f, 80f, 90f, 97.5f, 5.6f, coat)
                    rotOval(92f, 98.2f, 6.4f, 2.4f, 0f, shade(coat, 0.9f))
                    rotOval(88f, 74f, 14f, 12f, 30f, coat)
                    rotOval(99f, 83f, 14f, 10f, 28f, coat)
                    rotOval(110f, 91f, 7.5f, 5.6f, 24f, shade(coat, 1.05f))
                    fill.color = 0xFF3A2A22.toInt(); c.drawCircle(115.5f, 93f, 2f, fill)
                    eye(100f, 78f)
                    hareEar(94f, 73f, 68f, 59f, -3f, 8.4f, coat)
                    tail(13f, 68f)
                }
                2 -> {
                    // Far limbs, then the long stretched body.
                    leg(34f, 58f, 12f, 76f, 7f, farLeg); leg(12f, 76f, 3f, 90f, 4.2f, farLeg)
                    leg(98f, 60f, 117f, 68f, 4.4f, farLeg)
                    rotOval(64f, 56f, 46f, 15f, -4f, coat)
                    rotOval(70f, 46f, 36f, 6f, -4f, back)
                    rotOval(66f, 66f, 30f, 5f, -4f, light)
                    rotOval(34f, 60f, 18f, 13f, 20f, shade(coat, 0.92f))
                    leg(38f, 62f, 14f, 80f, 8f, shade(coat, 0.95f)); leg(14f, 80f, 5f, 93f, 4.6f, coat)
                    leg(92f, 62f, 114f, 72f, 5f, coat); leg(114f, 72f, 122f, 74f, 3.4f, coat)
                    rotOval(106f, 50f, 14f, 10f, 18f, coat)
                    rotOval(117f, 55f, 8f, 5.6f, 22f, shade(coat, 1.05f))
                    fill.color = 0xFF3A2A22.toInt(); c.drawCircle(123f, 56.5f, 2f, fill)
                    eye(110f, 47f)
                    hareEar(104f, 44f, 62f, 36f, -4f, 8f, coat)
                    tail(19f, 52f)
                }
                else -> {
                    hareEar(90f, 63f, 66f, 49f, 2f, 7f, shade(coat, 0.8f))
                    leg(82f, 84f, 90f, 97f, 5f, farLeg)
                    rotOval(40f, 80f, 28f, 20f, 0f, shade(coat, 0.92f))
                    rotOval(68f, 75f, 28f, 18f, 4f, coat)
                    rotOval(60f, 62f, 24f, 6f, 6f, back)
                    leg(26f, 98.2f, 68f, 98.2f, 6f, shade(coat, 0.85f))
                    leg(88f, 84f, 96f, 97.5f, 5.4f, coat)
                    rotOval(98f, 98.2f, 6.4f, 2.4f, 0f, shade(coat, 0.9f))
                    rotOval(98f, 68f, 14f, 10f, 14f, coat)
                    rotOval(109f, 73f, 8f, 5.6f, 18f, shade(coat, 1.05f))
                    fill.color = 0xFF3A2A22.toInt(); c.drawCircle(115f, 74.5f, 2f, fill)
                    eye(103f, 65f)
                    hareEar(94f, 62f, 70f, 46f, -3f, 8.2f, coat)
                    tail(13f, 74f)
                }
            }
        }

        /**
         * A shore crab seen head on: eye stalks, a domed carapace, four jointed legs a side and two
         * pincers. Frames 0 and 1 scuttle (alternate legs lift), 2 and 3 wave the right or the left
         * claw high, open.
         */
        fun crab(frame: Int) {
            val shell = 0xFFC9482B.toInt()
            val dark = shade(shell, 0.7f)
            val light = 0xFFE5704A.toInt()
            // Legs: each is two segments, knee up and out, foot on the ground; alternate legs lift when scuttling.
            line.strokeWidth = 3.4f
            for (side in 0..1) {
                val s = if (side == 0) -1f else 1f
                for (i in 0 until 4) {
                    val lift = if (frame < 2 && (i + frame + side) % 2 == 0) -5f else 0f
                    val bx = 75f + s * (20f + i * 5f)
                    val by = 76f + i * 2f
                    val kx = bx + s * (15f + i * 2f)
                    val ky = 74f + lift
                    val fx = kx + s * (7f - i)
                    val fy = 98f + lift * 0.5f
                    line.color = dark
                    c.drawLine(bx, by, kx, ky, line); c.drawLine(kx, ky, fx, fy, line)
                }
            }
            // The two claws: held out at the sides, or one raised and open.
            claw(24f, 46f, -1f, 8f, shell, dark, 0f)
            claw(126f, 46f, 1f, 8f, shell, dark, 0f)
            if (frame == 2) claw(130f, 20f, 1f, 22f, shell, dark, 1f)
            if (frame == 3) claw(20f, 20f, -1f, 22f, shell, dark, 1f)
            // Carapace: a wide dome, lighter on top, with a dark rim.
            oval(38f, 54f, 112f, 92f, dark)
            oval(40f, 52f, 110f, 88f, shell)
            oval(50f, 56f, 100f, 76f, light)
            // Eye stalks and eyes.
            line.color = shell; line.strokeWidth = 3.2f
            c.drawLine(62f, 56f, 59f, 44f, line); c.drawLine(88f, 56f, 91f, 44f, line)
            fill.color = 0xFF14100C.toInt(); c.drawCircle(59f, 42f, 3.6f, fill); c.drawCircle(91f, 42f, 3.6f, fill)
            fill.color = 0xCCFFFFFF.toInt(); c.drawCircle(58f, 40.8f, 1f, fill); c.drawCircle(90f, 40.8f, 1f, fill)
        }

        /** An arm from the body to a pincer at ([cx], [cy]): a palm with two prongs opened by [open] degrees. */
        private fun claw(cx: Float, cy: Float, side: Float, open: Float, shell: Int, dark: Int, raised: Float) {
            // Arm back to the shoulder.
            line.color = shell; line.strokeWidth = 5.2f
            c.drawLine(75f + side * 28f, 72f, cx - side * 2f, cy + 8f, line)
            fill.color = shell; c.drawCircle(cx - side * 2f, cy + 8f, 4f, fill)
            // Palm.
            oval(cx - 9.5f, cy - 6f, cx + 9.5f, cy + 8f, shell)
            // Two prongs, pivoting at the top of the palm; opening more when raised.
            for (p in 0..1) {
                val a = (if (p == 0) -1f else 1f) * open + side * 6f * (1f - raised)
                c.save(); c.translate(cx, cy - 3f); c.rotate(a)
                oval(-3.2f, -17f, 3.2f, 0f, if (p == 0) shell else dark)
                fill.color = shade(shell, 0.55f); c.drawCircle(0f, -16f, 1.6f, fill)
                c.restore()
            }
        }

        /**
         * A gull (variant 0) or pigeon (1) standing facing right. Frame 0 stands, 1 takes a step with the
         * legs apart, 2 pecks at the ground with the head down.
         */
        fun groundBird(frame: Int, variant: Int) {
            val pigeon = variant == 1
            val leg = if (pigeon) 0xFFC8506A.toInt() else 0xFFD9A06A.toInt()
            val body = if (pigeon) 0xFF8D96A6.toInt() else 0xFFF4F4F1.toInt()
            val back = if (pigeon) 0xFF6F7888.toInt() else 0xFF9FAAB8.toInt()
            val peck = frame == 2
            // Legs first, so the body overlaps their tops.
            line.color = leg; line.strokeWidth = 3.2f
            val stepOut = if (frame == 1) 9f else 0f
            c.drawLine(50f, 70f, 50f - stepOut, 98f, line); c.drawLine(50f - stepOut, 98f, 44f - stepOut, 98.6f, line)
            line.color = shade(leg, 0.82f)
            c.drawLine(60f, 70f, 61f + stepOut, 98f, line); c.drawLine(61f + stepOut, 98f, 67f + stepOut, 98.6f, line)
            c.save()
            if (peck) c.rotate(14f, 52f, 72f)
            // Tail, body and folded wing.
            path.rewind(); path.moveTo(30f, 56f); path.lineTo(5f, 66f); path.lineTo(6f, 70f); path.lineTo(32f, 68f); path.close()
            fill.color = if (pigeon) 0xFF3E4452.toInt() else body; c.drawPath(path, fill)
            rotOval(52f, 57f, 29f, 17f, -9f, body)
            rotOval(46f, 52f, 26f, 11f, -9f, back)
            if (pigeon) {
                fill.color = 0xFF1E2128.toInt(); c.drawRect(36f, 49f, 38.4f, 60f, fill); c.drawRect(44f, 48f, 46.4f, 59f, fill)
            } else {
                // Black wingtips and white spots, showing past the tail.
                path.rewind(); path.moveTo(26f, 60f); path.lineTo(6f, 66f); path.lineTo(28f, 66f); path.close()
                fill.color = 0xFF2A2E36.toInt(); c.drawPath(path, fill)
            }
            // Neck and head: up when standing, forward and down when pecking.
            val hx = if (peck) 92f else 80f
            val hy = if (peck) 66f else 33f
            line.color = body; line.strokeWidth = if (pigeon) 11f else 10f
            c.drawLine(70f, 52f, hx - 3f, hy + 3f, line)
            if (pigeon) {
                fill.color = 0xFF3F8A6C.toInt(); r.set(66f, 44f, 80f, 56f); c.drawOval(r, fill)
                fill.color = 0xFF8A5A9A.toInt(); r.set(70f, 48f, 82f, 55f); c.drawOval(r, fill)
            }
            val hr = if (pigeon) 8f else 9.5f
            fill.color = if (pigeon) 0xFF6A7382.toInt() else body
            c.drawCircle(hx, hy, hr, fill)
            // Bill: the gull's is yellow with a red spot, the pigeon's short and dark with a pale cere.
            path.rewind()
            if (pigeon) {
                path.moveTo(hx + 5f, hy - 2.5f); path.lineTo(hx + 15f, hy + 1f); path.lineTo(hx + 5f, hy + 3f); path.close()
                fill.color = 0xFF3A3A40.toInt(); c.drawPath(path, fill)
                fill.color = 0xFFF2F0EA.toInt(); c.drawCircle(hx + 6.2f, hy - 1.6f, 1.7f, fill)
            } else {
                path.moveTo(hx + 6f, hy - 3f); path.lineTo(hx + 24f, hy + 2.5f); path.lineTo(hx + 22f, hy + 5f); path.lineTo(hx + 6f, hy + 4f); path.close()
                fill.color = 0xFFE8B020.toInt(); c.drawPath(path, fill)
                fill.color = 0xFFD2432A.toInt(); c.drawCircle(hx + 19f, hy + 4f, 1.5f, fill)
            }
            fill.color = if (pigeon) 0xFFE8903A.toInt() else 0xFFF1D060.toInt(); c.drawCircle(hx + 1.8f, hy - 1.8f, 3f, fill)
            fill.color = 0xFF14100C.toInt(); c.drawCircle(hx + 2f, hy - 1.8f, 1.5f, fill)
            c.restore()
        }

        /** The speckled body of a duckling: yellow down, a dark eye stripe, a tiny orange bill. */
        private fun duckling() {
            oval(36f, 54f, 108f, 98f, 0xFFE8C964.toInt())
            oval(44f, 60f, 96f, 92f, 0xFFF1DB86.toInt())
            oval(30f, 64f, 48f, 84f, 0xFF7A5A30.toInt())
            oval(88f, 46f, 118f, 82f, 0xFFE8C964.toInt())
            oval(102f, 32f, 132f, 64f, 0xFFEFD27A.toInt())
            oval(106f, 39f, 124f, 49f, 0xFF4E3820.toInt())
            oval(124f, 46f, 139f, 52f, 0xFFD98A2C.toInt())
            fill.color = 0xFF14100C.toInt(); c.drawCircle(116f, 43f, 2.2f, fill)
            fill.color = 0x33000000; c.drawRect(0f, 90f, 160f, 100f, fill)
        }

        /**
         * A mallard afloat, facing right; [variant] 0 is the drake (green head, white collar, chestnut breast),
         * 1 the brown hen, 2 a duckling. Frame 0 swims with the neck up, 1 dabbles tail up with the head under
         * water, 2 preens with the bill tucked into the wing.
         */
        fun duck(frame: Int, variant: Int) {
            if (variant == 2) { duckling(); return }
            val hen = variant == 1
            val flank = if (hen) 0xFFA88B66.toInt() else 0xFFB9B5AD.toInt()
            val back = if (hen) 0xFF7E6445.toInt() else 0xFF7A736A.toInt()
            val breast = if (hen) 0xFFA88B66.toInt() else 0xFF6A3A26.toInt()
            val headCol = if (hen) 0xFF8E7352.toInt() else 0xFF1F7A43.toInt()
            val bill = if (hen) 0xFFD08A2C.toInt() else 0xFFE2B52C.toInt()
            if (frame == 1) {
                // Bottoms up: the whole bird tips forward about its chest so the tail points to the sky.
                c.save(); c.scale(0.88f, 0.88f, 96f, 92f); c.rotate(35f, 96f, 92f)
                duckBody(flank, back, breast, headCol, bill, hen, 1)
                c.restore()
            } else {
                duckBody(flank, back, breast, headCol, bill, hen, if (frame == 2) 2 else 0)
            }
            // Where the water covers the lower hull.
            fill.color = 0x33000000
            c.drawRect(0f, 90f, 170f, 100f, fill)
        }

        private fun duckBody(flank: Int, back: Int, breast: Int, headCol: Int, bill: Int, hen: Boolean, pose: Int) {
            // Tail: up-cocked black curl on the drake, buff feathers on the hen.
            path.rewind(); path.moveTo(24f, 66f); path.lineTo(7f, 47f); path.quadTo(22f, 52f, 34f, 58f); path.close()
            fill.color = if (hen) 0xFF8A6E4C.toInt() else 0xFF22201E.toInt(); c.drawPath(path, fill)
            // Hull: a pointed stern, a deep rounded breast, the waterline flat along the bottom.
            path.rewind(); path.moveTo(14f, 62f)
            path.cubicTo(30f, 50f, 72f, 51f, 98f, 55f)
            path.cubicTo(113f, 59f, 114f, 80f, 100f, 92f)
            path.lineTo(26f, 92f)
            path.cubicTo(14f, 86f, 10f, 70f, 14f, 62f); path.close()
            fill.color = flank; c.drawPath(path, fill)
            c.save(); c.clipPath(path)
            // Darker back, and the breast colour over the chest.
            oval(10f, 44f, 100f, 66f, back)
            oval(80f, 52f, 118f, 100f, breast)
            if (hen) {
                fill.color = 0x553E2C18
                for (i in 0 until 9) c.drawCircle(24f + (i % 5) * 14f + (i / 5) * 6f, 66f + (i / 5) * 10f + (i % 2) * 4f, 3.2f, fill)
            }
            // A soft shadow under the belly.
            oval(20f, 78f, 100f, 100f, 0x22000000)
            c.restore()
            // Folded wing with its blue speculum bordered in white.
            c.save(); c.rotate(-5f, 62f, 70f)
            oval(34f, 58f, 94f, 82f, if (hen) 0xFF957850.toInt() else 0xFFA29A8F.toInt())
            fill.color = 0xFF2C4FA8.toInt(); c.drawRect(44f, 70f, 64f, 76f, fill)
            fill.color = 0xFFF4F1EA.toInt(); c.drawRect(44f, 69f, 64f, 70.6f, fill); c.drawRect(44f, 76f, 64f, 77.4f, fill)
            c.restore()
            // The head and neck.
            val hx: Float; val hy: Float
            line.color = if (hen) 0xFF8E7352.toInt() else headCol
            line.strokeWidth = 11f
            path.rewind()
            if (pose == 2) {
                // Preening: the neck curves back over the shoulder and the bill reaches into the wing.
                path.moveTo(96f, 66f); path.cubicTo(112f, 52f, 108f, 34f, 90f, 38f)
                hx = 86f; hy = 41f
            } else if (pose == 1) {
                // Dabbling: the neck reaches forward and down, the bill pointing at the bottom.
                path.moveTo(96f, 64f); path.quadTo(114f, 54f, 122f, 70f)
                hx = 124f; hy = 73f
            } else {
                path.moveTo(95f, 66f); path.quadTo(96f, 46f, 106f, 34f)
                hx = 109f; hy = 29f
            }
            c.drawPath(path, line)
            if (!hen) {
                // White collar between the green neck and the chestnut breast.
                fill.color = 0xFFF2F0EA.toInt(); c.save(); c.rotate(-8f, 94f, 62f); r.set(88f, 59f, 103f, 63.5f); c.drawOval(r, fill); c.restore()
            }
            c.save()
            c.translate(hx, hy)
            if (pose == 2) { c.scale(-1f, 1f); c.rotate(28f) } else if (pose == 1) c.rotate(78f) else c.rotate(6f)
            oval(-10f, -10f, 11f, 10f, headCol)
            if (!hen) oval(-7f, -8f, 3f, -1f, 0x33FFFFFF) else { fill.color = 0xFF4A3922.toInt(); c.drawRect(-2f, -3.2f, 10f, -1.6f, fill) }
            // Bill: broad and flat, a dark nail at the tip (and a dark saddle on the hen's).
            path.rewind(); path.moveTo(8f, -4f); path.quadTo(22f, -4f, 32f, 0.5f); path.quadTo(33f, 4f, 28f, 5f); path.quadTo(16f, 6f, 8f, 5f); path.close()
            fill.color = bill; c.drawPath(path, fill)
            if (hen) { fill.color = 0xFF4A3922.toInt(); c.drawRect(12f, -3.6f, 24f, -1.2f, fill) }
            fill.color = 0xFF3A2E18.toInt(); c.drawCircle(30.5f, 1.8f, 1.5f, fill)
            fill.color = 0xFF14100C.toInt(); c.drawCircle(3.2f, -2.4f, 1.8f, fill)
            fill.color = 0xCCFFFFFF.toInt(); c.drawCircle(3.9f, -3f, 0.6f, fill)
            c.restore()
        }

        fun dolphin(frame: Int = 0) {
            val body = 0xFF7F96A8.toInt()
            val belly = 0xFFD8E2EA.toInt()
            val finDark = 0xFF66798A.toInt()

            val noseY = when (frame) {
                1 -> 58f
                2 -> 52f
                else -> 46f
            }
            val backMidY = when (frame) {
                1 -> 16f
                2 -> 22f
                else -> 24f
            }
            val tailY = when (frame) {
                1 -> 64f
                2 -> 42f
                else -> 50f
            }

            // Dorsal Fin coordinates with base extending DEEP (+12px) inside body
            val finCoords = when (frame) {
                1 -> floatArrayOf(122f, 35f, 94f, 4f, 94f, 38f)
                2 -> floatArrayOf(124f, 37f, 102f, 8f, 98f, 39f)
                else -> floatArrayOf(126f, 38f, 106f, 8f, 98f, 40f)
            }

            // 1. Draw Dorsal Fin embedded deep into back
            path.rewind()
            path.moveTo(finCoords[0], finCoords[1])
            path.lineTo(finCoords[2], finCoords[3])
            path.lineTo(finCoords[4], finCoords[5])
            path.close()
            fill.color = body; c.drawPath(path, fill)
            fill.color = finDark; c.drawPath(path, fill)

            // 2. Draw Main Body Path seamlessly over fin base
            path.rewind()
            path.moveTo(190f, noseY)
            path.quadTo(150f, backMidY, 80f, 42f)
            path.quadTo(50f, 52f, 22f, tailY)
            path.lineTo(10f, tailY - 14f)
            path.lineTo(16f, tailY)
            path.lineTo(10f, tailY + 14f)
            path.lineTo(22f, tailY + 6f)
            path.quadTo(50f, tailY + 10f, 85f, 72f)
            path.quadTo(150f, noseY + 32f, 190f, noseY)
            path.close()
            fill.color = body; c.drawPath(path, fill)

            // 3. Draw Lighter Countershaded Belly
            path.rewind()
            path.moveTo(175f, noseY + 6f)
            path.quadTo(130f, 78f, 85f, 68f)
            path.quadTo(135f, 66f, 175f, noseY + 6f)
            path.close()
            fill.color = belly; c.drawPath(path, fill)

            // 4. Draw Dorsal Fin Front Surface
            path.rewind()
            path.moveTo(finCoords[0] - 2f, finCoords[1] - 8f)
            path.lineTo(finCoords[2], finCoords[3])
            path.lineTo(finCoords[4] + 4f, finCoords[5] - 6f)
            path.close()
            fill.color = finDark; c.drawPath(path, fill)

            // 5. Pectoral Fin on side
            path.rewind()
            path.moveTo(142f, noseY + 10f)
            path.lineTo(122f, noseY + 30f)
            path.lineTo(128f, noseY + 8f)
            path.close()
            fill.color = finDark; c.drawPath(path, fill)

            // 6. Eye
            fill.color = 0xFF111820.toInt(); c.drawCircle(162f, noseY - 6f, 2.2f, fill)
        }
    }
}
