#!/usr/bin/env bash
# Drives the vertical slice on a connected emulator via uiautomator dumps and taps (Git Bash on Windows works).
# Usage: ADB=/path/to/adb bash tools/emulator/smoke.sh <screenshot-dir>   (install the debug app first)
set -u
export MSYS_NO_PATHCONV=1
ADB=${ADB:-adb}
OUT="$1"; mkdir -p "$OUT"
PKG=com.example.blacksmithproject

# Remove the previous dump first: a dump that fails mid-transition must not pass a check on stale content.
# A dump can come back empty while the screen is moving or the emulator is busy: try three times before giving up.
dump() {
  local x=""
  for i in 1 2 3; do
    $ADB shell rm -f /sdcard/ui.xml; $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    x=$($ADB shell cat /sdcard/ui.xml 2>/dev/null); [ -n "$x" ] && break; $ADB shell sleep 1
  done
  printf '%s' "$x"
}
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
# tap the node whose resource-id (a Compose testTag, exposed through testTagsAsResourceId) is exactly $1
has_id() { dump | grep -q "resource-id=\"$1\""; }
tap_id() {
  local x=$(dump | python -c "
import sys,re
s=sys.stdin.read()
m=re.search(r'resource-id=\"$1\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]', s)
if m:
  x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2)
")
  if [ -z "$x" ]; then echo "MISSING id: $1"; return 1; fi
  $ADB shell input tap $x; echo "tapped id '$1' at $x"; $ADB shell sleep 1.2
}
scroll_to() { for i in 1 2 3 4 5 6 7 8; do if has "$1"; then return 0; fi; $ADB shell input swipe 540 1200 540 950 600; $ADB shell sleep 1; done; has "$1" && return 0; echo "SCROLL MISSING: $1"; return 1; }
shot() { $ADB exec-out screencap -p > "$OUT/$1.png"; echo "screenshot $1"; }
wait_text() { for i in $(seq 1 20); do if has "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for '$1'"; return 1; }
# A run with a relic offer waiting opens every morning (and after a restart) on that dialog, which hides the workshop
# from the dump; "Decide later" keeps the offer, as in scenarios.sh. Returns once the Shop's End Day is on screen.
past_offers() {
  for i in $(seq 1 20); do
    local x=$(dump)
    if printf '%s' "$x" | grep -q 'resource-id="relic_later"'; then tap_id relic_later >/dev/null
    elif printf '%s' "$x" | grep -q 'resource-id="blessing_later"'; then tap_id blessing_later >/dev/null
    elif printf '%s' "$x" | grep -q 'resource-id="end_day"'; then return 0
    else $ADB shell sleep 1; fi
  done
  echo "TIMEOUT waiting for planning past the offers"; return 1
}

$ADB shell pm clear $PKG >/dev/null
$ADB shell am start -n $PKG/.MainActivity >/dev/null
wait_text "Tiny Blacksmith" && shot 01_title
tap_id title_new_run || exit 1
past_offers || exit 1
# A new run lands on the Shop destination, which leads with the day's one lead; the forge is one destination over.
wait_text "Forge your first blade" && shot 02_shop
has_id shop_lead && echo "CHECK shop leads with a lead: ok" || echo "CHECK shop leads with a lead: FAIL"
tap_id nav_forge || exit 1
wait_text "Forge · " && shot 02_workshop
# The workbench: each tap fills the open slot and the tray moves on to the next one.
tap_id forge_option_sword; tap_id forge_option_iron; tap_id forge_option_ember_resin
shot 03_forge_ready
tap_id forge_weapon || exit 1
wait_text "Suggested price" && shot 04_result
tap_id reveal_list || exit 1
tap_id nav_shop; $ADB shell sleep 1; shot 05_shop_listed
has "Shelf 1 of 8" && echo "CHECK shelf listed: ok" || echo "CHECK shelf listed: FAIL"
tap_id end_day || exit 1
# The shop day opens on its first card: step a few cards, skip to the day's last card, read the Gazette over it (closing
# it does not begin the next day), then begin the next day.
has_id shopday_next && echo "CHECK shop day opens: ok" || echo "CHECK shop day opens: FAIL"
shot 06_shopday
tap_id shopday_next; shot 06b_shopday_card
tap_id shopday_next; shot 06b2_shopday_card
tap_id shopday_skip || exit 1
shot 06c_tomorrow
tap_id shopday_gazette || exit 1
wait_text "EMBERFALL GAZETTE" && shot 06_gazette
dump | grep -o 'text="[^"]*"' | grep -iE "gazette|bought|forged|patrolled|routed|invasion" | head -8
tap_id report_close || exit 1
has_id shopday_close && echo "CHECK gazette closes onto the day: ok" || echo "CHECK gazette closes onto the day: FAIL"
tap_id shopday_close || exit 1
past_offers || exit 1
tap_id nav_town; $ADB shell sleep 1; shot 07_town
has "Champions" && echo "CHECK town panel: ok"
# Records: three segments; the gear opens the settings sheet; Back closes it, then returns from a destination to Shop.
tap_id nav_records; shot 07b_records
has_id page_gazette && has_id page_journal && has_id page_legacy && echo "CHECK records segments: ok" || echo "CHECK records segments: FAIL"
tap_id page_legacy; wait_text "Permanent upgrades" && echo "CHECK legacy segment: ok" || echo "CHECK legacy segment: FAIL"
tap_id nav_settings; wait_text "Reduced motion" && shot 07c_settings
has_id settings_version && echo "CHECK settings sheet: ok" || echo "CHECK settings sheet: FAIL"
$ADB shell input keyevent KEYCODE_BACK; $ADB shell sleep 2
$ADB shell input keyevent KEYCODE_BACK; $ADB shell sleep 2
has_id shop_list && echo "CHECK back returns to Shop: ok" || echo "CHECK back returns to Shop: FAIL"
# Process-death resume: kill and relaunch, expect the same day.
$ADB shell am force-stop $PKG; $ADB shell am start -n $PKG/.MainActivity >/dev/null
# A saved run opens on the main menu; Continue returns to the workshop.
wait_text "Continue run" && shot 07_menu && tap_id menu_continue
past_offers
wait_text "Day 2" && echo "CHECK resume after process death on day 2: ok" || echo "CHECK resume: FAIL"
shot 08_resumed
echo SMOKE_DONE
