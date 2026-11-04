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
}

/**
 * Draws silhouettes with a little shading, facing right, standing on the bottom edge of the
 * bitmap. Everything is drawn in a 100-unit-high box and scaled to the bitmap, so one painter
 * serves any size. [frame] 0 or 1 swaps the legs for a two-step walk.
 */
object AnimalSprites {


    /** Whether the species walks with a quadruped gait (frames 0-7 walking, 8 grazing). */
    fun isQuadruped(s: Species) = s == Species.DEER || s == Species.COW || s == Species.SHEEP ||
        s == Species.HORSE || s == Species.GOAT || s == Species.DOG

    private fun spec(s: Species): QuadSpec = when (s) {
        Species.DEER -> QuadSpec(56f, 22f, 20f, 24f, 26f, 62f, 15f, 8f, 0xFFA9784A.toInt(), 0xFFE0C9A6.toInt(), 0xFF7B5636.toInt(), 0xFF2A2018.toInt(), 8f, false, 3.2f, antlers = true, whiteTail = true)
        Species.COW -> QuadSpec(62f, 32f, 15f, 16f, 18f, 18f, 17f, 12f, 0xFFF2EEE6.toInt(), 0xFFD8D2C8.toInt(), 0xFFE6E0D4.toInt(), 0xFF3A322A.toInt(), 26f, false, 4.6f, horns = 1, patches = true, udder = true)
        Species.SHEEP -> QuadSpec(44f, 28f, 11f, 13f, 12f, 30f, 13f, 9f, 0xFFEFE9DC.toInt(), 0xFFEFE9DC.toInt(), 0xFF3A3430.toInt(), 0xFF1E1A18.toInt(), 6f, false, 3f, wool = true)
        Species.HORSE -> QuadSpec(60f, 26f, 22f, 26f, 30f, 52f, 20f, 10f, 0xFF7B4E2E.toInt(), 0xFF9A6A44.toInt(), 0xFF5A3822.toInt(), 0xFF1E140C.toInt(), 30f, false, 3.6f, mane = true)
        Species.GOAT -> QuadSpec(42f, 20f, 14f, 16f, 14f, 48f, 11f, 7f, 0xFFEAE2D2.toInt(), 0xFFD2C8B4.toInt(), 0xFF8C826E.toInt(), 0xFF2A2018.toInt(), 8f, true, 2.8f, horns = 2, beard = true)
        else -> QuadSpec(42f, 18f, 15f, 15f, 12f, 38f, 13f, 8f, 0xFF9A6B3F.toInt(), 0xFFB88C5E.toInt(), 0xFF7A5230.toInt(), 0xFF2A2018.toInt(), 16f, true, 3.2f, floppyEars = true)
    }
    fun bake(species: Species, frame: Int, heightPx: Int): Bitmap? {
        val wPx = (heightPx * species.aspect).toInt().coerceAtLeast(4)
        val bmp = runCatching { Bitmap.createBitmap(wPx, heightPx.coerceAtLeast(4), Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val c = Canvas(bmp)
        val s = heightPx / 100f
        c.scale(s, s)
        val p = Painter(c)
        when (species) {
            Species.DEER, Species.COW, Species.SHEEP, Species.HORSE, Species.GOAT, Species.DOG -> return Quadruped.bake(spec(species), frame, heightPx, species.aspect)
            Species.HARE -> p.hare(frame)
            Species.CRAB -> p.crab(frame)
            Species.DUCK -> p.duck()
            Species.DOLPHIN -> p.dolphin()
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

        fun hare(frame: Int) {
            val coat = 0xFFB9A58E.toInt()
            val stretch = if (frame == 0) 0f else 6f
            oval(28f, 52f - stretch * 0.4f, 94f, 98f, coat)
            oval(14f, 70f, 54f, 100f, shade(coat, 0.9f))
            oval(82f, 40f - stretch * 0.4f, 110f, 66f, coat)
            oval(98f, 48f - stretch * 0.4f, 116f, 62f, coat)
            line.color = coat; line.strokeWidth = 7f
            c.drawLine(94f, 44f - stretch * 0.4f, 90f, 10f, line); c.drawLine(100f, 44f - stretch * 0.4f, 104f, 12f, line)
            line.color = 0xFFE8B8B0.toInt(); line.strokeWidth = 3f
            c.drawLine(94f, 40f - stretch * 0.4f, 91f, 16f, line)
            fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(26f, 74f, 7f, fill)
            fill.color = 0xFF2A2018.toInt(); c.drawCircle(108f, 52f - stretch * 0.4f, 2f, fill)
        }

        fun crab(frame: Int) {
            val shell = 0xFFD2562E.toInt()
            line.color = shade(shell, 0.8f); line.strokeWidth = 3.4f
            for (i in 0 until 3) {
                val dy = if ((i + frame) % 2 == 0) -4f else 4f
                c.drawLine(40f + i * 10f, 70f, 28f - i * 3f, 92f + dy, line)
                c.drawLine(88f - i * 10f, 70f, 100f + i * 3f, 92f - dy, line)
            }
            oval(34f, 44f, 98f, 86f, shell)
            oval(44f, 50f, 88f, 72f, 0xFFE87A52.toInt())
            line.strokeWidth = 4.2f; line.color = shell
            c.drawLine(36f, 56f, 18f, 30f, line); c.drawLine(96f, 56f, 114f, 30f, line)
            fill.color = shell
            oval(8f, 14f, 26f, 34f, shell); oval(106f, 14f, 124f, 34f, shell)
            fill.color = 0xFF1E140C.toInt(); c.drawCircle(52f, 42f, 3f, fill); c.drawCircle(80f, 42f, 3f, fill)
        }

        fun duck() {
            // A mallard sitting on the water: only the upper body shows.
            oval(18f, 40f, 98f, 100f, 0xFF8B6B4A.toInt())
            oval(26f, 46f, 80f, 82f, 0xFFA98A66.toInt())
            path.rewind(); path.moveTo(16f, 56f); path.lineTo(2f, 42f); path.lineTo(22f, 50f); path.close()
            fill.color = 0xFF3A3A3A.toInt(); c.drawPath(path, fill)
            line.color = 0xFF2F7A4A.toInt(); line.strokeWidth = 14f
            c.drawLine(88f, 56f, 98f, 22f, line)
            oval(88f, 6f, 116f, 32f, 0xFF2F7A4A.toInt())
            oval(94f, 36f, 100f, 42f, 0xFFF2F2F2.toInt())
            oval(108f, 16f, 134f, 26f, 0xFFE8A428.toInt())
            fill.color = 0xFF111111.toInt(); c.drawCircle(104f, 16f, 2.2f, fill)
            // Water line shading to hide the lower half.
            fill.color = 0x33000000
            c.drawRect(0f, 88f, 140f, 100f, fill)
        }

        fun dolphin() {
            val body = 0xFF7F96A8.toInt()
            path.rewind()
            path.moveTo(4f, 44f)
            path.quadTo(40f, 8f, 120f, 36f)
            path.quadTo(150f, 46f, 188f, 40f)
            path.quadTo(150f, 62f, 112f, 66f)
            path.quadTo(46f, 84f, 4f, 44f)
            path.close()
            fill.color = body; c.drawPath(path, fill)
            path.rewind(); path.moveTo(24f, 60f); path.quadTo(70f, 80f, 112f, 62f); path.quadTo(60f, 70f, 24f, 60f)
            fill.color = 0xFFD8E2EA.toInt(); c.drawPath(path, fill)
            path.rewind(); path.moveTo(70f, 22f); path.lineTo(86f, -4f); path.lineTo(96f, 26f); path.close()
            fill.color = 0xFF66798A.toInt(); c.drawPath(path, fill)
            path.rewind(); path.moveTo(186f, 40f); path.lineTo(200f, 22f); path.lineTo(200f, 56f); path.close()
            c.drawPath(path, fill)
            fill.color = 0xFF111820.toInt(); c.drawCircle(46f, 36f, 2f, fill)
        }
    }
}
