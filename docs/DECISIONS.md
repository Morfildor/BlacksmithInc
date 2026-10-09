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
sensitivity runs. No reserve value makes the two upgrades register (see below).

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
from +4.2 (FAIR; +4.4 in the v2 1,000-run list) to -0.3 (INVEST): its common materials are exactly what a spending bot skips, so its value is a
beginner's convenience, not a run-length lever.

### Reputation-aware pricing: `BALANCED_REPUTED` (2026-10-09, after the reputation merge)
BALANCED_FAIR priced at the shop's reputation ceiling, `suggestedPrice x (1 + min(reputation x 0.01, 0.25))`, so
the reputation price bonus is visible to the sweep. Run after the reputation/loyalty economy merged
(`--runs 10000 --seed 1 --policy BALANCED_REPUTED`, launch content v2, balance v2, 103 s):

| Policy (10,000 seeds, post-merge economy) | Median (p10/p90) | Mean | Sell rate | Survived / lost sieges | Gold earned (median) |
|---|---|---|---|---|---|
| BALANCED_FAIR (same run, impact baseline) | 25 (15/35) | 23.8 | 14 % | 1.6 / 3.2 | - |
| **BALANCED_REPUTED** | 25 (15/30) | 23.6 | 14 % | 1.5 / 3.2 | 1,045 |
| Maxed upgrades, BALANCED_FAIR | 40 (25/50) | 37.4 | 9 % | 2.7 / 4.8 | 1,160 |

Charging the ceiling neither helps nor hurts run length (within 0.3 days of FAIR): reputation is earned by sales,
so the raise arrives late and shaves a few early sales. The reputation bonus is a late-run comfort, not a lever,
which matches its intent (DECISIONS "Reputation and loyalty depth"). Known Name (shop_reputation L3) stays at +0.0.
The reputation merge moved the pre-merge table above only within noise (maxed BALANCED_FAIR mean 38.5 -> 37.4);
the other rows were not re-run.

## Balance v3: gameplay depth (2026-10-09, session 6)
The owner's verdict on 0.3.0 was "not fun yet, not much in it". The harness agreed on the cause: a smith forges
about 116 weapons a run and sells 16 (sell rate 14 %), gold piles up with nothing to buy (median 500 on hand), and
nothing the player does between forging and End Day touches the siege. v3 adds things to decide and things to
watch, all inside the LOCKED pillars (shop actions only; no hero control, no gold-for-repair). Every number below is
PROPOSED and lives in `BalanceConfig` (version 3) or `LaunchContent`.

### What was added
- **Affix effects beyond flat multipliers** (GDD 4.4 "independently meaningful"): Undead Bane x1.2 against the
  Hollowbound only, Giant Slayer x1.25 against elites and warlord sieges only (their flat bonus dropped from
  1.08/1.10 to 1.03), Vampiric heals 12 after a won expedition, Lucky +25 points of loot chance, Swift and
  Reinforced cut the wound of a lost expedition (x0.8 / x0.85), Heavy raises it (x1.2), Brittle shatters on a loss
  (20 %), Cursed and Bloodbound cost their wielder 4 / 7 health per win (never lethal).
- **Elite encounters** (GDD 8): chance 6 % + 0.2 % per pressure point; enemy power x1.35, wound x1.25; a win pays
  x2.5 gold, +2 fame, a guaranteed catalyst or tier 3+ material, 4 extra suppression, a weapon title and the
  `ELITE_SLAIN` milestone (+1 legacy point).
- **Warlords** (GDD 8 boss variant): at pressure 70+ the faction's named warlord leads the siege (raid x1.15).
  Beating one drops pressure by a further 15, pays the smith 120 gold in tribute and earns `WARLORD_DEFEATED`
  (+3 legacy points). The siege warning names the warlord and the element the faction fears.
- **Hero ambitions** (GDD 6): every hero has one of SLAYER (4 expedition wins), DEFENDER (stand as champion in a won
  siege), COLLECTOR (wield quality 60+), FORTUNE (hold 300 gold). An unfulfilled ambition adds 0.6 to the matching
  activity weight (FORTUNE 0.3 to expeditions and x1.3 price sensitivity; COLLECTOR +0.6 purchase utility for
  quality 60+). Fulfilment: +3 fame, +2 loyalty, +2 shop reputation, a priority-6 Gazette line, `AMBITION_FULFILLED`
  (+1 legacy point).
- **Commissions with a desired element** (GDD 5 "desirable effect"): half the offers name the patron's taste, or
  the element the leading faction fears; only that element closes them and they pay x1.5.
- **Shop actions on finished weapons**: Salvage (1 energy, returns the core), Hone (2 energy + one unit of the core,
  +6 quality, once per weapon), Arm the watch (the weapon leaves the shop; 20 % of its power joins town defense, cap
  30, half of it wears away each siege; +1 reputation). Arming the watch is a weapon-supply mechanic, not the
  gold-funded repair the GDD forbids.
- **Workshop tools**, the in-run gold sink (reset with the run): Great Bellows (+1 energy, 300 / 700), Master
  Whetstone (+3 quality, 200 / 500), Painted Signboard (+8 points visit chance, 150 / 400), Display Case (+2 shelf
  slots, 200).
- **Siege forecast** (`GameEngine.siegeForecast`): the same defense and raid numbers the siege will use, as things
  stand, with a four-step descriptive label.
- **Legacy base points 5 -> 6**: a passive first run (falls on day 10) now banks 8 and affords the cheapest upgrade.

### Demand: trade-ins and patrol pay
With the additions above in place the sweep showed why so little sells: at fair prices 49 % of shop visits ended
TOO_EXPENSIVE (the hero could afford nothing on the shelf) and 34 % NOT_BETTER; 14 % bought. Heroes earned gold only
from won expeditions. Two changes:
- **Trade-in**: a hero replacing a weapon hands the old one back for 40 % of its fair price as credit against the
  new one. The old weapon returns to the shop's storage (history `TRADED_IN`), where it can be resold, honed,
  salvaged or given to the watch. Before this, replaced weapons sat unused in the hero's pack for the rest of the run.
- **Patrol pay**: the town pays 12 gold for a day's patrol.

BALANCED_FAIR sales rose from 13.5 to 18.8 a run and TOO_EXPENSIVE visits fell from 38.7 to 16.1; BALANCED_ACTIVE
sales rose from 15.0 to 25.6. Stronger heroes then stretched runs past the first-era band (BALANCED_ACTIVE 35
(20/40), SYNERGY 45), so raid growth went from 5.0 to 6.0 power per day:

| raidPerDay | BALANCED_FAIR | BALANCED_ACTIVE | SYNERGY | Maxed, BALANCED_FAIR |
|---|---|---|---|---|
| 5.0 (v2) | 25 (15/35), 25.0, survived 1.6 | 35 (20/40), 32.0 | 45 (35/50), 42.3 | 45 (30/50), 40.8 |
| **6.0 (adopted)** | 20 (15/30), 21.7, survived 1.2 | 30 (20/35), 27.5, survived 2.3 | 40 (25/45), 36.7 | 35 (25/45), 35.5 |
| 6.5 | 20 (15/25), 20.5, survived 1.0 | 25 (20/35), 25.8, survived 2.0 | 35 (25/40), 34.4 | 35 (25/40), 33.3 |
| 8.0 | 15 (15/25), 17.7, survived 0.6 | 20 (15/30), 21.6, survived 1.2 | 30 (20/35), 28.7 | 30 (20/35), 28.5 |

### Evidence for v3 as adopted (`--runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE`, launch content)
| Policy | v2 (0.3.0) | v3 |
|---|---|---|
| BALANCED_FAIR (ignores every new shop action) | 25 (15/30), mean 23.7, sold 16.2, survived 1.5, deaths 0.5 | 20 (15/30), mean 21.7, sold 17.7, survived 1.2, deaths 0.8 |
| **BALANCED_ACTIVE** (new: tools, hone, arm the watch, salvage) | - | 30 (20/35), mean 27.5, sold 23.4 (23 %), survived 2.3, deaths 0.9, gold on hand 91 |
| SAFE_FAIR / RECKLESS_FAIR / OVERWORK | 25 / 25 / 25 (10k) | 20 (21.3) / 20 (22.4) / 20 (21.8) |
| SYNERGY | 40 (20/50), mean 38.8 (10k) | 40 (25/45), mean 36.7, sold 24.3 |
| BALANCED_INVEST | 35 (15/45), mean 32.5 (10k) | 35 (20/40), mean 31.5, sold 25.6 (52 %) |
| BALANCED_CHEAP / EXPENSIVE | 30 / 15 (10k) | 25 (mean 25.8) / 10 (mean 12.5) |
| RANDOM | 25 (15/40), mean 26.2 (10k) | 25 (15/35), mean 24.4 |
| PASSIVE | 10, banks 7 points | 10, banks 8 points |
| Maxed upgrades, BALANCED_FAIR | 40 (25/50), mean 37.9 | 35 (25/45), mean 35.5 |
| Maxed upgrades, BALANCED_ACTIVE | - | 40 (30/50), mean 39.3 |

0 hard-lock days in all 16,000 runs; the longest run is 55 days. Gold on hand under BALANCED_FAIR is still about
590 (it never buys a tool); under BALANCED_ACTIVE it is 91. A smith who uses the new actions lives about 6 days
longer than one who does not and wins about one more siege; the first-era band of 15-25 days (GDD 8) holds for the
plain policies, and good play sits just above it at 30.

Armory sweep under BALANCED_ACTIVE, measured before the demand change and the raid retune (share of weapon power /
cap / wear per siege):

| Armory | Median (p10/p90) | Mean | Sieges survived |
|---|---|---|---|
| 0.5 / 80 / 0.3 (first draft) | 35 (25/40) | 33.4 | 3.6 |
| 0.25 / 50 / 0.5 | 30 (20/40) | 29.3 | 2.7 |
| 0.25 / 40 / 0.5 | 30 (20/35) | 28.1 | 2.5 |
| **0.2 / 30 / 0.5 (adopted)** | 25 (15/35) | 26.9 | 2.3 |

The first draft made surplus stock a free second wall. At 0.2 / 30 / 0.5 a full armory is worth about two thirds of
one early champion and has to be refilled after every siege.

Upgrade impact under BALANCED_ACTIVE (base 30 / 27.5; median / mean delta): Stalwart Walls +5 / +6.3, Well-Stocked
Cellar +0 / +3.7, Forge Mastery +0 / +2.8, Thrifty Hands +0 / +2.0, Lucky Hammer +0 / +0.9, Tireless Smith
+0 / +0.6, Family Savings +0 / +0.4, Known Name +0 / +0.0, all maxed +10 / +11.8. Starting gold registers for the
first time (+0.4) now that gold buys tools, but it is still the weakest track with Known Name.

The simulator now prints shop visits per run by outcome (`visitsPerRun` in the JSON report).

### Not changed, and not measured
- `RULES_VERSION` stays 1 (bump with the first release, as before). Hero generation now draws an ambition and
  expeditions draw an elite roll, so a given seed plays differently from 0.3.0.
- Save schema stays v1: every new field has a default (`Hero.ambition` is null on heroes from older saves; they
  simply have none). `SaveFixtureTest` still decodes the v1 fixture and plays a day on it.
- The 10,000-seed table above is v2; the v3 table at 10,000 seeds is in the next section.
- Tool prices and the individual affix magnitudes were set by judgement; the per-tool and per-affix sweeps in the
  next section measure them.

## Balance v3 review at 10,000 seeds and per-tool / per-affix sweeps (2026-10-09)
Evidence only: no gameplay code, content value or `BalanceConfig` default changed (balance stays v3). The 1,000-seed
v3 rows above reproduce exactly at seed 1 (BALANCED_ACTIVE 30 (20/35), mean 27.5, sold 23.4, survived 2.3, gold on
hand 91), so the Gazette-edition commit did not move gameplay.

### Harness additions (Simulator.kt only)
- Catalog sweeps: `--noTool id[,id]` drops a workshop tool, `--toolCost id=mult[,id=mult]` scales its per-level
  costs (rounded), `--noAffixEffect id[,id]|all` keeps the affix (it still rolls, its flat attack/defense multipliers
  stay) but neutralises the v3 effect (bane, elite, heal, loot, wound multiplier, shatter, self-harm). Each derives a
  `ContentCatalog.copy(...)` that is validated before the engine sees it; the overrides land in the `--json` report.
- `--noImpact` skips the maxed-legacy and per-upgrade runs (a sweep case is then one 1,000-seed run, about 12 s).
- New per-run counters in `RunStats` / `PolicySummary`: elites slain, weapons broken, warlord-led sieges fought and
  won, tool purchases (share of runs, level at run end, first-purchase day) and weapons carrying each affix.

Commands (launch content v2, balance v3, rules v1, 400-day cap, 16 threads):
- 10,000 seeds: `./gradlew :core:simulate --args="--runs 10000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE --content launch --perf --json <out>"` (375 s, run alongside the sweep).
- Sweeps: `--runs 1000 --seed 1 --policy BALANCED_ACTIVE --content launch --noImpact --json <out>` plus `--noTool X`,
  `--toolCost X=0.5`, `--toolCost X=2`, `--noAffixEffect X`; the same command at seeds 10001 and 20001 gives the noise
  floor; the SYNERGY cross-check uses `--policy SYNERGY`. Perf probe standalone: `--runs 1 --policy PASSIVE --noImpact --perf`.

### Full table at 10,000 seeds (v3; the 1,000-seed mean from the v3 section in parentheses)
| Policy (10,000 seeds) | Median (p10/p90) | Mean (1k) | Sold/run (rate) | Survived / lost sieges | Deaths | Gold on hand | Visits/run: bought / NOT_BETTER / TOO_EXPENSIVE | Elites slain | Rarity C/U/R/E/L % |
|---|---|---|---|---|---|---|---|---|---|
| BALANCED_FAIR | 20 (15/30) | 21.8 (21.7) | 17.6 (16 %) | 1.2 / 3.1 | 0.9 | 584 | 14.9 / 46.0 / 15.2 | 3.0 | 19/44/31/5/1 |
| **BALANCED_ACTIVE** | 30 (20/35) | 27.7 (27.5) | 23.5 (23 %) | 2.3 / 3.2 | 0.9 | 92 | 20.7 / 58.8 / 26.9 | 4.1 | 10/41/40/8/1 |
| SAFE_FAIR | 20 (15/30) | 21.3 (21.3) | 16.8 (16 %) | 1.2 / 3.1 | 0.9 | 548 | 14.5 / 44.3 / 15.5 | 2.8 | 19/49/29/3/0 |
| RECKLESS_FAIR | 20 (15/30) | 22.6 (22.4) | 18.6 (17 %) | 1.3 / 3.2 | 0.9 | 625 | 15.6 / 48.9 / 14.8 | 3.2 | 19/37/35/7/1 |
| OVERWORK | 20 (15/30) | 21.9 (21.8) | 17.7 (16 %) | 1.3 / 3.1 | 0.9 | 560 | 15.0 / 47.4 / 14.6 | 3.1 | 19/44/31/5/1 |
| BALANCED_REPUTED | 20 (15/30) | 21.6 (-) | 17.1 (16 %) | 1.2 / 3.1 | 0.9 | 589 | 14.5 / 43.3 / 17.7 | 2.9 | 19/44/31/5/1 |
| BALANCED_CHEAP | 25 (20/30) | 25.7 (25.8) | 17.9 (16 %) | 2.0 / 3.1 | 0.7 | 333 | 14.7 / 76.4 / 1.6 | 4.5 | 18/43/32/6/1 |
| SAFE_CHEAP | 25 (20/30) | 24.8 (-) | 16.9 (16 %) | 1.9 / 3.1 | 0.8 | 294 | 14.1 / 72.8 / 1.7 | 4.1 | 18/48/30/4/1 |
| BALANCED_EXPENSIVE | 10 (10/15) | 12.4 (12.5) | 4.5 (8 %) | 0.1 / 2.4 | 0.7 | 139 | 3.1 / 3.9 / 25.7 | 0.7 | 20/45/31/4/0 |
| RECKLESS_EXPENSIVE | 10 (10/15) | 12.7 (-) | 4.7 (8 %) | 0.1 / 2.5 | 0.7 | 142 | 3.1 / 4.5 / 24.9 | 0.7 | 20/38/35/7/1 |
| SYNERGY | 40 (25/45) | 36.7 (36.7) | 24.3 (30 %) | 4.0 / 3.3 | 0.9 | 3 | 20.0 / 87.6 / 26.1 | 6.5 | 3/26/45/17/8 |
| BALANCED_INVEST | 35 (20/40) | 31.5 (31.5) | 25.6 (52 %) | 2.9 / 3.4 | 0.9 | 3 | 20.9 / 65.6 / 27.9 | 5.6 | 5/21/41/24/10 |
| RANDOM | 25 (15/35) | 23.9 (24.4) | 16.8 (37 %) | 1.5 / 3.2 | 0.9 | 7 | 13.5 / 39.3 / 27.9 | 3.4 | 7/28/44/16/5 |
| PASSIVE | 10 (10/10) | 10.0 (10.0) | 0.0 | 0.0 / 2.0 | 0.5 | 250 | 0 / 0 / 0 (30.9 EMPTY_SHELVES) | 0.2 | - |
| Maxed upgrades, BALANCED_FAIR | 35 (25/45) | 35.5 (35.5) | 23.0 (11 %) | 2.4 / 4.7 | 1.0 | 1,288 | 15.9 / 79.4 / 39.1 | 6.1 | 1/23/54/16/5 |
| Maxed upgrades, BALANCED_ACTIVE | 40 (25/50) | 39.0 (39.3) | 24.9 (12 %) | 3.1 / 4.7 | 1.1 | 126 | 18.2 / 63.8 / 71.9 | 6.2 | 0/13/56/22/9 |

Every 1,000-seed v3 mean holds at 10,000 within 0.5 days. 0 hard-lock days in 160,000 runs; every run ends (longest:
45 BALANCED_ACTIVE, 55 SYNERGY / INVEST / maxed FAIR, 60 maxed ACTIVE). Weapons shatter 0.1 times a run. Warlord-led
sieges are fought 0.02-0.03 times a run (1,000-seed probes: 0.031 ACTIVE, 0.025 FAIR, 0.017 SYNERGY, about one
siege in 200) and **no policy wins one in 160,000 runs** (the tribute, the extra pressure drop and the
`WARLORD_DEFEATED` legacy point are unreachable; see recommendations). Under BALANCED_ACTIVE the bot buys
the cheapest affordable tool first, so purchases follow price: signboard in 100 % of runs on day 1 (level 1.7 at run
end), whetstone 100 % on day 2.3 (1.3), display case 100 % on day 3.7, bellows 92 % on day 7.4 (1.0).

Upgrade impact under BALANCED_ACTIVE, single upgrade maxed vs none, 10,000 seeds (base 30 / 27.7; 1,000-seed mean
delta in parentheses):

| Upgrade (L3) | Median delta | Mean delta (1k) |
|---|---|---|
| Stalwart Walls | +5 | +6.3 (+6.3) |
| Well-Stocked Cellar | +0 | +3.2 (+3.7) |
| Forge Mastery | +0 | +2.3 (+2.8) |
| Thrifty Hands | +0 | +1.5 (+2.0) |
| Lucky Hammer | +0 | +0.6 (+0.9) |
| Tireless Smith | +0 | +0.3 (+0.6) |
| Family Savings | +0 | +0.1 (+0.4) |
| Known Name | +0 | -0.4 (+0.0) |
| All maxed | +10 | +11.3 (+11.8) |

Known Name is the only track that measures negative at 10,000 seeds (the policy never prices on reputation, so a head
start in reputation only reorders which heroes visit and who asks for commissions); small, but worth a look when the
reputation economy is next touched.

Perf probe, standalone and warm (`--runs 200 --seed 1 --policy BALANCED_FAIR --noImpact --perf`; forced survival,
1,000 days): End Day p50 0.51 ms, p95 0.99 ms, max 2.60 ms with 3,114 weapons / 95 heroes / 2,040 events at the end
(v2 probe: 0.30 / 0.65 / 2.55 ms, 2,330 weapons). Inside the GDD 15.3 target (p95 < 200 ms); the probe that ran
alongside the sweep read 1.34 / 10.5 / 254 ms, which is CPU contention, not the engine. Trade-ins keep replaced
weapons in the shop, so the `weapons` map grows faster than before (PROGRESS next action 3).

### Noise floor (BALANCED_ACTIVE, 1,000 seeds)
| Base seed | Median (p10/p90) | Mean | Sold/run | Survived | Deaths | Gold on hand | Elites slain |
|---|---|---|---|---|---|---|---|
| 1 (the sweep baseline) | 30 (20/35) | 27.5 | 23.4 | 2.3 | 0.94 | 91 | 4.0 |
| 10001 | 30 (20/35) | 27.4 | 23.4 | 2.3 | 0.99 | 94 | 4.1 |
| 20001 | 30 (20/35) | 27.7 | 23.6 | 2.3 | 0.96 | 94 | 3.9 |

A sweep delta is real only above about 0.3 days of mean, 0.3 sales, 0.05 deaths or 0.2 elites; the median is quantised
to the 5-day siege rhythm and never moved in the tool sweep.

### Per-tool sweep (BALANCED_ACTIVE, 1,000 seeds, seed 1; deltas vs the baseline)
| Case | Median (p10/p90) | Mean | Sold/run | Survived | Gold on hand | What changed in the purchases (share of runs / level at run end / first day) |
|---|---|---|---|---|---|---|
| **Baseline** (catalog costs: bellows 300/700, whetstone 200/500, signboard 150/400, display case 200) | 30 (20/35) | 27.5 | 23.4 | 2.3 | 91 | signboard 100 % / 1.7 / 1.0; whetstone 100 % / 1.3 / 2.3; display case 100 % / 1.0 / 3.7; bellows 92 % / 1.0 / 7.2 |
| All four tools removed | 30 (20/35) | 27.8 (+0.3) | 24.1 (+0.7) | 2.3 | 778 | no gold sink: 133.8 forged (101.6), rarity 19/44/31/5/0 (10/41/39/8/1) |
| Bellows removed | 30 (20/35) | 27.6 (+0.1) | 23.7 (+0.4) | 2.3 | 100 | whetstone reaches 1.5 |
| Bellows x0.5 (150/350) | 30 (20/35) | 27.7 (+0.3) | 23.9 (+0.5) | 2.4 | 64 | bellows 100 % / 1.8 / 1.0; 110.8 forged |
| Bellows x2 (600/1,400) | 30 (20/35) | 27.6 (+0.1) | 23.7 (+0.4) | 2.3 | 100 | bellows 14 % / 0.1 / 23.1 |
| Whetstone removed | 30 (20/35) | 27.3 (-0.2) | 23.1 (-0.3) | 2.2 | 100 | rarity 18/43/33/6/1 (common share 10 -> 18 %) |
| Whetstone x0.5 (100/250) | 30 (20/35) | **28.2 (+0.8)** | **24.1 (+0.7)** | 2.4 | 90 | whetstone 100 % / 2.0 / 1.0; rarity 5/40/44/9/2 |
| Whetstone x2 (400/1,000) | 30 (20/35) | 27.6 (+0.1) | 23.7 (+0.4) | 2.3 | 100 | whetstone 80 % / 0.8 / 10.5 |
| Signboard removed | 30 (20/35) | **28.2 (+0.7)** | **24.5 (+1.2)** | 2.4 | 119 | visits/run 105.8 (107.3); whetstone and display case bought earlier (day 1.0 / 2.7), bellows 96 % on day 5.5 |
| Signboard x0.5 (75/200) | 30 (20/35) | 27.4 (-0.1) | 23.0 (-0.4) | 2.3 | 92 | signboard 100 % / 2.0 / 1.0 |
| Signboard x2 (300/800) | 30 (20/35) | 27.9 (+0.5) | 23.8 (+0.4) | 2.3 | 89 | signboard 88 % / 0.9 / 9.9 |
| Display case removed | 30 (20/35) | 27.6 (+0.1) | 22.8 (-0.5) | 2.3 | 100 | - |
| Display case x0.5 (100) | 30 (20/35) | 27.5 (+0.0) | 23.7 (+0.3) | 2.3 | 102 | display case 100 % / 1.0 / 1.0 |
| Display case x2 (400) | 30 (20/35) | 27.5 (+0.1) | 22.9 (-0.5) | 2.3 | 98 | display case 53 % / 0.5 / 16.3 |

Removing one tool also re-orders the others (the bot buys the cheapest affordable tool first), so the single-tool rows
mix the tool's own value with the gold it frees; the "all four removed" row isolates the layer.

Findings:
- **The tool layer buys no run length.** A smith who buys all four (about 1,100 gold a run) lives 27.5 days; one who
  buys none lives 27.8 with 778 gold idle. BALANCED_ACTIVE's +5.9 days over BALANCED_FAIR come from Hone, Arm the
  watch, Salvage and listing the strongest stock, not from tools. Tools do what v3 wanted as a gold sink (gold on hand
  584 -> 92) and nothing for the siege.
- **Signboard: its effect is capped out.** `maxCustomersPerDay = 4` and the baseline already draws 3.9 visits a day
  (107 a run over 27.5 days), so +8 points of visit chance per level mostly changes which heroes come, not how many:
  removing a level-1.7 signboard moves visits by about 4 % a day (3.90 -> 3.75) and, because its 150 + 400 gold then buy the whetstone and
  display case on days 1-3 instead of 2-4, the run gains +0.7 days and +1.2 sales. Halving its price makes it
  slightly worse (-0.1 / -0.4), doubling it slightly better (+0.5): at every price it is a trap for a buyer who takes
  the cheapest tool first.
- **Whetstone: pays only when it comes early.** At 200/500 it is within noise of not existing (-0.2 removed); at
  100/250 the bot has level 2 on day 1 and gains +0.8 days / +0.7 sales (the strongest positive in the sweep; common
  share 10 -> 5 %). At 400/1,000 it arrives on day 10 and is worth nothing.
- **Bellows: neutral at any price.** +1 energy becomes more unsold iron (forged 101.6 -> 110.8 at half price, sold
  +0.5); removing it is +0.1. For a human it is a convenience (more hones and salvages a day), not a lever.
- **Display case: a sales tool, not a survival tool.** +2 slots sell +0.5 weapons a run and change run length by
  0.0-0.1; at 400 only 53 % of runs buy it (day 16) and the sales gain disappears.

### Per-affix sweep (effect neutralised; BALANCED_ACTIVE, 1,000 seeds, seed 1; deltas vs the baseline)
| Effect neutralised | Weapons carrying it / run | Mean days | Sold/run | Survived | Deaths | Elites slain | Shattered/run |
|---|---|---|---|---|---|---|---|
| **Baseline** (all effects on) | - | 27.5 | 23.4 | 2.3 | 0.94 | 4.0 | 0.04 |
| Reinforced (wound x0.85) | 4.4 | 27.4 (-0.1) | 23.4 | 2.3 | 1.01 (+0.07) | 4.0 | 0.04 |
| Vampiric (heal 12 on a win) | 1.5 | 27.5 (0.0) | 23.5 | 2.3 | 0.94 (0.00) | 4.0 | 0.04 |
| Swift (wound x0.8) | 4.3 | 27.5 (0.0) | 23.3 | 2.3 | 1.00 (+0.06) | 4.0 | 0.04 |
| Giant Slayer (x1.25 vs elites and warlords) | 4.4 | 27.4 (-0.1) | 23.4 | 2.3 | 0.95 (+0.01) | 3.8 (-0.2) | 0.04 |
| Undead Bane (x1.2 vs the Hollowbound) | 1.5 | 27.3 (-0.2) | 23.3 | 2.3 | 0.94 (0.00) | 4.0 | 0.04 |
| Lucky (+25 points loot chance) | 4.3 | 27.4 (-0.1) | 23.3 | 2.3 | 0.93 (-0.01) | 4.0 (-0.1) | 0.05 |
| Brittle (20 % shatter on a loss) | 1.4 | 27.6 (+0.1) | 23.4 | 2.3 | 0.92 (-0.02) | 4.1 | 0.00 (-0.04) |
| Heavy (wound x1.2) | 1.4 | 27.5 (0.0) | 23.4 | 2.3 | 0.92 (-0.02) | 4.0 | 0.04 |
| Cursed (self-harm 4 on a win) | 1.4 | 27.5 (0.0) | 23.4 | 2.3 | 0.93 (-0.01) | 4.0 | 0.04 |
| Bloodbound (self-harm 7 on a win) | 1.3 | 27.5 (0.0) | 23.4 | 2.3 | 0.94 (0.00) | 4.0 | 0.04 |
| All ten effects | - | 27.1 (-0.3) | 23.3 | 2.2 | 0.98 (+0.04) | 3.7 (-0.3) | 0.00 (-0.04) |

SYNERGY cross-check (forges the element the leading faction fears, so the special affixes are 2-5x as common: Undead
Bane 7.9 weapons a run, Giant Slayer / Reinforced / Swift / Lucky 6.8-6.9, Vampiric 2.8, Frostbound 30.5):

| SYNERGY, effect neutralised | Median (p10/p90) | Mean days | Sold/run | Survived | Deaths | Elites slain | Shattered/run |
|---|---|---|---|---|---|---|---|
| **Baseline** (all effects on) | 40 (25/45) | 36.7 | 24.3 | 4.0 | 0.92 | 6.5 | 0.06 |
| All ten effects | 35 (25/45) | 35.8 (-0.9) | 24.1 | 3.9 | 1.05 (+0.13) | 6.2 (-0.3) | 0.00 |
| Undead Bane | 35 (25/45) | 36.0 (-0.7) | 24.1 | 3.9 | 0.92 | 6.4 | 0.06 |
| Giant Slayer | 40 (25/45) | 36.7 (0.0) | 24.3 | 4.0 | 0.94 | 6.2 (-0.3) | 0.07 |
| Reinforced | 40 (25/45) | 36.6 (-0.1) | 24.3 | 4.0 | 0.95 (+0.03) | 6.4 | 0.06 |
| Swift | 40 (25/45) | 36.5 (-0.2) | 24.3 | 4.0 | 0.96 (+0.04) | 6.5 | 0.06 |
| Lucky | 40 (25/45) | 36.5 (-0.2) | 24.2 | 4.0 | 0.94 | 6.5 | 0.07 |
| Vampiric | 40 (25/45) | 36.7 (0.0) | 24.3 | 4.0 | 0.92 | 6.5 | 0.07 |

Findings:
- **No affix dominates.** Under BALANCED_ACTIVE every single effect is inside the noise floor on every measure, and
  all ten together are worth -0.3 days / -0.3 elite kills / +0.04 deaths. The reason is occurrence, not magnitude:
  the cheapest recipe (iron + ember resin) puts Flaming, which has no v3 effect, on 41 of the 102 weapons forged a
  run; each non-elemental beneficial affix lands on 4.4 weapons (exceptional rolls), each flaw on 1.3-1.4, and only
  the sold and wielded fraction of those ever fights.
- **Undead Bane is the one effect that registers once the affix is common**: under SYNERGY it is worth +0.7 days and
  the median drops from 40 to 35 without it; the whole layer there is worth about a day and 0.13 deaths a run.
- **Effects that do something measurable**: Giant Slayer (-0.2 / -0.3 elite kills a run when off, about 5 %),
  Reinforced and Swift (deaths +0.03 to +0.07 a run when off), Brittle (0.04-0.06 shattered weapons a run, so a
  Brittle weapon breaks in about one run in 20).
- **Effects that do nothing measurable in either policy**: Vampiric (heal 12), Cursed and Bloodbound (self-harm 4 / 7;
  heroes rest the health back and deaths do not move), Heavy (wound x1.2), Lucky (+25 points loot chance: the loot is
  a random non-catalyst material, which the shop already has in surplus).

### Recommendations (evidence above; none applied)
1. **Signboard**: the +8 points of visit chance adds only a few percent of visits a day while `maxCustomersPerDay = 4`
   is nearly saturated (3.9 visits a day). Give it an effect that reaches sales, e.g. +1 customer a day per level (engine: the tool total
   added to `maxCustomersPerDay`), and keep 150/400; if the effect stays, price it 300/600 so it is bought after the
   whetstone and display case (x2: +0.5 days; removed: +0.7 days, +1.2 sales).
2. **Whetstone 200/500 -> 120/300**: at 200/500 it is within noise of not existing (-0.2 removed); at 100/250 it is
   +0.8 days / +0.7 sales because level 2 arrives before the first siege. The lever is the order, not the price alone:
   the signboard-removed row (+0.7) is the same case, the whetstone bought first; 120 < 150 puts it ahead of the
   signboard for a cheapest-first buyer. Confirm at 10,000 seeds after the change.
3. **Bellows 300/700**: leave. Neutral at half and double price; it is a convenience, not a lever, and the
   half-price run spent the gold on it at the whetstone's expense without gaining anything.
4. **Display case 200**: leave. Its +0.5 sales a run vanish at 400 (bought in 53 % of runs, on day 16).
5. **Warlords**: `warlordPressure = 70` is reached at a siege 0.02-0.03 times a run (one siege in about 200) and never
   won in 160,000 runs,
   so tribute (120 gold), the -15 pressure and the `WARLORD_DEFEATED` legacy point are dead content. Lower it to
   45-50 (a won siege already drops pressure by 25) and re-measure with the new `warlord sieges/run` counter, aiming
   at a warlord in roughly one siege in five with some of them won.
6. **Affix magnitudes**: do not tune them by the aggregate; occurrence hides them. Keep Undead Bane 1.2 (the model:
   it targets the faction that leads the siege). If Giant Slayer is meant to be a visible path to elite kills, 1.25 ->
   1.5 (it is worth 5 % of elite kills today). Lucky's loot bonus should hand out something scarce (a catalyst or a
   tier 3+ material, as elite loot does) or be dropped; a 25-point chance at a common material measures 0. Cursed /
   Bloodbound self-harm 4 / 7 is invisible next to a 20-55 expedition wound; make it 15-20 if it is meant to be a
   trade-off, or accept it as flavour. Reinforced / Swift (x0.85 / x0.8) and Heavy (x1.2) move deaths by at most
   0.07 a run; x0.6-0.7 and x1.5 would make them felt.
7. **Known Name** -0.4 days at 10,000 seeds under BALANCED_ACTIVE (the only negative track): measure it by the
   reputation price ceiling it is for (BALANCED_REPUTED) before changing anything.
8. **Perf**: p95 0.99 ms warm is inside the target, but the 1,000-day probe now ends with 3,114 weapons (2,330 in v2)
   because trade-ins return replaced weapons to the shop; prune or cap lost, destroyed and salvaged records before
   long saves matter (PROGRESS next action 3).

## Balance v4: weapon fame (2026-10-09, session 7)
GDD 7 PROPOSED: "Weapon fame can grant a limited mechanical effect, with caps against runaway snowballing." Until now
fame was only a record (titles, the Legend Board, the collector event). Every number lives in `BalanceConfig` under a
`// v4: weapon fame` marker (`version` 4, merged with the weapon-wear work below; combined evidence in "Balance v4 as merged").

### What was added
- **Counted fame**: only the first `weaponFameCap` = 10 fame points of a weapon count for any effect. Fame itself keeps
  growing as a record (+1 per won expedition, +2 for an elite, +2 per siege held) and the Legend Board still ranks raw
  fame; the clamp sits inside each effect, never on the stored value.
- **Power** (`Power.fameFactor`): +0.5 % attack and defense per counted point, +5 % at the cap, one plain multiplier next
  to class fit, condition, matchup, traits, affixes and blessing. This is the loop the cap closes: a famous blade wins
  more, winning adds fame, champions' blades collect +2 per siege held, and from ten fame on nothing more happens.
- **Desire** (`Market.evaluate`): +0.05 utility per counted point (+0.5 at the cap, one purchase threshold); a hero with
  an unfulfilled COLLECTOR ambition counts it twice (+1.0 at most). Fame never touches `improvement`, so it cannot make
  a weaker blade read as an upgrade; it tips undecided buyers and ranks a storied blade above a plain one of equal power.
- **Price** (`GameEngine.suggestedPrice`): +0.5 % per counted point, +5 % at the cap. Heroes do not raise their price
  ceiling for fame, so the premium is paid out of the desire bonus: at the cap the thriftiest buyer (Greedy x1.6,
  saving a FORTUNE x1.3) loses 3 x 0.05 x 2.08 = 0.31 utility to it against the 0.5 gained (`WeaponFameTest`).
- **Returned legends** (`famous_blade`): the blade now comes back with its legend's fame (it used to come back with 0),
  still at 70 % quality and power. Its effect is capped like any other; the raw fame makes it collector-eligible as
  soon as it is listed and lets it re-enter the Legend Board at era end (both existing mechanisms; one return per run).
- **Shelf line** (`Labels.fame`): "storied" from 3 (the Legend Board threshold), "famed" from 6, "renowned" from 10
  (the cap). Descriptive only; "legendary" was avoided because it is already the rarity word on the same line.

### Evidence (`--runs 1000 --seed 1 --policy all`, launch content)
Cells: median (p10/p90), mean, sold/run, sieges survived/run, deaths/run.

| Power per counted point | BALANCED_FAIR | BALANCED_ACTIVE | SYNERGY | BALANCED_INVEST | Maxed, BALANCED_FAIR |
|---|---|---|---|---|---|
| 0, no fame effects (= v3) | 20 (15/30), 21.7, 17.7, 1.2, 0.8 | 30 (20/35), 27.5, 23.4, 2.3, 0.9 | 40 (25/45), 36.7, 24.3 | 35 (20/40), 31.5, 25.6 | 35 (25/45), 35.5 |
| 0, desire and price only | 20 (15/30), 21.7, 17.7, 1.2, 0.8 | 30 (20/35), 27.6, 23.5, 2.3, 1.0 | 40 (25/45), 36.6, 24.4 | 35 (20/40), 31.5, 25.7 | 35 (25/45), 35.5 |
| **0.005 (+5 % cap, adopted)** | 20 (15/30), 22.5, 18.0, 1.3, 0.9 | 30 (20/35), 28.4, 24.0, 2.5, 1.0 | 40 (25/45), 38.0, 24.8 | 35 (20/45), 32.6, 26.2 | 40 (25/45), 36.5 |
| 0.01 (+10 % cap, the brief's example) | 25 (15/30), 23.2, 18.2, 1.5, 0.9 | 30 (20/40), 29.3, 24.5, 2.6, 1.0 | 40 (25/50), 39.5, 25.3 | 35 (20/45), 34.1, 27.0 | 40 (25/45), 37.7 |

0 hard-lock days in every row. The desire and price terms alone move nothing the harness can see: the bot's shelves are
mostly fresh forges, and a famous trade-in resells either way. The whole shift comes from champions' blades, which reach
the cap by the third or fourth siege. The brief's example (+1 % per point, +10 % at the cap) overshoots its own
acceptance band of "20 (15/30) / 30 (20/35), within about 2 days": BALANCED_FAIR's median ticks from 20 to 25 (sieges
fall every five days; mean +1.5), BALANCED_ACTIVE's p90 from 35 to 40 (mean +1.8), and SYNERGY and BALANCED_INVEST
gain 2.6-2.8 days. Half of it (+0.5 % per point, +5 % at the cap) leaves every median and p10/p90 exactly as in v3 with
means +0.8 (BALANCED_FAIR) and +0.9 (BALANCED_ACTIVE), +0.1-0.2 sieges survived, +0.3 sales a run and deaths up 0.1
with the longer runs, so 0.005 is the default; `weaponFamePowerPerPoint` 0.01 is one number away if the owner wants
the effect stronger at merge.

### Not changed, and not measured
- `BalanceConfig.version` stays 3 until merge; `RULES_VERSION` stays 1. A seed now plays differently from 0.4.0 once any
  blade has fame.
- Save schema unchanged: `Weapon.fame` already existed. Legends returned before this change keep fame 0.
- `Market.purchase` still measures its Gazette "premium" against `power x fairGoldPerPower`, so a renowned blade sold at
  its suggested price is reported as "above the going rate on the shop's good name" (it is the blade's name, not the
  shop's). Left alone to keep the Market change to one term.
- `Market.giveAndEquip` and the simulator bot still compare raw power; a hero handed a famous but slightly weaker blade
  keeps the stronger one.
- No multi-era harness run: the returned-legend path is covered by `WeaponFameTest` and `WorldEventsAndGenerationsTest`.

## Balance v4: weapon wear (2026-10-09, session 7)
The v3 sweep left demand as the bottleneck: at fair prices NOT_BETTER was the commonest visit outcome (45.8 of about
77 visits per BALANCED_FAIR run, 58.3 of about 107 for BALANCED_ACTIVE). A hero buys only a strict upgrade, keeps
one weapon for the whole run, and of the ~5 weapons forged a day ~1 sells. GDD 6's power formula carries a
`condition` factor and GDD 7 gives weapons lives; wear gives heroes a reason to come back and makes what the smith
forges, and re-hones, matter. Every number is PROPOSED and lives in `BalanceConfig` under `// v4: weapon wear`
(`BalanceConfig.version` 4; combined evidence in "Balance v4 as merged").

### What was built
- `Weapon.condition` 0-100, default 100. Save schema stays v1: older saves decode with every blade keen
  (`SaveFixtureTest` and `WeaponWearTest` check the v1 fixture).
- **Wear** (`Battle`): the equipped weapon loses 6 condition per won expedition, 10 per lost one and 15 per siege it
  stands as a champion's weapon, floor 0, applied after the fight resolves (a fight uses the condition it went in
  with). Fixed amounts, no new RNG draws. Bare hands do not wear; the armory's own siege wear is unchanged.
- **Power** (`Power.conditionFactor`): 1.0 at condition 100 down to 0.75 at 0, linear, multiplying the weapon's
  share only: `(heroBase + weaponPower x conditionFactor) x classFit x ...`. The GDD's `condition` slot is already
  the hero's health (`Power.condition`); discounting the hero's own base as well would have hit veterans hardest and
  spiked deaths. One function, one token in each of `attackPower` / `defensePower`.
- **Demand** (`Market`): `evaluate` compares worn power on both sides (the hero's current weapon and the listing: a
  traded-in blade relisted as it is counts for less), a hero whose weapon is below condition 50 adds a flat +0.6
  utility to every listing and the purchase is reported as `WORN_OUT`; `fairPrice` (the suggested price, the price
  ceiling, the trade-in credit and the Gazette premium) is discounted for wear, so a worn trade-in listed at the
  suggested price is not read as overpriced; `giveAndEquip` compares worn power, so a commissioned or inherited
  blade replaces a battered one.
- **Hone** always restores condition to 100. The +6 quality and the `honed` flag apply the first time only, so a
  worn trade-in can be re-honed for the usual 2 energy and one core (`Weapon.canBeHoned`); Hone on a keen,
  already-honed blade is still `AlreadyHoned`. Salvage and Arm the watch are unchanged (a donated weapon's armory
  value ignores wear; it is bounded by the cap of 30 and the 50 % siege wear).
- UI, one line each: `Labels.condition` ("worn" below 70, "battered" below 40) in the weapon summary and the Town
  hero lines; the Hone button reads "Re-hone" on a worn honed blade; the day report says "their own blade was worn
  out". Simulator: `BALANCED_ACTIVE` re-hones worn (< 50) trade-ins as well as unhoned stock; no other policy changed.

### Sweep (`--runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE`, launch content)
Cells: median (p10/p90), mean days, sold/run, NOT_BETTER visits/run (NB), hero deaths/run, sieges survived/run.

| Wear win/loss/siege, floor, threshold/urge | BALANCED_FAIR | BALANCED_ACTIVE | SYNERGY | Maxed FAIR / ACTIVE |
|---|---|---|---|---|
| v3, no wear | 20 (15/30), 21.7, sold 17.7, NB 45.8, deaths 0.8, sieges 1.2 | 30 (20/35), 27.5, sold 23.4, NB 58.3, deaths 0.9, sieges 2.3 | 40 (25/45), 36.7, sold 24.3 | 35 / 35.5; 40 / 39.3 |
| 5/8/12, 0.6, 50/0.6 | 20 (15/25), 19.6, sold 19.7, NB 33.8, deaths 0.9, sieges 0.9 | 25 (20/30), 24.7, sold 26.1, NB 39.3, deaths 1.0, sieges 1.8 | 35 (20/40), 31.8, sold 29.1 | 35 / 31.8; 35 / 35.6 |
| 4/6/10, 0.6, 50/0.6 | 20 (15/25), 19.9, sold 19.3, NB 35.7, deaths 0.9, sieges 1.0 | 25 (20/30), 25.2, sold 25.7, NB 42.5, deaths 0.9, sieges 1.9 | 35 (25/40), 32.5, sold 28.2 | 35 / 32.3; 40 / 36.0 |
| 5/8/12, 0.75, 50/0.6 | 20 (15/25), 20.2, sold 18.9, NB 37.7, deaths 0.9, sieges 1.0 | 25 (20/35), 25.6, sold 25.5, NB 45.8, deaths 1.0, sieges 2.0 | 35 (25/40), 33.3, sold 27.4 | 35 / 32.9; 40 / 36.7 |
| 5/8/12, 0.75, 60/0.8 | 20 (15/25), 20.2, sold 18.9, NB 37.9, deaths 0.9, sieges 1.0 | 25 (20/35), 25.6, sold 25.6, NB 45.8, deaths 1.0, sieges 2.0 | 35 (25/40), 33.3, sold 27.5 | 35 / 32.9; 40 / 36.8 |
| **6/10/15, 0.75, 50/0.6 (adopted)** | 20 (15/25), 20.0, sold 19.3, NB 36.3, deaths 0.9, sieges 1.0, WORN_OUT 1.3 | 25 (20/35), 25.4, sold 25.9, NB 43.5, deaths 0.9, sieges 1.9, WORN_OUT 2.7 | 35 (25/40), 33.0, sold 28.0 | 35 / 32.5; 40 / 36.3 |

Reading the table:
- Every variant keeps BALANCED_FAIR at 20 (band 15-25), lifts sales (FAIR +1.2 to +2.0 a run, ACTIVE +2.1 to
  +2.7) and cuts NOT_BETTER by a fifth to a quarter, with hero deaths flat at 0.9-1.0 and 0 hard-lock days.
- Wear costs every policy about 1.5-2 mean days, mostly at the walls: champions' blades are worn by siege day
  (sieges survived 1.2 -> 1.0 for FAIR, 2.3 -> 1.9 for ACTIVE). It narrows ACTIVE's lead over FAIR from 10 to 5
  days at the median (+5.4 mean): the active smith's extra sales are now partly replacements, and it spends cores
  re-honing trade-ins. The "ACTIVE at least 5 above FAIR" target holds, but only just.
- The floor matters more than the wear rate. 0.6 shortens the maxed-legacy ACTIVE run to 35 (from 40); 0.75 keeps
  it at 40, so the gentler floor was adopted together with heavier wear, which makes wear visible sooner (a blade
  reads "worn" after about five fights and "battered" after about ten) at the same run length.
- Threshold and urge barely move anything (60/0.8 against 50/0.6 is identical to the first decimal): the
  worn-power comparison does the work; the urge mainly decides when a purchase is labelled WORN_OUT.
- Upgrade impact under BALANCED_ACTIVE (base 25 / 25.4; median / mean delta): Stalwart Walls +5 / +6.0 (v3 +5 /
  +6.3), Well-Stocked Cellar +5 / +3.2 (+0 / +3.7), Forge Mastery +5 / +1.9 (+0 / +2.8), Thrifty Hands +0 / +1.3,
  Tireless Smith +0 / +0.4, Lucky Hammer +0 / +0.4, Family Savings +0 / +0.1, Known Name +0 / -0.1, all maxed
  +15 / +11.0 (+10 / +11.8). The order is v3's; two more upgrades now register at the median only because the base
  median sits at 25 and the 5-day siege rhythm quantises it.

### Not changed, and open
- `RULES_VERSION` stays 1 (bump with the first release). A given seed plays differently from 0.4.0: worn blades
  change win rolls from the first expedition.
- The returned famous blade (`famous_blade` world event) still arrives at condition 100 although its story says
  "dented and dormant"; making it arrive worn is a one-line decision for the owner.
- `Labels.condition` thresholds (70 / 40) are presentation words, not gameplay numbers, so they stay in the UI
  like the quality words.
- The 10,000-seed table is still v2; all rows above are 1,000 seeds.

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

## Weapon history bounds and schema v2 (2026-10-08, session 4)
Readers of `Weapon.history` (audit of `core/src/main` and `app/src/main`): `Legacy.closeRun` (SOLD/COMMISSION
subjects become a legend's owners), `WorldEvents.fallenOwnerName` (the last LOST/SEIZED subject names who carried a
lost blade), and nothing else; the app never renders it (no `history`/`HistoryEntry` reference in `app/`). Writers
and kinds: FORGED and SIGNATURE (forge), SOLD, EQUIPPED, COMMISSION (market), INHERITED (retirement, inheritance
event), LOST/SEIZED/RECOVERED (hero death), RETURNED (famous blade), COLLECTED (collector), all bounded by ownership
changes, plus VICTORY (one per won expedition) and SIEGE (one per siege a champion's weapon survives), the only
kinds that grow without bound per weapon. Their counts already live in `Weapon.kills/victories/siegesDefended/fame`.

Policy (`persistence/WeaponHistoryCompaction.kt`, called once in `GameEngine.endDay` right after
`EventCompaction`; `BalanceConfig.weaponHistoryCap = 10`, 0 disables): per weapon keep the newest 10 VICTORY/SIEGE
entries and drop the older ones; every other kind, including kinds added later, is kept verbatim (a denylist of
compactable kinds, so a new kind is safe by default). Order is preserved. Keeping the newest N commutes with daily
application, so one filter at the final End Day gives the same list as 400 daily passes; `WeaponHistoryCompactionTest`
plays a 400-day forced-survival pair (cap 10 vs cap 0) and asserts state equality outside histories, per-weapon
`compact(unbounded) == bounded`, identical ownership entries, and identical reader inputs (owners, fallen owner).
No RNG is drawn and no counter is touched, so `BalanceConfig.version` stays 2 (not a semantic balance change).

Measured on the 400-day pair (seed 77, after balance v2): 1,812 -> 1,721 history entries across 930 weapons, max
combat entries per weapon 21 -> 10, 14 weapons were over the cap, encoded save 973,919 -> 961,841 bytes. The
saving is small at 400 days because combat entries are 340 of 1,812 (FORGED is 930, INHERITED 223); what the cap
removes is the unbounded tail on a champion's weapon (one entry per siege, forever). `--perf` (`--runs 200 --seed 1
--perf`, 1,000 days, 2,330 weapons) before: p50 0.26 / p95 0.53 / max 0.94 ms; after, three runs: p50 0.34-0.40 /
p95 0.68-1.01 ms; a control with the cap disabled on the same (by then loaded) machine gave p50 0.39-0.71 ms, so
the daily pass over all weapons is below the run-to-run noise. The remaining growth is the `weapons` map itself
(one FORGED entry and one record per weapon forged, never pruned), noted in PROGRESS.

Schema: still v1. The cap changes no serialised shape (`history` stays `List<HistoryEntry>`, no field added), and
the codec already tolerates fields added with defaults (`ignoreUnknownKeys = true`, defaults fill missing keys), so
no migration step is registered and `SaveCodec.SCHEMA_VERSION` stays 1; a v2 bump with an identity step would only
break the two tests that pin the version. Instead the regression anchor the next bump needs is checked in:
`core/src/test/resources/saves/v1_forced_seed4242_day61.json` is a real mid-run save (forced survival, seed 4242,
60 End Days, 235 weapons, one with 15 combat entries) encoded by the codec as it stood before this change (its own
commit precedes the compaction commit). `SaveFixtureTest` decodes it, checks the captured fields, decode/encode
stability, invariants, that `GameEngine.handle` accepts an End Day on it, and that the 15-entry weapon is bounded to
10 on that End Day with its ownership entries intact. Byte-equality against the fixture is deliberately not asserted
because `encodeDefaults = true` changes the text whenever a defaulted field is added. Room needs nothing: the
envelope version is inside the JSON row (`SaveEntity.schemaVersion` mirrors it but is not read for migration).

`GameEngine.RULES_VERSION` stays 1 although balance v2 changed hero targeting and RNG draw order. It is mixed
into `RngState.seeded(seed, rulesVersion)` and stored on `GameState.rulesVersion`, so a mismatch is detectable at
load. It must flip at the first public release that can meet existing saves, and from then on whenever a change
alters the outcome of an already-saved command sequence (draw order, targeting, formulas; content-only changes go
through `contentVersion`). A run in progress cannot be finished on the old rules (the engine ships only the current
ones) and replaying it on new rules would silently change its past, so the policy is: on load, if
`state.rulesVersion != RULES_VERSION`, end the run with cause "the rules changed" and route to the claim screen;
the legacy (knowledge, upgrades, legends, lineages) is kept and claimed once, run-only state resets, as LOCKED.
Not implemented; until the first release every save is a developer save.

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
- `Pixel art assets/Tiny_Blacksmith_UI_Backgrounds_v3` (dropped 2026-10-08 22:45, session 4): a script-generated 1x
  chrome pack (96x48 panel banners, 24 px nine-slice frames, buttons, 24 px status and 40 px nav icons, 16 px
  tiles, 270x150 title/run-end backdrops, a second siege wall). Not adopted: at phone sizes the current 64 px
  concept icons, the forge scene and the parchment texture are richer, the frames would read as chunky 21 px
  borders over the calm Material surfaces of the decluttered layout, and the banners would replace the one
  persistent forge scene the layout is built around. The importer lists the folder in `PACK_SKIP` so a re-import
  does not silently swap `siege_wall` for the newer file (packs are read in folder order). Reversible: delete the
  entry and run `import_assets.py --pack-all` or widen `PACK_PREFIXES`.
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

## Reputation and loyalty depth (2026-10-08, session 4)
GDD 5 text this implements. LOCKED: "Relaxed economy with manual prices, automatic sales, complex buyer preferences,
suppliers, hero loot and random material events." Unlabelled requirement text in the same section (treated as a
requirement): "Market visitors independently compare affordable displayed weapons to their current gear according to
class, existing stats, elemental preference, traits, current wealth, planned activities, shop reputation/loyalty,
material tastes and relative price." PROPOSED (tunable):
"**Reputation:** Grows through sales, commissions, renowned blades and hero success; affects visitors and willingness
to pay. ... Individual loyalty influences repeat customers and story continuity." and the purchase algorithm's
`utility = equipmentImprovement + classFit + elementTaste + individualPreference + loyalty - pricePenalty`.
Before this session reputation and loyalty only moved visit chance (plus a tiny loyalty utility term); "willingness
to pay" and "repeat customers / story continuity" were unimplemented (plan P2/P6 items).

Design, the smallest that covers both PROPOSED sentences, all numbers appended to `BalanceConfig` (version stays 2:
no existing number changed, and `version` seeds the RNG, so a bump would reshuffle every run):
- **Willingness to pay** (`Market.priceCeilingMultiplier`): the price a hero treats as fair is
  `fair x (1 + min(reputation x 0.01, 0.25) + min(loyalty x 0.03, 0.25))`; the overpricing penalty is measured against
  that ceiling. Fair-priced listings are untouched (penalty already 0), so this only lets a known shop, or a regular,
  accept prices up to 25 % (50 % combined) above the suggested price. Caps keep sixfold prices unsellable
  (`overpricedWeaponIsRejectedWithReason` still passes; combined ceiling <= 1.5x).
- **Repeat customers / story continuity**: commission patrons are drawn with `pickWeighted` at weight
  `1 + min(loyalty, 10) x 0.5` instead of uniformly, so a hero who keeps buying here comes back with requests. The
  existing `loyalty x 0.01 x utilityLoyaltyWeight` term and the loyalty share of visit chance stay as they were.
  Note: `pick` and `pickWeighted` consume one draw each but map it differently, so commission patrons change from
  day 1 even at zero loyalty; that is the feature, not a tuning knob.
- **Gazette** (reused `EventType`s, compaction untouched): `WEAPON_SOLD` keeps `data.price` (the simulator reads it)
  and adds `data.premium` = gold above the base fair price when > 0, with the text "..., N above the going rate on the
  shop's good name."; a buyer or patron with loyalty >= `regularLoyaltyThreshold` (3) is written as "X, a regular of
  the shop,". `suggestedPrice` deliberately stays the base fair price so the UI never shows the weights (GDD 5).
- Not built (DEFERRED by the brief): loyalty steering the hero's choice between shopping and other activities;
  shopping stays a morning errand independent of the day's activity.

Evidence (`ReputationAndLoyaltyTest`, 7 tests): at 190 % of fair with 10,000 gold, regulars (loyalty 10) bought
40/40 seeds, strangers 29/40; the regular received 22 of 58 first commission offers (uniform would be 1/8, expected
6/13); `evaluate()` on the same hero and weapon at 120 % gives penalty > 0 for a stranger and 0 for a regular or at
reputation 25; caps hold at loyalty/reputation 100,000; two identical seeds encode byte-equal after 8 days.

Balance (`:core:simulate --runs 1000 --seed 1`, launch content, new account unless noted; survived = sieges
survived/run):

| Policy | Before: median (p10/p90) mean, sell rate, survived | After: median (p10/p90) mean, sell rate, survived |
|---|---|---|
| RANDOM | 25 (15/40) 26.7, 33 %, 2.0 | 25 (15/40) 26.2, 33 %, 1.9 |
| SAFE_FAIR | 25 (15/30) 23.6, 14 %, 1.5 | 25 (15/30) 23.2, 14 %, 1.5 |
| RECKLESS_FAIR | 25 (15/35) 24.8, 14 %, 1.7 | 25 (15/35) 24.7, 14 %, 1.8 |
| BALANCED_CHEAP | 30 (20/35) 29.6, 14 %, 2.8 | 30 (20/35) 29.2, 15 %, 2.7 |
| BALANCED_EXPENSIVE | 15 (10/20) 14.1, 8 %, 0.2 | 15 (10/20) 13.9, 8 %, 0.2 |
| SYNERGY | 40 (20/50) 38.9, 26 %, 4.4 | 40 (20/50) 38.8, 27 %, 4.4 |
| OVERWORK | 25 (15/35) 24.5, 14 %, 1.7 | 25 (15/35) 24.3, 14 %, 1.7 |
| BALANCED_FAIR | 25 (15/35) 24.0, 14 %, 1.6 | 25 (15/30) 23.7, 14 %, 1.5 |
| BALANCED_FAIR, all upgrades maxed | 40 (25/50) 38.5, 9 %, 2.9 | 40 (25/50) 37.9, 9 %, 2.8 |

Medians and sell rates are unchanged; means drift 0.1–0.6 days from the reshuffled commission patrons. The bot
prices at a fixed factor and never raises prices as reputation grows, so the sell-rate gain the ceiling offers a
player (up to +25 % on a known shop) is invisible to the harness; 1.8x stays beyond the combined ceiling, as intended.
No tuning was needed and no siege number moved.

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

## Balance v4 as merged: wear + fame, whetstone 120/300, warlord pressure 50 (2026-10-09, session 7)
The two v4 branches above were built in parallel against v3 and merged together (`BalanceConfig.version` 4; app
0.5.0). Two of the recommendations from "Balance v3 review at 10,000 seeds" were applied at the same time, both
PROPOSED:
- **Master Whetstone 200/500 -> 120/300**: the cheapest-first buyer now reaches level 2 in every BALANCED_ACTIVE run
  (mean level 2.0, was 1.8); the review measured +0.8 days at half price. The signboard (capped out by
  `maxCustomersPerDay`) and the affix magnitudes are untouched.
- **Warlord pressure 70 -> 50, raid multiplier 1.15 -> 1.0**: at 70 a warlord led one siege in about fifty runs and
  was never beaten in 160,000 runs, so the tribute, the pressure drop and the WARLORD_DEFEATED milestone were dead
  content. Sweep under the merged rules (`--runs 1000 --seed 1 --policy all --noImpact`):

| warlordPressure / raid multiplier | Warlord sieges per run (FAIR / ACTIVE / INVEST) | Beaten per run | BALANCED_ACTIVE | BALANCED_INVEST |
|---|---|---|---|---|
| 70 / 1.15 (v3) | 0.0 / 0.0 / 0.0 | 0.0 | 25 (20/35), 25.9 | 30 (20/40), 29.4 |
| 50 / 1.15 | 0.6 / 0.5 / 0.5 | 0.0 | 25 (15/35), 26.1 | 30 (15/40), 29.0 |
| **50 / 1.0 (adopted)** | 0.6 / 0.5 / 0.5 | 0.0 / 0.0 / 0.1 | 25 (20/35), 26.4 | 30 (20/40), 29.5 |
| 60 / 1.15 | 0.2 / 0.2 / 0.2 | 0.0 | 25 (20/35), 26.3 | 30 (15/40), 29.3 |

The 1.15 raid bonus is what cost the p10 five days: a warlord-led siege on day 10 or 15 against a faction already at
pressure 50 is the siege that ends weak runs, and the pressure alone already makes it the hard one. The warlord now
names and leads that siege (the forecast says who) without adding to it. Beating one stays rare in the first era
(0.1 per run under BALANCED_INVEST, 0.2 with maxed upgrades), which is what a boss should be.

### Evidence for v4 as merged (`--runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE`, launch content)
| Policy | v3 (0.4.0) | v4 (0.5.0) |
|---|---|---|
| BALANCED_FAIR | 20 (15/30), mean 21.7, sold 17.7, survived 1.2, deaths 0.8, gold on hand 590 | 20 (15/25), mean 20.5, sold 19.6, survived 1.1, deaths 0.9, gold on hand 653 |
| **BALANCED_ACTIVE** | 30 (20/35), mean 27.5, sold 23.4, survived 2.3, deaths 0.9, gold on hand 91 | 25 (20/35), mean 26.4, sold 26.9, survived 2.1, deaths 1.0, gold on hand 112 |
| SAFE_FAIR / RECKLESS_FAIR / OVERWORK | 20 (21.3) / 20 (22.4) / 20 (21.8) | 20 (20.2) / 20 (21.0) / 20 (20.5) |
| SYNERGY | 40 (25/45), mean 36.7, sold 24.3 | 35 (25/40), mean 34.0, sold 28.7 |
| BALANCED_INVEST | 35 (20/40), mean 31.5, sold 25.6 | 30 (20/40), mean 29.5, sold 28.3 |
| BALANCED_CHEAP / EXPENSIVE | 25 (25.8) / 10 (12.5) | 25 (24.0) / 10 (12.4) |
| RANDOM | 25 (15/35), mean 24.4 | 20 (15/35), mean 23.0 |
| PASSIVE | 10 | 10 |
| Maxed upgrades, BALANCED_FAIR | 35 (25/45), mean 35.5 | 35 (25/40), mean 33.5 |
| Maxed upgrades, BALANCED_ACTIVE | 40 (30/50), mean 39.3 | 40 (30/45), mean 37.7 |

0 hard-lock days in all 16,000 runs. Shop visits per BALANCED_FAIR / BALANCED_ACTIVE run: NOT_BETTER about 38 / 45
(v3: 46 / 58), TOO_EXPENSIVE 17 / 31, WORN_OUT 1.4 / 2.8. Sales are up by about a tenth over v3 and a quarter of
visits now buy; wear costs every policy 1-2 mean days, mostly at the walls (the champions' blades are worn by siege
day), so the active smith's lead over the plain one is now 5 days median (v3: 10). Upgrade impact under
BALANCED_ACTIVE (median / mean delta): Stalwart Walls +10 / +6.1, Well-Stocked Cellar +5 / +3.4, Forge Mastery
+5 / +2.1, Thrifty Hands +5 / +1.3, Lucky Hammer +5 / +0.6, Tireless Smith +0 / +0.3, Family Savings +0 / +0.1,
Known Name +0 / -0.5, all maxed +15 / +11.3.

### Not changed, and not measured
- The fame price premium sits in `Market.askingPrice` (the going rate), so a renowned blade sold at its suggested
  price is no longer reported as a premium "on the shop's good name".
- Not applied from the review: the signboard effect, affix magnitudes, Known Name. Not measured: v4 at 10,000 seeds;
  wear and fame interacting with returned legends over several eras (unit tests only).

## Balance v5 (pending merge): replays and weapon fates
Built on f73e5a1 (0.5.0, balance v4) in a worktree; `BalanceConfig.version` is still 4 and is bumped once on merge.
Two features: text replays for significant expeditions (GDD 6, 11) and the missing fates of a fallen hero's blade
(GDD 7 PROPOSED "surviving comrades, guild inheritance, merchant resale, monster seizure or permanent loss"). All
numbers PROPOSED.

### Replays for significant expeditions
- **What earns one**: an expedition against an elite (won or lost) and any expedition the hero dies on. Siege deaths
  stay inside the siege replay.
- **How the rounds are derived** (`Battle.fightReplay`, three rounds and an outcome): the hero meets the foe (number
  = the hero's attack power as rolled), the foe answers (number = the encounter's power as rolled; "gave ground" at
  win probability 0.65 or more, "pressed hard" at 0.35 or less, "stood firm" between), then the decisive blow (a win:
  the gold already looted; a rout or a death: the wound already rolled). The outcome line is the event's. Each replay
  carries `kind = EXPEDITION` and the `eventId` of the record it illustrates (ELITE_SLAIN, EXPEDITION_LOST or
  HERO_DIED), so a test can hold it to the record.
- **No RNG**: the builder reads locals of `resolveExpedition` and draws nothing; the wording varies with the odds
  already computed. Proof below.
- **Cap**: `Battle.dayReplays` puts the siege first (it alone feeds the diorama) and keeps the
  `maxExpeditionReplaysPerDay` (3) most significant fights by the priority of their event (death 8, elite slain 7,
  elite rout 3), ties in resolution order. `ctx.replays` is per-day scratch and `GameState.lastResolution` is the only
  place a replay is saved, so a save holds at most 1 + 3 replays of 3 rounds each.
- **Old saves**: `CombatReplay.kind` defaults to SIEGE and `eventId` to null, which is what every replay saved before
  v5 was. `SaveCodec.SCHEMA_VERSION` and `GameEngine.RULES_VERSION` are unchanged.
- **UI**: `DayReportDialog` lists every replay under "From the field" with the existing "N rounds" fold; the diorama
  is fed only when the first replay is the siege. No new screen, panel or dialog, and nothing is computed in the UI.

### Weapon fates on a hero's death
The blade the hero carried meets one fate; spare blades are lost with the hero as before. The two rolls the callers
already made keep their place and their short-circuit; their odds now depend on where and how the hero fell.

| Where the hero fell | Comrades recover | Enemy seizes (when not recovered) | Recovered / seized / left over |
|---|---|---|---|
| On the road, ordinary foe | `weaponRecoveryChance` 0.5 (unchanged) | `weaponSeizureChance` 0.5 (unchanged) | 50 % / 25 % / 25 % |
| To an elite | `eliteRecoveryChance` 0.4 | `eliteSeizureChance` 0.7 | 40 % / 42 % / 18 % |
| On the walls, in a lost siege | `wallsRecoveryChance` 0.7 | `wallsSeizureChance` 0.3 | 70 % / 9 % / 21 % |

Then, inside `Battle.kill`:
1. **Guild inheritance**: if the enemy did not seize the blade and the fallen hero's guild has another living member,
   `guildInheritanceChance` 0.6 that the guildmate it serves best (largest gain over the blade in hand by
   `Market.evaluate`, ties by ID, no draw) takes it instead of the forge or the road. `Market.giveAndEquip` applies
   the rule `Heroes.retire` uses for a mentee: wielded only if it beats their own blade by worn power, else kept as a
   spare.
2. **Comrades** (the existing fate): a recovered blade returns to storage.
3. **Seizure** (existing).
4. **Merchant**: a blade that is left over surfaces with a travelling merchant with chance
   `min(merchantMaxChance 0.7, merchantBaseChance 0.3 + merchantChancePerFame 0.05 x min(fame, weaponFameCap 10))`:
   30 % for an unknown blade, 55 % at fame 5, 70 % from fame 8. Fame raises it, capped twice, never to certainty.
5. **Loss** (existing).

**The merchant** (`Market.resolveMerchant`, End Day step 2 after shelf visits and commissions; no player command, no
RNG). The blade sits at `WeaponLocation.Lost(dayTheHeroFell, "held by a travelling merchant")`
(`WeaponLocation.Lost.WITH_MERCHANT`, `Weapon.isWithMerchant`; no new location type). On End Day
`fell + merchantDelayDays` (2) the merchant arrives and offers it at `Market.askingPrice` on that day and the
following ones, `merchantStayDays` (3) End Days in all. The buyer is the living hero with the highest
`Market.evaluate` utility (zero noise) among those who hold the full price in gold (no trade-in credit), gain power
from it and clear `purchaseUtilityThreshold`; ties by hero ID. The gold leaves the economy: nothing for the smith, no
reputation, no loyalty, and the sale is WEAPON_RESOLD, not WEAPON_SOLD, so the shop tally ignores it. Unsold on the
last day, the blade goes to `Lost(day, "carried off by a travelling merchant")` for good. **Longest stay in the
merchant's hands: delay + stay - 1 = 4 days after the death.** `Invariants.check` flags a blade still held after day
fell + delay + stay, one day of slack so that a delay of 0 stays a legal setting.

| Fate | Location after | History kind | Event (`data["fate"]`) |
|---|---|---|---|
| Recovered | Storage | RECOVERED | WEAPON_RECOVERED (RECOVERED) |
| Inherited | Owned by the guildmate | INHERITED (+ EQUIPPED) | WEAPON_INHERITED (INHERITED) |
| Seized | Lost "seized" | SEIZED | WEAPON_STOLEN (SEIZED) |
| Taken by a merchant | Lost "held by a travelling merchant" | SCAVENGED | WEAPON_LOST (MERCHANT): "... was gone from the field where ... fell." |
| ... arrives | unchanged | none | WEAPON_SURFACED |
| ... resold | Owned by the buyer | RESOLD (+ EQUIPPED) | WEAPON_RESOLD (RESOLD) |
| ... unsold | Lost "carried off by a travelling merchant" | LOST | WEAPON_LOST (LOST) |
| Lost | Lost "lost with <hero>" | LOST | WEAPON_LOST (LOST) |

Subjects are real IDs (weapon, hero, and the fallen hero where one is named). WEAPON_RESOLD joins
`EventCompaction.keptForever` with the other ownership records; WEAPON_SURFACED and WEAPON_RESOLD join the Gazette's
hero lines. **Lost reasons and what can still come back**: "seized" and "lost with <hero>" (both existing) can return
through the Heroic Inheritance event; "held by a travelling merchant" returns by resale within four days and must
never be pruned; "carried off by a travelling merchant" is final (the inheritance event does not match it).

**A seized blade's way back**: it already has one. `WorldEvents` "heroic_inheritance" (weight 2.0 in the 30 % daily
pool, at most 3 a run, 3 days apart) hands a random blade that is Lost "seized" or "lost with ..." to a random living
hero; `seizedWeaponsCanReturnThroughHeroicInheritance` covers it. No second path was added.

**New RNG draws** (COMBAT stream, death path only, after the callers' two rolls; a fate whose chance is 0 draws
nothing): one for the guild's claim, only when the blade was not seized and a living guildmate exists; one for the
merchant, only when the blade was neither recovered, seized nor inherited. At most two per death, none for a
bare-handed hero. The elite and walls odds also change how often the callers' second roll (seizure) happens.

### Numbers
`BalanceConfig` is at the JVM limit of 255 constructor parameter slots (a Double or Long takes two): 176 fields used
250 slots at f73e5a1. Past the limit the class compiles and then fails at load ("Too many arguments in method
signature"), so every v5 number lives in one nested field, `weaponFates: WeaponFatesConfig` (one slot):

| Field | Value | Meaning |
|---|---|---|
| `maxExpeditionReplaysPerDay` | 3 | Fight replays kept in a day's report |
| `wallsRecoveryChance` / `wallsSeizureChance` | 0.7 / 0.3 | Odds for a champion who dies in a lost siege |
| `eliteRecoveryChance` / `eliteSeizureChance` | 0.4 / 0.7 | Odds for a hero who dies to an elite |
| `guildInheritanceChance` | 0.6 | A living guildmate claims an unseized blade |
| `merchantBaseChance` / `merchantChancePerFame` / `merchantMaxChance` | 0.3 / 0.05 / 0.7 | A left-over blade surfaces with a merchant |
| `merchantDelayDays` / `merchantStayDays` | 2 / 3 | Days until the merchant arrives; End Days the blade is on offer |

### Proof that replays draw no RNG
Simulator output on the untouched tree, then after the replay feature alone, compared with Gradle and timing lines
removed:
- `--runs 200 --seed 1 --policy all --noImpact`: **identical** (158 lines; two runs of the untouched tree are also
  identical to each other, so the comparison has no noise floor).
- `--runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE`: **identical** (192 lines: 14 policies, both
  maxed-legacy rows, the per-upgrade impact).

On the finished tree `--noFates` (road odds everywhere, guild and merchant chances 0, so no new draw) gives the same
two outputs again, apart from the new "weapon fates" report line: identical at 200 and at 1,000 seeds. The unit test
`buildingAReplayDrawsNoRng` pins the draw count of an elite win (6) and an elite rout (5).

### Evidence (`--runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE`, launch content)
Median (p10/p90), mean. The v4 column is the untouched tree; its fate counters come from the v5 tree with `--noFates`.

| Policy | v4 (0.5.0) | v5 |
|---|---|---|
| BALANCED_FAIR | 20 (15/25), mean 20.5, sold 19.6, deaths 0.9 | 20 (15/25), mean 20.4, sold 19.6, deaths 0.9 |
| **BALANCED_ACTIVE** | 25 (20/35), mean 26.4, sold 26.9, deaths 1.0 | 25 (20/35), mean 26.4, sold 26.9, deaths 1.0 |
| SYNERGY | 35 (25/40), mean 34.0, sold 28.7 | 35 (25/40), mean 34.0, sold 28.6 |
| BALANCED_INVEST | 30 (20/40), mean 29.5, sold 28.3 | 30 (20/40), mean 29.5, sold 28.2 |
| SAFE_FAIR / RECKLESS_FAIR / OVERWORK | 20 (20.2) / 20 (21.0) / 20 (20.5) | 20 (20.2) / 20 (21.0) / 20 (20.5) |
| BALANCED_CHEAP / EXPENSIVE / REPUTED | 25 (24.0) / 10 (12.4) / 20 (20.3) | 25 (24.0) / 10 (12.4) / 20 (20.2) |
| SAFE_CHEAP / RECKLESS_EXPENSIVE | 25 (23.3) / 10 (12.6) | 25 (23.3) / 10 (12.6) |
| RANDOM / PASSIVE | 20 (15/35), mean 23.0 / 10 | 20 (15/35), mean 23.0 / 10 |
| Maxed upgrades, BALANCED_FAIR | 35 (25/40), mean 33.5 | 35 (25/40), mean 33.6 |
| Maxed upgrades, BALANCED_ACTIVE | 40 (30/45), mean 37.7 | 40 (30/45), mean 37.7 |

0 hard-lock days in all 16,000 runs. Upgrade impact under BALANCED_ACTIVE is unchanged to 0.1 day (Stalwart Walls
+10 / +6.1, Well-Stocked Cellar +5 / +3.3, Forge Mastery +5 / +2.1, Thrifty Hands +5 / +1.2, Lucky Hammer +5 / +0.6,
Tireless Smith +0 / +0.3, Family Savings +0 / +0.1, Known Name +0 / -0.5, all maxed +15 / +11.3).

Blades of fallen heroes per run, and artifact recovery = (recovered + inherited + resold) / (recovered + inherited +
resold + seized + lost), over the blades whose fate settled within the run (a blade still with a merchant at the end
counts in neither). The GDD 15.2 metric, now in the text and JSON reports (`weaponFatesPerRun`,
`artifactRecoveryRate`).

| Run | Recovered | Inherited | Resold | Seized | Lost | Taken by a merchant | Artifact recovery |
|---|---|---|---|---|---|---|---|
| BALANCED_FAIR v4 | 0.41 | 0 | n/a | 0.18 | 0.19 | n/a | 53 % |
| BALANCED_FAIR v5 | 0.36 | 0.00 | 0.02 | 0.25 | 0.11 | 0.06 | 52 % |
| BALANCED_ACTIVE v4 | 0.39 | 0 | n/a | 0.20 | 0.19 | n/a | 50 % |
| BALANCED_ACTIVE v5 | 0.34 | 0.00 | 0.02 | 0.27 | 0.14 | 0.06 | 48 % |
| SYNERGY v4 / v5 | 0.38 / 0.33 | 0 / 0.00 | n/a / 0.03 | 0.18 / 0.25 | 0.17 / 0.10 | n/a / 0.06 | 52 % / 51 % |
| BALANCED_INVEST v4 / v5 | 0.35 / 0.32 | 0 / 0.00 | n/a / 0.02 | 0.21 / 0.28 | 0.17 / 0.10 | n/a / 0.05 | 48 % / 47 % |
| Maxed, BALANCED_FAIR v4 / v5 | 0.36 / 0.31 | 0 / 0.00 | n/a / 0.03 | 0.15 / 0.23 | 0.19 / 0.12 | n/a / 0.08 | 51 % / 48 % |
| Maxed, BALANCED_ACTIVE v4 / v5 | 0.22 / 0.20 | 0 / 0.00 | n/a / 0.03 | 0.10 / 0.15 | 0.10 / 0.04 | n/a / 0.04 | 52 % / 53 % |
| 300 days forced survival, BALANCED_ACTIVE v4 | 5.53 | 0 | n/a | 2.91 | 3.00 | n/a | 48 % |
| 300 days forced survival, BALANCED_ACTIVE v5 | 4.84 | 0.00 | 0.51 | 4.22 | 1.99 | 1.28 | 46 % |

The last two rows are 200 seeds of `--policy BALANCED_ACTIVE --siegeModifier 0 --days 300 --noImpact` (12.6 and 12.8
deaths a run, 136.6 and 135.4 sold): a first-era run sees one death, so the long run is where the fates show. About
two blades in five that a merchant takes are resold; the rest find no hero with the gold and the need.

**Tuning.** Run length does not move with any setting tried (FAIR 20.4-20.5, ACTIVE 26.4, both within 0.1 day of
v4), because a first-era run has under one armed death. What moves is where the blades go. The first elite odds
(recovery 0.35, seizure 0.75) cut artifact recovery to 47 % / 45 % (FAIR / ACTIVE) and 44 % over 300 days: an elite
kills harder, so about 70 % of armed deaths in a long run are elite deaths and the elite row is the common case, not
the exception. 0.4 / 0.7 keeps the contrast (42 % seized against 25 % on the road) and holds recovery within two
points of v4 (52 % / 48 %, 46 % over 300 days). The forge gets about one blade in eight fewer back; the smith's
sales do not notice (19.6 and 26.9 a run, 135.4 against 136.6 over 300 days).

### Two rules that cannot fire yet
- **Guild inheritance**: `guildId` is set only in `Heroes.retire` (the founder, who is retired, and the mentee), so
  a guild has at most one living member at a time and no guildmate is ever alive to inherit. 0 inheritances in the
  16,000 runs above and in 200 runs of 300 days (22.5 retirements a run). The rule is covered by unit tests on built
  states and becomes live the day heroes can join a guild; its effect on balance is then unmeasured (it takes
  recovered blades from the forge as well as lost ones from the road).
- **Death on the walls**: champions are chosen at health 50 or more (`heroWoundedThreshold`) and a lost siege costs
  them 40 (`championSiegeDamageOnLoss`), so none can die there: 0 of 272 deaths in 300 BALANCED_ACTIVE runs. The walls
  odds are reached only in unit tests. Sensitivity, not adopted (1,000 seeds, measured with the first elite odds):

| `championSiegeDamageOnLoss` | BALANCED_FAIR mean, deaths | BALANCED_ACTIVE mean, deaths | Recovered a run (FAIR) | Artifact recovery (FAIR) |
|---|---|---|---|---|
| 40 (today) | 20.4, 0.9 | 26.4, 1.0 | 0.33 | 47 % |
| 55 | 20.3, 1.4 | 26.2, 1.5 | 0.70 | 60 % |
| 70 | 20.0, 3.3 | 25.8, 3.4 | 2.05 | 70 % |

### Commands
```
./gradlew :core:simulate --args="--runs 200 --seed 1 --policy all --noImpact"                       # baseline, replay feature, and with --noFates
./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE"   # same three, and the v5 table (add --json PATH)
./gradlew :core:simulate --args="--runs 200 --seed 1 --policy BALANCED_ACTIVE --siegeModifier 0 --days 300 --noImpact"   # long run, with and without --noFates
./gradlew :core:test                                                                              # 154 tests (140 + 14 in ReplaysAndWeaponFatesTest)
```
`--noFates` is new in the simulator: the v4 rules for a fallen hero's blade, with the v4 draw sequence.

### Not changed, and not measured
- Not changed: spare (unequipped) blades are still lost quietly with the hero; the Heroic Inheritance event; hero
  death rates; `championSiegeDamageOnLoss`; `BalanceConfig.version`, the save schema and the rules version.
- Not measured: 10,000 seeds; guild inheritance and wall deaths in play (see above); the merchant's price and stay
  as levers (only 2 / 3 days at the going rate was run); several eras; the tree merged with the other v5 branches.
- Open: a seized blade returns only through the inheritance event (3 a run at most), and seizure is now the largest
  single loss (4.2 of 12.8 deaths over 300 days). A bounded recovery from the faction that holds it would need the
  seizing faction recorded on the blade, which it is not.
