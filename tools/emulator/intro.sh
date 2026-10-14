#!/usr/bin/env bash
# The guided first day on a connected emulator: a new installation's first game from its first step to "Done", a
# process killed midway, the next game without it, the Settings row (replay queued and cancelled), then a second new
# installation that skips at once. Usage: ADB=/path/to/adb bash tools/emulator/intro.sh <screenshot-dir>
# (install the debug app first; set ANDROID_SERIAL when several devices are attached). The app's data is cleared.
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
shot() { $ADB exec-out screencap -p > "$OUT/$1.png"; echo "screenshot $1"; }
wait_text() { for i in $(seq 1 20); do if has "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for '$1'"; return 1; }
wait_id() { for i in $(seq 1 20); do if has_id "$1"; then return 0; fi; $ADB shell sleep 1; done; echo "TIMEOUT waiting for id '$1'"; return 1; }
check() { if "${@:2}"; then echo "CHECK $1: ok"; else echo "CHECK $1: FAIL"; fi; }
# the chosen charter row carries the id charter_name
charter_is() { dump | grep -q "text=\"$1\"[^>]*resource-id=\"charter_name\""; }
no_id() { ! has_id "$1"; }
start() { $ADB shell am start -n $PKG/.MainActivity >/dev/null; wait_text "Tiny Blacksmith"; }

$ADB shell pm clear $PKG >/dev/null
start && shot 01_title
check "title says the game is guided" has_id intro_menu_skip
tap_id title_new_run || exit 1
check "a new account starts on the shop alone" charter_is "No charter"
shot 01b_setup
tap_id setup_begin || exit 1
wait_id intro_strip && shot 02_step1_shop
check "step 1 on the Shop" has "Choose a weapon, a metal and an augment."
check "it points at the Forge" has "Open the Forge to do this."
check "no relic dialog over the first step" no_id relic_later
check "End Day is under the strip, untouched" has_id end_day
tap_id intro_why; shot 02b_why
check "Why? opens in place" has_id intro_why_text
tap_id intro_why
tap_id nav_forge || exit 1
shot 03_step1_forge
check "step 1 on the Forge explains the choice" has "The metal sets its strength"
tap_id forge_option_sword; tap_id forge_option_iron; tap_id forge_option_ember_resin
shot 04_step2
check "step 2" has "Forge it."
check "step 2 says the cost from the game" has "It costs 2 of your 10 energy"

# Killed here: a cold start comes back to the same run and the step the save shows.
$ADB shell am force-stop $PKG; start
tap_id menu_continue || exit 1
wait_id intro_strip && shot 05_after_kill
check "after a kill the guidance is still there" has "FIRST DAY"

tap_id nav_forge
has "Forge it." || { tap_id forge_option_sword; tap_id forge_option_iron; tap_id forge_option_ember_resin; }
tap_id forge_weapon || exit 1
wait_text "Suggested price" && shot 06_result
check "the result card carries step 3" has_id intro_card
check "and the way out" has_id intro_skip
tap_id reveal_list || exit 1
$ADB shell sleep 5; shot 07_step4
check "step 4, unassisted" has "Forge one more, your way."
# The recipe is still whole: the same again is a second forge too.
tap_id forge_weapon || exit 1
wait_text "Suggested price" && shot 08_second_result
check "no step line on the second result" no_id intro_card
tap_id reveal_store
tap_id nav_shop; shot 09_step5
check "step 5" has "End the day."
check "End Day does not ask for the relic the guidance keeps for later" has "6 energy unused"
tap_id end_day || exit 1
wait_id shopday_next && shot 10_shopday
check "no strip over the shop day" no_id intro_strip
tap_id shopday_skip; tap_id shopday_close
wait_id intro_strip && shot 11_outcome
check "the outcome names day 1" has "Day 1: "
check "it closes with Done" has_id intro_done
tap_id intro_done
$ADB shell sleep 1; shot 12_done
check "the strip is gone" no_id intro_strip

# Settings: completed, a replay waits for the next game, and can be cancelled.
has_id relic_later && tap_id relic_later
tap_id nav_settings; wait_id settings_intro && shot 13_settings
check "Settings says completed" has "Completed."
tap_id settings_intro_action; shot 14_replay_queued
check "the replay is queued and says so" has "Starts with your next new game."
tap_id settings_intro_action
check "cancelled: completed again" has "Completed."
$ADB shell input keyevent KEYCODE_BACK; $ADB shell sleep 1

# The next game: no guidance.
$ADB shell input keyevent KEYCODE_BACK; $ADB shell sleep 1
tap_id menu_abandon; tap_id menu_abandon_confirm
wait_id title_new_run && shot 15_title_again
check "no guidance line on the title" no_id intro_menu
tap_id title_new_run; tap_id setup_begin
$ADB shell sleep 3
has_id relic_later && tap_id relic_later
has_id opening_chooser || wait_id end_day
shot 16_second_game
check "the second game has no strip" no_id intro_strip

# A second new installation: skipped on the first step, and still off after a restart and in the next game.
$ADB shell pm clear $PKG >/dev/null
start; tap_id title_new_run; tap_id setup_begin
wait_id intro_strip && tap_id intro_skip
$ADB shell sleep 1; shot 17_skipped
check "skipped at once" no_id intro_strip
$ADB shell am force-stop $PKG; start; tap_id menu_continue
$ADB shell sleep 2
has_id relic_later && tap_id relic_later
check "still off after a restart" no_id intro_strip
tap_id nav_settings; wait_id settings_intro
check "Settings says off" has "Off."
shot 18_settings_off
echo "done"
