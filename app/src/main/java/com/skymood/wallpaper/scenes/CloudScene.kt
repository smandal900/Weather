package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import com.skymood.wallpaper.particles.Particle
import com.skymood.wallpaper.particles.ParticlePool
import kotlin.random.Random

/**
 * Slowly drifting cloud layers. The number of clouds follows the cloud cover and the drift
 * speed follows the wind.
 *
 * @param drawSky false when composed into another scene (the storm).
 * @param dark true for heavy storm clouds.
 */
class CloudScene(
    private val drawSky: Boolean = true,
    private val dark: Boolean = false,
) : BaseScene() {

    private val clouds = ParticlePool(MAX_CLOUDS)
    private val paints = Array(LAYERS) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val shape = Path()
    private var unit = 1f

    override fun onResize() {
        unit = width / 1080f
        buildShape()
        clouds.clear()
        fill(randomX = true)
    }

    override fun onIntensityChanged() = fill(randomX = true)

    override fun onTimeOfDayChanged() = applyColors()

    init {
        applyColors()
    }

    /** One cloud silhouette, about 400 x 160 units, reused for every cloud via canvas transforms. */
    private fun buildShape() {
        shape.reset()
        shape.addCircle(120f, 90f, 70f, Path.Direction.CW)
        shape.addCircle(200f, 60f, 85f, Path.Direction.CW)
        shape.addCircle(290f, 85f, 65f, Path.Direction.CW)
        shape.addCircle(60f, 120f, 45f, Path.Direction.CW)
        shape.addCircle(350f, 120f, 45f, Path.Direction.CW)
        shape.addRoundRect(40f, 100f, 380f, 165f, 32f, 32f, Path.Direction.CW)
    }

    private fun applyColors() {
        val night = timeOfDay == TimeOfDay.NIGHT
        val warm = timeOfDay == TimeOfDay.DAWN || timeOfDay == TimeOfDay.DUSK
        val base = when {
            dark && night -> 0xFF1E222A.toInt()
            dark -> 0xFF3C424C.toInt()
            night -> 0xFF3A4252.toInt()
            warm -> 0xFFCDB3AE.toInt()
            else -> 0xFFD3DAE2.toInt()
        }
        for (l in 0 until LAYERS) {
            val p = paints[l]
            p.color = base
            p.alpha = when (l) { 0 -> 175; 1 -> 135; else -> 100 } // 0 = nearest; kept soft so icons stay readable
        }
    }

    private fun targetClouds(): Int {
        val cover = if (dark) 1f else cloudCover / 100f
        return (3 + 11 * maxOf(cover, intensity * 0.6f)).toInt().coerceIn(2, MAX_CLOUDS)
    }

    private fun fill(randomX: Boolean) {
        while (clouds.activeCount < targetClouds()) {
            val p = clouds.obtain() ?: break
            spawn(p, randomX)
        }
    }

    private fun spawn(p: Particle, randomX: Boolean) {
        p.layer = Random.nextInt(LAYERS)
        val depth = 1f - p.layer * 0.28f
        p.size = unit * depth * (0.8f + Random.nextFloat() * 0.9f) // scale factor
        p.y = height * (0.04f + Random.nextFloat() * if (dark) 0.35f else 0.5f) * (1f + p.layer * 0.1f)
        p.x = if (randomX) Random.nextFloat() * (width + 400f * p.size) - 400f * p.size else -420f * p.size
        p.vx = depth * (0.5f + Random.nextFloat() * 0.5f)
    }

    override fun update(dtMs: Long) {
        val dt = dtMs / 1000f
        // Pixels per second: a gentle drift when calm, faster in strong wind.
        val speed = (10f + windKmh * 2.2f) * unit
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
        if (drawSky) sky.draw(canvas, width, height, SceneType.CLOUD, timeOfDay)
        val shift = parallaxPx
        // Far layers first.
        for (layer in LAYERS - 1 downTo 0) {
            val paint = paints[layer]
            val ls = shift * (1f - layer * 0.3f)
            for (i in 0 until clouds.activeCount) {
                val p = clouds[i]
                if (p.layer != layer) continue
                canvas.save()
                canvas.translate(p.x + ls, p.y)
                canvas.scale(p.size, p.size)
                canvas.drawPath(shape, paint)
                canvas.restore()
            }
        }
    }

    private companion object {
        const val MAX_CLOUDS = 16
        const val LAYERS = 3
    }
}
