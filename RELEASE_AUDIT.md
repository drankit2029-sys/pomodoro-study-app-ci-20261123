# Release audit — v2.0.0 (versionCode 7), 2026-11-26

## Build & signing
- Built on the Oracle VM with JDK 21 / SDK 36 / AGP 9.2.1 / Gradle 9.4.1.
- `:app:bundleRelease :app:assembleRelease` → **BUILD SUCCESSFUL** (56 tasks executed).
- AAB: `release/app-release-2.0.0.aab` (8,436,043 bytes).
- APK: `release/app-release-2.0.0.apk` (4,380,746 bytes).
- R8 mapping: `release/mapping/mapping.txt` (+ configuration/seeds/usage/resources).
- SHA-256 checksums: `release/SHA256SUMS.txt`.
- APK signer: `CN=Pomodoro, OU=Study App, O=Nexdert, L=New Delhi, ST=Delhi, C=IN`, SHA-256 `61f72838dafb1d4519efd580cfcb083c096fc66c0cd9dba48a789e4ab8688407`.
- **Caveat:** the previous local release certificate ("Pomodoro Local Test", SHA-256 `4e04c57c…`) keystore was not present anywhere on PC or VM. A new upload key was generated. If the published app used a different upload key, reset the upload key in Play Console (App signing) or restore the original JKS before uploading. See `RELEASE_DECLARATIONS.md`.

## Production AdMob IDs — verified in built artifacts
From generated `BuildConfig.java` (release):
- `ADMOB_BANNER_UNIT_ID = ca-app-pub-6974413429688784/2414906073`
- `ADMOB_NATIVE_UNIT_ID = ca-app-pub-6974413429688784/2414906073`
- `ADMOB_INTERSTITIAL_VIDEO_UNIT_ID = ca-app-pub-6974413429688784/3285692123`
- `NATIVE_ADS_ENABLED = true`

From AAB/APK AndroidManifest.xml:
- `com.google.android.gms.ads.APPLICATION_ID = ca-app-pub-6974413429688784~8336928581`

Google test IDs found in the dex (`ca-app-pub-3940256099942544/...`, `ca-app-pub-0000…`) are embedded by the `play-services-ads` SDK itself and are **not** the values used by this app at runtime. Debug builds override to test IDs and are never uploaded.

## Device install & smoke audit
- Installed `app-release-2.0.0.apk` on physical phone `RZCY61Z8TPD` (Galaxy M36 5G, Android 17, 1080×2340): **Success**.
- `dumpsys package`: versionCode=7, versionName=2.0.0, minSdk=26, targetSdk=36.
- Launched MainActivity; `topResumedActivity` = `com.studyapp.pomodoro/.MainActivity`.
- UI dump contains all core elements: Pomodoro header, Start focus, Reset, Focus, Music, Short/Long tabs, Daily Rhythm & Ledger, 30-day ledger, CSV, 365D, subject/task summary.
- Logcat: no FATAL / ANR / crash during launch and UI dump.

## Known-good test baseline (from the QA campaign)
- JVM: 106/106 tests, lint 0 errors.
- API 36 phone: FeatureCoverage 15/15, Ruthless 31/31, LayoutIntegrity 5/5.
- VM device matrix: 39 unique green cells across API 26/28/30/33/35 and all 12 layout profiles (all cells green after retry).
- Foreground-service race and mid-session duration-stepper bugs fixed in this build.

## Other checks
- Manifest: `resizeableActivity=true`, no `screenOrientation` lock, no restrictive `<uses-feature>` → compatible with large screens, foldables, desktop and Android XR as a 2D app.
- Permissions unchanged from previous release (no new dangerous permissions).
- No production secrets in the public CI repo; `local.properties` and keystore are git-ignored.
