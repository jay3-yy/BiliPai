#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PKG="com.android.purebilibili"
OUT_DIR="$ROOT_DIR/app/src/main/generated/baselineProfiles"
MODE="${1:-baseline}"

case "$MODE" in
  startup)
    OUT_FILE="$OUT_DIR/startup-prof.txt"
    ;;
  baseline)
    OUT_FILE="$OUT_DIR/baseline-prof.txt"
    ;;
  reset)
    adb shell cmd package compile --reset "$PKG"
    adb shell am force-stop "$PKG"
    echo "Reset ART profile & compilation state for $PKG."
    exit 0
    ;;
  *)
    echo "Usage: $0 [reset|startup|baseline]" >&2
    exit 1
    ;;
esac

mkdir -p "$OUT_DIR"

echo "Flushing ART profile for $PKG..."
adb shell cmd package dump-profiles "$PKG" >/dev/null 2>&1 || true
adb shell killall -s SIGUSR1 "$PKG" >/dev/null 2>&1 || true
sleep 2

adb root >/dev/null 2>&1 || true
sleep 1

APK_PATH="$(adb shell pm path "$PKG" | head -n 1 | cut -d: -f2 | tr -d '\r')"
if [[ -z "$APK_PATH" ]]; then
  echo "Error: $PKG is not installed on the connected device." >&2
  exit 1
fi

PROF_PATH="/data/misc/profiles/cur/0/$PKG/primary.prof"
PROF_SIZE="$(adb shell "stat -c %s '$PROF_PATH' 2>/dev/null || echo 0" | tr -d '\r')"
if [[ "$PROF_SIZE" -le 0 ]]; then
  echo "Error: $PROF_PATH is empty (size=$PROF_SIZE). Interact with the app for ~10s and retry." >&2
  exit 1
fi

TMP_FILE="$(mktemp)"
trap 'rm -f "$TMP_FILE"' EXIT

adb shell profman \
  --dump-classes-and-methods \
  --profile-file="$PROF_PATH" \
  --apk="$APK_PATH" \
  | tr -d '\r' \
  | awk 'NF > 0' \
  | sort -u > "$TMP_FILE"

LINE_COUNT="$(wc -l < "$TMP_FILE" | tr -d ' ')"
if [[ "$LINE_COUNT" -le 0 ]]; then
  echo "Error: profman produced 0 rules." >&2
  exit 1
fi

mv "$TMP_FILE" "$OUT_FILE"
chmod 644 "$OUT_FILE"
echo "Saved $LINE_COUNT HRF rules ($MODE) -> $OUT_FILE"
