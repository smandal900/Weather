package com.skymood.wallpaper.fauna

/**
 * A switch for inspecting sprites: when [sheet] names a species (optionally ":variant", e.g. "HORSE:1"),
 * "BIRD", or "INSECT", the wallpaper tiles every frame of it over the scene instead of waiting for one to
 * happen on screen. Set from the debug broadcast receiver; null (the default) costs nothing.
 */
object FaunaDebug {
    @Volatile
    @JvmField
    var sheet: String? = null
}
