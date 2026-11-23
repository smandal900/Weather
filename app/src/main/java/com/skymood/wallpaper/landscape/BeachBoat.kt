package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.skymood.wallpaper.scenes.SkyGradient
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/*
 * The speed boat: a planing runabout seen from the side, bow to the right. The sprite is painted
 * once (hull with a curved sheer and a raised bow, bottom paint, a swoosh stripe, a raked tinted
 * windshield, seats, two people and an outboard). BoatWake animates the water round it: the V-wake,
 * prop wash and rooster tail behind, spray off the bow, foam along the wetted hull and a reflection.
 * The sprite's waterline is at BOAT_WATERLINE of its height; everything below it is cut off by the
 * water when drawn.
 */

/** Where the waterline sits in the boat sprite, as a fraction of its height. */
internal const val BOAT_WATERLINE = 82f / 104f

internal fun PaintKit.paintSpeedBoat(): Bitmap? {
    val s = w * 0.001f
    val bw = (290 * s).toInt().coerceAtLeast(60)
    val bh = (104 * s).toInt().coerceAtLeast(22)
    val (c, bmp) = newSprite(bw, bh) ?: return null
    val ox = bw / 2f; val oy = bh * BOAT_WATERLINE
    fun px(x: Float) = ox + x * s
    fun py(y: Float) = oy + y * s
    fun Path.m(x: Float, y: Float) = moveTo(px(x), py(y))
    fun Path.l(x: Float, y: Float) = lineTo(px(x), py(y))
    fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadTo(px(cx), py(cy), px(x), py(y))
    fun Path.cb(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) =
        cubicTo(px(x1), py(y1), px(x2), py(y2), px(x), py(y))
    fun bar(x0: Float, y0: Float, x1: Float, y1: Float, wd: Float, col: Int) {
        line.strokeWidth = wd * s; line.color = col
        c.drawLine(px(x0), py(y0), px(x1), py(y1), line)
    }
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }

    val white = l.shade(0xFFF4F7FA.toInt())
    val navy = l.shade(0xFF16233A.toInt())
    val red = l.shade(0xFFD62828.toInt())
    val cream = l.shade(0xFFF1E6CF.toInt())
    val skin = l.shade(0xFFE8B48C.toInt())
    val steel = l.shade(0xFF2B2F36.toInt())

    // ------------------------------------------------------------ outboard (behind the hull)
    val leg = Path().apply { m(-131f, -30f); l(-121f, -30f); l(-119f, 13f); l(-128f, 15f); close() }
    shader = LinearGradient(0f, py(-30f), 0f, py(15f), l.shade(0xFF50565F.toInt()), l.shade(0xFF22262C.toInt()), Shader.TileMode.CLAMP)
    c.drawPath(leg, fill)
    shader = null
    fill.color = alpha(Color.BLACK, 110)   // the propeller, a blur of blades under the surface
    c.drawOval(px(-141f), py(4f), px(-133f), py(22f), fill)
    fill.color = l.shade(0xFF2B2F36.toInt())
    c.drawOval(px(-140f), py(7f), px(-120f), py(15f), fill)   // gearcase
    val cowl = Path().apply {
        m(-118f, -58f); q(-142f, -60f, -143f, -42f); l(-137f, -29f); l(-118f, -29f); close()
    }
    shader = LinearGradient(px(-143f), 0f, px(-118f), 0f, lighten(white, 0.1f), darken(white, 0.3f), Shader.TileMode.CLAMP)
    c.drawPath(cowl, fill)
    shader = null
    fill.color = navy
    c.drawPath(Path().apply { m(-143f, -44f); l(-118f, -41f); l(-118f, -37f); l(-142f, -39f); close() }, fill)
    fill.color = steel
    c.drawRect(px(-123f), py(-34f), px(-116f), py(-20f), fill)   // transom bracket

    // ------------------------------------------------------------ hull
    val hull = Path().apply {
        m(-118f, 0f); l(-118f, -34f)
        cb(-70f, -33f, -10f, -28f, 40f, -30f)
        cb(85f, -33f, 112f, -40f, 134f, -48f)
        cb(137f, -38f, 118f, -18f, 92f, -11f)
        cb(60f, -4f, 20f, -1f, -30f, 0f)
        close()
    }
    shader = LinearGradient(0f, py(-50f), 0f, py(0f), lighten(white, 0.12f), darken(white, 0.3f), Shader.TileMode.CLAMP)
    c.drawPath(hull, fill)
    shader = null
    c.save()
    c.clipPath(hull)
    // Bottom paint, with a thin white boot stripe above it.
    fill.color = navy
    c.drawPath(Path().apply { m(-120f, 3f); l(-120f, -8f); cb(-40f, -8f, 20f, -9f, 70f, -14f); l(125f, -30f); l(140f, 3f); close() }, fill)
    edge.color = alpha(white, 230); edge.strokeWidth = 1.3f * s
    c.drawPath(Path().apply { m(-118f, -8.5f); cb(-40f, -8.5f, 20f, -9.5f, 70f, -14.5f); l(112f, -27f) }, edge)
    // The red swoosh down the side, tapering towards the bow, with a navy pin-stripe under it.
    fill.color = red
    c.drawPath(Path().apply {
        m(-117f, -26f); cb(-40f, -24f, 40f, -26f, 114f, -39f)
        cb(40f, -17f, -40f, -16f, -117f, -16f); close()
    }, fill)
    edge.color = navy; edge.strokeWidth = 1.1f * s
    c.drawPath(Path().apply { m(-117f, -13.5f); cb(-40f, -13.5f, 40f, -14.5f, 104f, -25f) }, edge)
    // Portholes.
    fill.color = alpha(navy, 220)
    for (i in 0 until 3) c.drawOval(px(-8f + i * 22f), py(-8f - 0f), px(6f + i * 22f), py(-3f), fill)
    // Light from the sun's side and gloss along the sheer.
    shader = LinearGradient(
        px(-118f), 0f, px(134f), 0f,
        if (sunOnLeft) alpha(Color.WHITE, 60) else alpha(Color.BLACK, 70),
        if (sunOnLeft) alpha(Color.BLACK, 70) else alpha(Color.WHITE, 60), Shader.TileMode.CLAMP,
    )
    c.drawRect(px(-120f), py(-52f), px(140f), py(4f), fill)
    shader = null
    edge.color = alpha(Color.WHITE, 120); edge.strokeWidth = 2f * s
    c.drawPath(Path().apply { m(-112f, -31f); cb(-70f, -30f, -10f, -25f, 40f, -27f); cb(85f, -30f, 108f, -37f, 126f, -44f) }, edge)
    c.restore()
    // Rubrail along the sheer, and the hull's outline.
    edge.color = l.shade(0xFF8A9199.toInt()); edge.strokeWidth = 1.6f * s
    c.drawPath(Path().apply { m(-118f, -34f); cb(-70f, -33f, -10f, -28f, 40f, -30f); cb(85f, -33f, 112f, -40f, 134f, -48f) }, edge)
    edge.color = alpha(Color.BLACK, 70); edge.strokeWidth = 1f * s
    c.drawPath(hull, edge)

    // ------------------------------------------------------------ cockpit
    // Rear bench, then the passenger sitting on it with hair blown back by the wind.
    shader = LinearGradient(0f, py(-52f), 0f, py(-33f), lighten(cream, 0.1f), darken(cream, 0.2f), Shader.TileMode.CLAMP)
    c.drawRoundRect(px(-108f), py(-52f), px(-52f), py(-33f), 5f * s, 5f * s, fill)
    shader = null
    edge.color = alpha(darken(cream, 0.4f), 150); edge.strokeWidth = 0.9f * s
    c.drawLine(px(-104f), py(-42f), px(-56f), py(-42f), edge)
    bar(-68f, -37f, -69f, -58f, 11f, l.shade(0xFFFF7A66.toInt()))        // torso
    bar(-68f, -57f, -53f, -49f, 3.6f, skin)                              // arm resting forward
    fill.color = skin
    c.drawCircle(px(-68f), py(-67.5f), 6.2f * s, fill)
    fill.color = l.shade(0xFF3A2A1E.toInt())
    c.drawArc(px(-75f), py(-75f), px(-61f), py(-62f), 150f, 220f, true, fill)
    line.strokeWidth = 1.5f * s; line.color = alpha(l.shade(0xFF3A2A1E.toInt()), 200)
    for (i in 0 until 4) c.drawLine(px(-72f), py(-70f + i * 2.6f), px(-91f - i * 2f), py(-72f + i * 3.2f), line)

    // Driver's seat, the driver gripping the wheel, in a cap and sunglasses.
    shader = LinearGradient(0f, py(-54f), 0f, py(-33f), lighten(cream, 0.1f), darken(cream, 0.2f), Shader.TileMode.CLAMP)
    c.drawRoundRect(px(-28f), py(-54f), px(-18f), py(-33f), 4f * s, 4f * s, fill)
    c.drawRoundRect(px(-28f), py(-40f), px(2f), py(-33f), 3f * s, 3f * s, fill)
    shader = null
    bar(-13f, -38f, -10f, -58f, 11.5f, l.shade(0xFF2E86DE.toInt()))      // torso in a blue shirt
    bar(-11f, -56f, 15f, -49f, 3.6f, skin)                               // arm to the wheel
    fill.color = skin
    c.drawCircle(px(-9f), py(-68f), 6.4f * s, fill)
    fill.color = l.shade(0xFFFFFFFF.toInt())
    c.drawArc(px(-16f), py(-76f), px(-2f), py(-62f), 180f, 180f, true, fill)
    c.drawRect(px(-5f), py(-69.5f), px(6f), py(-67.5f), fill)            // cap peak
    fill.color = l.shade(0xFF111111.toInt())
    c.drawOval(px(-5.5f), py(-69f), px(-0.5f), py(-66f), fill)            // sunglasses
    bar(11f, -57f, 20f, -41f, 2.6f, steel)                               // steering wheel
    fill.color = steel
    c.drawCircle(px(15.5f), py(-49f), 2f * s, fill)

    // Console and dash.
    val console = Path().apply { m(22f, -31f); l(26f, -41f); l(46f, -39f); l(52f, -31f); close() }
    shader = LinearGradient(0f, py(-41f), 0f, py(-31f), l.shade(0xFF4B525C.toInt()), l.shade(0xFF22262C.toInt()), Shader.TileMode.CLAMP)
    c.drawPath(console, fill)
    shader = null
    fill.color = l.shade(0xFF9EE6FF.toInt())
    c.drawCircle(px(33f), py(-37f), 1.8f * s, fill)
    c.drawCircle(px(39f), py(-36.5f), 1.8f * s, fill)

    // Windshield: raked back, tinted glass reflecting the sky, in a dark frame.
    val glass = Path().apply { m(36f, -32f); l(15.5f, -61f); l(9f, -61f); l(29f, -32f); close() }
    shader = LinearGradient(
        0f, py(-61f), 0f, py(-32f),
        alpha(SkyGradient.blend(l.skyBottom, 0xFF5F95C4.toInt(), 0.45f), 175), alpha(l.shade(0xFF1D3550.toInt()), 205),
        Shader.TileMode.CLAMP,
    )
    c.drawPath(glass, fill)
    shader = null
    edge.color = alpha(Color.WHITE, 110); edge.strokeWidth = 2f * s
    c.drawLine(px(25f), py(-54f), px(31.5f), py(-40f), edge)             // glare streak
    edge.color = steel; edge.strokeWidth = 2.3f * s
    c.drawPath(glass, edge)
    edge.color = l.shade(0xFFC7CCD2.toInt()); edge.strokeWidth = 1.2f * s
    c.drawLine(px(9f), py(-61.5f), px(15.5f), py(-61.5f), edge)          // chrome top rail

    // ------------------------------------------------------------ fittings
    fill.color = l.shade(0xFFC7CCD2.toInt())
    c.drawRoundRect(px(112f), py(-45f), px(120f), py(-42.5f), 1f * s, 1f * s, fill)   // bow cleat
    fill.color = l.shade(0xFFE74C3C.toInt())
    c.drawCircle(px(123f), py(-46f), 1.8f * s, fill)                                // bow light
    bar(-111f, -34f, -111f, -64f, 1.1f, steel)                                     // flag staff
    fill.color = red
    c.drawPath(Path().apply { m(-111f, -64f); l(-125f, -60f); l(-111f, -56f); close() }, fill)
    if (l.windowsLit) {
        fill.color = 0xFFFFEAA7.toInt()
        c.drawCircle(px(-111f), py(-66f), 2.2f * s, fill)                           // stern light
    }
    return bmp
}

/**
 * The water round the boat, animated. Draw [drawTrail] first (it sits behind the boat), then the boat
 * (cut off at the waterline), then [drawFront]. All of them work in a frame whose origin is the boat's
 * waterline at its centre. Nothing here allocates.
 */
internal class BoatWake(private val st: BoatStyle = BoatStyle.RUNABOUT) {
    private val blob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val mirror = Paint(Paint.FILTER_BITMAP_FLAG)
    private var foam = Color.WHITE
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val poolPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cone = Path()
    private var lightFor = 0f

    fun applyLighting(l: Lighting) { foam = l.shade(0xFFFFFFFF.toInt()) }

    /** Reflection and the wake spreading out behind the boat. */
    fun drawTrail(canvas: Canvas, boat: Bitmap, t: Float, rough: Float, unit: Float) {
        val bw = boat.width.toFloat()
        val wl = boat.height * st.waterline
        // The hull and crew upside down in the water, faint.
        canvas.save()
        canvas.clipRect(-bw, 0f, bw, bw * 0.3f)
        canvas.scale(1f, -0.5f)
        mirror.alpha = 46
        canvas.drawBitmap(boat, -bw * 0.5f, -wl, mirror)
        canvas.restore()

        val stern = st.stern * bw
        val len = st.wakeLen * bw
        val stretch = st.wakeLen / 2.1f
        val spread = st.spread        // the wake is a flat wedge: we look along the water, not down on it
        line.color = foam
        line.strokeWidth = 2.2f * unit
        for (side in intArrayOf(-1, 1)) {
            var px = stern; var py = 0f
            val pts = 28
            for (k in 1..pts) {
                val d = len * k / pts
                val x = stern - d
                val y = side * (d * spread + 0.012f * bw) + sin(d * 0.02f / unit - t * 2f + side) * 0.004f * bw
                val noise = sin(d * 0.045f / unit + side * 1.7f + t * 0.9f) + sin(d * 0.11f / unit - t * 0.7f)
                if (noise > -0.9f) {
                    line.alpha = (235f * (1f - d / len).pow(1.2f) * (0.65f + 0.35f * rough)).toInt().coerceIn(0, 255)
                    canvas.drawLine(px, py, x, y, line)
                }
                px = x; py = y
            }
        }
        // Transverse waves: shallow chevrons drifting back inside the wedge.
        val drift = fract(t * 0.25f)
        line.strokeWidth = 1.2f * unit
        for (m in 0 until 6) {
            val d = (m + drift) * 0.3f * bw * stretch
            val x = stern - 0.1f * bw - d
            val e = d * spread * 0.8f
            val a = (80f * (1f - d / (1.9f * bw * stretch)) * (d / (0.2f * bw * stretch)).coerceAtMost(1f)).toInt().coerceIn(0, 255)
            line.alpha = a
            canvas.drawLine(x + 0.05f * bw, -e, x, 0f, line)
            canvas.drawLine(x, 0f, x + 0.05f * bw, e, line)
        }
        // Churned foam down the middle: big dense bubbles at the stern, thinning out into the distance.
        blob.color = foam
        val n = 34
        for (i in 0 until n) {
            val f = (i + 0.5f) / n
            val d = f.pow(1.25f) * 1.5f * bw * stretch
            val jy = fract(sin(i * 12.9898f) * 43758.547f) - 0.5f
            val y = jy * 2f * (0.012f + 0.06f * f) * bw + sin(t * 1.6f + i) * 0.004f * bw
            val r = unit * (2.2f + 7f * f) * (0.7f + 0.3f * sin(t * 2.3f + i * 1.9f))
            blob.alpha = (210f * (1f - f) * (1f - f) * (0.55f + 0.28f * rough)).toInt().coerceIn(0, 255)
            canvas.drawOval(stern - d - r * 1.8f, y - r * 0.55f, stern - d + r * 1.8f, y + r * 0.55f, blob)
        }
    }

    /** Foam along the wetted hull, spray off the bow and the rooster tail behind the engine. */
    fun drawFront(canvas: Canvas, bw: Float, t: Float, rough: Float, unit: Float) {
        blob.color = foam
        // The hull's wetted length: a bright, broken band of foam where it meets the water.
        blob.alpha = 150
        val wf = st.wetFrom * bw; val wt = st.wetTo * bw
        canvas.drawOval(wf, -0.012f * bw, wt, 0.03f * bw, blob)
        line.color = foam
        line.strokeWidth = 2.2f * unit
        var px = wf; var py = 0f
        for (k in 1..16) {
            val x = wf + (wt - wf) * k / 16f
            val y = sin(t * 5f + k * 1.3f) * 0.004f * bw
            line.alpha = (230f * (1f - 0.5f * k / 16f)).toInt()
            canvas.drawLine(px, py, x, y, line)
            px = x; py = y
        }
        // Bow spray: droplets thrown up and back where the bow cuts the water.
        val sprayN = 28
        val sx = st.sprayX * bw
        for (i in 0 until sprayN) {
            val ph = fract(t * (1.2f + 0.5f * rough) + i / sprayN.toFloat())
            val lift = 0.5f + 0.5f * fract(sin(i * 7.31f) * 9631.7f)
            val x = sx - ph * 0.27f * bw
            val y = -sin(ph * 3.1416f) * 0.075f * bw * st.spray * lift * (0.7f + 0.3f * rough)
            blob.alpha = (200f * (1f - ph)).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, unit * (0.9f + 1.8f * (1f - ph)) * st.spray.coerceIn(0.6f, 1.4f), blob)
        }
        // A soft mist of fine spray hanging over the bow wave and the engine, so the droplets read as water.
        val mist = st.spray.coerceAtMost(1.4f)
        for (j in 0 until 4) {
            blob.alpha = (55f * mist * (1f - j * 0.2f) * (0.7f + 0.3f * rough)).toInt().coerceIn(0, 255)
            canvas.drawOval(sx - 0.05f * bw - j * 0.06f * bw, -0.05f * bw * mist, sx + 0.05f * bw - j * 0.06f * bw, 0.012f * bw, blob)
            if (st.tail > 0.3f) {
                val tx = st.tailX * bw
                canvas.drawOval(tx - 0.08f * bw - j * 0.07f * bw, -0.07f * bw * st.tail, tx + 0.03f * bw - j * 0.07f * bw, 0.012f * bw, blob)
            }
        }
        // Rooster tail: a plume of water kicked up by the propeller, arcing back and falling.
        if (st.tail <= 0.02f) return
        val tailN = (30 * st.tail.coerceIn(0.2f, 1.2f)).toInt()
        for (i in 0 until tailN) {
            val ph = fract(t * 2.2f + i / tailN.toFloat())
            val lift = 0.5f + 0.5f * fract(sin(i * 5.17f) * 4375.3f)
            val x = st.tailX * bw - ph * 0.3f * bw
            val y = -sin(ph * 3.1416f) * 0.1f * bw * st.tail * lift * (0.7f + 0.3f * rough)
            blob.alpha = (215f * (1f - ph * 0.8f)).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, unit * (1.1f + 2.6f * (1f - ph)), blob)
        }
    }

    /**
     * The bow lamp: a bulb with a halo, a narrow cone of light that fades with distance and tilts down
     * to meet the sea, and the pool of light it makes on the water, sparkling. [strength] is 0..1.
     */
    fun drawLight(canvas: Canvas, bw: Float, pitch: Float, strength: Float, t: Float, unit: Float) {
        if (strength <= 0.01f) return
        if (lightFor != bw) {
            lightFor = bw
            val warm = 0xFFF0C4
            beamPaint.shader = LinearGradient(
                0f, 0f, 2.2f * bw, 0f, intArrayOf((0xC0 shl 24) or warm, (0x44 shl 24) or warm, warm),
                floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP,
            )
            glowPaint.shader = RadialGradient(
                0f, 0f, 0.06f * bw, intArrayOf(Color.WHITE, (0x99 shl 24) or warm, warm),
                floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP,
            )
            poolPaint.shader = RadialGradient(
                0f, 0f, 0.4f * bw, intArrayOf((0xB0 shl 24) or warm, (0x50 shl 24) or warm, warm),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
        }
        val lx = st.lampX * bw; val ly = st.lampY * bw
        val tilt = 11f

        // The cone, a few nested copies so its edges feather out, only ever above the water.
        canvas.save()
        canvas.clipRect(-bw, -3f * bw, 4f * bw, 0f)
        canvas.rotate(-pitch)
        canvas.translate(lx, ly)
        canvas.rotate(tilt)
        val len = 2.2f * bw
        beamPaint.alpha = (strength * 0.22f * 255f).toInt()
        for (g in 0 until 5) {
            val half = len * 0.1f * (1f - g * 0.2f)
            cone.rewind()
            cone.moveTo(0f, 0f); cone.lineTo(len, -half); cone.lineTo(len, half); cone.close()
            canvas.drawPath(cone, beamPaint)
        }
        canvas.restore()

        // The bulb and its halo.
        canvas.save()
        canvas.rotate(-pitch)
        canvas.translate(lx, ly)
        glowPaint.alpha = (strength * 255f).toInt()
        canvas.drawCircle(0f, 0f, 0.06f * bw, glowPaint)
        canvas.restore()

        // Where the beam lands on the sea (the boat's bow is raised, so it reaches further out).
        val net = tilt - pitch
        if (net < 1.5f) return
        val x = (lx + 0.16f * bw / tan(net * 0.017453f)).coerceAtMost(2.6f * bw)
        canvas.save()
        canvas.translate(x, 0.004f * bw)
        canvas.scale(1f, 0.11f)
        poolPaint.alpha = (strength * (0.8f + 0.2f * sin(t * 5f)) * 255f).toInt()
        canvas.drawCircle(0f, 0f, 0.4f * bw, poolPaint)
        canvas.restore()
        line.color = 0xFFFFF0C4.toInt()
        line.strokeWidth = 1.6f * unit
        for (i in 0 until 8) {
            val tw = sin(t * (2f + i * 0.3f) + i * 2f)
            if (tw < 0.2f) continue
            val dx = (fract(sin(i * 3.7f) * 1000f) - 0.5f) * 0.5f * bw
            val dy = (fract(sin(i * 9.1f) * 1000f) - 0.5f) * 0.03f * bw
            val half = unit * (4f + 7f * tw)
            line.alpha = (220f * strength * tw).toInt().coerceIn(0, 255)
            canvas.drawLine(x + dx - half, dy, x + dx + half, dy, line)
        }
    }

    private fun fract(v: Float) = v - floor(v)
}
