package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import com.skymood.wallpaper.particles.ParticlePool
import kotlin.math.sin
import kotlin.random.Random

/** Hazy fog: soft banks drifting sideways at different depths over a uniform haze. */
class FogScene : BaseScene() {

    private val banks = ParticlePool(MAX_BANKS)
    private val bankPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hazePaint = Paint()
    private var timeMs = 0f

    override fun onResize() {
        rebuild()
        banks.clear()
        for (n in 0 until MAX_BANKS) {
            val p = banks.obtain() ?: break
            p.layer = n % 3
            p.x = Random.nextFloat() * width * 1.6f - width * 0.3f
            p.y = height * (0.2f + (n.toFloat() / MAX_BANKS) * 0.8f) + (Random.nextFloat() - 0.5f) * height * 0.08f
            p.size = 0.9f + Random.nextFloat() * 0.8f // scale
            p.vx = (6f + Random.nextFloat() * 10f) * (1f - p.layer * 0.25f)
            p.phase = Random.nextFloat() * 6.28f
        }
    }

    override fun onTimeOfDayChanged() = rebuild()

    private fun rebuild() {
        val night = timeOfDay == TimeOfDay.NIGHT
        val fog = if (night) 0x5A8A93A3 else 0x70E8ECEF
        // Unit-radius gradient scaled per bank with canvas transforms.
        bankPaint.shader = RadialGradient(
            0f, 0f, 1f, intArrayOf(fog, fog and 0x00FFFFFF or 0x30000000, Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        hazePaint.color = if (night) 0xFF7A8292.toInt() else 0xFFDDE2E6.toInt()
    }

    override fun update(dtMs: Long) {
        timeMs += dtMs
        val dt = dtMs / 1000f
        val wind = 1f + windKmh / 20f
        for (i in 0 until banks.activeCount) {
            val p = banks[i]
            p.x += p.vx * wind * dt * (width / 1080f)
            val half = width * 0.6f * p.size
            if (p.x - half > width) p.x = -half
        }
    }

    override fun draw(canvas: Canvas) {
        sky.draw(canvas, width, height, SceneType.FOG, timeOfDay)
        hazePaint.alpha = (60 + 80 * intensity.coerceAtMost(1f)).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), hazePaint)

        val shift = parallaxPx
        val t = timeMs / 1000f
        val strength = (0.5f + 0.5f * intensity).coerceAtMost(1f)
        bankPaint.alpha = (255 * strength).toInt()
        for (layer in 2 downTo 0) {
            for (i in 0 until banks.activeCount) {
                val p = banks[i]
                if (p.layer != layer) continue
                val breathe = 1f + 0.05f * sin(t * 0.3f + p.phase)
                canvas.save()
                canvas.translate(p.x + shift * (1f - layer * 0.3f), p.y)
                canvas.scale(width * 0.6f * p.size * breathe, height * 0.09f * p.size)
                canvas.drawCircle(0f, 0f, 1f, bankPaint)
                canvas.restore()
            }
        }
    }

    private companion object {
        const val MAX_BANKS = 14
    }
}
