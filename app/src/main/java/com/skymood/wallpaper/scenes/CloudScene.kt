package com.skymood.wallpaper.scenes

import android.graphics.Canvas
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
) : BaseScene() {

    enum class Style { FAIR, RAIN, STORM, SNOW }

    private val clouds = ParticlePool(MAX_CLOUDS)
    private val bodyPaints = Array(LAYERS) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val highlightPaints = Array(LAYERS) { Paint(Paint.ANTI_ALIAS_FLAG) }

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
        get() = when (style) {
            Style.FAIR -> SceneType.CLOUD
            Style.SNOW -> SceneType.SNOW
            Style.RAIN -> SceneType.RAIN
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
        val big = if (style == Style.FAIR) 1f else 1.3f
        p.size = unit * depth * big * (0.9f + Random.nextFloat() * 0.7f)
        // Near clouds sit lower on screen, far ones higher, which reads as depth.
        val band = if (style == Style.FAIR) 0.5f else 0.24f
        val top = if (style == Style.FAIR) 0.02f else -0.04f
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

    override fun draw(canvas: Canvas) {
        if (drawSky) {
            sky.draw(canvas, width, height, skyType, timeOfDay)
            if (style == Style.FAIR) {
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
        val body = bodyPaints[layer]
        val hi = highlightPaints[layer]
        val breathe = 1f + 0.02f * sin(timeMs / 3000f + p.phase)
        canvas.save()
        canvas.translate(p.x + shift, p.y)
        canvas.scale(p.size, p.size)
        for (i in 0 until n) canvas.drawCircle(xs[i], ys[i], rs[i] * breathe, body)
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
    }
}
