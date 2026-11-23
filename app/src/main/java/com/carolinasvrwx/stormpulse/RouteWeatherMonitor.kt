package com.carolinasvrwx.stormpulse

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class NwsAlertKind { WARNING, WATCH, ADVISORY, OTHER }
enum class RouteAlertStatus { ON_ROUTE, AHEAD, NEARBY }

data class RouteWeatherAlert(
    val hazard: WeatherHazard,
    val kind: NwsAlertKind,
    val status: RouteAlertStatus,
    val distanceFromRouteStartMeters: Double,
    val distanceAheadMeters: Double,
    val spokenMessage: String
)

data class RouteWeatherAssessment(
    val alerts: List<RouteWeatherAlert>,
    val checkedAtEpochMillis: Long,
    val dataIsStale: Boolean
) {
    val hasOnRouteWarning: Boolean
        get() = alerts.any { it.kind == NwsAlertKind.WARNING && it.status == RouteAlertStatus.ON_ROUTE }
}

/** Converts route/polygon intersections into ordered, driver-readable alert assessments. */
class RouteWeatherMonitor(
    private val nearbyDistanceMeters: Double = 25_000.0,
    private val staleAfterMillis: Long = 15 * 60 * 1000L
) {
    fun assess(
        route: List<GeoPoint>,
        hazards: List<WeatherHazard>,
        currentLocation: GeoPoint,
        checkedAtEpochMillis: Long,
        dataReceivedAtEpochMillis: Long
    ): RouteWeatherAssessment {
        val stale = checkedAtEpochMillis - dataReceivedAtEpochMillis > staleAfterMillis
        if (route.size < 2) return RouteWeatherAssessment(emptyList(), checkedAtEpochMillis, stale)

        val progress = nearestProgressMeters(route, currentLocation)
        val matches = RouteHazardMatcher.match(route, hazards, checkedAtEpochMillis)
        val alerts = matches.mapNotNull { match ->
            val hazard = match.hazard
            val kind = classify(hazard.event)
            if (kind == NwsAlertKind.OTHER) return@mapNotNull null

            val distanceFromStart = distanceAlongRoute(
                route, match.firstIntersectingSegment, match.firstEncounterFraction
            )
            val distanceAhead = (distanceFromStart - progress).coerceAtLeast(0.0)
            val status = when {
                distanceAhead <= 100.0 -> RouteAlertStatus.ON_ROUTE
                distanceAhead <= nearbyDistanceMeters -> RouteAlertStatus.AHEAD
                else -> return@mapNotNull null
            }
            RouteWeatherAlert(
                hazard, kind, status, distanceFromStart, distanceAhead,
                spokenMessage(hazard.event, kind, status, distanceAhead)
            )
        }.sortedWith(compareBy<RouteWeatherAlert> { priority(it.kind) }
            .thenBy { it.distanceAheadMeters })

        return RouteWeatherAssessment(alerts, checkedAtEpochMillis, stale)
    }

    private fun classify(event: String): NwsAlertKind {
        val value = event.lowercase()
        return when {
            "warning" in value -> NwsAlertKind.WARNING
            "watch" in value -> NwsAlertKind.WATCH
            "advisory" in value || "statement" in value -> NwsAlertKind.ADVISORY
            else -> NwsAlertKind.OTHER
        }
    }

    private fun priority(kind: NwsAlertKind) = when (kind) {
        NwsAlertKind.WARNING -> 0
        NwsAlertKind.WATCH -> 1
        NwsAlertKind.ADVISORY -> 2
        NwsAlertKind.OTHER -> 3
    }

    private fun spokenMessage(event: String, kind: NwsAlertKind, status: RouteAlertStatus, meters: Double): String {
        val subject = when (kind) {
            NwsAlertKind.WARNING -> "Weather warning"
            NwsAlertKind.WATCH -> "Weather watch"
            NwsAlertKind.ADVISORY -> "Weather advisory"
            NwsAlertKind.OTHER -> "Weather alert"
        }
        return when (status) {
            RouteAlertStatus.ON_ROUTE -> "$subject: $event intersects your planned route. Follow official instructions."
            RouteAlertStatus.AHEAD -> "$subject: $event is about ${distancePhrase(meters)} ahead on your route. Check conditions and official guidance."
            RouteAlertStatus.NEARBY -> "$subject: $event is nearby."
        }
    }

    private fun distancePhrase(meters: Double): String =
        if (meters >= 1609.344) "%.1f miles".format(meters / 1609.344)
        else "${meters.toInt().coerceAtLeast(1)} meters"

    private fun nearestProgressMeters(route: List<GeoPoint>, location: GeoPoint): Double {
        var bestDistance = Double.POSITIVE_INFINITY
        var bestProgress = 0.0
        var traversed = 0.0
        for (i in 0 until route.lastIndex) {
            val segmentLength = haversine(route[i], route[i + 1])
            val fraction = projectionFraction(route[i], route[i + 1], location)
            val projected = interpolate(route[i], route[i + 1], fraction)
            val distance = haversine(location, projected)
            if (distance < bestDistance) {
                bestDistance = distance
                bestProgress = traversed + segmentLength * fraction
            }
            traversed += segmentLength
        }
        return bestProgress
    }

    private fun distanceAlongRoute(route: List<GeoPoint>, segment: Int, fraction: Double): Double {
        var distance = 0.0
        for (i in 0 until segment) distance += haversine(route[i], route[i + 1])
        return distance + haversine(route[segment], interpolate(route[segment], route[segment + 1], fraction))
    }

    private fun projectionFraction(a: GeoPoint, b: GeoPoint, p: GeoPoint): Double {
        val meanLat = Math.toRadians((a.latitude + b.latitude + p.latitude) / 3.0)
        val ax = a.longitude * cos(meanLat)
        val ay = a.latitude
        val bx = b.longitude * cos(meanLat)
        val by = b.latitude
        val px = p.longitude * cos(meanLat)
        val py = p.latitude
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) return 0.0
        return (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
    }

    private fun interpolate(a: GeoPoint, b: GeoPoint, fraction: Double) = GeoPoint(
        a.latitude + (b.latitude - a.latitude) * fraction,
        a.longitude + (b.longitude - a.longitude) * fraction
    )

    private fun haversine(a: GeoPoint, b: GeoPoint): Double {
        val radius = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return radius * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}
