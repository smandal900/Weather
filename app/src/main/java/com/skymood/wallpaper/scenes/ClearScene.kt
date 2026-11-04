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
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Clear daytime sky: a sun with a soft halo and slowly turning rays, a gentle lens flare,
 * and motes of light drifting upwards.
 */
class ClearScene : BaseScene() {

    private val sun = SunGlow()
    private val motePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val flarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val motes = ParticlePool(MAX_MOTES)

    private var sunRadius = 0f
    private var timeMs = 0f

    /** Clear scene is a daytime scene; if forced at night it shows the day palette. */
    private val dayTime: TimeOfDay get() = if (timeOfDay == TimeOfDay.NIGHT) TimeOfDay.DAY else timeOfDay

    override fun onResize() {
        sunRadius = min(width, height) * 0.085f
        rebuildShaders()
        motes.clear()
        fillMotes()
    }

    override fun onTimeOfDayChanged() = rebuildShaders()

    override fun onIntensityChanged() = fillMotes()

    private fun targetMotes() = (25 + 40 * intensity).toInt().coerceAtMost(MAX_MOTES)

    private fun fillMotes() {
        while (motes.activeCount < targetMotes()) {
            val p = motes.obtain() ?: break
            spawnMote(p, randomY = true)
        }
    }

    private fun spawnMote(p: Particle, randomY: Boolean) {
        p.x = Random.nextFloat() * width
        p.y = if (randomY) Random.nextFloat() * height else height + 10f
        p.vx = (Random.nextFloat() - 0.5f) * 12f
        p.vy = -(6f + Random.nextFloat() * 18f)
        p.size = (1.5f + Random.nextFloat() * 3.5f) * (width / 1080f).coerceAtLeast(0.5f)
        p.phase = Random.nextFloat() * 6.28f
        p.alpha = 0.15f + Random.nextFloat() * 0.35f
    }

    private fun sunX() = when (dayTime) {
        TimeOfDay.DAWN -> width * 0.25f
        TimeOfDay.DUSK -> width * 0.75f
        else -> width * 0.3f
    }

    private fun sunY() = when (dayTime) {
        TimeOfDay.DAWN, TimeOfDay.DUSK -> height * 0.42f
        else -> height * 0.34f
    }

    private fun rebuildShaders() {
        val warm = dayTime != TimeOfDay.DAY
        sun.setup(sunRadius, warm)
        val flare = if (warm) 0x55FFB27A else 0x44FFFFFF
        flarePaint.shader = RadialGradient(
            0f, 0f, 1f, intArrayOf(flare, flare and 0x00FFFFFF or 0x22000000, Color.TRANSPARENT),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
    }

    override fun update(dtMs: Long) {
        timeMs += dtMs
        val dt = dtMs / 1000f
        val target = targetMotes()
        for (i in motes.activeCount - 1 downTo 0) {
            val p = motes[i]
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.y < -10f || p.x < -10f || p.x > width + 10f) {
                if (motes.activeCount > target) motes.release(i) else spawnMote(p, randomY = false)
            }
        }
        if (motes.activeCount < target) fillMotes()
    }

    override fun drawBackground(canvas: Canvas) {
        sky.draw(canvas, width, height, SceneType.CLEAR, dayTime)

        val cx = sunX() + parallaxPx
        val cy = sunY()
        sun.draw(canvas, cx, cy, timeMs, 1f, showCore = true)

        // Lens flare: soft discs on the line from the sun through the screen centre.
        val dx = width * 0.5f - cx
        val dy = height * 0.5f - cy
        flare(canvas, cx + dx * 0.55f, cy + dy * 0.55f, sunRadius * 0.55f)
        flare(canvas, cx + dx * 0.9f, cy + dy * 0.9f, sunRadius * 0.3f)
        flare(canvas, cx + dx * 1.35f, cy + dy * 1.35f, sunRadius * 0.9f)
    }

    override fun drawForeground(canvas: Canvas) {
        for (i in 0 until motes.activeCount) {
            val p = motes[i]
            val twinkle = 0.6f + 0.4f * sin(timeMs / 600f + p.phase)
            motePaint.alpha = (255 * p.alpha * twinkle).toInt()
            canvas.drawCircle(p.x + parallaxPx * 0.5f, p.y, p.size, motePaint)
        }
    }

    private fun flare(canvas: Canvas, x: Float, y: Float, r: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(r, r)
        canvas.drawCircle(0f, 0f, 1f, flarePaint)
        canvas.restore()
    }

    private companion object {
        const val MAX_MOTES = 90
    }
}
