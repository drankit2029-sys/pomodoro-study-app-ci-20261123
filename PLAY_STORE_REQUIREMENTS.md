# Play Store preview assets — exact requirements (verified 2026-11-26)

Sources:
- https://support.google.com/googleplay/android-developer/answer/9866151 (Add preview assets)
- https://developer.android.com/google/play/requirements/screenshots-android-cli (Capture app screenshots, updated 2026-11-18)
- https://developer.android.com/blog/posts/google-play-adaptive-quality-standards (2026-11-18)

## Screenshot categories and exact specs

| Play Console section | Count | Aspect / bounds | Capture resolution used |
|---|---|---|---|
| Phone | 2–8 (4+ at 1080p+ recommended) | 9:16 portrait or 16:9 landscape; 320–3840 px per side; max long side ≤ 2.3 × short side | **1080×1920** portrait, **1920×1080** landscape |
| Foldable — 1:1 unfolded inner | ≥1 when uploading foldables (3–8 total) | Exact **1:1**, 960–3840 px | **2106×2106** |
| Foldable — 4:3 unfolded inner (landscape) | ≥1 | Exact **4:3** (427–3840 px width) | **2688×2016** |
| Foldable — 4:3 folded outer (portrait) | ≥1 | Exact **10:16** (320–2400 px width) | **1260×2016** |
| Foldable — folded landscape (optional, up to 5 extra) | optional | Exact **16:10** | **2016×1260** |
| 7-inch tablet | min 4, up to 8 | 16:9 or 9:16; 1080–3840 px per side | **1920×1080** landscape + **1080×1920** portrait |
| 10-inch tablet | min 4, up to 8 | 16:9 or 9:16; 1080–3840 px per side | **1920×1080** landscape + **1080×1920** portrait |
| Laptop / desktop (Chromebook) | 4–8 | Exact 16:9 (1920–7680 px width) or 9:16 (1080–4320 px width) | **1920×1080** landscape |
| Android XR | 4–8 | Exact **8:5**; 1920×1200 min, **3840×2400** recommended; ≤8 MB | **3840×2400** |

## General image rules
- JPEG or 24-bit PNG (no alpha), ≤ 8 MB each.
- Min dimension 320 px, max 3840 px (tablets/desktop guidance allows up to 7680 px, but general cap is 3840).
- Max long side ≤ 2.3 × short side for phone/tablet.
- Must show real in-app experience; no device frames, no store badges, no "Best/#1/New/Discount", no CTAs.
- Taglines ≤ 20 % of image. Hide/exclude notification-bar content that is not part of the app.
- Include alt text (≤140 chars) per screenshot.
- Starting April 2027, 3 foldable screenshots are mandatory for updates; from August 2027 target API 37 required.
- Android XR screenshots are required if the listing targets XR devices.

## Notes for this app
- `minSdk 26`, `targetSdk 36`, `resizeableActivity=true`, no orientation lock, no `screenOrientation` in manifest — compatible with large screens/foldables/desktop/XR as a 2D app.
- Desktop and XR captures are rendered at the exact required aspect ratio using `wm size` on an emulator; Play Console validates dimensions/aspect, not the device profile name.
- Capture script: `scripts/capture-store-screenshots.sh` on the Oracle VM.
