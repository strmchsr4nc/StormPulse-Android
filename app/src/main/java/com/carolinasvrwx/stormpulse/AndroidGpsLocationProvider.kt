package com.carolinasvrwx.stormpulse

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import java.io.Closeable

/**
 * Lightweight Android location adapter for foreground navigation.
 *
 * The host Activity must request runtime location permission before calling
 * start(). Updates are delivered on the main looper by default. This class
 * does not start a background service or claim to keep tracking when the app
 * is backgrounded; that requires a separately permissioned foreground service.
 */
class AndroidGpsLocationProvider(
    context: Context,
    private val onLocation: (GeoPoint, Float, Long) -> Unit,
    private val onError: (String) -> Unit,
    private val minTimeMillis: Long = 1_000L,
    private val minDistanceMeters: Float = 5f,
    private val callbackLooper: Looper = Looper.getMainLooper()
) : Closeable {
    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listening = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onLocation(
                GeoPoint(location.latitude, location.longitude),
                location.accuracy,
                location.time
            )
        }

        @Deprecated("Deprecated by Android; retained for older platform callbacks")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            ) {
                onError("Location services are unavailable. Enable GPS or network location.")
            }
        }
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    fun start() {
        if (listening) return
        if (!hasLocationPermission()) {
            onError("StormPulse needs location permission to determine your route starting point.")
            return
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider ->
                runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
            }
        if (providers.isEmpty()) {
            onError("No location provider is enabled. Turn on device location services.")
            return
        }

        try {
            providers.forEach { provider ->
                locationManager.requestLocationUpdates(
                    provider,
                    minTimeMillis.coerceAtLeast(1_000L),
                    minDistanceMeters.coerceAtLeast(0f),
                    listener,
                    callbackLooper
                )
            }
            listening = true
            val recent = providers.mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }.maxByOrNull { it.time }
            recent?.let {
                onLocation(GeoPoint(it.latitude, it.longitude), it.accuracy, it.time)
            }
        } catch (security: SecurityException) {
            stop()
            onError("Location permission was revoked. Allow location access and try again.")
        } catch (error: IllegalArgumentException) {
            stop()
            onError("The device could not start location tracking: ${error.message ?: "provider unavailable"}")
        }
    }

    fun stop() {
        // Always attempt cleanup: registration can partially succeed before a provider throws.
        runCatching { locationManager.removeUpdates(listener) }
        listening = false
    }

    override fun close() = stop()
}
