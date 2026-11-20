# StormPulse Android — device test checklist

Run these checks on a physical Android phone before distributing the app.

## Install and launch
- [ ] Install the debug APK on Android 7.0 or newer.
- [ ] Confirm the launcher label is StormPulse and the radar-themed icon appears.
- [ ] Launch on Wi-Fi and mobile data.
- [ ] Confirm the live Base44 StormPulse home screen loads.

## Core website behavior
- [ ] Pan and zoom the live radar map.
- [ ] Open each navigation area: Layers, Route, Alerts, Quakes, Tsunami, Storm Data, SPC, Space, SW Alerts, Seismos, Volcanoes, and Airports.
- [ ] Confirm map tiles and data layers load over HTTPS.
- [ ] Confirm login/session behavior if the site requires sign-in.

## Android integration
- [ ] Test Android Back navigation within StormPulse.
- [ ] Test external links, email links, and telephone links.
- [ ] Test location permission allow and deny flows.
- [ ] Confirm location is only granted to the StormPulse website origin.
- [ ] Rotate the device and background/restore the app.
- [ ] Disable network, confirm the retry screen appears, then restore network and retry.

## Release readiness
- [ ] Test on the intended Samsung device and at least one other Android device.
- [ ] Review Base44 authentication redirects and any external sign-in providers.
- [ ] Confirm privacy policy and location-use disclosure are available from the website.
- [ ] Create and securely back up a release signing key before publishing.
