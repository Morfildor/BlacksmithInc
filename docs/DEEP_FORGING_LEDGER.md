# Deep forging foundation: ledger

Source: the owner's prompt "deep forging foundation" (2026-10-11).
Base: `run-variety` `99d3949` (rules 6, schema 7, content 6, balance 12, tests of that branch not yet updated).
Work branch: `deep-forging`, worktree `.claude/worktrees/deep-forging`. Not pushed, not merged.
Evidence: `docs/deep_forging_evidence/` (baseline failures, sweep table, path demonstrations, guild simulator before
and after, device shots).

## Ownership
Mine: what each ingredient does in a fight, how a weapon's rules are composed, capabilities and their evaluator,
forge explanations, the bench, journal evidence. The run-variety agent owns rarity, Heartfire, masterwork rules,
difficulty, run variation and consequences. I change no quality, power, rarity, affix slot or legend number.

## Decisions
| # | Decision |
|---|---|
| F1 | One resolver, `crafting/WeaponRules`, composes a weapon's fight rules. `Loadout`, the weapon sheet, the forge outlook, the bench and capabilities read it. Order: family, metal, element, buffs, flaws, catalyst, signature. A rule two parts share is listed once and the other part is named beside it. |
| F2 | Metals get a fight rule each (`CombatCatalog.cores`). No new material: each reuses a mechanic the fight already had, and one (Obsidian) is also a plain number. Guild fights only. A classic outing never runs this engine, so there a metal stays a quality and power step. |
| F3 | A stored weapon keeps what it was. `Weapon.craft` (default 1) is the composition it was forged under. Every forge, the merchant's weapon and the guild's opening weapon write 2. A decoded older weapon is 1, and a legend returning from the board keeps the craft it was recorded with. No schema number moves: defaulted fields. |
| F4 | Capabilities are typed facts derived from effect data, never from prose. What the fight itself owns (Steam, the Mark bonus, Thorns, Regeneration) is entered explicitly. The evaluator looks one step for an enabler and answers Satisfied, Conditional with what is missing, or Unsatisfied with a reason. Commissions do not use it yet. |
| F5 | The forge outlook is a projection of `Forge.apply` built from its public parts (`centreQuality`, `rollSpread`, config). A test forges 120 seeds of 7 recipes and holds it to its word. |
| F6 | The bench resolves the production `Fight` on a constant seed with made-up people and enemies. It takes no context and returns a report, so it cannot touch a run. |
| F7 | The journal keeps two new sets (guild runs only): fight rules the smith has forged (`learnedRules`) and interaction paths seen in a real contract or siege (`witnessed`). A path the rules suggest is shown as suggested until a real fight shows it. The bench never writes either. |
| F8 | A rule can be rewritten for new weapons without touching old ones: `CombatCatalog.revisions` maps the old rule ID to a rule with an ID of its own, applied from craft 2. Used once. Void Ink took Burn at round end from the enemy that matters most, after that round's Burn had already ticked, and almost never found any. New weapons take it straight after the wielder acts, from the enemy the wielder faces. |

## Ingredient roles (guild fights, weapons forged from now on)
| Ingredient | Role | Limit |
|---|---|---|
| Iron | Enters with 3 Guard | Once a fight |
| Bronze | An enemy that breaks the wielder's Guard takes 5 | Needs Guard on the wielder. Once a round |
| Silver | Enters with 2 Charge | Does nothing without a Charge burst on the weapon or a third Charge |
| Obsidian | Strikes at 110% | Cracks the first time its wielder's Guard is broken |
| Starsteel | The wielder's own action spending a Mark hits for 3 more | Needs a Mark. Once a round |
| Moonsteel | A hit on an already chilled enemy lands 3 harder | The Chill must be there before the hit. Once a round |
| Ember Resin | Burn | Weakest augment in the sweep. Feeds Steam and Void Ink |
| Frost Bloom | Chill, weaker enemy strikes | The enemy's own strike spends it |
| Stormglass | Arc to a second enemy | Small against one enemy |
| Grave Dust | Bane of the undead | Little elsewhere |
| Verdant Sap | Regeneration | Slow |
| Sun Ash | Mark | One Mark at a time, anyone's hit spends it |
| Binding Salt | Healing received becomes Charge, burst at 3 | Idle with no healer |
| Runestone Shard | Answers a cracked weapon | Idle unless a weapon in the party cracks |
| Dragon Oil | Spreads Burn or Chill, spends Steam as a lance through Guard | The lance needs Steam |
| Void Ink | Burn into Guard for the weakest ally, Steam into Guard for all | That Burn deals no damage |

Families (delivery) and techniques (crafting trade-off) are unchanged. Quality and rarity belong to the other agent.

## Coverage after this work
| Step | Before | Now |
|---|---|---|
| Ingredient to weapon | Metal had no fight meaning | Every one of the 16 has a stated role and limit in content |
| Weapon to rules | Rule tables read in three places | One resolver, stable IDs, versioned by `Weapon.craft` |
| Rules to events | Engine existed | Unchanged engine. Metal rules and the Void Ink rewrite are content |
| Events to explanation | Fight log only | Outlook before forging, bench with causes and reasons, journal evidence |
| Requirements | None | Typed capability queries with a pure evaluator, not yet used by commissions |

## Slices and status
Status words: done (tested), partial (what is missing is named), not done.

| Slice | Content | Status |
|---|---|---|
| D1 | Resolver, `Loadout` reads it, fixtures unchanged | done |
| D2 | Metal rules, `Weapon.craft`, weapon construction sites | done |
| D3 | Capabilities and evaluator, checked against the fight | done |
| D4 | Forge outlook, bench | done |
| D5 | Journal evidence, interaction paths, fight hooks | done |
| D6 | App: forge card, weapon sheet, bench sheet, notebook tab | done (12 screen model tests. The shop-day sheet has no bench button) |
| D7 | Sweep, seven demonstrations, tests, guild simulator before and after | done |
| D8 | Emulator: select, explain, forge, sheet, bench, journal, font scale, process death | partial: all reached at 1.0 and 1.3. The anvil draft and an open bench do not survive a process kill. "Seen in a real fight" not shown on a device |

## Capability interface for future commissions
`Capabilities.evaluate(query, subject, company)` in `crafting/Capabilities.kt`. `subject` is
`WeaponRules.of(weapon, content)`. `company` says who carries it, who stands beside them and (optionally) which enemies.
Queries: `Generates(res, place, atLeast)`, `Spends(res)`, `Requires(res)`, `Converts(from, to)`, `ProtectsAnother`,
`ReachesBackline`. Answers: `Satisfied(by)`, `Conditional(by, missing)`, `Unsatisfied(reason)`. "Satisfied" means
every piece is present in that company. It does not promise the rule fires in a given fight: the sweep's table A and G
show how often a ready rule stays silent and why.

## Interfaces with the run-variety branch
- `Forge.kt`: `craft = WeaponRules.CRAFT` in the `Weapon(...)` call and one line after the experiment is recorded
  (`Interactions.learn`). Nothing else in that file is mine.
- `Missions` and `SiegeFight`: one call each after the real `Fight.resolve` (`Interactions.witness`).
- `Loadout.weaponEffects` now takes the `ContentCatalog`. `run-variety` has since changed a neighbouring line in
  `Loadout.kt` (plain gear is common or uncommon) and one line in `CombatContent.kt` and `GuildScenariosTest.kt`. The
  merge should be small. I did not merge: it was not authorised.
- Content 6 and balance 12 have no fingerprint pin on the base commit. I add none. The content 6 pin must be taken
  after this branch is merged: `CombatCatalog.cores`, `notes`, `interactions` and `revisions` are part of the catalog.
- Schema stays 7. `Weapon.craft`, `LegendEntry.craft`, `Journal.learnedRules`, `Journal.witnessed` are defaulted fields.
- Balance findings for the other agent, not acted on: Frost Bloom and Sun Ash are the strongest augments and Ember
  Resin and Grave Dust the weakest. Wet has no source in launch content. Runestone Shard does nothing unless a weapon
  cracks. Bronze, Silver and Obsidian are almost never alone the best metal (1 to 2% of groups).

## Baseline and gate
`./gradlew :core:test` on `99d3949`: 561 tests, 57 failing (`deep_forging_evidence/baseline_failures.txt`). Gate: no
test fails that did not fail there, and every new test passes. Result on this branch: see PROGRESS.
