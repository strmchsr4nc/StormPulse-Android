package com.carolinasvrwx.stormpulse

/** Creates the free, keyless OpenStreetMap search and driving-route providers. */
object RoutingServices {
    fun addressSearch(): DestinationSearchProvider =
        DestinationSearchProvider { query -> NominatimAddressSearch().search(query) }

    fun routePlanner(): RoutePlanningProvider = OsrmRoutePlanningProvider()

    fun navigationController(
        callbackExecutor: java.util.concurrent.Executor
    ): RouteNavigationController = RouteNavigationController(
        addressSearch = addressSearch(),
        routePlanner = routePlanner(),
        callbackExecutor = callbackExecutor
    )
}
