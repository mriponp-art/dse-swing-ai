# DSE Swing AI — Mobile-Only Real DSE Data Layer v1

This release moves the first live-data fetch into the Android layer so the app does not require a PC, VPS, or browser CORS workaround.

## Data path

Android native HTTP client → unofficial structured DSE proxy (`bdstock.org`) → source crawls DSE → normalized rows → app UI.

The source is intentionally labeled `UNOFFICIAL_DSE_PROXY`. The app never invents prices. A failed/empty response becomes `ERROR` or `INSUFFICIENT_DATA`.

## What is implemented

- Native Android HTTPS fetch
- Structured JSON parsing
- Symbol/LTP/high/low/close/volume normalization
- Source and freshness metadata
- Explicit failure states
- JavaScript bridge for the existing PWA UI
- In-app **Test Live Data** control
- No PC/VPS dependency for the first data test

## Important data-governance rule

This release does **not** treat the unofficial proxy as authoritative truth. It is an integration test/source adapter only. Before trade approval, the app should require fresh data and a successful validation pass.

## Build

Open `android/` in Android Studio and build a debug APK. A release/signed APK/AAB should be produced only after the live-data adapter is tested on an actual Android device.

The current environment does not contain a usable Android SDK/Gradle toolchain, so APK compilation was not performed here.
