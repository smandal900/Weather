package com.skymood.ui

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.skymood.domain.SceneSpec
import com.skymood.wallpaper.SceneDirector

/** Live in-app preview of the wallpaper, using the same scenes as the engine. */
@Composable
fun PreviewScreen(spec: SceneSpec, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ScenePreviewView(it) },
        update = { it.setSpec(spec) },
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(20.dp)),
    )
}

/** A View that runs a [SceneDirector] while it is visible on screen. */
class ScenePreviewView(context: Context) : View(context) {

    private val director = SceneDirector()
    private var lastFrame = 0L
    private var vsyncMs = 0L
    private var shown = false
    private var frameInterval = 33L

    /**
     * Redraws are paced by the display's vsync, like the wallpaper itself: a frame is only requested
     * on the vsync that best matches the frame-rate cap, so the gaps between frames are always a
     * whole number of refreshes and steady motion stays even instead of juddering.
     */
    private val vsync = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!shown) return
            vsyncMs = frameTimeNanos / 1_000_000L
            if (vsyncMs - lastFrame >= frameInterval - 4L) invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun setSpec(spec: SceneSpec) {
        frameInterval = 1000L / spec.fpsCap
        director.apply(spec)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        director.resize(w, h)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        Choreographer.getInstance().removeFrameCallback(vsync)
        shown = isVisible
        lastFrame = SystemClock.uptimeMillis()
        if (isVisible) {
            invalidate()
            Choreographer.getInstance().postFrameCallback(vsync)
        }
    }

    override fun onDetachedFromWindow() {
        shown = false
        Choreographer.getInstance().removeFrameCallback(vsync)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        // Time from the vsync that triggered this draw, so every step is a whole number of refreshes.
        val now = if (vsyncMs > lastFrame) vsyncMs else SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0L else (now - lastFrame).coerceIn(0L, 100L)
        lastFrame = now
        director.update(dt)
        director.draw(canvas)
    }
}
