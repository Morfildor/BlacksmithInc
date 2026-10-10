# Guild evolution: implementation plan and task ledger

**Goal:** turn the shop into the forge of a small adventurers' guild: contracted heroes take loaned blades on missions
the smith chooses, fights are resolved by a short automatic interaction engine (effects that trigger, convert and
consume each other), and what happens to the party sets up the next day's decisions.

**Spec:** `docs/GUILD_EVOLUTION_SPEC.md` (the owner's plan of 2026-10-10, copied verbatim). This file says how it is
built here and what is done. Section numbers below ("spec 6.4") point into the spec.

**Baseline:** `main` @ `8f2e284` (0.7.0 plus the notice banner): rules 4, save schema 5, content 4, balance 10.
**Branch:** `guild-evolution`, worktree `.claude/worktrees/guild-evolution`. Not merged into `main` by this work.

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
- No commit to `main`, no push of `main`, without the owner.

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
| `core/guild/Guild.kt` | Recruit, dismiss, loan, recall, reserve; candidates |
| `core/guild/Missions.kt` | Offers, plan, commit, stage resolution, checkpoint, aftermath |
| `core/guild/Loadout.kt` | Hero + weapon + relics -> `Actor` (the one place a hero becomes a combatant) |
| `core/guild/SiegeFight.kt` | Siege plan, defenders, the siege through the interaction engine |
| `core/guild/Stories.kt` | Mission-linked visitors, nemesis, rival, bonds, world laws, ranks |
| `core/sim/GuildPolicies.kt`, `GuildReport.kt` | The nine guild policies and their metrics |
| `core/sim/CombatSandbox.kt` | Text printout of fixture fights (`:core:combat`) |
| `app/.../ui/GuildPanel.kt`, `GuildUi.kt`, `MissionSheet.kt`, `PartySheet.kt`, `FightReport.kt` | Guild destination |
| `app/.../ui/debug/CombatSandbox.kt` (debug source set) | The debug screen for the fixture fights |

## Task ledger

Status: `todo`, `built` (compiles, unit-tested), `sim` (measured in the simulator), `device` (seen on an emulator).
Evidence is the test class or the table that shows it.

| ID | Task | Status | Evidence |
|---|---|---|---|
| A01 | Design revision in GDD addendum, CLAUDE.md, DECISIONS | todo | |
| A02 | New-run device scripts handle the opening offers | todo | |
| A03 | Combat vocabulary and validation rules | todo | |
| A04 | Resolver and event timeline | todo | |
| A05 | Class kits and weapon mapping | todo | |
| A06 | Stormwell and Scrap Choir | todo | |
| A07 | Text sandbox, debug screen | todo | |
| B01 | Guild aggregate, recruitment, invariants | todo | |
| B02 | Loan, recall, reservation guards | todo | |
| B03 | Offers and the one/two-day deployment lifecycle | todo | |
| B04 | Aftermath applied exactly once | todo | |
| B05 | Supply, Hunt, Sabotage | todo | |
| B06 | Siege through the engine with selected defenders | todo | |
| B07 | Two existing visitors connected to the guild | todo | |
| B08 | Guild screen and forge-to-loan flow | todo | |
| B09 | Daily highlights and guild scenarios | todo | |
| B10 | Save/reload, schema 6 | todo | |
| C01 | Ranger and Duelist; effects on the autonomous path | todo | |
| C02 | Rescue, capture, item recovery | todo | |
| C03 | Three faction behaviours; Boiling Point, Blood Bank, Icebreaker | todo | |
| C04 | Relic pool to twelve; first signature rules | todo | |
| C05 | Mission-linked visitors, bounded cadence | todo | |
| C06 | Charter victory, retire or continue, claim once | todo | |
| C07 | Schema transition and legacy mapping fixtures | todo | |
| C08 | Guild bots and balance report | todo | |
| C09 | Readability pass and Android verification | todo | |
| D01 | One rival, one nemesis | todo | |
| D02 | Bonds and deed-driven branches | todo | |
| D03 | World laws and Guild Ranks | todo | |

## Review focus (inputs the spec implies and no single task owns)

1. A member dies, retires or is dismissed while carrying a loan, while reserved, while planned for a deployment and
   while named by an open visitor: the loan has one explicit outcome and no stale ID is left anywhere.
2. End Day is retried with the same command ID in the middle of a two-day mission: nothing resolves twice.
3. A classic schema-5 save with an open visitor, a promised blade and three relics loads, plays and never grows a guild.
4. A relic is replaced while a party is away: the committed fight keeps the rules it left with.
5. The whole party is lost on a day the siege falls due: the siege resolves with whoever is left and the run can end.

## Open questions for the owner

Kept current at the end of the work; see `docs/PROGRESS.md` for the state.
