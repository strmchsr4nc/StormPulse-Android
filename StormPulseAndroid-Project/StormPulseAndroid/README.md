# StormPulse for Android

A branded Android WebView wrapper for the live StormPulse Base44 application.

**Start URL:** https://carolinasevereweatherresearch.base44.app/

## Current build scope
- Loads the live StormPulse site in an Android WebView
- JavaScript and DOM storage for the Base44 application
- Android geolocation permission handoff, restricted to the StormPulse origin
- Loading progress and tap-to-retry connection screen
- Back-button navigation within the site
- External HTTP(S), email, and telephone links handed to Android
- HTTPS-only navigation, mixed content disabled, local file access disabled
- SSL certificate errors fail closed; Android Safe Browsing hits return to safety
- WebView lifecycle and state restoration
- Recovery if Android terminates the WebView renderer
- Safe Browsing threat handling

## Build an APK
1. Install Android Studio (Giraffe or newer) and the Android SDK Platform 35.
2. Open this `StormPulseAndroid` folder in Android Studio.
3. Allow Gradle sync to finish.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

For a release APK, use **Build > Generate Signed Bundle / APK** and create a private signing key. Back up the key securely; it is required for future updates distributed outside Google Play.

## Package details
- Application ID: `com.carolinasvrwx.stormpulse`
- Version: `1.0.1` (versionCode 2)
- Minimum Android: 7.0 (API 24)
- Compile/target SDK: 35
- Java/Kotlin target: 17

## Optional cloud build with GitHub Actions

A workflow is included at `.github/workflows/android.yml`. After pushing this project to a GitHub repository, the workflow builds a debug APK on pushes to `main`/`master` or when started manually from the repository Actions tab. Download `StormPulse-debug-apk` from the completed workflow run under **Artifacts**. This is a debug build for testing, not a signed public release.

## Important limitations and test plan
This is a website wrapper: it does not bundle StormPulse or provide offline radar. Changes published to the Base44 website appear in the app automatically. Website push notifications are not automatically converted into Android push notifications by WebView.

Before public distribution, test on physical Android devices: sign-in/session behavior, map gestures, geolocation prompts, external sign-in flows, orientation changes, network loss/recovery, and links. A production release should also be tested with the exact Base44 authentication and redirect behavior used by the live site.

This source project has not yet been compiled into an APK in this environment because the Android SDK and Gradle build tool are not installed here.
