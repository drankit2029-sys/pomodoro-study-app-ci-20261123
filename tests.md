# Pomodoro — Play Store Readiness Audit, Test Matrix & Screenshot Tracker

App: `com.studyapp.pomodoro` · versionName `1.0.4` · versionCode `6`
minSdk **26** · targetSdk **36** · compileSdk **36** · Kotlin 2.2.10 · AGP 9.2.1 · Compose BOM 2025.10.01

This file is the single source of truth for the audit, the test matrix, the screenshot
matrix, and the execution log. It is updated by the orchestrator after every run.

---

## 1. Feature inventory (what must be tested and shown)

| # | Feature | What it does | Key entry points |
|---|---------|--------------|------------------|
| 1 | **Timer core** | Focus / Short break / Long break, start, pause, resume, reset, +/− duration steppers, double-tap custom duration (presets + free input), 4 session dots, progress ring, live status line, keep-screen-on | Ring, phase tabs, Reset |
| 2 | **Custom durations** | Focus 1–180 min, Short 1–90, Long 1–120; step 5; presets 15/25/45/60/90 (focus), 5/10/15/20 (short), 15/20/30/45 (long) | Double-tap clock |
| 3 | **Flow Overtime** | After a focus session, keep counting up; every 5 min earns +1 min break (cap +15); pause/resume; finish flow starts the adaptive break | Completion dialog → Keep flowing |
| 4 | **Daily Rhythm & Ledger** | Today sessions / minutes / goal, 4-session goal bar, 30-day heatmap, tap-day detail (sessions, minutes, flow overtime, tasks), 365-day archive with month cards, streak badge, reset today | Ledger card |
| 5 | **CSV export** | Saves `pomodoro_study_ledger_<date>.csv` to Downloads (API 29+) and opens the share sheet via FileProvider | CSV chip |
| 6 | **Subjects** | 6 built-in tags, custom subject (22 chars, 6-colour palette), select, remove, proportional breakdown bar + percentages, 30-day / 365-day window | Study Subjects row |
| 7 | **Tasks** | Today's checklist, add (60 chars, estimate 1/2/3/4/6/12), select active, complete/uncomplete, delete, clear completed, auto-increment pomodoros on focus completion, auto-complete at estimate, per-day archive, midnight reset | Today's Tasks card |
| 8 | **Focus Mode** | On/off, blocking modes Off / Block apps / Strict, per-app blocklist picker (search, suggest, clear, save, categories), block during breaks, Advanced Focus lock (focus toggle + reset + phase locked; play/pause allowed), permission disclosure, accessibility service | Focus pill |
| 9 | **Blocked screen** | Calm "app is paused" screen, time left, Back to focus, one 60-second emergency pass per session, 3-second hold to end session | Opening a blocked app |
| 10 | **Strict lockdown** | Consumes Back/Home/Recents/notification shade, pulls Pomodoro back, exempts dialer/emergency/permission controller/IME/launcher/ads | Strict mode |
| 11 | **Music / soundscapes** | 11 procedural soundscapes + custom track (device storage or inbuilt tones), on/off, preview, volume 5–100 %, audio focus, loops during focus, stops for calls/alarm | Music pill |
| 12 | **Alarm** | Enable, sound picker (default / silent / custom ringtone), vibration, ring duration 5/10/15/30/60/120 s, preview/stop, DND override | Alarm icon |
| 13 | **Notifications** | Ongoing timer notification (phase, clock, status, progress, Pause/Resume/Reset), completion notification (Start next / Keep flowing / Silence), channels, Android 13 permission | Start timer |
| 14 | **Home-screen widget** | Phase + subject, clock, today summary, progress, Start/Pause/Reset, Advanced Focus lock handling | Widget picker |
| 15 | **Launcher shortcuts** | Dynamic Start focus / Pause-Resume / Reset shortcuts | Long-press app icon |
| 16 | **Themes** | Sanctuary, Focus, Advanced Focus ambient morphs, botanical branch, session dots | Focus pill / Advanced Focus |
| 17 | **Responsive layouts** | Compact portrait, phone portrait, phone landscape two-pane, foldable square, tablet portrait, tablet landscape, large tablet; edge-to-edge, insets, IME | Any device |
| 18 | **Ads & consent** | UMP consent, privacy choices, privacy policy, completion banner/native ad, post-focus interstitial, no ads during timer/notification/widget, debug test IDs | Completion / Alarm settings |
| 19 | **Persistence** | Settings, deadline, flow anchor, history, subjects, tasks, alarm, music, focus settings; restore after process death/reboot; midnight rollover | Relaunch |
| 20 | **Accessibility** | Content descriptions, roles, selected states, 48 dp touch targets, RTL, font scale | TalkBack / large text |
| 21 | **API compatibility** | minSdk 26 → target 36; notification permission 33+, FGS `specialUse` 34+, edge-to-edge 35+, predictive back | Every API level |

---

## 2. Device / layout matrix

The app has five layout branches in `PomodoroScreen.kt`:

- `useTabletLandscape` — min dimension ≥ 600 dp, landscape, aspect ≥ 1.22
- `useTabletPortrait` — min dimension ≥ 600 dp, portrait, aspect ≥ 1.22
- `useFoldableSquare` — min dimension ≥ 540 dp, aspect < 1.22
- `useTwoPane` — landscape, width ≥ 520 dp, height ≥ 280 dp, not tablet/foldable
- `PortraitContent` — everything else (`compact` when width < 390 dp portrait or height < 440 dp landscape)

| Profile | Resolution (px) | Density | dp | Branch | Screenshot class |
|---------|-----------------|---------|----|--------|------------------|
| `small_phone_portrait` | 480×800 | 240 | 320×533 | PortraitContent (compact) | compact |
| `compact_portrait` | 720×1280 | 320 | 360×640 | PortraitContent (compact) | compact |
| `phone_portrait` | 1080×1920 | 420 | 411×731 | PortraitContent | normal phone |
| `phone_landscape` | 1920×1080 | 420 | 731×411 | LandscapeContent (compact) | normal phone |
| `large_phone_landscape` | 2400×1080 | 420 | 914×411 | LandscapeContent (compact) | normal phone |
| `foldable_square` | 1200×1200 | 320 | 600×600 | FoldableSquareContent | extra |
| `tablet7_portrait` | 600×1024 | 160 | 600×1024 | TabletPortraitContent | 7-inch |
| `tablet7_landscape` | 1024×600 | 160 | 1024×600 | TabletLandscapeContent | 7-inch |
| `tablet10_portrait` | 800×1280 | 160 | 800×1280 | TabletPortraitContent | 10-inch |
| `tablet10_landscape` | 1280×800 | 160 | 1280×800 | TabletLandscapeContent | 10-inch |
| `tablet_large_portrait` | 1600×2560 | 240 | 1067×1707 | TabletPortraitContent | extra |
| `tablet_large_landscape` | 2560×1600 | 240 | 1707×1067 | TabletLandscapeContent | extra |

**Play Store screenshot sizes produced**

- Compact: **720×1280** (9:16)
- Normal phone: **1080×1920** (9:16)
- 7-inch tablet: **1024×600** landscape + **600×1024** portrait
- 10-inch tablet: **1280×800** landscape + **800×1280** portrait

---

## 3. API level matrix

| API | Android | Emulator image | GitHub Actions | VM AVD |
|-----|---------|----------------|----------------|--------|
| 26 | 8.0 | `google_apis;x86` | ✅ | `k3d-api26` |
| 27 | 8.1 | `google_apis;x86` | ✅ | — |
| 28 | 9 | `google_apis;x86_64` | ✅ | `k3d-api28` |
| 29 | 10 | `google_apis;x86_64` | ✅ | — |
| 30 | 11 | `google_apis;x86_64` | ✅ | `k3d-api30` |
| 31 | 12 | `google_apis;x86_64` | ✅ | — |
| 32 | 12L | `google_apis;x86_64` | ✅ | — |
| 33 | 13 | `google_apis;x86_64` | ✅ | `k3d-api33` |
| 34 | 14 | `google_apis;x86_64` | ✅ | — |
| 35 | 15 | `google_apis;x86_64` | ✅ | `k3d-api35` |
| 36 | 16 | `google_apis;x86_64` | ✅ | `k3d-api36` |

---

## 4. Test suites

| Suite | Type | Location | Purpose |
|-------|------|----------|---------|
| `PomodoroDayTest` | JVM | `app/src/test/.../PomodoroDayTest.kt` | Day rollover, streak, adaptive break, ledger codec |
| `RhythmLedgerTest` | JVM | `app/src/test/.../RhythmLedgerTest.kt` | Focus/Advanced Focus state machine, ledger, subjects, CSV, flow |
| `BlockPolicyTest` | JVM | `app/src/test/.../BlockPolicyTest.kt` | Blocklist codec + policy |
| `RuthlessEdgeCaseTest` | JVM | `app/src/test/.../RuthlessEdgeCaseTest.kt` | Bounds, malformed input, overflow, unicode, DST, empty states |
| `FeatureCoverageTest` | Instrumented | `app/src/androidTest/.../FeatureCoverageTest.kt` | Happy-path feature coverage on every API/device |
| `RuthlessFeatureTest` | Instrumented | `app/src/androidTest/.../RuthlessFeatureTest.kt` | Limits, rapid actions, persistence, permissions, notifications, shortcuts, widget |
| `LayoutIntegrityTest` | Instrumented | `app/src/androidTest/.../LayoutIntegrityTest.kt` | Bounds/clipping/overlap, dialogs, IME, font scale |
| `PlayStoreScreenshotTest` | Instrumented | `app/src/androidTest/.../PlayStoreScreenshotTest.kt` | Captures every feature for the 4 Play Store classes |
| `FocusThemeVideoTest` | Instrumented | `app/src/androidTest/.../FocusThemeVideoTest.kt` | Holds theme for screen recording |

---

## 5. Execution log

Status legend: ⏳ pending · 🟡 running · ✅ passed · ❌ failed · ⚠️ passed with finding

| Run | Scope | Environment | Status | Notes |
|-----|-------|-------------|--------|-------|
| A1 | JVM unit tests + lint | VM | ⏳ | |
| A2 | Debug build | VM | ⏳ | |
| B1 | FeatureCoverageTest | VM API 26/28/30/33/35/36 | ⏳ | |
| B2 | RuthlessFeatureTest | VM API 26/28/30/33/35/36 | ⏳ | |
| B3 | LayoutIntegrityTest | VM 12 profiles × 6 APIs | ⏳ | |
| C1 | API matrix 26–36 | GitHub Actions | ⏳ | |
| C2 | Device/orientation matrix | GitHub Actions | ⏳ | |
| D1 | Play Store screenshots | VM 4 classes | ⏳ | |
| D2 | All-feature screenshots | VM 4 classes | ⏳ | |
| E1 | Manual visual review | Orchestrator | ⏳ | |

---

## 6. Findings / risks

_Updated as tests run._

- **Strict mode idle lock (suspected UX inconsistency):** `isFocusExitBlocked` is true whenever Focus Mode is on with `STRICT`, even before a timer starts. The UI copy says leaving is blocked "until the session ends". Ruthless test `strictModeIdleNavigation` documents the actual behaviour; decide whether to gate strict blocking on `isTimerSessionActive`.
- **Accessibility permission on CI:** `MainActivity.onResume` clears Focus Mode when Accessibility is not granted. Instrumented focus tests must enable the service via `settings put secure enabled_accessibility_services` first.
- **Ads on emulators:** debug builds use Google test ad units; UMP may show a consent form on first launch. Screenshot runs dismiss it and use the no-ad path.
- **API 26/27 emulator images:** x86 only; GitHub Actions must use `arch: x86` for those levels.
- **`local.properties` contains production AdMob IDs and keystore passwords** — it is git-ignored and must never be pushed to the temporary public repo.

---

## 7. Screenshot catalogue

Each class captures the same feature set (names are stable across classes):

1. `01_home_idle` — sanctuary home, idle 25:00
2. `02_timer_running` — focus running
3. `03_timer_paused` — paused / Resume
4. `04_focus_mode` — Focus theme
5. `05_advanced_focus` — Advanced Focus theme
6. `06_advanced_focus_locked` — locked running timer
7. `07_focus_menu` — blocking modes
8. `08_blocked_apps` — blocklist picker
9. `09_music_menu` — soundscapes + volume
10. `10_rhythm_ledger` — 30-day heatmap
11. `11_ledger_year` — 365-day archive
12. `12_day_detail` — day detail panel
13. `13_subjects` — subject tags + custom subject form
14. `14_tasks` — today's tasks
15. `15_task_input` — add-task form
16. `16_completion` — focus complete dialog
17. `17_flow_overtime` — flow overtime
18. `18_alarm_settings` — alarm dialog
19. `19_privacy_policy` — privacy policy
20. `20_notification` — ongoing notification shade
21. `21_landscape` — landscape layout
22. `22_landscape_ledger` — landscape ledger/tasks
23. `23_permission_disclosure` — accessibility disclosure
24. `24_blocked_screen` — blocked-app screen
25. `25_widget` — home-screen widget (script-assisted)
26. `26_shortcuts` — launcher shortcuts (script-assisted)
27. `27_csv_export` — share sheet (script-assisted)
28. `28_rtl` — Arabic RTL (script-assisted)
29. `29_font_scale_2x` — 200 % font scale (script-assisted)

Play Store selection (8 per class): `01`, `02`, `04`, `09`, `10`, `14`, `16`, `21` (tablets use the tablet-native captures).

---

## 8. Reproducing

```bash
# JVM + lint
./gradlew :app:testDebugUnitTest :app:lintDebug

# One instrumented suite on the connected emulator
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.studyapp.pomodoro.FeatureCoverageTest

# Full local device matrix (VM)
scripts/run-device-matrix.sh

# Play Store screenshots (VM)
scripts/capture-playstore.sh
```

GitHub Actions workflows live in `.github/workflows/`:
`unit-tests.yml`, `api-matrix.yml`, `device-matrix.yml`, `screenshots.yml`.
