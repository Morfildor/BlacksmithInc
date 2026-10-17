# Writing guide and glossary (player-facing English)

The game's text is read by adults, many of whom read English as a second language. Every description, report and
choice must be understood on the first read. The guild keeps its personality: a friendly, experienced guildmaster
with occasional dry humour. This file is the reference for every string a player can see.

**The glossary in section 2 is the authoritative vocabulary** (owner approval of the terminology overhaul V2,
2026-10-17, `docs/TERMINOLOGY_PROPOSAL.md` with the owner's amendments). New text uses these words. A new term is
added here before it ships. IDs, enum constants and save keys keep their old names: only display text follows the
glossary (a guild quest is still `Mission` in code, a property an `Affix`, a challenge tier a `rank`).

Naming order for a new term: the D&D word where the mechanic really matches, then the word Diablo, Torchlight or
Baldur's Gate players know, then a plain RPG word, and an original name only for a mechanic unique to this game.
Never give two mechanics the same word, and never replace a word players already understand just to make the text
uniform.

## 1. Style

- Natural, complete sentences in familiar words. One main idea per sentence, without making the prose choppy.
- Name the actor and the target whenever a pronoun could point at two people ("the wielder", "that enemy",
  "the Guardian"). "It" for a weapon only when no fighter is in the same sentence.
- Explain mechanics literally: "deals 5 damage", "puts 1 Burn on", "uses up the Mark", "triggers", "gains 2 Armor".
- Numbers as numerals, shares as percentages: "+10% Attack", not "a tenth harder". "Half" is fine when it is
  exactly 50% and reads better ("half of the blocked damage goes through").
- Conditions, timing, duration, costs and limits are explicit: "Once per round.", "Once per fight.", "until day 12",
  "at the end of each round", "before the wielder acts".
- **Deterministic numbers are stated**, with their caps and conditions ("+20 quality, never above the metal's quality
  cap"). **Random chances are words**, never numbers (owner decision A): "more exceptional results", "never certain".
- No em or en dashes, no semicolons between clauses (`ProseGuardTest` enforces both), no stacked " · " separators in
  sentences (a separator is fine between two short facts on a stat line), no cryptic fragments.
- "Weapon", never a generic "blade" (`ProseGuardTest`). "Blade" only for a weapon that has one, in flavour text.
- No abbreviations on screen (no HP, ATK, DoT, pts). "Health %" between fights is the only short form.
- No adjective-as-noun shorthand ("the hurt rest"), no colon fragments in rules, no labels built from enum names
  ("reckless risk"): every enum shown to a player goes through a label map.
- Remove repetition and filler, never information a decision needs. Keep text that already works.
- Personality lives in dialogue, visitors, flavour lines and names. Instructions, warnings and numbers stay direct.

### Rules and flavour

An ability or item effect reads as: **When [trigger], [actor] does [effect] to [target]. [Limit or duration].**
Leave out a field that does not apply. Take numbers from the data. The trigger clause has no comma before its end,
and the limit is the last sentence: the inspect cards parse this shape (`ui/InspectUi.kt`).

> When an enemy breaks the wielder's Armor, the wielder deals 5 damage to that enemy. Once per round.

A metal or element effect is named after its source, and the source is never shown alone: "Iron · Starts each fight
with 3 Armor", "Frost · Chills on hit". Flavour stays separate (an item's `flavor`, a visitor's first line) and never
carries a rule ("Quests only: the wielder starts each fight with 1 Chill." then "It balks away from home.").

| Pattern | Before | After |
|---|---|---|
| Sentence-names and fragments | "95% strike. Reaches past Guard." | "Attack 95%. Armor penetration: when Armor blocks the wielder's attack, half of the blocked damage goes through. Once per round." |
| Pronouns that could point at two fighters | "the Guardian strikes that enemy back for half its strike" | "the Guardian deals 50% of its Attack to that enemy" |
| Status text that hides the rule | "Its next strike is 35% weaker and uses up one stack." | "The fighter's next attack deals 35% less damage, then 1 Chill is used up." |
| Machine-like report lines | "Ember left 4 of the 9 stacks put on foes, in 4 activations." | "Put 4 of the party's 9 Burn on enemies. Its Fire effect triggered 4 times." |
| Theatrical instructions | "The party waits at the deeper door. Without word, they return home." | "The party is waiting at the checkpoint. If you do not choose, it returns with the rewards already won." |
| Vague amounts | "the gain is small: a little wall repair" | "It always succeeds: up to 5 forge health and 1 militia." |
| Hidden numbers | "Improves the result and is used up when you forge." | "+4 quality, more exceptional results and fewer flaws. Used up when you forge." |

### Quests, choices and reports

- A quest or a visitor choice: the situation in one or two sentences, then objective, reward, cost, timing and risk as
  separate rows. Departure and return days are explicit. An irreversible result or a missed siege is shown before
  the player confirms.
- Action labels say what the player chooses: "Sell the sword for 120 gold", "Send the party", not "OK".
- Daily and combat reports lead with the outcome, then losses, gains and what the player must do. Secondary events
  and the full combat log stay behind the existing disclosures. A fact appears once: not in the heading, the summary
  and the details.

## 2. Glossary

Numbers are the current `FightRules` and `BalanceConfig` defaults. "Not" lists words retired from player text for
that meaning.

### 2.1 Fighting

| Term | Meaning | Not |
|---|---|---|
| **Health** | How much damage a fighter can take. At 0 the fighter is **down** and takes no more turns. | hit points, HP |
| **Health %** | A hero's health between fights, 0 to 100, shown as a percentage so it is not mistaken for fight health. | "Health 80" |
| **Attack** (stat) | How much damage the fighter's own attack deals: class, level, the weapon's share, its weapon type (Axe 120%, Dagger 80%), its metal and, for a guild member, a Brave (×1.05) or Cautious (×0.97) personality. Enemies roll theirs in a range. | Strike, strike damage |
| **attack** (action) | The fighter's own ability hitting an enemy. "Attack" with a capital is the stat, "an attack" the action. Every card that shows the stat first says so once: "Attack: the damage of this fighter's attacks." | strike (verb or noun), blow, cut |
| **Support** | Guardian: Armor gained each round. Warden: healing each round. Shown only where an ability reads it. | |
| **hit** | Any direct damage: an attack or an effect's damage. Burn and Bleed damage are not hits. | blow |
| **On hit** | When the wielder's attack gets at least 1 damage past Armor. | |
| **Damage** | Lowers Health after Armor. Order: weakness or resistance, Wet, Mark, then Armor, then Health. | |
| **Heal** | Restores Health, never above the maximum. Healing past full is an **Overheal**. | mend, recover (Mend stays the Warden's ability name) |
| **Ability** | A fighter's action each round (Hold the line, Mend, Heavy swing). | move, Moves |
| **Passive** | An ability that answers events on its own (Riposte). | |
| **Effect** | What a weapon, trait, relic or law does in a fight. | rule, rules |
| **Ignores Armor** | This damage goes straight to Health. | past Guard, through Guard, ignores Guard |
| **Round** | Every party member acts in the order you chose, then every enemy. At most 8 rounds (12 in a Long Assault siege). | |
| **Defeat** | Bring an enemy down. | fell, downs, beat |
| **Retreat** | The party leaves the fight alive and wins no reward. Also after 8 rounds without a winner on a quest. | pulled back, breaks off |
| **Front / back enemy** | The first and the last enemy still standing. | |
| **Attack target** | The party attacks a marked enemy first, else the front enemy. Enemies attack a Guardian who has Armor, else the front member. | |
| **Priority target** | An enemy summoner first, then the enemy leader, then the enemy with the most Health. | "the foe that matters" |
| **Weakest ally** | The ally with the lowest health percentage. | |
| **Wielder** | The hero holding the weapon. | |
| **Element** | Fire, Frost, Storm, Grave, Verdant or Sun. A weapon's attacks deal its element's damage. Proper names of this world, not D&D damage types. | |
| **Weak to / Resists** | A hit of that element deals 30% more / 30% less. Enemies only. | |
| **Elite, Leader, Summoner** | Enemy labels on the enemy card. | champion, named fighter |
| **Enemy, enemies** | Whoever the party or the town fights. Siege enemies are **the attackers**. | foe, besiegers |
| **Once per round / per fight** | The most often an effect can trigger for its holder. A party effect "for each member" counts per member. | twice a fight |
| **Siege only / Quests only** | Where an effect applies. | "Only on the town's wall", "(at home)" |

### 2.2 Statuses (every status ends with the fight)

| Status | Rule | Not |
|---|---|---|
| **Armor** | Absorbs damage before Health, point for point, until it is used up. It does not reduce each hit. At most 14. Blocks Burn. Bleed and damage that ignores Armor pass. **Armor breaks** when a hit empties it. Every definition of it says "until it is used up", because players expect armor to reduce every hit. | Guard (renamed by the owner, 2026-10-17), Barrier, Block |
| **Chill** | The fighter's next attack deals 35% less damage, then 1 Chill is used up. Only the first damage of that ability is weakened. At most 3. | slowed, weaker |
| **Burn** | At the end of each round the fighter takes 1 damage per Burn (Armor blocks it), then loses 1 Burn. At most 6. | |
| **Bleed** | Before the fighter acts, it takes 1 damage per Bleed (ignores Armor), then loses 1 Bleed. At most 5. | |
| **Regeneration** | At the end of each round the fighter heals 3, then loses 1 Regeneration. More Regeneration lasts longer, it does not heal more. At most 4. | Regen |
| **Mark** | The party attacks a marked enemy first. The next hit on it deals 3 extra damage and removes the Mark. At most 1. | |
| **Thorns** | The next enemy attack that hits this fighter, even into Armor, takes all the Thorns as damage, and the Thorns are used up. Effects, Burn and Bleed never set it off. At most 8. | |
| **Charge** | Stored energy. A **Discharge** spends all of it at 3 or more for 8 storm damage. At most 5. | Charge burst |
| **Wet** | A storm hit on a Wet fighter deals 2 extra damage and uses up 1 Wet. Loses 1 Wet each round. At most 2. | |
| **Steam** | When a fighter puts Burn on an enemy with Chill or Wet, or Chill on an enemy with Burn, they cancel one for one and the giver gains that much Steam. At most 4. | |
| **Scrap** | Left (2) when a weapon cracks in a fight. Some relics spend it. At most 4. | |
| **Cracked** (weapon) | Cracks at most once per fight. It keeps working, but loses 25 extra condition afterwards. | |

### 2.3 Forging and weapons

| Term | Meaning | Not |
|---|---|---|
| **Materials** | Metals, infusions and catalysts. One metal and one infusion (and an optional catalyst) per forge. | ingredient |
| **Metal** | Sets quality, power, and the highest quality and rarity a weapon can reach (**quality cap**: Iron 55, Bronze 65, Silver 75, Obsidian 84, Starsteel and Moonsteel 100). In a guild era it also gives an effect named after it. | core, basic metal, fine metal |
| **Infusion** | Gives the weapon its element, and in a guild era that element's effect. | Augment |
| **Catalyst** | Optional, Advanced forge only. +4 quality, more exceptional results, fewer flaws. Used up. | |
| **Technique** | Temper (fewer flaws and fewer exceptional results), Quench (always the element's property, −3 quality) or Etch (+1 property, more flaws). | |
| **Risk** | **Careful / Standard / Daring**: how wide the quality roll is and how often a result is exceptional or flawed. Daring adds 1 property. | Safe, Balanced, Reckless (for the forge) |
| **Exceptional result** | +20 quality (never above the metal's quality cap) and 1 more property. | exceptional work |
| **Flawed result** | −12 quality and 1 flaw. | |
| **Quality** | 1 to 100, with a word: crude, decent, fine, superb, masterwork. | "Epic quality" |
| **Rarity** | Common, Uncommon, Rare, Epic, Legendary. Mostly from quality, capped by the metal. Legendary only by Heartfire or 3 deeds. | |
| **Power** | The weapon's overall strength. Sets its price and its share of a hero's Attack. | |
| **Condition** | Wear from 0 to 100. Fights lower it. At 0 the weapon still works, at 75% power. | durability |
| **Properties: Bonuses and Flaws** | Named traits a weapon rolls at the forge, good (Flaming, Swift) or bad (Brittle, Heavy). How many it can roll is set by its rarity at the forge. | Buffs, affix |
| **Drawback** | What a material always costs in a fight. | trade-off |
| **Fame** | How well known a weapon is (Unsung to Renowned). Raises its power and price a little. At 3 or more it can enter the Hall of Legends. | Renown (row) |
| **Deeds** | Great acts done with a weapon. 3 deeds make it Legendary. | renown |
| **Upgrade / Repair** | The first time: +6 quality (at most 100) and full condition. Every later time: full condition. The only "upgrade" in the game. | |
| **Salvage / Melt down** | Salvage one weapon for 1 metal (1 energy). Melt down many at once for 1 metal per 4 (no energy). | bulk Scrap |
| **Role** | What a recipe does for a party: Tank, Party support, Protects the weakest, Damage over time, Burst damage, Back-line damage, Weakens enemies, Marks targets, Self-healing, Steam combos, Breaks for the party, Plain damage. | Holds the line, a plain striker |
| **Combination** | A metal and infusion (or infusion and weapon type) forged together. Learning one shows how well they suit each other. | pairing, field notes |
| **Signature weapon** | A unique named weapon with a hidden recipe. Clues reveal it step by step. | signature work |
| **Heartfire** | A rare resource for Legendary forging. | |

### 2.4 Shop, town and legacy

| Term | Meaning | Not |
|---|---|---|
| **Energy** | 10 a day for forging and other work. You can borrow up to 4 from tomorrow, and start that day with less. | |
| **End Day** | Closes the shop for the day. Customers buy, heroes fight, the night's events happen, the next morning begins. | |
| **Commission** | A paid order for a weapon of a given type and minimum quality by a deadline. Never used for guild work. | order, request |
| **Customer wants** | What customers who bought nothing are looking for. Not a promise to buy. | |
| **Personality** | A townsperson's temperament (Brave, Greedy, Loyal). Brave and Cautious change a guild member's Attack a little. | Traits (customer) |
| **Visitor** | A morning caller with a choice. | encounter |
| **Blessing** | A timed boon from the town after a held siege. The game's only buff. | |
| **Workshop relic** | A lasting bonus kept at the forge. | |
| **Siege** | A major attack on the town on a known day. | |
| **Defenders** | The 3 heroes on the wall: the strongest available heroes. In a guild era you can reserve members for it. | champions |
| **Defense / Raid** | The town's total strength against the attackers'. In a classic era the town holds if defense is at least the raid. There is no roll. | odds |
| **Militia / Armory** | The town's soldiers / the weapons the forge donated to them. Action: **Donate to the armory**. | the watch, Arm the watch |
| **Siege modifier** | A condition the scouts report for this siege (Long Assault). | siege trait |
| **Pressure** | How strong a faction is growing: quiet, restless, rising threat, severe threat, overwhelming threat. The strongest faction leads the next siege. | grave threat |
| **Forge health** | The forge's integrity. A lost siege or a narrow hold lowers it. At 0 the era ends. | integrity |
| **Era** | One playthrough, classic or guild. Menu actions: **Continue**, **New game**, **End era**. | run, chapter |
| **Legacy points, Legacy perks** | Points earned each era, spent on lasting perks. | permanent upgrades, pts |
| **Hall of Legends** | Up to 3 famous weapons an era are remembered there and may return. | Legend Board |
| **Lineage** | A famous hero's descendant arrives next era. | |
| **Gazette** | The daily newspaper, built from the real event record. | |

### 2.5 Guild

| Term | Meaning | Not |
|---|---|---|
| **Guild era, charter** | An era started with a charter. The charter warlord leads the first siege on or after day 20. | guild run |
| **Member, Roster** | A hero who joined your guild (at most 6). | Company (tab) |
| **For hire, Recruit, Recruitment fee** | Heroes willing to join, the action, its fee. A member keeps a share (usually 10%) of the gold of every quest they win. | sign, signing fee |
| **Loan** | Lend a weapon from storage to a member until the loan ends. | |
| **Reserve for the wall** | Keep a member at home to defend. Cleared after each siege. | keep, kept |
| **Quest, Quest board** | Work for a party, with a fee, a length in days, one or more stages and a reward. | contract, job |
| **Party** | 1 to 3 members sent on a quest, acting in the order you picked them. | |
| **Stage, Checkpoint** | One fight of a quest. At a checkpoint you choose **Go deeper** or **Return**. If you choose nothing, the party returns. Either way it is home on the same morning. | the deeper door |
| **Danger** | Safe, Dangerous, Lethal. | |
| **Retreat rule** | Cautious, Balanced, Reckless (section 3). The wall always fights under Balanced. | posture |
| **Injured, Exhausted, Scarred, Captured** | Member conditions with their days. | held, taken |
| **Defeated** | A quest stage lost. | lost, beaten |
| **Trait** | A member's personal fight effect. | |
| **Speciality** | A permanent improvement a member chooses once they have won 3 quests, been downed twice, or fulfilled their own ambition. | branch |
| **Level** | 1 to 20. Each level adds Health, Attack and Support. | |
| **Starting kit** | The first morning's choice of relic, traits and gear. | opening, package |
| **Challenge tier** | After the charter is secured: an optional, permanent step up in difficulty (stronger enemies, more siege enemies, shorter quest offers) worth more legacy points. The tiers keep their names (Chartered Company, Wardens of the March, The Last Company). | Rank |
| **Preparation** | A siege plan the attackers make. A sabotage quest can spoil it. | |
| **Law of the season** | An effect that holds for a few days in every quest fight, not at the siege. | |
| **Rival, nemesis** | A rival company that takes quests off the board. A nemesis is an enemy who carries one of your weapons. | |

### 2.6 Names

Kept because they are familiar: Health, Burn, Bleed, Chill, Regeneration, Thorns, Mark, Charge, Wet,
Overheal, Quest, Party, Retreat, Stage, Checkpoint, Level, Rarity, Quality, Power, Condition, Salvage, Upgrade,
Repair, Commission, Blessing, Relic, Siege, Militia, Elite, Leader, Riposte, Second Wind, Mend, Second arrow.

Unique to this game, explained where they appear: Heartfire, Steam, Scrap, Discharge, Deeds, Hall of Legends,
Lineage, Deep road, Signature weapon, and every proper name (charters, starting kits, relics, signature weapons,
factions, named enemies, routes, challenge tiers, traits).

Effect names renamed in the overhaul (old name: new name, mechanic):

| Old | New | Mechanic |
|---|---|---|
| Guard (status) | Armor | A pool that absorbs damage before Health until it is used up. "Guard breaks" becomes "Armor breaks". The Guardian class keeps its name |
| Iron holds, Bronze rings back, Silver holds a charge, Obsidian cracks, Starsteel finds the Mark, Moonsteel bites the cold | Iron, Bronze, Silver, Obsidian, Starsteel, Moonsteel | The metal's effect, always shown with what it does |
| Ember, Rime, Arc, Grave chill, Sap, Noon light | Fire, Frost, Storm, Grave, Verdant, Sun | The element's on-hit effect |
| Charge burst | Discharge | At 3+ Charge: spend all for 8 storm damage |
| Reach (Spear) | Armor penetration | When Armor blocks the wielder's attack, half of the blocked damage goes through |
| Off-hand cut (Dagger) | Follow-up attack | After acting: 45% of Attack to the attack target. The wielder has one weapon |
| Capacitor | Conduit | When the wielder is healed: 1 Charge |
| Rune of answer | Rune of Retaliation | When an enemy breaks an ally's Armor after a weapon cracked: 5 damage to it |
| Cleave | Heavy swing | 100% Attack to one target |
| Cut and run | Backstab | Attacks the back party member. Never leaves |
| Rout | Demoralized | When their leader goes down: each gains 2 Chill |
| Raise the fallen | Summon shambler | Summons a new shambler |
| Grave weapon | Undying blow | Attack, then 1 Regeneration to itself |
| Rend | Scale armor | 4 Armor to itself, then attack |
| Shield wall (Bone warden ability) | Raise shield | 3 Armor to itself, then attack |
| Greedy (guild trait) | Gold-Hungry | +20% stage gold, keeps an extra 5% of the quest gold |
| Loyal (guild trait) | Avenger | When an enemy downs an ally: full Attack to that enemy, once per fight |

Not adopted, because no mechanic matches: Block, Dodge, Miss, Critical hit, Poisoned, Stunned, Frozen, Slowed,
Lifesteal, Taunt, Vulnerable, Companion, Loot, Expedition (for quests), Encounter (for visitors), Boss, Enchantment.

## 3. Retreat rules

| Rule | The party retreats when |
|---|---|
| Cautious | any member is down, or the party's total health is below 40% of the health it started with |
| Balanced | 2 members are down, or the last member standing is below 30% health |
| Reckless | never |

The rule is checked at the end of each round. A retreat wins no reward. A party with a Cowardly member keeps 34% of the
stage's gold, and the Coward's Medal keeps 50%. Members downed in a fight are carried home injured. The wall always
fights under Balanced.

## 4. Old saves

Saves store finished text: fight logs, highlights, weapon names and titles, histories, the Hall of Legends, lineage
deeds. Those are history and are never rewritten (`SaveCodec`). `core/text/LegacyProse.kt` shows old *templated*
wording in the new vocabulary at display time only, never written back: whole known templates and effect or status
names in fight records. It never touches weapon names, titles, proper names or free prose.

## 5. History

- 2026-10-14 clarity pass: every description checked against its code. Decisions in `docs/GDD_REVISION_2026-10-14.md`.
- 2026-10-17 terminology overhaul V2: proposal, evidence and amendments in `docs/TERMINOLOGY_PROPOSAL.md` and
  `docs/terminology_audit/`. Implementation record in `docs/PROGRESS.md`.
