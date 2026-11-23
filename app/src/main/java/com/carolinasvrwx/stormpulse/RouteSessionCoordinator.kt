package com.carolinasvrwx.stormpulse

import java.io.Closeable
import kotlin.math.*

/**
 * Connects foreground GPS fixes to route planning and throttled off-route recalculation.
 * Call onLocationFix from the location provider and supply a listener backed by the UI thread.
 */
class RouteSessionCoordinator(
    private val navigation: RouteNavigationController,
    private val listener: Listener,
    private val offRouteThresholdMeters: Double = 75.0,
    private val rerouteCooldownMillis: Long = 30_000L,
    private val clock: () -> Long = System::currentTimeMillis
) : Closeable {
    interface Listener {
        fun onLocation(point: GeoPoint, accuracyMeters: Float) {}
        fun onRoute(route: PlannedRoute, isReroute: Boolean) {}
        fun onRerouting(distanceOffRouteMeters: Double) {}
        fun onError(error: Exception) {}
    }

    private val lock = Any()
    private var latestFix: GeoPoint? = null
    private var latestAccuracy = Float.NaN
    private var destination: RouteDestination? = null
    private var activeRoute: PlannedRoute? = null
    private var requestInFlight = false
    private var lastRerouteAt = Long.MIN_VALUE
    private var generation = 0L
    private var closed = false

    init {
        require(offRouteThresholdMeters > 0.0 && offRouteThresholdMeters.isFinite())
        require(rerouteCooldownMillis >= 0L)
    }

    fun onLocationFix(point: GeoPoint, accuracyMeters: Float = Float.NaN) {
        val shouldReroute: Pair<RouteDestination, Double>?
        synchronized(lock) {
            if (closed) return
            latestFix = point
            latestAccuracy = accuracyMeters
            shouldReroute = rerouteCandidateLocked()
        }
        listener.onLocation(point, accuracyMeters)
        shouldReroute?.let { requestRoute(it.first, true, it.second) }
    }

    /** Starts or replaces navigation to a destination using the freshest GPS fix. */
    fun navigateTo(target: RouteDestination) {
        var origin: GeoPoint? = null
        var requestGeneration = 0L
        val hasStarted = synchronized(lock) {
            if (closed) return
            val fix = latestFix
            if (fix == null) {
                false
            } else {
                origin = fix
                destination = target
                activeRoute = null
                requestInFlight = true
                lastRerouteAt = Long.MIN_VALUE
                generation += 1
                requestGeneration = generation
                true
            }
        }
        if (!hasStarted) {
            listener.onError(IllegalStateException("Waiting for a GPS location before calculating the route."))
            return
        }
        calculate(origin!!, target, false, 0.0, requestGeneration)
    }

    fun currentRoute(): PlannedRoute? = synchronized(lock) { activeRoute }
    fun currentLocation(): GeoPoint? = synchronized(lock) { latestFix }

    fun stopNavigation() {
        synchronized(lock) {
            generation += 1
            destination = null
            activeRoute = null
            requestInFlight = false
            lastRerouteAt = Long.MIN_VALUE
        }
    }

    private fun rerouteCandidateLocked(): Pair<RouteDestination, Double>? {
        val route = activeRoute ?: return null
        val target = destination ?: return null
        if (requestInFlight) return null
        val fix = latestFix ?: return null
        val deviation = distanceToPolylineMeters(fix, route.geometry)
        if (deviation < offRouteThresholdMeters) return null
        val now = clock()
        if (lastRerouteAt != Long.MIN_VALUE && now - lastRerouteAt < rerouteCooldownMillis) return null
        requestInFlight = true
        lastRerouteAt = now
        return target to deviation
    }

    private fun requestRoute(target: RouteDestination, isReroute: Boolean, deviation: Double) {
        val origin: GeoPoint
        val requestGeneration: Long
        synchronized(lock) {
            if (closed) return
            origin = latestFix ?: run { requestInFlight = false; return }
            requestGeneration = generation
        }
        if (isReroute) listener.onRerouting(deviation)
        calculate(origin, target, isReroute, deviation, requestGeneration)
    }

    private fun calculate(
        origin: GeoPoint,
        target: RouteDestination,
        isReroute: Boolean,
        deviation: Double,
        requestGeneration: Long
    ) {
        navigation.calculateRoute(origin, target, { route ->
            val deliver = synchronized(lock) {
                if (closed || generation != requestGeneration) false
                else {
                    activeRoute = route
                    requestInFlight = false
                    true
                }
            }
            if (deliver) listener.onRoute(route, isReroute)
        }, { error ->
            val deliver = synchronized(lock) {
                if (generation != requestGeneration) false
                else { requestInFlight = false; true }
            }
            if (deliver) listener.onError(error)
        })
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            generation += 1
            destination = null
            activeRoute = null
            requestInFlight = false
        }
    }

    companion object {
        /** Minimum great-circle distance from a point to any route segment. */
        fun distanceToPolylineMeters(point: GeoPoint, line: List<GeoPoint>): Double {
            if (line.isEmpty()) return Double.POSITIVE_INFINITY
            if (line.size == 1) return haversineMeters(point, line[0])
            return (0 until line.lastIndex).minOf { index ->
                distanceToSegmentMeters(point, line[index], line[index + 1])
            }
        }

        private fun distanceToSegmentMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
            val lat0 = Math.toRadians(p.latitude)
            val scaleX = cos(lat0) * 6_371_000.0 * Math.PI / 180.0
            val scaleY = 6_371_000.0 * Math.PI / 180.0
            val ax = (a.longitude - p.longitude) * scaleX
            val ay = (a.latitude - p.latitude) * scaleY
            val bx = (b.longitude - p.longitude) * scaleX
            val by = (b.latitude - p.latitude) * scaleY
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else
                (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
            return hypot(ax + t * dx, ay + t * dy)
        }

        private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val dLat = lat2 - lat1
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
            return 2 * 6_371_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}
