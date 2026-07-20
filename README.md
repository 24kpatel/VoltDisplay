# VoltDisplay

VoltDisplay is a lightweight, full-screen Android display app built for the U8 Android 14 device. It opens a configurable website in Android System WebView and automatically retries when the network or page is temporarily unavailable.

## First launch

1. Install and open the APK.
2. Enter the website that the screen should display.
3. Select **Save and Open**.

To reopen settings, press the remote **Menu** button or press **Back twice**. The screen remains awake and the navigation bars are hidden while the app is active.

## Design choices

- Pure Java with no Kotlin runtime, avoiding the duplicate Kotlin standard-library conflict from the earlier APK.
- Supports Android 6.0 and newer; compiled for Android API 35.
- JavaScript, DOM storage, cookies, media playback, and responsive desktop-style pages are supported.
- HTTP can be used for a trusted local display server; HTTPS should be used for internet pages.
- SSL certificate errors are not bypassed.
- A failed page retries automatically after 15 seconds.
- A crashed WebView renderer restarts the activity instead of closing the app.

## Build in GitHub

The workflow at `.github/workflows/build-android.yml` runs the unit tests and creates a debug APK after a push to `main`.

1. Open the repository's **Actions** tab.
2. Select **Build Android APK**.
3. Select **Run workflow**.
4. When it finishes, download the `VoltDisplay-debug-apk` artifact.

## Build in Android Studio

Open this folder as a project in Android Studio, allow Gradle to sync, and select **Build > Build APK(s)**.

## Install with ADB

```bash
adb install -r app-debug.apk
```

If Android blocks installation, enable installation from the file manager or ADB source being used on the device.
