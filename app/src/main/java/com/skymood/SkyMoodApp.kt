package com.skymood

import android.app.Application
import android.content.Context
import com.skymood.data.LocationProvider
import com.skymood.data.WeatherApi
import com.skymood.data.WeatherCache
import com.skymood.data.WeatherRepository
import com.skymood.work.WeatherRefreshWorker

class SkyMoodApp : Application() {

    val cache by lazy { WeatherCache(this) }
    val locationProvider by lazy { LocationProvider(this) }
    val repository by lazy { WeatherRepository(WeatherApi.create(), cache, locationProvider) }

    override fun onCreate() {
        super.onCreate()
        WeatherRefreshWorker.schedulePeriodic(this)
    }
}

val Context.skyMood: SkyMoodApp get() = applicationContext as SkyMoodApp
