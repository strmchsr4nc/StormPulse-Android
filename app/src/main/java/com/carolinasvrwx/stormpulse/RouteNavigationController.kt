package com.carolinasvrwx.stormpulse

import java.io.Closeable
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Injectable address-search contract used by the navigation workflow. */
fun interface DestinationSearchProvider {
    fun search(query: String): List<GeocodedAddress>
}

/**
 * Coordinates destination lookup and route calculation without blocking the UI.
 *
 * UI code calls searchDestination() after an explicit user action, displays the
 * returned choices, then calls calculateRoute() with the selected destination.
 * The origin is expected to come from a current Android GPS fix supplied by
 * the app's location layer.
 */
class RouteNavigationController(
    private val addressSearch: DestinationSearchProvider,
    private val routePlanner: RoutePlanningProvider,
    private val worker: Executor = Executors.newSingleThreadExecutor(),
    private val callbackExecutor: Executor = Executor { it.run() },
    private val ownsWorker: Boolean = worker is ExecutorService
) : Closeable {
    @Volatile private var closed = false

    fun searchDestination(
        query: String,
        onResult: (List<GeocodedAddress>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (closed) return
        if (query.isBlank()) {
            callbackExecutor.execute { if (!closed) onError(IllegalArgumentException("Enter a destination address.")) }
            return
        }
        worker.execute {
            try {
                val results = addressSearch.search(query.trim())
                callbackExecutor.execute { if (!closed) onResult(results) }
            } catch (error: Exception) {
                callbackExecutor.execute { if (!closed) onError(error) }
            }
        }
    }

    fun calculateRoute(
        origin: GeoPoint,
        destination: RouteDestination,
        onResult: (PlannedRoute) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (closed) return
        worker.execute {
            try {
                val route = routePlanner.calculateRoute(origin, destination)
                callbackExecutor.execute { if (!closed) onResult(route) }
            } catch (error: Exception) {
                callbackExecutor.execute { if (!closed) onError(error) }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (ownsWorker && worker is ExecutorService) worker.shutdownNow()
    }
}
