package com.carolinasvrwx.stormpulse

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.PanModeListener
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.graphics.drawable.IconCompat
import java.util.TimeZone

/** Separate Android Auto entry point; the phone WebView remains unchanged. */
class StormPulseCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session = StormPulseCarSession()
}

private class StormPulseCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = StormPulseCarNavigationScreen(carContext)
}

/**
 * Publishes trip/destination/step estimates to Android Auto's navigation host
 * and draws the current route on the host-owned map surface.
 */
private class StormPulseCarNavigationScreen(carContext: CarContext) : Screen(carContext) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val navigationManager = carContext.getCarService(NavigationManager::class.java)
    private var navigationStarted = false
    private var mapSurface: Surface? = null
    private val streetMap: MapView = MapView(carContext).apply {
        Configuration.getInstance().userAgentValue = carContext.packageName
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(false)
        setUseDataConnection(true)
        controller.setZoom(11.0)
    }
    private var routeOverlay: Polyline? = null
    private var currentPositionMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var lastCameraRoute: PlannedRoute? = null

    private val navigationCallback = object : NavigationManagerCallback {
        override fun onStopNavigation() {
            navigationStarted = false
            CarNavigationState.setCarHostActive(false)
            invalidate()
            drawMapSurface()
        }
    }

    // Host gestures are translated into a simple map camera. Keep the camera
    // bounded so accidental gestures cannot make the route disappear.
    private var mapZoom = 1f
    private var mapPanX = 0f
    private var mapPanY = 0f

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            mapSurface = surfaceContainer.surface
            drawMapSurface()
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            mapSurface = null
        }

        override fun onVisibleAreaChanged(visibleArea: android.graphics.Rect) = drawMapSurface()
        override fun onStableAreaChanged(stableArea: android.graphics.Rect) = drawMapSurface()

        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
            if (!scaleFactor.isFinite() || scaleFactor <= 0f) return
            val nextZoom = (streetMap.zoomLevelDouble + kotlin.math.ln(scaleFactor) / kotlin.math.ln(2.0))
                .coerceIn(3.0, 19.0)
            streetMap.controller.setZoom(nextZoom)
            drawMapSurface()
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            streetMap.scrollBy(distanceX.toInt(), distanceY.toInt())
            drawMapSurface()
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            streetMap.scrollBy(
                (velocityX.coerceIn(-900f, 900f) * 0.12f).toInt(),
                (velocityY.coerceIn(-900f, 900f) * 0.12f).toInt()
            )
            drawMapSurface()
        }
    }

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) cleanup()
    }
    private var cleanedUp = false

    init {
        lifecycle.addObserver(lifecycleObserver)
        streetMap.onResume()
        navigationManager.setNavigationManagerCallback(navigationCallback)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
        CarNavigationState.setListener {
            invalidate()
            drawMapSurface()
            publishTripIfActive()
        }
    }

    override fun onGetTemplate(): Template {
        val state = CarNavigationState.current()
        if (state.active && !navigationStarted) {
            navigationManager.navigationStarted()
            navigationStarted = true
            CarNavigationState.setCarHostActive(true)
        }
        publishTripIfActive()
        val instruction = if (state.active) state.nextInstruction ?: "Continue on the current route"
            else "Choose a destination in StormPulse on your phone"
        val distance = state.distanceToManeuverMeters?.coerceAtLeast(0.0) ?: 0.0
        val panIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_pan)
        ).build()
        return NavigationTemplate.Builder()
            .setBackgroundColor(CarColor.SECONDARY)
            .setActionStrip(ActionStrip.Builder().addAction(Action.APP_ICON).build())
            .setMapActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder(Action.PAN).setIcon(panIcon).build())
                    .build()
            )
            .setPanModeListener(PanModeListener { isInPanMode ->
                if (!isInPanMode) drawMapSurface()
            })
            .setNavigationInfo(
                RoutingInfo.Builder()
                    .setCurrentStep(
                        Step.Builder(instruction).build(),
                        Distance.create(distance, Distance.UNIT_METERS)
                    )
                    .build()
            )
            .build()
    }

    private fun publishTripIfActive() {
        if (!navigationStarted) return
        val state = CarNavigationState.current()
        val route = state.route
        if (!state.active || route == null) {
            navigationManager.navigationEnded()
            navigationStarted = false
            CarNavigationState.setCarHostActive(false)
            return
        }
        try {
            val now = System.currentTimeMillis()
            val zone = TimeZone.getDefault()
            val totalSeconds = route.durationSeconds.toLong().coerceAtLeast(0L)
            val remainingMeters = route.distanceMeters.coerceAtLeast(0.0)
            val arrival = DateTimeWithZone.create(now + totalSeconds * 1000L, zone)
            val destinationEstimate = TravelEstimate.Builder(
                Distance.create(remainingMeters, Distance.UNIT_METERS), arrival
            ).setRemainingTimeSeconds(totalSeconds).build()
            val destination = Destination.Builder()
                .setName(route.destination.label)
                .setAddress(route.destination.label)
                .build()
            val tripBuilder = Trip.Builder().addDestination(destination, destinationEstimate)
            val instruction = state.nextInstruction
            if (!instruction.isNullOrBlank()) {
                val stepDistance = state.distanceToManeuverMeters?.coerceAtLeast(0.0) ?: 0.0
                val stepSeconds = if (remainingMeters > 0.0) {
                    (totalSeconds * (stepDistance / remainingMeters)).toLong().coerceAtLeast(0L)
                } else 0L
                val stepArrival = DateTimeWithZone.create(now + stepSeconds * 1000L, zone)
                val stepEstimate = TravelEstimate.Builder(
                    Distance.create(stepDistance, Distance.UNIT_METERS), stepArrival
                ).setRemainingTimeSeconds(stepSeconds).build()
                tripBuilder.addStep(Step.Builder(instruction).build(), stepEstimate)
            }
            navigationManager.updateTrip(tripBuilder.build())
        } catch (_: Exception) {
            // A host may disconnect while a trip update is being delivered.
        }
    }

    private fun drawMapSurface() {
        val surface = mapSurface ?: return
        if (!surface.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = surface.lockCanvas(null)
            val target = canvas
            val state = CarNavigationState.current()
            val route = state.route
            streetMap.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(
                    target.width, android.view.View.MeasureSpec.EXACTLY
                ),
                android.view.View.MeasureSpec.makeMeasureSpec(
                    target.height, android.view.View.MeasureSpec.EXACTLY
                )
            )
            streetMap.layout(0, 0, target.width, target.height)
            updateStreetMapOverlays(route, state.location)
            streetMap.draw(target)
            // Keep attribution visible over the rendered map surface.
            target.drawText("© OpenStreetMap contributors", 16f, target.height - 14f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 14f
                    setShadowLayer(3f, 1f, 1f, Color.BLACK)
                })

            if (route == null) {
                target.drawText("STORMPULSE", 32f, 54f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 34f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                })
                target.drawText("Navigation display connected", 32f, 88f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(105, 210, 255)
                        textSize = 22f
                    })
            }
        } catch (_: Exception) {
            // The host may replace its surface while a frame is being drawn.
        } finally {
            if (canvas != null) try { surface.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
        }
    }


    private fun updateStreetMapOverlays(route: PlannedRoute?, location: GeoPoint?) {
        if (routeOverlay == null && route != null) {
            routeOverlay = Polyline().apply {
                outlinePaint.color = Color.rgb(35, 165, 245)
                outlinePaint.strokeWidth = 12f
            }
            routeOverlay?.let { streetMap.overlays.add(it) }
        }
        routeOverlay?.setPoints(route?.geometry?.map {
            org.osmdroid.util.GeoPoint(it.latitude, it.longitude)
        } ?: emptyList())

        if (destinationMarker == null && route != null) {
            destinationMarker = Marker(streetMap).apply {
                title = route.destination.label
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            destinationMarker?.let { streetMap.overlays.add(it) }
        }
        route?.let {
            destinationMarker?.position = org.osmdroid.util.GeoPoint(
                it.destination.point.latitude, it.destination.point.longitude
            )
        }

        if (location != null && currentPositionMarker == null) {
            currentPositionMarker = Marker(streetMap).apply {
                title = "Current location"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            currentPositionMarker?.let { streetMap.overlays.add(it) }
        }
        if (location != null) {
            currentPositionMarker?.position =
                org.osmdroid.util.GeoPoint(location.latitude, location.longitude)
        }

        if (route != null && route !== lastCameraRoute) {
            val boxPoints = route.geometry.map {
                org.osmdroid.util.GeoPoint(it.latitude, it.longitude)
            }
            if (boxPoints.isNotEmpty()) {
                val north = boxPoints.maxOf { it.latitude }
                val south = boxPoints.minOf { it.latitude }
                val east = boxPoints.maxOf { it.longitude }
                val west = boxPoints.minOf { it.longitude }
                streetMap.post {
                    try {
                        streetMap.zoomToBoundingBox(
                            BoundingBox(north, east, south, west), false, 72
                        )
                    } catch (_: Exception) {
                        streetMap.controller.setCenter(boxPoints.first())
                    }
                }
            }
            lastCameraRoute = route
        } else if (route == null) {
            lastCameraRoute = null
        }
        streetMap.invalidate()
    }

    private fun cleanup() {
        if (cleanedUp) return
        cleanedUp = true
        CarNavigationState.setListener(null)
        CarNavigationState.setCarHostActive(false)
        if (navigationStarted) {
            try { navigationManager.navigationEnded() } catch (_: Exception) { }
        }
        navigationStarted = false
        try { navigationManager.clearNavigationManagerCallback() } catch (_: Exception) { }
        try {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
        } catch (_: Exception) { }
        mapSurface = null
        try { streetMap.onPause() } catch (_: Exception) { }
        streetMap.overlays.clear()
        lifecycle.removeObserver(lifecycleObserver)
    }
}
