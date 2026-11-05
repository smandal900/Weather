package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * A tropical island in a turquoise lagoon: a sandy mound with leafy bushes and three swaying
 * palms, foam lapping round its edge, a few hazy islets on the horizon and a sandy bank with a
 * big palm in front. Sea, foam and palms are animated; everything else is painted once.
 */
class TropicalIslandLandscape : PaintedLandscape() {
    override val layers = arrayOf(
        PaintLayer(0.50f, 0.62f, 0.08f), // far islets
        PaintLayer(0.60f, 0.84f, 0.5f),  // the island
        PaintLayer(0.86f, 1f, 1f),       // sand bank in front
    )

    private val water = Water(maxGlints = 64)
    private val foamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        water.build(-w * 0.1f, w * 1.1f, h * 0.6f, h, unit)
    }

    override fun onLightingChanged() {
        water.applyLighting(lighting, LAGOON, width)
        foamPaint.color = lighting.shade(0xFFFFFFFF.toInt())
    }

    override fun paintAll(k: PaintKit): PaintResult {
        val palm = paintPalm(k, 64)
        return PaintResult(arrayOf(islets(k), island(k), bank(k)), if (palm != null) mapOf("palm" to palm) else emptyMap())
    }

    private fun mound(k: PaintKit, c: Canvas, cx: Float, half: Float, hz: Float, hh: Float, top: Int, bottom: Int, bushes: Boolean, rnd: Random) {
        val l = k.l
        val n = 40
        val body = Path()
        body.moveTo(cx - half, hz + 1f)
        for (i in 0..n) {
            val u = i / n.toFloat()
            body.lineTo(cx - half + 2f * half * u, hz - hh * sin(PI.toFloat() * u).coerceAtLeast(0f).pow(1.3f) * (0.88f + 0.12f * sin(u * 21f)))
        }
        body.lineTo(cx + half, hz + 1f)
        body.close()
        k.shader = LinearGradient(0f, hz - hh, 0f, hz, top, bottom, Shader.TileMode.CLAMP)
        k.fill.alpha = 255
        c.drawPath(body, k.fill)
        k.shader = null
        if (!bushes) return
        val lit = l.foliage(0xFF4E9A3E.toInt()); val dark = l.foliage(0xFF1E4A24.toInt())
        for (i in 0 until 16) {
            val u = 0.12f + 0.76f * rnd.nextFloat()
            val topY = hz - hh * sin(PI.toFloat() * u).coerceAtLeast(0f).pow(1.3f)
            val th = hh * (0.28f + 0.3f * rnd.nextFloat())
            k.broadleaf(c, cx - half + 2f * half * u, topY + th * 0.35f, th, k.jitter(lit, rnd, 0.2f), dark, dark, rnd, 0.15f)
        }
    }

    private fun islets(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(91)
        val hz = h * 0.6f
        val col = l.distant(l.foliage(0xFF4A7A58.toInt()), 0.6f)
        mound(k, c, w * 0.18f, w * 0.16f, hz, h * 0.028f, col, l.distant(l.ground(0xFF7C8070.toInt()), 0.65f), false, rnd)
        mound(k, c, w * 0.88f, w * 0.1f, hz, h * 0.02f, col, l.distant(l.ground(0xFF7C8070.toInt()), 0.65f), false, rnd)
        k.haze(c, h * 0.56f, h * 0.61f, 0.4f)
        return bmp
    }

    private fun island(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(92)
        val sandTop = l.ground(0xFFF0DDB0.toInt()); val sandBase = l.ground(0xFFD8BE88.toInt())
        mound(k, c, w * 0.62f, w * 0.34f, h * 0.805f, h * 0.085f, sandTop, sandBase, true, rnd)
        // Wet sand rim and sparkle of grains at the water line.
        k.line.strokeWidth = 2f * k.unit
        k.line.color = k.alpha(l.ground(0xFF8C7650.toInt()), 90)
        c.drawLine(w * 0.3f, h * 0.803f, w * 0.94f, h * 0.803f, k.line)
        repeat((500 * k.unit).toInt()) {
            val x = w * (0.3f + 0.64f * rnd.nextFloat()); val y = h * (0.79f + 0.014f * rnd.nextFloat())
            k.fill.color = k.alpha(if (rnd.nextBoolean()) k.darken(sandTop, 0.25f) else k.lighten(sandTop, 0.4f), 120)
            c.drawCircle(x, y, (0.8f + rnd.nextFloat()) * k.unit, k.fill)
        }
        return bmp
    }

    private fun bank(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(93)
        val body = Path()
        body.moveTo(-k.margin, h)
        body.lineTo(-k.margin, h * 0.935f)
        body.cubicTo(w * 0.1f, h * 0.915f, w * 0.3f, h * 0.935f, w * 0.5f, h * 0.975f)
        body.cubicTo(w * 0.58f, h * 0.99f, w * 0.65f, h * 0.995f, w * 0.7f, h)
        body.lineTo(w * 0.7f, h)
        body.close()
        val dry = l.ground(0xFFEFDCAE.toInt())
        k.shader = LinearGradient(0f, h * 0.91f, 0f, h, l.ground(0xFFD6BC86.toInt()), dry, Shader.TileMode.CLAMP)
        c.drawPath(body, k.fill)
        k.shader = null
        c.save(); c.clipPath(body)
        repeat((1400 * k.unit).toInt()) {
            val x = -k.margin + rnd.nextFloat() * (w * 0.8f)
            val y = h * (0.92f + 0.08f * rnd.nextFloat())
            k.fill.color = k.alpha(if (rnd.nextFloat() < 0.5f) k.darken(dry, 0.3f) else k.lighten(dry, 0.5f), 120)
            c.drawCircle(x, y, (0.8f + rnd.nextFloat() * 1.3f) * k.unit, k.fill)
        }
        c.restore()
        k.rock(c, w * 0.42f, h * 0.99f, w * 0.03f, l.ground(0xFF9C9486.toInt()), rnd)
        return bmp
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = timeMs / 1000f
        val sway = windKmh / 14f
        when (stage) {
            1 -> water.draw(canvas, timeMs, windKmh, shift(0.4f))
            2 -> {
                // Foam lapping round the island, then the island's palms.
                canvas.save(); canvas.translate(shift(0.5f), 0f)
                val rough = 0.6f + (windKmh / 40f).coerceAtMost(1f)
                for (i in 0 until 26) {
                    val u = i / 25f
                    val x = w * (0.28f + 0.68f * u)
                    val pulse = 0.5f + 0.5f * sin(t * 0.9f + i * 1.9f)
                    foamPaint.alpha = (190 * pulse * (0.6f + 0.4f * rough)).toInt().coerceIn(0, 255)
                    canvas.drawCircle(x, h * 0.808f + h * 0.002f * sin(t * 1.2f + i), w * (0.006f + 0.008f * pulse * rough), foamPaint)
                }
                val palm = sprites["palm"]
                if (palm != null) {
                    drawPalm(canvas, palm, w * 0.50f, h * 0.752f, 0.3f, t, sway, 0f)
                    drawPalm(canvas, palm, w * 0.66f, h * 0.742f, 0.4f, t, sway, 1.3f)
                    drawPalm(canvas, palm, w * 0.80f, h * 0.76f, 0.28f, t, sway, 2.6f)
                }
                canvas.restore()
            }
            layers.size -> {
                val palm = sprites["palm"] ?: return
                canvas.save(); canvas.translate(shift(1f), 0f)
                drawPalm(canvas, palm, w * 0.12f, h * 0.955f, 0.85f, t, sway, 0.7f)
                canvas.restore()
            }
        }
    }

    private fun drawPalm(canvas: Canvas, palm: Bitmap, x: Float, y: Float, scale: Float, t: Float, sway: Float, phase: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        canvas.rotate(sin(t * (0.8f + windKmh / 30f) + phase) * (0.8f + sway))
        canvas.drawBitmap(palm, -palm.width * 0.55f, -palm.height + 2f, spritePaint)
        canvas.restore()
    }

    private companion object {
        const val LAGOON = 0xFF16808E.toInt()
    }
}
