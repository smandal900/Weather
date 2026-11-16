package com.skymood.wallpaper.landscape

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.skymood.wallpaper.scenes.SkyGradient
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * Meadow painting for the Grass field: the lawn is built the way a real one reads from a distance.
 * Colour drifts slowly in large flat patches, blades grow from tufts and shrink towards the horizon,
 * far tufts melt into the sky's haze, and near ones are lit on the sun's side with a dark root.
 * Everything here runs on the painter thread, never per frame; the swaying foreground is [GrassBlades].
 */

/** Smooth 2-D value noise in 0..1, for colour that drifts slowly across the field. */
internal fun valueNoise(x: Float, y: Float, seed: Int): Float {
    val ix = floor(x).toInt(); val iy = floor(y).toInt()
    val fx = x - ix; val fy = y - iy
    val sx = fx * fx * (3f - 2f * fx); val sy = fy * fy * (3f - 2f * fy)
    val a = lattice(ix, iy, seed); val b = lattice(ix + 1, iy, seed)
    val c = lattice(ix, iy + 1, seed); val d = lattice(ix + 1, iy + 1, seed)
    val top = a + (b - a) * sx
    val bottom = c + (d - c) * sx
    return top + (bottom - top) * sy
}

private fun lattice(ix: Int, iy: Int, seed: Int): Float {
    var n = ix * 374761393 + iy * 668265263 + seed * 1442695041
    n = (n xor (n ushr 13)) * 1274126177
    n = n xor (n ushr 16)
    return (n and 0xFFFF) / 65535f
}

/** Highest point of [ridge] across the screen, so depth can be measured down from the horizon. */
private fun PaintKit.horizonOf(ridge: Ridge): Float {
    var minY = h
    var x = ridge.left
    while (x < w * 1.1f) { minY = min(minY, ridge.yAt(x)); x += 12f * unit }
    return minY
}

/**
 * Big soft colour patches lying flat on the ground (wide and thin, as perspective squashes them),
 * larger towards the viewer. They give the lawn the uneven lush / sun-dried / shadowed tones of a
 * real meadow instead of one flat green.
 */
internal fun PaintKit.groundPatches(
    c: Canvas, ridge: Ridge, bottom: Float, rnd: Random, count: Int, palette: IntArray, strength: Int,
) {
    val body = Path(); ridge.addTo(body, bottom)
    c.save()
    c.clipPath(body)
    val horizon = horizonOf(ridge)
    repeat(count) {
        val d = rnd.nextFloat()
        val px = -margin + rnd.nextFloat() * (w + margin * 2)
        val py = horizon + (bottom - horizon) * d
        val rx = w * (0.05f + 0.3f * d) * (0.6f + rnd.nextFloat() * 0.8f)
        val ry = rx * (0.1f + 0.1f * rnd.nextFloat())
        val col = palette[rnd.nextInt(palette.size)]
        val a = (strength * (0.5f + rnd.nextFloat() * 0.5f)).toInt()
        c.save()
        c.translate(px, py)
        c.scale(1f, ry / rx)
        shader = RadialGradient(0f, 0f, rx, alpha(col, a), alpha(col, 0), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, rx, fill)
        c.restore()
    }
    shader = null
    c.restore()
}

/** One tapered, curved blade as a closed path; [t0] > 0 gives only the part from there to the tip. */
private fun bladePath(p: Path, x: Float, y: Float, hgt: Float, wid: Float, lean: Float, bow: Float, t0: Float) {
    val cx = x + lean * 0.3f + bow; val cy = y - hgt * 0.55f
    val tx = x + lean; val ty = y - hgt
    val ax = x + (cx - x) * t0; val ay = y + (cy - y) * t0
    val bx = cx + (tx - cx) * t0; val by = cy + (ty - cy) * t0
    val sx = ax + (bx - ax) * t0; val sy = ay + (by - ay) * t0
    val half = wid * (1f - t0 * 0.85f) * 0.5f
    p.rewind()
    p.moveTo(sx - half, sy)
    p.quadTo(bx - half * 0.5f, by, tx, ty)
    p.quadTo(bx + half * 0.5f, by, sx + half, sy)
    p.close()
}

/**
 * Covers the ground with tufts, far to near so that near ones overlap far ones. A tuft is a small
 * fan of tapered blades; its size follows the distance below the horizon. Far tufts are blended
 * towards [mist], near ones get a dark root and a lighter upper half on every blade.
 */
internal fun PaintKit.grassCarpet(
    c: Canvas, ridge: Ridge, bottom: Float, rnd: Random,
    deep: Int, lush: Int, dry: Int, lit: Int, mist: Int, density: Float,
) {
    val horizon = horizonOf(ridge)
    val span = max(1f, bottom - horizon)
    val keep = if (l.snow) 0.4f else 1f
    val p = Path()
    var y = horizon
    while (y < bottom + h * 0.01f) {
        val d = ((y - horizon) / span).coerceIn(0f, 1f)
        val s = h * (0.0042f + 0.042f * d.pow(1.2f))
        var x = -margin + rnd.nextFloat() * s
        while (x < w + margin) {
            if (y > ridge.yAt(x) + 1f && rnd.nextFloat() < keep) drawTuft(c, p, x, y, s, d, rnd, deep, lush, dry, lit, mist)
            x += s * (0.5f + 0.5f * rnd.nextFloat()) / density
        }
        y += s * 0.24f / density
    }
}

private fun PaintKit.drawTuft(
    c: Canvas, p: Path, x: Float, y: Float, s: Float, d: Float, rnd: Random,
    deep: Int, lush: Int, dry: Int, lit: Int, mist: Int,
) {
    val dryness = ((valueNoise(x / (w * 0.09f), y / (h * 0.035f), 7) - 0.52f) * 2.4f).coerceIn(0f, 0.75f)
    val base = mix(lush, dry, dryness)
    val blades = 4 + (d * 4f).toInt() + rnd.nextInt(3)
    val lean0 = (rnd.nextFloat() - 0.5f) * s * 0.5f
    val haze = (1f - d) * (1f - d) * 0.24f
    val width = max(1.2f * unit, s * 0.075f)
    if (d > 0.3f) {
        fill.color = alpha(deep, 70)
        c.drawOval(x - s * 0.5f, y - s * 0.07f, x + s * 0.5f, y + s * 0.1f, fill)
    }
    for (b in 0 until blades) {
        val spread = (b / (blades - 1f) - 0.5f) * 2f
        val bx = x + spread * s * 0.28f
        val lean = lean0 + spread * s * 0.45f + (rnd.nextFloat() - 0.5f) * s * 0.25f
        val bh = s * (0.55f + 0.65f * rnd.nextFloat()) * (1f - 0.25f * abs(spread))
        val facing = if (lean * sun > 0f) 0.18f else -0.05f
        val t = (0.18f + 0.62f * rnd.nextFloat() + facing).coerceIn(0f, 1f)
        val body = if (t < 0.5f) mix(deep, base, t * 2f) else mix(base, lit, (t - 0.5f) * 2f)
        val col = mix(body, mist, haze)
        val bow = (rnd.nextFloat() - 0.5f) * s * 0.2f
        fill.color = col
        bladePath(p, bx, y, bh, width, lean, bow, 0f)
        c.drawPath(p, fill)
        if (d > 0.4f) {
            fill.color = mix(col, lit, 0.35f)
            bladePath(p, bx, y, bh, width, lean, bow, 0.5f)
            c.drawPath(p, fill)
        }
    }
}

/**
 * Clusters of wildflowers: daisies, buttercups, clover, vetch, poppies and cornflowers. Far ones
 * are single dots, near ones get stems and petals. Closed at night and gone under snow.
 */
internal fun PaintKit.wildflowers(c: Canvas, ridge: Ridge, bottom: Float, rnd: Random, patches: Int) {
    if (l.snow || night) return
    val horizon = horizonOf(ridge)
    val stem = l.foliage(0xFF2F6A2A.toInt())
    // petal, centre, how often it turns up
    val petals = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFD21F.toInt(), 0xFFB07CE0.toInt(), 0xFFE98BB5.toInt(), 0xFFE0402E.toInt(), 0xFF5B7FE0.toInt())
    val centres = intArrayOf(0xFFFFC93C.toInt(), 0xFFE8A200.toInt(), 0xFF7A4CB0.toInt(), 0xFFB04A78.toInt(), 0xFF2A1A12.toInt(), 0xFF2E4DB0.toInt())
    val weights = intArrayOf(3, 3, 2, 1, 1, 1)
    val total = weights.sum()
    repeat(patches) {
        val cx = -margin + rnd.nextFloat() * (w + margin * 2)
        val d = 0.08f + rnd.nextFloat() * 0.9f
        val cy = horizon + (bottom - horizon) * d
        var pick = rnd.nextInt(total); var kind = 0
        while (pick >= weights[kind]) { pick -= weights[kind]; kind++ }
        val petal = l.ground(petals[kind]); val centre = l.ground(centres[kind])
        val radius = w * (0.012f + 0.05f * d)
        val n = (6 + 24 * (0.4f + 0.6f * d)).toInt()
        val head = (1.4f + 5.5f * d) * unit
        for (i in 0 until n) {
            val fx = cx + (rnd.nextFloat() + rnd.nextFloat() - 1f) * radius
            val fy = cy + (rnd.nextFloat() + rnd.nextFloat() - 1f) * radius * 0.35f
            if (fy < ridge.yAt(fx) + 2f) continue
            val stalk = h * (0.004f + 0.02f * d) * (0.7f + rnd.nextFloat() * 0.6f)
            val r = head * (0.75f + rnd.nextFloat() * 0.5f)
            if (r > 2.4f * unit) {
                line.color = stem
                line.strokeWidth = max(1f, r * 0.25f)
                c.drawLine(fx, fy, fx + (rnd.nextFloat() - 0.5f) * stalk * 0.3f, fy - stalk, line)
            }
            val hy = fy - stalk
            when {
                r < 2.4f * unit -> { fill.color = petal; c.drawCircle(fx, hy, r, fill) }
                kind == 2 || kind == 5 -> {
                    // A little spike of florets.
                    fill.color = petal
                    for (k in 0 until 3) c.drawCircle(fx, hy + k * r * 0.8f, r * (0.8f - 0.12f * k), fill)
                }
                else -> {
                    fill.color = petal
                    for (k in 0 until 6) {
                        val a = k * 1.0472f + rnd.nextFloat() * 0.3f
                        c.drawCircle(fx + kotlin.math.cos(a) * r * 0.7f, hy + sin(a) * r * 0.5f, r * 0.5f, fill)
                    }
                    fill.color = centre
                    c.drawCircle(fx, hy, r * 0.36f, fill)
                }
            }
        }
    }
}

/**
 * The swaying part of the foreground meadow: a few hundred narrow blades and seed heads, each a
 * five-row ribbon drawn in one [Canvas.drawVertices] call. Blades taper to a point, run from dark at
 * the root to pale at the tip and are shaded across their width, so they read as leaves rather
 * than strokes. A swell rolls across the field with the wind and every blade flutters on top of it.
 */
internal class GrassBlades(private val count: Int) {
    private val rows = 5
    private val fx = FloatArray(count)
    private val bx = FloatArray(count); private val by = FloatArray(count)
    private val bh = FloatArray(count); private val bw = FloatArray(count)
    private val head = FloatArray(count)
    private val phase = FloatArray(count); private val lean = FloatArray(count)
    private val tone = FloatArray(count); private val depth = FloatArray(count)
    private val seeded = BooleanArray(count)
    private val verts = FloatArray(count * rows * 4)
    private val colors = IntArray(count * rows * 2)
    private val indices = ShortArray(count * (rows - 1) * 6)
    private val paint = Paint()

    private val leafU = floatArrayOf(0f, 0.3f, 0.6f, 0.85f, 1f)
    private val leafW = floatArrayOf(1f, 0.92f, 0.68f, 0.34f, 0f)
    private val seedU = floatArrayOf(0f, 0.55f, 0.7f, 0.88f, 1f)
    private val leafBow = FloatArray(rows) { leafU[it].pow(1.7f) }
    private val seedBow = FloatArray(rows) { seedU[it].pow(2.4f) }

    init {
        val r = Random(13)
        val ds = FloatArray(count) { r.nextFloat().pow(0.85f) }
        ds.sort()
        for (i in 0 until count) {
            depth[i] = ds[i]
            fx[i] = r.nextFloat()
            phase[i] = r.nextFloat() * 6.28f
            lean[i] = (r.nextFloat() - 0.5f) * 0.24f
            tone[i] = r.nextFloat()
            seeded[i] = ds[i] > 0.35f && r.nextFloat() < 0.14f
        }
        var n = 0
        for (i in 0 until count) {
            for (row in 0 until rows - 1) {
                val v = (i * rows + row) * 2
                indices[n++] = v.toShort(); indices[n++] = (v + 1).toShort(); indices[n++] = (v + 2).toShort()
                indices[n++] = (v + 1).toShort(); indices[n++] = (v + 3).toShort(); indices[n++] = (v + 2).toShort()
            }
        }
    }

    /** Places the blades for a screen of [w] by [h]; their roots lie below the horizon of the lawn. */
    fun layout(w: Float, h: Float, unit: Float) {
        val r = Random(29)
        for (i in 0 until count) {
            val d = depth[i]
            bx[i] = -w * 0.05f + fx[i] * w * 1.1f
            by[i] = h * (0.8f + 0.2f * d)
            if (seeded[i]) {
                bh[i] = h * (0.04f + 0.05f * d) * (0.8f + r.nextFloat() * 0.4f)
                bw[i] = (1.2f + 1.2f * d) * unit
                head[i] = (3.5f + 4f * d) * unit
            } else {
                bh[i] = h * (0.01f + 0.062f * d.pow(1.5f)) * (0.75f + r.nextFloat() * 0.5f)
                bw[i] = (1.6f + 8.5f * d) * unit * (0.8f + r.nextFloat() * 0.5f)
            }
        }
    }

    /**
     * Blade colours: [base] at the root, [lush] or [dry] through the body, [tip] at the point; seed
     * heads run [stalk] to [gold]. The side of each blade facing the sun is lighter.
     */
    fun setColors(base: Int, lush: Int, dry: Int, tip: Int, stalk: Int, gold: Int, sunOnLeft: Boolean) {
        val cols = IntArray(rows)
        for (i in 0 until count) {
            if (!seeded[i]) {
                val mid = SkyGradient.blend(lush, dry, tone[i] * 0.5f)
                cols[0] = SkyGradient.blend(base, mid, 0.15f)
                cols[1] = SkyGradient.blend(base, mid, 0.6f)
                cols[2] = mid
                cols[3] = SkyGradient.blend(mid, tip, 0.55f)
                cols[4] = (tip and 0xFFFFFF) or (0xD0 shl 24)
            } else {
                cols[0] = SkyGradient.blend(base, stalk, 0.5f)
                cols[1] = stalk
                cols[2] = gold
                cols[3] = SkyGradient.blend(gold, tip, 0.35f)
                cols[4] = (cols[3] and 0xFFFFFF) or (0xC0 shl 24)
            }
            for (row in 0 until rows) {
                val shadowed = SkyGradient.blend(cols[row], 0xFF000000.toInt(), 0.14f)
                val o = (i * rows + row) * 2
                colors[o] = if (sunOnLeft) cols[row] else shadowed
                colors[o + 1] = if (sunOnLeft) shadowed else cols[row]
            }
        }
    }

    /**
     * Draws every blade. [shiftNear] and [shiftFar] are the parallax shifts of the nearest and the
     * farthest blades, so the field turns as one with the ground layer behind it.
     */
    fun draw(canvas: Canvas, t: Float, windKmh: Float, shiftNear: Float, shiftFar: Float) {
        val strength = (0.16f + windKmh / 34f).coerceAtMost(1.1f)
        val speed = 0.8f + windKmh / 16f
        var v = 0
        for (i in 0 until count) {
            val d = depth[i]
            val x0 = bx[i] + shiftFar + (shiftNear - shiftFar) * d
            val y0 = by[i]
            val hh = bh[i]
            val swell = sin(bx[i] * 0.0058f - t * speed + y0 * 0.0021f)
            val flutter = sin(t * speed * 2.9f + phase[i])
            val tip = hh * (lean[i] + strength * (0.22f + 0.275f * (1f + swell)) + 0.07f * strength * flutter)
            val bend = min(abs(tip) / hh, 0.9f)
            val rise = 1f - 0.45f * bend * bend
            val seed = seeded[i]
            val half = bw[i] * 0.5f
            for (row in 0 until rows) {
                val u = if (seed) seedU[row] else leafU[row]
                val bow = if (seed) seedBow[row] else leafBow[row]
                val cx = x0 + tip * bow
                val cy = y0 - hh * u * rise
                val hw = if (seed) {
                    when (row) { 0 -> half; 1 -> half * 0.85f; 2 -> head[i] * 0.5f; 3 -> head[i] * 0.4f; else -> 0f }
                } else half * leafW[row]
                verts[v++] = cx - hw; verts[v++] = cy
                verts[v++] = cx + hw; verts[v++] = cy
            }
        }
        // The count is of floats in `verts` (x and y for each of the vertices), not of vertices.
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES, verts.size, verts, 0, null, 0, colors, 0, indices, 0, indices.size, paint,
        )
    }
}
