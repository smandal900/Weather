package com.skymood.wallpaper.scenes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the two kinds of eclipse. Progress runs 0 (first contact) .. 0.5 (greatest eclipse) ..
 * 1 (last contact).
 *
 * Solar: a dark moon disc slides across the sun. Near totality the sky and landscape darken, a
 * diamond ring flashes at the last sliver of sun, and the corona shows around the black disc.
 * Lunar: the Earth's shadow sweeps across a full moon, which turns a dim copper red inside it.
 *
 * Shaders and the corona sprite are built in [setupSolar] / [setupLunar]; drawing does not allocate.
 */
class EclipsePainter {

    private val overlayPaint = Paint()
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF06070C.toInt() }
    private val coronaPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shadowShader: RadialGradient? = null
    private val shadowMatrix = Matrix()
    private val coronaDst = RectF()
    private var corona: Bitmap? = null
    private var sunR = 1f
    private var moonR = 1f

    /** Offset of the occulting body's centre from the occulted one's, set by the coverage functions. */
    private var offX = 0f
    private var offY = 0f

    fun setupSolar(sunRadius: Float) {
        sunR = sunRadius
        // Darkness over the whole screen with a bright hole around the sun itself.
        val dark = 0x0B1230
        overlayPaint.shader = RadialGradient(
            0f, 0f, sunR * 5f,
            intArrayOf(dark, dark, 0xFF000000.toInt() or dark),
            floatArrayOf(0f, 1.15f / 5f, 1.7f / 5f), Shader.TileMode.CLAMP,
        )
        ringPaint.shader = RadialGradient(
            0f, 0f, sunR * 0.9f,
            intArrayOf(Color.WHITE, 0xCCFFF2CC.toInt(), Color.TRANSPARENT), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP,
        )
        streakPaint.shader = RadialGradient(
            0f, 0f, sunR * 2.4f,
            intArrayOf(0x99FFF6DC.toInt(), 0x33FFE9B0, Color.TRANSPARENT), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP,
        )
        bakeCorona()
    }

    fun setupLunar(moonRadius: Float) {
        moonR = moonRadius
        // Colours across the umbra: deep copper in the middle, a soft edge where it meets the bright moon.
        shadowShader = RadialGradient(
            0f, 0f, moonR * UMBRA,
            intArrayOf(0xF2561A0C.toInt(), 0xF08A3516.toInt(), 0xE6A8481E.toInt(), 0x883E2420.toInt(), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 0.86f, 0.965f, 1f), Shader.TileMode.CLAMP,
        )
    }

    private fun bakeCorona() {
        corona?.recycle()
        val reach = sunR * CORONA_REACH
        val scale = 0.5f
        val size = (reach * 2f * scale).toInt().coerceAtLeast(8)
        val bmp = runCatching { Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888) }.getOrNull()
        corona = bmp
        bmp ?: return
        val c = Canvas(bmp)
        c.translate(size / 2f, size / 2f)
        c.scale(scale, scale)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            0f, 0f, reach,
            intArrayOf(0xF0F4F8FF.toInt(), 0xDDEAF0FF.toInt(), 0x70C8D4F4, 0x24A8B8E0, Color.TRANSPARENT),
            floatArrayOf(0f, 1f / CORONA_REACH, 0.4f, 0.68f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawCircle(0f, 0f, reach, p)
        // Streamers: long, uneven plumes, longest near the equator and short at the poles.
        val rnd = java.util.Random(11)
        val path = Path()
        val count = 16
        for (i in 0 until count) {
            val a = (i + (rnd.nextFloat() - 0.5f) * 0.6f) / count * 2.0 * PI
            val equatorial = abs(cos(a)).toFloat()
            val len = reach * (0.45f + 0.5f * equatorial) * (0.8f + 0.2f * rnd.nextFloat())
            val half = 0.10 + 0.08 * rnd.nextFloat()
            p.shader = RadialGradient(
                0f, 0f, len,
                intArrayOf(0xB8F4F8FF.toInt(), 0x66DCE6FF, 0x22C8D4F4, Color.TRANSPARENT),
                floatArrayOf(0.2f, 0.45f, 0.75f, 1f), Shader.TileMode.CLAMP,
            )
            for (layer in 0 until 2) {
                val h = half * (1.0 - layer * 0.45)
                p.alpha = if (layer == 0) 150 else 255
                path.reset()
                path.moveTo(0f, 0f)
                path.lineTo((cos(a - h) * len).toFloat(), (sin(a - h) * len).toFloat())
                path.lineTo((cos(a + h) * len).toFloat(), (sin(a + h) * len).toFloat())
                path.close()
                c.drawPath(path, p)
            }
        }
    }

    // ---- Solar -------------------------------------------------------------------------------

    /** Share of the sun's disc hidden by the moon, 0..1. Also records where the moon is. */
    fun solarCoverage(progress: Float): Float {
        val s = ease((progress - 0.5f) * 2f)
        val ang = Math.toRadians(-18.0)
        val ux = cos(ang).toFloat()
        val uy = sin(ang).toFloat()
        val along = -s * SOLAR_SPAN * sunR
        val across = 0.04f * sunR
        offX = ux * along - uy * across
        offY = uy * along + ux * across
        val d = sqrt(offX * offX + offY * offY)
        return (overlap(d, sunR, sunR * MOON_RATIO) / (PI.toFloat() * sunR * sunR)).coerceIn(0f, 1f)
    }

    /**
     * Darkens the sky and landscape, then draws the corona, the diamond ring and the dark moon.
     * Call after [solarCoverage]. [cx], [cy] is the sun's centre on screen.
     */
    fun drawSolar(canvas: Canvas, cx: Float, cy: Float, w: Int, h: Int, cov: Float, timeMs: Float) {
        if (cov <= 0.001f) return
        val dark = (0.8f * cov * cov * cov).coerceIn(0f, 1f)
        if (dark > 0.01f) {
            overlayPaint.alpha = (255 * dark).toInt()
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawRect(-cx - 2f, -cy - 2f, w - cx + 2f, h - cy + 2f, overlayPaint)
            canvas.restore()
        }
        val mx = cx + offX
        val my = cy + offY
        val totality = smooth(0.93f, 1f, cov)
        val sprite = corona
        if (totality > 0.01f && sprite != null) {
            val reach = sunR * CORONA_REACH
            val breathe = 1f + 0.015f * sin(timeMs / 1700f)
            coronaDst.set(-reach * breathe, -reach * breathe, reach * breathe, reach * breathe)
            coronaPaint.alpha = (255 * totality).toInt()
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawBitmap(sprite, null, coronaDst, coronaPaint)
            canvas.restore()
        }
        // Diamond ring: the last sliver of sun flares on the limb away from the moon.
        val ring = smooth(0.94f, 0.985f, cov) * (1f - smooth(0.992f, 1f, cov))
        if (ring > 0.01f) {
            val len = sqrt(offX * offX + offY * offY).coerceAtLeast(0.001f)
            val lx = cx - offX / len * sunR
            val ly = cy - offY / len * sunR
            ringPaint.alpha = (255 * ring).toInt()
            streakPaint.alpha = (200 * ring).toInt()
            canvas.save()
            canvas.translate(lx, ly)
            canvas.drawCircle(0f, 0f, sunR * 0.9f, ringPaint)
            canvas.scale(1f, 0.12f)
            canvas.drawCircle(0f, 0f, sunR * 2.4f, streakPaint)
            canvas.restore()
        }
        canvas.drawCircle(mx, my, sunR * MOON_RATIO, discPaint)
    }

    // ---- Lunar -------------------------------------------------------------------------------

    /** Share of the moon's disc inside the Earth's shadow, 0..1. Also records where the shadow is. */
    fun lunarCoverage(progress: Float): Float {
        val s = (progress - 0.5f) * 2f
        val ang = Math.toRadians(-14.0)
        val ux = cos(ang).toFloat()
        val uy = sin(ang).toFloat()
        val along = s * LUNAR_SPAN * moonR
        val across = 0.3f * moonR
        offX = ux * along - uy * across
        offY = uy * along + ux * across
        val d = sqrt(offX * offX + offY * offY)
        return (overlap(d, moonR * UMBRA, moonR) / (PI.toFloat() * moonR * moonR)).coerceIn(0f, 1f)
    }

    /** Paints the shadow over the moon already drawn at [cx], [cy]; [alpha] is 0..1. */
    fun drawLunar(canvas: Canvas, cx: Float, cy: Float, cov: Float, alpha: Float) {
        val shader = shadowShader ?: return
        if (cov <= 0.001f) return
        shadowMatrix.setTranslate(offX, offY)
        shader.setLocalMatrix(shadowMatrix)
        shadowPaint.shader = shader
        shadowPaint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawCircle(0f, 0f, moonR, shadowPaint)
        canvas.restore()
    }

    // ---- Helpers -----------------------------------------------------------------------------

    /** Slows the movement near greatest eclipse, so totality lasts a good part of the slider. */
    private fun ease(s: Float): Float {
        val m = Math.pow(abs(s).toDouble(), 1.8).toFloat()
        return if (s < 0f) -m else m
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Area where two circles of radius [r1] and [r2], [d] apart, overlap. */
    private fun overlap(d: Float, r1: Float, r2: Float): Float {
        if (d >= r1 + r2) return 0f
        if (d <= abs(r1 - r2)) return PI.toFloat() * min(r1, r2) * min(r1, r2)
        val a = r1 * r1 * acos(((d * d + r1 * r1 - r2 * r2) / (2f * d * r1)).coerceIn(-1f, 1f))
        val b = r2 * r2 * acos(((d * d + r2 * r2 - r1 * r1) / (2f * d * r2)).coerceIn(-1f, 1f))
        val c = 0.5f * sqrt(max(0f, (-d + r1 + r2) * (d + r1 - r2) * (d - r1 + r2) * (d + r1 + r2)))
        return a + b - c
    }

    private companion object {
        /** The moon looks a little bigger than the sun here, so totality lasts a while. */
        const val MOON_RATIO = 1.10f
        /** Distance the moon travels from first contact to the greatest eclipse, in sun radii. */
        const val SOLAR_SPAN = 2.14f
        /** The Earth's shadow is about 2.6 moon radii across at the moon. */
        const val UMBRA = 2.6f
        const val LUNAR_SPAN = 3.7f
        const val CORONA_REACH = 4.6f
    }
}
