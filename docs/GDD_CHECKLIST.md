# GDD checklist — built vs. left

One line per GDD feature, by GDD section. `[x]` = built and verified (evidence in PROGRESS.md / DECISIONS.md);
`[ ]` = open, with the missing piece named so ticking it means done. Tick items here when they ship.
IMPLEMENTATION_PLAN.md is the phase-gate view; this is the feature view. DEFERRED scope (GDD 16.2: multiplayer,
manual equip/combat, walking, idle production, online, accounts, achievements, ads, IAP) is not listed and is not built.

Last reviewed: 2026-10-09, app 0.5.0 plus unreleased work, balance config v5, 173 JVM tests.
In flight, not merged (session 8): legacy upgrade tracks.

## 3 Player loop
- [x] Run creation: seed, era, random faction pressures and world modifiers, 100 integrity, hero pool, legacy unlocks
- [x] Planning phase: yesterday's paper, shop, forecast, heroes, inventory, supplier, commissions; nothing advances without End Day
- [x] End Day in the fixed GDD 3.2 order, idempotent per command ID, saved atomically before the report
- [x] Run end at integrity 0: summary, claim-once legacy, next era
- [x] Home dashboard (owner request, beyond the GDD)
- [x] Day report shown again after process death until it is dismissed

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
- [x] 250 gold, 8 shelves (+2 with the Display Case), supplier with always-stocked basics, no softlock (0 hard-locks in the simulator)
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
- [x] Three dynamic champions, 0-2 handled
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
- [x] 8 upgrade tracks at 8 / 20 / 45: energy, gold, mastery, walls, efficiency, stock, luck, reputation
- [x] Persistence matrix: journal, signatures, upgrades, Legend Board, lineages, unspent points persist; the rest resets
- [ ] Upgrade tracks for catalog access, recipe odds and legacy-artifact opportunities
- [ ] Starting gold / energy upgrades measure about 0 days in the simulator (better metric or a lever)

## 10 Content
- [x] 16 materials, 6 families, 5 classes, 3 factions, 25 events (23 pooled + 2 generational rules), 24 signatures, 12 affixes, 6 flaws, 8 blessings
- [x] Events with eligibility, weight, effects, story template, repetition limits; artifact / lineage events ineligible without one
- [x] ContentCatalog.validate() over every ID

## 11 Gazette and replays
- [x] Daily paper from real event records, priority order, 3-5 headlines, archive
- [x] Edition layout: lede, tally, Shop / Heroes / Town / Forge
- [x] Siege replay: stepped text and an animated diorama fed by the CombatReplay only; skippable; reduced motion honoured
- [x] Replays for significant expeditions: elite fights and deaths, folded text in the day report, at most three a day

## 12 Workshop UX and accessibility
- [x] Portrait single workshop: top strip (day, gold, energy / debt, integrity, next siege), forge scene, panels, End Day with contextual sublabel
- [x] Forge flow: mode -> family -> core -> augment -> catalyst -> hints -> risk -> cost -> reveal -> item card -> List / Store
- [x] Market flow; Town flow (champions with equipment and condition, population, siege ETA and pressure in words)
- [x] Descriptive labels only, rarity by icon and text, 48 dp targets, font scale 1.5 pass, screen-reader descriptions, reduced-motion toggle, portrait lock, resume after process death
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
- [x] Migration scaffold with a checked-in v1 save fixture
- [ ] First real migration step (schema still 1); RULES_VERSION bump (still 1 after rule changes)

## 14 Pixel art
- [x] Import pipeline (concept sheets, weapon master sheet 336 sprites, artist pack), placeholders for the rest, 488 hand-made sprites on every screen
- [x] Forge scene, furnace states, weapons by element row, class portraits, faction sprites, materials, blessings, siege diorama, milestone burst
- [ ] Signature weapon variants and newspaper / journal panel frames (unused pack sprites)
- [ ] Full custom art pass after the UI redesign

## 15 Testing and balance
- [x] 173 JVM tests: determinism, UI never draws RNG, ownership, bounds, idempotence, siege forecast, Gazette truth, legacy reset, content validation, save round-trip, 5,000-day soak
- [x] Headless simulator with the GDD policy set, upgrade impact, catalog sweeps, JSON report
- [x] Balance v2 and v3 reviewed at 10,000 seeds; first-era median 20-25 days, longer paths with upgrades, 0 hard-locks
- [x] Instrumented tests: Room save / restore, title, forge hint, End Day budget (6)
- [ ] Balance v4 (wear + fame) reviewed at 10,000 seeds; affix magnitudes the v3 review flagged
- [x] Artifact recovery as a simulator metric (51-53 % of fallen heroes' blades come back at 1,000 seeds)
- [ ] Compose screenshot / accessibility tests
- [ ] Day-sim p95 < 200 ms on real mid-range hardware (8.5 ms on the emulator Android runtime, 0.99 ms on a desktop JVM)

## 16-19 Release
- [x] Vertical-slice acceptance on device: forge -> sell -> hero fights -> Gazette -> siege -> defeat -> legacy -> upgraded next run
- [x] Every locked v1 system has a UI surface
- [ ] Package rename from `com.example.blacksmithproject` (awaiting the application ID)
- [ ] Release signing, AAB
- [ ] Store listing (1.99 EUR, no ads / IAP), privacy and legal assets
- [ ] GDD 19 acceptance walk-through on a real device

## Pipeline (owner requests and review findings, beyond the GDD)
- [x] Signboard effect (+1 customer a day per level; +0.4 days, +3.5 sales a run for the active smith)
- [x] Affix magnitude sweep (Giant Slayer, Cursed / Bloodbound, Reinforced / Swift, Heavy)
- [x] Lucky affix: loot something scarce (a catalyst or tier 3+ material) instead of a common material
- [ ] Decide the wear margin (active smith leads the plain one by 5 days, was 10): condition floor 0.8 or less siege wear
- [ ] Known Name upgrade measures 0 or less impact
- [ ] Home: Yesterday block repeats the Shelf line on days without a lede
- [x] Weapons map pruning for 1,000-day saves (unsold storage stock still grows)
