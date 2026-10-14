# 06 Gameplay fun, decision depth, pacing, content (2026-10-14)

Lens: what the player decides, what it changes, what they see of it, and what content a run shows. Evidence: the two
simulators on this tree (balance 14, rules 6, content 6), the ledgers, and `app/src/main/java/com/example/blacksmithproject/ui`.
Bots are not people: nothing below says the game is fun; it says where a decision cannot matter or cannot be seen.

## Summary (10 lines)
1. The price decision is a one-sided cliff: over the suggested price costs ten days of run; under it costs only gold. There is no band to play in, and the one-tap "List at suggested" is the dominant move.
2. A classic run ends because raid power climbs linearly while the champions' defense plateaus from siege 2: held 94 / 69 / 22 / 2 / 0 % at sieges 1 to 5. Days 11 to 25 are a foreseeable slide the shop cannot bend.
3. Signatures, the GDD's "primary fun", are nearly invisible: 18 of 24 need a catalyst (Advanced forge) and bots that forge Quick find 0 to 0.7 a run. A new account also never sees a Legendary in a classic run (Heartfire from the 4th siege won, which 2 to 15 % reach).
4. The guild run's deciding action is one: spoil the preparation (ignore it 3 %, read it 60 %). Three builds land 49 to 66 %, so there is no dominant build, but Delve, Escort, Recovery and the Opportunity jobs are taken by no competent bot.
5. Nine of eleven legacy tracks move a run by about a day or less; three (Family Savings, Anvil Lore, Homing Steel) are identical to "none" in every yardstick.
6. Per run a player sees 17 to 19 visitor mornings (nine scenes, two options never taken), five relic offers of 14 relics, 1.2 to 1.6 sieges of each of the two traits (both only "harder"), 1.3 modified contracts of 7 modifiers, one law of 3 at most, 4 preparations of 6.
7. About ten decisions a day; half have a consequence the day cards show by name (sale reasons, contract "What decided it", siege "What decided it"); forge risk, tools, relics and upgrades have none the player can point to.
8. Pacing: days 1 to 5 are the game's best band; days 6 to 10 add fine metal, a law, the second siege; days 11 to 20 add nothing new in a classic run and only repeats in a guild run; past 20 the guild has ranks and nothing else new.
9. Fifteen content proposals below, all in the places the revision opened, each with its validator rule and config home; none touches a LOCKED count.
10. Owner decisions: ten from the ledgers restated, with a recommendation and the number each turns on.

## Ranked findings
| # | Sev | Defect | Action | Evidence |
|---|---|---|---|---|
| 1 | high | Price is a cliff, not a decision: EXPENSIVE median 15 days, 12 sales a run, 66 % refusals "too expensive"; CHEAP and FAIR both median 25, CHEAP earns 28 % less gold | Make the band worth playing: a premium a regular pays (loyalty), or a "haggle" refusal that comes back next day at the lower price. (Folding the stepper is an owner decision, Q11: manual pricing is LOCKED) | `simulate` lines "Policy BALANCED_EXPENSIVE/CHEAP/FAIR" (reproduce below); `ui/detail/PriceEditor.kt:55-59`, `ui/ShopPanel.kt:283` |
| 2 | high | Classic run ends by arithmetic: champions' defense 187, 213, 217, 213 at sieges 1 to 4 while the raid is 175, 235, 296, 364; held 94/69/22/2 % | A lever the shop owns after day 10: the siege-trait and deed proposals (P9, P10, P13) give the wall a decision; or raise `recoveryCap` | `simulate` BALANCED_FAIR "sieges (n, held …)" row; `BalanceConfig.kt` raidPerDay |
| 3 | high | Signatures unseen: 18/24 need a catalyst (11 Binding Salt, 3 Rune, 2 Oil, 2 Ink), 6 need a temper; signatures first found 0.0 to 0.7 a run | Harness: an Advanced-forge classic policy (review 6.4 still open). Game: the door is cost, not information (the catalyst rung exists, `Signatures.kt:44`): 15 % base chance × 4 energy × a catalyst each try; Binding Salt (11 of 24, 15 gold, 2 a day) is the cheap door Quick play never opens | `crafting/Signatures.kt:70-` (tabulated below); `simulate` "signatures first found/run" |
| 4 | high | Legendary absent for a new classic account: Heartfire from the 4th siege won; siege 4 held 2 to 15 % | `legend.classicSiegeHeartfireFrom` 4 → a warlord siege won at any number (P13) | `BalanceConfig.kt` LegendConfig; `simulate` siege rows |
| 5 | high | Guild: sabotage is the one necessary action (IGNORE_COUNTER 3 % vs 60 %); Delve/Escort/Recovery/Opportunity 0 to 9 % of competent contracts | Preparations with counters that pay without spoiling (P7, P8); a modifier that makes a hunt reveal the next preparation (P5) | `guildsim` contract lines per policy |
| 6 | medium | Upgrades: Family Savings, Anvil Lore, Homing Steel identical to "none" in every column; 9/11 tracks at or under +1.1 days (only Walls +6.1 and Cellar +3.4 above) | Not a nerf: add the metric each is for (first premium sale, first signature, legends returned) to the claim screen and measure there | `simulate` "Upgrade impact" and "Second yardsticks" tables |
| 7 | medium | Visitor options never taken by any bot: `masters_afternoon/lesson` (60 gold for a clue rung), `blade_for_the_wall/patron` | Lesson: the rung that names the catalyst (ties to #3). Patron: pay in fine metal (in_kind) | DECISIONS "Who chooses what"; `--depth` dead content lines |
| 8 | medium | Both siege traits only make the siege harder (won 19–30 % and 28–37 % against 51–58 % plain, siege 1 included) and both answer with "stock/donate" | Two traits that change what counts (P9) | `--depth` "sieges by trait"; `content/Depth.kt:89-95` |
| 9 | medium | Action relics (Bellows, Crucible) fire 0.00 times for a bot that does not act; the Ledger fires 60 times a run by itself | Show the ready action on the forge/storage row, not only in the relic sheet | `--depth` "relic triggers"; `engine/Relics.kt:386-398` |
| 10 | medium | Guild visitors are not measured: guildsim prints no visitor table | Add the `--depth` table to guildsim before tuning the four guild scenes | `sim/GuildSim.kt` (no visitor counters) |
| 11 | medium | Forge result never says why a quality-80 Silver sword is Rare (fine-metal rule lives in Supplies and the Heartfire row) | One line on the result card: "Silver holds work up to Rare" | REPORT "Other limitations"; `ui/SuppliesSheet.kt:132` |
| 12 | low | "Deeds: 4 of 3" shown when renown passes the threshold | `deedsLine`: "Legendary by its deeds" once renown ≥ needed | `ui/detail/ItemDetailSheet.kt:175` |
| 13 | low | Warlords defeated 0.0 a run in every classic policy; warlord sieges 0.0 to 0.4 | Content exists (3 warlords, 3 kits) and is never beaten in a classic run: tie to P13 | `simulate` "warlords defeated/run" |
| 14 | low | Customer wants: 54 to 60 % lapse, 11 to 16 % answered by the family asked | Wants with a deadline and a reward, or fewer wants | `simulate` "wants: voiced/run" |
| 15 | low | Ledger Q9 (merchant's fine-metal blade) looks closed by config: `merchantCoreTierMax = 4` | Verify no other path; close the question | `config/BalanceConfig.kt` DepthConfig |

## Measurements
Classic, `./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy gdd --customers"` (seed 1, new account, 400-day horizon):

| Policy | Days p10/med/mean/p90/max | Forged/sold | Gold earned | Rarity C/U/R/E/L % | Conv. d1-5 / d6-10 / d11-20 / d21+ |
|---|---|---|---|---|---|
| BALANCED_FAIR | 20/25/24.6/30/35 | 120/41 | 3,628 | 12/41/43/3/0 | 46/26/17/14 |
| BALANCED_CHEAP | 20/25/25.9/30/35 | 123/43 | 2,626 | 12/41/44/4/0 | 57/21/15/16 |
| BALANCED_EXPENSIVE | 10/15/15.7/20/30 | 69/12 | 1,824 | 14/43/41/2/0 | 9/12/11/8 |
| SAFE_FAIR | 20/25/24.0/30/35 | 117/39 | 3,293 | 1/69/27/3/0 | 46/25/16/14 |
| RECKLESS_FAIR | 25/30/27.7/30/40 | 136/47 | 4,550 | 16/36/45/4/0 | 41/29/20/17 |
| SYNERGY | 25/30/30.9/35/40 | 120/54 | 5,027 | 2/26/60/12/0 | 40/31/23/19 |
| Maxed BALANCED_FAIR | 30/35/35.0/40/45 | 207/62 | 6,644 | 1/15/80/4/0 | 24/28/23/22 |

- Why it ends: BALANCED_FAIR sieges held 93.9 / 69.2 / 22.4 / 1.9 / 0.1 %; champions' defense 187, 213, 217, 213 against raid 175, 235, 296, 364. Every new-account run ends by day 40.
- Rarity by day: the simulator gives totals only. Day-5 Epic: 16 to 22 % of classic runs (REPORT §2, balance 13); guild 5 to 8 % on day 5, 88 to 91 % on day 20, first Epic median day 7 (`guildsim` "epic:" rows).
- Price: refusals "too expensive" 2.7 % (cheap), 20.6 % (fair), 65.7 % plus 10.7 % "overpriced" (expensive). Hero gold at day 5 is 71 to 90; the cheapest listed blade is unaffordable for 30 to 41 % of visitors (fair) and 68 % (expensive).
- Sightings a run (classic, `--depth`, 300 runs, `visitors=first`, new account; the maxed account in brackets): last crate 3.7 to 4.4 (5.5), festival 3.0 to 3.4 (3.9), crooked merchant 2.4 to 2.6 (2.9), wager 2.2 to 2.5 (2.9), master 1.9 to 2.0 (its cap), collector 1.8 to 1.9 (cap), blade for the wall 0.7 to 1.6 (2.3), debt repaid 0.5 to 0.9 (1.5), cracked heirloom 0.12 to 0.19 with restore blocked 69 to 97 %. Visitor mornings 17 to 19 (69 to 71 % of days). Relic offers 4.6 to 5.2; replaced 1.6 to 2.2. Siege traits: long assault and many breaches about 1.2 to 1.6 sieges each a run.
- Classic upgrade impact (BALANCED_FAIR, maxed one track): Stalwart Walls +6.1 days, Well-Stocked Cellar +3.4, Forge Mastery +1.1, Thrifty Hands +0.8, Known Name +0.5, Lucky Hammer +0.4, Caravan Ties +0.1, Family Savings / Anvil Lore / Homing Steel +0.0, Tireless Smith −0.5; all maxed +10.4.

Guild, `./gradlew :core:guildsim --args="--runs 300 --seed 1"` (Town's Last Hope, horizon 30, new account):

| Policy | Charter / fallen / neither % | Siege 1 held, damage | Siege 4 held / cost / breached, damage | Contract mix | Legendary mean (deed / Heartfire) |
|---|---|---|---|---|---|
| SYNERGY | 66 / 4 / 30 | 54 %, 3.6 | 25 / 16 / 58, 16.0 | hunt 36, sabotage 35, supply 16, escort 9 | 0.75 (0.73 / 0.02) |
| ADAPTIVE | 60 / 0 / 40 | 81 %, 1.1 | 19 / 18 / 63, 15.1 | supply 37, sabotage 35, hunt 14 | 0.73 (0.69 / 0.03) |
| SUPPLY_FARMER | 51 / 3 / 46 | 59 %, 7.1 | 16 / 15 / 69, 17.2 | supply 57, sabotage 20 | 0.61 |
| DEFENCE_FIRST | 49 / 4 / 47 | 79 %, 1.1 | 12 / 13 / 75, 17.9 | supply 60, sabotage 38 | 0.58 |
| LEGEND_SEEKER | 36 / 3 / 61 | 63 %, 5.0 | 9 / 11 / 80, 22.3 | supply 35, opportunity 25 | 1.38 (0.46 / 0.92) |
| GREEDY_DIVER | 19 / 59 / 22 | 34 %, 15.4 | 7 / 5 / 88, 31.1 | supply 28, delve 26 (won 32 %) | 1.58 |
| IGNORE_COUNTER | 3 / 18 / 78 | 26 %, 9.7 | 1 / 1 / 98, 32.8 | supply 70 | 0.13 |
| PASSIVE | 0 / 100 / 0 | 3 %, 37.4 | — | — | 0 |

- Dominant strategy: the action, not the build. Spoiling the preparation separates 3 % from 60 %; among those who do, three mixes (hunt-heavy, supply-heavy, wall-first) land 49 to 66 %. The day-20 siege is the wall for everyone (held 9 to 25 %); the charter is then won at day 25 or 30 (REPORT transcript 4).
- Modified contracts a 30-day run, ADAPTIVE: ambush 0.13, hostage 0.12, narrow 0.23, in kind 0.12, guarded 0.21, reinforced 0.20, tired 0.24 (1.25 in all; about 0.8 in 20 days). A player sees "Hold the ford" once in eight runs.
- Laws: from day 8 at 25 % a morning while none holds (`GuildConfig.kt` lawFirstDay, lawChance), 3 to 4 days each: one or two of the three laws a run, not measured by guildsim.
- Guild visitors: unmeasured (finding 10). Inference from their gates: Coward needs a captive (0.25 to 0.55 a run for competent play), Across the Counter a nemesis (a taken blade), Sword Complaint a Sentient weapon (0.4 to 1.4 forged a run), the Adjuster a loan from day 4.

## Decision audit
Paths under `app/src/main/java/com/example/blacksmithproject/ui/`. "Next day" = a card of the shop day or a morning line names the consequence.

| Decision | Made on | Consequence | Shown next day? | Where |
|---|---|---|---|---|
| Recipe (family, core, augment) | journal hints, affinity words, fight rules per ingredient, outlook (guild) | quality, element rule, rarity cap by metal | result card at once; rarity cap not explained (finding 11) | `ForgeUi.kt:39-89`, `ForgePanel.kt` |
| Temper (Safe/Balanced/Reckless) | three sentences with the spread and flaw share | spread, extra slot, 30 % flaws | result only; the sheet keeps "Method: … reckless risk" but nothing ties the flaw or the slot to it | `ForgeUi.kt:111-113`; `detail/ItemDetailSheet.kt:268` |
| Quick vs Advanced, catalyst | cost line; fight rule of the catalyst | signature eligibility (18/24), catalyst rule in fights | signature reveal when it fires; miss gives a clue rung | `ForgeUi.kt:29`, `crafting/Signatures.kt:30-35` |
| Heartfire | outlook lines, warning in lesser metal; no confirm | Legendary or Epic + property, Heartfire −1 | at once; history line | `ForgeUi.kt:94-130` |
| Price | suggested price named first; "they may still pass" | sale / refusal with a reason code | yes: VisitCard reason; hero sheet "Left without buying" | `detail/PriceEditor.kt:55-59,116`; `shopday/VisitCard.kt:121`; `detail/HeroDetailSheet.kt:125` |
| List / unlist | shelf count, suggested price | who looks | yes: visit cards, tally | `ShopPanel.kt:283-285` |
| Commission accept / decline | asks, reward, due, buyer | completion at End Day, small expiry penalty | yes: board status, Gazette | `CommissionBoard.kt:100-106` |
| Visitor answer | option text, cost, blocked reason | gold, materials, order, consequence chain | morning line; chain returns after the next siege | `EncounterSheet.kt:119-136`; `engine/EncounterCatalog.kt:208-351` |
| Relic choose / replace / later | name, rule, slots | run-long rule | Ledger: forge bonus line; Seal: Gazette; Bellows/Crucible: only when used | `EncounterSheet.kt:191`; `engine/Relics.kt:386-398` |
| Blessing | three cards | 5-day effect | energy/gold yes; Hunter's Edge, Runic Insight no visible line | `Dialogs.kt:387-405`; `shopday/DayCards.kt:343` |
| Buy material / tool | price, stock, "from day N", "holds work up to Rare" | stock; tool effect | signboard: one more served customer on the tally (the shop is at cap 78 to 87 % of days, so the seat is used), but no line says the signboard did it | `SuppliesSheet.kt:132-167`; `market/Market.kt:51` |
| Salvage / scrap / donate / hone | return text, armory room | materials; watch strength; +quality once | watch: in the siege forecast number only | `StorageSheet.kt:209-232`; `detail/ItemDetailSheet.kt:91-119` |
| End Day | contextual note (visitor open, offer waiting, siege tonight) | the whole day | yes, card by card; Tomorrow card gives one lead and its reason | `WorkshopScreen.kt:147,382,410`; `shopday/DayCards.kt:313-320` |
| Legacy purchase | description, cost | next era | no next-run preview (review 6.3) | `RunEndScreen.kt` |
| Guild: opening | build, pitch, plan, drawback, relic | run-long | day-5 loans follow or not; no card ties back to the plan | `OpeningChooser.kt:92-118` |
| Loan / recall | member class, forge hint, fight rules | the member fights with it | yes: contract card "What decided it", fight notes "Seen in a real fight" | `detail/ItemDetailSheet.kt:374-392`; `shopday/DayCards.kt:107-134`; `FightReport.kt:68` |
| Party, posture, contract | card, prep line, failure line, modifier, "Up to N", posture rules in numbers | fight, gold, captures, materials | yes: contract card | `ContractSheet.kt:83-121`; `GuildUi.kt:139-142` |
| Checkpoint (push / return) | secured, deeper, danger, enemies | second stage | yes | `GuildPanel.kt:81`; `GuildUi.kt:289-295` |
| Reserve defender | wall card, "They prepare", counters, spoiled | who stands | yes: siege card | `GuildPanel.kt:189-257` |
| Recruit / dismiss | fee, share, trait, blocked reason | roster | roster | `GuildPanel.kt:357-370` |
| Speciality, rank, retire | branch text; rank text; milestone | run-long | sheet | `detail/HeroDetailSheet.kt:244-253`; `GuildPanel.kt:114-117` |

Count: a classic day is about 8 to 12 decisions (2 to 5 forges, prices, a visitor, orders, End Day); 4 of them (list, price, order, visitor) are named on the next day's cards; the rest resolve at once or never visibly. A guild day adds 3 to 4 (loan, party, posture, reserve) and all four are named on the contract or siege card: the guild loop closes better than the shop loop.

## Pacing
| Band | New in the band | Where it goes flat |
|---|---|---|
| Days 1–5 | Classic: relic draft of 3, first visitor day 2, journal rungs, siege 1 (held 94 %, plain by rule), blessing. Guild: charter, opening of 3, two members, first loan, sabotage posted day 1, rival day 6 | Siege 1 is not a test (REPORT: "not yet a test"; held 54 to 81 %) |
| Days 6–10 | Starsteel day 6, first Epic median day 7, siege 2 with the first trait / preparation, relic offer, law from day 8, second recruit refresh | Classic: last crate / festival / merchant repeat on cooldown; masters and collector capped at 2 and spent |
| Days 11–20 | Moonsteel day 11. Classic: nothing new; siege 3 held 22 %, siege 4 held 2 %. Guild: specialities after 3 wins, bonds, nemesis if a blade is taken, Heartfire from starfall/delve, siege 4 = charter warlord (held 9 to 25 %) | Classic flat from day 11: the slide is known and the shop has no lever on it. Guild: preparations repeat (6 for 4 sieges), modifiers rare |
| Beyond 20 | Classic: median run already over (day 25). Guild: ranks (3), retirement, sieges every 5 days with the same 6 preparations, supply thinning, Legendary by deeds mean day 22 | Nothing new is drawn; laws repeat from 3; no second milestone |

## Content inventory (what exists, what a run shows)
| Content | Count | Seen in a typical 20-day run |
|---|---|---|
| Materials | 16 (6 cores, 6 augments, 4 catalysts) | all 16 buyable by day 11; catalysts unused by Quick play |
| Families / classes / factions | 6 / 5 / 3 | all |
| Affixes / flaws | 12 / 6 | all 18 appear (classic 1.4 to 20 each a run) |
| Signatures | 24 (6 with a combat rule) | 0 to 0.7 a run (finding 3) |
| World events | 23 pooled + 2 generational rules (= GDD 25, `WorldEventReachabilityTest.kt:17`) | rumours 3.8, 3 moved to visitors |
| Visitors | 9 classic (8 drawn, 1 follow-up) + 4 guild = 13 | 17 to 23 mornings; cracked heirloom 0.1 to 0.2; guild four unmeasured |
| Relics | 14 (4 workshop, 8 party, 2 pacts) | 5 offers, 3 held; spec cap was 12 |
| Siege traits | 2 | 1.3 to 2.0 sieges each |
| Preparations | 6 (2 a faction) | 4 of 6 |
| Charters / openings | 3 / 8 (7 builds) | 1 / 1 of 3 offered |
| Missions / archetypes / modifiers | 10 / 9 / 7 | 13 to 14 contracts; 0.8 modified; Delve, Recovery, Opportunity 0 to 5 % |
| Laws / ranks / specialities / bonds | 3 / 3 / 6 / 2 | 1 law; ranks after day 20; 1 to 2 branches |
| Guild traits | 12 (6 behaviour, 6 hook) | 2 to 4 |
| Enemy units | 14 faction (5 Ashclaw, 5 Hollowbound, 4 Embermaw, warlords included); 18 with the helper, cart, watch and captive | one faction's 5 per siege; warlords never beaten in classic |
| Class kits / interactions | 5 / 7 | all kits; 2 to 3 paths witnessed |
| Blessings / upgrade tracks / tools | 8 / 11 / 4 | 2 blessings; 1 to 2 tools |

## Content proposals (in the opened places; counts LOCKED untouched)
Each: rule in one line; interaction; where it goes. Validator rules from `content/GuildContent.kt:209-266`: a charter needs ≥3 openings naming it (:246), an opening a drawback or a flawed weapon (:244), a preparation ≥2 counters and a `sabotage=true` stage (:249-250), a new non-supply mission a fitting modifier (:257). New numbers go in a nested group (`GuildConfig`, `DepthConfig`, `LegendConfig`), never the flat constructor.

| # | Place | Proposal | Rule | Interaction | Goes in |
|---|---|---|---|---|---|
| P1 | Charter | Quartermaster's Writ | Ranger + Guardian; supply runs pay one limited augment instead of three common units; the forge keeps 70 % of contract gold | Gives the 37 to 60 % of contracts that are supply runs a reason; Stormglass/Grave Dust get a route | `GuildContent.charters` (:428) + `CharterDef.supplyPayment: MaterialDraw?`; name it in openings overflow, plain_oath, careful_books |
| P2 | Charter | Oath of the Wall | Guardian + Warden; wall work mends 10 and the three places are members by default; contracts stay on the board a day less (`offerLifeDelta` −1 via a charter field) | A defence-first charter that is not Town's Last Hope; tests finding 5's second half | `charters` + `CharterDef.offerLifeDelta`; openings blood_price, plain_oath, salvage |
| P3 | Opening | Boiling Point | Ember Resin 2, Frost Bloom 2, Dragon Oil 1; Battlemage `second_wind`; relic P6; drawback forge −10 | Steam needs fire and cold in two hands: the only build using Ember Resin (finding: no niche) | `openings` (:492); charters licensed, honest |
| P4 | Opening | Cold Snap | Frost Bloom 3 and a Heavy iron axe (quality 45); Guardian `protective`; relic P4b; drawback: the axe is Heavy | Shatter path (`affix_heavy_shatter`) and Moonsteel from day 11 | `openings` + `StartWeaponDef(AXE, IRON, FROST_BLOOM, HEAVY, 45)` |
| P4b | Relic (party) | Icebound Ledger | When an ally's hit shatters Chill, the weakest ally gains 3 Guard, once a round | The relic P4 needs; makes the Heavy flaw a build piece as Brittle is for the Bell | `CombatContent.relicEffects` |
| P5 | Modifier | Scout's word | A hunt won under it reveals the next siege's preparation two days earlier (`siegeCommitDays` + 2) | Makes a hunt an answer to the preparation without spoiling it (finding 5) | `modifiers` (:552) + `MissionModifierDef.revealDays`; `GuildConfig.scoutRevealDays` |
| P6 | Relic (party) | Kettle Drum | When an ally gains Steam, every ally gains 1 Regeneration, once a round | Rewards the steam interaction the notebook already teaches | `CombatContent.relicEffects` (:361) + `Depth.relics` derives it |
| P6b | Relic (backlog) | Emergency Teapot (spec 9.2) | The first member downed on a contract stands back up at `downedHealth`, once a mission; never a dead one | Makes a Reckless posture survivable once; answers "lost rescue costs nothing" from the other side | `relicEffects`; `GuildConfig.teapotHealth` |
| P7 | Preparation (Ashclaw) | Hounds loosed | `addUnit = ashclaw_hound` (P12); counters: Iron Guard at the start, a Reinforced roll, Chill on the first round | A third Ashclaw plan so the two known ones repeat less | `preparations` (:531), `sabotage = spoil(...)` with the hound in the roster |
| P8 | Preparation (Hollowbound) | The Pale Knight rides | `addUnit = pale_knight`: two elites on the wall; counters: Giant Slayer, Sun, kill it inside two rounds | Giant Slayer gets a siege where it matters | `preparations` |
| P9 | Siege trait (classic too) | Hungry Horde | raid ×0.85, `conditionFloorFactor` 0.4: worn weapons count little; counsel: hone and fresh blades | The first trait that is not simply harder; makes hone/salvage a siege decision | `Depth.siegeTraits` (:89) |
| P10 | Siege trait | Night Assault | `watchMultiplier` 0, raid ×0.95; counsel: the three champions' weapons decide alone | Donation stops being the universal answer | `Depth.siegeTraits` |
| P11 | Visitor (guild, spec 11.3) | A Cult Wants Your Defective Blades | Sell up to 3 flawed blades for 40 gold each and the next preparation is announced now; or refuse and a sabotage is posted today | Uses flaws (Reckless 30 %) and the preparation chain | `GuildContent.encounters` (:301), `guild/GuildVisitors`; `GuildConfig.cultGoldPerBlade` |
| P12 | Enemy unit | Ashclaw hound | health 10, strike 3–4, acts before anybody once a fight (as `impatient`), weak to frost | Feeds P7 and a hunt roster variant | `CombatContent.units` (:391), `siegeGrunts` |
| P13 | Deed / classic Heartfire | Warlord's bane | A classic warlord siege won grants Heartfire whatever its number; a blade that stood on the wall for three held sieges earns 1 renown | Gives a classic account a Legendary path before day 25 (findings 4, 13) | `LegendConfig.classicWarlordHeartfire`, `renownWall` |
| P14 | Law | Thaw | For 3 days Chill cannot be put on anybody (party and enemy start with `CHILL` cleared each round) | Frost is the widest augment (deep-forging ledger); a law that forces the other five for three days | `laws` (:461) + `WorldLawDef.blockStat: Stat?` |
| P15 | Visitor (classic) | The Patron's Metal | The `blade_for_the_wall/patron` option pays in one fine-metal unit (Starsteel after day 6) instead of gold | Revives the never-taken option (finding 7) | `engine/EncounterCatalog.kt:227`; `DepthConfig.patronMetalFromDay` |

## Fun hypotheses for the playtest (each with its sign)
| H | Hypothesis | Measurable sign |
|---|---|---|
| H1 | Price is felt as a decision | >30 % of listings not at the suggested price by day 5, and "too expensive" refusals fall from day 1 to day 5. If every listing is the one tap, see Q11 |
| H2 | Day 5 is the first real fight | Player names the preparation or trait and what they did about it; damage 1 to 8 prepared against 15 to 37 neglected |
| H3 | Reading the preparation is the guild's fun | Sabotage chosen >60 % of the times it is posted; when not, the player names the counter they used |
| H4 | Discovery (pillar 1) lands | A first signature before day 10 in >30 % of runs; bots are at 0 to 0.7 a run, so <10 % means the catalyst gate blocks it |
| H5 | The loan is understood | A weapon's fight note turns "Suggested" → "Seen in a real fight" at least once a run and the player re-loans after reading a contract card |
| H6 | Days 11 to 20 feel flat | Distinct recipes forged in days 11–20 under half of days 1–10; no new listing kind |
| H7 | Openings are read for the plan, not the gold | Day-5 loans match the package plan (family, augment) in >50 % |
| H8 | Action relics are used | Bellows or Crucible used ≥2 times a run; bots never do |

## Owner decisions (ledger questions restated, with a recommendation)
| Q | Question | Recommendation | Evidence |
|---|---|---|---|
| 1 | Synergy 66 % one over the 40–65 band | Accept; widen to 40–67 and stop tuning on seeds (the 95 % interval 62–68 straddles 65) | REPORT §1; this run 66 (61–71) |
| 2 | Package drawbacks cost nothing measurable | Make the three gold drawbacks a standing cost the card can show (member share +5, one offer fewer); keep the weapon drawbacks | REPORT §4 `--drawbackPair` −4..+5 |
| 3 | Careful Books, Break It, Stormwell do not pay for the plan | Judge in playtest (H7); the bots do not prepare Bronze/Silver, so the lab undervalues two of the three | DEEP_FORGING_LEDGER "Still open" |
| 4 | Militia worthless at a guild wall | Pay militia rewards as a watch guard unit on the wall (`town_watch`, as rescue does) so the reward is seen in the fight | `outerHealthPerPoint` 0.5; REPORT "Other limitations" |
| 5 | Classic expert ends day 40 with two Legendary | Fine; the problem is the opposite for a new account (finding 4, P13) | this run: 0 warlords beaten, siege 4 held 2 % |
| 6 | "Renown" vs "Deeds: 2 of 3" | Keep "Renown"; the "4 of 3" case needs the fix in finding 12 | `ItemDetailSheet.kt:175` |
| 7 | Heartfire confirm step | Add one: it spends a scarce run resource and 2 energy, and a miss is permanent | `ForgeUi.kt:118-130` |
| 8 | Lost rescue costs nothing more | Keep; add P6b's limit as the risk the other way | REPORT §6 |
| 9 | Merchant's fine metal before the supplier | Appears closed by `merchantCoreTierMax = 4`; verify and close | finding 15 |
| 10 | Frost the universal answer; fire, storm, grave, sap no niche | Not a content number: a law (P14) and a charter (P1) give the others days; the faction what-if touches classic golden states, so leave weaknesses as they are this patch | DEEP_FORGING_LEDGER what-if table |
| 11 | Manual pricing is LOCKED (GDD round 7) but plays as one tap: keep the stepper, or make "List at suggested" the only control and keep manual pricing on the sheet? | Keep the stepper until H1 is measured; the trade-off is one fewer control against a LOCKED rule and finding 1's band, which only matters if the band exists | finding 1; `ShopPanel.kt:283` |

## Prior findings checked (review 2026-10-09 §6, lens items)
| Item | Status | Evidence |
|---|---|---|
| 6.1 demand view | fixed in part: a "Customer wants" board exists; wants still lapse 54–60 % | `CommissionBoard.kt:108-110`; `simulate` "wants" |
| 6.2 Signboard / Patronage visible | fixed: the signboard adds a seat; patronage is a stipend worth 0.4 % of gold, still imperceptible | `market/Market.kt:51,314`; `simulate` "guild patronage" |
| 6.3 uneven upgrades | open (finding 6) | upgrade impact table |
| 6.4 harness blind to Advanced / signatures | open for the classic `gdd` set (Quick only, `sim/Policies.kt:270,322,420`); guild bots use Advanced | finding 3 |
| 6.5 catalyst identity | fixed for fights (four rules); the clue ladder still names the catalyst kind only after a miss | `CombatContent.catalystEffects`; `Signatures.kt:44` |
| 6.6 continuity | fixed: weapon and hero sheets with history; returned legends at most Epic by D13 | `detail/ItemDetailSheet.kt` |
| 6.7 softlocks | measured: stuck days 0.00 %, drought days 3–5 % | `simulate` "recovery" rows |
UI/UX review cause 3 (pricing in a modal) is a stepper now with the suggested price named (`PriceEditor.kt:68-108`); cause 9's "defense vs raid" numbers are still drawn (`InfoPanels.kt:255`), outside this lens.

## Reproduce
```
./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy gdd --customers"   # classic tables, upgrade impact, yardsticks
./gradlew :core:simulate --args="--runs 300 --seed 1 --depth"                     # visitors, relics, siege traits (visitors=first)
./gradlew :core:guildsim --args="--runs 300 --seed 1"                             # fourteen guild policies, Town's Last Hope
```
Limits: bots, seed 1 only, no device, no player. Guild visitors and laws are not counted by any harness.
