# GDD checklist — built vs. left

One line per GDD feature, by GDD section. `[x]` = built and verified (evidence in PROGRESS.md / DECISIONS.md);
`[ ]` = open, with the missing piece named so ticking it means done. Tick items here when they ship.
IMPLEMENTATION_PLAN.md is the phase-gate view; this is the feature view. DEFERRED scope (GDD 16.2: multiplayer,
manual equip/combat, walking, idle production, online, accounts, achievements, ads, IAP) is not listed and is not built.

Last reviewed: 2026-10-09, app 0.6.0, balance config v5, 186 JVM tests (developer-run count, not an independent execution).
Wording corrections C01-C09 and C12 of the external review (docs/Tiny_Blacksmith_Thorough_Review_2026-10-09.md, section 9) were applied
by plan task T0.3: a ticked line states only what its evidence shows, and each narrowed claim has an open line next to it.

## 3 Player loop
- [x] Run creation: seed, era, random faction pressures and world modifiers, 100 integrity, hero pool, legacy unlocks
- [x] Planning phase: yesterday's paper, shop, forecast, heroes, inventory, supplier, commissions; nothing advances without End Day
- [x] End Day in the fixed GDD 3.2 order, idempotent per command ID; each accepted command through the ViewModel dispatch is saved atomically before the report
- [ ] Every authoritative operation (End Day, legacy purchase, claim, Begin era) serialized, so a run transition cannot race a stale write; today only dispatch has a busy guard and the legacy screen reads the UI snapshot (review F01; plan T1.1, T1.3)
- [x] Run end at integrity 0: summary, claim-once legacy, next era
- [x] Home dashboard (owner request, beyond the GDD)
- [x] Day report shown again after process death until it is dismissed
- [x] Resume on launch restores the committed world (the saved run) and the unread day report
- [ ] Resume after process death for the forge draft, the reveal and the open panel (none is restored); the report's dismissal marker is written asynchronously, so an interrupted dismissal can reopen a closed report (a repeated presentation, never a second resolution); the shop-day cursor does not exist yet (plan T1.3, T2.3)

## 4 Forging and discovery
- [x] 6 families, 16 materials (6 core, 6 augment, 4 catalyst), data-driven with hidden affinities
- [x] Quick Forge (2 energy) and Advanced Forge (4 energy, catalyst, Temper / Quench / Etch techniques)
- [x] 10 energy, up to 4 overwork, next-day exhaustion
- [x] Safe / Balanced / Reckless rolls, GDD quality and power formulas, always a usable weapon, defects attach a flaw
- [x] Rarity tiers from quality plus earned fame and titles
- [x] 12 beneficial affixes and 6 flaws, each with a gameplay effect (bane, heal, loot, shatter, self-harm, ...)
- [x] 24 signature recipes (4 per family), transformation chance, discoverable through experiments and world clues
- [x] Journal UNKNOWN -> OBSERVED -> UNDERSTOOD -> SIGNATURE_DISCOVERED, live hints, no farming, survives death

## 5 Economy, shelves, commissions
- [x] 250 gold, 8 shelves (+2 with the Display Case), supplier with always-stocked basics; 0 hard-locks for the recorded simulator policies (no softlock for those bots, not shown for every player path)
- [ ] Adversarial recovery: resting, drought and stuck days measured separately, and shock scenarios (no gold and no stock, empty shelves, a lost siege) shown to recover (review G10; plan T5.2)
- [x] List / unlist, manual price per weapon, suggested price, +/-10 buttons
- [x] GDD utility purchase (improvement, class fit, element taste, traits, loyalty, price), one purchase a visitor, reasons shown in words
- [x] Commissions: patron, family, min quality, element, reward, deadline; accept / decline; small expiry penalty
- [x] Reputation (sales, commissions, hero success) and per-hero loyalty; both reset per run except the Known Name perk
- [x] Shop actions beyond the GDD: Salvage, Hone, Arm the watch, trade-ins, four workshop tools
- [x] Signboard tool: +1 customer a day per level

## 6 Heroes, champions, generations
- [x] 5 classes, 9 traits each wired to a decision weight, element taste, wealth, ambition, deterministic names and portraits
- [x] Daily choice rest / shop / expedition / patrol / defend by trait-weighted seeded utility; wounded rest, dead never act
- [x] GDD effective-power formula; a suited weapon measurably helps
- [x] Three dynamic champions chosen as the strongest available heroes (health gate), 0-2 handled
- [ ] Champion ranking in a warlord siege uses the same foe context as the outlook; today the ranking assumes a plain foe and feeds `town.championIds` (review F08; plan T1.7, `ChampionSelectionTest`)
- [x] Level-ups, fame, retirement, guild founding, mentoring, newcomers when the population thins
- [x] Lineage anchors across eras; descendants reference stored ancestors
- [x] Guild hall (training, lessons from a higher-level guildmate, founding and joining) and ambition as scored daily activities; money and yesterday as inputs

## 7 Living weapons
- [x] Lifecycle with one authoritative location, history entries, titles from real records
- [x] On death: recovered by the shop, seized by the enemy, or lost; on retirement: inherited by the mentee
- [x] Bounded Legend Board; a famous blade returns dormant (reduced quality) in a later era, keeping its fame
- [x] Fame as a capped mechanical effect (power, desire, price); wear and condition
- [x] Fates of a fallen hero's blade by where the hero fell: guild inheritance, merchant resale (fame raises the chance), seizure, loss

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
- [ ] Paper complete and consistent: the report, the shop and the archive show the same day, and the tally accounts for every gold and hero event (today the archived day misses forge records and the tally counts warlord tribute as takings; review F03, F07; plan T1.4, T1.6)
- [x] Edition layout: lede, tally, Shop / Heroes / Town / Forge
- [x] Siege replay: stepped text and an animated diorama fed by the CombatReplay only; skippable; reduced motion honoured
- [x] Replays for significant expeditions: elite fights and deaths, folded text in the day report, at most three a day

## 12 Workshop UX and accessibility
- [x] Portrait single workshop: top strip (day, gold, energy / debt; integrity and the next siege are on Home and Town), forge scene, panels, End Day with contextual sublabel
- [x] Forge flow: mode -> family -> core -> augment -> catalyst -> hints -> risk -> cost -> reveal -> item card -> List / Store
- [x] Market flow; Town flow (champions with equipment and condition, population, siege ETA and pressure in words)
- [x] Descriptive labels only, rarity by icon and text, reduced-motion toggle, portrait lock
- [x] Implemented in code: 48 dp minimum targets on controls, screen-reader descriptions on disabled actions
- [x] Inspected on the emulator: Town at font scale 1.3 and 1.5 wraps without clipping, the other panels at 1.0 and 1.3 (navigation labels are tight at 1.5)
- [ ] Accessibility validated on a real device: no automated screenshot or accessibility suite, no completed 720x1280 or low-memory pass, fixed-width controls not audited (review A05, A07; plan T6.1, T6.2)
- [x] Onboarding tip banners (per panel, once)
- [ ] Settings panel (the reduced-motion switch lives in Legacy); audio and haptics (none)
- [ ] Tutorial beyond tips; Home first-run tip
- [ ] 720x1280 small-screen pass; low-memory interruption check
- [ ] Final UI design (owner will redesign)

## 13 Architecture and persistence
- [x] Pure JVM `:core` (rng, content, config, model, engine, crafting, market, heroes, battle, legacy, gazette, persistence, sim); `:app` observes
- [x] Typed, phase-checked commands and errors; invariants after every command
- [x] Versioned JSON envelope, Room atomic save (run + legacy + events in one transaction), DataStore for settings
- [x] Event-log compaction (30-day window, milestones kept), weapon-history cap, pruning of blades gone for good (30 days)
- [x] Four more bounds (T6.3a, `BalanceConfig.saveGrowth`, each 0 = off, each with a same-seed pair test): closed commissions leave 30 days after their deadline, the newest 30 End Day command IDs are kept, world events and arrivals are kept 30 days, a blade keeps its newest 24 everyday history lines (holders remembered in `Weapon.ownerIds`)
- [x] Migration scaffold with a checked-in v1 save fixture
- [ ] First real migration step (schema still 1); a save-compatibility policy and with it RULES_VERSION (still 1; it seeds the run RNG, so it does not move alone)

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
- [x] Balance v4 (wear + fame) reviewed at 10,000 seeds as part of v5; affix magnitudes the v3 review flagged applied
- [x] Artifact recovery as a simulator metric (51-53 % of fallen heroes' blades come back at 1,000 seeds)
- [ ] Compose screenshot / accessibility tests
- [ ] Day-sim p95 < 200 ms on real mid-range hardware (21 ms on the emulator Android runtime, 1.7 ms on a desktop JVM, balance v5)

## 16-19 Release
- [x] Vertical-slice acceptance on device: forge -> sell -> hero fights -> Gazette -> siege -> defeat -> legacy -> upgraded next run
- [x] Every locked v1 system is reachable from some screen (a row, a line of text or a control)
- [ ] Every locked v1 system is inspectable: hero taste, loyalty, guild and mentor, a weapon's history and an affix's description each have a readable surface (today the hero row shows none of the first four, nothing reads `Weapon.history`, affix text appears only in the forge reveal; review U04-U06, G08; plan T2.7)
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
- [ ] Home: Yesterday block repeats the Shelf line on days without a lede
- [x] Weapons map pruning of terminal blades (salvaged, shattered, donated, collected) 30 days after they leave play
- [ ] Bounded save growth (review F10): closed commissions, processed command IDs, routine kept-forever records and everyday history lines are bounded (T6.3a). **Still growing, by decision:** unsold stock (nothing the player owns is deleted; storage tools are T6.3c), blades seized or lost with a hero (an event can bring any of them home), dead and retired heroes, and history-grade records (deaths, retirements, sieges, milestones). Measured (T6.3b, JVM, 2,000 days): the save grows 3.3 to 4.2 KB a day; stock is 2.0 to 2.7 KB of it, returnable blades 0.3 to 0.4, records kept for the run 0.3 (half of them `WEAPON_INHERITED`), hero records 0.13 to 0.15; without stock 0.86 to 0.91 KB a day, not flat. The four rules take about 5 % off a day-1,000 save
