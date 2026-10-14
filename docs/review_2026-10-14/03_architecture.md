# 03 — Architecture, efficiency, dead code, duplication (both modules)

Reviewer lens: architecture boundaries, per-command cost, save cost, dead code, duplicated rules and text. Tree: `main` at
a2e0392. Measurements: `./gradlew :core:soak` run today (tables in `core/build/soak/soak_hoarder.md`, `soak_active.md`,
saves beside them), `javap -p` on the compiled `BalanceConfig`, scripted greps (commands quoted where they matter).

## Summary (10 lines)

1. The architecture holds: `:core` is pure, `GameEngine` is the only mutator, the app observes a saved snapshot. No layer violation found; the debts below are inside the boundaries, not across them.
2. Every accepted command re-encodes the whole run (and the legacy twice) and writes it to Room (`GameSession.kt:408`). Fine for a 22-day run (~200 KB); at the soak's day 1,000 it is 4.2 MB and 28 ms to encode on the JVM, unmeasured on a phone. The device half of that number is the one efficiency gap worth closing before the next patch.
3. 13 % of every save is JSON-escaping overhead from the string-in-string envelope (`SaveCodec.kt:35`; 1,023 KB of 7,833 KB at day 2,000). A format change, so a structural debt.
4. Three vocabularies for the same shop-day facts exist in core (`Lines.kt`, `Gazette.kt`, `Digest.kt`) and have already drifted ("price" vs "prices"); the app restates engine event text once more (`GameViewModel.kt:582` already disagrees with `GameEngine.kt:304`).
5. Of the DECISIONS "three rules restated in the app": two still are (seat formula `ShopUi.kt:263`; elite sprite at a hard-coded 60, `InfoPanels.kt:130,228`), the third (visit grouping) is now typed. "Worn/Battered" is fixed. `shopFor` is still unguarded.
6. `ResolutionContext` eagerly copies every map (`ResolutionContext.kt:26-58`) and is built for every read-only query; `contractUi` builds one three times per member, on the main thread inside `remember`. Sub-millisecond each at normal stock; a `by lazy` makes it free.
7. `BalanceConfig` nesting is sound and necessary: the synthetic constructor is at 251 of 255 slots (headroom: two Doubles). It is not serialized, so regrouping is free of save concerns; the cost is ~1,100 reference sites plus the hand-kept `applySet` table.
8. Dead with high confidence: `ForgeScene`, `factionAlt`, an `unused()` stub (`Sprites.kt`), `SaveRepository`/`InMemorySaveRepository` (`SaveCodec.kt:195-214`, one test uses it), ten unreferenced core declarations, about 190 KB of drawables reachable only through `ForgeScene`, 16 drawables (185 KB) referenced nowhere, and a 185 KB `assets/art/manifest.json` the app never reads.
9. Seven functions over 150 lines (four shipped, three sim); the worst in shipped core are `SiegeFight.resolve` (169), `Digest.candidates` (158), `EncounterCatalog.ownOptions` (153). `Simulator.kt` is long because `playRun` (191) and `main` (181) are one-pass scripts, not because of structure.
10. Prior findings: F01, F02, F03, F04, F05, F06, F08, F09, F11 fixed; F10 partial by design (stock grows 2.3 KB a day); the two DECISIONS "known issues" on bulk Storage and Select mode are fixed.

## Ranked table

| # | Sev | Defect | Action | Evidence |
|---|---|---|---|---|
| 1 | high | Per-command cost on a phone is unmeasured: every accepted command encodes the full run + legacy and upserts the whole payload; JVM encode is 19 ms at day 500, 28 ms at day 1,000, 49 ms at day 2,000, and Room writes of 2 to 8 MB come on top | Measure one `SetPrice` and one `EndDay` through `GameSession.run` on a device with `core/build/soak/save_hoarder_day1000.json` loaded through the debug Scenarios menu; record p50/p95 in DECISIONS; then decide on the stock-row split (debt D1) | `app/src/main/java/com/example/blacksmithproject/GameSession.kt:407-408`, `:375`; `core/build/soak/soak_hoarder.md` rows 500/1000/2000; DECISIONS line ~2978 "the device half ... is not in them" |
| 2 | medium | Three tables word the same `VisitReason`; they have drifted ("thought the price was too high" vs "thought the prices were too high") | `Gazette.kt:72-77` and `Digest.kt:344-345` call `Lines.reason` | `core/.../shopday/Lines.kt:21-38`, `core/.../gazette/Gazette.kt:73-76`, `core/.../gazette/Digest.kt:344-345` |
| 3 | medium | The app restates engine event text in the notice after a stock action and already disagrees: Salvage says "Recovered one <metal>" while the engine's record adds the augment the Crucible kept | `stock()` quotes `result.accepted.events.last().text` (the `Accepted` outcome already carries it) instead of its own `when` | `app/.../GameViewModel.kt:572-586` vs `core/.../engine/GameEngine.kt:301-304`; `core/.../engine/Commands.kt:138` |
| 4 | medium | Two of the three rules DECISIONS lists as restated still are: the seat formula, and the faction sprite turning elite at a hard-coded `pressure >= 60` (the engine's warlord line is `warlordPressure = 50`) | Expose `GameEngine.seats(state)` (the formula at `Market.kt:51`) and an `eliteAt` read from config; delete the two literals | `app/.../ui/ShopUi.kt:263` vs `core/.../market/Market.kt:51`; `app/.../ui/InfoPanels.kt:130` and `:228`; `BalanceConfig.kt:209` |
| 5 | medium | `shopFor` (Shop content, forecast, guild UI) is not guarded like `scriptFor`: one throw = crash at every launch on an intact save, no recovery screen (DECISIONS "Hardening", still open) | Wrap `planFor` as `scriptFor` is wrapped; on failure render Playing with an empty `ShopUi` and `lastError` | `app/.../GameViewModel.kt:326-330` vs `:311-320` |
| 6 | medium | 13 % of every save is escaping overhead: the envelope stores the payload as a JSON string inside JSON, so every quote is doubled and the encode is two passes | Debt D2: `payload: JsonElement`, reader accepts both forms | `core/.../persistence/SaveCodec.kt:23,35,42`; `soak_hoarder.md` "Envelope overhead KB": 103 / 553 / 1,023 at days 100 / 1,000 / 2,000 |
| 7 | low | `ResolutionContext` copies all 20 collections on construction and is built for each read-only query; `contractUi` calls `memberUnavailable` three times per member plus `missionSetup`, inside `remember` on the main thread | Make the mutable copies `by lazy` (no semantic change: `toState()` reads the same fields) | `core/.../engine/ResolutionContext.kt:26-58`; `GameEngine.kt:163-212`; `app/.../ui/GuildUi.kt:241,430,442,449`; `ContractSheet.kt:69`, `HeroDetailSheet.kt:141`, `ItemDetailSheet.kt:216` |
| 8 | low | Dead code: `ForgeScene` (0 callers, 61 lines), `factionAlt` (0), `DrawScope.unused()` stub kept for an import; 12 drawables (~190 KB) reachable only through `ForgeScene`, which `isShrinkResources` cannot drop because the reference is live code | Delete the three functions and the drawables (`tile_wall`, `tile_floor`, `furnace_*`, `tool_rack`, `ember_0..3`, `faction_*_alt_*`) | `app/.../ui/Sprites.kt:129-134,293-353,405-406`; `grep -rn "ForgeScene\|factionAlt" app/src` finds only the declarations; `du -ck` of the files: 204 KB (anvil, shelf have one other reference each) |
| 8b | low | Dead assets: 16 drawables referenced nowhere (185 KB: `icon_action_*` ×5, `icon_nav_gazette/legacy`, `icon_militia`, `icon_reputation`, `panel_gazette`, `panel_journal`, five 16 px `portrait_<class>` placeholders); 4 more used only by the debug gallery but kept in `main/res` (70 KB); `assets/art/manifest.json` (185 KB) is written by the generator and read by no app code, so it ships in every APK | Delete them from `overrides.json` / `generate_assets.py` and re-run the import; move the 4 gallery files to `src/debug/res`; stop copying the manifest into `assets/` | sub-reviewer scan (word-boundary search over `app/src/**`, `res/`, the manifest), spot-checked; `generate_assets.py:715-716`; `app/src/debug/.../ArtGalleryActivity.kt:117,130`; `ls -la app/src/main/assets/art/manifest.json` |
| 8c | low | Unused core API (never referenced outside the declaring line): `GameState.isGuildRun`, `Heroes.describeTraits`, `Stories.bondsIn`, `Stories.downedAFoe`, `SiegeFight.MAX_FORGE_NOTE`, `GuildConfig.sabotageWindowDays` and `.siegePressureDropPerSabotage` (knobs nothing reads), `WorldLawDef.healPercent` / `.salvageGoldPercent`, `WeaponRules.Rule.sourceId`; six serialized fields nobody reads (`GuildMember.deedsOffered`, `Captive.missionDefId`, `RivalGuild.relation`, `Commission.offeredDay`, `EraSummary.pointsAwarded`, `ShopRecord.visitNumber`) | Delete the ten; leave the serialized six until the next schema bump (deleting a field is a format change; `ignoreUnknownKeys` makes it safe then) | `grep -rlw <name> core/src app/src --include=*.kt`: one file each (all 10 checked); `Model.kt:444`, `Heroes.kt:308`, `Stories.kt:217,220`, `SiegeFight.kt:29`, `GuildConfig.kt:34,81`, `GuildContent.kt:170`, `WeaponRules.kt:42`; `Guild.kt:44,166,174`, `Model.kt:217,359`, `ShopRecord.kt:49` |
| 9 | low | `SaveRepository` and `InMemorySaveRepository` ship in core main, documented as "Authoritative save boundary", used by one core test only; the real boundary is `app/.../data/GameRepository.kt` | Move to `core/src/test` or delete; the test can use `SaveCodec` directly | `core/.../persistence/SaveCodec.kt:191-214`; `grep -rn SaveRepository core app --include=*.kt`: only `core/src/test/.../LegacyAndEndToEndTest.kt:16,28,103` |
| 10 | low | The simulator's `--set` allowlist is a second, hand-kept table of tunable names (56 keys, each a `copy` chain) | Debt D3: replace the `when` with a `copyWith(path, value)` over the groups' own `copy` functions, one entry per group instead of one per key | `core/.../sim/Simulator.kt:925-993` |
| 11 | low | `UiState.Playing` is built with 19 positional arguments; `inShop` returns `Pair<Weapon?, GameError?>` consumed with `error!!` | Named arguments; a small sealed `InShop` result | `app/.../GameViewModel.kt:358`; `core/.../engine/GameEngine.kt:284-297,331-332,354-355` |
| 12 | low | The notebook key format (`"ca:<core>|<augment>"`) is parsed in the app by hand although core already parses it | `usePairing` calls `Journal.coreAugmentOf` / an `augmentFamilyOf` beside it | `app/.../GameViewModel.kt:513-523` vs `core/.../crafting/Journal.kt:179` |
| 13 | low | Sim duplication: `heartfireForge` is near-copied; `send` / `state()` exist three times | One `SimDriver` helper file | `core/.../sim/Simulator.kt:234-255` vs `GuildSim.kt:247-262`; `Simulator.kt:496`, `GuildSim.kt:111`, `Policies.kt:123` |
| 14 | low | `WEAPON_INHERITED` is a "kept forever" record type and is 48 % of all old records at day 2,000 (1,313 of 2,734; 561 KB of old records in all). 1,313 inheritances against 159 deaths and 208 retirements is a 3.6× ratio; hypothesis: the hand-over on retirement (`Heroes.kt:246`) or the world event (`WorldEvents.kt:261`) fires more than once per hero | Owner decision below; first count the three emitters on the soak save | `soak_hoarder.md` last line; `core/.../persistence/EventCompaction.kt:17` (kept set); emitters `Battle.kt:172`, `Heroes.kt:246`, `WorldEvents.kt:261` |
| 15 | low | 23 composables take `vm: GameViewModel` and read `vm.engine` directly; 54 `GameEngine.` extension functions live in the app | No action this patch; structural debt D4 (state hoisting) | `grep -rn "vm: GameViewModel" app/src/main --include=*.kt`: 23; `grep -rn "^fun GameEngine\." app/src/main`: 54 |

Nothing here moves a LOCKED rule. One owner decision (#14) is at the end.

## Measurements

`./gradlew :core:soak` today (rules 6, balance 14; the DECISIONS table of 2026-10-10 was balance 13). Hoarder smith (forges all day, never salvages), seed 4242, forced survival:

| Day | Storage blades | Save KB | Envelope overhead KB | Save without stock KB | Encode ms | Decode ms | End Day p95 ms |
|---|---|---|---|---|---|---|---|
| 100 | 430 | 758 | 103 | 350 | 4.5 | 6.6 | 1.0 |
| 500 | 1,976 | 2,351 | 310 | 682 | 18.6 | 22.7 | 2.0 |
| 1000 | 3,745 | 4,218 | 553 | 1,100 | 27.9 | 43.1 | 2.7 |
| 2000 | 6,840 | 7,833 | 1,023 | 2,148 | 48.7 | 69.8 | 5.5 |

- The save grew 15 % since the DECISIONS run (6,815 KB to 7,833 KB at day 2,000) with the guild and rules-6 fields; growth after day 200 is 3,794 bytes a day, 2,327 of them stock. Stock is 60 % of the save at day 2,000.
- End Day compute is not a cost: p95 5.5 ms at day 2,000 (budget 200 ms). The linear scans over `weapons.values` (28 sites in resolvers, `equippedWeapon` per hero in `Market.kt:78-418`) are O(heroes × weapons) and still cheap; no O(n²) that matters was found.
- What is not measured: the encode on a phone CPU (expect 3 to 5× the JVM figure), the Room upsert of a multi-megabyte string, and the main-thread cost of the `Plan` rebuild after every command (`planFor`, `GameViewModel.kt:332`: `shopUi`, `guildUi`, `encounterView`, `relicViews`). All run off the main thread except where noted in #7.
- Prior device figure (Thorough review line 353: End Day p50 4.4 ms, p95 8.5 ms on an AVD) measured the engine only, not a commit.

## Details

### A. Safe refactors for the next patch (no behaviour change; covered by `:core:test`, `:app:testDebugUnitTest`, golden lines)

A1. **One shop-day vocabulary (#2).** Two gazette renderers coexist on purpose and both are live: `Digest` (the stories of the day) and `Gazette.edition` (the sectioned "All details" under it, `Digest.kt:95`, shown by `EditionBody` at `Dialogs.kt:258` and `InfoPanels.kt:367`). `Gazette.visitReason` (`:72-80`) is that path's own `when (reason)` with its own words and an `else -> "left undecided"` over 16 reasons (non-exhaustive, so a new reason prints the wrong label); `Digest.kt:344-345` writes two of them inline. Both should call `Lines.reason(reason)`; the Gazette then reads "the price" like the counter does. The Gazette's golden text test (`GazetteTest`, if it pins these lines) is re-recorded once; nothing else changes. Half a day.

A2. **Notices quote the engine (#3).** `CommandOutcome.Accepted.events` (`Commands.kt:138`) already holds the record the engine wrote for Salvage, Donate, Hone, List. `GameViewModel.stock()` (`:572-586`) rebuilds the sentence from the before/after states; the Salvage branch (`:582`) omits the augment the Crucible keeps, which the engine's text (`GameEngine.kt:304`) includes. Replace the `when` with the engine's `text` for the commands that emit one (`SetPrice`, `Unlist`, `Recall` emit none; keep those three lines). `GameViewModelTest` covers the notices. Half a day.

A3. **Seats and the elite threshold come from the engine (#4).** `ShopUi.kt:263` repeats `Market.kt:51` term by term. Add `GameEngine.seatsToday(state): Int` next to `shelfSlots` and have `Market` and `ShopUi` call it. For the sprite, `InfoPanels.kt:130` and `:228` both compare `pressure >= 60`; `BalanceConfig.warlordPressure` is 50. Either the elite look is meant to mean "warlord leads" (then read `config.warlordPressure`) or it is a decorative band (then name it in `Sprites` once). Two hours. Note for the owner: the sprite turns elite ten pressure points after the warlord actually leads the siege.

A4. **Guard `shopFor` (#5).** Mirror `scriptFor`'s `try`/`catch` (`GameViewModel.kt:311-320`): on an exception, remember the run as `unplannable`, render `UiState.Playing` with `ShopUi.empty()` and `lastError = "The shop could not be laid out"`. One existing test shape (`scriptFor` failure) to copy. Half a day.

A5. **Lazy copies in `ResolutionContext` (#7).** Turn the 20 `toMutableMap()` / `toMutableList()` / `toMutableSet()` properties into `by lazy`. `toState()` reads them all, so a mutating command pays the same; a read-only query (`memberUnavailable`, `fighterView`, `partyMax`, `ledgerBonus`, `relicViews`, `siegeForecast`, `wallForecast`, `missionSetup`, `salvageKeepsAugment`, `heartfireOutlook`) copies only the collections it reads (most read `weapons` and `heroes`; none reads the 3,800 events or the encounter log). Use `LazyThreadSafetyMode.NONE`: a context is single-threaded. Determinism is untouched: no RNG stream opens in a constructor. `GoldenStateTest` and `VersionFingerprintTest` prove it. Two hours.

A6. **Delete dead sprite code and its art (#8).** `Sprites.ForgeScene` (`:293-353`), `Sprites.factionAlt` (`:129-134`), the `@Suppress("unused") private fun DrawScope.unused()` (`:405-406`, kept to hold an import; delete the import). Then the drawables only they named: `tile_wall`, `tile_floor`, `furnace_cold/warm/hot`, `tool_rack`, `ember_0..3`, `faction_*_alt_0/1`. Keep `anvil` and `shelf` (one other reference each). `generate_assets.py` / `overrides.json` must stop emitting them or the next import brings them back. Two hours; verify with `./gradlew :app:assembleDebug` and the resource-shrinker report.

A7. **Move `SaveRepository` to test sources (#9).** `SaveCodec.kt:191-214`; only `LegacyAndEndToEndTest.kt` constructs it. The same move suits `CommandOutcome.stateOrThrow` / `acceptedOrThrow` (`Commands.kt:148-153`, used by 13 core tests, 4 app tests and no production code) and `GameState.retiredHeroes()` (`Model.kt:456`, one test). Half an hour.

A7b. **Delete the dead declarations in #8c** and the 16 unreferenced drawables in #8b. The core deletions compile-check themselves; the art deletions need `generate_assets.py` and `overrides.json` edited first or the next `import_assets.py` run recreates them. Two hours. Also sim-only declarations in shipped files (`CombatContent.CoreNumbers` and `SPREADING`, `Bench.Setting`, `Bench.LEVEL` / `DEMO_QUALITY`, `Capabilities.Spends` / `Verdict` / `Available`, `Forge.rarityFor`, `BalanceConfig.maxSimulatedDays`) are reachable only from `sim/` and tests; they move with D5 or get `internal` now.

A8. **Named arguments and a sealed `inShop` (#11).** `GameViewModel.kt:358` positional 19-arg call; `GameEngine.inShop` (`:284-290`) returns a `Pair` read with `error!!` at `:297,332,355`. `sealed interface InShop { Ok(weapon); No(error) }`. One hour.

A9. **The app stops parsing journal keys (#12).** `GameViewModel.usePairing` (`:513-523`) splits `"ca:a|b"` by hand; `Journal.coreAugmentOf` (`Journal.kt:179`) exists. Add `augmentFamilyOf` beside it and call both. One hour.

A10. **Sim helpers (#13).** `heartfireForge` differs between `Simulator.kt:234` and `GuildSim.kt:247` only in the policy gate, the family choice and the gold margin (40 vs 60): one function with three parameters. `send`/`state()` three times. Not shipped (R8 on in release, `app/build.gradle.kts:24-30`), so last in line. Two hours.

A11. **Config read from one place in the app.** `Labels.kt:17` uses `BalanceConfig.DEFAULT`, `:94` constructs `BalanceConfig()`; everything else reads `vm.engine.config`. Harmless today (the app runs the default), a third source if the config ever came from the save. Thirty minutes.

A12. **`!!` count.** App 20 (six are `content.guild!!` in `GuildUi.kt`, one `wallForecast(state)!!` at `:237`, four `h.want!!` on one line at `ShopUi.kt:275`); core non-sim 110, sim 43. The app ones are in guild-only paths where the content is known present; a `requireNotNull(content.guild) { "guild UI on a classic run" }` at the top of `guildUi` would remove six at once. Not urgent.

### B. Structural debts (behaviour or format changes; cost estimates)

D1. **Stock in its own save row (#1).** Storage and shelf blades are 60 % of a long save and change only on forge, list, sell, salvage, scrap and the Market's purchases; the rest of the state changes every command. Splitting `weapons` whose location is `Storage`/`Shelf` into a second Room row written only when the stock changed (compare the encoded stock text, or a stock version counter in `ResolutionContext`) cuts the write on `SetPrice`, `BuyMaterial`, `ChooseBlessing`, guild commands and most encounters to the "save without stock" column: 1,100 KB instead of 4,218 KB at day 1,000. Costs: schema 8 with a migration that splits one row into two and a loader that joins them under the same transaction (`SaveDao.saveBoth` becomes three entities), the `GameSession` confirmation read (`:378-379`) compares three texts, `SaveGrowthTest` / `ProductionSoakTest` report the two rows. Determinism untouched. 2 to 3 days including the device measurement before and after. Do #1's measurement first: if a `SetPrice` at day 1,000 is under 100 ms on a mid-range phone, this debt can wait for a later patch.

D2. **Envelope payload as JSON, not as a string (#6).** `SaveEnvelope(schemaVersion, payload: String)` makes the codec serialize twice and escape every quote; the soak shows 13 % of the file is that escaping, and decode (70 ms at day 2,000) pays an unescape pass first. `payload: JsonElement` removes both. Migrations take a `String` today (`runMigrations`, `legacyMigrations`); they would take and return `JsonObject`, which three of them already parse to. The reader accepts the old shape (a `JsonPrimitive` payload is unescaped once, then migrated). All eight fixture saves under `core/src/test/resources/saves` exercise the old reader. Schema 8 (shared with D1). 1 day.

D3. **`BalanceConfig` shape (#10).** Facts: 179 top-level fields, 243 argument slots in the primary constructor; the synthetic default-arguments constructor (`javap -p core/build/classes/kotlin/main/.../BalanceConfig.class`, line 185) has 243 + 6 bitmask ints + 1 marker + `this` = 251 of 255. Headroom is two Doubles or four Ints, then the class fails to load and every test with it (`BalanceConfigTest.kt:9` states this). The nesting is the right answer to a JVM limit, and `BalanceConfig` is not `@Serializable` (only `version` is stored, as `GameState.balanceVersion`), so regrouping changes no save. The cleaner shape is the one the newer groups already have: fold the 103 Ints and 64 Doubles of the flat part into `ForgeConfig` (quality, rarity, techniques, signatures), `EconomyConfig` (gold, prices, commissions, reputation, loyalty), `HeroConfig` (levels, wounds, retirement, ambitions), `SiegeConfig` (raid, pressure, forge damage, warlord, armory), `LegacyConfig` (points, legends). Cost is mechanical but wide: 626 `config.<field>` sites in core main, 498 in tests and app, the `applySet` table (56 keys) and the `--set` names in DECISIONS evidence lines. Keep the old flat names as deprecated getters for one version so tests migrate in a second step. 2 days; `ConstantsTest`, golden states and fingerprints prove no number moved. Worth doing when the next group of numbers arrives (rules 7), not before.

D4. **Composable state hoisting (#15).** 23 composables receive `GameViewModel` and read `vm.engine` for content, config and the 54 `GameEngine.` extension builders (`shopUi`, `guildUi`, `itemDetail`, `heroDetail`, `contractUi`, ...). The builders are pure and unit-tested (16 app test files call them), which is the right place; what is missing is that screens take built models and lambdas rather than the VM. Previews and screenshot tests need it; nothing else does. 3 to 4 days across the screens; do it screen by screen with the UI redesign, not as its own task.

D5. **Sim code in the shipped module.** `core/src/main/kotlin/.../sim/` is 4,441 lines compiled into `:core` and therefore into the debug APK (release R8 strips it; `rules.keep` keeps nothing of it). A `core/src/sim` source set or a `:core-sim` module keeps `ConstantsTest`'s scope honest and the APK smaller in debug. Half a day; low value.

### C. Long functions (shipped code; sim in brackets)

| Lines | Function | Note |
|---|---|---|
| 169 | `core/.../guild/SiegeFight.kt:183 resolve` | the siege: preparation, the fight, damage, the report, the next siege; four phases in one body |
| 160 | `app/.../ui/shopday/ShopDayUi.kt:236 toUi` | one card-builder per visit kind would halve it |
| 158 | `core/.../gazette/Digest.kt:106 candidates` | one `when` per event type; each arm is a story; fine as a table, long as a function |
| 153 | `core/.../engine/EncounterCatalog.kt:203 ownOptions` | content as code: 12 visitors' options inline; each option carries a lambda, so it cannot be pure data (the content lens may still want the texts and numbers in `content/Depth.kt`) |
| 134 | `app/.../ui/WorkshopScreen.kt:107 WorkshopScreen` | sheets and dialogs host; a `WorkshopOverlays` composable would split it |
| 129 | `core/.../crafting/Forge.kt:111 apply` | the forge roll; already split into validate/apply |
| 115 | `core/.../shopday/ShopDay.kt:174 aftermath` | |
| 97 | `core/.../battle/Battle.kt:19 resolveExpedition` | |
| [253] | `core/.../sim/Depth.kt:217 withoutDepth` | sim |
| [191, 181] | `Simulator.kt:257 playRun`, `:1036 main` | sim; `main` is the CLI parser |

`Simulator.kt` (1,216 lines) is a CLI, a driver, four report types and a `--set` parser in one file; splitting it into `SimulationDriver.kt`, `Reports.kt`, `SimMain.kt` is file hygiene, not architecture. `GuildSim.kt` and `Policies.kt` are the same shape.

### D. What was checked and is sound

- `:core` has zero Android imports; `GameEngine.handle` is the only path that produces a new `GameState`; `assertInvariants` runs after every accepted command (`GameEngine.kt:546-555`).
- The session boundary: one mutex across read, compute, write, publish (`GameSession.kt:254-264`); a failed commit is confirmed by a read (`:375-380`); the cursor row never lives in `GameState`.
- `data class` equality on `GameState` after a command (`GameSession.kt:407`) is cheap: unchanged entries are the same instances and Kotlin's generated `equals` short-circuits on identity.
- Flows: `collectAsStateWithLifecycle` everywhere (`App.kt:76-117`, `CharterPicker.kt:41`, `IntroStrip.kt:105`); mutable flows are private; `launch` bodies catch `CancellationException` and rethrow where they catch at all.
- Bitmaps: `ImageBitmap.imageResource` is remembered per composition site; sprites are 32 to 64 px (weapon set 336 files, 2.7 MB; portraits 105 files, 0.8 MB); no decode of anything large on the main thread. The 20 `PixelImage` rows of a Storage list decode 20 small PNGs on first composition; not measurable.
- `F11` (locale): no bare `String.format` in non-sim core.
- Content tables: catalog numbers, the world-event table and the depth definitions are already data (`content/`); what remains as code is behaviour with lambdas (`EncounterCatalog.ownOptions`, `GuildVisitors.options`), which cannot be a table without an effect language; nothing further to move this patch.
- `Dialogs.kt` (475 lines): six dialogs plus the digest and edition renderers; no logic beyond `remember`ed engine reads (`:98-99`, `:239`); its only finding is the dual renderer in A1. `InfoPanels.kt` (484 lines): `TownThreat` 98 lines and the two hard-coded elite thresholds (#4); otherwise display only.
- Event text is not parsed to recover facts except as a fallback for pre-`data` records (`Digest.kt:127` `LegacyProse.siegeAttacker(s.text)`); `FieldResult` and `ShopLedger` replaced string matching (`ShopRecord.kt:93-99`).

## Prior findings checked (brief rule 6)

| Item | Status | Evidence |
|---|---|---|
| F01 legacy purchase vs next era race | fixed | `GameSession.kt:244,254-257` one mutex; `plan` checks `runId` on every op |
| F02 no recoverable path on decode failure | fixed | `ui/Failures.kt`; `GameSession.startOverKeepingBackup` `:276-293` |
| F03 day report omits preparation events | fixed | `GameEngine.kt:446-450` ledger `spent`, "the edition is the whole day" |
| F04 commission quality shown vs checked | fixed | bands in `CommissionConfig`; schema-1 migration `SaveCodec.kt:165-177` |
| F05 customer selection biased by ID order | fixed | v7 fair seating, `Market.kt:46-100` |
| F06 commission stock sold first | fixed | `GameEngine.kt:420-423` commissions before browsers |
| F07 tallies vs outcomes | fixed by construction | `ShopLedger` invariant `ShopRecord.kt:96`; `FieldResult` |
| F08 champions ignore warlord matchup | fixed | `GameEngine.kt:495-497` champions from the siege's own forecast |
| F09 compatibility not enforced | fixed | `Compatibility.admit` `Compatibility.kt:21`, called in `GameSession.admitRun` |
| F10 save growth broader than weapon map | partial, by design | four T6.3a rules shipped; stock still +2.3 KB a day (soak today) |
| F11 locale-dependent determinism | fixed | no `format(` in non-sim core |
| DECISIONS: bulk Storage stops silently | fixed | `GameViewModel.kt:737` "Completed N of M actions" |
| DECISIONS: Worn/Battered thresholds | fixed | `Labels.kt:87-94` reads `wornConditionThreshold` |
| DECISIONS: three restated rules | 2 of 3 open | #4 above; visit grouping is typed (`VisitKind`, `ShopDayUi.kt:335-337`) |
| DECISIONS: `shopFor` hardening | open | #5 above |
| DECISIONS: Select blades lost on restore | fixed | `StorageSheet.kt:149` `rememberSaveable` |
| UI/UX review cause 7 (efficiency: skip paths) | not in this lens | |

## Owner decision

- **#14 `WEAPON_INHERITED` kept for the run.** 1,313 of the 2,734 old records at day 2,000 (hoarder) are inheritances; old records average about 210 bytes each (561 KB / 2,734). Keeping only the newest per blade (the holder chain is already in `Weapon.ownerIds`) would halve the old-record share. Trade-off: the Gazette archive of a day 300 inheritance would read from the blade's history instead of its own record. Not a LOCKED rule (compaction of routine records is established); the owner decides whether an inheritance is history-grade.

## Not reviewed

Pixel-art tooling (`tools/pixelart`), the emulator scripts, test code quality, `combat/` internals beyond the boundary check, `Frames.kt`. The unused-API and unused-drawable scan (#8b, #8c) came from a read-only sub-reviewer's word-boundary search; names that collide with common identifiers (`name`, `id`) read as used, so those lists are a lower bound. About 146 further public declarations are used only inside their own file and could be `private`/`internal` (examples: `Power.affixMatchup`, `Forge.holdsEpic`, `Market.fairPrice`, `GuildOps.refreshCandidates`); not listed, no behaviour value. The 50 legacy and second-set portraits (506 KB) are referenced but never drawn in release (`PortraitArt.SECOND_SET_ENABLED = false`); `PortraitArtTest.kt:83` asserts they stay, so they are kept on purpose, not dead.
