# Tiny Blacksmith — project guide for Claude Code

Premium (€1.99, offline, no ads/IAP/accounts) portrait Android game: a menu-only pixel-art blacksmith shop whose
weapons are bought by autonomous heroes who fight, defend the town and die. Source of truth for design:
`Tiny_Blacksmith_GDD_v1.0.md` (read it; do not copy it here). LOCKED = requirement, PROPOSED = tunable, DEFERRED = do not build.

## Non-negotiables (LOCKED in the GDD)
- Player never moves a character, commands combat or equips heroes. Only shop actions: forge, price, list, commissions, End Day.
- 10 daily energy; overwork causes next-day exhaustion. Every valid forge yields a usable (possibly flawed) weapon.
- Predictable major siege timing; living faction pressure decides strength. Forge integrity 0 = run over.
- Three dynamic champions = strongest *available* heroes (gracefully 0–2). Dead heroes never act or own equipment.
- Strong permanent legacy (knowledge, upgrades, legends, lineages); run-only state resets. Claim once per run.
- Gazette and replays derive from real event records; animations never consume gameplay RNG.
- No backend, telemetry, ads, billing SDK, runtime AI, timers or idle production.

## Architecture boundaries
- `:core` — pure Kotlin JVM, zero Android deps. Deterministic SplitMix64 streams per subsystem (`rng/`), content
  by stable IDs (`content/`), gameplay numbers in `config/BalanceConfig.kt` (versioned; new ones go into its nested
  groups, the constructor is at the JVM limit of 255 slots). The six resolvers (`Heroes`, `Market`, `Battle`, `Power`,
  `WorldEvents`, `Legacy`) hold none of their own: `ConstantsTest` scans them against a short allowlist (wording bands,
  record sizes, the 0..100 scales). Catalog numbers and the world-event table (weights, limits, cooldowns) are content;
  `GameEngine` (starting militia, the three season multipliers) and `Forge` (affix slots per rarity) still hold a few. Immutable `GameState`
  (`model/`), typed commands and errors (`engine/Commands.kt`), the only mutator `engine/GameEngine.kt`
  (End Day = fixed GDD 3.2 order, idempotent per command ID), invariants asserted after each command.
  Morning visitors (`engine/Encounters.kt`, what each is in `EncounterCatalog.kt`), what they set in motion
  (`Consequences.kt`), workshop relics (`Relics.kt`) and siege traits (`Battle.scheduleNext` / `outlook`) draw only on the
  ENCOUNTERS stream; their definitions are content (`content/Depth.kt`), their numbers `BalanceConfig.depth`.
- `:app` — Compose UI is an observer: `GameViewModel` sends every operation (commands, legacy purchases, claim, Begin
  era, abandon, the shop-day position) through `GameSession`, the one serialized boundary, which saves accepted results
  atomically (Room, `data/SaveStore.kt` behind `data/GameRepository.kt`) before the screen renders them; load failures
  open a recovery screen (`ui/Failures.kt`). DataStore holds only settings. Never compute outcomes in UI.
  Screens: main menu (`ui/App.kt`), then four destinations (Shop, Forge, Town, Records) in `ui/WorkshopScreen.kt` with
  Storage, Supplies, Settings and hero / blade sheets (`ui/detail/`); after End Day the saved day is shown card by card
  (`ui/shopday/`). There is no Home or Market panel any more. The Forge is a workbench built from the pure model in
  `ui/ForgeUi.kt`; commissions and customer wants are one board (`ui/CommissionBoard.kt`) opened from Shop and Forge; the
  Journal is a notebook (`ui/Notebook.kt`).
- Save format: versioned JSON envelope from `core/persistence/SaveCodec.kt` (schema 5; rules 4 and content 4 are
  enforced on load by `engine/Compatibility.kt`); migrations go there.
- Content names are PROPOSED; counts are LOCKED. Vertical slice content lives in `content/SliceContent.kt`.
- Pixel art has several sources, never hand-edited PNGs, all imported by `tools/pixelart/import_assets.py` into
  `tools/pixelart/overrides.json`: concept sheets in `Pixel art assets/` (sliced by cell layout, rich 64 px icons
  and the forge scene), the weapon master sheet (`Weapons master`, 6 families × 7 element rows × 8 levels, sliced into
  336 sprites and the generated `ui/WeaponArt.kt` lookup), loose `<id>.png` files, the owner's hero portrait set
  (`Assets/Implemented/heroes`, 20 heroes with a base and an upgraded face, `--heroes PATH`; the folder is committed and the step is
  skipped without it), and the artist's 1x production pack (a subfolder with
  `drawable-nodpi/` + `manifest.json`), from which only battle frames, siege wall, milestone burst and hero markers
  are taken by default (`--pack-all` takes everything). `generate_assets.py` draws placeholders only for IDs without
  imported art. The brief for new art
  is `docs/ART_BRIEF.md`. `ui/Sprites.kt` maps content IDs to sprites (nearest-neighbour when enlarging, bilinear
  when shrinking imported art); sprites stay decorative (no essential text, no gameplay reads).

## Commands
```
./gradlew :core:test                                   # JVM tests (determinism, bounds, idempotence, e2e)
./gradlew :core:simulate --args="--runs 1000 --seed 1"  # headless balance harness (add --policy X[,Y]|all|gdd|bots|every (`all` = the 14 classic policies, `bots` = the T0.7 bots), --blessing first|energy|quality|sales|patronage|defense, --eras N --buy cheapest|walls|track=ID for several eras on one account, --impactPolicy X, --reserve N, --content launch|slice, --rarityTable N, --siegeModifier, --forgeDamageBase/Slope, --recoveryCap, --customers for the customer/identity metrics, --set key=value[,key=value] for allowlisted BalanceConfig overrides, --depth for the visitor / relic / siege-trait table, --encounters decline|first|cash|defense|adaptive and --relic first|adaptive|none|<id> to override how bots answer, --noDepth for the rules-3 baseline without them, --probe N for the exploit probes)
#   catalog sweeps: --noTool id[,id], --toolCost id=mult[,id=mult], --noAffixEffect id[,id]|all (keeps the affix, neutralises its v3 effect); --noFates turns the v5 weapon fates off (v4 odds, no guild heir, no merchant); --noImpact skips the maxed-legacy and per-upgrade runs
#   legacy: --upgrades id=level[,id=level] plays the policy rows on that account, --yardsticks adds the first-siege and premium-sale table, --legends gives the maxed and impact runs a veteran Legend Board, --knownNameGold N
./gradlew :core:scenarios                              # rewrites the debug scenario saves in app/src/debug/assets/scenarios (played and constructed; run after a rules, content or balance change)
./gradlew :core:soak                                   # long-save soak, outside the default suite (about 70 s): 2,000 forced-survival days for two smiths; tables and the day-1,000 / 2,000 saves in core/build/soak/
./gradlew :app:testDebugUnitTest                       # app JVM tests (session, ViewModel, screen models; no device)
./gradlew :app:assembleDebug                           # APK (needs Android SDK at local.properties sdk.dir)
./gradlew :app:installDebug                            # install on connected device/emulator
./gradlew :app:connectedDebugAndroidTest               # instrumented tests (emulator required)
python tools/pixelart/import_assets.py                 # slice the source sheets from 'Pixel art assets/' into drawables (Pillow, numpy)
python tools/pixelart/generate_assets.py               # placeholders for IDs without imported art + manifest
ADB=<sdk>/platform-tools/adb bash tools/emulator/smoke.sh <dir>  # scripted device loop + screenshots (after installDebug)
ADB=<sdk>/platform-tools/adb bash tools/emulator/scenarios.sh <dir> # loads every debug scenario save and checks what its title promises (about an hour on a slow emulator)
ADB=<sdk>/platform-tools/adb bash tools/emulator/runend.sh <dir> # passive run to defeat, then claim + next era (about 2 min)
./gradlew :core:scenarios                              # rewrites the debug build's scenario saves (app/src/debug/assets/scenarios) from core test ScenarioSaves.kt
ADB=<sdk>/platform-tools/adb bash tools/emulator/scenarios.sh <dir> # loads each scenario through the debug menu and screenshots its card (set ANDROID_SERIAL when several devices are attached)
```
Toolchain: Gradle 9.5, AGP 9.3.3 (built-in Kotlin), Kotlin plugins 2.2.21 (compose/jvm/serialization), KSP 2.3.12,
Compose BOM 2026.02.01, Room 2.8.5, DataStore 1.2.1, JDK 21 launcher / JDK 25 daemon toolchain.

## Working rules
- Surgical edits; keep docs in `docs/` current: IMPLEMENTATION_PLAN (phase gates), DECISIONS (locked vs proposed,
  tuning evidence), PROGRESS (state, checks run, next actions), GDD_CHECKLIST (feature view: tick an item when it
  ships and is verified). Update PROGRESS before ending a session. While the major update is open, per-task status
  and evidence live in `docs/MAJOR_UPDATE_LEDGER.md` (plan: `docs/MAJOR_UPDATE_PLAN.md`). Visitors, relics and siege
  traits: `docs/GAMEPLAY_DEPTH_PLAN.md` (tables of every option and number, task ledger).
- Balance changes: run the simulator, record numbers in DECISIONS.md, bump `BalanceConfig.version` on semantic change.
- New gameplay numbers go in `BalanceConfig`, never inline. New content goes through `ContentCatalog.validate()`.
- Tests must pass before claiming a phase done; do not commit/push without being asked.
- Every user-visible change gets a line under `[Unreleased]` in `CHANGELOG.md`; on a version bump, rename that section
  to the version and date and raise `versionName` (SemVer `0.y.z` while in early development) in `app/build.gradle.kts`.
