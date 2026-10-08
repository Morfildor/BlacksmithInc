# Tiny Blacksmith — V2 production starter pack

## What this is
Genuine 1x integer-grid RGBA pixel sprites, drawn programmatically from deliberately authored shapes.
This is an integration-ready **working sprite draft**, not a pixel-perfect extraction of the earlier AI concept boards. The concept boards are visual references; those enlarged images cannot safely be sliced into 16x16 game sprites without repainting.

## Files
- `drawable-nodpi/`: exactly named individual PNG assets.
- `manifest.json`: IDs, sizes, classes, anchors, duration metadata, notes.
- `previews/`: nearest-neighbor contact sheets with labeled IDs.
- `palette.gpl`: coherent palette editable in most pixel tools.
- `tools/build_assets.py`: full editable pixel source; rerun with Pillow installed.

## Integration
Copy `drawable-nodpi/*.png` to `app/src/main/res/drawable-nodpi/`. Disable the placeholder generator only for IDs you replace, and preserve the renderer's fixed anchor and nearest-neighbor settings.
The five `portrait_<class>` IDs match the art brief. Four additional variants per class use `portrait_<class>_1..4`: **the game must select one of these IDs explicitly**; it will not use variants without that code change.

## Deliberate boundaries
Only the initial 12 named signatures are included. The brief intentionally leaves 12 additional signatures unnamed, so none were fabricated.
Animations and portrait variants are experimental; verify poses and layering in the Compose renderer. `panel_gazette` and `panel_journal` use 8px borders but may need Android nine-patch conversion depending on implementation.
The existing GDD has no established species/race rules: races here are artistic variation, not invented game mechanics.

## Art direction
Style F: clean 16-bit readability + hand-made forge personality. Upper-left highlights. All text remains UI-rendered. Strong silhouettes, restrained world colors, saturated metals and spell sparks.

## Verification
Run `python tools/build_assets.py` (Pillow required) and inspect `previews/master_contact.png` at 1x and enlarged. All PNGs use RGBA and exact logical pixel dimensions.
