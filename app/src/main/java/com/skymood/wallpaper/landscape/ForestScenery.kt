package com.skymood.wallpaper.landscape

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Painting for the Forest landscape. A real wood reads as a mix of species (dark spruce, bright
 * birch, flat-topped pine), a dark interior with bare trunks showing through, mist between the
 * rows of trees, shafts of sun falling through the canopy, and a floor of ferns, moss and logs.
 * Everything here runs on the painter thread, never per frame.
 */

/**
 * A tapered trunk with bark: lit on the sun's side and shaded on the other, with vertical furrows,
 * a flared foot and (when [moss] is given) moss creeping up the shaded side.
 */
internal fun PaintKit.barkTrunk(
    c: Canvas, x: Float, baseY: Float, topY: Float, wBase: Float, wTop: Float, bark: Int,
    rnd: Random, lean: Float = 0f, moss: Int = 0, furrows: Boolean = true,
) {
    val len = baseY - topY
    if (len <= 1f) return
    val steps = 12
    val cxs = FloatArray(steps + 1); val hws = FloatArray(steps + 1); val ys = FloatArray(steps + 1)
    for (s in 0..steps) {
        val t = s / steps.toFloat()
        cxs[s] = x + lean * t * t + (if (s in 1 until steps) (rnd.nextFloat() - 0.5f) * wBase * 0.05f else 0f)
        ys[s] = baseY - len * t
        // Tapers towards the top and flares into roots at the foot.
        hws[s] = (wBase + (wTop - wBase) * t.pow(0.75f)) * 0.5f * (1f + 0.45f * (1f - t * 7f).coerceAtLeast(0f))
    }
    val body = Path()
    body.moveTo(cxs[0] - hws[0], ys[0])
    for (s in 1..steps) body.lineTo(cxs[s] - hws[s], ys[s])
    for (s in steps downTo 0) body.lineTo(cxs[s] + hws[s], ys[s])
    body.close()
    fill.color = bark
    c.drawPath(body, fill)

    // Light across the trunk, from the lit edge (u = sun) to the shaded edge (u = -sun).
    fun strip(u0: Float, u1: Float, col: Int) {
        val p = Path()
        p.moveTo(cxs[0] + u0 * hws[0], ys[0])
        for (s in 1..steps) p.lineTo(cxs[s] + u0 * hws[s], ys[s])
        for (s in steps downTo 0) p.lineTo(cxs[s] + u1 * hws[s], ys[s])
        p.close()
        fill.color = col
        c.drawPath(p, fill)
    }
    strip(sun * 1.0f, sun * 0.5f, alpha(lighten(bark, 0.32f), 150))
    strip(sun * 0.5f, sun * 0.05f, alpha(lighten(bark, 0.12f), 80))
    strip(-sun * 0.25f, -sun * 0.6f, alpha(Color.BLACK, 55))
    strip(-sun * 0.6f, -sun * 1.0f, alpha(Color.BLACK, 115))

    if (furrows) {
        val n = (wBase / (4.5f * unit)).toInt().coerceIn(3, 20)
        for (i in 0 until n) {
            val u = (rnd.nextFloat() * 2f - 1f) * 0.9f
            var s = (rnd.nextFloat() * 0.3f * steps).toInt()
            val sEnd = ((0.35f + 0.65f * rnd.nextFloat()) * steps).toInt().coerceAtLeast(s + 2).coerceAtMost(steps)
            val inShade = u * -sun > 0f
            line.strokeWidth = max(1.2f, wBase * (0.025f + 0.035f * rnd.nextFloat()))
            line.color = alpha(darken(bark, 0.62f), if (inShade) 205 else 150)
            var wob = 0f
            var lx = cxs[s] + u * hws[s]; var ly = ys[s]
            while (s < sEnd) {
                s++
                wob += (rnd.nextFloat() - 0.5f) * wBase * 0.04f
                val nx = cxs[s] + u * hws[s] + wob; val ny = ys[s]
                c.drawLine(lx, ly, nx, ny, line)
                lx = nx; ly = ny
            }
        }
        // A few pale ridges on the lit side.
        for (i in 0 until n / 2) {
            val u = sun * (0.25f + 0.6f * rnd.nextFloat())
            var s = (rnd.nextFloat() * 0.25f * steps).toInt()
            val sEnd = ((0.3f + 0.5f * rnd.nextFloat()) * steps).toInt().coerceAtLeast(s + 2).coerceAtMost(steps)
            line.strokeWidth = max(1f, wBase * 0.025f)
            line.color = alpha(lighten(bark, 0.4f), 80)
            var lx = cxs[s] + u * hws[s]; var ly = ys[s]
            while (s < sEnd) {
                s++
                val nx = cxs[s] + u * hws[s]; val ny = ys[s]
                c.drawLine(lx, ly, nx, ny, line)
                lx = nx; ly = ny
            }
        }
    }
    if (furrows && wBase > 16f * unit) {
        // Thick bark cracks into plates: short cross-cracks between the furrows.
        repeat((len / (11f * unit)).toInt().coerceIn(8, 120)) {
            val t = rnd.nextFloat()
            val s = (t * steps).toInt().coerceAtMost(steps)
            val u = (rnd.nextFloat() * 2f - 1f) * 0.8f
            val y = baseY - len * t
            val shaded = u * -sun > 0f
            line.strokeWidth = max(1.2f, wBase * 0.03f)
            line.color = alpha(darken(bark, 0.7f), if (shaded) 190 else 130)
            val x0 = cxs[s] + u * hws[s]
            c.drawLine(x0, y, x0 + hws[s] * (0.25f + 0.3f * rnd.nextFloat()), y + (rnd.nextFloat() - 0.5f) * wBase * 0.08f, line)
        }
    }
    if (moss != 0) {
        repeat((wBase / (2f * unit)).toInt().coerceIn(6, 44)) {
            val s = (rnd.nextFloat().pow(2f) * 0.26f * steps).toInt()
            val u = -sun * (0.1f + 0.9f * rnd.nextFloat())
            fill.color = jitter(moss, rnd, 0.22f)
            c.drawCircle(cxs[s] + u * hws[s], ys[s] - rnd.nextFloat() * wBase * 0.1f, wBase * (0.03f + 0.06f * rnd.nextFloat()), fill)
        }
    }
}

/** An elliptical clump of leaves or needles: dark body, lit on top and on the sun's side. */
internal fun PaintKit.leafMass(
    c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, lit: Int, dark: Int,
    dabs: Int, dabR: Float, rnd: Random, backing: Boolean = true,
) {
    val deep = darken(dark, 0.35f)
    if (backing) {
        fill.color = deep
        c.drawOval(cx - rx * 0.96f, cy - ry * 0.86f, cx + rx * 0.96f, cy + ry * 0.96f, fill)
    }
    for (k in 0 until dabs) {
        val a = rnd.nextFloat() * 6.2832f
        val d = sqrt(rnd.nextFloat()) * 0.96f
        val dx = cos(a) * d; val dy = sin(a) * d
        val lightness = (0.5f - dy * 0.7f + sun * dx * 0.45f + (rnd.nextFloat() - 0.5f) * 0.3f).coerceIn(0f, 1f)
        fill.color = jitter(mix(deep, mix(dark, lit, 0.85f), lightness), rnd, 0.16f)
        c.drawCircle(cx + dx * rx, cy + dy * ry, dabR * (0.6f + rnd.nextFloat() * 0.8f), fill)
    }
}

/**
 * A clump of pine needles: a dark body, then hundreds of short strokes splaying out of it, lit on
 * top and on the sun's side, so the clump has the spiky outline of needles rather than round leaves.
 */
internal fun PaintKit.needleMass(
    c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, lit: Int, dark: Int, strokes: Int, len: Float, rnd: Random,
) {
    val deep = darken(dark, 0.35f)
    fill.color = deep
    c.drawOval(cx - rx * 0.88f, cy - ry * 0.8f, cx + rx * 0.88f, cy + ry * 0.92f, fill)
    line.strokeWidth = max(1.2f * unit, len * 0.14f)
    for (k in 0 until strokes) {
        val a = rnd.nextFloat() * 6.2832f
        val d = sqrt(rnd.nextFloat())
        val dx = cos(a) * d; val dy = sin(a) * d
        val lightness = (0.5f - dy * 0.7f + sun * dx * 0.45f + (rnd.nextFloat() - 0.5f) * 0.35f).coerceIn(0f, 1f)
        line.color = jitter(mix(deep, mix(dark, lit, 0.9f), lightness), rnd, 0.18f)
        // Needles splay outwards from the centre of the clump.
        val na = a + (rnd.nextFloat() - 0.5f) * 1.2f
        val nl = len * (0.5f + rnd.nextFloat())
        val px = cx + dx * rx * 0.92f; val py = cy + dy * ry * 0.92f
        c.drawLine(px, py, px + cos(na) * nl, py + sin(na) * nl * 0.8f, line)
    }
}

/**
 * A spruce or fir the way one really grows: a dark core, and tier after tier of drooping branches
 * with ragged, hanging tips. Each branch is a plump blade with a dark underside, lit on the
 * sun's side and sprigged with needles along its upper edge; branches thin out low down, so the
 * lowest tiers show gaps and the trunk. Sizes and gaps differ from one tree to the next.
 */
internal fun PaintKit.spruce(
    c: Canvas, x: Float, baseY: Float, th: Float, lit: Int, dark: Int, bark: Int,
    rnd: Random, detail: Float, bareFoot: Float = 0.1f,
) {
    val maxHalf = th * (0.19f + rnd.nextFloat() * 0.07f)
    markTree(c, x, baseY, th, maxHalf, 0.6f)
    val deep = darken(dark, 0.4f)
    val lean = (rnd.nextFloat() - 0.5f) * th * 0.03f
    val top = baseY - th
    val footY = baseY - th * bareFoot
    val big = th * detail > 60f * unit

    barkTrunk(c, x, baseY, top + th * 0.08f, th * 0.03f, th * 0.006f, bark, rnd, lean, furrows = big)

    // The dark heart of the tree, so the gaps between branches are shadow rather than sky.
    val core = Path()
    val midY = top + (footY - top) * 0.5f
    core.moveTo(x + lean, top)
    core.quadTo(x + lean - maxHalf * 0.22f, midY, x - maxHalf * 0.6f, footY - th * 0.03f)
    core.lineTo(x + maxHalf * 0.6f, footY - th * 0.03f)
    core.quadTo(x + lean + maxHalf * 0.22f, midY, x + lean, top)
    core.close()
    fill.color = mix(deep, dark, 0.25f)
    c.drawPath(core, fill)

    val tiers = ((10 + th / (34f * unit)) * (0.55f + 0.45f * detail)).toInt().coerceIn(9, 28)
    val slice = (footY - top) / tiers
    val p = Path()
    val fxs = FloatArray(10); val fys = FloatArray(10)
    val skew = (rnd.nextFloat() - 0.5f) * 0.35f   // one side of the tree grows fuller than the other

    // One drooping branch: a plump blade with a ragged lower edge. [under] ones are the shorter,
    // darker sub-branches that hang beneath a main branch and give the edge its layered, feathery look.
    fun branch(cx: Float, y: Float, side: Int, reach: Float, droop: Float, thick: Float, t: Float, under: Boolean) {
        val tipX = cx + side * reach
        val tipY = y + droop
        val rootY = y + thick * 0.55f
        val q0x = cx; val q0y = y - thick * 0.3f
        val q1x = cx + side * reach * 0.6f; val q1y = y - thick * 0.45f - droop * 0.1f

        p.rewind()
        p.moveTo(q0x, q0y)
        p.quadTo(q1x, q1y, tipX, tipY)
        val fr = (2 + reach / (11f * unit)).toInt().coerceIn(3, 9)
        for (k in 1..fr) {
            val u = k / fr.toFloat()
            val fx = tipX + (cx - tipX) * u
            val lineY = tipY + (rootY - tipY) * u
            val hang = thick * (if (k % 2 == 1) 0.5f else 0.12f) * (0.6f + rnd.nextFloat() * 0.8f) * (1f - 0.5f * u)
            p.lineTo(fx, lineY + hang)
            fxs[k] = fx; fys[k] = lineY + hang
        }
        p.close()

        val sunSide = side.toFloat() == sun
        val tone = ((if (sunSide) 0.8f else 0.4f) - 0.3f * t - (if (under) 0.22f else 0f) + (rnd.nextFloat() - 0.5f) * 0.18f)
            .coerceIn(0f, 1f)
        val outer = mix(dark, lit, tone)
        val inner = mix(deep, dark, 0.5f)
        // The dark underside of the branch, peeking out below it.
        c.save()
        c.translate(0f, thick * 0.32f)
        fill.color = deep
        c.drawPath(p, fill)
        c.restore()
        shader = LinearGradient(cx, 0f, tipX, 0f, inner, outer, Shader.TileMode.CLAMP)
        c.drawPath(p, fill)
        shader = null

        if (big && !under) {
            // Needles sprigging up and out along the top of the branch.
            val n = (reach / (4.5f * unit) * detail).toInt().coerceIn(3, 60)
            line.strokeWidth = max(1f, th * 0.0028f)
            for (s in 0 until n) {
                val u = 0.15f + 0.85f * rnd.nextFloat()
                val iu = 1f - u
                val px = iu * iu * q0x + 2 * iu * u * q1x + u * u * tipX
                val py = iu * iu * q0y + 2 * iu * u * q1y + u * u * tipY
                val a = -1.5708f + side * (0.2f + rnd.nextFloat() * 0.9f)
                val nl = th * (0.008f + 0.012f * rnd.nextFloat())
                line.color = jitter(if (sunSide) lighten(outer, 0.2f) else mix(outer, dark, 0.2f), rnd, 0.16f)
                c.drawLine(px, py, px + cos(a) * nl, py + sin(a) * nl, line)
            }
            // Short twigs hanging from the lower edge, in the branch's own shade so they read as needles.
            line.color = alpha(mix(deep, dark, 0.45f), 200)
            for (k in 1..fr step 2) {
                val tl = thick * (0.3f + rnd.nextFloat() * 0.5f)
                c.drawLine(fxs[k], fys[k] - 1f, fxs[k] + side * tl * 0.1f, fys[k] + tl, line)
            }
        }
    }

    for (i in 0 until tiers) {
        val t = (i + 0.35f + rnd.nextFloat() * 0.3f) / tiers   // 0 at the spire, 1 at the lowest tier
        val y = top + (footY - top) * t.pow(1.08f)
        val cx = x + lean * (1f - t)
        val reach0 = maxHalf * (0.1f + 0.9f * t.pow(0.8f))
        for (side in intArrayOf(-1, 1)) {
            if (t > 0.5f && rnd.nextFloat() < 0.2f * t) continue                  // gaps low down
            if (t in 0.12f..0.5f && rnd.nextFloat() < 0.06f) continue            // and the odd one higher up
            val reach = reach0 * (0.55f + rnd.nextFloat() * 0.7f) * (1f + skew * side)
            val droop = reach * (0.3f + 0.2f * t) * (0.8f + rnd.nextFloat() * 0.4f)
            val thick = slice * (1.1f + 0.5f * rnd.nextFloat())
            if (big) branch(cx, y + thick * 0.7f, side, reach * 0.66f, droop * 1.15f, thick * 0.9f, t, true)
            branch(cx, y, side, reach, droop, thick, t, false)
        }
    }
    line.strokeWidth = max(1f, th * 0.004f)
    line.color = lit
    c.drawLine(x + lean, top - th * 0.02f, x + lean, top + th * 0.05f, line)
}

/**
 * A Scots pine: a tall, straight trunk with orange-brown bark that runs up through the crown to the
 * leader, stubs of shed branches below, and a ragged, airy crown of needle clumps on branching limbs.
 */
internal fun PaintKit.scotsPine(
    c: Canvas, x: Float, baseY: Float, th: Float, lit: Int, dark: Int, bark: Int, rnd: Random, detail: Float,
    inward: Int = 0,
) {
    markTree(c, x, baseY, th, th * 0.26f, 0.4f)
    val lean = (rnd.nextFloat() - 0.5f) * th * 0.05f
    val crownTop = baseY - th
    val crownH = th * 0.4f
    val trunkTop = crownTop + th * 0.02f
    // The trunk's x at height y, following the lean that barkTrunk gives it.
    fun trunkX(y: Float): Float {
        val f = ((baseY - y) / (baseY - trunkTop)).coerceIn(0f, 1f)
        return x + lean * f * f
    }
    // How many needle strokes a clump of this size needs to look full; fewer for small, far trees.
    fun needles(rx: Float, ry: Float) = (rx * ry * 3.14f / (40f * unit * unit) * (0.4f + 0.6f * detail)).toInt().coerceIn(24, 900)
    fun clump(cx: Float, cy: Float, rx: Float) {
        val ry = rx * (0.5f + 0.2f * rnd.nextFloat())
        needleMass(c, cx, cy - ry * 0.4f, rx, ry, lit, dark, needles(rx, ry), max(4f * unit, rx * 0.17f), rnd)
    }

    // The trunk runs right up through the crown to the leader; it turns pale orange where the bark flakes.
    barkTrunk(c, x, baseY, trunkTop, th * 0.052f, th * 0.012f, bark, rnd, lean, moss = mix(dark, lit, 0.4f))
    val upper = Path()
    val ty1 = trunkTop + (baseY - trunkTop) * 0.42f
    val hw0 = th * 0.0065f; val hw1 = th * 0.0165f
    upper.moveTo(x + lean - hw0, trunkTop); upper.lineTo(x + lean + hw0, trunkTop)
    upper.lineTo(x + lean * 0.35f + hw1, ty1); upper.lineTo(x + lean * 0.35f - hw1, ty1); upper.close()
    shader = LinearGradient(0f, trunkTop, 0f, ty1, alpha(mix(bark, 0xFFD4824A.toInt(), 0.55f), 200), alpha(bark, 0), Shader.TileMode.CLAMP)
    c.drawPath(upper, fill)
    shader = null

    // Stubs of dead branches along the bare trunk below the crown, as pines shed their lower limbs.
    line.color = darken(bark, 0.2f)
    repeat(5) {
        val y = crownTop + crownH + (baseY - crownTop - crownH) * (0.02f + 0.3f * rnd.nextFloat())
        val side = if (rnd.nextBoolean()) -1 else 1
        val len = th * (0.008f + 0.016f * rnd.nextFloat())
        val sx = trunkX(y) + side * th * 0.012f
        // A tapering stub that bends down: two short strokes, the second thinner.
        line.strokeWidth = max(1.5f, th * 0.0055f)
        c.drawLine(sx, y, sx + side * len * 0.6f, y + len * 0.2f, line)
        line.strokeWidth = max(1.2f, th * 0.0035f)
        c.drawLine(sx + side * len * 0.6f, y + len * 0.2f, sx + side * len, y + len * 0.7f, line)
    }

    // The leader: a clump at the very top, flanked by two smaller ones, closing over the end of the trunk.
    val ax0 = trunkX(crownTop)
    clump(ax0, crownTop + th * 0.035f, th * 0.052f)
    clump(ax0 - th * 0.045f, crownTop + th * 0.07f, th * 0.036f)
    clump(ax0 + th * 0.045f, crownTop + th * 0.065f, th * 0.036f)

    // Limbs from the top of the crown down. High ones climb steeply and are short; low ones are long,
    // sag a little and turn up at the tip. Each carries foliage clumps (largest at the tip) and
    // most fork an upward side-branch with a clump of its own. On the side facing away from the
    // screen's centre ([inward] is the side that has room) limbs are kept short.
    val limbs = 9
    for (i in 0 until limbs) {
        val t = (i + 0.3f + rnd.nextFloat() * 0.5f) / limbs      // 0 near the top .. 1 low in the crown
        val side = if (i % 2 == 0) -1 else 1
        val y0 = crownTop + crownH * (0.1f + 0.9f * t)
        var reach = th * (0.06f + 0.12f * t) * (0.75f + rnd.nextFloat() * 0.5f)
        if (inward != 0 && side != inward) reach *= 0.55f
        val rise = reach * (0.4f - 0.3f * t) * (0.7f + 0.6f * rnd.nextFloat())
        val sag = reach * 0.1f * t
        val x0 = trunkX(y0)
        fun limbY(u: Float) = y0 - rise * u.pow(1.6f) + sag * sin(3.14f * u)

        // A crooked limb: it wanders a little off a straight line and thins towards the tip.
        val wobble = (rnd.nextFloat() - 0.5f) * reach * 0.12f
        fun limbX(u: Float) = x0 + side * reach * u
        fun limbYw(u: Float) = limbY(u) + wobble * sin(6.28f * u * 0.9f)
        var px = x0; var py = y0
        val segs = 7
        line.color = darken(bark, 0.12f)
        for (s in 1..segs) {
            val u = s / segs.toFloat()
            val nx = limbX(u); val ny = limbYw(u)
            line.strokeWidth = max(1.6f, th * 0.012f * (1f - 0.7f * u) * (1f - 0.3f * t))
            c.drawLine(px, py, nx, ny, line)
            px = nx; py = ny
        }
        // Foliage clumps cover the outer three quarters of the limb: big at the tip, smaller inboard.
        val rx = th * (0.04f + 0.028f * t) * (0.8f + 0.5f * rnd.nextFloat())
        clump(px, py - rx * 0.2f, rx)
        clump(limbX(0.78f), limbYw(0.78f) - rx * 0.2f, rx * 0.85f)
        clump(limbX(0.56f), limbYw(0.56f) - rx * 0.3f, rx * 0.75f)
        if (t > 0.15f) clump(limbX(0.36f), limbYw(0.36f) - rx * 0.35f, rx * 0.6f)
        if (t > 0.25f && rnd.nextFloat() < 0.75f) {
            val bx = limbX(0.5f); val by = limbYw(0.5f)
            val bl = reach * 0.3f
            line.strokeWidth = max(1.2f, th * 0.006f)
            line.color = darken(bark, 0.12f)
            c.drawLine(bx, by, bx + side * bl * 0.5f, by - bl * 0.8f, line)
            clump(bx + side * bl * 0.5f, by - bl * 0.8f - rx * 0.2f, rx * 0.7f)
        }
    }
}

/**
 * A silver birch: a slim white trunk with dark marks, a black rough foot, thin limbs, and an airy
 * crown of small, bright, yellow-green leaf clusters with sky showing through and slender
 * twigs hanging from them.
 */
internal fun PaintKit.birch(
    c: Canvas, x: Float, baseY: Float, th: Float, leaf: Int, leafDark: Int, rnd: Random, detail: Float,
) {
    markTree(c, x, baseY, th, th * 0.2f, 0.9f)
    val white = l.shade(0xFFE9E6DA.toInt())
    val lean = (rnd.nextFloat() - 0.5f) * th * 0.08f
    val crownTop = baseY - th
    val crownMid = baseY - th * 0.68f
    val wBase = th * 0.03f
    val trunkTop = baseY - th * 0.86f
    val deep = darken(leafDark, 0.3f)

    barkTrunk(c, x, baseY, trunkTop, wBase, wBase * 0.35f, white, rnd, lean, furrows = false)
    // Black, fissured foot.
    fill.color = alpha(darken(white, 0.8f), 215)
    c.drawRect(x - wBase * 0.62f, baseY - th * 0.07f, x + wBase * 0.62f, baseY, fill)
    // The dark diamond marks across the white bark.
    line.color = alpha(darken(white, 0.78f), 190)
    repeat((th / (9f * unit)).toInt().coerceIn(4, 40)) {
        val t = 0.08f + 0.85f * rnd.nextFloat()
        val cx = x + lean * t * t
        val hw = wBase * 0.5f * (1f - 0.65f * t.pow(0.75f))
        val side = (rnd.nextFloat() - 0.5f) * 2f
        line.strokeWidth = max(1f, wBase * (0.07f + 0.08f * rnd.nextFloat()))
        val y = baseY - (baseY - trunkTop) * t
        c.drawLine(cx + hw * side * 0.9f, y, cx + hw * (side * 0.9f + 0.7f), y - 0.5f * unit, line)
    }

    // Crown clumps in a loose oval, drawn lowest first, joined to the trunk by thin limbs.
    val clumps = (5 + 4 * detail).toInt().coerceIn(5, 9)
    val rx0 = th * 0.2f
    val cxs = FloatArray(clumps); val cys = FloatArray(clumps); val crs = FloatArray(clumps)
    for (i in 0 until clumps) {
        var dx: Float; var dy: Float
        do { dx = rnd.nextFloat() * 2f - 1f; dy = rnd.nextFloat() * 2f - 1f } while (dx * dx + dy * dy > 1f)
        cxs[i] = x + lean + dx * rx0
        cys[i] = crownMid + dy * (crownMid - crownTop) * 0.95f
        crs[i] = th * (0.07f + 0.05f * rnd.nextFloat()) * (1f - 0.25f * kotlin.math.abs(dx))
    }
    val order = (0 until clumps).sortedByDescending { cys[it] }
    val small = th < 120f * unit
    for (i in order) {
        // Limb from the trunk into this clump.
        line.color = alpha(darken(white, 0.18f), 235)
        line.strokeWidth = max(1f, th * 0.005f)
        c.drawLine(x + lean * 0.8f, baseY - th * (0.5f + 0.25f * rnd.nextFloat()), cxs[i], cys[i] + crs[i] * 0.3f, line)
        val r = crs[i]
        leafMass(
            c, cxs[i], cys[i], r * 1.15f, r * 0.85f, leaf, leafDark,
            (14 + 26 * detail).toInt(), max(1.2f * unit, r * 0.2f), rnd, backing = small,
        )
        if (!small) {
            // Slender twigs hanging from the clump.
            line.color = alpha(mix(deep, leafDark, 0.5f), 150)
            line.strokeWidth = max(1f, th * 0.002f)
            repeat(4) {
                val px = cxs[i] + (rnd.nextFloat() - 0.5f) * r * 1.6f
                c.drawLine(px, cys[i] + r * 0.6f, px + (rnd.nextFloat() - 0.5f) * r * 0.2f, cys[i] + r * (0.75f + 0.5f * rnd.nextFloat()), line)
            }
        }
    }
}

/** A low bush or shrub: a few overlapping leaf clumps on a contact shadow. */
internal fun PaintKit.bush(c: Canvas, x: Float, y: Float, r: Float, lit: Int, dark: Int, rnd: Random) {
    fill.color = alpha(Color.BLACK, 70)
    c.drawOval(x - r * 1.05f, y - r * 0.12f, x + r * 1.15f, y + r * 0.14f, fill)
    val n = 3 + rnd.nextInt(3)
    for (i in 0 until n) {
        val u = i / (n - 1f) - 0.5f
        val rx = r * (0.42f + 0.3f * rnd.nextFloat())
        leafMass(
            c, x + u * r * 1.2f, y - rx * 0.5f - rnd.nextFloat() * r * 0.15f, rx, rx * 0.75f, lit, dark,
            (rx / (2.5f * unit)).toInt().coerceIn(10, 60), max(1.3f * unit, rx * 0.18f), rnd,
        )
    }
}

/** A bracken fern: a fan of arching fronds, each a curved spine with a pair of leaflets at every joint. */
internal fun PaintKit.fern(c: Canvas, x: Float, y: Float, size: Float, lit: Int, dark: Int, rnd: Random) {
    fill.color = alpha(Color.BLACK, 45)
    c.drawOval(x - size * 0.5f, y - size * 0.05f, x + size * 0.5f, y + size * 0.07f, fill)
    val fronds = 8 + rnd.nextInt(4)
    for (f in 0 until fronds) {
        val a = -1.5708f + (f / (fronds - 1f) - 0.5f) * 2.6f + (rnd.nextFloat() - 0.5f) * 0.25f
        val len = size * (0.7f + rnd.nextFloat() * 0.5f)
        val sign = if (a >= -1.5708f) 1f else -1f
        val col = jitter(mix(dark, lit, 0.3f + rnd.nextFloat() * 0.6f), rnd, 0.16f)
        line.color = col
        var px = x; var py = y
        var dir = a
        val segs = 9
        val step = len / segs
        for (s in 1..segs) {
            val t = s / segs.toFloat()
            dir += sign * 0.15f
            val nx = px + cos(dir) * step; val ny = py + sin(dir) * step
            line.strokeWidth = max(1f, size * 0.022f * (1f - t))
            c.drawLine(px, py, nx, ny, line)
            val ll = size * 0.2f * sin(t * 3.14f).coerceAtLeast(0.18f)
            line.strokeWidth = max(1f, size * 0.017f)
            for (sd in intArrayOf(-1, 1)) {
                val la = dir + sd * 1.05f
                c.drawLine(nx, ny, nx + cos(la) * ll, ny + sin(la) * ll, line)
            }
            px = nx; py = ny
        }
    }
}

/** A boulder with moss draped over the top. */
internal fun PaintKit.mossyRock(c: Canvas, cx: Float, baseY: Float, size: Float, base: Int, moss: Int, rnd: Random) {
    rock(c, cx, baseY, size, base, rnd)
    repeat(max(8, (size / (1.6f * unit)).toInt())) {
        val a = -2.7f + rnd.nextFloat() * 2.25f
        val px = cx + cos(a) * size * (0.25f + 0.65f * rnd.nextFloat())
        val py = baseY + sin(a) * size * (0.35f + 0.45f * rnd.nextFloat())
        fill.color = jitter(if (rnd.nextFloat() < 0.3f) lighten(moss, 0.2f) else moss, rnd, 0.2f)
        c.drawCircle(px, py, size * (0.06f + 0.1f * rnd.nextFloat()), fill)
    }
}

/** A fallen trunk lying on the ground: bark, a cut end with growth rings, a broken end and moss on top. */
internal fun PaintKit.fallenLog(
    c: Canvas, x: Float, y: Float, len: Float, rad: Float, bark: Int, moss: Int, tiltDeg: Float, rnd: Random,
) {
    fill.color = alpha(Color.BLACK, 85)
    c.drawOval(x - rad, y + rad * 0.45f, x + len + rad * 0.5f, y + rad * 1.2f, fill)
    c.save()
    c.translate(x, y)
    c.rotate(tiltDeg)
    val body = Path()
    body.moveTo(0f, -rad)
    body.lineTo(len, -rad * 0.88f)
    body.lineTo(len, rad * 0.88f)
    body.lineTo(0f, rad)
    body.close()
    shader = LinearGradient(
        0f, -rad, 0f, rad, intArrayOf(lighten(bark, 0.24f), bark, darken(bark, 0.6f)), floatArrayOf(0f, 0.38f, 1f),
        Shader.TileMode.CLAMP,
    )
    c.drawPath(body, fill)
    shader = null
    // Bark furrows along the length.
    repeat((len / (9f * unit)).toInt().coerceIn(6, 40)) {
        val ly = (rnd.nextFloat() * 2f - 1f) * rad * 0.9f
        val sx = rnd.nextFloat() * len * 0.9f
        line.strokeWidth = max(1f, rad * (0.04f + 0.05f * rnd.nextFloat()))
        line.color = alpha(darken(bark, 0.65f), if (ly > 0f) 150 else 100)
        c.drawLine(sx, ly, sx + len * (0.06f + 0.18f * rnd.nextFloat()), ly + (rnd.nextFloat() - 0.5f) * rad * 0.06f, line)
    }
    // The broken far end, and the sawn near end with its rings.
    fill.color = darken(bark, 0.45f)
    c.drawOval(len - rad * 0.3f, -rad * 0.88f, len + rad * 0.3f, rad * 0.88f, fill)
    val wood = l.shade(0xFFC9AC7C.toInt())
    fill.color = wood
    c.drawOval(-rad * 0.42f, -rad, rad * 0.42f, rad, fill)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = alpha(darken(wood, 0.4f), 170)
        strokeWidth = max(1f, rad * 0.04f)
    }
    for (k in 1..3) {
        val f = k / 4f
        c.drawOval(-rad * 0.42f * f, -rad * f, rad * 0.42f * f, rad * f, ring)
    }
    // Moss on the top.
    repeat((len / (4f * unit)).toInt().coerceIn(10, 60)) {
        val px = len * (0.2f + 0.6f * rnd.nextFloat())
        fill.color = jitter(if (rnd.nextFloat() < 0.3f) lighten(moss, 0.2f) else moss, rnd, 0.2f)
        c.drawCircle(px, -rad * (0.78f + 0.2f * rnd.nextFloat()), rad * (0.08f + 0.12f * rnd.nextFloat()), fill)
    }
    c.restore()
}

/** The shade a tree throws on the ground, stretched away from the sun. */
internal fun PaintKit.treeShadow(c: Canvas, x: Float, y: Float, th: Float) {
    val r = th * 0.3f
    c.save()
    c.translate(x - sun * r * 0.45f, y)
    c.scale(1f, 0.26f)
    shader = RadialGradient(0f, 0f, r, alpha(Color.BLACK, 150), 0, Shader.TileMode.CLAMP)
    c.drawCircle(0f, 0f, r, fill)
    c.restore()
    shader = null
}

/**
 * Shafts of sunlight slanting down through the canopy. They fan out from the sun, fade in and out
 * over [top]..[bottom] and are built from three stacked, narrowing slivers so their edges are soft.
 * Only when the sun is out: none at night, under cloud, or in snow.
 */
internal fun PaintKit.lightShafts(c: Canvas, top: Float, bottom: Float, count: Int, rnd: Random, strength: Int) {
    if (night || l.snow || l.reflection == 0) return
    val sx = l.reflectionX * w
    val sy = h * 0.26f
    val warm = l.reflection
    val p = Path()
    val ramp = floatArrayOf(0f, 0.3f, 0.7f, 1f)
    for (i in 0 until count) {
        val tx = w * (-0.1f + 1.2f * (i + rnd.nextFloat() * 0.8f) / count)
        val ww = w * (0.02f + 0.05f * rnd.nextFloat())
        val f = (top - sy) / (bottom - sy)
        val topX = sx + (tx - sx) * f
        val topW = ww * f * 0.6f
        val a = (strength * (0.6f + 0.5f * rnd.nextFloat())).toInt()
        for (pass in 0..2) {
            val k = 1f - pass * 0.3f
            p.rewind()
            p.moveTo(topX - topW * k, top); p.lineTo(topX + topW * k, top)
            p.lineTo(tx + ww * k, bottom); p.lineTo(tx - ww * k, bottom)
            p.close()
            shader = LinearGradient(
                0f, top, 0f, bottom, intArrayOf(alpha(warm, 0), alpha(warm, a / 3), alpha(warm, a / 4), alpha(warm, 0)),
                ramp, Shader.TileMode.CLAMP,
            )
            c.drawPath(p, fill)
        }
    }
    shader = null
}

/** Patches of sun lying on the forest floor where the canopy opens. */
internal fun PaintKit.dapple(c: Canvas, ridge: Ridge, bottom: Float, rnd: Random, count: Int) {
    if (night || l.snow || l.reflection == 0) return
    val warm = l.reflection
    val horizon = ridge.yAt(w * 0.5f)
    repeat(count) {
        val d = 0.15f + 0.85f * rnd.nextFloat()
        val px = -margin + rnd.nextFloat() * (w + margin * 2)
        val py = horizon + (bottom - horizon) * d
        val rx = w * (0.04f + 0.1f * d) * (0.6f + rnd.nextFloat() * 0.8f)
        c.save()
        c.translate(px, py)
        c.scale(1f, 0.2f)
        shader = RadialGradient(0f, 0f, rx, alpha(warm, 75), alpha(warm, 0), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, rx, fill)
        c.restore()
    }
    shader = null
}
