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

/** Clear night: twinkling stars, a glowing moon and the odd shooting star. */
class NightScene : BaseScene() {

    private val stars = ParticlePool(MAX_STARS)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F0E6.toInt() }
    private val craterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 }
    private val moonGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val meteorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeCap = Paint.Cap.ROUND
    }

    private val meteor = Particle()
    private var nextMeteorMs = 6000f
    private var moonRadius = 0f
    private var timeMs = 0f

    override fun onResize() {
        moonRadius = min(width, height) * 0.07f
        moonGlow.shader = RadialGradient(
            0f, 0f, moonRadius * 4f,
            intArrayOf(0x55DDE6FF, 0x11AAB8E0, Color.TRANSPARENT), floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
        meteorPaint.strokeWidth = width / 540f
        stars.clear()
        while (true) {
            val p = stars.obtain() ?: break
            p.x = Random.nextFloat() * width * 1.1f - width * 0.05f
            // Denser near the top of the sky.
            val r = Random.nextFloat()
            p.y = r * r * height * 0.85f
            p.size = 0.6f + Random.nextFloat() * Random.nextFloat() * 2.6f
            p.phase = Random.nextFloat() * 6.28f
            p.vx = 0.4f + Random.nextFloat() * 1.6f // twinkle speed
            p.alpha = 0.35f + Random.nextFloat() * 0.65f
            p.layer = if (Random.nextFloat() < 0.5f) 1 else 0
        }
    }

    override fun update(dtMs: Long) {
        timeMs += dtMs
        if (meteor.life > 0f) {
            meteor.life -= dtMs
            val dt = dtMs / 1000f
            meteor.x += meteor.vx * dt
            meteor.y += meteor.vy * dt
        } else {
            nextMeteorMs -= dtMs
            if (nextMeteorMs <= 0f) {
                meteor.x = Random.nextFloat() * width * 0.7f
                meteor.y = Random.nextFloat() * height * 0.3f
                meteor.vx = width * 0.9f
                meteor.vy = width * 0.35f
                meteor.maxLife = 700f
                meteor.life = meteor.maxLife
                nextMeteorMs = 8000f + Random.nextFloat() * 20000f / intensity.coerceAtLeast(0.3f)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        sky.draw(canvas, width, height, SceneType.NIGHT, TimeOfDay.NIGHT)
        val shift = parallaxPx
        for (i in 0 until stars.activeCount) {
            val p = stars[i]
            val tw = 0.55f + 0.45f * sin(timeMs / 1000f * p.vx + p.phase)
            starPaint.alpha = (255 * p.alpha * tw).toInt()
            val s = if (p.layer == 1) shift * 0.4f else shift * 0.7f
            canvas.drawCircle(p.x + s, p.y, p.size, starPaint)
        }

        if (meteor.life > 0f) {
            val f = meteor.life / meteor.maxLife
            meteorPaint.alpha = (220 * f).toInt()
            val tail = 0.12f
            canvas.drawLine(meteor.x, meteor.y, meteor.x - meteor.vx * tail, meteor.y - meteor.vy * tail, meteorPaint)
        }

        val mx = width * 0.72f + shift
        val my = height * 0.2f
        canvas.save()
        canvas.translate(mx, my)
        canvas.drawCircle(0f, 0f, moonRadius * 4f, moonGlow)
        canvas.drawCircle(0f, 0f, moonRadius, moonPaint)
        canvas.drawCircle(-moonRadius * 0.35f, -moonRadius * 0.2f, moonRadius * 0.22f, craterPaint)
        canvas.drawCircle(moonRadius * 0.3f, moonRadius * 0.35f, moonRadius * 0.15f, craterPaint)
        canvas.drawCircle(moonRadius * 0.25f, -moonRadius * 0.4f, moonRadius * 0.1f, craterPaint)
        canvas.restore()
    }

    private companion object {
        const val MAX_STARS = 220
    }
}
