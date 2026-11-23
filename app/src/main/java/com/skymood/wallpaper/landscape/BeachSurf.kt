package com.skymood.wallpaper.landscape

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.skymood.wallpaper.scenes.SkyGradient
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * The animated surf of the beach, drawn on top of [Water]:
 *  - swells that roll in from the horizon in perspective (bunched far away, wide and spaced near the
 *    shore), slanted so each one reaches the beach at one end first, broken into dashes and turning
 *    to foam as they approach;
 *  - a turquoise shallows tint that follows the curve of the shore, so the sea clears over the sand;
 *  - the same swells running up the sand as a thin sheet of water with a lacy foam edge, then
 *    draining back (the sand's wet band is painted in [beachWetSand]).
 * [draw] methods never allocate; paths and arrays are reused.
 */
internal class BeachSurf {
    private var w = 1f
    private var h = 1f
    private var unit = 1f
    private var top = 0f

    private val crestPaint = linePaint()
    private val shadowPaint = linePaint()
    private val foamPaint = linePaint()
    private val lacePaint = linePaint()
    private val filmPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hazePaint = Paint()
    private val turqPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sandyPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glitterPaint = linePaint()
    /** Nested, narrowing copies of the sun path, so its edges feather out instead of ending in a hard line. */
    private val glow = Array(GLOW_LAYERS) { Path() }
    private val glintDepth = FloatArray(GLINTS)
    private val glintSide = FloatArray(GLINTS)
    private val glintPhase = FloatArray(GLINTS)
    private val glintSegs = FloatArray(GLINTS * 4)
    /** Sun or moon colour on the water, or 0 under cloud, and where across the screen it sits. */
    private var glitter = 0
    private var glitterX = 0f

    private val bands = Array(BANDS) { Path() }
    private val film = Path()
    private val crestSegs = FloatArray(CREST_PTS * 4)
    private val edgeSegs = FloatArray(RUN_PTS * 4)
    private val laceSegs = FloatArray(RUN_PTS * 4)
    /** Where the sand starts at each crest vertex and each runup vertex, worked out once. */
    private val crestShore = FloatArray(CREST_PTS + 1)
    private val runShore = FloatArray(RUN_PTS + 1)
    private val edgeX = FloatArray(RUN_PTS + 1)
    private val edgeY = FloatArray(RUN_PTS + 1)
    private val edgeS = FloatArray(RUN_PTS + 1)

    private fun linePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun build(w: Float, h: Float, unit: Float, top: Float) {
        this.w = w; this.h = h; this.unit = unit.coerceAtLeast(0.6f); this.top = top
        val rnd = kotlin.random.Random(5)
        for (i in 0 until GLINTS) {
            // Sparkles bunch up towards the horizon, like perspective.
            glintDepth[i] = rnd.nextFloat().pow(1.7f)
            glintSide[i] = rnd.nextFloat() * 2f - 1f
            glintPhase[i] = rnd.nextFloat() * 6.2832f
        }
        for (i in 0..CREST_PTS) crestShore[i] = shoreEdge(crestX(i), w, h)
        for (i in 0..RUN_PTS) runShore[i] = shoreEdge(runX(i), w, h)
        // Shallows: stacked translucent strips ending at the shore, each reaching further out than
        // the last, so the water clears gradually and follows the shoreline's curve.
        for (b in 0 until BANDS) {
            val depth = h * (0.064f * (1f - b / BANDS.toFloat()).pow(1.6f) + 0.0045f)
            val p = bands[b]
            p.rewind()
            for (i in 0..BAND_PTS) {
                val x = -w * 0.1f + w * 1.2f * i / BAND_PTS
                val y = shoreEdge(x, w, h) - depth * (1f + 0.25f * sin(x / w * 11f + b * 1.7f))
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            for (i in BAND_PTS downTo 0) {
                val x = -w * 0.1f + w * 1.2f * i / BAND_PTS
                p.lineTo(x, shoreEdge(x, w, h) + 4f * unit)
            }
            p.close()
        }
    }

    fun applyLighting(l: Lighting) {
        crestPaint.color = SkyGradient.blend(l.skyBottom, 0xFFFFFFFF.toInt(), 0.55f)
        shadowPaint.color = l.shade(0xFF0C4E70.toInt())
        foamPaint.color = l.shade(0xFFFFFFFF.toInt())
        lacePaint.color = l.shade(0xFFFFFFFF.toInt())
        filmPaint.color = l.shade(0xFF86E3D4.toInt())
        turqPaint.color = l.shade(0xFF5CD2C6.toInt())
        sandyPaint.color = SkyGradient.blend(l.shade(0xFF6CD0BE.toInt()), l.ground(0xFFD8C690.toInt()), 0.4f)
        glitter = l.reflection
        glitterX = l.reflectionX * w
        if (glitter != 0) {
            val bottom = glitterBottom()
            // A soft glow that widens from the horizon towards the viewer, the way a real sun path does.
            for (g in 0 until GLOW_LAYERS) {
                val f = 1.15f - 0.28f * g
                val p = glow[g]
                p.rewind()
                p.moveTo(glitterX - halfWidthAt(0f) * f, top)
                p.lineTo(glitterX + halfWidthAt(0f) * f, top)
                p.lineTo(glitterX + halfWidthAt(1f) * f, bottom)
                p.lineTo(glitterX - halfWidthAt(1f) * f, bottom)
                p.close()
            }
            val rgb = glitter and 0x00FFFFFF
            glowPaint.shader = LinearGradient(0f, top, 0f, bottom, rgb or (0x1C shl 24), rgb, Shader.TileMode.CLAMP)
            glitterPaint.color = glitter
        }
        val sky = l.skyBottom and 0x00FFFFFF
        hazePaint.shader = LinearGradient(
            0f, top, 0f, top + h * 0.05f, sky or (0xD0 shl 24), sky, Shader.TileMode.CLAMP,
        )
    }

    /** Swells rolling in over the open water; draw straight after the water body and its glints. */
    fun drawSwells(canvas: Canvas, timeMs: Float, windKmh: Float, shift: Float) {
        val t = timeMs / 1000f
        val rough = roughness(windKmh)
        val base = t * SPEED
        canvas.save()
        canvas.translate(shift, 0f)
        for (j in 0 until WAVES) {
            val pj = fract(base + j / WAVES.toFloat())
            val vis = smooth(0.03f, 0.14f, pj) * (1f - smooth(0.97f, 1f, pj))
            if (vis <= 0.01f) continue
            var n = 0
            var px = 0f; var py = 0f
            var prevOk = false
            for (i in 0..CREST_PTS) {
                val x = crestX(i)
                val p = fract(base + j / WAVES.toFloat() + SLANT * (x / w - 0.5f))
                var y = top + (crestShore[i] - top) * p.pow(1.8f)
                y += sin(x / w * (8f + j * 1.7f) + t * 0.7f + j * 2.1f) * h * 0.0022f * p * rough
                // Dashes: the crest is a broken line, and gets more continuous as the swell steepens.
                val noise = sin(x / w * 17f + j * 5.3f + t * 0.25f) + sin(x / w * 7.1f + j * 2.7f - t * 0.15f)
                val ok = noise > -0.2f - p && abs(p - pj) < 0.5f
                if (i > 0 && ok && prevOk && abs(p - pj) < 0.5f) {
                    crestSegs[n++] = px; crestSegs[n++] = py; crestSegs[n++] = x; crestSegs[n++] = y
                }
                px = x; py = y; prevOk = ok
            }
            if (n == 0) continue
            val steep = pj * pj
            // Trough shadow under the crest, then the lit crest on top.
            shadowPaint.strokeWidth = (1.4f + 3.2f * pj) * unit
            shadowPaint.alpha = (vis * (50f + 70f * pj)).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(0f, (1.2f + 3f * pj) * unit)
            canvas.drawLines(crestSegs, 0, n, shadowPaint)
            canvas.restore()
            crestPaint.strokeWidth = (0.8f + 2.6f * pj) * unit
            crestPaint.alpha = (vis * (40f + 120f * steep) * (0.7f + 0.3f * rough)).toInt().coerceIn(0, 255)
            canvas.drawLines(crestSegs, 0, n, crestPaint)
            // A swell about to break: a white crest tumbling over its face.
            val breaker = smooth(0.74f, 0.88f, pj) * (1f - smooth(0.95f, 0.99f, pj))
            if (breaker > 0.02f) {
                foamPaint.strokeWidth = (1.4f + 2.8f * breaker) * unit
                foamPaint.alpha = (breaker * 230f * (0.75f + 0.25f * rough)).toInt().coerceIn(0, 255)
                canvas.save()
                canvas.translate(0f, -1.5f * unit)
                canvas.drawLines(crestSegs, 0, n, foamPaint)
                canvas.restore()
            }
        }
        canvas.restore()
        // Mist where the sea meets the sky, so the horizon is not a ruled line.
        canvas.drawRect(-w * 0.1f, top, w * 1.1f, top + h * 0.05f, hazePaint)
    }

    /** Sparkles on the water below the sun or moon. Draw after the swells and the horizon haze. */
    fun drawGlitter(canvas: Canvas, timeMs: Float, shift: Float) {
        if (glitter == 0) return
        val t = timeMs / 1000f
        val bottom = glitterBottom()
        canvas.save()
        canvas.translate(shift, 0f)
        for (g in 0 until GLOW_LAYERS) canvas.drawPath(glow[g], glowPaint)
        var n = 0
        for (i in 0 until GLINTS) {
            val d = glintDepth[i]
            val tw = sin(t * (1.1f + glintPhase[i] * 0.2f) + glintPhase[i] * 6.2832f)
            if (tw < 0.1f) continue
            val half = halfWidthAt(d)
            val x = glitterX + glintSide[i] * half + sin(t * 0.9f + glintPhase[i] * 9f) * half * 0.15f
            val y = top + (bottom - top) * d
            val len = unit * (5f + 34f * d) * tw
            glintSegs[n++] = x - len; glintSegs[n++] = y; glintSegs[n++] = x + len; glintSegs[n++] = y
        }
        glitterPaint.strokeWidth = 2.2f * unit
        glitterPaint.alpha = 215
        if (n > 0) canvas.drawLines(glintSegs, 0, n, glitterPaint)
        canvas.restore()
    }

    private fun glitterBottom() = h * 0.82f
    private fun halfWidthAt(d: Float) = w * (0.015f + 0.14f * d)

    /** The shallows tint, clearing towards the sand. Draw after the water and boats, before the sand. */
    fun drawShallows(canvas: Canvas, shift: Float) {
        canvas.save()
        canvas.translate(shift, 0f)
        for (b in 0 until BANDS) {
            val sandy = b >= BANDS - 2
            val paint = if (sandy) sandyPaint else turqPaint
            paint.alpha = if (sandy) 44 else 32
            canvas.drawPath(bands[b], paint)
        }
        canvas.restore()
    }

    /** Swells running up the sand and draining back, with a lacy foam edge. Draw over the sand. */
    fun drawRunup(canvas: Canvas, timeMs: Float, windKmh: Float, shift: Float) {
        val t = timeMs / 1000f
        val rough = roughness(windKmh)
        val base = t * SPEED
        val reachMax = h * 0.024f * (0.75f + 0.25f * rough)
        var meanS = 0f
        for (i in 0..RUN_PTS) {
            val x = runX(i)
            val g = fract((base + SLANT * (x / w - 0.5f)) * WAVES)
            val s = surge(g)
            val amp = 0.7f + 0.3f * sin(x / w * 9f + 2f)
            edgeX[i] = x
            edgeS[i] = s
            edgeY[i] = runShore[i] + reachMax * amp * s + sin(x * 0.045f / unit + t * 1.6f) * h * 0.0012f * s
            meanS += s
        }
        meanS /= (RUN_PTS + 1)

        canvas.save()
        canvas.translate(shift, 0f)
        // The sheet of water: from just inside the sea to its leading edge.
        film.rewind()
        film.moveTo(edgeX[0], runShore[0] - h * 0.01f)
        for (i in 0..RUN_PTS) film.lineTo(edgeX[i], edgeY[i])
        film.lineTo(edgeX[RUN_PTS], runShore[RUN_PTS] - h * 0.01f)
        film.close()
        filmPaint.alpha = (70f + 60f * meanS).toInt()
        canvas.drawPath(film, filmPaint)

        // Foam along the leading edge, broken here and there, and a second finer lace behind it.
        var n = 0
        var m = 0
        for (i in 1..RUN_PTS) {
            val x0 = edgeX[i - 1]; val x1 = edgeX[i]
            val noise = sin(x1 * 0.09f / unit + t * 0.6f) + sin(x1 * 0.23f / unit - t * 0.4f)
            if (noise > -1.4f) {
                edgeSegs[n++] = x0; edgeSegs[n++] = edgeY[i - 1]; edgeSegs[n++] = x1; edgeSegs[n++] = edgeY[i]
            }
            if (noise > 0.1f) {
                val lift = (3f + 5f * edgeS[i]) * unit
                laceSegs[m++] = x0; laceSegs[m++] = edgeY[i - 1] - lift
                laceSegs[m++] = x1; laceSegs[m++] = edgeY[i] - lift
            }
        }
        foamPaint.strokeWidth = 2.6f * unit
        foamPaint.alpha = (150f + 90f * meanS).toInt().coerceIn(0, 255)
        if (n > 0) canvas.drawLines(edgeSegs, 0, n, foamPaint)
        lacePaint.strokeWidth = 1.6f * unit
        lacePaint.alpha = (90f + 80f * meanS).toInt().coerceIn(0, 255)
        if (m > 0) canvas.drawLines(laceSegs, 0, m, lacePaint)
        canvas.restore()
    }

    private fun crestX(i: Int) = -w * 0.1f + w * 1.2f * i / CREST_PTS
    private fun runX(i: Int) = -w * 0.1f + w * 1.2f * i / RUN_PTS

    /** Water rushes up the beach quickly, then drains back slowly. */
    private fun surge(g: Float): Float =
        if (g < 0.3f) {
            val u = g / 0.3f
            1f - (1f - u) * (1f - u)
        } else {
            val u = (g - 0.3f) / 0.7f
            1f - u * u * (3f - 2f * u)
        }

    private fun fract(v: Float) = v - floor(v)

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private companion object {
        const val GLINTS = 64
        const val GLOW_LAYERS = 4
        const val WAVES = 7
        const val CREST_PTS = 22
        const val RUN_PTS = 48
        const val BAND_PTS = 48
        const val BANDS = 7
        /** One swell reaches the shore every [PERIOD] seconds. */
        const val PERIOD = 6.5f
        const val SPEED = 1f / (WAVES * PERIOD)
        /** How far a swell's arrival is staggered along the beach (in swells), so it breaks diagonally. */
        const val SLANT = 0.05f
    }
}
