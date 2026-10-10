Tiny Blacksmith: art and audio sources

Put replacement assets in the folder for their kind. Each folder has a WANTED.txt listing the IDs the game uses
today and their in-game size. Tell Claude when a folder is ready; it is then imported by
tools/pixelart/import_assets.py and every piece is checked in the running app before it is switched on.

Folders: Heroes, Weapons, Materials, Blessings, Relics, Enemies, Icons, Backgrounds, Scene, Effects, UI, Audio, AppIcon

Rules of thumb
- One file per piece named <id>.png, or a sheet with a manifest.json describing its cells (as in Implemented/heroes/).
- Keep one consistent style and pixel density per folder; mixed styles at one screen were rejected before.
- Nothing here is edited by the importer: it only reads these files and writes game drawables elsewhere.
- This folder is in Git since 2026-10-10 (owner decision).
- A WANTED.txt may end with a "New with ... update" section: pieces a newer feature could use that are not in the
  game yet. They are optional; the feature works without them and nothing is drawn in their place.
- Everything directly in these folders is NOT in the game yet. Pieces that are in the game live in
  Assets/Implemented/<name> (the importer reads them from there): day_art, heroes, icons, materials.
  Each WANTED.txt says what is delivered and in the game (do not resend), what is delivered but waiting for a place in
  the screens, and what is still wanted. Hand the WANTED.txt of a folder to the artist as it is.
