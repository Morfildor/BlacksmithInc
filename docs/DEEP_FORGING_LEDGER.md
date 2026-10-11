# Deep forging foundation: ledger

Source: the owner's prompt "deep forging foundation" (2026-10-11).
Base: `run-variety` `99d3949` (rules 6, schema 7, content 6, balance 12, tests of that branch not yet updated).
Work branch: `deep-forging`, worktree `.claude/worktrees/deep-forging`. Not pushed, not merged.
Evidence: `docs/deep_forging_evidence/` (baseline failures, sweep table, the six demonstrations, device shots).

## Ownership
Mine: what each ingredient does in a fight, how a weapon's rules are composed, capabilities and their evaluator,
forge explanations, the bench, journal evidence. The run-variety agent owns rarity, Heartfire, masterwork rules,
difficulty, run variation and consequences. I change no quality, power, rarity, affix slot or legend number.

## Decisions
| # | Decision |
|---|---|
| F1 | One resolver, `crafting/WeaponRules`, composes a weapon's fight rules. `Loadout`, the weapon sheet, the forge outlook, the bench and capabilities read it. Order: family, core, element, affixes, flaws, catalyst, signature. A rule two sources share is listed once and the sharing is recorded. |
| F2 | Cores get a fight rule each (`CombatCatalog.cores`). No new material, no proc for its own sake: one reuse of an existing mechanic per core, one of them a plain number. Guild fights only: a classic outing never runs this engine, so there a core stays a quality and power step. |
| F3 | A stored weapon keeps what it was. `Weapon.craft` (default 1) is the composition it was forged under. Core rules apply from craft 2. Every forge from now on writes 2. A legend returning from the board, and any decoded older weapon, is 1. No schema number moves for it: a defaulted field. |
| F4 | Capabilities are typed facts derived from effect data, never from prose. Rules the fight itself owns (Steam, the Mark bonus, Wet and storm, Thorns, Chill) are explicit entries. One hop inside one weapon joins a two-step conversion. The evaluator answers Satisfied, Conditional with what is missing, or Unsatisfied with a reason. Commissions do not use it yet. |
| F5 | The forge outlook is a projection of `Forge.apply` built from its public parts (`centreQuality`, `rollSpread`, config). A test forges many seeds and holds it to its word. |
| F6 | The bench resolves the production `Fight` on a constant seed with synthetic people and enemies. It takes no context and returns a report, so it cannot touch a run. |
| F7 | The journal keeps two new sets (guild runs only): fight rules the smith has forged (`learnedRules`) and interaction paths seen in a real contract or siege (`witnessed`). A path the rules suggest is shown as suggested until a real fight shows it. |

## Slices and status
Status words: done (tested), partial (what is missing is named), not done.

| Slice | Content | Status |
|---|---|---|
| D1 | Resolver, `Loadout` reads it, fixtures unchanged | not done |
| D2 | Core rules, `Weapon.craft`, weapon construction sites | not done |
| D3 | Capabilities and evaluator, probe validation | not done |
| D4 | Forge outlook, bench | not done |
| D5 | Journal evidence, interaction paths, fight hooks | not done |
| D6 | App: forge card, weapon sheet, bench sheet, notebook tab | not done |
| D7 | Sweep, six demonstrations, tests, guild simulator before and after | not done |
| D8 | Emulator: select, explain, forge, sheet, bench, journal, font scale, process death | not done |

## Interfaces with the run-variety branch
- `Forge.kt`: one line added after the experiment is recorded (`Journal.recordRules`) and `craft = WeaponRules.CRAFT` in the
  `Weapon(...)` call. Nothing else in that file is mine.
- `Missions.advance` and `SiegeFight.resolve`: one call each after the real `Fight.resolve` (`Interactions.witness`).
- Content 6 and balance 12 have no fingerprint pin on the base commit. I add none. The content 6 pin, when it is
  written at integration, must be taken after this branch is merged: `CombatCatalog.cores` is part of the catalog.
- Schema stays 7. `Weapon.craft`, `Journal.learnedRules`, `Journal.witnessed` are defaulted fields.

## Baseline
`./gradlew :core:test` on `99d3949`: see `deep_forging_evidence/baseline_failures.txt`. My gate is "no test fails that
did not fail there, and every new test passes".
