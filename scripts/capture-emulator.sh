#!/usr/bin/env bash
set -euo pipefail
# Capture the CURRENT screen of one explicitly selected emulator. No accounts or navigation automated.
if [[ $# -ne 2 || ! "$1" =~ ^emulator-[0-9]+$ || ! "$2" =~ ^[a-z0-9-]+$ ]]; then
  echo 'Usage: scripts/capture-emulator.sh emulator-5554 screen-state-label' >&2
  exit 2
fi
command -v adb >/dev/null
test "$(adb -s "$1" get-state)" = device
mkdir -p captures
adb -s "$1" exec-out screencap -p > "captures/$2.png"
test -s "captures/$2.png"
echo "Captured captures/$2.png; inspect visually before recording a pass."
