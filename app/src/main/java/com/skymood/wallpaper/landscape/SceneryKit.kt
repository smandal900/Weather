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

/** Draws a side-on passenger train: a locomotive and coaches with lit windows and a headlight beam after dark. */
internal class TrainRenderer {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beam = Path()

    init {
        glow.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xCCFFF2C4.toInt(), 0x33FFE9A8, Color.TRANSPARENT), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
    }

    /** [headX] is the front of the locomotive; the train runs to the right. [trackY] is the top of the rails. */
    fun draw(c: Canvas, headX: Float, trackY: Float, w: Float, h: Float, l: Lighting, cars: Int) {
        val carLen = w * 0.15f; val carH = h * 0.03f; val gap = w * 0.004f
        val locoLen = carLen * 1.05f
        val lit = l.windowsLit
        // Locomotive: dark body with a red band and a sloping nose.
        val body = Path()
        body.moveTo(headX - locoLen, trackY - carH * 0.2f); body.lineTo(headX - locoLen, trackY - carH)
        body.lineTo(headX - carH * 0.9f, trackY - carH); body.lineTo(headX, trackY - carH * 0.45f); body.lineTo(headX, trackY - carH * 0.2f); body.close()
        p.shader = LinearGradient(0f, trackY - carH, 0f, trackY, l.shade(0xFF3E4A5E.toInt()), l.shade(0xFF1E2430.toInt()), Shader.TileMode.CLAMP)
        c.drawPath(body, p); p.shader = null
        p.color = l.shade(0xFFD9442F.toInt())
        c.drawRect(headX - locoLen, trackY - carH * 0.55f, headX - carH * 0.2f, trackY - carH * 0.4f, p)
        p.color = if (lit) 0xFFFFD27A.toInt() else l.shade(0xFF8FB0C8.toInt())
        c.drawRect(headX - carH * 2.2f, trackY - carH * 0.9f, headX - carH * 1.2f, trackY - carH * 0.62f, p)
        // Coaches.
        for (i in 0 until cars) {
            val right = headX - locoLen - gap - i * (carLen + gap)
            val left = right - carLen
            p.shader = LinearGradient(0f, trackY - carH, 0f, trackY, l.shade(0xFFE8E4DA.toInt()), l.shade(0xFFA8A49A.toInt()), Shader.TileMode.CLAMP)
            c.drawRect(left, trackY - carH, right, trackY - carH * 0.2f, p); p.shader = null
            p.color = l.shade(0xFFB5412F.toInt())
            c.drawRect(left, trackY - carH * 0.5f, right, trackY - carH * 0.36f, p)
            val ws = carLen / 8f
            for (k in 0 until 7) {
                p.color = if (lit) 0xFFFFD27A.toInt() else l.shade(0xFF6F8AA2.toInt())
                c.drawRect(left + ws * (k + 0.5f) + ws * 0.1f, trackY - carH * 0.9f, left + ws * (k + 1.1f), trackY - carH * 0.58f, p)
            }
        }
        // Wheels along the whole train.
        p.color = l.shade(0xFF14171C.toInt())
        var x = headX - carH
        val tail = headX - locoLen - gap - cars * (carLen + gap)
        while (x > tail) { c.drawCircle(x, trackY - carH * 0.1f, carH * 0.1f, p); x -= carH * 0.8f }
        if (lit) {
            c.save(); c.translate(headX, trackY - carH * 0.42f); c.scale(carH * 1.2f, carH * 1.2f); glow.alpha = 255; c.drawCircle(0f, 0f, 1f, glow); c.restore()
            beam.rewind(); beam.moveTo(headX, trackY - carH * 0.42f); beam.lineTo(headX + w * 0.35f, trackY - carH * 0.95f); beam.lineTo(headX + w * 0.35f, trackY + carH * 0.1f); beam.close()
            p.color = 0x22FFF2C4; c.drawPath(beam, p)
        }
    }
}

/** Two rails with sleepers across [x0]..[x1] at [y], with a gravel bed under them. */
internal fun PaintKit.railTrack(c: Canvas, x0: Float, x1: Float, y: Float) {
    fill.color = l.shade(0xFF7A746A.toInt())
    c.drawRect(x0, y - h * 0.002f, x1, y + h * 0.012f, fill)
    line.strokeWidth = 2f * unit; line.color = l.shade(0xFF4A3A2A.toInt())
    var x = x0
    while (x < x1) { c.drawLine(x, y + h * 0.001f, x - 2f * unit, y + h * 0.011f, line); x += 9f * unit }
    line.strokeWidth = 2.2f * unit; line.color = l.shade(0xFFC9CED6.toInt())
    c.drawLine(x0, y + h * 0.002f, x1, y + h * 0.002f, line)
    c.drawLine(x0, y + h * 0.0085f, x1, y + h * 0.0085f, line)
}
