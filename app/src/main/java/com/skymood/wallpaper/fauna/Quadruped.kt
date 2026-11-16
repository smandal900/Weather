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
    val horns: Int = 0, // 0 none, 1 cow horns, 2 goat horns, 3 wild ox horns
    val patches: Boolean = false,
    val mane: Boolean = false,
    val floppyEars: Boolean = false,
    val beard: Boolean = false,
    val whiteTail: Boolean = false,
    val udder: Boolean = false,
    /** A muscular hump over the shoulders (wild ox). */
    val hump: Boolean = false,
    /** Colour of the lower legs when it differs from the upper ones (0 = same). */
    val socks: Int = 0,
    /** A bushy brush-like tail carried out behind the body (fox, jackal) instead of a thin one. */
    val bushyTail: Boolean = false,
    val tailTip: Int = 0,
    /** How far below horizontal the bushy tail hangs, degrees. */
    val tailDroop: Float = 12f,
    val tailWidth: Float = 5f,
    /** Height of tall pointed ears; 0 keeps the small default ear. */
    val earHeight: Float = 0f,
    val earTone: Int = 0,
    val noseColor: Int = 0,
    /** Pale underside of the muzzle (0 = none). */
    val chin: Int = 0,
    /** Dark patch along the back (0 = none). */
    val saddle: Int = 0,
    /** How far the head points below the neck's line, degrees; smaller is a flatter, longer snout. */
    val headDrop: Float = 52f,
    /** Empty room behind the rump, for a tail held out horizontally. */
    val rearPad: Float = 0f,
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
    /** All four feet planted, head carried at its relaxed height. */
    const val STAND_FRAME = 9
    /** Planted, neck stretched upright and head level: watching something. */
    const val ALERT_FRAME = 10
    /** Standing with the ear flicked back and the tail swung out; alternated with [STAND_FRAME]. */
    const val TWITCH_FRAME = 11
    /** Frames 12 to 19 are the running cycle: longer reach, higher lift and a bounding body. */
    const val GALLOP_FRAME = 12

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
        val rumpX = 14f + q.rearPad
        val chestX = rumpX + q.bodyLen
        val hindHip = floatArrayOf(rumpX + q.bodyLen * 0.16f, bellyY - q.depth * 0.25f)
        val foreHip = floatArrayOf(chestX - q.bodyLen * 0.14f, bellyY - q.depth * 0.2f)

        fun dark(color: Int, k: Float) = Color.rgb((Color.red(color) * k).toInt(), (Color.green(color) * k).toInt(), (Color.blue(color) * k).toInt())

        /** Whether this frame is part of the running cycle. */
        var gallop = false

        fun draw(frameIn: Int) {
            gallop = frameIn >= GALLOP_FRAME
            val frame = if (gallop) frameIn - GALLOP_FRAME else frameIn
            val graze = !gallop && frame == GRAZE_FRAME
            val stand = !gallop && frame >= STAND_FRAME
            val alert = !gallop && frame == ALERT_FRAME
            val twitch = !gallop && frame == TWITCH_FRAME
            // Grazing and standing animals keep all four feet down.
            val planted = graze || stand
            val phase = when {
                graze -> 0.5f
                twitch -> 0.3f
                stand -> 0.5f
                else -> frame / WALK_FRAMES.toFloat()
            }
            // Far-side legs first (darker), then the tail, body, head and neck, then near-side legs.
            leg(foreHip, true, phase + 0.75f, planted, far = true)
            leg(hindHip, false, phase + 0.5f, planted, far = true)
            tail(phase)
            body(phase, planted)
            headAndNeck(graze, phase, stand, alert, twitch)
            leg(hindHip, false, phase + 0.0f, planted, far = false)
            leg(foreHip, true, phase + 0.25f, planted, far = false)
            if (q.udder) {
                fill.color = 0xFFE8A9A5.toInt()
                r.set(hindHip[0] + 6f, bellyY - 3f, hindHip[0] + 15f, bellyY + 5f); c.drawOval(r, fill)
            }
        }

        fun leg(hip: FloatArray, front: Boolean, phaseIn: Float, graze: Boolean, far: Boolean) {
            val p = ((phaseIn % 1f) + 1f) % 1f
            val stride = q.bodyLen * 0.34f * (if (gallop) 1.9f else 1f)
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
                fy = 100f - legLen * (if (gallop) 0.34f else 0.2f) * sin(PI.toFloat() * t)
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
            if (q.socks != 0) line.color = if (far) dark(q.socks, 0.78f) else q.socks
            line.strokeWidth = q.legWidth * 0.8f
            c.drawLine(kx, ky, tx, ty, line)
            fill.color = if (far) dark(q.hoof, 0.8f) else q.hoof
            r.set(tx - q.legWidth * 0.75f, ty - q.legWidth * 0.45f, tx + q.legWidth * 1.1f, ty + q.legWidth * 0.4f); c.drawOval(r, fill)
        }

        /** A tapered brush that trails behind the rump, drooping a little, with a pale or dark tip. */
        fun bushyTail(phase: Float) {
            val sway = sin(phase * 2f * PI.toFloat()) * 2.2f
            val droop = Math.toRadians(q.tailDroop.toDouble()).toFloat()
            val ax = -cos(droop); val ay = sin(droop)   // the tail's axis, pointing back and down
            val px = ay; val py = -ax                   // perpendicular to it
            val bx = rumpX + 1.5f; val by = backY + q.depth * 0.22f
            val tx = bx + ax * q.tailLen; val ty = by + ay * q.tailLen + sway
            val mx = (bx + tx) / 2f; val my = (by + ty) / 2f
            val wm = q.tailWidth * 1.9f; val w0 = q.tailWidth * 0.35f
            path.rewind()
            path.moveTo(bx - px * w0, by - py * w0)
            path.quadTo(mx - px * wm, my - py * wm, tx, ty)
            path.quadTo(mx + px * wm, my + py * wm, bx + px * w0, by + py * w0)
            path.close()
            fill.color = dark(q.coat, 0.92f); c.drawPath(path, fill)
            if (q.tailTip != 0) {
                c.save(); c.clipPath(path)
                fill.color = q.tailTip
                c.drawCircle(bx + ax * q.tailLen * 0.95f, by + ay * q.tailLen * 0.95f + sway * 0.95f, q.tailLen * 0.26f, fill)
                c.restore()
            }
        }

        fun tail(phase: Float) {
            if (q.bushyTail) { bushyTail(phase); return }
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

        fun body(phase: Float, planted: Boolean) {
            val bob = if (planted) 0f else abs(sin(phase * 2f * PI.toFloat())) * (if (gallop) 2.6f else 0.8f)
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
                if (q.saddle != 0) {
                    c.save(); c.clipPath(path)
                    fill.color = q.saddle
                    r.set(rumpX + q.bodyLen * 0.1f, backY - 4f, chestX - q.bodyLen * 0.08f, backY + q.depth * 0.5f); c.drawOval(r, fill)
                    c.restore()
                }
                if (q.hump) {
                    path.rewind()
                    path.moveTo(chestX - q.bodyLen * 0.52f, backY + 4f)
                    path.quadTo(chestX - q.bodyLen * 0.3f, backY - q.depth * 0.66f, chestX - q.bodyLen * 0.03f, backY + q.depth * 0.32f)
                    path.close()
                    fill.color = q.coat; c.drawPath(path, fill)
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

        fun headAndNeck(graze: Boolean, phase: Float, stand: Boolean, alert: Boolean, twitch: Boolean) {
            val nx = chestX - q.bodyLen * 0.06f
            val ny = backY + q.depth * 0.3f
            val nod = if (graze || stand) 0f else sin(phase * 2f * PI.toFloat()) * (if (gallop) 6f else 3f)
            // An alert animal stretches its neck up and holds its head level to look out; a runner stretches forward.
            val neckDeg = (q.neckUp.toDouble() + nod + (if (alert) 20.0 else 0.0) - (if (gallop) 8.0 else 0.0)).coerceAtMost(86.0)
            val ang = Math.toRadians((if (graze) -38.0 else neckDeg)).toFloat()
            val nl = q.neckLen * (if (graze) 1.05f else if (alert) 1.06f else 1f)
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
            val hAng = ang - Math.toRadians(if (graze) 25.0 else q.headDrop.toDouble() - (if (alert) 10.0 else 0.0)).toFloat()
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
            if (q.chin != 0) {
                // Pale cheek and underside of the muzzle.
                path.rewind()
                path.moveTo(pollX + qx * d0 * 0.2f, pollY + qy * d0 * 0.2f)
                path.lineTo(muzX + qx * d1 * 0.15f, muzY + qy * d1 * 0.15f)
                path.quadTo(muzX + dxh * 2.5f + qx * d1 * 0.6f, muzY + dyh * 2.5f + qy * d1 * 0.6f, muzX + qx * d1, muzY + qy * d1)
                path.lineTo(pollX + qx * d0, pollY + qy * d0)
                path.close()
                fill.color = q.chin; c.drawPath(path, fill)
            }
            fill.color = if (q.noseColor != 0) q.noseColor
            else if (q.patches) 0xFFE8B4B0.toInt() else dark(if (q.wool) 0xFF2E2824.toInt() else q.coat, 0.7f)
            val noseR = if (q.noseColor != 0) 2.2f else 3f
            r.set(muzX - noseR, muzY - noseR, muzX + noseR, muzY + noseR); c.drawOval(r, fill)
            // Eye and ear.
            fill.color = 0xFF16120E.toInt(); c.drawCircle(pollX + dxh * q.headLen * 0.38f + qx * -d0 * 0.2f, pollY + dyh * q.headLen * 0.38f + qy * -d0 * 0.2f, 1.5f, fill)
            val ex = pollX + qx * -d0 * 0.4f; val ey = pollY + qy * -d0 * 0.4f - 1f
            if (q.earHeight > 0f) {
                // Tall upright ears: the far one a little behind and darker.
                val eh = q.earHeight
                path.rewind(); path.moveTo(ex - 6f, ey + 2f); path.lineTo(ex - 5f, ey - eh * 0.92f); path.lineTo(ex - 0.5f, ey + 1f); path.close()
                fill.color = dark(if (q.earTone != 0) q.earTone else q.coat, 0.78f); c.drawPath(path, fill)
                path.rewind(); path.moveTo(ex - 3f, ey + 2f); path.lineTo(ex - 2f, ey - eh); path.lineTo(ex + 3.5f, ey + 1f); path.close()
                fill.color = if (q.earTone != 0) q.earTone else dark(q.coat, 0.85f); c.drawPath(path, fill)
            } else {
                path.rewind()
                if (q.floppyEars) {
                    path.moveTo(ex, ey); path.lineTo(ex - 5f, ey + 8f); path.lineTo(ex + 2f, ey + 5f); path.close()
                } else if (twitch) {
                    // Flicked back and down.
                    path.moveTo(ex, ey); path.lineTo(ex - 9f, ey - 4f); path.lineTo(ex + 3f, ey - 1f); path.close()
                } else if (alert) {
                    // Pricked forward.
                    path.moveTo(ex, ey); path.lineTo(ex - 2f, ey - 10f); path.lineTo(ex + 4f, ey - 4f); path.close()
                } else {
                    path.moveTo(ex, ey); path.lineTo(ex - 4f, ey - 9f); path.lineTo(ex + 3f, ey - 3f); path.close()
                }
                fill.color = dark(q.coat, 0.85f); c.drawPath(path, fill)
            }
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
            } else if (q.horns == 3) {
                // Thick horns that sweep out and up, curving inwards, pale with dark tips.
                line.color = 0xFFE6DCBE.toInt(); line.strokeWidth = 3.4f
                path.rewind(); path.moveTo(ex - 1f, ey); path.quadTo(ex - 8f, ey - 6f, ex - 3f, ey - 15f)
                c.drawPath(path, line)
                line.color = 0xFF3A322A.toInt(); line.strokeWidth = 2.4f
                c.drawLine(ex - 3.8f, ey - 12.5f, ex - 3f, ey - 15.5f, line)
            }
        }
    }
}
