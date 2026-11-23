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
                    ?.let { index to it }
            } ?: return@mapNotNull null
            Triple(RouteHazardMatch(hazard, encounter.first), encounter.first, encounter.second)
        }.sortedWith(compareBy<Triple<RouteHazardMatch, Int, Double>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    /** Returns the earliest fraction (0..1) along a segment that touches a polygon. */
    private fun firstIntersectionFraction(
        start: GeoPoint,
        end: GeoPoint,
        polygon: List<GeoPoint>
    ): Double? {
        if (pointInPolygon(start, polygon)) return 0.0
        val dx = end.longitude - start.longitude
        val dy = end.latitude - start.latitude
        val fractions = polygon.indices.mapNotNull { index ->
            val a = polygon[index]
            val b = polygon[(index + 1) % polygon.size]
            val ex = b.longitude - a.longitude
            val ey = b.latitude - a.latitude
            val denominator = dx * ey - dy * ex
            if (kotlin.math.abs(denominator) < 1e-12) {
                if (segmentsIntersect(start, end, a, b)) {
                    listOfNotNull(
                        fractionAlong(start, end, a),
                        fractionAlong(start, end, b)
                    ).filter { it in 0.0..1.0 }.minOrNull()
                } else null
            } else {
                val t = ((a.longitude - start.longitude) * ey -
                    (a.latitude - start.latitude) * ex) / denominator
                val u = ((a.longitude - start.longitude) * dy -
                    (a.latitude - start.latitude) * dx) / denominator
                if (t in 0.0..1.0 && u in 0.0..1.0) t else null
            }
        }
        return fractions.minOrNull()
            ?: if (pointInPolygon(end, polygon)) 1.0 else null
    }

    private fun fractionAlong(start: GeoPoint, end: GeoPoint, point: GeoPoint): Double {
        val dx = end.longitude - start.longitude
        val dy = end.latitude - start.latitude
        return if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
            if (kotlin.math.abs(dx) < 1e-12) 0.0 else (point.longitude - start.longitude) / dx
        } else {
            if (kotlin.math.abs(dy) < 1e-12) 0.0 else (point.latitude - start.latitude) / dy
        }
    }

