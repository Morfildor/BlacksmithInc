# Tiny Blacksmith — pixel-art asset brief (for outsourcing)

Tiny Blacksmith is a portrait-only Android game: a small fantasy blacksmith shop whose weapons are bought by
autonomous heroes who fight monsters and defend the town. The player only forges, prices and reads the news.
All art is decorative pixel art drawn over a Compose UI; the UI draws every piece of text itself.

Procedural placeholders already exist for every sprite below (see `docs/ART_MANIFEST.md`), so the game runs
without you. Your work replaces those placeholders one for one. Keep the IDs and sizes exactly as listed, and the
integration is a file drop.

## 1. Technical rules (apply to every sprite)

| Rule | Value |
|---|---|
| Format | PNG, RGBA, 1× (no pre-scaling), transparent background, file name = sprite ID + `.png` |
| Pixel grid | Strict integer grid; the app scales by whole numbers with nearest-neighbour (16 px sprites show at 3×, the forge scene at 3–4×) |
| Light source | Upper-left on everything; 1 px dark outline (`#1A1210`) on all objects, no outline on tiles |
| Palette | One shared palette for the whole set, ≤ 48 colours. Start from the current palette in section 7; you may replace it, but every sprite must use the same final palette |
| Text | Never draw letters or numbers into a sprite (labels, prices, rarity names are drawn by the UI) |
| Anchors | As listed per sprite; keep the subject inside the canvas with ≥ 1 px transparent margin unless the sprite is a tile |
| Animation | Deliver frames as separate files `id_0.png`, `id_1.png`, …; frame time is in the table |
| Source | Please also deliver the editable source (Aseprite `.ase` or layered PNG) and a contact sheet at 4× |

Silhouettes must read at 1× on a phone: a sword, spear and dagger need to be distinguishable at 16 px without the
colour cue, because colour is used for the material.

## 2. Priority 1 — replaces what the current build shows (needed first)

### 2.1 Forge scene (shown at the top of every screen; logical stage is 96 × 48 px, tiles extend to the edges)

| ID | Size | Anchor | Notes |
|---|---|---|---|
| `tile_wall` | 16×16 | tile | Stone or timber wall, seamless both axes, low contrast so UI text over it stays readable |
| `tile_floor` | 16×8 | tile | Floor strip under the stage, seamless horizontally |
| `furnace_cold` | 24×24 | bottom-left | Furnace with dark mouth |
| `furnace_warm` | 24×24 | bottom-left | Same furnace, embers glowing |
| `furnace_hot` | 24×24 | bottom-left | Same furnace, roaring; this is the "player has energy" state |
| `anvil` | 24×12 | bottom-left | Anvil on a stump |
| `tool_rack` | 12×24 | top-left | Wall rack with hammer, tongs, file |
| `shelf` | 32×12 | bottom-left | Display shelf; weapons are drawn on top by the app |
| `ember_0` … `ember_3` | 8×8 | top-left | 4 looping frames, 180 ms each, rising sparks above the furnace mouth (currently 3 frames; 4 are fine) |

### 2.2 Weapons (16×16, anchor bottom-center, shown in lists, result card, shelves, hero cards)

Six families × six core metals = 36 sprites. Two ways to deliver; either is fine, say which:

- **Minimum:** 6 family sprites using exactly two "metal" colours (lit face, shaded face). We recolour per core
  with the ramps in section 7. IDs: `weapon_sword_iron`, `weapon_axe_iron`, `weapon_spear_iron`, `weapon_bow_iron`,
  `weapon_dagger_iron`, `weapon_staff_iron`.
- **Full:** all 36 drawn by hand, IDs `weapon_<family>_<core>` with family ∈ sword, axe, spear, bow, dagger, staff
  and core ∈ iron, bronze, silver, obsidian, starsteel, moonsteel. Higher cores may add ornament (fuller, gems, runes).

Family feel: sword = straight arming sword; axe = bearded one-hander; spear = leaf-blade on a shaft; bow = recurve
with string; dagger = short, clearly shorter than the sword; staff = wooden shaft with a metal head (currently a
forked crescent, which reads as a tuning fork at 16 px; please improve).

### 2.3 Element overlays (16×16, anchor top-left, drawn on top of the weapon at ~60 % opacity)

`overlay_fire`, `overlay_frost`, `overlay_storm`, `overlay_grave`, `overlay_verdant`, `overlay_sun`. Mostly
transparent; a few glowing pixels/wisps around the blade edge that read as the element over any metal colour.
Grave (green-grey, lowest contrast today) needs the most work.

### 2.4 Rarity badges (8×8, drawn in the weapon's corner)

`badge_common` (grey), `badge_uncommon` (green), `badge_rare` (blue), `badge_epic` (purple), `badge_legendary`
(gold), `badge_flaw` (red crack). Shape must differ as well as colour (colour-blind players).

### 2.5 Hero portraits (64×64 opaque tiles, shown at 44 and 56 dp in Town, larger at the counter and on sheets)

The game draws heroes from one set of 20: `portrait_hero_01` to `portrait_hero_20` (base face) and
`portrait_hero_01_up` to `portrait_hero_20_up` (the same hero after earning the upgraded look). Faces per class:
guardian 5 (01, 06, 09, 11, 16), ranger 4 (02, 07, 12, 17), duelist 4 (03, 10, 13, 18), battlemage 4 (04, 08, 14, 19),
warden 3 (05, 15, 20). A class with fewer faces repeats them sooner; more faces for warden first.

Provenance: the set was supplied by the owner as the folder `Assets/Implemented/heroes/` (40 PNGs, `manifest.json`, `README.txt`).
Its README describes the portraits as generated: heroes 01-10 as 1254 px renders, heroes 11-20 as native 64 px
tiles. They are AI-generated images, imported and resized by `tools/pixelart/import_assets.py`; nothing is hand-drawn
or retouched. The folder is not in Git (large sources); only the 64 px imports are. To re-import:

```
python tools/pixelart/import_assets.py --heroes "<path>/Assets/Implemented/heroes"   # default <repo root>/Assets/Implemented/heroes, or TINY_BLACKSMITH_HEROES
python tools/pixelart/generate_assets.py
```

Without the folder the importer says so, skips the step and keeps the committed files. Two runs give byte-identical
output.

What the import does, and why: the 64 px files are taken pixel for pixel. The 1254 px renders sit on pixel grids of
about 117 to 290 cells, so no output size reproduces them cell for cell; they are reduced to 64 px (Lanczos) and to 96
colours without dithering (the native tiles use 81 to 96). 128 and 256 px were compared on the emulator at 44, 56, 85
and 112 dp: sharper on their own, but beside the native 64 px heroes they read as a second, finer art style. At 64 px
all twenty share one pixel size and are drawn nearest-neighbour. Heroes 11-15 are framed from further away, so their
faces are small at 44 dp: they also have `_sm` tiles for small list rows, a 48 px square of the same tile around the
head (not resampled).

For new portraits: a 64×64 opaque tile with a dark background, head and shoulders, the face at least a third of the
tile wide (as in heroes 16-20), and an upgraded state that differs in silhouette or colour, not only in armour detail
(18 and 04 read at a glance; 12 and 15 barely differ at list size).

The 25 older busts (`portrait_<class>_<0..4>`, concept sheet 3) and the 25 second-set tiles
(`portrait_v2_<class>_<1..5>`) stay in the repository and are not used for heroes; saved keys of either set map onto
the hero set in `ui/Sprites.kt`.

### 2.6 Faction monsters (16×16, anchor bottom-center, side view facing left)

| Faction | Grunt | Elite |
|---|---|---|
| Ashclaw Raiders (beast-men) | `faction_ashclaw_raider` | `faction_ashclaw_brute` |
| Hollowbound (undead) | `faction_hollowbound_shade` | `faction_hollowbound_wraith` |
| Embermaw Brood (fire drakes) | `faction_embermaw_whelp` | `faction_embermaw_drake` |

Elite must differ in silhouette from the grunt (not only colour or a horn).

## 3. Priority 2 — launch content icons

### 3.1 Materials (12×12, shown in the forge pickers and supplier)

Cores (ingots): `material_iron`, `material_bronze`, `material_silver`, `material_obsidian`, `material_starsteel`,
`material_moonsteel`.
Augments: `material_ember_resin` (orange resin lump), `material_frost_bloom` (ice flower), `material_stormglass`
(purple shard), `material_grave_dust` (grey-green pouch/skull), `material_verdant_sap` (green droplet),
`material_sun_ash` (gold ash).
Catalysts: `material_binding_salt` (white crystals), `material_runestone_shard` (carved stone), `material_dragon_oil`
(red vial), `material_void_ink` (black-purple inkpot).

### 3.2 Blessings (12×12, chosen after a survived siege)

`blessing_forgefire` (flame), `blessing_tireless_hands` (hammer), `blessing_merchants_favor` (coins),
`blessing_runic_insight` (eye/rune), `blessing_stalwart_town` (shield/wall), `blessing_hunters_edge` (arrow),
`blessing_lucky_alloy` (clover/ingot), `blessing_guild_patronage` (banner).

### 3.3 UI chrome

| ID | Size | Notes |
|---|---|---|
| `panel_gazette` | 24×24 | Nine-patch newspaper frame: 8 px border, plain centre that stretches |
| `panel_journal` | 24×24 | Nine-patch leather journal frame, same border rule |
| `tile_paper` | 16×16 | Seamless paper texture for the Gazette background |
| `icon_nav_forge`, `icon_nav_market`, `icon_nav_town`, `icon_nav_journal`, `icon_nav_gazette`, `icon_nav_legacy` | 12×12 | Bottom navigation icons; must read in one colour too (we tint them) |
| `icon_energy`, `icon_gold`, `icon_integrity`, `icon_militia`, `icon_reputation`, `icon_day` | 8×8 | Top status strip |

### 3.4 Signature weapons (16×16, anchor bottom-center)

24 named signature weapons at launch (12 exist in data now). Each is a recoloured/ornamented variant of its family
sprite with a distinctive detail (glowing fuller, wrapped grip, runes). IDs `weapon_sig_<signature_id>`; the current
12 signature IDs: `dawnbrand`, `winterwake`, `tempest_edge`, `ashen_vow` (swords); `hearthcleaver`, `glacier_maul`,
`thunderhead`, `emberfall` (axes); `stormsong`, `frostwhisper`, `cinder_arc`, `galewood` (bows). The remaining 12
(spear, dagger, staff) will be named by us; draw them once the IDs are confirmed.

## 4. Priority 3 — animation and polish

| Set | Frames | Size | Notes |
|---|---|---|---|
| Hero attack (per class, 5 classes) | idle 2 + attack 2, 120 ms | 16×16 side view, facing right | Used in the siege replay; weapon is drawn by the app, so draw the hand empty |
| Monster attack (per faction grunt and elite, 6 sprites) | idle 2 + attack 2 + hit 1, 120 ms | 16×16 facing left | Same stage as the heroes |
| Siege stage | 1 | 96×32 tile strip | Town wall / gate, with a damaged variant (`siege_wall`, `siege_wall_damaged`) |
| Milestone burst | 4, 100 ms | 16×16 | Sparkle used for level-up, legend recorded, signature discovered (`fx_milestone_0..3`) |
| Hero marker | 2 | 8×8 | `marker_dead` (grave), `marker_retired` (laurel) for the adventurer list |
| Modular portraits (optional) | — | 16×16 layers | 3 skin tones × 6 hair × 5 class headgear × 5 class outfits, aligned to the same bust; replaces the five fixed portraits if delivered |

## 5. Store and launch art (not pixel sprites, but needed for release)

| Asset | Size | Notes |
|---|---|---|
| App icon | 512×512 PNG + adaptive foreground/background layers (108 dp safe zone) | Anvil + ember motif; pixel style scaled cleanly |
| Google Play feature graphic | 1024×500 | Forge scene with a hero at the counter |
| Title screen key art | 270×150 pixels drawn, delivered at 1× (shown at 4×) | Night-time workshop exterior or interior; leaves the lower third calm for buttons |
| Phone screenshots frames (optional) | 1080×1920 | Decorative frame/background for store screenshots |

## 6. Counts

| Section | Sprites / frames |
|---|---|
| Forge scene | 8 statics + 4 ember frames |
| Weapons | 6 (minimum) or 36 (full) |
| Element overlays | 6 |
| Rarity badges | 6 |
| Portraits | 5 |
| Faction monsters | 6 |
| Materials | 16 |
| Blessings | 8 |
| UI chrome | 3 panels/tiles + 12 icons |
| Signature weapons | 12 now, 24 at launch |
| Animations (priority 3) | 20 hero frames + 30 monster frames + 2 stage + 4 burst + 2 markers |
| Store art | 4 |

Priority 1 total: about 49 sprites. Everything in sections 2 and 3: about 100.

## 7. Current palette (RGB), free to revise as one coherent set

Outline `26,18,16` · dark wood `43,35,32` / `74,59,51` / `107,75,50` / `140,98,57` · bone white `232,228,218` ·
stone `85,89,93` / `125,129,134` / `176,180,184` · fire `179,48,26` / `226,96,31` / `255,193,77` / `255,240,160` ·
skin `224,176,138` · leather `122,74,42` · cloth blue `58,90,138` · cloth red `160,48,48` · gold `216,160,48`.

Metal ramps (lit / shaded): iron `138,143,148` / `92,97,102` · bronze `199,136,58` / `138,90,34` · silver
`217,221,227` / `154,163,173` · obsidian `120,96,140` / `66,50,84` · starsteel `170,210,236` / `92,134,184` ·
moonsteel `206,200,240` / `134,126,190`.

Element accents: fire `226,96,31` · frost `127,216,255` / `58,160,216` · storm `180,140,255` / `106,75,214` ·
grave `178,206,150` / `80,104,84` · verdant `90,160,90` / `156,222,120` · sun `255,193,77` / `255,240,160`.

## 8. How we will integrate

Deliver into the `Pixel art assets/` folder, in either form:

- **Loose files named by ID**: `weapon_staff_iron.png`, `portrait_warden_2.png`, … Each is cropped to its content,
  fitted to that ID's size from the tables above and imported on its own. This is the simplest route.
- **Numbered sheets** (`…-6.png`, `…-7.png`): only after we agree the cell layout, because sheets are sliced by
  fixed rectangles; a sheet with no registered layout is skipped with a message.
- **Weapon master sheet** (file name starting `Weapons master`): six family panels, each 8 levels (+1..+8) by
  7 rows (base, fire, frost, storm, grave, verdant, sun) on a flat dark background. It is keyed and sliced into
  336 `weapon_<family>_<row>_<level>` sprites; a re-export must keep the same panel/grid proportions (the slicer
  scales with the image width). This sheet replaces the per-core weapon icons and the element overlays: the level
  column shows the core tier (+1 iron … +6 moonsteel) plus +1 for epic and +2 for legendary rolls.

`python tools/pixelart/import_assets.py` then writes the drawables and records the IDs as imported (the tooling still labels them
"hand-made" in its output and manifest until task T2.4 renames the label), and the placeholder generator leaves those IDs alone. Any sprite you do not deliver keeps its placeholder, so partial
deliveries are useful. Questions about an ID or size: ask before drawing, the sizes are load-bearing.

## 9. Art sources and provenance

The imported art has three kinds of source. The concept sheets (the five numbered sheets, the `Weapons master` sheet and the reference boards beside the artist pack) are AI-generated: each source file carries an embedded Content Credentials (C2PA) manifest naming ChatGPT / OpenAI as the generator (asset review `docs/major_update_evidence/04_assets_content.md`, section 9). The artist's 1x production pack and the UI backgrounds pack are script-drawn from authored shapes, by their own READMEs; the importer takes 58 sprites from the first. `generate_assets.py` draws programmatic placeholders for IDs with no imported art. No evidence of manual pixel editing exists, and the project rule is never to hand-edit PNGs. The slicer resamples, so the shipped drawables carry no credentials; source files are never re-saved in place, so their embedded credentials are preserved. Art-origin metadata is not a secret and not a security finding. No licence or attribution text exists under `Pixel art assets/`; how the art is described on a paid store listing and the usage terms of the generating account are owner decisions (plan section 5.7, 10.4). "Imported art" is the neutral name for the category the tooling still prints as "hand-made".
