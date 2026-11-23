# Free GPS routing for StormPulse

StormPulse uses OpenStreetMap data with two free, keyless public services:

- **OSRM** (Open Source Routing Machine) at `router.project-osrm.org` calculates driving routes, route geometry, distance, duration, and turn steps.
- **Nominatim** at `nominatim.openstreetmap.org` searches destination addresses.

No Mapbox account, API token, billing setup, or paid SDK is required.

## Important public-service limits

These are community-operated public endpoints, not guaranteed commercial services.
The OSRM demo server is best-effort and may throttle or change availability. Public
Nominatim requires an identifying User-Agent, allows at most one request per
second, and prohibits autocomplete/bulk use. StormPulse therefore must submit
Nominatim searches only after the user explicitly searches, not on every keystroke.
For a widely distributed production app, the recommended next step is hosting
OSRM and Nominatim or using a provider with an explicit production SLA.

## GPS and navigation

The routing provider calculates a route between the GPS origin supplied by the
app and the selected destination. Android location permissions, continuous GPS
tracking, map drawing, rerouting, and driver-facing UI still need to be connected
to this provider. The route provider itself does not activate GPS or display a map.
All network calls must run off the Android main thread.

## OpenStreetMap attribution

When displaying OpenStreetMap-derived map tiles or data, show the required
OpenStreetMap attribution and follow the tile/service usage policies. Routing
service availability is not a safety guarantee; drivers must obey road signs and
conditions.
