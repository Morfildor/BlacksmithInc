# Gameplay depth: plan and ledger (2026-10-10)

Implements delivery step 3 of `BlacksmithInc_Gameplay_Review_2026-10-10.md` (the report; rationale lives there, not
here): 8 morning encounters, 4 run relics, 2 forecast siege traits, 1 follow-up chain, with UI, migration, scenarios,
simulator play, tests and docs. Everything numeric below is PROVISIONAL and lives in `BalanceConfig`.

## Base
- Branch `gameplay-depth`, worktree `.claude/worktrees/gameplay-depth`, cut from `post-0.7.0` @ f1e5b3c (0.7.0 + bulk
  Scrap / balance 9, worn wording, day-one champions, eight debug scenarios and their menu). `main` @ ea01669 lacks
  those four commits. The UI/UX review is uncommitted docs on `main` only; it owns no source file.
- Differences from the report's baseline: balance is 9, not 8; scenario saves exist and are extended, not created.
- Not built here: remaining catalogue, ventures/recovery, rival smith, seasons, tiers, catalysts, Never listed, UI redesign.

## Versions and migration
| Number | From -> to | Why |
|---|---|---|
| Save schema | 4 -> 5 | New `RngStream.ENCOUNTERS` (appended; other streams' seeds unchanged). Run step 4 stamps it from the stored seed with the `RngState.seeded` formula; legacy step 4 is `{ it }`. All other new fields are defaulted. |
| Rules | 3 -> 4 | Siege trait and committed besieger; three automatic events become encounters. Golden `state_rules4.txt` recorded. |
| Content | 3 -> 4 | Encounter, relic and siege-trait definitions join `ContentCatalog` and its `validate()`. |
| Balance | 9 -> 10 | One new nested group `DepthConfig` (one constructor slot). |

A run in progress continues: no encounter until its next morning, no trait until the siege after next is scheduled,
the starter relic draft at its next morning (never a retroactive reward). A pending blessing and a saved shop-day
position are untouched. `collector`, `wandering_master` and `merchant_festival` stay in `WorldEvents.all` (old counters
must still validate) but can no longer fire by themselves; their encounters share the same counters and limits.

## Shared rules
- One morning slot from day 2. Priority: a due follow-up, else one eligible encounter by weight with
  `encounterChance` 0.75 (a pending relic or blessing choice does not keep visitors away; a visitor is a card, not a modal). Generated at the end of End Day (`newMorning`) on
  the ENCOUNTERS stream, with every random part of the offer stored in the instance. Reading never draws.
- `Command.ResolveEncounter(instanceId, optionId, commandId)`: validated against current state, applied once; the same
  command ID again is accepted with no effect, another is `EncounterNotOpen`. Allowed only in planning (session stage DONE).
- Expiry: at the top of the next End Day an open offer takes its stated default (always the free option) and a record is written.
- A subject that is gone (hero dead or retired, blade sold, scrapped, donated, owned) disables the options that need it
  with a reason; if no paid option remains valid the card says so and only the free option commits.
- Kept: the instance of the day and a log of the last 30 resolutions (`EncounterRecord`).

## Encounters (eight, plus the chain's second stage)
| ID | Eligible when | Options (cost -> effect) | Default | Subjects |
|---|---|---|---|---|
| `last_crate` | siege in <= 3 days; the leading faction's weakness has an augment | A: 3 of that augment at 80% price. B: 3 of the cheapest core at 70%. C: pass | C | faction as it stands (stated as contingent until the first warning commits it) |
| `blade_for_the_wall` | an open commission slot; a living hero with no blade or a worn one and a thin purse | A: accept their order at half reward, due by the siege (`WALL_PLEDGE`), starts the chain. B: a FINE order from the richest free hero at 125%. C: decline both | C | two real heroes |
| `masters_afternoon` (was `wandering_master`, at most 2 a run like the event) | an OBSERVED journal key, or a signature with a rung left | A: 4 energy -> the named key becomes UNDERSTOOD. B: 60 gold -> the next clue rung of the named signature. C: keep working | C | journal keys, picked at offer |
| `collectors_offer` (was `collector`, at most 2 a run like the event) | a blade in the shop with fame > 0 or EPIC+ | A: sell it now for asking x collector multiplier (+1 reputation, leaves the town). B: keep it for local buyers | B | one exact blade; never a hero's |
| `cracked_family_blade` | a RECOVERED blade in storage whose fallen holder has a living lineage-mate, guildmate or, failing both, a regular in town when the holder fell, not named on an open order; a slot | A: 2 energy + 1 of its core -> restored to condition 100 and bound to an `HEIRLOOM` commission for that exact blade at half asking, due in 3 days. B: sell to a collector at asking. C: keep it | C | blade + heir + the fallen |
| `crooked_merchant` | day >= 4 | A: buy unseen at 55% of a clean blade's price, never below the supplier price of its core and augment (family, core and "one flaw" disclosed). B: 15 gold to inspect: fixed stats shown, card stays open with buy / leave. C: leave | C | a blade generated at offer, minted only if bought |
| `smiths_wager` | day >= 3; no wager running | A: stake 40 gold: forge the named family at SUPERB+ within 3 days (a blade forged here; a bought one does not count) -> a relic offer (double stake back if none is left); stake lost otherwise. B: an ordinary DECENT order from a free hero. C: refuse | C | blades forged after acceptance (by serial) |
| `festival_contract` (was `merchant_festival`) | always | A: 25 gold stall fee -> festival crowd at the shop today. B: the town pays 30 gold for each of the next 2 blades given to the watch within 2 days. C: decline | C | none |
| `debt_repaid` (chain stage 2) | scheduled only | see the chain | free option | pledge hero + blade |

Costs and rewards scale from existing prices where shown as percentages; fixed numbers are config.

## Chain: the wall pledge
1. `blade_for_the_wall` option A records a pledge (hero, commission).
2. Commission completed -> a `ScheduledConsequence` due the morning after the next siege, naming hero and blade.
   Commission expired -> the pledge lapses with a record; nothing later.
3. Due morning, read from the simulation, never invented: hero dead or retired -> a record naming them and where the
   blade is, no reward; hero no longer carries the blade -> lapses with a record; otherwise `debt_repaid` takes the slot,
   its text saying whether they stood on the wall and whether the town held. Options: take the unpaid half (at most what
   the hero holds); forgive it (hero becomes a regular, +2 militia); and, only if they held the wall in a won siege, have
   them speak for the forge (+2 reputation).

## Relics (four; three slots)
Run-long, reset with the run, never stacked twice. Blessings stay as they are: five days, a reward for a won siege.
| ID | Trigger and cap | What the player does differently | Accounting |
|---|---|---|---|
| `salvagers_crucible` | Once a day, `Salvage` of a blade of quality >= `rareMin` also returns its augment | Melts a disappointing fine blade to try the same recipe again | Normal salvage energy; catalyst never returned; bulk `Scrap` excluded. No gold is created: a retry costs the forge and salvage energy each time |
| `tempering_ledger` | Each forge of a family not in the current streak extends it: +2 quality per step after the first, at most +6; forging a family already in the streak restarts it at that family | Rotates families instead of repeating the best one | Streak is run state on the relic; repeats stay legal |
| `collectors_seal` | The first shelf sale of a day whose cash paid is >= 100 gold and >= the going rate earns a seal; the third seal becomes 1 unit of a limited material (tier 3+, the one held least, ties by ID) | Prices fewer, better blades high instead of clearing stock | Counts coin a hero actually paid (after trade-in and stipend); commissions, the collector and the merchant never count |
| `ashen_bellows` | Once a day a forge may be marked `bellows`: +1 affix slot | Spends tomorrow on today's best blade | Adds 2 to `overworkToday`, the existing debt `newMorning` collects; rejected if the 4-point overwork budget cannot hold it, so it competes with ordinary overwork |

- Offers (`pendingRelicOffer`, drawn on ENCOUNTERS): 3 of 4 on day 1 as a separate first step; up to 3 not-owned relics
  after the 2nd and 4th siege, won or lost, while the forge stands; one from a won wager. Never for abandoning.
- `Command.ChooseRelic(relicId, replaceId?)`: with three owned, `replaceId` is required. `Command.DeclineRelicOffer` clears it.
- Replacement drops the old relic's seals or streak. Bellows debt already taken stays in `overworkToday`. A used daily
  charge is not refunded by re-acquiring the relic the same day (use day is kept in the log of used charges).

## Siege traits (two)
The trait of a siege is drawn when that siege is scheduled (at run start for none: the first siege is plain; after
each siege for the next), so it shows for the whole five-day window. The besieger is committed at the first warning
evening (2 days out) and no longer follows later pressure changes: a rule change the report proposes, adopted here.
Both live in `Battle.outlook`, which the forecast and the resolver already share.
| ID | Rule | Responses |
|---|---|---|
| `long_assault` | Blade wear counts double in defense (condition floor x0.6) and the siege wears champions' blades twice as much | Sell or commission fresh blades so champions replace worn ones; arm the watch and rely on militia, which do not wear |
| `many_breaches` | Watch armory and militia count x1.75; the raid is 5% stronger | Give blades to the watch (cheap ones count); or out-arm the raid through the champions as usual |

## Code map
- `:core` new: `content/Depth.kt` (EncounterDef, RelicDef, SiegeTraitDef + launch entries), `engine/Encounters.kt`
  (eligibility, offer, resolve, expiry, chain), `engine/Relics.kt`. Model: `EncounterInstance`, `EncounterOption`,
  `EncounterRecord`, `ActiveRelic`, `ScheduledConsequence`, `SiegeScenario`, `Commission.weaponId`, `Weapon.promisedTo`, two `CommissionKind`s,
  new `EventType`s, `GameState` fields (all defaulted). Hooks: `newRun`, `newMorning`, top of `endDay`, after
  `resolveSiegeIfDue`, `Forge.apply`, `salvage`, `donate`, `Market.purchase`, `Commissions.pick`, `Battle.outlook`,
  `Invariants`, `ResolutionContext`, `SaveCodec`.
- `:app`: `GameSession.planningOpen`, `GameViewModel` (describe, dismiss/reopen), `ui/EncounterSheet.kt`, relic row and
  dialog, Forge bellows switch, Town threat line, End Day note, shop lead, Storage salvage note.
- Sim: `EncounterPref` (DECLINE, FIRST, CASH, DEFENSE, ADAPTIVE), relic preference, `--encounters`, `--relic`, a depth summary.
- Scenarios: nineteen more saves (twelve were planned) through `ScenarioSaves` / `:core:scenarios`.

## Ledger
| # | Task | Verify | Status |
|---|---|---|---|
| 1 | Schema 5, ENCOUNTERS stream, state fields, version bumps, fixtures and pins | `:core:test` green; old fixtures admit; golden re-recorded | done |
| 2 | Slice A: `collectors_offer` end to end (core, session gate, sheet, save, reload) | core tests: deterministic offer, idempotent choice, expiry default, sold-subject fallback; app test over the session | done |
| 3 | Slice B: `ashen_bellows` + relic offer and choice (core + UI) | charge, overwork interaction, replacement tests | done |
| 4 | Slice C: `long_assault` + scenario commitment (core + Town line) | forecast equals resolution; besieger fixed after warning | done |
| 5 | Remaining 7 encounters, automatic-event conversion | every entry reachable from a fixture; no double payout | done |
| 6 | Remaining 3 relics, `many_breaches` | limits, reset, no profit loop | done |
| 7 | Chain: pledge -> consequence -> `debt_repaid` | continuation, death, hand-over, expiry | done |
| 8 | UI complete: sheet states, relic row, End Day note, leads, large font | app unit tests; emulator at 1.0 and 1.3 font scale | done; emulator at 366 dp wide, font 1.0 and a short pass at 1.3 |
| 9 | Simulator preferences and metrics | policy tests; 200-seed sweeps | done |
| 10 | Debug scenarios (12) + `scenarios.sh` | `ScenarioSavesTest`, `ScenarioAssetsTest` | done: 19 scenarios |
| 11 | Balance: tune on small sweeps, then the 10,000-seed gate against the balance 8/9 tables | numbers in DECISIONS; unmet bands named | done: numbers in DECISIONS; two bands not met, owner decision |
| 12 | Full suites, lint, debug + release builds, emulator pass, docs, debug APK | commands and results in PROGRESS | done except smoke.sh, runend.sh and the instrumented tests (not updated for the day-1 relic offer, not run) |

Order: 1, then 2-4 by one owner (engine, model, persistence). After 4 the interfaces are fixed and 8, 9 and 10 can run
beside 5-7 without sharing files.

## Risks to watch
- Balance comparability: removing three events from the automatic pool changes the EVENTS draw sequence, so a seed no
  longer replays its balance-9 run. Comparison is by distribution over the same seed set, not run by run.
- Bound heirloom blade (`Weapon.promisedTo`): every path that moves a blade (shelf, salvage, scrap, donate, another commission) must refuse it.
- A catalog without the depth lists, with `depth.commitBesieger` off, plays as rules 3 did: the matched "without" baseline for the simulator (`--noDepth`).
- `BalanceConfig` is at the 255-slot limit: exactly one new constructor field.
- `ConstantsTest` scans `Battle.kt`, `Market.kt`, `WorldEvents.kt`: new numbers there come from config only.
