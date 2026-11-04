package com.skymood.wallpaper.fauna

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** How one four-legged species is built: proportions in a 100-unit-high box, plus its look. */
internal class QuadSpec(
    val bodyLen: Float,
    val depth: Float,
    val thigh: Float,
    val shin: Float,
    val neckLen: Float,
    /** Neck angle above horizontal when the head is up, degrees. */
    val neckUp: Float,
    val headLen: Float,
    val headDepth: Float,
    val coat: Int,
    val belly: Int,
    val legColor: Int,
    val hoof: Int,
    val tailLen: Float,
    val tailUp: Boolean,
    val legWidth: Float,
    val wool: Boolean = false,
    val antlers: Boolean = false,
    val horns: Int = 0, // 0 none, 1 cow horns, 2 goat horns
    val patches: Boolean = false,
    val mane: Boolean = false,
    val floppyEars: Boolean = false,
    val beard: Boolean = false,
    val whiteTail: Boolean = false,
    val udder: Boolean = false,
)

/**
 * Draws a quadruped with a real walk cycle: each leg is a two-bone limb whose foot follows a
 * stance (sliding back along the ground) and swing (lifting and reaching forward) path, and the
 * knee is solved so the joint bends the way that animal's does (forward at the front, backward
 * at the hock). Legs on the far side are darker and drawn first. The head can drop to graze.
 */
internal object Quadruped {

    const val WALK_FRAMES = 8
    const val GRAZE_FRAME = 8

    fun bake(q: QuadSpec, frame: Int, heightPx: Int, aspect: Float): Bitmap? {
        val wPx = (heightPx * aspect).toInt().coerceAtLeast(4)
        val bmp = runCatching { Bitmap.createBitmap(wPx, heightPx.coerceAtLeast(4), Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val c = Canvas(bmp)
        val s = heightPx / 100f
        c.scale(s, s)
        Drawer(c, q, aspect * 100f).draw(frame)
        return bmp
    }

    private class Drawer(val c: Canvas, val q: QuadSpec, val boxW: Float) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
        val path = Path()
        val r = RectF()

        val legLen = q.thigh + q.shin
        val bellyY = 100f - legLen * 0.94f
        val backY = bellyY - q.depth
        val rumpX = 14f
        val chestX = rumpX + q.bodyLen
        val hindHip = floatArrayOf(rumpX + q.bodyLen * 0.16f, bellyY - q.depth * 0.25f)
        val foreHip = floatArrayOf(chestX - q.bodyLen * 0.14f, bellyY - q.depth * 0.2f)

        fun dark(color: Int, k: Float) = Color.rgb((Color.red(color) * k).toInt(), (Color.green(color) * k).toInt(), (Color.blue(color) * k).toInt())

        fun draw(frame: Int) {
            val graze = frame == GRAZE_FRAME
            val phase = if (graze) 0.5f else frame / WALK_FRAMES.toFloat()
            // Far-side legs first (darker), then the tail, body, head and neck, then near-side legs.
            leg(foreHip, true, phase + 0.75f, graze, far = true)
            leg(hindHip, false, phase + 0.5f, graze, far = true)
            tail(phase)
            body(phase)
            headAndNeck(graze, phase)
            leg(hindHip, false, phase + 0.0f, graze, far = false)
            leg(foreHip, true, phase + 0.25f, graze, far = false)
            if (q.udder) {
                fill.color = 0xFFE8A9A5.toInt()
                r.set(hindHip[0] + 6f, bellyY - 3f, hindHip[0] + 15f, bellyY + 5f); c.drawOval(r, fill)
            }
        }

        fun leg(hip: FloatArray, front: Boolean, phaseIn: Float, graze: Boolean, far: Boolean) {
            val p = ((phaseIn % 1f) + 1f) % 1f
            val stride = q.bodyLen * 0.34f
            val fx: Float
            val fy: Float
            if (graze) {
                fx = hip[0] + (if (front) 3f else -3f) + (if (far) -3f else 3f)
                fy = 100f
            } else if (p < 0.6f) {
                val t = p / 0.6f
                fx = hip[0] + stride * (0.5f - t)
                fy = 100f
            } else {
                val t = (p - 0.6f) / 0.4f
                val e = t * t * (3f - 2f * t)
                fx = hip[0] - stride * 0.5f + stride * e
                fy = 100f - legLen * 0.2f * sin(PI.toFloat() * t)
            }
            // Two-bone solve for the knee.
            val dx = fx - hip[0]; val dy = fy - hip[1]
            var d = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val maxD = q.thigh + q.shin - 0.2f
            val tx = if (d > maxD) hip[0] + dx / d * maxD else fx
            val ty = if (d > maxD) hip[1] + dy / d * maxD else fy
            d = d.coerceAtMost(maxD)
            val a = (q.thigh * q.thigh - q.shin * q.shin + d * d) / (2f * d)
            val hgt = sqrt((q.thigh * q.thigh - a * a).coerceAtLeast(0f))
            val ux = dx / sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val uy = dy / sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val k1x = hip[0] + ux * a - uy * hgt; val k1y = hip[1] + uy * a + ux * hgt
            val k2x = hip[0] + ux * a + uy * hgt; val k2y = hip[1] + uy * a - ux * hgt
            // Front knees bend forward, hind hocks bend backward.
            val useFirst = if (front) k1x > k2x else k1x < k2x
            val kx = if (useFirst) k1x else k2x
            val ky = if (useFirst) k1y else k2y
            val col = if (far) dark(q.legColor, 0.7f) else q.legColor
            line.color = col
            line.strokeWidth = q.legWidth * (if (front) 1.1f else 1.25f)
            c.drawLine(hip[0], hip[1], kx, ky, line)
            line.strokeWidth = q.legWidth * 0.8f
            c.drawLine(kx, ky, tx, ty, line)
            fill.color = if (far) dark(q.hoof, 0.8f) else q.hoof
            r.set(tx - q.legWidth * 0.75f, ty - q.legWidth * 0.45f, tx + q.legWidth * 1.1f, ty + q.legWidth * 0.4f); c.drawOval(r, fill)
        }

        fun tail(phase: Float) {
            val sway = sin(phase * 2f * PI.toFloat()) * 3f
            val bx = rumpX + 1f; val by = backY + q.depth * 0.18f
            line.color = if (q.whiteTail) 0xFFF4EEE4.toInt() else dark(q.coat, 0.8f)
            if (q.whiteTail) {
                path.rewind(); path.moveTo(bx + 2f, by - 2f); path.lineTo(bx - 7f, by - 6f + sway * 0.3f); path.lineTo(bx + 2f, by + 8f); path.close()
                fill.color = 0xFFF4EEE4.toInt(); c.drawPath(path, fill)
                return
            }
            line.strokeWidth = if (q.tailLen > 20f) 4.5f else 3.2f
            if (q.tailUp) {
                c.drawLine(bx + 1f, by, bx - 5f + sway, by - q.tailLen, line)
            } else {
                path.rewind(); path.moveTo(bx + 1f, by)
                path.quadTo(bx - q.tailLen * 0.35f + sway, by + q.tailLen * 0.3f, bx - q.tailLen * 0.15f + sway, by + q.tailLen)
                line.style = Paint.Style.STROKE; c.drawPath(path, line)
                if (q.tailLen > 20f) { fill.color = dark(q.coat, 0.6f); c.drawCircle(bx - q.tailLen * 0.15f + sway, by + q.tailLen, 3.2f, fill) }
            }
        }

        fun body(phase: Float) {
            val bob = abs(sin(phase * 2f * PI.toFloat())) * 0.8f
            c.save()
            c.translate(0f, -bob)
            path.rewind()
            val rb = backY + q.depth * 0.12f
            path.moveTo(rumpX, rb + q.depth * 0.25f)
            path.cubicTo(rumpX - 1f, rb - 2f, rumpX + q.bodyLen * 0.12f, backY + 1f, rumpX + q.bodyLen * 0.4f, backY + 0.5f)
            path.cubicTo(rumpX + q.bodyLen * 0.62f, backY - 1.5f, chestX - q.bodyLen * 0.14f, backY - 2.5f, chestX - q.bodyLen * 0.05f, backY + q.depth * 0.12f)
            path.cubicTo(chestX + 2f, backY + q.depth * 0.35f, chestX + 1.5f, bellyY - q.depth * 0.25f, chestX - 3f, bellyY - 1f)
            path.cubicTo(chestX - q.bodyLen * 0.3f, bellyY + 2f, rumpX + q.bodyLen * 0.3f, bellyY + 1.5f, rumpX + 4f, bellyY - q.depth * 0.1f)
            path.cubicTo(rumpX - 1f, bellyY - q.depth * 0.3f, rumpX - 2f, rb + q.depth * 0.3f, rumpX, rb + q.depth * 0.25f)
            path.close()
            if (q.wool) {
                woolBody()
            } else {
                fill.shader = LinearGradient(0f, backY, 0f, bellyY + 2f, q.coat, q.belly, Shader.TileMode.CLAMP)
                c.drawPath(path, fill)
                fill.shader = null
                // Shading under the belly and over the haunch.
                fill.color = 0x22000000; r.set(rumpX + 2f, bellyY - q.depth * 0.5f, rumpX + q.bodyLen * 0.38f, bellyY + 3f); c.drawOval(r, fill)
                if (q.patches) {
                    c.save(); c.clipPath(path)
                    fill.color = 0xFF26262A.toInt()
                    r.set(rumpX + 6f, backY - 2f, rumpX + 24f, backY + q.depth * 0.55f); c.drawOval(r, fill)
                    r.set(rumpX + q.bodyLen * 0.5f, backY + q.depth * 0.25f, rumpX + q.bodyLen * 0.8f, bellyY + 2f); c.drawOval(r, fill)
                    r.set(rumpX + q.bodyLen * 0.38f, backY - 3f, rumpX + q.bodyLen * 0.52f, backY + q.depth * 0.3f); c.drawOval(r, fill)
                    c.restore()
                }
            }
            c.restore()
        }

        fun woolBody() {
            val wool = q.coat
            val cx = rumpX + q.bodyLen * 0.5f
            val cy = backY + q.depth * 0.5f
            val n = 14
            for (i in 0 until n) {
                val a = i * 2.4f
                val rx = q.bodyLen * 0.5f * (0.25f + 0.75f * ((i * 37) % 10) / 10f)
                val ry = q.depth * 0.5f * (0.25f + 0.75f * ((i * 53) % 10) / 10f)
                val px = cx + cos(a) * rx * 0.85f
                val py = cy + sin(a) * ry * 0.8f
                fill.color = dark(wool, 0.9f - 0.12f * (py - backY) / q.depth)
                c.drawCircle(px, py, q.depth * 0.32f, fill)
            }
            for (i in 0 until n) {
                val a = i * 2.4f + 1f
                val px = cx + cos(a) * q.bodyLen * 0.4f
                val py = cy + sin(a) * q.depth * 0.38f - q.depth * 0.05f
                fill.color = Color.rgb(
                    (Color.red(wool) * 1.04f).toInt().coerceAtMost(255), (Color.green(wool) * 1.04f).toInt().coerceAtMost(255),
                    (Color.blue(wool) * 1.04f).toInt().coerceAtMost(255),
                )
                c.drawCircle(px, py, q.depth * 0.22f, fill)
            }
        }

        fun headAndNeck(graze: Boolean, phase: Float) {
            val nx = chestX - q.bodyLen * 0.06f
            val ny = backY + q.depth * 0.3f
            val nod = if (graze) 0f else sin(phase * 2f * PI.toFloat()) * 3f
            val ang = Math.toRadians((if (graze) -38.0 else q.neckUp.toDouble() + nod)).toFloat()
            val nl = q.neckLen * (if (graze) 1.05f else 1f)
            val hx = nx + cos(ang) * nl
            val hy = ny - sin(ang) * nl
            // Neck: a tapered band from the shoulders to the poll.
            val wBase = q.depth * 0.5f
            val wTop = q.headDepth * 0.6f
            val px = -sin(ang); val py = -cos(ang)
            path.rewind()
            path.moveTo(nx - px * wBase, ny - py * wBase * 0.4f + wBase * 0.35f)
            path.lineTo(nx + px * wBase * 0.1f - 0f, ny - wBase * 0.2f)
            path.lineTo(hx + px * wTop * 0.8f, hy + py * wTop * 0.5f - wTop * 0.5f)
            path.lineTo(hx - px * wTop * 0.7f, hy - py * wTop * 0.3f + wTop * 0.7f)
            path.lineTo(nx - 2f, ny + wBase)
            path.close()
            fill.color = q.coat; c.drawPath(path, fill)
            if (q.mane) {
                path.rewind(); path.moveTo(nx - 2f, ny - wBase * 0.5f); path.lineTo(hx - 2f, hy - wTop * 0.9f); path.lineTo(hx + 2f, hy - wTop * 0.6f); path.lineTo(nx + 3f, ny - wBase * 0.1f); path.close()
                fill.color = dark(q.coat, 0.45f); c.drawPath(path, fill)
            }
            // Head: pointing forward and down from the neck.
            val hAng = ang - Math.toRadians(if (graze) 25.0 else 52.0).toFloat()
            val dxh = cos(hAng); val dyh = -sin(hAng)
            val pollX = hx; val pollY = hy
            val muzX = pollX + dxh * q.headLen; val muzY = pollY + dyh * q.headLen
            val qx = -dyh; val qy = dxh
            val d0 = q.headDepth * 0.55f; val d1 = q.headDepth * 0.28f
            path.rewind()
            path.moveTo(pollX + qx * -d0, pollY + qy * -d0)
            path.lineTo(muzX + qx * -d1, muzY + qy * -d1)
            path.quadTo(muzX + dxh * 2.5f, muzY + dyh * 2.5f, muzX + qx * d1, muzY + qy * d1)
            path.lineTo(pollX + qx * d0, pollY + qy * d0)
            path.close()
            fill.color = if (q.wool) dark(0xFF3A3430.toInt(), 1f) else q.coat
            c.drawPath(path, fill)
            fill.color = if (q.patches) 0xFFE8B4B0.toInt() else dark(if (q.wool) 0xFF2E2824.toInt() else q.coat, 0.7f)
            r.set(muzX - 3f, muzY - 3f, muzX + 3f, muzY + 3f); c.drawOval(r, fill)
            // Eye and ear.
            fill.color = 0xFF16120E.toInt(); c.drawCircle(pollX + dxh * q.headLen * 0.38f + qx * -d0 * 0.2f, pollY + dyh * q.headLen * 0.38f + qy * -d0 * 0.2f, 1.5f, fill)
            val ex = pollX + qx * -d0 * 0.4f; val ey = pollY + qy * -d0 * 0.4f - 1f
            path.rewind()
            if (q.floppyEars) {
                path.moveTo(ex, ey); path.lineTo(ex - 5f, ey + 8f); path.lineTo(ex + 2f, ey + 5f); path.close()
            } else {
                path.moveTo(ex, ey); path.lineTo(ex - 4f, ey - 9f); path.lineTo(ex + 3f, ey - 3f); path.close()
            }
            fill.color = dark(q.coat, 0.85f); c.drawPath(path, fill)
            if (q.beard) {
                line.color = 0xFFD9D0C0.toInt(); line.strokeWidth = 2f
                c.drawLine(muzX - dxh * 3f + qx * d1, muzY - dyh * 3f + qy * d1, muzX - dxh * 3f + qx * d1, muzY - dyh * 3f + qy * d1 + 6f, line)
            }
            line.color = 0xFF8A7A5A.toInt()
            line.strokeWidth = 1.6f
            if (q.antlers) {
                val ax = ex; val ay = ey - 3f
                c.drawLine(ax, ay, ax - 3f, ay - 13f, line); c.drawLine(ax - 2f, ay - 7f, ax + 4f, ay - 12f, line)
                c.drawLine(ax - 3f, ay - 13f, ax + 1f, ay - 19f, line); c.drawLine(ax - 3f, ay - 13f, ax - 8f, ay - 16f, line)
            }
            if (q.horns == 1) {
                line.color = 0xFFEFE6C8.toInt(); line.strokeWidth = 2.4f
                c.drawLine(ex, ey - 1f, ex - 1f, ey - 7f, line)
            } else if (q.horns == 2) {
                line.color = 0xFF6E6454.toInt(); line.strokeWidth = 2.2f
                path.rewind(); path.moveTo(ex, ey - 1f); path.quadTo(ex - 3f, ey - 12f, ex - 12f, ey - 11f)
                c.drawPath(path, line)
            }
        }
    }
}
