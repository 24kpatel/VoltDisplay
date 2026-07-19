# Volt Display

Volt Display is a Kotlin Android kiosk browser for Volt Raceway display devices.

## Current milestone

- Android SDK 35
- Kotlin and Material 3
- Landscape-only full-screen WebView
- Keeps the screen awake
- Loads `http://192.168.152.236:5050/`
- Hides status and navigation bars
- Shows a recoverable connection-error screen
- Retries automatically every 10 seconds
- Launches after Android boot where the device permits background launches
- GitHub Actions builds debug and release APK artifacts
- Optional release signing through repository secrets

## Build requirements

- JDK 17
- Gradle 8.9
- Android SDK 35

Open the project in Android Studio or run:

```bash
gradle :app:assembleDebug
gradle :app:assembleRelease
```

## Release signing secrets

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Never commit a keystore or passwords to this public repository.

## Next milestone

- PIN-protected settings
- Saved URL and restore-default controls
- Test URL
- Auto-refresh setting
- Device and network information
