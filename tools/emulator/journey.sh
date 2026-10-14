#!/usr/bin/env bash
# The whole way from the anvil to the notebook, on a connected emulator, in a seeded guild run (debug build): forge a
# bronze sword, loan it, send the party down the supplier's road, read the evening's fight, open the notebook's fight
# notes, kill the process and look again. With the sword in the Guardian's hands an enemy breaks their Guard and
# "A broken Guard answers" is seen in a real fight. In the other member's hands it stays suggested.
# Usage: ADB=/path/to/adb ANDROID_SERIAL=emulator-5562 bash tools/emulator/journey.sh <dir> <seed> <opening-id> <member-name> seen|suggested
# Seed 9 on the tree this was written for: opening stormwell, Urien the Guardian (seen), Wren the Warden (suggested).
set -u
export MSYS_NO_PATHCONV=1
ADB=${ADB:-adb}
OUT="$1"; SEED="$2"; OPENING="$3"; WHO="$4"; EXPECT="$5"; mkdir -p "$OUT"
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
has_any() { dump | grep -q "$1" ; }
has_id() { dump | grep -q "resource-id=\"$1\""; }
has_pre() { dump | grep -q "resource-id=\"$1"; }
# taps the node whose resource-id starts with $1
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
# taps the first node whose text starts with $1 and lies below the first node whose text starts with $2 (when given)
tap() {
  local x=$(dump | python -c "
import sys,re
s=sys.stdin.read()
def nodes(t): return [tuple(map(int,m)) for m in re.findall(r'text=\"'+re.escape(t)+r'[^\"]*\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]', s)]
below=0
anchor=sys.argv[2] if len(sys.argv)>2 else ''
if anchor:
  a=nodes(anchor)
  if not a: sys.exit()
  below=a[0][1]
for x1,y1,x2,y2 in sorted(nodes(sys.argv[1]), key=lambda n:n[1]):
  if y1>=below: print((x1+x2)//2,(y1+y2)//2); break
" "$1" "${2:-}")
  if [ -z "$x" ]; then echo "MISSING: $1 ${2:+below $2}"; return 1; fi
  $ADB shell input tap $x; echo "tapped '$1' at $x"; $ADB shell sleep 1.2
}
# taps the lowest node on screen whose text or description contains $1 (a contract's row says its name in either)
tap_has() {
  local x=$(dump | python -c "
import sys,re
s=sys.stdin.read()
ms=re.findall(r'<node[^>]*?(?:text|content-desc)=\"[^\"]*'+re.escape(sys.argv[1])+r'[^\"]*\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]', s)
if ms:
  x1,y1,x2,y2=map(int,ms[-1]); print((x1+x2)//2,(y1+y2)//2)
" "$1")
  if [ -z "$x" ]; then echo "MISSING anywhere: $1"; return 1; fi
  $ADB shell input tap $x; echo "tapped what says '$1' at $x"; $ADB shell sleep 1.2
}
shot() { $ADB exec-out screencap -p > "$OUT/$1.png"; echo "screenshot $1"; }
wait_text() { for i in $(seq 1 25); do if has "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for '$1'"; return 1; }
wait_id() { for i in $(seq 1 25); do if has_id "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for id '$1'"; return 1; }
check() { if "$@" >/dev/null 2>&1; then echo "CHECK $*: ok"; else echo "CHECK $*: FAIL"; fi; }
swipe_up() { $ADB shell input swipe 540 1300 540 700 500; $ADB shell sleep 1; }
to_top() { for i in 1 2 3 4 5 6; do $ADB shell input swipe 540 700 540 1700 300; done; $ADB shell sleep 1; }
scroll_to_id() { for i in 1 2 3 4 5 6 7 8 9 10; do if has_pre "$1"; then return 0; fi; swipe_up; done; echo "SCROLL MISSING id: $1"; return 1; }
scroll_to_text() { for i in 1 2 3 4 5 6 7 8 9 10 11 12; do if has "$1"; then return 0; fi; $ADB shell input swipe 540 1300 540 900 500; $ADB shell sleep 1; done; echo "SCROLL MISSING text: $1"; return 1; }
texts() { dump | grep -o 'text="[^"]\+"' | head -n "${1:-80}"; }
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
# The notebook's fight notes, and what they say about the path.
notes() {
  tap_id nav_records; $ADB shell sleep 1
  tap_id page_journal; $ADB shell sleep 1
  tap_id book_tab_fights; $ADB shell sleep 1
  scroll_to_id book_interaction_guard_answer
  # the row sits under a pinned bar at times: bring it well into view
  $ADB shell input swipe 540 1300 540 1000 500; $ADB shell sleep 1
  shot "$1"
  local x=$(dump)
  echo "--- fight notes ($1)"; printf '%s' "$x" | grep -o 'text="[^"]\+"' | grep -i -B1 -A4 "broken Guard answers" | head -12
  echo "COUNT rows of the path: $(printf '%s' "$x" | grep -o 'resource-id="book_interaction_guard_answer"' | wc -l)"
  if printf '%s' "$x" | grep -q 'Seen in a real fight'; then echo "STATE $1: seen in a real fight"; else echo "STATE $1: not seen"; fi
  if printf '%s' "$x" | grep -q 'Possible, not seen yet'; then echo "STATE $1: a path is possible, not seen yet"; fi
}

$ADB shell pm clear $PKG >/dev/null
$ADB shell am start -n $PKG/.MainActivity --el seed "$SEED" >/dev/null
# The seeded run is begun behind the menu: go on into it.
wait_text "Continue run" && tap_id menu_continue
wait_id opening_chooser || { shot 00_no_chooser; texts; exit 1; }
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do has_id "opening_choose_$OPENING" && break; swipe_up; done
# at a large font the button first shows as a sliver at the bottom edge: bring it up before tapping
$ADB shell input swipe 540 1300 540 1000 500; $ADB shell sleep 1
shot 01_opening
tap_id "opening_choose_$OPENING" || exit 1
tap_id opening_begin || exit 1
past_offers || { shot 00_stuck; texts; exit 1; }

# 1. Forge the weapon whose interaction is known: bronze answers the blow that breaks its wielder's Guard.
tap_id nav_forge || exit 1
tap_id forge_option_sword; tap_id forge_option_bronze; tap_id forge_option_ember_resin
scroll_to_id forge_outlook; shot 02_outlook
echo "--- outlook"; texts 60 | grep -i -A3 "bronze\|needs" | head -20
tap_id forge_weapon || { scroll_to_id forge_weapon && tap_id forge_weapon; } || exit 1
$ADB shell sleep 2; shot 03_forged
tap_id reveal_store || tap_id reveal_list || exit 1

# Before any fight: the path is suggested by the rules, nothing more.
notes 04_notes_before

# 2. Loan it through the member's sheet.
tap_id nav_town || exit 1
wait_id guild_list
# members are on the Company part of the Guild
tap_id guild_tab_company; $ADB shell sleep 1
for i in 1 2 3 4 5 6 7 8 9 10; do has "$WHO" && break; swipe_up; done
tap "$WHO" || exit 1
wait_id sheet_member; shot 05_member_sheet
scroll_to_id sheet_loan_ && tap_id sheet_loan_
$ADB shell sleep 1; shot 06_member_loaned
echo "--- member sheet"; texts 60 | grep -i "sword\|carries\|loan" | head -8
tap_id sheet_close || $ADB shell input keyevent KEYCODE_BACK; $ADB shell sleep 1

# 3. A real contract: the supplier's road, both members.
tap_id guild_tab_contracts; $ADB shell sleep 1
to_top
for i in 1 2 3 4 5 6 7 8 9 10; do has "Bring a wagon" && has "Choose a party" && break; $ADB shell input swipe 540 1300 540 1000 500; $ADB shell sleep 1; done
tap "Choose a party" "Bring a wagon" || tap_has "Supply run" || { shot 00_no_offer; texts 120; exit 1; }
wait_id contract_sheet
check has "Supply run"
wait_id contract_sheet
tap_id contract_pick_ 0; tap_id contract_pick_ 1; shot 07_contract_party
echo "--- who goes"; texts 40 | grep -i "level" 
scroll_to_id contract_send && tap_id contract_send
$ADB shell sleep 1
tap_id nav_shop; $ADB shell sleep 1
tap_id end_day || exit 1
$ADB shell sleep 2
has_id end_day_confirm && tap_id end_day_confirm

# 4. The evening: the contract card with its whole fight.
SAW_CARD=no
for n in 10 11 12 13 14 15 16 17 18 19; do
  $ADB shell sleep 2
  has_id shopday_card || has_id shopday_progress || break
  if has_id shopday_contract; then
    SAW_CARD=yes; shot ${n}_contract_card
    for k in 1 2 3 4; do has_id fight_toggle && break; swipe_up; done
    tap_id fight_toggle
    FIGHT=""
    for k in 1 2 3 4 5 6 7 8 9 10 11 12; do FIGHT="$FIGHT$(dump | grep -o 'text="[^"]\+"')"; $ADB shell input swipe 540 1400 540 700 600; $ADB shell sleep 1; [ "$k" = 2 ] && shot ${n}b_contract_fight; done
    shot ${n}c_contract_fight_end
    echo "--- the fight's lines that name bronze or a broken Guard"; printf '%s\n' "$FIGHT" | grep -i "rings\|bronze sword strikes\|breaks.*guard\|guard.*breaks\|guard.*broken" | sort -u | head -12
    if printf '%s' "$FIGHT" | grep -qi "Bronze rings back\|rings back"; then echo "FIGHT: the bronze rule is in the report"; else echo "FIGHT: the bronze rule is not in the report"; fi
  fi
  tap_id shopday_next || { tap "Begin day"; break; }
done
echo "CHECK contract card shown: $SAW_CARD"
has_id shopday_skip && tap_id shopday_skip
$ADB shell sleep 2
past_offers

# 5. The notebook after the fight.
notes 20_notes_after
# 6. Process recreation: background, killed by the system, brought back.
$ADB shell input keyevent KEYCODE_HOME; $ADB shell sleep 2
$ADB shell am kill $PKG; $ADB shell sleep 1
echo "pid after kill: '$($ADB shell pidof $PKG)'"
$ADB shell am start -n $PKG/.MainActivity >/dev/null; $ADB shell sleep 4
has "Continue run" && { tap_id menu_continue; past_offers; }
notes 21_notes_after_recreation
# 7. A hard stop: the task is thrown away and the run is read from the save alone.
$ADB shell am force-stop $PKG; $ADB shell sleep 1
$ADB shell am start -n $PKG/.MainActivity >/dev/null
wait_text "Continue run" && tap_id menu_continue
past_offers
notes 22_notes_after_force_stop
echo "EXPECTED: $EXPECT"
echo "done"
