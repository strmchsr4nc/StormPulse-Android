package com.carolinasvrwx.stormpulse

/**
 * Free, keyless OpenStreetMap-based destination search and route planning.
 * Invoke network methods on a background thread.
 */
object RoutingServices {
    fun addressSearch(): NominatimAddressSearch = NominatimAddressSearch()
    fun routePlanner(): OsrmRoutePlanningProvider = OsrmRoutePlanningProvider()
}
