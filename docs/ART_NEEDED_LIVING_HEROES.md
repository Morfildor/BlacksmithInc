# Art needed for living heroes

What the twelve peoples and the 48 enemies of the content bible (`docs/Tiny_Blacksmith_Peoples_Names_and_Siege_Bestiary.md`)
still need as art. Written 2026-10-17 from the code as it stands: `core/.../content/LaunchContent.kt` (`portraitRaces`,
`HeroClassDef.appearances`), `core/.../heroes/Appearance.kt` (`stamp`, `silhouette`) and the import pipeline
`tools/pixelart/import_assets.py`. No art is generated here; the owner supplies it (bible 15.1, design 6.3).

## 1. How a hero gets a face today

- A new hero of a known people wears a face whose people (`portraitRaces`) matches and whose class lists it
  (`HeroClassDef.appearances`). With none, core stores the stable placeholder key `silhouette_<class>`.
- The app draws that key as a neutral class silhouette (`silhouette_guardian`, `silhouette_ranger`, `silhouette_duelist`,
  `silhouette_battlemage`, `silhouette_warden`): a 64 px opaque tile with the hero set's framing and warm vignette, no face,
  one class attribute. Drawn by `tools/pixelart/generate_assets.py`, mapped in `app/.../ui/Sprites.kt`. A silhouette serves
  the base, upgraded and small looks alike.
- A hero keeps the key stored at creation. A hero already wearing a silhouette keeps it after new art arrives unless core
  re-stamps silhouettes (a core decision, design 6.3: "enable matching selection without rerolling names").

## 2. Format the pipeline takes

Same as the 20 faces in `Assets/Implemented/heroes`:

- One identity = two files, `hero_XX_<class>_base.png` and `hero_XX_<class>_upgraded.png` (the earned look after a title or
  5 kills, `heroUpgraded`), listed in that folder's `manifest.json` (`id`, `class`, `race_concept`, `states`).
- Opaque square, 64 px native (or a larger render that the importer reduces to 64 px and 96 colours). No transparency,
  no text, the head whole inside the tile; a face that is small in the tile gets a 48 px `_sm` crop (`HERO_SMALL`).
- Numbering continues from `hero_21`. `python tools/pixelart/import_assets.py --heroes <folder>` imports them as
  `portrait_hero_XX` / `_up` and regenerates `PortraitArt.kt`.
- Wiring a face to a people and a class is a core content edit: add the key to the class's `appearances` and to
  `portraitRaces` (and `Appearance.legacyFaces` is never touched).

## 3. Coverage now (faces per people and class)

| People | Guardian | Ranger | Duelist | Battlemage | Warden | Identities |
|---|---|---|---|---|---|---|
| Human | 01, 11, 16 | 12 | 13 | 08, 19 | none | 7 |
| Elf | 06 | 17 | 10 | 04, 14 | 05, 15, 20 | 8 |
| Dwarf | none | none | none | none | none | 0 |
| Halfling | none | none | 18 | none | none | 1 |
| Gnome | none | none | none | none | none | 0 |
| Orc | 09 | 07 | none | none | none | 2 |
| Goblin | none | none | none | none | none | 0 |
| Dragonkin | none | none | none | none | none | 0 |
| Hornborn | none | none | 03 | none | none | 1 |
| Stonekin | none | none | none | none | none | 0 |
| Catfolk | none | none | none | none | none | 0 |
| Lizardfolk | none | 02 | none | none | none | 1 |

14 of the 60 people-and-class cells have a face; the other 46 show the class silhouette.

The people tags are the owner's best fit (2026-10-17). The folder's own `race_concept` differs for several faces and may
be worth a second look before more art is commissioned: 01 and 11 are drawn as Dwarf (tagged Human), 07 as Goblin (tagged
Orc), 03 as Tiefling (Hornborn), 04 as Undead, 05 as Dryad, 10 as Fae Elf, 14 as Drow, 15 as Horned Forest Elf and 20 as
Sylvan Fae (all tagged Elf). Retagging 01, 11 and 07 would give Dwarf two Guardians and Goblin a Ranger at no art cost.

## 4. What to make

The bible (15.2) asks for at least two identities per people before that people is enabled by default, ideally four,
each with a base and an upgraded look; vary age, build, skin, fur or scale, hair, expression and scars within a people,
and let clothing carry the class.

### 4.1 First batch: two identities for every people (15 identities, 30 files)

| People | Has | Needs | Suggested classes (cells with no face yet) |
|---|---|---|---|
| Dwarf | 0 | 2 | Guardian, Warden |
| Gnome | 0 | 2 | Battlemage, Ranger |
| Goblin | 0 | 2 | Ranger, Duelist |
| Dragonkin | 0 | 2 | Guardian, Battlemage |
| Stonekin | 0 | 2 | Guardian, Warden |
| Catfolk | 0 | 2 | Ranger, Duelist |
| Halfling | 1 | 1 | Ranger |
| Hornborn | 1 | 1 | Battlemage |
| Lizardfolk | 1 | 1 | Warden |

### 4.2 Full coverage: one face for every cell (46 identities, 92 files)

- Human: Warden
- Elf: none missing
- Dwarf: Guardian, Ranger, Duelist, Battlemage, Warden
- Halfling: Guardian, Ranger, Battlemage, Warden
- Gnome: Guardian, Ranger, Duelist, Battlemage, Warden
- Orc: Duelist, Battlemage, Warden
- Goblin: Guardian, Ranger, Duelist, Battlemage, Warden
- Dragonkin: Guardian, Ranger, Duelist, Battlemage, Warden
- Hornborn: Guardian, Ranger, Battlemage, Warden
- Stonekin: Guardian, Ranger, Duelist, Battlemage, Warden
- Catfolk: Guardian, Ranger, Duelist, Battlemage, Warden
- Lizardfolk: Guardian, Duelist, Battlemage, Warden

Until a cell has a face, its heroes keep the silhouette: honest, never another people's face.

## 5. Enemies (48 identities, asset ID = content ID)

The bible's 36 standard enemies (section 10), 6 captains (11), 3 siege engines and 3 major monsters (12). Asset ID is the
content ID. Priority batches are the bible's (15.4): A core variety, B new decisions, C large threats, D headline
encounters. Engine crews appear inside the engine; a swarm is one illustration; a rider is mounted.

| Asset ID | Enemy | Group | Batch |
|---|---|---|---|
| ib_goblin_skirmisher | Goblin Skirmisher | Iron Banner | A |
| ib_goblin_sapper | Goblin Sapper | Iron Banner | B |
| ib_wolf_rider | Goblin Wolf Rider | Iron Banner | C |
| ib_hobgoblin_shield | Hobgoblin Shieldbearer | Iron Banner | A |
| ib_hobgoblin_archer | Hobgoblin Archer | Iron Banner | A |
| ib_war_drummer | War Drummer | Iron Banner | B |
| ib_orc_breaker | Orc Gatebreaker | Iron Banner | B |
| ib_orc_shaman | Orc War Shaman | Iron Banner | C |
| ib_bugbear | Bugbear Ambusher | Iron Banner | C |
| ib_ogre | Ogre Ram-Bearer | Iron Banner | C |
| ib_troll | Troll Mercenary | Iron Banner | C |
| ib_deserter | Deserter Crossbowman | Iron Banner | A |
| pp_skeleton_guard | Skeleton Spearman | Pale Procession | A |
| pp_skeleton_archer | Skeleton Archer | Pale Procession | A |
| pp_zombie | Zombie | Pale Procession | A |
| pp_ghoul | Ghoul | Pale Procession | B |
| pp_wight | Wight | Pale Procession | C |
| pp_necromancer | Necromancer | Pale Procession | C |
| pp_acolyte | Grave Acolyte | Pale Procession | B |
| pp_ogre_zombie | Ogre Zombie | Pale Procession | C |
| pp_banshee | Banshee | Pale Procession | C |
| pp_wraith | Wraith | Pale Procession | C |
| pp_black_knight | Undead Black Knight | Pale Procession | C |
| pp_grave_rats | Grave Rat Swarm | Pale Procession | C |
| tc_dire_wolf | Dire Wolf | Thorn Covenant | A |
| tc_giant_spider | Giant Spider | Thorn Covenant | B |
| tc_harpy | Harpy | Thorn Covenant | B |
| tc_minotaur | Minotaur | Thorn Covenant | C |
| tc_manticore | Manticore | Thorn Covenant | C |
| tc_wyvern | Wyvern | Thorn Covenant | C |
| tc_treant | Thornbound Treant | Thorn Covenant | C |
| tc_earth_elemental | Earth Elemental | Thorn Covenant | C |
| tc_fire_elemental | Fire Elemental | Thorn Covenant | C |
| tc_giant_serpent | Giant Serpent | Thorn Covenant | C |
| tc_werewolf | Werewolf | Thorn Covenant | C |
| tc_beastcaller | Beastcaller | Thorn Covenant | B |
| cap_iron_marshal | Varka the Iron Marshal | Captain | D |
| cap_goblin_chief | Nibba Fuse-Ear | Captain | D |
| cap_lich | The Bellkeeper | Captain | D |
| cap_death_knight | Sir Carrow, the Unburied | Captain | D |
| cap_elder_treant | Old Root-Crown | Captain | D |
| cap_beast_matriarch | The White-Fang Matriarch | Captain | D |
| eng_battering_ram | Covered Battering Ram | Siege engine | D |
| eng_ballista | Field Ballista | Siege engine | D |
| eng_corpse_catapult | Corpse Catapult | Siege engine | D |
| boss_hill_giant | Hill Giant | Major monster | D |
| boss_bone_colossus | Bone Colossus | Major monster | D |
| boss_ash_dragon | Ash Dragon | Major monster | D |

Size and frame for enemy art are not fixed yet: the enemy inspect card and the replay draw a 64 px tile today (faction
sprites), so a 64 px opaque tile in the hero set's framing is the safe default until the enemy card is designed.
