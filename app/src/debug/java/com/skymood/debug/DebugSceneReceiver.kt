package com.skymood.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.skymood.domain.LandscapeType
import com.skymood.domain.SceneType
import com.skymood.domain.TimeOfDay
import com.skymood.skyMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only: sets the preview overrides from adb, so any of the 480 scenes can be
 * shown without tapping through settings. "auto" clears an override.
 *
 *   adb shell am broadcast -n com.skymood/.debug.DebugSceneReceiver \
 *     --es weather STORM --es time DUSK --es landscape LAKE
 */
class DebugSceneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val weather = intent.getStringExtra("weather")
        val time = intent.getStringExtra("time")
        val landscape = intent.getStringExtra("landscape")
        val phase = intent.getStringExtra("phase")
        val wildlife = intent.getStringExtra("wildlife")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.skyMood.repository.updateSettings { s ->
                    var out = s
                    if (weather != null) out = out.copy(previewOverride = parse<SceneType>(weather))
                    if (time != null) out = out.copy(timeOverride = parse<TimeOfDay>(time))
                    if (landscape != null) parse<LandscapeType>(landscape)?.let { out = out.copy(landscape = it) }
                    if (phase != null) out = out.copy(moonPhaseOverride = phase.toFloatOrNull())
                    if (wildlife != null) out = out.copy(wildlife = wildlife != "off")
                    out
                }
            } finally {
                pending.finish()
            }
        }
    }

    private inline fun <reified T : Enum<T>> parse(value: String): T? =
        if (value.equals("auto", ignoreCase = true)) null
        else enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
}
