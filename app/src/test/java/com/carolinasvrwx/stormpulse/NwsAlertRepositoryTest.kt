package com.carolinasvrwx.stormpulse

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NwsAlertRepositoryTest {
    private val repository = NwsAlertRepository()

    @Test
    fun parsesPolygonCoordinatesAndAlertMetadata() {
        val json = """
            {
              "features": [{
                "id": "https://api.weather.gov/alerts/TEST1",
                "properties": {
                  "event": "Tornado Warning",
                  "severity": "Extreme",
                  "expires": "2026-11-23T19:00:00Z"
                },
                "geometry": {
                  "type": "Polygon",
                  "coordinates": [[
                    [-79.0, 35.0],
                    [-79.0, 36.0],
                    [-78.0, 36.0],
                    [-78.0, 35.0],
                    [-79.0, 35.0]
                  ]]
                }
              }]
            }
        """.trimIndent()

        val hazards = repository.parseFeatureCollection(JSONObject(json))

        assertEquals(1, hazards.size)
        assertEquals("Tornado Warning", hazards.single().event)
        assertEquals(HazardSeverity.EXTREME, hazards.single().severity)
        assertEquals(4, hazards.single().polygon.size)
        assertEquals(35.0, hazards.single().polygon.first().latitude, 0.0)
        assertEquals(-79.0, hazards.single().polygon.first().longitude, 0.0)
        assertTrue(hazards.single().expiresAtEpochMillis != null)
    }

    @Test
    fun splitsMultiPolygonIntoIndividuallyMatchableAreas() {
        val json = """
            {
              "features": [{
                "id": "multi-alert",
                "properties": {"event": "Flood Watch", "severity": "Moderate"},
                "geometry": {
                  "type": "MultiPolygon",
                  "coordinates": [
                    [[[-80.0, 35.0], [-80.0, 36.0], [-79.0, 36.0], [-79.0, 35.0], [-80.0, 35.0]]],
                    [[[-78.0, 35.0], [-78.0, 36.0], [-77.0, 36.0], [-77.0, 35.0], [-78.0, 35.0]]]
                  ]
                }
              }]
            }
        """.trimIndent()

        val hazards = repository.parseFeatureCollection(JSONObject(json))

        assertEquals(2, hazards.size)
        assertEquals("multi-alert", hazards[0].id)
        assertEquals("multi-alert-1", hazards[1].id)
        assertTrue(hazards.all { it.severity == HazardSeverity.MODERATE })
    }

    @Test
    fun ignoresAlertsWithoutSupportedPolygonGeometry() {
        val json = """
            {
              "features": [{
                "id": "point-alert",
                "properties": {"event": "Special Weather Statement"},
                "geometry": null
              }]
            }
        """.trimIndent()

        assertTrue(repository.parseFeatureCollection(JSONObject(json)).isEmpty())
    }
}
