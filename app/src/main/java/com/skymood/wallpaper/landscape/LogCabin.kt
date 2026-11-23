package com.skymood.wallpaper.landscape

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.random.Random

/** Where things are on a painted cabin, in screen pixels: the chimney's mouth and the foot of its steps. */
internal class CabinSpot(val smokeX: Float, val smokeY: Float, val doorX: Float, val doorY: Float)

/**
 * A log cabin seen three-quarter on: a long front wall of round logs with notched corners, a narrow
 * shaded side wall under a plank gable, a shingled roof with a stone chimney, a door under a small
 * porch roof, shuttered windows (one with a flower box) and a lantern. A woodpile stands at one end and
 * a chopping stump with an axe at the other. Its windows and lantern glow whenever the scene is lit.
 *
 * The cabin is built facing the sun: the shaded side wall is always the one away from the light.
 * [cx] is the middle of the front wall, [base] where it meets the ground and [cw] its width.
 */
internal fun PaintKit.logCabin(c: Canvas, cx: Float, base: Float, cw: Float, rnd: Random): CabinSpot {
    // +1 when the side wall is on the right (sun on the left). Everything below is drawn for +1 and mirrored.
    val s = if (sunOnLeft) 1f else -1f
    val hw = cw / 2f
    val wh = cw * 0.46f
    val dx = cw * 0.17f
    val dy = dx * 0.3f
    val fnd = cw * 0.035f
    val courses = 9
    val logD = wh / courses
    val rh = cw * 0.19f
    val ov = cw * 0.06f
    val oy = logD * 0.5f
    val eaveY = -fnd - wh
    val ridgeY = eaveY - dy * 0.5f - rh
    val logBase = l.shade(0xFF9A6638.toInt())
    val wood = l.shade(0xFFC99A62.toInt())
    val roofCol = l.ground(0xFF5C3B2B.toInt())
    val glow = l.windowsLit
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val warm = 0xFFFFC866.toInt()

    c.save()
    c.translate(cx, base)
    c.scale(s, 1f)

    // Contact shadow thrown away from the sun, and the light of the windows spilling onto the grass.
    fill.color = alpha(Color.BLACK, 70)
    c.drawOval(-hw * 0.9f, -cw * 0.03f, hw + dx + cw * 0.14f, cw * 0.05f, fill)
    if (glow) {
        c.save(); c.translate(0f, cw * 0.05f); c.scale(1f, 0.2f)
        shader = RadialGradient(0f, 0f, cw * 0.4f, alpha(warm, 85), 0, Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, cw * 0.4f, fill)
        shader = null
        c.restore()
    }

    // Stone footing.
    fill.color = l.ground(0xFF77746C.toInt())
    c.drawRect(-hw - cw * 0.004f, -fnd, hw, 0f, fill)
    fill.color = darken(l.ground(0xFF77746C.toInt()), 0.4f)
    val foot = Path().apply { moveTo(hw, 0f); lineTo(hw + dx, -dy); lineTo(hw + dx, -dy - fnd); lineTo(hw, -fnd); close() }
    c.drawPath(foot, fill)
    repeat(16) {
        val sx = -hw + rnd.nextFloat() * cw
        fill.color = alpha(if (rnd.nextBoolean()) Color.BLACK else Color.WHITE, 38)
        c.drawOval(sx, -fnd * (0.2f + 0.7f * rnd.nextFloat()), sx + cw * 0.03f, -fnd * 0.1f, fill)
    }

    // Side wall (in shade), its log courses and the plank gable above it.
    val side = Path().apply { moveTo(hw, -fnd); lineTo(hw + dx, -fnd - dy); lineTo(hw + dx, eaveY - dy); lineTo(hw, eaveY); close() }
    fill.color = darken(logBase, 0.5f)
    c.drawPath(side, fill)
    line.strokeWidth = max(1f, 1.3f * unit)
    for (i in 1 until courses) {
        val y = -fnd - i * logD
        line.color = alpha(Color.BLACK, 120)
        c.drawLine(hw, y, hw + dx, y - dy, line)
        line.color = alpha(lighten(logBase, 0.2f), 45)
        c.drawLine(hw, y - logD * 0.3f, hw + dx, y - dy - logD * 0.3f, line)
    }
    val apexX = hw + dx * 0.5f
    val gable = Path().apply { moveTo(hw, eaveY); lineTo(hw + dx, eaveY - dy); lineTo(apexX, ridgeY); close() }
    fill.color = darken(logBase, 0.58f)
    c.drawPath(gable, fill)
    line.color = alpha(Color.BLACK, 110)
    for (k in 1..4) {
        val x = hw + dx * k / 5f
        val yb = eaveY - dy * k / 5f
        val yt = if (x < apexX) eaveY + (ridgeY - eaveY) * ((x - hw) / (dx * 0.5f))
        else (eaveY - dy) + (ridgeY - (eaveY - dy)) * ((hw + dx - x) / (dx * 0.5f))
        c.drawLine(x, yb, x, yt, line)
    }
    line.strokeWidth = max(1.6f, 2.6f * unit)
    line.color = lighten(wood, 0.1f)
    c.drawLine(hw + dx, eaveY - dy, apexX, ridgeY, line)

    // Front wall: courses of round logs, each lit on top and dark underneath.
    for (i in 0 until courses) {
        val y1 = -fnd - i * logD
        val y0 = y1 - logD
        val col = jitter(logBase, rnd, 0.14f)
        shader = LinearGradient(0f, y0, 0f, y1, lighten(col, 0.2f), darken(col, 0.38f), Shader.TileMode.CLAMP)
        c.drawRoundRect(-hw, y0 + logD * 0.03f, hw, y1, logD * 0.4f, logD * 0.4f, fill)
        shader = null
        line.strokeWidth = max(1f, 1.1f * unit)
        repeat(6) {
            val gx = -hw + rnd.nextFloat() * cw * 0.9f
            val gy = y0 + logD * (0.25f + 0.5f * rnd.nextFloat())
            line.color = alpha(darken(col, 0.5f), 70)
            c.drawLine(gx, gy, gx + cw * (0.04f + 0.1f * rnd.nextFloat()), gy, line)
        }
        if (rnd.nextFloat() < 0.18f) {
            val kx = -hw + rnd.nextFloat() * cw * 0.9f
            fill.color = alpha(darken(col, 0.6f), 150)
            c.drawOval(kx - logD * 0.12f, y0 + logD * 0.43f, kx + logD * 0.12f, y0 + logD * 0.57f, fill)
        }
        line.strokeWidth = max(1f, 1.4f * unit)
        line.color = alpha(Color.BLACK, 100)
        c.drawLine(-hw, y0 + logD * 0.02f, hw, y0 + logD * 0.02f, line)
    }
    // Light falls off across the wall, away from the sun, and the eaves shade the top courses.
    shader = LinearGradient(-hw, 0f, hw, 0f, alpha(Color.WHITE, 28), alpha(Color.BLACK, 38), Shader.TileMode.CLAMP)
    c.drawRect(-hw, eaveY, hw, -fnd, fill)
    shader = LinearGradient(0f, eaveY, 0f, eaveY + wh * 0.26f, alpha(Color.BLACK, 125), 0, Shader.TileMode.CLAMP)
    c.drawRect(-hw, eaveY, hw, eaveY + wh * 0.26f, fill)
    shader = null

    // Notched corners: the log ends poking out, alternately long and short.
    fun endGrain(x: Float, y: Float, r: Float) {
        fill.color = jitter(wood, rnd, 0.12f)
        c.drawOval(x - r * 0.55f, y - r, x + r * 0.55f, y + r, fill)
        ring.strokeWidth = max(1f, r * 0.12f)
        ring.color = alpha(darken(wood, 0.5f), 170)
        c.drawOval(x - r * 0.3f, y - r * 0.55f, x + r * 0.3f, y + r * 0.55f, ring)
        c.drawOval(x - r * 0.55f, y - r, x + r * 0.55f, y + r, ring)
    }
    for (i in 0 until courses) {
        val yc = -fnd - (i + 0.5f) * logD
        val r = logD * 0.48f
        val out = if (i % 2 == 0) r * 0.55f else r * 0.15f
        endGrain(-hw - out, yc, r)
        endGrain(hw + (r * 0.7f - out), yc, r)
    }

    // The door.
    val dw = cw * 0.115f
    val dh = wh * 0.74f
    val doorTop = -fnd - dh
    fill.color = darken(wood, 0.62f)
    c.drawRect(-dw / 2f - cw * 0.008f, doorTop - cw * 0.008f, dw / 2f + cw * 0.008f, -fnd, fill)
    fill.color = darken(wood, 0.45f)
    c.drawRect(-dw / 2f, doorTop, dw / 2f, -fnd, fill)
    line.strokeWidth = max(1f, 1.2f * unit)
    line.color = alpha(Color.BLACK, 95)
    for (k in 1..3) c.drawLine(-dw / 2f + dw * k / 4f, doorTop, -dw / 2f + dw * k / 4f, -fnd, line)
    line.strokeWidth = max(1.6f, 2.2f * unit)
    line.color = darken(wood, 0.7f)
    c.drawLine(-dw / 2f, doorTop + dh * 0.22f, dw / 2f, doorTop + dh * 0.22f, line)
    c.drawLine(-dw / 2f, doorTop + dh * 0.78f, dw / 2f, doorTop + dh * 0.78f, line)
    fill.color = l.shade(0xFFD8C27A.toInt())
    c.drawCircle(dw * 0.3f, doorTop + dh * 0.55f, max(1.4f, cw * 0.007f), fill)

    // Windows with shutters; the right one has a flower box.
    val ws = cw * 0.15f
    val wy = -fnd - wh * 0.56f
    val shutter = l.shade(0xFF3F6B52.toInt())
    val frame = l.shade(0xFFE8DFC8.toInt())
    for ((n, wx) in floatArrayOf(-cw * 0.33f, cw * 0.33f).withIndex()) {
        val x0 = wx - ws / 2f; val x1 = wx + ws / 2f
        val y0 = wy - ws / 2f; val y1 = wy + ws / 2f
        fill.color = frame
        c.drawRect(x0 - cw * 0.01f, y0 - cw * 0.01f, x1 + cw * 0.01f, y1 + cw * 0.01f, fill)
        if (glow) {
            shader = LinearGradient(0f, y0, 0f, y1, 0xFFFFE08A.toInt(), 0xFFF2A94A.toInt(), Shader.TileMode.CLAMP)
        } else {
            shader = LinearGradient(0f, y0, 0f, y1, mix(l.skyBottom, 0xFF8EB0C8.toInt(), 0.5f), l.shade(0xFF3F566A.toInt()), Shader.TileMode.CLAMP)
        }
        c.drawRect(x0, y0, x1, y1, fill)
        shader = null
        line.strokeWidth = max(1.4f, 2f * unit)
        line.color = frame
        c.drawLine(wx, y0, wx, y1, line)
        c.drawLine(x0, wy, x1, wy, line)
        // Shutters either side, with louvres.
        for (side2 in intArrayOf(-1, 1)) {
            val sx0 = if (side2 < 0) x0 - ws * 0.46f else x1
            fill.color = shutter
            c.drawRect(sx0, y0 - cw * 0.005f, sx0 + ws * 0.46f, y1 + cw * 0.005f, fill)
            line.strokeWidth = max(1f, 1.1f * unit)
            line.color = alpha(Color.BLACK, 70)
            var ly = y0 + ws * 0.08f
            while (ly < y1) { c.drawLine(sx0, ly, sx0 + ws * 0.46f, ly, line); ly += ws * 0.1f }
        }
        if (n == 1) {
            val by = y1 + cw * 0.012f
            fill.color = darken(wood, 0.5f)
            c.drawRect(x0 - cw * 0.012f, by, x1 + cw * 0.012f, by + cw * 0.026f, fill)
            if (!l.snow) {
                val petals = intArrayOf(0xFFE8465A.toInt(), 0xFFF4C9D4.toInt(), 0xFFF2D21E.toInt(), 0xFFFFFFFF.toInt())
                fill.color = l.foliage(0xFF3F7A33.toInt())
                c.drawRect(x0, by - cw * 0.012f, x1, by + cw * 0.004f, fill)
                repeat(9) {
                    fill.color = l.shade(petals[rnd.nextInt(petals.size)])
                    c.drawCircle(x0 + ws * rnd.nextFloat(), by - cw * 0.012f - rnd.nextFloat() * cw * 0.012f, max(1.2f, cw * 0.007f), fill)
                }
            }
        }
        if (glow) {
            shader = RadialGradient(wx, wy, ws * 2.6f, alpha(warm, 110), 0, Shader.TileMode.CLAMP)
            c.drawCircle(wx, wy, ws * 2.6f, fill)
            shader = null
        }
    }

    // Porch: a plank deck with steps, two posts and a small sloping roof over the door.
    val deckHalf = cw * 0.15f
    fill.color = lighten(wood, 0.04f)
    c.drawRect(-deckHalf, -fnd * 0.6f, deckHalf, cw * 0.03f, fill)
    fill.color = darken(wood, 0.35f)
    c.drawRect(-deckHalf, cw * 0.03f, deckHalf, cw * 0.043f, fill)
    line.strokeWidth = max(1f, 1.1f * unit)
    line.color = alpha(Color.BLACK, 80)
    for (k in 1..2) c.drawLine(-deckHalf, cw * 0.03f * k / 3f, deckHalf, cw * 0.03f * k / 3f, line)
    for (k in 0..1) {
        val sw = cw * (0.085f + 0.025f * k)
        val sy = cw * (0.043f + 0.02f * k)
        fill.color = if (k == 0) lighten(wood, 0.02f) else darken(wood, 0.12f)
        c.drawRect(-sw, sy, sw, sy + cw * 0.02f, fill)
    }
    val beamY = -fnd - wh * 0.8f
    val postTop = beamY
    for (px in floatArrayOf(-cw * 0.135f, cw * 0.135f)) {
        fill.color = darken(wood, 0.25f)
        c.drawRect(px - cw * 0.009f, postTop, px + cw * 0.009f, cw * 0.03f, fill)
        fill.color = alpha(Color.WHITE, 40)
        c.drawRect(px - cw * 0.009f, postTop, px - cw * 0.004f, cw * 0.03f, fill)
    }
    fill.color = darken(wood, 0.3f)
    c.drawRect(-cw * 0.15f, beamY, cw * 0.15f, beamY + cw * 0.016f, fill)
    val porchRoof = Path().apply {
        moveTo(-cw * 0.19f, beamY); lineTo(cw * 0.19f, beamY)
        lineTo(cw * 0.15f, -fnd - wh * 0.93f); lineTo(-cw * 0.15f, -fnd - wh * 0.93f); close()
    }
    shader = LinearGradient(0f, -fnd - wh * 0.93f, 0f, beamY, lighten(roofCol, 0.1f), darken(roofCol, 0.15f), Shader.TileMode.CLAMP)
    c.drawPath(porchRoof, fill)
    shader = null
    line.strokeWidth = max(1.6f, 2.4f * unit)
    line.color = darken(roofCol, 0.5f)
    c.drawLine(-cw * 0.19f, beamY, cw * 0.19f, beamY, line)
    // The lantern hanging from the beam.
    val lx = -cw * 0.1f
    line.strokeWidth = max(1f, 1.2f * unit)
    line.color = alpha(Color.BLACK, 150)
    c.drawLine(lx, beamY + cw * 0.016f, lx, beamY + cw * 0.04f, line)
    fill.color = if (glow) 0xFFFFE08A.toInt() else l.shade(0xFFB8B09A.toInt())
    c.drawRect(lx - cw * 0.008f, beamY + cw * 0.04f, lx + cw * 0.008f, beamY + cw * 0.062f, fill)
    if (glow) {
        shader = RadialGradient(lx, beamY + cw * 0.05f, cw * 0.09f, alpha(warm, 130), 0, Shader.TileMode.CLAMP)
        c.drawCircle(lx, beamY + cw * 0.05f, cw * 0.09f, fill)
        shader = null
    }

    // Roof: the near slope in rows of shingles, a fascia along the eave and a cap along the ridge.
    val ridgeL = -hw + dx * 0.5f - ov * 0.4f
    val ridgeR = hw + dx * 0.5f + ov * 0.4f
    val eaveL = -hw - ov
    val eaveR = hw + ov
    val eY = eaveY + oy
    val roof = Path().apply { moveTo(eaveL, eY); lineTo(eaveR, eY); lineTo(ridgeR, ridgeY); lineTo(ridgeL, ridgeY); close() }
    shader = LinearGradient(0f, ridgeY, 0f, eY, lighten(roofCol, 0.14f), darken(roofCol, 0.2f), Shader.TileMode.CLAMP)
    c.drawPath(roof, fill)
    shader = null
    val rows = 8
    line.strokeWidth = max(1f, 1.2f * unit)
    for (r in 1 until rows) {
        val t = r / rows.toFloat()
        val y = ridgeY + (eY - ridgeY) * t
        val xl = ridgeL + (eaveL - ridgeL) * t
        val xr = ridgeR + (eaveR - ridgeR) * t
        line.color = alpha(Color.BLACK, 85)
        c.drawLine(xl, y, xr, y, line)
        val yn = ridgeY + (eY - ridgeY) * ((r + 1) / rows.toFloat())
        line.color = alpha(Color.BLACK, 45)
        var x = xl + (if (r % 2 == 0) 0f else cw * 0.025f)
        while (x < xr) { c.drawLine(x, y, x, yn, line); x += cw * 0.05f }
    }
    line.strokeWidth = max(2f, 3.2f * unit)
    line.color = darken(roofCol, 0.55f)
    c.drawLine(eaveL, eY, eaveR, eY, line)
    line.color = darken(roofCol, 0.35f)
    c.drawLine(ridgeL, ridgeY, ridgeR, ridgeY, line)
    line.color = alpha(lighten(roofCol, 0.35f), 120)
    c.drawLine(eaveL, eY, ridgeL, ridgeY, line)

    // Stone chimney on the roof, with a cap and a dark flue.
    val chx = -hw * 0.62f
    val chw = cw * 0.04f
    val chb = ridgeY + (eY - ridgeY) * 0.62f
    val cht = ridgeY - cw * 0.085f
    val stone = l.ground(0xFF8A857C.toInt())
    shader = LinearGradient(chx - chw, 0f, chx + chw, 0f, lighten(stone, 0.1f), darken(stone, 0.35f), Shader.TileMode.CLAMP)
    c.drawRect(chx - chw, cht, chx + chw, chb, fill)
    shader = null
    var sy = cht
    line.strokeWidth = max(1f, 1.2f * unit)
    while (sy < chb) {
        line.color = alpha(Color.BLACK, 70)
        c.drawLine(chx - chw, sy, chx + chw, sy, line)
        val jx = chx - chw + chw * 2f * rnd.nextFloat()
        c.drawLine(jx, sy, jx, sy + cw * 0.02f, line)
        sy += cw * 0.02f
    }
    fill.color = darken(stone, 0.25f)
    c.drawRect(chx - chw * 1.25f, cht - cw * 0.012f, chx + chw * 1.25f, cht + cw * 0.002f, fill)
    fill.color = alpha(Color.BLACK, 190)
    c.drawOval(chx - chw * 0.7f, cht - cw * 0.016f, chx + chw * 0.7f, cht - cw * 0.006f, fill)

    // Woodpile at the sun end: stacked log ends between two stakes.
    val wpx1 = -hw - cw * 0.05f
    val wpx0 = wpx1 - cw * 0.2f
    val lr = cw * 0.0175f
    fill.color = alpha(Color.BLACK, 120)
    c.drawRect(wpx0 - cw * 0.006f, -lr * 2f * 5f, wpx1 + cw * 0.006f, 0f, fill)
    for (row in 0 until 5) {
        var x = wpx0 + lr + (row % 2) * lr
        while (x < wpx1 - lr * 0.5f) {
            endGrain(x, -lr - row * lr * 1.9f, lr)
            x += lr * 2f
        }
    }
    fill.color = darken(wood, 0.5f)
    c.drawRect(wpx0 - cw * 0.012f, -lr * 2f * 5.2f, wpx0 - cw * 0.004f, 0f, fill)
    c.drawRect(wpx1 + cw * 0.004f, -lr * 2f * 5.2f, wpx1 + cw * 0.012f, 0f, fill)
    if (l.snow) {
        fill.color = l.shade(Lighting.SNOW)
        c.drawOval(wpx0, -lr * 2f * 5.1f, wpx1, -lr * 2f * 4.55f, fill)
    }

    // A chopping stump with the axe left in it, beside the shaded wall.
    val stx = hw + dx + cw * 0.11f
    val sty = -dy * 0.4f + cw * 0.012f
    val sr = cw * 0.026f
    fill.color = alpha(Color.BLACK, 70)
    c.drawOval(stx - sr * 1.2f, sty - sr * 0.15f, stx + sr * 1.9f, sty + sr * 0.35f, fill)
    shader = LinearGradient(stx - sr, 0f, stx + sr, 0f, l.shade(0xFF6E4A2E.toInt()), l.shade(0xFF3A271A.toInt()), Shader.TileMode.CLAMP)
    c.drawRect(stx - sr, sty - sr * 1.2f, stx + sr, sty, fill)
    shader = null
    fill.color = lighten(wood, 0.1f)
    c.drawOval(stx - sr, sty - sr * 1.55f, stx + sr, sty - sr * 0.85f, fill)
    ring.strokeWidth = max(1f, unit)
    ring.color = alpha(darken(wood, 0.5f), 150)
    c.drawOval(stx - sr * 0.55f, sty - sr * 1.4f, stx + sr * 0.55f, sty - sr * 1f, ring)
    line.strokeWidth = max(1.8f, 2.6f * unit)
    line.color = l.shade(0xFF7A5232.toInt())
    c.drawLine(stx + sr * 0.2f, sty - sr * 1.2f, stx + sr * 1.9f, sty - sr * 3.6f, line)
    fill.color = l.shade(0xFF50565C.toInt())
    val head = Path().apply {
        moveTo(stx + sr * 1.8f, sty - sr * 3.55f); lineTo(stx + sr * 2.5f, sty - sr * 3.25f)
        lineTo(stx + sr * 2.15f, sty - sr * 3.85f); close()
    }
    c.drawPath(head, fill)

    c.restore()
    val smoke = cabinSmokePoint(cx, base, cw, sunOnLeft)
    return CabinSpot(smoke[0], smoke[1], cx, base + cw * 0.075f)
}

/**
 * Where smoke leaves the chimney of a cabin drawn by [logCabin] with the same arguments, as x and y in
 * screen pixels. The animation needs it on the main thread, long before the painter hands anything back.
 */
internal fun cabinSmokePoint(cx: Float, base: Float, cw: Float, sunOnLeft: Boolean): FloatArray {
    val s = if (sunOnLeft) 1f else -1f
    // Chimney centre is 0.31 cw towards the sun from the middle; its top is 0.8115 cw above the ground.
    return floatArrayOf(cx - s * cw * 0.31f, base - cw * 0.8115f)
}
