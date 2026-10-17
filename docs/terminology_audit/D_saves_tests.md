# D. Saves, compatibility and test coupling (terminology overhaul, Phase 1, read-only)

Audit date 2026-10-17, working tree as found (uncommitted changes left alone). Nothing in the repo was modified and no
build was run. Evidence is `file:line`.

Path shorthands:
- `core/` = `core/src/main/kotlin/com/tinyblacksmith/core/`
- `app/` = `app/src/main/java/com/example/blacksmithproject/`
- `CT/` = `core/src/test/kotlin/com/tinyblacksmith/core/`, `AU/` = `app/src/test/java/com/example/blacksmithproject/`,
  `AT/` = `app/src/androidTest/java/com/example/blacksmithproject/`

## TL;DR

1. **The save stores a lot of finished prose.** `SaveCodec` serialises the whole `GameState` and `LegacyProfile`
   (`core/persistence/SaveCodec.kt:35,42`), so every `String` field is persisted exactly as written. These are stored as text:
   event records, weapon names, titles and histories, fight logs, highlights and rule names, mission and siege reports,
   nemesis and rival names, wound causes, and the Legend Board, lineages and era causes. The last group lives in the
   cross-era legacy document. Only things that are referenced by ID get a renamed label in an old save: content names
   and descriptions, the notebook, visitor offers, relics, contract names, and shop-day reason templates. A renamed
   **effect, status, affix or move** therefore shows in **mixed vocabulary** in old saves.
2. **Loading compares no text.** `Compatibility.admit` compares only two integers with `>` and checks that stored content
   IDs exist (`core/engine/Compatibility.kt:34-35`, `core/engine/Invariants.kt:133-152`). Rewording display text needs no
   content-version bump. `VersionFingerprintTest` hashes every display field by presence or count only. The real traps:
   - renaming an **enum constant** (it is serialised by name);
   - **stored strings that are also keys** (see table 1b);
   - the name-pool "game terms" check in `ContentCatalog.validate()`.
3. **The debug scenario saves embed display text** (11,519 `"text"` fields). `./gradlew :core:scenarios` regenerates all
   40 saves and `index.json` from the current engine. The `core/src/test/resources/saves` fixtures are frozen on purpose.
4. **About 35 sites in core and app build player text from enum `.name`**, and about 10 more put it in test tags. There
   are also five separate Stat-to-word maps that disagree with each other, and two VisitReason maps.
5. **Test coupling is high but concentrated.** A heuristic finds about 890 display-text assertions in 113 test files.
   Most of them are in about 15 files. Guard tests that a rename can trip: `GoldenStateTest` (projects weapon titles and
   Lost reasons), `LaunchContentTest` (the name-pool term list), `ConstantsTest` (allow-list lines quote "grave threat"),
   `ProseGuardTest` (no dashes, no semicolons, no "blade"), the app's description parser (`InspectUi.kt:33-34`) and the
   emulator scripts.
6. **There is no localisation layer.** `strings.xml` holds only `app_name`, and all game text is Kotlin literals.

---

## Q1. Which display strings are persisted, and which are regenerated

### How persistence works

- `SaveCodec.encodeRun` / `encodeLegacy` write the whole `GameState` / `LegacyProfile` with kotlinx JSON
  (`encodeDefaults = true`, `ignoreUnknownKeys = true`) inside a versioned envelope (`core/persistence/SaveCodec.kt:28-47`).
  There is no per-field filter: **every `String` in the model is stored verbatim**.
- Migrations never rewrite histories (`SaveCodec.kt:49-58`, "Histories and RNG state are never rewritten").
- **Enums are stored by constant name.** No enum constant carries `@SerialName`. The only pinned serial names are the
  `WeaponLocation` subclasses (`core/model/Model.kt:31-46`). `VisitReason` is documented as "constants are only ever
  appended" (`core/model/ShopRecord.kt:16`).
- The run carries a copy of the legacy profile (`GameState.legacy`, `Model.kt:407`), so legacy text is in both documents.
- **A precedent for showing stored text in a new voice already exists.** `core/text/LegacyProse.kt:13-37` rewrites known
  old templates and normalises punctuation at display time, and never writes back. It is used in Gazette
  (`Gazette.kt:183,196,211,230`), Digest (`Digest.kt:127-260`, about 21 call sites), `shopday/Lines.kt:217,297` and
  `app/ui/detail/DetailCommon.kt:43`.

### 1a. Item by item

"Old" means an existing save keeps the wording it was written with. "New" means it follows the current code or content.

| Item | Stored as | Model field | Written at | Rendered from | Existing save after a rename |
|---|---|---|---|---|---|
| Weapon name ("Stormcharged Iron Sword") | **text** | `Weapon.name` `Model.kt:65` | `Forge.weaponName` `core/crafting/Forge.kt:204,249-251` (affix name + material name + family name, or a signature name); renamed on titling `Forge.kt:260`; merchant blade `EncounterCatalog.kt:132`; guild start weapon `GuildOps.kt:153`; a returning legend reuses the stored name `WorldEvents.kt:383` | `w.name` everywhere | **Old name.** The item sheet's property list resolves affix IDs live, so it shows the **new** affix name next to the old weapon name. |
| Weapon at the counter | text | `WeaponSnapshot.name/title` `ShopRecord.kt:34-43` | `WeaponSnapshot.of` | shop-day lines | Old |
| Weapon title | **text** | `Weapon.title` `Model.kt:86` | `Battle.kt:54` "Slayer of $encounter", `Battle.kt:57` "Bane of the ${faction.name}" | stored | Old. It also feeds `GoldenStateTest` (see Q5). |
| Weapon history / story | **text** plus a key | `HistoryEntry(kind, text)` `Model.kt:61` | e.g. FORGED `Forge.kt:212` (risk enum lowercased, technique + "ed"), SIGNATURE `Forge.kt:214` (signature name and flavor), TITLED `Forge.kt:261` | `LegacyProse.display` (`Lines.kt:297`, `DetailCommon.kt:43`) | Old (punctuation normalised only) |
| Hero names | text | `Hero.name/surname/descendantOf/mentorName` `Model.kt:128-149`; `CustomerSnapshot.name` `ShopRecord.kt:46`; `MarketVisit.heroName` `:81`; `FieldResult.heroName` `:102` | `heroes/Names.kt` from the content pools | stored | Old (names are not game terms) |
| Hero "title" / class | ID | `Hero.classId` | none | `content.heroClass(id).name` (e.g. `app/ui/GuildInspect.kt:74`) | New. Heroes have no stored title. |
| Item descriptions and flavor (material, affix, effect, relic, trait, tool, blessing, law, rank) | ID only | none | none | content, e.g. `app/ui/replay/SiegeReplay.kt:400` | **New**, except where a description was **copied into stored text**: SIGNATURE history `Forge.kt:214`; SIEGE_TRAIT event `Battle.kt:265`; SIEGE_PREPARATION event `SiegeFight.kt:55`; siege notes `SiegeFight.kt:297`; GUILD_STORY rank and law `Stories.kt:135,219`. Those stay old. |
| Affix names | ID | `Weapon.affixes/flaws/dormantAffixes` `Model.kt:77-78,96`; `LegendEntry.affixes/flaws` `Model.kt:337-338` | none | `content.affix(id).name` | New in sheets. **Old** wherever the name was baked in: weapon names, the WEAPON_FORGED text "Flaws include ..." (`Forge.kt:221`), and the fight-record `rules` map. |
| **FightResult** (guild contracts and sieges) | **text-heavy** | combat `Model.kt:183-197`: `title`, `ActorResult.name/kit/weapon` (`:165-176`), `FightEvent.text` (`:55`), `Highlight.text` (`:179`), `rules: Map<id,name>` (`:194`). Held in `MissionReport.fight` and `SiegeReport.fight` (`core/model/Guild.kt:161,188`) after `Missions.compact`, which keeps every event with text (`Missions.kt:503-508`). | `combat/Fight.kt`: `:151` "Name: Move.", `:159` "loses 1 Chill", `:372-374` stat words (REGEN reads "Regeneration"), `:399-401` "gains N Guard", `:444-445` "Guard blocks / breaks", `:107` the rules map from `EffectDef.name`; `combat/Highlights.kt:49` joins event texts | Log `app/ui/FightReport.kt:62-66`, highlights `:106` (verbatim). Replay: step lines are stored text (`Replay.kt:264`), the move name is **parsed** from the stored text (`Replay.kt:245`), rule names come from the stored map (`Replay.kt:134`, `Account.kt:156`). Pop labels are **regenerated** (`Replay.word` `Replay.kt:107-110`, "Blocked", "Guard broken", `outcomeWord` `:279-285`). `SiegeReplay.kt:408` prints the **stored** rule name with the **live** description. | **Mixed.** Log lines, highlights and rule names are old. Pop labels, outcome words and rule descriptions are new. |
| Classic replays | text | `CombatReplay(title, rounds, outcome)` and `CombatRound.note` `Model.kt:268-280` | `Battle.kt:133-137` "met $encounter with …"; `:306` "**strikes** with ${w.name}"; `:308-312` "holds the gate", "Town held", "Defenses broken"; `SiegeFight.kt:253-255` | stored | Old |
| Contract and siege reports | text | `MissionReport.title/lines` `Guild.kt:153,163`; `SiegeReport.outerLine/notes` `Guild.kt:188-190` | `Missions.kt:325-342` (title from `def.name.lowercase()` + route; Danger enum lowercased at `:329`); `SiegeFight.kt:294-300` | stored | Old |
| Contracts ("quests") on the board | ID plus text | `MissionOffer.defId/modifierId` (IDs), `reason` (text), `lapse` (key) `Guild.kt:95-114` | `Missions.kt:110,113` (reason) | name and description live (`app/ui/GuildUi.kt:288`); `why = offer.reason` stored | Name and description new; reason old |
| Event records (Gazette, Digest, replays) | **text** plus data map | `EventRecord.text`, `data: Map<String,String>` `Model.kt:255-264` | every `ctx.emit(type, priority, text, data)` | Edition: stored text through `LegacyProse` (`Gazette.kt:110-145,181-231`); tallies and section titles live (`:148-176,215-228`). Digest: headings are live templates (e.g. "Emberfall held", "$name died", `Digest.kt:149,162`); bodies are stored text **or stored data values**: `data["attacker"]` (`Battle.kt:299,323,355`), `data["faction"]` (faction name, `Battle.kt:430`), `data["weak"]` (**`Element.name.lowercase()`**, `Battle.kt:431`, printed raw at `Digest.kt:171`), `data["foe"]` (`Battle.kt:62`), `data["name"]` (tool name, `GameEngine.kt:381`). Archived days read `state.events` (`Gazette.kt:31-32`). | Record text and data names old; headings new |
| Daily headlines | text | `DayResolution.headlines` `Model.kt:287` | `GameEngine.kt:459` | **no reader in core or app** | Stored but never shown (dead stored text) |
| Shop-day lines | typed | `VisitReason`, `VisitFactor`, `RecognitionCue` enums plus numbers (`ShopRecord.kt:17-31,68-72`) | none | `shopday/Lines.kt` templates (e.g. `:19-29`) | **New** templates, but hero and blade names inside them come from stored snapshots (old). Aftermath cards are stored event text (`Lines.kt:216-217`). |
| Notebook, field notes, knowledge | keys, enums, rule IDs | `Journal` `Model.kt:309-318` | none | live: `app/ui/RecordsUi.kt:106-115,123+` (`Journal.hint/subjectName/clue`, `Interactions.page`) | **New**. DISCOVERY event text is stored (`Journal.kt:39,41,107`), so it is old in the Gazette and Digest. |
| Visitors (encounters) | ID plus amounts | `EncounterInstance.defId/amounts/...` `core/model/Depth.kt:124-149`; `EncounterRecord.optionId` `:153`; the merchant's `blade: Weapon` has a stored name | none | offer card from `EncounterCatalog.view/text` | Offer new. Outcomes are stored event text (`Encounters.kt:81,99,127`) and stay old. |
| Relics | ID | `ActiveRelic.id` `Depth.kt:157` | none | `content.relic(id)` | New. RELIC_* events and the Compatibility clamp note (`Compatibility.kt:82`) store the name as text. |
| Siege trait / preparation / law / rank | ID | `SiegeScenario.traitId/prepId` `Depth.kt:176-186`; `WorldLaw.id` `Guild.kt:180`; `rank` | none | content | New. The events that announce them copy name and description (above). |
| Nemesis | **text** | `Nemesis.name` `Guild.kt:172` | `Stories.kt:155-157` (picked from `faction.eliteNames`, articles stripped) | stored | Old |
| Rival guild | **text plus enum name** | `RivalGuild.name/leaderName/wants/taken` `Guild.kt:176` | `Stories.kt:196-198,209`; `wants = MissionArchetype.name` | `GuildUi.kt:477` "They prefer ${wants.lowercase()} quests." | Old |
| Member wounds, bonds, recruits, combo notes | text | `Wound.cause` `Guild.kt:20`; `Bond.kind/reason` `:24`; `RecruitCandidate.complication` `:49`; `comboNotes "key\|text"` `:218` | `GuildVisitors.kt:99,171`, `Missions.kt:386,449`, `SiegeFight.kt:288`; `Stories.kt:39-52` | stored (`GuildUi.kt:483,753`) | Old |
| **Legacy records (permanent, across eras)** | **text** | `LegendEntry.weaponName/title/owners/ownerLine` `Model.kt:327-352`; `LineageAnchor.heroName/surname/deed` `:356`; `EraSummary.cause` `:359`; `GameState.endCause` `:415` | `legacy/Legacy.kt:89-95` (default title "${w.name} of Era N"), `:99-103` (deed text), `:111` | Legend Board `app/ui/InfoPanels.kt:462-489` through `Lines.legend`: line 1 is stored name, title and fame; "Properties: …" is **live** affix names; the story is stored. `RunEndScreen.kt:54,70,72`. A lineage deed is copied into new events (`WorldEvents.kt:409`). | **Old forever.** The text lives in `LegacyProfile` and survives every later era. |
| Misc. | text | `WorldModifiers.name` "Calm Season" `Model.kt:379` (`GameEngine.kt:80`, shown `TownUi.kt:46`); NPC `Guild.name` "the X Company" (`Heroes.kt:252`); `FieldResult.foe` `ShopRecord.kt:103`; `WeaponLocation.Lost.reason` `Model.kt:37` | | stored | Old |

### 1b. Stored strings that are also identifiers or are parsed: do not reword in place

| Stored string | Where it is compared or parsed | Shown at | Risk if reworded |
|---|---|---|---|
| `WeaponLocation.Lost.reason` | `isWithMerchant` `Model.kt:117` (WITH_MERCHANT `:40`); `WeaponPruning.terminalReasons` `persistence/WeaponPruning.kt:25,31`; `WorldEvents.kt:164` (`== "seized"`, `startsWith("lost with")`, decides which blades may resurface); `GuildOps.kt:20-21` LOST_WITH_CAPTIVE / LOST_TAKEN used at `Missions.kt:450,489-490` | `app/ui/detail/ItemDetailSheet.kt:283` (shown raw, capitalised) | Gameplay changes in old **and** new saves. It is in the `GoldenStateTest` projection (`GoldenStateTest.kt:42`). Map it to a label at display time. |
| `Bond.kind` "comrades" / "protector" | `Stories.kt:25-26` (constants) | `GuildUi.kt:753` (capitalised) | Old saves keep the old key. Use a label map. |
| `RivalGuild.wants` = `MissionArchetype.name` | `Stories.kt:206` | `GuildUi.kt:477` | Use a label map keyed by the archetype |
| `HistoryEntry.kind` ("FORGED", "SIGNATURE", "TITLED", "LOST", "SEIZED", …) | `Legacy.kt:59`, `WorldEvents.kt:160`, `ItemDetailSheet.kt:298` | not shown | Keys: leave them alone |
| `comboNotes` "key\|text" | `Stories.kt:39` dedupes on the key | `GuildUi.kt:483` | Keep the key format |
| `EventRecord.data` keys and enum values (fate, "milestone", "event", "rung", "key") | Gazette and Digest throughout | | Keys: leave them alone |
| Fight action text "Name: Move." | `Replay.kt:245` strips the "$name: " prefix to get the move | replay titles | Keep the template shape in `Fight.kt:151` |
| Event text prefixes | `Digest.kt:160` (HERO_DIED starts with "$name "); `Gazette.kt:226` (TOOL_BOUGHT text after ": "); `SiegeFight.kt:253` (strips "${name}'s "); `LegacyProse.siegeAttacker` regexes (`LegacyProse.kt:42-50`) for old siege sentences without `data["attacker"]` (`Digest.kt:127`) | | If an emitter template changes shape, these fallbacks stop matching |
| **Every enum constant** (`Rarity`, `Element`, `Stat`, `Posture`, `WoundKind`, `Danger`, `VisitReason`, `MissionArchetype`, …) | kotlinx decodes by name; `Stat` is also inside `FightEvent.stat/tick` and `ActorResult.startStats` | via the `.name` sites in Q4 | Renaming a constant breaks decoding of every save that holds it. Rename labels only. |

---

## Q2. Do content version checks constrain changes to display strings?

**What load compares** (`core/engine/Compatibility.kt:32-41`):
1. `state.rulesVersion > GameEngine.RULES_VERSION` and `state.contentVersion > content.version`: two integers compared with
   `>` only (`:34-35`). Content version is `LaunchContent.kt:114` (`version = 6`), defined at `Content.kt:161`.
2. `clampToCaps` (balance caps), then `Invariants.check(..., content)`. Its content part is `unknownContent`
   (`Invariants.kt:19,133-152`), which checks that every **stored ID** resolves: materials, families, affixes, classes,
   traits, commission families, blessings, tools, upgrades, factions, relics, the visitor's `defId` and the siege trait.
3. On success it stamps the installed versions (`Compatibility.kt:40`).

**No names, descriptions, hashes or counts are compared at load.** So:
- Rewording any content name or description: loads fine, no version bump needed for Compatibility.
- Renaming or removing a content **ID**: the run is refused ("Unknown affix …", `Invariants.kt:132`).
- Renaming an **enum constant**: decoding fails before Compatibility runs. The app routes that to the recovery screen
  (`app/ui/Failures.kt`).

**Test-time guard, `CT/VersionFingerprintTest.kt`** (not a load check):
- It hashes the launch catalog by reflection. Fields in `prose` (`:67`: name, description, flavor, siegeName,
  warlordName, encounterNames, eliteNames, counsel, role, forgeHint, pattern, telegraph, prep, failure, pitch,
  advantage, constraint, play, rivalNames, where, costNote, counters, sabotageBrief, plan, build, drawback) are hashed
  as `"text"` or `"texts<count>"` (`:71,:78`). The test asserts that rewording is not seen (`:96`).
- I checked every display `String` field of the definitions in `content/Content.kt`, `content/Depth.kt`,
  `content/CombatContent.kt`, `content/GuildContent.kt`, `combat/Effects.kt` (`EffectDef`, `Move`, `KitDef`) and
  `combat/Model.kt` `UnitDef`. All of them are in `prose`. `SpecialityDef.deed` is not, but it is a key ("veteran",
  "survivor", `GuildContent.kt:464-471`, read at `Stories.kt:88`).
- So rewording catalog text does **not** move the fingerprint. It **does** move when:
  - an entry is added to or removed from a prose list (the count changes);
  - a name-pool entry changes (`firstNames/surnames` are hashed in full on purpose, `:21-22`);
  - an enum constant is renamed (hashed by name, `:74`);
  - an ID changes;
  - a **new** display field is added without adding its name to `prose`.
- `SignatureCatalog` (`core/crafting/Signatures.kt`) is not part of `ContentCatalog`, so it is not fingerprinted.
  Signature names still end up inside stored weapon names.

**`ContentCatalog.validate()` text coupling.** `nameProblems` (`Content.kt:309-316`) builds a "game terms" set from the
**display names** (and IDs) of families, materials, affixes, classes and factions, plus the `Element` constant names. It
rejects any hero first name or surname that contains a term or its 5-letter stem. A new display name such as "Attack" or
"Bonus" could newly collide with a pool name and fail `validate()`. That fails `LaunchContentTest.lockedCountsAndValidity`
and `namePoolsObeyTheAuthoringRules`. `LaunchContentTest.kt:93-95` also asserts specific terms ('ash', 'grave', 'bane',
'hollowbound', 'ember', 'sun', 'ranger'), and they break if those display names change.

Balance version and rules version have no bearing on text.

---

## Q3. Do the debug scenario saves embed display text?

**Yes, heavily.** `app/src/debug/assets/scenarios/` holds 40 saves plus `index.json`. Each save is exactly
`SaveCodec.encodeRun(state)` (`CT/ScenarioSavesWriter.kt:22`).

| Measured in the bundled files | Hits / files |
|---|---|
| `"text":` fields (events and histories) | 11,519 / 40 |
| "Stormcharged" (an affix name baked into weapon names) | 326 / 16 |
| "Guard" | 107 / 18 |
| "Calm Season" | 92 / 33 |
| "strike" | 10 / 8 |
| "Chill" | 14 / 2 |
| "Iron holds" (effect name) | 8 / 1 |
| `FightResult` with `highlights` and a `rules` map | 5 files: guild_captive, guild_charter_eve, guild_checkpoint, guild_siege_eve, siege_preparation |

`index.json` text (`title`, `description`, `built`) comes from the `ScenarioSaves.kt` test source, plus " The blade is
${it.name}." (`ScenarioSavesWriter.kt:27`). Several descriptions quote card titles, e.g. "At the guild hall", "A death",
"Sold on by a merchant".

**Regeneration.** `./gradlew :core:scenarios` (`core/build.gradle.kts:62-72`) runs `ScenarioSavesWriter`. That deletes
every `*.json` in the folder and rewrites each case from `ScenarioSaves.all` (`ScenarioSavesWriter.kt:20-33`). The cases
are **played by the real engine** from pinned seeds and days (`ScenarioSaves.kt:18-27`). So every stored text in them
comes from the emitters and content as they are at that moment. A text-only rename draws no RNG, so the pinned seeds
should still reach their cases. A change that touches a `Lost` reason, a title or anything else gameplay reads must be
checked with `-Dscenarios.search=true`. Until the task is rerun, the debug menu loads saves in the old vocabulary, which
still works.

**Never regenerated, by design:** `core/src/test/resources/saves/*.json` (v1 to v6 fixtures) and `.../golden/*.txt`. They
are regression anchors that keep old text forever. They are the natural input for an "old save still reads sensibly"
test in the rename phase.

**Consumers:**
- `AU/ScenarioAssetsTest.kt:53-80` loads every bundled file and checks the card *kind*, not text.
- `CT/ScenarioSavesTest.kt:99` asserts `"died defending the walls" in it.text` on a freshly played day.
- `tools/emulator/scenarios.sh` greps screen text: `:114` "died defending the walls", `:119-123` "a shop regular" and
  ", a regular", `:170` "The visitor leaves tonight".

---

## Q4. Player-facing text derived from enum `.name` or identifiers

Every one of these must become an explicit label map before any term changes, because the constants themselves can never
be renamed (Q1b).

| Site | Enum | Output | Stored or live |
|---|---|---|---|
| `core/battle/Battle.kt:429` | Element (`weakTo`) | "They are weak to Fire weapons." in SIEGE_WARNING | **stored** |
| `core/battle/Battle.kt:431` | Element | `data["weak"] = "fire"`, printed raw at `Digest.kt:171` | **stored** |
| `core/crafting/Forge.kt:205` | Technique | "tempered/quenched/etched" (name + "ed") in history | **stored** |
| `core/crafting/Forge.kt:212` | Risk | "(balanced risk …)" in FORGED history | **stored** |
| `core/crafting/Forge.kt:221` | Rarity | "Forged X. Rare. …" WEAPON_FORGED text | **stored** |
| `core/guild/Missions.kt:329` | Danger | "the next stage is dangerous" in MissionReport lines | **stored** |
| `core/guild/Missions.kt:387` | WoundKind | "X is injured: …" HERO_WOUNDED text | **stored** |
| `core/guild/SiegeFight.kt:201` | WoundKind | "X was exhausted." siege notes | **stored** |
| `core/guild/Stories.kt:197` | MissionArchetype | `RivalGuild.wants` (shown at `GuildUi.kt:477`) | **stored** |
| `core/guild/GuildOps.kt:46` | WoundKind | "is injured until day N" | live |
| `core/guild/SiegeFight.kt:172` | WoundKind | "injured until day N" (wall availability) | live |
| `core/gazette/Gazette.kt:219` | Rarity | "forged 3 weapons (1 epic)" | live |
| `core/crafting/WeaponRules.kt:64` | Element | element rule label "Fire" | live |
| `core/engine/EncounterCatalog.kt:169` | Element | visitor text | live |
| `core/market/Commissions.kt:66` | Element | "Fine fire Sword (quality 50+)" | live |
| `core/shopday/Lines.kt:257,329` | Element | "weak to fire" | live |
| `core/engine/Invariants.kt:37` | HeroFate | load-problem text (recovery screen) | live |
| `core/content/Content.kt:313` | Element | the "game terms" set in validation | n/a |
| `app/ui/GuildInspect.kt:39` (`rarityWord`), `app/ui/InspectUi.kt:223`, `app/ui/Sprites.kt:258` (contentDescription) | Rarity | "Rare" | live |
| `app/ui/detail/ItemDetailSheet.kt:229`, `app/ui/detail/WeaponStatCard.kt:139`, `app/ui/detail/HeroDetailSheet.kt:166` | Element | "Fire sword", "Favors fire weapons" | live |
| `app/ui/detail/ItemDetailSheet.kt:296` | ForgeMode, Risk | "Advanced forge, balanced risk" | live |
| `app/ui/GuildUi.kt:197` `postureName` | Posture | "Cautious / Balanced / Reckless" (the retreat rule) | live |
| `app/ui/GuildUi.kt:223` `statWord` | Stat | "Regen" (enum-derived) | live |
| `app/ui/GuildUi.kt:362`, `app/ui/GuildPlanUi.kt:77` | WoundKind | "Injured · day N" | live |
| `app/ui/GuildUi.kt:477` | stored archetype name | "They prefer hunt quests." | live over stored |
| `app/ui/GuildUi.kt:753`, `app/ui/detail/ItemDetailSheet.kt:283` | stored keys (`Bond.kind`, `Lost.reason`) | capitalised raw | live over stored |
| Test tags (not shown, but tests find nodes by them): `ContractSheet.kt:126`, `ForgePanel.kt:480,485,526,738`, `GuildPanel.kt:199`, `Notebook.kt:106`, `SettingsSheet.kt:111`, `WorkshopScreen.kt:327,342,358` | Posture, slot, mode, tab, destination | `*_${name.lowercase()}` | keep as they are |

**Explicit label maps that already exist** (the pattern to extend; some of them disagree):
- `app/ui/Labels.kt:46-77` (`outlook`, `rarity`, `risk`, `technique`).
- `WeaponRules.Source.word` (`core/crafting/WeaponRules.kt:31`, e.g. `AFFIX("buff")`) and `Res.word`
  (`core/crafting/Capabilities.kt:29-31`).
- **Five Stat-to-word maps**:
  - `Fight.word` (`combat/Fight.kt:372-374`, REGEN reads "Regeneration", written into stored text);
  - `FightReplay.word` (`combat/Replay.kt:107-110`, REGEN reads "Regen", live pops);
  - `Res.word` ("Regeneration", MARK reads "a Mark");
  - `InspectUi.statName` (`app/ui/InspectUi.kt:73-76`, "Regeneration");
  - `GuildUi.statWord` (built from the enum name, "Regen").
- **Two VisitReason maps**: `Lines.reason` (`shopday/Lines.kt:19-29`) and `Gazette.visitReason` (`gazette/Gazette.kt:71-80`).
  For example, OVERPRICED reads "the price was too high" in one and "the prices were too high" in the other.

One more coupling: `app/ui/InspectUi.kt:33-45` **parses content descriptions**. `LIMIT` requires a final sentence like
"Once per round." or "Twice per fight.", and `TRIGGER` requires an opening "When/Whenever/While/At the start of/…, ".
Rewritten effect descriptions must keep that shape or the parser has to change. `AU/GuildInspectTest.kt:103-104` pins
"Enemy breaks Guard" and "Once per round".

---

## Q5. Test coupling

### Method (a heuristic)

For each test file I counted:
- (a) Compose finders by text: `onNodeWithText`, `onAllNodesWithText`, `hasText`, content-description finders,
  `assertText*`;
- (b) `assertEquals`/`assertNotEquals` whose **expected** argument is a literal that reads as prose (has a lowercase
  letter and starts with a capital or contains a space). In JUnit files, a 3-argument call whose first literal is the
  message is excluded;
- (c) lines with `"…" in`, `.contains/startsWith/endsWith("…")`, `assertContains`, `Regex("…")`.

The counts are approximate and include some non-display checks.

| Suite | Files | Files with hits | Approx. assertions |
|---|---|---|---|
| core JVM (`core/src/test`) | 92 | 54 | 314 |
| app JVM (`app/src/test`) | 57 | 41 | 409 |
| instrumented (`app/src/androidTest`) | 22 | 18 | 170 (87 text finders vs 386 `onNodeWithTag`) |
| **Total** | 171 | 113 | **about 890** |

### The 15 most coupled files

| # | File | Approx. | Mostly |
|---|---|---|---|
| 1 | `AU/GuildUiTest.kt` | 60 | contract cards, reward summaries, "Strike" or "Health" numbers, healing phrasing |
| 2 | `AT/ShopDayScreenTest.kt` | 52 | 49 `onNodeWithText` on shop-day cards |
| 3 | `CT/DeepForgingTest.kt` | 47 | Capabilities sentences ("Chill on an enemy."), rule names, no "Wet" (`:591`) |
| 4 | `CT/ShopDayScriptTest.kt` | 43 | shop-day lines, forbidden words (`:398,:442`) |
| 5 | `AU/RunVarietyUiTest.kt` | 36 | opening and contract text, Heartfire |
| 6 | `AU/ForgeWorkbenchModelTest.kt` | 30 | forge bench labels |
| 7 | `AU/GuildInspectTest.kt` | 24 | rule trigger and limit words, keywords "Guard", "Burn" |
| 8 | `AU/ForgeResultTest.kt` | 20 | forge result card |
| 9 | `CT/GazetteDigestTest.kt` | 20 | digest headings and bodies |
| 10 | `AU/GuildPlanUiTest.kt` | 18 | party planning reasons |
| 11 | `AT/M4ScreensTest.kt` | 17 | screen text finders ("properties", catalyst) |
| 12 | `AU/DetailBuilderTest.kt` | 17 | item and hero detail facts |
| 13 | `AT/ShopPanelTest.kt` | 16 | shop panel text |
| 14 | `CT/ClueLadderTest.kt` | 16 | clue sentences |
| 15 | `AU/ShopDayUiTest.kt` | 16 | shop-day UI, forbidden words (`:271`) |

Close behind: `AU/GameplayDepthTest` (16), `AT/DetailSheetTest` (15), `CT/ReplaysAndWeaponFatesTest` (15),
`AU/LoadFailureTextTest` (14), `AU/ForgeLearningTest` (13).

**Literals naming candidate terms** (case-insensitive, in any test string, assert messages included):

| Term | Hits | Files |
|---|---|---|
| guard | 44 | 13 (e.g. GuildInspectTest, ReplaysAndWeaponFatesTest, FightStandoutTest, ShopDayScriptTest) |
| strike | 44 | 10 (CombatEngineTest, FightReplayTest, GuildUiTest `:398`, GuildInspectTest `:152`, SiegeContributionUiTest) |
| charge | 25 | 9 |
| mark | 19 | 11 |
| burn | 11 | 3 |
| chill | 5 | 4 |
| propert(y/ies) | 12 | 8 (incl. AT/M4ScreensTest, RecordsModelTest) |
| buff | 9 | 4 (WeaponStatCardTest, BladeRulesUiTest, V2ShellTest) |
| "Iron holds" | 3 | DeepRoadTest `:129`, BladeRulesUiTest `:88`, DeepForgingUiTest `:95` |
| effect | 28 | 12 |
| rule | 53 | 22 |
| contract | 20 | 12 |
| quest | 10 | 7 |
| retreat | 8 | 4 |
| upgrade | 27 | 18 |
| repair | 5 | 5 |

### Guard tests (what they enforce, and which renames they trip)

- **`CT/ProseGuardTest.kt`**
  - Scans every string literal in `core` main and `app` main, except `/sim/`, comments and `${}` expressions (`:14-48`).
  - Rules: no em dash or en dash (`:54-55`); no `"; "` between clauses (`:56-57`); no `\bblades?\b` (`:19,:61-66`).
  - Allow-list: `LegacyProse.kt` "; ", one Signatures flavor line, and the EncounterCatalog amount key "blades" (`:22-26`).
  - A rename must not introduce "blade", a dash or a semicolon. Any new label map lives in scanned code.
- **`CT/LaunchContentTest.kt`**
  - `lockedCountsAndValidity` (`:19-34`): `validate()` must be empty, plus LOCKED counts.
  - `namePoolsObeyTheAuthoringRules` (`:68-96`): the game-term collisions 'ash', 'grave', 'bane', 'hollowbound',
    'ember', 'sun', 'ranger' (`:93-95`).
  - `noPlayerFacingTextContainsAPercentChance` (`:125-137`): any catalog string with `%` or "percent" next to
    `chance|odds|likel|probab` fails.
- **`CT/ConstantsTest.kt`**
  - Scans the six resolvers for numeric literals. The allow-list matches **lines by snippet** (`:24-48`), and four
    snippets are display words in `Battle.kt`: "overwhelming threat", "grave threat", "rising threat", "restless"
    (`:42-45`).
  - Rewording those pressure words in `Battle.kt` fails the test ("listed literals that are no longer in the source").
  - It also strips `pressureEvent(...)` rows by regex (`:51`).
- **`CT/VersionFingerprintTest.kt`**: see Q2. Rewording prose is safe. IDs, enum constants, name pools and new non-prose
  fields are not.
- **`CT/GoldenStateTest.kt`**
  - Its projection includes `title=${w.title}` (`:67`) and `lost:${day}:${reason}` (`:42`).
  - Renaming the title templates (`Battle.kt:54,57`, which also use faction and encounter names) or any `Lost` reason
    forces a golden re-record even though RNG is untouched. The test's RNG-only half still passes.
  - Event text and weapon names are **not** projected.
- **`CT/LocaleDeterminismTest.kt`**: the encoded state must be byte-equal under en-US, de-DE and tr-TR. Labels must use
  Kotlin `lowercase()`/`uppercase()` (locale-invariant), never `toLowerCase(Locale.getDefault())`. The Turkish dotless i
  would break "FIRE" otherwise.
- **`CT/LegacyProseTest.kt`**: pins the old-template rewrites (9 assertions). Extend it if the adapter grows a term map.
- **Negative-vocabulary checks**:
  - `CT/ShopDayScriptTest.kt:398,442` (`would|because|thanks to|decid|%`);
  - `AU/ShopDayUiTest.kt:271` (`would|lower|cheaper|if you|%|chance`);
  - `AU/SiegeContributionUiTest.kt:81` (won / saved / decided / victory);
  - `CT/DeepForgingTest.kt:591` (no "Wet");
  - `AU/GuildUiTest.kt:391` (no "healing");
  - `AU/ForgeV2FactsTest.kt:54` (no "energy").
- **Required-word checks**:
  - `AU/GuildInspectTest.kt:103-104` ("Enemy breaks Guard", "Once per round") and the `InspectUi` `LIMIT`/`TRIGGER`
    parser (Q4);
  - `CT/ArtifactFidelityTest.kt:75,313`: a weapon name may contain at most one affix name, and only one it has;
  - `CT/ScenarioSavesTest.kt:99` ("died defending the walls").
- **Which tests fail if "Guard", "Strike" or "Chill" is renamed?**
  - Every test that asserts fight-log text, highlight text, keywords or rule-card text (the guard, strike and chill rows
    above).
  - `GuildInspectTest` (`:103,:106,:186`) and `GuildUiTest:398` ("Strike" as a stat label).
  - `DeepForgingTest:586-587` (Capabilities sentences).
  - `ReplaysAndWeaponFatesTest`, `FightStandoutTest`, `FightReplayTest` (pop and standout text).
  - Expect about 30 files across the three suites. Status names also sit inside `Res.word`, `Fight.word` and
    `Replay.word`, which tests reach indirectly.
- **Outside the test suites:** `tools/emulator/*.sh` grep on-screen text: intro 21, runend 19, smoke 15, journey 12,
  forge 9, scenarios 8, guild 5, runvariety 4.

---

## Q6. Localisation infrastructure

There is none. `app/src/main/res/values/strings.xml` holds only `app_name`, and no other `strings*.xml` exists (only
`values/` and `values-v31/`). There is no `stringResource`, `R.string` or `getString(` in app code. All player text is
Kotlin string literals and templates in `core` (emitters, `shopday/Lines.kt`, `gazette/*`, content catalogs) and in
`app` (the `ui/*` builders, `Labels.kt`). Core is pure JVM by architecture, so moving text to Android resources would
not reach core-built sentences anyway.

---

## Implications for a rename

**1. Renames that affect live text only. They are safe in old saves and follow automatically:**
- content `name`/`description`/`flavor` of families, materials, affixes, effects, kits and moves, units, relics, traits,
  tools, blessings, upgrades, laws, ranks, charters, openings, preparations, modifiers and routes, as shown on cards and
  sheets;
- shop-day templates (`Lines.kt`), digest headings, gazette tallies and section titles;
- the notebook and fight notes, visitor offer cards, the contract board's name and description;
- replay pop labels (`Replay.word`, "Blocked", "Guard broken"), `outcomeWord`;
- every Q4 site marked "live";
- `Labels.kt`.

None of this needs a content or rules version, a migration or a fingerprint re-pin, provided only prose fields change.

**2. Renames that change new stored text. Old saves keep the old words** and the screen mixes old and new vocabulary:

| What changes | How an old save then looks |
|---|---|
| A status word ("Guard", "Chill", …) | Old fight logs and highlights still say "gains 5 Guard" / "loses 1 Chill" (stored `FightEvent.text`, `Highlight.text`). Replay pops for the same fight use the new word. |
| An effect name ("Iron holds") | Old fights show the old name from `FightResult.rules`. `SiegeReplay.kt:408` prints it beside the **new** description. Rule-card tooltips are new. |
| An affix name | Old weapons keep their full names ("Stormcharged Iron Sword"); the property list shows the new affix name. Legend Board heads, era summaries, lineage deeds, weapon titles and histories stay old **across eras**, because they live in `LegacyProfile`. |
| An element word | Old siege warnings keep "weak to fire" (`data["weak"]`, the event text). |
| "strike" in classic siege replays (`Battle.kt:306`) | Stays in old replays. |
| Everything in the Gazette | Old days show old prose, through `LegacyProse.display`. |

Options:
- (a) Accept the mixed vocabulary as history. This matches the project rule that records are never rewritten
  (`SaveCodec.kt:49-58`).
- (b) Extend `LegacyProse.display` with a read-only old-to-new term map. That is the existing precedent, it is pure,
  and it never writes back. It works only for whole tokens that cannot collide with names.
- (c) Do **not** migrate stored text in `SaveCodec`. A text-rewriting migration would break the rule that histories are
  never rewritten. It could also break the readers that parse stored sentences (`Replay.kt:245`, `Digest.kt:160`,
  `LegacyProse.siegeAttacker`).

**3. What needs a display-label map separate from identifiers.** Never rename these. Add maps instead:
- every enum shown to the player: Rarity, Element, Stat, Posture, WoundKind, Danger, Risk, ForgeMode, Technique,
  MissionArchetype, HeroFate, VisitReason;
- the stored keys `Lost.reason`, `Bond.kind`, `RivalGuild.wants`.

Concrete targets:
- unify the five Stat maps (`Fight.word`, `Replay.word`, `Res.word`, `InspectUi.statName`, `GuildUi.statWord`) and the
  two VisitReason maps;
- replace each `.name.lowercase()` site in Q4 with the map;
- route `ItemDetailSheet.kt:283`, `GuildUi.kt:477` and `GuildUi.kt:753` through maps keyed by the stored value.

Keep `testTag` strings, `HistoryEntry.kind`, `EventRecord.data` keys and the "Name: Move." template shape unchanged.

**4. Blast radius:**
- **Tests.** About 890 heuristic display assertions in 113 of 171 test files. A focused combat-term pass ("Strike",
  "Guard", status names, "Iron holds", "Buffs" or "Properties") realistically touches about 25-35 files, most of them
  in the top 15. A pass over Gazette and shop-day prose touches `ShopDayScriptTest`, `GazetteDigestTest`,
  `ShopDayScreenTest` and `ShopDayUiTest`.
- **Guards to re-check after every batch:**
  - `ProseGuardTest`;
  - `LaunchContentTest` (`validate()`, the name-pool terms);
  - `ConstantsTest` (if `Battle.kt` pressure words change);
  - `GoldenStateTest` (if titles or `Lost` reasons change: re-record with a note);
  - `VersionFingerprintTest` (only if IDs, counts or enums change);
  - `LocaleDeterminismTest`.
- **Regenerate** with `./gradlew :core:scenarios`.
- **Re-check** the emulator scripts (about 93 text greps).
- **Unaffected:** RNG, balance and save compatibility, provided the rename stays inside prose fields and label maps.
