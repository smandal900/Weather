package com.skymood.wallpaper.landscape

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.skymood.wallpaper.scenes.SkyGradient
import kotlin.math.sin
import kotlin.random.Random

private fun fill() = Paint(Paint.ANTI_ALIAS_FLAG)

/** Fades a far-away colour into the sky near the horizon. */
internal fun Lighting.distant(color: Int, amount: Float) = SkyGradient.blend(color, skyBottom, amount)

class ForestLandscape : Landscape() {
    private val far = Path(); private val midGround = Path(); private val midTrees = Path()
    private val nearGround = Path(); private val nearTrees = Path()
    private val farPaint = fill(); private val midGroundPaint = fill(); private val midTreePaint = fill()
    private val nearGroundPaint = fill(); private val nearTreePaint = fill()

    override fun build() {
        val w = width; val h = height.toFloat(); val unit = w / 1080f
        listOf(far, midGround, midTrees, nearGround, nearTrees).forEach { it.reset() }
        val rnd = Random(21)
        val farRidge = Ridge(w, h * 0.67f, h * 0.06f, jagged = false, seed = 1)
        farRidge.addTo(far, h)
        // A soft row of distant treetops on the far ridge.
        var x = farRidge.left
        while (x < w * 1.1f) { Trees.pine(far, x, farRidge.yAt(x) + 4f, h * (0.03f + rnd.nextFloat() * 0.02f)); x += 14f * unit }
        val midRidge = Ridge(w, h * 0.75f, h * 0.035f, jagged = false, seed = 2)
        midRidge.addTo(midGround, h)
        x = midRidge.left
        while (x < w * 1.1f) { Trees.pine(midTrees, x, midRidge.yAt(x) + 3f, h * (0.05f + rnd.nextFloat() * 0.04f)); x += (20f + rnd.nextFloat() * 14f) * unit }
        val nearRidge = Ridge(w, h * 0.87f, h * 0.025f, jagged = false, seed = 3)
        nearRidge.addTo(nearGround, h)
        x = nearRidge.left
        while (x < w * 1.1f) { Trees.pine(nearTrees, x, nearRidge.yAt(x) + 6f, h * (0.11f + rnd.nextFloat() * 0.09f)); x += (48f + rnd.nextFloat() * 50f) * unit }
    }

    override fun applyLighting() {
        val l = lighting
        farPaint.color = l.distant(l.foliage(0xFF5F8572.toInt()), 0.45f)
        midGroundPaint.color = l.distant(l.ground(0xFF3E6B48.toInt()), 0.2f)
        midTreePaint.color = l.distant(l.foliage(0xFF2C5A3B.toInt()), 0.2f)
        nearGroundPaint.color = l.ground(0xFF223D29.toInt())
        nearTreePaint.color = l.foliage(0xFF17321F.toInt())
    }

    override fun draw(canvas: Canvas) {
        drawLayer(canvas, far, farPaint, 0.1f)
        drawLayer(canvas, midGround, midGroundPaint, 0.5f)
        drawLayer(canvas, midTrees, midTreePaint, 0.5f)
        drawLayer(canvas, nearGround, nearGroundPaint, 1f)
        drawLayer(canvas, nearTrees, nearTreePaint, 1f)
    }
}

class MountainLandscape : Landscape() {
    private val far = Path(); private val mid = Path(); private val near = Path(); private val trees = Path()
    private val farPaint = fill(); private val midPaint = fill(); private val nearPaint = fill(); private val treePaint = fill()

    override fun build() {
        val w = width; val h = height.toFloat(); val unit = w / 1080f
        listOf(far, mid, near, trees).forEach { it.reset() }
        Ridge(w, h * 0.66f, h * 0.2f, jagged = true, seed = 4).addTo(far, h)
        Ridge(w, h * 0.74f, h * 0.14f, jagged = true, seed = 5).addTo(mid, h)
        val nearRidge = Ridge(w, h * 0.88f, h * 0.06f, jagged = true, seed = 6)
        nearRidge.addTo(near, h)
        val rnd = Random(7)
        var x = nearRidge.left
        while (x < w * 1.1f) {
            if (rnd.nextFloat() < 0.6f) Trees.pine(trees, x, nearRidge.yAt(x) + 5f, h * (0.05f + rnd.nextFloat() * 0.05f))
            x += (30f + rnd.nextFloat() * 40f) * unit
        }
    }

    override fun applyLighting() {
        val l = lighting
        farPaint.color = l.distant(l.ground(0xFF7D8CA3.toInt()), 0.4f)
        midPaint.color = l.distant(l.ground(0xFF56647A.toInt()), 0.18f)
        nearPaint.color = l.ground(0xFF353F4C.toInt())
        treePaint.color = l.foliage(0xFF1E3326.toInt())
    }

    override fun draw(canvas: Canvas) {
        drawLayer(canvas, far, farPaint, 0.1f)
        drawLayer(canvas, mid, midPaint, 0.45f)
        drawLayer(canvas, near, nearPaint, 1f)
        drawLayer(canvas, trees, treePaint, 1f)
    }
}

class HillLandscape : Landscape() {
    private val far = Path(); private val mid = Path(); private val near = Path()
    private val midTrees = Path(); private val nearTrees = Path()
    private val farPaint = fill(); private val midPaint = fill(); private val nearPaint = fill()
    private val midTreePaint = fill(); private val nearTreePaint = fill()

    override fun build() {
        val w = width; val h = height.toFloat(); val unit = w / 1080f
        listOf(far, mid, near, midTrees, nearTrees).forEach { it.reset() }
        Ridge(w, h * 0.69f, h * 0.07f, jagged = false, seed = 8).addTo(far, h)
        val midRidge = Ridge(w, h * 0.78f, h * 0.06f, jagged = false, seed = 9)
        midRidge.addTo(mid, h)
        val nearRidge = Ridge(w, h * 0.9f, h * 0.045f, jagged = false, seed = 10)
        nearRidge.addTo(near, h)
        val rnd = Random(11)
        var x = midRidge.left
        while (x < w * 1.1f) {
            if (rnd.nextFloat() < 0.35f) Trees.round(midTrees, x, midRidge.yAt(x) + 3f, h * (0.035f + rnd.nextFloat() * 0.02f))
            x += 40f * unit
        }
        x = nearRidge.left
        while (x < w * 1.1f) {
            if (rnd.nextFloat() < 0.25f) Trees.round(nearTrees, x, nearRidge.yAt(x) + 4f, h * (0.07f + rnd.nextFloat() * 0.04f))
            x += 70f * unit
        }
    }

    override fun applyLighting() {
        val l = lighting
        farPaint.color = l.distant(l.ground(0xFF87AE74.toInt()), 0.4f)
        midPaint.color = l.distant(l.ground(0xFF5E9152.toInt()), 0.12f)
        nearPaint.color = l.ground(0xFF3D7035.toInt())
        midTreePaint.color = l.distant(l.foliage(0xFF2F6030.toInt()), 0.12f)
        nearTreePaint.color = l.foliage(0xFF234A24.toInt())
    }

    override fun draw(canvas: Canvas) {
        drawLayer(canvas, far, farPaint, 0.1f)
        drawLayer(canvas, mid, midPaint, 0.5f)
        drawLayer(canvas, midTrees, midTreePaint, 0.5f)
        drawLayer(canvas, near, nearPaint, 1f)
        drawLayer(canvas, nearTrees, nearTreePaint, 1f)
    }
}

/** A meadow whose grass sways with the wind, under a distant tree line. */
class GrassFieldLandscape : Landscape() {
    private val treeLine = Path(); private val meadow = Path()
    private val treePaint = fill(); private val meadowPaint = fill()
    private val darkBlade = fill().apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val lightBlade = fill().apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val flowerPaints = arrayOf(fill(), fill(), fill())
    private val bx = FloatArray(BLADES); private val by = FloatArray(BLADES)
    private val bh = FloatArray(BLADES); private val bp = FloatArray(BLADES)
    private val darkLines = FloatArray(BLADES * 4); private val lightLines = FloatArray(BLADES * 4)
    private val fx = FloatArray(FLOWERS); private val fy = FloatArray(FLOWERS)

    override fun build() {
        val w = width; val h = height.toFloat(); val unit = w / 1080f
        treeLine.reset(); meadow.reset()
        val ridge = Ridge(w, h * 0.72f, h * 0.02f, jagged = false, seed = 12)
        val rnd = Random(13)
        var x = ridge.left
        while (x < w * 1.1f) {
            // Sink the distant trees a little so no sky shows under their canopies.
            val th = h * (0.03f + rnd.nextFloat() * 0.025f)
            Trees.round(treeLine, x, ridge.yAt(x) + th * 0.16f, th)
            x += (16f + rnd.nextFloat() * 14f) * unit
        }
        ridge.addTo(treeLine, h)
        Ridge(w, h * 0.76f, h * 0.012f, jagged = false, seed = 14).addTo(meadow, h)
        for (i in 0 until BLADES) {
            val d = rnd.nextFloat()
            by[i] = h * (0.78f + 0.22f * d)
            bx[i] = -w * 0.05f + rnd.nextFloat() * w * 1.1f
            bh[i] = h * (0.015f + 0.06f * d * d) * (0.7f + rnd.nextFloat() * 0.6f)
            bp[i] = rnd.nextFloat() * 6.28f
        }
        for (i in 0 until FLOWERS) {
            fx[i] = rnd.nextFloat() * w
            fy[i] = h * (0.8f + rnd.nextFloat() * 0.18f)
        }
        darkBlade.strokeWidth = 3f * unit.coerceAtLeast(0.6f)
        lightBlade.strokeWidth = 2.2f * unit.coerceAtLeast(0.6f)
    }

    override fun applyLighting() {
        val l = lighting
        treePaint.color = l.distant(l.foliage(0xFF2F5A33.toInt()), 0.3f)
        meadowPaint.color = l.ground(0xFF5E9A45.toInt())
        darkBlade.color = l.foliage(0xFF2F6A2A.toInt())
        lightBlade.color = l.foliage(0xFF86C15A.toInt())
        flowerPaints[0].color = l.shade(0xFFFFFFFF.toInt())
        flowerPaints[1].color = l.shade(0xFFFFD34A.toInt())
        flowerPaints[2].color = l.shade(0xFFE87AA8.toInt())
    }

    override fun draw(canvas: Canvas) {
        drawLayer(canvas, treeLine, treePaint, 0.2f)
        drawLayer(canvas, meadow, meadowPaint, 0.6f)
        val t = timeMs / 1000f
        val gust = 1f + windKmh / 15f
        val sway = (0.15f + windKmh / 40f).coerceAtMost(1.4f)
        val s = shift(1f)
        if (!lighting.snow) {
            for (i in 0 until FLOWERS) canvas.drawCircle(fx[i] + s, fy[i], height * 0.0035f, flowerPaints[i % 3])
        }
        var d = 0; var li = 0
        for (i in 0 until BLADES) {
            val bend = (sin(t * gust + bp[i] + bx[i] * 0.004f) * 0.5f + 0.5f * sway) * bh[i] * sway
            val x0 = bx[i] + s
            if (i % 3 == 0) {
                lightLines[li++] = x0; lightLines[li++] = by[i]
                lightLines[li++] = x0 + bend; lightLines[li++] = by[i] - bh[i]
            } else {
                darkLines[d++] = x0; darkLines[d++] = by[i]
                darkLines[d++] = x0 + bend; darkLines[d++] = by[i] - bh[i]
            }
        }
        canvas.drawLines(darkLines, 0, d, darkBlade)
        canvas.drawLines(lightLines, 0, li, lightBlade)
    }

    private companion object {
        const val BLADES = 420
        const val FLOWERS = 28
    }
}

/** Tall jagged peaks with permanent snow caps above a snowy pine forest. */
class SnowyPeaksLandscape : Landscape() {
    private val far = Path(); private val farCaps = Path(); private val mid = Path(); private val midCaps = Path()
    private val near = Path(); private val trees = Path()
    private val farPaint = fill(); private val capPaint = fill(); private val midPaint = fill(); private val midCapPaint = fill()
    private val nearPaint = fill(); private val treePaint = fill()

    private fun caps(path: Path, ridge: Ridge, snowline: Float, w: Int) {
        val n = 120
        val xs = FloatArray(n + 1) { ridge.left + w * 1.2f * it / n }
        path.moveTo(xs[0], ridge.yAt(xs[0]))
        for (i in 0..n) path.lineTo(xs[i], ridge.yAt(xs[i]))
        for (i in n downTo 0) {
            val y = ridge.yAt(xs[i])
            val depth = ((snowline - y) * 0.75f).coerceAtLeast(0f)
            val jag = if (depth > 0f) sin(xs[i] * 0.05f) * depth * 0.18f else 0f
            path.lineTo(xs[i], y + depth + jag)
        }
        path.close()
    }

    override fun build() {
        val w = width; val h = height.toFloat(); val unit = w / 1080f
        listOf(far, farCaps, mid, midCaps, near, trees).forEach { it.reset() }
        val farRidge = Ridge(w, h * 0.64f, h * 0.3f, jagged = true, seed = 15)
        farRidge.addTo(far, h)
        caps(farCaps, farRidge, h * 0.5f, w)
        val midRidge = Ridge(w, h * 0.76f, h * 0.16f, jagged = true, seed = 16)
        midRidge.addTo(mid, h)
        caps(midCaps, midRidge, h * 0.66f, w)
        val nearRidge = Ridge(w, h * 0.9f, h * 0.03f, jagged = false, seed = 17)
        nearRidge.addTo(near, h)
        val rnd = Random(18)
        var x = nearRidge.left
        while (x < w * 1.1f) { Trees.pine(trees, x, nearRidge.yAt(x) + 5f, h * (0.07f + rnd.nextFloat() * 0.07f)); x += (34f + rnd.nextFloat() * 40f) * unit }
    }

    override fun applyLighting() {
        val l = lighting
        farPaint.color = l.distant(l.shade(0xFF6E7B92.toInt()), 0.35f)
        capPaint.color = l.distant(l.shade(Lighting.SNOW), 0.2f)
        midPaint.color = l.distant(l.shade(0xFF4A566B.toInt()), 0.12f)
        midCapPaint.color = l.shade(0xFFE4EAF2.toInt())
        nearPaint.color = l.shade(Lighting.SNOW)
        treePaint.color = l.shade(SkyGradient.blend(0xFF1C3A2C.toInt(), Lighting.SNOW, 0.2f))
    }

    override fun draw(canvas: Canvas) {
        drawLayer(canvas, far, farPaint, 0.1f)
        drawLayer(canvas, farCaps, capPaint, 0.1f)
        drawLayer(canvas, mid, midPaint, 0.45f)
        drawLayer(canvas, midCaps, midCapPaint, 0.45f)
        drawLayer(canvas, near, nearPaint, 1f)
        drawLayer(canvas, trees, treePaint, 1f)
    }
}

/** Rolling farmland with a little village; windows glow in the evening and at night. */
class VillageLandscape : Landscape() {
    private val far = Path(); private val mid = Path(); private val near = Path()
    private val walls = Path(); private val roofs = Path(); private val windows = Path(); private val trees = Path()
    private val farPaint = fill(); private val midPaint = fill(); private val nearPaint = fill()
    private val wallPaint = fill(); private val roofPaint = fill(); private val windowPaint = fill(); private val treePaint = fill()

    override fun build() {
        val w = width; val h = height.toFloat(); val unit = w / 1080f
        listOf(far, mid, near, walls, roofs, windows, trees).forEach { it.reset() }
        Ridge(w, h * 0.7f, h * 0.05f, jagged = false, seed = 19).addTo(far, h)
        val midRidge = Ridge(w, h * 0.8f, h * 0.035f, jagged = false, seed = 20)
        midRidge.addTo(mid, h)
        Ridge(w, h * 0.92f, h * 0.02f, jagged = false, seed = 21).addTo(near, h)
        val rnd = Random(22)
        var x = w * 0.06f
        var index = 0
        while (x < w * 0.95f) {
            val base = midRidge.yAt(x) + 2f
            if (rnd.nextFloat() < 0.3f) {
                Trees.round(trees, x, base, h * (0.03f + rnd.nextFloat() * 0.02f))
                x += 45f * unit
                continue
            }
            val church = index == 3
            val hw = w * (if (church) 0.032f else 0.035f + rnd.nextFloat() * 0.01f)
            val hh = if (church) hw * 2.2f else hw * (1f + rnd.nextFloat() * 0.3f)
            walls.addRect(x - hw, base - hh, x + hw, base + 2f, Path.Direction.CW)
            roofs.moveTo(x - hw * 1.2f, base - hh)
            roofs.lineTo(x, base - hh - hw * (if (church) 1.8f else 0.9f))
            roofs.lineTo(x + hw * 1.2f, base - hh)
            roofs.close()
            val ws = hw * 0.32f
            if (church) {
                windows.addRect(x - ws * 0.5f, base - hh * 0.75f, x + ws * 0.5f, base - hh * 0.45f, Path.Direction.CW)
            } else {
                windows.addRect(x - hw * 0.6f, base - hh * 0.65f, x - hw * 0.6f + ws, base - hh * 0.65f + ws, Path.Direction.CW)
                windows.addRect(x + hw * 0.6f - ws, base - hh * 0.65f, x + hw * 0.6f, base - hh * 0.65f + ws, Path.Direction.CW)
            }
            index++
            x += hw * 2f + (14f + rnd.nextFloat() * 40f) * unit
        }
    }

    override fun applyLighting() {
        val l = lighting
        farPaint.color = l.distant(l.ground(0xFF8BAF76.toInt()), 0.35f)
        midPaint.color = l.ground(0xFF6A9A55.toInt())
        nearPaint.color = l.ground(0xFF487C3A.toInt())
        wallPaint.color = l.shade(0xFFEADBC2.toInt())
        roofPaint.color = l.ground(0xFFA4483A.toInt())
        treePaint.color = l.foliage(0xFF2D5E2E.toInt())
        if (l.windowsLit) {
            windowPaint.color = 0xFFFFD27A.toInt()
        } else {
            windowPaint.color = l.shade(0xFF4A5566.toInt())
        }
    }

    override fun draw(canvas: Canvas) {
        drawLayer(canvas, far, farPaint, 0.15f)
        drawLayer(canvas, mid, midPaint, 0.55f)
        drawLayer(canvas, trees, treePaint, 0.55f)
        drawLayer(canvas, walls, wallPaint, 0.55f)
        drawLayer(canvas, roofs, roofPaint, 0.55f)
        drawLayer(canvas, windows, windowPaint, 0.55f)
        drawLayer(canvas, near, nearPaint, 1f)
    }
}
