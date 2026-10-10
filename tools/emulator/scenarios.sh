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
scroll_to_id() { for i in $(seq 1 30); do if has_id "$1"; then return 0; fi; $ADB shell input swipe 540 1300 540 800 600; $ADB shell sleep 1; done; echo "SCROLL MISSING id: $1"; return 1; }
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
  # A save with a relic offer waiting opens on that dialog, which hides the Shop from the dump; "Decide later" keeps the
  # offer. The two saves that are about the dialog leave it up.
  for i in $(seq 1 20); do
    local x=$(dump)
    if printf '%s' "$x" | grep -q 'resource-id="relic_later"'; then
      case "$1" in relic_offer|relic_full_workshop) echo "CHECK $1 loads onto the relic offer: ok"; return 0;; esac
      tap_id relic_later >/dev/null
    # A save may also open on a blessing offer: put it off, the scenario is about something else.
    elif printf '%s' "$x" | grep -q 'resource-id="blessing_later"'; then tap_id blessing_later >/dev/null
    elif printf '%s' "$x" | grep -q 'resource-id="end_day"'; then echo "CHECK $1 loads onto planning: ok"; return 0
    else $ADB shell sleep 1; fi
  done
  echo "CHECK $1 loads onto planning: FAIL"; return 1
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

# Gameplay depth: every morning visitor (card on the Shop, then its sheet), the relic saves, the two siege traits, the chain.
for v in visitor_collector visitor_crate_no_gold visitor_pledge visitor_master visitor_heirloom visitor_merchant visitor_wager visitor_festival chain_debt_repaid; do
  load $v && {
    wait_id visitor_card && { shot ${v}_1_shop; tap_id visitor_open; wait_id visitor_sheet && { shot ${v}_2_sheet; echo "CHECK $v opens its visitor: ok"; } || echo "CHECK $v opens its visitor: FAIL"; } || echo "CHECK $v shows a visitor card: FAIL"
  }
done
# The merchant's inspection keeps the sheet open and shows the blade.
load visitor_merchant && wait_id visitor_card && tap_id visitor_open && wait_id visitor_sheet && {
  tap_id visitor_option_inspect; tap_id visitor_commit; $ADB shell sleep 1
  has_id visitor_sheet && ! has_id visitor_option_inspect && { shot visitor_merchant_3_inspected; echo "CHECK the inspection keeps the merchant at the forge: ok"; } || echo "CHECK the inspection keeps the merchant at the forge: FAIL"
}
# Left unanswered: the End Day note names the default; the day can be ended without answering.
load visitor_unanswered && wait_id visitor_card && { shot visitor_unanswered_1_end_day_note; dump | grep -q "The visitor leaves tonight" && echo "CHECK End Day says what the visitor will be told: ok" || echo "CHECK End Day says what the visitor will be told: FAIL"; }
load relic_offer && { wait_id relic_later && { shot relic_offer_1_draft; echo "CHECK relic_offer opens the draft on day 1: ok"; tap_id relic_later; scroll_to_id shop_relics && shot relic_offer_2_shop_row; } || echo "CHECK relic_offer opens the draft on day 1: FAIL"; }
for r in relic_crucible relic_ledger relic_seal relic_bellows; do
  load $r && { scroll_to_id shop_relics && { shot ${r}_1_shop_row; echo "CHECK $r shows its relic on the Shop: ok"; } || echo "CHECK $r shows its relic on the Shop: FAIL"; }
done
load relic_bellows && { tap_id nav_forge; scroll_to_id forge_bellows && { shot relic_bellows_2_forge; echo "CHECK relic_bellows has the forge switch: ok"; } || echo "CHECK relic_bellows has the forge switch: FAIL"; }
load relic_ledger && { tap_id nav_forge; shot relic_ledger_2_forge; }
load relic_full_workshop && { wait_id relic_later && shot relic_full_workshop_1_offer || echo "CHECK relic_full_workshop offers the fourth relic: FAIL"; }
for s in siege_long_assault siege_many_breaches; do
  load $s && { tap_id nav_town; scroll_to_id town_trait && { shot ${s}_1_town; echo "CHECK $s shows its trait in Town: ok"; } || echo "CHECK $s shows its trait in Town: FAIL"; }
done
echo SCENARIOS_DONE
