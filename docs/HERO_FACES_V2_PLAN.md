# Hero faces v2: every portrait in the approved painterly style (2026-10-17)

Status 2026-10-17: phase 1 done and imported. All 35 faces (01-35) have a base and an upgraded look on a setting; the
owner asked to fix the weak ones (upgrades of 01, 06, 18, 33 redone stronger; 12 redrawn less cartoonish; 32 reframed)
and wire them in. Phase 2 (36-66, every people-and-class cell) done and imported the same day. Working files, prompts and request IDs per hero stayed in the session scratchpad (`heroes_v2/<ID>/notes.json`).

Owner direction 2026-10-17: "We will remake all the hero images with this style … create very unique looking heroes
according to requirements", after approving the dwarf Warden (hero_21). Requirements: content bible
`docs/Tiny_Blacksmith_Peoples_Names_and_Siege_Bestiary.md` §3 (look of each people) and §15 (shared style, two identities
per people, vary age, face, tone, hair, expression, scars; clothing carries the class); `docs/ART_BRIEF.md` (upgraded state
differs in colour or silhouette, not only armour detail); `docs/ART_NEEDED_LIVING_HEROES.md` §4.1.

## Format and recipe

- 64×64 native, opaque, no text, head whole in the tile (the importer takes 64 px files as they are).
- Base: Retro Diffusion `rd_pro__painterly`, 64×64, a stated backdrop colour, "no text". $0.18.
- Upgraded: `rd_pro__edit` on the base, strength about 0.5, "keep the exact same character, pose, framing, bright lighting
  and colours" plus the row's upgraded accent (a glow colour or trim). $0.18.
- Generated into the scratchpad first; nothing enters `Assets/` until the owner has seen contact sheets per people.

## Scope

Phase 1 (this plan, 33 identities, 66 images, about $12 to $15 with retries):
- Remake heroes 01-20 in place: same ID, same class, drawn as the people `LaunchContent.portraitRaces` tags them (not the
  manifest's older `race_concept`; that field is corrected at import). Living heroes in existing saves keep their key and
  show the new face.
- 13 new identities 23-35 so every people has at least two (§4.1).
- 21 and 22 (dwarves) stay.
Phase 2 (owner 2026-10-17, done): one face for every remaining people-and-class cell, 31 identities 36-66 (below).

## Variety rules

Poses: P1 three-quarter left, eyes to viewer · P2 three-quarter right, eyes to viewer · P3 straight on, chin raised ·
P4 turned left, glancing back over the shoulder · P5 head lowered, eyes up from under the brows · P6 holding the class
weapon or focus upright beside the face · P7 one hand raised in a gesture · P8 weapon or tool resting across the shoulder.
No pose repeats within a class. Backdrops are mid-dark hues; no backdrop repeats within a class. Owner 2026-10-17:
"Backgrounds could be more interesting. Keep the current ones though": faces generated after that note get a softly
blurred, low-contrast setting that fits the class (rampart, forest, lantern-lit alley, library, grove…) tinted with the
row's backdrop hue; faces already accepted keep their plain backdrop. Each people's §3 art
directions are spread over its faces; gender presentation and age alternate.

## Format standard (owner 2026-10-17: "same size, style and formatting"; "as long as all are interesting and unique")

- 64×64, opaque RGB, full-bleed: no frame, border or picture-in-picture.
- Chest-up bust: the head about a third of the tile wide, its top 3-10 px below the top edge, shoulders running off the
  bottom edge. A face drawn from further away (32's first base) is regenerated.
- `rd_pro__painterly`, warm light from the upper left, a mid-dark finish like the dwarves (mean brightness roughly 65-95
  of 255); no cartoon exaggeration.
- Every face has a softly blurred, low-contrast setting that fits its class, tinted with the row's backdrop hue, and no
  setting repeats within a class (table below). Faces made on a plain backdrop get theirs by `rd_pro__edit` (strength
  about 0.5): "Keep the hero exactly the same: face, pose, clothing, colours. Replace only the plain background with a
  softly blurred <setting>, tinted <hue>, low contrast, darker than the face". 02, 12, 17 and 23 add "slightly darker,
  moodier lighting" to the same edit. Upgraded faces are made after this, from the final base.

| Class | Settings (ID: setting) |
|---|---|
| Guardian | 01 harbour sea wall with lanterns · 06 town gate with banners · 11 barracks hall · 27 torchlit castle rampart · 09 stone wall with a hanging shield · 16 watchtower at dusk · 29 fortress courtyard |
| Ranger | 02 reed marsh · 07 misty pine forest · 12 sunlit meadow edge · 17 snowy mountain pass · 24 cliff trail at dawn · 25 rain-soaked forest road · 31 moonlit glade · 33 autumn birch woods |
| Duelist | 03 city roofs at dusk · 10 lantern-lit alley · 13 velvet theatre curtain · 26 candlelit fencing hall · 32 as drawn · 18 tavern with a fire |
| Battlemage | 04 night sky over ruins · 22 drifting ember sparks · 19 candlelit arcane study · 23 workshop with glowing jars · 28 storm clouds over a cliff · 34 library shelves with faint floating runes · 08 observatory under the stars · 14 mountain peak with lightning |
| Warden | 21 mossy grove with fireflies · 05 flowering orchard · 35 riverbank with reeds · 30 ancient tree roots · 20 mountain meadow with flowers · 15 stone circle in mist |

## Matrix

| ID | People | Class | Look | Age / presentation | Expression | Pose | Backdrop | Upgraded accent |
|---|---|---|---|---|---|---|---|---|
| 01 | human | guardian | tired defender, auburn braid, dented steel plate, round shield | middle-aged woman | tired, steady | P1 | slate blue | silver-white plate trim, pale blue shield glow |
| 06 | elf | guardian | woodland palette, copper skin, cropped hair, bronze leaf-pattern armour, kite shield | adult woman | alert | P2 | ochre | gold leaf filigree, warm gold glow |
| 11 | human | guardian | dark brown skin, shaved head, grey stubble, heavy pauldrons, tower shield | man in his sixties | stern, calm | P3 | oxblood | gold laurel engraving, warm rim light |
| 27 | dragonkin | guardian | matte copper scales, one chipped horn, heavy plate, tower shield | adult man | watchful | P4 | rust | molten-orange glow in the plate seams |
| 09 | orc | guardian | older orc, worn tusk tips, cool grey skin, shaved head, iron plate, hearth-banner shield | elder man | gentle eyes | P5 | warm umber | red banner cape, gold rim |
| 16 | human | guardian | light olive skin, short black bob, freckles, chainmail and tabard, spear held upright | young woman | determined | P6 | moss green | blue tabard with gold emblem, glowing spearhead |
| 29 | stonekin | guardian | tall, broad, weathered face with slate markings, iron-bound shield | adult woman | resolute | P7 (gauntlet raised to halt) | steel grey | quartz veins glowing pale cyan |
| 02 | lizardfolk | ranger | narrow green muzzle, red crest, hood, bow | adult man | focused | P7 (hand signalling) | ochre | brighter crest, gold scale cloak pin |
| 07 | orc | ranger | dark green skin, long tied hair, fur collar, bow and hand-axe | adult woman | calm, focused | P4 | rust | bone-bead trim, amber glow |
| 12 | human | ranger | young freckled scout, red hair, hood down, bow over the shoulder | young man | eager grin | P8 | sea green | green-gold cloak clasp, glowing fletching |
| 17 | elf | ranger | highland palette, pale skin, ash-blond, fur-lined hood, longbow | young man | serious | P3 | steel grey | white wolf-fur mantle, frost-blue arrow glow |
| 24 | gnome | ranger | short-cropped hair, professional expression, large ears, hooked nose, small crossbow, naturalist's satchel | adult man | precise | P1 | plum | brass lens monocle glint, gold satchel buckle |
| 25 | goblin | ranger | large folded ear, sharp thoughtful face, yellow-green skin, hood, short bow | adult woman | thoughtful | P5 | moss green | green-glowing arrow, silver ear cuff |
| 31 | catfolk | ranger | sandy tabby fur, one scarred ear, hooded cloak, longbow | adult man | wary | P6 | midnight navy | silver moon brooch, pale glow on the bow |
| 33 | halfling | ranger | weather-beaten hunter, stubble, rabbit-fur collar, sling and short bow | middle-aged man | squinting, dry | P2 | warm umber | copper-trimmed coat, warm amber glow |
| 03 | hornborn | duelist | swept-back small horns, amber eyes, crimson fitted coat, rapier | adult woman | confident smirk | P1 | dusk violet | gold-tipped horns, ember sparks |
| 13 | human | duelist | scar across the cheek, easy smile, tan skin, moustache, burgundy fitted coat, rapier hilt | adult man | easy smile | P2 | warm umber | gold embroidery, gleaming rapier |
| 26 | goblin | duelist | narrow face, immaculate short hair, spectacles tied with string, dark coat with lace cuffs, thin rapier | adult man | composed | P3 | plum | silver lace, blade gleams violet |
| 32 | catfolk | duelist | sleek black fur, white muzzle, fitted silver-grey coat, rapier at the hip | adult woman | cool, poised | P4 | slate blue | silver trim, pale blue blade glint |
| 10 | elf | duelist | dusk palette, brown skin, silver braids, dark teal fitted coat, twin daggers | adult man | intense | P5 | deep teal | silver-thread coat, teal dagger glow |
| 18 | halfling | duelist | determined narrow-eyed veteran, curly grey-brown hair, worn leather coat, short blades held upright | older woman | narrow-eyed | P6 | moss green | gold coat buttons, gleaming blades |
| 34 | hornborn | battlemage | curled ram-like horns, freckles, green robe, candle-flame spell | young man | curious | P1 | rust | glowing horn engravings |
| 23 | gnome | battlemage | wild white hair, thick expressive brows, patched orange robes, sparks between the fingers | old woman | delighted | P3 | deep teal | bright sparks, gold-thread patches |
| 04 | elf | battlemage | dusk palette, pale skin with dark freckles, long black hair, violet robe, purple flame orb | adult woman | aloof | P4 | plum | larger orb, silver circlet |
| 08 | human | battlemage | grey-haired scholar, grey bun, spectacles on a chain, deep blue robe, crackling spellbook | woman in her sixties | sharp, amused | P5 | midnight navy | book glowing violet-white, silver stars on the robe |
| 14 | elf | battlemage | highland palette, older elf with a broken nose, white hair tied back, grey-blue mantle, storm-crystal staff | elder man | weathered calm | P6 | ochre | lightning around the crystal |
| 19 | human | battlemage | dark skin, short locs, practical leather coat, copper rune bracers, frost on the fingers | adult man | focused | P7 | steel grey | icy blue glowing runes |
| 28 | dragonkin | battlemage | dark blue scales, ivory cheek ridges, storm robe, staff across the shoulder | adult woman | proud | P8 | sea green | crackling lightning, gold horn bands |
| 35 | lizardfolk | warden | older face with worn ridge scales, dark scales, pale throat, reed mantle, staff hung with river shells | elder woman | patient | P2 | deep teal | shells glowing aqua |
| 05 | elf | warden | woodland palette, olive skin, leaf-braided hair, bark-and-leaf mantle, carved staff | adult woman | serene | P3 | moss green | flowers blooming in the hair, soft green glow |
| 30 | stonekin | warden | older stonekin with chalk-white markings, moss on the shoulders, stone-headed staff | elder man | kind, slow | P5 | warm umber | markings glowing soft green |
| 20 | elf | warden | highland palette, young, red hair, antler circlet, small bird on the raised hand | young woman | warm | P7 | sea green | glowing antler circlet, luminous bird |
| 15 | elf | warden | dusk palette, grey-streaked dark hair, crescent tattoo, deep blue hooded mantle with owl feathers, lantern staff across the shoulder | middle-aged man | quiet | P8 | midnight navy | lantern glowing gold, silvered feathers |

21 (dwarf Warden, P1, deep teal-green) and 22 (dwarf Battlemage, three-quarter right with a raised flame hand, dusk violet)
are kept.

## Import (after owner review)

- Copy the chosen files over `Assets/Implemented/heroes/hero_XX_<class>_{base,upgraded}.png`; manifest: 64 px
  resolutions for 01-10, `race_concept` matching the tag, entries 23-35.
- `HERO_SMALL` crops in `tools/pixelart/import_assets.py` (11-15, drawn from further away) are re-measured or dropped.
- New IDs 23-35 go into class `appearances` and `portraitRaces`; the launch catalog changes, so `VersionFingerprintTest`
  needs the content version and hash handled (check `engine/Compatibility.kt` first; 21-22 already trip it).
- Face counts in `AppearanceTest` and `PortraitArtTest`, then a device check of the portraits at list and sheet size.

## Phase 2: every people-and-class cell (36-66, 2026-10-17)

Owner: "we gotta do them also. Try to be more efficient with cost … same structure with backgrounds. Make them unique,
fun to look at, memorable and aligning with their race/class." Recipe changes from phase 1, for cost:
- The setting is in the base prompt (the phase-1 recipe of 30), so no background edits.
- Upgrades at strength 0.7 on the first try; an upgrade that changed under 100 pixels was redone at 0.75 with an accent
  that recolours a large area (a mantle, a coat, the armour) rather than a small glint (37, 43, 47, 52, 53, 55-58).
- All bases judged on one sheet before any upgrade; 42, 48 and 54 came out too pale (mean above 135) and were redrawn
  with a dusk or night setting. Generated in one script loop (scratchpad `gen3.py`, `rows3.py`), not agents.
- 75 calls, about $13.50.

Poses: the phase-1 rule "no pose repeats within a class" cannot hold at 12-14 faces against eight poses. Extra poses
(side profile with eyes to the viewer, head tilted laughing or grinning, a hand at the chest, looking down at something
held, both hands on a planted weapon or staff, an object held up to the face) are used, and the rule becomes: no pose
repeats within a class inside this batch, and no setting repeats within a class at all. §3 art directions phase 1 left
unused come first (human healer, dwarf braided beard with a repaired clasp and soot-marked brows, halfling courier and
herbalist, gnome severe beard and thick brows with round spectacles, orc warm olive skin, goblin pale ear edges, dragonkin
pale green frill and charcoal with gold eyes, hornborn broken horn and pale horn tips, stonekin quartz specks and
close-cropped narrow face, catfolk lynx tufts and older mottled fur, lizardfolk turtle-like cheek plates).

| ID | People | Class | Look | Age / presentation | Expression | Pose | Setting | Hue | Upgraded accent |
|---|---|---|---|---|---|---|---|---|---|
| 36 | human | warden | dark brown skin, hair wrapped in a mustard headscarf, sleeves rolled up, linen apron over a green healer's coat with herb pouches | adult woman | warm, practical, a faint smile | holding a bundle of fresh herbs at her chest, eyes to the viewer | field hospital tent with bundles of dried herbs hanging | warm amber | the herbs in her hands glowing bright golden-green, a white healer's sash with gold embroidery across her chest |
| 37 | dwarf | guardian | carefully braided copper-red beard held by a repaired brass clasp, iron helm with cheek guards, heavy riveted plate | middle-aged man | stern, unshakeable | a warhammer resting on his shoulder | mine entrance in a mountainside with hanging lanterns | deep indigo | the iron helm and plate turned polished gold, orange runes glowing on the warhammer head |
| 38 | dwarf | ranger | soot-marked heavy brows, two thick black braids, no beard, leather hood thrown back, travel-worn brown scout coat, crossbow strap across her chest | adult woman | a wry crooked grin | head tilted | red rock canyon caravan trail at sunset | rose ochre | a silver-white wolf-fur mantle on her shoulders, a bright amber glow on the crossbow bolt |
| 39 | dwarf | duelist | forked black beard tied with copper rings, shaved sides of the head with a black topknot, fitted mustard-yellow doublet | young man | cocky half-smile | a short sword resting across his shoulder | smoky dice hall with hanging lamps | amber brown | the beard rings turned gleaming gold, the short sword blade glowing bright white-gold |
| 40 | halfling | guardian | serious young face, chestnut hair in a tight crown braid, mail shirt under a leather jerkin, round wooden shield painted with a wheat sheaf | young woman | grave, determined | one hand gripping the shield strap at her chest | wooden palisade of a military camp with tents | olive green | a polished steel gorget with gold edging, the wheat sheaf on the shield glowing gold |
| 41 | halfling | battlemage | curly brown hair, rosy cheeks, a courier's satchel strap, brown travelling coat with brass buttons | young man | eager, bright-eyed | looking down at an open scroll in his hands with a small swirl of wind and leaves rising from it | windy hilltop with a windmill at dusk | sky blue | the scroll and the swirl of wind glowing bright pale cyan, silver wind patterns on the coat |
| 42 | halfling | warden | broad ruddy cheeks, white mutton-chop whiskers, a frayed straw hat, green waistcoat with seed pockets | elderly man | laughing | head tilted back a little | vegetable garden with beehives in the evening dusk | deep honey brown | small glowing golden bees circling his hat, fresh green leaves sprouting on the hat brim |
| 43 | gnome | guardian | severe face with a neat squared grey beard, small round steel helm with brass rivets, brass-buckled breastplate | older man | severe, unimpressed | side profile facing left, eyes turned to the viewer | clockwork gatehouse with large brass gears | teal | the steel helm and breastplate turned gleaming brass-gold, a bright blue gem glowing on the helm |
| 44 | gnome | duelist | sharp face with a long pointed nose, auburn hair pinned up, a feathered green cap, fitted green doublet, slim rapier | young woman | playful challenge | one hand raised beckoning with two fingers | garden hedge maze with a fountain at dusk | dusky green | a long golden feather on the cap, the rapier blade glowing bright pale green |
| 45 | gnome | warden | thick ginger bushy brows, round spectacles, a ginger moustache, moss-green coat with many pockets, a large beetle on his shoulder | adult man | fascinated, gentle | holding a magnifying glass up to one eye | giant mushroom forest with glowing spores | blue violet | the beetle's shell glowing iridescent green, bright blue spores drifting around him |
| 46 | orc | duelist | warm olive skin, small tusks, glossy black hair in a high ponytail, gold hoop earrings, red sash over a white open-collared shirt, curved sabre | adult woman | fierce grin | tightening a leather glove at her chest | ship deck with rigging at sunset | coral red | gold embroidery on the red sash, the sabre gleaming bright gold |
| 47 | orc | battlemage | deep brown-green skin, braided topknot, white clay paint stripes on the cheeks, bone-bead necklace, layered earth-brown robes | young man | intense, focused | one fist raised with pebbles and dust floating around it | volcanic caldera glowing far below | dark red | his robes turned deep crimson with glowing orange runes, the floating pebbles glowing molten orange |
| 48 | orc | warden | light grey-green skin, grey-streaked braids, small tusks showing, oilskin ferry cloak, oak staff with a hanging lantern | middle-aged woman | calm, weathered | turned left, glancing back over the shoulder | dark river ferry landing at dusk with mist on the water | dark slate blue | the lantern glowing warm bright gold, a garland of white river flowers on her cloak |
| 49 | goblin | guardian | one notched long ear, freckled grey-green skin, dented steel pot helm pushed back, a tall rectangular shield | young woman | stubborn, brave | shield raised to her chin, peering over its rim | rainy stone bridge with torches at night | deep blue green | the shield rim and helm turned polished gold with a glowing blue gem on the shield |
| 50 | goblin | battlemage | copper rings along her long ears, bright green eyes, patchwork indigo robe | adult woman | sly, curious | holding up a lantern with a green spirit flame | foggy swamp with will-o'-wisps | swamp green | the spirit flame grown large and bright green, glowing green runes on the robe |
| 51 | goblin | warden | wrinkled face, pale ear edges, white tufts of hair, woven reed cloak, a crooked wooden staff with a bird's nest on top | elderly man | gentle smile | one hand resting on his chest | willow pond with lily pads at dusk | dusk teal | a small glowing blue bird in the nest, the reed cloak edged with glowing blue |
| 52 | dragonkin | ranger | sand-gold scales with dark stripes, long swept-back horns, a dusty desert scarf around the neck, leather bracers | adult man | keen | side profile, one hand shading his eyes, scanning the distance | dusty badlands at dusk | burnt orange | a bright crimson desert scarf around the neck, gold rings on the horns |
| 53 | dragonkin | duelist | charcoal-black scales, gold eyes, sleek back-swept horns, black-and-gold fitted coat | adult woman | cool, dangerous | side profile facing right, gold eye glancing at the viewer | moonlit palace balcony with arches | deep blue | the coat turned crimson with gold trim, the gold eyes glowing bright |
| 54 | dragonkin | warden | pale green scales, a short frill, small pale horns twined with a flower garland, deep rose-red shawl | young woman | serene | eyes closed, holding a flower up to her muzzle | terraced tea garden at night in the rain with paper lanterns | dark jade green | the flower garland glowing soft bright pink, petals drifting around her |
| 55 | hornborn | guardian | one broken horn, ash-blond beard, scale mail, a round shield with a sun emblem | adult man | laughing heartily | head tilted | temple steps with braziers | crimson | the scale mail turned polished gold, the sun emblem on the shield glowing bright gold |
| 56 | hornborn | ranger | dark brown skin, long back-swept horns with pale ivory tips, close-shaved hair, grey-green hooded cloak | young woman | focused | looking down along an arrow she is nocking | overgrown jungle ruins with hanging vines | deep jade | the grey-green cloak turned deep emerald with gold trim, the arrowhead glowing bright green |
| 57 | hornborn | warden | tall straight horns twined with ivy, lavender eyes, long grey hair, deep plum mantle | elderly woman | wise, knowing | both hands resting on a tall staff in front of her | autumn forest with falling leaves | copper | the plum mantle turned glowing silver-white, glowing violet flowers blooming in the ivy on her horns |
| 58 | stonekin | ranger | warm brown skin with glittering quartz-like specks, short dark curls, fur-trimmed leather | adult woman | quietly amused | drawing a bowstring back to her chest | high alpine lake among rocks at twilight | cool lilac | a white snow-fox fur cloak on her shoulders, the quartz specks on her skin sparkling bright white |
| 59 | stonekin | duelist | close-cropped dark hair, narrow face, pale grey skin with dark slate seams, long plum fencing coat | adult man | cold, precise | rapier raised upright in a salute beside his face | snowy courtyard with torches | ice blue | the slate seams glowing pale blue, silver frogging on the coat |
| 60 | stonekin | battlemage | very dark obsidian skin with thin gold metallic vein markings, shaved head, deep red robe | young woman | calm power | both hands cupped at her chest holding a floating crystal shard | crystal cavern | violet blue | the gold veins glowing bright, the crystal shining brilliant violet |
| 61 | catfolk | guardian | broad lynx-like face with long ear tufts, grey-brown spotted fur, heavy bear-fur mantle over plate armour | adult man | steady, stoic | gauntleted paws resting on the pommel of a greatsword planted in front | snowy mountain fortress gate | icy blue | a frost-white fur mantle, the sword pommel glowing pale blue |
| 62 | catfolk | battlemage | snow-leopard pale grey fur with dark rosettes, ice-blue eyes, white robe with blue trim | young woman | dreamy, curious | three-quarter right, one paw raised with drifting violet starlight motes | rooftop garden at night with paper lanterns | indigo | many more bright violet starlight motes, a silver star circlet on her brow |
| 63 | catfolk | warden | older mottled brown-grey fur, tired eyes, grey whiskers, woven grass mantle | elderly man | tired, kind | holding a staff hung with small bells upright beside his face | lavender heath under a cloudy sky | lavender | the bells glowing bright gold, small white flowers woven into the mantle |
| 64 | lizardfolk | guardian | broad turtle-like cheek plates, olive-brown scales, bronze scale armour, a big round shield with a hexagon pattern | adult man | solid, patient | holding a trident upright beside his face | marsh stockade on stilts with lanterns in fog | murky green | the bronze armour turned gleaming gold, the trident tips glowing aqua |
| 65 | lizardfolk | duelist | slender, teal scales with gold bands, a small blue frill, twin curved daggers | young woman | mischievous grin | head tilted | busy dock market with lanterns | teal gold | the frill glowing bright blue, the daggers gleaming gold |
| 66 | lizardfolk | battlemage | speckled brown-and-yellow scales, tall orange frill fanned out, amber eyes, hooded rust-red robe | young man | excited | a swirling orb of water above one raised palm | sunken flooded temple | aqua | the water orb glowing bright aqua, the frill glowing orange |
