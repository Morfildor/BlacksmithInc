# 04 — Assets and content review (Tiny Blacksmith major update, N04 / N07 / A10)

Reviewer: asset/content agent, 2026-10-09. Planning only: nothing inside the project was created, edited or run
(no importer, no Gradle, no git). All measurements were taken by reading the sources in place with Pillow/numpy;
every crop named below is in `...\scratchpad\major_update\crops\` (absolute root:
`C:\Users\tuncb\AppData\Local\Temp\claude\c--Users-tuncb-Desktop-Blacksmith-Project\cb469540-0a58-43a6-af0d-70fa21126365\scratchpad\major_update\`).
Line numbers are from the working tree at review time; `Model.kt` and `Dialogs.kt` were being edited by other agents
while I read them, so those two may drift by a line or two.

## Headline findings

1. **All 25 live portraits are cut off-grid, and 15 of them show a strip of a neighbouring portrait, visible on a device today.** `layout_3` cuts sheet -3
   with a uniform 5x5 grid (`tools/pixelart/import_assets.py:107-112`), but the portrait rows on the sheet are not on
   that grid. Rangers have a strip of the guardian's shield across the top, duelists a ranger strip on top and a
   battlemage hat tip at the bottom, battlemages have warden antler tips under the bust, wardens have their antlers cut
   off. Confirmed on the real screenshot `scratchpad/ui_v4/07_town.png`. A corrected slice (measured row bands and
   column cuts) is clean: see `crops\portraits_live_25_at_56dp_x3.png` vs `crops\portraits_proposed_reslice_at_56dp_x3.png`,
   and `crops\device_evidence_portrait_strip_and_forge_52dp.png`.
2. **There are 30 more class portraits and 8 townsfolk portraits that nobody has counted**, in
   `Pixel art assets\New folder\concept_references\v2_concept_reference.png` (6 per class; 5 per class are clean, the
   first of each row has the class label baked over it). That doubles the honest face pool to 10 per class.
   `crops\refboard_v2_portraits_38_tiles.png`.
3. **The best counter backdrop is not in the new atlas.** The same `concept_references` folder holds two painted forge
   interiors at more than twice the atlas resolution (a 1400 px panorama and a 590 px scene with a long workbench).
   A 540x270 crop of the panorama fits a 1080 px wide phone at exactly 2x. The atlas forge interiors (256x157 and
   232x211) are usable too but are the lowest-resolution option. `crops\counter_scene_backdrop_candidates_ABC.png`.
4. **Giving today's tiled `ForgeScene` more room does not work**: the wall tile is not seamless (every tile repeats the
   same ivy and beam, with dark seams), so at 128-192 dp it reads as wallpaper. `crops\forge_scene_today_vs_given_room.png`.
5. **The new atlas is a flat 1536x1024 RGB concept board with 525,973 distinct colours and no pixel grid.** It is good
   for cropped opaque backdrops (market street, town square, castle, night forge), for three menu icons the game lacks
   (home, settings gear, purse), and as reference. Everything with baked text is reference only.
6. **Provenance:** all seven top-level source images and both concept references carry an embedded Content Credentials
   (C2PA) manifest naming ChatGPT / OpenAI as the generator. The project documents call this art "hand-made". No licence
   or attribution text exists anywhere in the asset folders.

---

## 0. Sources audited

| # | Source (under `Pixel art assets\`) | Native size, mode | What it is | Imported today |
|---|---|---|---|---|
| 1 | `ChatGPT Image Oct 8, 2026, 08_29_54 PM-1.png` | 1448x1086 RGBA | Forge pieces: wall tile, floor tile, furnace x3, anvil, tool rack, shelf, 4 embers | 12 sprites (`layout_1`, `import_assets.py:81-96`) |
| 1 | `...08_29_55 PM-2.png` | 1448x1086 RGBA | 6 large weapons, 6 element tiles, 6 rarity gems | 6 badges only (`layout_2`, `:99-104`; weapons and overlays superseded) |
| 1 | `...08_29_56 PM-3.png` | 1254x1254 RGBA | 25 portraits, 5 per class | 25 portraits (`layout_3`, `:107-112`) |
| 1 | `...08_29_57 PM-4.png` | 1448x1086 RGBA | 3 factions: grunt, elite, 2 extras each | 12 sprites (`layout_4`, `:115-132`) |
| 1 | `...08_29_58 PM-5.png` | 1448x1086 RGBA | 16 materials, 8 blessings, 2 panels, paper, 6 nav icons, 6 status icons | 39 sprites (`layout_5`, `:135-155`) |
| 2 | `Weapons master` (no extension) | 1672x941 RGB | 6 families x 7 element rows x 8 levels | 336 sprites at 56x56 plus generated `ui/WeaponArt.kt` (`:243-284`) |
| 3 | `New folder\drawable-nodpi\` | 200 PNGs, true 1x RGBA, 33 colours across the 25 portraits | "V2 production starter pack", script-drawn | 58 sprites: `hero_`, `monster_`, `siege_wall`, `fx_milestone_`, `marker_` (`PACK_PREFIXES`, `:44`) |
| 3 | `New folder\concept_references\approved_hybrid_direction.png` | 1448x1086 RGB | "Style F" board: forge panorama, 5 portraits, weapons, monsters | No (the importer only reads `drawable-nodpi/` in a pack, `:312-335`) |
| 3 | `New folder\concept_references\v2_concept_reference.png` | 1212x1298 RGBA | "Asset preview v2": forge scene, 30 class portraits, 8 town NPCs, tiles, objects | No |
| 3 | `New folder\previews\*.png` | 14 contact sheets | Enlarged previews of the 200 sprites | No (previews) |
| 4 | `Tiny_Blacksmith_UI_Backgrounds_v3\drawable-nodpi\` | 51 PNGs, true 1x RGBA, 16-26 colours each | 11 `bg_*`, 12 `ui_frame_*`, 4 buttons, 2 slots, 5 tiles, 3 dividers, 12 icons, 2 siege walls | No (`PACK_SKIP`, `:46`) |
| 5 | `Tiny Blacksmith RPG Asset Atlas.png` (new) | 1536x1024 RGB | Labelled concept atlas "Background & World Assets" | No (falls into "skip ... not a numbered sheet and not a known sprite ID", `:404`) |
| — | `docs\art_contact_handmade.png` | 1200x6566 | Contact sheet the importer writes of everything it produced | — |

`app\src\main\res\drawable-nodpi\` holds 493 PNGs (2,827,519 bytes on disk): 488 recorded in
`tools\pixelart\overrides.json` (336 `Weapons master`, 94 sheet slices, 58 `pack:New folder`) and 5 generated 16 px
placeholders (`portrait_<class>`). `app\src\main\assets\art\manifest.json` lists all 493 but nothing reads it at
runtime (no `getIdentifier`, `assets.open` or `manifest.json` reference in any `.kt`).

### How "pixel" each source is (measured)

| Sample | Size | Distinct RGB | Identical horizontal neighbours | Reading |
|---|---|---|---|---|
| Atlas, whole image | 1536x1024 | 525,973 | — | Not a pixel grid |
| Atlas forge day `(8,103,264,260)` | 256x157 | 35,713 of 40,192 px | 0.00 | Painted, soft 1-2 px edges |
| Atlas one NPC `(1351,454,1386,508)` | 35x54 | 1,698 of 1,890 px | 0.02 | Painted |
| Sheet -3 guardian cell | 251x251 | 26,905 | 0.03 | Painted, art pixel about 4 source px |
| `res/portrait_guardian_0.png` | 64x64 | 2,429 of 2,601 opaque px | 0.01 | LANCZOS-reduced, about 1 art pixel per bitmap pixel |
| `res/weapon_sword_fire_5.png` | 56x56 | 751 of 753 | 0.00 | Painted, about 2 bitmap px per art pixel |
| Pack `portrait_guardian.png` | 16x16 | 9 | 0.66 | True pixel art |
| V3 `bg_forge_warm.png` | 96x48 | 26 | 0.76 | True pixel art, flat |

Consequence: a classic ramp-for-ramp palette swap is impossible on the concept art (thousands of colours, no ramps);
only hue-window rotation is available (section 2).

Alpha note: the concept sheets almost never reach alpha 255 (sheet -3: 809,663 px at 250-254, 786 px at 255). The
imported slices inherit that, and `tile_wall.png` has 263 of 4,096 pixels below alpha 250, which is one cause of the
tile seams. The weapon sprites are keyed by colour distance (`key_background`, `:188-197`), so a median 30 % of each
weapon's non-empty pixels are semi-transparent (max 61 %, `weapon_bow_frost_6`): dark outlines thin out on light
surfaces. See `crops\sprites_alpha_over_backgrounds.png`. The device runs the light parchment theme
(`ui/theme/Theme.kt:79` follows the system setting), so this is the look on any device in light mode (the device screenshots in `scratchpad/ui_v4` are in light mode).

---

## 1. Portraits

### What is live

- **Files:** `portrait_{guardian,ranger,duelist,battlemage,warden}_{0..4}.png`, 25 files, each 64x64 RGBA, all from
  sheet -3 (`overrides.json`: `"sheet": "ChatGPT Image Oct 8, 2026, 08_29_56 PM-3.png"`). Sliced by
  `layout_3` = `grid(0, 0, 1254, 1254, 5, 5)`, fitted into `box=(64, 64)` with `anchor="top"` (`import_assets.py:107-112`,
  LANCZOS in `fit`, `:172-181`).
- **Not live:** the pack's 25 portraits at 16x16 (`portrait_<class>` plus `_1.._4`) are not imported, because
  `portrait_` is not in `PACK_PREFIXES`. The 5 files named `portrait_<class>.png` in res are generator placeholders
  and are referenced nowhere.
- **Where drawn:** `InfoPanels.kt:111` at 56 dp (champion rows) and `InfoPanels.kt:149` at 44 dp (adventurer list,
  with the 16 dp dead/retired marker at `:150`). Nowhere else: `HomePanel.kt` (170 lines) and `RunEndScreen.kt` draw
  no sprites at all.
- **Filtering:** `PixelImage` (`Sprites.kt:166-176`) picks nearest-neighbour when the bitmap is smaller than the
  target and bilinear when larger (`:170`). At density 3.0, 56 dp is 168 px, so a 64 px portrait is enlarged 2.625x
  with nearest sampling: non-integer, so pixel columns alternate between 2 and 3 device pixels. It reads acceptably
  (see the device crop) but is not square-pixel rendering.

### How a variant is picked

`Sprites.kt:72-76`:

```kotlin
fun portrait(hero: Hero): Int {
    val faces = portraits[hero.classId.value] ?: portraits.getValue("guardian")
    return faces[Math.floorMod(hero.id.value.hashCode(), faces.size)]
}
```

- Index into a hard-coded `List<Int>` per class (`Sprites.kt:64-70`) by `String.hashCode()` of the hero ID.
- Hero IDs are `"h${nextHeroSerial++}"` (`core/.../engine/ResolutionContext.kt:63`), the serial is saved in
  `GameState` and restarts at 1 each run (`GameEngine.kt:56`). `Hero` has **no appearance or portrait field**
  (`core/.../model/Model.kt:92-122`: id, name, surname, classId, level, xp, gold, health, traits, elementTaste, loyalty,
  fame, fate, lastActivity, descendantOf, kills, victories, diedOnDay, guildId, mentorName, retiredOnDay, ambition,
  ambitionDone, expeditionWins, elitesSlain, drivenBackOnDay). `Heroes.generate` (`heroes/Heroes.kt:15-34`) draws class,
  names, traits, taste, gold and ambition from the HEROES stream and nothing cosmetic.
- **Stable across save and load:** yes. The ID is persisted and `String.hashCode` is specified, so the same hero shows
  the same face on any device.
- **Not stable against art changes:** reordering a class list changes which face an index means; extending it changes
  the modulus. Reproducing the JVM hash for `h1..h40`: buckets are `h1→3, h2→4, h3→0, h4→1, h5→2, h6→3, h7→4, h8→0,
  h9→1, h10→1, h11→2, h12→3 ...`; going from 5 to 6 faces moves 32 of 40 heroes to a different face, and going from 5
  to 10 moves about half.
- Side effect of restarting serials: the eight starting heroes `h1..h8` get variant indices 3,4,0,1,2,3,4,0 in every
  run, so each era opens with the same index pattern.

### The slicing defect (new finding)

Measured alpha bands on sheet -3 (alpha > 24): portrait rows occupy y 31-269, 284-507, 520-735, 742-971, 982-1214.
The uniform grid cuts at y 251, 502, 752, 1003. Result in the 64 px outputs (row and column runs of non-empty pixels):

| Class | Defect in the live file | Evidence |
|---|---|---|
| guardian (5) | Bottom 19 source rows cut; side slivers of the neighbour on `_1`, `_2` (`_2` col runs `(0,0),(2,60),(62,63)`) | `crops\portrait_slicing_spill_6x.png` |
| ranger (5) | Rows 0-4 are a strip of the guardian above, then a gap, bust starts at row 8-10 | same |
| duelist (5) | Rows 0-1 ranger strip; rows 61-63 battlemage hat tips | same |
| battlemage (5) | Hat tops cut by 10 source rows; rows 58/59-63 are warden antler tips | device crop |
| warden (5) | Top 21 source rows (antler tips) cut; `_2` has a side sliver | same |

Corrected cells (rows as above, column cuts at the minimum-occupancy column near each grid line, then the existing
`main_component_only` clean-up) are saved in `sheet3_proposed_cells.json` next to this report, for example
guardian cuts `[0, 259, 505, 741, 1000, 1254]`, ranger `[0, 266, 508, 752, 1002, 1254]`.

### Concept busts vs pack portraits

`crops\portraits_concept_vs_pack.png` shows both at 132 px. The concept busts are detailed anime-style characters with
distinct hair, age and gear; the pack portraits are 16 px icons with 9 colours each whose variants differ by skin tone
and one or two accent pixels (they encode the "races" of `hero_variants.csv`: human, dwarf, elf, orc, dragonkin,
goblin, satyr, lizardfolk, halfling, tiefling, feline, undead, dryad, beastkin). They cannot sit in the same list:
at equal display size the pack pixel is 4x larger. `crops\counter_scene_mockB_tiles_and_mockC_mismatch.png` (lower
half) shows the clash inside one scene.

### Distinct faces across all sources

| Class | Sheet -3 (live, 64 px) | `v2_concept_reference.png` tiles (86x84 px) | `approved_hybrid_direction.png` (118x94 px) | Pack (16 px) | Atlas chibis that read as the class | Distinct designs at 64 px quality |
|---|---|---|---|---|---|---|
| guardian | 5 (all full helms, no visible face) | 5 clean + 1 with label | 1, same design as `_0` | 5 | none | **10** |
| ranger | 5 | 5 clean + 1 with label | 1, same design as `_0` | 5 | 1 weak (blue hood) | **10** |
| duelist | 5 | 5 clean + 1 with label | 1, close to `_0` | 5 | 1 (feathered hat) | **10** |
| battlemage | 5 | 5 clean + 1 with label | 1, same design as `_0` | 5 | 1 (purple witch hat) | **10** |
| warden | 5 | 5 clean + 1 with label | 1, same design as `_0` | 5 | none | **10** |
| class-agnostic | — | 8 town NPC tiles: 5 clean humans (townswoman, hatted dwarf, elder woman, bearded man, goggles kid), wolf, cat, 1 hooded figure overlapped by a teal artefact | — | — | 7 NPC chibis, 2 prop chibis, the "Master Doran" bust, 1 tiny toast figure | **6** busts (5 townsfolk + Master Doran) |

So: 25 faces live; 50 distinct class faces exist at usable resolution (25 + 25 clean reference tiles); 55 if the five
label-covered tiles are counted, which I do not recommend. The atlas adds no class portrait: its figures are full-body
chibis of 32-37 x 49-52 px (`crops\atlas_npc_strip_8x.png`), and only three of the seven read as a hero class.

### Duplication a town shows today

Starting heroes 8, alive cap 12 (`config/BalanceConfig.kt:76`, `:163`); the adventurer list also keeps the dead and
retired. Modelling class and face as uniform (40,000 trials):

| Heroes shown | 5 faces/class (today) | 10 faces/class |
|---|---|---|
| 8 | 72 % chance of a shared face, 1.0 heroes wearing someone else's face | 45 %, 0.5 |
| 12 | 96 %, 2.3 | 76 %, 1.2 |
| 16 | 100 %, 4.0 | 93 %, 2.2 |
| 20 | 100 %, 6.1 | 99 %, 3.4 |
| 24 | 100 %, 8.4 | 100 %, 4.8 |

Doubling the pool halves the problem but hash-picking can never remove it. Removing it needs assignment that knows
which faces are in use (section 2).

---

## 2. Appearance variety plan

### Honest pool sizes

| Tier | What | Looks | New art | Work |
|---|---|---|---|---|
| 0 | Today | 25 faces, 15 with slicing defects | — | — |
| 1 | Re-slice sheet -3 with measured cells | 25 clean faces | none | importer: replace `layout_3` grid |
| 2 | Add the 25 clean class tiles of `v2_concept_reference.png` | 50 faces, 10 per class | none | importer: new flat-source entry, 25 boxes |
| 2b | Add 5 townsfolk tiles + "Master Doran" for non-hero roles (supplier, collector, patron) | +6 class-agnostic | none | same entry, 6 boxes |
| 3 | Curated import-time hue variants of the sheet -3 busts | about +45 liveries (same face, different colours) | none | importer: data table of (base ID, hue window, shift, min saturation) plus a visual review |
| — | Pack 16 px portraits | 25 | — | Do not mix with the 64 px set |
| — | Atlas chibis as portraits | 9 | — | Do not use as portraits; possible walk-on figures on dark backgrounds only |

Style caveat for tier 2: the reference tiles are darker, more painterly square tiles with their own background; the
sheet -3 busts are cleaner cut-outs on transparency. Put every portrait on one Compose-drawn dark tile with a frame so
both families read as one set. This needs a look on device before committing.

### Cheap deterministic variation, tested

| Technique | Feasible from these sources | Verdict and evidence |
|---|---|---|
| Ramp palette swap of hair or cloth | No | The art has no ramps (2,429 colours in a 64 px portrait) |
| Hue-window rotation at import time | Yes, curated per portrait | Guardian blue to crimson, green or purple is clean on `_0`, `_2`, `_3`; `_1` and `_4` barely change (2-3 % of pixels). Ranger hood to teal, russet or slate is clean on `_0`, `_1`, `_2`; blotchy on the olive hoods `_3`, `_4`. Battlemage hat to teal, crimson or midnight is clean on `_0`, `_1`, `_3` (hair shifts with the hat, which helps), partial on `_2`, `_4`. Warden leaves to autumn or frost work on all five; teal is too subtle to count. `crops\variety_hueshift_{guardian,ranger,battlemage,warden}.png` |
| Same, naive window on duelists | Looks wrong | The red window 340-20 degrees overlaps skin and lips: faces turn blue or green (`crops\variety_hueshift_duelist.png`). Tightened to 345-6 degrees with saturation at least 0.60 it is clean on `_0`, `_2`, `_3` (`crops\variety_hueshift_duelist_tightened.png`) |
| Hue rotation on the reference tiles | Not recommended | The tiles carry coloured backgrounds that shift with the cloth |
| Mirrored variants | Feasible at runtime (the siege stage already flips, `Sprites.kt:304`) | Not a new identity: a mirrored face is the same person, the light flips to upper-right against the brief, and guardian `_1`'s lion flips. Use only to make a customer face the shelf |
| Accessory overlays (scars, patches, hats) | No | No layered portrait parts exist (pack `ART_STATUS.md`: "Optional modular layered portraits are not included"); auto-placed overlays would not register on 25 different busts |
| Backdrop or frame tint by guild or class | Yes, runtime only | `Hero.guildId` exists (`Model.kt:112`). Zero assets. Adds context, not a face |
| Corner pips (regular customer, fame) | Yes | `icon_reputation` and `badge_*` are present; `icon_reputation` is currently unreferenced |

Honest ceiling: **50 distinct faces (10 per class) plus about 45 colour liveries**, roughly 95 class looks, and 6
class-agnostic busts. Hue variants are import-time outputs with their own IDs, so the "never hand-edit PNGs" rule holds.

### Saved appearance key

Constraints that are implied but not written anywhere:

- The key must be assigned **without drawing from the HEROES RNG stream**. `Heroes.generate` consumes that stream in a
  fixed order; one extra draw shifts every later hero, every simulator seed and every determinism test.
- `:core` has no Android dependency, so it stores a string and `:app` maps it to a drawable.

Proposal:

1. `Hero.appearanceKey: String? = null` (and the same optional field on `LineageAnchor`, `Model.kt:244`, if descendants
   should resemble their ancestor). `SaveCodec` already runs with `encodeDefaults = true` and `ignoreUnknownKeys = true`
   (`core/.../persistence/SaveCodec.kt:16-17`) and states that "Fields added with defaults need no step" (`:38`), so no
   migration step is required and `migrations` can stay empty (`:41`).
2. **Key = the asset ID string**, never an index: `portrait_guardian_0`, `portrait_guardian_0.crimson`,
   `portrait_v2_guardian_3`, `portrait_npc_elder_woman`. Core owns the list of valid keys per class as content
   (validated by `ContentCatalog.validate()`); the importer generates `ui/PortraitArt.kt` (key to `R.drawable`), the way
   it already generates `WeaponArt.kt`.
3. **Assignment at hero creation, pure function, no RNG:** order the class pool by `hash(state.seed, heroId, key)` (`GameState.seed` exists, `Model.kt:272`; hash the value, do not draw from an RNG stream), take
   the first key not held by a living hero, fall back to the first key if all are taken. Deterministic from saved
   state, varies between eras (unlike today), and gives zero duplicates among the living while a class has at most 10
   members.
4. **Fallback when a key's asset is missing** (art removed or renamed): `PortraitArt[key] ?: PortraitArt[legacyKey(hero)]
   ?: portrait_<class>_0`. The class is always known, so a face of the right class always renders.
5. **Backfill for old saves:** leave `appearanceKey` null and resolve it in one pure function:
   `legacyKey(hero) = "portrait_${classId}_${Math.floorMod(id.hashCode(), 5)}"`. That is today's formula with the
   modulus frozen at 5, so every existing hero keeps the face the player already knows, whatever is added later.
   A test should pin `h1..h40` to the table in section 1.
6. The shop-visit snapshot DTO (S04) should carry the resolved key string, so a replayed visit shows the right face
   after the hero has died.

---

## 3. Signature weapons

- Game: 24 recipes in `core/.../crafting/Signatures.kt:54-105`.
- Pack: 12 sprites, 16x16, `weapon_sig_<id>`.

| Family | Covered by the pack (16 px) | Missing |
|---|---|---|
| sword | dawnbrand, winterwake, tempest_edge, ashen_vow | — |
| axe | hearthcleaver, glacier_maul, thunderhead, emberfall | — |
| bow | stormsong, frostwhisper, cinder_arc, galewood | — |
| spear | — | thornwall, hoarfrost_pike, sunlance, gravewarden |
| dagger | — | nightletter, sparkfang, emberneedle, rimeshard |
| staff | — | noonward, greenheart, stormcaller, mourning_rod |

- **Runtime mapping: none.** No `weapon_sig_*` file is in res, and no `.kt` mentions the prefix. `Sprites.weapon`
  (`Sprites.kt:57-62`) looks only at family, core, element and rarity; `signatureId` is read once in the UI, to decide
  whether to play the burst on the result card (`Dialogs.kt:76`).
- The PROGRESS note is right (`docs/PROGRESS.md`, "Known limitations", line 126 at review time): a 16 px sprite with 8 colours next to 56 px painted
  weapons is the clash shown in `crops\counter_scene_mockB_tiles_and_mockC_mismatch.png` (top right of the lower mock).
- Practical position: all 24 signatures lack usable art. A signature is a fixed family + core + augment, so it already
  maps to one master-sheet cell. Cosmetic workaround with present art: show signatures two visual levels higher on the
  master sheet (clamped to 8), keep the milestone burst, add a Compose-drawn gold ring. Mapping change is confined to
  `Sprites.weaponLevel` (`Sprites.kt:53-55`). Bespoke sprites are an artist gap (section 7).

---

## 4. Unused art (verified by script)

Method: every `R.drawable.<name>` and `@drawable/<name>` in `app/src/**/*.kt|xml` matched against the 493 file stems.
Result: 477 referenced (336 in `WeaponArt.kt`, 131 in `Sprites.kt`, 9 in `WorkshopScreen.kt`, 1 in `ForgePanel.kt`),
**16 unreferenced**.

| In res, never referenced (16) | Notes |
|---|---|
| `panel_gazette` (256x126), `panel_journal` (256x163) | Ornate frames with a banner, lantern and scribbled pseudo-text; not nine-patchable |
| `badge_flaw` (24x24) | Red cracked gem. `Weapon.flaws` exists (`Model.kt:63`); nothing draws a flaw badge |
| `icon_militia`, `icon_reputation` (24x24) | The top strip shows only day, gold, energy (`WorkshopScreen.kt:169-171`) |
| `faction_{ashclaw,hollowbound,embermaw}_alt_{0,1}` (64x64, 6 files) | Archer, spearman, caster, second drake: unused enemy variety |
| `portrait_{guardian,ranger,duelist,battlemage,warden}` (16x16, 5 files) | Generator placeholders, dead resources |

| Present in the folders, not imported | Count | Notes |
|---|---|---|
| Pack sprites outside `PACK_PREFIXES` | 142 of 200 | 36 `weapon_<family>_<core>`, 12 `weapon_sig_*`, 6 `overlay_*`, 25 portraits, 8 forge fixtures + 4 embers, 6 badges, 16 materials, 8 blessings, 12 icons, 6 factions, 2 panels, `tile_paper` |
| `Tiny_Blacksmith_UI_Backgrounds_v3` | 51 of 51 | Whole pack skipped. 15 IDs collide with live IDs (12 icons, `siege_wall`, `siege_wall_damaged`, `tile_paper`), 36 are new (`bg_*`, `ui_*`) |
| `concept_references\*.png` | 2 boards | Forge panorama, forge scene, 35 portraits, 8 town NPCs |
| Sheet -2 weapon and overlay cells | 12 cells | Superseded by the master sheet (comment at `import_assets.py:99`) |
| Atlas | all | New today |

Used only in one place: the pack's `hero_*` idle and attack frames and `monster_*` frames appear solely in the siege
diorama (`Dialogs.kt:232-244`, `Sprites.kt:119-145`, `SiegeStage` `:284-313`). The embers are used (`Sprites.kt:227-232`).

---

## 5. Counter scene feasibility

### Today

`ForgePanel.kt:85` draws `ForgeScene(..., height = 52.dp)`. On a 360 dp, density 3.0 phone the canvas is 1080x156 px.
`ForgeScene` picks `scale = min(w/96, h/48).toInt()` = 3 (`Sprites.kt:242`) while the bitmaps are authored at 4 px per
scene pixel (`unit`, `:233`; `SCENE_UNIT = 4`, `import_assets.py:50`), so every piece is drawn at 0.75x with bilinear
filtering (`:247`): a 288x144 px stage inside a 1080 px strip of repeating wall. The replica in
`crops\forge_scene_today_vs_given_room.png` matches the device crop.

Only scales that are multiples of 4 draw the pieces at an integer factor (4, 8, 12). Scale 8 needs a 384 px tall canvas:
128 dp at density 3.0 but 192 dp at 2.0. A fixed dp height therefore cannot guarantee clean scaling; the scale must be
snapped. The scale is also bound by width: on a 720 px wide phone `min(720/96, ...)` caps at 7, so scale 8 is
unreachable there at any height and only scale 4 is clean. More importantly, at scale 8 the wall shows the same ivy
and beam on every 128 px tile with dark seams, and the
concept `shelf` is already full (plant, lantern, books, helmet) so there is nowhere to stand stock.
**Recommendation: do not build the counter scene on the tiled `ForgeScene`.**

### Backdrop candidates (all flat painted images, all need an import entry)

| | Source and clean crop box | Native | Integer scale on 1080 px | On screen at 3.0 | ARGB_8888 bytes | Notes |
|---|---|---|---|---|---|---|
| **C (recommended)** | `concept_references\approved_hybrid_direction.png` `(262,135,802,405)` | 540x270 | 2x, exact fit | 360x180 dp | 583,200 | Furnace, banners, tool wall, anvil, window. Caption box (x 40-235) and logo (x over 860, y under 135) are outside the crop |
| B (alternative) | `concept_references\v2_concept_reference.png` `(47,79,587,290)` | 540x211 | 2x, exact fit | 360x141 dp | 455,760 | Furnace, anvil, a long workbench along the back wall, town window, sleeping wolf. The baked "FORGE SCENE (96x48)" label sits above y 78 |
| A | Atlas forge day `(8,103,264,260)` | 256x157 | 4x = 1024 px, 28 px margins | 341x209 dp | 160,768 | Usable; half the detail of B and C |
| A night | Atlas forge night `(270,49,502,260)` | 232x211 | 4x = 928 px or 5x cropped | 309x281 dp | 195,808 | Only night interior available; different framing from C |
| — | V3 `bg_forge_{warm,hot,cold,night}` | 96x48 | 11x | 352x176 dp | 18,432 each | True pixel, 26 colours; clashes with every painted sprite |

`crops\counter_scene_backdrop_candidates_ABC.png` renders A, B and C at 1080 px with the same foreground;
`crops\counter_scene_recommended_C_1080px.png` and `crops\counter_scene_alt_B_1080px.png` are the individual mocks.
The mocks were composed in the scratch folder only; text boxes and the shelf band stand in for native Compose UI.

### Recommended composition (C)

| Layer | Asset | Native | Draw | Filtering |
|---|---|---|---|---|
| 0 Backdrop | new `bg_counter_forge` from crop C | 540x270 | Integer scale `s = ceil(widthPx / 540)`, source window centred horizontally and anchored to the bottom, cropped to the box | Nearest. Never a fractional nearest scale; if a fractional fit is ever forced, use bilinear with a scrim |
| 1 Customer | `portrait_<class>_<n>` bust (re-sliced) | 64x64, content 50-64 rows | `2s` (4x at 1080 px = 256 px = 85 dp), bottom of the content box on the counter line, centre at about 62 % of the width | Nearest, integer |
| 2 Counter | Compose-drawn plank band under the scene (palette `wood2 #8C6239`, `wood1 #6B4B32`, `ink #1A1210`) | — | Full width, 8-10 dp lip, overlapping the bust's bottom edge | — |
| 3 Stock | `weapon_<family>_<row>_<level>` from the 336-sprite set | 56x56, content median 45x50 | 4-5 slots, weapon at `s` (112 px = 37 dp) or 3x (168 px = 56 dp) inside a 56-64 dp slot on a dark surface | Nearest, integer |
| 4 Text | Native Compose only | — | Name plate on a solid surface in a scene corner; dialogue line, reason, price, cash and trade-in in a card below the shelf band | — |

Why this hangs together: the three painted sources agree on apparent pixel size at these factors. Backdrop art pixel
is about 2 source px, so 4 device px at 2x; a portrait bitmap pixel is about one art pixel, so 4 device px at 4x; a
weapon art pixel is about 2 bitmap px, so 4 device px at 2x. With backdrop A the backdrop pixel is 8 device px against
4 for the bust, which is tolerable but visibly coarser.

Density behaviour, since integer device-pixel scales change the dp size:

| Screen | `s` | Backdrop px | Visible source window | Scene height |
|---|---|---|---|---|
| 1080 px wide (360 dp at 3.0, 411 dp at 2.625) | 2 | 1080x540 | all 540x270 | 180 dp / 206 dp |
| 720 px wide (360 dp at 2.0) | 2 | 1080x540 | 360 wide; cap the box at about 200 dp, so 200 rows | 200 dp |
| 1440 px wide (411 dp at 3.5) | 3 | 1620x810 | 480 wide; cap at 200 dp, so 233 rows | 200 dp |

Keep the furnace and anvil inside the centre 360 source px so the 720 px crop still reads.

Weapons belong on a dark slot, not directly on the bright backdrop: their keyed outlines are partly transparent
(`crops\sprites_alpha_over_backgrounds.png`).

Scene memory, bitmaps decoded at native size because they live in `drawable-nodpi`:
backdrop C 583,200 + one bust 16,384 + five weapons 62,720 = **662,304 bytes** (about 0.63 MiB). With backdrop A it
is 239,872 bytes. For comparison, today's forge scene pieces total about 150 KB, all 336 weapons would be 4,214,784
bytes if every one were loaded, and shipping the atlas whole would cost 6,291,456 bytes for one bitmap. Import crops,
never the boards.

Do not mix inside one scene: pack 16 px figures or signatures, V3 `bg_*` backdrops, and the painted concept art
(`crops\counter_scene_mockB_tiles_and_mockC_mismatch.png`). The pack frames stay confined to the siege diorama, which is
internally consistent (16 px actors on the pack's 96x32 wall).

Limits of this composition, stated plainly: the furnace in a painted backdrop cannot change with energy the way
`furnace_cold/warm/hot` do today, there is no night version of C, and there is no customer animation beyond what
Compose can do to a still bust (slide, fade, a one-pixel bob, all off under reduced motion).

---

## 6. Asset-use table

Status key: PRESENT-USED, PRESENT-UNUSED-REUSABLE, ADAPTABLE (needs an import step), REFERENCE-ONLY, MISSING.

### Characters

| Asset ID or path | Intended scene or component | Current integration | Mapping or import work | Gaps and verification | Status |
|---|---|---|---|---|---|
| `portrait_<class>_0..4` (25, 64x64, sheet -3) | Customer bust at the counter; Town rows; hero detail | Used: `Sprites.kt:64-76`, `InfoPanels.kt:111` (56 dp), `:149` (44 dp) | Re-slice with measured cells; record the content box so a bust can be bottom-aligned; switch selection to a saved key | 15 files have neighbour strips or cut tops; check the re-slice on device at 44, 56 and 85 dp | PRESENT-USED (defective) |
| `v2_concept_reference.png` class tiles, columns 1-5 (25, 86x84) | Second face set, 5 per class | None | New flat-source entry, boxes in `v2ref_portrait_cells.json`, fit to 64x64, IDs `portrait_v2_<class>_<1..5>` | Darker painterly style on a tile background; confirm both sets read as one family on a shared tile | ADAPTABLE |
| Same file, column 0 of each class (5) | — | None | — | Class label baked across the top | REFERENCE-ONLY |
| Same file, town NPC tiles (8, about 56-64 x 65) | Supplier, collector, non-hero patrons | None | 5 clean boxes: townswoman, hatted dwarf, elder woman, bearded man, goggles kid | Hooded tile is overlapped by a teal artefact; wolf and cat are animals | ADAPTABLE (5), REFERENCE-ONLY (3) |
| `approved_hybrid_direction.png` portraits (5, 118x94) | — | None | — | Same character designs as the sheet -3 `_0` variants; adds no identity | REFERENCE-ONLY |
| Atlas dialogue portrait "Master Doran" `(1247,776,1300,840)`, 53x64 | The smith or a named NPC | None | Crop inside its frame, keep its own dark tile | More realistic rendering than the hero busts; only one pose | ADAPTABLE |
| Atlas NPC chibis (7, 32-37 x 49-52) and prop chibis (2) | Walk-on figures | None | Border flood-fill keying; global colour keying eats dark clothing | Soft, small, only 3 read as a class; fine only on dark surfaces (`crops\atlas_figures_keyed_4x.png`) | REFERENCE-ONLY (ADAPTABLE at best) |
| Pack `portrait_*` (25, 16x16) | — | Not imported | — | Clashes with the 64 px set | REFERENCE-ONLY for this update |
| `portrait_<class>` placeholders in res (5) | — | Unreferenced | Stop generating or delete on the next import | Dead resources | PRESENT-UNUSED |
| `hero_<class>_{idle,attack}_{0,1}` (20, 16x16, pack) | Siege diorama | Used: `Sprites.kt:119-140`, `Dialogs.kt:232` | None | Bust-like, no walk cycle; not for the counter | PRESENT-USED |
| `marker_dead`, `marker_retired` (8x8, pack) | Adventurer list | Used: `Sprites.kt:149`, `InfoPanels.kt:150` | None | — | PRESENT-USED |

### Weapons and items

| Asset | Intended use | Current integration | Work | Gaps and verification | Status |
|---|---|---|---|---|---|
| `weapon_<family>_<row>_<level>` (336, 56x56, master sheet) | Stock on the shelf, hero gear, forge preview, result card | Used: `WeaponArt.kt`, `Sprites.kt:53-62`; 24 dp chips (`ForgePanel.kt:131`), preview (`:257`), 44 dp (`InfoPanels.kt:117`), 48 dp (`MarketPanel.kt:172`), 96 dp (`Dialogs.kt:75`) | None for the scene; draw at an integer factor | Outlines are semi-transparent; keep on dark slots or switch the keying to border flood-fill and re-check | PRESENT-USED |
| `badge_{common..legendary}` (24x24) | Rarity pip | Used: `Sprites.kt:153-159`, `:183` | None | — | PRESENT-USED |
| `badge_flaw` | Flaw pip on shelf and counter | Unreferenced | Map from `Weapon.flaws` in `WeaponSprite` | Shape reads at 16 dp? | PRESENT-UNUSED-REUSABLE |
| Pack `weapon_sig_*` (12, 16x16) | Signature look | Not imported | — | 16 px against 56 px weapons; 12 of 24 only | REFERENCE-ONLY |
| Pack `weapon_<family>_<core>` (36), `overlay_*` (6) | — | Not imported | — | Superseded by the master sheet | REFERENCE-ONLY |
| Signature sprites at 56 px (24) | Recognisable legends | — | Level bump + ring as a stopgap | Genuine artist gap | MISSING |
| `material_*` (16, 48x48), `blessing_*` (8, 48x48) | Supplier, pickers, blessing choice | Used: `Sprites.kt:87-117`; `ForgePanel.kt:335`, `MarketPanel.kt:109`, `Dialogs.kt:204`, `InfoPanels.kt:232` | None | — | PRESENT-USED |
| Atlas inventory icons, "Flamebrand +5" sword | — | None | — | Baked rarity frames and text | REFERENCE-ONLY |

### Scenes and backdrops

| Asset | Intended use | Current integration | Work | Gaps and verification | Status |
|---|---|---|---|---|---|
| `tile_wall`, `tile_floor`, `furnace_{cold,warm,hot}`, `anvil`, `tool_rack`, `shelf`, `ember_0..3` (sheet -1) | Forge strip | Used: `Sprites.kt:213-274`; `ForgePanel.kt:85` (52 dp), `App.kt:51` (title, 100 dp) | If kept: snap scale to a multiple of 4; harden tile alpha | Wall tile is not seamless; shelf is fully dressed | PRESENT-USED |
| `approved_hybrid_direction.png` crop `(262,135,802,405)` | Counter and shop-day backdrop | None | New flat-source entry, opaque copy, ID `bg_counter_forge` | Verify the crop edges on device; no heat states, no night | ADAPTABLE (recommended) |
| Same board, wide clean strip `(240,140,1225,405)`, 985x265 | Title or store banner base | None | Optional entry | Logo and caption are outside the box (checked, `crops
efboard_hybrid_wide_clean_strip_2x.png`); a faint shadow touches the top-right 4 rows | ADAPTABLE |
| `v2_concept_reference.png` forge `(47,79,587,290)` | Alternative backdrop with a workbench | None | Same mechanism | — | ADAPTABLE |
| Atlas forge day `(8,103,264,260)`, forge night `(270,49,502,260)` | Low-res backdrop; night or End Day mood | None | Name-matched atlas entry, opaque crop | Boxes verified by corner zoom to within 1 px; label reads "FORGE INTERIOR - DIGHT" and is outside the box | ADAPTABLE |
| Atlas town square `(516,49,682,259)`, market street `(696,49,877,259)`, castle/guild `(890,49,1076,259)`, wilderness `(1096,49,1306,259)`, dungeon `(1318,49,1528,259)` | Town, Market, Gazette or expedition headers | None | Same; 166-210 px wide, so 5-6x on a phone | Last three boxes measured to about 2 px, not corner-verified; portrait aspect, soft at 5x | ADAPTABLE |
| Atlas environment and map thumbnails (18, about 71x91) | — | None | — | Too small, labels directly beneath | REFERENCE-ONLY |
| Atlas tile sets ("16x16") | — | None | — | Not on a grid (about 28-30 px pitch, soft) | REFERENCE-ONLY |
| Atlas props: counter table `(950,526,1042,582)`, anvil, barrels, stalls, cart, lamp | Scene dressing | None | Border flood-fill keying | 3/4 view, small, soft; the weapon rack and bottle shelf have baked contents | REFERENCE-ONLY (ADAPTABLE for the table at best) |
| V3 `bg_forge_*`, `bg_market`, `bg_town`, `bg_journal`, `bg_gazette`, `bg_legacy` (96x48), `bg_title_workshop_night`, `bg_run_end_fallen_forge` (270x150) | Panel headers, title, run end | Not imported (`PACK_SKIP`) | Allowlist specific IDs if wanted | Flat true-pixel style; do not put painted sprites on them. The two 270x150 pieces are the only title and run-end art that exists | PRESENT-UNUSED-REUSABLE (stand-alone only) |
| `siege_wall`, `siege_wall_damaged` (96x32, pack) | Siege diorama | Used: `Sprites.kt:147`, `:286` | None | V3 holds a second pair under the same IDs | PRESENT-USED |
| `faction_*_{raider,brute,shade,wraith,whelp,drake}` (80 and 112 px) | Threat lines | Used: `Sprites.kt:79-84`; `InfoPanels.kt:54` (56 dp), `:87` (40 dp) | None | — | PRESENT-USED |
| `faction_*_alt_{0,1}` (6, 64x64) | Enemy variety in Town and Gazette | Unreferenced | Add to `Sprites.faction` | — | PRESENT-UNUSED-REUSABLE |
| `monster_*` frames (30, pack), `fx_milestone_0..3` | Siege diorama, result burst | Used: `Sprites.kt:126-151`, `Dialogs.kt:76` | None | — | PRESENT-USED |

### UI chrome and icons

| Asset | Intended use | Current integration | Work | Gaps and verification | Status |
|---|---|---|---|---|---|
| `icon_nav_{forge,market,town,journal,gazette,legacy}` (40x40), `icon_{day,gold,energy,integrity}` (24x24) | Navigation, status | Used: `WorkshopScreen.kt:65`, `:169-171`, `:178`, `:189-190`; `ForgePanel.kt:206` | None | Home tab borrows `icon_day` (`WorkshopScreen.kt:189`) | PRESENT-USED |
| `icon_militia`, `icon_reputation` | Champions, regulars, reputation | Unreferenced | Map where the update shows those facts | — | PRESENT-UNUSED-REUSABLE |
| Atlas menu icons (14, framed, about 37x38): home `(1235,897,1272,935)`, purse `(1400,897,1437,935)`, gear `(1483,942,1520,981)` | Home tab, trade-in or purse, Settings | None | Atlas entry, square crop with its own frame, fit to 40x40 | Same framed look as the sheet -5 nav icons at a quarter of the source resolution; the three boxes are checked in `cropstlas_adaptable_icons_and_bust.png` | ADAPTABLE |
| `tile_paper` (64x64) | Gazette and day report | Used: `Sprites.kt:188-210`, `Dialogs.kt:115` | None | Stretched, not tiled | PRESENT-USED |
| `panel_gazette`, `panel_journal` | Header ornaments | Unreferenced | Use unstretched or drop | Not nine-patchable; pseudo-text scribbles | PRESENT-UNUSED-REUSABLE |
| V3 `ui_frame_*` (12), `ui_button_*` (4), `ui_slot_*` (2), `ui_tile_*` (4), `ui_divider_*` (3) | Frames, slots | Not imported | Allowlist + a nine-slice renderer (README: not `.9.png`) | 8 px caps become chunky borders; DECISIONS rejected them once (`docs/DECISIONS.md:868-875`) | PRESENT-UNUSED-REUSABLE |
| Atlas UI panels, HUD, 5 notification toasts, dialogue text panel, inventory grid | — | None | — | Baked text ("Blacksmith", "Enhance Weapon?", "Forge Complete!", "Master Doran", "12,450", "Day 12 Summer"); all text must be UI-rendered | REFERENCE-ONLY |
| Atlas empty slot frames, 4 at about `(661,892,695,926)` | Shelf slot | None | Crop one | Soft 34 px frame; a Compose-drawn slot is cleaner | REFERENCE-ONLY |
| Launcher icon | App identity | Android Studio default (`res/drawable/ic_launcher_foreground.xml`, background `#3DDC84`) | See section 7 | — | MISSING |

Measured atlas regions are also in `atlas_boxes.json` next to this report; enlarged crops with source-pixel rulers are
`crops\atlas_*.png`.

---

## 7. Missing art

| Need | Exists in usable form? | Workaround with present art | Verdict |
|---|---|---|---|
| Counter foreground strip in the painted style | No. The concept `shelf` is dressed, the atlas table is a small 3/4 prop | Compose-drawn plank band in palette wood colours | Workaround is adequate; artist gap if the counter should carry character |
| Shelf with an empty surface for stock | No | Compose-drawn slots on a dark band | Workaround adequate |
| Customer at scene scale that walks, idles, reacts | No. Only still busts, plus 16 px siege figures that clash | Slide and fade the bust, one-pixel bob, flip to face the shelf; all disabled under reduced motion | Genuine gap for any real animation |
| Transaction outcome icons (paid, too dear, not better, trade-in, commission, collector) | Only `icon_gold` | Native text chips plus `icon_gold`; atlas purse icon for trade-in | Genuine gap: 5-6 icons in the sheet -5 style |
| Trade-in icon | No | Atlas purse icon (ADAPTABLE), or a small weapon sprite with an arrow drawn by Compose | Small gap |
| Signature weapons at 56 px | No: 12 exist at 16 px, 12 do not exist | Two-level bump on the master sheet, burst, gold ring | Genuine gap: 24 sprites |
| Backdrop heat states and a night variant matching backdrop C | No | Overlay the existing `ember_*` loop; dim the furnace mouth and tint the scene in Compose; or accept the atlas night forge at lower resolution | Gap if heat must stay visible in the scene |
| App icon and adaptive layers | No | Sheet -5 `blessing_forgefire` (anvil in flames) or `icon_nav_forge`, re-exported large by the importer from the source cell | Workaround plausible; needs an owner decision |
| Feature graphic 1024x500, store screenshots frames | No | Wide strip of the hybrid panorama as a base, with native title lettering | Gap (store art) |
| Title and run-end key art in the painted style | Only V3 flat 270x150 pieces | Backdrop C for the title | Workaround adequate |
| Guild emblems | No | Tinted ring from `guildId` | Workaround adequate |
| Home and Settings icons | No | Atlas home and gear icons (ADAPTABLE) | Small gap |
| Dialogue frame, toast frames | Only with baked text | Material surfaces, or V3 `ui_frame_card` with a nine-slice renderer | Workaround adequate |
| More faces than 10 per class | No | Hue liveries (section 2) | Artist gap only if the population grows well past 16 |

---

## 8. Import plan

All changes are to `tools/pixelart/import_assets.py`; nothing here was run.

1. **Fix `layout_3` (`:107-112`).** Replace the uniform grid with explicit cells: rows
   `(31,270) (284,508) (520,736) (742,972) (982,1215)` and per-row column cuts from `sheet3_proposed_cells.json`.
   Run each cell through `main_component_only` (`:200-231`, already used for the master sheet) before `tight_crop`.
   Keep the 25 IDs and the 64x64 box. Add a self-check: a portrait whose non-empty rows form more than one run fails
   the import.
2. **Record the content box.** Portraits are padded at the bottom (`anchor="top"`; for example guardian `_3` fills rows
   0-49 of 64). Write `contentBox` into `overrides.json` and the generated lookup so a scene can stand the bust on the
   counter line.
3. **Add a flat-source table**, matched by file name like the master sheet (`:385-391`), each entry listing
   `id, cell, out size, mode`:
   - `New folder/concept_references/approved_hybrid_direction.png`: `bg_counter_forge` `(262,135,802,405)`, mode
     opaque (copy pixels 1:1, alpha forced to 255, no tight crop, no resample).
   - `New folder/concept_references/v2_concept_reference.png`: optional `bg_workshop_bench` `(47,79,587,290)`;
     25 `portrait_v2_<class>_<1..5>` and 5 `portrait_npc_<name>` from `v2ref_portrait_cells.json`, fitted to 64x64,
     opaque tiles.
   - `Tiny Blacksmith RPG Asset Atlas.png`: only what is adopted, for example `bg_market_street`, `bg_town_square`,
     `bg_forge_night`, `icon_nav_home`, `icon_settings`, `icon_purse`.
   The importer must also look inside `concept_references/`, which `import_pack` ignores today.
4. **No blanket flags.** `--pack-all` today would overwrite 83 sheet-derived IDs with 16 px pack sprites, including
   20 portraits (`_1.._4` become 16 px while `_0` stays 64 px) and `tile_wall` (which changes `ForgeScene`'s `unit`
   from 4 to 1), and would add 54 unreferenced files. Removing V3 from `PACK_SKIP` would by default replace `siege_wall` and `siege_wall_damaged` (prefix match; packs are read in folder order and the later write wins, `:415-422`), and under `--pack-all` also the 12 icons and `tile_paper`.
   Replace both with a per-pack allowlist of ID prefixes or exact IDs, and make an ID produced twice in one run an
   error that names both sources.
5. **Keying and alpha.** For flat RGB sources use background removal by flood fill from the cell border, not global
   colour distance, so dark interior pixels survive. After cropping, snap alpha: 250 and above to 255, below 16 to 0.
   Tiles should be written fully opaque.
6. **Hue variants as data.** A table of `(new id, base id, hue window, shift, minimum saturation)` applied after
   slicing, with every output on the contact sheet for review. Only reviewed rows ship.
7. **Generated lookup.** Emit `ui/PortraitArt.kt` (appearance key string to `R.drawable`, plus content box) the way
   `WeaponArt.kt` is emitted (`:265-284`), so Kotlin never hand-lists portrait IDs and a missing asset is a compile
   error rather than a wrong face.
8. **Reproducibility.** Inputs are the files in `Pixel art assets\`, boxes are constants in the script, LANCZOS is
   deterministic for a given Pillow build. Pin Pillow and numpy in a requirements file, and store each source's SHA-256
   in `overrides.json` so a re-exported sheet is detected instead of silently re-sliced with stale boxes. The prune
   step (`:423-429`) keeps removals consistent. `generate_assets.py` then only fills IDs with no override
   (`generate_assets.py:749-759`); it should stop emitting the five dead `portrait_<class>` placeholders.
9. **Filtering rules to write down.**
   - True 1x grids (pack, V3): nearest, integer factors only.
   - Reduced concept slices (portraits, weapons, materials): nearest at an integer enlargement, bilinear when shrunk.
     `PixelImage` currently allows fractional nearest enlargement (`Sprites.kt:170`); inside the scene Canvas compute
     integer destinations the way `ForgeScene` and `SiegeStage` do.
   - Flat backdrops: integer nearest with a centred, bottom-anchored source window.
10. **Anchors.** Backdrop: bottom-centre. Bust: bottom-centre of the content box. Weapons: centred in the slot (the
    56 px box is already centred, `pad_square` `:234-240`). The pack manifest's anchors (`bottom-center` for heroes,
    monsters and weapons, `top-left` for portraits and icons, `tile` for walls) are copied into `overrides.json`
    (`:327-331`) but no runtime code reads them.
11. **Animation timing available.** Pack `manifest.json` gives `durationMs` 120 for the 50 hero and monster frames,
    180 for the 4 embers, 100 for the 4 milestone frames. The app's own manifest lists one animation (embers, 180 ms).
    Runtime values are hard-coded and differ for two of the three: embers 720 ms over 4 frames = 180 ms
    (`Sprites.kt:230`), siege tick 480 ms over 2 frames = 240 ms (`:289`), burst 640 ms over 4 = 160 ms (`:320`).
    There are no frames for a customer; that motion is Compose-driven.

---

## 9. Licensing, attribution, provenance

- **No licence, usage or attribution text exists** in any README, manifest, CSV or script under `Pixel art assets\`
  (searched for licence/license, copyright, attribution, CC0, CC-BY, royalty, "all rights"). The repository root has no
  LICENSE or NOTICE file.
- **Embedded provenance.** All seven top-level images (sheets -1 to -5, `Weapons master`, the atlas) and both files in
  `concept_references\` carry a `caBX` PNG chunk holding a C2PA manifest. Readable strings from the atlas:
  `softwareAgent ... name "ChatGPT" version "gpt-image"`,
  `digitalSourceType http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia`,
  `claim_generator_info ... "OpenAI Media Service API"`, signer `OpenAI OpCo, LLC`, created `2026-10-08T19:44:12Z`.
  None of the 493 shipped PNGs retains the chunk (Pillow re-saves drop it), and none of the 251 pack or V3 sprites has one.
- **Generation artefacts in the images:** the atlas label reads "FORGE INTERIOR - DIGHT"; the v2 reference reads
  "BARITY BADGES".
- **Pack README (`New folder\README.md`):** "Genuine 1x integer-grid RGBA pixel sprites, drawn programmatically from
  deliberately authored shapes." and "This is an integration-ready **working sprite draft**, not a pixel-perfect
  extraction of the earlier AI concept boards. The concept boards are visual references; those enlarged images cannot
  safely be sliced into 16x16 game sprites without repainting." and "The existing GDD has no established species/race
  rules: races here are artistic variation, not invented game mechanics."
- **Pack `ART_STATUS.md`:** "The `concept_references/` folder contains target art direction, **not ready-to-import
  sprites**." The project already slices boards of the same kind at 64 px, so this caveat is about 16 px sprites, but
  it is the pack author's stated position and the lead should know it before adopting the reference portraits.
- **V3 README:** "Designed after reviewing `Morfildor/BlacksmithInc` on 2026-10-08. Original PNG assets; no project
  code was modified." and "These are original pixel-grid graphic assets and valid importable PNGs, **not already
  integrated into the app**." Its generator writes to `/mnt/data/...` (`build_ui.py:4`).
- **Terminology mismatch to resolve:** the pipeline labels every imported sprite "hand-made" (`generate_assets.py:754`
  writes `"source": "handmade"`; `docs/PROGRESS.md` (line 61 at review time) says "Hand-made pixel art on every screen"; `CLAUDE.md` uses the
  same word), while the sources' own credentials say AI-generated and the two packs are script-drawn. For a paid store
  release the owner should decide how the art is described and confirm the usage terms of the generating account. That
  is an owner decision, not something this review can settle.

---

## Crop and data files produced

In `...\scratchpad\major_update\crops\`:

| File | Shows |
|---|---|
| `portraits_live_25_at_56dp_x3.png` | The 25 live portraits as drawn at 56 dp on a 3.0 phone, with the neighbour strips |
| `portraits_proposed_reslice_at_56dp_x3.png` | Same 25 from corrected cells |
| `portrait_slicing_spill_6x.png` | Four live files at 6x on grey: the defects up close |
| `device_evidence_portrait_strip_and_forge_52dp.png` | Real device crops: stray antler tips under a battlemage, and the 52 dp forge strip |
| `portraits_concept_vs_pack.png` | Concept busts against the pack's 16 px portraits |
| `refboard_v2_portraits_38_tiles.png` | The 30 class tiles and 8 town NPC tiles from the v2 reference board |
| `variety_hueshift_{guardian,ranger,duelist,battlemage,warden}.png`, `variety_hueshift_duelist_tightened.png` | Import-time hue rotation tests |
| `counter_scene_backdrop_candidates_ABC.png` | Atlas vs v2 reference vs hybrid panorama under the same foreground |
| `counter_scene_recommended_C_1080px.png`, `counter_scene_alt_B_1080px.png` | The recommended and alternative scene at phone resolution |
| `counter_scene_mockA_atlas_backdrop.png` | Atlas day and night backdrops with a stone-ledge counter |
| `counter_scene_mockB_tiles_and_mockC_mismatch.png` | Tiled forge given room; and what mixing sources looks like |
| `forge_scene_today_vs_given_room.png` | Replica of `ForgeScene` at 52, 100, 128 and 192 dp |
| `sprites_alpha_over_backgrounds.png` | Weapons and busts over dark, parchment and bright backdrops |
| `refboard_v2_forge_scene_3x.png`, `refboard_hybrid_forge_panorama_2x.png` | The two reference forge interiors with source-pixel rulers |
| `refboard_hybrid_cropC_corners_8x.png`, `refboard_hybrid_wide_clean_strip_2x.png` | Edge check of the recommended crop; the wide text-free strip |
| `atlas_adaptable_icons_and_bust.png` | Atlas home, purse and gear icons, an empty slot frame, the "Master Doran" bust |
| `atlas_forge_day_3x_nn_vs_bilinear.png`, `atlas_forge_night_3x_nn_vs_bilinear.png`, `atlas_market_street_3x_nn_vs_bilinear.png` | Atlas scenes, nearest vs bilinear |
| `atlas_npc_strip_8x.png`, `atlas_npcs_stalls_animals_4x.png`, `atlas_figures_keyed_4x.png` | Atlas figures raw and after keying on light and dark |
| `atlas_props_3x.png`, `atlas_tilesets_2x.png`, `atlas_ui_panels_3x.png`, `atlas_hud_4x.png`, `atlas_inventory_slots_4x.png`, `atlas_notifications_4x.png`, `atlas_menu_icons_5x.png`, `atlas_dialogue_box_5x.png`, `atlas_dialogue_portrait_6x.png` | Remaining atlas regions with rulers |

Beside the report: `atlas_boxes.json`, `sheet3_proposed_cells.json`, `v2ref_portrait_cells.json`, and the measurement
scripts (`*.py`) that produced every number above.

## What was not verified

- Nothing was run on a device or emulator. The scene mocks are Pillow composites at 1080 px, not Compose output.
- The `ForgeScene` renders are a Python replica of `Sprites.kt:213-274`; the 52 dp case matches the device screenshot,
  the larger heights are a prediction.
- The "art pixel" sizes are estimates from zoomed crops and an autocorrelation check, not exact grids (the sources have none).
- Hue variants were judged by eye on a subset of shifts; the counts in section 2 are estimates until a full curated pass.
- Atlas boxes for town square, castle, wilderness and dungeon and the notification toasts are good to about 2 px; `atlas_boxes.json` marks which boxes were corner-checked.
- The duplication table assumes uniform class and face draws, not the real hero generator.
