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

## Slices and status
Status words: done (tested), partial (what is missing is named), not done.

| Slice | Content | Status |
|---|---|---|
| S1 | Simulator instrumentation, "before" tables | |
| S2 | Forge, rarity, Heartfire, deeds, hone bypass, rank scaling | |
| S3 | Opening draft, build directions, pact relics | |
| S4 | Preparations, opportunities, supply, consequence chains, recovery costs | |
| S5 | App screens, migration (schema 7, rules 6, content 6, balance 12) | |
| S6 | Bots, tuning sweep, 1,000-seed validation, tests, emulator, transcripts | |

## Gates
| Gate | Measure | Target | Result |
|---|---|---|---|
| G1 | Legendary forged without Heartfire, new rules | 0 | |
| G2 | Legendary weapons per run to the first charter, competent new account | median 0 to 1, p90 reported | |
| G3 | Charter secured, competent policies, new account, Town's Last Hope, 1,000 seeds | 40 to 65 % | |
| G4 | Supply farming | falls behind adaptive play on charter rate and end wealth | |
| G5 | Ignoring an announced preparation | worse siege result on paired seeds | |
| G6 | Each opening package | no package under half or over one and a half times the mean charter rate | |
| G7 | Rescue and recovery | won at least as often as the hunt that caused them, for a competent policy | |
| G8 | `:core:test`, `:app:testDebugUnitTest`, debug build | green | |
| G9 | Emulator: new flows, font scale, process death | seen | |

## Handoff note (session stopped on request, 2026-10-11)
State of the branch `run-variety`:
- S1 is done and committed (`8f577ee`): `GuildSim` reports rarity, Legendary count and source, recipes, overwork days
  and the till by source. Before table: `run_variety_evidence/before_guildsim_300.txt`. Competent bots forged a median
  of 5 to 8 Legendary weapons a run, the first near day 5, in 97 to 99 % of runs. Shelf sales are about 3,800 gold of
  a run's till and contract gold 400 to 1,000, so Novice's 3,162 end gold is sales, not supply runs.
- S2 is started and DOES NOT COMPILE yet. In the tree: `LegendConfig` (nested in `DepthConfig.legend`), `RiskProfile`
  with `rollSpread` and `extraSlots` (Safe 6, Reckless one slot and 30 % defect), balance version 12,
  `Weapon.legend` / `Weapon.renown`, `LegendOrigin`, `GameState.heartfire` (and in `ResolutionContext`), six new
  `EventType`s, `Command.Forge.heartfire`, `Command.ChooseOpening`, five new `GameError`s.
- Why it does not compile: `GameEngine.handle` has no branch for `Command.ChooseOpening` yet.

Next steps, in order:
1. `crafting/Legends.kt`: `gainHeartfire(ctx, amount, why)` (capped at `heartfireMax`, emits HEARTFIRE_GAINED) and
   `deed(ctx, weaponId, renown, text)` (history DEED, at `renownForLegend` sets rarity LEGENDARY, `legend = DEEDS`,
   `power + deedPower`, emits LEGEND_EARNED, once).
2. `Forge.kt`: validate Heartfire (Advanced only, one held), `energyCost`, the roll scaled by the temper's spread
   (same single draw), `forgedRarity` = quality rarity capped at Epic, breakthrough roll before the property slots,
   `profile.extraSlots`, known signature at `knownSignatureChance`, `heartfireOutlook` for the workbench.
3. Hone bypass: `GameEngine.hone` uses `Forge.rarityFor`; it must keep a Legendary and never make one
   (`Forge.rarityAfter`). The crooked merchant's weapon (`EncounterCatalog`) takes the capped rarity. A legend
   returning from the board (`WorldEvents` line 385) keeps the uncapped table.
4. Keep DEED and LEGEND history lines out of `WeaponHistoryCompaction`. Add LEGEND_EARNED to the milestone points map.
5. Rank scaling: `Missions.generate` and `SiegeFight.plan` add `rank.enemyPercent`; multiply through one helper.
6. Then S3 to S6 as in the table above. Versions to raise at the end: rules 6, schema 7 (identity steps in both
   migration tables), content 6. The classic golden state will change with the forge rules: review the diff, then record.
