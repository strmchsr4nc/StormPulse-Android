package com.carolinasvrwx.stormpulse

import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Connects NWS retrieval to route hazard assessment.
 *
 * Invoke refresh() when a route is active and whenever a fresh location fix
 * is available. Network work runs on the supplied background executor.
 * The callback is delivered on that same executor; UI callers must dispatch
 * to their main thread.
 */
class RouteWeatherCoordinator(
    private val repository: NwsAlertRepository = NwsAlertRepository(),
    private val monitor: RouteWeatherMonitor = RouteWeatherMonitor(),
    private val backgroundExecutor: Executor = Executors.newSingleThreadExecutor(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun refresh(
        route: List<GeoPoint>,
        currentLocation: GeoPoint,
        onSuccess: (RouteWeatherAssessment) -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Snapshot mutable caller input before crossing the thread boundary.
        val routeSnapshot = route.toList()
        backgroundExecutor.execute {
            try {
                val receivedAt = clock()
                val hazards = repository.fetchActiveAlerts()
                val checkedAt = clock()
                val assessment = monitor.assess(
                    route = routeSnapshot,
                    hazards = hazards,
                    currentLocation = currentLocation,
                    checkedAtEpochMillis = checkedAt,
                    dataReceivedAtEpochMillis = receivedAt
                )
                onSuccess(assessment)
            } catch (exception: Exception) {
                onError(exception)
            }
        }
    }
    
    /** Stops the default background executor when this coordinator is no longer used. */
    fun close() {
        (backgroundExecutor as? ExecutorService)?.shutdownNow()
    }
}
