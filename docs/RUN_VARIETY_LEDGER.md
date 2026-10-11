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

## Open for the owner
1. Is the supplier's "less stock when nobody goes" a consequence or a tax? Bots leave it in most runs.
2. Rescue: ease it further, or accept that a weakened party makes it harder than the hunt?
3. Licensed Bad Ideas is under the band (30 % average). Tune the charter or leave it as the hard one?
4. The weapon sheet shows fame as "Renown" and deeds as "Deeds: 2 of 3". One of the two words could change.
5. A Heartfire forging has no confirm step: the outlook is shown inline before the Forge button. Add one?
6. Version: this is a 0.9.0-sized change. `versionName` is untouched and the changelog entry is under Unreleased.
