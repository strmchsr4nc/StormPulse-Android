package com.carolinasvrwx.stormpulse

/**
 * Geometry primitives for matching an active route against NWS alert polygons.
 * Coordinates are latitude/longitude degrees. Segment/polygon edge checks ensure
 * a route crossing is detected even when both segment endpoints are outside.
 */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
    }
}

enum class HazardSeverity { EXTREME, SEVERE, MODERATE, MINOR, UNKNOWN }

data class WeatherHazard(
    val id: String,
    val event: String,
    val severity: HazardSeverity,
    val polygon: List<GeoPoint>,
    val expiresAtEpochMillis: Long? = null
) {
    init {
        require(id.isNotBlank())
        require(polygon.size >= 3) { "A hazard polygon needs at least three points" }
    }
}

data class RouteHazardMatch(
    val hazard: WeatherHazard,
    val firstIntersectingSegment: Int,
    val firstEncounterFraction: Double
)

object RouteHazardMatcher {
    /** Returns intersecting hazards in the order a driver first encounters them. */
    fun match(
        route: List<GeoPoint>,
        hazards: List<WeatherHazard>,
        nowEpochMillis: Long? = null
    ): List<RouteHazardMatch> {
        if (route.size < 2) return emptyList()

        return hazards.mapNotNull { hazard ->
            if (nowEpochMillis != null &&
                hazard.expiresAtEpochMillis != null &&
                hazard.expiresAtEpochMillis <= nowEpochMillis
            ) return@mapNotNull null

            var first: RouteHazardMatch? = null
            for (segmentIndex in 0 until route.lastIndex) {
                val fraction = firstIntersectionFraction(
                    route[segmentIndex], route[segmentIndex + 1], hazard.polygon
                )
                if (fraction != null) {
                    first = RouteHazardMatch(hazard, segmentIndex, fraction)
                    break
                }
            }
            first
        }.sortedWith(
            compareBy<RouteHazardMatch> { it.firstIntersectingSegment }
                .thenBy { it.firstEncounterFraction }
        )
    }

    /** Earliest fraction (0..1) along a segment that touches the polygon. */
    private fun firstIntersectionFraction(
        start: GeoPoint,
        end: GeoPoint,
        polygon: List<GeoPoint>
    ): Double? {
        if (pointInPolygon(start, polygon)) return 0.0

        val dx = end.longitude - start.longitude
        val dy = end.latitude - start.latitude
        val hits = mutableListOf<Double>()

        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val ex = b.longitude - a.longitude
            val ey = b.latitude - a.latitude
            val denominator = dx * ey - dy * ex
            val qx = a.longitude - start.longitude
            val qy = a.latitude - start.latitude

            if (kotlin.math.abs(denominator) < 1e-12) {
                // Parallel edges can still overlap collinearly.
                if (kotlin.math.abs(qx * dy - qy * dx) < 1e-10) {
                    fractionAlong(start, end, a)?.let { if (it in 0.0..1.0) hits.add(it) }
                    fractionAlong(start, end, b)?.let { if (it in 0.0..1.0) hits.add(it) }
                }
            } else {
                val t = (qx * ey - qy * ex) / denominator
                val u = (qx * dy - qy * dx) / denominator
                if (t in 0.0..1.0 && u in 0.0..1.0) hits.add(t)
            }
        }

        return hits.minOrNull() ?: if (pointInPolygon(end, polygon)) 1.0 else null
    }

    private fun fractionAlong(start: GeoPoint, end: GeoPoint, point: GeoPoint): Double? {
        val dx = end.longitude - start.longitude
        val dy = end.latitude - start.latitude
        return when {
            kotlin.math.abs(dx) >= kotlin.math.abs(dy) && kotlin.math.abs(dx) > 1e-12 ->
                (point.longitude - start.longitude) / dx
            kotlin.math.abs(dy) > 1e-12 -> (point.latitude - start.latitude) / dy
            else -> null
        }
    }

    private fun pointInPolygon(point: GeoPoint, polygon: List<GeoPoint>): Boolean {
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            if (onSegment(previous, current, point)) return true
            val crosses = (current.latitude > point.latitude) !=
                (previous.latitude > point.latitude)
            if (crosses) {
                val crossingLongitude = (previous.longitude - current.longitude) *
                    (point.latitude - current.latitude) /
                    (previous.latitude - current.latitude) + current.longitude
                if (point.longitude < crossingLongitude) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun onSegment(a: GeoPoint, b: GeoPoint, p: GeoPoint): Boolean =
        p.longitude >= minOf(a.longitude, b.longitude) - 1e-10 &&
            p.longitude <= maxOf(a.longitude, b.longitude) + 1e-10 &&
            p.latitude >= minOf(a.latitude, b.latitude) - 1e-10 &&
            p.latitude <= maxOf(a.latitude, b.latitude) + 1e-10 &&
            kotlin.math.abs(
                (b.longitude - a.longitude) * (p.latitude - a.latitude) -
                    (b.latitude - a.latitude) * (p.longitude - a.longitude)
            ) < 1e-10
}
