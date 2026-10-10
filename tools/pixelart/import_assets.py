"""
Import the sprite sheets in `Pixel art assets/` into app/src/main/res/drawable-nodpi/.

Sources are only ever opened for reading: nothing under `Pixel art assets/` is modified, re-saved, moved or renamed
(re-saving would strip the Content Credentials embedded in the sheets). Every crop comes from a constant below or from
a checked-in file in `cells/`, so a run is reproducible: the same sources give byte-identical outputs (Pillow and
numpy pinned in `requirements.txt`). What each source is (generated concept sheet, script-drawn pack) is recorded per
sprite in `overrides.json` as `sourceKind`, with the SHA-256 of the file it was cut from.

Each sheet is sliced with explicit cell rectangles (the sheets are not on a strict grid, so detection is not
reliable), tight-cropped by alpha inside the cell, downscaled to the target size and written under the sprite ID
that `ui/Sprites.kt` and `ForgeScene` load. Every imported ID is recorded in `overrides.json`; `generate_assets.py`
then leaves those files alone (placeholders are only generated for IDs with no imported source).

Usage: python tools/pixelart/import_assets.py            # imports every sheet with a known layout
       python tools/pixelart/import_assets.py --sheet 2   # one sheet
Sheets are matched by the trailing "-N" in the file name (…-1.png = scene, -2 weapons, -3 portraits, -4 factions,
-5 materials/blessings/UI). Add a layout below when a new sheet arrives; a sheet without a layout is skipped with a
message. A loose file named exactly `<sprite_id>.png` (e.g. `weapon_staff_iron.png`) is imported on its own: it is
tight-cropped, fitted to that ID's box and recorded as an override too.

The weapon master sheet (a file whose name starts with "Weapons master", any or no extension; 6 family panels of
8 levels x 7 rows: base + fire, frost, storm, grave, verdant, sun) is keyed against its flat dark background and
sliced into `weapon_<family>_<row>_<level>` (336 sprites, padded to 56 px, never enlarged). It also regenerates
`ui/WeaponArt.kt`, the drawable lookup table the UI uses for every weapon.

A production pack (a subfolder holding `drawable-nodpi/*.png` plus `manifest.json`; both packs are drawn by their own
scripts) is copied verbatim for the IDs in PACK_ALLOW only: 1x sprites the concept sheets do not provide (battle
animation frames, siege wall, milestone burst, hero markers, two stand-alone 270x150 screens).

Sheet 3 (25 portraits) is cut on the measured cells in `cells/sheet3_proposed_cells.json`. Each portrait must pass
the self-checks or the import fails: one vertical and one horizontal run of content, no pixel in the outer 1 px ring.
Its content box is recorded so a bust can stand on a line. Flat boards (`flat_sources`: the counter backdrop, the
second face set, the atlas crops) are matched by file name, also inside a pack's `concept_references/`. The atlas is
optional: pass `--atlas PATH` when it is not in the folder; without it its earlier sprites are kept. The portrait
lookup `ui/PortraitArt.kt` is regenerated on every run.

The hero portrait set (20 heroes, a base and an upgraded face each) is read from a folder that is not in Git:
`--heroes PATH`, else the environment variable TINY_BLACKSMITH_HEROES, else `<repo root>/Assets/Implemented/heroes` (`manifest.json`
plus `hero_XX_<class>_<base|upgraded>.png`). Each file becomes the opaque tile `portrait_hero_XX` or
`portrait_hero_XX_up`: a 64 px file is taken as it is, a larger render is reduced to HERO_BOX and HERO_COLOURS; the
heroes in HERO_SMALL also get a tighter `_sm` tile for small list rows. When the folder is
absent the step is skipped with a message and the committed hero drawables and their records are kept.

An ID produced by two sources in one run is an error naming both. A source whose SHA-256 differs from the one its
sprites were last cut from stops the import until `--accept-changed-sources` is passed. IDs imported earlier but no
longer produced are pruned from the drawable folder.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "Pixel art assets"
OUT = ROOT / "app/src/main/res/drawable-nodpi"
OVERRIDES = Path(__file__).with_name("overrides.json")
CELLS = Path(__file__).with_name("cells")
CONTACT = ROOT / "docs/art_contact.png"
PORTRAIT_ART_KT = ROOT / "app/src/main/java/com/example/blacksmithproject/ui/PortraitArt.kt"

# Per-pack allowlist: ID prefixes taken from each production pack (an exact ID is its own prefix). A pack that is not
# listed is skipped. There is no take-everything switch: most pack IDs collide with sheet-derived sprites.
PACK_ALLOW = {
    # 1x sprites the concept sheets do not provide (the sheets cover the rest at higher fidelity).
    "New folder": ("hero_", "monster_", "siege_wall", "fx_milestone_", "marker_"),
    # Stand-alone 270x150 screens only; the rest of this pack collides with live IDs (siege_wall, 12 icons, tile_paper).
    "Tiny_Blacksmith_UI_Backgrounds_v3": ("bg_title_workshop_night", "bg_run_end_fallen_forge"),
}

# Where each source comes from (docs/major_update_evidence/04_assets_content.md, section 9): the numbered concept
# sheets, the weapon master sheet, the atlas and both concept references carry embedded Content Credentials naming an
# image generator; the two packs are drawn by their own scripts. Nothing here is recorded as manually authored.
KIND_AI = "ai_generated_sheet"
KIND_PACK = "script_drawn_pack"
KIND_LOOSE = "loose_file"  # a single <id>.png dropped into the folder: origin not recorded
KIND_AI_PORTRAIT = "ai_generated_portrait"  # the owner-supplied hero set; its README calls the portraits generated

ATLAS_NAME = "Tiny Blacksmith RPG Asset Atlas.png"
PORTRAIT_BOX = 64
# The reference-board cells are measured to about 2 px and some include the gutter between tiles: tiles are cut this far inside.
TILE_INSET = 3
# The second face set stays out of the appearance pool until it has been verified on a device (PortraitArt.kt).
SECOND_SET_ENABLED = False
# The hero set lives outside Git (see the module text). Its tiles are HERO_BOX px: ten of the heroes are supplied at that size.
HEROES_DIR = Path(os.environ.get("TINY_BLACKSMITH_HEROES") or ROOT / "Assets/Implemented/heroes")
HEROES_SHEET = "Assets/Implemented/heroes"
# The owner's delivered pieces that the game uses (Assets/Implemented/<name>): taken verbatim from the drawable-nodpi
# subfolder, by ID prefix. New deliveries stay in Assets/<kind>/ until they are wired in, then move here.
OWNER_ROOT = ROOT / "Assets/Implemented"
OWNER_PACKS = {
    "day_art": ("art_day_",),
    "icons": ("icon_",),
    "materials": ("material_",),
    "blessings": ("blessing_",),
}
# An ID the owner delivered wins over every older source (concept sheets, atlas, packs) that also draws it.
OWNED_IDS = {p.stem for name in OWNER_PACKS for p in (OWNER_ROOT / name / "drawable-nodpi").glob("*.png") if p.stem.startswith(OWNER_PACKS[name])}
HERO_BOX = 64
# The larger renders (1254 px, on pixel grids of about 117 to 290 cells, so no size reproduces them cell for cell) are
# reduced to the same box and to the palette size of the native files (81 to 96 colours), without dithering. Compared
# on the emulator against 128 and 256 px at 44, 56, 85 and 112 dp: the larger sizes are sharper on their own but read
# as a second, finer art style beside the native 64 px heroes; at 64 px all twenty share one pixel size.
HERO_COLOURS = 96
# Heroes 11-15 are drawn from further away than the rest: on a 44 dp list row the face is about 35 px across and hard to
# read (15 most of all). They also get a `_sm` tile for small list rows: this HERO_SMALL_BOX square of the 64 px tile,
# cut at the given left and top so the head stays whole; no resampling. The other heroes need none.
HERO_SMALL_BOX = 48
HERO_SMALL = {"11": (0, 0), "12": (12, 0), "13": (3, 0), "14": (10, 0), "15": (0, 2)}

# Logical scene unit: the forge scene is laid out in 96x48 "scene pixels"; imported scene pieces are exported at
# SCENE_UNIT bitmap pixels per scene pixel so ForgeScene can keep positioning in scene coordinates.
SCENE_UNIT = 4

# ---------------------------------------------------------------------------------------------------------------
# Cell = (x0, y0, x1, y1) on the sheet; tight-cropped by alpha. `size` = max output dimension (aspect preserved)
# unless `scale` (fixed scale factor) is given, or `box` (exact output w,h with padding) is given.
# ---------------------------------------------------------------------------------------------------------------

def grid(x0, y0, x1, y1, cols, rows):
    cw, ch = (x1 - x0) / cols, (y1 - y0) / rows
    return [(int(x0 + c * cw), int(y0 + r * ch), int(x0 + (c + 1) * cw), int(y0 + (r + 1) * ch)) for r in range(rows) for c in range(cols)]


CLASSES = ["guardian", "ranger", "duelist", "battlemage", "warden"]
CORES = ["iron", "bronze", "silver", "obsidian", "starsteel", "moonsteel"]
WEAPON_ROWS = ["base", "fire", "frost", "storm", "grave", "verdant", "sun"]
WEAPON_LEVELS = 8
WEAPON_BOX = 56
WEAPON_ART_KT = ROOT / "app/src/main/java/com/example/blacksmithproject/ui/WeaponArt.kt"
# Weapon master sheet geometry, measured on the 1672x941 export: panel origin per family, grid at +97/+52, 55x57 cells.
MASTER_WIDTH = 1672
MASTER_PANELS = {"sword": (9, 10), "axe": (565, 10), "spear": (1119, 10), "bow": (9, 475), "dagger": (565, 475), "staff": (1119, 475)}
MASTER_GRID = (97, 52, 55, 57)
FAMILIES = ["sword", "axe", "spear", "bow", "dagger", "staff"]
ELEMENTS = ["fire", "frost", "storm", "grave", "verdant", "sun"]
AUGMENTS = ["ember_resin", "frost_bloom", "stormglass", "grave_dust", "verdant_sap", "sun_ash"]
CATALYSTS = ["binding_salt", "runestone_shard", "dragon_oil", "void_ink"]
BLESSINGS = ["forgefire", "tireless_hands", "merchants_favor", "runic_insight", "stalwart_town", "hunters_edge", "lucky_alloy", "guild_patronage"]
NAV = ["forge", "market", "town", "journal", "gazette", "legacy"]
STATUS = ["energy", "gold", "integrity", "militia", "reputation", "day"]


def layout_1():  # forge scene, 1448x1086
    cells = {
        "tile_wall": dict(cell=(48, 146, 309, 403), box=(16 * SCENE_UNIT, 16 * SCENE_UNIT), tile=True),
        "tile_floor": dict(cell=(21, 443, 337, 571), box=(16 * SCENE_UNIT, 8 * SCENE_UNIT), tile=True),
        "furnace_cold": dict(cell=(339, 116, 700, 575), scene_h=24),
        "furnace_warm": dict(cell=(700, 116, 1060, 575), scene_h=24),
        "furnace_hot": dict(cell=(1060, 116, 1436, 575), scene_h=24),
        "anvil": dict(cell=(12, 638, 532, 988), scene_h=18),
        "tool_rack": dict(cell=(540, 575, 800, 990), scene_h=22),
        "shelf": dict(cell=(813, 607, 1444, 937), scene_h=16),
        "ember_0": dict(cell=(925, 940, 1000, 1020), box=(8 * SCENE_UNIT, 8 * SCENE_UNIT)),
        "ember_1": dict(cell=(1020, 930, 1105, 1020), box=(8 * SCENE_UNIT, 8 * SCENE_UNIT)),
        "ember_2": dict(cell=(1120, 920, 1215, 1020), box=(8 * SCENE_UNIT, 8 * SCENE_UNIT)),
        "ember_3": dict(cell=(1240, 910, 1360, 1020), box=(8 * SCENE_UNIT, 8 * SCENE_UNIT)),
    }
    return cells


def layout_2():  # rarity badges, 1448x1086 (its weapon and overlay cells are superseded by the weapon master sheet)
    cells = {}
    badge_cells = [(127, 870, 278, 1040), (325, 873, 483, 1040), (526, 872, 686, 1040), (735, 876, 896, 1050), (946, 874, 1117, 1040), (1161, 873, 1323, 1058)]
    for rid, cell in zip(["common", "uncommon", "rare", "epic", "legendary", "flaw"], badge_cells):
        cells[f"badge_{rid}"] = dict(cell=cell, box=(24, 24))
    return cells


def layout_3():  # 25 portraits: one row per class, five variants, 1254x1254
    # The rows are not on a uniform grid: cells are measured (alpha bands, minimum-occupancy column cuts).
    measured = json.loads((CELLS / "sheet3_proposed_cells.json").read_text())
    return {f"portrait_{cls}_{v}": dict(cell=tuple(measured[f"portrait_{cls}_{v}"]), box=(PORTRAIT_BOX, PORTRAIT_BOX), portrait=True)
            for cls in CLASSES for v in range(5)}


def layout_4():  # factions: column per faction; rows grunt, elite, two extras; 1448x1086
    cols = {"ashclaw": ("raider", "brute", (47, 516, 4, 492)), "hollowbound": ("shade", "wraith", (516, 926, 501, 970)),
            "embermaw": ("whelp", "drake", (1024, 1392, 957, 1442))}
    cells = {
        "faction_ashclaw_raider": dict(cell=(47, 65, 447, 400), box=(80, 80)),
        "faction_hollowbound_shade": dict(cell=(516, 83, 926, 400), box=(80, 80)),
        "faction_embermaw_whelp": dict(cell=(1024, 143, 1392, 400), box=(80, 80)),
        "faction_ashclaw_brute": dict(cell=(4, 413, 492, 822), box=(112, 112)),
        "faction_hollowbound_wraith": dict(cell=(501, 421, 970, 827), box=(112, 112)),
        "faction_embermaw_drake": dict(cell=(957, 409, 1442, 837), box=(112, 112)),
        "faction_ashclaw_alt_0": dict(cell=(3, 832, 250, 1052), box=(64, 64)),
        "faction_ashclaw_alt_1": dict(cell=(250, 832, 494, 1052), box=(64, 64)),
        "faction_hollowbound_alt_0": dict(cell=(518, 825, 754, 1052), box=(64, 64)),
        "faction_hollowbound_alt_1": dict(cell=(726, 828, 958, 1052), box=(64, 64)),
        "faction_embermaw_alt_0": dict(cell=(967, 857, 1205, 1052), box=(64, 64)),
        "faction_embermaw_alt_1": dict(cell=(1205, 857, 1448, 1052), box=(64, 64)),
    }
    return cells


def layout_5():  # materials, blessings, panels, nav + status icons, 1448x1086
    cells = {}
    row1 = grid(3, 26, 1441, 200, 8, 1)
    row2 = grid(3, 200, 1441, 395, 8, 1)
    for i, core in enumerate(CORES):
        cells[f"material_{core}"] = dict(cell=row1[i], box=(48, 48))
    cells["material_ember_resin"] = dict(cell=row1[6], box=(48, 48))
    cells["material_frost_bloom"] = dict(cell=row1[7], box=(48, 48))
    for i, mid in enumerate(["stormglass", "grave_dust", "verdant_sap", "sun_ash"] + CATALYSTS):
        cells[f"material_{mid}"] = dict(cell=row2[i], box=(48, 48))
    for i, b in enumerate(BLESSINGS):
        cells[f"blessing_{b}"] = dict(cell=grid(3, 395, 1441, 590, 8, 1)[i], box=(48, 48))
    cells["panel_gazette"] = dict(cell=(10, 590, 612, 882), size=256)
    cells["panel_journal"] = dict(cell=(640, 590, 1100, 882), size=256)
    cells["tile_paper"] = dict(cell=(1125, 595, 1441, 880), box=(64, 64), tile=True)
    for i, n in enumerate(NAV):
        cells[f"icon_nav_{n}"] = dict(cell=grid(30, 890, 1010, 1050, 6, 1)[i], box=(40, 40))
    status_cells = grid(1140, 888, 1402, 1052, 3, 2)
    for i, s in enumerate(STATUS):
        cells[f"icon_{s}"] = dict(cell=status_cells[i], box=(24, 24))
    return cells


LAYOUTS = {1: layout_1, 2: layout_2, 3: layout_3, 4: layout_4, 5: layout_5}

# Keyed cut-outs of the second face set that passed the look-at-each-one review (see docs/ART_MANIFEST.md). A tile
# that is not listed here, or that fails the self-checks, is dropped, never repaired.
V2_CUT_REVIEWED: tuple = ()


def flat_sources() -> dict:
    """Flat boards matched by file name: sprite ID -> dict(cell, mode[, box]). Modes: see import_flat."""
    v2 = json.loads((CELLS / "v2ref_portrait_cells.json").read_text())
    atlas = json.loads((CELLS / "atlas_boxes.json").read_text())
    second = {}
    for cls in CLASSES:
        for n in range(1, 6):  # column 0 of each class has the class label baked across the top
            cell = tuple(v2[f"v2ref_{cls}_{n}"])
            second[f"portrait_v2_{cls}_{n}"] = dict(cell=cell, mode="tile", box=(PORTRAIT_BOX, PORTRAIT_BOX))
            second[f"portrait_v2_{cls}_{n}_cut"] = dict(cell=cell, mode="cut")
    return {
        "approved_hybrid_direction.png": {"bg_counter_forge": dict(cell=(262, 135, 802, 405), mode="opaque")},
        "v2_concept_reference.png": second,
        ATLAS_NAME: {
            "icon_nav_home": dict(cell=tuple(atlas["menu_icon_home"]), mode="icon", box=(40, 40)),
            "icon_purse": dict(cell=tuple(atlas["menu_icon_purse"]), mode="icon", box=(40, 40)),
            "icon_settings": dict(cell=tuple(atlas["menu_icon_gear"]), mode="icon", box=(40, 40)),
            "bg_forge_night": dict(cell=tuple(atlas["forge_night"]["box"]), mode="opaque"),
            # town_square and wilderness_camp were looked at and left out: 166-210 px wide and soft, and their boxes
            # (not corner-verified) leave a lighter 1 px line along the top edge.
        },
    }

# ---------------------------------------------------------------------------------------------------------------


def tight_crop(im: Image.Image, cell, threshold=24) -> Image.Image:
    region = im.crop(cell)
    a = np.array(region)[:, :, 3]
    ys, xs = np.where(a > threshold)
    if len(xs) == 0:
        raise ValueError(f"empty cell {cell}")
    return region.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


def fit(im: Image.Image, box, anchor="center") -> Image.Image:
    """Scale to fit inside box (aspect preserved) and pad to exactly box."""
    w, h = box
    s = min(w / im.width, h / im.height)
    scaled = im.resize((max(1, round(im.width * s)), max(1, round(im.height * s))), Image.LANCZOS)
    canvas = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    x = (w - scaled.width) // 2
    y = 0 if anchor == "top" else (h - scaled.height) // 2
    canvas.paste(scaled, (x, y))
    return canvas


def stretch(im: Image.Image, box) -> Image.Image:
    return im.resize(box, Image.LANCZOS)


def key_background(im: Image.Image) -> Image.Image:
    """RGB sheet on a flat dark background -> RGBA: alpha ramps with colour distance from the dominant dark colour."""
    arr = np.array(im.convert("RGB")).astype(np.int32)
    flat = arr.reshape(-1, 3)
    dark = flat[flat.sum(1) < 150]
    vals, counts = np.unique(dark, axis=0, return_counts=True)
    bg = vals[counts.argmax()]
    dist = np.abs(arr - bg).sum(2)
    alpha = np.clip((dist - 30) / 60.0, 0.0, 1.0) * 255
    return Image.fromarray(np.dstack([arr, alpha]).astype(np.uint8), "RGBA")


def main_component_only(cell: Image.Image, threshold=40) -> Image.Image:
    """Drop blobs that touch the cell edge and are not the largest one: neighbour spill and header labels."""
    a = np.array(cell)
    solid = a[:, :, 3] > threshold
    h, w = solid.shape
    labels = np.zeros((h, w), dtype=np.int32)
    sizes, touches = [], []
    for y in range(h):
        for x in range(w):
            if not solid[y, x] or labels[y, x]:
                continue
            n = len(sizes) + 1
            labels[y, x] = n
            stack, size, touch = [(y, x)], 0, False
            while stack:
                cy, cx = stack.pop()
                size += 1
                touch = touch or cy in (0, h - 1) or cx in (0, w - 1)
                for ny in (cy - 1, cy, cy + 1):
                    for nx in (cx - 1, cx, cx + 1):
                        if 0 <= ny < h and 0 <= nx < w and solid[ny, nx] and not labels[ny, nx]:
                            labels[ny, nx] = n
                            stack.append((ny, nx))
            sizes.append(size)
            touches.append(touch)
    if not sizes:
        raise ValueError("empty cell")
    main = int(np.argmax(sizes)) + 1
    for i, touch in enumerate(touches, start=1):
        if i != main and touch:
            a[labels == i, 3] = 0
    return Image.fromarray(a, "RGBA")


def snap_alpha(im: Image.Image) -> Image.Image:
    """Resampling leaves a faint fringe: alpha 250 and above becomes 255, below 16 becomes 0 (and its colour is cleared)."""
    a = np.array(im)
    a[a[:, :, 3] >= 250, 3] = 255
    a[a[:, :, 3] < 16] = 0
    return Image.fromarray(a, "RGBA")


def runs(mask) -> int:
    """Number of separate runs of True in a 1-D mask."""
    m = np.concatenate([[False], np.asarray(mask, dtype=bool)])
    return int(np.count_nonzero(m[1:] & ~m[:-1]))


def portrait_bust(region: Image.Image) -> Image.Image:
    """A cut-out bust in the 64 px box: neighbour spill dropped, fitted to 62 px so the outer 1 px ring stays empty."""
    clean = main_component_only(region)
    crop = tight_crop(clean, (0, 0, clean.width, clean.height))
    canvas = Image.new("RGBA", (PORTRAIT_BOX, PORTRAIT_BOX), (0, 0, 0, 0))
    canvas.paste(fit(crop, (PORTRAIT_BOX - 2, PORTRAIT_BOX - 2), "top"), (1, 1))
    return snap_alpha(canvas)


def portrait_defect(im: Image.Image) -> str | None:
    """Self-check of a cut-out portrait; None when it is clean."""
    solid = np.array(im)[:, :, 3] > 0
    if solid[0].any() or solid[-1].any() or solid[:, 0].any() or solid[:, -1].any():
        return "opaque pixel in the outer 1 px ring"
    if runs(solid.any(1)) != 1:
        return f"{runs(solid.any(1))} vertical runs of content (a strip of a neighbour, or a cut)"
    if runs(solid.any(0)) != 1:
        return f"{runs(solid.any(0))} horizontal runs of content (a side sliver of a neighbour)"
    return None


def content_box(im: Image.Image) -> list:
    ys, xs = np.where(np.array(im)[:, :, 3] > 0)
    return [int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1]


def key_border(tile: Image.Image, dark=72) -> Image.Image:
    """Opaque painted tile -> cut-out: dark background flood-filled from the top and side borders becomes transparent."""
    a = np.array(tile.convert("RGB").convert("RGBA"))
    is_dark = a[:, :, :3].max(2) < dark
    h, w = is_dark.shape
    seen = np.zeros((h, w), dtype=bool)
    stack = [(0, x) for x in range(w)] + [(y, x) for y in range(h) for x in (0, w - 1)]
    while stack:
        y, x = stack.pop()
        if not (0 <= y < h and 0 <= x < w) or seen[y, x] or not is_dark[y, x]:
            continue
        seen[y, x] = True
        stack += [(y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)]
    a[seen, 3] = 0
    return Image.fromarray(a, "RGBA")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class Run:
    """One import run: every sprite goes through `put`, which refuses a second producer for the same ID."""

    def __init__(self, previous: dict, accept_changed: bool):
        self.previous, self.accept_changed = previous, accept_changed
        self.overrides: dict = {}
        self.produced: list = []
        self._hashes: dict = {}

    def put(self, sid: str, img: Image.Image, source: Path, kind: str, /, sheet: str | None = None, **extra):
        sheet = sheet or source.name
        if sid in OWNED_IDS and OWNER_ROOT not in source.parents:
            return
        if sid in self.overrides:
            sys.exit(f"ERROR duplicate sprite ID '{sid}': produced from '{self.overrides[sid]['sheet']}' and again from '{sheet}'")
        digest = self._hashes.get(source) or self._hashes.setdefault(source, sha256(source))
        before = self.previous.get(sid, {})
        if before.get("sheet") == sheet and before.get("sha256", digest) != digest and not self.accept_changed:
            sys.exit(f"ERROR source '{sheet}' changed since '{sid}' was last imported (its cells were measured on the old "
                     "file). Re-check the cells, then rerun with --accept-changed-sources")
        img.save(OUT / f"{sid}.png", optimize=True)
        self.overrides[sid] = {"sheet": sheet, "width": img.width, "height": img.height, "sourceKind": kind, "sha256": digest, **extra}
        self.produced.append((sid, img))


def pad_square(im: Image.Image, size: int) -> Image.Image:
    """Centre on a transparent square; shrink only when the crop is larger than the box (never enlarge pixel art)."""
    if max(im.width, im.height) > size:
        im = fit(im, (size, size))
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(im, ((size - im.width) // 2, (size - im.height) // 2))
    return canvas


def master_cells(scale: float = 1.0) -> dict:
    gx, gy, cw, ch = MASTER_GRID
    cells = {}
    for fam, (px, py) in MASTER_PANELS.items():
        for j, row in enumerate(WEAPON_ROWS):
            for k in range(WEAPON_LEVELS):
                x0, y0 = px + gx + k * cw, py + gy + j * ch
                cells[f"weapon_{fam}_{row}_{k + 1}"] = tuple(int(round(v * scale)) for v in (x0, y0, x0 + cw, y0 + ch))
    return cells


def import_master(path: Path, run: Run):
    im = key_background(Image.open(path))
    for sid, cell in master_cells(im.width / MASTER_WIDTH).items():
        region = main_component_only(im.crop(cell))
        result = pad_square(tight_crop(region, (0, 0, region.width, region.height)), WEAPON_BOX)
        run.put(sid, result, path, KIND_AI)
    write_weapon_art()


def write_weapon_art():
    lines = [
        "// GENERATED by tools/pixelart/import_assets.py from the weapon master sheet; do not edit by hand.",
        "package com.example.blacksmithproject.ui", "", "import com.example.blacksmithproject.R", "",
        "/** Drawable per weapon family, element row (base = no augment) and visual level 1..LEVELS. */",
        "internal object WeaponArt {",
        f"    const val LEVELS = {WEAPON_LEVELS}",
        "    private val rows = listOf(" + ", ".join(f'"{r}"' for r in WEAPON_ROWS) + ")",
        "    private val table: Map<String, Array<IntArray>> = mapOf(",
    ]
    for fam in FAMILIES:
        lines.append(f'        "{fam}" to arrayOf(')
        for row in WEAPON_ROWS:
            lines.append("            intArrayOf(" + ", ".join(f"R.drawable.weapon_{fam}_{row}_{k}" for k in range(1, WEAPON_LEVELS + 1)) + "),")
        lines.append("        ),")
    lines += ["    )", "",
              "    fun sprite(family: String, row: String, level: Int): Int? =",
              "        table[family]?.get(rows.indexOf(row).coerceAtLeast(0))?.get((level - 1).coerceIn(0, LEVELS - 1))",
              "}", ""]
    WEAPON_ART_KT.write_text("\n".join(lines), encoding="utf-8", newline="\n")


def known_specs() -> dict:
    """Every sprite ID with its layout spec, across all sheets (for loose single-file imports)."""
    specs = {sid: dict(box=(WEAPON_BOX, WEAPON_BOX)) for sid in master_cells()}
    for layout in LAYOUTS.values():
        specs.update(layout())
    return specs


def import_single(path: Path, spec: dict, run: Run):
    im = Image.open(path).convert("RGBA")
    crop = tight_crop(im, (0, 0, im.width, im.height))
    sid = path.stem
    if "box" in spec:
        result = stretch(crop, spec["box"]) if spec.get("tile") else fit(crop, spec["box"], spec.get("anchor", "center"))
    elif "scene_h" in spec:
        s = spec["scene_h"] * SCENE_UNIT / crop.height
        result = crop.resize((max(1, round(crop.width * s)), spec["scene_h"] * SCENE_UNIT), Image.LANCZOS)
    else:
        s = spec["size"] / max(crop.width, crop.height)
        result = crop.resize((max(1, round(crop.width * s)), max(1, round(crop.height * s))), Image.LANCZOS)
    run.put(sid, result, path, KIND_LOOSE)


def import_pack(folder: Path, run: Run, allow: tuple) -> int:
    meta = {}
    manifest = folder / "manifest.json"
    if manifest.exists():
        data = json.loads(manifest.read_text(encoding="utf-8"))
        for a in data.get("assets", []):
            meta[a["id"]] = a
    count = 0
    for png in sorted((folder / "drawable-nodpi").glob("*.png")):
        sid = png.stem
        if not sid.startswith(allow):
            continue
        img = Image.open(png).convert("RGBA")
        entry = {}
        m = meta.get(sid)
        if m:
            for k in ("anchor", "kind", "durationMs", "notes"):
                if m.get(k) not in (None, "", False):
                    entry[k] = m[k]
        run.put(sid, img, png, KIND_PACK, sheet=f"pack:{folder.name}", **entry)
        count += 1
    return count


def import_flat(path: Path, entries: dict, run: Run, sheet: str):
    """
    Crops from a flat board. Modes: `opaque` = the cell copied 1:1 with alpha forced to 255 (backdrops; no resample);
    `icon` = the same, centred in its box (never enlarged); `tile` = centre square of the cell reduced to the box,
    opaque; `cut` = the cell keyed to a cut-out bust (reviewed list and self-checks; a failing tile is dropped).
    """
    im = Image.open(path).convert("RGB").convert("RGBA")
    dropped = []
    for sid, spec in entries.items():
        region, mode, extra = im.crop(spec["cell"]), spec["mode"], {}
        if mode == "opaque":
            result = region
        elif mode == "icon":
            result = pad_square(region, spec["box"][0])
        elif mode == "tile":
            side = min(region.size) - 2 * TILE_INSET
            x, y = (region.width - side) // 2, (region.height - side) // 2
            result = region.crop((x, y, x + side, y + side)).resize(spec["box"], Image.LANCZOS)
        else:
            result = portrait_bust(key_border(region))
            defect = portrait_defect(result) or (None if sid in V2_CUT_REVIEWED else "not in the reviewed list")
            if defect:
                dropped.append(f"{sid} ({defect})")
                continue
            extra = {"contentBox": content_box(result)}
        run.put(sid, result, path, KIND_AI, sheet=sheet, **extra)  # the table's name, whatever --atlas PATH is called
    if dropped:
        print(f"  dropped {len(dropped)} keyed cut-outs: " + "; ".join(dropped))


def import_heroes(folder: Path, run: Run) -> int:
    """The hero set, in manifest order: one opaque tile per state, `portrait_hero_XX` (base) and `portrait_hero_XX_up`."""
    heroes = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))["heroes"]
    for hero in heroes:
        number, cls = hero["id"].removeprefix("hero_"), hero["class"]
        if cls not in CLASSES or not re.fullmatch(r"\d\d", number):
            sys.exit(f"ERROR {HEROES_SHEET}/manifest.json: hero '{hero['id']}' of class '{cls}' is not a known class or ID")
        for state, suffix in (("base", ""), ("upgraded", "_up")):
            path = folder / hero["states"][state]
            if not path.is_file():
                sys.exit(f"ERROR {HEROES_SHEET}: '{path.name}' is listed in manifest.json and missing")
            im = Image.open(path)
            size = im.width
            if im.width != im.height or size < HERO_BOX or "transparency" in im.info or im.mode not in ("RGB", "P"):
                sys.exit(f"ERROR {HEROES_SHEET}/{path.name}: expected an opaque square of {HERO_BOX} px or more, found {im.size} {im.mode}")
            if size != HERO_BOX:  # a native 64 px file keeps its pixels and its palette
                im = im.resize((HERO_BOX, HERO_BOX), Image.LANCZOS).quantize(HERO_COLOURS, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
            sid, sheet = f"portrait_hero_{number}{suffix}", f"{HEROES_SHEET}/{path.name}"
            run.put(sid, im, path, KIND_AI_PORTRAIT, sheet=sheet, heroClass=cls, state=state, character=hero["character"], sourceSize=size)
            if number in HERO_SMALL:
                x, y = HERO_SMALL[number]
                box = [x, y, x + HERO_SMALL_BOX, y + HERO_SMALL_BOX]
                run.put(f"{sid}_sm", im.crop(box), path, KIND_AI_PORTRAIT, sheet=sheet, heroClass=cls, state=state, smallOf=sid, crop=box)
    return len(heroes)


def import_sheet(n: int, path: Path, run: Run):
    im = Image.open(path).convert("RGBA")
    cells = LAYOUTS[n]()
    for sid, spec in cells.items():
        if spec.get("portrait"):
            result = portrait_bust(im.crop(spec["cell"]))
            defect = portrait_defect(result)
            if defect:
                sys.exit(f"ERROR {sid}: {defect} (cell {spec['cell']} in {CELLS.name}/sheet3_proposed_cells.json)")
            run.put(sid, result, path, KIND_AI, contentBox=content_box(result))
            continue
        crop = tight_crop(im, spec["cell"])
        if "box" in spec:
            result = stretch(crop, spec["box"]) if spec.get("tile") else fit(crop, spec["box"], spec.get("anchor", "center"))
        elif "scene_h" in spec:
            s = spec["scene_h"] * SCENE_UNIT / crop.height
            result = crop.resize((max(1, round(crop.width * s)), spec["scene_h"] * SCENE_UNIT), Image.LANCZOS)
        else:
            s = spec["size"] / max(crop.width, crop.height)
            result = crop.resize((max(1, round(crop.width * s)), max(1, round(crop.height * s))), Image.LANCZOS)
        run.put(sid, result, path, KIND_AI)


def write_portrait_art(overrides: dict):
    def table(name, ids, key=lambda sid: sid):
        out = [f"    val {name}: Map<String, Entry> = mapOf("]
        for sid in ids:
            box = overrides[sid].get("contentBox") or [0, 0, overrides[sid]["width"], overrides[sid]["height"]]
            out.append(f'        "{key(sid)}" to Entry(R.drawable.{sid}, "{sid}", {", ".join(map(str, box))}),')
        return out + ["    )"]

    base = [f"portrait_{cls}_{v}" for cls in CLASSES for v in range(5)]
    tiles = [f"portrait_v2_{cls}_{n}" for cls in CLASSES for n in range(1, 6) if f"portrait_v2_{cls}_{n}" in overrides]
    cuts = [f"{sid}_cut" for sid in tiles if f"{sid}_cut" in overrides]
    heroes = sorted(sid for sid, o in overrides.items() if sid.startswith("portrait_hero_") and o.get("state") == "base" and "smallOf" not in o)
    of_class = {cls: [sid for sid in heroes if overrides[sid]["heroClass"] == cls] for cls in CLASSES}
    lines = [
        "// GENERATED by tools/pixelart/import_assets.py from the portrait sheets; do not edit by hand.",
        "package com.example.blacksmithproject.ui", "", "import com.example.blacksmithproject.R", "",
        "/** Drawable and content box per appearance key (the key is the asset ID a hero stores, never an index). */",
        "internal object PortraitArt {",
        "    /** The one switch for the second face set: it joins the lookup only after it has been verified on a device. */",
        f"    const val SECOND_SET_ENABLED = {'true' if SECOND_SET_ENABLED else 'false'}",
        "",
        "    /** [resName] is the drawable's resource name; the box (left, top, right, bottom; right and bottom exclusive) holds the pixels that are not empty. */",
        "    class Entry(val drawable: Int, val resName: String, val left: Int, val top: Int, val right: Int, val bottom: Int)",
        "",
        "    /** The 25 busts of concept sheet 3: transparent cut-outs, five per class. Kept as assets; heroes no longer draw them (Sprites maps their keys onto the hero set). */",
    ] + table("base", base) + [
        "", "    /** Candidate second set as opaque 64 px tiles with their painted background. */",
    ] + table("secondSet", tiles) + [
        "", "    /** The same keys as keyed cut-outs: only the tiles that passed the importer's checks and the review. */",
    ] + table("secondSetCutouts", cuts, key=lambda sid: sid[:-len("_cut")]) + [
        "", "    /** The hero set: opaque 64 px tiles, one per hero, keyed by the drawable name of the base face. Every hero draws from this set. */",
    ] + table("heroes", heroes) + [
        "", "    /** The same keys with each hero's upgraded face. */",
    ] + table("heroesUpgraded", [f"{sid}_up" for sid in heroes], key=lambda sid: sid[:-len("_up")]) + [
        "", "    /** Tighter tiles for small list rows, only for the heroes whose face is small on the full tile. */",
    ] + table("heroesSmall", [f"{sid}_sm" for sid in heroes if f"{sid}_sm" in overrides], key=lambda sid: sid[:-len("_sm")]) + [
        "", "    /** The same for the upgraded faces. */",
    ] + table("heroesUpgradedSmall", [f"{sid}_up_sm" for sid in heroes if f"{sid}_up_sm" in overrides], key=lambda sid: sid[:-len("_up_sm")]) + [
        "", "    /** Hero keys per class in hero order: the faces a class has (" + ", ".join(f"{cls} {len(of_class[cls])}" for cls in CLASSES) + "). */",
        "    val heroFaces: Map<String, List<String>> = mapOf(",
    ] + [f'        "{cls}" to listOf(' + ", ".join(f'"{sid}"' for sid in of_class[cls]) + ")," for cls in CLASSES] + [
        "    )",
        "",
        "    fun find(key: String, secondSet: Boolean = SECOND_SET_ENABLED): Entry? = base[key] ?: if (secondSet) this.secondSet[key] else null",
        "",
        "    operator fun get(key: String): Entry? = find(key)",
        "}", ""]
    PORTRAIT_ART_KT.write_text("\n".join(lines), encoding="utf-8", newline="\n")


def contact_sheet(produced):
    if not produced:
        return
    cols = 10
    cell = 120
    rows = (len(produced) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + 14)), (30, 30, 34, 255))
    from PIL import ImageDraw
    d = ImageDraw.Draw(sheet)
    for i, (sid, img) in enumerate(produced):
        x, y = (i % cols) * cell, (i // cols) * (cell + 14)
        s = min((cell - 8) / img.width, (cell - 8) / img.height, 2.0)
        thumb = img.resize((max(1, int(img.width * s)), max(1, int(img.height * s))), Image.NEAREST)
        sheet.paste(thumb, (x + (cell - thumb.width) // 2, y + (cell - thumb.height) // 2), thumb)
        d.text((x + 2, y + cell), sid[:22], fill=(220, 220, 220, 255))
    sheet.save(CONTACT)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheet", type=int, default=None)
    ap.add_argument("--atlas", type=Path, default=SRC / ATLAS_NAME, help="path of the asset atlas when it is not in 'Pixel art assets/'")
    ap.add_argument("--heroes", type=Path, default=HEROES_DIR, help="folder of the hero portrait set (default: TINY_BLACKSMITH_HEROES, else Assets/Implemented/heroes)")
    ap.add_argument("--accept-changed-sources", action="store_true", help="import although a source differs from the file its cells were measured on")
    args = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    previous = json.loads(OVERRIDES.read_text()) if OVERRIDES.exists() else {}
    run = Run(previous, args.accept_changed_sources)
    overrides = run.overrides
    found = 0
    absent = set()  # sheets that were not available this run: their earlier sprites are kept, not pruned
    specs = known_specs()
    masters = [p for p in sorted(SRC.iterdir()) if p.is_file() and p.name.lower().startswith("weapons master")]
    for path in masters:
        if args.sheet:
            continue
        import_master(path, run)
        found += 1
        print(f"weapon master: {path.name}")
    flats = flat_sources()
    for path in sorted(SRC.glob("*.png")):
        if path in masters or path.name in flats:
            continue
        m = re.search(r"-(\d+)\.png$", path.name)
        if not m:
            if path.stem in specs:
                if args.sheet:
                    continue
                import_single(path, specs[path.stem], run)
                found += 1
                print(f"single sprite: {path.name}")
            else:
                print(f"skip {path.name}: not a numbered sheet and not a known sprite ID")
            continue
        n = int(m.group(1))
        if args.sheet and n != args.sheet:
            continue
        if n not in LAYOUTS:
            print(f"skip {path.name}: no layout for sheet {n} (add one to import_assets.py)")
            continue
        import_sheet(n, path, run)
        found += 1
        print(f"sheet {n}: {path.name}")
    if not args.sheet:
        for name, entries in flats.items():
            # The atlas may live elsewhere (--atlas); the other boards are looked up in the folder and in concept_references/.
            hits = [args.atlas] if name == ATLAS_NAME else sorted(SRC.glob(name)) + sorted(SRC.glob(f"*/concept_references/{name}"))
            hits = [p for p in hits if p.is_file()]
            if not hits:
                absent.add(name)
                print(f"flat source {name}: not found, skipped (its earlier sprites are kept)")
                continue
            import_flat(hits[0], entries, run, name)
            found += 1
            print(f"flat source: {name}")
        for pack in [d for d in sorted(SRC.iterdir()) if d.is_dir() and (d / "drawable-nodpi").is_dir()]:
            if pack.name not in PACK_ALLOW:
                print(f"pack {pack.name}: skipped (not in PACK_ALLOW)")
                continue
            n = import_pack(pack, run, PACK_ALLOW[pack.name])
            found += 1
            print(f"pack {pack.name}: {n} sprites copied verbatim")
        for name, allow in OWNER_PACKS.items():
            folder = OWNER_ROOT / name
            if (folder / "drawable-nodpi").is_dir():
                n = import_pack(folder, run, allow)
                found += 1
                print(f"owner art {name}: {n} sprites copied verbatim")
            else:
                absent.add(f"pack:{name}")
        if (args.heroes / "manifest.json").is_file():
            n = import_heroes(args.heroes, run)
            found += 1
            print(f"hero set {args.heroes}: {n} heroes, {2 * n} portraits at {HERO_BOX} px")
        else:
            absent.add(HEROES_SHEET)
            print(f"hero set: no manifest.json in {args.heroes}, skipped (the committed hero portraits are kept; pass --heroes PATH)")
    # Sprites of a source that was not read this run stay as they are (one sheet with --sheet; an absent optional source).
    for sid, entry in previous.items():
        sheet = entry.get("sheet", "")
        if sid not in overrides and (args.sheet or sheet in absent or sheet.rpartition("/")[0] in absent) and (OUT / f"{sid}.png").exists():
            overrides[sid] = entry
    pruned = 0
    for sid in previous:
        if sid not in overrides and (OUT / f"{sid}.png").exists():
            (OUT / f"{sid}.png").unlink()
            pruned += 1
    if pruned:
        print(f"pruned {pruned} stale imported sprites (placeholders are regenerated by generate_assets.py)")
    OVERRIDES.write_text(json.dumps(dict(sorted(overrides.items())), indent=1) + "\n", newline="\n")
    write_portrait_art(overrides)
    contact_sheet([(sid, Image.open(OUT / f"{sid}.png").convert("RGBA")) for sid in sorted(overrides)])
    print(f"{found} sources, {len(run.produced)} sprites written; {len(overrides)} overrides recorded; contact sheet {CONTACT}")
    return 0 if found else 1


if __name__ == "__main__":
    sys.exit(main())
