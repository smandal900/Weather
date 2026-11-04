# SkyMood

An Android live wallpaper that animates the real-time weather at your location: rain, snow,
thunderstorms, clouds, fog, a sunny sky by day and stars at night. Weather comes from
[Open-Meteo](https://open-meteo.com/) (no API key needed).

## Build

Requirements: Android SDK with platform 37 installed and a JDK 17+ (the JBR bundled with
Android Studio works).

```bash
./gradlew assembleDebug
```

On Windows use `gradlew.bat assembleDebug`. The APK is written to
`app/build/outputs/apk/debug/app-debug.apk`.

Run the unit tests (WMO mapper, scene selection, repository with a fake API):

```bash
./gradlew testDebugUnitTest
```

## Install

With a device connected (USB or wireless debugging) or an emulator running:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Set the wallpaper

1. Open **SkyMood** from the launcher.
2. Tap **Use my location** and allow approximate location, or search for a city under
   **Set city manually**.
3. Optional: tap **Allow background updates** and choose "Allow all the time" so SkyMood follows
   you while the app is closed. Without it, SkyMood uses the location saved the last time you
   opened the app.
4. Tap **Set as wallpaper**, then **Set wallpaper** in the system preview.

You can also open the system live-wallpaper preview directly:

```bash
adb shell am start -a android.service.wallpaper.CHANGE_LIVE_WALLPAPER --ecn android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT com.skymood/.wallpaper.SkyWallpaperService
```

Weather refreshes every 30 minutes in the background (when online) and whenever you tap
**Refresh now**. The wallpaper switches scenes live, with a 2-second cross-fade.

## Scenes: 4 times x 12 landscapes x 10 weathers = 480

Every frame is three layers: the sky (gradient, sun or moon, clouds), the **landscape**, and the
weather in front (rain, snow, fog, haze, lightning flashes).

- **Time of day** (Morning, Day, Evening, Night) follows the clock and Open-Meteo's `is_day`.
  It sets the sky colours and lights the landscape: warm at morning and evening, dark blue at
  night, with village windows and boat lamps lit after dark.
- **Landscape** is picked in settings: Forest, Mountain, Hill, Sea beach, Open sea, Sea with
  boat, Grass field, Lake, River valley, Waterfall, Snowy peaks or Countryside village.
- **Weather** follows the live data: Clear, Partly cloudy, Overcast, Drizzle, Heavy rain,
  Thunderstorm, Fog / mist, Snow, Windy and Haze. Snow whitens the ground and rooftops; rain and
  storms darken and desaturate the landscape; wind sways grass, reeds, palms and boats.

## Test any scene with the preview overrides

In the app, under **Animation**, pick a **Landscape**, a **Preview weather** and a **Preview
time of day**. The in-app preview and the actual wallpaper switch immediately (with a 2-second
cross-fade). Pick **Auto** to go back to the real weather and clock.

Debug builds can also be driven from adb, which is handy for stepping through all 480 scenes:

```bash
adb shell am broadcast -n com.skymood/.debug.DebugSceneReceiver --es weather STORM --es time DUSK --es landscape LAKE
```

Use the enum names (`CLEAR`, `PARTLY_CLOUDY`, `OVERCAST`, `DRIZZLE`, `HEAVY_RAIN`, `STORM`,
`FOG`, `SNOW`, `WINDY`, `HAZE`; `DAWN`, `DAY`, `DUSK`, `NIGHT`; `FOREST`, `MOUNTAIN`, `HILL`,
`SEA_BEACH`, `OPEN_SEA`, `SEA_WITH_BOAT`, `GRASS_FIELD`, `LAKE`, `RIVER_VALLEY`, `WATERFALL`,
`SNOWY_PEAKS`, `COUNTRYSIDE_VILLAGE`), or `auto` to clear an override. The receiver is only in the
debug build.
The **Intensity** slider scales particle counts (for example rain goes from about 150 drops when
light to about 600 when heavy), **Frame rate cap** chooses 15, 30 or 60 FPS, and **Show
temperature** draws a small temperature label in the top-right corner of the wallpaper.

Tips for checking specific behaviour:

- **Lightning:** choose Storm and wait; strikes come every 4 to 12 seconds.
- **Cross-fade:** switch between two overrides while watching the preview.
- **Parallax:** set the wallpaper and swipe between home screens (needs a launcher that reports
  wallpaper offsets; many do, some keep it fixed).
- **Power saver:** turn on battery saver; the wallpaper drops to 15 FPS.
- **Offline:** turn on airplane mode and tap Refresh now; the last cached weather stays on screen.

## Architecture

```
app/src/main/java/com/skymood/
 ├─ data/        WeatherApi (Retrofit + kotlinx.serialization), WeatherRepository,
 │               LocationProvider (FusedLocation), WeatherCache (DataStore), WeatherStore
 ├─ domain/      WeatherCondition, SceneType, WmoMapper, SceneSelector, models
 ├─ wallpaper/   SkyWallpaperService (+ SkyEngine), SceneDirector (scene switching, cross-fade,
 │               temperature overlay), render/ (FrameRenderer, CanvasFrameRenderer),
 │               scenes/ (ClearSky = Clear/Night, Cloud, Rain, Storm, Fog, Snow, Windy, Haze,
 │               SkyGradient, SunGlow), landscape/ (12 landscapes, Lighting, Ridge, Trees, Water),
 │               particles/ (ParticlePool)
 ├─ work/        WeatherRefreshWorker
 └─ ui/          SettingsActivity (Compose), PreviewScreen
```

| WMO codes | Weather scene |
|---|---|
| 0, 1 | Clear (sun by day, moon, stars and Milky Way at night) |
| 2 | Partly cloudy |
| 3 | Overcast |
| 45, 48 | Fog / mist |
| 51–57, 61, 66, 80 | Drizzle (light rain) |
| 63, 65, 67, 81, 82 | Heavy rain |
| 71–77, 85, 86 | Snow |
| 95–99 | Thunderstorm |
| any dry code with wind of 35 km/h or more | Windy |
Every weather scene implements `Scene` (`update(dtMs)`, `draw(canvas)` split into `drawBackground` and `drawForeground`, `setIntensity(float)`,
`resize(w, h)`). Particles come from fixed-size pools allocated up front, and streaks are batched
into one `drawLines` call per layer, so the draw loop does not allocate. The render loop stops
when the wallpaper is not visible.

## Decisions

Choices made where the spec left room:

- **Toolchain:** AGP 9.4 with built-in Kotlin, Gradle 9.6, compile/target SDK 37, matching the
  other projects on this machine.
- **Retrofit 2.11** with the official kotlinx.serialization converter.
- **SkyEngine is an inner class of SkyWallpaperService.** Kotlin cannot subclass
  `WallpaperService.Engine` (a Java inner class) from another file, so the engine lives in the
  service file and delegates to `SceneDirector` and `FrameRenderer`.
- **Rendering runs on the main looper**, as in the platform's sample wallpapers. This avoids
  locking between the engine callbacks and the frame loop. `FrameRenderer` is the seam for a
  future OpenGL ES renderer, which would own its own thread.
- **Hardware canvas** (`lockHardwareCanvas`) is used, falling back to a software canvas.
- **FPS:** default cap 30. The settings slider also allows 60; battery saver always forces 15.
- **Unknown WMO codes** fall back to Clear. With no cache at all, the scene is Clear from 06:00 to
  17:59 local time and Night otherwise.
- **Wind direction** is not requested from the API, so wind always tilts rain and snow to the
  right. Tilt is up to about 35° at 60 km/h.
- **Time of day:** `is_day = 0` is night; during daylight, 05:00–07:59 is dawn and 17:00–20:59 is
  dusk, otherwise day.
- **Location names** for device locations come from the platform `Geocoder` (falls back to
  coordinates). Manual cities use the Open-Meteo geocoding name.
- **Location priority:** manual city, then a fresh device location, then the last saved one.
  The app also saves a fresh location each time it opens, which is what background refreshes use
  when background location is not granted.
- **Preview override intensity** is a fixed 70%, scaled by the intensity slider.
- **Haze** has no WMO code in Open-Meteo, so it only appears through the preview override.
- **Windy** replaces Clear, Partly cloudy or Overcast when the wind is 35 km/h or more. A forced
  Windy preview uses at least 50 km/h.
- **Light rain** (codes 61, 66, 80) uses the Drizzle scene; moderate and heavy rain use Heavy rain.
- **Morning / Evening** are the dawn and dusk buckets: 05:00–07:59 and 17:00–20:59 in daylight.
- **Landscape** defaults to Forest and is never chosen automatically.
- **Cross-fade** captures the old frame once into a reusable bitmap and fades it out, so changes
  of weather, time of day or landscape all blend smoothly.
- **Settings** and the cached weather are each stored as one JSON string in Preferences DataStore.
- **Readability:** a dim layer (default 35%, adjustable under Animation) darkens the top and bottom of the wallpaper, clouds are semi-transparent, and the engine reports dark wallpaper colors so launchers keep light text.
- `FOREGROUND_SERVICE` turned out not to be needed and is not declared.
