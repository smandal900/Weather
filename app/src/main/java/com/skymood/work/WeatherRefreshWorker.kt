package com.skymood.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.skymood.data.RefreshResult
import com.skymood.skyMood
import java.util.concurrent.TimeUnit

/**
 * Fetches fresh weather and writes it to the cache; the wallpaper picks it up through its Flow.
 *
 * A fresh device location is used when background location is granted, or when the refresh was
 * started from the foreground UI. Otherwise the repository falls back to the last saved location.
 */
class WeatherRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.skyMood
        val location = app.locationProvider
        val foreground = inputData.getBoolean(KEY_FOREGROUND, false)
        val useDevice = location.hasCoarsePermission() &&
            (foreground || location.hasBackgroundPermission())

        return when (app.repository.refresh(useDeviceLocation = useDevice)) {
            is RefreshResult.Success -> Result.success()
            RefreshResult.NoLocation -> Result.success()
            is RefreshResult.Failure -> if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val PERIODIC_NAME = "weather_refresh_periodic"
        const val ONE_TIME_NAME = "weather_refresh_now"
        private const val KEY_FOREGROUND = "foreground"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun refreshNow(context: Context, fromForeground: Boolean = true) {
            val request = OneTimeWorkRequestBuilder<WeatherRefreshWorker>()
                .setConstraints(networkConstraint)
                .setInputData(workDataOf(KEY_FOREGROUND to fromForeground))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_TIME_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
