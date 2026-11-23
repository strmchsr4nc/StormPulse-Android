package com.carolinasvrwx.stormpulse

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.Screen
import androidx.car.app.validation.HostValidator
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat

/**
 * Android Auto entry point. This service is independent of MainActivity:
 * normal phone use continues to load the full StormPulse WebView.
 *
 * This first milestone intentionally presents a simple host-compliant
 * navigation landing screen. Live maps, routes, and NWS alert overlays
 * are subsequent milestones and are not claimed to be implemented here.
 */
class StormPulseCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        // Allows Android Auto's supported production and desktop test hosts.
        // Host validation can be tightened after the release host allowlist is finalized.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session = StormPulseCarSession()
}

private class StormPulseCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return StormPulseCarHomeScreen(carContext)
    }
}

private class StormPulseCarHomeScreen(
    carContext: androidx.car.app.CarContext
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val appIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_stormpulse)
        ).build()

        return MessageTemplate.Builder(
            "StormPulse is connected to Android Auto. Route guidance and weather alerts are being prepared."
        )
            .setTitle("StormPulse Navigation")
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.APP_ICON)
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Phone App")
                    .setOnClickListener {
                        // The car display stays within the car-safe experience.
                        // Do not launch the phone UI on the vehicle screen.
                    }
                    .build()
            )
            .build()
    }
}
