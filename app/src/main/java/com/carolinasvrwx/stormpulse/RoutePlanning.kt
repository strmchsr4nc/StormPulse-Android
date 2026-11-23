package com.carolinasvrwx.stormpulse

/**
 * Provider-neutral route models. A real routing provider must supply these
 * values; this module deliberately does not fabricate routes or ETAs.
 */
data class RouteDestination(
    val label: String,
    val point: GeoPoint
) {
    init {
        require(label.isNotBlank()) { "Destination label cannot be blank." }
        require(point.latitude in -90.0..90.0) { "Destination latitude is invalid." }
        require(point.longitude in -180.0..180.0) { "Destination longitude is invalid." }
    }
}

data class RouteManeuver(
    val instruction: String,
    val distanceMeters: Double,
    val point: GeoPoint
) {
    init {
        require(instruction.isNotBlank()) { "Maneuver instruction cannot be blank." }
        require(distanceMeters >= 0.0 && distanceMeters.isFinite()) {
            "Maneuver distance must be finite and non-negative."
        }
    }
}

data class PlannedRoute(
    val destination: RouteDestination,
    val geometry: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val maneuvers: List<RouteManeuver>
) {
    init {
        require(geometry.size >= 2) { "A planned route needs at least two geometry points." }
        require(distanceMeters > 0.0 && distanceMeters.isFinite()) {
            "Route distance must be finite and positive."
        }
        require(durationSeconds > 0.0 && durationSeconds.isFinite()) {
            "Route duration must be finite and positive."
        }
    }
}

/**
 * Implement this contract with a licensed routing service (or a self-hosted
 * engine). Implementations must perform network work off the main thread.
 */
interface RoutePlanningProvider {
    fun calculateRoute(
        origin: GeoPoint,
        destination: RouteDestination
    ): PlannedRoute
}
