package com.carolinasvrwx.stormpulse

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import androidx.car.app.model.MessageInfo
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
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
    }

    override fun onGetTemplate(): Template {
        val actions = ActionStrip.Builder()
            .addAction(Action.APP_ICON)
            .build()

        return NavigationTemplate.Builder()
            .setBackgroundColor(CarColor.SECONDARY)
            .setActionStrip(actions)
            .setNavigationInfo(
                MessageInfo.Builder(CarText.create("StormPulse navigation is ready. Start a route on your phone to prepare guidance."))
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
            canvas.drawColor(Color.rgb(12, 24, 35))

            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(35, 58, 68)
                strokeWidth = 2f
                style = Paint.Style.STROKE
            }
            val spacing = 72f
            var x = 0f
            while (x < canvas.width) {
                canvas.drawLine(x, 0f, x, canvas.height.toFloat(), gridPaint)
                x += spacing
            }
            var y = 0f
            while (y < canvas.height) {
                canvas.drawLine(0f, y, canvas.width.toFloat(), y, gridPaint)
                y += spacing
            }

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(105, 210, 255)
                textSize = 22f
            }
            val centerX = canvas.width / 2f
            val centerY = canvas.height / 2f
            canvas.drawCircle(centerX, centerY, 13f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(105, 210, 255)
                style = Paint.Style.FILL
            })
            canvas.drawText("STORMPULSE", 32f, 54f, titlePaint)
            canvas.drawText("Navigation display connected", 32f, 88f, detailPaint)
            canvas.drawText("Select a destination on your phone to begin", 32f, canvas.height - 36f, detailPaint)
        } catch (_: Exception) {
            // The host may replace its surface while a frame is being drawn.
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {
                    // Surface was destroyed by the host during rendering.
                }
            }
        }
    }
}
