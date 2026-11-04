package com.skymood.wallpaper.render

import android.graphics.Canvas
import android.view.SurfaceHolder
import com.skymood.wallpaper.SceneDirector

/**
 * Puts one frame on the wallpaper surface. The engine only talks to this interface, so an
 * OpenGL ES renderer (EGL surface + GL scenes) can replace [CanvasFrameRenderer] later.
 */
interface FrameRenderer {
    fun render(holder: SurfaceHolder)
    fun release() {}
}

class CanvasFrameRenderer(private val director: SceneDirector) : FrameRenderer {

    private var hardwareFailed = false

    override fun render(holder: SurfaceHolder) {
        val canvas = lock(holder) ?: return
        try {
            director.draw(canvas)
        } finally {
            runCatching { holder.unlockCanvasAndPost(canvas) }
        }
    }

    private fun lock(holder: SurfaceHolder): Canvas? {
        if (!hardwareFailed) {
            try {
                return holder.lockHardwareCanvas()
            } catch (e: Exception) {
                hardwareFailed = true
            }
        }
        return try {
            holder.lockCanvas()
        } catch (e: Exception) {
            null
        }
    }
}
