# StormPulse dual-mode architecture

## Product requirement

StormPulse remains one Android application with two experiences:

- **Phone mode (default):** retain the existing Base44 WebView and the complete current StormPulse phone interface.
- **Android Auto mode (only while projected):** expose a separate, driver-safe navigation experience through the Android for Cars App Library. Do not replace, resize, or simplify the phone interface when Android Auto is disconnected.

## Mode selection

Android Auto availability is determined by the car-host lifecycle, not by guessing from screen size or a Bluetooth connection.

- The phone launcher continues to open `.MainActivity` and load the existing StormPulse website.
- A dedicated `CarAppService` is registered for the navigation category and is launched by the Android Auto host when the app is connected and approved.
- The car experience uses approved car templates and host-driven navigation surfaces. It must not mirror the phone WebView or present phone-sized touch controls.
- Disconnecting Android Auto ends the car-host session. The phone activity and its existing experience remain available.
- Shared route and alert state should live in a repository/service layer so both experiences can observe the same trip without sharing UI code.

## Navigation and weather safety

- Route planning accepts U.S. destination addresses within StormPulse.
- Route geometry and maneuver steps come from a documented routing provider.
- Current NWS watches and warnings are refreshed from official NWS alert data.
- Alert-to-route matching must use the alert geometry and route geometry, not just nearby point samples.
- Announcements distinguish watches from warnings and report stale/unavailable data honestly.
- A suggested alternate route is advisory only; never describe it as safe or guaranteed to avoid hazards.
- Avoid repeated voice alerts with event-based deduplication and configurable approach thresholds.

## Implementation sequence

1. Keep the current phone WebView behavior unchanged; add architecture and tests first.
2. Add the Car App Library service and verify the app is visible to the Android Auto host in a supported navigation category.
3. Implement a minimal host-compliant navigation template before adding map rendering.
4. Add route planning, location updates, maneuver progression, and text-to-speech.
5. Add NWS alert geometry intersection, alert prioritization, and voice announcements.
6. Add radar/map presentation only through supported car-screen APIs and driver-distraction constraints.
7. Test phone-only, connected, disconnect/reconnect, permissions, GPS loss, network loss, stale alerts, and real hardware.

## Current repository facts

The existing app is a WebView wrapper around `https://carolinasevereweatherresearch.base44.app/`. Its manifest currently declares only the phone launcher activity. The current source does not yet contain a Car App Library service, route engine, in-app routing screen, or NWS route-alert engine. This document is a plan, not a claim that those features are implemented.
