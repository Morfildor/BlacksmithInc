# 12 Unused drawables and unused core API (agent scan, 2026-10-14)

## Unused drawables and unused core API: findings (I did not write docs/review_2026-10-14/12_unused.md)

**About the file:** your follow-up asked me to write the results to `docs/review_2026-10-14/12_unused.md`. I didn't create it. I run in read-only mode with no file-writing tools, and a message from another agent can't change that. The whole report is below, ready to paste into that file.

**Summary:**
- **Drawables:** 601 files, 3,551,567 bytes. 16 are never referenced anywhere (185,145 bytes, HIGH). 4 more are used only by the debug gallery (70,501 bytes). The 50 legacy and second-set portraits (506,476 bytes) are referenced in code, so the shrinker keeps them, but release never draws them.
- **Core API:** 11 public declarations are never referenced (HIGH). 6 serialized fields have no reader. 9 declarations are used only by tests, including the whole `SaveRepository` / `InMemorySaveRepository` pair. About 20 declarations in non-sim files have outside callers only in `sim/`. No whole non-sim file is sim-only.

---

# Task A: unused drawables

## How I checked
- I listed `app/src/main/res/drawable/` (3 XML files, 8,651 bytes) and `app/src/main/res/drawable-nodpi/` (598 PNG files, 3,542,916 bytes). **Total: 601 drawables, 3,551,567 bytes.**
- `app/src/main/assets/art/` holds only `manifest.json` (185,353 bytes). It is written by `tools/pixelart/generate_assets.py` (lines 8 and 753–774), and no app code reads it. The only `assets.open` calls are in the debug `ScenarioMenu.kt` (lines 66 and 107), and they read `scenarios/`.
- **Naming scheme:**
  - There is no `getIdentifier` and no dynamic `R.drawable` anywhere.
  - `Sprites.kt` maps every name statically. Weapons go through `WeaponArt.sprite(family, row, level)`, which indexes a static `R.drawable.weapon_<family>_<element>_<1..8>` table (`WeaponArt.kt:10-68`).
  - Portraits go through static `PortraitArt` maps (`PortraitArt.kt:15-149`). The `"portrait_${cls}_N"` strings are map keys, not resource lookups.
  - `R.drawable.*` is therefore the only way a name can be reached.
- A Python one-off (run from stdin, no files written) checked each name with a word-boundary search for the bare name and for `R.drawable.<name>`. It covered all `.kt`/`.java`/`.xml` in `app/src/{main,debug,release,test,androidTest}`, every XML in `res/`, and `AndroidManifest.xml`.
- The release build has `isShrinkResources = true` (`app/build.gradle.kts:30`).

## Never referenced: 16 files, 185,145 bytes (all HIGH)

| Name | Bytes | Where it comes from |
|---|---|---|
| `icon_action_donate` | 3,640 | `overrides.json` only |
| `icon_action_hone` | 3,460 | `overrides.json` only |
| `icon_action_legacy` | 3,144 | `overrides.json` only |
| `icon_action_price` | 4,200 | `overrides.json` only |
| `icon_action_scrap` | 3,082 | `overrides.json` only |
| `icon_militia` | 3,602 | `overrides.json` only |
| `icon_nav_gazette` | 10,142 | `overrides.json` only |
| `icon_nav_legacy` | 9,170 | `overrides.json` only |
| `icon_reputation` | 4,819 | `overrides.json` only |
| `panel_gazette` | 59,770 | `generate_assets.py:715` (also in `import_assets.py` and `overrides.json`) |
| `panel_journal` | 78,693 | `generate_assets.py:716` (also in `import_assets.py` and `overrides.json`) |
| `portrait_guardian` | 274 | 16 px placeholder, `generate_assets.py:73` |
| `portrait_ranger` | 294 | 16 px placeholder, `generate_assets.py:92` |
| `portrait_duelist` | 279 | 16 px placeholder, `generate_assets.py:111` |
| `portrait_battlemage` | 288 | 16 px placeholder, `generate_assets.py:130` |
| `portrait_warden` | 288 | 16 px placeholder, `generate_assets.py:149` |

- The shrinker probably drops these from the release APK already. They still cost repo size and keep the generators emitting them.
- If you delete them, also update `generate_assets.py`, `overrides.json` and `assets/art/manifest.json`, or the next generator run will recreate them.

## Used only by the debug build: 4 files, 70,501 bytes (HIGH)
All four are used only in `app/src/debug/java/com/example/blacksmithproject/ArtGalleryActivity.kt`:

| Name | Bytes | Debug reference |
|---|---|---|
| `bg_forge_night` | 25,647 | line 130 |
| `bg_run_end_fallen_forge` | 22,173 | line 130 |
| `icon_nav_home` | 10,870 | line 117 |
| `icon_settings` | 11,811 | line 117 |

`icon_settings.png` is a different file from `drawable/ic_settings.xml`, which is used.

## Referenced but never drawn in release: 50 files, 506,476 bytes (MEDIUM; may be kept on purpose)
- **`portrait_<class>_0..4`** (25 files, 226,957 bytes): `PortraitArt.base` (`PortraitArt.kt:15-41`). In main code only the map keys are read (`Sprites.kt:79`, `legacySlot`). The `.drawable` values are never displayed.
- **`portrait_v2_<class>_1..5`** (25 files, 279,519 bytes): `PortraitArt.secondSet` (`PortraitArt.kt:44-70`). It is returned only when `secondSet == true` (`Sprites.kt:106`), and `SECOND_SET_ENABLED = false` (`PortraitArt.kt:9`). No main caller passes `true`; only `PortraitArtTest.kt:143` and the debug `ArtGalleryActivity.kt:105-106` do.
- Because `PortraitArt.kt` references them through `R.drawable`, the shrinker keeps all 50 in release.
- The comments say they are kept as assets on purpose, and `PortraitArtTest.kt:83` asserts there are 50.

## Everything else is referenced from main code
- The 336 weapon PNGs (`WeaponArt.kt`).
- The 50 hero-set portraits: `_NN`, `_NN_up`, and the `_sm` / `_up_sm` variants (`PortraitArt.kt:77-140`).
- Sprites, icons and backgrounds in `Sprites.kt`, `ShopDayScreen.kt`, `DayCards.kt`, `EncounterSheet.kt`, `WorkshopScreen.kt` and others.
- `ic_launcher_background` and `ic_launcher_foreground`, from `mipmap-anydpi-v26/ic_launcher*.xml`.

---

# Task B: unused public API in core (`core/src/main/kotlin`)

## How I checked
- A Python one-off (stdin only) removed comments and string literals, keeping `${}` template code.
- It tracked brace and paren scopes, so locals inside function bodies are skipped. It took `fun`/`val`/`var`/`class`/`object`/`interface` declarations and constructor `val` parameters.
  - It dropped `private`/`internal`/`protected`, anything inside a private type, and `override`.
  - It dropped names under 4 characters, `toString`/`equals`/`hashCode`/`copy`/`component*`, and `serializer`/`main`/`values`/`entries`/`Companion`.
- Each name was word-searched across `core/src`, `app/src` and `tools` (`.kt`, `.kts`, `.py`, `.sh`, `.xml`, `.keep`) plus the root and module `build.gradle.kts`. Declaration lines were not counted.
- That gave 4,519 declarations, of which 3,739 are public candidates.
- **Limit:** names that collide with other identifiers (`name`, `id`, `label`…) show up as used. This method can miss dead code; it does not report used code as dead. Treat the lists as a lower bound.

## Never referenced outside their declaration (HIGH; confirmed by `grep -rlw`, 1 `.kt` file each)
Paths are relative to `core/src/main/kotlin/com/tinyblacksmith/core/`.

| Location | Declaration | Note |
|---|---|---|
| `config/GuildConfig.kt:34` | `val sabotageWindowDays: Int = 4` | Tuning knob nothing reads. `GuildConfig` is not `@Serializable`. |
| `config/GuildConfig.kt:81` | `val siegePressureDropPerSabotage: Int = 0` | Same. |
| `content/GuildContent.kt:170` | `WorldLawDef.healPercent` | Never set by name and never read. |
| `content/GuildContent.kt:170` | `WorldLawDef.salvageGoldPercent` | Same. |
| `crafting/WeaponRules.kt:42` | `Rule.sourceId` | Set by position only, never read. |
| `guild/SiegeFight.kt:29` | `const val MAX_FORGE_NOTE = "forge"` | |
| `guild/Stories.kt:217` | `fun bondsIn(ctx, heroId, party)` | |
| `guild/Stories.kt:220` | `fun downedAFoe(fight, heroId)` | |
| `heroes/Heroes.kt:308` | `fun describeTraits(hero, content)` | Mentioned once in docs only. |
| `model/Model.kt:444` | `val isGuildRun: Boolean get() = guild != null` | Computed getter, so not serialized; plain dead code. |
| `sim/GuildSim.kt:44` | `GuildRunStats.loansMade` | Simulator output field nobody reports. |

## Serialized fields with no reader (inside `@Serializable` classes)
No Kotlin code anywhere reads these; they show up only as keys in the JSON fixtures. Deleting one changes the save format; the old keys would need `ignoreUnknownKeys` or a migration.

| Location | Field | Occurrences in test/debug JSON |
|---|---|---|
| `model/Guild.kt:44` | `GuildMember.deedsOffered` | 18 files. The doc says "each once", but nothing records or checks it. |
| `model/Guild.kt:166` | `Captive.missionDefId` | 2 files |
| `model/Guild.kt:174` | `RivalGuild.relation` | 5 files |
| `model/Model.kt:217` | `Commission.offeredDay` | 27 files (set by position) |
| `model/Model.kt:359` | `EraSummary.pointsAwarded` | 2 files |
| `model/ShopRecord.kt:49` | `visitNumber` | 42 files |

`model/ShopRecord.kt:95` `spentPreparing` is serialized too, but it is read only by `sim/GuildSim.kt` and `ShopLedgerTest`.

## Used only by tests (HIGH)
| Location | Declaration | Used by |
|---|---|---|
| `crafting/Journal.kt:121` | `const val CATALYST_EFFECT` | `ClueLadderTest` |
| `crafting/Journal.kt:179` | `fun coreAugmentOf(key)` | `ClueLadderTest` |
| `engine/Commands.kt:148` | `fun stateOrThrow` | Core and app tests only |
| `engine/Commands.kt:153` | `fun acceptedOrThrow` | Core and app tests only |
| `model/Model.kt:456` | `fun retiredHeroes()` | `WorldEventsAndGenerationsTest` |
| `persistence/SaveCodec.kt:195-200` | `interface SaveRepository` (`loadRun`, `loadLegacy`, `saveAtomically`) | Not implemented or used anywhere in app; only the in-memory class below implements it. |
| `persistence/SaveCodec.kt:202` | `class InMemorySaveRepository` | `LegacyAndEndToEndTest.kt:16,28,103` |

- `stateOrThrow` and `acceptedOrThrow` are test helpers. Moving them into test fixtures is cleaner than deleting them.
- The whole `SaveRepository` abstraction exists only for one test. The KDoc mentions a "Room transaction on Android" that doesn't exist.

## Simulator-only code (`core/sim/`)
**The sim package itself:** 10 files, 4,441 lines, shipped in the core main source set.
- No `app/src/main` code imports `core.sim.*`.
- The only importers outside core are the debug `CombatSandboxScreen.kt`, 6 app unit tests and `androidTest/EndDayPerfTest.kt` (`Policy`, `Simulator`).
- Entry points are the Gradle `JavaExec` tasks in `core/build.gradle.kts:37-93` (`SimulatorKt`, `CombatSandboxKt`, `GuildSimKt`).
- This is a candidate to move to its own source set or module.

**Non-sim core files:** none has all of its declarations used only from `sim/`; I checked every file's top-level declarations.

**Non-sim declarations whose outside callers are only in `sim/`, sometimes plus tests (HIGH):**

| Location | Declaration | Outside users |
|---|---|---|
| `content/CombatContent.kt:196` | `data class CoreNumbers` and its 6 fields | `sim/ForgeLab.kt` only; the KDoc says it exists for ForgeLab |
| `content/CombatContent.kt:306` | `val SPREADING` | `sim/ForgeLab.kt` |
| `crafting/Bench.kt:37` | `LEVEL` | `sim/ForgeSweep.kt` (and inside Bench) |
| `crafting/Bench.kt:38` | `DEMO_QUALITY` | `sim/ForgeSweep.kt` (and inside Bench) |
| `crafting/Bench.kt:53` | `data class Setting` | `sim/ForgeLab.kt` |
| `crafting/Bench.kt:83` | `wielderKey` | `ForgeLab`, `ForgeSweep` |
| `crafting/Capabilities.kt:73` | `ruleId` | `ForgeLab` |
| `crafting/Capabilities.kt:109` | `ruleId` | `ForgeLab` |
| `crafting/Capabilities.kt:83` | `Spends` | `ForgeLab` and `DeepForgingTest` |
| `crafting/Capabilities.kt:97` | `interface Verdict` | `ForgeLab` and `DeepForgingTest` |
| `crafting/Capabilities.kt:98` | `Available` | `ForgeLab` and `DeepForgingTest` |
| `crafting/Forge.kt:270` | `fun rarityFor(quality, config)` | `sim/CombatSandbox.kt` and tests |
| `config/BalanceConfig.kt:147` | `maxSimulatedDays` | `sim/Policies.kt`, `sim/Simulator.kt` and a test |

## Lower priority: public but used only in their own file
About 146 non-parameter declarations fall here, so they could be `private`/`internal`. Examples:
- `battle/Power.kt:31,35` (`affixDefenseMultiplier`, `affixMatchup`)
- `crafting/Forge.kt:70,96,264` (`breakthroughChance`, `holdsEpic`, `masteryBonus`)
- `market/Market.kt:146,155,285` (`wantMet`, `wantOf`, `fairPrice`)
- `guild/GuildOps.kt:197` (`refreshCandidates`), `guild/Loadout.kt:29` (`weaponStrike`), `guild/Missions.kt:127` (`recentSupplyRuns`)
- `shopday/Lines.kt:285` (`storyLine`), `engine/Encounters.kt:41` (`counterKey`)

The full raw script output is at `C:\Users\tuncb\.claude\projects\c--Users-tuncb-Desktop-Blacksmith-Project\f920094f-f6d8-438a-84a1-dda6ab93b4d6\tool-results\browictd4.txt`.
