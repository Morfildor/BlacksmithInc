# Tiny Blacksmith V2 — asset inventory and release notes

**200 exact-size transparent PNGs** in the working draft, distributed as follows:

| Group | Count | Notes |
|---|---:|---|
| Forge fixtures | 8 | Includes tile wall and floor, 3 furnace heat states, anvil, rack, shelf |
| Forge/combat animations | 58 | 4 embers + 20 heroes + 30 monsters + 4 milestone FX |
| Core weapons | 36 | 6 silhouettes × 6 metals, not just palette maps |
| Existing signature weapons | 12 | Unique signature accents; filenames match provided GDD/brief |
| Element overlays | 6 | Translucent at ~60% alpha |
| Rarity badges | 6 | Shape and color differentiate rarity |
| Hero portraits | 25 | 5 classes × 5 ancestries each; 20 extra variant IDs |
| Faction enemies | 6 | 3 enemy families, grunt/elite |
| Materials | 16 | 6 cores + 6 augments + 4 catalysts |
| Blessings | 8 | Siege-success rewards |
| Navigation/status icons | 12 | Monochrome navigation, separate status icons |
| Panel frames and paper tile | 3 | 24×24 nine-patch frames; paper texture |
| Siege stages | 2 | Undamaged and damaged 96×32 |
| Hero markers | 2 | Dead / retired |

All items use a shared 48-color RGB palette, are stored as RGBA PNG, use original logical 1× pixels with nearest-neighbor 4× contact sheets, and satisfy the specified 1px margin (other than tiles, which must reach the edge). All animation frames are distinct.

**Important:** This is a **technical working draft**, not a painted conversion of the detailed V2 AI concept sheets. Pixel details must still be artist-reviewed in-device at final display scale. The `concept_references/` folder contains target art direction, **not ready-to-import sprites**.

### Pending / out of scope
- 12 additional signature weapon IDs for spears, daggers and staves have not been provided and were not invented.
- App icon, feature graphic, title art and Play Store frames are not included.
- Portrait variant lookup in Compose requires app code; the currently specified class IDs alone select the five originals.
- Optional modular layered portraits are not included.
- Animation timing/pose alignment and Android nine-patch conversion require in-app integration QA.

### Useful first previews
- `previews/master_contact.png` — indexed sprite contact sheet
- `previews/portrait_contact.png` — 5×5 race/class grid
- `previews/weapon_contact.png` and `previews/signature_contact.png`
- `previews/forge_scene_example_8x.png` — 96×48 composition, scaled with nearest neighbor
- `previews/animation_contact.png` — early-frame movement reference

### Suggested next production pass
1. Artist polish on 16×16 signature weapon differentiation; store art after sign-off.
2. Implement portrait choice seeded by hero ID so it remains stable across saves.
3. Render on phone at 2×/3× and verify silhouette, contrast, edge effects, UI interactions and reduced-motion behavior.
4. Paint 12 remaining signatures after recipe IDs are confirmed.
