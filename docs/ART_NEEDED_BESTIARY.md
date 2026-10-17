# Art needed: the siege bestiary

The 48 enemies of `content/Bestiary.kt` (content bible sections 10 to 12) have no art yet. Each one's `assetId` is its
unit ID; until an imported sprite exists for that ID, `tools/pixelart/generate_assets.py` draws a placeholder. No art is
generated for them here. The briefs are one line each from the bible's "Recognition/art" and "Silhouette" notes; the
shared style and reuse groups are in the bible's section 15 and `docs/ART_BRIEF.md`.

## Iron Banner (Ashclaw Raiders): red cloth, dark iron, patched leather, stolen trade seals

| assetId | Name | Visual brief |
| --- | --- | --- |
| ib_goblin_skirmisher | Goblin Skirmisher | Small goblin with a hooked short blade, a scavenged buckler and a red armband. |
| ib_goblin_sapper | Goblin Sapper | Goblin with a satchel of powder, fuse cord and a short pick. No modern explosives. |
| ib_wolf_rider | Goblin Wolf Rider | Goblin riding a lean wolf, short spear, red saddlecloth. One figure, not two. |
| ib_hobgoblin_shield | Hobgoblin Shieldbearer | Broad rectangular shield, disciplined stance, red and black uniform. |
| ib_hobgoblin_archer | Hobgoblin Archer | Longbow, spare arrows, matching armour plates. |
| ib_war_drummer | War Drummer | A war drum carried on the body, mallets, cloth hearing protection. Any race. |
| ib_orc_breaker | Orc Gatebreaker | Orc in salvaged heavy work armour with a maul. |
| ib_orc_shaman | Orc War Shaman | Staff with tied bells, red ritual cloth, bone and wood charms. |
| ib_bugbear | Bugbear Ambusher | Tall shaggy humanoid with long arms, a heavy hood and an oversized knife. |
| ib_ogre | Ogre Ram-Bearer | Huge thick-limbed humanoid carrying a tree-trunk ram, crude helmet. |
| ib_troll | Troll Mercenary | Lanky long-armed giant, hooked nose, rough green-grey skin, regeneration scars. |
| ib_deserter | Deserter Crossbowman | Mixed town armour with the insignia removed, windlass crossbow, red contract ribbon. |

## Pale Procession (Hollowbound): bone white, dark violet cloth, tarnished bronze, cold lanterns

| assetId | Name | Visual brief |
| --- | --- | --- |
| pp_skeleton_guard | Skeleton Spearman | Bare bones, rusted spear, small battered shield. |
| pp_skeleton_archer | Skeleton Archer | Bony hands on an old bow, an arrow bundle tied to the ribs. |
| pp_zombie | Zombie | Clothed walking corpse, slack posture, grasping hands. Readable without gore. |
| pp_ghoul | Ghoul | Gaunt corpse-creature, long claws, hunched hungry posture. |
| pp_wight | Wight | Preserved undead warrior, faded funeral armour, cold glowing eyes. |
| pp_necromancer | Necromancer | Living robed caster, bone staff, a ledger or funeral tags, ritual mask. |
| pp_acolyte | Grave Acolyte | Living attendant with a black lantern and an ash-white shawl. |
| pp_ogre_zombie | Ogre Zombie | Enormous corpse with a broken helm and a chained timber beam. |
| pp_banshee | Banshee | Translucent mourning figure, torn veil, open mouth, cold mist. |
| pp_wraith | Wraith | Hooded smoke-like figure, visible hands, one bright line for its eyes. |
| pp_black_knight | Undead Black Knight | Sealed dark plate, broken heraldry, a skeletal hand on a broad sword. |
| pp_grave_rats | Grave Rat Swarm | Several rats around a broken grave lantern, one silhouette. |

## Thorn Covenant (Embermaw Brood): binding cords, carved horns, Covenant marks

| assetId | Name | Visual brief |
| --- | --- | --- |
| tc_dire_wolf | Dire Wolf | Oversized wolf, thick neck, scarred muzzle, a simple binding cord. |
| tc_giant_spider | Giant Spider | Large eight-legged spider, readable fangs, a pale web bundle. |
| tc_harpy | Harpy | Winged humanoid with taloned legs and a prominent feather ruff at the throat. |
| tc_minotaur | Minotaur | Bull-headed humanoid, thick horns, shoulder armour, large axe. |
| tc_manticore | Manticore | Leonine body, a humanlike face, bat wings, obvious tail spikes. |
| tc_wyvern | Wyvern | Two legs, two wings, a long venomous tail. Not a four-legged dragon. |
| tc_treant | Thornbound Treant | Moving old tree, branch arms, roots for feet, binding runes tied into the bark. |
| tc_earth_elemental | Earth Elemental | Compact boulder body, separate rock limbs, glowing cracks, no ornate armour. |
| tc_fire_elemental | Fire Elemental | Upright flame silhouette with a dark ember core. |
| tc_giant_serpent | Giant Serpent | Massive coiled snake, visible fangs, the head readable above the coil. |
| tc_werewolf | Werewolf | Upright wolf-human with torn clothing and clawed hands. |
| tc_beastcaller | Beastcaller | Humanoid with a carved horn, animal-call cords and Covenant bindings. Any race. |

## Captains

| assetId | Name | Visual brief |
| --- | --- | --- |
| cap_iron_marshal | Varka the Iron Marshal | Orc officer with a tall rectangular banner on the back, a broad shield, a dented officer's helm. |
| cap_goblin_chief | Nibba Fuse-Ear | Goblin with oversized leather ear guards, a scorched cap and a bundle of short fuses. |
| cap_lich | The Bellkeeper | Tall skeletal caster with a cracked chapel bell in a frame and hanging funeral tags. |
| cap_death_knight | Sir Carrow, the Unburied | Broad dark plate, a broken tower emblem, pale light behind the visor. |
| cap_elder_treant | Old Root-Crown | Ancient treant with crownlike broken branches, hanging rope bindings, a broad hollow trunk. |
| cap_beast_matriarch | The White-Fang Matriarch | Massive wolf with a white facial stripe and a broken iron collar. |

## Engines and great monsters

Drawn 2026-10-17 with Retro Diffusion and waiting in `Assets/Enemies/` (112x112), not wired: all six below.

| assetId | Name | Visual brief |
| --- | --- | --- |
| eng_battering_ram | Covered Battering Ram | Timber roof over a suspended iron-headed ram, the crew's feet visible below. |
| eng_ballista | Field Ballista | Large wooden crossbow with an iron bolt and a winding crew. |
| eng_corpse_catapult | Corpse Catapult | Crude throwing frame, a shrouded load, a living masked crew. Stylized, no gore. |
| boss_hill_giant | Hill Giant | Towering humanoid with a boulder sling and a gate-sized club. Scale must read in a portrait. |
| boss_bone_colossus | Bone Colossus | Giant of bound bones: a large skull cluster, a ribcage frame, one glowing binder seal. |
| boss_ash_dragon | Ash Dragon | Four-legged winged young dragon, dark ash-grey scales, a fire-scarred throat. |
