package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.skymood.domain.TimeOfDay
import com.skymood.wallpaper.scenes.SkyGradient
import kotlin.math.max
import kotlin.random.Random

/**
 * A detailed, painterly forest. Each depth layer is painted once into its own bitmap (when the
 * size, time of day or weather changes) with thousands of shaded details: individually lit pine
 * branches, texture in the grass, haze between layers and light from the sun's side. Every frame
 * then only copies three bitmaps, so it costs about the same as the simple shapes.
 */
class PaintedForestLandscape : Landscape() {

    private class Layer(val top: Float, val bottom: Float, val depth: Float) {
        var bitmap: Bitmap? = null
        var left = 0f
    }

    // Far: misty ridge and distant forest. Mid: forested hill. Near: big pines and meadow.
    private val far = Layer(0.5f, 0.82f, 0.1f)
    private val mid = Layer(0.6f, 1f, 0.5f)
    private val near = Layer(0.55f, 1f, 1f)
    private val layers = arrayOf(far, mid, near)
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun build() {
        // Painting happens in applyLighting, which always follows build.
    }

    override fun applyLighting() {
        for (layer in layers) {
            layer.bitmap?.recycle()
            layer.bitmap = null
        }
        paintFar(far)
        paintMid(mid)
        paintNear(near)
    }

    override fun draw(canvas: Canvas) {
        for (layer in layers) {
            val bmp = layer.bitmap ?: continue
            canvas.drawBitmap(bmp, layer.left + shift(layer.depth), height * layer.top, blit)
        }
    }

    // --- painting helpers (run only when re-painting, never per frame) ---

    private val margin get() = width * 0.08f

    private fun newCanvas(layer: Layer): Pair<Canvas, Bitmap>? {
        val w = (width + margin * 2).toInt()
        val h = (height * (layer.bottom - layer.top)).toInt().coerceAtLeast(1)
        val bmp = runCatching { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        layer.bitmap = bmp
        layer.left = -margin
        val c = Canvas(bmp)
        // Work in screen coordinates: x from -margin, y from 0 at the top of the screen.
        c.translate(margin, -height * layer.top)
        return c to bmp
    }

    private val sunOnLeft: Boolean get() = lighting.reflectionX < 0.5f
    private val night: Boolean get() = lighting.timeOfDay == TimeOfDay.NIGHT

    private fun shadeJitter(base: Int, rnd: Random, amount: Float): Int {
        val k = 1f + (rnd.nextFloat() - 0.5f) * amount
        return Color.rgb(
            (Color.red(base) * k).toInt().coerceIn(0, 255),
            (Color.green(base) * k).toInt().coerceIn(0, 255),
            (Color.blue(base) * k).toInt().coerceIn(0, 255),
        )
    }

    /**
     * A pine painted from a dozen drooping branch tiers with ragged edges, each shaded from the
     * lit side (towards the sun) to the shadow side, plus a few needle highlights.
     */
    private fun paintPine(
        c: Canvas, x: Float, baseY: Float, h: Float, lit: Int, dark: Int, trunk: Int,
        rnd: Random, detailed: Boolean,
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tiers = if (detailed) 11 + rnd.nextInt(4) else 6
        val maxHalf = h * (0.2f + rnd.nextFloat() * 0.06f)
        p.color = trunk
        c.drawRect(x - h * 0.012f, baseY - h * 0.25f, x + h * 0.012f, baseY, p)
        val path = Path()
        for (i in 0 until tiers) {
            val f = (i + 1f) / tiers
            val tierTop = baseY - h + h * 0.92f * (i / tiers.toFloat()) - h * 0.02f
            val tierBottom = tierTop + h * (0.14f + 0.06f * f)
            val half = maxHalf * (0.12f + 0.88f * f) * (0.85f + rnd.nextFloat() * 0.3f)
            path.reset()
            path.moveTo(x, tierTop)
            path.lineTo(x + half, tierBottom)
            // Ragged lower edge: little drooping needle clumps.
            val teeth = if (detailed) 7 else 4
            for (t in 1..teeth) {
                val u = 1f - t / teeth.toFloat()
                val tx = x - half + 2f * half * u
                val ty = tierBottom - (if (t % 2 == 0) 0f else h * 0.018f) - rnd.nextFloat() * h * 0.01f
                path.lineTo(tx + half * 0.06f, ty)
                path.lineTo(tx, tierBottom + rnd.nextFloat() * h * 0.012f)
            }
            path.lineTo(x - half, tierBottom)
            path.close()
            val l = if (sunOnLeft) x - half else x + half
            val r = if (sunOnLeft) x + half else x - half
            p.shader = LinearGradient(l, 0f, r, 0f, lit, dark, Shader.TileMode.CLAMP)
            c.drawPath(path, p)
        }
        p.shader = null
    }

    private fun hazeOver(c: Canvas, top: Float, bottom: Float, strength: Float) {
        val sky = lighting.skyBottom
        val p = Paint()
        val clear = sky and 0x00FFFFFF
        val misty = clear or ((255 * strength).toInt() shl 24)
        // Rises into a band of mist and fades out again, so there is no hard edge.
        p.shader = LinearGradient(
            0f, top, 0f, bottom, intArrayOf(clear, misty, misty, clear), floatArrayOf(0f, 0.45f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(-margin, top, width + margin, bottom, p)
    }

    private fun paintFar(layer: Layer) {
        val (c, _) = newCanvas(layer) ?: return
        val l = lighting
        val h = height.toFloat()
        val rnd = Random(101)
        // Distant mountain range, bluish with height-based haze.
        val range = Path()
        Ridge(width, h * 0.7f, h * 0.15f, jagged = true, seed = 7).addTo(range, h * layer.bottom)
        val mountain = Paint(Paint.ANTI_ALIAS_FLAG)
        val rock = l.distant(l.ground(0xFF6F8299.toInt()), 0.45f)
        mountain.shader = LinearGradient(0f, h * 0.55f, 0f, h * 0.75f, rock, l.distant(rock, 0.5f), Shader.TileMode.CLAMP)
        c.drawPath(range, mountain)
        // A far forest: many tiny pines, colour-jittered, sitting on a soft ridge.
        val ridge = Ridge(width, h * 0.75f, h * 0.035f, jagged = false, seed = 8)
        val ground = Path(); ridge.addTo(ground, h * layer.bottom)
        val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = l.distant(l.foliage(0xFF3E6450.toInt()), 0.42f) }
        c.drawPath(ground, gp)
        val litBase = l.distant(l.foliage(0xFF4F7A5E.toInt()), 0.4f)
        val darkBase = l.distant(l.foliage(0xFF2F4F3E.toInt()), 0.4f)
        var x = -margin
        while (x < width + margin) {
            val th = h * (0.025f + rnd.nextFloat() * 0.03f)
            paintPine(c, x, ridge.yAt(x) + th * 0.25f, th, shadeJitter(litBase, rnd, 0.18f), darkBase, darkBase, rnd, detailed = false)
            x += 5f + rnd.nextFloat() * 8f
        }
        hazeOver(c, h * 0.62f, h * layer.bottom, 0.5f)
    }

    private fun paintMid(layer: Layer) {
        val (c, _) = newCanvas(layer) ?: return
        val l = lighting
        val h = height.toFloat()
        val rnd = Random(202)
        val ridge = Ridge(width, h * 0.8f, h * 0.05f, jagged = false, seed = 9)
        val hill = Path(); ridge.addTo(hill, h)
        val hp = Paint(Paint.ANTI_ALIAS_FLAG)
        hp.shader = LinearGradient(0f, h * 0.74f, 0f, h, l.distant(l.ground(0xFF3D6B45.toInt()), 0.2f), l.ground(0xFF24402A.toInt()), Shader.TileMode.CLAMP)
        c.drawPath(hill, hp)
        val litBase = l.distant(l.foliage(0xFF3F7550.toInt()), 0.2f)
        val darkBase = l.distant(l.foliage(0xFF1C3A2A.toInt()), 0.2f)
        val trunk = l.shade(0xFF3A2C22.toInt())
        // Two rows so the forest looks dense, back row first.
        for (row in 0 until 2) {
            var x = -margin + rnd.nextFloat() * 10f
            while (x < width + margin) {
                val th = h * (0.06f + rnd.nextFloat() * 0.05f) * (if (row == 0) 0.85f else 1f)
                val base = ridge.yAt(x) + th * (0.18f + row * 0.12f)
                paintPine(c, x, base, th, shadeJitter(litBase, rnd, 0.22f), darkBase, trunk, rnd, detailed = true)
                x += (14f + rnd.nextFloat() * 18f) * (width / 1080f)
            }
        }
        hazeOver(c, h * 0.74f, h * 0.92f, 0.22f)
    }

    private fun paintNear(layer: Layer) {
        val (c, _) = newCanvas(layer) ?: return
        val l = lighting
        val h = height.toFloat()
        val w = width.toFloat()
        val rnd = Random(303)
        val ridge = Ridge(width, h * 0.9f, h * 0.035f, jagged = false, seed = 10)
        val meadow = Path(); ridge.addTo(meadow, h)
        val mp = Paint(Paint.ANTI_ALIAS_FLAG)
        val grassTop = l.ground(0xFF4E7A3A.toInt())
        val grassBottom = l.ground(0xFF253F1E.toInt())
        mp.shader = LinearGradient(0f, h * 0.86f, 0f, h, grassTop, grassBottom, Shader.TileMode.CLAMP)
        c.drawPath(meadow, mp)

        // Big foreground pines at the sides, framing the view.
        val litBase = l.foliage(0xFF34643F.toInt())
        val darkBase = l.foliage(0xFF13281C.toInt())
        val trunk = l.shade(0xFF33261C.toInt())
        val spots = floatArrayOf(-0.04f, 0.06f, 0.16f, 0.82f, 0.92f, 1.03f)
        for (s in spots) {
            val x = w * s + (rnd.nextFloat() - 0.5f) * w * 0.03f
            val th = h * (0.2f + rnd.nextFloat() * 0.1f)
            // Soft contact shadow under each tree.
            val sh = Paint(Paint.ANTI_ALIAS_FLAG)
            sh.shader = RadialGradient(x, ridge.yAt(x) + th * 0.02f, th * 0.25f, 0x66000000, 0, Shader.TileMode.CLAMP)
            c.drawCircle(x, ridge.yAt(x) + th * 0.02f, th * 0.25f, sh)
            paintPine(c, x, ridge.yAt(x) + th * 0.03f, th, shadeJitter(litBase, rnd, 0.15f), darkBase, trunk, rnd, detailed = true)
        }

        // Grass texture: thousands of short blades with varied colour and lean.
        val blade = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        val lightBlade = l.ground(0xFF7FA85A.toInt())
        val darkBlade = l.ground(0xFF1E3518.toInt())
        val count = (3200 * (w / 1080f)).toInt()
        for (i in 0 until count) {
            val x = -margin + rnd.nextFloat() * (w + margin * 2)
            val top = ridge.yAt(x)
            val d = rnd.nextFloat()
            val y = top + (h - top) * d
            val bh = h * (0.006f + 0.02f * d) * (0.6f + rnd.nextFloat())
            val lean = (rnd.nextFloat() - 0.5f) * bh * 0.6f
            blade.strokeWidth = max(1f, 1.2f + d * 2.2f) * (w / 1080f)
            blade.color = SkyGradient.blend(darkBlade, lightBlade, (0.25f + rnd.nextFloat() * 0.75f) * (1f - d * 0.5f))
            c.drawLine(x, y, x + lean, y - bh, blade)
        }
        // A few wildflowers catching the light.
        if (!l.snow && !night) {
            val flower = Paint(Paint.ANTI_ALIAS_FLAG)
            val colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFD34A.toInt(), 0xFFE87AA8.toInt(), 0xFFB9A4FF.toInt())
            repeat((60 * (w / 1080f)).toInt()) {
                val x = rnd.nextFloat() * w
                val top = ridge.yAt(x)
                val y = top + (h - top) * (0.1f + rnd.nextFloat() * 0.8f)
                flower.color = l.shade(colors[rnd.nextInt(colors.size)])
                c.drawCircle(x, y, (1.5f + rnd.nextFloat() * 2.5f) * (w / 1080f), flower)
            }
        }
    }
}
