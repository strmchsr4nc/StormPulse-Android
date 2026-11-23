package com.carolinasvrwx.stormpulse

/**
 * Small process-local bridge between the phone navigation activity and the
 * Android Auto CarApp session. It carries only navigation presentation state;
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
    @Volatile private var snapshot = CarNavigationSnapshot()
    @Volatile private var listener: (() -> Unit)? = null

    fun current(): CarNavigationSnapshot = snapshot

    fun setListener(callback: (() -> Unit)?) {
        listener = callback
        callback?.invoke()
    }

    fun update(
        route: PlannedRoute? = snapshot.route,
        location: GeoPoint? = snapshot.location,
        nextInstruction: String? = snapshot.nextInstruction,
        distanceToManeuverMeters: Double? = snapshot.distanceToManeuverMeters,
        active: Boolean = snapshot.active
    ) {
        snapshot = CarNavigationSnapshot(
            route = route,
            location = location,
            nextInstruction = nextInstruction,
            distanceToManeuverMeters = distanceToManeuverMeters,
            active = active
        )
        listener?.invoke()
    }

    fun clear() {
        snapshot = CarNavigationSnapshot()
        listener?.invoke()
    }
}
