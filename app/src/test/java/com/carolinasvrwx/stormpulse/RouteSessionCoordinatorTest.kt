package com.carolinasvrwx.stormpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class RouteSessionCoordinatorTest {
    private val direct = Executor { it.run() }

    @Test
    fun calculatesInitialRouteFromLatestGpsFix() {
        val origin = GeoPoint(36.0, -79.0)
        val destination = RouteDestination("Destination", GeoPoint(36.1, -78.9))
        var routeOrigin: GeoPoint? = null
        val controller = RouteNavigationController(
            DestinationSearchProvider { emptyList() },
            object : RoutePlanningProvider {
                override fun calculateRoute(origin: GeoPoint, destination: RouteDestination): PlannedRoute {
                    routeOrigin = origin
                    return PlannedRoute(destination, listOf(origin, destination.point), 10_000.0, 600.0, emptyList())
                }
            },
            worker = direct,
            callbackExecutor = direct,
            ownsWorker = false
        )
        var delivered: PlannedRoute? = null
        val session = RouteSessionCoordinator(controller, object : RouteSessionCoordinator.Listener {
            override fun onRoute(route: PlannedRoute, isReroute: Boolean) { delivered = route }
        })
        session.onLocationFix(origin, 4f)
        session.navigateTo(destination)

        assertEquals(origin, routeOrigin)
        assertNotNull(delivered)
        assertEquals(destination, session.currentRoute()?.destination)
        session.close()
        controller.close()
    }

    @Test
    fun reroutesWhenGpsMovesBeyondThresholdAndRespectsCooldown() {
        val start = GeoPoint(36.0, -79.0)
        val destination = RouteDestination("Destination", GeoPoint(36.1, -78.9))
        var routeCalls = 0
        val controller = RouteNavigationController(
            DestinationSearchProvider { emptyList() },
            object : RoutePlanningProvider {
                override fun calculateRoute(origin: GeoPoint, destination: RouteDestination): PlannedRoute {
                    routeCalls++
                    return PlannedRoute(
                        destination,
                        listOf(GeoPoint(36.0, -79.0), GeoPoint(36.0, -78.99)),
                        10_000.0, 600.0, emptyList()
                    )
                }
            },
            worker = direct,
            callbackExecutor = direct,
            ownsWorker = false
        )
        var reroutes = 0
        var now = 100_000L
        val session = RouteSessionCoordinator(
            controller,
            object : RouteSessionCoordinator.Listener {
                override fun onRoute(route: PlannedRoute, isReroute: Boolean) { if (isReroute) reroutes++ }
            },
            clock = { now }
        )
        session.onLocationFix(start)
        session.navigateTo(destination)
        val farFromRoute = GeoPoint(36.002, -78.995)
        session.onLocationFix(farFromRoute)
        assertEquals(2, routeCalls)
        assertEquals(1, reroutes)

        session.onLocationFix(GeoPoint(36.003, -78.995))
        assertEquals("Cooldown should prevent another immediate request", 2, routeCalls)
        now += 31_000L
        session.onLocationFix(GeoPoint(36.004, -78.995))
        assertEquals(3, routeCalls)
        session.close()
        controller.close()
    }

    @Test
    fun distanceToPolylineIsNearZeroOnLineAndPositiveOffLine() {
        val line = listOf(GeoPoint(36.0, -79.0), GeoPoint(36.0, -78.99))
        val onLine = RouteSessionCoordinator.distanceToPolylineMeters(GeoPoint(36.0, -78.995), line)
        val offLine = RouteSessionCoordinator.distanceToPolylineMeters(GeoPoint(36.002, -78.995), line)
        assertTrue(onLine < 1.0)
        assertTrue(offLine > 200.0)
    }
}
