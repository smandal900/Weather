package com.skymood.wallpaper.landscape

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import com.skymood.domain.LandscapeType
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import com.skymood.wallpaper.scenes.SkyGradient
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Colours for the landscape under the current time of day and weather. Every landscape colour
 * is a daylight "base" colour passed through [shade] (or [ground] for surfaces that get snow).
 * Recomputed only when the time of day or weather changes.
 */
class Lighting {
    var timeOfDay = TimeOfDay.DAY
        private set
    var weather = SceneType.CLEAR
        private set
    private var tint = Color.WHITE
    private var tintAmount = 0f
    private var desaturate = 0f
    private var brightness = 1f

    var snow = false
        private set
    /** Village windows glow in the evening, at night and in dark storms. */
    var windowsLit = false
        private set
    var skyBottom = Color.WHITE
        private set
    /** Sun or moon reflection on water, or 0 when the sky is too cloudy for one. */
    var reflection = 0
        private set
    var reflectionX = 0.3f
        private set

    fun setSun(x: Float) {
        reflectionX = x
    }

    fun set(timeOfDay: TimeOfDay, weather: SceneType) {
        this.timeOfDay = timeOfDay
        this.weather = weather
        when (timeOfDay) {
            TimeOfDay.DAWN -> { tint = 0xFFFF9A7A.toInt(); tintAmount = 0.22f; brightness = 0.86f }
            TimeOfDay.DAY -> { tint = Color.WHITE; tintAmount = 0f; brightness = 1f }
            TimeOfDay.DUSK -> { tint = 0xFFFF7040.toInt(); tintAmount = 0.28f; brightness = 0.72f }
            TimeOfDay.NIGHT -> { tint = 0xFF1B2850.toInt(); tintAmount = 0.55f; brightness = 0.34f }
        }
        desaturate = 0f
        snow = false
        when (weather) {
            SceneType.PARTLY_CLOUDY -> brightness *= 0.96f
            SceneType.OVERCAST, SceneType.DRIZZLE -> { desaturate = 0.22f; brightness *= 0.82f }
            SceneType.HEAVY_RAIN -> { desaturate = 0.3f; brightness *= 0.7f }
            SceneType.STORM -> { desaturate = 0.35f; brightness *= 0.58f }
            SceneType.FOG -> { desaturate = 0.3f; brightness *= 0.9f }
            SceneType.SNOW -> {
                snow = true; desaturate = 0.15f
                // Snow reflects moonlight, so a snowy night is brighter than a clear one.
                brightness = if (timeOfDay == TimeOfDay.NIGHT) 0.5f else brightness * 0.95f
            }
            SceneType.HAZE -> {
                tint = SkyGradient.blend(tint, 0xFFC9A673.toInt(), 0.6f)
                tintAmount = max(tintAmount, 0.25f); brightness *= 0.9f
            }
            else -> Unit
        }
        windowsLit = timeOfDay == TimeOfDay.NIGHT || timeOfDay == TimeOfDay.DUSK ||
            weather == SceneType.STORM || weather == SceneType.HEAVY_RAIN
        skyBottom = SkyGradient.bottomColor(weather, timeOfDay)
        val clearish = weather == SceneType.CLEAR || weather == SceneType.PARTLY_CLOUDY ||
            weather == SceneType.WINDY || weather == SceneType.HAZE
        reflection = if (!clearish) 0 else when (timeOfDay) {
            TimeOfDay.DAWN, TimeOfDay.DUSK -> 0xFFFFC08A.toInt()
            TimeOfDay.DAY -> 0xFFFFF6DC.toInt()
            TimeOfDay.NIGHT -> 0xFFE6E8F0.toInt()
        }
        reflectionX = when (timeOfDay) {
            TimeOfDay.DAWN -> 0.25f
            TimeOfDay.DUSK -> 0.75f
            else -> 0.3f
        }
    }

    fun shade(base: Int): Int {
        var c = base
        if (desaturate > 0f) {
            val g = (Color.red(c) * 0.3f + Color.green(c) * 0.59f + Color.blue(c) * 0.11f).toInt()
            c = SkyGradient.blend(c, Color.rgb(g, g, g), desaturate)
        }
        if (tintAmount > 0f) c = SkyGradient.blend(c, tint, tintAmount)
        return Color.argb(
            Color.alpha(c),
            (Color.red(c) * brightness).toInt().coerceIn(0, 255),
            (Color.green(c) * brightness).toInt().coerceIn(0, 255),
            (Color.blue(c) * brightness).toInt().coerceIn(0, 255),
        )
    }

    /** Open ground, rooftops and peaks: covered white when it snows. */
    fun ground(base: Int): Int = shade(if (snow) SkyGradient.blend(base, SNOW, 0.82f) else base)

    /** Trees and bushes: only dusted with snow. */
    fun foliage(base: Int): Int = shade(if (snow) SkyGradient.blend(base, SNOW, 0.3f) else base)

    companion object {
        const val SNOW = 0xFFF2F6FA.toInt()
    }
}

/**
 * Scenery drawn between the sky and the weather. Shapes are built as paths in [build] (on
 * resize) and coloured in [applyLighting] (when time or weather changes). [draw] must not
 * allocate; animated parts reuse pre-allocated arrays.
 */
abstract class Landscape {
    protected var width = 1
    protected var height = 1
    protected val lighting = Lighting()
    protected var windKmh = 0f
    protected var timeMs = 0f
    @JvmField protected var parallax = 0f
    private var built = false

    /** Pixel shift for a layer; depth 0 = horizon (barely moves), 1 = nearest. */
    protected fun shift(depth: Float): Float = -parallax * width * 0.04f * (0.25f + 0.75f * depth)

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (built && width == this.width && height == this.height) return
        this.width = width
        this.height = height
        build()
        built = true
        applyLighting()
    }

    /** [sunX] is where the sun (or the moon at night) is across the screen, 0..1; it sets the light's side. */
    fun setConditions(timeOfDay: TimeOfDay, weather: SceneType, windKmh: Float, sunX: Float = 0.3f) {
        this.windKmh = windKmh
        val sideFlipped = (sunX < 0.5f) != (lighting.reflectionX < 0.5f)
        if (timeOfDay != lighting.timeOfDay || weather != lighting.weather || !lightingSet || sideFlipped) {
            lightingSet = true
            lighting.set(timeOfDay, weather)
            lighting.setSun(sunX)
            if (built) applyLighting()
        } else if (kotlin.math.abs(sunX - lighting.reflectionX) > 0.02f) {
            lighting.setSun(sunX)
            onSunMoved()
        }
    }

    /** Called when the sun moved without changing the light's side, so no repaint is needed. */
    protected open fun onSunMoved() {}

    /** Where animals can walk or swim in this landscape. */
    open fun groundSlots(): List<com.skymood.wallpaper.fauna.GroundSlot> = emptyList()

    /** Colour that dims animals to the scene's light. */
    fun wildlifeTint(): Int = lighting.shade(0xFFFFFFFF.toInt())
    private var lightingSet = false

    fun setParallax(offset: Float) {
        parallax = offset.coerceIn(-1f, 1f)
    }

    open fun update(dtMs: Long) {
        timeMs += dtMs
    }

    protected abstract fun build()
    protected abstract fun applyLighting()
    abstract fun draw(canvas: Canvas)

    protected fun drawLayer(canvas: Canvas, path: Path, paint: android.graphics.Paint, depth: Float) {
        canvas.save()
        canvas.translate(shift(depth), 0f)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    companion object {
        fun create(type: LandscapeType): Landscape = when (type) {
            LandscapeType.FOREST -> ForestLandscape()
            LandscapeType.MOUNTAIN -> MountainLandscape()
            LandscapeType.HILL -> HillLandscape()
            LandscapeType.SEA_BEACH -> BeachLandscape()
            LandscapeType.OPEN_SEA -> SeaLandscape(withBoat = false)
            LandscapeType.SEA_WITH_BOAT -> SeaLandscape(withBoat = true)
            LandscapeType.GRASS_FIELD -> GrassFieldLandscape()
            LandscapeType.LAKE -> LakeLandscape()
            LandscapeType.RIVER_VALLEY -> RiverValleyLandscape()
            LandscapeType.WATERFALL -> WaterfallLandscape()
            LandscapeType.SNOWY_PEAKS -> SnowyPeaksLandscape()
            LandscapeType.COUNTRYSIDE_VILLAGE -> VillageLandscape()
        }
    }
}

/**
 * A horizon line sampled every few pixels, either smooth (rolling hills) or jagged (mountains).
 * Spans a little beyond the screen on both sides so parallax never shows an edge.
 */
class Ridge(
    private val width: Int,
    private val baseY: Float,
    private val amplitude: Float,
    private val jagged: Boolean,
    seed: Int,
) {
    val left = -width * 0.1f
    private val span = width * 1.2f
    // 2^7 + 1 samples, so midpoint displacement fills every point.
    private val samples = 129
    private val ys = FloatArray(samples)

    init {
        val rnd = Random(seed)
        if (jagged) {
            // Midpoint displacement gives natural-looking peaks.
            val n = samples - 1
            ys[0] = rnd.nextFloat()
            ys[n] = rnd.nextFloat()
            var step = n
            var rough = 1f
            while (step > 1) {
                val half = step / 2
                var i = half
                while (i < n) {
                    ys[i] = (ys[i - half] + ys[i + half]) / 2f + (rnd.nextFloat() - 0.5f) * rough
                    i += step
                }
                step = half
                rough *= 0.5f
            }
            var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
            for (v in ys) { lo = minOf(lo, v); hi = maxOf(hi, v) }
            for (i in ys.indices) ys[i] = baseY - amplitude * ((ys[i] - lo) / (hi - lo + 1e-6f))
        } else {
            val p1 = rnd.nextFloat() * 6.28f
            val p2 = rnd.nextFloat() * 6.28f
            val f1 = 1.5f + rnd.nextFloat() * 1.5f
            val f2 = 4f + rnd.nextFloat() * 3f
            for (i in ys.indices) {
                val u = i / (samples - 1f)
                ys[i] = baseY - amplitude * (0.5f + 0.32f * sin(u * f1 * 3.14f + p1) + 0.18f * sin(u * f2 * 3.14f + p2))
            }
        }
    }

    fun yAt(x: Float): Float {
        val u = ((x - left) / span).coerceIn(0f, 1f) * (samples - 1)
        val i = u.toInt().coerceAtMost(samples - 2)
        val f = u - i
        return ys[i] * (1 - f) + ys[i + 1] * f
    }

    /** Adds the filled shape under the ridge, down to [bottom], to [path]. */
    fun addTo(path: Path, bottom: Float) {
        path.moveTo(left, bottom)
        for (i in 0 until samples) path.lineTo(left + span * i / (samples - 1f), ys[i])
        path.lineTo(left + span, bottom)
        path.close()
    }
}

/** Tree shapes added to shared paths so a whole row of trees is one draw call. */
object Trees {
    fun pine(path: Path, x: Float, baseY: Float, h: Float) {
        val w = h * 0.42f
        for (tier in 0 until 3) {
            val top = baseY - h + tier * h * 0.22f
            val bottom = baseY - h * 0.04f - (2 - tier) * h * 0.24f
            val half = w * (0.55f + tier * 0.22f)
            path.moveTo(x, top)
            path.lineTo(x + half, bottom)
            path.lineTo(x - half, bottom)
            path.close()
        }
        path.addRect(x - h * 0.035f, baseY - h * 0.2f, x + h * 0.035f, baseY, Path.Direction.CW)
    }

    fun round(path: Path, x: Float, baseY: Float, h: Float) {
        val r = h * 0.32f
        path.addRect(x - h * 0.04f, baseY - h * 0.3f, x + h * 0.04f, baseY, Path.Direction.CW)
        path.addCircle(x, baseY - h + r, r, Path.Direction.CW)
        path.addCircle(x - r * 0.62f, baseY - h + r * 1.9f, r * 0.8f, Path.Direction.CW)
        path.addCircle(x + r * 0.62f, baseY - h + r * 1.9f, r * 0.8f, Path.Direction.CW)
    }
}
