# Implementation plan — phase gates (GDD §17)

Legend: [x] done and verified · [~] partial · [ ] not started. Verification evidence lives in PROGRESS.md.

## P0 — Project, schema, determinism, tests
- [x] `:core` pure Kotlin JVM module; `:app` Android Compose module; version catalog with verified versions
- [x] SplitMix64 PRNG with per-subsystem streams, serializable `RngState`, reference-vector test
- [x] Typed stable IDs; `ContentCatalog.validate()`; `BalanceConfig` (versioned)
- [x] Immutable `GameState`; typed `Command`/`GameError`/`CommandOutcome`; invariants asserted per command
- [x] Save architecture: JSON envelope codec + `SaveRepository` abstraction + in-memory impl
- [x] JVM test setup (kotlin-test/JUnit4), headless simulator entry point (`:core:simulate`)

## P1 — Crafting
- [x] 10 energy, Quick Forge 2, Advanced Forge 4 (+ optional catalyst), overwork ≤4 → next-day exhaustion
- [x] Safe/Balanced/Reckless exceptional/defect rolls (clamped), GDD quality and power formulas
- [x] Always-usable results; defects attach a flaw affix; rarity tiers from quality ranges
- [x] Hidden core↔augment and augment↔family affinities; Experiment Journal UNKNOWN→OBSERVED→UNDERSTOOD, no farming
- [x] Forge mastery upgrade and Forgefire blessing feed `masteryBonus`
- [ ] Signature recipes / transformations, SIGNATURE_DISCOVERED stage, world clues (P6)

## P2 — Market and hero autonomy
- [x] 2 classes (Guardian, Ranger), 7 traits each wired to a decision weight, element tastes, wealth
- [x] Manual pricing, list/unlist, 8 shelf slots, supplier with always-stocked basics and limited rares
- [x] GDD utility purchase algorithm with reason codes (great fit / overpriced / not suited / too expensive / not better)
- [x] Equip-if-better ownership transfer; single authoritative weapon location
- [x] Commissions: offered → accept/decline → delivered at End Day → reputation/loyalty; small expiry penalty
- [ ] Shop reputation effects on willingness to pay; loyalty-driven repeat customers beyond visit chance (P6)

## P3 — Town and battles
- [x] One faction (Ashclaw Raiders) with pressure, growth, suppression, weakness/resistance, encampment event
- [x] Hero activities (rest/expedition/patrol) by trait-weighted seeded choice; wounded heroes rest
- [x] Deterministic expeditions with GDD effective-power formula; weapon kills/victories/fame/titles
- [x] Death → weapon recovered or lost; newcomers arrive when population thins
- [x] Sieges on days 5/10/15…, warning events 1–2 days ahead, 3 strongest available champions (0–2 handled)
- [x] GDD raid/forge-damage formulas, hero-driven bounded recovery, militia from patrols, defeat at integrity 0
- [x] Event records → Gazette headlines; read-only `CombatReplay` DTO per siege
- [ ] Remaining two factions, encounter decks, elites, 25 scripted events (P6)

## P4 — Roguelite and legacy
- [x] Legacy points = 5 + days/5 + discoveries (cap) + milestone table; claim-once by run ID
- [x] 4 permanent upgrades with tiers (8/20/45); run-only state reset; journal merged across runs
- [x] Weapon history entries, Legend Board (bounded), lineage anchor → descendant hero next era
- [x] Blessing offer (3 of 5) after a survived siege, temporary effects with expiry
- [ ] Guilds, retirement, mentoring, artifact return events, full blessing catalog (P6)

## P5 — Compose vertical slice
- [x] Portrait single workshop: top strip, placeholder forge Canvas, panels (Forge/Market/Town/Journal/Gazette/Legacy), End Day
- [x] Forge flow with journal hints, descriptive risk/quality labels, result card with List/Store
- [x] Market pricing text fields, customers' reasons, commissions, supplier
- [x] Day report dialog (Gazette + replay text), blessing choice, run-end screen with claim-once and upgrades
- [x] Room atomic save (run + legacy in one transaction), DataStore reduced-motion setting, resume on launch
- [x] Verified on the Pixel 10 Pro AVD (API 37): scripted loop new run → forge → list → End Day → Gazette sale/battle → resume after process death; 3 instrumented tests pass

## P6 — Launch content, art, replays
- [~] Launch catalog (`content/LaunchContent.kt`): 6 families, 16 materials, 5 classes, 3 factions, 12 affixes, 6 flaws, 8 blessings, 8 upgrades — validated and simulated, **not yet the engine default** (quality formula saturates at tier 6; P7 retune)
- [x] 25 scripted world events: 23 in a weighted pool (incl. Strange Weapon Fragment, a signature clue) + 2 deterministic rules
- [x] 24 signature recipes (4 per launch family; spear/dagger/staff reachable once launch content is the default); SIGNATURE_DISCOVERED journal stage, clues, hints
- [x] Advanced Forge techniques (Temper/Quench/Etch) in engine and Forge panel
- [x] Guild/retirement/mentoring, weapon seizure and inheritance, famous-blade return from the Legend Board
- [ ] Reputation/loyalty depth (willingness to pay, repeat customers)
- [x] Pixel-art pipeline: hand-made sheets imported by `tools/pixelart/import_assets.py` (136 sprites: scene, 36 weapons, 6 overlays, 6 badges, 25 portraits, 12 faction sprites, 16 materials, 8 blessings, nav/status icons), placeholders generated for the rest; wired into every screen
- [x] Battle replay: stepped Gazette text plus an animated siege diorama (hero/monster frames from the artist pack) consuming `CombatReplay` only; Skip and reduced-motion honoured
- [x] Forge palette theme, onboarding tips, font-scale 1.5 pass, 8-slot shelf grid, ±10 price buttons, level dots, run-end claim flow
- [ ] Rename package from `com.example.blacksmithproject` to the release application ID

## P7 — Balance, reliability, onboarding, accessibility
- [ ] ≥10,000-seed balance reviews per policy; target early median 15–25 days with viable longer paths
- [ ] Room migration tests, instrumented save/restore tests, Compose screenshot/a11y tests
- [ ] Onboarding, font scaling, small screens, low-memory interruption checks, soak test without overflow

## P8 — Google Play premium launch
- [ ] Release signing, AAB, store listing (€1.99, no ads/IAP), privacy/legal assets
