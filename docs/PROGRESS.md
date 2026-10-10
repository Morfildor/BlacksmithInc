# Progress — 2026-10-10 (major update, final state at about 13:00)

## Current phase
**Update, 2026-10-10 (afternoon):** at the owner's request the history was rebuilt as one commit per task (no merge or
ledger-only commits; same files) and pushed to `main`. `main` now holds 0.7.0 as a debug-tested development build; it is
still not a store release. Commit hashes quoted below and in the ledger are those of the working branch
(`major-update` on GitHub), not of `main`.

P7b, the major update (the "shop day"), is built as app version **0.7.0** (versionCode 7) on the integration branch
`shop-day/m0`, which is pushed to GitHub as the branch `major-update`. It is **not released**: `main` is still 0.6.0.
Everything is merged; no agent branch holds unmerged work. The last commit that changed code or tests is `2eca650`;
later commits (`379b9b0`, `bceb326`, this one and the version bump) are documents and the version number.
Versions: rules 3, save schema 4, balance config 8, content 3.
The plan is `docs/MAJOR_UPDATE_PLAN.md`; the row-per-task tracker with evidence is `docs/MAJOR_UPDATE_LEDGER.md`;
rulings, known issues and open decisions are in `docs/DECISIONS.md` ("Major update: rulings and open decisions
(2026-10-10)"); the balance review is in the same file ("Balance v8 at 10,000 seeds").
The sections from "Session 6 focus" down to "Known limitations" are the history of 0.6.0 and earlier, kept as written.

**Short version.** The plan's milestones M0 to M4 and most of M5 and M6 are merged. On the tip 393 core and 122 app JVM
tests pass and the debug APK builds. The final device gate on the emulator passed in full: 56 of 56 device tests, the
smoke script (including resume after the process is killed), the run-to-defeat script, and a walk-through of the main
menu including Abandon run. An independent read of the whole branch led to three fixes; five smaller issues are left
open and listed. The balance review at 10,000 seeds is done and changed no number, but **the plan's exit condition
"both bands hold at 10,000 seeds" is not met**: the plain smith's first era is slightly longer than the band allows at
every seed, and nine other lines fail; all are owner decisions. What nobody has done: look at the newest screens with
large text or on a small screen, run TalkBack, take screenshots of the final build, or run anything on a physical
phone. The owner chose to test by hand; the list is at the end of this block.

**Where the build is.** `app/build/outputs/apk/debug/app-debug.apk` in the worktree `.claude/worktrees/shop-day`,
built with `./gradlew :app:assembleDebug`.

### How to read the verification words
- **Seen on the emulator**: a person or a script ran it on the Android virtual device (Pixel_10_Pro) and looked or
  checked. "Agent tree" means an agent saw it on its own copy before the merge.
- **Device tests pass (final gate)**: automated tests that draw the screen on the emulator passed in the last device
  run, on commit `2eca650`, at 1080x1920, density 420, font scale 1.0. This proves the screen draws and says the right
  thing at that one size. It does not prove it looks right, and nobody looked.
- **JVM tests only**: automated tests on the development machine pass; it was never run on an emulator or phone.
- **Not built** and **blocked** (waits for something outside the repository) mean what they say.
- "Simulator" means the headless balance harness that plays thousands of runs with scripted players ("bots").
- Nothing below was checked on a physical phone.

## What is in the build, by area (0.7.0 on `shop-day/m0`)

### Saves and recovery
- Every operation that changes the game (a command, a legacy purchase, Claim, Begin era, Abandon) goes through one
  serialized session, so two quick taps cannot lose a purchase or overwrite a new era. *JVM tests, and seen on the
  emulator: a force-stop after Claim reopens claimed with the upgrades, and a kill around End Day never left half a
  day. In the final gate the smoke script resumed correctly after a process kill on day 2.*
- A save that cannot be opened shows a recovery screen (Try again, or Start over with the unreadable run kept as a
  backup) instead of crashing. A damaged file is left untouched until the player confirms. A damaged legacy record
  can be rebuilt from the copy the run carries, and a run that has no legacy record beside it now reads the legacy it
  carries (a fix from the final review; before, a "Start over" cut short between two writes could leave an empty
  legacy). *Seen on the emulator for a damaged row, a damaged database file and a damaged legacy row. JVM tests only:
  the review fix, the "save is from a newer version" and "incompatible" screens, the "could not confirm" dialog.*
- The game checks a save's rules, content and schema versions on load and migrates older saves (fixtures for schemas
  1, 2 and 3 are in the tests). *JVM tests; an old-format day-1 save opened and accepted End Day on the emulator.
  Installing 0.7.0 over a real 0.6.0 install was not tried on a device.*
- Known gaps: a database file cut to 0 bytes opens as a new game with no message (not tried on a device); file damage
  that appears while playing shows the general "could not save" dialog until the next start.
- Android's automatic cloud backup of the save is switched off until restoring one is tested.

### The shop day (what happens after End Day)
- End Day saves the day first, then shows it card by card: the shop opens, each featured customer at the counter as a
  framed portrait with what they looked at and why they bought or left, a receipt for a sale, the other visitors as a
  tally, the till, up to three cards of what happened beyond the door, then one lead for tomorrow (or the blessing
  choice, or the fall of the forge). Next, Back, Skip day, and 1x / 2x auto-advance. *Seen on the emulator earlier;
  device tests, the smoke script and the run-to-defeat script pass in the final gate.*
- The place reached in the day is saved. Killing the game mid-day offers "Resume the day" or "Skip to tomorrow";
  watching, skipping or killing never changes what happened. *Seen on the emulator (same card after a force-stop, ten
  kills within 400 ms of End Day), and three device tests over a real save file pass in the final gate.*
- Onboarding: day 1 opens on the lead "Forge your first blade", and the first customer card shows one hint, once: "Tap
  anywhere to continue · Skip day jumps to the evening". *JVM and device tests pass (final gate); the hint was not
  looked at by a person.*
- Card timings were shortened (open 1200 ms, arrive 1000, browse 900, decide 1500, tally 2000) so an unattended day
  at 1x has a median of 24.6 seconds against a budget of 25 with the larger town. *JVM test that sums the timings; not
  timed on a device. Whether the pace feels right is an owner decision.*
- A day the counter cannot lay out no longer blocks the game: it counts as watched and the Shop opens. *JVM test only.*
- Never seen in the real app flow on a device: the blessing card, a siege aftermath card, a "fell at the wall" card,
  the replay overlay (seen in a preview only), the cards at font scale 1.3 or 2.0, TalkBack.
- Departures from the plan: one card per featured visit (not four), the replay overlay is text only.

### Customers and town
- Fair seating: every living hero gets an equal turn at the counter. *JVM tests and simulator at 10,000 runs: served
  ratio by list position 1.03 to 1.04 (it was about 2.4 to 1 in 0.6.0).*
- A town of 12 heroes with room for 16, all five classes from the first morning, six counter seats (seven or eight with
  the Signboard), raid pressure retuned to keep run length. *JVM tests and simulator at 10,000 runs.*
- 120 first names and 96 surnames, no shared names among the living; lineages matched by ID. A stored face per hero,
  spread evenly within a class. *JVM tests and simulator.* The owner's 20 hero portraits (base and upgraded face) are
  imported. *All 40 tiles viewed in the renderer on the emulator; the upgraded face was not seen in a live game. Two
  living heroes still share a face on about a third of days, because some classes have three or four faces.*
- Recognition lines at the counter ("first blade from your forge", "a regular returns", "held the wall"). *JVM tests;
  one line seen on the emulator on day 1; the regular's mark not verified there.*
- Guild Patronage is now a willing guild plus a 30-gold stipend per member. *JVM tests and simulator. Its own target
  is not met: open owner decision.*
- A champion can fall on the walls when a siege is lost badly (the rout rule). *JVM tests and simulator.*
- Earlier engine corrections: champions ranked against the foe they will face; the ore merchant's stock is on sale the
  next morning; faction ties break the same way everywhere, and since the final review the Town page names the same
  besieger as the Shop and the Forge; the Gazette's tally and the archived day are complete; the collector's price is
  capped. *JVM tests and simulator only.*

### Demand: wants, the besieger, requests, legends, the journal (milestone M4)
- Standing wants: a hero who leaves with nothing leaves a want (kind of weapon, strength, purse) for three days.
- Buyers weigh a blade's properties and fame; a sidegrade can be bought once for a reason; before a siege the town
  wants the element the besieger fears and passes over the one it resists.
- The journal's clue ladder (four clues per hidden recipe), rumours, and "Use this recipe".
- A returned legend is the blade it was, with its properties dormant until honed once; shorter weapon names.
- Requests have reasons (replacement, a blade for the wall, collector, a newcomer's first blade); two can be open.
- *Rules: JVM tests and simulator at 10,000 runs. Screens (wants in "Who is buying" with "Forge this", the besieger
  line on the Shop and Forge plates, the "why" on a request, Story and Dormant on the blade sheet, the Legend Board in
  full, the ladder in Records): JVM tests and six device tests pass (final gate). **Nobody has looked at these
  screens**, and they were not run at font scale 1.3 or 2.0.*

### The planning screens
- Four destinations (Shop, Forge, Town, Records) and a settings sheet replace the seven tabs. *Seen on the emulator at
  font scale 1.0, 1.3 and 2.0 (before the polish passes); at 2.0 the bottom bar clipped "Records".*
- The Shop is one page: counter and shelf, the one thing worth doing first, requests, "Who is buying", yesterday, the
  shelf, a Storage sheet. Home and Market are gone. *Device tests and the smoke script pass (final gate). Seen by a
  person only on an agent's earlier tree, in part.*
- Storage has filters (family, rarity, "Never sold"), four orders, and "Select blades" with Salvage and Arm the watch
  for many blades after one confirmation. *JVM and device tests pass (final gate). A real fault was found and fixed on
  the way: the sheet forgot its chosen order when the system restored the screen. "Select blades" mode may be lost the
  same way (untested). Bulk actions were not tried on a long save. Two lines of the plan are not met and need new core
  rules: clearing 200 blades in under ten taps (each salvage costs energy), and a "never listed" filter.*
- The Forge has the forge room header, "Forge this" from a request, Supplies and Journal buttons; Supplies is a sheet;
  Town is one fast list with shorter rows and a "Fallen and retired" header. *Device tests (Forge) and the smoke script
  (Forge, Town) pass in the final gate. Never seen by anyone: the Supplies sheet, the "Fallen and retired" header, a
  Town row with a guild or mentor line.*
- Hero and blade sheets (taste, purse, guild, mentor, history, each property's effect; pricing, listing, salvage, hone
  on the blade sheet). *Seen on the emulator at font 1.0 and 2.0 before the polish passes; device tests pass (final
  gate). Not exercised on a device: List / Set price / Salvage from the sheet, the links between sheets.*
- Haptic feedback with a switch in settings. *Switch seen on the emulator and persists; the feel needs a phone.*
- Each permanent upgrade says what its next level does in numbers. *Seen on the emulator by the run-end script.*
- The siege forecast and the shop-day screen are prepared off the main thread. *JVM tests; frame times on a long save
  were not measured.*

### Main menu
- The game opens on a menu: New game or Continue run, Abandon run (with a confirmation), and a Settings icon. Settings
  in the workshop has a "Main menu" entry. Abandoning discards the run and claims nothing (no points, legends or
  lineage); journal discoveries made in that run stay, and the dialog says so. *Seen on the emulator in the final
  gate's walk-through: New game, Settings to Main menu, Settings from the menu icon, Continue run, a cold start showing
  the menu with Abandon, the confirmation returning to New game, the abandoned run still gone after a restart.*

### Visual polish
- Pass 1: one dark forge theme in both system modes, bronze-framed panels, a gold primary button, a blade shown as an
  item card with power, quality and condition, buffs ("+") and flaws ("−"). *Its device test passes (final gate). Seen
  by a person in part on an agent's earlier tree: Settings over the menu, Shop, Forge, the forge result card, the lower
  half of a blade sheet.*
- Pass 2: the planning screens in the same look (lead, requests, shelf and storage rows with numbers, the Forge's
  plate with a full-width Forge button, Supplies, Town, Records, the top and bottom bars); the light flash at start
  should be gone. *The restyled screens pass their device tests and both scripts (final gate), which proves they work
  at one size. **Nobody has looked at pass 2**; the cold-start flash was not checked; there is no screenshot.*

### Save growth
- Bounds that keep a long save smaller: closed requests leave after 30 days, the newest 30 End Day IDs are kept,
  arrivals and world events are kept 30 days, a blade keeps its newest 24 everyday history lines. Two places where
  this pruning met newer rules are fixed (a replacement request after a blade shattered long ago; "held the wall"
  after ten later fights). *JVM tests: the same seed with the rules off reaches the same outcome.*
- A 2,000-day soak for two smiths (`./gradlew :core:soak`). End Day stays fast: 95th percentile 1.7 to 6.7 ms on the
  development machine against a budget of 200; `EndDayPerfTest` passes on the emulator in the final gate. **The save
  does not level off**: 3.7 to 4.7 MB at day 1,000, growing 3.3 to 4.2 KB a day; unsold stock is 60 to 65 % of that and
  is kept by decision. *JVM only. Not run: loading or playing such a save on a device.* A real run ends by day 65 in
  every one of 990,000 simulated runs.

### Tooling
- CI on GitHub (core tests, app unit tests, debug and minified release builds, lint) was green on the branch at the
  main-menu commit; it does not compile or run the device tests, and no CI or lint run is recorded for the tip. A
  manifest guard keeps permissions and network libraries out. More than a dozen simulator bots, customer metrics, a golden reference
  run. A release runbook and a minified trial build (4.2 MB) that played a day on the emulator.

## Checks run for this update
| Check | Result |
|---|---|
| Core JVM tests on the tip | 393 pass (reported by the controller, 2026-10-10) |
| App JVM (unit) tests on the tip | 122 pass |
| Debug APK on the tip | builds |
| First device gate, on `d946c4f` | 45 of 50. All five failures were faults in the new tests, not in screens; the tests were corrected |
| **Final device gate, on `2eca650`** (emulator Pixel_10_Pro, 1080x1920, density 420, font 1.0) | **56 of 56** device tests, including the M4 screens, the weapon card, the main menu, the saved shop-day position over a real save file, the storage and layout tests and the End Day speed test. One real screen fault was found on the way and fixed (the Storage sheet's order after restoration) |
| `smoke.sh` on `2eca650` | passed, including resume after a process kill on day 2 |
| `runend.sh` on `2eca650` | passed: a run to defeat, claim, the next era started |
| Main-menu walk-through on `2eca650` | passed, including Abandon run and a restart afterwards |
| Independent whole-branch review (by reading; nothing run) | 8 findings and a hardening note; three fixed in `677876b` plus one side effect (abandon clears the saved day position); findings 4, 5, 7, 8 and the hardening note left open; areas not reached are listed in DECISIONS |
| Simulator, balance 8, 10,000 runs at base seeds 1 / 10001 / 20001 (990,000 runs) | every run ends; plain smith (FAIR) mean 22.52 / 22.60 / 22.57 days, median 20 / 25 / 20; active smith 30.0 to 30.1; SYNERGY 35.3 to 35.4; EXPERT 45.5 to 45.7; maxed EXPERT 54.6 to 54.8, longest run 65; no tripwire crossed; maxed SYNERGY leads new by +10.15 (holds). **Ten band lines fail at all three seeds; the plan's M5 exit condition is not met.** DECISIONS, "Balance v8 at 10,000 seeds" |
| Soak, 2,000 days, JVM | see "Save growth" above; DECISIONS, "Production soak" |
| Lint and the CI command on the 0.7.0 commit (`cee83f7`) | pass locally: `:core:test :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:assembleRelease :app:lintDebug` exited 0. The GitHub workflow result for that commit is not recorded here |
| Per-upgrade gate table (T5.3) | not run |
| Screenshots of the final build | none taken; the owner chose to test by hand |
| Physical phone | nothing run |

## Not done, not verified, blocked

- Shop-day vignettes (2026-10-10): `art_day_fallen_forge`, `art_day_new_dawn`, `art_day_siege_victory` are imported from `Assets/Implemented/day_art` and drawn on the three stage banners (`StageBanner(vignette = true)`). Build and app unit tests pass; not yet looked at on a device. `art_day_till`, `art_day_sale_receipt`, `art_day_rumour` wait in `Assets/Scene/art_day_unwired` for a card slot. Rule: files left in `Assets/<folder>` are not implemented; implemented sources move to `Assets/Implemented`.
Not seen on a device by anyone (the code is merged and its tests pass)
- Font scales 1.3 and 2.0 on the M4 screens and on the screens restyled by polish pass 2.
- Small screens (360x640, 320 dp wide) beyond what `LayoutMatrixTest` covers. That test checks only that nothing
  reaches outside the screen width on the Shop, the Forge and the blade sheet; it does not check heights or clipping.
- TalkBack. Whether a light flash still shows at a cold start.
- In the real app flow: the blessing card, a siege aftermath card, the replay overlay, a "fell at the wall" card.
- A hero with guild or mentor lines in Town; the "Fallen and retired" header; the Supplies sheet.
- Bulk storage actions on a long save; "Select blades" mode after the system restores the screen.
- Installing 0.7.0 over a real 0.6.0 install; a database file cut to 0 bytes; a long save loaded on a device.
- The recovery screens for a newer or incompatible save; the "could not confirm" dialog.
- From 0.6.0 and still never seen on a device: a guild-hall day, a lesson, a merchant resale, an inherited blade.
- Lint and CI on the tip.

Known issues left open (from the final review; details in DECISIONS)
- A bulk Storage action stops silently when one save fails in the middle (finding 4).
- A save written by a later build that adds a new kind of record without raising the schema number would read as
  "damaged" rather than "newer" (finding 5). Nothing in the field is affected today.
- The blade sheet says "Worn" below condition 70; the game's own rule is 50 (finding 7).
- Three rules are restated in the app instead of read from the engine (seat count, yesterday's grouping, the elite
  sprite threshold); they agree today and can drift (finding 8).
- Hardening: building the Shop's content, the forecast or the run-end summary is unguarded; a throw there would crash
  the game at every start. No state that triggers it was found.
- Not reviewed at all: the market, battle, world-event, journal and Gazette rules in `:core`; whether every shop-day
  line matches the stored record; the hero sheet, Records, Forge, Supplies, run-end and recovery screens; the
  simulator, the tests, the device scripts and the art tooling.

Balance lines that fail at 10,000 seeds (nothing was changed; owner decisions)
- The plain smith's mean, 22.52 to 22.60 days against a band top of 22.5. This alone keeps "both bands hold" unmet.
- The bot that forges what customers ask for: +9.7 to +9.8 days over the plain smith (bound +5), answering 29.6 % of
  wants (target 60 %).
- "Not better than mine" refusals fell 5 points, not 8; three new purchase reasons stay under 1 % of visits; the plain
  smith sees 0.87 sidegrades a run (floor 1); a blade ordered for the wall is still wielded after its siege in 58 to
  60 % of cases (floor 60); the SIEGE_PREP bot's bound, as before M4; the simulator's old hard-lock counter.

Not built
- Scenario saves that would show a lesson, an inherited blade, a merchant resale or a wall death on a device, and the
  device scripts for killing the game mid-day and for layout screenshots (T2.9; `smoke.sh` and `runend.sh` exist).
- The layout matrix beyond the existing test, and fixes from it (T6.1). The TalkBack pass (T6.2).
- The device half of the soak: cold load, a save commit, frame times and memory with a 1,000-day save (T6.3b).
- The per-upgrade gate table (T5.3). The GDD section 19 walk-through (T7.3). The launcher icon.
- A rule that lets a player clear hundreds of stored blades quickly, and a "never listed" mark on a blade.
- Audio of any kind. A light theme. A tutorial beyond the two onboarding hints.

Blocked on things outside the repository
- Checks on a physical phone, including haptic feel and End Day speed on mid-range hardware (T6.6).
- Sessions with five first-time players (T7.2).
- The release application ID (still `com.example.blacksmithproject`) and release signing.
- Audio assets, the launcher icon, further portraits and art (the owner supplies them).

## Open owner decisions
Full wording, numbers and options are in `docs/DECISIONS.md`, "Major update: rulings and open decisions (2026-10-10)".
Balance numbers are never lowered automatically; the distributions are reported and the owner decides.
1. Guild Patronage ships at a stipend of 30 with its own target unmet.
2. The plain smith's first era: mean 22.52 to 22.60 days against a band top of 22.5, at every seed. Accept, restate
   the band, or raise raid growth slightly (which costs the maxed accounts 0.2 to 0.5 days).
3. The other lines that fail at 10,000 seeds, listed above: accept, restate or tune each.
4. The bot that forges what customers ask for lives +9.7 to +9.8 days longer than the plain smith (bound +5) while
   answering only about 30 % of wants (target 60 %).
5. Abandon run discards the run and claims nothing, but keeps journal discoveries made in that run. Should abandoning
   also erase those discoveries?
6. The blade card and every shelf and storage row show power, quality, condition and fame as numbers, with each buff
   and flaw by name (polish passes 1 and 2).
7. Button hierarchy from polish pass 2: gold for End Day, Forge weapon, the lead's action, Accept and Choose a
   blessing; outlines for the rest. The Shop can show three gold buttons at once.
8. Shop-day card timings, shortened to fit 25 seconds.
9. "200 blades cleared in ten taps" and a "never listed" filter both need new core rules.
10. Whether inheritance records are kept for the whole run (they are half of the kept records in a long save).
11. The save envelope format (13 % of the file is escaping), and whether fields added since schema 4 should have made
    a schema 5 before testers get a build.
12. End Day remembers only the newest 30 command IDs.
13. The save does not level off; unsold stock is 60 to 65 % of its growth.
14. The wording of the onboarding hint.
15. Outside the code: the application ID, the icon, audio, art, how AI-generated art is described on the store, name
    taste, and whether to commit the external review and the evidence folder (both still untracked).
Settled since the morning: a maxed account's lead over a new one for the SYNERGY bot holds at 10,000 seeds (+10.15).

## UI/UX review (2026-10-10, afternoon)
A design review of the 0.7.0 build with a phased redesign plan is in `docs/UI_UX_REVIEW_2026-10-10.md`
(evidence, the two independent reviews and the reliable emulator captures in `docs/ui_review_2026-10-10/`). Nothing
was implemented. It recommends an answer for open owner decisions 6, 7, 8 and 14 and raises six new ones (section 7
there). The capture found the emulator being driven by another process (the app was uninstalled at 12:17 and a
different build installed), so the shop-day cards, the sheets and the large-text and small-screen views are judged
from source; the planning screens were seen.

## UI/UX improvement 1: forge, choose a price, list (2026-10-10, afternoon; committed on `main`)
The first item of the UI/UX review was built at the owner's request: the journey from the forge result to a listed blade.
Design notes and the reasons are in `docs/DECISIONS.md` ("Forge, price, list: the first UI/UX improvement"); the player-facing
lines are in `CHANGELOG.md` under `[Unreleased]`. No balance, rules, save or content version changed.

**Which build.** The work is one commit on `main`, on top of `ea01669`. The branch `post-0.7.0` (the worktree
`.claude/worktrees/shop-day`) is four commits ahead of `main` and touches `ItemDetailSheet.kt`, `WorkshopScreen.kt` and
`Labels.kt` too; nothing here was merged with it. The reviews were written against that branch's build; every pricing problem
they describe was confirmed in `main`'s source before editing.

**What changed.**
- The forge result has the price on it: −10, a number that can be typed, +10, "List at <price>" and Store. The suggested price
  is named apart from the player's ("Suggested price 124 gold. Your price is 30 below it.").
- Under the price, on the forge result and on the blade sheet: "N of M heroes in town can afford this price", counted again on
  every change from the saved purses by the counter's own rule (`Market.funds`: purse, trade-in credit, an unspent guild
  stipend), red when nobody can, with "Able to pay is not a sale: the blade must also suit them."
- A full shelf greys "List at" with the reason beside it, on both surfaces; Store stays.
- After listing or storing, a notice over the workshop says where the blade went. Closing the card with Back or a tap outside
  stores the blade and says so. "List at" closes the card only once the listing is saved.
- A blade forged from "Forge this" keeps its request on the card: who, what, terms, whether this blade fits, and the engine's
  own readiness line for End Day, with the rule that no blade is set aside.
- The price and both buttons are pinned under the blade's scrolling card and stay above the keyboard; the blade sheet's price
  block does too.

**Checks.** The emulator used was a second instance started for this work (AVD `carbscan`, API 36, read-only, port 5556),
because another session was driving `emulator-5554` at the time. It is not the Pixel_10_Pro of the earlier gates.
| Check | Result |
|---|---|
| Core JVM tests (`./gradlew :core:test`) | 394 pass (393 + `DemandTest.fundsAreTheCountersOwnCanPay`) |
| App JVM tests (`./gradlew :app:testDebugUnitTest`) | 128 pass (122 + six in `ForgeResultTest`) |
| `:app:compileDebugAndroidTestKotlin :app:assembleRelease :app:lintDebug` | pass |
| Device tests (`:app:connectedDebugAndroidTest`, 1080x1920, density 420, font 1.0) | 60 of 61. New and passing: four in `ForgeResultCardTest` (price and count, full shelf, request context, and the card at 411x731, 360x640 and 320x569 dp with text at 1.0, 1.3 and 2.0) and one in `DetailSheetTest`. **One fails: `StorageSheetTest.bulkSalvageAsksOnceAndIssuesOneCommandPerBlade`. It fails the same way on untouched `main` on this emulator**, so it is not from this work; it passed in the earlier gate on Pixel_10_Pro and was not looked into |
| `tools/emulator/smoke.sh` | passed, all ten checks, SMOKE_DONE |
| The journey, seen on the emulator, at 1080x1920 density 420 (411x731 dp), font 1.0 | 23 of 23 scripted checks, and the screenshots were looked at: a new game, forge, the result with both buttons on screen without a scroll, −10 twice, a typed 9,999 ("0 of 12 heroes in town can afford this price", in red) with the keyboard up and the price, the count and both buttons above it, a typed price listed, the notice, the Shop showing the shelf, the repeat recipe in three taps (Forge tab, Forge weapon, List at), Store and Back with their notices, then the blade sheet from Storage with the same count, its List button above the keyboard, and "On the shelf, asking 33 gold." after listing |
| The same journey at 720x1280 density 320 (360x640 dp), font 1.0 | 23 of 23, screenshots looked at |
| The same journey at 360x640 dp, font 1.3 | 23 of 23, screenshots looked at |
| The same journey at 411x731 dp, font 1.3 | 23 of 23; the result and the sheet's keyboard screenshots were looked at |
| A full shelf in a live game (411x731 dp, font 1.0) | seen: seven blades listed on day 1 and one on day 2, all at 9,999; the ninth blade's card says "The shelf is full (8 of 8). ...", "List at" is greyed, a tap on it does nothing and raises no error, Store works, and the blade's sheet says the same |

Found and fixed on the way, each by a device run: the first layout put the buttons below the fold at normal size (pinned
footer); the pinned footer left the blade no room at font 2.0 (one scrolling column above 1.3); on the sheet at 360x640 the
List button was part-covered by the keyboard when the lines wrapped (the block is brought into view again after layout); at
360x640 with font 1.3 the result's buttons were cut off under the keyboard (the card shows only its price while typing).

Screenshots: `docs/ui_review_2026-10-10/improvement_1/` (a selection; the file names say the size and the step).

**Not verified, and limits.**
- Nothing ran on a physical phone or on Pixel_10_Pro. TalkBack was not run; the count is a polite live region and the greyed
  buttons carry their reason, but nobody listened to them.
- A blade forged for a request was **not seen in a live game** (no request was offered in the days played). Its card is
  covered by JVM tests and one device test only.
- Font 2.0 was run only inside the device test (the controls are reachable by scrolling); nobody looked at it.
- At 360 dp wide the blade card's "Renown" label wraps to "Renow / n" beside "Unsung (0)". That is the existing item card,
  seen here for the first time, and was left alone.
- At 360x640 with font 1.3 the blade above the pinned price has about 170 dp and shows its name and two numbers before it
  scrolls.
- The notice is a snackbar of about ten seconds; it lies over the bottom of the destination while it shows.
- The card still says "Value" for the suggested price in its number grid, above the line that calls it "Suggested price".
- The count says who can pay, not who will come: six of the twelve are seated a day. The line says it is not a sale; it does
  not say how many visit.
- Process hygiene, for the record: a build from `main` was installed on the shared `emulator-5554` and its app data cleared
  before the other session's script was noticed; that session's `post-0.7.0` APK was installed back within minutes, but its
  running scenario script will have failed in between. Later `./gradlew --stop` was run once, which also stops idle Gradle
  daemons of other sessions.

## UI/UX improvement 2: navigation and action hierarchy (2026-10-10, evening; committed on `main`)
Built in the worktree `.claude/worktrees/ui-nav` (branch `ui-navigation`) while `gameplay-depth` was being worked on
elsewhere. What changed is in `CHANGELOG.md` under `[Unreleased]`; the reasons are in `docs/DECISIONS.md` ("Navigation and
action hierarchy"). No balance, rules, save or content version changed.

**Checks.** Emulator: a separate read-only instance (AVD `carbscan`, API 36, port 5556, 1080x1920, density 420, font 1.0).
| Check | Result |
|---|---|
| App JVM tests (`:app:testDebugUnitTest`) | 133 pass (128 + five in `NavigationTest`: the draft across destinations and "Forge this", Back's order, a blessing put off, Back on the first card and the Resume prompt, the six stock actions) |
| Device tests, five classes only (`NavigationFlowTest`, `ShopPanelTest`, `DetailSheetTest`, `ShopDayPersistenceTest`, `LayoutMatrixTest`) | 19 of 19. New, `NavigationFlowTest`: Shop scroll kept across Forge, Back and the menu; the Forge's draft and open step kept and End Day absent on Town and Records; a blade inside the Storage sheet, Back to the same list, List and Salvage returning to it with the line; Back on a day's first card opening the menu with the day unacknowledged |

**Not verified, and limits.**
- The full device suite, `:core:test`, lint, the release build and `smoke.sh` were not run for this batch (core is untouched).
- Nobody looked at the screens: no screenshots at 411x731, 360x640 or with enlarged text. End Day's spacing, the outline End
  Day on the Forge, the lead button and the "‹ Storage" row are untested by eye; the owner's hand test should cover them.
- "Open Supplies, buy, return" was not run as a journey; Supplies is a sheet over the Forge, which stays composed under it.
- Added afterwards (a second commit, JVM tests only, 134 pass): a bulk salvage or "Arm the watch" says how many blades it took, and
  "Open Supplies" stands under the anvil plate while a chosen material is out. Neither was seen on a device.
- Looked at afterwards on the emulator (`carbscan`, port 5556): Shop, Forge and Town at 360x640 dp with text 1.3, Shop and Forge
  at 411x731 dp, and a blade opened, honed and left with Back inside the Storage sheet. Changed from what was seen: the
  under-plate "Open Supplies" is left out on a low screen or with large text (the steps have too little height there), and
  the hone line names only what changed. Still unseen: Records, the Forge with a material out, the day cards, text at 2.0.
- Merging `gameplay-depth` will conflict in `WorkshopScreen.kt`, `GameViewModel.kt`, `StorageSheet.kt`, `ItemDetailSheet.kt`
  and `CHANGELOG.md`; its `Sheet.Visitor` needs a branch in `DetailSheet`, and its relic dialog a place in Back's order.
## UI/UX improvement 3: rewards and consequences (2026-10-10, evening; merged into `main` at `ce4f0a6`)
The third batch from the UI/UX reviews: the forge result as a short reveal, a sale that looks unlike a refusal, a refusal
that leads with its numbers, one card for the day's fights when nothing else tells them, and the repeated wording of the
day sequence. Design notes and the reasons are in `docs/DECISIONS.md` ("Rewards and consequences: the third UI/UX
improvement"); the player-facing lines are in `CHANGELOG.md` under `[Unreleased]`. No balance, rules, save, schema or
content version changed. The day controls (Back, Next, Skip day, the speed chip) are as they were.

**Which build.** The work was done in its own worktree (`.claude/worktrees/ui-batch2`, branch `ui-batch-2`, cut from `main`
at `79010eb` and rebased onto `796ce32`) because other sessions were working in the repository at the same time. It is committed there and **not
merged into `main`**; that is the owner's call. The reviews' findings for this batch were confirmed in `main`'s source first
(all held; the list is in DECISIONS).

**What changed.**
- Forge result: the blade's sprite, name and rarity stand alone for about 0.7 s, then its numbers, buffs and recipe fade in
  under them (0.25 s). Nothing moves; the price and both buttons are on screen and live from the first frame; a tap on the
  card ends the fade; with reduced motion the card is whole at once. A new blade's card has no "Renown Unsung (0)" row (the
  blade's sheet keeps it; earned renown shows on the card).
- A sale: a gold band "SOLD +96 gold" (a request: "REQUEST PAID", a collector: "SOLD TO A COLLECTOR"), then "Earned today
  96 → 228 gold" straight under it, the blade, why, and the receipt. The tally shows the same line when someone in it bought.
  The till's total is "Earned today"; its rows are "Shelf sales", "Requests", "Guild stipends", "Town's blessing",
  "Collector", "Tribute from the town". A request's receipt has a "Request payment" row. All of it is sums of the saved
  `Sale` records; nothing is added to the purse by a card.
- A refusal: a plain card with a small "NO SALE" mark that leads with the recorded reason and number ("38 gold short of the
  cheapest blade", "Could pay up to 90 gold; the cheapest blade is 128 gold."). Each blade looked at says "For it: ...
  Against it: ..." in sentences.
- Beyond the shop: a day with fights and no other card gets one card, "Out in the field" ("5 heroes went out unarmed and all
  were driven back."), with the Gazette link. It is an ordinary card of the sequence for Back, Skip and a restart.
- Repetition: the opening card says "N visitors today" and how many are shown at the counter; the strip counts the shown
  ("Counter · 1 of 3"); the plate under the scene says what is left on the shelf; a bare-shelf day gives the count and the
  faces once; the last card reads "Day 1 / Evening" over "Tomorrow: day 2" and "Begin day 2"; Skip is "to the evening"
  everywhere (the restart prompt's button was "Skip to tomorrow").

**Checks.** The emulator was a second instance started for this work (AVD `carbscan`, API 36, read-only, port 5556); another
session's emulator (`emulator-5560`) was running and was not touched. It is not the Pixel_10_Pro of the earlier gates.
| Check | Result |
|---|---|
| Core JVM tests (`./gradlew :core:test`) | 395 pass (394 + `fightsWithNoCardOfTheirOwnAreSummedUpOnceFromTheFieldResults`; two existing tests were changed to expect the summary card, and exact-string checks were added for the new lines) |
| App JVM tests (`./gradlew :app:testDebugUnitTest`) | 135 pass (128 + seven: earned today against the ledger, receipts add up, refusal numbers, visitors against shown, the field summary's place in the sequence, a kill on the summary card, zero renown) |
| The 1x day length test | unchanged: median 24.6 s, p90 24.6 s (budget 25 and 30) |
| Device tests (`:app:connectedDebugAndroidTest`, 1080x1920, density 420, font 1.0) | 63 of 64. New and passing: the reveal leaves the price and both buttons live on its first frame and moves nothing; the summary card and the evening card; the opening card. **One fails: `StorageSheetTest.bulkSalvageAsksOnceAndIssuesOneCommandPerBlade`, the same failure as on untouched `main` on this emulator** (see improvement 1) |
| `tools/emulator/smoke.sh` | passed, all ten checks, SMOKE_DONE |
| A live game, 411x731 dp, font 1.0 (scripted taps, every card's text logged, screenshots looked at) | Day 1 with nothing forged: quiet card ("6 visitors looked in..."), then "Out in the field: 5 heroes went out unarmed and all were driven back.", then the evening card. Day 2: three blades (two at the suggested price, one at 9,999): a refusal ("5 gold short of the cheapest blade"), two sales ("+88", "Earned today 0 → 88"; "+108", "88 → 196"), tally, till "Shelf sales 196 / Earned today 196", purse 250 before and 446 after (250 + 196). Day 3: killed on the second card and reopened: the prompt ("Day 3 is done and saved. The shop earned 0 gold."), Resume lands on the same card with the same words; killed again, "Skip to the evening" lands on the evening card; purse still 446 |
| The repeat recipe | Forge tab, "Forge weapon", then "List at" tapped 0.4 s later at its remembered place: the blade was listed ("Iron Sword is on the shelf at 88 gold. Shelf 2 of 8."). Three taps, no wait |
| The reveal, filmed at ten frames a second | about 0.7 s with the title, sprite, name, rarity, price and both buttons and nothing where the numbers go; then the numbers fade in. With "Reduced motion" on, the numbers are in the first frame the card appears in |
| A request payment | **Not in a live game** (none was offered in the days played). Seen in the debug preview over a day the engine resolved (seed search, day 9): "REQUEST PAID +110 gold", "Request payment 110 gold", "Earned today 0 → 110"; the JVM test holds that day's cards against its ledger |
| 360x640 dp, font 1.3 (screenshots looked at) | Sale, refusal, request, field summary, opening and evening cards in the preview; the forge result in a live game (price, count and both buttons on screen, "List at" worked). The sale's band and "Earned today" and the refusal's heading and numbers are on screen without a scroll |
| 411x731 dp, font 1.3 | sale and refusal cards looked at in the preview |
| `:app:assembleRelease :app:lintDebug` | pass |

Found and fixed on the way, each by a device look: "Earned today" was under the receipt and off screen at normal size (moved
straight under the band); the first strip label ("At the counter: 1 of 3") lost its count at 360 dp with text 1.3 (shortened);
the plate and the shelf band both said "The shelf is bare" on a quiet day (the plate says "Nobody at the counter"); the strip
and the banner both said "Beyond the door" (the strip says "After closing").

**After the rebase onto `main` at `796ce32`** (the navigation batch and the new art; only CHANGELOG, DECISIONS and PROGRESS
conflicted, both sides kept): app JVM tests 141 pass (main's 134 + the seven), device tests 67 of 68 (the same one failure),
`smoke.sh` all ten checks, and the sale, refusal, tally, till and field-summary cards were looked at once more in the preview
(the till and tally now carry main's vignettes above the rows). Core is untouched by the rebase (395, not run again). The live
game, the film, the small-screen and large-text looks and the JVM counts in the table are from before the rebase.

Screenshots: `docs/ui_review_2026-10-10/improvement_3/` (the file names say the size and the card). They were taken before
the rebase, so they show the older backdrop and rarity pips.

**Not verified, and limits.**
- Nothing ran on a physical phone or on Pixel_10_Pro. TalkBack was not run. Font 2.0 was not looked at for these cards (the
  forge result's layout test still runs at 2.0).
- A request payment, a collector's sale, a guild stipend and a tribute were not seen in a live game; the first is seen in the
  preview and all four are covered by the ledger test only where the fixture days produce them (the test requires a request
  and a held siege among them; a stipend row, a collector's receipt and a tribute row are not asserted to occur).
- The reveal's fade cannot be told from a device test (a faded node is still "displayed"); the film above is the evidence.
- The summary card counts fights only. Rests, patrols and guild days are not mentioned, and on a day with one card about one
  hero the others' fights are still only in the Gazette.
- Fixed afterwards, the same evening (JVM tests only, not looked at on a device; 141 app tests and the full core suite pass):
  the evening card's lead says "left over the price today" (the next morning's Shop still says "yesterday"); a request's card
  and the tally say "Collected a requested blade" and "Collected the requested Iron Spear and paid 110 gold." (they said
  "commission"); the unused `Lines.considered` is removed and its tests read `Lines.weighed`. The Gazette and the event
  records still say "commission": those are saved sentences, and a vocabulary pass over them is a later batch.
- On a sale card at 360x640 with text 1.3 the reason and the receipt are below the fold (the band, the coin and "Earned today"
  are above it).
- The emulator hung once in "not responding" dialogs after a cold boot while the machine was busy; it was restarted (this
  session's instance only) and the runs above are from the second boot.

## Next actions
0. UI/UX improvement 3 (above) is on `main` (fast-forward from `ui-batch-2`, no checks re-run for the merge).
   UI/UX improvement 2 (navigation) is on `main`.
   UI/UX improvement 1 is on `main` and still to be reconciled with `post-0.7.0`; a request blade's result card still
   needs a look in a live game.
1. Owner hand test of the 0.7.0 debug build (list below), then the owner's decisions above, and the UI/UX review's
   decisions (`docs/UI_UX_REVIEW_2026-10-10.md`, section 7) before its phase 0 starts.
2. From the hand test: fix what the large-text and small-screen items show; take the screenshots nobody has.
3. Decide the balance lines; if a number changes, re-run the simulator and record it in DECISIONS.md.
4. The open review findings: say what a cut-short bulk action left undone (finding 4); use the engine's worn rule on
   the blade sheet (finding 7); guard the screen-model building (hardening note); test "Select blades" after a restore.
5. T2.9 scenario saves and scripts, T6.1 / T6.2 layout and TalkBack, the device half of the soak, the T5.3 table, T7.3
   walk-through, lint and CI on the tip.
6. Merging to `main` and a release wait for the owner; phone checks and playtests wait for a device and players.

## What to try when testing by hand
The 0.7.0 debug build: `app/build/outputs/apk/debug/app-debug.apk` in the worktree `.claude/worktrees/shop-day`
(`./gradlew :app:assembleDebug` builds it; `./gradlew :app:installDebug` installs it on a connected device).
Items marked **layout risk** are the ones most likely to show clipping, overlap or a button pushed off screen: each is
a concern written in a report, and none has been looked at with large text or on a small screen. To test them, set the
phone's font size to its largest and, if possible, use a small or short screen.
1. First launch: the main menu shows New game and the Settings icon. Start a game; the Shop opens on the lead "Forge
   your first blade" with a button that goes to the Forge. Watch for a light flash at start (there should be none;
   this was never checked).
2. Forge a blade. The result appears as an item card with power, quality and condition, buffs and flaws. **Layout
   risk** with large text: the card's two columns should fall into one, and "Renown" should not wrap badly.
3. List the blade from its card or from Storage, set a price on the blade sheet, then End Day and watch the day card
   by card. On the first customer card a one-line hint should appear once ("Tap anywhere to continue · Skip day jumps
   to the evening") and not come back on later days. **Layout risk**: with large text the hint wraps to two lines
   above the buttons. Try Next, Back, Skip day, 1x and 2x, and tap anywhere. Back must never begin the next day. Is
   the pace at 1x comfortable?
4. During a shop day, close the game from the recent-apps list and reopen it: it should offer "Resume the day" or
   "Skip to tomorrow", and the day must be the same either way.
5. Open the main menu from Settings, choose Abandon run and read the confirmation (it should say journal discoveries
   stay). Confirm: the menu offers a new game; close and reopen the game; the run is still gone, legacy points are
   unchanged, and a pairing discovered in the abandoned run is still in the Journal.
6. The Shop page, top to bottom: counter plate with "Seats 6" and the siege line, the lead, requests, "Who is buying",
   yesterday, the shelf, Storage, Supplies. **Layout risk**: with large text the plate has one or two extra lines
   about the besieger, and the lead's reason may be hidden behind End Day on a short screen. Are three gold buttons
   on one page (lead, Accept, End Day) too many?
7. A request card (accept one when it appears): Accept, Decline and "Forge this". **Layout risk**: three buttons in a
   row that should wrap at a narrow width with the largest font. The card should say why the request was made.
8. Tap "Forge this" on a request: the Forge opens with the family chosen and a line "For <name>: ..." pinned above
   the steps. **Layout risk**: the pinned plate is taller than before (the Forge button runs its full width), so on a
   short screen with large text little room is left for the steps.
9. On the Forge, look at the augment chips a day or two before a siege: an element the besieger fears should carry
   a "+", one it resists a "−", with a line under the chips. Open Supplies from the Forge and from the Shop and buy
   something (nobody has ever looked at this sheet).
10. After a customer leaves without buying, look at "Who is buying" the next morning for a line such as "wants a bow;
    can spend about 90 gold" with "Forge this"; forge and shelve that kind and check the line gets a tick.
11. Town: scroll the list of twelve heroes, tap one for the hero sheet, tap their blade for the blade sheet and its
    Story and History. After some deaths, open "Fallen and retired". Look for a hero with a guild or mentor line. The
    Town should name the same besieger as the Shop and the Forge.
12. Storage with many blades: open it, try the family and rarity chips, "Never sold" and the four orders. Tap "Select
    blades", choose several, Salvage them and read the confirmation (it should say the energy it costs and where it
    stops); do the same with Arm the watch. Send the game to the background and return: is the order kept, and is
    "Select blades" mode kept (**untested**)? **Layout risk** with large text: the chip rows scroll sideways and
    "List at" moves under the blade.
13. Records: News (yesterday's Gazette and older days), Journal (after a failed attempt at a hidden recipe, a ladder
    of four clues; a found signature has "Use this recipe"), Legacy (upgrades saying what the next level does; the
    Legend Board after a first era).
14. Play to a lost siege: the fall of the forge, the run-end screen, Claim, buy an upgrade, Begin era. Tap two
    upgrades quickly; nothing should be lost. Close the game on the claimed screen and reopen it. After surviving a
    siege, look at the blessing card inside the day (never seen in the real app).
15. Settings: reduced motion, the Haptics switch (feel a forge, a sale, End Day and a run end with it on, nothing with
    it off), and the bottom bar at the largest font (**known** from before the polish: "Records" clipped at font
    scale 2.0).
16. If an old 0.6.0 install with a saved run is at hand: install this build over it and check the run opens and
    accepts End Day (never tried on a device). Android only installs over an app signed with the same key, so this
    works only if both builds were signed alike (for example two debug builds from the same machine).

## Session 6 focus: gameplay depth (balance v3, app 0.4.0)
The owner asked for function and features over UI ("not fun yet, not much in it"; the UI will be redesigned later).
Added in `:core`, each with one plain UI surface: affix effects, elite foes and warlords, hero ambitions, element
commissions, the Salvage / Hone / Arm the watch shop actions, workshop tools, a siege forecast. Design, numbers and
simulator evidence are in DECISIONS.md ("Balance v3: gameplay depth").

## Session 7 focus: readable Gazette, Home dashboard, demand (balance v4, app 0.5.0)
The owner found the between-days journal "a text block" and asked for a home screen "like a dashboard", then for all
agents on gameplay. `Gazette.edition` (core) lays a day out as a paper: lede (priority >= 6, at most two lines, never
the standing siege warning), a tally line (gold taken net of trade-ins, visitors who bought, expeditions won/lost,
heroes fallen), then Shop / Heroes / Town / Forge; a hero's several records fold into one sentence, quiet heroes share
a line, visitors who left are named with the reason, milestones already told by the news fold away, the smith's own
work is one line. The report dialog and the archive render it (`EditionBody`); siege rounds fold behind the outcome.
Four agents then worked in worktrees and were merged: the Home panel (first tab, landing panel, one block per
concern), weapon wear (`Weapon.condition`, worn-power demand, Hone restores), weapon fame as a bounded effect, and
the v3 review at 10,000 seeds with per-tool / per-affix sweeps (two of its recommendations applied: whetstone
120/300, warlord pressure 50 without a raid bonus). Evidence in DECISIONS.md ("Balance v4 ...").

## Session 8 focus: major features from the checklist (app 0.6.0, balance v5)
`docs/GDD_CHECKLIST.md` now lists every GDD feature as built or open; the owner asked for the major open features,
kept in `:core` with plain text surfaces because the UI will be redesigned. On `main` and pushed: weapons-map pruning
(`WeaponPruning`, End Day tail), the day report reopening after process death (`SettingsStore.dismissedReport`), and
an instrumented End Day budget test (`EndDayPerfTest`). Merged from an agent worktree: hero daily life (the guild
hall and the ambition as scored daily activities, money and yesterday as inputs; `Heroes.activityWeights`,
`HeroLifeConfig`), then fight replays for elite fights and deaths plus the fates of a fallen hero's blade (guild
inheritance, merchant resale, odds by where the hero fell; `Battle.fightReplay`, `Market.resolveMerchant`,
`WeaponFatesConfig`), then three legacy tracks (Caravan Ties, Anvil Lore, Homing Steel), Known Name regulars and a
second yardstick table for upgrades (`LegacyTracksConfig`, `Simulator` `Yardsticks`). All three branches are merged;
nothing is in flight.
Also on `main`: the signboard now adds a customer a day per level and six affix magnitudes are stronger; that is
balance config v5 (the branches join v5 when they merge). Evidence in DECISIONS.md ("Weapon pruning and day-report
recovery", "Balance v5, part 1").
Two documents arrived in `docs/` during the session and were not part of this work:
`Tiny_Blacksmith_Thorough_Review_2026-10-09.md` (an external review pinned to b39ad76, findings F01-F11; still untracked) and
`MAJOR_UPDATE_PLAN.md` (the plan for the shop-day update, written by another session: complete, reconciled with 0.6.0
and independently reviewed; its task and evidence ledger is `MAJOR_UPDATE_LEDGER.md`, its evidence is in
`major_update_evidence/`, still untracked). The plan and the ledger are committed on `shop-day/m0`. None of the review findings
is fixed by session 8 except the signboard (its section 6.2); the review's checklist corrections (its section 9) were applied to
`docs/GDD_CHECKLIST.md` by task T0.3, as wording only.

## What existed at 0.6.0 (history)
- `core/` pure Kotlin engine: RNG, slice + launch content catalogs (launch is the default), balance config (v5 now; v2 made launch content the default), model,
  commands, End Day resolver, crafting with techniques and all 24 signature recipes, market, hero AI with retirement/
  guilds/mentoring, battles/sieges with weapon seizure, world-event pool (23 events + 2 deterministic rules = all 25 GDD
  events), legacy with famous-blade returns, Gazette, JSON save codec with a migration scaffold, End Day event-log
  compaction (30-day window, history-grade types kept), headless simulator (GDD policy set, `--content`,
  `--rarityTable`, `--impactPolicy`, `BALANCED_INVEST` purchasing rule with `--reserve`, `BALANCED_REPUTED`
  pricing, forge-damage overrides, JSON report, perf probe), reputation/loyalty depth (bounded price ceiling,
  loyalty-weighted commission patrons, premium/regular Gazette records), weapon-history cap, a checked-in v1 save
  fixture. Balance v3 (session 6): affix effects (bane, elite, heal, loot, wound, shatter, self-harm), elite
  encounters, warlord sieges, hero ambitions, element commissions, `Salvage` / `Hone` / `DonateWeapon` / `BuyTool`
  commands, workshop tools, `siegeForecast`, `BALANCED_ACTIVE` simulator policy, trade-ins and patrol pay. Balance v4 (session 7): weapon wear and condition, weapon fame effects (capped), whetstone 120/300, warlords from pressure 50; `Gazette.edition`; simulator catalog sweeps. Session 8: `WeaponPruning` (blades gone for good leave the save after 30 days); balance v5 part 1 (signboard +1 customer a day per level, stronger affix magnitudes) and part 2 (`HeroActivity.GUILD` / `AMBITION`, mentoring at the hall, guilds founded in life, simulator activity shares) and part 3 (`CombatReplay.kind`, expedition replays, `WeaponFate`, merchant resale, simulator fate counters and artifact recovery, `--noFates`) part 4 (Lucky brings back scarce materials) and part 5 (11 upgrade tracks, Known Name regulars, upgrade yardsticks, `--upgrades` / `--yardsticks` / `--legends`). 186 JVM tests.
- `app/` Compose portrait workshop (session 4 layout: three-stat top bar, pinned forge summary over collapsible
  auto-advancing steps, row-based market, per-panel tip banners, single End Day action, full-width paper day report;
  principles in DECISIONS.md; session 5: title/run-end on the spacing tokens with one primary action, Town lists
  every faction with pressure and weakness, screen-reader descriptions on disabled actions), seven panels (Home dashboard first, session 7), technique chips,
  newspaper day report with stepped replay, blessing choice (dismissable for the day), run-end/legacy screen; Room
  atomic save store; DataStore settings (reduced motion, seen tips, the last dismissed day report). Imported pixel art on every screen
  (AI-generated concept sheets, an AI-generated weapon master sheet and script-drawn pack sprites; see `docs/ART_BRIEF.md`, "Art sources and provenance").
- `tools/pixelart/import_assets.py` (slices concept sheets, copies selected pack sprites, prunes stale imports),
  `generate_assets.py` (placeholders), `tools/emulator/smoke.sh` (device loop). `docs/ART_BRIEF.md` is the brief.
- Animated siege diorama in the day report (artist pack frames), dead/retired markers, milestone burst on the result
  card for signature or epic+ weapons.
- Weapon master sheet sliced into 336 `weapon_<family>_<row>_<level>` sprites with a generated `ui/WeaponArt.kt`
  lookup; 488 imported sprites in total (94 slices of the five AI-generated concept sheets + 336 weapons from the AI-generated master sheet +
  58 script-drawn pack sprites). The tooling still prints and records the label "hand-made" for these (`generate_assets.py`, manifest
  `"source": "handmade"`) until task T2.4 renames it; read it as "imported".

## Checks run in sessions 4 to 8 (history, up to release 0.6.0)
| Check | Command | Result |
|---|---|---|
| Core tests | `./gradlew :core:test` | 85/85 pass (14 classes incl. RarityShape, EventCompaction, SignatureAndTechnique, SimulatorPolicy on the launch catalog); the soak runs on launch content |
| App compiles | `./gradlew :app:compileDebugKotlin -q` | BUILD SUCCESSFUL |
| Rarity tables | `./gradlew :core:simulate --args="--rarityTable 1000 --seed 1 --content launch"` | before/after tables in DECISIONS.md |
| Policy sweep | `./gradlew :core:simulate --args="--runs 1000 --seed 1 --content launch"` | BALANCED_FAIR 25 (15/35), mean 23.8, 1.6 sieges survived/run, 0 hard-locks; full table in DECISIONS.md |
| Confirmation | `--runs 10000 --policy BALANCED_FAIR` / `SAFE_FAIR` | 25 (15/35) mean 23.7 survived 1.5 / 25 (15/30) mean 23.2 survived 1.5 |
| 10,000-seed review | `./gradlew :core:simulate --args="--runs 10000 --seed 1 --policy all --impactPolicy BALANCED_INVEST --reserve 0 --perf --json <out>"` | every policy at 10,000 seeds, maxed legacy for BALANCED_FAIR and BALANCED_INVEST, upgrade impact under BALANCED_INVEST; full table in DECISIONS.md ("Balance review at 10,000 seeds") |
| Slice regression | `--content slice --policy SAFE_FAIR` | 30 (20/35), rarity 18/51/28/2/0 (was 1/43/52/4/0; accepted, see DECISIONS) |
| Event compaction | `EventCompactionTest` + `:core:simulate --runs 200 --seed 1 --perf` | 400-day forced-survival pair: gameplay identical with/without compaction; perf run ends at events=1,834 (was 13,275) |
| Weapon-history cap + v1 fixture | `./gradlew :core:test` (`WeaponHistoryCompactionTest`, `SaveFixtureTest`) + `:core:simulate --runs 200 --seed 1 --perf` | 92/92 pass; 400-day pair: gameplay identical with/without the cap, entries 1,812 -> 1,721, max combat entries 21 -> 10, save 973,919 -> 961,841 bytes; `saves/v1_forced_seed4242_day61.json` decodes, accepts an End Day and compacts; perf p50 0.34-0.40 ms vs 0.26 before, within machine noise (cap-disabled control 0.39-0.71) |
| Debug APK + install | `./gradlew :app:installDebug` | BUILD SUCCESSFUL |
| Instrumented | `./gradlew :app:connectedDebugAndroidTest` | 5/5 pass (Room atomic save/restore x2, title screen, forge hint x2) |
| Device loop | `tools/emulator/smoke.sh` | Re-run on the merged 0.2.0 build (launch content, new layout): shelf/town/resume checks ok, SMOKE_DONE; screenshots sent to the user |
| Instrumented on merged main | `./gradlew :app:connectedDebugAndroidTest` | 5/5 pass on the 0.2.0 build |
| Art import | `python tools/pixelart/import_assets.py` | 5 sheets + weapon master + 1 pack -> 488 sprites; contact sheet reviewed; weapon shelf verified on device |
| Reputation/loyalty depth (session 4) | `./gradlew :core:test`, `./gradlew :core:simulate --args="--runs 1000 --seed 1"` before/after | 89/89 pass (82 + 7 in `ReputationAndLoyaltyTest`: caps, evaluate() stranger vs regular, reputation ceiling, premium sales 40/40 vs 29/40 at 190 %, commission bias 22/58 offers to the regular, event text, determinism); BALANCED_FAIR 25 (15/30) mean 23.7 (was 25 (15/35) mean 24.0), sell rate 14 % unchanged; tables in DECISIONS.md |
| Simulator wave 2 + UI wave 2 (session 5) | `./gradlew :core:test`; `:core:simulate --runs 10000 --seed 1 --policy BALANCED_REPUTED`; `:app:assembleDebug`, `:app:installDebug`, `tools/emulator/smoke.sh scratchpad/ui_v4`, `:app:connectedDebugAndroidTest` | 103/103 pass (SimulatorPolicyTest added); BALANCED_REPUTED 25 (15/30) mean 23.6, within 0.3 days of FAIR (DECISIONS); build ok, SMOKE_DONE with shelf/town/resume checks ok, instrumented 5/5; title/forge/town screenshots sent |
| Run-end route + large fonts (session 5) | `tools/emulator/runend.sh scratchpad/runend`; Town at font scale 1.3 and 1.5 | passive run falls on day 10; final Gazette -> claim -> pinned Begin era -> era 2 day 1, all checks ok, RUNEND_DONE; Town wraps without clipping at both scales |
| Gameplay depth (session 6) | `./gradlew :core:test`; `:core:simulate --runs 1000 --seed 1 --policy all`; `--policy BALANCED_ACTIVE --impactPolicy BALANCED_ACTIVE` | 122/122 pass (18 new in `GameplayDepthTest`, 1 in `SimulatorPolicyTest`); BALANCED_FAIR 20 (15/30) mean 21.7 (was 25 / 23.7), BALANCED_ACTIVE 30 (20/35) mean 27.5, sales 17.7 / 23.4 a run (was 16.2), 0 hard-locks; armory and raid sweeps and the full table in DECISIONS.md |
| Weapon wear (session 7, merged into v4) | `./gradlew :core:test`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE` before/after plus a 4-variant wear sweep; `:app:compileDebugKotlin -q` | 130/130 pass (8 new in `WeaponWearTest`: bounds, worn power, expedition and siege wear, worn replacement, re-hone, save defaults, determinism); BALANCED_FAIR 20 (15/25) mean 20.0, sold 19.3, NOT_BETTER 36.3 (was 20 (15/30) 21.7 / 17.7 / 45.8); BALANCED_ACTIVE 25 (20/35) mean 25.4, sold 25.9, NOT_BETTER 43.5 (was 30 (20/35) 27.5 / 23.4 / 58.3); deaths 0.9, 0 hard-locks; app compiles; sweep table in DECISIONS.md ("Balance v4 (pending merge)") |
| 0.4.0 on device (session 6) | `:app:installDebug`, `tools/emulator/smoke.sh`, `:app:connectedDebugAndroidTest`, manual adb pass | SMOKE_DONE (shelf/town/resume ok), instrumented 5/5; Hone, Arm the watch, Salvage and a tool purchase each changed state on device; not exercised on device: commission element text, warlord line, Maxed tool state, trade-in sale text, run-end route |
| Weapon fame (session 7, merged into v4) | `./gradlew :core:test`; `:core:simulate --runs 1000 --seed 1 --policy all` with the fame numbers at 0 / desire+price only / half power / adopted; `:app:compileDebugKotlin -q` | 127/127 pass (5 new in `WeaponFameTest`: capped factor, famous beats plain in `evaluate`, capped price premium, returned legend at the cap, determinism); BALANCED_FAIR 20 (15/30) mean 22.5, sold 18.0, deaths 0.9 (was 20 (15/30) 21.7 / 17.7 / 0.8), BALANCED_ACTIVE 30 (20/35) mean 28.4, sold 24.0, deaths 1.0 (was 30 (20/35) 27.5 / 23.4 / 0.9), 0 hard-locks; desire and price alone change nothing, the +5 % power cap carries the shift (the brief's +10 % example overshoots the band: FAIR median 25, ACTIVE p90 40); app compiles; table in DECISIONS.md "Balance v4 (pending merge)" |
| Balance v3 review at 10k + per-tool / per-affix sweeps (session 6) | `:core:simulate --runs 10000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE --perf --json`; 1,000-seed `--noTool` / `--toolCost` / `--noAffixEffect` sweeps under BALANCED_ACTIVE (noise floor at seeds 10001 / 20001, SYNERGY cross-check); `./gradlew :core:test` | BALANCED_ACTIVE 30 (20/35) mean 27.7 at 10k (27.5 at 1k), BALANCED_FAIR 20 (15/30) 21.8, every 1k mean holds within 0.5 days, 0 hard-locks in 160,000 runs; all four tools removed +0.3 days (tools buy no run length), signboard capped out by `maxCustomersPerDay` (removed: +0.7 days, +1.2 sales), whetstone pays only at 100/250 (+0.8); single affix effects inside the 0.3-day noise floor, all ten off -0.3 (ACTIVE) / -0.9 (SYNERGY, Undead Bane alone -0.7); warlord-led sieges 0.02-0.03/run, never won; perf p50 0.51 / p95 0.99 ms warm; 122/122 pass; tables, commands and recommendations in DECISIONS.md |
| Gazette edition (session 7) | `./gradlew :core:test`; `:app:installDebug`; scripted days on the emulator (`scratchpad/drive_gazette.sh`, `drive_home*.sh`) | 127/127 at the time (5 new in `GazetteEditionTest`: hero folding, shop reasons, lede and implied milestones, forge line, a simulated run fully accounted for and order-independent); day 1-4 and day 10 (siege, run end) reports and the archive (latest open, older days as a lede, one opened) screenshotted and sent; the rounds toggle renders collapsed but was not tapped |
| Home panel on device (session 7) | `:app:installDebug` over a saved day-5 run; `tools/emulator/smoke.sh`; `:app:connectedDebugAndroidTest` | resumes on Home (siege-today block first, commission to answer, shelf with yesterday's visitors, champions, yesterday's lede); End Day returns to Home; smoke.sh SMOKE_DONE with shelf/town/resume checks ok on the 0.5.0 build; instrumented 5/5 |
| Balance v4 as merged (session 7) | `./gradlew :core:test`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE` before/after the whetstone and warlord changes; warlord sweep `--policy all --noImpact` x3 | 140/140 pass; BALANCED_FAIR 20 (15/25) mean 20.5, sold 19.6; BALANCED_ACTIVE 25 (20/35) mean 26.4, sold 26.9; 0 hard-locks; warlord sieges 0.5-0.6/run (was 0.0), p10 kept by dropping the 1.15 raid bonus; full table in DECISIONS.md |
| Weapon pruning (session 8) | `./gradlew :core:test` (`WeaponPruningTest`); `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE` before/after | 144/144 pass (4 new: prunable rule, 400-day pair identical but for the weapons map, every Lost reason classified, save round-trip); 400-day active pair: weapons 1,727 -> 857, save 1,745,453 -> 1,012,913 bytes; simulator output identical to 0.5.0 except the `affix weapons/run` line, which counts the end-of-run map |
| Day report recovery (session 8) | `:app:installDebug`; `scratchpad/drive_report_recovery.sh` on the emulator | End Day -> kill with the report open -> relaunch shows the report -> Begin day 2 -> Home -> kill -> relaunch stays on Home day 2; all checks ok, RECOVERY_DONE |
| End Day budget on the Android runtime (session 8) | `./gradlew :app:connectedDebugAndroidTest` (`EndDayPerfTest`) | 1/1 pass on the Pixel_10_Pro AVD (API 37, x86_64): 120 forced-survival days as the active smith, p50 4.38 ms, p95 8.45 ms, max 35.5 ms against the 200 ms budget; real mid-range hardware not measured |
| Balance v5 part 1: signboard and affixes (session 8) | `./gradlew :core:test`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE`; `--policy BALANCED_ACTIVE --noTool signboard --noImpact`; per-affix `--noAffixEffect <id>` under BALANCED_ACTIVE and SYNERGY before and after (32 runs, JSON); `:app:compileDebugKotlin -q` | 145/145 pass (1 new: the signboard lets 0-2 more customers in on the same seed); BALANCED_ACTIVE 25 (20/35) mean 27.2, sold 31.6 (was 26.4 / 26.9); signboard alone +0.4 days and +3.5 sales over no signboard (the old one was -0.1 / -0.9); Giant Slayer worth 0.42 elite kills a run (was 0.26), Reinforced -0.09 deaths (was -0.04), Heavy +0.07 (was +0.03), Swift, Cursed and Bloodbound unchanged in the aggregate; every other policy within 0.2 mean days; 0 hard-locks; app compiles; tables in DECISIONS.md |
| Hero daily life, merged (session 8) | `./gradlew :core:test`; `:app:compileDebugKotlin -q`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE` on the merged tree | 158/158 pass (13 new: 11 in `HeroDailyLifeTest`, 2 in `GazetteEditionTest`); app compiles (no UI change); BALANCED_FAIR 20 (15/25) mean 20.5, BALANCED_ACTIVE 30 (20/35) mean 27.5 (was 25 (20/35) 27.2), SYNERGY 35 (25/40) 34.7, maxed ACTIVE 40 (35/45) 40.6; deaths -0.1 a run; 8.1 % of hero-days at the hall, 6.5 % on ambitions, a guild in 97 % of runs; 0 hard-locks; tables in DECISIONS.md ("Balance v5, part 2") |
| Replays and weapon fates, merged (session 8) | `./gradlew :core:test`; `:app:compileDebugKotlin -q`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE` on the merged tree | 172/172 pass (14 new in `ReplaysAndWeaponFatesTest`; two more asserts in `WeaponPruningTest`; one seed range widened in `HeroDailyLifeTest`); app compiles (three lines in the day report); BALANCED_FAIR 20 (15/25) mean 20.5, BALANCED_ACTIVE 30 (20/35) mean 27.4, SYNERGY 35 (25/40) 34.7, no mean moved more than 0.1 day; artifact recovery 51-53 %, guild inheritance 0.04-0.12 a run (0 on the branch alone), merchant resale 0.01-0.03 a run; 0 hard-locks; the agent showed the replay feature alone leaves simulator output byte-identical; not run on a device yet; tables in DECISIONS.md ("Balance v5, part 3") |
| Lucky loot and forge-time affix count (session 8) | `./gradlew :core:test`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE`; `--policy BALANCED_ACTIVE` and `SYNERGY` with `--noAffixEffect lucky` | 173/173 pass (1 new: Lucky loot is always scarce and more frequent); Lucky is inside the noise floor for both bots (+0.06 / +0.07 mean days), which do not spend scarce stock; BALANCED_ACTIVE 25 (20/35) mean 27.4, BALANCED_FAIR 20 (15/25) mean 20.5, 0 hard-locks; table in DECISIONS.md ("Balance v5, part 4") |
| Legacy tracks, merged (session 8) | `./gradlew :core:test`; `:app:compileDebugKotlin -q`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE` on the merged tree | 186/186 pass (13 new in `LegacyTracksTest`); app compiles (no UI change: both upgrade lists iterate the catalog); new accounts identical to the part 4 baseline; maxed BALANCED_ACTIVE 45 (35/50) mean 42.2, longest 55 (was 40 (30/45) 40.4), maxed BALANCED_FAIR 35 (30/40) 36.5; Known Name +5 / +1.6 days (was +0 / -0.1), the three new tracks 0 days by construction (their own yardsticks in DECISIONS); 0 hard-locks; tables in DECISIONS.md ("Balance v5, part 5") |
| Balance v5 review at 10,000 seeds (session 8) | `./gradlew :core:simulate --args="--runs 10000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE --perf --json <out>"` | 290,000 runs: BALANCED_FAIR 20 (15/25) mean 20.7, BALANCED_ACTIVE 30 (20/35) mean 27.4, SYNERGY 35 (25/40) 34.8, BALANCED_INVEST 30 (20/40) 30.3, maxed FAIR 35 (30/40) 36.5, maxed ACTIVE 45 (35/50) 42.3, longest run 55, every run ends, 0 hard-locks; every 1,000-seed mean holds within 0.2 days; artifact recovery 49-55 %; perf p50 0.53 / p95 1.71 / max 2.24 ms over 1,000 forced days; full table in DECISIONS.md ("Balance v5 review at 10,000 seeds") |
| 0.6.0 on device (session 8) | `:app:installDebug`; `tools/emulator/smoke.sh`; `scratchpad/drive_v5.sh` (8 passive days); manual adb pass; `:app:connectedDebugAndroidTest` | SMOKE_DONE (shelf / town / resume ok); day reports show ambition days and the "From the field" fights, the rounds toggle opens three rounds; the Legacy panel lists all 11 tracks; instrumented 6/6, `EndDayPerfTest` p50 8.3 / p95 21.3 / max 47.1 ms with 367 weapons at day 120 (was 4.4 / 8.5 / 35.5 with 157: the active bot keeps more stock and the day does more); two screenshots sent. Not seen on device: a hall day, a lesson, a merchant, an inherited blade (none fell inside the 8 passive days) |
| UI declutter (session 4) | `:app:assembleDebug`, `:app:installDebug`, scripted screenshots of every panel at font scale 1.0 and 1.3 (`scratchpad/ui_v2/`), `tools/emulator/smoke.sh`, `:app:connectedDebugAndroidTest` | build ok; SMOKE_DONE with shelf/town/resume checks ok; instrumented 5 tests, 0 failures (ForgeHint x2, SaveStore, TitleScreen, Example) |

## Obstacles hit and resolved
- Launch content with the v1 siege numbers gave 0 survived sieges: heroes always fought the first faction by ID and
  three factions' growth (15/day) saturated pressure. Fixed by targeting the most pressing faction and growth 4/3/2;
  run length then retuned through forge damage (24 + 50x(ratio-1)) with siege modifier 2.0.
- The quality formula could not separate six core tiers (20-point core span vs 27-point roll); base 25 + 6/tier
  spreads them. Tier 5 remains a rare/epic split; the excellent top-tier pairs are 33-41 % legendary by design.
- Two tests encoded slice facts (12 signatures = 4 per slice family; `restless_graves` ineligible without
  Hollowbound): the signature test now checks the launch catalog (24 recipes); the world-event test expects the launch
  factions and the most pressing faction for Successful Patrol.
- The RANDOM policy reported 53 "hard-lock" days on launch content: it drew unaffordable moonsteel. It now draws only
  obtainable materials; 0 hard-locks in all runs.

## Known limitations
Written for release 0.6.0. In 0.7.0 on the branch `shop-day/m0` the following entries below no longer hold: the app
version line (it is 0.7.0, versionCode 7, not released and not tagged); the ore merchant's
stock is on sale the next morning (T1.9); a champion can fall on the walls (T3.8); Guild Patronage no longer works through
the visit chance (T3.6); rules, schema and content versions are raised and enforced (rules 3, schema 4, content 3); Home
is gone, so its two Home notes do not apply. The other entries stand, and the save-growth entry is restated with
measurements under "Save growth" above.
- Art provenance is undocumented for a store release: the seven top-level source images and both concept references carry embedded
  Content Credentials naming ChatGPT / OpenAI as the generator, the two packs are script-drawn, and no licence or attribution text exists
  under `Pixel art assets/`. How the art is described on a paid listing and the usage terms of the generating account are owner decisions
  (plan 5.7, 10.4); they block P8, not development.
- Starting energy and gold upgrades buy little run length (+0.1 to +0.3 mean days under the active smith); what they
  buy shows on the second yardsticks (about a sixth more weapons forged, one more tool level by the first siege).
  The three v5 tracks read 0 days for every bot: none hunts signatures, repeats a rare recipe or owns a Legend Board.
- The `weapons` map still grows with unsold stock: salvaged, shattered, donated and collected blades are pruned 30
  days later (session 8), but blades in storage, blades lost with a hero or seized (an event can bring them home) and
  kept-forever events are not. A plain smith who never salvages keeps about 3 weapons a day (3,140 after 1,000 days).
- `panel_gazette`/`panel_journal` frames and the pack's signature weapon variants are not used (the pack's 16 px
  signature sprites would clash with the 64 px concept weapons; signatures show their name and burst instead).
- Of the v3 review recommendations (DECISIONS.md), whetstone 120/300 and warlord pressure 50 are applied in v4, the
  signboard effect, the affix magnitudes, the Lucky loot and Known Name (starting regulars, +1.6 mean days) in v5.
  Guild Patronage still raises the visit chance, which the customer cap mostly swallows (the old signboard
  problem). v4 and v5 are reviewed at 10,000 seeds (DECISIONS.md, "Balance v5 review at 10,000 seeds").
- No champion can die on the walls (champions need health 50, a lost siege costs 40), so the walls odds for a fallen
  blade are reached only in unit tests; merchant resale is rare (0.01-0.03 a run) because few blades are left over.
- Cursed and Bloodbound self-harm (15 / 20 health a win) does not show in deaths or run length; the simulator has no
  per-wielder or expedition-count metric to show the rest days it costs.
- The simulator's other policies ignore the new shop actions, so their rows measure a smith who never uses them.
- Weapon wear costs every policy 1-2 mean days and had cut the lead of the active smith over the plain one from 10
  median days to 5. In v5 the mean lead is 6.9 days (27.4 against 20.5, was 5.9); the active median sits on the siege
  boundary between 25 and 30 and reads either on a 0.1-day change, so it is no evidence either way. The wear levers
  (condition floor ~0.8, less siege wear) stay unused; the owner has not decided whether more is wanted.
- `BalanceConfig` is at the JVM limit of 255 parameter slots (1 free on the generated `copy$default`; a Double takes
  two). New numbers go into a nested group (`HeroLifeConfig`, `WeaponFatesConfig`, `LegacyTracksConfig`), or the class
  compiles and every test fails at class load.
- A maxed account under the active smith is at median 45 days (35/50, longest 55): the signboard and the Known Name
  regulars stack. Inside the band asked for (about 50) but at its top; confirmed at 10,000 seeds (mean 42.3). Not to
  be lowered automatically: it is reassessed after the major update's customer and economy changes, on distributions
  and each upgrade's own yardstick (docs/MAJOR_UPDATE_PLAN.md, section 4.3).
- Noticed, not fixed: the Traveling Ore Merchant adds +2 supplier stock at End Day and the next morning restock
  overwrites it, so only its free unit is ever seen.
- Not exercised on device: Re-hone, the worn / storied labels, the WORN_OUT visit reason, and from 0.6.0 a hall
  day, a lesson, the merchant lines and an inherited blade (they need a longer played run; covered by JVM tests).
  Home repeats the Shelf line in its Yesterday block when yesterday had no lede, and has no first-run tip.
- Records that now accumulate faster: `GUILD_FOUNDED` (kept forever) fires in about 92 % of runs instead of 38 %,
  and a Known Name account writes a second `RUN_STARTED` record naming its regulars.
- Package name is still `com.example.blacksmithproject`; no release signing.
- `GameEngine.RULES_VERSION` stays 1 although the rules and the RNG draw order have changed several times. It seeds
  the run RNG, so raising it reshuffles every run, and nothing enforces it against a loaded save; it should move
  together with a save-compatibility policy (the external review's F09), not alone; the major update plans exactly that (plan 6.7, tasks T1.5a/b).
- Git: `main` tracks https://github.com/Morfildor/BlacksmithInc; commit and push per verified milestone. App version 0.6.0 (versionCode 6), tag `v0.6.0`.

## Next executable actions (P7)
Superseded on 2026-10-10 by "Next actions" near the top of this file. The older list (the 720x1280 pass, the first
migration step, the package rename and release signing) is covered there or in the "Not done" list: the migration steps
exist (schema 4), the small-screen pass is T6.1 (only a width test exists), the rename and signing are blocked on the
application ID.
