# Guild evolution: implementation plan and task ledger

**Goal:** turn the shop into the forge of a small adventurers' guild: contracted heroes take loaned blades on missions
the smith chooses, fights are resolved by a short automatic interaction engine (effects that trigger, convert and
consume each other), and what happens to the party sets up the next day's decisions.

**Spec:** `docs/GUILD_EVOLUTION_SPEC.md` (the owner's plan of 2026-10-10, copied verbatim). This file says how it is
built here and what is done. Section numbers below ("spec 6.4") point into the spec.

**Baseline:** `main` @ `8f2e284` (0.7.0 plus the notice banner): rules 4, save schema 5, content 4, balance 10.
**Branch:** `guild-evolution` (worktree `.claude/worktrees/guild-evolution`), merged into `main` as 0.8.0 on the owner's instruction to push when done.

**Owner's answers (2026-10-10):** the revision of the LOCKED rules is confirmed as spec 2.2 states it; Milestone A is
inspected as text first and then on a debug screen; the whole plan is to be implemented in one run, then tested.
The spec's stop gates (after A: is the combat fun to watch; after B: is the five-day slice fun) could therefore not be
held as gates. They remain open questions for the owner and are listed at the end of this file.

## Global constraints

- `:core` stays pure Kotlin with no Android dependency; every mutation goes through `GameEngine.handle`.
- Combat is automatic. No target picking, no turn-by-turn input, no timing input.
- Every random draw of a mission is made when the offer is generated or the deployment is committed, and stored.
  Previewing, reopening, reloading and replaying draw nothing.
- New numbers live in `BalanceConfig.guild` (one nested group; the root constructor is at the JVM slot limit) or in
  content definitions. Resolvers hold none (`ConstantsTest`).
- A run without a guild (`GameState.guild == null`) plays exactly as rules 4 did: same draws, same outcomes. Every
  existing test and golden state has to pass unchanged.
- One physical weapon, one location, one custodian. A loan is `WeaponLocation.Loaned`; it cannot be listed, sold,
  salvaged, scrapped, donated, honed from afar or loaned twice.
- Reward and claim commands are idempotent: a retried command ID changes nothing.
- An effect chain is finite by its visible limits. The internal event budget is a defect guard: it throws with the seed.
- Text the player needs is real text. Sprites stay decorative.
- Commits on the work branch as the work went; `main` pushed once, at the end, on the owner's instruction.

## Design decisions (how the spec is realised)

### D1. Guild mode is a property of the run
`GameEngine.newRun(legacy, seed, rulesVersion, charterId)`: with a charter the run gets a `GuildRunState`; without one
it is a classic run. The app starts guild runs (charter choice at "Begin era"); a classic run in progress when the
update is installed continues as a classic run to its end (spec 16.1, "finish the current classic era through a
compatible resolver"). Nothing is rewritten under the player. All existing tests keep building classic runs.

### D2. One aggregate, one new location, one new stream
- `GameState.guild: GuildRunState?` holds everything the guild adds (members, candidates, offers, the planned and the
  active mission, reservations, captives, milestone claims, the nemesis, the rival, the world law).
- `WeaponLocation.Loaned(heroId)` with a pinned serial name. `Weapon.ownerId` stays null for a loan, so the market,
  inheritance and death rules for private weapons never see it. `GameState.loanOf(heroId)` finds it.
- `RngStream.GUILD` is appended (seeded by ordinal, so the older streams keep their seeds). Candidates, offers and
  mission seeds draw from it. Schema 6 seeds it for older saves the way schema 5 seeded ENCOUNTERS.

### D3. The interaction engine (`core/combat/`)
Pure functions over explicit inputs; no `GameState`, no `ResolutionContext`.
- `Fight.resolve(setup: FightSetup): FightResult`. `FightSetup` = party actors, enemy actors, objective, party-wide
  effects (relics, banner), max rounds, seed. `FightResult` = outcome, per-actor end state, an ordered event timeline,
  three highlights.
- **Stats** (`Stat`): GUARD, CHARGE, MARK, BURN, REGEN, CHILL, WET, BLEED, SCRAP, THORNS, STEAM. Integer stacks on an
  actor. Health is separate (0..maxHealth).
- **Events** (`FightEvent`): id, round, kind, source, target, amount, stat, effectId, parentId, rootId, text. Kinds:
  ACTION, DAMAGE, GUARD_ABSORBED, GUARD_BROKEN, GUARD_GAINED, HEALED, OVERHEAL, STAT_GAINED, STAT_SPENT, HEALTH_PAID,
  FRACTURED, DOWNED, SUMMONED, RETREATED, OBJECTIVE, ROUND_START, ROUND_END.
- **Effects** (`EffectDef`): trigger, conditions, cost, actions, limit, description. All typed (sealed classes and
  enums); no string maps. An effect belongs to an actor (weapon, trait, class, enemy) or to the party (relic).
- **Order:** rounds 1..max. Party acts in slot order, then enemies in slot order. Each actor takes one class action.
  After every event the trigger queue is drained breadth-first in stable order (actor slot, then effect order).
  Round end: Burn ticks, Regeneration ticks, Guard above the carry-over cap expires, per-round limits reset.
- **Finite chains:** an event produced by an effect carries `effectId`; an effect never triggers on an event it
  produced itself, echoes do not trigger echoes (`Condition.NotFromEffect`), costs are paid before the action is
  emitted, DAMAGE and HEALED events only exist for positive effective amounts, every effect has a per-round or
  per-encounter limit. Budget: 400 events per round; exceeding it throws `FightLoopException(seed, round)`.
- **Randomness:** enemy strikes roll inside a stored [min, max] from `Rng(seed)`. Nothing else draws.
- **Health:** party actors enter with the hero's current health (0..100). At 0 an actor is downed for the fight.
- **Retreat:** by posture. Cautious: when any member is downed or the party is below 35% of its entering health.
  Balanced: when two are downed (or the last one standing is below 25%). Reckless: never.

### D4. Classes, weapons and builds
- Class kits are content (`ClassKitDef`): Guardian (Guard, intercept, strike), Warden (heal the weakest, small
  strike), Battlemage (opener: seed a Charge and Mark; then bolts), Ranger (Mark the priority enemy, follow up on
  marked), Duelist (two small strikes).
- A weapon contributes strike damage (power, condition, class fit, element matchup) and its **combat effects**:
  `ContentCatalog.combatEffects` maps affix IDs and signature IDs to `EffectDef`s. Existing affixes are re-expressed
  (Flaming applies Burn, Frostbound applies Chill, Stormcharged stores Charge, Vampiric heals on a kill, Guardian's adds
  Guard, Brittle fractures, Heavy costs opening Guard, Bloodbound pays health for damage, ...). No affix ID changes.
- New build anchors are forged, not handed out: a **catalyst decides the behaviour** (spec 7.1). Two new catalysts
  (content 5): Capacitor Coil (the blade becomes a Stormglass Capacitor: healing received becomes Charge, 3 Charge
  becomes a burst) and Bell Bronze Flux (Brittle Bellblade: controlled fracture into scrap tokens). They are affixes
  granted by the catalyst, so the forge, the journal and the weapon card need no new slot.
- Relics are build rules in the existing three-slot draft (spec 9.2): 8 combat relics added to the 4 existing ones
  (12 total, the spec's cap).

### D5. Missions (`core/guild/`)
- `MissionDef` (content): archetype, route, days (1 or 2), enemy roster by tier, objective, reward table, danger
  (SAFE: no death; DANGEROUS: capture or death if a downed member is not extracted; a RECKLESS push accepts lethal
  risk), optional second stage, what it does to the coming siege (sabotage).
- `MissionOffer`: the stored instance of a def: enemies with their rolled numbers, reward, expiry, seed.
- `Command.PlanDeployment` stores the plan during planning (absolute selection; replaceable until End Day).
  End Day commits it: fee paid, gear locked, party away. Stage 1 resolves that End Day. A one-day mission is back the
  next morning; a two-day mission waits at a checkpoint (Push deeper / Return, default Return) and resolves its second
  stage at the next End Day.
- Aftermath (applied once, at return): health, wounds (Exhausted 1 day, Injured 2 days, Scarred), loot, gold share,
  weapon condition, fracture repair, custody (a loan carried by a captured or dead member is seized: it becomes the
  target of a recovery mission), captive with a deadline (rescue mission), faction suppression, sabotage of the siege.

### D6. Day order for a guild run (spec 15.4)
1. Commit the planned deployment. 2. Expire the visitor; default the checkpoint. 3. Commissions and shop visits
(members do not shop for weapons). 4. Advance the mission one stage. 5. Autonomous activities for residents; members
at home rest, train or stand guard. 6. Factions, world events, consequences, siege warning. 7. Siege through the
interaction engine with the defenders who are present. 8. Aftermath, retirements, recovery, milestones. 9. Report.
10. Morning: returns, wounds tick, candidates and offers refresh, relic offer, visitor.

### D7. Siege in a guild run
`SiegeScenario` gains the enemy plan (roster, modifiers, sabotaged modifiers). Three defence places: reserved members
first, then the strongest present members and residents (the automatic recommendation). Militia and the armory fight
the outer approach: they remove enemy health before the decisive fight. Outcome: held (enemies defeated), held at a
cost (survived to the last round or defenders retreated behind the gate), broken (defenders downed). Forge damage
comes from the outcome and the enemies left standing, and the report says "held at a cost" when the town holds and
the forge is damaged (spec 1.2, last paragraph). Classic runs keep the scalar siege.

### D8. Charter milestone
Day 20: the charter warlord leads the siege. Beating it with the forge standing earns `CHARTER_SECURED`, stored as a
`RunMilestoneClaim`. The smith may retire the chapter (run ends as a success, legacy claimed once with the milestone
bonus) or continue. A later defeat cannot claim the milestone bonus again.

## Files

| File | Responsibility |
|---|---|
| `core/combat/Model.kt` | `Stat`, `Side`, `FightEvent`, `Actor`, `FightSetup`, `FightResult`, outcome enums |
| `core/combat/Effects.kt` | `EffectDef` and its grammar: `Trigger`, `Condition`, `Cost`, `Action`, `Amount`, `Target`, `Limit` |
| `core/combat/Fight.kt` | The resolver: rounds, class actions, trigger queue, limits, retreat, objective |
| `core/combat/Highlights.kt` | Three highlights and the report sentence from real event IDs |
| `core/content/CombatContent.kt` | Class kits, affix and signature effects, enemies, relic effects, trait hooks |
| `core/content/GuildContent.kt` | Missions, routes, charters, recruit traits |
| `core/model/Guild.kt` | `GuildRunState` and everything inside it |
| `core/guild/GuildOps.kt` | Recruit, dismiss, loan, recall, reserve; candidates |
| `core/guild/Missions.kt` | Offers, plan, commit, stage resolution, checkpoint, aftermath |
| `core/guild/Loadout.kt` | Hero + weapon + relics -> `Actor` (the one place a hero becomes a combatant) |
| `core/guild/SiegeFight.kt` | Siege plan, defenders, the siege through the interaction engine |
| `core/guild/Stories.kt`, `GuildVisitors.kt` | Charter, nemesis, rival, bonds, branches, world laws, ranks; the guild's visitors |
| `core/sim/GuildSim.kt` | The nine guild policies and their metrics (`:core:guildsim`) |
| `core/sim/CombatSandbox.kt` | Text printout of fixture fights (`:core:combat`) |
| `app/.../ui/GuildPanel.kt`, `GuildUi.kt`, `ContractSheet.kt`, `BladeRulesUi.kt`, `CharterPicker.kt`, `FightReport.kt` | Guild destination, blade rules, loans, charter |
| `app/src/debug/.../ui/CombatSandboxScreen.kt` | The debug screen for the fixture fights |

## Task ledger

Status: `built` (compiles, unit-tested on the JVM), `sim` (also measured with the guild bots), `device` (also seen on an
emulator: Pixel_10_Pro AVD at 1080x1920, 420 dpi, font scale 1.0, one scripted pass, `tools/emulator/guild.sh`),
`partly` (see the note), `not built`. Nothing was run on a physical phone, with TalkBack, at font scale above 1.0, or by
a person playing.

| ID | Task | Status | Evidence, and what is missing |
|---|---|---|---|
| A01 | Design revision in GDD addendum, CLAUDE.md, DECISIONS | built | `docs/GDD_REVISION_2026-10-10.md`, DECISIONS (two sections) |
| A02 | New-run device scripts handle the opening offers | device | `smoke.sh` and `runend.sh` ran on a guild run; `NavigationFlowTest` and `ShopDayPersistenceTest` (7 tests, fixtures fixed) pass on the emulator. The rest of `connectedDebugAndroidTest` was not run |
| A03 | Combat vocabulary and validation rules | built | `combat/Effects.kt` (`EffectRules.problems`), `CombatEngineTest` |
| A04 | Resolver and event timeline | built | `CombatEngineTest`: determinism, termination, parent/root links, loop guard |
| A05 | Class kits and weapon mapping | built | all five kits; `guild/Loadout.kt`; the breadth test (every class, family, element, affix, catalyst, signature) |
| A06 | Stormwell and Scrap Choir | built | positive, boundary, ablation and loop fixtures in `CombatEngineTest` |
| A07 | Text sandbox, debug screen | device | `:core:combat`, output in `docs/guild_evolution_evidence/combat_fixtures.txt`; the debug Combat sandbox opened on the emulator with the Stormwell fixture. **The owner has not looked at it: the gate "is it fun to watch" is open** |
| B01 | Guild aggregate, recruitment, invariants | sim | `GuildRunTest`, `Invariants.guild` |
| B02 | Loan, recall, reservation guards | sim | `GuildRunTest` (listed, promised, loaned twice, away) |
| B03 | Offers and the one/two-day deployment lifecycle | sim | `GuildRunTest` (one day, two days, checkpoint default and push) |
| B04 | Aftermath applied exactly once | sim | End Day retry returns the stored day; ledger balances |
| B05 | Supply, Hunt, Sabotage | sim | plus Escort, Relic delve, Rescue, Recovery, Work on the wall |
| B06 | Siege through the engine with selected defenders | sim | `GuildRunTest` (verdict, reserved member, party away) |
| B07 | Two existing visitors connected to the guild | built | festival tournament, heirloom heir (`GuildStoriesTest`) |
| B08 | Guild screen and forge-to-loan flow | device (partly) | Guild list, contract sheet, plan, member sheet seen. Loan from the forge result, the item sheet's Loan/Recall, Storage's "On loan", recruiting, reserving, the checkpoint card: JVM model tests only, not tapped on a device |
| B09 | Daily highlights and guild scenarios | device (partly) | contract card with highlights and the whole fight seen; the guild siege card seen with the verdict "The town held" (the other two verdicts: JVM test only). Six scenario saves; `guild_siege_eve` loaded and played on the device, the other five on the JVM only |
| B10 | Save/reload, schema 6 | sim | codec round trip every day in `GuildRunTest`; `smoke.sh` process-death check passed on a guild run (day 2, no party out). Process death with a party at a checkpoint: JVM only |
| C01 | Ranger and Duelist; effects on the autonomous path | partly | both kits built. Residents' own expeditions still use the scalar resolver (spec 15.5 allows it for the prototype, not for a release): the blade card says where its rules apply |
| C02 | Rescue, capture, item recovery | sim | `GuildStoriesTest` |
| C03 | Three faction behaviours; Boiling Point, Blood Bank, Icebreaker | built | units and rules exist and fire in the breadth test; Blood Bank and Icebreaker have fixtures; Boiling Point (Steam) has no fixture of its own and no bot builds it |
| C04 | Relic pool to twelve; first signature rules | built | 4 + 8 relics; six signatures with a rule. Drafts do not look at the party's tags (spec 9.3); no secondary imprint (7.2); no mastery stamp (7.5) |
| C05 | Mission-linked visitors, bounded cadence | partly | four guild visitors (twelve scenes in all). No scheduler for cadence, no recovery choice after a lost siege (11.2). Eight of the spec's named scenes are not built |
| C06 | Charter victory, retire or continue, claim once | built | `GuildStoriesTest`. The charter siege was not played on a device |
| C07 | Schema transition and legacy mapping fixtures | built | `GuildMigrationTest` on seven saves written by 0.7.0 |
| C08 | Guild bots and balance report | sim | DECISIONS, `docs/guild_evolution_evidence/guildsim_balance11.txt`. No human playtest |
| C09 | Readability pass and Android verification | partly | one emulator pass at one size. No large font, TalkBack, reduced motion, small-screen or phone check of the new screens |
| D01 | One rival, one nemesis | built | nemesis: `GuildStoriesTest`. The rival's test only checks its schedule and that its list is bounded |
| D02 | Bonds and deed-driven branches | built | `GuildStoriesTest` |
| D03 | World laws and Guild Ranks | built | `GuildStoriesTest`; no law or rank was seen on a device |

## Review focus (inputs the spec implies and no single task owns)

1. A member dies, retires or is dismissed while carrying a loan, while reserved, while planned for a deployment and
   while named by an open visitor: the loan has one explicit outcome and no stale ID is left anywhere.
   Held by `GuildOps.reconcile` and `Invariants.guild` over every bot day; a member named by an open visitor is not
   separately tested.
2. End Day is retried with the same command ID in the middle of a two-day mission: nothing resolves twice.
   Tested for a one-day contract; the retry path is the same stored-resolution return.
3. A classic schema-5 save with an open visitor, a promised blade and three relics loads, plays and never grows a guild.
   `GuildMigrationTest`.
4. A relic is replaced while a party is away: the committed fight keeps the rules it left with. `GuildStoriesTest`.
5. The whole party is lost on a day the siege falls due: the siege resolves with whoever is left and the run can end.
   Reached by the greedy bot in `GuildRunTest`'s sweep; no test names it.

## Open questions for the owner

1. **Is the fight worth watching?** (the spec's gate after Milestone A). Read `docs/guild_evolution_evidence/combat_fixtures.txt`
   or open "Combat sandbox (debug)" on the title screen.
2. **Is the five-day opening fun?** (the gate after Milestone B). Everything after it was built without that answer.
3. **How hard should the charter be?** Coherent play secures it in 76 to 81% of bot runs under Town's Last Hope against
   the spec's 40 to 65%. The levers and a steeper row are in DECISIONS. Not retuned.
4. **Should day 5 bite?** The first siege leaves the forge at 94 to 100 for every active policy.
5. **Should a recovery be easier than the hunt that lost the blade?** Recoveries are won 16 to 40% of the time.
6. **Residents' own fights.** They still use the old resolver. Moving them to the new engine changes every classic
   number, so it was left for a decision.
7. **The Guild screen is long.** The board is first and the wall moves up near a siege, but it is one scroll with
   seven sections. Tabs inside Guild, or a shorter wall card, are the obvious next step.
8. **Merge.** The work is on `main` as 0.8.0, pushed. To play the old game, choose "No charter".
