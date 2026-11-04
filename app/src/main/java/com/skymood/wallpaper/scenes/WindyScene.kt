package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import com.skymood.wallpaper.particles.Particle
import com.skymood.wallpaper.particles.ParticlePool
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Windy: clouds racing across the sky, thin gusts of air streaking past and leaves tumbling
 * through the air in front of the landscape.
 */
class WindyScene : BaseScene() {

    private val clouds = CloudScene(CloudScene.Style.FAIR, drawSky = false, coverRange = 30..65)
    private val gusts = ParticlePool(MAX_GUSTS)
    private val leaves = ParticlePool(MAX_LEAVES)
    private val gustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.WHITE
    }
    private val leafPaints = arrayOf(
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9822E.toInt() },
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8FA33A.toInt() },
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB4502A.toInt() },
    )
    private val leaf = Path()
    private val gustPath = Path()
    private var unit = 1f
    private var timeMs = 0f

    init {
        // A unit leaf: pointed oval about 2 x 1 units.
        leaf.moveTo(-1f, 0f)
        leaf.quadTo(0f, -0.7f, 1f, 0f)
        leaf.quadTo(0f, 0.7f, -1f, 0f)
        leaf.close()
    }

    override fun onResize() {
        unit = width / 1080f
        gustPaint.strokeWidth = 2.2f * unit.coerceAtLeast(0.6f)
        clouds.resize(width, height)
        gusts.clear()
        leaves.clear()
        repeat(targetGusts()) { gusts.obtain()?.let { spawnGust(it, randomX = true) } }
        repeat(targetLeaves()) { leaves.obtain()?.let { spawnLeaf(it, randomX = true) } }
    }

    override fun onIntensityChanged() = clouds.setIntensity(intensity)

    override fun setEnvironment(windKmh: Float, cloudCover: Int, timeOfDay: TimeOfDay) {
        super.setEnvironment(windKmh, cloudCover, timeOfDay)
        clouds.setEnvironment(windKmh, cloudCover, timeOfDay)
        gustPaint.color = if (timeOfDay == TimeOfDay.NIGHT) 0xFF9AA6C0.toInt() else Color.WHITE
    }

    override fun setParallax(offset: Float) {
        super.setParallax(offset)
        clouds.setParallax(offset)
    }

    private fun targetGusts() = (5 + 7 * intensity).toInt().coerceAtMost(MAX_GUSTS)
    private fun targetLeaves() = (10 + 22 * intensity).toInt().coerceAtMost(MAX_LEAVES)
    private fun speed() = (windKmh.coerceAtLeast(35f) / 50f) * width * 0.9f

    private fun spawnGust(p: Particle, randomX: Boolean) {
        p.length = width * (0.25f + Random.nextFloat() * 0.35f)
        p.x = if (randomX) Random.nextFloat() * width else -p.length - Random.nextFloat() * width * 0.5f
        p.y = height * (0.12f + Random.nextFloat() * 0.75f)
        p.vx = 1.2f + Random.nextFloat() * 0.8f
        p.phase = Random.nextFloat() * 6.28f
        p.size = height * (0.004f + Random.nextFloat() * 0.01f) // wave amplitude
        p.alpha = 0.18f + Random.nextFloat() * 0.25f
    }

    private fun spawnLeaf(p: Particle, randomX: Boolean) {
        p.x = if (randomX) Random.nextFloat() * width else -30f * unit - Random.nextFloat() * width * 0.3f
        p.y = height * (0.3f + Random.nextFloat() * 0.65f)
        p.vx = 0.7f + Random.nextFloat() * 0.6f
        p.vy = (Random.nextFloat() - 0.4f) * height * 0.04f
        p.size = unit * (7f + Random.nextFloat() * 7f)
        p.phase = Random.nextFloat() * 360f // rotation
        p.length = (Random.nextFloat() - 0.5f) * 600f // spin, degrees per second
        p.variant = Random.nextInt(leafPaints.size)
    }

    override fun update(dtMs: Long) {
        timeMs += dtMs
        clouds.update(dtMs)
        val dt = dtMs / 1000f
        val v = speed()
        for (i in gusts.activeCount - 1 downTo 0) {
            val p = gusts[i]
            p.x += p.vx * v * dt
            if (p.x > width + 20f) {
                if (gusts.activeCount > targetGusts()) gusts.release(i) else spawnGust(p, randomX = false)
            }
        }
        while (gusts.activeCount < targetGusts()) gusts.obtain()?.let { spawnGust(it, false) } ?: break
        val t = timeMs / 1000f
        for (i in leaves.activeCount - 1 downTo 0) {
            val p = leaves[i]
            p.x += p.vx * v * 0.6f * dt
            p.y += (p.vy + sin(t * 2f + p.phase) * height * 0.03f) * dt
            p.phase += p.length * dt
            if (p.x > width + 40f || p.y > height + 40f || p.y < -40f) {
                if (leaves.activeCount > targetLeaves()) leaves.release(i) else spawnLeaf(p, randomX = false)
            }
        }
        while (leaves.activeCount < targetLeaves()) leaves.obtain()?.let { spawnLeaf(it, false) } ?: break
    }

    override fun drawBackground(canvas: Canvas) {
        sky.draw(canvas, width, height, SceneType.WINDY, timeOfDay)
        clouds.drawBackground(canvas)
    }

    override fun drawForeground(canvas: Canvas) {
        val t = timeMs / 1000f
        for (i in 0 until gusts.activeCount) {
            val p = gusts[i]
            // A gently waving streak; the path is rebuilt in place, so nothing is allocated.
            gustPath.rewind()
            val steps = 8
            for (s in 0..steps) {
                val f = s / steps.toFloat()
                val x = p.x + f * p.length
                val y = p.y + sin(f * 6f + t * 3f + p.phase) * p.size
                if (s == 0) gustPath.moveTo(x, y) else gustPath.lineTo(x, y)
            }
            gustPaint.alpha = (255 * p.alpha).toInt()
            canvas.drawPath(gustPath, gustPaint)
        }
        for (i in 0 until leaves.activeCount) {
            val p = leaves[i]
            canvas.save()
            canvas.translate(p.x, p.y)
            canvas.rotate(p.phase)
            // Tumbling: squash one axis as the leaf turns.
            canvas.scale(p.size, p.size * (0.35f + 0.65f * kotlin.math.abs(cos(p.phase * 0.0349f))))
            canvas.drawPath(leaf, leafPaints[p.variant])
            canvas.restore()
        }
    }

    private companion object {
        const val MAX_GUSTS = 16
        const val MAX_LEAVES = 40
    }
}
