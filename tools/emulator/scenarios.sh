#!/usr/bin/env bash
# Loads every constructed scenario save through the debug build's main menu ("Scenarios (debug)") and screenshots the
# card or sheet that shows its mechanic: End Day and the shop-day cards where the mechanic is one day away, Storage
# and the blade sheet where it is already in the save. The screenshots show CONSTRUCTED saves, not natural play.
# Usage: ADB=/path/to/adb bash tools/emulator/scenarios.sh <screenshot-dir>   (install the debug app first; run from the repo root)
# The saves and their list come from ./gradlew :core:scenarios (app/src/debug/assets/scenarios/).
set -u
export MSYS_NO_PATHCONV=1
ADB=${ADB:-adb}
OUT="$1"; mkdir -p "$OUT"
PKG=com.example.blacksmithproject
INDEX="$(dirname "$0")/../../app/src/debug/assets/scenarios/index.json"

# Remove the previous dump first: a dump that fails mid-transition must not pass a check on stale content.
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
# tap the node whose resource-id (a Compose testTag, exposed through testTagsAsResourceId) is exactly $1
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
# the text of the node with resource-id $1 (uiautomator writes text before resource-id); Windows python ends lines with CR LF
text_of() { dump | python -c "
import sys,re
m=re.search(r'text=\"([^\"]*)\" resource-id=\"$1\"', sys.stdin.read())
print(m.group(1) if m else '')
" | tr -d '\r'; }
wait_id() { for i in $(seq 1 20); do if has_id "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for id '$1'"; return 1; }
# swipe the list up until the node with resource-id $1 is on screen
scroll_to_id() { for i in 1 2 3 4 5 6 7 8 9 10; do if has_id "$1"; then return 0; fi; $ADB shell input swipe 540 1300 540 800 600; $ADB shell sleep 1; done; echo "SCROLL MISSING id: $1"; return 1; }
shot() { $ADB exec-out screencap -p > "$OUT/$1.png"; echo "screenshot $1"; }
field() { python -c "
import json,sys
row=[r for r in json.load(open(sys.argv[1],encoding='utf-8')) if r['id']==sys.argv[2]][0]
print(row.get(sys.argv[3],''))
" "$INDEX" "$1" "$2" | tr -d '\r'; }

# Opens the main menu from a cold start and loads scenario $1; a run exists from the second scenario on, so the
# confirmation is the one that says the current run is replaced.
load() {
  $ADB shell am force-stop $PKG; $ADB shell am start -n $PKG/.MainActivity >/dev/null
  wait_id menu_scenarios || return 1
  tap_id menu_scenarios || return 1
  wait_id scenario_list || return 1
  scroll_to_id "scenario_$1" || return 1
  tap_id "scenario_$1" || return 1
  wait_id scenario_confirm || return 1
  shot "$1_0_confirm"
  tap_id scenario_confirm || return 1
  # A save may open on a blessing offer: put it off, the scenario is about something else.
  $ADB shell sleep 2; if has_id blessing_later; then tap_id blessing_later; fi
  wait_id end_day && echo "CHECK $1 loads onto planning: ok" || { echo "CHECK $1 loads onto planning: FAIL"; return 1; }
}

# Presses End Day and steps through the shop day to the aftermath card titled $2, then screenshots it as $1_$3.
# Further title/name pairs are looked for on the cards after it.
day_cards() {
  local id="$1"; shift
  tap_id end_day || return 1
  wait_id shopday_card || { echo "CHECK $id shop day opens: FAIL"; return 1; }
  while [ $# -ge 2 ]; do
    local found=""
    for i in $(seq 1 20); do
      if [ "$(text_of shopday_aftermath_kind)" = "$1" ]; then found=1; break; fi
      has_id shopday_next || break
      tap_id shopday_next >/dev/null
    done
    if [ -n "$found" ]; then shot "${id}_$2"; echo "CHECK $id shows \"$1\": ok"; else echo "CHECK $id shows \"$1\": FAIL"; return 1; fi
    shift 2
    [ $# -ge 2 ] && tap_id shopday_next >/dev/null
  done
}

open_storage() { scroll_to_id shop_storage && tap_id shop_storage && wait_id storage_sheet; }

$ADB shell pm clear $PKG >/dev/null
$ADB shell am start -n $PKG/.MainActivity >/dev/null
wait_id title_new_run
wait_id menu_scenarios && echo "CHECK the debug menu has a Scenarios entry: ok" || { echo "CHECK the debug menu has a Scenarios entry: FAIL"; exit 1; }
tap_id menu_scenarios; wait_id scenario_list && shot 00_scenario_list
has "Constructed saves for testing" && echo "CHECK the list is labelled as constructed: ok" || echo "CHECK the list is labelled as constructed: FAIL"

load hall_lesson && { shot hall_lesson_1_planning; day_cards hall_lesson "At the guild hall" 2_lesson; }
load inherited_blade && day_cards inherited_blade "A death" 1_death "A blade passed on" 2_passed_on
load merchant_resale && day_cards merchant_resale "Sold on by a merchant" 1_resold
load wall_death && day_cards wall_death "The siege" 1_siege "A death" 2_death_at_the_wall
dump | grep -q "died defending the walls" && echo "CHECK wall_death names the wall: ok" || echo "CHECK wall_death names the wall: FAIL"

# Known Name: the confirmation says the legacy is replaced; Town shows the regular on day 1; then the regular's visit.
load known_name && {
  tap_id nav_town; $ADB shell sleep 1
  for i in 1 2 3 4 5 6; do dump | grep -q "a regular of your shop" && break; $ADB shell input swipe 540 1300 540 800 600; $ADB shell sleep 1; done
  dump | grep -q "a regular of your shop" && { shot known_name_1_town; echo "CHECK known_name shows a regular in Town on day 1: ok"; } || echo "CHECK known_name shows a regular in Town on day 1: FAIL"
  tap_id nav_shop; tap_id end_day; wait_id shopday_card
  found=""
  for i in $(seq 1 12); do dump | grep -q ", a regular" && { found=1; break; }; has_id shopday_next || break; tap_id shopday_next >/dev/null; done
  [ -n "$found" ] && { shot known_name_2_regular_visit; echo "CHECK known_name regular comes to the counter: ok"; } || echo "CHECK known_name regular comes to the counter: FAIL"
}

# Returned legend: the blade sheet marks what sleeps in it; Hone wakes it.
load returned_legend && open_storage && {
  blade="stock_$(field returned_legend blade)"
  scroll_to_id "$blade" && tap_id "$blade" && wait_id item_sheet
  has_id card_dormant && { shot returned_legend_1_dormant; echo "CHECK returned_legend is dormant: ok"; } || echo "CHECK returned_legend is dormant: FAIL"
  scroll_to_id item_hone && tap_id item_hone
  # Back closes the blade sheet onto Storage; opened again, the sheet is at its top, where the dormant marker was.
  $ADB shell input keyevent KEYCODE_BACK; $ADB shell sleep 2
  scroll_to_id "$blade" && tap_id "$blade" && wait_id item_sheet
  has_id item_sheet && ! has_id card_dormant && { shot returned_legend_2_woken; echo "CHECK returned_legend wakes under the hone: ok"; } || echo "CHECK returned_legend wakes under the hone: FAIL"
}

# Storied blade: its Story on the blade sheet.
load storied_blade && open_storage && {
  blade="stock_$(field storied_blade blade)"
  scroll_to_id "$blade" && tap_id "$blade" && wait_id item_sheet
  shot storied_blade_1_sheet
  scroll_to_id sheet_story && { shot storied_blade_2_story; echo "CHECK storied_blade has a Story: ok"; } || echo "CHECK storied_blade has a Story: FAIL"
}

# Long storage: the storeroom and its tools.
load long_storage && open_storage && {
  count=$(dump | python -c "
import sys,re
m=re.search(r'text=\"Storage \S+ (\d+)\"', sys.stdin.buffer.read().decode('utf-8','replace'))
print(m.group(1) if m else 0)
" | tr -d '\r')
  [ "$count" -ge 200 ] && { shot long_storage_1_storage; echo "CHECK long_storage holds two hundred blades or more ($count): ok"; } || echo "CHECK long_storage holds two hundred blades or more ($count): FAIL"
  tap_id storage_select && shot long_storage_2_select
}
echo SCENARIOS_DONE
