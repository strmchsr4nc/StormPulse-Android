package com.carolinasvrwx.stormpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RoutePlanningTest {
    private val destination = RouteDestination(
        label = "Roxboro, NC",
        point = GeoPoint(36.3937, -78.9828)
    )

    @Test
    fun acceptsValidRouteGeometryAndMetrics() {
        val route = PlannedRoute(
            destination = destination,
            geometry = listOf(GeoPoint(36.0, -79.0), destination.point),
            distanceMeters = 12_500.0,
            durationSeconds = 900.0,
            maneuvers = listOf(
                RouteManeuver("Continue on Main Street", 500.0, GeoPoint(36.1, -79.0))
            )
        )

        assertEquals(12_500.0, route.distanceMeters, 0.0)
        assertEquals(2, route.geometry.size)
    }

    @Test
    fun rejectsRouteWithoutEnoughGeometryPoints() {
        assertThrows(IllegalArgumentException::class.java) {
            PlannedRoute(destination, listOf(destination.point), 100.0, 60.0, emptyList())
        }
    }

    @Test
    fun rejectsInvalidDestinationCoordinates() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteDestination("Bad destination", GeoPoint(100.0, 0.0))
        }
    }

    @Test
    fun rejectsNegativeManeuverDistance() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteManeuver("Turn left", -1.0, destination.point)
        }
    }
}
