package com.skymood.wallpaper.landscape

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * Sand painting for the beach. Everything is laid out relative to [shoreEdge], so the wet band, the
 * ripples and the line of seaweed all follow the curve of the waterline instead of sitting in
 * fixed horizontal stripes. Runs on the painter thread only.
 */

/** Broad soft patches of lighter and darker sand, so the beach is not one flat tone. */
internal fun PaintKit.beachTone(c: Canvas, rnd: Random, dry: Int) {
    repeat(16) {
        val x = -margin + rnd.nextFloat() * (w + margin * 2)
        val y = shoreEdge(x, w, h) + (h - shoreEdge(x, w, h)) * rnd.nextFloat()
        val rx = w * (0.1f + 0.22f * rnd.nextFloat())
        val ry = rx * (0.1f + 0.1f * rnd.nextFloat())
        val col = if (rnd.nextBoolean()) darken(dry, 0.14f) else lighten(dry, 0.3f)
        for (k in 0 until 4) {
            val f = 1f - k * 0.22f
            fill.color = alpha(col, 11)
            c.drawOval(x - rx * f, y - ry * f, x + rx * f, y + ry * f, fill)
        }
    }
}

/** Wet sand darkening towards the waterline, with a faint sheen of sky on the glossiest strip. */
internal fun PaintKit.beachWetSand(c: Canvas) {
    val p = Path()
    val steps = 40
    fun band(depth: Float, color: Int, a: Int) {
        p.rewind()
        for (i in 0..steps) {
            val x = -margin + (w + 2 * margin) * i / steps
            val y = shoreEdge(x, w, h) - h * 0.004f
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        for (i in steps downTo 0) {
            val x = -margin + (w + 2 * margin) * i / steps
            p.lineTo(x, shoreEdge(x, w, h) + depth + h * 0.004f * sin(x / w * 9f))
        }
        p.close()
        fill.color = alpha(color, a)
        c.drawPath(p, fill)
    }
    val wet = l.ground(0xFF6A563A.toInt())
    for (b in 0 until 9) band(h * 0.085f * (b + 1) / 9f, wet, 20)
    val sky = mix(l.skyBottom, Color.WHITE, 0.25f)
    for (b in 0 until 4) band(h * 0.02f * (b + 1) / 4f, sky, 16)
}

/** Wind and tide ripples: rows parallel to the shore that spread out towards the viewer. */
internal fun PaintKit.beachRipples(c: Canvas) {
    val rows = 24
    val dark = alpha(l.ground(0xFF8A6F44.toInt()), 44)
    val light = alpha(lighten(l.ground(0xFFEBD9AE.toInt()), 0.5f), 80)
    val step = 22f * unit
    for (r in 0 until rows) {
        val f = r / (rows - 1f)
        val dy = h * (0.035f + 0.15f * f.pow(1.15f))
        line.strokeWidth = (0.9f + 1.2f * f) * unit
        var px = 0f; var py = 0f
        var prevOk = false
        var x = -margin
        while (x <= w + margin) {
            val y = shoreEdge(x, w, h) + dy + h * 0.0035f * sin(x / w * (13f + r * 0.7f) + r * 1.9f) * (0.4f + f)
            val ok = valueNoise(x / (90f * unit), r * 3.7f, 11) > 0.42f
            if (ok && prevOk) {
                line.color = dark
                c.drawLine(px, py, x, y, line)
                line.color = light
                val dy2 = 1.5f * unit * (0.6f + f)
                c.drawLine(px, py + dy2, x, y + dy2, line)
            }
            px = x; py = y; prevOk = ok
            x += step
        }
    }
}

/**
 * What the sea leaves and people leave: the wrack line of seaweed at the top of the swash, shells,
 * a starfish, a trail of footprints on the wet sand, driftwood and tufts of dune grass.
 * Painted before the beach props so they stand on top.
 */
internal fun PaintKit.beachDebris(c: Canvas, rnd: Random) {
    val wrack = intArrayOf(0xFF3B3A22.toInt(), 0xFF55502C.toInt(), 0xFF2D3320.toInt(), 0xFF6B5A36.toInt())
        .map { l.ground(it) }
    repeat((240 * unit).toInt().coerceAtLeast(60)) {
        val x = -margin + rnd.nextFloat() * (w + margin * 2)
        if (valueNoise(x / (110f * unit), 5.5f, 21) < 0.5f) return@repeat
        val y = shoreEdge(x, w, h) + h * (0.026f + 0.008f * sin(x / w * 8f) + (rnd.nextFloat() - 0.5f) * 0.012f)
        val len = (3f + rnd.nextFloat() * 9f) * unit
        val a = (rnd.nextFloat() - 0.5f) * 1.2f
        line.strokeWidth = (1f + rnd.nextFloat() * 1.4f) * unit
        line.color = alpha(wrack[rnd.nextInt(wrack.size)], 210)
        c.drawLine(x, y, x + cos(a) * len, y + sin(a) * len * 0.4f, line)
    }
    // Footprints along the wet sand, fading out behind the walker.
    val wetDark = alpha(l.ground(0xFF5E4A30.toInt()), 90)
    val wetLight = alpha(lighten(l.ground(0xFFEBD9AE.toInt()), 0.5f), 70)
    for (i in 0 until 13) {
        val x = w * (0.61f + 0.03f * i)
        val side = if (i % 2 == 0) -1f else 1f
        val y = shoreEdge(x, w, h) + h * 0.034f + side * 3.5f * unit + sin(i * 0.5f) * h * 0.002f
        val fade = 1f - i / 16f
        fill.color = alpha(wetLight, (70 * fade).toInt())
        c.drawOval(x - 6f * unit, y - 2.6f * unit + 2.2f * unit, x + 6f * unit, y + 2.6f * unit + 2.2f * unit, fill)
        fill.color = alpha(wetDark, (90 * fade).toInt())
        c.drawOval(x - 6f * unit, y - 2.6f * unit, x + 6f * unit, y + 2.6f * unit, fill)
    }
    // Shells scattered mostly near the wrack line.
    val shellCols = intArrayOf(0xFFF3E6D0.toInt(), 0xFFEBC9B5.toInt(), 0xFFF7F0E3.toInt(), 0xFFD9B79A.toInt())
    for (i in 0 until 7) {
        val x = w * (0.06f + 0.9f * rnd.nextFloat())
        val y = shoreEdge(x, w, h) + h * (0.03f + 0.09f * rnd.nextFloat())
        fanShell(c, x, y, (4.5f + rnd.nextFloat() * 4f) * unit, l.ground(shellCols[rnd.nextInt(shellCols.size)]), (rnd.nextFloat() - 0.5f) * 50f)
    }
    starfish(c, w * 0.64f, shoreEdge(w * 0.64f, w, h) + h * 0.07f, 9f * unit, l.ground(0xFFE0762F.toInt()))
    starfish(c, w * 0.05f, shoreEdge(w * 0.05f, w, h) + h * 0.115f, 6.5f * unit, l.ground(0xFFD9573B.toInt()))

    driftwood(c, w * 0.63f, h * 0.935f, w * 0.17f, w * 0.012f)
    driftwood(c, w * 0.12f, h * 0.89f, w * 0.07f, w * 0.008f)

    // Dune grass at the back of the beach (the screen's lower corners).
    for (i in 0 until 5) duneTuft(c, w * (0.13f + 0.022f * i) + rnd.nextFloat() * 8f * unit, h * (0.975f + 0.006f * (i % 2)), h * (0.05f + 0.02f * rnd.nextFloat()), rnd)
    for (i in 0 until 7) duneTuft(c, w * (0.86f + 0.024f * i) + rnd.nextFloat() * 8f * unit, h * (0.965f + 0.008f * (i % 2)), h * (0.055f + 0.02f * rnd.nextFloat()), rnd)
}

private fun PaintKit.fanShell(c: Canvas, x: Float, y: Float, s: Float, col: Int, rot: Float) {
    c.save()
    c.rotate(rot, x, y)
    fill.color = alpha(Color.BLACK, 40)
    c.drawOval(x - s, y - s * 0.1f, x + s, y + s * 0.35f, fill)
    fill.color = col
    c.drawArc(x - s, y - s, x + s, y + s, 180f, 180f, true, fill)
    line.strokeWidth = max(0.8f, s * 0.07f)
    line.color = alpha(darken(col, 0.35f), 150)
    for (i in 0..4) {
        val a = 3.1416f + 3.1416f * i / 4f
        c.drawLine(x, y, x + cos(a) * s * 0.95f, y + sin(a) * s * 0.95f, line)
    }
    fill.color = darken(col, 0.15f)
    c.drawRect(x - s * 0.22f, y - s * 0.02f, x + s * 0.22f, y + s * 0.18f, fill)
    c.restore()
}

private fun PaintKit.starfish(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
    val p = Path()
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.42f
        val a = -1.5708f + i * 0.6283f
        val px = x + cos(a) * rad
        val py = y + sin(a) * rad * 0.55f
        if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
    }
    p.close()
    fill.color = alpha(Color.BLACK, 40)
    c.drawOval(x - r, y + r * 0.1f, x + r * 1.05f, y + r * 0.5f, fill)
    fill.color = col
    c.drawPath(p, fill)
    fill.color = lighten(col, 0.3f)
    for (i in 0 until 5) {
        val a = -1.5708f + i * 1.2566f
        c.drawCircle(x + cos(a) * r * 0.6f, y + sin(a) * r * 0.6f * 0.55f, max(0.8f, r * 0.07f), fill)
    }
}

private fun PaintKit.driftwood(c: Canvas, x: Float, y: Float, len: Float, thick: Float) {
    c.save()
    c.rotate(-6f, x, y)
    fill.color = alpha(Color.BLACK, 45)
    c.drawOval(x - len * 0.52f, y + thick * 0.35f, x + len * 0.52f, y + thick * 0.95f, fill)
    val body = Path()
    body.moveTo(x - len / 2f, y - thick * 0.3f)
    body.quadTo(x - len * 0.1f, y - thick * 0.75f, x + len / 2f, y - thick * 0.45f)
    body.lineTo(x + len / 2f + thick * 0.1f, y + thick * 0.15f)
    body.quadTo(x + len * 0.1f, y + thick * 0.6f, x - len / 2f, y + thick * 0.35f)
    body.close()
    val base = l.ground(0xFF9A8870.toInt())
    shader = LinearGradient(0f, y - thick * 0.7f, 0f, y + thick * 0.6f, lighten(base, 0.18f), darken(base, 0.35f), Shader.TileMode.CLAMP)
    c.drawPath(body, fill)
    shader = null
    line.strokeWidth = max(1f, thick * 0.07f)
    line.color = alpha(darken(base, 0.5f), 120)
    for (i in 0 until 5) {
        val f = -0.25f + i * 0.17f
        c.drawLine(x - len * 0.46f, y + thick * f, x + len * 0.46f, y + thick * (f - 0.08f), line)
    }
    fill.color = alpha(darken(base, 0.55f), 150)
    c.drawOval(x - len * 0.12f, y - thick * 0.25f, x - len * 0.12f + thick * 0.55f, y + thick * 0.15f, fill)
    line.strokeWidth = max(1.2f, thick * 0.18f)
    line.color = darken(base, 0.2f)
    c.drawLine(x + len * 0.2f, y - thick * 0.55f, x + len * 0.26f, y - thick * 1.2f, line)
    c.restore()
}

/** A clump of beach grass: long arching blades, dry and pale at the tips. */
private fun PaintKit.duneTuft(c: Canvas, x: Float, baseY: Float, hgt: Float, rnd: Random) {
    val light = l.foliage(0xFFC5C176.toInt())
    val mid = l.foliage(0xFF93A653.toInt())
    val dark = l.foliage(0xFF66793C.toInt())
    fill.color = alpha(Color.BLACK, 32)
    c.drawOval(x - hgt * 0.4f, baseY - hgt * 0.04f, x + hgt * 0.45f, baseY + hgt * 0.07f, fill)
    repeat(18) {
        val a = (rnd.nextFloat() - 0.5f) * 2f
        val bh = hgt * (0.55f + 0.5f * rnd.nextFloat())
        val bx = x + (rnd.nextFloat() - 0.5f) * 6f * unit
        val tipX = x + a * bh * 0.85f
        val tipY = baseY - bh * (1f - 0.35f * abs(a))
        val ctrlX = x + a * bh * 0.25f
        val ctrlY = baseY - bh * 0.8f
        line.strokeWidth = (1.4f + rnd.nextFloat() * 1.6f) * unit
        val t = rnd.nextFloat()
        var ax = bx; var ay = baseY
        val segs = 5
        for (s in 1..segs) {
            val u = s / segs.toFloat(); val iu = 1f - u
            val nx = iu * iu * bx + 2f * iu * u * ctrlX + u * u * tipX
            val ny = iu * iu * baseY + 2f * iu * u * ctrlY + u * u * tipY
            // Darker at the root, paler towards the tip.
            line.color = mix(mix(dark, mid, t), light, u * u)
            c.drawLine(ax, ay, nx, ny, line)
            ax = nx; ay = ny
        }
    }
}
