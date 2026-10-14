# Run variety and consequences: ledger

Source: the owner's prompt "distinct runs, consequential decisions, earned legendary weapons" (2026-10-11).
Base: `main` `5b7e1db` (0.8.0, rules 5, schema 6, content 5, balance 11). Work branch: `run-variety`. Not pushed.
Evidence and final figures: `docs/run_variety_evidence/REPORT.md` (one report; tables beside it).

## Baseline rechecked on this checkout
- Guild bots, 300 seeds, Town's Last Hope, new account: Synergy 81 % charter, Defence First 79 %, Adaptive 76 %, 0 %
  defeated for all three; first siege leaves 94 to 100 integrity; Novice 100 % supply runs at 99 % won. Reproduced
  (`run_variety_evidence/before_guildsim_300.txt`).
- Forge quality: 25 + 6 x core tier + 3 x augment tier + affinity + mastery/tool/blessing/catalyst/ledger + roll -13..13
  + 20 exceptional - 12 defect, clamped 1..100. Rarity is a threshold of quality (Legendary from 85). Confirmed in
  `Forge.kt`. A new account reaches Legendary with Starsteel + Sun Ash on a staff (84 before the roll) for 205 gold.
- Hone recomputed rarity from quality: an Epic of quality 79 became Legendary for 2 energy and one core. Confirmed.
- A rank's `enemyPercent` was added to a base of 165 to 250, so "a sixth stronger" was about a fifteenth. Confirmed.

## Decisions (defaults chosen without asking, as instructed)
| # | Decision |
|---|---|
| D1 | Rarity from quality stops at Epic. Legendary is a status with an origin: forged with Heartfire, or earned by deeds. One ladder, one field (`Weapon.legend`). |
| D2 | The scarce ingredient is Heartfire, a run-only count (not a 17th material: the count of 16 is LOCKED). It comes only from dangerous work. "Masterwork" stays the word for quality 85+. |
| D3 | A Heartfire forging is an Advanced forge with one Heartfire and 2 more energy. Below quality 70 it cannot break through. From 70 the chance is 30 % + 2 points per quality point, at most 75 %, +10 for a signature the journal knows, +10 reckless, -10 safe. A miss is still a weapon with one more property. |
| D4 | Deeds: a weapon collects renown from real records (charter leader felled 3, warlord at the wall 2, a rescue, a recovery or a deep hall 2, an elite slain in a classic run 1). At 3 it is Legendary (origin: deeds), +4 power, a history line, once. |
| D5 | Risk: Safe narrows the roll to 6 either way. Reckless adds a property slot and flaws 30 % of the time. Flaws keep their fight rules and no command removes one. |
| D6 | A signature the journal already knows is forged 75 % of the time when its recipe is exact (knowledge is control). |
| D7 | A guild run opens with three packages drawn once from its charter's list: a build direction, a member's defining trait, a relic, stock, a drawback. End Day waits for the choice. |
| D8 | Every guild siege has a preparation of its besieger, drawn when the besieger is committed (four days ahead) and stored. Six preparations, two per faction. Sabotage removes it. |
| D9 | Sabotage and the siege's opportunity are two-day jobs posted together and gone after the second morning: one party, one of them. |
| D10 | Supply runs stay free and safe. Their pay no longer grows with the day, and a road used in the last four days carries less (shown on the card). |
| D11 | Rank scaling multiplies. One helper feeds contracts, sieges and forecasts. |
| D12 | New rules apply to every forge and every newly posted contract and newly committed siege. A stored siege plan, a posted contract and a party on the road are played as stored. The opening draft needs a new run. Existing weapons keep rarity, stats, signature, title and history. |
| D13 | A legend returning from the Legend Board is at most Epic and has no legend origin: quality alone never makes a Legendary weapon, on any path. It can earn the status again by deeds. |

## Slices and status
Status words: done (tested), partial (what is missing is named), not done.

| Slice | Content | Status |
|---|---|---|
| S1 | Simulator instrumentation, "before" tables | done |
| S2 | Forge, rarity, Heartfire, deeds, hone bypass, rank scaling | done. Heartfire forging in a classic run is tested but not simulated (classic bots do not spend it). |
| S3 | Opening draft, build directions, pact relics | done: eight packages, seven directions, two pacts. Partial: only two packages have a bot that follows their plan. |
| S4 | Preparations, opportunities, supply, consequence chains, recovery costs | done: six preparations, the siege's two-day jobs, supplier mood, supply thinning, the nemesis on the wall, siege cause lines. Partial: three contract modifiers, none changes an objective. Rescue is still harder than a hunt. |
| S5 | App screens, migration (schema 7, rules 6, content 6, balance 12) | done in code and JVM tests. Partial on a device: see the report's "Not seen on a device". |
| S6 | Bots, tuning sweep, 1,000-seed validation, tests, emulator, transcripts | done. Four new policies, unseen seeds, three transcripts. |

## Gates
| Gate | Measure | Target | Result |
|---|---|---|---|
| G1 | Legendary forged without Heartfire, new rules | 0 | met: 0 in 13,000 guild runs and 1,200 classic runs, and by test |
| G2 | Legendary weapons per run to the first charter, competent new account | median 0 to 1, p90 reported | met: median 0 to 1, p90 1. Legend seeker median 1, p90 3 |
| G3 | Charter secured, competent policies, new account, Town's Last Hope, 1,000 seeds | 40 to 65 % | mean 52 %. Synergy 68 (over by 3), Adaptive 50, Defence first 38 (under by 2) |
| G4 | Supply farming | falls behind adaptive play on charter rate and end wealth | charter rate met (13 % against 50 %). End wealth not met: the supply farmer ends richer (2,465 gold against 529) because it sells much and spends little |
| G5 | Ignoring an announced preparation | worse siege result on paired seeds | met: 475 seeds against 11, 41.0 more forge damage |
| G6 | Each opening package | no package under half or over one and a half times the mean charter rate | met pooled (0.86 to 1.31). For Defence first alone, Paid in Blood is at 1.5 |
| G7 | Rescue and recovery | won at least as often as the hunt that caused them, for a competent policy | not met: rescue 59 to 89 %, hunt 94 to 98 % (rescue was 25 to 79 % before) |
| G8 | `:core:test`, `:app:testDebugUnitTest`, debug build | green | met: 581 and 217 tests, 0 failures, debug build |
| G9 | Emulator: new flows, font scale, process death | seen | partial: opening chooser, process death, the siege's jobs, font 1.3, the Heartfire row. A Heartfire forging, the wall's preparation card and a siege night's cause lines were not seen |

## Final numbers (balance 12)
Guild: `siegePercentBase` 190 (150), `siegePercentPerDay` 3.0 (5.0), `charterWarlordPercent` 110 (115), `nemesisPercent`
100 (110), rescue stages 60 and 55 percent with a 3-round hold (85 and 80, 4 rounds), recovery 85 (95), captive health
24 (16), sabotage two stages at 85 and 90. Forge: Safe roll spread 6, Reckless one slot and defect 0.30 (0.20),
Heartfire as in D3, renown as in D4, classic Heartfire from the third siege fought, when won.

## Open for the owner (first pass; 1, 2, 3 and 6 were answered, see the second pass below)
1. Is the supplier's "less stock when nobody goes" a consequence or a tax? Bots leave it in most runs.
2. Rescue: ease it further, or accept that a weakened party makes it harder than the hunt?
3. Licensed Bad Ideas is under the band (30 % average). Tune the charter or leave it as the hard one?
4. The weapon sheet shows fame as "Renown" and deeds as "Deeds: 2 of 3". One of the two words could change.
5. A Heartfire forging has no confirm step: the outlook is shown inline before the Forge button. Add one?
6. Version: this is a 0.9.0-sized change. `versionName` is untouched and the changelog entry is under Unreleased.

## Second pass (2026-10-11): the owner's rulings and the release gates
The owner answered three of the open questions and asked for the rest of the work. The gates below were written
before any number was changed. They replace G3, G4 and G7 above and add G10 to G14. Version 0.9.0 is set only if
every one of them is met. Seeds: tuning on 1 to 600 and 5001 to 5300 (the 100001 block of the first pass counts as seen and was not used).
Final figures on 200001 onward, which nothing was tuned on.

Rulings (owner):
| # | Ruling |
|---|---|
| R1 | The supplier never brings less because an optional job was left on the board. Bonus stock stays as a reward. Less stock stays only where the card of a job the smith chose says so (the supplier's cart lost). |
| R2 | Rescue is eased further. At comparable readiness it should be more reliable than the hunt that caused the loss. It keeps a cost and a risk, and it does not compound the setback. |
| R3 | The target version is 0.9.0. The changelog stays under Unreleased until the gates pass. |

Gates:
| Gate | Measure | Target |
|---|---|---|
| G3 | Charter secured by Synergy, Adaptive and Defence first, new account, 1,000 unseen seeds, each charter | each of the three inside 40 to 65 % under Town's Last Hope, one by one, no average. The same band under Licensed Bad Ideas and Honest Business |
| G4 | Supply farming | (a) the supply-only smith secures fewer charters than Adaptive on paired seeds, (b) a smith who farms supply runs and then spends the gold does not beat Adaptive on charters or on standing at day 40, (c) gold, stock value and spending are reported. More gold alone is not a failure (owner) |
| G5 | Reading the scouts | Adaptive still beats the smith who ignores them on paired seeds, by a wide margin |
| G7 | Rescue | (a) the same full-strength party wins the rescue at least as often as the hunt, over fixed fights, (b) in played runs each competent policy wins its rescues at least as often as its own dangerous one-day contracts, (c) a lost rescue takes no second member |
| G10 | Epic progression, new account, competent policies | E1: a member carries an Epic on the morning of day 5 in at most 25 % of runs. E2: the median day of a run's first Epic is between 6 and 12. E3: a member carries one on day 20 in at least 75 % of runs that get there. E4: a member carries a Rare or better on day 5 in at least 80 % of runs. Classic runs are reported beside it |
| G11 | Contract modifiers | at least three that change the objective, the party or the reward in kind, shown on the card, and a test in which one changes what a bot does |
| G12 | Opening packages | every one of the eight has a bot that follows its plan. Following the plan is compared with ignoring it on paired seeds. No package under half or over one and a half times the mean of its charter for the plan follower. Each drawback's cost is measured |
| G13 | Classic runs | Heartfire earned, spent and broken through is simulated, new and maxed account. A classic run has no opening, preparation or siege job (test). The classic golden state is unchanged apart from what the bot does not do |
| G14 | Device | a checklist with three columns (emulator, phone, blocked): Heartfire forging that breaks through and one that misses, the wall's preparation card, siege cause lines, supplier stock after a won and a lost cart, legend lines, process death, font 1.3, instrumented tests, device scripts |

Why the Epic target is what it is. A run to the charter has four sieges, on days 5, 10, 15 and 20, and five rarities.
Legendary is the end of that arc (met in the first pass: median 0 to 1 by the charter). Epic should be the step
before it: the reward of the middle of the run, bought with the first sieges' takings or brought back from dangerous
work. So the first siege is met in Rare (E4 keeps the early step that feels good), the first Epic comes between the
first and the third siege (E2), an Epic on day 5 stays possible but is luck or a costly bet (E1), and the charter
siege is fought with Epic in hand (E3).

### Second pass: decisions taken (defaults chosen without asking, as instructed)
| # | Decision |
|---|---|
| D14 | Only the fine metals hold Epic work (core tier 5 and 6: Starsteel, Moonsteel). In any other metal quality stops at Rare and Heartfire does not take. One ladder: Rare by quality, Epic by quality in fine metal, Legendary by Heartfire or deeds. It is a rule of the forge, so it holds in a classic run too. |
| D15 | ~~The supplier carries Starsteel from day 6 and Moonsteel from day 11.~~ Superseded 2026-10-14 by the deep road (rules 7, balance 16, owner playtest; DECISIONS "Owner playtest 2026-10-14"): the tier 4 and 5 materials after the first siege's caravan master or guild contract, Moonsteel after the second siege. Hunts, ordinary loot, the mine, the ore merchant and the seal bring only what the supplier carries by now. The deep hall, the starfall site, an elite slain and a contract paid in metal bring fine metal early. |
| D16 | The exceptional result keeps its 20 quality. Lowering it to 12 was tried and did not hold the target (58 to 68 % still carried an Epic on day 5). |
| D17 | The hone never lowers a rarity (an older Epic in silver stays Epic) and never promotes a lesser metal. |
| D18 | A rescue's stages are Safe, and a guard of the watch fills every empty place of the party and stands in front. Reason: every full party already won the rescue. It was lost by the one or two members left after the capture. |
| D19 | Hunts are 130 % of the day's strength (105), set the faction back 4 (6) and bring mid metal. A full party won 100 % of them before, which is not a danger. |
| D20 | Siege floor 186 (190). The watch's strength (`outerHealthPerPoint`) was tried at 1.5 and 2.0 and left at 0.5: it lifted every policy alike and took the forge's fall out of the game. |
| D21 | Licensed Bad Ideas: a Guardian at half fee and a Warden at the usual fee would sign on the first morning, levy 50 (80), two Silver, Paid in Blood among its openings. Militia was tried and does nothing. |
| D22 | No opening's drawback is militia. Overflowing Cup and Borrowed Fire cost 40 gold, Marked Card 50. |
| D23 | Four contract modifiers that change the decision, each drawn only for a contract it fits. |
| D24 | Classic Heartfire from the fourth siege won (third). With bots that spend it, the third gave an expert two breakthroughs by day 40 on tuning seeds. |
| D25 | Balance version 13. Rules 6 and content 6 are unreleased and stay. The content 6 fingerprint was re-pinned in place. |
| D26 | Synergy came out at 66 % on the final seeds, one over the band. No number was changed after the final seeds were read. A further round would have needed a third seed block and would likely have pushed Licensed Adaptive (42) under. |

### Second pass: gates, as measured on seeds 200001 onward
| Gate | Result |
|---|---|
| G1 Legendary from quality alone | met: 0 in every table |
| G2 Legendary a run to the charter | met: median 1, p90 1 for the three. Legend seeker median 1, p90 3 |
| G3 charter band, each policy, each charter | **not met by one cell**: Last Hope 66 / 60 / 47, Licensed 61 / 42 / 45, Honest 53 / 53 / 45. Synergy under Last Hope is 66 (62 to 68) |
| G4 supply farming | met: supply only 12 % against 60. The farmer who spends later 53 against 60, and 14 % fallen against 9 by day 40. Gold and stock reported |
| G5 reading the scouts | met: 573 seeds against 6 |
| G7 rescue | met: (a) full party rescue 100 %, hunt 93 to 94, read from played runs filtered to three members at health 80 or more (no separate fixed-fight harness). (b) rescue 96 to 98 %, the same policy's hunts 76 to 87 and sabotage 88 to 95. Defence first takes no one-day dangerous contract, so its sabotage is the comparison. (c) by test |
| G8 tests and build | met: 593 core, 218 app, debug build |
| G10 Epic progression | met: day 5 carried 5 to 7 %, median first day 7, day 20 carried 94 to 95 %, Rare on day 5 94 to 100 %. Classic: forged by day 5 in 16 to 22 % of runs (99 to 100 before) |
| G11 modifiers | met: four, with a test in which the card changes who a bot sends |
| G12 packages | **partly met**: all eight have a plan follower, all between 0.83 and 1.20 of their charter's mean, plan against no plan measured. Not met in substance: no drawback costs more than 5 points either way, and three plans do not clearly pay |
| G13 classic | met but for one clause: Heartfire simulated new and maxed, scope by test. **The classic golden state changed** (298 of 300 lines), because D14 and D15 apply to every run. With those numbers set back it is reproduced line for line |
| G14 device | **not met**: emulator only, no physical phone. Seen on the emulator: both Heartfire forgings, the lesser-metal warning, Supplies, the wall card, cause lines, both supplier moods, legend lines, process death, font 1.3. Not seen: three of the four new modifier cards. Instrumented tests 72 run, 9 failed (in screens this pass did not change, not run on the base commit). `scenarios.sh` left four checks unverified |

G6 (first pass, pooled package band) is replaced by G12. G9 (first pass, emulator) is replaced by G14. That leaves
twelve gates. Eight are met (G1, G2, G4, G5, G7, G8, G10, G11). Four are not fully met (G3, G12, G13, G14).

### Final numbers (balance 13), what changed from balance 12
`siegePercentBase` 186 (190). Hunt stage 130 % (105), suppression 4 (6), materials tier 3 to 4 (3 to 6). Rescue
stages Safe with `town_watch` (22 health, strike 4 to 6). `fineCoreTier` 5, `coreTierFromDay` 5 from day 6 and 6
from day 11. `classicSiegeHeartfireFrom` 4 (3). Licensed Bad Ideas gold -50 (-80), Silver 2, Guardian offered and
Warden also offered. Openings: overflow -40 gold, furnace -40 gold, marked -50 gold, none takes militia.
Modifiers: ambush (hold 5), hostage (protect, 2 militia), narrow (party of 2, 150 % gold), in_kind (no gold, one
tier 5 material).

### Open for the owner after the second pass
1. Synergy under Town's Last Hope is one point over the band. Accept, or widen the band to 40 to 67, or ask for
   another round on new seeds?
2. The packages' numeric drawbacks cost nothing a bot can measure. Make them bite (a standing cost: dearer hiring,
   a larger share for members, one contract fewer on the board), or accept that the mechanical cost on the card is
   the drawback?
3. Careful Books, Break It on Purpose and the Stormwell: following the card does not clearly beat ignoring it. Are
   the cards wrong or are the bots?
4. Militia is close to worthless at a guild's wall. Town's Last Hope still lists three more militia as an
   advantage, and the wall work and the held guard pay in it.
5. A classic expert ends day 40 with a median of two Legendary weapons, almost all by Heartfire. Enough, or too many?
6. The weapon sheet shows fame as "Renown" and deeds as "Deeds: 2 of 3" (open since the first pass).
7. A Heartfire forging has no confirm step (open since the first pass).
8. A rescue is won 96 to 98 % of the time and a lost one takes nobody. It keeps two days, a scarred captive and the
   deadline. Enough risk, or should a lost rescue cost something more (a wound that lasts, gold)?
9. A visiting merchant can still sell a fine-metal blade before the supplier carries the metal. Close it?
10. Reported by the deep-forging session, not acted on here: Ashclaw and Embermaw are both weak to frost and resist
    fire, and nothing is weak to fire, storm, grave or sap. Frost Bloom is the widest augment for that reason (the
    day 5 loans in the report are mostly frost).

### After this branch: what the deep-forging branch does with these numbers
The deep-forging session merged `run-variety` through `d71017a` into its branch `deep-forging-integration`,
which is now `main`. Its metal fight rules lift the charter rates, so on `main` the siege floor is 190 again (balance
14) and it reports its own G3 on its own seeds (600001 onward): Last Hope 63 / 60 / 50, Licensed 66 / 53 / 45,
Honest 54 / 57 / 52. Those figures are that session's, from `docs/DEEP_FORGING_LEDGER.md` on that branch. They were
not checked here.
