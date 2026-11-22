# Pomodoro Study App — CI, test matrix & Play Store screenshots

Temporary public CI mirror of the Pomodoro study app (`com.studyapp.pomodoro`).

- `tests.md` — feature audit, device/API matrix, execution log, findings.
- `.github/workflows/unit-tests.yml` — JVM unit tests + lint.
- `.github/workflows/api-matrix.yml` — instrumented suites on API 26–36.
- `.github/workflows/device-matrix.yml` — compact / phone landscape / foldable / 7" / 10" profiles.
- `.github/workflows/screenshots.yml` — Play Store screenshot capture.
- `scripts/run-device-matrix.sh` — Oracle VM device/orientation matrix.
- `scripts/capture-playstore.sh` — Oracle VM Play Store capture.

No production AdMob IDs, keystores, or signing secrets are included. Debug builds use
Google's public test ad units.
