#!/usr/bin/env bash
# Playback and network-failure lines from the phone's logcat for the last N minutes (default 10).
#
#   scripts/offline/android-log.sh [minutes]
#   ANDROID_SERIAL=<serial> scripts/offline/android-log.sh 30   # pick the device when several are connected
#   FULL=1 scripts/offline/android-log.sh                       # every line of the app, not just the filtered ones
#
# Reads the buffer only (logcat -d); it never clears it — old incidents are found there by time.
set -euo pipefail

minutes="${1:-10}"
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    ADB=("$ADB" -s "$ANDROID_SERIAL")
else
    count="$("$ADB" devices | awk 'NR > 1 && $2 == "device"' | wc -l)"
    if (( count > 1 )); then
        echo "Several devices are connected; set ANDROID_SERIAL:" >&2
        "$ADB" devices -l >&2
        exit 1
    fi
    ADB=("$ADB")
fi

# The device clock decides what "the last N minutes" means, not this computer's.
now="$("${ADB[@]}" shell date +%s | tr -d '\r')"
since="$(date -d "@$(( now - minutes * 60 ))" '+%m-%d %H:%M:%S.000')"

if [[ -n "${FULL:-}" ]]; then
    pid="$("${ADB[@]}" shell pidof com.maxrave.simpmusic | tr -d '\r' || true)"
    "${ADB[@]}" logcat -d -v threadtime -T "$since" | { if [[ -n "$pid" ]]; then grep -E "^\S+ \S+ +$pid "; else cat; fi; }
else
    "${ADB[@]}" logcat -d -v threadtime -T "$since" |
        grep -E ' (Offline|Stream|Player Error|Merging Media Source) *:|FATAL EXCEPTION|ANR in'
fi
