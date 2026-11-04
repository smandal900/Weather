package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
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
 * Soft, shaded clouds drifting in three depth layers. Each cloud is a cluster of soft puffs:
 * a shadow puff below and a highlight puff above, so clouds look lit from the sky.
 * The number of clouds follows the cloud cover and the drift speed follows the wind.
 * On a cloudy day the sun glows through the gaps.
 *
 * @param style colours and placement: plain clouds, rain clouds, storm clouds or snow clouds.
 * @param drawSky false when composed into another scene.
 */
class CloudScene(
    private val style: Style = Style.FAIR,
    private val drawSky: Boolean = true,
) : BaseScene() {

    enum class Style { FAIR, RAIN, STORM, SNOW }

    private val clouds = ParticlePool(MAX_CLOUDS)
    private val shadowPaints = Array(LAYERS) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val lightPaints = Array(LAYERS) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val sun = SunGlow()
    private var unit = 1f
    private var timeMs = 0f

    // Puff layouts in cloud units (a cloud is about 420 x 200 units). Built once.
    private val templateX = Array(TEMPLATES) { FloatArray(MAX_PUFFS) }
    private val templateY = Array(TEMPLATES) { FloatArray(MAX_PUFFS) }
    private val templateR = Array(TEMPLATES) { FloatArray(MAX_PUFFS) }
    private val templateCount = IntArray(TEMPLATES)

    init {
        buildTemplates()
        applyColors()
    }

    private fun buildTemplates() {
        val rnd = Random(7)
        for (t in 0 until TEMPLATES) {
            val n = 7 + rnd.nextInt(MAX_PUFFS - 7 + 1)
            templateCount[t] = n
            for (i in 0 until n) {
                val f = i / (n - 1f)
                val bump = sin(PI * f).toFloat()
                templateX[t][i] = 30f + f * 360f + (rnd.nextFloat() - 0.5f) * 30f
                templateR[t][i] = 48f + bump * (40f + rnd.nextFloat() * 45f)
                // Flat-ish base, taller in the middle.
                templateY[t][i] = 150f - templateR[t][i] * 0.55f - bump * rnd.nextFloat() * 25f
            }
        }
    }

    override fun onResize() {
        unit = width / 1080f
        sun.setup(min(width, height) * 0.075f, warm = timeOfDay != TimeOfDay.DAY)
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
        val (shadow, light) = when (style) {
            Style.FAIR -> when {
                night -> 0xFF222836.toInt() to 0xFF505B72.toInt()
                warm -> 0xFF9C7B8E.toInt() to 0xFFFFE0CC.toInt()
                else -> 0xFFA9B8CC.toInt() to 0xFFFFFFFF.toInt()
            }
            Style.SNOW -> if (night) 0xFF262B36.toInt() to 0xFF59627A.toInt()
            else 0xFF97A1B0.toInt() to 0xFFF0F3F7.toInt()
            Style.RAIN -> if (night) 0xFF14171D.toInt() to 0xFF333A47.toInt()
            else 0xFF4A525F.toInt() to 0xFF8D97A5.toInt()
            Style.STORM -> if (night) 0xFF0E1015.toInt() to 0xFF262A33.toInt()
            else 0xFF2E333C.toInt() to 0xFF5E6672.toInt()
        }
        for (l in 0 until LAYERS) {
            // Far layers fade towards the sky colour.
            val fade = l * 0.22f
            shadowPaints[l].shader = puffShader(SkyGradient.blend(shadow, light, fade * 0.6f))
            lightPaints[l].shader = puffShader(light)
            val alpha = when (l) { 0 -> 245; 1 -> 205; else -> 160 }
            shadowPaints[l].alpha = alpha
            lightPaints[l].alpha = alpha
        }
    }

    /** Unit-radius soft puff, scaled per puff with canvas transforms. */
    private fun puffShader(color: Int): Shader {
        val solid = color or 0xFF000000.toInt()
        val edge = color and 0x00FFFFFF or 0x99000000.toInt()
        return RadialGradient(
            0f, 0f, 1f,
            intArrayOf(solid, solid, edge, Color.TRANSPARENT), floatArrayOf(0f, 0.55f, 0.8f, 1f),
            Shader.TileMode.CLAMP,
        )
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
        p.layer = Random.nextInt(LAYERS)
        p.variant = Random.nextInt(TEMPLATES)
        val depth = 1f - p.layer * 0.25f
        val big = if (style == Style.FAIR) 1f else 1.25f
        p.size = unit * depth * big * (0.95f + Random.nextFloat() * 0.9f)
        val band = if (style == Style.FAIR) 0.55f else 0.28f
        p.y = height * (-0.03f + Random.nextFloat() * band) + p.layer * height * 0.02f
        p.x = if (randomX) Random.nextFloat() * (width + 420f * p.size) - 420f * p.size else -430f * p.size
        p.vx = depth * (0.6f + Random.nextFloat() * 0.4f)
        p.phase = Random.nextFloat() * 6.28f
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
    }

    override fun draw(canvas: Canvas) {
        if (drawSky) {
            val skyType = when (style) {
                Style.FAIR -> SceneType.CLOUD
                Style.SNOW -> SceneType.SNOW
                Style.RAIN -> SceneType.RAIN
                Style.STORM -> SceneType.STORM
            }
            sky.draw(canvas, width, height, skyType, timeOfDay)
        }
        val shift = parallaxPx
        if (style == Style.FAIR && timeOfDay != TimeOfDay.NIGHT && cloudCover < 97) {
            val strength = (1.1f - cloudCover / 100f).coerceIn(0.35f, 1f)
            sun.draw(canvas, width * 0.3f + shift * 0.3f, height * 0.34f, timeMs, strength, showCore = cloudCover < 80)
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
        val shadow = shadowPaints[layer]
        val light = lightPaints[layer]
        val breathe = 1f + 0.025f * sin(timeMs / 2600f + p.phase)
        canvas.save()
        canvas.translate(p.x + shift, p.y)
        canvas.scale(p.size, p.size)
        // Shadow pass: puffs shifted down form the darker underside.
        for (i in 0 until n) {
            puff(canvas, xs[i], ys[i] + rs[i] * 0.22f, rs[i] * 1.04f * breathe, shadow)
        }
        // Light pass: smaller puffs shifted up form the sunlit tops.
        for (i in 0 until n) {
            puff(canvas, xs[i] - rs[i] * 0.05f, ys[i] - rs[i] * 0.12f, rs[i] * 0.86f * breathe, light)
        }
        canvas.restore()
    }

    private fun puff(canvas: Canvas, x: Float, y: Float, r: Float, paint: Paint) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(r, r)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
    }

    private companion object {
        const val MAX_CLOUDS = 14
        const val LAYERS = 3
        const val TEMPLATES = 5
        const val MAX_PUFFS = 10
    }
}
