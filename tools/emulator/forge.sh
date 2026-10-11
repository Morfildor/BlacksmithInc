#!/usr/bin/env bash
# Deep forging on a connected emulator, in a new guild run: choose a recipe, read the outlook card and its details,
# rehearse the recipe on the bench, forge it, open the weapon's sheet and its bench, then the notebook's fight notes.
# Ends with a process kill and a look at what came back. Screenshots and CHECK lines; no assertion stops it.
# Usage: ADB=/path/to/adb ANDROID_SERIAL=emulator-5562 bash tools/emulator/forge.sh <screenshot-dir>
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
# a node whose resource-id starts with $1
has_pre() { dump | grep -q "resource-id=\"$1"; }
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
shot() { $ADB exec-out screencap -p > "$OUT/$1.png"; echo "screenshot $1"; }
wait_text() { for i in $(seq 1 25); do if has "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for '$1'"; return 1; }
wait_id() { for i in $(seq 1 25); do if has_id "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for id '$1'"; return 1; }
check() { if "$@" >/dev/null 2>&1; then echo "CHECK $*: ok"; else echo "CHECK $*: FAIL"; fi; }
# starts above the pinned Forge button, which does not scroll
swipe_up() { $ADB shell input swipe 540 1300 540 600 500; $ADB shell sleep 1; }
scroll_to_id() { for i in 1 2 3 4 5 6 7 8 9 10; do if has_pre "$1"; then return 0; fi; swipe_up; done; echo "SCROLL MISSING id: $1"; return 1; }
scroll_to_text() { for i in 1 2 3 4 5 6 7 8 9 10; do if has "$1"; then return 0; fi; $ADB shell input swipe 540 1300 540 900 500; $ADB shell sleep 1; done; echo "SCROLL MISSING text: $1"; return 1; }
texts() { dump | grep -o 'text="[^"]\+"' | head -n "${1:-60}"; }
# Whatever the morning puts in front of the workshop (relic, blessing, an opening package) is put off or taken first.
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
tap_id title_new_run || exit 1
$ADB shell sleep 2
past_offers || { shot 00_stuck_after_new_run; texts; exit 1; }

# 1. Ingredient selection: the metal's tray says what each metal does in a fight.
tap_id nav_forge || exit 1
$ADB shell sleep 1; shot 01_forge_empty
tap_id forge_option_sword; shot 02_metal_tray
check has_id forge_tray_fight
tap_id forge_option_iron; shot 03_augment_tray
tap_id forge_option_frost_bloom
# 2. The explanation: the outlook card.
scroll_to_id forge_outlook; shot 04_outlook
check has_id forge_outlook_role
echo "--- outlook card"; texts 80 | grep -n "" | sed -n '1,80p'
scroll_to_id forge_outlook_details && tap_id forge_outlook_details
swipe_up; shot 05_outlook_details
check has_id forge_outlook_expanded
swipe_up; shot 06_outlook_details_lower
# 3. The bench for the recipe.
scroll_to_id forge_bench_open && tap_id forge_bench_open
wait_id bench_sheet; shot 07_bench_recipe
check has "The bench"
tap_id bench_company_frost; shot 08_bench_recipe_frost
scroll_to_id bench_target_attrition && tap_id bench_target_attrition; shot 09_bench_recipe_attrition
# The town's wall: the labelled home-defence rehearsal.
scroll_to_id bench_target_wall && tap_id bench_target_wall; shot 09b_bench_recipe_wall
check has_id bench_synthetic
scroll_to_text "What happened"; shot 10_bench_recipe_what_happened
check has "What happened"
scroll_to_text "Waiting for its moment"; shot 11_bench_recipe_waiting
check has "Waiting for its moment"
echo "--- bench (recipe)"; texts 80
# Process recreation with the bench open: the app goes to the background, the system kills its process, the task is
# brought back. The recipe on the anvil is saved instance state and comes back. (A force-stop is a different thing: it
# throws the task's saved state away, as swiping the app out of recents does, and the anvil is empty afterwards.)
$ADB shell input keyevent KEYCODE_HOME; $ADB shell sleep 2
$ADB shell am kill $PKG; $ADB shell sleep 1
check_dead() { [ -z "$($ADB shell pidof $PKG)" ]; }
check check_dead
$ADB shell am start -n $PKG/.MainActivity >/dev/null; $ADB shell sleep 4
has "Continue run" && { tap_id menu_continue; past_offers; tap_id nav_forge; }
shot 12_after_recreation_forge
has_id bench_sheet && echo "NOTE bench came back open after recreation" || echo "NOTE bench was closed after recreation"
has_id bench_close && { scroll_to_id bench_close; tap_id bench_close; }
draft_kept() { dump | grep -q 'Iron sword'; }
check draft_kept
# If the anvil is empty all the same, the recipe is chosen again so that the rest of the pass can go on.
if has_id forge_option_sword; then echo "NOTE the draft did not survive recreation"; tap_id forge_option_sword; tap_id forge_option_iron; tap_id forge_option_frost_bloom; fi
# 4. Forge it and open the weapon.
tap_id forge_weapon || { scroll_to_id forge_weapon && tap_id forge_weapon; } || exit 1
$ADB shell sleep 2; shot 13_forge_result
echo "--- result"; texts 60
tap_id reveal_list || exit 1
# A second forge with bronze, whose rule opens a suggested interaction in the notebook.
for i in 1 2 3; do $ADB shell input swipe 540 700 540 1300 300; done; $ADB shell sleep 1
tap_id forge_slot_metal; tap_id forge_option_bronze; tap_id forge_slot_augment; tap_id forge_option_ember_resin
scroll_to_id forge_outlook; shot 14_outlook_bronze
tap_id forge_weapon && { $ADB shell sleep 2; tap_id reveal_store; }
# The first weapon's sheet, from the shelf.
tap_id nav_shop; $ADB shell sleep 1
scroll_to_id stock_ && tap_id stock_
wait_id item_sheet; shot 15_weapon_sheet
scroll_to_id card_bench_open; shot 15b_weapon_card
check has_id card_bench_open
# 5. The bench for the owned weapon.
tap_id card_bench_open
wait_id bench_sheet; shot 16_bench_owned
check has "The bench"
tap_id bench_company_fire; swipe_up; shot 17_bench_owned_fire
swipe_up; shot 17b_bench_owned_lower
echo "--- bench (owned)"; texts 80
scroll_to_id bench_close && tap_id bench_close
$ADB shell input keyevent KEYCODE_BACK; $ADB shell sleep 1
# 6. The notebook's fight notes.
tap_id nav_records; $ADB shell sleep 1
tap_id page_journal; $ADB shell sleep 1; shot 18_notebook
check has_id book_tab_fights
tap_id book_tab_fights; $ADB shell sleep 1; shot 19_fight_notes
check has_id book_learned
swipe_up; shot 20_fight_notes_lower
echo "--- fight notes"; texts 80
echo "done"
