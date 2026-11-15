package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.skymood.domain.Astro
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the moon with its real phase: the lit part is a half disc on one limb plus or minus half
 * an ellipse for the terminator, so crescent, quarter, gibbous and full moons all look right.
 * Waxing moons are lit on the right, waning moons on the left. The lit shape is rebuilt only
 * when the phase changes noticeably, never every frame.
 */
class MoonPainter {
    private val litPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F0E6.toInt() }
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A3042.toInt() }
    private val mariaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4B4E5C.toInt() }
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tychoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val maria = ArrayList<Path>()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lit = Path()
    private val limb = RectF()
    private val terminator = RectF()
    private var radius = 0f
    private var builtPhase = -1f

    fun setup(radius: Float) {
        this.radius = radius
        builtPhase = -1f
        glowPaint.shader = RadialGradient(
            0f, 0f, radius * 4f, intArrayOf(0x66DDE6FF, 0x18AAB8E0, Color.TRANSPARENT), floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
        buildMaria(radius)
        tychoPaint.shader = RadialGradient(
            0f, 0f, radius * 0.16f, intArrayOf(0xFFFFFFFF.toInt(), 0x66FFFFFF, Color.TRANSPARENT), floatArrayOf(0f, 0.25f, 1f),
            Shader.TileMode.CLAMP,
        )
        litPaint.shader = RadialGradient(
            -radius * 0.35f, -radius * 0.35f, radius * 1.5f,
            intArrayOf(0xFFFFFDF4.toInt(), 0xFFE9E6D8.toInt(), 0xFFB9B6AA.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
    }

    /**
     * The real near-side maria as irregular patches, in unit-disc coordinates (x right, y down,
     * north up) as seen from the northern hemisphere. Each is cx, cy, rx, ry, tilt in degrees.
     */
    private fun buildMaria(r: Float) {
        maria.clear()
        val defs = arrayOf(
            floatArrayOf(-0.30f, -0.40f, 0.30f, 0.24f, -15f),  // Imbrium
            floatArrayOf(-0.66f, -0.05f, 0.17f, 0.40f, 10f),   // Procellarum
            floatArrayOf(-0.50f, -0.55f, 0.12f, 0.09f, 0f),    // Procellarum north
            floatArrayOf(0.10f, -0.33f, 0.17f, 0.16f, 0f),     // Serenitatis
            floatArrayOf(0.27f, -0.02f, 0.22f, 0.17f, 15f),    // Tranquillitatis
            floatArrayOf(0.64f, -0.22f, 0.11f, 0.075f, 25f),   // Crisium
            floatArrayOf(0.55f, 0.18f, 0.14f, 0.17f, -20f),    // Fecunditatis
            floatArrayOf(0.03f, 0.04f, 0.10f, 0.08f, 0f),      // Vaporum / Sinus Medii
            floatArrayOf(-0.28f, 0.36f, 0.17f, 0.12f, 0f),     // Nubium
            floatArrayOf(-0.55f, 0.32f, 0.09f, 0.09f, 0f),     // Humorum
            floatArrayOf(0.12f, -0.68f, 0.20f, 0.04f, -8f),    // Frigoris
        )
        for ((idx, d) in defs.withIndex()) {
            val n = 24
            val tilt = Math.toRadians(d[4].toDouble())
            val ct = cos(tilt).toFloat()
            val st = sin(tilt).toFloat()
            val pts = FloatArray(n * 2)
            for (i in 0 until n) {
                val a = 2.0 * Math.PI * i / n
                // Low-frequency wobble keeps the outline organic but smooth, not polygonal.
                val w = 1f + 0.15f * sin(3.0 * a + idx * 1.7).toFloat() + 0.08f * sin(5.0 * a + idx * 2.9).toFloat()
                val ex = cos(a).toFloat() * d[2] * w
                val ey = sin(a).toFloat() * d[3] * w
                pts[i * 2] = (d[0] + ex * ct - ey * st) * r
                pts[i * 2 + 1] = (d[1] + ex * st + ey * ct) * r
            }
            val path = Path()
            // Smooth closed blob: quadratic curves between the midpoints, points as controls.
            path.moveTo((pts[(n - 1) * 2] + pts[0]) / 2f, (pts[(n - 1) * 2 + 1] + pts[1]) / 2f)
            for (i in 0 until n) {
                val nx = (i + 1) % n
                path.quadTo(pts[i * 2], pts[i * 2 + 1], (pts[i * 2] + pts[nx * 2]) / 2f, (pts[i * 2 + 1] + pts[nx * 2 + 1]) / 2f)
            }
            path.close()
            maria.add(path)
        }
        // Merge into one shape so overlapping patches don't stack into darker lens-shaped spots.
        val merged = Path()
        for (m in maria) merged.op(m, Path.Op.UNION)
        maria.clear()
        maria.add(merged)
    }

    private fun build(phase: Float) {
        builtPhase = phase
        val r = radius
        val t = cos(2.0 * Math.PI * phase).toFloat() // +1 new .. -1 full
        limb.set(-r, -r, r, r)
        val rx = abs(t) * r
        terminator.set(-rx, -r, rx, r)
        lit.rewind()
        // Lit limb on the right, then back along the terminator.
        lit.addArc(limb, -90f, 180f)
        lit.arcTo(terminator, 90f, if (t > 0f) -180f else 180f)
        lit.close()
    }

    /** @param alpha 0..1, so the moon can be faint by day. */
    fun draw(canvas: Canvas, cx: Float, cy: Float, phase: Float, alpha: Float = 1f, glow: Float = 1f) {
        if (radius <= 0f) return
        if (abs(phase - builtPhase) > 0.004f) build(phase)
        val a = (255 * alpha).toInt().coerceIn(0, 255)
        val illum = Astro.moonIllumination(phase)
        val waxing = phase < 0.5f
        canvas.save()
        canvas.translate(cx, cy)
        glowPaint.alpha = (a * (0.15f + 0.85f * illum) * glow).toInt().coerceIn(0, 255)
        canvas.drawCircle(0f, 0f, radius * 4f, glowPaint)
        // The unlit part of the disc, faintly visible (earthshine).
        darkPaint.alpha = (a * 0.55f).toInt()
        canvas.drawCircle(0f, 0f, radius, darkPaint)
        if (!waxing) canvas.scale(-1f, 1f)
        litPaint.alpha = a
        canvas.drawPath(lit, litPaint)
        canvas.save()
        canvas.clipPath(lit)
        canvas.scale(if (waxing) 1f else -1f, 1f) // undo the mirror: the surface never flips
        // Maria: a soft wider pass, then the darker core.
        mariaPaint.style = Paint.Style.FILL_AND_STROKE
        mariaPaint.strokeJoin = Paint.Join.ROUND
        mariaPaint.strokeWidth = radius * 0.07f
        mariaPaint.alpha = (a * 0.10f).toInt()
        for (m in maria) canvas.drawPath(m, mariaPaint)
        mariaPaint.strokeWidth = radius * 0.03f
        mariaPaint.alpha = (a * 0.14f).toInt()
        for (m in maria) canvas.drawPath(m, mariaPaint)
        mariaPaint.style = Paint.Style.FILL
        mariaPaint.alpha = (a * 0.22f).toInt()
        for (m in maria) canvas.drawPath(m, mariaPaint)
        // Tycho: a soft bright glow with a few faint, tapering rays, south of centre.
        val tx = -0.12f * radius
        val ty = 0.62f * radius
        canvas.save()
        canvas.translate(tx, ty)
        rayPaint.strokeWidth = radius * 0.02f
        rayPaint.alpha = (a * 0.10f).toInt()
        for (deg in intArrayOf(205, 250, 290, 335, 25, 75, 125, 165)) {
            val rad = Math.toRadians(deg.toDouble())
            val len = radius * (0.30f + 0.12f * ((deg % 3) / 2f))
            canvas.drawLine(0f, 0f, cos(rad).toFloat() * len, sin(rad).toFloat() * len, rayPaint)
        }
        tychoPaint.alpha = (a * 0.9f).toInt()
        canvas.drawCircle(0f, 0f, radius * 0.16f, tychoPaint)
        canvas.restore()
        canvas.restore()
        canvas.restore()
    }
}
