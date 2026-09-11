# DSE Swing AI — Android Build + Live Backend Package

This package contains:
- Android WebView wrapper project
- DSE Swing AI v3 PWA assets
- Production backend deployment files

## Important
The current runtime cannot compile an APK because Android SDK/Gradle binaries are not installed and outbound package download is blocked.

To build on a machine with Android Studio/SDK:
1. Open `android/` in Android Studio.
2. Sync Gradle.
3. Build > Build APK(s) for a debug APK or Build > Generate Signed App Bundle/APK for release.

The app expects the backend base URL in browser localStorage key `dse_api` and calls `/api/v1/...` endpoints.
Use HTTPS for production.
