#!/usr/bin/env bash
# Study-App Play Store screenshot capture (Oracle VM).
#
# Captures the full feature catalogue for the four Play Store device classes:
#   compact (720x1280), normal phone (1080x1920), 7-inch tablet (1024x600 + 600x1024),
#   10-inch tablet (1280x800 + 800x1280).
#
# Usage:
#   scripts/capture-playstore.sh
#   API=36 scripts/capture-playstore.sh
#   PROFILES="compact phone" scripts/capture-playstore.sh
#
# Output: screenshots/<profile>/ (all features) and screenshots/playstore/<class>/ (8 curated).

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export ANDROID_HOME="${ANDROID_HOME:-/home/ubuntu/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="${JAVA_HOME:-/opt/jdk-21}"
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"

API="${API:-36}"
AVD="${AVD:-k3d-api${API}}"
PORT="${PORT:-5620}"
SERIAL="emulator-$PORT"
OUT="${OUT:-screenshots}"
PROFILES="${PROFILES:-compact phone tablet7_landscape tablet7_portrait tablet10_landscape tablet10_portrait}"

mkdir -p "$OUT"
log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*"; }

claim() {
    local until
    until="$(date -u -d '+3 hours' +%H:%M 2>/dev/null || date -u +%H:%M)"
    echo "[STUDYAPP] CLAIM emulator $SERIAL for Play Store screenshots until ${until} UTC" \
        >> /home/ubuntu/talk.txt 2>/dev/null || true
}
release() {
    echo "[STUDYAPP] RELEASE emulator $SERIAL — Play Store screenshots complete" \
        >> /home/ubuntu/talk.txt 2>/dev/null || true
}

wait_for_boot() {
    local deadline=$((SECONDS + 240))
    "$ADB" -s "$SERIAL" wait-for-device >/dev/null 2>&1 || return 1
    while [ "$SECONDS" -lt "$deadline" ]; do
        local boot
        boot="$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
        if [ "$boot" = "1" ]; then
            "$ADB" -s "$SERIAL" shell input keyevent 82 >/dev/null 2>&1 || true
            return 0
        fi
        sleep 3
    done
    return 1
}

profile_geometry() {
    case "$1" in
        compact) echo "720 1280 320 portrait" ;;
        phone) echo "1080 1920 420 portrait" ;;
        phone_landscape) echo "1920 1080 420 landscape" ;;
        tablet7_landscape) echo "1024 600 160 landscape" ;;
        tablet7_portrait) echo "600 1024 160 portrait" ;;
        tablet10_landscape) echo "1280 800 160 landscape" ;;
        tablet10_portrait) echo "800 1280 160 portrait" ;;
        *) echo "1080 1920 420 portrait" ;;
    esac
}

log "Building debug + androidTest APKs"
nice -n 10 ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain \
    > "$OUT/build.log" 2>&1
if [ $? -ne 0 ]; then
    log "Build failed; see $OUT/build.log"
    exit 1
fi

if ! "$EMULATOR" -list-avds | grep -qx "$AVD"; then
    log "AVD $AVD not found"
    exit 1
fi

claim
trap release EXIT

log "Starting $AVD on $SERIAL"
nice -n 10 "$EMULATOR" -avd "$AVD" -port "$PORT" \
    -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect -camera-back none -camera-front none \
    -memory 2048 -cores 2 -no-metrics \
    > "$OUT/emulator.log" 2>&1 &
EMU_PID=$!

if ! wait_for_boot; then
    log "Emulator failed to boot"
    kill "$EMU_PID" >/dev/null 2>&1 || true
    exit 1
fi

"$ADB" -s "$SERIAL" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" shell settings put secure show_ime_with_hard_keyboard 0 >/dev/null 2>&1 || true

APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
"$ADB" -s "$SERIAL" install -r -t "$APP_APK" > "$OUT/install-app.txt" 2>&1 || true
"$ADB" -s "$SERIAL" install -r -t "$TEST_APK" > "$OUT/install-test.txt" 2>&1 || true

for profile in $PROFILES; do
    read -r width height density orientation <<< "$(profile_geometry "$profile")"
    log "Capturing $profile (${width}x${height} @ ${density}dpi, $orientation)"
    "$ADB" -s "$SERIAL" shell wm size "${width}x${height}" >/dev/null 2>&1 || true
    "$ADB" -s "$SERIAL" shell wm density "$density" >/dev/null 2>&1 || true
    "$ADB" -s "$SERIAL" shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
    if [ "$orientation" = "landscape" ]; then
        "$ADB" -s "$SERIAL" shell settings put system user_rotation 1 >/dev/null 2>&1 || true
    else
        "$ADB" -s "$SERIAL" shell settings put system user_rotation 0 >/dev/null 2>&1 || true
    fi
    "$ADB" -s "$SERIAL" shell am force-stop com.studyapp.pomodoro >/dev/null 2>&1 || true
    "$ADB" -s "$SERIAL" shell pm clear com.studyapp.pomodoro >/dev/null 2>&1 || true
    "$ADB" -s "$SERIAL" shell rm -rf "/sdcard/Android/data/com.studyapp.pomodoro/files/screenshots" >/dev/null 2>&1 || true
    sleep 2

    timeout 1800 "$ADB" -s "$SERIAL" shell am instrument -w -r \
        -e class com.studyapp.pomodoro.PlayStoreScreenshotTest \
        -e screenshotProfile "$profile" \
        -e screenshotOrientation "$orientation" \
        com.studyapp.pomodoro.test/androidx.test.runner.AndroidJUnitRunner \
        > "$OUT/$profile-instrumentation.txt" 2>&1
    rc=$?
    if [ "$rc" -ne 0 ]; then
        log "Screenshot test for $profile exited $rc (see $OUT/$profile-instrumentation.txt)"
    fi

    mkdir -p "$OUT/$profile"
    "$ADB" -s "$SERIAL" pull \
        "/sdcard/Android/data/com.studyapp.pomodoro/files/screenshots/$profile/." \
        "$OUT/$profile/" > "$OUT/$profile-pull.txt" 2>&1 || true
    count=$(find "$OUT/$profile" -maxdepth 1 -name '*.png' | wc -l | tr -d ' ')
    log "$profile: $count screenshots"
done

"$ADB" -s "$SERIAL" shell wm size reset >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" shell wm density reset >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || true
wait "$EMU_PID" 2>/dev/null || true

# Curated Play Store sets (8 per class).
mkdir -p "$OUT/playstore/compact" "$OUT/playstore/phone" \
    "$OUT/playstore/tablet7" "$OUT/playstore/tablet10"
copy_set() {
    local src="$1" dst="$2" shift 2
    for name in "$@"; do
        if [ -f "$OUT/$src/$name.png" ]; then
            cp "$OUT/$src/$name.png" "$OUT/playstore/$dst/$name.png"
        fi
    done
}
PHONE_SET="01_home_idle 02_timer_running 04_focus_mode 09_music_menu 10_rhythm_ledger 14_tasks 16_completion 21_landscape"
TABLET_SET="01_home_idle 02_timer_running 04_focus_mode 09_music_menu 10_rhythm_ledger 14_tasks 16_completion 22_landscape_ledger"
copy_set compact compact $PHONE_SET
copy_set phone phone $PHONE_SET
copy_set tablet7_landscape tablet7 $TABLET_SET
copy_set tablet10_landscape tablet10 $TABLET_SET

log "Play Store sets:"
find "$OUT/playstore" -name '*.png' -printf '%p\n' | sort
log "Done."
