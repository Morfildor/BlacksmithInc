#!/usr/bin/env bash
# A guild run's first days on a connected emulator: the charter on the title, the Guild destination, a party sent on
# the free contract, the evening's contract card, the next morning. Screenshots and CHECK lines; no assertions stop it.
# Usage: ADB=/path/to/adb bash tools/emulator/guild.sh <screenshot-dir>   (install the debug app first)
set -u
export MSYS_NO_PATHCONV=1
ADB=${ADB:-adb}
OUT="$1"; mkdir -p "$OUT"
PKG=com.example.blacksmithproject

dump() {
  local x=""
  for i in 1 2 3; do
    $ADB shell rm -f /sdcard/ui.xml; $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    x=$($ADB shell cat /sdcard/ui.xml 2>/dev/null); [ -n "$x" ] && break; $ADB shell sleep 1
  done
  printf '%s' "$x"
}
has() { dump | grep -q "text=\"$1" ; }
has_id() { dump | grep -q "resource-id=\"$1\""; }
# taps the node whose resource-id starts with $1; $2 picks the nth such node (0 = first)
tap_id() {
  local x=$(dump | python -c "
import sys,re
s=sys.stdin.read()
ms=re.findall(r'resource-id=\"$1[^\"]*\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]', s)
n=${2:-0}
if len(ms)>n:
  x1,y1,x2,y2=map(int,ms[n]); print((x1+x2)//2,(y1+y2)//2)
")
  if [ -z "$x" ]; then echo "MISSING id: $1"; return 1; fi
  $ADB shell input tap $x; echo "tapped id '$1' at $x"; $ADB shell sleep 1.2
}
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
wait_text() { for i in $(seq 1 25); do if has "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for '$1'"; return 1; }
wait_id() { for i in $(seq 1 25); do if has_id "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for id '$1'"; return 1; }
check() { if "$@" >/dev/null 2>&1; then echo "CHECK $*: ok"; else echo "CHECK $*: FAIL"; fi; }
swipe_up() { $ADB shell input swipe 540 1500 540 700 500; $ADB shell sleep 1; }
# scrolls the list until a node with resource-id starting with $1 is on screen
scroll_to_id() { for i in 1 2 3 4 5 6 7 8 9 10; do if has_id "$1"; then return 0; fi; swipe_up; done; echo "SCROLL MISSING id: $1"; return 1; }
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
wait_text "Tiny Blacksmith"
check has_id charter_picker
shot 01_title_charter
tap_id charter_about; shot 02_title_charter_about; tap_id charter_about
tap_id charter_next; shot 03_title_charter_second
tap_id charter_prev
tap_id title_new_run || exit 1
$ADB shell sleep 2; shot 04_opening_relic_offer
past_offers || exit 1
shot 05_shop_day1
tap_id nav_town || exit 1
check has "Guild"
wait_id guild_list; shot 06_guild_top
swipe_up; shot 07_guild_scroll1
swipe_up; shot 08_guild_scroll2
swipe_up; shot 09_guild_scroll3
swipe_up; shot 10_guild_scroll4
swipe_up; shot 11_guild_scroll5
# back to the top, then the first contract on the board
for i in 1 2 3 4 5 6; do $ADB shell input swipe 540 700 540 1700 300; done; $ADB shell sleep 1
# the board is under the wall: scroll until a contract's "Choose a party" is on screen
for i in 1 2 3 4 5 6 7 8; do has "Choose a party" && break; swipe_up; done
tap "Choose a party"
wait_id contract_sheet; shot 12_contract_sheet
tap_id contract_pick_ 0; tap_id contract_pick_ 1; shot 13_contract_party_picked
swipe_up; shot 14_contract_sheet_lower
scroll_to_id contract_send && tap_id contract_send
$ADB shell sleep 1; shot 15_guild_planned
check has_id guild_plan
# a member's sheet
for i in 1 2 3 4 5 6 7 8 9 10; do has_id guild_member_ && break; swipe_up; done
shot 15b_roster
tap_id guild_member_
$ADB shell sleep 1; shot 16_member_sheet
swipe_up; shot 17_member_sheet_lower
$ADB shell input keyevent KEYCODE_BACK; $ADB shell sleep 1
# forge a blade and look at what it does in a fight
tap_id nav_forge; $ADB shell sleep 1; shot 18_forge
swipe_up; shot 19_forge_lower
tap_id nav_shop; $ADB shell sleep 1
tap_id end_day || exit 1
$ADB shell sleep 2; shot 20_end_day_note_or_evening
has_id end_day_confirm && tap_id end_day_confirm
# the evening, card by card: every card is shot; the contract card also with its whole fight open
for n in 21 22 23 24 25 26 27 28 29; do
  $ADB shell sleep 2
  has_id shopday_card || has_id shopday_progress || break
  shot ${n}_evening_card
  if has_id shopday_contract; then echo "CHECK contract card shown: ok"; for k in 1 2 3 4; do has_id fight_toggle && break; swipe_up; done; tap_id fight_toggle && { swipe_up; $ADB shell sleep 1; shot ${n}b_evening_contract_fight; }; fi
  tap_id shopday_next || { tap "Begin day"; break; }
done
has_id shopday_skip && tap_id shopday_skip
$ADB shell sleep 2; shot 30_after_evening
past_offers
tap_id nav_town; wait_id guild_list; shot 31_guild_day2
swipe_up; shot 32_guild_day2_scroll1
swipe_up; shot 33_guild_day2_scroll2
echo "done"
