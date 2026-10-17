# Tiny Blacksmith: Peoples, Names, Heroes, and Siege Bestiary

**Version:** 1.0, authored content bible  
**Date:** 17 October 2026  
**Companion:** `Tiny_Blacksmith_Living_Heroes_Design_Review.md`  
**Purpose:** Supply concrete creative content for implementation. This document makes the creative choices instead of delegating the roster, naming cultures, enemy identities, and siege concepts to the implementing agent.

> A mixed town of adventurers defends a working forge. Some arrived for coin, some for refuge, and some for a promise they have not yet kept. Beyond its walls, organized armies, restless dead, and driven beasts gather for the next siege.

## 1. Authority and implementation contract

This is an original setting and game-content proposal with recognizable tabletop-fantasy influences. It is not an official Dungeons & Dragons supplement or a transcription of its rules. Names, factions, individual histories, encounter compositions, abilities, dialogue, and numerical tuning below are authored for Tiny Blacksmith. Traditional creature identities provide recognition. Consult the source notes in Section 18 for the limited researched touchstones.

The older attached GDD was read for context. The latest repository has not been inspected for this content bible. All IDs below are proposed stable content IDs, not claims about existing code or current content. Map them to existing systems without overwriting approved data or silently renaming existing races, factions, conditions, or heroes.

**Implementer:** Preserve these creative identities. Reuse existing classes and mechanics where the behaviour matches. Flag a mechanical incompatibility and propose the smallest faithful adaptation. Do not replace a distinctive enemy with a generic power bonus and report its mechanic as complete. Do not add new classes, unrequested materials, or unrelated building systems. Numerical values are prototype tuning, not balance evidence.

This file defines the full planned content catalogue. Asset-gated races and mechanically unsupported enemies may remain disabled until their dependencies are ready. Keep an implementation coverage ledger for every entry.

### Contents

1. Authority and contract
2. World and playable peoples
3. Race catalogue
4. Naming and generation rules
5. Complete name pools
6. Backgrounds and individual identity
7. Personality and hero examples
8. Enemy rules vocabulary
9. Three invading forces
10. Thirty-six standard enemies
11. Six captains
12. Three siege engines and three major monsters
13. Army compositions and variety
14. Hero demand, personal agendas, and opportunities
15. Art production briefs
16. Implementation data contracts
17. Validation and release batches
18. Research and source notes

## 2. World frame: the border town of Cinderwatch

**Cinderwatch** is the proposed setting name, not a required rename of an existing town. It is a trade settlement where roads, marsh paths, old burial lands, and mountain passes meet. Its forge supports an adventurers' guild and a changing local population. Use the existing town name if one is already established.

The three threat families are military raiders, an undead procession, and a coalition of driven beasts and bound natural creatures. Their armies have reasons to attack a wall. Wolves do not carry ladders, gargoyles do not march in infantry formations, and a dragon does not appear as a cheap recruit in every fifth wave.

**Race does not determine allegiance.** Orcs and goblins can be ordinary guild members and customers. Humans and elves can be deserters or cultists. The Iron Banner is an organization, not an entire people. Undead are not automatically disposable former enemies in narrative records: a recovered named body or weapon should reference actual history where such a link exists.

## 3. Complete playable race catalogue

There are **12 planned playable races**. All can use every existing class: Guardian, Ranger, Duelist, Battlemage, and Warden. Race supplies appearance, naming culture, lineage, and narrative identity. **This content version adds no innate combat bonuses or penalties.** Individual build differences come from class, background, expertise, traits, equipment, and experience. This avoids multiplying balance work or making particular races mandatory for a role.

The recruitment weights below are relative selection weights, totaling 100. Normalize over enabled races with valid art. They are not quotas. Starting-roster viability is checked by role, not race. No race is a rare superior tier.

| Stable ID | Display name | Weight | Defining visual identity | Naming pattern |
| --- | --- | ---: | --- | --- |
| human | Human | 24 | Wide range of complexions, builds, hair, and ages | Given + family |
| elf | Elf | 18 | Long pointed ears, angular features, varied hair and skin | Given + family |
| dwarf | Dwarf | 14 | Broad face, sturdy neck, strong brows, individual beard choices | Given + clan |
| halfling | Halfling | 8 | Small stature, rounded features, practical travelling clothes | Given + family |
| gnome | Gnome | 7 | Small stature, expressive brows, large ears, distinctive nose shapes | Given + family |
| orc | Orc | 8 | Prominent lower canines, strong jaw, varied skin tones | Given + clan |
| goblin | Goblin | 5 | Long ears, sharp expressive features, compact build | Given + household |
| dragonkin | Dragonkin | 4 | Reptilian muzzle, scales, horns, no default wings | Given + clutch-name |
| hornborn | Hornborn | 4 | Humanoid features, horns, pointed tail if visible | Given + family |
| stonekin | Stonekin | 3 | Tall broad build, mineral-like markings, humanlike face | Given + kin-name |
| catfolk | Catfolk | 3 | Feline muzzle, ears, whiskers, varied fur patterns | Given + family |
| lizardfolk | Lizardfolk | 2 | Reptilian face, crest or frill, scaled skin, visible external ears absent | Given + waterline-name |

### 3.1 Human

Humans in the town descend from river settlements, upland farms, and caravan communities. Their names are direct and readable rather than pseudo-medieval tongue-twisters. Family names often recall a trade, place, or landscape. Do not make all humans the default neutral face while other races receive personality.

**Art directions:** a tired middle-aged defender; a young freckled scout; a grey-haired scholar; a dark-skinned practical healer; a scarred duelist with an easy smile. These are appearance ideas, not fixed jobs.

**Name examples:** Maren Voss; Tobin Reed; Sabine Blackwell. Children may inherit either established parental family name under the existing lineage system.

### 3.2 Elf

Elves belong to orchard towns, river libraries, and woodland settlements. They are not uniformly aloof or ancient. Use three appearance palettes inside one race: woodland, dusk, and highland. These are art tags, not new subraces or power tiers.

Names use flowing vowels, but remain pronounceable. Family names are translated landscape names. Avoid long apostrophe chains and official setting houses.

**Art directions:** copper-skinned elf with a cropped haircut; pale elf with dark freckles; brown-skinned elf with silver braids; older elf with a broken nose. **Examples:** Ilyra Rainbloom; Caeren Ashenbough; Taelia Mossveil.

### 3.3 Dwarf

Dwarven households include masons, clerks, brewers, field medics, and caravan scouts as well as smiths. Clan names recall a landmark, an ancestor's tool, or a former holding. Facial hair varies by individual rather than determining competence or gender.

**Art directions:** soot-marked brows; carefully braided beard with a repaired clasp; clean-shaven scholar with brass spectacles; older dwarf with one silver eyebrow. **Examples:** Dagna Ironrivet; Borin Warmkeg; Sigrin Runechisel.

### 3.4 Halfling

Halfling families live in market lanes, farms, barges, and military camps. Their warm naming rhythm does not force them into comic relief. A halfling Guardian can be as serious as any other defender. Equipment fits their scale but uses the same class rules.

**Art directions:** determined narrow-eyed veteran; curly-haired courier; broad-cheeked herbalist; weather-beaten hunter. **Examples:** Tansy Copperkettle; Arlo Underhedge; Pippa Ryefoot.

### 3.5 Gnome

Gnomes include naturalists, record keepers, artificers in the ordinary occupational sense, and itinerant adventurers. This does not introduce an Artificer class. Names are brisk and bouncy, with family names recalling objects and household stories.

**Art directions:** thick brows and round spectacles; severe face with a neat beard; wild white hair; short-cropped hair and a professional expression. Avoid mandatory goggles on every face. **Examples:** Fizzi Lanternwick; Calzi Pebblegear; Nimma Reedbutton.

### 3.6 Orc

Orc clans include settled farmers, ferrymen, soldiers, and traders. Clan names can celebrate endurance, a shared home, or a historical symbol. Short names with strong consonants remain distinct from automatically aggressive personalities.

**Art directions:** warm olive skin and small tusks; cool grey skin and a shaved head; dark green skin and long tied hair; older orc with worn tusk tips. **Examples:** Ashka Riverfang; Gorak Hearthbanner; Urga Whiteboar.

### 3.7 Goblin

Goblin households often keep translated family nicknames inherited from an ancestor, workshop, or memorable mishap. The goblin who bears the name Crackedcup need not be clumsy. They are full members of the town, not disposable jokes.

**Art directions:** large folded ear; narrow face with a thoughtful expression; spectacles secured with string; immaculate short hair; an older goblin with pale ear edges. **Examples:** Nizz Lanternbit; Brik Copperclip; Zabba Wickpocket.

### 3.8 Dragonkin

Dragonkin have scaled faces and clutch-names recalling old family symbols. Scale colour is visual. It does not force alignment, damage type, or personality. No innate breath attack is added by this race version. A later breath expertise would need its own balanced rules and owner approval.

**Art directions:** matte copper scales and a chipped horn; dark blue scales with ivory cheek ridges; pale green scales and a short frill; charcoal scales and gold eyes. **Examples:** Keshra Bronzehorn; Arkan Stormhorn; Vadrak Pearlcrest.

### 3.9 Hornborn

Hornborn are people with inherited magical features, not inherently infernal servants. Horn shapes and unusual eyes create visual variety. Their families have their own local history, rather than copying a published cosmology. Names are restrained and lyrical; no mandatory virtue-name system.

**Art directions:** swept-back small horns and amber eyes; curled ramlike horns and freckles; one broken horn; dark skin and pale horn tips. **Examples:** Savris Lanternhush; Adira Rosehush; Tavra Silvercandle.

### 3.10 Stonekin

Stonekin are tall people with mineral-like skin markings and mountain kin-names. They are not stone constructs. Their markings may be subtle seams or patches, not literal armour plating. Body size does not restrict any class.

**Art directions:** weathered face with slate markings; warm brown skin with quartz-like specks; close-cropped hair and a narrow face; older stonekin with chalk-white markings. **Examples:** Karok Skyledge; Brana Rivercairn; Edra Flintstep.

### 3.11 Catfolk

Catfolk have feline faces, hands suited to ordinary equipment, and expressive ears. Fur patterns range from plain to striped and spotted. Do not make every individual playful, stealthy, or nomadic. Names are short and easy to read, followed by family names translated into the local language.

**Art directions:** broad lynxlike face with ear tufts; sleek black fur and a white muzzle; sandy tabby with a scarred ear; older mottled fur and tired eyes. **Examples:** Nauri Reedstep; Tiri Silverclaw; Vira Moonpaws.

### 3.12 Lizardfolk

Lizardfolk families maintain river and marsh settlements, including docks and trading posts. Their names feature sibilants but remain readable. Waterline-names mark a family home or historical crossing. Reptilian appearance does not imply emotionlessness, savagery, or primitive tools.

**Art directions:** broad turtlelike cheek plates without a shell requirement; narrow green muzzle and red crest; dark scales and pale throat; an older face with worn ridge scales. **Examples:** Esra Stillreed; Kash Heronscale; Izik Tidemark.

## 4. Naming and generation rules

- Every race has exactly **100 given names and 50 family/clan names** below: 1,200 given-name entries and 600 surname entries, yielding 60,000 within-race name pairs before titles. Entries may coincide across cultures intentionally; uniqueness is by full identity, not by given name globally.
- Pools are original authored fantasy names, not official D&D name lists. Accidental overlap with familiar names is possible.
- Names are not gender-locked. Choose a portrait and identity coherently without restricting class or personality by name.
- Default pattern is `Given Family`. The labels clan, household, clutch, kin, and waterline describe cultural meaning, not additional UI fields.
- Culture and ancestry are separate data concepts. This first version gives each race a default naming culture. Adoption or mixed upbringing may use another culture only through an explicit authored background rule; do not randomly scramble pools.
- Generate at creation using a dedicated deterministic stream. Persist the chosen strings and culture ID. Adding names to a later content version must not rename existing heroes.
- Prevent duplicate full names among living heroes using bounded retries. If needed, use another legitimate pair. Exhausted pools fall back to a location byname defined in Section 7; never append a visible numerical serial.
- Keep an existing family name for descendants where inheritance is intended. New relations do not retroactively rename established heroes.
- Titles are earned, not rolled at creation. Display the title as a separate line or field so long names do not break the portrait UI.
- On a small screen, allow name wrapping. Never bake a full name into portrait art.

## 5. Complete name pools

Each numbered row contains ten entries. The order is data order only, not rarity or quality.

### 5.1 Human — `human`

**Given names, 100**


1. Alden, Ansel, Arlen, Bastian, Bram, Brenna, Brina, Calder, Calla, Carys.

2. Cedric, Celine, Corin, Dalia, Daren, Delia, Dorian, Edric, Eira, Elian.

3. Elise, Elric, Emren, Enna, Esme, Evren, Fara, Farren, Fenna, Ferris.

4. Galen, Garran, Gemma, Gerrit, Gretta, Hadric, Halen, Hanne, Harlan, Hester.

5. Iden, Ilse, Iona, Iris, Isen, Jalen, Jessa, Joren, Joska, Jules.

6. Kael, Kellan, Kessa, Kira, Laren, Leona, Liora, Lorcan, Lucan, Lydia.

7. Maren, Marta, Merrin, Mira, Nessa, Niall, Nola, Oren, Orla, Osric.

8. Pella, Perrin, Petra, Quinn, Rafe, Rella, Renna, Rian, Roderic, Roska.

9. Rowan, Sabine, Sella, Seren, Silas, Sorin, Talia, Tamsin, Taren, Tessa.

10. Tobin, Tovin, Trista, Una, Varen, Vela, Vera, Willa, Wren, Ysra.


**Surnames / family names, 50**


1. Ashford, Ashwell, Barrow, Beckett, Bellmere, Blackwell, Bracken, Briarford, Brookmere, Carrow.

2. Clayborne, Coldwell, Crowley, Dunmere, Eastwick, Fairbrook, Farrow, Fenwick, Flintford, Greyford.

3. Harrow, Hawthorne, Heathmere, Holt, Ironwell, Kestrel, Langford, Larkspur, Lowbridge, Marshwell.

4. Merefield, Moorcroft, Mossley, Northcott, Oakley, Redfern, Reed, Rookwell, Rosemere, Rowanfield.

5. Sallow, Shaw, Southwick, Stoneford, Thorne, Vale, Voss, Westmere, Whitlock, Wrenley.



### 5.2 Elf — `elf`

**Given names, 100**


1. Aelir, Aelith, Aeran, Aeria, Aerin, Aethra, Alath, Alira, Alyn, Amriel.

2. Anar, Aneth, Aravel, Aria, Arien, Ashal, Athea, Aven, Avir, Aylen.

3. Caelar, Caelia, Caeren, Caeth, Calen, Calira, Celyr, Cereth, Cira, Cyrel.

4. Daelin, Daeria, Dalan, Delith, Elar, Elara, Elen, Elira, Elnar, Elowen.

5. Erynd, Esriel, Faelar, Faelia, Faen, Fala, Felyr, Feriel, Ilar, Ilara.

6. Ilien, Ilyra, Imriel, Irath, Isil, Ithra, Laelar, Laelia, Lareth, Lethia.

7. Liora, Loriel, Lyren, Maelar, Maelia, Maerin, Melith, Meriel, Mirath, Myren.

8. Naelar, Naelia, Nareth, Neria, Nethil, Nymra, Olar, Olyra, Orien, Oriel.

9. Raelar, Raelia, Rhaen, Riel, Saelar, Saelia, Saerin, Selith, Seriel, Sylra.

10. Taelar, Taelia, Talien, Thalen, Thalia, Vaelar, Vaelia, Valeth, Veyra, Yriel.


**Surnames / family names, 50**


1. Amberleaf, Ashenbough, Autumnmere, Birchveil, Brightfern, Cedarfall, Cloudbough, Copperleaf, Dawnmere, Dewbranch.

2. Duskmere, Emberbloom, Eveningreed, Fernwhisper, Frostbloom, Glimmerbrook, Goldwillow, Greenveil, Hazelwind, Hollowleaf.

3. Ivymere, Juniperfall, Lakewhisper, Larkbough, Leafwarden, Lilybrook, Mistbranch, Moonfern, Mossveil, Nightwillow.

4. Oakwhisper, Palegrove, Rainbloom, Reedwhisper, Riverbough, Roseveil, Rowanbloom, Shadewillow, Silverfern, Skybough.

5. Snowpetal, Starbrook, Stillgrove, Sunwillow, Thornbloom, Twilightreed, Verdantmere, Whitebranch, Wildbloom, Windfern.



### 5.3 Dwarf — `dwarf`

**Given names, 100**


1. Adrik, Asta, Baldrin, Barna, Belda, Belrik, Bera, Bofra, Borin, Brakka.

2. Bramli, Brenna, Brodda, Bruni, Dagna, Dagri, Dalla, Darrik, Delda, Dori.

3. Dranna, Drenni, Durna, Dval, Edda, Edrik, Elda, Erna, Falda, Farik.

4. Fenni, Filda, Fjorna, Frakki, Fredda, Galda, Garrik, Gerda, Gilda, Gorrin.

5. Granna, Gredda, Gruni, Hadda, Haldra, Harn, Heda, Helda, Hilda, Hroda.

6. Idda, Idri, Inga, Irda, Janna, Jarn, Jelda, Jora, Kadda, Kaldri.

7. Kelda, Korrin, Kreda, Kruni, Ladda, Larn, Leda, Lorna, Magda, Maldrin.

8. Marda, Morna, Nadda, Narn, Nella, Nordri, Odda, Odrik, Olga, Orna.

9. Radda, Ragna, Raldri, Redda, Rikka, Rolda, Runa, Sadda, Sigrin, Skelda.

10. Stenna, Tadda, Tilda, Torri, Udda, Ulrik, Urda, Vadda, Varn, Ydda.


**Surnames / family names, 50**


1. Amberanvil, Ashmantle, Basaltborn, Bellhammer, Blackgranite, Brassdelve, Bronzebraid, Cinderpick, Coalbraid, Coppervein.

2. Deepbarrel, Deepchisel, Emberbeard, Flintmantle, Forgeward, Gemdelve, Giltbuckle, Goldrivet, Granitehand, Grimbell.

3. Hammerfall, Hearthdelve, Highcairn, Hollowpick, Ironbarrel, Ironrivet, Keenpick, Kilnward, Leadmantle, Lowvault.

4. Mithrilbell, Oakenkeg, Orekeeper, Redanvil, Ridgehammer, Runechisel, Saltbeard, Shaleborn, Silverrivet, Slatemantle.

5. Smokechisel, Steelbuckle, Stoneledger, Stoutcairn, Tinbraid, Underbell, Vaultward, Warmkeg, Whitegranite, Zincdelve.



### 5.4 Halfling — `halfling`

**Given names, 100**


1. Addie, Afton, Albie, Alma, Amity, Anni, Arlo, Barda, Bea, Benna.

2. Bennie, Berta, Bess, Biddy, Bilby, Bina, Boddie, Bonnie, Bramble, Bree.

3. Brill, Bryn, Caddie, Callie, Celand, Ceri, Cilla, Clover, Cora, Corby.

4. Daffy, Della, Denny, Dilly, Dodie, Dora, Dovie, Ebbie, Effie, Elby.

5. Ellie, Elsie, Emmie, Etta, Fable, Fennie, Fia, Filby, Flori, Fritta.

6. Gilly, Goldie, Hallie, Hattie, Hazel, Hilda, Hob, Hobbis, Hollie, Ida.

7. Iggy, Ina, Ivy, Jilly, Jip, Jodie, Jossie, Junie, Kella, Kit.

8. Lally, Leni, Lettie, Liddy, Lillie, Linna, Lottie, Ludo, Mallow, Marnie.

9. Merry, Milla, Mim, Mina, Mossie, Nellie, Nib, Nori, Oatie, Ollie.

10. Pansy, Peppa, Perrie, Pippa, Posy, Rosie, Rudi, Tansy, Tilly, Winnie.


**Surnames / family names, 50**


1. Applebrook, Barleybell, Beechbutton, Berryfield, Bramblepot, Brindlefoot, Brookbutton, Buttercup, Ciderhill, Cloverpatch.

2. Cobblebrook, Copperkettle, Dapplemead, Dewberry, Elderbasket, Fairburrow, Fernbutton, Fiddlebrook, Figglepot, Foxglove.

3. Gingerhill, Greenpan, Haymeadow, Hazelpot, Heatherwick, Hillbutton, Honeyfield, Kettlebrook, Larkmead, Littlecask.

4. Maplefoot, Meadowpan, Millbasket, Mintburrow, Mossbutton, Nutmeg, Oatfield, Pennybrook, Poppypot, Quincehill.

5. Reedbasket, Roundhill, Ryefoot, Saffronfield, Smallmead, Softbrook, Thistlepot, Underhedge, Willowpan, Wrenburrow.



### 5.5 Gnome — `gnome`

**Given names, 100**


1. Abbin, Adzi, Albi, Amzi, Anka, Arbi, Arzi, Babbin, Baffi, Balzi.

2. Banni, Bebbi, Belzi, Bimma, Binni, Bix, Boddin, Bozzi, Brimma, Bubbi.

3. Caddik, Calzi, Cibbi, Cimma, Cizzi, Cobbin, Corzi, Dabbik, Dazbi, Debbi.

4. Dibbin, Dimzi, Dizzik, Dobbi, Duzza, Ebbik, Eddi, Elzi, Emmi, Enzik.

5. Fabbik, Fazzin, Febbi, Fimma, Fizzi, Fobbin, Folzi, Fribbi, Fuzza, Gabbin.

6. Gazbi, Gebbi, Gimma, Ginni, Gizzik, Gobbi, Gozza, Habbik, Hazzi, Hibbi.

7. Himzi, Hizzik, Hobbin, Ibbi, Idzik, Ilzi, Imma, Innik, Izzi, Jabbin.

8. Jazbi, Jebbi, Jimma, Jizzi, Jobbi, Jozza, Kabbik, Kazzi, Kibbi, Kimzi.

9. Kizzik, Kobbin, Lazbi, Lebbi, Limma, Lizzi, Lobbi, Lozza, Mabbik, Mazzi.

10. Mibbi, Mimzi, Mizzik, Mobbin, Nazzin, Nibbi, Nimma, Nizzi, Nobbi, Zibbin.


**Surnames / family names, 50**


1. Ambercog, Bellbutton, Brasswhistle, Brightbottle, Bristlewick, Bubblecap, Buttonspring, Cindergoggle, Clickbarrel, Clockfern.

2. Copperbutton, Corkwhistle, Dapplegear, Dewcog, Dimplewick, Embercrank, Fiddlecog, Fizzlebell, Flickerpin, Flintbutton.

3. Glimmerbolt, Glittercap, Glowspindle, Goldthimble, Hingeberry, Inkbutton, Jinglecog, Kettlepin, Lanternwick, Lensbottle.

4. Littlelever, Mistgoggle, Mosscrank, Nettlebolt, Pebblegear, Pennyspindle, Puddlecap, Quillcog, Reedbutton, Rivetfern.

5. Saffroncog, Silverwhistle, Sparkbottle, Spindlecap, Springberry, Tanglepin, Thimblewick, Tinkersprig, Whistlefern, Wobblecog.



### 5.6 Orc — `orc`

**Given names, 100**


1. Agra, Agruk, Akra, Arduk, Arga, Arnak, Ashka, Azra, Bagra, Balg.

2. Barka, Brakka, Brog, Brugha, Dagra, Daguk, Darg, Darsha, Drek, Druga.

3. Durna, Egra, Ekruk, Erga, Fagra, Faruk, Fesha, Frug, Gagra, Garka.

4. Gashra, Gavra, Gazuk, Ghora, Ghur, Giska, Gorak, Gorna, Grada, Grak.

5. Grasha, Greda, Gruk, Gurna, Hagra, Hark, Harsha, Hegra, Horg, Hruk.

6. Igra, Irga, Ishka, Jagra, Jark, Jasha, Jorga, Kagra, Karg, Karsha.

7. Kasha, Kazra, Korga, Krag, Krasha, Kreg, Kruk, Lagha, Larka, Lorga.

8. Magra, Marga, Maruk, Mog, Morga, Nagra, Nark, Nasha, Norga, Ogra.

9. Orga, Oruk, Ragra, Rakka, Rasha, Rorg, Ruk, Sagra, Sarka, Shagra.

10. Sharka, Shora, Targa, Torg, Ugra, Urga, Uruk, Vagra, Varka, Zorga.


**Surnames / family names, 50**


1. Ashbanner, Blackbriar, Blackridge, Boarcrest, Brassjaw, Bristleback, Brokenpeak, Bronzehide, Cinderhand, Cliffward.

2. Coalmark, Copperfang, Crowbanner, Deepdrum, Dustmane, Embercrest, Flintscar, Frostjaw, Greyboar, Hammerjaw.

3. Hardtusk, Hearthbanner, Highridge, Ironbrow, Ironreed, Keenfang, Longstride, Marshbanner, Oakscar, Redbriar.

4. Redcrest, Ridgewalker, Riverfang, Rockmane, Saltjaw, Scarbanner, Shalefang, Smokehide, Snowtusk, Spearcrest.

5. Steelreed, Stonebrow, Stormtusk, Sunscar, Thornjaw, Timbercrest, Trailward, Whiteboar, Wildridge, Wolfbanner.



### 5.7 Goblin — `goblin`

**Given names, 100**


1. Abik, Adzi, Aggi, Akki, Arri, Azzik, Babba, Bazz, Bekki, Bib.

2. Bikka, Bim, Binni, Bizz, Bokki, Brik, Bubba, Bukk, Chik, Chikka.

3. Chizz, Dabba, Dagg, Dekki, Dib, Dikka, Dizz, Dobb, Drekki, Drib.

4. Ebb, Eggi, Ekk, Fazz, Febbi, Fik, Fikka, Fizz, Fobb, Frik.

5. Gabb, Gazza, Gebbi, Gik, Gikka, Gizz, Gobb, Grib, Grikka, Gubb.

6. Hazz, Hebbi, Hikka, Hizz, Hobb, Ibbi, Iggi, Ikka, Izz, Jabba.

7. Jagg, Jekki, Jib, Jikka, Jizz, Jobb, Kabb, Kazza, Kekki, Kib.

8. Kikka, Kizz, Kobb, Krib, Krikka, Lazz, Lebbi, Likka, Lizz, Lobb.

9. Mabb, Mazza, Mekki, Mib, Mikka, Mizz, Mobb, Nazz, Nebbi, Nikka.

10. Nizz, Nobb, Pazz, Pekki, Pikka, Pizz, Rikka, Tizz, Vekki, Zabba.


**Surnames / family names, 50**


1. Bentnail, Blackpot, Blinkeye, Bluebutton, Bottlecap, Brassbite, Bristlecap, Brokenbell, Bucklebit, Candlewick.

2. Clatterpan, Copperclip, Crackedcup, Crowpocket, Dentedpan, Dripnose, Dustbutton, Emberpot, Fleapatch, Flintbit.

3. Forksnag, Glasseye, Goldtooth, Grinpatch, Hookbutton, Ironclip, Kettlecap, Knotfinger, Lanternbit, Longpocket.

4. Luckyspoon, Mothcap, Mudbutton, Nailpocket, Nettlegrin, Pebblebite, Picklepot, Quickbutton, Ragcap, Redkettle.

5. Ropebit, Rustpocket, Scrapbell, Shinyclip, Sootcap, Sparknail, Spoonhook, Stitchgrin, Tinbutton, Wickpocket.



### 5.8 Dragonkin — `dragonkin`

**Given names, 100**


1. Adraz, Akran, Akshar, Alzha, Arash, Ardax, Arkan, Ashra, Azhar, Azrak.

2. Baresh, Belrax, Bharra, Brask, Brezha, Chara, Charek, Chazra, Daran, Darash.

3. Dazha, Drazek, Drexa, Drish, Duzhar, Ekran, Elzha, Erash, Ezrak, Farash.

4. Fazra, Ferak, Frazha, Garash, Gazrek, Geshra, Gharek, Gorash, Grazha, Hazrak.

5. Heshra, Hizra, Horak, Ikran, Ilzha, Irash, Izrek, Jarash, Jazra, Jerak.

6. Jeshra, Jorash, Kadrak, Karash, Kazra, Keshra, Kharak, Khazra, Korash, Krazek.

7. Ladrak, Larash, Lazra, Leshra, Lorak, Lurash, Madrak, Marash, Mazra, Meshra.

8. Morak, Murash, Nadrak, Narash, Nazra, Neshra, Norak, Nurash, Odrak, Orash.

9. Ozra, Padrak, Parash, Pazra, Qadrak, Qarash, Qazra, Radrak, Rarash, Razra.

10. Reshra, Rorak, Sadrak, Sarash, Sazra, Tadrak, Tarash, Tazra, Vadrak, Zeshra.


**Surnames / family names, 50**


1. Ambercrest, Ashscale, Basaltcrest, Blackcinder, Brasscrest, Brightscale, Bronzehorn, Cinderclaw, Cloudscale, Coalcrest.

2. Coppercrest, Dawnscale, Deepember, Duskhorn, Embercrest, Firevein, Flintscale, Frostcrest, Gildedclaw, Glasshorn.

3. Goldscale, Granitecrest, Hearthscale, Highcinder, Ironcrest, Jadehorn, Keenclaw, Lavaward, Mooncrest, Obsidianhorn.

4. Opalscale, Paleember, Pearlcrest, Redscale, Ridgeclaw, Rubyhorn, Sandcrest, Sapphireclaw, Shalecrest, Silverhorn.

5. Smokeclaw, Snowcrest, Sparkvein, Steelcrest, Stoneclaw, Stormhorn, Sunscale, Thorncrest, Umberclaw, Whitecinder.



### 5.9 Hornborn — `hornborn`

**Given names, 100**


1. Abris, Adira, Aevin, Alara, Amris, Anira, Ardis, Asra, Avara, Aziel.

2. Belis, Berra, Brisel, Calis, Calra, Caris, Cevra, Cirel, Coris, Cyra.

3. Dalis, Daria, Delis, Demra, Desra, Dira, Dovris, Ebris, Edira, Elara.

4. Elris, Emra, Enira, Epris, Eris, Esra, Evira, Ezriel, Falis, Faris.

5. Felra, Feris, Fira, Galis, Gavris, Gelra, Gira, Halis, Havra, Helis.

6. Hera, Ibris, Idira, Ilara, Imris, Inara, Ipris, Ira, Isara, Ivara.

7. Jalis, Javris, Jelra, Jira, Kalis, Kavris, Kelra, Kira, Lalis, Lavra.

8. Leris, Lira, Malis, Mavra, Melra, Mira, Nalis, Navris, Nelra, Nira.

9. Obris, Odira, Olara, Omris, Ovara, Palis, Pavra, Pelis, Qira, Ralis.

10. Ravra, Relis, Sabria, Savris, Selra, Sira, Talis, Tavra, Velis, Zira.


**Surnames / family names, 50**


1. Ashveil, Bellshade, Blackcandle, Brighthollow, Bronzeveil, Candlebrook, Cinderlace, Coalveil, Copperhush, Crowlace.

2. Dawnveil, Deepcandle, Duskhollow, Emberhush, Eveninglace, Flintveil, Giltcandle, Glasshollow, Goldhush, Greyveil.

3. Hearthlace, Hollowbell, Inkveil, Ironcandle, Lanternhush, Lowflame, Mistlace, Mooncandle, Nightbell, Oakhush.

4. Paleveil, Quillshade, Raincandle, Redlace, Rosehush, Sablebell, Shadebrook, Silvercandle, Smokeveil, Softflame.

5. Starhollow, Stillbell, Sunlace, Thornveil, Umberbell, Velvetash, Violetcandle, Whisperbrook, Whiteflame, Wickshade.



### 5.10 Stonekin — `stonekin`

**Given names, 100**


1. Adan, Adra, Akar, Akra, Alun, Amra, Anok, Arda, Aruk, Aska.

2. Atun, Avra, Balan, Balra, Barok, Baska, Belun, Berra, Borak, Brana.

3. Brun, Bura, Cadan, Cadra, Calun, Carok, Caska, Cora, Dalan, Dalra.

4. Darok, Daska, Delun, Derra, Dorak, Drana, Drun, Dura, Edan, Edra.

5. Ekar, Ekra, Elun, Emra, Enok, Erda, Eruk, Eska, Etun, Evra.

6. Falan, Falra, Farok, Faska, Felun, Ferra, Forak, Frana, Frun, Fura.

7. Gadan, Gadra, Galun, Garok, Gaska, Gora, Halan, Halra, Harok, Haska.

8. Helun, Herra, Horak, Hrana, Hrun, Hura, Idan, Idra, Ikar, Ikra.

9. Ilun, Imra, Inok, Irda, Iruk, Iska, Itun, Ivra, Jalan, Jalra.

10. Jarok, Jaska, Jelun, Jerra, Jorak, Jrana, Kalun, Karok, Kelra, Kora.


**Surnames / family names, 50**


1. Ambercairn, Ashridge, Basaltstep, Bearcairn, Blackpeak, Boulderward, Broadledge, Cairnwalker, Chalkridge, Cliffstep.

2. Cloudcairn, Coldledge, Copperpeak, Deepcairn, Eagleledge, Emberridge, Farpeak, Flintstep, Frostcairn, Goatledge.

3. Graniteridge, Greyledge, Highcairn, Hollowpeak, Iceledge, Ironcairn, Keenridge, Lakepeak, Longledge, Mistcairn.

4. Moonridge, Northpeak, Oakeridge, Paleledge, Raincairn, Redpeak, Ridgestep, Rivercairn, Rockledge, Shalepeak.

5. Silvercairn, Skyledge, Snowridge, Stonepath, Stormcairn, Sunpeak, Tallridge, Thundercairn, Whiteledge, Windpeak.



### 5.11 Catfolk — `catfolk`

**Given names, 100**


1. Ami, Anri, Asha, Auri, Ayo, Bami, Bira, Bori, Cami, Chai.

2. Chira, Chori, Dami, Dira, Dori, Emi, Enri, Esha, Euri, Eyo.

3. Fami, Fira, Fori, Gami, Gira, Gori, Hami, Hira, Hori, Imai.

4. Imri, Isha, Iuri, Iyo, Jami, Jira, Jori, Kami, Kira, Kori.

5. Lami, Lira, Lori, Mai, Mairi, Masha, Mauri, Mayo, Meki, Miri.

6. Mori, Nami, Nari, Nasha, Nauri, Nayo, Neki, Niri, Nori, Omai.

7. Omri, Osha, Ouri, Oyo, Pami, Pira, Pori, Qami, Qira, Qori.

8. Rami, Rari, Rasha, Rauri, Rayo, Reki, Riri, Rori, Sami, Sari.

9. Sasha, Sauri, Sayo, Seki, Siri, Sori, Tami, Tari, Tasha, Tauri.

10. Tayo, Teki, Tiri, Tori, Umi, Vami, Vira, Wami, Yari, Zuri.


**Surnames / family names, 50**


1. Amberwhisker, Ashpaws, Bellstep, Blackreed, Brindlebrook, Brighttail, Bronzeclaw, Candlepaws, Cloudwhisker, Copperstep.

2. Dappletail, Dawnclaw, Dewpaws, Dustwhisker, Emberstep, Fernclaw, Fleetpaws, Frosttail, Goldwhisker, Grassstep.

3. Greyclaw, Hearthpaws, Honeytail, Ivorywhisker, Juniperstep, Lakeclaw, Larkpaws, Leopardtail, Lightwhisker, Maplestep.

4. Mistclaw, Moonpaws, Mosswhisker, Nightstep, Oakclaw, Paletail, Quickpaws, Rainwhisker, Reedstep, Rippleclaw.

5. Rosepaws, Sandwhisker, Silentstep, Silverclaw, Snowpaws, Softtail, Sunwhisker, Tawnyclaw, Thornstep, Willowpaws.



### 5.12 Lizardfolk — `lizardfolk`

**Given names, 100**


1. Asha, Ashik, Asra, Assik, Athra, Azik, Bash, Baresh, Bask, Bassa.

2. Besh, Besra, Bish, Bissik, Bosh, Brash, Chash, Chassa, Chesh, Chiss.

3. Dasha, Dashik, Dasra, Dassik, Dathra, Dazik, Esha, Eshik, Esra, Essik.

4. Ethra, Ezik, Fash, Faresh, Fask, Fassa, Fesh, Fesra, Fish, Fissik.

5. Fosh, Frash, Gash, Gassa, Gesh, Giss, Hash, Hashik, Hasra, Hassik.

6. Hathra, Hazik, Isha, Ishik, Isra, Issik, Ithra, Izik, Jash, Jaresh.

7. Jask, Jassa, Jesh, Jesra, Jish, Jissik, Josh, Jrash, Kash, Kassa.

8. Kesh, Kiss, Lasha, Lashik, Lasra, Lassik, Lathra, Lazik, Masha, Mashik.

9. Masra, Massik, Mathra, Mazik, Nasha, Nashik, Nasra, Nassik, Nathra, Nazik.

10. Osha, Oshik, Osra, Ossik, Othra, Ozik, Rasha, Rashik, Sasra, Zassik.


**Surnames / family names, 50**


1. Amberreed, Ashbank, Barkscale, Blackfen, Bluecrest, Bogwalker, Brackwater, Bronzeeye, Cattail, Claybank.

2. Coldpool, Copperreed, Deepmarsh, Dewscale, Driftwood, Duskbank, Embercrest, Fernwater, Flintscale, Floodreed.

3. Fogbank, Greenpool, Greycrest, Hearthreed, Heronscale, Hollowbank, Jadecrest, Lakewatch, Longreed, Marshlight.

4. Mirewalker, Moonpool, Mosscrest, Mudbank, Newtwatch, Palecrest, Rainpool, Redreed, Reedwatch, Ripplebank.

5. Rivercrest, Rootwater, Rushscale, Sandbank, Sedgewatch, Silverpool, Stillreed, Sunbank, Tidemark, Willowcrest.



## 6. Backgrounds and individual identity

These backgrounds provide opportunity hooks and starting experience tags, not extra free combat power. Tags influence autonomous expertise weighting only after eligibility checks. No background makes a race/class combination invalid. Any starting stat or equipment advantage must fit the existing hero-generation budget.

| ID | Background | Starting interest | Personal story seed | Natural shop demand |
| --- | --- | --- | --- | --- |
| bg_caravan_guard | Caravan guard | Protection | A caravan failed to return | Dependable guard weapon |
| bg_village_healer | Village healer | Recovery | An old patient joined the town | Recovery-compatible equipment |
| bg_poacher | Former poacher | Precision | A hunt crossed into forbidden land | Accurate bow or dagger |
| bg_militia | Militia veteran | Defence | Recognizes an enemy standard | Reliable low-risk weapon |
| bg_exiled_noble | Exiled noble | Reputation | Wants a useful deed, not pity | Fine workmanship within budget |
| bg_scholar | Field scholar | Discovery | A fragment describes an unusual effect | Experimental staff |
| bg_apprentice | Former apprentice | Mentorship | Wants to surpass a teacher | Affordable coherent upgrade |
| bg_deserter | Deserter | Redemption | Knows the Iron Banner's drills | Equipment for protecting others |
| bg_gravedigger | Gravedigger | Undead lore | An old cemetery has gone silent | Anti-undead effect |
| bg_ferryman | Ferryman | Rescue | Owes a family safe passage | Durable spear or staff |
| bg_hunter | Village hunter | Tracking | A predator no longer behaves naturally | Focused damage |
| bg_pit_fighter | Former pit fighter | Counters | A former rival seeks work | Guard plus retaliation |
| bg_temple_ward | Temple ward | Cleansing | Carries a damaged ceremonial tool | Cleanse-compatible equipment |
| bg_mapmaker | Mapmaker | Exploration | A pass appears on an old map | Reliable expedition weapon |
| bg_mason | Mason | Siegecraft | Recognizes weak masonry at the gate | Heavy impact weapon |
| bg_outcast | Community outcast | Belonging | Seeks a place without an old label | Functional gift over prestige |
| bg_cook | Expedition cook | Teamwork | Keeps a rescued party's recipe book | Affordable practical weapon |
| bg_relic_keeper | Relic keeper | Weapon history | A family item has been lost | Recovery of an actual tracked item |
| bg_scout | Border scout | Foresight | Knows a route used by raiders | Accurate or disruptive weapon |
| bg_retainer | Former retainer | Loyalty | A living former employer sends a request | Protective equipment |

A story seed becomes an active opportunity only after the game instantiates valid targets and resources. “Family item” must be an actual item record or an explicit quest objective, never a claim that an uncreated weapon exists in the inventory.

## 7. Personality, titles, and generated hero examples

Use the Living Heroes design for choice scoring. This table defines the intended creative meaning. Contradictions: Cautious/Reckless, Thrifty/Lavish, and Patient/Impulsive cannot be rolled together at creation. Other combinations are permitted when their effects remain coherent.

| ID | Trait | Decision preference | Limitation |
| --- | --- | --- | --- |
| cautious | Cautious | Recovery and known manageable danger | Gives up some risky opportunities |
| reckless | Reckless | Accepts greater uncertainty | Still respects hard safety and activity rules |
| loyal | Loyal | Guild ties and existing commitments | May stay with a merely adequate familiar weapon |
| curious | Curious | Unfamiliar useful effects | Must still evaluate suitability |
| ambitious | Ambitious | Challenges with real milestone value | Does not receive free progression |
| thrifty | Thrifty | Saves for meaningful upgrades | Can pass over small useful improvements |
| lavish | Lavish | Fine craftsmanship when affordable | Reserves survival/recovery money first |
| generous | Generous | Bounded assistance to companions | Cannot spend resources they do not own |
| proud | Proud | Independent achievements and respected work | No routine sabotage or tantrums |
| patient | Patient | Multi-day goals and preparation | Needs abandonment routes for impossible plans |
| impulsive | Impulsive | Near-term plausible opportunities | Bounded commitment switching |
| sentimental | Sentimental | Shared item and companion history | Serious functional need can overcome attachment |
| practical | Practical | Useful fit and value | No hidden perfect knowledge |
| competitive | Competitive | Matches an actual peer's achievement | Does not automatically hate peers |
| sociable | Sociable | Shared activities and mentorship | No free relationship points from idle presence |
| private | Private | Independent plans where feasible | Can still cooperate and fulfil assignments |

### Earned titles

Titles are examples with concrete triggers. Use existing achievement tracking where possible. Each hero displays at most one active title; retain the others in history. These triggers are prototype thresholds.

| Title | Trigger |
| --- | --- |
| Gatekeeper | Survive three sieges while serving as a reserved defender |
| Trollbane | Deal the finishing damage to a troll while its regeneration is suppressed |
| Gravewarden | Participate in three victories against the Pale Procession |
| Stormhand | Cause a supported lightning effect to damage three enemies in one battle |
| Oathkeeper | Complete three personal promises/opportunities accepted through real records |
| Last Lantern | Be the only surviving defender in a won siege |
| Trailfinder | Complete three scouting/recovery activities with valid discoveries |
| The Patient | Complete a personal goal spanning at least five game days |
| The Returned | Return alive from a rescue where the hero was the recorded missing target |
| Master of Ash | Suppress troll recovery or burn a rootbound enemy in three distinct victories |
| Shieldfriend | Trigger a real ally-protection effect in five distinct battles |
| Relicbearer | Win three battles using the same tracked historically notable weapon |

Fallback location bynames: `of Cinderwatch`, `of Reedford`, `of Highcairn`, `of Mossbridge`, `of Southbank`, `of Grey Hollow`, `of the Long Road`, `of Ember Quay`, `of White Hill`, `of Willow Crossing`. Substitute the established town name in the first entry if needed. These indicate residence or origin, not an earned title.

### Twelve example generated individuals

These are validation examples, not mandatory recruits or fixed heroes. All their names are present in the pools. Their eventual expertise remains autonomous.

| Name | Race / class | Background and traits | Likely starting interest | Possible personal initiative |
| --- | --- | --- | --- | --- |
| Maren Voss | Human Guardian | Caravan guard; loyal, cautious | Protecting allies | Organize recovery of a missing caravan |
| Ilyra Rainbloom | Elf Duelist | Pit fighter; proud, practical | Counterattacks | Challenge a known raider duelist |
| Dagna Ironrivet | Dwarf Warden | Village healer; patient, generous | Recovery | Ask for protection for a vulnerable expedition |
| Tansy Copperkettle | Halfling Ranger | Mapmaker; curious, private | Scouting | Offer an old route after validating a clue |
| Fizzi Lanternwick | Gnome Battlemage | Scholar; curious, thrifty | Elemental experiments | Request a test weapon with a known effect |
| Ashka Riverfang | Orc Guardian | Deserter; loyal, practical | Disrupting formations | Recognize a Banner officer's method |
| Nizz Lanternbit | Goblin Ranger | Scout; cautious, competitive | Accurate attacks | Propose scouting the sappers' approach |
| Keshra Bronzehorn | Dragonkin Duelist | Exiled noble; proud, patient | Precision | Seek a public deed to rebuild reputation |
| Savris Lanternhush | Hornborn Battlemage | Temple ward; ambitious, private | Cleansing or disruption | Study an enemy ritual after a real encounter |
| Karok Skyledge | Stonekin Warden | Ferryman; sociable, generous | Team protection | Propose a rescue along a known crossing |
| Nauri Reedstep | Catfolk Guardian | Militia veteran; sentimental, practical | Defence | Recover a fallen companion's tracked weapon |
| Esra Stillreed | Lizardfolk Battlemage | Apprentice; curious, cautious | Controlled elemental damage | Seek a compatible living mentor |

## 8. Enemy rules vocabulary and prototype numbers

### 8.1 Integration boundary

The following values make this catalogue testable, but do not instruct the implementer to replace the current combat clock. Translate a **round** into the existing engine's equivalent combat window. Preserve action counts, telegraph windows, cooldown ratios, and counterplay. If a faithful translation is impossible, list the missing capability before implementation.

Prototype reference: a basic hero has approximately **100 HP, 15 damage per ordinary action, 0% armour reduction, and 100 initiative**. This is a measuring scale, not a change to current hero stats. Enemy HP and damage in Section 10 are given on this scale. Initiative affects ordering, not free extra actions. Three heroes fighting five enemies is mechanically different from a three-versus-three fight; threat cost includes action count only provisionally and needs simulation calibration.

Enemy **cost** is an encounter construction budget. It is not tabletop Challenge Rating, XP, hero level, or gold. Reward tuning stays in the existing economy. Base profiles below are Tier I. When a higher siege budget becomes available, add a coherent composition before merely inflating every stat.

### 8.2 Shared exact rules

- **Default attack:** one physical direct attack per normal action, targeting the available frontline hero with the lowest current HP percentage; stable entity ID breaks ties. With no frontline, target any hero by the same rule. Hero formation is a logical priority tag, not a new grid. Existing taunt/protection rules override where explicitly supported.
- **Front/back rank:** logical target priority for ordinary melee. A back-rank unit becomes ordinarily accessible when no living front-rank unit remains. Ranged attacks and explicitly stated reach/priority abilities can select either rank. Every unit remains targetable by some supported option. Do not introduce permanently unreachable enemies.
- **Damage:** armour reduces physical damage by its stated percentage. Nonphysical damage ignores physical armour. Apply direct damage multipliers additively within their category, then multiply categories; cap total reduction at 60% except an explicitly stated brief shield. Floor final nonzero damage at 1 after rounding down.
- **Resistance:** 30% less damage from the listed element. **Weakness:** 25% more damage from the listed type/effect. These are this game's proposed values, not official monster rules. If multiple weaknesses apply to one hit, use the strongest single weakness rather than multiplying them.
- **Burn:** 4 fire damage at the end of each round for 2 rounds, nonstacking; reapplication refreshes remaining duration. This is also a trigger tag for specified enemy weaknesses.
- **Poison:** 3 poison damage at round end for 3 rounds, nonstacking; refresh on reapplication. Some attacks add the status separately from direct poison damage.
- **Bleed:** 3 physical damage ignoring armour at round end for 2 rounds, nonstacking. Constructs and skeletal units do not receive Bleed unless a card explicitly says otherwise.
- **Slow:** initiative -25 for the target's next two actions, nonstacking. Does not delete actions or reduce HP.
- **Exposed:** physical armour -15 percentage points, floored at 0, until the end of the target's next action. Nonstacking.
- **Weakened:** outgoing direct damage -20% for the next two actions. Does not reduce fixed status damage or healing.
- **Mortal Wound:** healing received -40% until the end of the target's next two actions; nonstacking.
- **Silence:** cannot use a tagged spell action on the next action. Uses an ordinary attack instead. No lost action. Expires after that action.
- **Interrupt:** cancels one visibly pending channel. The actor makes its ordinary attack at the would-have-completed action instead. Does not skip an entire action. No pending channel means no interrupt effect.
- **Guard:** temporary damage-absorption points. Absorb damage after reduction; unused points expire at the end of the recipient's next action unless otherwise stated. Guard never heals HP and does not intercept damage to another target by default.
- **Taunt:** next ordinary single-target attack chooses the taunter if legal. Does not redirect area attacks, structural attacks, or an explicitly telegraphed prey attack. One pending taunt per target.
- **Cleanse:** remove one eligible negative effect in priority order Mortal Wound, Silence, Weakened, Poison, Burn, Slow, Bleed, Exposed. Does not cancel an enemy channel or remove structural preparation.
- **Control protection:** after a hero's action is affected by Silence, that hero cannot be Silenced again until after their following action. No first-batch enemy stuns, petrifies, charms away, or permanently reduces a hero's max HP.
- **Area attack:** stated percentage of listed attack damage to each of at most three eligible targets. Secondary damage does not recursively trigger area/chain/counter effects. No friendly fire unless explicitly stated.
- **Healing and regeneration:** cannot exceed maximum HP. Dead units do not recover unless a card explicitly grants a bounded raise. Flat regeneration and direct healing are different triggers where specified.
- **Swarms:** one entity, one HP pool, one action. Their many visible bodies do not each roll or attack.
- **Flight:** visual/priority trait. Flying enemies remain reachable by ordinary attacks during their engagement windows. No immunity to all melee.
- **Structures:** wall and forge use current run rules. All direct siege-engine damage is routed through the existing siege consequence layer. The prototype structure values here assume a normalized 100-point wall. If no separate wall pool exists, map once to the actual integrity system; never apply both wall loss and duplicate forge loss for the same event.

### 8.3 Telegraph timing and durations

An ability “starts on action 2, completes on action 3” first announces at that actor's action 2; its next action completes it if alive and uninterrupted. Until completion, the intended target and remaining preparation are visible. Fixed channels do not complete immediately on spawn. Target death causes retargeting only where specified; otherwise the attack becomes an ordinary attack.

A periodic ability states its action schedule. Dead, banished, or defeated units do not execute queued actions. Effects from a killed support cease immediately where described. Never grant extra actions on summoning or resurrection in the same round. New arrivals act no earlier than the next round.

### 8.4 Global encounter caps

Normal army: at most five active enemy entities; captain army: at most four accompanying entities plus the captain; major monster: at most two accompanying entities. At most one recovery support, one control support, one siege engine, and one captain per encounter. Summoned/raised entities count toward the cap. A full battlefield causes the summon/raise to fail without consuming its one-use resource, but the action is still spent; it may retry only on the next scheduled eligible action.

At most two nonstacking enemy-generated afflictions from the above list may be active on a hero at once. On application of a third, keep the two earlier effects until one expires; refreshing an existing effect remains legal. This cap does not silently suppress existing player weapon mechanics. Apply these caps to the authored encounters, not as an unreviewed global change to current combat.

Anti-loop rule: retaliatory damage cannot trigger retaliation, resurrection occurs at most once per entity, copied effects never copy a copying/raising/summoning rule, and enemy support buffs of the same name do not stack.

### 8.5 Formation, targeting, and status timing defaults

Use the existing formation system if one exists. Otherwise create logical ranks at battle setup without a new player control: the living hero with the highest effective physical durability is frontline, and the other heroes are backline. For this prototype, durability is max HP divided by (1 - physical armour fraction), with stable hero ID breaking ties. Do not recalculate that role after every hit; when the frontline falls, the next most durable living hero becomes frontline. Enemies use the ranks in their cards. A hero with a melee-only weapon attacks the frontline unless a real expertise/effect explicitly permits another target. Bows and ranged spell attacks may select either rank. A staff is not automatically a ranged spell weapon unless the actual attack rules support that capability.

Hero targeting remains autonomous. Among legal targets, prefer an interruptible pending structural/raise/area channel when the equipped action can actually interrupt it; otherwise prefer a vulnerable support whose removal materially reduces immediate pressure, then a target that can be defeated with the current action, then a good weapon matchup. Use personality and persistent preferences to break close choices. This describes a bounded tactical evaluator, not knowledge of future rolls. A hero should not repeatedly cast an interrupt at a nonchanneling target or waste a cleanse on a healthy companion.

A default spell fallback uses the hero's real ordinary nonspell attack and existing damage values. If the current game has no such fallback, Silence requires a separately implemented legal fallback before enabling affected enemies; it must not silently skip the hero's turn. A ranged ordinary enemy attack can choose either rank using the same HP-percentage preference unless its card supplies a different target rule. Beastcaller targeting has no effect until the caller has actually hit a living hero; then use the most recent still-living target, falling back to ordinary targeting if that target dies.

A round includes each eligible actor's scheduled action once, ordered by initiative with stable ties. Actor-local action counters begin at 1 when an entity first acts. A raised unit starts a fresh counter but preserves spent once-per-battle resources, and cannot act before the next round. New buffs and debuffs affect subsequent actions; they never retroactively reorder actions already taken. An initiative change recomputes ordering among actors that have not acted yet, and never grants another action in that round.

Burn, Poison, and Bleed tick at the round end in which they were applied, then their remaining tick count decreases; a two-round Burn therefore causes exactly two ticks unless cleansed or its recipient dies. Reapplication refreshes remaining ticks without an immediate bonus tick. Actor-action durations count that recipient's actions after application; the effect expires after the specified affected action completes. Exposed follows its explicit next-action expiry. Guard from an ability granted on the recipient's own action remains through its next action, not expiring immediately as it is granted. Mark durations use the end of the next complete round after application. Dead targets lose temporary effects; any raised unit begins without prior temporary buffs/debuffs.

Creature tags are explicit. Dire Wolf, Giant Spider, Giant Serpent, and the Matriarch are beast-tagged. Wolf Rider is one humanoid/rider entity and does not gain a second beast buff. Werewolf is shapechanger-tagged but qualifies for the Beastcaller's authored pack coordination only through an explicit `pack_eligible` tag. For this catalogue the caller affects `beast OR pack_eligible`, not every unit of the Covenant. Treants are plant-tagged, Elementals elemental-tagged, and Wyvern dragon-tagged. Necromancer and Acolyte are living humanoids, despite their force affiliation. Wraith and Banshee are spirit/undead-tagged. Bone Colossus is undead and construct-like for its stated exclusions, but is never a standard raise target. A captain inherits only abilities explicitly in its own card.

No enemy is automatically immune to direct poison damage because Poison status is excluded; those are distinct fields. The first content version uses only the exclusions stated on each card. Avoid importing a whole tabletop immunity list behind the player's back. Engine fire weakness and the specific creature weaknesses above must be visible in the bestiary when known.

## 9. The three invading forces

### The Iron Banner — an army that wants the forge

A coalition of deserters, mercenaries, and opportunistic warbands. It takes toll roads and demands weapons as tribute. It fields trained shields, cheap skirmishers, sappers, and hired monsters. Red cloth, dark iron, patched leather, and stolen trade seals identify membership. The people within it have varied races; its ideology is conquest and profit.

**Combat identity:** organized protection, timed breach attacks, wounded-target pressure. **Strategic weakness:** remove a support or break the protected specialist before its plan completes. **Narrative hook:** a deserter can recognize a drill or officer without knowing the army's secret future composition.

### The Pale Procession — a siege that never tires

A necromantic order moves through old burial roads to recover a sealed relic beneath the town. Its army includes animated remains, living grave attendants, and a few bound spirits. Bone-white markings, dark violet cloth, tarnished bronze, and cold lanterns identify it.

**Combat identity:** endurance, limited reanimation, weakened recovery, pressure on fragile defenders. **Strategic weakness:** interrupt support, exploit skeletal fragility, use appropriate holy/anti-undead effects if the current content supports them. Fire is useful in selected encounters, not a universal bonus against every undead.

### The Thorn Covenant — beasts driven against civilization

A small group of oath-bound callers uses scent, binding marks, and promises to direct beasts and angry natural creatures. Intelligent participants pursue their own interests. Ordinary wolves attack because of a handler or a local crisis; an army of wolves does not spontaneously conduct engineering.

**Combat identity:** pack focus, exposure, poison, flyers, and hard-to-ignore large creatures. **Strategic weakness:** remove the coordination source, prepare recovery/cleanse, and exploit each creature's specific limitation. Fire can counter roots, but is poor against a fire elemental.

These are content-group names. Map them onto current faction IDs if the repository already has approved equivalents. Do not create six parallel factions by accident.

## 10. Thirty-six standard enemies

### 10.1 Compact stat register

`Arm` is physical damage reduction percentage. `Init` is ordering priority. `F/B` is default logical rank. Cards below define special behaviour; any absent elemental resistance means none. Cost is provisional.

| ID | Enemy | HP | Attack | Arm | Init | Cost | Rank | First eligible siege |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| ib_goblin_skirmisher | Goblin Skirmisher | 24 | 7 | 0 | 115 | 2 | F | 1 |
| ib_goblin_sapper | Goblin Sapper | 26 | 5 | 0 | 85 | 4 | B | 2 |
| ib_wolf_rider | Goblin Wolf Rider | 38 | 9 | 0 | 120 | 4 | F | 2 |
| ib_hobgoblin_shield | Hobgoblin Shieldbearer | 55 | 7 | 25 | 80 | 5 | F | 1 |
| ib_hobgoblin_archer | Hobgoblin Archer | 30 | 10 | 10 | 100 | 4 | B | 1 |
| ib_war_drummer | War Drummer | 34 | 4 | 5 | 90 | 5 | B | 2 |
| ib_orc_breaker | Orc Gatebreaker | 58 | 13 | 10 | 80 | 6 | F | 2 |
| ib_orc_shaman | Orc War Shaman | 32 | 8 | 0 | 95 | 5 | B | 3 |
| ib_bugbear | Bugbear Ambusher | 44 | 11 | 5 | 110 | 5 | F | 2 |
| ib_ogre | Ogre Ram-Bearer | 95 | 16 | 5 | 60 | 9 | F | 3 |
| ib_troll | Troll Mercenary | 78 | 12 | 0 | 75 | 9 | F | 3 |
| ib_deserter | Deserter Crossbowman | 35 | 11 | 10 | 95 | 4 | B | 1 |
| pp_skeleton_guard | Skeleton Spearman | 30 | 7 | 10 | 85 | 3 | F | 1 |
| pp_skeleton_archer | Skeleton Archer | 23 | 9 | 0 | 95 | 3 | B | 1 |
| pp_zombie | Zombie | 54 | 7 | 0 | 50 | 3 | F | 1 |
| pp_ghoul | Ghoul | 36 | 9 | 0 | 110 | 5 | F | 2 |
| pp_wight | Wight | 55 | 11 | 15 | 95 | 7 | F | 3 |
| pp_necromancer | Necromancer | 34 | 8 | 0 | 90 | 7 | B | 3 |
| pp_acolyte | Grave Acolyte | 29 | 5 | 0 | 80 | 5 | B | 2 |
| pp_ogre_zombie | Ogre Zombie | 110 | 14 | 0 | 40 | 8 | F | 3 |
| pp_banshee | Banshee | 42 | 8 | 0 | 105 | 7 | B | 3 |
| pp_wraith | Wraith | 47 | 10 | 10 | 110 | 7 | F | 3 |
| pp_black_knight | Undead Black Knight | 73 | 12 | 30 | 75 | 8 | F | 3 |
| pp_grave_rats | Grave Rat Swarm | 29 | 6 | 0 | 115 | 3 | F | 2 |
| tc_dire_wolf | Dire Wolf | 41 | 10 | 0 | 110 | 4 | F | 1 |
| tc_giant_spider | Giant Spider | 35 | 7 | 0 | 100 | 4 | B | 2 |
| tc_harpy | Harpy | 31 | 8 | 0 | 115 | 5 | B | 2 |
| tc_minotaur | Minotaur | 72 | 14 | 10 | 75 | 7 | F | 3 |
| tc_manticore | Manticore | 58 | 12 | 5 | 105 | 7 | B | 3 |
| tc_wyvern | Wyvern | 82 | 14 | 10 | 95 | 10 | F | 4 |
| tc_treant | Thornbound Treant | 96 | 12 | 20 | 45 | 9 | F | 3 |
| tc_earth_elemental | Earth Elemental | 102 | 13 | 30 | 40 | 10 | F | 4 |
| tc_fire_elemental | Fire Elemental | 60 | 10 | 0 | 105 | 8 | F | 4 |
| tc_giant_serpent | Giant Serpent | 65 | 11 | 5 | 85 | 6 | F | 3 |
| tc_werewolf | Werewolf | 61 | 11 | 10 | 110 | 8 | F | 3 |
| tc_beastcaller | Beastcaller | 33 | 6 | 0 | 90 | 5 | B | 2 |

### 10.2 Iron Banner enemy cards

#### ib_goblin_skirmisher — Goblin Skirmisher

**Recognition/art:** small goblin with a hooked short blade, a scavenged buckler, and a red armband. **Siege job:** climb rubble and crowd defenders while specialists work. **Rule:** first ordinary attack against this unit deals 20% less damage; after that its dodging advantage is spent. This is damage reduction, not an independent miss roll. **Strength:** cheap pressure and first-hit survival. **Weakness:** 24 HP and no lasting protection; a second hit or group damage removes it. **Synergy:** a drummer makes its low damage relevant. **Forecast:** “Goblin climbers are gathering below the wall.” **Loot flavour:** patched equipment, ordinary coin. No new mandatory material.

#### ib_goblin_sapper — Goblin Sapper

**Recognition/art:** satchel of powder, fuse cord, short pick; no modern explosives. **Siege job:** destroy the gate under cover. **Rule:** begins `Powder Charge` on action 2 and completes on action 3, dealing 4 normalized structural damage; then ordinary attacks only. Interrupt cancels the one charge permanently. Killing the sapper cancels it. **Strength:** creates time pressure behind a shield. **Weakness:** low HP; ranged targeting or an interrupt defeats the plan. **Synergy:** Shieldbearer. **Forecast:** “Powder sappers are preparing a breach.” **Guardrail:** at most one per early siege; never also add an engine during the introductory encounter.

#### ib_wolf_rider — Goblin Wolf Rider

**Recognition/art:** goblin riding a lean wolf, short spear, red saddlecloth. One combat entity, not two lives. **Siege job:** rush an opening and harry injured defenders. **Rule:** actions 1 and 4 select the hero with lowest HP percentage in either rank and deal normal attack damage. Other actions use default targeting. **Strength:** threatens fragile wounded heroes. **Weakness:** no armour or recovery; Guard and healing blunt the priority attack. **Synergy:** Crossbowman or Harpy exposure. **Forecast:** “Wolf riders will strike at wounded defenders.” Dismounting is visual defeat, not a free extra goblin spawn.

#### ib_hobgoblin_shield — Hobgoblin Shieldbearer

**Recognition/art:** broad rectangular shield, disciplined stance, red-black uniform. **Siege job:** escort an archer, sapper, or officer. **Rule:** at battle start selects the lowest-HP allied back-rank specialist; 30% of its incoming direct damage is redirected to the shieldbearer after the recipient's reduction. Redirection ceases when the shieldbearer dies and cannot chain through another protector. **Strength:** buys time with armour and HP. **Weakness:** elemental damage or armour penetration removes the protector more efficiently; group attacks still hurt both separately. **Forecast:** “Shieldbearers are escorting the enemy's specialists.” One protector per encounter in this version.

#### ib_hobgoblin_archer — Hobgoblin Archer

**Recognition/art:** longbow, spare arrows, matching armour plates. **Siege job:** disciplined covering fire. **Rule:** on actions 2 and 5, prioritizes the lowest-current-Guard hero in either rank, then lowest HP percentage; fires for normal damage. Other actions are ranged default attacks. **Strength:** pressures poorly protected heroes. **Weakness:** low HP and no defensive ability beyond light armour. **Synergy:** Shieldbearer or Gatebreaker. **Forecast:** “Hobgoblin archers are taking positions.” It does not secretly know which hero the player will heal next.

#### ib_war_drummer — War Drummer

**Recognition/art:** visibly carried war drum, mallets, hearing protection made of cloth. Race may use the available portrait; role is not race-locked. **Siege job:** keep the assault coordinated. **Rule:** while alive, allied Iron Banner units have +10 initiative and +10% direct attack damage. Same buff does not stack. This is static support, not an indefinitely growing ramp. **Strength:** improves multiple attackers. **Weakness:** poor personal damage; removing it immediately removes the aura. **Synergy:** several cheap attackers. **Forecast:** “War drums are keeping the raiders in step.”

#### ib_orc_breaker — Orc Gatebreaker

**Recognition/art:** orc in salvaged heavy work armour with a maul. **Siege job:** batter defenders and doors. **Rule:** action 2 and every third action thereafter makes `Crushing Blow`: normal damage and Exposed on the frontline target. **Strength:** creates openings for physical allies. **Weakness:** slow initiative and no self-protection; cleanse, Guard, or early burst reduce the danger. **Synergy:** Archer and Wolf Rider. **Forecast:** “Heavy mauls have been sighted among the raiders.” This is a hostile profession within the Banner, not a racial trait of playable orcs.

#### ib_orc_shaman — Orc War Shaman

**Recognition/art:** staff with tied bells, red ritual cloth, bone-and-wood charms. **Siege job:** disrupt the town's strongest spell support. **Rule:** on actions 2 and 5 applies Silence to the living hero who has dealt the most spell-tagged direct damage so far, if any; the action also deals 50% attack damage to that hero. If nobody qualifies, makes a normal ranged spirit attack. **Strength:** targets an observed behaviour. **Weakness:** low HP and no healing; physical pressure and control protection prevent lockout. **Forecast:** “A war shaman carries charms against spellcasters.” Counts as control support.

#### ib_bugbear — Bugbear Ambusher

**Recognition/art:** tall shaggy humanoid with long arms, heavy hood, oversized knife. **Siege job:** climb a weak point and strike behind the line. **Rule:** first action targets the back-rank hero with lowest HP percentage, dealing 125% attack damage. After that, ordinary frontline attacks. **Strength:** threatening opening hit. **Weakness:** the opening is forecast and finite; protection or an early Guard absorbs it. **Synergy:** ordinary infantry keeps attention on the gate. **Forecast:** “Large climbers are moving toward the side wall.” No hidden prebattle injury roll.

#### ib_ogre — Ogre Ram-Bearer

**Recognition/art:** huge thick-limbed humanoid carrying a tree-trunk ram, crude helmet. **Siege job:** mobile battering ram. **Rule:** begins `Gate Rush` on action 2, completes on action 3 for 5 structural damage, then attacks defenders. Interrupt cancels this rush permanently. **Strength:** high HP and heavy attacks. **Weakness:** slow, lightly armoured, and the rush has a response window. **Synergy:** Shieldbearer can protect another specialist while the ogre occupies the front. **Forecast:** “An ogre is carrying a ram toward the gate.” No extra ram engine in the same encounter during introductory use.

#### ib_troll — Troll Mercenary

**Recognition/art:** lanky long-armed giant with a hooked nose, rough green-grey skin, regenerative scars. **Siege job:** absorb defender effort and keep advancing. **Rule:** heals 6 HP at the start of its action if it took neither fire nor acid damage since its previous action. At 0 HP it dies normally; no resurrection. **Strength:** punishes weak unfocused damage. **Weakness:** fire or acid suppresses recovery; concentrated heavy damage can overcome it without either. **Synergy:** armour support, not a second healer. **Forecast:** “A troll marches with the mercenaries. Fire will stop its recovery.” No severed-limb subsystem is proposed.

#### ib_deserter — Deserter Crossbowman

**Recognition/art:** mixed town armour with removed insignia, windlass crossbow, red contract ribbon. **Siege job:** armour-piercing cover. **Rule:** every second action fires a bolt that ignores 15 percentage points of physical armour. Other actions are ordinary ranged shots. **Strength:** pressures armoured defenders. **Weakness:** ordinary HP; Guard still works. **Synergy:** infantry and a drummer. **Forecast:** “Deserters are bringing heavy crossbows.” Any supported humanoid ancestry can supply the art, but one core asset is enough initially.

### 10.3 Pale Procession enemy cards

#### pp_skeleton_guard — Skeleton Spearman

**Recognition/art:** exposed bones, rusted spear, small battered shield. **Siege job:** tire defenders and hold a narrow breach. **Rule:** takes 25% extra damage from explicit impact-tagged attacks. Poison status and Bleed do not apply. It does not independently rise again. **Strength:** resists biological attrition effects and can screen a caster. **Weakness:** fragile bones; impact or ordinary focused damage. **Synergy:** Necromancer can raise it once. **Forecast:** “Skeleton spearmen are marching in ranks.” Impact must map to an existing weapon/effect tag; do not invent a compulsory new weapon family.

#### pp_skeleton_archer — Skeleton Archer

**Recognition/art:** bony hands holding an old bow, arrow bundle tied to the ribs. **Siege job:** tire defenders with ranged volleys. **Rule:** same impact weakness and Poison/Bleed exclusions as Spearman; ordinary ranged attacks. **Strength:** attacks either rank with low-cost cover fire. **Weakness:** only 23 HP and no special defensive effect. **Synergy:** Zombies screen it while it shoots. **Forecast:** “Bone archers are assembling behind the dead.” Simplicity is intentional; every creature does not need an activated ability.

#### pp_zombie — Zombie

**Recognition/art:** clothed walking corpse, slack posture, crude grasping hands. Avoid gore-dependent readability. **Siege job:** soak damage and force a prolonged defence. **Rule:** first time a direct attack would defeat it, survives at 1 HP; this does not trigger against fire or anti-undead-tagged direct damage. Once spent it cannot trigger again, even after a raise. Poison status does not apply. **Strength:** one stubborn survival. **Weakness:** slow and low damage; follow-up damage or an appropriate finisher. **Synergy:** Acolyte can sustain it before the survival triggers. **Forecast:** “The dead are advancing slowly, but some refuse to fall.”

#### pp_ghoul — Ghoul

**Recognition/art:** gaunt corpse-creature, elongated claws, hunched hungry posture. **Siege job:** exploit wounded defenders. **Rule:** actions 2 and 5 inflict Mortal Wound alongside an ordinary hit on the lowest-HP-percentage legal hero. No paralysis. **Strength:** makes recovery less effective. **Weakness:** low HP, no armour, cleanse or preemptive Guard. **Synergy:** rats or a wight add sustained pressure. **Forecast:** “Ghouls are prowling behind the corpse line. Their wounds hinder healing.”

#### pp_wight — Wight

**Recognition/art:** preserved undead warrior, faded funeral armour, cold glowing eyes. **Siege job:** sustain a disciplined undead assault. **Rule:** on actions 2 and 5 drains life: ordinary attack damage, then heals itself for 40% of actual HP damage dealt, capped at 6 HP. Guarded damage that does not reach HP supplies no healing. **Strength:** benefits from exposed injured heroes. **Weakness:** Guard, focused damage, and supported anti-undead effects. **Synergy:** Ghoul makes the opposing recovery race harder. **Forecast:** “An armoured wight leads the dead. Its blade restores it.” No permanent max-HP loss.

#### pp_necromancer — Necromancer

**Recognition/art:** living robed caster, bone staff, ledger or funeral tags, ritual mask. **Siege job:** return a fallen troop to the assault. **Rule:** on action 2, or a later action if no valid corpse existed, begins a one-action `Raise Dead` channel targeting the earliest-defeated allied standard Skeleton or Zombie. On completion, restores it at 35% maximum HP. One successful raise per caster per battle and one per entity. Raised units act next round. After success, ordinary ranged shadow attacks. **Strength:** converts a defender's earlier work into renewed pressure. **Weakness:** fragile and interruptible; interruption spends the one raise attempt permanently. **Forecast:** “A necromancer will try to raise the fallen.” Counts as recovery support.

#### pp_acolyte — Grave Acolyte

**Recognition/art:** living attendant with a black lantern and ash-white shawl. **Siege job:** sustain bound undead. **Rule:** actions 2 and 5 restore 9 HP to the most injured allied undead, excluding spirits; other actions use a ranged shadow attack. No self-healing unless the acolyte is actually undead under a separately defined variant, which this card is not. **Strength:** rewards protecting durable dead. **Weakness:** low HP; focus or Mortal Wound on its patient. **Synergy:** Zombie or Black Knight. **Forecast:** “A lantern-bearer is tending the dead.” Cannot accompany a Necromancer or Lich under the recovery-support cap.

#### pp_ogre_zombie — Ogre Zombie

**Recognition/art:** enormous corpse with a broken helm and a chained timber beam. **Siege job:** immovable mass at the gate. **Rule:** Poison status does not apply. Actions 3 and 6 sweep up to two frontline heroes for 70% attack damage each. It lacks the ordinary Zombie's 1-HP survival. **Strength:** high HP and limited group pressure. **Weakness:** extremely slow; no armour or self-recovery; concentrated damage is effective. **Synergy:** archers behind it. **Forecast:** “An ogre corpse carries a beam toward the gate.” It is not also a structural-damage ram unless a separate card says so.

#### pp_banshee — Banshee

**Recognition/art:** translucent mourning figure, torn veil, open mouth, cold mist. **Siege job:** weaken defenders through a dreadful cry. **Rule:** begins `Wail` on action 2 and completes on action 3, dealing 50% attack as shadow damage to all heroes and applying Weakened for one action. One wail per battle; Interrupt cancels it permanently. Other attacks are ranged shadow. **Strength:** coordinated pressure on the whole party. **Weakness:** fragile; interrupt, priority damage, or recovery. **Forecast:** “A mourning spirit is preparing a dreadful cry.” No instant death or forced hero removal.

#### pp_wraith — Wraith

**Recognition/art:** hooded smoke-like figure with visible hands and one readable bright eye line. **Siege job:** pass through a breach and pressure a rear defender. **Rule:** actions 1 and 4 strike the lowest-HP back-rank hero with shadow damage; otherwise shadow default attacks. Has 30% cold resistance, but no physical immunity. **Strength:** bypasses frontline priority at known times. **Weakness:** modest HP and no recovery; Guard and focused damage work. **Synergy:** a Black Knight holds the front. **Forecast:** “A wraith has been seen drifting above the graves.”

#### pp_black_knight — Undead Black Knight

**Recognition/art:** sealed dark plate armour, broken heraldry, skeletal hand on a broad sword. **Siege job:** hold the breach while support works. **Rule:** on actions 2 and 5 receives 12 Guard and Taunts the hero who dealt the most direct damage last round. Poison and Bleed do not apply. **Strength:** high physical armour and a visible defensive cycle. **Weakness:** nonphysical damage or penetration; remove its support rather than endlessly hitting protected plate. **Synergy:** one recovery support. **Forecast:** “A black-armoured corpse is guarding the procession.” Not a captain despite its appearance.

#### pp_grave_rats — Grave Rat Swarm

**Recognition/art:** several rats around a broken grave lantern; one unified silhouette and health bar. **Siege job:** harass wounded defenders beneath the fighting line. **Rule:** attack selects the lowest-HP-percentage hero in either rank and deals ordinary damage. Takes 25% extra damage from area attacks. No Poison on this version. **Strength:** low-cost persistent pressure. **Weakness:** low HP and area weakness. **Synergy:** Ghoul. **Forecast:** “Grave rats are spreading through the drains.” Cannot steal inventory, destroy named weapons, or multiply after defeat.

### 10.4 Thorn Covenant enemy cards

#### tc_dire_wolf — Dire Wolf

**Recognition/art:** oversized wolf, thick neck, scarred muzzle, a simple binding cord if controlled. **Siege job:** rush breaches and isolate a defender. **Rule:** first direct attack each round against a hero already hit by another Covenant unit this round gains +20% damage. One bonus per wolf per round. **Strength:** rewards coordinated focus. **Weakness:** no armour; remove pack support, use protection, or kill one attacker early. **Synergy:** Beastcaller. **Forecast:** “Large wolves are being driven toward the breaches.” A wolf-only encounter is a driven incursion, not a military engineering siege.

#### tc_giant_spider — Giant Spider

**Recognition/art:** large eight-legged spider with readable fangs and a pale web bundle. **Siege job:** foul a gatehouse and slow defenders. **Rule:** actions 2 and 5 spit web, applying Slow to the hero with highest initiative; other actions bite for physical damage. Counts as control support. **Strength:** disrupts fast attackers' ordering. **Weakness:** modest HP; cleanse, ranged focus, or fire damage with the ordinary +25% weakness. **Synergy:** Minotaur or wolf. **Forecast:** “Giant spiders are webbing the outer timbers.” No permanent immobilization.

#### tc_harpy — Harpy

**Recognition/art:** winged humanoid with taloned legs and a prominent throat/feather ruff. **Siege job:** unsettle watchmen from above. **Rule:** actions 2 and 5 use `Shriek`, applying Exposed to the highest-armour hero in either rank and dealing 50% attack as physical damage. Other actions are claw attacks during a visible dive. **Strength:** makes heavy defenders less secure. **Weakness:** very low HP; reachable during ordinary engagement, no melee immunity. **Synergy:** Manticore or wolves. **Forecast:** “Harpies are circling the wall.” This adaptation deliberately avoids long charm lockouts.

#### tc_minotaur — Minotaur

**Recognition/art:** bull-headed humanoid, thick horns, shoulder armour, large axe. **Siege job:** charge the gate and break the front. **Rule:** begins `Horn Charge` on action 1, completes on action 2 for 150% attack damage to one frontline hero plus Exposed. One charge. Other actions ordinary. Interrupt cancels the charge. **Strength:** heavy telegraphed opening. **Weakness:** slow; Guard, interruption, or burst before completion. **Synergy:** ranged support exploits Exposed. **Forecast:** “A minotaur is clearing space for a charge.” No instant structural damage on spawn.

#### tc_manticore — Manticore

**Recognition/art:** leonine body, humanoidlike face, batlike wings, obvious tail spikes. **Siege job:** bombard defenders from the approach. **Rule:** actions 2 and 5 fire tail spikes at up to two heroes, dealing 65% attack damage each. Other actions are ordinary ranged spikes. **Strength:** pressures multiple heroes. **Weakness:** moderate HP and limited armour; recovery and focused damage. **Synergy:** a heavy frontline creature. **Forecast:** “A manticore is circling beyond the palisade.” Uses no unlimited spawned spike hazards.

#### tc_wyvern — Wyvern

**Recognition/art:** two legs, two wings, long venomous tail. This silhouette distinguishes it from a four-legged dragon. **Siege job:** dive on exposed defenders. **Rule:** actions 2 and 5 sting the lowest-HP-percentage hero in either rank for ordinary physical damage and Poison. Other attacks ordinary melee after landing. **Strength:** high HP and dangerous target pressure. **Weakness:** cleanse and Guard, concentrated attacks during engagement. **Synergy:** Harpy exposure, subject to threat budget. **Forecast:** “A wyvern has been sighted. Prepare for venomous dives.” No breath weapon or total melee immunity.

#### tc_treant — Thornbound Treant

**Recognition/art:** moving old tree, branch arms, roots forming feet, binding runes tied into bark. **Siege job:** tear barricades and shelter smaller creatures. **Rule:** first direct hit against it each round causes 3 physical retaliation damage to the attacker; retaliation cannot trigger further reactions. Has fire weakness (+25%). **Strength:** high HP and armour discourage many small direct hits. **Weakness:** fire or heavy fewer hits; fixed retaliation does not punish damage-over-time ticks. **Synergy:** Beastcaller and small creatures. **Forecast:** “A bound walking tree is approaching. Its bark catches fire.” Not all treants are hostile in the setting.

#### tc_earth_elemental — Earth Elemental

**Recognition/art:** compact boulder body, separated rock limbs, glowing cracks; no ornate armour. **Siege job:** crush a prepared breach. **Rule:** actions 2 and 5 gain 15 Guard; has Slow immunity. Takes +25% damage from explicitly tagged impact or shatter effects, but not from every physical hit. **Strength:** huge HP and strong armour. **Weakness:** very slow, vulnerable to appropriate impact/penetration, no healing. **Synergy:** light ranged support. **Forecast:** “Bound stone is moving beneath the hillside.” Cannot burrow directly into the forge and bypass the battle.

#### tc_fire_elemental — Fire Elemental

**Recognition/art:** upright flame silhouette with a dark ember core. **Siege job:** burn timber and force defenders to protect themselves. **Rule:** direct attacks are fire damage; actions 2 and 5 apply Burn to their target. Fire resistance (30%), cold weakness (+25%), Poison and Bleed do not apply. **Strength:** punishes fire-only preparation. **Weakness:** cold or ordinary nonfire focused damage; Guard and cleanse. **Synergy:** a slow armoured bodyguard, not a Treant whose weakness is cancelled by narrative convenience. **Forecast:** “A bound flame is advancing with the beasts.” No passive structure damage every frame.

#### tc_giant_serpent — Giant Serpent

**Recognition/art:** massive coiled snake, visible fangs, a readable head above the coil. **Siege job:** hold a breach and pin a defender's attention. **Rule:** actions 2 and 5 bite for ordinary damage and apply Weakened to one frontline hero. No swallowed or removed hero state. **Strength:** reduces opposing damage while remaining durable. **Weakness:** modest armour; cleanse, supported disruption, or focused damage. **Synergy:** wolves capitalize on the reduced counterpressure. **Forecast:** “A giant serpent has been driven toward the gate.” Does not require a water map.

#### tc_werewolf — Werewolf

**Recognition/art:** upright wolf-human hybrid with recognizably torn clothing and clawed hands. **Siege job:** rush defenders and sustain itself through a long struggle. **Rule:** heals 4 HP at the start of its action unless it took silver-tagged or fire damage since its previous action. Silver-tagged direct attacks also deal +25% damage. Normal damage still works and kills it normally. **Strength:** fast recovery fighter. **Weakness:** silver, fire, Mortal Wound, or concentrated ordinary damage. **Synergy:** Beastcaller. **Forecast:** “A werewolf is among the attackers. Silver disrupts its recovery.” Never permanently infects heroes through an unannounced roll.

#### tc_beastcaller — Beastcaller

**Recognition/art:** humanoid with a carved horn, animal-call cords, and Thorn Covenant bindings. Any humanoid race can fit the role; ancestry does not determine allegiance. **Siege job:** coordinate creatures around the weak point. **Rule:** while alive, allied beast-tagged or explicitly pack-eligible units gain +10% direct damage and their ordinary attacks prioritize the living hero last hit by the caller. Caller makes an ordinary ranged physical attack. No same-name buff stacking. **Strength:** gives packs a coherent target. **Weakness:** low HP; kill the caller to remove coordination. **Synergy:** wolves and serpent; does not buff elementals or treants. **Forecast:** “A horn-bearer is directing the beasts.”

## 11. Six captains

Captains are named encounter definitions. Their names are not drawn from the playable hero pool. Their identities persist within a run when a genuine escape rule preserves them, but no captain automatically escapes a victory just to force a story. A defeated captain is dead or routed according to the actual result. Reappearance requires an explicit authored survival state.

Captains cannot be raised or healed by another enemy recovery support. Each has one central special rule, plus ordinary attacks. Their higher cost accounts for leadership pressure provisionally. Captain victories can unlock existing material rewards or legacy progress through the current reward system, not automatic new currencies.

| ID | Captain | HP | Attack | Arm | Init | Cost | Rank | Earliest siege |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| cap_iron_marshal | Varka the Iron Marshal | 115 | 15 | 25 | 85 | 16 | F | 4 |
| cap_goblin_chief | Nibba Fuse-Ear | 82 | 11 | 10 | 110 | 13 | B | 3 |
| cap_lich | The Bellkeeper | 88 | 12 | 10 | 95 | 18 | B | 5 |
| cap_death_knight | Sir Carrow, the Unburied | 130 | 16 | 30 | 75 | 18 | F | 4 |
| cap_elder_treant | Old Root-Crown | 155 | 15 | 20 | 40 | 18 | F | 4 |
| cap_beast_matriarch | The White-Fang Matriarch | 112 | 15 | 10 | 115 | 16 | F | 4 |

### cap_iron_marshal — Varka the Iron Marshal

**Identity:** an orc military contractor who values trained formations, not a monarch of all orcs. **Silhouette:** tall rectangular banner fixed to the back, broad shield, dented officer's helm. **Rule:** when first reduced below 50% HP, announces `Last Advance`; on the Marshal's next action, living allied Banner units receive +20% direct damage for their next two actions. Interrupt cancels the rally permanently. It does not revive dead soldiers or grant immediate extra attacks. **Counterplay:** burst through the remaining HP, interrupt the rally, or remove accompanying troops before the threshold. **Forecast:** “Varka will rally surviving troops when wounded.” **Unique story hook:** a deserter may identify Varka's training method through a real personal opportunity, revealing the rally timing earlier.

### cap_goblin_chief — Nibba Fuse-Ear

**Identity:** a goblin engineer who sells breached gates to the highest bidder. **Silhouette:** oversized leather ear guards, scorched cap, a bundle of short fuses. **Rule:** begins `Demolition Order` on action 2, completing on action 3 to grant the accompanying Sapper an immediate **prepared** state. That Sapper's next scheduled action completes its existing charge; it gets no extra action and can still be interrupted or killed. If no living Sapper exists, Nibba instead grants 12 Guard to the lowest-HP ally. One order. Nibba then uses ordinary ranged physical attacks. **Counterplay:** kill or interrupt the sapper, interrupt Nibba, or remove the protected escort. **Forecast:** “Nibba's orders will speed the sapper's charge.” Do not pair with another structural engine.

### cap_lich — The Bellkeeper

**Identity:** a skeletal ritualist who carries the cracked bell of a flooded chapel. This is a local villain with original lore. **Silhouette:** tall skeletal caster, bell frame, hanging funeral tags. **Rule:** at the first allied standard undead death, begins `Funeral Toll` on its next action, completing one action later to raise that one unit at 40% HP. One raise per battle; no captain, spirit, engine, or already-raised target. Interrupt cancels the toll permanently. Other actions are ranged shadow attacks. **Counterplay:** interrupt, defeat the Bellkeeper, or defeat the first target again after a finite raise. **Forecast:** “The Bellkeeper will try to call one fallen soldier back.” **Boundary:** no phylactery side system or mandatory postbattle quest is needed for this version. Counts as recovery support; no Necromancer or Acolyte alongside it.

### cap_death_knight — Sir Carrow, the Unburied

**Identity:** an undead former gate commander, trapped in an oath to hold every wall he reaches. **Silhouette:** broad dark plate, broken tower emblem, a pale light behind the visor. **Rule:** actions 2 and 5 channel `Oath of Ruin`, beginning on that action and completing on the next for 80% attack as shadow damage to all heroes. Interrupt cancels the current use but not a later scheduled one; it cannot begin another while already channeling. Other actions ordinary physical attacks. **Counterplay:** disrupt a channel, sustain through the limited sweep, or use nonphysical/penetrating damage against his armour. **Forecast:** “The Unburied Knight is preparing an oath that strikes every defender.” No allied recovery support.

### cap_elder_treant — Old Root-Crown

**Identity:** an ancient treant bound by a Covenant oath, not an entire forest's universal enemy. **Silhouette:** crownlike broken branches, hanging rope bindings, broad hollow trunk. **Rule:** begins `Root Shelter` on action 2 and completes on action 3, granting 18 Guard to itself and all living allies. One shelter per battle. Fire weakness (+25%). **Counterplay:** interrupt, kill light support before the shelter, or burn through a limited defence. **Forecast:** “Root-Crown will shelter the creatures around it.” **Boundary:** unlike the standard Treant, it has no passive retaliation in this card. Avoid inheriting abilities automatically by creature ancestry.

### cap_beast_matriarch — The White-Fang Matriarch

**Identity:** an unusually large dire wolf following a Covenant handler's binding, protective of its pack. **Silhouette:** massive wolf, white facial stripe, broken iron collar. **Rule:** on actions 2 and 5 marks the living hero with lowest HP percentage as `Pack Prey` until the end of the next round. Her accompanying wolves prioritize that hero on ordinary attacks. The mark grants no free damage and does not stack with Beastcaller targeting; do not include both in her army. **Counterplay:** Guard or recover the marked hero, remove pack members, or defeat the Matriarch. **Forecast:** “The white-fanged wolf will direct the pack at a wounded defender.” No automatic summon of replacement wolves.

## 12. Three siege engines and three major monsters

These are additional definitions, bringing the complete bestiary to **48 entries**: 36 standard enemies, 6 captains, 3 engines, and 3 major monsters.

### 12.1 Engines

Engines are encounter entities with a visible crew represented in the same HP pool. Crew are not extra uncounted attackers. Engines do not receive biological healing, Poison, Bleed, resurrection, or beast buffs. They take +25% damage from fire. An engine's death cancels queued structural damage.

| ID | Engine | HP | Attack | Arm | Init | Cost | Rank | Earliest siege |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| eng_battering_ram | Covered Battering Ram | 90 | 0 | 20 | 35 | 10 | F | 3 |
| eng_ballista | Field Ballista | 48 | 16 | 5 | 45 | 8 | B | 3 |
| eng_corpse_catapult | Corpse Catapult | 65 | 0 | 10 | 30 | 11 | B | 4 |

**eng_battering_ram — Covered Battering Ram.** A timber roof, suspended iron-headed ram, and visible crew feet. Begins `Ram Stroke` on action 1, completes on action 2 for 4 structure damage. Repeats by beginning on actions 3 and 5, completing on 4 and 6. At most three strokes. Thereafter it remains a blocking entity with no attack. Interrupt cancels only the current stroke. **Counterplay:** fire or focused damage destroys the engine; interruption buys time. Shieldbearers can screen but cannot redirect structural damage. **Forecast:** “A covered ram is approaching. Its first stroke will come after a short preparation.”

**eng_ballista — Field Ballista.** A large wooden crossbow with an iron bolt and winding crew. Begins `Heavy Bolt` on action 1 and completes on action 2, targeting the highest-armour hero for 16 physical damage ignoring 20 percentage points of armour. Repeats start/complete on 3/4 and 5/6. Later actions make no attack. Interrupt wastes that bolt cycle. **Counterplay:** Guard still absorbs the bolt; low HP and fire weakness enable removal. No instant structure loss. **Forecast:** “A field ballista is being wound against the defenders.”

**eng_corpse_catapult — Corpse Catapult.** Crude throwing frame, shrouded load, living masked crew. Starts `Grave Volley` on action 2, completes on action 3: 3 structural damage and 6 shadow damage to each hero. One shot, then no attack. Interrupt cancels the shot permanently. No summoned zombies or infection persists after battle. **Counterplay:** fire, back-rank targeting, or interruption before the single shot. **Forecast:** “A corpse catapult is preparing one foul volley.” Keep presentation stylized; clarity does not depend on gore.

### 12.2 Major monsters

A major monster is a special late encounter, not an ordinary captain upgrade added to any army. It cannot accompany a captain or an engine. At most two low-cost standard escorts, total escort cost at most 6, and only when a listed template allows them. These encounters require distinct forecasts.

| ID | Monster | HP | Attack | Arm | Init | Cost | Rank | Earliest siege |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| boss_hill_giant | Hill Giant | 180 | 22 | 10 | 35 | 23 | F | 5 |
| boss_bone_colossus | Bone Colossus | 170 | 18 | 25 | 45 | 24 | F | 5 |
| boss_ash_dragon | Ash Dragon | 150 | 17 | 20 | 95 | 26 | F | 6 |

**boss_hill_giant — Hill Giant.** A towering humanoid with a boulder sling and gate-sized club, hired or coerced by the Iron Banner. Begins `Boulder Cast` on action 2, completes on 3 for 6 structure damage. On action 5 uses a club sweep for 70% attack damage to two frontline heroes. Other actions ordinary. One boulder. **Strength:** enormous HP and heavy pressure. **Weakness:** very slow, limited armour, no healing; interrupt the boulder, use sustained damage and protection. **Forecast:** “A hill giant is bringing stones large enough to break the gate.” **Art:** make the boulder and sling visible; scale cues must work inside a portrait.

**boss_bone_colossus — Bone Colossus.** An original mass of bound bones shaped into a giant humanoid, animated by the Procession. On actions 2 and 5 grants itself 15 Guard and then makes a normal attack; the action is one combined scripted ability, not two full actions. At first crossing below 50% HP loses its armour permanently as the shell cracks. Impact weakness (+25%), Poison and Bleed do not apply. No raising or spawned adds. **Strength:** immense durability before the crack. **Weakness:** impact, nonphysical damage, and concentrated damage to reach the vulnerable phase. **Forecast:** “A giant of bound bones is approaching. Break its outer shell.” **Art:** large skull cluster, ribcage frame, one obvious glowing binder seal.

**boss_ash_dragon — Ash Dragon.** An original four-legged, winged young dragon with dark ash-grey scales and a fire-scarred throat. It attacks for territory and tribute, not because every dragon is evil. Begins `Ash Breath` on action 2, completes on 3 for 70% attack as fire damage to all heroes plus Burn on one hero with the lowest HP percentage. One breath per battle. Fire resistance, cold weakness; other attacks ordinary physical. **Strength:** mixed physical/fire pressure and fast initiative. **Weakness:** forecast permits Guard/recovery and nonfire damage; interrupt cancels the breath. No flight immunity. **Forecast:** “An ash-scaled dragon demands the town's stores. Prepare for one great breath of fire.” **Boundary:** no tabletop age categories, lair actions, or official dragon lore are imported.

## 13. Army compositions and run variety

### 13.1 Selection rules

1. Select the approaching force from the existing faction system.
2. Select an eligible army template whose earliest siege has been reached.
3. Reject over-budget, capability-incompatible, or recently repeated templates.
4. Choose among remaining templates using deterministic weights; prefer a template not seen in the previous two same-faction sieges.
5. Forecast the actual defining threat. Finalize composition before the player's preparation window.
6. Use scouting only for details, not to secretly reroll the main threat.

The costs below are exact sums of the prototype definition costs. They are not proven equivalent difficulty. Suggested band labels: 8–12 introduction, 13–16 developing, 17–22 established, 23–28 captain/late, 29+ exceptional. Earliest-siege restrictions still apply. A 15-cost Wyvern encounter is not an introductory encounter.

Do not fill unused budget automatically with extra units if that breaks caps or the template's teaching purpose. If no template fits, choose a lower-budget valid template, not an arbitrary invalid combination. Any fallback should be recorded in diagnostics.

### 13.2 Twenty-four standard army templates


| ID / army | Members | Cost | First siege | Forecast and response |
| --- | --- | ---: | ---: | --- |
| army_ib_01 — Shield and Arrows | Hobgoblin Shieldbearer, Goblin Skirmisher, Hobgoblin Archer | 11 | 1 | **Shieldbearers escort archers toward the wall.** Remove the shield or use permitted back-rank pressure. |
| army_ib_02 — Stolen Colours | 2 × Goblin Skirmisher, Deserter Crossbowman, Hobgoblin Archer | 12 | 1 | **Climbers will cover deserter crossbows.** Guard exposed heroes and remove the fragile ranged troops. |
| army_ib_03 — Powder at the Gate | Hobgoblin Shieldbearer, Goblin Sapper, 2 × Goblin Skirmisher | 13 | 2 | **A protected sapper is preparing powder charges.** Interrupt or focus the sapper; the charge is finite. |
| army_ib_04 — Drums and Fangs | War Drummer, Goblin Wolf Rider, 2 × Goblin Skirmisher | 13 | 2 | **War drums will drive a wolf rider at the wounded.** Remove the drummer or protect the expected prey. |
| army_ib_05 — Broken Shields | Orc Gatebreaker, Deserter Crossbowman, Hobgoblin Shieldbearer | 15 | 2 | **Heavy mauls will open a path for crossbow bolts.** Cleanse exposure, use Guard, or remove ranged pressure. |
| army_ib_06 — The Side Wall | Bugbear Ambusher, Orc War Shaman, Hobgoblin Shieldbearer, Hobgoblin Archer | 19 | 3 | **A climber and a war shaman will test the back line.** Protect the opening target and pressure the shaman. |
| army_ib_07 — Ogre at the Door | Ogre Ram-Bearer, War Drummer, Goblin Skirmisher | 16 | 3 | **An ogre carries a ram under the war drums.** Interrupt the rush or destroy the drum while concentrating damage. |
| army_ib_08 — The Paid Troll | Troll Mercenary, Deserter Crossbowman, Hobgoblin Shieldbearer | 18 | 3 | **A regenerating troll escorts armoured mercenaries.** Suppress recovery or concentrate damage; use nonphysical pressure on armour. |
| army_pp_01 — Old Spears | 2 × Skeleton Spearman, Skeleton Archer, Zombie | 12 | 1 | **Skeleton spears and bows advance behind a shambling corpse.** Impact is useful; ordinary concentrated damage remains viable. |
| army_pp_02 — Slow Advance | 2 × Zombie, 2 × Skeleton Archer | 12 | 1 | **Slow corpses screen a line of bone archers.** Remove archers with legal priority attacks or efficiently clear the front. |
| army_pp_03 — Hungry Dead | Ghoul, Zombie, Grave Rat Swarm | 11 | 2 | **A ghoul and grave rats will pressure wounded defenders.** Cleanse reduced healing or remove the fragile ghoul early. |
| army_pp_04 — Black Lantern | Grave Acolyte, 2 × Skeleton Spearman, Skeleton Archer | 14 | 2 | **A lantern-bearer is sustaining the skeleton line.** Remove support or overwhelm its limited healing. |
| army_pp_05 — A Second March | Necromancer, 2 × Skeleton Spearman, Skeleton Archer | 16 | 3 | **A necromancer will try to raise one fallen skeleton.** Interrupt the raise or prepare to defeat one unit twice. |
| army_pp_06 — Mourning at the Gate | Banshee, 2 × Zombie, Skeleton Archer | 16 | 3 | **A banshee will weaken the party with a single wail.** Disrupt its preparation or recover through the finite cry. |
| army_pp_07 — The Empty Armour | Undead Black Knight, Wraith, Skeleton Archer | 18 | 3 | **A black knight holds the gate while a wraith seeks the rear.** Use nonphysical or penetrating damage and protect the rear. |
| army_pp_08 — The Great Corpse | Ogre Zombie, Wight, Grave Rat Swarm | 18 | 3 | **A giant corpse advances beside a life-draining wight.** Guard denies drain healing; concentration prevents a long attrition fight. |
| army_tc_01 — Driven Pack | 3 × Dire Wolf | 12 | 1 | **Three driven wolves are gathering at the breach.** Remove one early to reduce coordinated pressure. |
| army_tc_02 — Horn and Web | Dire Wolf, Giant Spider, Beastcaller | 13 | 2 | **A horn-bearer directs a wolf while a spider webs the wall.** Kill the caller or cleanse the slowed hero. |
| army_tc_03 — Horns Below, Wings Above | Minotaur, Harpy | 12 | 3 | **A minotaur will charge while a harpy exposes defenders.** Interrupt the charge, protect the target, or remove the harpy. |
| army_tc_04 — Roots and Teeth | Thornbound Treant, Dire Wolf | 13 | 3 | **A bound walking tree shelters a driven wolf.** Fire or heavy hits against the tree; ordinary focus against the wolf. |
| army_tc_05 — Spines and Coils | Manticore, Giant Serpent, Dire Wolf | 17 | 3 | **A giant serpent holds the breach beneath falling tail spikes.** Recovery, cleanse, and concentrated damage reduce overlapping pressure. |
| army_tc_06 — Venom from Above | Wyvern, Harpy | 15 | 4 | **A wyvern will dive while harpies strip away protection.** Cleanse poison, Guard the prey, and remove the fragile harpy. |
| army_tc_07 — Bound Stone, Bound Flame | Earth Elemental, Fire Elemental | 18 | 4 | **Bound stone and flame are advancing together.** Split preparation: impact or penetration for stone, nonfire damage for flame. |
| army_tc_08 — The Marked Hunt | Werewolf, Beastcaller, Dire Wolf | 17 | 3 | **A horn-bearer leads a werewolf and a dire wolf.** Remove coordination; silver, fire, or concentrated damage limits recovery. |


### 13.3 Six captain army templates


| ID / army | Members | Cost | First siege | Forecast and response |
| --- | --- | ---: | ---: | --- |
| army_cap_01 — The Last Advance | Varka the Iron Marshal, Hobgoblin Shieldbearer, Hobgoblin Archer | 25 | 4 | **Varka will rally surviving troops when wounded.** Remove escorts or interrupt the announced rally. |
| army_cap_02 — Fuse-Ear's Breach | Nibba Fuse-Ear, Goblin Sapper, Hobgoblin Shieldbearer, Goblin Skirmisher | 24 | 3 | **Nibba will hasten a protected sapper's charge.** Focus or interrupt either preparation before the sapper completes. |
| army_cap_03 — The Funeral Toll | The Bellkeeper, 2 × Skeleton Spearman, Skeleton Archer | 27 | 5 | **The Bellkeeper will call one fallen soldier back.** Interrupt the toll or defeat the finite returned unit. |
| army_cap_04 — The Unburied Oath | Sir Carrow, the Unburied, Grave Rat Swarm, Skeleton Archer | 24 | 4 | **The Unburied Knight prepares a party-wide oath.** Disrupt a channel and use damage that answers his armour. |
| army_cap_05 — Under Root-Crown | Old Root-Crown, Giant Spider, Dire Wolf | 26 | 4 | **Root-Crown will shelter the creatures around it.** Interrupt shelter or remove light escorts before it resolves. |
| army_cap_06 — White-Fang's Pack | The White-Fang Matriarch, 2 × Dire Wolf | 24 | 4 | **The white-fanged matriarch will mark a wounded defender.** Protect marked prey and reduce pack count. |


### 13.4 Three engine and three major-monster templates


| ID / army | Members | Cost | First siege | Forecast and response |
| --- | --- | ---: | ---: | --- |
| army_eng_01 — Under the Timber Roof | Covered Battering Ram, Hobgoblin Shieldbearer, Deserter Crossbowman | 19 | 3 | **A covered ram is approaching beneath shields and crossbows.** Burn or destroy the ram; interrupt buys another cycle. |
| army_eng_02 — The Heavy Bolt | Field Ballista, 2 × Goblin Skirmisher, Hobgoblin Archer | 16 | 3 | **A field ballista is winding a bolt against the defenders.** Guard the armour-priority target or remove the fragile engine. |
| army_eng_03 — A Foul Volley | Corpse Catapult, 2 × Zombie, Skeleton Archer | 20 | 4 | **A corpse catapult is preparing one foul volley.** Destroy or interrupt the engine before its single shot. |


| ID / army | Members | Cost | First siege | Forecast and response |
| --- | --- | ---: | ---: | --- |
| army_boss_01 — Stones for the Gate | Hill Giant, 2 × Goblin Skirmisher | 27 | 5 | **A hill giant is bringing a boulder toward the gate.** Interrupt its throw; use sustained damage and protection. |
| army_boss_02 — The Walking Ossuary | Bone Colossus, 2 × Skeleton Spearman | 30 | 5 | **A giant of bound bones is approaching with a brittle outer shell.** Impact or nonphysical damage breaks its protection. |
| army_boss_03 — Tribute in Ash | Ash Dragon | 26 | 6 | **An ash-scaled dragon demands tribute. Prepare for one great breath.** Interrupt or withstand the breath; use nonfire damage. |


### 13.5 Controlled variants

Variants reuse the same creature identity and core art with an optional badge or palette accent. They never replace a missing mechanic with a misleading label. At most one variant on one noncaptain enemy per encounter. Do not apply variants before siege 3.

| ID | Variant | Exact change | Budget surcharge | Restriction |
| --- | --- | --- | ---: | --- |
| var_veteran | Veteran | HP +15%, direct attack +10% | +2 | No support, engine, captain, or boss |
| var_armoured | Armoured | Physical armour +10 points, initiative -10 | +2 | Max final armour 40%; no spirits or fire elementals |
| var_swift | Swift | Initiative +20, HP -10% | +1 | No engine, giant, treant, or earth elemental |
| var_frostbound | Frostbound | First successful ordinary attack applies Slow; 30% cold resistance and +25% fire weakness | +2 | No existing control support, elemental, captain, or boss |
| var_frenzied | Frenzied | Direct damage +15%, armour -10 points floored at 0 | +1 | No support, engine, captain, or boss |
| var_scarred | Scarred | HP +20%, healing received -50% | +1 | No spirit, skeleton, elemental, engine, captain, or boss |

Round HP changes down, minimum 1. Variant modifiers cannot cancel a core counter: Armoured never removes impact weakness; Frostbound cannot be put on a Troll to complicate its teaching encounter; no variant gives immunity. Variant names appear as “Veteran Orc Gatebreaker,” not a wholly new unexplained monster name.

### 13.6 Siege pacing and recurrence

Index sieges by encounter number. Siege 1 normally corresponds to day 5; use the actual run calendar rather than a duplicated day formula. The first two sieges should explain one central interaction each. Later templates combine learned roles. Do not use more than one captain mechanic at once.

An army is authored from templates, not from a bag of every enemy in the game. Allow within-template flavour substitutions only where equivalence has been tested and the forecast remains accurate. Never replace a Zombie with a Wraith merely because both belong to the Procession.

A surviving captain may return once with a visible Veteran stat adjustment only if a recorded outcome allowed escape; that return consumes the captain slot and uses its own announced higher budget. Do not grant random immunity to the player's favourite weapon. Unique run identity should come from different casts, preparations, and credible threats.

## 14. Hero demand, personal agendas, and opportunities

This section defines creative content to connect the bestiary to the shop and autonomous heroes. It uses the goal/ownership rules in the companion design. A proposal is generated from a real eligible state, not simply from the hero's race.

### 14.1 Enemy knowledge creates reasonable demand

| Known approaching threat | Possible hero demand | Valid alternative |
| --- | --- | --- |
| Troll | Fire/acid-compatible weapon | More concentrated damage or healing reduction |
| Skeleton front line | Impact-tagged weapon | Ordinary high damage or nonphysical attacks |
| Armoured Banner soldiers | Penetration or elemental effect | Sustained damage with adequate protection |
| Ghoul | Recovery/cleanse-compatible equipment | Early removal or Guard |
| Necromancer | Interrupt or back-rank targeting | Defeat the raised unit twice |
| Banshee | Disruption and party recovery | Early focused damage |
| Spider | Cleanse or fire | Ignore small ordering loss and use heavy reliable hits |
| Werewolf | Silver/fire | Healing reduction or concentrated ordinary damage |
| Treant | Fire or heavy hits | Avoid many tiny direct hits |
| Fire Elemental | Cold/nonfire damage | Guard and recovery |
| Wyvern | Protection and cleanse | Kill it quickly during engagement |
| Sapper/engine | Interrupt or priority damage | Accept a bounded structural loss and win the fight |

These are preferences under known circumstances, not compulsory recipes. A hero should not demand an unsupported effect or an ingredient that cannot exist in the current run. If the desired option is absent, choose a practical alternative and explain it.

### 14.2 Eighteen authored opportunity templates

All proposals use stable IDs, an origin event, an initiating hero, and validated target references. The baseline cooldown is five days per initiating hero and opportunity type; outstanding identical goals block duplicates. Ordinary expiry is three planning days unless listed otherwise. Rewards below refer to existing reward categories and must be assigned through the current economy, never fabricated gold from nowhere.

| ID | Trigger and request | Player options | Actual outcome and boundary |
| --- | --- | --- | --- |
| opp_powder_trail | A scout survives a Banner encounter and a sapper is forecast; offers to find the powder route | Assign a supported scouting slot or decline | Successful scouting reveals the already-generated escort; no free deletion of the army |
| opp_troll_test | Hero has encountered a troll and lacks useful recovery suppression | Forge/list a suitable tool or decline | Actual later use records suppression evidence; no instant expertise |
| opp_silver_saving | Hero knows a Werewolf threat and has sufficient earning prospects | Accept a payable commission or leave it open | Hero saves and purchases if still suitable; money is real |
| opp_bone_lesson | Hero wins against skeletons with an impact effect and an eligible mentor exists | Permit normal mentorship or decline | Existing training progress; cannot create a new class |
| opp_lantern_hunt | Hero personally identifies a living Grave Acolyte in a prior real encounter | Assign a follow-up hunt if the target persists | Resolves against that target or expires; does not refer to an already-dead acolyte |
| opp_gate_practice | Militia/Mason background hero survives a structural attack | Support an existing training activity | Gains legitimate experience evidence, not permanent free wall HP |
| opp_returned_blade | A tracked lost weapon has a valid recovery lead | Assign recovery or decline | Real item moves through ownership states exactly once |
| opp_missing_caravan | A generated caravan event creates missing-person/stock records | Assign a rescue/recovery expedition | Current quest rewards and real survivors; no automatic new rare material |
| opp_shared_watch | Two heroes have actual trust history before a siege | Reserve both if legal or choose another defence | Relationship evidence from the ensuing siege, no hidden large combat multiplier |
| opp_apprentice_request | Eligible experienced hero and available novice share a training interest | Permit a supported lesson | Progress under existing training rules; novice still chooses expertise |
| opp_wounded_friend | A hero's important companion is injured and an assistance action exists | Support recovery or decline | Bounded resource transfer/recovery, never free resurrection |
| opp_captains_drill | Deserter/Veteran personally recognizes a forecast captain | Support reconnaissance or simply inspect the report | Reveal captain timing information already encoded in the encounter |
| opp_ash_samples | Scholar observes a Fire Elemental and a valid discovery clue can be earned | Assign a supported investigation | Grants an existing clue/knowledge reward, not a new material system |
| opp_unburied_oath | Hero has a real connection to an undead commander through authored backstory | Pursue an eligible personal objective | Finite reputation/goal outcome; commander stats unchanged without explicit quest rules |
| opp_forests_binding | Scout observes visible Covenant bindings and a supported investigation target exists | Investigate or decline | Reveals the Beastcaller role or an existing optional encounter |
| opp_reputation_deed | Exiled noble/Deserter completes two legitimate useful activities | Accept a suitably risky public contract | Existing reputation reward on completion; no reward for acceptance alone |
| opp_familiar_weapon | Sentimental hero reaches an actual shared weapon milestone | Support a commission to improve fit, if upgrade mechanics exist, or acknowledge | No inventing weapon upgrades if the engine lacks them; acknowledgement records story only |
| opp_safe_experiment | Curious hero has observed an unstable/flawed weapon effect and can afford a test commission | Accept a concrete compatible commission or decline | Actual weapon use supplies evidence; no lethal test forced on another hero |

An opportunity that requires a missing activity must remain disabled and listed as such in the coverage ledger. Do not ship a button whose promised outcome is silently replaced with generic gold. Personal initiatives should create decisions worth noticing, not a new mandatory daily checklist.

### 14.3 Twelve concise report templates

Render only when the matching event and reason are true:

1. “{hero} bought {weapon}. Its {effect} suits the approaching {enemy}.”
2. “{hero} returned your loan and bought {weapon}.”
3. “{hero} kept {weapon}. The new blade offered little improvement.”
4. “{hero} chose {expertise} after repeated work protecting companions.”
5. “{hero} is saving for a weapon suited to {threat}.”
6. “{hero} found a lead on {target} and proposed a hunt.”
7. “{hero} recognized the captain's drill. The next rally is predictable.”
8. “{hero}'s fire stopped the troll's recovery.”
9. “The necromancer raised one fallen soldier.”
10. “The sapper's charge was interrupted before it reached the gate.”
11. “{hero} survived the siege with {weapon}. Their attachment has grown.”
12. “The offer expired when {target} was no longer available.”

Do not claim a full causal story from a weak correlation. When several reasons mattered, present the principal supported reason or use restrained wording.

## 15. Art production briefs

### 15.1 Shared style

Detailed fantasy pixel art consistent with the existing game. Prioritize readable faces and silhouettes at actual portrait size. Use the established palette and pixel scale from current assets; verify those before generating a large batch. No UI words, HP bars, or names inside artwork. Keep significant props inside a safe frame so cropping does not remove the creature's identity.

This document supplies concepts, not final image dimensions. The implementer must identify the current portrait/atlas requirements before the owner makes assets. Assign stable asset IDs that match content IDs. No art needs to be generated to validate mechanics.

### 15.2 Races: first practical asset batch

Provide at least two distinct portrait identities per race before enabling that race by default, ideally four over time. That is 24 initial identities or 48 expanded identities, not 24/48 hero classes. Each identity should support a base and an earned upgraded version if the existing pipeline uses those pairs. This produces 48 initial images or 96 expanded images with upgrades; do not confuse identities with file count.

Within a race, vary age, face shape, skin/fur/scale tone, hair, expression, and scars. Across races, vary class clothing rather than visually forcing a role. Avoid making every elf young, every dwarf old, every goblin comedic, or every orc angry.

### 15.3 Enemy identity groups and reusable art

Every one of the 48 definitions has a proposed asset ID identical to its stable content ID. Engine crews appear within the engine illustration. Swarms use one illustration. Riders use a mounted illustration. Captains require distinctive silhouettes and cannot be represented only by a rarity border.

Potential controlled reuse: Skeleton Spearman/Archer can share a bone body base with changed weapon and pose; Zombie/Ogre Zombie require different proportions; Shieldbearer/Archer can share uniform palette but not identical silhouettes; standard Treant/Root-Crown require clearly different branch crowns; Wolf/Matriarch require different scale cues and face marks. Shared bases do not imply identical final portraits.

### 15.4 Priority art list

**Batch A, core variety:** Goblin Skirmisher; Hobgoblin Shieldbearer; Hobgoblin Archer; Deserter Crossbowman; Skeleton Spearman; Skeleton Archer; Zombie; Dire Wolf. Eight identities.

**Batch B, teach new decisions:** Goblin Sapper; War Drummer; Orc Gatebreaker; Ghoul; Grave Acolyte; Giant Spider; Harpy; Beastcaller. Eight more.

**Batch C, recognizable large threats:** Wolf Rider; War Shaman; Bugbear Ambusher; Ogre Ram-Bearer; Troll Mercenary; Wight; Necromancer; Ogre Zombie; Banshee; Wraith; Undead Black Knight; Grave Rat Swarm; Minotaur; Manticore; Wyvern; Treant; Earth Elemental; Fire Elemental; Giant Serpent; Werewolf. Twenty more.

**Batch D, headline encounters:** six captains, three engines, three major monsters. Twelve more.

Total: 8 + 8 + 20 + 12 = **48 enemy identities**. Variant badges/palette accents are optional extra files, not six new monsters each time.

### 15.5 Minimum replay cues

| Mechanic | Visible cue | Outcome label |
| --- | --- | --- |
| Guard | Small shield pulse and brief shield value | Guard |
| Redirect | Shieldbearer pulse linking to protected ally | Protected |
| Troll/Werewolf recovery | Brief recovery number only when HP changes | Recovered |
| Recovery suppressed | Crossed small recovery icon after relevant hit | Recovery stopped |
| Channel | Countdown/prepare marker on actor and target cue | Preparing / Interrupted |
| Raised unit | One short reappearance pulse and reduced HP | Raised |
| Exposed | Brief cracked-shield icon | Exposed |
| Poison/Burn | Distinct small damage ticks with text/icon | Poison / Burn |
| Captain threshold | Brief banner/bell/root visual tied to real event | Rally / Toll / Shelter |
| Structural hit | Wall icon pulse once per committed hit | Wall -{amount} |

Effects must use precomputed replay events. Skipping or reopening a replay cannot change a result. Do not rely on colour alone to distinguish poison, burn, and healing. Prefer few readable popups over simultaneous clutter.

## 16. Implementation data contracts

### 16.1 Race definition

Conceptual schema, adapted to existing code:

```text
raceId, displayName, recruitmentWeight, defaultCultureId,
appearanceTags, availablePortraitIds, enabled

cultureId, givenNames[100], familyNames[50], defaultPattern,
inheritanceRule, fallbackBynames
```

No implied ancestry combat trait exists in this version. If current approved races already have bonuses, preserve them until explicitly reconciled, rather than silently zeroing an existing save. Map a current equivalent race to this catalogue deliberately, including displayed names and history.

### 16.2 Enemy definition

```text
enemyId, displayName, forceId, roleTags, creatureTags,
baseHP, baseAttack, physicalArmourPercent, initiative,
threatCost, rank, earliestSiegeIndex,
attackType, defaultTargetRule, abilityIds,
resistanceTags, weaknessTags, excludedConditions,
assetId, forecastKey, rewardProfileId, enabled
```

Store concrete authored abilities as reusable validated definitions, with start/complete timing, uses, target selection, cancellation, and event output. Do not parse the prose at runtime. Missing fields must be explicit defaults; do not infer all undead share every listed condition exclusion. For example, the Ghoul card does not inherit Skeleton's impact weakness.

Every ordinary enemy directly attacks physically unless its card states shadow/fire/ranged behaviour. “Ranged” is a targeting capability, not a damage element. Banshee, Wraith, Necromancer, and Acolyte use shadow direct attacks as specified. War Shaman uses its spirit attack mapped to the existing magic damage type; if no spirit type exists, use the established nonphysical magic type rather than adding a damage family only for one card.

### 16.3 Combat tags for counterplay

Reuse existing tags where semantics match. Required concepts: physical versus nonphysical damage, fire, cold, poison, impact, silver, anti-undead, direct versus periodic, spell-tagged action, beast, undead, spirit, construct/engine, and channel. Acid is optional because ordinary concentrated damage remains a valid troll response; enable acid-based suppression only if a real source exists.

Silver refers to actual material/effect provenance. Do not give any shiny weapon a silver tag based on its art. Impact may map to a heavy/impacting technique on current families. No new hammer family is required. A bow does not become a blunt weapon because its numeric power is high.

### 16.4 Army and opportunity definitions

```text
armyId, forceId, displayName, members[{enemyId,count}],
computedCost, earliestSiegeIndex, mainThreatTag,
forecastText, capabilityRequirements, repetitionKey

opportunityId, originPredicate, initiatorPredicate, targetPredicate,
request, acceptedActionType, resourceCostProfile,
expiryDays, cooldownDays, outcomeRule, reportKeys
```

Resolve targets at instantiation, preserve them, and revalidate on execution. Generate complete final armies before forecasting. If a save already contains an approaching army, content expansion must not reroll it.

### 16.5 Translation and balance discipline

The implementer may scale prototype HP/damage to the current engine using one documented conversion and then calibrate. Do not mix prototype-scale damage with live-scale HP. Preserve relative roles and bounded ability timing. Cost is a starting estimate, not an excuse to skip encounter simulations.

If current battles have a single aggregate enemy power and no entity-specific targeting, support protection, or channels, these mechanics require an engine extension. Report that clearly. The complete asset catalogue can be loaded as disabled data first; it is not acceptable to advertise full mechanical implementation while only displaying different pictures.

## 17. Validation and release batches

### 17.1 Content validation

- Exactly 12 race definitions and 12 name cultures, 100 given names and 50 surnames per pool.
- No duplicate entry within one pool under case-insensitive comparison. Cross-culture reuse is permitted.
- All example hero names use their indicated pool.
- All enemy/army IDs are unique and all references resolve.
- Exactly 36 standard enemies, 6 captains, 3 engines, and 3 major monsters.
- Exactly 24 standard army templates, 6 captain templates, 3 engine templates, and 3 major-monster templates.
- Template cost equals the sum of its members, including any variant surcharge.
- Template earliest siege is at least the latest member's minimum.
- No template violates entity count, support, captain, engine, or major-monster restrictions.
- Every new damaging/controlling ability has a visible cue, a limit, and practical counterplay.
- Every enabled race has compatible portrait coverage; missing art uses an explicit placeholder policy in development, not a mismatched final identity.

### 17.2 Mechanical scenarios

Test shield redirection without recursive protection; a raised Zombie cannot regain its used survival; a Necromancer cannot raise a captain or an already-raised entity; an interrupted Sapper never deals queued structure damage; fire suppresses Troll recovery; poison does not affect Skeletons; a Wyvern remains attackable; a silenced hero makes a legal fallback attack; Guard prevents life-drain healing from absorbed damage; sustained auras end on source death; a killed engine's channel is cancelled; and an area effect cannot recursively trigger itself through retaliation.

Test fixture tuning against multiple viable preparations, including ordinary non-specialist damage. Special counter gear should improve results without being the only route through a normal early encounter. Verify starting rosters can reasonably respond using available inventory and the actual preparation window.

### 17.3 Suggested implementation sequence

1. Import names/races and validate data, preserving old saves.
2. Implement or map basic stat/condition/targeting semantics.
3. Release eight Batch A enemy identities mechanically, then Batch B's small tactical library.
4. Validate all standard templates that use enabled content.
5. Add specialized large creatures in coherent small groups.
6. Add captains, engines, and major monsters as separate milestones with their own tests.
7. Activate matching personal opportunities after their dependencies work.
8. Complete final art and device/replay readability checks.

The content catalogue is complete as a design specification. Implementation remains staged because abilities have real dependencies, not because creative choices have been left open.

### 17.4 Required implementation coverage ledger

For every race, enemy, army, and opportunity, report: authored ID, current code/data path, status (`implemented`, `mapped to equivalent`, `disabled pending dependency`, or `deferred with reason`), asset status, tests, and any deliberate deviation. Do not silently drop entries. Obtain owner direction for a material creative change rather than replacing it with unrelated content.

## 18. Research and source notes

Research checked on 17 October 2026. Source links are references for recognizable fantasy identities, not permission to copy every item on those sites. All numerical profiles, schedules, attack roles, factions, names, and encounter templates in this file are original game design.

### S1. Official D&D Beyond character origins

[Character Origins — D&D Beyond Basic Rules](https://www.dndbeyond.com/sources/dnd/br-2024/character-origins)

Used to check familiar ancestry vocabulary and the separation of background, species, and class. This bible retains recognizable humans, elves, dwarves, gnomes, halflings, and orcs, while using original local identities for Dragonkin, Hornborn, Stonekin, Catfolk, and Lizardfolk. It does not import official species features, ability-score rules, cosmology, or name lists.

### S2. Official D&D Beyond monster reference

[Creature Stat Blocks — D&D Beyond Basic Rules](https://www.dndbeyond.com/sources/dnd/br-2024/creature-stat-blocks)

Limited checked touchstones: trolls regenerate and fire/acid interfere; skeletons are fragile against bludgeoning and unaffected by poison; hobgoblins have a coordinated martial identity; harpies have a disruptive song; gargoyles are flying stone creatures; wyverns use a venomous sting. This game's abilities deliberately differ: its harpy exposes defenders instead of prolonged charm, its trolls die normally at zero HP, and its numerical resistances and timing are bespoke. Gargoyles were researched but omitted from this initial catalogue to avoid another armoured flyer overlapping the selected roles.

Other familiar fantasy creatures here are adapted through original siege concepts, not copied stat blocks. The Bone Colossus, faction-specific professions, captains, and Ash Dragon are original local definitions. A familiar creature name does not imply a verbatim match to a particular D&D edition.

### S3. Official SRD landing page

[System Reference Document — D&D Beyond](https://www.dndbeyond.com/srd)

Checked to distinguish published reference rules from the wider branded setting. This bible does not import official stat blocks, named settings, named deities, or branded unique monsters. If implementation later incorporates SRD expression directly, track the actual material and include its required attribution from the applicable source; do not assume this design document's reference links substitute for that step.

### Closing implementation instruction

Implement the authored identities and interactions faithfully. Keep heroes autonomous, equipment decisions coherent, race-appropriate names stable, enemy roles recognizable, and siege preparation consequential. Preserve the existing game where the catalogue does not explicitly ask for change. Where an existing system cannot express an authored mechanic, show the gap and the smallest faithful solution instead of redesigning the content silently.
