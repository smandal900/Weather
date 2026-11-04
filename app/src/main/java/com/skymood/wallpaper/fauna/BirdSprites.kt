package com.skymood.wallpaper.fauna

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Side-view bird silhouettes with feathered wings, baked once per wing pose. Frames 0 to 7 are a
 * wing-beat cycle (wings up, level, down, level), frame 8 is a glide with wings held out.
 */
internal object BirdSprites {
    const val KIND_SMALL = 0
    const val KIND_GULL = 1
    const val KIND_EAGLE = 2
    const val KIND_GOOSE = 3
    const val KIND_BAT = 4

    const val FLAP_FRAMES = 8
    const val GLIDE_FRAME = 8
    /** Bitmap pixels per drawing unit. */
    const val SC = 2f
    const val BOX_W = 120f
    const val BOX_H = 100f

    /** Wing length of each kind in drawing units, used to size the sprite on screen. */
    val WING = floatArrayOf(20f, 34f, 38f, 30f, 28f)

    private class Look(
        val bodyLen: Float, val bodyH: Float, val wing: Float, val rootW: Float, val tail: Float,
        val color: Int, val tip: Int, val notches: Int, val longNeck: Boolean = false, val bat: Boolean = false,
    )

    private val looks = arrayOf(
        Look(14f, 5f, 20f, 8f, 8f, 0xFF2A2E38.toInt(), 0xFF1A1D24.toInt(), 3),
        Look(22f, 6.5f, 34f, 9f, 9f, 0xFFF3F3F0.toInt(), 0xFF30343C.toInt(), 4),
        Look(26f, 8.5f, 38f, 17f, 15f, 0xFF4B3A2C.toInt(), 0xFF2A1F17.toInt(), 5),
        Look(30f, 9f, 30f, 11f, 8f, 0xFF4E4E56.toInt(), 0xFF26262C.toInt(), 4, longNeck = true),
        Look(8f, 6f, 28f, 14f, 3f, 0xFF14141A.toInt(), 0xFF0C0C10.toInt(), 3, bat = true),
    )

    fun bake(kind: Int, frame: Int): Bitmap? {
        val bmp = runCatching { Bitmap.createBitmap((BOX_W * SC).toInt(), (BOX_H * SC).toInt(), Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val c = Canvas(bmp)
        c.scale(SC, SC)
        val look = looks[kind]
        val theta = if (frame == GLIDE_FRAME) (if (kind == KIND_EAGLE) 0.4f else 0.55f) else -0.12f + 0.95f * cos(2f * PI.toFloat() * frame / FLAP_FRAMES)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = 62f; val cy = 52f
        val sx = cx + look.bodyLen * 0.12f
        val sy = cy - look.bodyH * 0.2f

        // Far wing, a little behind and darker.
        wing(c, fill, sx - 2f, sy, theta * 0.8f - 0.1f, look, dark(look.color, 0.68f), look.tip)
        body(c, fill, look, cx, cy)
        // Near wing.
        wing(c, fill, sx, sy, theta, look, look.color, look.tip)
        return bmp
    }

    private fun dark(color: Int, k: Float) = Color.rgb((Color.red(color) * k).toInt(), (Color.green(color) * k).toInt(), (Color.blue(color) * k).toInt())

    private fun body(c: Canvas, p: Paint, look: Look, cx: Float, cy: Float) {
        val path = Path()
        val r = RectF()
        // Tail first, so the body overlaps its root.
        p.color = dark(look.color, 0.85f)
        path.moveTo(cx - look.bodyLen * 0.4f, cy - look.bodyH * 0.2f)
        path.lineTo(cx - look.bodyLen * 0.4f - look.tail, cy - look.bodyH * 0.3f)
        path.lineTo(cx - look.bodyLen * 0.4f - look.tail * 0.9f, cy + look.bodyH * 0.35f)
        path.lineTo(cx - look.bodyLen * 0.35f, cy + look.bodyH * 0.2f)
        path.close()
        c.drawPath(path, p)
        p.color = look.color
        r.set(cx - look.bodyLen * 0.5f, cy - look.bodyH * 0.5f, cx + look.bodyLen * 0.5f, cy + look.bodyH * 0.5f)
        c.drawOval(r, p)
        // Lighter belly.
        p.color = if (look.bat) look.color else Color.argb(60, 255, 255, 255)
        r.set(cx - look.bodyLen * 0.4f, cy, cx + look.bodyLen * 0.4f, cy + look.bodyH * 0.45f)
        c.drawOval(r, p)
        // Head, neck and beak.
        val hx: Float; val hy: Float
        p.color = look.color
        if (look.longNeck) {
            path.rewind()
            path.moveTo(cx + look.bodyLen * 0.35f, cy - look.bodyH * 0.2f)
            path.lineTo(cx + look.bodyLen * 0.35f + 11f, cy - look.bodyH * 0.55f)
            path.lineTo(cx + look.bodyLen * 0.35f + 12f, cy - look.bodyH * 0.2f)
            path.lineTo(cx + look.bodyLen * 0.3f, cy + look.bodyH * 0.2f)
            path.close()
            c.drawPath(path, p)
            hx = cx + look.bodyLen * 0.35f + 12f; hy = cy - look.bodyH * 0.45f
        } else {
            hx = cx + look.bodyLen * 0.5f; hy = cy - look.bodyH * 0.25f
        }
        val hr = look.bodyH * (if (look.bat) 0.45f else 0.38f)
        r.set(hx - hr, hy - hr, hx + hr, hy + hr); c.drawOval(r, p)
        if (look.bat) {
            path.rewind(); path.moveTo(hx - hr * 0.6f, hy - hr * 0.5f); path.lineTo(hx - hr * 0.2f, hy - hr * 2f); path.lineTo(hx + hr * 0.3f, hy - hr * 0.6f); path.close()
            c.drawPath(path, p)
        } else {
            p.color = if (look.color == 0xFFF3F3F0.toInt()) 0xFFE8B030.toInt() else 0xFF2A2420.toInt()
            path.rewind(); path.moveTo(hx + hr * 0.7f, hy - hr * 0.4f); path.lineTo(hx + hr * 0.7f + hr * 1.5f, hy + hr * 0.1f); path.lineTo(hx + hr * 0.7f, hy + hr * 0.4f); path.close()
            c.drawPath(path, p)
        }
    }

    /**
     * One wing: a leading edge bowing out to the tip, and a trailing edge with feather notches
     * (or scalloped membrane for a bat) back to the root. [theta] is the angle above level.
     */
    private fun wing(c: Canvas, p: Paint, sx: Float, sy: Float, theta: Float, look: Look, color: Int, tipColor: Int) {
        val l = look.wing
        val tipX = sx - l * 0.34f
        val tipY = sy - sin(theta) * l + (if (abs(theta) < 0.3f) l * 0.1f else 0f)
        val rootBackX = sx - look.rootW
        val rootBackY = sy + look.bodyH * 0.15f
        val path = Path()
        path.moveTo(sx + 2f, sy)
        // The leading edge bows forward and out.
        path.quadTo(sx - l * 0.02f, (sy + tipY) / 2f - sin(theta) * l * 0.08f, tipX, tipY)
        val n = look.notches
        val dx = rootBackX - tipX; val dy = rootBackY - tipY
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        val nx = -dy / len; val ny = dx / len // perpendicular
        for (k in 1..n) {
            val t = k / (n + 1f)
            val bx = tipX + dx * t; val by = tipY + dy * t
            val depth = look.rootW * (if (look.bat) 0.35f else 0.22f) * sin(PI.toFloat() * t).coerceAtLeast(0.2f)
            val side = if (look.bat) 1f else (if (k % 2 == 0) -1f else 1f)
            path.lineTo(bx + nx * depth * side * 0.6f, by + ny * depth * side * 0.6f)
            if (look.bat) path.quadTo(bx + nx * depth * 1.4f, by + ny * depth * 1.4f, tipX + dx * (t + 0.5f / (n + 1f)), tipY + dy * (t + 0.5f / (n + 1f)))
        }
        path.lineTo(rootBackX, rootBackY)
        path.lineTo(sx - look.rootW * 0.4f, sy + look.bodyH * 0.2f)
        path.close()
        p.color = color
        c.drawPath(path, p)
        // Darker wing tip.
        if (!look.bat && tipColor != color) {
            val tp = Path()
            tp.moveTo(tipX, tipY)
            tp.lineTo(tipX + (sx - tipX) * 0.18f + 1.5f, tipY + (sy - tipY) * 0.18f)
            tp.lineTo(tipX + dx * 0.22f, tipY + dy * 0.22f)
            tp.close()
            p.color = tipColor
            c.drawPath(tp, p)
        }
        // A shade line along the wing bones.
        p.color = Color.argb(40, 0, 0, 0)
        val bone = Path()
        bone.moveTo(sx, sy); bone.lineTo(tipX + 1.5f, tipY)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f
        c.drawPath(bone, p)
        p.style = Paint.Style.FILL
    }
}
