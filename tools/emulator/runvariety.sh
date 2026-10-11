#!/usr/bin/env bash
# Run variety (rules 6) on a device: the six constructed saves of core test RunVarietyScenarios.kt, loaded through the
# debug build's main menu ("Scenarios (debug)"). Heartfire on the workbench before and after a forging, a lesser metal,
# the supplier's days and moods, the wall card's preparation and the siege card, the three legend lines, and the save
# after a killed process. Screenshots, the UI dump beside each one (<name>.xml), and CHECK lines; nothing stops it.
# Usage: ADB="/path/to/adb -s <serial>" bash tools/emulator/runvariety.sh <screenshot-dir> [step ...]
#   steps: breakthrough miss supplies siege supplier legends kill font   (none given = all, in that order)
# Install the debug app first; run from the repo root. The screenshots show CONSTRUCTED saves, not natural play.
set -u
export MSYS_NO_PATHCONV=1
ADB=${ADB:-adb}
OUT="$1"; mkdir -p "$OUT"; shift
PKG=com.example.blacksmithproject

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
# true when some text on screen contains $1
sees() { dump | grep -qF "$1"; }
# tap the node whose resource-id (a Compose testTag) is exactly $1
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
import sys,re,html
m=re.search(r'text=\"([^\"]*)\" resource-id=\"$1\"', sys.stdin.buffer.read().decode('utf-8','replace'))
sys.stdout.buffer.write((html.unescape(m.group(1)) if m else '').encode('utf-8'))
" | tr -d '\r'; }
# every text under the node with resource-id $1, one per line (a Column with a testTag has its lines as children)
lines_of() { dump | python -c "
import sys,re,html
import xml.etree.ElementTree as ET
try: root=ET.fromstring(sys.stdin.buffer.read().decode('utf-8','replace'))
except Exception: sys.exit(0)
for n in root.iter('node'):
  if n.get('resource-id')=='$1':
    for c in n.iter('node'):
      t=c.get('text') or c.get('content-desc') or ''
      if t: sys.stdout.buffer.write((t+'\n').encode('utf-8'))
    break
" | tr -d '\r'; }
# every text on screen, one per line
texts() { dump | python -c "
import sys,re,html
for t in re.findall(r'text=\"([^\"]+)\"', sys.stdin.buffer.read().decode('utf-8','replace')): sys.stdout.buffer.write((html.unescape(t)+'\n').encode('utf-8'))
" | tr -d '\r'; }
wait_id() { for i in $(seq 1 20); do if has_id "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for id '$1'"; return 1; }
wait_text() { for i in $(seq 1 20); do if has "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for '$1'"; return 1; }
swipe_up() { $ADB shell input swipe 540 1300 540 800 600; $ADB shell sleep 1; }
swipe_down() { $ADB shell input swipe 540 800 540 1300 600; $ADB shell sleep 1; }
# swipe the list up until the node with resource-id $1 is on screen
scroll_to_id() { for i in $(seq 1 30); do if has_id "$1"; then return 0; fi; swipe_up; done; echo "SCROLL MISSING id: $1"; return 1; }
# swipe up until some text containing $1 is on screen
scroll_to_text() { for i in $(seq 1 30); do if sees "$1"; then return 0; fi; swipe_up; done; echo "SCROLL MISSING text: $1"; return 1; }
# the screenshot and the dump it was taken over
shot() { $ADB exec-out screencap -p > "$OUT/$1.png"; dump > "$OUT/$1.xml"; echo "screenshot $1"; }
ok() { echo "CHECK $1: ok"; }
bad() { echo "CHECK $1: FAIL${2:+ ($2)}"; }
# check "<what>" <command...>
check() { local what="$1"; shift; if "$@" >/dev/null 2>&1; then ok "$what"; else bad "$what"; fi; }
# expect "<what>" "<got>" "<wanted substring>"
expect() { case "$2" in *"$3"*) ok "$1 [$2]";; *) bad "$1" "got [$2], wanted [$3]";; esac; }

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
# Opens the main menu from a cold start and loads scenario $1 (the confirmation says the current run is replaced).
load() {
  $ADB shell am force-stop $PKG; $ADB shell am start -n $PKG/.MainActivity >/dev/null
  wait_id menu_scenarios || return 1
  tap_id menu_scenarios >/dev/null || return 1
  wait_id scenario_list || return 1
  scroll_to_id "scenario_$1" || return 1
  tap_id "scenario_$1" >/dev/null || return 1
  wait_id scenario_confirm || return 1
  tap_id scenario_confirm >/dev/null || return 1
  if past_offers; then ok "$1 loads onto planning"; else bad "$1 loads onto planning"; return 1; fi
}
# The Advanced recipe of the two Heartfire saves on the workbench, with metal $1: sword, $1, Sun Ash, no catalyst, plain
# technique, Balanced risk. Each slot is opened by its own tile, so the order the trays move on in does not matter.
pick() { # pick <slot> <option id>
  has_id "forge_tray_$1" || { scroll_to_id "forge_slot_$1" >/dev/null; tap_id "forge_slot_$1" >/dev/null; }
  scroll_to_id "forge_option_$2" >/dev/null && tap_id "forge_option_$2" >/dev/null || { echo "MISSING option $2 for $1"; return 1; }
}
top() { for i in 1 2 3 4; do $ADB shell input swipe 540 700 540 1700 300; done; $ADB shell sleep 1; }
recipe() {
  tap_id nav_forge >/dev/null; $ADB shell sleep 1
  top
  pick weapon sword; pick metal "$1"; pick augment sun_ash
  has_id forge_tray_done && tap_id forge_tray_done >/dev/null
  # Quick or Advanced stands under the field notes
  scroll_to_id forge_mode_advanced >/dev/null && tap_id forge_mode_advanced >/dev/null
  top
}
slots() { dump | grep -o 'content-desc="\(Weapon\|Metal\|Augment\|Catalyst\|Technique\|Risk\): [^"]*"' | sed 's/content-desc=//' | tr '\n' ' '; }
heartfire_on() {
  scroll_to_id forge_heartfire >/dev/null
  has_id forge_heartfire_outlook || tap_id forge_heartfire >/dev/null
  # the whole row on screen: its last lines are the rule and where Heartfire comes from
  for i in 1 2 3; do sees "Heartfire comes from" && break; $ADB shell input swipe 540 1300 540 1000 600; $ADB shell sleep 1; done
}
# A weapon's sheet: its own sheet (item_sheet) or, opened from Storage, a page of the storage sheet (sheet_back).
wait_sheet() { for i in $(seq 1 20); do if dump | grep -q 'resource-id="item_sheet"\|resource-id="sheet_back"'; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for a weapon's sheet"; return 1; }
open_storage() { tap_id nav_shop >/dev/null; scroll_to_id shop_storage && tap_id shop_storage >/dev/null && wait_id storage_sheet; }
open_supplies() { tap_id nav_shop >/dev/null; scroll_to_id shop_supplies && tap_id shop_supplies >/dev/null && wait_id supplies_sheet; }
# the lines of the Supplies row of material "$1" (by name): everything between its name and its Buy button
supply_row() { dump | python -c "
import sys,re,html
s=html.unescape(sys.stdin.buffer.read().decode('utf-8','replace'))
t=re.findall(r'text=\"([^\"]*)\"', s)
out=[]
if '$1' in t:
  for x in t[t.index('$1')+1:]:
    if x=='Buy' or x=='':
      if x=='Buy': break
      continue
    out.append(x)
sys.stdout.buffer.write(' | '.join(out).encode('utf-8'))
" | tr -d '\r'; }
buy_state() { dump | grep -o "content-desc=\"Buy $1[^\"]*\"[^>]*enabled=\"[a-z]*\"" | sed 's/checkable="[a-z]*" checked="[a-z]*"//' | head -1; }
# Ends the day and goes to the next morning without reading the cards.
skip_day() {
  tap_id nav_shop >/dev/null; tap_id end_day >/dev/null || return 1
  $ADB shell sleep 2; has_id end_day_confirm && tap_id end_day_confirm >/dev/null
  for i in $(seq 1 30); do
    local x=$(dump)
    if printf '%s' "$x" | grep -q 'resource-id="shopday_close"'; then tap_id shopday_close >/dev/null; break
    elif printf '%s' "$x" | grep -q 'resource-id="shopday_later"'; then tap_id shopday_later >/dev/null
    elif printf '%s' "$x" | grep -q 'resource-id="shopday_skip"'; then tap_id shopday_skip >/dev/null
    elif printf '%s' "$x" | grep -q 'resource-id="shopday_next"'; then tap_id shopday_next >/dev/null
    else $ADB shell sleep 1; fi
  done
  past_offers
}
[ "${RV_LIB:-}" = 1 ] && return 0

energy() { dump | grep -o 'content-desc="Energy[^"]*"' | head -1 | sed 's/content-desc=//; s/"//g'; }
rarity_line() { texts | grep -m1 -E '^(Common|Uncommon|Rare|Epic|Legendary)' ; }
# every contract on the Guild board with its modifier line, and the four rules-6 modifiers by name
board() { # board <name>
  local seen="" x
  for i in 1 2 3 4 5 6 7 8 9 10; do
    x=$(dump)
    seen="$seen
$(printf '%s' "$x" | python -c "
import sys,re,html
s=html.unescape(sys.stdin.buffer.read().decode('utf-8','replace'))
for t,i in re.findall(r'text=\"([^\"]*)\" resource-id=\"(guild_offer_modifier_[^\"]*)\"', s): sys.stdout.buffer.write((i+': '+t+'\n').encode('utf-8'))
for n in ('Hold the ford','One of the watch is held','A narrow way','Paid in metal'):
  if n in s: sys.stdout.buffer.write(('NEW MODIFIER ON SCREEN: '+n+'\n').encode('utf-8'))
" | tr -d '\r')"
    if printf '%s' "$x" | grep -q 'NEW MODIFIER\|Hold the ford\|One of the watch is held\|A narrow way\|Paid in metal'; then shot "$1_modifier_$i"; fi
    swipe_up
  done
  printf '%s\n' "$seen" | sort -u | grep . || echo "no contract with a modifier line on this board"
}

# Item 4: the supplier's rows on day 1.
step_supplies() {
  load heartfire_breakthrough || return
  open_supplies || { bad "Supplies opens"; return; }
  shot supplies_0_top
  scroll_to_text "Holds work up to Rare." >/dev/null
  shot supplies_1_plain_metals
  for m in Iron Bronze Silver Obsidian; do local r=$(supply_row $m); echo "ROW $m: $r"; [ -n "$r" ] && expect "$m says what it holds" "$r" "Holds work up to Rare."; done
  echo "BUY Iron: $(buy_state Iron)"
  scroll_to_text "Starsteel" >/dev/null; scroll_to_text "Moonsteel" >/dev/null; $ADB shell input swipe 540 1300 540 1100 600; $ADB shell sleep 1
  shot supplies_2_starsteel_moonsteel
  local st=$(supply_row Starsteel) mo=$(supply_row Moonsteel)
  echo "ROW Starsteel: $st"; echo "ROW Moonsteel: $mo"
  expect "Starsteel says what it holds" "$st" "Holds Epic work."
  expect "Starsteel says the supplier's day" "$st" "day 6"
  expect "Moonsteel says what it holds" "$mo" "Holds Epic work."
  expect "Moonsteel says the supplier's day" "$mo" "day 11"
  echo "BUY Starsteel: $(buy_state Starsteel)"; echo "BUY Moonsteel: $(buy_state Moonsteel)"
}

# Items 1 to 3: the forging of the two Heartfire saves. heart <scenario> <rarity wanted> <legend line wanted or "">
heart() {
  local id=$1
  load $id || return
  echo "ENERGY before: $(energy)"
  recipe starsteel
  shot ${id}_1_recipe
  echo "RECIPE $(slots) $(dump | grep -o 'content-desc="Risk: [^"]*"') mode=$(dump | grep -o 'resource-id="forge_mode_advanced"[^>]*selected="[a-z]*"' | grep -o 'selected="[a-z]*"')"
  heartfire_on
  shot ${id}_2_heartfire_before
  local held=$(text_of forge_heartfire_held) out=$(lines_of forge_heartfire_outlook | tr '\n' ' ')
  echo "HEARTFIRE before: $held | $(text_of forge_heartfire_state) | $out"
  expect "$id holds two Heartfire before the forging" "$held" "Heartfire 2 of 3"
  expect "$id says what is spent" "$out" "Uses 1 Heartfire and"
  expect "$id says the quality range" "$out" "Quality lands between"
  expect "$id says the chance to break through" "$out" "Chance to break through:"
  expect "$id says what a miss gives" "$out" "If it does not break through"
  expect "$id names the action" "$(texts | grep -m1 'Forge with Heartfire ·')" "Forge with Heartfire · 6 energy"
  # Item 3: a lesser metal with Heartfire on. Nothing is forged.
  top; pick metal iron; has_id forge_tray_done && tap_id forge_tray_done >/dev/null; top
  shot ${id}_3a_lesser_metal_iron_recipe; local lesser="$(slots)"; heartfire_on
  shot ${id}_3_lesser_metal_iron
  echo "LESSER METAL: $lesser | $(lines_of forge_heartfire_outlook | tr '\n' ' ')"
  expect "$id warns that Heartfire does not take in iron" "$(text_of forge_heartfire_warning)" "Heartfire does not take in this metal"
  expect "$id still holds two Heartfire" "$(text_of forge_heartfire_held)" "Heartfire 2 of 3"
  top; pick metal starsteel; has_id forge_tray_done && tap_id forge_tray_done >/dev/null; top
  local recipe="$(slots)"; heartfire_on
  echo "RECIPE forged: $recipe $(dump | grep -o 'content-desc="Risk: [^"]*"') | $(text_of forge_heartfire_held) | $(texts | grep -m1 'Forge with Heartfire ·')"
  tap_id forge_weapon >/dev/null || { bad "$id forges"; return; }
  wait_text "Suggested price" || { bad "$id shows the result"; shot ${id}_4_stuck; return; }
  shot ${id}_4_result
  local rar=$(rarity_line) leg=$(text_of card_legend)
  echo "RESULT: $(texts | sed -n 2p) | $rar | legend line [$leg]"
  expect "$id result is $2" "$rar" "$2"
  if [ -n "$3" ]; then expect "$id result says why it is Legendary" "$leg" "$3"; else [ -z "$leg" ] && ok "$id result has no legend line" || bad "$id result has no legend line" "$leg"; fi
  swipe_up; shot ${id}_4b_result_lower
  tap_id reveal_store >/dev/null || { $ADB shell input keyevent KEYCODE_BACK; $ADB shell sleep 1; }
  scroll_to_id forge_heartfire >/dev/null
  shot ${id}_5_forge_after
  expect "$id holds one Heartfire after the forging" "$(text_of forge_heartfire_held)" "Heartfire 1 of 3"
  local e=$(energy); echo "ENERGY after: $e"
  expect "$id spent six energy of ten" "$e" "4"
  open_storage || { bad "$id opens Storage"; return; }
  shot ${id}_6_storage
  tap_id stock_w1 >/dev/null && wait_sheet || { bad "$id opens the weapon's sheet"; return; }
  shot ${id}_7_sheet
  rar=$(rarity_line); leg=$(text_of card_legend)
  echo "SHEET: $rar | legend line [$leg] | deeds [$(text_of card_deeds)]"
  expect "$id sheet is $2" "$rar" "$2"
  if [ -n "$3" ]; then expect "$id sheet says why it is Legendary" "$leg" "$3"; else [ -z "$leg" ] && ok "$id sheet has no legend line" || bad "$id sheet has no legend line" "$leg"; fi
  scroll_to_id sheet_history >/dev/null && { shot ${id}_8_sheet_history; echo "HISTORY: $(lines_of sheet_history | tr '\n' '|')"; }
}
step_breakthrough() { heart heartfire_breakthrough "Legendary" "Forged with Heartfire"; }
step_miss() { heart heartfire_miss "Epic" ""; }

# Item 5: the wall card's preparation, then the siege card.
step_siege() {
  load siege_preparation || return
  tap_id nav_town >/dev/null; wait_id guild_list
  scroll_to_id guild_wall_prep >/dev/null || { bad "the wall card names a preparation"; shot siege_0_no_prep; }
  $ADB shell input swipe 540 1300 540 1050 600; $ADB shell sleep 1
  shot siege_1_wall_prep
  echo "WALL: $(lines_of guild_wall | tr '\n' '|')"
  echo "PREP: $(lines_of guild_wall_prep | tr '\n' '|')"
  [ "$(lines_of guild_wall_prep | wc -l)" -ge 2 ] && ok "the wall card names the preparation and its answers" || bad "the wall card names the preparation and its answers"
  swipe_up; shot siege_1b_wall_lower
  for i in 1 2 3 4 5 6; do $ADB shell input swipe 540 700 540 1700 300; done; $ADB shell sleep 1
  echo "BOARD siege_preparation:"; board siege_preparation
  tap_id nav_shop >/dev/null; tap_id end_day >/dev/null || return
  $ADB shell sleep 2; has_id end_day_confirm && { shot siege_2_end_day_confirm; tap_id end_day_confirm >/dev/null; }
  wait_id shopday_card || { bad "the evening opens"; return; }
  local found=""
  for i in $(seq 1 60); do
    if has_id shopday_siege; then found=1; break; fi
    has_id shopday_next || break
    tap_id shopday_next >/dev/null
  done
  [ -n "$found" ] || { bad "the siege card comes"; shot siege_3_stuck; return; }
  ok "the siege card comes"
  shot siege_3_siege_card
  echo "SIEGE CARD: $(lines_of shopday_siege | tr '\n' '|')"
  for n in 4 5 6 7; do $ADB shell input swipe 540 1300 540 700 600; $ADB shell sleep 1; shot siege_${n}_siege_card_scrolled; echo "SIEGE CARD (scroll $n): $(lines_of shopday_siege | tr '\n' '|')"; has_id shopday_siege_notes && echo "NOTES: $(lines_of shopday_siege_notes | tr '\n' '|')"; done
  has_id shopday_siege_notes && ok "the siege card has its cause lines" || echo "NOTE: shopday_siege_notes not on screen after scrolling (see the SIEGE CARD lines above)"
}

# Item 6: the supplier's two moods, today and after End Day.
step_supplier() {
  for m in grateful shaken; do
    local id=supplier_$m
    load $id || continue
    open_supplies || { bad "$id opens Supplies"; continue; }
    shot ${id}_1_day1_mood
    echo "MOOD day 1 ($m): $(text_of supplies_mood)"
    [ -n "$(text_of supplies_mood)" ] && ok "$id names the mood" || bad "$id names the mood"
    scroll_to_text "Silver" >/dev/null; $ADB shell input swipe 540 1300 540 1000 600; $ADB shell sleep 1
    shot ${id}_2_day1_stock
    for x in Silver Obsidian Starsteel Moonsteel; do echo "ROW day 1 $x: $(supply_row $x)"; done
    $ADB shell input keyevent KEYCODE_BACK; $ADB shell sleep 1
    skip_day || { bad "$id reaches the next morning"; shot ${id}_stuck; continue; }
    open_supplies || { bad "$id opens Supplies on day 2"; continue; }
    shot ${id}_3_day2_mood
    echo "MOOD day 2 ($m): $(text_of supplies_mood)"
    scroll_to_text "Silver" >/dev/null; $ADB shell input swipe 540 1300 540 1000 600; $ADB shell sleep 1
    shot ${id}_4_day2_stock
    for x in Silver Obsidian Starsteel Moonsteel; do echo "ROW day 2 $x: $(supply_row $x)"; done
    local want="2 left today"; [ $m = shaken ] && want="0 left today"
    expect "$id Silver stock on day 2" "$(supply_row Silver)" "$want"
    for i in 1 2 3 4 5 6 7 8; do swipe_up; done; shot ${id}_5_day2_lower
  done
}

# Item 7: the three weapons of legend_lines.
step_legends() {
  load legend_lines || return
  open_storage || { bad "legend_lines opens Storage"; return; }
  shot legends_0_storage
  # the storage list is lazy: a weapon further down is only in the dump once it is scrolled to
  local done_ids="" pass w ids
  for pass in 1 2 3 4; do
    ids=$(dump | grep -o 'resource-id="stock_w[0-9]*"' | sed 's/resource-id=//; s/"//g' | sort -u)
    for w in $ids; do
      case " $done_ids " in *" $w "*) continue;; esac
      done_ids="$done_ids $w"
      scroll_to_id $w >/dev/null; tap_id $w >/dev/null && wait_sheet || { bad "$w opens"; continue; }
      shot legends_${w}_1_sheet
      echo "SHEET $w: $(texts | sed -n 1,2p | tr '
' '|') $(rarity_line) | legend [$(text_of card_legend)] | deeds [$(text_of card_deeds)] | $(texts | grep -m1 -A1 '^Renown' | tr '
' ' ')"
      scroll_to_id sheet_history >/dev/null && { $ADB shell input swipe 540 1300 540 900 600; $ADB shell sleep 1; shot legends_${w}_2_history; echo "HISTORY $w: $(lines_of sheet_history | tr '
' '|')"; }
      for i in 1 2 3 4 5 6; do has_id sheet_back && break; $ADB shell input swipe 540 900 540 1400 300; $ADB shell sleep 1; done; tap_id sheet_back >/dev/null
      has_id storage_list || open_storage
    done
    swipe_up
  done
  echo "STORAGE weapons opened:$done_ids"
  [ "$(echo $done_ids | wc -w)" = 3 ] && ok "legend_lines has three weapons to open" || bad "legend_lines has three weapons to open" "$done_ids"
}

resume() { # after a relaunch: the main menu's Continue, then planning
  for i in $(seq 1 20); do
    local x=$(dump)
    if printf '%s' "$x" | grep -q 'resource-id="menu_continue"'; then tap_id menu_continue >/dev/null; break
    elif printf '%s' "$x" | grep -q 'resource-id="end_day"\|resource-id="forge_weapon"'; then break
    else $ADB shell sleep 1; fi
  done
}
# Item 8: a killed process, before a forging (the draft) and after one (the save).
step_kill() {
  load heartfire_miss || return
  recipe starsteel; heartfire_on
  shot kill_1_draft_before
  echo "DRAFT before: $(slots) | $(text_of forge_heartfire_held) | $(texts | grep -m1 '^Forge.*energy') | $(energy)"
  $ADB shell input keyevent KEYCODE_HOME; $ADB shell sleep 3
  echo "PID before kill: [$($ADB shell pidof $PKG | tr -d '\r')]"
  $ADB shell am kill $PKG; $ADB shell sleep 2
  local pid=$($ADB shell pidof $PKG | tr -d '\r')
  echo "PID after am kill: [$pid]"
  [ -z "$pid" ] && ok "the backgrounded process was killed" || bad "the backgrounded process was killed" "pid $pid"
  $ADB shell am start -n $PKG/.MainActivity >/dev/null; $ADB shell sleep 3
  shot kill_2_back_first_screen
  echo "FIRST SCREEN after the kill: $(texts | head -6 | tr '\n' '|')"
  resume
  has_id forge_workbench || { tap_id nav_forge >/dev/null; $ADB shell sleep 1; }
  shot kill_3_back_forge
  echo "DRAFT after the kill (top): $(slots)"
  scroll_to_id forge_heartfire >/dev/null; for i in 1 2 3; do sees "Heartfire comes from" && break; $ADB shell input swipe 540 1300 540 1000 600; $ADB shell sleep 1; done
  shot kill_4_back_heartfire
  echo "DRAFT after the kill: mode advanced $(dump | grep -o 'resource-id="forge_mode_advanced"[^>]*selected="[a-z]*"' | grep -o 'selected="[a-z]*"') | $(text_of forge_heartfire_held) | $(text_of forge_heartfire_state) | outlook shown: $(has_id forge_heartfire_outlook && echo yes || echo no) | $(texts | grep -m1 '^Forge.*energy') | $(energy)"
  expect "Heartfire held is still two after the kill" "$(text_of forge_heartfire_held)" "Heartfire 2 of 3"
  expect "no energy was spent by the kill" "$(energy)" "10"
  open_storage && { [ -z "$(dump | grep -o 'resource-id="stock_w[0-9]*"')" ] && ok "storage is still empty after the kill" || bad "storage is still empty after the kill"; $ADB shell input keyevent KEYCODE_BACK; $ADB shell sleep 1; }
  # now the forging, then a force-stop
  recipe starsteel; heartfire_on
  tap_id forge_weapon >/dev/null; wait_text "Suggested price" || { bad "the forging after the kill gives a result"; return; }
  shot kill_5_result
  echo "RESULT after the kill: $(texts | sed -n 2p) | $(rarity_line) | legend [$(text_of card_legend)]"
  tap_id reveal_store >/dev/null
  $ADB shell am force-stop $PKG; $ADB shell sleep 1; $ADB shell am start -n $PKG/.MainActivity >/dev/null
  wait_id menu_continue && shot kill_6_menu_after_force_stop
  resume; past_offers
  tap_id nav_forge >/dev/null; $ADB shell sleep 1; scroll_to_id forge_heartfire >/dev/null
  shot kill_7_forge_after_force_stop
  expect "Heartfire is one of three after the force-stop" "$(text_of forge_heartfire_held)" "Heartfire 1 of 3"
  expect "the energy stays spent after the force-stop" "$(energy)" "4"
  open_storage || { bad "Storage opens after the force-stop"; return; }
  shot kill_8_storage_after_force_stop
  has_id stock_w1 && ok "the forged weapon is in storage after the force-stop" || bad "the forged weapon is in storage after the force-stop"
  tap_id stock_w1 >/dev/null && wait_sheet && { shot kill_9_sheet_after_force_stop; echo "SHEET after the force-stop: $(texts | sed -n 1,2p | tr '\n' '|') $(rarity_line) | legend [$(text_of card_legend)]"; expect "it is still Epic" "$(rarity_line)" "Epic"; }
}

# Item 9: the same places at font scale 1.3. Screenshots only: clipping is judged by eye.
step_font() {
  $ADB shell settings put system font_scale 1.3
  echo "FONT SCALE: $($ADB shell settings get system font_scale | tr -d '\r')"
  load heartfire_breakthrough && {
    recipe starsteel; shot font_forge_1_top
    scroll_to_id forge_heartfire >/dev/null; has_id forge_heartfire_outlook || tap_id forge_heartfire >/dev/null
    shot font_forge_2_heartfire
    for n in 3 4 5; do $ADB shell input swipe 540 1300 540 900 600; $ADB shell sleep 1; shot font_forge_${n}_heartfire_scrolled; done
    has_id forge_heartfire_outlook && ok "font 1.3: the Heartfire outlook is open" || echo "NOTE: outlook id not on the last screen"
    open_supplies && {
      shot font_supplies_0_top
      scroll_to_text "Starsteel" >/dev/null; scroll_to_text "Moonsteel" >/dev/null; $ADB shell input swipe 540 1300 540 1050 600; $ADB shell sleep 1
      shot font_supplies_1_starsteel_moonsteel
      echo "ROW Starsteel (1.3): $(supply_row Starsteel)"; echo "ROW Moonsteel (1.3): $(supply_row Moonsteel)"
      swipe_up; shot font_supplies_2_lower
    }
  }
  load siege_preparation && {
    tap_id nav_town >/dev/null; wait_id guild_list; shot font_guild_0_top
    scroll_to_id guild_wall_prep >/dev/null; $ADB shell input swipe 540 1300 540 1050 600; $ADB shell sleep 1
    shot font_wall_1_prep
    swipe_up; shot font_wall_2_lower; swipe_up; shot font_wall_3_lower
    for i in 1 2 3 4 5 6; do $ADB shell input swipe 540 700 540 1700 300; done; $ADB shell sleep 1
    for i in 1 2 3 4 5 6 7 8 9 10 11 12; do dump | grep -q 'resource-id="guild_offer_' && break; swipe_up; done
    $ADB shell input swipe 540 1300 540 1000 600; $ADB shell sleep 1
    shot font_contract_1_card
    swipe_up; shot font_contract_2_card; swipe_up; shot font_contract_3_card
  }
  # a new guild run: the menu's Abandon, the charter, the three opening packages
  $ADB shell am force-stop $PKG; $ADB shell am start -n $PKG/.MainActivity >/dev/null
  wait_id menu_abandon && { shot font_menu_0_run; tap_id menu_abandon >/dev/null; shot font_menu_1_abandon_confirm; tap_id menu_abandon_confirm >/dev/null; }
  wait_id title_new_run
  shot font_menu_2_title_charter
  echo "CHARTER: $(text_of charter_name)"
  scroll_to_id title_new_run >/dev/null; tap_id title_new_run >/dev/null
  if wait_id opening_chooser; then
    ok "font 1.3: a new guild run opens on the opening chooser"
    echo "OPENINGS: $(dump | grep -o 'resource-id="opening_choose_[^"]*"' | sort -u | tr '\n' ' ')"
    shot font_opening_1
    # a package is the node opening_<id>; its Choose button can be scrolled past between two screenshots
    local pk='resource-id="opening_[a-z_]*"' own='opening_chooser\|opening_choose_\|opening_drawback_\|opening_confirm'
    local all=$(dump | grep -o "$pk")
    for n in 2 3 4 5 6 7 8 9; do $ADB shell input swipe 540 1300 540 700 600; $ADB shell sleep 1; shot font_opening_$n; all="$all $(dump | grep -o "$pk")"; done
    echo "PACKAGES: $(printf '%s\n' $all | sort -u | grep -v "$own" | tr '\n' ' ')"
    local count=$(printf '%s\n' $all | sort -u | grep -vc "$own")
    [ "$count" = 3 ] && ok "font 1.3: the chooser shows three packages" || bad "font 1.3: the chooser shows three packages" "$count"
  else bad "font 1.3: a new guild run opens on the opening chooser"; shot font_opening_stuck; fi
  $ADB shell settings put system font_scale 1.0
  echo "FONT SCALE restored: $($ADB shell settings get system font_scale | tr -d '\r')"
  $ADB shell am force-stop $PKG
}

STEPS="$*"; [ -z "$STEPS" ] && STEPS="supplies breakthrough miss siege supplier legends kill font"
for s in $STEPS; do echo "== $s"; step_$s; done
# never leave the device on the large font, whatever stopped a step
$ADB shell settings put system font_scale 1.0
echo RUNVARIETY_DONE
