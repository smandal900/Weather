package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import com.skymood.domain.TimeOfDay
import com.skymood.wallpaper.scenes.SkyGradient
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * A detailed, painterly forest. Each depth layer is painted into its own bitmap when the size,
 * time of day or weather changes, on a background thread, with tens of thousands of shaded
 * details: conifers built branch by branch from needle clusters, grass texture, haze between
 * layers and light from the sun's side. Every frame only copies three bitmaps, so it costs about
 * the same as simple shapes.
 */
class PaintedForestLandscape : Landscape() {

    private class Layer(val top: Float, val bottom: Float, val depth: Float)

    // Far: misty ridge and distant forest. Mid: forested hill. Near: big pines and meadow.
    private val far = Layer(0.5f, 0.82f, 0.1f)
    private val mid = Layer(0.6f, 1f, 0.5f)
    private val near = Layer(0.5f, 1f, 1f)
    private val layers = arrayOf(far, mid, near)
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG)

    private var bitmaps = arrayOfNulls<Bitmap>(3)
    private var left = 0f
    private var generation = 0
    private val main = Handler(Looper.getMainLooper())

    override fun build() {
        // Painting happens in applyLighting, which always follows build.
    }

    override fun applyLighting() {
        val gen = ++generation
        val w = width
        val h = height
        val tod = lighting.timeOfDay
        val weather = lighting.weather
        painter.execute {
            val l = Lighting().apply { set(tod, weather) }
            val p = Painter(w, h, l)
            val result = arrayOf(p.paintFar(far), p.paintMid(mid), p.paintNear(near))
            main.post {
                if (gen != generation) {
                    result.forEach { it?.recycle() }
                    return@post
                }
                val old = bitmaps
                bitmaps = result
                left = -p.margin
                old.forEach { it?.recycle() }
            }
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bitmaps
        for (i in layers.indices) {
            val bmp = b[i] ?: continue
            canvas.drawBitmap(bmp, left + shift(layers[i].depth), height * layers[i].top, blit)
        }
    }

    /** Paints one set of layer bitmaps with a private [Lighting] snapshot (runs off the main thread). */
    private class Painter(private val width: Int, private val height: Int, private val l: Lighting) {

        val margin = width * 0.08f
        private val unit = width / 1080f
        private val sunOnLeft = l.reflectionX < 0.5f
        private val night = l.timeOfDay == TimeOfDay.NIGHT
        private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        private fun newCanvas(layer: Layer): Pair<Canvas, Bitmap>? {
            val w = (width + margin * 2).toInt()
            val h = (height * (layer.bottom - layer.top)).toInt().coerceAtLeast(1)
            val bmp = runCatching { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
            val c = Canvas(bmp)
            // Work in screen coordinates: x from -margin, y from 0 at the top of the screen.
            c.translate(margin, -height * layer.top)
            return c to bmp
        }

        private fun jitter(base: Int, rnd: Random, amount: Float): Int {
            val k = 1f + (rnd.nextFloat() - 0.5f) * amount
            return Color.rgb(
                (Color.red(base) * k).toInt().coerceIn(0, 255),
                (Color.green(base) * k).toInt().coerceIn(0, 255),
                (Color.blue(base) * k).toInt().coerceIn(0, 255),
            )
        }

        /** Tiny distant pine: a few ragged tiers, enough at this size. */
        private fun simplePine(c: Canvas, x: Float, baseY: Float, h: Float, lit: Int, dark: Int, rnd: Random) {
            val path = Path()
            val tiers = 5
            val maxHalf = h * 0.22f
            for (i in 0 until tiers) {
                val f = (i + 1f) / tiers
                val top = baseY - h + h * 0.9f * (i / tiers.toFloat())
                val bottom = top + h * (0.18f + 0.06f * f)
                val half = maxHalf * (0.15f + 0.85f * f) * (0.85f + rnd.nextFloat() * 0.3f)
                path.reset()
                path.moveTo(x, top)
                path.lineTo(x + half, bottom)
                path.lineTo(x, bottom - h * 0.03f)
                path.lineTo(x - half, bottom)
                path.close()
                fill.shader = LinearGradient(
                    if (sunOnLeft) x - half else x + half, 0f, if (sunOnLeft) x + half else x - half, 0f,
                    lit, dark, Shader.TileMode.CLAMP,
                )
                c.drawPath(path, fill)
            }
            fill.shader = null
        }

        /**
         * A conifer grown the way real ones are: a tapering trunk with whorls of drooping
         * branches, each covered in needle clusters. Branches behind the trunk are painted
         * first and darker; clusters get lighter towards the tips and on the sun's side, and
         * darker deep inside the crown.
         */
        private fun conifer(
            c: Canvas, x: Float, baseY: Float, h: Float, lit: Int, dark: Int, trunk: Int,
            rnd: Random, detail: Float,
        ) {
            val lean = (rnd.nextFloat() - 0.5f) * h * 0.03f
            val trunkW = h * 0.014f
            // Trunk, slightly darker on the shadow side.
            val tp = Path()
            tp.moveTo(x - trunkW, baseY)
            tp.lineTo(x - trunkW * 0.15f + lean, baseY - h * 0.7f)
            tp.lineTo(x + trunkW * 0.15f + lean, baseY - h * 0.7f)
            tp.lineTo(x + trunkW, baseY)
            tp.close()
            fill.shader = LinearGradient(
                x - trunkW, 0f, x + trunkW, 0f,
                if (sunOnLeft) SkyGradient.blend(trunk, Color.WHITE, 0.12f) else SkyGradient.blend(trunk, Color.BLACK, 0.4f),
                if (sunOnLeft) SkyGradient.blend(trunk, Color.BLACK, 0.4f) else SkyGradient.blend(trunk, Color.WHITE, 0.12f),
                Shader.TileMode.CLAMP,
            )
            c.drawPath(tp, fill)
            fill.shader = null

            val crownBottom = baseY - h * 0.05f
            val crownTop = baseY - h
            val maxLen = h * (0.3f + rnd.nextFloat() * 0.06f)
            val whorls = (46 * detail).toInt().coerceAtLeast(12)
            val clusterSize = h * 0.03f
            needle.strokeWidth = max(1.2f, h * 0.0042f)
            val sunSide = if (sunOnLeft) -1 else 1
            val deep = SkyGradient.blend(dark, Color.BLACK, 0.35f)

            for (pass in 0..1) { // 0 = branches behind the trunk, 1 = in front
                for (i in 0 until whorls) {
                    val t = i / (whorls - 1f) // 0 bottom .. 1 top
                    val y = crownBottom - (crownBottom - crownTop) * t.pow(0.92f) + (rnd.nextFloat() - 0.5f) * h * 0.01f
                    val cx = x + lean * t
                    val len = (maxLen * (1f - t).pow(0.65f) * (0.75f + rnd.nextFloat() * 0.45f) + h * 0.02f)
                    for (side in intArrayOf(-1, 1)) {
                        if (pass == 0 && rnd.nextFloat() < 0.2f) continue
                        val reach = if (pass == 0) len * 0.8f else len
                        // Branch droops: up slightly near the trunk, down towards the tip.
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
                            // Light: tips and sun side bright, inner crown and back branches dark.
                            var light = 0.25f + 0.55f * u + (if (side == sunSide) 0.18f else -0.12f)
                            if (pass == 0) light -= 0.3f
                            light = (light + (rnd.nextFloat() - 0.5f) * 0.25f).coerceIn(0f, 1f)
                            needle.color = jitter(
                                if (light < 0.3f) SkyGradient.blend(deep, dark, light / 0.3f)
                                else SkyGradient.blend(dark, lit, (light - 0.3f) / 0.7f),
                                rnd, 0.12f,
                            )
                            // A spray of needles hanging from the branch at this point.
                            val size = clusterSize * (0.6f + 0.6f * u) * (0.8f + rnd.nextFloat() * 0.4f)
                            val needles = (7 * detail).toInt().coerceIn(4, 9)
                            for (n in 0 until needles) {
                                val a = (side * 0.35f + (n - needles / 2f) * 0.32f + (rnd.nextFloat() - 0.5f) * 0.3f) +
                                    1.2f // mostly downwards
                                val nl = size * (0.6f + rnd.nextFloat() * 0.6f)
                                c.drawLine(px, py, px + cos(a) * nl * side * 0.9f, py + sin(a) * nl, needle)
                            }
                        }
                    }
                }
            }
            // Leader shoot at the very top.
            needle.color = lit
            c.drawLine(x + lean, crownTop + h * 0.02f, x + lean, crownTop - h * 0.02f, needle)
        }

        private fun hazeOver(c: Canvas, top: Float, bottom: Float, strength: Float) {
            val clear = l.skyBottom and 0x00FFFFFF
            val misty = clear or ((255 * strength).toInt() shl 24)
            val p = Paint()
            // Rises into a band of mist and fades out again, so there is no hard edge.
            p.shader = LinearGradient(
                0f, top, 0f, bottom, intArrayOf(clear, misty, misty, clear), floatArrayOf(0f, 0.45f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
            c.drawRect(-margin, top, width + margin, bottom, p)
        }

        fun paintFar(layer: Layer): Bitmap? {
            val (c, bmp) = newCanvas(layer) ?: return null
            val h = height.toFloat()
            val rnd = Random(101)
            val range = Path()
            Ridge(width, h * 0.7f, h * 0.15f, jagged = true, seed = 7).addTo(range, h * layer.bottom)
            val rock = l.distant(l.ground(0xFF6F8299.toInt()), 0.45f)
            fill.shader = LinearGradient(0f, h * 0.55f, 0f, h * 0.75f, rock, l.distant(rock, 0.5f), Shader.TileMode.CLAMP)
            c.drawPath(range, fill)
            fill.shader = null
            val ridge = Ridge(width, h * 0.75f, h * 0.035f, jagged = false, seed = 8)
            val ground = Path(); ridge.addTo(ground, h * layer.bottom)
            fill.color = l.distant(l.foliage(0xFF3E6450.toInt()), 0.42f)
            c.drawPath(ground, fill)
            val litBase = l.distant(l.foliage(0xFF4F7A5E.toInt()), 0.4f)
            val darkBase = l.distant(l.foliage(0xFF2F4F3E.toInt()), 0.4f)
            var x = -margin
            while (x < width + margin) {
                val th = h * (0.025f + rnd.nextFloat() * 0.03f)
                simplePine(c, x, ridge.yAt(x) + th * 0.25f, th, jitter(litBase, rnd, 0.18f), darkBase, rnd)
                x += 5f + rnd.nextFloat() * 8f
            }
            hazeOver(c, h * 0.62f, h * layer.bottom, 0.5f)
            return bmp
        }

        fun paintMid(layer: Layer): Bitmap? {
            val (c, bmp) = newCanvas(layer) ?: return null
            val h = height.toFloat()
            val rnd = Random(202)
            val ridge = Ridge(width, h * 0.8f, h * 0.05f, jagged = false, seed = 9)
            val hill = Path(); ridge.addTo(hill, h)
            fill.shader = LinearGradient(0f, h * 0.74f, 0f, h, l.distant(l.ground(0xFF3D6B45.toInt()), 0.2f), l.ground(0xFF24402A.toInt()), Shader.TileMode.CLAMP)
            c.drawPath(hill, fill)
            fill.shader = null
            val litBase = l.distant(l.foliage(0xFF4C8255.toInt()), 0.18f)
            val darkBase = l.distant(l.foliage(0xFF1C3A2A.toInt()), 0.18f)
            val trunk = l.shade(0xFF3A2C22.toInt())
            for (row in 0 until 2) {
                var x = -margin + rnd.nextFloat() * 10f
                while (x < width + margin) {
                    val th = h * (0.06f + rnd.nextFloat() * 0.05f) * (if (row == 0) 0.85f else 1f)
                    val base = ridge.yAt(x) + th * (0.15f + row * 0.12f)
                    conifer(c, x, base, th, jitter(litBase, rnd, 0.2f), darkBase, trunk, rnd, detail = 0.45f)
                    x += (16f + rnd.nextFloat() * 18f) * unit
                }
            }
            hazeOver(c, h * 0.74f, h * 0.92f, 0.22f)
            return bmp
        }

        fun paintNear(layer: Layer): Bitmap? {
            val (c, bmp) = newCanvas(layer) ?: return null
            val h = height.toFloat()
            val w = width.toFloat()
            val rnd = Random(303)
            val ridge = Ridge(width, h * 0.9f, h * 0.035f, jagged = false, seed = 10)
            val meadow = Path(); ridge.addTo(meadow, h)
            fill.shader = LinearGradient(0f, h * 0.86f, 0f, h, l.ground(0xFF4E7A3A.toInt()), l.ground(0xFF253F1E.toInt()), Shader.TileMode.CLAMP)
            c.drawPath(meadow, fill)
            fill.shader = null

            // Big foreground conifers at the sides, framing the view.
            val litBase = l.foliage(0xFF4F8A4A.toInt())
            val darkBase = l.foliage(0xFF183322.toInt())
            val trunk = l.shade(0xFF4A3628.toInt())
            val spots = floatArrayOf(-0.04f, 0.07f, 0.17f, 0.83f, 0.93f, 1.03f)
            for (s in spots) {
                val x = w * s + (rnd.nextFloat() - 0.5f) * w * 0.03f
                val th = h * (0.22f + rnd.nextFloat() * 0.12f)
                val gy = ridge.yAt(x)
                fill.shader = RadialGradient(x, gy + th * 0.02f, th * 0.28f, 0x77000000, 0, Shader.TileMode.CLAMP)
                c.drawCircle(x, gy + th * 0.02f, th * 0.28f, fill)
                fill.shader = null
                conifer(c, x, gy + th * 0.03f, th, jitter(litBase, rnd, 0.14f), darkBase, trunk, rnd, detail = 1f)
            }

            // Grass texture: thousands of short blades with varied colour and lean.
            val lightBlade = l.ground(0xFF7FA85A.toInt())
            val darkBlade = l.ground(0xFF1E3518.toInt())
            val count = (3200 * unit).toInt()
            for (i in 0 until count) {
                val x = -margin + rnd.nextFloat() * (w + margin * 2)
                val top = ridge.yAt(x)
                val d = rnd.nextFloat()
                val y = top + (h - top) * d
                val bh = h * (0.006f + 0.02f * d) * (0.6f + rnd.nextFloat())
                val leanB = (rnd.nextFloat() - 0.5f) * bh * 0.6f
                needle.strokeWidth = max(1f, 1.2f + d * 2.2f) * unit
                needle.color = SkyGradient.blend(darkBlade, lightBlade, (0.25f + rnd.nextFloat() * 0.75f) * (1f - d * 0.5f))
                c.drawLine(x, y, x + leanB, y - bh, needle)
            }
            if (!l.snow && !night) {
                val colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFD34A.toInt(), 0xFFE87AA8.toInt(), 0xFFB9A4FF.toInt())
                repeat((60 * unit).toInt()) {
                    val x = rnd.nextFloat() * w
                    val top = ridge.yAt(x)
                    val y = top + (h - top) * (0.1f + rnd.nextFloat() * 0.8f)
                    fill.color = l.shade(colors[rnd.nextInt(colors.size)])
                    c.drawCircle(x, y, (1.5f + rnd.nextFloat() * 2.5f) * unit, fill)
                }
            }
            return bmp
        }
    }

    private companion object {
        /** One background thread for all painting; slow, detailed work never blocks a frame. */
        val painter = Executors.newSingleThreadExecutor { r -> Thread(r, "SkyMoodPainter").apply { isDaemon = true } }
    }
}
