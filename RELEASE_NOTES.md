# Pomodoro 2.0.0 (versionCode 7) — release notes

## Highlights
- Major release of the on-device Pomodoro study app.
- Existing features: Focus/Short/Long timer with custom durations, Flow Overtime with adaptive breaks, 30/365-day rhythm ledger, subject tags, today's task checklist, Focus Mode (per-app blocklist / Strict / Advanced Focus), 11 ambient soundscapes, alarm, ongoing/completion notifications, home-screen widget, launcher shortcuts, CSV export.
- Quality work in this release: fixed the mid-session duration stepper no-op and a foreground-service start/stop crash; hardened the app for compact phones, foldables, 7"/10" tablets, desktop and Android XR.
- All study data remains local on device; no account, no cloud. AdMob + UMP remain the only network SDKs.

## Stability / compatibility
- 106/106 JVM tests, lint clean; instrumented suites green across API 26–36 and 12 layout profiles.
- Desktop (1920×1080) and Android XR (3840×2400, 8:5) verified visually: adaptive two-pane layout, no stretching/letterboxing/crashes.
- Production AdMob IDs verified in the signed AAB/APK (see `RELEASE_AUDIT.md`).

## Signing caveat
A new upload key was generated because the previous local keystore was missing. If the published app was signed with a different upload key, reset the upload key in Play Console or restore the original JKS before uploading.

## Store assets
72 curated screenshots (8 per class) are in `store-screenshots/playstore/` and uploaded to Google Drive folder **Pomodoro 2.0.0 - Play Store Screenshots** (ID `1ZMrKOas-8VqBUaAat3LetMtdhQxrtIJN`): phone, phone landscape, foldable 1:1, foldable 4:3 unfolded, foldable 4:3 folded, 7" tablet, 10" tablet, desktop, Android XR.

See `RELEASE_DECLARATIONS.md` for Play Console declaration updates required for this release.
