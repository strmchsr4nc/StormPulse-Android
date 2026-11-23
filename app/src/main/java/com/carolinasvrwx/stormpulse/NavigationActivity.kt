package com.carolinasvrwx.stormpulse

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import kotlin.math.roundToInt

/** Standalone native map navigation; the existing MainActivity home is untouched. */
class NavigationActivity : Activity() {
    companion object { private const val LOCATION_PERMISSION_REQUEST = 4102 }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var map: MapView
    private lateinit var destinationInput: EditText
    private lateinit var statusText: TextView
    private lateinit var routeSummary: TextView
    private lateinit var progress: ProgressBar
    private lateinit var searchButton: Button
    private lateinit var externalMapButton: Button
    private lateinit var locationProvider: AndroidGpsLocationProvider
    private lateinit var navigationController: RouteNavigationController
    private lateinit var routeSession: RouteSessionCoordinator
    private var routeLine: Polyline? = null
    private var userMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var latestFix: GeoPoint? = null
    private var selectedDestination: GeoPoint? = null
    private var selectedDestinationLabel: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        buildLayout()
        navigationController = RoutingServices.navigationController(
            java.util.concurrent.Executor { command -> mainHandler.post(command) }
        )
        routeSession = RouteSessionCoordinator(navigationController, object : RouteSessionCoordinator.Listener {
            override fun onLocation(point: GeoPoint, accuracyMeters: Float) {
                latestFix = point
                updateUserMarker(point)
                if (routeSession.currentRoute() == null) map.controller.setCenter(point.toOsmPoint())
            }
            override fun onRoute(route: PlannedRoute, isReroute: Boolean) {
                progress.visibility = View.GONE
                searchButton.isEnabled = true
                drawRoute(route)
                externalMapButton.visibility = if (selectedDestination != null) View.VISIBLE else View.GONE
                routeSummary.text = formatDistance(route.distanceMeters) + " · about " + formatDuration(route.durationSeconds)
                val turn = route.maneuvers.firstOrNull()?.instruction
                statusText.text = if (isReroute) "Route updated" else "Route ready"
                if (!turn.isNullOrBlank()) statusText.text = statusText.text.toString() + " · " + turn
            }
            override fun onRerouting(distanceOffRouteMeters: Double) {
                statusText.text = "Off route by " + formatDistance(distanceOffRouteMeters) + ". Recalculating…"
                progress.visibility = View.VISIBLE
            }
            override fun onError(error: Exception) {
                progress.visibility = View.GONE
                searchButton.isEnabled = true
                statusText.text = error.message ?: "Navigation request failed."
            }
        })
        locationProvider = AndroidGpsLocationProvider(
            this,
            onLocation = { point, accuracy, _ -> routeSession.onLocationFix(point, accuracy) },
            onError = { message -> statusText.text = message }
        )
        requestOrStartLocation()
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 18, 28))
            setPadding(dp(12), dp(10), dp(12), dp(8))
        }
        val title = TextView(this).apply {
            text = "StormPulse Navigation"; textSize = 21f; setTextColor(Color.WHITE)
        }
        destinationInput = EditText(this).apply {
            hint = "Enter a US destination address"; textSize = 16f; setSingleLine(true)
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setPadding(dp(12), 0, dp(12), 0); setBackgroundColor(Color.rgb(35, 45, 60))
        }
        searchButton = Button(this).apply { text = "Find route"; setOnClickListener { searchDestination() } }
        externalMapButton = Button(this).apply {
            text = "Open directions in another map app"
            visibility = View.GONE
            setOnClickListener { openInMapChooser() }
        }
        progress = ProgressBar(this).apply { visibility = View.GONE }
        statusText = TextView(this).apply {
            text = "Waiting for GPS location…"; textSize = 14f; setTextColor(Color.rgb(170, 205, 235))
        }
        routeSummary = TextView(this).apply {
            text = "Enter a destination to begin"; textSize = 16f; setTextColor(Color.WHITE)
        }
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(13.0)
        }
        root.addView(title, matchWrap())
        root.addView(destinationInput, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
        root.addView(searchButton, matchWrap())
        root.addView(externalMapButton, matchWrap())
        root.addView(progress, matchWrap())
        root.addView(statusText, matchWrap())
        root.addView(routeSummary, matchWrap())
        root.addView(map, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
        setContentView(root)
    }

    private fun searchDestination() {
        val query = destinationInput.text.toString().trim()
        if (query.isBlank()) { statusText.text = "Enter a destination address."; return }
        if (latestFix == null) { statusText.text = "Waiting for a GPS fix before finding your route."; return }
        searchButton.isEnabled = false
        progress.visibility = View.VISIBLE
        statusText.text = "Searching for destination…"
        navigationController.searchDestination(query, { matches ->
            if (matches.isEmpty()) {
                progress.visibility = View.GONE; searchButton.isEnabled = true
                statusText.text = "No matching US address was found. Add city and state."; return@searchDestination
            }
            val match = matches.first()
            destinationInput.setText(match.formattedAddress)
            val target = RouteDestination(match.formattedAddress, match.point)
            selectedDestination = match.point
            selectedDestinationLabel = match.formattedAddress
            externalMapButton.visibility = View.GONE
            destinationMarker?.let { map.overlays.remove(it) }
            val marker = Marker(map).apply {
                position = match.point.toOsmPoint(); title = match.formattedAddress
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            destinationMarker = marker
            map.overlays.add(marker)
            routeSession.navigateTo(target)
            statusText.text = "Calculating route…"
        }, { error ->
            progress.visibility = View.GONE; searchButton.isEnabled = true
            statusText.text = error.message ?: "Address search failed."
        })
    }


    /** Offers installed navigation apps without forcing a handoff. */
    private fun openInMapChooser() {
        val destination = selectedDestination ?: run {
            statusText.text = "Find a route before opening another map app."
            return
        }
        val label = Uri.encode(selectedDestinationLabel)
        val geoUri = Uri.parse("geo:${destination.latitude},${destination.longitude}?q=${destination.latitude},${destination.longitude}($label)")
        val intent = Intent(Intent.ACTION_VIEW, geoUri)
        try {
            startActivity(Intent.createChooser(intent, "Choose a map or navigation app"))
        } catch (_: Exception) {
            statusText.text = "No compatible map app is installed. Continue with StormPulse navigation."
        }
    }

    private fun drawRoute(route: PlannedRoute) {
        routeLine?.let { map.overlays.remove(it) }
        val line = Polyline().apply {
            setPoints(route.geometry.map { it.toOsmPoint() })
            outlinePaint.color = Color.rgb(25, 135, 245)
            outlinePaint.strokeWidth = dp(6).toFloat()
            title = "StormPulse route"
        }
        routeLine = line
        map.overlays.add(line)
        route.geometry.firstOrNull()?.let { map.controller.setCenter(it.toOsmPoint()) }
        map.controller.setZoom(14.0)
        map.invalidate()
    }

    private fun updateUserMarker(point: GeoPoint) {
        if (userMarker == null) {
            userMarker = Marker(map).apply {
                title = "Your location"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                map.overlays.add(this)
            }
        }
        userMarker?.position = point.toOsmPoint()
        map.invalidate()
    }

    private fun requestOrStartLocation() {
        if (hasLocationPermission()) locationProvider.start()
        else requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            LOCATION_PERMISSION_REQUEST
        )
    }
    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) locationProvider.start()
            else statusText.text = "Location permission is required for route navigation."
        }
    }
    override fun onResume() {
        super.onResume()
        if (::map.isInitialized) map.onResume()
        if (::locationProvider.isInitialized && hasLocationPermission()) locationProvider.start()
    }
    override fun onPause() {
        if (::map.isInitialized) map.onPause()
        if (::locationProvider.isInitialized) locationProvider.stop()
        super.onPause()
    }
    override fun onDestroy() {
        if (::locationProvider.isInitialized) locationProvider.close()
        if (::routeSession.isInitialized) routeSession.close()
        if (::navigationController.isInitialized) navigationController.close()
        if (::map.isInitialized) map.onDetach()
        super.onDestroy()
    }

    private fun GeoPoint.toOsmPoint() = OsmGeoPoint(latitude, longitude)
    private fun formatDistance(meters: Double): String =
        if (meters >= 1609.344) String.format(Locale.US, "%.1f mi", meters / 1609.344)
        else meters.roundToInt().toString() + " m"
    private fun formatDuration(seconds: Double): String {
        val minutes = (seconds / 60.0).roundToInt().coerceAtLeast(1)
        return if (minutes >= 60) (minutes / 60).toString() + " hr " + (minutes % 60) + " min"
        else minutes.toString() + " min"
    }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
