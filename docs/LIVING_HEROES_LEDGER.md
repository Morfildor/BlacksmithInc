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

- [x] M0 ledger, decisions rows (DECISIONS.md "Living heroes and siege variety"), baseline `:core:test` green (686 tests)
- [x] M1 core: revision + versions (schema 8, rules 8, content 7, balance 17), peoples and names, Hooks, counter visit,
  loan returns, leans, sponsored offers, concerns and departures, invariants, fixtures 1-14 and 20 (`LivingIdentityTest`,
  `LivingAgencyTest`), guildsim `--living` / `--legacy`. Commits baf7db3, 1f9b8a5, bdb02a9.
- [ ] M1 UI: identity on sheet / roster / recruit card, silhouettes (agent, worktree), then agency notices
- [~] M2: five Hooks done; eight ambitions done (Avenger, Heirloom, Captain, Name wired); 18 opportunities classified
  (section 5); candidate inclination text (`LivingText.inclination`); market attachment done
- [~] M3-M5: combat grammar (agent, worktree); army templates machinery in `SiegeFight.planArmy` (data pending);
  bestiary data after the grammar merges; structural accounting after the grammar merges
- [ ] M6 sims (legacy vs living, paired influence), UI render checks, docs (GDD, Writing Guide, PROGRESS, checklist, bible notice)

Resume notes: `Agency` (counter, returns, leans, evidence) and `Hooks` (offers, concerns, departures, visitors) are the
member-side resolvers. Scripts that generated content from the bible: scratchpad `tools/peoples.py`, `tools/armies.py`
(not committed; rerun from the bible if needed).

## 5. Content coverage ledger (status: implemented / mapped / narrative / deferred)

Peoples: 12 races, 12 cultures (1,200 given / 600 family names), 20 backgrounds, 10 bynames: implemented
(`content/Peoples.kt`, generated from the bible). Portrait coverage: 20 imported faces tagged human (7), elf (8), orc (2),
lizardfolk, hornborn, halfling (1 each) as best fit; every other people and class pair wears `silhouette_<class>`.

Hooks: Grudge, Debt, Lost Weapon, Oath, Kin implemented (classic eras: Grudge as background, Kin). Ambitions: Slayer,
Defender, Collector, Fortune (kept), Avenger, Heirloom, Captain, Name (implemented, guild members). Specialities: the
existing six, autonomous in new eras. Personality concepts (16 in the bible): backlog, the existing nine are used.

Opportunities (content bible 14.2):

| ID | Status | Route |
|---|---|---|
| opp_powder_trail | implemented | A scout, militia veteran, deserter or mason sponsors the siege's sabotage when the field brings a breach |
| opp_troll_test | pending | Member want with an element (counter) |
| opp_silver_saving | pending | Member want for Silver |
| opp_bone_lesson | implemented (shared route) | The lesson visitor |
| opp_lantern_hunt | deferred | Enemies have no identity that persists between fights except the nemesis |
| opp_gate_practice | pending | Evidence after a structural hit (needs the grammar) |
| opp_returned_blade | implemented | Family-weapon recovery card; the weapon goes home to its family |
| opp_missing_caravan | implemented | `missing_caravan` quest, sponsored by a caravan guard |
| opp_shared_watch | pending | Evidence when two bonded members hold the wall |
| opp_apprentice_request | implemented | The lesson visitor (costs both members the day) |
| opp_wounded_friend | implemented | The healer visitor for an injured bonded member |
| opp_captains_drill | pending | Forecast line by a deserter or militia veteran when a captain is committed |
| opp_ash_samples | pending | Clue rung after a Fire Elemental siege with a scholar on the wall |
| opp_unburied_oath | pending | Reputation when a member with a Hollowbound grudge holds against Sir Carrow |
| opp_forests_binding | narrative | The forecast already names every Covenant role |
| opp_reputation_deed | implemented | A noble's or deserter's sponsored hunt, +2 reputation on a win |
| opp_familiar_weapon | pending | Acknowledgement at an attachment milestone |
| opp_safe_experiment | deferred | No test-commission mechanic for members |

Enemies and armies: 48 definitions and 36 templates extracted (scratchpad `armies_rows.kt`); status filled when the
bestiary lands.

## 6. Evidence and seeds

Tuning seeds 1-600 / 5001-5300. Validation block reserved for this work: 800001-800300 (unseen). Baseline tests: pending.
