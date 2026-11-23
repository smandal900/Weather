package com.skymood.wallpaper.landscape

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * Rolling-hill painting. A real hillside reads as form first (one flank turned to the sun, the other
 * in shade, dark creases where two hills meet), then as land use: mown or ploughed fields that follow
 * the contours and shrink towards the horizon, hedgerows with trees at their corners, a lane winding
 * up the slope, and pools of mist lying in the valleys. Everything runs on the painter thread.
 */

/** How much the surface under [x] faces the sun, -1 (turned away) .. 1 (facing it). */
private fun PaintKit.facing(ridge: Ridge, x: Float): Float {
    val d = 18f * unit
    val slope = (ridge.yAt(x + d) - ridge.yAt(x - d)) / (2f * d)
    return (slope * sun / 0.3f).coerceIn(-1f, 1f)
}

/** Average height of the ridge across the screen: the plain the hill rises from. */
private fun PaintKit.plainOf(ridge: Ridge): Float {
    var sum = 0f; var n = 0
    var x = ridge.left
    while (x < w * 1.1f) { sum += ridge.yAt(x); n++; x += 12f * unit }
    return sum / max(1, n)
}

/**
 * Height of the contour at depth [t] (0 at the crest .. 1 at the foot) below [ridge] at [x]. Contours
 * follow the crest near the top and relax towards level ground lower down, the way a real hill's do;
 * copying the crest all the way down makes a hill look like a stack of ribbons.
 */
private fun contourY(ridge: Ridge, x: Float, t: Float, reach: Float, plain: Float): Float {
    val flat = (t.pow(0.7f) * 0.9f).coerceIn(0f, 1f)
    val crest = ridge.yAt(x)
    return crest + (plain - crest) * flat + t * reach
}

/** How deep a crease the ridge makes at [x], 0 (none) .. 1 (the bottom of a valley). */
private fun PaintKit.creaseAt(ridge: Ridge, x: Float): Float {
    val d = 60f * unit
    val curve = (ridge.yAt(x - d) + ridge.yAt(x + d)) / 2f - ridge.yAt(x)
    return (-curve / (h * 0.012f)).coerceIn(0f, 1f)
}

/**
 * Light and shade over the form of a hill: the flank facing the sun gets a warm glow fading down
 * from the crest, the flank turned away a cool shadow, and each valley floor a dark crease.
 */
internal fun PaintKit.terrainShade(c: Canvas, ridge: Ridge, bottom: Float, reach: Float, top: Int, base: Int) {
    val body = Path(); ridge.addTo(body, bottom)
    c.save()
    c.clipPath(body)
    val warm = lighten(top, 0.4f)
    val cool = darken(base, 0.5f)
    val step = 5f * unit
    var x = ridge.left
    while (x < w * 1.1f) {
        val f = facing(ridge, x + step / 2f)
        val y0 = ridge.yAt(x) - 1f
        if (f > 0.05f) {
            shader = LinearGradient(0f, y0, 0f, y0 + reach, alpha(warm, (95 * f).toInt()), alpha(warm, 0), Shader.TileMode.CLAMP)
            c.drawRect(x, y0, x + step + 1f, y0 + reach, fill)
        } else if (f < -0.05f) {
            shader = LinearGradient(0f, y0, 0f, y0 + reach * 1.3f, alpha(cool, (120 * -f).toInt()), alpha(cool, 0), Shader.TileMode.CLAMP)
            c.drawRect(x, y0, x + step + 1f, y0 + reach * 1.3f, fill)
        }
        val crease = creaseAt(ridge, x + step / 2f)
        if (crease > 0.05f) {
            val len = reach * 1.6f
            shader = LinearGradient(0f, y0, 0f, y0 + len, alpha(cool, (110 * crease).toInt()), alpha(cool, 0), Shader.TileMode.CLAMP)
            c.drawRect(x, y0, x + step + 1f, y0 + len, fill)
        }
        x += step
    }
    shader = null
    c.restore()
}

/**
 * Short strokes of grass laid out in rows that follow the slope: rows are packed tight near the
 * crest and open up towards the viewer, so the hillside has the grain of a real lawn or pasture
 * seen in perspective, with the sun-facing side lighter.
 */
internal fun PaintKit.contourGrass(
    c: Canvas, ridge: Ridge, bottom: Float, reach: Float, light: Int, dark: Int, rnd: Random, rows: Int,
) {
    val body = Path(); ridge.addTo(body, bottom)
    c.save()
    c.clipPath(body)
    val plain = plainOf(ridge)
    for (k in 0 until rows) {
        val t = ((k + 0.5f) / rows).pow(1.5f)
        val gap = (3f + 13f * t) * unit
        var x = ridge.left + rnd.nextFloat() * gap
        while (x < w * 1.1f) {
            val len = gap * (1.1f + rnd.nextFloat() * 1.4f)
            val y = contourY(ridge, x, t, reach, plain)
            val f = facing(ridge, x)
            line.strokeWidth = (1f + 1.5f * t) * unit
            line.color = alpha(mix(dark, light, ((0.5f + 0.4f * f) * (0.5f + rnd.nextFloat() * 0.7f)).coerceIn(0f, 1f)), 95)
            c.drawLine(x, y, x + len, contourY(ridge, x + len, t, reach, plain), line)
            x += gap * (0.7f + rnd.nextFloat() * 0.8f)
        }
    }
    c.restore()
}

/**
 * A patchwork of fields draped over the hill. Fields sit in bands that deepen towards the viewer, so
 * their width and height grow with depth. Field boundaries lean and wobble instead of forming a
 * grid, edges follow the terrain, and each field carries mown stripes along the contour. Hedgerows
 * line some of the boundaries. Returns the hedge corners (x, y, depth 0..1) where trees can stand.
 */
internal fun PaintKit.patchwork(
    c: Canvas, ridge: Ridge, bottom: Float, reach: Float, rnd: Random, palette: IntArray,
    hedge: Int, hedgeLit: Int, cell: Float, fillAlpha: Int,
): List<FloatArray> {
    val body = Path(); ridge.addTo(body, bottom)
    c.save()
    c.clipPath(body)
    val corners = ArrayList<FloatArray>()
    val p = Path()
    val step = 7f * unit
    val plain = plainOf(ridge)
    // Contour height with a gentle wobble so field edges are never ruler-straight.
    fun yy(x: Float, t: Float) = min(bottom, contourY(ridge, x, t, reach, plain) + sin(x / (45f * unit) + t * 29f) * 2.5f * unit * t)

    var t0 = 0.02f + rnd.nextFloat() * 0.04f
    while (t0 < 0.97f) {
        val t1 = min(1f, t0 + 0.09f + 0.2f * t0 + 0.06f * rnd.nextFloat())
        // Band boundaries along the top edge; each leans by its own skew towards the bottom edge.
        val xs = ArrayList<Float>(); val sk = ArrayList<Float>()
        var bx = ridge.left - rnd.nextFloat() * w * 0.1f
        while (bx < w * 1.2f) {
            xs.add(bx); sk.add((rnd.nextFloat() - 0.5f) * w * cell * (0.5f + 1.2f * t0))
            bx += w * cell * (0.45f + 1.2f * t0) * (0.7f + 0.7f * rnd.nextFloat())
        }
        // A point on a boundary at fraction u (0 top .. 1 bottom) of this band.
        fun edgeX(i: Int, u: Float) = xs[i] + sk[i] * u
        fun edgeT(u: Float) = t0 + (t1 - t0) * u
        for (i in 0 until xs.size - 1) {
            if (rnd.nextFloat() < 0.82f) {
                p.rewind()
                val ntop = max(1, ((xs[i + 1] - xs[i]) / step).toInt())
                for (s in 0..ntop) {
                    val f = s / ntop.toFloat(); val x = xs[i] + (xs[i + 1] - xs[i]) * f
                    if (s == 0) p.moveTo(x, yy(x, t0)) else p.lineTo(x, yy(x, t0))
                }
                for (s in ntop downTo 0) {
                    val f = s / ntop.toFloat(); val x = edgeX(i, 1f) + (edgeX(i + 1, 1f) - edgeX(i, 1f)) * f
                    p.lineTo(x, yy(x, t1))
                }
                p.close()
                val col = palette[rnd.nextInt(palette.size)]
                fill.color = alpha(col, fillAlpha)
                c.drawPath(p, fill)
                // Mown stripes along the contour, alternate ones a touch darker or lighter.
                val n = max(2, min(12, ((t1 - t0) * reach / (4f * unit * (1f + 3f * t0))).toInt()))
                val stripeCol = if (rnd.nextBoolean()) alpha(darken(col, 0.4f), 30) else alpha(lighten(col, 0.4f), 28)
                for (j in 1 until n step 2) {
                    val ua = j / n.toFloat(); val ub = (j + 1) / n.toFloat()
                    p.rewind()
                    for (s in 0..ntop) {
                        val f = s / ntop.toFloat()
                        val x = edgeX(i, ua) + (edgeX(i + 1, ua) - edgeX(i, ua)) * f
                        if (s == 0) p.moveTo(x, yy(x, edgeT(ua))) else p.lineTo(x, yy(x, edgeT(ua)))
                    }
                    for (s in ntop downTo 0) {
                        val f = s / ntop.toFloat()
                        val x = edgeX(i, ub) + (edgeX(i + 1, ub) - edgeX(i, ub)) * f
                        p.lineTo(x, yy(x, edgeT(ub)))
                    }
                    p.close()
                    fill.color = stripeCol
                    c.drawPath(p, fill)
                }
            }
            // Hedgerows: along the top of the field and down its leaning left side, but not on every edge.
            if (rnd.nextFloat() < 0.6f) {
                var f = 0f
                var px = xs[i]; var py = yy(px, t0)
                while (f < 1f) {
                    f = min(1f, f + (5f + 7f * t0) * unit / max(1f, xs[i + 1] - xs[i]))
                    val x = xs[i] + (xs[i + 1] - xs[i]) * f
                    val y = yy(x, t0)
                    hedgeBit(c, px, py, x, y, t0, hedge, hedgeLit, rnd)
                    px = x; py = y
                }
            }
            if (rnd.nextFloat() < 0.45f) {
                var u = 0f
                var px = edgeX(i, 0f); var py = yy(px, t0)
                while (u < 1f) {
                    u = min(1f, u + 0.2f)
                    val x = edgeX(i, u)
                    val y = yy(x, edgeT(u))
                    hedgeBit(c, px, py, x, y, edgeT(u), hedge, hedgeLit, rnd)
                    px = x; py = y
                }
            }
            corners.add(floatArrayOf(xs[i], yy(xs[i], t0), t0))
        }
        t0 = t1
    }
    c.restore()
    return corners
}

/**
 * Patches of woodland lying on the slope: tight clusters of dark crowns with lit tops, stretched
 * along the contour. Small and numerous on distant hills, they break up the farmland and give the
 * far slopes the speckled texture of real hill country.
 */
internal fun PaintKit.woodland(
    c: Canvas, ridge: Ridge, bottom: Float, reach: Float, rnd: Random, count: Int, dark: Int, lit: Int, size: Float,
) {
    val body = Path(); ridge.addTo(body, bottom)
    c.save()
    c.clipPath(body)
    val plain = plainOf(ridge)
    repeat(count) {
        val cx = ridge.left + rnd.nextFloat() * w * 1.2f
        val t = 0.03f + rnd.nextFloat() * 0.85f
        val cy = contourY(ridge, cx, t, reach, plain)
        val span = size * (0.7f + 0.9f * rnd.nextFloat()) * (0.6f + t)
        val crowns = 6 + rnd.nextInt(8)
        repeat(crowns) {
            val x = cx + (rnd.nextFloat() - 0.5f) * span * 2.4f
            val y = contourY(ridge, x, t, reach, plain) + (rnd.nextFloat() - 0.5f) * span * 0.5f
            val r = span * (0.28f + 0.3f * rnd.nextFloat())
            fill.color = alpha(mix(dark, darken(dark, 0.3f), rnd.nextFloat()), 235)
            c.drawCircle(x, y - r * 0.4f, r, fill)
            fill.color = alpha(mix(dark, lit, 0.25f + 0.55f * rnd.nextFloat()), 210)
            c.drawCircle(x - sun * r * 0.3f, y - r * 0.8f, r * 0.65f, fill)
        }
        if (cy > bottom) return@repeat
        groundShadow(c, cx, cy + span * 0.15f, span * 1.6f, 45)
    }
    c.restore()
}

/** One clump of hedge: a dark bush with a lit side, so a row of them reads as a hedgerow. */
private fun PaintKit.hedgeBit(c: Canvas, x0: Float, y0: Float, x: Float, y: Float, t: Float, dark: Int, lit: Int, rnd: Random) {
    val r = (1f + 1.8f * t) * unit * (0.8f + 0.6f * rnd.nextFloat())
    // A continuous band under the bushes, so the row reads as one hedge rather than separate dots.
    line.strokeWidth = r * 1.2f
    line.color = alpha(darken(dark, 0.2f), 225)
    c.drawLine(x0, y0 - r * 0.4f, x, y - r * 0.4f, line)
    fill.color = alpha(mix(darken(dark, 0.15f), dark, rnd.nextFloat()), 235)
    c.drawCircle(x, y - r * 0.5f, r, fill)
    fill.color = alpha(mix(dark, lit, 0.3f + 0.6f * rnd.nextFloat()), 200)
    c.drawCircle(x - sun * r * 0.3f, y - r * 0.9f, r * 0.6f, fill)
}

/** A flat shadow lying on the ground away from the sun, under a tree or bush at [x],[baseY]. */
internal fun PaintKit.groundShadow(c: Canvas, x: Float, baseY: Float, th: Float, strength: Int = 70) {
    val rx = th * 0.45f
    val cx = x - sun * th * 0.3f
    c.save()
    c.translate(cx, baseY)
    c.scale(1f, 0.16f)
    shader = RadialGradient(0f, 0f, rx, alpha(0xFF0E1E10.toInt(), strength), 0, Shader.TileMode.CLAMP)
    c.drawCircle(0f, 0f, rx, fill)
    shader = null
    c.restore()
}

/** Pools of mist lying in the valleys between hills; they hide the foot of the hill behind. */
internal fun PaintKit.valleyMist(c: Canvas, ridge: Ridge, reach: Float, strength: Float) {
    val tint = l.skyBottom and 0x00FFFFFF
    var x = ridge.left
    var lastX = -1e9f
    while (x < w * 1.1f) {
        if (creaseAt(ridge, x) > 0.5f && x - lastX > w * 0.25f) {
            lastX = x
            val rx = w * 0.42f
            val cy = ridge.yAt(x) + reach * 0.45f
            c.save()
            c.translate(x, cy)
            c.scale(1f, h * 0.05f / rx)
            val a = (255 * (strength * (1f + (4 - l.clarity) * 0.3f)).coerceAtMost(0.9f)).toInt()
            shader = RadialGradient(0f, 0f, rx, intArrayOf((a shl 24) or tint, ((a * 0.55f).toInt() shl 24) or tint, tint),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(0f, 0f, rx, fill)
            shader = null
            c.restore()
        }
        x += 14f * unit
    }
}

/**
 * A lane winding up a hillside from [startY] to the crest near [crestX], narrowing with distance.
 * Drawn clipped to the hill, so it simply disappears over the top.
 */
internal fun PaintKit.hillLane(c: Canvas, ridge: Ridge, bottom: Float, startX: Float, startY: Float, crestX: Float, color: Int) {
    val body = Path(); ridge.addTo(body, bottom)
    c.save()
    c.clipPath(body)
    val crestY = ridge.yAt(crestX) - 4f * unit
    val n = 60
    val left = FloatArray(n + 1); val right = FloatArray(n + 1); val ys = FloatArray(n + 1); val mid = FloatArray(n + 1)
    for (i in 0..n) {
        val s = i / n.toFloat()
        val u = 1f - s
        val sway = sin(s * 6.8f + 0.6f) * w * 0.13f * u.pow(0.7f)
        val cx = startX + (crestX - startX) * s + sway
        val hw = (2f + 26f * u.pow(1.7f)) * unit
        ys[i] = startY + (crestY - startY) * s
        mid[i] = cx; left[i] = cx - hw; right[i] = cx + hw
    }
    val lane = Path()
    lane.moveTo(left[0], ys[0])
    for (i in 1..n) lane.lineTo(left[i], ys[i])
    for (i in n downTo 0) lane.lineTo(right[i], ys[i])
    lane.close()
    fill.color = alpha(color, 235)
    c.drawPath(lane, fill)
    // Worn centre, and the verge on each side darker where the grass is longer.
    line.strokeWidth = 1.6f * unit
    for (side in 0..1) {
        line.color = alpha(darken(color, 0.45f), 150)
        val edge = if (side == 0) left else right
        for (i in 0 until n) c.drawLine(edge[i], ys[i], edge[i + 1], ys[i + 1], line)
    }
    line.color = alpha(lighten(color, 0.3f), 110)
    for (i in 0 until n) {
        line.strokeWidth = (1f + 5f * (1f - i / n.toFloat())) * unit
        c.drawLine(mid[i], ys[i], mid[i + 1], ys[i + 1], line)
    }
    c.restore()
}
