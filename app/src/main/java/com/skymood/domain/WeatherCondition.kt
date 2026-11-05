package com.skymood.domain

/** Broad weather condition derived from a WMO weather code. */
enum class WeatherCondition { CLEAR, CLOUDY, FOG, RAIN, SNOW, STORM }

/**
 * The weather layer the wallpaper shows. Combined with a [TimeOfDay] and a [LandscapeType]
 * this gives 4 x 12 x 10 = 480 scenes. At night, Clear shows stars and the moon.
 */
enum class SceneType(val label: String) {
    CLEAR("Clear"),
    PARTLY_CLOUDY("Partly cloudy"),
    OVERCAST("Overcast"),
    DRIZZLE("Drizzle"),
    HEAVY_RAIN("Heavy rain"),
    STORM("Thunderstorm"),
    FOG("Fog / mist"),
    SNOW("Snow"),
    WINDY("Windy"),
    HAZE("Haze"),
}

/** The scenery in front of the sky. Picked in settings. */
enum class LandscapeType(val label: String) {
    FOREST("Forest"),
    MOUNTAIN("Mountain"),
    HILL("Hill"),
    SEA_BEACH("Sea beach"),
    OPEN_SEA("Open sea"),
    SEA_WITH_BOAT("Sea with boat"),
    GRASS_FIELD("Grass field"),
    LAKE("Lake"),
    RIVER_VALLEY("River valley"),
    WATERFALL("Waterfall"),
    SNOWY_PEAKS("Snowy peaks"),
    COUNTRYSIDE_VILLAGE("Countryside village"),
    LIGHTHOUSE("Lighthouse on rocks"),
    TROPICAL_ISLAND("Tropical island"),
    HARBOR("Harbor with fishing boats"),
    FROZEN_LAKE("Frozen lake"),
    MANGROVE_BACKWATERS("Backwaters with houseboat"),
    CITY_SKYLINE("City skyline"),
    TEMPLE_HILLTOP("Temple on a hilltop"),
    CAMPING("Camping with campfire"),
    RAILWAY_HILLS("Railway through hills"),
    TRAIN_VIADUCT("Train on a bridge"),
    COUNTRY_CYCLE("Bicycle on a country path"),
}
