package com.skymood.wallpaper.scenes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay

/**
 * Vertical sky background chosen by scene and time of day. The shader is rebuilt only when
 * the size, scene or time of day changes, never per frame.
 */
class SkyGradient {

    private val paint = Paint()
    private var key = -1
    private var keyWidth = 0
    private var keyHeight = 0

    fun draw(canvas: Canvas, width: Int, height: Int, scene: SceneType, timeOfDay: TimeOfDay) {
        val k = scene.ordinal * 8 + timeOfDay.ordinal
        if (k != key || width != keyWidth || height != keyHeight) {
            key = k; keyWidth = width; keyHeight = height
            val top = topColor(scene, timeOfDay)
            val mid = midColor(scene, timeOfDay)
            val bottom = bottomColor(scene, timeOfDay)
            paint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(top, mid, bottom), floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    companion object {
        private val clearTop = mapOf(
            TimeOfDay.DAWN to 0xFF3B4A7A.toInt(), TimeOfDay.DAY to 0xFF2F6FD0.toInt(),
            TimeOfDay.DUSK to 0xFF2C2F66.toInt(), TimeOfDay.NIGHT to 0xFF050A1C.toInt(),
        )
        private val clearMid = mapOf(
            TimeOfDay.DAWN to 0xFFC27BA0.toInt(), TimeOfDay.DAY to 0xFF5FA2EA.toInt(),
            TimeOfDay.DUSK to 0xFFB8577A.toInt(), TimeOfDay.NIGHT to 0xFF0E1838.toInt(),
        )
        private val clearBottom = mapOf(
            TimeOfDay.DAWN to 0xFFF6B98A.toInt(), TimeOfDay.DAY to 0xFFA9D3F5.toInt(),
            TimeOfDay.DUSK to 0xFFF09A5B.toInt(), TimeOfDay.NIGHT to 0xFF1C2A52.toInt(),
        )
        private val overcastTop = mapOf(
            TimeOfDay.DAWN to 0xFF4A4F5E.toInt(), TimeOfDay.DAY to 0xFF6E7A88.toInt(),
            TimeOfDay.DUSK to 0xFF3D3E4E.toInt(), TimeOfDay.NIGHT to 0xFF101319.toInt(),
        )
        private val overcastBottom = mapOf(
            TimeOfDay.DAWN to 0xFF9C9097.toInt(), TimeOfDay.DAY to 0xFFB9C1C9.toInt(),
            TimeOfDay.DUSK to 0xFF7E6E73.toInt(), TimeOfDay.NIGHT to 0xFF262B35.toInt(),
        )

        /** How grey the sky is for each scene, 0 = clear blue, 1 = fully overcast. */
        private fun overcast(scene: SceneType): Float = when (scene) {
            SceneType.CLEAR -> 0f
            SceneType.PARTLY_CLOUDY -> 0.3f
            SceneType.WINDY -> 0.25f
            SceneType.HAZE -> 0.35f
            SceneType.OVERCAST -> 0.75f
            SceneType.SNOW -> 0.6f
            SceneType.FOG -> 0.75f
            SceneType.DRIZZLE -> 0.7f
            SceneType.HEAVY_RAIN -> 0.85f
            SceneType.STORM -> 0.95f
        }

        /** Storms darken the sky; haze gives it a dusty yellow-brown cast. */
        private fun storm(scene: SceneType, c: Int) = when (scene) {
            SceneType.STORM -> blend(c, Color.BLACK, 0.35f)
            SceneType.HAZE -> blend(c, 0xFFC9A673.toInt(), 0.4f)
            else -> c
        }

        fun topColor(scene: SceneType, t: TimeOfDay) =
            storm(scene, blend(clearTop.getValue(t), overcastTop.getValue(t), overcast(scene)))

        fun midColor(scene: SceneType, t: TimeOfDay) = storm(
            scene,
            blend(clearMid.getValue(t), blend(overcastTop.getValue(t), overcastBottom.getValue(t), 0.5f), overcast(scene)),
        )

        fun bottomColor(scene: SceneType, t: TimeOfDay) =
            storm(scene, blend(clearBottom.getValue(t), overcastBottom.getValue(t), overcast(scene)))

        fun blend(a: Int, b: Int, t: Float): Int {
            val u = 1f - t
            return Color.argb(
                (Color.alpha(a) * u + Color.alpha(b) * t).toInt(),
                (Color.red(a) * u + Color.red(b) * t).toInt(),
                (Color.green(a) * u + Color.green(b) * t).toInt(),
                (Color.blue(a) * u + Color.blue(b) * t).toInt(),
            )
        }
    }
}
