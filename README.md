# StormPulse for Android

StormPulse is a single Android application with two experiences:

- **Phone mode:** loads the existing StormPulse Base44 website in a hardened Android WebView.
- **Android Auto mode:** a separate Android for Cars navigation service with a driver-oriented navigation template and host-provided map surface.

**Phone start URL:** https://carolinasevereweatherresearch.base44.app/

## Implemented Android features

- WebView home, JavaScript/DOM storage, origin-restricted geolocation prompts, progress/retry UI, safe external links, SSL fail-closed behavior, Safe Browsing handling, and renderer recovery.
- Separate native navigation activity with GPS permission handling, OpenStreetMap tiles, destination search, route geometry, route summary, turn progression, voice prompts, and optional external map-app chooser.
- OSRM driving route calculation and Nominatim US address search, performed off the UI thread.
- Android Auto CarAppService, NavigationTemplate, shared phone-to-car route/GPS state, destination/step estimates, and a host-owned map surface.
- Android Auto map route/GPS rendering, Web Mercator coordinate projection, and pan/zoom gesture callbacks.
- NWS active-alert retrieval, polygon/route intersection, route-ahead prioritization, alert freshness indicator, and deduplicated voice announcements.
- Unit tests for routing models/controller, NWS parsing, hazard matching, alert policy, and route session behavior.

## Known limitations — not yet production complete

- The Android Auto surface currently draws route geometry and the GPS marker on a custom background; it does **not yet render real street-map tiles**.
- Radar imagery, lightning overlays, and animated radar playback have not yet been integrated into the Android Auto map surface.
- Weather-alert route assessment is advisory. Automatic hazard-avoiding alternate route generation is not implemented and no route is guaranteed safe.
- OSRM and Nominatim public endpoints are community services without a production SLA. A production deployment should use appropriately hosted or contracted services and follow their usage policies.
- The app has not yet completed physical-phone, Android Auto Desktop Head Unit, or in-vehicle validation.
- A successful CI APK build is not the same as Google Play approval or a signed production release.

## Build

1. Install Android Studio and Android SDK Platform 35.
2. Open the project in Android Studio and allow Gradle sync to finish.
3. Build with **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

The GitHub Actions workflow runs unit tests, Android lint, and debug/release assembly. The downloadable CI artifact is a debug APK for testing. A public release requires a securely backed-up signing key and Play Console setup.

## Package details

- Application ID: `com.carolinasvrwx.stormpulse`
- Version: `1.1.0` (versionCode 3)
- Minimum Android: 7.0 (API 24)
- Compile/target SDK: 35
- Java/Kotlin target: 17
- Android Auto minimum Car App API: 2

## Testing

See [DEVICE_TEST_CHECKLIST.md](docs/DEVICE_TEST_CHECKLIST.md) for phone and Android Auto test steps. Complete the physical-device and car-host checks before calling navigation production-ready.
