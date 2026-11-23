package com.skymood.wallpaper.landscape

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader

/**
 * A small sailing yacht seen side-on, for the far water: white hull with a blue stripe, a curved
 * mainsail and jib lit from the sun's side, and a stern lantern after dark. Sails right.
 */
internal fun PaintKit.paintSailboat(): Bitmap? {
    val s = w * 0.0006f
    val bw = (120 * s).toInt().coerceAtLeast(24)
    val bh = (125 * s).toInt().coerceAtLeast(26)
    val (c, bmp) = newSprite(bw, bh) ?: return null
    val ox = bw / 2f; val oy = bh * 0.9f
    fun px(x: Float) = ox + x * s
    fun py(y: Float) = oy + y * s

    // Hull.
    val hull = Path()
    hull.moveTo(px(-42f), py(-9f)); hull.lineTo(px(46f), py(-11f))
    hull.quadTo(px(40f), py(4f), px(26f), py(5f)); hull.lineTo(px(-30f), py(5f))
    hull.quadTo(px(-40f), py(-2f), px(-42f), py(-9f)); hull.close()
    shader = LinearGradient(0f, py(-11f), 0f, py(5f), l.shade(0xFFFFFFFF.toInt()), darken(l.shade(0xFFE8EEF2.toInt()), 0.25f), Shader.TileMode.CLAMP)
    c.drawPath(hull, fill)
    shader = null
    fill.color = l.shade(0xFF1F5FA8.toInt())
    val stripe = Path()
    stripe.moveTo(px(-41f), py(-8f)); stripe.lineTo(px(45f), py(-10f)); stripe.lineTo(px(44f), py(-6.5f)); stripe.lineTo(px(-40f), py(-4.5f)); stripe.close()
    c.drawPath(stripe, fill)

    // Mast, boom and the stay to the bow.
    line.strokeWidth = 1.5f * s; line.color = l.shade(0xFF3A3A40.toInt())
    c.drawLine(px(0f), py(-9f), px(0f), py(-110f), line)
    c.drawLine(px(0f), py(-16f), px(-38f), py(-13f), line)
    line.strokeWidth = 0.9f * s
    c.drawLine(px(0f), py(-108f), px(46f), py(-11f), line)

    // Mainsail and jib, belling out with the wind and shaded on the far side.
    fun sail(path: Path, x0: Float, x1: Float) {
        val base = l.shade(0xFFF8F4EA.toInt())
        shader = if (sunOnLeft) LinearGradient(px(x0), 0f, px(x1), 0f, lighten(base, 0.1f), darken(base, 0.3f), Shader.TileMode.CLAMP)
        else LinearGradient(px(x0), 0f, px(x1), 0f, darken(base, 0.3f), lighten(base, 0.1f), Shader.TileMode.CLAMP)
        c.drawPath(path, fill)
        shader = null
    }
    val main = Path()
    main.moveTo(px(-2f), py(-104f)); main.quadTo(px(-26f), py(-58f), px(-40f), py(-14f)); main.lineTo(px(-2f), py(-17f)); main.close()
    sail(main, -40f, -2f)
    val jib = Path()
    jib.moveTo(px(3f), py(-100f)); jib.quadTo(px(36f), py(-56f), px(45f), py(-13f)); jib.lineTo(px(3f), py(-17f)); jib.close()
    sail(jib, 3f, 45f)
    // A seam on each sail, and a pennant at the masthead.
    line.strokeWidth = 0.8f * s; line.color = alpha(Color.BLACK, 40)
    c.drawLine(px(-2f), py(-70f), px(-27f), py(-44f), line)
    c.drawLine(px(3f), py(-66f), px(30f), py(-40f), line)
    fill.color = l.shade(0xFFE74C3C.toInt())
    val pen = Path()
    pen.moveTo(px(0f), py(-110f)); pen.lineTo(px(-12f), py(-107f)); pen.lineTo(px(0f), py(-103f)); pen.close()
    c.drawPath(pen, fill)

    if (l.windowsLit) {
        fill.color = 0xFFFFEAA7.toInt()
        c.drawCircle(px(-39f), py(-12f), 2.4f * s, fill)
    }
    return bmp
}
