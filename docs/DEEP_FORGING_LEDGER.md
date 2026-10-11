# Deep forging foundation: ledger

Sources: the owner's prompts "deep forging foundation" and "complete integration, correctness and targeted balance
validation" (both 2026-10-11).
Branch: `deep-forging-integration` (worktree `.claude/worktrees/deep-forging`). It holds `deep-forging` and
`run-variety` up to its balance 13 commits, merged twice. Not pushed, not merged into `main`.
State: rules 6, schema 7, content 6, balance 14. Both suites pass.
Evidence: `docs/deep_forging_evidence/`. Bots and bench fights, not people. None of it says the game is fun.

## Ownership
Mine: what each ingredient does in a fight, how a weapon's rules are composed, capabilities and their evaluator,
forge explanations, the bench, journal evidence. The run-variety agent owns rarity, Heartfire, masterwork rules,
difficulty, run variation and consequences. One number of theirs was moved here with the owner's leave and their
knowledge: the guild siege base (see Balance).

## Decisions
| # | Decision |
|---|---|
| F1 | One resolver, `crafting/WeaponRules`, composes a weapon's fight rules. `Loadout`, the weapon sheet, the forge outlook, the bench and capabilities read it. Order: family, metal, element, buffs, flaws, catalyst, signature. A rule two parts share is listed once and the other part is named beside it. |
| F2 | Metals get a fight rule each (`CombatCatalog.cores`, numbers in `CombatContent.CoreNumbers`). No new material. Guild fights only. A classic outing never runs this engine. |
| F3 | A stored weapon keeps what it was. `Weapon.craft` (default 1) is the composition it was forged under. Every forge, the merchant's weapon and the guild's opening weapon write 2. A decoded older weapon is 1, and a legend returning from the board keeps the craft it was recorded with. |
| F4 | Capabilities are typed facts derived from effect data, never from prose. The evaluator looks one step for an enabler. A rule that only adds to what is already there (an echo, a spreading fire) is not counted as where it comes from. |
| F5 | The forge outlook is a projection of `Forge.apply` built from its public parts. A test forges 120 seeds of 7 recipes and holds it to its word. It reads the metal's tier for rarity (Epic needs fine metal). |
| F6 | The bench resolves the production `Fight` on a constant seed with made-up people and enemies. It takes no context and returns a report, so it cannot touch a run. One of its lines is the town's wall. |
| F7 | The journal keeps fight rules the smith has forged (`learnedRules`) and interaction paths seen in a real contract or siege (`witnessed`). The bench writes neither. |
| F8 | A rule can be rewritten for new weapons without touching old ones (`CombatCatalog.revisions`, applied from craft 2). Used twice: Void Ink, Dragon Oil. |
| F9 | Verdicts are named for what they promise: `Available` (the weapon has the rule and the company brings every piece), `Needs` (what is missing, or what could not be checked), `CannotProvide`. "Available" never promises that the moment comes. Where an answer hangs on a second step it is `Need.Unverified`, said as not checked, and the bench is named as the place to see it. |

## Ingredient roles (guild fights, weapons forged from now on)
| Ingredient | Price | Role | Limit |
|---|---|---|---|
| Iron | 10 | Enters with 3 Guard | Once a fight. Small and always there |
| Bronze | 22 | An enemy that breaks the wielder's Guard takes 5 | Needs Guard on the wielder: in practice a Guardian. Once a round |
| Silver | 45 | Enters with 3 Charge, one burst | Worth nothing without a Charge burst on the weapon (Binding Salt, a Stormcharged roll) |
| Obsidian | 75 | Strikes a tenth harder | Cracks the first time its wielder's Guard breaks: 25 condition after the fight for whoever holds the line |
| Starsteel | 120 | The wielder's own action spending a Mark hits for 3 more | Needs a Mark, and anyone's hit spends it first. Once a round |
| Moonsteel | 180 | A hit on an already chilled enemy lands 2 harder | The Chill must be there before the hit. Once a round |
| Ember Resin | 8 | Burn | Ashclaw and Embermaw resist fire. Weakest augment. Feeds Steam, Void Ink and Dragon Oil |
| Frost Bloom | 18 | Chill, weaker enemy strikes | The enemy's own strike spends it. Ashclaw and Embermaw are weak to frost |
| Verdant Sap | 16 | Regeneration for the wielder | Slow |
| Stormglass | 40 | Arc to the last enemy | Small against one enemy |
| Grave Dust | 55 | Bleed, which goes through Guard | No faction is weak to it and the Hollowbound bleed half as much |
| Sun Ash | 85 | Mark | The Hollowbound are weak to sun. Anyone's hit spends the Mark |
| Binding Salt | 15 | Healing received becomes Charge, burst at 3 | Idle with no healer, unless the metal is Silver |
| Runestone Shard | 30 | Answers a cracked weapon | Idle unless a weapon in the party cracks |
| Dragon Oil | 55 | Spreads the fire its wielder lights, spends Steam as a lance through Guard | Does nothing with Chill. The lance needs Steam from a fire and a cold in two hands |
| Void Ink | 90 | Burn into Guard for the weakest ally, Steam into Guard for all | That Burn deals no damage |

The first ledger's table said Grave Dust was a bane of the undead. That was wrong: it is their resisted element. The
card in the game always said so.

## Integration
- The first merge attempt (first pass) was refused by the harness because the owner's prompt then said not to merge.
  With the second prompt the same command ran. Nothing was bypassed.
- Merge 1 (`run-variety` `ff656ef`): five textual conflicts (changelog, command list, decisions, `ForgePanel.kt`,
  `ForgeUi.kt`). The Forge keeps the outlook card in place of the older draft fight notes, and keeps the Heartfire
  row and the tempers' numbers.
- Merge 2 (balance 13): two conflicts (changelog, the opening weapon in `GuildOps`). The outlook and the bench now pass
  the metal's tier to `Forge.forgedRarity`, so they do not promise an Epic slot in a metal that cannot hold one.
- One implementation each: rules `WeaponRules`, craft `Weapon.craft` at four construction sites (forge, merchant,
  opening weapon, legend return), learning in `Forge`, witnessing after the one `Fight.resolve` in `Missions` and in
  `SiegeFight`, metal numbers in `CombatContent.CoreNumbers`, siege base in `GuildConfig`.

## The inherited failures
The 57 core and 15 app failures of the first report were the base commit `99d3949` ("tests not yet updated"). The
run-variety branch fixed them itself before this pass. What failed on the integrated tree, each classified:
| Test | Class | What was done |
|---|---|---|
| `CombatEngineTest`, every rule fires somewhere | Fixture | It forged with bronze and the older composition only. It now rotates every metal and both compositions |
| `VersionFingerprintTest`, content 6 | Fingerprint | Re-pinned once, after the catalog was final |
| `VersionFingerprintTest`, balance 14 | Fingerprint | New row |
| `LaunchContentTest`, no percent in player text | Real regression (mine) | Obsidian's line says "a tenth harder" again |
| `GuildScenariosTest`, delve checkpoint | Fixture pin | The played scenario's day moved from 10 to 5: fights end differently |
| `GuildScenariosTest` (two), `ScenarioSavesTest`, charter eve | Fixture pin | Seed 1 no longer has the three defenders at home on day 20. Seed 5 keeps every promise of the scenario (breached as played, held by the strong roster) |
| Four `DeepForgingTest` cases | Obsolete expectation | They asserted Silver at 2 Charge and Dragon Oil answering Chill. Rewritten to the new rules, with the old behaviour asserted on a craft 1 weapon |
No test was disabled and no assertion was weakened.

## Versions
- Schema 7: unchanged. `Weapon.craft`, `LegendEntry.craft`, `Journal.learnedRules`, `Journal.witnessed` ride on the
  schema run-variety already raised, which has not shipped. A 0.8.0 save decodes with craft 1, which is exactly what
  "forged before" means. This holds only if the two features ship together.
- Rules 6: unchanged. The engine did not move. The classic golden state passes.
- Content 6: same number, new pin. The catalog changed and content 6 has not shipped.
- Balance 14: new, for the siege base.
- Old weapons: `DeepForgingTest` takes two craft 1 weapons through the hone, the shelf, a price, a loan, a recall, a
  save, a real contract and another save, and asserts the same rule list at every step and no newer rule in the
  fight. Older save fixtures (schemas 1 to 5) decode as craft 1. The legend board keeps the craft.

## Balance
All from `./gradlew :core:combat --args="lab ..."` and `--args="guild ..."`. Tuning seeds and validation seeds are
different, and the last tables are from seeds nothing was tuned on.

**The metal's rule by itself** (same weapon and quality, forged since minus forged before, seeds 31 to 33, 933,120
bench fights, early stage. Score runs 0 to 200. Wins are per 100 fights):
| Metal | Prepared in | When prepared: score, wins | When not | Verdict |
|---|---|---|---|---|
| Iron | always | +1.8, +0.5 | | Kept. A starter that is never wasted |
| Bronze | 25% (a Guardian) | +8.1, +7.8 | 0 | Kept. The preparation is cheap and it pays |
| Silver | 33% (Binding Salt) | +9.2, +5.8 | 0 | Changed, 2 to 3 Charge. At 2 it also needed a third Charge from somebody else and was prepared in 15% of fights. Now Silver with a Charge burst is the whole recipe |
| Obsidian | no preparation | +4.3 where it cracks, +5.7 where it does not | | Kept. The first report called it weak. It is the striker's metal, and the crack costs condition, not the fight. Its card now says so |
| Starsteel | 72% by the evaluator, fires in 44% of those | +2.1, +1.7 | 0 | Kept at 3 |
| Moonsteel | 18% | +3.2, +3.8 | 0 | Changed, 3 to 2. In guild runs it was the rule worth most, on the metal quality already favours |

**The choice at the anvil** (each metal at its own quality, share of groups within 5 of the best metal, early): Iron
42%, Bronze 44% (69% on a Guardian), Silver 57% (76% with a burst), Obsidian 75%, Starsteel 80%, Moonsteel 87%. A
cheaper metal is at least as good as Starsteel in 87% of groups and as Moonsteel in 87%: the two fine metals are
bought for Epic and quality, not for their rules. "Alone the best 1 to 2%" in the first report was the wrong measure.

**Frost Bloom with Dragon Oil.** The 72% of the first report was: share of bench scenarios (wielder, company, line,
one seed) in which a staff or bow of that pair scored in the top tenth of all recipes. Measured again over three
stages, four lines and several seeds, the pair as a whole was at 60%, the next pair at 39%. Cause: Dragon Oil
answered Chill with Burn on every enemy, so one frost weapon made Steam and a lance every round by itself. On new
weapons it spreads only fire: 43%, beside Frost Bloom with Void Ink at 41% and with Binding Salt at 38%. A cheaper
recipe of the same family is within 5 of it in 96% of its fights.

**Frost Bloom by itself is still the widest augment** (within 5 of the best augment in 69% of groups, Sun Ash 57%,
Verdant Sap 39%, Stormglass 35%, Grave Dust 24%, Ember Resin 20%). The cause is not an ingredient number: Ashclaw
and Embermaw are both weak to frost and resist fire, the Hollowbound are weak to sun and resist grave, and nothing is
weak to fire, storm, grave or sap. A what-if with the Ashclaw weak to grave gives Frost Bloom 46%, Grave Dust 49%,
Sun Ash 58% (`lab/whatif_seed11_ashclaw_weak_to_grave.txt`). Not applied: faction weaknesses are shown to the player,
read by classic runs and locked in golden states. It is the owner's decision. Ember Resin and Grave Dust do not have
a valuable niche today. Ember Resin is the cheapest augment and the fuel for Dragon Oil, Void Ink and Steam.

**Guild difficulty, paired** (same seeds, policies, charters, new account, 30 days. Charter secured / forge fallen /
neither, as a share of 300 runs. `guild_final_600001_300.txt`, seeds 600001 to 600300, balance 14):
| Charter | Policy | Final build | Same build without the metals' rules |
|---|---|---|---|
| Town's Last Hope | Synergy | 63 / 3 / 34 (189 runs, 57 to 68) | 62 / 5 / 33 |
| | Adaptive | 60 / 1 / 39 (180, 54 to 65) | 50 / 1 / 49 |
| | Defence first | 50 / 1 / 49 (151, 45 to 56) | 41 / 8 / 51 |
| Licensed Bad Ideas | Synergy | 66 / 2 / 32 (198, 60 to 71) | 56 / 6 / 38 |
| | Adaptive | 53 / 1 / 46 (160, 48 to 59) | 38 / 4 / 58 |
| | Defence first | 45 / 5 / 50 (134, 39 to 50) | 41 / 6 / 53 |
| Honest Business | Synergy | 54 / 6 / 39 (163, 49 to 60) | 52 / 7 / 41 |
| | Adaptive | 57 / 1 / 43 (170, 51 to 62) | 48 / 2 / 50 |
| | Defence first | 52 / 3 / 45 (156, 46 to 58) | 48 / 5 / 48 |
- Run-variety's gate G3 asks each of these for 40 to 65%. Eight of nine are inside on this block and Licensed Bad
  Ideas Synergy is one point over. On the block the change was validated on (500001 to 500300) Town's Last Hope
  Synergy was 70% (64 to 75). Synergy sits at the top edge of the band. Not resolved further.
- How it got there. With balance 13 the metals' rules lifted the charter share by 5 to 16 points
  (`guild_paired_500001_300.txt`: Town's Last Hope 73 / 67 / 55 against 65 / 59 / 44 without them). Switching one
  rule off at a time on tuning seeds showed Moonsteel worth most (4 to 5 points), then Obsidian and Silver, with
  Bronze and Starsteel near nothing because the bots do not prepare them. Moonsteel went to 2 and the siege base from
  186 to 190. The siege base is one number, on the night a charter is decided. Contracts keep the whole benefit.
- The first report's "Synergy 58 to 68, Adaptive 35 to 48" were percentages of 200 runs, not counts, on two different
  builds with the older siege numbers. They are superseded by the table above.

## Capability interface for future commissions
`Capabilities.evaluate(query, subject, company)`. `subject` is `WeaponRules.of(weapon, content)`. `company` says who
carries it, who stands beside them, optionally which enemies, and whether it is the wall. Queries: `Generates(res,
place)`, `Spends(res)`, `Requires(res)`, `Converts(from, to)`, `ProtectsAnother`, `ReachesBackline`. Answers:
`Available(by)`, `Needs(by, missing)`, `CannotProvide(reason)`. `Capabilities.text(verdict, subject)` words them:
"Available when its moment comes: ...", "Needs: ...", "Cannot provide. ...". A commission must not treat `Available`
as "will fire": Starsteel is available in 72% of lab fights and fires in 44% of those.

## Device (emulator, read-only Pixel_10_Pro AVD, 1080x1920)
- `journey_seen/`: seed 9, a Bronze sword forged, loaned to the Guardian through his sheet, the supplier's road with
  both members, End Day. The fight report shows "Urien's Guard breaks. Urien's Bronze rings back hits Ashclaw scout
  for 5." Fight notes: "Suggested by the rules" before, "Seen in a real fight" after, one row, the same after the
  process is killed in the background and after a force-stop. Also at font scale 1.3 (`journey_seen_1.3/`).
- `journey_suggested/`: the same run with the sword in the Warden's hands. No Guard breaks on the wielder, the rule
  is not in the report, and the path stays "Suggested by the rules" through the same kills.
- `forge_1.0/`, `forge_1.3/`: trays, outlook, details, the recipe bench with "What happened", "Waiting for its
  moment" and the town's wall, the owned-weapon bench, fight notes. The recipe on the anvil comes back after the
  process is killed in the background (saved instance state, which was already there). An open bench closes. A
  force-stop empties the anvil, as it empties any app's saved state.
- `scenarios/`: every regenerated scenario save loaded through the debug menu.

## Still open
- Nobody has played it. Bots and bench fights do not show whether the choices are interesting.
- Synergy is at the top edge of the difficulty band (63 to 70% under Town's Last Hope, depending on the seed block).
- Frost Bloom is the widest augment and Ember Resin and Grave Dust have no real niche, because of what the three
  factions are weak to. A decision for the owner, with the what-if above.
- The evaluator is generous for Starsteel (anyone's hit spends the Mark first). It says "available", never "fires".
- The bots do not prepare Bronze or Silver on purpose, so the guild simulator undervalues both.
- The shop-day read-only weapon sheet has no bench button. The outlook card assumes no particular company.
- Instrumented tests (`connectedDebugAndroidTest`) were compiled, not run. No physical phone.
- `run-variety` may still add docs commits. They merge without code conflicts.
