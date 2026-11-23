package com.skymood.wallpaper.landscape

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.hypot
import kotlin.math.max

/*
 * The things people leave on the beach. Each is a small painted figure lit from the sun's side
 * (PaintKit.sun: -1 is the left), with a shadow cast the other way. Props are drawn with fills only
 * (the kit's line paint is a fill-style paint, so never hand a Path to it) and everything runs on
 * the painter thread.
 */

/** Everything on the sand, back to front. */
internal fun PaintKit.beachProps(c: Canvas) {
    val u = unit
    val sx = -sun
    val uy = h * 0.865f

    // Built beyond the reach of the waves (the swash runs up to about 0.024 * h past the shore).
    sandcastle(c, w * 0.17f, shoreEdge(w * 0.17f, w, h) + 70f * u, sx)
    bucketAndSpade(c, w * 0.17f + 58f * u, shoreEdge(w * 0.17f, w, h) + 72f * u, sx)

    val ux = w * 0.33f
    // The umbrella's shade falls on the loungers, so it goes under them.
    castShadow(c, ux + sx * 28f * u, uy + 112f * u, 78f * u, 9f * u, 0f, 60)

    beachBag(c, w * 0.215f, uy + 72f * u, sx)
    lounger(c, w * 0.27f, uy + 70f * u, 64f * u, l.shade(0xFF00A896.toInt()), sx, sitting = false)
    lounger(c, w * 0.39f, uy + 72f * u, 64f * u, l.shade(0xFFFF6B6B.toInt()), sx, sitting = true)
    umbrella(c, ux, uy, sx)
    sideTable(c, w * 0.445f, uy + 66f * u, sx)

    towel(c, w * 0.525f, uy + 80f * u, sx)
    beachBall(c, w * 0.59f, uy + 84f * u, sx)
    walker(c, w * 0.62f, shoreEdge(w * 0.62f, w, h) + 66f * u, sx)
    surfboard(c, w * 0.68f, uy + 56f * u, sx)
}

private fun PaintKit.shadowAlpha(a: Int) = if (night) (a * 0.35f).toInt() else a

private fun PaintKit.castShadow(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, dx: Float, a: Int = 55) {
    fill.color = alpha(Color.BLACK, shadowAlpha(a))
    c.drawOval(x + dx - rx, y - ry, x + dx + rx, y + ry, fill)
}

/** A gradient across [x0]..[x1] that is lit on the sun's side and dark on the far side. */
private fun PaintKit.sideShader(x0: Float, x1: Float, base: Int): Shader =
    if (sunOnLeft) LinearGradient(x0, 0f, x1, 0f, lighten(base, 0.2f), darken(base, 0.3f), Shader.TileMode.CLAMP)
    else LinearGradient(x0, 0f, x1, 0f, darken(base, 0.3f), lighten(base, 0.2f), Shader.TileMode.CLAMP)

private fun PaintKit.limb(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, wdt: Float, col: Int) {
    line.strokeWidth = wdt; line.color = col
    c.drawLine(x0, y0, x1, y1, line)
}

/** The part of a limb from fraction [a] to [b] of its length, for clothes on a body. */
private fun PaintKit.limbPart(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, a: Float, b: Float, wdt: Float, col: Int) {
    limb(c, x0 + (x1 - x0) * a, y0 + (y1 - y0) * a, x0 + (x1 - x0) * b, y0 + (y1 - y0) * b, wdt, col)
}

private fun PaintKit.stripedBar(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, wdt: Float, base: Int, stripe: Int, every: Float) {
    limb(c, x0, y0, x1, y1, wdt, base)
    val dx = x1 - x0; val dy = y1 - y0
    val len = hypot(dx, dy).coerceAtLeast(1f)
    val n = (len / every).toInt().coerceAtLeast(1)
    val nx = -dy / len; val ny = dx / len
    val sw = every * 0.45f
    val half = max(0.5f, wdt * 0.5f - sw * 0.5f)
    line.strokeWidth = sw; line.color = stripe
    var i = 0
    while (i < n) {
        val t = (i + 0.5f) / n
        val px = x0 + dx * t; val py = y0 + dy * t
        c.drawLine(px - nx * half, py - ny * half, px + nx * half, py + ny * half, line)
        i += 2
    }
}

private fun PaintKit.outline(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    this.color = color; strokeWidth = width
}

// ------------------------------------------------------------------------------------ sandcastle

private fun PaintKit.sandcastle(c: Canvas, x: Float, y: Float, sx: Float) {
    val u = unit
    val sand = l.ground(0xFFD6B77F.toInt())
    // The dug-out moat, dark and wet, and the long shadow.
    fill.color = alpha(l.ground(0xFF5E4A30.toInt()), 120)
    c.drawOval(x - 48f * u, y - 2f * u, x + 48f * u, y + 12f * u, fill)
    castShadow(c, x, y + 1f * u, 40f * u, 5f * u, sx * 26f * u)

    fun block(x0: Float, x1: Float, top: Float) {
        shader = sideShader(x0, x1, sand)
        c.drawRect(x0, top, x1, y, fill)
        shader = null
    }
    fun merlons(x0: Float, x1: Float, top: Float, n: Int) {
        val mw = (x1 - x0) / (2f * n - 1f)
        fill.color = darken(sand, 0.06f)
        for (i in 0 until n) c.drawRect(x0 + i * 2f * mw, top - 4.5f * u, x0 + i * 2f * mw + mw, top + 0.5f, fill)
    }
    // Curtain wall with a gate.
    block(x - 32f * u, x + 32f * u, y - 20f * u)
    merlons(x - 32f * u, x + 32f * u, y - 20f * u, 7)
    fill.color = darken(sand, 0.55f)
    val gate = Path()
    gate.moveTo(x - 5f * u, y); gate.lineTo(x - 5f * u, y - 9f * u)
    gate.quadTo(x, y - 17f * u, x + 5f * u, y - 9f * u); gate.lineTo(x + 5f * u, y)
    gate.close()
    c.drawPath(gate, fill)
    // Corner towers.
    for (side in intArrayOf(-1, 1)) {
        val tx = x + side * 36f * u
        block(tx - 8f * u, tx + 8f * u, y - 33f * u)
        merlons(tx - 8f * u, tx + 8f * u, y - 33f * u, 3)
        fill.color = darken(sand, 0.6f)
        c.drawRect(tx - 1.2f * u, y - 25f * u, tx + 1.2f * u, y - 20f * u, fill)
    }
    // The keep, with a conical roof and a flag.
    block(x - 11f * u, x + 11f * u, y - 46f * u)
    shader = sideShader(x - 13f * u, x + 13f * u, darken(sand, 0.05f))
    val roof = Path()
    roof.moveTo(x - 13f * u, y - 46f * u); roof.lineTo(x, y - 64f * u); roof.lineTo(x + 13f * u, y - 46f * u); roof.close()
    c.drawPath(roof, fill)
    shader = null
    fill.color = darken(sand, 0.6f)
    c.drawRect(x - 1.4f * u, y - 38f * u, x + 1.4f * u, y - 31f * u, fill)
    line.strokeWidth = 1.2f * u; line.color = l.shade(0xFF5B4636.toInt())
    c.drawLine(x, y - 64f * u, x, y - 76f * u, line)
    fill.color = l.shade(0xFFE74C3C.toInt())
    val flag = Path()
    flag.moveTo(x, y - 76f * u); flag.lineTo(x + 11f * u, y - 72f * u); flag.lineTo(x, y - 68f * u); flag.close()
    c.drawPath(flag, fill)
    // Shells pressed into the wall.
    fill.color = l.ground(0xFFF3E6D0.toInt())
    for (i in -2..2) c.drawCircle(x + i * 10f * u, y - 3.5f * u + (if (i % 2 == 0) 0f else -4f * u), 1.4f * u, fill)
}

private fun PaintKit.bucketAndSpade(c: Canvas, x: Float, y: Float, sx: Float) {
    val u = unit
    castShadow(c, x, y + 1f * u, 9f * u, 2.5f * u, sx * 8f * u)
    val bucket = Path()
    bucket.moveTo(x - 8f * u, y - 13f * u); bucket.lineTo(x + 8f * u, y - 13f * u)
    bucket.lineTo(x + 6f * u, y); bucket.lineTo(x - 6f * u, y); bucket.close()
    shader = sideShader(x - 8f * u, x + 8f * u, l.shade(0xFF2E86DE.toInt()))
    c.drawPath(bucket, fill)
    shader = null
    fill.color = l.shade(0xFFFFD43B.toInt())
    c.drawRect(x - 8.5f * u, y - 14.5f * u, x + 8.5f * u, y - 12f * u, fill)
    line.strokeWidth = 1.2f * u; line.color = darken(l.shade(0xFF2E86DE.toInt()), 0.3f)
    c.drawLine(x - 8f * u, y - 13f * u, x - 3f * u, y - 22f * u, line)
    c.drawLine(x - 3f * u, y - 22f * u, x + 8f * u, y - 13f * u, line)
    // A spade stuck in the sand.
    val sp = x + 16f * u
    line.strokeWidth = 1.6f * u; line.color = l.shade(0xFFE74C3C.toInt())
    c.drawLine(sp, y + 1f * u, sp + 3f * u, y - 18f * u, line)
    fill.color = l.shade(0xFFE74C3C.toInt())
    c.drawRect(sp + 0.5f * u, y - 23f * u, sp + 6f * u, y - 18f * u, fill)
}

// ------------------------------------------------------------------------------------ umbrella

private fun PaintKit.umbrella(c: Canvas, ux: Float, uy: Float, sx: Float) {
    val u = unit
    val rad = 78f * u
    val dome = 38f * u
    val poleH = 112f * u
    val panels = 8
    val cols = intArrayOf(l.shade(0xFFE8504A.toInt()), l.shade(0xFFF8F4EA.toInt()))

    // Pole, lit on the sun's side.
    limb(c, ux, uy - dome * 0.7f, ux, uy + poleH, 3.4f * u, l.shade(0xFF6B4A32.toInt()))
    limb(c, ux + sun * 0.9f * u, uy, ux + sun * 0.9f * u, uy + poleH, 1.1f * u, l.shade(0xFFA07850.toInt()))
    // Where the pole goes into the sand.
    fill.color = alpha(Color.BLACK, shadowAlpha(60))
    c.drawOval(ux - 6f * u, uy + poleH - 2f * u, ux + 6f * u, uy + poleH + 3f * u, fill)

    // Underside, seen from below the rim.
    val under = Path()
    under.moveTo(ux - rad, uy); under.quadTo(ux, uy + 20f * u, ux + rad, uy); under.close()
    fill.color = darken(cols[0], 0.5f)
    c.drawPath(under, fill)

    // The dome: a fan of panels clipped to a shallow curved outline. Panels are wedges measured by
    // angle from the apex, over the full half-turn, so the rounded shoulders are covered as well.
    val apexY = uy - dome
    val silhouette = Path()
    silhouette.moveTo(ux - rad, uy + 2f * u)
    silhouette.quadTo(ux - rad * 0.62f, apexY + dome * 0.12f, ux, apexY)
    silhouette.quadTo(ux + rad * 0.62f, apexY + dome * 0.12f, ux + rad, uy + 2f * u)
    silhouette.close()
    c.save()
    c.clipPath(silhouette)
    val reach = rad * 3f
    val step = 3.1416f / panels
    val wedge = Path()
    for (i in 0 until panels) {
        val a0 = -1.5708f + i * step; val a1 = a0 + step
        wedge.rewind()
        wedge.moveTo(ux, apexY)
        wedge.lineTo(ux + kotlin.math.sin(a0) * reach, apexY + kotlin.math.cos(a0) * reach)
        wedge.lineTo(ux + kotlin.math.sin(a1) * reach, apexY + kotlin.math.cos(a1) * reach)
        wedge.close()
        fill.color = cols[i % 2]
        c.drawPath(wedge, fill)
    }
    // Light from the sun's side, shade on the other, and a darker lower rim.
    shader = LinearGradient(
        ux - rad, 0f, ux + rad, 0f,
        if (sunOnLeft) alpha(Color.WHITE, 80) else alpha(Color.BLACK, 95),
        if (sunOnLeft) alpha(Color.BLACK, 95) else alpha(Color.WHITE, 80),
        Shader.TileMode.CLAMP,
    )
    c.drawRect(ux - rad, apexY - 2f * u, ux + rad, uy + 6f * u, fill)
    shader = LinearGradient(0f, uy - dome * 0.5f, 0f, uy + 3f * u, alpha(Color.BLACK, 0), alpha(Color.BLACK, 70), Shader.TileMode.CLAMP)
    c.drawRect(ux - rad, uy - dome * 0.5f, ux + rad, uy + 6f * u, fill)
    shader = null
    // Ribs.
    line.strokeWidth = 1.1f * u; line.color = alpha(Color.BLACK, 55)
    for (i in 1 until panels) {
        val a = -1.5708f + i * step
        c.drawLine(ux, apexY, ux + kotlin.math.sin(a) * reach, apexY + kotlin.math.cos(a) * reach, line)
    }
    c.restore()
    // Scalloped hem.
    fill.color = alpha(Color.BLACK, 40)
    val pw = rad * 2f / panels
    for (i in 0 until panels) {
        val mx = ux - rad + (i + 0.5f) * pw
        c.drawOval(mx - pw * 0.5f, uy - 1.5f * u, mx + pw * 0.5f, uy + 3f * u, fill)
    }
    // Finial.
    fill.color = l.shade(0xFF3A2A22.toInt())
    c.drawCircle(ux, apexY + 1.5f * u, 3.2f * u, fill)
    limb(c, ux, apexY + 2f * u, ux, apexY - 6f * u, 1.6f * u, l.shade(0xFF3A2A22.toInt()))
}

// ------------------------------------------------------------------------------------ loungers

private fun PaintKit.lounger(c: Canvas, x: Float, y: Float, len: Float, cloth: Int, sx: Float, sitting: Boolean) {
    val u = unit
    val frame = l.ground(0xFFEDEDED.toInt())
    val stripe = l.shade(0xFFFFFFFF.toInt())
    castShadow(c, x, y + 1f * u, len * 0.5f, len * 0.05f, sx * len * 0.18f)

    // Pivot points of the reclining frame: top of the back, hip, knee and end of the leg rest.
    val p0x = x - len * 0.46f; val p0y = y - len * 0.36f
    val p1x = x - len * 0.12f; val p1y = y - len * 0.13f
    val p2x = x + len * 0.18f; val p2y = y - len * 0.13f
    val p3x = x + len * 0.46f; val p3y = y - len * 0.04f
    // Legs and the prop behind the back.
    limb(c, p1x, p1y, x - len * 0.17f, y, 2f * u, frame)
    limb(c, p2x, p2y, x + len * 0.22f, y, 2f * u, frame)
    limb(c, x - len * 0.3f, y - len * 0.25f, x - len * 0.44f, y, 1.6f * u, darken(frame, 0.12f))
    // Striped fabric over the frame.
    val bar = len * 0.07f
    stripedBar(c, p0x, p0y, p1x, p1y, bar, cloth, stripe, 5f * u)
    stripedBar(c, p1x, p1y, p2x, p2y, bar, cloth, stripe, 5f * u)
    stripedBar(c, p2x, p2y, p3x, p3y, bar, cloth, stripe, 5f * u)
    val skin = l.shade(0xFFEBBF98.toInt())
    val skinShade = darken(skin, 0.12f)

    if (!sitting) {
        // Sunbather lying back: swimsuit top and trunks, sunglasses and a book on the belly.
        val lift = len * 0.075f
        val sx0 = p0x + len * 0.07f; val sy0 = p0y - lift
        // Thighs and shins.
        limb(c, p1x, p1y - lift, p2x, p2y - lift, len * 0.1f, skin)
        limbPart(c, p1x, p1y - lift, p2x, p2y - lift, 0f, 0.5f, len * 0.105f, l.shade(0xFF2E86DE.toInt()))
        limb(c, p2x, p2y - lift, p3x, p3y - lift, len * 0.075f, skinShade)
        fill.color = skin
        c.drawCircle(p3x + len * 0.02f, p3y - lift - len * 0.01f, len * 0.05f, fill)
        // Torso.
        limb(c, sx0, sy0, p1x, p1y - lift, len * 0.13f, skin)
        limbPart(c, sx0, sy0, p1x, p1y - lift, 0.2f, 0.52f, len * 0.135f, l.shade(0xFFEE5253.toInt()))
        limbPart(c, sx0, sy0, p1x, p1y - lift, 0.88f, 1f, len * 0.135f, l.shade(0xFF2E86DE.toInt()))
        // Arm folded across, and the book.
        limb(c, sx0 + len * 0.1f, sy0 - len * 0.04f, p1x - len * 0.02f, p1y - lift - len * 0.07f, len * 0.05f, skinShade)
        fill.color = l.shade(0xFFF1C40F.toInt())
        c.drawRect(p1x - len * 0.1f, p1y - lift - len * 0.13f, p1x + len * 0.05f, p1y - lift - len * 0.07f, fill)
        // Head.
        val hx = p0x - len * 0.01f; val hy = p0y - lift - len * 0.04f
        fill.color = skin
        c.drawCircle(hx, hy, len * 0.082f, fill)
        fill.color = l.shade(0xFF2B1D14.toInt())
        c.drawArc(hx - len * 0.09f, hy - len * 0.09f, hx + len * 0.09f, hy + len * 0.07f, 150f, 210f, true, fill)
        fill.color = l.shade(0xFF111111.toInt())
        c.drawRect(hx + len * 0.01f, hy - len * 0.02f, hx + len * 0.075f, hy + len * 0.012f, fill)
    } else {
        // Sitting up with a straw hat and a coconut drink.
        val hipx = p1x + len * 0.03f; val hipy = p1y - len * 0.07f
        val shx = hipx - len * 0.03f; val shy = hipy - len * 0.3f
        val kneex = p2x + len * 0.04f; val kneey = hipy - len * 0.01f
        // Shin hangs down to the sand.
        limb(c, kneex, kneey, kneex + len * 0.03f, y - len * 0.015f, len * 0.07f, skinShade)
        fill.color = skinShade
        c.drawOval(kneex - len * 0.01f, y - len * 0.045f, kneex + len * 0.1f, y + len * 0.01f, fill)
        limb(c, hipx, hipy, kneex, kneey, len * 0.1f, skin)
        limbPart(c, hipx, hipy, kneex, kneey, 0f, 0.6f, len * 0.108f, l.shade(0xFF00D2D3.toInt()))
        // Torso in a yellow top.
        limb(c, hipx, hipy, shx, shy, len * 0.15f, l.shade(0xFFFCA311.toInt()))
        // Arm reaching forward with the coconut.
        val hx = shx + len * 0.2f; val hy = shy + len * 0.14f
        limb(c, shx, shy + len * 0.03f, hx, hy, len * 0.055f, skin)
        fill.color = l.shade(0xFF5A3A24.toInt())
        c.drawCircle(hx + len * 0.02f, hy - len * 0.02f, len * 0.065f, fill)
        line.strokeWidth = 1.1f * u; line.color = l.shade(0xFFE74C3C.toInt())
        c.drawLine(hx + len * 0.02f, hy - len * 0.04f, hx + len * 0.06f, hy - len * 0.14f, line)
        // Head and a wide straw hat.
        val hdx = shx + len * 0.01f; val hdy = shy - len * 0.11f
        fill.color = skin
        c.drawCircle(hdx, hdy, len * 0.08f, fill)
        fill.color = l.shade(0xFFE6C77A.toInt())
        c.drawOval(hdx - len * 0.17f, hdy - len * 0.075f, hdx + len * 0.17f, hdy - len * 0.02f, fill)
        c.drawArc(hdx - len * 0.09f, hdy - len * 0.15f, hdx + len * 0.09f, hdy - len * 0.03f, 180f, 180f, true, fill)
        fill.color = l.shade(0xFFC0392B.toInt())
        c.drawRect(hdx - len * 0.09f, hdy - len * 0.07f, hdx + len * 0.09f, hdy - len * 0.05f, fill)
    }
}

private fun PaintKit.beachBag(c: Canvas, x: Float, y: Float, sx: Float) {
    val u = unit
    castShadow(c, x, y + 1f * u, 14f * u, 3f * u, sx * 8f * u)
    val body = Path()
    body.moveTo(x - 12f * u, y - 18f * u); body.lineTo(x + 12f * u, y - 18f * u)
    body.lineTo(x + 14f * u, y); body.lineTo(x - 14f * u, y); body.close()
    shader = sideShader(x - 14f * u, x + 14f * u, l.shade(0xFFF2EBDD.toInt()))
    c.drawPath(body, fill)
    shader = null
    fill.color = l.shade(0xFF2E86DE.toInt())
    for (i in 0 until 3) c.drawRect(x - 13f * u, y - 15f * u + i * 6f * u, x + 13f * u, y - 12f * u + i * 6f * u, fill)
    val strap = outline(l.shade(0xFF8D6E4A.toInt()), 1.6f * u)
    val handle = Path()
    handle.moveTo(x - 7f * u, y - 18f * u); handle.quadTo(x, y - 34f * u, x + 7f * u, y - 18f * u)
    c.drawPath(handle, strap)
}

private fun PaintKit.sideTable(c: Canvas, x: Float, y: Float, sx: Float) {
    val u = unit
    castShadow(c, x, y + 1f * u, 12f * u, 3f * u, sx * 8f * u)
    limb(c, x, y - 16f * u, x, y, 2f * u, l.shade(0xFF6D4C3A.toInt()))
    fill.color = l.shade(0xFF8D6A4A.toInt())
    c.drawOval(x - 12f * u, y - 20f * u, x + 12f * u, y - 14f * u, fill)
    fill.color = lighten(l.shade(0xFF8D6A4A.toInt()), 0.25f)
    c.drawOval(x - 12f * u, y - 20f * u, x + 12f * u, y - 17f * u, fill)
    // A coconut with a straw and a tall orange drink.
    fill.color = l.shade(0xFF4E342E.toInt())
    c.drawCircle(x - 4f * u, y - 24f * u, 5.5f * u, fill)
    line.strokeWidth = 1.1f * u; line.color = l.shade(0xFFE74C3C.toInt())
    c.drawLine(x - 4f * u, y - 27f * u, x - 1f * u, y - 36f * u, line)
    fill.color = alpha(l.shade(0xFFFF9F1A.toInt()), 235)
    c.drawRect(x + 4f * u, y - 33f * u, x + 9f * u, y - 19f * u, fill)
    fill.color = l.shade(0xFF27AE60.toInt())
    c.drawCircle(x + 9.5f * u, y - 33f * u, 1.8f * u, fill)
}

// ------------------------------------------------------------------------------------ towel, ball

private fun PaintKit.towel(c: Canvas, x: Float, y: Float, sx: Float) {
    val u = unit
    val tw = 84f * u; val th = 30f * u
    c.save()
    c.rotate(-8f, x, y)
    fill.color = alpha(Color.BLACK, shadowAlpha(45))
    c.drawRect(x - tw / 2f + sx * 2f * u, y - th / 2f + 3f * u, x + tw / 2f + sx * 2f * u, y + th / 2f + 3f * u, fill)
    // Seen from a low angle, the far edge is narrower.
    val cloth = Path()
    cloth.moveTo(x - tw * 0.46f, y - th / 2f); cloth.lineTo(x + tw * 0.46f, y - th / 2f)
    cloth.lineTo(x + tw / 2f, y + th / 2f); cloth.lineTo(x - tw / 2f, y + th / 2f); cloth.close()
    fill.color = l.shade(0xFFFF9F43.toInt())
    c.drawPath(cloth, fill)
    c.save()
    c.clipPath(cloth)
    fill.color = l.shade(0xFFFFFFFF.toInt())
    c.drawRect(x - tw * 0.32f, y - th / 2f, x - tw * 0.2f, y + th / 2f, fill)
    c.drawRect(x + tw * 0.2f, y - th / 2f, x + tw * 0.32f, y + th / 2f, fill)
    fill.color = l.shade(0xFFE67E22.toInt())
    c.drawRect(x - tw * 0.12f, y - th / 2f, x + tw * 0.12f, y + th / 2f, fill)
    c.restore()
    // Fringe at both ends.
    line.strokeWidth = 1f * u; line.color = l.shade(0xFFFFF3E0.toInt())
    for (i in 0..5) {
        val fy = y - th / 2f + 2f * u + i * (th - 4f * u) / 5f
        c.drawLine(x - tw / 2f - 1f * u, fy, x - tw / 2f - 5f * u, fy + 0.5f * u, line)
        c.drawLine(x + tw / 2f + 1f * u, fy, x + tw / 2f + 5f * u, fy + 0.5f * u, line)
    }
    // Sunglasses on the towel.
    val gx = x + tw * 0.02f; val gy = y - 1f * u
    fill.color = l.shade(0xFF111111.toInt())
    c.drawOval(gx - 7f * u, gy - 2.5f * u, gx - 1.5f * u, gy + 2.5f * u, fill)
    c.drawOval(gx + 1.5f * u, gy - 2.5f * u, gx + 7f * u, gy + 2.5f * u, fill)
    line.strokeWidth = 0.9f * u; line.color = l.shade(0xFF111111.toInt())
    c.drawLine(gx - 1.5f * u, gy - 1f * u, gx + 1.5f * u, gy - 1f * u, line)
    c.restore()
    // A pair of flip-flops beside it.
    for (i in 0..1) {
        val fx = x + tw * 0.62f + i * 9f * u; val fy = y + 8f * u + i * 2f * u
        fill.color = l.shade(0xFF16A085.toInt())
        c.drawOval(fx - 3.5f * u, fy - 6f * u, fx + 3.5f * u, fy + 6f * u, fill)
        line.strokeWidth = 1.1f * u; line.color = l.shade(0xFFF8F4EA.toInt())
        c.drawLine(fx, fy - 1f * u, fx - 2.5f * u, fy - 5f * u, line)
        c.drawLine(fx, fy - 1f * u, fx + 2.5f * u, fy - 5f * u, line)
    }
}

private fun PaintKit.beachBall(c: Canvas, x: Float, y: Float, sx: Float) {
    val u = unit
    val r = 16f * u
    castShadow(c, x, y + r * 0.95f, r * 0.95f, r * 0.2f, sx * r * 0.6f)
    c.save()
    val disc = Path()
    disc.addCircle(x, y, r, Path.Direction.CW)
    c.clipPath(disc)
    val cols = intArrayOf(l.shade(0xFFEE5253.toInt()), l.shade(0xFFFFFFFF.toInt()), l.shade(0xFF54A0FF.toInt()), l.shade(0xFFFECA57.toInt()), l.shade(0xFFFFFFFF.toInt()), l.shade(0xFF1DD1A1.toInt()))
    for (i in cols.indices) {
        fill.color = cols[i]
        c.drawArc(x - r, y - r, x + r, y + r, -90f + i * 60f, 60f, true, fill)
    }
    // Round shading: bright patch towards the sun, dark limb away from it.
    shader = RadialGradient(
        x + sun * r * 0.35f, y - r * 0.35f, r * 1.5f,
        intArrayOf(alpha(Color.WHITE, 120), alpha(Color.WHITE, 0), alpha(Color.BLACK, 110)),
        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawCircle(x, y, r, fill)
    shader = null
    c.restore()
    fill.color = alpha(Color.WHITE, 190)
    c.drawCircle(x + sun * r * 0.38f, y - r * 0.4f, r * 0.14f, fill)
}

// ------------------------------------------------------------------------------------ people

/** Someone strolling along the water's edge in shorts and a cap, mid-stride. */
private fun PaintKit.walker(c: Canvas, x: Float, y: Float, sx: Float) {
    val u = unit
    val skin = l.shade(0xFFE8B48C.toInt())
    val skinShade = darken(skin, 0.14f)
    castShadow(c, x, y + 1f * u, 11f * u, 2.6f * u, sx * 10f * u)
    val hipx = x; val hipy = y - 24f * u
    val shx = x + 1.5f * u; val shy = y - 40f * u
    // Back leg (darker) then front leg.
    limb(c, hipx, hipy, x - 6f * u, y - 11f * u, 4f * u, skinShade)
    limb(c, x - 6f * u, y - 11f * u, x - 9f * u, y - 1.5f * u, 3.2f * u, skinShade)
    limb(c, hipx, hipy, x + 6f * u, y - 12f * u, 4.2f * u, skin)
    limb(c, x + 6f * u, y - 12f * u, x + 7f * u, y - 1.5f * u, 3.4f * u, skin)
    fill.color = skin
    c.drawOval(x + 4f * u, y - 3f * u, x + 11f * u, y + 0.5f * u, fill)
    // Shorts, torso, arms.
    limbPart(c, hipx, hipy, x + 6f * u, y - 12f * u, 0f, 0.5f, 5f * u, l.shade(0xFF2E86DE.toInt()))
    limbPart(c, hipx, hipy, x - 6f * u, y - 11f * u, 0f, 0.5f, 5f * u, l.shade(0xFF2765A8.toInt()))
    limb(c, hipx, hipy - 1f * u, shx, shy, 8.5f * u, l.shade(0xFFF5F0E6.toInt()))
    limb(c, shx - 1f * u, shy + 1f * u, x - 7f * u, y - 28f * u, 2.8f * u, skinShade)
    limb(c, shx + 1f * u, shy + 1f * u, x + 8f * u, y - 29f * u, 2.8f * u, skin)
    // Head and a red cap.
    fill.color = skin
    c.drawCircle(shx + 1f * u, y - 45f * u, 4.8f * u, fill)
    fill.color = l.shade(0xFFE74C3C.toInt())
    c.drawArc(shx - 4f * u, y - 51f * u, shx + 6.5f * u, y - 43f * u, 180f, 180f, true, fill)
    c.drawRect(shx + 3f * u, y - 47.5f * u, shx + 9f * u, y - 46f * u, fill)
}

// ------------------------------------------------------------------------------------ surfboard

private fun PaintKit.surfboard(c: Canvas, x: Float, y: Float, sx: Float) {
    val u = unit
    val len = 64f * u; val wd = 9f * u
    // The board's shadow falls along the sand.
    val sh = Path()
    sh.moveTo(x - 4f * u, y); sh.lineTo(x + 4f * u, y)
    sh.lineTo(x + sx * len * 0.6f + 4f * u, y + 6f * u); sh.lineTo(x + sx * len * 0.6f - 4f * u, y + 6f * u); sh.close()
    fill.color = alpha(Color.BLACK, shadowAlpha(42))
    c.drawPath(sh, fill)

    c.save()
    c.rotate(-11f, x, y)
    val board = Path()
    board.moveTo(x, y - len)
    board.quadTo(x + wd * 1.05f, y - len * 0.55f, x + wd * 0.9f, y + 8f * u)
    board.lineTo(x - wd * 0.9f, y + 8f * u)
    board.quadTo(x - wd * 1.05f, y - len * 0.55f, x, y - len)
    board.close()
    val base = l.shade(0xFFFECA57.toInt())
    shader = sideShader(x - wd, x + wd, base)
    c.drawPath(board, fill)
    shader = null
    c.save()
    c.clipPath(board)
    // Rails and stripes: a sun-faded blue band near the top, a red stringer down the middle.
    fill.color = l.shade(0xFF0FB9B1.toInt())
    c.drawRect(x - wd * 1.2f, y - len * 0.78f, x + wd * 1.2f, y - len * 0.7f, fill)
    c.drawRect(x - wd * 1.2f, y - len * 0.3f, x + wd * 1.2f, y - len * 0.22f, fill)
    fill.color = l.shade(0xFFE74C3C.toInt())
    c.drawRect(x - 0.9f * u, y - len, x + 0.9f * u, y + 8f * u, fill)
    // Light and shade down the length.
    shader = sideShader(x - wd, x + wd, Color.WHITE)
    fill.alpha = 60
    c.drawPath(board, fill)
    shader = null
    c.restore()
    // Rim and the fin showing at the bottom.
    val rim = outline(alpha(darken(base, 0.55f), 190), 1.1f * u)
    c.drawPath(board, rim)
    c.restore()
    // Sand heaped round the buried end.
    fill.color = l.ground(0xFFD3B681.toInt())
    c.drawOval(x - wd * 1.5f, y + 3f * u, x + wd * 1.7f, y + 9f * u, fill)
}
