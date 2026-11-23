package com.carolinasvrwx.stormpulse

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
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
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
            mapZoom = (mapZoom * scaleFactor).coerceIn(0.75f, 4f)
            drawMapSurface()
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            mapPanX = (mapPanX + distanceX).coerceIn(-1200f, 1200f)
            mapPanY = (mapPanY + distanceY).coerceIn(-900f, 900f)
            drawMapSurface()
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            mapPanX = (mapPanX + velocityX.coerceIn(-900f, 900f) * 0.12f)
                .coerceIn(-1200f, 1200f)
            mapPanY = (mapPanY + velocityY.coerceIn(-900f, 900f) * 0.12f)
                .coerceIn(-900f, 900f)
            drawMapSurface()
        }
    }

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) cleanup()
    }
    private var cleanedUp = false

    init {
        lifecycle.addObserver(lifecycleObserver)
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
        return NavigationTemplate.Builder()
            .setBackgroundColor(CarColor.SECONDARY)
            .setActionStrip(ActionStrip.Builder().addAction(Action.APP_ICON).build())
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
            target.drawColor(Color.rgb(12, 24, 35))
            val state = CarNavigationState.current()
            val route = state.route
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(35, 58, 68)
                strokeWidth = 1.5f
                style = Paint.Style.STROKE
            }
            var x = 0f
            while (x < target.width) {
                target.drawLine(x, 0f, x, target.height.toFloat(), gridPaint)
                x += 80f
            }
            var y = 0f
            while (y < target.height) {
                target.drawLine(0f, y, target.width.toFloat(), y, gridPaint)
                y += 80f
            }

            if (route != null && route.geometry.size >= 2) {
                val points = route.geometry
                val visiblePoints = if (state.location != null) points + state.location else points
                // Project geographic coordinates into Web Mercator so route
                // geometry keeps a consistent map-like shape at different latitudes.
                fun mercatorY(latitude: Double): Double {
                    val clamped = latitude.coerceIn(-85.05112878, 85.05112878)
                    val radians = Math.toRadians(clamped)
                    return kotlin.math.ln(kotlin.math.tan(Math.PI / 4.0 + radians / 2.0))
                }
                val projected = visiblePoints.map { it.longitude to mercatorY(it.latitude) }
                val minX = projected.minOf { it.first }
                val maxX = projected.maxOf { it.first }
                val minY = projected.minOf { it.second }
                val maxY = projected.maxOf { it.second }
                val xSpan = (maxX - minX).coerceAtLeast(0.000001)
                val ySpan = (maxY - minY).coerceAtLeast(0.000001)
                val pad = 54f
                val usableW = (target.width - pad * 2).coerceAtLeast(1f)
                val usableH = (target.height - pad * 2).coerceAtLeast(1f)
                val scale = minOf(usableW / xSpan, usableH / ySpan)
                val centerX = (minX + maxX) / 2.0
                val centerY = (minY + maxY) / 2.0
                val saveCount = target.save()
                target.clipRect(0f, 0f, target.width.toFloat(), target.height.toFloat())
                fun px(lon: Double) = target.width / 2f + mapPanX +
                    (((lon - centerX) * scale).toFloat() * mapZoom)
                fun py(lat: Double) = target.height / 2f + mapPanY -
                    (((mercatorY(lat) - centerY) * scale).toFloat() * mapZoom)
                val path = Path()
                points.forEachIndexed { index, point ->
                    if (index == 0) path.moveTo(px(point.longitude), py(point.latitude))
                    else path.lineTo(px(point.longitude), py(point.latitude))
                }
                target.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(15, 80, 130)
                    strokeWidth = 18f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                })
                target.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(55, 190, 255)
                    strokeWidth = 9f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                })
                val destinationPoint = route.destination.point
                target.drawCircle(px(destinationPoint.longitude), py(destinationPoint.latitude), 11f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 190, 65) })
                state.location?.let { location ->
                    target.drawCircle(px(location.longitude), py(location.latitude), 17f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                    target.drawCircle(px(location.longitude), py(location.latitude), 11f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 160, 255) })
                }
                target.restoreToCount(saveCount)
            } else {
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
                target.drawText("Select a destination on your phone to begin", 32f,
                    target.height - 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(180, 205, 220)
                        textSize = 18f
                    })
            }
        } catch (_: Exception) {
            // The host may replace its surface while a frame is being drawn.
        } finally {
            if (canvas != null) try { surface.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
        }
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
        lifecycle.removeObserver(lifecycleObserver)
    }
}
