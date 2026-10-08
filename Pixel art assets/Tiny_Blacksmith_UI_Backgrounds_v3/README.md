# Tiny Blacksmith — Background & UI V3

Designed after reviewing `Morfildor/BlacksmithInc` on 2026-10-08. Original PNG assets; no project code was modified.

## Contents

- `drawable-nodpi/`: pixel-grid RGBA assets, 1× logical sizes.
- `previews/`: NN-upscaled annotated contact sheets; annotations are **not** inside sprites.
- `manifest.json`, `asset_inventory.csv`: dimensions, integration flags, IDs.
- `build_ui.py`: editable deterministic PIL generation source.

## Drop-in replacements (already referenced in current UI)

The six `icon_nav_*` graphics (40×40), six `icon_*` status graphics (24×24), two 96×32 `siege_wall` assets, and the 16×16 `tile_paper` can replace files of the same ID in `app/src/main/res/drawable-nodpi/`. Keep a backup or commit these on a separate branch first.

## New background assets — wiring required

The files starting with `bg_` are backgrounds, not existing `R.drawable` references. Wire them into `TitleScreen`/`RunEndScreen` or panel compositions if desired. The current `WorkshopScreen` deliberately shows one persistent `ForgeScene` across all tabs; switching to per-tab banners changes that behavior. Preserve text as native Compose components.

**Important**: Do **not** replace `tile_wall` or `tile_floor` alone with a different pixel unit. The existing `ForgeScene` derives its shared unit from `tile_wall.width / 16`; replacing only wall tiles would change object scaling. This pack consequently does not include drop-in replacements for those IDs.

## UI frames and buttons — wiring required

Frame sprites are 24×24 nine-slice assets with 8 px cap sizes. Split at x=8,16 and y=8,16; draw corners fixed, stretch edge middles and center. Buttons, slots and divider assets are purely decorative; labels, accessibility semantics, touch targets, resizing, and dynamic state stay in Compose. The `ui_frame_*` nine-slice images are not Android `.9.png` binaries: they need a nine-slice renderer.

## Design intent

- A+E hybrid: legible 1× silhouettes and handcrafted artisan motifs.
- Same 48-RGB-color palette as the prior V2 production pack; upper-left lighting.
- High-detail reserved for margins/corners, central content areas kept calm.
- All labels remain native Compose text, never baked into shipped sprites.
- Low-contrast ambient backgrounds, brighter accent icons for actionable controls.

## Caveat

These are original pixel-grid graphic assets and valid importable PNGs, **not already integrated into the app**. Preview on a phone and refine contrast/legibility after integration.
