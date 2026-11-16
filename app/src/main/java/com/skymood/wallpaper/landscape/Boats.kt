package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import com.skymood.wallpaper.scenes.SkyGradient
import kotlin.math.max

/** How much shorter than the real thing a boat's mirror image is, so it fits the water below it. */
internal const val REFL_SQUASH = 0.78f

/**
 * One kind of boat for the sea scene. Sizes are in boat units (about a pixel on a 1080 wide screen, times
 * [scale]) measured from a waterline origin inside the sprite; [stern] and [bow] are where the hull meets
 * the water, in boat units from that origin. [depth] is how far down the water it sails (0 horizon .. 1
 * nearest). [speed], [wake] and [pitch] multiply the drift speed, the wake length and the pitching with the
 * swell (a heavy ship pitches less); [trim] is a constant angle in degrees, negative lifting the bow.
 */
internal class BoatSpec(
    val key: String,
    val w: Float, val h: Float, val ox: Float, val oy: Float, val scale: Float,
    val depth: Float, val stern: Float, val bow: Float,
    val speed: Float, val wake: Float, val pitch: Float, val trim: Float = 0f,
) {
    /** Keys of the sprite and its reflection, built once so no string is made per frame. */
    val boatKey = "boat_$key"
    val reflKey = "refl_$key"
}

internal val SLOOP = BoatSpec("sloop", 300f, 330f, 150f, 314f, 0.95f, 0.21f, -100f, 112f, 1f, 1f, 1f)
internal val DHOW = BoatSpec("dhow", 290f, 250f, 145f, 236f, 0.95f, 0.19f, -95f, 110f, 0.9f, 1f, 1.1f)
internal val TRAWLER = BoatSpec("trawler", 300f, 215f, 150f, 201f, 1f, 0.23f, -100f, 112f, 0.7f, 1.3f, 0.7f)
internal val JUNK = BoatSpec("junk", 310f, 290f, 155f, 276f, 0.95f, 0.17f, -105f, 108f, 0.8f, 1f, 0.9f)
internal val TALL_SHIP = BoatSpec("tallship", 340f, 300f, 170f, 286f, 0.78f, 0.13f, -120f, 140f, 0.55f, 0.8f, 0.5f)
internal val MOTOR_YACHT = BoatSpec("motor", 290f, 150f, 145f, 136f, 1f, 0.24f, -100f, 110f, 2.6f, 1.8f, 0.5f, -2.5f)

internal val BOATS = listOf(SLOOP, DHOW, TRAWLER, JUNK, TALL_SHIP, MOTOR_YACHT)

/** Paints every kind of boat and its reflection; the sprites are keyed "boat_<key>" and "refl_<key>". */
internal fun paintBoats(k: PaintKit): Map<String, Bitmap> {
    val out = HashMap<String, Bitmap>()
    val painters = listOf<Pair<BoatSpec, (PaintKit) -> Pair<Bitmap, Bitmap?>?>>(
        SLOOP to ::paintSloop, DHOW to ::paintDhow, TRAWLER to ::paintTrawler,
        JUNK to ::paintJunk, TALL_SHIP to ::paintTallShip, MOTOR_YACHT to ::paintMotorYacht,
    )
    for ((spec, paint) in painters) {
        // One boat failing to paint must not cost the others.
        val r = runCatching { paint(k) }.getOrNull() ?: continue
        out[spec.boatKey] = r.first
        r.second?.let { out[spec.reflKey] = it }
    }
    return out
}

/**
 * A sprite being painted for one boat: drawing helpers in boat units around the waterline origin
 * (x towards the bow, y up is negative), and [finish] to add the mirror image.
 */
internal class BoatArt private constructor(val k: PaintKit, val spec: BoatSpec, val c: Canvas, val bmp: Bitmap) {
    val s = k.unit * spec.scale
    val l = k.l
    val sunL = k.sunOnLeft
    val lamp = l.windowsLit
    val ox = spec.ox * s
    val oy = spec.oy * s
    val brush = Paint(Paint.ANTI_ALIAS_FLAG)
    val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    fun px(x: Float) = ox + x * s
    fun py(y: Float) = oy + y * s
    fun sw(v: Float) = max(1f, v * s)
    fun qb(a: Float, b: Float, e: Float, t: Float) = (1f - t) * (1f - t) * a + 2f * (1f - t) * t * b + t * t * e

    fun fillPath(p: Path, color: Int) { brush.shader = null; brush.color = color; c.drawPath(p, brush) }
    fun gradPath(p: Path, shader: Shader) { brush.color = Color.BLACK; brush.shader = shader; c.drawPath(p, brush); brush.shader = null }
    fun fillOval(x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
        brush.shader = null; brush.color = color; c.drawOval(px(x0), py(y0), px(x1), py(y1), brush)
    }
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, width: Float) {
        pen.color = color; pen.strokeWidth = sw(width); c.drawLine(px(x0), py(y0), px(x1), py(y1), pen)
    }
    fun stroke(p: Path, color: Int, width: Float) { pen.color = color; pen.strokeWidth = sw(width); c.drawPath(p, pen) }

    inline fun clipped(p: Path, block: () -> Unit) { c.save(); c.clipPath(p); block(); c.restore() }

    /** Darkens the end of a hull or sail that faces away from the sun. */
    fun sideShade(x0: Float, x1: Float, y0: Float, y1: Float, amount: Int) {
        brush.color = Color.BLACK
        brush.shader = LinearGradient(
            px(x0), 0f, px(x1), 0f,
            k.alpha(Color.BLACK, if (sunL) 0 else amount), k.alpha(Color.BLACK, if (sunL) amount else 0), Shader.TileMode.CLAMP,
        )
        c.drawRect(px(x0), py(y0), px(x1), py(y1), brush)
        brush.shader = null
    }

    /** A small standing figure with its feet at ([x], [baseY]), optionally in a [hat] (turban, cap). */
    fun figure(x: Float, baseY: Float, jacket: Int, hat: Int = 0, tall: Float = 16f) {
        val torso = Path().apply {
            moveTo(px(x - 5f), py(baseY)); lineTo(px(x - 4f), py(baseY - tall))
            quadTo(px(x), py(baseY - tall - 3f), px(x + 4f), py(baseY - tall)); lineTo(px(x + 5f), py(baseY)); close()
        }
        fillPath(torso, jacket)
        fillOval(x - 3.5f, baseY - tall - 8.5f, x + 3.5f, baseY - tall - 1.5f, l.shade(0xFFDDB08A.toInt()))
        if (hat != 0) fillOval(x - 4.1f, baseY - tall - 11f, x + 4.1f, baseY - tall - 4.6f, hat)
    }

    /** A square sail on a yard: wider at the foot, belly bulging out, with seams and a lit side. */
    fun squareSail(cx: Float, topY: Float, botY: Float, halfTop: Float, halfBot: Float, bulge: Float, color: Int) {
        val p = Path().apply {
            moveTo(px(cx - halfTop), py(topY)); lineTo(px(cx + halfTop), py(topY)); lineTo(px(cx + halfBot), py(botY))
            quadTo(px(cx), py(botY + bulge), px(cx - halfBot), py(botY)); close()
        }
        val dark = k.darken(color, 0.2f)
        gradPath(p, LinearGradient(px(cx - halfBot), 0f, px(cx + halfBot), 0f, if (sunL) color else dark, if (sunL) dark else color, Shader.TileMode.CLAMP))
        clipped(p) {
            val belly = Path().apply {
                moveTo(px(cx - halfTop * 0.5f), py(topY)); quadTo(px(cx - halfBot * 0.75f), py((topY + botY) / 2f), px(cx - halfBot * 0.3f), py(botY + bulge))
                lineTo(px(cx + halfBot * 0.25f), py(botY + bulge)); quadTo(px(cx - halfBot * 0.1f), py((topY + botY) / 2f), px(cx + halfTop * 0.25f), py(topY)); close()
            }
            fillPath(belly, k.alpha(Color.WHITE, 38))
            for (i in 1..2) {
                val f = 2f * i / 3f - 1f
                line(cx + halfTop * f, topY, cx + halfBot * f, botY + bulge * 0.7f, k.alpha(Color.BLACK, 30), 0.9f)
            }
        }
        line(cx - halfTop - 4f, topY, cx + halfTop + 4f, topY, l.shade(0xFF4A3322.toInt()), 2.2f)
    }

    /** The mirror image, flattened and faded with depth, so the water can ripple it. */
    fun finish(): Pair<Bitmap, Bitmap?> {
        val bw = bmp.width
        val rh = (oy * REFL_SQUASH + 10f * s).toInt()
        val (rc, rbmp) = k.newSprite(bw, rh) ?: return bmp to null
        rc.save()
        rc.scale(1f, -REFL_SQUASH)
        rc.translate(0f, -oy)
        rc.drawBitmap(bmp, 0f, 0f, null)
        rc.restore()
        val fade = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            shader = LinearGradient(
                0f, 0f, 0f, rh.toFloat(), intArrayOf(0xB0FFFFFF.toInt(), 0x66FFFFFF, 0x1AFFFFFF, 0), floatArrayOf(0f, 0.3f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        rc.drawRect(0f, 0f, bw.toFloat(), rh.toFloat(), fade)
        val tint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            color = k.alpha(SkyGradient.blend(l.shade(SEA_FAR), l.skyBottom, 0.2f), 80)
        }
        rc.drawRect(0f, 0f, bw.toFloat(), rh.toFloat(), tint)
        return bmp to rbmp
    }

    companion object {
        fun create(k: PaintKit, spec: BoatSpec): BoatArt? {
            val s = k.unit * spec.scale
            val (c, bmp) = k.newSprite((spec.w * s).toInt(), (spec.h * s).toInt()) ?: return null
            return BoatArt(k, spec, c, bmp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Sloop
// ---------------------------------------------------------------------------------------------

/**
 * A sloop seen side-on: raked bow, white topsides with a navy cove stripe and red boot, a coach-roof
 * with windows, a crew in the cockpit, a mast with spreaders and stays, and a billowed main and genoa
 * with seams and battens.
 */
private fun paintSloop(k: PaintKit): Pair<Bitmap, Bitmap?>? {
    val art = BoatArt.create(k, SLOOP) ?: return null
    with(art) {
        // ---- hull
        fun sheer(dy: Float, full: Boolean): Path = Path().apply {
            moveTo(px(-120f), py(-36f + dy))
            cubicTo(px(-70f), py(-30f + dy), px(30f), py(-31f + dy), px(104f), py(-44f + dy))
            if (full) quadTo(px(125f), py(-49f + dy), px(135f), py(-46f + dy))
        }
        val hull = Path().apply {
            moveTo(px(-118f), py(-36f))
            cubicTo(px(-70f), py(-30f), px(30f), py(-31f), px(104f), py(-44f))
            quadTo(px(125f), py(-49f), px(135f), py(-46f))
            quadTo(px(125f), py(-18f), px(108f), py(4f))
            lineTo(px(-97f), py(4f))
            close()
        }
        val white = l.shade(0xFFF5F3EC.toInt())
        gradPath(hull, LinearGradient(0f, py(-48f), 0f, py(4f), white, k.darken(white, 0.2f), Shader.TileMode.CLAMP))
        c.save()
        c.clipPath(hull)
        // Light falls from the sun's side: the far end of the hull sits a little darker.
        brush.color = Color.BLACK
        brush.shader = LinearGradient(
            px(-118f), 0f, px(135f), 0f,
            k.alpha(Color.BLACK, if (sunL) 0 else 52), k.alpha(Color.BLACK, if (sunL) 52 else 0), Shader.TileMode.CLAMP,
        )
        c.drawRect(px(-130f), py(-60f), px(145f), py(8f), brush)
        brush.shader = null
        // Red boot top, white pin-stripe over it, navy cove stripe along the sheer.
        brush.color = l.shade(0xFF8E2C25.toInt())
        c.drawRect(px(-130f), py(-5f), px(145f), py(8f), brush)
        pen.color = k.alpha(Color.WHITE, 210); pen.strokeWidth = sw(1.3f)
        c.drawLine(px(-130f), py(-5.6f), px(145f), py(-5.6f), pen)
        pen.color = l.shade(0xFF1D3B5E.toInt()); pen.strokeWidth = sw(4.5f)
        c.drawPath(sheer(8f, true), pen)
        // Portholes.
        for (x in floatArrayOf(-62f, -30f, 2f, 34f, 66f)) {
            fillOval(x - 3.6f, -15.6f, x + 3.6f, -10.4f, if (lamp) 0xFFFFD27A.toInt() else l.shade(0xFF22313D.toInt()))
            pen.color = k.alpha(Color.WHITE, 120); pen.strokeWidth = sw(0.9f)
            c.drawLine(px(x - 3.4f), py(-13.6f), px(x + 3.4f), py(-13.6f), pen)
        }
        // Soft shadow where the hull curves under, so it reads as a solid volume.
        brush.shader = LinearGradient(0f, py(-14f), 0f, py(4f), k.alpha(Color.BLACK, 0), k.alpha(Color.BLACK, 70), Shader.TileMode.CLAMP)
        c.drawRect(px(-130f), py(-14f), px(145f), py(6f), brush)
        brush.shader = null
        c.restore()

        // ---- cockpit and crew
        pen.color = l.shade(0xFF8C6A42.toInt()); pen.strokeWidth = sw(3.5f)
        c.drawLine(px(-112f), py(-37f), px(-58f), py(-33f), pen)
        fun person(x: Float, jacket: Int, hair: Int) {
            val torso = Path().apply {
                moveTo(px(x - 5.5f), py(-35f)); lineTo(px(x - 4.5f), py(-51f)); quadTo(px(x), py(-54f), px(x + 4.5f), py(-51f)); lineTo(px(x + 5.5f), py(-35f)); close()
            }
            fillPath(torso, jacket)
            fillOval(x - 3.6f, -62f, x + 3.6f, -54.6f, l.shade(0xFFE2B48E.toInt()))
            fillOval(x - 3.8f, -63f, x + 3.8f, -58.4f, hair)
        }
        person(-84f, l.shade(0xFFD2452E.toInt()), l.shade(0xFF2A2018.toInt()))
        person(-68f, l.shade(0xFF2F78A8.toInt()), l.shade(0xFF8A5A2A.toInt()))
        line(-112f, -36f, -94f, -45f, l.shade(0xFF6E4E2C.toInt()), 1.8f)   // tiller

        // ---- coach-roof and windows
        val cabin = Path().apply {
            moveTo(px(-56f), py(-31f)); lineTo(px(-50f), py(-53f))
            quadTo(px(0f), py(-60f), px(40f), py(-57f))
            lineTo(px(64f), py(-38f)); lineTo(px(64f), py(-35f)); lineTo(px(-56f), py(-31f)); close()
        }
        val roof = l.shade(0xFFEDE6D4.toInt())
        gradPath(cabin, LinearGradient(0f, py(-60f), 0f, py(-31f), k.lighten(roof, 0.1f), k.darken(roof, 0.16f), Shader.TileMode.CLAMP))
        for (wx in floatArrayOf(-42f, -12f, 18f)) {
            val win = Path().apply {
                moveTo(px(wx), py(-51f)); lineTo(px(wx + 24f), py(-51.5f)); lineTo(px(wx + 25.5f), py(-43f)); lineTo(px(wx + 1.5f), py(-42.5f)); close()
            }
            fillPath(win, if (lamp) 0xFFFFD27A.toInt() else l.shade(0xFF1F2D38.toInt()))
            if (!lamp) line(wx + 3f, -49.6f, wx + 21f, -49.9f, k.alpha(Color.WHITE, 90), 1.4f)
        }
        // Toe rail over the junction of hull and deck.
        pen.color = l.shade(0xFFB88A55.toInt()); pen.strokeWidth = sw(2.6f)
        c.drawPath(sheer(-0.4f, true), pen)

        // ---- lifelines and bow pulpit
        val rail = k.alpha(l.shade(0xFFEDEDED.toInt()), 190)
        for (x in floatArrayOf(72f, 88f, 104f, 119f)) {
            val y = -38.6f - (x - 64f) * 0.155f
            line(x, y, x, y - 11f, rail, 1.1f)
        }
        line(72f, -49.6f, 119f, -60.4f, rail, 1.1f)
        line(119f, -60.4f, 130f, -56f, rail, 1.1f)

        // ---- rig: mast, boom, sails
        val mastX = 30f; val mastTop = -296f
        val sail = l.shade(0xFFF7F2E4.toInt())
        val sailShade = k.darken(sail, 0.17f)
        val mainP = Path().apply {
            moveTo(px(26f), py(-282f)); quadTo(px(-60f), py(-174f), px(-98f), py(-66f)); lineTo(px(26f), py(-62f)); close()
        }
        val sailShader = LinearGradient(
            px(-98f), 0f, px(26f), 0f, if (sunL) sail else sailShade, if (sunL) sailShade else sail, Shader.TileMode.CLAMP,
        )
        gradPath(mainP, sailShader)
        c.save()
        c.clipPath(mainP)
        // Belly of the sail: a darker fold along the luff that curves with the draft.
        val fold = Path().apply { moveTo(px(26f), py(-282f)); quadTo(px(-24f), py(-168f), px(26f), py(-60f)); close() }
        fillPath(fold, k.alpha(Color.BLACK, 36))
        val belly = Path().apply { moveTo(px(-8f), py(-260f)); quadTo(px(-52f), py(-150f), px(-40f), py(-72f)); lineTo(px(-8f), py(-70f)); quadTo(px(-26f), py(-160f), px(-8f), py(-260f)); close() }
        fillPath(belly, k.alpha(Color.WHITE, 40))
        // Cloth seams and battens.
        for (i in 1..6) {
            val t = i / 7f
            val lx = qb(26f, -60f, -98f, t); val ly = qb(-282f, -174f, -66f, t)
            val sx = 26f; val sy = ly + 2.5f
            val seam = Path().apply { moveTo(px(sx), py(sy)); quadTo(px((sx + lx) / 2f), py((sy + ly) / 2f + 7f), px(lx), py(ly)) }
            pen.color = k.alpha(Color.BLACK, 30); pen.strokeWidth = sw(0.9f)
            c.drawPath(seam, pen)
            if (i >= 2 && i != 6) line(lx, ly, lx + 30f, ly + 2f, k.alpha(Color.BLACK, 75), 1.3f)
        }
        c.restore()
        // Sail edge: a darker tape along the leech and foot.
        pen.color = k.alpha(l.shade(0xFF6E6A60.toInt()), 150); pen.strokeWidth = sw(1f)
        c.drawPath(Path().apply { moveTo(px(26f), py(-282f)); quadTo(px(-60f), py(-174f), px(-98f), py(-66f)) }, pen)

        // Mast (silver, with a lit edge) and boom.
        line(mastX, -57f, mastX, mastTop, l.shade(0xFF8E979E.toInt()), 3.6f)
        line(mastX + (if (sunL) -0.9f else 0.9f), -57f, mastX + (if (sunL) -0.9f else 0.9f), mastTop, k.lighten(l.shade(0xFFC8CFD4.toInt()), 0.25f), 1.3f)
        line(mastX, -60f, -100f, -65f, l.shade(0xFF9AA2A8.toInt()), 3.2f)
        line(-72f, -64f, -77f, -36f, k.alpha(Color.BLACK, 120), 1f)             // mainsheet

        // Genoa, overlapping the mast.
        val jibP = Path().apply {
            moveTo(px(38f), py(-273f)); quadTo(px(-6f), py(-174f), px(-18f), py(-76f))
            quadTo(px(52f), py(-58f), px(122f), py(-48f)); close()
        }
        val jibCol = k.darken(sail, 0.04f)
        gradPath(jibP, LinearGradient(px(-18f), 0f, px(122f), 0f, if (sunL) jibCol else k.darken(jibCol, 0.17f), if (sunL) k.darken(jibCol, 0.17f) else jibCol, Shader.TileMode.CLAMP))
        c.save()
        c.clipPath(jibP)
        val jibFold = Path().apply { moveTo(px(38f), py(-273f)); quadTo(px(60f), py(-150f), px(122f), py(-48f)); lineTo(px(120f), py(-60f)); quadTo(px(70f), py(-120f), px(40f), py(-270f)); close() }
        fillPath(jibFold, k.alpha(Color.BLACK, 30))
        for (i in 1..4) {
            val t = i / 5f
            val lx = 38f + (122f - 38f) * t; val ly = -273f + (-48f + 273f) * t
            val seam = Path().apply { moveTo(px(-18f + t * 6f), py(-76f - 4f * t)); quadTo(px((lx - 18f) / 2f), py((ly - 76f) / 2f - 8f), px(lx), py(ly)) }
            pen.color = k.alpha(Color.BLACK, 26); pen.strokeWidth = sw(0.9f)
            c.drawPath(seam, pen)
        }
        c.restore()
        // UV strip along the foot and a pair of tell-tales.
        line(-14f, -77f, 118f, -50f, k.alpha(l.shade(0xFF2F78A8.toInt()), 190), 2.2f)
        line(70f, -190f, 82f, -189f, l.shade(0xFFD2452E.toInt()), 1.2f)
        line(58f, -222f, 69f, -221f, l.shade(0xFF3A9A5C.toInt()), 1.2f)

        // Stays, shrouds, burgee.
        val wire = k.alpha(l.shade(0xFF22282D.toInt()), 165)
        line(mastX, mastTop + 4f, -116f, -37f, wire, 0.95f)       // backstay
        line(mastX, -180f, 11f, -40f, wire, 0.95f)                // shrouds
        line(mastX, -180f, 49f, -40f, wire, 0.95f)
        line(mastX - 1f, -180f, mastX + 1f, -180f, l.shade(0xFFB0B8BE.toInt()), 2f)
        line(mastX, mastTop + 3f, 128f, -50f, wire, 0.95f)         // forestay, under the genoa luff
        val flag = Path().apply {
            moveTo(px(mastX), py(mastTop)); lineTo(px(mastX - 28f), py(mastTop + 6f)); lineTo(px(mastX), py(mastTop + 13f)); close()
        }
        fillPath(flag, l.shade(0xFFD6402F.toInt()))
        if (lamp) fillOval(mastX - 2.6f, mastTop - 6f, mastX + 2.6f, mastTop - 0.8f, 0xFFFFF2C0.toInt())
    }
    return art.finish()
}

// ---------------------------------------------------------------------------------------------
// Dhow
// ---------------------------------------------------------------------------------------------

/**
 * A wooden dhow: a long curved prow, painted strakes, a raised poop with a cabin, a steering oar, and one
 * great lateen sail on a yard that slants steeply up and aft, its cloth hanging in radiating panels.
 */
private fun paintDhow(k: PaintKit): Pair<Bitmap, Bitmap?>? {
    val a = BoatArt.create(k, DHOW) ?: return null
    with(a) {
        val wood = l.shade(0xFF7C4A28.toInt())
        val dark = l.shade(0xFF4A2E1A.toInt())
        fun sheer(dy: Float) = Path().apply {
            moveTo(px(-76f), py(-33f + dy))
            cubicTo(px(-30f), py(-26f + dy), px(40f), py(-27f + dy), px(90f), py(-42f + dy))
            quadTo(px(114f), py(-50f + dy), px(130f), py(-70f + dy))
        }
        // Crew sit inside the rail: painted first, so the hull hides their legs.
        figure(-66f, -22f, l.shade(0xFF2F6FA8.toInt()), hat = l.shade(0xFFF2EEE4.toInt()))
        figure(-30f, -20f, l.shade(0xFFD9822B.toInt()), hat = l.shade(0xFFC8402E.toInt()))
        figure(24f, -20f, l.shade(0xFF3E8A5C.toInt()), hat = l.shade(0xFFF2EEE4.toInt()))

        val hull = Path().apply {
            moveTo(px(-108f), py(-54f)); lineTo(px(-80f), py(-54f)); lineTo(px(-75f), py(-33f))
            cubicTo(px(-30f), py(-26f), px(40f), py(-27f), px(90f), py(-42f))
            quadTo(px(114f), py(-50f), px(130f), py(-70f))
            quadTo(px(125f), py(-26f), px(108f), py(4f))
            lineTo(px(-93f), py(4f)); close()
        }
        gradPath(hull, LinearGradient(0f, py(-62f), 0f, py(4f), k.lighten(wood, 0.1f), k.darken(wood, 0.32f), Shader.TileMode.CLAMP))
        clipped(hull) {
            // Planking, then a cream strake and a red one under the rail, and a tarred bottom.
            pen.color = k.alpha(Color.BLACK, 50); pen.strokeWidth = sw(0.9f)
            for (i in 1..3) c.drawPath(sheer(7f * i), pen)
            stroke(sheer(4f), l.shade(0xFFEFE4C8.toInt()), 3.2f)
            stroke(sheer(10.5f), l.shade(0xFFA8342A.toInt()), 2.2f)
            brush.shader = null; brush.color = l.shade(0xFF20140E.toInt())
            c.drawRect(px(-120f), py(-4f), px(140f), py(8f), brush)
            sideShade(-118f, 130f, -80f, 8f, 52)
        }
        // The raised poop with its little cabin and lantern.
        val cabin = Path().apply {
            moveTo(px(-106f), py(-54f)); lineTo(px(-103f), py(-74f)); lineTo(px(-79f), py(-74f)); lineTo(px(-77f), py(-54f)); close()
        }
        gradPath(cabin, LinearGradient(0f, py(-74f), 0f, py(-54f), l.shade(0xFFD2B27A.toInt()), l.shade(0xFFA9855A.toInt()), Shader.TileMode.CLAMP))
        fillOval(-98f, -70f, -86f, -59f, if (lamp) 0xFFFFD27A.toInt() else l.shade(0xFF2A1E16.toInt()))
        line(-108f, -75f, -76f, -75f, dark, 3f)
        line(-106f, -50f, -122f, 6f, dark, 3f)          // steering oar over the stern

        // Mast, then the lateen sail: a long yard from low at the bow to high aft, panels radiating from the clew.
        line(-8f, -30f, -2f, -142f, dark, 4.2f)
        val tackX = 92f; val tackY = -44f; val peakX = -66f; val peakY = -178f; val clewX = -95f; val clewY = -58f
        val sail = Path().apply {
            moveTo(px(tackX), py(tackY)); lineTo(px(peakX), py(peakY))
            quadTo(px(-92f), py(-122f), px(clewX), py(clewY)); close()
        }
        val cloth = l.shade(0xFFEBDDB9.toInt())
        val clothDark = k.darken(cloth, 0.2f)
        gradPath(sail, LinearGradient(px(clewX), 0f, px(tackX), 0f, if (sunL) cloth else clothDark, if (sunL) clothDark else cloth, Shader.TileMode.CLAMP))
        clipped(sail) {
            for (i in 1..9) {
                val t = i / 10f
                line(clewX, clewY, tackX + (peakX - tackX) * t, tackY + (peakY - tackY) * t, k.alpha(Color.BLACK, 30), 0.9f)
            }
            fillPath(
                Path().apply { moveTo(px(-40f), py(-62f)); lineTo(px(10f), py(-66f)); lineTo(px(4f), py(-92f)); lineTo(px(-36f), py(-96f)); close() },
                k.alpha(l.shade(0xFFD2B070.toInt()), 110),
            )
        }
        line(tackX + 5f, tackY + 3f, peakX - 3f, peakY - 3f, dark, 3.4f)
        val wire = k.alpha(l.shade(0xFF22282D.toInt()), 160)
        line(-2f, -144f, 128f, -68f, wire, 0.95f)
        line(-2f, -144f, -104f, -56f, wire, 0.95f)
        line(clewX, clewY, -104f, -54f, wire, 0.95f)
        val flag = Path().apply { moveTo(px(-2f), py(-144f)); lineTo(px(-24f), py(-139f)); lineTo(px(-2f), py(-133f)); close() }
        fillPath(flag, l.shade(0xFFC8402E.toInt()))
    }
    return a.finish()
}

// ---------------------------------------------------------------------------------------------
// Trawler
// ---------------------------------------------------------------------------------------------

/**
 * A small fishing trawler: navy hull with tyre fenders, a white wheelhouse with a red funnel, a mast with
 * radar and stays, an orange A-frame gantry over the stern, a heap of net, buoys and a fisherman in oilskins.
 */
private fun paintTrawler(k: PaintKit): Pair<Bitmap, Bitmap?>? {
    val a = BoatArt.create(k, TRAWLER) ?: return null
    with(a) {
        val navy = l.shade(0xFF2B4F7A.toInt())
        val white = l.shade(0xFFF1EFE8.toInt())
        val orange = l.shade(0xFFE0652A.toInt())
        fun sheer(dy: Float, full: Boolean) = Path().apply {
            moveTo(px(-110f), py(-33f + dy))
            cubicTo(px(-50f), py(-27f + dy), px(40f), py(-30f + dy), px(98f), py(-46f + dy))
            if (full) quadTo(px(114f), py(-52f + dy), px(126f), py(-56f + dy))
        }
        // Gear and crew on the working deck, painted first so the bulwark hides their feet.
        fillPath(
            Path().apply { moveTo(px(-76f), py(-24f)); quadTo(px(-64f), py(-58f), px(-42f), py(-24f)); close() },
            l.shade(0xFFD5652A.toInt()),
        )
        for (i in 0 until 5) {
            val x = -72f + i * 6.5f
            line(x, -27f, x + 8f, -44f + (i % 2) * 3f, k.alpha(Color.BLACK, 55), 0.9f)
        }
        line(-102f, -30f, -84f, -100f, orange, 2.8f)
        line(-64f, -30f, -84f, -100f, orange, 2.8f)
        line(-96f, -66f, -72f, -66f, orange, 2f)
        line(-84f, -100f, -84f, -72f, k.alpha(Color.BLACK, 140), 1f)
        figure(-22f, -24f, l.shade(0xFFF2A91E.toInt()), tall = 17f)
        fillOval(-38f, -34f, -30f, -26f, l.shade(0xFFD2452E.toInt()))
        fillOval(-8f, -34f, 0f, -26f, l.shade(0xFFF2EEE4.toInt()))

        val hull = Path().apply {
            moveTo(px(-108f), py(-33f))
            cubicTo(px(-50f), py(-27f), px(40f), py(-30f), px(98f), py(-46f))
            quadTo(px(114f), py(-52f), px(126f), py(-56f))
            quadTo(px(122f), py(-20f), px(104f), py(4f))
            lineTo(px(-96f), py(4f)); close()
        }
        gradPath(hull, LinearGradient(0f, py(-56f), 0f, py(4f), k.lighten(navy, 0.08f), k.darken(navy, 0.3f), Shader.TileMode.CLAMP))
        clipped(hull) {
            stroke(sheer(1.6f, true), white, 2.6f)
            brush.shader = null; brush.color = l.shade(0xFF8E2C25.toInt())
            c.drawRect(px(-120f), py(-5f), px(140f), py(8f), brush)
            line(-120f, -5.6f, 140f, -5.6f, k.alpha(Color.WHITE, 200), 1.2f)
            for (x in floatArrayOf(-64f, -26f, 12f, 50f)) fillOval(x - 5f, -23f, x + 5f, -12f, l.shade(0xFF1A1A1A.toInt()))
            sideShade(-118f, 130f, -80f, 8f, 52)
        }
        // Wheelhouse, funnel, mast.
        val funnel = Path().apply { moveTo(px(8f), py(-60f)); lineTo(px(10f), py(-108f)); lineTo(px(24f), py(-108f)); lineTo(px(26f), py(-60f)); close() }
        fillPath(funnel, l.shade(0xFFB63A2E.toInt()))
        fillPath(Path().apply { moveTo(px(9.3f), py(-88f)); lineTo(px(9.6f), py(-95f)); lineTo(px(24.4f), py(-95f)); lineTo(px(24.7f), py(-88f)); close() }, white)
        fillPath(Path().apply { moveTo(px(8f), py(-108f)); lineTo(px(8.6f), py(-113f)); lineTo(px(25.4f), py(-113f)); lineTo(px(26f), py(-108f)); close() }, l.shade(0xFF1D1D1F.toInt()))
        val lower = Path().apply { moveTo(px(8f), py(-31f)); lineTo(px(10f), py(-58f)); lineTo(px(72f), py(-60f)); lineTo(px(78f), py(-40f)); close() }
        val upper = Path().apply { moveTo(px(26f), py(-58f)); lineTo(px(28f), py(-82f)); lineTo(px(68f), py(-84f)); lineTo(px(74f), py(-60f)); close() }
        for (p in arrayOf(lower, upper)) {
            gradPath(p, LinearGradient(0f, py(-84f), 0f, py(-31f), white, k.darken(white, 0.14f), Shader.TileMode.CLAMP))
        }
        val glass = Path().apply { moveTo(px(31f), py(-78f)); lineTo(px(66f), py(-80f)); lineTo(px(71f), py(-64f)); lineTo(px(30f), py(-63f)); close() }
        fillPath(glass, if (lamp) 0xFFFFD27A.toInt() else l.shade(0xFF1F2D38.toInt()))
        line(44f, -79f, 43f, -63f, white, 1.6f)
        line(57f, -80f, 58f, -63f, white, 1.6f)
        fillPath(Path().apply { moveTo(px(16f), py(-54f)); lineTo(px(24f), py(-54f)); lineTo(px(24f), py(-38f)); lineTo(px(16f), py(-38f)); close() }, k.alpha(Color.BLACK, 90))
        line(24f, -85f, 76f, -87f, k.darken(white, 0.3f), 3f)
        line(50f, -86f, 50f, -158f, l.shade(0xFF8E979E.toInt()), 3.2f)
        line(40f, -132f, 60f, -132f, l.shade(0xFF2A2E33.toInt()), 2.6f)         // radar scanner
        fillOval(46f, -137f, 54f, -131f, l.shade(0xFFD8DCDF.toInt()))
        val wire = k.alpha(l.shade(0xFF22282D.toInt()), 165)
        line(50f, -158f, 126f, -56f, wire, 0.95f)
        line(50f, -158f, -102f, -36f, wire, 0.95f)
        line(50f, -158f, -84f, -100f, wire, 0.95f)
        if (lamp) fillOval(47.4f, -165f, 52.6f, -159.8f, 0xFFFFF2C0.toInt())
    }
    return a.finish()
}

// ---------------------------------------------------------------------------------------------
// Junk
// ---------------------------------------------------------------------------------------------

/**
 * A Chinese junk: a tall stern castle, a blunt painted-eye bow, and three masts carrying fully battened
 * sails of red-brown cloth, each panel between two battens a shade apart.
 */
private fun paintJunk(k: PaintKit): Pair<Bitmap, Bitmap?>? {
    val a = BoatArt.create(k, JUNK) ?: return null
    with(a) {
        val wood = l.shade(0xFF7E2B20.toInt())
        val dark = l.shade(0xFF3C2418.toInt())
        fun sheer(dy: Float) = Path().apply {
            moveTo(px(-66f), py(-37f + dy))
            cubicTo(px(-20f), py(-29f + dy), px(50f), py(-30f + dy), px(104f), py(-44f + dy))
        }
        fun hat(x: Float, baseY: Float, shirt: Int) {
            figure(x, baseY, shirt)
            fillPath(
                Path().apply { moveTo(px(x - 9f), py(baseY - 23f)); lineTo(px(x), py(baseY - 35f)); lineTo(px(x + 9f), py(baseY - 23f)); close() },
                l.shade(0xFFD9B66A.toInt()),
            )
        }
        hat(-38f, -24f, l.shade(0xFF3A6EA5.toInt()))
        hat(30f, -26f, l.shade(0xFFC9892A.toInt()))

        val hull = Path().apply {
            moveTo(px(-110f), py(-70f)); lineTo(px(-70f), py(-70f)); lineTo(px(-66f), py(-37f))
            cubicTo(px(-20f), py(-29f), px(50f), py(-30f), px(104f), py(-44f))
            quadTo(px(112f), py(-46f), px(114f), py(-40f))
            quadTo(px(112f), py(-16f), px(100f), py(4f))
            lineTo(px(-96f), py(4f)); close()
        }
        gradPath(hull, LinearGradient(0f, py(-70f), 0f, py(4f), k.lighten(wood, 0.1f), k.darken(wood, 0.35f), Shader.TileMode.CLAMP))
        clipped(hull) {
            pen.color = k.alpha(Color.BLACK, 55); pen.strokeWidth = sw(0.9f)
            for (i in 1..3) c.drawPath(sheer(6f * i), pen)
            stroke(sheer(2.5f), l.shade(0xFF1E1410.toInt()), 3f)
            brush.shader = null; brush.color = l.shade(0xFFD7A83A.toInt())
            c.drawRect(px(-120f), py(-67f), px(-68f), py(-63f), brush)
            brush.color = l.shade(0xFF1E1410.toInt())
            c.drawRect(px(-120f), py(-4f), px(140f), py(8f), brush)
            sideShade(-118f, 130f, -80f, 8f, 52)
        }
        // Painted eye on the bow, stern windows and a lantern.
        fillOval(87f, -29f, 98f, -19f, l.shade(0xFFF4F0E4.toInt()))
        fillOval(91f, -26f, 96f, -21f, l.shade(0xFF1A1410.toInt()))
        for (wx in floatArrayOf(-102f, -90f, -78f)) {
            fillPath(
                Path().apply { moveTo(px(wx), py(-58f)); lineTo(px(wx + 7f), py(-58f)); lineTo(px(wx + 7f), py(-50f)); lineTo(px(wx), py(-50f)); close() },
                if (lamp) 0xFFFFD27A.toInt() else l.shade(0xFF2A1810.toInt()),
            )
        }
        line(-112f, -71f, -66f, -71f, dark, 3f)
        fillOval(-114f, -82f, -107f, -75f, if (lamp) 0xFFFFC870.toInt() else l.shade(0xFFC8402E.toInt()))

        // Fully battened sails: [fx] is the forward edge, the aft edge is a curve from ([ax0],[ay0]) to ([ax1],[ay1]).
        fun junkSail(fx: Float, fTop: Float, fBot: Float, ax0: Float, ay0: Float, acx: Float, acy: Float, ax1: Float, ay1: Float, base: Int) {
            val n = 9
            fun xA(f: Float) = qb(ax0, acx, ax1, 1f - f)
            fun yA(f: Float) = qb(ay0, acy, ay1, 1f - f)
            fun yF(f: Float) = fBot + (fTop - fBot) * f
            val outline = Path().apply {
                moveTo(px(fx), py(yF(1f))); lineTo(px(ax0), py(ay0))
                for (i in 1..12) { val f = 1f - i / 12f; lineTo(px(xA(f)), py(yA(f))) }
                lineTo(px(fx), py(fBot)); close()
            }
            val clothA = l.shade(base)
            val clothB = k.darken(clothA, 0.1f)
            clipped(outline) {
                for (i in 0 until n) {
                    val f0 = i / n.toFloat(); val f1 = (i + 1) / n.toFloat()
                    val panel = Path().apply {
                        moveTo(px(fx), py(yF(f0))); lineTo(px(xA(f0)), py(yA(f0))); lineTo(px(xA(f1)), py(yA(f1))); lineTo(px(fx), py(yF(f1))); close()
                    }
                    fillPath(panel, if (i % 2 == 0) clothA else clothB)
                }
                sideShade(minOf(fx, ax0, acx, ax1) - 2f, fx + 2f, -280f, -30f, 70)
            }
            for (i in 0..n) {
                val f = i / n.toFloat()
                line(fx, yF(f), xA(f), yA(f), k.alpha(l.shade(0xFF2A1810.toInt()), 190), 1.3f)
            }
        }
        junkSail(22f, -236f, -48f, -70f, -250f, -98f, -150f, -84f, -52f, 0xFFB5582B.toInt())
        junkSail(88f, -178f, -46f, 44f, -188f, 32f, -118f, 40f, -50f, 0xFFC8742E.toInt())
        junkSail(-62f, -150f, -76f, -96f, -160f, -112f, -120f, -104f, -80f, 0xFFA84E28.toInt())
        // Masts in front of the sails' forward edges, with pennants and a few stays.
        line(16f, -34f, 16f, -262f, dark, 4f)
        line(82f, -34f, 82f, -190f, dark, 3.4f)
        line(-58f, -70f, -58f, -170f, dark, 3f)
        val wire = k.alpha(l.shade(0xFF22282D.toInt()), 160)
        line(16f, -262f, 112f, -44f, wire, 0.95f)
        line(16f, -262f, -108f, -72f, wire, 0.95f)
        line(82f, -190f, 112f, -44f, wire, 0.95f)
        for ((fx, fy) in arrayOf(16f to -262f, 82f to -190f, -58f to -170f)) {
            fillPath(
                Path().apply { moveTo(px(fx), py(fy)); lineTo(px(fx - 22f), py(fy + 5f)); lineTo(px(fx), py(fy + 11f)); close() },
                l.shade(0xFFD6402F.toInt()),
            )
        }
    }
    return a.finish()
}

// ---------------------------------------------------------------------------------------------
// Tall ship
// ---------------------------------------------------------------------------------------------

/**
 * A square-rigged three-master: dark hull with an ochre band and a row of gun ports, a raised stern with
 * lit windows, a bowsprit and jibs, and tiers of billowed square sails on all three masts.
 */
private fun paintTallShip(k: PaintKit): Pair<Bitmap, Bitmap?>? {
    val a = BoatArt.create(k, TALL_SHIP) ?: return null
    with(a) {
        val wood = l.shade(0xFF33261C.toInt())
        val dark = l.shade(0xFF4A3322.toInt())
        val sailCol = l.shade(0xFFEFE6CF.toInt())
        // Masts first, so the sails billow in front of them.
        line(62f, -48f, 62f, -204f, dark, 3.4f)
        line(-8f, -40f, -8f, -246f, dark, 4f)
        line(-74f, -66f, -74f, -196f, dark, 3f)
        line(118f, -52f, 166f, -76f, dark, 3.2f)         // bowsprit

        // Jibs between the foremast and the bowsprit.
        val jib1 = Path().apply {
            moveTo(px(62f), py(-192f)); lineTo(px(164f), py(-77f)); quadTo(px(132f), py(-62f), px(100f), py(-64f)); quadTo(px(78f), py(-130f), px(62f), py(-192f)); close()
        }
        gradPath(jib1, LinearGradient(px(62f), 0f, px(164f), 0f, if (sunL) sailCol else k.darken(sailCol, 0.2f), if (sunL) k.darken(sailCol, 0.2f) else sailCol, Shader.TileMode.CLAMP))
        val jib2 = Path().apply {
            moveTo(px(62f), py(-148f)); lineTo(px(140f), py(-67f)); quadTo(px(118f), py(-58f), px(92f), py(-62f)); quadTo(px(74f), py(-100f), px(62f), py(-148f)); close()
        }
        gradPath(jib2, LinearGradient(px(62f), 0f, px(140f), 0f, if (sunL) k.darken(sailCol, 0.05f) else k.darken(sailCol, 0.22f), if (sunL) k.darken(sailCol, 0.22f) else k.darken(sailCol, 0.05f), Shader.TileMode.CLAMP))

        // Square sails: course, topsail, topgallant and royal on the main; fewer on the others.
        squareSail(62f, -62f, -112f, 34f, 42f, 7f, sailCol)
        squareSail(62f, -118f, -158f, 28f, 34f, 6f, sailCol)
        squareSail(62f, -164f, -190f, 20f, 24f, 4f, sailCol)
        squareSail(-8f, -56f, -118f, 38f, 46f, 8f, sailCol)
        squareSail(-8f, -124f, -170f, 32f, 38f, 7f, sailCol)
        squareSail(-8f, -176f, -208f, 24f, 28f, 5f, sailCol)
        squareSail(-8f, -214f, -234f, 16f, 18f, 3f, sailCol)
        squareSail(-74f, -80f, -124f, 24f, 30f, 5f, sailCol)
        squareSail(-74f, -130f, -160f, 18f, 22f, 4f, sailCol)
        val spanker = Path().apply { moveTo(px(-74f), py(-130f)); lineTo(px(-114f), py(-98f)); lineTo(px(-112f), py(-70f)); lineTo(px(-74f), py(-72f)); close() }
        gradPath(spanker, LinearGradient(px(-114f), 0f, px(-74f), 0f, if (sunL) sailCol else k.darken(sailCol, 0.22f), if (sunL) k.darken(sailCol, 0.22f) else sailCol, Shader.TileMode.CLAMP))

        // Hull.
        val hull = Path().apply {
            moveTo(px(-124f), py(-66f)); lineTo(px(-86f), py(-66f)); lineTo(px(-82f), py(-42f))
            cubicTo(px(-30f), py(-35f), px(40f), py(-36f), px(92f), py(-46f))
            lineTo(px(98f), py(-55f)); lineTo(px(128f), py(-52f))
            quadTo(px(124f), py(-20f), px(104f), py(4f))
            lineTo(px(-102f), py(4f)); close()
        }
        gradPath(hull, LinearGradient(0f, py(-66f), 0f, py(4f), k.lighten(wood, 0.1f), k.darken(wood, 0.3f), Shader.TileMode.CLAMP))
        clipped(hull) {
            val band = Path().apply {
                moveTo(px(-124f), py(-52f)); lineTo(px(-84f), py(-52f)); lineTo(px(-80f), py(-31f))
                cubicTo(px(-30f), py(-25f), px(40f), py(-26f), px(92f), py(-36f)); lineTo(px(130f), py(-40f))
            }
            stroke(band, l.shade(0xFFD9B04A.toInt()), 5f)
            // Gun ports along the band.
            for (i in 0 until 9) {
                val x = -66f + i * 17f
                val y = -28f - 0.04f * (x + 70f)
                brush.shader = null; brush.color = l.shade(0xFF14100C.toInt())
                c.drawRect(px(x - 3.4f), py(y - 3f), px(x + 3.4f), py(y + 3f), brush)
            }
            brush.color = l.shade(0xFF14100C.toInt())
            c.drawRect(px(-130f), py(-4f), px(140f), py(8f), brush)
            sideShade(-122f, 130f, -80f, 8f, 56)
        }
        // Stern windows and lantern.
        for (wx in floatArrayOf(-118f, -108f, -98f)) {
            fillPath(
                Path().apply { moveTo(px(wx), py(-62f)); lineTo(px(wx + 6f), py(-62f)); lineTo(px(wx + 6f), py(-55f)); lineTo(px(wx), py(-55f)); close() },
                if (lamp) 0xFFFFD27A.toInt() else l.shade(0xFFE8D79A.toInt()),
            )
        }
        fillOval(-128f, -78f, -120f, -69f, if (lamp) 0xFFFFC870.toInt() else l.shade(0xFFD9B04A.toInt()))
        stroke(Path().apply { moveTo(px(-124f), py(-67f)); lineTo(px(-86f), py(-67f)) }, l.shade(0xFFEDE6D4.toInt()), 1.8f)

        // Yards on the lowest sails, rigging between the mastheads, shrouds and pennants.
        val wire = k.alpha(l.shade(0xFF22282D.toInt()), 150)
        line(62f, -204f, -8f, -246f, wire, 0.9f)
        line(-8f, -246f, -74f, -196f, wire, 0.9f)
        line(62f, -200f, 36f, -48f, wire, 0.9f); line(62f, -200f, 90f, -48f, wire, 0.9f)
        line(-8f, -240f, -36f, -40f, wire, 0.9f); line(-8f, -240f, 22f, -40f, wire, 0.9f)
        line(-74f, -192f, -98f, -66f, wire, 0.9f); line(-74f, -192f, -50f, -66f, wire, 0.9f)
        line(62f, -204f, 164f, -77f, wire, 0.9f)
        for ((fx, fy) in arrayOf(62f to -204f, -8f to -246f, -74f to -196f)) {
            fillPath(
                Path().apply { moveTo(px(fx), py(fy)); lineTo(px(fx - 24f), py(fy + 4f)); lineTo(px(fx), py(fy + 9f)); close() },
                l.shade(0xFFC8402E.toInt()),
            )
        }
        line(-120f, -67f, -120f, -108f, dark, 1.6f)
        fillPath(
            Path().apply { moveTo(px(-120f), py(-108f)); lineTo(px(-100f), py(-106f)); lineTo(px(-100f), py(-97f)); lineTo(px(-120f), py(-98f)); close() },
            l.shade(0xFFC8402E.toInt()),
        )
    }
    return a.finish()
}

// ---------------------------------------------------------------------------------------------
// Motor yacht
// ---------------------------------------------------------------------------------------------

/**
 * A sleek white motor yacht: navy lower hull and a band of hull windows, a raked main cabin of dark glass,
 * a flybridge with a radar arch and dome, a flag at the stern and a guest on the aft deck. It runs bow up.
 */
private fun paintMotorYacht(k: PaintKit): Pair<Bitmap, Bitmap?>? {
    val a = BoatArt.create(k, MOTOR_YACHT) ?: return null
    with(a) {
        val white = l.shade(0xFFF7F8F8.toInt())
        val glass = if (lamp) 0xFFFFD27A.toInt() else l.shade(0xFF1B2A38.toInt())
        val navy = l.shade(0xFF16294A.toInt())
        fun sheer(dy: Float, full: Boolean) = Path().apply {
            moveTo(px(-108f), py(-28f + dy))
            cubicTo(px(-30f), py(-24f + dy), px(50f), py(-28f + dy), px(106f), py(-42f + dy))
            if (full) quadTo(px(120f), py(-46f + dy), px(130f), py(-46f + dy))
        }
        figure(-92f, -26f, l.shade(0xFFE8527A.toInt()), hat = l.shade(0xFFF4F0E4.toInt()), tall = 15f)

        val hull = Path().apply {
            moveTo(px(-108f), py(-28f))
            cubicTo(px(-30f), py(-24f), px(50f), py(-28f), px(106f), py(-42f))
            quadTo(px(120f), py(-46f), px(130f), py(-46f))
            quadTo(px(120f), py(-16f), px(100f), py(4f))
            lineTo(px(-102f), py(4f)); close()
        }
        gradPath(hull, LinearGradient(0f, py(-46f), 0f, py(4f), white, k.darken(white, 0.2f), Shader.TileMode.CLAMP))
        clipped(hull) {
            brush.shader = null; brush.color = navy
            c.drawRect(px(-130f), py(-9f), px(140f), py(8f), brush)
            line(-130f, -9.6f, 140f, -9.6f, k.alpha(Color.WHITE, 200), 1.3f)
            stroke(sheer(6f, true), navy, 2f)
            fillPath(
                Path().apply { moveTo(px(-70f), py(-22f)); lineTo(px(60f), py(-27f)); lineTo(px(58f), py(-19f)); lineTo(px(-68f), py(-15f)); close() },
                glass,
            )
            for (x in intArrayOf(-48, -22, 4, 30)) line(x.toFloat(), -21f, x + 0.6f, -16.5f, white, 1.6f)
            sideShade(-110f, 130f, -80f, 8f, 48)
        }
        // Main cabin, flybridge and arch.
        val cabin = Path().apply {
            moveTo(px(-90f), py(-27f)); lineTo(px(-84f), py(-58f)); lineTo(px(34f), py(-62f)); lineTo(px(70f), py(-36f)); lineTo(px(70f), py(-31f)); close()
        }
        gradPath(cabin, LinearGradient(0f, py(-62f), 0f, py(-30f), white, k.darken(white, 0.14f), Shader.TileMode.CLAMP))
        val cabinGlass = Path().apply { moveTo(px(-76f), py(-54f)); lineTo(px(30f), py(-57f)); lineTo(px(60f), py(-38f)); lineTo(px(-78f), py(-38f)); close() }
        fillPath(cabinGlass, glass)
        clipped(cabinGlass) {
            if (!lamp) fillPath(Path().apply { moveTo(px(-40f), py(-58f)); lineTo(px(-26f), py(-58f)); lineTo(px(-52f), py(-36f)); lineTo(px(-66f), py(-36f)); close() }, k.alpha(Color.WHITE, 36))
            for (x in intArrayOf(-56, -26, 6, 36)) line(x.toFloat(), -56f, x - 3f, -38f, white, 1.8f)
        }
        val fly = Path().apply { moveTo(px(-64f), py(-62f)); lineTo(px(-58f), py(-84f)); lineTo(px(-8f), py(-86f)); lineTo(px(14f), py(-64f)); close() }
        gradPath(fly, LinearGradient(0f, py(-86f), 0f, py(-62f), white, k.darken(white, 0.12f), Shader.TileMode.CLAMP))
        fillPath(Path().apply { moveTo(px(-54f), py(-80f)); lineTo(px(-10f), py(-82f)); lineTo(px(8f), py(-66f)); lineTo(px(-56f), py(-66f)); close() }, glass)
        line(-68f, -87.5f, 18f, -85f, k.darken(white, 0.18f), 3f)
        stroke(Path().apply { moveTo(px(-48f), py(-87f)); lineTo(px(-44f), py(-108f)); lineTo(px(-14f), py(-108f)); lineTo(px(-10f), py(-87f)) }, white, 2.4f)
        fillOval(-35f, -120f, -23f, -108f, l.shade(0xFFEDEFF1.toInt()))
        line(-18f, -108f, -18f, -128f, l.shade(0xFF6E767C.toInt()), 1.2f)
        // Bow rail and stern flag.
        val rail = k.alpha(l.shade(0xFFEDEDED.toInt()), 190)
        for (x in floatArrayOf(78f, 92f, 106f)) line(x, -36f - (x - 70f) * 0.17f, x, -47f - (x - 70f) * 0.17f, rail, 1.1f)
        line(78f, -48f, 106f, -53f, rail, 1.1f)
        line(-106f, -28f, -106f, -62f, l.shade(0xFF6E767C.toInt()), 1.4f)
        fillPath(
            Path().apply { moveTo(px(-106f), py(-62f)); lineTo(px(-90f), py(-60f)); lineTo(px(-90f), py(-52f)); lineTo(px(-106f), py(-54f)); close() },
            l.shade(0xFFC8402E.toInt()),
        )
        if (lamp) fillOval(-30f, -125f, -26f, -121f, 0xFFFFF2C0.toInt())
    }
    return a.finish()
}
