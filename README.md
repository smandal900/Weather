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
`SNOWY_PEAKS`, `COUNTRYSIDE_VILLAGE`), or `auto` to clear an override. Extras `--es phase 0.25` (moon phase, 0 new to 0.5 full, `none` to follow the calendar) and `--es wildlife off` are also accepted. The receiver is only in the
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
- **Sun and moon** follow the real date, time and your location (a standard low-precision astronomy formula, good to about a degree for the sun and a few degrees for the moon). The sun is placed by its azimuth and elevation looking south; the moon uses the calendar phase, drawn as new, crescent, quarter, gibbous or full, lit on the right while waxing and on the left while waning. Previews of a chosen time of day use typical positions instead.
- **Birds and animals** depend on weather, time and landscape: swallows and eagles by day, gulls over the sea, a V of geese at dawn and dusk, bats and fireflies after dark, and deer, hares, cows, sheep, horses, goats, dogs, ducks, crabs and dolphins where they belong. Heavy rain and storms keep only ducks, snow keeps goats, hares and deer, and nothing appears when the Birds and animals switch is off. Flyers are drawn as batched line segments and animals as baked sprites, so it stays cheap.
- **New landscape on every lock:** when the screen turns off, SkyMood picks a different landscape right away and paints it in the background, so it is ready, with no fade, before you unlock. It is on by default; turn it off with the *New landscape every time the phone locks* switch (the landscape picker then stays as you set it).
- **Photo clouds:** the clouds are real photographs (CC0 / public domain from Wikimedia Commons, listed in `app/src/main/assets/clouds/LICENSES.txt`), about 190 KB in total. At first start they are decoded once on a background thread and turned into grey-scale cloud shapes with their own transparency (blue-sky photos: transparency from how little blue a pixel has; dark storm photos: darker pixels are cloud), with feathered edges so banks blend. They drift in three depth layers and are tinted for the light, so dusk clouds are pink and night clouds dark. Until they load, the drawn clouds are used.
- **Painted landscapes:** every landscape is painted in detail into a few bitmaps on a background thread (conifers grown branch by branch from needle clusters, leafy trees from shaded clusters, faceted mountains with snow, textured rocks, grass, painted houses and cliffs). The bitmaps are repainted only when the size, time of day or weather changes; each frame only copies them and draws the moving parts (water, boat, foam, palm, waterfall, swaying grass). CPU use stays around 30-45% of one core, comparable to the simple style.
- A GPU-shader version of the sea was tried and dropped because it used about twice the CPU (see the ealistic-sea-demo branch).
- **Cross-fade** captures the old frame once into a reusable bitmap and fades it out, so changes
  of weather, time of day or landscape all blend smoothly.
- **Settings** and the cached weather are each stored as one JSON string in Preferences DataStore.
- **Readability:** a dim layer (default 35%, adjustable under Animation) darkens the top and bottom of the wallpaper, clouds are semi-transparent, and the engine reports dark wallpaper colors so launchers keep light text.
- `FOREGROUND_SERVICE` turned out not to be needed and is not declared.
- **Sky from the weather report, nothing random:** the request now also asks Open-Meteo for cloud cover at low, mid and high level, humidity, visibility, wind direction and gusts. `Atmosphere` turns them into cloud amount per height (near layer = low cloud, far layer = high cloud, each with its own count and thickness), cloud darkness (cover, rain and storms), air clarity (visibility and humidity), and the drift direction. Clouds, rain, snow, fog banks, gusts and leaves travel the way the wind blows (looking south, east is on the left). The sun's brightness, rays and flare, the moon, the stars and the Milky Way fade with cloud cover and hazy air, the far hills fade into the sky when clarity drops, and fog thickness follows visibility. Clear, partly cloudy and windy skies share one sky (sun, moon, stars plus the reported clouds), so a partly cloudy night now shows a dimmed moon and stars. A previewed weather type uses believable fixed numbers for that type but keeps the real wind direction.
- **More landscapes (11 added since the first twelve):** lighthouse, tropical island, harbor, frozen lake, backwaters with a houseboat, city skyline, hilltop temple, camping with a campfire, railway through hills, train on a viaduct, lonely mountain road and bicycle on a country path (sailboat at sunset is the existing sea-with-boat scene at evening time). They use the same painted-bitmap approach plus small animated sprites (boats, palms, trains, cars, a cyclist, flames, beams and flags). Everything sits above the dock row so it stays visible, and the shared helpers live in `SceneryKit.kt`.
- **No scratchy mountains:** mountain faces and snow caps are shaded with one smooth path per layer (slices drawn separately left hairline seams and random grooves that looked like scratches), the light side is decided over a wider stretch of ridge so it does not flip every few pixels, and the sky gradient is dithered to hide banding.
