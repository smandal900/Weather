package com.skymood.ui

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
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
    private var shown = false
    private var frameInterval = 33L

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
        shown = isVisible
        lastFrame = SystemClock.uptimeMillis()
        if (isVisible) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0L else (now - lastFrame).coerceIn(0L, 100L)
        lastFrame = now
        director.update(dt)
        director.draw(canvas)
        // Leave room for the wait until the next vsync so the preview gets close to the cap.
        if (shown) postInvalidateDelayed((frameInterval - 12L).coerceAtLeast(0L))
    }
}
