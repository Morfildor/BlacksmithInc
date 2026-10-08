# Decisions register

Tags: **LOCKED** (GDD requirement, implemented as stated) · **PROPOSED** (GDD starting value, configurable) ·
**ENGINEERING** (reversible technical choice) · **SLICE** (deliberate vertical-slice reduction, restored by P6).

## Technical (ENGINEERING)
- Two Gradle modules (`:core`, `:app`) instead of the GDD's twelve; package boundaries inside `:core` mirror the
  proposed modules (`crafting`, `market`, `heroes`, `battle`, `legacy`, `gazette`, `persistence`, `content`).
- AGP 9.3.3 built-in Kotlin + KSP 2.3.12 (standalone versioning, Kotlin-independent). KSP 2.2.x refuses built-in
  Kotlin, and opting out (`android.builtInKotlin=false` + KGP 2.2.21) crashes on AGP 9.3.3's removed `BaseExtension`,
  so neither older path works; KSP ≥ 2.3 is required with AGP 9.
- Saves are a versioned JSON envelope (kotlinx.serialization) stored as one Room row for the run and one for the
  legacy profile, written in a single `@Transaction`. Event log is embedded in the run and compacted at End Day
  (see "Event-log compaction" below).
- `GameEngine` is a class holding `ContentCatalog` + `BalanceConfig`; `ResolutionContext` is a mutable scratch
  copy used only inside one command, so the public boundary stays immutable.
- End Day idempotence: command ID = `"<runId>:day<N>"`; processed IDs and the last `DayResolution` are stored in
  the state, so a retry returns the stored result without simulation.
- Policy randomness in the simulator uses a separate `Rng` seeded from the run seed, never gameplay streams.
- The legacy profile is embedded in `GameState` during a run (journal writes happen on forge); on claim the run's
  journal is merged into the stored profile by max knowledge state.
- Pixel art is produced by `tools/pixelart/generate_assets.py` (Pillow): palette-indexed pixel maps plus procedural
  scene pieces, one palette, upper-left light, 1px outlines, nearest-neighbour scaling, `drawable-nodpi`. Hand-drawn
  replacements can be dropped in with the same IDs; the manifest (`app/src/main/assets/art/manifest.json`,
  `docs/ART_MANIFEST.md`) is the contract.
- androidx.test bumped to ext-junit 1.3.0 / espresso 3.7.0: the template's 1.1.5 / 3.5.1 crashed the
  instrumentation process on API 37 before any test ran.
- Kept the Android Studio template package name `com.example.blacksmithproject` and theme files to avoid churn;
  renaming is a P8 release task.

## LOCKED, implemented as stated
- 10 daily energy; overwork → next-day exhaustion; no immediate forging penalty.
- Every valid forge yields a usable weapon; materials/energy consumed exactly once; no retry by interruption.
- Quick and Advanced modes; Safe/Balanced/Reckless chosen before forging.
- Manual per-item pricing; autonomous purchases; commissions accepted/declined by the player only.
- Three dynamic champions = strongest available (health ≥ wounded threshold); 0–2 handled; militia alone if none.
- Predictable siege days; living pressure decides strength; integrity 0 ends the run; no gold repair button.
- Baseline + milestone legacy rewards; claim once; strong permanent upgrades; persistence matrix (GDD §9) honoured.
- Gazette from real events only; replays are precomputed DTOs; UI never computes outcomes.
- Portrait, single workshop, panels, descriptive labels, no probabilities shown.

## PROPOSED values adopted (all in `BalanceConfig` / `SliceContent`)
- GDD table: risk chances (6/2, 15/8, 30/20 %), quality and power formulas, rarity ranges, 250 gold, 8 shelves,
  2–4 customers, siege interval 5, raid base 32 + 5/day + 0.65×pressure, forge damage 12 + 25×(ratio−1),
  legacy points 5 + days/5 + bonuses, upgrade tiers 8/20/45, integrity 100, Quick 2 / Advanced 4 energy, overwork 4.
- Mine (no GDD value existed): hero base power 16/14, +2 per level, 120 XP per level; encounter power
  18 + 1.4×day + 0.25×pressure ±20 %; win probability 0.5 + Δ/40 clamped 5–95 %; expedition loot 20–60 gold;
  death at health ≤ 0 with 20–55 damage on loss; 50 % weapon recovery on death; faction growth 6/day,
  suppression 3 per expedition win and 1 per patrol; militia +3 per patrol (max 30, −1/day); recovery cap 2/day;
  milestone point table (first sale 1, siege survived 2, champion armed 2, epic 1, legendary 3, level 5 hero 1,
  five-kill weapon 2); blessing duration 5 days; purchase utility weights; fair price = 4 gold × power.
- Catalyst effect: +5 % exceptional, −5 % defect, +4 quality. Advanced Forge is available from day 1 in the slice
  (GDD mentions an unlock; the unlock mechanism is P6).

## Balance tuning evidence (2026-10-08, `:core:simulate`, BALANCED_FAIR bot policy)
| Config | New account median (p10/p90) | Maxed upgrades median | Sieges survived/run |
|---|---|---|---|
| siegeModifier 1.0, recovery 6/day, suppression 6, growth 4, +3/level | 130 (105/150) | 150 | 15.5 |
| recovery 2, suppression 3/1, growth 6, +2/level, 120 XP | 85 (70/100) | 100 | 10.7 |
| + siegeModifier 2.0 | 45 (30/60) | 55 | 3.2 |
| + siegeModifier 2.5 | 35 (20/50) | 45 | 1.6 |
| + siegeModifier 2.75 **(adopted)** | 30 (20/45) | 40 | 1.0 |
| + siegeModifier 3.0 | 30 (20/40) | 35 | 0.6 |

Final 1,000-run check with the adopted config (seed base 1): BALANCED_FAIR 30 (20/40), SAFE_CHEAP 35 (30/45),
RECKLESS_EXPENSIVE 20 (10/30), RANDOM 40 (25/55), OVERWORK 30 (20/40); maxed upgrades 40 (25/50). Rarity for a
new account ≈ 2/40/49/8/0 % common→legendary; maxed mastery shifts it to 0/7/61/26/6 %. Hero deaths ≈ 0.5/run.
Pricing and risk choices therefore change survival materially (GDD §15.2 goal).

Rationale: the GDD target is a 15–25-day early median; the bot is a weak player (random families, no element
matching), so landing slightly above target is intended. 2.75 keeps about one survived siege per run so the
blessing mechanic is visible. Hard-lock days: 0 in all 7,000+ runs. Upgrade effect (+10 days) is modest because
the slice has only four upgrades; P6/P7 will widen the gap. Sell rate (~12 % of forged items) is inflated by the
bot forging ~3 items/day; it is not a market defect.

## P6 integration decisions (2026-10-08, session 2)
- **Default content stays `SliceContent`.** `LaunchContent` (version 2, all LOCKED counts) is complete data and
  passes `validate()` plus 50 seeded headless runs, but the quality formula was tuned for tiers 1–3; tier-6 cores
  push the base term so high that output saturates at epic/legendary. The switch to launch content is P7 work
  gated on a rarity-distribution sweep and a quality-formula retune, not a silent flip.
- Hero element taste is drawn from the elements present on the active catalog's augments, not the whole `Element`
  enum (the enum grew to six values for launch content, which would give slice heroes unsatisfiable tastes).
- `ContentCatalog.validate()` checks element coverage against the elements the catalog's augments actually use.
- The six launch-only effects are wired: `EXCEPTIONAL_CHANCE` (blessing points + upgrade points/level added before
  the clamp), `MATERIAL_EFFICIENCY` (percent chance the augment is refunded; the roll only happens when the level
  is > 0 so slice determinism is untouched), `STARTING_MATERIALS` (+N per level to every starting-kit material),
  `DISCOVERY_BONUS` (extra experiment progress per forge), `HERO_VISIT_CHANCE` (percentage points on visit chance).
- Signature recipes live in `crafting/Signatures.kt` as a data table keyed by family+core+augment with optional
  catalyst/risk/quality conditions; techniques are TEMPER (fewer defects, fewer exceptionals), QUENCH (forces the
  element affix at −3 quality) and ETCH (+1 affix slot, more defects). Quench is deliberately a trade, not the GDD's
  literal "guarantee the element affix when a slot exists", which `Forge.apply` already did.
- World events are a weighted daily pool (`engine/WorldEvents.kt`, one pick per day at 30 %, eligibility, cooldown,
  max-per-run); 22 of the 25 GDD events were in the pool, Guild Founded and Champion Retirement are deterministic
  rules in step 8, Strange Weapon Fragment came later (see below). Retirement at level 8 or 10 victories (15 %/day), guild at
  fame 3, every retiree mentors a newcomer who inherits the retiree's weapons (one-owner invariant).
- Events were not compacted in P6; the engine policy below replaced that in P7. The simulator's own trimming hook
  (`SimulationDriver.eventRetentionDays`, type-blind) is still used by the 5,000-day soak and is independent of it.
- Invariant check for "one equipped weapon per hero" is a single pass over weapons instead of heroes × weapons.
- `BalanceConfig.version` stays 1: fields were only added with defaults; every pre-existing value is unchanged.
- `GameEngine.RULES_VERSION` is held at 1 until the first public build even though P6 changed RNG draw order
  (event pool, retirement/seizure rolls, signature rolls, hero taste pool): a seed replays identically within one
  build, not across the P5 and P6 builds. No user saves exist yet; bump it with the first release.

## Balance after P6 integration (2026-10-08, 1,000 runs, seed 1, siegeModifier 2.75 kept)
| Policy | New account median (p10/p90) | Notes |
|---|---|---|
| BALANCED_FAIR | 35 (20/40) | was 30 before world events; sieges survived 1.1/run, lost 5.3, deaths 0.6, retirements 2.5 |
| SAFE_FAIR | 30 (20/40) | rarity 1/43/52/4/0 % |
| RECKLESS_FAIR | 35 (25/40) | 3.8 signature discoveries/run (three recipes need no catalyst) |
| BALANCED_CHEAP | 35 (30/40) | |
| BALANCED_EXPENSIVE | 20 (10/35) | 8 % sell rate, 97 % faction wins |
| SYNERGY | 45 (30/55) | strongest: element counters + affinity, 4 % legendary |
| OVERWORK | 35 (20/40) | |
| RANDOM | 40 (25/45) | |
| Maxed upgrades, BALANCED_FAIR | 40 (30/50) | rarity 0/7/61/26/6 % |

Siege-modifier re-sweep (BALANCED_FAIR): 3.0 → median 30, survived 0.7/run; 3.25 → 30, 0.4; 3.5 → 25, 0.3.
Raising the modifier shortens runs only by making sieges unwinnable, which starves the blessing loop, so 2.75 is
kept. The median moved 30 → 35 because the raider-encampment event now fires ≈3–4 %/day inside the pool instead of
≈12 %, and Successful Patrol drains pressure. The P7 lever for a shorter early median is forge damage per lost
siege (`forgeDamageBase`/`forgeDamagePerRatio`), not raid power. Upgrade impact: Stalwart Walls +4.7 mean days,
Forge Mastery +2.4, starting energy/gold ≈ 0 (tuning signal for P7). 0 hard-locks in 13,000 runs.

## Event-log compaction (2026-10-08, session 3, ENGINEERING)
GDD 13.3 asks to "compact ordinary events and retain rare milestones"; 15.1's "migration does not mutate histories"
is honoured because the save schema is unchanged (still v1) and no stored record is rewritten, only dropped by a
rule that runs inside End Day. Audit of every reader of `GameState.events` and the other append-only lists:

| Reader | Needs |
|---|---|
| `GameEngine.endDay` → `DayResolution` (events, headlines, visits, replays) | Today's events only (`ctx.newEvents`); never reads `state.events` |
| `GameState.lastResolution` (day report dialog, Market panel visits, End Day retry) | One day, replaced daily; bounded by construction |
| `GazettePanel` (`InfoPanels.kt`): every distinct day → `Gazette.headlines(eventsForDay(day))` | Full records for recent days; older days show only the retained history-grade headlines, days with none vanish from the archive (intended) |
| `Legacy.closeRun` (legends, lineage) | `Weapon.history` SOLD/COMMISSION owners, `Weapon.fame/kills`, `Hero.fame/fate` — not events |
| `WorldEvents` (famous blade, descendant, inheritance, cooldowns) | `legacy.legendBoard`/`lineages`, `Weapon.history` LOST/SEIZED, `eventCounters`/`eventLastDay` — not events |
| Milestones, siege tallies, discoveries | `milestones` set, `Town.siegesSurvived/Lost`, `discoveriesThisRun`, `Journal` — not events |
| Simulator (`RunStats`, perf `events=`) | `lastResolution.events` for sales; `events.size` as a size probe only |
| Tests (`WorldEventsAndGenerationsTest` 200-seed counter reconciliation, signature counts, FORGE_DESTROYED at run end) | `WORLD_EVENT` and `SIGNATURE_DISCOVERED` for the whole run; today's events |

No gameplay rule reads past events, so compaction cannot change outcomes; it only bounds the save and the
Gazette archive. `legendBoard` (20) and `lineages` (10) were already bounded. `Weapon.history` (VICTORY per
fight, SIEGE per siege) is left alone: its readers need it, it is per-weapon, and its writers live in files owned
by other agents; a 400-day forced-survival run ends with 1,766 history entries across 926 weapons, so it is
the next list to watch, not a problem today.

Policy (`persistence/EventCompaction.kt`, called once in `GameEngine.endDay` after the Gazette is built, before the
new morning; `BalanceConfig.eventRetentionDays = 30`, 0 disables): keep every record of the last 30 days, keep
forever RUN_STARTED, HERO_ARRIVED, HERO_DIED, HERO_RETIRED, GUILD_FOUNDED, HERO_MENTORED, SIEGE_WON/LOST,
FORGE_DESTROYED, SIGNATURE_DISCOVERED, MILESTONE, LEGEND_RECORDED, WORLD_EVENT, ARTIFACT_RETURNED,
WEAPON_STOLEN/INHERITED/RECOVERED/LOST; drop the rest (forged/listed/sold/equipped, rest/patrol/expedition,
wounded/leveled, commissions, pressure, warnings, damage/recovery, blessings, discovery clues), all of which are
already folded into `Weapon`/`Hero`/`Town`/`Journal` fields. Daily compaction equals one filter at the final End
Day, so a seed replays identically with or without it (`EventCompactionTest`: 400-day paired run, state equal
except `events`). Runs of 30 days or fewer never compact; longer runs lose only ordinary records older than 30 days.

Measured (`:core:simulate --runs 200 --seed 1 --perf`, forced survival, 1,000 days, JVM): before p50 0.37 ms /
p95 0.91 ms / max 7.52 ms, events=13,275 at the end; after p50 0.38 ms / p95 0.89 ms / max 2.48 ms,
events=1,834. 400-day paired test: 860 compacted vs 5,705 unbounded records (486 retained from
before the window: WEAPON_INHERITED 185, WORLD_EVENT 117, SIEGE_WON 74, HERO_RETIRED 29, HERO_MENTORED 29,
HERO_DIED 12, GUILD_FOUNDED 11, HERO_ARRIVED 9, WEAPON_RECOVERED 9, MILESTONE 7, the rest <= 2). The encoded
save shrank from 2.01 MB to 0.98 MB; weapons (926, with histories) now dominate a 400-day save. If the retained
set ever needs trimming, WEAPON_INHERITED (one per weapon per retirement) is the lever.

## Art sources (2026-10-08, session 3)
- The pixel artist's V2 pack (200 true 1x sprites) and the AI concept sheets (high-resolution pseudo-pixel art)
  both exist in `Pixel art assets/`. At phone display sizes (48–72 dp icons, a 100 dp scene strip) the concept
  slices read as the more polished set, so they stay the production art for scene, weapons, portraits, factions,
  materials, blessings and icons. The pack is used where the sheets have nothing: hero/monster idle, attack and hit
  frames, the siege wall (intact/damaged), the four-frame milestone burst and the dead/retired markers. Switching
  the whole app to the pack is one command (`import_assets.py --pack-all`), recorded here so the choice is reversible.
- Siege replay animation is a diorama in the day report that only reads `CombatReplay` rounds (attacker name →
  hero class, faction siege name → monster set); poses follow the step being shown, idle frames tick on a UI
  timer, static under reduced motion. No gameplay RNG is touched (GDD 11, 15.1).
- Portrait variants are chosen by a stable hash of the hero ID (decorative, deterministic, save-independent).
- Weapon art (2026-10-08, later the same day): the weapon master sheet gives every family an element row and
  eight visual levels, so the per-core recoloured icons and the element overlays were retired. The level column is
  core tier (iron 1 … moonsteel 6) + 1 for epic + 2 for legendary, clamped to 8; the preview shows the "base" row
  until an augment is chosen. The choice is in `Sprites.weaponLevel` and reversible. Cost: 336 PNGs at 56 px,
  about 2.5 MB of drawables (fine for a premium title; pngquant is an option if the APK ever matters).

## Signatures 13–24 and the fragment event (2026-10-08, session 3)
- `SignatureCatalog` now holds all 24 PROPOSED recipes, keyed by `LaunchContent` IDs (identical to the slice IDs
  for shared content; spear/dagger/staff recipes are unreachable under `SliceContent` because Forge validation
  rejects unknown families). Lookup by family+core+augment is a map. The 12 new recipes, by family:
  spear Thornwall (iron+verdant, salt, q50), Hoarfrost Pike (moonsteel+frost, no catalyst, safe, q70), Sunlance
  (starsteel+sun, dragon oil, reckless, q75), Gravewarden (bronze+grave, runestone, q60); dagger Nightletter
  (bronze+grave, void ink, q60), Sparkfang (silver+storm, no catalyst, reckless, q60), Emberneedle (obsidian+ember,
  salt, q55), Rimeshard (iron+frost, runestone, safe, q50); staff Noonward (starsteel+sun, void ink, q75),
  Greenheart (bronze+verdant, no catalyst, balanced, q55), Stormcaller (silver+storm, runestone, q65), Mourning Rod
  (obsidian+grave, dragon oil, safe, q65). Each grants its element affix plus one neutral affix and +4…+9 power.
  Floors sit a few points under the pre-retune median quality of the recipe (the six "excellent affinity" pairs
  carry the tier-4+ recipes); re-check reachability (`everyLaunchSignatureIsReachable…`) after the quality retune.
- Strange Weapon Fragment (GDD event 20, "signature clue"): weight 1.5, max 2 per run, cooldown 5, eligible while
  the active catalog has a signature whose journal entry is UNKNOWN. It sets that entry to OBSERVED (the journal
  then shows the base recipe and the descriptive "wants" hint, never the condition itself), adds one of the
  recipe's core and one of its augment, and emits a DISCOVERY record plus the Gazette story. No new state fields.

## SLICE reductions still in force
- Default catalog: 3 families, 6 materials + 1 catalyst, 2 classes, 1 faction, 5 blessings, 4 upgrades, 6 affixes,
  3 flaws (launch catalog exists, see above).
- World modifiers are three fixed variants.
- Weapons bought by heroes stay with them unless the hero dies (recovered, lost or seized) or retires (inherited).

## UI layout principles (2026-10-08, session 4, PROPOSED)
- One primary action per screen: Forge weapon on the Forge panel; End Day is a tonal button so it never competes.
  The six-panel nav bar (LOCKED) stays but is 64dp; no other bars stack at the bottom.
- Global chrome shows only what every panel needs: Day, Gold, Energy. Forge integrity and the next siege are the
  run-over condition and belong to the Forge (threat line) and Town (header card), not the global strip.
- Forge flow is a pinned summary (preview sprite, recipe in words, cost, button) over collapsible steps. The first
  unfinished step opens; a pick advances to the next unfinished one and the open step scrolls into view. A familiar
  recipe is three taps (GDD 12). Step headers always show the chosen value, so launch content (6 families,
  16 materials) adds chips inside a step, never a wall. Disabled choices use `GameViewModel.describe(GameError)` as
  their reason, so the UI never invents rules.
- Hierarchy comes from four styles: serif `titleLarge` for section headings, bold `titleMedium` for card titles and
  key numbers, `bodyMedium` (16sp floor) for body, `bodySmall` (14sp) only via `Secondary()` in the muted colour.
  Spacing uses `Space.sm/md/lg` (8/16/24dp). `InkMuted` darkened to 7.6:1 on parchment.
- Onboarding tips are per-panel slim banners shown one at a time; dismissal is a setting, not save state.
- Empty state is one sentence, never a grid of placeholders ("6 empty shelves").
- Day report uses a full-width `Dialog` (not `AlertDialog`) so the diorama and headlines get the whole phone width;
  anchor texts kept for scripts: "EMBERFALL GAZETTE", "Begin day", "Skip", "Suggested price", "List at".
- Verification anchors in `tools/emulator/smoke.sh` moved from "Weapon family"/"End Day 2" to
  "Forge weapon"/"Day 2".
