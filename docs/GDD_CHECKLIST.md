# GDD checklist — built vs. left

One line per GDD feature, by GDD section. `[x]` = built and verified (evidence in PROGRESS.md / DECISIONS.md);
`[ ]` = open, with the missing piece named so ticking it means done. Tick items here when they ship.
IMPLEMENTATION_PLAN.md is the phase-gate view; this is the feature view. DEFERRED scope (GDD 16.2: multiplayer,
manual equip/combat, walking, idle production, online, accounts, achievements, ads, IAP) is not listed and is not built.

Last reviewed: 2026-10-09, app 0.6.0, balance config v5, 186 JVM tests (developer-run count, not an independent execution).
Wording corrections C01-C09 and C12 of the external review (docs/Tiny_Blacksmith_Thorough_Review_2026-10-09.md, section 9) were applied
by plan task T0.3: a ticked line states only what its evidence shows, and each narrowed claim has an open line next to it.

Reviewed again: 2026-10-10, for the major update on the integration branch `shop-day/m0` (pushed as `major-update`; not released,
`main` is still 0.6.0). Rules 3, save schema 4, balance config 8, content 3; 391 core and 111 app JVM tests pass on the tip.
How a line is ticked in this review: a rule that lives in `:core` is ticked when its JVM tests pass, and the line says "JVM tests";
something the player sees is ticked only when a report says it was seen running on the emulator, and the line says so. A screen that
is built and merged but was never run on the emulator or a phone stays open with a note "built, not seen on a device". Nothing in
this file was checked on a physical phone. Evidence: `docs/MAJOR_UPDATE_LEDGER.md` (one row per task) and `docs/PROGRESS.md`.

## 3 Player loop
- [x] Run creation: seed, era, random faction pressures and world modifiers, 100 integrity, hero pool, legacy unlocks
- [x] Planning phase: yesterday's paper, shop, forecast, heroes, inventory, supplier, commissions; nothing advances without End Day
- [x] End Day in the fixed GDD 3.2 order, idempotent per command ID; each accepted command through the ViewModel dispatch is saved atomically before the report
- [x] Every authoritative operation (End Day, legacy purchase, claim, Begin era, abandon) goes through one serialized session, so a run transition cannot race a stale write (review F01; T1.1, T1.3: `GameSessionTest`, and on the emulator a force-stop after Claim and around End Day never left a half-written run)
- [x] Run end at integrity 0: summary, claim-once legacy, next era
- [x] Home dashboard (owner request, beyond the GDD)
- [x] Day report shown again after process death until it is dismissed
- [x] Resume on launch restores the committed world (the saved run) and the unread day report
- [x] The shop day has a saved position: closing or killing the game mid-day offers "Resume the day" or "Skip to tomorrow", and watching, skipping or killing never changes the day (T2.3: emulator, seed 42, including ten kills within 400 ms of End Day)
- [ ] Resume after the system closes the game for the forge draft, the forge result and the open destination: built (T1.3), JVM `SavedStateTest` only, not seen on a device. Lifecycle tests on a real Room file (T6.4) are written on an agent branch, not merged, never run

## 4 Forging and discovery
- [x] 6 families, 16 materials (6 core, 6 augment, 4 catalyst), data-driven with hidden affinities
- [x] Quick Forge (2 energy) and Advanced Forge (4 energy, catalyst, Temper / Quench / Etch techniques)
- [x] 10 energy, up to 4 overwork, next-day exhaustion
- [x] Safe / Balanced / Reckless rolls, GDD quality and power formulas, always a usable weapon, defects attach a flaw
- [x] Rarity tiers from quality plus earned fame and titles
- [x] 12 beneficial affixes and 6 flaws, each with a gameplay effect (bane, heal, loot, shatter, self-harm, ...)
- [x] 24 signature recipes (4 per family), transformation chance, discoverable through experiments and world clues
- [x] Journal UNKNOWN -> OBSERVED -> UNDERSTOOD -> SIGNATURE_DISCOVERED, live hints, no farming, survives death
- [x] Clue ladder (four clues a hidden recipe), rumours and recall (T4.3, JVM tests and simulator). The ladder and "Use this recipe" in Records: built, not seen on a device

## 5 Economy, shelves, commissions
- [x] 250 gold, 8 shelves (+2 with the Display Case), supplier with always-stocked basics; 0 hard-locks for the recorded simulator policies (no softlock for those bots, not shown for every player path)
- [ ] Adversarial recovery: resting, drought and stuck days measured separately, and shock scenarios (no gold and no stock, empty shelves, a lost siege) shown to recover (review G10; plan T5.2). Measured at balance 6 only (stuck streaks of three days or more in 0.00-0.10 % of novice runs; an over-spending bot at 1.9-3.0 %); the shock arms without a simulator option and the re-measure at balance 8 were not run
- [x] List / unlist, manual price per weapon, suggested price, +/-10 buttons
- [x] GDD utility purchase (improvement, class fit, element taste, traits, loyalty, price), one purchase a visitor, reasons shown in words
- [x] Commissions: patron, family, min quality, element, reward, deadline; accept / decline; small expiry penalty
- [x] A commission asks for a named quality band, one rule decides delivery, the patron collects before the browsers and takes the least blade that fits (review F04, F06; T1.8, JVM tests). The request card was seen once on the emulator; the handed-over line was not
- [x] Requests have reasons (replacement, a blade for the wall, collector, first blade for a newcomer) and two can be open at once (T4.6, JVM tests and simulator). The "why" line on the card: built, not seen on a device
- [x] Fair customer selection: every living hero gets an equal turn at the counter (review F05; T3.1, simulator: served ratio by list position 0.39 to 0.97)
- [x] Standing wants, the sidegrade gate and siege demand (T4.1, T4.2; JVM tests and simulator). Their screens (wants in "Who is buying", the besieger line on the Shop and Forge plates): built, not seen on a device
- [x] Reputation (sales, commissions, hero success) and per-hero loyalty; both reset per run except the Known Name perk
- [x] Shop actions beyond the GDD: Salvage, Hone, Arm the watch, trade-ins, four workshop tools
- [x] Signboard tool: +1 customer a day per level

## 6 Heroes, champions, generations
- [x] 5 classes, 9 traits each wired to a decision weight, element taste, wealth, ambition, deterministic names and portraits
- [x] Daily choice rest / shop / expedition / patrol / defend by trait-weighted seeded utility; wounded rest, dead never act
- [x] GDD effective-power formula; a suited weapon measurably helps
- [x] Three dynamic champions chosen as the strongest available heroes (health gate), 0-2 handled
- [x] Champion ranking in a warlord siege uses the same foe context as the outlook (review F08; T1.7, JVM tests; not looked at on a device)
- [x] A town of 12 with room for 16, all five classes from day one, six counter seats; 120 first names and 96 surnames; a stored face per hero spread over the class (T3.2, T3.3, T3.4; JVM tests and simulator at three base seeds; not at 10,000 seeds)
- [ ] Recognition lines at the counter (T3.5): the rule is tested on the JVM; one line was seen on the emulator on day 1; the regular's mark was not verified there
- [ ] Guild Patronage as a willing guild and a 30-gold stipend (T3.6): built and tested on the JVM; its own target (+0.6 days or +0.3 sales a day) is met by no setting, an open owner decision
- [x] Level-ups, fame, retirement, guild founding, mentoring, newcomers when the population thins
- [x] Lineage anchors across eras; descendants reference stored ancestors
- [x] Guild hall (training, lessons from a higher-level guildmate, founding and joining) and ambition as scored daily activities; money and yesterday as inputs

## 7 Living weapons
- [x] Lifecycle with one authoritative location, history entries, titles from real records
- [x] On death: recovered by the shop, seized by the enemy, or lost; on retirement: inherited by the mentee
- [x] Bounded Legend Board; a famous blade returns dormant (reduced quality) in a later era, keeping its fame
- [x] Fame as a capped mechanical effect (power, desire, price); wear and condition
- [x] Fates of a fallen hero's blade by where the hero fell: guild inheritance, merchant resale (fame raises the chance), seizure, loss
- [x] A returned legend is the blade it was (signature, flaws, catalyst, title, story), its properties dormant until honed once; at most one property in a new blade's name (T4.5, `ArtifactFidelityTest`, JVM). The Story section and the dormant marker on the blade sheet: built, not seen on a device
- [x] A champion can fall on the walls when a siege is lost badly (the rout rule, T3.8; JVM tests and simulator: 17-19 % of plain first eras). A "fell at the wall" card was not seen on a device

## 8 Town, factions, sieges
- [x] 3 factions with pressure 0-100, growth, suppression, weakness / resistance, elites, warlords
- [x] Sieges on days 5 / 10 / 15 ..., warnings 1-2 days ahead with descriptive danger, siege forecast in Town
- [x] GDD raid / defense / forge-damage scaffolds (retuned), militia from patrols, bounded hero-driven recovery, no gold repair
- [x] Encounters and sieges from one deterministic event series; read-only CombatReplay

## 9 Variation and legacy
- [x] 8 blessings, three offered after a survived siege, temporary and seed-dependent
- [x] Legacy points = base + days / 5 + discoveries + milestones, claimed once per run
- [x] 11 upgrade tracks at 8 / 20 / 45: energy, gold, mastery, walls, efficiency, stock, luck, reputation, supplier depth, signature odds, legend returns
- [x] Persistence matrix: journal, signatures, upgrades, Legend Board, lineages, unspent points persist; the rest resets
- [x] Upgrade tracks for catalog access (Caravan Ties), recipe odds (Anvil Lore) and legacy-artifact opportunities (Homing Steel)
- [x] Starting gold / energy upgrades measured on second yardsticks (weapons forged and tool levels by the first siege); run length stays within +0.3 mean days

## 10 Content
- [x] 16 materials, 6 families, 5 classes, 3 factions, 25 events (23 pooled + 2 generational rules), 24 signatures, 12 affixes, 6 flaws, 8 blessings
- [x] Events with eligibility, weight, effects, story template, repetition limits; artifact / lineage events ineligible without one
- [x] ContentCatalog.validate() over every ID

## 11 Gazette and replays
- [x] Daily paper from real event records, priority order, 3-5 headlines, archive
- [ ] Paper complete and consistent: the report, the shop and the archive show the same day, and the tally accounts for every gold and hero event (review F03, F07). Built and merged (T1.4, T1.6, T2.1; JVM tests: a fixture day returns 20 of 20 records, typed money ledger); the three views were not compared on a device
- [x] Edition layout: lede, tally, Shop / Heroes / Town / Forge
- [x] Siege replay: stepped text and an animated diorama fed by the CombatReplay only; skippable; reduced motion honoured
- [x] Replays for significant expeditions: elite fights and deaths, folded text in the day report, at most three a day

## 12 Workshop UX and accessibility
Lines ticked before 2026-10-10 describe the seven-panel layout of 0.6.0 (Home, Market and the rest); the major update replaced
it, and the lines dated T2.x below say what exists now.
- [x] Main menu on launch: New game or Continue run, Settings (seen on the emulator). "Abandon run" is built and its rule is tested on the JVM; the confirming tap and the run being gone after a relaunch were never checked on a device
- [x] Four destinations (Shop, Forge, Town, Records) with a settings sheet (T2.8a: emulator at font scale 1.0, 1.3 and 2.0; at 2.0 the bottom bar clips "Records")
- [x] The shop day after End Day: customers at the counter card by card, receipts, the tally, aftermath and tomorrow's lead, with Next, Back, Skip day and 1x / 2x (T2.5, T2.6: emulator; a blessing day, a siege aftermath and the replay overlay were seen in a preview only)
- [ ] The Shop page (lead, requests, "Who is buying", yesterday, shelf, Storage sheet), the Forge header with "Forge this", the Supplies sheet and the lazy Town (T2.8b, T2.8c, T3.7): merged; seen on the emulator only on an agent's own tree and only in part (the Supplies sheet, the compact Town rows and the "Fallen and retired" header were never seen)
- [ ] The dark forge look (both polish passes): pass 1 seen in part on the emulator (Shop, Forge, forge result card, half of a blade sheet); pass 2 (planning screens) built, not seen on a device
- [x] Portrait single workshop: top strip (day, gold, energy / debt; integrity and the next siege are on Home and Town), forge scene, panels, End Day with contextual sublabel
- [x] Forge flow: mode -> family -> core -> augment -> catalyst -> hints -> risk -> cost -> reveal -> item card -> List / Store
- [x] Market flow; Town flow (champions with equipment and condition, population, siege ETA and pressure in words)
- [x] Descriptive labels only, rarity by icon and text, reduced-motion toggle, portrait lock
- [x] Implemented in code: 48 dp minimum targets on controls, screen-reader descriptions on disabled actions
- [x] Inspected on the emulator: Town at font scale 1.3 and 1.5 wraps without clipping, the other panels at 1.0 and 1.3 (navigation labels are tight at 1.5)
- [ ] Accessibility validated on a real device: no accessibility suite, no completed 720x1280 or low-memory pass (review A05, A07; plan T6.1, T6.2). A width check exists (`LayoutMatrixTest`: Shop, Forge and the blade sheet at 360 and 320 dp, font 1.0 / 1.3 / 2.0); it passed once on an agent's tree before the polish and M4 screens changed those layouts and does not check heights. TalkBack was never run
- [x] Onboarding tip banners (per panel, once)
- [x] Settings sheet with reduced motion and a Haptics switch (T2.8a, T6.7: seen on the emulator; the switch persists across a force-stop)
- [ ] Haptic feel on a real motor (needs hardware); audio and audio settings (no sound asset exists)
- [ ] Tutorial beyond tips. The day-1 lead "Forge your first blade" is on the Shop (seen on the emulator by the smoke script); a coach line for the first customer (T2.10) is built on an agent branch, not merged, not seen on a device
- [ ] 720x1280 small-screen pass; low-memory interruption check
- [ ] Final UI design (owner will redesign)

## 13 Architecture and persistence
- [x] Pure JVM `:core` (rng, content, config, model, engine, crafting, market, heroes, battle, legacy, gazette, persistence, sim); `:app` observes
- [x] Typed, phase-checked commands and errors; invariants after every command
- [x] Versioned JSON envelope, Room atomic save (run + legacy + events in one transaction), DataStore for settings
- [x] Event-log compaction (30-day window, milestones kept), weapon-history cap, pruning of blades gone for good (30 days)
- [x] Four more bounds (T6.3a, `BalanceConfig.saveGrowth`, each 0 = off, each with a same-seed pair test): closed commissions leave 30 days after their deadline, the newest 30 End Day command IDs are kept, world events and arrivals are kept 30 days, a blade keeps its newest 24 everyday history lines (holders remembered in `Weapon.ownerIds`)
- [x] Migration scaffold with a checked-in v1 save fixture
- [x] Real migration steps and a save-compatibility policy: schema 4, rules 3 and content 3 are enforced on load, with checked-in fixtures for schemas 1 to 3 (review F09; T1.5a, T1.5b, T3.1, T3.2; JVM tests; an old-format day-1 save opened and accepted End Day on the emulator). Not run: an upgrade from the real 0.6.0 APK
- [x] A save that cannot be opened shows a recovery screen instead of crashing; a damaged row or file is kept, never deleted silently (review F02; T1.2 and the M1 review fixes: emulator). Not seen on a device: the "newer version" and "incompatible" screens and the "could not confirm" dialog
- [ ] Two interactions between save pruning and newer rules (a replacement request after a blade shattered more than 30 days ago; the "held the wall" line after ten later fights) are fixed on an agent branch (T6.3a follow-up), not merged

## 14 Pixel art
- [x] Import pipeline (concept sheets, weapon master sheet 336 sprites, artist pack), placeholders for the rest, 488 imported sprites on every screen (94 slices of AI-generated concept sheets, 336 from the AI-generated weapon master sheet, 58 script-drawn pack sprites; see ART_BRIEF, "Art sources and provenance")
- [x] Forge scene, furnace states, weapons by element row, class portraits, faction sprites, materials, blessings, siege diorama, milestone burst
- [ ] Signature weapon variants and newspaper / journal panel frames (unused pack sprites)
- [ ] Full custom art pass after the UI redesign

## 15 Testing and balance
- [x] 186 JVM tests recorded passing for 0.6.0 (PROGRESS, session 8): determinism, UI never draws RNG, ownership, bounds, idempotence, siege forecast, Gazette truth, legacy reset, content validation, save round-trip; the count says what exists, not coverage
- [x] 5,000-day arithmetic stress check (no overflow, invariants hold) with simulator trimming of history; it is not a memory or save-size bound for a production-shaped save
- [ ] Production-shaped soak (plan T6.3b). JVM half done: `./gradlew :core:soak` plays 2,000 forced-survival days for two smiths; End Day p95 1.7 to 3.9 ms over the first 1,000 days (budget 200 ms), save 3.7 to 4.7 MB at day 1,000 and 6.8 to 8.6 MB at day 2,000, not bounded. Device half (cold load, commit, frames, heap, memory pressure) pending
- [x] Headless simulator with the GDD policy set, upgrade impact, catalog sweeps, JSON report
- [x] Balance v2, v3 and v5 (with v4) reviewed at 10,000 seeds; first-era median 20 days at fair prices, 25-35 on the better paths, 35-45 with every upgrade, 0 hard-locks
- [x] Instrumented tests: Room save / restore, title, forge hint, End Day budget (6)
- [ ] Instrumented tests for the update: 14 classes exist. The last full run before the main-menu, M4 and polish merges passed 36 of 37 (`EndDayPerfTest` p95 232 ms under load; alone it passes at 29-89 ms). Never run since they were written or rewritten: `M4ScreensTest`, `WeaponStatCardTest`, `TitleScreenTest` (rewritten for the menu) and the edited `DetailSheetTest`. Device gate on the tip in progress, result to be recorded
- [x] 391 core and 111 app JVM tests pass on the tip of `shop-day/m0` (2026-10-10, reported by the controller); CI on GitHub was green on the branch at the main-menu commit
- [ ] Balance 8 reviewed at 10,000 seeds (T5.5: running on an agent branch, no result recorded). At 1,000 runs and three base seeds the plain smith's mean is 22.4-22.7 days against a band top of 22.5: open owner decision
- [x] Balance v4 (wear + fame) reviewed at 10,000 seeds as part of v5; affix magnitudes the v3 review flagged applied
- [x] Artifact recovery as a simulator metric (51-53 % of fallen heroes' blades come back at 1,000 seeds)
- [ ] Compose screenshot / accessibility tests
- [ ] Day-sim p95 < 200 ms on real mid-range hardware (21 ms on the emulator Android runtime, 1.7 ms on a desktop JVM, balance v5)

## 16-19 Release
- [x] Vertical-slice acceptance on device: forge -> sell -> hero fights -> Gazette -> siege -> defeat -> legacy -> upgraded next run
- [x] Every locked v1 system is reachable from some screen (a row, a line of text or a control)
- [x] Every locked v1 system is inspectable: hero and blade sheets show taste, purse, regular status, guild and mentor, a weapon's history and each affix's description (review U04-U06, G08; T2.7: seen on the emulator at font scale 1.0 and 2.0). Since restyled as an item card (polish pass 1, lower half seen on the emulator) and extended with a Story section and the hero's want line (not seen on a device). Not exercised on a device: List / Set price / Salvage from the sheet, the holder and mentor links
- [ ] Package rename from `com.example.blacksmithproject` (awaiting the application ID)
- [ ] Release signing, AAB
- [ ] Store listing (1.99 EUR, no ads / IAP), privacy and legal assets
- [ ] GDD 19 acceptance walk-through on a real device

## Pipeline (owner requests and review findings, beyond the GDD)
- [x] Signboard effect (+1 customer a day per level; +0.4 days, +3.5 sales a run for the active smith)
- [x] Affix magnitude sweep (Giant Slayer, Cursed / Bloodbound, Reinforced / Swift, Heavy)
- [x] Lucky affix: loot something scarce (a catalyst or tier 3+ material) instead of a common material
- [ ] Decide the wear margin (active smith leads the plain one by 5 days, was 10): condition floor 0.8 or less siege wear
- [x] Known Name: one starting regular with savings per level (+1.6 mean days; first siege held in 94 % of runs against 86 %)
- [ ] (no longer applies) Home: Yesterday block repeats the Shelf line on days without a lede. Home was replaced by the Shop page (T2.8b)
- [x] Weapons map pruning of terminal blades (salvaged, shattered, donated, collected) 30 days after they leave play
- [ ] Bounded save growth (review F10): closed commissions, processed command IDs, routine kept-forever records and everyday history lines are bounded (T6.3a). **Still growing, by decision:** unsold stock (nothing the player owns is deleted; storage tools are T6.3c), blades seized or lost with a hero (an event can bring any of them home), dead and retired heroes, and history-grade records (deaths, retirements, sieges, milestones). Measured (T6.3b, JVM, 2,000 days): the save grows 3.3 to 4.2 KB a day; stock is 2.0 to 2.7 KB of it, returnable blades 0.3 to 0.4, records kept for the run 0.3 (half of them `WEAPON_INHERITED`), hero records 0.13 to 0.15; without stock 0.86 to 0.91 KB a day, not flat. The four rules take about 5 % off a day-1,000 save. Storage filters and bulk actions (T6.3c) are built on an agent branch, not merged; clearing hundreds of blades in a few taps needs a core rule that does not exist (owner decision)
