package com.carolinasvrwx.stormpulse

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Reads active, official NWS alerts and converts supported GeoJSON geometries
 * into route-matchable hazards. Call fetchActiveAlerts() off the main thread.
 */
class NwsAlertRepository(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 15_000
) {
    companion object {
        const val ACTIVE_ALERTS_URL =
            "https://api.weather.gov/alerts/active?status=actual&message_type=alert,update"

        // NWS requests require an identifying User-Agent with contact information.
        const val USER_AGENT =
            "StormPulse-Android/1.1 (https://carolinasevereweatherresearch.com; info@carolinasvrwxresearch.com)"
    }

    fun fetchActiveAlerts(): List<WeatherHazard> {
        val connection = (URL(ACTIVE_ALERTS_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/geo+json")
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw NwsApiException("NWS returned HTTP $status")
            }
            return parseFeatureCollection(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    fun parseFeatureCollection(collection: JSONObject): List<WeatherHazard> {
        val features = collection.optJSONArray("features") ?: JSONArray()
        val hazards = mutableListOf<WeatherHazard>()

        for (featureIndex in 0 until features.length()) {
            val feature = features.optJSONObject(featureIndex) ?: continue
            val properties = feature.optJSONObject("properties") ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue
            val id = feature.optString("id").takeIf { it.isNotBlank() }
                ?: properties.optString("id").takeIf { it.isNotBlank() }
                ?: "nws-alert-$featureIndex"
            val event = properties.optString("event", "Weather Alert")
            val severity = parseSeverity(properties.optString("severity"))
            val expires = parseInstant(properties.optString("expires"))
                ?: parseInstant(properties.optString("ends"))

            polygonRings(geometry).forEachIndexed { polygonIndex, ring ->
                val points = parseOuterRing(ring)
                if (points.size >= 3) {
                    hazards += WeatherHazard(
                        id = if (polygonIndex == 0) id else "$id-$polygonIndex",
                        event = event,
                        severity = severity,
                        polygon = points,
                        expiresAtEpochMillis = expires
                    )
                }
            }
        }
        return hazards
    }

    private fun polygonRings(geometry: JSONObject): List<JSONArray> {
        val coordinates = geometry.optJSONArray("coordinates") ?: return emptyList()
        return when (geometry.optString("type")) {
            "Polygon" -> listOfNotNull(coordinates.optJSONArray(0))
            "MultiPolygon" -> (0 until coordinates.length()).mapNotNull { index ->
                coordinates.optJSONArray(index)?.optJSONArray(0)
            }
            else -> emptyList()
        }
    }

    /** GeoJSON positions are [longitude, latitude]. The first ring is the exterior. */
    private fun parseOuterRing(ring: JSONArray): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        for (index in 0 until ring.length()) {
            val position = ring.optJSONArray(index) ?: continue
            if (position.length() < 2) continue
            val longitude = position.optDouble(0, Double.NaN)
            val latitude = position.optDouble(1, Double.NaN)
            if (latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
            ) {
                points += GeoPoint(latitude, longitude)
            }
        }
        // GeoJSON commonly repeats the first coordinate at the end. The matcher closes rings itself.
        if (points.size > 1 && points.first() == points.last()) points.removeAt(points.lastIndex)
        return points
    }

    private fun parseSeverity(value: String): HazardSeverity = when (value.lowercase()) {
        "extreme" -> HazardSeverity.EXTREME
        "severe" -> HazardSeverity.SEVERE
        "moderate" -> HazardSeverity.MODERATE
        "minor" -> HazardSeverity.MINOR
        else -> HazardSeverity.UNKNOWN
    }

    private fun parseInstant(value: String): Long? = try {
        Instant.parse(value).toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

class NwsApiException(message: String) : Exception(message)
