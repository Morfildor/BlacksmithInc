# Classic gameplay rescue

15 October 2026. Local `main`, HEAD `d9c31b41c2e3e055fb75902f0c50f66e51f6fe75`; rules 7, balance 16, content 6, save schema 7. Initially no tracked edits; pre-existing untracked `build-output/` is preserved. No branch changes, commits, pushes, resets or scenario regeneration.

**Recommendation:** make the first two Classic sieges ask for a specific response that cheap general-purpose weapons cannot erase. Keep an inexpensive watch-led answer and a more selective customer-equipment answer. Do this before adding recipes or increasing the enemy stat ladder.

**Progression finding:** no broken material-unlock transition was reproduced at this HEAD. Paid, declined and ignored visits, saving/reloading, delivery delays, sold-out stock and the second-siege fallback work in the tested pipelines. Consequently this delivery changes **no production gameplay or balance code**. It adds deterministic coverage, a reproducible measurement harness and this proposed design. There is no substantiated progression fix to claim.

The owner's experience remains the design problem. A repeated **Iron/Frost sword** held 96/96 first sieges and 91/96 second sieges. Untargeted expensive variety held 96/96 and 92/96 while spending about twice as much by day 10. Preparation becomes decisive later, but the first ten days poorly demonstrate why to change the recipe.

## 1. Material progression: what actually happens

### Pipeline and authoritative state

Read alongside `02-gameplay.md`, `04-roadmap.md`, Wave 1 validation, the current GDD and actual source. Below, `C/` means `core/src/main/kotlin/com/tinyblacksmith/core/`; `U/` means `app/src/main/java/com/example/blacksmithproject/ui/`.

1. `C/battle/Battle.resolveSiegeIfDue` records a win **or loss** on day 5. `DeepRoad.fought` counts both. Winning is not required to unlock supplies; an ended run cannot reach another morning.
2. `GameEngine.newMorning` advances to day 6, calls `DeepRoad.morning`, restocks, then offers visitors. `Encounters.offerMorning` gives a due debt follow-up priority; otherwise `DeepRoad.visitor` guarantees the caravan master independently of visitor chance. A debt can postpone her to the next free morning. This is intentional, not a random failure to draw her.
3. Offering her immediately persists `deepRoadOpenDay = visitorDay + 2`. Paying 60 gold invokes `DeepRoad.openNow`: stage 1 opens and its ordinary daily stock is added **that morning**. The fee buys access, not an owned Starsteel ingot. Declining and ignoring leave the scheduled date intact. Ignoring expires to the free wait answer on End Day. An unaffordable answer is rejected without changing the schedule.
4. On the scheduled morning `DeepRoad.morning` announces access before restock. Daily deliveries can still be zero because of a delayed caravan. Restock replaces yesterday's limited stock; it does not accumulate unbought units. Caravan Ties increases stock, not access.
5. After the second siege, normally morning **11**, stage 2 opens everything regardless of the visitor, payment or `deepRoadOpenDay`. This also recovers a catalog with other visitors but no Deep Road encounter. A catalog with no visitors at all intentionally opens stage 1 on morning 6.
6. `ResolutionContext.toState` carries the date, encounter, counters and supplier stock. `SaveCodec` round-trips them; `Compatibility.admit` preserves current rules-7 progression. Older pre-road runs that already fought a siege are grandfathered into stage 1. App `GameSession` writes accepted state through the serialized repository boundary before publishing it.

### Every material, at current catalog prices

Prices below precede the world price multiplier (0.9/1.0/1.1). Limited stock is per normal delivery, before Caravan Ties/events. "Stage 1" normally means morning 6 paid or morning 8 waiting; a follow-up can shift the visitor and wait, with morning 11 as the fallback.

| Group | Materials: price / stock | Supplier access |
|---|---|---|
| Basic metals | Iron 10 / unlimited; Bronze 22 / unlimited | Day 1 |
| Limited early metal | Silver 100 / 1 | Day 1 |
| Advanced metals | Obsidian 190 / 1; Starsteel 300 / 1 | Stage 1 |
| Final metal | Moonsteel 450 / 1 | After second siege |
| Basic augments | Ember Resin 8; Frost Bloom 18; Verdant Sap 16 / unlimited | Day 1 |
| Early limited augment | Stormglass 90 / 1 | Day 1 |
| Advanced augments | Grave Dust 140 / 1; Sun Ash 210 / 1 | Stage 1 |
| Early catalysts | Binding Salt 15 / 2; Runestone Shard 30 / 1; Dragon Oil 120 / 1 | Day 1 |
| Advanced catalyst | Void Ink 220 / 1 | Stage 1 |

Tier gating applies to **all limited materials**, not just metals. Owned expedition rewards remain usable before supplier access. Ordinary expedition loot, the mine, ore merchant and Collector's Seal respect road access; elite spoils can reach beyond it. The ore merchant's extra stock is carried by a next-morning flag rather than overwritten by restock. This is already fixed at HEAD; the older PROGRESS note describing its overwrite is stale.

Availability must be read on two axes: acquisition at the supplier, and ingredients already owned for forging:

| Supplier diagnosis | Authoritative condition | What the player can do |
|---|---|---|
| LOCKED | `!engine.onTheRoad(state, id)`; normally supplier stock 0 | Wait/accelerate; use any independently acquired owned unit |
| UNLOCKED BUT OUT OF STOCK | Road permits it; finite stock is 0 | Wait for another delivery; an owned unit remains usable |
| UNAFFORDABLE | Stock exists/unlimited; gold below `materialPrice` | Earn/save gold or use owned stock |
| AVAILABLE | Stock exists/unlimited and price is affordable | Buy, then forge from owned inventory |

These are diagnostic categories, not four new production enums. `BuyMaterial` currently returns `SupplierOutOfStock` for both locked and exhausted stock; inspect access to distinguish them. A zero-stock material can also be unaffordable, so report access/stock before affordability.

`U/SuppliesSheet` uses current supplier stock, price and purse for Buy, and `roadLine`/`supplyNotes` for the reason/date. `U/ForgeUi.forgeOptions` uses **owned** inventory for usable tiles; a zero-owned ingredient stays blocked even when it can be bought. A zero-owned, newly unlocked ingredient correctly points to Supplies. Neither screen has a separate unlock timer. Owned loot can be selected while its supplier row remains locked. This separation can feel like "it never unlocked" when the player expects the visit to deliver ingredients directly.

### Verification and limits

New `ClassicProgressionTest` exercises real End Day resolution for days 1–15 on seeds 11, 31 and 4242, for paid/pass/ignore answers. Every morning checks every catalog material's access and limited stock. It round-trips and admits saves before End Day, while the visitor is open, after answering and after opening; continued state/RNG must equal uninterrupted play. Separate cases buy the last unit, reject an unaffordable purchase, forge the owned sold-out unit, restock it, inject a day-8 caravan delay without relocking, omit the story to exercise siege-two fallback, and remove all visitors to exercise the day-6 fallback.

These timing tests fund the purse and set starting integrity to 1000 **only in the fixture** so defeat/cash cannot prevent observing day 15. The 1,344 strategy runs below use normal production health/economy except explicitly labeled diagnostic variants. Existing `DeepRoadTest`, `RunVarietySecondPassTest`, compatibility/save tests and app Forge/Supplies model tests cover adjacent behavior. UI wiring was inspected; no new device/restart-on-Android claim is made.

**Intentional restrictions explaining the report:** the 60-gold fee supplies no owned ingredients; high metals cost more than the initial 250-gold purse; Moonsteel cannot normally be bought until after day 10; quality caps stop Iron at 55 and Bronze at 65; only Starsteel/Moonsteel reach Epic; all catalysts share the same ordinary Classic forge bonuses. Reloading does not bypass these limits. An existing installation or the owner's particular save was not supplied, so this does not establish what build/save produced their experience.

The GDD is not a reliable numeric reference here: §5.2 still lists Silver 45, Starsteel 120, Moonsteel 180 and fixed fine-metal days, while actual catalog/config use 100/300/450 and Deep Road; §3's Heartfire table omits the early warlord exception. Treat the current engine/config as the measured behavior. Do not revert to those older timings/prices under the label of a bug fix.

## 2. Measured Classic difficulty

### Method and strategy definitions

**96 paired seeds, 910001–910096, horizon 80 resolved days; 1,344 runs total.** All reached defeat before the cap. Launch catalog, default account except labeled maxed runs. The primary comparison fixes blessings to first offered, declines visitors and relics, and accepts ordinary offered commissions. This controls extra visitor/relic policy differences; it deliberately does not estimate an engaged human's whole experience. A separate native-depth check restores existing visitor/relic habits.

- **A Fire / A Frost:** test-only repeat control. Quick, Balanced, Iron Sword + the same augment every time; forge while normal energy and money allow; buy missing ingredients, list at suggested price, accept offered commissions. No tools, honing, donations or equipment control. Existing balanced bots do **not** repeat swords, so were unsuitable substitutes.
- **B Invest:** unchanged `SimulationDriver` / `BALANCED_INVEST`: random weapon families, highest-tier affordable/owned materials, Quick/Balanced, no demand or siege response. It attempts expensive variety, but can fall back when broke. "More expensive" is measured spending, not guaranteed stronger weapons for every hero.
- **C Expert:** unchanged `EXPERT`: existing affinity/element recipe search, commission/want responses, reservations/listing priorities, budget pricing and forecast-triggered donations. No tool/hone active package. It adapts weapon families through requests and recipe search; it does **not** reason about a unique role for every family or plan multi-day purchases optimally.
- **Siege Prep / Passive:** unchanged existing policies, used as controls.

The test-only repeat adapter follows the existing driver's basic day order and real commands. Its separate deterministic test checks identical replay and recipe use. It is not a new production bot. The others run through the existing driver, not a replacement combat simulator.

### First four sieges

Cells are **held / reached**, not survival. Forge health is the mean immediately after that siege's ordinary recovery, among runs reaching it. Death-before-siege is a failure in paired success comparisons.

| Policy | Day 5 | Day 10 | Day 15 | Day 20 | Health after sieges 1 / 2 / 3 / 4 |
|---|---:|---:|---:|---:|---|
| A Fire | 57/96 | 9/96 | 0/95 | 0/49 | 79.0 / 49.5 / 10.3 / 0.9 |
| A Frost | 96/96 | 91/96 | 45/96 | 5/96 | 93.8 / 86.4 / 71.1 / 43.9 |
| B Invest | 96/96 | 92/96 | 69/96 | 12/96 | 92.0 / 86.7 / 76.3 / 54.8 |
| C Expert | 96/96 | 96/96 | 96/96 | 92/96 | 96.1 / 95.4 / 91.7 / 85.1 |
| Siege Prep | 96/96 | 96/96 | 94/96 | 91/96 | 94.2 / 93.7 / 90.2 / 83.9 |
| Passive | 0/96 | 0/96 | — | — | 42.0 / 0 / — / — |

Day-20 surviving forges: A Fire **2/96**, A Frost **89/96**, B **95/96**, C **96/96**. Thus surviving is substantially easier than holding every siege. In particular, repeatedly losing later sieges need not immediately invalidate the player's familiar recipe.

| Policy | Median / mean survival days | Material gold by day 5 / 10 / 20* | Whole-run material gold / units | Forged / sold per run |
|---|---:|---:|---:|---:|
| A Fire | 20 / 17.6 | 337 / 769 / 1,613 | 1,420 / 164.5 | 88.5 / 25.2 |
| A Frost | 30 / 27.6 | 552 / 1,230 / 2,378 | 2,878 / 213.1 | 113.6 / 38.0 |
| B Invest | 30 / 29.7 | 1,175 / 2,322 / 3,912 | 5,142 / 107.0 | 82.1 / 51.2 |
| C Expert | 40 / 41.6 | 701 / 2,589 / 5,071 | 9,328 / 265.4 | 171.6 / 86.2 |
| Siege Prep | 40 / 40.3 | 601 / 2,256 / 4,477 | 7,404 / 268.2 | 168.5 / 67.8 |

*Means among runs reaching that day; A Fire's day-20 cell is strongly survivor-selected. Materials exclude starting stock, gifts, unlock fees, tools and forgone sale value. Whole-run totals have unequal lifetimes and are not efficiency estimates. "Sold" includes completed commissions, as in the existing harness.*

**Does C beat A?** Yes over four sieges; barely in the first two against Frost. C minus A Frost held-rate differences are **0, +5.2, +53.1, +90.6 percentage points**. Approximate paired 95% intervals: siege 2 **+0.7 to +9.7**, siege 3 **+43.1 to +63.2**, siege 4 **+84.8 to +96.5**. At siege 4 C alone held on 87 seeds, A Frost alone on zero. C versus B at siege 4 is **+83.3 points**, approximate interval **+75.9 to +90.8**. These are paired normal approximations, not exact small-cell or simultaneous intervals; identical first-siege records do not prove equal population performance.

**Does more elaborate planning earn a visible extra reward?** C versus the simpler Siege Prep policy holds only one additional fourth siege net (C-only 3, Prep-only 2; +1.0 points, paired SE 2.3). This sample does not establish an advantage for the full Expert bundle over the simpler counter-production/donation policy. It strongly supports siege-directed supply over indiscriminate expensive variety.

Native visitor/relic habits: B holds **93/90/70/21** of 96 sieges and averages **29.8** days; C holds **96/96/96/94** and averages **44.0** days. The controlled comparison's central result survives. These policies choose different visitor/relic habits, so the native difference is not a pure material-response effect.

### Why the curve feels easy, and what actually contributes

Classic is `C/battle/Battle` + `Power`, **not** `guild/SiegeFight` or the interaction engine. The outcome is deterministic once that evening's state is known:

`defense = sum(top three healthy heroes' defensePower) + militia + armory`

`raid = (32 + 6.5 × day + 0.65 × pressure) × 2 × season × warlord × trait`

`forge damage = round(24 + 50 × (raid / max(1, defense) − 1)), clamped 0..60`

There is no separate siege outcome roll. A hold can still damage the forge. At defense approximately **1.923 × raid**, damage reaches zero. Healthy replacements automatically fill the strongest three slots; the player need not form a balanced party.

| Contribution | Measured evidence | Interpretation |
|---|---|---|
| Autonomous champions | C's actual first-siege defense: **236.3 champions + 30 militia + 4.6 watch = 270.9** vs raid **167.5**. By siege 4: **347.1 + 29.2 + 15.0 = 391.1** vs **317.4**. | Champions supply about 87–89% of C's defense. Twelve residents provide an automatic selection buffer; not all twelve need suitable equipment. Passive heroes alone do not solve the wall: Passive loses both sieges and falls on day 10. |
| Weapons, quality, family | At first-siege-morning frozen C forecasts, removing weapons from the same selected heroes removes **172.0 defense**; removing only direct `quality/5` power removes **53.7**; neutralizing class fit and family weight removes **52.9**. | Player blades matter greatly. But these are overlapping effects, not additive shares. Quality ablation retains quality-earned affixes and so is not its full value. Ordinary family power/fit matters; spear reach, dagger extra attacks, staff healing and metal triggers do not exist in Classic. |
| Elements | Frozen C snapshot: removing elements loses **27.5 defense** before siege 1 and **51.1** before siege 4. Fire-repeat's element instead costs **22.7 defense** before siege 1 relative to neutral. Frost-repeat gains **29.4**. | Ashclaw and Embermaw both weak to Frost/resist Fire. Cheap Frost is already an excellent answer without reading a new forecast. These snapshot deltas exclude the element's earlier expedition/suppression effects. |
| Militia and watch | Actual first-siege militia averages **30** even for Passive; patrols rapidly reach the cap. C watch averages **4.6**, rising to **15.0** by siege 4; ordinary A/B do not donate. | Militia is largely automatic background strength. Watch helps, especially under Many Breaches, but is not the main cause of early C wins. Current anonymous armory accepts any family; it creates little recipe identity. |
| Enemy progression | C mean raids **167.5 → 206.2 → 258.5 → 317.4**; A Frost **165.2 → 207.9 → 265.2 → 323.2**. Default warlord raid multiplier is 1.0. | The challenge arrives mostly as the day term outruns growth. Better equipment also suppresses faction pressure through autonomous expeditions, easing subsequent raids. Later difficulty does not prove that the opening asks interesting questions. |
| Siege traits | Siege 1 is always plain; from siege 2 trait chance is 70%. Removing all traits changes C mean life **41.6 → 41.8** and siege-4 holds **92 → 89**; Fire **17.6 → 17.2**. | Existing variation is not uniformly hostile. Hungry Horde/Night Assault reduce raid power. At C siege-2 mornings, traits on average remove **10.3 raid** but **5.4 defense**. Full-run removal also changes the encounter-stream draw sequence, so use this as sensitivity, not a clean trait-only causal estimate. |
| Repair and recovery | Setting base daily recovery from 2 to 0 changes C mean life **41.6 → 35.5**, Fire **17.6 → 16.0**. C's day-20 health falls **85.1 → 66.7**, while fourth holds remain **92/96**. | Repair masks attrition more than it determines holds. This variant leaves recovery blessings active: C still repairs 7.7 total/run, not zero. No purchasable gold-repair action exists. Honing uses energy and can repair owned shop stock, not command a hero's equipment. |
| Legacy | All upgrade levels maxed: C mean life **50.8**, first-siege health **157.2**, siege-4 holds **95/96**; Fire mean life **25.2**, siege-4 holds still **0/96**. | Walls' +60 starting health extends losses; mastery/resources/reputation help supply. Maxing all tracks is a bundle, not evidence that each helps equally. Knowledge, lineages and a veteran Legend Board are not added by this max-level control. |
| Repeated cheap production | Fire forges 88.5/sells 25.2 per run; Frost 113.6/38.0. B spends **1,092** more material gold than Frost by day 10 for only **one** extra second siege held in aggregate. | Cheap repeat is an attractive opening shortcut. It is not immortal, and Fire is substantially worse than Frost. Extra production rapidly becomes surplus instead of a fresh choice. |

Frozen forecasts are measured at the **start of siege morning**, before that day's customers, activities, wounds and siege XP. They hold the selected heroes fixed. They are sensitivity probes, not exact causal attribution of the evening's recorded outcome. Actual contribution totals come separately from saved siege replay rounds/events. Do not add the sensitivity deltas together.

**Simulator limits:** C prioritizes requests before siege forges, reads the leading faction in its recipe search rather than always the committed besieger, and reacts to traits only indirectly through forecast donations. It can inspect exact state and score catalog affinities without a human learning them. It has no human attention cost, enjoyment model, deliberate saving plan, fatigue, or inference from UI. Common seeds do not keep all later draws aligned once strategies change. None of these scripted results overrides the owner's playtest.

## 3. Why runs repeat: ranked by first-ten-day impact

| Rank | Cause and category | Actual source/evidence | Effect on decisions |
|---|---|---|---|
| 1 | **Dominant opening answer**: Frost plus broadly fitting swords | Two of three factions share the same weak/resisted elements. Sword fits Guardian, Duelist and Battlemage at 1.2 and has 1.1 wall weight. Actual catalog off-family fit is 0.8 (GDD's 0.85 is stale). | A cheap unlimited augment serves multiple foes. Automatic top-three selection lets good sword users carry the wall; the smith need not supply every class. |
| 2 | **Variation too weak to matter early**: one scalar target with considerable headroom | First siege has no trait. First two Frost-repeat holds are 100%/94.8%; costly variety is 100%/95.8%. | Early "success" teaches that repeating works. Later linear pressure asks for more power before it asks for a different plan. |
| 3 | **Variation hidden by access and cost** | Grave/Sun/Void and high metals wait for the road; even after opening, Sun costs 210 and Starsteel 300. Hollowbound's Sun counter is ordinarily unavailable before siege 1. | The palette is initially Fire/Frost/Verdant plus one expensive Storm unit. The one counter that differs most is gated. New metal quality is real, but its elaborate Guild trigger never becomes a Classic tactical rule. |
| 4 | **Starting situations lack consequential variation** | Across 96 opening towns: **one identical starting kit**, all initially unarmed, every class present every run; 74 different class-count compositions. Seasons: 41 Lean, 36 Calm, 19 Restless, changing multipliers by about 10%. Classic has no Guild opening packages. | Names, traits and surplus classes vary, but the same kit equips broadly similar empty hands. Randomness in the roster rarely creates a different procurement problem. |
| 5 | **Demand variety exists but repeats its generation rules** | `Market.wantOf` retains an existing want's family; fallback is the class's first preferred family. Commission family draws from that class's small preferred list; ordinary quality asks are 35/50, generated at 30%/day with two open slots. Elements favor taste or the looming weakness. | A want often means "a stronger version of what I already ask for". Not every commission is a sword, but familiar class/faction combinations keep recurring. Fulfillment is not guaranteed wall attendance. |
| 6 | **Existing visitor/relic variation has weak leverage or eligibility barriers** | Ten morning observations × 96 controlled C runs contain 143 Last Crates, 115 festivals, 108 masters, but only 12 wall-weapon visitors and 6 cracked heirlooms; these are observed offers, not weighted-selection probabilities. Four Classic workshop relics already support different habits. | Interesting identity-building visitors need suitable events/stock/deaths. Ledger rewards family rotation, Crucible salvage, Bellows overwork and Seal premium sales, but they alter production/economy without changing the wall's central question. No more visitor names are needed. |
| 7 | **Rewards/unlock milestones converge rather than branch** | Everyone reaches the same stage-2 catalog after siege 2; Classic has no charter/opening/preparation choices. Blessings temporarily change quality, output, income, repair or hero power. Warlord holds can award early Heartfire; otherwise won siege 4 onward does. | Rewards mostly make the existing routine stronger. Full-catalog access does not itself create a run identity. Deeds/signatures offer real later goals but cannot rescue the same opening decision by themselves. |

This is neither an empty content catalog nor a lack of random generators. The largest deficit is **different conditions failing to invalidate the same adequate response**. Wave 1 made that response easier to understand; repeating more presentation work will not make its trade-off real.

## 4. One proposed slice: supply the town's answer, days 1–10

**Design proposal only; none of this is implemented or represented by the measurements.** Preserve five-day sieges, fully automatic scalar Classic combat, autonomous heroes, the existing gold/material/energy economy, and the present unlock schedule. No player-controlled fighting, daily task requirement, new currency or expanded material/weapon catalog.

Three approaches considered: (a) raise all raid stats—would move the failure date without changing the answer; (b) add more recipes/events—would enlarge a catalog whose differences already fail to reach Classic combat; (c) make the existing siege, customers and material rewards form one supply problem. Recommend **(c)**.

The slice combines four small rules around one decision: **sell or preserve a few counter-weapons for autonomous customers, or sacrifice sale income to provision the watch**. A seed's patrons and coming attack determine which route is attractive. There is no party assignment and no daily readiness checklist.

### M1. A siege preparation creates a bounded, counterable cost

Reuse the existing Shield Wall and Gravecaller preparation concepts, with **explicit Classic scalar rules**, not their Guild combat descriptions. Existing Long Assault/Many Breaches/Night Assault continue to determine wear/watch exposure. The first siege gets a visible preparation; the faction/preparation is committed and shown at run start, with the next one shown on morning 6. This is a proposed earlier commitment/reveal, not a description of current Classic warnings.

Prototype rule: preserve the current raid/defense calculation and 60-damage cap. Separately compute a preparation coverage fraction, capped at 1:

`coverage = (eligible champions' positive weapon-added defense + effective watch defense) / (0.25 × raid)`

For Shield Wall, a **Spear OR Grave** weapon qualifies. For Gravecaller, **Bow OR Storm** qualifies. A weapon matching both counts once; an unarmed hero's base power never qualifies. Use actual healthy selected champions, current condition and the current trait's watch multiplier. Add up to **32 forge damage × (1 − coverage)** for the unopposed preparation, within the existing overall 60 cap. Coverage is a derived property, not stored currency or extra additive defense. A high-power sword's general defense still matters, but cannot cancel every special cost. Existing held/breached verdict remains based on the general battle; a costly hold reports the unopposed preparation separately. These coefficients are an initial experiment, not approved tuning.

One attainable partial response reduces the cost; no exact recipe, top-quality roll or perfect coverage is required to survive the opening. Physical Spear/Bow routes remain cheap before Grave Dust is unlocked. No early enemy may demand only Sun, Grave or a fine metal. Watch-first can deal with Many Breaches cheaply; Night Assault explicitly makes that investment insufficient for this siege, prompting a small customer-equipment pivot rather than a universal watch solution.

### M2. Use the existing order system to make the equipment route attainable

At most one existing `SIEGE_PREP` order occupies an existing commission slot per siege cycle. Offer it early enough for two normal forging mornings, for a living plausible defender **who actually benefits**. The named buyer requests either a fitting family counter or an available element counter; the player can decline and take the watch/shelf route. No new guaranteed sale channel on top of commissions.

The order must check improvement against current equipment using the **same Classic valuation including preparation** used by autonomous equip decisions. A delivered but inferior blade remains honestly reported as spare; do not force equipment or attendance. If the buyer falls or a better defender replaces them, the outcome changes and the player can use the watch as a fallback. Reuse existing commission delivery before visitors' purchases, storage priority, deadlines, want budgets, the Forge purpose, and Wave 1 receipts. This changes generation/valuation, not the smith's control over heroes.

### M3. Progression rewards make one new material usable, rather than only visible

Keep the caravan's 60-gold acceleration versus free wait, all stage dates, Moonsteel after siege two, normal daily prices/stock and paid unlock semantics. When a preparation order settles **after** a road stage is open, offer its existing reward as **gold OR one explicitly named, currently unlocked ingredient** useful to the next response. The reward preview must name the item before acceptance; taking the ingredient replaces the gold, not supplements it. No parcel/voucher currency, random inaccessible reward, extra claim chore or bypass of road stages.

For example, after day-8 access a qualifying order can pay one Sun Ash instead of 140 gold: a purposeful route to the expensive counter, at the cost of liquidity. Do not offer a locked Sun Ash payout as an early-siege promise. Stage 1 is not silently moved earlier; a day-6 paid opening can make the same reward available earlier at the access fee's opportunity cost. Moonsteel rewards cannot appear before stage 2.

### M4. Starting patrons make different production economically sensible

Keep all five classes represented and the existing population size. Seed two established customers with modest **existing-catalog** worn weapons instead of all twelve starting unarmed. Generate their class/gear together, holding total starting equipment value and power within a narrow shared band. Show their actual owned blades, wants and coming preparation using the current town/board surfaces. No named scripted hero is required.

The same two Iron/Frost swords should therefore be easy opening sales in one town and poor upgrades in another. Existing recipe stock and relic choices remain; do not add a separate starting-management screen or a set of new bonuses. This reuses gear, condition, autonomous valuation and demand, rather than translating Guild's party/opening controls into Classic.

### Decisions, risks and acceptance

| Mechanic | Player decision and real trade-off | Systems/code | Variety created | Likely dominant strategy / balance risk | Testable success criterion |
|---|---|---|---|---|---|
| M1: counterable preparation | Sacrifice saleable weapons to watch, or pay/time a few suitable customer upgrades; some preparation damage can be accepted to save gold | `Battle.outlook/resolveSiegeIfDue`, `Power`, `Depth`/preparation content, nested `BalanceConfig`, Classic forecast/receipt | Shield versus rear threat values Spear/Grave versus Bow/Storm; existing watch/wear traits change route value | Donate cheapest power forever; counter with one universal high-quality blade; additive damage becomes a hidden tax. Cap/count once, show source, test Night/watch interaction and low-roll partial answers | Across paired early seeds, either response avoids at least 15 cumulative day-10 forge damage versus cheap repeat on relevant starts; unprepared median day-10 health 40–70, deliberate preparation 70–95; at least 90% of legal novice starts remain alive through day 5 |
| M2: consequential orders | Take one named job and commit scarce stock/energy, or keep commission slot/cash/shelf flexibility | `Market.commissionSituations/maybeOfferCommission/giveAndEquip`, `Commissions`, `Want`, Forge purpose, saved receipts | Buyer class, owned gear and current danger change family/element choice | Auto-accept every prep order; guarantee everyone equips. Risk: unavailable buyer or sword-favoring equip valuation invalidates an intended counter | On unchanged healthy recipients, at least 85% of qualifying deliveries are equipped by the siege; no forced attendance; unfit/obsolete/dead cases retain truthful outcomes; order consumes the existing slot and cannot duplicate payment |
| M3: one accessible ingredient reward | Take gold for tomorrow's ordinary trade or take a specific expensive ingredient now | Commission reward model/settlement, `DeepRoad`, supplier availability, reward preview, `SaveCodec` if model expands | A saved fine metal/Sun/Grave unit becomes a real experiment; gold-first/watch and ingredient-first/specialist diverge | Always choose max retail-value material then salvage/resell; free reward farming. Risk: material payouts eclipse all trade | One payout only, fair disclosed replacement value, road gate enforced at offer and settlement; both reward choices used by viable policies; stage dates unchanged under pay/pass/ignore/restart tests |
| M4: established patrons | Serve the town's actual upgrade gaps rather than mass-produce the familiar starter sword | `GameEngine.newRun`, `Heroes.generate`, existing `Weapon` creation/ownership, Market valuation; separate deterministic stream discipline | Starting gear/class pairings change who can afford/use which family and how quickly a counter reaches the top three | Always serve the richest sword user; universally superior opening. Risk: inaccessible or secretly stronger starts | Every start has a cheap feasible counter and affordable sellable recipe; equal-value opening bands; at least two policy routes win in different cells; no starting high-tier unlock leak; same seed/save yields identical patrons/gear |

These are **prototype gates**, not predictions. Replace the targets if owner playtests establish a better pressure band, before expanding beyond day 10. Policies should make different sacrifices: a watch-led run uses cheap throughput and loses sale income; an equipment-led run uses fewer targeted sales/orders, dear ingredients and exposure to autonomous attendance. Neither should beat the other across every opening/trait cell. Do not require every run to forge all six families.

### Three concrete first-ten-day runs under the proposal

1. **Many Breaches, poorly armed Guardians/Wardens, Shield Wall.** Day 1 shows cheap Spear demand and a watch-favoring threat; choose throughput over buying Silver. Days 2–4 sell Bronze/Ember Spears to fill gaps and donate surplus Iron weapons, deliberately giving up roughly three ordinary sales. Day 5's preparation is covered largely by the watch. On morning 6 the next threat is Gravecaller: keep a Ranger's Bow order instead of more identical donations. Wait for the day-8 train and take gold from the order to preserve liquidity. This is the low-cost supply route; its risk is a later Night Assault or weak watch funding.
2. **Night Assault, established sword customers, Gravecaller.** Starter Frost swords are small upgrades for the well-armed sword patrons; a Ranger still needs a Bow. Skip donations, take that named order, and spend the starting Stormglass on a Silver/Storm Bow or meet the cheaper Bow-family counter if cash is tight. Day 5 depends on a useful autonomous purchase, with some uncovered-preparation damage acceptable. Pay 60 on day 6 only if the next Shield Wall order justifies early Grave access; otherwise the fee loses two days of useful ordinary material spending. Taking Grave Dust instead of gold after opening lets the smith pair a Grave blade with a suitable existing sword user. This is the selective equipment route, with fewer crafts and more cash exposure.
3. **Long Assault, worn established Ranger/Battlemage gear, Shield Wall then Gravecaller.** Save the expensive starter Silver/Stormglass rather than making the same first sword. Use a cheap Spear-family order for the first preparation, accept a partial costly hold, and reserve later output for actual replacement demand. The smith cannot hone heroes' owned gear directly: offer fresh replacements through the shop and keep a watch reserve if buyers stay away. Wait for the train, choose a useful unlocked ingredient payout over gold for the second cycle, and use Tempering Ledger to alternate Spear, Bow and a demanded Staff only when those outputs have buyers. This route trades early integrity and liquid gold for later equipment quality; the ledger is useful, not a mandatory rotation chore.

These are plausible proposed decisions, not played transcripts or measured wins. The first two are the two strategy targets; the third demonstrates a risky recovery/equipment hybrid and must be tested before being called viable.

## 5. Three implementation tasks, in dependency order

1. **Implement M1's Classic preparation cost and shared valuation/receipt.** Start with Shield Wall and Gravecaller, known in time for the first siege, and two accessible answers each. Build the pure coverage calculation, expose exactly the same result in outlook and resolution, persist the recorded contribution/cost, and leave Guild combat unchanged. Add fixed-state/paired-policy tests. This is the first gameplay change: it changes what an adequate opening response means.
2. **Connect M2/M3 supply through existing orders and road milestones.** Generate at most one appropriate prep order within current slots, align autonomous equip valuation with the new preparation rule, and add the alternative material reward without a new currency/claim step. Extend the full day-1–15/pay/pass/ignore/reload matrix and app delivery/stock tests. Only after task 1 can "useful counter" be defined consistently.
3. **Introduce M4's modest starting gear, then calibrate and playtest the complete slice.** Verify equal-value start bands and cheap legal exits, stratify both strategy routes by opening/preparation/trait/legacy, and rerun human first-ten-day sessions. Tune preparation cost, accessible ingredient rewards and watch caps together; do not raise the whole raid ladder. Update GDD numeric tables, balance version, compatibility/save treatment, changelog and ledgers for the actual approved changes. Extend the horizon only after the opening decisions work.

No speculative task above has been implemented in this session.

## 6. Automated evidence and verification

The delivered artifacts are the two new test sources, this report, the compact [measurement summary](CLASSIC-GAMEPLAY-RESCUE-MEASUREMENTS.json), `tools/classic_rescue_summary.py`, and a PROGRESS entry. Full per-day, per-siege, per-run and forecast/selection CSVs are under ignored `core/build/classic-rescue/`; their SHA-256 hashes are in the summary. No bulky screenshots/assets are added.

Reproduce from the repository root in PowerShell:

```powershell
.\gradlew.bat :core:test --tests '*ClassicProgressionTest' --tests '*DeepRoadTest'
$env:BLACKSMITH_RESCUE_MEASURE = '1'
.\gradlew.bat :core:test --tests '*ClassicRescueMeasurementTest' --rerun-tasks
Remove-Item Env:BLACKSMITH_RESCUE_MEASURE
python tools/classic_rescue_summary.py
.\gradlew.bat :core:test
```

`BLACKSMITH_RESCUE_RUNS` optionally changes sample size; the evidence here uses its default 96. Normal `:core:test` does not run the large measurement sweep; it does run the repeat-control determinism test and all progression cases. The summary's confidence intervals pair by seed, and treat a missed siege as not held. Full-run and day tables distinguish reached, held and alive.

Executed verification:

- Focused `ClassicProgressionTest`/`DeepRoadTest` passed; the final full suite includes all five new progression cases and both harness entry points.
- Opt-in paired measurement command passed: 1,344 runs, all ending before day 80. CSV hashes match the compact summary. The Python summary generator ran successfully using only the standard library.
- `:core:test` freshly executed: **682 tests, zero failures/errors/skips**, build successful. The large evidence sweep is opt-in; its entry point returns without simulating in this normal run.
- `:app:testDebugUnitTest` and `:app:assembleDebug` succeeded as **UP-TO-DATE** in the combined verification command; no app source changed. The cached full app result had 320 passing tests. Then a fresh focused app run (`RunVarietyUiTest`, `ForgeBrowsingTest`, `ForgeShortcutTest`) executed **25 tests, zero failures/errors/skips** for Supplies road explanations and Forge inventory/browsing/shortcuts. This is not a fresh execution of all 320 app tests.
- Logs: `core/build/classic-rescue-measure.log`, `classic-rescue-verification.log`, `classic-rescue-app-focused.log`. No device test, physical-phone pass or APK installation was performed.

No `:core:scenarios` invocation, golden recording, or scenario-asset write is part of this workflow.

Future automatic acceptance must also establish:

- Road access, limited stock, purse and owned forge inventory remain distinct across saving and rejected/retried commands; no accelerated stage date is hidden in balance tuning.
- Classic uses its own truthful preparation rule and receipts; Guild-only Mark/Charge/Bleed/metal triggers are never claimed as Classic effects.
- Partial counter coverage gives monotonic benefit, counted once, with final damage bounded; unarmed hero power cannot masquerade as a weapon response. Forecast/resolution use the same settled state, with normal daytime uncertainty stated separately.
- Broad cheap supply and targeted equipment have situational wins, not just different average lifetimes. Use paired seeds and opening/trait cells; no one route dominates all sufficiently sampled cells. Record day-5/day-10 health, damage, ingredients, revenue forgone, useful deliveries and families actually wielded.
- At least two families are usefully sold/equipped or meaningfully donated in most successful first-ten-day runs; do not count unused crafts, forced rotation or six mandatory family quotas as variety.
- Reload/End Day retries duplicate neither order rewards nor donor value; road-locked items cannot become ordinary rewards. Available owned rare loot remains forgeable.
- Human verification: the owner can name why they changed one recipe and what it cost, deliberately choose between the two routes, and see that response in the siege receipt. Bot win rates cannot satisfy this gate.

## 7. Owner decisions needed (maximum three)

1. **Approve a bounded preparation cost from siege 1**, with watch supply and suited autonomous equipment as alternatives, earlier threat disclosure, and meaningful costly holds. The illustrative 25%-of-raid coverage denominator / 32 maximum cost need a prototype tuning decision; they are not a request to globally increase enemy stats.
2. **Approve one existing commission's gold-or-material reward and shared counter valuation**, retaining autonomous equipment/attendance and the exact current road schedule. This is a real reward/economy change, not a progression bug fix.
3. **Approve two modestly equipped starting customers**, within a common value/power band, to make different upgrade gaps matter. Keep every class represented and show their gear before the smith commits resources.

**First change after any verified progression repair: give the first Classic siege a visible, bounded preparation cost with both a cheap watch answer and a targeted weapon answer.** At this HEAD the access pipeline passed; the next gameplay implementation should address that opening decision directly.
