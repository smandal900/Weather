package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import com.skymood.wallpaper.particles.Particle
import com.skymood.wallpaper.particles.ParticlePool
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * Snow: soft glowing flakes that sway as they fall and drift with the wind, under grey snow
 * clouds, above gentle snow-covered hills.
 */
class SnowScene : BaseScene() {

    private val flakes = ParticlePool(MAX_FLAKES)
    private val flakePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val clouds = CloudScene(CloudScene.Style.SNOW, drawSky = false)
    private val farHill = Path()
    private val nearHill = Path()
    private val farHillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nearHillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var unit = 1f
    private var timeMs = 0f

    init {
        // Unit-radius soft flake, scaled per flake.
        flakePaint.shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(Color.WHITE, 0xCCFFFFFF.toInt(), 0x33FFFFFF, Color.TRANSPARENT),
            floatArrayOf(0f, 0.35f, 0.65f, 1f), Shader.TileMode.CLAMP,
        )
    }

    private fun targetFlakes() = (110 + 300 * intensity).toInt().coerceIn(0, MAX_FLAKES)

    override fun onResize() {
        unit = width / 1080f
        clouds.resize(width, height)
        buildHills()
        applyHillColors()
        flakes.clear()
        fill(randomY = true)
    }

    override fun onIntensityChanged() {
        fill(randomY = true)
        clouds.setIntensity(intensity)
    }

    override fun onTimeOfDayChanged() = applyHillColors()

    override fun setEnvironment(windKmh: Float, cloudCover: Int, timeOfDay: TimeOfDay) {
        super.setEnvironment(windKmh, cloudCover, timeOfDay)
        clouds.setEnvironment(windKmh, cloudCover, timeOfDay)
    }

    override fun setAtmosphere(atmosphere: com.skymood.domain.Atmosphere) {
        super.setAtmosphere(atmosphere)
        clouds.setAtmosphere(atmosphere)
    }

    override fun setParallax(offset: Float) {
        super.setParallax(offset)
        clouds.setParallax(offset)
    }

    private fun buildHills() {
        val w = width.toFloat()
        val h = height.toFloat()
        fun hill(path: Path, base: Float, amp: Float, phase: Float) {
            path.reset()
            path.moveTo(-w * 0.1f, h)
            val steps = 6
            for (i in 0..steps) {
                val x = -w * 0.1f + i * (w * 1.2f / steps)
                val y = base - amp * sin(i * 1.3f + phase)
                if (i == 0) path.lineTo(x, y) else {
                    val px = x - w * 1.2f / steps / 2f
                    path.quadTo(px, y - amp * 0.6f, x, y)
                }
            }
            path.lineTo(w * 1.1f, h)
            path.close()
        }
        hill(farHill, h * 0.9f, h * 0.018f, 0.5f)
        hill(nearHill, h * 0.95f, h * 0.014f, 2.1f)
    }

    private fun applyHillColors() {
        if (height <= 1) return
        val night = timeOfDay == TimeOfDay.NIGHT
        val farTop = if (night) 0xFF4A5468.toInt() else 0xFFC9D3E0.toInt()
        val nearTop = if (night) 0xFF5C6780.toInt() else 0xFFE9EEF5.toInt()
        val bottom = if (night) 0xFF2A3040.toInt() else 0xFFB7C3D3.toInt()
        farHillPaint.shader = LinearGradient(0f, height * 0.87f, 0f, height.toFloat(), farTop, bottom, Shader.TileMode.CLAMP)
        nearHillPaint.shader = LinearGradient(0f, height * 0.93f, 0f, height.toFloat(), nearTop, bottom, Shader.TileMode.CLAMP)
    }

    private fun fill(randomY: Boolean) {
        while (flakes.activeCount < targetFlakes()) {
            val p = flakes.obtain() ?: break
            spawn(p, randomY)
        }
    }

    private fun spawn(p: Particle, randomY: Boolean) {
        p.layer = Random.nextInt(3) // 0 = near
        val depth = 1f - p.layer * 0.3f
        p.size = unit * depth * (3f + Random.nextFloat() * 5f)
        p.vy = height * depth * (0.05f + Random.nextFloat() * 0.06f)
        p.phase = Random.nextFloat() * 6.28f
        p.length = 0.6f + Random.nextFloat() * 1.2f // sway frequency
        p.alpha = depth * (0.65f + Random.nextFloat() * 0.35f)
        // Start upwind of the screen so flakes drifting sideways still cover it.
        p.x = Random.nextFloat() * width * 1.4f - (if (driftDir > 0f) width * 0.4f else 0f)
        p.y = if (randomY) Random.nextFloat() * height else -p.size * 3f
    }

    override fun update(dtMs: Long) {
        timeMs += dtMs
        clouds.update(dtMs)
        val dt = dtMs / 1000f
        val drift = tan(windAngle) * driftDir
        val target = targetFlakes()
        for (i in flakes.activeCount - 1 downTo 0) {
            val p = flakes[i]
            p.y += p.vy * dt
            p.x += p.vy * drift * dt
            val outSide = if (driftDir > 0f) p.x > width + 40f else p.x < -40f
            if (p.y > height + p.size * 3f || outSide) {
                if (flakes.activeCount > target) flakes.release(i) else spawn(p, randomY = false)
            }
        }
        if (flakes.activeCount < target) fill(randomY = false)
    }

    override fun drawBackground(canvas: Canvas) {
        sky.draw(canvas, width, height, SceneType.SNOW, timeOfDay)
        clouds.drawBackground(canvas)
    }

    override fun drawForeground(canvas: Canvas) {
        // The landscape layer shows the snowy ground, so no hills are drawn here.
        val shift = parallaxPx
        val t = timeMs / 1000f
        for (i in 0 until flakes.activeCount) {
            val p = flakes[i]
            val sway = sin(t * p.length + p.phase) * 18f * unit
            val x = p.x + sway + shift * (1f - p.layer * 0.3f)
            if (p.layer == 2) {
                // Distant flakes: plain small dots.
                corePaint.alpha = (255 * p.alpha).toInt()
                canvas.drawCircle(x, p.y, p.size * 0.6f, corePaint)
            } else {
                flakePaint.alpha = (255 * p.alpha).toInt()
                canvas.save()
                canvas.translate(x, p.y)
                canvas.scale(p.size * 1.6f, p.size * 1.6f)
                canvas.drawCircle(0f, 0f, 1f, flakePaint)
                canvas.restore()
            }
        }
    }

    private companion object {
        const val MAX_FLAKES = 500
    }
}
