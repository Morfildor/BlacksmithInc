#!/usr/bin/env bash
# Plays a passive run (End Day only) until the forge falls, then walks the run-end flow: claim, begin the next era.
# Usage: ADB=/path/to/adb bash tools/emulator/runend.sh <screenshot-dir>   (install the debug app first)
set -u
export MSYS_NO_PATHCONV=1
ADB=${ADB:-adb}
OUT="$1"; mkdir -p "$OUT"
PKG=com.example.blacksmithproject

dump() { $ADB shell rm -f /sdcard/ui.xml; $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $ADB shell cat /sdcard/ui.xml 2>/dev/null; }
has() { dump | grep -q "text=\"$1" ; }
tap() {
  local x=$(dump | python -c "
import sys,re
s=sys.stdin.read()
m=re.search(r'text=\"$1[^\"]*\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]', s)
if m:
  x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2)
")
  if [ -z "$x" ]; then echo "MISSING: $1"; return 1; fi
  $ADB shell input tap $x; echo "tapped '$1' at $x"; $ADB shell sleep 1.2
}
shot() { $ADB exec-out screencap -p > "$OUT/$1.png"; echo "screenshot $1"; }
wait_text() { for i in $(seq 1 20); do if has "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for '$1'"; return 1; }
texts() { dump | grep -o 'text="[^"]\+"' | head -12; }

$ADB shell pm clear $PKG >/dev/null
$ADB shell am start -n $PKG/.MainActivity >/dev/null
wait_text "Tiny Blacksmith" && tap "New game" || exit 1
wait_text "End Day" || exit 1
fallen=0
# Each day: End Day, Skip to evening (to the day's last card), then Begin day; the day the forge falls ends on "See the legacy".
for day in $(seq 1 160); do
  if has "See the legacy"; then fallen=1; break; fi
  if has "Begin day"; then tap "Begin day"; continue; fi
  if has "Decide later"; then tap "Decide later"; continue; fi
  if has "Skip to evening"; then tap "Skip to evening"; continue; fi
  if has "End Day"; then tap "End Day"; continue; fi
  echo "UNEXPECTED SCREEN:"; texts; shot "stuck_$day"; exit 1
done
[ $fallen = 1 ] && echo "CHECK forge fell: ok" || { echo "CHECK forge fell: FAIL"; exit 1; }
shot 00_final_gazette
tap "See the legacy" || exit 1
wait_text "Legacy reward" || exit 1
shot 01_runend_unclaimed
tap "Claim [0-9]" || exit 1
wait_text "Legacy claimed" && echo "CHECK claimed: ok" || echo "CHECK claimed: FAIL"
shot 02_runend_claimed
# Process death after the claim: the ended run stays saved, so run end reopens, claimed, with upgrades still to buy.
$ADB shell am force-stop $PKG; $ADB shell am start -n $PKG/.MainActivity >/dev/null
wait_text "Continue run" && tap "Continue run"
wait_text "Legacy claimed" && echo "CHECK run end reopens after process death: ok" || { echo "CHECK run end reopens after process death: FAIL"; texts; shot stuck_reopen; exit 1; }
upgrades=0
for i in 1 2 3 4 5 6; do
  if dump | grep -q 'content-desc="Buy [^"]*"[^>]*enabled="true"'; then upgrades=1; break; fi
  $ADB shell input swipe 540 1500 540 900 400; $ADB shell sleep 1
done
[ $upgrades = 1 ] && echo "CHECK upgrades available after reopening: ok" || echo "CHECK upgrades available after reopening: FAIL"
shot 02b_runend_reopened
for i in 1 2 3 4 5 6; do has "Begin era" && break; $ADB shell input swipe 540 1500 540 500 400; $ADB shell sleep 1; done
shot 03_runend_bottom
tap "Begin era" || exit 1
wait_text "Day 1" && echo "CHECK next era started: ok" || echo "CHECK next era: FAIL"
shot 04_next_era
echo RUNEND_DONE
