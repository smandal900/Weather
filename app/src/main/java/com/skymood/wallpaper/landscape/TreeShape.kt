package com.skymood.wallpaper.landscape

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import kotlin.random.Random

/**
 * The outline of a real broadleaf tree (assets/trees/oak.path, a simplified vector trace, 1000 units
 * tall and about 1162 wide). Only the silhouette is used: it carries the true ragged edge and
 * the gaps between leaves and branches, and the app paints the colours and leaf texture itself.
 * When the file is missing, landscapes fall back to the drawn leafy trees.
 */
object TreeShape {
    const val WIDTH = 1162f
    const val HEIGHT = 1000f
    private val ready = CountDownLatch(1)
    @Volatile private var shape: Path? = null
    private var started = false

    @Synchronized
    fun init(assets: AssetManager) {
        if (started) return
        started = true
        Executors.newSingleThreadExecutor { r -> Thread(r, "SkyMoodTreeShape").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
            .execute {
                try {
                    shape = runCatching { parse(assets.open("trees/oak.path").bufferedReader().use { it.readText() }) }.getOrNull()
                } finally {
                    ready.countDown()
                }
            }
    }

    /** The outline, or null when it is not bundled. Call from a background (painting) thread. */
    fun path(): Path? {
        if (started) ready.await(2, TimeUnit.SECONDS)
        return shape
    }

    /** Minimal reader for the "M x y l dx dy ... z" data the outline is stored in. */
    private fun parse(d: String): Path {
        val p = Path()
        var i = 0
        var x = 0f; var y = 0f
        fun num(): Float {
            while (i < d.length && d[i] == ' ') i++
            val s = i
            if (i < d.length && d[i] == '-') i++
            while (i < d.length && d[i].isDigit()) i++
            return d.substring(s, i).toFloat()
        }
        var cmd = ' '
        while (i < d.length) {
            val ch = d[i]
            when {
                ch == 'M' -> { i++; x = num(); y = num(); p.moveTo(x, y); cmd = 'l' }
                ch == 'l' -> { i++; cmd = 'l' }
                ch == 'z' || ch == 'Z' -> { i++; p.close() }
                ch == ' ' -> i++
                else -> if (cmd == 'l') { x += num(); y += num(); p.lineTo(x, y) } else i++
            }
        }
        return p
    }
}

/**
 * Paints a leafy tree with the real outline: leaf-coloured crown (lit on top and on the sun's
 * side, dark underneath, with a texture of leaf dabs) fading into brown limbs and trunk where the
 * outline narrows. [th] is the tree's height; returns false (nothing drawn) if the outline is missing.
 */
internal fun PaintKit.shapedTree(c: Canvas, x: Float, baseY: Float, th: Float, lit: Int, dark: Int, trunk: Int, rnd: Random): Boolean {
    val outline = TreeShape.path() ?: return false
    val s = th / TreeShape.HEIGHT
    val wPx = (TreeShape.WIDTH * s).toInt().coerceAtLeast(8)
    val hPx = th.toInt().coerceAtLeast(8)
    val (cc, color) = newSprite(wPx, hPx) ?: return false
    val (mc, mask) = newSprite(wPx, hPx) ?: run { color.recycle(); return false }
    val deep = darken(dark, 0.42f)

    // Crown colour: lit at the top, deep at the bottom, lighter on the sun's side.
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val bright = lighten(mix(lit, 0xFFB4D83C.toInt(), 0.25f), 0.08f)   // sunlit, yellow-green
    val mid = mix(dark, lit, 0.45f)
    val shade = mix(dark, deep, 0.4f)
    p.shader = LinearGradient(0f, 0f, 0f, hPx * 0.62f, mix(mid, bright, 0.4f), shade, Shader.TileMode.CLAMP)
    cc.drawRect(0f, 0f, wPx.toFloat(), hPx.toFloat(), p)
    // Leaf dabs: many small blobs in varied greens, brighter towards the top and the sun.
    val dabs = (3200 * (th / (300f * unit)).coerceIn(0.4f, 2.2f)).toInt()
    for (i in 0 until dabs) {
        val dx = rnd.nextFloat(); val dy = rnd.nextFloat() * 0.62f
        val lightness = (0.72f - dy * 0.9f - sun * (dx - 0.5f) * 0.5f + (rnd.nextFloat() - 0.5f) * 0.5f).coerceIn(0f, 1f)
        p.shader = null
        p.color = jitter(mix(shade, bright, lightness), rnd, 0.14f)
        cc.drawCircle(dx * wPx, dy * hPx, hPx * (0.005f + 0.011f * rnd.nextFloat()), p)
    }
    // Sunlit tufts along the upper side of each leafy tier, as in the picture's layered canopy.
    for (tier in 0 until 7) {
        val yc = hPx * (0.1f + 0.075f * tier)
        repeat((150 * (th / (300f * unit)).coerceIn(0.4f, 2.2f)).toInt()) {
            val tx = rnd.nextFloat() * wPx
            val ty = yc + (rnd.nextFloat() - 0.3f) * hPx * 0.06f + sin(tx / wPx * 9f + tier * 1.7f) * hPx * 0.02f
            p.color = alpha(jitter(mix(bright, 0xFFE6EC6A.toInt(), 0.3f * rnd.nextFloat()), rnd, 0.1f), 120)
            cc.drawCircle(tx, ty, hPx * (0.004f + 0.007f * rnd.nextFloat()), p)
        }
    }
    // Limbs and trunk: brown that takes over below the crown, so the branches show as in the picture.
    val brown = lighten(mix(trunk, Color.BLACK, 0.05f), 0.22f)
    p.shader = LinearGradient(0f, hPx * 0.46f, 0f, hPx * 0.6f, alpha(brown, 0), brown, Shader.TileMode.CLAMP)
    cc.drawRect(0f, hPx * 0.46f, wPx.toFloat(), hPx.toFloat(), p)
    p.shader = null
    // Bark streaks on the trunk.
    p.strokeWidth = max(1f, th * 0.004f); p.style = Paint.Style.STROKE
    repeat(40) {
        p.color = alpha(if (rnd.nextBoolean()) Color.BLACK else lighten(brown, 0.3f), 70)
        val bx = wPx * (0.42f + 0.16f * rnd.nextFloat())
        cc.drawLine(bx, hPx * 0.7f, bx + (rnd.nextFloat() - 0.5f) * 6f, hPx.toFloat(), p)
    }
    p.style = Paint.Style.FILL

    // Cut the painted picture to the outline.
    val m = Paint(Paint.ANTI_ALIAS_FLAG); m.color = Color.WHITE
    mc.save(); mc.scale(s, s); mc.drawPath(outline, m); mc.restore()
    val cut = Paint(Paint.FILTER_BITMAP_FLAG); cut.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    mc.drawBitmap(color, 0f, 0f, cut)
    color.recycle()

    c.drawBitmap(mask, x - wPx / 2f, baseY - hPx + th * 0.01f, Paint(Paint.FILTER_BITMAP_FLAG))
    mask.recycle()
    return true
}

private fun max(a: Float, b: Float) = if (a > b) a else b
