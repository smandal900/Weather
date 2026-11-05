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
        tp.lineTo(x - trunkW * 0.15f + lean, baseY - th * 0.7f)
        tp.lineTo(x + trunkW * 0.15f + lean, baseY - th * 0.7f)
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
        val whorls = (46 * detail).toInt().coerceAtLeast(12)
        val clusterSize = th * 0.03f
        line.strokeWidth = max(1.2f, th * 0.0042f)
        val deep = darken(dark, 0.35f)

        for (pass in 0..1) { // 0 = branches behind the trunk, 1 = in front
            for (i in 0 until whorls) {
                val t = i / (whorls - 1f) // 0 bottom .. 1 top
                val y = crownBottom - (crownBottom - crownTop) * t.pow(0.92f) + (rnd.nextFloat() - 0.5f) * th * 0.01f
                val cx = x + lean * t
                val len = maxLen * (1f - t).pow(0.65f) * (0.75f + rnd.nextFloat() * 0.45f) + th * 0.02f
                for (side in intArrayOf(-1, 1)) {
                    if (pass == 0 && rnd.nextFloat() < 0.2f) continue
                    val reach = if (pass == 0) len * 0.8f else len
                    val ex = cx + side * reach
                    val ey = y + reach * (0.22f + rnd.nextFloat() * 0.2f) * (0.5f + t * 0.3f)
                    val qx = cx + side * reach * 0.5f
                    val qy = y - reach * 0.04f
                    val clusters = (reach / (clusterSize * 0.75f)).toInt().coerceIn(2, 16)
                    for (k in 1..clusters) {
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
                        val needles = (7 * detail).toInt().coerceIn(4, 9)
                        for (n in 0 until needles) {
                            val a = side * 0.35f + (n - needles / 2f) * 0.32f + (rnd.nextFloat() - 0.5f) * 0.3f + 1.2f
                            val nl = size * (0.6f + rnd.nextFloat() * 0.6f)
                            c.drawLine(px, py, px + cos(a) * nl * side * 0.9f, py + sin(a) * nl, line)
                        }
                    }
                }
            }
        }
        line.color = lit
        c.drawLine(x + lean, crownTop + th * 0.02f, x + lean, crownTop - th * 0.02f, line)
    }

    /**
     * A leafy tree: a forked trunk under a crown of overlapping leaf clusters. Each cluster is a
     * dark base with a lighter blob shifted towards the sun, so the crown reads as lit from one
     * side, with sunlit dabs of single leaves along the top edge.
     */
    fun broadleaf(
        c: Canvas, x: Float, baseY: Float, th: Float, lit: Int, dark: Int, trunk: Int,
        rnd: Random, detail: Float,
    ) {
        val r = th * 0.34f
        val cy = baseY - th + r * 1.05f
        val trunkW = th * 0.028f
        val tp = Path()
        tp.moveTo(x - trunkW, baseY)
        tp.lineTo(x - trunkW * 0.55f, cy + r * 0.3f)
        tp.lineTo(x + trunkW * 0.55f, cy + r * 0.3f)
        tp.lineTo(x + trunkW, baseY)
        tp.close()
        shader = LinearGradient(
            x - trunkW, 0f, x + trunkW, 0f,
            if (sunOnLeft) lighten(trunk, 0.1f) else darken(trunk, 0.35f),
            if (sunOnLeft) darken(trunk, 0.35f) else lighten(trunk, 0.1f),
            Shader.TileMode.CLAMP,
        )
        c.drawPath(tp, fill)
        shader = null
        line.color = darken(trunk, 0.2f)
        line.strokeWidth = max(1f, th * 0.012f)
        for (i in 0 until 4) {
            val a = -0.9f + i * 0.6f
            c.drawLine(x, cy + r * 0.45f, x + sin(a) * r * 0.8f, cy - cos(a) * r * 0.45f, line)
        }
        val n = (70 * detail).toInt().coerceAtLeast(16)
        val bx = FloatArray(n); val by = FloatArray(n); val br = FloatArray(n)
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.2832f
            val d = sqrt(rnd.nextFloat())
            bx[i] = x + cos(a) * d * r * 0.85f
            by[i] = cy + sin(a) * d * r * 0.72f
            br[i] = r * (0.2f + rnd.nextFloat() * 0.2f)
        }
        val order = (0 until n).sortedByDescending { by[it] }
        val deep = darken(dark, 0.3f)
        for (i in order) {
            val lightness = (0.5f - (by[i] - cy) / (2f * r) * 0.9f - sun * (bx[i] - x) / (2f * r) * 0.9f +
                (rnd.nextFloat() - 0.5f) * 0.18f).coerceIn(0f, 1f)
            val base = jitter(mix(deep, mix(dark, lit, 0.6f), lightness), rnd, 0.1f)
            fill.color = base
            c.drawCircle(bx[i], by[i], br[i], fill)
            fill.color = lighten(base, 0.12f + 0.14f * lightness)
            c.drawCircle(bx[i] + sun * br[i] * 0.22f * -1f, by[i] - br[i] * 0.2f, br[i] * 0.66f, fill)
        }
        // Single sunlit leaves along the upper rim.
        val leaf = mix(lit, Color.WHITE, 0.12f)
        line.color = leaf
        line.strokeWidth = max(1f, th * 0.006f)
        repeat((60 * detail).toInt()) {
            val a = -3.14f + rnd.nextFloat() * 3.14f
            val px = x + cos(a) * r * (0.78f + rnd.nextFloat() * 0.2f)
            val py = cy + sin(a) * r * 0.7f * (0.8f + rnd.nextFloat() * 0.2f)
            c.drawLine(px, py, px + (rnd.nextFloat() - 0.5f) * th * 0.03f, py - th * 0.012f, line)
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
        val step = 5f * unit
        val depth = h * 0.05f
        val q = scratch
        x = ridge.left
        while (x < w * 1.1f) {
            val x1 = x + step
            val y0 = ridge.yAt(x); val y1 = ridge.yAt(x1)
            val dy = y1 - y0
            val steep = min(1f, abs(dy) / (step * 1.1f))
            if (steep > 0.12f) {
                val shadow = if (sunOnLeft) dy > 0f else dy < 0f
                val base = if (shadow) Color.BLACK else Color.WHITE
                val a = if (shadow) 34 else 18
                for (k in 0 until 6) {
                    val d = depth * (1f - k * 0.15f) * (0.4f + steep)
                    fill.color = alpha(base, (a * steep).toInt())
                    q.rewind(); q.moveTo(x, y0); q.lineTo(x1, y1); q.lineTo(x1, y1 + d); q.lineTo(x, y0 + d); q.close()
                    c.drawPath(q, fill)
                }
            }
            x = x1
        }
        repeat((220 * unit).toInt().coerceAtLeast(60)) {
            val gx = ridge.left + rnd.nextFloat() * w * 1.2f
            val gy = ridge.yAt(gx)
            val len = h * (0.03f + rnd.nextFloat() * 0.09f)
            line.strokeWidth = (1f + rnd.nextFloat() * 1.5f) * unit
            line.color = alpha(Color.BLACK, 22 + rnd.nextInt(28))
            c.drawLine(gx, gy + 2f, gx + (rnd.nextFloat() - 0.5f) * len * 0.5f, gy + len, line)
        }
        if (snowLine != null) {
            val snowLit = l.shade(Lighting.SNOW)
            val snowShade = mix(snowLit, 0xFF7A90B8.toInt(), 0.5f)
            x = ridge.left
            while (x < w * 1.1f) {
                val x1 = x + step * 2f
                val y0 = ridge.yAt(x); val y1 = ridge.yAt(x1)
                val top = min(y0, y1)
                if (top < snowLine) {
                    val shadow = if (sunOnLeft) y1 > y0 else y1 < y0
                    val d = ((snowLine - top) * 0.75f + 3f) * (0.65f + rnd.nextFloat() * 0.7f)
                    fill.color = if (shadow) snowShade else snowLit
                    q.rewind(); q.moveTo(x, y0); q.lineTo(x1, y1); q.lineTo(x1, y1 + d); q.lineTo(x, y0 + d); q.close()
                    c.drawPath(q, fill)
                }
                x = x1
            }
        }
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
