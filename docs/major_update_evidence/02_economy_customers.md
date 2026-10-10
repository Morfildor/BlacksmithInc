# 02 — Economy, customers, names and new loops (designer-reviewer report)

Planning only. Nothing in the project was created, edited or run (no Gradle). Numbers marked **measured** come from
`docs/DECISIONS.md`. Numbers marked **arithmetic** are my own closed-form or back-of-envelope work from the current
config; they are hypotheses until the simulator confirms them.

## 0. Snapshot and reading notes

- **The repository moved twice while I was reading.** `main` is now `8cc133b` ("Merge fight replays and weapon fates
  (balance v5 part 3)"), on top of `77919fe` (hero daily life, v5 part 2) and `d7b3283` (signboard and affix
  magnitudes, v5 part 1). The dirty-tree signboard/affix edits and the hero-daily-life worktree named in my brief are
  **committed and merged**, and so is the fight-replay / weapon-fates branch. The working tree is clean apart from
  untracked docs and art. Every `path:line` below is against `8cc133b`; lines will shift again when the last branch lands.
- **After this report was written `main` moved again**, to `eac17dc` ("Lucky brings back scarce materials; simulator
  counts affixes at forge time"), with the legacy-tracks merge staged in the index. I did not re-pin: neither change
  touches the visit resolver, hero generation, commissions or the name pools, so the findings stand, but line numbers
  in `Battle.kt`, `Simulator.kt`, `BalanceConfig.kt`, `LaunchContent.kt` and `Forge.kt` may be off by a few lines, and
  the baseline table below should be re-read from `DECISIONS.md` once that merge is committed.
- `BalanceConfig.version` is **5** (`BalanceConfig.kt:19`), content version **2** (`LaunchContent.kt:103`),
  `RULES_VERSION` **1** (`GameEngine.kt:31`). The update proposed here should be **balance v6 / content v3**.
- One feature worktree is still unmerged and touches this area: `.claude/worktrees/agent-ac2ba6fe2027c080d` (legacy
  tracks). It adds three upgrades, **reworks Known Name** (one starting hero per level is already a regular with +30
  gold), adds `LegacyTracksConfig`, simulator "yardsticks" (first-siege town defense, first tier-4+ sale ...) and the
  flags `--upgrades`, `--yardsticks`, `--legends`, `--knownNameGold`.
- Merged in part 3 and relevant here: `Market.resolveMerchant` (`Market.kt:159-184`, called at `GameEngine.kt:281`), a
  **second purchase path**. A travelling merchant offers a fallen hero's blade to the living hero who values it most
  and can pay in full; the gold leaves the economy. It reuses `evaluate` with a fixed 0.5 noise roll and the same
  `improvement > 0` gate (`Market.kt:171`) and draws no RNG.
- **What hero daily life (merged as `77919fe`) changed for customers.** Visit *selection* is untouched: shopping is
  still a morning errand outside the weighted activity choice (`Market.resolveShelfVisits` was not edited;
  `DECISIONS.md:1300-1303`). Indirect effects, measured for BALANCED_FAIR (`DECISIONS.md:1240-1263`): expeditions fall
  from 43.4 % to 37.1 % of hero-days and patrols from 29.4 % to 25.0 % (6.7 % at the guild hall, 7.4 % on ambition
  days), so each hero suppresses and loots a little less; COLLECTOR and FORTUNE heroes earn 40 gold on guard-work days
  (3.8 % of hero-days), so `TOO_EXPENSIVE` visits fall from 16.7 to 15.3 a run while `NOT_BETTER` rises from 37.7 to
  39.6 and sales move by -0.3; deaths fall by 0.2 and retirements by 0.2 a run (slightly less turnover, so fewer new
  faces). Useful new state for recognition lines: `Hero.drivenBackOnDay`, guild membership in most runs (a guild
  stands in 91 % of FAIR runs), `mentorName` from hall lessons (2.4 a run).
- **Hard engineering constraint for every number in this report:** `BalanceConfig` is at the JVM limit of 255
  constructor parameter slots. `docs/PROGRESS.md:139` records 3 free slots; `heroLife` and `weaponFates` are already
  nested groups (`BalanceConfig.kt:265`, `272`) and the legacy-tracks branch takes one more. Flat fields can no longer
  be added. Everything proposed here goes into **one nested group** (`CustomerConfig`, reached as
  `BalanceConfig.customers`) plus at most one more (`DiscoveryConfig`). I recommend moving the seven existing flat
  customer and population fields into the new group at the same time (frees 9 slots).

File aliases (all under `C:\Users\tuncb\Desktop\Blacksmith Project\`):
`Market.kt` = `core/src/main/kotlin/com/tinyblacksmith/core/market/Market.kt`; `Heroes.kt` = `.../core/heroes/Heroes.kt`;
`Battle.kt`, `Power.kt` = `.../core/battle/`; `GameEngine.kt`, `ResolutionContext.kt`, `WorldEvents.kt`, `Commands.kt` =
`.../core/engine/`; `BalanceConfig.kt` = `.../core/config/`; `Content.kt`, `LaunchContent.kt`, `SliceContent.kt` =
`.../core/content/`; `Model.kt` = `.../core/model/`; `Journal.kt`, `Forge.kt`, `Signatures.kt` = `.../core/crafting/`;
`Legacy.kt` = `.../core/legacy/`; `Simulator.kt` = `.../core/sim/`; `Labels.kt`, `MarketPanel.kt`, `HomePanel.kt` =
`app/src/main/java/com/example/blacksmithproject/ui/`.

**Baseline at `8cc133b`** (measured, 1,000 seeds, seed 1, `DECISIONS.md:1528-1540`), median (p10/p90), mean days, sold:

| Policy | New account | All upgrades maxed |
|---|---|---|
| BALANCED_FAIR | 20 (15/25), 20.5, 19.3 sold | 35 (25/40), 34.7 |
| BALANCED_ACTIVE | 30 (20/35), 27.4, 31.4 sold | 40 (30/45), 40.6 |
| SYNERGY | 35 (25/40), 34.7, 28.2 sold | - |
| BALANCED_INVEST | 30 (20/40), 30.2, 28.5 sold | - |

0 hard-lock days. BALANCED_ACTIVE draws about 4.7 visits a day with the v5 signboard (`DECISIONS.md:1082`).

---

## 1. Verified current behaviour

### 1.1 Customer pipeline (`Market.resolveShelfVisits`, `Market.kt:17-52`)

| Step | Where | What happens today |
|---|---|---|
| Stream | `Market.kt:19` | `RngStream.PURCHASES`. Nothing else in the engine draws from it. |
| Capacity | `Market.kt:22` | `maxCustomersPerDay` (4, `BalanceConfig.kt:60`) + `toolTotal(EXTRA_CUSTOMERS)` (Signboard, +1 per level, 2 levels, `LaunchContent.kt:256`) + `festivalExtraCustomers` (2, `BalanceConfig.kt:157`) on a festival day. So 4 / 5 / 6 ordinary, 6 / 7 / 8 on a festival. |
| Scan order | `Market.kt:25`, `ResolutionContext.kt:91` | `aliveHeroes()` = living heroes **sorted by the ID string**. |
| Stop | `Market.kt:26` | The loop **breaks** when `customers >= maxCustomers`. Heroes after that point are never asked and draw no RNG. |
| Visit chance | `Market.kt:27-29` | `(baseVisitChance 0.35 + sum(trait.shopWeight) x 0.1 + loyalty x 0.01 + reputation x 0.005 + bonus).coerceIn(0.05, 0.9)`; one `rng.chance` per scanned hero. `bonus` = `festivalVisitBonus` 0.2 on a festival + Guild Patronage magnitude / 100 = 0.15 (`Market.kt:23`, `LaunchContent.kt:212`). **0.1, 0.01, 0.005, 0.05 and 0.9 are inline constants**, not in `BalanceConfig` (review E02). |
| Trait weights | `LaunchContent.kt:167-176` | `shopWeight`: Vain 0.7, Curious 0.6, Ambitious 0.5, Loyal 0.4, Greedy 0.3, the other four 0. Mean per trait 0.278; with 2-3 traits a fresh hero's chance is about **0.42** (arithmetic). |
| Empty shelf | `Market.kt:30-34` | A visitor who finds everything sold is recorded `EMPTY_SHELVES` **and still uses a capacity seat** (`customers++` at line 30 precedes the check). |
| Evaluate | `Market.kt:36`, `54-86` | Every listed weapon is scored; **one `rng.nextDouble()` per listed weapon per served visitor** (preference noise +/-0.4). |
| Candidate gate | `Market.kt:37`, `61-63` | `affordable && improvement > 0`, where `improvement = (weapon.power x conditionFactor x classFit).toInt() - (currentPower x conditionFactor x currentFit).toInt()`. Both sides are **truncated to Int before subtracting**, so a real gain of 0.9 can read 0 and be excluded. Affix multipliers, faction matchup and the fame power factor are not in the gate (fame and taste only enter `utility`). |
| Utility | `Market.kt:76-84` | `improvement x 0.12 + (fit-1) x 1.5 + elementTaste x 1.0 + loyalty x 0.01 x 0.3 + collector 0.6 + fame (<= 0.5, x2 for a COLLECTOR) + worn 0.6 + noise - pricePenalty x 3.0`; buy if the best candidate reaches `purchaseUtilityThreshold` 0.5 (`Market.kt:38`). |
| Affordability | `Market.kt:85`, `96-97` | `price <= hero.gold + tradeInCredit(current)`; credit = 40 % of the old weapon's fair price; fair price = `power x conditionFactor x 4` gold (`Market.kt:89`). |
| Price ceiling | `Market.kt:73-74`, `104-106` | Penalty only above `fairPrice x (1 + min(rep x 0.01, 0.25) + min(loyalty x 0.03, 0.25))`, scaled by trait price sensitivity (Greedy 1.6, Patient 0.7, Vain 0.6) and x1.3 for an unfulfilled FORTUNE ambition. |
| Reason codes | `Market.kt:40`, `42-48` | Bought: `WORN_OUT` / `GREAT_FIT` (fit >= 1.0) / `GOOD_ENOUGH`. Left: `TOO_EXPENSIVE` (nothing affordable), `NOT_BETTER` (nothing affordable **and** better), `OVERPRICED`, `NOT_SUITED`, `UNDECIDED`, plus `EMPTY_SHELVES`. |
| Purchase | `Market.kt:110-134` | Shop gold `+= price - credit + bonus` (`bonus` = Merchant's Favor 20 %); reputation +1; hero pays `price - credit`; loyalty `+= max(1, product of trait loyaltyGain)` (Loyal doubles it); the old weapon returns to **Storage** with a `TRADED_IN` history entry; `WEAPON_SOLD` carries `price`, `premium`, `tradeIn`, `tradedWeapon`. |
| Record | `Model.kt:206` | `MarketVisit(heroId, heroName, purchasedWeaponId, reason)` only. |

Two properties of the reason codes matter for authored encounter lines. First, a refusal names the first failing
filter over the **whole shelf**, not why the best-fitting item was declined: a hero who can afford a weak blade but not
the one that would help is reported `NOT_BETTER`, never `TOO_EXPENSIVE`. Second, `NOT_SUITED` and `OVERPRICED` can only
occur when an affordable improvement existed and lost on utility. Per-evaluation typed factors (review 4.3) are needed
before a line such as "could not afford the bow she wanted" can be truthful.

**How F05 starvation works, precisely.** Hero IDs are `"h" + serial` (`ResolutionContext.kt:63`) and the scan order is
lexicographic, so the order is `h1, h10, h11, ... h19, h100..., h2, h20, ... h3, ... h9`. Consequences:

1. The starting cast `h2 ... h9` sits **behind** every later arrival numbered 10-19. The heroes the player met first
   are pushed to the back of the queue as soon as a newcomer or a mentee arrives. `h9` is last until `h90`.
2. A hero at scan position k is served with probability `p_k x P(fewer than cap of the k-1 heroes before them visited)`.
   Served share by scan position (arithmetic, equal p):

| Town, chance, cap | Positions 1 ... n | Last / first |
|---|---|---|
| 8 heroes, p 0.50, cap 4 (fresh run) | .50 .50 .50 .50 .47 .41 .33 .25 | 0.50 |
| 8 heroes, p 0.65, cap 4 (mid run, reputation 25-40) | .65 .65 .65 .65 .53 .37 .23 .13 | 0.20 |
| 8 heroes, p 0.90, cap 4 (the review's example) | .90 .90 .90 .90 .31 .07 .014 .002 | 0.003 |
| 8 heroes, p 0.65, cap 6 (Signboard level 2) | .65 x6, .60, .50 | 0.77 |
| 12 heroes, p 0.50, cap 6 | .50 x6, .49 .47 .43 .37 .31 .25 | 0.50 |
| 12 heroes, p 0.65, cap 6 | .65 x6, .60 .50 .37 .25 .16 .10 | 0.15 |
| 12 heroes, p 0.65, cap 8 | .65 x8, .63 .57 .48 .37 | 0.57 |

3. The bias grows with exactly what a good shop earns: reputation (+1 per sale and per donation, +2 per commission
   and per fulfilled ambition, +3 per ballad) and loyalty. It is worst late in a run, when regulars should matter most.
4. The v5 Signboard (cap 5-6) **hides** the bias at 8 heroes (ratio 0.77) but does not remove it, and it returns in
   full the moment population is raised (ratio 0.15 at 12 heroes, cap 6). **Fair selection must land before any
   population or probability change**, as the brief says.
5. The same string sort is used wherever order matters without a cap (`Heroes.kt:45`, `206`, `244`; champion tie-break
   `Battle.kt:191`; `Model.kt:319`). Those are harmless (no truncation) but should move to numeric order together.

Expected visitors per day under today's rule (arithmetic, `E[min(Binomial(n, p), cap)]`):

| Heroes, p | cap 4 | cap 5 | cap 6 | cap 8 | uncapped |
|---|---|---|---|---|---|
| 8, 0.42 (day 1) | 3.08 | 3.28 | 3.35 | 3.36 | 3.36 |
| 8, 0.50 | 3.45 | 3.82 | 3.96 | 4.00 | 4.00 |
| 8, 0.60 | 3.77 | 4.36 | 4.68 | 4.80 | 4.80 |
| 12, 0.42 | 3.74 | 4.35 | 4.74 | 5.01 | 5.04 |
| 12, 0.50 | 3.90 | 4.71 | 5.32 | 5.90 | 6.00 |
| 12, 0.60 | 3.98 | 4.92 | 5.77 | 6.87 | 7.20 |
| 14, 0.50 | 3.96 | 4.87 | 5.66 | 6.66 | 7.00 |

Measured for comparison (`DECISIONS.md:1080-1082`): BALANCED_ACTIVE 3.75 visits a day without a signboard, 4.73 with
the v5 signboard; the extra 28 visits a run produced +3.5 sales, a **marginal conversion of about 13 %** against about
25 % on average. More seats for the same eight purses mostly add refusals.

### 1.2 Population

| Fact | Where | Value |
|---|---|---|
| Starting heroes | `BalanceConfig.kt:76`, `GameEngine.kt:69-72` | **8**. The GDD's PROPOSED range is **10-14** (GDD section 6); the code is below it. Hero 1 is the newest lineage's descendant when one exists (`GameEngine.kt:68-70`). |
| Class of a new hero | `Heroes.kt:18` | Uniform over 5 classes. P(all five present at the start) = **32 %** at 8 heroes, 68 % at 12 (arithmetic). |
| Arrival floor | `Heroes.kt:190-195`, `BalanceConfig.kt:77` | One newcomer a day only while living heroes < `minHeroPopulation` **5**. With 8 heroes and under one death a run this almost never fires. |
| Event arrivals | `WorldEvents.kt:165-177`, `179-189`, `327-340` | New Adventurers (+2, at most 3 a run), Veteran Returns (+1, level 5, +150 gold, at most 2), Descendant (+1, at most 2); all gated by `maxHeroPopulation` **12** (`BalanceConfig.kt:163`), which gates nothing else. |
| Retirement | `Heroes.kt:202-212`, `BalanceConfig.kt:171-173` | Level >= 8, or >= 10 victories and a 15 % daily roll. Every retiree is replaced by a mentee (`Heroes.kt:220-221`), so retirement is population-neutral. |
| Deaths | `Battle.kt:75`, `286-287` | Expedition loss with health <= 0; a champion at a lost siege loses 40 health (the decisions log notes no champion can actually die there, since champions have health >= 50). |
| Measured rates | `DECISIONS.md:1240-1246`, `1263` | BALANCED_FAIR: deaths 0.7, retirements 0.5 a run; BALANCED_ACTIVE 0.8 / 1.4; SYNERGY 0.8 / 3.6. Hero-days per run 167.5 over 20.5 days = **8.2 living heroes on average**. |
| Unique heroes met | arithmetic | About 8 + 0.5 mentees + about 1 event arrival = **9-11 identities in a first era**. |
| Starting gold | `LaunchContent.kt:160-164`, `Heroes.kt:31` | Guardian 60-140, Ranger 50-120, Duelist 55-130, Battlemage 70-150, Warden 50-120: mean **94.5**. Every hero starts **unarmed** (`unarmedPower` 4). |
| Income | `Battle.kt:36`, `Heroes.kt:115`, `171` | Expedition win 20-60 gold (x2.5 against an elite); patrol 12; guard work 40 (COLLECTOR / FORTUNE ambition days). |

Buying power (arithmetic from the measured v5 BALANCED_FAIR activity shares, `DECISIONS.md:1263`): 65 expeditions a run,
about 32 won (backed out of 15.1 level-ups a run; win rate about 49 %, **not reported by the simulator**), 42 patrols,
6 paid-work days: about **13 gold per hero-day**, 107 gold a day for the town, about 2,200 a run on top of 756 starting
gold. A plain iron + ember weapon lists at about 84 gold, a bronze + frost one at 100-116, so about 64 % of fresh
heroes can pay for a first weapon and each hero earns one replacement roughly every seven days. **Sales are bound by
purses and by need, not by seats** (`DECISIONS.md:301-320` reached the same conclusion from a visit diagnostic).

### 1.3 How population feeds difficulty

| Channel | Where | Scales with hero count? |
|---|---|---|
| Faction suppression | `Battle.kt:38` (3 per won expedition, +4 elite), `Heroes.kt:114` (1 per patrol), applied to the **single most pressing faction** (`Heroes.kt:42`), netted in `GameEngine.kt:314-320` against growth 4 + 3 + 2 = 9 a day (`LaunchContent.kt:183-197`) | **Yes, linearly.** Today about 7.2 a day (arithmetic: 1.55 wins x 3 + 0.13 elites x 4 + 2.04 patrols). At 12 heroes about 10.6, more than total growth. Bounded below by pressure 0. |
| Raid power | `Battle.kt:220-221`: `(32 + 6 x day + 0.65 x pressure) x 2.0 x world` | **No** direct term. Indirectly through pressure: 25 points less pressure is 32 raid power, about 2.7 days of the 12-a-day growth. |
| Champions | `Battle.kt:187-192` | Top 3 by defense power among heroes with health >= 50. **Weakly**: an order statistic (top 3 of 12 is about 0.27 sd better than top 3 of 8) plus better availability. |
| Militia | `Heroes.kt:112` (+3 per patrol, +5 per DEFENDER drill), cap 30 (`BalanceConfig.kt:106`), -1 a day (`GameEngine.kt:332`), start 5 (`GameEngine.kt:53`) | **Saturated** (arithmetic): about +6 a day pins it at 30 by the first siege. |
| Armory | donations, cap 30 | No. |
| Forge recovery | `GameEngine.kt:325-326`, cap 2 a day | **Saturated** (about 3.6 qualifying actions a day, arithmetic). |
| Forge damage | `Battle.kt:237`: `24 + 50 x (raid / defense - 1)`, cap 60 | No direct term. |
| Hero income and loot | `Battle.kt:36`, `62` | **Yes, linearly**: 50 % more heroes is 50 % more gold to spend at the shop and 50 % more looted materials. |
| Elites, warlords | `Battle.kt:24`, `BalanceConfig.kt:206` | Fewer if pressure falls (warlord at pressure >= 50). |

So more heroes makes survival easier mainly through **suppression** (linear, bounded) and **better-armed champions**
(through sales). Militia, armory, recovery, champion count and the siege calendar do not move.

Also verified: review F08 stands (`Battle.kt:191` ranks champions with the default `elite = false`, `Battle.kt:218`
values them with `elite = warlord`).

### 1.4 Names

| Fact | Where | Value |
|---|---|---|
| Pools | `LaunchContent.kt:244-251`; `SliceContent.kt:126-127` | Launch **30 first names x 24 surnames = 720**; slice 14 x 10 = 140. |
| Choice | `Heroes.kt:19-20` | Two independent uniform `rng.pick` draws on the stream the caller passes (HEROES at run start, arrivals and retirement; EVENTS for event arrivals). |
| Descendants | `Heroes.kt:20`, `GameEngine.kt:68-76`, `WorldEvents.kt:329-337` | Surname forced from `LineageAnchor.surname`; first name random, so a descendant can receive the ancestor's exact name (1 in 30). |
| Collision avoidance | none | No check against living heroes, the run, lineages or guilds. |
| Display | `Model.kt:123` | `fullName = "$name $surname"`. |
| Identity by name | `GameEngine.kt:75`, `WorldEvents.kt:329`, `333`; `Model.kt:244` | `Hero.descendantOf` holds the ancestor's **name string** and lineage matching compares names; `LineageAnchor` has no ID. Guild names are `"the <surname> Company"` (`Heroes.kt:234`). |

Collision odds (arithmetic, exact products):

| Living heroes | Share a first name | Share a surname | Two with the same full name |
|---|---|---|---|
| 8 (today) | **64 %** | **73 %** | 3.8 % |
| 12 | 92 % | 97 % | 8.8 % |
| 14 | 97 % | 99 % | 11.9 % |
| 16 | 99 % | 99.9 % | 15.5 % |

Across eras: among 100 heroes ever met (about ten eras) the expected number of identical-full-name pairs is 6.9; among
200 it is 27.6. Existing pool problems: `Tessa` / `Nessa` differ by one letter; `Rook` + `Rooksbane` can produce
"Rook Rooksbane"; `Ashwood`, `Holloway` and `Mossgrave` share stems with the Ashclaw, the Hollowbound and Grave Dust.

A larger pool alone does not fix the town: with 120 x 96 names and no avoidance, 12 heroes still share a first name in
**43 %** of towns and a surname in 51 %. **In-town avoidance is required whatever the pool size; pool size governs
repetition across a long run and across eras.**

### 1.5 Commissions

| Fact | Where | Value |
|---|---|---|
| Offer | `Market.kt:218-240` | EVENTS stream; only when **no** commission is offered or accepted (line 220); 30 % a day (line 221, `BalanceConfig.kt:71`). |
| Patron | `Market.kt:225` | Weighted `1 + min(loyalty, 10) x 0.5` over living heroes. |
| Family | `Market.kt:227` | Uniform over the patron class's preferred families. |
| Quality | `Market.kt:228` | `rng.nextInt(35, 60)` inclusive: **any integer 35-60**. |
| Element | `Market.kt:230-232`, `BalanceConfig.kt:225-226` | 50 %: the patron's taste, else the weakness of the highest-pressure faction; reward x1.5. |
| Reward, deadline | `Market.kt:233-236` | `40 + 2 x minQuality` (110-160, or 165-240 with an element); 4 days. |
| Noble | `WorldEvents.kt:146-163` | Uniform patron, quality 60-75, reward x3 (480-570), 6 days. |
| Fulfilment | `Market.kt:195-197` | In storage **or listed**, same family, `quality >= minQuality`, element match; **the highest-quality match is taken** (`maxByOrNull { it.quality }`). |
| Order | `GameEngine.kt:279-280` | Shelf visits first, commissions second. |
| Expiry | `Market.kt:206-210` | Reputation -1; a dead patron voids it without penalty (`Market.kt:190-194`). |

**F04 confirmed and wider than reported.** `Labels.quality` (`Labels.kt:12-18`) bands at 35 / 50 / 70 / 85, the same
thresholds as rarity (`uncommonMin` 35, `rareMin` 50, `epicMin` 70, `legendaryMin` 85). The request is rendered as the
band of `minQuality` in `MarketPanel.kt:82` **and** `HomePanel.kt:130`, and the Gazette line always says "asks for a
fine ..." whatever the number (`Market.kt:239`). Eleven of the 26 possible standard values (50-60) read "fine" and ten
of them (51-60) demand more than the band's floor; noble values 60-69 read "fine" and 70-75 "superb". The COLLECTOR
ambition has the same shape: `ambitionCollectorQuality` 60 (`BalanceConfig.kt:213`) sits inside the "fine" band.

**F06 confirmed, with a second trap.** A listed match can sell to a visitor before the commission is checked. And when
it is checked, the patron takes the **best** matching weapon in the shop, including a masterwork on a priced shelf,
for the fixed reward. The UI promises "A matching weapon in storage or on the shelf is delivered at End Day"
(`MarketPanel.kt:89`) and offers no preview of which one.

### 1.6 Demand, Signboard, Guild Patronage, upgrades, catalysts, the collector

- **Sidegrade gate (6.1)**: as in 1.1. The merchant resale path uses the same gate (`Market.kt:171`).
- **Signboard (6.2)**: fixed in v5 part 1. Measured (`DECISIONS.md:1073-1090`): +0.4 mean days and +3.5 sales a run for
  BALANCED_ACTIVE against no signboard; bought in every run on day 2.2. The review's text is stale here.
- **Guild Patronage (6.2)**: still `BlessingEffect.HERO_VISIT_CHANCE` +15 points for 5 days (`Content.kt:113-118`,
  `LaunchContent.kt:212`, `Market.kt:23`). Arithmetic: with 8 heroes at p 0.50 it moves expected visitors from 3.45
  to 3.86 at cap 4 (+2 visits over the blessing, about half a sale) and from 3.96 to 5.2 at cap 6. It is still
  swallowed by the cap for a smith without a signboard; `PROGRESS.md` lists it as open.
- **Upgrade impact (6.3)**: the review's table is v4. Latest measured, BALANCED_ACTIVE, v5 part 1
  (`DECISIONS.md:1144-1148`), median / mean days for a maxed track: Stalwart Walls +10 / +6.4, Well-Stocked Cellar
  +5 / +3.5, Forge Mastery +5 / +2.8, Thrifty Hands +5 / +1.6, Tireless Smith +5 / +0.6, Lucky Hammer +5 / +0.6,
  Family Savings +5 / +0.3, Known Name +0 / 0.0, all maxed +15 / +12.3. The log notes that with a fifth and sixth
  customer "sales are less demand-bound, so starting energy and gold register for the first time". That is direct
  evidence that customer throughput is what the weak tracks were waiting for. Known Name is being reworked in the
  legacy-tracks worktree.
- **Catalysts (6.5)**: `Forge.kt:68-71` and `87`: any catalyst gives +5 points exceptional, -5 points defect, +4
  quality; its identity matters only as a signature key (`Signatures.kt:31-36`). 11 signatures need Binding Salt, 3
  Runestone Shard, 2 Dragon Oil, 2 Void Ink, and **6 need none** and are reachable by Quick Forge. The persistent hint
  (`Journal.kt:111-121`) says "a steadying hand" for every catalyst, always reveals the risk wish and always says
  "fine work". `recordSignatureClue` returns at once unless the entry is `UNKNOWN` (`Journal.kt:56`), so the second
  and later near-misses say nothing; `SignatureDef.missing` reports catalyst before risk before quality, so a player
  with the wrong catalyst never learns the rest. A discovered entry shows name and flavour only (`Journal.kt:114`).
- **Collector (world event 24)**: `WorldEvents.kt:353-367`. Eligible when a listed weapon has fame > 0 or is Epic+;
  takes the most famous; pays `listedPrice x 1.5`; the weapon leaves for good; at most 2 a run. It is not a
  `MarketVisit` and not counted as a sale (review F07). **New finding:** the payment has no ceiling. `SetPrice` and
  `ToggleShelf` only reject negative prices (`GameEngine.kt:118`, `131`) and the price field accepts six digits
  (`MarketPanel.kt:189`, `195`), so an Epic listed at 999,999 gold, which no hero can buy, is paid for at 1,499,998
  gold when the collector comes. Cap the payment at `askingPrice x collectorPriceMultiplier`.

### 1.7 Simulator (`Simulator.kt`)

**Policies** (`Simulator.kt:23-70`): RANDOM, SAFE_FAIR, RECKLESS_FAIR, BALANCED_CHEAP (x0.7), BALANCED_EXPENSIVE (x1.8),
SYNERGY, OVERWORK, BALANCED_FAIR, BALANCED_INVEST, BALANCED_REPUTED, BALANCED_ACTIVE, SAFE_CHEAP, RECKLESS_EXPENSIVE,
PASSIVE. `GDD_SET` is the first eight (line 68).

**What `playRun` does each day** (`Simulator.kt:143-250`): takes the **first** offered blessing (line 165); **accepts
every** commission (166) and never forges for one; ACTIVE buys the cheapest affordable tool and hones one weapon;
forges until `chooseForge` returns null or a forge is rejected; lists stored weapons until the shelf is full at the
policy's price factor (189 onward); ACTIVE donates then salvages the rest; End Day.

**What `chooseForge` does** (`Simulator.kt:294-314`): always `ForgeMode.QUICK`, **never a catalyst or a technique**
(line 313); family random (or from SYNERGY); core and augment = highest tier on hand, else the cheapest (SYNERGY:
weakness element, then best affinity; INVEST: highest affordable tier).

**Metrics reported today** (`RunStats` 73-116, `PolicySummary` 378-431, `render` 474-496): survival days p10 / median /
mean / p90 / max and runs ended; forged, sold (shelf sales **plus** commission completions, lines 212-214) and sell
rate; gold earned (median), final gold, gold on hand, materials on hand; rarity shares; hero deaths, retirements;
sieges survived and lost, faction win proportion; hard-lock days and runs; **shop visits per run by reason code**
(`visitReasons`, lines 96, 210; `visitsPerRun`, 408); elites slain, weapons broken, warlord sieges fought and won; tool
purchase share, level and first day; weapons per affix; hero-days per run and activity shares, level-ups, mentorings,
guilds per run and share of runs with a guild; fates of fallen heroes' blades and the artifact recovery rate (430);
legacy points (median); discoveries; signature discoveries; upgrade impact (median and mean delta); optional End Day
timing.

**`hardLocks`** (`Simulator.kt:187`): incremented when the policy made no forge today, the day is past 1 and **no weapon
is listed** at that moment. The check runs **before** the day's listing step (line 189 onward), so "listed" means left
over from yesterday: a day with twenty weapons in storage about to be listed counts if the shelf was empty, and a day
with one unsellable weapon on the shelf and no gold does not. PASSIVE is excluded.

**`signatureDiscoveries`** (`Simulator.kt:238`): weapons in the final state with `signatureId != null`, so repeats count
(signature weapons are never pruned). Unique first discoveries are folded into `discoveries` (`state.discoveriesThisRun`)
together with understood journal entries and are not reported on their own.

**CLI flags that exist at HEAD** (`Simulator.kt:623-632`, parsed from 636): `--runs`, `--seed`, `--policy NAME|all|gdd`,
`--days`, `--json`, `--perf`, `--content launch|slice`, `--impactPolicy`, `--reserve`, `--siegeModifier`,
`--recoveryCap`, `--forgeDamageBase`, `--forgeDamageSlope`, `--maxForgeDamage`, `--rarityTable [N]`, `--noTool`,
`--toolCost`, `--noAffixEffect`, `--noImpact`, `--noFates`. **There is no override for `maxCustomersPerDay`,
`startingHeroCount`, `baseVisitChance`, `raidPerDay` or `expeditionSuppression`**, so none of the sweeps in section B
can run today.

**Customer metrics that are missing** (refusals by code are *not* missing):

| Missing metric | Why it is needed |
|---|---|
| Visitors served per day as a distribution (mean, p10, p50, p90, share of days with <= 2 and with capacity reached) | Only the per-run total exists; "more shoppers" cannot be accepted on a mean. |
| Willing visitors, served visitors and the overflow between them, per day | Shows whether capacity binds; gives the Signboard a measured purpose. |
| Unique heroes served per run, by day 5 and by day 10 | Lever 1 against lever 2. |
| Per-hero served share (max / min among heroes alive >= 10 days), and by arrival order | The F05 regression metric. |
| Class coverage: classes present, classes that visited, classes that bought, by day 5 / 10 | Whether the six families have buyers. |
| Newcomer wait: days from arrival to first served visit and to first purchase | Newcomer opportunity. |
| Return rate: share of visits by a hero served before; days between a hero's visits; purchases per hero | Regulars. |
| Conversion (purchases / served visits), per day band (1-5, 6-15, 16+) | Whether extra visits are extra refusals. |
| Sales per day, shelf sell-outs (days the last visitor found the shelf empty) | Supply against 10 energy. |
| Name collisions: living heroes sharing a first name, a surname, a full name; repeats across eras | Lever 3. |
| Expeditions won / lost, militia and pressure at each siege, counter-element champions at a siege | Needed to attribute a run-length change to suppression or to arms. |

---

## 2. Design

Every default below is PROPOSED and reversible: setting the new fields to the "today" column reproduces current
behaviour except for the selection order, which is the bug being fixed. All of them live in `CustomerConfig`
(`BalanceConfig.customers`), bumped as balance **v6**.

### A. Customer throughput plan

#### A.1 The three levers, kept separate

| Lever | Today | Recommended start | Bounds over a run | What it is for |
|---|---|---|---|---|
| **1. Active population** | 8 at the start, refill only below 5, events up to 12; 8.2 alive on average | **12** at the start with one hero of each class guaranteed; refill toward 12; never below 9 for long; events up to **16** | 9 to 16, typically 11-13 | More purses, more needs, more faces. The only lever that raises sales without raising refusals. |
| **2. Visitors served per day** | capacity 4 (+1 / +2 Signboard, +2 festival); 3.1-3.9 served without a signboard | capacity **6** (+1 / +2 Signboard = 7 / 8; festival +3) | ordinary 4-8 served, festival up to 11 | A visibly busier counter; the cap exists so the Signboard and a crowded day mean something. |
| **3. Lifetime identity variety** | 720 full names, no avoidance; 9-11 identities an era | 120 x 96 = **11,520** full names, no shared first name or surname among the living; 14-18 identities an era | - | Recognition. Never a substitute for levers 1 and 2. |

Expected served visitors per day at the recommended values (arithmetic; `p` is the mean willingness to visit):

| Phase | p | Capacity 6 (no signboard) | Capacity 7 | Capacity 8 (Signboard 2) | Today: 8 heroes, capacity 4 |
|---|---|---|---|---|---|
| Days 1-5 (reputation 0-10, everyone unarmed) | 0.42 without need terms, 0.60 with | 4.7 to 5.8 | 4.9 to 6.4 | 5.0 to 6.9 | 3.1 |
| Days 6-15 | 0.50 | 5.3 | 5.7 | 5.9 | 3.5 |
| Day 16+ (reputation 40+, regulars) | 0.70 | 6.0 | 6.8 | 7.6 | 3.9 |
| Festival day (+0.2, +3 seats) | 0.70-0.90 | 8.1-9.0 | 8.3-9.9 | 8.4-10.5 | 5.3-6.0 (capacity 6) |

That is **+50 to +70 % served visitors without the Signboard and roughly double with it**, on 50 % more heroes, so
visits per hero stay about where they are (0.45 a hero-day). A day is 5-6 encounters, 7-8 for a built-up shop. The End
Day shop scene should feature every encounter up to 6 and summarise the rest in one line; that is a presentation
budget, not an engine rule.

I do not recommend capacity 8 as the base. At 12 heroes the willing count is about 6, so a base of 8 almost never
binds: the Signboard would be dead again from the other side and a "crowded" day could not exist.

#### A.2 Population rules

| Rule | Today | Proposed | Notes |
|---|---|---|---|
| `startingHeroes` | 8 | **12** | Inside the GDD's PROPOSED 10-14. |
| `startingClassCoverage` | off | **on** | The first `classes.size` starting heroes take one class each, in a seeded order (HEROES stream; one shuffle instead of five `pick` draws). All five classes present in 100 % of runs (today 32 %). A descendant keeps the lineage's class and counts toward coverage. |
| `populationTarget` / `arrivalChancePerMissing` | none | **12** / **0.15** | Each End Day, if living heroes < target, one newcomer arrives with chance `min(0.6, 0.15 x missing)`. One HEROES draw a day, always. Deaths are replaced over a few days instead of never. |
| `minHeroPopulation` (guaranteed arrival) | 5 | **9** | The existing rule (`Heroes.kt:190-195`), moved up. |
| `maxHeroPopulation` (event arrivals) | 12 | **16** | New Adventurers, Veteran, Descendant. |
| Newcomer gear | unarmed, class gold | **unchanged** | Hold fixed. A newcomer is a customer because they arrive with nothing. |

#### A.3 Willingness to visit

Keep the shape; move the inline constants into config; cap the two terms that grow without bound; add need.

```
willing(h) = clamp( baseVisitChance                        // 0.35 (unchanged)
                  + traitShopWeight(h) * visitTraitScale    // 0.10 (inline today)
                  + min(h.loyalty, 10) * visitPerLoyalty    // 0.01 (inline today, uncapped)
                  + min(reputation, 50) * visitPerReputation// 0.005 (inline today, uncapped below the clamp)
                  + need(h)                                 // new, see below
                  + festivalVisitBonus (festival day)       // 0.20 (unchanged)
                  , visitFloor 0.05, visitCeiling 0.90 )
need(h) = + needUnarmed 0.20        if h has no weapon
          + needWorn 0.15           if equipped condition < wornConditionThreshold
          - needJustBought 0.20     if h bought within the last 2 days
          + needWantMet 0.30        if a listed weapon answers h's standing want (loop E1)
          + needNewcomer 0.30       on the day after h arrived
```

The need terms are a **separate, later sweep step** (B.3, step 5): they exist to send visitors who have a reason to
come, which is the lever on the `NOT_BETTER` share (about half of all visits today). They ship only if conversion
rises and no refusal code disappears from the player's feedback.

#### A.4 Fair seeded selection (fixes F05; lands first, alone)

```
fun resolveShelfVisits(ctx):
    rng      = ctx.rng(RngStream.PURCHASES)
    heroes   = ctx.aliveHeroes()                      // ordered by NUMERIC serial; the order only assigns draws
    capacity = cfg.shopCapacity + toolTotal(EXTRA_CUSTOMERS) + (festival ? cfg.festivalExtraSeats : 0)

    // 1. Intent: exactly one draw per living hero, every day, whoever ends up seated.
    willing = heroes.filter { rng.nextDouble() < willing(it) }

    // 2. Seats. Nobody waits a third day running.
    seated  = willing.filter { it.turnedAwayStreak >= cfg.maxTurnedAwayDays }
                     .sortedByDescending { it.turnedAwayStreak }.take(capacity)                    // no draw
    pool    = willing - seated
    while (seated.size < capacity && pool.isNotEmpty()):
        // class spread: while fewer than cfg.classSeats classes are seated, draw among classes not yet seated
        candidates = if (classesOf(seated).size < cfg.classSeats)
                         pool.filter { it.classId !in classesOf(seated) }.ifEmpty { pool } else pool
        pick   = rng.pickWeighted(candidates.map { it to seatWeight(it) })                         // one draw per seat
        seated += pick; pool -= pick
    turnedAway = pool                                   // recorded; streak + 1; everyone else's streak resets

    // 3. Serve in the order seated (that order is who gets first pick of the stock). Evaluation is unchanged:
    //    one nextDouble per listed weapon per served visitor.
    for (h in seated): serve(h)

seatWeight(h) = 1.0
              + cfg.seatLoyaltyWeight  * min(h.loyalty, 10) / 10      // 0.5: a regular at loyalty 10 is 1.5x a stranger
              + cfg.seatNewcomerWeight * (h.shopVisits == 0 ? 1 : 0)  // 1.0: never served yet
              + cfg.seatWaitWeight     * h.turnedAwayStreak           // 0.75 per day turned away
              + cfg.seatNeedWeight     * (unarmed or worn ? 1 : 0)    // 0.5
          then * cfg.seatBrowsedYesterday (0.5) if h was served yesterday and bought nothing
```

- **Stream and draw count.** PURCHASES only. Per day: `N` intent draws (N = living heroes) + one draw per seat filled
  by a pick (at most `capacity`; none for a guaranteed seat) + the existing one draw per listed weapon per served
  visitor. The intent draws no longer depend on capacity, so two runs of the same seed that differ only in capacity
  or Signboard level see the **same willing heroes** on a day until their shelves diverge. That makes the sweeps in B
  much less noisy.
- **Why `pickWeighted` and not a key trick.** Weighted reservoir keys (`u^(1/w)` or `-ln(u)/w`) need `pow` or `ln`,
  which may differ in the last bit between the desktop JVM and ART. `Rng.pickWeighted` uses only `+ - *` and
  comparisons, so the simulator and the device agree bit for bit. This is the F11 lesson applied early.
- **Loyalty is an explicit, bounded weight** (at most 1.5x), not an accident of ID order. Regulars are seated a little
  more often and a little earlier. Earlier seating is the real privilege, because stock runs out.
- **Newcomer opportunity**: a never-served hero weighs 2.0x and (with the need terms) is 0.30 more willing the day
  after arriving. Target: median wait from arrival to first served visit <= 2 days, p90 <= 4.
- **Class diversity**: the first `classSeats` (3) seats go to distinct classes when the willing pool allows.
- **Repeat visits**: one ordinary purchase per visitor per day stays (GDD 5). A hero may come on consecutive days;
  `seatBrowsedYesterday` damps the dull case (same hero, same shelf, same refusal) without forbidding it.
- **High-demand saturation**: `turnedAway` is recorded per day and shown ("Three more found the shop too full"). It is
  the player-visible reason to buy the Signboard and the simulator's measure of whether capacity binds.
- **Guarantee** (a testable invariant): no hero is turned away on `maxTurnedAwayDays + 1` = 3 consecutive days on
  which they were willing, **provided the heroes owed a seat that day do not outnumber capacity**. If they do (a
  festival ending in a town of 16), the longest-waiting are seated first and the rest keep their claim.
- **Empty shelf**: a seated hero who finds the shelves bare still counts as a visit, but keeps newcomer priority and
  gains a turned-away day, so bad luck with stock does not cost them their place tomorrow.
- **State added** (all on `Hero`, all defaulted, save schema unchanged): `shopVisits`, `shopPurchases`,
  `lastServedDay`, `lastPurchaseDay`, `turnedAwayStreak`, `arrivedOnDay`. `DayResolution` gains `turnedAway: Int`.
- **Consequences to plan for**: every seed plays differently from v5 (full re-baseline; consider `RULES_VERSION` 2,
  review F09). `WorldEventsAndGenerationsTest.kt:199-200` asserts that the maximum visit count equals
  `maxCustomersPerDay`, and `GameplayDepthTest.theSignboardLetsOneMoreCustomerInPerLevel` compares visit counts on
  the same seed; both need rewriting against the new rule. `Market.resolveMerchant` should take its buyer in numeric
  order too.

Regression tests for the fix: (1) eight identical heroes, 2,000 days, capacity 4, p 0.9: every hero's served share
within 3 % of 1/8 (today the last hero gets 0.2 %); (2) permute the hero serials: the distribution of served shares
does not change; (3) loyalty 10 against loyalty 0, all else equal: served ratio between 1.2 and 1.6; (4) a newcomer
among eleven veterans is served within two days in at least 80 % of seeds; (5) the streak invariant above; (6) same
seed, capacity 6 against 8: identical willing sets on day 1.

#### A.5 Signboard and Guild Patronage: two different, measurable effects

| | Signboard (tool, 150 / 400 gold, lasts the run) | Guild Patronage (blessing, 5 days, free) |
|---|---|---|
| Lever | **Seats**: +1 capacity per level (as merged in v5). | **Who comes and what they can pay**: for 5 days every guild member is willing at the ceiling (0.90), and the guild pays `patronageStipend` **30** gold toward one purchase per member (added to affordability, paid to the shop, recorded on the sale). |
| Player-visible | "Seats 6 -> 7"; fewer "turned away" lines. | "Sent by the Ashwood Company" on the encounter; "the guild paid 30" on the receipt. |
| Measured by | served a day, turned away a day, sales from the 7th and 8th seat. | guild-member share of visits, conversion and `TOO_EXPENSIVE` share on blessing days against the 5 days before; stipend gold per blessing. |
| Why it is not the other one | Does nothing when the shop is not full. | Works when the shop is not full; does nothing for a town without a guild. |
| Guard | - | Not offered while `town.guilds` is empty (a guild stands in 91-97 % of runs since v5 part 2, `DECISIONS.md:1263`, `1330`); one stipend per member per blessing. Six members is at most 180 gold, about 7 % of a run's shop income. |

Alternative for Guild Patronage if minting stipend gold is unwanted: "guests of the guild", up to two willing guild
members a day are seated **outside** capacity. Sweep both; ship one.

Festival: +3 seats (was +2) and +0.20 willingness (unchanged), so a festival day is clearly the busiest of the week.
Known Name: leave to the legacy-tracks branch (starting regulars), then re-measure under the new rules.

#### A.6 `CustomerConfig` (one constructor slot)

| Field | Default | Today |
|---|---|---|
| `startingHeroes`, `startingClassCoverage` | 12, true | 8, false |
| `populationTarget`, `arrivalChancePerMissing`, `arrivalChanceMax` | 12, 0.15, 0.6 | none |
| `minHeroPopulation`, `maxHeroPopulation` | 9, 16 | 5, 12 |
| `shopCapacity`, `festivalExtraSeats`, `festivalVisitBonus` | 6, 3, 0.20 | 4, 2, 0.20 |
| `baseVisitChance`, `visitTraitScale`, `visitPerLoyalty`, `visitLoyaltyCap`, `visitPerReputation`, `visitReputationCap`, `visitFloor`, `visitCeiling` | 0.35, 0.10, 0.01, 10, 0.005, 50, 0.05, 0.90 | same values, inline, uncapped |
| `needUnarmed`, `needWorn`, `needJustBought`, `needJustBoughtDays`, `needWantMet`, `needNewcomer` | 0.20, 0.15, 0.20, 2, 0.30, 0.30 | 0 |
| `seatLoyaltyWeight`, `seatNewcomerWeight`, `seatWaitWeight`, `seatNeedWeight`, `seatBrowsedYesterday`, `classSeats`, `maxTurnedAwayDays` | 0.5, 1.0, 0.75, 0.5, 0.5, 3, 2 | none |
| `patronageStipend`, `patronageWillingness` | 30, 0.90 | +0.15 flat |

`expeditionSuppression` 3 -> **2** (B.2) stays a flat field. Move `maxCustomersPerDay`, `baseVisitChance`,
`startingHeroCount`, `minHeroPopulation`, `maxHeroPopulation`, `festivalExtraCustomers` and `festivalVisitBonus` out
of the flat list into this group in the same change.

---

### B. Economy model

#### B.1 What the recommended values do (arithmetic from current numbers; to be confirmed by the sweep)

| Quantity | Today (8 heroes, capacity 4) | Recommended (12 heroes, capacity 6), uncompensated | Reasoning |
|---|---|---|---|
| Starting hero gold | 756 | 1,134 | 94.5 a head. |
| Hero income | 107 gold a day | about 157 | 13 a hero-day; linear in heroes. |
| Affordability | about 64 % can pay for a first iron weapon; `TOO_EXPENSIVE` about 20 % of visits (FAIR) | same per hero | Per-hero purse is unchanged, so the **share** of visits that are too poor should hold; the **count** rises with visits. |
| Sales a day | 0.94 (FAIR), 1.15 (ACTIVE) | 1.3-1.5 (FAIR), 1.6-1.9 (ACTIVE) | Bounded by income: 157 gold a day over about 75 net gold a sale is 2.1 a day at most. |
| Supply | 5 Quick forges a day on 10 energy (7 with full overwork, 6 with both Bellows); or 2 Advanced + 1 Quick | unchanged (10 energy is LOCKED) | Bots forge 5 a day and sell 1; a careful human forges 2-3. At 1.5-2 sales a day the careful smith comes close to selling out on a good day. That is the intended feel: stock decisions matter without being short every day. |
| Stock depletion | sell rate about 17-19 % (FAIR) | 25-30 % | Still a surplus for a Quick-forging smith. Watch `EMPTY_SHELVES` and sell-out days under an Advanced-forging policy. |
| Class coverage | all five classes in 32 % of runs | 100 % with coverage | Every family has a buyer from day 1 (Bow needs a Ranger; Staff a Battlemage or a Warden). |
| Unique customers an era | 9-11 | 14-18 | 12 + arrivals replacing deaths + events. |
| Champion strength | top 3 of 8 | top 3 of 12, each a little better armed | Order statistic +0.27 sd, better availability, more purchases: estimate **+3 to +6 % town defense**, about +1 day. |
| Expedition income to the shop | about 11 looted materials a run | about 17 | 35 % of won expeditions bring one. |
| Threat suppression | 7.2 a day against growth 9 | 10.6 a day | Total pressure trend goes from +1.8 to -1.6 a day. By days 15-20 the besieging faction is about 17-23 points lower: raid -22 to -30 power, about **+2 to +2.5 days**. |
| Siege difficulty | raid `(32 + 6 x day + 0.65 x pressure) x 2` | same formula, lower pressure, fewer warlords | Militia, armory and recovery are capped and do not move. |
| Run length, BALANCED_FAIR | 20 (15/25), mean 20.5 | mean about **23.5-24.5**, median likely 25 | Suppression +2 to +2.5, champions +1, arms +0.5. Too long for the first-era band. |
| Legacy gain | - | +0 to +1 point | One point per 5 days; milestones are one-time. |

#### B.2 Compensating levers

| Lever | Use it? | Why |
|---|---|---|
| `expeditionSuppression` 3 -> **2** | **Yes, first** | It is the one channel that scales linearly with heroes. At 12 heroes: 12 x (0.19 wins x 2 + 0.25 patrols) + elites = about 8.3 a day, against 7.2 today and growth 9. The pressure trend becomes about +0.7 a day (today +1.8). Expected BALANCED_FAIR mean after it: about 22-23. |
| `raidPerDay` 6.0 -> 6.5 | Second, probably needed | Already calibrated: v3 measured -1.2 mean days for FAIR and -1.7 for ACTIVE for +0.5 (`DECISIONS.md:392-393`). With both: about 21-22. |
| `populationTarget` 12 -> 10 | Third | Smaller change, smaller payoff; use if the scene budget or readability asks for it. |
| Hero starting gold, expedition gold, patrol pay | **Hold fixed** | Lowering purses to offset more heroes would rebuild the `TOO_EXPENSIVE` wall v3 removed. |
| `tradeInShare` 0.4, `fairGoldPerPower` 4 | **Hold fixed** | The price language the player is learning. |
| Newcomer gear | **Hold fixed** (unarmed) | Arming newcomers removes the first sale, which is the best one. |
| Siege strength scaled by head count | **Reject** | A hidden rubber band; breaks "living faction pressure decides strength". |
| 10 energy, 5-day sieges, 3 champions | LOCKED | - |

#### B.3 Sweep plan

Prerequisite (no behaviour change): the metrics of G.2 and a generic override flag, for example
`--set startingHeroes=12,shopCapacity=6,expeditionSuppression=2,raidPerDay=6.5` (the pattern at `Simulator.kt:668-672`).
The instrumented build must reproduce the `8cc133b` table exactly at seed 1.

| Step | Vary | Hold | Policies | Seeds | Passes when |
|---|---|---|---|---|---|
| 1. Fair selection alone | new selection, 8 heroes, capacity 4, today's willingness | all else | all 14 + both maxed rows | 1,000 x base seeds 1, 10001, 20001 | No mean moves more than 0.6 days; served-share max / min <= 2.5; visits a day within 0.1. |
| 2. Population | starting heroes and target 8 / 10 / 12 / 14 | capacity 4, suppression 3 | FAIR, ACTIVE, SYNERGY, INVEST, CHEAP, EXPENSIVE, PASSIVE | 1,000 x 3 | Read the slope: days, sales, unique visitors, pressure at sieges per +2 heroes. |
| 3. Capacity | 4 / 5 / 6 / 8 at 8 and at 12 heroes | suppression 3 | same | 1,000 x 3 | Served and conversion per extra seat; turned-away share. |
| 4. Compensation | suppression 3 / 2 x raid per day 6.0 / 6.5 at (12, 6) | - | same + both maxed rows | 1,000 x 3 | The band in B.4. |
| 5. Need terms | off / on, each term alone | chosen (heroes, capacity, compensation) | FAIR, ACTIVE, the new ADVANCED and REQUEST policies | 1,000 x 3 | Conversion up, `NOT_BETTER` share down, no code share below 3 %. |
| 6. Patronage, festival | stipend 0 / 20 / 30 / 40; the guests variant; festival seats 2 / 3 | - | ACTIVE, FAIR with a fixed blessing choice | 1,000 x 3 | Patronage worth choosing in at least one policy; under 10 % of shop income. |
| 7. Review | chosen values | - | all policies, both maxed rows, per-upgrade impact, multi-era | **10,000**, seed 1 | B.4 holds at 10,000 seeds. |

One step changes one family of numbers. Steps 2 and 3 are deliberately uncompensated, so the cost of each lever is
known before it is paid for.

#### B.4 Acceptance band

| Measure | Band | Baseline |
|---|---|---|
| BALANCED_FAIR days | median 20, p10 >= 15, p90 <= 30, **mean 19.5-22.5** | 20 (15/25), 20.5 |
| BALANCED_ACTIVE days | median 25-30, mean <= 30.5 | 30 (20/35), 27.4 |
| SYNERGY days | mean <= 38, every run ends, longest <= 80 | 35 (25/40), 34.7 |
| BALANCED_EXPENSIVE, PASSIVE | median 10-15 and 10: pricing and forging still matter | 10, 10 |
| Maxed upgrades | BALANCED_ACTIVE mean <= 44 and at least 10 days above the new account | 40.6 against 27.4 |
| Served visitors a day (no signboard) | mean **5.0-6.0**; days with <= 2 visitors <= 5 % | about 3.5-3.7 |
| Served visitors a day (ACTIVE, signboard) | mean 6.0-7.5 | 4.7 |
| Unique heroes served | >= 9 by day 5, >= 13 a run | not measured (estimate 7 and 9-10) |
| Fairness | served share max / min <= 2.5 among heroes alive >= 10 days; no hero turned away 3 willing days running | 5x or worse at mid-run reputation (arithmetic) |
| Conversion | >= 25 % FAIR, >= 28 % ACTIVE | about 23-25 % |
| Sales a day | 1.2-2.0 FAIR, 1.5-2.4 ACTIVE | 0.94, 1.15 |
| Refusal mix (share of served visits) | `TOO_EXPENSIVE` <= 25 %, `NOT_BETTER` <= 40 %, `EMPTY_SHELVES` <= 8 % (FAIR) and <= 15 % (ADVANCED policy) | about 20 %, 53 %, under 2 % |
| Buyers by class | >= 4 of 5 classes have bought by day 10 in >= 90 % of runs | not measured |
| Newcomer wait | median <= 2 days to a first served visit, p90 <= 4 | not measured |
| Hero deaths a run | <= 1.5 x baseline (more heroes, same risk each) | 0.7-0.8 |
| Legacy points, median | within +/- 2 of baseline | ask the lead (JSON `legacyPointsMedian`) |
| Hard-lock days; stuck days (G.2) | 0; <= 0.5 % of days | 0; not measured |
| End Day, JVM, 1,000-day probe | p95 < 2 ms | 0.99 ms (v3 probe, `DECISIONS.md:512`) |

**What is noise.** Confirmed in `DECISIONS.md:517-525`: three 1,000-seed runs of BALANCED_ACTIVE at base seeds 1,
10001 and 20001 gave means 27.5 / 27.4 / 27.7, so "a sweep delta is real only above about 0.3 days of mean, 0.3 sales,
0.05 deaths or 0.2 elites", and every 1,000-seed mean held within 0.5 days at 10,000 seeds (`DECISIONS.md:484`). The
median is quantised to the 5-day siege rhythm: v5 part 2 moved BALANCED_ACTIVE's median from 25 to 30 on a +0.3 mean
(`DECISIONS.md:1327-1329`). **Accept on the mean and on p10 / p90; report the median.** The floor was measured on v3
with 8 heroes; re-measure it on the instrumented build, because 50 % more heroes changes the variance.

---

### C. Names

#### C.1 Pools and style

| | Today | Proposed |
|---|---|---|
| First names | 30 | **120** |
| Surnames | 24 | **96** |
| Full names | 720 | **11,520** |
| Shared first name among 12 living | 92 % | **0 %** (avoidance) |
| Identical full names among 200 heroes over ten eras, expected pairs | 27.6 | 1.7, and 0 within an era |

Style rules an author follows, all checkable in `ContentCatalog.validate()` (names are catalog content, so the content
version goes to 3):

1. Plain ASCII letters, one capital, no apostrophes, hyphens or diacritics. First names 3-8 letters and one to three
   syllables; surnames 4-11 letters. A full name is at most 20 characters, so it fits a Town row and a counter caption
   at large font.
2. Pronounceable at sight: no silent clusters, no doubled vowels beyond "ee", "oo", "ae"; the register of the existing
   list (north-European and British rural: Mira, Halvard, Elspeth, Faolan).
3. **Distinct at a glance.** No two first names share their first three letters; any two first names differ by at
   least two edits, and by at least three if they share an initial. At most eight first names per initial, at least
   fifteen initials used. Surnames: no two share their first four letters; at least two edits apart.
4. No first name shares its first four letters with any surname (no "Rook Rooksbane", no "Quillon Quill").
5. Surnames are of four kinds, mixed evenly: nature compounds (Ashwood, Larkspur), places (Dunmore, Eastmere), trades
   (Tanner, Wainwright), old family names (Vance, Ingram). No more than four surnames per ending (-wood, -hurst, -by ...).
6. No game terms: not a material, element, affix, family, class or faction word or stem (Iron, Ember, Frost, Storm,
   Grave, Sun, Salt, Rune, Void, Ash-, Hollow-, -maw, Sword, Bow, Staff, Warden, Guardian ...) and nothing that reads
   as a weapon title ("-bane", "Slayer"). Existing `Ashwood`, `Holloway`, `Mossgrave` and `Rooksbane` are kept for old
   saves and lineages but no more are added; `Nessa` is replaced (one letter from `Tessa`).
7. Appearance and ancestry are cosmetic: one shared pool, no name sub-pools tied to a look.

Thirty sample first names in the intended style (none collides with the current 30 under rules 3 and 4):
Anwen, Alba, Brisa, Bertram, Cael, Ceridwen, Dorrin, Davin, Evander, Elowen, Fenna, Fintan, Gideon, Hedda, Imre, Joss,
Keir, Leof, Maud, Niall, Ottilie, Petra, Ragna, Rowan, Saskia, Tobin, Ulla, Varek, Willa, Yorick.

Thirty sample surnames: Aldermoor, Applegarth, Barrowby, Birchall, Cobbett, Crowhurst, Dovecote, Dray, Eastmere,
Fenwick, Foxglove, Gorse, Harrowgate, Ingram, Juniper, Kettleby, Larkspur, Millrace, Norwood, Orchard, Penhallow,
Ravensworth, Sedgewick, Stroud, Tanner, Upcott, Varley, Wainwright, Whitlock, Yewdale.

#### C.2 Generation

One place: `Heroes.generate` (`Heroes.kt:16-35`) already creates every hero (run start, arrivals, mentees, event
arrivals, descendants). Same streams as today (HEROES or EVENTS, whichever the caller passes) and the **same two
draws**; only the lists they pick from change.

```
firstPool   = content.firstNames
              - first names of living heroes
              - first names already used in this run (any fate)          // relaxed first when the pool runs dry
              - the ancestor's first name (descendants)
surnamePool = content.surnames
              - surnames of living heroes
              - surnames of this run's guild founders ("the <surname> Company" stays unambiguous)
              - surnames held by a legacy lineage, unless this hero is that lineage's descendant
name    = rng.pick(firstPool)        // fallback chain: allow reuse of the longest-dead hero's name, then any name that
surname = rng.pick(surnamePool)      // does not repeat a full name in this run
```

- **Active-town collisions**: none, by construction, for first names, surnames and full names. Similar-looking names
  are prevented by the pool rules (rule 3), not by run-time logic.
- **Long runs**: 120 first names cover 120 heroes without reuse. The 1,000-day probe ended with 95 heroes
  (`DECISIONS.md:512`). After that the name of the hero dead longest returns first; a full name never repeats in a run.
- **Across eras**: lineage surnames (at most 10 are kept, `Legacy.kt:88`) are reserved for descendants, so a surname
  the Legend Board remembers always means the family. Mentees do not take the mentor's surname: kinship is `descendantOf`.
- **Stable identity**: `HeroId` is the identity everywhere and names are data on the hero, so changing the pools breaks
  no save. Fix the three places that use a name as a key (`GameEngine.kt:75`, `WorldEvents.kt:329`, `333`) by giving
  `LineageAnchor` an `id` (era + hero ID, default empty for old profiles) and `Hero` a `lineageId`.
- **Appearance** must come from the same stable source as identity (hero serial and run seed, or a stored seed), never
  from the name string, so a renamed pool does not change a face.
- **Test**: 1,000 seeds x 60 days: zero living pairs sharing a first name or surname; zero repeated full names in a
  run; every descendant carries the anchor's surname and a different first name; two draws per hero as before.

---

### D. Regulars and recognition

#### D.1 Facts already in state

| Fact | Field | Written at |
|---|---|---|
| Loyalty; "a regular" at 3 | `Hero.loyalty` (`Model.kt:103`), `regularLoyaltyThreshold` (`BalanceConfig.kt:191`), `Market.isRegular` (`Market.kt:108`) | purchase +1 (x2 Loyal) `Market.kt:121-122`; commission +2 `Market.kt:202`; ambition +2 `Heroes.kt:259` |
| Purchases | weapon history `SOLD` (kept forever), `WEAPON_SOLD` event (30 days) with price, premium, trade-in | `Market.kt:128-131` |
| Trade-ins | weapon history `TRADED_IN` with the hero | `Market.kt:117` |
| Commissions | `Commission.buyerId`, `status`, `deliveredWeaponId` (`Model.kt:148`); history `COMMISSION` | `Market.kt:198-205` |
| Ambition and progress | `Hero.ambition`, `ambitionDone`, `expeditionWins`, `gold` (`Model.kt:115-118`) | `Heroes.kt:242-262`; text in `Heroes.describeAmbition` (`Heroes.kt:273`) |
| Guild, mentor | `Hero.guildId`, `mentorName` (`Model.kt:112-113`); `Town.guilds`; `Guild.founderId` | `Heroes.kt:123-150`, `220-221` |
| Descendant | `Hero.descendantOf` (`Model.kt:107`), `LineageAnchor.deed` | `GameEngine.kt:68-76` |
| The blade in hand was made here | equipped `Weapon` with `forgedEra`, `kills`, `victories`, `siegesDefended`, `fame`, `title`, `condition`, `signatureId` (`Model.kt:65-79`) and its history | forge, `Battle.kt:48`, `267` |
| Champion | `Town.championIds` (`Model.kt:135`); history `SIEGE` with the hero | `Battle.kt:267` |
| Yesterday | `Hero.lastActivity`, `drivenBackOnDay` (`Model.kt:106`, `121`), `health`, `elitesSlain` | `Heroes.kt`, `Battle.kt` |

Missing, and worth storing rather than deriving (weapons are pruned 30 days after they are destroyed, donated or
collected, and `lastActivity == SHOP` is set only on a purchase and cleared next morning, `GameEngine.kt:345`): the
six `Hero` counters of A.4. With them every line below is a field read.

#### D.2 How often

- At most **one** recognition line per encounter, chosen in core at End Day and stored on the visit record, so the
  scene, the Gazette and a relaunch show the same line. The choice among eligible templates is a **stateless hash**
  of run seed, hero serial and day; no gameplay stream is touched.
- **Milestone lines** (first purchase, became a regular, first visit of a descendant, a champion back from the walls
  with your blade, an ambition fulfilled with your blade) always show, once per hero per run.
- **Recurring lines** need `shopVisits >= 2`, have a 3-day cooldown per hero and never repeat the same template twice
  running for that hero.
- Targets: on days 1-3 at most 20 % of encounters carry one (introductions instead: class and what they look for);
  from day 6 on **35-50 %**; never more than 3 among the featured encounters of one day.

#### D.3 Twelve template lines

Narration only, no invented speech or opinion; every clause is a field.

| # | Line | State that justifies it |
|---|---|---|
| 1 | "{hero}'s first time at your counter." | `hero.shopVisits == 0` |
| 2 | "{hero} leaves with {weapon}: a first blade from your forge." | this visit bought; `hero.shopPurchases == 0` before it |
| 3 | "That makes {n} from your forge. {hero} is a regular now." | `hero.loyalty` crosses `regularLoyaltyThreshold` on this purchase; n = `shopPurchases` |
| 4 | "{hero}, a regular, last in on day {d}." | `Market.isRegular(hero)`; `hero.lastServedDay == d` |
| 5 | "{hero} still carries {weapon}: {k} victories with it." | equipped weapon owned by the hero; `weapon.victories == k >= 1`; its history has `SOLD` or `COMMISSION` to this hero |
| 6 | "{hero} lays {weapon} on the counter. The edge is {worn / battered}." | equipped `weapon.condition < wornConditionThreshold`; the word from the condition bands (`Labels.kt:55-59`) |
| 7 | "{hero} held the wall on day {d} with {weapon}." | weapon history `SIEGE` entry with this hero on day d; `weapon.siegesDefended >= 1` |
| 8 | "{hero} brought down {foe} with {weapon}. They call it '{title}' now." | `hero.elitesSlain >= 1`; equipped `weapon.title` begins "Slayer of"; history `VICTORY` naming the foe |
| 9 | "{hero} kept the vow, {n} foes routed, and came back for more steel." | `hero.ambition == SLAYER && ambitionDone`; n = `ambitionSlayerWins` |
| 10 | "{hero} trained under {mentor} and carries {mentor}'s old {weapon}." | `hero.mentorName != null`; equipped weapon history `INHERITED` with both heroes (`Heroes.kt:226`) |
| 11 | "{hero} of the line of {ancestor}, who {deed}." | `hero.descendantOf`; the matching `LineageAnchor.deed` |
| 12 | "{hero} could not get in yesterday and is first through the door." | `hero.turnedAwayStreak >= 1` and seated first |

Further lines that become possible with the loops in E: "{hero} asked for a {family} two days ago; today there is one"
(`hero.want`); "{hero} picks up {weapon}, once {previousOwner}'s" (history `TRADED_IN` or `RECOVERED` with another
hero); "Sent by {guild}; the guild paid 30" (Guild Patronage); "{hero} came in limping from yesterday's rout"
(`drivenBackOnDay == day - 1`).

---

### E. New gameplay loops (five, each inside the existing day)

None adds a navigation destination, a timer, a combat command, manual equipping or generated text. Each maps to one
clause of the owner's brief. Build order is the order listed; E1 and E2 carry most of the value.

| | Loop | Brief clause | Drive |
|---|---|---|---|
| E1 | Standing wants | recognizable customers; a reason to play another day | anticipation |
| E2 | The town arms for the siege | consequences I can follow | mastery |
| E3 | Rumours and the clue ladder | discoveries worth pursuing | curiosity |
| E4 | Commission situations | satisfying transactions | anticipation, ownership |
| E5 | Maker's ledger | a reason to play another era | ownership |

#### E1. Standing wants

| | |
|---|---|
| Trigger | A served hero leaves without buying (`NOT_BETTER`, `NOT_SUITED`, `TOO_EXPENSIVE`, `EMPTY_SHELVES`). The engine records what would have sold: `Hero.want = (family, minimum effective power, budget = gold + trade-in credit, sinceDay)`. Family is the hero's class-preferred family with the fewest listed weapons (ties by catalog order); no RNG. |
| Player decision | Tomorrow's stock: forge the bow Wren asked for, price it inside her budget, or ignore it and serve someone richer. Shown in the customers list and on the End Day "Tomorrow" card: "Wren wants a bow; can spend about 90." (Town already shows gold.) |
| World response | While a listed weapon answers the want (right family, affordable, a real gain) the hero is +0.30 willing (`needWantMet`) and, being a need case, weighs more for a seat. A want lapses after `wantDays` 3 or on any purchase. |
| Payoff | The hero comes back and buys; recognition line "asked for a bow two days ago; today there is one"; loyalty and, later, a regular. |
| Variation | 5 classes x 6 families x purse x current gear; wants change as heroes level, wear blades and earn. |
| Reuses | `Evaluation` fields (`Market.kt:15`), class `preferredFamilies`, `tradeInCredit`, the reason codes. |
| Cost | **M**: one nullable field, 20 lines in the visit resolver, one read-only list in the UI. |
| Balance risk | Medium: conversion rises and heroes are better armed. Bounded by purses. |
| Acceptance | A REQUEST_DRIVEN bot satisfies >= 60 % of wants within 3 days (BALANCED_FAIR <= 25 %); heroes with a satisfied want return within 2 days in >= 50 % of cases; in a playtest a fresh player can name one hero and what they want by day 3. |

#### E2. The town arms for the siege

| | |
|---|---|
| Trigger | The existing siege warning, two days before each siege (`Battle.warnOfSiege`, `Battle.kt:311`), which already names the faction and its weakness. |
| Player decision | Spend scarce Frost Bloom or Sun Ash on counter weapons for the next two days, or sell ordinary stock. The forge and the shelf show "Frost bites the Ashclaw" beside matching items. |
| World response | During the warning window heroes value a counter-element weapon (`threatUtility` +0.8 in `utility`, and the faction matchup counts in the sidegrade gate of F.3); current champions are +0.15 willing. A resisted element is refused with a new code, `RESISTED` ("no use against the Ashclaw"). |
| Payoff | The siege replay shows champions striking with the counter blades (matchup x1.25, `Power.kt:19-26`); less forge damage; the blessing choice that follows a held siege. |
| Variation | Three factions, two weakness elements (frost, frost, sun), two resisted (fire, fire, grave), a warlord at pressure 50 (Giant Slayer x1.5). |
| Reuses | `FactionDef.weakTo / resists`, `Power.matchup`, the forecast (`GameEngine.siegeForecast`), the SYNERGY bot as the upper bound of what the behaviour is worth (+14 mean days over BALANCED_FAIR). |
| Cost | **S-M**: one utility term, one reason code, two labels. |
| Balance risk | **Medium-high**: it teaches every player what SYNERGY does. It only reorders purchases; the player still has to forge the blades. Keep the first-era band defined on BALANCED_FAIR and watch the gap. |
| Acceptance | A SIEGE_PREP bot (BALANCED_FAIR, but counter-element forging in the warning window only) lands between FAIR and SYNERGY, at most +6 mean days over FAIR; share of champions holding a counter element at a siege rises measurably; `RESISTED` is 3-10 % of refusals in warning windows. |

Why this matters more than its size: the cheapest recipe in the game, iron + ember resin, makes **fire** weapons, and
two of the three factions resist fire (x0.8, `LaunchContent.kt:183`, `197`). The default action is quietly penalised
and nothing at the forge says so. E2 makes that a visible refusal and a visible choice.

#### E3. Rumours and the clue ladder

| | |
|---|---|
| Trigger | Real events only: a hero slays an elite with a blade from this forge; a commission is completed; the existing fragment, notes and master-smith events. Each grants one rung on one undiscovered signature (chosen on the EVENTS stream, as the fragment event already does at `WorldEvents.kt:288-297`), preferring a signature that shares the family or element of the weapon involved. |
| Player decision | Which rumour to chase: buy the catalyst (15-90 gold), spend 4 energy on an Advanced forge, accept a 15-50 % chance. Or stock the shelf. |
| World response | The journal entry gains a rung (see F.5): base recipe, then what kind of catalyst, then the temper, then how fine. Success names the weapon; a discovered signature becomes a recallable recipe. |
| Payoff | A permanent discovery (journal, a legacy discovery point up to the cap of 5 a run), a named weapon heroes want (`PRIZED` / `STORIED`), a line in the paper. |
| Variation | 24 signatures, four catalysts, three tempers; rumours persist across eras, so a clue found in era 2 can be finished in era 3. |
| Reuses | `SignatureCatalog`, `Journal` states, `SignatureDef.missing`, the three knowledge events. It also closes a LOCKED sentence the game does not yet honour: "clues from heroes, merchants and events" (GDD 4.6). |
| Cost | **M**: `Journal.signatureClues: Map<String, Int>` (a bit set, merged across eras by OR), authored phrases, a recall button. |
| Balance risk | Low: a signature adds 4-9 power and two affixes; discovery points are capped. |
| Acceptance | A SIGNATURE_PURSUIT bot makes >= 1 unique first discovery by the end of era 2 in >= 70 % of accounts; no signature is first found without at least rung 1; rumours per run 2-4. |

#### E4. Commission situations

| | |
|---|---|
| Trigger | The daily commission roll, but the kind comes from state. REPLACEMENT: the patron's blade broke, was lost or is worn below 50. SIEGE_PREP: the patron is a current champion and a siege is at most 4 days away (family + the faction's weakness, due the day before the siege). AMBITION: an unfulfilled COLLECTOR asks for the band that fulfils the ambition. FIRST_BLADE: a guild founder or mentor orders for a named unarmed newcomer (payer and recipient differ). STANDARD and NOBLE as today. Up to **two** open at once. |
| Player decision | Accept or decline; which blade to hand over (F.2); whether to hold stock back from the shelf. |
| World response | The reward, +2 loyalty and +2 reputation as today, and a consequence the player can see: the champion carries it into the siege replay, the collector's ambition headline, the newcomer's first expedition. |
| Payoff | A transaction with a reason and an afterlife. |
| Variation | Six kinds x family x element x band x deadline. |
| Reuses | `Commission` (+ `kind`, `recipientId`, both defaulted), `wornConditionThreshold`, `Town.championIds`, ambitions, guilds, the existing reward formula. |
| Cost | **M**. |
| Balance risk | Low-medium: two slots raise commission income by perhaps 300-400 gold a run (arithmetic). Hold `commissionChancePerDay` at 0.3 and measure. |
| Acceptance | A REQUEST_DRIVEN bot completes >= 70 % of accepted commissions; no kind exceeds 40 % of offers; a SIEGE_PREP blade is wielded at its siege in >= 60 % of completions. |

#### E5. Maker's ledger

| | |
|---|---|
| Trigger | Any blade of yours earns a history line that matters: a first victory, a title, a siege, a change of hands, a return from the dead owner, a return from an earlier era. |
| Player decision | When a storied blade comes back to the shop (trade-in, recovered, returned legend): resell it (fame premium, the `STORIED` reason), re-hone it, give it to the watch, or melt it. All four exist; the ledger makes the choice visible. |
| World response | The buyer becomes the next line of the blade's story; a legend recorded at run end can return next era. |
| Payoff | Following one blade across a sale, several fights and an ownership change (the review's 6.6 acceptance test). |
| Variation | Earned titles, fates (the part 3 merge added merchant resale and guild inheritance), eras. |
| Reuses | `Weapon.history`, `fame`, `title`, `LegendEntry`, the fates of part 3. Engine work is only review G09: store affixes, flaws, catalyst and signature on `LegendEntry`, and read owners from every ownership kind, not only `SOLD` / `COMMISSION` (`Legacy.kt:54`). |
| Cost | **S** in core, the rest is presentation. |
| Balance risk | None. |
| Acceptance | Every line of a ledger entry maps to a stored history entry; a returned legend keeps its signature name only if it keeps the signature. |

#### Rejected, and why

| Idea | Why not |
|---|---|
| Haggling with each visitor | A decision inside the committed End Day resolution; needs a new phase and makes a day much longer. Prices are set in preparation by design. |
| Heroes bring worn blades in for repair | A good transaction, but it needs a weapon that is owned by a hero and held by the shop: a new `WeaponLocation`, new invariants and a save migration. Wear already drives replacement and trade-in. Revisit after this update. |
| "Recommend this blade to this hero" | One step from equipping heroes, which is LOCKED out. Commissions and wants cover the intent. |
| Apprentice, auto-forge, stock that sells while away | Idle production; LOCKED out. |
| Rival smith, auctions, rentals | Large systems with their own economies; nothing in state to build on. |
| Daily demand fads not tied to state | Noise. The brief asks for demand tied to real threats. |
| Reputation tiers that unlock shop features | A permanent meta layer and, in practice, a new screen. |
| Siege strength scaled to population | Hidden rubber band. |
| Relationship meters, gifts | A second loyalty system; loyalty already exists and is underused. |

---

### F. Fixes

#### F.1 F04: the visible criterion is the rule

1. One definition of the quality bands in core (`QualityBand`, from the four thresholds `BalanceConfig` already holds
   for rarity); `Labels.quality` reads it.
2. Commissions ask for a **band**: `minQuality` is always a band floor. Standard: decent (35) 60 %, fine (50) 40 %,
   the same split as today's 35-60 draw. Noble: superb (70). Rewards keep the formula: 110 / 140 / 540 gold
   (today 110-160 and 480-570; the standard mean drops from 135 to 122, left alone because E4 adds a second slot).
3. COLLECTOR: `ambitionCollectorQuality` 60 -> **50** (fine), so "wants a fine blade" is true. This makes the ambition
   a little easier; measure ambitions fulfilled per run.
4. One pure function, `Market.commissionFit(weapon, commission)` returning OK / FAMILY / ELEMENT / QUALITY, used by
   `resolveCommissions` and by the UI, which shows on each candidate "fits Mira's request" or the one thing missing.
5. The Gazette line uses the band word instead of the fixed "fine" (`Market.kt:239`).
6. Old saves: an open commission with an off-band `minQuality` is lowered to its band floor on load (in the player's
   favour), in `SaveCodec`.
7. Regression: qualities 49 / 50 and 69 / 70; family and element mismatches; delivery on the deadline day.

#### F.2 F06: reservation

- **Order**: resolve accepted commissions **before** shelf visits (swap `GameEngine.kt:279-280`; GDD 3.2 groups both in
  step 2 and marks the ordering PROPOSED).
- **Which blade**: the **least sufficient** eligible one (lowest quality, then lowest asking price, then ID), **storage
  before shelf**. Never the best one by default.
- **Preview**: the commission card states before End Day what will happen: "Ready: Frostbound Bronze Spear will be
  handed over" or "Nothing fits yet" (the same pure function as F.1).
- **Trade-off**: with the swap a patron can take a shelf blade a visitor would have paid more for. Storage-first,
  least-sufficient and the preview make that rare and visible. The alternative (keep the order and hide reserved
  stock from visitors) keeps the "sold before delivery" trap for anything the player forgot to reserve.
- **Optional follow-up** if playtests ask for it: `Command.ReserveForCommission(commissionId, weaponId)` to choose the
  blade explicitly. Not needed for the fix.

#### F.3 Sidegrade gate (6.1)

```
value(w, hero) = w.power * conditionFactor(w) * classFit(hero, w)          // as today, kept as Double
               * affixAttackMultiplier(w) * fameFactor(w)                  // both exist in Power.kt and are bounded
               * matchup(w, besieger)        only while a siege warning is active (E2)
gain     = value(candidate) - value(current)                               // no Int truncation
eligible = affordable && ( gain > 0
        || ( gain >= -sidegradeTolerance * value(current)                  // 0.05
             && sideReason(hero, current, candidate) != null ) )
sideReason = TASTE    candidate.element == hero.elementTaste && current.element != hero.elementTaste
           | PRIZED   unfulfilled COLLECTOR && candidate.quality >= ambitionCollectorQuality > current.quality
           | STORIED  candidate.fame >= legendFameThreshold > current.fame
```

Each reason is one-way: once the hero holds a blade with that property it cannot fire again, so there is no churn.
`utility` and the 0.5 threshold are unchanged (a taste match is worth 1.0 already). New bought codes: `TASTE_MATCH`,
`PRIZED`, `STORIED`, `COUNTERS_THREAT`. The merchant resale path stays strict. Acceptance: 1-4 sidegrade purchases a
run; no hero makes more than three; champion defense at the first two sieges not more than 2 % below baseline; hero
gold on siege days not more than 10 % below.

#### F.4 Weak upgrades (6.3)

- Do not change magnitudes now. Two things are about to move the measurements: the legacy-tracks branch (Known Name
  starts heroes as regulars; yardsticks) and v6 traffic. The v5 signboard alone lifted Tireless Smith from +0.3 to
  +0.6 and Family Savings from +0.1 to +0.3 mean days.
- **Gate per track at max level**: at least +1.5 mean days under one competent policy (ACTIVE, ADVANCED or REQUEST),
  **or** at least +15 % on its own purpose metric.

| Track | Purpose metric |
|---|---|
| Tireless Smith | shop actions a day in days 1-10; Advanced forges a run |
| Family Savings | tools owned by day 5; day of the first tier-3+ sale |
| Lucky Hammer | exceptional share; affix-bearing weapons sold |
| Known Name | sales and unique buyers by day 5; champion weapon power at the first siege |
| Thrifty Hands | augments spared; rare-augment forges a run |

- Rework only what fails both after v6: Family Savings (add: the first tool level at half price) and Lucky Hammer
  (2 -> 3 points a level) are the likely candidates.
- Whatever the numbers, the run-end screen should state the concrete next-run change of a purchase.

#### F.5 Catalyst identity, clue progression, recipe recall (6.5)

**Identity.** Today all four are the same +5 / -5 / +4 (`Forge.kt:68-71`, `87`). Give each one job, as content fields
on the catalyst's `MaterialDef`, validated (no two alike, all bounded). This also removes two flat `BalanceConfig`
fields (3 slots).

| Catalyst | Price, stock | Exceptional | Defect | Quality | Other | Reads as |
|---|---|---|---|---|---|---|
| Binding Salt | 15, 2 a day | +0 | **-8 points** | +2 | - | steady: the safe hand |
| Runestone Shard | 30, 1 | +0 | +0 | +2 | **+1 affix slot** | "guides an affix" |
| Dragon Oil | 55, 1 | +0 | +5 points | **+8** | - | "burns hotter": raw quality, some risk |
| Void Ink | 90, 1 | **+12 points** | +0 | +0 | - | "rewrites the roll": brilliance |

Acceptance (CATALYST_x bots): each is best on its own metric (defect rate, affixes per weapon, mean quality,
exceptional rate); none beats the others on mean days by more than 1.0; none is worse than no catalyst per 100 gold.

**Clue ladder**, stored per signature as a bit set in the legacy journal:

| Rung | Journal shows | Earned by |
|---|---|---|
| 0 | nothing | - |
| 1 base | "Hides something more." (today's OBSERVED) | forging the base recipe; the fragment event; a rumour |
| 2 catalyst | one authored phrase per catalyst: "wants something to bind it" (salt), "wants a word cut into it" (rune), "wants a hotter fire" (oil), "wants a rule rewritten" (ink), or "wants nothing added" | a second attempt on the base recipe, or a rumour |
| 3 temper | "more patience" / "a steadier temper" / "more daring" (the existing words) | an attempt with the right catalyst, or a rumour |
| 4 finish | "finer work: at least {band}" | an attempt with the right catalyst and temper |
| found | name, flavour and the **full recipe**, with "Use this recipe" filling the forge draft | the transformation |

Changes this needs: `recordSignatureClue` must keep answering after the first miss (`Journal.kt:56`); the hint must
show only the rungs earned (today it always shows the temper, `Journal.kt:115-120`); each miss shows as a line on the
forge result, not as a new Gazette discovery. No odds are shown at any rung.

#### F.6 Softlock and recovery (6.7)

Three states, measured separately (G.2): **resting** (the player could act and chose not to), **drought** (no possible
sale today but a legal forge or other useful action exists) and **stuck** (no legal forge, no listed weapon any living
hero could both afford and gain from, no deliverable commission).

Test with shock scenarios rather than more competent bots: SPENDTHRIFT (always the dearest materials, prices x1.8),
NOVICE (random recipes and prices 0.5-2.0, never salvages or donates), BROKE_START (0 gold and no materials on day 3).
Report stuck days, the longest stuck streak and days to the next sale.

By arithmetic a true stuck state is reachable today: gold under 8, no augment in stock, every hero already better
armed than the shelf. Salvage returns a core but no augment, and the only ways out are looted augments (about one
every three to four days), wear on heroes' blades and events. I do not recommend a valve before the scenarios run. If stuck
streaks of 3+ days appear in more than 1 % of NOVICE runs, add the smallest one: Salvage also returns the augment of
an unworn, never-sold blade. No gold is minted and nothing is repaired with gold.

#### F.7 Small fixes found on the way

| Fix | Where | Why |
|---|---|---|
| Cap the collector's payment at `askingPrice x collectorPriceMultiplier` | `WorldEvents.kt:359` | Unbounded today (1.6). |
| Numeric hero order everywhere | `ResolutionContext.kt:91`, `Model.kt:319` | One rule; removes the `h10 < h2` surprise from every list. |
| Keep `improvement` as a Double | `Market.kt:61-63` | A 0.9 gain is a gain. |
| Reason code per declined item, not per shelf | `Market.kt:42-48` | Needed for truthful encounter lines (1.1). |
| Count an `EMPTY_SHELVES` visitor as unserved for priority | `Market.kt:30-34` | A.4. |

Numbers introduced by E and F, all in `CustomerConfig` unless noted: `wantDays` 3; `threatUtility` 0.8;
`championSiegeWillingness` 0.15; `sidegradeTolerance` 0.05; `maxOpenCommissions` 2; commission band weights 0.6 / 0.4;
`ambitionCollectorQuality` 50 (existing flat field). Catalyst numbers are content (F.5). Clue rules need no numbers.

---

### G. Simulator extensions

#### G.1 Policies

| Policy | What it does | What it answers |
|---|---|---|
| ADVANCED_SMITH | Advanced Forge whenever a catalyst is on hand or affordable within the reserve; Quick with the remainder | Quick against Advanced viability (review 6.4); Tireless Smith's purpose metric |
| TECHNIQUE_TEMPER / _QUENCH / _ETCH | ADVANCED_SMITH with one fixed technique | "Each is a trade, never strictly better" |
| CATALYST_SALT / _RUNE / _OIL / _INK | ADVANCED_SMITH with one fixed catalyst | F.5 acceptance |
| REQUEST_DRIVEN | Forges for open commissions (family, element, band) and for recorded wants before general stock; holds matching blades in storage | E1, E4, F.2; commission completion and expiry rates |
| SIEGE_PREP | BALANCED_FAIR, but forges the besieger's weakness in the warning window | E2; the size of the SYNERGY gap a new player can reach |
| SIGNATURE_PURSUIT | When a signature has at least rung 1 and its materials are obtainable, attempts it with every condition the journal shows | E3; unique discoveries; time to the first signature |
| SPENDTHRIFT, NOVICE, BROKE_START | The shock scenarios of F.6 | Recovery |
| Blessing strategy `--blessing first|energy|quality|sales|patronage|defense` | Replaces "take the first offered" (`Simulator.kt:165`) for any policy | Whether each blessing is worth choosing (A.5) |
| Multi-era `--eras N --buy cheapest|walls|track=...` | Plays N eras in sequence, claims legacy, buys upgrades by rule, carries the journal, Legend Board and lineages | Natural legacy growth; days per era; eras to all upgrades; discoveries and name repeats across eras |

#### G.2 Metrics (definitions)

| Metric | Definition |
|---|---|
| `willingPerDay`, `servedPerDay`, `turnedAwayPerDay` | Counts from the visit resolver, as mean, p10, p50, p90; share of days with served <= 2; share of days at capacity |
| `uniqueServed` (run, day 5, day 10) | Distinct hero IDs with a served visit |
| `servedShareSpread` | Among heroes alive >= 10 days: max and min of served visits per day alive, and their ratio; also by arrival order (starting cast against later arrivals) |
| `classCoverage` | Classes present at the start; classes with a served visit and with a purchase by day 5 and day 10 |
| `newcomerWait` | Days from arrival to the first served visit and to the first purchase (median, p90), non-starting heroes |
| `returnRate` | Share of served visits by a hero served before; mean days between a hero's visits; purchases per hero (mean, max) |
| `conversion` | Purchases / served visits, overall and for days 1-5, 6-15, 16+ |
| `refusalMix` | Share of served visits by code (the counts exist); with F.3 and E2 also `TASTE_MATCH`, `PRIZED`, `STORIED`, `COUNTERS_THREAT`, `RESISTED` |
| `salesPerDay`, `sellOutDays` | Shelf sales a day; days on which the last served visitor met an empty shelf |
| `heroGold` | Mean hero gold on days 1, 5, 10, 15; share of heroes who cannot afford the cheapest listed weapon |
| `nameCollisions` | Days x pairs of living heroes sharing a first name, a surname or a full name; repeated full names per run and across eras |
| `expeditions` | Won, lost, fatal; win rate |
| `siegeSnapshot` | Per siege: pressure, militia, armory, the three champions' powers, champions holding the weakness or the resisted element, raid power, result |
| `stuckDays`, `droughtDays`, `longestStuckStreak` | F.6 definitions; replaces reading `hardLocks` alone |
| `signatureFirsts`, `signatureWeapons`, `daysToFirstSignature`, `cluesEarned` | Unique first discoveries; all transformed weapons (today's `signatureDiscoveries`, renamed); the day of the first; rungs earned |
| `commissions` | Offered, accepted, completed, expired, by kind; blades delivered from storage against from the shelf |
| `wants` | Recorded, satisfied within `wantDays`, lapsed |
| `sidegrades` | Purchases admitted by a side reason, by reason; most by one hero |
| `patronage` | Guild-member share of visits and conversion on blessing days against the five days before; stipend gold |

Already present and to keep: visits by reason code, activity shares, fates and artifact recovery (part 3), and the
legacy-tracks branch's yardsticks once merged.

#### G.3 Baseline runs to execute now (flags that exist at `8cc133b`)

With `OUT=C:/Users/tuncb/AppData/Local/Temp/claude/c--Users-tuncb-Desktop-Blacksmith-Project/cb469540-0a58-43a6-af0d-70fa21126365/scratchpad/major_update/sim`
(create the folder first; `--json` takes a path relative to the simulator's working directory, so an absolute path
is safest).

| # | Command | Purpose |
|---|---|---|
| 1 | `./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE --content launch --json $OUT/base_all_s1.json"` | The reference table at HEAD (must reproduce `DECISIONS.md:1532-1540`) with visits by code, activity shares, gold earned, legacy points and upgrade impact. Fills the "Baseline" column of B.4. |
| 2 | `./gradlew :core:simulate --args="--runs 1000 --seed 10001 --policy all --noImpact --content launch --json $OUT/noise_s10001.json"` | Noise floor on v5, every policy. |
| 3 | `./gradlew :core:simulate --args="--runs 1000 --seed 20001 --policy all --noImpact --content launch --json $OUT/noise_s20001.json"` | Same, third base seed. |
| 4 | `./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy BALANCED_ACTIVE --noImpact --noTool signboard --json $OUT/active_cap4.json"` | Capacity 4 for the whole run: the lower bracket of lever 2 at 8 heroes. |
| 5 | `./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy BALANCED_ACTIVE --noImpact --toolCost signboard=0.1 --json $OUT/active_cap6_early.json"` | Signboard at 15 / 40 gold, so capacity 6 from about day 2: the upper bracket. Rows 4, 5 and the ACTIVE row of 1 give visits, sales and conversion per extra seat **without** extra heroes. |
| 6 | `./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy all --noImpact --siegeModifier 2.1 --json $OUT/siege_2_10.json"` | Slope of days per unit of raid strength on v5, the compensation lever available today. |
| 7 | `./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy all --noImpact --siegeModifier 2.2 --json $OUT/siege_2_20.json"` | Second point of the slope. |
| 8 | `./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy BALANCED_FAIR --impactPolicy BALANCED_FAIR --json $OUT/impact_fair.json"` | Per-upgrade impact for the plain smith (F.4). |
| 9 | `./gradlew :core:simulate --args="--runs 10000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE --content launch --perf --json $OUT/base_all_10k.json"` | The 10,000-seed v5 review the checklist still lacks (review B01); about six minutes by the v3 timing. |

Rows 1-5 are needed before any design number above is trusted; 6-9 can follow. From row 1 I need, per policy:
`visitsPerRun` by code, `soldPerRun`, `forgedPerRun`, `goldEarnedMedian`, `heroDaysPerRun`, `activityShare`,
`legacyPointsMedian`, days mean / p10 / p90.

Not possible today and needed first (B.3 prerequisite): overrides for `startingHeroCount`, `minHeroPopulation`,
`maxHeroPopulation`, `maxCustomersPerDay`, `baseVisitChance`, `expeditionSuppression`, `raidPerDay`.

---

## 3. Where the review is wrong, stale or incomplete

| # | Review text | Finding at `8cc133b` |
|---|---|---|
| 1 | 6.2: "The Signboard raises visit probability while ordinary customer capacity remains four" | **Stale.** Since `d7b3283` the Signboard adds a seat per level; measured +0.4 days and +3.5 sales for the active smith. Guild Patronage is the one still on probability. |
| 2 | F05: reputation, loyalty "and the Signboard raise chances" | Stale for the Signboard. The mechanism and the 0.246 % example are correct (re-derived). Two additions: the string sort puts **later arrivals h10-h19 ahead of the starting cast h2-h9**, and the v5 Signboard only masks the bias at 8 heroes (last / first 0.77) and not at 12 (0.15). |
| 3 | 6.3 table | v4 numbers. v5 part 1 already moved Tireless Smith to +0.6, Family Savings to +0.3 and Known Name to 0.0, and Known Name is being reworked in an unmerged branch. |
| 4 | F04 | Understated. The band label is also on Home (`HomePanel.kt:130`); the Gazette offer says "fine" for every value (`Market.kt:239`); noble commissions (60-75) and the COLLECTOR ambition (60) have the same mid-band problem. |
| 5 | F06 | Understated. Besides selling first, the patron takes the **highest-quality** match in the shop (`Market.kt:197`), including from a priced shelf. |
| 6 | 6.4: Quick-only bots do not establish signature pursuit | True, with a nuance: 6 of the 24 signatures need no catalyst and are reachable by Quick Forge, which is where the bots' signature counts come from. The artifact-recovery metric the review asks for exists since part 3. |
| 7 | 6.7: the counter increments "when the policy cannot forge and there are no listed weapons" | Correct, and weaker than it reads: the check runs before the day's listing step (`Simulator.kt:187-189`), so it tests yesterday's leftover shelf. |
| 8 | 6.1 | Correct. Add: both sides of the improvement are truncated to Int before subtracting (`Market.kt:61-63`). |
| 9 | Not in the review | The collector pays 1.5 x any listed price with no ceiling (1.6). |
| 10 | Not in the review | Reason codes describe the shelf, not the declined item: `NOT_BETTER` hides "the better one was too dear" (1.1). This limits truthful encounter lines. |
| 11 | Not in the review | Starting population is 8; the GDD's PROPOSED range is 10-14. Raising it is a move toward the GDD. "Roughly 2-4 customers a day" is also PROPOSED; going to 6 is a deliberate departure to record in DECISIONS. |
| 12 | Not in the review | The cheapest recipe makes fire weapons, which two of three factions resist; nothing at the forge says so (E2). |
| 13 | Not in the review | `BalanceConfig` cannot take flat fields any more; every plan item that says "add a number to `BalanceConfig`" must say which nested group. |
| 14 | Not in the review | Lineages are matched by name string (1.4); with a 3.8-15 % chance of duplicate full names in a town this is a latent bug, removed by C. |

## 4. Suggested order and open decisions

Order (each step is separately measurable): (0) simulator metrics and overrides, no behaviour change; (1) fair
selection and numeric order; (2) names with in-town avoidance and class coverage; (3) population, capacity and the
suppression / raid compensation; (4) F04, F06 and the collector cap; (5) wants and the need terms (E1); (6) the
sidegrade gate and siege demand (F.3, E2); (7) catalysts, clue ladder and rumours (F.5, E3); (8) commission situations
(E4); (9) the ledger's `LegendEntry` fields (E5). Steps 1-3 change RNG consumption and need the full re-baseline;
steps 2 and 4 are independent of the rest.

Decisions I need from the owner or the lead:

1. Base capacity **6** and population **12** (my recommendation) against 5 / 10 (safer, less visible) or 8 / 14 (the
   scene budget and the Signboard suffer).
2. Guild Patronage as a guild **stipend** (my recommendation) or as **guests outside capacity**.
3. COLLECTOR threshold moved to 50 (fine) or to 70 (superb).
4. Two open commissions at once.
5. Whether catalysts change general forging (F.5) or keep the shared bonus and gain identity only through clues.
6. `RULES_VERSION` 2 with this update, since every seed replays differently.

