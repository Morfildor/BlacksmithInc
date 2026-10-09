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

## Balance v5 (pending merge): legacy tracks
GDD 9, LOCKED: "strong permanent upgrades". PROPOSED: the category list ("starting energy, forge/tool mastery,
material efficiency, better starting resources, extra starting gold, enhanced quality/recipe odds, catalog access,
starting shop reputation, legacy artifact opportunities") and "Intentionally noticeable strength growth, with bounded
effects". GDD 8: "Progression cannot guarantee immortality". Eight tracks existed; catalog access, recipe odds and
legacy artifacts had none, and three of the eight (Known Name, Family Savings, Tireless Smith) measured about 0 days.
This section adds the three tracks, changes Known Name, keeps the other two with evidence, and gives the impact table
a second set of yardsticks. `BalanceConfig.version` is not bumped here (the merge does it); `RULES_VERSION` and the
save schema are unchanged and no model field was added (upgrade levels already live in `LegacyProfile.upgrades`).

### The three new tracks (names PROPOSED; three levels each at 8 / 20 / 45)
| Track (ID) | Category | Lever | Level 0 / 1 / 2 / 3 | Bound |
|---|---|---|---|---|
| **Caravan Ties** (`catalog_access`) | catalog access | daily supplier stock of every limited material (4 rare cores, 3 rare augments, 4 catalysts) | 1 / 2 / 3 / 4 units a day (Binding Salt 2 / 3 / 4 / 5) | three levels; gold and energy still limit what is bought; a delayed caravan still empties the shelf |
| **Anvil Lore** (`recipe_odds`) | recipe odds | signature transformation chance of an eligible forge | 0.15 / 0.20 / 0.25 / 0.30 before mastery | the existing `signatureMaxChance` 0.50 is applied after every bonus |
| **Homing Steel** (`legacy_artifacts`) | legacy artifacts | weight of "A Famous Blade Returns", and the share of its old quality and power the blade keeps | weight 1 / 2 / 3 / 4 (of about 42 when every event is eligible); factor 0.70 / 0.75 / 0.80 / 0.85 | still once a run (`maxPerRun` 1); factor capped at 0.85, so never whole (GDD 7); ineligible with an empty Legend Board |

- Why these levers. Every one of the 16 materials is already sold from day 1, so "access" can only mean depth: the
  rare ones are limited to one unit a day (`MaterialDef.dailySupplierStock`), which is what stops a smith with gold
  from forging the same rare recipe twice. The signature roll is one line in `Forge.apply`
  (`base + mastery x 0.01`, capped); the track adds a term inside the same cap. A legend returns through one weighted
  world event with a fixed dormancy factor; the track moves both, within bounds.
- Stacking at the cap: 0.15 + 0.12 (Forge Mastery 3) + 0.15 (Anvil Lore 3) = 0.42; two whetstone levels make 0.48;
  Forgefire on top would be 0.54 and is held at 0.50 (`aSignatureIsNeverCertainWithEveryBonusStacked`: 600 eligible
  forges, about half transform).
- Determinism: no RNG draw was added anywhere. The supplier map, the signature chance, the event weight and the
  dormancy factor are the same Doubles and Ints as before at level 0, so a new account plays byte-identically (see
  Evidence). Homing Steel changes which event a draw picks only while "A Famous Blade Returns" is eligible.
- Code: `GameEngine.restockedSupplier(legacy)` (called by `newRun` and `newMorning`), `Forge.apply`,
  `WorldEvents.weight` / `returnedLegendFactor`, `ResolutionContext.upgradeTotal`, three `UpgradeEffect` values and
  three `UpgradeDef`s in `LaunchContent` (the slice catalog has none of them and ignores a profile that owns them).
  The new tracks count levels (`magnitudePerLevel = 1`); their numbers are in `BalanceConfig`.
- Player-facing text describes the effect in words (GDD round 9); the run-end screen and the Legacy panel list
  `content.upgrades` in a scrolling column, so eleven rows need no UI change, and no screen draws an upgrade icon.

### Numbers (`BalanceConfig.legacyTracks: LegacyTracksConfig`, all PROPOSED)
| Field | Value | Meaning |
|---|---|---|
| `catalogStockPerLevel` | 1 | extra daily units of every limited material per Caravan Ties level |
| `recipeOddsPerLevel` | 0.05 | added to the signature chance per Anvil Lore level |
| `legendReturnWeightPerLevel` | 1.0 | added to the event weight per Homing Steel level |
| `legendQualityFactorPerLevel` | 0.05 | added to `returnedLegendQualityFactor` (0.7) per Homing Steel level |
| `returnedLegendQualityFactorMax` | 0.85 | ceiling of that factor |
| `knownNameRegularGold` | 30 | coin each Known Name regular has saved (see below) |

One nested object, not six flat fields: `BalanceConfig` is at the JVM limit of 255 argument slots per method (176
parameters in 242 slots before v5, a Double taking two; the generated `copy$default` needed 250). Flat fields beyond
the limit compile and then fail every test at class load ("Too many arguments in method signature"). The object costs
one slot (243 / 251 now).

### Known Name, Family Savings, Tireless Smith
Run length is quantised to the 5-day siege rhythm and sales are bound by what heroes can pay, so the impact table now
prints a second table per upgrade (see Simulator below). With it, each of the three was examined.

**Known Name: changed.** +15 starting reputation measured -0.3 days at 10,000 seeds under BALANCED_ACTIVE (26.0 vs
26.4), -0.2 under SYNERGY and +0.1 under BALANCED_INVEST (1,000 seeds). Why: reputation adds visit chance (+7.5 points a
hero at level 3), but the shop serves at most `maxCustomersPerDay` = 4 and a new account already fills 3.9 of them
(102.3 visits over 26.4 days). The upgrade adds 0.1 visits a day, the added visits end TOO_EXPENSIVE (31.3 -> 32.9 a
run) and sales fall (26.7 -> 25.8). Its other effect, the price ceiling, only matters to a smith who prices above fair.
What decides the first week is the heroes' purses (about 85 gold on day 1), so the track now also sets that:
**one starting hero per level (the first by ID) begins as a regular** (loyalty `regularLoyaltyThreshold`, so the
Gazette calls them one and they tolerate a small premium) **with 30 gold saved for a blade**; a run-start record names
them. It stays a starting-reputation perk, draws no RNG and is bounded at three heroes and 90 gold.

| Known Name, BALANCED_ACTIVE, 10,000 seeds | Median (p10/p90) | Mean | Sold by day 5 | Defense at the first siege | First siege held | Sold / run |
|---|---|---|---|---|---|---|
| none | 25 (20/35) | 26.4 | 8.2 | 213.5 | 83 % | 26.7 |
| level 3, reputation only (v4) | 25 (20/35) | 26.0 | 8.3 | 213.3 | 83 % | 25.8 |
| level 3, regulars without savings (`--knownNameGold 0`) | 25 (20/35) | 25.9 | 8.2 | 213.1 | 82 % | 25.6 |
| level 3, 20 gold | 30 (20/35) | 27.2 | 8.8 | 221.8 | 89 % | 27.7 |
| **level 3, 30 gold (adopted)** | 30 (20/35) | 27.8 | 9.0 | 226.2 | 91 % | 28.4 |
| level 3, 40 gold | 30 (20/35) | 28.3 | 9.1 | 230.8 | 94 % | 29.1 |
| level 3, 50 gold | 30 (20/35) | 28.7 | 9.2 | 234.5 | 95 % | 29.5 |
| level 1 / level 2 at 30 gold | 25 (20/35) / 30 (20/35) | 26.9 / 27.3 | 8.5 / 8.8 | 218.5 / 222.1 | 86 % / 89 % | 27.3 / 27.9 |

30 gold puts the track between Thrifty Hands (+1.2) and Forge Mastery (+2.1), with every level above the 0.3-day noise
floor (+0.5 / +0.9 / +1.4). Loyalty alone does nothing the bots can see. Two temporary probes, not kept in the code:
giving the savings to the last three heroes by ID instead of the first three gives 27.6 (so the effect is the purse,
not the place in the queue); raising the daily customer cap by one per level instead gives 27.7 days and 34.7 sales a
run, which is the lever the signboard needs (DECISIONS "Per-tool sweep") and was left for that decision.

**Family Savings: kept as it is.** +300 gold moves a run by +0.2 days (BALANCED_ACTIVE), +0.9 (SYNERGY), -0.4
(BALANCED_INVEST), all at 10,000 seeds, and 0.0 under the FAIR policies, which never spend it. What it buys shows on
the second yardsticks: one more tool level by the first siege under BALANCED_ACTIVE (3.5 -> 4.5; defense 213.5 ->
217.5, held 83 -> 86 %); under SYNERGY two more weapons forged by the first siege, 1.8 more rare units and the first
premium sale 0.4 days earlier (defense 238.0 -> 242.3); under BALANCED_INVEST the first premium sale 0.6 days earlier
but a first siege held less often (69 -> 66 %), because the invest rule turns the gold into blades no first-week hero
can afford. Gold may not buy survival directly (GDD 8, LOCKED: no gold-funded repair), so the smith's gold is worth
what the purchases are worth: 300 gold in the till is worth -0.4 to +0.9 days, 90 gold in three heroes' purses (Known
Name) +1.4. A larger number was tried in v2 without effect; the yardstick, not the magnitude, was wrong.

**Tireless Smith: kept as it is.** +3 energy is +30 % of the LOCKED 10-energy day and buys exactly that much work:
weapons forged by the first siege 21.8 -> 25.4 and per run 104.0 -> 135.6 under BALANCED_ACTIVE (BALANCED_FAIR 25 ->
30 by the first siege; RECKLESS_FAIR signature weapons 0.67 -> 0.83). Days: +0.5 (BALANCED_ACTIVE), -0.5 (SYNERGY),
+0.1 (BALANCED_INVEST) at 10,000 seeds, 0.0 (BALANCED_FAIR) and +0.2 (RECKLESS_FAIR) at 1,000. Sales by the first
siege do not move (8.2 -> 8.1): output is not what limits a run, and no bot spends energy on anything but more Quick
Forges (none uses Advanced Forge, a catalyst, a technique or a second Hone a day). Raising it would mean a larger
share of the locked energy budget for no measured gain.

A side finding from the same table: on the v4 rules a maxed account sold 3.2 weapons by day 5 (a new account: 8.2) and
held the first siege in 72 % of runs (new: 84 %), because its better weapons cost more than first-week heroes hold.
With the regulars a maxed account sells 4.5 and holds 88 %.

### Simulator (`sim/Simulator.kt`)
- `RunStats` gains `firstSiegeDefense`, `firstSiegeHeld`, `forgedByFirstSiege`, `soldByFirstSiege`,
  `toolsByFirstSiege`, `firstPremiumSaleDay` (first sale or commission of a weapon with a tier-4+ core),
  `rareMaterialsBought` (limited-stock supplier units) and `legendsReturned`, all read from commands the driver issues
  and from event records. `Report.yardsticks()` averages them; `UpgradeImpact.yardsticks` carries them in the JSON.
- After the impact lines the CLI prints "Second yardsticks": one row for no upgrade, one per upgrade, one for all
  maxed. The policy reports are untouched, so `--noImpact` output is unchanged.
- `--upgrades id=level[,id=level]` plays the policy rows with that legacy account; `--yardsticks` prints the same table
  for the policy rows; `--knownNameGold N` sweeps the savings.
- `--legends`: a new account and `maxedLegacy` have no Legend Board, so no blade can return in the default tables.
  With the flag the maxed runs, the impact baseline and every impact row carry the same board: the blades remembered
  from 10 new-account runs of the impact policy (`Simulator.veteranLegendBoard`; under BALANCED_ACTIVE 20 blades,
  mean quality 74, mean power 41). Only the board is taken (no journal, lineages or points).

### Evidence
Launch content, seed 1, 1,000 seeds unless noted. Median (p10/p90) mean.

| Row | Before (v4) | After |
|---|---|---|
| New account, all 14 policies (`--policy all`) | BALANCED_FAIR 20 (15/25) 20.5; BALANCED_ACTIVE 25 (20/35) 26.4; SYNERGY 35 (25/40) 34.0; BALANCED_INVEST 30 (20/40) 29.5 | byte-identical: `diff` of the new-account sections of the two outputs finds no differing line |
| Maxed, BALANCED_FAIR | 35 (25/40) 33.5, max 50 | 35 (30/40) 35.8, max 50 |
| Maxed, BALANCED_ACTIVE | 40 (30/45) 37.7, max 55 | 40 (30/45) 39.8, max 55 (10,000 seeds: 40 (30/45) 39.9, max 55) |
| Maxed, BALANCED_ACTIVE, veteran Legend Board (`--legends`) | - | 40 (35/45) 40.0, max 55 |
| Maxed, SYNERGY | 45 (30/50) 42.4, max 60 | 45 (35/50) 44.3, max 55 (10,000 seeds: 45 (35/50) 44.5, max 60) |
| Maxed, BALANCED_INVEST | 40 (25/45) 37.5, max 55 | 40 (30/50) 40.6, max 55 |
| Maxed, RECKLESS_FAIR | not run | 40 (30/40) 36.7, max 50 |
| Hard-lock days | 0 | 0 in every run of this section |

Guard rail: the maxed active smith stays at median 40 (p90 45, longest run 55 of 10,000); the longest-lived maxed
policy is SYNERGY at 45 (p90 50). Every run ends. The whole movement of the maxed rows is Known Name: with the three
new tracks removed from the maxed account (`--upgrades` with the eight old IDs) BALANCED_ACTIVE is the same 40 (30/45)
39.8 and BALANCED_FAIR the same 35 (30/40) 35.8; SYNERGY is 45 (40/50) 44.8 and BALANCED_INVEST 40 (30/45) 39.7.

Upgrade impact, single upgrade at level 3 vs none (median delta / mean delta in days):

| Upgrade | BALANCED_ACTIVE before, 1,000 | BALANCED_ACTIVE after, 1,000 | BALANCED_ACTIVE after, 10,000 | SYNERGY after, 10,000 | BALANCED_INVEST after, 10,000 | BALANCED_FAIR after, 1,000 |
|---|---|---|---|---|---|---|
| Base | 25 / 26.4 | 25 / 26.4 | 25 / 26.4 | 35 / 34.0 | 30 / 29.3 | 20 / 20.5 |
| Tireless Smith | +0 / +0.3 | +0 / +0.3 | +0 / +0.5 | +0 / -0.5 | +0 / +0.1 | +0 / +0.0 |
| Family Savings | +0 / +0.1 | +0 / +0.1 | +0 / +0.2 | +0 / +0.9 | +0 / -0.4 | +0 / +0.0 |
| Forge Mastery | +5 / +2.1 | +5 / +2.1 | +5 / +2.1 | +0 / +0.5 | +5 / +1.4 | +0 / +1.7 |
| Stalwart Walls | +10 / +6.1 | +10 / +6.1 | +10 / +6.1 | +5 / +6.5 | +10 / +6.8 | +5 / +6.0 |
| Thrifty Hands | +5 / +1.3 | +5 / +1.3 | +5 / +1.2 | +0 / +0.2 | +0 / +0.5 | +0 / +1.0 |
| Well-Stocked Cellar | +5 / +3.4 | +5 / +3.4 | +5 / +3.0 | +0 / +0.3 | +0 / -1.1 | +5 / +4.3 |
| Lucky Hammer | +5 / +0.6 | +5 / +0.6 | +0 / +0.6 | +0 / +0.5 | +0 / +0.4 | +0 / +0.5 |
| Known Name | +0 / -0.5 | +5 / +1.3 | +5 / +1.4 | +0 / +1.6 | +5 / +2.4 | +0 / +1.4 |
| Caravan Ties | - | +0 / +0.0 | +0 / +0.0 | +0 / +0.1 | +0 / +0.2 | +0 / +0.0 |
| Anvil Lore | - | +0 / +0.0 | +0 / +0.0 | +0 / +0.0 | +0 / +0.0 | +0 / +0.0 |
| Homing Steel (no Legend Board) | - | +0 / +0.0 | +0 / +0.0 | +0 / +0.0 | +0 / +0.0 | +0 / +0.0 |
| All maxed | +15 / +11.3 | +15 / +13.4 | +15 / +13.5 | +10 / +10.5 | +10 / +11.3 | +15 / +15.3 |

Second yardsticks, BALANCED_ACTIVE, 10,000 seeds (means per run; the first five columns are the day-5 siege and the
work done by it):

| Upgrade (level 3) | Defense | Held | Forged | Sold | Tool levels | First tier-4+ sale | Forged / run | Sold / run | Legacy points |
|---|---|---|---|---|---|---|---|---|---|
| none | 213.5 | 83 % | 21.8 | 8.2 | 3.5 | day 9.5 in 89 % | 104.0 | 26.7 | 28.0 |
| Tireless Smith | 220.0 | 86 % | 25.4 | 8.1 | 3.4 | day 9.4 in 90 % | 135.6 | 27.5 | 28.3 |
| Family Savings | 217.5 | 86 % | 22.9 | 8.2 | 4.5 | day 9.5 in 89 % | 109.5 | 26.7 | 28.3 |
| Forge Mastery | 218.5 | 80 % | 21.2 | 6.2 | 3.2 | day 10.7 in 88 % | 116.1 | 26.8 | 29.6 |
| Stalwart Walls | 213.5 | 83 % | 21.8 | 8.2 | 3.5 | day 9.9 in 92 % | 127.4 | 30.2 | 29.5 |
| Thrifty Hands | 221.6 | 88 % | 22.2 | 8.4 | 3.7 | day 9.3 in 91 % | 115.2 | 28.3 | 28.7 |
| Well-Stocked Cellar | 228.6 | 84 % | 21.4 | 5.1 | 3.2 | day 10.9 in 91 % | 124.5 | 27.3 | 29.7 |
| Lucky Hammer | 214.9 | 83 % | 21.7 | 8.0 | 3.5 | day 9.7 in 89 % | 107.5 | 27.2 | 28.5 |
| Known Name | 226.2 | 91 % | 22.2 | 9.0 | 3.9 | day 8.5 in 94 % | 111.4 | 28.4 | 29.1 |
| All eleven maxed | 249.8 | 88 % | 28.2 | 4.4 | 4.3 | day 12.2 in 97 % | 234.6 | 35.0 | 32.9 |

The three new tracks buy no days under any bot, by construction: no policy forges an exact signature recipe on
purpose, hoards gold while buying rare stock, or starts with a Legend Board. Each is picked up on its own yardstick:

| Track | Yardstick (1,000 seeds) | Level 0 / 1 / 2 / 3 | Days |
|---|---|---|---|
| Anvil Lore | signature weapons a run, RECKLESS_FAIR (the only bot whose risk meets some recipes) | 0.67 / 0.90 / 1.14 / 1.36; maxed account 5.25 with eight tracks, 8.10 with eleven | 21.0 at every level |
| Caravan Ties | limited-stock units bought a run, SYNERGY | 14.7 / 16.4 / 16.6 / 16.7; maxed account 22.6 -> 26.5 | 34.0 at every level |
| Caravan Ties | exact rare-recipe forges in five days with gold to spare (`caravanTiesLetsASmithWithGoldRepeatARareRecipe`) | 6 / 11 / 16 / 21 | - |
| Homing Steel | legends returned a run, BALANCED_ACTIVE with `--legends` | 0.19 / - / - / 0.55 (maxed account 0.70) | 26.4 at level 0 and 3 |
| Homing Steel | runs of 150 in which the one legend returned within 20 days, BALANCED_FAIR (unit test numbers) | 22 / 33 / 48 / 61 | - |

Caravan Ties is a choice-widening upgrade, not a strength one: every purchasing bot is bound by gold, not stock.
Depth lets the naive affinity bot overbuy 198-gold blades (SYNERGY with Family Savings, 2,000 seeds: 35.2 -> 34.0;
maxed account, 1,000 seeds: 44.8 -> 44.3) and helps the invest rule a little (with Family Savings, 2,000 seeds:
28.8 -> 29.7; maxed account, 1,000 seeds: 39.7 -> 40.6).
A returned legend is one mid-run weapon at 70-85 % of its old power; it does not move a run (GDD 7: it must not erase
progression difficulty).

Tests: 153 JVM tests pass (140 + 13 in `LegacyTracksTest`); `:app:compileDebugKotlin` succeeds.

### Commands
```
./gradlew :core:test
./gradlew :app:compileDebugKotlin -q
./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE"      # before and after
./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy P --impactPolicy P"                      # P = BALANCED_FAIR, BALANCED_INVEST, SYNERGY, RECKLESS_FAIR (SYNERGY and BALANCED_INVEST also before)
./gradlew :core:simulate --args="--runs 10000 --seed 1 --policy P --impactPolicy P"                     # P = BALANCED_ACTIVE (before and after), SYNERGY, BALANCED_INVEST
./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy BALANCED_ACTIVE --impactPolicy BALANCED_ACTIVE --legends"
./gradlew :core:simulate --args="--runs 10000 --seed 1 --policy BALANCED_ACTIVE --noImpact --yardsticks --upgrades shop_reputation=L --knownNameGold G"   # L 1-3, G 0/20/30/40/50
./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy P --noImpact --yardsticks --upgrades recipe_odds=L"      # P = RECKLESS_FAIR; catalog_access=L under SYNERGY
./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy P --noImpact --yardsticks --upgrades <eight old IDs>=3[,<three new IDs>=3]"   # maxed with and without the new tracks
./gradlew :core:simulate --args="--runs 2000 --seed 1 --policy P --noImpact --yardsticks --upgrades starting_gold=3[,catalog_access=3]"      # P = SYNERGY, BALANCED_INVEST
```

### Not changed, and not measured
- Not changed: tier costs, the eight existing magnitudes, `maxCustomersPerDay`, the visiting order, `maxPerRun` of the
  legend event. `SliceContent` keeps its four tracks. `Market.kt`, `Heroes.kt` and `Battle.kt` are untouched.
- Not measured: a bot that hunts signatures or repeats a rare recipe (Advanced Forge, catalysts, several units a day),
  so Anvil Lore and Caravan Ties have no run-length number; Homing Steel on a real multi-era account (a board that
  grows, lineages, a known journal) rather than the 10-run veteran board; the per-level sweeps of the new tracks at
  10,000 seeds; anything on a device (the two upgrade lists were checked in code and by compiling only); the
  interaction with the hero-activity and replay / weapon-fate work built in parallel.
- Open: Family Savings and Tireless Smith still buy about 0 days. Their value is visible on the second yardsticks and
  to a player; if run length must move, the evidence above says the lever is hero purchasing power or a bot that
  spends energy on Advanced Forge, not a larger number on either track.
- Noticed, not touched: the Traveling Ore Merchant adds 2 to the supplier's stock at End Day, and the morning restock
  that follows overwrites it, so only the free unit it brings is ever seen.
