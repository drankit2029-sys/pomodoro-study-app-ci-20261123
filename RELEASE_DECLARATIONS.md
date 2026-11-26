# Play Console declaration changes for the 2.0.0 major release

The app already had declarations for v1.0.x. For v2.0.0 (versionCode 7) update/confirm the following in Play Console. These are **Play Console declarations**, not code.

## 1. Data safety (Grow users → Data safety)
Keep the existing declaration, but confirm it still matches:
- **Data collected/shared:** Device or other IDs (Advertising ID), Approximate location (IP-derived, AdMob), App activity (ad views/clicks), App info/performance (SDK diagnostics) — via Google AdMob + UMP.
- **Data stored locally, not collected:** study sessions, focused minutes, 30/365-day ledger, subject tags, daily tasks, alarm/music settings (SharedPreferences only).
- **Encryption in transit:** yes (HTTPS/TLS for ad SDK traffic).
- **Deletion:** in-app data is local; user can clear app data. No account, no server.
- No new data types were added in v2.0.0 — the new features (flow overtime, adaptive breaks, task ledger, desktop/XR layouts) are all local.

## 2. Ads declaration
- Declare **Ads: yes** — Google AdMob banner on the completion screen + interstitial on Focus→break transition.
- Confirm the **production AdMob app ID / ad unit IDs** in the release AAB (verified during release audit; see `RELEASE_AUDIT.md`).
- Debug builds use Google public test IDs and are never uploaded.

## 3. Accessibility API declaration
- Keep the prominent-disclosure declaration for `FocusLockAccessibilityService`.
- Confirm `canRetrieveWindowContent="false"`, no screen-content/keystroke reading, no personal data collection, one-line justification in the Play Console "Accessibility API" declaration. The blocklist and Strict lock behaviour is unchanged.

## 4. Permissions declaration
- `POST_NOTIFICATIONS` (runtime, 33+) — ongoing timer + completion alerts.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` with the existing special-use justification: user-started focus/break countdown with live ongoing notification.
- `WAKE_LOCK` (capped at 2 h) for timer accuracy; `VIBRATE` for alarm; `REORDER_TASKS` to return to the timer from the block screen.
- `INTERNET` / `ACCESS_NETWORK_STATE` for AdMob + UMP.
- No new permissions in v2.0.0.

## 5. Privacy policy
- Existing URL: https://drankit2029-sys.github.io/pomodoro-privacy/
- Update the policy text to mention the new local-only features (Flow Overtime credits, adaptive break bonus, per-day task archive, 365-day archive, desktop/foldable/XR layouts) and that no new data is collected. Keep the AdMob/UMP and Accessibility clauses.

## 6. Store listing / graphics
- Add the new screenshot sets: **Phone, Foldable, 7-inch tablet, 10-inch tablet, Laptop/desktop, Android XR** (see `PLAY_STORE_REQUIREMENTS.md`).
- Foldables become mandatory for updates from April 2027 — include them now.
- Update the app description to mention: Flow Overtime with adaptive breaks, 365-day ledger, custom subjects, today's task checklist, Focus Mode with per-app blocklist + Advanced Focus, 11 ambient soundscapes, alarm, widget, shortcuts, and large-screen/foldable/desktop/XR adaptive layouts.
- Add alt text to every screenshot.
- Release notes for 2.0.0: summarise the above and the stability fixes (foreground-service race, focus/duration fixes).

## 7. Target API & large-screen compliance
- Current target SDK 36; Play will require API 37 from August 2027 — plan the next update.
- No `screenOrientation` restrictions; `resizeableActivity=true` — compliant with the large-screen/foldable orientation/resizability rules.
- Tier 3 adaptive quality: the app uses WindowSizeClass-style branches (compact, phone, foldable square, tablet portrait/landscape) — verify no letterboxing in the new screenshots.

## 8. Signing / upload key
- The old local release certificate ("Pomodoro Local Test") keystore was not present on disk. A **new upload key** was generated for this build (`release-keys/pomodoro-upload.jks`).
- **If the previous release was published with a different upload key**, reset the upload key in Play Console (App signing → Upload key certificate → request reset) or restore the original JKS before uploading this AAB. If the app uses Play App Signing and you cannot reset, this AAB cannot be uploaded as an update.
