package com.carolinasvrwx.stormpulse

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Free OpenStreetMap-based routing through the public OSRM demo service.
 *
 * Network calls are blocking and MUST run off the Android main thread.
 * The public demo server is best-effort, rate-limited/fair-use and has no SLA.
 * For production-scale traffic, deploy a private OSRM instance.
 */
class OsrmRoutePlanningProvider(
    private val baseUrl: String = DEFAULT_BASE_URL
) : RoutePlanningProvider {
    companion object {
        const val DEFAULT_BASE_URL = "https://router.project-osrm.org"
    }

    override fun calculateRoute(origin: GeoPoint, destination: RouteDestination): PlannedRoute {
        val coordinates = String.format(
            Locale.US, "%.6f,%.6f;%.6f,%.6f",
            origin.longitude, origin.latitude,
            destination.point.longitude, destination.point.latitude
        )
        val endpoint = "${baseUrl.trimEnd('/')}/route/v1/driving/$coordinates" +
            "?overview=full&geometries=geojson&steps=true&alternatives=true"
        val response = requestJson(endpoint)
        if (response.optString("code") != "Ok") {
            throw IOException(response.optString("message").ifBlank {
                "OpenStreetMap routing could not find a drivable route."
            })
        }
        val routes = response.optJSONArray("routes")
            ?: throw IOException("Routing service returned no routes.")
        if (routes.length() == 0) throw IOException("No driving route was found.")

        val route = routes.getJSONObject(0)
        val geometry = route.optJSONObject("geometry")
            ?: throw IOException("Route geometry was missing.")
        val coordinatesArray = geometry.optJSONArray("coordinates")
            ?: throw IOException("Route coordinates were missing.")
        val points = (0 until coordinatesArray.length()).mapNotNull { index ->
            val pair = coordinatesArray.optJSONArray(index) ?: return@mapNotNull null
            if (pair.length() < 2) return@mapNotNull null
            val lon = pair.optDouble(0, Double.NaN)
            val lat = pair.optDouble(1, Double.NaN)
            if (!lon.isFinite() || !lat.isFinite()) null else GeoPoint(lat, lon)
        }
        if (points.size < 2) throw IOException("Routing service returned incomplete geometry.")

        val maneuvers = mutableListOf<RouteManeuver>()
        val legs = route.optJSONArray("legs") ?: org.json.JSONArray()
        for (legIndex in 0 until legs.length()) {
            val steps = legs.optJSONObject(legIndex)?.optJSONArray("steps") ?: continue
            for (stepIndex in 0 until steps.length()) {
                val step = steps.optJSONObject(stepIndex) ?: continue
                val maneuver = step.optJSONObject("maneuver") ?: continue
                val location = maneuver.optJSONArray("location") ?: continue
                if (location.length() < 2) continue
                val lon = location.optDouble(0, Double.NaN)
                val lat = location.optDouble(1, Double.NaN)
                val distance = step.optDouble("distance", Double.NaN)
                if (!lon.isFinite() || !lat.isFinite() || !distance.isFinite() || distance < 0.0) continue

                val type = maneuver.optString("type").replace('_', ' ')
                val modifier = maneuver.optString("modifier").replace('_', ' ')
                val road = step.optString("name").takeIf { it.isNotBlank() }
                val instruction = buildString {
                    append(type.replaceFirstChar { it.uppercase() })
                    if (modifier.isNotBlank()) append(" ").append(modifier)
                    if (road != null) append(" onto ").append(road)
                }
                maneuvers += RouteManeuver(
                    instruction,
                    distance,
                    GeoPoint(lat, lon)
                )
            }
        }

        return PlannedRoute(
            destination = destination,
            geometry = points,
            distanceMeters = route.optDouble("distance", Double.NaN),
            durationSeconds = route.optDouble("duration", Double.NaN),
            maneuvers = maneuvers
        )
    }
}

/**
 * Free OpenStreetMap Nominatim address lookup.
 *
 * Public Nominatim has a strict maximum of one request per second, requires
 * an identifying User-Agent, and must not be used for autocomplete. Call this
 * only after an explicit user search, from a background thread.
 */
class NominatimAddressSearch(
    private val userAgent: String = "StormPulse-Android/1.1 (info@carolinasvrwxresearch.com)"
) {
    fun search(query: String, limit: Int = 5): List<GeocodedAddress> {
        require(query.isNotBlank()) { "Enter a destination address." }
        require(limit in 1..10) { "Result limit must be between 1 and 10." }

        // Nominatim's limit applies across the app, not per geocoder instance.
        // Synchronize the request start globally so simultaneous searches cannot
        // bypass the one-request-per-second policy through separate instances.
        synchronized(rateLimitLock) {
            val wait = 1_100L - (System.currentTimeMillis() - lastRequestAt)
            if (wait > 0L) Thread.sleep(wait)
            lastRequestAt = System.currentTimeMillis()
        }

        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val endpoint = "https://nominatim.openstreetmap.org/search" +
            "?q=$encoded&format=jsonv2&limit=$limit&countrycodes=us&addressdetails=1"
        val results = requestArray(endpoint, userAgent)
        return (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val lat = item.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
            val lon = item.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
            val label = item.optString("display_name").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            GeocodedAddress(label, GeoPoint(lat, lon))
        }
    }

    companion object {
        private val rateLimitLock = Any()
        @Volatile private var lastRequestAt: Long = 0L
    }
}

private fun requestJson(endpoint: String): JSONObject {
    val connection = openConnection(endpoint, "StormPulse-Android/1.1")
    try {
        val status = connection.responseCode
        val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw IOException("Routing request failed (HTTP $status).")
        return JSONObject(body)
    } finally {
        connection.disconnect()
    }
}

private fun requestArray(endpoint: String, userAgent: String): org.json.JSONArray {
    val connection = openConnection(endpoint, userAgent)
    try {
        val status = connection.responseCode
        val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw IOException("Address search failed (HTTP $status).")
        return org.json.JSONArray(body)
    } finally {
        connection.disconnect()
    }
}

private fun openConnection(endpoint: String, userAgent: String) =
    (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", userAgent)
    }
