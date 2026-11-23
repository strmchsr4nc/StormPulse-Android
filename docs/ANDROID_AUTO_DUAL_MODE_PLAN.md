# StormPulse dual-mode architecture

## Product requirement

StormPulse remains one Android application with two experiences:

- **Phone mode (default):** preserve the existing Base44 WebView and hosted StormPulse phone interface.
- **Android Auto mode (when projected):** expose a separate driver-oriented navigation experience through the Android for Cars App Library. Do not mirror the phone WebView or replace the phone home screen.

## Implemented foundation

- Phone launcher continues to open MainActivity and load the hosted StormPulse website.
- A dedicated CarAppService is registered for the NAVIGATION category with the required navigation-template and map-surface permissions.
- NavigationActivity provides destination search, foreground GPS, OSRM route calculation, an osmdroid phone map, route progress, rerouting, and spoken turn prompts.
- CarNavigationState shares route geometry, current position, and the current maneuver with the Android Auto service in-process.
- StormPulseCarAppService publishes NavigationTemplate instructions and Trip estimates and draws route/GPS geometry onto the host-provided surface.
- Android Auto map pan/zoom callbacks and the map pan action are enabled for minimum Car App API level 2.
- NWS active-alert polygons are matched against the route and announced with deduplication and stale-data suppression.

## Known gaps and release gates

1. Android Auto map surface still uses a custom background; integrate real street-map tiles and attribution before calling it a full map.
2. Integrate live radar, lightning, and severe-weather overlays where allowed by Android Auto driver-distraction rules.
3. Implement advisory alternate-route evaluation for weather hazards; never guarantee a route is safe.
4. Implement and verify navigation audio focus and Android Auto lifecycle/stop behavior.
5. Run unit tests, Android lint, debug/release assembly in CI.
6. Test on physical Android phones and Android Auto Desktop Head Unit, then a real vehicle. Verify connect/disconnect, GPS/network loss, permissions, map gestures, rerouting, stale alerts, and spoken guidance.
7. Prepare a signed release, Play Console declarations, and Android for Cars app-quality review only after the preceding gates pass.

## Safety and service constraints

- NWS watches and warnings are advisory information; users must follow official instructions and road conditions.
- OSRM and Nominatim public endpoints are best-effort community services without a production SLA. Observe Nominatim's usage policy and provide a production-suitable routing/geocoding backend before broad distribution.
- GPS ownership and route calculation currently remain in the foreground NavigationActivity. Do not claim background tracking when the activity is stopped.
