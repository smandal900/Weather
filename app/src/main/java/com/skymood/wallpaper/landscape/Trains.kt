package com.skymood.wallpaper.landscape

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The kinds of train that pass, one after another. [length] is the train's length and [speed] its
 * speed, both in screen widths (per second for the speed); [gap] is the pause, in seconds, before
 * the next train comes.
 */
internal enum class TrainKind(val length: Float, val speed: Float, val gap: Float) {
    EXPRESS(0.93f, 0.19f, 5f),
    FREIGHT(1.16f, 0.12f, 4f),
    COMMUTER(0.82f, 0.17f, 6f),
    STEAM(0.70f, 0.10f, 5f),
}

private val TRAINS = TrainKind.entries

/** A car's height as a fraction of the screen height; everything on a train is sized from it. */
internal const val TRAIN_CAR_H = 0.03f

/**
 * Where the near rail lies below the y given to [railTrack], as a fraction of the screen height.
 * Trains are drawn with their wheels on this rail (and tall enough to reach the far one), so the
 * track and the trains use the same value.
 */
internal const val NEAR_RAIL = 0.0085f

/** Height of the overhead contact wire above the rails, in car heights; the pantographs reach exactly this high. */
internal const val OVERHEAD_CARS = 2.1f

/**
 * Overhead line over the track from [x0] to [x1]: lattice masts, a messenger wire hanging between
 * them, droppers, and a straight contact wire at the height the pantographs reach. [trackY] must be
 * the same rail height the trains are drawn at, so the wire meets their pantographs.
 */
internal fun PaintKit.overheadLine(c: Canvas, trackY: Float, x0: Float, x1: Float) {
    val carH = h * TRAIN_CAR_H
    val contactY = trackY - OVERHEAD_CARS * carH
    val messengerY = contactY - 0.7f * carH
    val topY = messengerY - 0.1f * carH
    val baseY = trackY + h * 0.003f
    val steel = l.shade(0xFF454A52.toInt())
    val wire = alpha(l.shade(0xFF1E2226.toInt()), 225)
    val spacing = w * 0.3f
    val mastW = carH * 0.1f

    val xs = ArrayList<Float>()
    var x = w * 0.1f
    while (x - spacing > x0) x -= spacing
    while (x < x1 + spacing) { xs.add(x); x += spacing }

    // Masts: two posts braced with zigzag lattice, a base plate and a cap.
    for (mx in xs) {
        if (mx < x0 - mastW || mx > x1 + mastW) continue
        line.color = steel; line.strokeWidth = max(1.5f, carH * 0.03f)
        c.drawLine(mx - mastW / 2f, baseY, mx - mastW / 2f, topY, line)
        c.drawLine(mx + mastW / 2f, baseY, mx + mastW / 2f, topY, line)
        line.strokeWidth = max(1f, carH * 0.016f)
        var y = baseY; var side = -1f
        val step = carH * 0.2f
        while (y - step > topY) {
            c.drawLine(mx + side * mastW / 2f, y, mx - side * mastW / 2f, y - step, line)
            y -= step; side = -side
        }
        fill.color = steel
        c.drawRect(mx - mastW, baseY - carH * 0.05f, mx + mastW, baseY, fill)
        c.drawRect(mx - carH * 0.18f, topY - carH * 0.03f, mx + carH * 0.18f, topY + carH * 0.04f, fill)
    }

    // Messenger wire sagging between masts, with droppers down to the contact wire.
    line.color = wire; line.strokeWidth = max(1.2f, carH * 0.022f)
    val sagY = contactY - 0.15f * carH
    for (i in 0 until xs.size - 1) {
        val a = xs[i]; val b = xs[i + 1]
        var px = a; var py = messengerY
        for (s in 1..10) {
            val u = s / 10f
            val xx = a + (b - a) * u
            val yy = messengerY + (sagY - messengerY) * 4f * u * (1f - u)
            c.drawLine(px, py, xx, yy, line); px = xx; py = yy
        }
        line.strokeWidth = max(1f, carH * 0.014f)
        for (u in floatArrayOf(0.15f, 0.3f, 0.5f, 0.7f, 0.85f)) {
            val dx = a + (b - a) * u
            val dy = messengerY + (sagY - messengerY) * 4f * u * (1f - u)
            if (contactY - dy > carH * 0.05f) c.drawLine(dx, dy, dx, contactY, line)
        }
        line.strokeWidth = max(1.2f, carH * 0.022f)
    }

    // The contact wire itself: dead straight, at the pantograph height.
    line.strokeWidth = max(1.5f, carH * 0.03f)
    c.drawLine(x0, contactY, x1, contactY, line)
}

/** Half-width at the far end (in car heights) and opacity of each stacked headlight cone, widest first. */
private val BEAM_LAYERS = arrayOf(2.4f to 38, 1.5f to 56, 0.8f to 84, 0.35f to 110)

/**
 * Draws side-on trains that cross the screen one at a time: an express, a long freight, a sleek
 * commuter unit and a steam train with drifting smoke, in turn. Each runs to the right, with lit
 * windows and a headlight beam after dark. Call [drawPass] every frame.
 */
internal class TrainRenderer {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pool = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beam = Path()
    private val shape = Path()
    private val rect = RectF()

    private var index = 0
    private var slotStart = 0f

    init {
        // Every stop keeps the same warm RGB and only its alpha changes: fading to plain
        // Color.TRANSPARENT (black) would pass through a dirty grey ring.
        glow.shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(0xF2FFF8E2.toInt(), 0x80FFECBC.toInt(), 0x26FFE6A8, 0x00FFE6A8),
            floatArrayOf(0f, 0.16f, 0.5f, 1f), Shader.TileMode.CLAMP)
        // Unit-length beam: bright at the lamp and fading smoothly to nothing; scaled to size when drawn.
        beamPaint.shader = LinearGradient(0f, 0f, 1f, 0f,
            intArrayOf(0xC0FFF4D2.toInt(), 0x70FFEBBC.toInt(), 0x30FFE6A8, 0x00FFE6A8),
            floatArrayOf(0f, 0.22f, 0.6f, 1f), Shader.TileMode.CLAMP)
        // The patch of light the beam throws onto the rails ahead.
        pool.shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(0x66FFEBBC, 0x30FFE6A8, 0x00FFE6A8), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    /**
     * Draws whichever train is passing at time [t] (seconds); nothing while the track is empty.
     * [trackY] is the top of the rails. Trains start fully off the left edge and carry on until
     * their last car has left on the right, then the next kind follows after a short pause.
     */
    fun drawPass(c: Canvas, t: Float, trackY: Float, w: Float, h: Float, l: Lighting) {
        var kind = TRAINS[index]
        var run = (1.1f + kind.length) / kind.speed
        while (t - slotStart >= run + kind.gap) {
            slotStart += run + kind.gap
            index = (index + 1) % TRAINS.size
            kind = TRAINS[index]
            run = (1.1f + kind.length) / kind.speed
        }
        val elapsed = t - slotStart
        if (elapsed < 0f || elapsed >= run) return
        val speedPx = kind.speed * w
        val headX = -w * 0.05f + speedPx * elapsed
        val carH = h * TRAIN_CAR_H
        when (kind) {
            TrainKind.EXPRESS -> express(c, headX, trackY, w, l, carH)
            TrainKind.FREIGHT -> freight(c, headX, trackY, w, l, carH)
            TrainKind.COMMUTER -> commuter(c, headX, trackY, w, l, carH)
            TrainKind.STEAM -> steam(c, t, headX, trackY, w, l, carH, speedPx)
        }
    }

    // ---- shared pieces -------------------------------------------------------------------

    private fun fill(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
        p.color = color; c.drawRect(x0, y0, x1, y1, p)
    }

    private fun glass(l: Lighting) = if (l.windowsLit) 0xFFFFD27A.toInt() else l.shade(0xFF6F8AA2.toInt())

    private fun visible(x0: Float, x1: Float, w: Float) = x1 > -w * 0.1f && x0 < w * 1.1f

    /**
     * A pantograph on a roof at [roof]: two arms folded up to a contact strip whose top sits exactly
     * at the overhead wire's height, so the strip presses against the wire.
     */
    private fun pantograph(c: Canvas, cx: Float, roof: Float, carH: Float, l: Lighting) {
        val rise = (OVERHEAD_CARS - 1f) * carH
        val col = l.shade(0xFF2A2E34.toInt())
        val top = roof - rise
        val knee = roof - rise * 0.5f
        fill(c, cx - carH * 0.32f, roof - carH * 0.05f, cx + carH * 0.32f, roof, col)
        st.color = col; st.strokeWidth = max(1.5f, carH * 0.05f)
        c.drawLine(cx - carH * 0.24f, roof - carH * 0.04f, cx + carH * 0.18f, knee, st)
        c.drawLine(cx + carH * 0.18f, knee, cx - carH * 0.04f, top, st)
        c.drawLine(cx + carH * 0.1f, roof - carH * 0.04f, cx + carH * 0.34f, knee, st)
        c.drawLine(cx + carH * 0.34f, knee, cx + carH * 0.06f, top, st)
        st.strokeWidth = max(2f, carH * 0.06f)
        c.drawLine(cx - carH * 0.3f, top, cx + carH * 0.4f, top, st)
    }

    /** A wheel set under a car: [axles] wheels around [cx], resting on the rails at [trackY]. */
    private fun bogie(c: Canvas, cx: Float, trackY: Float, carH: Float, l: Lighting, axles: Int = 2) {
        val r = carH * WHEEL_R
        val spacing = carH * 0.3f
        val half = spacing * (axles - 1) / 2f
        p.color = l.shade(0xFF16191E.toInt())
        c.drawRect(cx - half - r, trackY - r * 2.2f, cx + half + r, trackY - r * 1.3f, p)
        for (i in 0 until axles) c.drawCircle(cx - half + i * spacing, trackY - r, r, p)
    }

    /** The lamp's small lens, plus, after dark, its soft beam, the light on the rails and a halo. */
    private fun headlight(c: Canvas, x: Float, lampY: Float, trackY: Float, w: Float, carH: Float, l: Lighting) {
        p.color = if (l.windowsLit) 0xFFFFFBEA.toInt() else l.shade(0xFFE8E4C8.toInt())
        c.drawCircle(x - carH * 0.05f, lampY, carH * 0.1f, p)
        if (!l.windowsLit || x > w * 1.1f || x < -w * 0.55f) return
        val len = w * 0.5f
        val aim = (trackY - lampY) / carH // the beam leans down towards the rails
        // Light pooling on the rails ahead of the train.
        c.save(); c.translate(x + len * 0.34f, trackY + carH * 0.02f); c.scale(len * 0.4f, carH * 0.5f)
        pool.alpha = 255; c.drawCircle(0f, 0f, 1f, pool); c.restore()
        // The beam: a few stacked cones of different widths, so its sides fall off softly instead
        // of ending in a hard edge; each fades out along its length.
        c.save(); c.translate(x, lampY); c.scale(len, carH)
        for ((spread, alpha) in BEAM_LAYERS) {
            beam.rewind()
            beam.moveTo(0f, -0.05f); beam.lineTo(1f, aim - spread); beam.lineTo(1f, aim + spread * 0.7f); beam.lineTo(0f, 0.05f); beam.close()
            beamPaint.alpha = alpha; c.drawPath(beam, beamPaint)
        }
        c.restore()
        // The lamp itself: a wide soft halo with a small hot core.
        c.save(); c.translate(x, lampY); c.scale(carH * 2.6f, carH * 2.6f); glow.alpha = 255; c.drawCircle(0f, 0f, 1f, glow); c.restore()
        p.color = 0xFFFFFBEA.toInt(); c.drawCircle(x - carH * 0.05f, lampY, carH * 0.13f, p)
    }

    // ---- express: dark locomotive with a red band and cream coaches ---------------------------

    private fun express(c: Canvas, headX: Float, trackY: Float, w: Float, l: Lighting, carH: Float) {
        val carLen = w * 0.15f; val gap = w * 0.004f
        val locoLen = carLen * 1.05f
        val cars = 5
        val lit = l.windowsLit
        // Locomotive: dark body with a red band and a sloping nose.
        shape.rewind()
        shape.moveTo(headX - locoLen, trackY - carH * 0.2f); shape.lineTo(headX - locoLen, trackY - carH)
        shape.lineTo(headX - carH * 0.9f, trackY - carH); shape.lineTo(headX, trackY - carH * 0.45f)
        shape.lineTo(headX, trackY - carH * 0.2f); shape.close()
        p.shader = LinearGradient(0f, trackY - carH, 0f, trackY, l.shade(0xFF3E4A5E.toInt()), l.shade(0xFF1E2430.toInt()), Shader.TileMode.CLAMP)
        c.drawPath(shape, p); p.shader = null
        fill(c, headX - locoLen, trackY - carH * 0.55f, headX - carH * 0.2f, trackY - carH * 0.4f, l.shade(0xFFD9442F.toInt()))
        fill(c, headX - carH * 2.2f, trackY - carH * 0.9f, headX - carH * 1.2f, trackY - carH * 0.62f, if (lit) 0xFFFFD27A.toInt() else l.shade(0xFF8FB0C8.toInt()))
        pantograph(c, headX - locoLen * 0.45f, trackY - carH, carH, l) // the express is electric
        // Coaches share one body shading, set up once per frame.
        val coachShade = LinearGradient(0f, trackY - carH, 0f, trackY, l.shade(0xFFE8E4DA.toInt()), l.shade(0xFFA8A49A.toInt()), Shader.TileMode.CLAMP)
        for (i in 0 until cars) {
            val right = headX - locoLen - gap - i * (carLen + gap)
            val left = right - carLen
            if (!visible(left, right, w)) continue
            p.shader = coachShade
            c.drawRect(left, trackY - carH, right, trackY - carH * 0.2f, p); p.shader = null
            fill(c, left, trackY - carH * 0.5f, right, trackY - carH * 0.36f, l.shade(0xFFB5412F.toInt()))
            val ws = carLen / 8f
            for (k in 0 until 7) {
                fill(c, left + ws * (k + 0.5f) + ws * 0.1f, trackY - carH * 0.9f, left + ws * (k + 1.1f), trackY - carH * 0.58f, glass(l))
            }
        }
        // Wheels along the whole train.
        p.color = l.shade(0xFF14171C.toInt())
        var x = headX - carH
        val tail = headX - locoLen - gap - cars * (carLen + gap)
        while (x > tail) {
            if (x > -w * 0.1f && x < w * 1.1f) c.drawCircle(x, trackY - carH * WHEEL_R, carH * WHEEL_R, p)
            x -= carH * 0.8f
        }
        headlight(c, headX, trackY - carH * 0.42f, trackY, w, carH, l)
    }

    // ---- freight: diesel locomotive hauling boxcars, tankers, containers and coal hoppers ----

    private fun freight(c: Canvas, headX: Float, trackY: Float, w: Float, l: Lighting, carH: Float) {
        val locoLen = w * 0.15f; val wagLen = w * 0.095f; val gap = w * 0.004f
        val bottom = trackY - carH * 0.2f
        val yellow = l.shade(0xFFE0A62C.toInt())
        // Diesel locomotive: tall cab at the back, lower hood in front, grey skirt and a red stripe.
        val cabL = headX - locoLen; val cabR = headX - locoLen * 0.6f
        val roof = trackY - carH; val hoodTop = trackY - carH * 0.78f
        fill(c, cabL, roof, cabR, bottom, yellow)
        fill(c, cabR, hoodTop, headX, bottom, yellow)
        fill(c, cabL, trackY - carH * 0.42f, headX, bottom, l.shade(0xFF30343B.toInt()))
        fill(c, cabL, trackY - carH * 0.48f, headX, trackY - carH * 0.42f, l.shade(0xFFC23A2A.toInt()))
        fill(c, cabL + locoLen * 0.05f, roof + carH * 0.12f, cabR - locoLen * 0.05f, trackY - carH * 0.62f, glass(l))
        fill(c, headX - locoLen * 0.3f, hoodTop - carH * 0.1f, headX - locoLen * 0.12f, hoodTop, l.shade(0xFF3A3F47.toInt()))
        st.color = l.shade(0xFFB58420.toInt()); st.strokeWidth = max(1f, carH * 0.03f)
        for (k in 0 until 5) {
            val x = cabR + (headX - cabR) * (0.12f + k * 0.17f)
            c.drawLine(x, hoodTop + carH * 0.1f, x, trackY - carH * 0.52f, st)
        }
        bogie(c, headX - locoLen * 0.22f, trackY, carH, l, 3)
        bogie(c, headX - locoLen * 0.78f, trackY, carH, l, 3)

        val boxColors = intArrayOf(0xFF8C3B2A.toInt(), 0xFF3E6B4A.toInt(), 0xFF5B4A3A.toInt(), 0xFF3F5E86.toInt())
        val boxCols = intArrayOf(0xFFC24A2E.toInt(), 0xFF2F6DB5.toInt(), 0xFFE0A62C.toInt(), 0xFF3F8F5A.toInt(), 0xFFB5B5B8.toInt())
        val under = l.shade(0xFF22252B.toInt())
        for (i in 0 until FREIGHT_WAGONS) {
            val right = headX - locoLen - gap - i * (wagLen + gap)
            val left = right - wagLen
            if (!visible(left, right, w)) continue
            when (i % 4) {
                0 -> { // boxcar
                    fill(c, left, trackY - carH * 0.95f, right, bottom, l.shade(boxColors[(i / 4 + i) % boxColors.size]))
                    fill(c, left, trackY - carH * 0.95f, right, trackY - carH * 0.88f, under)
                    fill(c, left + wagLen * 0.38f, trackY - carH * 0.82f, left + wagLen * 0.62f, bottom, l.shade(0xFF2A2018.toInt()))
                    st.color = l.shade(0xFF1E1812.toInt()); st.strokeWidth = max(1f, carH * 0.02f)
                    for (k in 1..6) { if (k == 3 || k == 4) continue; val x = left + wagLen * k / 7f; c.drawLine(x, trackY - carH * 0.86f, x, bottom, st) }
                }
                1 -> { // tank car
                    fill(c, left, trackY - carH * 0.3f, right, bottom, under)
                    rect.set(left + wagLen * 0.04f, trackY - carH * 0.92f, right - wagLen * 0.04f, trackY - carH * 0.28f)
                    p.color = l.shade(if ((i / 4) % 2 == 0) 0xFF2E3238.toInt() else 0xFFB9BEC6.toInt())
                    c.drawRoundRect(rect, carH * 0.3f, carH * 0.3f, p)
                    fill(c, left + wagLen * 0.44f, trackY - carH * 1.02f, left + wagLen * 0.56f, trackY - carH * 0.9f, under)
                    st.color = under; st.strokeWidth = max(1f, carH * 0.03f)
                    c.drawLine(left + wagLen * 0.2f, trackY - carH * 0.92f, right - wagLen * 0.2f, trackY - carH * 0.92f, st)
                }
                2 -> { // two containers on a flat wagon
                    fill(c, left, trackY - carH * 0.32f, right, bottom, under)
                    for (k in 0..1) {
                        val x0 = left + wagLen * (0.03f + 0.49f * k); val x1 = x0 + wagLen * 0.45f
                        fill(c, x0, trackY - carH * 0.92f, x1, trackY - carH * 0.32f, l.shade(boxCols[(i + k * 2) % boxCols.size]))
                        st.color = l.shade(0xFF1A1D22.toInt()); st.alpha = 70; st.strokeWidth = max(1f, carH * 0.02f)
                        for (j in 1..4) { val x = x0 + (x1 - x0) * j / 5f; c.drawLine(x, trackY - carH * 0.88f, x, trackY - carH * 0.36f, st) }
                        st.alpha = 255
                    }
                }
                else -> { // coal hopper
                    shape.rewind()
                    shape.moveTo(left, trackY - carH * 0.8f); shape.lineTo(right, trackY - carH * 0.8f)
                    shape.lineTo(right - wagLen * 0.12f, bottom); shape.lineTo(left + wagLen * 0.12f, bottom); shape.close()
                    p.color = l.shade(0xFF4A3F36.toInt()); c.drawPath(shape, p)
                    shape.rewind()
                    shape.moveTo(left + wagLen * 0.06f, trackY - carH * 0.78f)
                    shape.quadTo((left + right) / 2f, trackY - carH * 1.12f, right - wagLen * 0.06f, trackY - carH * 0.78f); shape.close()
                    p.color = l.shade(0xFF0E1013.toInt()); c.drawPath(shape, p)
                }
            }
            bogie(c, left + wagLen * 0.16f, trackY, carH, l)
            bogie(c, right - wagLen * 0.16f, trackY, carH, l)
        }
        headlight(c, headX, trackY - carH * 0.62f, trackY, w, carH, l)
    }

    // ---- commuter: sleek white unit with a blue stripe, big windows and a pantograph --------

    private fun commuter(c: Canvas, headX: Float, trackY: Float, w: Float, l: Lighting, carH: Float) {
        val carLen = w * 0.2f; val gap = w * 0.004f
        val roof = trackY - carH; val bottom = trackY - carH * 0.2f
        val body = l.shade(0xFFE9EDF2.toInt()); val low = l.shade(0xFFB7BFCB.toInt())
        val stripe = l.shade(0xFF1F6FB5.toInt()); val door = l.shade(0xFF98A4B4.toInt())
        val windows = glass(l)
        for (i in 0 until COMMUTER_CARS) {
            val right = headX - i * (carLen + gap); val left = right - carLen
            if (!visible(left, right, w)) continue
            if (i == 0) { // the leading car has a rounded nose
                shape.rewind()
                shape.moveTo(left, roof); shape.lineTo(right - carH * 1.9f, roof)
                shape.quadTo(right - carH * 0.15f, roof + carH * 0.04f, right, trackY - carH * 0.5f)
                shape.lineTo(right, bottom); shape.lineTo(left, bottom); shape.close()
                p.color = body; c.drawPath(shape, p)
            } else fill(c, left, roof, right, bottom, body)
            fill(c, left, trackY - carH * 0.34f, right, bottom, low)
            fill(c, left, trackY - carH * 0.46f, right, trackY - carH * 0.36f, stripe)
            val s = carLen / 8f
            val doors = if (i == 0) 4 else 6
            for (k in 0 until 8) {
                if (i == 0 && k >= 6) continue
                val x0 = left + s * k
                if (k == 1 || k == doors) {
                    fill(c, x0 + s * 0.12f, roof + carH * 0.06f, x0 + s * 0.88f, trackY - carH * 0.33f, door)
                    fill(c, x0 + s * 0.28f, roof + carH * 0.16f, x0 + s * 0.72f, trackY - carH * 0.52f, windows)
                } else fill(c, x0 + s * 0.1f, roof + carH * 0.16f, x0 + s * 0.9f, trackY - carH * 0.52f, windows)
            }
            if (i == 0) { // windscreen
                shape.rewind()
                shape.moveTo(right - carH * 1.55f, roof + carH * 0.14f); shape.lineTo(right - carH * 0.55f, roof + carH * 0.14f)
                shape.quadTo(right - carH * 0.2f, roof + carH * 0.2f, right - carH * 0.08f, trackY - carH * 0.55f)
                shape.lineTo(right - carH * 1.55f, trackY - carH * 0.55f); shape.close()
                p.color = l.shade(0xFF27323F.toInt()); c.drawPath(shape, p)
            }
            if (i == 1) pantograph(c, (left + right) / 2f, roof, carH, l) // reaches the overhead wire
            bogie(c, left + carLen * 0.15f, trackY, carH, l)
            bogie(c, right - carLen * 0.15f, trackY, carH, l)
        }
        headlight(c, headX, trackY - carH * 0.5f, trackY, w, carH, l)
    }

    // ---- steam: black locomotive with a tender, three old coaches and drifting smoke ---------

    private fun steam(c: Canvas, t: Float, headX: Float, trackY: Float, w: Float, l: Lighting, carH: Float, speedPx: Float) {
        val locoLen = w * 0.17f; val tenderLen = w * 0.09f; val coachLen = w * 0.14f; val gap = w * 0.004f
        val bottom = trackY - carH * 0.2f
        val black = l.shade(0xFF1C1F24.toInt())
        val boilerTop = trackY - carH * 1.18f

        // Coaches at the back, in alternating maroon and green.
        val coachColors = intArrayOf(0xFF6B2A25.toInt(), 0xFF2F5B3F.toInt(), 0xFF6B2A25.toInt())
        val tenderRight = headX - locoLen - gap
        val tenderLeft = tenderRight - tenderLen
        for (i in 0 until STEAM_COACHES) {
            val right = tenderLeft - gap - i * (coachLen + gap); val left = right - coachLen
            if (!visible(left, right, w)) continue
            fill(c, left, trackY - carH * 0.92f, right, bottom, l.shade(coachColors[i % coachColors.size]))
            rect.set(left - coachLen * 0.01f, trackY - carH * 1.04f, right + coachLen * 0.01f, trackY - carH * 0.86f)
            p.color = l.shade(0xFF2B2420.toInt()); c.drawRoundRect(rect, carH * 0.1f, carH * 0.1f, p)
            fill(c, left, trackY - carH * 0.4f, right, trackY - carH * 0.36f, l.shade(0xFFC9A24A.toInt()))
            val ws = coachLen / 8.5f
            for (k in 0 until 8) {
                rect.set(left + ws * (k + 0.55f), trackY - carH * 0.8f, left + ws * (k + 1.05f), trackY - carH * 0.52f)
                p.color = glass(l); c.drawRoundRect(rect, ws * 0.25f, ws * 0.25f, p)
            }
            bogie(c, left + coachLen * 0.15f, trackY, carH, l)
            bogie(c, right - coachLen * 0.15f, trackY, carH, l)
        }

        if (visible(tenderLeft, headX, w)) {
            // Tender with a heap of coal.
            val tTop = trackY - carH * 0.82f
            fill(c, tenderLeft, tTop, tenderRight, bottom, l.shade(0xFF24272D.toInt()))
            shape.rewind()
            shape.moveTo(tenderLeft + tenderLen * 0.06f, tTop)
            shape.quadTo((tenderLeft + tenderRight) / 2f, tTop - carH * 0.42f, tenderRight - tenderLen * 0.06f, tTop); shape.close()
            p.color = l.shade(0xFF0E1013.toInt()); c.drawPath(shape, p)
            bogie(c, tenderLeft + tenderLen * 0.22f, trackY, carH, l)
            bogie(c, tenderRight - tenderLen * 0.22f, trackY, carH, l)

            // Cab with an overhanging roof.
            val cabL = headX - locoLen; val cabR = headX - locoLen * 0.76f
            fill(c, cabL, trackY - carH * 1.45f, cabR, trackY - carH * 0.3f, l.shade(0xFF5A1F1B.toInt()))
            fill(c, cabL - carH * 0.08f, trackY - carH * 1.55f, cabR + carH * 0.1f, trackY - carH * 1.45f, black)
            fill(c, cabL + carH * 0.14f, trackY - carH * 1.3f, cabR - carH * 0.14f, trackY - carH * 0.95f, glass(l))
            // Boiler, smokebox band, domes and the chimney.
            rect.set(headX - locoLen * 0.76f, boilerTop, headX - carH * 0.12f, trackY - carH * 0.5f)
            p.color = black; c.drawRoundRect(rect, carH * 0.34f, carH * 0.34f, p)
            st.color = l.shade(0xFF3A4048.toInt()); st.strokeWidth = max(1.5f, carH * 0.05f)
            c.drawLine(headX - locoLen * 0.7f, boilerTop + carH * 0.1f, headX - carH * 0.3f, boilerTop + carH * 0.1f, st)
            st.color = l.shade(0xFF8A6B2A.toInt()); st.strokeWidth = max(1f, carH * 0.04f)
            for (k in 1..3) { val x = headX - locoLen * (0.15f + 0.17f * k); c.drawLine(x, boilerTop + carH * 0.06f, x, trackY - carH * 0.54f, st) }
            fill(c, headX - carH * 0.45f, boilerTop + carH * 0.05f, headX - carH * 0.12f, trackY - carH * 0.55f, l.shade(0xFF2A2E34.toInt()))
            rect.set(headX - locoLen * 0.4f, boilerTop - carH * 0.26f, headX - locoLen * 0.4f + carH * 0.42f, boilerTop + carH * 0.1f)
            p.color = l.shade(0xFFB58A2E.toInt()); c.drawRoundRect(rect, carH * 0.16f, carH * 0.16f, p)
            rect.set(headX - locoLen * 0.56f, boilerTop - carH * 0.16f, headX - locoLen * 0.56f + carH * 0.3f, boilerTop + carH * 0.1f)
            p.color = black; c.drawRoundRect(rect, carH * 0.12f, carH * 0.12f, p)
            val chX = headX - carH * 0.62f
            shape.rewind()
            shape.moveTo(chX - carH * 0.1f, boilerTop + carH * 0.05f); shape.lineTo(chX - carH * 0.1f, boilerTop - carH * 0.3f)
            shape.lineTo(chX - carH * 0.22f, boilerTop - carH * 0.52f); shape.lineTo(chX + carH * 0.22f, boilerTop - carH * 0.52f)
            shape.lineTo(chX + carH * 0.1f, boilerTop - carH * 0.3f); shape.lineTo(chX + carH * 0.1f, boilerTop + carH * 0.05f); shape.close()
            p.color = black; c.drawPath(shape, p)
            // Footplate, cowcatcher and the lamp on the smokebox.
            fill(c, headX - locoLen, trackY - carH * 0.55f, headX, trackY - carH * 0.42f, l.shade(0xFF24272D.toInt()))
            shape.rewind()
            shape.moveTo(headX + carH * 0.12f, trackY - carH * 0.08f); shape.lineTo(headX - carH * 0.7f, trackY - carH * 0.55f)
            shape.lineTo(headX - carH * 0.7f, trackY - carH * 0.08f); shape.close()
            p.color = l.shade(0xFF7A1F1A.toInt()); c.drawPath(shape, p)
            fill(c, headX - carH * 0.3f, boilerTop - carH * 0.06f, headX - carH * 0.02f, boilerTop + carH * 0.22f, black)

            // Three red driving wheels joined by a coupling rod, with small leading and trailing wheels.
            val r = carH * 0.4f
            val spin = headX / r
            val cy = trackY - r
            val wheelX = floatArrayOf(headX - locoLen * 0.62f, headX - locoLen * 0.44f, headX - locoLen * 0.26f)
            val red = l.shade(0xFF9A2A22.toInt())
            for (wx in wheelX) {
                p.color = red; c.drawCircle(wx, cy, r, p)
                p.color = black; c.drawCircle(wx, cy, r * 0.8f, p)
                st.color = red; st.strokeWidth = max(1f, carH * 0.04f)
                for (k in 0 until 3) {
                    val a = spin + k * 1.0472f
                    c.drawLine(wx - cos(a) * r * 0.8f, cy - sin(a) * r * 0.8f, wx + cos(a) * r * 0.8f, cy + sin(a) * r * 0.8f, st)
                }
                p.color = red; c.drawCircle(wx, cy, r * 0.18f, p)
            }
            st.color = l.shade(0xFFC9CED6.toInt()); st.strokeWidth = max(1.5f, carH * 0.07f)
            c.drawLine(
                wheelX[0] + cos(spin) * r * 0.55f, cy + sin(spin) * r * 0.55f,
                wheelX[2] + cos(spin) * r * 0.55f, cy + sin(spin) * r * 0.55f, st,
            )
            p.color = black
            c.drawCircle(headX - carH * 0.95f, trackY - carH * 0.2f, carH * 0.2f, p)
            c.drawCircle(headX - locoLen * 0.92f, trackY - carH * 0.2f, carH * 0.2f, p)

            // Smoke: puffs stay where they were let out, so against the moving train they trail
            // back, swell, rise and fade.
            val life = 3.2f
            val puffs = 14
            val smoke = l.shade(0xFFDADDE2.toInt()) and 0x00FFFFFF
            val chimneyTop = boilerTop - carH * 0.52f
            for (i in 0 until puffs) {
                val age = (t / life + i / puffs.toFloat()) % 1f
                // Each puff swells as it climbs and thins out as it fades; a little wobble keeps the column from looking mechanical.
                val fade = (1f - age) * (1f - age) * (age * 6f).coerceAtMost(1f)
                val a = (fade * 215f).toInt().coerceIn(0, 255)
                val wobble = sin(i * 2.3f + t * 1.1f) * carH * 0.12f * age
                p.color = smoke or (a shl 24)
                c.drawCircle(
                    chX - speedPx * life * age + wobble, chimneyTop - carH * (0.2f + 2.6f * age),
                    carH * (0.22f + 0.85f * age), p,
                )
            }
        }
        headlight(c, headX, boilerTop + carH * 0.08f, trackY, w, carH, l)
    }

    private companion object {
        /** Wheel radius in car heights: big enough that a wheel on the near rail reaches up to the far rail. */
        const val WHEEL_R = 0.12f
        const val FREIGHT_WAGONS = 10
        const val COMMUTER_CARS = 4
        const val STEAM_COACHES = 3
    }
}
