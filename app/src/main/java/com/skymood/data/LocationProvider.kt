package com.skymood.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.skymood.domain.SavedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** Coarse device location through the fused location provider. */
class LocationProvider(context: Context) : LocationSource {

    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    fun hasCoarsePermission(): Boolean = granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasBackgroundPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): SavedLocation? {
        if (!hasCoarsePermission()) return null
        val cts = CancellationTokenSource()
        val location = try {
            withTimeoutOrNull(15_000) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
            } ?: client.lastLocation.await()
        } catch (e: SecurityException) {
            // Happens in the background without ACCESS_BACKGROUND_LOCATION.
            null
        } finally {
            cts.cancel()
        } ?: return null
        val name = placeName(location.latitude, location.longitude)
        return SavedLocation(name, location.latitude, location.longitude)
    }

    @Suppress("DEPRECATION")
    private suspend fun placeName(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        val fallback = String.format(Locale.US, "%.2f, %.2f", lat, lon)
        if (!Geocoder.isPresent()) return@withContext fallback
        runCatching {
            Geocoder(appContext, Locale.getDefault()).getFromLocation(lat, lon, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull() ?: fallback
    }
}
