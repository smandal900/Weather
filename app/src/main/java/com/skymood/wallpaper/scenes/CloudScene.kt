package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import com.skymood.wallpaper.particles.Particle
import com.skymood.wallpaper.particles.ParticlePool
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Drifting clouds in three depth layers.
 *
 * Each cloud is a cluster of overlapping circles filled with one opaque vertical gradient
 * (bright tops, shaded bases), so overlaps never show and clouds properly hide what is behind
 * them. A faint, slightly larger rim pass softens the outline. Distant layers are smaller,
 * slower and tinted towards the sky colour. The number of clouds follows the cloud cover and
 * the drift speed follows the wind. On a partly cloudy day the sun shines from behind them.
 *
 * @param style colours and placement: fair-weather, rain, storm or snow clouds.
 * @param drawSky false when composed into another scene.
 */
class CloudScene(
    private val style: Style = Style.FAIR,
    private val drawSky: Boolean = true,
    /** Keeps the cloud cover inside a range, e.g. 25..55 for Partly cloudy, 85..100 for Overcast. */
    private val coverRange: IntRange = 0..100,
    private val skyOverride: SceneType? = null,
) : BaseScene() {

    enum class Style { FAIR, OVERCAST, RAIN, STORM, SNOW }

    private val clouds = ParticlePool(MAX_CLOUDS)
    private val bodyPaints = Array(LAYERS) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val highlightPaints = Array(LAYERS) { Paint(Paint.ANTI_ALIAS_FLAG) }
    /**
     * Fluffy cloud sprites, one per template, painted once from dozens of soft, shaded puffs
     * (bright tops, greyer bases) in neutral grey, then tinted per layer when drawn.
     */
    private val sprites = arrayOfNulls<android.graphics.Bitmap>(TEMPLATES)
    private var spriteScale = 1f
    private val spritePaints = Array(LAYERS) { Paint(Paint.FILTER_BITMAP_FLAG) }
    private val dst = android.graphics.RectF()

    private fun bakeSprites() {
        for (s in sprites) s?.recycle()
        spriteScale = unit * 1.3f
        val rnd = Random(77)
        for (t in 0 until TEMPLATES) {
            val bw = (SPRITE_W * spriteScale).toInt().coerceAtLeast(8)
            val bh = (SPRITE_H * spriteScale).toInt().coerceAtLeast(8)
            val bmp = runCatching { android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888) }.getOrNull()
            sprites[t] = bmp
            bmp ?: continue
            val c = Canvas(bmp)
            c.scale(spriteScale, spriteScale)
            c.translate(0f, -SPRITE_TOP)
            val n = templateCount[t]
            // Collect soft sub-puffs, then paint from the bottom up so bright tops sit in front.
            val px = ArrayList<Float>(); val py = ArrayList<Float>(); val pr = ArrayList<Float>()
            for (i in 0 until n) {
                val r = templateR[t][i]
                repeat(10) {
                    val a = rnd.nextFloat() * 6.283f
                    val d = r * 0.6f * kotlin.math.sqrt(rnd.nextFloat())
                    px += templateX[t][i] + kotlin.math.cos(a) * d
                    py += templateY[t][i] + kotlin.math.sin(a) * d * 0.8f
                    pr += r * (0.32f + rnd.nextFloat() * 0.33f)
                }
            }
            val order = py.indices.sortedByDescending { py[it] }
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (y in py) { minY = minOf(minY, y); maxY = maxOf(maxY, y) }
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            // Dense core so no sky shows through the middle.
            p.color = Color.rgb(170, 175, 182)
            for (i in 0 until n) c.drawCircle(templateX[t][i], templateY[t][i] + templateR[t][i] * 0.1f, templateR[t][i] * 0.72f, p)
            for (k in order) {
                val f = ((py[k] - minY) / (maxY - minY + 1f)).coerceIn(0f, 1f)
                val v = (255 * (1f - 0.42f * f + (rnd.nextFloat() - 0.5f) * 0.06f)).toInt().coerceIn(0, 255)
                val col = Color.rgb(v, v, (v + 6).coerceAtMost(255))
                p.shader = android.graphics.RadialGradient(
                    px[k], py[k], pr[k],
                    intArrayOf(col, col and 0x00FFFFFF or 0xCC000000.toInt(), col and 0x00FFFFFF),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                )
                c.drawCircle(px[k], py[k], pr[k], p)
            }
        }
    }

    /** Soft outer edge so clouds fade into the sky instead of ending in a hard line. */
    private val softPaints = Array(LAYERS) { Paint(Paint.ANTI_ALIAS_FLAG) }

    // Thin, wide stratus streaks far behind the clouds when the sky is mostly covered.
    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val streakX = FloatArray(STREAKS)
    private val streakY = FloatArray(STREAKS)
    private val streakW = FloatArray(STREAKS)
    private val veilPaint = Paint()
    private val sun = SunGlow()
    private var unit = 1f
    private var timeMs = 0f

    // Puff layouts in cloud units (a cloud is about 420 units wide). Built once.
    private val templateX = Array(TEMPLATES) { FloatArray(MAX_PUFFS) }
    private val templateY = Array(TEMPLATES) { FloatArray(MAX_PUFFS) }
    private val templateR = Array(TEMPLATES) { FloatArray(MAX_PUFFS) }
    private val templateCount = IntArray(TEMPLATES)

    private val skyType: SceneType
        get() = skyOverride ?: when (style) {
            Style.FAIR -> SceneType.PARTLY_CLOUDY
            Style.OVERCAST -> SceneType.OVERCAST
            Style.SNOW -> SceneType.SNOW
            Style.RAIN -> SceneType.HEAVY_RAIN
            Style.STORM -> SceneType.STORM
        }

    init {
        buildTemplates()
        applyColors()
    }

    /** Cumulus-like outlines: a flat base and a taller, lumpy middle. */
    private fun buildTemplates() {
        val rnd = Random(11)
        for (t in 0 until TEMPLATES) {
            val n = 7 + rnd.nextInt(MAX_PUFFS - 7 + 1)
            templateCount[t] = n
            for (i in 0 until n) {
                val f = i / (n - 1f)
                val bump = sin(PI * f).toFloat()
                val r = 42f + bump * (38f + rnd.nextFloat() * 40f)
                templateR[t][i] = r
                templateX[t][i] = 40f + f * 340f + (rnd.nextFloat() - 0.5f) * 24f
                // Every puff's bottom sits near y = 160, which gives the flat base.
                templateY[t][i] = 160f - r + rnd.nextFloat() * 10f
            }
        }
    }

    override fun onResize() {
        unit = width / 1080f
        bakeSprites()
        sun.setup(min(width, height) * 0.075f, warm = timeOfDay != TimeOfDay.DAY)
        for (i in 0 until STREAKS) {
            streakX[i] = Random.nextFloat() * width
            streakY[i] = height * (0.05f + i * 0.11f + Random.nextFloat() * 0.04f)
            streakW[i] = width * (0.5f + Random.nextFloat() * 0.5f)
        }
        clouds.clear()
        fill(randomX = true)
    }

    override fun onIntensityChanged() = fill(randomX = true)

    override fun onTimeOfDayChanged() {
        applyColors()
        if (width > 1) sun.setup(min(width, height) * 0.075f, warm = timeOfDay != TimeOfDay.DAY)
    }

    override fun setEnvironment(windKmh: Float, cloudCover: Int, timeOfDay: TimeOfDay) {
        super.setEnvironment(windKmh, cloudCover.coerceIn(coverRange.first, coverRange.last), timeOfDay)
    }

    private fun applyColors() {
        val night = timeOfDay == TimeOfDay.NIGHT
        val warm = timeOfDay == TimeOfDay.DAWN || timeOfDay == TimeOfDay.DUSK
        val light: Int
        val shade: Int
        when (style) {
            Style.FAIR -> when {
                night -> { light = 0xFF4E586C.toInt(); shade = 0xFF262C39.toInt() }
                warm -> { light = 0xFFFFE3D2.toInt(); shade = 0xFFA88596.toInt() }
                else -> { light = 0xFFFFFFFF.toInt(); shade = 0xFFC3CEDB.toInt() }
            }
            Style.OVERCAST -> if (night) { light = 0xFF3C4350.toInt(); shade = 0xFF1C2028.toInt() }
            else { light = 0xFFC7CCD4.toInt(); shade = 0xFF838B97.toInt() }
            Style.SNOW -> if (night) { light = 0xFF5A6378.toInt(); shade = 0xFF2B303C.toInt() }
            else { light = 0xFFEEF1F5.toInt(); shade = 0xFFA6AFBC.toInt() }
            Style.RAIN -> if (night) { light = 0xFF3A414E.toInt(); shade = 0xFF181B22.toInt() }
            else { light = 0xFF9AA3B0.toInt(); shade = 0xFF4E5663.toInt() }
            Style.STORM -> if (night) { light = 0xFF2C313B.toInt(); shade = 0xFF101217.toInt() }
            else { light = 0xFF6A727E.toInt(); shade = 0xFF30353E.toInt() }
        }
        val skyColor = SkyGradient.midColor(skyType, timeOfDay)
        for (l in 0 until LAYERS) {
            // Atmospheric perspective: far layers take on the sky colour instead of turning transparent.
            val haze = l * 0.28f
            val top = SkyGradient.blend(light, skyColor, haze)
            val base = SkyGradient.blend(shade, skyColor, haze)
            val shader = LinearGradient(
                0f, 0f, 0f, 165f,
                intArrayOf(top, top, base), floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP,
            )
            bodyPaints[l].shader = shader
            spritePaints[l].colorFilter = android.graphics.PorterDuffColorFilter(
                SkyGradient.blend(light, skyColor, haze * 0.8f), android.graphics.PorterDuff.Mode.MULTIPLY,
            )
            val edge = SkyGradient.blend(top, base, 0.35f)
            softPaints[l].shader = android.graphics.RadialGradient(
                0f, 0f, 1f, intArrayOf(edge, edge and 0x00FFFFFF or 0x66000000, 0), floatArrayOf(0f, 0.78f, 1f), Shader.TileMode.CLAMP,
            )
            highlightPaints[l].color = SkyGradient.blend(top, 0xFFFFFFFF.toInt(), 0.6f)
            highlightPaints[l].alpha = if (night) 20 else 50 - l * 10
        }
        val streak = SkyGradient.blend(shade, skyColor, 0.5f)
        streakPaint.shader = android.graphics.RadialGradient(
            0f, 0f, 1f, intArrayOf(streak and 0x00FFFFFF or 0x88000000.toInt(), streak and 0x00FFFFFF or 0x33000000, 0),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
        veilPaint.color = if (night) 0xFF161A22.toInt() else 0xFF9AA5B3.toInt()
    }

    private fun targetClouds(): Int {
        val cover = when (style) {
            Style.FAIR -> cloudCover / 100f
            else -> 0.85f
        }
        return (4 + 10 * maxOf(cover, intensity * 0.5f)).toInt().coerceIn(2, MAX_CLOUDS)
    }

    private fun fill(randomX: Boolean) {
        while (clouds.activeCount < targetClouds()) {
            val p = clouds.obtain() ?: break
            spawn(p, randomX)
        }
    }

    private fun spawn(p: Particle, randomX: Boolean) {
        p.layer = pickLayer()
        p.variant = Random.nextInt(TEMPLATES)
        val depth = 1f - p.layer * 0.28f
        val big = when (style) { Style.FAIR -> 1f; Style.OVERCAST -> 1.5f; else -> 1.3f }
        p.size = unit * depth * big * (0.9f + Random.nextFloat() * 0.7f)
        // Near clouds sit lower on screen, far ones higher, which reads as depth.
        val band = when (style) { Style.FAIR -> 0.5f; Style.OVERCAST -> 0.42f; else -> 0.24f }
        val top = when (style) { Style.FAIR -> 0.02f; Style.OVERCAST -> -0.05f; else -> -0.04f }
        p.y = height * (top + (1f - p.layer / (LAYERS - 1f)) * band * 0.6f + Random.nextFloat() * band * 0.4f)
        p.x = if (randomX) Random.nextFloat() * (width + 420f * p.size) - 420f * p.size else -430f * p.size
        p.vx = depth * (0.7f + Random.nextFloat() * 0.3f)
        p.phase = Random.nextFloat() * 6.28f
    }

    /** Keeps roughly even numbers of clouds per layer. */
    private fun pickLayer(): Int {
        var best = 0
        var bestCount = Int.MAX_VALUE
        val start = Random.nextInt(LAYERS)
        for (k in 0 until LAYERS) {
            val l = (start + k) % LAYERS
            var c = 0
            for (i in 0 until clouds.activeCount - 1) if (clouds[i].layer == l) c++
            if (c < bestCount) { bestCount = c; best = l }
        }
        return best
    }

    override fun update(dtMs: Long) {
        timeMs += dtMs
        val dt = dtMs / 1000f
        val speed = (9f + windKmh * 2f) * unit
        val target = targetClouds()
        for (i in clouds.activeCount - 1 downTo 0) {
            val p = clouds[i]
            p.x += p.vx * speed * dt
            if (p.x > width + 20f) {
                if (clouds.activeCount > target) clouds.release(i) else spawn(p, randomX = false)
            }
        }
        if (clouds.activeCount < target) fill(randomX = false)
        for (i in 0 until STREAKS) {
            streakX[i] += speed * 0.25f * dt
            if (streakX[i] - streakW[i] > width) streakX[i] = -streakW[i]
        }
    }

    override fun drawBackground(canvas: Canvas) {
        if (drawSky) {
            sky.draw(canvas, width, height, skyType, timeOfDay)
            if (style == Style.FAIR || style == Style.OVERCAST) {
                // The more cloud cover, the greyer and flatter the sky.
                veilPaint.alpha = (cloudCover / 100f * 0.5f * 255).toInt().coerceIn(0, 140)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), veilPaint)
            }
        }
        val shift = parallaxPx
        if (style != Style.FAIR || cloudCover > 50) {
            for (i in 0 until STREAKS) {
                canvas.save()
                canvas.translate(streakX[i] + shift * 0.2f, streakY[i])
                canvas.scale(streakW[i], height * 0.025f)
                canvas.drawCircle(0f, 0f, 1f, streakPaint)
                canvas.restore()
            }
        }
        if (style == Style.FAIR && timeOfDay != TimeOfDay.NIGHT && cloudCover < 95) {
            val partly = cloudCover < 55
            val strength = if (partly) 1f else (1.2f - cloudCover / 100f).coerceIn(0.3f, 0.6f)
            sun.draw(
                canvas, width * 0.3f + shift * 0.3f, height * 0.3f, timeMs, strength,
                showCore = partly, rays = partly,
            )
        }
        for (layer in LAYERS - 1 downTo 0) {
            val ls = shift * (1f - layer * 0.3f)
            for (i in 0 until clouds.activeCount) {
                val p = clouds[i]
                if (p.layer == layer) drawCloud(canvas, p, ls, layer)
            }
        }
    }

    private fun drawCloud(canvas: Canvas, p: Particle, shift: Float, layer: Int) {
        val t = p.variant
        val n = templateCount[t]
        val xs = templateX[t]
        val ys = templateY[t]
        val rs = templateR[t]
        val sprite = sprites[t]
        if (sprite != null) {
            dst.set(
                p.x + shift, p.y + SPRITE_TOP * p.size,
                p.x + shift + SPRITE_W * p.size, p.y + (SPRITE_TOP + SPRITE_H) * p.size,
            )
            canvas.drawBitmap(sprite, null, dst, spritePaints[layer])
            return
        }
        val body = bodyPaints[layer]
        val hi = highlightPaints[layer]
        val breathe = 1f + 0.02f * sin(timeMs / 3000f + p.phase)
        canvas.save()
        canvas.translate(p.x + shift, p.y)
        canvas.scale(p.size, p.size)
        val soft = softPaints[layer]
        for (i in 0 until n) {
            val r = rs[i] * breathe * 1.22f
            canvas.save()
            canvas.translate(xs[i], ys[i])
            canvas.scale(r, r)
            canvas.drawCircle(0f, 0f, 1f, soft)
            canvas.restore()
        }
        for (i in 0 until n) canvas.drawCircle(xs[i], ys[i], rs[i] * breathe * 0.94f, body)
        // Sunlit tops: smaller translucent puffs nudged up and left give the clouds volume.
        for (i in 0 until n) {
            val r = rs[i] * breathe
            canvas.drawCircle(xs[i] - r * 0.12f, ys[i] - r * 0.22f, r * 0.68f, hi)
        }
        canvas.restore()
    }

    private companion object {
        const val MAX_CLOUDS = 14
        const val LAYERS = 3
        const val TEMPLATES = 5
        const val MAX_PUFFS = 10
        const val STREAKS = 5
        // Sprite bounds in cloud units.
        const val SPRITE_W = 440f
        const val SPRITE_H = 270f
        const val SPRITE_TOP = -80f
    }
}
