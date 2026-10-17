# RPG terminology and writing overhaul V2: Phase 1 proposal

**Status: approved with amendments, 2026-10-17. The authoritative glossary is now `docs/WRITING_GUIDE.md` section 2.**

Owner amendments (everything else approved as recommended):
- T1 (changed by the owner after approval, 2026-10-17): the status **Guard is renamed Armor**. Its definition always
  says it absorbs damage until it is used up. "Guard penetration" becomes **Armor penetration**, Rend becomes
  **Scale armor**. The Guardian class keeps its name.
- T3: Dagger "Off-hand attack" becomes **Follow-up attack** (the wielder has one weapon). Spear "Penetration" becomes
  **Guard penetration**. A metal or element source label is always shown beside what the effect does.
- T7: guild Greedy becomes **Gold-Hungry**, not Mercenary (every member is hired). Avenger approved.
- T8: **Hall of Legends**, not Hall of Fame. Era stays the playthrough word, with **Continue**, **New game** and
  **End era** as the actions, never "Continue era".
- T9: the system is the **Challenge tier**, not Rank. The tier names stay. Show the difficulty modifiers and the
  extra legacy points.
- T10: display-only compatibility extends to other unambiguous templated legacy wording, never broad word
  replacement over proper names, weapon titles or historical prose.
- T11: deterministic numbers with their caps and conditions (+20 quality never above the metal's quality cap).
  Chances stay words.
- Also: the 22 text-versus-code errors are mandatory; Attack the stat and attack the action are told apart where
  first introduced; understandable words are not replaced for uniformity; small screens and large fonts are checked;
  every test is run and any that cannot run is reported.

The original proposal follows unchanged.
Baseline: `main` at `bd6939b` plus the uncommitted V3 UI work (inspect cards). Evidence for every claim below, with
`file:line`, is in `docs/terminology_audit/`: `A_combat.md`, `B_crafting_town.md`, `C_guild.md`, `D_saves_tests.md`.
Each mechanic was read from the code, not from its name.

How to approve: answer the eleven decisions in section 2 (T1 to T11). "Approve as recommended" covers them all. The
replacement table (section 4) follows from those answers. Rows marked **(T#)** change if that decision does.

## 1. Summary

| Audited | Count |
|---|---|
| Concepts (stats, statuses, triggers, crafting, economy, town, legacy, guild) | about 300 rows across the four reports |
| Named effects, abilities and enemy moves, each checked against its code | 113, plus 22 laws, preparations and quest modifiers |
| Same thing under several names, or several things under one name | 94 |
| Writing problems with a rewrite | 75 |
| Text that states a mechanic the code does not have, or hides one it has | 22 (section 8) |

**The diagnosis.**
1. Most core words are already familiar RPG words. Health, Burn, Bleed, Chill, Regeneration, Thorns, Mark, Charge,
   Quest, Party, Rarity, Quality and Retreat need no rename.
2. The real problems are four:
   - **Invented sentence-names** for simple effects ("Iron holds", "Bronze rings back", "Silver holds a charge").
   - **Collisions:** one word for two mechanics. "Renown", "Trait", "Scrap", "Upgrade", "Watch", "Temper", and
     "Balanced" or "Reckless" both as forge risk and as retreat rule.
   - **Drift:** one mechanic under several words. Heal, mend and recover. Retreat, "Pulled back" and "breaks off".
     Quest, contract and job. Defender and champion. Status, state and stack.
   - **Text that disagrees with the code.** Chill is called "slowed", but nothing in the game is slow. The checkpoint
     promises an earlier return that does not happen.
3. Two names mislead outright. "Strike" is the stat every RPG calls Attack. "Buffs" heads permanent weapon properties,
   while the game's real timed buffs are Blessings.
4. The writing is mostly accurate but often compressed or machine-like, for example "95% strike. Reaches past Guard."
   or "left 4 of the 9 stacks put on foes, in 4 activations". Rewrites must keep every number, timing and limit.

**What does not change.** No rule, number, chance, RNG draw or reward changes. Content IDs, enum constants and save
keys keep their names; only display text changes (`D_saves_tests.md` Q1 to Q4). Proper names stay: Heartfire, Frost
Bloom, Void Ink, Moonsteel, Emberfall, the factions, named enemies, charters, openings, relics and signature weapons.
Owner decision A of 2026-10-14 stands: random chances are shown as words, never numbers.

## 2. Decisions for the owner

| # | Question | Recommendation | Alternatives |
|---|---|---|---|
| T1 | The 2026-10-14 revision kept Guard, Mark, Chill, Burn, Bleed, Regeneration, Wet, Steam, Thorns, Charge and Scrap "not renamed". Reopen it? | **Keep every status name.** Fix the definitions: Chill is never "slowed", Regeneration is never "Regen", and Thorns fire once and are used up. | Guard → **Barrier** (Diablo IV). Not recommended: see below. |
| T2 | Rename the stat "Strike"? | **Strike → Attack**, the stat and the weapon-type shares ("Attack 120% · Support 90%"). **Support** stays. Show it only where an ability reads it (Guardian, Warden, the enemy Gravecaller). | Keep Strike. "Damage" as the stat reads badly in effect text ("50% of the Guardian's Damage"). |
| T3 | Effect names that are sentences ("Iron holds") or traps ("Grave chill" applies Bleed) | **Name each metal and element effect after its source.** Iron, Bronze, Silver, Obsidian, Starsteel, Moonsteel, Fire, Frost, Storm, Grave, Verdant, Sun: "◆ Frost · On hit · once per round: puts 1 Chill on the enemy". This retires 12 invented names. **Charge burst → Discharge.** Rename the other trap names in section 4. Two places that list effects by name alone change with it. The Forge identity panel shows a short phrase per effect ("Starts with 3 Guard · Chills on hit") instead of "◆ Iron ◆ Frost". The log line "{who} hits X for N" becomes "Mira's weapon (Bronze) deals 5 damage to Ashclaw scout". | Give each a short ability noun instead ("Ironclad", "Retaliate", "Frostbite"): more names to learn. |
| T4 | Weapon properties are headed "Buffs" | **Properties**, shown as **Bonuses** and **Flaws**. A material's built-in cost becomes a **Drawback**. "Buff" is used only for timed effects (Blessings). | Diablo's "Affixes" (jargon), or "Modifiers" with Bonus and Drawback. |
| T5 | "Augment" (the slot that sets the element) | **Infusion**: "Frost Bloom · Infusion · Frost". It always infuses the weapon with its element, as a Dark Souls or D&D artificer infusion does. | **Enchantment**, as the brief suggests (BG3 and Diablo players read it as "+1" or a reroll). Or keep Augment. |
| T6 | Forge risk Safe / Balanced / Reckless shares two words with the locked retreat rules and one with quest danger | **Careful / Standard / Daring** for forge risk: plain English rather than an RPG convention, chosen to clash with nothing. The retreat rules stay Cautious / Balanced / Reckless. | Rename the retreat rules instead (locked 2026-10-14). |
| T7 | "Trait" means a customer's personality and a guild member's fight trait, on the same hero sheet. "Greedy" and "Loyal" exist in both sets with different rules. | Customer traits become **Personality**; guild **Trait** stays. The two guild traits that share a personality's name are renamed: Greedy → **Mercenary**, Loyal → **Avenger**. Also say what is hidden today: Brave and Cautious change a member's Attack in guild fights (×1.05, ×0.97). | Rename the guild trait "Quirk" (Darkest Dungeon). |
| T8 | Word collisions outside combat | Apply section 4.4. The main ones: **Fame** vs **Deeds**; bulk Scrap becomes **Melt down**; legacy upgrades become **Legacy perks**, so Upgrade means only the weapon action; **Militia** and **Armory** instead of "watch"; **Defenders** instead of "champions"; the pressure band "grave threat" becomes **severe threat** ("pressure" itself stays); siege trait becomes **Siege modifier**; the Company tab becomes **Roster**; **Era** everywhere instead of "run" (roguelite players know "run"; flip it if you prefer); the Legend Board becomes the **Hall of Fame** (or stays, with "famous weapons"). | Fix only the collisions inside one screen. |
| T9 | Guild "Rank" reads as a promotion. It is an optional handicap, and its only reward (2/4/6 legacy points) is never shown. | Keep the rank names. Present the step as a **harder challenge** and show its legacy points before and after. | Rename the system "Difficulty". |
| T10 | Old saves store fight logs, highlights, weapon names and the Legend Board as finished text | **Display-time map for effect and status names only** (old name → new), in the existing `LegacyProse` adapter, never written back. Other stored prose stays as history. | No map: old fights keep old words beside new ones. A save migration would break "histories are never rewritten". |
| T11 | Deterministic numbers are hidden, and a deterministic outcome is called "odds" | State them: catalyst +4 quality, Quench −3, exceptional result +20 quality and +1 property, flawed result −12 quality and 1 flaw, each metal's quality cap. Classic siege outlook words lose "odds": "Dire odds" → **Far behind**. Chances stay words (decision A). | Leave as they are. |

**Why keep Guard (T1).** Guard is a pool. It absorbs damage before health, stacks to 14, never fades between rounds,
blocks Burn but not Bleed, and "breaks" when emptied, which triggers effects. Each familiar alternative needs the same
footnotes plus a wrong first impression:
- **Barrier** fades over time in Diablo IV.
- **Block** is a chance in Diablo and WoW, and expires every turn in Slay the Spire.
- **Armor** reduces every hit and never runs out.
- **Temporary hit points** do not stack in D&D.

"Guard" with a one-line definition ("Absorbs damage before Health. It lasts until it is used up.") is the most accurate
choice. It also fits the Guardian class: enemies attack the Guardian while it has Guard.

## 3. Proposed vocabulary (the authoritative glossary)

On approval this replaces `docs/WRITING_GUIDE.md` section 2. Numbers are the current `FightRules` and `BalanceConfig`
defaults. "Replaces" lists words to remove from player text. No abbreviations are used (no HP, ATK or DoT on screen).
"Health %" between fights is the only short form.

### 3.1 Fighting

| Term | Player definition (card and glossary) | Exact mechanic | Replaces | Where |
|---|---|---|---|---|
| **Health** | How much damage a fighter can take. At 0 the fighter is down. | Fight scale: class base + per level. A hero enters a fight with their stored health as a share of that. | "hit points" never used | Cards, replay, report |
| **Health %** (between fights) | A hero's health outside a fight. | Stored 0 to 100. Shown as a percentage, so it is not mistaken for fight health (31/40). | "Health 80" | Hero sheet, roster |
| **Attack** (T2) | The damage of the fighter's own attack. | Old Strike: class base + level + weapon share × weapon-type % × metal % × personality. Enemies have a range, rolled per action. | Strike, strike damage | Every stat row and effect text |
| **Support** | Guardian: Guard gained each round. Warden: healing each round. | Read by those two classes' moves only. | "the Guard and healing their own move gives" | Guardian and Warden cards |
| **attack** (verb, noun) | The fighter's own move hitting an enemy. | A damage step of the actor's own move. "On hit" = such an attack that gets at least 1 damage past Guard. | strike (verb), blow, cut | Effect text |
| **hit** | Any direct damage: an attack or an effect. Not Burn or Bleed. | Non-tick damage. | blow | Effect text |
| **Damage** | Lowers Health after Guard. | Order: weakness or resistance, Wet, Mark, then Guard, then Health. | | Everywhere |
| **Heal** | Restores Health, never above the maximum. | Effective healing only. Overflow is an Overheal. | mend, recover, mended | Effect text, report |
| **Ability** | A fighter's action each round (Hold the line, Mend, Double cut). | `Move`. | move, "Moves" heading | Hero and enemy cards, log |
| **Passive** | An ability that answers events on its own (Riposte, Echo). | A class or unit `EffectDef`. | (none) | Hero and enemy cards |
| **Effect** | What a weapon, trait, relic or law does in a fight. | `EffectDef`. | rule, rules | Cards, replay, report |
| **Ignores Guard** | This damage goes straight to Health. | `pierce`. | past Guard, through Guard, reaches past Guard | Effect text |
| **Round** | Every party member acts in the order you chose, then every enemy. At most 8 rounds (12 in a Long Assault siege). | `Fight.kt` order. The party always acts first. | | Log, replay |
| **Down** / **defeat** | At 0 Health a fighter is down. Defeat = bring an enemy down. | | felled, downs, beaten | Log, effect text |
| **Retreat** | The party leaves the fight alive and wins no reward. | Retreat rule, or 8 rounds without a winner on a quest. | Pulled back, breaks off | Replay banner, report |
| **Front / back enemy** | The first and the last enemy still standing. | | | Effect text |
| **Priority target** | An enemy summoner first, then the enemy leader, then the enemy with the most Health. | Tagged `anchor`, `leader`. | "the foe that matters" | Effect text, enemy card shows **Summoner** too |
| **Weak to / Resists** | Hits of that element deal 30% more / 30% less. | Enemies only, direct hits only. | | Enemy card |
| **Elite**, **Leader**, **Summoner** | Enemy labels. | Tags `elite`, `leader`, `anchor`. Summoner is new on the card. | named fighter, champion | Enemy card |
| **Once per round / per fight** | The most often an effect can trigger for its holder. | Per effect per holder. | twice a fight | Ability rows |

### 3.2 Statuses (all kept, T1). Each lasts until the fight ends at most.

| Status | Player definition | Exact mechanic | Replaces |
|---|---|---|---|
| **Guard** | Absorbs damage before Health, point for point. Lasts until used up. At most 14. | Blocks Burn. Bleed and damage that ignores Guard pass. "Guard breaks" when a hit empties it. | "Blows hit Guard before health" |
| **Chill** | The fighter's next attack deals 35% less damage, then 1 Chill is used up. At most 3. | Only the first damage step of the next move. Heals and effect damage are untouched. | slowed, "next strike is weaker" |
| **Burn** | At the end of each round, the fighter takes 1 damage per Burn, then loses 1 Burn. Guard blocks it. At most 6. | | "loses 1 health per stack" (wrong: Guard blocks it) |
| **Bleed** | Before the fighter acts, it takes 1 damage per Bleed, then loses 1 Bleed. Ignores Guard. At most 5. | | |
| **Regeneration** | At the end of each round, the fighter heals 3, then loses 1 Regeneration. More Regeneration lasts longer, it does not heal more. At most 4. | | Regen, mends 3 |
| **Mark** | The party attacks a marked enemy first. The next hit on it deals 3 extra damage and removes the Mark. At most 1. | | Mark struck |
| **Thorns** | The next enemy attack that hits this fighter, even into Guard, deals all the Thorns back to the attacker. The Thorns are then used up. At most 8. | Effects, Burn and Bleed never set it off. | "hurts the attacker for this much, once" |
| **Charge** | Stored energy. A **Discharge** spends all of it at 3 or more for 8 storm damage. At most 5. | | "Stored for a rule that spends it" |
| **Wet** | A storm hit on a Wet fighter deals 2 extra damage and uses up 1 Wet. Loses 1 Wet each round. At most 2. | Burn put on a Wet enemy turns into Steam. | Wet struck |
| **Steam** | Made when a fighter gives Burn to an enemy that has Chill or Wet, or Chill to an enemy that has Burn: they cancel one for one and the giver gains that much Steam. At most 4. | Spent by Steam lance and Steam veil. | Chill boiled off |
| **Scrap** | Left (2) when a weapon cracks in a fight. The Salvage Bell spends it. At most 4. | | |
| **Cracked** (weapon) | The weapon cracked in this fight. It still works but loses 25 extra condition afterwards. | At most once per fight. | |

### 3.3 Forging and weapons

| Term | Player definition | Exact mechanic | Replaces | Where |
|---|---|---|---|---|
| **Materials** | Metals, infusions and catalysts. | One metal + one infusion (+ an optional catalyst) per forge. | ingredient(s) | Forge, Supplies |
| **Metal** | Sets quality, power and the highest quality and rarity a weapon can reach. In a guild run it also gives an effect. | +6 quality and +3 power per tier. Caps 55/65/75/84/100. | core, basic metals, fine metals | Forge, cards |
| **Infusion** (T5) | Gives the weapon its element, and in a guild run that element's effect. | Always sets the element. | Augment, "Adds Fire" | Forge slot, cards, Notebook |
| **Catalyst** | Optional, Advanced forge only. +4 quality, more exceptional results, fewer flaws. Used up. | | "Helps the forge along" | Forge, Supplies |
| **Technique** | Temper, Quench or Etch: changes a forge's odds or properties. | Temper: fewer flaws and exceptional results. Quench: always the element property, −3 quality. Etch: +1 property, more flaws. | | Advanced forge |
| **Risk** (T6) | **Careful / Standard / Daring**: how wide the quality roll is and how often results are exceptional or flawed. | Old Safe / Balanced / Reckless. Daring adds 1 property. | Safe, Balanced, Reckless (forge only); "Temper" as the risk clue | Forge, Notebook clues |
| **Quality** | 1 to 100, with a word: crude, decent, fine, superb, masterwork. | | "Epic quality" (rarity) | Cards, commissions |
| **Rarity** | Common, Uncommon, Rare, Epic, Legendary. | Mostly from quality, capped by the metal. Legendary only by Heartfire or deeds. | | Cards |
| **Power** | The weapon's overall strength. Sets its price and its share of a hero's Attack. | | | Card headline |
| **Condition** | Wear from 0 to 100. Fights lower it. Repair restores it. | 0 still works, at 75% power. Not "durability" (in Diablo 0 durability means broken). | | Cards |
| **Fame** | How well known a weapon is. Raises its power and price a little. At 3 or more it can join the Hall of Fame. | `Weapon.fame`. | "Renown: Unsung" row | Weapon sheet, Legend Board |
| **Deeds** | Great acts done with a weapon. 3 deeds make it Legendary. | `Weapon.renown`. | "earned renown" | Weapon card, Gazette |
| **Properties: Bonuses and Flaws** (T4) | Named traits a weapon rolls at the forge, good (Flaming, Swift) or bad (Brittle, Heavy). | Affixes. Slots by rarity at the forge. | Buffs, buff slot, affix | Weapon card headings, outlook |
| **Drawback** (T4) | What a material always costs in a fight. | Material constraint. | Trade-off, "slight drawback" (affinity word becomes "slight mismatch") | Forge, recipe card, Notebook |
| **Exceptional / flawed result** (T11) | Exceptional: +20 quality and 1 more property. Flawed: −12 quality and 1 flaw. | Chance by risk, catalyst and technique, shown as words. | "exceptional work" | Risk menu, result card |
| **Upgrade / Repair** | Upgrade (first time): +6 quality and full condition. Repair (later): full condition. | Locked 2026-10-14. The only "Upgrade" in the game (T8). | | Weapon sheet |
| **Salvage** / **Melt down** (T8) | Salvage one weapon for 1 metal (1 energy). Melt down many at once for 1 metal per 4 (no energy). | | bulk "Scrap" | Storage |
| **Role** (recipe) | What the weapon does for a party: Tank, Party support, Protects the weakest, Damage over time, Burst damage, Back-line damage, Weakens enemies, Marks targets, Self-healing, Steam combos, Breaks for the party, Plain damage. | `ForgeOutlook.role`. | "Holds the line" (the Guardian's ability), "A plain striker" | Forge, recipe card |
| **Combination** | A metal and infusion (or infusion and weapon type) forged together. Learning one shows how well they suit each other. | Pairing affinity. | pairing, untried pairing, Field notes | Forge notes, Notebook |
| **Signature weapon** | A unique named weapon with a hidden recipe. Clues reveal it step by step. | | Signature work, new recipe discovered | Forge, Notebook, Gazette |

### 3.4 Shop, town and legacy

| Term | Player definition | Replaces | Where |
|---|---|---|---|
| **Energy** | 10 a day for forging and other work. You can borrow up to 4 from tomorrow. | overwork, "exhausted" (the smith) | Header, Forge |
| **Commission** | A paid order for a weapon (locked). | order, request, offer | Shop, board |
| **Customer wants** | What customers who bought nothing are looking for. Not an order. | request | Board, lead |
| **Visitor** | A morning caller with a choice. | (code: encounter) | Shop |
| **Blessing** | A timed boon from the town after a held siege. The game's only buff. | | Offer, receipts |
| **Workshop relic** | A lasting bonus kept at the forge. | | Relic slots |
| **Siege** | A major attack on the town on a known day. | | Header, Town |
| **Defenders** | The 3 heroes on the wall. | Champions, champion | Town, run end |
| **Defense / Raid** | The town's total strength against the attackers' strength. The town holds if defense is at least the raid (classic run). | | Town |
| **Militia** / **Armory** | The town's soldiers / the weapons the forge donated to them. | "the watch" for either, "Arm the watch" becomes **Donate to the armory** | Town, weapon sheet |
| **Siege modifier** | A condition the scouts report for this siege (Long Assault). | Siege trait | Town |
| **Pressure** | How strong a faction is growing: quiet, restless, rising threat, severe threat, overwhelming threat. The strongest faction leads the next siege. | "grave threat" | Town, Gazette |
| **Forge health** | The forge's integrity. A siege lowers it unless the town holds by a wide margin. At 0 the era ends. | integrity, "the wall was mended" | Town, header |
| **Era** (T8) | One playthrough. | run, chapter | Menu, run end, Gazette |
| **Legacy points**, **Legacy perks** | Points earned each era, spent on lasting perks. | Permanent upgrades, pts | Run end |
| **Hall of Fame** | Up to 3 famous weapons an era are remembered and may return. | Legend Board, legend | Records, run end |
| **Lineage** | A famous hero's descendant arrives next era. | | Hero sheet |

### 3.5 Guild

| Term | Player definition | Replaces | Where |
|---|---|---|---|
| **Member**, **Roster** | A hero who joined your guild (at most 6). | Company (tab) | Guild |
| **For hire**, **Recruit**, **Recruitment fee** | Heroes willing to join, the action, its fee. | Recruits (for people not yet recruited), sign, hire, signing fee | Guild |
| **Quest**, **Quest board** | Work for a party (locked). | contract, job | Guild, lead card |
| **Party** | 1 to 3 members sent on a quest, acting in the order you picked. | | |
| **Stage**, **Checkpoint** | One fight of a quest. At a checkpoint you choose **Go deeper** or **Return**. | push deeper, the deeper door, come home | Quest sheet |
| **Danger** | Safe, Dangerous, Lethal. | | Quest card |
| **Retreat rule** | Cautious, Balanced, Reckless (locked). The wall always fights under Balanced. | posture | Quest sheet, Wall |
| **Injured, Exhausted, Scarred, Captured** | Member conditions with their days. | held, taken, in enemy hands | Roster |
| **Reserve for the wall** | Keep a member home to defend. Cleared after each siege. | keep, kept | Wall |
| **Trait** (T7) | A member's personal fight effect. | | Member card |
| **Personality** (T7) | A townsperson's temperament (Brave, Greedy). Some change Attack a little. | Traits (customer) | Hero sheet |
| **Speciality** | A permanent improvement a member chooses once they have won 3 quests, been downed twice, or fulfilled their own ambition. | branch | Hero sheet, roster |
| **Level** | 1 to 20. Each level adds Health, Attack and Support. | | Cards |
| **Starting kit** | The first morning's choice of relic, traits and gear. | opening, start, package, build | Opening chooser |
| **Rank** (T9) | An optional harder challenge after the charter is secured, worth 2/4/6 legacy points. Cannot be undone. | | Charter tab |

## 4. Replacement table

Original → proposed, with the mechanic it describes. Names in section 5 stay.

### 4.1 Effect and ability names

| Original | Proposed | Actual mechanic | Why | Decision |
|---|---|---|---|---|
| Iron holds | **Iron** | Start of fight: wielder gains 3 Guard | Sentence-name; "Bonus Defense" would collide with the siege's Defense | T3 |
| Bronze rings back | **Bronze** | When an enemy breaks the wielder's Guard: 5 damage to it, once per round | Riposte and Rune of answer also retaliate, so the generic word "Retaliation" cannot be one item's name | T3 |
| Silver holds a charge | **Silver** | Start of fight: 3 Charge (a Discharge fires at once if the wielder has one) | | T3 |
| Obsidian cracks | **Obsidian** | +10% Attack. The first time an enemy breaks the wielder's Guard, the weapon cracks | | T3 |
| Starsteel finds the Mark | **Starsteel** | When the wielder's attack uses up a Mark: 3 more damage, once per round | | T3 |
| Moonsteel bites the cold | **Moonsteel** | When the wielder's attack damages a Chilled enemy: 2 more damage | | T3 |
| Ember, Rime, Arc, Grave chill, Sap, Noon light | **Fire, Frost, Storm, Grave, Verdant, Sun** | The element's effect: 1 Burn / 1 Chill / 2 storm damage to the back enemy / 1 Bleed / 1 Regeneration to the wielder / a Mark, on hit, once per round | "Grave chill" applies Bleed, not Chill; one effect per element, so the element names it | T3 |
| Charge burst | **Discharge** | At 3+ Charge: spend all, 8 storm damage to the attack target, once per round | Not a "charged attack": it is a separate burst (Path of Exile's Discharge spends charges the same way) | T3 |
| Reach (Spear) | **Penetration** | When Guard blocks the wielder's attack, half the blocked damage (rounded up) goes through | D&D "Reach" is range | |
| Off-hand cut (Dagger) | **Off-hand attack** | After acting: 45% of Attack to the attack target, once per round | D&D two-weapon off-hand attack | T2 |
| Second arrow (Bow) | keep | After acting: 35% of Attack to the back enemy | Already says what it does; "Additional Attack" would drop the target | |
| Capacitor (Binding Salt) | **Conduit** | When the wielder is healed: 1 Charge | "Capacitor" is modern electrical jargon | |
| Rune of answer | **Rune of Retaliation** | After a party weapon cracked: when an enemy breaks an ally's Guard, 5 damage to it | | |
| Dragon oil / Void ink on weapons forged before rules 2 | **Dragon oil (old forging)**, **Void ink (old forging)** | Older rules kept by old weapons | Two weapons show one name with two rules today | |
| Cleave (Ashclaw warchief, Warlord Krag) | **Heavy swing** | 100% Attack to one target | Cleave hits several targets in D&D, Diablo and WoW | |
| Cut and run (Ashclaw thief) | **Backstab** | Attacks the back party member, ignoring the Guardian. Never leaves | "Run" promises it flees | |
| Rout (Ashclaw passive) | **Demoralized** | When their leader goes down: each gains 2 Chill | Rout means fleeing | |
| Raise the fallen (Gravecaller) | **Summon shambler** | Summons a new shambler | No corpse is raised | |
| Grave weapon (The Pale Knight) | **Undying blow** | Attack, then 1 Regeneration to self | Implies grave damage or Bleed | |
| Rend (Broodmother Vyrash) | **Scale guard** | 4 Guard to self, then attack | Rend is a bleed in WoW and Diablo | |
| Shield wall (Bone warden move) | **Raise shield** | 3 Guard to self, then attack | The siege preparation "Shield wall" is a different rule | |
| Fire breath (drake) | keep, description says "applies no Burn" | 130% Attack to every member | Familiar; the text removes the expectation | |
| Vampiric, Swift (properties) | keep names; descriptions say "heals 6 when the wielder defeats an enemy", "attacks again for 30% of Attack" | | Names are baked into weapon names ("Swift Iron Sword") | |
| Mourning Rod, Bone Music Box | keep names; text: "a spectral helper joins the party" | Summons a fixed helper, not the enemy | The current text says the enemy rises | |
| Giant Slayer | keep; text says "an Elite enemy" | Checks Elite only | Text promises leaders too | |

### 4.2 Labels and stats

| Original | Proposed | Why | Decision |
|---|---|---|---|
| Strike (stat), strike damage, "Strike 120%" | Attack, "Attack 120%" | The stat every RPG calls Attack. "Strike" is also used as a verb and a noun beside it | T2 |
| Moves (enemy card), move | Abilities, ability | The familiar word for a fighter's action | |
| Regen | Regeneration | One name per status. The replay alone says "Regen" | T1 |
| Pulled back, breaks off | Retreated, retreats | Locked term Retreat (2026-10-14) | |
| foe, foes, Besiegers | enemy, enemies, attackers | "Foe" was retired in 2026-10-14 | |
| rule (in replay and report text) | effect | Locked term Effect | |
| state, states | status, statuses | One word for one thing | |
| mend, mends, recover, health mended | heal, heals, healed | Four verbs for one mechanic. "Mend" stays only as the Warden's ability name | |
| "Chilled · weaker", "Mark struck", "Wet struck", "Chill boiled off" (replay pops) | "Chill −35%", "Mark +3", "Wet +2", "Steam" | The number says what happened | |
| fired 4 times / in 4 activations | triggered 4 times | One phrasing | |
| "Only when defending the town" / "Only on the town's wall" / "(at home)" | "Siege only" / "Quests only" | Three phrasings of one condition | |

### 4.3 Crafting and weapons

| Original | Proposed | Why | Decision |
|---|---|---|---|
| Augment, augment slot, "Metal + augment" | Infusion | Says what the slot does: it always infuses the element | T5 |
| ingredient | material | Two words for one thing; "Materials" is the crafting word in Diablo and Torchlight | |
| Buffs (heading), buff slot, Another buff, affix | Bonuses, property slot, another bonus, property | A buff is temporary in every RPG. These are permanent | T4 |
| Trade-off | Drawback | The brief's word. Kept apart from Flaw, which is rolled | T4 |
| Safe / Balanced / Reckless (forge risk) | Careful / Standard / Daring | Frees the locked retreat-rule words and quest danger "Safe". Plain English, not an RPG convention | T6 |
| Notebook clue steps "Recipe / Catalyst / Temper / Finish" | Recipe / Catalyst / Risk / Quality | "Temper" is a technique and "finish" its hint | T6 |
| "Can reach Epic quality", "Stops at Rare quality" | "Rarity can reach Epic", "Rarity stops at Rare" | Rarity is not quality | |
| Basic metals, fine metals | Named: "Iron, Bronze, Silver and Obsidian", "Starsteel and Moonsteel" | "Basic" means two sets, and "fine" is also a quality word | |
| Renown (fame row), "Unsung … Renowned" | Fame (same words) | The weapon's fame. "Renown" also named deeds | T8 |
| "earned renown" | "earned a deed (1 of 3 toward Legendary)" | Says what it counts toward | T8 |
| Rare power (Heartfire) | For Legendary forging | "Rare" is a rarity tier | |
| Holds the line, Protects the party, A plain striker … (recipe roles) | Tank, Party support, Plain damage … (3.3) | The familiar party roles, and the Guardian's ability name is freed | |
| Untried pairing, Field notes, Metal pairings | Untried combination, Notebook, Metal combinations | The brief's word. "Field notes" was a third name for the Notebook | |
| Signature work, New recipe discovered | Signature weapon, Signature discovered | One name | |
| Scrap (bulk storage action) | Melt down | "Scrap" is also a fight status. A smith melts weapons down | T8 |
| Restock, Buy more | Buy | "Restock" is also the supplier's own refill | |
| Arm the watch, donate | Donate to the armory | "Watch" meant both the militia and the armory | T8 |
| "Advanced forge, reckless risk, upgraded" | "Advanced forge · Risk: Daring · Upgraded" | Built from enum names today | |

### 4.4 Town, legacy and guild

| Original | Proposed | Why | Decision |
|---|---|---|---|
| Champions, champion | Defenders, defender | Locked glossary term; "champion" reads as a class or a title | T8 |
| Watch (armory part), town watch (militia) | Armory, Militia | One value had two labels on one screen | T8 |
| Siege trait | Siege modifier | "Trait" already names hero and member traits. Quest modifiers work the same way | T8 |
| grave threat (pressure band) | severe threat | Sits beside "Resists Grave". "Pressure" itself stays: it is the GDD's word | T8 |
| Strong position / Evenly matched / Outmatched / Dire odds (classic outlook) | Holding strong / Holding / Falling short / Far behind | The classic siege has no roll. "Odds" and "evenly matched" promise chance | T11 |
| run, chapter ("Continue run", "Retire the chapter") | era ("Continue era", "Retire the guild") | Two words for one thing. Era is the game's word; roguelite players know "run", so the owner may prefer it | T8 |
| Eras survived | Eras played | It counts lost eras too | |
| Permanent upgrades, Fully upgraded, pts | Legacy perks, Fully unlocked, points | Leaves "Upgrade" to the weapon action. Perks are the familiar meta-progression word | T8 |
| Legend Board, legend, "6 victories" | Hall of Fame, famous weapon, "6 kills" | A "legend" need not be Legendary, and the board is about fame. "Hall of Fame" reads modern; keeping "Legend Board" with "famous weapons" also works | T8 |
| "bought an upgrade", "found no upgrade" (customers) | "bought a better weapon", "found nothing better" | A third meaning of "upgrade" | T8 |
| Heroes repaired the forge | Heroes restored forge health | Repair is the weapon action | |
| Company (tab), Recruits (section) | Roster, For hire | "Company" has six uses; "Recruits" lists people not yet recruited | T8 |
| contract, job, "Open guild contracts" | quest, "Open the quest board" | Locked term Quest | |
| The Festival Contract (council visitor) | Festival Day | "Commission" would collide with weapon orders | |
| branch, Choose branch | speciality, Choose a speciality | One name | |
| kept for the wall, Keep for the wall | reserved for the wall, Reserve for the wall | Glossary term Reserve | |
| held, taken, in enemy hands | captured | One name; "held" also means a siege held | |
| Lost, Defeated, beaten (a quest) | Defeated | One name | |
| opening, start, package, build | Starting kit | Four words for one choice. "Kit" says what it holds: a relic, traits and gear | |
| Traits (customer) | Personality | "Trait" is the guild member's fight effect | T7 |
| Greedy, Loyal (guild traits) | Mercenary, Avenger | Personalities with the same names have other rules. Mercenary takes a 15% share; Avenger answers an ally going down | T7 |
| Work on the wall (quest) | Restore the forge | It restores forge health. "The wall" is where defenders fight | |
| loot (Cowardly) | gold | It is only gold | |

## 5. Names kept

**Already familiar.**
- Health, Guard, Burn, Bleed, Chill, Regeneration, Thorns, Mark, Charge, Wet, Overheal.
- Quest, Party, Retreat, Stage, Checkpoint, Level, Class, Rarity tiers, Quality, Power, Condition.
- Salvage, Upgrade, Repair, Commission, Blessing, Relic, Siege, Militia, Elite, Leader.
- Riposte, Second Wind, Mend, Second arrow.

**Unique to this game, kept and explained where they appear.**
- **Mechanics:** Heartfire, Steam, Scrap (fight), Discharge, Deeds, Hall of Fame, Lineage, Deep road, Signature weapon.
- **Proper names:** the charters, starting kits, relics, signature weapons, factions, named enemies, the routes, ranks
  and traits (except the guild traits Greedy and Loyal, T7).
- **Elements:** Fire, Frost, Storm, Grave, Verdant, Sun. These are world identity, not D&D damage types. Grave applies
  Bleed and Verdant heals, so "Necrotic" or "Poison" would mislead.

## 6. RPG terms not adopted (no matching mechanic)

| Term | Why not |
|---|---|
| Armor, Block, Dodge, Miss, Critical hit | Nothing in a fight is random except an enemy's damage roll, and no fighter has armor or evasion (`A_combat.md` "Mechanics absent") |
| Poisoned, Stunned, Frozen, Slowed | No such statuses. Chill weakens one attack and never slows or skips a turn |
| Lifesteal | Vampiric heals on a defeat, not per hit |
| Taunt | The Guardian only draws single-target attacks, and only while it has Guard. Said in its ability text instead |
| Vulnerable, Damage reduction % | Only flat bonuses (Mark +3, Wet +2) and the 30% element weakness or resistance exist |
| Companion | Members are hired, paid and dismissed; there is no story or approval system |
| Loot | Rewards include militia, forge health and rescued members. "Reward" stays |
| Expedition (for quests) | Already the townsfolk's own fights. Quests stay quests |
| Encounter (for visitors) | Means a fight to an RPG player. "Visitor" stays |
| Boss | Only the three siege warlords would qualify. "Warlord" already says it |
| Enchantment (unless T5 picks it) | Reads as "+1" or a reroll |

## 7. The brief's examples, checked against the code

| Brief | Proposed | Finding |
|---|---|---|
| Iron holds → Starting Guard / Bonus Defense | **Iron**: "Starts each fight with 3 Guard." | "Defense" is the siege total and is never read in a fight. Reinforced (+4), Wallwarden (+5) and Cowardly (+4) also give starting Guard, so the mechanic is a description, not one item's name |
| Bronze rings back → Counterattack / Retaliation | **Bronze**: "When an enemy breaks the wielder's Guard, deals 5 damage to that enemy. Once per round." | Riposte (50% of Attack) and Rune of Retaliation (5, after a crack) share the trigger with different rules. They need distinct names |
| Rime → Frost / Chill | **Frost** | Chill is the status it applies, so it cannot also be the effect's name |
| Holds the line → Defensive / Guard | The Guardian ability **Hold the line** stays; the recipe role becomes **Tank** | The role label copied the ability's name |
| Second arrow → Additional Attack | keep | 35% to the back enemy. "Additional Attack" (D&D Extra Attack is a full attack) drops the target |
| Charge burst → Charged Attack | **Discharge** | It does not modify an attack. It spends all Charge for a separate 8 storm damage |
| Untried pairings → New Combinations | **Untried combination** | Accepted |
| Strike / Support % → Attack / Support Effectiveness | **Attack 120% · Support 90%** | Accepted (T2) |
| Chill example: "reduces damage from the next attack by 35%" | "The next attack deals 35% less damage, then 1 Chill is used up." | The brief's line drops that each attack uses up 1 Chill, and that only the first hit of a double attack is weakened |
| Frost Bloom: "Attacks apply Chill" | "Frost Bloom · Infusion · Frost. On hit, once per round: puts 1 Chill on the enemy." | Only the wielder's own attacks that get past Guard, at most once per round |

## 8. Text that disagrees with the code (fix in Phase 2 whatever the naming)

1. The quest sheet says returning at the checkpoint "ends the quest earlier". The party comes home on the same morning
   either way, as the checkpoint panel itself says (`GuildUi.kt:308` vs `Missions.kt:286`, `GuildUi.kt:442`).
2. Heavy and Sentient say "(slowed)". Chill only weakens one attack (`CombatContent.kt:291,304`).
3. Giant Slayer says "elite or leader". The code checks Elite only (`CombatContent.kt:282`, `Capabilities.kt:402`).
4. Mourning Rod and Bone Music Box say the first fallen enemy "rises". A fixed spectral helper joins instead
   (`CombatContent.kt:353-354,376-377`).
5. "The Coward Returns Alone" calls the first member at home a survivor of the lost quest (`GuildVisitors.kt:31,71`).
6. Licensed Bad Ideas: "dangerous quests from day 1". Every quest type opens, Safe ones included (`Missions.kt:72,101`).
7. Two charters do not mention the half-price third member (`GuildContent.kt:446,456`, `CharterPicker.kt:78`).
8. The legacy points of ranks and of the secured charter are never shown (`GuildConfig.kt:88`, `Legacy.kt:108`).
9. The wall always fights under the Balanced retreat rule, and a Long Assault siege lasts 12 rounds. Neither is said
   (`SiegeFight.kt:211,213`).
10. Brave and Cautious change a member's Attack in guild fights, unsaid (`Power.kt:16-17` via `Loadout.kt:41`).
11. "Rarity sets how many properties it holds" is only true at the forge. Later rarity gains add none
    (`Dialogs.kt:153`).
12. The classic siege is decided by defense ≥ raid with no roll, yet the outlook says "odds" (`Battle.kt:300`).
13. A narrow hold also costs up to 24 forge health. The glossary says only a lost siege does (`Battle.kt:301-302`).
14. Burn's description says "loses 1 health per stack". Guard blocks Burn (`ReplaySupport.kt:74`, `Fight.kt:180`).
15. The title "Bane of the Hollowbound" is cosmetic but reads like the Undead Bane property (`Battle.kt:57`).
16. "Eras survived" counts lost eras (`App.kt:163`). The Legend Board prints kills as "victories" (`Lines.kt:317`).
17. The quest fee is paid when planned, but the departure line says it was paid at departure (`Missions.kt:242`).
18. The Battlemage's text omits Storm-Touched members as Charge targets (`CombatContent.kt:174`).
19. Speciality unlock conditions and members' experience are never shown (`Stories.kt:83-87`).
20. "Fine weapons are wasted on the party" (The Plain Oath) hides the rule: rare or better weapons lose the oath bonus.
21. Property cards show two rule sets side by side without saying where each applies. One is the power text, used by
    the townsfolk's expeditions and sieges; its attack bonus also applies in every guild fight. The other is the guild
    fight effect (`LaunchContent.kt:149-167`, `CombatContent.kt:274-304`). "+X% attack away from town" also raises
    Attack at the guild siege (`Loadout.kt:29-31`), so "away from town" is untrue there. Phase 2 states where each
    part applies.
22. The siege outlook word "grave threat" sits beside "Resists Grave" (`Battle.kt:437`); `Labels.kt:50` already bans
    "grave" for the odds words for this reason.

## 9. Writing problems beyond terminology

The rules below extend `WRITING_GUIDE.md` section 1. Examples are from the audit, rewritten with the same numbers.

| Pattern | Before | After |
|---|---|---|
| Sentence-names and fragments | "95% strike. Reaches past Guard." | "Attack 95%. **Penetration:** once per round, when Guard blocks the wielder's attack, half of the blocked damage goes through." |
| Pronouns that could point at two fighters | "When an enemy breaks this Guardian's Guard, the Guardian strikes that enemy back for half its strike." | "When an enemy breaks the Guardian's Guard, the Guardian deals 50% of its Attack to that enemy. Once per round." |
| Status text that hides the rule | "Its next strike is 35% weaker and uses up one stack." | "Chill: the fighter's next attack deals 35% less damage, then 1 Chill is used up." |
| Machine-like report lines | "Mira's Ashen Sword: Ember left 4 of the 9 stacks put on foes, in 4 activations." | "Mira's Ashen Sword put 4 of the party's 9 Burn on enemies. Its Fire effect triggered 4 times." |
| Theatrical instructions | "The party waits at the deeper door. Without word, they return home with the rewards already won." | "The party is waiting at the checkpoint. If you do not choose, it returns with the rewards already won." |
| Vague amounts | "Nothing can go wrong, and the gain is small: a little wall repair and 1 more militia." | "It always succeeds: up to 5 forge health and 1 militia. The party is away tonight and cannot defend a siege." |
| Hidden numbers | "Optional. Improves the result and is used up when you forge." | "Optional. +4 quality, more exceptional results and fewer flaws. Used up when you forge." |
| Compressed shorthand | "Restock: 22 → 100 gold, uses 1" | "Buy more: 100 gold each. This forge uses 1." |
| Flavour carrying a rule | "Away from home the weapon balks: on a quest, the wielder starts each fight with 1 Chill (slowed)…" | Rule: "Quests only: the wielder starts each fight with 1 Chill." Flavour line: "It balks away from home." |
| Words from enum names | "Advanced forge, reckless risk, upgraded" | "Advanced forge · Risk: Daring · Upgraded" |
| Repeated facts | Sword card: "Strike 100%, Support 100%" then "One clean strike. 100% strike and support." | Stats only (already fixed in V3) |

Rules for the guide:
- **Effect text shape:** When [trigger], [actor] [does] to [target]. [Limit]. The trigger clause must have no comma
  before its end, and the limit must be the last sentence. The inspect cards parse this shape (`InspectUi.kt:33-45`).
- **One word per mechanic, across every screen.** The glossary in section 3 is the list.
- **No adjective-as-noun shorthand** ("the hurt rest and the fit train"), no colon fragments in rules, no "it" when two
  fighters are in the sentence.
- **Deterministic numbers are stated.** Chances are words (decision A).

## 10. Inspect cards: what the player learns first (brief section 5)

The V3 cards already answer the brief's four questions, in this order, and keep their look:
1. **What is it:** art, name in its rarity colour, type line ("Infusion · Frost", "Uncommon · Fire bow").
2. **What does it do:** effect rows, each with when and how often beside its name, then what it does.
3. **How strong:** the headline number (Power, Likely quality), then the stat rows.
4. **Conditions and drawbacks:** ◇ conditions, − drawbacks and flaws, ? chances ("never certain"), then each status
   named on the card explained once.

Calculations, sources and every chance stay one tap away in "Full details".

Phase 2 changes on the cards:
- With T3, metal and element effect rows show the source and what it does, so the name no longer repeats the
  description.
- Support appears only where an ability reads it (T2).
- Property cards say where each of their two rule blocks applies (section 8, item 21).
- Enemy cards add Summoner to Elite and Leader, because it decides the priority target.
- Status lines use the definitions in section 3.2. Hero cards show Attack, and Health % between fights.

Nothing that decides a choice moves behind "Full details".

## 11. Phase 2: plan, risks and verification

**Order of work.** Each batch is tested before the next.
1. **Label maps first:**
   - one Stat map instead of the five that disagree;
   - one VisitReason map instead of two;
   - maps for every enum shown to players (Rarity, Element, Risk, Technique, WoundKind, Danger, Posture,
     MissionArchetype);
   - maps for the stored keys `Lost.reason`, `Bond.kind` and `RivalGuild.wants`.

   Enum constants, IDs and save keys never change (`D_saves_tests.md` Q4).
2. **Combat content and statuses:** `CombatContent.kt`, `ReplaySupport.kt`, `Replay.kt`, `Account.kt`,
   `SiegeReport.kt`, `FightReport.kt`.
3. **Crafting and weapons:** `LaunchContent.kt`, `ForgeOutlook.kt`, `ForgeUi.kt`, `Labels.kt`, `WeaponStatCard.kt`,
   the inspect builders.
4. **Guild:** `GuildContent.kt`, `GuildVisitors.kt`, `Missions.kt`, guild UI.
5. **Town, legacy, Gazette, shop day:** `Battle.kt`, `Lines.kt`, `Digest.kt`, `Gazette.kt`, `RunEndScreen.kt`.
6. **Old saves (T10):** extend `LegacyProse.display` with an old-to-new map for effect and status names. It is
   read-only and covers whole tokens only.
7. **Docs:** `WRITING_GUIDE.md` sections 1 and 2 replaced by sections 3 and 9 above, a GDD revision note for T1 to
   T11, and CHANGELOG.

**Risks and how they are handled.**
- **Stored text.** New fights, events and weapon names use the new words. Old fight logs, histories and the Hall of
  Fame keep theirs, apart from the T10 map. Weapon titles and `Lost` reasons are not reworded, so `GoldenStateTest`
  stays as recorded.
- **Tests.** About 890 assertions pin display text in 113 test files. The combat-term batch touches about 25 to 35
  files. The guard tests are rerun after every batch:
  - `ProseGuardTest`: no dashes, no semicolons, no "blade";
  - `LaunchContentTest`: validate, the name-pool terms, no percentage beside chance words;
  - `ConstantsTest`: if the threat words in `Battle.kt` change, its allow-list changes with them;
  - `VersionFingerprintTest`: rewording prose leaves it unchanged;
  - `LocaleDeterminismTest`.
- **Name pools.** `ContentCatalog.validate()` rejects hero names containing a game term. New display names ("Iron",
  "Attack") are checked against the pools.
- **Debug scenarios.** Regenerated with `./gradlew :core:scenarios`. The emulator scripts' 93 text greps are updated.
- **Layout.** "Attack" is shorter than "Strike". "Personality", "Siege modifier" and "Infusion" are longer. The 320 dp /
  2.0× captures are repeated for the cards, the Forge, Town and the roster.

**Verification.**
- `:core:test`, `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest`, `:core:scenarios`.
- A grep for every retired word in player text.
- Device captures at 411 / 360 / 320 dp.
- A count of terms changed.
- A list of any remaining unique terms.

Out of scope: rules, numbers, balance, RNG, save format and visual design.
