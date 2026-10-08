#!/usr/bin/env python3
"""
Tiny Blacksmith pixel-art pipeline (GDD §14).

Every sprite is authored here as a palette-indexed pixel map or a small procedural routine, so the art is
reproducible, diff-able and consistent (one palette, one light source from the upper left, 1px dark outlines).
Run:  python tools/pixelart/generate_assets.py
Writes PNGs to app/src/main/res/drawable-nodpi/ and the manifest to app/src/main/assets/art/manifest.json.
The Compose layer scales them with nearest-neighbour filtering and never draws essential text into bitmaps.
"""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow is required: pip install pillow")

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "app/src/main/res/drawable-nodpi"
MANIFEST = ROOT / "app/src/main/assets/art/manifest.json"
DOC = ROOT / "docs/ART_MANIFEST.md"

# One limited palette (letters are used in the pixel maps below).
PALETTE = {
    ".": None,
    "K": (26, 18, 16), "D": (43, 35, 32), "B": (74, 59, 51), "b": (107, 75, 50), "W": (140, 98, 57), "w": (232, 228, 218),
    "S": (85, 89, 93), "s": (125, 129, 134), "L": (176, 180, 184),
    "R": (179, 48, 26), "O": (226, 96, 31), "Y": (255, 193, 77), "F": (255, 240, 160),
    "I": (138, 143, 148), "i": (92, 97, 102),          # iron
    "Z": (199, 136, 58), "z": (138, 90, 34),           # bronze
    "V": (217, 221, 227), "v": (154, 163, 173),        # silver
    "C": (127, 216, 255), "c": (58, 160, 216),         # frost
    "P": (180, 140, 255), "p": (106, 75, 214),         # storm
    "G": (90, 160, 90), "g": (50, 100, 50),            # verdant / ranger cloth
    "e": (224, 176, 138), "h": (74, 46, 26), "q": (58, 90, 138), "l": (122, 74, 42), "r": (160, 48, 48),
    "y": (216, 160, 48), "k": (20, 20, 24), "m": (120, 40, 30), "M": (70, 24, 20), "T": (200, 190, 150),
    "X": (120, 96, 140), "x": (66, 50, 84),            # obsidian
    "N": (170, 210, 236), "n": (92, 134, 184),         # starsteel
    "U": (206, 200, 240), "u": (134, 126, 190),        # moonsteel
    "E": (178, 206, 150), "d": (80, 104, 84),          # grave dust
    "a": (156, 222, 120),                              # verdant sap highlight
    "Q": (48, 24, 72), "J": (96, 56, 140),             # void ink
}

CORE_RECOLOR = {  # family silhouettes use I/i, recoloured per core material (I = lit face, i = shaded face)
    "iron": {"I": "I", "i": "i"},
    "bronze": {"I": "Z", "i": "z"},
    "silver": {"I": "V", "i": "v"},
    "obsidian": {"I": "X", "i": "x"},
    "starsteel": {"I": "N", "i": "n"},
    "moonsteel": {"I": "U", "i": "u"},
}

SPRITES: dict[str, list[str]] = {}
META: dict[str, dict] = {}


def sprite(name: str, rows: list[str], **meta):
    w = len(rows[0])
    assert all(len(r) == w for r in rows), f"{name}: ragged rows"
    SPRITES[name] = rows
    META[name] = {"width": w, "height": len(rows), **meta}


# Weapons come only from the weapon master sheet (import_assets.py); there are no weapon or overlay placeholders.

# ---------------------------------------------------------------- portraits (16x16)
sprite("portrait_guardian", [
    "................",
    ".....KKKKKK.....",
    "....KsLLLLsK....",
    "....KsLLLLsK....",
    "...KsLKKKKLsK...",
    "...KsKeeeeKsK...",
    "...KsKekekKsK...",
    "....KKeeeeKK....",
    ".....KeeeeK.....",
    "....KKKKKKKK....",
    "...KsLsqqsLsK...",
    "..KsLsKqqKsLsK..",
    "..KsKKKqqKKKsK..",
    "..KKK.KqqK.KKK..",
    "......KKKK......",
    "................",
], anchor="top-left", layer="portrait", heroClass="guardian")

sprite("portrait_ranger", [
    "................",
    "......KKKK......",
    ".....KGggGK.....",
    "....KGgggggK....",
    "....KgKKKKgK....",
    "....KgKeeeKgK...",
    "...KgKekekKgK...",
    "....KKeeeeKK....",
    ".....KeeeeK.....",
    "....KKKKKKKK....",
    "...KlgGgGgglK...",
    "..KlKgGggGgKlK..",
    "..KlKKgGGgKKlK..",
    "..KKK.KggK.KKK..",
    "......KKKK......",
    "................",
], anchor="top-left", layer="portrait", heroClass="ranger")

sprite("portrait_duelist", [
    "................",
    "..........Ky....",
    ".....KKKKKKyK...",
    "....KbBBBBKyK...",
    "..KKbBBBBBBKK...",
    "...KhKeeeeKhK...",
    "...KhKekekKhK...",
    "....KKeeeeKK....",
    ".....KeeeeK.....",
    "....KKKKKKKK....",
    "...KrRrwwrRrK...",
    "..KrRrKwwKrRrK..",
    "..KrKKKwwKKKrK..",
    "..KKK.KwwK.KKK..",
    "......KKKK......",
    "................",
], anchor="top-left", layer="portrait", heroClass="duelist")

sprite("portrait_battlemage", [
    ".......KK.......",
    "......KPpK......",
    ".....KPPppK.....",
    "....KPPPpppK....",
    "..KKPPPPPpppKK..",
    "...KhKeeeeKhK...",
    "...KhKekekKhK...",
    "....KKeeeeKK....",
    ".....KeeeeK.....",
    "....KKKKKKKK....",
    "...KpPpYYpPpK...",
    "..KpPpKYYKpPpK..",
    "..KpKKKYYKKKpK..",
    "..KKK.KYYK.KKK..",
    "......KKKK......",
    "................",
], anchor="top-left", layer="portrait", heroClass="battlemage")

sprite("portrait_warden", [
    "...KK.....KK....",
    "..KZzK...KZzK...",
    "..KZzKKKKKZzK...",
    "...KKZZZZzzKK...",
    "...KZZKKKKzzK...",
    "...KZKeeeeKzK...",
    "...KZKekekKzK...",
    "....KKeeeeKK....",
    ".....KeeeeK.....",
    "....KKKKKKKK....",
    "...KZzGggGzZK...",
    "..KZzzKGgKzzZK..",
    "..KZKKKGgKKKzK..",
    "..KKK.KGgK.KKK..",
    "......KKKK......",
    "................",
], anchor="top-left", layer="portrait", heroClass="warden")

# ---------------------------------------------------------------- faction (16x16)
sprite("faction_ashclaw_raider", [
    "................",
    "...KK.....KK....",
    "..KMMK...KMMK...",
    "..KMmMKKKMmMK...",
    "...KMmmmmmMK....",
    "...KmRkmkRmK....",
    "...KmmmmmmmK....",
    "....KmMMMmK.....",
    "...KKmmmmmKK....",
    "..KmMmmmmmMmK...",
    "..KmKmmmmmKmK...",
    "..KKKmmmmmKKK...",
    "....KmmKmmK.....",
    "....KmK.KmK.....",
    "...KKKK.KKKK....",
    "................",
], anchor="bottom-center", layer="enemy", faction="ashclaw_raiders")

sprite("faction_ashclaw_brute", [
    "..KK.......KK...",
    ".KMMK.....KMMK..",
    ".KMmMK...KMmMK..",
    "..KMmmKKKmmMK...",
    "..KMmmmmmmmMK...",
    "..KmRRkmkRRmK...",
    "..KmmmmmmmmmK...",
    "...KmMMMMMmK....",
    ".KKKmmmmmmmKKK..",
    "KmMmmmmmmmmmMmK.",
    "KmKKmmmmmmmKKmK.",
    "KKK.KmmmmmK.KKK.",
    "....KmmKmmK.....",
    "....KmmKmmK.....",
    "...KKKK.KKKK....",
    "................",
], anchor="bottom-center", layer="enemy", faction="ashclaw_raiders", variant="elite")

sprite("faction_hollowbound_shade", [
    "................",
    ".......KK.......",
    "......KsSK......",
    ".....KsSSSK.....",
    ".....KsKKKSK....",
    "....KsKEkEKSK...",
    "....KsKkkkKSK...",
    "....KsSKKKSSK...",
    "....KsSSSSSSK...",
    "...KsSSSSSSSSK..",
    "...KsSSSSSSSSK..",
    "...KsSSSSSSSSK..",
    "....KsSKSSKSSK..",
    "....KKK.KKK.KK..",
    "................",
    "................",
], anchor="bottom-center", layer="enemy", faction="hollowbound")

sprite("faction_hollowbound_wraith", [
    "......KKKK......",
    ".....KsSSSK.....",
    "....KsSSSSSK....",
    "....KsKKKKSK....",
    "...KsKEEkEEKSK..",
    "...KsKkkkkkKSK..",
    "...KsSKKKKKSSK..",
    "..KLKsSSSSSSKLK.",
    "..KLKsSSSSSSKLK.",
    "..KKKsSSSSSSKKK.",
    "...KsSSSSSSSSK..",
    "...KsSSSSSSSSK..",
    "...KsSKSSSKSSK..",
    "....KsKKSSKKSK..",
    "....KKK.KKK.KK..",
    "................",
], anchor="bottom-center", layer="enemy", faction="hollowbound", variant="elite")

sprite("faction_embermaw_whelp", [
    "................",
    "................",
    "................",
    ".....KK..KK.....",
    "....KRRKKRRK....",
    "....KRRRRRRK....",
    "....KRYRRYRK....",
    "....KRRRRRRK....",
    ".KKK.KRRRRK.KKK.",
    "KmmmKKROORKKmmmK",
    "KmmmmKROORKmmmmK",
    ".KKKKKROORKKKKK.",
    ".....KRRRRRK....",
    "....KRK..KRK....",
    "...KKKK..KKKK...",
    "................",
], anchor="bottom-center", layer="enemy", faction="embermaw_brood")

sprite("faction_embermaw_drake", [
    "...KK......KK...",
    "..KMMK....KMMK..",
    "...KMMKKKKMMK...",
    "....KRRRRRRK....",
    "....KRYRRYRK....",
    "....KRRRRRRK....",
    ".....KRFFRK.....",
    "KKK..KRRRRK..KKK",
    "KmmKKKROORKKKmmK",
    "KmmmmKROORKmmmmK",
    "KmmmmKROORKmmmmK",
    ".KmmmKROORKmmmK.",
    "..KKKKRRRRKKKK..",
    "....KRRK.KRRK...",
    "...KKKK..KKKK...",
    "................",
], anchor="bottom-center", layer="enemy", faction="embermaw_brood", variant="elite")

# ---------------------------------------------------------------- badges (8x8): shape differs per tier, not only colour
sprite("badge_common", [
    "........", "........", "...KK...", "..KLLK..", "..KLLK..", "...KK...", "........", "........",
], layer="badge", rarity="COMMON")
sprite("badge_uncommon", [
    "........", ".KK..KK.", "KGGKKGGK", "KGGKKGGK", ".KK..KK.", "........", "........", "........",
], layer="badge", rarity="UNCOMMON")
sprite("badge_rare", [
    "...KK...", "..KccK..", ".KcCCcK.", "KcCwCcK.", ".KcCCcK.", "..KccK..", "...KK...", "........",
], layer="badge", rarity="RARE")
sprite("badge_epic", [
    "...KK...", "..KPPK..", ".KPpPPK.", "KPPPwPPK", ".KPpPPK.", "..KPPK..", ".KK..KK.", "........",
], layer="badge", rarity="EPIC")
sprite("badge_legendary", [
    "K..KK..K", ".KYYYYK.", "KYYFFYYK", "KYFwwFYK", "KYFwwFYK", "KYYFFYYK", ".KYYYYK.", "K..KK..K",
], layer="badge", rarity="LEGENDARY")
sprite("badge_flaw", [
    "...KK...", "..KRRK..", "..KRRK..", "..KRRK..", "...KK...", "..KRRK..", "...KK...", "........",
], layer="badge", meaning="flaw")

# ---------------------------------------------------------------- material icons (12x12)
INGOT = [  # shared core-ingot shape: 1 = lit top face, 2 = shaded front face
    "............",
    "............",
    "....KKKK....",
    "...Kw111K...",
    "..Kw11111K..",
    ".Kw1111111K.",
    ".K22222222K.",
    ".K22222222K.",
    ".K22222222K.",
    ".KKKKKKKKKK.",
    "............",
    "............",
]
for _core, _rc in CORE_RECOLOR.items():
    sprite(f"material_{_core}", [r.replace("1", _rc["I"]).replace("2", _rc["i"]) for r in INGOT],
           layer="material", material=_core, category="CORE")

sprite("material_ember_resin", [
    "............",
    ".....KK.....",
    "....KYOK....",
    "....KYOK....",
    "...KYYOOK...",
    "..KYFYOORK..",
    "..KYYOOORK..",
    "..KOOOORRK..",
    "...KOORRK...",
    "....KKKK....",
    "............",
    "............",
], layer="material", material="ember_resin", category="AUGMENT")

sprite("material_frost_bloom", [
    "............",
    "....KKKK....",
    "...KcCCcK...",
    ".KKKCwCCKKK.",
    "KcCCCwwCCCcK",
    "KcCCCwwCCCcK",
    ".KKKCCCCKKK.",
    "...KcCCcK...",
    "....KKKK....",
    "............",
    "............",
    "............",
], layer="material", material="frost_bloom", category="AUGMENT")

sprite("material_stormglass", [
    "............",
    ".......KK...",
    "......KwPK..",
    ".....KwPpK..",
    "....KPPPpK..",
    "....KPPppK..",
    "...KPPpppK..",
    "...KPpppK...",
    "..KPppKK....",
    "..KppK......",
    "..KKK.......",
    "............",
], layer="material", material="stormglass", category="AUGMENT")

sprite("material_grave_dust", [
    "............",
    "...KKKKKK...",
    "..KwwwwwwK..",
    "..KwkwwkwK..",
    "...KwwwwK...",
    "....KwKwK...",
    "..KKKEEKKK..",
    ".KEEEEEdddK.",
    "KEEEEEdddddK",
    ".KKKKKKKKKK.",
    "............",
    "............",
], layer="material", material="grave_dust", category="AUGMENT")

sprite("material_verdant_sap", [
    "............",
    ".......KKK..",
    ".....KKaGK..",
    "....KaaGgK..",
    "...KaaGGgK..",
    "..KaGGGgK...",
    "..KGGggK....",
    ".KKGgKK.....",
    ".KlK........",
    "KlK.........",
    "KK..........",
    "............",
], layer="material", material="verdant_sap", category="AUGMENT")

sprite("material_sun_ash", [
    "............",
    "....KKKK....",
    "...KFFFYK...",
    "..KFwwFYYK..",
    ".KFwwFFYYYK.",
    ".KFFFFYYYYK.",
    ".KFFYYYYYyK.",
    ".KYYYYYyyyK.",
    "..KYYYyyyK..",
    "...KYyyyK...",
    "....KKKK....",
    "............",
], layer="material", material="sun_ash", category="AUGMENT")

sprite("material_binding_salt", [
    "............",
    ".....KK.....",
    "....KwLK....",
    "..KKKwLLK...",
    ".KwLKwLLsK..",
    ".KwLKwLLsKK.",
    ".KwLLKLLsKwK",
    ".KLLLKLssKLK",
    "..KKKKKKKKK.",
    "............",
    "............",
    "............",
], layer="material", material="binding_salt", category="CATALYST")

sprite("material_runestone_shard", [
    "............",
    "....KKK.....",
    "...KsLLK....",
    "..KsLPLSK...",
    "..KsLPPSK...",
    "..KsLPLSK...",
    "..KsLPPSK...",
    "...KSSSK....",
    "....KKK.....",
    "............",
    "............",
    "............",
], layer="material", material="runestone_shard", category="CATALYST")

sprite("material_dragon_oil", [
    "............",
    ".....KlK....",
    ".....KLK....",
    ".....KLK....",
    "....KKLKK...",
    "...KwLOOyK..",
    "..KwOOOOyK..",
    "..KOOOOyyK..",
    "..KOOyyyyK..",
    "...KKKKKK...",
    "............",
    "............",
], layer="material", material="dragon_oil", category="CATALYST")

sprite("material_void_ink", [
    "............",
    "....KKKK....",
    "....KkkkK...",
    ".....KQK....",
    "...KKKQKKK..",
    "..KJQQQQQQK.",
    ".KJpQQQQQQQK",
    ".KJQQQQQQQQK",
    ".KJQQQQQQQQK",
    "..KJQQQQQQK.",
    "...KKKKKKK..",
    "............",
], layer="material", material="void_ink", category="CATALYST")

# ---------------------------------------------------------------- blessing icons (12x12)
sprite("blessing_forgefire", [
    "............",
    "......K.....",
    ".....KOK....",
    "....KOYOK...",
    "...KOYYOK...",
    "..KOYFYOOK..",
    "..KOYFYOORK.",
    "..KROYYORRK.",
    "..KRROOORRK.",
    "...KRRRRRK..",
    "....KKKKK...",
    "............",
], layer="blessing", blessing="forgefire")

sprite("blessing_tireless_hands", [
    "............",
    ".KKKKKKKKK..",
    ".KLLLLLssK..",
    ".KLsssssSK..",
    ".KssssssSK..",
    "..KKKlKKKK..",
    "....KlK.....",
    "....KlK.....",
    "....KlK.....",
    "....KlK.....",
    "....KKK.....",
    "............",
], layer="blessing", blessing="tireless_hands")

sprite("blessing_merchants_favor", [
    "............",
    "...KKKKKK...",
    "..KFYYYYyK..",
    "..KYyyyyyK..",
    "..KKKKKKKK..",
    "..KFYYYYyK..",
    "..KYyyyyyK..",
    "..KKKKKKKK..",
    "..KFYYYYyK..",
    "..KYyyyyyK..",
    "...KKKKKK...",
    "............",
], layer="blessing", blessing="merchants_favor")

sprite("blessing_runic_insight", [
    "............",
    "............",
    "....KKKK....",
    "..KKwwwwKK..",
    ".KwwKPPKwwK.",
    "KwwKPpkPKwwK",
    ".KwwKppKwwK.",
    "..KKwwwwKK..",
    "....KKKK....",
    "............",
    "............",
    "............",
], layer="blessing", blessing="runic_insight")

sprite("blessing_stalwart_town", [
    "............",
    ".KKKKKKKKKK.",
    ".KLLLqqsssK.",
    ".KLLLqqsssK.",
    ".KqqqqqqqqK.",
    ".KLLLqqsssK.",
    "..KLLqqssK..",
    "..KLLqqssK..",
    "...KLqqsK...",
    "....KqqK....",
    ".....KK.....",
    "............",
], layer="blessing", blessing="stalwart_town")

sprite("blessing_hunters_edge", [
    ".....KK.....",
    "....KwIK....",
    "...KwIIiK...",
    "..KwIIIiiK..",
    "..KKKlhKKK..",
    "....KlhK....",
    "....KlhK....",
    "....KlhK....",
    "..KKKlhKKK..",
    ".KLLKlhKLsK.",
    ".KLLKlhKLsK.",
    "..KKKKKKKK..",
], layer="blessing", blessing="hunters_edge")

sprite("blessing_lucky_alloy", [
    "............",
    "..KKK..KKK..",
    ".KaGGKKGGgK.",
    ".KaGGGGGGgK.",
    "..KKGGGGKK..",
    ".KaGGGGGGgK.",
    ".KaGGKKGGgK.",
    "..KKKKgKKK..",
    ".....KgK....",
    "......KgK...",
    ".......KK...",
    "............",
], layer="blessing", blessing="lucky_alloy")

sprite("blessing_guild_patronage", [
    "............",
    "KKKKKKKKKKKK",
    "KllllllllllK",
    "KKKKKKKKKKKK",
    ".KqqqYYqqqK.",
    ".KqqYYYYqqK.",
    ".KqqqYYqqqK.",
    ".KqqqqqqqqK.",
    ".KqqKKKKqqK.",
    "..KqK..KqK..",
    "..KKK..KKK..",
    "............",
], layer="blessing", blessing="guild_patronage")

# ---------------------------------------------------------------- embers (8x8, 3 frames, 180 ms each)
sprite("ember_0", [".O......", "........", "....Y...", "........", "........", "..O.....", "......O.", "........"], layer="fx", frames=1)
sprite("ember_1", ["........", "..Y.....", "........", "......O.", ".O......", "........", "....Y...", "........"], layer="fx", frames=1)
sprite("ember_2", ["......Y.", "........", ".O......", "........", "....O...", "........", "........", "..Y....."], layer="fx", frames=1)


# ---------------------------------------------------------------- procedural scene pieces
def blank(w, h):
    return [["."] * w for _ in range(h)]


def rect(g, x, y, w, h, c):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            if 0 <= yy < len(g) and 0 <= xx < len(g[0]):
                g[yy][xx] = c


def rows(g):
    return ["".join(r) for r in g]


def wall_tile():
    g = blank(16, 16)
    rect(g, 0, 0, 16, 16, "D")
    for row in range(2):
        off = 0 if row == 0 else 4
        for bx in range(-1, 3):
            x = bx * 8 + off
            rect(g, x, row * 8, 7, 7, "B")
            rect(g, x, row * 8, 7, 1, "b")   # lit top edge
            rect(g, x, row * 8, 1, 7, "b")   # lit left edge
    return rows(g)


def floor_tile():
    g = blank(16, 8)
    rect(g, 0, 0, 16, 8, "b")
    rect(g, 0, 0, 16, 1, "W")
    rect(g, 0, 7, 16, 1, "B")
    rect(g, 5, 0, 1, 8, "B")
    rect(g, 11, 0, 1, 8, "B")
    return rows(g)


def furnace(state: str):
    g = blank(24, 24)
    rect(g, 2, 2, 20, 22, "K")
    rect(g, 3, 3, 18, 20, "S")
    rect(g, 3, 3, 18, 1, "s"); rect(g, 3, 3, 1, 20, "s")
    rect(g, 4, 0, 16, 3, "K"); rect(g, 5, 1, 14, 2, "S")       # chimney cap
    rect(g, 6, 9, 12, 10, "K"); rect(g, 7, 10, 10, 8, "k")       # mouth
    rect(g, 3, 20, 18, 3, "B")                                   # hearth base
    if state == "warm":
        rect(g, 8, 14, 8, 3, "R"); rect(g, 10, 13, 4, 2, "O")
    if state == "hot":
        rect(g, 8, 12, 8, 5, "R"); rect(g, 9, 11, 6, 4, "O"); rect(g, 11, 10, 2, 3, "Y"); rect(g, 10, 12, 4, 2, "F")
    return rows(g)


def anvil():
    g = blank(24, 12)
    rect(g, 2, 2, 20, 4, "K"); rect(g, 3, 3, 18, 2, "s"); rect(g, 3, 3, 18, 1, "L")
    rect(g, 0, 3, 3, 2, "K"); rect(g, 1, 3, 2, 1, "s")           # horn
    rect(g, 8, 6, 8, 3, "K"); rect(g, 9, 6, 6, 3, "S")
    rect(g, 5, 9, 14, 3, "K"); rect(g, 6, 9, 12, 2, "S"); rect(g, 6, 9, 12, 1, "s")
    return rows(g)


def tool_rack():
    g = blank(12, 24)
    rect(g, 0, 0, 12, 24, "K"); rect(g, 1, 1, 10, 22, "b"); rect(g, 1, 1, 10, 1, "W"); rect(g, 1, 1, 1, 22, "W")
    for x in (3, 6, 9):
        rect(g, x, 3, 1, 14, "L"); rect(g, x - 1, 2, 3, 2, "s")   # hammer/tong handles
    rect(g, 2, 19, 8, 2, "D")
    return rows(g)


def shelf():
    g = blank(32, 12)
    rect(g, 0, 8, 32, 4, "K"); rect(g, 1, 9, 30, 2, "W"); rect(g, 1, 9, 30, 1, "T")
    rect(g, 0, 0, 2, 9, "K"); rect(g, 30, 0, 2, 9, "K")
    for x in (8, 16, 24):
        rect(g, x, 1, 1, 8, "B")
    return rows(g)


def paper_tile():
    g = blank(16, 16)
    rect(g, 0, 0, 16, 16, "w")
    for (x, y) in ((3, 2), (11, 5), (6, 9), (13, 12), (1, 14)):
        g[y][x] = "T"
    return rows(g)


def panel_gazette():
    g = blank(24, 24)
    rect(g, 0, 0, 24, 24, "K")
    rect(g, 1, 1, 22, 22, "w")
    rect(g, 3, 3, 18, 1, "D"); rect(g, 3, 20, 18, 1, "D")        # inner rule (double-rule newspaper border)
    rect(g, 3, 3, 1, 18, "D"); rect(g, 20, 3, 1, 18, "D")
    for (x, y) in ((2, 2), (21, 2), (2, 21), (21, 21)):
        g[y][x] = "T"                                             # faded corner nicks
    return rows(g)


def panel_journal():
    g = blank(24, 24)
    rect(g, 0, 0, 24, 24, "K")
    rect(g, 1, 1, 22, 22, "b")
    rect(g, 1, 1, 22, 1, "W"); rect(g, 1, 1, 1, 22, "W")          # lit top/left edge
    rect(g, 1, 22, 22, 1, "h"); rect(g, 22, 1, 1, 22, "h")        # shaded bottom/right edge
    for i in range(4, 20, 2):                                     # stitching
        g[3][i] = "y"; g[20][i] = "y"; g[i][3] = "y"; g[i][20] = "y"
    for (x, y) in ((2, 2), (21, 2), (2, 21), (21, 21)):
        g[y][x] = "Z"                                             # bronze corner rivets
    return rows(g)


sprite("tile_wall", wall_tile(), layer="background", tile=True)
sprite("tile_floor", floor_tile(), layer="background", tile=True)
sprite("tile_paper", paper_tile(), layer="ui", tile=True)
sprite("panel_gazette", panel_gazette(), layer="ui", tile=True)
sprite("panel_journal", panel_journal(), layer="ui", tile=True)
sprite("furnace_cold", furnace("cold"), anchor="bottom-left", layer="scene", state="cold")
sprite("furnace_warm", furnace("warm"), anchor="bottom-left", layer="scene", state="warm")
sprite("furnace_hot", furnace("hot"), anchor="bottom-left", layer="scene", state="hot")
sprite("anvil", anvil(), anchor="bottom-left", layer="scene")
sprite("tool_rack", tool_rack(), anchor="top-left", layer="scene")
sprite("shelf", shelf(), anchor="bottom-left", layer="scene")


def render(rows_: list[str], recolor: dict[str, str] | None = None) -> Image.Image:
    w, h = len(rows_[0]), len(rows_)
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows_):
        for x, ch in enumerate(row):
            ch = (recolor or {}).get(ch, ch)
            rgb = PALETTE[ch]
            if rgb is not None:
                px[x, y] = (*rgb, 255)
    return img


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    manifest = {"version": 1, "pixelGrid": "1px = 1 game pixel; scale with nearest-neighbour only", "lightSource": "upper-left", "sprites": []}
    written = 0

    # Hand-made sprites imported by import_assets.py win over placeholders: keep their file, record their real size.
    overrides_path = Path(__file__).with_name("overrides.json")
    overrides = json.loads(overrides_path.read_text()) if overrides_path.exists() else {}
    kept = 0

    def emit(name, img, meta):
        nonlocal written, kept
        path = OUT_DIR / f"{name}.png"
        if name in overrides and path.exists():
            o = overrides[name]
            manifest["sprites"].append({"id": name, "file": f"drawable-nodpi/{name}.png", **meta, "width": o["width"], "height": o["height"], "source": "handmade"})
            kept += 1
            return
        img.save(path, optimize=True)
        manifest["sprites"].append({"id": name, "file": f"drawable-nodpi/{name}.png", **meta, "source": "generated"})
        written += 1

    for name, rows_ in SPRITES.items():
        emit(name, render(rows_), {k: v for k, v in META[name].items()})

    # Hand-made sprites that have no placeholder counterpart (portrait variants, icons, faction alts, ember_3).
    listed = {s["id"] for s in manifest["sprites"]}
    for name, o in sorted(overrides.items()):
        if name in listed or not (OUT_DIR / f"{name}.png").exists():
            continue
        layer = o.get("kind") or name.split("_")[0]
        row = {"id": name, "file": f"drawable-nodpi/{name}.png", "width": o["width"], "height": o["height"], "layer": layer, "source": "handmade"}
        for k in ("anchor", "durationMs", "notes"):
            if k in o:
                row[k] = o[k]
        manifest["sprites"].append(row)
        kept += 1
    ember_frames = [f"ember_{i}" for i in range(4) if (OUT_DIR / f"ember_{i}.png").exists()]
    manifest["animations"] = [{"id": "embers", "frames": ember_frames, "frameMs": 180, "loop": True}]
    MANIFEST.write_text(json.dumps(manifest, indent=2), encoding="utf-8")

    lines = ["# Art manifest (generated by tools/pixelart/generate_assets.py)", "",
             "All sprites share one palette, an upper-left light source and 1px outlines. Scale with nearest-neighbour only.",
             "Essential text is never baked into bitmaps; Compose draws labels over the art.", "",
             "| id | size | layer | notes |", "|---|---|---|---|"]
    for s in manifest["sprites"]:
        notes = ", ".join(f"{k}={v}" for k, v in s.items() if k not in ("id", "file", "width", "height", "layer", "source")) + (" (hand-made)" if s.get("source") == "handmade" else "")
        lines.append(f"| {s['id']} | {s['width']}×{s['height']} | {s.get('layer', '')} | {notes} |")
    lines += ["", f"Animations: embers = {', '.join(ember_frames)}, 180 ms per frame, looping (disabled under reduced motion).", "",
              "Still missing for launch (P6): signature weapon variants, siege/hero milestone animations, modular portrait layers."]
    DOC.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {written} placeholder sprites, kept {kept} hand-made sprites; manifest {MANIFEST.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
