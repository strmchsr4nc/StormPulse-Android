package com.carolinasvrwx.stormpulse

import android.content.Context

/**
 * Joins route-weather assessments to the announcement policy and Android TTS.
 *
 * Feed each fresh RouteWeatherAssessment from RouteWeatherCoordinator into
 * announce(). Call resetRoute() when navigation starts a different route.
 */
class RouteWeatherAlertAnnouncer(
    context: Context,
    private val policy: RouteAlertAnnouncementPolicy = RouteAlertAnnouncementPolicy(),
    onSpeechReady: ((Boolean) -> Unit)? = null
) {
    private val speaker = RouteAlertSpeaker(context, onSpeechReady)

    fun announce(assessment: RouteWeatherAssessment) {
        speaker.speak(policy.messagesFor(assessment))
    }

    fun resetRoute() {
        policy.reset()
    }

    fun close() {
        speaker.close()
    }
}
