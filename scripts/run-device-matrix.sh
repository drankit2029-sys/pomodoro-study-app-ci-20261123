#!/usr/bin/env bash
# Study-App device/orientation matrix runner (Oracle VM).
#
# Runs the instrumented suites on every supported API level and every layout branch.
# Each API gets its own emulator; profiles are applied with `wm size` + `wm density` +
# `user_rotation`, so one emulator covers all resolutions and both orientations.
#
# Usage:
#   scripts/run-device-matrix.sh
#   APIS="26 30 36" PARALLEL=2 scripts/run-device-matrix.sh
#   SUITES="com.studyapp.pomodoro.LayoutIntegrityTest" scripts/run-device-matrix.sh
#
# Output: test-results/device-matrix/<api>/<profile>/instrumentation.txt + summary.tsv

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export ANDROID_HOME="${ANDROID_HOME:-/home/ubuntu/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export JAVA_HOME="${JAVA_HOME:-/opt/jdk-21}"
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
AVD_PREFIX="${AVD_PREFIX:-k3d-api}"

APIS="${APIS:-26 28 30 33 35 36}"
PARALLEL="${PARALLEL:-2}"
PORT_BASE="${PORT_BASE:-5600}"
OUT="${OUT:-test-results/device-matrix}"
SUITES="${SUITES:-com.studyapp.pomodoro.FeatureCoverageTest,com.studyapp.pomodoro.RuthlessFeatureTest}"
LONG_RUNNING="${LONG_RUNNING:-false}"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-240}"
TEST_TIMEOUT="${TEST_TIMEOUT:-1800}"

# name:width:height:density:rotation
PROFILES="${PROFILES:-\
small_phone_portrait:480:800:240:0 \
compact_portrait:720:1280:320:0 \
phone_portrait:1080:1920:420:0 \
phone_landscape:1920:1080:420:1 \
large_phone_landscape:2400:1080:420:1 \
foldable_square:1200:1200:320:0 \
tablet7_portrait:600:1024:160:0 \
tablet7_landscape:1024:600:160:1 \
tablet10_portrait:800:1280:160:0 \
tablet10_landscape:1280:800:160:1 \
tablet_large_portrait:1600:2560:240:0 \
tablet_large_landscape:2560:1600:240:1}"

mkdir -p "$OUT"
SUMMARY="$OUT/summary.tsv"
printf 'api\tprofile\tstatus\ttests\tfailures\tlog\n' > "$SUMMARY"

log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*"; }

claim() {
    local until
    until="$(date -u -d '+4 hours' +%H:%M 2>/dev/null || date -u +%H:%M)"
    echo "[STUDYAPP] CLAIM emulators ${PORT_BASE}-$((PORT_BASE + 40)) for Study-App device matrix until ${until} UTC" \
        >> /home/ubuntu/talk.txt 2>/dev/null || true
}

release() {
    echo "[STUDYAPP] RELEASE emulators ${PORT_BASE}-$((PORT_BASE + 40)) — Study-App device matrix complete" \
        >> /home/ubuntu/talk.txt 2>/dev/null || true
}

wait_for_boot() {
    local serial="$1" deadline=$((SECONDS + BOOT_TIMEOUT))
    "$ADB" -s "$serial" wait-for-device >/dev/null 2>&1 || return 1
    while [ "$SECONDS" -lt "$deadline" ]; do
        local boot
        boot="$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
        if [ "$boot" = "1" ]; then
            "$ADB" -s "$serial" shell input keyevent 82 >/dev/null 2>&1 || true
            return 0
        fi
        sleep 3
    done
    return 1
}

run_profile() {
    local api="$1" serial="$2" profile="$3"
    local name width height density rotation
    IFS=':' read -r name width height density rotation <<< "$profile"
    local dir="$OUT/api$api/$name"
    mkdir -p "$dir"

    log "API $api / $name (${width}x${height} @ ${density}dpi, rotation $rotation)"
    "$ADB" -s "$serial" shell wm size "${width}x${height}" >/dev/null 2>&1 || true
    "$ADB" -s "$serial" shell wm density "$density" >/dev/null 2>&1 || true
    "$ADB" -s "$serial" shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
    "$ADB" -s "$serial" shell settings put system user_rotation "$rotation" >/dev/null 2>&1 || true
    "$ADB" -s "$serial" shell am force-stop com.studyapp.pomodoro >/dev/null 2>&1 || true
    "$ADB" -s "$serial" shell pm clear com.studyapp.pomodoro >/dev/null 2>&1 || true
    sleep 2

    local logfile="$dir/instrumentation.txt"
    timeout "$TEST_TIMEOUT" "$ADB" -s "$serial" shell am instrument -w -r \
        -e class "$SUITES" \
        -e longRunning "$LONG_RUNNING" \
        com.studyapp.pomodoro.test/androidx.test.runner.AndroidJUnitRunner \
        > "$logfile" 2>&1
    local rc=$?

    local tests failures status
    tests="$(grep -Eo 'OK \([0-9]+ tests?\)' "$logfile" | tail -1 | grep -Eo '[0-9]+' || true)"
    failures="$(grep -Eo 'FAILURES!!! Tests run: [0-9]+,  Failures: [0-9]+' "$logfile" | tail -1 | grep -Eo 'Failures: [0-9]+' | grep -Eo '[0-9]+' || true)"
    if [ "$rc" -eq 0 ] && grep -q 'OK (' "$logfile"; then
        status="PASS"
    elif [ "$rc" -eq 124 ]; then
        status="TIMEOUT"
    else
        status="FAIL"
    fi
    [ -z "$tests" ] && tests="?"
    [ -z "$failures" ] && failures="0"
    if [ "$status" != "PASS" ]; then
        "$ADB" -s "$serial" logcat -d -v brief > "$dir/logcat.txt" 2>&1 || true
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$api" "$name" "$status" "$tests" "$failures" "$logfile" >> "$SUMMARY"
    log "API $api / $name -> $status (tests=$tests failures=$failures)"
}

run_api() {
    local api="$1" port="$2"
    local avd="${AVD_PREFIX}${api}"
    # API 36's k3d AVD is used by another agent; use a dedicated copy.
    if [ "$api" = "36" ] && "$EMULATOR" -list-avds | grep -qx "study-api36"; then
        avd="study-api36"
    fi
    local serial="emulator-$port"
    local dir="$OUT/api$api"
    mkdir -p "$dir"

    if ! "$EMULATOR" -list-avds | grep -qx "$avd"; then
        log "SKIP API $api: AVD $avd not found"
        printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$api" "-" "SKIP" "0" "0" "-" >> "$SUMMARY"
        return
    fi

    log "Starting $avd on $serial"
    nice -n 10 "$EMULATOR" -avd "$avd" -port "$port" \
        -no-window -no-audio -no-boot-anim -no-snapshot -read-only \
        -gpu swiftshader_indirect -camera-back none -camera-front none \
        -memory 2048 -cores 2 -no-metrics \
        > "$dir/emulator.log" 2>&1 &
    local emu_pid=$!

    if ! wait_for_boot "$serial"; then
        log "API $api failed to boot"
        printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$api" "-" "BOOT_FAIL" "0" "0" "$dir/emulator.log" >> "$SUMMARY"
        kill "$emu_pid" >/dev/null 2>&1 || true
        "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
        return
    fi

    "$ADB" -s "$serial" shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
    "$ADB" -s "$serial" shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
    "$ADB" -s "$serial" shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
    "$ADB" -s "$serial" shell settings put secure show_ime_with_hard_keyboard 0 >/dev/null 2>&1 || true

    local app_apk="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
    local test_apk="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    "$ADB" -s "$serial" install -r -t "$app_apk" > "$dir/install-app.txt" 2>&1 || true
    "$ADB" -s "$serial" install -r -t "$test_apk" > "$dir/install-test.txt" 2>&1 || true

    for profile in $PROFILES; do
        run_profile "$api" "$serial" "$profile"
    done

    "$ADB" -s "$serial" shell wm size reset >/dev/null 2>&1 || true
    "$ADB" -s "$serial" shell wm density reset >/dev/null 2>&1 || true
    "$ADB" -s "$serial" emu kill >/dev/null 2>&1 || true
    wait "$emu_pid" 2>/dev/null || true
    log "Stopped $serial"
}

log "Building debug + androidTest APKs"
nice -n 10 ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PnativeAdsEnabled=false --console=plain \
    > "$OUT/build.log" 2>&1
if [ $? -ne 0 ]; then
    log "Build failed; see $OUT/build.log"
    exit 1
fi

claim
trap release EXIT

running=0
for api in $APIS; do
    port=$((PORT_BASE + api))
    run_api "$api" "$port" &
    running=$((running + 1))
    if [ "$running" -ge "$PARALLEL" ]; then
        wait -n
        running=$((running - 1))
    fi
done
wait

log "Matrix complete. Summary: $SUMMARY"
column -t -s $'\t' "$SUMMARY" 2>/dev/null || cat "$SUMMARY"
