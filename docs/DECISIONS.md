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
- **Default content stays `SliceContent`** (superseded by balance v2 below). `LaunchContent` (version 2, all LOCKED counts) is complete data and
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

## Balance v2: launch content becomes the default (2026-10-08, session 4)
`GameEngine()` now builds on `LaunchContent.catalog`; `BalanceConfig.version` is 2; `GameEngine.RULES_VERSION` stays 1
(no user saves exist; the first release bumps it). `SaveCodec` was not touched: it has no content-version check and
`GameState.contentVersion` simply records 2 for new runs. Simulator gained `--content launch|slice` and
`--rarityTable [N]` (N forges per core x augment x risk through `Command.Forge` on a stocked fresh run, families cycled,
energy reset between forges; only the CRAFTING stream is consumed).

### Quality formula retune (rarity tables, 1,000 forges per cell, seed 1, BALANCED risk, families cycled)
The GDD shape is kept (`base + core*tier + aug*tier + affinity + mastery + roll[-13,13] + 20 exc - 12 defect`);
only `qualityBase` 35 -> 25 and `qualityPerCoreTier` 4 -> 6 changed (tier 5 is the pivot: 35+4*5 = 25+6*5). The
diagnosis: the core span was 20 points against a 27-point roll and a 20-point exceptional bonus, so tiers could not
separate and tiers 1-4 all landed rare. Thresholds stay at the GDD's 35/50/70/85.

| Core (mean over augments) | v1 C/U/R/E/L % (mean q) | v2 C/U/R/E/L % (mean q) |
|---|---|---|
| Iron (t1) | 3/35/54/8/1 (54.1) | 17/46/33/4/0 (45.8) |
| Bronze (t2) | 1/20/60/16/4 (59.9) | 4/36/51/8/1 (53.3) |
| Silver (t3) | 1/16/56/22/5 (62.5) | 2/23/55/16/3 (58.6) |
| Obsidian (t4) | 0/9/53/30/8 (66.4) | 0/12/55/27/6 (64.3) |
| Starsteel (t5) | 0/2/42/41/15 (71.7) | 0/2/42/41/15 (71.7) |
| Moonsteel (t6) | 0/1/33/45/20 (74.7) | 0/1/28/45/26 (76.6) |

| Pair (BALANCED) | v1 C/U/R/E/L % | v2 C/U/R/E/L % |
|---|---|---|
| Iron + Ember Resin | 3/41/49/8/0 | 22/47/28/3/0 |
| Iron + Verdant Sap (excellent affinity) | 1/24/62/11/2 | 8/45/42/6/0 |
| Bronze + Frost Bloom | 1/19/66/12/3 | 2/37/53/7/0 |
| Silver + Stormglass | 0/3/52/37/9 | 0/6/58/29/7 |
| Obsidian + Grave Dust | 0/1/43/45/12 | 0/2/49/40/9 |
| Moonsteel + Sun Ash (top tiers, affinity -4) | 0/1/35/47/16 | 0/0/28/48/23 |
| Moonsteel + Frost Bloom (excellent) | 0/0/27/47/26 | 0/0/21/47/33 |
| Starsteel + Sun Ash (excellent) | 0/0/11/49/41 | 0/0/11/49/41 |

What the tables show: tier 1 is mostly common/uncommon (63 %); tier 2 splits uncommon/rare (36/51, down from
20/60); tiers 3-4 are rare-centred; tier 6 is epic-centred with legendary 26 % on average and 23 % for
Moonsteel + Sun Ash; iron is never legendary. Tier 5 is a rare/epic split (42/41): raising
`qualityPerCoreTier` to 7 fixes that but pushes tier-6 legendary past 30 % (grid: base 21-22, core 7 -> moonsteel
L 31-33 %), so 6 was kept. The excellent top-tier pairs (Starsteel + Sun Ash, Moonsteel + Frost Bloom) stay at
33-41 % legendary; that is the affinity-discovery reward and no base/tier value changes it without flattening the
tiers, so it is accepted and recorded rather than chased with a new term. Known cost: the slice's SAFE_FAIR rarity
moves from 1/43/52/4/0 to 18/51/28/2/0 (iron+ember is centred at 44 instead of 50); the launch targets and the
slice's old feel could not both hold, and the launch shape wins because it is the shipped catalog.
`RarityShapeTest` locks the shape (2,000 forges per cell).

### Siege balance (BALANCED_FAIR, 1,000 runs, seed 1, launch content, quality v2)
Baseline with the v1 siege numbers: median 20 (15/25) but 0.0 sieges survived per run and 99 % faction wins. Two
causes, neither a siege-modifier problem: (1) `Heroes.resolveActivities` sent every expedition and patrol against
the first faction by ID, so with three factions two grew unchecked; (2) even with heroes on the most pressing
faction, growth 6+5+4 = 15/day exceeds what eight heroes suppress (about 10-14/day), so all three saturate at 100
pressure by day 20. Fixes: heroes act against the most pressing faction (tie by ID; identical behaviour with one
faction), and launch growth is 4/3/2.

| Config (growth 4/3/2, heroes on max pressure) | Median (p10/p90) | Mean | Survived / lost per run | Maxed median |
|---|---|---|---|---|
| siegeModifier 2.75, damage 12 + 25x(ratio-1) (v1 siege numbers) | 25 (20/30) | 24.3 | 0.2 / 4.7 | 40 |
| 2.0, 12 + 25x | 35 (25/45) | 35.5 | 1.6 / 5.5 | 55 |
| 2.0, 12 + 45x | 30 (20/40) | 29.9 | 1.6 / 4.4 | 50 |
| 2.0, 12 + 75x | 25 (15/35) | 26.3 | 1.6 / 3.7 | 45 |
| 2.25, 24 + 30x | 20 (15/30) | 22.0 | 0.8 / 3.6 | 35 |
| 2.1, 24 + 30x | 25 (15/30) | 24.0 | 1.2 / 3.6 | 40 |
| 2.0, 24 + 30x | 25 (20/35) | 25.6 | 1.6 / 3.6 | 40 |
| 2.0, 24 + 30x, recovery cap 1 | 25 (15/30) | 23.3 | 1.6 / 3.1 | 40 |
| 2.0, 20 + 50x | 25 (15/35) | 25.6 | 1.6 / 3.6 | 45 |
| 2.0, 30 + 30x | 20 (15/30) | 22.1 | 1.6 / 2.9 | 35 |
| 2.0, 24 + 50x **(adopted)** | 25 (15/35) | 23.8 | 1.6 / 3.2 | 40 |

Why 2.0 + 24/50: survived sieges need the first one or two sieges winnable (raid ~200 vs defence ~170 on day 5),
which only the modifier controls; run length is then set by damage per lost siege. Slope-only changes barely move
the median because early losses sit at ratio 1.1-1.3. 30/30 lands the median at 20 but charges a narrowly won
siege 27 integrity, which erases the win; 24/50 charges a 0.9-ratio win 19 and a 1.25-ratio loss 37, keeping
win/lose meaningfully different while the mean stays inside 15-25. Recovery cap stayed at 2/day.

Final 1,000-run table (seed 1, adopted config):

| Policy | Median (p10/p90) | Mean | Sell rate | Survived / lost | Rarity C/U/R/E/L % |
|---|---|---|---|---|---|
| BALANCED_FAIR | 25 (15/35) | 23.8 | 14 % | 1.6 / 3.2 | 19/44/31/5/0 |
| SAFE_FAIR | 25 (15/30) | 23.4 | 14 % | 1.5 / 3.2 | 19/49/29/3/0 |
| RECKLESS_FAIR | 25 (15/35) | 24.8 | 14 % | 1.7 / 3.2 | 20/38/35/7/1 |
| BALANCED_CHEAP | 30 (20/35) | 29.3 | 14 % | 2.7 / 3.2 | 19/44/31/5/1 |
| BALANCED_EXPENSIVE | 15 (10/20) | 14.1 | 8 % | 0.2 / 2.7 | 20/44/31/5/0 |
| SYNERGY | 40 (20/50) | 39.0 | 26 % | 4.4 / 3.4 | 3/27/46/16/8 |
| OVERWORK | 25 (15/35) | 24.3 | 14 % | 1.7 / 3.2 | 19/44/31/5/0 |
| RANDOM | 25 (15/40) | 26.6 | 33 % | 2.0 / 3.3 | 7/29/44/15/5 |
| Maxed upgrades, BALANCED_FAIR | 40 (25/50) | 38.5 | 9 % | 2.9 / 4.8 | 1/24/55/15/5 |

10,000-run confirmation (seed 1): BALANCED_FAIR median 25 (15/35), mean 23.7, survived 1.5, lost 3.2, sell rate
14 %, 0 hard-lock days; SAFE_FAIR median 25 (15/30), mean 23.2, survived 1.5. Hard-locks: 0 in all runs after the
RANDOM policy was limited to materials it can afford (its 53 "hard-lock" days were unaffordable moonsteel picks,
not a game lock). Hero deaths 0.5/run.

Upgrade impact (BALANCED_FAIR, mean days vs 23.8): Stalwart Walls +6.4, Well-Stocked Cellar +4.4, Forge Mastery
+2.7, Thrifty Hands +1.8, Lucky Hammer +0.7, Tireless Smith +0.5, Known Name +0.4, Family Savings 0.0; all maxed
+14.7. **Starting energy and gold still show ~0 and this was not fixed by magnitude:** Tireless Smith at +2
energy/level measured +0.8 (vs +0.5 at +1), and gold cannot register because the BALANCED_FAIR bot keeps a median
~500 gold on hand (it restocks iron at 10 gold and never buys up). Under `--impactPolicy SYNERGY`, which spends
down to ~3 gold on top-tier cores, Family Savings measures +5 median / +1.4 mean. Magnitudes were therefore left at
the GDD values (+1 energy, +100 gold per level); the honest next lever is a harness purchasing rule (buy the best
affordable core), which would move every baseline and is left for the next balance review.

## Balance review at 10,000 seeds (2026-10-08, session 4)
Numbers are provisional: reputation/loyalty economy effects were being added in parallel and the commands below are
meant to be re-run after both merge. The durable parts are the purchasing rule, the methodology and the command lines.

### Harness purchasing rule: `BALANCED_INVEST` (new policy, `--reserve N`)
The v2 review could not measure the starting-gold and starting-energy upgrades because the BALANCED_FAIR bot keeps
~500 gold and only restocks iron. Rather than change the FAIR policies (every earlier table would move), a new policy
`BALANCED_INVEST` = BALANCED_FAIR plus a purchasing rule: before each forge it takes the highest-tier core that is
already owned or in supplier stock with price <= `gold - reserve`, then the highest-tier augment the same way against
what is left; when nothing fits it falls back to the best owned pair, then the cheapest (iron + ember), so the reserve
gates premium purchases only and never stops a forge. Affinity-blind, so it stays distinct from SYNERGY. Everything
goes through `Command.BuyMaterial`/`Command.Forge` via `GameEngine.handle`; the driver reads state only to decide.
`GDD_SET` is unchanged (`--policy all` includes the new one); `--impactPolicy` still defaults to BALANCED_FAIR and now
also drives a second maxed-legacy run (the "all maxed" delta previously compared a BALANCED_FAIR maxed run with
whatever impact policy was chosen); `--json` records the reserve.

Reserve sweep (`--runs 1000 --seed 1 --policy BALANCED_INVEST --impactPolicy BALANCED_INVEST --reserve R`):

| Reserve | Median (p10/p90) | Mean | Forged / sold per run | Sell rate | Gold on hand (median) | Family Savings / Tireless Smith (mean delta) |
|---|---|---|---|---|---|---|
| 0 **(default)** | 35 (20/45) | 33.0 | 44 / 20.0 | 45 % | 3 | -0.5 / -0.2 |
| 50 | 35 (15/40) | 31.3 | 77 / 19.5 | 25 % | 4 | +0.2 / -0.0 |
| 100 | 30 (15/40) | 30.7 | 98 / 19.5 | 20 % | 10 | +0.3 / -0.3 |
| 150 | 30 (15/40) | 30.3 | 110 / 19.4 | 18 % | 38 | +0.2 / -0.2 |
| 250 | 30 (15/40) | 29.1 | 118 / 19.1 | 16 % | 95 | +1.3 / -0.6 |

Lower reserves live longer (fewer, better weapons beat more iron ones), so 0 is the default; the flag stays for
sensitivity runs. No reserve makes the two upgrades register (see below).

### Full table
`./gradlew :core:simulate --args="--runs 10000 --seed 1 --policy all --impactPolicy BALANCED_INVEST --reserve 0 --perf --json <out>"`
(launch content v2, balance v2, rules v1, 400-day cap, seeds 1..10000 per policy; 170 s on 16 threads). The
BALANCED_FAIR impact list came from `--runs 10000 --seed 1 --policy BALANCED_FAIR` (default impact policy).

| Policy (10,000 seeds) | Median (p10/p90) | Mean | Sell rate | Survived / lost sieges | Hero deaths | Hard-lock days | Rarity C/U/R/E/L % |
|---|---|---|---|---|---|---|---|
| BALANCED_FAIR | 25 (15/35) | 23.9 | 14 % | 1.6 / 3.2 | 0.5 | 0 | 19/44/31/5/0 |
| SAFE_FAIR | 25 (15/30) | 23.2 | 14 % | 1.5 / 3.2 | 0.5 | 0 | 19/49/29/3/0 |
| RECKLESS_FAIR | 25 (15/35) | 24.9 | 14 % | 1.7 / 3.3 | 0.5 | 0 | 20/38/35/7/1 |
| BALANCED_CHEAP | 30 (20/35) | 29.3 | 14 % | 2.7 / 3.2 | 0.5 | 0 | 19/44/32/5/1 |
| BALANCED_EXPENSIVE | 15 (10/20) | 14.2 | 8 % | 0.2 / 2.6 | 0.3 | 0 | 20/44/31/5/0 |
| SYNERGY | 40 (20/50) | 38.8 | 26 % | 4.4 / 3.3 | 0.6 | 0 | 3/27/46/16/8 |
| OVERWORK | 25 (15/35) | 24.3 | 14 % | 1.6 / 3.2 | 0.5 | 0 | 19/44/31/5/0 |
| RANDOM | 25 (15/40) | 26.2 | 33 % | 2.0 / 3.3 | 0.5 | 0 | 7/29/44/15/5 |
| **BALANCED_INVEST** (new) | 35 (15/45) | 32.5 | 45 % | 3.1 / 3.4 | 0.6 | 0 | 5/21/41/23/10 |
| SAFE_CHEAP | 30 (20/35) | 28.3 | 15 % | 2.5 / 3.2 | 0.5 | 0 | 18/48/29/3/0 |
| RECKLESS_EXPENSIVE | 15 (10/25) | 15.1 | 8 % | 0.3 / 2.7 | 0.4 | 0 | 20/38/35/7/1 |
| PASSIVE | 10 (10/10) | 10.0 | 0 % | 0.0 / 2.0 | 0.3 | 0 | - |
| Maxed upgrades, BALANCED_FAIR | 40 (25/50) | 38.5 | 9 % | 2.9 / 4.8 | 0.7 | 0 | 1/24/55/15/5 |
| Maxed upgrades, BALANCED_INVEST | 45 (25/55) | 43.0 | 25 % | 3.6 / 5.0 | 0.7 | 0 | 0/9/37/30/25 |

Maximum run lengths: 45-55 days for the FAIR family, 65 SYNERGY, 55 INVEST, 70 INVEST maxed; `ended` = 10,000 for
every policy (nobody reaches the 400-day cap). Perf probe (forced survival, 1,000 days): End Day p50 0.30 ms,
p95 0.65 ms, max 2.55 ms, 2,330 weapons / 94 heroes / 1,680 events at the end.

Upgrade impact, single upgrade maxed vs none, 10,000 seeds (median delta / mean delta in days):

| Upgrade (L3) | BALANCED_INVEST (base 35 / 32.5) | BALANCED_FAIR (base 25 / 23.9) |
|---|---|---|
| Stalwart Walls | +5 / +7.4 | +5 / +6.3 |
| Forge Mastery | +0 / +1.7 | +0 / +2.6 |
| Thrifty Hands | +0 / +1.3 | +0 / +1.5 |
| Lucky Hammer | +0 / +0.6 | +0 / +0.7 |
| Tireless Smith (energy) | +0 / +0.1 | +0 / +0.3 |
| Known Name | +0 / -0.0 | +0 / +0.1 |
| Well-Stocked Cellar | +0 / -0.3 | +5 / +4.2 |
| Family Savings (gold) | +0 / -0.4 | +0 / +0.0 |
| All maxed | +10 / +10.4 | +15 / +14.6 |

### Targets
- Early median 15-25 days: **holds** for the baselines (BALANCED_FAIR 25 (15/35), SAFE_FAIR 25, OVERWORK 25,
  RECKLESS_FAIR 25, RANDOM 25); the mean sits at 23-26. The cheap shelves and the invest rule sit above it
  (30 and 35), the expensive shelves below (15).
- Viable longer paths: **holds**. SYNERGY 40 (mean 38.8) and BALANCED_INVEST 35 (32.5) are clearly longer than the
  FAIR baselines; maxed legacy adds +15 median under BALANCED_FAIR (40) and +10 under BALANCED_INVEST (45, max 70).
- Hard-locks: 0 days in all 140,000 runs. No run-length outlier: every policy ends before day 70.

### Why starting gold and energy still measure ~0 (investigated, not fixed)
The purchasing rule removes the hoarding (gold on hand median 3, forged weapons 5/21/41/23/10 instead of
19/44/31/5/0) and the bot lives 8.6 days longer, yet Family Savings (+300 gold) and Tireless Smith (+3 energy) still
measure within noise under every reserve. A 300-run visit diagnostic (temporary test, not kept) shows why:

- Sales are demand-bound, not supply-bound: every forging policy sells 14-21 weapons per run (FAIR 16.3, INVEST
  19.6, SYNERGY 21.0, maxed 20.0-20.2) because a hero buys only when the weapon is affordable *and* an improvement.
  TOO_EXPENSIVE is the most common visit outcome (38/run under FAIR, 61/run under INVEST), then NOT_BETTER.
- Heroes hold ~85 gold on day 1. The two extra premium weapons that +300 gold buys on day 1 cost several hundred
  gold at 4 gold/power, so by day 5 (first siege) tier 5-6 sales are 0.05-0.1 per run with or without the upgrade;
  the extra gold only shifts which premium weapon sits on the shelf, not what the champions carry at the first sieges.
- Extra energy under INVEST is unspent (the bot is gold-limited: 44 forges per run, 1.3/day) and under FAIR buys
  more iron that nobody wants (117 forged, 16 sold).

Proposals (evidence above, no engine change made): (a) measure these two upgrades by what they are for, i.e. the
first-siege champion weapon power or the day of the first tier-4+ sale, rather than run length; (b) if run length
must move, the lever is hero purchasing power in the first week (hero starting gold / expedition gold), not the
upgrade magnitudes, which were already tested at double size in v2 without effect. Well-Stocked Cellar also flips
from +4.4 (FAIR) to -0.3 (INVEST): its common materials are exactly what a spending bot skips, so its value is a
beginner's convenience, not a run-length lever.

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
- Default catalog is now `LaunchContent` (balance v2 above); `SliceContent` remains only for slice-specific tests
  and `--content slice`.
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
