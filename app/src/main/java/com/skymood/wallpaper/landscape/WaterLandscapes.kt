package com.skymood.wallpaper.landscape

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.sin
import kotlin.random.Random

private fun fill() = Paint(Paint.ANTI_ALIAS_FLAG)
private fun stroke() = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

private const val DEEP_SEA = 0xFF123E5C.toInt()
private const val DEEP_LAKE = 0xFF173F4A.toInt()

/** Sea meeting a sandy beach with lapping foam and a palm tree swaying in the wind. */
class BeachLandscape : Landscape() {
    private val water = Water()
    private val headland = Path(); private val sand = Path(); private val wet = Path(); private val foam = Path()
    private val trunk = Path(); private val fronds = Path()
    private val headlandPaint = fill(); private val sandPaint = fill(); private val wetPaint = fill()
    private val foamPaint = stroke(); private val trunkPaint = stroke(); private val frondPaint = fill()
    private var shoreY = 0f
    private var palmX = 0f
    private var palmBase = 0f

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        listOf(headland, sand, wet, trunk, fronds).forEach { it.reset() }
        val horizon = h * 0.66f
        shoreY = h * 0.83f
        water.build(-w * 0.1f, w * 1.1f, horizon, shoreY + h * 0.03f, unit)
        // A low headland on the horizon at the left.
        headland.moveTo(-w * 0.1f, horizon + 1f)
        headland.quadTo(w * 0.05f, horizon - h * 0.045f, w * 0.22f, horizon - h * 0.012f)
        headland.quadTo(w * 0.3f, horizon - h * 0.004f, w * 0.36f, horizon + 1f)
        headland.close()
        sand.moveTo(-w * 0.1f, h)
        sand.lineTo(-w * 0.1f, shoreY + h * 0.02f)
        sand.quadTo(w * 0.35f, shoreY - h * 0.012f, w * 0.7f, shoreY + h * 0.01f)
        sand.quadTo(w * 0.95f, shoreY + h * 0.022f, w * 1.1f, shoreY + h * 0.005f)
        sand.lineTo(w * 1.1f, h)
        sand.close()
        wet.moveTo(-w * 0.1f, shoreY + h * 0.02f)
        wet.quadTo(w * 0.35f, shoreY - h * 0.012f, w * 0.7f, shoreY + h * 0.01f)
        wet.quadTo(w * 0.95f, shoreY + h * 0.022f, w * 1.1f, shoreY + h * 0.005f)
        wet.lineTo(w * 1.1f, shoreY + h * 0.03f)
        wet.quadTo(w * 0.35f, shoreY + h * 0.012f, -w * 0.1f, shoreY + h * 0.04f)
        wet.close()
        // Palm tree in local coordinates around its base.
        palmX = w * 0.82f
        palmBase = h * 0.93f
        val th = h * 0.26f
        trunk.moveTo(0f, 0f)
        trunk.quadTo(th * 0.05f, -th * 0.55f, -th * 0.18f, -th)
        for (i in 0 until 7) {
            val a = -2.9f + i * 0.5f
            val len = th * (0.45f + (i % 2) * 0.1f)
            val tipX = -th * 0.18f + kotlin.math.cos(a) * len
            val tipY = -th + kotlin.math.sin(a) * len * 0.55f + len * 0.25f
            fronds.moveTo(-th * 0.18f, -th)
            fronds.quadTo((-th * 0.18f + tipX) / 2f, -th - len * 0.22f, tipX, tipY)
            fronds.quadTo((-th * 0.18f + tipX) / 2f, -th - len * 0.05f, -th * 0.18f, -th)
            fronds.close()
        }
        trunkPaint.strokeWidth = th * 0.06f
        foamPaint.strokeWidth = 3f * unit.coerceAtLeast(0.6f)
    }

    override fun applyLighting() {
        val l = lighting
        water.applyLighting(l, DEEP_SEA, width)
        headlandPaint.color = l.distant(l.foliage(0xFF3F6B4A.toInt()), 0.5f)
        sandPaint.color = l.ground(0xFFE4CD9C.toInt())
        wetPaint.color = l.ground(0xFFB79E70.toInt())
        foamPaint.color = l.shade(0xFFFFFFFF.toInt())
        trunkPaint.color = l.shade(0xFF6A4B2E.toInt())
        frondPaint.color = l.foliage(0xFF2F6B35.toInt())
    }

    override fun draw(canvas: Canvas) {
        water.draw(canvas, timeMs, windKmh, shift(0.4f))
        drawLayer(canvas, headland, headlandPaint, 0.1f)
        drawLayer(canvas, wet, wetPaint, 0.8f)
        // Foam line that washes up and back.
        val t = timeMs / 1000f
        val w = width.toFloat(); val h = height.toFloat()
        val reach = sin(t * 0.7f) * h * 0.008f
        foam.rewind()
        for (i in 0..24) {
            val x = -w * 0.1f + w * 1.2f * i / 24f
            val y = shoreY + h * 0.02f - h * 0.03f * (1f - ((x / w - 0.35f) * (x / w - 0.35f))) * 0.4f +
                reach + sin(x * 0.02f + t * 2f) * h * 0.002f
            if (i == 0) foam.moveTo(x, y) else foam.lineTo(x, y)
        }
        foamPaint.alpha = (170 + 60 * sin(t * 0.7f)).toInt()
        drawLayer(canvas, foam, foamPaint, 0.8f)
        drawLayer(canvas, sand, sandPaint, 1f)
        canvas.save()
        canvas.translate(palmX + shift(1f), palmBase)
        canvas.rotate(sin(t * (0.8f + windKmh / 30f)) * (1f + windKmh / 12f))
        canvas.drawPath(trunk, trunkPaint)
        canvas.drawPath(fronds, frondPaint)
        canvas.restore()
    }
}

/** Open water to the horizon, optionally with a sailing boat bobbing on the swell. */
class SeaLandscape(private val withBoat: Boolean) : Landscape() {
    private val water = Water(maxGlints = 110)
    private val swell = Path()
    private val swellPaint = stroke()
    private val hull = Path(); private val sail = Path(); private val jib = Path(); private val mast = Path()
    private val hullPaint = fill(); private val sailPaint = fill(); private val jibPaint = fill(); private val mastPaint = stroke()
    private val lampPaint = fill()
    private var horizon = 0f
    private var boatX = 0f

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        horizon = h * 0.64f
        water.build(-w * 0.1f, w * 1.1f, horizon, h, unit)
        swellPaint.strokeWidth = 2.5f * unit.coerceAtLeast(0.6f)
        listOf(hull, sail, jib, mast).forEach { it.reset() }
        // Boat in local units, about 160 wide; origin at the waterline centre.
        val s = unit * 1.1f
        hull.moveTo(-80f * s, -6f * s)
        hull.lineTo(80f * s, -6f * s)
        hull.lineTo(58f * s, 18f * s)
        hull.lineTo(-60f * s, 18f * s)
        hull.close()
        mast.moveTo(0f, -6f * s); mast.lineTo(0f, -150f * s)
        sail.moveTo(4f * s, -146f * s); sail.lineTo(70f * s, -12f * s); sail.lineTo(4f * s, -12f * s); sail.close()
        jib.moveTo(-4f * s, -132f * s); jib.lineTo(-4f * s, -12f * s); jib.lineTo(-64f * s, -12f * s); jib.close()
        mastPaint.strokeWidth = 4f * s
        boatX = w * 0.62f
    }

    override fun applyLighting() {
        val l = lighting
        water.applyLighting(l, DEEP_SEA, width)
        swellPaint.color = l.shade(0xFFD8ECF5.toInt())
        hullPaint.color = l.shade(0xFF7A3B24.toInt())
        sailPaint.color = l.shade(0xFFF5F0E2.toInt())
        jibPaint.color = l.shade(0xFFE4DCC8.toInt())
        mastPaint.color = l.shade(0xFF3A2A20.toInt())
        lampPaint.color = if (l.windowsLit) 0xFFFFD27A.toInt() else 0
    }

    override fun update(dtMs: Long) {
        super.update(dtMs)
        if (withBoat) {
            boatX += (8f + windKmh * 0.6f) * (width / 1080f) * dtMs / 1000f
            if (boatX > width * 1.15f) boatX = -width * 0.15f
        }
    }

    override fun draw(canvas: Canvas) {
        water.draw(canvas, timeMs, windKmh, shift(0.5f))
        val t = timeMs / 1000f
        val w = width.toFloat(); val h = height.toFloat()
        val rough = 1f + windKmh / 25f
        // Rolling swells, closer ones bigger and lower.
        for (row in 0 until 5) {
            val f = (row + 1) / 6f
            val y0 = horizon + (h - horizon) * f * f
            val amp = h * 0.004f * (1f + row) * rough
            swell.rewind()
            for (i in 0..30) {
                val x = -w * 0.1f + w * 1.2f * i / 30f
                val y = y0 + sin(x * (0.012f / (1f + row * 0.4f)) + t * (0.8f + row * 0.15f) + row) * amp
                if (i == 0) swell.moveTo(x, y) else swell.lineTo(x, y)
            }
            swellPaint.alpha = (35 + row * 18).coerceAtMost(130)
            drawLayer(canvas, swell, swellPaint, f)
        }
        if (withBoat) {
            val by = h * 0.78f + sin(t * 1.3f) * h * 0.004f * rough
            canvas.save()
            canvas.translate(boatX + shift(0.7f), by)
            canvas.rotate(sin(t * 1.1f) * 2.5f * rough)
            canvas.drawPath(jib, jibPaint)
            canvas.drawPath(sail, sailPaint)
            canvas.drawPath(mast, mastPaint)
            canvas.drawPath(hull, hullPaint)
            if (lampPaint.color != 0) canvas.drawCircle(0f, -150f * width / 1080f * 1.1f, width / 200f, lampPaint)
            canvas.restore()
        }
    }
}

/** A still lake mirroring the tree line and mountains behind it, with reeds at the near shore. */
class LakeLandscape : Landscape() {
    private val water = Water()
    private val mountains = Path(); private val treeLine = Path(); private val shore = Path(); private val reeds = Path()
    private val mountainPaint = fill(); private val treePaint = fill(); private val shorePaint = fill(); private val reedPaint = stroke()
    private val mirrorMountain = fill(); private val mirrorTree = fill()
    private var horizon = 0f
    private var shoreTop = 0f

    override fun build() {
        val w = width; val h = height.toFloat(); val unit = w / 1080f
        listOf(mountains, treeLine, shore, reeds).forEach { it.reset() }
        horizon = h * 0.66f
        shoreTop = h * 0.86f
        Ridge(w, horizon, h * 0.13f, jagged = true, seed = 30).addTo(mountains, horizon + 1f)
        val trees = Ridge(w, horizon, h * 0.008f, jagged = false, seed = 31)
        val rnd = Random(32)
        var x = trees.left
        while (x < w * 1.1f) { Trees.pine(treeLine, x, horizon + 1f, h * (0.02f + rnd.nextFloat() * 0.025f)); x += (10f + rnd.nextFloat() * 8f) * unit }
        treeLine.addRect(trees.left, horizon - h * 0.006f, w * 1.1f, horizon + 1f, Path.Direction.CW)
        water.build(-w * 0.1f, w * 1.1f, horizon, shoreTop + h * 0.02f, unit)
        Ridge(w, shoreTop + h * 0.02f, h * 0.02f, jagged = false, seed = 33).addTo(shore, h)
        x = -w * 0.05f
        while (x < w * 1.05f) {
            if (rnd.nextFloat() < 0.5f) {
                val rh = h * (0.025f + rnd.nextFloat() * 0.035f)
                reeds.moveTo(x, shoreTop + h * 0.03f)
                reeds.quadTo(x + rh * 0.1f, shoreTop + h * 0.03f - rh * 0.6f, x + rh * 0.2f, shoreTop + h * 0.03f - rh)
            }
            x += 9f * unit
        }
        reedPaint.strokeWidth = 2.5f * unit.coerceAtLeast(0.6f)
    }

    override fun applyLighting() {
        val l = lighting
        water.applyLighting(l, DEEP_LAKE, width)
        mountainPaint.color = l.distant(l.ground(0xFF6C7D93.toInt()), 0.4f)
        treePaint.color = l.distant(l.foliage(0xFF24452F.toInt()), 0.15f)
        mirrorMountain.color = mountainPaint.color; mirrorMountain.alpha = 70
        mirrorTree.color = treePaint.color; mirrorTree.alpha = 110
        shorePaint.color = l.ground(0xFF3E6A35.toInt())
        reedPaint.color = l.foliage(0xFF4E7A33.toInt())
    }

    override fun draw(canvas: Canvas) {
        drawLayer(canvas, mountains, mountainPaint, 0.05f)
        drawLayer(canvas, treeLine, treePaint, 0.2f)
        water.draw(canvas, timeMs, windKmh, shift(0.5f))
        // Mirror image of the far shore, squashed and faint.
        canvas.save()
        canvas.clipRect(0f, horizon, width.toFloat(), shoreTop + height * 0.02f)
        canvas.translate(0f, horizon)
        canvas.scale(1f, -0.7f)
        canvas.translate(0f, -horizon)
        drawLayer(canvas, mountains, mirrorMountain, 0.05f)
        drawLayer(canvas, treeLine, mirrorTree, 0.2f)
        canvas.restore()
        drawLayer(canvas, shore, shorePaint, 1f)
        val t = timeMs / 1000f
        canvas.save()
        canvas.translate(shift(1f) + sin(t * (1f + windKmh / 20f)) * (1f + windKmh / 15f), 0f)
        canvas.drawPath(reeds, reedPaint)
        canvas.restore()
    }
}

/** A river winding out of the mountains through a green valley. */
class RiverValleyLandscape : Landscape() {
    private val water = Water()
    private val mountains = Path(); private val valley = Path(); private val river = Path()
    private val leftSlope = Path(); private val rightSlope = Path(); private val trees = Path()
    private val mountainPaint = fill(); private val valleyPaint = fill(); private val slopePaint = fill(); private val treePaint = fill()

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        listOf(mountains, valley, river, leftSlope, rightSlope, trees).forEach { it.reset() }
        Ridge(width, h * 0.66f, h * 0.16f, jagged = true, seed = 40).addTo(mountains, h)
        Ridge(width, h * 0.7f, h * 0.015f, jagged = false, seed = 41).addTo(valley, h)
        val top = h * 0.69f
        val n = 40
        for (i in 0..n) {
            val f = i / n.toFloat()
            val y = top + (h - top) * f
            val half = w * (0.006f + 0.2f * f * f)
            val cx = w * 0.5f + sin(f * 7f) * w * 0.12f * f
            if (i == 0) river.moveTo(cx - half, y) else river.lineTo(cx - half, y)
        }
        for (i in n downTo 0) {
            val f = i / n.toFloat()
            val y = top + (h - top) * f
            val half = w * (0.006f + 0.2f * f * f)
            val cx = w * 0.5f + sin(f * 7f) * w * 0.12f * f
            river.lineTo(cx + half, y)
        }
        river.close()
        water.build(0f, w, top, h, unit)
        leftSlope.moveTo(-w * 0.1f, h * 0.72f)
        leftSlope.quadTo(w * 0.15f, h * 0.76f, w * 0.24f, h)
        leftSlope.lineTo(-w * 0.1f, h)
        leftSlope.close()
        rightSlope.moveTo(w * 1.1f, h * 0.7f)
        rightSlope.quadTo(w * 0.85f, h * 0.78f, w * 0.8f, h)
        rightSlope.lineTo(w * 1.1f, h)
        rightSlope.close()
        val rnd = Random(42)
        for (i in 0 until 7) Trees.pine(trees, w * (0.0f + i * 0.035f), h * (0.74f + i * 0.025f) + rnd.nextFloat() * h * 0.01f, h * (0.06f + rnd.nextFloat() * 0.03f))
        for (i in 0 until 6) Trees.pine(trees, w * (1.0f - i * 0.035f), h * (0.73f + i * 0.026f), h * (0.06f + rnd.nextFloat() * 0.03f))
    }

    override fun applyLighting() {
        val l = lighting
        water.applyLighting(l, DEEP_LAKE, width)
        mountainPaint.color = l.distant(l.ground(0xFF6A7B8F.toInt()), 0.35f)
        valleyPaint.color = l.ground(0xFF6E9C56.toInt())
        slopePaint.color = l.ground(0xFF3F6E35.toInt())
        treePaint.color = l.foliage(0xFF1F3F26.toInt())
    }

    override fun draw(canvas: Canvas) {
        drawLayer(canvas, mountains, mountainPaint, 0.1f)
        drawLayer(canvas, valley, valleyPaint, 0.5f)
        canvas.save()
        canvas.translate(shift(0.6f), 0f)
        canvas.clipPath(river)
        water.draw(canvas, timeMs, windKmh + 10f, 0f)
        canvas.restore()
        drawLayer(canvas, leftSlope, slopePaint, 1f)
        drawLayer(canvas, rightSlope, slopePaint, 1f)
        drawLayer(canvas, trees, treePaint, 1f)
    }
}

/** A waterfall pouring between two cliffs into a misty pool. */
class WaterfallLandscape : Landscape() {
    private val pool = Water()
    private val ridge = Path(); private val leftCliff = Path(); private val rightCliff = Path(); private val greenery = Path()
    private val ridgePaint = fill(); private val cliffPaint = fill(); private val greenPaint = fill()
    private val fallPaint = Paint(); private val streakPaint = stroke(); private val mistPaint = fill()
    private val sx = FloatArray(STREAKS); private val sy = FloatArray(STREAKS); private val sl = FloatArray(STREAKS); private val sv = FloatArray(STREAKS)
    private val streakLines = FloatArray(STREAKS * 4)
    private var fallLeft = 0f; private var fallRight = 0f; private var fallTop = 0f; private var poolTop = 0f

    override fun build() {
        val w = width.toFloat(); val h = height.toFloat(); val unit = w / 1080f
        listOf(ridge, leftCliff, rightCliff, greenery).forEach { it.reset() }
        Ridge(width, h * 0.6f, h * 0.12f, jagged = true, seed = 50).addTo(ridge, h)
        fallLeft = w * 0.42f; fallRight = w * 0.56f; fallTop = h * 0.6f; poolTop = h * 0.86f
        leftCliff.moveTo(-w * 0.1f, h * 0.58f)
        leftCliff.lineTo(w * 0.2f, h * 0.57f)
        leftCliff.lineTo(fallLeft + w * 0.01f, fallTop)
        leftCliff.lineTo(fallLeft, h * 0.7f)
        leftCliff.lineTo(fallLeft + w * 0.015f, poolTop)
        leftCliff.lineTo(-w * 0.1f, poolTop)
        leftCliff.close()
        rightCliff.moveTo(w * 1.1f, h * 0.56f)
        rightCliff.lineTo(w * 0.8f, h * 0.565f)
        rightCliff.lineTo(fallRight - w * 0.01f, fallTop)
        rightCliff.lineTo(fallRight, h * 0.72f)
        rightCliff.lineTo(fallRight - w * 0.01f, poolTop)
        rightCliff.lineTo(w * 1.1f, poolTop)
        rightCliff.close()
        val rnd = Random(51)
        var x = -w * 0.05f
        while (x < fallLeft) { Trees.round(greenery, x, h * 0.58f + (x / fallLeft) * h * 0.02f, h * (0.035f + rnd.nextFloat() * 0.025f)); x += 38f * unit }
        x = fallRight + w * 0.02f
        while (x < w * 1.05f) { Trees.round(greenery, x, h * 0.575f, h * (0.035f + rnd.nextFloat() * 0.025f)); x += 38f * unit }
        // Bushes on the cliff faces.
        for (i in 0 until 6) Trees.round(greenery, w * (0.05f + i * 0.06f), h * (0.7f + rnd.nextFloat() * 0.12f), h * 0.03f)
        for (i in 0 until 6) Trees.round(greenery, w * (0.62f + i * 0.06f), h * (0.7f + rnd.nextFloat() * 0.12f), h * 0.03f)
        pool.build(-w * 0.1f, w * 1.1f, poolTop, h, unit)
        for (i in 0 until STREAKS) {
            sx[i] = fallLeft + rnd.nextFloat() * (fallRight - fallLeft)
            sy[i] = fallTop + rnd.nextFloat() * (poolTop - fallTop)
            sl[i] = h * (0.02f + rnd.nextFloat() * 0.05f)
            sv[i] = h * (0.5f + rnd.nextFloat() * 0.4f)
        }
        streakPaint.strokeWidth = 3f * unit.coerceAtLeast(0.6f)
        mistPaint.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xAAFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }

    override fun applyLighting() {
        val l = lighting
        pool.applyLighting(l, DEEP_LAKE, width)
        ridgePaint.color = l.distant(l.ground(0xFF6D7E92.toInt()), 0.35f)
        cliffPaint.color = l.ground(0xFF5C5248.toInt())
        greenPaint.color = l.foliage(0xFF2F5E30.toInt())
        fallPaint.shader = LinearGradient(
            0f, fallTop, 0f, poolTop,
            l.shade(0xFFE8F4FA.toInt()), l.shade(0xFFB9D7E6.toInt()), Shader.TileMode.CLAMP,
        )
        streakPaint.color = l.shade(0xFFFFFFFF.toInt())
    }

    override fun update(dtMs: Long) {
        super.update(dtMs)
        val dt = dtMs / 1000f
        for (i in 0 until STREAKS) {
            sy[i] += sv[i] * dt
            if (sy[i] - sl[i] > poolTop) sy[i] = fallTop
        }
    }

    override fun draw(canvas: Canvas) {
        drawLayer(canvas, ridge, ridgePaint, 0.1f)
        pool.draw(canvas, timeMs, windKmh + 15f, shift(0.9f))
        val s = shift(0.8f)
        canvas.save()
        canvas.translate(s, 0f)
        canvas.drawRect(fallLeft, fallTop, fallRight, poolTop, fallPaint)
        var n = 0
        for (i in 0 until STREAKS) {
            streakLines[n++] = sx[i]; streakLines[n++] = sy[i] - sl[i]
            streakLines[n++] = sx[i]; streakLines[n++] = minOf(sy[i], poolTop)
        }
        streakPaint.alpha = 150
        canvas.drawLines(streakLines, 0, n, streakPaint)
        // Spray where the water hits the pool.
        val t = timeMs / 1000f
        val mid = (fallLeft + fallRight) / 2f
        for (k in 0 until 5) {
            val r = (fallRight - fallLeft) * (0.6f + 0.25f * sin(t * 1.5f + k))
            canvas.save()
            canvas.translate(mid + (k - 2) * r * 0.45f, poolTop - r * 0.15f)
            canvas.scale(r, r * 0.5f)
            canvas.drawCircle(0f, 0f, 1f, mistPaint)
            canvas.restore()
        }
        canvas.restore()
        drawLayer(canvas, leftCliff, cliffPaint, 0.8f)
        drawLayer(canvas, rightCliff, cliffPaint, 0.8f)
        drawLayer(canvas, greenery, greenPaint, 0.8f)
    }

    private companion object {
        const val STREAKS = 60
    }
}
