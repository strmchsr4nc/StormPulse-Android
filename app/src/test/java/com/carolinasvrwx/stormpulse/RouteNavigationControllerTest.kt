package com.carolinasvrwx.stormpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class RouteNavigationControllerTest {
    private val direct = Executor { it.run() }

    @Test
    fun searchReturnsProviderResults() {
        val expected = listOf(GeocodedAddress("Roxboro, NC", GeoPoint(36.393, -78.982)))
        val controller = RouteNavigationController(
            addressSearch = DestinationSearchProvider { expected },
            routePlanner = object : RoutePlanningProvider {
                override fun calculateRoute(origin: GeoPoint, destination: RouteDestination) =
                    PlannedRoute(destination, listOf(GeoPoint(1.0, 1.0), GeoPoint(2.0, 2.0)), 1000.0, 100.0, emptyList())
            },
            worker = direct,
            callbackExecutor = direct,
            ownsWorker = false
        )
        var actual: List<GeocodedAddress>? = null
        controller.searchDestination("Roxboro, NC", { actual = it }, { throw it })
        assertEquals(expected, actual)
        controller.close()
    }

    @Test
    fun calculatesRouteFromSelectedDestination() {
        val origin = GeoPoint(36.0, -79.0)
        val destination = RouteDestination("Destination", GeoPoint(36.1, -78.9))
        val expected = PlannedRoute(destination, listOf(origin, destination.point), 15000.0, 900.0, emptyList())
        val controller = RouteNavigationController(
            addressSearch = DestinationSearchProvider { emptyList() },
            routePlanner = object : RoutePlanningProvider {
                override fun calculateRoute(origin: GeoPoint, destination: RouteDestination) = expected
            },
            worker = direct,
            callbackExecutor = direct,
            ownsWorker = false
        )
        var actual: PlannedRoute? = null
        controller.calculateRoute(origin, destination, { actual = it }, { throw it })
        assertEquals(expected, actual)
        controller.close()
    }

    @Test
    fun rejectsBlankSearch() {
        val controller = RouteNavigationController(
            DestinationSearchProvider { emptyList() },
            object : RoutePlanningProvider {
                override fun calculateRoute(origin: GeoPoint, destination: RouteDestination): PlannedRoute =
                    throw AssertionError("Must not route")
            },
            worker = direct,
            callbackExecutor = direct,
            ownsWorker = false
        )
        var failed = false
        controller.searchDestination("  ", {}, { failed = it is IllegalArgumentException })
        assertTrue(failed)
        controller.close()
    }

    @Test
    fun closedControllerDoesNotDeliverResults() {
        val controller = RouteNavigationController(
            DestinationSearchProvider { emptyList() },
            RoutePlanningProvider { _, _ -> throw AssertionError("Must not route") },
            worker = direct,
            callbackExecutor = direct,
            ownsWorker = false
        )
        controller.close()
        var called = false
        controller.searchDestination("Roxboro", { called = true }, { called = true })
        assertFalse(called)
    }
}
