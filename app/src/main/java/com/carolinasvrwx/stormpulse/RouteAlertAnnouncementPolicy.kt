package com.carolinasvrwx.stormpulse

/**
 * Decides which route-weather messages are new enough to speak.
 *
 * Ahead and on-route are separate stages: a hazard can be announced once
 * while approaching and once again when the route enters its polygon.
 * Stale assessments are never announced.
 */
class RouteAlertAnnouncementPolicy(
    private val maxAnnouncementsPerAssessment: Int = 2
) {
    private val announcedStages = mutableSetOf<String>()

    init {
        require(maxAnnouncementsPerAssessment >= 1)
    }

    @Synchronized
    fun messagesFor(assessment: RouteWeatherAssessment): List<String> {
        if (assessment.dataIsStale) return emptyList()

        val selected = mutableListOf<String>()
        for (alert in assessment.alerts) {
            val stage = when (alert.status) {
                RouteAlertStatus.AHEAD -> "ahead"
                RouteAlertStatus.ON_ROUTE -> "on-route"
                RouteAlertStatus.NEARBY -> "nearby"
            }
            val key = "${alert.hazard.id}|${alert.kind}|$stage"
            if (announcedStages.add(key)) {
                selected += alert.spokenMessage
                if (selected.size >= maxAnnouncementsPerAssessment) break
            }
        }
        return selected
    }

    /** Call when the active route is replaced or navigation ends. */
    @Synchronized
    fun reset() {
        announcedStages.clear()
    }
}
