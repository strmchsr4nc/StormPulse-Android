package com.carolinasvrwx.stormpulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAlertAnnouncementPolicyTest {
    private fun alert(
        id: String,
        kind: NwsAlertKind = NwsAlertKind.WARNING,
        status: RouteAlertStatus = RouteAlertStatus.AHEAD,
        message: String = "$id message"
    ) = RouteWeatherAlert(
        hazard = WeatherHazard(
            id = id,
            event = "Tornado Warning",
            severity = HazardSeverity.EXTREME,
            polygon = listOf(
                GeoPoint(35.0, -80.0), GeoPoint(35.1, -80.0),
                GeoPoint(35.1, -79.9), GeoPoint(35.0, -79.9)
            )
        ),
        kind = kind,
        status = status,
        distanceFromRouteStartMeters = 5000.0,
        distanceAheadMeters = 5000.0,
        spokenMessage = message
    )

    private fun assessment(vararg alerts: RouteWeatherAlert, stale: Boolean = false) =
        RouteWeatherAssessment(alerts.toList(), 10_000L, stale)

    @Test
    fun announcesAheadOnlyOnceAndAnnouncesAgainWhenRouteEntersHazard() {
        val policy = RouteAlertAnnouncementPolicy()
        val ahead = alert("nws-1")
        assertEquals(listOf("nws-1 message"), policy.messagesFor(assessment(ahead)))
        assertTrue(policy.messagesFor(assessment(ahead)).isEmpty())

        val onRoute = alert("nws-1", status = RouteAlertStatus.ON_ROUTE, message = "Now on route")
        assertEquals(listOf("Now on route"), policy.messagesFor(assessment(onRoute)))
    }

    @Test
    fun staleAssessmentsAreNeverSpoken() {
        val policy = RouteAlertAnnouncementPolicy()
        assertTrue(policy.messagesFor(assessment(alert("old"), stale = true)).isEmpty())
        assertEquals(listOf("old message"), policy.messagesFor(assessment(alert("old"))))
    }

    @Test
    fun warningAndWatchAreTrackedSeparatelyAndOutputIsCapped() {
        val policy = RouteAlertAnnouncementPolicy(maxAnnouncementsPerAssessment = 2)
        val messages = policy.messagesFor(
            assessment(
                alert("warning", NwsAlertKind.WARNING),
                alert("watch", NwsAlertKind.WATCH),
                alert("advisory", NwsAlertKind.ADVISORY)
            )
        )
        assertEquals(listOf("warning message", "watch message"), messages)
        assertTrue(policy.messagesFor(assessment(alert("warning"), alert("watch"))).isEmpty())
    }

    @Test
    fun resetAllowsCurrentRouteAlertsToBeAnnouncedAgain() {
        val policy = RouteAlertAnnouncementPolicy()
        val current = assessment(alert("same-id"))
        assertEquals(1, policy.messagesFor(current).size)
        policy.reset()
        assertEquals(1, policy.messagesFor(current).size)
    }
}
