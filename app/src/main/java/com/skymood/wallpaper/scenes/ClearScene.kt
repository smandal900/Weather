package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import com.skymood.wallpaper.particles.ParticlePool
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Clear daytime sky: a glowing sun and slowly drifting motes of light. */
class ClearScene : BaseScene() {

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val motePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val motes = ParticlePool(MAX_MOTES)

    private var sunRadius = 0f
    private var timeMs = 0f

    /** Clear scene is a daytime scene; if forced at night it shows the day palette. */
    private val dayTime: TimeOfDay get() = if (timeOfDay == TimeOfDay.NIGHT) TimeOfDay.DAY else timeOfDay

    override fun onResize() {
        sunRadius = min(width, height) * 0.09f
        rebuildShaders()
        motes.clear()
        fillMotes()
    }

    override fun onTimeOfDayChanged() = rebuildShaders()

    override fun onIntensityChanged() = fillMotes()

    private fun targetMotes() = (20 + 40 * intensity).toInt().coerceAtMost(MAX_MOTES)

    private fun fillMotes() {
        while (motes.activeCount < targetMotes()) {
            val p = motes.obtain() ?: break
            spawnMote(p, randomY = true)
        }
    }

    private fun spawnMote(p: com.skymood.wallpaper.particles.Particle, randomY: Boolean) {
        p.x = Random.nextFloat() * width
        p.y = if (randomY) Random.nextFloat() * height else height + 10f
        p.vx = (Random.nextFloat() - 0.5f) * 12f
        p.vy = -(6f + Random.nextFloat() * 18f)
        p.size = 1.5f + Random.nextFloat() * 3.5f
        p.phase = Random.nextFloat() * 6.28f
        p.alpha = 0.15f + Random.nextFloat() * 0.35f
    }

    private fun sunX() = when (dayTime) {
        TimeOfDay.DAWN -> width * 0.25f
        TimeOfDay.DUSK -> width * 0.78f
        else -> width * 0.7f
    }

    private fun sunY() = when (dayTime) {
        TimeOfDay.DAWN, TimeOfDay.DUSK -> height * 0.5f
        else -> height * 0.2f
    }

    private fun rebuildShaders() {
        val warm = dayTime != TimeOfDay.DAY
        val core = if (warm) 0xFFFFC27A.toInt() else 0xFFFFF4C8.toInt()
        val glow = if (warm) 0x88FF9A4A.toInt() else 0x66FFF1B0
        sunPaint.color = core
        glowPaint.shader = RadialGradient(
            0f, 0f, sunRadius * 4f,
            intArrayOf(glow, 0x22FFE6A0, Color.TRANSPARENT), floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
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
    }

    override fun draw(canvas: Canvas) {
        sky.draw(canvas, width, height, SceneType.CLEAR, dayTime)

        val cx = sunX() + parallaxPx
        val cy = sunY()
        val pulse = 1f + 0.04f * sin(timeMs / 1400f)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(pulse, pulse)
        canvas.drawCircle(0f, 0f, sunRadius * 4f, glowPaint)
        canvas.restore()
        canvas.drawCircle(cx, cy, sunRadius, sunPaint)

        for (i in 0 until motes.activeCount) {
            val p = motes[i]
            val twinkle = 0.6f + 0.4f * sin(timeMs / 600f + p.phase)
            motePaint.alpha = (255 * p.alpha * twinkle).toInt()
            canvas.drawCircle(p.x + parallaxPx * 0.5f, p.y, p.size, motePaint)
        }
    }

    private companion object {
        const val MAX_MOTES = 90
    }
}
