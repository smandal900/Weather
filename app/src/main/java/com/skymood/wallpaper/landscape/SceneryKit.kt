package com.skymood.wallpaper.landscape

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Helpers shared by the newer landscapes: surf, cottages, boats, windows and train cars. */

/** How rough the sea looks for a wind speed: 0.6 calm .. 1.6 gale. */
internal fun roughness(windKmh: Float) = 0.6f + (windKmh / 40f).coerceAtMost(1f)

/** A row of foam dots that swell and fade, to put along a shore or round a rock. */
internal fun foamDots(canvas: Canvas, paint: Paint, x0: Float, x1: Float, y: Float, n: Int, t: Float, size: Float, rough: Float) {
    for (i in 0 until n) {
        val x = x0 + (x1 - x0) * i / (n - 1).coerceAtLeast(1)
        val pulse = 0.5f + 0.5f * sin(t * (0.9f + 0.2f * (i % 3)) + i * 1.7f)
        paint.alpha = (200 * pulse * (0.6f + 0.4f * rough)).toInt().coerceIn(0, 255)
        canvas.drawCircle(x, y + size * 0.3f * sin(t * 1.3f + i), size * (0.6f + 0.9f * pulse * rough), paint)
    }
}

/** Linear interpolation through (xs, ys) control points; clamps outside. */
internal fun interp(xs: FloatArray, ys: FloatArray, x: Float): Float {
    if (x <= xs[0]) return ys[0]
    for (i in 1 until xs.size) if (x <= xs[i]) {
        val u = (x - xs[i - 1]) / (xs[i] - xs[i - 1])
        return ys[i - 1] + (ys[i] - ys[i - 1]) * u
    }
    return ys[xs.size - 1]
}

/** A gabled cottage: shaded walls, two-tone roof, door, and a window that glows when it is dark. */
internal fun PaintKit.cottage(c: Canvas, x: Float, base: Float, hw: Float, hh: Float, wall: Int, roof: Int) {
    val wl = l.shade(wall)
    val rf = l.ground(roof)
    shader = LinearGradient(
        x - hw, 0f, x + hw, 0f,
        if (sunOnLeft) lighten(wl, 0.1f) else darken(wl, 0.3f),
        if (sunOnLeft) darken(wl, 0.3f) else lighten(wl, 0.1f), Shader.TileMode.CLAMP,
    )
    c.drawRect(x - hw, base - hh, x + hw, base, fill)
    shader = null
    val rh = hw * 0.8f; val eave = hw * 0.16f
    val left = Path(); val right = Path()
    left.moveTo(x, base - hh - rh); left.lineTo(x - hw - eave, base - hh + eave * 0.4f); left.lineTo(x, base - hh + eave * 0.4f); left.close()
    right.moveTo(x, base - hh - rh); right.lineTo(x + hw + eave, base - hh + eave * 0.4f); right.lineTo(x, base - hh + eave * 0.4f); right.close()
    fill.color = if (sunOnLeft) lighten(rf, 0.14f) else darken(rf, 0.3f)
    c.drawPath(left, fill)
    fill.color = if (sunOnLeft) darken(rf, 0.3f) else lighten(rf, 0.14f)
    c.drawPath(right, fill)
    fill.color = darken(wl, 0.5f)
    c.drawRect(x - hw * 0.14f, base - hh * 0.5f, x + hw * 0.14f, base, fill)
    val ws = hw * 0.28f
    for (side in intArrayOf(-1, 1)) {
        val wx = x + side * hw * 0.58f; val wy = base - hh * 0.75f
        if (l.windowsLit) {
            shader = RadialGradient(wx, wy + ws / 2, ws * 2f, 0x66FFC060, 0, Shader.TileMode.CLAMP)
            c.drawCircle(wx, wy + ws / 2, ws * 2f, fill)
            shader = null
            fill.color = 0xFFFFD27A.toInt()
        } else fill.color = l.shade(0xFF3F4756.toInt())
        c.drawRect(wx - ws / 2, wy, wx + ws / 2, wy + ws, fill)
    }
}

/** A grid of windows on a flat wall; a share of them are lit when it is dark. */
internal fun PaintKit.windows(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, cols: Int, rows: Int, rnd: Random, litShare: Float) {
    val cw = (x1 - x0) / cols; val rh = (y1 - y0) / rows
    for (r in 0 until rows) for (col in 0 until cols) {
        val on = l.windowsLit && rnd.nextFloat() < litShare
        fill.color = if (on) 0xFFFFD27A.toInt() else l.shade(if (rnd.nextBoolean()) 0xFF55657A.toInt() else 0xFF45546A.toInt())
        c.drawRect(x0 + col * cw + cw * 0.22f, y0 + r * rh + rh * 0.2f, x0 + (col + 1) * cw - cw * 0.22f, y0 + (r + 1) * rh - rh * 0.2f, fill)
    }
}

/** Fills a rock face with strata, cracks and a lit/shadow side, clipped to [body]. */
internal fun PaintKit.rockFace(c: Canvas, body: Path, x0: Float, x1: Float, y0: Float, y1: Float, lit: Int, dark: Int, rnd: Random, litOnRight: Boolean) {
    shader = LinearGradient(x0, 0f, x1, 0f, if (litOnRight) dark else lit, if (litOnRight) lit else dark, Shader.TileMode.CLAMP)
    c.drawPath(body, fill)
    shader = null
    c.save(); c.clipPath(body)
    line.strokeWidth = 1.6f * unit
    repeat(60) {
        val x = x0 + (x1 - x0) * rnd.nextFloat(); val y = y0 + (y1 - y0) * rnd.nextFloat()
        line.color = alpha(if (rnd.nextFloat() < 0.6f) Color.BLACK else Color.WHITE, 34)
        c.drawLine(x, y, x + (x1 - x0) * (0.05f + 0.1f * rnd.nextFloat()), y + (y1 - y0) * 0.004f * (rnd.nextFloat() - 0.5f), line)
    }
    repeat(14) {
        val x = x0 + (x1 - x0) * rnd.nextFloat(); val y = y0 + (y1 - y0) * rnd.nextFloat()
        line.color = alpha(Color.BLACK, 40)
        c.drawLine(x, y, x + (rnd.nextFloat() - 0.5f) * unit * 12f, y + (y1 - y0) * 0.08f * (0.5f + rnd.nextFloat()), line)
    }
    c.restore()
}

/** Smooth hill silhouette path across [x0]..[x1], used for distant coasts and mounds. */
internal fun moundPath(x0: Float, x1: Float, baseY: Float, height: Float, wobble: Float = 0.12f): Path {
    val n = 40
    val p = Path()
    p.moveTo(x0, baseY + 1f)
    for (i in 0..n) {
        val u = i / n.toFloat()
        val prof = sin(PI.toFloat() * u).coerceAtLeast(0f)
        p.lineTo(x0 + (x1 - x0) * u, baseY - height * Math.pow(prof.toDouble(), 1.3).toFloat() * (1f - wobble + wobble * sin(u * 21f)))
    }
    p.lineTo(x1, baseY + 1f)
    p.close()
    return p
}

internal fun maxF(a: Float, b: Float) = max(a, b)
