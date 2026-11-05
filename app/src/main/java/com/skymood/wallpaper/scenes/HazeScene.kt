package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import com.skymood.wallpaper.particles.ParticlePool
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Haze: a dusty, yellow-brown sky with a dim sun (or a faint moon at night), a warm veil that
 * thickens towards the horizon in front of the landscape, and slowly drifting dust.
 */
class HazeScene : BaseScene() {

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val veilPaint = Paint()
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dust = ParticlePool(MAX_DUST)
    private var radius = 1f
    private var timeMs = 0f

    override fun onResize() {
        radius = min(width, height) * 0.07f
        rebuild()
        dust.clear()
        while (true) {
            val p = dust.obtain() ?: break
            p.x = Random.nextFloat() * width
            p.y = Random.nextFloat() * height
            p.vx = 4f + Random.nextFloat() * 10f
            p.vy = (Random.nextFloat() - 0.5f) * 4f
            p.size = (1f + Random.nextFloat() * 2.5f) * (width / 1080f).coerceAtLeast(0.5f)
            p.phase = Random.nextFloat() * 6.28f
            p.alpha = 0.15f + Random.nextFloat() * 0.3f
        }
    }

    override fun onTimeOfDayChanged() = rebuild()

    private fun rebuild() {
        if (height <= 1) return
        val night = timeOfDay == TimeOfDay.NIGHT
        discPaint.color = if (night) 0xFFC9BFA8.toInt() else 0xFFF2C27E.toInt()
        val glow = if (night) 0x33C9BFA8 else 0x66F0B060
        glowPaint.shader = RadialGradient(
            0f, 0f, radius * 5f, intArrayOf(glow, Color.TRANSPARENT), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        val veil = if (night) 0x6A3A3428 else 0xA0D2B47F.toInt()
        val veilTop = if (night) 0x103A3428 else 0x20D2B47F
        veilPaint.shader = LinearGradient(
            0f, height * 0.35f, 0f, height.toFloat(), veilTop, veil, Shader.TileMode.CLAMP,
        )
        dustPaint.color = if (night) 0xFF9C9180.toInt() else 0xFFF3DDB0.toInt()
    }

    override fun update(dtMs: Long) {
        timeMs += dtMs
        val dt = dtMs / 1000f
        val wind = 1f + windKmh / 25f
        for (i in 0 until dust.activeCount) {
            val p = dust[i]
            p.x += p.vx * wind * dt * driftDir
            p.y += p.vy * dt
            if (p.x > width + 5f) p.x = -5f else if (p.x < -5f) p.x = width + 5f
            if (p.y < -5f) p.y = height + 5f else if (p.y > height + 5f) p.y = -5f
        }
    }

    override fun drawBackground(canvas: Canvas) {
        sky.draw(canvas, width, height, SceneType.HAZE, timeOfDay)
        val night0 = timeOfDay == TimeOfDay.NIGHT && skyState.moonVisible
        val cx = width * (if (night0) skyState.moonX else skyState.sunX) + parallaxPx * 0.3f
        val cy = height * (if (night0) skyState.moonY else skyState.sunY)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawCircle(0f, 0f, radius * 5f, glowPaint)
        canvas.restore()
        // A flat, washed-out disc: the haze hides the sun's halo and rays.
        discPaint.alpha = if (timeOfDay == TimeOfDay.NIGHT) 150 else 200
        canvas.drawCircle(cx, cy, radius, discPaint)
    }

    override fun drawForeground(canvas: Canvas) {
        // The less clear the air is (reported visibility and humidity), the thicker the veil.
        veilPaint.alpha = (255 * (1.1f - atmo.clarity)).toInt().coerceIn(120, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), veilPaint)
        val t = timeMs / 1000f
        for (i in 0 until dust.activeCount) {
            val p = dust[i]
            dustPaint.alpha = (255 * p.alpha * (0.6f + 0.4f * sin(t + p.phase))).toInt()
            canvas.drawCircle(p.x, p.y, p.size, dustPaint)
        }
    }

    private companion object {
        const val MAX_DUST = 70
    }
}
