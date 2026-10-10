# Blacksmith Inc. — Complete wanted icon pack

Includes **21 individually named transparent PNG icons** matching the IDs in `WANTED(5).txt`.

- **16 replacements** (`category: replacement` in `manifest.json`): replace current placeholder icons.
- **5 optional visitor-update icons** (`category: optional`): wire up only when the corresponding game UI exists.
- **No previously delivered icons are included.** In particular, existing resource/nav icons and the eight action icons should remain untouched.
- **Use the exact asset IDs** as filenames, e.g. `icon_integrity.png`.
- **All images are exported at 2× the in-game size.** The `game_size` field records the intended final visual dimensions: 8×8, 24×24, or 40×40 logical game pixels. The PNG itself is 16×16, 48×48, or 80×80.
- Keep the PNG alpha channel; **do not** add a colored rectangle behind the icons. The preview uses a dark background only to demonstrate legibility.
- Icons use consistent dark ink outlines, weathered metal, warm brass, saturated enamel and fantasy details. The two 8px hero markers are intentionally simplified/pixel crisp for legibility.
- `PREVIEW.png` is a visual index, not a sprite atlas to display in the game.

## Suggested Claude Code implementation instruction

Unzip this pack into a temporary asset staging location. Read `manifest.json`. For each icon with `category: replacement`, locate its exact call site and replace only the placeholder with the matching new transparent PNG; preserve the app's existing size/tint semantics and accessible labels (do not apply a tint that obscures multicolor art). For optional icons, connect only where the new screen already uses a text placeholder, if present. Use the project's existing asset pipeline/packing convention, not assumptions about `res/drawable`. Verify names, dimensions, alpha and dark/light UI rendering, and run affected tests. Do not replace any assets excluded from this pack.
