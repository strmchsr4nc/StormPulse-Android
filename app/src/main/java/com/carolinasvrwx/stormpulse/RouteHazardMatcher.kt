package com.carolinasvrwx.stormpulse

/**
 * Geometry primitives for matching an active route against NWS alert polygons.
 *
 * Coordinates use latitude/longitude degrees. This deliberately performs
 * polygon intersection, not just point sampling, so a route segment crossing
 * a warning polygon is detected even when neither endpoint is inside it.
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
    /** Index of the first route segment intersecting the polygon. */
    val firstIntersectingSegment: Int,
    /** Fraction from 0.0 to 1.0 along that segment at first contact. */
    val firstEncounterFraction: Double
)

object RouteHazardMatcher {
    /**
     * Returns each intersecting hazard once, ordered by its first encounter
     * along the route. Expired alerts are excluded when [nowEpochMillis] is supplied.
     */
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

            val encounter = (0 until route.lastIndex).firstNotNullOfOrNull { index ->
                firstIntersectionFraction(route[index], route[index + 1], hazard.polygon)
                    ?.let { fraction -> index to fraction }
            } ?: return@mapNotNull null
            RouteHazardMatch(hazard, encounter.first, encounter.second)
        }.sortedWith(
            compareBy<RouteHazardMatch> { it.firstIntersectingSegment }
                .thenBy { it.firstEncounterFraction }
        )
    }

    private fun firstIntersectionFraction(
        start: GeoPoint,
        end: GeoPoint,
        polygon: List<GeoPoint>
    ): Double? {
        if (pointInPolygon(start, polygon)) return 0.0
        val dx = end.longitude - start.longitude
        val dy = end.latitude - start.latitude
        var earliest: Double? = null
        polygon.indices.forEach { index ->
            val a = polygon[index]
            val b = polygon[(index + 1) % polygon.size]
            val ex = b.longitude - a.longitude
            val ey = b.latitude - a.latitude
            val denominator = dx * ey - dy * ex
            if (kotlin.math.abs(denominator) > 1e-12) {
                val ax = a.longitude - start.longitude
                val ay = a.latitude - start.latitude
                val t = (ax * ey - ay * ex) / denominator
                val u = (ax * dy - ay * dx) / denominator
                if (t in 0.0..1.0 && u in 0.0..1.0) {
                    earliest = minOf(earliest ?: t, t)
                }
            } else if (orientation(start, end, a) == 0) {
                // Collinear overlap: project polygon edge endpoints onto the route.
                val lengthSquared = dx * dx + dy * dy
                if (lengthSquared > 0.0) {
                    listOf(a, b).forEach { point ->
                        val t = ((point.longitude - start.longitude) * dx +
                            (point.latitude - start.latitude) * dy) / lengthSquared
                        if (t in 0.0..1.0) earliest = minOf(earliest ?: t, t)
                    }
                }
            }
        }
        if (earliest != null) return earliest
        return if (pointInPolygon(end, polygon)) 1.0 else null
    }

    private fun pointInPolygon(point: GeoPoint, polygon: List<GeoPoint>): Boolean {
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            if (onSegment(previous, current, point)) return true
            val crossesLatitude = (current.latitude > point.latitude) !=
                (previous.latitude > point.latitude)
            if (crossesLatitude) {
                val crossingLongitude = (previous.longitude - current.longitude) *
                    (point.latitude - current.latitude) /
                    (previous.latitude - current.latitude) + current.longitude
                if (point.longitude < crossingLongitude) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun segmentsIntersect(a: GeoPoint, b: GeoPoint, c: GeoPoint, d: GeoPoint): Boolean {
        val o1 = orientation(a, b, c)
        val o2 = orientation(a, b, d)
        val o3 = orientation(c, d, a)
        val o4 = orientation(c, d, b)
        if (o1 == 0 && onSegment(a, b, c)) return true
        if (o2 == 0 && onSegment(a, b, d)) return true
        if (o3 == 0 && onSegment(c, d, a)) return true
        if (o4 == 0 && onSegment(c, d, b)) return true
        return o1 != o2 && o3 != o4
    }

    private fun orientation(a: GeoPoint, b: GeoPoint, c: GeoPoint): Int {
        val cross = (b.longitude - a.longitude) * (c.latitude - b.latitude) -
            (b.latitude - a.latitude) * (c.longitude - b.longitude)
        return when {
            kotlin.math.abs(cross) < 1e-10 -> 0
            cross > 0 -> 1
            else -> 2
        }
    }

    private fun onSegment(a: GeoPoint, b: GeoPoint, p: GeoPoint): Boolean =
        p.longitude <= maxOf(a.longitude, b.longitude) + 1e-10 &&
            p.longitude >= minOf(a.longitude, b.longitude) - 1e-10 &&
            p.latitude <= maxOf(a.latitude, b.latitude) + 1e-10 &&
            p.latitude >= minOf(a.latitude, b.latitude) - 1e-10 &&
            orientation(a, b, p) == 0
}
