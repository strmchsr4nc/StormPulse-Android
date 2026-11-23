package com.carolinasvrwx.stormpulse

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Lifecycle owner that connects a supplied route and incoming GPS fixes to
 * periodic NWS assessment and spoken route alerts.
 *
 * A navigation/location layer supplies route geometry and calls
 * updateLocation() as GPS fixes arrive. This class does not request location
 * permission or calculate routes itself.
 */
class RouteWeatherNavigationSession(
    context: Context,
    route: List<GeoPoint>,
    private val coordinator: RouteWeatherCoordinator = RouteWeatherCoordinator(),
    private val announcer: RouteWeatherAlertAnnouncer = RouteWeatherAlertAnnouncer(context),
    private val refreshIntervalMillis: Long = DEFAULT_REFRESH_INTERVAL_MILLIS,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val onAssessment: ((RouteWeatherAssessment) -> Unit)? = null,
    private val onError: ((Exception) -> Unit)? = null
) {
    companion object {
        const val DEFAULT_REFRESH_INTERVAL_MILLIS = 5 * 60 * 1000L
    }

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val routeSnapshot = route.toList()
    @Volatile private var latestLocation: GeoPoint? = null
    @Volatile private var active = false
    @Volatile private var closed = false

    init {
        require(routeSnapshot.size >= 2) { "A navigation route needs at least two points." }
        require(refreshIntervalMillis >= 30_000L) { "Weather refresh interval must be at least 30 seconds." }
    }

    /** Begin periodic monitoring. The first assessment runs as soon as a location is available. */
    @Synchronized
    fun start() {
        if (closed || active) return
        active = true
        scheduler.scheduleWithFixedDelay(
            { refreshIfReady() },
            0L,
            refreshIntervalMillis,
            TimeUnit.MILLISECONDS
        )
    }

    /** Supply the latest GPS fix from the app's location provider. */
    fun updateLocation(location: GeoPoint) {
        if (active) latestLocation = location
    }

    private fun refreshIfReady() {
        if (!active) return
        val location = latestLocation ?: return
        coordinator.refresh(
            route = routeSnapshot,
            currentLocation = location,
            onSuccess = { assessment ->
                if (active) {
                    announcer.announce(assessment)
                    mainHandler.post {
                        if (active) onAssessment?.invoke(assessment)
                    }
                }
            },
            onError = { exception ->
                if (active) mainHandler.post {
                    if (active) onError?.invoke(exception)
                }
            }
        )
    }

    /** Stop monitoring and release the speech engine. This session cannot be restarted. */
    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        active = false
        scheduler.shutdownNow()
        announcer.close()
        coordinator.close()
    }
}
