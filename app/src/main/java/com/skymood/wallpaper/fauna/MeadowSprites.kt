package com.skymood.wallpaper.fauna

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Baked sprites for the meadow: flower heads on a stem (closed bud, half open, fully open) and
 * the kingfisher (perched, three wing poses, diving). Everything is drawn in a small unit box and
 * scaled by [SC] pixels per unit, facing right.
 */
internal object MeadowSprites {
    const val FLOWER_KINDS = 4
    const val LEVELS = 3
    const val FLOWER_SC = 1.5f
    const val FLOWER_W = 48f
    const val FLOWER_H = 96f
    /** Where the stem meets the ground, in box units. */
    const val FLOWER_BASE_X = 24f

    const val KF_PERCHED = 0
    const val KF_DIVE = 4
    const val KF_SC = 2f
    const val KF_W = 100f
    const val KF_H = 96f

    private fun argb(v: Long): Int = v.toInt()

    private class Bloom(val petals: Int, val len: Float, val wid: Float, val front: Int, val back: Int, val center: Int)

    private val blooms = arrayOf(
        Bloom(13, 13f, 4.2f, argb(0xFFFFFFFF), argb(0xFFE2E7EE), argb(0xFFF2C21E)), // daisy
        Bloom(5, 11f, 8f, argb(0xFFFFD21F), argb(0xFFE9B810), argb(0xFFE8861A)), // buttercup
        Bloom(7, 12f, 7f, argb(0xFFF06AA0), argb(0xFFD64E88), argb(0xFFF7D44A)), // pink cosmos
        Bloom(15, 12f, 3.6f, argb(0xFFB8A2F5), argb(0xFF9884DA), argb(0xFFF2C21E)), // aster
    )

    /** A flower on a stem; [level] 0 is a closed bud, 1 half open, 2 fully open. */
    fun bakeFlower(kind: Int, level: Int): Bitmap? {
        val bmp = runCatching {
            Bitmap.createBitmap((FLOWER_W * FLOWER_SC).toInt(), (FLOWER_H * FLOWER_SC).toInt(), Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return null
        val c = Canvas(bmp)
        c.scale(FLOWER_SC, FLOWER_SC)
        val b = blooms[kind.coerceIn(0, blooms.size - 1)]
        val o = when (level) { 0 -> 0f; 1 -> 0.55f; else -> 1f }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = 24f; val cy = 28f

        // Stem and two leaves.
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 2.8f
        p.color = argb(0xFF4A8636)
        val stem = Path()
        stem.moveTo(24f, 96f); stem.quadTo(28f, 64f, cx, cy + 3f)
        c.drawPath(stem, p)
        p.style = Paint.Style.FILL
        p.color = argb(0xFF3E7A2E)
        c.save(); c.translate(25f, 80f); c.rotate(-38f); c.drawOval(-14f, -3f, 0f, 3f, p); c.restore()
        p.color = argb(0xFF58963F)
        c.save(); c.translate(26f, 64f); c.rotate(36f); c.drawOval(0f, -3f, 13f, 3f, p); c.restore()

        // Petals: the upper (far) ones first, then the near ones, so the head reads as 3/4 view.
        val width = b.wid * (if (o < 0.3f) 0.85f else 1f)
        for (pass in 0..1) {
            p.color = if (pass == 0) b.back else b.front
            for (i in 0 until b.petals) {
                val a = 2f * PI.toFloat() * i / b.petals + 0.2f
                val s = sin(a)
                if ((s < 0f) != (pass == 0)) continue
                // Open petals radiate in a flattened circle; closed ones fold up around the bud.
                val dx = cos(a) * o + cos(a) * 0.22f * (1f - o)
                val dy = s * 0.62f * o - 0.95f * (1f - o)
                val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
                val ang = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                val l = b.len * (0.8f + 0.2f * o) * len.coerceIn(0.62f, 1f)
                c.save(); c.translate(cx, cy); c.rotate(ang)
                c.drawOval(1.5f, -width / 2f, 1.5f + l, width / 2f, p)
                c.restore()
            }
        }
        if (o < 0.8f) {
            // Green cup under the bud.
            p.color = argb(0xFF4A8636)
            c.drawOval(cx - 4.6f, cy - 1.5f, cx + 4.6f, cy + 5f, p)
        }
        if (o > 0.2f) {
            val r = 3.4f + 2.4f * o
            p.color = b.center
            c.drawCircle(cx, cy, r, p)
            p.color = argb(0x40000000)
            c.drawCircle(cx + 0.9f, cy + 0.9f, r * 0.55f, p)
        }
        return bmp
    }

    // ---------------------------------------------------------------- kingfisher

    private val BLUE = argb(0xFF1F8FC9)
    private val BLUE_DARK = argb(0xFF176FA8)
    private val HEAD = argb(0xFF2F8FC4)
    private val TEAL = argb(0xFF29C2BE)
    private val TAIL = argb(0xFF1B7F9E)
    private val ORANGE = argb(0xFFE88A2C)
    private val BEAK = argb(0xFF1B1D21)

    /** Frame 0 perched, 1 to 3 flying (wings up, level, down), 4 diving. */
    fun bakeKingfisher(frame: Int): Bitmap? {
        val bmp = runCatching {
            Bitmap.createBitmap((KF_W * KF_SC).toInt(), (KF_H * KF_SC).toInt(), Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return null
        val c = Canvas(bmp)
        c.scale(KF_SC, KF_SC)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (frame) {
            KF_PERCHED -> perched(c, p)
            KF_DIVE -> {
                c.save(); c.rotate(62f, 52f, 44f)
                flying(c, p, 0.35f, 0.55f)
                c.restore()
            }
            1 -> flying(c, p, 1.05f, 1f)
            2 -> flying(c, p, 0.15f, 1f)
            else -> flying(c, p, -0.85f, 1f)
        }
        return bmp
    }

    /** Where the bird's reference point sits in the box, in box units. */
    fun kingfisherAnchorX(frame: Int) = if (frame == KF_PERCHED) 46f else 52f
    fun kingfisherAnchorY(frame: Int) = when (frame) { KF_PERCHED -> 72f; KF_DIVE -> 44f; else -> 40f }

    private fun oval(c: Canvas, p: Paint, cx: Float, cy: Float, rx: Float, ry: Float, rot: Float, color: Int) {
        p.color = color
        c.save(); c.translate(cx, cy); c.rotate(rot)
        c.drawOval(-rx, -ry, rx, ry, p)
        c.restore()
    }

    private fun perched(c: Canvas, p: Paint) {
        p.style = Paint.Style.FILL
        val path = Path()
        // Short tail pointing down and back.
        p.color = TAIL
        path.moveTo(40f, 56f); path.lineTo(26f, 76f); path.lineTo(35f, 79f); path.lineTo(50f, 62f); path.close()
        c.drawPath(path, p)
        oval(c, p, 46f, 50f, 15f, 21f, -12f, BLUE)
        oval(c, p, 53f, 54f, 10f, 16f, -12f, ORANGE)
        oval(c, p, 42f, 52f, 8f, 17f, -12f, BLUE_DARK)
        oval(c, p, 40f, 40f, 5f, 9f, -12f, TEAL)
        // Head, orange cheek, white throat patch.
        p.color = HEAD
        c.drawCircle(58f, 30f, 11f, p)
        oval(c, p, 56f, 23.5f, 7f, 2.8f, -10f, TEAL)
        p.color = ORANGE; c.drawCircle(60f, 33f, 4.3f, p)
        p.color = argb(0xFFF3F1EA); c.drawCircle(64f, 40f, 4f, p)
        p.color = argb(0xFF14181C); c.drawCircle(61f, 29f, 1.9f, p)
        p.color = argb(0xFFFFFFFF); c.drawCircle(61.6f, 28.4f, 0.6f, p)
        // The long dagger bill.
        p.color = BEAK
        path.rewind(); path.moveTo(67f, 28.5f); path.lineTo(96f, 33f); path.lineTo(67f, 36f); path.close()
        c.drawPath(path, p)
        // Feet.
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.8f; p.strokeCap = Paint.Cap.ROUND
        p.color = argb(0xFFD2442A)
        c.drawLine(43f, 68f, 43f, 72f, p); c.drawLine(49f, 68f, 49f, 72f, p)
        p.style = Paint.Style.FILL
    }

    private fun flying(c: Canvas, p: Paint, theta: Float, wingScale: Float) {
        p.style = Paint.Style.FILL
        // Far wing, a little behind and darker.
        wing(c, p, theta * 0.85f - 0.05f, wingScale, argb(0xFF135C88))
        val path = Path()
        p.color = TAIL
        path.moveTo(36f, 40f); path.lineTo(20f, 37f); path.lineTo(21f, 45f); path.lineTo(38f, 44f); path.close()
        c.drawPath(path, p)
        oval(c, p, 50f, 40f, 18f, 9.5f, 0f, BLUE)
        oval(c, p, 52f, 45f, 15f, 5.5f, 0f, ORANGE)
        p.color = HEAD; c.drawCircle(68f, 36f, 8.5f, p)
        p.color = ORANGE; c.drawCircle(70f, 39f, 3.4f, p)
        p.color = argb(0xFF14181C); c.drawCircle(71f, 34.5f, 1.6f, p)
        p.color = BEAK
        path.rewind(); path.moveTo(75f, 34.5f); path.lineTo(98f, 38f); path.lineTo(75f, 41.5f); path.close()
        c.drawPath(path, p)
        wing(c, p, theta, wingScale, BLUE_DARK)
    }

    private fun wing(c: Canvas, p: Paint, theta: Float, scale: Float, color: Int) {
        val l = 27f * scale
        val tipX = 44f - 6f * cos(theta)
        val tipY = 38f - sin(theta) * l
        val path = Path()
        path.moveTo(58f, 38f)
        path.quadTo(50f, (38f + tipY) / 2f - 3f, tipX, tipY)
        path.lineTo(34f, 41f - sin(theta) * l * 0.25f)
        path.lineTo(46f, 43f)
        path.close()
        p.color = color
        c.drawPath(path, p)
        // Teal shoulder patch.
        p.color = TEAL
        c.drawOval(44f, 34.5f, 56f, 39.5f, p)
    }
}
