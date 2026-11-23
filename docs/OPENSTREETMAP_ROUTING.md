# OpenStreetMap routing and map data for StormPulse

## Services currently used

- **OSRM** at `router.project-osrm.org` calculates driving routes, route geometry, distance, duration, and turn steps.
- **Nominatim** at `nominatim.openstreetmap.org` searches US destination addresses after an explicit user action.
- **osmdroid** renders OpenStreetMap-based tiles in the native phone navigation activity.

The routing and address-search requests run off the Android main thread. Nominatim requests are serialized and throttled to approximately one request per second, and requests identify StormPulse with a User-Agent.

## Public-service limits

These community-operated endpoints are best-effort and have no production SLA. Public Nominatim prohibits autocomplete and bulk geocoding. Do not add keystroke-based requests or batch address harvesting. For a widely distributed production app, use appropriately hosted services or a provider with an explicit production SLA and terms suitable for the expected traffic.

## Current map implementation status

- **Phone navigation activity:** osmdroid street-map tiles, route polyline, destination marker, and GPS marker are implemented.
- **Android Auto:** the host-provided surface currently draws the route line and current-position marker on a custom dark background. Real street-map tile rendering is not integrated on the Android Auto surface yet.
- OpenStreetMap-derived map data and tiles require proper attribution and compliance with the tile provider's usage policy. Do not assume the public tile server is suitable for heavy production use.

## Navigation and safety

Route geometry, duration, and turn steps are supplied by the routing service. GPS tracking is foreground-only in the current activity; the app does not claim background navigation after the activity is stopped. Off-route recalculation is best-effort and throttled. NWS route alerts are advisory and depend on the availability and geometry of official alert data. No route is guaranteed to avoid hazardous weather or road conditions.
