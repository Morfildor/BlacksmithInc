# Progress — 2026-10-10 (major update)

## Current phase
P7b, the major update (the "shop day"), is built and merged on the integration branch `shop-day/m0` (tip `d946c4f`),
which is pushed to GitHub as the branch `major-update`. `main` is still release 0.6.0 and nothing of the update is
released. The app version is still 0.6.0 (versionCode 6); the changes are under `[Unreleased]` in `CHANGELOG.md`.
Versions on the branch: rules 3, save schema 4, balance config 8, content 3.
The plan is `docs/MAJOR_UPDATE_PLAN.md`; the row-per-task tracker with evidence is `docs/MAJOR_UPDATE_LEDGER.md`;
rulings and open decisions are in `docs/DECISIONS.md` ("Major update: rulings and open decisions (2026-10-10)").
The sections from "Session 6 focus" down to "Known limitations" are the history of 0.6.0 and earlier, kept as written.

**Short version.** Almost everything in the plan's milestones M0 to M4 is merged and passes its JVM tests (391 core,
111 app). The earlier parts (saves and recovery, the shop day itself, the four destinations, the hero and blade
sheets) were also seen running on the Android emulator. Everything merged in the last
stretch (main menu, the new Shop / Forge / Town pages, both visual polish passes, the wants / threat / request / legend /
journal screens, save-growth rules) has **not** been run on a device by the integrating session; a device gate is
running on the tip now and its result is not yet known. Nothing at all was checked on a physical phone.

### How to read the verification words
- **Seen on the emulator**: somebody ran it on the Android virtual device (Pixel_10_Pro) and looked or a script
  checked it. Where it says "agent tree", an agent saw it on its own copy before the merge, not on the merged build.
- **JVM tests only**: automated tests on the development machine pass; it was never run on an emulator or phone.
- **Built, never run**: the code or the device test exists and compiles; nobody has executed it.
- **Not built** and **blocked** (waits for something outside the repository) mean what they say.
- "Simulator" means the headless balance harness that plays thousands of runs with scripted players ("bots").

## What is in the build, by area (branch `shop-day/m0`)

### Saves and recovery
- Every operation that changes the game (a command, a legacy purchase, Claim, Begin era, Abandon) goes through one
  serialized session, so two quick taps cannot lose a purchase or overwrite a new era. *JVM tests (19 session tests;
  9 of them fail if the lock is removed) and seen on the emulator: a force-stop after Claim reopens claimed with the
  upgrades, and a kill around End Day never left half a day.*
- A save that cannot be opened shows a recovery screen (Try again, or Start over with the unreadable run kept as a
  backup) instead of crashing. A damaged file is left untouched until the player confirms. A damaged legacy record
  can be rebuilt from the copy the run carries. *Seen on the emulator for a damaged row, a damaged database file and
  a damaged legacy row. Never seen on a device: the "save is from a newer version" and "incompatible" screens and
  the "could not confirm the save" dialog (JVM tests only).*
- The game checks a save's rules, content and schema versions on load and migrates older saves (fixtures for schemas
  1, 2 and 3 are in the tests). *JVM tests; an old-format day-1 save opened and accepted End Day on the emulator.
  An upgrade from the real 0.6.0 APK over an existing install was not run.*
- Known gaps: a database file cut to 0 bytes opens as a new game with no message; file damage that appears while
  playing shows the general "could not save" dialog until the next start.
- Android's automatic cloud backup of the save is switched off until restoring one is tested.

### The shop day (what happens after End Day)
- End Day saves the day first, then shows it card by card: the shop opens, each featured customer at the counter as a
  framed portrait with what they looked at and why they bought or left, a receipt for a sale, the other visitors as a
  tally, the till, up to three cards of what happened beyond the door, then one lead for tomorrow (or the blessing
  choice, or the fall of the forge). Next, Back, Skip day, and 1x / 2x auto-advance. *Seen on the emulator on the
  integrated build at the time (smoke script 9 of 9, a passive run to defeat, claim and next era 5 of 5).*
- The place reached in the day is saved. Killing the game mid-day offers "Resume the day" or "Skip to tomorrow";
  watching, skipping or killing never changes what happened. *Seen on the emulator: same card after a force-stop, the
  saved run byte-identical through watch / kill / skip, ten kills within 400 ms of End Day.*
- Card timings were shortened (open 1200 ms, arrive 1000, browse 900, decide 1500, tally 2000) so an unattended day
  at 1x has a median of 24.6 seconds against a budget of 25 with the larger town. *JVM test that sums the timings; not
  timed on a device.*
- A day the counter cannot lay out no longer blocks the game: it counts as watched and the Shop opens. *JVM test only.*
- Never seen in the real app on a device: a blessing day and a defeat day card by card, a siege aftermath card, a "fell
  at the wall" card, the replay overlay, a sheet opened from the counter, the cards at font scale 1.5 or 2.0, TalkBack.
- Departures from the plan: one card per featured visit (not four), the replay overlay is text only.

### Customers and town
- Fair seating: every living hero gets an equal turn at the counter (before, the eighth hero was served less than half
  as often as the first four). *JVM tests and simulator: served ratio by list position 0.39 to 0.97.*
- A town of 12 heroes with room for 16, all five classes from the first morning, six counter seats (seven or eight with
  the Signboard), raid pressure retuned to keep run length. *JVM tests and simulator at three base seeds.*
- 120 first names and 96 surnames, no shared names among the living; lineages matched by ID. A stored face per hero,
  spread evenly within a class. *JVM tests and simulator.* The owner's 20 hero portraits (base and upgraded face) are
  imported. *All 40 tiles viewed in the renderer on the emulator; the upgraded face was not seen in a live game.*
- Recognition lines at the counter ("first blade from your forge", "a regular returns", "held the wall"). *JVM tests;
  one line seen on the emulator on day 1; the regular's mark not verified there.*
- Guild Patronage is now a willing guild plus a 30-gold stipend per member. *JVM tests and simulator. Its own target
  is not met: open owner decision.*
- A champion can fall on the walls when a siege is lost badly (the rout rule). *JVM tests and simulator.*
- Earlier engine corrections: champions ranked against the foe they will face; the ore merchant's stock is on sale the
  next morning; faction ties break the same way everywhere; the Gazette's tally and the archived day are complete; the
  collector's price is capped. *JVM tests and simulator only.*

### Demand: wants, the besieger, requests, legends, the journal (milestone M4)
The rules are in `:core` and were measured in the simulator at three base seeds; the screens were added afterwards.
- Standing wants: a hero who leaves with nothing leaves a want (kind of weapon, strength, purse) for three days.
- Buyers weigh a blade's properties and fame; a sidegrade can be bought once for a reason; before a siege the town
  wants the element the besieger fears and passes over the one it resists.
- The journal's clue ladder (four clues per hidden recipe), rumours, and "Use this recipe".
- A returned legend is the blade it was, with its properties dormant until honed once; shorter weapon names.
- Requests have reasons (replacement, a blade for the wall, collector, a newcomer's first blade); two can be open.
- *Rules: JVM tests and simulator. Screens (wants in "Who is buying" with "Forge this", the besieger line on the Shop
  and Forge plates, the "why" on a request, Story and Dormant on the blade sheet, the Legend Board in full, the ladder
  in Records): app JVM tests pass (9 added); the six device tests in `M4ScreensTest` are built, never run; **nothing
  of these screens has been seen on a device**.*

### The planning screens
- Four destinations (Shop, Forge, Town, Records) and a settings sheet replace the seven tabs. *Seen on the emulator at
  font scale 1.0, 1.3 and 2.0; at 2.0 the bottom bar clips "Records".*
- The Shop is one page: counter and shelf, the one thing worth doing first, requests, "Who is buying", yesterday, the
  shelf, a Storage sheet. Home and Market are gone. *Seen on the emulator on an agent's tree (6 device tests, smoke
  10 of 10, screenshots at 1080x1920 and at 720x1280 with font 1.3 and 2.0). Not on the merged build. The shelf rows
  of the final build are in no screenshot; the Storage sheet closed when the screen size changed (possible bug).*
- The Forge has the forge room header, "Forge this" from a request, Supplies and Journal buttons; Supplies is a sheet;
  Town is one fast list with shorter rows and a "Fallen and retired" header. *Seen in part on an agent's tree before
  its last edits (Forge and Town at 1080x1920). Never seen: the Supplies sheet, the pinned "For ..." request line, the
  compact Town rows, the "Fallen and retired" header, anything of these at a small size or large font.*
- Hero and blade sheets (taste, purse, guild, mentor, history, each property's effect; pricing, listing, salvage, hone
  on the blade sheet). *Seen on the emulator at font 1.0 and 2.0 (6 device tests at the time). Not exercised on a
  device: List / Set price / Salvage from the sheet, the links between sheets.*
- Haptic feedback with a switch in settings. *Switch seen on the emulator and persists; the feel needs a phone.*
- Each permanent upgrade says what its next level does in numbers. *Seen on the emulator by the run-end script; the
  "maxed" line was not seen.*

### Main menu
- The game opens on a menu: New game or Continue run, Abandon run (with a confirmation; discards the run and claims
  nothing), and a Settings icon. Settings in the workshop has a "Main menu" entry. *Seen on the emulator by one agent:
  the menu and its buttons on a fresh install and with a saved run. **The tap on "Abandon run", its confirmation and
  whether the run is gone after a relaunch were never checked on a device**; the operation itself has a JVM test. The
  rewritten device test for the menu is built, never run.*

### Visual polish
- Pass 1: one dark forge theme in both system modes, bronze-framed panels, a gold primary button, a blade shown as an
  item card with power, quality and condition, buffs ("+") and flaws ("−"). *Seen in part on an agent's tree at
  1080x1920: Settings over the menu, Shop, Forge, the forge result card, the lower half of a blade sheet. Not seen: hero
  sheet, Town, Records, the shop-day cards, run end, the card with large text. Its device test is built, never run.*
- Pass 2: the planning screens in the same look (lead, requests, shelf and storage rows with numbers, the Forge's
  plate with a full-width Forge button, Supplies, Town, Records, the top and bottom bars); no light flash at start.
  ***Built, never run on any device**: every layout statement about it comes from reading the code.*

### Save growth
- Four more bounds keep a long save smaller: closed requests leave after 30 days, the newest 30 End Day IDs are kept,
  arrivals and world events are kept 30 days, a blade keeps its newest 24 everyday history lines. *JVM tests: the
  same seed with the rules off reaches the same outcome.*
- A 2,000-day soak for two smiths (`./gradlew :core:soak`). End Day stays fast: 95th percentile 1.7 to 6.7 ms on the
  development machine against a budget of 200. **The save does not level off**: 3.7 to 4.7 MB at day 1,000, growing
  3.3 to 4.2 KB a day; unsold stock is 60 to 65 % of that and is kept by decision. *JVM only. Not run: loading such a
  save on a device, frame times in Storage with thousands of blades.* A real run ends by day 65 in every simulation.

### Tooling
- CI on GitHub (core tests, app unit tests, debug and minified release builds, lint) was green on the branch at the
  main-menu commit; it does not compile or run the device tests. A manifest guard keeps permissions and network
  libraries out. 13 simulator bots, customer metrics, a golden reference run. A release runbook and a minified trial
  build (4.2 MB) that played a day on the emulator.

## Checks run for this update
| Check | Result |
|---|---|
| Core JVM tests on the tip `d946c4f` | 391 pass (reported by the controller, 2026-10-10) |
| App JVM (unit) tests on the tip | 111 pass |
| Debug APK on the tip | builds; the device-test sources compile |
| Lint | 0 errors, 43 warnings on the last agent tree before the tip; not re-run on the tip by the controller |
| CI on GitHub | green at the main-menu commit (run 37999273824, 9 m 45 s); no run recorded for the tip |
| Last full device-test run by the controller | 36 of 37, **before** the main-menu, M4-screen and polish merges. The one failure was the End Day speed test at 232 ms (95th percentile) with the emulator under load; alone it passes at 29 to 89 ms |
| Device gate on the tip (full device-test suite, `smoke.sh`, `runend.sh`) | **device gate in progress, result to be recorded.** The emulator hung at 08:21 and was restarted at 09:50 |
| Agent device runs since the last full run | Shop page: 6 of 6 device tests and smoke 10 of 10 on the agent's tree; Forge / Town / Supplies: 12 of 12 and smoke 10 of 10 before its last 9-line edit; polish pass 1: screenshots only, no device test. Polish pass 2, the Town at the new scale and the M4 screens: nothing |
| Simulator, balance 8, 1,000 runs at base seeds 1 / 10001 / 20001 | plain smith (FAIR) mean 22.4 / 22.7 / 22.5 days, median 20 / 25 / 20; active smith 30.0; SYNERGY 35.3 to 35.5; EXPERT 45.6 to 45.8; maxed EXPERT 54.4 to 54.8, longest run 65; every run ends. Table in DECISIONS.md, "M4 gate" |
| Simulator at 10,000 seeds for balance 8 | not done; running on an agent branch |
| Soak, 2,000 days, JVM | see "Save growth" above; DECISIONS.md, "Production soak" |
| Physical phone | nothing run |

## Not done, not verified, blocked

Not verified on any device (built and merged; JVM tests pass)
- Polish pass 2 on every planning screen; the Town's compact rows and "Fallen and retired" header; the Supplies sheet.
- All M4 screens: wants and "Forge this", the besieger line and the "+" / "−" marks on forge chips, the "why" on a
  request, Story and Dormant on a blade, the Legend Board, the clue ladder and "Use this recipe".
- Main menu: the Abandon run confirmation and its result after a relaunch.
- The recovery screens for a newer or incompatible save; the "could not confirm" dialog.
- In the shop day: a blessing day, a defeat day, a siege aftermath, a "fell at the wall" card, the replay overlay.
- From 0.6.0 and still never seen on a device: a guild-hall day, a lesson, a merchant resale, an inherited blade.
- Device tests that exist and were never executed: `M4ScreensTest` (6), `WeaponStatCardTest`, the rewritten
  `TitleScreenTest`, the edited `DetailSheetTest`. `LayoutMatrixTest` (widths at 360 and 320 dp, font up to 2.0)
  passed once, before polish and M4 changed the layouts it checks; it does not check heights.
- Layout at small sizes and large fonts after the polish and M4 changes; dark-on-dark contrast in screens not shot;
  TalkBack (never run); lint and CI on the tip.

Built on an agent branch, not merged, not in the build
- Two fixes where save pruning met newer rules: a hero whose only blade shattered more than 30 days ago could no
  longer get a replacement request, and "held the wall" was forgotten after ten later fights. Both reproduce on the
  tip and are reported fixed (branch `worktree-agent-a21d7457a3a6e63af`, commits `c73dde7`, `285638a`). They only
  matter in long saves.
- Onboarding coach line (T2.10), Storage filters and bulk actions (T6.3c), main-thread work (T6.5), lifecycle tests
  (T6.4): four commits on branch `worktree-agent-a23a96c7b45829e3f`; JVM tests pass there, their device tests have
  never been run. T2.10 and T6.3c were parked by the owner ("finalize a build before adding features").
- The 10,000-seed review of balance 8 (T5.5): running, no result.

Not built
- Device scripts for killing the game mid-day and for layout screenshots; scenario saves that would show a lesson, an
  inherited blade, a merchant resale or a wall death on a device (T2.9, partial).
- TalkBack and semantics work (T6.2); the layout matrix beyond three screens, and fixes from it (T6.1, partial).
- The device half of the soak: cold load, a save commit, frame times and memory with a 1,000-day save (T6.3b).
- The per-upgrade gate table after the town change (T5.3); the recovery re-measure at balance 8 (T5.2).
- A rule that lets a player clear hundreds of stored blades quickly (needs an owner decision).
- Version bump and release notes (T7.1), the GDD section 19 walk-through (T7.3), the launcher icon (T7.4).
- Audio of any kind. A light theme. A tutorial beyond tips.

Blocked on things outside the repository
- Checks on a physical phone, including haptic feel and End Day speed on mid-range hardware (T6.6).
- Sessions with five first-time players (T7.2).
- The release application ID (still `com.example.blacksmithproject`), release signing.
- Audio assets, the launcher icon, further portraits and art (the owner supplies them).

## Open owner decisions
Full wording, numbers and options are in `docs/DECISIONS.md`, "Major update: rulings and open decisions (2026-10-10)".
Balance numbers are never lowered automatically; the distributions are reported and the owner decides.
1. Guild Patronage ships at a stipend of 30 with its own target unmet.
2. The plain smith's first era: mean 22.4 to 22.7 days against a band top of 22.5.
3. A maxed account's lead over a new one for the SYNERGY bot: +9.96 to +10.3 mean days against a floor of +10.
4. The bot that forges what customers ask for lives +8.4 to +9.6 days longer than the plain smith (bound +5) while
   answering only about 30 to 34 % of wants (target 60 %).
5. Abandon run discards the run and claims nothing.
6. The blade card and the shelf rows now show power, condition and fame as numbers.
7. Whether inheritance records are kept for the whole run (they are half of the kept records in a long save).
8. The save envelope format (13 % of the file is escaping).
9. End Day remembers only the newest 30 command IDs.
10. The save does not level off; unsold stock is 60 to 65 % of its growth.
11. Outside the code: the application ID, the icon, audio, art, how AI-generated art is described on the store, and
    whether to commit the external review and the evidence folder (both still untracked).

## Next actions
1. Record the result of the device gate that is running on the tip (full device-test suite, `smoke.sh`, `runend.sh`)
   in the ledger and in the table above; fix what it finds before the owner's hand test.
2. Decide which unmerged agent branches go into the test build: the two pruning fixes (small, core, tested) and the
   four app commits (parked features among them), then re-run the JVM suites and the device gate on that tip.
3. Take the screenshots nobody has: every planning screen after polish pass 2, the M4 screens, the Supplies sheet, the
   Town list, at 1080x1920 and at 720x1280 with font 1.3 and 2.0. Run `LayoutMatrixTest` and `M4ScreensTest`.
4. Check "Abandon run" by hand or by script: confirm, see the menu offer a new game, relaunch, the run is gone and the
   legacy is unchanged.
5. Merge the 10,000-seed review when it finishes and record it in DECISIONS.md; run the per-upgrade table with it.
6. Owner hand test (list below), then the owner's decisions above.
7. After that: T2.9 scenario saves and scripts, T6.1 / T6.2 layout and TalkBack, the device half of the soak, T7.1
   version and changelog, T7.3 walk-through. Phone checks and playtests wait for a device and players.

## What to try when testing by hand
A debug build of the tip. Items marked **layout risk** are the ones most likely to show clipping, overlap or a button
pushed off screen; each is a concern written in a report and none has been seen on a device. To test them, set the
phone's font size to its largest and, if possible, use a small or short screen.
1. First launch: the main menu shows New game and the Settings icon. Start a game; the Shop opens on the lead "Forge
   your first blade" with a button that goes to the Forge. Watch for a light flash at start (there should be none).
2. Forge a blade. The result appears as an item card with power, quality and condition, buffs and flaws. **Layout
   risk** with large text: the card's two columns should fall into one, and "Renown" should not wrap badly.
3. List the blade from its card or from Storage, set a price on the blade sheet, then End Day and watch the day card
   by card. Try Next, Back, Skip day, 1x and 2x, and tap anywhere. Back must never begin the next day.
4. During a shop day, close the game from the recent-apps list and reopen it: it should offer "Resume the day" or
   "Skip to tomorrow", and the day must be the same either way.
5. Open the main menu from Settings, choose Abandon run and confirm. The menu should offer a new game; close and
   reopen the game and check the run is still gone and your legacy points are unchanged. **This was never run.**
6. The Shop page, top to bottom: counter plate with "Seats 6" and the siege line, the lead, requests, "Who is buying",
   yesterday, the shelf, Storage, Supplies. **Layout risk**: with large text the plate now has one or two extra lines
   about the besieger, and the lead's reason may be hidden behind End Day on a short screen.
7. A request card (accept one when it appears): Accept, Decline and "Forge this". **Layout risk**: three buttons in a
   row that should wrap at a narrow width with the largest font. The card should say why the request was made.
8. Tap "Forge this" on a request: the Forge opens with the family chosen and a line "For <name>: ..." pinned above
   the steps. **Layout risk**: the pinned plate is taller than before (the Forge button now runs its full width), so
   on a short screen with large text little room is left for the steps.
9. On the Forge, look at the augment chips a day or two before a siege: an element the besieger fears should carry
   a "+", one it resists a "−", with a line under the chips. Open Supplies from the Forge and from the Shop and buy
   something (this sheet has never been seen on a device).
10. After a customer leaves without buying, look at "Who is buying" the next morning for a line such as "wants a bow;
    can spend about 90 gold" with "Forge this"; forge and shelve that kind and check the line gets a tick.
11. Town: scroll the list of twelve heroes, tap one for the hero sheet, tap their blade for the blade sheet and its
    Story and History. After some deaths, open "Fallen and retired". Does the Town name the same besieger as the
    Shop and the Forge? (They can differ when two factions are tied.)
12. Storage with many blades: open it, scroll, use "List at". Rotate or resize the screen if the device allows and see
    whether the sheet stays open (it closed once in a script). **Layout risk** with large text: "List at" moves under
    the blade.
13. Records: News (yesterday's Gazette and older days), Journal (after a failed attempt at a hidden recipe, a ladder
    of four clues; a found signature has "Use this recipe"), Legacy (upgrades saying what the next level does; the
    Legend Board after a first era).
14. Play to a lost siege: the fall of the forge, the run-end screen, Claim, buy an upgrade, Begin era. Tap two
    upgrades quickly; nothing should be lost. Close the game on the claimed screen and reopen it.
15. Settings: reduced motion, the Haptics switch (feel a forge, a sale, End Day and a run end with it on, nothing with
    it off), and the bottom bar at the largest font (**known**: "Records" is clipped at font scale 2.0).

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
Written for release 0.6.0. On the branch `shop-day/m0` the following entries below no longer hold: the ore merchant's
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
exist (schema 4), the small-screen pass is T6.1 (partial), the rename and signing are blocked on the application ID.
