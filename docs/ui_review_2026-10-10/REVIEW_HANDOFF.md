# Tiny Blacksmith: review handoff for the four UI/UX batches (2026-10-10)

This is the evidence for reviewing the four UI/UX improvement batches together. It says what was built, on which
revision, what was checked and how, and what is still wrong. It proposes no further redesign.

Screenshots and passing tests show that screens draw and say what they should at the sizes tried. They do not show
that the game is easy to use; nobody outside the sessions has played these builds.

## 1. What was tested

| | |
|---|---|
| Branch | `ui-batch-4`, in the worktree `.claude/worktrees/ui-batch4`. Not merged into `main`, not pushed. |
| Revisions | `39c7bb9` (the batch) and `bdee417` (one follow-up: the shop day with text above 1.3). Parent: `e1fe41c` (batch 3 on `ui-batch-2`, also not on `main`), which sits one commit above `main` at `796ce32` (batches 1 and 2). |
| App | 0.7.0, versionCode 7, debug build. Rules 3, save schema 4, balance 8, content 3: unchanged by all four batches. |
| Installed builds | `app-debug.apk` built from `39c7bb9`, SHA-256 `f7447a6194a3…`; and from `bdee417`, SHA-256 `b6d3a5420b3d…`. The journey script compared the installed APK's hash with the local file before and after each run. |
| Device | Android emulator, AVD `carbscan`, Android 16 (API 36), started read-only. Sizes were set with `wm size` / `wm density`: 1080x1920 at 420 dpi (411x731 dp) and 720x1280 at 320 dpi (360x640 dp). It is not the Pixel_10_Pro AVD of the 0.7.0 gates and not a phone. |
| Isolation | Another session's device tests installed its own build on this emulator once during the work. Everything captured then was thrown away, and the emulator was restarted on ports only this session's adb server uses. All evidence below is from after that. |

Which revision each piece of evidence comes from:
- The six layout journeys (section 4 and the screenshots without "preview" in their names): `39c7bb9`.
- The "preview" screenshots, the request card and the device test suite: `bdee417`.
- `bdee417` differs from `39c7bb9` only in `ShopDayScreen.kt`, and only when the font scale is above 1.3 (a lower scene;
  no first-day hint on a screen under 700 dp high). The font 2.0 journeys were therefore run on the build before that change.

## 2. The four batches in short

**Batch 1: forge, choose a price, list** (`5aa313e`, on `main`). The forge result carries the price: −10, a typed
number, +10, "List at N" and Store. Under it, "N of M heroes in town can afford this price", counted from the saved
purses by the counter's own rule. A full shelf greys "List at" with the reason. A notice says where the blade went. A
blade forged from a request keeps the request on its card.

**Batch 2: navigation and action hierarchy** (`dea85f7`, `aa57770`, `37079ba`, on `main`). Each destination keeps its
scroll position and open step. Back closes what is open, returns to the Shop, then opens the main menu (it used to leave
the game). End Day is on the Shop and the Forge only, further from the tab bar, and an outline on the Forge. Storage and
a blade's details are one sheet. Stock actions say what they did.

**Batch 3: rewards and consequences** (`e1fe41c`, on `ui-batch-2`). The forge result shows the blade alone for 0.7 s
before its numbers fade in, with the price and buttons live throughout. A sale has a gold band and "Earned today
before → after"; a refusal leads with its recorded reason and numbers. A day with fights and no other card gets "Out in
the field". The opening card, the strip, the plate and the last card stop repeating each other.

**Batch 4: consistency, readability, accessibility** (`39c7bb9`, `bdee417`, on `ui-batch-4`).
- Actions: three components for three kinds of action (gold plate, bronze outline, gold inline). The Gazette's Close,
  "Alright", "Try again", "Start over", the blessing choices and every other plain button go through them. The Records
  tabs have no check mark.
- Names: "Supplies", "request", "heroes" and "Gazette" are each used the same way. "Up to 6 visitors a day · shelf 0 of 8",
  "Requests · N" with a line under it, "Forge integrity 100", "Not tried yet. Forge it to learn about this pairing."
  Playback reads "Manual", "Auto 1x", "Auto 2x". The skip is "Skip to evening" in all three places.
- Readability: the Records masthead and its Hide/Show control are apart. Town leads with the siege date and "Siege
  outlook" with both numbers, then the supporting rows. Empty champion places state the engine's rule. "Worn" follows the
  engine's threshold (50; the screens used 70).
- Controls: a tap on the day strip beside its controls no longer advances a card. The strip's controls wrap under the
  label instead of cutting it. Above font 1.3 the Forge is one scrolling page (the steps had about one line of height at
  2.0). The first-day hint is not shown to a screen reader exploring by touch. Greyed Buy, upgrade and "Begin era"
  buttons say why.

Details and reasons: `docs/DECISIONS.md` (one section per batch) and `CHANGELOG.md`, `[Unreleased]`.

## 3. Screenshots

Batch 4: `docs/ui_review_2026-10-10/improvement_4/`, 79 files. Names are `<size in dp>_<font scale>_<what is on screen>`;
`font10` is 1.0, `font13` is 1.3, `font20` is 2.0. Day cards are named by what their own text says (`sale`, `refusal`,
`till`, `evening`). Earlier evidence is untouched: `improvement_1/` (batch 1), `improvement_3/` (batch 3, taken before its
rebase, so with older art), and the numbered files of the original review. Batch 2 has no screenshots.

| Journey | 411x731, font 1.0 | 360x640, font 1.3 | Other four layouts |
|---|---|---|---|
| Shop on day 1 | `…_shop_day1_empty_shelf` | same | same |
| Forge, recipe ready | `…_forge_recipe_ready` | same | same |
| Forge result, typed price, listed | `…_forge_result_first_view`, `…_typed_9999_nobody_can_pay`, `…_forge_after_list_notice`, `…_shop_two_blades_listed` | same | result only; at 2.0 also `…_price_after_scroll` |
| Sale and refusal | `…_day1_sale`, `…_day1_refusal` | `…_day1_02_sale`, `…_day1_03_refusal` | same |
| Till, evening, Gazette over the evening card | `…_day1_05_till`, `…_day1_07_evening`, `…_day1_gazette_over_the_evening_card` | same | evening only |
| Restart prompt | `…_day2_restart_prompt` | same | not copied |
| Storage and Supplies | `…_storage_blade_sheet`, `…_supplies_sheet_from_shop` | same | not copied |
| Town and Records | `…_town_outlook_and_standing`, `…_town_champions_and_heroes`, `…_records_gazette` | same | first and third |
| Request paid (debug preview of an engine-resolved day) | `411x731_font10_preview_request_paid_card` | | |
| Large-text day cards after the follow-up | | `360x640_font13_preview_*` | `360x640_font20_preview_*` (with `…_before_the_followup`), `411x731_font20_preview_*` |

Looked at by the session, not only captured: the Shop, Town, Records, forge result, sale, refusal, evening, Gazette,
restart prompt and blade sheet at 411x731 font 1.0; the Shop, Forge, sale, refusal, Town, Records and Supplies at 411x731
font 2.0; the sale, till, evening, Forge, Shop and Supplies at 360x640 font 2.0; the Forge and a refusal at 411x731
font 1.3; the Shop and evening at 360x640 font 1.0; the till at 360x640 font 1.3; all eight preview cards. The remaining
files were captured by the script and not opened.

## 4. Verification

**Automated checks**

| Check | Revision | Result |
|---|---|---|
| Core JVM tests (`:core:test`) | `bdee417` | 395 pass |
| App JVM tests (`:app:testDebugUnitTest`) | `bdee417` | 142 pass (141 + `conditionWordsFollowTheEnginesWornLine`) |
| `:app:assembleDebug :app:compileDebugAndroidTestKotlin :app:assembleRelease :app:lintDebug`, from scratch | `39c7bb9` | pass. Not rerun on `bdee417` (the debug build and the device tests were). |
| Device tests (`:app:connectedDebugAndroidTest`, 411x731 dp, font 1.0) | `bdee417` | 70 of 71. New in this batch and passing: the Gazette row and tabs, Town's outlook order and champion text, a tap on the strip, the Forge's height at font 2.0. **One fails: `StorageSheetTest.bulkSalvageAsksOnceAndIssuesOneCommandPerBlade`**, the test batches 1 and 3 recorded as failing on untouched `main` on this AVD. It was not looked into again. |
| `tools/emulator/smoke.sh`, `runend.sh` | | **Not run.** Both were edited for the new wording ("Up to 6 visitors a day…", "Skip to evening") and are untested. |

**Scripted journeys in a live game** (a new game each time; every tap by script; 17 checks per layout)

The 17 checks: the plate's wording; shelf count after two listings; Storage opens, a blade opens in it, Back returns to
the list, Back closes it; Supplies opens from the Shop with its title, a purchase, Back closes it; Supplies opens from
the Forge; Back then Next returns to the same card; a sale card and a refusal card appear; closing the Gazette leaves the
evening card; after a kill the prompt offers "Resume the day" and "Skip to evening"; Resume lands on the same card; after
a second kill "Skip to evening" lands on the evening card; Back from it stays in the day; the strip's skip lands on the
evening card; "Begin day 3" opens the Shop.

| Layout | Result on `39c7bb9` |
|---|---|
| 411x731 dp, font 1.0 | 17 of 17 |
| 411x731 dp, font 1.3 | 17 of 17 |
| 411x731 dp, font 2.0 | 17 of 17 |
| 360x640 dp, font 1.0 | 17 of 17 |
| 360x640 dp, font 1.3 | 16 of 17. "Supplies opens from the Forge" was not exercised: the script tapped the Forge tab while a sheet was still closing and stayed on the Shop. It passed at this layout on an earlier build of this batch whose screenshots were not kept. |
| 360x640 dp, font 2.0 | 15 of 17. "Supplies opens from the Shop" was not reached (the script's tap on the last row of the list missed). "A sale was seen" failed because the sale card's text was off screen; that is the defect the follow-up `bdee417` addresses, and the full journey was not rerun after it. |

**Checked by hand on the emulator, once, at 411x731 font 1.0, on an early build of this batch:** scrolling a day card, a
short drag on it, a tap on a visitor's name (the hero sheet opens) and Back out of the sheet do not advance the card; a
tap on the greyed Back on the first card does nothing; system Back on the first card opens the main menu.

**Not verified**

| Scenario | State |
|---|---|
| TalkBack on the core journey | **Not verified.** TalkBack is installed on the AVD and was switched on once; its own permission dialog covered the game and the AVD has no speech engine running. Spoken labels, focus order and the disabled-button reasons are covered by semantics in code and by a few device-test assertions only. Nobody listened. |
| Accepted request → forge result → handover, in a live game | **Not verified in this batch.** A request was offered on day 1 of one run and seen in the Gazette, but the journey was not played. The paid card is seen in the debug preview only; the result card with its request is covered by batch 1's tests. |
| Reduced motion | Not looked at in this batch. Batch 3 filmed the reveal with it on. The strip's greyed playback control with reduced motion was not seen. |
| Touch targets | Not measured on a device. `LayoutMatrixTest` checks widths, not heights. The new Gazette row asserts 48 dp. |
| Focus order with a keyboard or switch | Not tried. |
| The error dialog, the load and save failure screens, the blessing dialog and card, the run-end screen with their new buttons | Not seen on a device in this batch. Their device tests pass where they exist. |
| The Forge with a material out, Auto 1x / 2x playing a real day, a champion place that is empty after day 1 | Not seen. |
| A physical phone, the Pixel_10_Pro AVD | Nothing run. |

## 5. Remaining issues, by player impact

1. **Screen readers are untested.** Everything said about accessibility in these four batches rests on code and tests.
2. **360x640 with the largest text is still cramped during the shop day.** After the follow-up the sale card shows its
   band, "Earned today" and the blade's name without a scroll, and the rest by scrolling; before it, one line. The first-day
   hint is not shown at this size, and it still counts as seen, so these players never get it. On the Forge the "Forge weapon" button is a scroll away after each forge.
3. **At font 2.0 the Shop's shelf and storage rows are very tall.** The text column beside the blade's picture is narrow, so
   "Guardians and Duelists and Battlemages favour the sword" takes four lines and "Battlemages" breaks mid-word at 360 dp.
4. **At 360x640 with font 1.3 the till card's picture pushes its rows below the fold.** The total is a scroll away.
5. **The evening card's lead says "customers" and "yesterday"** ("5 customers left over the price yesterday") for the day
   just watched. It is core's line, written for the next morning.
6. **"Up to 6 visitors a day" wraps to two lines** at 360 dp with text above 1.0. Readable, but the plate is taller.
7. **Records and event sentences still say "commission"** in the Gazette's prose ("collected the commissioned…"), and the
   era's first line reads "under a restless roads". Both are stored event text and were left.
8. **The bulk-salvage device test fails on this AVD** and has done since batch 1. Whether the test or the screen is at
   fault there is unknown.
9. **The tested stack is three branches deep.** Batch 3 and batch 4 are not on `main`; `gameplay-depth` and `post-0.7.0`
   touch the same files.
10. Left from the reviews on purpose, for the combined review: the blessing offer as a system dialog, the spinner on the
    first frame, the Mode and Risk segmented controls with check marks, "Who is buying", "hale", the rarity glyphs.
