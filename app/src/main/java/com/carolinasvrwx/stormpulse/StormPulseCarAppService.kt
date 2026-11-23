package com.carolinasvrwx.stormpulse

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
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
import androidx.car.app.model.CarText
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.model.Distance
import androidx.car.app.validation.HostValidator

/**
 * Separate Android Auto entry point.
 *
 * MainActivity remains the regular phone experience. The car host receives a
 * NavigationTemplate and a host-owned map surface; route data is connected in
 * a later integration milestone.
 */
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
    override fun onCreateScreen(intent: Intent): Screen =
        StormPulseCarNavigationScreen(carContext)
}

/**
 * First real navigation-template milestone. It draws a lightweight StormPulse
 * map placeholder to the Android Auto surface instead of showing a generic
 * connection message. It deliberately does not pretend to have a live route.
 */
private class StormPulseCarNavigationScreen(carContext: CarContext) : Screen(carContext) {
    private var mapSurface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            mapSurface = surfaceContainer.surface
            surfaceWidth = surfaceContainer.width
            surfaceHeight = surfaceContainer.height
            drawMapSurface()
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            mapSurface = null
            surfaceWidth = 0
            surfaceHeight = 0
        }

        override fun onVisibleAreaChanged(visibleArea: android.graphics.Rect) {
            drawMapSurface()
        }

        override fun onStableAreaChanged(stableArea: android.graphics.Rect) {
            drawMapSurface()
        }
    }

    init {
        carContext.getCarService(AppManager::class.java)
            .setSurfaceCallback(surfaceCallback)
        CarNavigationState.setListener {
            invalidate()
            drawMapSurface()
        }
    }

    override fun onGetTemplate(): Template {
        val actions = ActionStrip.Builder()
            .addAction(Action.APP_ICON)
            .build()

        val state = CarNavigationState.current()
        val instruction = if (state.active) state.nextInstruction ?: "Continue on the current route"
            else "Choose a destination in StormPulse on your phone"
        val distance = state.distanceToManeuverMeters?.coerceAtLeast(0.0) ?: 0.0
        return NavigationTemplate.Builder()
            .setBackgroundColor(CarColor.SECONDARY)
            .setActionStrip(actions)
            .setNavigationInfo(
                RoutingInfo.Builder()
                    .setCurrentStep(Step.Builder(instruction).build(),
                        Distance.create(distance, Distance.UNIT_METERS))
                    .build()
            )
            .build()
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
            while (x < target.width) { target.drawLine(x, 0f, x, target.height.toFloat(), gridPaint); x += 80f }
            var y = 0f
            while (y < target.height) { target.drawLine(0f, y, target.width.toFloat(), y, gridPaint); y += 80f }

            if (route != null && route.geometry.size >= 2) {
                val points = route.geometry
                val minLat = points.minOf { it.latitude }
                val maxLat = points.maxOf { it.latitude }
                val minLon = points.minOf { it.longitude }
                val maxLon = points.maxOf { it.longitude }
                val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
                val lonSpan = (maxLon - minLon).coerceAtLeast(0.0001)
                val pad = 54f
                val usableW = (target.width - pad * 2).coerceAtLeast(1f)
                val usableH = (target.height - pad * 2).coerceAtLeast(1f)
                fun px(lon: Double) = pad + ((lon - minLon) / lonSpan * usableW).toFloat()
                fun py(lat: Double) = target.height - pad - ((lat - minLat) / latSpan * usableH).toFloat()
                val path = Path()
                points.forEachIndexed { index, point ->
                    if (index == 0) path.moveTo(px(point.longitude), py(point.latitude))
                    else path.lineTo(px(point.longitude), py(point.latitude))
                }
                target.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(15, 80, 130); strokeWidth = 18f
                    style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                })
                target.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(55, 190, 255); strokeWidth = 9f
                    style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                })
                val destination = route.destination.point
                target.drawCircle(px(destination.longitude), py(destination.latitude), 11f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 190, 65) })
                state.location?.let { location ->
                    target.drawCircle(px(location.longitude), py(location.latitude), 17f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                    target.drawCircle(px(location.longitude), py(location.latitude), 11f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 160, 255) })
                }
            } else {
                target.drawText("STORMPULSE", 32f, 54f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE; textSize = 34f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                })
                target.drawText("Navigation display connected", 32f, 88f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(105, 210, 255); textSize = 22f })
                target.drawText("Select a destination on your phone to begin", 32f,
                    target.height - 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(180, 205, 220); textSize = 18f
                    })
            }
        } catch (_: Exception) {
            // The host may replace its surface while a frame is being drawn.
        } finally {
            if (canvas != null) try { surface.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
        }
    }
}
