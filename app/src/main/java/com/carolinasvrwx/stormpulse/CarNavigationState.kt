package com.carolinasvrwx.stormpulse

import android.os.Handler
import android.os.Looper

/**
 * Process-local presentation bridge between phone navigation and Android Auto.
 * GPS ownership and route calculation remain in NavigationActivity.
 */
data class CarNavigationSnapshot(
    val route: PlannedRoute? = null,
    val location: GeoPoint? = null,
    val nextInstruction: String? = null,
    val distanceToManeuverMeters: Double? = null,
    val active: Boolean = false
)

object CarNavigationState {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var snapshot = CarNavigationSnapshot()
    @Volatile private var listener: (() -> Unit)? = null\n    @Volatile private var carHostActive: Boolean = false

    fun current(): CarNavigationSnapshot = snapshot\n\n    fun isCarHostActive(): Boolean = carHostActive\n\n    fun setCarHostActive(active: Boolean) {\n        carHostActive = active\n    }

    fun setListener(callback: (() -> Unit)?) {
        listener = callback
        dispatch(callback)
    }

    fun update(
        route: PlannedRoute? = snapshot.route,
        location: GeoPoint? = snapshot.location,
        nextInstruction: String? = snapshot.nextInstruction,
        distanceToManeuverMeters: Double? = snapshot.distanceToManeuverMeters,
        active: Boolean = snapshot.active
    ) {
        snapshot = CarNavigationSnapshot(route, location, nextInstruction, distanceToManeuverMeters, active)
        dispatch(listener)
    }

    fun clear() {
        snapshot = CarNavigationSnapshot()
        dispatch(listener)
    }

    private fun dispatch(callback: (() -> Unit)?) {
        if (callback == null) return
        if (Looper.myLooper() == Looper.getMainLooper()) callback()
        else mainHandler.post { if (listener === callback) callback() }
    }
}
