package com.carolinasvrwx.stormpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteWeatherMonitorTest {
    private val route = listOf(GeoPoint(35.5, -80.0), GeoPoint(35.5, -77.0))

    private fun hazard(id: String, event: String, west: Double, east: Double) = WeatherHazard(
        id = id,
        event = event,
        severity = HazardSeverity.EXTREME,
        polygon = listOf(
            GeoPoint(35.2, west), GeoPoint(35.8, west),
            GeoPoint(35.8, east), GeoPoint(35.2, east)
        )
    )

    @Test
    fun identifiesWarningAheadAndCreatesSpokenGuidance() {
        val assessment = RouteWeatherMonitor().assess(
            route = route,
            hazards = listOf(hazard("warning", "Tornado Warning", -79.8, -79.6)),
            currentLocation = route.first(),
            checkedAtEpochMillis = 10_000L,
            dataReceivedAtEpochMillis = 9_000L
        )

        assertEquals(1, assessment.alerts.size)
        assertEquals(NwsAlertKind.WARNING, assessment.alerts.single().kind)
        assertEquals(RouteAlertStatus.AHEAD, assessment.alerts.single().status)
        assertTrue(assessment.alerts.single().spokenMessage.contains("Tornado Warning"))
        assertFalse(assessment.dataIsStale)
    }

    @Test
    fun warningIsPrioritizedAheadOfWatch() {
        val assessment = RouteWeatherMonitor().assess(
            route = route,
            hazards = listOf(
                hazard("watch", "Severe Thunderstorm Watch", -79.8, -79.6),
                hazard("warning", "Tornado Warning", -79.75, -79.65)
            ),
            currentLocation = route.first(),
            checkedAtEpochMillis = 20_000L,
            dataReceivedAtEpochMillis = 19_000L
        )

        assertEquals(
            listOf(NwsAlertKind.WARNING, NwsAlertKind.WATCH),
            assessment.alerts.map { it.kind }
        )
    }

    @Test
    fun marksDataStaleAndDetectsWarningAlreadyAtCurrentLocation() {
        val current = GeoPoint(35.5, -79.7)
        val assessment = RouteWeatherMonitor().assess(
            route = route,
            hazards = listOf(hazard("warning", "Flash Flood Warning", -79.8, -79.6)),
            currentLocation = current,
            checkedAtEpochMillis = 2_000_000L,
            dataReceivedAtEpochMillis = 1L
        )

        assertTrue(assessment.dataIsStale)
        assertTrue(assessment.hasOnRouteWarning)
        assertEquals(RouteAlertStatus.ON_ROUTE, assessment.alerts.single().status)
    }

    @Test
    fun ignoresAlertsBeyondNearbyDistance() {
        val assessment = RouteWeatherMonitor().assess(
            route = route,
            hazards = listOf(hazard("warning", "Tornado Warning", -78.8, -78.6)),
            currentLocation = route.first(),
            checkedAtEpochMillis = 10_000L,
            dataReceivedAtEpochMillis = 10_000L
        )
        assertTrue(assessment.alerts.isEmpty())
    }
}
