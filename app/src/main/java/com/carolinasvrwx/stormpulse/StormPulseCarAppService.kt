package com.carolinasvrwx.stormpulse

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator

/**
 * Separate Android Auto entry point.
 *
 * MainActivity remains the regular phone experience. Android Auto connects
 * to this service and receives a driver-safe Car App Library template.
 *
 * This initial milestone establishes the car connection only. Live mapping,
 * route calculation, maneuver guidance, and NWS route alerts are not yet wired.
 */
class StormPulseCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        // Allow unknown hosts only in debug builds. Release builds accept
        // Android for Cars' maintained sample allow-list plus privileged hosts.
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
        StormPulseCarHomeScreen(carContext)
}

private class StormPulseCarHomeScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(
            "StormPulse is connected. Navigation and route-weather guidance are the next development steps."
        )
            .setTitle("StormPulse")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
