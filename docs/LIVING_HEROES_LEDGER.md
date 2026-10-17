# Living Heroes and Siege Variety: implementation ledger

Branch `living-heroes` (from `main` `fdd6495`, which contains the review baseline `a81e43b`). Spec:
`docs/Tiny_Blacksmith_Living_Heroes_Final_Design.md` (wins on mechanics), content: `docs/Tiny_Blacksmith_Peoples_Names_and_Siege_Bestiary.md`
(wins on names and identities), vocabulary: `docs/WRITING_GUIDE.md`. This file is the resume point after a context reset:
read it first, then `git log --oneline living-heroes`.

## 1. Verified implementation map (core = `core/src/main/kotlin/com/tinyblacksmith/core/`)

| Concern | Real code | Extension |
|---|---|---|
| Hero record | `model/Model.kt Hero` (name, surname, classId, traits = 9 Personalities, ambition, lineage, want) | `raceId`, `cultureId`, `backgroundId`, `familyId`, `hook`, bounded `reasons` |
| Member | `model/Guild.kt GuildMember` (traitId = 12 Traits, specialityId, bonds ≤ 2) | lean, loan-return intent, concern/departure, evidence counters, counter rotation |
| Generation | `heroes/Heroes.generate` + `heroes/Names` (one draw per name on the caller's stream) + `heroes/Appearance` (no RNG) | culture-aware names keeping the draw count; identity/hook by keyed draws |
| Shopping | `market/Market.evaluate`, `purchase` (trade-in = owned equipped only, never a loan) | member counter visit after `resolveShelfVisits`; attachment and switching terms |
| Loans | `guild/GuildOps.loan/recall/weaponOf` (loan outranks own) | autonomous announced return at reconciliation |
| Specialities | `guild/Stories.openBranches/chooseBranch`, `content/GuildContent.specialities` (6) | lean then autonomous choice; `ChooseSpeciality` rejected in revision 1 |
| Board | `guild/Missions.refreshBoard` (subjectWeaponId only for the nemesis), `grant` (weapon to Storage) | sponsor fields; hook-weapon branch; hero-owned recovery |
| Visitors | `engine/Encounters`, `guild/GuildVisitors`, `content/GuildContent.encounters` | creditor, lesson, wounded friend |
| Siege | `guild/SiegeFight.plan/fieldPlan/resolve`, `battle/Battle.warnOfSiege/scheduleNext` | army templates (revision 1), structural hits, verdict |
| Fight | `combat/Fight.kt`, `Effects.kt`, `Model.kt`; `Aim.WeakestFoe` = lowest raw Health, no intercept (Fight.kt `targets`) | actor-action cadence, Poison, channels, Interrupt, return, survival, source tags, front aim |
| Fighter build | `guild/Loadout.combatant/enemy` | `family:<id>`, `metal:<id>` tags |
| Versions | `GameEngine.RULES_VERSION` 7, content 6, `BalanceConfig.version` 16, `SaveCodec.SCHEMA_VERSION` 7 | rules 8, content 7, balance 17 (`LivingConfig`), schema 8 |
| Day order | `GameEngine.endDay` = GDD 4.3 | see §3 |

## 2. Fixed technical choices

- **Era feature revision**: `GameState.livingRevision` (0 = legacy, the default on decode; `newRun` stamps `LivingRevision.CURRENT` = 1).
  Every changed behaviour is gated on it; legacy eras keep every old path, including a pending manual Speciality.
- **Keyed draws** (`rng/Keyed.kt`): `Rng(mix(seed, fnv64(key)))` for identity, hooks, leans, opportunities and armies. No new
  stream state, no stream migration; changing a name pool or an opportunity rerolls nothing else.
- **Classic parity**: a classic revision-1 era draws exactly what a legacy one draws on every stream (names one draw each,
  ambition from the legacy four, no new affixes in classic forging). Proof: `GoldenStateTest` unchanged.
- Departure: `HeroFate.DEPARTED`; possessions go to `Lost(day, "carried away by ...")` (external holder). Not dead.
- Combat additions are actor-local: `Cadence.Actions`, `Stat.POISON`, channels on the actor, structural hits recorded in
  the fight result and charged once in `SiegeFight.resolve`.

## 3. End Day insertion points (GDD 4.3 numbering)

| Step | Insertion |
|---|---|
| 2 party leaves | unchanged freeze |
| 6 shelf visits | then `MemberCounter.visit` (one member a day) |
| 8 contract fights | evidence per quest (one per family) |
| 12 siege | committed template field; structural hits; evidence |
| 13 reconciliation | hooks progress, debts due, leans created/resolved, loan returns, departures (after retirements) |
| 16 new morning | sponsored cards through `refreshBoard`, visitors through `Encounters.offerMorning` |

## 4. Milestone checklist

- [ ] M0 ledger, decisions rows, baseline tests
- [ ] M1 people loop: revision + versions, cultures and names, hooks, counter visit, loan return, lean, one sponsored card, fixtures
- [ ] M2 full personal content: 5 hooks, 8 ambitions, 18 opportunities, concerns and departure, candidate cards, attachment
- [ ] M3 bestiary grammar and standard enemies and templates
- [ ] M4 channel / interrupt, Poison, return and survival, structural hits, Disrupting and Purifying
- [ ] M5 captains, engines, bosses, variants, charter escort, art manifest
- [ ] M6 sims, paired interventions, UI render checks, docs, handoff

## 5. Content coverage ledger (status: implemented / mapped / narrative / deferred)

Filled per milestone. Groups: 12 races, 12 cultures (1,200 given, 600 family names), 20 backgrounds, 5 Hooks, 8 ambitions,
6 Specialities, 16 personality concepts (backlog), 18 opportunities, 36 standard enemies, 6 captains, 3 engines,
3 major monsters, 24 + 6 + 3 + 3 templates, 6 variants, 12 titles (backlog).

## 6. Evidence and seeds

Tuning seeds 1-600 / 5001-5300. Validation block reserved for this work: 800001-800300 (unseen). Baseline tests: pending.
