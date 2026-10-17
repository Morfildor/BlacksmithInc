"""
Weapon art in layers, derived from the sliced weapon master sheet (`import_assets.import_master`).

A weapon is drawn as up to three 56 px layers stacked at the same origin:
  weapon_<family>_<metal>_<level>        the base-row cell of that family and level, its metal parts recoloured with the
                                         core metal's ramp (blades, heads, fittings, bow strings, staff orbs)
  weapon_fx_<family>_<element>_<level>   the augment: the metal's edge in the element colour, a glow around it and the
                                         sparks, flames or leaves of the same cell in the element row (aligned on the base)
  weapon_charm_<family>_<catalyst>_<level>  the catalyst: its material icon shrunk to a charm, on a cord from the grip
The level is the visual level `ui/Sprites.weaponLevel` picks (metal tier + rarity + signature), so each column keeps its
ornaments while the metal and the augment show at every level. Pure numpy/Pillow; the same cells give the same files.
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image

METALS = ["iron", "bronze", "silver", "obsidian", "starsteel", "moonsteel"]
ELEMENTS = ["fire", "frost", "storm", "grave", "verdant", "sun"]
CATALYSTS = ["binding_salt", "runestone_shard", "dragon_oil", "void_ink"]

# Dark, mid, light, shine of each core metal, after the colours of its material icon (silver and moonsteel are kept apart:
# moonsteel is the cold, pale cyan one). Starsteel also gets a few star glints.
RAMPS = {
    "iron": [(40, 38, 56), (98, 98, 118), (170, 170, 190), (225, 225, 235)],
    "bronze": [(86, 34, 30), (168, 82, 44), (236, 150, 78), (255, 214, 150)],
    "silver": [(92, 94, 108), (172, 174, 184), (232, 232, 238), (255, 255, 255)],
    "obsidian": [(14, 10, 24), (40, 26, 62), (96, 64, 140), (178, 140, 220)],
    "starsteel": [(16, 16, 60), (36, 52, 150), (110, 140, 230), (220, 230, 255)],
    "moonsteel": [(40, 90, 110), (100, 190, 205), (170, 240, 240), (235, 255, 255)],
}
# The element colour of the edge and the glow, as the element rows of the master sheet paint it.
ELEMENT_COLOURS = {
    "fire": (255, 110, 40), "frost": (120, 200, 255), "storm": (180, 110, 255),
    "grave": (90, 230, 200), "verdant": (140, 230, 70), "sun": (255, 210, 90),
}
MATERIAL_ICONS = Path(__file__).resolve().parents[2] / "Assets" / "Implemented" / "materials" / "drawable-nodpi"
CHARM_SIZE = 15
CORD = (70, 44, 30, 255)
OUTLINE = (44, 26, 22, 255)
# Cells whose metal head is painted gold: there the big gold parts are the metal too (small gold trim stays gold).
GOLD_HEADS = {("axe", 7), ("axe", 8), ("bow", 7)}


def _hsv(a: np.ndarray):
    rgb = a[..., :3] / 255.0
    mx, mn = rgb.max(-1), rgb.min(-1)
    d = np.maximum(mx - mn, 1e-6)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    h = np.where(mx == r, ((g - b) / d) % 6, np.where(mx == g, (b - r) / d + 2, (r - g) / d + 4)) * 60
    s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    return h, s, mx


def _grow(m: np.ndarray, n: int = 1) -> np.ndarray:
    """Binary dilation by n pixels (8-neighbourhood), without wrapping at the edges."""
    out = m.copy()
    for _ in range(n):
        p = np.pad(out, 1)
        out = np.zeros_like(out)
        for dy in (0, 1, 2):
            for dx in (0, 1, 2):
                out |= p[dy:dy + m.shape[0], dx:dx + m.shape[1]]
    return out


def _components(m: np.ndarray, min_size: int) -> np.ndarray:
    """The union of the 4-connected parts of m with at least min_size pixels."""
    seen = np.zeros_like(m)
    keep = np.zeros_like(m)
    h, w = m.shape
    for y0, x0 in zip(*np.nonzero(m)):
        if seen[y0, x0]:
            continue
        stack, part = [(y0, x0)], []
        seen[y0, x0] = True
        while stack:
            y, x = stack.pop()
            part.append((y, x))
            for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                if 0 <= ny < h and 0 <= nx < w and m[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    stack.append((ny, nx))
        if len(part) >= min_size:
            ys, xs = zip(*part)
            keep[list(ys), list(xs)] = True
    return keep


def metal_mask(a: np.ndarray, family: str, level: int) -> np.ndarray:
    """The parts of a base cell that are the core metal: grey steel, the painted blue and violet blades and orbs of the
    higher levels and the blue sparkles loose around them, and the gold heads of GOLD_HEADS.
    Other gold, wood, leather and the small gems are left alone."""
    h, s, v = _hsv(a)
    opaque = a[..., 3] > 0
    grey = opaque & (s < 0.28) & (v > 0.25)
    bluish = opaque & (h > 185) & (h < 235) & (v > 0.35)
    loose = opaque & ~_components(opaque, int(opaque.sum() * 0.5))
    mask = grey | _components(bluish, 40) | (bluish & loose)
    mask |= _components(opaque & (h >= 235) & (h < 300) & (v > 0.35), 25 if family == "staff" else 40)
    if (family, level) in GOLD_HEADS:
        mask |= _components(opaque & (h >= 20) & (h < 60) & (s > 0.35) & (v > 0.45), 60)
    return mask


def recolour(a: np.ndarray, mask: np.ndarray, metal: str) -> np.ndarray:
    out = a.copy()
    if not mask.any():
        return out
    lum = (a[..., :3] @ np.array([0.3, 0.59, 0.11])) / 255.0
    lo, hi = np.percentile(lum[mask], 3), np.percentile(lum[mask], 97)
    t = np.clip((lum - lo) / max(hi - lo, 1e-3), 0, 1)
    ramp = np.array(RAMPS[metal], float)
    seg = np.minimum((t * 3).astype(int), 2)
    f = (t * 3 - seg)[..., None]
    colour = ramp[seg] * (1 - f) + ramp[seg + 1] * f
    out[..., :3] = np.where(mask[..., None], np.round(colour), a[..., :3])
    if metal == "starsteel":
        ys, xs = np.nonzero(mask & (t > 0.35))
        for y, x in zip(ys, xs):
            if (x * 7 + y * 13) % 29 == 0:
                out[y, x, :3] = (240, 244, 255)
    return out


def _best_shift(base: np.ndarray, other: np.ndarray, reach: int = 8) -> tuple[int, int]:
    best, shift = -1.0, (0, 0)
    for dy in range(-reach, reach + 1):
        for dx in range(-reach, reach + 1):
            s = np.roll(np.roll(other, dy, 0), dx, 1)
            iou = (base & s).sum() / max(1, (base | s).sum())
            if iou > best:
                best, shift = iou, (dx, dy)
    return shift


def element_layer(base: np.ndarray, mask: np.ndarray, painted: np.ndarray, element: str, level: int) -> np.ndarray:
    """Edge, glow and the painted effects of the element cell outside the base silhouette."""
    solid = base[..., 3] > 0
    out = np.zeros_like(base)
    dx, dy = _best_shift(base[..., 3] > 128, painted[..., 3] > 128)
    moved = np.roll(np.roll(painted, dy, 0), dx, 1)
    keep = ~_grow(solid) & (moved[..., :3].max(-1) > 95) & (moved[..., 3] > 0)
    out[keep] = moved[keep]
    colour = np.array(ELEMENT_COLOURS[element])
    glow = _grow(mask) & ~solid & ~keep
    out[glow, :3] = colour
    out[glow, 3] = min(255, 70 + 12 * level)
    edge = mask & _grow(~solid)
    out[edge, :3] = colour
    out[edge, 3] = 215
    return out


def charm_from_icon(icon: Image.Image) -> Image.Image:
    """The icon at CHARM_SIZE: each output pixel takes the most common colour of its block (pixel art keeps its flat
    colours that way), then a one-pixel dark outline so the charm reads against the blade."""
    a = np.array(icon.convert("RGBA"))
    ys, xs = np.nonzero(a[..., 3] > 0)
    a = a[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    h, w = a.shape[:2]
    scale = (CHARM_SIZE - 2) / max(h, w)
    oh, ow = max(1, round(h * scale)), max(1, round(w * scale))
    out = np.zeros((oh + 2, ow + 2, 4), np.uint8)
    for oy in range(oh):
        for ox in range(ow):
            block = a[int(oy * h / oh):max(int((oy + 1) * h / oh), int(oy * h / oh) + 1),
                      int(ox * w / ow):max(int((ox + 1) * w / ow), int(ox * w / ow) + 1)].reshape(-1, 4)
            solid = block[block[:, 3] > 127]
            if len(solid) * 2.5 < len(block):
                continue
            keys = [tuple(int(c) >> 4 for c in px[:3]) for px in solid]
            best = max(set(keys), key=keys.count)
            out[oy + 1, ox + 1, :3] = np.mean([px[:3] for px, k in zip(solid, keys) if k == best], axis=0)
            out[oy + 1, ox + 1, 3] = 255
    ring = _grow(out[..., 3] > 0) & ~(out[..., 3] > 0)
    out[ring] = OUTLINE
    return Image.fromarray(out, "RGBA")


def _charm_spot(solid: np.ndarray, anchor: tuple[int, int], size: tuple[int, int]) -> tuple[int, int]:
    """The free place nearest the anchor, below it where it fits, with the charm whole and three pixels clear of the
    weapon so the cord shows."""
    h, w = solid.shape
    cw, ch = size
    blocked = _grow(solid, 3)
    best, spot = None, (0, h - ch)
    for y in range(0, h - ch + 1):
        for x in range(0, w - cw + 1):
            if blocked[y:y + ch, x:x + cw].any():
                continue
            d = (x + cw / 2 - anchor[0]) ** 2 + (y - anchor[1]) ** 2 + (400 if y < anchor[1] else 0)
            if best is None or d < best:
                best, spot = d, (x, y)
    return spot


def grip_anchor(solid: np.ndarray, family: str) -> tuple[int, int]:
    """Where the cord is tied: the grip, a quarter up the weapon from its bottom-left end (the middle of a bow)."""
    ys, xs = np.nonzero(solid)
    along = xs - ys  # weapons lie from bottom-left to top-right
    target = np.quantile(along, 0.5 if family == "bow" else 0.22)
    i = int(np.argmin(np.abs(along - target) * 4 - ys))  # on that cross-line, the lowest pixel
    return int(xs[i]), int(ys[i])


def charm_layer(base: np.ndarray, family: str, charm: Image.Image) -> np.ndarray:
    solid = base[..., 3] > 0
    ax, ay = grip_anchor(solid, family)
    x, y = _charm_spot(solid, (ax, ay), charm.size)
    layer = Image.new("RGBA", (base.shape[1], base.shape[0]), (0, 0, 0, 0))
    top = (x + charm.width // 2, y)
    n = max(abs(top[0] - ax), abs(top[1] - ay), 1)
    for i in range(n + 1):  # the cord, from the grip to the top of the charm
        px, py = round(ax + (top[0] - ax) * i / n), round(ay + (top[1] - ay) * i / n)
        if not solid[py, px]:
            layer.putpixel((px, py), CORD)
    layer.alpha_composite(charm, (x, y))
    return np.array(layer)


def derive(cells: dict) -> dict:
    """cells: {(family, row, level): 56 px RGBA image} for every master-sheet cell. Returns {sprite id: image}."""
    out = {}
    charms = {c: charm_from_icon(Image.open(MATERIAL_ICONS / f"material_{c}.png")) for c in CATALYSTS
              if (MATERIAL_ICONS / f"material_{c}.png").exists()}
    for (family, row, level), cell in sorted(cells.items()):
        if row != "base":
            continue
        base = np.array(cell.convert("RGBA"))
        mask = metal_mask(base, family, level)
        for metal in METALS:
            out[f"weapon_{family}_{metal}_{level}"] = recolour(base, mask, metal)
        for element in ELEMENTS:
            painted = np.array(cells[(family, element, level)].convert("RGBA"))
            out[f"weapon_fx_{family}_{element}_{level}"] = element_layer(base, mask, painted, element, level)
        for catalyst, charm in charms.items():
            out[f"weapon_charm_{family}_{catalyst}_{level}"] = charm_layer(base, family, charm)
    return {sid: Image.fromarray(a.astype(np.uint8), "RGBA") for sid, a in out.items()}
