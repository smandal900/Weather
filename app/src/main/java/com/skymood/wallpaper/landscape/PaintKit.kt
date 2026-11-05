package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.skymood.domain.TimeOfDay
import com.skymood.wallpaper.scenes.SkyGradient
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Fades a far-away colour into the sky near the horizon. */
internal fun Lighting.distant(color: Int, amount: Float) =
    SkyGradient.blend(color, skyBottom, (amount * (1f + (4 - clarity) * 0.3f)).coerceAtMost(0.92f))

/** A horizontal slab of the screen painted once into its own bitmap. [depth] drives parallax. */
class PaintLayer(val top: Float, val bottom: Float, val depth: Float)

class PaintResult(val bitmaps: Array<Bitmap?>, val sprites: Map<String, Bitmap> = emptyMap())

/**
 * Drawing helpers for the painted landscapes. A kit belongs to one background painting job and
 * carries a private [Lighting] snapshot, so it never touches state used by the main thread.
 * Everything here may allocate: it only runs when the size, time of day or weather changes.
 */
class PaintKit(val width: Int, val height: Int, val l: Lighting) {

    val w = width.toFloat()
    val h = height.toFloat()
    val margin = w * 0.08f
    val unit = w / 1080f
    val sunOnLeft = l.reflectionX < 0.5f
    /** -1 when the sun is on the left, +1 when on the right. */
    val sun = if (sunOnLeft) -1f else 1f
    val night = l.timeOfDay == TimeOfDay.NIGHT

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val scratch = Path()

    /** Sets the fill shader and resets the paint's alpha, which would otherwise dim any shader. */
    var shader: Shader?
        get() = fill.shader
        set(v) { fill.alpha = 255; fill.shader = v }

    fun newLayer(layer: PaintLayer): Pair<Canvas, Bitmap>? {
        val bw = (w + margin * 2).toInt()
        val bh = (h * (layer.bottom - layer.top)).toInt().coerceAtLeast(1)
        val bmp = runCatching { Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        val c = Canvas(bmp)
        // Work in screen coordinates: x from -margin, y from 0 at the top of the screen.
        c.translate(margin, -h * layer.top)
        return c to bmp
    }

    fun newSprite(wPx: Int, hPx: Int): Pair<Canvas, Bitmap>? {
        val bmp = runCatching { Bitmap.createBitmap(wPx.coerceAtLeast(2), hPx.coerceAtLeast(2), Bitmap.Config.ARGB_8888) }
            .getOrNull() ?: return null
        return Canvas(bmp) to bmp
    }

    fun mix(a: Int, b: Int, t: Float) = SkyGradient.blend(a, b, t)
    fun lighten(c: Int, t: Float) = SkyGradient.blend(c, Color.WHITE, t)
    fun darken(c: Int, t: Float) = SkyGradient.blend(c, Color.BLACK, t)
    fun alpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    fun jitter(base: Int, rnd: Random, amount: Float): Int {
        val k = 1f + (rnd.nextFloat() - 0.5f) * amount
        return Color.rgb(
            (Color.red(base) * k).toInt().coerceIn(0, 255),
            (Color.green(base) * k).toInt().coerceIn(0, 255),
            (Color.blue(base) * k).toInt().coerceIn(0, 255),
        )
    }

    /** A band of mist that fades in and out vertically, so layers melt into the distance. */
    fun haze(c: Canvas, top: Float, bottom: Float, strength: Float) {
        val clear = l.skyBottom and 0x00FFFFFF
        // Reported visibility and humidity: the less clear the air, the thicker the mist.
        val thick = (strength * (1f + (4 - l.clarity) * 0.35f)).coerceAtMost(0.95f)
        val misty = clear or ((255 * thick).toInt() shl 24)
        val p = Paint()
        p.shader = LinearGradient(
            0f, top, 0f, bottom, intArrayOf(clear, misty, misty, clear), floatArrayOf(0f, 0.45f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(-margin, top, w + margin, bottom, p)
    }

    // ---------------------------------------------------------------- trees

    /** Tiny distant pine: a few ragged tiers, enough at this size. */
    fun simplePine(c: Canvas, x: Float, baseY: Float, th: Float, lit: Int, dark: Int, rnd: Random) {
        val path = Path()
        val tiers = 5
        val maxHalf = th * 0.22f
        for (i in 0 until tiers) {
            val f = (i + 1f) / tiers
            val top = baseY - th + th * 0.9f * (i / tiers.toFloat())
            val bottom = top + th * (0.18f + 0.06f * f)
            val half = maxHalf * (0.15f + 0.85f * f) * (0.85f + rnd.nextFloat() * 0.3f)
            path.rewind()
            path.moveTo(x, top)
            path.lineTo(x + half, bottom)
            path.lineTo(x, bottom - th * 0.03f)
            path.lineTo(x - half, bottom)
            path.close()
            shader = LinearGradient(
                if (sunOnLeft) x - half else x + half, 0f, if (sunOnLeft) x + half else x - half, 0f,
                lit, dark, Shader.TileMode.CLAMP,
            )
            c.drawPath(path, fill)
        }
        shader = null
    }

    /**
     * A conifer grown the way real ones are: a trunk with whorls of drooping branches, each
     * covered in needle clusters. Branches behind the trunk are painted first and darker;
     * clusters get lighter towards the tips and on the sun's side, darker deep inside the crown.
     */
    fun conifer(
        c: Canvas, x: Float, baseY: Float, th: Float, lit: Int, dark: Int, trunk: Int,
        rnd: Random, detail: Float,
    ) {
        val lean = (rnd.nextFloat() - 0.5f) * th * 0.03f
        val trunkW = th * 0.014f
        val tp = Path()
        tp.moveTo(x - trunkW, baseY)
        tp.lineTo(x - trunkW * 0.15f + lean, baseY - th * 0.3f)
        tp.lineTo(x + trunkW * 0.15f + lean, baseY - th * 0.3f)
        tp.lineTo(x + trunkW, baseY)
        tp.close()
        shader = LinearGradient(
            x - trunkW, 0f, x + trunkW, 0f,
            if (sunOnLeft) lighten(trunk, 0.12f) else darken(trunk, 0.4f),
            if (sunOnLeft) darken(trunk, 0.4f) else lighten(trunk, 0.12f),
            Shader.TileMode.CLAMP,
        )
        c.drawPath(tp, fill)
        shader = null

        val crownBottom = baseY - th * 0.05f
        val crownTop = baseY - th
        val maxLen = th * (0.3f + rnd.nextFloat() * 0.06f)
        val whorls = (44 * detail).toInt().coerceAtLeast(12)
        val clusterSize = th * 0.03f
        line.strokeWidth = max(1f, th * 0.0032f)
        val deep = darken(dark, 0.35f)

        // A dense core down the middle of the crown, so no thin see-through line shows where the
        // branches start (it read as a pale vertical scratch through every tree).
        val core = Path()
        val coreSteps = 14
        core.moveTo(x - maxLen * 0.1f, crownBottom)
        for (s in 1..coreSteps) {
            val t = s / coreSteps.toFloat()
            val hw = maxLen * 0.12f * (1f - t).pow(0.7f) + th * 0.004f
            core.lineTo(x + lean * t - hw, crownBottom - (crownBottom - crownTop) * t)
        }
        for (s in coreSteps downTo 1) {
            val t = s / coreSteps.toFloat()
            val hw = maxLen * 0.12f * (1f - t).pow(0.7f) + th * 0.004f
            core.lineTo(x + lean * t + hw, crownBottom - (crownBottom - crownTop) * t)
        }
        core.lineTo(x + maxLen * 0.1f, crownBottom)
        core.close()
        fill.color = mix(deep, dark, 0.6f)
        c.drawPath(core, fill)

        for (pass in 0..1) { // 0 = branches behind the trunk, 1 = in front
            for (i in 0 until whorls) {
                val t = i / (whorls - 1f) // 0 bottom .. 1 top
                val y = crownBottom - (crownBottom - crownTop) * t.pow(0.92f) + (rnd.nextFloat() - 0.5f) * th * 0.01f
                val cx = x + lean * t
                val len = maxLen * (1f - t).pow(0.65f) * (0.6f + rnd.nextFloat() * 0.7f) + th * 0.02f
                for (side in intArrayOf(-1, 1)) {
                    if (pass == 0 && rnd.nextFloat() < 0.2f) continue
                    // Real firs have gaps between their drooping branches, most of all low down.
                    if (pass == 1 && t < 0.85f && rnd.nextFloat() < 0.24f * (1.1f - t)) continue
                    val reach = if (pass == 0) len * 0.8f else len
                    val ex = cx + side * reach
                    // Lower branches droop more; the tips curl back up a little.
                    val ey = y + reach * (0.3f + rnd.nextFloat() * 0.26f) * (0.95f - 0.45f * t)
                    val qx = cx + side * reach * 0.5f
                    val qy = y - reach * 0.1f
                    if (pass == 1) {
                        // The bare branch itself, so each tier reads as a branch with needles hanging off it.
                        line.strokeWidth = max(1.4f, th * 0.006f * (1f - t * 0.6f))
                        line.color = alpha(deep, 235)
                        // Start a little out from the trunk so the branches do not pile into a dark pole.
                        var bx = cx + side * reach * 0.14f; var by = y
                        for (s in 1..4) {
                            val u = 0.14f + 0.86f * s / 4f; val iu = 1f - u
                            val nx = iu * iu * cx + 2 * iu * u * qx + u * u * ex
                            val ny = iu * iu * y + 2 * iu * u * qy + u * u * ey
                            c.drawLine(bx, by, nx, ny, line); bx = nx; by = ny
                        }
                        line.strokeWidth = max(1f, th * 0.0032f)
                    }
                    val clusters = (reach / (clusterSize * 0.75f)).toInt().coerceIn(2, 16)
                    for (k in 0..clusters) {
                        val u = k / clusters.toFloat()
                        val iu = 1f - u
                        val px = iu * iu * cx + 2 * iu * u * qx + u * u * ex
                        val py = iu * iu * y + 2 * iu * u * qy + u * u * ey
                        var light = 0.25f + 0.55f * u + (if (side.toFloat() == sun) 0.18f else -0.12f)
                        if (pass == 0) light -= 0.3f
                        light = (light + (rnd.nextFloat() - 0.5f) * 0.25f).coerceIn(0f, 1f)
                        line.color = jitter(
                            if (light < 0.3f) mix(deep, dark, light / 0.3f) else mix(dark, lit, (light - 0.3f) / 0.7f),
                            rnd, 0.12f,
                        )
                        val size = clusterSize * (0.6f + 0.6f * u) * (0.8f + rnd.nextFloat() * 0.4f)
                        val needles = (8 * detail).toInt().coerceIn(4, 9)
                        for (n in 0 until needles) {
                            val a = side * 0.35f + (n - needles / 2f) * 0.32f + (rnd.nextFloat() - 0.5f) * 0.3f + 1.2f
                            val nl = size * (0.6f + rnd.nextFloat() * 0.6f)
                            c.drawLine(px, py, px + cos(a) * nl * side * 0.9f, py + sin(a) * nl, line)
                        }
                        // Fine twigs hanging from the branch: the droopy curtain real firs have.
                        if (pass == 1 && k > 0) {
                            repeat(2) {
                                val tl = size * (1.1f + rnd.nextFloat() * 1.2f)
                                val tx = px + (rnd.nextFloat() - 0.5f) * size * 0.5f
                                c.drawLine(tx, py, tx + side * size * 0.12f, py + tl, line)
                            }
                        }
                    }
                }
            }
        }
        line.color = lit
        c.drawLine(x + lean, crownTop + th * 0.02f, x + lean, crownTop - th * 0.02f, line)
    }

    /**
     * A leafy tree: a trunk that forks into limbs, under a lumpy dome made of many overlapping
     * clumps of leaves. Every clump is dark underneath and lit on top and on the sun's side, built
     * from hundreds of small leaf dabs with a ragged edge, so the crown has the broken, billowy
     * outline of a real tree instead of a flat disc.
     */
    fun broadleaf(
        c: Canvas, x: Float, baseY: Float, th: Float, lit: Int, dark: Int, trunk: Int,
        rnd: Random, detail: Float,
    ) {
        val deep = darken(dark, 0.42f)
        val small = th < 140f * unit
        val r = th * 0.4f                      // half the crown width
        val cy = baseY - th * 0.58f            // centre of the crown
        val forkY = baseY - th * 0.26f

        // Trunk, with bark lines, flaring a little at the base.
        val trunkW = th * 0.032f
        val tp = Path()
        tp.moveTo(x - trunkW * 1.5f, baseY)
        tp.quadTo(x - trunkW * 0.6f, baseY - th * 0.1f, x - trunkW * 0.75f, forkY)
        tp.lineTo(x + trunkW * 0.75f, forkY)
        tp.quadTo(x + trunkW * 0.6f, baseY - th * 0.1f, x + trunkW * 1.5f, baseY)
        tp.close()
        shader = LinearGradient(
            x - trunkW, 0f, x + trunkW, 0f,
            if (sunOnLeft) lighten(trunk, 0.14f) else darken(trunk, 0.4f),
            if (sunOnLeft) darken(trunk, 0.4f) else lighten(trunk, 0.14f), Shader.TileMode.CLAMP,
        )
        c.drawPath(tp, fill)
        shader = null
        if (!small) {
            line.strokeWidth = max(1f, th * 0.004f)
            line.color = alpha(darken(trunk, 0.5f), 150)
            for (i in 0 until 7) {
                val bx = x + (rnd.nextFloat() - 0.5f) * trunkW * 1.4f
                c.drawLine(bx, baseY - th * 0.02f, bx + (rnd.nextFloat() - 0.5f) * trunkW * 0.3f, forkY + th * 0.02f, line)
            }
        }
        // Main limbs into the crown, thinning as they go.
        val limbs = if (small) 2 else 4
        for (i in 0 until limbs) {
            val spread = if (limbs == 1) 0f else (i / (limbs - 1f) - 0.5f) * 2f
            val tx = x + spread * r * 0.7f
            val ty = cy + r * (0.15f - 0.1f * (1f - abs(spread)))
            var ax = x; var ay = forkY
            for (s in 1..6) {
                val t = s / 6f
                val nx = x + (tx - x) * t + sin(t * 3.14f) * spread * r * 0.1f
                val ny = forkY + (ty - forkY) * t
                line.strokeWidth = max(1f, trunkW * 1.1f * (1f - 0.75f * t))
                line.color = darken(trunk, 0.1f + 0.12f * (i % 2))
                c.drawLine(ax, ay, nx, ny, line)
                ax = nx; ay = ny
            }
        }

        // The crown: clumps scattered over a dome, drawn lowest first so higher ones overlap.
        val clumps = if (small) 7 else (12 + (detail * 5f).toInt()).coerceAtMost(17)
        val cxs = FloatArray(clumps); val cys = FloatArray(clumps); val crs = FloatArray(clumps)
        for (i in 0 until clumps) {
            var dx: Float; var dy: Float
            do {
                dx = (rnd.nextFloat() * 2f - 1f)
                dy = (rnd.nextFloat() * 1.15f - 0.8f)
            } while (dx * dx + (dy / 0.8f) * (dy / 0.8f) > 1f)
            cxs[i] = x + dx * r * 0.78f
            cys[i] = cy + dy * r * 0.8f
            crs[i] = r * (0.3f + 0.16f * rnd.nextFloat()) * (1f - 0.18f * abs(dx))
        }
        val order = (0 until clumps).sortedByDescending { cys[it] }
        val dabsPer = (if (small) 18 else (46 * detail).toInt().coerceAtLeast(26))
        for (i in order) {
            val cr = crs[i]; val ccx = cxs[i]; val ccy = cys[i]
            // Dark body of the clump.
            fill.color = deep
            c.drawCircle(ccx, ccy + cr * 0.1f, cr * 0.96f, fill)
            val dabR = cr * (if (small) 0.34f else 0.2f)
            for (k in 0 until dabsPer) {
                val a = rnd.nextFloat() * 6.2832f
                val d = sqrt(rnd.nextFloat()) * 0.94f
                val dx = cos(a) * d; val dy = sin(a) * d
                // Light from above and from the sun's side; the lower rim stays in shadow.
                val lightness = (0.58f - dy * 0.75f - sun * dx * 0.5f + (rnd.nextFloat() - 0.5f) * 0.28f).coerceIn(0f, 1f)
                val base = mix(deep, mix(dark, lit, 0.8f), lightness)
                fill.color = jitter(base, rnd, 0.16f)
                c.drawCircle(ccx + dx * cr, ccy + dy * cr, dabR * (0.55f + rnd.nextFloat() * 0.7f), fill)
            }
            // Ragged rim of single leaves on the upper half.
            if (!small) {
                val rim = mix(lit, Color.WHITE, 0.12f)
                repeat(14) {
                    val a = -3.14f + rnd.nextFloat() * 3.14f
                    val px = ccx + cos(a) * cr * (0.92f + rnd.nextFloat() * 0.16f)
                    val py = ccy + sin(a) * cr * (0.92f + rnd.nextFloat() * 0.16f)
                    fill.color = jitter(rim, rnd, 0.14f)
                    c.drawCircle(px, py, dabR * (0.3f + rnd.nextFloat() * 0.3f), fill)
                }
            }
        }
    }

    private fun sqrt(v: Float) = kotlin.math.sqrt(v)

    // ----------------------------------------------------------- landforms

    /**
     * A mountain range: base gradient, faceted light and shadow on every slope (the side facing
     * away from the sun is darker), vertical gullies, and snow on the high parts when [snowLine]
     * is given (the screen y above which snow lies).
     */
    fun mountain(
        c: Canvas, ridge: Ridge, bottom: Float, lit: Int, dark: Int, rnd: Random, snowLine: Float? = null,
    ) {
        val body = Path(); ridge.addTo(body, bottom)
        var minY = bottom
        var x = ridge.left
        while (x < w * 1.1f) { minY = min(minY, ridge.yAt(x)); x += 12f * unit }
        shader = LinearGradient(0f, minY, 0f, bottom, lit, dark, Shader.TileMode.CLAMP)
        c.drawPath(body, fill)
        shader = null
        // Faces are shaded with one smooth path per layer (a path per slice leaves hairline seams
        // that look like scratches), with soft, fading depth rather than hard grooves.
        val step = 6f * unit
        val depth = h * 0.065f
        val shadowPath = Path(); val lightPath = Path()
        // Soften the edges between lit and shaded faces so no hard vertical line shows; clipped to
        // the mountain so the blur never leaks into the sky.
        c.save()
        c.clipPath(body)
        for (k in 0 until 12) {
            shadowPath.rewind(); lightPath.rewind()
            val f = 1f - k * 0.075f
            x = ridge.left
            // Smooth the steepness along the ridge so neighbouring slices fade into each other.
            var prevD = 0f
            while (x < w * 1.1f) {
                val x1 = x + step
                val y0 = ridge.yAt(x); val y1 = ridge.yAt(x1)
                val dy = y1 - y0
                val steep = min(1f, abs(dy) / (step * 1.1f))
                val d = prevD * 0.35f + depth * f * 0.42f * (if (steep > 0.1f) 1f else 0f)
                prevD = d
                if (d > 0.5f) {
                    val wide = ridge.yAt(x + step * 5f) - ridge.yAt(x - step * 5f)
                    val shadow = if (sunOnLeft) wide > 0f else wide < 0f
                    val p = if (shadow) shadowPath else lightPath
                    p.moveTo(x, y0); p.lineTo(x1 + 0.6f, y1); p.lineTo(x1 + 0.6f, y1 + d); p.lineTo(x, y0 + d); p.close()
                }
                x = x1
            }
            fill.color = alpha(Color.BLACK, 11); c.drawPath(shadowPath, fill)
            fill.color = alpha(Color.WHITE, 4); c.drawPath(lightPath, fill)
        }
        if (snowLine != null) {
            val snowLit = l.shade(Lighting.SNOW)
            val snowShade = mix(snowLit, 0xFF7A90B8.toInt(), 0.5f)
            val litP = Path(); val shadeP = Path()
            x = ridge.left
            val sstep = step * 0.8f
            var prevD = 0f
            while (x < w * 1.1f) {
                val x1 = x + sstep
                val y0 = ridge.yAt(x); val y1 = ridge.yAt(x1)
                val top = min(y0, y1)
                if (top < snowLine) {
                    val wide = ridge.yAt(x + step * 5f) - ridge.yAt(x - step * 5f)
                    val shadow = if (sunOnLeft) wide > 0f else wide < 0f
                    // Depth follows the peak height and is smoothed, so the snow edge flows instead of striping.
                    val d = minOf(h * 0.09f, (((snowLine - top) * 0.75f + 3f) * 0.8f) * 0.3f + prevD * 0.7f)
                    prevD = d
                    val p = if (shadow) shadeP else litP
                    p.moveTo(x, y0); p.lineTo(x1 + 0.6f, y1); p.lineTo(x1 + 0.6f, y1 + d); p.lineTo(x, y0 + d); p.close()
                }
                x = x1
            }
            fill.color = snowShade; c.drawPath(shadeP, fill)
            fill.color = snowLit; c.drawPath(litP, fill)
        }
        fill.maskFilter = null
        c.restore()
    }

    /** Smooth hill: gradient body, a lit crest on the sun side and a scatter of grass tufts. */
    fun hill(c: Canvas, ridge: Ridge, bottom: Float, top: Int, base: Int, rnd: Random, tufts: Int = 600) {
        val body = Path(); ridge.addTo(body, bottom)
        var minY = bottom
        var x = ridge.left
        while (x < w * 1.1f) { minY = min(minY, ridge.yAt(x)); x += 12f * unit }
        shader = LinearGradient(0f, minY, 0f, bottom, top, base, Shader.TileMode.CLAMP)
        c.drawPath(body, fill)
        shader = null
        line.strokeWidth = 2f * unit
        line.color = alpha(lighten(top, 0.35f), 110)
        x = ridge.left
        while (x < w * 1.1f) {
            c.drawLine(x, ridge.yAt(x) + 1f, x + 8f * unit, ridge.yAt(x + 8f * unit) + 1f, line)
            x += 8f * unit
        }
        val lo = darken(base, 0.25f)
        val hi = lighten(top, 0.12f)
        repeat((tufts * unit).toInt()) {
            val tx = ridge.left + rnd.nextFloat() * w * 1.2f
            val ty0 = ridge.yAt(tx)
            val ty = ty0 + (bottom - ty0) * rnd.nextFloat() * rnd.nextFloat()
            val bh = h * (0.003f + 0.008f * rnd.nextFloat())
            line.strokeWidth = (1f + rnd.nextFloat() * 1.2f) * unit
            line.color = mix(lo, hi, rnd.nextFloat())
            c.drawLine(tx, ty, tx + (rnd.nextFloat() - 0.5f) * bh, ty - bh, line)
        }
    }

    /** Patchwork of fields over a hill: short strips that widen towards the viewer, with furrows. */
    fun fields(c: Canvas, ridge: Ridge, bottom: Float, rnd: Random, count: Int = 9) {
        val body = Path(); ridge.addTo(body, bottom)
        c.save()
        c.clipPath(body)
        val colors = intArrayOf(0xFFB7C45A.toInt(), 0xFF6F9A3E.toInt(), 0xFFD6BE5E.toInt(), 0xFF4F8238.toInt(), 0xFF9DB24A.toInt())
        val q = scratch
        for (i in 0 until count * 2) {
            val x0 = ridge.left + rnd.nextFloat() * w * 1.1f
            val topY = ridge.yAt(x0) + (bottom - ridge.yAt(x0)) * (0.05f + 0.55f * rnd.nextFloat())
            val ht = (bottom - topY) * (0.12f + 0.3f * rnd.nextFloat())
            val bw = w * (0.05f + 0.1f * rnd.nextFloat()) * (0.6f + (topY - ridge.yAt(x0)) / max(1f, bottom - ridge.yAt(x0)))
            val skew = (rnd.nextFloat() - 0.5f) * bw * 0.8f
            val grow = 1.35f
            fill.color = alpha(l.ground(colors[rnd.nextInt(colors.size)]), 55)
            q.rewind()
            q.moveTo(x0, topY); q.lineTo(x0 + bw, topY)
            q.lineTo(x0 + bw * grow + skew, topY + ht); q.lineTo(x0 - bw * (grow - 1f) + skew, topY + ht); q.close()
            c.drawPath(q, fill)
            line.color = alpha(Color.BLACK, 26)
            line.strokeWidth = 1f * unit
            for (f in 1 until 6) {
                val u = f / 6f
                c.drawLine(x0 + bw * u, topY, x0 + (bw * grow) * u - bw * (grow - 1f) * (1f - u) + skew, topY + ht, line)
            }
        }
        c.restore()
    }
    /** Thousands of short blades with varied colour and lean, denser and larger towards the viewer. */
    fun grass(c: Canvas, ridge: Ridge, bottom: Float, count: Int, light: Int, dark: Int, rnd: Random) {
        repeat((count * unit).toInt()) {
            val x = -margin + rnd.nextFloat() * (w + margin * 2)
            val top = ridge.yAt(x)
            val d = rnd.nextFloat()
            val y = top + (bottom - top) * d
            val bh = h * (0.006f + 0.02f * d) * (0.6f + rnd.nextFloat())
            val lean = (rnd.nextFloat() - 0.5f) * bh * 0.6f
            line.strokeWidth = max(1f, 1.2f + d * 2.2f) * unit
            line.color = mix(dark, light, (0.25f + rnd.nextFloat() * 0.75f) * (1f - d * 0.5f))
            c.drawLine(x, y, x + lean, y - bh, line)
        }
    }

    fun flowers(c: Canvas, ridge: Ridge, bottom: Float, count: Int, rnd: Random) {
        if (l.snow || night) return
        val colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFD34A.toInt(), 0xFFE87AA8.toInt(), 0xFFB9A4FF.toInt())
        repeat((count * unit).toInt()) {
            val x = rnd.nextFloat() * w
            val top = ridge.yAt(x)
            val y = top + (bottom - top) * (0.1f + rnd.nextFloat() * 0.8f)
            fill.color = l.shade(colors[rnd.nextInt(colors.size)])
            c.drawCircle(x, y, (1.5f + rnd.nextFloat() * 2.5f) * unit, fill)
        }
    }

    /** A boulder: flat base, lumpy top, a lit facet towards the sun and a shadow facet away. */
    fun rock(c: Canvas, cx: Float, baseY: Float, size: Float, base: Int, rnd: Random) {
        val n = 8
        val xs = FloatArray(n); val ys = FloatArray(n)
        for (i in 0 until n) {
            val t = i / (n - 1f)
            xs[i] = cx + size * (2f * t - 1f)
            ys[i] = baseY - size * 0.85f * sin(3.1416f * t).pow(0.7f) * (0.7f + rnd.nextFloat() * 0.5f)
        }
        val body = Path()
        body.moveTo(cx - size, baseY)
        for (i in 0 until n) body.lineTo(xs[i], ys[i])
        body.lineTo(cx + size, baseY)
        body.close()
        fill.color = base
        c.drawPath(body, fill)
        // Facet towards the sun, with the ridge line as its boundary.
        var peak = 0
        for (i in 0 until n) if (ys[i] < ys[peak]) peak = i
        val facet = Path()
        if (sunOnLeft) {
            facet.moveTo(cx - size, baseY)
            for (i in 0..peak) facet.lineTo(xs[i], ys[i])
            facet.lineTo(xs[peak] - size * 0.1f, baseY - size * 0.15f)
        } else {
            facet.moveTo(cx + size, baseY)
            for (i in n - 1 downTo peak) facet.lineTo(xs[i], ys[i])
            facet.lineTo(xs[peak] + size * 0.1f, baseY - size * 0.15f)
        }
        facet.close()
        fill.color = lighten(base, 0.18f)
        c.drawPath(facet, fill)
        val shade = Path()
        if (sunOnLeft) {
            shade.moveTo(cx + size, baseY)
            for (i in n - 1 downTo peak) shade.lineTo(xs[i], ys[i])
            shade.lineTo(xs[peak] + size * 0.1f, baseY - size * 0.2f)
        } else {
            shade.moveTo(cx - size, baseY)
            for (i in 0..peak) shade.lineTo(xs[i], ys[i])
            shade.lineTo(xs[peak] - size * 0.1f, baseY - size * 0.2f)
        }
        shade.close()
        fill.color = darken(base, 0.3f)
        c.drawPath(shade, fill)
        line.strokeWidth = max(1f, size * 0.03f)
        line.color = alpha(Color.BLACK, 70)
        repeat(2) {
            val sx = cx + (rnd.nextFloat() - 0.5f) * size
            c.drawLine(sx, baseY - size * 0.6f, sx + size * 0.2f, baseY - size * 0.2f, line)
        }
        // Soft contact shadow.
        fill.color = alpha(Color.BLACK, 50)
        c.drawOval(cx - size * 1.05f, baseY - size * 0.07f, cx + size * 1.15f, baseY + size * 0.1f, fill)
    }
}
