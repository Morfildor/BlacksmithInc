#!/usr/bin/env bash
# Drives the vertical slice on a connected emulator via uiautomator dumps and taps (Git Bash on Windows works).
# Usage: ADB=/path/to/adb bash tools/emulator/smoke.sh <screenshot-dir>   (install the debug app first)
set -u
export MSYS_NO_PATHCONV=1
ADB=${ADB:-adb}
OUT="$1"; mkdir -p "$OUT"
PKG=com.example.blacksmithproject

# Remove the previous dump first: a dump that fails mid-transition must not pass a check on stale content.
dump() { $ADB shell rm -f /sdcard/ui.xml; $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $ADB shell cat /sdcard/ui.xml 2>/dev/null; }
has() { dump | grep -q "text=\"$1" ; }
# tap the first node whose text starts with $1
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
scroll_to() { for i in 1 2 3 4 5 6 7 8; do if has "$1"; then return 0; fi; $ADB shell input swipe 540 1200 540 950 600; $ADB shell sleep 1; done; has "$1" && return 0; echo "SCROLL MISSING: $1"; return 1; }
shot() { $ADB exec-out screencap -p > "$OUT/$1.png"; echo "screenshot $1"; }
wait_text() { for i in $(seq 1 20); do if has "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for '$1'"; return 1; }

$ADB shell pm clear $PKG >/dev/null
$ADB shell am start -n $PKG/.MainActivity >/dev/null
wait_text "Tiny Blacksmith" && shot 01_title
tap "Light the forge" || exit 1
# A new run lands on the Home dashboard; the forge is one tab over.
wait_text "Today" && shot 02_home
tap "Forge" || exit 1
wait_text "Forge weapon" && shot 02_workshop
scroll_to "Sword" && tap "Sword"; scroll_to "Iron" && tap "Iron"; scroll_to "Ember Resin" && tap "Ember Resin"
scroll_to "Forge weapon"; shot 03_forge_ready
tap "Forge weapon" || exit 1
wait_text "Suggested price" && shot 04_result
tap "List at" || exit 1
tap "Market"; $ADB shell sleep 1; shot 05_market
has "Shelves (1/8)" && echo "CHECK shelf listed: ok" || echo "CHECK shelf listed: FAIL"
tap "End Day" || exit 1
wait_text "EMBERFALL GAZETTE" && shot 06_gazette
dump | grep -o 'text="[^"]*"' | grep -iE "gazette|bought|forged|patrolled|routed|invasion" | head -8
tap "Begin day" || exit 1
tap "Town"; $ADB shell sleep 1; shot 07_town
has "Champions" && echo "CHECK town panel: ok"
# Process-death resume: kill and relaunch, expect the same day.
$ADB shell am force-stop $PKG; $ADB shell am start -n $PKG/.MainActivity >/dev/null
# A saved run resumes straight into the workshop (no title detour).
wait_text "Day 2" && echo "CHECK resume after process death on day 2: ok" || echo "CHECK resume: FAIL"
shot 08_resumed
echo SMOKE_DONE
