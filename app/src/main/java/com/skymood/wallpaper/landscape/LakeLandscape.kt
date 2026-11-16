package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import com.skymood.wallpaper.fauna.FlowerBed
import com.skymood.wallpaper.fauna.GroundSlot
import com.skymood.wallpaper.fauna.GroundY
import com.skymood.wallpaper.fauna.MeadowSpec
import com.skymood.wallpaper.fauna.Perch
import com.skymood.wallpaper.fauna.Species
import com.skymood.wallpaper.scenes.SkyGradient
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private const val LAKE_BED = 0xFF173F4A.toInt()

/** Fractions of the screen height: the far shore, and where the water meets the near bank. */
private const val HORIZON = 0.66f
private const val WATER_BOTTOM = 0.9f

/** How much shorter than the real thing the mirrored shore is, so it fits the water below it. */
private const val MIRROR_SQUASH = 0.8f
private const val MIRROR_COLS = 4
private const val MAX_MIRROR_ROWS = 200

private const val LANES = 20
private const val SPARKLES = 44
private const val GLITTER = 52
private const val WISPS = 4
private const val PADS = 20
private const val CLUMPS = 9

private const val ISLET_X = 0.7f
private const val ISLET_BASE = 0.708f

/** A cheap deterministic hash to a float in 0..1, so ripples can pick a new spot every cycle without state. */
private fun hash01(n: Int): Float {
    var x = n * -1640531535 // 0x9E3779B1
    x = x xor (x ushr 15)
    x *= 0x2C1B3C6D
    x = x xor (x ushr 12)
    x *= 0x297A2D39
    x = x xor (x ushr 15)
    return (x and 0xFFFFFF) / 16777216f
}

/**
 * A mountain lake. Two faceted ranges and a forested shore stand on the far side and are mirrored
 * in the water through a rippling mesh that fades with distance, so the reflection wobbles in the
 * wind and dissolves into the deep water like a real one. On the surface: broad wind streaks,
 * sparkles, a sun or moon glitter path, a fish rising now and then, rings from falling rain, mist
 * drifting over the far shore, a forested islet, lily pads and reeds with cattails on the near bank.
 */
class LakeLandscape : PaintedLandscape() {
    private var meadowSpec: MeadowSpec? = null
    private var meadowFor = 0
    override fun meadow(): MeadowSpec {
        if (meadowSpec == null || meadowFor != width * 31 + height) {
            meadowFor = width * 31 + height
            val w = width.toFloat(); val h = height.toFloat()
            val r = Ridge(width, h * 0.89f, h * 0.02f, false, 33)
            meadowSpec = MeadowSpec(
                listOf(FlowerBed(0.02f, 0.98f, GroundY { x -> r.yAt(x) + h * 0.01f }, h * 0.07f, 14)),
                // A snag out in the water, fished from the far bank side of the ducks.
                Perch(w * 0.82f, h * 0.835f, h * 0.045f, h * 0.8f, 1.2f),
            )
        }
        return meadowSpec!!
    }

    override val layers = arrayOf(
        PaintLayer(0.38f, HORIZON, 0.03f), // the two mountain ranges
        PaintLayer(0.56f, HORIZON, 0.1f),  // forest on the far shore
        PaintLayer(0.82f, 1f, 1f),         // near bank
    )

    private var slots: List<GroundSlot> = emptyList()
    private var slotsFor = 0
    override fun groundSlots(): List<GroundSlot> {
        if (slotsFor != width * 31 + height) {
            slotsFor = width * 31 + height
            slots = makeSlots(width.toFloat(), height.toFloat())
        }
        return slots
    }

    private fun makeSlots(w: Float, h: Float): List<GroundSlot> {
        val bank = Ridge(width, h * 0.89f, h * 0.02f, false, 33)
        return listOf(
            GroundSlot(Species.DUCK, 0.15f, 0.85f, GroundY { _ -> h * 0.77f }, 1.3f, 3),
            // Frogs along the reeds in rain and after dark; wild cattle come down to drink by day.
            GroundSlot(Species.FROG, 0.08f, 0.92f, GroundY { x -> bank.yAt(x) + h * 0.03f }, 1.6f, 4),
            GroundSlot(Species.WILD_COW, 0.3f, 0.7f, GroundY { x -> bank.yAt(x) + h * 0.075f }, 1f, 2),
        )
    }

    // ------------------------------------------------------------------ animated water

    private val bodyPaint = Paint()
    private val veilPaint = Paint()
    private val mirrorPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 235 }
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val lightLane = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val darkLane = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val glitterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val mistPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val padPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val padLitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val petalPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val verts = FloatArray((MIRROR_COLS + 1) * (MAX_MIRROR_ROWS + 1) * 2)

    private var reflecting = false
    private var rings = 0
    private var mist = 0.12f

    private val laneD = FloatArray(LANES)
    private val laneX = FloatArray(LANES)
    private val laneLen = FloatArray(LANES)
    private val lanePh = FloatArray(LANES)
    private val laneAlpha = FloatArray(LANES)
    private val laneIsLight = BooleanArray(LANES)

    private val spX = FloatArray(SPARKLES)
    private val spD = FloatArray(SPARKLES)
    private val spP = FloatArray(SPARKLES)

    private val glD = FloatArray(GLITTER)
    private val glO = FloatArray(GLITTER)
    private val glP = FloatArray(GLITTER)

    private val wispX = FloatArray(WISPS)
    private val wispY = FloatArray(WISPS)
    private val wispW = FloatArray(WISPS)
    private val wispSpeed = FloatArray(WISPS)

    private val padX = FloatArray(PADS)
    private val padY = FloatArray(PADS)
    private val padR = FloatArray(PADS)
    private val padPh = FloatArray(PADS)
    private val padRot = FloatArray(PADS)
    private val padFlower = BooleanArray(PADS)

    // ------------------------------------------------------------------ reeds

    private val reedBack = Array(CLUMPS) { Path() }
    private val reedFront = Array(CLUMPS) { Path() }
    private val cattails = Array(CLUMPS) { Path() }
    private val clumpX = FloatArray(CLUMPS)
    private val clumpY = FloatArray(CLUMPS)
    private val clumpPh = FloatArray(CLUMPS)
    private val reedBackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reedFrontPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cattailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        val rnd = Random(81)
        for (i in 0 until LANES) {
            val r = rnd.nextFloat()
            laneD[i] = 0.03f + 0.95f * r * r // streaks bunch up towards the horizon, like perspective
            laneX[i] = -w * 0.1f + rnd.nextFloat() * w * 1.2f
            laneLen[i] = w * (0.12f + 0.3f * rnd.nextFloat())
            lanePh[i] = rnd.nextFloat() * 6.28f
            laneAlpha[i] = 26f + rnd.nextFloat() * 28f
            laneIsLight[i] = rnd.nextFloat() < 0.55f
        }
        for (i in 0 until SPARKLES) {
            val r = rnd.nextFloat()
            spD[i] = r * r
            spX[i] = -w * 0.1f + rnd.nextFloat() * w * 1.2f
            spP[i] = rnd.nextFloat() * 6.28f
        }
        for (i in 0 until GLITTER) {
            val r = rnd.nextFloat()
            glD[i] = r * r * 0.9f + 0.01f
            glO[i] = rnd.nextFloat() + rnd.nextFloat() - 1f // clustered round the middle of the path
            glP[i] = rnd.nextFloat() * 6.28f
        }
        for (i in 0 until WISPS) {
            wispX[i] = rnd.nextFloat() * w * 1.6f
            wispY[i] = h * (HORIZON + (rnd.nextFloat() - 0.4f) * 0.03f)
            wispW[i] = w * (0.3f + 0.3f * rnd.nextFloat())
            wispSpeed[i] = (3f + 6f * rnd.nextFloat()) * unit
        }
        val hz = h * HORIZON; val wh = h * (WATER_BOTTOM - HORIZON)
        for (i in 0 until PADS) {
            val second = i >= 14
            val cx = if (second) w * 0.62f else w * 0.2f
            val spread = if (second) w * 0.08f else w * 0.17f
            padX[i] = cx + (rnd.nextFloat() + rnd.nextFloat() - 1f) * spread
            padY[i] = h * (0.822f + 0.062f * rnd.nextFloat())
            val d = ((padY[i] - hz) / wh).coerceIn(0f, 1f)
            padR[i] = unit * (9f + 26f * d) * (0.75f + 0.5f * rnd.nextFloat())
            padPh[i] = rnd.nextFloat() * 6.28f
            padRot[i] = rnd.nextFloat() * 360f
            padFlower[i] = i % 7 == 3
        }

        // Reed clumps: tapering blades with a few tall stems carrying cattail heads.
        val bank = Ridge(width, h * 0.89f, h * 0.02f, false, 33)
        for (c in 0 until CLUMPS) {
            reedBack[c].rewind(); reedFront[c].rewind(); cattails[c].rewind()
            val cx = w * (0.03f + 0.94f * (c + 0.2f + 0.6f * rnd.nextFloat()) / CLUMPS)
            clumpX[c] = cx
            clumpY[c] = bank.yAt(cx) + h * 0.012f
            clumpPh[c] = rnd.nextFloat() * 6.28f
            val blades = 8 + rnd.nextInt(6)
            for (b in 0 until blades) {
                val back = b % 3 == 0
                val bx = (rnd.nextFloat() - 0.5f) * w * 0.035f
                val bh = h * (0.03f + 0.05f * rnd.nextFloat()) * (if (back) 0.85f else 1f)
                val lean = (bx / (w * 0.0175f)) * bh * 0.12f + (rnd.nextFloat() - 0.5f) * bh * 0.2f
                val wd = (1.3f + 1.2f * rnd.nextFloat()) * unit.coerceAtLeast(0.6f)
                val p = if (back) reedBack[c] else reedFront[c]
                p.moveTo(bx - wd, 0f)
                p.quadTo(bx + lean * 0.15f - wd * 0.4f, -bh * 0.6f, bx + lean, -bh)
                p.quadTo(bx + lean * 0.15f + wd * 0.4f, -bh * 0.6f, bx + wd, 0f)
                p.close()
                if (!back && bh > h * 0.058f) {
                    val cy = -bh * 0.8f
                    val cxs = bx + lean * 0.8f
                    cattails[c].moveTo(cxs, cy)
                    cattails[c].lineTo(cxs + lean * 0.02f, cy - h * 0.013f)
                }
            }
        }
        cattailPaint.strokeWidth = 4.2f * unit.coerceAtLeast(0.6f)
    }

    override fun onLightingChanged() {
        val h = height.toFloat()
        val l = lighting
        val hz = h * HORIZON; val wb = h * WATER_BOTTOM
        val deep = l.shade(LAKE_BED)
        val sky = l.skyBottom

        // The water is lightest where it reflects the sky near the far shore and darkens towards us.
        bodyPaint.shader = LinearGradient(
            0f, hz, 0f, wb,
            intArrayOf(SkyGradient.blend(sky, deep, 0.22f), SkyGradient.blend(sky, deep, 0.5f), deep),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP,
        )
        // Laid over the mirrored shore so the reflection grows fainter, and murkier, with distance.
        veilPaint.shader = LinearGradient(
            0f, hz, 0f, wb,
            intArrayOf(
                SkyGradient.blend(sky, deep, 0.4f) and 0x00FFFFFF or (62 shl 24),
                SkyGradient.blend(sky, deep, 0.6f) and 0x00FFFFFF or (125 shl 24),
                deep and 0x00FFFFFF or (246 shl 24),
            ),
            floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP,
        )
        lightLane.color = SkyGradient.blend(sky, Color.WHITE, 0.35f)
        darkLane.color = l.shade(0xFF0A2229.toInt())
        sparklePaint.color = SkyGradient.blend(sky, Color.WHITE, 0.6f)
        ringPaint.color = SkyGradient.blend(sky, Color.WHITE, 0.7f)
        mistPaint.shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(SkyGradient.blend(sky, Color.WHITE, 0.3f) and 0x00FFFFFF or (0xB0 shl 24), SkyGradient.blend(sky, Color.WHITE, 0.3f) and 0x00FFFFFF or (0x50 shl 24), 0),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        reflecting = l.reflection != 0
        if (reflecting) {
            glitterPaint.color = l.reflection
            val rc = l.reflection and 0x00FFFFFF
            glowPaint.shader = RadialGradient(
                0f, 0f, 1f, intArrayOf(rc or (95 shl 24), rc or (45 shl 24), 0), floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        rings = when (l.weather) {
            SceneType.DRIZZLE -> 12
            SceneType.HEAVY_RAIN -> 28
            SceneType.STORM -> 36
            else -> 0
        }
        mist = when {
            l.weather == SceneType.FOG -> 0.55f
            l.timeOfDay == TimeOfDay.DAWN -> 0.4f
            l.weather == SceneType.OVERCAST || l.weather == SceneType.DRIZZLE || l.weather == SceneType.HAZE -> 0.3f
            l.timeOfDay == TimeOfDay.DUSK -> 0.22f
            else -> 0.12f
        }
        padPaint.color = l.foliage(0xFF3C7330.toInt())
        padLitPaint.color = l.foliage(0xFF6CA648.toInt())
        petalPaint.color = l.shade(0xFFFDF3F6.toInt())
        corePaint.color = l.shade(0xFFF2B63A.toInt())
        reedBackPaint.color = l.foliage(0xFF3A6428.toInt())
        reedFrontPaint.color = l.foliage(0xFF77A33E.toInt())
        cattailPaint.color = l.shade(0xFF5A3A22.toInt())
    }

    override fun drawAnimated(canvas: Canvas, stage: Int) {
        if (stage == 2) drawWater(canvas)
        else if (stage == layers.size) drawReeds(canvas)
    }

    private fun drawWater(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        val hz = h * HORIZON; val wb = h * WATER_BOTTOM; val wh = wb - hz
        val t = timeMs / 1000f
        val left = -w * 0.1f; val right = w * 1.1f
        val span = right - left
        val sh = shift(0.5f)

        canvas.save()
        canvas.clipRect(left, hz, right, wb)
        canvas.drawRect(left, hz, right, wb, bodyPaint)

        // The far shore, mirrored.
        bitmap(0)?.let { drawMirror(canvas, it, -margin + shift(layers[0].depth), h * layers[0].top, hz, t, unit, hz, wh) }
        bitmap(1)?.let { drawMirror(canvas, it, -margin + shift(layers[1].depth), h * layers[1].top, hz, t, unit, hz, wh) }
        val islet = sprites["islet"]
        val ix = w * ISLET_X + shift(0.15f) - (islet?.width ?: 0) / 2f
        val iy = h * ISLET_BASE - (islet?.height ?: 0)
        if (islet != null) drawMirror(canvas, islet, ix, iy, h * ISLET_BASE, t, unit, hz, wh)
        canvas.drawRect(left, hz, right, wb, veilPaint)

        // Broad streaks where the wind brushes the surface, drifting slowly with it.
        val drift = t * (2f + windKmh * 0.15f) * unit * 6f
        for (i in 0 until LANES) {
            val d = laneD[i]
            val y = hz + d * wh
            val x = left + (laneX[i] - left + drift * (0.3f + d)) % span
            val len = laneLen[i] * (0.4f + 0.8f * d) * 0.5f
            val p = if (laneIsLight[i]) lightLane else darkLane
            p.strokeWidth = (1.5f + 7f * d) * unit
            p.alpha = (laneAlpha[i] * (0.55f + 0.45f * sin(t * 0.5f + lanePh[i]))).toInt().coerceIn(0, 255)
            canvas.drawLine(x - len + sh, y, x + len + sh, y, p)
        }

        // Sparkles of sky catching the ripples; livelier with wind.
        val sdrift = t * (6f + windKmh * 0.8f)
        sparklePaint.strokeWidth = 2f * unit.coerceAtLeast(0.6f)
        for (i in 0 until SPARKLES) {
            val a = sin(t * 1.4f + spP[i])
            if (a <= 0.25f) continue
            val d = spD[i]
            val x = left + (spX[i] - left + sdrift * (0.3f + d)) % span
            val len = unit * (6f + 30f * d) * a
            val y = hz + wh * 0.01f + d * wh * 0.97f
            sparklePaint.alpha = ((70f + windKmh * 2f).coerceIn(50f, 150f) * a).toInt().coerceIn(0, 255)
            canvas.drawLine(x - len + sh, y, x + len + sh, y, sparklePaint)
        }

        // Sun or moon: a soft glow and a column of glitter widening towards us.
        if (reflecting) {
            val rx = w * lighting.reflectionX + sh
            canvas.save()
            canvas.translate(rx, hz + wh * 0.32f)
            canvas.scale(w * 0.1f, wh * 0.62f)
            canvas.drawCircle(0f, 0f, 1f, glowPaint)
            canvas.restore()
            for (i in 0 until GLITTER) {
                val a = sin(t * 1.7f + glP[i])
                if (a < 0.1f) continue
                val d = glD[i]
                val y = hz + wh * 0.012f + d * wh * 0.95f
                val spread = w * (0.025f + 0.11f * d)
                val x = rx + glO[i] * spread + sin(t * 0.6f + glP[i] * 2f) * spread * 0.15f
                val len = unit * (5f + 34f * d) * a
                glitterPaint.alpha = (230f * a * (1f - abs(glO[i]) * 0.55f) * (1f - 0.25f * d)).toInt().coerceIn(0, 255)
                glitterPaint.strokeWidth = (1.6f + 2.6f * d) * unit
                canvas.drawLine(x - len, y, x + len, y, glitterPaint)
            }
        }
        canvas.restore()

        // The islet stands in front of the glitter and streaks, not under them.
        if (islet != null) canvas.drawBitmap(islet, ix, iy, spritePaint)

        canvas.save()
        canvas.clipRect(left, hz, right, wb)
        drawRipples(canvas, t, unit, hz, wh, w)
        drawPads(canvas, t, unit)
        drawMist(canvas, t, w)
        canvas.restore()
    }

    /**
     * Draws [bmp], whose top-left corner is at ([left], [top]), upside down about the water line at
     * [mirrorY]. The mesh is cut into thin rows and each row is shoved sideways by a travelling
     * wave that grows towards the viewer, which is what makes a reflection shimmer and break up.
     */
    private fun drawMirror(
        canvas: Canvas, bmp: Bitmap, left: Float, top: Float, mirrorY: Float, t: Float, unit: Float,
        horizon: Float, waterH: Float,
    ) {
        val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
        val rows = ((bh * MIRROR_SQUASH) / (4f * unit.coerceAtLeast(0.5f))).toInt().coerceIn(8, MAX_MIRROR_ROWS)
        val wind = (0.7f + windKmh / 15f).coerceAtMost(3f)
        var i = 0
        for (r in 0..rows) {
            val srcY = top + bh * (r / rows.toFloat())
            val my = mirrorY + (mirrorY - srcY) * MIRROR_SQUASH
            val d = ((my - horizon) / waterH).coerceIn(0.01f, 1f)
            val phase = d.pow(0.55f) * 46f
            val amp = unit * (0.7f + 5.5f * d) * wind
            val dx = amp * (0.7f * sin(phase - t * 0.9f) + 0.3f * sin(phase * 2.3f + t * 1.4f))
            for (c in 0..MIRROR_COLS) {
                verts[i++] = left + bw * c / MIRROR_COLS + dx * (1f + 0.25f * sin(c * 1.7f + phase * 0.5f + t * 0.3f))
                verts[i++] = my
            }
        }
        canvas.drawBitmapMesh(bmp, MIRROR_COLS, rows, verts, 0, null, 0, mirrorPaint)
    }

    /** Rings from falling rain in wet weather; a fish rising now and then when it is calm. */
    private fun drawRipples(canvas: Canvas, t: Float, unit: Float, hz: Float, wh: Float, w: Float) {
        ringPaint.strokeWidth = 1.4f * unit.coerceAtLeast(0.6f)
        for (i in 0 until rings) {
            val x0 = t / (0.8f + 0.9f * hash01(i * 7 + 1)) + hash01(i * 13 + 2)
            val cycle = floor(x0).toInt()
            val u = x0 - cycle
            val d = hash01(i * 53 + cycle * 331 + 5).pow(1.5f)
            val px = -w * 0.05f + hash01(i * 101 + cycle * 977 + 3) * w * 1.1f
            val py = hz + wh * 0.015f + d * wh * 0.96f
            val rx = (3f + 22f * u) * unit * (0.35f + 1.1f * d)
            val ry = rx * (0.22f + 0.15f * d)
            ringPaint.alpha = (150f * (1f - u) * (0.5f + 0.5f * d)).toInt().coerceIn(0, 255)
            canvas.drawOval(px - rx, py - ry, px + rx, py + ry, ringPaint)
        }
        if (rings == 0) {
            for (j in 0 until 3) {
                val x0 = t / (9f + 5f * hash01(j * 17 + 9)) + hash01(j * 29 + 4)
                val cycle = floor(x0).toInt()
                val u = (x0 - cycle) / 0.2f // the rise lasts a fifth of the cycle; the rest of the time the lake is still
                if (u >= 1f) continue
                val d = 0.12f + 0.7f * hash01(j * 61 + cycle * 211 + 7)
                val px = w * (0.1f + 0.8f * hash01(j * 83 + cycle * 149 + 11))
                val py = hz + d * wh
                val rx = (2f + 28f * u) * unit * (0.4f + 1.1f * d)
                ringPaint.alpha = (170f * (1f - u)).toInt().coerceIn(0, 255)
                canvas.drawOval(px - rx, py - rx * 0.3f, px + rx, py + rx * 0.3f, ringPaint)
                if (u > 0.25f) {
                    val r2 = rx * 0.55f
                    canvas.drawOval(px - r2, py - r2 * 0.3f, px + r2, py + r2 * 0.3f, ringPaint)
                }
            }
        }
    }

    private fun drawPads(canvas: Canvas, t: Float, unit: Float) {
        val sh = shift(0.9f)
        for (i in 0 until PADS) {
            val x = padX[i] + sh + sin(t * 0.5f + padPh[i]) * 1.2f * unit
            val y = padY[i] + sin(t * 0.9f + padPh[i]) * 0.7f * unit
            val rx = padR[i]; val ry = rx * 0.3f
            rect.set(x - rx, y - ry, x + rx, y + ry)
            canvas.drawArc(rect, padRot[i], 335f, true, padPaint)
            rect.set(x - rx * 0.7f, y - ry * 0.85f, x + rx * 0.55f, y + ry * 0.4f)
            padLitPaint.alpha = 150
            canvas.drawArc(rect, padRot[i], 335f, true, padLitPaint)
            if (padFlower[i]) {
                val fr = rx * 0.32f
                for (p in 0 until 5) {
                    val a = p * 1.2566f
                    canvas.drawCircle(x + kotlin.math.cos(a) * fr * 0.8f, y - ry * 0.7f + sin(a) * fr * 0.35f, fr * 0.55f, petalPaint)
                }
                canvas.drawCircle(x, y - ry * 0.7f, fr * 0.4f, corePaint)
            }
        }
    }

    /** Low mist drifting over the far shore: thick at dawn and in fog, a trace on a clear afternoon. */
    private fun drawMist(canvas: Canvas, t: Float, w: Float) {
        val h = height.toFloat()
        mistPaint.alpha = (255 * mist).toInt().coerceIn(0, 255)
        val span = w * 1.6f
        for (i in 0 until WISPS) {
            val x = (wispX[i] + t * wispSpeed[i]) % span - w * 0.3f
            canvas.save()
            canvas.translate(x, wispY[i])
            canvas.scale(wispW[i], h * 0.022f)
            canvas.drawCircle(0f, 0f, 1f, mistPaint)
            canvas.restore()
        }
    }

    private fun drawReeds(canvas: Canvas) {
        val t = timeMs / 1000f
        val k = 0.012f + windKmh / 900f
        val sh = shift(1f)
        for (c in 0 until CLUMPS) {
            val sway = sin(t * (1f + windKmh / 25f) + clumpPh[c]) * k + sin(t * 0.37f + clumpPh[c] * 2f) * k * 0.5f
            canvas.save()
            canvas.translate(clumpX[c] + sh, clumpY[c])
            canvas.skew(sway, 0f)
            canvas.drawPath(reedBack[c], reedBackPaint)
            canvas.drawPath(reedFront[c], reedFrontPaint)
            canvas.drawPath(cattails[c], cattailPaint)
            canvas.restore()
        }
    }

    // ------------------------------------------------------------------ painted scenery

    override fun paintAll(k: PaintKit): PaintResult {
        val sp = HashMap<String, Bitmap>()
        islet(k)?.let { sp["islet"] = it }
        return PaintResult(arrayOf(mountains(k), shore(k), nearBank(k)), sp)
    }

    /** A high, snowy range far off and a lower, darker, bluer one in front, so the mountains recede. */
    private fun mountains(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[0]) ?: return null
        val l = k.l; val h = k.h
        val hz = h * HORIZON
        k.peaks(
            c, Ridge(k.width, hz - h * 0.01f, h * 0.22f, true, 40), hz + 1f,
            l.distant(l.ground(0xFF9AABC2.toInt()), 0.42f), l.distant(l.ground(0xFF667A96.toInt()), 0.42f),
            Random(90), snowLine = hz - h * 0.12f, snowDepth = 0.14f,
        )
        k.haze(c, h * 0.46f, hz, 0.4f)
        k.peaks(
            c, Ridge(k.width, hz, h * 0.1f, true, 47), hz + 1f,
            l.distant(l.ground(0xFF566B88.toInt()), 0.3f), l.distant(l.ground(0xFF37495F.toInt()), 0.3f),
            Random(93), snowLine = hz - h * 0.085f, snowDepth = 0.07f,
        )
        k.haze(c, h * 0.57f, hz, 0.24f)
        return bmp
    }

    /** Forest down to the water's edge: a dark rolling foot, a hazy back row and bigger trees in front. */
    private fun shore(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[1]) ?: return null
        val l = k.l; val h = k.h; val w = k.w
        val rnd = Random(84)
        val hz = h * HORIZON
        val foot = Ridge(k.width, hz, h * 0.02f, false, 31)
        val body = Path(); foot.addTo(body, hz + 1f)
        k.shader = LinearGradient(
            0f, hz - h * 0.03f, 0f, hz,
            l.distant(l.foliage(0xFF3A6A48.toInt()), 0.3f), l.distant(l.foliage(0xFF1F4030.toInt()), 0.3f),
            Shader.TileMode.CLAMP,
        )
        c.drawPath(body, k.fill)
        k.shader = null

        // Back row: small, pale, nearly lost in the haze.
        val backLit = l.distant(l.foliage(0xFF3F7550.toInt()), 0.45f)
        val backDark = l.distant(l.foliage(0xFF1C3A2A.toInt()), 0.45f)
        var x = -k.margin
        while (x < k.w + k.margin) {
            val th = h * (0.014f + 0.016f * rnd.nextFloat())
            k.simplePine(c, x, foot.yAt(x) - h * 0.004f, th, k.jitter(backLit, rnd, 0.2f), backDark, rnd)
            x += (6f + rnd.nextFloat() * 6f) * k.unit
        }
        k.haze(c, hz - h * 0.04f, hz, 0.22f)

        // Front row, in groves: taller where a grove is thick, with the odd leafy tree among the firs.
        val lit = l.distant(l.foliage(0xFF3F7550.toInt()), 0.22f); val dark = l.distant(l.foliage(0xFF1C3A2A.toInt()), 0.22f)
        val trunk = l.shade(0xFF33261C.toInt())
        x = -k.margin
        while (x < k.w + k.margin) {
            val grove = 0.5f + 0.5f * sin(x / w * 7f + 0.5f) * sin(x / w * 19f + 1.3f)
            val th = h * (0.018f + 0.03f * rnd.nextFloat()) * (0.75f + 0.55f * grove)
            val y = foot.yAt(x) + 1f
            if (rnd.nextFloat() < 0.1f) {
                k.broadleaf(
                    c, x, y, th * 1.1f, k.jitter(l.distant(l.foliage(0xFF74A03A.toInt()), 0.22f), rnd, 0.2f),
                    l.distant(l.foliage(0xFF2F5A2A.toInt()), 0.22f), trunk, rnd, 0.3f,
                )
            } else {
                k.conifer(c, x, y, th, k.jitter(lit, rnd, 0.2f), dark, trunk, rnd, 0.3f)
            }
            x += (9f + rnd.nextFloat() * 8f) * k.unit
        }
        // Shade pooled at the foot of the trees, a crisp waterline, and mist among the trunks.
        k.shader = LinearGradient(0f, hz - h * 0.008f, 0f, hz, k.alpha(Color.BLACK, 0), k.alpha(l.shade(0xFF0C2018.toInt()), 90), Shader.TileMode.CLAMP)
        c.drawRect(-k.margin, hz - h * 0.008f, w + k.margin, hz + 1f, k.fill)
        k.shader = null
        k.haze(c, hz - h * 0.05f, hz, 0.3f)
        return bmp
    }

    /** A small forested islet; it is drawn, and mirrored, by [drawWater]. */
    private fun islet(k: PaintKit): Bitmap? {
        val sw = (k.w * 0.26f).toInt(); val sh = (k.h * 0.12f).toInt()
        val (c, bmp) = k.newSprite(sw, sh) ?: return null
        val l = k.l; val h = k.h
        val rnd = Random(85)
        val base = sh - 2f
        val cx = sw / 2f
        val mound = Path()
        mound.moveTo(cx - sw * 0.46f, base)
        mound.cubicTo(cx - sw * 0.38f, base - h * 0.012f, cx - sw * 0.24f, base - h * 0.03f, cx - sw * 0.05f, base - h * 0.032f)
        mound.cubicTo(cx + sw * 0.14f, base - h * 0.034f, cx + sw * 0.36f, base - h * 0.018f, cx + sw * 0.46f, base)
        mound.close()
        k.shader = LinearGradient(
            0f, base - h * 0.034f, 0f, base,
            l.distant(l.foliage(0xFF4C7A3C.toInt()), 0.1f), l.distant(l.ground(0xFF4A4A44.toInt()), 0.1f), Shader.TileMode.CLAMP,
        )
        c.drawPath(mound, k.fill)
        k.shader = null
        k.rock(c, cx - sw * 0.3f, base, sw * 0.05f, l.distant(l.ground(0xFF6E7076.toInt()), 0.1f), rnd)
        k.rock(c, cx + sw * 0.34f, base, sw * 0.04f, l.distant(l.ground(0xFF6E7076.toInt()), 0.1f), rnd)
        val lit = l.distant(l.foliage(0xFF336A44.toInt()), 0.1f); val dark = l.distant(l.foliage(0xFF15301F.toInt()), 0.1f)
        val trunk = l.shade(0xFF33261C.toInt())
        val ground = base - h * 0.022f
        k.conifer(c, cx - sw * 0.12f, ground, h * 0.058f, k.jitter(lit, rnd, 0.2f), dark, trunk, rnd, 0.35f)
        k.conifer(c, cx + sw * 0.03f, ground - h * 0.004f, h * 0.082f, k.jitter(lit, rnd, 0.2f), dark, trunk, rnd, 0.4f)
        k.conifer(c, cx + sw * 0.17f, ground, h * 0.05f, k.jitter(lit, rnd, 0.2f), dark, trunk, rnd, 0.35f)
        k.broadleaf(c, cx - sw * 0.24f, ground + h * 0.006f, h * 0.034f, l.foliage(0xFF6E9C3A.toInt()), l.foliage(0xFF2F5A2A.toInt()), trunk, rnd, 0.3f)
        k.fill.color = k.alpha(Color.BLACK, 80)
        c.drawOval(cx - sw * 0.46f, base - 2f * k.unit, cx + sw * 0.46f, base + 2f * k.unit, k.fill)
        return bmp
    }

    private fun nearBank(k: PaintKit): Bitmap? {
        val (c, bmp) = k.newLayer(layers[2]) ?: return null
        val l = k.l; val h = k.h; val w = k.w; val u = k.unit
        val rnd = Random(83)
        val ridge = Ridge(k.width, h * 0.89f, h * 0.02f, false, 33)
        k.hill(c, ridge, h, l.ground(0xFF4E7A3A.toInt()), l.ground(0xFF21401C.toInt()), rnd, 300)

        // Wet, dark earth where the water laps the bank, and a scatter of pebbles along it.
        val step = 6f * u
        val edge = Path()
        var x = -k.margin
        edge.moveTo(x, ridge.yAt(x))
        while (x < w + k.margin) { x += step; edge.lineTo(x, ridge.yAt(x)) }
        while (x > -k.margin) { edge.lineTo(x, ridge.yAt(x) + h * 0.009f); x -= step }
        edge.close()
        k.fill.color = k.alpha(l.ground(0xFF2B261E.toInt()), 200)
        c.drawPath(edge, k.fill)
        x = -k.margin
        while (x < w + k.margin) {
            k.fill.color = k.jitter(l.ground(0xFF8E9094.toInt()), rnd, 0.35f)
            c.drawCircle(x, ridge.yAt(x) + h * 0.009f * rnd.nextFloat(), (1.1f + 2.2f * rnd.nextFloat()) * u, k.fill)
            x += (3f + 5f * rnd.nextFloat()) * u
        }

        for (i in 0 until 9) {
            val rx = w * (i / 8f) + (rnd.nextFloat() - 0.5f) * w * 0.08f
            k.rock(c, rx, ridge.yAt(rx) + h * 0.006f, w * (0.012f + 0.02f * rnd.nextFloat()), l.ground(0xFF7A7F86.toInt()), rnd)
        }
        k.grass(c, ridge, h, 1600, l.ground(0xFF7FA85A.toInt()), l.ground(0xFF1E3518.toInt()), rnd)
        k.flowers(c, ridge, h, 40, rnd)
        return bmp
    }
}
