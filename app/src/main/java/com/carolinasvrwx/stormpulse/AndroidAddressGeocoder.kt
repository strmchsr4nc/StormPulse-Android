package com.carolinasvrwx.stormpulse

import android.content.Context
import android.location.Geocoder
import java.io.IOException
import java.util.Locale

data class GeocodedAddress(
    val formattedAddress: String,
    val point: GeoPoint
)

/**
 * Android's platform geocoder adapter for destination address lookup.
 *
 * Call search() from a background thread. Device geocoder availability and
 * returned coverage depend on the Android device/location backend.
 */
class AndroidAddressGeocoder(context: Context) {
    private val geocoder = Geocoder(context.applicationContext, Locale.US)

    @Suppress("DEPRECATION")
    @Throws(IOException::class)
    fun search(address: String, maxResults: Int = 5): List<GeocodedAddress> {
        require(address.isNotBlank()) { "Enter a destination address." }
        require(maxResults in 1..20) { "maxResults must be between 1 and 20." }
        if (!Geocoder.isPresent()) {
            throw IOException("Address lookup is not available on this device.")
        }

        val matches = geocoder.getFromLocationName(address.trim(), maxResults)
            ?: return emptyList()

        return matches.mapNotNull { result ->
            val label = result.getAddressLine(0)?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            GeocodedAddress(
                formattedAddress = label,
                point = GeoPoint(result.latitude, result.longitude)
            )
        }
    }
}
