package com.skymood.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.WallpaperColors
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.skymood.domain.SceneSelector
import com.skymood.domain.SceneType
import com.skymood.domain.Settings
import com.skymood.domain.WeatherState
import com.skymood.skyMood
import com.skymood.wallpaper.render.CanvasFrameRenderer
import com.skymood.wallpaper.render.FrameRenderer
import com.skymood.wallpaper.scenes.SkyGradient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

class SkyWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SkyEngine()

    /**
     * The live wallpaper engine. It observes the cached weather and settings, feeds them to a
     * [SceneDirector] and runs a frame loop on the main looper only while visible.
     *
     * Kotlin cannot subclass WallpaperService.Engine (a Java inner class) outside its service,
     * so the engine lives here and keeps its logic in [SceneDirector] and [FrameRenderer].
     */
    inner class SkyEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val director = SceneDirector()
        private val renderer: FrameRenderer = CanvasFrameRenderer(director)
        private val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        private var visible = false
        private var surfaceReady = false
        private var running = false
        private var lastFrameMs = 0L
        private var fpsCap = 30
        private var powerSave = false

        private var lastColorsType: SceneType? = null
        private var lastSpecColor = Color.BLACK

        private var weather: WeatherState? = null
        private var settings = Settings()

        private val frame = object : Runnable {
            override fun run() = drawFrame()
        }

        private val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                powerSave = powerManager.isPowerSaveMode
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            powerSave = powerManager.isPowerSaveMode
            ContextCompat.registerReceiver(
                this@SkyWallpaperService, powerReceiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )

            applySpec()
            val cache = skyMood.cache
            scope.launch {
                combine(cache.weather, cache.settings) { w, s -> w to s }.collect { (w, s) ->
                    weather = w
                    settings = s
                    applySpec()
                }
            }
        }

        private fun applySpec() {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val spec = SceneSelector.select(weather, settings, hour)
            lastSpecColor = SkyGradient.blend(
                SkyGradient.midColor(spec.type, spec.timeOfDay), Color.BLACK, 0.3f + spec.dim * 0.5f,
            )
            fpsCap = spec.fpsCap
            director.apply(spec)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && spec.type != lastColorsType) {
                lastColorsType = spec.type
                notifyColorsChanged()
            }
        }

        /**
         * Tells the launcher and system UI the wallpaper is dark, so they keep light text and
         * icons with shadows instead of switching to dark text over a bright sky.
         */
        @RequiresApi(Build.VERSION_CODES.O_MR1)
        override fun onComputeColors(): WallpaperColors =
            WallpaperColors(Color.valueOf(lastSpecColor), null, null)

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) applySpec() // picks up dawn/dusk changes since last shown
            updateLoop()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            director.resize(width, height)
            surfaceReady = true
            updateLoop()
            if (!running) renderer.render(holder) // keep a correct still frame after rotation
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            updateLoop()
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int,
        ) {
            director.setParallax(xOffset * 2f - 1f)
        }

        override fun onDestroy() {
            running = false
            handler.removeCallbacks(frame)
            scope.cancel()
            runCatching { unregisterReceiver(powerReceiver) }
            renderer.release()
            super.onDestroy()
        }

        private fun updateLoop() {
            val shouldRun = visible && surfaceReady
            if (shouldRun == running) return
            running = shouldRun
            handler.removeCallbacks(frame)
            if (running) {
                lastFrameMs = SystemClock.uptimeMillis()
                handler.post(frame)
            }
        }

        private fun frameIntervalMs(): Long {
            val fps = if (powerSave) minOf(fpsCap, 15) else fpsCap
            return 1000L / fps
        }

        private fun drawFrame() {
            if (!running) return
            val start = SystemClock.uptimeMillis()
            val dt = (start - lastFrameMs).coerceIn(0L, 100L)
            lastFrameMs = start
            director.update(dt)
            renderer.render(surfaceHolder)
            if (running) {
                val spent = SystemClock.uptimeMillis() - start
                handler.postDelayed(frame, (frameIntervalMs() - spent).coerceAtLeast(1L))
            }
        }
    }
}
