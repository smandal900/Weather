package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A coconut palm built from separate parts, so the wind can move it the way it moves a real one:
 * the trunk bends a little, the whole crown rocks on top of it and every frond flutters by itself.
 *
 * The trunk and a handful of frond poses are painted once into sprites. A [PalmCrown] says which
 * poses make up one tree's crown, and [PalmDrawer] assembles and animates them.
 */

/** How one frond bends: heading at the crown, how far it curls down, relative length, age (0 fresh .. 1 dying) and how wide its leaflets spread. */
internal class FrondPose(val start: Float, val bend: Float, val len: Float, val age: Float, val open: Float)

internal object PalmGeometry {
    // Frond sprite: pivot at (PIVOT_X, PIVOT_Y) of the frond length, in a FROND_W x FROND_H box.
    const val FROND_W = 1.4f
    const val FROND_H = 1.9f
    const val PIVOT_X = 0.1f
    const val PIVOT_Y = 0.95f

    // Trunk sprite: base bottom-centre, crown at the top, in terms of the trunk height.
    const val TRUNK_W = 0.9f
    const val PAD_TOP = 0.06f
    const val PAD_BOTTOM = 0.03f

    val POSES = arrayOf(
        FrondPose(-82f, 30f, 0.62f, 0.0f, 0.45f),  // young spear, still folded
        FrondPose(-58f, 100f, 0.98f, 0.1f, 1f),    // high arch
        FrondPose(-28f, 92f, 1.0f, 0.2f, 1f),      // mid arch
        FrondPose(2f, 78f, 0.98f, 0.45f, 1f),      // drooping
        FrondPose(32f, 48f, 0.8f, 0.85f, 0.9f),    // old, hanging, going yellow
    )
    const val DEG = 0.017453292f
}

/** The painted parts of one palm size: a few trunk shapes and every frond pose (in [variants] versions). */
internal class PalmSprites(val trunks: List<Bitmap>, val leans: FloatArray, private val fronds: List<Bitmap>) {
    fun frond(pose: Int, variant: Int): Bitmap =
        fronds[(variant % (fronds.size / PalmGeometry.POSES.size)) * PalmGeometry.POSES.size + pose]

    companion object {
        /** Paints the parts for a palm [th] pixels tall; every bitmap is also put in [out] under a [prefix] name. */
        fun paint(
            k: PaintKit, prefix: String, th: Float, leans: FloatArray, variants: Int, frondLen: Float,
            seed: Int, out: MutableMap<String, Bitmap>,
        ): PalmSprites? {
            val trunks = ArrayList<Bitmap>()
            for (i in leans.indices) {
                val b = paintTrunk(k, th, leans[i] * th, seed + i) ?: return null
                out["$prefix.t$i"] = b; trunks.add(b)
            }
            val fronds = ArrayList<Bitmap>()
            for (v in 0 until variants) for (pose in PalmGeometry.POSES.indices) {
                val b = paintFrond(k, th * frondLen, pose, seed * 7 + v * 31 + pose) ?: return null
                out["$prefix.f${v}_$pose"] = b; fronds.add(b)
            }
            return PalmSprites(trunks, leans, fronds)
        }

        /** Rebuilds the parts from the sprite map [paint] filled, or null while it is incomplete. */
        fun from(map: Map<String, Bitmap>, prefix: String, leans: FloatArray, variants: Int): PalmSprites? {
            val trunks = ArrayList<Bitmap>()
            for (i in leans.indices) trunks.add(map["$prefix.t$i"] ?: return null)
            val fronds = ArrayList<Bitmap>()
            for (v in 0 until variants) for (pose in PalmGeometry.POSES.indices) {
                fronds.add(map["$prefix.f${v}_$pose"] ?: return null)
            }
            return PalmSprites(trunks, leans, fronds)
        }
    }
}

/** Which fronds make up one tree's crown; each tree gets its own random tilts, sizes and flutter phases. */
internal class PalmCrown(seed: Int, variants: Int) {
    val n = RECIPE.size
    val pose = IntArray(n)
    val variant = IntArray(n)
    val mirror = BooleanArray(n)
    val back = BooleanArray(n)
    val rot = FloatArray(n)
    val scale = FloatArray(n)
    val phase = FloatArray(n)

    init {
        val rnd = Random(seed)
        for (i in 0 until n) {
            val r = RECIPE[i]
            pose[i] = r[0]; mirror[i] = r[1] == 1; back[i] = r[2] == 1
            rot[i] = r[3] + (rnd.nextFloat() - 0.5f) * 12f
            scale[i] = 0.9f + 0.16f * rnd.nextFloat()
            phase[i] = rnd.nextFloat() * 6.28f
            variant[i] = rnd.nextInt(variants.coerceAtLeast(1))
        }
    }

    private companion object {
        // pose, mirrored, behind the trunk top, extra tilt in degrees
        val RECIPE = arrayOf(
            intArrayOf(1, 0, 1, 0), intArrayOf(1, 1, 1, 0), intArrayOf(3, 0, 1, 0), intArrayOf(3, 1, 1, 0),
            intArrayOf(0, 0, 0, 4), intArrayOf(0, 1, 0, -6),
            intArrayOf(2, 0, 0, 0), intArrayOf(2, 1, 0, 0),
            intArrayOf(4, 0, 0, 0), intArrayOf(4, 1, 0, 0),
            intArrayOf(1, 0, 0, 14), intArrayOf(2, 1, 0, -16),
        )
    }
}

/** Assembles and animates palms. Holds only paints, so a drawer can be shared between frames. */
internal class PalmDrawer {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    // Fronds behind the trunk top sit in the crown's shade.
    private val backPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = LightingColorFilter(0xFFB0B0B0.toInt(), 0) }

    /** Draws one palm with its trunk base at ([x], [y]). With [t] and [windKmh] at 0 the tree stands still. */
    fun draw(
        canvas: Canvas, s: PalmSprites, crown: PalmCrown, trunk: Int, x: Float, y: Float,
        scale: Float, t: Float, windKmh: Float, phase: Float,
    ) {
        val g = PalmGeometry
        val tr = s.trunks[trunk]
        val th = tr.height / (1f + g.PAD_TOP + g.PAD_BOTTOM)
        val baseX = tr.width * 0.5f
        val baseY = tr.height - th * g.PAD_BOTTOM
        val crownX = baseX + s.leans[trunk] * th
        val crownY = th * g.PAD_TOP
        val gust = (windKmh / 14f).coerceAtMost(4f)

        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        canvas.rotate(sin(t * 0.7f + phase) * (0.25f + gust * 0.25f))
        canvas.drawBitmap(tr, -baseX, -baseY, paint)
        canvas.translate(crownX - baseX, crownY - baseY)
        canvas.rotate(sin(t * 1.15f + phase * 1.7f) * (0.6f + gust * 0.5f))
        for (pass in 0..1) {
            for (i in 0 until crown.n) {
                if (crown.back[i] != (pass == 0)) continue
                val f = s.frond(crown.pose[i], crown.variant[i])
                val flutter = sin(t * (1.6f + windKmh / 22f) + crown.phase[i]) * (1.1f + gust * 1.4f)
                canvas.save()
                if (crown.mirror[i]) canvas.scale(-1f, 1f)
                canvas.rotate(crown.rot[i] + flutter)
                canvas.scale(crown.scale[i], crown.scale[i])
                canvas.drawBitmap(
                    f, -f.width * (g.PIVOT_X / g.FROND_W), -f.height * (g.PIVOT_Y / g.FROND_H),
                    if (pass == 0) backPaint else paint,
                )
                canvas.restore()
            }
        }
        canvas.restore()
    }
}

/**
 * A palm trunk: a curved, ringed column that swells at the base and thins towards the crown,
 * shaded across its width (lit on the sun's side), with a crown boot and a cluster of coconuts at
 * the top. [lean] is how far the crown sits to the right of the base, in pixels.
 */
internal fun paintTrunk(k: PaintKit, th: Float, lean: Float, seed: Int): Bitmap? {
    val g = PalmGeometry
    val bw = (th * g.TRUNK_W).toInt()
    val bh = (th * (1f + g.PAD_TOP + g.PAD_BOTTOM)).toInt()
    val (c, bmp) = k.newSprite(bw, bh) ?: return null
    val l = k.l
    val rnd = Random(seed)
    val bx = bw * 0.5f
    val by = bh - th * g.PAD_BOTTOM
    val cx = bx + lean
    val cy = th * g.PAD_TOP
    val c1x = bx + lean * 0.08f
    val c1y = by - th * 0.55f
    k.shader = null

    val lit = l.shade(0xFFB8AA90.toInt())
    val mid = l.shade(0xFF8C7E68.toInt())
    val dark = l.shade(0xFF4C4236.toInt())

    val n = 72
    val w0 = th * 0.03f
    val px = FloatArray(n + 1); val py = FloatArray(n + 1)
    val rx = FloatArray(n + 1); val ry = FloatArray(n + 1)
    val hw = FloatArray(n + 1)
    for (i in 0..n) {
        val t = i / n.toFloat(); val u = 1f - t
        px[i] = u * u * bx + 2f * u * t * c1x + t * t * cx
        py[i] = u * u * by + 2f * u * t * c1y + t * t * cy
        var tx = 2f * u * (c1x - bx) + 2f * t * (cx - c1x)
        var ty = 2f * u * (c1y - by) + 2f * t * (cy - c1y)
        val tl = sqrt(tx * tx + ty * ty).coerceAtLeast(0.001f)
        tx /= tl; ty /= tl
        rx[i] = -ty; ry[i] = tx                    // points to the right of the trunk
        hw[i] = w0 * (1f + 0.85f * exp(-t * 8f)) * (1.12f - 0.22f * t)
    }

    // The column, as bands of shade across its width.
    val bands = 6
    val quad = Path()
    for (i in 0 until n) {
        for (b in 0 until bands) {
            val s0 = -1f + 2f * b / bands
            val s1 = (-1f + 2f * (b + 1) / bands) + (if (b < bands - 1) 0.05f else 0f)
            val sm = (s0 + s1) * 0.5f
            val light = if (k.sunOnLeft) 0.5f - 0.5f * sm else 0.5f + 0.5f * sm
            val col = if (light > 0.55f) k.mix(mid, lit, (light - 0.55f) / 0.45f) else k.mix(dark, mid, light / 0.55f)
            quad.rewind()
            quad.moveTo(px[i] + rx[i] * hw[i] * s0, py[i] + ry[i] * hw[i] * s0)
            quad.lineTo(px[i] + rx[i] * hw[i] * s1, py[i] + ry[i] * hw[i] * s1)
            quad.lineTo(px[i + 1] + rx[i + 1] * hw[i + 1] * s1, py[i + 1] + ry[i + 1] * hw[i + 1] * s1 - 0.7f)
            quad.lineTo(px[i + 1] + rx[i + 1] * hw[i + 1] * s0, py[i + 1] + ry[i + 1] * hw[i + 1] * s0 - 0.7f)
            quad.close()
            k.fill.color = k.jitter(col, rnd, 0.06f)
            c.drawPath(quad, k.fill)
        }
    }

    // Leaf-scar rings, each a shallow curve across the trunk with a lighter lip under it.
    var acc = 0f
    val spacing = th * 0.012f
    val ringW = max(1f, th * 0.0026f)
    for (i in 1..n) {
        acc += hypot(px[i] - px[i - 1], py[i] - py[i - 1])
        if (acc < spacing) continue
        acc = 0f
        val w = hw[i] * 0.97f
        for (pass in 0..1) {
            k.line.strokeWidth = ringW
            k.line.color = if (pass == 0) k.alpha(dark, 130) else k.alpha(lit, 55)
            val dy = if (pass == 0) 0f else ringW * 1.3f
            var ax = px[i] - rx[i] * w; var ay = py[i] - ry[i] * w + dy
            for (s in 1..4) {
                val f = s / 4f
                val bx2 = px[i] + rx[i] * w * (2f * f - 1f)
                val by2 = py[i] + ry[i] * w * (2f * f - 1f) + dy + w * 0.3f * sin(f * PI.toFloat())
                c.drawLine(ax, ay, bx2, by2, k.line)
                ax = bx2; ay = by2
            }
        }
    }
    // Fibres running along the bark.
    k.line.strokeWidth = max(1f, th * 0.0016f)
    repeat(70) {
        val i = 2 + rnd.nextInt(n - 3)
        val s = (rnd.nextFloat() * 2f - 1f) * 0.85f
        val x0 = px[i] + rx[i] * hw[i] * s; val y0 = py[i] + ry[i] * hw[i] * s
        k.line.color = k.alpha(if (rnd.nextBoolean()) dark else lit, 50)
        c.drawLine(x0, y0, px[i + 1] + rx[i + 1] * hw[i + 1] * s, py[i + 1] + ry[i + 1] * hw[i + 1] * s, k.line)
    }

    // Flared roots, and a soft contact shadow that also hides the cut edge at the foot.
    val r0 = hw[0]
    k.fill.color = k.alpha(0xFF000000.toInt(), 55)
    c.drawOval(bx - r0 * 2.5f, by - r0 * 0.3f, bx + r0 * 2.5f, by + r0 * 0.35f, k.fill)
    k.fill.color = k.darken(mid, 0.15f)
    for (side in intArrayOf(-1, 1)) {
        quad.rewind()
        quad.moveTo(bx + side * r0 * 0.6f, by - r0 * 1.4f)
        quad.quadTo(bx + side * r0 * 1.5f, by - r0 * 0.3f, bx + side * r0 * 2.2f, by + r0 * 0.1f)
        quad.lineTo(bx + side * r0 * 0.2f, by + r0 * 0.1f)
        quad.close()
        c.drawPath(quad, k.fill)
    }

    // Crown boot and coconuts.
    val r1 = th * 0.032f
    k.fill.color = k.darken(l.foliage(0xFF5C5A2A.toInt()), 0.2f)
    c.drawOval(cx - r1, cy - r1 * 0.5f, cx + r1, cy + r1 * 1.2f, k.fill)
    val nutCols = intArrayOf(l.foliage(0xFF6C9A2E.toInt()), l.foliage(0xFFB3A93F.toInt()), l.ground(0xFF6A4D2B.toInt()))
    val nr = th * 0.0165f
    repeat(9) {
        val nx = cx + (rnd.nextFloat() - 0.5f) * th * 0.09f
        val ny = cy + r1 * 0.5f + rnd.nextFloat() * th * 0.04f
        val base = nutCols[rnd.nextInt(nutCols.size)]
        k.fill.color = k.darken(base, 0.4f)
        c.drawCircle(nx, ny, nr, k.fill)
        k.fill.color = k.jitter(base, rnd, 0.1f)
        c.drawCircle(nx - nr * 0.12f, ny - nr * 0.12f, nr * 0.84f, k.fill)
        k.fill.color = k.alpha(k.lighten(base, 0.45f), 150)
        c.drawCircle(nx - nr * 0.35f, ny - nr * 0.35f, nr * 0.32f, k.fill)
    }
    return bmp
}

/**
 * One palm frond, pointing right from its pivot: a rachis that arches then curls under its own
 * weight, with hundreds of tapering leaflets hanging off both sides. The upper leaflets are
 * lighter, the lower darker, the oldest fronds yellow towards the tip; leaflet length, angle and
 * spacing are all a little irregular so the frond never reads as a comb.
 */
internal fun paintFrond(k: PaintKit, len: Float, pose: Int, seed: Int): Bitmap? {
    val g = PalmGeometry
    val bw = (len * g.FROND_W).toInt()
    val bh = (len * g.FROND_H).toInt()
    val (c, bmp) = k.newSprite(bw, bh) ?: return null
    val l = k.l
    val rnd = Random(seed)
    val p = g.POSES[pose]
    k.shader = null

    val steps = 44
    val xs = FloatArray(steps + 1); val ys = FloatArray(steps + 1); val hs = FloatArray(steps + 1)
    xs[0] = len * g.PIVOT_X; ys[0] = len * g.PIVOT_Y
    val seg = len * p.len / steps
    for (i in 1..steps) {
        val t = (i - 0.5f) / steps
        val hd = (p.start + p.bend * t.pow(1.25f)) * g.DEG
        hs[i] = hd
        xs[i] = xs[i - 1] + cos(hd) * seg
        ys[i] = ys[i - 1] + sin(hd) * seg
    }
    hs[0] = hs[1]

    val fresh = l.foliage(0xFF86C040.toInt())
    val green = l.foliage(0xFF45962E.toInt())
    val deepG = l.foliage(0xFF1B5427.toInt())
    val old = l.ground(0xFFBBA23C.toInt())
    val leafMax = len * 0.30f * (0.55f + 0.45f * p.open)
    val spread = 28f + 36f * p.open
    val path = Path()

    fun leaflets(side: Int) {
        for (i in 3..steps) {
            if (rnd.nextFloat() < 0.04f) continue
            val t = i / steps.toFloat()
            // Longest about a third of the way out, tapering to the tip.
            val env = ((t.pow(0.45f) * (1f - t).pow(0.55f)) / 0.502f).coerceAtMost(1.1f)
            val ll = leafMax * env * (0.78f + 0.44f * rnd.nextFloat())
            if (ll < 1.5f) continue
            val psi = hs[i] + side * (spread + (rnd.nextFloat() - 0.5f) * 18f) * g.DEG
            var dx = cos(psi)
            var dy = sin(psi)
            // Gravity: leaflets hang, the upper ones folding over the rachis.
            dy += (0.5f + 0.55f * p.age + 0.3f * t) * (if (side < 0) 1.3f else 1f) * (0.4f + 0.6f * p.open)
            val dl = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            dx /= dl; dy /= dl
            val bx = xs[i] + (rnd.nextFloat() - 0.5f) * seg * 0.5f
            val by = ys[i]
            val ex = bx + dx * ll; val ey = by + dy * ll
            val qx = bx + dx * ll * 0.5f; val qy = by + dy * ll * 0.5f + ll * 0.1f
            val wb = max(1.1f, len * 0.0115f * (0.6f + 0.7f * env))
            val nx = -dy; val ny = dx
            path.rewind()
            path.moveTo(bx + nx * wb * 0.5f, by + ny * wb * 0.5f)
            path.quadTo(qx + nx * wb * 0.3f, qy + ny * wb * 0.3f, ex, ey)
            path.quadTo(qx - nx * wb * 0.3f, qy - ny * wb * 0.3f, bx - nx * wb * 0.5f, by - ny * wb * 0.5f)
            path.close()
            val light = (0.28f + 0.42f * t.pow(0.7f) + (if (side < 0) 0.2f else -0.04f) + (rnd.nextFloat() - 0.5f) * 0.2f)
                .coerceIn(0f, 1f)
            var col = if (light < 0.5f) k.mix(deepG, green, light * 2f) else k.mix(green, fresh, (light - 0.5f) * 2f)
            col = k.mix(col, old, (p.age * 0.8f * (0.4f + 0.6f * t) + (if (t > 0.88f) 0.2f else 0f)).coerceAtMost(0.95f))
            k.fill.color = k.jitter(col, rnd, 0.1f)
            c.drawPath(path, k.fill)
        }
    }

    leaflets(-1)
    // The rachis, thick at the crown and thinning to nothing.
    k.line.color = k.mix(l.foliage(0xFF9BBF52.toInt()), l.ground(0xFF9A8440.toInt()), p.age)
    for (i in 1..steps) {
        k.line.strokeWidth = max(1.2f, len * 0.017f * (1f - 0.8f * i / steps))
        c.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], k.line)
    }
    leaflets(1)
    return bmp
}
