# UI art revamp with Retro Diffusion (2026-10-17)

Owner request: remake the whole UI art set with Retro Diffusion (RD), not only the missing pieces, so it reads as one
polished, fun and professional set. This file is the feasibility report, the plan and the task ledger.

## Scope (61 pieces)

"UI art" here means the interface layer: icons, badges, markers, panels and paper, interface effects, the replay cue
marks and the launcher icon. Portraits, enemies, weapons, materials, blessings, relics, scenes and backgrounds are not
part of this pass (they are content art with their own WANTED lists).

| Group | IDs | File size | Shown today |
|---|---|---|---|
| Status | icon_gold, icon_energy, icon_integrity, icon_promised, icon_relic; icon_day, icon_militia, icon_reputation | 48 px | first five yes; last three no screen |
| Navigation and headings | icon_nav_forge, icon_nav_town, icon_nav_journal, icon_nav_market, icon_purse, icon_visitor, icon_trait_long_assault, icon_trait_many_breaches; icon_nav_gazette, icon_nav_home, icon_nav_legacy, icon_settings | 80 px (some 40) | first eight yes; last four no screen |
| Actions | icon_action_commission, _storage, _supplies; _hone, _donate, _legacy, _price, _scrap | 48 px | first three yes; last five no screen |
| Rarity | badge_common, _uncommon, _rare, _epic, _legendary, badge_flaw | 48 px | yes (weapon corner badge) |
| Fates | marker_dead, marker_retired | 16 px | yes |
| Panels | tile_paper, panel_gazette, panel_journal | 64 / 256 px | paper yes; both frames no screen yet (wired in this pass) |
| Effects | ember_0..3, fx_milestone_0..3 | 32 / 16 px | yes (forge scene, milestone) |
| Replay cues | fx_cue_guard, _protected, _recovered, _recovery_stopped, _preparing, _interrupted, _raised, _exposed, _poison, _burn, _wall | 16 px | no screen yet (delivered, waiting) |
| Launcher | ic_launcher foreground + background | 1024 master, mipmaps | yes |

The thirteen icons with no screen are remade too (owner: the whole set) but come last, so a budget stop leaves only
unused art behind.

## Feasibility

- Style test (6 images, $1.08): gold, energy and commission in `rd_pro__fantasy` and `rd_pro__default` at 48 px with
  background removal. Both read at small size far better than the current mixed set (the old action and nav icons
  turn to noise at 24 to 40 dp). Fantasy keeps more warmth and texture; chosen for the set. Evidence:
  `docs/ui_revamp/style_test.png`.
- Cost: every RD Pro image is $0.18 at any size up to 256 px; a single tile $0.06; a VFX strip $0.07. One take of
  the 61 pieces is about $11; two takes of the pieces on screen and one of the rest about $17. Balance at the start
  $39.77: feasible with room for retakes.
- Limits and how they are handled:
  - RD's largest output is 256 px: the launcher subject is drawn at 256 and enlarged by whole multiples to 1024
    (nearest neighbour, which pixel art survives), then placed in the adaptive-icon safe zone.
  - RD's smallest is 12 px: markers and cues are drawn at 16 px, their file size.
  - Panels must stretch over cards of any height: they are drawn as frames with every ornament on the border and a
    plain centre, and drawn in Compose as nine slices (corners fixed, edges and centre stretched).
  - `tile_paper` is drawn seamless (`tile_x`/`tile_y`); `paperBackground()` then tiles it instead of stretching it.
  - Effects: RD's VFX style returns its own frame count; four frames are picked from its sprite sheet. Embers can
    also be drawn as four stills from one prompt with seeds, whichever reads better.
- Pipeline: RD outputs replace the files in the owner packs (`Assets/Implemented/icons`, a new
  `Assets/Implemented/ui` for panels, paper and effects); `import_assets.py` copies them, and an owner-pack file wins over
  every older source. The old files stay in Git history. Nothing is hand-edited after RD except whole-pixel
  resizing and background removal.
- Risks: style drift between calls (same style, prompt frame and outline wording on every call; a contact sheet of
  the whole set is checked before import); tinted icons (none of these is tinted in code, checked); a piece that
  will not come out clean in two takes keeps its current art and is listed below.

## Plan

1. Style test, report → done when the owner has the comparison sheet.
2. Generate the on-screen pieces, then the rest, two takes where it matters → verify: contact sheet on dark and
   light grounds, every piece at its file size with alpha.
3. Import (`import_assets.py`), wire the Gazette and Journal frames and the seamless paper → verify:
   `:app:testDebugUnitTest`, emulator screenshots of Shop, Forge, Gazette, Journal, a weapon sheet.
4. Launcher icon → verify on the emulator launcher (round and square masks).
5. Bookkeeping: WANTED.txt statuses, CHANGELOG `[Unreleased]`, PROGRESS.

## Ledger

| Step | Status | Evidence |
|---|---|---|
| Style test | done | `docs/ui_revamp/style_test.png`, $1.08 |
| Size rule (owner, mid-pass: "not harsh on phone screens, cleanly readable, detail based on their size") | done | art drawn at about one pixel per displayed dp, thin dark-brown outline, no pure black or white; every icon call site goes through `PixelIcon` (whole-pixel `wholePixelDp` from the drawable's own width). `docs/ui_revamp/phone_scale_test.png` compares the old 48 px art at 1.375x nearest with the new 24 px art at a whole 3x |
| 36 icons, badges, markers | done | two takes for each piece on a screen, one for the rest; picks in the table below |
| Paper, Gazette and Journal frames | done | frames keyed and cut to 25 px nine-slices (`pixelFrame` in `ui/Sprites.kt`), on the Gazette dialog, the archive edition and the Notebook list; paper mirrored 2x2 to tile |
| Milestone burst | done | four frames of an `rd_animation__vfx` sheet, 32 to 16 px by the most opaque colour of each 2x2 block |
| Embers | not done | `rd_animation__vfx` failed twice (502); the old frames stay |
| Replay cues (11) | delivered, waiting | `Assets/Effects/fx_cue_*.png`; interrupted and exposed are weak (see the WANTED note) |
| Launcher | done | `Assets/AppIcon/`, mipmaps regenerated; `docs/ui_revamp/launcher_masks.png` (circle, squircle, rounded square, mdpi to xxhdpi) |
| Import and tests | done | `import_assets.py --accept-changed-sources` (new owner pack `ui`), `generate_assets.py`, `:app:testDebugUnitTest` passes |

Spent about $15 of Retro Diffusion credit on this pass (two failed frame takes, $0.72, were lost to background removal keeping the
flat centre; frames are now generated edge to edge and keyed locally). A parallel session drew on the same key at the same time.

### Picks

| ID | Size | Subject |
|---|---|---|
| icon_gold, icon_energy, icon_day, icon_integrity, icon_militia, icon_reputation, icon_promised, icon_relic (in that order) / day / integrity / militia / reputation / promised / relic | 24 | coin stack with an anvil mark / forge flame / sun over a hill / steel shield with an anvil / guard shield and spear / laurel and star / sealed tag / glowing chalice |
| badge_common … legendary, badge_flaw | 24 | iron stud, emerald in bronze, sapphire in a silver hexagon, amethyst in a gold star, sun gem in a gold sunburst; cracked stone |
| marker_dead / marker_retired | 16 | skull / laurel |
| icon_action_commission / storage / supplies / hone / donate / legacy / price / scrap | 32 | sealed scroll / banded chest / ore sack and tongs / whetstone / ribboned sword on a shield / golden anvil with laurel / price tag and coin / broken sword |
| icon_nav_forge / town / journal / market / gazette / home / legacy, icon_settings | 32 | anvil and hammer / walled town / leather journal and quill / market stall / newspaper / smithy cottage / blue banner / gear and hammer |
| icon_purse, icon_trait_long_assault, icon_trait_many_breaches | 40 | coin purse / hourglass and crossed swords / broken wall |
| icon_visitor | 48 | shop door ajar with lantern and bell |

## Second pass: content art (2026-10-17, evening)

Owner: "Remake any artwork that we didn't update today with Retro Diffusion", then "do the big artworks (day,
treasure, siege)" and "make sure the big artworks are polished and great looking". Same style as the UI set
(`rd_pro__fantasy`, thin dark-brown outline, no pure black or white); icons with one reference image (two of the new
48 px material icons, 28 colours, about 1.3 KB). Every piece keeps its file size, so no code changed, except the
title painting (RD's largest output is 256 px: drawn at 144x256 and stored at a whole 8x, 1152x2048) and the two
backdrops (stored at their native 256 px; `Backdrop` enlarges by whole pixels).

| Group | IDs | Takes | Notes |
|---|---|---|---|
| Backgrounds | bg_title_workshop_night, bg_counter_forge, bg_run_end_fallen_forge | 1, 2, 1 | counter retaken: the first came back letterboxed with white bars |
| Shop-day pictures | art_day_till, _sale_receipt, _siege_victory, _fallen_forge, _new_dawn | 1, 2, 2, 1, 1 | victory retaken (the sky filled the canvas: a vignette has to be described as an object, not a scene); receipt retaken (a letter on the seal) |
| Siege stage | siege_wall, siege_wall_damaged | 2 (one call of 2), 2 (one call of 2) | towers on the outer 16 px, as `SiegeStage` places the formation; the breach drawn with the intact wall as reference (`rd_pro__edit` needs 64 px and moved the wall) |
| Forge | anvil | 1 | 109x72 kept, the blade still rests on its face (emulator) |
| Faction portraits | faction_ashclaw_raider, _brute, faction_hollowbound_shade, _wraith, faction_embermaw_whelp, _drake | 4, 2, 1, 2, 1, 1 | new owner pack `Assets/Implemented/enemies`; the raider filled its 80 px canvas edge to edge three times, so it is drawn at 64 px and set on the 80 px canvas (no resampling); the wraith also stands for the lich and the drake for the ash dragon (`BESTIARY_FIGURES`) |
| Blessings | all 8 | 1 each | |
| Relics | all 5 | 1, 1, 1, 1, 2 | bellows retaken (background left in) |

Checks: no key colour left and sizes as before on every pick; edges: the walls run the full width and the fallen
forge and dawn pictures stand on a ground line at the bottom by design, a few grazes of 2 to 10 pixels remain (shade's
sword, whelp's wing, patronage banner, crucible, fallen forge and dawn sides), and the anvil's face touches the top row
(53 px; the old anvil left a margin); `import_assets.py --accept-changed-sources` then `generate_assets.py` change only these 30
drawables; `:app:testDebugUnitTest` 402/402; emulator (Pixel_10_Pro read-only, 1080x2400 at 420 dpi): title,
counter, Forge preview, shop-day sale, tally, fallen, tomorrow, siege lost. Spent about $7.70 of Retro Diffusion
credit (balance $9.08 to $1.34).

Kept as they were, and why: `art_day_rumour` (no screen), the faction alternates `faction_*_alt_*`, the furnaces,
tool rack, shelf, wall and floor tiles and embers (the `ForgeScene` that draws them is not called), `bg_forge_night`
and `splash_anvil` (not referenced), the 50 battle frames (animation, 16 px, out of budget), hero silhouettes and old
portrait sets (hero art belongs to the portrait work; the old sets are not shown).

Bestiary start (same evening, the last $1.26): the three engines and three great monsters of
`docs/ART_NEEDED_BESTIARY.md`, drawn at 96 px and set on 112 px canvases, imported as drawables under their own IDs
(`R.drawable.eng_battering_ram` … `boss_ash_dragon`, owner pack `enemies`) and not wired yet (the replay code maps
bestiary IDs to figures). The hill giant retake came out worse and was not used. Balance $0.08.

For the owner: `blessing_runic_insight` draws rune glyphs (the brief says no letters; runes read as decoration);
the victory gatehouse fills about half its canvas and reads smaller than the other banners; the intact and breached
walls differ slightly in tone (never shown together); the anvil's face touches the top of its canvas; the raider is
a 64 px figure on the 80 px canvas, so it reads about 80% the size of the shade beside it; the banners that use the
title painting (guild's party, "Beyond the door", the town's thanks) need `NIGHT_FOCUS` of about 0.68 to frame the smith
at the door (the visual pass session owns that constant and was told).
