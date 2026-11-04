package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.skymood.domain.SceneType
import com.skymood.wallpaper.particles.Particle
import com.skymood.wallpaper.particles.ParticlePool
import kotlin.math.tan
import kotlin.random.Random

/**
 * Rain: thin streaks in two depth layers, tilted by the wind, with small splashes at the bottom.
 * About 150 drops for light rain and 600 for heavy rain.
 *
 * @param drawSky false when another scene (the storm) draws its own background.
 */
class RainScene(
    private val skyType: SceneType = SceneType.RAIN,
    private val drawSky: Boolean = true,
) : BaseScene() {

    private val drops = ParticlePool(MAX_DROPS)
    private val splashes = ParticlePool(MAX_SPLASHES)

    // Pre-sized line buffers so all streaks of a layer go out in one drawLines call.
    private val nearLines = FloatArray(MAX_DROPS * 4)
    private val farLines = FloatArray(MAX_DROPS * 4)

    private val nearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 210, 225, 245); strokeCap = Paint.Cap.ROUND
    }
    private val farPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 190, 205, 230); strokeCap = Paint.Cap.ROUND
    }
    private val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 220, 230, 250) }
    private val hazePaint = Paint().apply { color = Color.argb(40, 20, 30, 45) }

    private var tilt = 0f

    fun targetDrops(): Int = (150 + 450 * intensity).toInt().coerceIn(0, MAX_DROPS)

    override fun onResize() {
        val density = width / 1080f
        nearPaint.strokeWidth = 2.2f * density.coerceAtLeast(0.6f)
        farPaint.strokeWidth = 1.3f * density.coerceAtLeast(0.6f)
        drops.clear()
        splashes.clear()
        fill()
    }

    override fun onIntensityChanged() = fill()

    private fun fill() {
        while (drops.activeCount < targetDrops()) {
            val p = drops.obtain() ?: break
            spawnDrop(p, randomY = true)
        }
    }

    private fun spawnDrop(p: Particle, randomY: Boolean) {
        p.layer = if (Random.nextFloat() < 0.4f) 1 else 0 // 1 = far
        val depth = if (p.layer == 1) 0.6f else 1f
        val speed = height * (1.3f + Random.nextFloat() * 0.9f) * depth
        p.vy = speed
        p.vx = speed * tilt
        p.length = height * (0.025f + Random.nextFloat() * 0.035f) * depth
        // Start further left when the wind blows right, so the screen stays covered.
        p.x = Random.nextFloat() * (width + height * tilt) - height * tilt
        p.y = if (randomY) Random.nextFloat() * height else -p.length - Random.nextFloat() * height * 0.2f
        p.maxLife = height * (0.88f + Random.nextFloat() * 0.12f) // ground line
    }

    private fun spawnSplash(x: Float, y: Float) {
        val count = 1 + Random.nextInt(3)
        for (n in 0 until count) {
            val s = splashes.obtain() ?: return
            s.x = x
            s.y = y
            s.vx = (Random.nextFloat() - 0.5f) * height * 0.12f
            s.vy = -height * (0.05f + Random.nextFloat() * 0.08f)
            s.size = 1.2f + Random.nextFloat() * 1.8f
            s.maxLife = 260f + Random.nextFloat() * 160f
            s.life = s.maxLife
        }
    }

    override fun update(dtMs: Long) {
        tilt = tan(windAngle)
        val dt = dtMs / 1000f
        val target = targetDrops()
        for (i in drops.activeCount - 1 downTo 0) {
            val p = drops[i]
            p.vx = p.vy * tilt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.y >= p.maxLife) {
                if (p.layer == 0 && Random.nextFloat() < 0.35f) spawnSplash(p.x, p.maxLife)
                if (drops.activeCount > target) drops.release(i) else spawnDrop(p, randomY = false)
            }
        }
        if (drops.activeCount < target) fill()

        val gravity = height * 0.9f
        for (i in splashes.activeCount - 1 downTo 0) {
            val s = splashes[i]
            s.life -= dtMs
            if (s.life <= 0f) {
                splashes.release(i)
                continue
            }
            s.vy += gravity * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
        }
    }

    override fun draw(canvas: Canvas) {
        if (drawSky) {
            sky.draw(canvas, width, height, skyType, timeOfDay)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), hazePaint)
        }
        var near = 0
        var far = 0
        val shift = parallaxPx
        for (i in 0 until drops.activeCount) {
            val p = drops[i]
            val dx = p.length * tilt
            if (p.layer == 1) {
                farLines[far++] = p.x + shift * 0.5f; farLines[far++] = p.y
                farLines[far++] = p.x - dx + shift * 0.5f; farLines[far++] = p.y - p.length
            } else {
                nearLines[near++] = p.x + shift; nearLines[near++] = p.y
                nearLines[near++] = p.x - dx + shift; nearLines[near++] = p.y - p.length
            }
        }
        if (far > 0) canvas.drawLines(farLines, 0, far, farPaint)
        if (near > 0) canvas.drawLines(nearLines, 0, near, nearPaint)

        for (i in 0 until splashes.activeCount) {
            val s = splashes[i]
            splashPaint.alpha = (200 * (s.life / s.maxLife)).toInt().coerceIn(0, 255)
            canvas.drawCircle(s.x + shift, s.y, s.size, splashPaint)
        }
    }

    private companion object {
        const val MAX_DROPS = 900
        const val MAX_SPLASHES = 240
    }
}
