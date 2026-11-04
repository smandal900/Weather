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
    DEER(0.05f, 1.5f),
    COW(0.042f, 1.45f),
    SHEEP(0.03f, 1.15f),
    HORSE(0.055f, 1.5f),
    GOAT(0.034f, 1.3f),
    HARE(0.017f, 1.3f),
    DOG(0.026f, 1.4f),
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

    fun bake(species: Species, frame: Int, heightPx: Int): Bitmap? {
        val wPx = (heightPx * species.aspect).toInt().coerceAtLeast(4)
        val bmp = runCatching { Bitmap.createBitmap(wPx, heightPx.coerceAtLeast(4), Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val c = Canvas(bmp)
        val s = heightPx / 100f
        c.scale(s, s)
        val p = Painter(c)
        when (species) {
            Species.DEER -> p.deer(frame)
            Species.COW -> p.cow(frame)
            Species.SHEEP -> p.sheep(frame)
            Species.HORSE -> p.horse(frame)
            Species.GOAT -> p.goat(frame)
            Species.HARE -> p.hare(frame)
            Species.DOG -> p.dog(frame)
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

        fun deer(frame: Int) {
            val coat = 0xFFA9784A.toInt()
            legs(60f, 98f, 48f, 3.6f, 0xFF6B4A2E.toInt(), frame)
            oval(32f, 36f, 106f, 68f, coat)
            oval(40f, 54f, 98f, 70f, 0xFFD8BC94.toInt())
            // Neck and head.
            path.rewind(); path.moveTo(96f, 46f); path.lineTo(112f, 18f); path.lineTo(122f, 22f); path.lineTo(108f, 58f); path.close()
            fill.color = coat; c.drawPath(path, fill)
            oval(112f, 12f, 138f, 28f, coat)
            oval(128f, 17f, 142f, 26f, 0xFFD8BC94.toInt())
            fill.color = 0xFF2A2018.toInt(); c.drawCircle(139f, 20f, 1.6f, fill)
            path.rewind(); path.moveTo(114f, 14f); path.lineTo(110f, 4f); path.lineTo(120f, 12f); path.close()
            fill.color = coat; c.drawPath(path, fill)
            // Antlers.
            line.color = 0xFF8A6A4A.toInt(); line.strokeWidth = 1.8f
            c.drawLine(120f, 12f, 116f, -2f, line); c.drawLine(118f, 6f, 126f, 0f, line); c.drawLine(116f, -2f, 122f, -6f, line)
            // Tail.
            path.rewind(); path.moveTo(34f, 40f); path.lineTo(26f, 36f); path.lineTo(34f, 48f); path.close()
            fill.color = 0xFFF2E8D8.toInt(); c.drawPath(path, fill)
            fill.color = 0x33FFFFFF; c.drawCircle(60f, 46f, 2.2f, fill); c.drawCircle(72f, 44f, 2f, fill); c.drawCircle(84f, 47f, 2.2f, fill)
        }

        fun cow(frame: Int) {
            val leg = 0xFFEDE8E0.toInt()
            legs(64f, 104f, 40f, 5f, leg, frame, 7f)
            oval(24f, 32f, 118f, 70f, 0xFFF4F0EA.toInt())
            fill.color = 0xFF2A2A2E.toInt()
            oval(38f, 36f, 62f, 56f, 0xFF2A2A2E.toInt()); oval(72f, 44f, 100f, 66f, 0xFF2A2A2E.toInt()); oval(52f, 54f, 66f, 68f, 0xFF2A2A2E.toInt())
            oval(94f, 52f, 108f, 64f, 0xFFD9A3A3.toInt()) // udder side hint
            r.set(112f, 38f, 140f, 62f); fill.color = 0xFFF4F0EA.toInt(); c.drawRoundRect(r, 8f, 8f, fill)
            oval(128f, 50f, 142f, 62f, 0xFFE8B4B0.toInt())
            oval(110f, 36f, 124f, 46f, 0xFF2A2A2E.toInt())
            fill.color = 0xFF2A2018.toInt(); c.drawCircle(130f, 44f, 1.6f, fill)
            line.color = 0xFFEDE0C0.toInt(); line.strokeWidth = 2.2f
            c.drawLine(114f, 38f, 112f, 31f, line); c.drawLine(122f, 38f, 124f, 31f, line)
            line.color = 0xFFD8D2C8.toInt(); line.strokeWidth = 2.4f
            c.drawLine(24f, 42f, 14f, 66f, line)
        }

        fun sheep(frame: Int) {
            legs(70f, 78f, 40f, 3f, 0xFF3A3430.toInt(), frame, 5f)
            val wool = 0xFFF1ECE2.toInt()
            for ((x, y, rr) in listOf(Triple(40f, 48f, 17f), Triple(58f, 42f, 19f), Triple(76f, 48f, 17f), Triple(50f, 58f, 17f), Triple(68f, 60f, 16f), Triple(32f, 60f, 13f))) {
                fill.color = wool; c.drawCircle(x, y, rr, fill)
                fill.color = 0x22000000; c.drawCircle(x + 3f, y + 4f, rr * 0.7f, fill)
            }
            oval(84f, 42f, 104f, 62f, 0xFF3A3430.toInt())
            oval(82f, 40f, 90f, 48f, 0xFF2E2824.toInt())
            fill.color = 0xFFF1ECE2.toInt(); c.drawCircle(95f, 42f, 2.6f, fill)
        }

        fun horse(frame: Int) {
            val coat = 0xFF7B4E2E.toInt()
            legs(62f, 104f, 40f, 4f, 0xFF4A2E1C.toInt(), frame, 10f)
            oval(26f, 34f, 112f, 68f, coat)
            path.rewind(); path.moveTo(98f, 44f); path.lineTo(112f, 12f); path.lineTo(126f, 18f); path.lineTo(112f, 60f); path.close()
            fill.color = coat; c.drawPath(path, fill)
            oval(114f, 8f, 146f, 28f, coat)
            oval(136f, 16f, 148f, 28f, 0xFF5A3822.toInt())
            fill.color = 0xFF1E140C.toInt(); c.drawCircle(136f, 14f, 1.8f, fill)
            path.rewind(); path.moveTo(98f, 40f); path.quadTo(104f, 20f, 112f, 8f); path.lineTo(116f, 12f); path.quadTo(108f, 28f, 106f, 46f); path.close()
            fill.color = 0xFF1E140C.toInt(); c.drawPath(path, fill)
            path.rewind(); path.moveTo(28f, 40f); path.quadTo(8f, 50f, 14f, 82f); path.quadTo(24f, 60f, 32f, 54f); path.close()
            c.drawPath(path, fill)
        }

        fun goat(frame: Int) {
            val coat = 0xFFE9E1D2.toInt()
            legs(62f, 96f, 44f, 3.2f, 0xFF8C826E.toInt(), frame, 6f)
            oval(34f, 38f, 104f, 68f, coat)
            oval(40f, 52f, 98f, 70f, 0xFFD4CABA.toInt())
            path.rewind(); path.moveTo(92f, 46f); path.lineTo(108f, 28f); path.lineTo(118f, 34f); path.lineTo(104f, 60f); path.close()
            fill.color = coat; c.drawPath(path, fill)
            oval(106f, 22f, 130f, 40f, coat)
            line.color = 0xFF5E5648.toInt(); line.strokeWidth = 2.4f
            path.rewind(); path.moveTo(112f, 24f); path.quadTo(108f, 8f, 94f, 6f)
            line.style = Paint.Style.STROKE; c.drawPath(path, line)
            c.drawLine(122f, 40f, 124f, 52f, line) // beard
            fill.color = 0xFF2A2018.toInt(); c.drawCircle(124f, 29f, 1.6f, fill)
            c.drawLine(34f, 46f, 28f, 40f, line)
        }

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

        fun dog(frame: Int) {
            val coat = 0xFF9A6B3F.toInt()
            legs(62f, 98f, 46f, 4f, 0xFF6B4A2A.toInt(), frame, 8f)
            oval(34f, 40f, 106f, 70f, coat)
            oval(98f, 28f, 128f, 54f, coat)
            oval(118f, 38f, 138f, 52f, 0xFF7A5230.toInt())
            path.rewind(); path.moveTo(104f, 30f); path.lineTo(100f, 14f); path.lineTo(112f, 28f); path.close()
            fill.color = 0xFF6B4A2A.toInt(); c.drawPath(path, fill)
            fill.color = 0xFF1E140C.toInt(); c.drawCircle(124f, 34f, 1.8f, fill); c.drawCircle(136f, 40f, 2.4f, fill)
            line.color = coat; line.strokeWidth = 5f
            c.drawLine(36f, 48f, 20f, 26f, line)
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
