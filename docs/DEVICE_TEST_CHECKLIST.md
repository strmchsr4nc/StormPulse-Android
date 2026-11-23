# StormPulse Android and Android Auto device test checklist

Complete these checks on real hardware before public distribution. CI success alone does not replace device testing.

## Phone app

- [ ] Install the debug APK on Android 7.0 or newer.
- [ ] Confirm StormPulse launcher label and icon.
- [ ] Launch on Wi-Fi and mobile data; verify the hosted Base44 home loads.
- [ ] Test map gestures and each website navigation area.
- [ ] Test login/session behavior and external sign-in redirects.
- [ ] Test Back navigation, external HTTP(S), email, and telephone links.
- [ ] Test geolocation allow/deny and confirm prompts are limited to the StormPulse origin.
- [ ] Rotate, background, and restore the app.
- [ ] Disable network, verify failure UI, restore network, and retry.
- [ ] Open native Navigation from the launcher shortcut; grant/deny location; test destination search, route calculation, GPS marker, route summary, turn prompts, rerouting, and map-app chooser.

## Android Auto / Desktop Head Unit

- [ ] Confirm StormPulse appears as a navigation app on a supported Android Auto host.
- [ ] Confirm the host accepts the app's minimum Car App API level 2.
- [ ] Connect with no active route; verify the idle guidance screen and map surface.
- [ ] Start a route on the phone; verify destination, maneuver, and trip estimates update in the car.
- [ ] Verify route line and GPS marker draw on the car surface.
- [ ] Test pan mode and map zoom/scroll on supported hosts.
- [ ] Stop navigation from the host; verify the car UI exits navigation cleanly and the phone state remains consistent.
- [ ] Disconnect/reconnect Android Auto; verify callbacks, map surface, and navigation state are cleaned up and restored.
- [ ] Test GPS loss, network loss, expired/stale NWS alerts, and recovery.
- [ ] Verify voice guidance and weather announcements are audible without overlapping or repeating excessively.

## Release readiness

- [ ] Run CI unit tests, Android lint, debug assembly, and release assembly with no errors.
- [ ] Test on the intended Samsung device and at least one other Android device.
- [ ] Test in a real vehicle only when safely parked; do not interact with the app while driving.
- [ ] Confirm privacy policy and location-use disclosure are available.
- [ ] Configure and securely back up the release signing key.
- [ ] Complete Android for Cars app-quality and Play Console requirements.
