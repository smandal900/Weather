package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.skymood.domain.SceneType
import com.skymood.wallpaper.particles.Particle
import com.skymood.wallpaper.particles.ParticlePool
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/** Snowflakes that sway as they fall and drift with the wind. */
class SnowScene : BaseScene() {

    private val flakes = ParticlePool(MAX_FLAKES)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var unit = 1f
    private var timeMs = 0f

    private fun targetFlakes() = (100 + 300 * intensity).toInt().coerceIn(0, MAX_FLAKES)

    override fun onResize() {
        unit = width / 1080f
        flakes.clear()
        fill(randomY = true)
    }

    override fun onIntensityChanged() = fill(randomY = true)

    private fun fill(randomY: Boolean) {
        while (flakes.activeCount < targetFlakes()) {
            val p = flakes.obtain() ?: break
            spawn(p, randomY)
        }
    }

    private fun spawn(p: Particle, randomY: Boolean) {
        p.layer = Random.nextInt(3) // 0 = near
        val depth = 1f - p.layer * 0.3f
        p.size = unit * depth * (2.5f + Random.nextFloat() * 4f)
        p.vy = height * depth * (0.06f + Random.nextFloat() * 0.07f)
        p.phase = Random.nextFloat() * 6.28f
        p.length = 0.6f + Random.nextFloat() * 1.2f // sway frequency
        p.alpha = depth * (0.6f + Random.nextFloat() * 0.4f)
        p.x = Random.nextFloat() * width * 1.4f - width * 0.4f
        p.y = if (randomY) Random.nextFloat() * height else -p.size * 2f
    }

    override fun update(dtMs: Long) {
        timeMs += dtMs
        val dt = dtMs / 1000f
        val drift = tan(windAngle)
        val target = targetFlakes()
        for (i in flakes.activeCount - 1 downTo 0) {
            val p = flakes[i]
            p.y += p.vy * dt
            p.x += p.vy * drift * dt
            if (p.y > height + p.size * 2f || p.x > width + 40f) {
                if (flakes.activeCount > target) flakes.release(i) else spawn(p, randomY = false)
            }
        }
        if (flakes.activeCount < target) fill(randomY = false)
    }

    override fun draw(canvas: Canvas) {
        sky.draw(canvas, width, height, SceneType.SNOW, timeOfDay)
        val shift = parallaxPx
        val t = timeMs / 1000f
        for (i in 0 until flakes.activeCount) {
            val p = flakes[i]
            val sway = sin(t * p.length + p.phase) * 18f * unit
            paint.alpha = (255 * p.alpha).toInt()
            canvas.drawCircle(p.x + sway + shift * (1f - p.layer * 0.3f), p.y, p.size, paint)
        }
    }

    private companion object {
        const val MAX_FLAKES = 500
    }
}
