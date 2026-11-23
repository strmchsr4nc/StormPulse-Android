package com.carolinasvrwx.stormpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteHazardMatcherTest {
    private val square = listOf(
        GeoPoint(35.0, -79.0),
        GeoPoint(36.0, -79.0),
        GeoPoint(36.0, -78.0),
        GeoPoint(35.0, -78.0)
    )
    private val warning = WeatherHazard(
        id = "test-warning",
        event = "Tornado Warning",
        severity = HazardSeverity.EXTREME,
        polygon = square
    )

    @Test fun detectsRouteCrossingPolygonBetweenOutsideEndpoints() {
        val matches = RouteHazardMatcher.match(
            listOf(GeoPoint(35.5, -79.5), GeoPoint(35.5, -77.5)),
            listOf(warning)
        )
        assertEquals(1, matches.size)
        assertEquals(0, matches.single().firstIntersectingSegment)
    }

    @Test fun ignoresRouteThatMissesPolygon() {
        val matches = RouteHazardMatcher.match(
            listOf(GeoPoint(34.0, -79.5), GeoPoint(34.0, -77.5)),
            listOf(warning)
        )
        assertTrue(matches.isEmpty())
    }

    @Test fun excludesExpiredHazards() {
        val expired = warning.copy(expiresAtEpochMillis = 1_000L)
        val matches = RouteHazardMatcher.match(
            listOf(GeoPoint(35.5, -79.5), GeoPoint(35.5, -77.5)),
            listOf(expired),
            nowEpochMillis = 1_000L
        )
        assertTrue(matches.isEmpty())
    }

    @Test fun sortsHazardsByFirstRouteEncounter() {
        val later = warning.copy(id = "later", polygon = square.map {
            GeoPoint(it.latitude, it.longitude + 2.0)
        })
        val earlier = warning.copy(id = "earlier")
        val route = listOf(GeoPoint(35.5, -80.0), GeoPoint(35.5, -75.0))
        val matches = RouteHazardMatcher.match(route, listOf(later, earlier))
        assertEquals(listOf("earlier", "later"), matches.map { it.hazard.id })
    }
}
