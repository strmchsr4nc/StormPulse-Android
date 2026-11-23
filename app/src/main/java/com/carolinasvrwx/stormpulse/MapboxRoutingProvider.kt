package com.carolinasvrwx.stormpulse

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Mapbox Geocoding v6 forward-search adapter.
 *
 * Results are returned to the caller only; do not persist temporary
 * geocoding results unless Mapbox permanent-geocoding terms are enabled.
 */
class MapboxAddressSearch(private val accessToken: String) {
    init {
        require(accessToken.isNotBlank()) { "A Mapbox public access token is required." }
    }

    fun search(query: String, limit: Int = 5, country: String = "us"): List<GeocodedAddress> {
        require(query.isNotBlank()) { "Enter a destination address." }
        require(limit in 1..10) { "Result limit must be between 1 and 10." }

        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val countryParam = URLEncoder.encode(country, Charsets.UTF_8.name())
        val endpoint = "https://api.mapbox.com/search/geocode/v6/forward" +
            "?q=$encoded&limit=$limit&country=$countryParam&access_token=$accessToken"
        val json = getJson(endpoint)
        val features = json.optJSONArray("features") ?: return emptyList()

        return (0 until features.length()).mapNotNull { index ->
            val feature = features.optJSONObject(index) ?: return@mapNotNull null
            val properties = feature.optJSONObject("properties") ?: JSONObject()
            val geometry = feature.optJSONObject("geometry") ?: return@mapNotNull null
            val coordinates = geometry.optJSONArray("coordinates") ?: return@mapNotNull null
            if (coordinates.length() < 2) return@mapNotNull null

            val longitude = coordinates.optDouble(0, Double.NaN)
            val latitude = coordinates.optDouble(1, Double.NaN)
            if (!longitude.isFinite() || !latitude.isFinite()) return@mapNotNull null

            val label = properties.optString("full_address")
                .takeIf { it.isNotBlank() }
                ?: properties.optString("name").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            GeocodedAddress(label, GeoPoint(latitude, longitude))
        }
    }
}

/**
 * Mapbox Directions API v5 adapter. Requests full GeoJSON geometry and
 * turn-by-turn steps, with imperial voice instruction wording.
 */
class MapboxRoutePlanningProvider(
    private val accessToken: String,
    private val profile: String = PROFILE_DRIVING_TRAFFIC
) : RoutePlanningProvider {
    companion object {
        const val PROFILE_DRIVING_TRAFFIC = "mapbox/driving-traffic"
        const val PROFILE_DRIVING = "mapbox/driving"
        const val PROFILE_WALKING = "mapbox/walking"
        const val PROFILE_CYCLING = "mapbox/cycling"
        private val VALID_PROFILES = setOf(
            PROFILE_DRIVING_TRAFFIC, PROFILE_DRIVING, PROFILE_WALKING, PROFILE_CYCLING
        )
    }

    init {
        require(accessToken.isNotBlank()) { "A Mapbox public access token is required." }
        require(profile in VALID_PROFILES) { "Unsupported Mapbox routing profile." }
    }

    override fun calculateRoute(origin: GeoPoint, destination: RouteDestination): PlannedRoute {
        val coordinates = "${origin.longitude},${origin.latitude};" +
            "${destination.point.longitude},${destination.point.latitude}"
        val endpoint = "https://api.mapbox.com/directions/v5/$profile/$coordinates" +
            "?alternatives=true&steps=true&voice_instructions=true&voice_units=imperial" +
            "&overview=full&geometries=geojson&language=en&access_token=$accessToken"
        val response = getJson(endpoint)
        val routes = response.optJSONArray("routes")
            ?: throw IOException("Mapbox did not return a routes collection.")
        if (routes.length() == 0) throw IOException("Mapbox could not find a route to this destination.")

        val route = routes.getJSONObject(0)
        val geometry = route.optJSONObject("geometry")
            ?: throw IOException("Mapbox route geometry was missing.")
        val coordinatesArray = geometry.optJSONArray("coordinates")
            ?: throw IOException("Mapbox route coordinates were missing.")
        val points = (0 until coordinatesArray.length()).mapNotNull { index ->
            val pair = coordinatesArray.optJSONArray(index) ?: return@mapNotNull null
            if (pair.length() < 2) return@mapNotNull null
            val longitude = pair.optDouble(0, Double.NaN)
            val latitude = pair.optDouble(1, Double.NaN)
            if (!longitude.isFinite() || !latitude.isFinite()) null
            else GeoPoint(latitude, longitude)
        }
        if (points.size < 2) throw IOException("Mapbox returned incomplete route geometry.")

        val maneuvers = mutableListOf<RouteManeuver>()
        val legs = route.optJSONArray("legs") ?: org.json.JSONArray()
        for (legIndex in 0 until legs.length()) {
            val steps = legs.optJSONObject(legIndex)?.optJSONArray("steps") ?: continue
            for (stepIndex in 0 until steps.length()) {
                val step = steps.optJSONObject(stepIndex) ?: continue
                val maneuver = step.optJSONObject("maneuver") ?: continue
                val instruction = maneuver.optString("instruction").takeIf { it.isNotBlank() } ?: continue
                val location = maneuver.optJSONArray("location") ?: continue
                if (location.length() < 2) continue
                val longitude = location.optDouble(0, Double.NaN)
                val latitude = location.optDouble(1, Double.NaN)
                val distance = step.optDouble("distance", Double.NaN)
                if (!longitude.isFinite() || !latitude.isFinite() || !distance.isFinite() || distance < 0.0) continue
                maneuvers += RouteManeuver(instruction, distance, GeoPoint(latitude, longitude))
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

private fun getJson(endpoint: String): JSONObject {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "StormPulse-Android/1.1")
    }
    try {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
            throw IOException(message?.takeIf { it.isNotBlank() } ?: "Mapbox request failed (HTTP $status).")
        }
        return JSONObject(body)
    } finally {
        connection.disconnect()
    }
}
