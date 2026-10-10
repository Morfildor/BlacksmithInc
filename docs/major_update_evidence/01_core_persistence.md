# 01 — Core / persistence review for the major update

Reviewer scope: review sections 4.3, 5 (F01–F03, F06–F11), 6.6, 9, 12; GDD 3.2, 11, 13, 15. Read-only; nothing in the
project was created, edited, built or run. No Gradle, so **no test was executed**; every claim below is from source,
`git`, or a read-only parse of the checked-in save fixture.

## 0. Repository state while this was written (it moved under me)

| When | `main` | Notes |
|---|---|---|
| Task start | `4ecbac0`, dirty (Signboard / affix edits) | as briefed |
| Mid-review | **`77919fe`**, clean | `d7b3283` "Balance v5 part 1" (the dirty edits, committed) + `26c66a0`/`77919fe` hero daily life (worktree `agent-a965…` merged; that worktree is now clean at `26c66a0`) |
| End of review | `77919fe` + **staged, uncommitted merge of `agent-a9c81…`** (replays + weapon fates) | conflicts in `BalanceConfig.kt`, `Gazette.kt`, `Model.kt`, `CHANGELOG.md` were being resolved; no conflict markers left in `core/src` or `app/src` when last checked |
| Still in flight | `agent-ac2ba6fe…` (legacy tracks), base `f73e5a1`, 11 dirty files | not merged |

**All `path:line` references below are against commit `77919fe`** (`git show 77919fe:<path>`), not the working tree.
Files not touched since the review snapshot `b39ad76` (so the review's own line numbers still hold): everything under
`core/.../engine`, `legacy`, `persistence`, `rng`, `crafting`, `battle/Power.kt`, and all of `app/src/main`
(`git diff --stat b39ad76 77919fe` shows only a 2-line in-place change in `Market.kt:22-23` among those).

Line shifts once the staged `a9c81` merge is committed (read from the staged tree):

| Symbol | `77919fe` | after `a9c81` |
|---|---|---|
| `Model.kt` `MarketVisit` / `DayResolution` / `GameState` / `lastResolution` | 179 / 182 / 243 / 271 | 206 / 209 / 270 / 298 |
| `Model.kt` new: `WeaponLocation.Lost.WITH_MERCHANT`, `WeaponFate`, `ReplayKind`, `CombatReplay(kind, eventId)` | — | 27 / 37 / 193 / 197 |
| `Battle.kt` `"%.2f".format` sites | 53, 56 | 56, 60 |
| `Battle.kt` `kill` / `selectChampions` / `outlook` | 95 / 117 / 141 | 138 / 187 / 211 (+ new `fightReplay` 99, `dayReplays` 118) |
| `GameEngine.kt` End Day: visits / commissions / activities / `dayEvents` / compaction / `newMorning` | 279 / 280 / 283 / 298 / 305-307 / 308 | 279 / 280 / **281 `Market.resolveMerchant`** / 284 / 299 / 306-308 / 309 |
| `Market.kt` `resolveCommissions` / `maybeOfferCommission` | 152 / 184 | 186 / 218 (new `resolveMerchant` 159); `resolveShelfVisits` 17, `evaluate` 54, `purchase` 110 unchanged |
| `BalanceConfig.kt` | `heroLife` 265 | + `weaponFates` 272, `WeaponFatesConfig` 308 |

## 1. Status table

| Finding | Status | One-line verdict |
|---|---|---|
| F01 upgrade / claim / new-era concurrency | **CONFIRMED** | Only `dispatch` is guarded. Four further defects the review did not list (1.1). |
| F02 load / decode / write failure paths | **CONFIRMED** (failure mode corrected) | Not a stuck `busy`: an uncaught exception in `viewModelScope` kills the process; a bad save is a crash loop. |
| F03 report vs archive event sets | **CONFIRMED**, re-verified on the fixture (20 vs 15, five `WEAPON_FORGED`) | Three more divergences (visits, compaction, tally wording). CHANGED-BY-INFLIGHT(a9c81): replays now `Battle.dayReplays(ctx)`. |
| F06 shelf visits before commissions | **CONFIRMED** | Plus: the commission takes the *highest-quality* match and ignores its shelf price. CHANGED-BY-INFLIGHT(a9c81): merchant resale inserted after commissions. |
| F07 money / encounter tallies | **CONFIRMED** | Plus: warlord tribute is counted as shop takings; material and tool spending carry no amount; the simulator's `goldEarned` shares the omission. |
| F08 champion ranking ignores warlord context | **CONFIRMED** | Also affects `town.championIds` (Town/Home). CHANGED-BY-INFLIGHT(a9c81): line shift only. |
| F09 version recording / enforcement | **CONFIRMED**, got worse | Balance v5 and a new hero-activity RNG pattern landed under `RULES_VERSION = 1`, content version 2. |
| F10 save growth | **PARTIAL** (as the review says) | a9c81's new terminal reason is classified in the staged merge. Dominant growth is unsold storage. |
| F11 locale-dependent payload | **CONFIRMED, narrowed** | Exactly two sites. `lowercase()` / `uppercase()` are locale-invariant in Kotlin; treating them as findings would be wrong. |
| §4.3 `MarketVisit` shape | **CONFIRMED** | `Model.kt:179`. |
| §6.2 Signboard cap (affects F05) | **FIXED** at `d7b3283` | `Market.kt:22`, `LaunchContent.kt:256`: +1 customer per level. Guild Patronage still a probability (`Market.kt:23`). |
| §6.6 cross-era fidelity | **CONFIRMED** | Plus: a returned blade re-enters the Legend Board by itself, with no owners; duplicates across eras. CHANGED-BY-INFLIGHT(ac2ba): quality factor becomes upgradeable; (a9c81): new ownership kinds the owner summary also misses. |
| §9 checklist items 1–3 | **CONFIRMED** | |
| §12 invariants / inline constants / dispatcher | **CONFIRMED** | Plus: `BalanceConfig` is at the JVM 255-slot limit; unsorted `maxByOrNull` on factions; a full `ResolutionContext` per Home recomposition. |

Not verifiable here (**UNVERIFIED**): runtime frequency of the F01 races; anything requiring a build or a device; which
stores Android Auto Backup includes (`AndroidManifest.xml:6-8` sets `allowBackup="true"` with rule XMLs I did not read).

---

## 2. Evidence

### 2.1 F01 — concurrency in `GameViewModel`

`app/src/main/java/com/example/blacksmithproject/GameViewModel.kt`

| Method | Lines | Guard | Where it reads "current" | Write | Publish |
|---|---|---|---|---|---|
| `init` | 65-77 | — | store (`loadLegacy` 67, `loadRun` 68), DataStore (70) | — | `_ui.value = …` |
| `newRun` | 79-85 | **none** | **store** (`loadLegacy`, 80) | `saveAtomically(state, state.legacy)` 83 | unconditional `_ui.value = Playing(state)` 84 |
| `continueRun` | 87-90 | none | store (88) | — | `RunEnded(…, claimed = false)` hard-coded 89 |
| `dismissReport` | 99-107 | none | `_ui.value` (100) | fire-and-forget DataStore write (101) | `RunEnded(…, claimed = false)` hard-coded 103 |
| `dispatch` | 109-125 | **`if (current.busy) return`** 111, sets busy 112 | `current.state` captured before `launch` (110, 114) | `saveAtomically(out.state, out.state.legacy)` 116 | `_ui.update { if (ui !is Playing) ui else … }` 117-120 |
| `endDay` | 128-131 | via `dispatch` | `_ui.value` | — | deterministic id `"${runId}:day${day}"` 130 |
| `claimLegacy` | 133-148 | **none** | `current.runEnd` captured (134) + **store** (136) | `saveAtomically(null, …)` 139 / 143 → **deletes the run row** | unconditional `_ui.value = current.copy(…)` 140 / 144 |
| `buyUpgrade` | 150-161 | **none** | **`current.legacy` captured at tap** (151, 153) — never the store | `saveAtomically(null, out.legacy)` 155 | unconditional `_ui.value = current.copy(legacy = …)` 156 |
| `beginNextEra` | 163-167 | only `current.claimed` 165 | — | via `newRun` | — |

So three different notions of "latest" coexist: the captured UI snapshot (`buyUpgrade`), a fresh store read
(`newRun`, `claimLegacy`), and the engine result (`dispatch`). UI: Claim button always enabled while unclaimed
(`ui/RunEndScreen.kt:61`); upgrade buttons `enabled = s.claimed && cost != null && s.legacy.points >= cost` (`:92`);
Begin era enabled whenever claimed (`:102`). No `busy` field exists on `UiState.Title` or `UiState.RunEnded`
(`GameViewModel.kt:38`, `:50`). Persistence: `SaveDao.saveBoth` is one `@Transaction`
(`data/SaveStore.kt:38-42`: `if (run == null) delete(KEY_RUN) else upsert(run); upsert(legacy)`), so each write is atomic;
the read-compute-write sequences around it are not.

Reachable interleavings (source-derived, not reproduced):

1. **Two upgrades.** A and B both compute from the same pre-A `current.legacy` (153). Each result holds one purchase.
   Last DB commit wins; last continuation to resume sets the UI. UI and store can end on *different* profiles, and the
   next `buyUpgrade` trusts the UI while `newRun` trusts the store.
2. **Upgrade then Begin era.** `newRun` may load the pre-A profile (80) and save a run embedding it (83). If A's
   `saveAtomically(null, A)` commits afterwards the new run row is **deleted** (`SaveStore.kt:40`), and A's continuation
   sets `_ui.value` back to `RunEnded` (156) over `Playing`. If A commits first but `newRun` read before it, the first
   `dispatch` of the new run writes `out.state.legacy` (pre-A) over the legacy row (116) and A is silently undone.
3. **Double Begin era / double "Light the forge".** Two `newRun` coroutines, two `System.nanoTime()` seeds (81), two runs;
   the second save overwrites the first; the UI shows whichever resumed last.
4. **Double Claim.** Both read the same stored profile and both produce the same merged profile (`Legacy.claim` is a
   pure function of its inputs, `legacy/Legacy.kt:80-92`). No double reward. The review is right not to call this an exploit.

Defects the review did not list:

- **The ended run is deleted at claim** (139, 143, 155), and `RunEndResult` is not serializable (`Legacy.kt:9-24`), so
  the run-end screen cannot be restored. After claim + process death, `init` sees `run == null` and shows `Title` (72).
  The in-run Legacy panel is read-only (`ui/InfoPanels.kt:217-227`), so banked points cannot be spent until the *next*
  era ends. The `claimed = run.runId.value in legacy.claimedRunIds` branch at 73 is therefore unreachable for a claimed run.
- **`claimed = false` is hard-coded** in `continueRun` (89) and `dismissReport` (103) while `init` derives it (73).
- **`Title(hasSavedRun = true)` is never constructed in production** (`init` builds `Title` only when `run == null`, 72;
  the only `true` is `app/src/androidTest/.../TitleScreenTest.kt:23`), so `continueRun` is dead code.
- **The legacy row is rewritten from the run's embedded copy on every command** (116; `GameState.legacy`,
  `Model.kt:268`, is a copy taken at `GameEngine.newRun`, `GameEngine.kt:55`). Any profile change made while a run row
  exists is overwritten by the next dispatch. Today that is only reachable through race 2, but it is the structural
  reason "two documents, one embedding the other" needs a single writer.

### 2.2 F02 — failure paths

- `init` (65-77), `newRun` (79-85), `continueRun`, `dispatch` (113-124), `claimLegacy`, `buyUpgrade`: no `try`, no
  `CoroutineExceptionHandler`. `viewModelScope` is `SupervisorJob + Main.immediate`, so an exception reaches the thread's
  uncaught handler and **the process dies**.
- What can throw on load (`SaveStore.kt:51`, `:53` → `core/.../persistence/SaveCodec.kt:24-34`):
  `IllegalArgumentException` from `require(env.schemaVersion in 1..SCHEMA_VERSION)` (`SaveCodec.kt:44`; note the message
  says "newer than supported" for version 0 as well); `IllegalStateException` from `error("No migration…")` (`:47`);
  `SerializationException` for malformed JSON, a missing required field or an unknown enum constant (`:25-26`; the
  `Json` at `:15-20` has `ignoreUnknownKeys` but not `coerceInputValues`); `IOException` / `CorruptionException` from
  `settings.dismissedReport()` (`data/SettingsStore.kt:37`, no corruption handler); SQLite exceptions.
  Because `init` runs again on every launch, **a bad row is a crash loop**; the only exit is clearing app data, which
  also destroys the legacy profile (same database).
- A save that decodes but is semantically bad is not checked at load. It fails later: `assertInvariants` uses `check`
  (`GameEngine.kt:365-368`) on the next accepted command, and content lookups use `error("Unknown …")`
  (`content/Content.kt:175-182`) from the engine or from composition.
- A failing write in `dispatch` (116) throws before the UI update (117), so nothing unsaved is ever shown — correct —
  but the process dies rather than leaving `busy` stuck. The review's "busy state is not restored" is imprecise.
- Cancellation: `withContext(Dispatchers.IO)` around a Room transaction either commits or does not; the UI update is
  skipped. Safe for End Day (idempotent id, `GameEngine.kt:269-273`) and for any other command (re-running a pure engine
  on the same committed state gives the same result).
- No fallback copy: `@Upsert` overwrites in place (`SaveStore.kt:31-32`). `SaveEntity.schemaVersion` and `savedAt`
  (`:21`, `:23`) are written and never read. `SaveDatabase` is `version = 1, exportSchema = false` (`:45`) with no
  migration or fallback registered.
- The dismissal marker is written by an un-awaited coroutine (`GameViewModel.kt:101`); a failure there also crashes.

### 2.3 F03 — how the report and the archive are built

- `ResolutionContext` copies the stored log into `events` and starts `newEvents` empty
  (`engine/ResolutionContext.kt:32-33`); `emit` appends to both (`:66-77`).
- End Day: `val dayEvents = ctx.newEvents.toList()` (`GameEngine.kt:298`) → `DayResolution(commandId, day, events =
  dayEvents, headlines = Gazette.headlines(dayEvents), visits, replays, defeated)` (`:299-302`) → `ctx.lastResolution`
  (`:303`) → compaction afterwards (`:305-307`). `GameState.lastResolution` holds **one** day (`Model.kt:271`).
- Readers: the dialog uses `r.events` and `r.visits` (`ui/Dialogs.kt:128`); the archive uses
  `st.eventsForDay(day)` with visits only when `lastResolution.day == day` (`ui/InfoPanels.kt:181`, `:186-188`);
  Home "Yesterday" does the same (`ui/HomePanel.kt:156`), Home "Shelf" reads `lastResolution.visits` (`:108`), Market
  reads them without the day check (`ui/MarketPanel.kt:64`).
- Events stamped with the same `day` but emitted by planning commands, hence absent from the report:
  `WEAPON_FORGED` (`crafting/Forge.kt:140`), `EPIC_/LEGENDARY_FORGED` milestones (`:147-148`),
  `SIGNATURE_DISCOVERED` priority 6 (`:152`), journal `DISCOVERY`, `WEAPON_LISTED` (`GameEngine.kt:120`),
  `WEAPON_SALVAGED` (`:196`), `WEAPON_HONED` (`:215`), `WEAPON_DONATED` (`:229`), `TOOL_BOUGHT` (`:241`),
  `BLESSING_CHOSEN` (`:260`), and on day 1 `RUN_STARTED` / `HERO_ARRIVED` (`:73-77`). A signature born on the anvil
  never leads that day's paper.
- Fixture `core/src/test/resources/saves/v1_forced_seed4242_day61.json` (my own parse): day 60 has 20 archived events,
  `lastResolution.events` has 15; the missing five are all `WEAPON_FORGED`.
- Further divergences:
  (a) visits exist only for the last day, so the same edition changes wording a day later —
  `"$sold of N visitors bought"` becomes `"$sold sold"` and the "who left and why" lines vanish (`Gazette.kt:109-111`, `:117`);
  (b) after `eventRetentionDays` the archive edition loses its ordinary events (`persistence/EventCompaction.kt:21-28`);
  (c) one won expedition can emit **two** `EXPEDITION_WON` records (`battle/Battle.kt:56` and the loot line `:64`),
  which is why the tally must filter on `"winProbability" in it.data` (`Gazette.kt:105`).
- No existing test compares the report edition with the archive edition (`eventsForDay` appears in tests only for
  compaction equality and one end-to-end sale check).

### 2.4 F06 — order, and what the commission takes

`GameEngine.kt:279` `Market.resolveShelfVisits` → `:280` `Market.resolveCommissions`. The commission candidate is

```kotlin
ctx.weapons.values
    .filter { (it.isInStorage || it.isListed) && it.familyId == c.familyId && it.quality >= c.minQuality && (c.element == null || it.element == c.element) }
    .maxByOrNull { it.quality }                                   // market/Market.kt:161-163
```

So (1) a sole qualifying listed blade can sell at `:279` first, and the commission then expires with −1 reputation at
its deadline (`Market.kt:172-175`); (2) when several qualify the patron takes the **best** one, at the flat reward,
whatever its shelf price; (3) the patron's purse is untouched (`:168` only raises loyalty) while the smith gains the
reward (`:165`). UI promise: "A matching weapon in storage or on the shelf is delivered at End Day"
(`ui/MarketPanel.kt:89`). The collector takes a listed blade later still (step 6, `engine/WorldEvents.kt:357-362`).

### 2.5 F07 — every change to the smith's gold, and what records it

| Where | Change | Record | Amount carried? |
|---|---|---|---|
| `Market.purchase` `Market.kt:119` | `+ price − credit + bonus` | `WEAPON_SOLD` data `price`, `premium?`, `tradeIn?`, `tradedWeapon?` (`:128-130`) | price and trade-in yes; **`bonus` (`:111`) no** |
| `Market.resolveCommissions` `:165` | `+ c.reward` | `COMMISSION_COMPLETED` data `reward` (`:169`) | yes |
| `WorldEvents` collector `WorldEvents.kt:360` | `+ listedPrice × collectorPriceMultiplier` | `WORLD_EVENT` vars `weapon` (a *name*), `price`, `event=collector` (`:364`, `:57`) | as an untyped var; **ignored by the tally** |
| `Battle.resolveSiegeIfDue` `Battle.kt:203` | `+ warlordTribute` | `MILESTONE` data `tribute` (`:204`) | yes — and **counted as "Shop took"** (`Gazette.kt:101`) |
| `GameEngine.buyMaterial` `GameEngine.kt:145` (planning) | `− cost` | **no event at all** | no |
| `GameEngine.buyTool` `:238` (planning) | `− cost` | `TOOL_BOUGHT` data `tool`, `name`, `level` (`:241`) | **no** |
| a9c81 `Market.resolveMerchant` (staged) | hero pays a merchant; smith gets nothing | `WEAPON_RESOLD` data `price` | yes, but it is not shop revenue |

Nothing else at End Day touches `ctx.gold` (expedition loot goes to the hero, `Battle.kt:39`; materials to the forge, `:63`).

Tally (`Gazette.kt:95-115`): `sold` = `WEAPON_SOLD` + `COMMISSION_COMPLETED` (`:96`) printed against `visits.size`
(`:110`), so "2 of 1 visitor bought" is possible; `gold` omits the bonus and the collector and includes the tribute
(`:97-104`); `lost` counts only `EXPEDITION_LOST` (`:106`), and a fatal expedition emits `HERO_DIED` alone
(`Battle.kt:71-73` → `kill`, `:97`). `sim/Simulator.kt:209-211` computes `goldEarned` and `sold` the same way, so
recorded balance evidence under-reports revenue under Merchant's Favor, commissions and collectors.

### 2.6 F08

```kotlin
// battle/Battle.kt:117-122 — ranking: elite defaults to false (Power.kt:67)
.sortedWith(compareByDescending<Pair<Hero, Weapon?>> { Power.defensePower(it.first, it.second, faction, ctx.content, ctx.config, ctx.blessingMagnitude(BlessingEffect.HERO_POWER)) }.thenBy { it.first.id.value })
// battle/Battle.kt:145-148 — valuation: elite = warlord
val warlord = faction.warlordName != null && factionState.pressure >= config.warlordPressure
val championPowers = champions.map { (h, w) -> Power.defensePower(h, w, faction, ctx.content, config, blessing, warlord) }
```

`Power.affixMatchup` applies `eliteMultiplier` only when `elite` (`battle/Power.kt:35-41`). The same un-contexted
ranking feeds `town.championIds` (`GameEngine.kt:332-333`). Fix is one defaulted parameter:
`selectChampions(ctx, faction, elite: Boolean = false)`, passed `warlord` from `outlook` and from `recover`.
Existing tests call `engine.siegeForecast` (`GameplayDepthTest.kt:119-120`, `:255-258`) but none ranks four heroes.

### 2.7 F09 — versions

| Axis | Lives in | Stored in the run? | Checked on load / in `handle`? |
|---|---|---|---|
| Save schema | `SaveCodec.SCHEMA_VERSION = 1` (`SaveCodec.kt:13`), envelope (`:10`); duplicated in the unread Room column (`SaveStore.kt:21`) | envelope | only `in 1..SCHEMA_VERSION` (`:44`) |
| Rules | `GameEngine.RULES_VERSION = 1` (`GameEngine.kt:31`) | `GameState.rulesVersion` (`Model.kt:246`); also salts every stream seed (`rng/Rng.kt:42`) | **never** (`handle`, `GameEngine.kt:83-99`) |
| Content | `ContentCatalog.version` (`Content.kt:149`; launch = 2, `LaunchContent.kt:103`; slice = 1) | `GameState.contentVersion` (`Model.kt:247`) | **never** |
| Balance | `BalanceConfig.version = 5` (`BalanceConfig.kt:19`) | **no** | n/a (printed by the simulator only, `Simulator.kt:661`, `:706`) |
| RNG algorithm | `RngState.version` (`Rng.kt:31`, `:37`) | yes | never |
| Legacy profile | `LegacyProfile.version = 1` (`Model.kt:225`) | yes | never read |

`ResolutionContext.toState` copies the two stored versions through (`ResolutionContext.kt:112-113`). The affix
magnitude changes of `d7b3283` are catalog numbers, yet the content version stayed 2; hero daily life changed the
HEROES-stream draw pattern (`heroes/Heroes.kt:56`, `:75-91`) under rules version 1.

Why `RULES_VERSION` is sticky: bumping it changes `RngState.seeded` for every seed (`Rng.kt:42`), which reshuffles
every seed-pinned test and every recorded balance table.

Two compatibility traps the review does not mention:
- **Adding an `RngStream` breaks every existing save at first use**: `stateOf` is `streams[stream] ?: error("Missing
  RNG stream")` (`Rng.kt:34`) and old saves carry only the eight current streams.
- **The migration table is shared by both documents**: `migrations: Map<Int, (String) -> String>` (`SaveCodec.kt:41`)
  is applied by `decodeRun` (`:26`) and `decodeLegacy` (`:33`) alike, so a run-shaped step would also run on the legacy
  payload. And the run payload embeds a `LegacyProfile`.
`MigrationTest.kt:123` asserts `SCHEMA_VERSION == 1` as a tripwire.

### 2.8 F10 — what is bounded, what grows

Bounded today (all inside End Day, `GameEngine.kt:305-307`):
`EventCompaction` (ordinary events, 30 days; `EventCompaction.kt:14-28`), `WeaponHistoryCompaction` (only `VICTORY` /
`SIEGE`, newest 10; `WeaponHistoryCompaction.kt:19`), `WeaponPruning` (Destroyed, or Lost for a terminal reason, 30
days, not fame ≥ 3, not signature; `WeaponPruning.kt:20-30`). `legendBoard` ≤ 20, `lineages` ≤ 10 (`Legacy.kt:87-88`).

Still growing, with slopes from the fixture (forced survival, BALANCED_FAIR, 60 days, codec of 2026-10-08 — one old
sample, indicative only):

| Source | Evidence | Fixture | ≈ per day |
|---|---|---|---|
| **Unsold storage** (player property; trade-ins land here unasked, `Market.kt:116`) | `Model.kt:259` | 196 of 235 weapons in Storage; weapons = 151,430 of 285,969 payload bytes; 637 B/weapon, 30 % of it `history` text | ~2,100 B |
| Kept-forever events (`HERO_ARRIVED`, `WORLD_EVENT`, `MILESTONE`, inheritances, losses…) | `EventCompaction.kt:14-19` | 71 of 615 events, 15,320 B | ~255 B |
| Closed commissions (never pruned) | `Model.kt:263`; only OFFERED/ACCEPTED are ever read (`Market.kt:186`) | 18 (17 completed, 1 expired), 2,901 B | ~48 B |
| Dead / retired heroes (never removed) | `Battle.kt:96`, `Heroes.kt:215` | 6 of 16, 334 B each | ~33 B |
| `processedEndDayIds` | `GameEngine.kt:304`, `Model.kt:270` | 60 ids, 1,312 B | ~22 B |
| Non-combat weapon history (`TRADED_IN`, `SOLD`, `EQUIPPED`, `HONED`, `INHERITED`) | not in `compactable` | 45,549 B of history in total | per sale cycle ~3 entries |
| Recoverable lost blades (`seized`, `lost with …`) | `WeaponPruning.kt:26` | 2 | small |

Fixed overheads worth knowing: the envelope stores the payload as a JSON *string inside JSON* (`SaveCodec.kt:10`, `:22`),
costing 330,421 − 285,969 = **15.5 %** in escapes; the run embeds the legacy profile (2,104 B) that is also its own
row; `encodeDefaults = true` writes every default. Every accepted command copies all maps
(`ResolutionContext.kt:106-147`), asserts invariants and rewrites both documents (`GameViewModel.kt:116`), including
for `SetPrice`.

What the soak really exercises (`core/src/test/.../SoakTest.kt`): `eventRetentionDays = 30, maxForgesPerDay = 1`
(`:31-34`). The driver-level trim drops **all** events older than the cutoff, kept-forever types included
(`Simulator.kt:221-224`), and the test then asserts exactly that (`SoakTest.kt:61`), so engine retention of
history-grade events is never measured; one forge a day hides stock growth; `siegeModifier = 0`
(`Simulator.kt:574`) means no siege is ever lost. The perf test asserts only `p95 > 0` (`SoakTest.kt:78`).
`measureEndDay` (`Simulator.kt:577`) is production-shaped for timing but measures neither bytes nor encode/decode.

UI consequence in long saves: the archive composes one header per distinct event day in a non-lazy column and filters
the whole log per day (`InfoPanels.kt:181-188`, `Model.kt:294`) — quadratic in run length once kept-forever events
give every day an entry.

a9c81: adds `Lost` reasons `"held by a travelling merchant"` (transient, invariant-bounded) and `"carried off by a
travelling merchant"`. The worktree predates `WeaponPruning`; the **staged merge on `main` adds the second to
`terminalReasons` and teaches `WeaponPruningTest.everyLostReasonIsClassified` about the first** — gap closed, pending commit.

### 2.9 F11 — locale

Locale-dependent and inside saved state: **only** `"%.2f".format(winProbability)` at `battle/Battle.kt:53` and `:56`
(`String.format` uses `Locale.getDefault(FORMAT)`: `0,62` in German, other digits in some locales). No reader parses
the value (`Gazette.kt:105` tests key presence), so outcomes are unaffected; serialized state is not.

Checked and cleared: every `lowercase()` / `uppercase()` / `replaceFirstChar { it.uppercase() }` in `:core`
(`GameEngine.kt:73`, `Battle.kt:211`, `:247`, `Market.kt:205`, `Forge.kt:127`, `:134`, `:142`, `Gazette` forge lines,
`Journal.kt:129`, `Invariants.kt:23`) — Kotlin's no-argument forms use the invariant locale. Every `.toString()` in an
event payload is on an `Int` or `Boolean` (e.g. `Forge.kt:144`, `Battle.kt:189`). The other `.format(` calls are
simulator console output (`Simulator.kt`) and a test `println`. Sorting is ordinal `String.compareTo`.

### 2.10 §6.6 — cross-era fidelity

`LegendEntry` (`Model.kt:205-214`): `era, weaponName, title, kills, fame, owners: List<String>` + defaulted
`familyId?, coreId?, augmentId?, quality, power, element?`. Not stored: weapon id, affixes, flaws, catalyst,
`signatureId`, mode/risk, victories, sieges defended, condition, history.

Recording (`Legacy.kt:49-60`): any weapon with `fame >= legendFameThreshold` regardless of location — destroyed,
collected or lost blades qualify; owners = names of `SOLD` / `COMMISSION` subjects only (`:54-55`), missing
`INHERITED` (`Heroes.kt:226`, `WorldEvents.kt:200`) and, after a9c81, guild inheritance and `RESOLD`.

Return (`WorldEvents.kt:301-325`): new id; `name = legend.weaponName` (a name built from affix names at forge time,
`Forge.kt:122-126`) with `affixes = emptyList(), flaws = emptyList()` (`:316`), no catalyst, no signature;
`mode = ADVANCED, risk = BALANCED`, `forgedDay = 1` invented (`:315`, `:317`); quality and power × 0.7 (`:310-312`),
where the stored power still contains the lost affixes' power (`Forge.kt:119-120`); kills and **full fame** carried,
victories and sieges reset; condition 100 although the text says "worn and dormant".
Consequences not in the review: the carried fame is already ≥ the threshold, so the blade **re-enters the Legend Board
at the end of that era even if nobody ever touches it, with an empty owner list**; and `rng.pick(legendBoard)` (`:306`)
can return the same legend in several eras, filling the board with copies.

Lineage: `LineageAnchor` (`Model.kt:217`) has no hero id; descendants are matched by **name string**
(`GameEngine.kt:75`, `WorldEvents.kt:329`, `:333`). The replay stage also joins heroes by `fullName`
(`ui/Dialogs.kt:228`). With 30 first names × 24 surnames (`LaunchContent.kt:244-251`) and a larger population planned,
generation must guarantee unique full names or these joins must move to ids.

ac2ba (unmerged): `returnedLegendFactor` raises the 0.7 up to 0.85 with an upgrade and weights the event; fidelity unchanged.

### 2.11 §12

**Invariants** (`engine/Invariants.kt:9-43`) check: gold, energy, overwork, materials ≥ 0; integrity ≥ 0; armory bounds;
shelf count; owner exists and alive; price ≥ 0; condition 0..100; ≤ 1 equipped per hero; hero gold ≥ 0; champions alive;
pressure 0..100; guild founder exists; world-event counters.
Not checked: any content reference (family, core, augment, catalyst, affixes, class, traits, blessings, tools,
upgrades, material and faction keys); champion uniqueness, count and health; hero health/level/xp bounds; weapon
quality/power bounds and rarity consistency; `Lost`/`Destroyed` day ≤ today; commission buyer exists, at most one open
commission (assumed at `Market.kt:186`); `phase == ENDED` ⇔ integrity 0 ⇔ `endCause`; `lastResolution.commandId ∈
processedEndDayIds`; serial counters ahead of existing ids; every `RngStream` present; map key == entity id.
`Invariants.check` already returns a list (used that way in `SaveFixtureTest.kt:50`), so it can validate a loaded save
without throwing.

**Inline tunables outside `BalanceConfig`** (notable ones):
- `Market.kt:28` visit chance `shopWeight * 0.1 + loyalty * 0.01 + reputation * 0.005`, clamp `0.05..0.9`; `:45`
  overpriced threshold `0.5`; `:79` loyalty `* 0.01`; `:120` reputation +1 per sale; `:166` +2 and `:168` loyalty +2 per
  commission; `:174` −1 on expiry; `:194` commission quality `35..60`.
- `Heroes.kt:21` trait count `2..3`; `:27` taste `0.6` / `0.5`; `:30` descendant level 2; `:80-82` activity bases
  `1.0`, `0.8`, `0.2`, pressure `* 0.5`, armed `+0.5 / −0.3`, integrity `< 60 → +0.4`, floors `0.05` / `0.02`; `:184`
  level-5 milestone; `:221` mentee `+1` level.
- `Battle.kt:40`, `:44` fame +1; `:47` title at 5 kills; `:131-133` odds bands `1.15 / 1.0 / 0.8`; `:192`, `:196` fame
  +2; `:252-257` pressure words `80 / 60 / 40 / 20`. `Power.kt:14` `0.6 + 0.4 × health`; `:17` clamp `0.8..1.2`; `:68`
  unarmed weight `0.9`.
- `GameEngine.kt:53` militia 5; `:60-64` the three world modifiers; `:226` donate reputation +1.
- `WorldEvents.kt`: each event's `weight`, `maxPerRun`, `cooldownDays` (catalog-like, fine where they are) and effect
  literals `:117` (+2 stock), `:155` (+15), `:158` (+2 days), `:183` (fame 2), `:267` (+2), `:310` (fallback quality
  60), `:345-346` (+2 reputation, +3 militia), `:361` (+1 reputation).
- `Legacy.kt:34-45` milestone points; `:52` three legends per run; `:87-88` board sizes.

**A hard constraint on moving them:** per the in-code comment (`BalanceConfig.kt:274`; not recomputed here) the
`BalanceConfig` constructor uses about 250 of the JVM's 255 parameter slots; past the limit the class compiles and
fails to load. `heroLife` (265), `weaponFates` (staged) and ac2ba's `legacyTracks` each take one slot, leaving about
two. **Every new group of numbers must be a nested config object.**

**Determinism nit:** `GameEngine.kt:332`, `Battle.kt:143`, `:244` use `factions.values.maxByOrNull { it.pressure }` on
an unsorted map, while `Heroes.kt:42` and `Market.kt:197` sort by id first. Ties resolve by map order (stable through
JSON today) and the two conventions can name different factions on a tie.

**Dispatcher:** `engine.handle` runs inside `viewModelScope.launch` on Main (`GameViewModel.kt:113-114`), as do
`newRun` (82), `closeRun` (73, 89, 103), `claimLegacy` (137) and `purchaseUpgrade` (153); only encode + Room run on IO
(116; encode inside `SaveStore.kt:58`). Composition also calls `vm.engine.siegeForecast(st)` unremembered
(`ui/HomePanel.kt:38`), which builds a full `ResolutionContext` — a copy of every map — per recomposition
(`GameEngine.kt:169`).

---

## 3. Current shapes (commit `77919fe`, `core/.../model/Model.kt`)

Codec: `Json { encodeDefaults = true; ignoreUnknownKeys = true; classDiscriminator = "type"; allowStructuredMapKeys =
true }` (`SaveCodec.kt:15-20`). Envelope `SaveEnvelope(schemaVersion: Int, payload: String)` (`:10`), same
`SCHEMA_VERSION` for run and legacy (`:22`, `:29`). All ids are `@Serializable @JvmInline value class X(val value:
String)` (`model/Ids.kt:5-17`); maps keyed by them serialize as string keys. Enums serialize by constant name.

| Type | Required (no default) | Defaulted |
|---|---|---|
| `MarketVisit` `:179` | `heroId: HeroId, heroName: String, purchasedWeaponId: WeaponId?, reason: String` | — |
| `DayResolution` `:182-190` | `commandId: CommandId, day: Int, events: List<EventRecord>, headlines: List<String>, visits: List<MarketVisit>, replays: List<CombatReplay>, defeated: Boolean` | — |
| `EventRecord` `:160-169` (the "GameEvent") | `id: String, era: Int, day: Int, type: EventType, priority: Int, text: String` | `subjectIds: List<String> = []`, `data: Map<String, String> = {}` |
| `CombatReplay` `:176` / `CombatRound` `:172` | `title, day, rounds, outcome` / `attacker, defender, damage, note` | a9c81 adds `kind: ReplayKind = SIEGE`, `eventId: String? = null` |
| `Weapon` `:32-71` | `id, name, familyId, coreId, augmentId, mode, risk, quality, rarity, power, element: Element?, affixes, flaws, location, forgedEra, forgedDay` | `catalystId = null, kills = 0, victories = 0, siegesDefended = 0, fame = 0, title = null, history = [], signatureId = null, honed = false, condition = 100` |
| `Hero` `:74-107` | `id, name, surname, classId, level, xp, gold, health, traits, elementTaste: Element?` | `loyalty, fame, fate = ALIVE, lastActivity = IDLE, descendantOf, kills, victories, diedOnDay, guildId, mentorName, retiredOnDay, ambition, ambitionDone, expeditionWins, elitesSlain, drivenBackOnDay` |
| `Commission` `:130-142` | `id, buyerId, familyId, minQuality, reward, offeredDay, deadlineDay, status` | `deliveredWeaponId = null, element = null` |
| `LegacyProfile` `:224-237` | — | all: `version = 1, points, upgrades, journal, legendBoard, lineages, claimedRunIds, eras, totalPointsEarned` |
| `GameState` `:243-295` | 32 fields `runId` … `nextEventSerial`, including `rng`, `world`, `blessings`, `pendingBlessingOffer`, `legacy`, `processedEndDayIds` and **`lastResolution: DayResolution?` (nullable but required: must be present, as `null`)** | `endCause = null, eventCounters, eventLastDay, worldFlags, tools` |

Not serializable (must stay out of the save, or be given a DTO): `Market.Evaluation` (`Market.kt:15`),
`RunEndResult` (`Legacy.kt:9`), `Command`, `GameError`, `CommandOutcome`, `Battle.SiegeOutlook`.

Compatibility today:
- **Safe:** adding a field with a default (old saves take the default; `MigrationTest.decodesStoredV1FixtureWithoutDefaultedKeys`);
  unknown keys (`toleratesUnknownKeysSoAddedFieldsMigrateForward`); adding an enum constant; widening a type
  (`HeroId` → `HeroId?`); changing a `String` field to an enum whose constant names equal the stored strings.
- **Breaks decode of a save written today:** a new field without a default; removing or renaming an enum constant that
  appears in a save; a narrowing type change; removing a `RngStream`.
- **Silently loses data:** renaming a defaulted field (old key ignored, default taken).
- **Breaks every save:** renaming or moving `WeaponLocation` or a subclass. With `classDiscriminator = "type"` and no
  `@SerialName`, the fixture stores `"type":"com.tinyblacksmith.core.model.WeaponLocation.Storage"` (and `.Shelf`,
  `.Owned`, `.Lost`). Pin `@SerialName` to those exact strings before any package refactor (no schema bump needed; add a fixture assertion).
- **Older build reading a newer save:** fails on a new enum value (e.g. `HeroActivity.GUILD`, `EventType.GUILD_TRAINED`
  since `77919fe`) and silently ignores new keys — which matters for C below.

---

## 4. Proposals

### A. One operation boundary

Three changes, in order of value:

1. **Stop deleting the ended run.** Claim and upgrades commit `(endedRun, newLegacy)`; only Begin era replaces the run.
   `init` already knows how to rebuild the run-end screen from an ended run plus the stored profile
   (`GameViewModel.kt:73`). This alone removes the destructive write from the upgrade path, makes the run-end screen
   survive process death, and makes `claimed` always derivable (`runId in legacy.claimedRunIds`).
2. **One mutex, one mirror of what is on disk, one place that writes.**
3. **An injectable repository**, which is also the F02 test seam.

```kotlin
// app/.../data/GameRepository.kt — implemented by SaveStore; faked in tests
sealed interface LoadResult {
    data class Ok(val run: GameState?, val legacy: LegacyProfile) : LoadResult
    data class Failed(val kind: LoadFailure, val message: String) : LoadResult
}
enum class LoadFailure { NEWER_SAVE, CORRUPT, INVALID, STORAGE }

interface GameRepository {
    /** One read. Never writes or deletes: after Failed the rows are byte-identical. */
    suspend fun load(): LoadResult
    /** One transaction (today's SaveDao.saveBoth). Throws on failure; never half-written. */
    suspend fun commit(run: GameState?, legacy: LegacyProfile)
}
```

```kotlin
class GameViewModel(private val repo: GameRepository, val settings: SettingsStore, val engine: GameEngine = GameEngine()) : ViewModel() {

    /** The last successful commit. Read and written only while holding [gate]. */
    private data class Committed(val run: GameState?, val legacy: LegacyProfile)
    private var committed: Committed? = null
    private val gate = Mutex()                       // kotlinx Mutex is fair: operations run in arrival order

    private sealed interface Op {
        data class Play(val runId: RunId, val command: Command) : Op          // every engine command, End Day and AcknowledgeDay included
        data class Claim(val runId: RunId) : Op
        data class BuyUpgrade(val runId: RunId?, val id: UpgradeId) : Op
        data class BeginEra(val afterRunId: RunId?) : Op
    }
    private sealed interface Step {
        data class Commit(val next: Committed, val forged: WeaponId? = null, val resolution: DayResolution? = null) : Step
        data class Reject(val error: GameError) : Step
        data object Stale : Step                      // the world the tap was aimed at is gone: drop silently
    }

    private fun decide(base: Committed, op: Op): Step          // pure: engine calls only
    private fun submit(op: Op): Job = viewModelScope.launch {
        gate.withLock {
            val base = committed ?: return@withLock
            setBusy(true)
            try {
                when (val step = decide(base, op)) {
                    is Step.Commit -> { repo.commit(step.next.run, step.next.legacy); committed = step.next; publish(step) }
                    is Step.Reject -> publishError(describe(step.error))
                    Step.Stale -> Unit
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { publishSaveFailure(op) }   // committed and the visible state are unchanged; Retry resubmits op
            finally { setBusy(false) }
        }
    }
}
```

- **Latest state** is `committed`, read inside the lock. Never the UI snapshot captured at tap time (`buyUpgrade`
  today), never a fresh store read mid-session (`newRun`, `claimLegacy` today). The store is read in `init` and on an
  explicit Retry from the load-failure screen, nowhere else.
- **Stale rejection across a run transition** is the `runId` each op carries from the screen that produced it:
  `Play` and `Claim` are `Stale` unless `base.run?.runId == op.runId`; `Claim` also needs `run.isEnded` and (with C) no
  unacknowledged day; `BuyUpgrade` and `BeginEra` need `base.run?.runId == op.runId` and the run absent or ended-and-claimed.
  A second Begin era finds a live run and is dropped; an upgrade queued behind Begin era is dropped; two different
  upgrades both apply, the second validated by `Legacy.purchaseUpgrade` against the profile the first produced.
- `dispatch` keeps its existing early return while `busy` (`GameViewModel.kt:111`), so a double tap on Forge still
  forges once. During a run `Commit` keeps today's rule `Committed(out.state, out.state.legacy)`.
- **Busy / failure states:** add `busy` to `Title` and `RunEnded`; add `UiState.LoadFailed(kind, message)` (Retry;
  never replaced by a fresh game automatically); add `saveError: String?` to `Playing` and `RunEnded`.
  A failed commit is safe to retry because the engine is pure and the failed result was never shown: the same command
  on the same committed state yields the same outcome.
- Non-authoritative settings (`setReducedMotion`, `dismissTip`) stay outside the gate.
- Moving `decide` to `Dispatchers.Default` is then a one-line change; not justified yet (recorded AVD p95 8.5 ms) —
  do it when a production-shaped soak says so.

Missing seam, precisely: `GameViewModel` is an `AndroidViewModel` that builds `SaveStore.create(app)` and
`SettingsStore(app)` itself (`GameViewModel.kt:57-60`); `SaveStore` is a final class over a Room DAO
(`SaveStore.kt:50`); core's `SaveRepository` (`SaveCodec.kt:58-63`) is synchronous and used only by
`LegacyAndEndToEndTest`. `MainActivity.kt:12` uses `by viewModels()` without a factory. `app/build.gradle.kts` has
`kotlinx.coroutines.test` for `androidTest` only; add it to `testImplementation` to run these as plain JVM tests.

### B. Typed shop-day record

**Extend `MarketVisit` in place; add one list and one version field to `DayResolution`.** No new parallel structure:
two records of the same day is how F03 and F07 happened. Every new field is defaulted and the two retyped fields are
wire-compatible, so `SCHEMA_VERSION` does not have to move for B.

```kotlin
enum class VisitKind { BROWSE, COMMISSION, COLLECTOR }

/** Same spellings as the v1 `reason` strings, so stored days decode unchanged; the last two are new. */
enum class VisitReason {
    EMPTY_SHELVES, TOO_EXPENSIVE, NOT_BETTER, OVERPRICED, NOT_SUITED, UNDECIDED,     // left
    WORN_OUT, GREAT_FIT, GOOD_ENOUGH,                                                // bought
    COMMISSION_DELIVERED, COLLECTOR_PURCHASE,
}

/** Facts the counter scene may state. No utility, weight or chance ever leaves Market (GDD 12: descriptive only). */
enum class VisitFactor {
    SUITS_CLASS, OFF_CLASS, ELEMENT_TASTE, LIKES_NOVELTY, STRONGER_THAN_OWN, NOT_STRONGER_THAN_OWN, OWN_BLADE_WORN,
    STORIED_BLADE, COLLECTOR_PRIZE, CAN_AFFORD, CANNOT_AFFORD, ABOVE_GOING_RATE, REGULAR,
}

@Serializable data class WeaponSnapshot(
    val weaponId: WeaponId, val name: String, val familyId: WeaponFamilyId, val coreId: MaterialId, val element: Element? = null,
    val rarity: Rarity, val quality: Int, val power: Int, val condition: Int, val fame: Int = 0, val title: String? = null,
    val affixes: List<AffixId> = emptyList(), val flaws: List<AffixId> = emptyList(), val signatureId: String? = null,
)
@Serializable data class CustomerSnapshot(
    val heroId: HeroId, val name: String, val classId: HeroClassId, val level: Int, val appearance: Int,
    val traits: List<TraitId> = emptyList(), val elementTaste: Element? = null, val ambition: Ambition? = null,
    val regular: Boolean = false, val gold: Int, val equipped: WeaponSnapshot? = null,
)
@Serializable data class Considered(val weaponId: WeaponId, val price: Int, val factors: List<VisitFactor> = emptyList())
@Serializable data class Sale(
    val listedPrice: Int, val tradeInCredit: Int = 0, val tradeInWeaponId: WeaponId? = null,
    val cashPaid: Int, val saleBonus: Int = 0, val commissionId: CommissionId? = null,
)

@Serializable
data class MarketVisit(
    val heroId: HeroId?,                       // was HeroId; null only for COLLECTOR
    val heroName: String,
    val purchasedWeaponId: WeaponId?,
    val reason: VisitReason,                   // was String
    val seq: Int = 0,
    val kind: VisitKind = VisitKind.BROWSE,
    val customer: CustomerSnapshot? = null,    // null: the collector, or a day decoded from a v1 save
    val considered: List<Considered> = emptyList(),
    val sale: Sale? = null,
    val eventIds: List<String> = emptyList(),
)

// DayResolution gains:
val shopWeapons: List<WeaponSnapshot> = emptyList(),   // the opening shelf + any stored blade a commission took, as they were
val recordVersion: Int = 0,                            // 0 = v1 day without snapshots, 1 = this record
```

Where each value already exists (nothing new is computed, **no `ctx.rng` call is added**, so seeds replay unchanged):

| Field | Source |
|---|---|
| `shopWeapons` (opening shelf) | `listed` at `Market.kt:20`, before the loop |
| `seq` | the `customers` counter, `Market.kt:30` |
| `customer`, `customer.equipped` | `hero` (`:25`) and `current` (`:35`), read **before** `purchase` moves the old blade to storage (`:116`) |
| `considered` + factors | `evaluations` (`:36`): `Evaluation(weapon, utility, affordable, improvement, fit, pricePenalty, worn)` (`:15`). Taste, collector and fame branches are locals of `evaluate` (`:65-72`) — add four booleans to `Evaluation`. Keep the chosen blade plus the best alternatives by the utility already computed, capped by a nested-config number. |
| `reason` | the existing strings at `:32`, `:40`, `:42-48` |
| `sale` (shelf) | locals of `purchase`: `bonus` (`:111`), `old` (`:113`), `credit` (`:114`), cash `price − credit` (`:119`, `:122`). Make `purchase` return the `Sale` (it returns `Unit` today). |
| `eventIds` | `ctx.emit` already returns the record (`ResolutionContext.kt:66-77`); keep the results at `Market.kt:130` and `:145` |
| commission visit | `buyer`, `candidate`, `c.reward` at `Market.kt:155-169`; `listedPrice` = the candidate's shelf price if it had one (makes F06 visible) |
| collector visit | `w`, `price` at `WorldEvents.kt:357-364`; its event is emitted afterwards by `fire` (`:57`), so add one defaulted field `WorldEventOutcome.visit: MarketVisit? = null` and let `fire` append it with the event id |

Visit-time truth is already guaranteed by End Day order: visits and commissions (`GameEngine.kt:279-280`) precede
activities, sieges and retirements (`:283-293`). a9c81's merchant resale is **not** a visit: the gold leaves the
economy; it belongs to the aftermath.

Consumers to touch: `Gazette.tally` / `shopLines` (`Gazette.kt:95`, `:117`; count `kind == BROWSE` as visitors, sum
`sale.cashPaid + sale.saleBonus` by kind, tribute on its own line), `Gazette.visitReason` (`:51`), the three panels in
2.3, `Simulator.kt:207-211`, and the no-op at `Heroes.kt:48`.

Also for F03 (independent of B): build the day's edition from the whole day,
`ctx.events.filter { it.day == day }` taken at `GameEngine.kt:298` before compaction, and keep `newEvents` only as the
command's return value (`accept`, `:362`).

Cost (hand estimate, `encodeDefaults = true`): a weapon snapshot ≈ 240 B, a customer without blade ≈ 200 B, a
considered entry ≈ 75 B. Per day: shelf of 8–10 ≈ 2–2.4 KB, plus ≈ 0.9 KB per visitor → **≈ 6 KB at four visitors,
≈ 10 KB at eight**, against 372 B for four visits today (fixture). Naively embedding a snapshot per considered blade
per visitor would be ~18 KB; referencing `shopWeapons` by id avoids that.

Retention: **one day**, inside `lastResolution`, exactly like visits today — a constant, not growth. For the archive,
emit one ordinary `SHOP_DAY` event per End Day with the day's counts and cash by kind; it rides the existing 30-day
log and makes report and archive tallies agree by construction. Do not archive snapshots.

### C. Presentation acknowledgement and cursor

Today: `UiState.Playing.showReport` in memory plus one DataStore string (`SettingsStore.kt:21`, `:37-41`), compared in
`init` (`GameViewModel.kt:70`), written fire-and-forget (`:101`). The engine does not know a report is open; only the
modal blocks input, and back / outside-tap acknowledges (`Dialogs.kt:110`).

| | In `GameState` via a typed command (recommended) | In DataStore (today) |
|---|---|---|
| Atomic with the day's commit | yes, same Room transaction | no: two stores, asynchronous write (review §9.3) |
| Planning locked while presenting | enforced by `handle` | UI modal only |
| Testable in `:core` | yes | no |
| Survives backup / restore consistently | yes (one document) | not guaranteed (UNVERIFIED which stores are backed up) |
| Cost | one field, one command, one extra full save per day, harness updates | none |
| Architecture rule "DataStore holds only settings" | honoured | bent (`SettingsStore.kt:17` says "never gameplay state") |

```kotlin
// GameState
val unacknowledgedDay: CommandId? = null        // set by endDay next to lastResolution (GameEngine.kt:303-304)

// Command / GameError
data class AcknowledgeDay(val commandId: CommandId) : Command
data class DayNotAcknowledged(val commandId: CommandId) : GameError

// GameEngine.handle, before the isEnded check at :84
if (command is Command.AcknowledgeDay)
    return CommandOutcome.Accepted(if (state.unacknowledgedDay == command.commandId) state.copy(unacknowledgedDay = null) else state, emptyList())
state.unacknowledgedDay?.let { if (command !is Command.EndDay || command.commandId != it) return CommandOutcome.Rejected(GameError.DayNotAcknowledged(it)) }
```

- `AcknowledgeDay` builds no `ResolutionContext`, so it opens no RNG stream, emits no event and moves no serial:
  the state after it differs from the state before in that one field. It is idempotent and ignores stale ids.
- **The cursor is not gameplay state.** Which encounter is showing, speed and "skip all" stay in the UI
  (`rememberSaveable`; a speed preference may go to DataStore). Skip, watch and restart then produce byte-identical
  saves by construction. After a process death with an unacknowledged day the sequence restarts with a "Skip to
  results" option, which the review explicitly allows.
- **Rejected while unacknowledged:** `Forge`, `ToggleShelf`, `SetPrice`, `BuyMaterial`, `AcceptCommission`,
  `DeclineCommission`, `ChooseBlessing`, `Salvage`, `Hone`, `DonateWeapon`, `BuyTool`, and an `EndDay` with a new id.
  Allowed: `AcknowledgeDay`, and the idempotent retry of the same `EndDay` (`GameEngine.kt:269-273`).
- **Blessing:** the offer is created during End Day (`Battle.kt:232-239`) and the dialog is already held back until the
  report closes (`ui/WorkshopScreen.kt:107-108`). The offer stays in `pendingBlessingOffer` until chosen, so rejecting
  `ChooseBlessing` before acknowledgement loses nothing; the presentation's last step acknowledges, then offers.
- **Final day:** the siege sets `ENDED` (`Battle.kt:224-229`), `newMorning` is skipped (`GameEngine.kt:308`) and
  `handle` rejects everything but End Day (`:84`) — hence `AcknowledgeDay` goes above that check. Order becomes final
  report → acknowledge → run-end screen → claim (`Op.Claim` requires `unacknowledgedDay == null`) → upgrades → Begin era.
- **Cost, honestly:** the simulator (`Simulator.kt:202`) and tests must acknowledge. `TestSupport.endDay()` /
  `endDayAccepted()` (`TestSupport.kt:19-21`, about 53 uses) absorb most of it; about 20 direct `Command.EndDay(` call
  sites in 10 test files need a look. The softer alternative — let the next `EndDay` acknowledge implicitly — is not
  reachable through the UI, but one rule without exceptions is easier to reason about; recommend the strict rule.
- The GDD's PROPOSED `REPORTING` phase (GDD 13.2) would express the same thing, but `Phase.ENDED` is read in many
  places during and after End Day (`GameEngine.kt:292`, `:301`, `:308`, `:322`; `Heroes.kt:203`; `Legacy.kt:48`;
  `Simulator.kt` run loop) and the last day needs "ended" and "unread" at once. A nullable field is the smaller change.
- Retire the `dismissed_report` key (leave it unread).

### D. Version and compatibility policy

1. **Four numbers, four meanings.** *Schema* = shape of the JSON. *Rules* = End Day order and RNG consumption.
   *Content* = the set of ids and the numbers that live in definitions. *Balance* = `BalanceConfig`.
2. **Run policy: continue forward, never rewrite.** A saved run continues under the installed build's rules, content
   and balance — what happens today, made explicit. It is sound because no rule replays or re-reads past days
   (`EventCompaction.kt:8-10`). Events, RNG state, serials and histories are never touched by a migration. The
   determinism promise is scoped to "same build, seed and commands".
3. **Record:** add `GameState.balanceVersion: Int = 0` (0 = unknown, i.e. a pre-stamp save), set in `newRun` beside
   the other two (`GameEngine.kt:49`). Bump content on any catalog change, rules on any change to order or draws.
   Because a rules bump reshuffles every seed (`Rng.kt:42`), do it **once**, at this update, together with the fresh
   balance review the update needs anyway.
4. **Enforce only what can break**, in one pure `validateLoaded(state, content, config): List<String>` called by the
   repository: stored rules/content versions not newer than the build; every content id in the save resolves; every
   `RngStream` present; `Invariants.check` empty. Failure → `LoadResult.Failed`, bytes untouched.
5. **Make bumps mechanical:** two fingerprint tests (a hash of the launch catalog's numbers and of
   `BalanceConfig.DEFAULT`, pinned per version) that fail until the version is raised.

**First real migration, v1 → v2.** B and C decode without it, but three things defaults cannot express, and the
project still has no non-trivial step (the review's F09 point):
- an older build silently ignores `unacknowledgedDay` and would unlock planning — raising the schema makes it refuse
  the save instead (`SaveCodec.kt:44`);
- **hero appearance**: today a face is `floorMod(hero.id.value.hashCode(), 5)` computed in the UI
  (`ui/Sprites.kt:73-76`); nothing is stored. Add `Hero.appearance: Int` and let content own the variant count per
  class. The step writes `appearance = floorMod(id.hashCode(), 5)` for every existing hero (`String.hashCode` is fixed
  by the language spec), so **every existing hero keeps the face they have now**, and indices 0–4 stay valid when art
  adds variants. New heroes get theirs from a pure mix of `(run seed, hero serial)` — **not** from a gameplay stream
  and **not** from a new `RngStream` (see 2.7), so no outcome moves;
- `balanceVersion = 0` and `unacknowledgedDay = null` written explicitly.
  Names need no step: `name` and `surname` are stored per hero (`Model.kt:76-77`), so pools can grow freely
  (`Rng.pick` draws once whatever the pool size, `Rng.kt:76-79`); new name-like fields are nullable with defaults and,
  where they describe the past, derived from recorded counters rather than rolled.
- Prerequisites: split `migrations` into run and legacy tables (2.7); the run step must also pass the embedded
  profile through the legacy steps; update the tripwire at `MigrationTest.kt:123`; add a `v2_…json` fixture and keep
  the v1 fixture forever.

### E. Regression tests

App tests need the seam in A (fake `GameRepository` whose `commit` waits on a gate or throws). Suggested homes:
`app/src/test/.../GameViewModelTest.kt` (JVM), `core/src/test/...` for the rest.

| Finding | Class · test | Asserts |
|---|---|---|
| F01 | `GameViewModelTest.twoUpgradesWithHeldCommitsBothSurvive` | both levels present, points reduced by both costs, last commit equals the UI state |
| F01 | `…upgradeThenBeginEraKeepsBoth` (run with both release orders) | committed run is new and live, its embedded profile and the legacy row both hold the upgrade, UI is `Playing` |
| F01 | `…doubleBeginEraCreatesOneRun` | exactly one commit with a new `runId` |
| F01 | `…doubleClaimAwardsOnce` | points rise once; `claimedRunIds` holds the run |
| F01 | `…commandForAPreviousRunIsDropped` | no commit, no error |
| F01 | `…runEndScreenSurvivesRecreationAfterClaim` | new ViewModel on the same repository shows `RunEnded(claimed = true)`, not `Title` |
| F02 | `…corruptSaveShowsRecoveryAndLeavesBytes` / `…newerSchemaIsNotReset` | `LoadFailed`; repository never asked to commit |
| F02 | `…failedCommitKeepsCommittedStateAndClearsBusy` | UI still shows the pre-command state, `busy == false`, `saveError != null`; Retry commits a state equal to the pure engine result |
| F02 | `…cancellationDuringCommitNeverPublishesUnsaved` | no emission of the new state; repository holds old or new, never a mix |
| F02 | `SaveStoreTest.corruptPayloadYieldsFailedAndRowIsUntouched` (instrumented) | row bytes identical after `load()` |
| F02 | `LoadValidationTest.unknownContentIdIsReported` / `.missingRngStreamIsReported` | `validateLoaded` lists the problem and does not throw |
| F03 | `DayReportTest.reportAndArchiveAreTheSameEdition` | forge, list, hone, buy a tool, End Day: `Gazette.edition(resolution.events, …) == Gazette.edition(state.eventsForDay(day), …)` and each preparation event id appears once |
| F03 | `…signatureForgedWhilePlanningLeadsThePaper` | the `SIGNATURE_DISCOVERED` text is in the lede |
| F03 | `SaveFixtureTest.endDayOnFixtureReportsTheWholeDay` | ids of `resolution.events` equal ids of `eventsForDay(61)` |
| F07 | `ShopAccountingTest.recordedCashEqualsGoldDelta` | Merchant's Favor active, buyer trades in: Σ(`cashPaid + saleBonus`) over visits == gold after − gold before (no tribute) |
| F07 | `…commissionOnlyDayReportsNoVisitorPurchase` | browse purchases 0, commissions 1; the visitor ratio never exceeds 1 |
| F07 | `…collectorPurchaseIsAVisitWithItsPrice` | via the existing `fire(s, "collector")` helper (`WorldEventsAndGenerationsTest.kt:212`) |
| F07 | `…fatalExpeditionCountsAsLost` | tally "lost" includes the death |
| F08 | `ChampionSelectionTest.eliteBaneBearerIsChosenOnlyAgainstAWarlord` | four otherwise equal heroes; bearer in `outlook.champions` iff pressure ≥ `warlordPressure`; `town.championIds` agree |
| F09 | `VersionPolicyTest.newRunStampsRulesContentAndBalance` / `…saveFromNewerRulesIsRejected` | |
| F09 | `…catalogFingerprintMatchesItsVersion` / `…balanceFingerprintMatchesItsVersion` | fail until the version is raised |
| F11 | `LocaleDeterminismTest.thirtyDaysEncodeIdenticallyUnderThreeLocales` | `Locale.setDefault` US / GERMANY / `ar-SA` (restored in `finally`): `SaveCodec.encodeRun` equal |
| Migration | `SaveFixtureTest.v1FixtureMigratesWithSameFacesAndUntouchedHistory` | `appearance == floorMod(id.hashCode(), 5)` for all heroes; `rng`, `events`, serials equal the v1 decode; End Day accepted |
| Migration | `MigrationTest.runStepIsNotAppliedToTheLegacyDocument` / `…v1VisitReasonsDecodeAsEnum` / `…weaponLocationDiscriminatorsArePinned` | |
| RNG safety of B | `ShopRecordTest.captureDoesNotMoveAnyStream` | End Day on the fixture: `rng.streams` and event texts equal a golden recorded from the pre-change build |
| Presentation | `PresentationTest.acknowledgeChangesOnlyTheAckField` | `after == before.copy(unacknowledgedDay = null)`; same `rng`, same `nextEventSerial` |
| Presentation | `…everyPlanningCommandIsRejectedUntilAcknowledged` | each of the eleven commands and a new `EndDay` → `DayNotAcknowledged` |
| Presentation | `…blessingIsRejectedBeforeAndAcceptedAfter` / `…finalDayAcknowledgeThenClaim` | |
| Presentation | `GameViewModelTest.skipWatchAndRestartEncodeIdentically` | three sessions (ack at once; step every encounter; recreate mid-way then ack): byte-equal `encodeRun` |
| Process death | `…deathBeforeCommitReplaysTheSameDay` | commit never completes; new ViewModel shows the previous day; End Day again gives the same resolution |
| Process death | `…deathAfterCommitReopensThePresentationWithoutResimulating` | `processedEndDayIds` unchanged |
| Process death | `…deathBeforeAckReopens` / `…deathAfterAckDoesNot` | |

### F. Long saves

**A production-shaped soak** (JVM, forced survival): `SimulationDriver(eventRetentionDays = 0, maxForgesPerDay =
null)`, 1,000 and 2,000 days, at least one hoarding policy (BALANCED_FAIR) and one tidy one (BALANCED_ACTIVE).
Every 100 days record: `SaveCodec.encodeRun(state).length` and the envelope overhead; encode ms and decode ms;
weapons by location (Storage / Shelf / Owned / Lost-returnable / Lost-terminal / Destroyed); heroes by fate;
commissions by status; `processedEndDayIds.size`; events total and kept-forever; longest and mean weapon history and
its non-combat share; `lastResolution` bytes. Assert budgets (PROPOSED numbers, to be set from the first run):
bytes at day 1,000, encode + decode time, and bytes per day after day 200 **excluding Storage weapons**. Replace
`assertTrue(perf.p95Ms > 0.0)` (`SoakTest.kt:78`) with a real bound. On a device: load time and `SetPrice` commit time
on a generated 1,000-day save, and Market / Gazette composition with that stock.

**Bounded policies, none of which deletes player property:**

| Source | Policy | Why it is safe |
|---|---|---|
| Closed commissions | prune `COMPLETED` / `EXPIRED` / `DECLINED` older than `eventRetentionDays`, in the End Day compaction block | nothing reads them (`Market.kt:186`; UI filters open ones); ids come from a serial |
| `processedEndDayIds` | keep the last few | only the latest day can be retried; older states no longer exist. With A, the app never sends an old id |
| Returnable lost blades | treat `seized` / `lost with …` as terminal once Heroic Inheritance has fired its 3 times (`WorldEvents.kt:194`, `canFire` `:46`) | from then on no rule can bring one back; fame ≥ threshold and signatures stay, as today |
| Kept-forever events | demote `WORLD_EVENT` and `HERO_ARRIVED` to ordinary retention; keep sieges, deaths, retirements, guilds, milestones, signatures, artifacts | they are the bulk of the ~1.2 kept events a day and are not "rare milestones" (GDD 13.3) |
| Non-combat weapon history | keep first and last ownership entries; add `Weapon.ownerIds` (distinct, capped), backfilled from history | `Legacy.closeRun` reads owners from history today (`Legacy.kt:54`) — the list must exist before entries are dropped |
| Dead / retired heroes | **leave** (~33 B/day) | they are read by id: fallen-owner names (`WorldEvents.kt:101`), legend owners (`Legacy.kt:55`), guild-founder invariant (`Invariants.kt:37`), and descendant eligibility (`WorldEvents.kt:329`) — pruning would change outcomes |
| **Unsold storage** | **no automatic deletion.** Measure first; then a design decision between a visible capacity rule with bulk salvage / donate, and cheaper storage (lazy lists; typed instead of pre-rendered history text) | it is the player's stock; it is also ~2 KB a day under a hoarding policy |
| Recoverable (`WITH_MERCHANT`) | nothing | already bounded by an invariant in a9c81 |

Cheap wins unrelated to policy, to weigh against their migration cost: store the payload as nested JSON or rely on the
existing Room `schemaVersion` column (−13 % of bytes); stop embedding a second copy of the profile semantics; make the
archive lazy and index events by day once.

---

## 5. Where the review is wrong or imprecise

1. **F02**: "the exception is unhandled and the busy state is not restored" — the process dies; nothing is left stuck.
   The serious consequence it does not state is the **crash loop** on a bad row.
2. **F07**: lists what the cash line omits (bonus, collector) but not what it wrongly **includes** (warlord tribute,
   `Gazette.kt:101`), nor that material and tool spending are unrecorded, nor that the simulator's revenue figure has
   the same hole.
3. **F03**: "Forge/list/hone/tool" understates it — signature discoveries, forge milestones, blessing choice and the
   day-1 arrival events are also missing, and the archive diverges in three further ways.
4. **F01**: correct as far as it goes; it misses that the run-end screen is unrecoverable after claim, the hard-coded
   `claimed = false`, the dead `continueRun` path and the unguarded double Begin era.
5. **§6.6**: misses that a returned legend re-qualifies for the board on its own, ownerless, and can multiply.
6. **§6.2 / F05 context** is out of date since `d7b3283`: the Signboard now raises the customer cap.
7. **F11** is exact as written (it names only the `"%.2f"` calls). The broader worry about `lowercase()` /
   `uppercase()` that sometimes accompanies it does not apply to Kotlin.

Everything else I checked in sections 4.3, 5, 6.6, 9 and 12 matches the source, including the review's line ranges.
