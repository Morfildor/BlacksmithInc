# 02 — Code correctness: `:core` and the session boundary

Reviewer lens: correctness of the pure engine (`core/src/main/kotlin/com/tinyblacksmith/core/`) and of the `:app` session
boundary (`GameSession.kt`, `GameViewModel.kt`, `data/`). Method: the `code-review` skill was started over
`ea01669..HEAD` but hit the session limit before producing output; everything below is from reading the files named in
the brief end to end, tracing the paths by hand, and the greps quoted under each finding. Nothing was run on a device.
Tree: `main` at a2e0392.

## Summary (10 lines)
1. The engine is in good shape: End Day follows GDD 3.2 in order, every accepted command re-asserts the invariants, End Day
   and the visitor answer are idempotent per command ID, every guild command is declarative ("the same command twice changes
   nothing"), and all randomness goes through named SplitMix64 streams. No `System.*`, `Random`, locale formatting or
   order-dependent `HashMap` iteration exists in `core/main` outside `sim/`.
2. Two confirmed defects: rank 3's "contracts stay on the board a day less" is dead code (a default parameter shadows the
   rank-aware one), and a planned Recovery contract plus the dealer's "Buy it back" answer trips an invariant, so the
   answer is refused with a generic error and the fee stays paid.
3. Two plausible medium risks at the boundary: the Shop/Guild screen models are built inside the `ui` flow without the
   guard the shop-day script has (a throw = crash at every launch), and `Compatibility.admit` refuses a save whenever a
   balance change lowers a cap (armory, relic slots, overwork, member count).
4. Three low items: a law's repair percent applies to tonight's wear the evening it is announced; a lost cursor write on
   Abandon can survive into a same-ID debug run; three stale comments (Abandon and the legacy row, `MissionOffer.lapse`,
   a no-op line in `recruit`).
5. All eleven findings of the 2026-10-09 review that touch this lens (F01–F11) are fixed on this tree; F10 is fixed as
   far as its own text asked (unsold property still accumulates, with Scrap as the valve).
6. Determinism, dead-hero ownership, member isolation and save/load symmetry were checked path by path; the notes at the
   end say what was verified so the next reviewer need not repeat it.

## Ranked findings

| # | Sev | Status | Defect | Action | Evidence |
|---|-----|--------|--------|--------|----------|
| 1 | medium | CONFIRMED | Rank 3 ("The Last Company") promises contracts leave the board a day sooner; the rank's `offerLifeDelta` is never applied because the local `post()` in `refreshBoard` has its own default for `expires` and always passes it to `generate`. | Drop the default from `post` (or compute it with the rank delta there) and add a test: rank 3, an ordinary offer, `expiresDay == day + lifeDays - 2`. | `core/.../guild/Missions.kt:55` (default `expires = ctx.day + def.lifeDays - 1`), `:56` (always passed), `:138` (the rank-aware default that is shadowed), `core/.../content/GuildContent.kt:471` (the promise, `offerLifeDelta = -1`) |
| 2 | medium | CONFIRMED | Plan the Recovery contract (fee 10), then answer the dealer "Buy it back": `Stories.recovered` removes the offer the plan names, `Invariants.guild` reports "A party is planned for a contract that is not on the board", `accept` throws, the session answers `EngineFault` and the screen says "The forge cannot do that right now." The answer is impossible until the plan is cancelled; nothing tells the player; the fee stays paid meanwhile. | In `Stories.recovered` (and anywhere an offer is removed during planning) also drop a plan that names it and refund its fee as `cancelPlan` does; or block the option while the plan stands, as the "lead" option already special-cases the fee. Test: plan Recovery, answer buy, expect Accepted and the fee back. | `core/.../guild/GuildVisitors.kt:126-131`, `core/.../guild/Stories.kt:163`, `core/.../engine/Invariants.kt:114`, `core/.../engine/GameEngine.kt:552-555`, `app/.../GameSession.kt:195-201`, `app/.../GameViewModel.kt:450`, `core/.../content/GuildContent.kt:381` (fee = 10) |
| 3 | medium | PLAUSIBLE | `shopFor`/`planFor` build `shopUi`, `guildUi`, `encounterView`, `relicViews` and `siegeForecast` inside the `ui` StateFlow with no `try`; `scriptFor` beside it is guarded precisely because "the game would then crash at every launch". Any throw in those read-only views on a stored state (e.g. `content.guild!!.mission(offer.defId)`, `wallForecast(state)!!`) ends the flow and the app at every launch while that save exists. | Guard `planFor` like `scriptFor` (a minimal `Plan` plus a notice) and add a ViewModel test with a throwing builder. | `app/.../GameViewModel.kt:307-322` (the guarded one), `:326-336` (unguarded), `:250-252` (the flow), `app/.../ui/GuildUi.kt:187,202,237,340` |
| 4 | medium | PLAUSIBLE | Admission runs the whole invariant set against the installed config, so a balance change that lowers any cap (`armoryMax`, `depth.relicSlots`, `maxOverworkPerDay`, `heroMaxLevel`, `guild.maxMembers`, `guild.defenders`, `championCount`) turns every existing save into `SaveFailure.Incompatible` and the recovery screen ("Start over"). The GDD wants runs to continue under new balance. | Split admission: refuse only structural faults (unknown content, dead owners, duplicate IDs); clamp config-bound counts on admit (and say so in the first Gazette). Test: a save at the old `armoryMax` opened under a lower one is Admitted. | `core/.../engine/Compatibility.kt:25-29`, `core/.../engine/Invariants.kt:27,30,55,72,94,108`, `app/.../GameSession.kt:170-174` |
| 5 | low | PLAUSIBLE | A law of the season is announced "from tomorrow" (`fromDay = day + 1`), and `setup` gates it on `day in fromDay until untilDay`, but the fracture wear in `aftermath` reads `law(ctx.guild?.law?.id)?.repairPercent` ungated: tonight's crack already costs the announced law's repair percent. | Gate the read as `setup` does (one shared helper). | `core/.../guild/Missions.kt:246` (gated), `:352` (ungated), `core/.../guild/Stories.kt:211` |
| 6 | low | PLAUSIBLE | On Abandon the cursor row is cleared outside the run+legacy transaction and a failed clear is swallowed. A stale cursor names `"<runId>:day<N>"`; the debug seeded run reuses the run ID, so `pending()` can read DONE for an unwatched day N and skip its counter. Production seeds come from `System.nanoTime()`, so only debug builds reach it. | Write the cursor inside `saveBoth` (one transaction for run, legacy and cursor). | `app/.../GameSession.kt:214-219`, `:300-306`, `app/.../data/SaveStore.kt:52-56`, `app/.../GameViewModel.kt:469,752-755` |
| 7 | low | CONFIRMED | Three comments contradict the code: `Op.Abandon` says "the legacy row stays as it was before the run", but every accepted command writes the run's legacy copy to the legacy row, so journal progress of an abandoned run stays (DECISIONS records this as the chosen behaviour, and the Abandon dialog says so); `MissionOffer.lapse` says an untaken supplier's road leaves the supplier to the road, but ruling R1 and the code apply it only to a lost cart; `recruit` carries a no-op `championIds` copy. | Fix the two comments; delete the line. | `app/.../GameSession.kt:50` vs `:239`; `docs/DECISIONS.md` "the Abandon dialog now says that journal discoveries stay"; `core/.../model/Guild.kt:110` vs `core/.../guild/Missions.kt:300` and `docs/RUN_VARIETY_LEDGER.md:82`; `core/.../guild/GuildOps.kt:218` |

## Details

### 1. Rank 3's offer life (CONFIRMED)
`Missions.generate` has a default `expires` that subtracts the rank's `offerLifeDelta` (`Missions.kt:138`). `refreshBoard`
never lets it run: its local `post(def, faction, hero, weapon, reason, expires = ctx.day + def.lifeDays - 1, modifier, lapse)`
(`:55`) passes `expires` on every call (`:56`). The standing work, the siege jobs and the rescue pass their own values, so
the only offers the delta was written for (the ordinary draws at `:92-98` and the supply card at `:66`) get the plain
default. `grep -rn offerLifeDelta core/src/main` shows exactly the def field, the content row and the shadowed default; no
test asserts an offer's `expiresDay` under rank 3 (`RunVarietyTest.kt:374-375` checks the enemy percent only).

### 2. A plan for a contract the morning removes (CONFIRMED)
Order of play: morning board holds Recovery `R` (`subjectWeaponId` = the nemesis blade) and the dealer visits (eligible
while no mission names the blade, `GuildVisitors.kt:45`). `PlanDeployment(R, …)` is accepted and takes the fee
(`Missions.kt:185`). `ResolveEncounter("buy")` then runs `Stories.recovered` (`GuildVisitors.kt:130`), which filters
`offers` by `subjectWeaponId != weaponId` (`Stories.kt:163`) and leaves `planned` as it was. `accept` checks the invariants
(`GameEngine.kt:548`), `Invariants.kt:114` fails, `check` throws, `GameSession.execute` turns the exception into
`Result.EngineFault` and writes nothing (`GameSession.kt:195-201`), and the ViewModel shows the generic sentence
(`GameViewModel.kt:450`). The state stays sound (the invariant did its job); the cost is a dead end with no explanation and
a fee held until the player finds `CancelPlannedDeployment`. The "lead" answer on the same visitor already special-cases a
planned offer (`GuildVisitors.kt:135`), so the authors saw the pairing once. The same shape exists for the rival's morning
pick, but `commit` has set `planned = null` before any morning runs (`Missions.kt:216`), so that one cannot happen.

### 3. Unguarded screen models in the `ui` flow (PLAUSIBLE)
`ui = combine(...).mapLatest { … scriptFor(snap); render(…, shopFor(snap), …) }.stateIn(viewModelScope, Eagerly, …)`
(`GameViewModel.kt:250-252`). `scriptFor` catches everything but cancellation and marks the day watched (`:313-321`).
`shopFor` → `planFor` (`:326-336`) does not. `planFor` calls `engine.guildUi`, `engine.shopUi`, `engine.siegeForecast`,
`engine.encounterView`, `engine.relicViews`; the first two dereference content by stored IDs (`GuildUi.kt:187,202,340`) and
`wallForecast(state)!!` (`:237`). Today those IDs are guarded by `Compatibility.admit` (unknown content is refused on load)
and `refreshBoard` drops offers whose def is gone (`Missions.kt:51`), so no stored save is known to throw. The risk is any
future exception in a read-only view: an uncaught throw inside `mapLatest` completes the StateFlow exceptionally and the
`viewModelScope` dies, at every launch, with the save intact and unloadable from the player's side.

### 4. Admission refuses saves over balance caps (PLAUSIBLE)
`Compatibility.admit` appends `Invariants.check(state, config, shelfSlots, content)` to its problems and returns
`Unsupported` on any (`Compatibility.kt:27-28`); `GameSession.admitRun` turns that into `SaveFailure.Incompatible`
(`GameSession.kt:173`) and the recovery screen. Of the checks, these read the installed config rather than the save:
overwork bound (`Invariants.kt:27`), armory (`:30`), hero level (`:55`), champion count (`:58`), relic slots
(`:72`), member count (`:94`), wall reservations (`:108`). A balance bump that lowers any of them (the simulator's
`--set` sweeps change them routinely) strands every live run, contrary to "a run from older balance continues forward
under the installed ones" (`Compatibility.kt:9-11`). Not reproduced with a real bump; the path is direct.

### 5–7. See the table. For 6, the production seed is `System.nanoTime() xor eras.size` (`GameViewModel.kt:469`), so the
collision needs the debug seeded start (`startSeededRun`) after a failed cursor delete; the fix is cheap and removes a class
of split-write risks the rest of the session was built to avoid.

## Prior findings (2026-10-09 review, section 5)

| Item | Then | Now | Evidence |
|---|---|---|---|
| F01 legacy ops race | P1 | fixed | one mutex across read, compute, write, publish; `Claim`/`BuyUpgrade`/`BeginEra` are ops of the same boundary (`GameSession.kt:75-95,194-224`) |
| F02 no recovery path | P1 | fixed | typed `SaveFailure`, `Status.Failed`, retry, `startOverKeepingBackup` (`GameSession.kt:107-124`, `data/GameRepository.kt:15-23`) |
| F03 report omits prep events | P1 | fixed | `dayEvents = ctx.events.filter { era, day }` before compaction (`GameEngine.kt:450`) |
| F04 commission band vs rule | P1 | fixed | band floors (`market/Commissions.kt:16-26`), migration of open orders (`SaveCodec.kt:165-177`) |
| F05 ID-order customer bias | P1/P2 | fixed | one willingness draw per resident, seats drawn by weight, waiters first (`Market.kt:46-73`) |
| F06 shelf sells commission stock first | P2 | fixed | commissions resolve before shelf visits (`GameEngine.kt:421-423`); heirlooms are `promisedTo` (`Invariants.kt:74-79`) |
| F07 tallies disagree | P2 | fixed | typed `ShopLedger` from `ctx.income` kinds (`GameEngine.kt:446-449`, `ResolutionContext.kt:96-99`); collector is a recorded visit (`WorldEvents.kt:105`); fatal fights are `FieldOutcome.DIED` (`Battle.kt:100`) |
| F08 champion ranking ignores warlord | P2 | fixed | `selectChampions(ctx, faction, warlord, config)` (`Battle.kt:213-218,274`) |
| F09 versions not enforced | P2 | fixed | `handle` rejects other rules/content (`GameEngine.kt:119`); `Compatibility.admit` stamps and refuses (`Compatibility.kt:21-30`); `balanceVersion` stored |
| F10 save growth | P2 | fixed as asked | `EventCompaction` with routine window, `CommissionPruning`, `WeaponHistoryCompaction` (combat and everyday), `ProcessedCommands.trim(30)`, `WeaponPruning` (`GameEngine.kt:463-470`); unsold property still grows, with `Command.Scrap` as the valve |
| F11 locale formatting | P2 | fixed | `grep -rnE 'String\.format|\.format\(|toLowerCase|Locale' core/src/main` outside `sim/`: no match |

## Verified sound (so the next pass can skip it)
- End Day order (`GameEngine.kt:405-473`) against GDD 3.2 steps 1–10: lock/snapshot (streams open lazily), commissions
  then customers, equipment inside purchases, party then activities then members at home, factions/events/warning,
  siege, ambitions/retirements/reconcile/recover, blessings, Gazette and compaction, new morning. `Battle.warnOfSiege`
  runs after the daily commission draw, so the evening the besieger is committed the request draw still reads the leader;
  they are the same faction on that evening.
- Idempotence: End Day by stored `DayResolution` and the newest 30 IDs (`:406-410`); visitor answers by command ID on the
  instance (`:195-200`, the "inspect" answer keeps the instance open and re-stamps the ID, so a repeat of it is a no-op);
  every guild command returns null for "already so" (`GuildOps.kt:210,236,253,266`, `Stories.kt:93,131`,
  `GuildOps.kt:139`), and the session writes nothing when the state is equal (`GameSession.kt:238`). The session's
  `launch` guard is sound on `Main.immediate`: `Working` is set before the first suspension.
- Invariants after every accepted command and on `newRun` (`:114,548`); exceptions from the engine never reach the UI
  unhandled through the session (`GameSession.kt:194-201`).
- RNG: `CRAFTING` (`Forge.apply`), `HEROES` (generation, activities, retirements), `PURCHASES` (willingness, seats,
  evaluations), `COMBAT` (expeditions, deaths, siege fight seed), `FACTIONS` (start), `EVENTS` (world events, rumours,
  commissions), `LEGACY` (blessing offer), `WORLD` (season), `ENCOUNTERS` (visitors, relics, siege trait), `GUILD`
  (candidates, board, prep, nemesis name, rival, law). Read-only paths draw nothing: `Forge.heartfireOutlook`, `Bench`,
  `ForgeOutlook`, `Encounters.view`, `SiegeFight.forecast`, `Battle.outlook`, `ShopDay.script`, `Demand` — grep of
  `.rng(`/`Rng(` across those files: no match. Fights are seeded from the stored stage seed or one `COMBAT` draw
  (`Missions.kt:159`, `SiegeFight.kt:213`). Chosen-order helpers (`Appearance.mix`, `Recognition.mix`) are fixed hashes
  of the run seed and IDs, not streams.
- Determinism of iteration: every `*.values` walk that decides something is sorted by `IdOrder.numeric` or is over an
  insertion-ordered map that round-trips JSON in the same order; `HashSet`/`HashMap` appear only for membership and
  per-key counters (`GameEngine.kt:466-470`, `Recognition.kt:39`, `Fight.kt:32,48-50`, `Missions.kt:133-135` which sorts
  before returning).
- Dead heroes: `Battle.kill` re-homes every `Owned` blade and drops the hero from the champions (`Battle.kt:161-193`);
  `Heroes.retire` passes blades to the mentee (`Heroes.kt:243-247`); captives past their day die through `kill`
  (`Missions.kt:485-494`); invariants catch the rest (`Invariants.kt:32-38,60-63`).
- Members and `residents()`: shelf visits, activities, arrivals, retirements, world-event picks, commission patrons,
  visitor subjects and candidates all take `residents()` (`Market.kt:55,388,503`, `Heroes.kt:47,207,224`,
  `WorldEvents.kt:207-288`, `EncounterCatalog.kt:29`, `GuildOps.kt:172`). Two `aliveHeroes()` uses touch members on
  purpose and are harmless: `resolveAmbitions` (fame/loyalty/reputation on a member's own deeds, `Heroes.kt:262`) and
  `guildHeir` (an NPC-guild mate inheriting a fallen comrade's blade, `Battle.kt:201`). One path by design, for the owner
  to confirm: a hero named on an open HEIRLOOM order is also made a standing candidate by the restore answer
  (`GuildVisitors.kt:177-189`); recruited, they are a member who collects a commission at End Day and is handed the blade
  as their own (`Market.kt:432`). It reads as intended ("Carries a family weapon the smith made whole").
- Save/load: migrations keyed by from-version, both tables complete through 6→7, `migrate` refuses a newer schema and
  `readRow` tells Newer from Corrupt (`SaveCodec.kt:59-91,180-188`, `GameSession.kt:182-186`); `encodeDefaults = true`
  and `allowStructuredMapKeys` cover every added field and the value-class map keys; the merchant bound in the invariants
  matches `resolveMerchant`'s last day (`Invariants.kt:42`, `Market.kt:397`); `WeaponPruning.terminalReasons` match the
  three strings that write them (`GameEngine.kt:361`, `EncounterCatalog.kt:263,285`, `WorldEvents.kt:435`,
  `Market.kt:398`).
- Day and siege arithmetic: blessing expiry (`chosen day + duration - 1`, active while `expiresDay >= day`, dropped when
  `expiresDay > day` fails) gives exactly `duration` days; the siege's two-day jobs leave by `nextSiegeDay - days` and
  return on the siege morning (`Missions.kt:78,227,481`); the wall pledge falls due the morning after the siege it was made
  for and reads that siege's record (`Consequences.kt:46,88`); the warning window and the "shopping under a warning"
  window are the same two days (`Battle.kt:388-409`).

## Owner decisions
- Finding 4: should a balance change that lowers a cap strand existing runs (today) or clamp them on admission? Trade-off:
  strictness and a simple invariant set versus never showing "Start over" for a tuning change.
- The heirloom heir who signs: keep the member collecting their order and owning the blade (story), or release the order
  on recruitment (the "members are not customers" line)? One function either way (`GuildOps.recruit` or `askable`).
