package com.skymood.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import com.skymood.domain.LandscapeType
import com.skymood.domain.SceneSpec
import com.skymood.domain.SceneType
import com.skymood.wallpaper.landscape.Landscape
import com.skymood.wallpaper.scenes.ClearSkyScene
import com.skymood.wallpaper.scenes.CloudScene
import com.skymood.wallpaper.scenes.FogScene
import com.skymood.wallpaper.scenes.HazeScene
import com.skymood.wallpaper.scenes.RainScene
import com.skymood.wallpaper.scenes.Scene
import com.skymood.wallpaper.scenes.SnowScene
import com.skymood.wallpaper.scenes.StormScene
import com.skymood.wallpaper.scenes.WindyScene
import java.util.EnumMap
import kotlin.math.roundToInt

/**
 * Composes a frame from three layers: the weather scene's background (sky, sun or moon,
 * clouds), the landscape, then the weather scene's foreground (rain, snow, fog, haze, lightning).
 * Adds the readability scrim and the optional temperature overlay. Shared by the wallpaper
 * engine and the in-app preview.
 *
 * When the weather, time of day or landscape changes, the old frame is captured once into a
 * reusable bitmap and faded out over [FADE_MS]. Scenes and landscapes are created lazily
 * (outside the draw loop) and kept for reuse.
 */
class SceneDirector(
    private val factory: (SceneType) -> Scene = ::createScene,
) {
    private val scenes = EnumMap<SceneType, Scene>(SceneType::class.java)
    private val landscapes = EnumMap<LandscapeType, Landscape>(LandscapeType::class.java)
    private var current: Scene? = null
    private var landscape: Landscape? = null
    var currentType: SceneType? = null
        private set

    private var width = 0
    private var height = 0
    private var parallax = 0f

    private var snapshot: Bitmap? = null
    private var snapshotCanvas: Canvas? = null
    private val snapshotPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var fadeMs = 0f
    private var key = -1

    private val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.RIGHT
        setShadowLayer(6f, 0f, 2f, Color.argb(120, 0, 0, 0))
    }
    private var tempText: String? = null

    /**
     * Readability scrim: black, strongest at the top (clock, widgets) and bottom (icons, dock)
     * and lighter in the middle. Its overall strength is the user's dim setting.
     */
    private val scrimPaint = Paint()
    private var dim = 0f

    fun apply(spec: SceneSpec) {
        val newKey = (spec.type.ordinal * 8 + spec.timeOfDay.ordinal) * 16 + spec.landscape.ordinal
        if (key != -1 && newKey != key && current != null) captureForFade()
        key = newKey

        val scene = sceneFor(spec.type)
        scene.setIntensity(spec.intensity)
        scene.setEnvironment(spec.windKmh, spec.cloudCover, spec.timeOfDay)
        current = scene
        currentType = spec.type

        val land = landscapeFor(spec.landscape)
        land.setConditions(spec.timeOfDay, spec.type, spec.windKmh)
        landscape = land

        dim = spec.dim
        scrimPaint.alpha = (255 * dim).toInt()
        tempText = if (spec.showTemperature && spec.temperatureC != null) {
            "${spec.temperatureC.roundToInt()}°"
        } else null
    }

    private fun captureForFade() {
        if (width <= 0 || height <= 0) return
        val canvas = snapshotCanvas ?: run {
            val bmp = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
                ?: return
            snapshot = bmp
            Canvas(bmp).also { snapshotCanvas = it }
        }
        drawWorld(canvas)
        fadeMs = FADE_MS
    }

    private fun sceneFor(type: SceneType): Scene = scenes.getOrPut(type) {
        factory(type).also {
            if (width > 0) it.resize(width, height)
            it.setParallax(parallax)
        }
    }

    private fun landscapeFor(type: LandscapeType): Landscape = landscapes.getOrPut(type) {
        Landscape.create(type).also {
            if (width > 0) it.resize(width, height)
            it.setParallax(parallax)
        }
    }

    fun resize(width: Int, height: Int) {
        if (width != this.width || height != this.height) {
            snapshot?.recycle()
            snapshot = null
            snapshotCanvas = null
            fadeMs = 0f
        }
        this.width = width
        this.height = height
        tempPaint.textSize = minOf(width, height) * 0.06f
        if (height > 0) {
            scrimPaint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.BLACK, Color.argb(150, 0, 0, 0), Color.argb(150, 0, 0, 0), Color.BLACK),
                floatArrayOf(0f, 0.3f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            scrimPaint.alpha = (255 * dim).toInt()
        }
        for (scene in scenes.values) scene.resize(width, height)
        for (land in landscapes.values) land.resize(width, height)
    }

    fun setParallax(offset: Float) {
        parallax = offset
        for (scene in scenes.values) scene.setParallax(offset)
        for (land in landscapes.values) land.setParallax(offset)
    }

    fun update(dtMs: Long) {
        current?.update(dtMs)
        landscape?.update(dtMs)
        if (fadeMs > 0f) fadeMs -= dtMs
    }

    private fun drawWorld(canvas: Canvas) {
        val scene = current ?: return
        scene.drawBackground(canvas)
        landscape?.draw(canvas)
        scene.drawForeground(canvas)
    }

    fun draw(canvas: Canvas) {
        if (current == null) {
            canvas.drawColor(Color.BLACK)
            return
        }
        drawWorld(canvas)
        val snap = snapshot
        if (fadeMs > 0f && snap != null) {
            snapshotPaint.alpha = (255 * (fadeMs / FADE_MS)).toInt().coerceIn(0, 255)
            canvas.drawBitmap(snap, 0f, 0f, snapshotPaint)
        }
        if (dim > 0f) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        tempText?.let { canvas.drawText(it, width * 0.94f, height * 0.11f, tempPaint) }
    }

    companion object {
        const val FADE_MS = 2000f

        fun createScene(type: SceneType): Scene = when (type) {
            SceneType.CLEAR -> ClearSkyScene()
            SceneType.PARTLY_CLOUDY -> CloudScene(coverRange = 25..55)
            SceneType.OVERCAST -> CloudScene(CloudScene.Style.OVERCAST, coverRange = 88..100)
            SceneType.DRIZZLE -> RainScene(skyType = SceneType.DRIZZLE, drizzle = true)
            SceneType.HEAVY_RAIN -> RainScene(skyType = SceneType.HEAVY_RAIN)
            SceneType.STORM -> StormScene()
            SceneType.FOG -> FogScene()
            SceneType.SNOW -> SnowScene()
            SceneType.WINDY -> WindyScene()
            SceneType.HAZE -> HazeScene()
        }

    }
}
