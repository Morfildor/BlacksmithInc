"""
Import hand-made sprite sheets from `Pixel art assets/` into app/src/main/res/drawable-nodpi/.

Each sheet is sliced with explicit cell rectangles (the sheets are not on a strict grid, so detection is not
reliable), tight-cropped by alpha inside the cell, downscaled to the target size and written under the sprite ID
that `ui/Sprites.kt` and `ForgeScene` load. Every imported ID is recorded in `overrides.json`; `generate_assets.py`
then leaves those files alone (placeholders are only generated for IDs with no hand-made source).

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

A production pack (a subfolder holding `drawable-nodpi/*.png` plus `manifest.json`, as delivered by the pixel artist)
is copied verbatim for the IDs in PACK_PREFIXES: those are 1x sprites the concept sheets do not provide (battle
animation frames, siege wall, milestone burst, hero markers). Pass `--pack-all` to take every pack sprite instead.
IDs imported earlier but no longer produced are pruned from the drawable folder.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "Pixel art assets"
OUT = ROOT / "app/src/main/res/drawable-nodpi"
OVERRIDES = Path(__file__).with_name("overrides.json")
CONTACT = ROOT / "docs/art_contact_handmade.png"

# Sprites taken from a production pack by default (the sheets cover the rest at higher fidelity).
PACK_PREFIXES = ("hero_", "monster_", "siege_wall", "fx_milestone_", "marker_")
# Packs that are deliberately not imported (see docs/DECISIONS.md "Art sources"). Remove a name here to adopt it.
PACK_SKIP = ("Tiny_Blacksmith_UI_Backgrounds_v3",)

# Logical scene unit: the forge scene is laid out in 96x48 "scene pixels"; hand-made scene pieces are exported at
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
    cells = {}
    for i, cell in enumerate(grid(0, 0, 1254, 1254, 5, 5)):
        cls, variant = CLASSES[i // 5], i % 5
        cells[f"portrait_{cls}_{variant}"] = dict(cell=cell, box=(64, 64), anchor="top")
    return cells


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


def import_master(path: Path, overrides: dict, produced: list):
    im = key_background(Image.open(path))
    for sid, cell in master_cells(im.width / MASTER_WIDTH).items():
        region = main_component_only(im.crop(cell))
        result = pad_square(tight_crop(region, (0, 0, region.width, region.height)), WEAPON_BOX)
        result.save(OUT / f"{sid}.png", optimize=True)
        overrides[sid] = {"sheet": path.name, "width": result.width, "height": result.height}
        produced.append((sid, result))
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
    WEAPON_ART_KT.write_text("\n".join(lines), encoding="utf-8")


def known_specs() -> dict:
    """Every sprite ID with its layout spec, across all sheets (for loose single-file imports)."""
    specs = {sid: dict(box=(WEAPON_BOX, WEAPON_BOX)) for sid in master_cells()}
    for layout in LAYOUTS.values():
        specs.update(layout())
    return specs


def import_single(path: Path, spec: dict, overrides: dict, produced: list):
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
    result.save(OUT / f"{sid}.png", optimize=True)
    overrides[sid] = {"sheet": path.name, "width": result.width, "height": result.height}
    produced.append((sid, result))


def import_pack(folder: Path, overrides: dict, produced: list, take_all: bool) -> int:
    meta = {}
    manifest = folder / "manifest.json"
    if manifest.exists():
        data = json.loads(manifest.read_text(encoding="utf-8"))
        for a in data.get("assets", []):
            meta[a["id"]] = a
    count = 0
    for png in sorted((folder / "drawable-nodpi").glob("*.png")):
        sid = png.stem
        if not take_all and not sid.startswith(PACK_PREFIXES):
            continue
        img = Image.open(png).convert("RGBA")
        img.save(OUT / png.name, optimize=True)
        entry = {"sheet": f"pack:{folder.name}", "width": img.width, "height": img.height}
        m = meta.get(sid)
        if m:
            for k in ("anchor", "kind", "durationMs", "notes"):
                if m.get(k) not in (None, "", False):
                    entry[k] = m[k]
        overrides[sid] = entry
        produced.append((sid, img))
        count += 1
    return count


def import_sheet(n: int, path: Path, overrides: dict, produced: list):
    im = Image.open(path).convert("RGBA")
    cells = LAYOUTS[n]()
    for sid, spec in cells.items():
        crop = tight_crop(im, spec["cell"])
        if "box" in spec:
            result = stretch(crop, spec["box"]) if spec.get("tile") else fit(crop, spec["box"], spec.get("anchor", "center"))
        elif "scene_h" in spec:
            s = spec["scene_h"] * SCENE_UNIT / crop.height
            result = crop.resize((max(1, round(crop.width * s)), spec["scene_h"] * SCENE_UNIT), Image.LANCZOS)
        else:
            s = spec["size"] / max(crop.width, crop.height)
            result = crop.resize((max(1, round(crop.width * s)), max(1, round(crop.height * s))), Image.LANCZOS)
        result.save(OUT / f"{sid}.png", optimize=True)
        overrides[sid] = {"sheet": path.name, "width": result.width, "height": result.height}
        produced.append((sid, result))


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
    ap.add_argument("--pack-all", action="store_true", help="take every sprite from a production pack, not only PACK_PREFIXES")
    args = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    previous = json.loads(OVERRIDES.read_text()) if OVERRIDES.exists() else {}
    overrides: dict = {}
    produced = []
    found = 0
    specs = known_specs()
    masters = [p for p in sorted(SRC.iterdir()) if p.is_file() and p.name.lower().startswith("weapons master")]
    for path in masters:
        if args.sheet:
            continue
        import_master(path, overrides, produced)
        found += 1
        print(f"weapon master: {path.name}")
    for path in sorted(SRC.glob("*.png")):
        if path in masters:
            continue
        m = re.search(r"-(\d+)\.png$", path.name)
        if not m:
            if path.stem in specs:
                if args.sheet:
                    continue
                import_single(path, specs[path.stem], overrides, produced)
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
        import_sheet(n, path, overrides, produced)
        found += 1
        print(f"sheet {n}: {path.name}")
    if not args.sheet:
        for pack in [d for d in sorted(SRC.iterdir()) if d.is_dir() and (d / "drawable-nodpi").is_dir()]:
            if pack.name in PACK_SKIP:
                print(f"pack {pack.name}: skipped (PACK_SKIP)")
                continue
            n = import_pack(pack, overrides, produced, args.pack_all)
            found += 1
            print(f"pack {pack.name}: {n} sprites copied verbatim")
        pruned = 0
        for sid in previous:
            if sid not in overrides and (OUT / f"{sid}.png").exists():
                (OUT / f"{sid}.png").unlink()
                pruned += 1
        if pruned:
            print(f"pruned {pruned} stale imported sprites (placeholders are regenerated by generate_assets.py)")
    OVERRIDES.write_text(json.dumps(dict(sorted(overrides.items())), indent=1) + "\n")
    contact_sheet(produced)
    print(f"{found} sheets, {len(produced)} sprites written; {len(overrides)} overrides recorded; contact sheet {CONTACT}")
    return 0 if found else 1


if __name__ == "__main__":
    sys.exit(main())
