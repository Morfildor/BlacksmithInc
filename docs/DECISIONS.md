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
  (superseded: the signboard, the affix magnitudes and Known Name were applied in v5, and v4 and v5 were reviewed at 10,000 seeds, see
  "Balance v5 review at 10,000 seeds" below)
  wear and fame interacting with returned legends over several eras (unit tests only).

## Weapon pruning and day-report recovery (session 8, no balance change)

**What a long save is made of** (forced survival, seed 77, `Weapon` JSON bytes are 52-57 % of the save):

| Policy, days | Weapons | Storage | Destroyed | Given to the watch | Lost with a hero / seized | Owned + shelf | Save bytes |
|---|---:|---:|---:|---:|---:|---:|---:|
| BALANCED_FAIR, 400 | 1,242 | 1,157 | 0 | 0 | 52 | 31 | 1,207,457 |
| BALANCED_FAIR, 1,000 | 3,140 | 2,932 | 0 | 0 | 184 | 22 | 2,933,776 |
| BALANCED_ACTIVE, 400 | 1,727 | 701 | 713 | 233 | 10 | 68 | 1,745,453 |
| BALANCED_ACTIVE, 1,000 | 4,524 | 1,901 | 1,871 | 568 | 96 | 86 | 4,426,371 |

**Rule** (`persistence/WeaponPruning.kt`, called in End Day after the history cap; `weaponRetentionDays = 30`, 0 = off):
a weapon leaves the map when it is `Destroyed` (salvaged or shattered) or `Lost` for a terminal reason ("given to the
town watch", "sold to a collector"), that happened at least 30 days ago, its fame is below `legendFameThreshold` and it
is not a signature weapon. Everything else stays: a blade lost with a hero or seized is read by the Heroic Inheritance
event and can come home, and a `Lost` reason added later is kept until it is classified
(`WeaponPruningTest.everyLostReasonIsClassified` fails on an unknown reason). Weapon IDs come from a serial counter,
so pruning cannot cause a collision.

**Evidence that play is unchanged:** `WeaponPruningTest` plays the active smith for 400 forced-survival days with and
without pruning: every field of the state except the weapons map is equal, the surviving weapons are equal and in the
same order, exactly the prunable set is gone, the Legend Board candidates match. Weapons 1,727 -> 857, save
1,745,453 -> 1,012,913 bytes (-42 %). `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy
BALANCED_ACTIVE` before and after: every survival, sales, visit and upgrade-impact line is identical; only the
`affix weapons/run` line moves (it counted the end-of-run map; to be counted at forge time).

**Not done, on purpose:** unsold storage is the plain smith's whole problem (93 % of weapons at day 1,000) and it is
the player's property, so it is not pruned; a storage cap would be a design decision. Blades lost with heroes grow by
about one every five days and stay because a rule can still read them. `WEAPON_INHERITED` and the other kept-forever
event types still grow with the run (695 of 2,099 events at day 1,000).

**Day-report recovery (GDD 3.3):** the engine already saved the last `DayResolution` with the run; the app now stores
the command ID of the last report the player closed (DataStore, not gameplay state) and reopens an unread one on
launch, including the final report of a fallen forge. Checked on the emulator: End Day, kill with the report open,
relaunch (report back), close, kill, relaunch (stays closed, Home on day 2).

## Balance v5, part 1: signboard and affix magnitudes (session 8)

Two recommendations of the v3 review (items 1 and 6 above), applied on top of v4 as merged. Catalog numbers
(`LaunchContent`), one engine line (`Market.resolveShelfVisits`). All runs: 1,000 seeds, seed 1.

### Signboard: +1 customer a day per level (was +8 points of visit chance)
`ToolEffect.HERO_VISIT_CHANCE` is replaced by `ToolEffect.EXTRA_CUSTOMERS` (magnitude 1 per level, prices 150 / 400
unchanged); the tool total is added to `maxCustomersPerDay` and no longer to the visit chance. Guild Patronage (a
blessing) still raises the visit chance.

| BALANCED_ACTIVE | Median (p10/p90) | Mean days | Sold/run | Gold earned (median) | Visits/run (per day) | Elites slain |
|---|---|---|---|---|---|---|
| No signboard (`--noTool signboard`) | 25 (20/35) | 26.5 | 27.8 | 2,403 | 99.3 (3.75) | 3.6 |
| v4: +8 points visit chance | 25 (20/35) | 26.4 (-0.1) | 26.9 (-0.9) | 2,319 | 102.5 (3.88) | 3.6 |
| **v5: +1 customer per level** | 25 (20/35) | **26.9 (+0.4)** | **31.3 (+3.5)** | 2,706 | 127.3 (4.73) | 3.9 |

- The v4 signboard was still a trap (worse than not buying it); the v5 one is worth +0.4 days and +3.5 sales over no
  signboard, bought in 100 % of runs on day 2.2 (mean level 1.7), the same order as before.
- In gold alone it returns about 300 of the roughly 430 spent on it in a 27-day run: level 1 (150) pays, level 2
  (400) is a sales and survival purchase, not a profit. Left as is; the lever is the level-2 price.
- Only the tool buyer moves: every other policy is identical to v4 (none buys tools).
- `GameplayDepthTest.theSignboardLetsOneMoreCustomerInPerLevel`: on the same seed the signed shop sees 0 to 2 more
  visitors than the plain one, and 2 on a busy day.

### Affix magnitudes
| Affix | v4 | v5 |
|---|---|---|
| Giant Slayer, attack against elites and warlord sieges | x1.25 | x1.5 |
| Reinforced, wound of a lost expedition | x0.85 | x0.7 |
| Swift, wound of a lost expedition | x0.8 | x0.6 |
| Heavy (flaw), wound of a lost expedition | x1.2 | x1.5 |
| Cursed (flaw), health lost per won expedition | 4 | 15 |
| Bloodbound (flaw), health lost per won expedition | 7 | 20 |

Kept: Undead Bane x1.2 (the review: it already registers once common). Not done: Lucky (its loot should be
something scarce; that line is in `Battle.resolveExpedition`, which a feature branch is editing; after the merge).

What each effect is worth, measured the review way (the effect neutralised with `--noAffixEffect <id>`, delta against
the baseline of the same build; signboard v5 in both columns):

| Effect | Carried / run (ACTIVE / SYNERGY) | BALANCED_ACTIVE v4 | BALANCED_ACTIVE v5 | SYNERGY v4 | SYNERGY v5 |
|---|---|---|---|---|---|
| Giant Slayer, elite kills a run | 4.9 / 6.9 | +0.26 of 3.87 | **+0.42 of 3.96** | +0.26 of 5.84 | **+0.32 of 5.90** |
| Reinforced, deaths a run | 4.8 / 7.0 | -0.04 | **-0.09** | -0.04 | -0.06 |
| Swift, deaths a run | 4.9 / 6.9 | -0.05 | -0.05 | -0.08 | -0.08 |
| Heavy, deaths a run | 1.5 / 1.1 | +0.03 | **+0.07** | +0.01 | **+0.06** |
| Cursed, deaths a run | 1.6 / 1.2 | 0.00 | 0.00 | 0.00 | -0.01 |
| Bloodbound, deaths a run | 1.5 / 1.1 | +0.02 | 0.00 | +0.01 | +0.02 |
| All ten effects, mean days | - | +0.5 | +0.7 | +1.0 | +0.7 |

| Baseline of each build | BALANCED_ACTIVE | SYNERGY |
|---|---|---|
| v4 magnitudes | 25 (20/35), mean 26.9, sold 31.3, deaths 0.92 | 35 (25/40), mean 34.0, sold 28.7, deaths 0.92 |
| v5 magnitudes | 25 (20/35), mean 27.2, sold 31.6, deaths 0.94 | 35 (25/40), mean 33.8, sold 28.5, deaths 0.96 |

- Giant Slayer is now worth about a tenth of the elite kills of an active smith (was 7 %), Reinforced and Heavy move
  deaths twice as much as before. Swift did not register a change at 1,000 seeds; Cursed and Bloodbound still do not
  show in deaths or run length: the wielder rests the health back (at 15-20 health a win a hero is under the wounded
  threshold of 50 after three or four wins instead of eight or more). The simulator has no per-wielder or expedition-count
  metric, so that cost is not measured here.
- No policy moved by more than 0.3 mean days, no median or p10/p90 moved: the aggregate stays inside the noise floor
  (about 0.3 days at 1,000 seeds), as the review predicted (each of these lands on 1-5 of about 113 weapons a run).
  The change is felt per weapon, not per run.
- Stacking: an Etched blade can carry Reinforced and Swift together (wound x0.42, 8-23 health instead of 20-55).
  Rare (two exceptional affixes on one weapon) and bounded by the win roll, left uncapped.

### Baseline for the feature branches (v5 part 1, `--policy all --impactPolicy BALANCED_ACTIVE`)
| Policy | v4 as merged | v5 part 1 |
|---|---|---|
| BALANCED_FAIR | 20 (15/25), mean 20.5, sold 19.6 | 20 (15/25), mean 20.5, sold 19.6 |
| BALANCED_ACTIVE | 25 (20/35), mean 26.4, sold 26.9 | 25 (20/35), mean 27.2, sold 31.6 |
| SYNERGY | 35 (25/40), mean 34.0, sold 28.7 | 35 (25/40), mean 33.8, sold 28.5 |
| BALANCED_INVEST | 30 (20/40), mean 29.5, sold 28.3 | 30 (20/40), mean 29.6, sold 28.5 |
| BALANCED_FAIR, all upgrades | 35 (25/40), mean 33.5 | 35 (25/40), mean 33.9, sold 27.3 |
| BALANCED_ACTIVE, all upgrades | 40 (30/45), mean 37.7, sold 30.6 | 40 (30/45), mean 39.5, sold 40.1 |

0 hard-locks in every policy. Upgrade impact under BALANCED_ACTIVE (median / mean days): Tireless Smith +5 / +0.6
(was +0 / +0.3), Family Savings +5 / +0.3 (was +0 / +0.1), Forge Mastery +5 / +2.8, Stalwart Walls +10 / +6.4, Thrifty
Hands +5 / +1.6, Well-Stocked Cellar +5 / +3.5, Lucky Hammer +5 / +0.6, Known Name +0 / 0.0 (was -0.5), all maxed
+15 / +12.3. With a fifth and sixth customer a day sales are less demand-bound, so starting energy and gold register
for the first time.

- The active smith now leads the plain one by 6.7 mean days (was 5.9); the medians are 25 and 20 as before. The wear
  margin question (PROGRESS, known limitations) is unchanged and still open.
- The three feature branches measured against v4 as merged (20.5 / 26.4); after the merge they are compared against
  this table.
## Balance v5, part 2: hero daily life (2026-10-09, session 8)
GDD 6 (PROPOSED utility model) names six activities: rest, shop, expedition, patrol/defense, guild/mentor and personal
ambition, scored from traits, wounds, money, quest availability, faction pressure and prior history. Up to v4 the
daily choice scored expedition, patrol and rest; guilds and mentoring were retirement rules and an ambition was a
tilt on the expedition or patrol weight. This branch makes GUILD and AMBITION scored daily choices and adds money and
prior history as inputs. Every number is PROPOSED. The branch was measured at config version 4 and merged into v5. `RULES_VERSION` and the save schema are unchanged.

### Why guild founding had to move
The brief for this work allowed widening who may train at an existing hall if membership proved too rare. A probe of
the v4 rules (300 seeds, a temporary test, not kept) showed that halls themselves are the rare thing:

| v4 rules | Runs that ever have a guild | First guild, mean day (of mean run length) | Hero-days with a hall in town | Hero-days lived by a guild member |
|---|---|---|---|---|
| BALANCED_FAIR | 41 % | 18.6 (of 20.9) | 17 % | 3.2 % |
| BALANCED_ACTIVE | 69 % | 19.3 (of 26.8) | 33 % | 8.3 % |

A guild appeared only when a famous hero retired, about two days before the forge fell, and its only living member
was the retiree's mentee, who never had a guildmate to learn from. Opening existing halls to everyone would have
produced hall days in the last two days of fewer than half the BALANCED_FAIR runs. A hero with fame 3
(`guildFameThreshold`, the existing number) appeared in 299 of the 300 BALANCED_FAIR runs, on day 4.5 on average, so
founding now also happens in life (GDD 6 LOCKED: heroes "join/found guilds" during a run). At 1,000 seeds a guild
stands at the end of 91 % of BALANCED_FAIR runs (v4: 38 %).

### What was built
- **The choice** (`Heroes.activityWeights`): a healthy hero's viable activities and their weights, drawn with one
  `RngStream.HEROES` draw per hero as before. Expedition, patrol and rest keep their v3 formulas. GUILD is listed
  only when the hero has a hall to go to, AMBITION only while the ambition is unfulfilled. A wounded hero (health
  under 50) still rests without a choice; dead and retired heroes are never asked.
- **GUILD**: open to a guild's members, to any hero once a guild stands in town (the first day there enrols them in
  the oldest guild, record `GUILD_JOINED`), and to a guildless hero with fame 3+ while the town has no guild (the day
  founds "the <surname> Company", the existing `GUILD_FOUNDED` record). Weight 0.35 + trait weights, floor 0.02. The
  day gives 20 XP and 10 health; no gold, no suppression, no militia, no forge recovery. Record `GUILD_TRAINED`.
- **Mentoring**: after everyone has acted, each hero who trained that day is taught by the highest-level guildmate
  who trained the same day and outranks them (ties by ID; no RNG): +15 XP, at most once a day per pupil, record
  `GUILD_MENTORED` with pupil and mentor as subjects. `Hero.mentorName` takes the first mentor and is not
  overwritten (a retiree's mentee keeps the retiree). A mentor may teach several pupils and gains nothing. A
  mentored hall day (35 XP) stays under one won expedition (40).
- **AMBITION** replaces the v3 tilt (SLAYER +0.6 expedition, FORTUNE +0.3 expedition, DEFENDER +0.6 patrol). The
  existing `ambitionActivityWeight` 0.6 is now the weight of the activity itself, for all four ambitions. One record,
  `AMBITION_PURSUED` (data `ambition`), per day:
  - SLAYER hunts: an expedition with +0.30 on the elite roll (`Battle.resolveExpedition(..., eliteChanceBonus)`, the
    same single roll, no draw added or moved). Wins count toward the vow as before.
  - DEFENDER drills the watch: +5 militia (a patrol adds 3), capped at `militiaMax`; no suppression, pay, XP or forge
    recovery.
  - COLLECTOR and FORTUNE take paid guard work: +40 gold (a patrol pays 12); nothing else. They share the action; the
    record says which goal it serves.
  Fulfilment rules (`resolveAmbitions`, `fulfilAmbition`) are untouched.
- **Money**: an unarmed hero holding under 60 gold adds 0.6 to the patrol weight (the town pays for patrols).
- **Prior history**: new field `Hero.drivenBackOnDay` (default null), written by `Heroes` when a hero survives a lost
  expedition or hunt. The day after, rest +0.5 and the hall +0.4. Older routs do not count.
- **Traits** (`TraitDef.guildWeight`, both catalogs): Patient +0.6, Loyal +0.4, Curious +0.4, Greedy -0.2; Restless
  -0.2 (launch catalog only, the slice has no Restless).
- **Gazette**: `GUILD_TRAINED` is quiet news and folds into one shared line ("At the guild hall: A, B."), between
  "On the walls:" and "Resting:". `GUILD_JOINED`, `GUILD_MENTORED` and `AMBITION_PURSUED` join the hero's sentence.
  None of the four is kept forever by `EventCompaction`; `GUILD_FOUNDED` already was.
- **Simulator**: per-run `activityDays` (what each hero alive at End Day did, read from the day's records: a hunt
  counts as AMBITION_SLAYER, a wounded hero's forced rest as REST_WOUNDED), hero-days, level-ups, mentorings and
  guilds; the report prints the shares.

### Numbers (`HeroLifeConfig`, reached as `BalanceConfig.heroLife`)
| Field | Value | Meaning |
|---|---|---|
| `guildBaseWeight` | 0.35 | hall weight before traits |
| `guildXp` / `guildHeal` | 20 / 10 | a day at the hall |
| `mentorXp` | 15 | extra for the pupil, once a day |
| `slayerHuntEliteChance` | 0.30 | added to the elite roll of a hunt |
| `defenderDrillMilitia` | 5 | militia from a drill |
| `ambitionWorkGold` | 40 | gold from a day of paid work |
| `poorHeroGold` / `poorPatrolWeight` | 60 / 0.6 | unarmed and under this gold: added to patrol |
| `setbackRestWeight` / `setbackGuildWeight` | 0.5 / 0.4 | driven back yesterday: added to rest / hall |

**`BalanceConfig` has no room for flat fields.** The JVM allows 255 parameter slots per method and a Double takes
two. v4's 176 constructor parameters use 242 slots, and the generated `copy$default` adds 8 (six default masks, the
instance, a marker): 250. Eleven more flat numbers (16 slots) compile and then fail at class load with
"ClassFormatError: Too many arguments in method signature". The numbers therefore sit in a small data class,
`HeroLifeConfig`, in `BalanceConfig.kt`, held by one field at the end of `BalanceConfig`. Four slots are left after
this branch (two Doubles, or four Ints); other branches that add flat numbers will hit the same wall on merge and
should group theirs the same way.

### Evidence (launch content, 1,000 seeds, seed 1)
Commands, both from the repository root with output redirected to a file:
`./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy all --noImpact"`, once on v4 rules with only the new
simulator counters applied (survival, sales and deaths identical to the v4 table above) and once on this branch.

| Policy | v4 (0.5.0) | v5 |
|---|---|---|
| BALANCED_FAIR | 20 (15/25), mean 20.5, sold 19.6, survived 1.1, deaths 0.9, retired 0.7, level-ups 14.8, elites 2.7 | 20 (15/25), mean 20.5, sold 19.3, survived 1.1, deaths 0.7, retired 0.5, level-ups 15.1, elites 2.7 |
| BALANCED_ACTIVE | 25 (20/35), mean 26.4, sold 26.9, survived 2.1, deaths 1.0, retired 1.8, level-ups 22.9, elites 3.6 | 25 (20/35), mean 26.3, sold 26.4, survived 2.2, deaths 0.8, retired 1.4, level-ups 23.5, elites 3.7 |
| SYNERGY | 35 (25/40), mean 34.0, sold 28.7, survived 3.6, deaths 0.9, retired 3.9, level-ups 37.2, elites 5.8 | 35 (25/40), mean 34.6, sold 28.1, survived 3.7, deaths 0.8, retired 3.6, level-ups 39.1, elites 5.9 |
| BALANCED_INVEST | 30 (20/40), mean 29.5, sold 28.3, survived 2.6, deaths 0.9, retired 2.8, level-ups 29.6, elites 4.9 | 30 (20/40), mean 30.0, sold 28.5, survived 2.7, deaths 0.7, retired 2.6, level-ups 31.2, elites 5.1 |
| SAFE_FAIR / RECKLESS_FAIR / OVERWORK | 20 (20.2) / 20 (21.0) / 20 (20.5) | 20 (20.2) / 20 (21.3) / 20 (20.7) |
| BALANCED_CHEAP / EXPENSIVE | 25 (24.0) / 10 (12.4) | 25 (23.6) / 10 (12.8) |
| SAFE_CHEAP / RECKLESS_EXPENSIVE | 25 (20/30), 23.3 / 10 (12.6) | 20 (20/30), 22.7 / 10 (13.0) |
| BALANCED_REPUTED / RANDOM / PASSIVE | 20 (20.3) / 20 (15/35), 23.0 / 10 | 20 (20.3) / 25 (15/35), 23.3 / 10 |

0 hard-lock days in all 14,000 runs on either side. Every mean is within 0.6 days of v4 and BALANCED_FAIR and
BALANCED_ACTIVE within 0.1; the two medians that moved (SAFE_CHEAP 25 to 20, RANDOM 20 to 25) are the 5-day siege
quantum with means 0.6 and 0.3 apart. Hero deaths fall by 0.1 to 0.2 a run for every policy and retirements by 0.2
to 0.5 where there were any; heroes go on fewer expeditions (37 % of hero-days against 43 %). Sales fall by 1.2 to
1.3 a run for the two cheap policies, rise by 0.7 to 0.8 for the two expensive ones and move by -0.6 to +0.3 for the
rest (the v3 noise floor is 0.3). Shop visits per BALANCED_FAIR / BALANCED_ACTIVE run: TOO_EXPENSIVE
16.7 / 31.1 to 15.3 / 29.1, NOT_BETTER 37.7 / 46.0 to 39.6 / 48.1. Legacy points (median) are unchanged for the four
policies in the table.

How heroes spend their days (share of hero-days, per cent; hero-days = heroes alive at End Day, summed over the run):

| Policy | Hero-days per run | Expedition | Patrol | Rest (chosen) | Rest (wounded) | Guild hall | Hunt (SLAYER) | Drill (DEFENDER) | Paid work (COLLECTOR) | Paid work (FORTUNE) | Mentorings per run | Guilds per run | Runs ending with a guild |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| BALANCED_FAIR v4 | 165.7 | 43.4 | 29.4 | 8.5 | 18.7 | 0 | 0 | 0 | 0 | 0 | 0 | 0.7 | 38 % |
| BALANCED_FAIR v5 | 167.5 | 37.1 | 25.0 | 7.9 | 16.0 | 6.7 | 1.7 | 1.9 | 1.6 | 2.2 | 2.4 | 1.1 | 91 % |
| BALANCED_ACTIVE v4 | 215.2 | 43.7 | 30.4 | 9.0 | 16.9 | 0 | 0 | 0 | 0 | 0 | 0 | 1.7 | 67 % |
| BALANCED_ACTIVE v5 | 217.2 | 37.4 | 25.6 | 8.1 | 14.4 | 7.9 | 1.6 | 1.8 | 1.1 | 2.2 | 3.9 | 1.4 | 96 % |
| SYNERGY v4 | 283.6 | 45.1 | 31.8 | 9.4 | 13.7 | 0 | 0 | 0 | 0 | 0 | 0 | 3.7 | 89 % |
| SYNERGY v5 | 290.4 | 38.5 | 26.8 | 8.3 | 11.1 | 9.1 | 1.4 | 1.7 | 1.0 | 2.1 | 6.5 | 1.8 | 99 % |
| BALANCED_INVEST v4 | 243.7 | 44.5 | 31.8 | 9.1 | 14.6 | 0 | 0 | 0 | 0 | 0 | 0 | 2.7 | 76 % |
| BALANCED_INVEST v5 | 250.1 | 38.1 | 26.6 | 8.1 | 12.1 | 8.4 | 1.5 | 1.9 | 1.1 | 2.2 | 5.0 | 1.7 | 97 % |

In a BALANCED_FAIR run that is about 11 hall days, 2.4 lessons and 12 ambition days. Long runs end with fewer guilds
than before (SYNERGY 3.7 to 1.8): heroes join the standing company, and a retiring member no longer founds one of
their own. The hall is rare under policies whose heroes never earn a name (PASSIVE 0.5 % of hero-days, a guild in
9 % of runs; BALANCED_EXPENSIVE 2.1 %, 37 %).

Tuning sweep (`--policy BALANCED_FAIR` and `--policy BALANCED_ACTIVE`, same seeds; mean days / sold / hall share /
mentorings per run):

| `guildBaseWeight` / `ambitionWorkGold` | BALANCED_FAIR | BALANCED_ACTIVE |
|---|---|---|
| v4 | 20.5 / 19.6 / - / - | 26.4 / 26.9 / - / - |
| 0.20 / 25 | 20.2 / 19.1 / 5.1 % / 1.4 | 25.9 / 25.9 / 6.1 % / 2.5 |
| 0.35 / 25 | 20.2 / 19.1 / 6.6 % / 2.3 | 25.9 / 25.5 / 7.7 % / 3.7 |
| 0.50 / 25 | 20.2 / 19.2 / 7.9 % / 3.2 | 25.8 / 25.3 / 9.3 % / 5.1 |
| **0.35 / 40 (adopted)** | 20.5 / 19.3 / 6.7 % / 2.4 | 26.3 / 26.4 / 7.9 % / 3.9 |

Run length did not move with the hall weight between 0.2 and 0.5, so 0.35 was kept for how often the hall and its
lessons are seen. Raising the pay for guard work from 25 to 40 gold brought back the 0.3 to 0.5 days the first three
rows lost and part of the sales (BALANCED_ACTIVE 25.5 to 26.4, against v4's 26.9). Which of the other changes cost
those days (ambition days in place of the tilt, the money input or the history input) was not isolated.

Measured shifts in choice (one morning, 40 seeds x 8 heroes = 320 hero-days; asserted in `HeroDailyLifeTest`):
- Trait: members with only Patient are at the hall on 30.0 % of days, with no trait 16.9 %, with only Greedy 4.4 %.
- Money: unarmed heroes with 0 gold patrol on 50.0 % of days, with 500 gold 41.6 %.
- Prior history: healthy guild members driven back the day before rest or train on 34.1 % of days, others 24.7 %
  (rest 11.9 % against 7.8 %, hall 22.2 % against 16.9 %).
- Ambition: with every hero a slayer on a hunt (30 seeds, 240 fights), 104 fights are against an elite; with the
  hunt bonus at 0 it is 44; at 1.0 all 240.

### Not changed, and not measured
- Quest availability, the one GDD input left, is not scored (commissions are the smith's quests, not the heroes').
  Shop stays a morning errand outside the weighted choice, as before.
- COLLECTOR and FORTUNE share one action. A collector buying a prized weapon as the ambition day was considered and
  left out: it would be a second purchase path outside `Market.resolveShelfVisits`.
- Guild founding in life changes two things beside the hall: `GUILD_FOUNDED` (kept forever by the event log) now
  appears in nearly every run, and the Forgotten Guild Banner world event (eligible once a guild stands) can fire in a
  first era. The retirement rule itself is unchanged: a retiree without a guild and with fame 3+ still founds one.
- The content version is not bumped for `TraitDef.guildWeight`; bump it with the merge if the other branches change
  content too.
- Not measured: 10,000 seeds; maxed-legacy accounts and the per-upgrade impact (`--noImpact` was used throughout);
  ambitions fulfilled per run (paid work at 40 gold should complete fortunes sooner; legacy point medians did not
  move); the slice catalog under the simulator (it has no elites, so a slayer's hunt there is a plain expedition).
- The UI shows the new days only through the Gazette. No screen reads `Hero.guildId`, `mentorName` or `lastActivity`.

### As merged on top of v5 part 1 (1,000 seeds, seed 1, `--policy all --impactPolicy BALANCED_ACTIVE`)
The branch measured against v4 as merged; main had since taken the signboard and affix changes. Both together:

| Policy | v5 part 1 | + hero daily life |
|---|---|---|
| BALANCED_FAIR | 20 (15/25), mean 20.5, sold 19.6 | 20 (15/25), mean 20.5, sold 19.3 |
| BALANCED_ACTIVE | 25 (20/35), mean 27.2, sold 31.6 | 30 (20/35), mean 27.5, sold 31.5 |
| SYNERGY | 35 (25/40), mean 33.8, sold 28.5 | 35 (25/40), mean 34.7, sold 28.2 |
| BALANCED_INVEST | 30 (20/40), mean 29.6, sold 28.5 | 30 (20/40), mean 30.1, sold 28.4 |
| BALANCED_FAIR, all upgrades | 35 (25/40), mean 33.9 | 35 (25/40), mean 34.7 |
| BALANCED_ACTIVE, all upgrades | 40 (30/45), mean 39.5 | 40 (35/45), mean 40.6 |

0 hard-locks. Deaths fall by about 0.1 a run in every policy (a hall day heals and risks nothing). The active
median crosses a siege boundary (25 -> 30) on a +0.3 mean; it sits on that boundary and flips back on smaller
changes (see part 4), so the lead of the active smith is better read as 7.0 mean days. BALANCED_ACTIVE spends 8.1 % of hero-days at the hall and 6.5 % on ambitions; 4.1
lessons and 1.5 guilds a run, a guild in 97 % of runs. 158 JVM tests pass on the merged tree.

## Balance v5, part 3: replays and weapon fates (session 8)
Built on f73e5a1 (0.5.0, balance v4) in a worktree, measured at config version 4 and merged into v5.
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

### As merged on top of v5 parts 1 and 2 (1,000 seeds, seed 1, `--policy all --impactPolicy BALANCED_ACTIVE`)
The branch measured against v4 as merged and could not see guild inheritance, because a guild never had two living
members there. Part 2 lets heroes join a guild in life, so on the merged tree the rule fires.

| Policy | v5 parts 1-2 | + replays and fates |
|---|---|---|
| BALANCED_FAIR | 20 (15/25), mean 20.5, sold 19.3 | 20 (15/25), mean 20.5, sold 19.3 |
| BALANCED_ACTIVE | 30 (20/35), mean 27.5, sold 31.5 | 30 (20/35), mean 27.4, sold 31.4 |
| SYNERGY | 35 (25/40), mean 34.7, sold 28.2 | 35 (25/40), mean 34.7, sold 28.2 |
| BALANCED_INVEST | 30 (20/40), mean 30.1, sold 28.4 | 30 (20/40), mean 30.2, sold 28.5 |
| BALANCED_FAIR, all upgrades | 35 (25/40), mean 34.7 | 35 (25/40), mean 34.7 |
| BALANCED_ACTIVE, all upgrades | 40 (35/45), mean 40.6 | 40 (30/45), mean 40.6 |

| Fates per run, merged tree | Recovered | Inherited by a guildmate | Resold by a merchant | Seized | Lost | Taken by a merchant | Artifact recovery |
|---|---|---|---|---|---|---|---|
| BALANCED_FAIR | 0.26 | 0.04 | 0.03 | 0.22 | 0.10 | 0.06 | 51 % |
| BALANCED_ACTIVE | 0.27 | 0.07 | 0.01 | 0.22 | 0.09 | 0.05 | 53 % |
| SYNERGY | 0.20 | 0.12 | 0.02 | 0.23 | 0.07 | 0.03 | 53 % |

0 hard-locks; no mean moves by more than 0.1 day. A guildmate inherits in about one armed death in seven (ACTIVE)
to one in five (SYNERGY). Pruning: "held by a travelling merchant" is kept (the merchant may still sell it),
"carried off by a travelling merchant" is terminal and prunable (`WeaponPruning.terminalReasons`,
`WeaponPruningTest`). `HeroDailyLifeTest.woundedDeadAndRetiredHeroesNeverTrainOrPursue` plays 60 seeds instead of 25:
the new combat draws moved the runs and no lesson at the hall fell inside the first 25. 172 JVM tests pass.
Still true on the merged tree: no champion can die on the walls (health 50 or more, a lost siege costs 40), so the
walls odds are reached only in unit tests.

## Balance v5, part 4: Lucky loot and the simulator affix count (session 8)

**Lucky** (item 6 of the v3 review): a material a Lucky wielder brings back now comes from the pool elites carry
(catalysts and tier 3 and up; `AffixDef.scarceLoot`, read in `Battle.resolveExpedition`); the +25 points of loot chance
stay. No RNG draw is added or moved. `GameplayDepthTest.aLuckyBladeBringsBackWhatElitesCarry`: over 20 seeds every
material a Lucky blade brought back is scarce, a plain blade brings back common ones, and Lucky brings back more.

| 1,000 seeds, seed 1 | Median (p10/p90) | Mean days | Sold/run | Elites slain | Lucky weapons forged/run |
|---|---|---|---|---|---|
| BALANCED_ACTIVE, Lucky neutralised (`--noAffixEffect lucky`) | 30 (20/35) | 27.33 | 31.23 | 4.15 | 4.9 |
| BALANCED_ACTIVE, Lucky as merged | 25 (20/35) | 27.39 | 31.45 | 4.23 | 4.9 |
| SYNERGY, Lucky neutralised | 35 (25/40) | 34.59 | 28.04 | 5.99 | 6.5 |
| SYNERGY, Lucky as merged | 35 (25/40) | 34.66 | 28.14 | 6.02 | 6.7 |

Inside the noise floor in both policies, and expected to be: no simulator policy changes its recipe because a
catalyst or a tier 3 core turned up in stock, so the bots do not spend what Lucky brings. For a player it is a free
catalyst or rare core on about three wins in five. Not measured: a policy that forges from scarce stock.

**The active median sits on a siege boundary.** BALANCED_ACTIVE reads 25 in one of these rows and 30 in the other on
a 0.06-day difference in the mean: about half of its runs end at day 25 or earlier. Parts 2 and 3 above report "30";
read the lead of the active smith over the plain one as 6.9 mean days (27.4 against 20.5), not as a 10-day median.

**Simulator**: `affix weapons/run` now counts weapons as they are forged, not the weapons map at run end, so weapon
pruning and salvage no longer lower it (flaming 32.9 forged a run under BALANCED_ACTIVE; the old line counted what
was left).

### Baseline before the legacy-tracks branch (1,000 seeds, seed 1, `--policy all --impactPolicy BALANCED_ACTIVE`)
| Policy | v5 parts 1-4 |
|---|---|
| BALANCED_FAIR | 20 (15/25), mean 20.5, sold 19.3 |
| BALANCED_ACTIVE | 25 (20/35), mean 27.4, sold 31.5 |
| SYNERGY | 35 (25/40), mean 34.7, sold 28.1 |
| BALANCED_INVEST | 30 (20/40), mean 30.2, sold 28.5 |
| BALANCED_FAIR, all upgrades | 35 (25/40), mean 34.6, sold 27.1 |
| BALANCED_ACTIVE, all upgrades | 40 (30/45), mean 40.4, sold 40.9 |

0 hard-locks.
## Balance v5, part 5: legacy tracks (session 8)
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

### As merged on top of v5 parts 1-4 (1,000 seeds, seed 1, `--policy all --impactPolicy BALANCED_ACTIVE`)
New accounts are identical to the part 4 baseline in every policy (the tracks do nothing at level 0). Accounts with
upgrades:

| Row | v5 parts 1-4 | + legacy tracks |
|---|---|---|
| BALANCED_FAIR, all upgrades | 35 (25/40), mean 34.6, sold 27.1 | 35 (30/40), mean 36.5, sold 28.8, longest 50 |
| BALANCED_ACTIVE, all upgrades | 40 (30/45), mean 40.4, sold 40.9 | 45 (35/50), mean 42.2, sold 44.9, longest 55 |

Every run still ends. The maxed active smith is at median 45 where the branch alone measured 40: the signboard
(part 1) and the Known Name regulars both add sales, and they stack. That is the top of the band this branch was
asked to stay under (about 50) and the number to watch in the 10,000-seed review.

Impact under BALANCED_ACTIVE on the merged tree (level 3 against none, median / mean days): Tireless Smith +5 / +0.1,
Family Savings +5 / +0.3, Forge Mastery +5 / +2.6, Stalwart Walls +10 / +6.2, Thrifty Hands +5 / +1.0, Well-Stocked
Cellar +5 / +3.6, Lucky Hammer +5 / +0.4, Known Name +5 / +1.6 (was +0 / -0.1), Caravan Ties, Anvil Lore and Homing
Steel +0 / 0.0, all maxed +20 / +14.8. The three new tracks read 0 days because no bot hunts signatures, repeats a
rare recipe or owns a Legend Board; their own yardsticks are in the tables above. Known Name on the second
yardsticks: the first siege is held in 94 % of runs (86 % without), 9.9 weapons sold by day 5 (8.5), first premium
sale on day 8.1 (9.0). 186 JVM tests pass on the merged tree.

`BalanceConfig` now holds three nested groups (`heroLife`, `weaponFates`, `legacyTracks`) and one slot is left under
the JVM limit of 255 on its generated `copy$default`; every further number goes into a group.

## Balance v5 review at 10,000 seeds (2026-10-09, session 8)

`./gradlew :core:simulate --args="--runs 10000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE --perf --json <out>"`
on main after parts 1-5 (content v2, balance v5, rules v1): 16 policy rows and 13 impact rows, 290,000 runs.
This is the first 10,000-seed review since v3; it covers v4 (wear, fame) and v5 together.

| Policy (10,000 seeds) | Median (p10/p90) | Mean (1k) | Sold/run | Sieges survived | Deaths | Longest |
|---|---|---|---|---|---|---|
| PASSIVE | 10 (10/10) | 10.0 (10.0) | 0.0 | 0.0 | 0.5 | 10 |
| BALANCED_EXPENSIVE | 10 (10/15) | 12.8 (12.9) | 5.5 | 0.1 | 0.6 | 30 |
| RECKLESS_EXPENSIVE | 10 (10/20) | 13.2 (13.1) | 5.7 | 0.1 | 0.7 | 35 |
| SAFE_FAIR | 20 (15/25) | 20.1 (20.2) | 18.6 | 1.0 | 0.7 | 40 |
| BALANCED_REPUTED | 20 (15/25) | 20.5 (20.3) | 18.7 | 1.1 | 0.8 | 40 |
| **BALANCED_FAIR** | 20 (15/25) | 20.7 (20.5) | 19.3 | 1.1 | 0.8 | 35 |
| OVERWORK | 20 (15/25) | 20.8 (20.7) | 19.4 | 1.1 | 0.7 | 35 |
| RECKLESS_FAIR | 20 (15/30) | 21.5 (21.4) | 20.3 | 1.2 | 0.8 | 40 |
| SAFE_CHEAP | 25 (20/30) | 22.8 (22.8) | 19.5 | 1.6 | 0.6 | 40 |
| RANDOM | 25 (15/35) | 23.3 (23.5) | 18.7 | 1.4 | 0.8 | 45 |
| BALANCED_CHEAP | 25 (20/30) | 23.7 (23.5) | 20.2 | 1.7 | 0.6 | 40 |
| **BALANCED_ACTIVE** | 30 (20/35) | 27.4 (27.4) | 31.3 | 2.3 | 0.8 | 45 |
| BALANCED_INVEST | 30 (20/40) | 30.3 (30.2) | 28.6 | 2.8 | 0.8 | 55 |
| SYNERGY | 35 (25/40) | 34.8 (34.7) | 28.3 | 3.8 | 0.8 | 50 |
| BALANCED_FAIR, all upgrades | 35 (30/40) | 36.5 (36.5) | 28.8 | 2.8 | 0.8 | 55 |
| BALANCED_ACTIVE, all upgrades | 45 (35/50) | 42.3 (42.2) | 45.3 | 3.9 | 0.7 | 55 |

- **0 hard-lock days** in every row; **every run ends** (no immortal forge, with or without upgrades).
- Every 1,000-seed mean holds within 0.2 days, so the 1,000-seed tables of parts 1-5 stand.
- The fair-priced first-era smith sits at median 20 (GDD target 15-25); cheaper prices, shop actions, buying
  materials and matching the element to the threat each buy a longer run (25 / 30 / 30 / 35); overpricing or doing
  nothing ends at the second siege. The active median reads 30 here and 25 in some 1,000-seed runs: it sits on the
  siege boundary (part 4); the mean is 27.4 in both.
- A maxed account lives 35-45 median days and at most 55. The active one is at the top of the band the legacy
  tracks were asked to keep (about 50): the signboard and the Known Name regulars both add sales and stack
  (45.3 sold a run against 31.3). No change made; the lever, if it should come down, is the signboard level-2 price
  or the Known Name savings (30 gold a regular).

Upgrade impact under BALANCED_ACTIVE, level 3 against none (base 30 / 27.4; median / mean days): Stalwart Walls
+5 / +6.2, Well-Stocked Cellar +0 / +3.5, Forge Mastery +0 / +2.8, Known Name +0 / +1.6 (v3: -0.4), Thrifty Hands
+0 / +1.2, Lucky Hammer +0 / +0.6, Tireless Smith +0 / +0.4, Family Savings +0 / +0.2, Caravan Ties / Anvil Lore /
Homing Steel +0 / 0.0, all maxed +15 / +14.9. Second yardsticks (means per run): Known Name holds the first siege
in 93 % of runs (86 % without) and sells 9.8 weapons by day 5 (8.5); Tireless Smith forges 25.5 by day 5 (21.8)
and 147 a run (114); Family Savings owns 4.5 tool levels by day 5 (3.6). The three v5 tracks need their own
yardsticks (part 5): no bot hunts signatures, repeats a rare recipe or owns a Legend Board.

| Per run, 10,000 seeds | BALANCED_FAIR | BALANCED_ACTIVE | SYNERGY | ACTIVE, all upgrades |
|---|---|---|---|---|
| Blade recovered to the forge | 0.25 | 0.24 | 0.22 | 0.10 |
| Inherited by a guildmate | 0.06 | 0.08 | 0.10 | 0.07 |
| Resold by a merchant (taken by one) | 0.02 (0.05) | 0.01 (0.04) | 0.02 (0.04) | 0.01 (0.02) |
| Seized | 0.24 | 0.23 | 0.21 | 0.11 |
| Lost | 0.09 | 0.09 | 0.08 | 0.04 |
| Artifact recovery | 49 % | 51 % | 54 % | 55 % |
| Hero-days at the hall / on an ambition | 6.7 % / 7.3 % | 8.1 % / 6.5 % | 9.2 % / 6.3 % | 9.9 % / 6.2 % |
| Lessons at the hall | 2.4 | 4.3 | 6.6 | 9.0 |
| Runs with a guild | 92 % | 98 % | 99 % | 100 % |
| Elites slain | 2.8 | 4.2 | 6.1 | 8.1 |
| Sell rate | 19 % | 28 % | 34 % | 18 % |

- About half of the blades fallen heroes carried come back to Emberfall (forge, guildmate or merchant). The merchant
  is the rare path (one or two runs in a hundred end with a resale): most blades are recovered, inherited or seized
  before one is left over for him. If he should matter more, the lever is `merchantBaseChance` or letting him take
  seized blades too.
- **End Day timing** (`--perf`, forced survival, 1,000 days, desktop JVM): p50 0.53 ms, p95 1.71 ms, max 2.24 ms,
  ending with 2,933 weapons, 115 heroes and 1,816 events. v3 measured p95 0.99 ms: the day does more now (activity
  weights, hall lessons, merchant, fates); the budget is 200 ms.
- **`RULES_VERSION` stays 1.** It seeds the run RNG, so raising it would reshuffle every run and void these tables;
  and a number that nothing enforces protects no save. It moves together with a save-compatibility policy, not
  before.

Not measured: several eras played in sequence with a growing Legend Board; Advanced Forge, technique, catalyst,
commission-led and signature-hunting policies (every bot forges Quick); a human player.

Also on the merged tree: `WeaponPruningTest` (active smith, 400 forced days, seed 77) ends with 1,147 weapons
against 1,852 unpruned, save 1,235,889 against 1,835,036 bytes (-33 %; -42 % before v5, the active bot now keeps
more stock). On the emulator `EndDayPerfTest` reads p50 8.3 / p95 21.3 / max 47.1 ms with 367 weapons at day 120
(4.4 / 8.5 / 35.5 with 157 before v5); one run, emulator noise not characterised, budget 200 ms.

## Art provenance wording (2026-10-09, documentation only, no code change)
- **Finding (asset review, `docs/major_update_evidence/04_assets_content.md`, section 9):** the seven top-level source images and both concept
  references carry embedded Content Credentials (C2PA) naming ChatGPT / OpenAI as the generator; the two packs are script-drawn, by their READMEs;
  no licence or attribution text exists under `Pixel art assets/`. The documents had called all of it "hand-made", and the tooling still does
  (`generate_assets.py` writes `"source": "handmade"`).
- **Decision:** the documents say "imported art" for the category and name each source: AI-generated concept sheets and weapon master sheet
  (94 slices and 336 weapons), script-drawn pack sprites (58), programmatic placeholders from `generate_assets.py`. Manual editing is claimed
  nowhere, because no evidence shows any. The tooling label stays until task T2.4 renames it; until then, read "hand-made" in tool output and
  in `docs/ART_MANIFEST.md` (generated) as "imported".
- Art-origin metadata is not a secret and not a security finding. Source files under `Pixel art assets/` are never re-saved in place, so their
  embedded credentials are preserved; the shipped drawables carry none, because the slicer resamples.
- **Open (owner, blocks P8 not development):** how the art is described on the store listing, and the usage terms of the generating account
  (plan 5.7, 10.4). One paragraph in `docs/ART_BRIEF.md` ("Art sources and provenance") is the working statement.

## Planned departures from PROPOSED GDD values (2026-10-09; planned, lands with M2/M3, not yet in the code)
Recorded by task T0.3 from plan 2.5 and 4.8. Nothing below is built: the code is release 0.6.0 and still plays 8 starting heroes, 4 seats and
seven panels. Each entry becomes a normal dated section, with measured evidence, when its slice lands. No LOCKED decision changes.
| GDD PROPOSED value | Planned value | Why | Lands |
|---|---|---|---|
| "roughly 2-4 customers a day" (GDD table; adopted above as 2–4) | 6 counter seats (7 / 8 with the Signboard; +3 on a festival) | the shop day shows the customers; four seats left the counter thin and, at today's 8 heroes, 3.5 served a day | M3 (T3.4, T3.1 first) |
| starting population 8 (8 start, floor 5, cap 12 today); the GDD's own range is 10-14 | 12 residents | 8 is below the GDD range, so 12 moves toward it; about +1.2 mean days per extra resident at fixed rules, compensated in balance v6 | M3 (T3.4) |
| GDD 12 panel list (Market, Town, Journal, Gazette, Legacy, Settings) | four destinations (Shop, Forge, Town, Records) with detail sheets, and a Settings gear | seven tabs auto-size labels down to 8-12 sp; sheets reach any hero or blade from anywhere | M2 (T2.8a) |
Alternatives kept in the sweep: 5 seats / 10 residents (safer, less visible); 8 seats / 14 residents (rejected: the Signboard dies and the
first-era band breaks). One GDD 12 / 19 line stays open after the update: audio and haptic toggles (deferred together, plan 10.3).

## Documentation corrections (2026-10-09, task T0.3)
Checklist wording of the external review's section 9 (C01-C09, C12) was applied to `docs/GDD_CHECKLIST.md`: a ticked line states only what its
evidence shows and each narrowed claim has an open line beside it. Older sections of this file keep their historical "Not measured: 10,000 seeds"
and "Known Name is not applied" lines as written at the time; they are superseded by "Balance v5 review at 10,000 seeds" (every policy and both maxed
accounts reviewed) and by "Balance v5, part 5" (Known Name reworked, +1.6 mean days).

## Recovery and reachability, measured at balance 6 (2026-10-09, task T5.2; G10, C15)
Measurement only: no rule or number changed. Command: `./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy every --customers --noImpact"`
(`--customers` now also prints a `recovery (morning state)` line per row; the default output is byte-identical to before). Metric code is
`RecoveryProbe` in `core/sim/Metrics.kt`; the definitions below are what the numbers mean.

**Definitions (read from the morning state, before the policy acts).** *Possible sale*: a blade in the shop (shelf or storage) that some living
hero can afford at the going rate (`suggestedPrice`) and would be stronger with (`Market.evaluate`: affordable and improvement > 0), or a blade
that closes an offered or accepted commission. *Legal forge*: a Quick forge the energy (with the day's overwork allowance) and the gold on hand
allow, a core and an augment each on hand or buyable from stock. **Drought** = no possible sale, a legal forge exists. **Stuck** = no possible
sale and no legal forge. A **streak** is consecutive stuck mornings; a drought day or a day with a possible sale ends it. Salvage, hone and
donating are not counted as ways out. Day 1 morning is always a drought day (empty shop), about 5 % of a 20-day run's days. The old
`hardLockDays` ("hard-lock days total" in the default output) is kept unchanged and is now documented as what it measures: days after day 1 with no
successful forge and an empty shelf when the policy reaches its listing step, i.e. yesterday's leftover shelf; it stays 0 in every row below
while `BALANCED_ACTIVE` is stuck on 0.47 % of days, which is the G10 finding made visible. The older `empty-shelf days` in the customers block
(`RunCustomers.droughtDays`) counts End Days with nothing listed; it is a different number from the drought above.

**Valve trigger (plan 4.7: stuck streaks of three days or more in more than 1 % of NOVICE runs).** NOVICE, 1,000 runs, seed 1: **0 of 1,000 runs
(0.00 %)** had a stuck streak of three days or more. Seeds 10001 and 20001: 0.00 % and 0.10 %. The 1 % trigger is **not crossed** (the three
sets are within 0.1 % of each other, ten times below the trigger). Longest NOVICE streak: 1 day (seed 1), 2 (seed 10001), 4 (seed 20001). The valve
(Salvage also returns the augment of an unworn, never-sold blade) is therefore not implemented. Streak 3+ share by bot, 1,000 runs, seed 1:

| policy | mean days | stuck days | drought days | runs with a stuck day | streak 2+ | **streak 3+** | streak 5+ | longest |
|---|---|---|---|---|---|---|---|---|
| NOVICE | 23.1 | 0.01 % | 9.79 % | 0.30 % | 0.00 % | **0.00 %** | 0.00 % | 1 |
| SPENDTHRIFT | 29.3 | 0.44 % | 6.27 % | 9.20 % | 2.30 % | **0.50 %** | 0.00 % | 4 |
| BROKE_START | 20.1 | 0.00 % | 5.68 % | 0.10 % | 0.00 % | **0.00 %** | 0.00 % | 1 |
| FREE_LISTINGS | 24.0 | 0.00 % | 4.38 % | 0.10 % | 0.00 % | **0.00 %** | 0.00 % | 1 |
| BALANCED_FAIR | 20.1 | 0.00 % | 5.66 % | 0.00 % | 0.00 % | **0.00 %** | 0.00 % | 0 |
| BALANCED_ACTIVE | 27.0 | 0.47 % | 10.98 % | 7.50 % | 2.10 % | **0.90 %** | 0.20 % | 5 |
| BALANCED_INVEST | 29.4 | 0.19 % | 4.64 % | 3.40 % | 1.30 % | **0.60 %** | 0.00 % | 4 |
| SYNERGY | 33.4 | 0.02 % | 3.59 % | 0.70 % | 0.10 % | **0.00 %** | 0.00 % | 2 |
| SIEGE_PREP | 39.1 | 0.10 % | 3.50 % | 2.10 % | 1.00 % | **0.40 %** | 0.00 % | 4 |
| EXPERT | 42.0 | 0.07 % | 3.21 % | 2.30 % | 0.50 % | **0.00 %** | 0.00 % | 2 |
| EXPERT_ACTIVE | 42.1 | 1.05 % | 9.12 % | 24.30 % | 8.40 % | **2.20 %** | 0.20 % | 5 |

Every other policy (RANDOM, SAFE_*, RECKLESS_*, BALANCED_CHEAP / EXPENSIVE / REPUTED, OVERWORK, the technique and catalyst bots, REQUEST_DRIVEN,
SIGNATURE_PURSUIT, SCARCE_RECIPE) has 0.00 % of runs with a streak of 3 or more and no stuck day beyond one run in a thousand; PASSIVE is
all drought by construction. Noise floor, streak 3+ share over three seed sets (1 / 10001 / 20001): NOVICE 0.0 / 0.0 / 0.1 %, SPENDTHRIFT 0.5 / 0.8 / 1.1 %,
BROKE_START 0.0 / 0.0 / 0.0 %, FREE_LISTINGS 0.0 / 0.0 / 0.1 %, BALANCED_ACTIVE 0.9 / 0.4 / 0.6 %, EXPERT_ACTIVE 2.2 / 3.0 / 1.9 %.

**Reading.** The trigger as written (NOVICE) is not crossed. Two other bots do cross 1 %: EXPERT_ACTIVE (every seed, 1.9-3.0 %) and SPENDTHRIFT
(once, 1.1 % at seed 20001, 0.5-0.8 % elsewhere). Both are bots that spend gold down to near zero on tools and premium materials, which is the way
the stuck state is reached by arithmetic (gold under the price of a core plus an augment, no augment in stock, every hero better armed than the
shelf). Whether the valve should also answer them is the owner's decision; this task records the numbers. BROKE_START barely shocks (the
starting kit forges without gold) and FREE_LISTINGS does not run away: end reputation 34.4 and top loyalty 7.9 against 29.0 and 7.9 for BROKE_START
(a BALANCED_FAIR stand-in), conversion 18.9 %, sales 0.82 a day; it lives 24.0 days against 20.1 because free blades make heroes stronger. A
material-poor start, a `Lean Harvest` world, prices at 70 % / 180 % beyond BALANCED_CHEAP / EXPENSIVE, and a town that lost half its heroes have no
simulator option yet and were not run.

**C15: the 25 events.** `WorldEventReachabilityTest.everyPooledEventFiresInTwoThousandRunsAndBothRulesTrigger`: 1,000 new-account runs (seeds 1-1000)
and 1,000 veteran-account runs (three claimed eras of BALANCED_ACTIVE: journal, Legend Board, lineages, eras), policies BALANCED_ACTIVE, SYNERGY,
EXPERT_ACTIVE, BALANCED_FAIR in turn. All 23 pooled events fire, and both generational rules trigger. `famous_blade` and `descendant` need a
history, so they are required on the veteran cohort only (223 and 189 of 1,000 runs there; 0 on a new account by construction). Rarest on a new
account: `heroic_inheritance` 39 of 1,000 runs, `guild_banner` 187, `collector` 214, `wandering_master` 214; `champion_retirement` 653,
`guild_founded` 980. No event is unreachable.

These numbers are re-measured after the customer changes of M3 (T3.1 seats and fair selection, T3.4 residents), which move who can afford what.
## Balance v7, rules 3: fair customer selection (2026-10-09, task T3.1; F05, X14, E02, m13)
- **Finding (measured at M0, repeated on this base):** `Market.resolveShelfVisits` walked the living heroes in ID-string order and stopped at
  the seat limit. BALANCED_FAIR, 1,000 seeds: positions 1-4 were served on 54-55 % of their days, position 8 on 21.8 % (0.39 of position 1);
  under EXPERT position 8 got 9.4 %, 10.9 % of all heroes were never served, and 56 % of runs had a hero who lived ten days or more without
  one visit (75.5 % on a maxed account).
- **Decision (plan 4.2):** one willingness draw per living hero (PURCHASES stream), then one weighted draw per seat among the willing, served
  in seating order. Willing heroes turned away `maxTurnedAwayDays` (2) days running are seated first, longest streak first. Seat weight =
  1 + 0.5 x min(loyalty, 10) / 10 + 1.0 for a hero never served + 0.75 x streak + 0.5 if unarmed or the own blade is worn, halved for a hero
  who browsed yesterday and bought nothing; the first seats go to classes not yet seated until three classes sit. A commission patron who
  collected today is not also seated (their draw is still made). `aliveHeroes()`, commissions, `Commissions.pick`, the champion tie-break and
  the merchant use `IdOrder.numeric` (h2 before h10).
- **Numbers:** new group `BalanceConfig.customers: CustomerConfig`. Moved at their values: `startingHeroes` 8 (was `startingHeroCount`),
  `minHeroPopulation` 5, `maxHeroPopulation` 12, `shopCapacity` 4 (was `maxCustomersPerDay`), `festivalExtraSeats` 2 (was
  `festivalExtraCustomers`), `baseVisitChance` 0.35, `festivalVisitBonus` 0.2. Former inline constants: `visitTraitScale` 0.1, `visitPerLoyalty`
  0.01, `visitPerReputation` 0.005, `visitFloor` 0.05, `visitCeiling` 0.9. New: `visitLoyaltyCap` 10 and `visitReputationCap` 50 (the two terms
  grew until the 0.9 clamp), `maxTurnedAwayDays` 2, `classSeats` 3, `seatLoyaltyWeight` 0.5, `seatLoyaltyCap` 10, `seatNewcomerWeight` 1.0,
  `seatWaitWeight` 0.75, `seatNeedWeight` 0.5, `seatBrowsedYesterday` 0.5. All PROPOSED. Seats, population, purses and prices are NOT changed
  (that is T3.4); weapon wear is not touched.
- **Readings chosen where the plan was silent:** a day whose shelf is empty when the browsers arrive changes no hero's counters (seated or
  turned away); on a day the shelf sells out, those seated afterwards keep their streak and first-visit standing, those turned away gain a
  day. A commission does not count as a shop visit and does not touch the streak.
- **Versions:** balance 6 -> 7, rules 2 -> 3, save schema 3 -> 4 (no-op step in both tables: the six defaulted `Hero` counters), content 2.
  `GameEngine.STREAM_SEED_VERSION` stays 1. A rules-2 run is admitted and continues; its heroes read as newcomers nobody kept waiting.
  Golden file `golden/state_rules3.txt` is new: 290 of 300 lines differ from rules 2, from day 1 of every seed. Day 1 moves because the
  PURCHASES stream is drawn in a new order (one draw per living hero and one per seat, where the scan drew until the seats were full); from
  the day a hero with serial 10 or higher is alive, numeric order also reassigns the per-hero draws of the HEROES, COMBAT and EVENTS streams.
  `state_rules2.txt` is kept.
- **Evidence** (`--runs 1000 --seed 1 --policy all|bots --customers --noImpact`, before = `shop-day/m0` at `2dd13a1`; maxed = `--upgrades` all
  eleven at 3). Days are p10 / median / mean / p90, longest.

| Row | days before | days after | mean | served/day | conversion | sold/run | gold earned (median) |
|---|---|---|---|---|---|---|---|
| BALANCED_FAIR | 15/20/20.1/25, 35 | 15/20/20.5/25, 35 | +0.4 | 3.57 -> 3.55 | 23.4 -> 23.9 % | 19.5 -> 20.2 | 1830 -> 1910 |
| BALANCED_ACTIVE | 20/25/27.0/35, 40 | 20/30/27.4/35, 45 | +0.4 | 4.78 -> 4.67 | 21.6 -> 22.6 % | 31.2 -> 32.4 | 3231 -> 3367 |
| SYNERGY | 25/35/33.4/40, 45 | 25/35/33.7/40, 50 | +0.3 | 3.73 -> 3.70 | 19.9 -> 21.2 % | 28.9 -> 30.4 | 3324 -> 3457 |
| BALANCED_EXPENSIVE | 10/10/12.4/15, 30 | 10/10/12.5/15, 25 | +0.1 | 3.22 -> 3.18 | 9.9 -> 10.3 % | 5.6 -> 5.8 | 761 -> 779 |
| PASSIVE | 10/10/10.0/10, 10 | same | 0.0 | 3.09 -> 3.10 | 0 | 0 | 0 |
| SIEGE_PREP | 30/40/39.1/45, 50 | 30/40/40.3/50, 55 | **+1.2** | 3.82 -> 3.79 | 21.3 -> 23.5 % | 37.7 -> 42.3 | 4848 -> 5472 |
| EXPERT | 35/45/42.0/50, 55 | 35/45/42.9/50, 55 | **+0.9** | 3.88 -> 3.83 | 19.4 -> 22.3 % | 43.0 -> 48.5 | 5958 -> 6694 |
| EXPERT_ACTIVE | 35/45/42.1/50, 60 | 35/45/43.1/50, 60 | **+1.0** | 4.90 -> 4.81 | 20.9 -> 22.8 % | 49.6 -> 54.4 | 6331 -> 6958 |
| maxed BALANCED_FAIR | 30/35/35.4/40, 45 | 30/35/35.9/40, 45 | +0.5 | 3.88 -> 3.86 | 16.6 -> 18.5 % | 30.2 -> 33.1 | 3866 -> 4104 |
| maxed BALANCED_ACTIVE | 35/45/42.1/45, 55 | 35/45/42.4/50, 55 | +0.3 | 5.45 -> 5.18 | 16.7 -> 18.4 % | 46.2 -> 48.2 | 6352 -> 6564 |
| maxed SYNERGY | 35/45/43.9/50, 55 | 35/45/44.6/50, 60 | +0.7 | 3.90 -> 3.88 | 17.2 -> 19.3 % | 35.1 -> 39.2 | 4701 -> 5195 |
| maxed EXPERT | 45/50/51.4/55, 60 | 50/55/53.4/60, 65 | **+2.0** | 3.96 -> 3.92 | 17.1 -> 21.2 % | 49.4 -> 59.5 | 7532 -> 8942 |
| maxed EXPERT_ACTIVE | 50/55/54.2/60, 65 | 50/55/55.3/60, 65 | **+1.1** | 5.37 -> 5.16 | 16.7 -> 19.1 % | 62.0 -> 68.7 | 9415 -> 10394 |

  The other nine classic policies move by +0.1 to +0.4 mean days and the other nine bots by 0.0 to +0.4; every run ends; hard-lock days 0.
- **Fairness:** served share of hero-days by position among the living (numeric ID order), BALANCED_FAIR, before -> after: 1: 55.3 -> 43.9,
  2: 53.6 -> 43.5, 3: 54.3 -> 44.1, 4: 54.7 -> 44.1, 5: 49.3 -> 44.2, 6: 40.6 -> 44.3, 7: 29.1 -> 44.1, 8: 21.8 -> 43.1 %. Worst / best of
  positions 1-8: 0.39 -> 0.97 (classic policies 0.95-0.98, bots 0.92-0.98, maxed accounts 0.89-0.93, where positions 1-3 are the Known Name
  regulars and are served more for their loyalty). Positions 9-12 read 40, 39, 33, 44 %: those positions exist only on crowded days, when four
  seats are shared among more heroes; split by population the rate is flat (600 runs: 8 alive 43.1-44.5 % at every position, 10 alive
  37.4-41.4 %). Per run, highest / lowest served share among heroes alive ten days or more: median 3.67 -> 2.25, p90 10.0 -> 3.67. Heroes
  never served: 3.5 -> 1.5 % (EXPERT 10.9 -> 1.3 %); runs with a hero alive ten days or more and never served: 7.7 -> 0.2 % (EXPERT
  56.4 -> 0.1 %). Newcomer wait: median 1 -> 0, p90 4 -> 3 days. Turned away: 0.83 willing heroes a day,
  someone on 45 % of days; the longest streak any hero reached in 600 BALANCED_FAIR runs is 2.
- **Sweep step 1 (plan 4.3) against its three conditions:** (1) "no mean moves more than 0.6 days" holds for all fourteen classic policies and
  both maxed rows the step names (largest +0.5); it does NOT hold for SIEGE_PREP, EXPERT, EXPERT_ACTIVE, maxed SYNERGY and maxed EXPERT. Cause:
  those bots forge the strongest stock, and it used to go to the same four heroes; now every hero is armed from it (sold/run +10 % to +20 %).
  (2) "served-share max / min at most 2.5": 0.97 by position; the per-run statistic has median 2.25 and p90 3.67, so about a quarter of
  20-day runs still exceed 2.5 between two individual heroes (traits and loyalty differ, and a hero has about nine visits in a run).
  (3) "visits per day within 0.1": holds without the Signboard (-0.02); with it -0.11 (BALANCED_ACTIVE) and -0.27 (maxed BALANCED_ACTIVE),
  because the loyalty and reputation terms of the visit chance are now capped and a commission patron no longer browses the same day.
- **Tripwire (plan 4.3), reported, nothing tuned:** new-account EXPERT mean 42.9 against the line 34.8 + 8 = 42.8: **over by 0.1** (before:
  42.0; the noise floor is about 0.3). Maxed EXPERT p90 60 (the line is "above 60"), longest 65. No run reaches 100 days. Population, seats,
  prices, wear and maxed-account survival were not adjusted; the decision is the owner's, with T3.4 still to add residents and seats.
- **Tests:** new `CustomerSelectionTest` (11 tests): draw counts, intent independent of seats, equal turns over 10,000 full days (chi-square,
  11 degrees of freedom, p > 0.01) and under a permutation of serials, loyalty 10 against 0 served 1.38x (bound 1.2-1.6), the waiting bound
  with 16 keen heroes and 4 seats (longest streak 4), reputation 50 and saturation (max / min 1.03-1.07), class seats, empty shelves, the
  stored `turnedAway`, one ID order. `ShopRecordTest.aPatronAppearsOncePerDay`. The newcomer line of the plan ("served within two days in
  four seeds of five") is met counted in days the newcomer chose to come (198 of 200); by the calendar it is 145 of 200, because a hero comes
  on 35 % of days at the base chance.

## Content 3: hero names and lineage IDs (2026-10-09, task T3.2; N03, X07)
- **Finding (base `b0035ca`, 1,000 seeds, BALANCED_FAIR):** two living heroes shared a first name on 66.9 % of days and a surname on 73.5 %;
  a full name was given twice in 6.1 % of runs (17.4 % under EXPERT_ACTIVE). A descendant was matched to a lineage by the ancestor's name
  string in three places of the engine and once in the siege scene.
- **Decision (plan 4.4, 6.6):** 120 first names and 96 surnames in one shared pool (content version 3). `Names.first` picks among the first
  names nobody in the run has carried (any fate) and not the ancestor's; `Names.surname` among the surnames no living hero carries, no guild
  founder of the run carries and no lineage of the account holds. A descendant's surname is the lineage's. When a pool is spent, the names
  of the heroes dead longest come round again, never a living hero's and never a full name already given. Each name is one draw on the
  stream `Heroes.generate` was handed, as before (a descendant's surname is none), so no stream moves: 1,000 seeds x 28 policies give the
  same simulator output with the old pools and the new, apart from the name clashes, which read 0.0 % / 0.0 % / 0.0 % for every policy.
- **Authoring rules, checked by `ContentCatalog.validate()` on every catalog:** plain letters with one capital, 3-8 and 4-11 long (so a full
  name is at most 20 characters); no two first names share three opening letters, any two are two edits apart, three if they share an
  initial; at most eight per initial and at least fifteen initials; no two surnames share four opening letters or are one edit apart; at most
  four surnames per ending (the last three letters); no first name opens a surname ("Rook Rooksbane"); no name contains a game term (a word
  of an ID or display name of a family, material, affix, class or faction, or an element) or opens with the first five letters of one. The
  four kinds of surname (24 each: nature compounds, places, trades, old family names), pronounceability and "nothing that reads as a weapon
  title" are the author's and are not machine-checked. The counts 120 and 96 are pinned by `LaunchContentTest`.
- **Names that left the pool:** `Ashwood`, `Holloway`, `Mossgrave`, `Rooksbane` and `Nessa` as the plan names them, and `Brackenridge`: at
  twelve letters it breaks the plan's own 4-11 rule (with an eight-letter first name it makes 21 characters). `Imre`, a sample of the
  plan, is two edits from the existing `Ione` and was not taken. Heroes and lineages that carry a departed name keep it. The slice catalog
  swaps `Nessa`, `Ashwood` and `Holloway` for launch names and gains `Dagny` (fifteen initials).
- **Identity:** `LineageAnchor.id` ("era2-h7": the era and the hero), `Hero.lineageId`, `CombatRound.attackerId`. `GameEngine.newRun`, the
  "descendant" event (eligibility and pick) and the siege scene join by these. `Hero.descendantOf` stays as the ancestor's display name.
  The schema-4 step, still a number no release has shipped, now converts once: a lineage written without an ID takes "era<N>" (at most one
  is founded per era) in the legacy document and in the run's copy, and a hero recorded as a descendant is linked to the latest lineage of
  that name. A siege replay stored before the update names no hero IDs, so its diorama shows the raiders without the champions until the
  next siege; the text of the replay is unchanged.
- **Numeric ID order, completed (ruling: rides on rules 3):** `GameState.listedWeapons` / `storedWeapons` / `retiredHeroes`, the pick among
  lost blades (`heroic_inheritance`), the collector's tie-break, the guild-hall mentor's tie-break, the order a retiree's blades are handed
  down, and the hero a lineage is founded on (`Legacy.closeRun`) use `IdOrder.numeric`. Left as they are: sorts of factions and classes
  (content IDs, no serial), sorts on integer serials, and two tie-breaks in `shopday/Advice.kt` (text order of commission and weapon IDs;
  advice only, no outcome; that package was outside this task).
- **Effect of the sorts (1,000 seeds, base seed 1, mean days, before -> after):** the names contribute +0.0 to every row. Classic policies:
  RANDOM 23.2 -> 22.8, SAFE_FAIR 19.9 -> 20.4, RECKLESS_FAIR 21.0 -> 21.5, BALANCED_CHEAP 23.3 -> 23.8, BALANCED_EXPENSIVE 12.5 -> 12.4,
  SYNERGY 33.7 -> 32.6, OVERWORK 20.4 -> 20.8, BALANCED_FAIR 20.5 -> 20.8, BALANCED_INVEST 29.8 -> 29.1, BALANCED_REPUTED 20.3 -> 20.6,
  BALANCED_ACTIVE 27.4 -> 27.4, SAFE_CHEAP 22.7 -> 23.3, RECKLESS_EXPENSIVE 12.5 -> 12.5, PASSIVE 10.0 -> 10.0. Bots: ADVANCED_SMITH
  21.6 -> 21.9, TECHNIQUE_TEMPER 20.5 -> 20.9, TECHNIQUE_QUENCH 20.9 -> 21.3, TECHNIQUE_ETCH 22.9 -> 23.1, REQUEST_DRIVEN 23.1 -> 23.6,
  SIEGE_PREP 40.3 -> 40.1, SIGNATURE_PURSUIT 25.6 -> 25.6, SCARCE_RECIPE 23.8 -> 24.0, EXPERT 42.9 -> 42.8, EXPERT_ACTIVE 43.1 -> 43.1,
  SPENDTHRIFT 29.6 -> 28.8, NOVICE 23.4 -> 23.2, BROKE_START 20.5 -> 20.9, FREE_LISTINGS 24.1 -> 24.6. The policies that list their stock
  in storage order now put the oldest blade on the shelf first once a tenth blade exists (text order put w10 before w2); the largest moves
  are SYNERGY -1.1, SPENDTHRIFT -0.8, BALANCED_INVEST -0.7 and SAFE_CHEAP +0.6 against a noise floor of about 0.3. One base seed. Nothing
  was tuned.
- **Golden file `state_rules3.txt` re-recorded** (20 seeds x 15 days). The sorts change 188 of 300 lines (107 RNG hashes), in every seed,
  first on days 4-10: the golden script lists stored blades in `storedWeapons()` order, and its tenth blade is forged on day 4. The names
  change 8 further lines of one seed (14, from day 3) and no RNG hash: the projection includes the text "lost with <hero name>" of a lost
  blade. `ReplaysAndWeaponFatesTest.theGazetteTellsEveryFate` moved from seed 3 to seed 4 (the sorts left seed 3 with four fate records
  where the test wants six).

## Balance v7, a larger, fairer town: 12 residents, 6 seats, pressure retuned (2026-10-09, task T3.4; N01, N02)
The plan names this section "Balance v6: a larger, fairer town"; balance 6 was taken by T1.8, so M3 is balance 7 (ledger ruling). Balance
stays 7, rules 3, schema 4, `STREAM_SEED_VERSION` 1: M3 is one unreleased step, and the v7 fingerprint pin of T3.1 was re-pinned for it.

Owner instructions this tuning is bound by (quoted):
- "Treat the 45-day median as baseline evidence, not a hard survival ceiling. Tune stacked customer/economy changes using distributions and
  player decisions."
- "Do not automatically nerf wear or maxed-account survival because the median is 45 days."

Weapon wear, hero purses, expedition gold, patrol pay, `tradeInShare`, `fairGoldPerPower` and the newcomer's empty hands are untouched. No
number was chosen to pull a maxed account toward a median: the pressure numbers below were chosen as the smallest step that keeps the
first-era bands of plan 4.3 (BALANCED_FAIR, BALANCED_ACTIVE), and they leave every maxed-account row at or above its baseline.

- **Numbers (`BalanceConfig.customers`, all PROPOSED):** `startingHeroes` 8 -> **12**; new `populationTarget` **12**,
  `arrivalChancePerMissing` **0.15**, `arrivalChanceMax` **0.6**; `minHeroPopulation` 5 -> **9**; `maxHeroPopulation` 12 -> **16**;
  `shopCapacity` 4 -> **6** (7 / 8 with the Signboard); `festivalExtraSeats` 2 -> **3**. Flat: `expeditionSuppression` 3 -> **2**,
  `raidPerDay` 6.0 -> **6.5**. `--set` accepts the three new keys.
- **Rules:** (1) every run starts with all five classes: until all are present a starting hero draws its class from the classes still
  missing (one HEROES draw, as before), so the first five heroes are one of each in a seeded order; a descendant keeps the lineage's class
  and counts. (2) Arrivals: each End Day one HEROES draw is made whatever the population. Below the floor a newcomer always arrives; below
  the target one arrives when the draw is under min(0.6, 0.15 x missing); at or above the target nobody does. At most one a day. Event
  arrivals (New Adventurers, Veteran Returns, a descendant) are as before and stop at 16. (3) A newcomer is unarmed with class gold, as
  before.
- **Old saves:** nothing is added at admission. A town saved with 8-11 heroes keeps them and fills up by rule 2 (certain below 9, then by
  chance), and meets the new pressure numbers at once. Sweep step 4b (below) measures that case; it stays under the plan's 2-day line, so
  no "keep the old threat numbers until the next siege" rule was built.

### What a player will notice
- **Faces.** Five or six customers on an ordinary day instead of three or four (5.6 against 3.6 served a day; 7.1 against 4.7 with the
  Signboard). The shop is full (six served) on three days in four. A day with two or fewer visitors was one day in eight and is now one in
  eighty; a day with nobody at the counter was one in 250 and is now below one in 2,000. Over a first era about 14 different heroes are
  served instead of 9, eleven of them by day 5, and in practice every class has bought something by day 10 (99.9 % of runs, was 83 %).
- **Turned away.** On six days in ten somebody willing finds the shop full (1.5 heroes a day; 45 % of days and 0.8 before). That is the
  room the Signboard sells: with it the count falls to 1.0 a day. Nobody waits long: a newcomer's first visit is the day they first want to
  come (median 0 days, p90 2).
- **Gold.** About 40 % more sales a day on the same ten energy (BALANCED_FAIR 0.97 -> 1.36; BALANCED_ACTIVE 1.18 -> 1.71), so a careful
  player's stock runs down and what to forge next matters on more days. Gold earned over a first era rises by about 45 % (median 1,888 ->
  2,716; active 3,356 -> 5,363). Purses per hero are the same: on day 1 a third of the town still cannot pay for the cheapest blade, and
  "too expensive" is the same 20 % of visits. "Not better than what I carry" stays the main refusal (55 -> 57 % of visits), and conversion
  falls about two points (23.4 -> 21.6 %) because more people look at the same shelf. That is M4's subject (wants, sidegrades).
- **Difficulty.** The first siege is held more often (BALANCED_FAIR 65 -> 80 %, a novice 48 -> 61 %) because twelve heroes give the wall
  three better champions, and so is the second (35 -> 47 %). The faster raid growth takes it back from the third (10 -> 12 %): a plain
  first era still ends around day 20-25 (mean 20.8 -> 22.3, median 20, p10 15, p90 25). Other ways of playing gain two to three and a half
  days: counter-element stock for sieges 40.1 -> 43.3, SYNERGY 32.6 -> 35.2, a novice 23.2 -> 26.8. A hero is no likelier to die on a
  given day than before.
- **Dead ends.** Stuck mornings (nothing sellable and nothing forgeable) nearly vanish: runs with a stuck streak of three days or more fall
  from 2.6-3.6 % to 0-0.1 % for the hardest-spending bot, and to zero for the novice.
- **Not better:** warlord sieges are rarer (BALANCED_FAIR 0.8 -> 0.3 a run): twelve heroes hold faction pressure lower even at suppression
  2 (31 against 36 at the second siege). Reported, not tuned.

### Baseline against final (1,000 runs, base seed 1; days are mean / median / p10 / p90 / longest; the other two base seeds are in the tables file)
| policy | days before | days after | gold earned (median) | served a day | distinct heroes served | conversion % | nobody at the counter, % of days | never served, % of heroes | stuck streak 3+, % of runs | deaths per hero-day | first siege held % |
|---|---|---|---|---|---|---|---|---|---|---|---|
| BALANCED_FAIR | 20.8 / 20 / 15 / 25 / 40 | 22.3 / 20 / 15 / 25 / 35 | 1888 -> 2716 | 3.55 -> 5.63 | 9.2 -> 14.2 | 23.4 -> 21.6 | 0.4 -> 0.0 | 1.6 -> 1.2 | 0 -> 0 | 0.0045 -> 0.0042 | 64.8 -> 79.7 |
| BALANCED_ACTIVE | 27.4 / 25 / 20 / 35 / 45 | 29.4 / 30 / 20 / 35 / 45 | 3356 -> 5363 | 4.67 -> 7.10 | 10.8 -> 16.6 | 22.6 -> 22.1 | 0.2 -> 0.0 | 1.1 -> 1.1 | 1.3 -> 0 | 0.0035 -> 0.0031 | 86.0 -> 89.2 |
| BALANCED_EXPENSIVE | 12.4 / 10 / 10 / 15 / 30 | 13.4 / 15 / 10 / 15 / 30 | 771 -> 1342 | 3.18 -> 5.00 | 8.4 -> 12.8 | 10.2 -> 10.6 | 1.0 -> 0.1 | 1.5 -> 1.6 | 0 -> 0 | 0.0068 -> 0.0065 | 3.7 -> 6.5 |
| PASSIVE | 10.0 / 10 / 10 / 10 / 10 | 10.0 / 10 / 10 / 10 / 10 | 0 -> 0 | 3.10 -> 4.77 | 8.2 -> 12.4 | 0 -> 0 | 1.3 -> 0.1 | 2.6 -> 2.3 | 0 -> 0 | 0.0060 -> 0.0063 | 0 -> 0 |
| NOVICE | 23.2 / 25 / 15 / 35 / 40 | 26.8 / 30 / 20 / 35 / 45 | 2060 -> 3548 | 3.53 -> 5.62 | 9.8 -> 15.7 | 23.8 -> 21.5 | 0.5 -> 0.0 | 1.8 -> 1.4 | 0.1 -> 0 | 0.0047 -> 0.0040 | 47.7 -> 61.3 |
| SYNERGY | 32.6 / 35 / 25 / 40 / 45 | 35.2 / 35 / 30 / 40 / 45 | 3166 -> 4547 | 3.69 -> 5.78 | 12.4 -> 19.0 | 20.7 -> 18.8 | 0.2 -> 0.0 | 1.7 -> 1.5 | 0 -> 0 | 0.0029 -> 0.0026 | 88.8 -> 96.3 |
| SIEGE_PREP | 40.1 / 40 / 30 / 50 / 55 | 43.3 / 45 / 40 / 50 / 55 | 5210 -> 7870 | 3.79 -> 5.85 | 14.3 -> 22.0 | 23.2 -> 22.0 | 0.1 -> 0.0 | 1.4 -> 1.3 | 0.1 -> 0 | 0.0027 -> 0.0023 | 90.3 -> 97.0 |
| EXPERT | 42.8 / 45 / 35 / 50 / 55 | 44.4 / 45 / 40 / 50 / 55 | 6510 -> 9142 | 3.83 -> 5.86 | 15.7 -> 23.3 | 21.9 -> 21.3 | 0.1 -> 0.0 | 1.6 -> 1.4 | 0.4 -> 0 | 0.0019 -> 0.0019 | 95.2 -> 98.6 |
| EXPERT_ACTIVE | 43.1 / 45 / 35 / 50 / 55 | 46.4 / 45 / 40 / 50 / 55 | 7023 -> 11088 | 4.81 -> 7.26 | 15.7 -> 24.1 | 22.9 -> 22.0 | 0.1 -> 0.0 | 0.9 -> 1.0 | 2.6 -> 0 | 0.0021 -> 0.0019 | 92.8 -> 98.7 |
| maxed BALANCED_FAIR | 35.8 / 35 / 30 / 40 / 50 | 36.8 / 35 / 30 / 40 / 45 | 4413 -> 5618 | 3.84 -> 5.90 | 13.0 -> 19.6 | 19.8 -> 18.0 | 0.1 -> 0.0 | 1.7 -> 1.5 | 0 -> 0 | 0.0029 -> 0.0025 | 79.4 -> 89.9 |
| maxed BALANCED_ACTIVE | 42.4 / 45 / 35 / 50 / 55 | 43.8 / 45 / 40 / 50 / 60 | 6529 -> 9192 | 5.18 -> 7.45 | 14.8 -> 22.0 | 18.4 -> 18.3 | 0.0 -> 0.0 | 0.9 -> 0.8 | 0.1 -> 0 | 0.0019 -> 0.0018 | 93.1 -> 96.2 |
| maxed SYNERGY | 43.9 / 45 / 35 / 50 / 55 | 45.2 / 45 / 40 / 50 / 55 | 4745 -> 6441 | 3.88 -> 5.92 | 15.2 -> 22.7 | 18.3 -> 17.2 | 0.1 -> 0.0 | 1.4 -> 1.2 | 0 -> 0 | 0.0022 -> 0.0022 | 89.7 -> 94.9 |
| maxed EXPERT | 53.2 / 55 / 50 / 60 / 65 | 53.6 / 55 / 50 / 60 / 60 | 8881 -> 11592 | 3.92 -> 5.95 | 18.3 -> 26.2 | 20.9 -> 19.5 | 0.0 -> 0.0 | 1.2 -> 1.1 | 0 -> 0 | 0.0017 -> 0.0018 | 95.6 -> 98.0 |
| maxed EXPERT_ACTIVE | 55.4 / 55 / 50 / 60 / 70 | 56.0 / 55 / 50 / 60 / 65 | 10383 -> 14091 | 5.16 -> 7.46 | 19.2 -> 27.6 | 19.1 -> 18.5 | 0.0 -> 0.0 | 0.7 -> 0.7 | 1.4 -> 0 | 0.0015 -> 0.0015 | 96.3 -> 98.0 |

Refusal mix, share of visits (NOT_BETTER / TOO_EXPENSIVE): BALANCED_FAIR 54.9 / 20.0 -> 57.1 / 20.0; BALANCED_ACTIVE 48.6 / 27.1 -> 46.0 / 30.5;
NOVICE 42.7 / 32.2 -> 43.2 / 34.2; EXPERT 57.1 / 19.6 -> 54.6 / 23.1; maxed BALANCED_ACTIVE 33.9 / 46.8 -> 33.3 / 47.6. Mean days at the
three base seeds (1 / 10001 / 20001) after: BALANCED_FAIR 22.3 / 22.5 / 22.2, BALANCED_ACTIVE 29.4 / 29.4 / 29.9, EXPERT 44.4 / 44.8 / 44.7,
maxed EXPERT 53.6 / 54.1 / 53.9, NOVICE 26.8 / 26.8 / 26.9. All 28 new-account rows and the five maxed rows, three seeds each, are in the
tables file. Upgrade impact on the new numbers (BALANCED_FAIR, seed 1): all maxed +14.6 mean days (was +15.0), Stalwart Walls +6.0,
Well-Stocked Cellar +5.1, Forge Mastery +2.3; for EXPERT all maxed +9.1 (was +10.4).

### The sweep (plan 4.3; 1,000 runs, base seed 1 unless noted)
- **Step 2, residents alone (4 seats, uncompensated).** Mean days at 8 / 10 / 12 / 14 / 16 residents: BALANCED_FAIR 21.1 / 23.0 / 24.2 /
  25.7 / 26.5; BALANCED_ACTIVE 27.4 / 30.9 / 32.9 / 34.9 / 36.2; SYNERGY 32.8 / 35.7 / 37.2 / 38.2 / 38.9; BALANCED_EXPENSIVE 12.3 / 13.1 /
  13.8 / 14.2 / 15.1; PASSIVE 10.0 throughout. About +0.8 mean days per extra resident for a plain smith from 8 to 12, less after. Sales a
  day (FAIR) 1.00 / 1.11 / 1.22 / 1.30 / 1.39; distinct heroes served 9.7 / 12.1 / 14.4 / 16.7 / 18.9; first siege held 67 / 80 / 83 / 90 /
  93 %. At four seats served a day stays at 4.0 and the turned-away count climbs to 3.2 a day at twelve: residents without seats do not
  make a busier counter. The "as today" arm (8, refill below 5, cap 12) reproduces the unedited tree within 0.3 days on every policy.
- **Step 3, seats alone.** At 12 residents, seats 4 / 5 / 6 / 8: served a day (FAIR) 3.97 / 4.88 / 5.67 / 6.79; shop full on 96 / 90 / 79 /
  48 % of days; conversion 27.3 / 23.0 / 20.3 / 17.5 %; sales a day 1.22 / 1.26 / 1.30 / 1.33; mean days 24.2 / 24.6 / 24.7 / 24.7. Seats
  move who is seen, hardly who buys or how long the town lives. At 8 residents six seats fill on 27 % of days and eight on 4 %: the
  Signboard would be dead. Six at twelve leaves the Signboard two seats that are wanted (7.2 served a day with it).
- **Step 4, compensation at (12, 6).** Mean days (median):

| expedition suppression, raid growth | BALANCED_FAIR | BALANCED_ACTIVE | BALANCED_EXPENSIVE | SYNERGY | NOVICE | EXPERT | maxed FAIR | maxed ACTIVE | maxed EXPERT (p90, longest) |
|---|---|---|---|---|---|---|---|---|---|
| before T3.4 (8 residents, 4 seats, 3, 6.0) | 20.8 (20) | 27.4 (25) | 12.4 (10) | 32.6 (35) | 23.2 (25) | 42.8 (45) | 35.8 | 42.4 | 53.2 (60, 65) |
| 3, 6.0 (uncompensated) | 24.7 (25) | 33.0 (35) | 14.0 (15) | 37.5 (40) | 30.0 (30) | 47.6 (50) | 39.7 | 46.8 | 57.0 (60, 65) |
| 3, 6.5 | 23.4 (25) | 30.9 (30) | 13.6 (15) | 35.5 (35) | 27.9 (30) | 44.9 (45) | 37.6 | 44.4 | 54.2 (60, 60) |
| 3, 7.0 | 22.1 (20) | 29.0 (30) | 13.2 (15) | 33.8 (35) | 26.1 (25) | 42.7 (45) | 35.7 | 42.2 | 51.7 (55, 60) |
| 2, 6.0 | 23.6 (25) | 31.6 (30) | 13.8 (15) | 37.1 (35) | 28.7 (30) | 47.2 (50) | 39.0 | 46.3 | 56.6 (60, 65) |
| **2, 6.5 (chosen)** | **22.3 (20)** | **29.4 (30)** | 13.4 (15) | 35.2 (35) | 26.8 (30) | 44.4 (45) | 36.8 | 43.8 | 53.6 (60, 60) |
| 2, 7.0 | 21.0 (20) | 27.6 (30) | 13.0 (15) | 33.4 (35) | 25.1 (25) | 42.3 (45) | 35.2 | 41.5 | 51.1 (55, 60) |

  Uncompensated, a plain first era is four days longer (median 25) and the active one 33.0: outside the band (FAIR median 20, mean at most
  22.5; ACTIVE mean at most 30.5). Three arms hold both rows. **(2, 6.5) is chosen** because it is the plan's first lever in full (the one
  channel that grows with head count) and half of the second, and it is the only passing arm that leaves no maxed-account row below its
  baseline (maxed EXPERT 53.6 against 53.2; at raid 7.0 it would be 51.1-51.7, a reduction nobody asked for). It is also the only passing
  arm under the 2-day line of step 4b for both policies. Suppression 2 alone or raid 6.5 alone leaves FAIR at 23.4-23.6, median 25.
  Finer values at suppression 2, FAIR mean at seeds 1 / 10001 / 20001: raid 6.5 22.3 / 22.5 (22.455) / 22.2; 6.6 22.1 / 22.2 / 21.9; 6.75
  21.7 / 21.8 / 21.6. 6.5 holds at all three seeds with 0.05 days to spare at one of them; 6.6 would buy margin by taking maxed EXPERT
  slightly under its baseline, so the round number stands. `populationTarget` 12 -> 10 (the third lever) was not needed.
- **Step 4b, a run saved before this update.** 1,000 towns per base seed played to the morning of day 8 under the old numbers, then
  continued: under the chosen numbers BALANCED_FAIR loses 0.75 / 0.81 / 0.75 mean days against continuing under the old ones (20.2 against
  20.95 at seed 1) and BALANCED_ACTIVE 1.25 / 1.25 / 1.19 (26.1 against 27.3). Under the plan's line of 2 days, so admitted runs get no
  special case. (At raid 7.0 the active loss is 2.1-2.75 days.) Comparator: the tree before T3.4, not balance v5.

### The M3 band of plan 4.3 (1,000 runs at each of base seeds 1 / 10001 / 20001)
| measure | threshold | before | after | 1 | 10001 | 20001 |
|---|---|---|---|---|---|---|
| BALANCED_FAIR days: median (p10/p90) mean | median 20, p10 >= 15, p90 <= 30, mean 19.5-22.5 | 20 (15/25) 20.8 / 21.0 / 20.8 | 20 (15/25) 22.3; 20 (15/30) 22.5; 20 (15/25) 22.2 | HOLDS | HOLDS (22.455) | HOLDS |
| BALANCED_ACTIVE days | median 25-30, mean <= 30.5 | 27.4 / 27.4 / 27.1 | 30 (20/35) 29.4; 30 (20/35) 29.4; 30 (25/35) 29.9 | HOLDS | HOLDS | HOLDS |
| BALANCED_EXPENSIVE, PASSIVE median | 10-15 and 10 | 10, 10 | 15, 10 | HOLDS | HOLDS | HOLDS |
| every run ends (28 new-account rows, 5 maxed) | all | all | all | HOLDS | HOLDS | HOLDS |
| maxed above new, mean days: FAIR / ACTIVE / SYNERGY | at least +10 (distributions reported, not capped) | +15.0 / +15.0 / +11.3 | +14.5 / +14.4 / +10.0; +14.5 / +14.4 / +10.2; +14.9 / +13.8 / +10.3 | HOLDS | HOLDS | HOLDS |
| served a day, BALANCED_FAIR | 5.0-6.0 | 3.55 | 5.63 / 5.65 / 5.65 | HOLDS | HOLDS | HOLDS |
| days with two or fewer visitors, BALANCED_FAIR | <= 5 % | 12.5 / 12.7 / 11.9 % | 1.2 / 1.2 / 1.2 % | HOLDS | HOLDS | HOLDS |
| served a day, BALANCED_ACTIVE | 6.0-7.5 | 4.67 | 7.10 / 7.10 / 7.14 | HOLDS | HOLDS | HOLDS |
| distinct heroes served, BALANCED_FAIR: by day 5 / in a run (means) | >= 9 / >= 13 | 7.5 / 9.2 | 11.4 / 14.2 at each seed | HOLDS | HOLDS | HOLDS |
| fairness by position (worst / best visit rate, positions 1-12) | max / min <= 2.5 | 0.97 (1-8) | 0.96 / 0.97 / 0.96 | HOLDS | HOLDS | HOLDS |
| newcomer wait, days: median / p90 | <= 2 / <= 4 | 1 / 3 | 0 / 2 | HOLDS | HOLDS | HOLDS |
| sales a day: FAIR / ACTIVE | 1.2-2.0 / 1.5-2.4 | 0.97 / 1.18 | 1.36 / 1.71; 1.34 / 1.71; 1.35 / 1.71 | HOLDS | HOLDS | HOLDS |
| conversion: FAIR / ACTIVE | not more than 3 points below baseline | 23.4 / 22.6; 23.1 / 22.4; 23.4 / 22.5 % | 21.6 / 22.1; 21.2 / 22.0; 21.5 / 22.0 % | HOLDS | HOLDS | HOLDS |
| 4 of 5 classes have bought by day 10: FAIR / ACTIVE | >= 90 % of runs | 83.1 / 82.5 % | 99.9 / 99.7; 100 / 100; 100 / 99.9 % | HOLDS | HOLDS | HOLDS |
| deaths per hero-day: FAIR / ACTIVE | <= 1.15 x baseline | 0.0045 / 0.0035 | 0.0042 / 0.0031; 0.0041 / 0.0032; 0.0042 / 0.0030 | HOLDS | HOLDS | HOLDS |
| legacy points, median, BALANCED_FAIR | within 2 of baseline | 26 | 27 | HOLDS | HOLDS | HOLDS |
| hard-lock days | 0 | 0 in every row | 0 in 32 of 33 rows; SPENDTHRIFT 1 / 3 / 3 days in 1,000 runs | **FAILS** (SPENDTHRIFT) | **FAILS** (SPENDTHRIFT) | **FAILS** (SPENDTHRIFT) |
| stuck days (T5.2 probe): FAIR / worst row | <= 0.5 % of days | 0 / 1.21-1.45 % (EXPERT_ACTIVE) | 0 / 0.10-0.11 % (SPENDTHRIFT) | HOLDS | HOLDS | HOLDS |
| shared first name / surname among the living | 0 % of days | 0 / 0 | 0 / 0 | HOLDS | HOLDS | HOLDS |
| shared face among the living, BALANCED_FAIR | reported (T3.3 saves a face per hero) | 49.9 / 53.8 / 47.9 % | 88.4 / 90.3 / 88.7 % | reported | reported | reported |

Reported, not gated: served share max / min among heroes alive ten days or more, per run, median 2.2 and p90 3.2-3.3 (2.2 and 3.3-3.45
before; ruling of T3.1); refusal mix above; SYNERGY 35 (30/40) 35.2 and the maxed distributions in the table above.

**The one failing line.** The "hard-lock" counter is the old simulator count of days on which a policy forged nothing and had nothing on
the shelf from the day before (T5.2 showed it is not a stuck state). SPENDTHRIFT, the bot that spends every coin on tools each morning,
now has 1, 3 and 3 such days in 1,000 runs at the three seeds (it had none), because six customers sell its shelf out on a day it cannot
buy iron. The stuck-state probe that replaced the counter improves for the same bot (stuck days 0.56 % -> 0.11 %, streaks of three or more
1.0 % -> 0.2 % of runs). Left failing as written: no lever of this task addresses a bot emptying its own purse, and none was tried.

**Margins worth knowing.** BALANCED_FAIR's mean sits at the top of its band (22.2-22.5 against 22.5) and maxed SYNERGY is +10.0 to +10.3
over a new account against the gate of +10. Every M4 loop pushes the first up; sweep step 6b re-runs this grid when FAIR or ACTIVE moves.

### Tripwire (restated by ruling: new-account EXPERT mean above 50.4, maxed EXPERT p90 above 70, or any 100-day run)
Not crossed. New-account EXPERT mean 44.4 / 44.8 / 44.7 (42.8 / 43.0 / 43.3 before). Maxed EXPERT mean 53.6 / 54.1 / 53.9, p90 60 at
every seed, longest 60 / 60 / 65 (before: 53.2 / 53.4 / 53.5, p90 60, longest 65). Longest run of any of the 99 policy-seed rows: 65 days
(maxed accounts; 70 before). Uncompensated (3, 6.0) the same rows read 47.6 and 57.0 (p90 60, longest 65): also under the lines, so
the compensation was chosen for the first-era band, not for the tripwire.

### Tests, golden file, fingerprint
- New `PopulationTest`: `everyRunStartsWithAllFiveClasses` (1,000 seeds; each class opens 150-250 of them; a descendant of each class
  keeps it and counts), `arrivalsRefillTowardTheTarget` (4,000 draws per population: 0, 0.15, 0.30, 0.45 at 12, 11, 10, 9 alive, certain
  below 9, capped at 0.6; 200 played runs stay at nine or more on 97 % of days and never pass 17), `oneHeroesDrawPerDayForArrivals` (the
  stream advances by one draw at 3 to 16 alive when nobody comes, and by one draw plus the newcomer's when somebody does).
- `SaveFixtureTest.olderSavesJoinTheLargerTown`: all five fixtures (schema 1, 1, 2, 2, 3; 9-11 living heroes) decode, are admitted with
  nobody added, accept ten End Days with invariants held and the counter within its seats, and draw newcomers. There is no schema-4 fixture
  yet (T3.3 still adds a schema-4 field; the gate records one).
- `VersionFingerprintTest`: the balance-7 row was **re-pinned** (66214786... -> 33af5dfb...). The file's own rule is a new row per changed
  number; the dispatch keeps balance at 7 because no balance-7 build has left development. If that is wrong, the fix is one line: version 8.
- Adjusted for twelve heroes: `ChampionSelectionTest` (tie-break compared in numeric ID order now that h10-h12 exist on day 1; every
  second town starts under a warlord, since twelve armed heroes no longer let pressure reach one in 12 seeds),
  `CustomerSelectionTest.turnedAwayIsStoredOnTheDayAndCounted` (pins four seats, as its siblings do), `HeroDailyLifeTest` (the four heroes
  beyond the eight it scripts are wounded and guildless), `EventCompactionTest` (a 400-day compacted log is 1,408 events with twelve
  residents; the bound moves from 1,200 to 1,700).
- **Golden file `state_rules3.txt` re-recorded** (20 seeds x 15 days): all 300 lines and all 300 RNG hashes differ, from day 1 of every
  seed (twelve starting heroes are four more generations on the HEROES stream before day 1, and every End Day has four more willingness
  draws and one arrival draw). Fifteen of the old lines were "ended" (runs of the golden script that fell by day 15); none is now.
  `state_rules1.txt` and `state_rules2.txt` are untouched.

### Commands and evidence
`./gradlew :core:simulate --args="--runs 1000 --seed <1|10001|20001> --policy <all|bots> --customers --noImpact"`, the same with
`--policy EXPERT,EXPERT_ACTIVE,BALANCED_FAIR,BALANCED_ACTIVE,SYNERGY --upgrades <all eleven>=3` for the maxed rows, and at seed 1 without
`--noImpact` (`--policy all`, and `--policy bots --impactPolicy EXPERT`). Sweep arms through `--set` (new keys `populationTarget`,
`arrivalChancePerMissing`, `arrivalChanceMax`). Step 4b through `SimulationDriver.playRun(from = state)` (new optional parameter) from a
probe test that is not committed. Raw outputs `T3.4-*.txt` and the full tables `T3.4-tables.md` are in the session scratchpad (`exec/`).

### Not changed, and not measured
Wear, purses, prices, trade-ins, energy, siege interval, champions: unchanged. Guild Patronage and festival seats 2 against 3 are sweep
step 5 (T3.6). 10,000 seeds are step 7 (T5.5). The Town list and the counter at 16 heroes on a device are T3.7. Faces repeat among twelve
heroes on about 89 % of days until T3.3 gives each hero a saved face.

## Recognition lines at the counter (2026-10-09, task T3.5; N05)

One recognition line at most per browsing visit, chosen in core at End Day (`shopday/Recognition.kt`, object `Recognitions`) and stored on
the visit (`MarketVisit.recognition`), so the counter, a relaunch and any later reader show the same line. Sentences are authored templates
in `shopday/Lines.recognition`; a sentence whose field is gone is not shown. Narration only: no gameplay stream is drawn and no rule reads
what it writes. Outcomes are unchanged: `golden/state_rules3.txt` passes unrecorded, and the nine 1,000-run simulations (base seeds 1 /
10001 / 20001 x classic policies, bots, maxed accounts) are identical before and after except the new recognition line and the corrected
face line. No balance or content number changed; neither fingerprint moved.

**Rules.** Milestones are told once per hero per run, the rarer first when one visit earns several: a descendant's first visit, became a
regular, first blade, first visit, back from the wall with a blade of yours, a kept slayer's vow. The last two wait for the next visit
when another milestone wins; the others are spent. Recurring lines (a regular returns, still carries a blade bought here, a worn edge,
an elite slain with a titled blade, the retired mentor's blade, could not get in yesterday) need two earlier visits, three days since the
hero's last line of any kind, and never repeat the hero's previous cue; among several the pick is a hash of run seed, hero ID and day.
Memory is three defaulted `Hero` fields (`lastLineDay`, `lastLineCue`, `milestoneLines`) inside the unreleased schema-4 step; an older save
reads them as "nothing told yet". Commission and empty-shelf visits carry no line.

**Pacing constants** (`Recognitions`, presentation like `ShopDay.FEATURED_MAX`, not `BalanceConfig`): `INTRO_DAYS` 3, `INTRO_DAY_MAX` 1,
`RECURRING_MIN_VISITS` 2, `COOLDOWN_DAYS` 3.

**Departures from plan 4.5, each forced by its own targets.**
- "Milestones always show" against "days 1-3 at most 20 % of visits": uncapped, 52 % of day 1-3 visits carried a line (first blades; a
  first visit on every visit of day 1 on top). So on days 1-3 a first visit is not a line and a day tells one line at most; a first blade
  that loses that day is not told later. Measured after: 19.5-19.9 % (BALANCED_FAIR).
- No per-day cap after day 3. "Never more than three in the featured visits" holds because three visits are featured.
- "Brought down {foe}" reads "an elite foe": the foe's name is not a stored field of the hero or the record.
- "The edge is {worn / battered}" reads "worn": a second word needs a second threshold, which nothing else uses.

**Share of browsing visits with a line** (1,000 runs; base seeds 1 / 10001 / 20001):

| policy | days 1-3 | days 4-5 | from day 6 (target 35-50 %) |
|---|---|---|---|
| BALANCED_FAIR | 19.8 / 19.9 / 19.5 | 42.4 / 42.6 / 42.5 | 42.6 / 42.6 / 42.3 |
| BALANCED_ACTIVE | 18.9 / 19.0 / 18.6 | 41.2 / 40.8 / 42.0 | 36.8 / 36.7 / 36.9 |
| SYNERGY | 18.7 / 18.8 / 18.5 | 43.9 / 43.7 / 42.5 | 48.9 / 48.4 / 48.6 |
| NOVICE | 15.9 / 15.9 / 15.8 | 42.2 / 42.1 / 42.3 | 44.8 / 44.5 / 45.0 |
| EXPERT | 18.3 / 18.3 / 18.4 | 38.0 / 38.1 / 37.8 | 50.4 / 50.4 / 50.4 |
| BALANCED_EXPENSIVE | 5.4 / 5.2 / 5.4 | 28.0 / 27.5 / 27.7 | 25.2 / 24.7 / 25.0 |
| maxed BALANCED_FAIR | 12.6 / 12.5 / 12.4 | 42.1 / 41.3 / 41.2 | 49.0 / 49.0 / 49.0 |
| maxed EXPERT | 14.1 / 13.8 / 13.7 | 34.0 / 34.5 / 35.0 | 49.8 / 49.9 / 50.0 |

Outside the target: EXPERT by 0.4 points (above), and the two policies that price at 180 % (about 25 %: almost nobody buys, so there are
few blades to speak of). Every other of the 33 rows is inside 36-50 %. BALANCED_FAIR, seed 1: 2.25 lines a day; of the lines, still
carries 26 %, a regular returns 20 %, first blade 17 %, worn edge 9 %, became a regular 8 %, held the wall 6 %, waited yesterday 6 %,
elite 4 %, kept the vow 3 %, first visit 2 %, mentor's blade 0.4 %.

**Simulator.** `--customers` prints the recognition line (share by day band, lines a day, cue mix). Its "face shared" number now reads
the stored face (`Appearance.keyOf`) instead of the pre-T3.3 hash: 25-42 % of days across the classic policies (it printed 88-90 %),
0.4 pairs a day, never more than three alike. It is above zero because a class has three to five faces (plan band: reported once a class
has more living members than faces).

**Tests.** `RecognitionTest` (6): `everyCueIsJustifiedByItsFields` (30 towns to day 30, each stored line checked against the morning
state; eleven of twelve cues occur, the mentor's blade is built by hand in `theMentorsBladeIsToldOnlyWhileItIsCarried`),
`milestonesShowOncePerHeroPerRun`, `recurringLinesRespectTheCooldown`, `atMostThreeAmongTheFeatured` (also the opening-day cap and a
30-55 % share from day 6 under the golden script), `choiceLeavesEveryRngStreamUnchanged`.

**Not done here.** The counter does not show the line yet (the app half of T3.5, `VisitCard` and the regular pip, is the UI agents').
The Gazette text is unchanged. The later cues of plan 4.5 (a want answered, the guild paid, once another owner's) belong to their tasks.

## Guild Patronage: willing members and a guild stipend (2026-10-09, task T3.6; G03)

**What it is now.** For its five days every guild member is willing to visit at the ceiling (`visitCeiling` 0.9), and the member's guild
pays `CustomerConfig.patronageStipend` **30** gold toward one purchase per member per blessing. The stipend counts toward what the member
can afford (`Market.evaluate`, and the "short by" of a refusal), is capped at what is owed after the trade-in, reaches the till as its own
income (`IncomeKind.STIPEND`), and is recorded on the sale (`Sale.stipend`; `cashPaid` is the member's own coin), on the `WEAPON_SOLD`
record (`data["stipend"]`) and in the receipt line of `Lines.decision`. The blessing is not among the town's three choices while no guild
stands (`Battle.offerBlessing`). The old effect (`HERO_VISIT_CHANCE`, +15 points of visit chance for everyone) is gone from the enum, the
market and the simulator; the effect is now `BlessingEffect.GUILD_PATRONAGE` (a flag; the number lives in `BalanceConfig`).
"One per member per blessing" is `Hero.stipendSpentFor` (the blessing's last day), a defaulted field inside the unreleased schema-4 step.

**Versions.** Balance stays 7 and content stays 3 (one unreleased M3 step); both fingerprint rows were re-pinned
(content `aa0a71b8...` -> `d9befd94...`, balance `33af5dfb...` -> `33499217...`). Rules 3, schema 4.

**Sweep step 5** (plan 4.3; 1,000 runs at base seeds 1 / 10001 / 20001; every policy takes Patronage whenever it is offered, against the
same policy taking the first blessing offered; full table `T3.6-step5-table.md` in the session scratchpad). Deltas against the default arm:

| arm | BALANCED_FAIR mean days | BALANCED_ACTIVE mean days | FAIR sales a day | ACTIVE sales a day | stipend share of gold earned (FAIR / ACTIVE / EXPERT) |
|---|---|---|---|---|---|
| stipend 0 (members willing only) | +0.0 / -0.2 / -0.1 | -0.4 / -0.2 / -0.3 | +0.00 | -0.01 | 0 |
| stipend 20 | +0.0 / -0.1 / -0.1 | -0.2 / -0.1 / -0.1 | +0.00 | +0.01 | 0.6 / 1.4 / 1.9 % |
| **stipend 30 (shipped)** | +0.0 / -0.1 / +0.0 | -0.1 / +0.0 / +0.0 | +0.00 | +0.02 | 0.9 / 2.2 / 3.0 % |
| stipend 40 | +0.1 / -0.1 / +0.0 | -0.1 / +0.2 / +0.1 | +0.00 | +0.03 | 1.3 / 3.1 / 4.2 % |
| guests: 2 members seated beyond capacity, no stipend | +0.0 / -0.2 / +0.0 | -0.2 / -0.2 / -0.2 | +0.00 | -0.01 | 0 |
| stipend 30 and 2 guests | +0.0 / -0.1 / +0.0 | +0.0 / +0.0 / -0.1 | +0.00 | +0.03 | 1.0 / 2.3 / 3.1 % |
| stipend 30, festival seats 2 instead of 3 | +0.0 / -0.1 / +0.0 | -0.1 / +0.0 / +0.0 | +0.00 | +0.02 | as stipend 30 |

Step 5's rule: at least +0.6 mean days or +0.3 sales a day under FAIR or ACTIVE; at most +2.0 days under any policy; under 10 % of shop
income. **Every arm passes the two ceilings and no arm reaches the floor.** The blessing is live on too few days to move a whole-run mean:
a plain smith holds 1.4 sieges a run and takes Patronage 0.37 times (1.9 of 22 days); the active smith 0.8 times. The old +15-point
version measured -0.2 / -0.3 days against the default blessing.

**What one blessing is worth (its own yardstick).** Per Patronage taken: 2.3 purchases with a stipend for a plain smith (69 gold from the
guilds), 4.8 for the active smith (143 gold) and for EXPERT (145 gold); with twelve residents nearly everyone is in a guild by the first
won siege (a guild stands in 99-100 % of runs). For scale, Merchant's Favor (a fifth more gold on sales for five days) is about 185 gold for
the active smith. "Too expensive" refusals fall 0.2-1.0 points over a whole run (EXPERT, which always takes it: 23.1 -> 20.7 %). Because
the seats were already full, members at the ceiling mostly add to those turned away (ACTIVE 0.93 -> 1.03 a day under Patronage): the
willingness half is what the Signboard is for.

**Decision.** Stipend 30 ships (the plan's default); the "guests" arm is not kept in the code. G03 is closed on the mechanism, **not on
the step-5 floor**, which no arm met. Nothing was retuned to meet it. Smallest levers, if the owner wants the blessing to register on a
whole-run mean: stipend 40 (+0.03 sales a day, still 4.2 % of income for the heaviest user); stipend plus two guest seats; or judging a
five-day blessing on its own five days rather than on run length. Festival seats stay 3 (2 against 3 moves nothing here).

**Effect of the change itself** (every policy on its own habit; before = T3.5, after = this task; mean days at seeds 1 / 10001 / 20001):
BALANCED_FAIR 22.3 / 22.5 / 22.2 -> 22.2 / 22.5 / 22.3 (exact 22.225 / 22.465 / 22.310; median 20, p10 15, p90 30 / 30 / 25);
BALANCED_ACTIVE 29.4 / 29.4 / 29.9 -> 29.5 / 29.6 / 30.0; SYNERGY 35.2 -> 35.1 / 35.2 / 35.2; NOVICE 26.8 / 26.8 / 26.9 -> 26.8 / 26.8 / 27.0;
SIEGE_PREP 43.3 / 43.4 / 43.2 -> 43.3 / 43.6 / 43.4; EXPERT (takes Patronage) 44.4 / 44.8 / 44.7 -> 44.7 / 44.9 / 45.0 (p90 50, longest 55);
EXPERT_ACTIVE 46.4 / 46.4 / 46.5 -> 46.9 / 47.0 / 47.2 (longest 60); maxed BALANCED_FAIR 36.8 / 37.0 / 37.1 -> 36.9 / 37.1 / 37.1; maxed
SYNERGY 45.2 / 45.4 / 45.5 -> 45.3 / 45.4 / 45.5; maxed EXPERT 53.6 / 54.1 / 53.9 -> 53.9 / 54.2 / 54.0 (p90 60, longest 60); maxed
EXPERT_ACTIVE 56.0 / 56.2 / 56.0 -> 56.3 / 56.7 / 56.6 (p90 60, longest 65). Gold earned: within 1 % except EXPERT_ACTIVE +5 % (400 gold of
stipends a run). Deaths per hero-day, served a day, conversion, legacy points and the fairness numbers are unchanged within seed noise.

**M3 band.** Holds at all three seeds as after T3.4: FAIR mean inside 19.5-22.5 (top edge: 22.465 at seed 10001), ACTIVE mean at most
30.03, EXPENSIVE median 15, PASSIVE 10, every run ends, maxed at least 10 mean days above new (SYNERGY +10.2 / +10.2 / +10.3), deaths per
hero-day FAIR 0.0040-0.0043, legacy median 27. The old hard-lock counter still fails for SPENDTHRIFT only (1 / 4 / 2 days in 1,000 runs),
as before. **Tripwire** not crossed: new-account EXPERT mean 44.7-45.0 against 50.4; maxed EXPERT p90 60 against 70; longest run 65 days.

**Tests.** `PatronageTest` (4): `guildMembersAreWillingAtTheCeiling`, `oneStipendPerMemberPerBlessing`,
`theStipendIsOnTheReceiptAndInTheLedger`, `notOfferedWithoutAGuild`; `CustomerSelectionTest.saturationUnderPatronageKeepsServedShareMaxOverMinAtMostTwoAndAHalf`
(sixteen members at the ceiling, six seats: equal heroes within 1.15, regulars against strangers at most 2.5).
**Golden file `state_rules3.txt` re-recorded:** 32 of 300 lines change, in 4 of 20 seeds (1, 2, 10, 12), each from day 5; the RNG column is
identical on every line. Reason: those towns hold the day-5 siege with no guild standing, so the blessing offer is drawn from seven
blessings instead of eight (the same three draws on the LEGACY stream, different picks), and the golden script never takes the offer.

**Simulator.** A "guild patronage/run" line on every policy row (times taken, purchases with a stipend, stipend gold and its share of gold
earned); `--set patronageStipend=N`. The dispatch asked for "impact rows for the Guild Patronage upgrade": it is a blessing, not a legacy
track, so there is no upgrade row; the per-blessing yardstick above stands in for it.

## Wall deaths made reachable: the rout rule (2026-10-09, task T3.8; X19)

**The gap.** A champion could not die on the walls: only heroes at `heroWoundedThreshold` 50 health or more are chosen, a lost siege
cost each 40 (`championSiegeDamageOnLoss`), and death is at 0. The "died defending the walls" branch and its two odds
(`wallsRecoveryChance` 0.7, `wallsSeizureChance` 0.3) never ran.

**The rule (adopted).** A siege lost with the raid at `weaponFates.wallsRoutRatio` **1.5** times the town's defense or more is a rout:
each champion takes `weaponFates.wallsRoutDamage` **55** instead of 40. A champion who went up at 50-55 health falls; anyone fitter
survives (at 56 with 1 health), and a narrower loss wounds as before. The fallen champion's blade takes the walls' fates that already
existed. The `SIEGE_LOST` record says "routed the defenders" and carries `data["rout"]`. Balance stays 7 (unreleased M3 step; fingerprint
re-pinned `33499217...` -> `daf9ca72...`); rules 3, schema 4.

**Sweep** (1,000 runs at base seeds 1 / 10001 / 20001, every classic policy, every bot and five maxed rows; full table
`T3.8-sweep-table.md` in the session scratchpad). Damage 40 leaves the rule inert and reproduces the tree before this task byte for byte.

| rout damage (ratio 1.5) | FAIR deaths per hero-day | ACTIVE deaths per hero-day | FAIR champions fallen a run | ACTIVE | EXPERT | FAIR mean days |
|---|---|---|---|---|---|---|
| 40 (inert) | 0.0043 / 0.0040 / 0.0042 | 0.0031 / 0.0033 / 0.0030 | 0 | 0 | 0 | 22.2 / 22.5 / 22.3 |
| 50 | 0.0045 / 0.0042 / 0.0045 | 0.0031 / 0.0033 / 0.0031 | 0.04 / 0.05 / 0.06 | 0.01-0.02 | 0.004-0.008 | 22.2 / 22.5 / 22.3 |
| **55 (adopted)** | 0.0051 / 0.0046 / 0.0049 | 0.0034 / 0.0035 / 0.0033 | 0.22 / 0.20 / 0.20 | 0.08-0.10 | 0.04 | 22.2 / 22.5 / 22.3 |
| 60 | 0.0059 / 0.0055 / 0.0058 | 0.0036 / 0.0038 / 0.0036 | 0.44 / 0.42 / 0.45 | 0.20-0.22 | 0.12-0.13 | 22.2 / 22.5 / 22.3 |
| 55 at ratio 1.25 | 0.0051 / 0.0050 / 0.0052 | 0.0036 / 0.0038 / 0.0034 | 0.27 / 0.28 / 0.28 | 0.16-0.18 | 0.07-0.09 | 22.2 / 22.5 / 22.3 |

**Adoption test** (the plan's: adopt only if deaths per hero-day and BALANCED_FAIR stay inside the M3 band). Band: deaths per hero-day
at most 1.15 x the M0 value, 0.0045 for FAIR (limit 0.00518) and 0.0035 for ACTIVE (limit 0.00403). At 55: FAIR 0.00507 / 0.00464 /
0.00493 and ACTIVE 0.00335 / 0.00349 / 0.00325: **inside, by 0.0001 at seed 1**. FAIR days: mean 22.215 / 22.465 / 22.315, median 20,
p10 15, p90 30 / 30 / 25: inside (and unchanged: a fallen champion does not shorten the run measurably). 60 is outside (FAIR 0.0055-0.0059)
and so is ratio 1.25 at seed 20001 (0.0052). 50 is inside but nearly inert, and it leaves a full-health champion at exactly 50, which is
not "wounded", so they go straight back out (PASSIVE dies more at 50 than at 55 for that reason). So 55 at 1.5, the plan's start values.

**What a player will see.** About four lost sieges in ten are routs for a plain smith (1.3 of 3.0 lost a run). A champion falls on the
walls in about one plain first era in five (19 / 17 / 19 % of runs), one active era in ten to thirteen, one expert era in about twenty-five; on a maxed
account 0.10-0.16 a run. Most routs kill nobody: the champions are usually well above 55. A shop that prices itself out of its town sees
it most (BALANCED_EXPENSIVE 0.31-0.36 a run, deaths per hero-day 0.0065 -> 0.0081-0.0086).

**Other rows** (before -> after, seeds 1 / 10001 / 20001; days are unchanged within 0.1 everywhere):
deaths per hero-day SYNERGY 0.0027 / 0.0025 / 0.0027 -> 0.0029 / 0.0028 / 0.0029; NOVICE 0.0040 / 0.0044 / 0.0039 -> 0.0044 / 0.0046 /
0.0042; EXPERT 0.0019 / 0.0018 / 0.0017 -> 0.0020 / 0.0019 / 0.0018; maxed BALANCED_FAIR 0.0025 -> 0.0028; maxed EXPERT 0.0018 / 0.0017 /
0.0016 -> 0.0019 / 0.0018 / 0.0017. Maxed EXPERT 53.9 / 54.2 / 54.0 -> 53.8 / 54.2 / 54.1 days (p90 60, longest 60); maxed SYNERGY 45.3 /
45.4 / 45.5 -> 45.2 / 45.4 / 45.4, which is +10.1 / +10.2 / +10.2 over a new account against the gate of +10. Tripwire not crossed (new
EXPERT 44.7 / 44.9 / 45.0; longest run of any row 65 days). Every run ends.

**Warlord sieges** (T3.4 concern: 0.8 -> 0.3 a run for a plain smith with twelve residents). Unchanged by this rule: BALANCED_FAIR 0.3 /
0.3 / 0.4, BALANCED_ACTIVE 0.3 / 0.3 / 0.2, EXPERT 0.0-0.1, NOVICE 0.6-0.7, PASSIVE 0.8-0.9, BALANCED_EXPENSIVE 1.1-1.2. Wall deaths do
not depend on the warlord: they come from ordinary routs. The warlord's rarity is still open and nothing here addresses it.

**Tests.** `SiegeWallTest` (3): `aNarrowLossNeverKillsAHealthyChampion` (40 seeds x three ratios below 1.5 with every champion at the
weakest health allowed; and a threefold rout against champions at 56), `aRoutCanKillAChampionAtTheThreshold` (at 50 and at 55, just
past the ratio and far past it), `theFallenChampionsBladeTakesAWallsFate` (600 fallen champions: one fate each, recovered 70 % +/- 6).
`ReplaysAndWeaponFatesTest.wallsFates` now forces deaths through the rout damage. **Golden file `state_rules3.txt` re-recorded:** 65 of
300 lines change, in all 20 seeds, from the day-10 siege (9 seeds) or the day-15 one (11 seeds); the RNG column moves on 50 lines in 12
seeds. Reason: the golden script loses those sieges as routs, so the champions end them 15 health lower (the projection prints health),
and from then on some rest instead of acting (HEROES and COMBAT draws differ) and a few fall (COMBAT draws for the blade's fate).

**Simulator.** A "wall" line on every policy row (routs a run, champions fallen a run, share of runs with one); `--set wallsRoutDamage=N`,
`--set wallsRoutRatio=X`. `FieldOutcome.FELL_AT_THE_WALL` now occurs.
