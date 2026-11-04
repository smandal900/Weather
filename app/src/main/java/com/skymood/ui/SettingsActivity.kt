package com.skymood.ui

import android.Manifest
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.skymood.domain.LandscapeType
import com.skymood.domain.SavedLocation
import com.skymood.domain.SceneSelector
import com.skymood.domain.SceneType
import com.skymood.domain.Settings
import com.skymood.domain.TimeOfDay
import com.skymood.domain.WeatherCondition
import com.skymood.domain.WeatherState
import com.skymood.skyMood
import com.skymood.wallpaper.SkyWallpaperService
import com.skymood.work.WeatherRefreshWorker
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { SettingsScreen() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Save a foreground location so background refreshes work without background permission.
        val app = skyMood
        if (app.locationProvider.hasCoarsePermission()) {
            lifecycleScope.launch {
                app.locationProvider.currentLocation()?.let { app.repository.rememberLocation(it) }
            }
        }
    }
}

private fun launchWallpaperPicker(context: Context) {
    val component = ComponentName(context, SkyWallpaperService::class.java)
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (e2: ActivityNotFoundException) {
            Toast.makeText(context, "No live wallpaper picker on this device", Toast.LENGTH_LONG).show()
        }
    }
}

private fun conditionLabel(c: WeatherCondition) = when (c) {
    WeatherCondition.CLEAR -> "Clear"
    WeatherCondition.CLOUDY -> "Cloudy"
    WeatherCondition.FOG -> "Fog"
    WeatherCondition.RAIN -> "Rain"
    WeatherCondition.SNOW -> "Snow"
    WeatherCondition.STORM -> "Thunderstorm"
}

private val fpsSteps = listOf(15, 30, 60)

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.skyMood
    val repo = app.repository
    val scope = rememberCoroutineScope()

    val weather by repo.weather.collectAsState(initial = null)
    val settings by repo.settings.collectAsState(initial = Settings())
    val refreshing by remember {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(WeatherRefreshWorker.ONE_TIME_NAME)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
    }.collectAsState(initial = false)

    var hasLocation by remember { mutableStateOf(app.locationProvider.hasCoarsePermission()) }
    var hasBackground by remember { mutableStateOf(app.locationProvider.hasBackgroundPermission()) }
    var showLocationRationale by remember { mutableStateOf(false) }
    var showBackgroundRationale by remember { mutableStateOf(false) }

    val coarseLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocation = granted
        if (granted) WeatherRefreshWorker.refreshNow(context)
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasBackground = it
    }

    // Refresh once on first open if nothing is cached yet.
    LaunchedEffect(Unit) {
        if (weather == null) WeatherRefreshWorker.refreshNow(context)
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val spec = SceneSelector.select(weather, settings, hour, System.currentTimeMillis())

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("SkyMood", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            PreviewScreen(spec)

            Button(onClick = { launchWallpaperPicker(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Set as wallpaper")
            }

            WeatherCard(weather, refreshing, onRefresh = { WeatherRefreshWorker.refreshNow(context) })

            LocationCard(
                settings = settings,
                hasLocation = hasLocation,
                hasBackground = hasBackground,
                onRequestLocation = { showLocationRationale = true },
                onRequestBackground = { showBackgroundRationale = true },
                onSearch = { repo.searchCity(it) },
                onPickCity = { city ->
                    scope.launch {
                        repo.setManualLocation(city)
                        WeatherRefreshWorker.refreshNow(context)
                    }
                },
            )

            AnimationCard(settings, onChange = { transform -> scope.launch { repo.updateSettings(transform) } })
        }
    }

    if (showLocationRationale) {
        AlertDialog(
            onDismissRequest = { showLocationRationale = false },
            title = { Text("Use your location?") },
            text = {
                Text(
                    "SkyMood uses your approximate (city-level) location to fetch the current weather " +
                        "from Open-Meteo. Your location is only sent to the weather service and is not stored anywhere else.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLocationRationale = false
                    coarseLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showLocationRationale = false }) { Text("Not now") } },
        )
    }

    if (showBackgroundRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        AlertDialog(
            onDismissRequest = { showBackgroundRationale = false },
            title = { Text("Background location (optional)") },
            text = {
                Text(
                    "To follow you as you travel, SkyMood can check your approximate location every 30 minutes " +
                        "while the app is closed. Choose \"Allow all the time\" on the next screen. Without it, " +
                        "SkyMood uses the location saved the last time you opened the app.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundRationale = false
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showBackgroundRationale = false }) { Text("Not now") } },
        )
    }
}

@Composable
private fun WeatherCard(weather: WeatherState?, refreshing: Boolean, onRefresh: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Current weather", style = MaterialTheme.typography.titleMedium)
            if (weather == null) {
                Text("No weather yet. Allow location or set a city below, then refresh.")
            } else {
                Text(
                    "${conditionLabel(weather.condition)} · ${weather.temperatureC.roundToInt()}°C · " +
                        "wind ${weather.windSpeedKmh.roundToInt()} km/h",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(weather.location.name)
                Text(
                    "Updated " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(weather.updatedAtMillis)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) "Refreshing…" else "Refresh now")
            }
        }
    }
}

@Composable
private fun LocationCard(
    settings: Settings,
    hasLocation: Boolean,
    hasBackground: Boolean,
    onRequestLocation: () -> Unit,
    onRequestBackground: () -> Unit,
    onSearch: suspend (String) -> List<SavedLocation>,
    onPickCity: (SavedLocation?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SavedLocation>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Location", style = MaterialTheme.typography.titleMedium)

            val manual = settings.manualLocation
            if (manual != null) {
                Text("Using city: ${manual.name}")
                OutlinedButton(onClick = { onPickCity(null) }) { Text("Use device location instead") }
            } else if (!hasLocation) {
                Text("Location access is off.")
                Button(onClick = onRequestLocation) { Text("Use my location") }
            } else {
                Text("Using your approximate device location.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackground) {
                    OutlinedButton(onClick = onRequestBackground) { Text("Allow background updates (optional)") }
                }
            }

            Text("Set city manually", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("City") },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    scope.launch {
                        searchError = null
                        results = try {
                            onSearch(query).also { if (it.isEmpty()) searchError = "No matches" }
                        } catch (e: Exception) {
                            searchError = "Search failed. Check your connection."
                            emptyList()
                        }
                    }
                }) { Text("Search") }
            }
            searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            results.forEach { city ->
                TextButton(onClick = {
                    onPickCity(city)
                    results = emptyList()
                    query = ""
                }) { Text(city.name) }
            }
        }
    }
}

@Composable
private fun AnimationCard(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
    var intensity by remember(settings.intensity) { mutableFloatStateOf(settings.intensity) }
    var dimLevel by remember(settings.dim) { mutableFloatStateOf(settings.dim) }
    var fpsIndex by remember(settings.fpsCap) {
        mutableIntStateOf(fpsSteps.indexOf(settings.fpsCap).coerceAtLeast(0))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Animation", style = MaterialTheme.typography.titleMedium)

            Picker(
                title = "Landscape",
                current = settings.landscape.label,
                options = LandscapeType.entries.map { it.label to it },
                onPick = { type -> onChange { it.copy(landscape = type) } },
            )
            Picker(
                title = "Preview weather",
                current = settings.previewOverride?.label ?: "Auto (real weather)",
                options = listOf("Auto (real weather)" to null) + SceneType.entries.map { it.label to it },
                onPick = { type -> onChange { it.copy(previewOverride = type) } },
            )
            Picker(
                title = "Preview time of day",
                current = settings.timeOverride?.label ?: "Auto (clock)",
                options = listOf("Auto (clock)" to null) + TimeOfDay.entries.map { it.label to it },
                onPick = { time -> onChange { it.copy(timeOverride = time) } },
            )

            Text("Intensity: ${(intensity * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = intensity,
                onValueChange = { intensity = it },
                onValueChangeFinished = { onChange { s -> s.copy(intensity = intensity) } },
                valueRange = 0.25f..1.5f,
            )

            Text("Dim for readability: ${(dimLevel * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = dimLevel,
                onValueChange = { dimLevel = it },
                onValueChangeFinished = { onChange { s -> s.copy(dim = dimLevel) } },
                valueRange = 0f..0.7f,
            )
            Text(
                "Darkens the wallpaper so app icons, labels and the clock stand out.",
                style = MaterialTheme.typography.bodySmall,
            )

            Text("Frame rate cap: ${fpsSteps[fpsIndex]} FPS", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = fpsIndex.toFloat(),
                onValueChange = { fpsIndex = it.roundToInt().coerceIn(0, 2) },
                onValueChangeFinished = { onChange { s -> s.copy(fpsCap = fpsSteps[fpsIndex]) } },
                valueRange = 0f..2f,
                steps = 1,
            )
            Text(
                "Power saver mode always limits the wallpaper to 15 FPS.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("New landscape every time the phone locks", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.changeOnLock,
                    onCheckedChange = { on -> onChange { it.copy(changeOnLock = on) } },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Birds and animals", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.wildlife,
                    onCheckedChange = { on -> onChange { it.copy(wildlife = on) } },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show temperature on wallpaper", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.showTemperature,
                    onCheckedChange = { on -> onChange { it.copy(showTemperature = on) } },
                )
            }
        }
    }
}

/** A labelled dropdown. [options] pairs a display label with its value. */
@Composable
private fun <T> Picker(title: String, current: String, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(title, style = MaterialTheme.typography.labelLarge)
    Box {
        OutlinedButton(onClick = { open = true }) { Text(current) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, value) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    open = false
                    onPick(value)
                })
            }
        }
    }
}