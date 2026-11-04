package com.skymood.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.skymood.domain.SceneSpec
import com.skymood.domain.SceneType
import com.skymood.wallpaper.scenes.ClearScene
import com.skymood.wallpaper.scenes.CloudScene
import com.skymood.wallpaper.scenes.FogScene
import com.skymood.wallpaper.scenes.NightScene
import com.skymood.wallpaper.scenes.RainScene
import com.skymood.wallpaper.scenes.Scene
import com.skymood.wallpaper.scenes.SnowScene
import com.skymood.wallpaper.scenes.StormScene
import java.util.EnumMap
import kotlin.math.roundToInt

/**
 * Owns the scenes, picks the active one from a [SceneSpec] and draws the optional
 * temperature overlay. Shared by the wallpaper engine and the in-app preview.
 *
 * Scenes are created lazily when first needed (outside the draw loop) and kept for reuse.
 */
class SceneDirector(
    private val factory: (SceneType) -> Scene = ::createScene,
) {
    private val scenes = EnumMap<SceneType, Scene>(SceneType::class.java)
    private var current: Scene? = null
    /** The scene fading out, drawn on top of [current] with decreasing alpha. */
    private var previous: Scene? = null
    private var fadeMs = 0f
    var currentType: SceneType? = null
        private set
    private var spec: SceneSpec? = null

    private var width = 0
    private var height = 0
    private var parallax = 0f

    private val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
        setShadowLayer(6f, 0f, 2f, Color.argb(120, 0, 0, 0))
    }
    private var tempText: String? = null

    fun apply(spec: SceneSpec) {
        this.spec = spec
        val scene = sceneFor(spec.type)
        scene.setIntensity(spec.intensity)
        scene.setEnvironment(spec.windKmh, spec.cloudCover, spec.timeOfDay)
        if (spec.type != currentType) {
            // Cross-fade from what is on screen now. The very first scene appears without a fade.
            previous = current
            fadeMs = if (previous != null) FADE_MS else 0f
            current = scene
            currentType = spec.type
        }
        tempText = if (spec.showTemperature && spec.temperatureC != null) {
            "${spec.temperatureC.roundToInt()}°"
        } else null
    }

    private fun sceneFor(type: SceneType): Scene = scenes.getOrPut(type) {
        factory(type).also {
            if (width > 0) it.resize(width, height)
            it.setParallax(parallax)
        }
    }

    fun resize(width: Int, height: Int) {
        this.width = width
        this.height = height
        tempPaint.textSize = minOf(width, height) * 0.06f
        for (scene in scenes.values) scene.resize(width, height)
    }

    fun setParallax(offset: Float) {
        parallax = offset
        for (scene in scenes.values) scene.setParallax(offset)
    }

    fun update(dtMs: Long) {
        current?.update(dtMs)
        if (previous != null) {
            previous?.update(dtMs)
            fadeMs -= dtMs
            if (fadeMs <= 0f) previous = null
        }
    }

    fun draw(canvas: Canvas) {
        val scene = current
        if (scene == null) {
            canvas.drawColor(Color.BLACK)
            return
        }
        scene.draw(canvas)
        previous?.let { old ->
            val alpha = (255 * (fadeMs / FADE_MS)).toInt().coerceIn(0, 255)
            val save = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), alpha)
            old.draw(canvas)
            canvas.restoreToCount(save)
        }
        tempText?.let { canvas.drawText(it, width * 0.94f, height * 0.11f, tempPaint) }
    }

    companion object {
        const val FADE_MS = 2000f

        fun createScene(type: SceneType): Scene = when (type) {
            SceneType.CLEAR -> ClearScene()
            SceneType.NIGHT -> NightScene()
            SceneType.CLOUD -> CloudScene()
            SceneType.FOG -> FogScene()
            SceneType.RAIN -> RainScene()
            SceneType.SNOW -> SnowScene()
            SceneType.STORM -> StormScene()
        }
    }
}
