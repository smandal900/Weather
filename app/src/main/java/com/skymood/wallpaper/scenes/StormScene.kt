package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import kotlin.random.Random

/**
 * Thunderstorm: dark clouds and heavy rain, plus lightning every 4 to 12 seconds. Each strike
 * is a jagged bolt and a full-screen white flash that fades over about 200 ms.
 */
class StormScene : BaseScene() {

    private val clouds = CloudScene(CloudScene.Style.STORM, drawSky = false)
    private val rain = RainScene(drawSky = false)

    private val flashPaint = Paint().apply { color = Color.WHITE }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 235, 240, 255); strokeCap = Paint.Cap.ROUND
    }
    private val bolt = FloatArray(BOLT_SEGMENTS * 4)

    private var nextStrikeMs = randomInterval()
    private var flashMs = 0f
    private var secondFlash = false

    private fun randomInterval() = 4000f + Random.nextFloat() * 8000f

    override fun onResize() {
        clouds.resize(width, height)
        rain.resize(width, height)
        boltPaint.strokeWidth = width / 300f
    }

    override fun onIntensityChanged() {
        clouds.setIntensity(intensity)
        rain.setIntensity(intensity)
    }

    override fun setEnvironment(windKmh: Float, cloudCover: Int, timeOfDay: TimeOfDay) {
        super.setEnvironment(windKmh, cloudCover, timeOfDay)
        clouds.setEnvironment(windKmh, cloudCover, timeOfDay)
        rain.setEnvironment(windKmh, cloudCover, timeOfDay)
    }

    override fun setAtmosphere(atmosphere: com.skymood.domain.Atmosphere) {
        super.setAtmosphere(atmosphere)
        clouds.setAtmosphere(atmosphere)
        rain.setAtmosphere(atmosphere)
    }

    override fun setParallax(offset: Float) {
        super.setParallax(offset)
        clouds.setParallax(offset)
        rain.setParallax(offset)
    }

    /** Builds the bolt into the pre-allocated line buffer. */
    private fun buildBolt() {
        var x = width * (0.15f + Random.nextFloat() * 0.7f)
        var y = 0f
        val step = height * 0.7f / BOLT_SEGMENTS
        var i = 0
        for (s in 0 until BOLT_SEGMENTS) {
            val nx = x + (Random.nextFloat() - 0.5f) * width * 0.08f
            val ny = y + step * (0.7f + Random.nextFloat() * 0.6f)
            bolt[i++] = x; bolt[i++] = y; bolt[i++] = nx; bolt[i++] = ny
            x = nx; y = ny
        }
    }

    override fun update(dtMs: Long) {
        clouds.update(dtMs)
        rain.update(dtMs)
        if (flashMs > 0f) {
            flashMs -= dtMs
            if (flashMs <= 0f && secondFlash) {
                secondFlash = false
                flashMs = FLASH_MS * 0.7f
            }
        } else {
            nextStrikeMs -= dtMs
            if (nextStrikeMs <= 0f) {
                buildBolt()
                flashMs = FLASH_MS
                secondFlash = Random.nextFloat() < 0.4f
                nextStrikeMs = randomInterval()
            }
        }
    }

    override fun drawBackground(canvas: Canvas) {
        sky.draw(canvas, width, height, SceneType.STORM, timeOfDay)
        if (flashMs > 0f) {
            // The bolt strikes behind the landscape.
            boltPaint.alpha = (255 * (flashMs / FLASH_MS).coerceIn(0f, 1f)).toInt()
            canvas.drawLines(bolt, boltPaint)
        }
        clouds.drawBackground(canvas)
    }

    override fun drawForeground(canvas: Canvas) {
        rain.drawForeground(canvas)
        if (flashMs > 0f) {
            val f = (flashMs / FLASH_MS).coerceIn(0f, 1f)
            flashPaint.alpha = (200 * f * f).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
        }
    }

    private companion object {
        const val FLASH_MS = 200f
        const val BOLT_SEGMENTS = 10
    }
}
