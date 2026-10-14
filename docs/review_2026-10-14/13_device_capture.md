# 13 Device capture by the coordinator (2026-10-14, debug APK of a2e0392)

Device: a read-only Pixel AVD on emulator-5556, 1080x1920 at 420 dpi, font scale 1.0 unless stated. Fresh install, a guild
run (Town's Last Hope, opening "Break It on Purpose") through day 1 and End Day, then a classic run's first day.
Screens are in `device/coordinator/` (15 of the 60 taken). Each finding was seen, not read from source.

## Summary
1. The guild opening chooser is the "scenario selection" the owner means: three packages, each taller than a screen, all
   in 16 sp Regular tan prose (trait sentences, a relic paragraph, "The first days"), then a stock Material confirm dialog
   in 24 sp Regular. At font scale 2.0 one package needs four screens (`56_opening_font2.png`).
2. Body text reads thin everywhere: Regular weight, muted cream on near-black, 14 sp for most secondary lines. The hero and
   weapon sheets, the visitor sheet and the shop-day cards read well because they use bold labels and few lines.
3. The Shop at 1.0 clips the lead card's action ("Open storage") and the "On the shelf" row under the pinned End Day
   button until the list is scrolled (`10_guild_shop.png`); at 2.0 nothing but the intro strip and End Day is visible
   (`51_shop_font2.png`), and the Forge shows one line of its workbench (`50_forge_font2.png`).
4. The Forge opens with three unlabelled "Choose" chips (`1x_nav_forge.png`); the result card's Quality row is half
   hidden under the guidance box (`24_forge_result.png`).
5. Two coaching lines disagree on the Shop after the first sale: the lead says "Forge for today's buyers", the guided
   strip says "Forge one more, your way".
6. The weapon on the shelf appears twice on the Shop: in the shelf strip and again as a list row (`26_shop_with_blade.png`).
7. Back from the Shop opens the main menu and a second Back leaves the app with no confirm (the capture's scripted Back
   presses left the app twice). The run is saved, so nothing is lost; cause 10 of the 10-10 review is closed as far as it goes.
8. The debug title: "Scenarios (debug)" and "Combat sandbox (debug)" sit under "New game" as thin outlined peers; the
   scenario list itself is plain thin cards (`02_scenarios.png`).
9. The shop day (End Day cards) is the best-looking surface in the app: one fact per card, serif headline, a picture.
   The evening card's "Worth doing first" plate is the model for the Shop's lead card.
10. A classic run's day 1 starts with the relic dialog over the Shop (`53_classic_day1.png`): a 24 sp Regular title, three
    option rows whose borders are barely visible, and "Take none / Decide later" as the primary pair.

## Ranked findings
| # | Sev | Defect | Action | Evidence |
|---|---|---|---|---|
| 1 | high | Opening chooser is prose, not a comparison: 2+ screens per package, 3 gold buttons, confirm dialog repeats the drawback | Compact cards (name, build in one line, 2 signers, relic, one plus, one minus), one pinned "Begin with X"; confirm only names the drawback | `device/coordinator/07_opening_scrolled.png`, `56_opening_font2.png` |
| 2 | high | Thin text: body Regular 16/14 sp in muted cream on black | Body to Medium weight; secondary colour one step brighter; consequence lines in primary colour | every capture; `theme/Type.kt:23-27` |
| 3 | high | Shop lead card action and shelf row clipped under End Day; at 2.0 the content area is one line | Bottom padding = End Day height; intro strip collapses to one line once read; nav bar grows with font | `10_guild_shop.png`, `51_shop_font2.png`, `50_forge_font2.png` |
| 4 | low | A second Back on the main menu leaves the app with no confirm | Leave, or ask once | relaunch needed twice during capture |
| 5 | medium | Forge slots read "Choose · Choose · Choose" | Label each slot: Weapon, Metal, Augment | `1x_nav_forge.png` |
| 6 | medium | Forge result card hides its Quality row under the guidance box | Guidance box below the stats, or the card scrolls | `24_forge_result.png` |
| 7 | medium | Stock Material dialogs (opening confirm, relic, abandon) in 24 sp Regular, grey plate, no frame | One framed dialog wrapper with the Serif Bold title and the house buttons | `09_opening_confirm.png`, `53_classic_day1.png` |
| 8 | medium | Two coaching lines disagree after the first sale | One voice: the guided strip owns the Shop lead while the guidance runs | `26_shop_with_blade.png` |
| 9 | medium | The listed weapon is shown twice on the Shop | The list row carries the price and the buyer; the strip is the thumbnail only (or drop one) | `26_shop_with_blade.png` |
| 10 | medium | Debug entries styled as peers of New game; scenario list plain | Move under the gear; style the list rows with `forgeRow()` borders | `02_scenarios.png`, `52_classic_picker.png` |
| 11 | low | Charter picker carousel hides 3 of 4; "Charter 4 of 4" for "No charter" | Four rows, no counter | `52_classic_picker.png` |
| 12 | low | Guild board and roster are dense prose cards | Lead with the danger word and the reward, one line each; detail on the sheet | `30_guild_scrolled.png` |

## What works and must not be lost
- Shop-day cards, the hero sheet, the weapon sheet, the visitor sheet (radio rows with cost and effect, the default stated).
- The lead card idea ("Worth doing first") and the End Day caption that says what is pending.
- The forge tray flow once the chips are labelled: three taps, auto-close when complete.
