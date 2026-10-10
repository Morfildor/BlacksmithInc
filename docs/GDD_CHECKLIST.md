# GDD checklist — built vs. left

One line per GDD feature, by GDD section. `[x]` = built and verified (evidence in PROGRESS.md / DECISIONS.md);
`[ ]` = open, with the missing piece named so ticking it means done. Tick items here when they ship.
IMPLEMENTATION_PLAN.md is the phase-gate view; this is the feature view. DEFERRED scope (GDD 16.2: multiplayer,
manual equip/combat, walking, idle production, online, accounts, achievements, ads, IAP) is not listed and is not built.

Last reviewed: 2026-10-09, app 0.6.0, balance config v5, 186 JVM tests (developer-run count, not an independent execution).
Wording corrections C01-C09 and C12 of the external review (docs/Tiny_Blacksmith_Thorough_Review_2026-10-09.md, section 9) were applied
by plan task T0.3: a ticked line states only what its evidence shows, and each narrowed claim has an open line next to it.

Reviewed again: 2026-10-10 (final state, about 13:00), for the major update: app 0.7.0, built on the integration branch
`shop-day/m0` (pushed as `major-update`), not released; `main` is still 0.6.0. Rules 3, save schema 4, balance config 8,
content 3; 393 core and 122 app JVM tests pass on the tip.
How a line is ticked in this review: a rule that lives in `:core` is ticked when its JVM tests pass, and the line says "JVM tests";
something the player sees is ticked when it was seen running on the emulator or when an automated device test that draws it
passes there, and the line says which. "Final gate" means the last device run, on commit 2eca650 (later commits are documents
only): 56 of 56 device tests, the smoke script, the run-to-defeat script and a main-menu walk-through, all on the Pixel_10_Pro
emulator at 1080x1920, density 420, font scale 1.0. A device test proves a screen draws and says the right thing at that one size;
it does not prove it looks right. No screenshots of the final build were taken (the owner tests by hand), nothing was run at font
scale 1.3 or 2.0 on the newest screens, TalkBack was never run, and nothing in this file was checked on a physical phone.
Evidence: `docs/MAJOR_UPDATE_LEDGER.md` (one row per task) and `docs/PROGRESS.md`.

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
- [x] Resume after the system closes the game: the day and its position over a real save file, the main menu staying closed, the open Storage sheet and its order (T6.4, `ShopDayPersistenceTest`, 3 device tests in the final gate; the smoke script resumes after a process kill on day 2). The forge draft, the forge result and the open destination are covered by JVM tests only (`SavedStateTest`, `LifecycleTest`). Not tested: "Select blades" mode in Storage after a restore

## 4 Forging and discovery
- [x] 6 families, 16 materials (6 core, 6 augment, 4 catalyst), data-driven with hidden affinities
- [x] Quick Forge (2 energy) and Advanced Forge (4 energy, catalyst, Temper / Quench / Etch techniques)
- [x] 10 energy, up to 4 overwork, next-day exhaustion
- [x] Safe / Balanced / Reckless rolls, GDD quality and power formulas, always a usable weapon, defects attach a flaw
- [x] Rarity tiers from quality plus earned fame and titles
- [x] 12 beneficial affixes and 6 flaws, each with a gameplay effect (bane, heal, loot, shatter, self-harm, ...)
- [x] 24 signature recipes (4 per family), transformation chance, discoverable through experiments and world clues
- [x] Journal UNKNOWN -> OBSERVED -> UNDERSTOOD -> SIGNATURE_DISCOVERED, live hints, no farming, survives death
- [x] Clue ladder (four clues a hidden recipe), rumours and recall (T4.3, JVM tests and simulator). The ladder and "Use this recipe" in Records: its device test passes in the final gate (emulator, 1080x1920, font 1.0); nobody looked at it at another size or font

## 5 Economy, shelves, commissions
- [x] 250 gold, 8 shelves (+2 with the Display Case), supplier with always-stocked basics; 0 hard-locks for the recorded simulator policies (no softlock for those bots, not shown for every player path)
- [ ] Adversarial recovery: resting, drought and stuck days measured separately, and shock scenarios (no gold and no stock, empty shelves, a lost siege) shown to recover (review G10; plan T5.2). Measured at balance 6 only (stuck streaks of three days or more in 0.00-0.10 % of novice runs; an over-spending bot at 1.9-3.0 %); at balance 8 and 10,000 runs the stuck-day measure holds (worst bot 0.07-0.09 % of days, the plain smith 0) and no novice run has a stuck streak of three days; the shock arms without a simulator option (prices at 70 % and 180 %, a material-poor start, half the town) were not run
- [x] List / unlist, manual price per weapon, suggested price, +/-10 buttons
- [x] GDD utility purchase (improvement, class fit, element taste, traits, loyalty, price), one purchase a visitor, reasons shown in words
- [x] Commissions: patron, family, min quality, element, reward, deadline; accept / decline; small expiry penalty
- [x] A commission asks for a named quality band, one rule decides delivery, the patron collects before the browsers and takes the least blade that fits (review F04, F06; T1.8, JVM tests). The request card was seen once on the emulator; the handed-over line was not
- [x] Requests have reasons (replacement, a blade for the wall, collector, first blade for a newcomer) and two can be open at once (T4.6, JVM tests and simulator). The "why" line on the card: its device test passes in the final gate (emulator, 1080x1920, font 1.0); nobody looked at it at another size or font
- [x] Fair customer selection: every living hero gets an equal turn at the counter (review F05; T3.1, simulator: served ratio by list position 0.39 to 0.97)
- [x] Standing wants, the sidegrade gate and siege demand (T4.1, T4.2; JVM tests and simulator). Their screens (wants in "Who is buying", the besieger line on the Shop and Forge plates): their device tests pass in the final gate (emulator, 1080x1920, font 1.0); nobody looked at them at another size or font
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
- [x] A returned legend is the blade it was (signature, flaws, catalyst, title, story), its properties dormant until honed once; at most one property in a new blade's name (T4.5, `ArtifactFidelityTest`, JVM). The Story section and the dormant marker on the blade sheet: their device test passes in the final gate (emulator, 1080x1920, font 1.0); nobody looked at them at another size or font
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
- [x] Main menu on launch: New game or Continue run, Settings, Abandon run (final gate, walk-through on the emulator: New game, Settings to Main menu and back, Continue run, a cold start showing the menu with Abandon, the confirmation returning to New game, the abandoned run still gone after a restart). Abandoning claims nothing but keeps journal discoveries made in that run; the dialog says so (open owner decision)
- [x] Four destinations (Shop, Forge, Town, Records) with a settings sheet (T2.8a: emulator at font scale 1.0, 1.3 and 2.0; at 2.0 the bottom bar clips "Records")
- [x] The shop day after End Day: customers at the counter card by card, receipts, the tally, aftermath and tomorrow's lead, with Next, Back, Skip day and 1x / 2x (T2.5, T2.6: emulator; a blessing day, a siege aftermath and the replay overlay were seen in a preview only)
- [x] The Shop page (lead, requests, "Who is buying", yesterday, shelf, Storage sheet), the Forge header with "Forge this", and the Town list (T2.8b, T2.8c, T3.7): their device tests and the smoke script pass in the final gate. Seen by a person only on an agent's earlier tree and only in part. Never seen by anyone: the Supplies sheet, the "Fallen and retired" header, a Town row with a guild or mentor line
- [x] Storage filters, four orders and multi-select Salvage / Arm the watch with one confirmation (T6.3c: JVM tests and device tests in the final gate). Not met: clearing 200 blades in under ten taps and a "never listed" filter, which need core rules that do not exist (owner decision). Bulk actions were not tried on a long save
- [ ] The dark forge look (both polish passes): the restyled screens pass their device tests in the final gate, which proves they draw, not how they look. Pass 1 was seen in part by a person on an earlier tree (Shop, Forge, forge result card, half of a blade sheet); pass 2 (planning screens) has never been looked at, and there is no screenshot of the final build
- [x] Portrait single workshop: top strip (day, gold, energy / debt; integrity and the next siege are on Home and Town), forge scene, panels, End Day with contextual sublabel
- [x] Forge flow: mode -> family -> core -> augment -> catalyst -> hints -> risk -> cost -> reveal -> item card -> List / Store
- [x] Market flow; Town flow (champions with equipment and condition, population, siege ETA and pressure in words)
- [x] Descriptive labels only, rarity by icon and text, reduced-motion toggle, portrait lock
- [x] Implemented in code: 48 dp minimum targets on controls, screen-reader descriptions on disabled actions
- [x] Inspected on the emulator: Town at font scale 1.3 and 1.5 wraps without clipping, the other panels at 1.0 and 1.3 (navigation labels are tight at 1.5)
- [ ] Accessibility validated on a real device: no accessibility suite, no completed 720x1280 or low-memory pass (review A05, A07; plan T6.1, T6.2). A width check exists and passes in the final gate (`LayoutMatrixTest`: Shop, Forge and the blade sheet at 360 and 320 dp, font 1.0 / 1.3 / 2.0); it checks that nothing reaches outside the screen width, not heights or clipping, and covers no other screen. TalkBack was never run
- [x] Onboarding tip banners (per panel, once)
- [x] Settings sheet with reduced motion and a Haptics switch (T2.8a, T6.7: seen on the emulator; the switch persists across a force-stop)
- [ ] Haptic feel on a real motor (needs hardware); audio and audio settings (no sound asset exists)
- [x] Onboarding (T2.10): day 1 opens on the lead "Forge your first blade" with its reason, and the first customer card shows one hint about the controls, once (JVM test, two device tests and the smoke script in the final gate). The hint was not looked at by a person; a fresh-install recording was not made
- [ ] Tutorial beyond that
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
- [x] Two interactions between save pruning and newer rules are fixed (a replacement request after a blade shattered more than 30 days ago; the "held the wall" line after ten later fights): f2f8845, 44d69a0, JVM tests
- [x] A run with no legacy record beside it reads the legacy the run carries, so a "Start over" cut short cannot leave an empty legacy (final review finding 1; 677876b, JVM test)
- [ ] Known issues left open from the final review (DECISIONS, "Known issues left open from the final review"): a bulk Storage action stops silently when one save fails; a save with a newer kind of record under the same schema number would read as "damaged"; the blade sheet's "Worn" word uses its own thresholds; three rules restated in the app; unguarded screen-model building. A database file cut to 0 bytes still opens as a new game with no message (not tried on a device)

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
- [x] Instrumented (device) tests for the update: 56 of 56 pass in the final gate on 2eca650, including `M4ScreensTest`, `WeaponStatCardTest`, the main-menu test, `ShopDayPersistenceTest`, the storage and layout tests and `EndDayPerfTest`; `smoke.sh` and `runend.sh` pass. History: the first gate on d946c4f read 45 of 50, five faults in the new tests themselves; one real screen fault followed and was fixed (the Storage sheet lost its order on restoration). Emulator only, one size, font 1.0
- [x] 393 core and 122 app JVM tests pass on the tip of `shop-day/m0` (2026-10-10, reported by the controller); the debug APK builds. CI on GitHub was green on the branch at the main-menu commit; no CI run or lint run is recorded for the tip
- [x] Balance 8 reviewed at 10,000 seeds at three base seeds, 990,000 runs (T5.5; DECISIONS, "Balance v8 at 10,000 seeds"): every run ends, longest 65 days, no tripwire crossed, a maxed account leads a new one by at least 10 mean days
- [ ] Both run-length bands hold at 10,000 seeds (the plan's M5 exit condition): NOT met. The plain smith's mean is 22.52 / 22.60 / 22.57 days against a band top of 22.5 at every seed, and nine other lines fail at all three seeds; no number was changed, all are open owner decisions. The per-upgrade gate table (T5.3) was not run
- [x] Balance v4 (wear + fame) reviewed at 10,000 seeds as part of v5; affix magnitudes the v3 review flagged applied
- [x] Artifact recovery as a simulator metric (51-53 % of fallen heroes' blades come back at 1,000 seeds)
- [ ] Compose screenshot / accessibility tests
- [ ] Day-sim p95 < 200 ms on real mid-range hardware (21 ms on the emulator Android runtime, 1.7 ms on a desktop JVM, balance v5)

## 16-19 Release
- [x] Vertical-slice acceptance on device: forge -> sell -> hero fights -> Gazette -> siege -> defeat -> legacy -> upgraded next run
- [x] Every locked v1 system is reachable from some screen (a row, a line of text or a control)
- [x] Every locked v1 system is inspectable: hero and blade sheets show taste, purse, regular status, guild and mentor, a weapon's history and each affix's description (review U04-U06, G08; T2.7: seen on the emulator at font scale 1.0 and 2.0). Since restyled as an item card (polish pass 1, lower half seen on the emulator) and extended with a Story section and the hero's want line (device tests pass in the final gate; not looked at by a person). Not exercised on a device: List / Set price / Salvage from the sheet, the holder and mentor links
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
- [ ] Bounded save growth (review F10): closed commissions, processed command IDs, routine kept-forever records and everyday history lines are bounded (T6.3a). **Still growing, by decision:** unsold stock (nothing the player owns is deleted; storage tools are T6.3c), blades seized or lost with a hero (an event can bring any of them home), dead and retired heroes, and history-grade records (deaths, retirements, sieges, milestones). Measured (T6.3b, JVM, 2,000 days): the save grows 3.3 to 4.2 KB a day; stock is 2.0 to 2.7 KB of it, returnable blades 0.3 to 0.4, records kept for the run 0.3 (half of them `WEAPON_INHERITED`), hero records 0.13 to 0.15; without stock 0.86 to 0.91 KB a day, not flat. The four rules take about 5 % off a day-1,000 save. Storage filters and bulk actions (T6.3c) are merged; clearing hundreds of blades in a few taps needs a core rule that does not exist (owner decision). The device half of the soak (loading and playing a 1,000-day save on a device) was not run
