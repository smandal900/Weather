package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/*
 * The boats on the beach's sea. Each type is a side-on sprite, bow to the right, painted once, plus a
 * BoatStyle that says where its waterline is, how its wake and spray are shaped (BoatWake) and how it
 * crosses the sea (row, speed, how long it stays out of sight between passes).
 *
 *   racer    long, low offshore go-fast boat with a bubble canopy and twin engines: very fast, far out
 *   cruiser  white cabin cruiser with a flybridge, radar arch and bow rail: slow and stately
 *   runabout the original planing speed boat (BeachBoat.kt)
 *   rib      rigid inflatable with orange tubes, a console and a crew in life jackets
 *   jetski   a yellow personal watercraft with a rider and a passenger: quick, close in, big rooster tail
 */

internal class BoatStyle(
    val key: String,
    /** Where the waterline sits in the sprite, as a fraction of its height. */
    val waterline: Float,
    /** Stern, wetted hull, bow spray, prop wash and bow lamp, as fractions of the sprite's width from its centre. */
    val stern: Float, val wetFrom: Float, val wetTo: Float, val sprayX: Float, val tailX: Float,
    val lampX: Float, val lampY: Float,
    /** Strength of the rooster tail and bow spray (1 is the runabout), the wake's spread and its length in boat widths. */
    val tail: Float, val spray: Float, val spread: Float, val wakeLen: Float,
    /** Row on the sea (fraction of the screen height), speed (screen widths per second), the cycle it repeats over
     *  (screen widths, so more than 1.5 means it spends part of the time out of sight) and where in it we start. */
    val y: Float, val speed: Float, val span: Float, val start: Float,
    /** How much it bounces over the chop, how far its bow rides up (degrees) and how widely it weaves (screen heights). */
    val bob: Float, val pitch: Float, val weave: Float = 0f,
) {
    companion object {
        val RUNABOUT = BoatStyle(
            "boat", BOAT_WATERLINE, -0.41f, -0.44f, 0.14f, 0.15f, -0.45f, 0.42f, -0.16f,
            1f, 1f, 0.085f, 2.1f, 0.725f, 0.075f, 1.5f, 0f, 1f, 4.2f,
        )
        val RACER = BoatStyle(
            "racer", RACER_H_WL, -0.45f, -0.46f, -0.02f, 0.02f, -0.5f, 0.5f, -0.07f,
            1.25f, 1.1f, 0.07f, 3.2f, 0.685f, 0.17f, 3.4f, 1f, 0.7f, 6.5f,
        )
        val CRUISER = BoatStyle(
            "cruiser", CRUISER_WL, -0.4f, -0.4f, 0.35f, 0.32f, -0.42f, 0.44f, -0.17f,
            0.12f, 0.3f, 0.06f, 2.2f, 0.702f, 0.028f, 1.7f, 0.5f, 0.3f, 0.8f,
        )
        val RIB = BoatStyle(
            "rib", RIB_WL, -0.46f, -0.47f, 0.3f, 0.3f, -0.53f, 0.45f, -0.2f,
            0.85f, 1f, 0.09f, 3f, 0.752f, 0.055f, 2.2f, 1.1f, 1.3f, 3.5f, 0.002f,
        )
        val JETSKI = BoatStyle(
            "jetski", JETSKI_WL, -0.42f, -0.43f, 0.25f, 0.3f, -0.47f, 0.4f, -0.22f,
            1.6f, 1.3f, 0.1f, 4.2f, 0.778f, 0.12f, 2.8f, 0.2f, 1.7f, 6f, 0.007f,
        )

        /** Far to near, which is the order they are drawn in. */
        val FLEET = listOf(RACER, CRUISER, RUNABOUT, RIB, JETSKI)
    }
}

private const val JETSKI_H = 86f
private const val RACER_H = 84f
private const val RIB_H = 94f
private const val CRUISER_H = 140f
internal const val JETSKI_WL = 70f / JETSKI_H
internal const val RACER_H_WL = 64f / RACER_H
internal const val RIB_WL = 78f / RIB_H
internal const val CRUISER_WL = 130f / CRUISER_H

/** The pen the boat sprites are drawn with: coordinates are in design units, x from the centre, y up from the waterline. */
private class BoatPen(val k: PaintKit, val c: Canvas, val s: Float, val ox: Float, val oy: Float) {
    val l = k.l
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    fun px(x: Float) = ox + x * s
    fun py(y: Float) = oy + y * s
    fun Path.m(x: Float, y: Float) = moveTo(px(x), py(y))
    fun Path.l(x: Float, y: Float) = lineTo(px(x), py(y))
    fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadTo(px(cx), py(cy), px(x), py(y))
    fun Path.cb(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) =
        cubicTo(px(x1), py(y1), px(x2), py(y2), px(x), py(y))

    fun shade(col: Int) = l.shade(col)
    fun lite(col: Int, t: Float) = k.lighten(col, t)
    fun dim(col: Int, t: Float) = k.darken(col, t)
    fun a(col: Int, alpha: Int) = k.alpha(col, alpha)

    fun bar(x0: Float, y0: Float, x1: Float, y1: Float, wd: Float, col: Int) {
        k.line.strokeWidth = wd * s; k.line.color = col
        c.drawLine(px(x0), py(y0), px(x1), py(y1), k.line)
    }
    fun fillPath(p: Path, col: Int) { k.shader = null; k.fill.color = col; c.drawPath(p, k.fill) }
    fun grad(p: Path, y0: Float, y1: Float, top: Int, bottom: Int) {
        k.shader = LinearGradient(0f, py(y0), 0f, py(y1), top, bottom, Shader.TileMode.CLAMP)
        c.drawPath(p, k.fill); k.shader = null
    }
    fun disc(x: Float, y: Float, r: Float, col: Int) { k.shader = null; k.fill.color = col; c.drawCircle(px(x), py(y), r * s, k.fill) }
    fun oval(x0: Float, y0: Float, x1: Float, y1: Float, col: Int) { k.shader = null; k.fill.color = col; c.drawOval(px(x0), py(y0), px(x1), py(y1), k.fill) }
    fun rect(x0: Float, y0: Float, x1: Float, y1: Float, col: Int, r: Float = 0f) {
        k.shader = null; k.fill.color = col
        if (r > 0f) c.drawRoundRect(px(x0), py(y0), px(x1), py(y1), r * s, r * s, k.fill) else c.drawRect(px(x0), py(y0), px(x1), py(y1), k.fill)
    }
    fun stroke(p: Path, col: Int, wd: Float) { edge.color = col; edge.strokeWidth = wd * s; c.drawPath(p, edge) }
    fun streak(x0: Float, y0: Float, x1: Float, y1: Float, col: Int, wd: Float) {
        edge.color = col; edge.strokeWidth = wd * s; c.drawLine(px(x0), py(y0), px(x1), py(y1), edge)
    }

    /** Light from the sun's side, shade on the other, across a box. */
    fun sheen(x0: Float, y0: Float, x1: Float, y1: Float) {
        k.shader = LinearGradient(
            px(x0), 0f, px(x1), 0f,
            if (k.sunOnLeft) a(Color.WHITE, 60) else a(Color.BLACK, 70),
            if (k.sunOnLeft) a(Color.BLACK, 70) else a(Color.WHITE, 60), Shader.TileMode.CLAMP,
        )
        c.drawRect(px(x0), py(y0), px(x1), py(y1), k.fill)
        k.shader = null
    }

    /** Tinted glass reflecting the sky, or lit from inside after dark. */
    fun glass(p: Path, y0: Float, y1: Float) {
        val top = if (l.windowsLit) a(0xFFFFD27A.toInt(), 235) else a(k.mix(l.skyBottom, 0xFF5F95C4.toInt(), 0.45f), 175)
        val bottom = if (l.windowsLit) a(0xFFE8A64A.toInt(), 235) else a(shade(0xFF1D3550.toInt()), 205)
        grad(p, y0, y1, top, bottom)
    }

    /** A head with hair or a cap. */
    fun head(x: Float, y: Float, r: Float, skin: Int) = disc(x, y, r, skin)
    fun hair(x: Float, y: Float, r: Float, col: Int) {
        k.shader = null; k.fill.color = col
        c.drawArc(px(x - r * 1.1f), py(y - r * 1.2f), px(x + r * 1.1f), py(y + r * 1.0f), 150f, 220f, true, k.fill)
    }
    fun helmet(x: Float, y: Float, r: Float, col: Int, visor: Int) {
        k.shader = null; k.fill.color = col
        c.drawArc(px(x - r * 1.12f), py(y - r * 1.15f), px(x + r * 1.12f), py(y + r * 1.0f), 175f, 190f, true, k.fill)
        k.fill.color = visor
        c.drawOval(px(x + r * 0.05f), py(y - r * 0.4f), px(x + r * 1.05f), py(y + r * 0.2f), k.fill)
    }

    /** An outboard on a transom whose top is at ([tx], [ty]): leg, propeller, gearcase, cowling and a stripe. */
    fun outboard(tx: Float, ty: Float, cowl: Int, stripe: Int, dark: Float = 0f) {
        val body = if (dark > 0f) dim(cowl, dark) else cowl
        val leg = Path().apply { m(tx - 13f, ty + 4f); l(tx - 3f, ty + 4f); l(tx - 1f, ty + 47f); l(tx - 10f, ty + 49f); close() }
        grad(leg, ty + 4f, ty + 49f, dim(shade(0xFF50565F.toInt()), dark), dim(shade(0xFF22262C.toInt()), dark))
        oval(tx - 23f, ty + 38f, tx - 15f, ty + 56f, a(Color.BLACK, 110))
        oval(tx - 22f, ty + 41f, tx - 2f, ty + 49f, dim(shade(0xFF2B2F36.toInt()), dark))
        val cap = Path().apply { m(tx, ty - 24f); q(tx - 24f, ty - 26f, tx - 25f, ty - 8f); l(tx - 19f, ty + 5f); l(tx, ty + 5f); close() }
        k.shader = LinearGradient(px(tx - 25f), 0f, px(tx), 0f, lite(body, 0.1f), dim(body, 0.3f), Shader.TileMode.CLAMP)
        c.drawPath(cap, k.fill); k.shader = null
        fillPath(Path().apply { m(tx - 25f, ty - 10f); l(tx, ty - 7f); l(tx, ty - 3f); l(tx - 24f, ty - 5f); close() }, dim(stripe, dark))
        rect(tx - 5f, ty, tx + 2f, ty + 14f, dim(shade(0xFF2B2F36.toInt()), dark))
    }
}

// ---------------------------------------------------------------------------------------------
// Jet ski
// ---------------------------------------------------------------------------------------------

internal fun PaintKit.paintJetSki(): Bitmap? {
    val s = w * 0.001f * 1.3f
    val bw = (150 * s).toInt().coerceAtLeast(40)
    val bh = (JETSKI_H * s).toInt().coerceAtLeast(24)
    val (c, bmp) = newSprite(bw, bh) ?: return null
    val p = BoatPen(this, c, s, bw / 2f, bh * JETSKI_WL)
    with(p) {
        val yellow = shade(0xFFFFC21A.toInt()); val navy = shade(0xFF16233A.toInt()); val white = shade(0xFFF4F7FA.toInt())
        val steel = shade(0xFF2B2F36.toInt()); val skin = shade(0xFFD9A07A.toInt()); val black = shade(0xFF1B1D22.toInt())

        // The jet nozzle at the stern.
        fillPath(Path().apply { m(-71f, -9f); l(-62f, -9f); l(-62f, -2f); l(-71f, -3f); close() }, steel)

        // Hull and deck in one: a low stern, a raised seat and console, a sloping hood and a pointed bow.
        val body = Path().apply {
            m(-64f, -4f); l(-63f, -19f)
            cb(-46f, -21f, -26f, -24f, -13f, -29f)
            cb(-2f, -36f, 14f, -33f, 24f, -32f)
            cb(40f, -31f, 54f, -26f, 67f, -15f)
            cb(69f, -10f, 66f, -5f, 58f, -2f)
            cb(30f, 2f, -20f, 2f, -64f, -4f)
            close()
        }
        grad(body, -36f, 0f, lite(yellow, 0.18f), dim(yellow, 0.3f))
        c.save()
        c.clipPath(body)
        fillPath(Path().apply { m(-70f, 4f); l(-70f, -8f); cb(-20f, -9f, 30f, -10f, 76f, -16f); l(76f, 4f); close() }, navy)
        stroke(Path().apply { m(-66f, -8.5f); cb(-20f, -9.5f, 30f, -10.5f, 72f, -17f) }, a(white, 230), 1.3f)
        fillPath(Path().apply { m(-63f, -18f); cb(-25f, -17f, 15f, -21f, 68f, -24f); cb(20f, -19f, -25f, -12f, -63f, -12f); close() }, black)
        stroke(Path().apply { m(-63f, -10.5f); cb(-25f, -10.5f, 15f, -14f, 60f, -18f) }, a(white, 220), 1f)
        sheen(-70f, -40f, 76f, 4f)
        stroke(Path().apply { m(-60f, -19f); cb(-46f, -21f, -26f, -24f, -13f, -29f) }, a(Color.WHITE, 120), 1.6f)
        c.restore()
        stroke(body, a(Color.BLACK, 70), 1f)

        // The seat.
        val seat = Path().apply { m(-54f, -20f); cb(-49f, -27f, -27f, -29f, -14f, -32f); l(-11f, -27f); cb(-26f, -24f, -46f, -22f, -53f, -18f); close() }
        grad(seat, -32f, -18f, shade(0xFF3A3D44.toInt()), black)
        stroke(Path().apply { m(-50f, -22f); cb(-40f, -26f, -26f, -27f, -15f, -30f) }, a(Color.WHITE, 70), 0.9f)

        // The passenger behind, hair in a ponytail, arms round the rider's waist.
        bar(-46f, -27f, -48f, -46f, 10f, shade(0xFF2E86DE.toInt()))
        bar(-46f, -41f, -31f, -38f, 3.3f, skin)
        head(-48f, -55f, 5.8f, skin)
        hair(-48f, -55f, 5.8f, shade(0xFF3A2A1E.toInt()))
        bar(-53f, -57f, -60f, -52f, 2.2f, shade(0xFF3A2A1E.toInt()))

        // The rider, crouched over the bars in an orange life vest and a white helmet.
        bar(-32f, -27f, -6f, -22f, 5.5f, shade(0xFF1F2A44.toInt()))
        bar(-6f, -22f, 3f, -20.5f, 3.6f, skin)
        bar(-26f, -30f, -13f, -52f, 11.5f, shade(0xFFFF6B1A.toInt()))
        bar(-22f, -34f, -11f, -49f, 1.2f, a(white, 160))
        head(-8f, -61.5f, 6.4f, skin)
        helmet(-8f, -61.5f, 6.4f, white, shade(0xFF111111.toInt()))

        // Windscreen, steering mast and bars, then the arm gripping them.
        val screen = Path().apply { m(17f, -33f); l(24f, -45f); l(33f, -43.5f); l(32f, -33f); close() }
        glass(screen, -45f, -33f)
        stroke(screen, steel, 1.6f)
        bar(10f, -34f, 10.5f, -49f, 3.2f, steel)
        bar(8f, -49.5f, 15f, -49.5f, 3.6f, black)
        bar(-13f, -49f, 9f, -48.5f, 3.6f, skin)
        if (l.windowsLit) disc(61f, -20f, 2.2f, 0xFFFFEAA7.toInt())
    }
    return bmp
}

// ---------------------------------------------------------------------------------------------
// Offshore racer
// ---------------------------------------------------------------------------------------------

internal fun PaintKit.paintRacer(): Bitmap? {
    val s = w * 0.001f * 0.7f
    val bw = (340 * s).toInt().coerceAtLeast(80)
    val bh = (RACER_H * s).toInt().coerceAtLeast(24)
    val (c, bmp) = newSprite(bw, bh) ?: return null
    val p = BoatPen(this, c, s, bw / 2f, bh * RACER_H_WL)
    with(p) {
        val white = shade(0xFFF4F7FA.toInt()); val blue = shade(0xFF0B3C8C.toInt()); val orange = shade(0xFFFF6A13.toInt())
        val steel = shade(0xFF2B2F36.toInt()); val skin = shade(0xFFE8B48C.toInt())

        // Twin outboards, the far one dimmer.
        outboard(-160f, -24f, white, orange, 0.3f)
        outboard(-153f, -22f, white, orange)

        val hull = Path().apply {
            m(-152f, 0f); l(-153f, -22f)
            cb(-100f, -22f, -20f, -21f, 50f, -23f)
            cb(110f, -26f, 160f, -29f, 186f, -21f)
            cb(191f, -15f, 172f, -6f, 138f, -1f)
            cb(100f, 0f, 40f, 0f, -20f, 0f)
            close()
        }
        grad(hull, -30f, 0f, lite(white, 0.12f), dim(white, 0.28f))
        c.save()
        c.clipPath(hull)
        fillPath(Path().apply { m(-156f, 3f); l(-156f, -9f); cb(-60f, -9f, 40f, -10f, 100f, -12f); l(196f, -18f); l(196f, 3f); close() }, blue)
        fillPath(Path().apply { m(-153f, -19.5f); cb(-60f, -19f, 60f, -21f, 180f, -23f); cb(80f, -13f, -60f, -12f, -153f, -11f); close() }, orange)
        stroke(Path().apply { m(-153f, -9.5f); cb(-60f, -9.5f, 40f, -10.5f, 120f, -14f) }, a(white, 230), 1.2f)
        disc(-30f, -14.5f, 6.5f, a(white, 240))
        bar(-34f, -18.5f, -26f, -18.5f, 1.7f, blue)
        bar(-26f, -18.5f, -31f, -10.5f, 1.7f, blue)
        sheen(-156f, -34f, 196f, 4f)
        stroke(Path().apply { m(-146f, -21f); cb(-100f, -20.5f, -20f, -19.5f, 50f, -21.5f); cb(110f, -24f, 150f, -27f, 176f, -21f) }, a(Color.WHITE, 120), 1.8f)
        c.restore()
        stroke(hull, a(Color.BLACK, 70), 1f)

        // The engine hatch, raised over the stern, with louvres.
        val hatch = Path().apply { m(-146f, -22f); cb(-130f, -33f, -90f, -35f, -64f, -23f); close() }
        grad(hatch, -35f, -22f, lite(white, 0.1f), dim(white, 0.3f))
        for (i in 0 until 4) streak(-124f + i * 12f, -29f + i * 0.4f, -120f + i * 12f, -23f, a(steel, 170), 1.1f)
        stroke(hatch, a(Color.BLACK, 80), 1f)

        // Two crew in helmets under the bubble canopy.
        for (hx in floatArrayOf(30f, 53f)) {
            bar(hx, -23f, hx, -32f, 9f, shade(0xFF1F2A44.toInt()))
            head(hx, -38f, 5.4f, skin)
            helmet(hx, -38f, 5.4f, if (hx < 40f) white else orange, shade(0xFF111111.toInt()))
        }
        val canopy = Path().apply { m(2f, -22f); cb(10f, -48f, 52f, -58f, 86f, -42f); cb(98f, -35f, 106f, -28f, 112f, -22f); close() }
        glass(canopy, -56f, -22f)
        streak(22f, -43f, 40f, -52f, a(Color.WHITE, 110), 2.2f)
        stroke(canopy, steel, 2.2f)
        streak(44f, -54f, 46f, -22.5f, a(steel, 200), 1.6f)
        if (l.windowsLit) disc(-150f, -26f, 2.2f, 0xFFFFEAA7.toInt())
    }
    return bmp
}

// ---------------------------------------------------------------------------------------------
// Rigid inflatable
// ---------------------------------------------------------------------------------------------

internal fun PaintKit.paintRib(): Bitmap? {
    val s = w * 0.001f * 0.95f
    val bw = (215 * s).toInt().coerceAtLeast(60)
    val bh = (RIB_H * s).toInt().coerceAtLeast(24)
    val (c, bmp) = newSprite(bw, bh) ?: return null
    val p = BoatPen(this, c, s, bw / 2f, bh * RIB_WL)
    with(p) {
        val tube = shade(0xFFFF8A1F.toInt()); val navy = shade(0xFF16233A.toInt()); val steel = shade(0xFF2B2F36.toInt())
        val skin = shade(0xFFE8B48C.toInt()); val white = shade(0xFFF4F7FA.toInt())

        outboard(-96f, -26f, shade(0xFF30343B.toInt()), tube)

        // The rigid hull below the tubes.
        val hull = Path().apply { m(-92f, 0f); l(-96f, -16f); cb(-40f, -17f, 40f, -18f, 88f, -26f); cb(94f, -14f, 80f, -4f, 52f, 0f); close() }
        grad(hull, -26f, 0f, shade(0xFF5A6270.toInt()), shade(0xFF1F2430.toInt()))
        stroke(Path().apply { m(-92f, -4f); cb(-30f, -5f, 30f, -6f, 70f, -9f) }, a(white, 200), 1.1f)

        // The console and its windscreen, then the crew, then the tube in front of them.
        val console = Path().apply { m(-8f, -33f); l(-6f, -48f); l(14f, -48f); l(18f, -33f); close() }
        grad(console, -48f, -33f, shade(0xFF4B525C.toInt()), shade(0xFF22262C.toInt()))
        val screen = Path().apply { m(-6f, -48f); l(-12f, -66f); l(-4f, -67f); l(8f, -48f); close() }
        glass(screen, -67f, -48f)
        stroke(screen, steel, 1.8f)

        // Driver standing behind the console, in a navy jacket and a white helmet.
        bar(-22f, -34f, -22f, -58f, 11f, shade(0xFF1F3C88.toInt()))
        bar(-20f, -54f, -4f, -47f, 3.5f, skin)
        head(-22f, -68f, 6.2f, skin)
        helmet(-22f, -68f, 6.2f, white, shade(0xFF111111.toInt()))
        // Two passengers on the bench, in a yellow and a red life jacket; one is waving.
        bar(-62f, -30f, -62f, -50f, 11f, shade(0xFFFFD60A.toInt()))
        head(-62f, -59f, 6f, skin)
        hair(-62f, -59f, 6f, shade(0xFF3A2A1E.toInt()))
        bar(-60f, -46f, -52f, -45f, 3.2f, skin)
        bar(-80f, -30f, -80f, -49f, 11f, shade(0xFFE63946.toInt()))
        head(-80f, -58f, 6f, skin)
        hair(-80f, -58f, 6f, shade(0xFF8A5A2B.toInt()))
        bar(-78f, -46f, -72f, -63f, 3.2f, skin)

        // The tubes: a fat sausage along the sheer, curving up to a rounded bow, with a highlight and shadow.
        bar(-90f, -27f, -10f, -26f, 17f, dim(tube, 0.15f))
        bar(-10f, -26f, 94f, -35f, 17f, dim(tube, 0.15f))
        bar(-90f, -32f, -10f, -31f, 5f, a(lite(tube, 0.5f), 170))
        bar(-10f, -31f, 92f, -40f, 5f, a(lite(tube, 0.5f), 170))
        bar(-88f, -21f, -10f, -20f, 4f, a(Color.BLACK, 70))
        bar(-10f, -20f, 92f, -29f, 4f, a(Color.BLACK, 70))
        sheen(-100f, -45f, 104f, -10f)
        // Grab line along the tube.
        stroke(Path().apply { m(-84f, -26f); q(-45f, -20f, -8f, -25f); q(40f, -26f, 86f, -33f) }, a(navy, 200), 0.9f)
        if (l.windowsLit) disc(98f, -36f, 2.2f, 0xFFFFEAA7.toInt())
    }
    return bmp
}

// ---------------------------------------------------------------------------------------------
// Cabin cruiser
// ---------------------------------------------------------------------------------------------

internal fun PaintKit.paintCruiser(): Bitmap? {
    val s = w * 0.001f * 0.8f
    val bw = (336 * s).toInt().coerceAtLeast(80)
    val bh = (CRUISER_H * s).toInt().coerceAtLeast(30)
    val (c, bmp) = newSprite(bw, bh) ?: return null
    val p = BoatPen(this, c, s, bw / 2f, bh * CRUISER_WL)
    with(p) {
        val white = shade(0xFFF6F8FA.toInt()); val navy = shade(0xFF16233A.toInt()); val red = shade(0xFFD62828.toInt())
        val steel = shade(0xFF2B2F36.toInt()); val chrome = shade(0xFFC7CCD2.toInt()); val skin = shade(0xFFE8B48C.toInt())
        val teak = shade(0xFFA87B4F.toInt())

        // Swim platform and the flag at the stern, behind the hull.
        rect(-140f, -7f, -118f, -4f, teak)
        bar(-126f, -31f, -126f, -72f, 1.1f, steel)
        fillPath(Path().apply { m(-126f, -72f); l(-141f, -68f); l(-126f, -64f); close() }, red)

        // Hull.
        val hull = Path().apply {
            m(-130f, 0f); l(-132f, -30f)
            cb(-60f, -28f, 20f, -26f, 100f, -34f)
            cb(130f, -38f, 150f, -46f, 166f, -52f)
            cb(160f, -34f, 140f, -14f, 110f, -6f)
            cb(60f, -1f, 0f, 0f, -110f, 0f)
            close()
        }
        grad(hull, -52f, 0f, lite(white, 0.12f), dim(white, 0.26f))
        c.save()
        c.clipPath(hull)
        fillPath(Path().apply { m(-136f, 3f); l(-136f, -8f); cb(-40f, -7f, 40f, -9f, 110f, -14f); l(170f, -24f); l(170f, 3f); close() }, navy)
        stroke(Path().apply { m(-134f, -8.5f); cb(-40f, -7.5f, 40f, -9.5f, 110f, -14.5f) }, a(white, 230), 1.3f)
        stroke(Path().apply { m(-132f, -25f); cb(-60f, -23f, 20f, -21f, 100f, -29f); cb(130f, -33f, 150f, -40f, 168f, -47f) }, navy, 3.5f)
        for (i in 0 until 5) {
            oval(-76f + i * 24f, -17f, -69f + i * 24f, -11f, a(navy, 230))
            stroke(Path().apply { addOval(px(-76f + i * 24f), py(-17f), px(-69f + i * 24f), py(-11f), Path.Direction.CW) }, a(chrome, 200), 0.8f)
        }
        sheen(-136f, -56f, 170f, 4f)
        c.restore()
        stroke(Path().apply { m(-132f, -30f); cb(-60f, -28f, 20f, -26f, 100f, -34f); cb(130f, -38f, 150f, -46f, 166f, -52f) }, shade(0xFF8A9199.toInt()), 1.6f)
        stroke(hull, a(Color.BLACK, 70), 1f)

        // Deckhouse with a band of windows.
        val house = Path().apply { m(-100f, -30f); l(-97f, -60f); cb(-40f, -67f, 20f, -65f, 62f, -58f); l(84f, -31f); close() }
        grad(house, -66f, -30f, lite(white, 0.12f), dim(white, 0.22f))
        val windows = Path().apply { m(-90f, -57f); cb(-40f, -62f, 18f, -60f, 56f, -54f); l(70f, -41f); cb(20f, -43f, -40f, -44f, -92f, -44f); close() }
        glass(windows, -62f, -42f)
        val dx = floatArrayOf(-62f, -26f, 8f, 38f); val dt = floatArrayOf(-59f, -61f, -60f, -56f); val db = floatArrayOf(-44f, -44f, -43f, -42f)
        for (i in dx.indices) bar(dx[i], dt[i], dx[i], db[i], 1.5f, white)
        stroke(windows, a(steel, 200), 1.2f)
        stroke(house, a(Color.BLACK, 70), 1f)
        sheen(-100f, -70f, 84f, -30f)

        // Flybridge: a low wall, the people behind it, the windscreen and the hardtop on a post.
        bar(-74f, -98f, -72f, -74f, 3f, white)
        bar(-4f, -73f, -4f, -83f, 9.5f, shade(0xFF2A9D8F.toInt()))
        head(-4f, -89f, 5.4f, skin)
        helmet(-4f, -89f, 5.4f, white, white)
        bar(-36f, -73f, -36f, -83f, 9.5f, shade(0xFFFF7A66.toInt()))
        head(-36f, -89f, 5.4f, skin)
        oval(-46f, -95.5f, -26f, -91.5f, shade(0xFFE6C58A.toInt()))
        c.save()
        c.clipRect(px(-48f), py(-100f), px(-24f), py(-93f))
        oval(-42f, -101f, -30f, -86f, shade(0xFFE6C58A.toInt()))
        c.restore()
        val wall = Path().apply { m(-72f, -65f); l(-70f, -74f); l(28f, -74f); l(34f, -65f); close() }
        grad(wall, -74f, -65f, lite(white, 0.1f), dim(white, 0.25f))
        val screen = Path().apply { m(36f, -74f); l(26f, -99f); l(32f, -99f); l(44f, -74f); close() }
        glass(screen, -99f, -74f)
        stroke(screen, steel, 1.6f)
        rect(-78f, -103f, 30f, -98f, white, 2f)
        streak(-76f, -98.5f, 28f, -98.5f, a(Color.BLACK, 70), 1f)
        // Radar arch and dome.
        bar(-56f, -103f, -56f, -118f, 2.4f, white)
        bar(-66f, -112f, -46f, -112f, 1.8f, steel)
        disc(-56f, -124f, 5.5f, lite(white, 0.05f))
        bar(-56f, -129f, -56f, -131f, 1.2f, steel)

        // Bow rail: stanchions along the foredeck and a top rail.
        var rx = 0f; var ry = 0f
        for (i in 0..6) {
            val x = 88f + i * 12f
            val sheer = if (x <= 100f) -26f + (x - 20f) * -0.1f else -34f + (x - 100f) * -0.27f
            bar(x, sheer, x, sheer - 12f, 1.1f, chrome)
            if (i > 0) bar(rx, ry, x, sheer - 12f, 1.1f, chrome)
            rx = x; ry = sheer - 12f
        }
        if (l.windowsLit) {
            disc(-126f, -73f, 2.2f, 0xFFFFEAA7.toInt())
            disc(-56f, -133f, 2.2f, 0xFFFFEAA7.toInt())
        }
    }
    return bmp
}
