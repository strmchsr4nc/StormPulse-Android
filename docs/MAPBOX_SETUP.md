# Mapbox setup for StormPulse

StormPulse uses Mapbox Geocoding API v6 for destination search and Mapbox
Directions API v5 for route calculation. The current default profile is
`mapbox/driving-traffic`; `mapbox/driving`, walking, and cycling are also
available in the provider.

## Configure a token

Create a **public** Mapbox access token in your Mapbox account. Grant only the
public scopes needed for Geocoding and Directions. Do not use a secret token
or commit a token to this repository.

For local development, add this to your user-level Gradle properties file
(`~/.gradle/gradle.properties`):

```properties
MAPBOX_ACCESS_TOKEN=pk.your_public_token_here
```

Alternatively, set the `MAPBOX_ACCESS_TOKEN` environment variable before
running Gradle. The environment variable takes precedence over the Gradle
property. CI builds without a token still compile, but Mapbox requests will
report that configuration is missing.

A token included in an Android APK can be extracted. Treat it as a public
client token: restrict its scopes and usage as supported by Mapbox, monitor
usage, and rotate it if exposed beyond intended distribution.

## API behavior and current scope

- Address search uses temporary Geocoding v6 results and does not persist them.
  Mapbox's permanent-geocoding option has separate account and storage terms.
- Route calculation requests full GeoJSON route geometry, traffic-aware driving
  where supported, and turn-by-turn steps with imperial voice instruction
  wording.
- The provider currently returns the first route. Alternative routes are
  requested but not yet exposed in the app's route-selection UI.
- The phone WebView and Android Auto UI are not yet wired to these provider
  classes. Call network methods on a background thread; they perform blocking
  HTTP requests.
- A successful API response is required before displaying distance, ETA, or
  maneuver information. Never substitute an estimated or fabricated route.
