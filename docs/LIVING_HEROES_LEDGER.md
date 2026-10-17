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
- [x] M1 UI: identity on sheet / roster / recruit card, silhouettes, the owner's six UI fixes (merged; evidence `docs/living_heroes_evidence/ui_identity/`). Agency and siege UI: merged (evidence `docs/living_heroes_evidence/ui_agency/`)
- [~] M2: five Hooks done; eight ambitions done (Avenger, Heirloom, Captain, Name wired); 18 opportunities classified
  (section 5); candidate inclination text (`LivingText.inclination`); market attachment done
- [x] M3-M5: combat grammar (merged 9d79ef5), 48 enemies and 36 armies (`content/Bestiary.kt`, `BestiaryTest`),
  structural accounting and sabotage of the charge (`LivingSiegeTest`), army strength calibrated (`living.armyPercent` 140,
  DECISIONS "Army strength calibration")
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
| opp_troll_test | implemented (needs bestiary units) | A member who watched an enemy mend itself notes what stops it; when one comes in the committed army they visit the counter and value a weapon that stops it (+0.4 utility); stopping it later is recorded |
| opp_silver_saving | implemented (shared route) | Same route as the troll, with Silver among what stops a werewolf; the member buys with their own saved gold. No member commission (adapted) |
| opp_bone_lesson | implemented (shared route) | The lesson visitor |
| opp_lantern_hunt | deferred | Enemies have no identity that persists between fights except the nemesis |
| opp_gate_practice | implemented | A militia veteran or mason standing on the wall while the defences are struck gains "gate" evidence toward Wallwarden |
| opp_returned_blade | implemented | Family-weapon recovery card; the weapon goes home to its family |
| opp_missing_caravan | implemented | `missing_caravan` quest, sponsored by a caravan guard |
| opp_shared_watch | implemented | Evidence when two bonded members hold the wall |
| opp_apprentice_request | implemented | The lesson visitor (costs both members the day) |
| opp_wounded_friend | implemented | The healer visitor for an injured bonded member |
| opp_captains_drill | implemented (needs bestiary units) | A deserter or militia veteran at home adds the captain's own telegraph to the scouts' report |
| opp_ash_samples | implemented, adapted | A field scholar standing after a Fire Elemental siege earns the next clue rung of an open signature; no separate assignment step |
| opp_unburied_oath | implemented (needs bestiary units) | A member with an open Pale Procession grievance holds the wall against Sir Carrow: Hook resolved, +2 reputation, Avenger fulfilled |
| opp_forests_binding | narrative | The forecast already names every Covenant role |
| opp_reputation_deed | implemented | A noble's or deserter's sponsored hunt, +2 reputation on a win |
| opp_familiar_weapon | implemented | Acknowledgement at an attachment milestone |
| opp_safe_experiment | deferred | No test-commission mechanic for members |

Enemies and armies: 48 implemented (`EnemyProfile.status`: adapted ones carry their reason, see `content/Bestiary.kt`),
36 armies: 24 standard, 6 captain, 3 engine, 3 great monster. Art wanted: `docs/ART_NEEDED_BESTIARY.md`.

## 6. Evidence and seeds

Tuning seeds 1-600 / 5001-5300. Validation block reserved for this work: 800001-800300 (unseen). Baseline tests: pending.

Paired influence, 2026-10-17 (seeds 1-200, `--living x --personal answer|ignore`, charter towns_last_hope, horizon 30; the
answer bot takes a member's personal offer first when the party is rested, health 75 or more, and two members are fit):

| Policy | Personal offers | Charter | Defeated | Captured a run | Departures (runs with one) | Concerns a run |
|---|---|---|---|---|---|---|
| SYNERGY | answer | 60% | 13% | 0.30 | 5% | 0.37 |
| SYNERGY | ignore | 54% | 11% | 0.17 | 23% | 1.55 |
| ADAPTIVE | answer | 38% | 7% | 0.74 | 10% | 1.13 |
| ADAPTIVE | ignore | 59% | 5% | 0.40 | 17% | 1.52 |

Reading: the members' wishes measurably answer the smith's choices (departures fall by a half to three quarters when
offers are answered). Answering is a real trade-off for the one party: ADAPTIVE, which otherwise sabotages before each
siege (37% of its contracts), gives that up and holds fewer sieges. SYNERGY, which hunts anyway, gains. Without the
readiness gate the answer bot sent hurt parties on grudge hunts (ADAPTIVE captured 2.39 a run). Recorded, not tuned:
owner decides (no automatic nerf).
