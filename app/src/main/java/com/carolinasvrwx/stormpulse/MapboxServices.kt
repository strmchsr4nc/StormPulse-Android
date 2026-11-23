package com.carolinasvrwx.stormpulse

/**
 * Creates configured Mapbox adapters from the build-time public token.
 * A missing token is reported explicitly instead of silently using fake data.
 */
object MapboxServices {
    fun requireAddressSearch(): MapboxAddressSearch =
        MapboxAddressSearch(requireToken())

    fun requireRoutePlanner(): MapboxRoutePlanningProvider =
        MapboxRoutePlanningProvider(requireToken())

    private fun requireToken(): String =
        BuildConfig.MAPBOX_ACCESS_TOKEN.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "Mapbox is not configured. Set MAPBOX_ACCESS_TOKEN in your Gradle user properties or environment."
            )
}
